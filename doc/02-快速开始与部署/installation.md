# 安装部署

本文档详细介绍 ZenVis 的安装部署流程。

## 环境要求

### 硬件要求

| 资源 | 最低配置 | 推荐配置 |
| :--- | :--- | :--- |
| CPU | 2核 | 4核+ |
| 内存 | 4GB | 8GB+ |
| 磁盘 | 20GB | 50GB+ SSD |

### 软件要求

| 软件 | 版本要求 |
| :--- | :--- |
| Docker | 20.10+ |
| Docker Compose | 2.0+ |
| 系统 | Linux/macOS/Windows |

## 部署模式

ZenVis 支持两种部署模式：

1. **Docker Compose 部署（推荐）** - 适用于开发测试环境
2. **手动部署** - 适用于生产环境定制部署

## Docker Compose 部署

### 1. 准备部署目录

```bash
cd zenvis-backend/deploy
```

### 2. 配置文件说明

```
deploy/
├── docker-compose.yml      # 主编排文件
├── config/
│   ├── mysql/             # MySQL 配置与初始化 SQL
│   │   ├── my.cnf
│   │   └── init.sql
│   ├── redis/             # Redis 配置
│   │   └── redis.conf
│   ├── redis-stack/       # Redis Stack 配置
│   ├── zenvis-backend/config/application.properties
│   └── zenvis-frontend/conf.d/default.conf
├── data/                  # 数据持久化目录
│   ├── mysql/
│   ├── redis/
│   ├── clickhouse/
│   ├── kafka/
│   └── vectum/
└── open_config/          # 开放配置
```

### 3. 启动服务

```bash
# 启动所有服务
docker compose up -d

# 查看服务状态
docker compose ps

# 查看服务日志
docker compose logs -f
```

### 4. 服务端口

| 服务 | 端口 | 说明 |
| :--- | :--- | :--- |
| kafka-service | 9094 | 宿主机 Kafka 入口；容器内使用 9092 |
| redis | 6379 | Redis服务 |
| redis-stack | 16379 | Redis向量存储 |
| mysql | 3306 | MySQL数据库 |
| clickhouse | 8123、9000、9009 | ClickHouse HTTP、Native 与集群通信 |
| zenvis-backend | 11001 | 后端API服务 |
| zenvis-frontend | 11000 | 前端 Web 与 `/zenvis` 反向代理 |
| vectum-service | 11002 | 数据推送任务服务 |

## 手动部署

适用于需要自定义配置的生产环境。

### 1. 环境准备

#### 安装 Java 17

```bash
# macOS
brew install openjdk@17

# Ubuntu/Debian
sudo apt install openjdk-17-jdk
```

#### 安装 Maven

```bash
# macOS
brew install maven

# Ubuntu/Debian
sudo apt install maven
```

### 2. 数据库部署

#### MySQL

```bash
# 使用Docker启动MySQL
docker run -d \
  --name zenvis-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=zenvis \
  -v /data/zenvis/mysql:/var/lib/mysql \
  mysql:8.4
```

初始化数据库：

```bash
mysql -h localhost -u root -p < deploy/open_config/web_config/init/mysql-init.sql
```

#### ClickHouse

```bash
# 使用Docker启动ClickHouse
docker run -d \
  --name zenvis-clickhouse \
  -p 8123:8123 \
  -p 9000:9000 \
  -v /data/zenvis/clickhouse:/var/lib/clickhouse \
  clickhouse/clickhouse-server
```

初始化数据库：

```bash
cat deploy/open_config/web_config/init/clickhouse-init.sql | docker exec -i zenvis-clickhouse clickhouse-client
```

当前版本不再创建固定的 `zenvis.msg` 业务表，实体表由元数据 `auto_create` 配置按需创建。旧版本升级后如已确认不再需要历史 `msg` 数据，可手工清理：

```sql
DROP TABLE IF EXISTS zenvis.msg;
```

升级过程不会自动执行该语句，避免误删已有数据。

#### Redis

```bash
# 启动Redis
docker run -d \
  --name zenvis-redis \
  -p 6379:6379 \
  -v /data/zenvis/redis:/data \
  redis:7

# 启动Redis Stack（用于向量存储）
docker run -d \
  --name zenvis-redis-stack \
  -p 16379:6379 \
  -v /data/zenvis/redis-stack:/data \
  redis/redis-stack-server:7.2.0-v18
```

### 3. 构建后端

```bash
# 编译项目
mvn clean package -DskipTests

# 或跳过代码混淆（开发环境）
mvn clean package -DskipTests -Dproguard.skip=true
```

### 4. 配置后端

编辑 `deploy/config/zenvis-backend/config/application.properties`。以下仅展示配置项形式，凭据应通过环境变量或密钥管理系统注入：

```properties
# 数据库配置
spring.datasource.mysql.url=jdbc:mysql://localhost:3306/zenvis?useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.mysql.username=root
spring.datasource.mysql.password=zenvis

spring.datasource.clickhouse.url=jdbc:clickhouse://localhost:8123/default
spring.datasource.clickhouse.username=default
spring.datasource.clickhouse.password=

# Redis配置
spring.data.redis.host=localhost
spring.data.redis.port=6379

# AI配置（OpenAI）
spring.ai.openai.base-url=${OPENAI_BASE_URL:}
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=${OPENAI_CHAT_MODEL}
spring.ai.openai.embedding.options.model=${OPENAI_EMBEDDING_MODEL}
```

### 5. 启动后端

```bash
java -jar target/zenvis-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=main
```

## 防火墙配置

如果部署在云服务器，需开放以下端口：

```bash
# Ubuntu/Debian
sudo ufw allow 11000/tcp  # 前端
sudo ufw allow 11001/tcp  # 后端API
sudo ufw allow 3306/tcp   # MySQL
sudo ufw allow 6379/tcp   # Redis
sudo ufw allow 8123/tcp   # ClickHouse
```

## 验证部署

### 健康检查

```bash
curl http://localhost:11001/actuator/health
curl http://localhost:11001/api/v1/system/about/info
```

Compose 的后端容器实际使用系统信息接口判活；Actuator 端点用于诊断。

### 访问Swagger

打开浏览器访问：http://localhost:11001/swagger-ui/index.html

## 常见问题

### 1. 服务启动失败

检查日志：
```bash
docker compose logs zenvis-backend
```

### 2. 数据库连接失败

确认数据库服务已启动：
```bash
docker compose ps
```

### 3. 内存不足

增加Docker内存限制到4GB以上。

## 下一步

- [系统架构](../03-架构设计/architecture.md)
- [部署配置](deployment.md)
- [开发指南](../04-开发指南/development.md)
