# ZenVis 资产管理插件

本仓库独立维护资产管理插件及其动态 API 源码。

| 目录 | 构件 | 版本 | 说明 |
| --- | --- | --- | --- |
| `plugin-asset` | `com.coolxer.plugin.asset` | `1.3.1` | 十类资产的接入、检索、规则、详情、IP 调查与治理看板 |
| `extend-asset` | `com.coolxer.plugin:extend-asset` | `1.3.1` | 资产规则动态 API 薄 JAR 工程 |

## 环境与构建

需要 JDK 17、Maven 3.x 和 tar；Windows 使用 PowerShell 及 tar。

```bash
mvn -f extend-asset/pom.xml test
./build.sh
# 兼容显式插件参数
./build.sh plugin-asset
```

Windows：

```powershell
.\build.ps1
.\build.ps1 plugin-asset
```

构建会先执行 Maven 测试和打包，将唯一薄 JAR 写入 `plugin-asset/03_api`，随后生成
`com-coolxer-plugin-asset.tar.gz`。`target/`、运行 JAR、日志和归档均为可再生成物，不提交到 Git。

插件能力、接口、环境变量和升级约束见 [plugin-asset/README.md](plugin-asset/README.md)。

## 发布检查

- JAR 必须包含 `com.coolxer.plugin` 业务类且不能包含 `BOOT-INF`。
- 归档根目录必须直接包含 `index.json`，不得包含源码、`.git`、`target` 或旧归档。
- 安装、升级和卸载前确认资产表及 MySQL 迁移的数据影响。
