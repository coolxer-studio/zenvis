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
zenvis-backend/
├── src/main/java/com/coolxer/
│   ├── controller/          # REST接口
│   │   ├── business/       # 业务接口
│   │   │   ├── asset/      # 资产管理
│   │   │   ├── operation/ # 运营事件
│   │   │   └── risk/       # 风险管理
│   │   ├── dih/           # AI对话
│   │   ├── dashboard/     # 仪表盘
│   │   └── system/         # 系统管理
│   ├── service/            # 业务逻辑
│   │   └── dih/           # AI服务
│   │       └── agent/     # Agent实现
│   ├── dao/                # 数据访问
│   │   ├── mysql/         # MySQL实体
│   │   └── clickhouse/    # ClickHouse实体
│   ├── configuration/      # 配置类
│   └── aop/               # 切面
├── src/main/resources/
│   └── application.properties
├── deploy/                 # 部署配置
├── doc/                    # 文档
└── pom.xml
```

## 开发环境搭建

### 1. 克隆代码

```bash
git clone https://github.com/your-repo/zenvis-backend.git
cd zenvis-backend
```

### 2. 启动依赖服务

使用 Docker 启动数据库服务：

```bash
cd deploy

# 启动数据库服务（不包含后端）
docker-compose -f docker-compose.yml up -d redis-service redis-stack-service mysql-service clickhouse-service
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
cp deploy/config/zenvis-backend/application.properties src/main/resources/application-dev.properties
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
@RequestMapping("/api/v1/asset")
public class AssetHostController {

    @PostMapping("/list")
    public Result<?> list(@RequestBody QueryDTO query) {
        return Result.success(assetService.list(query));
    }
}
```

### Service 规范

采用接口+实现模式：

```java
// 接口
public interface AssetService {
    Result<?> list(QueryDTO query);
}

// 实现
@Service
public class AssetServiceImpl implements AssetService {
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
@Table(name = "asset_host")
public class AssetHost {
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

## 分层开发

### 新增业务模块

1. **Controller 层**

```java
@RestController
@RequestMapping("/api/v1/business/{module}")
public class BusinessController {
    @Autowired
    private BusinessService businessService;
}
```

2. **Service 层**

```java
public interface BusinessService {
    Result<?> query(QueryDTO dto);
}

@Service
public class BusinessServiceImpl implements BusinessService {
    @Override
    public Result<?> query(QueryDTO dto) {
        // 业务逻辑
    }
}
```

3. **DAO 层**

```java
// MySQL
public interface BusinessRepository extends JpaRepository<BusinessEntity, Long> {
}

// ClickHouse
public interface EventRepository extends ClickHouseRepository<EventEntity> {
}
```

## 数据库迁移

使用 Flyway 管理数据库迁移：

```bash
# 创建迁移文件
touch src/main/resources/db/migration/V1__init_schema.sql

# Flyway 会自动在应用启动时执行
```

## 热部署

开发环境支持热部署：

```bash
# 使用 spring-boot-devtools
mvn spring-boot:run -Dspring-boot.run.fork=false
```

修改代码后会自动重启。

## 调试技巧

### Retrieval 模块

修改全局检索前先阅读 [Retrieval 全局检索模块快速上手](retrieval-module.md)。该模块由元数据、规则兼容、受限表达式、查询引擎和前端异步状态共同组成，不应绕过规则生成逻辑直接拼接自由 SQL。

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

- [MCP Client 与 Agent 设计](../DIH/MCP-Client-Agent-Design.md)
- [插件开发](plugin-development.md)
- [API参考](api-reference.md)
