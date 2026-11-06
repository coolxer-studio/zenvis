# ZenVis Backend 开发规范

## 适用范围与事实来源

本文件适用于 `zenvis-backend/` 目录。根目录 [`AGENTS.md`](../AGENTS.md) 提供项目级通用规则；本文件中的后端细节优先于根规范，但不得覆盖用户明确指令、系统约束或更深层目录的 `AGENTS.md`。

当前代码、`pom.xml`、`application*.properties` 和测试是最终事实来源。`README`、开发指南和本文件可能滞后；发现冲突时先核对实现，再同步修正文档。

## 项目定位与技术基线

`zenvis-backend` 是 ZenVis 平台核心服务，负责登录认证、用户/角色/菜单、系统配置、Meta 加载、ClickHouse 建表、统一检索、看板、低代码配置、插件生命周期、动态 API、Vectum 推送任务代理、DIH/RAG/业务 Agent/MCP、业务服务心跳与事件上报。

具体业务模型、业务表、业务接口和页面优先通过插件交付。只有可被多个业务复用的平台能力才进入后端核心；不得把单个客户或单个插件的业务逻辑写进平台 Controller/Service。

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
| 对象存储 | MinIO |
| 数据接入 | Kafka、Vectum |
| API 文档 | SpringDoc OpenAPI 2.3.0 |
| 测试 | Spring Boot Test、JUnit 5、H2、Mockito 和领域测试替身 |

版本以 `pom.xml` 为准。后端可以在未配置模型服务时启动，但 AI 功能会失败；推送任务依赖 Kafka 与 Vectum。

## 目录与分层

```text
zenvis-backend/
├── pom.xml
├── Dockerfile
├── build.sh
├── src/main/java/com/coolxer/
│   ├── Application.java
│   ├── aop/                    # 权限、日志、全局异常
│   ├── commons/                # 常量、枚举、公共异常
│   ├── component/              # 初始化和通用组件
│   ├── configuration/          # 数据源、AI、MCP、动态扩展配置
│   ├── controller/
│   │   ├── config/
│   │   ├── dashboard/
│   │   ├── dih/
│   │   ├── minio/
│   │   ├── retrieval/
│   │   └── system/
│   ├── dao/
│   │   ├── clickhouse/
│   │   └── mysql/
│   ├── model/                  # DTO、VO、Meta 和查询模型
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
│   └── application-saas.properties
└── src/test/
```

职责边界：

- Controller 只处理 HTTP 契约、参数校验、权限入口和响应转换，不直接拼接查询或访问数据库。
- Service 承载业务规则、权限边界、事务和跨组件编排；接口与实现按现有目录组织。
- Repository/查询引擎负责 MySQL/ClickHouse 持久化；动态插件业务表不得加入核心 Entity 扫描。
- DTO 接收请求，VO 返回前端，领域模型保持独立；不要在 Controller 中用临时 Map 代替稳定接口模型。
- 跨模块能力优先通过已有 Service、MCP 工具和公开契约扩展，不在 Agent 或 Controller 中复制底层访问逻辑。

## 构建、运行与测试

```bash
cd zenvis-backend

# 编译
mvn clean compile

# 全量测试
mvn test

# 定向测试
mvn -Dtest=ExampleServiceTest test

# 本地开发运行，端口 11001，Profile dev
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 打包，产物为 target/application.jar
mvn clean package -DskipTests
java -jar target/application.jar --spring.profiles.active=dev
```

本地依赖可按[后端开发指南](../doc/07-开发指南/zenvis-backend-开发对接指南.md)启动 MySQL、ClickHouse、Redis、Redis Stack、MinIO、Kafka 和 Vectum：

```bash
docker compose -f ../deploy/docker-compose.yml up -d \
  redis-service redis-stack-service mysql-service \
  clickhouse-service minio-service kafka-service zenvis-service-vectum
```

镜像构建只在明确需要发布时执行：

```bash
./build.sh
PUSH_IMAGE=true ./build.sh
```

## 配置与 Profile

- 本地开发通常使用 `dev` Profile；可用 Profile 以 `application*.properties` 为准。
- 后端默认端口为 `11001`，以 `server.port` 和 Compose 实际配置为准。
- 密码、token 和密钥使用环境变量或忽略提交的 `config/local-secrets.properties`，不得写入代码、文档或示例。
- 不要直接用容器部署配置覆盖 `application-dev.properties`；容器服务名、挂载路径和宿主机地址不同。
- 新增配置时同步检查 `application-dev/prod/saas`、`deploy/config/zenvis-backend`、Compose、健康检查和文档。

关键配置域包括 MySQL/ClickHouse/Redis 数据源、Redis Stack 向量存储、模型服务、MinIO Bucket、Vectum 地址、开放配置路径、插件目录、REST Bearer Token 和 MCP Bearer Token。

## HTTP 与跨模块契约

- 常规业务接口返回 `ResponseWrap<T>`，结构为 `status`、`msg`、`data`，成功状态为 `0`。
- Controller 路径使用 `/api/v1/**`；前端浏览器路径为 `/zenvis/api/v1/**`，代理时移除 `/zenvis`。
- 动态插件 API 使用 `/api/v1/plugin/{package_name}/**`。
- JSON 请求/响应使用 `snake_case`，字段名与前端类型、插件契约、MCP Schema 和 API 文档保持一致。
- Swagger 位于 `http://localhost:11001/swagger-ui/index.html`；内置 MCP Server 使用七组 `/mcp/{serviceCode}/sse` 与 `/mcp/{serviceCode}/message`。
- 公开业务服务上报接口不要求 Session/Bearer Token，但生产环境必须在部署层限制来源、请求规模和频率。

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

接口变更必须同步检查前端请求/响应类型、API 文档、插件动态 API、MCP Schema 和兼容性测试。不要在没有明确需求时破坏既有字段或改变错误语义。

## 数据、权限与插件边界

- MySQL 保存用户、角色、菜单、配置和插件元数据；ClickHouse 保存插件实体分析数据；Redis 保存 Session、缓存和运行状态；Redis Stack 只用于向量/RAG；MinIO 保存附件和业务对象。
- Retrieval 由 Meta、规则、受限表达式、查询引擎和前端状态共同组成，不接受任意 SQL。修改前阅读[数据与检索架构](../doc/06-架构设计/数据与检索架构.md)。
- 权限改动必须同时检查 `AuthorityInterceptor`、角色菜单树、超级管理员行为、审计日志和敏感字段脱敏。
- 插件业务表不加入核心 Entity 扫描。插件 MySQL 迁移放在 `03_api/migrations/mysql`，已执行迁移不得修改，只能追加更高版本；插件卸载不删除业务表和迁移历史。
- 插件 ClickHouse 结构由 Meta `auto_create` 和部署共同决定；安装、升级、恢复、卸载必须保持 Meta、动态 API、UI、看板、MCP、Skill、菜单和迁移一致。
- 不得恢复任意 SQL 生成、SQL 执行或 SQL 校验链路；数据访问只能通过平台定义的安全查询和工具白名单。

## Java 代码规范

- 类名使用大驼峰，方法/变量使用小驼峰，常量使用 `UPPER_SNAKE_CASE`，包名全小写；禁止拼音命名。
- 使用 4 个空格缩进，不使用 Tab；保持现有文件格式，不进行与任务无关的全局格式化。
- 禁止通配符导入；导入按 Java 标准库、第三方库、本项目分组。
- 公共类型和复杂业务方法保持职责清晰；注释解释约束和原因，不写无意义注释或保留注释掉的代码。
- 使用 Lombok 时保持现有风格，不因减少样板代码引入难以追踪的行为。
- 业务异常按项目现有 `ApiException`/全局异常处理机制抛出；禁止静默吞异常，错误信息应包含必要上下文但不泄露凭据或敏感数据。
- 使用 SLF4J 参数化日志，禁止 `System.out.println`；日志级别按 DEBUG/INFO/WARN/ERROR 语义选择。
- 配置值不得硬编码；敏感值通过环境变量或受控配置注入。
- 请求和响应类型必须明确，优先使用已有 DTO/VO；不要返回 `Map<String, Object>` 代替契约模型。
- 新代码遵循现有包结构和命名；不要为了局部需求创建平行的第二套查询、权限或插件加载链路。

## 测试与交付检查

- 优先运行与改动领域最接近的测试，再运行 `mvn test`。Retrieval 重点回归可参考[后端开发指南](../doc/07-开发指南/zenvis-backend-开发对接指南.md)。
- 涉及权限、插件生命周期、Retrieval、DIH、RAG、MCP、MinIO、Kafka 或 Vectum 的改动，必须检查对应领域测试和失败路径。
- 需要真实 MySQL、ClickHouse、Redis、Kafka、Vectum 或模型服务的场景，单独标注为集成验证；不得用单元测试通过代替真实链路结论。
- 不提交 `target/`、日志、运行数据、本机配置、`.DS_Store` 或凭据。
- 接口、配置、部署方式或用户可见行为变化时，同步更新 `doc/08-API参考`、主题文档、前端类型和插件契约。

交付前至少确认：

- `mvn test` 通过，或明确记录与本次改动无关的既有失败；
- Controller、DTO/VO、Service、Repository 职责清晰；
- `/api/v1`、`snake_case` 和 `ResponseWrap` 契约未被破坏；
- 新配置在 dev/容器/文档中一致且没有凭据；
- Retrieval、插件或 AI 改动完成对应领域回归；
- 在 `zenvis-backend` 所属 Git 工作树检查 `git status --short` 和 `git diff --check`。

## AI Agent 架构与执行边界

ZenVis Backend 的 AI Agent 基于 Spring AI、MCP 工具调用和 Skill 配置构建。当前内置业务智能体包括元数据配置、数据推送服务、临时图表、可视化应用、数据大屏和报表制作。所有智能体都不生成或执行任意 SQL。

### DIH 执行边界

- 普通问答 `ask` 不加载 Skill，不解析或调用业务 MCP/本地工具，并在所有未删除知识库中自动执行 RAG。
- `ask` 仅在本轮存在附件时加载 `attachment_*` 只读附件工具；工具访问范围严格限制为当前用户、当前会话和本轮明确引用的附件，不改变 `ask` 不加载业务 MCP 的边界。
- `ask + deep_think=true` 与普通问答使用同一全库 RAG 范围，并保留原生模型 reasoning 流。
- 业务 Agent / Skill 只加载显式绑定且已启用的 Skill，并在 Skill 绑定知识库的并集内自动执行 RAG；未绑定知识库时不检索，也不回退全库。
- RAG 参考资料由平台在每轮请求自动注入，不占用 MCP 工具名额、工具调用预算或审批流程。
- Agent 请求携带 `deep_think=true` 时按 `false` 执行。

### 核心 Agent

可视化智能体位于：

- `com.coolxer.service.dih.agent.VisualizationChartAgent`
- `com.coolxer.service.dih.agent.VisualizationAppAgent`
- `com.coolxer.service.dih.agent.VisualizationDashboardAgent`

核心能力：

- 分别使用独立会话类型、Skill、Workflow、决策协议和结果分区。
- 使用 `PromptDrivenAgentRuntime` 驱动对话，并复用统一聊天流式响应与会话保存逻辑。
- 先通过只读 retrieval MCP 获取实体与字段 Meta，再执行已批准的安全查询。
- 临时图表只生成 ECharts 快照；可视化应用生成 AMIS 配置与菜单；数据大屏只生成 `DashboardSpec`，由平台编译 HTML/AMIS 并发布配置与 Dashboard。

工具边界：

- 临时图表只允许只读 retrieval、实体查询和 `add_to_library` 服务端动作，无配置、Dashboard、菜单或 Meta 写工具。
- 可视化应用只允许 retrieval、只读菜单查询和 `data-app` 的 `visualization_app_list/get/validate/apply/retry_menu`，无配置写、Dashboard、推送或 Meta 写工具。
- 数据大屏只允许 retrieval 只读工具和 `dashboard` 的 `visualization_dashboard_list/get/validate/apply`，无 `config_*`、`dashboard_create`、`menu_*`、推送或 Meta 写工具；发布不创建菜单。
- 只有临时图表可产生图表库记录；三者都不追加外部 MCP tools。
- 尊重 `app.ai.mcp.enabled=false` 的全局禁用。

其他业务 Agent：

- 元数据配置、数据推送服务和报表 Agent 通过各自类型接入 `PromptDrivenAgentRuntime`。
- 动态 `agent_skill` 会话仅在 Skill manifest 显式声明 `runtime.tools` 时获得对应工具白名单。
- MCP 工具由 `AgentMcpToolService` 按 agent 类型解析。
- Skill 由 `SkillService` 加载后拼入 system prompt。

### 通用 AI 服务与 RAG

`com.coolxer.service.dih.AgentLlmService` 封装 Spring AI `ChatClient`，支持按线程设置模型、注入 MCP tool callback 和工具 system prompt，并供 AI 分析任务等非会话流程复用。

RAG 相关能力位于 `com.coolxer.service.dih.rag`，服务于普通问答、深度问答和业务 Agent/Skill。插件通过 `08_rag/config.json` 声明托管数据集和知识库；安装、升级、卸载和导出由 `PluginRagService` 处理，`00_doc` 不再自动加载到 RAG。可通过 `VectorStoreQueryController` 管理这些插件资源。普通问答检索全部未删除知识库，业务会话只检索所选 Skill 绑定的知识库。

### AI 开发注意事项

- 可视化 Agent 新能力应优先通过 retrieval MCP 工具扩展，而不是在 Agent 内直接访问数据库。
- 不要恢复 SQL 生成、SQL 执行或 SQL 校验链路。
- 新增工具时需要在 `AgentMcpToolService` 中明确 agent 可见范围。
- 会产生副作用的工具必须按领域分别暴露：临时图表不获得任何写工具，应用不获得 Dashboard 工具，大屏不获得 Meta 或推送写工具。
- 修改 Agent 工具白名单、Skill 绑定、RAG 范围或审批流程时，必须补充权限边界测试和拒绝路径测试。

## 关联文档

- [项目级规范](../AGENTS.md)
- [后端开发指南](../doc/07-开发指南/zenvis-backend-开发对接指南.md)
- [平台架构](../doc/06-架构设计/README.md)
- [数据与检索架构](../doc/06-架构设计/数据与检索架构.md)
- [插件开发与集成](../doc/03-插件开发与集成/README.md)
- [AI 与数据智能](../doc/04-AI与数据智能/README.md)
- [API 参考](../doc/08-API参考/README.md)
