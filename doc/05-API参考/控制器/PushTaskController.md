# PushTask 推送任务接口文档

**基础信息**
- **模块名称**: 推送任务
- **基础路径**: `/api/v1/system/push-task`
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. PushTaskDto (推送任务传输对象)

```json
{
  "id": 1,                    // Integer - 任务ID（更新时使用，创建时不传）
  "name": "推送任务名称",       // String - 任务名称（必填）
  "description": "任务描述",    // String - 描述信息（可选）
  "config": "{}",             // String - 配置内容（必填）
  "source": "SYSTEM"          // String - 来源（可选）
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| id | Integer | 否 | 任务ID，更新时需传入 |
| name | String | 是 | 任务名称 |
| description | String | 否 | 描述信息 |
| config | String | 是 | 配置内容，支持JSON格式 |
| source | String | 否 | 来源标识 |

### 2. PushTaskVo (推送任务视图对象)

```json
{
  "id": 1,                    // Integer - 任务ID
  "name": "推送任务名称",       // String - 任务名称
  "description": "任务描述",    // String - 描述信息
  "source": "SYSTEM",         // String - 来源
  "config": "{}",             // String - 配置内容
  "status": "created",        // String - 任务状态(created/running/error/stopped)
  "pid": 12345,               // Integer - 进程ID（运行时为实际PID，停止时为0）
  "updateTime": "2024-01-01 12:00:00"  // String - 更新时间
}
```

**状态说明**:
| 状态值 | 说明 |
|-------|------|
| created | 任务已创建，未启动 |
| running | 任务正常运行中 |
| error | 任务错误，无法运行 |
| stopped | 任务已停止 |

### 3. PushTaskSearchDto (推送任务搜索传输对象)

```json
{
  "name": "推送",             // String - 任务名称（可选）
  "page": 1,                 // Integer - 页码（继承自SortPageDto）
  "size": 10,                // Integer - 每页大小（继承自SortPageDto）
  "sort": "updateTime",      // String - 排序字段（继承自SortPageDto）
  "order": "desc"            // String - 排序方式（继承自SortPageDto）
}
```

### 4. ResponseWrap (统一响应格式)

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
| 1 | POST | `/api/v1/system/push-task/add` | 创建任务 | 创建新的推送任务 |
| 2 | DELETE | `/api/v1/system/push-task/{id}` | 删除任务 | 根据ID删除单个任务 |
| 3 | DELETE | `/api/v1/system/push-task/bulk/{ids}` | 批量删除任务 | 批量删除多个任务 |
| 4 | POST | `/api/v1/system/push-task/{id}/update` | 更新任务 | 根据ID更新任务信息 |
| 5 | POST | `/api/v1/system/push-task/{ids}/bulk-update` | 批量更新任务 | 批量更新多个任务 |
| 6 | GET | `/api/v1/system/push-task/list` | 查询任务列表 | 分页查询任务列表 |
| 7 | GET | `/api/v1/system/push-task/{id}/view` | 查询任务详情 | 根据ID查询任务详细信息 |
| 8 | POST | `/api/v1/system/push-task/{id}/toggle` | 启动/停止任务 | 切换任务的运行状态 |
| 9 | GET | `/api/v1/system/push-task/{id}/log` | 获取任务日志 | 根据任务ID和日志类型获取日志内容 |

---

## 🔌 接口详情

### 1️⃣ 创建任务

**接口地址**: `POST /api/v1/system/push-task/add`

**功能描述**: 创建新的推送任务

**请求参数**:
- Content-Type: `application/json`
- Body: PushTaskDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/push-task/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试推送任务",
    "description": "这是一个测试推送任务",
    "config": "{\"target\":\"http://example.com/webhook\",\"method\":\"POST\"}",
    "source": "API"
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": "创建成功"
}
```

---

### 2️⃣ 删除任务

**接口地址**: `DELETE /api/v1/system/push-task/{id}`

**功能描述**: 根据ID删除单个推送任务

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:11002/api/v1/system/push-task/1
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": "删除成功"
}
```

---

### 3️⃣ 批量删除任务

**接口地址**: `DELETE /api/v1/system/push-task/bulk/{ids}`

**功能描述**: 批量删除多个推送任务

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<Long\> | 是 | 任务ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:11002/api/v1/system/push-task/bulk/1,2,3
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": "删除成功"
}
```

---

### 4️⃣ 更新任务

**接口地址**: `POST /api/v1/system/push-task/{id}/update`

**功能描述**: 根据ID更新推送任务信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求参数**:
- Content-Type: `application/json`
- Body: PushTaskDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/push-task/1/update \
  -H "Content-Type: application/json" \
  -d '{
    "name": "更新后的任务名称",
    "description": "更新后的描述"
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": "修改成功"
}
```

---

### 5️⃣ 批量更新任务

**接口地址**: `POST /api/v1/system/push-task/{ids}/bulk-update`

**功能描述**: 批量更新多个推送任务

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | Long[] | 是 | 任务ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: PushTaskDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/push-task/1,2,3/bulk-update \
  -H "Content-Type: application/json" \
  -d '{
    "description": "批量更新的描述"
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": "修改成功"
}
```

---

### 6️⃣ 查询任务列表

**接口地址**: `GET /api/v1/system/push-task/list`

**功能描述**: 分页查询推送任务列表

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| name | String | 否 | 任务名称（模糊查询） |
| page | Integer | 否 | 页码，默认1 |
| size | Integer | 否 | 每页大小，默认10 |
| sort | String | 否 | 排序字段 |
| order | String | 否 | 排序方式 |

**请求示例**:
```bash
curl -X GET "http://localhost:11002/api/v1/system/push-task/list?name=测试&page=1&size=10"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "total": 10,
    "page": 1,
    "size": 10,
    "rows": [
      {
        "id": 1,
        "name": "测试推送任务",
        "description": "这是一个测试推送任务",
        "source": "API",
        "config": "{\"target\":\"http://example.com/webhook\",\"method\":\"POST\"}",
        "status": "running",
        "pid": 12345,
        "updateTime": "2024-01-01 12:00:00"
      }
    ]
  }
}
```

---

### 7️⃣ 查询任务详情

**接口地址**: `GET /api/v1/system/push-task/{id}/view`

**功能描述**: 根据ID查询推送任务详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求示例**:
```bash
curl -X GET http://localhost:11002/api/v1/system/push-task/1/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "name": "测试推送任务",
    "description": "这是一个测试推送任务",
    "source": "API",
    "config": "{\"target\":\"http://example.com/webhook\",\"method\":\"POST\"}",
    "status": "running",
    "pid": 12345,
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 8️⃣ 启动/停止任务

**接口地址**: `POST /api/v1/system/push-task/{id}/toggle`

**功能描述**: 切换任务的运行状态
- 如果任务当前是停止状态，则启动任务
- 如果任务当前是运行状态，则停止任务

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/push-task/1/toggle
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": null
}
```

---

### 9️⃣ 获取任务日志

**接口地址**: `GET /api/v1/system/push-task/{id}/log?log_type=console`

**功能描述**: 根据任务ID和日志类型获取日志内容

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| log_type | String | 是 | 日志类型(console/system) |

**请求示例**:
```bash
curl -X GET "http://localhost:11002/api/v1/system/push-task/1/log?log_type=console"
```

**成功响应**: 返回纯文本日志内容

---

## 📊 响应码汇总

| 响应码 | 说明 | 触发场景 |
|--------|------|---------|
| 0 | 请求成功 | 操作成功完成 |
| -1 | 未知错误 | 遇到未定义的异常情况 |

---

## 🔐 注意事项

1. **认证授权**: 需要登录认证
2. **任务启停**: toggle操作会立即生效，请谨慎操作
3. **日志查询**: 日志接口返回纯文本格式，不是JSON格式
4. **批量操作**: 批量删除/更新时，ID列表不能为空
5. **进程管理**: 删除任务时会自动停止对应的进程

---