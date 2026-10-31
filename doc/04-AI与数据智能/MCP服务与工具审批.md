# MCP 服务与工具审批

本文面向管理员和运维人员，按“接入服务、发现工具、配置策略、处理审批、查询审计、排障”的顺序说明 ZenVis MCP 能力。

## MCP 在 ZenVis 中的角色

ZenVis 同时具备：

| 角色 | 用途 | 当前端点或来源 |
| --- | --- | --- |
| MCP Server | 按业务域向外部客户端暴露 80 个 ZenVis 本地工具 | 五组 `/mcp/{serviceCode}/sse`、`/mcp/{serviceCode}/message` |
| MCP Client | 连接数据库中配置的外部 MCP Server | 当前使用 SSE Client |
| 工具运行时 | 按 Agent、Skill 和策略组装实际工具集合 | 本地工具与已连接外部工具 |
| 权限网关 | 统一实施策略、审批、授权、状态和审计 | Chat、后台任务、MCP Server、管理端测试 |

普通问答 `ask` 不获得 MCP 工具。业务智能体、专项 Skill 和后台任务只有在工具范围与策略都允许时才能调用。

## 1. 接入外部 MCP 服务

在 MCP 服务管理页新增配置：

| 字段 | 运维要求 |
| --- | --- |
| `code` | 稳定且唯一；会参与工具身份和 Skill 白名单 |
| `name`、`description` | 描述真实来源和用途 |
| `base_url` | MCP Server 基础地址；有上下文路径时一并填写 |
| `sse_endpoint` | 当前 SSE 路径，通常为 `/sse` |
| `headers` | 固定 HTTP 请求头 JSON |
| `enabled` | 建议新服务验证后再启用 |
| 连接/请求超时 | 按目标系统响应时间配置 |

示例只使用占位值：

```json
{
  "code": "asset-center",
  "name": "资产中心 MCP",
  "base_url": "https://mcp.example.invalid/asset",
  "sse_endpoint": "/sse",
  "headers": "{\"Authorization\":\"Bearer <MCP_TOKEN>\"}",
  "enabled": false,
  "connect_timeout_seconds": 10,
  "request_timeout_seconds": 30
}
```

`headers` 当前作为固定配置保存和发送。不要把真实 Token 复制到文档、工单、截图或普通配置样例；部署环境应通过受控配置或密钥管理处理。

### 私网地址

`app.ai.mcp.allow-private-server-urls` 控制是否允许连接私网 MCP 地址。默认部署可能允许私网访问；面向不可信管理员或多租户环境时，应关闭该能力或在网络层限制 SSRF 可达范围。

## 2. 启用、刷新和发现工具

启用服务或修改 URL、SSE 路径、Headers、超时后执行刷新：

```text
读取配置
  → 建立 SSE Client
  → initialize
  → listTools
  → 更新连接状态与最后错误
```

- 启动或刷新失败不会阻断 ZenVis 后端启动；
- 失败服务标记为未连接并记录错误；
- 刷新会关闭旧 Client，再建立新连接；
- 外部工具清单是运行时内容，不能假设永久不变；
- 服务停用、删除或失联后，相关工具不应继续出现在 Agent 实际工具集合中。

先在管理页确认服务已连接、工具数大于 0，再配置 Agent 或 Skill。

## 3. 工具身份

策略和授权使用稳定工具键：

```text
本地工具：local::<toolName>
外部工具：external::<serverId>::<originalToolName>
```

外部工具还具有：

- 服务 `code`；
- MCP Server 返回的原始工具名；
- 经过规范化、用于模型调用的 Agent 工具名。

审批和授权绑定精确 `tool_key`，不能只按页面标题、格式化名称或相似名称匹配。修改服务或重新发现工具后，应重新核对策略页和 Agent 工具提示词。

## 4. 配置工具策略

| 策略 | 行为 |
| --- | --- |
| `ALLOW` | 无需人工审批即可执行，但仍记录调用 |
| `ASK` | 执行前创建审批请求 |
| `DENY` | 不执行底层工具，其他授权不能覆盖 |

本地工具通过代码声明默认策略和风险；外部工具只有明确标记只读时才适合采用低风险默认值，其他工具应按需询问。超级管理员可以在策略页覆盖默认策略。

建议：

- 查询、列表、详情和统计类工具评估后使用 `ALLOW`；
- 写入、改名、删除、执行、发布和任务触发类工具保持 `ASK`；
- 未识别、已下线或不应提供的工具使用 `DENY`；
- 调试完成后恢复临时策略，不要把高风险工具长期设为 `ALLOW`。

策略只是工具可执行性的一个条件。最终集合还会应用全局开关、Agent scope、Skill allowlist、服务连接状态和专属工具限制。

## 5. Chat 内联审批

聊天中的 `ASK` 调用流程：

```text
Agent 请求工具
  → 创建 PENDING 审计记录
  → NDJSON approval_required
  → 页面显示审批卡
  → 用户提交决定
  → approval_updated
  → 原工具调用继续或拒绝
  → 最终状态写回消息与审计
```

聊天支持：

| 决定 | 生效范围 |
| --- | --- |
| `approved` | 仅当前请求 |
| `approved_session` | 当前用户、当前会话、精确工具 |
| `approved_run` | 当前持久化 Agent Run、精确工具 |
| `rejected` | 拒绝当前请求 |

会话授权不会允许同一服务的其他工具，也不能覆盖全局 `DENY`。停止生成、断开本轮流或删除会话会清理相关等待状态；已完成的外部副作用不会自动回滚。

内置元数据配置、数据推送服务、可视化应用和数据大屏演示会对默认 `ASK` 写工具强制逐次审批，不使用会话授权，详见[业务智能体与工作流](/04-AI与数据智能/业务智能体与工作流.md)。

### 持久化 Agent Run 审批

启用 Agent Run 的业务智能体使用非阻塞审批：

```text
Run 请求 ASK 工具
  → 创建 PENDING 调用并写入 agent_run_id
  → Run 进入 WAITING_APPROVAL，释放 worker 租约
  → 审批事件写入 Run 证据，最终消息保存 mcp-approval 部件
  → 用户从运行卡或任务中心提交决定
  → approval.updated / tool.approval_updated
  → Run 重新入队并执行已批准调用
```

- Run 中的只读工具在未被全局 `DENY` 时自动执行，不弹出审批。
- 外部写入、改名、删除、发布等工具仍按有效策略决定是否询问。
- Agent Run 的审批等待默认为 24 小时，不占 worker，也不计入 Run 有效执行时间；超时后 Run 进入 `PAUSED`。
- `approved_run` 只允许当前 Run 内同一精确 `tool_key` 复用授权，不跨 Run、不跨会话，也不能覆盖 `DENY`。
- 审批决定必须幂等。重复提交同一决定不能产生第二次执行；终态请求不能重新获得执行权。
- 审批卡属于对应 AI 消息的 `mcp-approval` 结构化部件，不是只在实时流中存在、关闭页面就消失的临时 UI。卡片展示 `requestId`、工具名、说明、风险级别、授权范围、当前状态和决定按钮。
- 关闭页面不会取消等待或运行中的任务。重新打开会话时，优先使用 Run 快照的 `pendingInteraction.requestId` 查询审批并恢复卡片；只有旧快照没有该字段时才兼容 `pendingAction.requestId`。`tool.approval_required` 可能已经早于事件游标，不能作为唯一恢复来源。
- 服务重启时，Run 的 `PENDING`/`APPROVED` 审批继续保留；已经进入 `RUNNING` 但结果未落库的工具调用标记为 `UNKNOWN`，Run 暂停，禁止盲目重放可能已生效的写操作。

## 6. 后台任务审批

AI 分析任务复用同一策略与审计：

- `AUTO`：自动批准有效策略为 `ASK` 的调用并记录 `TASK_AUTO`；
- `MANUAL`：进入 `WAITING_APPROVAL`，等待任务所有者或超级管理员处理；
- `approved_task`：只对当前 `execution_id` 和精确工具持续允许；
- `DENY`：任何任务模式都不能覆盖。

任务审批应通过任务自己的审批入口处理，不能把 `approved_task` 提交到普通聊天审批接口。完整任务状态见[AI 分析任务与 Skill](/04-AI与数据智能/AI分析任务与Skill.md)。

## 7. 调用状态与审计

典型状态：

```text
PENDING
  → APPROVED
  → RUNNING
  → SUCCEEDED / FAILED

PENDING
  → REJECTED / EXPIRED / CANCELLED

有效策略 DENY
  → DENIED
```

终态由条件更新控制，避免重复审批或重复执行。审计可以按请求、工具、状态、策略、用户、任务和 execution 查询。

### 敏感数据

当前工具参数和结果默认保存到审计字段，达到数据库类型容量时才按 UTF-8 字节边界截断；原始结果字节数另行记录。错误摘要会做递归脱敏和长度控制，但不能据此假设全部参数和结果已经脱敏。

运维要求：

- 调用前避免在参数中传递不必要的密钥和个人信息；
- 外部工具控制返回规模和敏感字段；
- 限制审批与审计页面权限；
- 根据部署合规要求制定数据库保留、备份和清理策略；
- 截图和工单中对参数、结果、Headers 和错误详情二次脱敏。

## 8. MCP Server 运维

ZenVis 自带五个固定 MCP Server：

| code | 工具数 | SSE / Message |
| --- | ---: | --- |
| `retrieval` | 22 | `/mcp/retrieval/sse`、`/mcp/retrieval/message` |
| `config` | 10 | `/mcp/config/sse`、`/mcp/config/message` |
| `push-task` | 11 | `/mcp/push-task/sse`、`/mcp/push-task/message` |
| `visualization` | 21 | `/mcp/visualization/sse`、`/mcp/visualization/message` |
| `analysis-task` | 16 | `/mcp/analysis-task/sse`、`/mcp/analysis-task/message` |

服务端点和分组随代码固定发布；`app.ai.mcp.builtin-server.*` 只配置版本、请求超时和
SSE 保活间隔。五组端点共享 `app.security.mcp.bearer-token`，旧聚合端点不再提供。

`config` 服务中的 `config_rename` 和 `config_delete` 默认 `ASK/HIGH`，并只授予元数据配置智能体。重命名只允许同一父目录且禁止覆盖；非空目录删除必须显式携带 `recursive=true`。Meta 操作完成后会严格重载并刷新 Schema，文件系统已变更但重载失败时必须保留现场并报告 `changed=true/applied=false`，不得宣称成功。

本地 Server、外部 Client、Chat、后台任务和管理端测试调用共享工具策略与审计包装。
内置策略键仍为 `local::<toolName>`；审计额外记录工具所属内置服务 code。外部客户端不能自行构造“已经审批”的状态绕过策略。

## 常见问题

### 服务启用但未连接

检查基础地址、上下文路径、SSE endpoint、Headers JSON、证书、DNS、防火墙、私网地址策略和连接超时。

### 服务已连接但没有工具

确认外部 Server `listTools` 返回、工具能力是否启用、刷新是否成功，以及最后连接错误。

### 工具在总表中但 Agent 看不到

按顺序检查：

1. `app.ai.mcp.enabled`；
2. 服务是否启用并连接；
3. Agent scope；
4. Skill 工具 allowlist；
5. 全局策略是否为 `DENY`；
6. 目标 Agent 是否有专属工具限制。

可以通过 `/api/v1/dih/mcp/agent/prompt` 查看目标 Agent 当前实际工具提示词。

### Chat 或 Agent Run 没有出现审批卡

普通 Chat 先确认请求使用 `response_format=events`、有效策略确为 `ASK`、工具没有会话授权，以及前端能处理 `approval_required`。

持久化 Agent Run 还要检查：

1. Run 状态为 `WAITING_APPROVAL`，`pendingInteraction.kind=mcp_approval` 且 `requestId` 非空；旧快照才检查 `pendingAction` 的 `type=approval` 和 `requestId`；
2. 审批记录仍为 `PENDING`，没有被超时、拒绝或取消；
3. 会话消息含对应的 `mcp-approval` 部件，或重连逻辑能按 `requestId` 回填该部件；
4. 刷新或重新打开会话后审批卡仍存在。若只在实时流中出现、刷新后消失，说明审批部件未持久化或快照恢复未执行。

### 审批后工具重复执行

按 `request_id` 检查状态竞争和调用审计，不要重复提交决定。终态请求不应再次获得执行权。

### 审批一直等待

普通审批检查超时：

```properties
app.ai.mcp.approval.timeout-seconds=300
```

后台 `MANUAL` 任务可以持续等待，直到任务被决定或取消。

持久化 Agent Run 的审批默认等待 24 小时。若任务中心显示等待但聊天区没有审批卡，按“Chat 或 Agent Run 没有出现审批卡”的 Run 检查项确认快照、审批状态和 `mcp-approval` 部件。

### 连接错误影响后端启动

外部 MCP 初始化失败按设计不会阻断后端。排查该服务并刷新连接，不要通过停用整个 DIH 掩盖单服务故障。

## 运维验收

1. 新服务以停用状态保存，不包含明文真实凭据。
2. 启用和刷新后连接成功，工具清单与外部 Server 一致。
3. 只读与写入工具的策略符合风险。
4. Chat `ASK` 能完成单次允许、会话允许和拒绝。
5. 全局 `DENY` 不能被会话或任务授权覆盖。
6. 停止生成或取消任务会清理等待请求。
7. 审计可以还原策略、审批、执行、结果和错误全过程。
8. 目标 Agent 的实际工具提示词符合 scope 与 Skill allowlist。

## 关联文档

- [AI 与数据智能](/04-AI与数据智能/README.md)
- [AI 分析任务与 Skill](/04-AI与数据智能/AI分析任务与Skill.md)
- [AI 与 MCP 架构](/06-架构设计/AI与MCP架构.md)
- [MCP 服务管理 API](/08-API参考/RestfulAPI/MCP服务管理.md)
- [MCP Tool 权限、审批与运行约束](/08-API参考/MCPtool/权限审批与运行约束.md)
- [MCP Tool 总览与接入](/08-API参考/MCPtool/总览与接入.md)
