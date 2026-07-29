# API 参考

本主题从统一调用约定延伸到逐接口完整契约：

- [后端 API 完整地图](api-reference.md)
- [第三方 REST API 对接指南](third-party-api-integration.md)
- [前端 API 接口说明](前端API接口文档.md)
- [Controller 详细接口目录](控制器/)
- [TypeScript Skill API 示例](示例/skill-api.ts)

## 基础信息

| 项目 | 值 |
| --- | --- |
| 后端地址 | `http://localhost:11001` |
| Controller 前缀 | `/api/v1` |
| 容器 Web 代理前缀 | `/zenvis/api/v1` |
| Swagger UI | `/swagger-ui/index.html` |
| OpenAPI JSON | `/v3/api-docs` |
| JSON 命名 | `snake_case` |

本文给出稳定的领域入口和调用约定。字段级模型以 Swagger 和当前 DTO/VO 为准。

## 认证

### Session/Cookie

浏览器登录后使用 `JSESSIONID`。前端 Axios 开启 `withCredentials`，同一会话的后续请求自动携带 Cookie。

### REST API Bearer Token

外部系统可以使用：

```http
Authorization: Bearer <API_BEARER_TOKEN>
```

服务端通过 `API_BEARER_USER` 映射到一个有效系统用户，并使用该用户的权限和审计上下文。Token 未配置、无效或映射用户不存在时请求被拒绝。

### MCP Bearer Token

MCP Server 的 `/sse` 和 `/mcp/message` 使用独立的 `MCP_BEARER_TOKEN`，不与普通 REST Token 混用。

### 公开业务服务接口

`/api/v1/public/business-services/heartbeat` 和 `/events` 不需要 Session 或 Bearer Token。应仅在受控网络开放，并在外围网关增加来源、速率或签名保护。

代码归属上，这两个公开接口由 `BusinessServicePublicController` 实现；`BusinessServiceController` 只实现 `/api/v1/system/business-services/**` 管理查询。旧文档将两者合并讲解，是为了保留一份完整接入契约。

## 统一响应

除流式、文件和代理接口外，业务接口返回：

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {}
}
```

- `status = 0` 表示业务成功；
- HTTP 200 不等于业务成功；
- `status = 101` 表示需要重新登录；
- 错误原因优先读取 `msg`。

分页接口通常返回 `data.rows` 与总量字段；具体结构以对应 Swagger 模型为准。GET 查询使用 query 参数，POST/PUT 通常使用 JSON body。

前端或第三方 TypeScript 客户端可以统一解包：

```ts
type ResponseWrap<T> = {
  status: number;
  msg: string;
  data: T;
};

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) },
    ...init,
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);

  const body = (await response.json()) as ResponseWrap<T>;
  if (body.status !== 0) throw new Error(body.msg || '请求失败');
  return body.data;
}
```

## 登录与系统信息

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/system/login/sign-in` | 账号密码登录 |
| GET | `/api/v1/system/login/kaptcha` | 验证码 |
| GET | `/api/v1/system/login/encrypt/key` | 登录加密公钥 |
| POST | `/api/v1/system/login/sign-out` | 登出 |
| GET | `/api/v1/system/about/info` | 系统与产品信息 |
| PUT | `/api/v1/system/about/info/update` | 更新产品信息 |
| POST | `/api/v1/system/about/icon/upload` | 上传图标 |
| POST | `/api/v1/system/about/logo/upload` | 上传 Logo |
| POST | `/api/v1/system/about/banner/upload` | 上传 Banner |

## 用户、角色与菜单

### 用户 `/api/v1/system/user`

| 方法 | 相对路径 | 说明 |
| --- | --- | --- |
| POST | `/add` | 新增用户 |
| DELETE | `/{id}` | 删除用户 |
| POST | `/{id}/update` | 更新用户 |
| POST | `/{ids}/bulk-update` | 批量更新 |
| GET | `/list` | 分页查询 |
| GET | `/{id}/view` | 用户详情 |
| POST | `/update-password` | 修改密码 |

### 角色 `/api/v1/system/role`

提供新增、单个/批量删除、更新、批量更新、列表、详情、类型列表和 `/permission/tree` 权限树。内置超级管理员角色受服务端保护。

### 菜单 `/api/v1/system/menu`

提供新增、删除、批量删除、更新、批量更新、列表、详情、父菜单、类型和层级列表。`POST /update-order` 更新菜单顺序。

## 看板

### 首页 `/api/v1/dashboard/home`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/overview` | 系统概览 |
| GET | `/entity-statistics` | 实体统计 |

### 看板管理 `/api/v1/system/dashboard`

提供新增、删除、批量删除、更新、批量更新、列表、详情和类型列表。部分查询同时保留 GET/POST 列表形式，以 Swagger 当前声明为准。

## Retrieval

### 检索 `/api/v1/retrieval`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/do` | 执行检索 |
| POST | `/rule/create` | 新建过滤器 |
| POST | `/rule/update` | 更新过滤器 |
| POST | `/rule/delete` | 删除过滤器 |
| GET | `/rule/list` | 当前用户过滤器列表 |
| GET | `/rule/get` | 兼容规则读取 |
| GET | `/rule/detail` | 原子返回配置、Meta 和失效问题 |
| GET | `/entity/list` | 实体列表 |
| GET | `/attribute/list` | 属性列表 |
| GET | `/candidate/list` | 候选值 |
| GET | `/display/entity/list` | 可展示实体 |
| GET | `/display/attribute/list` | 可展示属性 |

检索请求使用逻辑实体和属性名。高级表达式由后端解析安全子集，不接受原始 SQL。

示例：

```bash
curl -X POST http://localhost:11001/api/v1/retrieval/do \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -d '{
    "entity": "event",
    "criteria_list": [],
    "display_list": ["event_id", "event_time"],
    "page": 1,
    "size": 20
  }'
```

### 通用实体 `/api/v1/entity/{entity}`

提供新增、删除、批量删除、更新、批量更新、列表、详情、字段 mapping、字段值列表和自动补全。具体写能力取决于实体数据源和服务实现。

统计入口位于 `/api/v1/entity`：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/overview/query` | 多实体累计量、当前周期量和对比 |
| POST | `/summary/query` | 单实体多指标汇总 |
| POST | `/trend/query` | 单/多实体时间趋势 |
| POST | `/distribution/query` | 任意字段分组 TopN |
| POST | `/value-statistics/query` | 指定值跨实体、跨字段统计 |
| POST | `/relations/query` | 任意字段关系聚合 |
| POST | `/relation-timeline/query` | 任意字段关系时间轴 |

统一统计契约和请求示例见
[EntityAnalyticsController](控制器/EntityAnalyticsController.md)。

## 配置管理

基础路径 `/api/v1/config/{type}`：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/tree` | 配置文件树 |
| GET | `/schema` | 配置 Schema |
| GET | `/read` | 读取文件 |
| GET | `/get` | 获取配置 |
| POST | `/modify` | 修改文件 |
| POST | `/apply` | 应用配置 |
| POST | `/add` | 新增文件 |
| POST | `/rename` | 重命名 |
| POST | `/delete` | 删除文件 |

文件名、路径和配置类型均受服务端目录边界校验。

## 插件

基础路径 `/api/v1/system/plugin`：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/upload` | 上传插件包 |
| POST | `/add` | 新增插件记录 |
| GET | `/list` | 插件列表 |
| GET | `/{id}/view` | 插件详情 |
| GET | `/{id}/readme` | 查看插件 README |
| GET | `/{id}/doc/tree` | 查看文档树 |
| GET | `/{id}/doc/view` | 查看文档内容 |
| REQUEST | `/{id}/export` | 导出插件 |
| GET | `/{id}/logs` | 安装、升级、恢复或卸载日志 |
| POST | `/{id}/install` | 安装 |
| POST | `/{id}/upgrade` | 使用已上传的更高版本包升级已安装插件 |
| POST | `/{id}/upgrade/recover` | 从持久化快照恢复升级前旧版本 |
| POST | `/{id}/uninstall` | 卸载 |
| DELETE | `/{id}`、`/bulk/{ids}` | 删除未安装插件记录 |

已安装动态 API 自动增加：

```text
/api/v1/plugin/{package_name}/...
```

## 数据推送任务

`/api/v1/system/push-task/**` 代理到配置的 Vectum 数据服务，并注入服务端 Bearer Token。调用方不应绕过 ZenVis 直接暴露内部 Vectum 凭据。

## DIH Chat

基础路径 `/api/v1/dih`：

`chat`、`suggest`、模型列表、健康检查、附件和动作决策当前全部由 `ChatController` 实现；仓库中没有独立的 `DihController`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/chat` | 流式聊天或业务 Agent |
| POST | `/suggest` | 文本建议 |
| GET | `/model/list` | 可用模型 |
| GET | `/health` | DIH 健康状态 |
| POST | `/upload` | 上传会话附件 |
| GET | `/upload/{fileId}/preview` | 预览附件 |
| POST | `/chat/action-decision` | 记录结构化业务动作决策 |

`/chat` 使用 `response_format=events` 时返回 NDJSON：

```json
{"event":"delta","content":"增量内容"}
{"event":"approval_required","data":{}}
{"event":"approval_updated","data":{}}
{"event":"done","message":{"sender":"ai","content":"完整内容","parts":[]}}
{"event":"error","message":"错误消息"}
```

### 会话 `/api/v1/dih/chat-session`

提供新增、单个/批量删除、更新、置顶列表、分页列表、详情和 `/{sessionId}/session` 业务会话读取。会话列表按当前用户隔离。

## MCP 管理

基础路径 `/api/v1/dih/mcp`：

- `/servers/*`：服务增删改查、启停和刷新；
- `/tools`、`/tools/call`：工具发现与测试调用；
- `/tools/policies/*`：审批策略管理；
- `/approvals/*`：待审批查询与决策；
- `/invocations/list`：调用审计；
- `/agent/prompt`：查看 Agent 工具提示片段。

测试调用命中 `ASK` 时先返回 request ID；批准后必须用相同 request ID 和原参数重试，服务端校验参数 SHA-256 校验值并保证最多执行一次。

## Skill 与向量文档

### Skill `/api/v1/dih/skills`

提供列表、Agent 状态、任务选项、详情、重载、启停和按 Agent 查看 Prompt。Skill 文件位于配置目录，任务保存 Skill ID，执行时读取最新启用内容。

典型前端路径：

```text
GET  /api/v1/dih/skills/list
GET  /api/v1/dih/skills/chat-entries?enabled=true
GET  /api/v1/dih/skills/{id}/view
POST /api/v1/dih/skills/reload
POST /api/v1/dih/skills/{id}/enable
POST /api/v1/dih/skills/{id}/disable
GET  /api/v1/dih/skills/agent/{agentType}/prompt
```

### Vector Store `/api/v1/dih/vectorstore`

提供文档列表、详情、删除、清空和 GET/POST 搜索，用于管理插件文档 RAG。修改向量索引前应确认 embedding 配置和重建影响。

## AI 分析任务

基础路径 `/api/v1/system/analysis-task`，提供：

- 新增、删除、批量删除、更新、列表和详情；
- `/{id}/enqueue` 排队；
- `/{id}/cancel` 取消；
- `/queue/run-once` 触发一次调度；
- `/queue/status` 队列状态；
- 任务审批列表与决策。

任务审批模式、Skill、模型、计划时间和优先级属于任务契约，字段以 Swagger 为准。

## 业务应用服务

公开上报：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/public/business-services/heartbeat` | 注册或续报实例 |
| POST | `/api/v1/public/business-services/events` | 上报业务事件 |

管理查询位于 `/api/v1/system/business-services`：`/summary`、`/instances`、`/instances/{id}` 和 `/events`。

完整接入示例见[业务服务接入](../08-业务服务接入/README.md)。
