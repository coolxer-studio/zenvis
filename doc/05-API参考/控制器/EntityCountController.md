# EntityCountController 实体统计接口文档

**基础信息**
- **模块名称**: 实体统计
- **基础路径**: `/api/v1/entity`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. ResponseWrap (统一响应格式)

```json
{
  "status": 0,                // Integer - 响应码(0:成功，其他:失败)
  "msg": "success",           // String - 响应消息
  "data": {}                  // Object - 响应数据
}
```

---

## 📊 接口总览

| 序号 | HTTP方法 | 接口路径 | 接口名称 | 功能描述 |
|:---:|:-------:|---------|---------|---------|
| 1 | GET | `/api/v1/entity/count` | 实体数量统计 | 获取多个实体的数量统计 |
| 2 | GET | `/api/v1/entity/count-increase` | 实体当日增量统计 | 同时获取总量和当日增量 |
| 3 | GET | `/api/v1/entity/trend` | 实体趋势统计 | 获取多个实体的趋势数据 |
| 4 | GET | `/api/v1/entity/statistics` | 实体字段统计 | 获取实体指定字段的统计信息 |
| 5 | GET | `/api/v1/entity/ip-statistics` | 跨实体 IP 统计 | 获取指定 IP 在多个实体中的数据量统计 |
| 6 | POST | `/api/v1/entity/ip-relations/query` | 跨实体 IP 关系聚合 | 按显式逻辑字段映射和时间范围聚合真实对端 IP |

---

## 🔌 接口详情

### 1️⃣ 实体数量统计

**接口地址**: `GET /api/v1/entity/count`

**功能描述**: 获取多个实体的数量统计

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| entities | List\<String\> | 是 | 实体名称列表（逗号分隔） |

**请求示例**:
```bash
curl -X GET "http://localhost:11001/api/v1/entity/count?entities=asset_host,asset_pc,asset_mobile"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "asset_host": 100,
    "asset_pc": 200,
    "asset_mobile": 150
  }
}
```

---

### 2️⃣ 实体当日增量统计

**接口地址**: `GET /api/v1/entity/count-increase`

**功能描述**: 同时返回各实体累计数量和当日新增数量。

**查询参数**: 与 `/count` 相同，`entities` 为必填实体名称列表。

```bash
curl -X GET "http://localhost:11001/api/v1/entity/count-increase?entities=asset_host,asset_pc"
```

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "count": {
      "asset_host": 100,
      "asset_pc": 200
    },
    "countToday": {
      "asset_host": 5,
      "asset_pc": 8
    }
  }
}
```

`countToday` 是 Controller 写入 `Map` 的显式键，不经过 DTO 的 `snake_case` 属性转换，调用方应按该驼峰键读取。

---

### 3️⃣ 实体趋势统计

**接口地址**: `GET /api/v1/entity/trend`

**功能描述**: 获取多个实体的趋势数据

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| entities | List\<String\> | 是 | 实体名称列表（逗号分隔） |

**请求示例**:
```bash
curl -X GET "http://localhost:11001/api/v1/entity/trend?entities=asset_host,asset_pc"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "asset_host": {
      "dates": ["2024-01-01", "2024-01-02"],
      "counts": [100, 105]
    },
    "asset_pc": {
      "dates": ["2024-01-01", "2024-01-02"],
      "counts": [200, 210]
    }
  }
}
```

---

### 4️⃣ 实体字段统计

**接口地址**: `GET /api/v1/entity/statistics`

**功能描述**: 获取实体指定字段的统计信息

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| entities | List\<String\> | 是 | 实体名称列表（逗号分隔） |
| field | String | 是 | 统计字段名称 |

**请求示例**:
```bash
curl -X GET "http://localhost:11001/api/v1/entity/statistics?entities=asset_host&field=status"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "asset_host": {
      "active": 80,
      "inactive": 20
    }
  }
}
```

---

### 5️⃣ 跨实体 IP 统计

**接口地址**: `GET /api/v1/entity/ip-statistics`

**功能描述**: 按传入顺序统计指定 IP 在多个实体中的数据量，返回汇总、逐实体明细及可直接用于图表的横轴和序列数据。

**查询参数**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| entities | List\<String\> | 是 | 实体名称列表；支持逗号分隔，也支持重复传入参数。重复实体仅按首次出现的位置统计一次 |
| ip | String | 是 | 待统计的非空 IPv4 或 IPv6 地址，接口使用精确匹配 |

**请求示例**:

```bash
curl -G "http://localhost:11001/api/v1/entity/ip-statistics" \
  --data-urlencode "entities=traffic_event" \
  --data-urlencode "entities=domain_event" \
  --data-urlencode "ip=192.0.2.1"
```

也可以使用逗号分隔实体：

```bash
curl -G "http://localhost:11001/api/v1/entity/ip-statistics" \
  --data-urlencode "entities=traffic_event,domain_event" \
  --data-urlencode "ip=192.0.2.1"
```

**统计语义**:

1. 每个实体只使用其中存在的逻辑字段 `src_ip`、`dst_ip`、`dest_ip`；逻辑字段会通过元数据映射到实际数据库列。
2. 同一实体内的多个 IP 字段使用 OR 精确匹配，并通过一次 `count(*)` 统计，因此同一条数据即使多个字段同时匹配也只计数一次。
3. 存在但没有上述 IP 字段的实体仍会返回一行，`fields` 为空且 `total` 为 0；不存在的实体会跳过。
4. 响应中的实体和图表数据保持请求中实体首次出现的顺序。

**成功响应**:

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "ip": "192.0.2.1",
    "total": 12,
    "entity_count": 2,
    "matched_entity_count": 1,
    "rows": [
      {
        "entity": "traffic_event",
        "label": "流量事件",
        "fields": ["src_ip", "dst_ip"],
        "total": 12
      },
      {
        "entity": "domain_event",
        "label": "域名事件",
        "fields": [],
        "total": 0
      }
    ],
    "xaxis_data": ["流量事件", "域名事件"],
    "series_data": [12, 0]
  }
}
```

| 响应字段 | 说明 |
|---------|------|
| ip | 去除首尾空白后的查询 IP |
| total | 所有返回实体的数据量之和 |
| entity_count | `rows` 中返回的实体数量 |
| matched_entity_count | `total` 大于 0 的实体数量 |
| rows | 按实体列出的统计明细 |
| rows[].fields | 该实体实际参与统计的逻辑 IP 字段 |
| xaxis_data | 与 `rows` 同序的实体展示名称，可用作图表横轴 |
| series_data | 与 `rows` 同序的实体数据量，可用作图表序列 |

---

### 6️⃣ 跨实体 IP 关系聚合

**接口地址**: `POST /api/v1/entity/ip-relations/query`

**功能描述**: 以当前 IP 为中心，在指定业务时间范围内跨实体聚合真实对端 IP。查询在 ClickHouse 内通过带时间条件的 `UNION ALL` 完成，只向 Java 返回全局 Top N 和实体级汇总，不加载原始明细。

**请求体**:

```json
{
  "ip": "192.0.2.1",
  "startTime": "2026-07-18 00:00:00",
  "endTime": "2026-07-24 15:30:00",
  "limit": 50,
  "entities": [
    "jmr_0001_controlled_event",
    "jmr_0022_rule_report"
  ],
  "relationMappings": [
    {
      "entity": "jmr_0001_controlled_event",
      "sourceField": "src_ip",
      "targetField": "dst_ip",
      "timeField": "found_time"
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| ip | String | 是 | 中心 IPv4 或 IPv6；仅去除首尾空白，之后精确匹配 |
| startTime | String | 是 | 开始时间，严格格式 `yyyy-MM-dd HH:mm:ss` |
| endTime | String | 是 | 结束时间，严格格式 `yyyy-MM-dd HH:mm:ss` |
| limit | Integer | 是 | 仅允许 `20`、`50`、`100` |
| entities | Array\<String\> | 是 | 统计范围内的 Meta 实体逻辑名称；不可重复 |
| relationMappings | Array\<Object\> | 是 | 参与关系聚合的显式字段映射 |
| relationMappings[].entity | String | 是 | 必须存在于 `entities`，每个实体最多一组映射 |
| relationMappings[].sourceField | String | 是 | Meta 源 IP 逻辑字段，必须是标量 `String` |
| relationMappings[].targetField | String | 是 | Meta 目的 IP 逻辑字段，必须是标量 `String` |
| relationMappings[].timeField | String | 是 | Meta 业务时间逻辑字段，必须是 `DateTime` 或 `DateTime64` |

**校验和安全约束**:

1. 三个字段必须属于映射声明的同一实体且互不重复。接口只按 Meta 的 `name` 解析逻辑字段，再使用相应 `column_name`；未知字段、跨实体字段和仅传物理列名都会被拒绝。
2. 开始时间不得晚于结束时间，时间跨度可等于但不得超过 90 天。时间边界按 `app.retrieval.time-zone` 解释，默认 `Asia/Shanghai`，开始和结束边界均包含。
3. 表名与列名只能来自已校验的 Meta 并再次执行安全标识符校验；IP、时间和 Top 对端使用查询参数绑定。
4. 当前 IP 位于源字段时记为 `outbound`，位于目的字段时记为 `inbound`。空对端和当前 IP 指向自身的自环会从关系聚合中排除。
5. 同一条记录若在两个方向分别满足条件，会按相应方向产生关系计数；`total = inbound + outbound`。IPv6 不做地址规范化。

**成功响应**:

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "ip": "192.0.2.1",
    "start_time": "2026-07-18 00:00:00",
    "end_time": "2026-07-24 15:30:00",
    "time_zone": "Asia/Shanghai",
    "limit": 50,
    "total": 18,
    "entity_count": 2,
    "matched_entity_count": 1,
    "relation_total": 16,
    "peer_count": 2,
    "peer_total": 2,
    "has_more": false,
    "peers": [
      {
        "ip": "198.51.100.8",
        "total": 12,
        "inbound": 4,
        "outbound": 8,
        "entities": [
          {
            "entity": "jmr_0001_controlled_event",
            "label": "木马和僵尸网络受控事件",
            "total": 12,
            "inbound": 4,
            "outbound": 8
          }
        ]
      }
    ],
    "rows": [
      {
        "entity": "jmr_0001_controlled_event",
        "label": "木马和僵尸网络受控事件",
        "fields": ["src_ip", "dst_ip"],
        "time_field": "found_time",
        "total": 18
      },
      {
        "entity": "jmr_0022_rule_report",
        "label": "规则下发结果上报",
        "fields": [],
        "time_field": null,
        "total": 0
      }
    ],
    "xaxis_data": [
      "木马和僵尸网络受控事件",
      "规则下发结果上报"
    ],
    "series_data": [18, 0]
  }
}
```

| 响应字段 | 说明 |
|---------|------|
| total | 时间范围内，各映射实体中源或目的 IP 匹配当前 IP 的去重记录数之和 |
| relation_total | 排除空对端和自环后的全部方向关联次数，不受 Top N 截断影响 |
| peer_count | 当前 `peers` 实际返回数量 |
| peer_total | 时间范围内全部不同对端 IP 数量 |
| has_more | `peer_total` 是否超过请求的 `limit` |
| peers | 按 `total` 降序、IP 升序返回的全局 Top N |
| peers[].entities | 当前对端按实体聚合后的方向和数量 |
| rows | 与 `entities` 同序的时间范围统计；没有关系映射的实体保留为 0 |

---

## 📊 响应码汇总

| 响应码 | 说明 | 触发场景 |
|--------|------|---------|
| 0 | 请求成功 | 操作成功完成 |
| -1 | 未知错误 | 遇到未定义的异常情况 |

---

## 🔐 注意事项

1. **认证授权**: 需要登录认证
2. **多实体支持**: 支持同时统计多个实体的数据
