# MCP Client 与 MCP 工具智能体设计说明

## Context

ZenVis 已经通过 Spring AI MCP Server 对外提供本系统工具能力。本次设计在保留服务提供端能力的基础上，补充 MCP Client 能力，使 ZenVis 可以配置并连接多个外部 MCP 服务，并将外部服务暴露的工具交给 AI 在对话中调用。

目标：

- 支持在 MySQL 中管理多个 MCP 服务配置。
- 支持服务启停、刷新连接、查看工具、测试调用工具。
- 支持通过 AMIS 低代码页面完成配置管理。
- 支持 AI 聊天通过 `agent_mcp` 自动使用已连接 MCP 工具。
- 参考 Skill 管理流程，形成“配置管理 + 运行期加载 + Agent 注入”的闭环。

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
        └─ McpAgent(agent_mcp)
            - 构造 MCP 工具提示词
            - 构造 ToolCallbackProvider
            - 注入 AIChatService
                    │
                    ▼
              Spring AI ChatClient
              toolCallbacks(...)
                    │
                    ▼
              AI 自动选择并调用 MCP 工具
```

核心思路与 Skill 管理一致：

| 流程 | Skill 管理 | MCP 管理 |
|------|------------|----------|
| 配置来源 | `deploy/open_config/skill_config` 文件 | MySQL 表 `t_ai_mcp_server` |
| 管理接口 | `SkillController` | `McpController` |
| 运行期加载 | `SkillService.reload()` | `McpClientService.refresh()/refreshAll()` |
| Agent 注入 | 构造 Skill Prompt | 构造 MCP Prompt + ToolCallbackProvider |
| 前端管理 | AMIS 低代码页 | AMIS 低代码页 |

---

## 二、数据存储设计

### 表名

`t_ai_mcp_server`

实体文件：

`src/main/java/com/coolxer/dao/mysql/entity/McpServerConfig.java`

当前项目各环境配置了 `spring.jpa.hibernate.ddl-auto=update`，启动后由 JPA 自动建表或更新表结构。

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
| GET | `/agent/prompt` | 查看 MCP Agent 当前加载的工具提示词 |

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
- 查看 MCP Agent Prompt

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

### Agent 类型

新增聊天类型：

```text
agent_mcp
```

核心文件：

- `src/main/java/com/coolxer/service/dih/agent/McpAgent.java`
- `src/main/java/com/coolxer/controller/dih/ChatController.java`
- `src/main/java/com/coolxer/service/dih/AIChatService.java`

### 调用流程

```text
POST /api/v1/dih/chat
  type = agent_mcp
        │
        ▼
ChatController
  → mcpAgent.chat(...)
        │
        ▼
McpAgent
  → 检查是否有已连接工具
  → buildEnabledMcpPrompt()
  → getToolCallbackProvider()
        │
        ▼
AIChatService.chatWithSystemPromptAndTools(...)
  → ChatClient.prompt()
  → system(...)
  → user(...)
  → toolCallbacks(toolCallbackProvider)
  → stream().content()
```

### System Prompt 边界

`McpAgent` 的系统提示词要求：

- 当问题需要外部系统信息时，优先选择语义最匹配的 MCP 工具。
- 参数不足时先追问，不编造参数。
- 写入、删除、执行任务等副作用工具，先说明动作并请求用户确认。
- 工具返回后用中文归纳结果，保留关键字段、异常信息和下一步建议。

### 无可用工具时

如果没有启用且连接成功的 MCP 服务，`agent_mcp` 会直接返回：

```text
当前没有启用且连接成功的 MCP 服务，请先在 MCP 服务管理中配置并刷新服务。
```

---

## 七、前端入口

文件：

`zenvis-frontend/src/views/dih/index.vue`

新增智能体建议项：

```ts
{ type: 'agent_mcp', label: 'MCP 工具', icon: Tools }
```

MCP 工具智能体不挂载专属右侧面板，聊天中间栏保持主要交互区域。

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
- MCP Agent：把外部 MCP 工具注入 AI 对话。

---

## 九、验证方式

### 编译验证

```bash
cd zenvis-backend
mvn -DskipTests clean compile
```

### AMIS JSON 校验

```bash
cd zenvis-backend
jq empty deploy/open_config/mcp_config/index.json
```

### 前端类型检查

```bash
cd zenvis-frontend
npm run test
```

### 接口联调流程

1. 新增 MCP 服务。
2. 调用刷新连接接口。
3. 查询工具列表，确认工具数大于 0。
4. 调用工具测试接口，确认外部 MCP 工具可返回结果。
5. 调用 `/api/v1/dih/chat`，传入 `type=agent_mcp`，确认 AI 能根据问题触发工具调用。

### 聊天请求示例

```bash
curl -X POST "http://localhost:11001/api/v1/dih/chat" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "chat_id": "mcp-demo-001",
    "type": "agent_mcp",
    "model": "auto",
    "message": "帮我查询风险系统中 userId 为 10001 的风险记录"
  }'
```

---

## 十、已知边界与后续规划

### 当前边界

- 当前实现使用 Spring AI MCP SSE Client，适配 SSE MCP 服务。
- 工具调用权限依赖外部 MCP 服务自身鉴权，ZenVis 侧通过固定 headers 支持令牌透传。
- `agent_mcp` 已在 prompt 中约束副作用工具需先确认，但最终是否调用仍依赖模型工具调用行为。
- 已有环境需要手动添加“`MCP 服务`”低代码菜单和角色权限。

### 后续可演进方向

| 方向 | 说明 |
|------|------|
| 权限控制 | 按用户、角色、租户限制可见 MCP 服务和工具 |
| 工具审计 | 记录每次 AI 工具调用的服务、工具、参数、返回摘要和耗时 |
| 工具确认流 | 将副作用工具调用接入现有 `actionDecision` 确认机制 |
| 健康检查 | 定时刷新 MCP 连接状态，避免长时间状态过期 |
| 多传输协议 | 后续按 Spring AI MCP 能力扩展 Streamable HTTP 等传输 |
| 工具分组 | 在 AMIS 管理页支持按服务、标签、只读/写入风险筛选工具 |
| 故障降级 | MCP 服务不可用时给出可恢复建议或转为普通问答 |

