# Spring Boot Starter 接入

`zenvis-business-service-spring-boot-starter` 面向 Spring Boot 3.x、JDK 17 应用，自动处理实例身份、心跳调度、启动/停止事件和异步自定义事件上报。

## 引入依赖

```xml
<dependency>
    <groupId>com.coolxer.zenvis</groupId>
    <artifactId>zenvis-business-service-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

部署或试用前，应确认该版本已经发布到应用可访问的 Maven 仓库。需要从当前源码构建时，参考[业务服务 Starter 开发对接指南](/07-开发指南/zenvis-business-service-spring-boot-starter-开发对接指南.md)。

## 最小配置

```properties
spring.application.name=order-api
zenvis.business-service.base-url=http://localhost:11001
```

`base-url` 是 ZenVis 后端根地址，不包含 `/api/v1`。应用触发 `ApplicationReadyEvent` 后，Starter 会立即调度第一次心跳。

需要显式禁用时配置：

```properties
zenvis.business-service.enabled=false
```

禁用后不会发送心跳或事件，但仍提供 no-op `BusinessServiceReporter`，业务代码可以继续安全注入。

## 身份推导

没有显式配置时，Starter 按以下顺序确定实例身份，并在当前进程内固定解析结果：

| 信息 | 默认推导 |
| --- | --- |
| `service-code` | `spring.application.name`，仍为空时使用 `spring-boot-service` |
| `service-name` | `service-code` |
| `host` | 本机主机名，解析失败时使用 `localhost` |
| `port` | `local.server.port`，然后回退 `server.port` |
| `instance-id` | `<service-code>-<host>-<port>`；端口未知时使用 `no-port` |
| `version` | Spring Boot `BuildProperties` 中的版本；不可用时为空 |
| `environment` | 第一个 active profile；没有 active profile 时使用 `default` |

标识中的非法字符会被规范化，并按服务端长度限制截断。生产环境建议显式配置稳定的 `service-code`，并确认自动生成的 `instance-id` 能区分并发实例。

## 配置参考

配置前缀为 `zenvis.business-service`：

| 属性 | 默认值或推导方式 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否启用自动上报 |
| `base-url` | `http://localhost:11001` | ZenVis 后端根地址 |
| `service-code` | 应用名或 `spring-boot-service` | 稳定服务编码 |
| `service-name` | `service-code` | 展示名称 |
| `instance-id` | 服务编码、主机和端口组合 | 实例唯一标识 |
| `version` | `BuildProperties` | 应用版本；无法推导时为空 |
| `environment` | 首个 active profile 或 `default` | 部署环境 |
| `host` | 本机主机名或 `localhost` | 实例主机 |
| `port` | 应用实际端口或配置端口 | 服务端口 |
| `management-url` | 空 | 管理或健康检查地址 |
| `heartbeat-interval-millis` | `30000` | 心跳间隔，运行时最小为 1ms |
| `connect-timeout-millis` | `2000` | HTTP 连接超时 |
| `read-timeout-millis` | `3000` | HTTP 读取超时 |
| `event-queue-capacity` | `100` | 异步事件队列容量，运行时最小为 1 |
| `time-zone` | `Asia/Shanghai` | 上报时间格式化时区 |
| `metadata` | 空 Map | 实例扩展信息 |

Spring Boot relaxed binding 支持使用环境变量：

```bash
export ZENVIS_BUSINESS_SERVICE_BASE_URL=http://zenvis-host:11001
export ZENVIS_BUSINESS_SERVICE_SERVICE_CODE=order-api
export ZENVIS_BUSINESS_SERVICE_SERVICE_NAME=订单服务
export ZENVIS_BUSINESS_SERVICE_INSTANCE_ID=order-api-node-01
export ZENVIS_BUSINESS_SERVICE_ENVIRONMENT=prod
```

容器环境可将 Pod UID、稳定主机名或编排平台提供的唯一实例标识注入 `instance-id`。不要让多个并发实例使用同一个值。

## 生命周期

默认生命周期如下：

1. `ApplicationReadyEvent` 触发上报管理器。
2. Starter 立即调度一次 `UP` 心跳。
3. 首次心跳成功后，上报 `SERVICE_STARTED` 事件。
4. 默认每 30 秒继续上报 `UP` 心跳。
5. 应用正常关闭时，上报 `SERVICE_STOPPING` 事件和 `DOWN` 心跳。

如果自定义事件发生在首次心跳成功之前，Starter 会先尝试发送心跳；注册仍失败时跳过该事件并记录警告。

强制终止、容器 `SIGKILL` 或过短的关闭窗口可能阻止停止事件和 `DOWN` 心跳。ZenVis 仍会在超过离线阈值后将实例判定为 `OFFLINE`。

## 上报自定义事件

在业务组件中注入公共接口 `BusinessServiceReporter`：

```java
import com.coolxer.zenvis.businessservice.BusinessServiceEventSeverity;
import com.coolxer.zenvis.businessservice.BusinessServiceReporter;

import java.util.Map;

public class OrderSyncService {

    private final BusinessServiceReporter businessServiceReporter;

    public OrderSyncService(BusinessServiceReporter businessServiceReporter) {
        this.businessServiceReporter = businessServiceReporter;
    }

    public void reportFailure(String traceId) {
        businessServiceReporter.reportEvent(
                "ORDER_SYNC_FAILED",
                BusinessServiceEventSeverity.ERROR,
                "订单同步失败",
                "下游库存接口返回 503",
                traceId,
                Map.of("downstream", "inventory-api"));
    }
}
```

严重级别支持 `INFO`、`WARN`、`ERROR` 和 `CRITICAL`。Starter 会生成事件 ID，并规范化事件类型、标题、消息、Trace ID 和扩展数据：

- 空事件类型使用 `UNKNOWN_EVENT`；
- 空严重级别使用 `ERROR`；
- 空标题使用规范化后的事件类型；
- `metadata` 的 JSON 上限为 16 KiB；
- 事件 `data` 的 JSON 上限为 64 KiB；
- 过大或无法序列化的扩展数据会被替换为带原因的截断标记。

仍建议接入方主动控制内容大小，避免依赖 Starter 截断。

## 失败与可靠性

Starter 使用独立 HTTP 客户端、单线程心跳调度器和容量受限的单线程事件执行器：

- 心跳或事件网络失败不会向宿主业务线程传播；
- ZenVis 返回非 2xx，或响应中的 `status` 不为 `0`，均视为上报失败；
- 事件队列已满、组件正在停止或传输失败时，事件可能被丢弃并记录警告；
- 事件队列只提供进程内异步缓冲，不提供磁盘持久化和跨进程重试；
- 关键事件应由宿主业务持久化，并通过事务消息、可靠队列或补偿任务保证交付。

生产参数、安全要求和验收步骤见[生产部署与验收](/05-业务服务接入/生产部署与验收.md)。

上一篇：[接入模型与运行机制](/05-业务服务接入/接入模型与运行机制.md)

下一篇：[REST API 直连接入](/05-业务服务接入/REST-API直连接入.md)

相关文档：[业务服务 Starter 开发对接指南](/07-开发指南/zenvis-business-service-spring-boot-starter-开发对接指南.md)
