# API 参考

ZenVis API 以当前后端源码和运行时 Swagger UI 为准。历史 Markdown 文档只作为背景资料。

## 在线文档

启动服务后访问 Swagger UI：

```text
http://localhost:11001/swagger-ui/index.html
```

## AI 功能专题

| 文档 | 说明 |
| :--- | :--- |
| [第三方 REST API 对接指南](third-party-api-integration.md) | 产品接口地图、Bearer Token 调用、示例和排障 |
| [MCP 审批与 AI分析任务快速上手](../DIH/MCP审批与AI分析任务快速上手.md) | 产品概念、操作流程、权限顺序、验收与排障 |
| [MCP Client 与业务 Agent 设计](../DIH/MCP-Client-Agent-Design.md) | MCP 客户端、策略状态机、Chat 审批与任务调度实现 |
| [AnalysisTaskController](../api接口文档/AnalysisTaskController.md) | 后台 AI分析任务、队列和任务审批接口 |
| [McpController](../api接口文档/McpController.md) | MCP 服务、策略、通用审批和调用审计接口 |
| [SkillController](../api接口文档/SkillController.md) | Skill 扫描、启停、Agent 加载和任务选项接口 |

## 全局检索专题

| 文档 | 说明 |
| :--- | :--- |
| [Retrieval 模块快速上手](retrieval-module.md) | 产品行为、前后端状态流、规则兼容、元数据和排障 |
| [RetrievalController](../api接口文档/RetrievalController.md) | 检索、过滤器和元数据 REST 契约 |

## 服务管理专题

| 文档 | 说明 |
| :--- | :--- |
| [业务应用服务接口](../api接口文档/BusinessServiceController.md) | 无认证心跳/事件上报、受认证管理查询、状态与保留规则 |
| [服务管理使用手册](../使用手册/功能说明-服务管理.md) | 数据推送服务、AI分析任务和业务应用服务的页面使用说明 |

## 当前接口模块

当前有效 Markdown 文档如下：

| 文档 | 说明 |
| :--- | :--- |
| [AboutController](../api接口文档/AboutController.md) | 系统关于信息 |
| [AnalysisTaskController](../api接口文档/AnalysisTaskController.md) | AI分析任务 |
| [BusinessServiceController](../api接口文档/BusinessServiceController.md) | 业务应用服务注册、事件与只读管理查询 |
| [ChatController](../api接口文档/ChatController.md) | AI 对话、上传与预览 |
| [ChatSessionController](../api接口文档/ChatSessionController.md) | AI 对话会话 |
| [ConfigController](../api接口文档/ConfigController.md) | 配置文件管理 |
| [DashboardController](../api接口文档/DashboardController.md) | 看板管理 |
| [DihController](../api接口文档/DihController.md) | DIH 能力 |
| [EntityCoreController](../api接口文档/EntityCoreController.md) | 动态实体数据 |
| [EntityCountController](../api接口文档/EntityCountController.md) | 实体统计 |
| [HomeBoardController](../api接口文档/HomeBoardController.md) | 首页看板 |
| [LoginController](../api接口文档/LoginController.md) | 登录认证 |
| [McpController](../api接口文档/McpController.md) | MCP 服务管理 |
| [MenuController](../api接口文档/MenuController.md) | 菜单管理 |
| [PluginController](../api接口文档/PluginController.md) | 插件管理 |
| [PushTaskController](../api接口文档/PushTaskController.md) | 推送任务代理 |
| [RetrievalController](../api接口文档/RetrievalController.md) | 数据检索 |
| [RoleController](../api接口文档/RoleController.md) | 角色管理 |
| [SkillController](../api接口文档/SkillController.md) | Skill 管理 |
| [UserController](../api接口文档/UserController.md) | 用户管理 |
| [VectorStoreQueryController](../api接口文档/VectorStoreQueryController.md) | 插件文档 RAG 管理 |

## 通用对接规则

### 响应格式

所有普通 JSON 接口统一返回 `ResponseWrap`：

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {}
}
```

前端应以 `status === 0` 判断业务成功。HTTP 200 只表示传输成功，不代表业务成功。

### 字段命名

后端 Jackson 全局使用 `snake_case`。接口 wire 字段统一按 `snake_case` 对接，例如：

```json
{
  "per_page": 10,
  "order_by": "update_time",
  "order_dir": "desc"
}
```

前端页面层需要驼峰字段时，应在 service/mapper 层集中转换。

### 分页格式

新接口优先使用：

```json
{
  "page": 1,
  "per_page": 10,
  "order_by": "update_time",
  "order_dir": "desc"
}
```

`per_page` 是推荐 wire 字段；后端同时兼容历史驼峰字段 `perPage`，GET query/form 和 JSON body 两种传参方式都可识别。
但 Controller 上显式声明了 `@RequestParam("perPage")`、`sourceType` 等名称的接口，以接口专题文档和 Swagger 中的查询参数名为准。JSON 请求与响应字段仍使用 `snake_case`。

标准分页响应优先使用：

```json
{
  "rows": [],
  "total": 100,
  "page": 1,
  "per_page": 10
}
```

Retrieval 列表接口使用 `{ "total": 100, "datalist": [] }`；`POST /retrieval/do` 还会返回查询上下文 `token`。其 `page/size` 是请求字段，不会原样放入响应。

### 鉴权方式

普通 `/api/v1/**` Web API 同时支持两种鉴权方式：

- Web 前端默认使用服务端 Session/Cookie 鉴权，登录相关接口、系统公开信息和健康检查接口按拦截器配置放行。
- 第三方系统可配置 `API_BEARER_TOKEN` 后使用 `Authorization: Bearer <token>` 直接调用普通 REST API。调用身份由 `API_BEARER_USER` 映射到系统用户，详见 [第三方 REST API 对接指南](third-party-api-integration.md)。

业务应用服务只有 `POST /api/v1/public/business-services/heartbeat` 和 `POST /api/v1/public/business-services/events` 两个精确路径无需认证。相似路径、其他 HTTP 方法及其管理查询接口均不放行。

MCP Server SSE/消息端点使用独立的 `MCP_BEARER_TOKEN`，由 MCP 专用拦截器校验，不与普通 REST API token 混用。

### RESTful 演进

现有 `/api/v1` 路径保持兼容。新增接口或新增别名时优先使用：

- `POST /resource`
- `GET /resource`
- `GET /resource/{id}`
- `PUT/PATCH /resource/{id}`
- `DELETE /resource/{id}`

命令型动作保留 `POST`，例如 `enable`、`disable`、`install`、`enqueue`。

## 对接注意

当前已移除不存在的 `POST /api/v1/retrieval/criteria` 前端封装，检索以 `POST /api/v1/retrieval/do` 为准。
