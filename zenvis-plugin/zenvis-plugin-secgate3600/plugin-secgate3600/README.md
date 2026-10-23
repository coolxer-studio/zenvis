# 奇安信网神 SecGate 3600 NSG 防火墙插件

版本：`1.0.0`  
包名：`com.coolxer.plugin.secgate3600`

本插件独立对接奇安信网神 SecGate 3600 NSG 防火墙的 Syslog 外发日志，提供流量会话、DNS 解析、本机终结会话、威胁事件和四类系统事件的采集、严格校验、ClickHouse 入库、低代码检索、详情查看、IP 关联统计与态势看板。未接入真实设备时，可使用独立的演示数据服务先行查看页面与看板效果。

## 能力范围

| 数据定义 | Entity / ClickHouse 表 | 日志类型 | 字段数 |
| --- | --- | --- | ---: |
| `secgate3600_nsg_traffic_event` | `secgate3600_nsg_traffic_event` | 流量会话事件 | 72 |
| `secgate3600_nsg_dns_event` | `secgate3600_nsg_dns_event` | DNS 解析事件 | 66 |
| `secgate3600_nsg_self_end_flow_event` | `secgate3600_nsg_self_end_flow_event` | 本机终结会话 | 72 |
| `secgate3600_nsg_threat_event` | `secgate3600_nsg_threat_event` | 威胁事件 | 80 |
| `secgate3600_nsg_system_alert_event` | `secgate3600_nsg_system_alert_event` | 系统告警 | 21 |
| `secgate3600_nsg_system_upgrade_event` | `secgate3600_nsg_system_upgrade_event` | 系统升级事件 | 21 |
| `secgate3600_nsg_system_restore_event` | `secgate3600_nsg_system_restore_event` | 配置恢复事件 | 21 |
| `secgate3600_nsg_system_admin_event` | `secgate3600_nsg_system_admin_event` | 管理员事件 | 21 |

合计 8 个实体、8 张表、374 个业务 Attribute。威胁表不配置 TTL，其余七张表按 `zenvis_insert_time` 保留 1 天。

## 推送链路

```text
正式链路
SecGate 3600 Syslog
  → 12001/UDP（可配置）
  → secgate3600-nsg-syslog-raw
  → 严格解析、来源与字段校验
      ├── 合法 → 八张 ClickHouse 表
      ├── 已识别类型异常 → secgate3600-nsg-dead-letter
      └── 未建模类型/模块 → Vector stdout

演示链路（可选）
SecGate 3600 NSG 演示数据生成
  → secgate3600-nsg-syslog-raw（同一正式原始 Topic）
  → 复用上述严格解析、来源与字段校验
  → 八张 ClickHouse 表
```

安装后创建三个服务：

1. `SecGate 3600 NSG Syslog 原始接入`：`syslog-to-kafka.yaml`，正式设备接入入口。
2. `SecGate 3600 NSG Kafka 校验入库`：`kafka-to-clickhouse.yaml`，正式校验与入库链路。
3. `SecGate 3600 NSG 演示数据生成`：`demo-data-to-kafka.yaml`，可选演示服务，默认每 5 秒随机产生一条覆盖八类日志的合成事件。

演示服务只向 `SECGATE3600_NSG_RAW_TOPIC` 写入合成事件，不直接写 ClickHouse，也不维护第二套字段映射。正式设备接入后，请在平台推送任务页面手动停用 `SecGate 3600 NSG 演示数据生成`，避免真实数据与演示数据混存。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SECGATE3600_NSG_SYSLOG_ADDRESS` | `0.0.0.0:12001` | Syslog 监听地址 |
| `SECGATE3600_NSG_SYSLOG_MODE` | `udp` | `udp` 或 `tcp` |
| `SECGATE3600_NSG_RAW_TOPIC` | `secgate3600-nsg-syslog-raw` | 正式接入与演示服务共用的原始 Kafka Topic |
| `SECGATE3600_NSG_GROUP_ID` | `secgate3600-nsg-transform-v1` | 转换消费者组 |
| `SECGATE3600_NSG_DLQ_TOPIC` | `secgate3600-nsg-dead-letter` | 死信 Topic |
| `SECGATE3600_NSG_ALLOWED_SOURCE_IPS` | `*` | 允许的设备来源 IP，逗号分隔；生产环境建议显式配置 |
| `SECGATE3600_NSG_DEMO_INTERVAL_SECONDS` | `5` | 演示服务生成间隔（秒） |
| `SECGATE3600_NSG_DEMO_SOURCE_IP` | `192.0.2.10` | 演示数据外层来源 IP，也是正式链路来源校验 IP |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` | Kafka Broker |
| `CLICKHOUSE_ENDPOINT` | `http://clickhouse-service:8123` | ClickHouse 地址 |
| `CLICKHOUSE_DATABASE` | `zenvis` | ClickHouse 数据库 |
| `CLICKHOUSE_USER` | `default` | ClickHouse 用户 |
| `CLICKHOUSE_PASSWORD` | 平台默认值 | ClickHouse 密码，部署时覆盖 |

> 如果 `SECGATE3600_NSG_ALLOWED_SOURCE_IPS` 已配置为明确设备地址，需临时加入演示来源 IP `192.0.2.10`（或设置的 `SECGATE3600_NSG_DEMO_SOURCE_IP`），否则演示事件会被正式链路判为非法并进入死信。

## 页面入口

- 低代码应用：`com.coolxer.plugin.secgate3600.app`，包含八类日志列表及详情。
- IP 关联统计：`com.coolxer.plugin.secgate3600.ip-statistics`，查询流量、DNS、本机终结和威胁日志中的参与方 IP。
- 态势看板：`com.coolxer.plugin.secgate3600.dashboard.nsg-overview`。
- 菜单：应用、可视化配置和 IP 关联调查。

## 安全与运维

- 原始接入阶段保留完整事件，不改写业务字段。
- 已识别类型的解析或类型转换失败只进入插件专属死信，不写 ClickHouse。
- 未建模的官方日志类型和未知 `system` 模块输出 Vector stdout，不进入 Kafka/ClickHouse/DLQ。
- 死信 Topic 与正常主题分离；重放时提取原始 `message` 写回原始 Topic。
- 演示数据为固定样例派生，事件时间、会话 ID、设备序列号和会话时长会在运行时刷新，但不代表真实网络事件；禁止用于生产取证或长期留存分析。
- 正式设备接入后必须手动停用演示服务；插件升级或重新安装服务时也应确认该服务保持停用。
- 插件不包含动态 API、MCP 或凭据；ClickHouse 密码由部署环境覆盖。

## 验证结果与已知限制

- 已使用 Vector 0.49.0 完成三份 Push Task 配置校验；正式转换执行 11 条单元测试，演示服务执行 9 条单元测试，覆盖八类日志和 dropped 分支。
- 已完成 Meta、映射字段、UI 页面引用和归档结构静态校验。
- 尚未连接真实 SecGate 3600、Kafka 和 ClickHouse 做端到端联调；上线前应使用现场设备验证 Syslog 编码、外层字段和字段数量。
- 官方手册中的操作、URL、邮件过滤、内容、行为、即时通讯、SSL 解密、NAT444 以及部分 `system` 模块尚未建模，不会进入 ClickHouse。

详细字段和未建模范围见 `00_doc/syslog-integration.md` 与 `00_doc/契约矩阵.md`；演示服务的样例、参数和停用步骤见 `00_doc/demo-data.md`。

## 版本记录

### 1.0.0 - 2026-10-22

- 首次发布奇安信网神 SecGate 3600 NSG 防火墙独立插件，包名为 `com.coolxer.plugin.secgate3600`。
- 支持流量会话、DNS 解析、本机终结会话、威胁事件和四类系统事件的 Syslog 原始接入、严格校验与 ClickHouse 入库。
- 提供 8 个独立实体和数据表、插件专用死信主题、低代码列表/详情页、IP 关联统计与安全态势看板。
- 提供可独立停止的演示数据推送服务，复用正式校验与入库链路。
- 图表使用 adaptor 生成原生 HTML/SVG，支持低代码菜单切换、空数据提示和标签 HTML 转义。
