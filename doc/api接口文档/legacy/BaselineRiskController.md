# BaselineRiskController 基线风险接口文档

**基础信息**
- **模块名称**: 基线风险管理
- **基础路径**: `/api/v1/risk/baseline`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. BaselineRiskDto (基线风险传输对象)

```json
{
  "id": "baseline-001",       // String - 基线风险ID（更新时使用）
  "configurationName": "SSH配置", // String - 配置名称
  "expectedValue": "22",      // String - 期望值
  "currentValue": "22",       // String - 当前值
  "severity": "LOW",          // String - 严重程度
  "description": "描述信息",   // String - 描述信息
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
| 1 | POST | `/api/v1/risk/baseline` | 添加基线风险 | 添加新的基线风险记录 |
| 2 | DELETE | `/api/v1/risk/baseline/{id}` | 删除基线风险 | 根据ID删除单个基线风险 |
| 3 | DELETE | `/api/v1/risk/baseline` | 批量删除基线风险 | 批量删除多个基线风险 |
| 4 | PUT | `/api/v1/risk/baseline/{id}` | 更新基线风险 | 根据ID更新基线风险信息 |
| 5 | GET | `/api/v1/risk/baseline/{id}` | 查询基线风险详情 | 根据ID查询基线风险详细信息 |
| 6 | GET | `/api/v1/risk/baseline/list` | 查询基线风险列表 | 分页查询基线风险列表 |
| 7 | GET | `/api/v1/risk/baseline/labels` | 获取标签列表 | 获取所有不重复的标签 |
| 8 | GET | `/api/v1/risk/baseline/ids` | 获取相似ID | 根据关键词获取相似ID |
| 9 | GET | `/api/v1/risk/baseline/names` | 获取相似配置名称 | 根据关键词获取相似配置名称 |
| 10 | GET | `/api/v1/risk/baseline/statistics/total` | 获取总数统计 | 获取基线风险总数 |
| 11 | GET | `/api/v1/risk/baseline/statistics/increase` | 获取增长统计 | 获取基线风险增长数 |
| 12 | GET | `/api/v1/risk/baseline/auto_complete/configuration_name` | 配置名称自动补全 | 配置名称自动补全（暂未实现） |
| 13 | GET | `/api/v1/risk/baseline/auto_complete/expected_value` | 期望值自动补全 | 期望值自动补全（暂未实现） |
| 14 | GET | `/api/v1/risk/baseline/auto_complete/current_value` | 当前值自动补全 | 当前值自动补全（暂未实现） |

---

## 🔌 接口详情

### 1️⃣ 添加基线风险

**接口地址**: `POST /api/v1/risk/baseline`

**功能描述**: 添加新的基线风险记录

**请求参数**:
- Content-Type: `application/json`
- Body: BaselineRiskDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/risk/baseline \
  -H "Content-Type: application/json" \
  -d '{
    "configurationName": "SSH配置",
    "expectedValue": "22",
    "currentValue": "22",
    "severity": "LOW"
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

### 2️⃣ 删除基线风险

**接口地址**: `DELETE /api/v1/risk/baseline/{id}`

**功能描述**: 根据ID删除单个基线风险

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 基线风险ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/risk/baseline/baseline-001
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

### 3️⃣ 批量删除基线风险

**接口地址**: `DELETE /api/v1/risk/baseline`

**功能描述**: 批量删除多个基线风险

**请求参数**:
- Content-Type: `application/json`
- Body: List\<String\> - ID列表

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/risk/baseline \
  -H "Content-Type: application/json" \
  -d '["baseline-001", "baseline-002"]'
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

### 4️⃣ 更新基线风险

**接口地址**: `PUT /api/v1/risk/baseline/{id}`

**功能描述**: 根据ID更新基线风险信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 基线风险ID |

**请求参数**:
- Content-Type: `application/json`
- Body: BaselineRiskDto

**请求示例**:
```bash
curl -X PUT http://localhost:8080/api/v1/risk/baseline/baseline-001 \
  -H "Content-Type: application/json" \
  -d '{
    "severity": "MEDIUM"
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

### 5️⃣ 查询基线风险详情

**接口地址**: `GET /api/v1/risk/baseline/{id}`

**功能描述**: 根据ID查询基线风险详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 基线风险ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/risk/baseline/baseline-001
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "baseline-001",
    "configurationName": "SSH配置",
    "expectedValue": "22",
    "currentValue": "22",
    "severity": "LOW",
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 6️⃣ 查询基线风险列表

**接口地址**: `GET /api/v1/risk/baseline/list`

**功能描述**: 分页查询基线风险列表

**请求参数**:
- Query: BaselineRiskSearchDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/risk/baseline/list?page=1&size=10"
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

**接口地址**: `GET /api/v1/risk/baseline/labels`

**功能描述**: 获取所有不重复的标签

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/risk/baseline/labels
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

**接口地址**: `GET /api/v1/risk/baseline/ids?term=xxx`

**功能描述**: 根据关键词获取相似ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/risk/baseline/ids?term=baseline"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["baseline-001", "baseline-002"]
}
```

---

### 9️⃣ 获取相似配置名称

**接口地址**: `GET /api/v1/risk/baseline/names?term=xxx`

**功能描述**: 根据关键词获取相似配置名称

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/risk/baseline/names?term=SSH"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["SSH配置", "SSH端口"]
}
```

---

### 🔟 获取总数统计

**接口地址**: `GET /api/v1/risk/baseline/statistics/total`

**功能描述**: 获取基线风险总数

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/risk/baseline/statistics/total
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

**接口地址**: `GET /api/v1/risk/baseline/statistics/increase`

**功能描述**: 获取基线风险增长数

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/risk/baseline/statistics/increase
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

### 1️⃣2️⃣ 配置名称自动补全

**接口地址**: `GET /api/v1/risk/baseline/auto_complete/configuration_name?term=xxx`

**功能描述**: 配置名称自动补全（暂未实现，返回空数组）

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/risk/baseline/auto_complete/configuration_name?term=SSH"
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

### 1️⃣3️⃣ 期望值自动补全

**接口地址**: `GET /api/v1/risk/baseline/auto_complete/expected_value?term=xxx`

**功能描述**: 期望值自动补全（暂未实现，返回空数组）

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/risk/baseline/auto_complete/expected_value?term=22"
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

### 1️⃣4️⃣ 当前值自动补全

**接口地址**: `GET /api/v1/risk/baseline/auto_complete/current_value?term=xxx`

**功能描述**: 当前值自动补全（暂未实现，返回空数组）

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/risk/baseline/auto_complete/current_value?term=22"
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