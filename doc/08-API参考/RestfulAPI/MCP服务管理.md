# MCP 服务管理 REST API

本页说明 `McpController` 的 19 个 HTTP 接口，用于管理外部 MCP Server、查看工具、配置审批策略、处理审批和查询调用审计。

这些接口属于 RESTful API，基础路径是 `/api/v1/dih/mcp`。MCP Client 连接 ZenVis Server 时使用的是 `/sse` 和 `/mcp/message`，详见 [MCP Tool 总览与接入](/08-API参考/MCPtool/总览与接入.md)。

## 外部 MCP Server

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| GET | `/api/v1/dih/mcp/servers/list` | `McpServerSearchDto` | 分页外部服务配置 |
| POST | `/api/v1/dih/mcp/servers/add` | `McpServerDto` | 新增并初始化连接 |
| POST | `/api/v1/dih/mcp/servers/{id}/update` | `McpServerDto` | 更新服务配置 |
| DELETE | `/api/v1/dih/mcp/servers/{id}` | 服务 ID | 删除服务 |
| GET | `/api/v1/dih/mcp/servers/{id}/view` | 服务 ID | 服务详情与状态 |
| POST | `/api/v1/dih/mcp/servers/{id}/enable` | 服务 ID | 启用并连接 |
| POST | `/api/v1/dih/mcp/servers/{id}/disable` | 服务 ID | 停用连接 |
| POST | `/api/v1/dih/mcp/servers/{id}/refresh` | 服务 ID | 刷新单个连接与工具 |
| POST | `/api/v1/dih/mcp/servers/refresh` | 无 | 刷新全部连接 |

`McpServerDto`：

| 字段 | 说明 |
| --- | --- |
| `code` | 稳定且唯一的服务代码，用于工具命名和 Skill 边界 |
| `name`、`description` | 展示名称和说明 |
| `base_url` | MCP Server 基础地址 |
| `sse_endpoint` | SSE 路径，未指定时通常为 `/sse` |
| `headers` | 固定请求头 JSON，例如 `{"Authorization":"Bearer ${...}"}` |
| `enabled` | 是否启用 |
| `request_timeout_seconds` | 请求超时 |
| `connect_timeout_seconds` | 连接超时 |
| `source` | 配置来源，用于插件资源追踪 |

更新 URL、SSE 路径、请求头或超时后应刷新连接。`app.ai.mcp.allow-private-server-urls` 控制是否允许连接私网地址；面向不可信管理员的部署应关闭或在网络层限制 SSRF 风险。

## 工具发现与测试调用

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| GET | `/api/v1/dih/mcp/tools` | query `serverId?` | 外部 MCP 工具列表 |
| POST | `/api/v1/dih/mcp/tools/call` | `McpToolCallDto` | 管理端测试调用 |

测试调用请求：

```json
{
  "server_id": 1,
  "server_code": "asset-center",
  "name": "asset_query",
  "arguments": {
    "keyword": "host"
  },
  "approval_request_id": null
}
```

优先使用 `server_id` 或稳定 `server_code` 定位服务，`name` 使用服务端原始工具名。工具进入 Spring AI 后可能按 `serverCode_toolName` 格式化，Agent 实际可见名称以工具列表和提示词接口为准。

命中 `ASK` 时首次调用创建审批请求而不执行底层工具；批准后平台流程携带 `approval_request_id` 继续原调用。不要由普通客户端伪造已批准请求 ID。

## 工具策略

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| GET | `/api/v1/dih/mcp/tools/policies/list` | `page`、`perPage`、`keyword?`、`sourceType?`、`policy?`、`available?` | 策略分页 |
| POST | `/api/v1/dih/mcp/tools/policies/update` | `tool_key`、`policy` | 更新单工具策略 |
| POST | `/api/v1/dih/mcp/tools/policies/bulk-update` | `tool_keys`、`policy` | 批量更新策略 |

策略值：

- `ALLOW`：无需人工审批。
- `ASK`：调用前创建审批请求。
- `DENY`：工具不进入可用集合或直接拒绝。

策略写接口只允许超级管理员。`tool_key` 是稳定策略键，本地工具形如 `local::<toolName>`，外部工具由来源服务和原始名称共同确定。完整规则见[权限审批与运行约束](/08-API参考/MCPtool/权限审批与运行约束.md)。

## 审批与调用审计

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| GET | `/api/v1/dih/mcp/approvals/list` | `page`、`perPage` | 当前用户可处理的待审批请求 |
| GET | `/api/v1/dih/mcp/approvals/{requestId}/view` | 请求 ID | 审批详情 |
| POST | `/api/v1/dih/mcp/approvals/{requestId}/decision` | `decision`、`comment?` | 提交聊天/通用工具审批 |
| GET | `/api/v1/dih/mcp/invocations/list` | `McpInvocationSearchDto` | 历史调用审计 |

聊天审批决定：

| 决定 | 作用域 |
| --- | --- |
| `approved` | 仅当前请求 |
| `approved_session` | 当前用户、会话和精确工具 |
| `rejected` | 拒绝当前请求 |

AI 分析任务的 `approved_task` 必须通过任务自己的审批接口提交，见 [DIH 与 AI](/08-API参考/RestfulAPI/DIH与AI.md#analysistaskcontroller)。

审计查询支持：

- `keyword`
- `request_id`
- `channel`
- `status`
- `policy`
- `approval_scope`
- `requester_user_id`
- `decision_by`
- `analysis_task_id`
- `execution_id`
- `page`、`per_page`

访问审批详情和提交决定时，服务端会校验请求归属、任务所有者或超级管理员权限、当前状态和允许的审批范围。

## Agent 工具提示词

| 方法 | 路径 | 输入 | 返回/用途 |
| --- | --- | --- | --- |
| GET | `/api/v1/dih/mcp/agent/prompt` | query `agentType?` | `SingleValueVo`，查看该 Agent 当前可用 MCP 服务与工具提示词 |

该结果已应用全局开关、Agent scope、Skill 工具边界、策略 `DENY`、数据可视化专属工具限制和外部服务可用状态，因此比静态工具总表更能反映某个 Agent 的实际运行视图。

## 管理建议

- 外部服务 Token 放在受保护配置或环境变量引用中，不直接写入文档或日志。
- 新服务先保持停用，验证 URL、请求头和网络边界后再启用。
- 测试调用写工具仍应遵守 `ASK`，不要为了调试永久改成 `ALLOW`。
- 修改策略后通过工具提示词接口确认目标 Agent 的实际工具集合。
- 审计记录保存参数、结果或错误信息时要按部署要求处理敏感字段。
