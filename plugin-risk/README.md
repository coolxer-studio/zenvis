# 风险监控插件

`com.coolxer.plugin.risk` 1.2.1 统一管理风险汇总、数据风险、漏洞、基线、弱口令和攻击六类安全数据，提供真实数据概览、检索、独立详情页、IP 命中调查、HTML 态势看板和测试用模拟数据服务。

## 能力

- 六类 ClickHouse 实体，统一使用 `risk_level`、网络信息和更新时间检索。
- Meta 使用 11 个标准操作符，补充风险等级、验证结果、验证方法和漏洞状态候选映射。
- 所有列表使用 `zenvis_id` 作为平台主键，按 `update_time` 倒序；弱口令 `password` 保持隐藏，不允许复制或自动完成。
- 概览页和 `安全风险态势` HTML 看板使用 Entity Analytics 真实接口；旧随机时间线和静态 Sankey 不再作为页面数据源。
- `push_risk.yaml` 包含 18 条样例，覆盖六类实体，可在推送任务页面停用。

## 模拟数据任务

任务用于开发和验收，直接写入 ClickHouse。环境变量包括 `RISK_DEMO_INTERVAL_SECONDS`、`CLICKHOUSE_ENDPOINT`、`CLICKHOUSE_DATABASE`、`CLICKHOUSE_USER` 和 `CLICKHOUSE_PASSWORD`。配置严格校验 JSON 与 `risk_type`，补充 UUID 和毫秒时间，且不忽略未知字段。

样例中的弱口令只用于验证风险检测数据结构；UI 和 Meta 不展示、不复制、不补全该字段。生产环境应使用脱敏策略和受控权限。

## 动态 API

运行时前缀为 `/api/v1/plugin/com.coolxer.plugin.risk`。原时间线 API 为兼容旧调用保留在 Jar 中，但新 UI 与看板不再调用随机数据接口。

## 安装与升级

在当前仓库根目录运行：

```bash
./build.sh
```

上传 `com-coolxer-plugin-risk.tar.gz`。1.2.1 使用全局实体 ID `6201–6206` 和字段 ID `620001–620100`，避免与资产及运营插件冲突；表名、字段类型和 MergeTree 物理定义保持不变。若 1.2.0 已成功安装，不能直接执行普通增量升级，应先完成平台级 ID 迁移；安装失败或从未成功安装的环境可直接安装 1.2.1。

详细契约与运维说明见 [`00_doc/契约矩阵与运维.md`](00_doc/契约矩阵与运维.md)。
