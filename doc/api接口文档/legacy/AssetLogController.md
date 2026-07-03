# AssetLog 日志资产接口文档

**基础信息**
- **模块名称**: 日志资产管理
- **基础路径**: `/api/v1/asset/log`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. AssetLogDto (日志资产传输对象)

```json
{
  "id": "log-001",            // String - 日志资产ID（更新时使用）
  "logName": "日志名称",       // String - 日志名称
  "logPath": "/var/log/app.log", // String - 日志路径
  "logType": "application",   // String - 日志类型
  "logLevel": "INFO",         // String - 日志级别
  "description": "描述信息",   // String - 描述信息
  "labels": ["label1"]        // List - 标签列表
}
```

### 2. AssetLogVo (日志资产视图对象)

```json
{
  "id": "log-001",            // String - 日志资产ID
  "logName": "日志名称",       // String - 日志名称
  "logPath": "/var/log/app.log", // String - 日志路径
  "logType": "application",   // String - 日志类型
  "logLevel": "INFO",         // String - 日志级别
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
| 1 | POST | `/api/v1/asset/log/add` | 添加日志资产 | 添加新的日志资产 |
| 2 | DELETE | `/api/v1/asset/log/{id}` | 删除日志资产 | 根据ID删除单个日志资产 |
| 3 | DELETE | `/api/v1/asset/log/bulk/{ids}` | 批量删除日志资产 | 批量删除多个日志资产 |
| 4 | POST | `/api/v1/asset/log/{id}/update` | 更新日志资产 | 根据ID更新日志资产信息 |
| 5 | POST | `/api/v1/asset/log/{ids}/bulk_update` | 批量更新日志资产 | 批量更新多个日志资产 |
| 6 | GET | `/api/v1/asset/log/list` | 查询日志资产列表 | 分页查询日志资产列表 |
| 7 | GET | `/api/v1/asset/log/{id}/view` | 查询日志资产详情 | 根据ID查询日志资产详细信息 |
| 8 | GET | `/api/v1/asset/log/label/list` | 获取标签列表 | 获取所有不重复的标签 |
| 9 | GET | `/api/v1/asset/log/auto_complete/id` | ID自动补全 | 根据关键词获取相似ID |
| 10 | GET | `/api/v1/asset/log/auto_complete/log_name` | 日志名称自动补全 | 根据关键词获取相似日志名称 |
| 11 | GET | `/api/v1/asset/log/auto_complete/log_path` | 日志路径自动补全 | 根据关键词获取相似日志路径 |
| 12 | GET | `/api/v1/asset/log/auto_complete/log_type` | 日志类型自动补全 | 根据关键词获取相似日志类型 |
| 13 | GET | `/api/v1/asset/log/auto_complete/log_level` | 日志级别自动补全 | 根据关键词获取相似日志级别 |

---

## 🔌 接口详情

### 1️⃣ 添加日志资产

**接口地址**: `POST /api/v1/asset/log/add`

**功能描述**: 添加新的日志资产

**请求参数**:
- Content-Type: `application/json`
- Body: AssetLogDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/log/add \
  -H "Content-Type: application/json" \
  -d '{
    "logName": "应用日志",
    "logPath": "/var/log/app.log",
    "logType": "application",
    "logLevel": "INFO"
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

### 2️⃣ 删除日志资产

**接口地址**: `DELETE /api/v1/asset/log/{id}`

**功能描述**: 根据ID删除单个日志资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 日志资产ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/log/log-001
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

### 3️⃣ 批量删除日志资产

**接口地址**: `DELETE /api/v1/asset/log/bulk/{ids}`

**功能描述**: 批量删除多个日志资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<String\> | 是 | 日志资产ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/log/bulk/log-001,log-002
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

### 4️⃣ 更新日志资产

**接口地址**: `POST /api/v1/asset/log/{id}/update`

**功能描述**: 根据ID更新日志资产信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 日志资产ID |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetLogDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/log/log-001/update \
  -H "Content-Type: application/json" \
  -d '{
    "logLevel": "WARN"
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

### 5️⃣ 批量更新日志资产

**接口地址**: `POST /api/v1/asset/log/{ids}/bulk_update`

**功能描述**: 批量更新多个日志资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | String[] | 是 | 日志资产ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetLogDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/log/log-001,log-002/bulk_update \
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

### 6️⃣ 查询日志资产列表

**接口地址**: `GET /api/v1/asset/log/list`

**功能描述**: 分页查询日志资产列表

**请求参数**:
- Query: AssetLogSearchDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/log/list?page=1&size=10"
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

### 7️⃣ 查询日志资产详情

**接口地址**: `GET /api/v1/asset/log/{id}/view`

**功能描述**: 根据ID查询日志资产详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 日志资产ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/log/log-001/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "log-001",
    "logName": "应用日志",
    "logPath": "/var/log/app.log",
    "logType": "application",
    "logLevel": "INFO",
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 8️⃣ 获取标签列表

**接口地址**: `GET /api/v1/asset/log/label/list`

**功能描述**: 获取所有不重复的标签

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/log/label/list
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

**接口地址**: `GET /api/v1/asset/log/auto_complete/id?term=xxx`

**功能描述**: 根据关键词获取相似日志资产ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/log/auto_complete/id?term=log"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "log-001", "value": "log-001"},
      {"label": "log-002", "value": "log-002"}
    ]
  }
}
```

---

### 🔟 日志名称自动补全

**接口地址**: `GET /api/v1/asset/log/auto_complete/log_name?term=xxx`

**功能描述**: 根据关键词获取相似日志名称

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/log/auto_complete/log_name?term=应用"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "应用日志", "value": "应用日志"}
    ]
  }
}
```

---

### 1️⃣1️⃣ 日志路径自动补全

**接口地址**: `GET /api/v1/asset/log/auto_complete/log_path?term=xxx`

**功能描述**: 根据关键词获取相似日志路径

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/log/auto_complete/log_path?term=/var/log"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "/var/log/app.log", "value": "/var/log/app.log"}
    ]
  }
}
```

---

### 1️⃣2️⃣ 日志类型自动补全

**接口地址**: `GET /api/v1/asset/log/auto_complete/log_type?term=xxx`

**功能描述**: 根据关键词获取相似日志类型

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/log/auto_complete/log_type?term=app"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "application", "value": "application"}
    ]
  }
}
```

---

### 1️⃣3️⃣ 日志级别自动补全

**接口地址**: `GET /api/v1/asset/log/auto_complete/log_level?term=xxx`

**功能描述**: 根据关键词获取相似日志级别

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/log/auto_complete/log_level?term=INF"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "INFO", "value": "INFO"}
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