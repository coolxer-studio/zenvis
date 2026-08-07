# 探针消息数据契约与运维说明

本文档是 `com.coolxer.plugin.probe` 1.1.0 的权威接入契约。插件面向首次安装和 additive schema 升级，不包含旧 `msg` 表、网关 Syslog 或业务日志文件兼容逻辑。

## 契约矩阵

| 数据定义 | 代码 | 实体 | ClickHouse 表 | Kafka 主题 | UI 页面 | 结构化 |
| --- | --- | --- | --- | --- | --- | --- |
| 探针标准事实消息 | `agent-message` | `probe_agent_message` | `zenvis.probe_agent_message` | `^(android\|h5\|ios\|host\|wechat)_fact_.*$` | `detail-message`、`parameter-analytics` | 是 |

所有匹配主题使用相同的 `{fact:{common,type}, agendas, punishes, rule, risk}` Checkpoint 信封，因此对应一个实体和一张表。Kafka 主题、分区和偏移量作为来源坐标随记录保存。Host AuditData 与 WeChat 新 Fact 是 `fact` 的载荷变体，不是新的 Kafka 信封定义：通用字段提升为列，业务载荷继续完整保存在 `fact` JSON。

## 输入结构

完整示例：

```json
{
  "rule": "rule-001",
  "server_time": "2026-07-30T02:00:01.123Z",
  "agendas": [
    {
      "tag": "privacy",
      "level": "high"
    }
  ],
  "punishes": [
    {
      "action": {
        "type": 2,
        "name": "block"
      }
    }
  ],
  "risk": {
    "detector_version": "wechat-risk-1",
    "rule_version": "2026-08-05",
    "score": 25.5,
    "confidence": 0.5,
    "reason_codes": ["WECHAT_DEVTOOLS_DETECTED"],
    "evidence_count": 2,
    "observation": true,
    "trust_level": "app-key-codec/untrusted"
  },
  "fact": {
    "type": "runtime",
    "message_id": "message-001",
    "batch_id": "batch-001",
    "observed_at": 1785376800123,
    "sequence": 7,
    "request_ip": "203.0.113.10",
    "transport_trust": "app-key-codec/untrusted",
    "sequence_observation": "CONTIGUOUS",
    "server_received_at": 1785376800623,
    "client_server_skew_ms": 500,
    "common": {
      "guid": "device-001",
      "start_id": 1001,
      "client_time": 1785376800123,
      "user_id": "user-001",
      "sdk_version": "2.4.0",
      "app_id": 42,
      "app_name": "示例应用",
      "app_package": "wx123",
      "app_version": "3.2.1",
      "platform": "wechat",
      "manufacturer": "Example",
      "model": "X1",
      "system": "WeChat",
      "system_version": "8.0",
      "net_type": "wifi",
      "lan_ip": "192.168.1.10",
      "wan_ip": "2001:db8::10",
      "latitude": 39.9042,
      "longitude": 116.4074,
      "country": "中国",
      "province": "北京",
      "city": "北京市",
      "county": "朝阳区",
      "thoroughfare": "示例路"
    },
    "data": {
      "devtools": true,
      "enableDebug": false,
      "state": "observed"
    }
  }
}
```

## 字段映射

| 属性 ID | 字段 | ClickHouse 类型 | 来源或生成规则 | 必填/空值 |
| ---: | --- | --- | --- | --- |
| 550001 | `source_topic` | `String` | Kafka `topic` | Source 必须提供 |
| 550002 | `source_partition` | `UInt32` | Kafka `partition` | Source 必须提供 |
| 550003 | `source_offset` | `UInt64` | Kafka `offset` | Source 必须提供 |
| 550004 | `kafka_timestamp` | `DateTime64(3)` | Kafka `timestamp` | 缺失时使用 Vector 当前时间 |
| 550005 | `guid` | `String` | `fact.common.guid` | 必填、非空 |
| 550006 | `start_id` | `UInt64` | `fact.common.start_id` | 必填、正整数；链接到参数值聚合页 |
| 550007 | `user_id` | `String` | `fact.common.user_id` | 缺失为空字符串 |
| 550008 | `platform` | `String` | `fact.common.platform` | 缺失为空字符串 |
| 550009 | `sdk_version` | `String` | `fact.common.sdk_version` | 缺失为空字符串 |
| 550010 | `app_id` | `Nullable(UInt32)` | `fact.common.app_id` | 缺失为 null；非法进入 DLQ |
| 550011 | `app_name` | `String` | `fact.common.app_name` | 缺失为空字符串 |
| 550012 | `app_package` | `String` | `fact.common.app_package` | 缺失为空字符串 |
| 550013 | `app_version` | `String` | `fact.common.app_version` | 缺失为空字符串 |
| 550014 | `manufacturer` | `String` | `fact.common.manufacturer` | 缺失为空字符串 |
| 550015 | `model` | `String` | `fact.common.model` | 缺失为空字符串 |
| 550016 | `system_name` | `String` | `fact.common.system` | 缺失为空字符串 |
| 550017 | `system_version` | `String` | `fact.common.system_version` | 缺失为空字符串 |
| 550018 | `net_type` | `String` | `fact.common.net_type` | 缺失为空字符串 |
| 550019 | `lan_ip` | `String` | `fact.common.lan_ip` | IPv4/IPv6 文本；缺失为空 |
| 550020 | `wan_ip` | `String` | `fact.common.wan_ip` | IPv4/IPv6 文本；缺失为空 |
| 550021 | `latitude` | `Nullable(Float64)` | `fact.common.latitude` | 缺失为 null；范围 -90～90 |
| 550022 | `longitude` | `Nullable(Float64)` | `fact.common.longitude` | 缺失为 null；范围 -180～180 |
| 550023 | `country` | `String` | `fact.common.country` | 缺失为空字符串 |
| 550024 | `province` | `String` | `fact.common.province` | 缺失为空字符串 |
| 550025 | `city` | `String` | `fact.common.city` | 缺失为空字符串 |
| 550026 | `county` | `String` | `fact.common.county` | 缺失为空字符串 |
| 550027 | `thoroughfare` | `String` | `fact.common.thoroughfare` | 缺失为空字符串 |
| 550028 | `client_time` | `DateTime64(3)` | `fact.common.client_time` | 必填、合法时间 |
| 550029 | `server_time` | `DateTime64(3)` | 顶层 `server_time` | 缺失使用 Kafka 时间 |
| 550030 | `rule` | `String` | 顶层 `rule` | 缺失为空字符串 |
| 550031 | `fact_type` | `String` | `fact.type` | 必填、非空 |
| 550032 | `fact` | `json` | 删除 `fact.common` 后的 `fact` 对象 | 必填对象 |
| 550033 | `agenda_tags` | `Array(String)` | `agendas[].tag + ":" + agendas[].level` | 缺失为 `[]` |
| 550034 | `agendas` | `Array(String)` | 每个 agenda 对象编码为紧凑 JSON | 缺失为 `[]` |
| 550035 | `punish_types` | `Array(UInt8)` | `punishes[].action.type` | 缺失为 `[]` |
| 550036 | `punishes` | `Array(String)` | 每个 punish 对象编码为紧凑 JSON | 缺失为 `[]` |
| 550037 | `raw_message` | `String` | 未改写的 Kafka `message` | 必填、非空 |
| 550038 | `message_id` | `String` | `fact.message_id` | H5/WeChat 必填；其他平台缺失为空 |
| 550039 | `batch_id` | `String` | `fact.batch_id` | H5/WeChat 传输批次或 Host 审计 UUID；缺失为空 |
| 550040 | `observed_at` | `Nullable(DateTime64(3))` | `fact.observed_at` Unix 毫秒 | 缺失为 null；非法进入 DLQ |
| 550041 | `sequence` | `Nullable(UInt64)` | `fact.sequence` | 缺失为 null；必须非负 |
| 550042 | `request_ip` | `String` | `fact.request_ip` | 服务端解析的来源 IP；缺失为空 |
| 550043 | `request_user_agent` | `String` | `fact.request_user_agent` | 缺失为空 |
| 550044 | `request_origin` | `String` | `fact.request_origin` | 缺失为空 |
| 550045 | `fetch_site` | `String` | `fact.fetch_site` | 缺失为空 |
| 550046 | `fetch_mode` | `String` | `fact.fetch_mode` | 缺失为空 |
| 550047 | `fetch_dest` | `String` | `fact.fetch_dest` | 缺失为空 |
| 550048 | `transport_trust` | `String` | `fact.transport_trust` | 缺失为空 |
| 550049 | `sequence_observation` | `String` | `fact.sequence_observation` | 缺失为空 |
| 550050 | `server_received_at` | `Nullable(DateTime64(3))` | `fact.server_received_at` Unix 毫秒 | 缺失为 null；非法进入 DLQ |
| 550051 | `client_server_skew_ms` | `Nullable(Int64)` | `fact.client_server_skew_ms` | 有符号毫秒；缺失为 null |
| 550052 | `request_asn` | `String` | `fact.request_asn` | 缺失为空 |
| 550053 | `request_network_category` | `String` | `fact.request_network_category` | 缺失为空 |
| 550054 | `request_tls_fingerprint` | `String` | `fact.request_tls_fingerprint` | 缺失为空 |
| 550055 | `request_ip_reputation` | `String` | `fact.request_ip_reputation` | 缺失为空 |
| 550056 | `risk_detector_version` | `String` | `risk.detector_version` | 无风险评估时为空 |
| 550057 | `risk_rule_version` | `String` | `risk.rule_version` | 无风险评估时为空 |
| 550058 | `risk_score` | `Nullable(Float64)` | `risk.score` | 0～100；无评估时为 null |
| 550059 | `risk_confidence` | `Nullable(Float64)` | `risk.confidence` | 0～1；无评估时为 null |
| 550060 | `risk_reason_codes` | `Array(String)` | `risk.reason_codes` | 无评估时为 `[]` |
| 550061 | `risk_evidence_count` | `Nullable(UInt32)` | `risk.evidence_count` | 非负；无评估时为 null |
| 550062 | `risk_observation` | `Nullable(Bool)` | `risk.observation` | 无评估时为 null |
| 550063 | `risk_trust_level` | `String` | `risk.trust_level` | 无风险评估时为空 |

Zenvis 另外注入 `zenvis_id Nullable(UUID)` 和 `zenvis_insert_time DateTime64(3)`。它们不属于上述 63 个业务字段，也不会出现在 Vector 输出中。

## 时间规则

`client_time` 为必填，`server_time` 为可选。两者支持：

1. Unix 秒整数；
2. Unix 毫秒整数；
3. RFC3339，例如 `2026-07-30T02:00:01.123Z`；
4. `yyyy-MM-dd HH:mm:ss`；
5. `yyyy-MM-dd HH:mm:ss.SSS`。

无时区的文本按 `Asia/Shanghai` 解释，统一输出为 `yyyy-MM-dd HH:mm:ss.SSS`。非空但无法解析的时间进入 DLQ。

`observed_at` 和 `server_received_at` 是 synap-server 生成或透传的 Unix 毫秒整数。存在时严格按毫秒解析并输出 `DateTime64(3)`；不把秒值猜测为毫秒值。

## 数字和数组规则

- `start_id` 必须是大于 0 的 UInt64 兼容整数。
- `app_id` 为空时为 null；非空时必须在 UInt32 范围内。
- 经纬度为空时为 null；非空时必须是数字并满足地理范围。
- `sequence` 必须是非负整数，`client_server_skew_ms` 允许正负整数。
- `risk.score` 范围为 0～100，`risk.confidence` 范围为 0～1，`risk.evidence_count` 必须在 UInt32 范围，`risk.observation` 必须为布尔值。
- `risk.reason_codes` 必须为非空字符串组成的数组。顶层 `risk` 为 null 时，风险字符串为空、数组为 `[]`、数字/布尔值为 null。
- `agendas` 必须为数组；每项必须为对象，且 `tag`、`level` 非空。
- `punishes` 必须为数组；每项必须包含对象类型的 `action`，且 `action.type` 在 0～255。
- 插件不会把非法数字自动转换为 `0`，也不会把非法数组静默丢弃。

## 新增 Fact 载荷

- Host `AuditData` 发布到 `host_fact_audit`，`fact.batch_id` 为 UUID，`fact.events` 为 1～100 行固定 18 列数组：`eventId,eventTime,category,action,result,actor,subject,target,pid,process,localEndpoint,remoteEndpoint,protocol,digestAlgorithm,digest,source,bootId,detail`。插件将批次 ID 提升为列，并原样保留事件数组。
- WeChat 新增 `self-app`、`runtime`、`integrity`、`session`、`error`、`behavior`、`action`、`network`、`location`、`debug` 主题；相应业务载荷在 `fact.data` 或 Fact 自有字段中保存。插件不将不同 Fact 的业务字段合并为不可靠的通用列。
- H5/WeChat Checkpoint 可携带顶层 `risk`，插件将其 8 个字段结构化。Host 和其他平台的 `risk` 通常为 null。

## Kafka 与 ClickHouse

默认主题正则：

```text
^(android|h5|ios|host|wechat)_fact_.*$
```

Vector Kafka Source 支持以 `^` 开头的主题正则。需要限制到固定主题时，可覆盖 `PROBE_KAFKA_TOPIC_PATTERN`。

主题过滤与消息校验的边界如下：

- 主题名不匹配正则：Kafka Source 不订阅，消息不会进入本任务、ClickHouse 或 DLQ，仍按原主题的 Kafka 保留策略保存。
- 主题名匹配且消息合法：转换为 63 个业务字段并写入 `zenvis.probe_agent_message`。
- 主题名匹配但消息解析或校验失败：通过 `map_probe_agent_message.dropped` 写入插件 DLQ。
- `fact.type` 没有固定枚举白名单；任意非空值都可以入库，但消息仍须满足其余契约。

若以后扩大正则，同一消费组对新纳入且没有已提交位点的分区会受 `auto_offset_reset: earliest` 控制，从 Kafka 当前仍保留的最早位置开始消费。

`probe-kafka-to-clickhouse.yaml` 必须以且仅以三个短横线 `---` 开头。该合法 YAML 文档头用于确保 Vectum 将任务写成 `push.yaml`；缺失时复杂 VRL 内容可能干扰格式探测，四个短横线 `----` 则会导致 YAML 解析失败。

ClickHouse 表由 Meta 自动创建：

```text
ENGINE = MergeTree()
ORDER BY (source_topic, source_partition, source_offset, client_time)
PARTITION BY toYYYYMM(zenvis_insert_time)
```

表按平台入库时间分区，避免客户端可控时间导致异常分区。业务默认排序仍为 `client_time`。

## DLQ 与重放

默认 DLQ：

```text
com.coolxer.plugin.probe.dead-letter
```

会进入 DLQ 的情况包括：

- 空消息、非法 JSON 或非对象根节点；
- `fact`/`fact.common` 不是对象；
- 必填字段缺失；
- 时间、整数或经纬度非法；
- 可选消息序列、服务端接收时间、时钟偏差或风险评估字段类型/范围非法；
- Kafka 来源坐标非法；
- `agendas` 或 `punishes` 结构不符合契约。

DLQ Sink 开启：

- `acks=-1`；
- 最多 10 次 Kafka 发送重试；
- zstd 压缩；
- 256 MiB 磁盘缓冲；
- 缓冲满时阻塞，避免静默丢失。

重放步骤：

1. 根据 `metadata.dropped.message` 修复产生异常的字段。
2. 从 DLQ 事件提取原始 `message`、`topic`、`partition` 和 `offset`。
3. 把修复后的业务 JSON 发送回原始 `topic`。
4. 确认消息进入 `zenvis.probe_agent_message`。
5. 使用来源主题、分区和偏移量记录重放审计。

不要把整个 DLQ JSON 信封重放到源主题。重放是至少一次语义，重复记录需通过来源坐标识别。

## UI

- `com.coolxer.plugin.probe.detail-message`：按 `record_id` 调用 `/api/v1/entity/probe_agent_message/{record_id}/view`。
- `com.coolxer.plugin.probe.parameter-analytics`：调用值统计、趋势、检索和分布接口，支持 IP 与启动 ID 两种聚合模式；页面不注册菜单。

插件不提供探针消息管理低代码应用，不注册任何插件菜单或数据看板。记录列表、筛选和复制使用平台通用实体检索能力。

参数聚合分析页兼容两类入口和手工查询：

- `parameter-analytics?ip={lan_ip|wan_ip}`：选择 IP 模式，`match_fields` 为 `lan_ip`、`wan_ip`，使用 OR 语义保证每条记录最多计数一次并自动查询；
- `parameter-analytics?start_id={start_id}`：选择启动 ID 模式，`match_fields` 为 `start_id` 并自动查询；浏览器直接刷新时，趋势图仍直接读取趋势接口返回的数据集和序列；
- 无参数打开：默认选择 IP 模式，用户可切换参数类型并输入查询值；
- 后端用 `toString(column) = focus_value` 比较，因此 UInt64 类型的启动 ID 可通过字符串参数精确匹配。

所有聚合视图共享同一个参数值和统计周期，默认周期为近 7 天，并可切换近 24 小时或近 30 天。页面主 Tab 依次提供“分布统计”“趋势图”“时间轴”三个页签，并默认首先展示分布统计：

- 值统计接口返回周期内的命中总量；
- 趋势接口按小时或按天分桶，页面支持在可缩放趋势图和原生时间轴之间切换；原生时间轴按时间倒序展示命中数大于零的分桶；
- 时间轴和分布统计各自按同一参数和周期读取最近 100 条明细，避免明细服务的数据作用域覆盖趋势图；时间轴节点去重汇总 `fact_type`、`agenda_tags` 和 `punish_types`，节点命中数继续使用全量趋势结果；
- “分布统计”以 2×2 等高图表展示事实类型、探针 SDK、识别标签和处置类型，其中事实类型与探针 SDK 由分布接口按当前周期全量聚合；
- 识别标签与处置类型共享上述最近 100 条明细，在页面中展开、计数、降序排序并各保留 Top 10。识别标签按 `tag:level` 展示，处置类型直接展示 `0`～`255` 数值；
- 同一条消息中重复的相同识别标签或处置类型只计一次，不同值分别参与统计。空数组或无样本时显示空状态；总命中超过 100 条时显示总命中数和实际样本数，明确说明这两项分布不是全量统计。

详情页新增“传输与请求上下文”和条件显示的“风险评估”属性区，并保留“事实详情”“识别详情”“处置详情”和“原始消息”四个 JSON 页签。页面只对接口响应创建展示副本：

- `fact` 与 `raw_message` 的 JSON 文本解析为对象；
- `agendas` 与 `punishes` 兼容单条或多条拼接 JSON，并统一展示为数组；
- 递归还原 `&quot;`、`&amp;`、`&lt;`、`&gt;` 等 HTML 实体；
- 使用 amis JSON 组件的 `source` 绑定保留嵌套对象，避免显示为 `[object Object]`；
- 不修改 ClickHouse 中的 `fact`、`agendas`、`punishes` 或 `raw_message` 原值。

`1.1.0` 新增 26 个传输/风险结构化字段以及 Host AuditData、WeChat 新 Fact 的契约和测试；后端 additive schema 升级会为已有表补列。`08_menu/config.json` 固定为空数组，安装时仅注册 `parameter-analytics` 参数聚合页和详情页，不注册消息管理应用、插件菜单或探针看板。

## 环境覆盖

配置中的环境变量均带有插件默认值；生产部署仍应显式覆盖连接凭据。Vectum 覆盖变量需要同时：

1. 把覆盖变量注入 `vectum-service`；
2. 将变量名加入 `VECTUM_VECTOR_ALLOWED_ENV`；
3. 重启 Vectum；
4. 在 Zenvis 中重新启动推送任务。

当前 ClickHouse Sink 配置为 `password: "${CLICKHOUSE_PASSWORD:-SFGEfSVVMcUHCBCjKmzJ}"`。部署注入的 `CLICKHOUSE_PASSWORD` 优先于平台默认值，并应加入 `VECTUM_VECTOR_ALLOWED_ENV`。

## 运行限制

- 插件只处理标准探针 JSON 信封，不处理 Syslog、文件日志或任意 JSON。
- 不进行 Base64 解码；不把未声明字段拆成 ClickHouse 列。
- `raw_message` 和 `fact` 可能较大，默认列表不展示完整内容。
- 正常记录使用磁盘缓冲和重试；持续的 ClickHouse 或 Kafka 故障会产生背压。
- 生产环境应为源主题和 DLQ 配置合适的保留期、容量告警和访问控制。
