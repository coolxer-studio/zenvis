# 检索元数据配置生成

当用户要求生成 ZenVis Retrieval 元数据、meta_config、实体配置、检索字段配置或 ClickHouse 自动建表配置时，输出可直接保存为 `meta_config/*.json` 的 JSON。

## 输出规则

- 只输出一个合法 JSON 对象；不要输出 Markdown、说明文字、注释、尾逗号或代码围栏。
- 顶层固定为 `entity`、`attribute`、`operator` 三个数组；字段名使用 snake_case。
- 以 Java 运行时代码为准，不以 `meta_schema.json` 的旧字段为准。禁止生成 `search_type`。
- 信息不足时先询问最小必要信息：实体含义、表名、字段清单、字段类型、主键/排序字段、时间字段、是否自动建表、是否需要 CRUD、趋势或聚合能力。

## Entity 规则

每个 `entity` 必填：

- `id`: 整数，当前文件内唯一。
- `name`: 实体名，稳定英文标识；会被 `MetaDataServiceImpl` 用作实体索引。
- `label`: 中文展示名。
- `description`: 实体说明。
- `table_name`: ClickHouse 物理表名，可带库名。
- `data_source`: 通常填 `clickhouse`。

可选字段：

- `sort_column`: 默认排序物理列名；如填写，必须存在于本实体 attribute 的 `column_name`。
- `auto_create`: 用于启动时自动建 ClickHouse 表。一旦生成，必须包含 `engine`、`order_by`、`partition_by`；`order_by` 中每个字段都必须是物理列名并存在于 attribute。

需要实体 CRUD/MCP 工具稳定工作时，必须包含物理列 `id`。需要 `entity_trend` 时，包含 `insert_time`。需要 `retrieval_msg_trend` 时，包含 `server_time` 和 `fact_type`。需要 `retrieval_msg_tag` 时，包含 `agenda_tags`，推荐类型为 `Array(String)`。

## Attribute 规则

每个 `attribute` 必填：

- `id`: 整数，当前文件内唯一。
- `entity`: 所属实体的 `name`。
- `name`: 属性逻辑名；前端、检索规则和实体接口会使用它。
- `label`: 中文展示名。
- `description`: 字段说明。
- `column_name`: ClickHouse 物理列名。
- `column_type`: ClickHouse 类型，例如 `String`、`Int32`、`Int64`、`UInt8`、`UInt32`、`Float64`、`DateTime64(3)`、`Array(String)`、`json`、`IPv4`、`Bool`。
- `operators`: 当前字段允许的检索操作符。
- `display_selected`: 布尔值，控制默认展示字段。

可选字段：

- `display_type`: 仅影响展示转换；`Array(String)` 用 `array`，`json` 字段用 `json`。
- `display_name`: 一般不要生成。中文展示名写入 `label`；若必须生成，值必须是可用于 SQL select/alias 与结果映射的字段名，不要填中文。
- `retrieval_type`: 仅当运行时需要把输入值转换成查询值时使用。当前代码只识别 `date`，并会把 `yyyy-MM-dd HH:mm:ss` 转为 epoch 毫秒，因此只适合实际按 epoch 毫秒存储的时间列；普通 `DateTime64(3)` 不要滥用。
- `must_candidate`: 为 `true` 时必须同时提供 `mapping`，且写入值必须来自 mapping 值集合。
- `mapping`: 字典映射对象，键和值使用稳定字符串或数字。
- `aggregate_link`: 只作为 UI/交互提示，不改变查询 SQL。

## Operator 规则

标准 operator 名称必须与后端 switch 分支完全一致：

- `equal`: 等于
- `notequal`: 不等于
- `match`: 模糊匹配
- `greatthan`: 大于
- `greatequalthan`: 大于等于
- `lessthan`: 小于
- `lessequalthan`: 小于等于
- `between`: 之间，值列表必须有两个值
- `in`: 包含，值列表可有多个值

凡被 attribute 引用的 operator，必须在顶层 `operator` 数组中定义。为保证单文件可独立加载，默认输出完整标准 operator 列表。

## 类型与操作符建议

- 字符串、IP、枚举字段：`equal`、`notequal`、`in`，需要模糊搜索时加 `match`。
- 数值字段：`equal`、`notequal`、`greatthan`、`greatequalthan`、`lessthan`、`lessequalthan`、`between`、`in`。
- 时间字段：如果是 `DateTime64(3)`，按实际 SQL 可比较值选择范围操作符；不要生成 `search_type`。
- 数组字段：设置 `display_type: "array"`；实体分页搜索支持数组 `has()`，通用 Retrieval 查询仍按普通 operator 拼 SQL，谨慎开放条件检索。
- JSON 字段：设置 `display_type: "json"`，通常只展示，不作为常规检索条件。

## 自检清单

输出前逐项检查：

- JSON 可解析，顶层只有 `entity`、`attribute`、`operator`。
- 实体 `name` 唯一；属性 `(entity,name)` 唯一；所有 `id` 不冲突。
- 每个 attribute 的 `entity` 都能匹配某个 entity `name`。
- `table_name`、`column_name`、`sort_column`、`auto_create.order_by` 都是物理名，不是中文。
- `auto_create.order_by`、`sort_column`、趋势/聚合硬编码字段都存在于对应 attribute。
- 所有 attribute 引用的 operator 都在 `operator` 数组中定义。
- 未出现 `search_type`；未把中文写入 `display_name`。
- 未编造用户没有提供且无法合理推断的业务字段；不确定的字段先询问。

## 最小输出骨架

生成新配置时使用这个结构，并替换占位值：

{
  "entity": [
    {
      "id": 1,
      "name": "example_entity",
      "label": "示例实体",
      "description": "示例实体表",
      "table_name": "zenvis.example_entity",
      "data_source": "clickhouse",
      "sort_column": "id",
      "auto_create": {
        "engine": "MergeTree()",
        "order_by": [
          "id"
        ],
        "partition_by": ""
      }
    }
  ],
  "attribute": [
    {
      "id": 1001,
      "entity": "example_entity",
      "name": "id",
      "label": "ID",
      "description": "主键",
      "column_name": "id",
      "column_type": "String",
      "operators": [
        "equal",
        "notequal",
        "in"
      ],
      "display_selected": true
    }
  ],
  "operator": [
    {
      "id": 1,
      "name": "equal",
      "label": "等于"
    },
    {
      "id": 2,
      "name": "notequal",
      "label": "不等于"
    },
    {
      "id": 3,
      "name": "match",
      "label": "模糊匹配"
    },
    {
      "id": 4,
      "name": "greatthan",
      "label": "大于"
    },
    {
      "id": 5,
      "name": "greatequalthan",
      "label": "大于等于"
    },
    {
      "id": 6,
      "name": "lessthan",
      "label": "小于"
    },
    {
      "id": 7,
      "name": "lessequalthan",
      "label": "小于等于"
    },
    {
      "id": 8,
      "name": "between",
      "label": "之间"
    },
    {
      "id": 9,
      "name": "in",
      "label": "包含"
    }
  ]
}
