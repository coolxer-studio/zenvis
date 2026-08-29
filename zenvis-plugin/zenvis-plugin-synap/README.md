# ZenVis Synap 探针数据采集插件

本仓库独立维护 Synap 探针 Kafka 数据采集插件。

| 目录 | 包名 | 版本 | 说明 |
| --- | --- | --- | --- |
| `plugin-synap` | `com.coolxer.plugin.synap` | `1.2.0` | Android、H5、iOS、Host、WeChat 与 Server 事实统一入库、严格校验、DLQ、详情和参数聚合分析 |

该插件不包含动态 API JAR。构建仅需 Bash 与 tar，Windows 使用 PowerShell 与 tar：

```bash
./build.sh
# 或 ./build.sh plugin-synap
```

Windows 执行 `.\build.ps1`。产物为 `com-coolxer-plugin-synap.tar.gz`。
详细的 Kafka Topic、环境变量、DLQ 重放和升级说明见 [plugin-synap/README.md](plugin-synap/README.md)。

归档、日志、系统文件和任何 `target/` 均不提交到 Git。
