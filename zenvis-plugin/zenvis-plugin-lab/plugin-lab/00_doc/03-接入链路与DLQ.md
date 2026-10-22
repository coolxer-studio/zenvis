# 接入链路与 DLQ

`02_push-task` 同时提供“演示生产”和“消费入库”两条链路。演示生成器只负责把合成消息写入 Kafka，不直接访问 ClickHouse；消费任务负责校验、映射并写入两张 ClickHouse 表。

## 演示链路

文件：`02_push-task/lab-demo-generator.yaml`

- `lab_access_demo` 使用 `demo_logs`，每 5 秒生成一条访问事件；
- `lab_risk_demo` 使用 `demo_logs`，每 9 秒生成一条风险线索；
- 两个 `remap` 转换补齐 `type`、业务 ID、ISO8601 时间、IP、资源、标签和 JSON 详情；
- `lab_access_kafka` 与 `lab_risk_kafka` 两个 Kafka Sink 分别写入 `LAB_ACCESS_TOPIC` 与 `LAB_RISK_TOPIC`；
- 生成器不写 ClickHouse，入库完全交给 `lab-kafka-to-clickhouse.yaml`。

```text
demo_logs(access) -> lab_access_demo_map -> lab_access_kafka -> lab.access.raw
demo_logs(risk)   -> lab_risk_demo_map   -> lab_risk_kafka   -> lab.risk.raw
                                                                      |
                                              lab-kafka-to-clickhouse.yaml
                                                                      |
                                              lab_access_event / lab_risk_signal
```

两个推送任务都需要运行，并且 Kafka 与 ClickHouse 可用时，安装后约 15 秒内才能在页面看到数据。生成的 IP 会在两个实体间复用，便于验证 IP 调查页。Topic 必须预先存在，或在目标 Kafka 集群启用自动建 Topic。

## Kafka 生产链路

文件：`02_push-task/lab-kafka-to-clickhouse.yaml`

```text
lab.access.raw -> lab_access_parse -> lab_access_event
lab.risk.raw   -> lab_risk_parse   -> lab_risk_signal
       \______________ dropped ______________/
                          -> lab.dead-letter
```

两个 Kafka Source 使用独立的消费组。每个 `remap` 均启用：

- `drop_on_error: true`：确定性校验失败不进入正常输出；
- `reroute_dropped: true`：原始事件和失败信息进入 `<transform>.dropped`。

两个 `.dropped` 输出统一进入 `lab_dead_letter` Kafka Sink。DLQ Topic 不与两个 Source Topic 重叠，避免坏数据回灌。

## 校验规则

访问事件必须满足：

- 根节点是合法 JSON 对象；
- `type` 等于 `lab.access.event.v1`；
- `event_id` 非空；
- `occurred_at` 是合法 ISO8601 时间；
- `action`、`result`、`severity` 在枚举范围内；
- 源 IP 和目的 IP 非空；
- `tags` 与 `detail` 在缺失时安全落为数组和对象。

风险线索必须满足：

- 根节点是合法 JSON 对象；
- `type` 等于 `lab.risk.signal.v1`；
- `signal_id` 非空；
- `detected_at` 是合法 ISO8601 时间；
- `severity`、`status` 在枚举范围内；
- `risk_score` 可转换为数字且位于 `0–100`。

非法时间、非法枚举、缺失关键 ID、非法数字都会进入 DLQ，不会静默改写为默认值。

入库前，消费端会把 `occurred_at`、`detected_at` 转成 ClickHouse `JSONEachRow` 可接受的时间字符串 `YYYY-MM-DD HH:MM:SS.mmm`（`Asia/Shanghai`）。不要把带 `Z` 或 `+08:00` 的 RFC3339 时间直接交给 ClickHouse，否则会返回 `400 Bad Request` / `CANNOT_PARSE_INPUT_ASSERTION_FAILED`。

## 环境变量

| 变量 | 默认值 | 作用 |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | Kafka 地址 |
| `LAB_ACCESS_TOPIC` | `lab.access.raw` | 访问事件生产/消费 Topic |
| `LAB_RISK_TOPIC` | `lab.risk.raw` | 风险线索生产/消费 Topic |
| `LAB_ACCESS_GROUP_ID` | `lab-access-clickhouse-v1` | 访问事件消费组 |
| `LAB_RISK_GROUP_ID` | `lab-risk-clickhouse-v1` | 风险线索消费组 |
| `LAB_DLQ_TOPIC` | `lab.dead-letter` | 统一死信 Topic |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse HTTP 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | 数据库名 |
| `CLICKHOUSE_USER` | `default` | 数据库用户 |
| `CLICKHOUSE_PASSWORD` | 平台默认密码 | 数据库密码；生产环境应显式注入覆盖 |

## DLQ 内容与重放

Vector dropped 事件在 `metadata.dropped` 下保留原始 `message` 与失败 `reason`，实际附加字段可能随 Vector 版本增加。DLQ 消息使用 JSON 编码，便于检索、分类和重放。

建议处理流程：

1. 按 `reason` 聚合失败原因，先修复字段契约或上游格式；
2. 确认目标实体表中不存在相同业务 ID，避免重放产生重复记录；
3. 将修复后的消息重新投递到对应 Source Topic；
4. 验证正常输出条数增加且 DLQ 不再增长；
5. DLQ Topic 的保留期、容量和权限由部署方 Kafka 运维负责，插件不自动删除坏消息。

## 测试

`lab-demo-generator.yaml` 的内置 `tests` 校验访问事件与风险线索的枚举、标签、风险分和 JSON 详情映射。

`lab-kafka-to-clickhouse.yaml` 的内置 `tests` 覆盖合法访问事件、合法风险线索、缺少 ID、非法时间、非法动作和非法风险分。安装器或本地 Vector 应验证：

- 合法样例进入正常输出；
- 非法样例进入对应 `.dropped`；
- invalid 不得进入 ClickHouse Sink。

