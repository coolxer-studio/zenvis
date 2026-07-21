# User 用户接口文档

**基础信息**
- **模块名称**: 用户管理
- **基础路径**: `/api/v1/system/user`
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. UserDto (用户传输对象)

```json
{
  "id": 1,                    // Integer - 用户ID（更新时使用，创建时不传）
  "email": "user@example.com",// String - 登录邮箱（必填）
  "name": "用户名",           // String - 用户名（必填）
  "password": "******",       // String - 密码（创建时必填）
  "roleId": 1                // Integer - 角色ID（必填）
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| id | Integer | 否 | 用户ID，更新时需传入 |
| email | String | 是 | 登录邮箱 |
| name | String | 是 | 用户名 |
| password | String | 是 | 密码，创建时必填 |
| roleId | Integer | 是 | 角色ID |

### 2. UserVo (用户视图对象)

```json
{
  "id": 1,                    // Integer - 用户ID
  "email": "user@example.com",// String - 登录邮箱
  "name": "用户名",           // String - 用户名
  "roleId": 1,               // Integer - 角色ID
  "roleName": "管理员",       // String - 角色名称
  "isSuperAdmin": false,     // Boolean - 是否超级管理员内置账号
  "updateTime": "2024-01-01 12:00:00"  // String - 更新时间
}
```

`isSuperAdmin` 用于前端识别系统内置超级管理员账号。默认超级管理员账号为 `super@admin.com`，名称为 `超级管理员`，仅超级管理员本人可在用户管理接口中看到。

### 3. UserSearchDto (用户搜索传输对象)

```json
{
  "name": "用户名",           // String - 用户名（可选）
  "email": "user@example.com",// String - 登录邮箱（可选）
  "page": 1,                 // Integer - 页码（继承自SortPageDto）
  "size": 10,                // Integer - 每页大小（继承自SortPageDto）
  "sort": "updateTime",      // String - 排序字段（继承自SortPageDto）
  "order": "desc"            // String - 排序方式（继承自SortPageDto）
}
```

### 4. PasswordChangeDto (密码修改传输对象)

```json
{
  "password": "newPassword", // String - 新密码（必填）
  "oldPassword": "oldPassword" // String - 旧密码（必填）
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
| 1 | POST | `/api/v1/system/user/add` | 创建用户 | 创建新的用户 |
| 2 | DELETE | `/api/v1/system/user/{id}` | 删除用户 | 根据ID删除单个用户 |
| 3 | POST | `/api/v1/system/user/{id}/update` | 更新用户 | 根据ID更新用户信息 |
| 4 | POST | `/api/v1/system/user/{ids}/bulk-update` | 批量更新用户 | 批量更新多个用户 |
| 5 | GET | `/api/v1/system/user/list` | 查询用户列表 | 分页查询用户列表 |
| 6 | GET | `/api/v1/system/user/{id}/view` | 查询用户详情 | 根据ID查询用户详细信息 |
| 7 | POST | `/api/v1/system/user/update-password` | 修改密码 | 当前用户修改密码 |

---

## 🔌 接口详情

### 1️⃣ 创建用户

**接口地址**: `POST /api/v1/system/user/add`

**功能描述**: 创建新的用户

**业务规则**: 创建普通用户时不能选择超级管理员角色。

**请求参数**:
- Content-Type: `application/json`
- Body: UserDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/user/add \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "name": "测试用户",
    "password": "123456",
    "roleId": 1
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

### 2️⃣ 删除用户

**接口地址**: `DELETE /api/v1/system/user/{id}`

**功能描述**: 根据ID删除单个用户

**业务规则**: 超级管理员账号为系统内置账号，不允许通过用户管理接口删除。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 用户ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:11002/api/v1/system/user/1
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

### 3️⃣ 更新用户

**接口地址**: `POST /api/v1/system/user/{id}/update`

**功能描述**: 根据ID更新用户信息

**业务规则**: 超级管理员账号为系统内置账号，不允许通过用户管理接口修改。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 用户ID |

**请求参数**:
- Content-Type: `application/json`
- Body: UserDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/user/1/update \
  -H "Content-Type: application/json" \
  -d '{
    "name": "更新后的用户名",
    "roleId": 2
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

### 4️⃣ 批量更新用户

**接口地址**: `POST /api/v1/system/user/{ids}/bulk-update`

**功能描述**: 批量更新多个用户

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | Long[] | 是 | 用户ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: UserDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/user/1,2,3/bulk-update \
  -H "Content-Type: application/json" \
  -d '{
    "roleId": 2
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

### 5️⃣ 查询用户列表

**接口地址**: `GET /api/v1/system/user/list`

**功能描述**: 分页查询用户列表

**可见性规则**: 非超级管理员查询用户列表时不会返回超级管理员账号；超级管理员可见全部用户。

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| name | String | 否 | 用户名（模糊查询） |
| email | String | 否 | 登录邮箱（模糊查询） |
| page | Integer | 否 | 页码，默认1 |
| size | Integer | 否 | 每页大小，默认10 |
| sort | String | 否 | 排序字段 |
| order | String | 否 | 排序方式 |

**请求示例**:
```bash
curl -X GET "http://localhost:11002/api/v1/system/user/list?name=测试&page=1&size=10"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "total": 100,
    "page": 1,
    "size": 10,
    "rows": [
      {
        "id": 1,
        "email": "user@example.com",
        "name": "测试用户",
        "roleId": 1,
        "roleName": "管理员",
        "isSuperAdmin": false,
        "updateTime": "2024-01-01 12:00:00"
      }
    ]
  }
}
```

---

### 6️⃣ 查询用户详情

**接口地址**: `GET /api/v1/system/user/{id}/view`

**功能描述**: 根据ID查询用户详细信息

**可见性规则**: 非超级管理员不能查看超级管理员账号详情；超级管理员可见全部用户详情。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 用户ID |

**请求示例**:
```bash
curl -X GET http://localhost:11002/api/v1/system/user/1/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "name": "测试用户",
    "roleId": 1,
    "roleName": "管理员",
    "isSuperAdmin": false,
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 7️⃣ 修改密码

**接口地址**: `POST /api/v1/system/user/update-password`

**功能描述**: 当前登录用户修改密码

**业务规则**: 超级管理员本人可以通过该接口修改自己的密码；该限制不同于用户管理中的编辑/删除保护。

**请求参数**:
- Content-Type: `application/json`
- Body: PasswordChangeDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/user/update-password \
  -H "Content-Type: application/json" \
  -d '{
    "password": "newPassword123",
    "oldPassword": "oldPassword123"
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

## 📊 响应码汇总

| 响应码 | 说明 | 触发场景 |
|--------|------|---------|
| 0 | 请求成功 | 操作成功完成 |
| -1 | 未知错误 | 遇到未定义的异常情况 |

---

## 🔐 注意事项

1. **认证授权**: 需要登录认证
2. **密码安全**: 密码修改需要验证旧密码
3. **批量操作**: 批量更新时，ID列表不能为空
4. **内置账号保护**: 超级管理员账号由系统初始化生成，默认账号为 `super@admin.com`，仅超级管理员本人可见，禁止通过用户管理修改或删除
5. **角色分配限制**: 创建或修改普通用户时不能手动分配超级管理员角色

---
