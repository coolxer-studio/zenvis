# Nginx日志监控插件

## 概述

本插件用于收集和展示 Nginx 的访问日志（access_log）和错误日志（error_log），提供日志检索、详情查看、IP 命中调查功能。

插件目录使用平台编号：`06_menu`、`07_mcp`、`08_rag`。当前 `08_rag/` 仅含 `.gitkeep`，安装器不读取其配置，文档 RAG 仍来自 `00_doc/`。

## 插件信息

| 项目 | 值 |
|------|-----|
| 插件名称 | Nginx日志监控 |
| 包名 | `com.coolxer.plugin.nginx` |
| 版本 | 1.0.1 |
| 作者 | CoolXer |

## 数据模型

### 实体一览

| 实体 | 表名 | 说明 |
|------|------|------|
| `nginx_access_log` | `zenvis.nginx_access_log` | Nginx 访问日志，JSON 格式采集 |
| `nginx_error_log` | `zenvis.nginx_error_log` | Nginx 错误日志，正则格式解析 |

### nginx_access_log 属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `remote_addr` | String | 客户端 IP（含 IP 命中链接） |
| `server_addr` | String | 服务端地址（含 IP 命中链接） |
| `request_line` | String | 完整请求行（含详情页链接） |
| `time_local` | DateTime | 请求时间 |
| `method` | String | HTTP 请求方法 |
| `uri` | String | 请求 URI |
| `protocol` | String | HTTP 协议版本 |
| `status` | UInt16 | HTTP 状态码 |
| `body_bytes_sent` | UInt64 | 响应大小（字节） |
| `http_referer` | String | 来源页 |
| `http_user_agent` | String | User-Agent |
| `request_time` | Float64 | 请求耗时（秒） |
| `upstream_addr` | String | 上游地址 |
| `upstream_response_time` | Float64 | 上游耗时（秒） |
| `server_name` | String | 虚拟主机名 |

### nginx_error_log 属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `client_addr` | String | 客户端 IP（含 IP 命中链接） |
| `server_addr` | String | 服务端地址（含 IP 命中链接） |
| `message` | String | 错误消息（含详情页链接） |
| `level` | String | 日志级别 |
| `log_time` | DateTime | 日志时间 |
| `pid` | UInt32 | 进程 PID |
| `tid` | UInt64 | 线程 ID |
| `connection_id` | UInt64 | 连接 ID |

## 推送任务架构

采用两段式 Vector 管道：

| 任务 | 文件 | 说明 |
|------|------|------|
| Nginx日志Syslog采集 | `nginx-syslog-to-kafka.yaml` | 在 UDP 514 接收 Nginx Syslog 日志，按 tag 打标后转发到 Kafka |
| Nginx日志解析入库 | `nginx-kafka-to-clickhouse.yaml` | 消费 Kafka，解析后写入 ClickHouse |

### 数据流

```
Nginx access_log (syslog tag=nginx_access) ──→ UDP 514 Vector syslog source ──→ Kafka (nginx-access-raw) ──→ JSON 解析 ──→ ClickHouse (nginx_access_log)
Nginx error_log  (syslog tag=nginx_error)  ──→ UDP 514 Vector syslog source ──→ Kafka (nginx-error-raw)  ──→ 正则解析 ──→ ClickHouse (nginx_error_log)
```

- 采集端按 syslog tag（appname）路由：`nginx_access` → access 队列，`nginx_error` → error 队列，其余 tag 打印到控制台。
- 两类日志解析失败的消息分别进入死信主题 `nginx-access-dead-letter` / `nginx-error-dead-letter`。

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `NGINX_ACCESS_TOPIC` | `nginx-access-raw` | Access 日志 Kafka 主题 |
| `NGINX_ERROR_TOPIC` | `nginx-error-raw` | Error 日志 Kafka 主题 |
| `NGINX_ACCESS_DLQ_TOPIC` | `nginx-access-dead-letter` | Access 死信主题 |
| `NGINX_ERROR_DLQ_TOPIC` | `nginx-error-dead-letter` | Error 死信主题 |
| `NGINX_ACCESS_GROUP_ID` | `nginx-access-clickhouse-v1` | Access 解析任务 Kafka 消费组 |
| `NGINX_ERROR_GROUP_ID` | `nginx-error-clickhouse-v1` | Error 解析任务 Kafka 消费组 |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | Kafka 地址 |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | ClickHouse 数据库 |
| `CLICKHOUSE_USER` | `default` | ClickHouse 用户 |
| `CLICKHOUSE_PASSWORD` | 见 YAML 默认值 | ClickHouse 密码 |

## Nginx 配置

详见 [00_doc/nginx-log-config.md](00_doc/nginx-log-config.md)。

核心配置：
- Access log 使用 JSON 格式（`log_format json_access`）
- Error log 使用 Nginx 默认格式
- 日志路径默认 `/var/log/nginx/access.log` 和 `/var/log/nginx/error.log`

## UI 页面

插件以低代码应用形式提供统一入口，`04_ui/site.json` 组织应用导航，应用配置索引为 `com.coolxer.plugin.nginx`。

| 页面 | 配置文件 | 说明 |
|------|----------|------|
| 监控概览 | `04_ui/index.json` | 应用首页，含访问/错误日志总量与今日新增 KPI、近 7 天趋势图、HTTP 状态码分布、错误级别分布 |
| 访问日志 | `04_ui/access-log.json` | 访问日志分页列表，支持排序、筛选、详情跳转和 IP 命中调查 |
| 错误日志 | `04_ui/error-log.json` | 错误日志分页列表，支持排序、筛选、详情跳转和 IP 命中调查 |
| IP 命中调查 | `04_ui/ip-statistics/index.json` | 输入或携带 IP 检索命中记录数，OR 匹配各实体的多个 IP 字段（配置索引 `com.coolxer.plugin.nginx.ip-statistics`） |
| 访问日志详情 | `04_ui/detail-access-log/index.json` | 按 record_id 查看访问日志完整属性（配置索引 `com.coolxer.plugin.nginx.detail-access-log`） |
| 错误日志详情 | `04_ui/detail-error-log/index.json` | 按 record_id 查看错误日志完整属性（配置索引 `com.coolxer.plugin.nginx.detail-error-log`） |

访问日志、错误日志、IP 命中调查和两类详情页均包含在应用导航或列表跳转中；详情按钮与 IP 链接在新窗口打开。

## 菜单

| 菜单项 | 类型 | 路由参数 |
|--------|------|----------|
| Nginx日志监控 | LOW_CODE_APP | `com.coolxer.plugin.nginx` |

## 部署步骤

1. 在生产 Nginx 上按 [00_doc/nginx-log-config.md](00_doc/nginx-log-config.md) 配置 JSON 格式访问日志，并添加 Syslog 转发（UDP 514 → vectum）
2. 确保 Nginx 所在网络可达 Syslog 采集端（`zenvis-service-vectum:514/UDP`）
3. 安装插件到 Zenvis 平台
4. 在推送任务页面启用「Nginx日志Syslog采集」和「Nginx日志解析入库」
5. 从菜单进入「Nginx日志监控」页面验证数据

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.1 | 2026-10-19 | 将低代码页面升级为低代码应用，新增访问日志、错误日志分页列表页，并纳入应用导航 |
| 1.0.0 | 2026-10-10 | 初始版本，支持 access_log 和 error_log 采集、检索、详情、IP 调查 |
