# 基础设施端点监测：设计与运维说明

## 目标

在不引入新的监控后端的前提下，统一展示 Zenvis 依赖基础设施的 Prometheus 指标状态，并把关注指标归档到 ClickHouse。职责边界如下：

1. 推送任务负责轮询抓取 Prometheus 端点；
2. 入库任务负责把 Kafka 样本写入 ClickHouse；
3. 动态 API 只聚合 ClickHouse 已入库数据，不访问 Exporter、ClickHouse 原生指标端点或 MinIO 指标端点；
4. 低代码应用与 HTML 大屏只调用动态 API 和平台实体接口。

## 组成

| 组成 | 实现 |
| --- | --- |
| 指标采集 | `infra-prometheus-to-kafka.yaml`，Vector `prometheus_scrape` 每 30 秒抓取六个端点 |
| 指标入库 | `infra-kafka-to-clickhouse.yaml`，消费 Kafka 并写入 ClickHouse |
| 数据模型 | Meta 实体 `infra_metric_sample` / ClickHouse 表 `infra_metric_sample` |
| 聚合 API | `extend-infra-monitor` 读取 `clickHouseDataSource`，返回六个组件的最新批次状态 |
| 低代码应用 | `04_ui/app` + `04_ui/detail-metric` |
| HTML 大屏 | `04_ui/html-page/infra-endpoints.html` |
| 看板注册 | `05_dashboard/config.json` 注册 HTML 大屏，不进入 `06_menu` |
| MCP | 未注册，`07_mcp/config.json` 为空数组 |
| RAG | 未声明插件托管资源，`08_rag/.gitkeep` |
| 运行时技能 | 未配置，`09_skill/.gitkeep` |

## 数据流

```text
Prometheus 端点（Exporter / ClickHouse / MinIO）
        │  Vector prometheus_scrape，每 30 秒
        ▼
infra-prometheus-to-kafka.yaml
        │  白名单筛选 → metric_to_log → 字段规范化
        ▼
Kafka infra-metrics-raw
        │
        ▼
infra-kafka-to-clickhouse.yaml
        │  校验 JSON、字段、枚举、时间与数值
        ▼
ClickHouse infra_metric_sample（TTL 30 天）
        │
        ├── GET /api/v1/plugin/com.coolxer.plugin.infra.monitor/endpoints
        │        └── 按服务取最新 observed_at，聚合验证指标与关键指标
        ├── 低代码“指标样本”列表与详情
        └── HTML 大屏“归档指标”弹窗与归档 KPI
```

页面刷新不会触发对任何指标端点的请求。采集链路停止后，展示 API 会依据数据新鲜度把组件标记为 `down`。

## 聚合口径

动态 API 每次请求执行以下步骤：

1. 在 `infra_metric_sample` 中查询最近 `ZENVIS_INFRA_LOOKBACK_MINUTES` 分钟的数据；
2. 按 `service` 取 `max(observed_at)`，只保留每个服务最新一批样本；
3. 使用验证指标判断状态；
4. 从同一批次提取页面需要的关键指标；
5. 计算 `data_age_ms = now - last_observed_at`。

默认查询最近 15 分钟，新鲜度阈值 120 秒。阈值和查询范围可通过环境变量调整。

## 状态语义

| 状态 | 条件 |
| --- | --- |
| `healthy` | 最新批次在新鲜度阈值内，验证指标存在且大于 0（ClickHouse 只要求指标存在） |
| `degraded` | 最新批次在新鲜度阈值内，但验证指标缺失或为 0 |
| `down` | 查询范围内无样本，或最新样本延迟超过阈值 |

ClickHouse 的部分计数器在实例刚启动时可能为 0，因此采用 `PRESENT` 语义，只要求 `ClickHouseProfileEvents_Query` 存在；其他端点采用 `POSITIVE` 语义，验证指标必须大于 0。

## 端点与指标映射

| 端点 | 验证指标 | 关键指标 |
| --- | --- | --- |
| MySQL | `mysql_up > 0` | 运行时长、当前连接、累计查询、接收/发送字节 |
| Redis | `redis_up > 0` | 运行时长、当前连接、内存占用、累计命令、命中次数 |
| Redis-Stack | `redis_up > 0` | 同 Redis，通过多目标抓取区分 |
| Kafka | `kafka_brokers > 0` | Broker 数量、业务 Topic 去重数量、消费组数量、消费积压总和 |
| ClickHouse | `ClickHouseProfileEvents_Query` 存在 | Query、SelectQuery、InsertQuery、InsertedRows、FailedQuery |
| MinIO | `minio_cluster_health_status > 0` | 已用容量、对象数、可用容量、Bucket 数、在线节点、离线磁盘 |

## 归档数据模型

`infra_metric_sample` 的一条记录代表一次抓取中的一个 Prometheus 指标序列：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sample_id` | `String` | 指标名、端点、实例、时间与标签的 SHA-256 指纹 |
| `observed_at` | `DateTime64(3)` | 抓取时刻（Asia/Shanghai） |
| `service` | `String` | 六个服务的稳定标识 |
| `service_name` | `String` | 服务易读名称 |
| `metric_name` | `String` | Prometheus 指标名 |
| `metric_value` | `Float64` | 指标数值 |
| `metric_kind` | `String` | `counter` / `gauge` |
| `source_type` | `String` | `exporter` / `native` |
| `endpoint` | `String` | 完整抓取 URL |
| `instance` | `String` | Prometheus `instance` 标签 |
| `labels` | `json` | `{"tags": {...}}` 标签集合 |

物理表按 `(service, metric_name, observed_at)` 排序，按 `toYYYYMM(zenvis_insert_time)` 分区，TTL 30 天。平台自动补充 `zenvis_id`、`zenvis_insert_time` 等记录字段。

## 推送任务设计

### Prometheus → Kafka

- 使用 Vector `prometheus_scrape` 并行抓取六个端点，周期 30 秒，超时 10 秒；
- 只保留约 29 个关注指标，避免高基数内部指标全部写入 Kafka；
- 使用 `metric_to_log` 保留原生时间戳，规范化服务标识、指标类型、端点与实例；
- Redis-Stack 多目标抓取通过 `contains(endpoint, "/scrape")` 识别；
- 输出主题默认 `infra-metrics-raw`，异常进入 `infra-metrics-dead-letter`。

### Kafka → ClickHouse

- 独立消费 `infra-metrics-raw`，消费组默认 `infra-metric-sample-clickhouse-v1`；
- 校验 JSON、`sample_id`、`observed_at`、服务枚举、指标名、数值、类型、端点、实例与 `labels.tags`；
- 合法记录以 `json_each_row` 写入 `zenvis.infra_metric_sample`；
- 校验失败进入 `infra-metrics-dead-letter`，不会写入 ClickHouse，也不会被静默丢弃。

Vectum 为每个推送任务启动独立 Vector 进程并共用 `/var/lib/vector`。磁盘缓冲路径由 sink 名称生成，因此同一部署内所有任务的 sink 名称必须唯一。本插件两个任务的死信 sink 分别命名为 `infra_metrics_ingest_dead_letter` 与 `infra_metrics_archive_dead_letter`，避免第二个任务尝试锁定同一 `buffer.lock`。

## API 契约

```text
GET /api/v1/plugin/com.coolxer.plugin.infra.monitor/endpoints
```

- 成功：`status=0`，`data.summary` 和 `data.endpoints` 可用；
- 字段命名：平台 Jackson 配置输出 snake_case；
- 组件顺序固定：MySQL、Redis、Redis-Stack、Kafka、ClickHouse、MinIO；
- `data.summary.avg_data_age_ms` 为有样本组件的平均数据延迟；
- `endpoints[].last_observed_at` 为最新入库样本的抓取时刻；
- `endpoints[].data_age_ms` 为当前时间与最新样本时刻的差值；
- 单个组件无样本或过期时，只影响该组件状态；
- ClickHouse 查询失败时仍返回六个组件，全部标记为 `down` 并写入 `error`。

历史样本使用平台通用实体接口：

```text
GET /api/v1/entity/infra_metric_sample/list
    ?page=1&perPage=50&orderBy=observed_at&orderDir=desc

GET /api/v1/entity/infra_metric_sample/{zenvis_id}/view
```

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ZENVIS_INFRA_LOOKBACK_MINUTES` | `15` | 聚合查询时间范围，5–1440 |
| `ZENVIS_INFRA_STALE_SECONDS` | `120` | 数据新鲜度阈值，30–3600 |
| `ZENVIS_INFRA_QUERY_TIMEOUT_SECONDS` | `10` | ClickHouse 查询超时，1–60 |

采集任务仍使用 `ZENVIS_*_EXPORTER_URL`、`ZENVIS_CLICKHOUSE_METRICS_URL` 与 `ZENVIS_MINIO_METRICS_URL`。这些 URL 只由 Vectum 中的 Vector 使用，不会由动态 API 发起请求。

## 部署前提

`deploy/docker-compose.yml` 已为 ClickHouse 增加：

- `9363:9363` 端口映射（按 `ZENVIS_INFRA_BIND_ADDRESS` 绑定）；
- `/etc/clickhouse-server/config.d/prometheus.xml` 只读挂载。

MinIO 已增加 `MINIO_PROMETHEUS_AUTH_TYPE=${MINIO_PROMETHEUS_AUTH_TYPE:-public}`。这两项是采集任务读取原生指标的前提，插件本身不修改 Compose。

## 验证命令

先确认推送任务已写入数据：

```bash
docker exec clickhouse-service clickhouse-client --query \
  "SELECT service, max(observed_at), count() FROM zenvis.infra_metric_sample GROUP BY service ORDER BY service"
```

再调用动态 API，确认六个组件都有 `last_observed_at`，且 `state` 与 `verification_value` 符合预期：

```bash
curl -fsS http://127.0.0.1:8080/api/v1/plugin/com.coolxer.plugin.infra.monitor/endpoints
```

实际后端端口以部署配置为准；需要登录态时，先取得平台会话 Cookie。

## 已知限制

- 状态代表最近一批入库样本，不是持续可用率；
- 采集任务停止后，页面在新鲜度阈值到期后显示 `down`；
- 动态 API 依赖平台 `clickHouseDataSource`，ClickHouse 不可用时只能返回降级后的全 `down` 结果；
- 聚合查询使用最近时间窗口；若需要长时间趋势，应使用实体接口或独立趋势查询；
- 组件集合与关注指标白名单固定，新增服务需同时修改 API、采集任务与 Meta/入库校验；
- Kafka 至少一次投递可能产生重复样本，趋势查询应结合 `sample_id` 或聚合函数处理。
