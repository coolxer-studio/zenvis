# `zenvis-plugin` 开发对接指南

## 项目定位

`zenvis-plugin` 保存 ZenVis 平台内置插件、正式动态 API 源码和跨平台打包脚本。每个 `plugin-*` 目录是独立发布单元，可以生成 `.tar.gz` 并通过插件管理页面安装或升级。

平台核心只提供插件运行框架；业务数据模型、接入任务、API、页面、看板、MCP、Skill 和菜单应在插件内共同交付。

## 上下游关系

- 上游：数据字典、对接规范、后端插件/Meta/动态 API 契约、前端低代码与看板契约。
- 下游：插件归档、后端安装生命周期、Vectum/Kafka/ClickHouse 数据链路、前端菜单与页面。
- 发布边界：插件是独立版本单元；契约变化需要同步 `index.json`、实现、测试、`00_doc` 和发布说明，不直接修改平台公开 API。

## 技术栈与当前插件

插件描述层主要使用 JSON、YAML/TOML、Markdown 和 HTML；数据接入使用 Vector/Vectum，动态 API 使用 Java 17、Spring Boot 兼容 API 与 Maven reactor，发布格式为 `.tar.gz`。

版本以各插件 `index.json` 为准：

| 目录 | 包名 | 当前版本 | 作用 |
| --- | --- | --- | --- |
| `plugin-asset` | `com.coolxer.plugin.asset` | `1.2.1` | 资产管理 |
| `plugin-integrated` | `com.coolxer.plugin.integrated` | `1.0.0` | 探针集成 |
| `plugin-operation` | `com.coolxer.plugin.operation` | `1.1.1` | 运营分析 |
| `plugin-probe` | `com.coolxer.plugin.probe` | `2.0.1` | 探针数据采集 |
| `plugin-risk` | `com.coolxer.plugin.risk` | `1.1.1` | 风险监控 |
| `plugin-user-event` | `com.coolxer.plugin.user.event` | `1.0.4` | 用户事件分析与测试示例 |

修改发布内容时同步更新对应插件版本和文档，不要只修改根 README 中的版本表。

## 仓库结构

```text
zenvis-plugin/
├── pom.xml                         # 正式插件 API Maven reactor
├── api-common/                     # 动态 API 共用模型
├── extend-api/                     # 历史/独立 API 示例
├── build.conf
├── build.sh
├── build.ps1
├── plugin-asset/
├── plugin-integrated/
├── plugin-operation/
├── plugin-probe/
├── plugin-risk/
└── plugin-user-event/
```

根 Maven reactor 当前管理 `api-common` 以及资产、风险、运营插件的 `api-src` 模块。新增正式 API 模块时需要同时更新根 `pom.xml`。

## 插件目录契约

```text
plugin-xxx/
├── index.json
├── README.md
├── icon.png
├── 00_doc/
├── 01_meta/
├── 02_push-task/
├── 03_api/
│   ├── plugin-xxx-api.jar
│   └── migrations/mysql/
├── api-src/                        # 源码仓库存在，归档排除
├── 04_ui/
├── 05_dashboard/
│   ├── config.json
│   └── html-page/
├── 06_mcp/
├── 07_skill/
└── 08_menu/
```

| 位置 | 责任 |
| --- | --- |
| `index.json` | 名称、包名、版本、说明、作者和图标 |
| `00_doc` | 数据字典、对接说明、契约矩阵和运维说明 |
| `01_meta` | Entity、Attribute、Operator 和 ClickHouse 建表 |
| `02_push-task` | Vectum/Vector 任务注册与配置 |
| `03_api` | 最多一个动态 API 薄 Jar，以及 MySQL 迁移 |
| `api-src` | 动态 API Java 源码和测试，不进入归档 |
| `04_ui` | 低代码应用、独立页面和详情页 |
| `05_dashboard` | 看板配置和静态 HTML |
| `06_mcp` | 插件 MCP 服务配置 |
| `07_skill` | 平台运行时 Skill |
| `08_menu` | 安装生命周期最后导入的菜单 |

空能力使用合法空配置或目录占位，不要发明安装器不认识的目录或字段。

## 环境与配置

- JDK 17、Maven 3.x：动态 API；
- Bash 和 tar：Linux；
- Bash 和 GNU tar `gtar`：macOS；
- PowerShell 和 tar：Windows；
- 需要完整联调时使用 ZenVis 后端、Vectum、Kafka、ClickHouse、MySQL 和前端。

配置值应通过插件任务环境变量、安装环境或受控配置注入。不要在 Meta、Vector、HTML、README 或归档中写入真实数据库密码、令牌和客户地址。

## 开发工作流

### 1. 确认归属与标识

新增插件前确认：

- 插件应进入内置仓库还是社区仓库；
- `package_name`、Entity、表、Kafka topic、菜单和看板 code 全局不冲突；
- 结构化数据定义数量和字段顺序；
- 非结构化附件是否需要独立存储；
- 需要的 API、UI、看板、MCP 和 Skill。

建议先建立：

| 数据定义 | 代码 | Entity | 表 | Kafka topic | UI 页面 | 是否结构化 |
| --- | --- | --- | --- | --- | --- | --- |

### 2. 维护 Meta

Meta JSON 顶层使用 `entity`、`attribute`、`operator`。开发时至少保证：

- Entity 和 Attribute ID 在加载范围内唯一；
- 标识符、实体引用和操作符引用有效；
- `sort_column`、`order_by` 和 Vector 输出均能解析到物理列；
- 每个结构化定义对应一个 Entity 和一张 ClickHouse 表；
- `zenvis_id`、`zenvis_insert_time` 由平台注入，插件不定义、不转换、不写入；
- 类型、显示、复制、链接、映射和说明来自数据规范；
- 第一业务标识和详情链接符合当前后端、前端契约。

### 3. 维护推送任务

新任务默认使用 YAML，并采用两段式结构：

```text
外部来源 → 原始记录 → Kafka
Kafka → 校验/转换 → ClickHouse
                  └→ 插件独立 DLQ
```

- 来源到 Kafka 阶段原样转发，不解析或丢弃业务字段。
- Kafka 到 ClickHouse 阶段按数据字典转换。
- 转换输出字段与 Meta 业务属性完全一致。
- 字段数量、必填、类型、枚举和解码失败进入 DLQ。
- DLQ 保留原始 `message` 和可操作的失败原因，不回流正常 topic。
- Kafka、ClickHouse、认证和调优项使用环境变量。

历史插件可能使用 TOML；没有明确迁移需求时不要只为格式统一改写稳定任务。

### 4. 动态 API 与迁移

正式动态 API 放在插件 `api-src`，使用 Java 17 和薄 Jar：

- Controller 只声明插件内相对路径；
- 后端自动增加 `/api/v1/plugin/{package_name}`；
- 使用插件允许的 Repository、Service 和公共模型；
- 每个插件归档最多一个 API Jar；
- Jar 不包含 `BOOT-INF`，不把整个 Spring Boot 应用打入插件。

MySQL 迁移放在 `03_api/migrations/mysql`：

```text
V001__init_schema.sql
V002__add_status_index.sql
```

平台记录版本和 SHA-256。已执行文件禁止修改，后续变化新增更高版本；卸载插件不删除业务表和迁移历史。

### 5. UI、看板和菜单

`04_ui` 一级子目录对应独立配置索引：

```text
04_ui/
├── app/                  # <package_name>.app
├── ip-statistics/        # <package_name>.ip-statistics
└── detail-event/         # <package_name>.detail-event
```

- 含 `site.json` 时为低代码应用；只有 `index.json` 时为独立页面。
- 菜单 `params`、应用 `schemaApi` 和 Meta `link_template` 使用完整配置索引。
- 详情页通过当前实体 API 读取记录，不复用错误实体。
- URL 只允许相对路径或安全的 `http/https`。

`05_dashboard/config.json` 使用当前 `DashboardDto` 数组。HTML 看板的 `html_path` 是相对 `05_dashboard/html-page` 的插件内路径，不包含运行时 `/zenvis/html-page/` 前缀。

### 6. 文档与版本

插件 README 和 `00_doc` 至少说明：

- 适用场景、数据来源和边界；
- Entity、表、topic 和页面映射；
- 环境变量、时区、编码和 Base64 策略；
- DLQ、重放、保留和运维责任；
- API、安装顺序、升级兼容和限制；
- 已执行和未执行的验证。

发布内容发生实质变化时提高 `index.json` 版本。README 声明必须与当前文件和归档一致。

## 构建与测试

### API 测试

```bash
cd zenvis-plugin
mvn test
```

也可以定向测试 Maven 模块：

```bash
mvn -pl :plugin-asset-api -am test
```

### 打包

macOS/Linux：

```bash
bash build.sh plugin-asset plugin-risk
```

Windows：

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1 plugin-asset plugin-risk
```

打包脚本会构建 API、删除 `03_api` 中旧 Jar、复制新 Jar并在仓库根目录生成归档。这些操作会改变工作树或生成文件；只在明确准备发布时执行。

## 归档验证

生成归档后不能只检查源码目录。至少验证：

1. 归档根目录直接包含 `index.json`，没有多余顶层目录。
2. 包名、版本、README、图标正确。
3. Meta、推送任务、UI、看板、MCP、Skill 和菜单为最新内容。
4. `api-src`、`.DS_Store`、日志、旧归档和构建缓存未进入包。
5. `03_api` 最多一个薄 Jar，迁移完整。
6. JSON/YAML 可解析，Vector 配置由目标版本校验。
7. Entity、表、topic、转换、页面和看板标识一致。

## 扩展点

- 新增数据模型：扩展 `01_meta`，同步 Vector 输出、页面字段、查询链接和数据字典。
- 新增接入任务：扩展 `02_push-task`，明确输入、转换、DLQ、目标表、凭据和重放边界。
- 新增动态 API：在 `api-src` 增加 Maven 模块并纳入根 reactor，归档 `03_api` 仍只保留一个薄 Jar。
- 新增页面或看板：使用 `04_ui`、`05_dashboard` 和 `08_menu` 对齐配置索引、路由和菜单。
- 新增 MCP 或运行时 Skill：使用 `06_mcp`、`07_skill`，保持工具白名单、审批策略和插件卸载边界。

## 常见问题

### 安装后实体或页面缺失

检查归档是否包含对应目录、配置索引是否冲突、JSON 是否为安装器预期结构，以及插件日志是否在前一阶段失败。

### 数据已进入 Kafka 但 ClickHouse 无记录

检查 Vector 转换字段、DLQ、目标表、ClickHouse 认证和时间解析。不要通过关闭校验隐藏异常数据。

### 动态 API 未注册

检查 Jar 数量、Jar 结构、包名、Controller/Service 扫描、公共依赖版本和后端动态扩展日志。

### 升级提示迁移校验和变化

说明已执行的迁移文件被修改。恢复原文件，并新增更高版本迁移。

### 低代码详情跳转后记录为空

检查 `link_template` 使用逻辑字段、后端返回链接依赖、`zenvis_id` 和目标详情页 Entity。

## 交付检查

- 数据字典、契约矩阵、Meta、Vector、UI 和文档一致；
- 所有 JSON/YAML 可解析，动态 API 测试通过；
- 无重复 ID、Entity、表、topic、配置索引和菜单 code；
- 异常记录进入 DLQ，正常记录不会写入错误实体；
- 版本、迁移和归档内容正确；
- 在 `zenvis-plugin` 独立工作树检查状态，不覆盖其他未提交插件修改。

## 关联文档

- [插件开发与集成](/03-插件开发与集成/README.md)
- [插件包规范](/03-插件开发与集成/插件包规范.md)
- [生命周期与发布验证](/03-插件开发与集成/生命周期与发布验证.md)
- [`agent-skills` 开发对接指南](/07-开发指南/agent-skills-开发对接指南.md)
- [`zenvis-plugin-community` 开发对接指南](/07-开发指南/zenvis-plugin-community-开发对接指南.md)
- [`zenvis-backend` 开发对接指南](/07-开发指南/zenvis-backend-开发对接指南.md)
