# DataRiskController 数据风险接口文档

**基础信息**
- **模块名称**: 数据风险管理
- **基础路径**: `/api/v1/risk/data`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. DataRiskDto (数据风险传输对象)

```json
{
  "id": "data-001",           // String - 数据风险ID（更新时使用）
  "type": "DATA_LEAK",        // String - 风险类型
  "description": "描述信息",   // String - 描述信息
  "severity": "HIGH",         // String - 严重程度
  "labels": ["label1"]        // List - 标签列表
}
```

### 2. ResponseWrap (统一响应格式)

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
| 1 | POST | `/api/v1/risk/data` | 添加数据风险 | 添加新的数据风险记录 |
| 2 | DELETE | `/api/v1/risk/data/{id}` | 删除数据风险 | 根据ID删除单个数据风险 |
| 3 | DELETE | `/api/v1/risk/data` | 批量删除数据风险 | 批量删除多个数据风险 |
| 4 | PUT | `/api/v1/risk/data/{id}` | 更新数据风险 | 根据ID更新数据风险信息 |
| 5 | GET | `/api/v1/risk/data/{id}` | 查询数据风险详情 | 根据ID查询数据风险详细信息 |
| 6 | GET | `/api/v1/risk/data/list` | 查询数据风险列表 | 分页查询数据风险列表 |
| 7 | GET | `/api/v1/risk/data/labels` | 获取标签列表 | 获取所有不重复的标签 |
| 8 | GET | `/api/v1/risk/data/ids` | 获取相似ID | 根据关键词获取相似ID |
| 9 | GET | `/api/v1/risk/data/types` | 获取相似类型 | 根据关键词获取相似风险类型 |
| 10 | GET | `/api/v1/risk/data/statistics/total` | 获取总数统计 | 获取数据风险总数 |
| 11 | GET | `/api/v1/risk/data/statistics/increase` | 获取增长统计 | 获取数据风险增长数 |
| 12 | GET | `/api/v1/risk/data/auto_complete/type` | 风险类型自动补全 | 风险类型自动补全（暂未实现） |

---

## 🔌 接口详情

### 1️⃣ 添加数据风险

**接口地址**: `POST /api/v1/risk/data`

**功能描述**: 添加新的数据风险记录

**请求参数**:
- Content-Type: `application/json`
- Body: DataRiskDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/risk/data \
  -H "Content-Type: application/json" \
  -d '{
    "type": "DATA_LEAK",
    "severity": "HIGH"
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "添加成功",
  "data": "添加成功"
}
```

---

### 2️⃣ 删除数据风险

**接口地址**: `DELETE /api/v1/risk/data/{id}`

**功能描述**: 根据ID删除单个数据风险

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 数据风险ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/risk/data/data-001
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "添加成功",
  "data": "添加成功"
}
```

---

### 3️⃣ 批量删除数据风险

**接口地址**: `DELETE /api/v1/risk/data`

**功能描述**: 批量删除多个数据风险

**请求参数**:
- Content-Type: `application/json`
- Body: List\<String\> - ID列表

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/risk/data \
  -H "Content-Type: application/json" \
  -d '["data-001", "data-002"]'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "添加成功",
  "data": "添加成功"
}
```

---

### 4️⃣ 更新数据风险

**接口地址**: `PUT /api/v1/risk/data/{id}`

**功能描述**: 根据ID更新数据风险信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 数据风险ID |

**请求参数**:
- Content-Type: `application/json`
- Body: DataRiskDto

**请求示例**:
```bash
curl -X PUT http://localhost:8080/api/v1/risk/data/data-001 \
  -H "Content-Type: application/json" \
  -d '{
    "severity": "CRITICAL"
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "添加成功",
  "data": "添加成功"
}
```

---

### 5️⃣ 查询数据风险详情

**接口地址**: `GET /api/v1/risk/data/{id}`

**功能描述**: 根据ID查询数据风险详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 数据风险ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/risk/data/data-001
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "data-001",
    "type": "DATA_LEAK",
    "severity": "HIGH",
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 6️⃣ 查询数据风险列表

**接口地址**: `GET /api/v1/risk/data/list`

**功能描述**: 分页查询数据风险列表

**请求参数**:
- Query: DataRiskSearchDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/risk/data/list?page=1&size=10"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "page": 1,
    "size": 10,
    "total": 100,
    "rows": []
  }
}
```

---

### 7️⃣ 获取标签列表

**接口地址**: `GET /api/v1/risk/data/labels`

**功能描述**: 获取所有不重复的标签

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/risk/data/labels
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "label1", "value": "label1"}
    ]
  }
}
```

---

### 8️⃣ 获取相似ID

**接口地址**: `GET /api/v1/risk/data/ids?term=xxx`

**功能描述**: 根据关键词获取相似ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/risk/data/ids?term=data"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["data-001", "data-002"]
}
```

---

### 9️⃣ 获取相似类型

**接口地址**: `GET /api/v1/risk/data/types?term=xxx`

**功能描述**: 根据关键词获取相似风险类型

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/risk/data/types?term=DATA"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["DATA_LEAK", "DATA_EXPOSURE"]
}
```

---

### 🔟 获取总数统计

**接口地址**: `GET /api/v1/risk/data/statistics/total`

**功能描述**: 获取数据风险总数

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/risk/data/statistics/total
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": 100
}
```

---

### 1️⃣1️⃣ 获取增长统计

**接口地址**: `GET /api/v1/risk/data/statistics/increase`

**功能描述**: 获取数据风险增长数

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/risk/data/statistics/increase
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": 10
}
```

---

### 1️⃣2️⃣ 风险类型自动补全

**接口地址**: `GET /api/v1/risk/data/auto_complete/type?term=xxx`

**功能描述**: 风险类型自动补全（暂未实现，返回空数组）

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/risk/data/auto_complete/type?term=DATA"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": []
}
```

---

## 📊 响应码汇总

| 响应码 | 说明 | 触发场景 |
|--------|------|---------|
| 0 | 请求成功 | 操作成功完成 |
| -1 | 未知错误 | 遇到未定义的异常情况 |

---

## 🔐 注意事项

1. **认证授权**: 需要登录认证
2. **批量删除**: 使用POST请求，Body为ID列表数组
3. **自动补全接口**: 部分auto_complete接口暂未实现，返回空数组