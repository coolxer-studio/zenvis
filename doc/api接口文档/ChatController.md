# ChatController DIH Chat 接口

## 1. 基础信息

- 基础路径：`/api/v1/dih`
- 鉴权：Session/Cookie 登录用户
- 普通响应：`ResponseWrap`
- Chat 流式响应：纯文本或 NDJSON，推荐 `response_format=events`

## 2. 接口总览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/chat` | AI 流式对话 |
| GET | `/model/list` | 查询当前可用模型，AI分析任务表单复用此接口 |
| POST | `/suggest` | 生成编辑器补全建议 |
| POST | `/upload` | 上传本轮聊天附件 |
| GET | `/upload/{fileId}/preview` | 预览当前用户的图片附件 |
| POST | `/chat/action-decision` | 保存已完成消息中的业务动作决定；不用于 MCP 审批 |
| GET | `/health` | 健康检查 |

MCP 审批使用 `/api/v1/dih/mcp/approvals/{requestId}/decision`，因为审批发生时 AI 消息尚未完成保存。

## 3. Chat 请求

```json
{
  "chat_id": "chat-001",
  "model": "auto",
  "type": "ask",
  "message": "请分析当前系统的风险态势",
  "attachments": [],
  "deep_think": false,
  "online_search": false,
  "response_format": "events"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `chat_id` | String | 建议 | 会话 ID，用于上下文和会话工具授权 |
| `model` | String | 否 | 模型 ID；`auto` 表示自动选择 |
| `type` | String | 否 | `ask` 或业务 Agent 类型 |
| `message` | String | 是 | 用户消息 |
| `attachments` | Array | 否 | 上传接口返回的附件信息 |
| `deep_think` | Boolean | 否 | 是否启用深度思考 |
| `online_search` | Boolean | 否 | 是否启用在线检索 |
| `response_format` | String | 否 | `events` 返回 NDJSON；其他值返回兼容纯文本流 |

```bash
curl -N -X POST "http://localhost:11001/api/v1/dih/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "chat_id": "chat-001",
    "model": "auto",
    "type": "ask",
    "message": "创建一个外链看板",
    "response_format": "events"
  }'
```

## 4. NDJSON 事件协议

当 `response_format=events` 时，响应类型为 `application/x-ndjson`，每行是独立 JSON 对象。

### 文本增量

```json
{"event":"delta","content":"正在分析..."}
```

### MCP 请求审批

```json
{
  "event": "approval_required",
  "data": {
    "request_id": "f46adbc2-e1c4-4a27-9e80-4a46f03eb42c",
    "tool_key": "local::dashboard_create",
    "tool_name": "dashboard_create",
    "source_type": "LOCAL",
    "server_name": "ZenVis 内置工具",
    "risk_level": "high",
    "description": "创建一个新的看板",
    "arguments_summary": "{\"request\":{...}}",
    "status": "PENDING",
    "expire_time": "2026-07-14T15:05:00.000+08:00"
  }
}
```

### MCP 状态更新

```json
{
  "event": "approval_updated",
  "data": {
    "request_id": "f46adbc2-e1c4-4a27-9e80-4a46f03eb42c",
    "status": "RUNNING",
    "approval_scope": "SESSION"
  }
}
```

### 完成和失败

```json
{"event":"done","message":{"sender":"ai","content":"...","parts":[]}}
{"event":"error","message":"AI 对话暂不可用，请稍后再试。"}
```

命中 `ASK` 时连接保持打开，不会提前发送 `done`。审批完成后原工具调用和模型循环继续。

## 5. Chat MCP 审批

```http
POST /api/v1/dih/mcp/approvals/{requestId}/decision
Content-Type: application/json
```

| decision | 页面操作 | 范围 |
|---|---|---|
| `approved` | 允许本次 | 当前 requestId |
| `approved_session` | 本会话始终允许 | 当前用户、chatId、精确 toolKey |
| `rejected` | 拒绝执行 | 当前 requestId |

```json
{
  "decision": "approved_session",
  "comment": "当前会话允许继续使用"
}
```

会话授权持久化到聊天删除为止，刷新页面和服务重启后仍有效。全局 `DENY` 始终优先。停止生成会取消本轮仍在等待的请求，但不会撤销已有会话授权。

审批卡片在最终消息中保存为 `mcp-approval` part；参数和返回结果由 MCP 调用日志 JSON 代码块各展示一次，卡片不重复显示 payload。

## 6. 模型列表

```http
GET /api/v1/dih/model/list
```

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": [
    {"model": "auto", "desc": "系统自动选择合适的模型"},
    {"model": "gpt-4.1", "desc": "gpt-4.1"}
  ]
}
```

模型目录来自当前 OpenAI 兼容配置和远端 `/v1/models`。DIH Chat 与 AI分析任务创建/编辑表单共用此接口。

## 7. 附件

### 上传

```http
POST /api/v1/dih/upload
Content-Type: multipart/form-data
```

表单字段名为 `file`。返回的附件对象应原样放入下一次 Chat 请求的 `attachments`。

### 图片预览

```http
GET /api/v1/dih/upload/{fileId}/preview
```

只允许预览当前用户上传且内容类型为图片的附件。

## 8. 注意事项

1. 前端新实现应使用 `response_format=events` 和 NDJSON 解析，不要按 SSE `data:` 行解析。
2. 客户端应按 `event` 分发，未知事件应忽略而不是终止整个流。
3. 同一轮可能出现多个审批请求，必须按 `request_id` 独立维护状态。
4. 后续文本增量不能覆盖已插入的审批 part。
5. `action-decision` 用于已保存消息的业务动作卡片，不用于 MCP 工具审批。
6. 详细权限规则见 [MCP 审批与 AI分析任务快速上手](../DIH/MCP审批与AI分析任务快速上手.md)。
