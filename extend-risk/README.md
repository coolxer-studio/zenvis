# extend-risk

风险监控插件的独立动态 API 薄 JAR 工程，仅保留旧风险时间线接口用于兼容。

```bash
mvn clean test
```

业务类位于 `com.coolxer.plugin.risk`，平台依赖均使用 `provided`，工程不包含 Spring Boot 启动类。运行时接口前缀为 `/api/v1/plugin/com.coolxer.plugin.risk`。
