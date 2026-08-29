# extend-user-event

用户事件示例插件的独立动态 API 薄 JAR 工程。它不是可单独启动的 Spring Boot 应用，由 ZenVis 在插件安装时加载。

## 构建

```bash
mvn clean test
```

仓库根构建脚本会执行测试与打包，并把 `target/extend-user-event-1.0.5.jar` 复制到插件的 `03_api`。

## 运行时接口

ZenVis 自动添加 `/api/v1/plugin/{package_name}` 前缀：

- `POST /api/v1/plugin/com.coolxer.plugin.user.event/user/add`
- `GET /api/v1/plugin/com.coolxer.plugin.user.event/user/list?page=1&perPage=5`

响应字段为 `status`、`msg`、`data`，分页数据包含 `rows` 和 `total`。Spring Web 和 Context 由平台提供，发布 JAR 不包含 `BOOT-INF`。
