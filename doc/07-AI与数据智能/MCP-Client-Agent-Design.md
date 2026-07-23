# MCP Client 与业务 Agent 工具集成设计说明

## Context

ZenVis 已经通过 Spring AI MCP Server 对外提供本系统工具能力。本次设计在保留服务提供端能力的基础上，补充 MCP Client 能力，使 ZenVis 可以配置并连接多个外部 MCP 服务，并将外部服务暴露的工具交给 AI 在对话中调用。

目标：

- 支持在 MySQL 中管理多个 MCP 服务配置。
- 支持服务启停、刷新连接、查看工具、测试调用工具。
- 支持通过 AMIS 低代码页面完成配置管理。
- 支持业务 Agent 按需使用允许范围内的本地或外部 MCP 工具；普通问答保持无工具边界。
- 支持通过配置控制每个业务 Agent 可使用的 MCP 服务范围。
- 为本地工具和外部 MCP 工具提供统一的 `ALLOW / ASK / DENY` 审批策略、调用审计与并发安全状态机。
- DIH Chat 在原 AI 消息内展示审批卡片，审批完成后继续当前模型工具循环。
- 参考 Skill 管理流程，形成“配置管理 + 运行期加载 + 工具上下文注入”的闭环。

---

## 一、总体架构

```text
外部 MCP 服务 A/B/C
        │
        │ SSE
        ▼
McpClientServiceImpl
  - 从 MySQL 读取 MCP 服务配置
  - 创建 McpSyncClient
  - initialize/listTools
  - 维护运行期 clients 注册表
        │
        ├─ McpController
        │   - 服务管理
        │   - 工具查看
        │   - 工具测试调用
        │
        └─ AgentMcpToolService
            - 按业务 Agent 类型解析 MCP scope
            - 构造 MCP 工具提示词
            - 构造 ToolCallbackProvider
            - 注入业务 Agent / AnalysisTask（普通问答不注入）
                    │
                    ▼
              Spring AI ChatClient toolCallbacks(...)
                    │
                    ▼
              AI 在业务语境中按需调用 MCP 工具
```

核心思路与 Skill 管理一致：

| 流程 | Skill 管理 | MCP 管理 |
|------|------------|----------|
| 配置来源 | `deploy/open_config/skill_config` 文件 | MySQL 表 `t_ai_mcp_server` |
| 管理接口 | `SkillController` | `McpController` |
| 运行期加载 | `SkillService.reload()` | `McpClientService.refresh()/refreshAll()` |
| Agent 注入 | 构造 Skill Prompt | 按业务 Agent 构造 MCP Prompt + ToolCallbackProvider |
| 前端管理 | AMIS 低代码页 | AMIS 低代码页 |

---

## 二、数据存储设计

### 表名

`t_ai_mcp_server`

实体文件：

`src/main/java/com/coolxer/dao/mysql/entity/McpServerConfig.java`

当前项目各环境配置了 `spring.jpa.hibernate.ddl-auto=update`，启动后由 JPA 自动建表或更新表结构。

审批能力新增四张表：

| 表名 | 说明 |
|------|------|
| `t_ai_mcp_tool_policy` | 工具发现信息、默认策略、人工覆盖、可用状态和最后发现时间 |
| `t_ai_mcp_invocation` | 参数摘要及校验值、策略快照、审批人、调用状态、结果摘要和耗时审计 |
| `t_ai_mcp_chat_tool_grant` | DIH Chat 内按用户、chatId 和 toolKey 持久化的会话授权 |
| `t_ai_mcp_task_tool_grant` | 按 AI分析任务 executionId 和 toolKey 持久化的任务授权 |

AI分析任务另通过 `t_ai_analysis_task_skill` 保存任务与 Skill ID 的关联。任务只保存 ID，运行时读取最新 Skill 内容。

工具唯一键固定为 `local::<toolName>` 或 `external::<serverId>::<originalToolName>`。参数、结果和错误在落库前递归打码并截断；参数另存 SHA-256 摘要，用于两阶段调用重试时防止替换参数。

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Integer | 主键，继承 `BaseEntity` |
| code | varchar(64) | 服务标识，唯一，用于区分多个 MCP 服务，也会参与 AI 工具名前缀 |
| name | varchar(128) | 服务名称 |
| description | varchar(512) | 服务描述 |
| base_url | varchar(512) | MCP SSE 基础地址，如 `http://127.0.0.1:11002` |
| sse_endpoint | varchar(512) | SSE endpoint，默认 `/sse` |
| headers | text | 固定 HTTP 请求头，JSON 对象格式 |
| enabled | bit | 是否启用 |
| request_timeout_seconds | int | MCP 请求超时秒数，默认 30 |
| connect_timeout_seconds | int | HTTP 连接超时秒数，默认 10 |
| connected | bit | 最近一次连接是否成功 |
| last_error | text | 最近一次连接错误 |
| last_connected_time | timestamp | 最近一次连接成功时间 |
| create_time/update_time/create_by/update_by/is_delete | - | 继承 `BaseEntity` |

### 配置示例

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

如果外部 MCP 服务部署在带上下文路径的应用下，`base_url` 需要包含上下文路径，例如：

```text
base_url = http://host:port/zenvis
sse_endpoint = /sse
```

---

## 三、运行期 MCP Client 注册表

核心实现：

`src/main/java/com/coolxer/service/dih/mcp/McpClientServiceImpl.java`

### 启动初始化

`McpClientServiceImpl` 在 `@PostConstruct` 中加载所有启用的 MCP 服务：

```text
findByEnabledTrueOrderByIdAsc()
  → refresh(id)
  → createClient(config)
  → client.initialize()
  → client.listTools()
  → clients.put(id, ClientHolder)
```

初始化失败不会阻断后端启动，只会记录错误并把服务标记为未连接。

### 刷新连接

刷新单个服务时会先关闭旧 client，再重新初始化：

```text
closeClient(id)
  → createClient(config)
  → initialize
  → listTools
  → 更新 connected/lastError/lastConnectedTime
```

### HTTP Headers

`headers` 字段要求是 JSON 对象，例如：

```json
{
  "Authorization": "Bearer xxx",
  "X-Tenant-Id": "tenant-a"
}
```

服务层会解析后通过 `HttpClientSseClientTransport.httpRequestCustomizer(...)` 注入到 MCP HTTP 请求中。

### 工具命名

外部 MCP 服务可能存在同名工具，因此 AI 侧工具名会加服务标识前缀：

```text
原始工具名: query_user
服务 code: risk-system
AI 工具名: risk_system_query_user
```

工具名会经过 `McpToolUtils.format(...)` 规范化，并限制最大长度 64。

---

## 四、管理接口设计

控制器：

`src/main/java/com/coolxer/controller/dih/McpController.java`

基础路径：

`/api/v1/dih/mcp`

| HTTP 方法 | 路径 | 说明 |
|----------|------|------|
| GET | `/servers/list` | 分页查询 MCP 服务 |
| POST | `/servers/add` | 新增 MCP 服务 |
| POST | `/servers/{id}/update` | 更新 MCP 服务 |
| DELETE | `/servers/{id}` | 删除 MCP 服务 |
| GET | `/servers/{id}/view` | 查询 MCP 服务详情 |
| POST | `/servers/{id}/enable` | 启用 MCP 服务 |
| POST | `/servers/{id}/disable` | 停用 MCP 服务 |
| POST | `/servers/{id}/refresh` | 刷新单个 MCP 服务连接 |
| POST | `/servers/refresh` | 刷新全部已启用 MCP 服务连接 |
| GET | `/tools` | 查询已连接 MCP 工具列表，可选 `serverId` |
| POST | `/tools/call` | 测试调用 MCP 工具 |
| GET | `/tools/policies/list` | 分页查询工具策略 |
| POST | `/tools/policies/update` | 修改或恢复单工具策略，仅超级管理员 |
| POST | `/tools/policies/bulk-update` | 批量修改策略，仅超级管理员 |
| GET | `/approvals/list` | 查询当前用户可处理的 `PENDING` 审批 |
| GET | `/approvals/{requestId}/view` | 查询审批详情 |
| POST | `/approvals/{requestId}/decision` | `approved` 单次允许、`approved_session` 当前聊天持续允许或 `rejected` 拒绝当前请求 |
| GET | `/invocations/list` | 查询工具调用审计 |
| GET | `/agent/prompt` | 查看指定业务 Agent 当前加载的 MCP 工具提示词 |

工具测试调用使用原始 MCP 工具名，不使用 AI 前缀工具名：

```json
{
  "server_id": 1,
  "name": "query_user",
  "arguments": {
    "userId": "10001"
  }
}
```

`/tools/call` 命中 `ASK` 时采用两阶段协议：首次返回 `requestId` 且不执行；批准后使用相同 `requestId` 和原参数重试。服务端校验参数摘要并以条件更新保证最多执行一次。

---

## 五、AMIS 低代码管理页面

配置文件：

`deploy/open_config/mcp_config/index.json`

页面能力：

- 新增 MCP 服务
- 查询/筛选 MCP 服务
- 编辑服务配置
- 启用/停用服务
- 刷新单个服务连接
- 刷新全部服务连接
- 查看已连接工具
- 测试调用工具
- 查看业务 Agent MCP 工具提示词
- 管理全部本地和外部工具的审批策略
- 逐行或跨页批量设置允许、询问、禁止或恢复默认
- 审批队列只处理当前待审批请求，所有终态在长期调用审计中查看

默认菜单：

新环境初始化时会在“服务管理”下创建“`MCP 服务`”菜单，菜单参数为 `mcp`：

```text
服务管理 / MCP 服务
type = LOW_CODE_PAGE
params = mcp
```

已有环境不会自动改写菜单和角色权限，可在“菜单管理”中手动添加：

```text
名称: MCP 服务
类型: 低代码页面
参数: mcp
父菜单: 服务管理
```

---

## 六、AI 调用链路设计

MCP 不再作为独立聊天 Agent。它是业务 Agent 和后台分析任务的通用工具能力层；普通问答 `ask` 不解析 scope，也不注入工具。

核心文件：

- `src/main/java/com/coolxer/service/dih/mcp/AgentMcpToolService.java`
- `src/main/java/com/coolxer/service/dih/mcp/McpClientServiceImpl.java`
- `src/main/java/com/coolxer/controller/dih/ChatController.java`
- `src/main/java/com/coolxer/service/dih/AIChatService.java`
- `src/main/java/com/coolxer/service/dih/AgentLlmService.java`

### 调用流程

```text
POST /api/v1/dih/chat
  type = ask / agent_data_access / agent_data_visualization / ...
        │
        ▼
ChatController
  → AgentMcpToolService.resolve(type)
        │
        ▼
业务 Agent / AgentLlmService
  → 注入 MCP system prompt
  → 注入 ToolCallbackProvider
        │
        ▼
Spring AI ChatClient.toolCallbacks(...)
```

### MCP 工具 Prompt 边界

注入到业务 Agent 的 MCP 工具提示词要求：

- 仅当用户问题确实需要外部系统数据、动作或上下文时才调用工具。
- 参数不足时先追问，不编造参数。
- `DENY` 工具不注入 Agent；`ASK` 工具会标记为需要审批，并由统一回调在执行前阻塞等待审批。
- 工具返回后用中文归纳结果，保留关键字段、异常信息和下一步建议。

### 策略推导与状态机

- 本地查询、列表、详情、统计、校验和模拟类默认 `ALLOW/LOW`；写入、删除、执行和任务触发类默认 `ASK/HIGH`。每个 `@Tool` 必须通过 `@McpToolApproval` 显式声明默认策略与风险，遗漏会导致测试失败。
- 外部工具仅在 MCP `readOnlyHint=true` 时默认 `ALLOW`，其他情况默认 `ASK`。
- 未识别风险强制 `ASK`，人工覆盖优先于默认策略。
- `ALLOW` 直接进入 `running`；`ASK` 创建 `pending` 请求并等待；`DENY` 直接进入 `denied` 且不触达底层工具。
- 状态集合为 `pending / approved / running / succeeded / failed / rejected / denied / expired / cancelled`，终态和执行权通过条件更新竞争，避免重复审批或重复执行。
- 拒绝、超时和取消会向 Agent 返回结构化拒绝结果，使模型可以继续解释；MCP Server 调用则返回错误结果。

### Scope 配置

普通问答 `ask` 不解析 MCP scope，也不会注入任何本地或外部工具。默认所有业务 Agent 都可以使用全部已连接 MCP 服务，可通过配置收窄范围：

```properties
app.ai.mcp.enabled=true
app.ai.mcp.agent-scopes.default=*
app.ai.mcp.agent-scopes.agent_data_access=risk,asset
app.ai.mcp.agent-scopes.agent_data_visualization=none
app.ai.mcp.approval.timeout-seconds=300
```

scope 值为 `none/off/false/disabled` 时，该业务 Agent 不注入 MCP 工具。
`app.ai.mcp.agent-scopes.ask` 不产生效果；旧 `mcp_agent` 请求会兼容转换为无工具的普通问答，同时保留对历史别名 `agent_mcp` 的兼容。

---

## 七、DIH Chat 内联审批

聊天 NDJSON 协议增加 `approval_required` 和 `approval_updated`，事件的 `data` 包含请求 ID、工具来源、风险、脱敏参数、状态和过期时间。工具回调从 Spring AI `toolContext` 获取用户、chatId、Agent 类型、轮次 ID 和事件发送器。

命中 `ASK` 后，当前流不发送 `done`：前端在当前 AI 消息 parts 中插入 `mcp-approval` 卡片，用户可选择“允许本次”“本会话始终允许”或“拒绝执行”，随后原工具回调被唤醒并继续模型循环。“本会话始终允许”只对当前用户、chatId 和精确 toolKey 生效，刷新或服务重启后仍保留；全局 `DENY` 始终优先。同一轮可维护多个独立请求；停止生成和流取消会清理本轮仍在等待的请求，但不撤销已经建立的会话授权。最终消息保存审批 part 和审批范围，重新加载会话仍能看到决策及最终状态。

工具参数和返回结果由普通 MCP 调用日志以 JSON 代码块展示；审批卡片不重复展示 payload，只承载来源、风险、说明、倒计时、操作和状态。

---

## 八、与当前 MCP Server 能力的关系

当前 ZenVis 仍然是 MCP Server，已有配置位于：

`src/main/resources/application-dev.properties`

关键配置：

```properties
spring.ai.mcp.server.name=zenvis-mcp-server
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.type=SYNC
spring.ai.mcp.server.sse-endpoint=/sse
spring.ai.mcp.server.sse-message-endpoint=/mcp/message
spring.ai.mcp.server.capabilities.tool=true
```

服务端工具注册位于：

`src/main/java/com/coolxer/configuration/mcp/McpServerToolConfiguration.java`

因此本次设计后，ZenVis 同时具备：

- MCP Server：把本系统 `@Tool` 暴露给外部客户端。
- MCP Client：连接其他系统暴露的 MCP Server。
- MCP 工具上下文：把外部 MCP 工具按业务 Agent scope 注入 AI 对话。
- MCP 权限网关：本地 Server、外部 Client、后台任务、测试调用和 DIH Chat 共用同一策略与审计链路。

---

## 九、AI分析任务调度、Skill 与审批

AI分析任务是一种一次性后台 Agent 执行。创建接口必须明确传入 `approval_mode`，并可通过
`skill_ids` 选择任意已扫描且已启用的 Skill。任务保存 Skill ID，实际执行前再次读取最新
Skill 内容并校验启用状态；Skill 已停用或删除时任务直接进入 `FAILED`，不会静默跳过。
创建和编辑表单的模型列表复用 `/api/v1/dih/model/list`，与 DIH Chat 保持一致；`auto` 表示运行时由系统选择。

任务支持立即排队或设置一次性 `scheduled_time`。到期计划任务优先于普通任务，普通任务按
优先级降序、创建时间升序执行。执行由后台线程池完成，HTTP `run-once` 仅认领并提交任务，
页面关闭不会中断执行。`RUNNING` 或 `WAITING_APPROVAL` 任务在服务重启后会生成新的
`execution_id` 并从头重新入队，旧 execution 的审批与工具授权会失效。

审批模式：

- `AUTO`：全局 `ALLOW` 正常执行；全局 `ASK` 自动批准并记录 `TASK_AUTO`；全局 `DENY` 始终禁止。
- `MANUAL`：全局 `ASK` 创建无限期待审批请求，任务进入 `WAITING_APPROVAL` 并释放普通执行槽。
- `approved`：只允许当前 requestId。
- `approved_task`：仅当前 execution、精确 toolKey 持续允许，审计范围为 `TASK_RUN`。
- `rejected`：只拒绝当前工具调用，底层工具不执行，Agent 获得结构化拒绝结果后继续分析。

AI分析任务审批仅允许任务创建人或超级管理员处理。任务完成、取消或重新入队后会清理当前
execution 的工具授权。审批与审计接口支持 `analysisTaskId`、`executionId` 关联和筛选。

关键配置：

```properties
app.ai.analysis-task.max-concurrency=1
app.ai.analysis-task.max-suspended=20
app.ai.analysis-task.dispatch-delay-ms=5000
```

---

## 十、验证方式

### 编译验证

```bash
cd zenvis-backend
mvn -DskipTests clean compile
```

### AMIS JSON 校验

```bash
cd zenvis
jq empty deploy/open_config/mcp_config/index.json
```

AI分析任务已迁移到 DIH 原生前端，不再维护对应的 AMIS JSON。

### 前端类型检查与构建

```bash
cd zenvis-frontend
npm run test
npm run build:pro
```

### 接口联调流程

1. 新增 MCP 服务。
2. 调用刷新连接接口。
3. 查询工具列表，确认工具数大于 0。
4. 调用工具测试接口，确认外部 MCP 工具可返回结果。
5. 调用 `/api/v1/dih/chat`，传入 `type=ask` 或具体业务 Agent，确认 AI 能根据问题触发工具调用。

### 聊天请求示例

```bash
curl -X POST "http://localhost:11001/api/v1/dih/chat" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "chat_id": "mcp-demo-001",
    "type": "agent_data_access",
    "model": "auto",
    "message": "帮我查询风险系统中 userId 为 10001 的风险记录"
  }'
```

---

## 十一、已知边界与后续规划

### 当前边界

- 当前实现使用 Spring AI MCP SSE Client，适配 SSE MCP 服务。
- 工具调用权限依赖外部 MCP 服务自身鉴权，ZenVis 侧通过固定 headers 支持令牌透传。
- MCP 工具 prompt 已约束副作用工具需先确认，但最终是否调用仍依赖模型工具调用行为。
- 已有环境需要手动添加“`MCP 服务`”低代码菜单和角色权限。

### 后续可演进方向

| 方向 | 说明 |
|------|------|
| 细粒度策略 | 在全局工具策略之上按角色或租户增加可见性与上限 |
| 授权撤销 | 为 Chat 会话授权提供可视化查询和主动撤销入口 |
| 审计导出 | 按任务、用户、工具和时间范围导出脱敏记录 |
| 健康检查 | 定时刷新 MCP 连接状态，避免长时间状态过期 |
| 多传输协议 | 后续按 Spring AI MCP 能力扩展 Streamable HTTP 等传输 |
| 工具分组 | 在 AMIS 管理页支持按服务、标签、只读/写入风险筛选工具 |
| 故障降级 | MCP 服务不可用时给出可恢复建议或转为普通问答 |
