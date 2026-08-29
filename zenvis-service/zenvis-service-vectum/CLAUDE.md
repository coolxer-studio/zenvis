# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目简介

**Vectum** 是基于 [Vector](https://vector.dev/) 的轻量级数据管道管理工具，通过 Web UI、RESTful API 和 MCP 协议统一管理多实例 Vector 进程。

**技术栈**: Java 17 + Spring Boot 3.2.0 + Spring AI MCP Server 1.1.4 + SpringDoc OpenAPI 2.3.0 + AMIS (百度低代码前端) + Thymeleaf

## 命令

```bash
mvn clean compile                          # 编译
mvn spring-boot:run                        # 开发运行（访问 http://localhost:11002）
mvn clean package -DskipTests             # 打包（输出 target/application.jar）
java -jar target/application.jar           # 运行 Jar

# 测试
mvn test                                   # 全部测试
mvn test -Dtest=ApplicationTests           # 指定测试类
mvn test -Dtest=ApplicationTests#contextLoads  # 指定测试方法

# Docker
./build.sh                                 # 构建脚本
docker build -t vectum:latest .
docker run -d -p 11002:11002 vectum:latest
```

## 架构核心

### 数据存储
**无数据库**。`TaskRepositoryImpl` 将任务数据全部保存在内存（`List<Task> data`），并通过 `ObjectMapper` 持久化为 JSON 文件（路径由 `task.file` 配置，默认 `workspace/tasks.json`）。所有读写方法均用 `synchronized` 保护。

### 任务工作空间
每个任务在 `task.workspace/{taskId}/` 下拥有独立目录：
```
{task.workspace}/{taskId}/
  push.yaml (或 .json / .toml)   # Vector 配置文件
  pid                             # 当前进程 PID
  info.log                        # 标准输出日志
  error.log                       # 标准错误日志
  lua/                            # Lua 脚本目录
```

### Vector 进程管理
`VectorServiceImpl` 通过 `ProcessBuilder` 启动 `{vector.home}/bin/vector -c <configPath>`，进程引用存储在静态 `ConcurrentHashMap<String, Process> PROCESS_CACHE` 中（key 为任务 ID 字符串）。

**启动恢复机制**：应用启动时（`@PostConstruct`），扫描工作空间中有 `pid` 文件的目录 → 先 kill 旧进程 → 重新启动，实现进程恢复。

**配置格式自动检测**：`detectFormat()` 方法通过内容特征（`{` → JSON，`---` → YAML，`=` 计数 > `:` → TOML）自动推断格式后缀，支持 YAML/JSON/TOML。

### MCP 协议
`McpTaskTools` 中用 `@Tool` 注解暴露任务管理工具，由 `McpConfig` 注册到 Spring AI MCP Server，通过 SSE 接口 `/sse` 提供服务。Jackson 版本冲突处理：pom.xml 中显式排除了 Spring AI MCP 引入的 `tools.jackson.core`，保留 Spring Boot 3.2.0 自带的 Jackson 2.x。

### 前端
Thymeleaf 渲染 `index.html`，前端逻辑完全通过 AMIS JSON Schema 配置（不直接操作 DOM），AMIS SDK 静态文件位于 `src/main/resources/static/sdk/`。

## 分层约定

```
controller/  →  入参校验，调用 service，返回 ResponseWrap{status, msg, data}
service/     →  接口定义 + impl/ 实现，所有业务逻辑在此层
dao/         →  TaskRepository 接口 + TaskRepositoryImpl（JSON文件持久化）
model/dto/   →  接收前端参数（TaskDto）
model/vo/    →  返回给前端（TaskVo, ResponseWrap）
```

- API 基础路径：`/vectum/api/v1/task`
- Swagger 文档：`http://localhost:11002/swagger-ui/index.html`
- 统一异常：业务错误抛 `ApiException`，禁止静默吞掉异常

## 关键配置

| 配置项 | 开发默认值 | 说明 |
|--------|-----------|------|
| `vector.home` | `vector` | Vector 安装目录，需在此目录下有 `bin/vector` 可执行文件 |
| `task.workspace` | `workspace` | 任务工作空间根目录 |
| `task.file` | `workspace/tasks.json` | 任务数据 JSON 文件 |
| `server.port` | `11002` | 服务端口 |

开发环境配置文件：`src/main/resources/application-dev.properties`

## 代码规范（摘要）

- 4 个空格缩进，禁止 Tab；导入禁止通配符
- 使用 `@Slf4j` + SLF4J 日志，禁止 `System.out.println`
- 参数化日志：`log.info("Task {} started", taskId)`
- 使用 Lombok（`@Data`, `@Slf4j` 等）减少样板代码
- Git 分支：`fix/xxx`（Bug修复）、`feature/xxx`（新功能），禁止直接修改 main
