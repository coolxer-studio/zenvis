# 项目简介

ZenVis Backend 是基于 Spring Boot构建的ZenVis的后端项目，提供仪表盘、数据检索、策略管理、数据集成等核心接口功能模块。

## 快速运行 ZenVis（含前后端） 服务

### 1. Docker Compose 运行（推荐）

```bash
cd zenvis/deploy
docker compose up -d
```

> 默认运行架构由项目根目录 `deploy/.env` 中的 `ARCH` 决定。初始化账号由部署配置创建，首次登录后应立即修改密码；不要在 README 或生产脚本中固化真实凭据。

### 2. 服务访问

| 服务          | 地址                                          |
|:------------|:--------------------------------------------|
| web前端服务     | `http://<ip>:11000`                         |
| API 接口 服务   | `http://<ip>:11001`                         |
| MCP 接口服务    | `http://<ip>:11001/sse`                     |
| Vectum 数据服务 | `http://<ip>:11002`                         |
| Swagger 文档  | `http://<ip>:11001/swagger-ui/index.html`   |

***

## 一、产品定位

ZenVis = **配置化数据存储 + 可视化引擎 + 检索分析 + 插件扩展 + AI 智能分析**

### 解决传统数据分析平台的痛点

| 传统平台痛点        | ZenVis 解决方案         |
| :------------ | :------------------ |
| 硬编码数据模型，变更成本高 | 基于配置定义数据模型，灵活变更     |
| 可视化能力固化，定制困难  | 配置驱动的可视化引擎，支持自定义图表  |
| 分析能力有限，扩展不便   | 插件化扩展机制，按需添加分析能力    |
| 缺乏智能分析能力      | 集成 AI，支持自然语言查询和智能分析 |
| 难以对接第三方工具     | 开放 API，支持嵌入三方 BI 工具 |

***

## 二、核心能力

### 1. 配置化数据接入及存储

- 基于配置文件定义数据模型和存储策略
- 支持多数据源接入（文件日志、消息队列、数据仓库等）
- 灵活的数据接入和管理机制

### 2. 基于配置的可视化展示

- 基于 ECharts 的可视化引擎
- 支持多种图表类型（折线图、柱状图、饼图、地图等）
- 自定义仪表盘配置

### 3. 基于元数据数据分析接口

- 强大的数据检索、聚合与分析能力
- 支持复杂查询和多维度分析
- 实时数据查询与统计

### 4. 应用插件扩展

- 基于数据服务的插件化扩展机制
- 支持动态加载和功能定制
- 可插拔的业务模块设计

### 5. 三方 BI 嵌入

- 开放 API 接口，支持第三方集成
- 可嵌入主流 BI 工具
- 扩展分析能力

### 6. AI 智能分析

- **AI 聊天**: 支持多轮对话的智能问答
- **自然语言数据分析**: 支持自然语言查询数据，自动生成图标展示
- **智能化策略配置**: 基于 AI 模型的策略配置，自动调整系统配置参数

***

## 三、典型使用场景

| 场景       | 描述                 |
| :------- | :----------------- |
| 事件数据分析   | 事件检索、关联分析、异常识别       |
| 指标趋势分析   | 时序指标、性能监控、趋势追踪       |
| IoT 数据处理 | 设备数据采集、实时监控、趋势分析   |
| 日志分析     | 日志聚合、异常检测、合规审计     |
| API 数据分析 | 接口调用统计、性能分析、错误追踪   |
| AI 智能问答  | 自然语言查询数据、自动生成报表    |

***

## 四、系统架构

整体拓扑、模块边界和数据流见[系统架构](../doc/03-架构设计/README.md)。本模块负责后端 API、权限、检索、插件生命周期、DIH/MCP 和业务服务管理。

### 架构分层

#### 1. 应用层

- **数据插件**：可插拔的业务模块，按需加载
- **三方 BI**：嵌入第三方 BI 工具，扩展分析能力
- **自定义应用**：基于框架构建的定制化应用
- **管理控制台**：系统管理和配置界面

#### 2. 服务层

| 模块      | 核心能力            |
| :------ | :-------------- |
| 检索引擎    | 数据检索、过滤、排序      |
| 聚合服务    | 数据聚合、统计、分析      |
| 配置服务    | 配置管理、动态配置       |
| AI 智能分析 | RAG、MCP 工具调用、智能问答 |

#### 3. 数据层

| 数据库        | 用途                      |
| :--------- | :---------------------- |
| MySQL      | 存储业务数据（用户、角色、菜单、配置等）    |
| ClickHouse | 存储插件定义的时序数据、事件数据和分析指标 |
| Redis      | Session、缓存和运行状态 |
| Redis Stack | 插件文档向量索引与 RAG |

***

## 五、快速上手

### 1. 技术栈

| 分类      | 技术                  | 版本       |
| :------ | :------------------ | :------- |
| 语言      | Java                | 17       |
| 框架      | Spring Boot         | 3.2.0    |
| AI 框架   | Spring AI           | 1.1.0-M4 |
| AI 服务   | OpenAI              | -        |
| 关系型数据库  | MySQL               | 当前 Compose：8.4 |
| 分析数据库   | ClickHouse          | 当前 Compose：25.9 |
| 缓存       | Redis               | 当前 Compose：7 |
| 向量存储    | Redis Stack         | 当前 Compose：7.2.0-v18 |
| 消息队列    | Kafka               | 当前 Compose：4.2.0 |
| ORM     | Spring Data JPA     | -        |
| API 文档  | SpringDoc OpenAPI   | 2.3.0    |
| 构建工具    | Maven               | 3.8+     |
| 容器化     | Docker              | -        |

### 2. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL、ClickHouse、Redis；启用 RAG 时还需要 Redis Stack
- 运行数据接入任务时还需要 Kafka 与 Vectum

### 3. 启动方式

#### 开发态运行

```bash
cd zenvis-backend

# 编译项目
mvn clean compile

# 运行项目（开发环境）
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

> **注意**
> 当前项目仅为后端服务，不包含前端界面。
> 前端运行参考前端仓库：[zenvis-frontend](https://gitee.com/coolxer-studio/zenvis-frontend)

### 4. 构建与部署

#### 打包构建

```bash
# 打包
mvn clean package -DskipTests

# 运行打包后的 Jar
java -jar target/application.jar --spring.config.location=src/main/resources/application.properties
```

#### Docker 构建启动

```bash
# 构建镜像
docker build -t zenvis-backend:latest .

# 运行容器
docker run -d -p 11001:11001 zenvis-backend:latest
```

#### 使用构建脚本

```bash
# 使用构建脚本构建（默认不推送镜像）
./build.sh

# 构建并推送镜像到 Docker Registry
PUSH_IMAGE=true ./build.sh

```

该脚本会自动执行：

1. Maven 打包
2. Docker 镜像构建
3. 镜像推送至阿里云容器仓库（需docker login认证后执行）

***

## 六、配置说明

### 配置文件

项目支持多环境配置：

| Profile | 说明       |
| :------ | :------- |
| `dev`   | 开发环境     |
| `prod`  | 容器/生产环境 |
| `saas`  | SaaS 环境 |

`application.properties` 当前默认激活 `dev`，并可选导入 `./config/local-secrets.properties`。部署时应显式选择目标 Profile 或使用外部 `application.properties`。

### 主要配置项

| 配置项                                      | 默认值         | 说明                    |
| :--------------------------------------- | :---------- | :-------------------- |
| `server.port`                            | `11001`     | 服务端口                  |
| `spring.datasource.mysql.jdbc-url`       | -           | MySQL 连接地址            |
| `spring.datasource.clickhouse.jdbc-url`  | -           | ClickHouse 连接地址       |
| `MYSQL_PASSWORD`                         | -           | MySQL 密码，通过环境变量或本地密钥文件注入 |
| `CLICKHOUSE_PASSWORD`                    | -           | ClickHouse 密码，通过环境变量或本地密钥文件注入 |
| `REDIS_PASSWORD`                         | -           | Redis 密码，通过环境变量或本地密钥文件注入 |
| `spring.data.redis.host`                 | `localhost` | Redis 主机地址            |
| `spring.data.redis.port`                 | `6379`      | Redis 端口              |
| `spring.ai.openai.base-url`              | -           | OpenAI 兼容模型服务地址；未配置不影响启动，调用 AI 功能时报错 |
| `spring.ai.openai.api-key`               | -           | OpenAI API Key           |
| `app.security.api.bearer-token`          | -           | 普通 REST API Bearer Token；配置后 `/api/v1/**` 支持 `Authorization: Bearer <token>` |
| `app.security.api.bearer-user`           | `admin@admin.com` | Bearer Token 调用映射到的系统用户邮箱，用于权限上下文和审计 |
| `app.security.mcp.bearer-token`          | -           | MCP Server Bearer Token，未配置时 MCP 接口返回 401 |
| `spring.servlet.multipart.max-file-size` | `300MB`     | 最大上传文件大小              |
| `server.servlet.session.timeout`         | `3600S`     | 会话超时时间                |

第三方系统调用普通 REST API 时，既可以按前端方式先登录拿 `JSESSIONID`，也可以配置 `API_BEARER_TOKEN` 后直接携带请求头：`Authorization: Bearer <app.security.api.bearer-token>`。Bearer Token 调用会以 `app.security.api.bearer-user` 对应用户身份执行。

MCP 客户端访问 Spring AI MCP Server 接口时需要携带请求头：`Authorization: Bearer <app.security.mcp.bearer-token>`。

本地开发不要把数据库、Redis、LLM、REST API 或 MCP 密钥写入已提交的 profile 配置。可以使用环境变量，也可以在 `zenvis-backend/config/local-secrets.properties` 中放本机密钥；该文件已加入 `.gitignore`，应用启动时会自动读取。例如：

```properties
MYSQL_PASSWORD=your_mysql_password
CLICKHOUSE_PASSWORD=your_clickhouse_password
REDIS_PASSWORD=your_redis_password
OPENAI_BASE_URL=https://your-llm-endpoint
OPENAI_API_KEY=your_api_key
OPENAI_CHAT_MODEL=your_chat_model
API_BEARER_TOKEN=your_api_token
API_BEARER_USER=admin@admin.com
MCP_BEARER_TOKEN=your_mcp_token
```

如果启动时报 `DB::Exception: default: Authentication failed` 且连接地址是 ClickHouse，请先确认 `CLICKHOUSE_PASSWORD` 已按上述方式注入，并且与本机 ClickHouse `default` 用户密码一致。

### 数据库初始化

**MySQL**：执行项目根目录的 `deploy/config/mysql/init.sql` 创建数据库和用户：

```sql
CREATE DATABASE IF NOT EXISTS zenvis CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'zenvis'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON zenvis.* TO 'zenvis'@'%';
FLUSH PRIVILEGES;
```

**ClickHouse**：数据库会自动创建，确保 ClickHouse 服务可用且配置正确。

***

## 七、项目结构

```
zenvis/
├── deploy/                          # 系统部署目录
│   ├── docker-compose.yml
│   ├── config/                      # 各服务配置
│   ├── data/                        # 数据目录
│   └── open_config/                 # ZenVis 开放配置
└── zenvis-backend/
    ├── src/main/java/com/coolxer/
    │   ├── Application.java              # 启动类
    │   ├── aop/                          # 切面处理
    │   │   ├── AuthorityInterceptor.java # 权限拦截器
    │   │   ├── LogAopAspect.java         # 日志切面
    │   │   └── ApiExceptionHandler.java  # 全局异常处理
    │   ├── commons/                      # 公共模块
    │   │   ├── constants/                # 常量定义
    │   │   ├── enums/                    # 枚举类
    │   │   └── exception/                # 异常类
    │   ├── component/                    # Spring 组件
    │   ├── configuration/                # 配置类
    │   ├── controller/                   # REST API 控制器
    │   │   ├── dashboard/                # 仪表盘
    │   │   ├── dih/                      # 深度思考助手
    │   │   ├── policy/                   # 策略配置
    │   │   ├── retrieval/                # 检索引擎
    │   │   └── system/                   # 系统管理
    │   ├── dao/                          # 数据访问层
    │   │   ├── clickhouse/               # ClickHouse 实体和仓库
    │   │   └── mysql/                    # MySQL 实体和仓库
    │   ├── model/                        # 数据模型
    │   ├── service/                      # 业务服务层
    │   └── utils/                        # 工具类
    ├── src/main/resources/               # 资源文件
    ├── doc/                              # 模块 README 与 banner；整体文档在 ../doc
    ├── Dockerfile                        # Docker 配置
    ├── build.sh                          # 构建脚本
    ├── LICENSE                           # 许可证
    ├── CONTRIBUTING.md                   # 贡献指南
    └── README.md                         # 项目文档
```

***

## 八、主要功能模块

### 系统管理

| 功能    | 控制器                   | 说明          |
| :---- | :-------------------- | :---------- |
| 用户管理  | `UserController`      | 用户信息管理、认证授权 |
| 角色管理  | `RoleController`      | 角色定义、权限分配   |
| 菜单管理  | `MenuController`      | 菜单配置、权限控制   |
| 插件管理  | `PluginController`    | 插件上传、安装、升级、恢复与卸载  |
| 推送任务  | `PushTaskController`  | 定时推送任务管理    |
| 仪表盘配置 | `DashboardController` | 仪表盘配置管理     |

### 深度思考助手 (DIH)

| 功能        | 控制器                          | 说明               |
| :-------- | :--------------------------- | :--------------- |
| AI 聊天     | `ChatController`             | 智能聊天接口，支持自然语言查询  |
| 聊天会话管理    | `ChatSessionController`      | 会话生命周期管理         |
| RAG 文档管理 | `VectorStoreQueryController` | 插件文档向量管理和相似度搜索 |
| 数据可视化Agent | `DataVisualizationAgent`     | retrieval MCP 只读可视化分析 |

### 检索引擎

| 功能   | 控制器                     | 说明     |
| :--- | :---------------------- | :----- |
| 实体核心 | `EntityCoreController`  | 实体数据查询 |
| 实体计数 | `EntityCountController` | 实体数量统计 |
| 检索服务 | `RetrievalController`   | 通用检索接口 |

***

## 九、API 接口文档

### 基础路径

所有 API 接口前缀：`/api/v1`

### 接口模块

| 模块   | 基础路径                | 说明        |
| :--- | :------------------ | :-------- |
| 系统管理 | `/api/v1/system`    | 用户、角色、菜单等 |
| 插件接口 | `/api/v1/plugin/{package_name}` | 已安装插件的动态业务接口 |
| DIH  | `/api/v1/dih`       | AI 相关接口   |
| 检索引擎 | `/api/v1/retrieval` | 数据检索接口    |

### 接口访问

启动服务后访问 Swagger UI：

- <http://localhost:11001/swagger-ui/index.html>

***

## 十、优势

| 特性      | 说明                               |
| :------ | :------------------------------- |
| 配置驱动    | 数据模型和业务逻辑通过配置定义，无需硬编码            |
| 多数据源支持  | 原生支持 MySQL、ClickHouse、Redis 多数据源 |
| 插件化扩展   | 业务模块可插拔，灵活扩展功能                   |
| AI 赋能   | 集成大语言模型，支持自然语言查询                 |
| 可视化引擎   | 内置 ECharts，丰富的图表展示能力             |
| 企业级 API | RESTful API 设计，易于集成到业务系统         |
| 一键部署    | Docker / Docker Compose 快速部署     |

***

## 十一、适用人群

- 数据分析工程师
- 安全运维团队
- 业务开发团队
- 需要构建数据分析平台的企业
- DevOps / SRE 团队
- AI 应用开发者

***

## 十二、贡献指南

欢迎提交 Issue 和 Pull Request！\
贡献指南参考 [CONTRIBUTING.md](CONTRIBUTING.md)

***

## 十三、许可证

\[LICENSE]\(Apache 2.0 License)

***

## 十四、联系方式

如有问题或建议，欢迎通过以下方式联系：

- 提交 Issue
- 发送邮件：<coolxer@163.com>

***

**ZenVis** — 让数据更专注于业务
