# Task 任务接口文档

**基础信息**
- **模块名称**: 任务管理
- **基础路径**: `/vectum/api/v1/task`
- **作者**: cipherzen 
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. TaskDto (任务传输对象)

```json
{
  "id": 1,                    // Integer - 任务ID（更新时使用，创建时不传）
  "name": "任务名称",          // String - 任务名（必填）
  "description": "任务描述",   // String - 描述信息（可选）
  "config": "{}",             // String - 配置内容（必填，支持YAML/TOML/JSON格式）
  "source": "SYSTEM",         // String - 来源（可选，枚举值：SYSTEM/MCP/API/WEB）
  "mark": "备注信息",          // String - 备注（可选）
  "updateTime": "2024-01-01 12:00:00"  // String - 更新时间（内部字段）
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| id | Long | 否 | 任务ID，更新时需传入 |
| name | String | 是 | 任务名称，不能为空 |
| description | String | 否 | 任务描述 |
| config | String | 是 | Vector配置内容，支持YAML/TOML/JSON格式 |
| source | String | 否 | 任务来源标识（枚举值：SYSTEM/MCP/API/WEB） |
| mark | String | 否 | 备注信息 |
| updateTime | Date | 否 | 更新时间（内部字段） |

### 2. TaskVo (任务视图对象)

```json
{
  "id": 1,                         // Long - 任务ID
  "name": "任务名称",               // String - 任务名
  "description": "任务描述",        // String - 描述信息
  "source": "SYSTEM",               // String - 来源（枚举值：SYSTEM/MCP/API/WEB）
  "mark": "备注信息",               // String - 备注
  "status": "created",             // String - 状态(created/running/running[error]/error/stopped)
  "pid": 12345,                    // Integer - 进程ID（运行时为实际PID，停止时为0）
  "createTime": "2024-01-01 12:00:00",  // String - 创建时间
  "updateTime": "2024-01-01 12:00:00"   // String - 更新时间
}
```

**状态说明**:
| 状态值 | 说明 |
|-------|------|
| created | 任务已创建，未启动 |
| running | 任务正常运行中 |
| running[error] | 任务运行中但存在错误 |
| error | 任务错误，无法运行 |
| stopped | 任务已停止 |

### 3. ResponseWrap (统一响应格式)

```json
{
  "status": 0,                // Integer - 响应码(0:成功，其他:失败)
  "msg": "success",           // String - 响应消息
  "data": {}                  // Object - 响应数据
}
```

**响应码说明**:
| 响应码 | 说明 |
|-------|------|
| 0 | 请求成功 |
| -1 | 未知错误 |
| 101 | 请求失败 |
| 102 | 任务名称不能为空 |
| 103 | 任务配置不能为空 |
| 104 | 任务不存在 |
| 105 | 任务操作失败 |

> **注意**: 校验失败（如名称为空）时，返回对应的业务错误码（102/103）

---

## 📊 接口总览

| 序号 | HTTP方法 | 接口路径 | 接口名称 | 功能描述 |
|:---:|:-------:|---------|---------|---------|
| 1 | POST | `/vectum/api/v1/task/add` | 创建任务 | 创建新的任务 |
| 2 | DELETE | `/vectum/api/v1/task/{id}` | 删除任务 | 根据ID删除单个任务 |
| 3 | DELETE | `/vectum/api/v1/task/batch` | 批量删除任务 | 批量删除多个任务 |
| 4 | PUT | `/vectum/api/v1/task/{id}` | 更新任务 | 根据ID更新任务信息 |
| 5 | PUT | `/vectum/api/v1/task/batch` | 批量更新任务 | 批量更新多个任务 |
| 6 | GET | `/vectum/api/v1/task/all` | 查询所有任务 | 获取所有任务列表 |
| 7 | GET | `/vectum/api/v1/task/{id}/view` | 查询任务详情 | 根据ID查询任务详细信息 |
| 8 | POST | `/vectum/api/v1/task/{id}/toggle` | 启动/停止任务 | 切换任务的运行状态 |
| 9 | GET | `/vectum/api/v1/task/{id}/log` | 获取任务日志 | 根据任务ID和日志类型获取日志内容 |

---

## 🔌 接口详情

### 1️⃣ 创建任务

**接口地址**: `POST /vectum/api/v1/task/add`

**功能描述**: 创建新的任务

**请求参数**:
- Content-Type: `application/json`
- Body: TaskDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/vectum/api/v1/task/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试任务",
    "description": "输出测试日志到控制台",
    "config": "{\"sources\":{\"my_demo_log\":{\"type\":\"demo_logs\",\"format\":\"apache_common\"}},\"sinks\":{\"my_sink\":{\"type\":\"console\",\"encoding\":{\"codec\":\"json\"},\"inputs\":[\"my_demo_log\"]}}}"
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "name": "测试任务",
    "description": "输出测试日志到控制台",
    "config": "{\"sources\":{\"my_demo_log\":{\"type\":\"demo_logs\",\"format\":\"apache_common\"}},\"sinks\":{\"my_sink\":{\"type\":\"console\",\"encoding\":{\"codec\":\"json\"},\"inputs\":[\"my_demo_log\"]}}}",
    "status": "created",
    "pid": 0,
    "createTime": "2024-01-01 12:00:00",
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

**失败响应**(参数校验失败):
```json
{
  "status": 400,
  "msg": "任务名称不能为空",
  "data": null
}
```

---

### 2️⃣ 删除单个任务

**接口地址**: `DELETE /vectum/api/v1/task/{id}`

**功能描述**: 根据ID删除单个任务（会先停止进程再删除）

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:11002/vectum/api/v1/task/123
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": "删除成功"
}
```

**失败响应**(任务不存在):
```json
{
  "status": 104,
  "msg": "任务不存在",
  "data": null
}
```

---

### 3️⃣ 批量删除任务

**接口地址**: `DELETE /vectum/api/v1/task/batch?ids=1,2,3`

**功能描述**: 批量删除多个任务

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<Long\> | 是 | 任务ID列表(重复参数格式，如 ids=1&ids=2&ids=3) |

**请求示例**:
```bash
curl -X DELETE "http://localhost:11002/vectum/api/v1/task/batch?ids=1&ids=2&ids=3&ids=4&ids=5"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": "批量删除成功"
}
```

**失败响应**(ID列表为空):
```json
{
  "status": 400,
  "msg": "任务ID列表不能为空",
  "data": null
}
```

---

### 4️⃣ 更新单个任务

**接口地址**: `PUT /vectum/api/v1/task/{id}`

**功能描述**: 根据ID更新任务信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求参数**:
- Content-Type: `application/json`
- Body: TaskDto

**请求示例**:
```bash
curl -X PUT http://localhost:11002/vectum/api/v1/task/123 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "更新后的任务名称",
    "description": "更新后的描述",
    "config": "{\"sources\":{\"my_demo_log\":{\"type\":\"demo_logs\",\"format\":\"apache_common\"}},\"sinks\":{\"my_sink\":{\"type\":\"console\",\"encoding\":{\"codec\":\"json\"},\"inputs\":[\"my_demo_log\"]}}}"
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

**失败响应**(任务不存在):
```json
{
  "status": 404,
  "msg": "任务不存在",
  "data": null
}
```

---

### 5️⃣ 批量更新任务

**接口地址**: `PUT /vectum/api/v1/task/batch?ids=1,2,3`

**功能描述**: 批量更新多个任务

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<Long\> | 是 | 任务ID列表(重复参数格式，如 ids=1&ids=2&ids=3) |

**请求参数**:
- Content-Type: `application/json`
- Body: TaskDto（部分字段更新）

**请求示例**:
```bash
curl -X PUT "http://localhost:11002/vectum/api/v1/task/batch?ids=1&ids=2&ids=3" \
  -H "Content-Type: application/json" \
  -d '{
    "description": "批量更新的描述信息"
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": "批量修改成功: 3/3"
}
```

**响应说明**: 返回格式为 `"批量修改成功: {成功数}/{总数}"`

---

### 6️⃣ 查询所有任务

**接口地址**: `GET /vectum/api/v1/task/all`

**功能描述**: 获取所有任务列表

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:11002/vectum/api/v1/task/all
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "name": "测试任务",
      "description": "输出测试日志到控制台",
      "config": "{\"sources\":{\"my_demo_log\":{\"type\":\"demo_logs\",\"format\":\"apache_common\"}},\"sinks\":{\"my_sink\":{\"type\":\"console\",\"encoding\":{\"codec\":\"json\"},\"inputs\":[\"my_demo_log\"]}}}",
      "status": "running",
      "pid": 12345,
      "createTime": "2024-01-01 12:00:00",
      "updateTime": "2024-01-01 12:00:00"
    },
    {
      "id": 2,
      "name": "syslog-collector",
      "description": "采集系统日志",
      "config": "{\"sources\":{\"my_demo_log\":{\"type\":\"demo_logs\",\"format\":\"apache_common\"}},\"sinks\":{\"my_sink\":{\"type\":\"console\",\"encoding\":{\"codec\":\"json\"},\"inputs\":[\"my_demo_log\"]}}}",
      "source": "SYSTEM",
      "status": "stopped",
      "pid": 0,
      "createTime": "2024-01-02 10:00:00",
      "updateTime": "2024-01-02 10:00:00"
    }
  ]
}
```

---

### 7️⃣ 查询任务详情

**接口地址**: `GET /vectum/api/v1/task/{id}/view`

**功能描述**: 根据ID查询任务详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求示例**:
```bash
curl -X GET http://localhost:11002/vectum/api/v1/task/<task_id>/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": 123,
    "name": "测试任务",
    "description": "输出测试日志到控制台",
    "config": "{\"sources\":{\"my_demo_log\":{\"type\":\"demo_logs\",\"format\":\"apache_common\"}},\"sinks\":{\"my_sink\":{\"type\":\"console\",\"encoding\":{\"codec\":\"json\"},\"inputs\":[\"my_demo_log\"]}}}",
    "status": "running",
    "pid": 12345,
    "createTime": "2024-01-01 12:00:00",
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

**失败响应**(任务不存在):
```json
{
  "status": 404,
  "msg": "任务不存在",
  "data": null
}
```

---

### 8️⃣ 启动/停止任务

**接口地址**: `POST /vectum/api/v1/task/{id}/toggle`

**功能描述**: 切换任务的运行状态
- 如果任务当前是停止状态，则启动任务
- 如果任务当前是运行状态，则停止任务

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求示例**:
```bash
curl -X POST http://localhost:11002/vectum/api/v1/task/<task_id>/toggle
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": null
}
```

**失败响应**(操作失败):
```json
{
  "status": 105,
  "msg": "任务操作失败",
  "data": null
}
```

---

### 9️⃣ 获取任务日志

**接口地址**: `GET /vectum/api/v1/task/{id}/log?log_type=console`

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
curl -X GET "http://localhost:11002/vectum/api/v1/task/<task_id>/log?log_type=console"
```

**成功响应**:
```text
2024-01-01 12:00:00 [INFO] Vector v0.35.0 starting
2024-01-01 12:00:01 [INFO] Configuration loaded from /tmp/vector-123/vector.toml
2024-01-01 12:00:02 [INFO] Sources: nginx
2024-01-01 12:00:03 [INFO] Sinks: console
2024-01-01 12:00:04 [INFO] Vector has started successfully
```

> **注意**: 日志接口返回的是纯文本格式，不是JSON格式

**失败响应**(获取日志失败):
```json
{
  "status": 101,
  "msg": "请求失败",
  "data": null
}
```

---

## 📊 响应码汇总

| 响应码 | 说明 | 触发场景 |
|--------|------|---------|
| 0 | 请求成功 | 操作成功完成 |
| -1 | 未知错误 | 遇到未定义的异常情况 |
| 101 | 请求失败 | 请求处理失败 |
| 102 | 任务名称不能为空 | 创建/更新任务时未提供任务名称 |
| 103 | 任务配置不能为空 | 创建/更新任务时未提供任务配置 |
| 104 | 任务不存在 | 任务ID不存在或已被删除 |
| 105 | 任务操作失败 | 任务启停、删除等操作执行失败 |


---

## 🔐 注意事项

1. **认证授权**: 当前版本暂未启用身份认证，所有接口均可直接访问
2. **数据校验**: 
   - 任务名称不能为空
   - 配置字段不能为空
   - 配置内容支持 YAML、TOML、JSON 三种格式
3. **批量操作**: 
   - 批量删除/更新时，ID列表不能为空
   - 建议单次批量操作不超过100条记录
4. **日志查询**: 日志类型参数值仅支持 `console` 和 `system`
5. **任务启停**: toggle操作会立即生效，请谨慎操作
6. **进程管理**: 删除任务时会自动停止对应的Vector进程

---

## 💡 使用示例

### JavaScript (Axios)

```javascript
// 创建任务
axios.post('/vectum/api/v1/task/add', {
  name: '测试任务',
  description: '这是一个测试任务',
  config: '{"sources":{"my_demo_log":{"type":"demo_logs","format":"apache_common"}},"sinks":{"my_sink":{"type":"console","encoding":{"codec":"json"},"inputs":["my_demo_log"]}}}',
  source: 'API'
})

// 获取所有任务
axios.get('/vectum/api/v1/task/all')

// 更新任务
axios.put('/vectum/api/v1/task/123', {
  name: '更新任务',
  description: '更新描述'
})

// 启动/停止任务
axios.post('/vectum/api/v1/task/<task_id>/toggle')

// 获取日志
axios.get('/vectum/api/v1/task/<task_id>/log', {
  params: {
    log_type: 'console'
  }
})

// 批量删除任务
axios.delete('/vectum/api/v1/task/batch', {
  params: {
    ids: [1, 2, 3]
  }
})
```

### cURL

```bash
# 创建任务
curl -X POST http://localhost:11002/vectum/api/v1/task/add \
  -H "Content-Type: application/json" \
  -d '{"name":"测试任务","description":"测试","config":"{\"sources\":{\"my_demo_log\":{\"type\":\"demo_logs\",\"format\":\"apache_common\"}},\"sinks\":{\"my_sink\":{\"type\":\"console\",\"encoding\":{\"codec\":\"json\"},\"inputs\":[\"my_demo_log\"]}}}","source":"API"}'

# 获取所有任务
curl -X GET http://localhost:11002/vectum/api/v1/task/all

# 更新任务
curl -X PUT http://localhost:11002/vectum/api/v1/task/123 \
  -H "Content-Type: application/json" \
  -d '{"name":"更新任务"}'

# 启动/停止任务
curl -X POST http://localhost:11002/vectum/api/v1/task/<task_id>/toggle

# 获取日志
curl -X GET "http://localhost:11002/vectum/api/v1/task/<task_id>/log?log_type=console"

# 批量删除任务
curl -X DELETE "http://localhost:11002/vectum/api/v1/task/batch?ids=1&ids=2&ids=3"
```

---