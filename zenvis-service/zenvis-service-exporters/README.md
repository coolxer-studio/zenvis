# zenvis-service-exporters

**zenvis-service-exporters** 是一个用于采集 ZenVis 基础设施指标的 Prometheus Exporter 集合容器。单容器内同时运行 MySQL、Redis（含 redis-stack）与 Kafka 的官方 Exporter；ClickHouse 与 MinIO 复用其内置的原生 Prometheus 指标端点。

该服务为**独立服务**，不修改 ZenVis 主部署栈（`deploy/docker-compose.yml`）；通过示例 override 文件可按需纳入主栈编排。

---

## 一、采集范围与端口

| 组件 | 采集目标 | 端点 | 实现方式 |
| :--- | :--- | :--- | :--- |
| MySQL 8.4 | `mysql-service:3306` | 容器 `:9104/metrics` | `prom/mysqld_exporter` |
| Redis 7 | `redis-service:6379` | 容器 `:9121/metrics`、`:9121/scrape?target=` | `oliver006/redis_exporter`（多目标） |
| Redis-Stack 7.2 | `redis-stack-service:6379` | 同上（`/scrape?target=redis://redis-stack-service:6379`） | `oliver006/redis_exporter`（多目标） |
| Kafka 4.2 | `kafka-service:9092` | 容器 `:9308/metrics` | `danielqsj/kafka_exporter` |
| ClickHouse 25.9 | `clickhouse-service:9363` | `:9363/metrics` | ClickHouse 内置 Prometheus 端点 |
| MinIO | `minio-service:9000` | `/minio/v2/metrics/cluster` | MinIO 内置 Prometheus 端点 |

> Redis 与 Redis-Stack 在 ZenVis 中共用 `REDIS_PASSWORD`，因此一个 `redis_exporter` 即可覆盖两者；Prometheus 通过 `/scrape?target=` 做多目标采集。

---

## 二、目录结构

```
zenvis-service-exporters/
├── Dockerfile                              # 多进程容器镜像（alpine + supervisor）
├── build.sh                                # 按架构下载二进制并构建/推送镜像
├── supervisord.conf.template               # 进程托管模板（entrypoint 渲染）
├── config/
│   ├── mysqld_exporter.cnf.template        # MySQL 采集凭据模板
│   └── clickhouse-prometheus.xml           # ClickHouse 原生指标端点配置（供主栈挂载）
├── scripts/entrypoint.sh                   # 渲染配置并启动 supervisord
├── docker-compose.yml                      # 独立运行
├── docker-compose.override.example.yml     # 接入主栈的示例 override
├── .env.example
└── README.md
```

---

## 三、构建

```bash
cd zenvis-service/zenvis-service-exporters

# 构建（按当前架构下载 exporter 二进制，默认不推送）
./build.sh

# 构建并推送镜像
PUSH_IMAGE=true ./build.sh
```

**构建参数**

| 变量 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `PUSH_IMAGE` | `false` | 是否推送镜像到 Registry |

**镜像标签**

| 标签格式 | 示例 |
| :--- | :--- |
| `{version}-{arch}` | `1.0.0-amd64`、`1.0.0-arm64` |
| `{YYYYMMDD}-{arch}` | `20261013-amd64` |

**打包的 Exporter 版本**（见 `build.sh`，升级时同步修改）：

| Exporter | 版本 |
| :--- | :--- |
| mysqld_exporter | 0.18.0 |
| redis_exporter | 1.86.0 |
| kafka_exporter | 1.9.0 |

> 支持 `amd64` 与 `arm64`：`build.sh` 会按本机架构下载对应二进制，并通过 `--build-arg BASE_IMAGE_ARCH` 选择同架构基础镜像。
>
> 基础镜像使用内网 Registry：`crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/alpine:3.20-{amd64|arm64}`。

---

## 四、运行

### 4.1 独立运行（推荐）

前置条件：ZenVis 主栈已通过 `./zenvisctl compose up` 启动，容器可加入 `zenvis_zenvis-network`。

```bash
cd zenvis-service/zenvis-service-exporters
export MYSQL_ROOT_PASSWORD='<mysql root 密码>'
export REDIS_PASSWORD='<redis 密码>'
docker compose up -d
```

也可复制 `.env.example` 为 `.env` 后按需填写再 `docker compose up -d`。

### 4.2 docker run

```bash
docker run -d --name zenvis-service-exporters \
  --network zenvis_zenvis-network \
  -e MYSQL_ROOT_PASSWORD='<mysql root 密码>' \
  -e REDIS_PASSWORD='<redis 密码>' \
  -p 127.0.0.1:9104:9104 -p 127.0.0.1:9121:9121 -p 127.0.0.1:9308:9308 \
  --restart unless-stopped \
  crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/zenvis-service-exporters:1.0.0-amd64
```

---

## 五、环境变量

| 变量 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `TZ` | `Asia/Shanghai` | 时区 |
| `MYSQL_HOST` / `MYSQL_PORT` | `mysql-service` / `3306` | MySQL 地址 |
| `MYSQL_EXPORTER_USER` | `root` | 采集账号 |
| `MYSQL_ROOT_PASSWORD` | 无 | 回退凭据（未提供 `MYSQL_EXPORTER_PASSWORD` 时使用） |
| `MYSQL_EXPORTER_PASSWORD` | 无 | 专用采集账号密码 |
| `REDIS_HOST` / `REDIS_PORT` | `redis-service` / `6379` | 默认 Redis 目标 |
| `REDIS_PASSWORD` | 无（必填） | Redis / Redis-Stack 密码 |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | Kafka 地址 |
| `MYSQL_EXPORTER_PORT` | `9104` | mysqld_exporter 监听端口 |
| `REDIS_EXPORTER_PORT` | `9121` | redis_exporter 监听端口 |
| `KAFKA_EXPORTER_PORT` | `9308` | kafka_exporter 监听端口 |

> 修改监听端口时，`Dockerfile` 的 HEALTHCHECK 与 `docker-compose.yml` 的端口映射需同步调整。

> 密码字符集：entrypoint 会把密码渲染进 supervisord 配置，请避免使用**空白字符**与 **`%`**（supervisord 插值语义）。`./zenvisctl compose init` 生成的随机密码完全满足该约束。

### MySQL 最小权限账号（可选，替代 root）

```sql
CREATE USER 'exporter'@'%' IDENTIFIED BY '<password>';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';
FLUSH PRIVILEGES;
```

---

## 六、ClickHouse 原生指标端点

ClickHouse 25.9 内置 Prometheus 端点（默认注释未启用）。启用方式：将本目录的配置挂载到 `clickhouse-service`：

```bash
mkdir -p deploy/config/clickhouse
cp zenvis-service/zenvis-service-exporters/config/clickhouse-prometheus.xml \
   deploy/config/clickhouse/prometheus.xml
```

然后在 `deploy/docker-compose.yml` 的 `clickhouse-service` 中增加：

```yaml
    ports:
      - "${ZENVIS_INFRA_BIND_ADDRESS}:9363:9363"
    volumes:
      - ./config/clickhouse/prometheus.xml:/etc/clickhouse-server/config.d/prometheus.xml:ro
```

或直接使用本目录提供的 override（见 `docker-compose.override.example.yml`）。重启后验证：

```bash
curl -fsS http://clickhouse-service:9363/metrics | head
```

---

## 七、MinIO 原生指标端点

MinIO 默认以 `jwt` 方式保护指标端点，推荐在内部网络内开启 public：

```yaml
services:
  minio-service:
    environment:
      MINIO_PROMETHEUS_AUTH_TYPE: "public"
```

也可保持 jwt，用 `mc` 生成 bearer token：

```bash
mc alias set zenvis http://minio-service:9000 <MINIO_ROOT_USER> <MINIO_ROOT_PASSWORD>
mc admin prometheus generate zenvis cluster
```

将生成的 `bearer_token` 填入 Prometheus 的 `authorization` 配置即可。指标路径：

- 集群指标：`/minio/v2/metrics/cluster`
- 节点指标：`/minio/v2/metrics/node`
- 桶指标：`/minio/v2/metrics/bucket`

---

## 八、Prometheus 抓取配置

```yaml
scrape_configs:
  - job_name: zenvis-mysql
    static_configs:
      - targets: ['<exporter-host>:9104']

  - job_name: zenvis-redis
    metrics_path: /scrape
    static_configs:
      - targets:
          - redis://redis-service:6379
          - redis://redis-stack-service:6379
    relabel_configs:
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: <exporter-host>:9121

  - job_name: zenvis-kafka
    static_configs:
      - targets: ['<exporter-host>:9308']

  - job_name: zenvis-clickhouse
    static_configs:
      - targets: ['clickhouse-service:9363']

  - job_name: zenvis-minio
    metrics_path: /minio/v2/metrics/cluster
    static_configs:
      - targets: ['minio-service:9000']
```

其中 `<exporter-host>` 为本容器的地址（`127.0.0.1` 或宿主机 IP）。

---

## 九、验证

```bash
# 进程状态
docker exec zenvis-service-exporters supervisorctl -c /etc/zenvis-exporters/supervisord.conf status

# 各 Exporter 指标
curl -fsS http://127.0.0.1:9104/metrics | head
curl -fsS "http://127.0.0.1:9121/scrape?target=redis://redis-service:6379" | head
curl -fsS "http://127.0.0.1:9121/scrape?target=redis://redis-stack-service:6379" | head
curl -fsS http://127.0.0.1:9308/metrics | head

# ClickHouse / MinIO（需先按第六、七节启用）
curl -fsS http://clickhouse-service:9363/metrics | head
curl -fsS http://minio-service:9000/minio/v2/metrics/cluster | head
```

容器健康状态由 `supervisorctl status` 判定。

---

## 十、常见问题

| 现象 | 排查方向 |
| :--- | :--- |
| MySQL 指标缺失 / 权限报错 | 采集账号是否具备 `PROCESS`、`REPLICATION CLIENT`、`SELECT`；密码是否包含需转义字符 |
| Redis 连接失败 | `REDIS_PASSWORD` 是否正确；`redis_exporter` 需 `/scrape?target=` 才能采集 redis-stack |
| Kafka 指标为空 | `KAFKA_BOOTSTRAP_SERVERS` 是否正确、Kafka 是否健康；PLAINTEXT 无需 SASL |
| ClickHouse `:9363` 不可达 | 是否已挂载 `prometheus.xml` 并重启 `clickhouse-service` |
| MinIO 返回 403 | 是否设置 `MINIO_PROMETHEUS_AUTH_TYPE=public`，或未配置 bearer token |
| 容器解析不到服务名 | 是否加入 `zenvis_zenvis-network`（compose project 为 `zenvis`） |
| 容器启动即退出 | 密码是否含空白字符或 `%`；`REDIS_PASSWORD` 是否已提供 |

---

## 十一、许可证

Apache 2.0 License
