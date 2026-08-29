# ZenVis 风险监控插件

本仓库独立维护风险监控插件及兼容动态 API。

| 目录 | 构件 | 版本 | 说明 |
| --- | --- | --- | --- |
| `plugin-risk` | `com.coolxer.plugin.risk` | `1.2.1` | 六类安全风险数据的检索、详情、IP 调查与态势看板 |
| `extend-risk` | `com.coolxer.plugin:extend-risk` | `1.2.1` | 历史风险时间线接口的动态 API 薄 JAR |

需要 JDK 17、Maven 3.x 和 tar。执行：

```bash
mvn -f extend-risk/pom.xml test
./build.sh
```

Windows 使用 `.\build.ps1`。也可显式传入 `plugin-risk`。产物为
`com-coolxer-plugin-risk.tar.gz`；`target/`、`03_api/*.jar`、日志和归档不提交。

详细的数据契约、敏感字段策略和升级限制见 [plugin-risk/README.md](plugin-risk/README.md)。
