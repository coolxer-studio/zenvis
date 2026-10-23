# DIH 与 AI API

本页合并 DIH Chat、持久化 Agent Run、会话、报表、Skill、RAG 数据集、知识库、RAG 文档、检索审计和后台 AI 分析任务接口。旧 `DihController` 已不存在，原建议、模型和健康接口均由 `ChatController` 实现。

## ChatController

基础路径：`/api/v1/dih`

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| POST | `/api/v1/dih/chat` | `ChatDto` | AI 文本流或 NDJSON 事件流 |
| POST | `/api/v1/dih/suggest` | `SuggestDto` | 编辑器补全建议 |
| GET | `/api/v1/dih/model/list` | 无 | 当前可用模型 |
| GET | `/api/v1/dih/health` | 无 | DIH 健康检查 |
| POST | `/api/v1/dih/upload` | multipart `file` | 上传聊天附件 |
| GET | `/api/v1/dih/upload/{fileId}/preview` | 当前用户的附件 ID | 图片二进制预览 |
| POST | `/api/v1/dih/chat/action-decision` | `ChatActionDecisionDto` | 保存内置演示或旧卡片决定 |
| POST | `/api/v1/dih/chat/workflow/action` | `WorkflowActionDto` | 执行普通 DIH 工作流动作 |
| POST | `/api/v1/dih/chat/workflow/telemetry` | `WorkflowTelemetryDto` | 记录客户端图表渲染故障 |

推荐 Chat 请求：

```json
{
  "chat_id": "chat-001",
  "model": "auto",
  "type": "ask",
  "message": "请分析当前系统的风险态势",
  "attachments": [],
  "deep_think": false,
  "online_search": false,
  "response_format": "events"
}
```

`response_format=events` 时响应类型为 `application/x-ndjson`，每一行都是独立 JSON。核心事件：

| `event` | 内容 |
| --- | --- |
| `delta` | 文本增量，读取 `content` |
| `approval_required` | MCP 工具需要审批，读取 `data.request_id`、工具、参数和风险 |
| `approval_updated` | 审批或工具调用状态变化 |
| `tool_call` | 一次 MCP 工具调用的开始、成功或失败快照，按 `data.callId` 更新同一项 |
| `rag_search` | 一次知识库检索的完整快照，包含查询范围、状态、原因和命中来源 |
| `done` | 本轮完成，包含最终 `message` |
| `error` | 本轮失败 |

客户端必须逐行解析，未知事件应忽略而不是终止整个流。同一轮可能产生多个审批请求，应按 `request_id` 分别维护。MCP 审批提交到 [MCP 服务管理接口](/08-API参考/RestfulAPI/MCP服务管理.md#审批与调用审计)，不能提交到 `action-decision`。

当 `type` 属于 `app.ai.agent-run.enabled-types` 时，`POST /api/v1/dih/chat` 兼容适配器会创建 Agent Run，并把 Run 事件映射为 `delta`、`tool_call`、`approval_required`、`approval_updated`、`rag_search`、`done` 和 `error`。非事件流请求只返回“任务已创建”和 `runId`，不会等待任务完成。持久化运行、steer 和断线恢复应优先使用下节的 Run API。

内置示例提示词优先走确定性示例流，不创建 Run；直接向 Run API 提交示例时返回业务码 `503`，客户端应回退到 `POST /api/v1/dih/chat`。

### 工具调用事件

`tool_call` 在模型参数通过 Schema 校验并进入工具执行阶段后产生。参数未通过 Schema 校验的调用不会生成该事件，也不消耗工具调用预算。审批拒绝和策略拒绝位于该阶段之后，仍会产生工具记录并可能占用一次预算，结果中会返回可解释的拒绝原因。同一 `callId` 会依次收到 `running`、`succeeded` 或 `failed` 状态，客户端必须按 `callId` upsert，不能把开始、成功和失败拆成多条消息。

```json
{
  "event": "tool_call",
  "data": {
    "callId": "4d8f0c47-1cbf-4d56-9a0d-99f8a625289f",
    "toolName": "entity_trend",
    "status": "succeeded",
    "arguments": "{\"request\":{\"entity\":\"lab_access_event\",\"granularity\":\"day\"}}",
    "result": "{\n  \"meta\": {\"query_type\": \"trend\"},\n  \"result\": {}\n}",
    "error": null,
    "durationMillis": 43,
    "startedAt": "2026-10-22T17:41:44.294Z",
    "completedAt": "2026-10-22T17:41:44.337Z"
  }
}
```

`status` 取值为 `running`、`succeeded`、`failed`。`arguments` 保留模型传入的原始 JSON 字符串；`result` 是规范化后的展示文本，能解析为 JSON 时会以缩进 JSON 字符串返回。`error` 仅失败时非空，耗时和时间为毫秒与 ISO-8601 字符串。`done.message.parts` 中的对应部件类型为 `tool-call`，`id` 与 `callId` 一致，`metadata` 使用相同字段；`content` 写入“工具名 · 调用状态”的短摘要，供尚不识别新部件的客户端回退展示。

### RAG 检索事件

`rag_search` 在模型首条 `delta` 前输出，每轮每次同步检索只产生一条记录。状态映射固定为 `ok → succeeded`、`no_match → empty`、前置跳过原因 → `skipped`、检索异常 → `failed`。RAG 不属于 MCP 工具，不占用工具调用预算，也不并入“工具活动”分组。

```json
{
  "event": "rag_search",
  "data": {
    "searchId": "98f3f1c6-70bb-4ef7-a3df-4cdcbcdb6c6d",
    "query": "实验室访问趋势",
    "mode": "agent",
    "scopeType": "bound",
    "knowledgeBaseIds": [1, 2],
    "topK": 6,
    "status": "succeeded",
    "reason": "ok",
    "hitCount": 2,
    "durationMillis": 43,
    "startedAt": "2026-10-22T17:41:44.294Z",
    "completedAt": "2026-10-22T17:41:44.337Z",
    "results": [
      {
        "documentId": "doc-1",
        "knowledgeBaseId": 1,
        "knowledgeBaseName": "产品文档",
        "datasetName": "接入指南",
        "fileName": "quick-start.md",
        "source": "kb_1",
        "text": "命中内容摘要，最多 500 字"
      }
    ],
    "error": null
  }
}
```

`status` 取值为 `succeeded`、`empty`、`skipped`、`failed`；`reason` 取值为 `ok`、`no_match`、`empty_query`、`no_knowledge_base`、`no_bound_knowledge_base`、`embedding_disabled`、`retrieval_error`、`knowledge_base_lookup_error`。`done.message.parts` 中的对应部件类型为 `rag-search`，`id` 与 `searchId` 一致，`content` 写入“知识库检索 · N 个来源”等短摘要。

未使用 `response_format=events` 时，工具调用继续以旧 Markdown 日志拼接在文本流中，兼容已有第三方调用；不会输出 `tool_call` 或 `rag_search` 事件，RAG 记录也不会混入正文。

普通工作流动作要求 `chat_id`、`message_id`、`part_id`、`workflow_id`、`action`，并按动作提供 `answers` 或 `revision`。服务端校验会话归属、消息/part 精确匹配、状态版本、允许动作和严格候选快照。遥测接口当前只接受数据可视化工作流的 `chart_render_failed`。

附件上传结果应原样放入下一次 Chat 请求的 `attachments`。预览接口只允许访问当前用户上传且媒体类型为图片的附件。

## AgentRunController

基础路径：`/api/v1/dih/chat/runs`

Agent Run 用于所有真实业务 Agent 任务，普通问答 `ask` 和内置演示夹具不能创建 Run。默认 `enabled-types=*`；`disabled-types` 可临时停用指定类型。所有接口只允许 Run 所属用户访问。

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| POST | `/api/v1/dih/chat/runs` | `AgentRunRequestDto` | 创建 Run；会话已有活动 Run 时转为 steer |
| GET | `/api/v1/dih/chat/runs/{runId}` | Run ID | `AgentRunVo` 快照 |
| GET | `/api/v1/dih/chat/runs` | `chatId?`、`status?`、`page=1`、`perPage=20` | `PageRowsVo<AgentRunVo>` |
| GET | `/api/v1/dih/chat/runs/{runId}/events` | query `after=0`、`types?` | `application/x-ndjson` 事件流 |
| GET | `/api/v1/dih/chat/runs/{runId}/events/list` | query `after=0`、`types?` | 最多 500 条历史事件数组 |
| POST | `/api/v1/dih/chat/runs/{runId}/steer` | `AgentRunSteerDto` | 追加当前 Run 指令；任务可能重新入队 |
| POST | `/api/v1/dih/chat/runs/{runId}/pause` | query `reason?` | 暂停任务 |
| POST | `/api/v1/dih/chat/runs/{runId}/resume` | 无 | 继续 `PAUSED` 或 `WAITING_USER` 任务 |
| POST | `/api/v1/dih/chat/runs/{runId}/cancel` | 无 | 取消任务或进入 `CANCELING` |

### 创建与 steer

```json
{
  "chat_id": "chat-001",
  "type": "agent_report",
  "model": "auto",
  "message": "生成一份本周安全运行报告",
  "attachments": [],
  "report_action": {
    "type": "full_generate",
    "document_id": "report-001",
    "base_revision": 0,
    "source_refs": []
  },
  "client_message_id": "6c3c8e42-2a8f-4cf8-9d0f-7efcf676d6f0"
}
```

`chat_id` 必填；`message` 与 `attachments` 至少一个非空。`type` 必须是已启用 Run 的业务 Agent 类型，否则返回 `400`；类型未启用 Run 时返回 `503`。`client_message_id` 在 Run 内用于 steer 幂等。

平台在会话锁内检查活动 Run：

- 没有活动 Run：创建新 Run，返回 `steered=false`。
- 已有活动 Run：同一请求转为 steer，返回该 Run，且 `steered=true`。
- 同一 Run 重复提交相同 `client_message_id`：返回当前快照，不重复追加消息。

steer 请求：

```json
{
  "message": "结论部分尽量面向管理层",
  "attachments": [],
  "client_message_id": "f627b7d0-7f70-4e33-a6ac-bf2af6c3035c"
}
```

steer 先保存为 `PENDING`，在当前模型步骤结束后注入。任务处于 `PAUSED` 或 `WAITING_USER` 时，steer 会把状态改为 `QUEUED`。终态 Run 返回 `409`，`CANCELING` 状态同样拒绝 steer。

### Run 快照

`AgentRunVo` 的主要字段：

| 字段 | 说明 |
| --- | --- |
| `runId`、`chatId`、`chatType`、`agentType`、`model` | Run 与会话标识 |
| `status`、`statusLabel` | 运行状态及中文说明 |
| `goal`、`plan`、`planRevision`、`currentStepId` | 目标、完整计划和当前步骤 |
| `decisionSteps`、`maxSteps` | 已用决策步数与上限 |
| `toolCalls`、`maxToolCalls` | 已用工具调用与 Run 总预算 |
| `effectiveMillis`、`maxDurationSeconds` | 有效执行时长与上限 |
| `lastEventSequence` | 最后一条事件序号，用作重放游标 |
| `pendingAction`、`pausedReason`、`lastError` | 待审批/待用户动作和暂停、错误原因 |
| `attemptCount`、`recoveryCount`、`truncationCount` | 认领、租约恢复和截断重试次数 |
| `finalAnswer`、`startedAt`、`finishedAt` | 最终答复与时间 |

`status` 取值为 `QUEUED`、`RUNNING`、`WAITING_APPROVAL`、`WAITING_USER`、`PAUSED`、`SUCCEEDED`、`FAILED`、`CANCELING`、`CANCELLED`。`SUCCEEDED`、`FAILED` 和 `CANCELLED` 为终态。

`WAITING_USER` 时，`pendingAction` 是包含完整问题的 JSON 字符串；客户端应优先读取该字段，而不是使用可能被截断的 `pausedReason`：

```json
{
  "type": "user",
  "question": "请补充推送任务配置中的 Source、Topic、消费组和 DLQ Topic 实际值。"
}
```

提交补充信息使用 `POST /runs/{runId}/steer`。服务端重新排队时会清空 `pendingAction`，客户端应更新 Run 快照并关闭输入框，避免继续展示过期问题。

### 事件流

`GET /runs/{runId}/events` 每行一个 JSON，固定公共字段：

```json
{
  "sequence": 42,
  "run_id": "0fbd14d0-a220-4d86-a2c5-2f9ca79c4d33",
  "type": "step.updated",
  "step_id": "step-2",
  "created_at": "2026-10-22T09:30:12.000+00:00",
  "data": {
    "step": {
      "id": "step-2",
      "title": "写入风险分析章节",
      "status": "DONE",
      "summary": "已保存 2 条关键发现和来源引用"
    }
  }
}
```

| `type` | 主要 `data` |
| --- | --- |
| `run.created`、`run.snapshot` | `run` 快照 |
| `plan.updated` | `plan`、`revision` |
| `step.updated` | `step` |
| `assistant.delta` | `content` |
| `tool.started`、`tool.succeeded`、`tool.failed` | `callId`、`toolName`、`arguments`、`result`、`error`、`durationMillis` |
| `tool.approval_required`、`tool.approval_updated` | `requestId`、`toolName`、`status`、`riskLevel`、`agentRunId`、`runApprovalAllowed` |
| `tool.approval_executed`、`tool.approval_unavailable` | 审批后工具已执行，或审批执行链路不可用 |
| `approval.required`、`approval.updated` | Run 等待审批状态和 `requestId` |
| `rag.search` | 与 Chat `rag_search.data` 相同 |
| `context.compacted` | 压缩阶段、原因、前后 Token/字符、预算、消息与附件计数、摘要预览 |
| `steer.queued` | `inputId`、`message`、`messageId`、`status` |
| `artifact.updated` | 报表文档、章节、哈希或正式修订信息 |
| `run.queued`、`run.paused`、`run.resumed` | `reason` 等状态原因 |
| `run.output_truncated` | `attempt`、`maxAttempts` |
| `run.error` | `message`、`retryable`、`failureCount`、可选 `httpStatus` |
| `run.waiting_user` | `question` |
| `run.succeeded` | `answer`，报表任务还包含 `artifact` |
| `run.failed` | `error` |
| `run.canceling`、`run.cancelled` | 无固定业务字段 |

`/events` 和 `/events/list` 均支持可选 `types`，值为逗号分隔的事件类型集合，例如 `types=rag.search,tool.started,tool.succeeded,tool.failed,context.compacted`。省略 `types` 时返回全量事件，保持向后兼容。活动视图应使用该过滤，并在结果达到 500 条时以最后一条 `sequence` 继续请求，避免 `assistant.delta` 占满历史窗口。

`context.compacted` 的 `data` 字段：

```json
{
  "compactionId": "compact-001",
  "stage": "model-input",
  "reason": "prompt_token_budget",
  "beforeTokens": 8000,
  "afterTokens": 600,
  "savedTokens": 7400,
  "beforeChars": 32000,
  "afterChars": 2400,
  "budgetTokens": 600,
  "budgetChars": 2400,
  "omittedMessages": 0,
  "retainedMessages": 6,
  "truncatedMessages": 0,
  "omittedAttachments": 0,
  "includedAttachments": 0,
  "summaryPreview": "保留用户目标、当前计划和最近工具结果。"
}
```

`stage` 与 `reason` 的对应关系如下，该事件只在真实发生裁剪、摘要或附件正文移除时产生；历史 Run 不补造事件。前端把 `rag.search`、工具生命周期和 `context.compacted` 归并为同一执行过程时间线，详情默认折叠。

| `stage` | 典型 `reason` | 含义 |
| --- | --- | --- |
| `run-context` | `checkpoint_truncated`、`prompt_char_limit`、`recent_history_window`、`recent_history_truncated` | 检查点、任务提示词或近期会话超出字符/条数上限 |
| `model-input` | `prompt_token_budget` | 当前步骤提示词按模型输入 Token 预算截断 |
| `history-window` | `history_window_budget`、`history_window_summary` | 聊天记忆窗口裁剪或生成摘要 |
| `attachment-memory` | `attachment_body_removed` | 附件正文写入聊天记忆前被压缩为引用或短摘要 |

客户端的处理要求：

- 以 `sequence` 严格递增去重，不从事件到达顺序推断业务顺序。
- 保存最后成功应用的 `sequence`，断线后以 `?after=<sequence>` 续订。
- 流在 Run 进入终态且剩余事件发送完毕后关闭；网络中断应重新连接，而不是创建新 Run。
- 事件只用于展示和恢复触发，最终状态以 `GET /runs/{runId}` 快照为准。

### 暂停、继续与取消

- `pause` 把非终态 Run 置为 `PAUSED` 并释放租约；`reason` 缺省为“用户暂停”。
- `resume` 只处理 `PAUSED` 和 `WAITING_USER`。若已达到步数预算，服务端追加默认步数；若已达到有效时长预算，追加默认时长；工具总预算不自动追加。
- `cancel` 对 `RUNNING` Run 先置为 `CANCELING`，由执行线程协作退出后进入 `CANCELLED`；对等待状态可立即取消。
- 取消会撤销待审批和已批准但未执行的调用，但不会回滚已经生效的外部副作用。
- 取消中的执行线程会中止当前模型流；已发出但结果未知的工具调用进入 `UNKNOWN`，任务暂停或取消时不得伪造成功。

## ChatSessionController

基础路径：`/api/v1/dih/chat-session`

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| POST | `/api/v1/dih/chat-session/add` | `ChatSessionDto` | 创建会话 |
| DELETE | `/api/v1/dih/chat-session/{id}` | 数据库记录 ID | 删除当前用户会话 |
| DELETE | `/api/v1/dih/chat-session/bulk/{ids}` | 记录 ID 列表 | 批量删除 |
| POST | `/api/v1/dih/chat-session/{id}/update` | `ChatSessionDto` | 更新标题、置顶、消息等 |
| GET | `/api/v1/dih/chat-session/list/pin` | 无 | 当前用户置顶会话 |
| GET | `/api/v1/dih/chat-session/list` | `ChatSessionSearchDto` | `PageRowsVo<ChatSessionVo>` |
| GET | `/api/v1/dih/chat-session/{id}/view` | 数据库记录 ID | 会话详情 |
| GET | `/api/v1/dih/chat-session/{sessionId}/session` | query `type?` | 按业务会话 ID 获取会话；不存在时返回对应 Agent 开场模板 |

`id` 是数据库记录 ID，`sessionId` 是 Chat 使用的业务会话标识，不可混用。所有读写都校验当前用户归属。删除会话也会影响该会话持久化的工具授权。

`ChatSessionVo.message_list[]` 中由 Agent Run 产生的用户消息和 AI 消息都携带 `run_id`。客户端应保存该字段，并在恢复会话时为每一轮消息挂载对应 Run 卡片；不要把 `run_id` 仅用于查询当前活动 Run，否则历史任务卡会在重新加载消息后丢失。一个会话可以包含多个已完成 Run，只有处于活动状态的 Run 才需要续订事件流。

## ReportDocumentController 与 ReportExportController

共同基础路径：`/api/v1/dih/chat-session/{sessionId}/report`

这是当前代码新增且旧 API 文档缺失的报表工作区契约。

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| GET | `/api/v1/dih/chat-session/{sessionId}/report` | 会话记录 ID | `ReportWorkspaceVo` |
| GET | `/api/v1/dih/chat-session/{sessionId}/report/materials` | 会话记录 ID | 可导入报表的会话素材 |
| POST | `/api/v1/dih/chat-session/{sessionId}/report/save` | `ReportDocumentSaveDto` | 保存当前文档 |
| POST | `/api/v1/dih/chat-session/{sessionId}/report/archive` | `ReportArchiveDto` | 归档当前文档 |
| POST | `/api/v1/dih/chat-session/{sessionId}/report/artifacts/{artifactId}/restore` | `ReportArchiveDto` | 恢复归档版本 |
| POST | `/api/v1/dih/chat-session/{sessionId}/report/artifacts/{artifactId}/rename` | `ReportArtifactRenameDto` | 重命名归档 |
| DELETE | `/api/v1/dih/chat-session/{sessionId}/report/artifacts/{artifactId}` | query `base_revision` | 删除归档 |
| GET | `/api/v1/dih/chat-session/{sessionId}/report/export/{format}` | 导出格式 | 文件下载 |

保存请求：

```json
{
  "document_id": "report-001",
  "base_revision": 3,
  "title": "运行分析报告",
  "format": "markdown",
  "content": "# 报告",
  "outline": [],
  "source_refs": []
}
```

归档请求包含 `document_id`、`base_revision`、`name`；重命名请求包含 `base_revision`、`name`。所有写操作采用乐观修订控制：`base_revision` 不是最新版本时返回 HTTP 409，`data.current_document` 包含服务端当前文档，客户端必须合并后重试。

`ReportWorkspaceVo` 包含：

| 字段 | 说明 |
| --- | --- |
| `current_document` | 当前编辑文档 |
| `revisions` | 修订列表 |
| `artifacts` | 归档产物 |
| `extra_data` | 扩展数据 |

导出接口不使用 `ResponseWrap`，返回带 `Content-Disposition` 文件名的字节流；支持格式以 `ReportExportService` 当前实现为准。

启用 Agent Run 时，报表正文通过 `report_prepare_document`、`report_upsert_section`、`report_replace_section`、`report_reorder_sections` 和 `report_finalize` 写入章节草稿，单章硬上限 2,400 字符。正式修订只在 `report_finalize` 校验通过后生成，`run.succeeded.data.artifact` 携带文档和修订信息。

旧单轮路径仍要求模型在 `zenvis:report-document-config` 围栏内返回正文。若模型以 `finish_reason=length` 结束，或围栏未闭合，服务端会移除不完整报表部件、返回失败 `notice`，并不写入当前文档。客户端不得把截断内容中的半截围栏渲染为可保存报表。

## SkillController

基础路径：`/api/v1/dih/skills`

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| GET | `/api/v1/dih/skills/list` | `SkillSearchDto` | 分页 Skill 列表 |
| GET | `/api/v1/dih/skills/agents` | query `enabled?` | 内置 Agent Skill 状态 |
| GET | `/api/v1/dih/skills/chat-entries` | query `enabled?` | 输入区可展示的聊天入口 |
| GET | `/api/v1/dih/skills/options` | query `enabled=true` | AI 分析任务可选的已启用 Skill |
| GET | `/api/v1/dih/skills/{id}/view` | Skill ID | 元数据和入口文件内容 |
| POST | `/api/v1/dih/skills/add` | `SkillSaveDto` | 创建页面自定义 Skill |
| POST | `/api/v1/dih/skills/{id}/update` | `SkillSaveDto` | 更新可编辑 Skill；插件 Skill 仅临时覆盖 |
| DELETE | `/api/v1/dih/skills/{id}` | Skill ID | 删除页面创建的自定义 Skill |
| POST | `/api/v1/dih/skills/upload` | multipart `file` | 上传 zip 技能包并安装为自定义 Skill |
| GET | `/api/v1/dih/skills/{id}/export` | Skill ID | 导出 Skill 目录为 zip |
| POST | `/api/v1/dih/skills/reload` | 无 | 重新扫描 `app.paths.skills` |
| POST | `/api/v1/dih/skills/{id}/enable` | Skill ID | 启用并写回 `skill.json` |
| POST | `/api/v1/dih/skills/{id}/disable` | Skill ID | 停用并写回 `skill.json` |
| GET | `/api/v1/dih/skills/knowledge-base-options` | 无 | 可绑定到 Skill 的 RAG 知识库选项 |
| GET | `/api/v1/dih/skills/agent/{agentType}/prompt` | Agent 类型 | 当前加载的 Skill 提示词片段 |

`SkillSearchDto` 支持按 `keyword`、`agentType`、`enabled`、`custom` 和 `source` 筛选；`source` 可匹配插件包名、`custom` 或 `builtin`。`/options` 只允许 `enabled=true`，AI 分析任务不能选择未启用 Skill。任务只保存 Skill ID，真正执行时读取最新 Skill 内容和工具边界。

`SkillSaveDto` 的 `knowledgeBaseIds` 用于绑定一个或多个 RAG 知识库。绑定后平台在每轮 Skill 会话中检索这些知识库的并集并自动注入系统提示词，不把它注册为 MCP 工具。未绑定知识库时不检索，也不回退全库。页面更新插件 Skill 时会写入数值 ID 并移除包内 `knowledgeBaseCodes`；下一次插件升级或重装会以包内声明重新覆盖。`source` 和 `editable` 由平台根据来源维护：插件包名标识安装归属，页面自定义 Skill 使用 `custom`，内置 Skill 使用 `builtin`。TypeScript 示例见[概览与接入](概览与接入.md#typescript)。

## VectorStoreQueryController

基础路径：`/api/v1/dih/vectorstore`

所有接口受 `app.ai.vectorstore.management.enabled` 控制，默认关闭。未启用时返回无权限错误。

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| GET | `/api/v1/dih/vectorstore/documents` | query `keyword?`、`source?` | 全部匹配文档 |
| GET | `/api/v1/dih/vectorstore/documents/list` | 分页、`keyword?`、`source?`、`knowledge_base_id?` | 分页文档，可按知识库过滤 |
| GET | `/api/v1/dih/vectorstore/document/{documentId}` | 文档 ID | 文档详情 |
| DELETE | `/api/v1/dih/vectorstore/document/{documentId}` | 文档 ID | 删除单文档 |
| DELETE | `/api/v1/dih/vectorstore/documents` | query `documentIds` | 批量删除 |
| POST | `/api/v1/dih/vectorstore/search` | query `query`、`topK=5`、`source?`、`knowledge_base_id?` | 相似度搜索列表 |
| GET | `/api/v1/dih/vectorstore/search` | 分页、`query`、`topK=5`、`source?`、`knowledge_base_id?` | 相似度搜索分页 |
| GET | `/api/v1/dih/vectorstore/retrieval-logs/list` | 分页、`knowledge_base_id?`、`requestType?`、`status?`、`keyword?` | 检索审计记录分页 |

`RagDocumentVo` 包含 `id`、`text`、`metadata`、`source`。`knowledge_base_id` 匹配文档 metadata 中的 `knowledge_base_id` 字段（构建知识库时写入），用于查看单个知识库已入库的文档。管理接口用于运维和排障，插件文档的正常入库由插件生命周期处理。

**检索审计**：每次相似度搜索（`MANUAL`）和问答或业务 Skill 触发的自动 RAG 检索（`CHAT`）都会写入 `t_rag_retrieval_log` 表，记录请求文本、TopK、来源筛选、命中数、状态、耗时、错误信息和命中结果摘要 JSON（`result_json`，单条内容截断 500 字符）。审计 `status` 只使用 `ok`、`no_match`、`empty_query`、`embedding_disabled`、`retrieval_error`；`no_knowledge_base`、`no_bound_knowledge_base`、`knowledge_base_lookup_error` 等细分原因会归并为 `retrieval_error`，精确原因应查看会话 `rag_search.reason` 或后端 RAG 日志。同一次检索的内存快照同时用于会话中的 `rag_search` 事件和审计记录，但查询与命中摘要最多展示 500 字。日志保存为 best-effort，失败不影响检索主流程。请求未指定 `knowledge_base_id` 时，按命中文档 metadata 中出现次数最多的知识库归属。`retrieval-logs/list` 的 `requestType` 取值为 `MANUAL` / `CHAT`，`keyword` 模糊匹配检索文本。

## RagDatasetController

基础路径：`/api/v1/dih/vectorstore/datasets`

数据集是 Markdown 文件的存储单元，文件保存在 MinIO 的 `rag/datasets/{id}` 目录下。数据库存储名称、来源、标签、描述、目录和文件数。

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| POST | `/api/v1/dih/vectorstore/datasets/add` | `RagDatasetDto` | 创建数据集 |
| POST | `/api/v1/dih/vectorstore/datasets/{id}/update` | `RagDatasetDto` | 更新基础信息 |
| DELETE | `/api/v1/dih/vectorstore/datasets/{id}` | 数据集 ID | 删除数据集及 MinIO 文件；被知识库引用时拒绝 |
| GET | `/api/v1/dih/vectorstore/datasets/list` | `RagDatasetSearchDto`（`name?`、`source?`、`tag?`、分页） | `PageRowsVo<RagDatasetVo>` |
| GET | `/api/v1/dih/vectorstore/datasets/options` | 无 | 知识库关联选择的数据集选项 |
| GET | `/api/v1/dih/vectorstore/datasets/{id}` | 数据集 ID | 数据集详情 |
| POST | `/api/v1/dih/vectorstore/datasets/{id}/upload` | multipart `file`，query/form `relativePath?` | 上传单个 `.md` 文件 |
| GET | `/api/v1/dih/vectorstore/datasets/{id}/files` | 数据集 ID | MinIO 文件列表，`name` 为相对数据集根目录的层级路径 |
| GET | `/api/v1/dih/vectorstore/datasets/{id}/files/content` | query `objectName` | 读取文件内容 |
| DELETE | `/api/v1/dih/vectorstore/datasets/{id}/files` | query `objectName` | 删除指定文件 |

`upload` 仅接受 `.md` 文件。传入 `relativePath`（如 `guide/intro.md`，对应浏览器 `File.webkitRelativePath`）时按目录层级存储；服务端会清洗路径，去除盘符、开头斜杠和 `..` 越级片段。上传后自动刷新数据集文件数。`files/content` 与 `files` 删除接口都校验 `objectName` 必须属于该数据集目录。

`RagDatasetDto` 字段：`name`（必填）、`source`、`tags`、`description`。`name` 在未删除的数据集范围内唯一，创建或更新为重名时返回「数据集名称已存在」（更新时排除自身）。唯一性为应用层校验，未加数据库唯一索引——表使用逻辑删除（`is_delete`），数据库级唯一约束会导致删除后无法重建同名数据集。

## RagKnowledgeBaseController

基础路径：`/api/v1/dih/vectorstore/knowledge-bases`

知识库关联一个或多个数据集，构建时将数据集中的 Markdown 文件切分、向量化写入 Redis Vector Store，并在文档 metadata 中写入 `knowledge_base_id`、`knowledge_base_name`、`knowledge_base_tags`。所有接口同样受 `app.ai.vectorstore.management.enabled` 控制。

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| POST | `/api/v1/dih/vectorstore/knowledge-bases/add` | `RagKnowledgeBaseDto` | 创建知识库并关联数据集 |
| POST | `/api/v1/dih/vectorstore/knowledge-bases/{id}/update` | `RagKnowledgeBaseDto` | 更新基础信息和关联数据集 |
| DELETE | `/api/v1/dih/vectorstore/knowledge-bases/{id}` | 知识库 ID | 删除知识库并清理 Redis 向量数据 |
| GET | `/api/v1/dih/vectorstore/knowledge-bases/list` | `RagKnowledgeBaseSearchDto`（分页） | `PageRowsVo<RagKnowledgeBaseVo>` |
| GET | `/api/v1/dih/vectorstore/knowledge-bases/{id}` | 知识库 ID | 知识库详情 |
| POST | `/api/v1/dih/vectorstore/knowledge-bases/{id}/build` | 知识库 ID | 异步触发向量化构建 |
| GET | `/api/v1/dih/vectorstore/knowledge-bases/{id}/logs` | 知识库 ID | 流式构建日志，出现「完成......」或「失败......」时结束 |

`RagKnowledgeBaseDto` 字段：`name`（必填）、`source`、`tags`、`description`、`dataset_ids`（必填，关联数据集 ID 列表）。`name` 在未删除的知识库范围内唯一，创建或更新为重名时返回「知识库名称已存在」（更新时排除自身），与数据集同为应用层校验。`build` 为异步任务，进度通过 `logs` 流式接口获取（最长 30 分钟）。查看知识库已入库文档请使用 `/documents/list?knowledge_base_id={id}`。

## AnalysisTaskController

基础路径：`/api/v1/system/analysis-task`

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| POST | `/api/v1/system/analysis-task/add` | `AnalysisTaskDto` | 创建后台分析任务 |
| DELETE | `/api/v1/system/analysis-task/{id}` | 任务 ID | 删除非活动任务 |
| DELETE | `/api/v1/system/analysis-task/bulk/{ids}` | 任务 ID 列表 | 批量删除非活动任务 |
| POST | `/api/v1/system/analysis-task/{id}/update` | `AnalysisTaskDto` | 完整更新非活动任务 |
| GET | `/api/v1/system/analysis-task/list` | `AnalysisTaskSearchDto` | 分页任务 |
| GET | `/api/v1/system/analysis-task/{id}/view` | 任务 ID | 详情、结果和结构化卡片 |
| POST | `/api/v1/system/analysis-task/{id}/enqueue` | 任务 ID | 清理旧结果并重新入队 |
| POST | `/api/v1/system/analysis-task/{id}/cancel` | 任务 ID | 取消等待、执行或待审批任务 |
| POST | `/api/v1/system/analysis-task/queue/run-once` | 无 | 原子认领一个到期任务并异步提交 |
| GET | `/api/v1/system/analysis-task/queue/status` | 无 | 队列、执行槽和挂起容量 |
| GET | `/api/v1/system/analysis-task/{id}/approvals/list` | `page`、`perPage` | 当前任务待审批请求 |
| POST | `/api/v1/system/analysis-task/{id}/approvals/{requestId}/decision` | `McpApprovalDecisionDto` | 任务审批决定 |

创建/更新请求主要字段：

```json
{
  "name": "最近7天 API 调用分析",
  "description": "分析调用趋势和异常点",
  "model": "auto",
  "prompt": "分析最近7天调用量、失败率和异常峰值。",
  "priority": 10,
  "scheduled_time": null,
  "approval_mode": "MANUAL",
  "skill_ids": ["continuous-analysis"]
}
```

该接口只管理一次性任务。`scheduled_time` 为空时立即进入队列；任务完成后保持终态。`list` 可用 `scheduleId` 筛选某个周期配置生成的任务，生成任务详情包含 `schedule_id` 和 `schedule_fire_time`。

`approval_mode` 必填：

- `AUTO`：`ALLOW` 直接执行，`ASK` 自动批准并记录，`DENY` 禁止。
- `MANUAL`：`ASK` 进入 `WAITING_APPROVAL`，等待任务创建人或超级管理员处理。

任务状态包括 `PENDING`、`RUNNING`、`WAITING_APPROVAL`、`CANCELING`、`SUCCESS`、`FAILED`、`CANCELED`。活动任务不能编辑或删除。重新入队会生成新的 `execution_id`，清除旧结果、错误、执行时间和旧 execution 工具授权。

任务审批决定支持 `approved`、`approved_task`、`rejected`。`approved_task` 仅对当前 execution 和精确 `tool_key` 持续有效。取消任务会终止待审批请求。任务对应的 MCP 工具见 [AI 分析任务工具](/08-API参考/MCPtool/AI分析任务工具.md)。

## AnalysisTaskScheduleController

基础路径：`/api/v1/system/analysis-task-schedule`

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| POST | `/add` | `AnalysisTaskScheduleDto` | 创建周期配置，从下一个 Cron 时间开始 |
| POST | `/{id}/update` | `AnalysisTaskScheduleDto` | 更新模板和下次触发时间 |
| GET | `/list` | `name?`、`enabled?`、分页 | 分页查询周期配置 |
| GET | `/{id}/view` | 配置 ID | 查询详情 |
| POST | `/{id}/enabled` | `{ "enabled": true }` | 启用或停用未来投递 |
| DELETE | `/{id}` | 配置 ID | 删除配置，不影响已生成任务 |

`AnalysisTaskScheduleDto` 包含名称、描述、模型、Prompt、优先级、审批模式、Skill、`cron_expression` 和 `enabled`。Cron 使用部署实例时区与 Spring 6 段格式。响应同时提供 `next_fire_time`、`last_fire_time`、`generated_count` 和 `last_error`。

## 相关使用与运维

- [AI 问答与会话](/04-AI与数据智能/AI问答与会话.md)
- [智能体任务与持久化运行](/04-AI与数据智能/智能体任务与持久化运行.md)
- [业务智能体与工作流](/04-AI与数据智能/业务智能体与工作流.md)
- [MCP 服务与工具审批](/04-AI与数据智能/MCP服务与工具审批.md)
- [AI 分析任务与 Skill](/04-AI与数据智能/AI分析任务与Skill.md)
- [RAG 与知识库运维](/04-AI与数据智能/RAG与知识库运维.md)
