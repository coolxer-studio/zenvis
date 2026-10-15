# 智能体安全监测插件

## 概述

本插件接入 **Sentinel 智能体安全检测网关**导出的安全事件（消息类型 `sentinel.security.event.v1`），提供原始留存、解析入库、检索、单事件详情复盘、安全态势总览，以及两个只读 AI 研判 Skill。

Sentinel 是安全检测模块，检测命中统一称「安全事件」。其 `sentinel-service` 已把检测命中库导出为 Kafka 消息（主题 `ai-agent-security-result`），本插件直接消费该主题并做结构化入库。

## 插件信息

| 项目 | 值 |
| --- | --- |
| 插件名称 | 智能体安全监测 |
| 包名 | `com.coolxer.plugin.sentinel` |
| 版本 | 1.0.0 |
| 作者 | CoolXer |
| 能力目录 | 00_doc / 01_meta / 02_push-task / 03_api(空) / 04_ui / 05_dashboard / 06_mcp(空) / 07_skill / 08_menu |

## 适用场景与边界

**适用**：接入 Sentinel 镜像档安全事件，做统计、检索、复盘与只读研判；识别提示注入、越狱、资源攻击、敏感数据、越界请求与数据外渗等命中。

**不适用 / 明确边界**：

- 本插件**不实际阻断或改写**请求。Sentinel 镜像档的 `action` 是策略建议动作，不代表请求已被拦截。
- 本插件**不包含网络 IP 字段**（事件来源为智能体网关会话），因此**不提供 IP 命中调查页**；跨事件关联改用 `session_id` / `msg_hash` / `subject` / `rule_ids`。
- 本插件**不做上游去重**。投递语义为 at-least-once，重复 `event_id` 会重复入库，对账需按 `event_id` 去重。
- 本插件不提供外部 MCP 连接与写操作；两个 Skill 均为只读检索。

## 数据映射

| 数据来源 | Entity | ClickHouse 表 | Kafka Topic | UI 页面 |
| --- | --- | --- | --- | --- |
| `ai-agent-security-result` | `sentinel_security_event` | `sentinel_security_event` | `ai-agent-security-result`（直接消费） | 事件列表 / 事件详情 / 态势总览 |

字段清单、枚举定义与投递语义详见 [00_doc/数据字典与接入说明.md](00_doc/数据字典与接入说明.md)。

## 推送任务架构

数据来源本身就是上游多消息汇聚平台的 Kafka 主题，因此**不做 Kafka→Kafka 转存**，直接用本插件专属消费组消费上游主题并入库。仅注册一个推送任务：

| 任务名 | 文件 | 说明 |
| --- | --- | --- |
| Sentinel安全事件解析入库 | `02_push-task/sentinel-security-event-clickhouse.yaml` | 上游 Kafka → 校验/转换 → ClickHouse，异常进 DLQ |

```
ai-agent-security-result（上游主题，原始报文留存于此）
        │ 本插件专属消费组，独立于上游导出器
        ▼
校验 type / event_id / occurred_at / payload
        ├── 合法 → ClickHouse: sentinel_security_event
        └── 异常 → sentinel-security-event-dead-letter
```

### 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SENTINEL_KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | 上游事件主题所在 Kafka 地址 |
| `SENTINEL_SOURCE_TOPIC` | `ai-agent-security-result` | 上游 Sentinel 事件主题（消费来源） |
| `SENTINEL_GROUP_ID` | `sentinel-security-event-clickhouse-v1` | 本插件消费组（独立于上游导出器） |
| `SENTINEL_DLQ_TOPIC` | `sentinel-security-event-dead-letter` | 死信主题 |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | 死信主题所在 Kafka 地址 |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | ClickHouse 数据库 |
| `CLICKHOUSE_USER` | `default` | ClickHouse 用户 |
| `CLICKHOUSE_PASSWORD` | 无默认值 | 由部署环境注入 |

- 时区统一为 `Asia/Shanghai`，`occurred_at` 入库为 `DateTime64(3)`。
- 编码统一为 UTF-8；信封为单条 JSON，不做压缩包装。
- 网络依赖：上游 Kafka、本插件 Kafka、ClickHouse。

## DLQ、重放与保留

- 契约异常（非法 JSON、非预期 `type`、缺 `event_id`、`occurred_at` 无法解析、缺 `payload`/`payload.verdict`）写入 `sentinel-security-event-dead-letter`，保留原始 `message` 与 `metadata.dropped.*`。
- 原始报文保留在上游主题中。重放时重置本插件消费组（`SENTINEL_GROUP_ID`）偏移即可（受上游保留期约束）；重置前确认上游版本与当前 Meta 匹配，并确认可接受重复 `event_id`。
- 保留：ClickHouse 表按 `zenvis_insert_time` 分区，行级 TTL **90 天**（TTL 变更可能导致历史数据永久删除，升级前请确认留存策略）。

## 安装与升级

1. 安装顺序由平台统一执行：解压 → Meta/建表 → 推送任务 → MySQL 迁移（本插件无）→ API（本插件无）→ UI → Dashboard → MCP → RAG → Skill → 菜单。
2. 在推送任务页面**手动启用**「Sentinel安全事件解析入库」。
3. 菜单安装为一级菜单，**不自动授权**，需在角色权限中分配。
4. 升级限制：包名不可变、版本递增；已有 Entity / Attribute / 表 / 列 / 类型 / 排序键 / 分区键不可删改；推送任务名、Dashboard Code、Skill ID、UI 配置索引保持稳定。本插件 `03_api` 无迁移，无 MySQL 数据。
5. 卸载会删除菜单、Dashboard、低代码配置、推送任务、Meta 与 Meta 关联的 ClickHouse 表及数据、运行时 Skill；不会回滚 MySQL（本插件无）。

## UI 页面

| 页面 | 配置索引 | 说明 |
| --- | --- | --- |
| 智能体安全态势总览 | `com.coolxer.plugin.sentinel.app`（overview.json） | 累计/今日事件量、近 7 天趋势、风险等级/命中类别/建议动作/判定点分布 |
| 智能体安全事件列表 | `com.coolxer.plugin.sentinel.app`（event-list.json） | 多维检索、默认列、详情链接 |
| 智能体安全事件详情 | `com.coolxer.plugin.sentinel.detail-security-event` | 按 `record_id` 展示判定、请求与会话、命中正文、命中规则与证据 |

所有页面（低代码）统一位于 `04_ui/`，通过 `com.coolxer.plugin.sentinel.app` 应用导航访问。本插件不注册独立看板：`05_dashboard/config.json` 为 `[]`，`05_dashboard/` 下只有该配置文件，不放任何页面配置。

## 菜单

| 菜单项 | 类型 | 参数 |
| --- | --- | --- |
| 智能体安全监测配置 | POLICY_CONFIG | `com.coolxer.plugin.sentinel.app` |
| 智能体安全监测 | LOW_CODE_APP | `com.coolxer.plugin.sentinel.app` |

## 运行时 Skill

| Skill ID | 入口 | 工具范围 |
| --- | --- | --- |
| `sentinel-security-event-analysis` | 智能体安全研判 | 只读 `retrieval`：`retrieval_list_entity`、`retrieval_list_attribute`、`retrieval_search` |
| `sentinel-security-posture-analysis` | 智能体安全态势 | 只读 `retrieval`：`retrieval_list_entity`、`retrieval_list_attribute`、`entity_overview`、`entity_summary`、`entity_trend`、`entity_distribution` |

无外部 MCP 连接（`06_mcp/config.json` 为 `[]`）。工具可用性还取决于全局 MCP 开关、Agent Scope 与工具审批策略。

## 构建

```bash
cd zenvis-plugin/zenvis-plugin-sentinel
bash build.sh plugin-sentinel
```

产出归档 `com-coolxer-plugin-sentinel.tar.gz`（归档根目录直接包含 `index.json`）。

## 版本历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| 1.0.0 | 2026-10-13 | 初始版本：接入 `sentinel.security.event.v1`，直接消费上游主题并解析入库，1 个实体、列表/详情/态势页、2 个只读 Skill |
