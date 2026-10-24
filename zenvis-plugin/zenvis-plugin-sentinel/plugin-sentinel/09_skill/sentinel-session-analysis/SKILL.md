# 智能体会话现场回放

## 目标与触发场景

对 Warden 会话流（实体 `sentinel_chat_session_event`）做只读重建，并按 `message_id` / `msg_hash` 关联 Sentinel 安全事件（实体 `sentinel_security_event`），回答“命中了哪条消息、消息前后发生了什么、是否发生历史改写”。触发场景：

- 用户给定 `session_id`，要求还原消息线或查看完整会话现场；
- 用户给定安全事件，要求定位其命中的消息原文与上下文；
- 用户给定 `message_id` / `msg_hash`，要求查找所属会话与安全事件；
- 用户要求审计 `mutation` 历史改写。

不处理：写库、修改会话、执行拦截、删除记录或访问两个实体以外的数据。本 Skill 只读，不产生副作用。

## 输入与拒绝条件

必需输入之一：`session_id`、`message_id`、`msg_hash`，或一个明确的 Asia/Shanghai 时间窗。

拒绝或要求澄清：

- 只有模糊描述且没有会话或消息标识；
- 时间窗起止缺失或无法解析；
- 用户要求修改会话内容、伪造消息线或执行处置动作。

## 工具范围（严格白名单）

| 工具 | 用途 | 使用条件 |
| --- | --- | --- |
| `retrieval_list_entity` | 确认真实实体逻辑名 | 首次调用，确认两个实体存在 |
| `retrieval_list_attribute` | 确认字段逻辑名与含义 | 首次调用，确认本 Skill 引用字段 |
| `retrieval_search` | 检索事件或消息记录 | 单次最多 50 条，必要时分页 |

字段一律使用逻辑名，不得使用物理表名或列名。不得声称拥有写入、阻断或外部 MCP 工具。

## 固定步骤

1. **确认元数据**：确认 `sentinel_chat_session_event`、`sentinel_security_event` 及所需字段存在。会话实体缺失则停止；安全实体缺失时仍可回放消息线，但必须说明无法关联命中。
2. **拉取会话事件**：按 `session_id equal <值>` 检索 `sentinel_chat_session_event`，以 `occurred_at` 升序为主排序，`zenvis_insert_time`、`event_key` 为辅；按 `event_key` 去重重投记录。
3. **重建消息线**：维护 `Map<msg_hash, message>`：
   - `started`：只增加 started 计数，不产生消息；
   - `append`：将 `messages[]` 按 `msg_hash` 合并；
   - `snapshot`：用 `messages[]` 覆盖当前全量视图；
   - `mutation`：删除 `old.msg_hash`，加入 `new`。
   最终按 `msg_index` 升序排序，`msg_index=-1` 的 system / instructions 伪消息排在最前。
4. **定位命中消息**：优先用安全事件的 `message_id` 匹配，其次用 `msg_hash`；展示 `original` 原文，并注明 `role`、`msg_index` 与相邻消息。
5. **关联安全事件**：按 `session_id` 检索 `sentinel_security_event`，需要时再按 `message_id` / `msg_hash` 缩小范围；按 `occurred_at` 排序并标出命中消息所在轮次。
6. **审计改写与完整性**：统计 started / append / snapshot / mutation 数量，说明 `started` 是否为 1、是否发生 snapshot 覆盖、mutation 替换了哪些旧消息、查询是否截断。
7. **输出结果**：按下方固定格式输出。

## 输出格式

```text
结论：<一句话说明消息线是否完整、命中了哪条消息>

会话摘要
- session_id：<值>
- session_id_source：<header / body_field / derived_hash>
- 事件数：<started / append / snapshot / mutation>
- 重建消息数：<数量>
- 完整性：<started 数量、snapshot 覆盖、mutation 改写、是否截断>

消息线
| msg_index | role | message_id | msg_hash | 内容摘要 | 关联安全事件 |
|---|---|---|---|---|---|

命中现场
- 事件ID：<event_id 或“无”>
- 判定：<severity / category / decision_point / action>
- 命中消息：<message_id / msg_hash / role / msg_index>
- 原文摘录：<original 中的原文，注明是否截断>
- 前后上下文：<相邻消息摘要>

审计说明
- <mutation 改写详情、snapshot 覆盖说明或数据缺口>
```

## 完成标准

- 消息线中的每条消息都能追溯到会话事件与原始 `messages[]`；
- 不得把 `append` 重投、`snapshot` 覆盖或 `mutation` 旧消息误算为独立新消息；
- 安全事件与消息的关联必须使用 `message_id` 或 `msg_hash`，不得仅凭文本相似度猜测；
- 明确说明数据覆盖范围、查询条数上限与会话流缺失项。

## 审批与风险动作

本 Skill 无写操作，不需要审批。用户要求处置动作时，指引其走平台流程。

## 降级处理

- **会话实体缺失**：报告 Meta 未加载或未授权并停止。
- **无会话事件**：明确“未检索到该会话事件”，建议核对 session_id 或扩大时间窗；不得编造消息线。
- **未找到 started**：继续展示已有事件，但标注消息线可能不完整。
- **安全事件未匹配到消息**：保留会话现场，明确说明命中消息无法定位，并给出已尝试的 `message_id` / `msg_hash`。
- **工具失败**：同一工具同类失败连续 2 次即停止，报告失败工具与查询参数。
- **预算耗尽**：停止调用，基于已重建部分给出阶段性现场与未完成步骤。
