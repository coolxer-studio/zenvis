# DIH 与 AI API

本页合并 DIH Chat、会话、报表、Skill、RAG 文档和后台 AI 分析任务接口，共 7 个 Controller、53 个路由。旧 `DihController` 已不存在，原建议、模型和健康接口均由 `ChatController` 实现。

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
| `done` | 本轮完成，包含最终 `message` |
| `error` | 本轮失败 |

客户端必须逐行解析，未知事件应忽略而不是终止整个流。同一轮可能产生多个审批请求，应按 `request_id` 分别维护。MCP 审批提交到 [MCP 服务管理接口](/08-API参考/RestfulAPI/MCP服务管理.md#审批与调用审计)，不能提交到 `action-decision`。

普通工作流动作要求 `chat_id`、`message_id`、`part_id`、`workflow_id`、`action`，并按动作提供 `answers` 或 `revision`。服务端校验会话归属、消息/part 精确匹配、状态版本、允许动作和严格候选快照。遥测接口当前只接受数据可视化工作流的 `chart_render_failed`。

附件上传结果应原样放入下一次 Chat 请求的 `attachments`。预览接口只允许访问当前用户上传且媒体类型为图片的附件。

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

## SkillController

基础路径：`/api/v1/dih/skills`

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| GET | `/api/v1/dih/skills/list` | `SkillSearchDto` | 分页 Skill 列表 |
| GET | `/api/v1/dih/skills/agents` | query `enabled?` | 内置 Agent Skill 状态 |
| GET | `/api/v1/dih/skills/chat-entries` | query `enabled?` | 输入区可展示的聊天入口 |
| GET | `/api/v1/dih/skills/options` | query `enabled=true` | AI 分析任务可选的已启用 Skill |
| GET | `/api/v1/dih/skills/{id}/view` | Skill ID | 元数据和入口文件内容 |
| POST | `/api/v1/dih/skills/reload` | 无 | 重新扫描 `app.paths.skills` |
| POST | `/api/v1/dih/skills/{id}/enable` | Skill ID | 启用并写回 `skill.json` |
| POST | `/api/v1/dih/skills/{id}/disable` | Skill ID | 停用并写回 `skill.json` |
| GET | `/api/v1/dih/skills/agent/{agentType}/prompt` | Agent 类型 | 当前加载的 Skill 提示词片段 |

`/options` 只允许 `enabled=true`，AI 分析任务不能选择未启用 Skill。任务只保存 Skill ID，真正执行时读取最新 Skill 内容和工具边界。TypeScript 示例见[概览与接入](概览与接入.md#typescript)。

## VectorStoreQueryController

基础路径：`/api/v1/dih/vectorstore`

所有接口受 `app.ai.vectorstore.management.enabled` 控制，默认关闭。未启用时返回无权限错误。

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| GET | `/api/v1/dih/vectorstore/documents` | query `keyword?`、`source?` | 全部匹配文档 |
| GET | `/api/v1/dih/vectorstore/documents/list` | 分页、`keyword?`、`source?` | 分页文档 |
| GET | `/api/v1/dih/vectorstore/document/{documentId}` | 文档 ID | 文档详情 |
| DELETE | `/api/v1/dih/vectorstore/document/{documentId}` | 文档 ID | 删除单文档 |
| DELETE | `/api/v1/dih/vectorstore/documents` | query `documentIds` | 批量删除 |
| POST | `/api/v1/dih/vectorstore/search` | query `query`、`topK=5`、`source?` | 相似度搜索列表 |
| GET | `/api/v1/dih/vectorstore/search` | 分页、`query`、`topK=5`、`source?` | 相似度搜索分页 |

`RagDocumentVo` 包含 `id`、`text`、`metadata`、`source`。管理接口用于运维和排障，插件文档的正常入库由插件生命周期处理。

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

`approval_mode` 必填：

- `AUTO`：`ALLOW` 直接执行，`ASK` 自动批准并记录，`DENY` 禁止。
- `MANUAL`：`ASK` 进入 `WAITING_APPROVAL`，等待任务创建人或超级管理员处理。

任务状态包括 `PENDING`、`RUNNING`、`WAITING_APPROVAL`、`CANCELING`、`SUCCESS`、`FAILED`、`CANCELED`。活动任务不能编辑或删除。重新入队会生成新的 `execution_id`，清除旧结果、错误、执行时间和旧 execution 工具授权。

任务审批决定支持 `approved`、`approved_task`、`rejected`。`approved_task` 仅对当前 execution 和精确 `tool_key` 持续有效。取消任务会终止待审批请求。任务对应的 MCP 工具见 [AI 分析任务工具](/08-API参考/MCPtool/AI分析任务工具.md)。

## 相关使用与运维

- [AI 问答与会话](/04-AI与数据智能/AI问答与会话.md)
- [业务智能体与工作流](/04-AI与数据智能/业务智能体与工作流.md)
- [MCP 服务与工具审批](/04-AI与数据智能/MCP服务与工具审批.md)
- [AI 分析任务与 Skill](/04-AI与数据智能/AI分析任务与Skill.md)
- [RAG 与知识库运维](/04-AI与数据智能/RAG与知识库运维.md)
