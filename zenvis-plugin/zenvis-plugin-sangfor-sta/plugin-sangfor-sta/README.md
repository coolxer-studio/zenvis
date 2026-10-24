# 深信服 STA 潜伏威胁探针插件

版本：`1.0.0`

包名：`com.coolxer.plugin.sangfor.sta`

本插件从 OneSOC 的 STA 接入能力中独立提取，直接对接深信服 STA 潜伏威胁探针 V2.0 Kafka 数据。插件不依赖 OneSOC、动态 API JAR 或外部服务，并可与其同时安装。

插件图标使用深信服蓝绿交织球体标识，叠加表示威胁探针持续采集的扫描环与信号点，并以清晰的 `STA` 字标标明产品类型；矢量源文件为 `icon-source.svg`，安装图标为 `icon.png`。

## 数据模型

- 结构化实体：55
- 业务字段：1487
- ClickHouse 表：55
- 日志类型：55 类，覆盖流量日志、47 类协议审计、WAF/IPS/UTM/PVS 安全日志
- 每类日志一一对应一个实体、一张表、一个 Kafka 路由和一个 Vector 转换器

所有实体使用 `sangfor_sta_<logtype>`，数据表使用 `zenvis.t_sangfor_sta_<logtype>`。`zenvis_id` 与 `zenvis_insert_time` 由平台自动维护。完整映射见 `00_doc/契约矩阵.md`。

## 接入链路

```text
深信服 STA ────────────────┐
                           ├→ Kafka(STA_SOURCE_TOPIC) → 严格解析/路由 → 55 类字段转换
演示数据任务（可选） ───────┘                              ↘ 异常记录 → Kafka(STA_DLQ_TOPIC)
转换结果 → 55 张 ClickHouse 表 → 低代码检索/详情/IP 关联调查
```

插件包含两个相互独立的推送任务，可在推送任务管理中分别启停。

### 正式入库任务

`02_push-task/sangfor-sta-kafka-to-clickhouse.yaml`（任务名：`深信服 STA Kafka 校验转换入库`）负责正式链路：

- 原始消息必须是 JSON 对象，且 `logtype` 属于 55 类定义。
- 字段名先规范为小写 snake_case，再按目标实体校验必填字段、数量、类型、时间、布尔值和 JSON。
- 转换成功记录逐字段写入对应 ClickHouse 表。
- 解析或转换失败记录保留原始 `message`，附带 `metadata.dropped.component_id`、`metadata.dropped.message` 和 `metadata.dropped.reason`，统一写入死信主题。
- 未知 `logtype` 同样进入死信主题，不静默丢弃。

### 演示数据任务

`02_push-task/sangfor-sta-demo-to-kafka.yaml`（任务名：`深信服 STA 演示数据生成`）用于设备尚未接入时展示数据效果：

- 从 STA V2.0 官方 55 类样例中随机选择一条，刷新 `uid` 和当前时间后写入 `STA_SOURCE_TOPIC`。
- 演示消息与真实设备消息共用正式入库任务，因此自动经过相同的字段校验、DLQ、转换和 ClickHouse 写入，无需维护第二套表或页面。
- 默认每 60 秒生成一条，可通过 `SANGFOR_STA_DEMO_INTERVAL_SECONDS` 调整。
- 该任务与正式入库任务相互独立。正式设备接入后，可在推送任务管理中单独停止“深信服 STA 演示数据生成”；停止演示不会影响正式入库任务运行或已有数据。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | Kafka 地址 |
| `STA_SOURCE_TOPIC` | `sta-log` | STA 原始日志主题 |
| `STA_CLICKHOUSE_GROUP_ID` | `zenvis-sangfor-sta-clickhouse-v1` | 入库消费组 |
| `STA_DLQ_TOPIC` | `sangfor-sta-dead-letter` | 独立死信主题 |
| `SANGFOR_STA_DEMO_INTERVAL_SECONDS` | `60` | 演示数据生成间隔，单位秒 |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse HTTP 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | ClickHouse 数据库 |
| `CLICKHOUSE_USER` | `default` | ClickHouse 用户 |
| `CLICKHOUSE_PASSWORD` | 平台默认密码 | ClickHouse 密码 |

`STA_DLQ_TOPIC` 必须与所有源主题不同。死信主题的保留策略、告警和重放由部署方负责；修复原始记录后可重新投递到 `STA_SOURCE_TOPIC`。

## 页面与入口

- 低代码应用：接入总览、4 类流量日志、47 类协议审计、4 类安全日志。
- 接入总览提供近 7 天流量柱状趋势、应用协议环形占比、网络动作分段占比、Top 10 源 IP 排行榜和累计安全日志环形占比图表。
- 独立 IP 关联调查页：按源 IP 和目的 IP 跨 50 类主要实体精确统计；5 类文件审计子类型由对应协议日志覆盖。
- 菜单入口：配置、低代码应用、IP 关联调查。
- 每个实体均有独立列表页和基于 `zenvis_id` 的详情页；IP 字段可直接跳转调查页。

## 安装与升级

1. 执行 `./build.sh` 生成 `com-coolxer-plugin-sangfor-sta-1.0.0.tar.gz`。
2. 在 Zenvis 插件管理页安装归档。
3. 确认 Kafka 主题可读、ClickHouse 凭据正确，并观察“深信服 STA Kafka 校验转换入库”任务状态。
4. 未接入设备时，确认“深信服 STA 演示数据生成”任务运行，等待消息经正式链路进入 ClickHouse 后检查页面效果。
5. 正式设备接入并验证数据正常后，单独停止“深信服 STA 演示数据生成”任务。
6. 如需验证正式链路，向 `STA_SOURCE_TOPIC` 投递一条符合官方 V2.0 定义的 JSON 消息。

## 兼容性与限制

- 仅支持 STA 主动写入 Kafka 的模式，不包含 SFTP 文件落地、Syslog 接收或设备 API 拉取。
- 按参考实现要求 55 类日志字段完整存在；缺失可选字段也会进入 DLQ，部署前应核对设备固件版本。
- 源接口中的 Base64 内容保持原值，不进行解码。
- 演示任务默认随插件启动，且默认向正式源主题写入数据；正式上线后应手动停止，避免演示数据与真实设备数据混合。
- 演示任务停止后不会删除已写入的数据，也不会改变正式入库任务的消费组、DLQ 或表结构。
- 本插件静态复用 STA 实体语义，但不与 OneSOC 共用实体、表、ID 或 UI 配置。
- 静态构建通过不等同于目标环境联调通过；Kafka、ClickHouse 和低代码页面仍需在部署环境验证。

## 版本记录

### 1.0.0 - 2026-10-22

- 首次发布深信服 STA 潜伏威胁探针独立插件，包名为 `com.coolxer.plugin.sangfor.sta`。
- 支持 55 类 STA 日志的 Kafka 严格校验、类型转换、死信隔离和 55 张独立 ClickHouse 表入库。
- 提供 55 个低代码列表/详情页、接入总览，以及覆盖 50 类主要日志的跨实体 IP 关联调查。
- 接入总览提供流量趋势、应用协议、网络动作、Top 源 IP 和安全日志分布图表，使用 STA 风格原生 HTML/SVG 渲染。
- 提供可独立停止的演示数据推送服务，正式设备接入后可单独停用，不影响正式入库任务。
