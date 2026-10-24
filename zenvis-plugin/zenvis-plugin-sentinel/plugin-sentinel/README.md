# 智能体安全监测插件

## 概述

本插件同时接入两条上游流：

- **Sentinel 安全事件流**：`ai-agent-security-result`，消息类型 `sentinel.security.event.v1`，兼容 `schema_version` 1.0 / 1.1；
- **Warden 会话流**：`ai-agent-chat-message`，消息类型 `chat.session.*.v1`，`schema_version` 1.0。

插件提供原始留存、结构化入库、检索、安全事件详情、会话事件详情、按 `session_id` 重建会话消息线、安全态势总览，以及三个只读 AI 研判 Skill。安全事件与会话消息通过 `message_id` / `msg_hash` 关联；两条流由独立推送任务写入独立 ClickHouse 实体，互不覆盖。

## 插件信息

| 项目 | 值 |
| --- | --- |
| 插件名称 | 智能体安全监测 |
| 包名 | `com.coolxer.plugin.sentinel` |
| 版本 | 1.1.0 |
| 作者 | CoolXer |
| 能力目录 | 00_doc / 01_meta / 02_push-task / 03_api(空) / 04_ui / 05_dashboard / 06_menu / 07_mcp(空) / 08_rag(未声明) / 09_skill |

## 适用场景与边界

**适用**：接入 Sentinel 镜像档安全事件与 Warden 会话消息线，做安全命中统计、检索、复盘、会话现场还原与只读研判；识别提示注入、越狱、资源攻击、敏感数据、越界请求与数据外渗等风险。

**不适用 / 明确边界**：

- 本插件**不实际阻断或改写**请求。Sentinel 镜像档的 `action` 是策略建议动作，不代表请求已被拦截。
- 会话事件包含消息原文，可能含敏感内容；页面与 Skill 均为只读，访问范围仍需由角色权限和数据治理要求控制。
- 两条流均为 at-least-once 投递。安全事件按 `event_id` 去重；会话事件按 `event_key` 去重（`append` → `piece_id`，`snapshot` → `snapshot_id`，`mutation` → `mutation_id`，`started` → `st_<session_id>`）。插件入库不主动去重，重放会保留重复记录，查询与重建时按幂等键处理。
- `started` 事件上游不提供独立 ID，多实例同时首见时可能产生重复；消息线重建时按 `(type, session_id)` 兜底去重。
- 本插件不提供外部 MCP 连接与写操作；三个 Skill 均为只读检索。

## 数据映射

| 数据来源 | 消息类型 | Entity | ClickHouse 表 | Kafka Topic | UI 页面 |
| --- | --- | --- | --- | --- | --- |
| Sentinel 安全事件 | `sentinel.security.event.v1` | `sentinel_security_event` | `sentinel_security_event` | `ai-agent-security-result`（直接消费） | 事件列表 / 事件详情 / 态势总览 |
| Warden 会话事件 | `chat.session.*.v1` | `sentinel_chat_session_event` | `sentinel_chat_session_event` | `ai-agent-chat-message`（直接消费） | 会话事件列表 / 会话事件详情 / 会话消息线 |

字段清单、枚举定义、上游信封与投递语义详见 [00_doc/数据字典与接入说明.md](00_doc/数据字典与接入说明.md)。

## 推送任务架构

两条上游主题均由插件使用专属消费组直接消费，不做 Kafka→Kafka 转存。当前注册两个推送任务：

| 任务名 | 文件 | 说明 |
| --- | --- | --- |
| Sentinel安全事件解析入库 | `02_push-task/sentinel-security-event-clickhouse.yaml` | `ai-agent-security-result` → 协议校验/字段转换 → `sentinel_security_event`，异常进安全事件 DLQ |
| Sentinel会话事件解析入库 | `02_push-task/sentinel-chat-session-event-clickhouse.yaml` | `ai-agent-chat-message` → 四类事件校验/消息抽取 → `sentinel_chat_session_event`，异常进会话事件 DLQ |

```text
ai-agent-security-result ──专属消费组──▶ 校验/转换 ──▶ sentinel_security_event
        └─ 异常 ──▶ sentinel-security-event-dead-letter

ai-agent-chat-message ─────专属消费组──▶ 校验/转换 ──▶ sentinel_chat_session_event
        └─ 异常 ──▶ sentinel-chat-session-event-dead-letter
```

### 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SENTINEL_KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | 安全事件主题所在 Kafka 地址 |
| `SENTINEL_SOURCE_TOPIC` | `ai-agent-security-result` | 上游安全事件主题 |
| `SENTINEL_GROUP_ID` | `sentinel-security-event-clickhouse-v1` | 安全事件专属消费组 |
| `SENTINEL_DLQ_TOPIC` | `sentinel-security-event-dead-letter` | 安全事件死信主题 |
| `SENTINEL_CHAT_KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | 会话流主题所在 Kafka 地址 |
| `SENTINEL_CHAT_SOURCE_TOPIC` | `ai-agent-chat-message` | 上游会话流主题 |
| `SENTINEL_CHAT_GROUP_ID` | `sentinel-chat-session-event-clickhouse-v1` | 会话流专属消费组 |
| `SENTINEL_CHAT_DLQ_TOPIC` | `sentinel-chat-session-event-dead-letter` | 会话流死信主题 |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | 两个死信主题所在 Kafka 地址 |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | ClickHouse 数据库 |
| `CLICKHOUSE_USER` | `default` | ClickHouse 用户 |
| `CLICKHOUSE_PASSWORD` | 内置部署回退值 | 生产环境必须由部署环境覆盖 |

- 时区统一为 `Asia/Shanghai`，两个实体的 `occurred_at` 均入库为 `DateTime64(3)`。安全事件上游为 ISO8601，会话事件上游为 10 位 epoch 秒，转换在推送任务中完成。
- 编码统一为 UTF-8；信封为单条 JSON，不做压缩包装。
- 网络依赖：上游 Kafka、死信 Kafka、ClickHouse。

## DLQ、重放与保留

- 安全事件契约异常写入 `sentinel-security-event-dead-letter`，包括非法 JSON、非预期 `type`、缺 `event_id`、`occurred_at` 无法解析、缺 `payload` / `payload.verdict`、1.1 使用数组证据等。
- 会话事件契约异常写入 `sentinel-chat-session-event-dead-letter`，包括非法 JSON、非预期 `type`、`schema_version` 非 1.0、`occurred_at` 非 epoch 秒、缺 `session_id`、事件幂等键缺失、消息缺少稳定标识或 `original`、mutation 旧/新标识缺失等。
- 两类死信均保留原始 `message` 与 `metadata.dropped.*`，用于排查和重放。
- 原始报文保留在上游主题中。重放时分别重置 `SENTINEL_GROUP_ID` 或 `SENTINEL_CHAT_GROUP_ID` 偏移；重置前确认上游版本与当前 Meta 匹配，并确认可接受重复记录的幂等键。
- 两个 ClickHouse 表均按 `zenvis_insert_time` 月分区，行级 TTL **90 天**。TTL 变更可能导致历史数据永久删除，升级前请确认留存策略。

## 安装与升级

1. 安装顺序由平台统一执行：解压 → Meta/建表 → 推送任务 → MySQL 迁移（本插件无）→ API（本插件无）→ UI → Dashboard → 菜单 → MCP → RAG → Skill。
2. 在推送任务页面**手动启用**「Sentinel安全事件解析入库」和「Sentinel会话事件解析入库」。
3. 菜单安装后**不自动授权**，需在角色权限中分配；会话事件包含消息原文，应额外限制可见角色。
4. 升级限制：包名不可变、版本递增；已有 Entity / Attribute / 表 / 列 / 类型 / 排序键 / 分区键不可删改；推送任务名、Dashboard Code、Skill ID、UI 配置索引保持稳定。本插件 `03_api` 无迁移，无 MySQL 数据。
5. 卸载会删除菜单、Dashboard、低代码配置、推送任务、Meta 与 Meta 关联的 ClickHouse 表及数据、运行时 Skill；不会回滚 MySQL（本插件无）。

## UI 页面

| 页面 | 配置索引 | 说明 |
| --- | --- | --- |
| 智能体安全态势总览 | `com.coolxer.plugin.sentinel.app`（overview.json） | 两类事件的累计/今日量、安全事件趋势与分布、会话事件趋势与类型分布、会话覆盖与消息规模 |
| 智能体安全事件列表 | `com.coolxer.plugin.sentinel.app`（event-list.json） | 安全事件多维检索、事件详情、会话消息线跳转、`message_id` 与请求现场 |
| 智能体会话事件列表 | `com.coolxer.plugin.sentinel.app`（session-event-list.json） | 四类会话事件检索、事件详情、按 `session_id` 进入消息线 |
| 智能体安全事件详情 | `com.coolxer.plugin.sentinel.detail-security-event` | 判定、请求与会话、命中文档、规则、证据与 1.1 字段 |
| 智能体会话事件详情 | `com.coolxer.plugin.sentinel.detail-chat-session-event` | 会话事件上下文、消息数组、mutation 改写与请求头快照 |
| 智能体会话消息线 | `com.coolxer.plugin.sentinel.session-timeline` | 按 `session_id` 去重重建消息线，展示 started / append / snapshot / mutation 事件时间线 |

所有页面均为低代码页面，统一位于 `04_ui/`，通过应用导航或菜单访问。本插件不注册独立 HTML 看板：`05_dashboard/config.json` 为 `[]`。

## 菜单

| 菜单项 | 类型 | 参数 |
| --- | --- | --- |
| 智能体安全监测配置 | POLICY_CONFIG | `com.coolxer.plugin.sentinel.app` |
| 智能体安全监测 | LOW_CODE_APP | `com.coolxer.plugin.sentinel.app` |
| 智能体会话现场 | LOW_CODE_PAGE | `com.coolxer.plugin.sentinel.session-timeline` |

## 运行时 Skill

| Skill ID | 入口 | 工具范围 |
| --- | --- | --- |
| `sentinel-security-event-analysis` | 智能体安全研判 | 只读 `retrieval`：`retrieval_list_entity`、`retrieval_list_attribute`、`retrieval_search` |
| `sentinel-security-posture-analysis` | 智能体安全态势 | 只读 `retrieval`：`retrieval_list_entity`、`retrieval_list_attribute`、`entity_overview`、`entity_summary`、`entity_trend`、`entity_distribution` |
| `sentinel-session-analysis` | 智能体会话现场 | 只读 `retrieval`：`retrieval_list_entity`、`retrieval_list_attribute`、`retrieval_search` |

无外部 MCP 连接（`07_mcp/config.json` 为 `[]`）。工具可用性还取决于全局 MCP 开关、Agent Scope 与工具审批策略。

## 构建

```bash
cd zenvis-plugin/zenvis-plugin-sentinel
bash build.sh plugin-sentinel
```

产出归档 `com-coolxer-plugin-sentinel-1.1.0.tar.gz`（归档根目录直接包含 `index.json`）。

## 版本历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| 1.0.0 | 2026-10-13 | 初始版本：接入 `sentinel.security.event.v1`，直接消费上游主题并解析入库，1 个实体、列表/详情/态势页、2 个只读 Skill |
| 1.1.0 | 2026-10-21 | 增加独立 topic `ai-agent-chat-message` 的 Warden 会话流：新增 `sentinel_chat_session_event` 实体、会话事件列表/详情/消息线页面、会话现场 Skill；安全事件兼容 1.0/1.1，补充 `message_id` 等关联字段，总览增加会话规模与事件类型图表 |
