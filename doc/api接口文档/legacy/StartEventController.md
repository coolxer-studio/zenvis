# StartEventController 启动事件接口文档

**基础信息**
- **模块名称**: 启动事件管理
- **基础路径**: `/api/v1/operation/start`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. StartEventDto (启动事件传输对象)

```json
{
  "id": "start-001",          // String - 启动事件ID（更新时使用）
  "appName": "MyApp",         // String - 应用名称
  "packageName": "com.example.app", // String - 包名
  "deviceModel": "iPhone 15", // String - 设备型号
  "deviceOs": "iOS 17",       // String - 设备操作系统
  "launchTime": 1000,         // Long - 启动时间(ms)
  "labels": ["label1"]        // List - 标签列表
}
```

### 2. StartEventVo (启动事件视图对象)

```json
{
  "id": "start-001",          // String - 启动事件ID
  "appName": "MyApp",         // String - 应用名称
  "packageName": "com.example.app", // String - 包名
  "deviceModel": "iPhone 15", // String - 设备型号
  "deviceOs": "iOS 17",       // String - 设备操作系统
  "launchTime": 1000,         // Long - 启动时间(ms)
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
| 1 | POST | `/api/v1/operation/start/add` | 添加启动事件 | 添加新的启动事件记录 |
| 2 | DELETE | `/api/v1/operation/start/{id}` | 删除启动事件 | 根据ID删除单个启动事件 |
| 3 | DELETE | `/api/v1/operation/start/bulk/{ids}` | 批量删除启动事件 | 批量删除多个启动事件 |
| 4 | POST | `/api/v1/operation/start/{id}/update` | 更新启动事件 | 根据ID更新启动事件信息 |
| 5 | GET | `/api/v1/operation/start/list` | 查询启动事件列表 | 分页查询启动事件列表 |
| 6 | GET | `/api/v1/operation/start/{id}/view` | 查询启动事件详情 | 根据ID查询启动事件详细信息 |
| 7 | GET | `/api/v1/operation/start/auto_complete/id` | ID自动补全 | 根据关键词获取相似ID |
| 8 | GET | `/api/v1/operation/start/auto_complete/app_name` | 应用名称自动补全 | 根据关键词获取相似应用名称 |
| 9 | GET | `/api/v1/operation/start/auto_complete/package_name` | 包名自动补全 | 根据关键词获取相似包名 |
| 10 | GET | `/api/v1/operation/start/auto_complete/device_model` | 设备型号自动补全 | 根据关键词获取相似设备型号 |
| 11 | GET | `/api/v1/operation/start/auto_complete/device_os` | 设备操作系统自动补全 | 根据关键词获取相似设备操作系统 |

---

## 🔌 接口详情

### 1️⃣ 添加启动事件

**接口地址**: `POST /api/v1/operation/start/add`

**功能描述**: 添加新的启动事件记录

**请求参数**:
- Content-Type: `application/json`
- Body: StartEventDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/operation/start/add \
  -H "Content-Type: application/json" \
  -d '{
    "appName": "MyApp",
    "packageName": "com.example.app",
    "deviceModel": "iPhone 15",
    "deviceOs": "iOS 17",
    "launchTime": 1000
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

### 2️⃣ 删除启动事件

**接口地址**: `DELETE /api/v1/operation/start/{id}`

**功能描述**: 根据ID删除单个启动事件

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 启动事件ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/operation/start/start-001
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

### 3️⃣ 批量删除启动事件

**接口地址**: `DELETE /api/v1/operation/start/bulk/{ids}`

**功能描述**: 批量删除多个启动事件

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<String\> | 是 | 启动事件ID列表 |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/operation/start/bulk/start-001,start-002
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

### 4️⃣ 更新启动事件

**接口地址**: `POST /api/v1/operation/start/{id}/update`

**功能描述**: 根据ID更新启动事件信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 启动事件ID |

**请求参数**:
- Content-Type: `application/json`
- Body: StartEventDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/operation/start/start-001/update \
  -H "Content-Type: application/json" \
  -d '{
    "launchTime": 1200
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

### 5️⃣ 查询启动事件列表

**接口地址**: `GET /api/v1/operation/start/list`

**功能描述**: 分页查询启动事件列表

**请求参数**:
- Query: StartEventDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/operation/start/list?page=1&size=10"
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

### 6️⃣ 查询启动事件详情

**接口地址**: `GET /api/v1/operation/start/{id}/view`

**功能描述**: 根据ID查询启动事件详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 启动事件ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/operation/start/start-001/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "start-001",
    "appName": "MyApp",
    "packageName": "com.example.app",
    "deviceModel": "iPhone 15",
    "deviceOs": "iOS 17",
    "launchTime": 1000,
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

**接口地址**: `GET /api/v1/operation/start/auto_complete/id?term=xxx`

**功能描述**: 根据关键词获取相似ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/operation/start/auto_complete/id?term=start"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["start-001", "start-002"]
}
```

---

### 8️⃣ 应用名称自动补全

**接口地址**: `GET /api/v1/operation/start/auto_complete/app_name?term=xxx`

**功能描述**: 根据关键词获取相似应用名称

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/operation/start/auto_complete/app_name?term=My"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["MyApp", "MyAppPro"]
}
```

---

### 9️⃣ 包名自动补全

**接口地址**: `GET /api/v1/operation/start/auto_complete/package_name?term=xxx`

**功能描述**: 根据关键词获取相似包名

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/operation/start/auto_complete/package_name?term=com.example"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["com.example.app", "com.example.service"]
}
```

---

### 🔟 设备型号自动补全

**接口地址**: `GET /api/v1/operation/start/auto_complete/device_model?term=xxx`

**功能描述**: 根据关键词获取相似设备型号

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/operation/start/auto_complete/device_model?term=iPhone"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["iPhone 15", "iPhone 14"]
}
```

---

### 1️⃣1️⃣ 设备操作系统自动补全

**接口地址**: `GET /api/v1/operation/start/auto_complete/device_os?term=xxx`

**功能描述**: 根据关键词获取相似设备操作系统

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/operation/start/auto_complete/device_os?term=iOS"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": ["iOS 17", "iOS 16"]
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