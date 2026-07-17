# RetrievalController 数据检索接口

## 1. 基础信息

| 项目 | 说明 |
| :--- | :--- |
| 基础路径 | `/api/v1/retrieval` |
| 请求/响应 | `application/json` |
| JSON 命名 | `snake_case` |
| 认证 | 使用 ZenVis 登录会话 |
| 功能边界 | 单实体条件检索、元数据读取和个人过滤器管理 |

统一响应：

```json
{
  "status": 0,
  "msg": "success",
  "data": {}
}
```

`status = 0` 表示成功。业务异常通过非零状态和 `msg` 返回，前端应展示 `msg`，不要依赖某个固定的非零数值。

完整设计和限制见 [Retrieval 全局检索模块快速上手](../设计说明/retrieval-module.md)。

## 2. 接口总览

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| POST | `/do` | 执行普通或高级检索 |
| POST | `/rule/create` | 创建过滤器 |
| POST | `/rule/update` | 部分或完整更新过滤器 |
| POST | `/rule/delete` | 删除过滤器 |
| GET | `/rule/list` | 当前用户过滤器列表 |
| GET | `/rule/get?id=` | 获取旧版可执行规则结构，兼容接口 |
| GET | `/rule/detail?id=` | 获取编辑规则所需的配置、元数据和失效项 |
| GET | `/entity/list` | 实体列表 |
| GET | `/attribute/list` | 条件字段列表，兼容旧规则回填 |
| GET | `/candidate/list` | 字段候选值 |
| GET | `/display/entity/list` | 展示实体列表，兼容接口 |
| GET | `/display/attribute/list` | 展示字段及默认选择 |

新前端加载已保存过滤器时应使用 `/rule/detail`，不要并行调用三个旧回填接口。

## 3. 公共请求模型

### 3.1 RetrievalRequestDto

```json
{
  "type": "normal",
  "entity": "asset",
  "criteria_logic": "and",
  "criteria_list": [
    {
      "attribute": "ip",
      "operator": "equal",
      "value_list": ["10.0.0.1"]
    }
  ],
  "display_list": [
    {
      "entity": "asset",
      "attribute_list": ["ip", "device_name"]
    }
  ],
  "page": 1,
  "size": 10,
  "sort_by": "device_name",
  "order": "asc"
}
```

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `type` | `normal` / `advanced` | 检索模式 |
| `entity` | string | 当前逻辑实体名 |
| `criteria_list` | array | 普通检索条件；高级模式提交空数组或省略 |
| `criteria_logic` | `and` / `or` | 普通条件关系 |
| `sql` | string | 高级 where 表达式，不是完整 SQL |
| `display_list` | array | 展示实体及逻辑字段名列表 |
| `page` | integer | 页码，从 1 开始，默认 1 |
| `size` | integer | 页大小 1–200，默认 10 |
| `sort_by` | string | 当前实体逻辑字段名 |
| `order` | `asc` / `desc` | 排序方向 |

### 3.2 统一约束

- `entity` 必须存在。
- 只允许一个 `display_list` 项，且展示实体必须等于 `entity`。
- 展示字段必须为 1–100 个，不能重复或为空。
- 普通条件最多 50 个；单条件最多 200 个值，单值最长 2 KiB。
- `sort_by` 必须属于当前实体。
- 请求中的实体、属性和排序都使用逻辑 `name`，不要传物理表列名或 `label`。
- 日期条件格式为 `yyyy-MM-dd HH:mm:ss`，默认按 `Asia/Shanghai` 解释。

### 3.3 操作符

| 名称 | 说明 | 值数量 |
| :--- | :--- | :--- |
| `equal` | 等于 | 1 |
| `notequal` | 不等于 | 1 |
| `match` | 模糊匹配 | 1 |
| `greatthan` / `greatequalthan` | 大于 / 大于等于 | 1 |
| `lessthan` / `lessequalthan` | 小于 / 小于等于 | 1 |
| `between` | 范围 | 2 |
| `in` | 值集合 | 1–200 |
| `isnull` / `isnotnull` | 空 / 非空 | 0 |

字段实际可用操作符以元数据接口返回的 `operator_list` 为准。

## 4. 执行检索

### `POST /api/v1/retrieval/do`

普通检索请求：

```bash
curl -X POST http://localhost:11001/api/v1/retrieval/do \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "normal",
    "entity": "asset",
    "criteria_logic": "and",
    "criteria_list": [
      {"attribute": "ip", "operator": "equal", "value_list": ["10.0.0.1"]}
    ],
    "display_list": [
      {"entity": "asset", "attribute_list": ["ip", "device_name"]}
    ],
    "page": 1,
    "size": 10
  }'
```

高级检索请求：

```json
{
  "type": "advanced",
  "entity": "asset",
  "sql": "`ip` = '10.0.0.1' and risk_level >= 2",
  "display_list": [
    { "entity": "asset", "attribute_list": ["ip", "risk_level"] }
  ],
  "page": 1,
  "size": 10
}
```

成功响应：

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "total": 1,
    "datalist": [
      {
        "ip": "10.0.0.1",
        "device_name": "gateway-01"
      }
    ],
    "token": "288023ca-8ee1-4fd5-a67d-b1b384c1db30"
  }
}
```

响应记录的键始终是逻辑属性 `name`。即使元数据的 `column_name` 为 `dev_name`，响应仍使用 `device_name`。

### 高级表达式语法

支持：

```sql
field = 'value'
field != 'value'
field > 1 and field <= 10
field like '%text%'
field between 1 and 10
field in ('a', 'b')
field is null
field is not null
(field_a = 'a' or field_b = 'b') and field_c = 'c'
```

字段可用反引号包裹。支持普通引号和中文智能引号。禁止完整 SQL、函数、子查询、注释、分号拼接以及 `1=1` 之类无字段条件。

资源限制：表达式最长 8 KiB、最多 50 个条件、括号最多 10 层、单个 `IN` 最多 200 个值、单值最长 2 KiB。

## 5. 过滤器管理

过滤器接口都按 `create_by = 当前用户` 隔离。不存在和无权访问统一返回“检索规则不可用”。

### 5.1 创建

`POST /api/v1/retrieval/rule/create`

创建请求在公共查询配置上增加：

| 字段 | 必填 | 说明 |
| :--- | :--- | :--- |
| `rule_name` | 是 | 过滤器名称，不能为空 |
| `rule_description` | 否 | 描述 |
| `entity` | 是 | 实体 |
| `type` | 是 | `normal` 或 `advanced` |
| `display_list` | 是 | 至少一个展示字段 |

示例：

```json
{
  "rule_name": "高风险资产",
  "rule_description": "风险等级大于等于 2",
  "type": "normal",
  "entity": "asset",
  "criteria_list": [
    { "attribute": "risk_level", "operator": "greatequalthan", "value_list": ["2"] }
  ],
  "display_list": [
    { "entity": "asset", "attribute_list": ["ip", "risk_level"] }
  ]
}
```

成功响应：

```json
{
  "status": 0,
  "msg": "success",
  "data": { "id": 7 }
}
```

### 5.2 更新

`POST /api/v1/retrieval/rule/update`

- `id` 必填；请求体中的旧字段 `rule_id` 仍可读取。
- 缺失字段继承旧配置。
- `criteria_list: []` 表示主动清空普通条件。
- `display_list: []` 非法。
- 同一实体内可以只更新名称、条件或展示字段。
- 切换实体时必须显式提交新实体展示字段，以及普通 `criteria_list` 或高级 `sql`。
- 切换检索类型时必须提交目标类型所需的条件。

```json
{
  "id": 7,
  "rule_name": "高风险资产（已调整）",
  "criteria_list": []
}
```

成功响应同创建接口：`data` 为 `{ "id": 7 }`。更新会保留原 `create_by/create_time`，成功保存后旧格式规则自动转为 V2。

### 5.3 删除

`POST /api/v1/retrieval/rule/delete`

```json
{ "id": 7 }
```

兼容 `{ "rule_id": 7 }`。删除不解析和执行规则，因此失效规则也可以删除。

成功响应：

```json
{
  "status": 0,
  "msg": "success",
  "data": null
}
```

### 5.4 列表

`GET /api/v1/retrieval/rule/list`

按更新时间倒序返回当前用户规则：

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "total": 2,
    "datalist": [
      {
        "id": 7,
        "name": "高风险资产",
        "description": "风险等级大于等于 2",
        "create_time": "2026-07-15T10:00:00.000+08:00",
        "update_time": "2026-07-15T10:10:00.000+08:00",
        "status": "valid",
        "issue_count": 0
      }
    ]
  }
}
```

`invalid` 规则仍出现在列表中，但不能自动执行。

### 5.5 编辑详情

`GET /api/v1/retrieval/rule/detail?id=7`

该接口一次返回编辑过滤器需要的全部内容：

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": 7,
    "name": "高风险资产",
    "description": "风险等级大于等于 2",
    "create_time": "2026-07-15T10:00:00.000+08:00",
    "update_time": "2026-07-15T10:10:00.000+08:00",
    "config": {
      "type": "normal",
      "entity": "asset",
      "criteria_list": [
        { "attribute": "risk_level", "operator": "greatequalthan", "value_list": ["2"] }
      ],
      "criteria_logic": "and",
      "sql": null,
      "display_list": [
        { "entity": "asset", "attribute_list": ["ip", "risk_level"] }
      ]
    },
    "status": "valid",
    "issues": [],
    "entity_list": [
      { "name": "asset", "label": "资产", "description": "资产实体" }
    ],
    "attribute_list": [
      {
        "name": "risk_level",
        "label": "风险等级",
        "retrieval_type": "number",
        "display_type": null,
        "link_template": "/asset/risk?level={risk_level}",
        "auto_complete": false,
        "copyable": true,
        "operator_list": [
          { "name": "equal", "label": "等于" }
        ]
      }
    ]
  }
}
```

规则失效时 `status = invalid`，`issues` 可能包含：

| code | scope | 含义 |
| :--- | :--- | :--- |
| `LEGACY_SQL_DISABLED` | `rule` | 旧自由 SQL 规则已禁用 |
| `ENTITY_MISSING` | `entity` | 实体已删除或改名 |
| `DISPLAY_EMPTY` | `display` | 没有展示字段 |
| `DISPLAY_ENTITY_MISMATCH` | `display` | 展示实体与检索实体不一致 |
| `DISPLAY_FIELD_MISSING` | `display` | 展示字段不存在 |
| `CRITERIA_FIELD_MISSING` | `criteria` | 条件字段不存在 |
| `OPERATOR_MISSING` | `criteria` | 操作符不存在 |
| `INVALID_EXPRESSION` | `criteria` | 高级表达式无法解析 |
| `TYPE_INVALID` | `rule` | 检索类型不支持 |
| `RULE_INVALID` | `rule` | 其他规则校验失败 |

失效配置保留原字段和表达式，前端应允许用户编辑或删除，但不能自动执行。

### 5.6 旧规则结构

`GET /api/v1/retrieval/rule/get?id=7`

该接口返回绑定当前元数据后的 `RetrievalRule` 领域结构，用于旧调用方兼容。新前端不要使用它做表单回填。失效规则和旧自由 SQL 规则可能直接返回业务错误，编辑场景统一使用 `/rule/detail`。

## 6. 元数据接口

### 6.1 实体列表

`GET /api/v1/retrieval/entity/list`

可选参数 `rule_id` 仅为旧回填流程保留。携带规则 ID 时同样执行当前用户归属校验。

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "entity_list": [
      { "name": "asset", "label": "资产", "description": "资产实体" }
    ],
    "selected_entity": ["asset"]
  }
}
```

### 6.2 条件字段列表

`GET /api/v1/retrieval/attribute/list?entity=asset`

也可使用旧参数 `rule_id` 回填规则。响应：

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "entity": "asset",
    "attribute_list": [
      {
        "name": "ip",
        "label": "IP 地址",
        "retrieval_type": "string",
        "display_type": null,
        "link_template": "/asset/detail?ip={ip}",
        "auto_complete": true,
        "copyable": true,
        "operator_list": [
          { "name": "equal", "label": "等于" },
          { "name": "match", "label": "模糊匹配" }
        ]
      }
    ],
    "select_attribute_list": [],
    "criteria_logic": null,
    "sql": null
  }
}
```

### 6.3 展示字段列表

`GET /api/v1/retrieval/display/attribute/list?entity=asset`

响应结构与条件字段列表相同。`select_attribute_list` 为元数据中 `display_selected: true` 的默认展示字段。`/display/entity/list` 与 `/entity/list` 结构相同。

字段返回 `copyable: boolean`，表示结果单元格是否支持复制，元数据未配置时为 `false`。字段还可选返回 `link_template: string`，表示结果单元格的页面跳转模板。`{属性名}` 引用当前结果行中同名逻辑属性的值，前端替换时会执行 URL 编码；模板可使用相对地址或 `http/https` 地址。未配置时不返回该字段。前端不额外要求占位符属于可见列，只要求属性实际存在于结果行且值非空；当前普通属性需通过展示字段进入查询结果，平台内置 `{zenvis_id}` 则会在模板引用它时由后端自动作为隐藏查询字段随行返回，而不增加可见列。

### 6.4 候选值

`GET /api/v1/retrieval/candidate/list`

支持两种参数组合：

```text
?entity=asset&attribute=ip&text=10.0
?attributeId=101&text=10.0
```

同时提供 `entity + attribute` 时优先使用该组合。`attributeId` 必须对应元数据中非零且唯一的字段 ID。

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "total": 2,
    "datalist": ["10.0.0.1", "10.0.0.2"]
  }
}
```

候选值最多返回 50 项。数组字段未传 `text` 时会展开去重；普通字段执行 distinct；传入 `text` 时执行转义后的模糊匹配。

前端条件输入自动补全使用另一个通用实体接口：`GET /api/v1/entity/{entity}/{attribute}/auto-complete?term=`，不是本 Controller 的 candidate 接口。

## 7. 兼容和发布约定

- 保留所有旧 Retrieval REST 路径。
- 创建和更新成功统一返回 `{ id }`。
- 更新和删除请求推荐使用 `id`，继续兼容请求体 `rule_id`。
- 新保存规则使用 `schema_version: 2`；旧 compact/expanded JSON 按需读取，下次更新时转成 V2。
- 不批量改写存量数据库规则。
- 后端应先于使用 `/rule/detail` 的新前端发布。

## 8. 常见错误信息

| `msg` 示例 | 常见原因 |
| :--- | :--- |
| `检索实体不存在: ...` | 元数据未注册或实体逻辑名错误 |
| `展示实体必须与检索实体一致` | `entity` 与 `display_list[0].entity` 不同 |
| `展示字段数量必须为1到100个` | 未选择展示字段或字段过多 |
| `展示字段不存在: ...` | 传了旧字段、物理列名或元数据已漂移 |
| `高级where表达式操作符不正确` | 高级表达式不符合安全语法 |
| `检索字段不存在: ...` | 条件字段不属于当前实体 |
| `排序字段不存在: ...` | 实体切换后仍携带旧排序字段 |
| `检索规则不可用` | 规则不存在或不属于当前用户 |
| `旧自由SQL检索规则已禁用，请编辑后保存` | 存量自由 SQL 规则尝试执行 |
