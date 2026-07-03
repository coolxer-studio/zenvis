# AssetService 系统服务资产接口文档

**基础信息**
- **模块名称**: 系统服务资产管理
- **基础路径**: `/api/v1/asset/service`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. AssetServiceDto (系统服务资产传输对象)

```json
{
  "id": "service-001",        // String - 系统服务资产ID（更新时使用）
  "serviceName": "服务名称",    // String - 服务名称
  "version": "1.0.0",         // String - 版本
  "port": 8080,               // Integer - 端口
  "protocol": "HTTP",         // String - 协议
  "status": "running",        // String - 状态
  "description": "描述信息",   // String - 描述信息
  "labels": ["label1"]        // List - 标签列表
}
```

### 2. AssetServiceVo (系统服务资产视图对象)

```json
{
  "id": "service-001",        // String - 系统服务资产ID
  "serviceName": "服务名称",    // String - 服务名称
  "version": "1.0.0",         // String - 版本
  "port": 8080,               // Integer - 端口
  "protocol": "HTTP",         // String - 协议
  "status": "running",        // String - 状态
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
| 1 | POST | `/api/v1/asset/service/add` | 添加系统服务资产 | 添加新的系统服务资产 |
| 2 | DELETE | `/api/v1/asset/service/{id}` | 删除系统服务资产 | 根据ID删除单个系统服务资产 |
| 3 | DELETE | `/api/v1/asset/service/bulk/{ids}` | 批量删除系统服务资产 | 批量删除多个系统服务资产 |
| 4 | POST | `/api/v1/asset/service/{id}/update` | 更新系统服务资产 | 根据ID更新系统服务资产信息 |
| 5 | POST | `/api/v1/asset/service/{ids}/bulk_update` | 批量更新系统服务资产 | 批量更新多个系统服务资产 |
| 6 | GET | `/api/v1/asset/service/list` | 查询系统服务资产列表 | 分页查询系统服务资产列表 |
| 7 | GET | `/api/v1/asset/service/{id}/view` | 查询系统服务资产详情 | 根据ID查询系统服务资产详细信息 |
| 8 | GET | `/api/v1/asset/service/label/list` | 获取标签列表 | 获取所有不重复的标签 |
| 9 | GET | `/api/v1/asset/service/auto_complete/id` | ID自动补全 | 根据关键词获取相似ID |
| 10 | GET | `/api/v1/asset/service/auto_complete/service_name` | 服务名称自动补全 | 根据关键词获取相似服务名称 |
| 11 | GET | `/api/v1/asset/service/auto_complete/version` | 版本自动补全 | 根据关键词获取相似版本 |
| 12 | GET | `/api/v1/asset/service/auto_complete/protocol` | 协议自动补全 | 根据关键词获取相似协议 |
| 13 | GET | `/api/v1/asset/service/auto_complete/status` | 状态自动补全 | 根据关键词获取相似状态 |

---

## 🔌 接口详情

### 1️⃣ 添加系统服务资产

**接口地址**: `POST /api/v1/asset/service/add`

**功能描述**: 添加新的系统服务资产

**请求参数**:
- Content-Type: `application/json`
- Body: AssetServiceDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/service/add \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "Nginx",
    "version": "1.24.0",
    "port": 80,
    "protocol": "HTTP",
    "status": "running"
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

### 2️⃣ 删除系统服务资产

**接口地址**: `DELETE /api/v1/asset/service/{id}`

**功能描述**: 根据ID删除单个系统服务资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 系统服务资产ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/service/service-001
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

### 3️⃣ 批量删除系统服务资产

**接口地址**: `DELETE /api/v1/asset/service/bulk/{ids}`

**功能描述**: 批量删除多个系统服务资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<String\> | 是 | 系统服务资产ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/service/bulk/service-001,service-002
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

### 4️⃣ 更新系统服务资产

**接口地址**: `POST /api/v1/asset/service/{id}/update`

**功能描述**: 根据ID更新系统服务资产信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 系统服务资产ID |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetServiceDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/service/service-001/update \
  -H "Content-Type: application/json" \
  -d '{
    "version": "1.25.0"
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

### 5️⃣ 批量更新系统服务资产

**接口地址**: `POST /api/v1/asset/service/{ids}/bulk_update`

**功能描述**: 批量更新多个系统服务资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | String[] | 是 | 系统服务资产ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetServiceDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/service/service-001,service-002/bulk_update \
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

### 6️⃣ 查询系统服务资产列表

**接口地址**: `GET /api/v1/asset/service/list`

**功能描述**: 分页查询系统服务资产列表

**请求参数**:
- Query: AssetServiceSearchDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/service/list?page=1&size=10"
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

### 7️⃣ 查询系统服务资产详情

**接口地址**: `GET /api/v1/asset/service/{id}/view`

**功能描述**: 根据ID查询系统服务资产详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 系统服务资产ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/service/service-001/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "service-001",
    "serviceName": "Nginx",
    "version": "1.24.0",
    "port": 80,
    "protocol": "HTTP",
    "status": "running",
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 8️⃣ 获取标签列表

**接口地址**: `GET /api/v1/asset/service/label/list`

**功能描述**: 获取所有不重复的标签

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/service/label/list
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

**接口地址**: `GET /api/v1/asset/service/auto_complete/id?term=xxx`

**功能描述**: 根据关键词获取相似系统服务资产ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/service/auto_complete/id?term=service"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "service-001", "value": "service-001"},
      {"label": "service-002", "value": "service-002"}
    ]
  }
}
```

---

### 🔟 服务名称自动补全

**接口地址**: `GET /api/v1/asset/service/auto_complete/service_name?term=xxx`

**功能描述**: 根据关键词获取相似服务名称

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/service/auto_complete/service_name?term=Nginx"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "Nginx", "value": "Nginx"}
    ]
  }
}
```

---

### 1️⃣1️⃣ 版本自动补全

**接口地址**: `GET /api/v1/asset/service/auto_complete/version?term=xxx`

**功能描述**: 根据关键词获取相似版本

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/service/auto_complete/version?term=1.2"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "1.24.0", "value": "1.24.0"},
      {"label": "1.25.0", "value": "1.25.0"}
    ]
  }
}
```

---

### 1️⃣2️⃣ 协议自动补全

**接口地址**: `GET /api/v1/asset/service/auto_complete/protocol?term=xxx`

**功能描述**: 根据关键词获取相似协议

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/service/auto_complete/protocol?term=HT"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "HTTP", "value": "HTTP"},
      {"label": "HTTPS", "value": "HTTPS"}
    ]
  }
}
```

---

### 1️⃣3️⃣ 状态自动补全

**接口地址**: `GET /api/v1/asset/service/auto_complete/status?term=xxx`

**功能描述**: 根据关键词获取相似状态

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/service/auto_complete/status?term=run"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "running", "value": "running"}
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