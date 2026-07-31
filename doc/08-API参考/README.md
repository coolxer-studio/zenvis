# API 参考

本目录集中说明 ZenVis 对外提供的两类接口能力：面向应用开发和系统集成的 RESTful API，以及面向 AI Agent 的 MCP Tool。文档内容以当前 `zenvis-backend`、`zenvis-frontend` 源码为准。

## 两类接口的区别

| 类型 | 调用方 | 传输与入口 | 主要用途 |
| --- | --- | --- | --- |
| [RESTful API](/08-API参考/RestfulAPI/概览与接入.md) | 浏览器、前端应用、第三方业务系统 | HTTP `/api/v1/**` | 登录、配置、检索、实体、看板、插件、DIH、报表和系统管理 |
| [MCP Tool](/08-API参考/MCPtool/总览与接入.md) | MCP Client、ZenVis Agent、Skill | SSE `/sse`、消息端点 `/mcp/message` | 让大模型以结构化工具调用方式查询数据或执行受控动作 |

`McpController` 虽然名称中包含 MCP，但它暴露的是用于管理 MCP 服务、策略、审批和审计的 REST API，详见 [MCP 服务管理](/08-API参考/RestfulAPI/MCP服务管理.md)。真正通过 MCP 协议暴露的工具见 [MCP Tool 文档](/08-API参考/MCPtool/总览与接入.md)。

## 阅读路线

### 应用或第三方系统接入

1. 阅读 [RESTful API 概览与接入](/08-API参考/RestfulAPI/概览与接入.md)，确认服务地址、认证方式、字段命名和统一响应。
2. 按业务域查阅对应接口文档。
3. 以运行中的 [Swagger UI](http://localhost:11001/swagger-ui/index.html) 和当前 DTO/VO 为最终字段依据。

### Agent 或 MCP Client 接入

1. 阅读 [MCP Tool 总览与接入](/08-API参考/MCPtool/总览与接入.md)，配置端点和 `MCP_BEARER_TOKEN`。
2. 按工具族查阅工具名、参数、返回值、默认审批策略和风险等级。
3. 阅读 [权限审批与运行约束](/08-API参考/MCPtool/权限审批与运行约束.md)，确认 Agent scope、Skill allowlist 和调用上限。

## 当前代码覆盖

- REST：23 个 Controller，共 183 个方法级映射。
- MCP：7 个内置工具类，共 71 个工具。
- 前端：8 个 API 服务模块及报表导出等页面内调用。

本目录只维护 API 契约。产品使用方法见[产品理念与使用](/01-产品理念与使用/README.md)，MCP 与 Agent 的整体设计见[AI 与数据智能](/04-AI与数据智能/README.md)，插件扩展见[插件开发与集成](/03-插件开发与集成/README.md)。

## 源码依据

| 内容 | 代码位置 |
| --- | --- |
| REST Controller | `zenvis-backend/src/main/java/com/coolxer/controller` |
| 请求与响应模型 | `zenvis-backend/src/main/java/com/coolxer/model` |
| REST 认证 | `AuthorityInterceptor`、`OpenApiConfig` |
| MCP Server 工具注册 | `McpServerToolConfiguration` |
| MCP 工具实现 | `ConfigMcpTool`、`ConfigValidationMcpTool`、`RetrievalMcpTool`、`AnalysisTaskMcpTool`、`DashboardMcpTool`、`MenuMcpTool`、`PushTaskMcpTool` |
| Agent 工具范围 | `AgentMcpToolService` |
| 前端调用封装 | `zenvis-frontend/src/service/api` |
