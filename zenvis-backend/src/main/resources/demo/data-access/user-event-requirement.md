# 用户事件数据接入

## 1. 数据格式定义

### 1.1 实体定义

| 项目 | 内容 |
| --- | --- |
| 实体英文名 | user_event |
| 实体中文名 | 用户事件数据 |
| 数据描述 | 记录用户登录、点击、浏览、删除、修改等行为事件，用于测试验证场景。 |
| 数据类型 | 用户事件日志 |
| 目标表名（可选） | msg_user_event |

### 1.2 字段清单

| 字段名 | 样例值 | 中文名 | 字段含义 | 建议类型 | 是否展示 | 查询方式/备注 |
| --- | --- | --- | --- | --- | --- | --- |
| event_id | evt-550e8400-e29b-41d4-a716-446655440000 | 事件ID | 业务侧事件标识符 | String | 是 | equal、notequal、in |
| procid | 104 | 进程id | 产生事件时关联的进程编号 | UInt16 | 是 | greatthan、lessthan、greatequalthan、lessequalthan、between |
| user | dGVzdC11c2Vy | 用户 | 用户名称或脱敏后的用户标识 | String | 是 | equal、notequal、in |
| event_type | login | 事件类型 | 用户行为事件类型 | String | 是 | equal、notequal、in；枚举值见关键字段与特殊类型 |
| reliability | 8.6 | 可信度 | 行为的可信评估结果 | Float64 | 是 | equal、notequal、greatthan、lessthan、greatequalthan、lessequalthan、between |
| detail | {"method":"POST","path":"/v1/orders","query":"dry_run=false"} | 数据详情 | 事件明细 JSON 数据 | json | 是 | 作为 JSON 展示，不配置查询操作 |
| tags | ["登录","认证"] | 标记 | 事件标签数组 | Array(String) | 是 | in；作为数组展示 |
| server_time | 2026-07-08 10:30:00 | 入库时间 | 数据写入或服务端处理时间 | DateTime64(3) | 是 | greatthan、lessthan、greatequalthan、lessequalthan、between |

### 1.3 示例数据

```json
{
  "event_type": "login",
  "tags": ["登录", "认证"],
  "event_id": "evt-550e8400-e29b-41d4-a716-446655440000",
  "user": "dGVzdC11c2Vy",
  "procid": 104,
  "reliability": 8.6,
  "detail": {
    "method": "POST",
    "path": "/v1/orders",
    "query": "dry_run=false"
  },
  "server_time": "2026-07-08 10:30:00"
}
```

### 1.4 关键字段与特殊类型

| 项目 | 内容 |
| --- | --- |
| 业务标识字段 | event_id；平台记录ID `zenvis_id` 由系统自动生成，不需要配置或写入。 |
| 排序字段 | server_time |
| 时间字段 | server_time，格式为 yyyy-MM-dd HH:mm:ss |
| 枚举字段 | event_type：登录=login、点击=click、浏览=view、删除=delete、修改=modify、其他=other |
| 数组字段 | tags：Array(String) |
| JSON 字段 | detail：JSON，包含 method、path、query 等请求上下文 |
| 其他特殊字段 | reliability 为 0.0 到 10.0 的数值评分 |

## 2. 数据来源、解析清洗映射与推送规则

### 2.1 数据来源定义

| 项目 | 内容 |
| --- | --- |
| 数据源类型 | demo_logs |
| 连接信息 | 无，使用定时生成的演示日志。 |
| 认证方式 | 无 |
| 输入格式 | JSON 文本 |
| 输入样例 | {"event_type":"login","tags":["登录","认证"]}、{"event_type":"click","tags":[]}、{"event_type":"view","tags":[]}、{"event_type":"delete","tags":["已认证"]}、{"event_type":"modify","tags":["重要","有风险"]} |

### 2.2 解析、清洗与映射规则

| 项目 | 内容 |
| --- | --- |
| 解析规则 | 将输入日志中的 message 按 JSON 解析为事件对象。 |
| 字段映射 | 保留 event_type、tags；自动补齐 event_id、user、procid、reliability、detail、server_time；不映射平台字段 zenvis_id、zenvis_insert_time。 |
| 清洗规则 | 不过滤，全部保留；ClickHouse 写入时跳过未知字段。 |
| 转换规则 | event_id 使用业务事件标识；user 使用随机字节的 base64 字符串；procid 生成 100 到 110 的整数；reliability 生成 0.0 到 10.0 的浮点数；detail 固定为 {"method":"POST","path":"/v1/orders","query":"dry_run=false"}；server_time 使用当前时间格式化为 yyyy-MM-dd HH:mm:ss。 |
| 异常数据处理 | 同时输出到 console，编码为 JSON，便于调试观察。 |

### 2.3 推送规则

| 数据类型或条件 | 对应实体 | 说明 |
| --- | --- | --- |
| 全部用户事件数据 | user_event / 用户事件数据 | 写入 msg_user_event 表；目标库默认为系统的 zenvis 库。 |
