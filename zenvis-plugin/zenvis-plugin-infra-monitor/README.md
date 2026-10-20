# zenvis-plugin-infra-monitor 构建仓库

本目录是“基础设施端点监测与指标归档”插件的源码与构建入口，插件交付目录为 `plugin-infra-monitor/`。插件包含 Prometheus → Kafka → ClickHouse 采集归档任务、基于 ClickHouse 最新批次的聚合 API、低代码应用与 HTML 大屏。

## 目录

| 路径 | 说明 |
| --- | --- |
| `plugin-infra-monitor/` | 可直接安装的插件包内容 |
| `extend-infra-monitor/` | 动态 API 的 Maven 工程 |
| `build.conf` | 插件目录和 Maven 模块声明 |
| `build.sh` | 测试、构建、JAR 校验和归档脚本 |

插件交付目录始终保留 `00_doc` 到 `09_skill` 的完整骨架。未启用的能力使用空目录占位：`07_mcp/config.json` 为空数组，`08_rag/.gitkeep`、`09_skill/.gitkeep` 保持目录存在；`08_rag` 没有 `config.json` 表示不托管插件 RAG 资源。这些占位仅用于源码组织，平台安装不应因缺少建议目录而拒绝插件。

## 构建

```bash
./build.sh
```

产物：

- `plugin-infra-monitor/03_api/extend-infra-monitor-1.0.0.jar`
- `com-coolxer-plugin-infra-monitor.tar.gz`

脚本会检查 `index.json` 与 `pom.xml` 版本一致，执行 `mvn clean package`，拒绝包含 `BOOT-INF` 的胖 JAR，并检查归档根目录存在 `index.json`。

## 单独测试

```bash
cd extend-infra-monitor
mvn test
```

测试覆盖 ClickHouse 最新批次聚合、数据新鲜度判定、Kafka 指标汇总、ClickHouse 故障降级和 snake_case JSON 契约。

插件功能、API 契约和部署说明见 `plugin-infra-monitor/README.md`。
