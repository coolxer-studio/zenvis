# extend-asset

资产管理插件的独立动态 API 薄 JAR 工程，版本与 `plugin-asset/index.json` 保持一致。

```bash
mvn clean test
```

业务类位于 `com.coolxer.plugin.asset`，由 ZenVis 动态类加载器扫描。Spring Web、Context、JDBC、Transaction、Jackson 和 Validation 均由平台提供；工程不包含 Spring Boot 启动类。运行时接口前缀为 `/api/v1/plugin/com.coolxer.plugin.asset`。
