# Nginx日志监控知识说明

本目录是 Nginx 日志监控插件的专项文档入口。

- [`nginx-log-config.md`](nginx-log-config.md)：Nginx 生产环境日志配置指南，包含 access_log JSON 格式定义、error_log 配置、日志轮转和完整配置示例。

## 基本信息

- **插件名称**: Nginx日志监控
- **包名**: `com.coolxer.plugin.nginx`
- **版本**: 1.0.0
- **作者**: CoolXer
- **描述**: 收集 Nginx 访问日志（access_log）和错误日志（error_log），提供日志检索、详情查看、IP 命中调查。

## 数据模型

### 实体一览

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| `nginx_access_log` | `zenvis.nginx_access_log` | Nginx 访问日志，JSON 格式采集 |
| `nginx_error_log` | `zenvis.nginx_error_log` | Nginx 错误日志，正则格式解析 |

### nginx_access_log 属性

| 属性 | 类型 | 含义 | 默认展示 |
| --- | --- | --- | --- |
| `remote_addr` | String | 客户端 IP 地址 | ✔ |
| `time_local` | DateTime | 请求时间（本地时间） | ✔ |
| `method` | String | HTTP 请求方法 | ✔ |
| `uri` | String | 请求 URI（含查询参数） | ✔ |
| `protocol` | String | HTTP 协议版本 | ✖ |
| `status` | UInt16 | HTTP 响应状态码 | ✔ |
| `body_bytes_sent` | UInt64 | 响应主体字节数 | ✖ |
| `http_referer` | String | Referer 头 | ✖ |
| `http_user_agent` | String | User-Agent 头 | ✖ |
| `request_time` | Float64 | 请求处理总耗时（秒） | ✔ |
| `upstream_addr` | String | 上游服务器地址 | ✖ |
| `upstream_response_time` | Float64 | 上游响应耗时（秒） | ✖ |
| `request_line` | String | 完整请求行 | ✖ |
| `server_addr` | String | 服务端接收地址 | ✖ |
| `server_name` | String | 匹配的虚拟主机名 | ✖ |

### nginx_error_log 属性

| 属性 | 类型 | 含义 | 默认展示 |
| --- | --- | --- | --- |
| `log_time` | DateTime | 错误日志记录时间 | ✔ |
| `level` | String | 日志级别（debug/info/notice/warn/error/crit/alert/emerg） | ✔ |
| `pid` | UInt32 | Nginx worker 进程 PID | ✔ |
| `tid` | UInt64 | Nginx worker 线程 ID | ✖ |
| `connection_id` | UInt64 | 连接序号（用于关联同一连接的多个事件） | ✔ |
| `client_addr` | String | 触发错误的客户端 IP | ✔ |
| `server_addr` | String | 服务器接收地址 | ✖ |
| `message` | String | 错误消息正文 | ✔ |

## 数据推送服务

采用两阶段管道架构：

1. **Nginx日志Syslog采集**（`nginx-syslog-to-kafka.yaml`）：Vector 在 UDP 514 端口启动 Syslog 服务，接收 Nginx 通过 syslog 指令转发的 access/error 日志（RFC3164），按 syslog tag 打标后转发到 Kafka 原始消息队列，不做任何解析或转换。
2. **Nginx日志解析入库**（`nginx-kafka-to-clickhouse.yaml`）：从 Kafka 消费原始消息，access_log 按 JSON 格式解析、error_log 按正则格式解析，映射字段后写入对应 ClickHouse 表。非法日志进入死信队列。

### 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
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

## 可视化配置

插件提供以下可视化入口：

- **Nginx日志监控概览**（插件首页，LOW_CODE_PAGE）：访问/错误日志总量与今日新增 KPI、近 7 天日志趋势图、HTTP 状态码分布、错误日志级别分布
- **访问日志详情页**：按平台记录 ID 查看完整访问日志属性，通过 `request_line` 属性链接进入
- **错误日志详情页**：按平台记录 ID 查看完整错误日志属性，通过 `message` 属性链接进入
- **IP 命中调查页**：输入 IP 或从 `remote_addr` / `client_addr` 属性链接进入，OR 匹配 access_log 的 `remote_addr` / `server_addr` / `upstream_addr` 和 error_log 的 `client_addr` / `server_addr`，按实体返回命中记录数（每条记录最多计数一次）

详情页与 IP 命中调查页不在菜单中，均通过日志列表的属性链接（`link_template`）跳转进入。

## 菜单项

| 菜单 | 类型 | 路由 |
| --- | --- | --- |
| Nginx日志监控 | LOW_CODE_PAGE | `com.coolxer.plugin.nginx` |

## 使用说明

1. 在生产 Nginx 上按 [`nginx-log-config.md`](nginx-log-config.md) 配置 JSON 格式访问日志和标准错误日志，并添加 Syslog 转发（UDP 514 → vectum）
2. 确保 Nginx 所在网络可达 Syslog 采集端（`zenvis-service-vectum:514/UDP`）
3. 安装插件到 Zenvis 平台
4. 在推送任务页面启用「Nginx日志Syslog采集」和「Nginx日志解析入库」
5. 从菜单进入「Nginx日志监控」页面验证数据
