# 探针数据采集

> 把 Android、H5、iOS、Host、WeChat 和 Server 六类探针事实统一采集到一张表，供平台检索、详情查看和参数聚合分析。

## 插件概览

| 项目 | 内容 |
| --- | --- |
| 插件名称 | 探针数据采集 |
| 包名 | `com.coolxer.plugin.synap` |
| 当前版本 | 1.0.0 |
| 插件形态 | 探针消息接入型插件（Kafka → ClickHouse） |
| 数据实体 | 1 个：探针消息 |
| 主要入口 | 实体详情页、参数聚合分析页（不提供插件菜单） |
| 安装包 | [com-coolxer-plugin-synap-1.0.0.tar.gz](https://gitee.com/coolxer-studio/zenvis/releases/download/zenvis-plugin-1.0.0/com-coolxer-plugin-synap-1.0.0.tar.gz) |

## 用途与价值

不同平台的探针各自使用不同的 Kafka 主题和字段结构，直接消费会导致模型重复、口径不一致。本插件用一个消费组和一个推送任务消费六类平台事实主题，把每个 Kafka Checkpoint 转换为 63 个公共字段，统一写入 `synap_agent_message`：

- **统一模型**：六端消息共用一套公共字段，便于跨端检索和统计。
- **保留现场**：Kafka 主题、分区、偏移量、记录时间和完整原始消息都会保留。
- **严格校验**：Server 的 12 个 `server_fact_*` 主题额外校验平台、Topic/Fact 对应关系、追踪字段和 `schemaVersion`。
- **异常隔离**：确定性校验失败的消息进入插件专属死信主题，不静默丢弃。

**适用对象**

- 移动端、前端和后端研发：排查探针上报内容和字段结构。
- 数据平台人员：维护统一的探针消息模型和采集链路。
- 安全分析人员：按设备 ID、IP 或启动 ID 聚合分析探针行为。

## 核心能力

- 一个 Kafka 消费组同时消费 Android、H5、iOS、Host、WeChat 和 Server 事实主题。
- 对 JSON 信封、公共必填字段、时间、整数、经纬度、标签、处置和风险结构执行确定性校验。
- 对 Server 的 12 个主题额外校验 Topic 与 Fact 的对应关系、追踪字段、`schemaVersion=1` 和业务字段类型。
- 每个平台每个 Checkpoint 写一行；Server 的批量数组和漏洞富化结果完整保存在 `fact` JSON 中，不拆行、不派生额外物理列。
- 正常消息写入单一 ClickHouse 表，失败消息进入 `com.coolxer.plugin.synap.dead-letter`。
- 保留 `source_topic`、`source_partition`、`source_offset` 等溯源字段。
- 提供单条消息详情页和按设备 ID、IP、启动 ID 检索的参数聚合分析页。

## 数据与页面

**数据实体**

| 实体 | 说明 | 存储 |
| --- | --- | --- |
| `synap_agent_message` | 探针消息 | `zenvis.synap_agent_message` |

实体按 `source_topic / source_partition / source_offset / client_time` 排序，由平台自动维护 `zenvis_id` 和 `zenvis_insert_time`。

**页面**

| 页面 | 说明 |
| --- | --- |
| 探针消息详情 | 按 `zenvis_id` 查看单条消息完整属性 |
| 参数聚合分析 | 按设备 ID、IP 或启动 ID 分析探针消息 |

本插件不提供插件菜单，也不提供数据看板；页面通过平台实体入口访问。

## 典型使用流程

1. 在[插件管理](插件管理.md)中安装插件。
2. 确认唯一的“探针 Kafka 消息入库”推送任务已创建并运行。
3. 确认上游主题符合插件的主题正则；如部署环境覆盖了主题配置，需要把 Server 的 12 个主题加入正则。
4. 通过实体列表或全局检索查看 `synap_agent_message` 记录，使用 `platform=server` 筛选 Server 数据。
5. 打开详情页查看完整消息，或进入参数聚合分析页按设备 ID、IP、启动 ID 分析。

## 注意事项

- 未匹配主题正则的 Kafka 主题不会进入 ClickHouse，也不会进入死信。
- Server 消息必须满足 Topic 与 Fact 类型一一对应、必填字段和时间格式等严格契约；缺失字段会进入死信。
- 修复死信数据后，只把原始 `message` 重放到原始主题，不要发送完整死信信封。
- Kafka 为至少一次投递，可使用 `source_topic + source_partition + source_offset` 识别原始 Checkpoint。
- 插件不安装消息管理应用、插件菜单或数据看板，也不包含 Server 专属表或数据迁移。

安装、升级和卸载操作见[插件管理](插件管理.md)。
