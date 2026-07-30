# DIH 数据接入与数据可视化工作流

本文说明 DIH 中数据接入和数据可视化智能体的确定性工作流、MCP 证据、审批边界、
无模型演示及图表产物协议。实体分析请求和响应字段见
[EntityAnalyticsController](../05-API参考/控制器/EntityAnalyticsController.md)，聊天接口见
[ChatController](../05-API参考/控制器/ChatController.md)。

## 设计目标

- 普通数据可视化必须先查询真实 Meta，再让用户确认实体、字段和统计口径。
- 实体、字段、查询、配置写入和读回结果都以真实 MCP 调用为证据，不采信模型的文字声明。
- 已批准的查询和配置参数由服务端锁定，后续执行不得重新生成或替换。
- 高风险 MCP 写操作必须经过平台审批，审批拒绝或读回不一致时不得生成成功记录。
- 内置示例在没有配置模型时仍可完整演示，但不会拦截普通自由输入。
- 图表预览、图表库和手动刷新使用同一份 ECharts JSON，不执行任意 SQL、URL、
  amis adaptor 或 JavaScript。

## 普通请求的共享工作流

共享实现由以下组件组成：

| 组件 | 职责 |
| --- | --- |
| `WorkflowOrchestrator` | 根据智能体定义补充卡片元数据并校验状态转换 |
| `WorkflowStateStore` | 在会话 `extra_data.agentWorkflows` 中保存工作流状态 |
| `WorkflowEvidenceService` | 引用真实 MCP 调用审计，形成脱敏证据摘要 |
| `WorkflowActionService` | 精确校验会话、消息、part、工作流、修订号和允许动作 |
| `WorkflowMetrics` | 记录阶段耗时、非法转换、失败和图表渲染异常 |

普通工作流卡片包含：

- `workflowId`、`workflowVersion`、`stateRevision`
- `step`、`objectType`、`action`、`allowedActions`
- `evidenceRefs`、`validationStatus`

服务端保留 `extra_data.agentWorkflows`。客户端更新会话扩展数据时不能覆盖该字段。
工作流动作使用 `POST /api/v1/dih/chat/workflow/action`，支持：

- `submit`
- `approve`
- `reject`
- `revise`
- `retry`
- `add_to_library`

接口必须精确匹配 `chat_id`、`message_id`、`part_id` 和 `workflow_id`。旧卡片、过期
`stateRevision`、不在 `allowedActions` 中的动作或与服务端状态不一致的跳步请求都会被拒绝。
返回的 `continuation` 由服务端生成，前端不拼接业务提示词。

## 数据接入普通流程

数据接入支持三类意图：

- 只创建或更新 Meta。
- 直接创建 PushTask/Vectum 数据推送。
- 完成 Meta 后继续创建数据推送。

Meta 主流程：

```text
信息收集 → config_tree → 候选配置确认 → 写前重查
→ 可选覆盖确认 → config_add/config_apply → config_read → 完成或阻塞
```

规则：

- 候选配置的完整内容和 SHA-256 摘要在确认后锁定。
- 新文件依次执行 `config_add` 和 `config_apply`；已有文件内容不同时必须单独确认覆盖。
- 同名同内容按幂等成功处理。
- `config_read` 的 JSON 语义与候选配置一致后，才能生成元数据成功记录。
- 业务卡上的“添加配置到系统”只代表进入写入流程，不能替代 MCP 审批。

PushTask 主流程：

```text
信息收集 → 候选配置确认 → push_task_detect_format
→ sourceMark 冲突检查 → 创建或复用 → 状态读回
→ system 日志验证 → 有证据修复 → 完成或阻塞
```

规则：

- 多任务冲突时停止，不猜测要复用的任务。
- 创建响应异常后进行一次按 `sourceMark` 读回，避免重复创建。
- 成功记录要求取得唯一任务、运行状态和本轮 system 日志证据。
- 自动修复最多五轮，只能修改日志已经证明错误的配置；网络、认证、权限和密钥问题直接阻塞。

## 数据可视化普通流程

普通请求固定执行：

```text
意图确认 → 实体 Meta → 严格实体选择 → 字段 Meta
→ 查询方案确认 → 精确数据查询 → ECharts 产物
→ 渲染 → 可选图表库或配置写入 → 读回验证
```

### Meta 与确认

1. 实体候选来自 `retrieval_list_display_entity` 或 `retrieval_list_entity`。
2. 用户选择后，平台使用返回的准确逻辑 `name` 调用
   `retrieval_list_display_attribute` 或 `retrieval_list_attribute`。
3. 系统来源选项设置 `strictOptions=true`，并在服务端保存候选快照。
4. 提交值必须同时存在于当前卡片和服务端候选快照；自由输入、篡改值、过期候选和编造字段都会被拒绝。
5. 查询确认卡展示实体、时间字段、指标、维度、过滤、排序、目标 MCP 工具及完整请求。

### 查询工具选择

| 需求 | MCP 工具 |
| --- | --- |
| 指标卡 | `entity_summary` |
| 简单时间趋势 | `entity_trend` |
| 计数 TopN | `entity_distribution` |
| 任意指标分组、分组趋势、双维透视或热力图 | `entity_aggregate` |
| 数值分布 | `entity_histogram` |
| 相关性或气泡图 | `entity_scatter` |
| 明细表 | `retrieval_search` 或 `entity_list` |
| 关系图和关系时间线 | `entity_relations`、`entity_relation_timeline` |

用户批准方案后，平台严格执行卡片内的 `query.tool/query.request`。Agent 不得替换工具、
修改参数、传递 SQL 或使用演示数据。

### 图表产物

可入库图表保存：

- `artifactId`、`planId`
- 实体及字段角色清单
- `query.tool`、`query.request`
- `queryMeta`
- `echartsOption`
- `amisConfig`
- `queriedAt`、`validationStatus`、`source`

实体分析接口统一返回 `meta`、`result.columns/rows` 和 `echarts.chart_type/option`。
`echarts.option` 是纯 JSON，可直接交给 ECharts。对话和右侧图表库共同使用
`SafeEcharts`，统一处理加载、容器尺寸、空数据、非法配置和渲染失败。

图表库行为：

- “加入图表库”以 `artifactId` 幂等复制当前已验证快照，不要求 Agent 重新生成。
- 操作栏固定提供配置、预览、刷新和复制入口。
- 预览使用入库时的 `echartsOption`；历史记录缺少快照时显示明确错误，不隐藏预览入口。
- 手动刷新只允许调用固定实体分析白名单，并复用原 `query.tool/query.request`。
- 刷新成功后更新 `queryMeta`、`echartsOption` 和查询时间；失败时保留旧快照。
- 缺少安全查询参数的历史记录禁用刷新。

### 配置、看板和菜单

- 配置写入执行检查、创建或覆盖确认、应用、`config_read` 和 JSON 语义校验。
- Dashboard 创建后调用 `dashboard_view` 读回。
- Menu 创建前查询类型、父菜单和同名记录，创建后调用 `menu_view` 读回。
- 创建失败、审批拒绝、读回缺失或关键字段不一致时，不保存成功记录。

## 确定性无模型演示

内置示例在模型可用性检查之前精确路由，并将状态保存在
`extra_data.agentDemos`。普通自由输入不会模糊命中演示。

当前示例包括：

- 数据接入：用户事件完整接入演示 1 个。
- 数据可视化：临时图表、单页面应用、带侧边栏应用、数据看板、添加菜单 5 个。
- 报表制作：固定报表示例 4 个。

演示消息携带 `demoId`，不携带普通工作流的 `workflowId`。

数据接入演示仍调用真实 MCP：

- `config_tree → [config_add] → config_apply → config_read`
- `push_task_detect_format → list → [create_and_start] → list → get_log`

数据可视化的应用、看板和菜单演示也调用真实配置、Dashboard 和 Menu MCP，并进行读回。
临时图表加入图表库时同时保存演示 ECharts 快照和 amis 配置。

内置演示中的默认 `ASK` 写工具强制逐次显示真实 MCP 审批卡，即使管理员曾把该工具设为
`ALLOW`，或当前会话已有同工具授权，也不能跳过演示审批。演示审批只支持“允许本次”和
“拒绝执行”，不提供“本会话始终允许”。全局 `DENY` 仍然优先并会阻止底层工具执行。

## 业务确认与 MCP 审批

两类确认不能互相替代：

- 业务确认决定是否进入查询、写入或创建阶段。
- MCP 审批决定某一次高风险工具是否允许执行。

普通工作流业务卡调用 `/api/v1/dih/chat/workflow/action`；内置演示卡继续使用演示动作协议。
工具审批统一调用 `/api/v1/dih/mcp/approvals/{requestId}/decision`。

事件流顺序为：

1. 工具开始调用并产生审计记录。
2. `ASK` 工具发送 `approval_required`。
3. 前端显示 `mcp-approval` 卡片。
4. 用户审批后发送 `approval_updated`。
5. 原工具回调继续；成功后才生成配置、菜单、看板或任务记录。

## 失败账本与排障

工作流失败记录阶段、工具、请求摘要、真实错误、是否可重试、重试检查点和保留的产物 ID。
读操作瞬时失败最多自动重试两次；写操作不盲目重试，而是依靠幂等标识和读回判断。

常见检查：

| 现象 | 检查项 |
| --- | --- |
| 实体选择没有选项 | 实体 Meta MCP 是否成功、返回是否为空、卡片是否有 `evidenceRefs` |
| 字段是编造值 | 字段 Meta 调用参数是否使用所选实体准确 `name`，卡片是否为严格选项 |
| 查询确认后没有图表 | 实际工具和请求是否与批准方案一致，响应是否包含 `echarts.option` |
| 图表无法渲染 | `echartsOption` 是否为纯 JSON，查看 `chart_render_failed` 失败账本 |
| 图表库没有预览 | 重新加载前端；旧记录缺少快照时预览弹窗会给出提示 |
| 演示没有审批卡 | 使用 `response_format=events`，确认后端已重启且调用的是默认 `ASK` 写工具 |
| 写入后没有成功记录 | 检查 MCP 审批、工具返回和 `config_read`/查看接口读回结果 |
