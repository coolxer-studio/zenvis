# 开发指南

本目录面向需要接手 ZenVis 源码、部署编排、插件或接入组件的开发人员。文档按项目平铺组织，每个项目一篇开发对接指南，不再为项目创建二级目录。

## 项目导航

| 项目 | 主要职责 | 首次接手建议 |
| --- | --- | --- |
| [`agent-skills`](/07-开发指南/agent-skills-开发对接指南.md) | 研发侧 Agent Skill，当前用于创建、校验和打包 ZenVis 插件 | 先区分研发 Skill 与平台运行时 Skill，再阅读插件契约 |
| [`deploy`](/07-开发指南/deploy-开发对接指南.md) | Docker Compose、基础服务配置、开放配置和持久化目录 | 先执行 `docker compose config`，再核对架构与凭据 |
| [`zenvis-backend`](/07-开发指南/zenvis-backend-开发对接指南.md) | 平台 API、检索、插件生命周期、DIH/MCP 和业务服务管理 | 先启动依赖并运行 Maven 测试，再按领域定位 Controller 与 Service |
| [`zenvis-frontend`](/07-开发指南/zenvis-frontend-开发对接指南.md) | 管理控制台、检索、看板、低代码和 DIH 工作台 | 先完成类型/单元检查和生产构建，再进入具体页面 |
| [`zenvis-plugin`](/07-开发指南/zenvis-plugin-开发对接指南.md) | 平台内置插件、动态 API 源码和打包工具 | 先确认插件目录契约，再同步修改 Meta、数据接入、UI 和文档 |
| [`zenvis-plugin-community`](/07-开发指南/zenvis-plugin-community-开发对接指南.md) | 社区与客户场景插件集合 | 先选择目标子仓库，并确认插件间依赖和部署顺序 |
| [`zenvis-business-service-spring-boot-starter`](/07-开发指南/zenvis-business-service-spring-boot-starter-开发对接指南.md) | Spring Boot 业务服务心跳与事件上报组件 | 先运行 Starter 测试，再用最小配置接入测试服务 |

## 推荐阅读路线

### 平台功能开发

1. 阅读 [平台架构](/06-架构设计/README.md)，确认模块边界。
2. 阅读[后端开发对接指南](/07-开发指南/zenvis-backend-开发对接指南.md)和[前端开发对接指南](/07-开发指南/zenvis-frontend-开发对接指南.md)。
3. 涉及接口时查阅 [API 参考](/08-API参考/README.md)。
4. 涉及 AI、MCP、RAG 或业务 Agent 时继续阅读 [AI 与数据智能](/04-AI与数据智能/README.md)。

### 插件或数据接入开发

1. 先阅读 [插件开发与集成](/03-插件开发与集成/README.md)。
2. 根据归属选择[内置插件](/07-开发指南/zenvis-plugin-开发对接指南.md)或[社区插件](/07-开发指南/zenvis-plugin-community-开发对接指南.md)。
3. 需要 Agent 辅助创建或校验插件时，阅读 [`agent-skills` 开发对接指南](/07-开发指南/agent-skills-开发对接指南.md)。
4. 最后通过 [`deploy` 开发对接指南](/07-开发指南/deploy-开发对接指南.md)理解插件安装目录和运行依赖。

### 外部业务服务接入

1. 阅读 [业务服务接入](/05-业务服务接入/README.md)了解服务端协议。
2. Spring Boot 应用使用 [Starter 开发对接指南](/07-开发指南/zenvis-business-service-spring-boot-starter-开发对接指南.md)。
3. 非 Spring Boot 应用按 [RESTful API](/08-API参考/RestfulAPI/业务服务.md)直接调用公开上报接口。

## 工程与 Git 边界

```text
zenvis/                                      # 根工作树
├── agent-skills/
├── deploy/
├── doc/
├── zenvis-business-service-spring-boot-starter/
├── zenvis-backend/                          # 独立 Git 工作树
├── zenvis-frontend/                         # 独立 Git 工作树
├── zenvis-plugin/                           # 独立 Git 工作树
└── zenvis-plugin-community/
    ├── zenvis-plugin-jmr/                   # 独立 Git 工作树
    └── zenvis-plugin-xiangtanhospital/      # 独立 Git 工作树
```

修改前后应分别在实际工作树中运行 `git status --short`。不要在一个工作树的提交中混入另一个工作树的文件，也不要清理不属于当前任务的未提交内容。

## 跨项目公共约定

- 后端 Controller 路径使用 `/api/v1/**`；浏览器通过前端代理访问时通常带 `/zenvis` 前缀。
- JSON 请求和响应使用 `snake_case`；常规后端响应为 `ResponseWrap<T>`，结构包含 `status`、`msg` 和 `data`。
- 平台核心提供认证、配置、检索、看板、插件生命周期和 AI 框架能力；具体业务模型和接口优先由插件交付。
- 插件的 Meta、Vector、动态 API、UI、看板、MCP、Skill、菜单和文档是同一发布单元，修改时必须保持契约一致。
- 本地凭据使用环境变量或忽略提交的本机配置，不在 README、示例命令和源码中新增真实密码或令牌。
- 版本、命令和字段以当前 `pom.xml`、`package.json`、`index.json`、Compose 和代码实现为准。

## 提交前通用检查

1. 运行目标项目指南列出的编译、测试和构建命令。
2. 检查 `git diff --check`，确认没有空白错误和冲突标记。
3. 确认没有提交 `.DS_Store`、日志、运行数据、构建目录、归档或凭据。
4. 接口、配置、部署方式或用户行为发生变化时，同步更新对应主题文档。
5. 在目标 Git 工作树内再次检查文件范围，再提交或发起评审。

## 文档站预览

文档站使用 Docsify 4。在项目根目录运行：

```bash
npx docsify-cli serve ./doc
```

默认访问 `http://localhost:3000`。预览时至少检查本页、侧边栏、七篇项目指南及其内部链接。
