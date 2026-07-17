# 第三方 REST API 对接指南

本文面向新同事和第三方系统开发者，说明 ZenVis 后端 REST API 的产品边界、认证方式、调用规范和快速验证方法。接口细节以运行时 Swagger UI 和源码为准。

## 产品与接口地图

ZenVis 后端以 `/api/v1` 为主要 REST API 前缀，围绕“配置化数据存储 + 全局检索 + 可视化看板 + 插件扩展 + AI 数智中心”提供能力。

| 能力域 | 典型路径 | 说明 |
| :--- | :--- | :--- |
| 登录与系统信息 | `/api/v1/system/login/**`、`/api/v1/system/about/**` | Web 登录、验证码、公钥、系统关于信息 |
| 系统管理 | `/api/v1/system/user/**`、`/role/**`、`/menu/**` | 用户、角色、菜单、权限管理 |
| 业务应用服务 | `/api/v1/public/business-services/**`、`/api/v1/system/business-services/**` | 心跳/事件公开上报与受认证只读管理查询 |
| 插件与配置 | `/api/v1/system/plugin/**`、`/api/v1/config/{type}/**` | 插件安装、文档、配置文件读写与应用 |
| 数据检索 | `/api/v1/retrieval/**`、`/api/v1/entity/{entity}/**` | 元数据驱动检索、动态实体 CRUD、统计趋势 |
| 看板与插件接口 | `/api/v1/dashboard/home/**`、`/api/v1/plugin/{package_name}/**` | 系统状态看板，以及按插件包名动态注册的接口 |
| 数智中心 DIH | `/api/v1/dih/**` | AI 对话、会话、附件、Skill、MCP 客户端管理 |
| MCP Server | `/sse`、`/mcp/message` | Spring AI MCP Server 端点，不属于普通 REST API |

在线文档入口：

```text
http://<host>:11001/swagger-ui/index.html
http://<host>:11001/v3/api-docs
```

## 统一调用规则

普通 JSON 接口返回 `ResponseWrap`：

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {}
}
```

判断业务成功时以 `status === 0` 为准，HTTP 200 只表示传输成功。字段命名使用 `snake_case`，例如 `per_page`、`order_by`、`chat_id`、`response_format`。

常用请求头：

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <API_BEARER_TOKEN>
```

文件上传接口使用 `multipart/form-data`，不要手动拼 boundary。

## 认证方式

ZenVis 当前支持两套认证方式，可同时存在。

### 公开业务应用服务上报

业务服务程序可以直接调用以下两个精确的公开 `POST` 路径，无需 Cookie 或 Bearer Token：

```text
POST /api/v1/public/business-services/heartbeat
POST /api/v1/public/business-services/events
```

该放行不覆盖 `GET`、子路径或 `/api/v1/system/business-services/**` 管理查询。公开接口没有应用层签名和限流，生产环境应通过网关、防火墙或内部服务网络控制可访问来源。

最小接入顺序是先心跳注册，再上报事件：

```bash
curl -X POST "http://<host>:11001/api/v1/public/business-services/heartbeat" \
  -H "Content-Type: application/json" \
  -d '{
    "service_code": "payment-api",
    "service_name": "支付服务",
    "instance_id": "payment-api-10.0.0.9-8080",
    "status": "UP",
    "version": "1.4.0",
    "environment": "prod"
  }'

curl -X POST "http://<host>:11001/api/v1/public/business-services/events" \
  -H "Content-Type: application/json" \
  -d '{
    "event_id": "payment-api-20260715-0001",
    "service_code": "payment-api",
    "instance_id": "payment-api-10.0.0.9-8080",
    "event_type": "PAYMENT_TIMEOUT",
    "severity": "ERROR",
    "title": "支付请求超时"
  }'
```

调用方应为每个进程或副本生成稳定且唯一的 `instance_id`，为每个事件生成全局唯一的 `event_id`，并以小于离线阈值的间隔持续发送心跳。完整字段、限制和幂等规则见 [业务应用服务接口文档](../api接口文档/BusinessServiceController.md)。

### 1. Web Session/Cookie

适合浏览器前端。流程是获取验证码和 `JSESSIONID`，用 RSA 公钥加密密码后登录，后续请求带同一个 Cookie。

```bash
curl -c cookie.txt http://<host>:11001/api/v1/system/login/kaptcha -o kaptcha.png

curl -b cookie.txt http://<host>:11001/api/v1/system/login/encrypt/key

curl -b cookie.txt -c cookie.txt \
  -H "Content-Type: application/json" \
  -X POST http://<host>:11001/api/v1/system/login/sign-in \
  -d '{
    "user_name": "admin@admin.com",
    "password": "<RSA加密后的密码>",
    "auth_code": "<验证码>"
  }'
```

### 2. REST API Bearer Token

适合第三方系统、脚本、后端服务调用。配置 `API_BEARER_TOKEN` 后，普通 `/api/v1/**` 接口可直接携带 Bearer Token，不需要验证码和 Cookie。

```bash
curl -H "Authorization: Bearer <API_BEARER_TOKEN>" \
  "http://<host>:11001/api/v1/system/user/list?page=1&per_page=10"
```

相关配置：

| 配置项 | 环境变量 | 说明 |
| :--- | :--- | :--- |
| `app.security.api.bearer-token` | `API_BEARER_TOKEN` | 普通 REST API Bearer Token。生产环境默认空，必须显式配置后才启用 |
| `app.security.api.bearer-user` | `API_BEARER_USER` | Bearer 调用映射到的系统用户邮箱，默认 `admin@admin.com` |
| `app.security.mcp.bearer-token` | `MCP_BEARER_TOKEN` | MCP Server `/sse`、`/mcp/message` 专用 Bearer Token |

`API_BEARER_USER` 必须是系统中存在的用户。服务端会把该用户写入本次请求上下文，供 `getSessionUser()`、权限上下文和 JPA 审计使用。

安全建议：

- 生产环境不要使用示例 token 或开发默认值。
- REST API token 和 MCP token 分开配置，避免跨用途复用。
- 如果需要不同第三方系统使用不同身份，优先扩展为“多 token 到多用户”的配置或表结构，而不是共享同一个 token。

## 典型调用示例

### 健康检查

```bash
curl http://<host>:11001/api/v1/dih/health
```

### 动态实体分页查询

```bash
curl -H "Authorization: Bearer <API_BEARER_TOKEN>" \
  "http://<host>:11001/api/v1/entity/user-event/list?page=1&per_page=10"
```

### 元数据检索

```bash
curl -H "Authorization: Bearer <API_BEARER_TOKEN>" \
  -H "Content-Type: application/json" \
  -X POST http://<host>:11001/api/v1/retrieval/do \
  -d '{
    "display_list": [
      {
        "entity": "user-event",
        "attribute_list": ["id", "user", "event_type"]
      }
    ],
    "criteria_list": [
      {
        "attribute": "event_type",
        "operator": "equal",
        "value_list": ["login"]
      }
    ],
    "criteria_logic": "and",
    "page": 1,
    "size": 10
  }'
```

### AI 对话

```bash
curl -H "Authorization: Bearer <API_BEARER_TOKEN>" \
  -H "Content-Type: application/json" \
  -X POST http://<host>:11001/api/v1/dih/chat \
  -d '{
    "chat_id": "third-party-demo-001",
    "type": "general",
    "message": "帮我分析最近数据",
    "response_format": "events"
  }'
```

`response_format=events` 时返回换行分隔事件流，调用方需要按流式响应处理。

## 新同事排障清单

| 现象 | 常见原因 | 处理方式 |
| :--- | :--- | :--- |
| 返回 `status=101` | 未携带 Cookie，也未携带正确 Bearer Token | 检查 `Authorization: Bearer ...`，或重新登录获取 `JSESSIONID` |
| 业务应用服务实例显示 `OFFLINE` | 心跳停止，或心跳间隔超过默认 90 秒 | 检查实例 ID 是否稳定、网络和上报调度；客户端时间不会影响在线判定 |
| 事件返回 `status=404` | 对应 `service_code + instance_id` 尚未心跳注册 | 先成功上报一次心跳，并确保两个请求的联合标识完全一致 |
| 事件返回 `status=409` | `event_id` 已属于其他实例 | 为新事件生成全局唯一 ID；同实例原事件可用原 ID 安全重试 |
| 返回 `status=100` | Bearer Token 未配置，或 `API_BEARER_USER` 不存在 | 配置 `API_BEARER_TOKEN`，确认用户邮箱存在 |
| HTTP 200 但业务失败 | 业务状态码不为 0 | 以响应体 `status` 和 `msg` 判断 |
| JSON 字段收不到 | 使用了驼峰字段或 query 参数名不匹配 | JSON 优先使用 `snake_case`；分页 query 用 `per_page` |
| AI 接口报模型错误 | LLM 配置为空或不可访问 | 检查 `OPENAI_BASE_URL`、`OPENAI_API_KEY`、`OPENAI_CHAT_MODEL` |
| MCP `/sse` 返回 401 | 使用了 REST API token 调 MCP Server | MCP Server 使用 `MCP_BEARER_TOKEN` |

## 关键源码入口

| 文件 | 作用 |
| :--- | :--- |
| `src/main/java/com/coolxer/aop/AuthorityInterceptor.java` | `/api/v1/**` Session 与 REST API Bearer Token 鉴权 |
| `src/main/java/com/coolxer/aop/McpBearerTokenInterceptor.java` | MCP Server Bearer Token 鉴权 |
| `src/main/java/com/coolxer/controller/BaseController.java` | 当前用户解析，兼容 Session 与 Bearer 上下文 |
| `src/main/java/com/coolxer/configuration/JpaAuditingConfiguration.java` | JPA 创建人/更新人审计用户解析 |
| `src/main/java/com/coolxer/configuration/OpenApiConfig.java` | Swagger/OpenAPI 安全方案定义 |
| `src/main/java/com/coolxer/configuration/JacksonConfig.java` | JSON 字段命名、时间格式和反序列化规则 |
