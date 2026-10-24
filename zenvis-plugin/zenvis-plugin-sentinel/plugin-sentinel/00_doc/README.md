# 智能体安全监测知识说明

本目录是「智能体安全监测」插件的专项文档入口。

- [`数据字典与接入说明.md`](数据字典与接入说明.md)：上游安全事件流与会话流的信封结构、字段映射、枚举定义、Kafka 投递语义、DLQ 与重放说明。

## 基本信息

- **插件名称**：智能体安全监测
- **包名**：`com.coolxer.plugin.sentinel`
- **版本**：1.0.0
- **作者**：CoolXer
- **描述**：接入 Sentinel 安全事件流（`sentinel.security.event.v1`）与 Warden 会话流（`chat.session.*.v1`），对提示注入、越狱、敏感数据、资源攻击与数据外渗等命中做原始留存、解析入库、检索、详情复盘、态势分析、会话消息线重建和 AI 研判。

## 数据模型

### 实体一览

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| `sentinel_security_event` | `sentinel_security_event` | 智能体安全事件，来自 `ai-agent-security-result`，兼容 `schema_version` 1.0 / 1.1 |
| `sentinel_chat_session_event` | `sentinel_chat_session_event` | Warden 会话事件，来自 `ai-agent-chat-message`，包含 started / append / snapshot / mutation |

两个表均按 `zenvis_insert_time` 月分区，行级 TTL 90 天。平台自动注入字段：`zenvis_id`（`Nullable(UUID)`）、`zenvis_insert_time`（`DateTime64(3)`）、`zenvis_update_time`。

### 安全事件属性

| 属性 | 类型 | 来源字段 | 默认展示 |
| --- | --- | --- | --- |
| `event_id` | String | 信封 `event_id` | ✔（详情链接） |
| `occurred_at` | DateTime64(3) | 信封 `occurred_at`（ISO8601 → Asia/Shanghai） | ✔ |
| `verdict_severity` | String | `payload.verdict.severity` | ✔ |
| `verdict_action` | String | `payload.verdict.action` | ✔ |
| `verdict_category` | String | `payload.verdict.category` | ✔ |
| `verdict_decision_point` | String | `payload.verdict.decision_point` | ✔ |
| `session_id` | String | `payload.session_id` | ✔（会话消息线链接） |
| `session_id_source` | String | `payload.session_id_source` | ✖ |
| `subject` | String | `payload.subject` | ✔ |
| `rule_ids` | Array(String) | `payload.verdict.hits[].rule_id` | ✔ |
| `verdict_reason` | String | `payload.verdict.reason` | ✖ |
| `message_id` | String | `payload.message_id`，兼容回退 `evidence.message_id` | ✔ |
| `msg_hash` | String | `payload.msg_hash`，兼容回退 `evidence.msg_hash` | ✔ |
| `frag_kind` | String | `payload.frag_kind`，兼容回退 `evidence.kind` | ✔ |
| `evidence_msg_index` | Nullable(Int32) | `evidence.msg_index` | ✖ |
| `evidence_count` | UInt16 | 1.1 为 1；1.0 为证据数组长度 | ✖ |
| `flagged_content` | String | 命中证据的 `content` | ✖ |
| `evidence` | json | `payload.evidence`，保留原始对象/数组并包裹存储 | ✖ |
| `verdict_hits` | json | `payload.verdict.hits` 包裹为对象 | ✖ |
| `request_method` | String | `payload.request.method` | ✔ |
| `request_path` | String | `payload.request.path` | ✔ |
| `request_wire` | String | `payload.request.wire` | ✔ |
| `request_remote_addr` | String | `payload.request.remote_addr` | ✔ |
| `request_scenario` | String | `payload.request.scenario` | ✖ |
| `source_component` | String | `source.component` | ✖ |
| `source_policy_version` | String | `source.policy_version` | ✔ |
| `schema_version` | String | 信封 `schema_version` | ✖ |
| `message_type` | String | 信封 `type` | ✖ |
| `mode` | String | `payload.mode` | ✖ |

安全事件 1.1 的 `payload.evidence` 为单对象，1.0 为数组；转换任务保留原始结构，1.1 强制要求 `message_id` / `msg_hash`。`message_id` / `msg_hash` 与会话事件消息三件套同值，用于跨流关联。

### 会话事件属性

| 属性 | 类型 | 来源字段 | 默认展示 |
| --- | --- | --- | --- |
| `event_key` | String | `started` → `st_<session_id>`；其余取事件 ID | ✔（详情链接） |
| `occurred_at` | DateTime64(3) | 信封 `occurred_at`（epoch 秒 → Asia/Shanghai） | ✔ |
| `event_kind` | String | `started` / `append` / `snapshot` / `mutation` | ✔ |
| `session_id` | String | `payload.session.session_id` | ✔（消息线链接） |
| `session_id_source` | String | `payload.session.session_id_source` | ✖ |
| `message_type` | String | 信封 `type` | ✖ |
| `request_method` | String | `payload.request.method` | ✔ |
| `request_path` | String | `payload.request.path` | ✔ |
| `request_wire` | String | `payload.request.wire` | ✔ |
| `request_remote_addr` | String | `payload.request.remote_addr` | ✔ |
| `message_count` | UInt32 | append / snapshot 为消息数组长度，mutation 为 1，started 为 0 | ✔ |
| `turn_no` | Nullable(UInt32) | `payload.turn_no` | ✖ |
| `msg_index` | Nullable(Int32) | mutation 的 `payload.msg_index` | ✖ |
| `old_message_id` | String | mutation `payload.old.message_id` | ✖ |
| `old_msg_hash` | String | mutation `payload.old.msg_hash` | ✖ |
| `new_message_id` | String | mutation `payload.new.message_id` | ✖ |
| `new_msg_hash` | String | mutation `payload.new.msg_hash` | ✖ |
| `message_ids` | Array(String) | 事件消息的 `message_id` 集合 | ✖ |
| `msg_hashes` | Array(String) | 事件消息的 `msg_hash` 集合 | ✖ |
| `message_roles` | Array(String) | 事件消息的 `role` 集合 | ✖ |
| `messages` | json | 包裹后的消息数组，保留 `message_id`、`msg_index`、`msg_hash`、`role`、`original` | ✖ |
| `mutation` | json | mutation 的 `{old, new}` 原始对象 | ✖ |
| `request_headers` | json | `payload.request.headers` 包裹为 `{headers: ...}` | ✖ |
| `source_component` | String | `source.component` | ✖ |
| `schema_version` | String | 信封 `schema_version`，当前固定 1.0 | ✖ |

## 数据推送服务

两条主题都由插件专属消费组直接消费，不做 Kafka→Kafka 转存：

1. **Sentinel安全事件解析入库**（`sentinel-security-event-clickhouse.yaml`）：消费 `ai-agent-security-result`，校验 `type`、`event_id`、`occurred_at`、`payload.verdict` 与 1.0/1.1 证据结构，抽取命中、证据、消息标识和请求现场，写入 `sentinel_security_event`；异常进入 `sentinel-security-event-dead-letter`。
2. **Sentinel会话事件解析入库**（`sentinel-chat-session-event-clickhouse.yaml`）：消费 `ai-agent-chat-message`，校验四类 `chat.session.*.v1` 事件、`schema_version` 1.0、epoch 秒时间、会话号、事件幂等键和消息稳定标识，写入 `sentinel_chat_session_event`；异常进入 `sentinel-chat-session-event-dead-letter`。

### 幂等键与重建

- 安全事件：`event_id`。
- 会话 append：`piece_id`。
- 会话 snapshot：`snapshot_id`。
- 会话 mutation：`mutation_id`。
- 会话 started：`st_<session_id>`（消费端可按 `(type, session_id)` 兜底）。

会话消息线按 `occurred_at` 排序，应用以下规则：`started` 计数；`append` 按 `msg_hash` 合并；`snapshot` 覆盖全量视图；`mutation` 删除 `old.msg_hash` 并加入 `new`。最终按 `msg_index` 排序，system / instructions 伪消息为 -1。

### 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SENTINEL_KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | 安全事件主题所在 Kafka 地址 |
| `SENTINEL_SOURCE_TOPIC` | `ai-agent-security-result` | 上游安全事件主题 |
| `SENTINEL_GROUP_ID` | `sentinel-security-event-clickhouse-v1` | 安全事件专属消费组 |
| `SENTINEL_DLQ_TOPIC` | `sentinel-security-event-dead-letter` | 安全事件死信主题 |
| `SENTINEL_CHAT_KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | 会话流主题所在 Kafka 地址 |
| `SENTINEL_CHAT_SOURCE_TOPIC` | `ai-agent-chat-message` | 上游会话流主题 |
| `SENTINEL_CHAT_GROUP_ID` | `sentinel-chat-session-event-clickhouse-v1` | 会话流专属消费组 |
| `SENTINEL_CHAT_DLQ_TOPIC` | `sentinel-chat-session-event-dead-letter` | 会话流死信主题 |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | 两个死信主题所在 Kafka 地址 |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | ClickHouse 数据库 |
| `CLICKHOUSE_USER` | `default` | ClickHouse 用户 |
| `CLICKHOUSE_PASSWORD` | 内置部署回退值 | 生产环境必须由部署环境覆盖 |

## 可视化入口

- **智能体安全态势总览**（低代码应用首页 `overview.json`）：安全事件/会话事件累计与今日量、近 7 天趋势、会话覆盖与消息规模、安全事件风险分布、会话事件类型分布。
- **智能体安全事件列表**（`event-list.json`）：多维检索、详情链接、`session_id` 消息线跳转、`message_id` 与请求现场。
- **智能体会话事件列表**（`session-event-list.json`）：四类事件检索、事件详情、`session_id` 消息线跳转。
- **智能体安全事件详情**（`com.coolxer.plugin.sentinel.detail-security-event`）：判定、请求与会话、命中正文、规则、证据及 1.1 字段。
- **智能体会话事件详情**（`com.coolxer.plugin.sentinel.detail-chat-session-event`）：消息数组、mutation 新旧标识、请求头快照与事件上下文。
- **智能体会话消息线**（`com.coolxer.plugin.sentinel.session-timeline`）：按 `session_id` 去重并重建消息线，展示事件时间线与消息原文。

## 菜单项

| 菜单 | 类型 | 参数 |
| --- | --- | --- |
| 智能体安全监测配置 | POLICY_CONFIG | `com.coolxer.plugin.sentinel.app` |
| 智能体安全监测 | LOW_CODE_APP | `com.coolxer.plugin.sentinel.app` |
| 智能体会话现场 | LOW_CODE_PAGE | `com.coolxer.plugin.sentinel.session-timeline` |

## 运行时 Skill

| Skill ID | 入口 | 工具范围 |
| --- | --- | --- |
| `sentinel-security-event-analysis` | 智能体安全研判 | 本地 `retrieval`：`retrieval_list_entity`、`retrieval_list_attribute`、`retrieval_search` |
| `sentinel-security-posture-analysis` | 智能体安全态势 | 本地 `retrieval`：`retrieval_list_entity`、`retrieval_list_attribute`、`entity_overview`、`entity_summary`、`entity_trend`、`entity_distribution` |
| `sentinel-session-analysis` | 智能体会话现场 | 本地 `retrieval`：`retrieval_list_entity`、`retrieval_list_attribute`、`retrieval_search` |

三个 Skill 均为只读，不注册外部 MCP 连接，不产生副作用。

## 使用说明

1. 确认上游已将安全事件导出到 `ai-agent-security-result`，并按需开启 Warden 会话流 `ai-agent-chat-message`。
2. 安装插件到 Zenvis 平台，按部署环境覆盖两类主题、消费组、DLQ 与 ClickHouse 配置。
3. 在推送任务页面启用「Sentinel安全事件解析入库」和「Sentinel会话事件解析入库」。
4. 从「智能体安全监测」应用查看安全事件、会话事件与会话消息线，验证 `message_id` / `msg_hash` 跨流关联。
5. 在角色权限中分配菜单与页面访问权限；会话消息含原文，应限制可见角色。
