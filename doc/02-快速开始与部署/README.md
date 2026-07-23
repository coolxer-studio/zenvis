# 快速开始

本主题保留从快速体验到生产运维的完整资料：

- [统一安装部署指南](安装部署.md)
- [原快速入门详细文档](getting-started.md)
- [原安装详细文档](installation.md)
- [原部署配置详细文档](deployment.md)

## 选择运行方式

| 方式 | 适用场景 | 启动内容 |
| --- | --- | --- |
| Docker Compose | 体验、联调、完整环境 | 前后端、MySQL、ClickHouse、Redis、Redis Stack、Kafka、Vectum |
| 本地开发 | 日常研发与调试 | 基础服务使用容器，前后端在本机运行 |

## 环境要求

### 完整容器环境

- Docker Engine 与 Docker Compose v2；
- 为 MySQL、ClickHouse、Kafka 和应用容器预留足够磁盘与内存；
- macOS/Linux 使用 `ARCH` 选择适用镜像架构。

### 本地开发

| 组件 | 要求 |
| --- | --- |
| 后端 | JDK 17、Maven 3.8+ |
| 前端 | Node.js `^20.19.0` 或 `>=22.12.0`、Yarn 1.x |
| 数据库 | MySQL、ClickHouse、Redis；启用 RAG 时还需要 Redis Stack |
| 数据接入 | 需要运行推送任务时使用 Kafka 与 Vectum |

## 使用 Docker Compose 启动

```bash
cd zenvis/deploy
docker compose up -d
docker compose ps
```

主要入口：

| 服务 | 地址 |
| --- | --- |
| Web | `http://localhost:11000` |
| API | `http://localhost:11001` |
| Swagger UI | `http://localhost:11001/swagger-ui/index.html` |
| 后端容器健康检查 | `http://localhost:11001/api/v1/system/about/info` |
| Actuator 诊断 | `http://localhost:11001/actuator/health` |
| Vectum | `http://localhost:11002` |

部署前应复制并调整环境配置，至少设置数据库密码、普通 API Bearer Token、MCP Bearer Token、Vectum Token 和模型服务参数。不要沿用示例默认值部署到生产环境。

查看日志：

```bash
docker compose logs -f zenvis-backend
docker compose logs -f zenvis-frontend
```

停止环境：

```bash
docker compose down
```

`down` 默认不删除挂载的数据目录；执行任何卷或数据清理前应先完成备份。

## 本地开发启动

### 1. 启动基础服务

可以从根目录的 `deploy/docker-compose.yml` 选择启动数据库与中间件：

```bash
cd zenvis/deploy
docker compose up -d mysql-service clickhouse-service redis-service redis-stack-service kafka-service vectum-service
```

### 2. 配置后端

开发环境默认读取 `zenvis-backend/src/main/resources/application-dev.properties`，主要配置包括：

- MySQL：`spring.datasource.mysql.*`；
- ClickHouse：`spring.datasource.clickhouse.*`；
- Redis：`spring.data.redis.*`；
- Vectum：`app.services.data.*`；
- OpenAI 兼容模型：`spring.ai.openai.*`；
- RAG：`app.ai.embedding.enabled` 与 `spring.ai.vectorstore.redis.*`；
- API/MCP Token：`app.security.*`。

通过环境变量覆盖凭据，不要把本地或生产密钥写入文档和提交记录。

### 3. 启动后端

```bash
cd zenvis-backend
mvn test
mvn spring-boot:run
```

后端默认监听 `11001`。验证：

```bash
curl http://localhost:11001/actuator/health
curl http://localhost:11001/api/v1/system/about/info
```

### 4. 启动前端

```bash
cd zenvis-frontend
yarn install
yarn test
yarn server:dev
```

前端开发服务器监听 `8090`。环境变量的当前约定为：

```properties
VITE_BASE_API=http://localhost:11001
VITE_BASE_URL=/zenvis
```

浏览器请求 `/zenvis/api/v1/...`，Vite 在开发环境把 `/zenvis` 代理到后端并移除此前缀。

## 首次验证

1. 打开 Web 登录页并完成初始化账号登录。
2. 进入首页确认系统概览与实体统计可加载。
3. 打开 Swagger，确认 Controller 文档可访问。
4. 进入全局检索，确认实体和字段元数据可以加载。
5. 如启用了模型服务，进入数智中心检查模型列表和健康状态。
6. 如需数据接入，确认 Vectum 与 Kafka 正常，再启用推送任务。

## 常见问题

### 前端请求出现 404

确认 `VITE_BASE_URL=/zenvis`，并检查 Vite 或 Nginx 是否将 `/zenvis/` 重写后代理到后端 `11001`。

### 后端启动但检索不可用

检查 ClickHouse 连接、Meta 配置目录和目标表是否存在。插件安装生成的实体还需要对应推送任务持续写入数据。

### AI 页面可打开但无法回答

检查 OpenAI 兼容地址、API Key、模型名及网络可达性。RAG 不可用会降级为无检索问答，但模型本身不可用时只能返回不可用提示。

### 登录状态无法保持

浏览器模式使用 Session/Cookie，前端请求开启 `withCredentials`。跨域开发时应保证代理、Cookie 域和 SameSite 配置一致。

后续阅读：[安装部署](安装部署.md)、[开发指南](../04-开发指南/README.md)和[使用手册](../01-产品与使用/使用手册/README.md)。
