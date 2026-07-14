# RetrievalController 数据检索接口文档

**基础信息**
- **模块名称**: 数据检索
- **基础路径**: `/api/v1/retrieval`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. RetrievalRequestDto (检索请求传输对象)

```json
{
  "displayList": [],          // List - 展示列表配置
  "ruleId": 1,                // Integer - 检索规则ID
  "searchParams": {}          // Object - 搜索参数
}
```

### 2. DataListVo (数据列表视图对象)

```json
{
  "page": 1,                  // Integer - 当前页码
  "size": 10,                 // Integer - 每页条数
  "total": 100,               // Long - 总记录数
  "data": []                  // List - 数据列表
}
```

### 3. DataEntityResultVo (数据实体结果视图对象)

```json
{
  "entities": [],             // List - 实体列表
  "total": 10                 // Integer - 实体总数
}
```

### 4. DataAttributeResultVo (数据属性结果视图对象)

```json
{
  "attributes": [],           // List - 属性列表
  "total": 20                 // Integer - 属性总数
}
```

### 5. ResponseWrap (统一响应格式)

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
| 1 | POST | `/api/v1/retrieval/do` | 数据检索 | 根据条件进行数据检索 |
| 2 | POST | `/api/v1/retrieval/rule/create` | 创建检索规则 | 创建新的检索规则 |
| 3 | POST | `/api/v1/retrieval/rule/update` | 更新检索规则 | 更新已有检索规则 |
| 4 | POST | `/api/v1/retrieval/rule/delete` | 删除检索规则 | 删除检索规则 |
| 5 | GET | `/api/v1/retrieval/rule/list` | 检索规则列表 | 获取检索规则列表 |
| 6 | GET | `/api/v1/retrieval/rule/get` | 获取指定检索规则 | 根据ID获取检索规则 |
| 7 | GET | `/api/v1/retrieval/entity/list` | 获取实体列表 | 获取数据实体列表 |
| 8 | GET | `/api/v1/retrieval/attribute/list` | 获取属性列表 | 获取数据属性列表 |
| 9 | GET | `/api/v1/retrieval/candidate/list` | 获取备选信息 | 获取指定字段备选信息 |
| 10 | GET | `/api/v1/retrieval/display/entity/list` | 获取展示实体列表 | 获取展示用实体列表 |
| 11 | GET | `/api/v1/retrieval/display/attribute/list` | 获取展示属性列表 | 获取展示用属性列表 |

---

## 🔌 接口详情

### 1️⃣ 数据检索

**接口地址**: `POST /api/v1/retrieval/do`

**功能描述**: 根据条件进行数据检索

**请求参数**:
- Content-Type: `application/json`
- Body: RetrievalRequestDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/retrieval/do \
  -H "Content-Type: application/json" \
  -d '{
    "displayList": [
      {
        "entityName": "asset_host",
        "attributeList": ["id", "host_name", "ip"]
      }
    ]
  }'
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
    "data": []
  }
}
```

**失败响应**:
```json
{
  "status": -1,
  "msg": "DISPLAY_LIMIT_ERROR",
  "data": null
}
```

---

### 2️⃣ 创建检索规则

**接口地址**: `POST /api/v1/retrieval/rule/create`

**功能描述**: 创建新的检索规则

**请求参数**:
- Content-Type: `application/json`
- Body: RetrievalRequestDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/retrieval/rule/create \
  -H "Content-Type: application/json" \
  -d '{
    "displayList": [],
    "searchParams": {}
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": null
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

### 3️⃣ 更新检索规则

**接口地址**: `POST /api/v1/retrieval/rule/update`

**功能描述**: 更新已有检索规则

**请求参数**:
- Content-Type: `application/json`
- Body: RetrievalRequestDto

**更新行为说明**:

更新规则会先读取已有规则，再将本次请求中缺失的上下文补齐，然后复用创建规则的生成逻辑保存。可补齐的上下文包括：

- `entity`
- `display_list`
- `type`
- 普通筛选的 `criteria_list` / `criteria_logic`
- 高级筛选的 `sql`
- `rule_name` / `rule_description`

因此，前端在更新高级过滤器时即使只提交 `id`、`type`、`sql` 和本次展示字段，也可以正常保存；如果本次请求显式传了实体、展示字段或条件，则以本次请求为准。

**高级 where 表达式安全规则**:

- 字段必须来自当前实体元数据。
- 支持 `= != > < >= <= like between in is null is not null`。
- 支持反引号包裹字段名，例如 `` `ip` = '10.0.0.1' ``。
- 不支持任意 SQL、子查询、函数调用、注释拼接或 `1=1` 这类无字段条件。

**请求示例**:

```bash
curl -X POST http://localhost:8080/api/v1/retrieval/rule/update \
  -H "Content-Type: application/json" \
  -d '{
    "id": 7,
    "rule_name": "攻击事件过滤器",
    "type": "advanced",
    "sql": "module_type_name = '\''网站攻击'\'' and attack_type_name in ('\''信息泄露'\'', '\''SQL注入'\'')",
    "display_list": [
      {
        "entity": "asset",
        "attribute_list": ["module_type_name", "attack_type_name"]
      }
    ]
  }'
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

### 4️⃣ 删除检索规则

**接口地址**: `POST /api/v1/retrieval/rule/delete`

**功能描述**: 删除检索规则

**请求参数**:
- Content-Type: `application/json`
- Body: RetrievalRequestDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/retrieval/rule/delete \
  -H "Content-Type: application/json" \
  -d '{
    "ruleId": 1
  }'
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

### 5️⃣ 检索规则列表

**接口地址**: `GET /api/v1/retrieval/rule/list`

**功能描述**: 获取检索规则列表

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/retrieval/rule/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "page": 1,
    "size": 10,
    "total": 5,
    "data": []
  }
}
```

---

### 6️⃣ 获取指定检索规则

**接口地址**: `GET /api/v1/retrieval/rule/get?id=1`

**功能描述**: 根据ID获取检索规则

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Integer | 是 | 检索规则ID |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/retrieval/rule/get?id=1"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {}
}
```

---

### 7️⃣ 获取实体列表

**接口地址**: `GET /api/v1/retrieval/entity/list`

**功能描述**: 获取数据实体列表

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| rule_id | Integer | 否 | 检索规则ID |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/retrieval/entity/list?rule_id=1"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "entities": [],
    "total": 10
  }
}
```

---

### 8️⃣ 获取属性列表

**接口地址**: `GET /api/v1/retrieval/attribute/list`

**功能描述**: 获取数据属性列表

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| entity | String | 否 | 实体名称 |
| rule_id | Integer | 否 | 检索规则ID |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/retrieval/attribute/list?entity=asset_host"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "attributes": [],
    "total": 20
  }
}
```

---

### 9️⃣ 获取备选信息

**接口地址**: `GET /api/v1/retrieval/candidate/list`

**功能描述**: 获取指定字段备选信息

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| attributeId | Integer | 是 | 属性ID |
| text | String | 否 | 搜索文本 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/retrieval/candidate/list?attributeId=1&text=test"
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
    "data": []
  }
}
```

---

### 🔟 获取展示实体列表

**接口地址**: `GET /api/v1/retrieval/display/entity/list`

**功能描述**: 获取展示用实体列表

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| rule_id | Integer | 否 | 检索规则ID |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/retrieval/display/entity/list?rule_id=1"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "entities": [],
    "total": 10
  }
}
```

---

### 1️⃣1️⃣ 获取展示属性列表

**接口地址**: `GET /api/v1/retrieval/display/attribute/list`

**功能描述**: 获取展示用属性列表

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| entity | String | 否 | 实体名称 |
| rule_id | Integer | 否 | 检索规则ID |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/retrieval/display/attribute/list?entity=asset_host"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "attributes": [],
    "total": 20
  }
}
```

---

## 📊 响应码汇总

| 响应码 | 说明 | 触发场景 |
|--------|------|---------|
| 0 | 请求成功 | 操作成功完成 |
| -1 | 未知错误 | 遇到未定义的异常情况 |
| DISPLAY_LIMIT_ERROR | 展示限制错误 | 展示属性列表少于2个 |

---

## 🔐 注意事项

1. **认证授权**: 需要登录认证
2. **检索规则**: 创建规则时displayList中的每个实体的attributeList至少需要2个属性
