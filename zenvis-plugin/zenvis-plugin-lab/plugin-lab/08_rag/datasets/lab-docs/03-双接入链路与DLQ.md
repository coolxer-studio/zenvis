# 双接入链路与 DLQ

## 演示链路

`lab-demo-generator.yaml` 使用两个 `demo_logs` Source：访问事件每 5 秒生成一条，风险线索每 9 秒生成一条。两个 `remap` 补齐 `type`、业务 ID、ISO8601 时间、演示 IP、资源和 JSON 详情，再分别写入 `LAB_ACCESS_TOPIC` 与 `LAB_RISK_TOPIC`。生成器只生产 Kafka 消息，不直接写入 ClickHouse。

## Kafka 链路

`lab-kafka-to-clickhouse.yaml` 使用 `lab.access.raw` 与 `lab.risk.raw` 两个 Source，消费组分别可配置。两个 `remap` 先验证 JSON、`type`、业务 ID、ISO8601 时间、枚举和风险分，再写入 ClickHouse。演示数据要经过这条消费链路才会出现在页面。

确定性失败启用 `drop_on_error: true` 与 `reroute_dropped: true`。两个转换的 `.dropped` 输出汇入 `lab_dead-letter`，Topic 可由 `LAB_DLQ_TOPIC` 覆盖，且不与 Source Topic 重叠。

## 环境变量

`KAFKA_BOOTSTRAP_SERVERS`、`LAB_ACCESS_TOPIC`、`LAB_RISK_TOPIC`、`LAB_ACCESS_GROUP_ID`、`LAB_RISK_GROUP_ID`、`LAB_DLQ_TOPIC` 控制 Kafka。`CLICKHOUSE_ENDPOINT`、`CLICKHOUSE_DATABASE`、`CLICKHOUSE_USER`、`CLICKHOUSE_PASSWORD` 控制 ClickHouse。

## DLQ 语义

dropped 记录保留原始 `message` 和失败 `reason`，不会静默补默认值。重放前必须先修复上游契约、按业务 ID 去重，再投递回正确 Source Topic。DLQ 的保留、容量和权限由部署方 Kafka 运维负责。

内置 Vector 测试覆盖合法访问事件、合法风险线索、缺少 `event_id`、非法 `occurred_at`、非法 `action` 和非法 `risk_score`。非法记录应只进入 `.dropped`，不得进入 ClickHouse Sink。

