# 插件开发与集成

ZenVis 插件把数据模型、接入任务、动态 API、低代码页面、看板、外部 MCP、运行时 Skill、菜单和用户文档封装为一个可安装单元。本目录说明这些扩展面的开发契约、组合方式和交付要求。

## 能力全景

```text
外部数据或业务规范
        │
        ▼
插件包（index.json + 00_doc … 08_menu）
        │
        ├── Meta ───────────────→ ClickHouse / Retrieval
        ├── Push Task ──────────→ Vectum / Kafka / ClickHouse
        ├── Dynamic API ────────→ Spring Bean / REST
        ├── UI / Dashboard ─────→ 低代码容器 / HTML 页面
        ├── MCP / Skill ────────→ DIH / Agent 工具调用
        └── Menu / Document ────→ 权限入口 / RAG
```

插件适合承载具有明确业务边界、需要独立发布或可能由社区维护的能力。平台级通用运行机制仍应在 ZenVis 核心工程中维护。

## 文档地图

| 文档 | 解决的问题 |
| --- | --- |
| [插件包规范](/03-插件开发与集成/插件包规范.md) | 插件由哪些文件组成，目录、标识和资源归属如何约定 |
| [Meta 与数据建模](/03-插件开发与集成/Meta与数据建模.md) | 如何把数据规范映射为 Entity、Attribute 和 ClickHouse 表 |
| [数据接入与推送任务](/03-插件开发与集成/数据接入与推送任务.md) | 如何设计 Source、Kafka、转换、ClickHouse 和 DLQ 链路 |
| [动态 API 与数据迁移](/03-插件开发与集成/动态API与数据迁移.md) | 如何开发插件 REST API、薄 JAR 和 MySQL 迁移 |
| [UI、看板与菜单](/03-插件开发与集成/UI看板与菜单.md) | 如何组织低代码应用、独立页面、HTML 看板和权限入口 |
| [MCP 与 Skill 集成](/03-插件开发与集成/MCP与Skill集成.md) | 如何声明外部 MCP、运行时 Skill、工具范围和聊天入口 |
| [生命周期与发布验证](/03-插件开发与集成/生命周期与发布验证.md) | 如何安装、升级、恢复、卸载、打包、检查和排障 |

## 推荐阅读路线

### 新建数据型插件

1. 阅读[插件包规范](/03-插件开发与集成/插件包规范.md)，确定包名和能力范围。
2. 建立数据定义、Entity、表、Kafka Topic 和页面之间的契约矩阵。
3. 完成[Meta 与数据建模](/03-插件开发与集成/Meta与数据建模.md)。
4. 完成[数据接入与推送任务](/03-插件开发与集成/数据接入与推送任务.md)。
5. 按需增加[动态 API](/03-插件开发与集成/动态API与数据迁移.md)、[UI 和看板](/03-插件开发与集成/UI看板与菜单.md)。
6. 根据[生命周期与发布验证](/03-插件开发与集成/生命周期与发布验证.md)完成交付。

### 扩展已有插件

先核对目标插件的 `README.md`、`00_doc/` 和 `index.json`，再阅读对应专题。已有 TOML 推送任务和 `04_ui` 平铺配置仍受平台兼容；没有业务收益时不应只为统一格式而改写稳定插件。

### 接入 Agent 能力

先阅读[MCP 与 Skill 集成](/03-插件开发与集成/MCP与Skill集成.md)，再结合 [AI 与 MCP 架构](/06-架构设计/AI与MCP架构.md)和 [MCP Tool 运行约束](/08-API参考/MCPtool/权限审批与运行约束.md)确认工具权限与审批边界。

## 仓库边界

| 位置 | 责任 |
| --- | --- |
| `zenvis-plugin` | 统一维护平台、社区与企业插件、公共插件 API 模型和构建脚本 |
| `zenvis-plugin/zenvis-plugin-*` | 场景或产品子仓库；保留独立 Git、版本和发布节奏 |
| `agent-skills/create-zenvis-plugin` | 研发侧创建、校验和打包插件的工作流 |
| 插件的 `07_skill` | 安装到平台并供 DIH 使用的运行时 Skill |

研发 Skill 与运行时 Skill 不是同一种资源：前者帮助开发插件，后者随插件安装并进入平台 Skill 注册表。

平台、社区和企业插件使用同一平台安装契约，但具体字段、数据源、部署顺序和兼容要求以目标插件自己的 `README.md` 与 `00_doc/` 为准。企业插件还必须提供兼容矩阵、安全评审、运维保障和生产验收材料。统一开发入口见：

- [`zenvis-plugin` 开发对接指南](/07-开发指南/zenvis-plugin-开发对接指南.md)
- [`agent-skills` 开发对接指南](/07-开发指南/agent-skills-开发对接指南.md)

## 文档边界

- 本目录维护插件包格式、能力契约和交付规则。
- [插件与扩展架构](/06-架构设计/插件与扩展架构.md)维护运行时组件关系和生命周期设计。
- [开发指南](/07-开发指南/README.md)维护各代码仓库的环境、命令和协作方式。
- [API 参考](/08-API参考/README.md)维护平台 REST API 和 MCP Tool 的接口清单。
- [插件管理使用说明](/01-产品理念与使用/系统管理/插件管理.md)面向平台管理员，不替代开发契约。

## 源码依据

本文档以当前项目代码为准，主要核验点包括：

- 后端 `PluginServiceImpl`、`PluginMigrationServiceImpl` 和动态 JAR 加载器；
- `DataEntity`、`DataAttribute`、`DashboardDto`、`MenuDto`、`PushTaskDto`、`McpServerDto`；
- `SkillService`、Agent 工具范围与 MCP 客户端实现；
- 前端低代码路由、HTML 页面容器和 Dashboard 容器；
- `zenvis-plugin` 构建脚本、根插件目录和嵌套场景/产品子仓库。

样例只能说明用法。样例与当前代码冲突时，以当前运行契约为准。
