# Menu 菜单接口文档

**基础信息**
- **模块名称**: 菜单管理
- **基础路径**: `/api/v1/system/menu`
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. MenuDto (菜单传输对象)

```json
{
  "id": 1,                    // Integer - 菜单ID（更新时使用，创建时不传）
  "name": "用户管理",         // String - 菜单名称（必填）
  "type": "MENU",            // MenuType - 类型（必填，枚举值：MENU/BUTTON/CATEGORY）
  "route": "/system/user",   // String - 路由（可选）
  "params": "{}",            // String - 参数（可选）
  "createRootPath": false,   // Boolean - 是否创建根目录（策略配置类型菜单有效）
  "parentId": 0,             // Integer - 父级ID（可选，0表示顶级）
  "orderNumber": 1,          // Integer - 目录顺序（可选）
  "level": "FIRST",          // MenuLevel - 目录级别（可选，枚举值：FIRST/SECOND/THIRD）
  "superscript": "NEW",      // String - 角标文字（可选）
  "children": [],            // List<MenuDto> - 子级菜单（可选）
  "source": "SYSTEM"         // String - 来源（可选）
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| id | Integer | 否 | 菜单ID，更新时需传入 |
| name | String | 是 | 菜单名称 |
| type | MenuType | 是 | 类型（MENU/BUTTON/CATEGORY） |
| route | String | 否 | 路由路径 |
| params | String | 否 | 参数，不同类型参数值不同 |
| createRootPath | Boolean | 否 | 是否创建根目录（策略配置类型菜单有效） |
| parentId | Integer | 否 | 父级ID，0表示顶级 |
| orderNumber | Integer | 否 | 目录顺序 |
| level | MenuLevel | 否 | 目录级别（FIRST/SECOND/THIRD） |
| superscript | String | 否 | 角标文字 |
| children | List\<MenuDto\> | 否 | 子级菜单列表 |
| source | String | 否 | 来源标识 |

### 2. MenuVo (菜单视图对象)

```json
{
  "id": 1,                    // Integer - 菜单ID
  "name": "用户管理",         // String - 菜单名称
  "type": "MENU",            // MenuType - 类型
  "typeDescription": "菜单", // String - 类型描述
  "route": "/system/user",   // String - 路由
  "params": "{}",            // String - 参数
  "parentId": 0,             // Integer - 父级ID
  "orderNumber": 1,          // Integer - 目录顺序
  "level": "FIRST",          // MenuLevel - 目录级别
  "superscript": "NEW",      // String - 角标文字
  "isEditable": true,        // Boolean - 是否可编辑
  "updateTime": "2024-01-01 12:00:00", // String - 更新时间
  "children": [],            // List<MenuVo> - 子级菜单
  "source": "SYSTEM"         // String - 来源
}
```

### 3. MenuSearchDto (菜单搜索传输对象)

```json
{
  "name": "用户",            // String - 菜单名称（可选）
  "route": "/system",       // String - 路由（可选）
  "page": 1,                 // Integer - 页码（继承自SortPageDto）
  "size": 10,                // Integer - 每页大小（继承自SortPageDto）
  "sort": "updateTime",      // String - 排序字段（继承自SortPageDto）
  "order": "desc"            // String - 排序方式（继承自SortPageDto）
}
```

### 4. MenuOrderRowDto (菜单排序传输对象)

```json
{
  "ids": "1,2,3",            // String - 排序ID列表
  "rows": []                 // List<MenuDto> - 数据集合
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
| 1 | POST | `/api/v1/system/menu/add` | 创建菜单 | 创建新的菜单 |
| 2 | DELETE | `/api/v1/system/menu/{id}` | 删除菜单 | 根据ID删除单个菜单 |
| 3 | DELETE | `/api/v1/system/menu/bulk/{ids}` | 批量删除菜单 | 批量删除多个菜单 |
| 4 | POST | `/api/v1/system/menu/{id}/update` | 更新菜单 | 根据ID更新菜单信息 |
| 5 | POST | `/api/v1/system/menu/update-order` | 更新菜单顺序 | 更新菜单排序 |
| 6 | POST | `/api/v1/system/menu/{ids}/bulk-update` | 批量更新菜单 | 批量更新多个菜单 |
| 7 | GET | `/api/v1/system/menu/list` | 查询菜单列表 | 分页查询菜单列表 |
| 8 | GET | `/api/v1/system/menu/{id}/view` | 查询菜单详情 | 根据ID查询菜单详细信息 |
| 9 | GET | `/api/v1/system/menu/parent-menu/list` | 获取一级目录列表 | 获取所有一级目录用于下拉选择 |
| 10 | GET | `/api/v1/system/menu/type/list` | 获取类型列表 | 获取菜单类型枚举值 |
| 11 | GET | `/api/v1/system/menu/level/list` | 获取级别列表 | 获取菜单级别枚举值 |

---

## 🔌 接口详情

### 1️⃣ 创建菜单

**接口地址**: `POST /api/v1/system/menu/add`

**功能描述**: 创建新的菜单

**请求参数**:
- Content-Type: `application/json`
- Body: MenuDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/menu/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试菜单",
    "type": "MENU",
    "route": "/test/menu",
    "parentId": 0,
    "orderNumber": 1,
    "level": "FIRST"
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

### 2️⃣ 删除菜单

**接口地址**: `DELETE /api/v1/system/menu/{id}`

**功能描述**: 根据ID删除单个菜单

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 菜单ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:11002/api/v1/system/menu/1
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

### 3️⃣ 批量删除菜单

**接口地址**: `DELETE /api/v1/system/menu/bulk/{ids}`

**功能描述**: 批量删除多个菜单

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<Long\> | 是 | 菜单ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:11002/api/v1/system/menu/bulk/1,2,3
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

### 4️⃣ 更新菜单

**接口地址**: `POST /api/v1/system/menu/{id}/update`

**功能描述**: 根据ID更新菜单信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 菜单ID |

**请求参数**:
- Content-Type: `application/json`
- Body: MenuDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/menu/1/update \
  -H "Content-Type: application/json" \
  -d '{
    "name": "更新后的菜单名称",
    "route": "/updated/route"
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

### 5️⃣ 更新菜单顺序

**接口地址**: `POST /api/v1/system/menu/update-order`

**功能描述**: 更新菜单排序顺序

**请求参数**:
- Content-Type: `application/json`
- Body: MenuOrderRowDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/menu/update-order \
  -H "Content-Type: application/json" \
  -d '{
    "ids": "1,2,3",
    "rows": []
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

### 6️⃣ 批量更新菜单

**接口地址**: `POST /api/v1/system/menu/{ids}/bulk-update`

**功能描述**: 批量更新多个菜单

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | Long[] | 是 | 菜单ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: MenuDto

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/menu/1,2,3/bulk-update \
  -H "Content-Type: application/json" \
  -d '{
    "parentId": 1
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

### 7️⃣ 查询菜单列表

**接口地址**: `GET /api/v1/system/menu/list`

**功能描述**: 分页查询菜单列表

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| name | String | 否 | 菜单名称（模糊查询） |
| route | String | 否 | 路由（模糊查询） |
| page | Integer | 否 | 页码，默认1 |
| size | Integer | 否 | 每页大小，默认10 |
| sort | String | 否 | 排序字段 |
| order | String | 否 | 排序方式 |

**请求示例**:
```bash
curl -X GET "http://localhost:11002/api/v1/system/menu/list?name=用户&page=1&size=10"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "total": 50,
    "page": 1,
    "size": 10,
    "rows": [
      {
        "id": 1,
        "name": "用户管理",
        "type": "MENU",
        "typeDescription": "菜单",
        "route": "/system/user",
        "parentId": 0,
        "orderNumber": 1,
        "level": "FIRST",
        "isEditable": true,
        "updateTime": "2024-01-01 12:00:00"
      }
    ]
  }
}
```

---

### 8️⃣ 查询菜单详情

**接口地址**: `GET /api/v1/system/menu/{id}/view`

**功能描述**: 根据ID查询菜单详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 菜单ID |

**请求示例**:
```bash
curl -X GET http://localhost:11002/api/v1/system/menu/1/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "name": "用户管理",
    "type": "MENU",
    "typeDescription": "菜单",
    "route": "/system/user",
    "parentId": 0,
    "orderNumber": 1,
    "level": "FIRST",
    "isEditable": true,
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 9️⃣ 获取一级目录列表

**接口地址**: `GET /api/v1/system/menu/parent-menu/list`

**功能描述**: 获取所有一级目录，用于下拉选择框

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:11002/api/v1/system/menu/parent-menu/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {
        "label": "系统管理",
        "value": "1"
      },
      {
        "label": "业务管理",
        "value": "2"
      }
    ]
  }
}
```

---

### 🔟 获取类型列表

**接口地址**: `GET /api/v1/system/menu/type/list`

**功能描述**: 获取菜单类型枚举值列表

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:11002/api/v1/system/menu/type/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {
        "label": "菜单",
        "value": "MENU"
      },
      {
        "label": "按钮",
        "value": "BUTTON"
      },
      {
        "label": "分类",
        "value": "CATEGORY"
      }
    ]
  }
}
```

---

### 1️⃣1️⃣ 获取级别列表

**接口地址**: `GET /api/v1/system/menu/level/list`

**功能描述**: 获取菜单级别枚举值列表

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:11002/api/v1/system/menu/level/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {
        "label": "一级",
        "value": "FIRST"
      },
      {
        "label": "二级",
        "value": "SECOND"
      },
      {
        "label": "三级",
        "value": "THIRD"
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
2. **菜单层级**: 支持三级菜单结构（FIRST/SECOND/THIRD）
3. **菜单类型**: 支持菜单(MENU)、按钮(BUTTON)、分类(CATEGORY)三种类型
4. **批量操作**: 批量删除/更新时，ID列表不能为空

---