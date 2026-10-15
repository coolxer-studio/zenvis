# 智能体安全监测知识说明

本目录是「智能体安全监测」插件的专项文档入口。

- [`数据字典与接入说明.md`](数据字典与接入说明.md)：Sentinel 安全事件信封结构、字段映射、枚举定义、Kafka 投递语义、DLQ 与重放说明。

## 基本信息

- **插件名称**：智能体安全监测
- **包名**：`com.coolxer.plugin.sentinel`
- **版本**：1.0.0
- **作者**：CoolXer
- **描述**：接入 Sentinel 智能体安全检测网关导出的安全事件（`sentinel.security.event.v1`），对提示注入、越狱、敏感数据、资源攻击与数据外渗等命中做原始留存、解析入库、检索、详情复盘、安全态势分析和 AI 研判。

## 数据模型

### 实体一览

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| `sentinel_security_event` | `sentinel_security_event` | 智能体安全事件，来自 Kafka 主题 `ai-agent-security-result` 的 JSON 信封 |

### 属性

| 属性 | 类型 | 来源字段 | 默认展示 |
| --- | --- | --- | --- |
| `event_id` | String | 信封 `event_id` | ✔（详情链接） |
| `occurred_at` | DateTime64(3) | 信封 `occurred_at` | ✔ |
| `verdict_severity` | String | `payload.verdict.severity` | ✔ |
| `verdict_action` | String | `payload.verdict.action` | ✔ |
| `verdict_category` | String | `payload.verdict.category` | ✔ |
| `verdict_decision_point` | String | `payload.verdict.decision_point` | ✔ |
| `session_id` | String | `payload.session_id` | ✔ |
| `subject` | String | `payload.subject` | ✔ |
| `rule_ids` | Array(String) | 由 `payload.verdict.hits[].rule_id` 抽取 | ✔ |
| `verdict_reason` | String | `payload.verdict.reason` | ✖ |
| `msg_hash` | String | `payload.msg_hash` | ✔ |
| `frag_kind` | String | `payload.frag_kind` | ✔ |
| `evidence_count` | UInt16 | `payload.evidence` 数组长度 | ✖ |
| `flagged_content` | String | `payload.evidence` 中 `flagged=true` 的 `content` | ✖ |
| `evidence` | json | `payload.evidence`（包裹为对象） | ✖ |
| `verdict_hits` | json | `payload.verdict.hits`（包裹为对象） | ✖ |
| `request_path` | String | `payload.request.path` | ✔ |
| `request_wire` | String | `payload.request.wire` | ✔ |
| `request_scenario` | String | `payload.request.scenario` | ✖ |
| `source_component` | String | `source.component` | ✖ |
| `source_policy_version` | String | `source.policy_version` | ✔ |
| `schema_version` | String | 信封 `schema_version` | ✖ |
| `message_type` | String | 信封 `type` | ✖ |
| `mode` | String | `payload.mode` | ✖ |

平台自动注入、无需配置的字段：`zenvis_id`（`Nullable(UUID)`，平台记录 ID）、`zenvis_insert_time`（`DateTime64(3)`，入库时间）。行级 TTL 为 90 天，依据 `zenvis_insert_time`。

## 数据推送服务

数据来源本身就是上游 Kafka 主题，因此不做 Kafka→Kafka 转存，只注册一个推送服务：

1. **Sentinel安全事件解析入库**（`sentinel-security-event-clickhouse.yaml`）：用本插件专属消费组直接消费上游主题（默认 `ai-agent-security-result`）的 JSON 信封，校验协议版本与必填字段，抽取命中与证据字段，转换时间后写入 ClickHouse 表 `sentinel_security_event`；契约异常进入死信主题（默认 `sentinel-security-event-dead-letter`）。

### 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SENTINEL_KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | 上游事件主题所在 Kafka 地址 |
| `SENTINEL_SOURCE_TOPIC` | `ai-agent-security-result` | 上游 Sentinel 事件主题（消费来源） |
| `SENTINEL_GROUP_ID` | `sentinel-security-event-clickhouse-v1` | 本插件消费组，独立于上游导出器 |
| `SENTINEL_DLQ_TOPIC` | `sentinel-security-event-dead-letter` | 死信主题 |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | 死信主题所在 Kafka 地址 |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | ClickHouse 数据库 |
| `CLICKHOUSE_USER` | `default` | ClickHouse 用户 |
| `CLICKHOUSE_PASSWORD` | 无默认值 | 由部署环境注入 |

## 可视化入口

- **智能体安全态势总览**（低代码应用首页）：累计与今日事件量、近 7 天趋势、风险等级/命中类别/建议动作/判定点分布。
- **智能体安全事件列表**：多维检索与默认列展示，点击「事件唯一ID」进入详情。
- **智能体安全事件详情**（独立页面 `com.coolxer.plugin.sentinel.detail-security-event`）：按平台记录 ID 展示判定信息、请求与会话信息、命中片段正文、命中规则明细与证据片段。

## 菜单项

| 菜单 | 类型 | 参数 |
| --- | --- | --- |
| 智能体安全监测配置 | POLICY_CONFIG | `com.coolxer.plugin.sentinel.app` |
| 智能体安全监测 | LOW_CODE_APP | `com.coolxer.plugin.sentinel.app` |

## 运行时 Skill

| Skill ID | 入口 | 工具范围 |
| --- | --- | --- |
| `sentinel-security-event-analysis` | 智能体安全研判 | 本地 `retrieval`：`retrieval_list_entity`、`retrieval_list_attribute`、`retrieval_search` |
| `sentinel-security-posture-analysis` | 智能体安全态势 | 本地 `retrieval`：`retrieval_list_entity`、`retrieval_list_attribute`、`entity_overview`、`entity_summary`、`entity_trend`、`entity_distribution` |

两个 Skill 均为只读，不注册外部 MCP 连接，不产生副作用。

## 使用说明

1. 确认上游 Sentinel 已把安全事件导出到 Kafka 主题 `ai-agent-security-result`。
2. 安装插件到 Zenvis 平台，按需覆盖上游主题、消费组与 DLQ 主题环境变量。
3. 在推送任务页面启用「Sentinel安全事件解析入库」。
4. 从菜单进入「智能体安全监测」，在事件列表与详情页验证数据。
5. 在角色权限中为需要的角色分配菜单。
