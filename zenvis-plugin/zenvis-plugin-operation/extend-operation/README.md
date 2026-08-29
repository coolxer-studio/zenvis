# extend-operation

运营分析插件的独立动态 API 薄 JAR 工程，保留历史运营看板配置接口以兼容既有调用。

```bash
mvn clean test
```

业务类位于 `com.coolxer.plugin.operation`，平台依赖均使用 `provided`，工程不包含 Spring Boot 启动类。运行时接口前缀为 `/api/v1/plugin/com.coolxer.plugin.operation`。
