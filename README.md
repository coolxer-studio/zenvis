# ZenVis 运营分析插件

本仓库独立维护运营分析插件及兼容动态 API。

| 目录 | 构件 | 版本 | 说明 |
| --- | --- | --- | --- |
| `plugin-operation` | `com.coolxer.plugin.operation` | `1.2.1` | 十类用户行为与应用质量数据的检索、详情、IP 调查和态势看板 |
| `extend-operation` | `com.coolxer.plugin:extend-operation` | `1.2.1` | 历史运营看板配置接口的动态 API 薄 JAR |

需要 JDK 17、Maven 3.x 和 tar。执行：

```bash
mvn -f extend-operation/pom.xml test
./build.sh
```

Windows 使用 `.\build.ps1`。也可显式传入 `plugin-operation`。产物为
`com-coolxer-plugin-operation.tar.gz`；`target/`、`03_api/*.jar`、日志和归档不提交。

详细的数据契约、升级限制与接口说明见 [plugin-operation/README.md](plugin-operation/README.md)。
