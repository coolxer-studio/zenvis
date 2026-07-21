# Dashboard 看板接口文档

**基础信息**
- **模块名称**: 看板管理
- **基础路径**: `/api/v1/system/dashboard`
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. DashboardDto (看板传输对象)

```json
{
  "id": 1,                    // Integer - 看板ID（更新时使用，创建时不传）
  "name": "数据分析看板",     // String - 看板名称（必填）
  "code": "system-board",     // String - 路由编码（仅内置看板 BUILT 必填）
  "type": "BUILT",            // DashboardType - 看板类型（必填）
  "config_index": "user-event-dashboard", // String - 低代码页面看板配置索引
  "html_path": "demo/dashboard.html",     // String - HTML页面相对路径
  "is_default": false,                    // Boolean - 是否为默认看板
  "url": "https://example.com/report"     // String - 外链地址
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| id | Integer | 否 | 看板ID，更新时需传入 |
| name | String | 是 | 看板名称 |
| type | DashboardType | 是 | 类型：`BUILT`、`LOW_CODE_PAGE`、`HTML_PAGE`、`LINK` |
| code | String | 仅 `BUILT` 必填 | 内置看板路由编码，用于前端映射内置组件 |
| config_index | String | 仅 `LOW_CODE_PAGE` 必填 | AMIS低代码页面配置索引 |
| html_path | String | 仅 `HTML_PAGE` 必填 | HTML页面相对路径，不允许 `/` 开头、URL、查询参数或 `..` |
| is_default | Boolean | 否 | 是否为默认看板；更新时不传表示保留原值 |
| url | String | 仅 `LINK` 必填 | 外链地址 |

**类型校验规则**:

- `BUILT`：必须填写 `code`，用于前端根据编码找到内置看板组件。
- `LOW_CODE_PAGE`：必须填写 `configIndex`，不要求 `code`。
- `HTML_PAGE`：必须填写相对路径 `html_path`，页面实际从 `/zenvis/html-page/{html_path}` 加载，不要求 `code`。
- `LINK`：必须填写 `url`，不要求 `code`。
- 非内置看板的选中、高亮和打开逻辑推荐使用 `id`，不要依赖 `code`。
- 系统始终保留一个默认看板；将其他看板设为默认会自动取消原默认项，当前默认项不能直接取消或删除。

### 2. DashboardVo (看板视图对象)

```json
{
  "id": 1,                    // Integer - 看板ID
  "name": "数据分析看板",     // String - 看板名称
  "code": "system-board",     // String - 内置看板编码，非内置可为空
  "type": "BUILT",            // DashboardType - 看板类型
  "type_description": "内置看板", // String - 类型描述
  "config_index": "",         // String - 低代码页面配置索引
  "html_path": "",            // String - HTML页面相对路径
  "is_default": true,         // Boolean - 是否为默认看板
  "url": "",                  // String - 外链地址
  "update_time": "2024-01-01 12:00:00"  // String - 更新时间
}
```

### 3. DashboardSearchDto (看板搜索传输对象)

```json
{
  "name": "数据",             // String - 看板名称（可选）
  "url": "/dashboard",       // String - URL地址（可选）
  "page": 1,                 // Integer - 页码（继承自SortPageDto）
  "per_page": 10,            // Integer - 每页大小（继承自SortPageDto）
  "order_by": "update_time", // String - 排序字段（继承自SortPageDto）
  "order_dir": "desc"       // String - 排序方向（继承自SortPageDto）
}
```

上例是 JSON body 命名；`GET /list` 的排序 query 参数当前使用 `orderBy`、`orderDir`，`POST /list` JSON 使用 `order_by`、`order_dir`。

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
| 1 | POST | `/api/v1/system/dashboard/add` | 创建看板 | 创建新的看板 |
| 2 | DELETE | `/api/v1/system/dashboard/{id}` | 删除看板 | 根据ID删除单个看板 |
| 3 | DELETE | `/api/v1/system/dashboard/bulk/{ids}` | 批量删除看板 | 批量删除多个看板 |
| 4 | POST | `/api/v1/system/dashboard/{id}/update` | 更新看板 | 根据ID更新看板信息 |
| 5 | POST | `/api/v1/system/dashboard/{ids}/bulk-update` | 批量更新看板 | 批量更新多个看板 |
| 6 | GET | `/api/v1/system/dashboard/list` | 查询看板列表 | 分页查询看板列表 |
| 7 | GET | `/api/v1/system/dashboard/{id}/view` | 查询看板详情 | 根据ID查询看板详细信息 |
| 8 | GET | `/api/v1/system/dashboard/type/list` | 获取类型列表 | 获取看板类型枚举值 |
| 9 | POST | `/api/v1/system/dashboard/list` | 获取用户自定义看板列表 | 获取所有看板列表（POST方式） |

---

## 🔌 接口详情

### 1️⃣ 创建看板

**接口地址**: `POST /api/v1/system/dashboard/add`

**功能描述**: 创建新的看板

**请求参数**:
- Content-Type: `application/json`
- Body: DashboardDto

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/dashboard/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试看板",
    "code": "system-board",
    "type": "BUILT",
    "is_default": true
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

### 2️⃣ 删除看板

**接口地址**: `DELETE /api/v1/system/dashboard/{id}`

**功能描述**: 根据ID删除单个看板

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 看板ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:11001/api/v1/system/dashboard/1
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

### 3️⃣ 批量删除看板

**接口地址**: `DELETE /api/v1/system/dashboard/bulk/{ids}`

**功能描述**: 批量删除多个看板

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<Long\> | 是 | 看板ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:11001/api/v1/system/dashboard/bulk/1,2,3
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

### 4️⃣ 更新看板

**接口地址**: `POST /api/v1/system/dashboard/{id}/update`

**功能描述**: 根据ID更新看板信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 看板ID |

**请求参数**:
- Content-Type: `application/json`
- Body: DashboardDto

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/dashboard/1/update \
  -H "Content-Type: application/json" \
  -d '{
    "name": "更新后的看板名称",
    "url": "/dashboard/updated"
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

### 5️⃣ 批量更新看板

**接口地址**: `POST /api/v1/system/dashboard/{ids}/bulk-update`

**功能描述**: 批量更新多个看板

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | Long[] | 是 | 看板ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: DashboardDto

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/dashboard/1,2,3/bulk-update \
  -H "Content-Type: application/json" \
  -d '{
    "type": "ROUTE"
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

### 6️⃣ 查询看板列表（GET）

**接口地址**: `GET /api/v1/system/dashboard/list`

**功能描述**: 分页查询看板列表

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| name | String | 否 | 看板名称（模糊查询） |
| url | String | 否 | URL地址（模糊查询） |
| page | Integer | 否 | 页码，默认1 |
| per_page | Integer | 否 | 每页数量，默认 10 |
| orderBy | String | 否 | 排序字段 |
| orderDir | String | 否 | 排序方向（`asc` 或 `desc`） |

**请求示例**:
```bash
curl -X GET "http://localhost:11001/api/v1/system/dashboard/list?name=数据&page=1&per_page=10"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "total": 20,
    "rows": [
      {
        "id": 1,
        "name": "数据分析看板",
        "code": "dashboard_001",
        "type": "ROUTE",
        "type_description": "路由",
        "url": "/dashboard/data",
        "update_time": "2024-01-01 12:00:00"
      }
    ]
  }
}
```

---

### 7️⃣ 查询看板详情

**接口地址**: `GET /api/v1/system/dashboard/{id}/view`

**功能描述**: 根据ID查询看板详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 看板ID |

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/dashboard/1/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "name": "数据分析看板",
    "code": "dashboard_001",
    "type": "ROUTE",
    "type_description": "路由",
    "url": "/dashboard/data",
    "update_time": "2024-01-01 12:00:00"
  }
}
```

---

### 8️⃣ 获取类型列表

**接口地址**: `GET /api/v1/system/dashboard/type/list`

**功能描述**: 获取看板类型枚举值列表

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/dashboard/type/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {
        "label": "路由",
        "value": "ROUTE"
      },
      {
        "label": "外链",
        "value": "LINK"
      }
    ]
  }
}
```

---

### 9️⃣ 获取用户自定义看板列表（POST）

**接口地址**: `POST /api/v1/system/dashboard/list`

**功能描述**: 获取所有用户自定义看板列表

**请求参数**: 无

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/dashboard/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "name": "数据分析看板",
      "code": "dashboard_001",
      "type": "ROUTE",
      "type_description": "路由",
      "url": "/dashboard/data",
      "update_time": "2024-01-01 12:00:00"
    },
    {
      "id": 2,
      "name": "外部链接看板",
      "code": "dashboard_002",
      "type": "LINK",
      "type_description": "外链",
      "url": "https://example.com/dashboard",
      "update_time": "2024-01-02 10:00:00"
    }
  ]
}
```

---

## 📊 响应码汇总

| 响应码 | 说明 | 触发场景 |
|--------|------|---------|
| 0 | 请求成功 | 操作成功完成 |
| -1 | 未知错误 | 遇到未定义的异常情况 |
| 183 | 系统必须保留一个默认看板 | 试图关闭当前唯一默认看板 |
| 184 | 默认看板不能删除 | 单个或批量删除包含默认看板 |
| 185 | 不能同时设置多个默认看板 | 批量将多个看板设为默认 |
| 186 | HTML看板路径必须是相对文件路径 | HTML路径包含URL、绝对路径、查询参数或路径穿越 |

---

## 🔐 注意事项

1. **认证授权**: 需要登录认证
2. **看板类型**: 支持 `BUILT`、`LOW_CODE_PAGE`、`HTML_PAGE` 和 `LINK`
3. **默认看板**: 系统始终保留一个默认项；深链参数优先于默认项
4. **批量操作**: 批量删除/更新时，ID列表不能为空，且不能同时设置多个默认项
5. **重复接口**: `/list` 接口同时支持 GET（分页查询）和 POST（获取全部）两种方式

---
