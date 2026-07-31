# `zenvis-backend` 开发对接指南

## 项目定位

`zenvis-backend` 是 ZenVis 平台核心服务，负责：

- 登录认证、用户、角色、菜单和系统配置；
- Meta 加载、ClickHouse 建表、统一检索和统计分析；
- 看板、低代码配置和静态 HTML 配置；
- 插件上传、安装、升级、恢复、卸载和动态 API；
- Vectum 推送任务代理；
- DIH 会话、RAG、业务 Agent、MCP 工具、审批和分析任务；
- 外部业务服务心跳、事件和运行状态管理。

具体业务模型、业务表、业务接口和页面优先通过插件交付。只有可被多个业务复用的平台能力才进入后端核心。

## 上下游关系

- 上游：`deploy/open_config`、平台 MySQL/ClickHouse/Redis/Redis Stack、Kafka、Vectum、模型服务和已安装插件。
- 下游：`zenvis-frontend`、业务服务 Starter、插件动态 API、MCP 客户端及其他 `/api/v1/**` 调用方。
- 契约边界：后端定义统一响应、Meta、Retrieval、插件生命周期和公开上报接口；跨项目修改必须同步前端类型、部署配置、插件契约或 Starter 文档。

## 技术基线与依赖

| 分类 | 当前基线 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.2.0 |
| Spring AI | 1.1.0-M4 |
| 构建 | Maven 3.8+ |
| 关系数据 | MySQL、Spring Data JPA |
| 分析数据 | ClickHouse |
| Session/缓存 | Redis |
| RAG | Redis Stack |
| 数据接入 | Kafka、Vectum |
| API 文档 | SpringDoc OpenAPI 2.3.0 |
| 测试 | Spring Boot Test、JUnit 5、H2 和领域测试替身 |

版本以 `pom.xml` 为准。后端可以在未配置模型服务时启动，但调用 AI 功能会失败；数据推送功能依赖 Vectum 和 Kafka。

## 目录职责

```text
zenvis-backend/
├── pom.xml
├── Dockerfile
├── build.sh
├── src/main/java/com/coolxer/
│   ├── Application.java
│   ├── aop/                         # 权限、日志和异常处理
│   ├── commons/                     # 常量、枚举和公共异常
│   ├── component/                   # 初始化与通用组件
│   ├── configuration/               # 数据源、AI、MCP 和动态扩展
│   ├── controller/
│   │   ├── config/
│   │   ├── dashboard/
│   │   ├── dih/
│   │   ├── retrieval/
│   │   └── system/
│   ├── dao/
│   │   ├── clickhouse/
│   │   └── mysql/
│   │       ├── entity/
│   │       └── repository/
│   ├── model/                       # DTO、VO、Meta 和查询模型
│   ├── service/
│   │   ├── config/
│   │   ├── core/
│   │   ├── dashboard/
│   │   ├── dih/
│   │   ├── retrieval/
│   │   └── system/
│   └── utils/
├── src/main/resources/
│   ├── application.properties
│   ├── application-dev.properties
│   ├── application-prod.properties
│   ├── application-saas.properties
│   └── demo/
└── src/test/
```

Controller 负责 HTTP 契约和参数校验，Service 负责业务规则、权限边界和事务，Repository/查询引擎负责持久化。不要让 Controller 直接拼接查询或访问数据库。

## 开发环境

### 环境要求

- JDK 17；
- Maven 3.8 或更高；
- Docker 与 Docker Compose；
- IntelliJ IDEA 或支持 Java 17 的 IDE。

### 启动基础服务

在项目根目录运行：

```bash
docker compose -f deploy/docker-compose.yml up -d \
  redis-service redis-stack-service mysql-service \
  clickhouse-service kafka-service vectum-service
```

如果只开发不涉及 RAG 或推送任务的功能，可以按实际依赖减少服务，但完整测试仍应覆盖相应组件。

### IDE 配置

- Maven 项目目录：`zenvis-backend`；
- Main class：`com.coolxer.Application`；
- Project SDK：JDK 17；
- Working directory：`zenvis-backend`；
- Spring Profile：本地通常使用 `dev`。

## 配置与 Profile

`application.properties` 当前默认激活 `dev`。项目提供 `dev`、`prod`、`saas` 三套 Spring 配置，容器部署使用外部 `application.properties`。

常用配置键：

| 配置 | 作用 |
| --- | --- |
| `server.port` | 后端端口，当前为 `11001` |
| `spring.datasource.mysql.jdbc-url` | MySQL JDBC 地址 |
| `spring.datasource.clickhouse.jdbc-url` | ClickHouse JDBC 地址 |
| `spring.data.redis.*` | Redis 连接 |
| `spring.ai.vectorstore.redis.*` | Redis Stack 向量存储 |
| `spring.ai.openai.*` | OpenAI 兼容模型服务 |
| `app.services.data.url` | Vectum 服务地址 |
| `app.paths.config.base` | 开放配置根路径 |
| `app.paths.plugins` | 插件展开目录 |
| `app.security.api.*` | 普通 REST API Bearer Token |
| `app.security.mcp.*` | MCP Server Bearer Token |

本地开发使用环境变量或忽略提交的 `config/local-secrets.properties` 注入密码和令牌。不要直接把部署文件覆盖到 `application-dev.properties`：容器服务名、挂载路径和宿主机地址并不相同。

## 常用命令

```bash
cd zenvis-backend

# 编译
mvn clean compile

# 全量测试
mvn test

# 本地运行
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 打包，产物为 target/application.jar
mvn clean package -DskipTests

# 运行 Jar
java -jar target/application.jar --spring.profiles.active=dev
```

开发态可使用 DevTools：

```bash
mvn spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.fork=false
```

镜像构建脚本会执行 Maven 打包并按宿主机架构构建镜像：

```bash
./build.sh
PUSH_IMAGE=true ./build.sh
```

只有明确需要发布镜像时才设置 `PUSH_IMAGE=true`。

## HTTP 与模型契约

### 统一响应

常规业务接口返回 `ResponseWrap<T>`：

```java
@RestController
@RequestMapping("/api/v1/example")
public class ExampleController {

    private final ExampleService exampleService;

    public ExampleController(ExampleService exampleService) {
        this.exampleService = exampleService;
    }

    @PostMapping("/list")
    public ResponseWrap<List<ExampleVo>> list(@Valid @RequestBody ExampleQueryDto query) {
        return ResponseWrap.success(exampleService.list(query));
    }
}
```

成功响应的 `status` 为 `0`。JSON 使用 `snake_case`，DTO/VO 使用明确类型，不在 Controller 中返回临时 Map 代替稳定接口模型。

### API 路径

- 后端 Controller：`/api/v1/**`；
- 前端浏览器代理：`/zenvis/api/v1/**`，代理时移除 `/zenvis`；
- 动态插件 API：`/api/v1/plugin/{package_name}/**`；
- Swagger：`http://localhost:11001/swagger-ui/index.html`；
- MCP Server：`/sse` 与 `/mcp/message`。

公开业务服务上报接口不要求 Session 或 Bearer Token，但生产环境必须在部署层限制来源、请求规模和调用频率。

## 核心开发路径

### 系统与配置

用户、角色、菜单、插件、看板、推送任务和业务服务位于 `controller/system` 与 `service/system`。修改权限或系统配置时同时检查：

- `AuthorityInterceptor`；
- 角色菜单树和超级管理员行为；
- 配置重载与插件生命周期；
- 审计日志和敏感字段脱敏。

### Retrieval

Retrieval 由 Meta、规则兼容、受限表达式、查询引擎和前端状态共同组成，不接受任意 SQL。修改前阅读[数据与检索架构](/06-架构设计/数据与检索架构.md)。

重点测试：

```bash
mvn -Dtest='WhereExpressionParserTest,RetrievalRuleLifecycleTest,RetrievalRuleServiceImplTest,MetaDataServiceImplTest,QueryEngineImplTest,RetrievalControllerTest,LogAopAspectTest' test
```

新增字段能力时需要同时验证：

- Meta 模型和校验；
- 逻辑字段名与物理列名映射；
- 操作符、类型转换和排序；
- 规则创建、加载、失效和更新；
- MCP retrieval 工具 Schema；
- 前端查询、表格和自动补全。

### DIH、Agent 与 MCP

- 普通 `ask` 不加载 Skill，也不调用 MCP 或本地工具。
- 业务 Agent 通过 `PromptDrivenAgentRuntime` 执行，通过 `AgentMcpToolService` 获取工具白名单。
- 数据可视化 Agent 只使用只读 retrieval MCP 工具，不生成或执行任意 SQL。
- 写工具必须保留审批、审计、参数校验和风险策略。
- RAG 只服务普通问答和深度问答；业务 Agent 使用显式绑定的 Skill。

修改流式协议时必须保持文本、审批、完成、错误和富消息事件兼容，并同步前端类型和渲染逻辑。

### 插件生命周期

插件安装器负责按目录导入 Meta、推送任务、动态 API、UI、看板、MCP、Skill 和菜单。平台通用契约见[插件开发与集成](/03-插件开发与集成/README.md)。

修改插件能力时重点检查：

- `PluginServiceImpl` 的安装、升级、恢复和回滚；
- Meta 跨文件合并与冲突校验；
- 动态 Jar 的注册、重载和类加载隔离；
- MySQL 迁移版本和 SHA-256；
- 配置索引、静态文件路径和卸载清理；
- 旧插件兼容与测试归档。

## 扩展点

- 平台能力：在现有 Controller、Service、DTO/VO 和 Repository 分层内增加可复用能力，并保持 `/api/v1/**` 与 `ResponseWrap<T>` 契约。
- 数据能力：通过 Meta、Retrieval 查询模型、MCP 工具 Schema 和相应领域测试扩展，不开放任意 SQL。
- 插件能力：通过插件目录、动态 API、迁移、配置索引和生命周期钩子扩展，业务专属代码不直接并入核心。
- AI 能力：通过 Skill、Agent、MCP 工具白名单和审批策略扩展，不能绕过权限、审计或确认步骤。

## 数据库变更

### 平台表

平台 MySQL Entity 位于 `dao/mysql/entity`，Repository 位于 `dao/mysql/repository`。修改平台表前确认：

- 多环境已有数据兼容；
- 唯一索引、长度和时区；
- Hibernate `ddl-auto` 行为；
- 服务层事务和测试数据；
- 是否需要显式迁移方案。

### 插件表

插件业务表不要加入核心 Entity 扫描。插件 MySQL 迁移放在：

```text
plugin-custom/
└── 03_api/
    └── migrations/mysql/
        └── V001__init_schema.sql
```

已执行迁移不得修改；后续变更增加更高版本。插件卸载不删除业务表和迁移历史。ClickHouse 结构由 Meta `auto_create` 与实际部署共同决定。

## 测试策略

`src/test` 覆盖权限、日志、配置、插件、Retrieval、DIH、RAG、MCP、业务服务和动态扩展。开发时至少运行受影响领域测试，提交前运行：

```bash
mvn test
```

需要真实 MySQL、ClickHouse、Redis、Kafka、Vectum 或模型服务的场景，应单独标注为集成验证，不能用单元测试通过代替真实链路结论。

## 常见问题与排障

### 端口占用

```bash
lsof -i :11001
```

### 日志

开发运行优先查看控制台；文件日志位于 `logs/`。不要提交运行日志。

### 数据库连接失败

1. 运行 `docker compose -f ../deploy/docker-compose.yml ps`。
2. 核对 `jdbc-url` 使用宿主机地址还是容器服务名。
3. 核对密码环境变量与数据库实际配置。
4. 对 ClickHouse 单独执行 `SELECT 1`。

### 插件加载失败

检查归档根目录、`index.json`、唯一包名、动态 API Jar 数量、迁移校验和及后端插件日志。

### AI 页面可用但问答失败

检查模型 base URL、API key、模型名、HTTP 协议兼容、Redis Stack 和工具策略。模型服务未配置不影响后端启动。

## 交付检查

- `mvn test` 通过，或明确记录与本次改动无关的既有失败；
- Controller、DTO/VO、Service 和 Repository 职责清晰；
- `/api/v1`、`snake_case` 和 `ResponseWrap` 契约未被破坏；
- 新配置在开发、容器和文档中一致，且没有提交凭据；
- Retrieval、插件或 AI 改动完成对应领域回归；
- API 或行为变化已同步 [API 参考](/08-API参考/README.md)和相关主题文档；
- 在 `zenvis-backend` 独立工作树检查 `git status` 与 `git diff --check`。

## 关联文档

- [平台架构](/06-架构设计/README.md)
- [数据与检索架构](/06-架构设计/数据与检索架构.md)
- [插件开发与集成](/03-插件开发与集成/README.md)
- [AI 与数据智能](/04-AI与数据智能/README.md)
- [API 参考](/08-API参考/README.md)
- [`deploy` 开发对接指南](/07-开发指南/deploy-开发对接指南.md)
