# Meta 与数据建模

Meta 是插件数据能力的核心契约。它同时决定检索实体、ClickHouse 表结构、查询字段、低代码页面展示和链接跳转。

## 基本结构

`01_meta/` 可以包含一个或多个 JSON 文件。平台会合并加载，顶层使用三个数组：

```json
{
  "entity": [],
  "attribute": [],
  "operator": []
}
```

一个新插件通常使用一个 Meta 文件。拆分文件时，Entity、Attribute ID、Entity 名称和同一 Entity 下的字段名称仍必须在合并范围内唯一。

## 一对一建模

每个结构化数据定义对应一个 Entity 和一张 ClickHouse 表：

```text
数据定义
  ├── 字段顺序与语义 ──→ Attribute
  ├── 业务记录类型 ────→ Entity
  ├── 存储目标 ────────→ ClickHouse Table
  ├── 数据转换 ────────→ Vector Transform
  └── 用户入口 ────────→ 列表页 / 详情页
```

不要把含义不同的命令结果、事件、报告或样本合并成一个“通用结果”实体。附件、PCAP 和归档等非结构化内容默认不写入 ClickHouse。

## Entity

```json
{
  "id": 12001,
  "name": "example_event",
  "label": "示例事件",
  "description": "来自示例数据源的安全事件",
  "table_name": "msg_example_event",
  "data_source": "clickhouse",
  "sort_column": "event_time",
  "auto_create": {
    "engine": "MergeTree()",
    "order_by": ["event_id", "event_time"],
    "partition_by": "toYYYYMM(zenvis_insert_time)"
  }
}
```

| 字段 | 规则 |
| --- | --- |
| `id` | 使用稳定正整数，并避免与所有已加载 Meta 冲突 |
| `name` | 逻辑实体名，使用安全的 snake_case 标识 |
| `label` | 面向用户的名称 |
| `description` | 说明来源、含义和边界 |
| `table_name` | ClickHouse 物理表名，可以按当前校验规则使用一个数据库限定符 |
| `data_source` | 插件分析表通常为 `clickhouse` |
| `sort_column` | 必须引用该 Entity 已定义的物理 `column_name` |
| `auto_create` | 自动建表配置；使用已有表时可以不提供 |

`auto_create` 必须包含非空引擎和排序键。排序键优先选择业务 ID 和业务时间；不要把高基数的 `zenvis_id` 作为排序键，也不要按不可靠的可选业务时间分区。

## Attribute

```json
{
  "id": 1200101,
  "entity": "example_event",
  "name": "event_id",
  "label": "事件 ID",
  "description": "数据源生成的事件唯一标识",
  "column_name": "event_id",
  "column_type": "String",
  "operators": ["equal", "notequal", "in"],
  "display_selected": true,
  "copyable": true,
  "link_template": "/#/service/low-code-page/com.example.plugin.analytics.detail-event?record_id={zenvis_id}"
}
```

| 字段 | 规则 |
| --- | --- |
| `id` | 稳定正整数，在全局 Meta 中唯一 |
| `entity` | 引用已存在的 Entity `name` |
| `name` | API、查询条件和链接占位符使用的逻辑名称 |
| `column_name` | ClickHouse 物理列名；除兼容已有表外与 `name` 保持一致 |
| `column_type` | 真实存储类型 |
| `retrieval_type` | 后端查询值转换提示，仅在当前查询契约需要时使用 |
| `search_type` | 当前模型仍支持的前端输入组件提示；维护旧插件时保留，新插件不机械添加 |
| `display_type` | 数组、JSON、日期等特殊展示提示 |
| `operators` | 该字段允许的检索操作符 |
| `display_selected` | 是否进入默认结果列 |
| `must_candidate` | 输入是否必须来自 `mapping` 候选 |
| `auto_complete` | 是否启用有限候选的自动补全 |
| `copyable` | 是否提供复制操作 |
| `mapping` | 展示值与存储值之间的受限映射 |
| `link_template` | 当前行字段驱动的安全跳转模板 |

描述应写清格式、单位、枚举、分隔符、编码和条件必填关系，不要只重复字段标签。

## 平台保留字段

平台为每个 Entity 注入：

| 字段 | 类型 | 用途 |
| --- | --- | --- |
| `zenvis_id` | `Nullable(UUID)` | 平台记录唯一 ID |
| `zenvis_insert_time` | `DateTime64(3)` | 平台写入时间 |

插件不应在业务 Attribute、Vector 转换或 ClickHouse Sink 中显式定义或写入这两个字段。`zenvis_insert_time` 可以用于 `partition_by`；详情链接可以引用 `{zenvis_id}`。

历史 Meta 若同时以保留逻辑名和保留列名显式配置字段，平台会用内置定义替换；只占用其中一个名称则会被拒绝。

## 类型选择

| 数据含义 | 推荐 `column_type` | 说明 |
| --- | --- | --- |
| 文本、ID、URL、哈希、混合 IPv4/IPv6、Opaque Base64 | `String` | 不从示例标点推断数组或 JSON |
| 有符号整数 | 最小安全的 `Int*` | 按规范范围选择 |
| 非负计数或枚举 | 最小安全的 `UInt*` | 保留未来取值空间 |
| 比例、测量值 | `Float64` 或明确精度的 `Decimal` | 精确金额等场景需说明精度 |
| 布尔语义 | `Bool` | 转换阶段拒绝不合法取值 |
| 秒级时间 | `DateTime` | 明确时区 |
| 毫秒级时间 | `DateTime64(3)` | 明确输入格式与时区 |
| 重复值 | `Array(String)` | 同时设置 `display_type: "array"` |
| 明确 JSON | `json` | 同时设置 `display_type: "json"` |

只有规范明确声明解码内容时才解码 Base64。未声明或二进制含义的 Base64 保持 `String`。

## Operator

平台会补充内置操作符，但插件仍应让字段的意图清晰。常用集合：

| 类型 | 常用操作符 |
| --- | --- |
| String / JSON | `equal`、`notequal`、`isnull`、`isnotnull`、`in`；需要文本匹配时增加 `match` |
| Array | `equal`、`notequal`、`isnull`、`isnotnull`、`in`、`match` |
| Number | 上述基础比较及 `greatthan`、`greatequalthan`、`lessthan`、`lessequalthan`、`between` |
| Date | `equal`、`notequal`、空值判断、大小比较和 `between` |

自定义 Operator 示例：

```json
{
  "id": 1,
  "name": "equal",
  "label": "等于"
}
```

Attribute 引用的每个 Operator 必须能在合并后的 Meta 中解析。

## 显示、复制与链接

- 默认列优先展示业务 ID、时间、对象、状态和关键结果。
- 大型 JSON、正文和载荷字段通常不设为默认列。
- ID、IP、域名、URL、文件路径和哈希适合 `copyable: true`。
- 每个结构化 Entity 应提供明确的列表和详情入口。

`link_template` 只能是字符串，允许：

- 以 `/` 开头的平台相对 URL；
- 绝对 `http/https` URL；
- 同一 Entity 的 `{逻辑字段名}` 占位符；
- 平台字段 `{zenvis_id}`。

禁止协议相对 URL、反斜杠路径、控制字符以及 `javascript:`、`data:`、`blob:`、`file:` 等协议。旧的 `aggregate_link` 已废弃。

推荐的详情链接：

```text
"link_template": "/#/service/low-code-page/com.example.plugin.analytics.detail-event?record_id={zenvis_id}"
```

详情页通过当前实体接口读取记录：

```text
GET /api/v1/entity/{entity}/{record_id}/view
```

## 与接入和页面保持一致

对每个 Entity 检查：

1. Vector 转换输出字段集合与业务 Attribute 完全一致。
2. `sort_column` 和 `auto_create.order_by` 都能解析到物理列。
3. ClickHouse Sink 目标表等于 Entity `table_name`。
4. 列表页和详情页使用 Entity `name`，不混用表名。
5. `link_template` 引用的逻辑字段能从结果行获得。
6. 数据字典、Meta、转换和 UI 对相同字段使用相同语义。

## 升级约束

当前安装器对 Meta 升级采用新增式校验：

- 不允许删除或重命名已有 Entity；
- Entity ID、表名、数据源、引擎、排序键和分区键不可修改；
- 不允许删除或重命名已有 Attribute；
- Attribute ID、物理列名和字段类型不可修改；
- 同一 Entity 内不能出现重复物理列；
- 可以新增 Entity 和 Attribute，但必须满足全局唯一性和 SQL 安全校验。

需要破坏性变更时，应设计新 Entity/新表和明确的数据迁移方案，不能通过覆盖旧 Meta 隐式完成。

## 检查清单

- JSON 可解析，顶层数组名称正确。
- 每个结构化定义只映射一个 Entity 和一张表。
- Entity、Attribute ID 与名称无冲突。
- 字段引用、Operator、链接占位符全部可解析。
- 表名、列名、类型、引擎和表达式不包含危险 SQL 片段。
- 插件没有显式定义或写入保留字段。
- 类型、时区、枚举、Base64 和空值策略来自数据规范。
- Meta、Vector、UI 和 `00_doc` 保持一致。

## 关联文档

- [数据接入与推送任务](/03-插件开发与集成/数据接入与推送任务.md)
- [UI、看板与菜单](/03-插件开发与集成/UI看板与菜单.md)
- [数据与检索架构](/06-架构设计/数据与检索架构.md)
