# AI 会话实现说明

本文面向需要理解 ZenVis Backend 当前 AI 会话实现的开发者，重点说明 `/api/v1/dih/chat`、会话管理、普通问答、深度思考、附件、统一 MCP 工具审批和业务 Agent 的主要逻辑。

## 代码范围

| 模块 | 关键文件 | 作用 |
| :--- | :--- | :--- |
| Chat 接口入口 | `src/main/java/com/coolxer/controller/dih/ChatController.java` | 接收 Chat、模型列表、附件和业务动作请求 |
| Chat 应用服务 | `src/main/java/com/coolxer/service/dih/DihChatApplicationService.java` | 校验请求、维护会话、选择 Agent、合并 MCP 事件、保存最终 AI 消息 |
| 会话管理接口 | `src/main/java/com/coolxer/controller/dih/ChatSessionController.java` | 会话增删改查、置顶列表、首次进入会话时返回默认开场白 |
| 普通聊天服务 | `src/main/java/com/coolxer/service/dih/AIChatService.java` | 调用 Spring AI ChatClient 或原生 OpenAI 兼容接口，处理 RAG、记忆、深度思考、附件图片 |
| 会话服务 | `src/main/java/com/coolxer/service/dih/impl/ChatSessionServiceImpl.java` | 读写 `t_ai_chat_session`，按当前用户隔离会话 |
| 会话实体 | `src/main/java/com/coolxer/dao/mysql/entity/ChatSession.java` | 保存会话标题、类型、消息 JSON、置顶、深度思考等状态 |
| 结构化消息解析 | `src/main/java/com/coolxer/service/dih/ChatMessagePartParser.java` | 将 AI 回复解析为 markdown、code、thinking、notice、confirm、chart 等片段 |
| 附件服务 | `src/main/java/com/coolxer/service/dih/ChatAttachmentService.java` | 上传附件、读取文本附件、图片转 OpenAI image_url 输入 |
| 数据可视化 Agent | `src/main/java/com/coolxer/service/dih/agent/DataVisualizationAgent.java` | 通过查询工具和受控配置、看板、菜单工具完成可视化分析与落地 |
| 报表制作 Agent | `src/main/java/com/coolxer/service/dih/agent/ReportAgent.java` | 生成和改写报表正文，输出可同步到右侧编辑器的结构化报表文档 |
| 报表示例服务 | `src/main/java/com/coolxer/service/dih/ReportDemoResponseService.java` | 为报表演示示例提供内置模板，命中后不请求后台模型 |
| MCP 工具注入 | `src/main/java/com/coolxer/service/dih/mcp/AgentMcpToolService.java` | 按 Agent scope 注入本地和外部工具及工具提示词 |
| MCP 策略与审批 | `src/main/java/com/coolxer/service/dih/mcp/McpApprovalService.java` | 执行 ALLOW/ASK/DENY，会话授权、审批状态和调用审计 |
| 记忆配置 | `src/main/java/com/coolxer/configuration/ai/SpringAiChatMemoryConfiguration.java` | 初始化 Spring AI JDBC chat memory，并使用 MySQL 存储 |

## 总体设计

当前 AI 会话有两层“会话数据”：

| 数据 | 主要用途 | 写入位置 | 读取位置 |
| :--- | :--- | :--- | :--- |
| `ChatSession.messages` | 前端展示历史消息 | `DihChatApplicationService` 的用户消息和 AI 结果保存逻辑 | `ChatSessionController.sessionInfo/list/view` |
| Spring AI `ChatMemory` | 给模型提供多轮上下文 | Spring AI `MessageChatMemoryAdvisor` 自动写入；原生 OpenAI 分支和业务 Agent 运行时手动写入 | `AIChatService`、`PromptDrivenAgentRuntime` |

这两层不是同一个表，也不是同一份 JSON。前端历史主要依赖 `t_ai_chat_session.messages`，模型记忆主要依赖 Spring AI JDBC memory 表。

整体请求链路如下：

```text
前端 POST /api/v1/dih/chat
        |
        v
ChatController.chat
        |
        v
DihChatApplicationService.chat
        |
        |-- 校验模型、消息、agent 类型权限
        |-- 读取/拼接附件上下文
        |-- 创建或更新 ChatSession.messages，先保存用户消息
        |
        v
按 type/deep_think/fixed response 选择执行分支
        |
        |-- agent_data_access -> DataAccessAgent -> AIChatService.agentChat
        |-- agent_data_visualization -> DataVisualizationAgent.chat
        |-- agent_report -> ReportAgent.chat 或 ReportDemoResponseService 内置模板
        |-- ask + deep_think=true -> AIChatService.qaChat(..., true)
        |-- 默认 ask/普通聊天 -> AIChatService.qaChat(..., false)
        |
        v
Flux<String> 模型输出
        |
        |-- response_format=events -> NDJSON: delta/approval_required/approval_updated/done/error
        |-- 否则 -> 纯文本流
        |
        v
保存 AI 回复到 ChatSession.messages
```

## `/api/v1/dih/chat` 主流程

`ChatController.chat` 负责 HTTP 入口和响应类型，核心会话编排位于 `DihChatApplicationService.chat`。

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

- `type` 如果以 `agent` 开头，只允许已注册且已启用的内置 agent 类型，例如 `agent_data_access`、`agent_data_visualization`、`agent_data_analysis`、`agent_config_management`、`agent_report`。
- 模型列表来自 `AIBaseService.getModels()`，综合默认模型与 OpenAI 兼容 `/v1/models` 返回，并总是提供 `auto`。
- `model` 为空、`auto` 或历史自动值时，由 `AIBaseService.resolveChatModel` 按当前可用目录选择模型。
- 用户消息为空但有附件时，会自动使用“请分析上传的附件内容。”作为本轮消息。
- 消息和附件都为空时直接返回错误。
- `agent_data_analysis`、`agent_config_management` 和 `agent_report` 的固定演示提示词会在模型校验前命中对应的内置响应服务，直接返回预置结果；这些链路不会请求后台模型，也不会调用标题模型。

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

默认分支调用 `AIChatService.qaChat(..., deepThinking=false)`。普通问答不会解析 Skill，也不会调用 `AgentMcpToolService`。

主要逻辑：

1. 通过 `RagContextService` 从公共 Redis vector store 检索 topK 6，并将结果格式化为不可执行的参考资料。
2. 如果 embedding/Redis 不可用，记录降级原因并继续普通问答。
3. 如果本轮有图片附件，并且配置了 `spring.ai.openai.base-url` 和 `spring.ai.openai.api-key`，走原生 OpenAI 兼容流式接口。
4. 否则走 Spring AI `ChatClient`。
5. 两条模型路径使用相同的问答系统提示词和 RAG 上下文。
6. 通过 `MessageChatMemoryAdvisor` 或原生分支的手动记忆维护多轮会话。
7. 返回 `Flux<String>` 增量文本。

普通聊天使用 `AIChatService` 内部的 `MessageWindowChatMemory`，底层仓库为 MySQL JDBC chat memory。

### 深度思考

`type=ask&deep_think=true` 时调用 `AIChatService.qaChat(..., deepThinking=true)`。它与普通问答一样使用 RAG，且不会获得 MCP 工具。

当前有两种处理方式：

- 模型调用前先完成 RAG 检索并把参考资料拼入系统提示词。
- 如果模型是 Qwen3 系列，或本轮有图片附件，并且可以使用原生 OpenAI 流式接口，则走 `nativeOpenAiChat(..., deepThinking=true)`。
- 否则走 Spring AI `ChatClient`，并追加 `deepThinkPromptTemplate`，要求模型输出 `<think>...</think>` 后再回答。

后端会用 `ReasoningContentAdvisor` 读取模型返回 metadata 中的 `reasoningContent`，包装成 `<think>...</think>` 插入文本流。原生 OpenAI 分支则直接解析流式 delta 中的 `reasoning_content` 或 `reasoningContent` 字段，并在输出中自动补 `<think>` 标签。

最终保存 AI 消息时，`ChatMessagePartParser` 会把 `<think>` 内容转为 `thinking` part。如果用户请求了深度思考但模型没有返回可解析思考内容，后端会补一个“已完成深度思考，当前模型未返回可展示的思考过程。”的 thinking 片段。

### 数据接入 Agent

`type=agent_data_access` 时调用 `DataAccessAgent`。

它本身不实现复杂工具链，主要是：

1. 使用 `agentDataAccessSystemPromptTemplate` 替换默认系统提示词。
2. 通过 `SkillService.buildAgentSkillPrompt` 严格加载显式绑定的 `data-access-agent` Skill。
3. 调用 `AIChatService.agentChat`，按 Agent scope 注入允许的 MCP 工具。

因此它仍支持聊天记忆、附件文本和图片原生流式，但不会检索公共 RAG 文档。

### 统一 MCP 工具与内联审批

MCP 不再作为独立 `mcp_agent` 入口。只有业务 Agent 通过 `AgentMcpToolService.resolve(type)` 按 scope 获取可见的本地和外部工具；普通问答始终使用空工具上下文。旧 `mcp_agent` 请求兼容映射为无工具的 `ask`，并保留对历史别名 `agent_mcp` 的兼容。

工具回调由 `McpApprovalService` 强制执行全局 `ALLOW / ASK / DENY` 策略，不依赖模型自觉遵守提示词。命中 `ASK` 时：

1. 保持当前 Chat Flux 打开。
2. 向 NDJSON 流发送 `approval_required`。
3. 前端在当前 AI 消息中插入 `mcp-approval` part。
4. 用户选择单次允许、会话允许或拒绝。
5. 后端发送 `approval_updated`，唤醒工具回调并继续模型工具循环。

工具参数和返回结果由 MCP 调用日志以 JSON 代码块展示，审批卡片不重复展示 payload。最终审批 part 与模型消息按发生顺序保存。

### 数据可视化 Agent

`type=agent_data_visualization` 时调用 `DataVisualizationAgent.chat`。这条链路由 `PromptDrivenAgentRuntime` 驱动，后端按专用本地白名单注入工具，返回文本或 Markdown 流式内容。它不会加载外部 MCP。

核心流程：

```text
用户问题
  |
  v
解析数据可视化 Agent 的本地工具白名单
  |
  v
拼接数据可视化 system prompt、Skill prompt 和工具说明
  |
  v
调用 Spring AI ChatClient 流式对话
  |
  v
模型按需调用查询、统计、趋势、详情或受控配置落地工具
  |
  v
输出普通文本/Markdown 分析，MessageType.TEXT
```

白名单包括 Retrieval/实体查询，以及 `config_*`、`dashboard_*`、`menu_*` 中明确允许的读取、创建和应用操作；不包含实体更新、删除或任意外部 MCP。配置新增/应用、看板创建和菜单创建等写入动作通过 `@McpToolApproval(ASK, HIGH)` 进入审批。Agent 仍不直接访问数据库，也不生成任意 SQL。

### 数据分析与配置管理 Agent

`type=agent_data_analysis` 和 `type=agent_config_management` 的普通请求分别由 `DataAnalysisAgent`、`ConfigManagementAgent` 处理，并继续执行各自的真实三阶段流程。

两个开场白示例采用与其他演示智能体一致的前置固定响应机制：

- 数据分析示例由 `DataAnalysisDemoResponseService` 依次返回内置数据集、统计分析结果和报告。
- 配置管理示例由 `ConfigManagementDemoResponseService` 依次返回内置配置记录、试验场验证结果和演示范围内的模拟生效结果；不会写入实际系统配置。
- 命中条件是对应完整示例提示词的精确匹配，后续阶段通过演示标题、记录 ID 和会话消息继续识别，不拦截其他新会话请求。
- `DihChatApplicationService.chat` 在模型支持校验、模型解析、MCP 解析和 Agent 调用之前完成示例分流。
- `ChatTitleService` 直接返回固定演示标题，避免标题生成调用模型。
- 非示例请求仍进入真实 Agent；数据分析缺少分析服务时停止并提示能力缺失，配置管理的真实生效仍要求验证、用户确认、审批和读回一致。

### 报表制作 Agent

`type=agent_report` 时进入报表制作链路。它的目标不是只返回普通聊天文本，而是生成可写入右侧文档编辑器的报表正文。

主要规则：

1. `SystemPromptConfig.agentReportSystemPromptTemplate` 要求完整报表优先输出 Markdown，必要时可输出完整 HTML。
2. 完整报表或整篇重写必须在回答末尾输出 `zenvis:report-document-config` 围栏，围栏内只放最终 Markdown 或 HTML 正文。
3. `ChatMessagePartParser` 会把该围栏解析成 `report-document` part，并提取标题、格式、版本、更新时间和大纲。
4. `DihChatApplicationService.mergeStructuredExtraData` 会把 `report-document` 合并到 `ChatSession.extraData.report`，包括 `currentDocument`、`documents`、`artifacts`。
5. 前端 `view-center.vue` 发布 `dihReportRecordsUpdated`，右侧 `view-right-report.vue` 读取当前文档并注入 wangEditor。

报表智能体的新会话开场白由 `ChatSessionController.buildPrologueMessage` 生成。开场白包含 `prompt-suggestions`，示例包括用户事件分析报告、运营周报、风险事件复盘和可视化结论归档报告。

#### 报表示例模板

报表示例命中后走 `ReportDemoResponseService`：

- 命中条件是固定示例提示词的精确匹配，避免拦截普通用户需求。
- 在 `DihChatApplicationService.chat` 中先于模型校验执行，因此无模型配置时也能演示。
- `ChatTitleService` 对报表示例返回固定标题“报表生成演示”，避免新会话标题生成调用模型。
- 未命中示例时，仍按普通 `ReportAgent.chat` 走模型生成。

#### 右侧报表工作台

右侧报表面板由前端 `view-right-report.vue` 实现：

- 顶部整篇快捷操作：生成初稿、继续写、正式语气、摘要、标题、结论。
- 文档编辑器：基于 wangEditor 展示和编辑当前报表。
- 大纲：根据 `h1` 至 `h6` 自动生成章节导航。
- 保存：通过 `/api/v1/dih/chat-session/{id}/update` 更新 `extra_data`。
- 归档：把当前文档复制为 artifact 版本。
- 导出：前端直接下载 Markdown 或 HTML。
- 选区右键：选中文档片段后右键展示润色、缩写、扩写，只替换选区。

选区改写不要求 AI 返回完整 `report-document-config`。前端会通过 `dihReportQuickActionRequested(target=selection)` 发送只包含选中片段的请求；中间对话完成后触发 `dihReportSelectionRewriteCompleted`，右侧恢复缓存选区并替换为 AI 返回片段。

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
| `zenvis:mcp-approval` 内部标记 | `mcp-approval` | 保存 MCP 工具、决策、范围和最终状态 |
| `zenvis:report-document-config` 代码围栏 | `report-document` | 解析为报表文档，自动同步到 `extraData.report` |
| `prompt-suggestions` part | `prompt-suggestions` | 用于智能体开场白展示可点击示例提示词 |
| `MessageType.CHART` | `chart` | 整体作为图表内容 |

如果 `zenvis:notice` 或 `zenvis:confirm` 的 JSON 非法，会回退成普通 Markdown。

### 确认动作记录

`POST /api/v1/dih/chat/action-decision` 用来记录 confirm 片段的用户选择。

请求字段：

- `chat_id`
- `message_id`
- `part_id`
- `decision`，业务动作根据 part 类型支持 `approved`、`rejected`、`dispose`、`ignore`、`continue`、`apply_config`、`abandon`、`revise` 或 `submitted`

后端会在对应 `Message.parts` 中找到相应 part，并更新 `status`。这个接口只用于已完成保存的业务动作；MCP 审批必须使用 `/api/v1/dih/mcp/approvals/{requestId}/decision`。

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

普通问答和深度问答的 RAG 只在 `app.ai.embedding.enabled=true` 时启用，通过 `RagContextService` 查询 Redis vector store。检索失败采用 fail-open，不阻断模型问答。

全部业务 Agent 均不检索该公共文档索引。数据可视化等 Agent 依赖显式 Skill 和允许范围内的 retrieval/MCP 工具获取业务数据。

### JSON 字段命名

后端全局 `ObjectMapper` 使用 snake_case 策略。前端当前发送的是 `chat_id`、`deep_think`、`online_search`、`response_format`，返回也主要按 snake_case 解析。

已有部分接口文档示例仍写成 `chatId`、`deepThink`，阅读时要以当前前端和 Jackson 配置为准。

## 需要注意的实现细节

1. `ChatSession.messages` 和 Spring AI `ChatMemory` 是两套数据。界面历史和模型记忆可能不同步，排查“模型不记得上下文”时不要只看 `t_ai_chat_session.messages`。
2. 普通 Spring AI 分支通过 `MessageChatMemoryAdvisor` 自动维护记忆；原生 OpenAI 图片/深度思考分支通过 `saveNativeChatMemory` 手动维护；业务 Agent 由 `PromptDrivenAgentRuntime` 维护对应记忆，但不使用 RAG。
3. `agent_data_visualization` 会调用白名单内的查询或受控配置工具。慢查询、审批等待或 LLM 慢响应都会影响接口完成时间。
4. `online_search` 目前只保存到会话字段，聊天主流程没有看到实际在线搜索逻辑。
5. `response_format=events` 时，`done` 事件会保存并返回最终 AI 消息；如果流中途异常，当前只返回 `error` 事件，不会保存部分 AI 回复。
6. 非 events 的纯文本模式会在 `doOnComplete` 保存 AI 回复，但不会返回最终 message id/parts；前端如果需要结构化渲染，应继续使用 events。
7. 附件上传限制是 30MB；文本附件只截取前 80,000 字符；图片输入只有在走原生 OpenAI 兼容接口时才会作为 `image_url` 发送。
8. 深度思考显示依赖 `<think>` 标签或模型 metadata 的 `reasoningContent`。不同模型字段兼容性不同，当前代码只显式处理 DeepSeek reasoning metadata 和 Qwen3 原生 `reasoning_content`。
9. MCP 副作用工具由后端统一策略回调强制门禁；`DENY`、拒绝、超时或取消时底层工具不得执行。
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
| Agent 不会调用 MCP 工具 | 工具是否可用、是否被 `DENY`、Agent scope 是否包含该服务、工具提示词是否已注入 |
| 审批卡片不出现 | 有效策略是否为 `ASK`，当前 chatId 是否已有会话授权，是否使用 `response_format=events` |
| 深度思考没有过程 | 模型是否支持 reasoning metadata 或 `<think>` 输出；是否触发了后端 fallback thinking part |
