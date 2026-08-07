# 运营分析插件

`com.coolxer.plugin.operation` 1.2.1 统一管理十类用户行为、性能与应用质量事件，提供真实数据概览、实体检索、独立详情页、IP 命中调查、HTML 态势看板和测试用模拟数据服务。

## 能力

- 十类 ClickHouse 实体：启动、点击、页面、函数调用、网络、扩展、性能、位置、崩溃和 ANR 事件。
- Meta 使用 11 个标准操作符，按字段类型声明检索能力；补充候选映射、复制、自动完成、详情和 IP 链接。
- 所有列表使用 `zenvis_id` 作为平台主键，按 `event_time` 倒序，详情通过实体 `/view` 接口精确查询。
- 概览页和 `运营行为态势` HTML 看板调用 Entity Analytics 真实接口，不再把静态图表数据当成业务数据。
- IP 命中调查覆盖各实体的 `lan_ip`、`wan_ip`，网络事件同时覆盖 `source_ip`、`target_ip`。
- `push_operation.yaml` 默认每 5 秒随机产生一条样例，可在推送任务页面停用。

## 模拟数据任务

任务用于开发和验收，直接写入 ClickHouse，不应代替生产采集链路。支持以下环境变量：

| 变量 | 默认值 |
| --- | --- |
| `OPERATION_DEMO_INTERVAL_SECONDS` | `5` |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` |
| `CLICKHOUSE_DATABASE` | `zenvis` |
| `CLICKHOUSE_USER` | `default` |
| `CLICKHOUSE_PASSWORD` | 平台当前默认密码 |

配置会严格解析 JSON、校验 `operation_type`、补充 UUID 与毫秒时间、移除路由字段，并以 `skip_unknown_fields: false` 写入对应表。错误数据会进入控制台错误分支。

## 动态 API

运行时前缀为 `/api/v1/plugin/com.coolxer.plugin.operation`。保留已有看板配置接口以兼容升级；Repository 使用普通组件注册，写操作通过 `TransactionTemplate` 使用 `pluginMysqlTransactionManager`，避免动态插件类加载器触发 CGLIB 子类生成失败。

## 安装与升级

在当前仓库根目录运行：

```bash
./build.sh
```

上传 `com-coolxer-plugin-operation.tar.gz`。1.2.1 使用全局实体 ID `6101–6110` 和字段 ID `610001–610203`，修复与资产插件旧 ID `1–10` 的冲突；表名、字段类型和 MergeTree 物理定义保持不变。若 1.2.0 已成功安装，不能直接执行普通增量升级，应先完成平台级 ID 迁移；安装失败或从未成功安装的环境可直接安装 1.2.1。

详细契约与运维说明见 [`00_doc/契约矩阵与运维.md`](00_doc/契约矩阵与运维.md)。
