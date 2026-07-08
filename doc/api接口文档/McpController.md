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

MCP 工具作为通用工具能力注入聊天接口，不再需要单独的 `agent_mcp` 类型。

**接口地址**: `POST /api/v1/dih/chat`

请求中的 `type` 可为普通问答 `ask`，也可为具体业务 Agent，如 `agent_data_access`、`agent_data_visualization`。后端会根据 `app.ai.mcp.agent-scopes.<type>` 控制可用 MCP 服务范围。

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
6. 可通过 `app.ai.mcp.agent-scopes.<agentType>=serverCode1,serverCode2` 限制某个业务 Agent 可使用的 MCP 服务；值为 `none` 时禁用该 Agent 的 MCP 工具。
