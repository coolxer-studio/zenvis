# 探针数据采集

`com.coolxer.plugin.probe` 1.1.0 是面向 Zenvis 首次安装和升级场景的探针 Kafka 数据采集插件。它消费 Android、H5、iOS、Host 和 WeChat 探针事实主题，将统一消息信封转换为 `probe_agent_message` 实体，并支持 Host AuditData、WeChat 新事实、传输上下文与风险评估的结构化检索。

## 主要能力

- 按正则订阅探针 Kafka 事实主题，新增同前缀主题无需修改配置。
- 对 JSON 信封、必填字段、时间、整数、经纬度、识别标签和处置结构执行确定性校验。
- 自动创建 `zenvis.probe_agent_message` ClickHouse 表。
- 正常消息写入 ClickHouse；异常消息写入插件专属 Kafka DLQ，不会混入业务表。
- 保留 Kafka 主题、分区、偏移量、记录时间和完整原始消息。
- 结构化 `message_id`、`batch_id`、消息序列、请求来源、传输信任与网关上下文，并校验可选字段的类型和范围。
- 结构化 H5/WeChat `risk` 评估；Host AuditData 的 18 列事件和 WeChat 各 Fact 的 `data` 载荷完整保留在 `fact` JSON。
- 提供单条详情和无菜单的参数聚合分析页，可按 IP 地址或启动 ID 查看命中总量，并在“分布统计 / 趋势图 / 时间轴”主 Tab 中分析分类构成与时序；不安装消息管理应用、插件菜单或数据看板。

## 数据流

```text
Kafka 主题
  ├── 主题名不匹配 ──▶ 本任务不消费，消息仍由 Kafka 保留
  └── ^(android|h5|ios|host|wechat)_fact_.*$
        ▼
      Vector JSON 解析与业务校验
        ├── 正常 ──▶ zenvis.probe_agent_message
        └── 异常 ──▶ com.coolxer.plugin.probe.dead-letter
```

上游已经是 Kafka，因此插件只安装一项 Kafka → ClickHouse 推送任务，不创建重复的 source-to-Kafka 服务。

主题过滤发生在 Kafka Source 订阅阶段。不匹配 `PROBE_KAFKA_TOPIC_PATTERN` 的主题不会进入转换器，因此不会写入 ClickHouse 或 DLQ。匹配主题中的消息只有在解析或校验失败时才进入 DLQ。插件不限制 `fact.type` 枚举，只要求其非空且整条消息满足结构契约。

推送配置第一行的 YAML 文档头必须严格保持为 `---`。Vectum 依靠它稳定识别配置格式并生成 `push.yaml`；删除文档头可能被误判为 TOML，写成四个或更多短横线则不是合法 YAML。

## 插件资源

| 资源 | 标识 |
| --- | --- |
| 包名 | `com.coolxer.plugin.probe` |
| 版本 | `1.1.0` |
| 实体 | `probe_agent_message` |
| ClickHouse 表 | `zenvis.probe_agent_message` |
| 详情页面 | `com.coolxer.plugin.probe.detail-message` |
| 参数聚合页面 | `com.coolxer.plugin.probe.parameter-analytics`（支持 IP、启动 ID，不注册菜单） |
| 消息管理应用 | 不提供 |
| 插件菜单 | 不提供，`08_menu/config.json` 为 `[]` |
| 数据看板 | 不提供 |

平台自动注入 `zenvis_id` 和 `zenvis_insert_time`。插件不会解析、转换或写入这两个保留字段。

## 输入信封

最小合法消息：

```json
{
  "fact": {
    "type": "start",
    "common": {
      "guid": "device-001",
      "start_id": 1,
      "client_time": "2026-07-30 10:00:00"
    }
  }
}
```

必填字段为 `fact.common.guid`、`fact.common.start_id`、`fact.common.client_time` 和 `fact.type`。完整字段、类型、数组结构和时间格式见 [`00_doc/README.md`](00_doc/README.md)。

## 环境变量

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | Kafka 引导节点 |
| `PROBE_KAFKA_GROUP_ID` | `zenvis-plugin-probe-agent-message-v2` | 消费组 |
| `PROBE_KAFKA_TOPIC_PATTERN` | `^(android\|h5\|ios\|host\|wechat)_fact_.*$` | 探针主题正则 |
| `PROBE_DLQ_TOPIC` | `com.coolxer.plugin.probe.dead-letter` | 异常消息主题 |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse HTTP 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | 数据库 |
| `CLICKHOUSE_USER` | `default` | 用户名 |
| `CLICKHOUSE_PASSWORD` | `SFGEfSVVMcUHCBCjKmzJ` | ClickHouse 密码；部署注入值优先 |

Vectum 只允许使用服务环境中已注入且被 `VECTUM_VECTOR_ALLOWED_ENV` 放行的变量。需要覆盖默认值时，应同时更新 Vectum 容器环境和允许列表。ClickHouse Sink 使用 `${CLICKHOUSE_PASSWORD:-SFGEfSVVMcUHCBCjKmzJ}`，部署注入的 `CLICKHOUSE_PASSWORD` 优先。

## DLQ

任何确定性解析或校验失败都会从正常输出重路由到 `${PROBE_DLQ_TOPIC}`。DLQ 使用 JSON 编码，保留：

- 原始 `message`；
- Kafka 的 `topic`、`partition`、`offset` 和 `timestamp`；
- `metadata.dropped.component_id`；
- `metadata.dropped.message`；
- `metadata.dropped.reason`。

DLQ 由 Kafka 运维侧负责保留策略。修复数据后，应将原始 `message` 重放到原始 `topic`；不要把 DLQ JSON 信封直接发送到源主题，也不要把 DLQ 主题纳入 `PROBE_KAFKA_TOPIC_PATTERN`。

## 安装

1. 在 `zenvis-plugin` 仓库执行 `bash build.sh zenvis-plugin-synap/plugin-synap`。
2. 在 Zenvis 的“插件管理”上传 `com-coolxer-plugin-probe.tar.gz`。
3. 安装插件。平台会自动创建 Meta、ClickHouse 表、推送任务和独立低代码页面；插件不注册菜单或看板。
4. 在“数据推送服务”确认“探针 Kafka 消息入库”已启动。
5. 通过平台实体检索检查记录；详情链接进入记录详情页，IP 与启动 ID 字段链接进入参数值聚合页。

`1.1.0` 在 `1.0.0` 的单实体兼容模型上新增 Host AuditData、WeChat Fact、通用传输上下文和 H5/WeChat 风险评估字段。升级时后端的 additive schema 流程会为现有 ClickHouse 表补充新列；插件仍只安装参数聚合页和记录详情页，不安装消息管理应用、菜单或看板。

## 运行说明

- 语义为至少一次消费；Kafka 重放可能产生重复记录，可通过主题、分区和偏移量识别。
- `fact` 仅移除已结构化的 `common`，其他内容原样保存为 JSON；不推断字段、不解码 Base64。
- Host `AuditData` 的 `batch_id` 会提升为检索列，`events` 仍以固定 18 列数组保存在 `fact.events`；WeChat `self-app/runtime/integrity/session/error/behavior/action/network/location/debug` 载荷保存在 `fact` 中。
- H5/WeChat 的消息 ID、批次、序号、观察时间、请求链路和风险评估提升为结构化列；旧平台未提供的字符串保存为空字符串，可选数字、布尔和时间保存为 `null`。
- 详情页为 `fact`、`agendas`、`punishes` 和 `raw_message` 创建独立展示副本：解析 JSON 字符串、将识别/处置详情归一化为数组，并还原 `&quot;` 等嵌套 HTML 实体；ClickHouse 中的原始字段不会被改写。
- `agendas` 和 `punishes` 每项保存为紧凑 JSON 字符串，并分别派生 `agenda_tags` 和 `punish_types`。
- `lan_ip`、`wan_ip` 使用 `parameter-analytics?ip=` 打开聚合页并在两个 IP 字段间 OR 计数；`start_id` 使用 `parameter-analytics?start_id=` 打开同一页面并按启动 ID 计数。链接进入后会自动查询，默认统计近 7 天，支持切换近 24 小时和近 30 天。
- 参数聚合页调用值统计、趋势、检索和分布接口，主 Tab 依次为“分布统计”“趋势图”“时间轴”，默认首先展示分布统计。页面从 IP 或启动 ID 链接进入以及浏览器直接刷新时都会自动查询；趋势图直接使用趋势接口的数据作用域，时间轴和分布统计分别读取最近 100 条明细，避免嵌套服务覆盖趋势数据。时间轴只列出命中数大于零的分桶，并汇总数据类别、识别标签和处置类型；节点命中数来自全量趋势聚合。
- “分布统计”页签以 2×2 等高图表展示事实类型、探针 SDK、识别标签和处置类型。事实类型与探针 SDK 由分布接口按当前周期全量统计；识别标签与处置类型从当前周期最近 100 条明细计算 Top 10 样本分布。
- 识别标签按 `tag:level` 展示，处置类型直接展示 `0`～`255` 数值。同一条消息内重复的相同标签或处置类型只计一次，不同值分别计数；空数组显示空状态。周期命中超过 100 条时，页面会同时提示总命中数与实际样本数，避免将样本统计误解为全量结果。
- 空的可选数值保存为 `null`；非空但非法的值进入 DLQ，不会替换为 `0`。

## 目录

```text
plugin-synap/
├── index.json
├── README.md
├── icon.png
├── 00_doc/
├── 01_meta/probe-agent-message.json
├── 02_push-task/
│   ├── config.json
│   └── probe-kafka-to-clickhouse.yaml
├── 04_ui/
│   ├── detail-message/
│   └── parameter-analytics/
├── 06_mcp/config.json
├── 07_skill/
└── 08_menu/config.json
```
