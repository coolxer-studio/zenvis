# REST 接口与 MySQL

插件 API 前缀是 `/api/v1/plugin/com.coolxer.plugin.lab`。笔记接口包括：

- `GET /notes?page=&perPage=&keyword=&status=`；
- `GET /notes/{id}`；
- `POST /notes`；
- `POST /notes/{id}/update`；
- `DELETE /notes/{id}`；
- `GET /notes/stats`；
- `GET /capabilities`。

查询参数使用 `page` 与 `perPage`，JSON 使用 snake_case。创建/更新请求字段为 `title`、`content`、`status`、`owner`；响应还包括 `id`、`create_time`、`update_time`。统计响应为 `total` 与 `by_status`。

服务端将 `perPage` 限制为 1–100，`status` 只允许 `draft`、`published`、`archived`，标题最多 120 字符、正文最多 4000 字符、负责人最多 80 字符、关键字最多 100 字符。LIKE 查询转义 `!`、`%`、`_` 并使用 `ESCAPE '!'`；参数通过 JDBC 具名参数绑定，不拼接 SQL。

MySQL 表为 `t_lab_note`，迁移 `V001__create_lab_note.sql` 使用幂等建表与幂等示例插入。查询使用 `pluginMysqlJdbcTemplate`；创建、更新、删除使用 `pluginMysqlTransactionManager` 的 `TransactionTemplate`。

MCP 只复用查询能力。任何创建、更新或删除都必须由用户通过 REST 或低代码页面完成，导览 Skill 不得建议执行 SQL 代替接口。

