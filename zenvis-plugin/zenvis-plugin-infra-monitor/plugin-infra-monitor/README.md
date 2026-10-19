# 基础设施端点监测与指标归档插件

统一展示 Zenvis 部署内 MySQL、Redis、Redis-Stack、Kafka、ClickHouse 与 MinIO 的运行状态。推送任务每 30 秒采集 Prometheus 指标并经 Kafka 写入 ClickHouse；动态 API 只聚合 ClickHouse 中每个服务的最新批次，不直接访问任何 Exporter 或指标端点。插件提供低代码管理应用，并通过 `05_dashboard` 注册 HTML 大屏看板。

## 插件信息

| 项目 | 值 |
| --- | --- |
| 插件名称 | 基础设施端点监测 |
| 包名 | `com.coolxer.plugin.infra.monitor` |
| 版本 | `1.2.1` |
| 数据实体 | 1（`infra_metric_sample`） |
| ClickHouse 表 | 1（`infra_metric_sample`，保留 30 天） |
| 推送任务 | 2（Prometheus → Kafka，Kafka → ClickHouse） |
| 菜单入口 | 低代码应用 `com.coolxer.plugin.infra.monitor.app` |
| 看板入口 | HTML 大屏 `infra-endpoints.html`（由 `05_dashboard` 注册，不占用插件菜单） |

## 监测端点

| 标识 | 服务 | 采集方式 | 默认端点 | 验证指标 |
| --- | --- | --- | --- | --- |
| `mysql` | MySQL | 单容器 Exporter | `http://zenvis-service-exporters:9104/metrics` | `mysql_up > 0` |
| `redis` | Redis | 单容器 Exporter | `http://zenvis-service-exporters:9121/metrics` | `redis_up > 0` |
| `redis-stack` | Redis-Stack | Exporter 多目标抓取 | `http://zenvis-service-exporters:9121/scrape?target=redis://redis-stack-service:6379` | `redis_up > 0` |
| `kafka` | Kafka | 单容器 Exporter | `http://zenvis-service-exporters:9308/metrics` | `kafka_brokers > 0` |
| `clickhouse` | ClickHouse | 原生端点 | `http://clickhouse-service:9363/metrics` | 存在 `ClickHouseProfileEvents_Query` |
| `minio` | MinIO | 原生集群端点 | `http://minio-service:9000/minio/v2/metrics/cluster` | `minio_cluster_health_status > 0` |

MySQL、Redis、Redis-Stack 与 Kafka 由 `zenvis-service-exporters` 单容器内的官方 Exporter 提供。ClickHouse 使用挂载到容器内的原生 Prometheus 配置，MinIO 使用 public 认证的集群指标端点；两者由 `deploy` 的 Docker Compose 启用，插件本身不修改 Compose。

## 状态语义

| 状态 | 条件 |
| --- | --- |
| `healthy` | 最近批次在新鲜度阈值内，验证指标存在且大于 0（ClickHouse 只要求指标存在） |
| `degraded` | 最近批次在新鲜度阈值内，但验证指标缺失或为 0 |
| `down` | 查询范围内无入库样本，或最近样本延迟超过 `ZENVIS_INFRA_STALE_SECONDS` |

动态 API 通过平台 `clickHouseDataSource` 查询 `infra_metric_sample`，按服务取最新 `observed_at` 对应的一批样本。默认查询最近 15 分钟，新鲜度阈值默认 120 秒；查询或 ClickHouse 异常时仍返回六个组件，全部标记为 `down`，错误写入 `error` 字段。

## 数据模型

实体 `infra_metric_sample` 写入 ClickHouse 表 `infra_metric_sample`，每条记录对应一次抓取中的一个 Prometheus 指标序列。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sample_id` | `String` | 指标名、端点、实例、抓取时刻与标签的 SHA-256 指纹，可用于重放去重 |
| `observed_at` | `DateTime64(3)` | Prometheus 抓取时刻（Asia/Shanghai） |
| `service` | `String` | `mysql`、`redis`、`redis-stack`、`kafka`、`clickhouse`、`minio` |
| `service_name` | `String` | 服务易读名称 |
| `metric_name` | `String` | Prometheus 指标名 |
| `metric_value` | `Float64` | 指标数值 |
| `metric_kind` | `String` | `counter` 或 `gauge` |
| `source_type` | `String` | `exporter` 或 `native` |
| `endpoint` | `String` | 实际抓取的完整 URL |
| `instance` | `String` | Prometheus `instance` 标签 |
| `labels` | `json` | `{"tags": {...}}` 形式的序列标签 |

表按 `(service, metric_name, observed_at)` 排序，按 `toYYYYMM(zenvis_insert_time)` 分区，TTL 为 30 天。平台自动维护 `zenvis_id`、`zenvis_insert_time` 等记录字段。

## 数据链路

```text
Prometheus 端点（Exporter / ClickHouse / MinIO）
        │  每 30 秒并行抓取
        ▼
infra-prometheus-to-kafka.yaml
        │  白名单筛选 → 规范化 → JSON
        ▼
Kafka: infra-metrics-raw
        │
        ▼
infra-kafka-to-clickhouse.yaml
        │  校验 JSON、字段、枚举、时间、数值、labels
        ▼
ClickHouse: infra_metric_sample（TTL 30 天）
        │
        ├── 动态 API 按服务聚合最新批次
        └── 低代码应用 / HTML 大屏
```

两个推送任务互相独立：采集任务只负责把规范化样本送入 Kafka，入库任务只消费 Kafka 并写 ClickHouse。动态 API 不参与采集，只读取入库结果；页面刷新不会触发对 Exporter 的请求。

Vectum 会为每个推送任务启动独立 Vector 进程，并共用 `/var/lib/vector`。Vector 的磁盘缓冲目录按 sink 名称生成，因此不同任务中的 sink 名称必须唯一；本插件两个任务的死信 sink 分别为 `infra_metrics_ingest_dead_letter` 与 `infra_metrics_archive_dead_letter`。若复用同名 sink，第二个任务会因 `buffer.lock` 被占用而启动失败。

### 采集任务

`02_push-task/infra-prometheus-to-kafka.yaml`：

- 使用 Vector `prometheus_scrape` 并行抓取六个端点，周期 30 秒，超时 10 秒；
- 只保留约 29 个关注指标，避免把 Exporter 内部高基数指标全部写入 Kafka；
- 使用 `metric_to_log` 保留原生时间戳，规范化服务标识、指标类型、端点与实例；
- Redis-Stack 多目标抓取通过 `contains(endpoint, "/scrape")` 识别，避免 URL 百分号编码影响匹配；
- 输出主题默认 `infra-metrics-raw`，异常进入 `infra-metrics-dead-letter`。

### 入库任务

`02_push-task/infra-kafka-to-clickhouse.yaml`：

- 独立消费 `infra-metrics-raw`，消费组默认 `infra-metric-sample-clickhouse-v1`；
- 校验合法 JSON、64 位 `sample_id`、`observed_at` 格式、服务枚举、指标名、数值、`metric_kind`、`source_type`、端点、实例与 `labels.tags`；
- 合法记录以 `json_each_row` 写入 `zenvis.infra_metric_sample`；
- 任意校验失败进入 `infra-metrics-dead-letter`，不会写入 ClickHouse，也不会被静默丢弃。

## 环境变量

Kafka 与 ClickHouse 连接变量：

| 变量 | 默认值 |
| --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` |
| `INFRA_METRICS_TOPIC` | `infra-metrics-raw` |
| `INFRA_METRICS_GROUP_ID` | `infra-metric-sample-clickhouse-v1` |
| `INFRA_METRICS_DLQ_TOPIC` | `infra-metrics-dead-letter` |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` |
| `CLICKHOUSE_DATABASE` | `zenvis` |
| `CLICKHOUSE_USER` | `default` |
| `CLICKHOUSE_PASSWORD` | 平台默认密码，部署时应通过环境变量覆盖 |

抓取端点变量（由采集任务使用，同时作为展示接口的 URL 兜底）：

| 变量 | 默认值 |
| --- | --- |
| `ZENVIS_MYSQL_EXPORTER_URL` | `http://zenvis-service-exporters:9104/metrics` |
| `ZENVIS_REDIS_EXPORTER_URL` | `http://zenvis-service-exporters:9121/metrics` |
| `ZENVIS_REDIS_STACK_EXPORTER_URL` | `http://zenvis-service-exporters:9121/scrape?target=redis://redis-stack-service:6379` |
| `ZENVIS_KAFKA_EXPORTER_URL` | `http://zenvis-service-exporters:9308/metrics` |
| `ZENVIS_CLICKHOUSE_METRICS_URL` | `http://clickhouse-service:9363/metrics` |
| `ZENVIS_MINIO_METRICS_URL` | `http://minio-service:9000/minio/v2/metrics/cluster` |
| `ZENVIS_INFRA_LOOKBACK_MINUTES` | `15`，允许范围 5–1440，动态 API 查询最近样本的时间范围 |
| `ZENVIS_INFRA_STALE_SECONDS` | `120`，允许范围 30–3600，超过后判定为 `down` |
| `ZENVIS_INFRA_QUERY_TIMEOUT_SECONDS` | `10`，允许范围 1–60，ClickHouse 查询超时 |

URL 只由 Vectum 中的采集任务访问，`extend-infra-monitor` 不会向这些地址发起请求。展示 API 使用平台 ClickHouse 数据源，因此本地 IDE 后端继承了系统代理也不会影响状态读取。

> 部署注意：Vectum 通过允许列表控制可透传给 Vector 的环境变量。若部署层尚未把 `INFRA_METRICS_TOPIC`、`INFRA_METRICS_GROUP_ID`、`INFRA_METRICS_DLQ_TOPIC` 加入允许列表，任务仍可使用默认值运行，但无法在部署层覆盖主题与消费组。

## 动态 API

```text
GET /api/v1/plugin/com.coolxer.plugin.infra.monitor/endpoints
```

响应使用平台统一包装和 snake_case 字段：

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "generated_at": "2026-10-19T18:00:00+08:00",
    "summary": {
      "total": 6,
      "healthy": 6,
      "degraded": 0,
      "down": 0,
      "avg_data_age_ms": 5200,
      "metric_count": 185
    },
    "endpoints": [
      {
        "id": "mysql",
        "name": "MySQL Exporter",
        "state": "healthy",
        "data_age_ms": 4800,
        "last_observed_at": "2026-10-19T17:59:55.200+08:00",
        "verification_metric": "mysql_up",
        "verification_value": 1.0,
        "metric_count": 6,
        "metrics": [
          {
            "key": "uptime_seconds",
            "label": "运行时长",
            "value": 3600.0,
            "unit": "s",
            "format": "duration"
          }
        ],
        "prometheus": {
          "job": "zenvis-mysql",
          "target": "zenvis-service-exporters:9104",
          "metrics_path": "/metrics",
          "target_parameter": null
        },
        "error": null
      }
    ]
  }
}
```

`endpoints[]` 还包含 `category`、`mode`、`description`、`endpoint` 和 `target`。`last_observed_at` 为该服务最新入库样本的抓取时刻，`data_age_ms` 为当前时间与该时刻的差值；`metric_count` 是最新批次中的指标序列条数。历史样本使用平台通用实体接口：

```text
GET /api/v1/entity/infra_metric_sample/list?page=1&perPage=50&orderBy=observed_at&orderDir=desc
GET /api/v1/entity/infra_metric_sample/{zenvis_id}/view
```

## 页面

### 低代码应用

菜单“基础设施指标监控”打开应用 `com.coolxer.plugin.infra.monitor.app`：

- `overview.json`：基于 ClickHouse 最新批次的组件汇总、状态与关键指标表格；
- `metric-list.json`：`infra_metric_sample` 分页列表，可按服务、指标、时间筛选并查看详情；
- `detail-metric`：按 `zenvis_id` 展示单条样本及 `labels` JSON。

### HTML 大屏

`04_ui/html-page/infra-endpoints.html` 提供：

大屏由 `05_dashboard/config.json` 注册，通过平台内置的“大屏看板”入口访问；`06_menu/config.json` 不再重复声明该页面。

- 8 个 KPI：组件总数、正常、降级、异常、平均数据延迟、最新批次样本、归档总量、最近归档时间；
- 六个组件卡片：状态、数据延迟、验证指标、最近入库时间、关键指标、采集目标与 Prometheus job；
- “抓取配置”弹窗：根据当前端点动态生成 `scrape_configs`，支持一键复制；
- “归档指标”弹窗：展示 ClickHouse 最近 50 条样本，包含服务、指标、值、类型、实例、端点与标签；
- 底部数据链路条：`infra-metrics-raw → infra_metric_sample → ClickHouse（保留 30 天）`；
- 手动刷新、默认 30 秒自动刷新、加载/空数据/错误状态及最后更新时间；
- 自适应 iframe 高度，页面本身不产生纵向滚动，长内容在卡片与归档表格内部滚动。

大屏同时请求动态 API 与实体列表接口，使用 `Promise.allSettled` 隔离两类失败：归档接口不可用时，组件卡片仍正常显示；ClickHouse 聚合失败时，最近归档仍可查看。接口兼容 `/zenvis` 部署前缀。

## DLQ 与重放

异常记录进入 `infra-metrics-dead-letter`，消息为 JSON，包含原始内容和丢弃元数据：

```json
{
  "message": "原始指标或 Kafka 消息",
  "metadata": {
    "dropped": {
      "component_id": "normalize_infra_metric",
      "message": "具体校验失败原因",
      "reason": "error"
    }
  }
}
```

处理步骤：

1. 按 `metadata.dropped.component_id` 判断失败发生在采集规范化还是 Kafka 入库校验；
2. 按 `metadata.dropped.message` 修正端点、指标名、时间格式或业务字段；
3. 将修正后的原始 JSON 重新投递到 `infra-metrics-raw`；
4. 确认 `infra_metric_sample` 出现对应样本后，再按团队保留策略清理 DLQ 记录。

DLQ 的记录保留策略由 Kafka 主题配置决定，插件不自动删除。不要直接把 DLQ 消息原样重新投递到 `infra-metrics-raw`，否则会再次校验失败。

## 投递语义

Kafka 为至少一次投递语义。生产端启用 `acks=-1`、重试与磁盘缓冲，消费端在写入 ClickHouse 成功后才提交位点，因此故障重试或重放可能产生重复样本。表没有物理主键，`sample_id` 是稳定指纹；需要精确去重时按 `sample_id` 聚合，或在查询侧使用 `any`/`argMax`。

## 外部 Prometheus

本插件不启动 Prometheus。需要在外部 Prometheus 增加抓取配置，可点击大屏“抓取配置”获取动态版本，或使用：

```yaml
scrape_configs:
  - job_name: zenvis-mysql
    static_configs:
      - targets: ['zenvis-service-exporters:9104']

  - job_name: zenvis-redis
    static_configs:
      - targets: ['zenvis-service-exporters:9121']

  - job_name: zenvis-redis-stack
    metrics_path: /scrape
    params:
      target: ['redis://redis-stack-service:6379']
    static_configs:
      - targets: ['zenvis-service-exporters:9121']

  - job_name: zenvis-kafka
    static_configs:
      - targets: ['zenvis-service-exporters:9308']

  - job_name: zenvis-clickhouse
    static_configs:
      - targets: ['clickhouse-service:9363']

  - job_name: zenvis-minio
    metrics_path: /minio/v2/metrics/cluster
    static_configs:
      - targets: ['minio-service:9000']
```

Prometheus 运行在宿主机时，targets 应改为 `127.0.0.1:9104`、`127.0.0.1:9121`、`127.0.0.1:9308`、`127.0.0.1:9363` 和 `127.0.0.1:19000`。

## 安全与限制

- MinIO 使用 `MINIO_PROMETHEUS_AUTH_TYPE=public`，任何能访问 `19000` 端口且知道指标路径的客户端都可读取集群指标。默认 Compose 绑定 `127.0.0.1`；若改为 `0.0.0.0`，应改用 JWT/Bearer 认证或通过网络策略限制访问。
- ClickHouse 的 `9363/metrics` 同样只应绑定可信网络；必要时删除宿主端口映射，只允许容器网络抓取。
- 展示状态来自最近入库批次；采集任务停止后，页面会在新鲜度阈值到期后显示 `down`。
- 动态 API 不直接访问 Exporter、ClickHouse 原生指标端点或 MinIO 指标端点，只使用平台 ClickHouse 数据源。
- 组件集合与关注指标白名单固定。新增服务需要同时修改动态 API、采集任务白名单与 Meta/入库校验，并提升插件版本。
- Kafka 至少一次语义可能产生重复样本；查询趋势时应结合 `sample_id` 或聚合函数处理。

## 验证

```bash
curl -fsS -H 'User-Agent: Prometheus/2.0' http://127.0.0.1:9363/metrics | head
curl -fsS http://127.0.0.1:19000/minio/v2/metrics/cluster | head
curl -fsS http://127.0.0.1:8080/api/v1/plugin/com.coolxer.plugin.infra.monitor/endpoints
curl -fsS 'http://127.0.0.1:8080/api/v1/entity/infra_metric_sample/list?page=1&perPage=5&orderBy=observed_at&orderDir=desc'
```

Vectum 容器内校验推送任务：

```bash
docker exec zenvis-service-vectum /vector/bin/vector validate --no-environment \
  --config-yaml /path/to/infra-prometheus-to-kafka.yaml
docker exec zenvis-service-vectum /vector/bin/vector test \
  --config-yaml /path/to/infra-kafka-to-clickhouse.yaml
```

## 构建

```bash
cd zenvis-plugin/zenvis-plugin-infra-monitor
./build.sh
```

构建会执行 Maven 测试，确认动态 JAR 是薄 JAR（无 `BOOT-INF`），复制到 `plugin-infra-monitor/03_api/`，并生成 `com-coolxer-plugin-infra-monitor.tar.gz`。
