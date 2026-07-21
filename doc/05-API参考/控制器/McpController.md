# McpController MCP 服务管理接口文档

**基础信息**

- **模块名称**: MCP 服务管理
- **基础路径**: `/api/v1/dih/mcp`
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 数据模型定义

### McpServerDto

```json
{
  "code": "risk-system",
  "name": "风险系统 MCP",
  "description": "风险系统提供的查询和处置工具",
  "base_url": "http://127.0.0.1:11002",
  "sse_endpoint": "/sse",
  "headers": "{\"Authorization\":\"Bearer token\"}",
  "enabled": true,
  "request_timeout_seconds": 30,
  "connect_timeout_seconds": 10
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | String | 是 | MCP 服务标识，唯一，最多 64 个字符 |
| name | String | 是 | MCP 服务名称 |
| description | String | 否 | 服务描述 |
| baseUrl/base_url | String | 是 | MCP 服务基础地址 |
| sseEndpoint/sse_endpoint | String | 否 | SSE endpoint，默认 `/sse` |
| headers | String | 否 | 固定 HTTP 请求头，JSON 对象字符串 |
| enabled | Boolean | 否 | 是否启用，默认 `true` |
| requestTimeoutSeconds/request_timeout_seconds | Integer | 否 | MCP 请求超时秒数，默认 30 |
| connectTimeoutSeconds/connect_timeout_seconds | Integer | 否 | HTTP 连接超时秒数，默认 10 |

### McpServerVo

```json
{
  "id": 1,
  "code": "risk-system",
  "name": "风险系统 MCP",
  "description": "风险系统提供的查询和处置工具",
  "base_url": "http://127.0.0.1:11002",
  "sse_endpoint": "/sse",
  "headers": "{\"Authorization\":\"Bearer token\"}",
  "enabled": true,
  "request_timeout_seconds": 30,
  "connect_timeout_seconds": 10,
  "connected": true,
  "last_error": null,
  "last_connected_time": "2026-06-30 07:00:00",
  "tool_count": 8,
  "create_time": "2026-06-30 07:00:00",
  "update_time": "2026-06-30 07:00:00"
}
```

### McpToolVo

```json
{
  "server_id": 1,
  "server_code": "risk-system",
  "server_name": "风险系统 MCP",
  "name": "query_user",
  "ai_tool_name": "risk_system_query_user",
  "title": "查询用户",
  "description": "根据用户ID查询用户风险信息",
  "input_schema": {},
  "output_schema": {},
  "read_only_hint": true,
  "destructive_hint": false,
  "idempotent_hint": true,
  "open_world_hint": false
}
```

### McpToolCallDto

```json
{
  "server_id": 1,
  "server_code": "risk-system",
  "name": "query_user",
  "approval_request_id": null,
  "arguments": {
    "userId": "10001"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| serverId/server_id | Integer | 否 | MCP 服务 ID |
| serverCode/server_code | String | 否 | MCP 服务标识，未传 `serverId` 时使用 |
| name | String | 是 | 原始 MCP 工具名，不是 `ai_tool_name` |
| arguments | Object | 否 | 工具参数 |
| approvalRequestId/approval_request_id | String | 否 | `ASK` 两阶段调用批准后重试时携带的 requestId |

### 工具策略

每个工具使用稳定唯一键：

```text
local::<toolName>
external::<serverId>::<originalToolName>
```

策略值为 `ALLOW`、`ASK`、`DENY`。策略列表同时返回默认策略、人工策略和最终有效策略；更新请求的 `policy=null` 表示恢复默认。

批量更新请求：

```json
{
  "tool_keys": ["local::dashboard_create", "external::1::query_user"],
  "policy": "ASK"
}
```

策略修改仅超级管理员可用。

### 审批与调用状态

调用状态包括：`PENDING`、`APPROVED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`REJECTED`、`DENIED`、`EXPIRED`、`CANCELLED`。

审批范围包括：

| 范围 | 说明 |
|---|---|
| `ONCE` | 单次批准 |
| `SESSION` | DIH Chat 当前用户、chatId、toolKey 会话授权 |
| `TASK_AUTO` | AI分析任务 `AUTO` 自动批准 |
| `TASK_RUN` | 当前 AI分析任务 execution 的工具授权 |

---

## 接口总览

| HTTP 方法 | 接口路径 | 功能描述 |
|----------|----------|----------|
| GET | `/api/v1/dih/mcp/servers/list` | 分页查询 MCP 服务 |
| POST | `/api/v1/dih/mcp/servers/add` | 新增 MCP 服务 |
| POST | `/api/v1/dih/mcp/servers/{id}/update` | 更新 MCP 服务 |
| DELETE | `/api/v1/dih/mcp/servers/{id}` | 删除 MCP 服务 |
| GET | `/api/v1/dih/mcp/servers/{id}/view` | 查询 MCP 服务详情 |
| POST | `/api/v1/dih/mcp/servers/{id}/enable` | 启用 MCP 服务 |
| POST | `/api/v1/dih/mcp/servers/{id}/disable` | 停用 MCP 服务 |
| POST | `/api/v1/dih/mcp/servers/{id}/refresh` | 刷新单个 MCP 服务连接 |
| POST | `/api/v1/dih/mcp/servers/refresh` | 刷新全部已启用 MCP 服务连接 |
| GET | `/api/v1/dih/mcp/tools` | 查询 MCP 工具列表 |
| POST | `/api/v1/dih/mcp/tools/call` | 测试调用 MCP 工具 |
| GET | `/api/v1/dih/mcp/tools/policies/list` | 分页查询工具策略 |
| POST | `/api/v1/dih/mcp/tools/policies/update` | 更新单工具策略 |
| POST | `/api/v1/dih/mcp/tools/policies/bulk-update` | 批量更新工具策略 |
| GET | `/api/v1/dih/mcp/approvals/list` | 查询当前待审批请求 |
| GET | `/api/v1/dih/mcp/approvals/{requestId}/view` | 查询审批详情 |
| POST | `/api/v1/dih/mcp/approvals/{requestId}/decision` | 提交审批决定 |
| GET | `/api/v1/dih/mcp/invocations/list` | 分页查询调用审计 |
| GET | `/api/v1/dih/mcp/agent/prompt` | 查看业务 Agent MCP 工具提示词 |

---

## 接口详情

### 查询 MCP 服务列表

**接口地址**: `GET /api/v1/dih/mcp/servers/list`

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | String | 否 | 匹配 code、name、description、baseUrl |
| enabled | Boolean | 否 | 启用状态 |
| connected | Boolean | 否 | 连接状态 |
| page | Integer | 否 | 页码，默认 1 |
| perPage | Integer | 否 | 每页数量，默认 10 |

**请求示例**:

```bash
curl "http://localhost:11001/api/v1/dih/mcp/servers/list?page=1&perPage=10&enabled=true"
```

**成功响应**:

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "rows": [
      {
        "id": 1,
        "code": "risk-system",
        "name": "风险系统 MCP",
        "enabled": true,
        "connected": true,
        "tool_count": 8
      }
    ],
    "total": 1
  }
}
```

### 新增 MCP 服务

**接口地址**: `POST /api/v1/dih/mcp/servers/add`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/mcp/servers/add" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "risk-system",
    "name": "风险系统 MCP",
    "base_url": "http://127.0.0.1:11002",
    "sse_endpoint": "/sse",
    "enabled": true,
    "request_timeout_seconds": 30,
    "connect_timeout_seconds": 10
  }'
```

新增时如果 `enabled=true`，服务会立即尝试连接并拉取工具列表。

### 更新 MCP 服务

**接口地址**: `POST /api/v1/dih/mcp/servers/{id}/update`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/mcp/servers/1/update" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "risk-system",
    "name": "风险系统 MCP",
    "base_url": "http://127.0.0.1:11002",
    "sse_endpoint": "/sse",
    "enabled": true
  }'
```

更新后会关闭旧连接；如果服务仍启用，会重新刷新连接。

### 删除 MCP 服务

**接口地址**: `DELETE /api/v1/dih/mcp/servers/{id}`

```bash
curl -X DELETE "http://localhost:11001/api/v1/dih/mcp/servers/1"
```

删除前会先关闭运行期 MCP client。

### 查询 MCP 服务详情

**接口地址**: `GET /api/v1/dih/mcp/servers/{id}/view`

```bash
curl "http://localhost:11001/api/v1/dih/mcp/servers/1/view"
```

### 启用 MCP 服务

**接口地址**: `POST /api/v1/dih/mcp/servers/{id}/enable`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/mcp/servers/1/enable"
```

启用后会立即刷新连接。

### 停用 MCP 服务

**接口地址**: `POST /api/v1/dih/mcp/servers/{id}/disable`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/mcp/servers/1/disable"
```

停用后会关闭运行期 MCP client，并从 AI 可用工具中移除。

### 刷新单个 MCP 服务连接

**接口地址**: `POST /api/v1/dih/mcp/servers/{id}/refresh`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/mcp/servers/1/refresh"
```

成功时 `connected=true`，并返回最新工具数。

### 刷新全部 MCP 服务连接

**接口地址**: `POST /api/v1/dih/mcp/servers/refresh`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/mcp/servers/refresh"
```

只刷新 `enabled=true` 的服务。

### 查询 MCP 工具列表

**接口地址**: `GET /api/v1/dih/mcp/tools`

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| serverId | Integer | 否 | 指定 MCP 服务 ID；不传时返回全部已连接工具 |

```bash
curl "http://localhost:11001/api/v1/dih/mcp/tools?serverId=1"
```

### 测试调用 MCP 工具

**接口地址**: `POST /api/v1/dih/mcp/tools/call`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/mcp/tools/call" \
  -H "Content-Type: application/json" \
  -d '{
    "server_id": 1,
    "name": "query_user",
    "arguments": {
      "userId": "10001"
    }
  }'
```

如果工具有效策略为 `ASK`，首次调用只创建审批请求，不执行底层工具。批准后使用完全相同的参数和返回的 requestId 重试：

```json
{
  "server_id": 1,
  "name": "query_user",
  "approval_request_id": "a1bd28ef-91ce-45b8-b501-d13a1b29c3dc",
  "arguments": {
    "userId": "10001"
  }
}
```

服务端校验规范化参数摘要，并通过条件更新保证最多执行一次。

### 查询工具审批策略

```http
GET /api/v1/dih/mcp/tools/policies/list?page=1&perPage=20
```

| 参数 | 类型 | 说明 |
|---|---|---|
| `keyword` | String | 匹配工具键、名称、描述或服务 |
| `sourceType` | Enum | `LOCAL` 或 `EXTERNAL` |
| `policy` | Enum | 按有效策略过滤 |
| `available` | Boolean | 工具当前是否可用 |

不可用工具仍保留策略记录，便于在外部服务恢复前提前维护权限。

### 更新工具策略

```http
POST /api/v1/dih/mcp/tools/policies/update
```

```json
{
  "tool_key": "local::dashboard_create",
  "policy": "ASK"
}
```

`policy` 传 `null` 恢复默认。批量接口 `/tools/policies/bulk-update` 使用 `tool_keys` 数组，语义相同。

### 查询待审批队列

```http
GET /api/v1/dih/mcp/approvals/list?page=1&perPage=20
```

该接口只返回 `PENDING` 请求。审批完成、超时或取消后从队列移除，但仍可在调用审计中查询。

### 提交审批决定

```http
POST /api/v1/dih/mcp/approvals/{requestId}/decision
```

```json
{
  "decision": "approved",
  "comment": "确认本次调用"
}
```

- `approved`：允许当前 requestId。
- `approved_session`：仅 DIH Chat 使用，授权当前用户、chatId 和精确 toolKey。
- `rejected`：拒绝当前调用。

AI分析任务的 `approved_task` 应使用任务专用接口，见 [AnalysisTaskController](AnalysisTaskController.md)。普通用户只能处理自己的请求，超级管理员可以代审。

### 查询调用审计

```http
GET /api/v1/dih/mcp/invocations/list?page=1&perPage=20
```

| 参数 | 类型 | 说明 |
|---|---|---|
| `keyword` | String | 匹配 requestId、工具、服务、chatId、Agent |
| `channel` | Enum | `CHAT_AGENT`、`BACKGROUND_AGENT`、`MCP_SERVER`、`MANUAL` |
| `status` | Enum | 调用状态 |
| `policy` | Enum | 策略快照 |
| `approvalScope` | Enum | `ONCE`、`SESSION`、`TASK_AUTO`、`TASK_RUN` |
| `requesterUserId` | Integer | 发起用户，超级管理员可指定 |
| `decisionBy` | Integer | 审批人 |
| `analysisTaskId` | Integer | AI分析任务 ID |
| `executionId` | String | AI分析任务 executionId |

参数、结果和错误摘要均会递归脱敏并截断。普通用户只能查看自己的调用记录，超级管理员可以查看全量记录。

### 查看业务 Agent MCP 工具提示词

**接口地址**: `GET /api/v1/dih/mcp/agent/prompt`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agentType | String | 否 | 业务 Agent 类型，如 `ask`、`agent_data_access`、`agent_data_visualization`；不传时按默认 scope |

```bash
curl "http://localhost:11001/api/v1/dih/mcp/agent/prompt?agentType=agent_data_access"
```

返回指定业务 Agent 当前会注入的 MCP 工具规则和已连接服务工具摘要。

---

## 聊天中使用 MCP 工具

MCP 工具作为业务 Agent 的工作流能力注入聊天接口，不再需要单独的 `mcp_agent` 类型。历史别名 `agent_mcp` 也按兼容请求处理。

**接口地址**: `POST /api/v1/dih/chat`

请求中的 `type` 可为普通问答 `ask`，也可为具体业务 Agent，如 `agent_data_access`、`agent_data_visualization`。`ask` 始终不注入 MCP 或本地工具，`app.ai.mcp.agent-scopes.ask` 不产生效果；业务 Agent 根据 `app.ai.mcp.agent-scopes.<type>` 控制可用 MCP 服务范围。

`ASK` 工具会在当前 AI 消息内插入审批卡片，支持“允许本次”“本会话始终允许”和“拒绝执行”。聊天会话授权不会覆盖全局 `DENY`，停止生成只取消本轮待审批请求。

示例：

```bash
curl -X POST "http://localhost:11001/api/v1/dih/chat" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "chat_id": "mcp-demo-001",
    "type": "agent_data_access",
    "model": "auto",
    "message": "帮我查询 userId 为 10001 的风险记录"
  }'
```

---

## 注意事项

1. `headers` 必须是 JSON 对象字符串，格式错误会返回失败。
2. 服务 `code` 会被规范化为 AI 工具名前缀，建议只使用英文、数字、点、下划线和中划线。
3. MCP 服务只有在 `enabled=true` 且刷新连接成功后，工具才会进入 AI 可用工具列表。
4. 工具测试接口使用原始 MCP 工具名；AI 对话中使用的是带服务前缀的规范化工具名。
5. 当前实现主要面向 SSE MCP 服务，`base_url` 需要填写服务根地址，`sse_endpoint` 填写 SSE 路径。
6. 可通过 `app.ai.mcp.agent-scopes.<agentType>=serverCode1,serverCode2` 限制某个业务 Agent 可使用的 MCP 服务；值为 `none` 时禁用该 Agent 的 MCP 工具。普通问答不读取该配置。
7. MCP 审批队列只展示待处理项；历史和终态记录统一在调用审计查看。
8. 更完整的操作和排障流程见 [MCP 审批与 AI分析任务快速上手](../../07-AI与数据智能/MCP审批与AI分析任务快速上手.md)。
