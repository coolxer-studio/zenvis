# ZenVis

![ZenVis](doc/assets/banner.jpg)

ZenVis 是一个面向企业数据分析场景的配置化应用框架，将数据接入、元数据管理、统一检索、可视化、插件扩展和 AI 智能分析组织在同一平台中。业务团队可以通过配置和插件快速建立数据模型、接入数据、构建应用，并复用平台提供的权限、检索、看板和智能分析能力。

## 项目组成

| 目录 | 作用 | 技术栈 |
| --- | --- | --- |
| `zenvis-backend` | 平台 API、系统管理、检索、插件生命周期、DIH 与业务服务管理 | Java 17、Spring Boot 3.2、Spring AI |
| `zenvis-frontend` | 管理控制台、检索、看板、低代码页面与 DIH 工作台 | Vue 3、TypeScript、Vite、Element Plus |
| `zenvis-plugin` | 可安装插件、插件 API 扩展与打包工具 | JSON/YAML、Java、HTML、tar.gz |
| `zenvis-business-service-spring-boot-starter` | 业务应用服务心跳和事件上报组件 | Spring Boot 3.x、Java 17 |
| `agent-skills` | ZenVis 插件创建与校验技能 | Codex Skill |

各子项目保留自己的 `README.md`，用于独立构建和维护。跨模块的产品、架构、部署、使用与开发说明统一维护在根目录的 [ZenVis 文档中心](doc/README.md)。

## 快速开始

### Docker Compose

完整环境由后端的部署目录统一编排：

```bash
cd zenvis-backend/deploy
docker compose up -d
```

默认服务入口：

| 服务 | 地址 |
| --- | --- |
| ZenVis Web | `http://localhost:11000` |
| ZenVis API / Swagger | `http://localhost:11001`、`http://localhost:11001/swagger-ui/index.html` |
| Vectum 数据服务 | `http://localhost:11002` |

首次部署前应在部署配置中设置数据库、API、MCP 和模型服务凭据，并在首次登录后修改初始化账号密码。完整说明见[安装部署](doc/02-快速开始与部署/安装部署.md)。

### 本地开发

后端：

```bash
cd zenvis-backend
mvn test
mvn spring-boot:run
```

前端：

```bash
cd zenvis-frontend
yarn install
yarn server:dev
```

前端开发服务器位于 `http://localhost:8090`，默认将 `/zenvis` 请求代理到 `http://localhost:11001`。详细步骤见[快速开始](doc/02-快速开始与部署/README.md)。

## 核心能力

- 通过 Meta 配置定义实体、属性、操作符和 ClickHouse 存储结构。
- 通过 Vector 服务、Kafka 和插件推送任务接入外部数据。
- 通过全局检索完成筛选、排序、聚合、趋势分析和过滤器复用。
- 通过内置、低代码、HTML 与外链四类看板组织可视化应用。
- 通过插件安装 Meta、推送任务、动态 API、UI、看板、MCP、Skill 和菜单。
- 通过 DIH 提供知识问答、数据接入、数据可视化、研判、策略控制、报表和后台 AI 分析任务。
- 通过业务服务 Starter 采集业务应用实例心跳和事件。

## 文档

| 主题 | 文档 |
| --- | --- |
| 产品与使用 | [产品与使用](doc/01-产品与使用/README.md) |
| 启动与部署 | [快速开始与部署](doc/02-快速开始与部署/README.md) |
| 技术设计 | [架构设计](doc/03-架构设计/README.md) |
| 工程协作 | [开发指南](doc/04-开发指南/README.md) |
| 接口对接 | [API 参考](doc/05-API参考/README.md) |
| 插件扩展 | [插件开发与集成](doc/06-插件开发与集成/README.md) |
| AI 能力 | [AI 与数据智能](doc/07-AI与数据智能/README.md) |
| 应用接入 | [业务服务接入](doc/08-业务服务接入/README.md) |

## 许可证与联系

项目采用 [Apache License 2.0](LICENSE)。问题与建议可通过项目 Issue 或 <coolxer@163.com> 反馈。
