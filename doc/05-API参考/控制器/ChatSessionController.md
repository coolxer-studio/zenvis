# ChatSessionController 会话管理接口文档

**基础信息**
- **模块名称**: 会话管理
- **基础路径**: `/api/v1/dih/chat-session`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. ChatSessionDto (会话传输对象)

```json
{
  "session_id": "chat-001",    // String - 会话ID
  "title": "新建会话",         // String - 会话标题
  "type": "ask",              // String - 会话类型
  "deep_think": false,         // Boolean - 是否深度思考模式
  "online_search": false,      // Boolean - 是否在线搜索
  "messages": "[]"            // String - 消息列表(JSON字符串)
}
```

### 2. ChatSessionSearchDto (会话搜索传输对象)

```json
{
  "page": 1,                  // Integer - 当前页码
  "per_page": 10,             // Integer - 每页条数
  "order_by": "update_time", // String - JSON body 排序字段
  "order_dir": "desc",       // String - JSON body 排序方向
  "title": "",                // String - 会话标题（搜索关键词）
  "type": ""                  // String - 会话类型（筛选条件）
}
```

会话列表为 GET Bean 绑定：分页可使用 `per_page`，排序 query 参数当前使用 `orderBy`、`orderDir`。

### 3. ChatSessionVo (会话视图对象)

```json
{
  "id": 1,                    // Long - 会话ID
  "session_id": "chat-001",    // String - 会话唯一标识
  "title": "新建会话",         // String - 会话标题
  "type": "ask",              // String - 会话类型
  "messages": [],             // List<Message> - 消息列表
  "update_time": "2024-01-01 12:00:00" // String - 更新时间
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
| 1 | POST | `/api/v1/dih/chat-session/add` | 创建会话 | 创建新的聊天会话 |
| 2 | DELETE | `/api/v1/dih/chat-session/{id}` | 删除会话 | 根据ID删除单个会话 |
| 3 | DELETE | `/api/v1/dih/chat-session/bulk/{ids}` | 批量删除会话 | 批量删除多个会话 |
| 4 | POST | `/api/v1/dih/chat-session/{id}/update` | 更新会话 | 根据ID更新会话信息 |
| 5 | GET | `/api/v1/dih/chat-session/list/pin` | 获取置顶会话列表 | 获取用户置顶的会话列表 |
| 6 | GET | `/api/v1/dih/chat-session/list` | 获取会话列表 | 分页获取会话列表 |
| 7 | GET | `/api/v1/dih/chat-session/{id}/view` | 获取会话详情 | 根据ID获取会话详细信息 |
| 8 | GET | `/api/v1/dih/chat-session/{sessionId}/session` | 获取会话信息 | 根据路径变量 `sessionId` 获取会话信息 |

---

## 🔌 接口详情

### 1️⃣ 创建会话

**接口地址**: `POST /api/v1/dih/chat-session/add`

**功能描述**: 创建新的聊天会话

**请求参数**:
- Content-Type: `application/json`
- Body: ChatSessionDto

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/dih/chat-session/add \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "chat-001",
    "title": "新建会话",
    "type": "ask",
    "messages": "[]"
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

**失败响应**:
```json
{
  "status": -1,
  "msg": "UNKNOWN_ERROR",
  "data": null
}
```

---

### 2️⃣ 删除会话

**接口地址**: `DELETE /api/v1/dih/chat-session/{id}`

**功能描述**: 根据ID删除单个会话

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 会话ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:11001/api/v1/dih/chat-session/1
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

### 3️⃣ 批量删除会话

**接口地址**: `DELETE /api/v1/dih/chat-session/bulk/{ids}`

**功能描述**: 批量删除多个会话

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<Long\> | 是 | 会话ID列表（逗号分隔） |

**请求示例**:
```bash
curl -X DELETE http://localhost:11001/api/v1/dih/chat-session/bulk/1,2,3
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

### 4️⃣ 更新会话

**接口地址**: `POST /api/v1/dih/chat-session/{id}/update`

**功能描述**: 根据ID更新会话信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 会话ID |

**请求参数**:
- Content-Type: `application/json`
- Body: ChatSessionDto

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/dih/chat-session/1/update \
  -H "Content-Type: application/json" \
  -d '{
    "title": "更新后的会话标题"
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

### 5️⃣ 获取置顶会话列表

**接口地址**: `GET /api/v1/dih/chat-session/list/pin`

**功能描述**: 获取用户置顶的会话列表

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/dih/chat-session/list/pin
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

### 6️⃣ 获取会话列表

**接口地址**: `GET /api/v1/dih/chat-session/list`

**功能描述**: 分页获取会话列表

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| page | Integer | 否 | 页码（默认1） |
| per_page | Integer | 否 | 每页数量，默认 10 |
| orderBy | String | 否 | 排序字段 |
| orderDir | String | 否 | 排序方向（`asc` 或 `desc`） |
| title | String | 否 | 会话标题（搜索关键词） |
| type | String | 否 | 会话类型（筛选条件） |

**请求示例**:
```bash
curl -X GET "http://localhost:11001/api/v1/dih/chat-session/list?page=1&per_page=10"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "total": 100,
    "rows": []
  }
}
```

---

### 7️⃣ 获取会话详情

**接口地址**: `GET /api/v1/dih/chat-session/{id}/view`

**功能描述**: 根据ID获取会话详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 会话ID |

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/dih/chat-session/1/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "session_id": "chat-001",
    "title": "新建会话",
    "type": "ask",
    "messages": [],
    "update_time": "2024-01-01 12:00:00"
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

### 8️⃣ 获取会话信息

**接口地址**: `GET /api/v1/dih/chat-session/{sessionId}/session`

**功能描述**: 根据session_id获取会话信息，如果会话不存在则返回默认问候语

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| session_id | String | 是 | 会话唯一标识 |

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| type | String | 否 | 会话类型（决定默认问候语） |

**请求示例**:
```bash
curl -X GET "http://localhost:11001/api/v1/dih/chat-session/chat-001/session?type=ask"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "session_id": "chat-001",
    "title": "新建会话",
    "type": "ask",
    "messages": [
      {
        "sender": "ai",
        "content": "我是数智助手，可以解答系统相关运营问题，有什么问题尽管提问吧！",
        "time": "2024-01-01 12:00:00"
      }
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
2. **会话类型**: 支持多种会话类型（ask/agent_data_access/agent_data_visualization/agent_data_analysis/agent_config_management/agent_report）
3. **默认问候语**: 根据会话类型返回不同的默认问候语
4. **批量操作**: 支持批量删除会话
