# 开发指南

本文档帮助开发者在本地搭建 ZenVis 开发环境。

## 环境要求

| 工具 | 版本 |
| :--- | :--- |
| JDK | 17+ |
| Maven | 3.8+ |
| Docker | 20.10+ |
| IDE | IntelliJ IDEA / VS Code |

## 项目结构

```
zenvis/
├── deploy/                  # 系统部署目录
│   ├── docker-compose.yml
│   ├── config/              # 容器服务配置
│   ├── data/                # 数据持久化目录
│   └── open_config/         # ZenVis 开放配置
└── zenvis-backend/
    ├── src/main/java/com/coolxer/
    │   ├── controller/          # REST接口
    │   │   ├── dih/            # AI对话
    │   │   ├── dashboard/      # 仪表盘
    │   │   └── system/         # 系统管理
    │   ├── service/            # 业务逻辑
    │   │   └── dih/            # AI服务
    │   │       └── agent/      # Agent实现
    │   ├── dao/                # 数据访问
    │   │   ├── mysql/          # MySQL实体
    │   │   └── clickhouse/     # ClickHouse实体
    │   ├── configuration/      # 配置类
    │   └── aop/                # 切面
    ├── src/main/resources/
    │   └── application.properties
    └── pom.xml
```

## 开发环境搭建

### 1. 进入代码目录

```bash
cd zenvis/zenvis-backend
```

### 2. 启动依赖服务

使用 Docker 启动数据库服务：

```bash
# 启动数据库服务（不包含后端）
docker compose -f ../deploy/docker-compose.yml up -d redis-service redis-stack-service mysql-service clickhouse-service
```

### 3. 配置 IDE

#### IntelliJ IDEA

1. File → Open → 选择项目根目录
2. Import as Maven Project
3. 设置 Project SDK 为 JDK 17
4. 等待 Maven 依赖下载完成

#### 配置运行

1. 创建 Run Configuration
2. Main class: `com.coolxer.Application`
3. VM options: `-Dspring.profiles.active=dev`
4. Working directory: `$MODULE_DIR$`

### 4. 本地配置

复制并修改配置文件：

```bash
cp ../deploy/config/zenvis-backend/config/application.properties src/main/resources/application-dev.properties
```

修改数据库连接：

```properties
# 开发环境使用 localhost
spring.datasource.mysql.url=jdbc:mysql://localhost:3306/zenvis
spring.datasource.clickhouse.url=jdbc:clickhouse://localhost:8123/zenvis
spring.data.redis.host=localhost
```

## 常用命令

### Maven 命令

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 跳过测试编译
mvn clean compile -DskipTests

# 打包（不混淆）
mvn clean package -Dproguard.skip=true

# 跳过测试打包
mvn clean package -DskipTests -Dproguard.skip=true
```

### 运行应用

```bash
# 使用 Maven
mvn spring-boot:run

# 使用 JAR
java -jar target/zenvis-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

## 代码规范

### Controller 规范

```java
@RestController
@RequestMapping("/api/v1/example")
public class ExampleController {

    @PostMapping("/list")
    public Result<?> list(@RequestBody QueryDTO query) {
        return Result.success(exampleService.list(query));
    }
}
```

### Service 规范

采用接口+实现模式：

```java
// 接口
public interface ExampleService {
    Result<?> list(QueryDTO query);
}

// 实现
@Service
public class ExampleServiceImpl implements ExampleService {
    @Override
    public Result<?> list(QueryDTO query) {
        // 实现
    }
}
```

### DAO 规范

```java
// MySQL 实体
@Entity
@Table(name = "analysis_record")
public class AnalysisRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}

// ClickHouse 实体
@Entity
@Table(name = "events")
public class Event {
    @Id
    private String id;
}
```

## 扩展边界

具体业务能力不应在 `zenvis-backend` 中新增 Controller、Service、Entity 或
Repository。后端核心只维护认证、系统管理、检索、看板、插件生命周期和 AI 等框架能力。
需要新增业务接口、数据表或页面时，应创建独立插件并遵循
[插件开发指南](../06-插件开发与集成/plugin-development.md)。

## 数据库结构变更

核心 JPA 表当前由 Hibernate `ddl-auto=update` 维护。插件自己的 MySQL 表不得加入
核心 Entity 扫描，而应在插件发布目录的 `03_api/migrations/mysql/` 中提供版本化 SQL：

```text
plugin-custom/
└── 03_api/
    └── migrations/mysql/
        └── V001__init_schema.sql
```

平台安装或升级插件时执行尚未应用的迁移并记录 SHA-256；已执行的迁移文件禁止修改。
完整规则见[插件开发指南](../06-插件开发与集成/plugin-development.md#5-添加版本化-mysql-迁移)。

## 热部署

开发环境支持热部署：

```bash
# 使用 spring-boot-devtools
mvn spring-boot:run -Dspring-boot.run.fork=false
```

修改代码后会自动重启。

## 调试技巧

### Retrieval 模块

修改全局检索前先阅读 [Retrieval 全局检索模块快速上手](../03-架构设计/retrieval-module.md)。该模块由元数据、规则兼容、受限表达式、查询引擎和前端异步状态共同组成，不应绕过规则生成逻辑直接拼接自由 SQL。

后端重点回归命令：

```bash
mvn -Dtest='WhereExpressionParserTest,RetrievalRuleLifecycleTest,RetrievalRuleServiceImplTest,MetaDataServiceImplTest,QueryEngineImplTest,RetrievalControllerTest,LogAopAspectTest' test
```

### 接口调试

启动应用后访问 Swagger UI：

```
http://localhost:11001/swagger-ui/index.html
```

### 日志调试

查看实时日志：

```bash
tail -f logs/zenvis.log
```

### 数据库调试

连接数据库：

```bash
# MySQL
mysql -h localhost -u zenvis -p zenvis

# ClickHouse
clickhouse-client --host localhost
```

## 常见问题

### 1. Maven 依赖下载慢

配置阿里云镜像：

```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>*</mirrorOf>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

### 2. 端口被占用

```bash
# macOS
lsof -i :11001

# Linux
netstat -tlnp | grep 11001
```

### 3. 数据库连接失败

确认 Docker 服务已启动：

```bash
docker ps
```

## 下一步

- [MCP Client 与 Agent 设计](../07-AI与数据智能/MCP-Client-Agent-Design.md)
- [插件开发](../06-插件开发与集成/plugin-development.md)
- [API参考](../05-API参考/api-reference.md)
