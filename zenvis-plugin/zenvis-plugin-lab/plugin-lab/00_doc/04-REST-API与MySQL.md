# REST API 与 MySQL

动态 API 位于 `extend-lab/`，是 Spring Boot 薄 JAR。插件路由由平台挂载在：

```text
/api/v1/plugin/com.coolxer.plugin.lab
```

## 笔记接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/notes` | 分页查询，参数固定为 `page`、`perPage`、`keyword`、`status` |
| `GET` | `/notes/{id}` | 查询单条笔记 |
| `POST` | `/notes` | 创建笔记 |
| `POST` | `/notes/{id}/update` | 更新笔记 |
| `DELETE` | `/notes/{id}` | 删除笔记 |
| `GET` | `/notes/stats` | 按状态汇总数量 |
| `GET` | `/capabilities` | 返回供页面和 MCP 共用的能力目录 |

查询参数使用 Spring MVC 原名 `page` 与 `perPage` 中的大写 `P`；JSON 请求体和响应字段使用 snake_case，例如 `create_time`、`update_time`、`by_status`、`demo_entry`、`read_only`。

`create_time`、`update_time` 固定输出为字符串 `yyyy-MM-dd'T'HH:mm:ss`，由 `LabNote` 上的专用 Jackson 序列化器生成，不依赖平台 `ObjectMapper` 是否注册 JSR-310 模块。

## 请求示例

创建：

```bash
curl -X POST 'http://localhost:8080/api/v1/plugin/com.coolxer.plugin.lab/notes' \
  -H 'Content-Type: application/json' \
  -d '{"title":"接入检查","content":"确认 Kafka 与 DLQ","status":"draft","owner":"demo"}'
```

分页：

```bash
curl 'http://localhost:8080/api/v1/plugin/com.coolxer.plugin.lab/notes?page=1&perPage=20&keyword=DLQ&status=published'
```

更新：

```bash
curl -X POST 'http://localhost:8080/api/v1/plugin/com.coolxer.plugin.lab/notes/1/update' \
  -H 'Content-Type: application/json' \
  -d '{"title":"接入检查（已复核）","content":"合法样例正常写入","status":"published","owner":"demo"}'
```

删除：

```bash
curl -X DELETE 'http://localhost:8080/api/v1/plugin/com.coolxer.plugin.lab/notes/1'
```

## 服务端校验

- `page` 最小为 1；`perPage` 限制在 `1–100`；
- `status` 只接受 `draft`、`published`、`archived`；
- `title` 去空格后必填且不超过 120 字符；
- `content` 不超过 4000 字符；
- `owner` 不超过 80 字符，空值落为 `demo`；
- `keyword` 去空格后不超过 100 字符；
- LIKE 查询转义 `!`、`%`、`_` 并使用 `ESCAPE '!'`，用户输入不会被当作通配符；
- 所有 SQL 使用具名参数或 `PreparedStatement`，不拼接用户值。

创建、更新和删除均通过 `pluginMysqlTransactionManager` 构造的 `TransactionTemplate` 执行；查询使用 `pluginMysqlJdbcTemplate`。

## MySQL 迁移

迁移文件：`03_api/migrations/mysql/V001__create_lab_note.sql`

表 `t_lab_note`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT` 自增 | 主键 |
| `title` | `VARCHAR(120)` | 标题 |
| `content` | `TEXT` | 正文 |
| `status` | `VARCHAR(24)` | 状态 |
| `owner` | `VARCHAR(80)` | 负责人 |
| `create_time` | `TIMESTAMP` | 创建时间 |
| `update_time` | `TIMESTAMP` | 更新时间 |

迁移使用 `CREATE TABLE IF NOT EXISTS`，并通过 `INSERT ... SELECT ... WHERE NOT EXISTS` 幂等插入三条示例笔记；重复安装或执行不会产生同标题重复数据。

## 页面约定

低代码页面 `04_ui/app/notes.json` 直接调用上述接口，包含分页、关键字/状态筛选、状态统计、新建、编辑和删除。MCP 的 `lab_note_search` 复用同一查询服务，但强制只读。

