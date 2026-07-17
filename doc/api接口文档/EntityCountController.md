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
| 2 | GET | `/api/v1/entity/trend` | 实体趋势统计 | 获取多个实体的趋势数据 |
| 3 | GET | `/api/v1/entity/statistics` | 实体字段统计 | 获取实体指定字段的统计信息 |
| 4 | GET | `/api/v1/entity/ip-statistics` | 跨实体 IP 统计 | 获取指定 IP 在多个实体中的数据量统计 |

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
curl -X GET "http://localhost:8080/api/v1/entity/count?entities=asset_host,asset_pc,asset_mobile"
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

### 2️⃣ 实体趋势统计

**接口地址**: `GET /api/v1/entity/trend`

**功能描述**: 获取多个实体的趋势数据

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| entities | List\<String\> | 是 | 实体名称列表（逗号分隔） |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/entity/trend?entities=asset_host,asset_pc"
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

### 3️⃣ 实体字段统计

**接口地址**: `GET /api/v1/entity/statistics`

**功能描述**: 获取实体指定字段的统计信息

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| entities | List\<String\> | 是 | 实体名称列表（逗号分隔） |
| field | String | 是 | 统计字段名称 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/entity/statistics?entities=asset_host&field=status"
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

### 4️⃣ 跨实体 IP 统计

**接口地址**: `GET /api/v1/entity/ip-statistics`

**功能描述**: 按传入顺序统计指定 IP 在多个实体中的数据量，返回汇总、逐实体明细及可直接用于图表的横轴和序列数据。

**查询参数**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| entities | List\<String\> | 是 | 实体名称列表；支持逗号分隔，也支持重复传入参数。重复实体仅按首次出现的位置统计一次 |
| ip | String | 是 | 待统计的非空 IPv4 或 IPv6 地址，接口使用精确匹配 |

**请求示例**:

```bash
curl -G "http://localhost:8080/api/v1/entity/ip-statistics" \
  --data-urlencode "entities=traffic_event" \
  --data-urlencode "entities=domain_event" \
  --data-urlencode "ip=192.0.2.1"
```

也可以使用逗号分隔实体：

```bash
curl -G "http://localhost:8080/api/v1/entity/ip-statistics" \
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

## 📊 响应码汇总

| 响应码 | 说明 | 触发场景 |
|--------|------|---------|
| 0 | 请求成功 | 操作成功完成 |
| -1 | 未知错误 | 遇到未定义的异常情况 |

---

## 🔐 注意事项

1. **认证授权**: 需要登录认证
2. **多实体支持**: 支持同时统计多个实体的数据
