# Server 探针统一消息契约

本文档描述 `com.coolxer.plugin.synap` 1.2.0 对 Synap Server/Spring Boot Agent 协议 v1 Checkpoint 的统一入库契约。源字段以当前 `synap-server` 的 Server Fact、Topic 定义和 `synap-agent/agent-springboot` 采集实现为准。

## 统一模型

12 个 Server Topic 与 Android、H5、iOS、Host、WeChat 共用：

- Zenvis 实体：`synap_agent_message`；
- ClickHouse 表：`zenvis.synap_agent_message`；
- Vector 推送任务：`synap-kafka-to-clickhouse.yaml`；
- 行粒度：每个 Kafka Checkpoint 一行。

| Kafka Topic | Fact 类型 |
| --- | --- |
| `server_fact_start` | `StartData` |
| `server_fact_application` | `ApplicationData` |
| `server_fact_runtime` | `RuntimeData` |
| `server_fact_api_asset` | `ApiAssetData` |
| `server_fact_api_observation` | `ApiObservationData` |
| `server_fact_security_config` | `SecurityConfigData` |
| `server_fact_dependency` | `DependencyData` |
| `server_fact_dangerous_call` | `DangerousCallData` |
| `server_fact_security_event` | `SecurityEventData` |
| `server_fact_agent_health` | `AgentHealthData` |
| `server_fact_user` | `UserData` |
| `server_fact_action` | `ActionData` |

Vector 只输出统一实体已有的 63 个属性。`fact.common` 被提升为公共列后从 `fact` JSON 中移除，其余字段保持上游名称和结构，不将 camelCase 业务字段转为物理列，也不写入 Zenvis 保留字段 `zenvis_id`、`zenvis_insert_time`。

## Checkpoint 和严格校验

示例：

```json
{
  "rule": "ServerRuntimeFactRule.groovy",
  "server_time": "2026-08-13T08:00:00.100Z",
  "agendas": [],
  "punishes": [],
  "risk": null,
  "fact": {
    "type": "RuntimeData",
    "message_id": "server-message-1",
    "observed_at": 1786608000000,
    "sequence": 2,
    "common": {
      "guid": "server-device-001",
      "start_id": 1700000000000,
      "client_time": "2026-08-13T08:00:00Z",
      "platform": "server"
    },
    "payload": {
      "schemaVersion": 1,
      "heapUsed": 1024
    }
  }
}
```

所有 Server 消息先执行统一信封校验，再执行 Server 专属校验：

- `fact.common.guid` 非空，`start_id` 为正整数，`client_time` 可解析，`platform` 必须为 `server`；
- `message_id` 非空，`observed_at` 为合法 Unix 毫秒时间，`sequence` 为正整数；
- Kafka Topic 与上表中的 Fact 类型严格一一对应；
- `StartData.config` 必须为对象；
- 其他 11 类 Fact 的 `payload` 必须为对象，`payload.schemaVersion` 固定为 1；
- 数字、布尔、时间和数组字段必须满足 Server v1 类型与范围；必填业务标识不得为空；
- `assets`、`observations`、`dependencies` 必须是非空对象数组；字符串数组和延迟桶逐项校验；
- `DependencyData.vulnerabilities` 若存在，必须为对象数组，且 purl、漏洞 ID、别名和 CVSS 结构合法。

任一失败都会进入 `${SYNAP_DLQ_TOPIC:-com.coolxer.plugin.synap.dead-letter}`，不会写入 ClickHouse。DLQ JSON 保留原始 `message`、Kafka 来源元数据及 `metadata.dropped.component_id/message/reason`。

## 业务载荷保留语义

- `ApplicationData`、`RuntimeData`、`SecurityConfigData`、`DangerousCallData`、`SecurityEventData`、`AgentHealthData`、`UserData` 和 `ActionData` 的 `payload` 原样保存在 `fact.payload`。
- `ApiAssetData.assets[]`、`ApiObservationData.observations[]` 和 `DependencyData.dependencies[]` 不拆行；包含多少数组项都只生成一条 Zenvis 记录。
- `DependencyData.vulnerabilities` 原样保存在 `fact.vulnerabilities`，不在 Vector 中按 purl 派生漏洞列。
- `StartData.config` 原样保存在 `fact.config`。
- 插件不解码 Base64、不推断未知字段、不访问外网漏洞源。

## Kafka、回溯和重放

统一任务使用：

```text
${SYNAP_KAFKA_GROUP_ID:-zenvis-plugin-synap-agent-message-v2}
```

默认 `SYNAP_KAFKA_TOPIC_PATTERN` 同时匹配原五端事实主题和上表 12 个精确 Server Topic。消费组保持不变，因此已有 Android/H5/iOS/Host/WeChat 位点不会重新回溯；首次纳入的 Server 分区受 `auto_offset_reset: earliest` 控制，从 Kafka 当前仍保留的最早消息开始。

部署环境若覆盖了 `SYNAP_KAFKA_TOPIC_PATTERN`，升级时必须同步加入 Server Topic。不再使用 `SYNAP_SERVER_KAFKA_GROUP_ID` 或 `SYNAP_SERVER_KAFKA_TOPIC_PATTERN`。

修复 DLQ 数据后，只将原始 `message` 重放到原始 `topic`，不要发送完整 DLQ 信封。统一任务为至少一次语义，可使用 `source_topic + source_partition + source_offset` 定位原始 Checkpoint；不存在 `item_index`。

## UI 边界

Server 记录沿用现有详情页和参数聚合页，并可通过 Zenvis 通用实体检索按 `platform=server`、Fact 类型、设备、应用、消息 ID 或来源坐标筛选。本版本不新增 Server 页面、菜单或仪表盘。
