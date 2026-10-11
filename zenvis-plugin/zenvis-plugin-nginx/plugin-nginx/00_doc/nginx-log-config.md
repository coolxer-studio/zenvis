# Nginx 日志配置指南

本文档指导您在生产环境 Nginx 中配置访问日志（access_log）和错误日志（error_log），使其输出为 Zenvis 插件可采集的格式。

> **采集方式**：Zenvis 插件通过 Vector Syslog 服务（UDP 514）接收 Nginx 转发的日志。Nginx 需在 `access_log` / `error_log` 中额外配置 `syslog:` 输出（文件输出可保留作本地留存），具体见下文示例。

## 一、日志目录规划

建议将 Nginx 日志统一存放到一个固定目录（本地留存与故障排查用；采集经由 Syslog 完成）：

```bash
# 创建日志目录
mkdir -p /var/log/nginx

# 确保 Nginx worker 进程有写权限
chown -R nginx:nginx /var/log/nginx
```

## 二、Access Log 配置

### 2.1 JSON 格式日志（推荐）

在 Nginx 配置文件中定义 JSON 格式的访问日志，便于 Vector 精确解析：

```nginx
# /etc/nginx/nginx.conf

http {
    # 定义 JSON 格式的 access_log
    log_format json_access escape=json '{'
        '"remote_addr":"$remote_addr",'
        '"time_local":"$time_local",'
        '"method":"$request_method",'
        '"uri":"$request_uri",'
        '"protocol":"$server_protocol",'
        '"status":$status,'
        '"body_bytes_sent":$body_bytes_sent,'
        '"http_referer":"$http_referer",'
        '"http_user_agent":"$http_user_agent",'
        '"request_time":$request_time,'
        '"upstream_addr":"$upstream_addr",'
        '"upstream_response_time":"$upstream_response_time",'
        '"request_line":"$request",'
        '"server_addr":"$server_addr",'
        '"server_name":"$server_name"'
    '}';

    access_log /var/log/nginx/access.log json_access;
    # 同时转发到 Zenvis Syslog 采集端（UDP 514）
    access_log syslog:server=<vectum-host>:514,facility=local6,tag=nginx_access json_access;
    # ...
}
```

### 2.2 字段说明

| 字段 | 来源变量 | 说明 |
|------|----------|------|
| `remote_addr` | `$remote_addr` | 客户端 IP 地址 |
| `time_local` | `$time_local` | 请求时间（本地时间，格式 `dd/MMM/yyyy:HH:mm:ss +zzzz`） |
| `method` | `$request_method` | HTTP 方法（GET、POST 等） |
| `uri` | `$request_uri` | 请求 URI（含查询参数） |
| `protocol` | `$server_protocol` | HTTP 协议版本 |
| `status` | `$status` | HTTP 响应状态码 |
| `body_bytes_sent` | `$body_bytes_sent` | 响应主体字节数 |
| `http_referer` | `$http_referer` | Referer 头 |
| `http_user_agent` | `$http_user_agent` | User-Agent 头 |
| `request_time` | `$request_time` | 请求处理总时长（秒） |
| `upstream_addr` | `$upstream_addr` | 上游服务器地址 |
| `upstream_response_time` | `$upstream_response_time` | 上游响应时长（秒） |
| `request_line` | `$request` | 完整请求行 |
| `server_addr` | `$server_addr` | 服务器接收请求的地址 |
| `server_name` | `$server_name` | 匹配的 server_name |

### 2.3 日志轮转

配置 logrotate 防止日志文件过大：

```
# /etc/logrotate.d/nginx
/var/log/nginx/access.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    missingok
    sharedscripts
    postrotate
        [ -f /var/run/nginx.pid ] && kill -USR1 `cat /var/run/nginx.pid`
    endscript
}
```

## 三、Error Log 配置

Nginx 的 error_log 使用内置格式，无需额外定义 log_format：

```nginx
# /etc/nginx/nginx.conf

# 全局错误日志（main 上下文）
error_log /var/log/nginx/error.log warn;
# 同时转发到 Zenvis Syslog 采集端（UDP 514）
error_log syslog:server=<vectum-host>:514,facility=local6,tag=nginx_error,severity=warn;

http {
    # 也可以在 http/server/location 上下文覆盖
    # error_log /var/log/nginx/error.log warn;
    # ...
}
```

### 3.1 错误日志级别

Nginx error_log 支持以下日志级别（从低到高）：

| 级别 | 说明 |
|------|------|
| `debug` | 调试信息（生产环境不推荐，量极大） |
| `info` | 一般信息 |
| `notice` | 通知信息 |
| `warn` | 警告信息（推荐生产环境最低级别） |
| `error` | 错误信息 |
| `crit` | 严重错误 |
| `alert` | 警报 |
| `emerg` | 紧急 |

### 3.2 错误日志格式

Nginx error_log 的默认格式为：

```
2026/10/10 12:34:56 [error] 1234#5678: *6789 client 10.0.0.1 ... message
```

各部分含义：
- `2026/10/10 12:34:56` — 日期时间
- `[error]` — 日志级别
- `1234` — worker 进程 PID
- `5678` — 线程 ID
- `*6789` — 连接 ID（以 `*` 开头，部分消息可能不包含）
- 后续为消息正文（可能包含客户端 IP、请求路径等上下文信息）

## 四、Syslog 前提条件

Nginx 通过 UDP 514 将日志发送到 Zenvis 的 Vector Syslog 采集端（vectum 容器），需确保：

1. Nginx 所在主机可访问 `<vectum-host>:514/UDP`（同 Docker 网络时直接使用容器名 `zenvis-service-vectum`）。
2. 中间防火墙/安全组放行 UDP 514。
3. Vector Syslog 服务已启动（插件推送任务「Nginx日志Syslog采集」启用后生效）。514 为特权端口，要求 vectum 容器以 root 运行或具备 `CAP_NET_BIND_SERVICE`。

## 五、环境变量

日志采集已改为 Syslog 推送，不再需要日志文件路径类环境变量。推送任务的 Kafka、ClickHouse 等环境变量见插件 [README](../README.md)。

关键参数在 Nginx 侧：

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `server` | `zenvis-service-vectum:514` | Syslog 采集端地址（UDP） |
| `facility` | `local6` | Syslog facility |
| `tag`（access） | `nginx_access` | 采集端按此 tag 路由到 access 队列 |
| `tag`（error） | `nginx_error` | 采集端按此 tag 路由到 error 队列 |

### 5.1 多 Nginx 实例

多台 Nginx（或多个虚拟主机）可同时向同一 Syslog 采集端发送日志，tag 保持 `nginx_access` / `nginx_error` 不变即可；各实例的主机名记录在 Syslog 报文的 hostname 字段（`.hostname`），可用于区分来源。

## 六、完整 Nginx 配置示例

```nginx
# /etc/nginx/nginx.conf

user nginx;
worker_processes auto;
error_log /var/log/nginx/error.log warn;
error_log syslog:server=zenvis-service-vectum:514,facility=local6,tag=nginx_error,severity=warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    # JSON 格式访问日志
    log_format json_access escape=json '{'
        '"remote_addr":"$remote_addr",'
        '"time_local":"$time_local",'
        '"method":"$request_method",'
        '"uri":"$request_uri",'
        '"protocol":"$server_protocol",'
        '"status":$status,'
        '"body_bytes_sent":$body_bytes_sent,'
        '"http_referer":"$http_referer",'
        '"http_user_agent":"$http_user_agent",'
        '"request_time":$request_time,'
        '"upstream_addr":"$upstream_addr",'
        '"upstream_response_time":"$upstream_response_time",'
        '"request_line":"$request",'
        '"server_addr":"$server_addr",'
        '"server_name":"$server_name"'
    '}';

    access_log /var/log/nginx/access.log json_access;
    access_log syslog:server=zenvis-service-vectum:514,facility=local6,tag=nginx_access json_access;

    sendfile        on;
    keepalive_timeout 65;

    # 示例 server
    server {
        listen 80;
        server_name localhost;

        location / {
            root /usr/share/nginx/html;
            index index.html index.htm;
        }

        error_page 500 502 503 504 /50x.html;
        location = /50x.html {
            root /usr/share/nginx/html;
        }
    }
}
```

## 七、验证

配置完成后，重启 Nginx 并验证日志输出：

```bash
# 检查配置语法
nginx -t

# 重新加载配置
nginx -s reload

# 发送测试请求
curl http://localhost/

# 检查 access 日志
tail -f /var/log/nginx/access.log

# 检查 error 日志
tail -f /var/log/nginx/error.log

# 确认 Syslog 报文已发出（监听 UDP 514 出向流量）
tcpdump -i any -n udp port 514
```

确认 access.log 每行为合法 JSON，error.log 每行符合 Nginx 标准格式，且 vectum 侧能收到 UDP 514 报文后，即可启动 Zenvis 推送任务开始采集。

## 八、Zenvis Docker 部署下的日志配置

当 zenvis-frontend 以 Docker 容器方式运行时，Nginx 运行在容器内部。Zenvis 的 docker-compose 已预置好以下挂载，无需手动操作：

### 8.1 已完成的配置

| 挂载项 | 容器路径 | 宿主机路径 | 说明 |
|--------|----------|------------|------|
| nginx.conf | `/etc/nginx/nginx.conf:ro` | `deploy/config/zenvis-frontend/nginx.conf` | 主配置，定义 JSON `log_format`，文件日志 + Syslog（UDP 514 → vectum）双写 |
| default.conf | `/etc/nginx/conf.d/default.conf:ro` | `deploy/config/zenvis-frontend/conf.d/default.conf` | server 块配置，自动继承 http 块的 `log_format` |
| 日志目录 | `/var/log/nginx` | `deploy/data/nginx/logs` | 本地文件留存（仅作排查用，采集走 Syslog） |

### 8.2 日志去向

在 Zenvis Docker 部署中，日志有两条去向：

- **采集链路**：Nginx → `syslog:server=zenvis-service-vectum:514`（UDP）→ vectum 内 Vector Syslog source → Kafka → ClickHouse
- **本地留存**：`deploy/data/nginx/logs/access.log`、`deploy/data/nginx/logs/error.log`（文件双写，仅用于排查，不再被 Vector 读取）

### 8.3 日志轮转（Docker 环境）

在 Docker 环境下，建议通过 Docker 的 `json-file` 日志驱动限制日志大小，同时在宿主机层面配置 logrotate：

```
# /etc/logrotate.d/zenvis-nginx
deploy/data/nginx/logs/access.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    missingok
    sharedscripts
    postrotate
        docker exec zenvis-frontend nginx -s reopen 2>/dev/null || true
    endscript
}

deploy/data/nginx/logs/error.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    missingok
    sharedscripts
    postrotate
        docker exec zenvis-frontend nginx -s reopen 2>/dev/null || true
    endscript
}
```

### 8.4 快速验证

```bash
# 进入部署目录
cd deploy

# 确认日志目录存在（首次启动前需创建）
mkdir -p data/nginx/logs

# 启动服务
docker compose up -d zenvis-frontend

# 发送测试请求
curl http://localhost:11000/

# 检查 access 日志（宿主机路径）
tail -f data/nginx/logs/access.log

# 检查 error 日志（宿主机路径）
tail -f data/nginx/logs/error.log
```

