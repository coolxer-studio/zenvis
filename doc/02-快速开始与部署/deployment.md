# 部署配置

本文档介绍 ZenVis 生产环境部署的配置细节。

## 配置文件结构

```
deploy/
├── docker-compose.yml           # Docker Compose 编排
├── config/
│   ├── mysql/
│   │   ├── my.cnf             # MySQL配置
│   │   └── init.sql           # 初始化SQL
│   ├── redis/
│   │   └── redis.conf         # Redis配置
│   ├── redis-stack/
│   │   └── redis-stack.conf   # Redis Stack配置
│   ├── zenvis-backend/config/
│   │   └── application.properties  # 后端配置
│   └── zenvis-frontend/conf.d/
│       └── default.conf            # 前端 Nginx 配置
├── data/                       # 数据持久化
├── open_config/                # 开放配置
│   ├── web_config/            # Web前端配置
│   ├── plugin_config/         # 插件配置
│   ├── menu_config/           # 菜单配置
│   └── ...
└── .env                       # 环境变量
```

## Docker Compose 配置

### 服务组件

| 服务 | 当前 Compose 镜像版本 | 宿主端口 | 资源上限 |
| :--- | :--- | :--- | :--- |
| kafka-service | Kafka 4.2.0 | 9094 | 2CPU/4GB |
| redis | redis:7 | 6379 | 2CPU/2GB |
| redis-stack | redis-stack-server:7.2.0-v18 | 16379 | 2CPU/2GB |
| mysql | MySQL 8.4 | 3306 | 2CPU/4GB |
| clickhouse | ClickHouse 25.9 | 8123、9000、9009 | 4CPU/8GB |
| zenvis-backend | 当前 ZenVis 镜像 | 11001 | 4CPU/8GB |
| zenvis-frontend | 当前 ZenVis 镜像 | 11000 | 1CPU/1GB |
| vectum-service | 当前 Vectum 镜像 | 11002 | 2CPU/4GB |

### 环境变量

部署目录已有 `.env` 模板。至少按目标环境设置架构，并替换 Compose、后端配置和数据库初始化配置中的所有示例凭据：

```env
# 架构
ARCH=amd64  # 或 arm64

# 时区
TZ=Asia/Shanghai
```

当前 Compose 并非所有数据库配置都由 `.env` 自动接线，不能只修改一个文件。生产部署应把数据库、Redis、普通 API、MCP、Vectum 和模型凭据改为环境变量或密钥挂载，并确认容器端与应用端取值一致。

## 数据库配置

### MySQL 配置

文件：`config/mysql/my.cnf`

```ini
[mysqld]
default_authentication_plugin=mysql_native_password
max_connections=500
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
innodb_buffer_pool_size=1G
```

### ClickHouse 配置

```xml
<!-- clickhouse-init.sql -->
CREATE DATABASE IF NOT EXISTS zenvis;
```

### Redis 配置

文件：`config/redis/redis.conf`

```conf
bind 0.0.0.0
protected-mode no
tcp-backlog 511
timeout 0
tcp-keepalive 300
daemonize no
loglevel notice
databases 16
save 900 1
save 300 10
save 60 10000
```

## 后端配置

文件：`config/zenvis-backend/config/application.properties`

```properties
# 应用配置
server.port=11001
server.servlet.context-path=/

# MySQL 数据源
spring.datasource.mysql.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.mysql.url=jdbc:mysql://mysql-service:3306/zenvis?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.mysql.username=zenvis
spring.datasource.mysql.password=${MYSQL_PASSWORD}
spring.datasource.mysql.hikari.maximum-pool-size=20
spring.datasource.mysql.hikari.minimum-idle=5

# ClickHouse 数据源
spring.datasource.clickhouse.driver-class-name=com.clickhouse.jdbc.ClickHouseDriver
spring.datasource.clickhouse.url=jdbc:clickhouse://clickhouse-service:8123/zenvis
spring.datasource.clickhouse.username=default
spring.datasource.clickhouse.password=
spring.datasource.clickhouse.hikari.maximum-pool-size=20

# Redis 配置
spring.data.redis.host=redis-service
spring.data.redis.port=6379
spring.data.redis.password=${REDIS_PASSWORD}
spring.data.redis.timeout=3000ms

# Redis Vector 配置
spring.data.redis.vector-host=redis-stack-service
spring.data.redis.vector-port=6379

# Spring AI 配置
spring.ai.openai.base-url=${OPENAI_BASE_URL:}
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=${OPENAI_CHAT_MODEL}
spring.ai.openai.embedding.options.model=${OPENAI_EMBEDDING_MODEL}

# 日志配置
logging.level.root=INFO
logging.level.com.coolxer=DEBUG
logging.file.name=/var/log/zenvis/zenvis.log
logging.file.max-size=100MB
logging.file.max-history=30

# 文件上传
spring.servlet.multipart.max-file-size=300MB
spring.servlet.multipart.max-request-size=300MB
```

## 前端 Nginx 配置

文件：`config/zenvis-frontend/conf.d/default.conf`

```nginx
server {
    listen 11000;
    server_name localhost;

    root /usr/share/nginx/html;
    index index.html;

    # 前端静态资源
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 与后端静态资源代理
    location /zenvis/ {
        rewrite ^/zenvis(.*)$ $1 break;
        proxy_pass http://zenvis-backend:11001;
    }
}
```

## 前端 API 前缀约定

当前前端环境变量 `VITE_BASE_URL=/zenvis`。浏览器请求 `/zenvis/api/...`、`/zenvis/system-files/...` 或 `/zenvis/html-page/...`；Vite 与生产 Nginx 统一去除 `/zenvis` 后转发到后端。Controller 的源码路径仍是 `/api/v1/...`。

如果后续需要部署到子路径，应同步调整前端 `VITE_BASE_URL`、Vite/Nginx 代理规则、AMIS 页面 `baseUrl` 参数以及开放配置中的接口地址，避免出现普通页面可访问但低代码页面请求旧路径的问题。

## 生产环境优化

### JVM 调优

```bash
java -Xms2g \
  -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Djava.security.egd=file:/dev/./urandom \
  -jar zenvis-backend.jar
```

### 数据库连接池

```properties
# HikariCP 优化
spring.datasource.mysql.hikari.connection-timeout=30000
spring.datasource.mysql.hikari.idle-timeout=600000
spring.datasource.mysql.hikari.max-lifetime=1800000
spring.datasource.mysql.hikari.maximum-pool-size=30
spring.datasource.mysql.hikari.minimum-idle=10
```

### Redis 优化

```conf
# redis.conf
maxmemory 2gb
maxmemory-policy allkeys-lru
appendonly yes
appendfsync everysec
```

## 监控配置

### Actuator 端点

```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```

### 健康检查

```yaml
# docker-compose.yml 中的实际后端检查
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:11001/api/v1/system/about/info"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

## 安全配置

### 同源与跨域

仓库当前没有 `cors.allowed-*` 这一组应用配置。默认部署通过 `/zenvis` 反向代理保持同源；如必须跨域，应在受控网关或后端明确增加并验证 CORS 策略，不要仅写入未被代码读取的属性。

### API 认证

浏览器使用 Session/Cookie 登录，登录接口为：

```bash
POST /api/v1/system/login/sign-in
Content-Type: application/json

{
  "user_name": "user@example.com",
  "password": "<RSA 加密后的密码>",
  "auth_code": "<验证码>"
}
```

第三方系统可另行配置普通 REST Bearer Token，在后续请求中携带：

```bash
Authorization: Bearer <token>
```

## 备份策略

### 数据库备份

```bash
# MySQL 备份
mysqldump -h localhost -u root -p zenvis > backup_$(date +%Y%m%d).sql

# ClickHouse 备份
clickhouse-backup create --database zenvis
```

### 配置备份

定期备份 `deploy/open_config/` 目录：

```bash
tar -czf open_config_backup_$(date +%Y%m%d).tar.gz open_config/
```

## 下一步

- [开发指南](../04-开发指南/development.md)
- [MCP Client 与业务 Agent 设计](../07-AI与数据智能/MCP-Client-Agent-Design.md)
- [API参考](../05-API参考/api-reference.md)
