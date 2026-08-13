# 探针数据采集

`com.coolxer.plugin.synap` 1.2.0 是 Zenvis 的统一探针 Kafka 数据采集插件。Android、H5、iOS、Host、WeChat 和 Server 的每个 Kafka Checkpoint 都转换为 63 个公共字段，写入 `synap_agent_message` / `zenvis.synap_agent_message`。

## 主要能力

- 使用一个 Kafka 消费组和一个推送任务消费六类平台事实主题。
- 对 JSON 信封、公共必填字段、时间、整数、经纬度、识别标签、处置和风险结构执行确定性校验。
- 对 12 个 `server_fact_*` 主题额外校验平台、Topic/Fact 对应关系、追踪字段、`schemaVersion=1` 及业务字段类型。
- 所有平台每个 Checkpoint 写一行；Server 的批量数组和漏洞富化结果完整保存在 `fact` JSON，不拆行、不派生额外物理列。
- 正常消息写入单一 ClickHouse 表；任意确定性失败写入插件专属 Kafka DLQ。
- 保留 Kafka 主题、分区、偏移量、记录时间和完整原始消息。
- 沿用单条详情和无菜单的参数聚合分析页，可按设备 ID、IP 或启动 ID 检索；不安装消息管理应用、插件菜单或数据看板。

## 数据流

```text
Kafka
  ├── ^(android|h5|ios|host|wechat)_fact_.*$
  └── 12 个精确 server_fact_* 主题
        ▼
统一信封解析与 63 列映射
        ▼
Server Topic ──▶ Server v1 严格校验
        ├── 合法 ──▶ zenvis.synap_agent_message
        └── 异常 ──▶ com.coolxer.plugin.synap.dead-letter
```

主题过滤发生在 Kafka Source。未匹配 `SYNAP_KAFKA_TOPIC_PATTERN` 的主题不会进入 ClickHouse 或 DLQ。原五端 `fact.type` 只要求非空；Server Topic 必须与受支持的 12 种 Fact 类型一一对应。

推送配置第一行必须严格保持为 `---`，确保 Vectum 将配置识别并写成 `push.yaml`。

## 插件资源

| 资源 | 标识 |
| --- | --- |
| 包名 | `com.coolxer.plugin.synap` |
| 版本 | `1.2.0` |
| 实体 | `synap_agent_message` |
| ClickHouse 表 | `zenvis.synap_agent_message` |
| 推送任务 | `synap-kafka-to-clickhouse.yaml` |
| 详情页面 | `com.coolxer.plugin.synap.detail-message` |
| 参数聚合页面 | `com.coolxer.plugin.synap.parameter-analytics` |
| 插件菜单 | 不提供，`08_menu/config.json` 为 `[]` |
| 数据看板 | 不提供 |

平台自动注入 `zenvis_id` 和 `zenvis_insert_time`；插件不会解析、转换或写入这两个保留字段。

## 输入和 Server 语义

所有消息必须包含 `fact.common.guid`、正整数 `fact.common.start_id`、可解析的 `fact.common.client_time` 和非空 `fact.type`。

Server 还必须满足：

- `fact.common.platform=server`；
- `fact.message_id` 非空，`fact.observed_at` 是合法 Unix 毫秒时间，`fact.sequence` 是正整数；
- Topic 与 `StartData` 及 11 类业务 Fact 严格一一对应；
- `StartData.config` 为对象；其余 Fact 的 `payload` 为对象且 `schemaVersion=1`；
- 业务数字、布尔、时间、字符串数组和对象数组满足 Server v1 契约；三类批量数组非空且每项为对象；
- 依赖漏洞集合若存在，必须具有合法对象、purl、漏洞 ID、别名数组和 CVSS 类型。

`fact` 仅移除已结构化的 `common`，其余结构原样保存。`assets[]`、`observations[]`、`dependencies[]` 即使有多项也只生成一条 Zenvis 记录；`vulnerabilities` 不按 purl 拆分或派生列。完整映射见 [`00_doc/server-data-contract.md`](00_doc/server-data-contract.md)。

## 环境变量

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | Kafka 引导节点 |
| `SYNAP_KAFKA_GROUP_ID` | `zenvis-plugin-synap-agent-message-v2` | 六类平台共用消费组 |
| `SYNAP_KAFKA_TOPIC_PATTERN` | 原五端事实主题 + 12 个精确 Server Topic | Kafka Topic 正则 |
| `SYNAP_DLQ_TOPIC` | `com.coolxer.plugin.synap.dead-letter` | 异常消息主题 |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse HTTP 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | 数据库 |
| `CLICKHOUSE_USER` | `default` | 用户名 |
| `CLICKHOUSE_PASSWORD` | `SFGEfSVVMcUHCBCjKmzJ` | ClickHouse 密码；部署注入值优先 |

不再使用 `SYNAP_SERVER_KAFKA_GROUP_ID` 或 `SYNAP_SERVER_KAFKA_TOPIC_PATTERN`。若部署环境覆盖了 `SYNAP_KAFKA_TOPIC_PATTERN`，升级时必须把 12 个 Server Topic 加入该正则。

Vectum 只允许使用已注入且被 `VECTUM_VECTOR_ALLOWED_ENV` 放行的变量。ClickHouse Sink 固定使用 `${CLICKHOUSE_PASSWORD:-SFGEfSVVMcUHCBCjKmzJ}`，部署变量优先。

## DLQ 与回溯

DLQ 使用 JSON 编码并保留：

- 原始 `message`；
- Kafka 的 `topic`、`partition`、`offset` 和 `timestamp`，或映射后的对应来源字段；
- `metadata.dropped.component_id`、`message` 和 `reason`。

修复数据后，只将原始 `message` 重放到原始 Topic，不要发送完整 DLQ 信封，也不要让 DLQ Topic 匹配 Source 正则。

消费组保持 `zenvis-plugin-synap-agent-message-v2`，因此已有五端位点不会回退。首次纳入的 Server 分区使用 `auto_offset_reset: earliest`，从 Kafka 当前仍保留的最早位置开始消费。语义为至少一次，可使用 `source_topic + source_partition + source_offset` 识别原始 Checkpoint。

## 安装与升级

1. 执行 `./build.sh`。
2. 在 Zenvis 插件管理上传 `com-coolxer-plugin-synap.tar.gz`。
3. 安装或升级插件，并确认唯一的“探针 Kafka 消息入库”任务已启动。
4. 通过 `synap_agent_message` 检查记录；使用 `platform=server` 筛选 Server 数据。

`1.2.0` 不改变现有实体和 ClickHouse 表结构，只扩大统一任务的主题范围并增加 Server 严格校验。没有 Server 专属表、数据迁移或 UI。

## 目录

```text
plugin-synap/
├── index.json
├── README.md
├── icon.png
├── 00_doc/
│   ├── README.md
│   └── server-data-contract.md
├── 01_meta/synap-agent-message.json
├── 02_push-task/
│   ├── config.json
│   └── synap-kafka-to-clickhouse.yaml
├── 04_ui/
│   ├── detail-message/
│   └── parameter-analytics/
├── 06_mcp/config.json
├── 07_skill/
└── 08_menu/config.json
```
