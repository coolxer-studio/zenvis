# `zenvis-business-service-spring-boot-starter` 开发对接指南

## 项目定位

`zenvis-business-service-spring-boot-starter` 是面向 Spring Boot 3.x、JDK 17 应用的业务服务接入组件。它自动向 ZenVis 上报实例心跳、启动/停止事件，并提供异步自定义事件上报接口。

Starter 的目标是让监测链路与宿主业务解耦：ZenVis 网络异常、业务响应失败或事件队列满时记录告警并尽力降级，不向宿主业务请求传播异常。

## 上下游关系

- 上游：宿主 Spring Boot 应用的生命周期、应用身份、端口、环境、版本和自定义事件。
- 下游：ZenVis 后端两个公开上报端点，以及服务管理和事件展示页面。
- 兼容边界：Starter 不改变宿主业务接口，不要求 Session/Bearer Token，也不把网络失败传播到业务线程；服务端字段或限制变化必须同步后端、Starter 测试和接入文档。

## 技术栈与构件信息

```xml
<dependency>
    <groupId>com.coolxer.zenvis</groupId>
    <artifactId>zenvis-business-service-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

| 基线 | 当前值 |
| --- | --- |
| Java | 17 |
| Spring Boot parent | 3.2.0 |
| HTTP 客户端 | 独立 `RestTemplate` |
| JSON | 宿主 `ObjectMapper` 或默认实例 |
| 调度 | 单线程 `TaskScheduler` |
| 事件执行 | 容量受限的单线程 `TaskExecutor` |
| 测试 | JUnit 5、Spring Boot Test |

## 目录职责

```text
zenvis-business-service-spring-boot-starter/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/coolxer/zenvis/businessservice/
    │   ├── BusinessServiceEventSeverity.java
    │   ├── BusinessServiceReporter.java
    │   └── autoconfigure/
    │       ├── ZenvisBusinessServiceAutoConfiguration.java
    │       ├── ZenvisBusinessServiceProperties.java
    │       ├── ZenvisBusinessServiceIdentity.java
    │       ├── ZenvisBusinessServiceManager.java
    │       ├── ZenvisBusinessServiceClient.java
    │       └── 请求、响应和传输模型
    ├── main/resources/META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
```

`BusinessServiceReporter` 是宿主应用使用的公共接口；`autoconfigure` 下大多数类型属于 Starter 内部实现。

## 环境与最小接入

宿主应用需要 Spring Boot 3.x、JDK 17，并能够通过 HTTP 访问 ZenVis 后端。

```properties
spring.application.name=order-api
zenvis.business-service.base-url=http://localhost:11001
```

启动后：

1. `ApplicationReadyEvent` 触发上报管理器。
2. 立即调度一次 `UP` 心跳。
3. 首次注册成功后上报 `SERVICE_STARTED`。
4. 默认每 30 秒续报心跳。
5. 应用关闭时上报 `SERVICE_STOPPING` 和 `DOWN` 心跳。

禁用：

```properties
zenvis.business-service.enabled=false
```

禁用时仍提供一个 no-op `BusinessServiceReporter`，业务代码可以继续安全注入。

## 全部配置

前缀为 `zenvis.business-service`：

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `enabled` | `true` | 是否启用上报 |
| `base-url` | `http://localhost:11001` | ZenVis 后端根地址，不含接口路径 |
| `service-code` | 见身份推导 | 稳定服务代码 |
| `service-name` | `service-code` | 展示名称 |
| `instance-id` | 见身份推导 | 实例唯一标识 |
| `version` | BuildProperties | 应用版本 |
| `environment` | 首个 active profile 或 `default` | 部署环境 |
| `host` | 本机主机名 | 实例主机 |
| `port` | `local.server.port` 或 `server.port` | 服务端口 |
| `management-url` | 空 | 管理或健康检查地址 |
| `heartbeat-interval-millis` | `30000` | 心跳间隔，运行时至少为 1ms |
| `connect-timeout-millis` | `2000` | HTTP 连接超时 |
| `read-timeout-millis` | `3000` | HTTP 读取超时 |
| `event-queue-capacity` | `100` | 异步事件队列容量，运行时至少为 1 |
| `time-zone` | `Asia/Shanghai` | 上报时间格式化时区 |
| `metadata` | 空 Map | 心跳扩展元数据 |

Spring Boot relaxed binding 支持环境变量：

```bash
export ZENVIS_BUSINESS_SERVICE_BASE_URL=http://zenvis-host:11001
export ZENVIS_BUSINESS_SERVICE_SERVICE_CODE=order-api
export ZENVIS_BUSINESS_SERVICE_SERVICE_NAME=订单服务
```

## 核心开发流程

### 身份推导

如果没有显式配置，Starter 按以下顺序确定身份：

- `service-code`：`spring.application.name`，缺失时为 `spring-boot-service`；
- `service-name`：`service-code`；
- `host`：本机主机名，解析失败时为 `localhost`；
- `port`：`local.server.port`，再回退 `server.port`；
- `instance-id`：`<service-code>-<host>-<port>`；
- `version`：Spring Boot `BuildProperties`；
- `environment`：第一个 active profile，没有时为 `default`。

标识中的非法字符会被规范化并按服务端长度截断。生产环境建议显式设置稳定的 `service-code` 和可区分实例的 `instance-id`，避免容器主机名变化造成实例漂移。

### 自定义事件

注入公共接口：

```java
private final BusinessServiceReporter businessServiceReporter;
```

上报：

```java
businessServiceReporter.reportEvent(
        "ORDER_SYNC_FAILED",
        BusinessServiceEventSeverity.ERROR,
        "订单同步失败",
        "下游接口返回 503",
        traceId,
        Map.of("downstream", "inventory-api"));
```

事件级别：

- `INFO`
- `WARN`
- `ERROR`
- `CRITICAL`

事件先进入单线程有界队列。队列满、组件正在停止或网络失败时事件可能被丢弃，因此它是尽力上报机制，不是可靠消息队列。关键事件应由宿主应用持久化并按业务要求重试。

### 服务端接口

Starter 固定调用：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/api/v1/public/business-services/heartbeat` | 实例心跳和状态 |
| `POST` | `/api/v1/public/business-services/events` | 运行事件 |

接口返回 ZenVis 标准 `{ status, msg, data }`。Starter 同时要求 HTTP 2xx 和 `status = 0` 才认为成功。

公开接口不需要 Session 或 Bearer Token。生产环境仍应通过网关、网络策略、限流和来源控制保护。

### 容量、校验与幂等

- 心跳 `metadata` JSON 最大 16 KiB。
- 事件 `data` JSON 最大 64 KiB。
- 事件类型最大 64 字符。
- 标题最大 255 字符。
- 消息最大 4000 字符。
- trace ID 最大 128 字符。
- 服务端按 `event_id` 幂等接收。
- Starter 为每个自定义事件生成 UUID；启动和停止事件在进程生命周期内使用固定 ID。

超长文本和数据由 Starter 规范化或截断，过大的 Map 会被清理。接入方仍应主动控制内容，避免把敏感信息或大对象放入事件。

## 自动配置扩展点

宿主应用可以通过公共类型或约定名称覆盖：

- `BusinessServiceReporter`：完全替换公共上报实现；
- 名为 `zenvisBusinessServiceRestTemplate` 的 `RestTemplate`；
- 名为 `zenvisBusinessServiceEventExecutor` 的执行器；
- 名为 `zenvisBusinessServiceHeartbeatScheduler` 的调度器。

身份解析和传输接口当前位于内部 `autoconfigure` 实现，不属于承诺给宿主应用的公共扩展 API。一般接入只需配置属性并使用 `BusinessServiceReporter`。

## 构建与测试

```bash
cd zenvis-business-service-spring-boot-starter

# 测试
mvn test

# 打包
mvn clean package

# 安装到本机 Maven 仓库供其他应用试用
mvn clean install
```

测试重点包括：

- 自动配置启用和禁用；
- 属性绑定与默认值；
- 身份推导；
- 心跳和事件 HTTP 契约；
- 首次注册、调度和关闭生命周期；
- 队列、网络和业务失败不传播到宿主；
- JSON、长度、时区和幂等。

## 接入验收

1. 启动宿主应用，确认 ZenVis 服务管理页出现实例。
2. 检查 `service_code`、`instance_id`、版本、环境、主机和端口。
3. 等待两个心跳周期，确认在线状态持续更新。
4. 调用 `reportEvent`，确认级别、标题、trace ID 和 data。
5. 重启应用，确认旧实例超时、新实例身份符合预期。
6. 正常关闭应用，确认停止事件和 `DOWN`。
7. 临时断开 ZenVis 或填满测试队列，确认宿主业务请求不失败。
8. 检查日志中没有输出敏感事件数据或凭据。

## 常见问题

### 服务没有出现在 ZenVis

检查 `base-url` 是否为后端根地址、后端公开接口是否可达、网络策略和 Starter 日志。不要在 `base-url` 末尾追加 `/api/v1`。

### 多个实例互相覆盖

显式设置唯一 `instance-id`，或确保主机名和端口组合稳定且唯一。

### 自定义事件被跳过

事件上报前 Starter 必须先注册心跳。检查后端可达性、队列容量和组件是否正在停止。

### 关闭时没有收到 DOWN

强制终止、容器 `SIGKILL` 或短关闭超时可能阻止最后上报。服务端应结合心跳超时判断离线，不能只依赖 DOWN。

### 事件必须可靠送达

Starter 不提供磁盘队列和跨进程重试。关键事件使用宿主持久化、事务消息或可靠消息队列，再调用 Starter 作为观测副本。

## 交付检查

- `mvn test` 通过；
- 公共 `BusinessServiceReporter` 和配置前缀保持兼容；
- 服务端路径、字段、大小限制和幂等语义与后端一致；
- 失败不会传播到宿主业务线程；
- 新配置有默认值、绑定测试和 README 说明；
- 没有记录真实凭据或敏感业务数据；
- Starter 位于根工作树，提交时不混入后端等独立工作树修改。

## 关联文档

- [业务服务接入](/05-业务服务接入/README.md)
- [业务服务 REST API](/08-API参考/RestfulAPI/业务服务.md)
- [`zenvis-backend` 开发对接指南](/07-开发指南/zenvis-backend-开发对接指南.md)
- [`deploy` 开发对接指南](/07-开发指南/deploy-开发对接指南.md)
