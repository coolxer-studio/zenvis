# ZenVis 文档中心

本目录以 ZenVis 整体产品为对象组织文档。后端、前端和业务服务 Starter 的资料已经按主题合并到同一目录树中，不再划分“后端文档归档”和“前端文档归档”。主题 README 给出统一口径和阅读入口，原项目中的详细设计、逐接口说明、操作步骤、截图和示例源码直接作为主题正文保留，不由摘要替代。

## 目录结构

```text
doc/
├── 01-产品与使用/
│   ├── README.md
│   ├── 使用指南.md
│   └── 使用手册/                  # 完整产品手册与功能说明
├── 02-快速开始与部署/
│   ├── README.md
│   ├── 安装部署.md
│   ├── getting-started.md
│   ├── installation.md
│   └── deployment.md
├── 03-架构设计/
│   ├── README.md
│   ├── architecture.md
│   └── retrieval-module.md
├── 04-开发指南/                  # 后端、前端、测试与性能资料
├── 05-API参考/
│   ├── README.md
│   ├── 控制器/                    # 21 份 Controller 详细接口
│   └── 示例/                      # TypeScript 调用示例
├── 06-插件开发与集成/
├── 07-AI与数据智能/              # DIH、MCP、RAG、会话与报表
├── 08-业务服务接入/
├── 旧文档迁移映射.md              # 后端、前端旧资料逐项去向
└── assets/                       # banner 与 83 张产品截图
```

## 阅读路线

### 产品使用

1. [产品与使用](01-产品与使用/README.md)：产品定位、能力边界和模块关系。
2. [统一使用指南](01-产品与使用/使用指南.md)：登录、看板、检索、数智中心和系统管理的主流程。
3. [完整产品手册](01-产品与使用/使用手册/README.md)：原产品手册的全部章节、操作细节和截图。

### 安装与研发

1. [快速开始与部署](02-快速开始与部署/README.md)：开发环境和最短启动链路。
2. [完整安装部署](02-快速开始与部署/安装部署.md)：Docker、生产配置、备份与排障。
3. [架构设计](03-架构设计/README.md)：平台统一架构，以及原架构和 Retrieval 详细设计。
4. [开发指南](04-开发指南/README.md)：工程协作、后端研发、前端检索、性能和测试资料。

### 集成与扩展

1. [API 参考](05-API参考/README.md)：统一认证、响应格式、接口地图和逐 Controller 契约。
2. [插件开发与集成](06-插件开发与集成/README.md)：平台插件契约、完整开发指南和专项插件入口。
3. [AI 与数据智能](07-AI与数据智能/README.md)：DIH、会话、MCP、AI 分析任务、报表与 Redis Search。
4. [业务服务接入](08-业务服务接入/README.md)：Spring Boot Starter、心跳和事件上报。

完整的逐文件前后对应关系见[旧文档迁移映射](旧文档迁移映射.md)。

## 原项目内容融合清单

| 原资料 | 统一目录 | 保留内容 |
| --- | --- | --- |
| 后端产品手册 | [`01-产品与使用/使用手册`](01-产品与使用/使用手册/) | 14 份手册文档和全部操作步骤 |
| 后端使用手册图片 | [`assets/使用手册`](assets/使用手册/) | 83 张 PNG 截图 |
| 后端快速入门、安装和部署 | [`02-快速开始与部署`](02-快速开始与部署/) | 3 份原设计文档及统一部署文档 |
| 后端架构与 Retrieval | [`03-架构设计`](03-架构设计/) | 架构设计和全局检索完整设计 |
| 后端开发说明、测试记录 | [`04-开发指南`](04-开发指南/) | 完整开发指南和测试记录 |
| 前端检索与性能基线 | [`04-开发指南`](04-开发指南/) | 2 份前端研发文档 |
| 后端 API 总览与第三方接入 | [`05-API参考`](05-API参考/) | API 地图、认证、示例和排障 |
| 后端逐 Controller 文档 | [`05-API参考/控制器`](05-API参考/控制器/) | 21 份详细接口文档 |
| 前端 API 说明与示例 | [`05-API参考`](05-API参考/) | 前端接口文档和 TypeScript 示例 |
| 后端插件开发设计 | [`06-插件开发与集成`](06-插件开发与集成/) | 完整插件开发指南 |
| DIH、MCP、报表和 Redis Search | [`07-AI与数据智能`](07-AI与数据智能/) | 4 份 DIH 专项文档 |
| AI 会话设计 | [`07-AI与数据智能`](07-AI与数据智能/) | 会话数据结构与实现说明 |
| Starter README | [原位保留](../zenvis-business-service-spring-boot-starter/README.md) | Starter 最小使用契约；平台接入说明已纳入主题文档 |
| 社区插件资料 | [原位保留](../zenvis-plugin-community/) | 作为只读专项资料，由统一插件主题建立入口，不复制或改写 `plugin-*` 内容 |
| 插件创建 Skill | [原位保留](../agent-skills/create-zenvis-plugin/SKILL.md) | 作为代码、Meta、Vector、UI 与文档一致性工作流的维护入口 |

两个子项目原有的 `doc/banner.jpg` 内容完全相同，统一复用 [`assets/banner.jpg`](assets/banner.jpg)。除这一相同资源去重外，原项目中的文档、示例和图片均在上述主题目录中有对应内容。

## 文档约定

- 外部浏览器访问 API 时，容器部署通常使用 `/zenvis/api/v1/...`；后端 Controller 的实际路径为 `/api/v1/...`，由前端代理去掉 `/zenvis`。
- JSON 请求与响应使用 `snake_case`。统一业务响应为 `{ status, msg, data }`，`status = 0` 表示成功。
- 示例中的密码、Token、模型地址均应通过环境变量配置，不应把生产凭据提交到仓库。
- 插件专项资料继续在 `zenvis-plugin/plugin-*/README.md` 和 `00_doc/` 中原位维护，统一插件主题只负责平台契约和入口索引。
- 主题 README 维护当前统一口径；详细文档保留完整设计。二者有版本冲突时，以代码、`pom.xml`、`package.json` 和环境配置为准，并同步修正文档。

## 模块 README

- [后端](../zenvis-backend/README.md)
- [前端](../zenvis-frontend/README.md)
- [插件仓库](../zenvis-plugin/README.md)
- [社区插件集合](../zenvis-plugin-community/)
- [业务服务 Starter](../zenvis-business-service-spring-boot-starter/README.md)
- [插件创建 Skill](../agent-skills/create-zenvis-plugin/SKILL.md)
