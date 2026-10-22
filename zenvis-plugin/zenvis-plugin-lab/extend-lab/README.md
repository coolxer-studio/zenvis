# extend-lab

`插件能力实验室`的动态 API 源码工程。发布时只把薄 JAR 放入
`plugin-lab/03_api/`，不携带 Spring Boot 服务端或 `BOOT-INF`。

## 能力

- `LabNoteController`：笔记分页查询、详情、创建、更新、删除和状态汇总；
- `LabCapabilityController`：返回插件能力目录；
- `LabMcpController`：提供只读 MCP SSE 工具 `lab_capability_catalog` 与
  `lab_note_search`；
- `LabNoteRepository`：使用平台提供的 `pluginMysqlJdbcTemplate`；
- `LabNoteService`：校验分页、状态、文本长度和 LIKE 通配符，写操作使用
  `pluginMysqlTransactionManager`。

## 开发

```bash
mvn -f extend-lab/pom.xml test
```

插件包名决定最终路由前缀：

```text
/api/v1/plugin/com.coolxer.plugin.lab
```

Controller 只声明插件内相对路径。MySQL 表结构由
`plugin-lab/03_api/migrations/mysql/V001__create_lab_note.sql` 初始化。

## 边界

笔记是演示动态 API 与 MySQL 迁移的合成数据，不是平台正式业务数据。MCP 工具
只有只读查询，不提供写入、删除或处置动作。
