# 资产管理插件

## 基本信息

- 插件名称：资产管理
- 包名：`com.coolxer.plugin.asset`
- 版本：`1.3.1`
- 作者：CoolXer

插件统一管理主机、PC、IoT、移动端、探针、应用、服务、API、日志和文件十类资产，提供检索、通用 CRUD、规则配置、详情页、IP 命中调查与资产治理看板。

## 数据契约

| 实体 | 说明 | ClickHouse 表 | 应用列表 |
| --- | --- | --- | --- |
| `asset_host` | 主机资产 | `asset_host` | 服务器设备管理 |
| `asset_pc` | PC 终端资产 | `asset_pc` | PC 端设备管理 |
| `asset_iot` | IoT 设备资产 | `asset_iot` | IoT 设备管理 |
| `asset_mobile` | 移动端资产 | `asset_mobile` | 移动端设备管理 |
| `asset_probe` | 数据探针 SDK 资产 | `asset_probe` | 探针管理 |
| `asset_app` | 应用资产 | `asset_app` | 应用管理 |
| `asset_service` | 系统服务资产 | `asset_service` | 服务管理 |
| `asset_api` | RESTful API 资产 | `asset_api` | API 管理 |
| `asset_log` | 日志资产 | `asset_log` | 日志资产列表 |
| `asset_file` | 文件资产 | `asset_file` | 文件资产列表 |

每个实体由平台自动补充 `zenvis_id` 和 `zenvis_insert_time`。业务字段 `id` 是来源系统资产标识；详情、修改、删除和批量操作使用平台 `zenvis_id`。

Meta 为枚举字段提供候选映射，为可复用标识、IP、路径、URL 和哈希启用复制，为适合的文本字段启用自动补全。每个实体的业务 `id` 都可跳转到独立详情页；主机、PC、IoT 和移动端的内外网 IP 可跳转到跨实体 IP 调查页。

## 数据接入

插件安装后会创建并启动“资产模拟数据生成与入库”测试任务。任务使用
`02_push-task/push_asset.yaml`，默认每 5 秒从 21 条样例中随机生成一条记录，
补充业务 `id`、`update_time` 和 `insert_time`，按资产类型写入十张 ClickHouse 表。

可通过以下环境变量调整运行参数：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ASSET_DEMO_INTERVAL_SECONDS` | `5` | 模拟记录生成间隔（秒） |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse HTTP 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | 目标数据库 |
| `CLICKHOUSE_USER` | `default` | 数据库用户 |
| `CLICKHOUSE_PASSWORD` | 平台默认密码 | 数据库密码，部署值优先 |

该任务只用于开发、演示和页面验收，会持续增加资产记录；不需要模拟数据时，应在
“数据推送服务”页面停用任务。静态样例解析失败会输出到任务日志，不依赖 Kafka。

接入真实资产源时，应依据来源协议另行提供两段式 Vector YAML：

```text
真实来源 → 原始记录 → Kafka
Kafka → 校验/转换 → 对应 ClickHouse 表
                  └→ com-coolxer-plugin-asset-dead-letter
```

来源协议、字段顺序、必填规则和交付语义尚未给定，因此模拟任务不能替代生产接入。
真实资产源仍应使用上述两段式架构，并配置独立 DLQ。

## 动态 API

运行时前缀：`/api/v1/plugin/com.coolxer.plugin.asset`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/rule/add` | 新增资产规则 |
| DELETE | `/rule/{id}` | 删除规则 |
| DELETE | `/rule/bulk/{ids}` | 批量删除规则 |
| POST | `/rule/{id}/update` | 更新规则 |
| POST | `/rule/{ids}/bulk_update` | 批量更新规则 |
| GET | `/rule/list` | 分页查询规则 |
| GET | `/rule/{id}/view` | 查询规则详情 |
| GET | `/rule/action/list` | 规则动作选项 |
| GET | `/rule/asset/list` | 资产类别选项 |
| GET | `/rule/status/list` | 规则状态选项 |

规则写操作使用 `pluginMysqlTransactionManager` 的编程式事务，避免动态类加载环境触发不可见的 CGLIB 子类。MySQL 表由 `03_api/migrations/mysql/V001__create_asset_rule.sql` 初始化；已执行迁移文件不得修改。

## UI 与看板

- 低代码应用：`com.coolxer.plugin.asset`
- 独立详情页：`com.coolxer.plugin.asset.detail-<host|pc|iot|mobile|probe|app|service|api|log|file>`
- 通用治理属性维护：应用内路由 `/asset/:entity/:id/edit`
- IP 命中调查：`com.coolxer.plugin.asset.ip-statistics`
- HTML 看板：`com.coolxer.plugin.asset.dashboard.asset-governance-overview`

看板只调用 Zenvis 实体分析 API，展示十类资产总量、今日新增、在线资产、高风险资产、类型规模、近 7 日趋势、风险分布和生命周期状态；包含加载、空数据、错误、手动刷新和 60 秒自动刷新状态。

## 升级兼容

`1.3.1` 保留原实体 ID、表名、字段 ID、列名、列类型、引擎、排序键和分区键，可从 `1.2.3` 或 `1.3.0` 走平台增量升级。历史表使用 `ReplacingMergeTree(update_time)`、`ORDER BY id` 和 `PARTITION BY id`；平台禁止升级包修改这些物理属性，因此本版本没有就地改变已有表结构。

如需迁移到 `toYYYYMM(zenvis_insert_time)` 分区，应制定数据迁移、双写/停机和回滚方案，不能通过普通插件升级直接修改。

## 构建与验证

```bash
mvn -f extend-asset/pom.xml test
./build.sh
```

安装包为仓库根目录下的 `com-coolxer-plugin-asset.tar.gz`。构建后应检查 JSON、YAML、Meta、页面引用、API 薄 JAR、版本和归档内容；没有目标 ClickHouse/Zenvis 环境时，不应宣称完成实时写入验证。
