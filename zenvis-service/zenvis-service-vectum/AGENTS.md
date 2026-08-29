# AGENTS.md

## 1. 项目概述

**Vectum** 是基于 Vector 封装的轻量级数据管道管理工具，提供可视化界面 + RESTful API + MCP协议三位一体能力。

**技术栈**: Java 17 + Spring Boot 3.2.0 + Spring AI MCP Server 1.1.4 + SpringDoc OpenAPI 2.3.0 + Vector 0.35+ + AMIS + Thymeleaf

**目录结构**:
- `src/main/java/com/coolxer/` - 后端业务代码
- `src/main/resources/` - 配置文件与静态资源
- `src/test/` - 单元测试
- `deploy/` - Docker部署配置
- `doc/` - 项目文档

---

## 2. 构建/测试/启动命令

### 核心命令
| 命令 | 说明 |
|------|------|
| `mvn clean compile` | 编译项目 |
| `mvn clean package -DskipTests` | 打包构建(跳过测试) |
| `mvn spring-boot:run` | 开发态运行(访问 http://localhost:11002) |
| `java -jar target/application.jar` | 运行打包后的Jar |

### 测试命令
| 命令 | 说明 |
|------|------|
| `mvn test` | 运行所有单元测试 |
| `mvn test -Dtest=ApplicationTests` | 运行单个测试类 |
| `mvn test -Dtest=ApplicationTests#contextLoads` | 运行单个测试方法 |
| `mvn test -Dtest="*ServiceTest"` | 运行匹配模式的测试 |

### 质量检查
| 命令 | 说明 |
|------|------|
| `mvn checkstyle:check` | 代码风格检查 |
| `mvn dependency:tree` | 依赖分析 |

### Docker构建
```bash
./build.sh                    # 使用构建脚本
docker build -t vectum:latest .  # 手动构建
docker run -d -p 11002:11002 vectum:latest
```

---

## 3. 代码风格规范

### 命名约定
- **类名**: 大驼峰 `TaskController`, `VectorServiceImpl`
- **方法/变量**: 小驼峰 `taskService`, `getAllTasks()`
- **常量**: 全大写+下划线 `MAX_TASK_COUNT`, `DEFAULT_PORT`
- **枚举类**: 后缀Enum `ResultCodeEnum`, `TaskStatusEnum`
- **包名**: 全小写 `com.coolxer.service.impl`
- **禁止使用拼音命名**

### 格式规范
- **缩进**: 必须使用4个空格，禁止Tab
- **行尾**: 必须加分号
- **大括号**: K&R风格，左括号不换行
- **行长度**: 建议不超过120字符

### 注释规范
- **类级**: 必须有类级Javadoc说明职责
- **公共方法**: 必须有Javadoc注释(`@param`, `@return`, `@throws`)
- **复杂逻辑**: 必须有单行注释解释
- **禁止**: 无意义的注释、注释掉的代码

### 导入规范
- 禁止使用通配符导入 `import java.util.*`
- 按顺序组织: Java标准库 → 第三方库 → 本项目包
- 使用Lombok注解(`@Data`, `@Slf4j`, `@AllArgsConstructor`等)减少样板代码

### 异常处理
- 使用统一的 `ApiException` 处理业务异常
- 禁止 `catch` 后静默吞掉异常
- 异常信息必须包含上下文变量
- 使用 `@ControllerAdvice` 统一处理全局异常

### 日志规范
- 使用SLF4J + Lombok `@Slf4j` 注解
- 禁止使用 `System.out.println`
- 日志级别: `DEBUG`(开发调试) → `INFO`(业务流程) → `WARN`(警告) → `ERROR`(错误)
- 使用参数化日志: `log.info("Task {} started", taskId)`

### 配置管理
- 配置项必须在 `application.properties` 中定义
- 使用 `@Value` 或 `@ConfigurationProperties` 注入
- 禁止硬编码配置值
- 敏感配置通过环境变量注入

---

## 4. 架构分层约定

```
controller/ → REST API层 (入参校验, 调用service, 返回ResponseWrap)
    ↓
service/ → 业务逻辑层 (接口+impl实现, 事务管理)
    ↓
dao/ → 数据访问层 (Repository接口+实现)
    ↓
model/ → 数据模型 (dto入参, vo出参, entity实体)
```

**关键约定**:
- Controller层: 仅处理HTTP请求/响应, 不包含业务逻辑
- Service层: 接口定义在 `service/`, 实现在 `service/impl/`
- 统一响应格式: `{status, msg, data}` (使用 `ResponseWrap`)
- DTO用于接收前端参数, VO用于返回给前端

---

## 5. API规范

- **基础路径**: `/vectum/api/v1/task`
- **HTTP方法**: GET(查询), POST(创建/操作), PUT(更新), DELETE(删除)
- **统一响应**: 所有接口返回 `{status: 0|1, msg: "success|error", data: {...}}`
- **Swagger文档**: 访问 http://localhost:11002/swagger-ui/index.html

---

## 6. 前端约定(AMIS)

- 使用AMIS内置组件: `Page`, `Table`, `Form`, `Dialog`, `CodeEditor`
- 通过JSON Schema配置页面，禁止直接操作DOM
- 数据源通过 `api` 属性绑定后端接口，统一使用POST方法
- 表单校验使用AMIS内置规则
- 模板引擎: Thymeleaf渲染AMIS配置JSON

---

## 7. Git工作流

1. **严禁直接修改main分支**
2. Bug修复: `git checkout -b fix/xxx`
3. 新功能: `git checkout -b feature/xxx`
4. 提交信息: `类型: 简要说明` (如 `fix: 修复任务启动失败问题`)
5. 提交前确保: 编译通过 + 测试通过 + 风格检查通过

---

## 8. 关键配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 11002 | 服务端口 |
| `vector.home` | /vector/ | Vector安装目录 |
| `task.workspace` | /workspace/ | 任务工作空间目录 |

**配置文件**: `src/main/resources/application.properties`
**环境配置**: `application-dev.properties`, `application-prod.properties`

---

## 9. 日志路径

- 应用日志: `logs/vectum.log`
- 任务日志: `{task.workspace}/{task.id}/vector.log`

---

## 10. 参考资源优先级

1. Spring Boot官方文档
2. Spring AI官方文档 (MCP协议)
3. AMIS官方文档 (前端)
4. Vector官方文档 (数据管道配置)
5. 本项目现有代码约定优先于一切外部规范
