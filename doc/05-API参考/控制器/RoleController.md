# Role 角色接口文档

**基础信息**
- **模块名称**: 角色管理
- **基础路径**: `/api/v1/system/role`
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. RoleDto (角色传输对象)

```json
{
  "id": 1,                    // Integer - 角色ID（更新时使用，创建时不传）
  "name": "管理员",           // String - 角色名称（必填）
  "menu_ids": "1,2,3"          // String - 菜单权限列表，逗号分隔（可选）
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| id | Integer | 否 | 角色ID，更新时需传入 |
| name | String | 是 | 角色名称 |
| menu_ids | String | 否 | 菜单权限列表，逗号分隔 |

### 2. RoleVo (角色视图对象)

```json
{
  "id": 1,                    // Integer - 角色ID
  "name": "管理员",           // String - 角色名称
  "role_id": 1,               // Integer - 角色ID（冗余字段）
  "is_super_admin": false,     // Boolean - 是否超级管理员内置角色
  "update_time": "2024-01-01 12:00:00", // String - 更新时间
  "menu_ids": [1, 2, 3],      // List<Integer> - 菜单权限ID列表
  "menu_names": ["用户管理", "角色管理", "菜单管理"]  // List<String> - 菜单权限名称列表
}
```

`is_super_admin` 用于前端识别系统内置超级管理员角色。超级管理员角色由系统初始化生成，默认拥有全部菜单权限，新增菜单会自动同步授权。

### 3. RoleSearchDto (角色搜索传输对象)

```json
{
  "name": "管理员",           // String - 角色名称（可选）
  "page": 1,                 // Integer - 页码（继承自SortPageDto）
  "per_page": 10,            // Integer - 每页大小（继承自SortPageDto）
  "order_by": "update_time", // String - 排序字段（继承自SortPageDto）
  "order_dir": "desc"       // String - 排序方向（继承自SortPageDto）
}
```

该接口列表为 GET Bean 绑定：分页可使用 `per_page`，排序 query 参数当前使用 `orderBy`、`orderDir`。

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
| 1 | POST | `/api/v1/system/role/add` | 创建角色 | 创建新的角色 |
| 2 | DELETE | `/api/v1/system/role/{id}` | 删除角色 | 根据ID删除单个角色 |
| 3 | DELETE | `/api/v1/system/role/bulk/{ids}` | 批量删除角色 | 批量删除多个角色 |
| 4 | POST | `/api/v1/system/role/{id}/update` | 更新角色 | 根据ID更新角色信息 |
| 5 | POST | `/api/v1/system/role/{ids}/bulk-update` | 批量更新角色 | 批量更新多个角色 |
| 6 | GET | `/api/v1/system/role/list` | 查询角色列表 | 分页查询角色列表 |
| 7 | GET | `/api/v1/system/role/{id}/view` | 查询角色详情 | 根据ID查询角色详细信息 |
| 8 | GET | `/api/v1/system/role/type/list` | 获取全部角色列表 | 获取所有角色用于下拉选择 |
| 9 | GET | `/api/v1/system/role/permission/tree` | 获取全权限树 | 获取系统所有权限的树形结构 |

---

## 🔌 接口详情

### 1️⃣ 创建角色

**接口地址**: `POST /api/v1/system/role/add`

**功能描述**: 创建新的角色

**请求参数**:
- Content-Type: `application/json`
- Body: RoleDto

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/role/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试角色",
    "menu_ids": "1,2,3"
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

### 2️⃣ 删除角色

**接口地址**: `DELETE /api/v1/system/role/{id}`

**功能描述**: 根据ID删除单个角色

**业务规则**: 超级管理员角色为系统内置角色，不允许删除。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 角色ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:11001/api/v1/system/role/1
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

### 3️⃣ 批量删除角色

**接口地址**: `DELETE /api/v1/system/role/bulk/{ids}`

**功能描述**: 批量删除多个角色

**业务规则**: 批量删除中不能包含超级管理员角色。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<Long\> | 是 | 角色ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:11001/api/v1/system/role/bulk/1,2,3
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

### 4️⃣ 更新角色

**接口地址**: `POST /api/v1/system/role/{id}/update`

**功能描述**: 根据ID更新角色信息

**业务规则**: 超级管理员角色为系统内置角色，不允许修改名称或菜单权限。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 角色ID |

**请求参数**:
- Content-Type: `application/json`
- Body: RoleDto

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/role/1/update \
  -H "Content-Type: application/json" \
  -d '{
    "name": "更新后的角色名称",
    "menu_ids": "1,2,3,4"
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

### 5️⃣ 批量更新角色

**接口地址**: `POST /api/v1/system/role/{ids}/bulk-update`

**功能描述**: 批量更新多个角色

**业务规则**: 批量更新中不能包含超级管理员角色。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | Long[] | 是 | 角色ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: RoleDto

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/role/1,2,3/bulk-update \
  -H "Content-Type: application/json" \
  -d '{
    "name": "批量更新角色"
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

### 6️⃣ 查询角色列表

**接口地址**: `GET /api/v1/system/role/list`

**功能描述**: 分页查询角色列表

**可见性规则**: 非超级管理员查询角色列表时不会返回超级管理员角色；超级管理员可见全部角色。

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| name | String | 否 | 角色名称（模糊查询） |
| page | Integer | 否 | 页码，默认1 |
| per_page | Integer | 否 | 每页数量，默认 10 |
| orderBy | String | 否 | 排序字段 |
| orderDir | String | 否 | 排序方向（`asc` 或 `desc`） |

**请求示例**:
```bash
curl -X GET "http://localhost:11001/api/v1/system/role/list?name=管理员&page=1&per_page=10"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "total": 10,
    "rows": [
      {
        "id": 1,
        "name": "管理员",
        "role_id": 1,
        "is_super_admin": false,
        "update_time": "2024-01-01 12:00:00",
        "menu_ids": [1, 2, 3],
        "menu_names": ["用户管理", "角色管理", "菜单管理"]
      }
    ]
  }
}
```

---

### 7️⃣ 查询角色详情

**接口地址**: `GET /api/v1/system/role/{id}/view`

**功能描述**: 根据ID查询角色详细信息

**可见性规则**: 非超级管理员不能查看超级管理员角色详情；超级管理员可见全部角色详情。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 角色ID |

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/role/1/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "name": "管理员",
    "role_id": 1,
    "is_super_admin": false,
    "update_time": "2024-01-01 12:00:00",
    "menu_ids": [1, 2, 3],
    "menu_names": ["用户管理", "角色管理", "菜单管理"]
  }
}
```

---

### 8️⃣ 获取全部角色列表

**接口地址**: `GET /api/v1/system/role/type/list`

**功能描述**: 获取所有角色列表，用于下拉选择框

**可见性规则**: 非超级管理员获取角色下拉列表时不会返回超级管理员角色，避免将内置角色分配给普通用户。

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/role/type/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {
        "label": "管理员",
        "value": "1"
      },
      {
        "label": "普通用户",
        "value": "2"
      }
    ]
  }
}
```

---

### 9️⃣ 获取全权限树

**接口地址**: `GET /api/v1/system/role/permission/tree`

**功能描述**: 获取系统所有权限的树形结构，用于角色权限配置

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/role/permission/tree
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": [
    {
      "id": "1",
      "name": "系统管理",
      "children": [
        {
          "id": "1-1",
          "name": "用户管理",
          "children": []
        },
        {
          "id": "1-2",
          "name": "角色管理",
          "children": []
        }
      ]
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

---

## 🔐 注意事项

1. **认证授权**: 需要登录认证
2. **权限管理**: 角色关联菜单权限，通过menu_ids字段配置
3. **批量操作**: 批量删除/更新时，ID列表不能为空
4. **内置角色保护**: 超级管理员角色由系统初始化生成，仅超级管理员可见，不允许编辑或删除
5. **菜单权限同步**: 超级管理员角色默认拥有全部菜单权限，新建菜单后系统会自动补齐授权

---
