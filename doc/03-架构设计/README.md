# 系统架构

本主题包含统一架构视图和两份完整专项设计：

- [平台架构详细设计](architecture.md)
- [Retrieval 全局检索详细设计](retrieval-module.md)

## 整体视图

```text
浏览器 / 外部系统 / 业务应用
        │
        ├── Web、低代码、HTML、外链
        ├── REST API / MCP
        └── 业务服务心跳与事件
        │
        ▼
ZenVis Frontend（Vue 3） ────────┐
        │ /zenvis               │
        ▼                       │
ZenVis Backend（Spring Boot）    │
  ├── 系统管理与权限             │
  ├── Meta / Retrieval / Dashboard
  ├── Plugin Lifecycle           │
  ├── DIH / RAG / Agent / MCP    │
  └── Business Service Registry  │
        │                       │
        ├── MySQL：平台数据与会话
        ├── ClickHouse：分析实体数据
        ├── Redis：Session 与缓存
        ├── Redis Stack：向量索引
        ├── Kafka + Vectum：数据接入
        └── OpenAI 兼容模型 / 外部 MCP
```

## 应用层

### 前端控制台

前端使用 Vue 3、TypeScript、Vite、Pinia、Element Plus 和 ECharts。路由采用 Hash 模式，内置登录、首页、检索、DIH、策略配置、系统信息以及低代码/外部/HTML 页面容器。

请求层使用 Axios，统一 `baseURL` 为 `VITE_BASE_URL`，并携带 Cookie。业务 JSON 使用 `snake_case`，成功与失败由响应中的 `status` 判断。

### 低代码与静态页面

- 低代码应用通过 `site.json` 组织多个页面；
- 低代码页面通过单个 `index.json` 渲染；
- HTML 页面由后端 `/html-page/**` 静态资源路径提供；
- 外部应用进入 iframe 前执行 URL 协议和允许来源检查。

## 服务层

### 系统与权限

用户登录后使用服务端 Session/Cookie；外部系统也可以配置普通 REST API Bearer Token，并映射到指定系统用户以复用权限和审计上下文。用户、角色、菜单、看板、插件和系统信息均由系统管理模块维护。

### 元数据与检索

Meta 描述实体、属性和操作符。后端根据逻辑字段构建查询，向 ClickHouse 发起受控检索，并以逻辑属性名返回结果。Retrieval 模块负责普通条件、高级表达式、显示字段、过滤器保存和失效检测。

### 插件生命周期

插件安装器按目录顺序处理文档、Meta、推送任务、动态 API、UI、看板、MCP、Skill 与菜单。动态 API 使用独立类加载和 Spring Bean 注册；MySQL 迁移具有版本和 SHA-256 校验。升级使用持久化快照、只向前 MySQL 迁移和 ClickHouse 新增式结构变更保留业务数据；卸载会删除插件关联的 ClickHouse 表和数据，但保留 MySQL 迁移历史。

`zenvis-plugin` 保存平台内置插件，`zenvis-plugin-community` 保存社区与客户场景插件，二者使用同一安装契约。`agent-skills/create-zenvis-plugin` 是研发侧创建和校验工作流，不属于运行时插件，也不会被后端安装器扫描。

### DIH 与 AI

DIH 应用服务统一编排消息保存、附件、模型选择、RAG、业务 Agent、MCP 工具事件与结构化消息。普通问答和业务 Agent 具有不同的数据边界：

- 普通问答可使用公共 RAG，不加载 Skill 和 MCP 工具；
- 业务 Agent 加载显式 Skill，并获得 scope 允许的工具；
- 数据可视化 Agent 不接入外部 MCP，也不执行任意 SQL 或实体写入；当前本地白名单除 Retrieval/实体查询外，还包含配置、看板和菜单的受控创建/应用工具，写入类工具默认进入审批；
- 工具调用经过策略、审批、授权和审计状态机。

### 业务应用服务

外部应用通过公开心跳和事件接口注册实例。后端按离线阈值计算状态，并提供概览、实例与事件查询。Starter 把注册、续报、关闭通知和异步事件队列封装为 Spring Boot 自动配置。

## 数据层

| 组件 | 主要数据 | 特点 |
| --- | --- | --- |
| MySQL | 用户、角色、菜单、插件、配置、会话、任务、MCP 策略与审计 | JPA 与插件 JDBC 共存 |
| ClickHouse | 插件声明的检索实体和分析数据 | 面向大规模查询与聚合 |
| Redis | HTTP Session、缓存和运行状态 | 默认数据库与 Session 超时由环境配置 |
| Redis Stack | 文档向量和相似度检索 | 可通过配置关闭或重建索引 |
| Kafka | 原始消息、中间路由和死信 | 与 Vector 推送任务组成接入链路 |

## 核心数据流

### 数据接入

```text
外部源 → Vector Source → Kafka 原始主题 → 校验/转换 → ClickHouse Sink
                                      └→ 无效数据 → DLQ
```

Meta 的业务属性集合、转换输出字段和 ClickHouse Sink 字段必须一致。

### 全局检索

```text
选择实体 → 加载 Meta → 构建条件/展示列 → Retrieval 校验 → ClickHouse 查询 → 表格/统计/跳转
```

保存的过滤器属于创建用户。元数据变化后，规则详情返回失效项，前端允许修复但不会自动执行失效规则。

### AI 会话

```text
用户消息 → 会话与附件处理 → 选择普通问答或业务 Agent
        → RAG 或 Skill/MCP → 流式事件 → 结构化消息 → 会话持久化
```

界面历史保存在 ZenVis 会话消息中，模型多轮上下文保存在 Spring AI JDBC Chat Memory 中；两者用途不同。

## 安全边界

- 普通 REST API 使用 Session/Cookie 或配置的 API Bearer Token。
- MCP Server SSE 和消息端点使用独立 MCP Bearer Token。
- 高级检索只接受受限表达式，不接受任意 SQL、子查询或注释拼接。
- 插件链接只允许相对地址或 `http/https`，前端进一步检查 iframe 来源。
- 插件上传请求当前最大 300MB；发布包仍应排除源码、构建产物、历史归档和非必要样本。
- MCP 工具按 `ALLOW / ASK / DENY` 执行，拒绝、超时和取消均进入审计状态。
- 凭据通过环境变量注入，不应写入文档、前端静态资源或插件包。

## 扩展边界

- 平台功能优先通过稳定 REST、MCP、插件目录契约和 Starter 接入。
- 插件 API 不应引用后端内部业务 Entity、Repository 或工具类。
- 前端低代码页面依赖逻辑实体与属性名，不应绑定 ClickHouse 物理列名。
- 业务 Agent 新能力优先通过受控工具扩展，不在 Agent 内直接访问数据库。
