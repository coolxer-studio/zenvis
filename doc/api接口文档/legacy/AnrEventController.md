# AnrEventController ANR事件接口文档

**基础信息**
- **模块名称**: ANR事件管理
- **基础路径**: `/api/v1/operation/anr`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. AnrEventDto (ANR事件传输对象)

```json
{
  "id": "anr-001",            // String - ANR事件ID（更新时使用）
  "pagePath": "/home",        // String - 页面路径
  "pageName": "首页",          // String - 页面名称
  "duration": 5000,           // Long - ANR持续时间(ms)
  "processName": "com.example.app", // String - 进程名称
  "labels": ["label1"]        // List - 标签列表
}
```

### 2. AnrEventVo (ANR事件视图对象)

```json
{
  "id": "anr-001",            // String - ANR事件ID
  "pagePath": "/home",        // String - 页面路径
  "pageName": "首页",          // String - 页面名称
  "duration": 5000,           // Long - ANR持续时间(ms)
  "processName": "com.example.app", // String - 进程名称
  "updateTime": "2024-01-01 12:00:00" // String - 更新时间
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
| 1 | POST | `/api/v1/operation/anr/add` | 添加ANR事件 | 添加新的ANR事件记录 |
| 2 | DELETE | `/api/v1/operation/anr/{id}` | 删除ANR事件 | 根据ID删除单个ANR事件 |
| 3 | DELETE | `/api/v1/operation/anr/bulk/{ids}` | 批量删除ANR事件 | 批量删除多个ANR事件 |
| 4 | POST | `/api/v1/operation/anr/{id}/update` | 更新ANR事件 | 根据ID更新ANR事件信息 |
| 5 | GET | `/api/v1/operation/anr/list` | 查询ANR事件列表 | 分页查询ANR事件列表 |
| 6 | GET | `/api/v1/operation/anr/{id}/view` | 查询ANR事件详情 | 根据ID查询ANR事件详细信息 |
| 7 | GET | `/api/v1/operation/anr/auto_complete/id` | ID自动补全 | 根据关键词获取相似ID |
| 8 | GET | `/api/v1/operation/anr/auto_complete/page_path` | 页面路径自动补全 | 根据关键词获取相似页面路径 |
| 9 | GET | `/api/v1/operation/anr/auto_complete/page_name` | 页面名称自动补全 | 根据关键词获取相似页面名称 |

---

## 🔌 接口详情

### 1️⃣ 添加ANR事件

**接口地址**: `POST /api/v1/operation/anr/add`

**功能描述**: 添加新的ANR事件记录

**请求参数**:
- Content-Type: `application/json`
- Body: AnrEventDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/operation/anr/add \
  -H "Content-Type: application/json" \
  -d '{
    "pagePath": "/home",
    "pageName": "首页",
    "duration": 5000
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

### 2️⃣ 删除ANR事件

**接口地址**: `DELETE /api/v1/operation/anr/{id}`

**功能描述**: 根据ID删除单个ANR事件

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | ANR事件ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/operation/anr/anr-001
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

### 3️⃣ 批量删除ANR事件

**接口地址**: `DELETE /api/v1/operation/anr/bulk/{ids}`

**功能描述**: 批量删除多个ANR事件

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<String\> | 是 | ANR事件ID列表 |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/operation/anr/bulk/anr-001,anr-002
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

### 4️⃣ 更新ANR事件

**接口地址**: `POST /api/v1/operation/anr/{id}/update`

**功能描述**: 根据ID更新ANR事件信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | ANR事件ID |

**请求参数**:
- Content-Type: `application/json`
- Body: AnrEventDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/operation/anr/anr-001/update \
  -H "Content-Type: application/json" \
  -d '{
    "duration": 6000
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

### 5️⃣ 查询ANR事件列表

**接口地址**: `GET /api/v1/operation/anr/list`

**功能描述**: 分页查询ANR事件列表

**请求参数**:
- Query: AnrEventDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/operation/anr/list?page=1&size=10"
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

### 6️⃣ 查询ANR事件详情

**接口地址**: `GET /api/v1/operation/anr/{id}/view`

**功能描述**: 根据ID查询ANR事件详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | ANR事件ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/operation/anr/anr-001/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "anr-001",
    "pagePath": "/home",
    "pageName": "首页",
    "duration": 5000,
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

**失败响应**:
```json
{
  "status": -1,
  "msg": "fail",
  "data": null
}
```

---

### 7️⃣ ID自动补全

**接口地址**: `GET /api/v1/operation/anr/auto_complete/id?term=xxx`

**功能描述**: 根据关键词获取相似ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/operation/anr/auto_complete/id?term=anr"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["anr-001", "anr-002"]
}
```

---

### 8️⃣ 页面路径自动补全

**接口地址**: `GET /api/v1/operation/anr/auto_complete/page_path?term=xxx`

**功能描述**: 根据关键词获取相似页面路径

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/operation/anr/auto_complete/page_path?term=/home"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["/home", "/home/detail"]
}
```

---

### 9️⃣ 页面名称自动补全

**接口地址**: `GET /api/v1/operation/anr/auto_complete/page_name?term=xxx`

**功能描述**: 根据关键词获取相似页面名称

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/operation/anr/auto_complete/page_name?term=首"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["首页", "首页列表"]
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
2. **批量删除**: 使用DELETE请求，ID通过路径参数传递，多个ID用逗号分隔
3. **更新接口**: 使用POST请求而非PUT