# Zenvis Business Service Spring Boot Starter

面向 Spring Boot 3.x、JDK 17 的 Zenvis 业务服务心跳与事件上报组件。

## 使用

```xml
<dependency>
    <groupId>com.coolxer.zenvis</groupId>
    <artifactId>zenvis-business-service-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

最小配置：

```properties
spring.application.name=order-api
zenvis.business-service.base-url=http://localhost:11001
```

组件默认开启，在 `ApplicationReadyEvent` 后立即注册实例，之后默认每 30 秒续报心跳。
关闭时自动发送停止事件和 `DOWN` 心跳。需要禁用时配置：

```properties
zenvis.business-service.enabled=false
```

自定义事件：

```java
businessServiceReporter.reportEvent(
        "ORDER_SYNC_FAILED",
        BusinessServiceEventSeverity.ERROR,
        "订单同步失败",
        "下游接口返回 503",
        traceId,
        Map.of("downstream", "inventory-api"));
```

标准环境变量可直接使用 Spring Boot relaxed binding，例如：

```bash
export ZENVIS_BUSINESS_SERVICE_BASE_URL=http://zenvis-host:11001
export ZENVIS_BUSINESS_SERVICE_SERVICE_CODE=order-api
export ZENVIS_BUSINESS_SERVICE_SERVICE_NAME=订单服务
```

组件使用独立 HTTP 客户端、心跳调度线程和容量受限的单线程事件队列。
Zenvis 网络异常、业务响应失败或队列满不会影响宿主业务请求。
