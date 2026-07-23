# 插件开发与集成

本主题包含平台统一契约和原有完整开发资料：

- [插件开发完整指南](plugin-development.md)
- [产品侧插件应用说明](../01-产品与使用/使用手册/插件应用-插件应用介绍.md)
- [探针集成说明](../01-产品与使用/使用手册/插件应用-探针集成.md)
- [探针管理说明](../01-产品与使用/使用手册/插件应用-探针管理.md)

## 插件模型

ZenVis 插件是可安装的业务能力包。一个插件可以同时提供数据模型、数据接入、动态 API、低代码 UI、看板、MCP、Skill、菜单和用户文档。

```text
plugin-name/
├── index.json
├── README.md
├── icon.png
├── 00_doc/
├── 01_meta/
├── 02_push-task/
├── 03_api/
├── 04_ui/
├── 05_dashboard/
├── 06_mcp/
├── 07_skill/
└── 08_menu/
```

平台安装时按能力类型处理这些目录，并记录来源，支持查看日志、升级、导出和卸载。

插件上传大小受后端 multipart 配置限制，当前上限为 300MB。归档仍应保持最小化，不把 `api-src`、构建目录、样本数据或旧归档带入发布包。

## 插件索引

`index.json` 定义基础身份：

```json
{
  "name": "示例插件",
  "package_name": "com.example.plugin",
  "version": "1.0.0",
  "description": "插件描述",
  "author": "Example",
  "icon": "icon.png"
}
```

- `package_name` 使用稳定 Java 包名风格并在平台内唯一；
- 物料发生可分发变更时应升级 `version`；
- 归档文件名通常把包名中的 `.` 转换为 `-`；
- 插件代码、配置和文档中的包名必须一致。

## Meta 与 ClickHouse

### 顶层结构

Meta JSON 使用三个顶层数组：

```json
{
  "entity": [],
  "attribute": [],
  "operator": []
}
```

### 实体

每个结构化数据定义映射为一个实体和一个 ClickHouse 表。实体至少需要稳定 ID、名称、标签、描述、表名和数据源。使用 ClickHouse 时通常配置自动建表：

```json
{
  "engine": "MergeTree()",
  "order_by": ["event_id", "event_time"],
  "partition_by": "toYYYYMM(zenvis_insert_time)"
}
```

`zenvis_id` 和 `zenvis_insert_time` 由平台注入，插件不得显式定义、转换或写入这两个保留字段。

### 属性

属性定义逻辑 `name` 和物理 `column_name`。除兼容已有表外，两者保持一致。类型应选择符合业务含义的最窄类型：

| 业务类型 | Meta 类型 |
| --- | --- |
| 文本、ID、URL、混合 IP、Opaque Base64 | `String` |
| 整数、计数、枚举 | 对应 `Int*` 或 `UInt*` |
| 小数、比例 | `Float64` 或明确精度的 `Decimal` |
| 布尔 | `Bool` |
| 时间 | `DateTime` 或 `DateTime64(3)` |
| 重复值 | `Array(String)`，`display_type: array` |
| 明确 JSON | `json`，`display_type: json` |

不要仅根据示例中的标点推断数组或 JSON。Base64 只有在规范明确说明解码后内容时才解码。

### 显示与链接

- `display_selected` 控制默认展示字段；
- `copyable` 适合 ID、IP、URL、哈希和任务编号；
- `mapping` 与 `must_candidate` 定义受限枚举；
- `auto_complete` 只用于有限、可复用候选；
- `link_template` 只允许相对 URL 或绝对 `http/https` URL；
- 模板占位符只能引用同一实体逻辑属性或平台提供的 `{zenvis_id}`。

## 数据推送任务

默认使用 YAML 定义 Vector 服务，并拆分为两个阶段：

1. Source → Kafka：按源协议接收原始记录，不解析、不重命名、不丢字段；
2. Kafka → ClickHouse：校验字段、转换类型并写入对应实体表。

每个转换输出字段集合必须与 Meta 业务属性完全一致。无效字段数、必填缺失、类型错误、枚举错误和必要解码失败应进入插件独立 DLQ，不得静默丢弃或写入正常表。

关键配置通过环境变量提供：Kafka、ClickHouse、认证、批次、重试、缓冲和 DLQ topic。短暂的 DLQ 故障不能导致异常记录无声丢失。

`02_push-task/config.json` 注册插件提供的推送任务，并使用清晰的名称和描述。

## 动态 API 与迁移

插件 API 源码位于插件仓库的 `api-src/`，打包后的单一薄 Jar 放入 `03_api/`。业务类位于 `com.coolxer.plugin` 包下，运行时发现 Spring stereotype 并自动增加路径前缀：

```text
/api/v1/plugin/{package_name}
```

插件不应引用 ZenVis 后端内部业务 Entity、Repository、DTO 或工具类。平台与插件之间只使用公开 Bean、Spring/Jackson API 和插件自身模型。

MySQL 迁移放在：

```text
03_api/migrations/mysql/Vnnn__description.sql
```

平台记录版本和 SHA-256。已经执行的迁移禁止修改；后续变更只能新增更高版本。卸载不删除业务表和迁移历史。

## UI 与页面

`04_ui` 的一级子目录表示独立配置：

```text
04_ui/
├── app/                 # site.json，低代码应用
├── ip-statistics/       # index.json，独立页面
└── detail-event/        # index.json，实体详情
```

运行时配置索引为 `<package_name>.<目录名>`。应用的 `schemaApi`、菜单 `params` 和 Meta 链接必须使用完整索引。

- 包含 `site.json` 时按低代码应用安装；
- 没有 `site.json` 时必须提供 `index.json`；
- 每个结构化实体应有明确的列表与详情入口；
- 多实体 IP 调查中，同一实体的源/目的 IP 使用 OR，避免一条记录重复计数。

## 看板

`05_dashboard/config.json` 使用与平台 `DashboardDto` 兼容的数组。HTML 看板示例：

```json
[
  {
    "name": "安全态势总览",
    "code": "com.example.plugin.dashboard.security-overview",
    "type": "HTML_PAGE",
    "html_path": "security-overview.html"
  }
]
```

HTML 文件放在 `05_dashboard/html-page/`，`html_path` 只写相对于该目录的文件路径。不要写部署 URL、`/html-page/` 或包名。

静态看板应使用真实 ZenVis API，提供加载、空数据、失败、最后更新时间、手动刷新和自动刷新状态。没有实时性依据时，不应把历史数据存在描述为链路健康。

## MCP、Skill 与菜单

- `06_mcp/config.json` 可以注册外部 MCP 服务，也可以为空数组；
- `07_skill/` 可以包含一个或多个 Skill；
- `08_menu/config.json` 在安装生命周期末尾创建菜单；
- 菜单、UI 配置索引和页面路由必须一致；
- 安装后仍需给角色分配菜单权限。

## 文档与 RAG

- 根 `README.md` 介绍插件能力、安装方式和运维限制；
- `00_doc/` 保存数据字典、接入契约和可进入 RAG 的资料；
- README、Meta、Vector、UI 和看板声明必须保持一致；
- 二进制附件、PCAP 和样本文件不应仅为了 RAG 被写入 ClickHouse。

## 打包和安装

在插件仓库执行：

```bash
cd zenvis-plugin
bash build.sh plugin-name
```

Windows：

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1 plugin-name
```

安装前检查归档：

1. 根目录包含 `index.json`、README 和图标；
2. Meta、推送任务、UI、看板、菜单与文档为最新版本；
3. `03_api` 最多一个薄 Jar；
4. 归档不包含 `api-src`、`BOOT-INF`、`.DS_Store` 或旧构建物；
5. JSON/YAML 可解析，版本和包名一致。

随后在系统管理的插件管理页面上传、查看、安装，并检查安装日志和生成资源。

## 专项插件资料

以下目录是各插件的权威专项说明，保持在插件包内：

| 仓库 | 插件 | 包名 | 当前版本 | 专项资料 |
| --- | --- | --- | --- | --- |
| 内置 | 资产管理 | `com.coolxer.plugin.asset` | 1.1.0 | [README](../../zenvis-plugin/plugin-asset/README.md) |
| 内置 | 探针集成 | `com.coolxer.plugin.integrated` | 1.0.0 | [README](../../zenvis-plugin/plugin-integrated/README.md) |
| 内置 | 运营分析 | `com.coolxer.plugin.operation` | 1.1.0 | [README](../../zenvis-plugin/plugin-operation/README.md) |
| 内置 | 数据采集 | `com.coolxer.plugin.probe` | 1.0.0 | [README](../../zenvis-plugin/plugin-probe/README.md) |
| 内置 | 风险监控 | `com.coolxer.plugin.risk` | 1.1.0 | [README](../../zenvis-plugin/plugin-risk/README.md) |
| 内置 | 用户事件 | `com.coolxer.plugin.user.event` | 1.0.0 | [README](../../zenvis-plugin/plugin-user-event/README.md) |
| 社区 | 僵木蠕流量检测 | `com.coolxer.plugin.jmr` | 2.7.2 | [仓库说明](../../zenvis-plugin-community/zenvis-plugin-jmr/README.md) / [插件 README](../../zenvis-plugin-community/zenvis-plugin-jmr/plugin-jmr/README.md) |
| 社区 | Lubinsun 智能任务 | `com.coolxer.plugin.lubinsun` | 2.0.0 | [README](../../zenvis-plugin-community/zenvis-plugin-xiangtanhospital/plugin-lubinsun/README.md) |
| 社区 | 安全设备数据（含 STA） | `com.coolxer.plugin.security.device.data` | 1.3.0 | [README](../../zenvis-plugin-community/zenvis-plugin-xiangtanhospital/plugin-security-device-data/README.md) |

社区集合的构建、依赖和部署顺序见[僵木蠕仓库说明](../../zenvis-plugin-community/zenvis-plugin-jmr/README.md)与[湘潭医院仓库说明](../../zenvis-plugin-community/zenvis-plugin-xiangtanhospital/README.md)。插件开发自动化工作流见 [`create-zenvis-plugin` Skill](../../agent-skills/create-zenvis-plugin/SKILL.md)；该 Skill 要求从当前后端、前端契约重新核对 Meta、Vector、UI、看板和文档，不能以旧样例代替代码事实。

具体数据字典、字段兼容和生成工具位于相应 `00_doc/`，不由整体文档替代。
