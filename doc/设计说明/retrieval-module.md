# Retrieval 全局检索模块快速上手

本文面向第一次接触 ZenVis 全局检索的新同事，说明 Retrieval 模块解决什么问题、一次查询如何流转、已保存过滤器如何管理，以及修改代码和元数据时最容易踩到的边界。

相关文档：

- [全局检索使用手册](../使用手册/功能说明-全局检索.md)
- [Retrieval REST API](../api接口文档/RetrievalController.md)
- 前端仓库 `doc/全局检索模块开发指南.md`

## 1. 模块定位

Retrieval 是一个元数据驱动的单实体查询模块。业务数据仍存放在 ClickHouse 等上游数据源中，ZenVis 通过元数据把“逻辑实体和字段”映射到实际表列，并向用户提供统一的条件编辑、结果展示和过滤器复用能力。

模块包含四类核心对象：

| 对象 | 作用 |
| :--- | :--- |
| 实体 `entity` | 对应一类可检索数据及其物理表 |
| 属性 `attribute` | 对应逻辑字段、物理列、展示方式和可用操作符 |
| 查询配置 | 当前页面中的条件、展示列、分页和排序 |
| 过滤器/规则 | 保存到 MySQL、可再次加载的查询配置 |

当前明确边界：

- 一次请求只支持一个实体，不支持跨实体查询。
- 展示实体必须与检索实体一致。
- 普通检索和高级检索最终都转换为受控条件树，不提供自由 SQL 执行入口。
- 已保存过滤器按创建用户个人隔离。
- 当前没有实体级、字段级或行级数据权限模型；`RetrievalAccessPolicy` 是后续扩展点，默认实现只保留接口边界。

## 2. 用户看到的产品行为

### 2.1 新建查询

1. 页面加载实体列表，并选中默认实体。
2. 加载该实体的字段元数据和默认展示列。
3. 用户选择普通条件或填写高级 where 表达式。
4. 页面将实体、条件、展示列、分页和排序一次提交到 `/api/v1/retrieval/do`。
5. 后端校验逻辑字段后生成物理查询，响应记录始终使用逻辑字段 `name` 作为键。

### 2.2 保存和复用过滤器

- 新建过滤器必须包含名称、实体、类型和至少一个展示字段。
- 左侧规则列表只显示当前用户创建的过滤器，并按更新时间倒序排列。
- 选择过滤器时，前端只调用一次 `/rule/detail`，原子应用实体、条件、展示列和失效信息。
- 有效过滤器加载完成后自动查询一次；失效过滤器只进入编辑态，不会自动执行。
- 更新支持部分提交。同一实体内未提交的字段继承旧值，显式提交 `criteria_list: []` 表示清空普通条件。
- 切换实体时不得继承旧实体字段，必须显式提交新实体的展示字段和相应条件。
- 删除当前过滤器后，页面和本地缓存一起回到“新建过滤器”状态。

### 2.3 元数据变化后的失效规则

过滤器保存的是逻辑字段名，不复制完整元数据。字段删除、改名或操作符变化后，规则不会被自动改写或丢弃，而是变为 `invalid`：

- 规则列表展示失效状态和问题数量。
- 规则详情返回具体 `issues`。
- 普通条件和展示字段可由用户主动移除或替换。
- 高级表达式保留原文，用户修正后才能再次保存和执行。
- 旧版本自由 SQL 规则标记为 `LEGACY_SQL_DISABLED`，永不执行。

## 3. 一次查询的前后端流转

```text
retrieval/index.vue（唯一查询状态源）
        │
        ├── filter.vue 编辑实体、条件和表达式
        ├── table.vue 编辑展示列、分页和排序
        │
        ▼
POST /api/v1/retrieval/do
        │
        ▼
RetrievalController
        │
        ▼
RetrievalRuleServiceImpl
  校验请求 → 解析高级表达式 → 绑定当前元数据
        │
        ▼
DataQueryServiceImpl
  生成查询上下文和逻辑字段映射
        │
        ▼
QueryEngineImpl
  生成受控 ClickHouse 查询 → 分页/排序 → 结果映射
        │
        ▼
{ total, datalist, token }
```

关键代码位置：

| 层次 | 主要文件 | 职责 |
| :--- | :--- | :--- |
| REST | `controller/retrieval/RetrievalController.java` | 接口、当前用户解析和基础请求检查 |
| MCP | `controller/retrieval/RetrievalMcpTool.java` | Agent 工具入口；规则写操作需要审批并复用归属校验 |
| 编排 | `service/retrieval/impl/RetrievalServiceImpl.java` | 查询、规则、元数据和候选值能力编排 |
| 规则 | `service/retrieval/impl/RetrievalRuleServiceImpl.java` | 请求校验、规则生命周期、V2/旧格式兼容和失效分析 |
| 表达式 | `service/retrieval/impl/WhereExpressionParser.java` | 无共享游标的受限 where 解析器 |
| 元数据 | `service/retrieval/impl/MetaDataServiceImpl.java` | 加载、校验并原子切换不可变元数据快照 |
| 查询 | `service/retrieval/impl/DataQueryServiceImpl.java`、`QueryEngineImpl.java` | 逻辑条件到物理查询及结果映射 |
| 权限扩展 | `service/retrieval/RetrievalAccessPolicy.java` | 数据读取访问策略扩展点 |

## 4. 查询请求约定

所有 REST JSON 字段使用 `snake_case`。典型请求如下：

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

统一约束：

| 项目 | 约束 |
| :--- | :--- |
| 实体 | 必须存在，只允许一个实体 |
| 普通条件 | 最多 50 个；`criteria_logic` 为 `and` 或 `or` |
| 条件值 | 单条件最多 200 个值，单值最长 2 KiB |
| 展示字段 | 只能有一个展示实体，字段数 1–100，不能重复或为空 |
| 排序 | `sort_by` 必须是当前实体的逻辑字段，`order` 仅为 `asc/desc` |
| 分页 | `page >= 1`，`size` 为 1–200；默认 1/10 |
| 日期 | 前端格式为 `yyyy-MM-dd HH:mm:ss`，按 `app.retrieval.time-zone` 转换，默认 `Asia/Shanghai` |

`attribute`、`sort_by` 和响应对象键都使用逻辑字段 `name`，物理 `column_name` 只在后端查询层使用。不要把 `label`、旧 `display_name` 或物理列名作为接口字段。

## 5. 普通检索与高级表达式

### 5.1 普通检索

普通检索由前端根据字段元数据提供操作符。内置操作符如下：

| 操作符 | 含义 |
| :--- | :--- |
| `equal` / `notequal` | 等于 / 不等于 |
| `match` | 模糊匹配 |
| `greatthan` / `greatequalthan` | 大于 / 大于等于 |
| `lessthan` / `lessequalthan` | 小于 / 小于等于 |
| `between` | 两值范围 |
| `in` | 值集合 |
| `isnull` / `isnotnull` | 空 / 非空，不需要值 |

字符串、数值、日期和数组字段会补全各自适用的默认操作符。字段显式配置的未知操作符会使整次元数据加载失败。

### 5.2 高级表达式

高级模式接受 where 表达式安全子集，例如：

```sql
`ip` = '10.0.0.1'
attack_state >= 2 and attack_type_name like '%注入%'
module_type_name = '网站攻击' and (attack_type_name = '信息泄露' or attack_type_name = 'SQL注入')
```

支持 `= != <> > < >= <= like between in is null is not null`，字段可使用反引号，字符串可使用普通引号或中文智能引号。解析器保留括号优先级，并把符号操作符规范化为内部逻辑操作符。

安全限制：

- 表达式最长 8 KiB。
- 最多 50 个条件、10 层括号。
- 单个 `IN` 最多 200 个值，单值最长 2 KiB。
- 禁止函数、子查询、注释、分号拼接和 `1=1` 等无字段条件。
- 字段必须属于当前实体；自由 SQL 入口已禁用。

`WhereExpressionParser` 每次解析都创建独立 `ParserState`。不要把 token 游标或条件计数重新放回 Spring 单例字段，否则并发请求会再次出现随机“操作符不正确”。

## 6. 过滤器存储、兼容与权限

### 6.1 V2 存储结构

新建和更新后的 `rule_string` 使用 `schema_version: 2`，只保存逻辑配置：

```json
{
  "schema_version": 2,
  "type": "normal",
  "entity": "asset",
  "criteria_list": [],
  "criteria_logic": "and",
  "sql": null,
  "display_list": [
    { "entity": "asset", "attribute_list": ["ip"] }
  ],
  "page": 1,
  "size": 10,
  "sort_by": "ip",
  "order": "asc"
}
```

V2 不保存完整实体、字段和操作符对象，加载时始终绑定当前元数据，避免旧对象覆盖新配置。

### 6.2 兼容策略

- 继续读取历史 compact/expanded `RetrievalRule` JSON。
- 旧规则读取时不立即修改数据库。
- 旧规则下次成功更新后自动写成 V2。
- 含 `RetrievalSql` 的旧自由 SQL 规则只允许查看、编辑和删除，不允许执行。
- 不做破坏性批量迁移；发布前应只读统计存量格式和失效项。

### 6.3 归属隔离

列表、详情、旧规则读取、更新、删除和按规则执行都使用 `create_by = 当前用户`。规则不存在和越权访问统一返回“检索规则不可用”，避免通过 ID 枚举其他用户规则。

更新直接修改数据库中原实体，仅更新 `update_by` 和配置，不重建记录，因此 `create_by/create_time` 会保留。MCP 规则写操作从调用上下文解析当前用户并执行相同校验。

## 7. 元数据约定

元数据默认从 `app.paths.retrieval.metadata` 指定目录读取。每个 JSON 文件可提供 `entity`、`attribute`、`operator` 数组；加载时先合并全部文件、完成校验，再一次性替换内存快照。

简化示例：

```json
{
  "entity": [
    {
      "id": 1,
      "name": "asset",
      "label": "资产",
      "table_name": "asset_table",
      "sort_column": "create_time"
    }
  ],
  "attribute": [
    {
      "id": 101,
      "entity": "asset",
      "name": "device_name",
      "label": "设备名称",
      "column_name": "dev_name",
      "column_type": "String",
      "display_selected": true,
      "auto_complete": true,
      "copyable": true,
      "link_template": "/asset/detail?name={device_name}",
      "operators": ["equal", "notequal", "match", "in"]
    }
  ],
  "operator": []
}
```

加载时检查：

- 重复实体名、实体 ID、同实体字段名和字段 ID。
- 字段引用不存在的实体。
- 非法逻辑名、表名或列名。
- 实体默认排序列不存在。
- 字段引用未知操作符。
- 出现已废弃的跳转链接字段，或 `link_template` 不是字符串。
- `link_template` 占位符格式错误、引用当前实体不存在的逻辑属性，或目标不是相对地址和 `http/https` 地址。

如果新配置读取或校验失败，服务记录具体文件和原因，并继续使用上一份有效快照。查询期间只读取不可变快照，不会看到加载到一半的状态。

`link_template` 是可选字符串 URL 模板，例如 `/asset/detail?ip={ip}`。占位符只接受当前实体的逻辑属性 `name`，不接受布尔值、数字、数组或对象。规则仍只保存实体名和字段名，详情加载时从最新元数据绑定模板。

前端只从 `/retrieval/do` 返回的完整行数据解析占位符，不额外查询隐藏字段。字符串、数值和布尔值按字符串编码，数组和对象先转为 JSON，再对每个值执行 URL 编码。模板引用字段未展示、值为空或解析出的 URL 不安全时，单元格按普通文本显示；相对地址和 `http/https` 地址在新标签页打开。

`candidate/list` 支持两种定位方式：优先使用 `entity + attribute`，也兼容稳定且非零的 `attributeId`。前端输入自动补全走实体通用接口 `/api/v1/entity/{entity}/{attribute}/auto-complete`，只有元数据 `auto_complete: true` 时启用。

`copyable` 是可选布尔值，用于标识字段值是否支持在页面复制；未配置时默认为 `false`。该标记会透传到条件字段、展示字段和规则详情接口。

## 8. 前端状态编排原则

前端入口是 `zenvis-frontend/src/views/retrieval/index.vue`，它是查询配置的唯一状态源：

- `filter.vue` 只编辑条件并发送事件，不自行加载规则或执行查询。
- `table.vue` 只管理展示列交互、排序和分页事件。
- `table.vue` 通过统一模板解析函数生成字段链接，不硬编码目标页面地址；只有当前行的全部占位字段均可用且 URL 安全时才展示链接样式。
- 加载规则使用递增的 `loadGeneration`；过期详情响应不能覆盖新选择。
- 数据查询使用递增的 `dataRequestId` 和 `AbortController`；取消请求不弹错误，只有最新请求可以写表格或结束 loading。
- 切换实体或规则时立即清空旧条件、展示列、排序、分页和表格数据。
- 本地只缓存 `__rule__`。启动时缓存规则不存在、无权访问或失效，会清除缓存并进入新建状态。
- 条件输入框的本地草稿不能在每次 `v-model` 回写时整体重建，否则 Element Plus 输入框会卸载并表现为文字消失或焦点丢失。`filter.vue` 的 `modelMatchesDraft` 用于阻断这类自反馈。

加载一个有效过滤器的正常网络序列应当只有：

```text
GET /api/v1/retrieval/rule/detail?id=...
POST /api/v1/retrieval/do
```

如果同时出现旧的 `entity/list?rule_id`、`attribute/list?rule_id` 和 `display/attribute/list?rule_id`，说明前端又回到了并行水合规则的旧实现。

## 9. 日志、安全与排障

检索完成的 INFO 日志只记录：`context_id`、实体、条件数、字段数和耗时。生成后的查询语句只在 DEBUG 记录；请求日志切面会对 retrieval 请求中的 SQL、值和 token 脱敏。

常见问题：

| 现象 | 优先检查 |
| :--- | :--- |
| 偶发“高级 where 表达式操作符不正确” | 是否引入了共享 parser 游标；运行并发解析测试 |
| 保存后提示展示字段不存在 | `/rule/detail` 的 `issues`、字段逻辑 `name` 是否改名、是否误传物理列名 |
| 输入框输入后文字立即消失 | `filter.vue` 是否在父状态回写时重建 `criteria_list` 或改变行 `key` |
| 快速切换过滤器后显示旧数据 | generation/request ID 是否仍生效，取消请求是否被当作普通错误处理 |
| 过滤器列表看不到某条规则 | 先核对数据库 `create_by`；规则已按当前用户隔离 |
| 候选值按 ID 查不到 | 元数据 `id` 必须非零且唯一；也可改用 `entity + attribute` |
| 元数据修改未生效 | 查看 reload 错误；失败时系统会继续使用上一份快照 |

## 10. 开发与回归

后端重点测试：

```bash
mvn -Dtest='WhereExpressionParserTest,RetrievalRuleLifecycleTest,RetrievalRuleServiceImplTest,MetaDataServiceImplTest,QueryEngineImplTest,RetrievalControllerTest,LogAopAspectTest' test
```

前端检查：

```bash
cd ../zenvis-frontend
yarn test
yarn build:pro
```

浏览器回归至少覆盖：

1. 新建普通/高级检索，保存后再次加载。
2. 加载规则只有一次详情请求，有效规则只有一次数据请求。
3. 快速切换不同实体、普通和高级规则，最终只展示最后一次选择。
4. 分页、排序、展示列增减、自动补全、日期、数组、空值。
5. 更新时清空普通条件、切换检索类型和切换实体。
6. 删除当前规则和刷新恢复。
7. 两个用户只能看到和操作各自规则。
8. 元数据删除字段后，规则可编辑和删除但不会自动执行。

## 11. 发布和监控

推荐先发布兼容后端，再发布使用 `/rule/detail` 的前端。旧前端接口继续保留，不应反向先发布新前端。

发布前：

- 只读扫描存量 `rule_string` 格式、创建者和失效原因，不自动改库。
- 校验新元数据可以完整加载。
- 完成后端测试、前端类型检查和生产构建。

发布后重点观察：

- 高级表达式和展示字段错误数量。
- `invalid` 规则数量及主要 issue code。
- 规则越权拒绝。
- 检索耗时和分页大小分布。
- 每次规则切换触发的 `/retrieval/do` 请求数。
