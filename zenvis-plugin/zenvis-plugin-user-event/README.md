# ZenVis 用户事件示例插件

本仓库独立维护用户事件测试插件及其动态 API 源码。

| 目录 | 构件 | 版本 | 说明 |
| --- | --- | --- | --- |
| `plugin-user-event` | `com.coolxer.plugin.user.event` | `1.0.5` | 用户事件接入、检索、页面、看板和菜单示例 |
| `extend-user-event` | `com.coolxer.plugin:extend-user-event` | `1.0.5` | 用户增查示例动态 API 薄 JAR |

需要 JDK 17、Maven 3.x 和 tar。执行：

```bash
mvn -f extend-user-event/pom.xml test
./build.sh
```

Windows 使用 `.\build.ps1`。也可显式传入 `plugin-user-event`。产物为
`com-coolxer-plugin-user-event.tar.gz`；生成 JAR、`target/`、日志和归档不提交。

动态 API 由 ZenVis 自动增加插件前缀：

- `POST /api/v1/plugin/com.coolxer.plugin.user.event/user/add`
- `GET /api/v1/plugin/com.coolxer.plugin.user.event/user/list`

插件说明见 [plugin-user-event/README.md](plugin-user-event/README.md)，源码说明见
[extend-user-event/README.md](extend-user-event/README.md)。
