# AssetProbe 数据探针SDK接口文档

**基础信息**
- **模块名称**: 数据探针SDK管理
- **基础路径**: `/api/v1/asset/probe`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. AssetProbeDto (数据探针传输对象)

```json
{
  "id": "probe-001",          // String - 探针ID（更新时使用）
  "probeName": "探针名称",      // String - 探针名称
  "probeType": "SDK",         // String - 探针类型
  "language": "Java",         // String - 语言
  "framework": "Spring",      // String - 框架
  "encryptionMethod": "AES",  // String - 加密方式
  "authenticationMethod": "TOKEN", // String - 认证方式
  "dataTransmissionProtocol": "HTTP", // String - 数据传输协议
  "description": "描述信息",   // String - 描述信息
  "labels": ["label1"]        // List - 标签列表
}
```

### 2. AssetProbeVo (数据探针视图对象)

```json
{
  "id": "probe-001",          // String - 探针ID
  "probeName": "探针名称",      // String - 探针名称
  "probeType": "SDK",         // String - 探针类型
  "language": "Java",         // String - 语言
  "framework": "Spring",      // String - 框架
  "encryptionMethod": "AES",  // String - 加密方式
  "authenticationMethod": "TOKEN", // String - 认证方式
  "dataTransmissionProtocol": "HTTP", // String - 数据传输协议
  "description": "描述信息",   // String - 描述信息
  "labels": ["label1"],       // List - 标签列表
  "updateTime": "2024-01-01 12:00:00"  // String - 更新时间
}
```

### 3. ResponseWrap (统一响应格式)

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
| 1 | POST | `/api/v1/asset/probe/add` | 添加探针 | 添加新的数据探针 |
| 2 | DELETE | `/api/v1/asset/probe/{id}` | 删除探针 | 根据ID删除单个探针 |
| 3 | DELETE | `/api/v1/asset/probe/bulk/{ids}` | 批量删除探针 | 批量删除多个探针 |
| 4 | POST | `/api/v1/asset/probe/{id}/update` | 更新探针 | 根据ID更新探针信息 |
| 5 | POST | `/api/v1/asset/probe/{ids}/bulk_update` | 批量更新探针 | 批量更新多个探针 |
| 6 | GET | `/api/v1/asset/probe/list` | 查询探针列表 | 分页查询探针列表 |
| 7 | GET | `/api/v1/asset/probe/{id}/view` | 查询探针详情 | 根据ID查询探针详细信息 |
| 8 | GET | `/api/v1/asset/probe/label/list` | 获取标签列表 | 获取所有不重复的标签 |
| 9 | GET | `/api/v1/asset/probe/auto_complete/id` | ID自动补全 | 根据关键词获取相似ID |
| 10 | GET | `/api/v1/asset/probe/auto_complete/probe_name` | 探针名称自动补全 | 根据关键词获取相似探针名称 |
| 11 | GET | `/api/v1/asset/probe/auto_complete/probe_type` | 探针类型自动补全 | 根据关键词获取相似探针类型 |
| 12 | GET | `/api/v1/asset/probe/auto_complete/language` | 语言自动补全 | 根据关键词获取相似语言 |
| 13 | GET | `/api/v1/asset/probe/auto_complete/framework` | 框架自动补全 | 根据关键词获取相似框架 |
| 14 | GET | `/api/v1/asset/probe/auto_complete/encryption_method` | 加密方式自动补全 | 根据关键词获取相似加密方式 |
| 15 | GET | `/api/v1/asset/probe/auto_complete/authentication_method` | 认证方式自动补全 | 根据关键词获取相似认证方式 |
| 16 | GET | `/api/v1/asset/probe/auto_complete/data_transmission_protocol` | 传输协议自动补全 | 根据关键词获取相似传输协议 |

---

## 🔌 接口详情

### 1️⃣ 添加探针

**接口地址**: `POST /api/v1/asset/probe/add`

**功能描述**: 添加新的数据探针

**请求参数**:
- Content-Type: `application/json`
- Body: AssetProbeDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/probe/add \
  -H "Content-Type: application/json" \
  -d '{
    "probeName": "Java探针",
    "probeType": "SDK",
    "language": "Java",
    "framework": "Spring"
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

### 2️⃣ 删除探针

**接口地址**: `DELETE /api/v1/asset/probe/{id}`

**功能描述**: 根据ID删除单个探针

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 探针ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/probe/probe-001
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "删除成功",
  "data": "删除成功"
}
```

---

### 3️⃣ 批量删除探针

**接口地址**: `DELETE /api/v1/asset/probe/bulk/{ids}`

**功能描述**: 批量删除多个探针

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<String\> | 是 | 探针ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/probe/bulk/probe-001,probe-002
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "删除成功",
  "data": "删除成功"
}
```

---

### 4️⃣ 更新探针

**接口地址**: `POST /api/v1/asset/probe/{id}/update`

**功能描述**: 根据ID更新探针信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 探针ID |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetProbeDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/probe/probe-001/update \
  -H "Content-Type: application/json" \
  -d '{
    "probeName": "更新后的探针名称"
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "修改成功",
  "data": "修改成功"
}
```

---

### 5️⃣ 批量更新探针

**接口地址**: `POST /api/v1/asset/probe/{ids}/bulk_update`

**功能描述**: 批量更新多个探针

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | String[] | 是 | 探针ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetProbeDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/probe/probe-001,probe-002/bulk_update \
  -H "Content-Type: application/json" \
  -d '{
    "description": "批量更新描述"
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "修改成功",
  "data": "修改成功"
}
```

---

### 6️⃣ 查询探针列表

**接口地址**: `GET /api/v1/asset/probe/list`

**功能描述**: 分页查询探针列表

**请求参数**:
- Query: AssetProbeSearchDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/probe/list?page=1&size=10"
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

### 7️⃣ 查询探针详情

**接口地址**: `GET /api/v1/asset/probe/{id}/view`

**功能描述**: 根据ID查询探针详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 探针ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/probe/probe-001/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "probe-001",
    "probeName": "Java探针",
    "probeType": "SDK",
    "language": "Java",
    "framework": "Spring",
    "encryptionMethod": "AES",
    "authenticationMethod": "TOKEN",
    "dataTransmissionProtocol": "HTTP",
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 8️⃣ 获取标签列表

**接口地址**: `GET /api/v1/asset/probe/label/list`

**功能描述**: 获取所有不重复的标签

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/probe/label/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "label1", "value": "label1"},
      {"label": "label2", "value": "label2"}
    ]
  }
}
```

---

### 9️⃣ ID自动补全

**接口地址**: `GET /api/v1/asset/probe/auto_complete/id?term=xxx`

**功能描述**: 根据关键词获取相似探针ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/probe/auto_complete/id?term=probe"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "probe-001", "value": "probe-001"},
      {"label": "probe-002", "value": "probe-002"}
    ]
  }
}
```

---

### 🔟 探针名称自动补全

**接口地址**: `GET /api/v1/asset/probe/auto_complete/probe_name?term=xxx`

**功能描述**: 根据关键词获取相似探针名称

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/probe/auto_complete/probe_name?term=Java"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "Java探针", "value": "Java探针"}
    ]
  }
}
```

---

### 1️⃣1️⃣ 探针类型自动补全

**接口地址**: `GET /api/v1/asset/probe/auto_complete/probe_type?term=xxx`

**功能描述**: 根据关键词获取相似探针类型

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/probe/auto_complete/probe_type?term=SDK"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "SDK", "value": "SDK"}
    ]
  }
}
```

---

### 1️⃣2️⃣ 语言自动补全

**接口地址**: `GET /api/v1/asset/probe/auto_complete/language?term=xxx`

**功能描述**: 根据关键词获取相似语言

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/probe/auto_complete/language?term=Ja"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "Java", "value": "Java"}
    ]
  }
}
```

---

### 1️⃣3️⃣ 框架自动补全

**接口地址**: `GET /api/v1/asset/probe/auto_complete/framework?term=xxx`

**功能描述**: 根据关键词获取相似框架

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/probe/auto_complete/framework?term=Spr"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "Spring", "value": "Spring"}
    ]
  }
}
```

---

### 1️⃣4️⃣ 加密方式自动补全

**接口地址**: `GET /api/v1/asset/probe/auto_complete/encryption_method?term=xxx`

**功能描述**: 根据关键词获取相似加密方式

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/probe/auto_complete/encryption_method?term=AE"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "AES", "value": "AES"}
    ]
  }
}
```

---

### 1️⃣5️⃣ 认证方式自动补全

**接口地址**: `GET /api/v1/asset/probe/auto_complete/authentication_method?term=xxx`

**功能描述**: 根据关键词获取相似认证方式

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/probe/auto_complete/authentication_method?term=TOK"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "TOKEN", "value": "TOKEN"}
    ]
  }
}
```

---

### 1️⃣6️⃣ 传输协议自动补全

**接口地址**: `GET /api/v1/asset/probe/auto_complete/data_transmission_protocol?term=xxx`

**功能描述**: 根据关键词获取相似传输协议

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/probe/auto_complete/data_transmission_protocol?term=HT"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "HTTP", "value": "HTTP"}
    ]
  }
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
2. **批量操作**: 批量删除/更新时，ID列表不能为空
3. **自动补全**: term参数为可选，不传则返回全部匹配项