# REST API 直连接入

非 Spring Boot 应用可以直接调用 ZenVis 公开 REST API。接入方需要自行管理实例身份、心跳调度、事件 ID、重试、关闭处理和本地可靠性。

本页只提供完成接入所需的最小协议。字段格式、长度限制、响应结构、管理查询参数和错误码以[业务服务 REST API](/08-API参考/RestfulAPI/业务服务.md)为准。

## 接入前准备

准备以下信息：

- ZenVis 后端根地址，例如 `http://zenvis-host:11001`；
- 稳定的 `service_code`；
- 当前实例唯一的 `instance_id`；
- 服务展示名称、版本、环境、主机和端口；
- 统一的时间格式 `yyyy-MM-dd HH:mm:ss`。

`base-url` 不包含 `/api/v1`。如果通过网关接入，应使用网关实际暴露给业务服务的后端地址和路径。

## 公开端点

只有以下两个精确的 POST 路径不需要 ZenVis Session 或普通 API Bearer Token：

```text
POST /api/v1/public/business-services/heartbeat
POST /api/v1/public/business-services/events
```

其他 HTTP 方法、相似路径和所有 `/api/v1/system/business-services/**` 管理查询都需要登录 Session 或 REST API Bearer Token。

## 上报心跳

首次心跳自动注册实例，后续心跳更新该实例的最新状态。

```bash
curl -X POST "http://zenvis-host:11001/api/v1/public/business-services/heartbeat" \
  -H "Content-Type: application/json" \
  -d '{
    "service_code": "order-api",
    "service_name": "订单服务",
    "instance_id": "order-api-10.0.0.8-8080",
    "status": "UP",
    "status_message": "ready",
    "version": "2.3.1",
    "environment": "prod",
    "host": "10.0.0.8",
    "port": 8080,
    "management_url": "http://10.0.0.8:8080/actuator/health",
    "heartbeat_time": "2026-07-31 09:30:00",
    "metadata": {
      "region": "cn-east",
      "zone": "az-1"
    }
  }'
```

成功响应示例：

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "service_code": "order-api",
    "instance_id": "order-api-10.0.0.8-8080",
    "registered": true,
    "received_at": "2026-07-31 09:30:01",
    "effective_status": "UP",
    "offline_after_seconds": 90
  }
}
```

接入方应同时检查 HTTP 状态码为 2xx 且响应 `status` 为 `0`。`registered` 仅表示本次是否首次注册，不表示后续是否需要继续发送心跳。

建议心跳间隔显著小于服务端离线阈值。默认配置为每 30 秒发送一次，超过 90 秒未收到心跳后显示 `OFFLINE`。

## 上报事件

实例至少成功上报过一次心跳后才能发送事件。

```bash
curl -X POST "http://zenvis-host:11001/api/v1/public/business-services/events" \
  -H "Content-Type: application/json" \
  -d '{
    "event_id": "order-api-1-20260731-0001",
    "service_code": "order-api",
    "instance_id": "order-api-10.0.0.8-8080",
    "event_type": "ORDER_SYNC_FAILED",
    "severity": "ERROR",
    "title": "订单同步失败",
    "message": "下游库存接口返回 503",
    "occurred_at": "2026-07-31 09:31:00",
    "trace_id": "4f43d6c98b7a",
    "data": {
      "downstream": "inventory-api",
      "retry_count": 3
    }
  }'
```

成功响应示例：

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "event_id": "order-api-1-20260731-0001",
    "accepted_at": "2026-07-31 09:31:01",
    "duplicate": false
  }
}
```

严重级别仅支持 `INFO`、`WARN`、`ERROR` 和 `CRITICAL`。建议将事件类型设计成稳定的大写编码，便于跨版本查询和告警。

## 重试与幂等

心跳失败时，可以在下一调度周期重试。实现时应设置连接超时和读取超时，避免上报阻塞业务线程或占满工作线程。

事件重试必须遵守以下规则：

- 每个逻辑事件先生成唯一且稳定的 `event_id`；
- 同一事件重试时复用原 `event_id`；
- 相同实例重复上报时，成功响应中的 `duplicate` 为 `true`；
- 不要为每次重试生成新 ID，否则会产生多条事件记录；
- `event_id` 已属于其他实例时会返回业务状态 `409`，此时不能继续按当前实例重试。

关键事件需要由业务应用先持久化，再异步提交到 ZenVis。只依赖一次 HTTP 请求无法保证可靠交付。

## 客户端实现要求

生产客户端至少应具备：

- 独立于业务请求的心跳调度器；
- 明确且有限的连接、读取和总请求超时；
- 容量受限的异步事件队列；
- 对 HTTP 2xx 和业务 `status` 的双重检查；
- 对重试次数、退避和最大积压时间的限制；
- 正常关闭时发送停止事件和 `DOWN` 心跳；
- 上报失败日志或指标，但不得记录密码、Token 和完整敏感事件数据。

如果使用持久队列重放事件，需要保持原 `event_id`，并确保重放不会阻塞正常心跳。

## 安全边界

公开端点当前不要求 Session 或 Bearer Token，也不提供内置应用签名和限流。公开不等于适合直接暴露到互联网。生产环境必须通过 TLS、网关、防火墙、来源白名单、服务网络策略和限流保护。

具体部署要求见[生产部署与验收](/05-业务服务接入/生产部署与验收.md)。

上一篇：[Spring Boot Starter 接入](/05-业务服务接入/Spring-Boot-Starter接入.md)

下一篇：[生产部署与验收](/05-业务服务接入/生产部署与验收.md)

相关文档：[业务服务 REST API](/08-API参考/RestfulAPI/业务服务.md)
