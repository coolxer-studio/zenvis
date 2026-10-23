# 演示数据推送服务

## 用途

`SecGate 3600 NSG 演示数据生成` 用于尚未接入真实防火墙时预览低代码页面、详情页、IP 关联统计和态势看板效果。演示服务与正式接入服务相互独立，可在平台推送任务页面单独停止。

## 数据来源与链路

- 固定样例取自正式接入链路中已通过校验的九条事件，覆盖 `traffic-end/flow`、`dns/dns`、`self-end/flow`、`threat/blacklist`、`threat/ips`、`system/alert`、`system/upgrade`、`system/restore` 和 `system/admin`。
- 生成时刷新事件时间、DNS 毫秒时间、会话 ID、设备序列号和会话时长，并设置 `source_type=demo`。
- 演示事件写入正式原始 Topic `SECGATE3600_NSG_RAW_TOPIC`，随后复用 `kafka-to-clickhouse.yaml` 的正式校验、分流、死信和入库逻辑。
- 演示服务不直接写 ClickHouse，不新增实体、字段或 Topic。

## 配置与运行

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SECGATE3600_NSG_DEMO_INTERVAL_SECONDS` | `5` | 每条演示事件的生成间隔（秒） |
| `SECGATE3600_NSG_DEMO_SOURCE_IP` | `192.0.2.10` | 演示事件外层 `source_ip` |
| `SECGATE3600_NSG_RAW_TOPIC` | `secgate3600-nsg-syslog-raw` | 与正式链路共用的原始 Topic |

演示事件的外层 `source_ip` 仍受 `SECGATE3600_NSG_ALLOWED_SOURCE_IPS` 校验。如果生产环境已配置明确设备地址，需要临时加入演示来源 IP；若仍为 `*`，则无需调整。

## 上线与停用

1. 未接入真实设备时，启动 `SecGate 3600 NSG 演示数据生成`，等待数据进入八张表后检查页面与看板。
2. 正式设备接入前，在推送任务页面手动停用 `SecGate 3600 NSG 演示数据生成`。
3. 确认演示服务已停止后，再按现场要求恢复或收紧 `SECGATE3600_NSG_ALLOWED_SOURCE_IPS`。

演示数据为合成数据，不应用于生产取证、真实告警处置或长期趋势判断。
