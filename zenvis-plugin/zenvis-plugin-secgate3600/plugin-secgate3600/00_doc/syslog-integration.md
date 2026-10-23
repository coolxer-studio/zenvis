# SecGate 3600 NSG Syslog 对接说明

## 适用范围

本插件面向网神 SecGate 3600 防火墙 V3.6.6.0 的文本 Syslog 外发格式：

```text
key1="value1" key2="value2" ... keyn="valuen"
```

当前独立建模以下八类实际接入场景：

| 内层 `type` | 内层 `module` | 实体 | 内层字段数 |
| --- | --- | --- | ---: |
| `traffic-end` | `flow` | `secgate3600_nsg_traffic_event` | 61 |
| `dns` | `dns` | `secgate3600_nsg_dns_event` | 55 |
| `self-end` | `flow` | `secgate3600_nsg_self_end_flow_event` | 61 |
| `threat` | 非空 | `secgate3600_nsg_threat_event` | 67 或 69 |
| `system` | `alert` | `secgate3600_nsg_system_alert_event` | 10 |
| `system` | `upgrade` | `secgate3600_nsg_system_upgrade_event` | 10 |
| `system` | `restore` | `secgate3600_nsg_system_restore_event` | 10 |
| `system` | `admin` | `secgate3600_nsg_system_admin_event` | 10 |

此外，每个实体保存 11 个由 Vector Syslog 源产生的外层字段（`syslog_appname`、`syslog_facility`、`syslog_host`、`syslog_hostname`、`syslog_procid`、`syslog_severity`、`syslog_source_ip`、`syslog_source_type`、`syslog_timestamp`、`syslog_version`、`syslog_message`）。

## 处理语义

- 原始接收阶段只负责把完整 Syslog 事件写入 Kafka，不做业务字段改写。
- 转换阶段先解析外层 JSON，再去除内层消息开头可能存在的 BOM，使用严格 quoted key-value 解析。
- `time` 为 Unix 秒时间戳，写入 UTC `DateTime64(3)`。
- 端口限制在 0–65535；计数、会话 ID 与风险等级按无符号整数校验，不把非法值默认为 0。
- `focus_type`、`non_standard_port` 只接受 `YES`/`NO`，写入 ClickHouse `Bool`。
- 威胁日志兼容 67/69 个内层字段；URL 中未转义引号的已知形式做保留式兼容解析。
- 来源 IP 通过 `SECGATE3600_NSG_ALLOWED_SOURCE_IPS` 控制；默认 `*` 表示不限制，生产环境应配置明确的设备地址列表。

## 未建模日志

官方手册还列出操作日志（`operate`）、URL、邮件过滤、内容、行为、即时通讯、SSL 解密和 NAT444 等类型，以及 `alert`/`upgrade`/`restore`/`admin` 之外的 `system` 模块。本插件不会把这些日志写入 Kafka 或 ClickHouse；它们会由转换服务的未匹配路由输出到 Vector stdout，便于按需扩展。

## 异常与重放

已进入原始主题、通过类型分流，但字段数量、必填字段、时间、端口、数值、布尔值或外层 Syslog 字段校验失败的记录不会写入 ClickHouse，而是进入 Kafka 死信主题。死信保留原始 `message`、`metadata.dropped.component_id`、`metadata.dropped.message` 和 `metadata.dropped.reason`。重放时应提取原始消息，修复后写回 `SECGATE3600_NSG_RAW_TOPIC`，不要直接写回死信封装对象。
