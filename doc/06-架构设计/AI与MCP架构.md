# AI 与 MCP 架构

本文说明 DIH 会话、RAG、业务 Agent、Skill、Workflow、MCP、审批审计、分析任务和报表能力之间的关系。

## 能力全景

```text
DIH 前端
  ├── 普通问答 / 兼容单轮 → ChatController → DihChatApplicationService
  │     ├── 普通问答 → AIChatService → 全库 RAG → OpenAI 兼容模型
  │     ├── 深度思考 → 推理模型 + 全库 RAG + reasoning 消息
  │     ├── 元数据配置 → MetadataConfigAgent → Workflow Orchestrator
  │     ├── 数据推送服务 → DataPushServiceAgent → Workflow Orchestrator
  │     ├── 临时图表 → VisualizationChartAgent → 受控只读 MCP Tool
  │     ├── 可视化应用 → VisualizationAppAgent → 受控配置/菜单 MCP Tool
  │     ├── 数据大屏 → VisualizationDashboardAgent → 受控配置/看板/菜单 MCP Tool
  │     └── 专项 Skill → PromptDrivenAgentRuntime → 绑定范围 RAG
  │
  └── 持久化业务 Agent → AgentRunController
        → AgentRunService / AgentRunScheduler / AgentRunExecutor
        → Plan · Steer · Checkpoint · Event · Approval
        → Skill · MCP · RAG · ReportDraft · Report Revision
```

DIH 是应用编排层，不是单一模型代理。它先确定会话分支，再为该分支配置上下文、模型、Skill、工具和输出协议。

## 会话与消息

### 两类持久化

| 数据 | 存储 | 用途 |
| --- | --- | --- |
| ZenVis 会话与消息 | MySQL 平台表 + `zenvis-chat` | 页面历史、附件元信息/归属、结构化消息和业务状态；附件正文在 MinIO |
| Spring AI Chat Memory | MySQL JDBC Chat Memory | 提供给模型的多轮上下文 |

两者不能互相替代。删除或截断模型上下文不应破坏页面历史，页面消息迁移也不能假设模型记忆自动同步。

### 结构化消息

服务端流式返回文本、思考过程、配置记录、审批卡片、数据分析、图表、报表片段等消息部件。前端按 `type` 渲染并根据 `status` 控制交互。

```text
模型/工作流事件
  → 规范化 Message Part
  → NDJSON 流
  → 前端增量合并
  → 会话消息持久化
```

工具和 RAG 不进入正文 Markdown 日志：

- MCP 调用使用 `tool_call` 事件，同一 `callId` 的 `running`、`succeeded`、`failed` 更新同一条 `tool-call` 部件；
- 自动 RAG 使用 `rag_search` 事件，每轮每次检索生成一条 `rag-search` 部件；
- `done` 携带最终消息，正文只保留模型回答，工具和检索记录作为独立结构化部件持久化。

这样前端可以按状态渲染折叠卡、限制长结果高度并支持复制，同时避免模型输出被工具 JSON 污染。历史会话中的旧 Markdown 工具日志只在展示层折叠转换，不执行数据库回填。

普通问答和未启用持久化运行的 Agent 使用 Chat NDJSON；Agent Run 使用独立的 Run 事件流。Run 的 `assistant.delta`、`tool.*`、`approval.*` 和 `artifact.updated` 由运行卡消费，最终答复和报表产物再写回会话消息与工作台。两套事件都要求客户端按稳定 ID 合并，但 Run 额外要求按 `sequence` 去重和断线重放。

Run 消息保存 `runId`，前端据此把每轮任务卡挂回对应 AI 消息。会话重载时不能只恢复当前活动 Run，而要先读取消息引用的全部 Run，再为活动 Run 建立实时订阅；否则历史轮次会在 `run.succeeded` 触发重载后消失。

确认、审批和执行结果必须写回消息状态，刷新页面后不能重新出现已完成动作。

## 普通问答与 RAG

普通问答和深度思考在所有未删除知识库中自动执行 RAG，但不加载业务 Skill 和 MCP Tool：

```text
用户问题
  → 附件与历史上下文
  → 未删除知识库 source 过滤 + Redis Vector Store 相似度召回
  → Prompt + RAG Context
  → 模型流式回答
```

知识库向量文档使用 `source=kb_<knowledge_base_id>` 标识。普通问答把所有未删除知识库 ID 转为该标签的 `in` 过滤条件；业务会话只使用所选 Skill 绑定知识库的 ID。范围过滤复用现有 Redis 索引字段，不新增向量字段或数据库迁移。

RAG 原文和插件数据集以 `zenvis-rag` 为权威存储。Redis 只保存可重建的切分文本、Embedding 和索引元数据；对象缺失时知识库构建直接失败，不回退读取旧 Bucket 或本地文件。

向量能力可通过配置关闭。关闭后普通聊天仍可使用模型，但依赖 RAG 的管理和召回能力不可用。

## 业务 Agent 与 Skill

业务 Agent 只在用户显式选择 Agent 或 Skill 后启用。Skill 提供：

- 适用 Agent 类型。
- 业务提示词和领域约束。
- 本地工具 allowlist。
- 外部 MCP Server 与工具 allowlist。
- 可选的单轮资源上限覆盖。
- 可选的 RAG 知识库绑定；绑定后平台每轮自动注入该范围内的参考资料。

业务 Agent / Skill 的 RAG 范围取所选启用 Skill 绑定知识库的并集。没有绑定知识库时不执行检索，也不回退到全库。RAG 上下文直接并入系统提示词，不注册为 MCP 工具，因此不占用工具名额、工具调用预算或审批流程。

运行时工具集合取以下范围的交集：

```text
平台已注册工具
  ∩ Agent scope
  ∩ Skill allowlist
  ∩ 当前会话或任务授权
  ∩ 全局工具策略
```

没有工具声明的 Skill 不会自动获得全部工具。显式空列表表示对应范围不允许任何工具。

## 持久化 Agent Run

Agent Run 把一个业务目标从 HTTP 请求生命周期中拆出来，保存为可恢复的执行实体。除普通问答外，所有业务 Agent 类型默认统一使用该执行模型；`enabled-types=*`，并可通过显式白名单或 `disabled-types` 做灰度与应急回滚。业务工作流卡片需要用户操作时，Run 保存结构化消息并进入 `WAITING_USER`，用户动作通过 steer 恢复同一任务。

```text
创建或 steer
  → AgentRunService 锁定会话并保存 Run / Input
  → AgentRunScheduler CAS 认领并获取租约
  → AgentRunExecutor 执行一次模型决策
  → 保存计划、步骤、检查点、工具计数和事件
  → QUEUED / WAITING_APPROVAL / WAITING_USER / PAUSED / SUCCEEDED
```

### 核心组件

| 组件 | 职责 |
| --- | --- |
| `AgentRunService` | 创建、steer、暂停、继续、取消、状态迁移、事件追加和快照查询 |
| `AgentRunScheduler` | 数据库 CAS 认领、并发槽、租约、心跳和启动恢复 |
| `AgentRunExecutor` | 单次模型决策、工具执行、审批恢复、结构化卡片等待、截断重试和完成判定 |
| `AgentRunContextAssembler` | 组装目标、计划、摘要、近期对话、steer、证据和附件，并在真实裁剪时产生 `context.compacted` 诊断 |
| `AgentRunInternalToolProvider` | 提供计划、步骤、用户提问和报表草稿控制工具 |
| `ReportDraftService` | 保存章节草稿，校验后生成不可变报表修订 |
| `AgentRunMetrics` | 记录队列、步骤、审批、恢复、截断、终态和失败指标 |

### 持久化数据

| 数据 | 用途 |
| --- | --- |
| `t_ai_agent_run` | 目标、计划、状态、预算计数、检查点、租约和最终答复 |
| `t_ai_agent_run_event` | 按 `(run_id, sequence)` 唯一的只追加事件日志 |
| `t_ai_agent_run_input` | steer 和附件，按 `(run_id, client_message_id)` 幂等 |
| `t_ai_report_draft` | 报表章节、顺序、来源引用、哈希和完成状态 |
| `t_ai_mcp_invocation.agent_run_id` | 把工具审批与审计绑定到具体 Run |

Run 的创建在会话行锁和 MySQL 命名锁内完成，确保同一用户、同一会话不会并发创建两个活动 Run。`ChatSession` 和报表草稿使用乐观锁版本，消息追加在事务内完成，避免后台执行、steer 和多端恢复互相覆盖。

### 执行可观测性

运行卡通过独立的可观测层展示检索、工具调用和上下文压缩，不把工具 JSON 或检索正文混入最终答复，也不重复持久化事件明细。

| 数据源 | 归并方式 | 展示信息 |
| --- | --- | --- |
| `rag.search` | `searchId` | 查询范围、状态、命中数、耗时 |
| `tool.started` / `tool.succeeded` / `tool.failed` | `callId` | 工具名、生命周期、参数、结果、错误和耗时 |
| `tool.approval_*` | `requestId` | 审批状态及关联工具 |
| `context.compacted` | `compactionId` | 阶段、原因、前后 Token/字符、预算、省略消息和附件数、摘要预览 |

归并器按 `sequence` 去重并按首次发生顺序排列。活动事件可通过 `types` 查询，避免 `assistant.delta` 占满历史窗口；达到 500 条时以最后序号继续分页。任务卡和任务中心复用同一视图，详细数据默认折叠。

上下文压缩只在 `run-context`、`model-input`、`history-window` 或 `attachment-memory` 阶段真实发生裁剪、摘要或附件正文移除时记录。历史 Run 不回填事件；缺少记录时明确标记为旧任务未采集诊断。

### 状态与恢复

活动状态为 `QUEUED`、`RUNNING`、`WAITING_APPROVAL`、`WAITING_USER`、`PAUSED` 和 `CANCELING`；终态为 `SUCCEEDED`、`FAILED` 和 `CANCELLED`。等待状态释放租约和并发槽，终态不会阻止会话创建下一个 Run。

调度器默认 1 秒扫描一次、单实例并发 4、租约 60 秒。worker 定期续租；租约过期后其他实例可重新认领，连续恢复 3 次仍无法稳定执行时标记失败。服务启动会把心跳超时的中断 Run 重新入队，同时保留等待中的 Run 审批，服务重启前已发出但未记录结果的工具调用则标记为 `UNKNOWN` 并暂停 Run。

### 计划、完成与预算

模型通过 `run_update_plan`、`run_update_step`、`run_request_user` 和 `run_finish` 维护显式计划。控制器只在计划全部 `DONE/SKIPPED`、无等待动作、报表已生成正式修订并存在最终答复时判定成功。模型只输出文本但没有推进计划时自动重新入队；连续 3 次无进展则暂停。

Run 默认最多 30 个决策步、30 分钟有效执行和 80 次工具调用。Skill 的 `maxToolCalls` 作为单步上限，Run 总预算跨步骤累计。等待审批和等待用户不消耗有效执行时间。`finish_reason=length` 视为当前步骤未完成，最多自动重试 2 次，之后以 `OUTPUT_TRUNCATED` 暂停。

## Workflow Orchestrator

元数据配置、数据推送服务、临时图表、可视化应用和数据大屏使用共享工作流基础设施，但各自只接受本领域步骤与动作：

| 组件 | 职责 |
| --- | --- |
| `WorkflowDefinition` | 定义步骤、转移和完成条件 |
| `WorkflowOrchestrator` | 推进步骤并协调 Agent、工具和用户确认 |
| `WorkflowStateStore` | 保存会话工作流状态 |
| `WorkflowEvidenceService` | 保存 MCP 查询证据和严格候选 |
| `WorkflowActionService` | 执行精确、可审计动作 |
| `WorkflowMetrics` | 记录步骤、失败和耗时 |

工作流保存已确认参数、工具证据、失败账本和下一步动作。模型不能通过自由文本跳过必需的确认与审批。

## 元数据配置 Agent

```text
METADATA_INPUT_COLLECTION
  → config_tree
  → Meta 候选确认
  → 写前重查
  → 可选覆盖确认
  → config_add / config_apply
  → config_read
  → 成功记录或阻塞
```

工具白名单仅包含 `config_tree`、`config_read`、`config_add`、`config_apply`。候选文件、内容摘要和计划 ID 在确认后由服务端锁定；覆盖必须单独确认，读回与候选语义一致前不能记录成功。

## 数据推送服务 Agent

```text
PUSH_INPUT_COLLECTION
  → config_tree(type="meta") + config_read（只读）
  → PushTask 候选确认
  → push_task_detect_format
  → sourceMark 冲突检查
  → 创建或复用
  → 状态读回
  → system 日志验证
  → 成功记录或阻塞
```

- 工具白名单包含 `config_tree`、`config_read` 和 `push_task_*`，不包含 `config_add/config_apply`。
- 唯一业务输入依赖是已发布 Meta 的只读契约；不存在 Agent 间自动调用。
- 缺失 Meta 时停止写入并返回 `missing_meta` 深链，不创建任务。
- Meta 内容摘要变化后必须重新确认推送配置，不能静默沿用旧方案。
- 修复最多 5 轮，每轮必须引用本轮 system 日志证据。

## 可视化智能体

临时图表、可视化应用和数据大屏共享只读查询基础设施，但会话入口、Skill、工具白名单、Workflow 和结果分区完全独立：

| Agent | Workflow | 写入边界 |
| --- | --- | --- |
| `VisualizationChartAgent` | `visualization_chart` | 无配置、Dashboard、菜单或 Meta 写工具；只生成临时图表和图表库快照 |
| `VisualizationAppAgent` | `visualization_app` | 只写配置与菜单；不写 Dashboard、图表库或 Meta |
| `VisualizationDashboardAgent` | `visualization_dashboard` | 写配置、Dashboard 与菜单；不写图表库或 Meta |

- 三者都不接入外部 MCP、不执行任意 SQL，也不新增、修改或删除动态实体数据。
- 先查询实体和字段 Meta，再调用实体分析白名单；`entity_aggregate`、`entity_histogram`、`entity_scatter` 仅对这三个可视化 Agent 类型开放，且仍需 Skill 显式允许。
- 应用和大屏的配置、Dashboard、菜单写入默认进入审批，创建后必须通过 `config_read`、`dashboard_view` 或 `menu_view` 读回校验。
- 图表预览是临时产物；应用配置、看板和菜单是持久化产物。只有临时图表可产生图表库记录。
- 三个 Agent 之间没有自动调用或跨领域续跑。缺少 Meta 时只返回本领域 `missing_meta` 卡和前往元数据配置的预填深链。

## 本地与外部 MCP

### MCP Server

ZenVis 内置 MCP Server 通过 SSE 与消息端点暴露本地工具。工具由 `McpServerToolConfiguration` 注册，并通过独立 MCP Bearer Token 认证。

### MCP Client

ZenVis 也可以作为 MCP Client 连接外部 Server：

```text
数据库中的 MCP Server 配置
  → McpClientService 初始化连接
  → 运行期发现工具
  → 原始名称 + Server code 形成稳定身份
  → 进入策略、Agent scope 和审批链路
```

外部工具是动态内容。文档和 Skill 只声明发现与允许机制，不假设运行期工具永远不变。

## 策略、审批与审计

每个工具具有默认策略和风险等级：

| 策略 | 行为 |
| --- | --- |
| `ALLOW` | 满足 scope 和授权后直接执行 |
| `ASK` | 创建审批请求，批准后执行 |
| `DENY` | 拒绝执行，其他授权不能覆盖 |

```text
工具候选
  → 工具身份规范化
  → 全局策略
  → Agent scope / Skill allowlist
  → 会话或任务授权
  → ALLOW / ASK / DENY
  → 执行状态与审计
```

审批支持会话内联决策、持久化 Agent Run 决策和后台分析任务决策。Run 中的只读工具在未被 `DENY` 时自动执行；外部写入等 `ASK` 工具进入持久化审批，默认等待 24 小时，不持有 worker。`approved_run` 允许当前 Run 内同一精确工具复用授权。请求、批准、拒绝、超时、取消、执行中、成功和失败都需要形成可查询状态。

## AI 分析任务

分析任务把业务 Agent 放入后台调度：

```text
创建任务
  → 选择模型、Agent、Skill 和审批模式
  → 入队
  → 调度器按并发限制领取
  → Agent 执行与工具审批
  → 结果、日志和审计落库
```

自动审批模式只自动批准符合条件的 `ASK` 工具，不覆盖 `DENY`。任务取消、挂起、恢复和失败必须遵循状态机，不能通过直接改数据库跳过。

## 报表文档

报表 Agent 生成或修改版本化文档：

- 会话保存当前报表关联。
- 文档更新使用版本检查避免覆盖并发修改。
- 用户可以归档、恢复和导出。
- 导出服务根据当前文档生成目标格式。
- 报表工具范围独立于元数据配置、数据推送服务、临时图表、可视化应用和数据大屏 Agent。

启用 Agent Run 时，报表 Agent 先创建草稿，再通过受控工具逐章节写入，最后由 `report_finalize` 生成正式修订。单章目标约 1,200 字、硬上限 2,400 字符；已写入章节独立保存，当前章节失败不会删除其他成果。Run 只有在存在已 finalize 草稿时才可成功结束。

未启用 Run 或旧客户端仍可能使用完整的 `zenvis:report-document-config` 围栏。该路径受模型输出 Token 上限约束，检测到 `finish_reason=length` 或围栏未闭合时会丢弃不完整报表，避免半份内容覆盖正式文档。长报表应优先使用持久化 Run 的章节化写入。

### 存储、修订与导出

- `ReportDocument.content` 是当前可编辑正文的权威副本，继续保存在 MySQL，支持并发修订检查和文档工作区读取。
- 正式修订写入 `zenvis-reports` 的固定对象键 `documents/{documentId}/revisions/{revision}/content.{md|html}`。`ReportRevision` 和 `ReportArtifact` 只保存元信息，新记录正文为 `NULL`；归档产物复用同一修订对象，不复制第二份正文。
- 保存修订时先写对象再提交数据库。写入前记录对象是否已存在，事务回滚时只删除本次新建的对象，避免误删可复用修订。
- 归档、恢复、素材摘要和工作区读取从 MinIO 读取修订正文。对象缺失时返回明确业务错误，不回退读取 MySQL 正文字段。
- DOCX/PDF 导出按 `documentId + revision + sourceFormat + title + contentHash + exporterVersion` 计算 `exportHash`，并以目标格式区分对象键 `documents/{documentId}/revisions/{revision}/exports/v1/{format}/{exportHash}.{docx|pdf}`；对象已存在时直接复用。
- 导出接口保持原 URL 和下载行为，由后端鉴权后返回 MinIO 流式响应；不签发 presigned URL。

本次迁移不回填历史修订。旧修订没有对应对象时不能恢复或导出，应在新的修订流程下重新生成。

## 资源上限

平台分别限制单次模型步骤和整个 Agent Run：

- 工具调用次数。
- 单次与累计工具结果字符数。
- 累计工具结果 Token。
- 重复失败次数。
- 上下文窗口、附件和历史消息预算。
- 审批等待时间和后台任务并发数。

Run 额外限制决策步数、有效执行时间、全 Run 工具调用总数和租约恢复次数。等待审批、等待用户和用户暂停不占 worker，也不计入有效执行时间。

Skill 可以在允许范围内提供正整数覆盖；缺失或非法值回退平台默认。达到上限后应基于已有证据作答或说明限制，不能通过递归和工具改名绕过。

工具调用预算只统计通过参数 Schema 校验并进入工具执行阶段的调用。模型返回非法参数时，平台先请求一次修正；未执行的非法调用不扣预算，因此“界面显示的实际调用数”和“模型尝试数”可能不同。审批拒绝和策略拒绝发生在参数校验之后，仍可能占用一次预算，但不会产生底层工具的业务结果。

## 关联文档

- [AI 与数据智能](/04-AI与数据智能/README.md)
- [AI 问答与会话](/04-AI与数据智能/AI问答与会话.md)
- [智能体任务与持久化运行](/04-AI与数据智能/智能体任务与持久化运行.md)
- [业务智能体与工作流](/04-AI与数据智能/业务智能体与工作流.md)
- [MCP 服务与工具审批](/04-AI与数据智能/MCP服务与工具审批.md)
- [AI 分析任务与 Skill](/04-AI与数据智能/AI分析任务与Skill.md)
- [RAG 与知识库运维](/04-AI与数据智能/RAG与知识库运维.md)
- [MCP Tool 总览](/08-API参考/MCPtool/总览与接入.md)
- [权限审批与运行约束](/08-API参考/MCPtool/权限审批与运行约束.md)
