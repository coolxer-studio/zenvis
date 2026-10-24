# 智能体安全态势分析

## 目标与触发场景

基于真实检索统计快照，对 Sentinel 安全事件（实体 `sentinel_security_event`）与 Warden 会话事件（实体 `sentinel_chat_session_event`）做宏观态势分析，输出确定性发现与图表。触发场景：

- 用户要求“分析最新态势 / 最近 N 天安全情况”；
- 用户要求比较不同判定点、协议线、命中类别或会话事件类型的分布；
- 用户要求评估会话消息规模、会话覆盖率或安全事件现场还原的数据基础；
- 用户给出一个明确时间窗要求态势评估。

不处理：单条事件的逐条取证（改用“智能体安全事件研判”）、任何写操作。

## 输入与拒绝条件

必需：一个明确的时间范围（使用 `TODAY`、`LAST_7_DAYS`、`LAST_30_DAYS` 等预置，或 Asia/Shanghai 自定义起止）。

拒绝或要求澄清：时间范围缺失或无法解析；要求输出 SQL、物理表名或越权数据。

## 工具范围（严格白名单）

| 工具 | 用途 |
| --- | --- |
| `retrieval_list_entity` | 确认实体 `sentinel_security_event`、`sentinel_chat_session_event` |
| `retrieval_list_attribute` | 确认维度字段逻辑名 |
| `entity_overview` | 累计量与周期量 |
| `entity_summary` | 单实体多指标汇总 |
| `entity_trend` | 按时间粒度趋势 |
| `entity_distribution` | 按维度 TopN 分布 |

安全事件维度仅使用逻辑字段：`verdict_severity`、`verdict_category`、`verdict_action`、`verdict_decision_point`、`frag_kind`、`request_wire`、`source_policy_version`；会话事件维度使用 `event_kind`、`session_id_source`、`request_wire`。两类实体的时间字段均使用 `occurred_at`。

## 固定步骤

1. 确认实体与字段（`retrieval_list_entity`、`retrieval_list_attribute`）。若会话实体缺失，可仅分析安全事件，但必须说明消息现场分析不可用。
2. `entity_overview`：取指定时间范围内两个实体的累计量与周期量。
3. `entity_trend`（粒度 `HOUR` 或 `DAY`）：安全事件与会话事件趋势，识别爆发时段和两流覆盖偏差。
4. `entity_distribution`：风险等级（`verdict_severity`）与命中类别（`verdict_category`）分布。
5. `entity_distribution`：判定点（`verdict_decision_point`）与协议线（`request_wire`）分布，说明风险面。
6. `entity_summary`：对 `sentinel_chat_session_event` 统计 `DISTINCT_COUNT(session_id)`、`SUM(message_count)` 与 `DISTINCT_COUNT(event_kind)`，说明会话覆盖和消息规模。
7. `entity_distribution`：会话事件类型（`event_kind`）与会话号来源（`session_id_source`）分布。
8. 如需要，对高危类别补充 `entity_summary` 计数。
9. 输出发现与图表。

## 输出格式

1. **态势摘要**（不超过 5 句）：时间范围、安全事件总量、高危占比、最突出类别、会话事件覆盖与会话消息规模。
2. **确定性发现**，按风险排序，每条包含：现象、支撑数据（工具与维度）、判读、建议。
3. **图表**：用 Markdown 表格或代码块描述趋势与分布数据；若平台支持图表输出，给出 ECharts 数据集与 series 的明确字段。
4. **数据覆盖与限制**：说明时间范围、查询粒度、TopN 上限、两流时间字段差异（安全事件 ISO8601、会话事件 epoch 秒已在入库时统一），以及镜像档判定不代表实际阻断的事实；`SUM(message_count)` 是事件消息数组累计量，不宣称等于去重后的最终消息数。

## 完成标准

- 所有数字来自工具返回，不估算、不补全；
- 明确标注“镜像档判定不代表已拦截”；
- 对样本不足（如总量很小）主动提示结论置信度有限。

## 审批与风险动作

只读分析，无需审批。用户要求处置动作时，指引其走平台流程。

## 降级处理

- **实体缺失**：报告 Meta 未加载或未授权并停止。
- **无数据**：输出“该时间范围内暂无事件”，不编造趋势。
- **工具失败**：同类失败连续 2 次即停止，报告失败工具与查询参数。
- **预算耗尽**：停止调用，基于已有统计给阶段性结论并列出未完成维度。
