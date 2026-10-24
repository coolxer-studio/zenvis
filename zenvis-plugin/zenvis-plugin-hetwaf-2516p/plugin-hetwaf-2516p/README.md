# 惠尔特 HET-WAF-2516P 防火墙插件

独立接入惠尔特 HET-WAF-2516P 防火墙 Syslog。插件将符合公共规则字段结构的任意 `type` 事件写入规则事件表，将 DDoS、未知格式及未来新增格式完整写入原始事件表，并提供检索、详情和安全态势看板。

## 插件信息

| 项目 | 值 |
| --- | --- |
| 插件名称 | 惠尔特 HET-WAF-2516P 防火墙 |
| 包名 | `com.coolxer.plugin.hetwaf2516p` |
| 插件目录 | `plugin-hetwaf-2516p` |
| 版本 | `1.0.0` |
| 数据实体 | 2（`het_waf_2516p_rule_event`、`het_waf_2516p_raw_event`） |
| ClickHouse 表 | 2，均按 `toYYYYMM(zenvis_insert_time)` 月分区 |
| 推送任务 | 3（Syslog → Kafka、Kafka → ClickHouse、演示数据 → Kafka） |
| 默认 Syslog 端口 | `12516/UDP` |
| 低代码应用 | `com.coolxer.plugin.hetwaf2516p.app` |
| HTML 看板 | `waf-security-overview.html` |

## 适用边界

- 默认仅接收来源 IP 为 `192.168.42.234` 的 HET-WAF-2516P Syslog，可通过 `HET_WAF_2516P_SOURCE_IP` 覆盖。
- 规则事件不限制 `type` 枚举，适配 Signature Rule、Upload Rule、HTTP Conformity Rule、ASCN Rule 等公共字段结构。
- DDoS 或其他不匹配公共字段结构的消息不做强行拆字段，完整保留在原始事件表。
- 插件不负责配置防火墙侧 Syslog 服务器地址，也不主动修改 ClickHouse、Kafka 或 Vectum 部署参数。
- 默认 Compose 仅发布 Vectum HTTP 端口 `11002`；接入前需为 `zenvis-service-vectum` 增加 `12516/UDP` 映射，Kubernetes 部署同样需要在 Service 上暴露 UDP 端口。
- Kafka 至少一次投递语义下，插件不做去重；重放历史消息可能产生重复记录。
- 演示任务安装后会随插件自动启动。正式接入前后均可手动停止；停止只影响后续合成数据，不删除已经入库的演示记录。

## 数据映射

| 上游数据 | Entity / ClickHouse 表 | 路由条件 | Kafka 主题 | 页面 |
| --- | --- | --- | --- | --- |
| HET-WAF 公共规则事件 | `het_waf_2516p_rule_event` | `HH:mm:ss WAF:` 前缀、公共键值结构、`type` 与 `severity` 字段齐全、以 `|` 结束 | `hetwaf2516p-syslog-raw` | 规则事件列表 / 规则事件详情 / 安全态势看板 |
| HET-WAF DDoS、未知或未来格式 | `het_waf_2516p_raw_event` | 不匹配规则事件结构 | `hetwaf2516p-syslog-raw` | 原始事件列表 / 原始事件详情 / 安全态势看板 |

规则事件保留 WAF 消息前缀中的客户端地址、端口、请求域名、`dip`、URL、HTTP 方法、检测类型、动作、级别和全部公共键值，同时保存外层 Syslog 字段与完整原始消息。原始事件保存事件时间、可识别的 `device_time` / `event_type`、全部外层 Syslog 字段和完整原始消息。

字段、类型和样例详见 [00_doc/接入说明.md](00_doc/接入说明.md)。

## 推送架构

```text
HET-WAF-2516P
    │ Syslog UDP 12516（可配置）
    ▼
syslog-to-kafka.yaml
    │ 校验来源 IP 与非空 message，保留完整 Syslog JSON
    ├── 正常 ──▶ hetwaf2516p-syslog-raw
    └── 异常 ──▶ hetwaf2516p-dead-letter

hetwaf2516p-syslog-raw
    │ waf-kafka-to-clickhouse.yaml
    ├── 公共规则结构 ──▶ het_waf_2516p_rule_event
    ├── DDoS / 其他 ───▶ het_waf_2516p_raw_event
    └── 转换失败 ──────▶ hetwaf2516p-dead-letter

demo-data-to-kafka.yaml（独立演示任务，可停止）
    │ 合成规则 / DDoS / 系统原文，source_type=demo
    └──────────────▶ hetwaf2516p-syslog-raw（复用上述转换链路）
```

### 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `HET_WAF_2516P_SYSLOG_ADDRESS` | `0.0.0.0:12516` | Syslog 监听地址 |
| `HET_WAF_2516P_SYSLOG_MODE` | `udp` | Syslog 传输模式 |
| `HET_WAF_2516P_SOURCE_IP` | `192.168.42.234` | 允许接入的 WAF 发送端 IP |
| `HET_WAF_2516P_SYSLOG_TOPIC` | `hetwaf2516p-syslog-raw` | 原始 Syslog Kafka 主题 |
| `HET_WAF_2516P_CLICKHOUSE_GROUP_ID` | `hetwaf2516p-waf-v1` | Kafka → ClickHouse 消费组 |
| `HET_WAF_2516P_DLQ_TOPIC` | `hetwaf2516p-dead-letter` | 死信主题，必须与原始主题不同 |
| `HET_WAF_2516P_DEMO_INTERVAL_SECONDS` | `2` | 演示事件生成间隔（秒） |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | Kafka 地址 |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse HTTP 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | ClickHouse 数据库 |
| `CLICKHOUSE_USER` | `default` | ClickHouse 用户 |
| `CLICKHOUSE_PASSWORD` | `SFGEfSVVMcUHCBCjKmzJ` | 平台默认密码，部署时优先使用环境变量覆盖 |

## 演示数据推送

插件包含一个独立推送任务“惠尔特 HET-WAF 演示数据推送（正式上线后停止）”，用于尚未接入真实防火墙时预览列表、详情和态势看板。

- 任务文件：`02_push-task/demo-data-to-kafka.yaml`。
- 默认每 2 秒生成一条合成事件，在 HTTP Conformity Rule、Signature Rule、ASCN Rule、Upload Rule、Sensitive Data Rule、Crawler Rule、DDoS 和系统事件之间随机轮换。
- 合成事件写入正式原始主题 `hetwaf2516p-syslog-raw`，由 `waf-kafka-to-clickhouse.yaml` 走同一套校验、分流和入库逻辑，不维护第二套字段映射。
- 合成数据统一带 `source_type=demo`、外层 `host/hostname=HET-WAF-DEMO`，规则事件另带 `devicename=HET-WAF-DEMO`，可与真实设备数据区分。
- 时间戳使用任务运行时的当前时间：外层为 UTC RFC3339，消息内的 `HH:mm:ss` 为 `Asia/Shanghai` 本地时间。
- 修改生成频率可在 Vectum 环境中设置 `HET_WAF_2516P_DEMO_INTERVAL_SECONDS`；修改后重新启动该任务。
- 插件安装时任务会随插件自动创建并启动。正式上线后，在“数据推送服务”中对该任务执行“停止”即可；正式 Syslog 接入和 Kafka 转换入库任务无需改动、也不受影响。
- 停止演示任务不会删除已经写入 ClickHouse 的演示数据。需要清理时，可按 `syslog_source_type = 'demo'` 或 `syslog_host = 'HET-WAF-DEMO'` 识别，并按平台数据保留策略处理。

## UI 与看板

- 低代码应用：`com.coolxer.plugin.hetwaf2516p.app`，包含规则事件列表和原始事件列表。
- 规则事件详情：`com.coolxer.plugin.hetwaf2516p.detail-rule-event`，使用平台内置 `zenvis_id` 查询。
- 原始事件详情：`com.coolxer.plugin.hetwaf2516p.detail-raw-event`，使用平台内置 `zenvis_id` 查询。
- 安全态势看板：`com.coolxer.plugin.hetwaf2516p.dashboard.waf-security-overview`，展示规则事件总量、今日事件、拒绝率、客户端 IP 与规则数、原始事件量、近 7 日趋势、处置动作、Top 客户端 IP、检测类型和最近规则事件。

## 死信与重放

来源 IP/空消息校验失败、非法 JSON、非法时间、非法数值、规则结构解析失败或未知路由记录均写入 `hetwaf2516p-dead-letter`，不会进入 ClickHouse。死信事件保留原始事件和 Vector `metadata.dropped` 信息。

重放时应从死信记录中取出原始 Syslog 事件并写回 `hetwaf2516p-syslog-raw`；不要把整个死信封装对象写回原始主题。修复数据、配置或转换规则后，再按 Kafka offset 定向重放。

## 验证状态

已完成 JSON/YAML 解析、Meta 字段与 VRL 输出对齐、Vector 测试和插件归档检查。未连接实际 HET-WAF-2516P、Kafka 或 ClickHouse 执行端到端联调，部署后仍需发送一条真实 Syslog 并核对两张表。

## 版本记录

### 1.0.0 - 2026-10-22

- 首次发布惠尔特 HET-WAF-2516P 防火墙独立插件。
- 提供 Syslog 原始接入、Kafka 严格校验、规则事件与原始事件双表入库、死信隔离和完整原文留存。
- 提供规则事件、原始事件列表与详情页，以及安全态势看板。
- 提供可独立停止的演示数据推送服务，正式设备接入后可在推送任务中手动停用。
