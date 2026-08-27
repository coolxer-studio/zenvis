# AI 问答与会话

本文说明数智中心普通问答、深度思考、模型选择、会话历史、附件和流式消息的当前行为，并提供使用与运维排障方法。

## 使用前提

| 依赖 | 用途 | 不可用时 |
| --- | --- | --- |
| ZenVis 后端与 MySQL | 会话、消息、模型调用编排 | 数智中心不可正常使用 |
| OpenAI 兼容 Chat 服务 | 普通问答和深度思考 | 自定义问答失败，确定性演示仍可能可用 |
| Redis Stack 与 embedding | RAG 语义检索 | 普通问答降级为无 RAG 模式 |
| 浏览器 `/zenvis` 代理 | 同源访问后端 API | 页面可能出现 404 或认证失效 |

先在登录会话或配置好的 Bearer Token 下检查：

```bash
curl -H "Authorization: Bearer <API_BEARER_TOKEN>" \
  http://localhost:11001/api/v1/dih/health

curl -H "Authorization: Bearer <API_BEARER_TOKEN>" \
  http://localhost:11001/api/v1/dih/model/list
```

模型服务未配置不影响后端其他平台能力启动，但真正请求模型时会失败。

## 普通问答与深度思考

### 普通问答

普通问答使用 `type=ask`：

- 可以从公共 RAG 知识库召回参考文档；
- 使用会话历史维持多轮上下文；
- 不加载 Skill；
- 不注入本地或外部 MCP 工具；
- RAG 不可用时继续无知识库问答。

### 深度思考

深度思考只对 `type=ask` 生效。前端发送 `deep_think=true` 后，后端根据模型能力读取原生 reasoning 字段或请求模型输出可解析的 `<think>` 内容。

业务智能体和 `skill:<skillId>` 入口有各自的工作流与工具边界，不能通过 `deep_think=true` 改变这些边界。

界面显示的思考片段取决于模型是否返回可展示内容。不能把页面没有展示思考过程理解为请求未执行。

## 模型选择

模型列表综合：

- `spring.ai.openai.chat.options.model` 配置的默认模型；
- OpenAI 兼容服务 `/v1/models` 返回的模型；
- 固定的 `auto` 选项。

`model` 为空、为 `auto` 或为兼容的历史自动值时，后端在运行时选择可用模型。`auto` 不是模型服务名称，也不保证一定能找到可调用模型。

主要配置：

| 配置 | 说明 |
| --- | --- |
| `spring.ai.openai.base-url` | OpenAI 兼容服务地址 |
| `spring.ai.openai.api-key` | 模型服务 API Key |
| `spring.ai.openai.chat.options.model` | 默认 Chat 模型 |
| `app.ai.openai.force-http1` | 是否强制 OpenAI 兼容调用使用 HTTP/1.1 |

不要在文档、日志截图或前端环境变量中复制真实 Key。

## 会话、页面历史与模型记忆

ZenVis 同时维护两类数据：

| 数据 | 用途 |
| --- | --- |
| ZenVis 会话消息 | 页面历史、附件元信息、结构化消息、业务卡片和右侧工作台状态 |
| Spring AI Chat Memory | 提供给模型的多轮上下文 |

两者不是同一份数据。典型表现：

- 页面能看到旧消息，不代表这些消息全部仍在当前模型上下文中；
- 上下文压缩或截断不应删除页面历史；
- 删除会话会影响该会话关联的授权和工作台数据；
- 相同 `chat_id` 仍按当前用户隔离，其他用户不能读取。

会话支持列表、置顶、重命名、删除和重新打开。接口中的数据库记录 `id` 与业务 `sessionId/chat_id` 不是同一个标识，调用接口时不能混用。

## 上下文预算

当前生产基线：

| 配置 | 默认值 | 作用 |
| --- | ---: | --- |
| `app.ai.dih.context.window-tokens` | `102400` | 模型上下文窗口预算 |
| `app.ai.dih.context.output-reserve-tokens` | `4096` | 为模型输出预留 |
| `app.ai.dih.context.safety-margin-tokens` | `4096` | 安全余量 |
| `app.ai.dih.context.max-history-tokens` | `24000` | 历史消息预算 |
| `app.ai.dih.context.summary-tokens` | `2048` | 历史摘要预算 |
| `app.ai.dih.context.recent-turns` | `6` | 优先保留的最近轮次 |
| `app.ai.dih.context.max-attachment-chars-per-file` | `12000` | 单个文本附件进入本轮 Prompt 的字符上限 |
| `app.ai.dih.context.max-attachment-chars` | `24000` | 本轮所有文本附件的合计字符上限 |

系统会在窗口预算内保留系统规则、当前请求、近期对话和摘要。达到预算时旧历史或附件内容可能被截断；这属于受控行为，不应通过无限提高配置掩盖超长会话。

## 附件

### 上传与类型

- 单个聊天附件最大 30 MB。
- 文本类附件按 UTF-8 读取，非法字节使用替换字符。
- 图片支持 PNG、JPEG、WebP、GIF 和 BMP，并检查实际文件内容。
- 其他文件只向模型提供文件名、大小和类型，不会伪装成已经读取正文。

平台级 multipart 上限可以更大，但聊天附件服务仍执行独立的 30 MB 限制。

### 文本上下文

默认每个文本附件最多读取前 12,000 字符，本轮所有文本附件合计最多 24,000 字符。被截断的附件会带明确提示。

页面历史和 Chat Memory 只保留稳定附件引用与元信息，不持续保存每轮展开后的完整附件正文。

### 图片输入

图片只有在当前模型调用走支持图片的 OpenAI 兼容路径时，才作为 `image_url` 内容发送。模型不支持视觉、图片校验失败或模型配置不完整时，不能声称已经识别图片内容。

附件只允许当前上传用户访问；图片预览接口同时校验用户和媒体类型。

## 事件流与结构化消息

前端当前使用 `response_format=events`。后端返回 `application/x-ndjson`，每行是一个独立 JSON 事件：

| 事件 | 用途 |
| --- | --- |
| `delta` | 文本增量 |
| `approval_required` | MCP 工具等待审批 |
| `approval_updated` | 审批或工具状态更新 |
| `done` | 本轮完成，携带最终结构化消息 |
| `error` | 本轮失败 |

客户端应逐行解析并忽略未知事件，不能把整个响应当成一个 JSON。未使用 `events` 时返回纯文本流，不包含最终结构化 `Message`。

最终消息可以包含：

| part | 用途 |
| --- | --- |
| `markdown`、`code` | 普通正文和代码 |
| `thinking` | 可展示的思考内容 |
| `notice`、`confirm`、`info-steps` | 通知、业务确认和信息补充 |
| `mcp-approval` | 工具审批卡 |
| `visualization-*` | 图表、配置、看板和菜单产物 |
| `report-document`、`report-fragment` | 完整报表和选区改写 |
| `prompt-suggestions` | 新会话开场建议 |

非法结构化围栏会降级为普通 Markdown，不应导致页面崩溃。

## `online_search` 当前状态

请求和会话模型保留 `online_search` 字段，前端也会保存开关状态；当前后端没有根据该字段执行外部联网搜索的服务链路。

因此：

- 开启开关不代表回答已经访问互联网；
- 不应把回答中的链接或时效性内容当成联网检索证据；
- 需要外部系统数据时，应使用明确配置的业务 Agent、Skill 或 MCP 工具，并接受相应权限和审批约束。

## 常见问题

### 模型列表为空或只有 `auto`

检查 OpenAI 兼容地址、Key、默认模型和 `/v1/models` 兼容性。`auto` 存在不代表目标服务可用。

### 页面一直等待或流式内容解析失败

检查请求是否使用 `response_format=events`、响应类型是否为 NDJSON、代理是否缓冲流式响应，以及客户端是否按行解析。

### 页面有历史，但模型忘记早期内容

检查上下文窗口、历史预算、摘要预算和会话长度。页面历史与模型记忆本来就是两层数据。

### 附件上传成功但回答没有引用内容

确认文件是否为支持的文本或图片类型、是否被字符预算截断、图片模型是否支持视觉。其他文件只传递元信息。

### RAG 故障但问答仍有结果

这是 fail-open 行为。查看后端 RAG 日志中的 `embedding_disabled`、`no_match` 或 `retrieval_error`，再按[RAG 与知识库运维](/04-AI与数据智能/RAG与知识库运维.md)排查。

### 业务智能体没有使用深度思考或 RAG

这是设计边界。业务智能体使用显式 Skill、Workflow 和受控工具，详见[业务智能体与工作流](/04-AI与数据智能/业务智能体与工作流.md)。

## 运维验收

- 健康检查和模型列表可访问；
- 普通问答能收到 `delta` 和 `done`；
- 重新打开会话后消息、附件元信息和结构化 part 正常；
- 深度思考开关只影响普通问答；
- 文本附件的每文件和总字符预算生效；
- 图片和不支持附件的提示符合实际处理结果；
- RAG 关闭或故障时普通问答可降级；
- `online_search` 未被描述为已实现的联网搜索。

## 关联文档

- [AI 与数据智能](/04-AI与数据智能/README.md)
- [业务智能体与工作流](/04-AI与数据智能/业务智能体与工作流.md)
- [RAG 与知识库运维](/04-AI与数据智能/RAG与知识库运维.md)
- [DIH 与 AI API](/08-API参考/RestfulAPI/DIH与AI.md)
- [AI 与 MCP 架构](/06-架构设计/AI与MCP架构.md)
