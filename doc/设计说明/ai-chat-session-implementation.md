# AI 会话实现说明

本文面向需要理解 ZenVis Backend 当前 AI 会话实现的开发者，重点说明 `/api/v1/dih/chat`、会话管理、普通问答、深度思考、附件、MCP Agent 和数据可视化 Agent 的主要逻辑。

## 代码范围

| 模块 | 关键文件 | 作用 |
| :--- | :--- | :--- |
| Chat 接口入口 | `src/main/java/com/coolxer/controller/dih/ChatController.java` | 接收聊天请求、创建/更新会话、选择聊天分支、封装流式事件、保存 AI 回复 |
| 会话管理接口 | `src/main/java/com/coolxer/controller/dih/ChatSessionController.java` | 会话增删改查、置顶列表、首次进入会话时返回默认开场白 |
| 普通聊天服务 | `src/main/java/com/coolxer/service/dih/AIChatService.java` | 调用 Spring AI ChatClient 或原生 OpenAI 兼容接口，处理 RAG、记忆、深度思考、附件图片 |
| 会话服务 | `src/main/java/com/coolxer/service/dih/impl/ChatSessionServiceImpl.java` | 读写 `t_ai_chat_session`，按当前用户隔离会话 |
| 会话实体 | `src/main/java/com/coolxer/dao/mysql/entity/ChatSession.java` | 保存会话标题、类型、消息 JSON、置顶、深度思考等状态 |
| 结构化消息解析 | `src/main/java/com/coolxer/service/dih/ChatMessagePartParser.java` | 将 AI 回复解析为 markdown、code、thinking、notice、confirm、chart 等片段 |
| 附件服务 | `src/main/java/com/coolxer/service/dih/ChatAttachmentService.java` | 上传附件、读取文本附件、图片转 OpenAI image_url 输入 |
| 数据可视化 Agent | `src/main/java/com/coolxer/service/dih/agent/DataVisualizationAgent.java` | 通过只读 retrieval MCP 工具完成可视化分析 |
| MCP Agent | `src/main/java/com/coolxer/service/dih/agent/McpAgent.java` | 将已连接 MCP 工具挂到模型工具调用链路 |
| 记忆配置 | `src/main/java/com/coolxer/configuration/ai/SpringAiChatMemoryConfiguration.java` | 初始化 Spring AI JDBC chat memory，并使用 MySQL 存储 |

## 总体设计

当前 AI 会话有两层“会话数据”：

| 数据 | 主要用途 | 写入位置 | 读取位置 |
| :--- | :--- | :--- | :--- |
| `ChatSession.messages` | 前端展示历史消息 | `ChatController.saveAiResponse` 和用户消息追加逻辑 | `ChatSessionController.sessionInfo/list/view` |
| Spring AI `ChatMemory` | 给模型提供多轮上下文 | Spring AI `MessageChatMemoryAdvisor` 自动写入；原生 OpenAI 分支和业务 Agent 运行时手动写入 | `AIChatService`、`PromptDrivenAgentRuntime` |

这两层不是同一个表，也不是同一份 JSON。前端历史主要依赖 `t_ai_chat_session.messages`，模型记忆主要依赖 Spring AI JDBC memory 表。

整体请求链路如下：

```text
前端 POST /api/v1/dih/chat
        |
        v
ChatController.chat
        |
        |-- 校验模型、消息、agent 类型权限
        |-- 读取/拼接附件上下文
        |-- 创建或更新 ChatSession.messages，先保存用户消息
        |
        v
按 type/deep_think/fixed response 选择执行分支
        |
        |-- agent_data_access -> DataAccessAgent -> AIChatService.chatWithSystemPrompt
        |-- agent_mcp         -> McpAgent -> AIChatService.chatWithSystemPromptAndTools
        |-- agent_data_visualization -> DataVisualizationAgent.chat
        |-- deep_think=true   -> AIChatService.deepThinkingChat
        |-- 默认 ask/普通聊天 -> AIChatService.chat
        |
        v
Flux<String> 模型输出
        |
        |-- response_format=events -> NDJSON: delta/done/error
        |-- 否则 -> 纯文本流
        |
        v
保存 AI 回复到 ChatSession.messages
```

## `/api/v1/dih/chat` 主流程

`ChatController.chat` 是当前 AI 会话最核心的入口。

### 1. 响应格式

请求体中 `response_format` 为 `events` 时，后端返回 `application/x-ndjson;charset=UTF-8`，每行是一个 JSON 事件。

```json
{"event":"delta","content":"模型增量内容"}
{"event":"done","message":{"sender":"ai","content":"完整内容","parts":[]}}
{"event":"error","message":"错误消息"}
```

前端当前主要使用事件流模式。这样做的好处是最终 `done` 事件会带上后端解析好的完整 `Message`，包括 `parts`、`type`、`id` 等字段。

未使用 `events` 时，后端只返回纯文本流，并在流结束时保存 AI 回复；这种模式不会返回最终结构化消息对象。

### 2. 请求校验

Controller 会做几类前置检查：

- `type` 如果以 `agent` 开头，只允许已注册且已启用的内置 agent 类型，例如 `agent_data_access`、`agent_data_visualization`、`agent_analysis`、`agent_dispose`、`agent_report`。
- 模型列表来自 `AIBaseService.getModels()`，底层读取 `src/main/resources/models.yaml`。当前模型白名单包括 `auto` 和 `${OPENAI_CHAT_MODEL}` 解析后的值。
- `model` 为空、`auto` 或 `x-sage-v1` 时最终会转成 `null`，交给 Spring AI 默认模型配置。
- 用户消息为空但有附件时，会自动使用“请分析上传的附件内容。”作为本轮消息。
- 消息和附件都为空时直接返回错误。

### 3. 附件上下文拼接

`ChatAttachmentService.appendAttachmentContext` 会把本轮附件转成模型可见上下文：

- 文本类附件会读取 UTF-8 文本，最多读取前 80,000 字符，并用代码块追加到 prompt。
- 图片附件会在 prompt 中说明图片将作为 `image_url` 输入发送给支持视觉能力的模型。
- 其他不支持解析的附件只提供文件名、大小、类型，并要求模型不要声称已经读取内部内容。

需要注意：保存到 `ChatSession.messages` 的用户消息仍是用户原始文本和附件元信息；发送给模型的是追加了附件内容后的 `prompt`。

### 4. 会话创建与用户消息保存

后端以 `chat_id` 查找当前用户自己的 `ChatSession`：

- 不存在时创建新会话，标题使用用户本轮消息，`messages` 初始化为用户消息数组。
- 已存在时把本轮用户消息 append 到原有 `messages` JSON 中，再调用 `ChatSessionService.update` 保存。

用户隔离依赖 `create_by == currentUser.id`。如果相同 `session_id` 属于其他用户，当前用户会被视为找不到该会话。

## 聊天分支

### 普通聊天

默认分支调用 `AIChatService.chat`。

主要逻辑：

1. 如果本轮有图片附件，并且配置了 `spring.ai.openai.base-url` 和 `spring.ai.openai.api-key`，走原生 OpenAI 兼容流式接口。
2. 否则走 Spring AI `ChatClient`。
3. `ChatClient` 默认注入问答系统提示词 `askSystemPromptTemplate`。
4. 通过 `MessageChatMemoryAdvisor` 按 `chat_id` 注入多轮记忆。
5. 如果 `app.ai.embedding.enabled=true`，额外挂 `QuestionAnswerAdvisor` 从 Redis vector store 做 RAG，topK 为 6。
6. 返回 `Flux<String>` 增量文本。

普通聊天使用 `AIChatService` 内部的 `MessageWindowChatMemory`，底层仓库为 MySQL JDBC chat memory。

### 深度思考

`deep_think=true` 时调用 `AIChatService.deepThinkingChat`。

当前有两种处理方式：

- 如果模型是 Qwen3 系列，或本轮有图片附件，并且可以使用原生 OpenAI 流式接口，则走 `nativeOpenAiChat(..., deepThinking=true)`。
- 否则走 Spring AI `ChatClient`，并追加 `deepThinkPromptTemplate`，要求模型输出 `<think>...</think>` 后再回答。

后端会用 `ReasoningContentAdvisor` 读取模型返回 metadata 中的 `reasoningContent`，包装成 `<think>...</think>` 插入文本流。原生 OpenAI 分支则直接解析流式 delta 中的 `reasoning_content` 或 `reasoningContent` 字段，并在输出中自动补 `<think>` 标签。

最终保存 AI 消息时，`ChatMessagePartParser` 会把 `<think>` 内容转为 `thinking` part。如果用户请求了深度思考但模型没有返回可解析思考内容，后端会补一个“已完成深度思考，当前模型未返回可展示的思考过程。”的 thinking 片段。

### 数据接入 Agent

`type=agent_data_access` 时调用 `DataAccessAgent`。

它本身不实现复杂工具链，主要是：

1. 使用 `agentDataAccessSystemPromptTemplate` 替换默认系统提示词。
2. 通过 `SkillService.buildRequiredSkillPrompt` 加载 `data-access-agent` skill。
3. 调用 `AIChatService.chatWithSystemPrompt` 复用普通聊天能力。

因此它仍支持普通聊天记忆、附件文本、图片原生流式、可选 RAG。

### MCP Agent

`type=agent_mcp` 时调用 `McpAgent`。

主要逻辑：

1. 先检查 `McpClientService.hasAvailableTools()`，没有可用工具时直接返回提示。
2. 构造 MCP 专用 system prompt，要求参数不足先追问，有副作用工具先请求确认。
3. 把已连接服务与工具列表拼进 system prompt。
4. 使用 `AIChatService.chatWithSystemPromptAndTools`，通过 `ToolCallbackProvider` 把 MCP 工具暴露给 Spring AI。

注意：MCP Agent 的 system prompt 要求“副作用动作先确认”，但当前工具确认的强制性主要依赖模型遵守提示；后端还有 `zenvis:confirm` 结构化片段和 `/chat/action-decision` 记录接口，但这只是记录用户选择，不会自动执行或阻断工具调用。

### 数据可视化 Agent

`type=agent_data_visualization` 时调用 `DataVisualizationAgent.chat`。这条链路由 `PromptDrivenAgentRuntime` 驱动，后端为数据可视化 Agent 注入只读 retrieval MCP 工具，返回文本或 Markdown 流式内容。

核心流程：

```text
用户问题
  |
  v
解析数据可视化 Agent 的 retrieval MCP 工具白名单
  |
  v
拼接数据可视化 system prompt、Skill prompt 和工具说明
  |
  v
调用 Spring AI ChatClient 流式对话
  |
  v
模型按需调用 retrieval 查询、统计、趋势或详情工具
  |
  v
输出普通文本/Markdown 分析，MessageType.TEXT
```

数据可视化 Agent 不直接访问数据库、不生成查询语句、不执行写入类 MCP 工具。

## 消息结构与渲染

### Message

`ChatSession.messages` 中的每条消息最终对应 `model.dih.Message`：

| 字段 | 说明 |
| :--- | :--- |
| `id` | 消息 UUID，后端创建 |
| `sender` | `user` 或 `ai` |
| `content` | 完整文本内容；图表消息时是 ECharts JSON |
| `time` | 创建时间 |
| `type` | `text`、`chart`、`code`、`table`、`image` 等 |
| `parts` | 结构化片段，主要给前端富渲染 |
| `attachments` | 用户消息携带的附件元信息 |

### parts 解析规则

`ChatMessagePartParser` 负责把 AI 回复拆成多个 part：

| 输入形式 | part 类型 | 说明 |
| :--- | :--- | :--- |
| 普通文本 | `markdown` | 默认渲染 Markdown |
| 普通代码围栏，例如 `java` | `code` | 保存 `language` 和代码内容 |
| `<think>...</think>` | `thinking` | 保存思考过程，状态为 `completed` |
| `zenvis:notice` 代码围栏中的 JSON | `notice` | 解析标题、内容、level、metadata |
| `zenvis:confirm` 代码围栏中的 JSON | `confirm` | 解析为待确认片段，默认 `status=pending` |
| `MessageType.CHART` | `chart` | 整体作为图表内容 |

如果 `zenvis:notice` 或 `zenvis:confirm` 的 JSON 非法，会回退成普通 Markdown。

### 确认动作记录

`POST /api/v1/dih/chat/action-decision` 用来记录 confirm 片段的用户选择。

请求字段：

- `chat_id`
- `message_id`
- `part_id`
- `decision`，只支持 `approved` 或 `rejected`

后端会在对应 `Message.parts` 中找到 `type=confirm` 的 part，并更新 `status`。这个接口当前只改会话历史，不触发后续业务动作。

## 会话管理接口

`ChatSessionController` 提供：

| 接口 | 说明 |
| :--- | :--- |
| `POST /api/v1/dih/chat-session/add` | 创建会话 |
| `DELETE /api/v1/dih/chat-session/{id}` | 删除单个会话 |
| `DELETE /api/v1/dih/chat-session/bulk/{ids}` | 批量删除 |
| `POST /api/v1/dih/chat-session/{id}/update` | 更新标题、置顶、配置等 |
| `GET /api/v1/dih/chat-session/list/pin` | 当前用户置顶会话 |
| `GET /api/v1/dih/chat-session/list` | 当前用户分页会话 |
| `GET /api/v1/dih/chat-session/{id}/view` | 按主键查看 |
| `GET /api/v1/dih/chat-session/{sessionId}/session?type=...` | 按业务会话 ID 查看；不存在时返回默认开场白 |

`/{sessionId}/session` 不会自动创建数据库记录。它只在没有历史时返回一个临时 `ChatSessionVo`，里面放对应 agent 类型的开场白。真正的会话记录会在用户第一次调用 `/chat` 时创建。

## 配置与运行依赖

### 大模型配置

主要配置项在 `application-*.properties`：

| 配置 | 说明 |
| :--- | :--- |
| `spring.ai.openai.base-url` | OpenAI 兼容服务地址；原生流式图片/深度思考分支也用它 |
| `spring.ai.openai.api-key` | API Key |
| `spring.ai.openai.chat.options.model` | 默认聊天模型 |
| `app.ai.embedding.enabled` | 是否启用 embedding/RAG |
| `spring.ai.vectorstore.redis.*` | Redis Vector Store 配置 |
| `spring.ai.chat.memory.repository.jdbc.initialize-schema` | 是否初始化 Spring AI chat memory 表 |
| `app.paths.session.workspace` | 附件上传和会话工作空间根路径 |

如果没有有效 API Key，`LocalAiFallbackConfiguration` 会注入本地 fallback `ChatModel`，聊天会返回“AI 对话暂不可用”的提示，避免应用启动失败。

### RAG 与向量召回

普通聊天的 RAG 只在 `app.ai.embedding.enabled=true` 时启用，通过 `QuestionAnswerAdvisor` 查询 Redis vector store。

数据可视化 Agent 依赖 retrieval MCP 工具获取业务数据；普通聊天的 RAG 仍由 Redis vector store 支撑。

### JSON 字段命名

后端全局 `ObjectMapper` 使用 snake_case 策略。前端当前发送的是 `chat_id`、`deep_think`、`online_search`、`response_format`，返回也主要按 snake_case 解析。

已有部分接口文档示例仍写成 `chatId`、`deepThink`，阅读时要以当前前端和 Jackson 配置为准。

## 需要注意的实现细节

1. `ChatSession.messages` 和 Spring AI `ChatMemory` 是两套数据。界面历史和模型记忆可能不同步，排查“模型不记得上下文”时不要只看 `t_ai_chat_session.messages`。
2. 普通 Spring AI 分支通过 `MessageChatMemoryAdvisor` 自动维护记忆；原生 OpenAI 图片/深度思考分支通过 `saveNativeChatMemory` 手动维护；业务 Agent 由 `PromptDrivenAgentRuntime` 维护对应记忆。
3. `agent_data_visualization` 会调用只读 retrieval MCP 工具获取真实数据。慢查询或 LLM 慢响应会直接影响接口首包时间。
4. `online_search` 目前只保存到会话字段，聊天主流程没有看到实际在线搜索逻辑。
5. `response_format=events` 时，`done` 事件会保存并返回最终 AI 消息；如果流中途异常，当前只返回 `error` 事件，不会保存部分 AI 回复。
6. 非 events 的纯文本模式会在 `doOnComplete` 保存 AI 回复，但不会返回最终 message id/parts；前端如果需要结构化渲染，应继续使用 events。
7. 附件上传限制是 30MB；文本附件只截取前 80,000 字符；图片输入只有在走原生 OpenAI 兼容接口时才会作为 `image_url` 发送。
8. 深度思考显示依赖 `<think>` 标签或模型 metadata 的 `reasoningContent`。不同模型字段兼容性不同，当前代码只显式处理 DeepSeek reasoning metadata 和 Qwen3 原生 `reasoning_content`。
9. MCP Agent 的副作用确认目前主要靠提示词约束和 `confirm` 片段记录，不是后端强制的事务门禁。
10. `ChatSession` 主键实体字段是 `Integer`，但 Controller/Service 的部分入参使用 `Long`；当前基础仓库使用 `Serializable` 所以能工作，后续如果收紧泛型需要统一类型。
11. `ChatSessionService.update` 捕获异常后返回 `false`，部分调用方只记录日志或继续流程。保存会话失败时，模型响应可能已经返回给前端但历史没有落库。
12. 日志工具 `LlmLogHelper` 会记录 LLM prompt 和 response，并会对常见敏感字段脱敏；但 prompt 本身可能包含用户上传文本附件内容，生产环境需要注意日志留存策略。

## 快速定位问题

| 现象 | 优先检查 |
| :--- | :--- |
| 前端有历史，模型却不接上下文 | Spring AI chat memory 表中是否有同一 `chat_id` 的记录；是否走了不会写 memory 的异常路径 |
| 图片附件没有被模型看到 | 是否配置了 `spring.ai.openai.base-url` 和 `api-key`；是否走了原生 OpenAI 分支 |
| 数据可视化 Agent 数据查询失败 | retrieval MCP 工具是否启用、可查询实体和字段是否存在、工具返回是否为空或报错 |
| 图表没有渲染 | AI 消息 `MessageType` 是否为 `CHART`，`content` 是否为 ECharts JSON，最终 `parts` 是否有 `chart` |
| MCP Agent 不会调用工具 | MCP 服务是否 enabled/connected，`McpClientService.hasAvailableTools()` 是否为 true，工具名是否进入 system prompt |
| 深度思考没有过程 | 模型是否支持 reasoning metadata 或 `<think>` 输出；是否触发了后端 fallback thinking part |
