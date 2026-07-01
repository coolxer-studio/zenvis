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
│   └── zenvis-backend/
│       └── application.properties  # 后端配置
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

| 服务 | 镜像 | 端口 | 资源限制 |
| :--- | :--- | :--- | :--- |
| redis | redis:7 | 6379 | 2CPU/2GB |
| redis-stack | redis-stack-server:7.2.0-v18 | 16379 | 2CPU/2GB |
| mysql | mysql:8.0 | 3306 | 2CPU/4GB |
| clickhouse | clickhouse-server | 8123 | 2CPU/4GB |
| zenvis-backend | 自构建 | 11001 | 2CPU/4GB |
| nginx | nginx:alpine | 11000 | 1CPU/512MB |

### 环境变量

创建 `.env` 文件：

```env
# 架构
ARCH=amd64  # 或 arm64

# MySQL
MYSQL_ROOT_PASSWORD=your_secure_password
MYSQL_DATABASE=zenvis
MYSQL_USER=zenvis
MYSQL_PASSWORD=your_secure_password

# Redis
REDIS_PASSWORD=your_secure_password

# 时区
TZ=Asia/Shanghai
```

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

文件：`config/zenvis-backend/application.properties`

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
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

## 前端 Nginx 配置

文件：`deploy/zenvis-frontend/conf.d/default.conf`

```nginx
server {
    listen 80;
    server_name localhost;

    root /usr/share/nginx/html;
    index index.html;

    # 前端静态资源
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 代理
    location /api/ {
        proxy_pass http://zenvis-backend-service:11001/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # Swagger 代理
    location /swagger-ui/ {
        proxy_pass http://zenvis-backend-service:11001/swagger-ui/;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

## 生产环境优化

### JVM 调优

```bash
java -jar zenvis-backend.jar \
  -Xms2g \
  -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Djava.security.egd=file:/dev/./urandom
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
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
management.metrics.export.prometheus.enabled=true
```

### 健康检查

```yaml
# docker-compose.yml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:11001/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

## 安全配置

### CORS 配置

```properties
# 允许的源
cors.allowed-origins=http://localhost:11000,https://your-domain.com

# 允许的方法
cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS

# 允许的头
cors.allowed-headers=*
```

### API 认证

ZenVis 使用 Token 认证，登录接口：

```bash
POST /api/v1/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin"
}
```

返回 Token 后，在后续请求中携带：

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

- [开发指南](development.md)
- [AI智能分析](ai-analysis.md)
- [API参考](api-reference.md)
