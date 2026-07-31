# `agent-skills` 开发对接指南

## 项目定位

`agent-skills` 保存面向研发人员和 Codex 的工作流技能。当前包含 `create-zenvis-plugin`，用于根据数据字典或集成规范创建、更新、校验和打包 ZenVis 插件。

它不属于 ZenVis 后端运行时插件，也不会被后端插件安装器或 `deploy/open_config/skill_config` 自动扫描：

| 类型 | 位置 | 使用者 | 生命周期 |
| --- | --- | --- | --- |
| 研发侧 Skill | `agent-skills/<skill>/` | Codex、插件开发人员 | 随研发仓库维护和安装 |
| 平台运行时 Skill | `deploy/open_config/skill_config/` 或插件 `07_skill/` | ZenVis DIH 与业务 Agent | 由平台配置或插件安装器加载 |

## 技术栈与目录职责

该项目以 Markdown 和 YAML 为主，不包含独立运行服务：

```text
agent-skills/
└── create-zenvis-plugin/
    ├── SKILL.md
    └── agents/
        └── openai.yaml
```

- `SKILL.md`：技能元数据、触发范围、工作步骤、平台契约和验证清单。
- `agents/openai.yaml`：客户端展示名称、简短说明和默认提示词。

当前技能没有独立构建脚本、运行时依赖或测试框架。它的质量依赖于说明完整性、引用路径有效性，以及与当前后端、前端和插件代码的一致性。

## 上下游关系

```text
数据字典或集成规范
        ↓
create-zenvis-plugin
        ↓
zenvis-backend + zenvis-frontend 当前契约
        ↓
zenvis-plugin 或 zenvis-plugin-community
        ↓
插件归档与安装验证
```

技能不能以旧插件样例代替平台代码事实。涉及 Meta、插件安装、看板、菜单、推送任务或动态 API 时，应同时核对：

- [插件开发与集成](/03-插件开发与集成/README.md)；
- 后端 `PluginServiceImpl`、Meta 模型、DTO 和校验逻辑；
- 前端低代码、检索表格、链接安全和看板处理；
- 目标插件的 `index.json`、README 和 `00_doc/`。

## 环境与配置

- 维护环境只需要能够读取整个 ZenVis 工作区、解析 Markdown/YAML/JSON，并运行目标插件的 Maven 或 Vector 校验。
- `SKILL.md` 的 YAML front matter 是技能发现配置；`agents/openai.yaml` 是客户端展示配置，二者不是 ZenVis 运行时 `skill_config`。
- 修改前先检查根工作树及目标插件独立工作树的 `git status`，避免把其他项目的未提交内容带入交付。
- 技能引用当前仓库路径时使用可追踪的项目相对路径，不引用个人绝对路径、临时目录或真实凭据。

## 开发与维护流程

### 1. 定义触发边界

`SKILL.md` 顶部必须保留有效的 YAML front matter：

```yaml
---
name: create-zenvis-plugin
description: Create, update, review, validate, and package Zenvis plugins...
---
```

`name` 应稳定、可调用；`description` 应明确技能适用场景。不要把普通后端、前端或部署任务扩大成插件创建任务。

### 2. 建立事实来源

修改技能规则前先确认当前项目实际行为：

1. 阅读目标工作树内的 `AGENTS.md`。
2. 检查平台级插件开发文档和代码契约。
3. 检查目标插件和相邻插件，但把平台代码作为最终依据。
4. 检查所有工作树状态，保留无关修改。

### 3. 保持插件全链路一致

技能的核心维护对象包括：

- `index.json` 的包名、版本、说明和图标；
- `01_meta` 的实体、属性、操作符和 ClickHouse 建表契约；
- `02_push-task` 的 Vector 输入、转换、DLQ 和 ClickHouse 输出；
- `03_api` 的单一薄 Jar 与版本化 MySQL 迁移；
- `04_ui` 的低代码应用、独立页面和详情页；
- `05_dashboard` 的配置和静态 HTML；
- `06_mcp`、`07_skill`、`08_menu`；
- README、数据字典、契约矩阵和发布说明。

如果其中一个环节的标识、字段或路径变化，技能必须要求同步核对其他环节。

### 4. 更新客户端展示

修改技能名称或主要用途时同步更新 `agents/openai.yaml`：

```yaml
interface:
  display_name: "Zenvis 插件创建"
  short_description: "从数据字典创建、校验并打包完整 Zenvis 插件"
  default_prompt: "使用 $create-zenvis-plugin 根据输入数据字典创建、校验并打包 Zenvis 插件。"
```

`default_prompt` 中的技能名必须与 front matter 的 `name` 一致。

## 核心工作流

### 契约矩阵

创建插件前先形成数据定义到实现的映射：

| 数据定义 | 代码 | Entity | ClickHouse 表 | Kafka topic | UI 页面 | 是否结构化 |
| --- | --- | --- | --- | --- | --- | --- |

每个结构化数据定义对应一个实体和一张表；附件、PCAP、压缩包等非结构化内容只有在规范明确要求时才进入结构化存储。

### Meta 与数据接入

- Meta 顶层使用 `entity`、`attribute`、`operator`。
- 标识、ID、实体引用、排序列、建表列和操作符引用必须可解析且唯一。
- `zenvis_id` 与 `zenvis_insert_time` 由平台注入，插件不得定义或写入。
- 默认采用“源数据原样进入 Kafka、Kafka 消费后转换并写入 ClickHouse”的两段式任务。
- 可确定的异常记录进入插件独立 DLQ，不得静默丢弃或写入正常表。
- Vector 输出字段必须与对应实体业务属性完全一致。

### UI、看板与文档

- `04_ui` 的一级子目录对应独立运行时配置索引。
- 详情链接、IP 统计链接使用当前后端返回字段和安全 URL 规则。
- HTML 看板使用真实 API，提供加载、空数据、错误和刷新状态。
- README 的实体、任务、页面、环境变量和限制必须与实际文件同步。

## 启动、构建与测试

`agent-skills` 没有需要启动的服务，也没有独立编译产物。验证以元数据、引用和目标插件契约为主：

1. YAML front matter 和 `agents/openai.yaml` 可解析。
2. 技能中引用的仓库路径实际存在。
3. 技能名、默认提示词和说明一致。
4. 规则与当前 Meta、插件安装、低代码和看板契约一致。
5. 技能要求解析所有 JSON/YAML，并检查实体、Vector、UI 和菜单的标识对齐。
6. 技能要求执行目标插件 API 测试、Vector 校验和归档检查，不把静态检查描述为真实环境联调。
7. 不在技能中加入真实凭据、客户敏感数据或仅适用于单一插件的临时值。

## 扩展点

- 新增研发侧 Skill：在 `agent-skills/<skill-name>/` 增加 `SKILL.md`，需要客户端展示时同时提供 `agents/openai.yaml`。
- 扩展现有 Skill：优先增加可复用的契约检查、路由规则和验证步骤，不把某个客户插件的固定字段写成平台通用规则。
- 新增平台运行时 Skill：放入插件 `07_skill/` 或 `deploy/open_config/skill_config/`，由后端运行时契约管理，不放入本目录冒充运行时配置。

## 常见问题

### 研发 Skill 为什么没有出现在 ZenVis Skill 管理页

`agent-skills` 是研发工具源目录，不是平台 `skill_config`。需要在平台运行的 Skill 应放入开放配置或插件 `07_skill/`，并遵循后端 Skill 配置契约。

### 示例插件与代码不一致时听谁的

以当前后端和前端代码为准。记录不兼容点，再更新技能或插件；不要为迁就旧样例擅自改变平台代码。

### 修改技能后是否需要重打插件

研发 Skill 本身不进入插件归档。只有目标插件内容发生变化时才需要提高插件版本、重新校验并打包。

## 交付检查

- 说明修改原因和影响的插件环节；
- 列出核对过的后端、前端和插件契约；
- 给出已执行的静态验证和测试；
- 说明未执行的真实 Kafka、ClickHouse、Vectum 或浏览器联调；
- 确认 `agent-skills` 位于根工作树，提交时不混入其他独立工作树修改。

## 关联文档

- [插件开发与集成](/03-插件开发与集成/README.md)
- [`zenvis-plugin` 开发对接指南](/07-开发指南/zenvis-plugin-开发对接指南.md)
- [`zenvis-plugin-community` 开发对接指南](/07-开发指南/zenvis-plugin-community-开发对接指南.md)
- [AI 与数据智能](/04-AI与数据智能/README.md)
