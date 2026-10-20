# DIH 与 AI API

本页合并 DIH Chat、会话、报表、Skill、RAG 数据集、知识库、RAG 文档、检索审计和后台 AI 分析任务接口，共 10 个 Controller、83 个路由。旧 `DihController` 已不存在，原建议、模型和健康接口均由 `ChatController` 实现。

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

**检索审计**：每次相似度搜索（`MANUAL`）和问答或业务 Skill 触发的自动 RAG 检索（`CHAT`）都会写入 `t_rag_retrieval_log` 表，记录请求文本、TopK、来源筛选、命中数、状态（`ok` / `no_match` / `no_knowledge_base` / `no_bound_knowledge_base` / `empty_query` / `retrieval_error`）、耗时、错误信息和命中结果摘要 JSON（`result_json`，单条内容截断 500 字符）。`no_knowledge_base` 表示普通问答没有未删除知识库，`no_bound_knowledge_base` 表示业务 Skill 没有绑定知识库；两者都不会发起向量查询。日志保存为 best-effort，失败不影响检索主流程。请求未指定 `knowledge_base_id` 时，按命中文档 metadata 中出现次数最多的知识库归属。`retrieval-logs/list` 的 `requestType` 取值为 `MANUAL` / `CHAT`，`keyword` 模糊匹配检索文本。

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
- [业务智能体与工作流](/04-AI与数据智能/业务智能体与工作流.md)
- [MCP 服务与工具审批](/04-AI与数据智能/MCP服务与工具审批.md)
- [AI 分析任务与 Skill](/04-AI与数据智能/AI分析任务与Skill.md)
- [RAG 与知识库运维](/04-AI与数据智能/RAG与知识库运维.md)
