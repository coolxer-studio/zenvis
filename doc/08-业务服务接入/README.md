# 业务服务接入

本主题给出平台侧完整接入说明；Starter 的依赖、属性和示例仍完整保留在[模块 README](../../zenvis-business-service-spring-boot-starter/README.md)。

## 目标

业务应用服务通过心跳和事件向 ZenVis 汇报运行状态。平台统一展示服务概览、实例、版本、环境、最近心跳和事件，适合补充业务级可观测性。

Spring Boot 3.x、JDK 17 应用可以使用 `zenvis-business-service-spring-boot-starter`；其他技术栈可以直接调用公开 REST API。

## 引入 Starter

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

组件默认启用。应用触发 `ApplicationReadyEvent` 后立即上报 `UP` 心跳，默认每 30 秒续报；关闭时发送停止事件和 `DOWN` 心跳。

禁用：

```properties
zenvis.business-service.enabled=false
```

## 配置参考

配置前缀为 `zenvis.business-service`：

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否启用自动配置 |
| `base-url` | `http://localhost:11001` | ZenVis 后端地址 |
| `service-code` | 回退到应用名 | 稳定服务编码 |
| `service-name` | 回退到服务编码 | 展示名称 |
| `instance-id` | 自动生成/推导 | 实例唯一标识，应在实例生命周期内稳定 |
| `version` | 空 | 应用版本 |
| `environment` | 空 | 环境，如 dev/test/prod |
| `host` | 自动推导 | 主机名或地址 |
| `port` | 应用端口 | 服务端口 |
| `management-url` | 空 | 管理或健康检查地址 |
| `heartbeat-interval-millis` | `30000` | 心跳间隔 |
| `connect-timeout-millis` | `2000` | 连接超时 |
| `read-timeout-millis` | `3000` | 读取超时 |
| `event-queue-capacity` | `100` | 异步事件队列容量 |
| `time-zone` | `Asia/Shanghai` | 时间格式化时区 |
| `metadata` | 空 Map | 实例扩展信息 |

Spring Boot relaxed binding 支持环境变量：

```bash
export ZENVIS_BUSINESS_SERVICE_BASE_URL=http://zenvis-host:11001
export ZENVIS_BUSINESS_SERVICE_SERVICE_CODE=order-api
export ZENVIS_BUSINESS_SERVICE_SERVICE_NAME=订单服务
export ZENVIS_BUSINESS_SERVICE_ENVIRONMENT=prod
```

## 上报事件

注入 `BusinessServiceReporter`：

```java
businessServiceReporter.reportEvent(
        "ORDER_SYNC_FAILED",
        BusinessServiceEventSeverity.ERROR,
        "订单同步失败",
        "下游接口返回 503",
        traceId,
        Map.of("downstream", "inventory-api"));
```

事件字段：

| 字段 | 说明 |
| --- | --- |
| `event_id` | Starter 生成的唯一事件 ID |
| `service_code` | 服务编码 |
| `instance_id` | 实例 ID |
| `event_type` | 事件类型；空值规范为 `UNKNOWN_EVENT` |
| `severity` | 事件严重级别 |
| `title` | 简短标题 |
| `message` | 详细说明 |
| `occurred_at` | 发生时间 |
| `trace_id` | 链路追踪 ID |
| `data` | 扩展 JSON 数据 |

服务端校验上限：`event_type` 64 字符、`title` 255 字符、`message` 4000 字符、`trace_id` 128 字符；心跳 `metadata` 的 UTF-8 JSON 不超过 16KiB，事件 `data` 不超过 64KiB。严重级别仅支持 `INFO`、`WARN`、`ERROR`、`CRITICAL`。

`event_id` 用于幂等重试：同一服务实例重复上报同一 ID 返回已有记录；如果该 ID 已属于另一 `service_code + instance_id`，服务端返回业务冲突状态 409。

事件通过容量受限的单线程队列异步发送。队列满、ZenVis 网络失败或业务响应失败不会阻塞宿主业务请求，但会记录警告并丢弃无法提交的事件。重要事件如果要求可靠交付，应由业务系统额外持久化并实现重试/补偿。

## 直接调用 REST API

### 心跳

```http
POST /api/v1/public/business-services/heartbeat
Content-Type: application/json
```

```json
{
  "service_code": "order-api",
  "service_name": "订单服务",
  "instance_id": "order-api-10.0.0.8-8080",
  "status": "UP",
  "status_message": "ready",
  "version": "1.4.0",
  "environment": "prod",
  "host": "10.0.0.8",
  "port": 8080,
  "management_url": "http://10.0.0.8:8080/actuator/health",
  "heartbeat_time": "2026-07-21T09:30:00+08:00",
  "metadata": {
    "region": "cn-north"
  }
}
```

状态支持 `UP`、`DEGRADED` 和 `DOWN`。

### 事件

```http
POST /api/v1/public/business-services/events
Content-Type: application/json
```

```json
{
  "event_id": "evt-20260721-0001",
  "service_code": "order-api",
  "instance_id": "order-api-10.0.0.8-8080",
  "event_type": "ORDER_SYNC_FAILED",
  "severity": "ERROR",
  "title": "订单同步失败",
  "message": "下游库存接口返回 503",
  "occurred_at": "2026-07-21T09:31:00+08:00",
  "trace_id": "trace-123",
  "data": {
    "downstream": "inventory-api"
  }
}
```

公开接口不需要 ZenVis Session 或普通 API Bearer Token。生产环境应通过网络隔离、API 网关、来源白名单、限流或额外签名进行保护。

## ZenVis 管理接口

登录用户可以通过以下接口查看：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/system/business-services/summary` | 服务概览 |
| GET | `/api/v1/system/business-services/instances` | 实例列表 |
| GET | `/api/v1/system/business-services/instances/{id}` | 实例详情 |
| GET | `/api/v1/system/business-services/events` | 事件列表 |

服务端配置：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `app.business-service.offline-threshold-seconds` | `90` | 超过阈值未收到心跳则视为离线 |
| `app.business-service.event-retention-days` | `30` | 事件保留天数 |
| `app.business-service.instance-retention-days` | `30` | 实例保留天数 |
| `app.business-service.cleanup-delay-ms` | `3600000` | 清理任务间隔 |

心跳间隔应显著小于离线阈值。默认 30 秒心跳配合 90 秒阈值，可以容忍短时网络抖动。

## 运行与容错

- Starter 使用独立 HTTP 客户端、心跳调度线程和事件执行器；
- 心跳失败不会让宿主应用退出；
- 事件队列满时丢弃新事件并记录日志；
- `instance_id` 不稳定会造成一个实例被展示为多个历史实例；
- 事件队列只提供进程内异步缓冲，不是持久消息队列；可靠事件需由宿主应用自行持久化和补偿；
- 应用关闭钩子不是绝对可靠，服务端仍应依赖离线阈值判断异常退出。

## 接入检查

1. 应用启动后一分钟内出现服务与实例；
2. 实例 ID 在重启策略范围内符合预期；
3. 心跳时间持续更新，状态从 `UP` 正确变化；
4. 自定义事件能按服务、实例、级别和时间查询；
5. ZenVis 不可用时宿主业务请求仍正常；
6. 恢复连接后心跳重新出现；
7. 生产网络对公开接口设置了外围保护。
