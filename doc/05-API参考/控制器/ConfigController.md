# ConfigController API 接口文档

## 基础信息

| 属性 | 值 |
| :--- | :--- |
| **模块** | policy（策略配置） |
| **Controller** | ConfigController |
| **基础路径** | `/api/v1/config/{type}` |
| **描述** | 策略配置文件管理，支持文件树查看、读取、修改、应用、添加、重命名、删除等操作 |

---

## 数据模型定义

### 3.1 请求模型

#### ConfigDto

| 字段名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | Integer | 否 | ID（备用） | 1 |
| `originalFileName` | String | 否 | 原文件名（更新操作才用） | "old_config.json" |
| `fileName` | String | 是 | 文件名（带路径） | "config/app.json" |
| `text` | String | 是 | 文件内容 | "{\"key\": \"value\"}" |
| `commitMsg` | String | 否 | 提交信息 | "更新配置" |

### 3.2 响应模型

#### ConfigVo

| 字段名 | 类型 | 含义 | 示例 |
| :--- | :--- | :--- | :--- |
| `id` | Long | 文件ID | 1 |
| `parentId` | Long | 父文件夹ID | 0 |
| `fileName` | String | 文件名(文件夹名) | "app.json" |
| `size` | Long | 文件大小 | 1024 |
| `path` | String | 文件路径 | "config/app.json" |
| `depth` | Integer | 文件级数 | 2 |
| `isDir` | Boolean | 是否是文件夹 | false |
| `modifiable` | Boolean | 是否可修改 | true |
| `nodes` | List\<ConfigVo\> | 文件夹下的文件列表 | [] |

---

## 接口列表

| API 路径 | HTTP 方法 | 所属文件 | 功能描述 |
| :--- | :--- | :--- | :--- |
| `/api/v1/config/{type}/tree` | GET | ConfigController.java | 获取配置文件树 |
| `/api/v1/config/{type}/schema` | GET | ConfigController.java | 获取文件schema |
| `/api/v1/config/{type}/read` | GET | ConfigController.java | 读取文件内容 |
| `/api/v1/config/{type}/get` | GET | ConfigController.java | 获取配置（不包装） |
| `/api/v1/config/{type}/modify` | POST | ConfigController.java | 修改文件 |
| `/api/v1/config/{type}/apply` | POST | ConfigController.java | 应用配置（保存并执行策略） |
| `/api/v1/config/{type}/add` | POST | ConfigController.java | 添加文件 |
| `/api/v1/config/{type}/rename` | POST | ConfigController.java | 修改文件名 |
| `/api/v1/config/{type}/delete` | POST | ConfigController.java | 删除文件 |

---

## 接口详细描述

### 5.1 获取配置文件树

- **路径**: `GET /api/v1/config/{type}/tree`
- **功能**: 获取指定类型的配置文件树结构

**路径参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `type` | String | 是 | 配置类型 | "policy" |

**成功响应**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 1,
            "parentId": 0,
            "fileName": "config",
            "size": 0,
            "path": "config",
            "depth": 1,
            "isDir": true,
            "modifiable": false,
            "nodes": [
                {
                    "id": 2,
                    "parentId": 1,
                    "fileName": "app.json",
                    "size": 1024,
                    "path": "config/app.json",
                    "depth": 2,
                    "isDir": false,
                    "modifiable": true,
                    "nodes": []
                }
            ]
        }
    ]
}
```

---

### 5.2 获取文件schema

- **路径**: `GET /api/v1/config/{type}/schema?file_name={fileName}`
- **功能**: 获取配置文件的schema定义

**路径参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `type` | String | 是 | 配置类型 | "policy" |

**查询参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `file_name` | String | 是 | 文件全名，带路径 | "config/app.json" |

**成功响应**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "type": "object",
        "properties": {}
    }
}
```

---

### 5.3 读取文件内容

- **路径**: `GET /api/v1/config/{type}/read?file_name={fileName}`
- **功能**: 读取配置文件内容

**路径参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `type` | String | 是 | 配置类型 | "policy" |

**查询参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `file_name` | String | 是 | 文件全名，带路径 | "config/app.json" |

**成功响应**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": "{\"key\": \"value\"}"
}
```

**失败响应**

```json
{
    "code": 403,
    "message": "无权限",
    "data": null
}
```

---

### 5.4 获取配置（不包装）

- **路径**: `GET /api/v1/config/{type}/get?file_name={fileName}`
- **功能**: 获取配置文件内容，返回原始内容不包含包装数据

**路径参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `type` | String | 是 | 配置类型 | "policy" |

**查询参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `file_name` | String | 是 | 文件全名，带路径 | "config/app.json" |

**成功响应**

```json
{
    "key": "value"
}
```

**失败响应**

```json
{
    "code": 403,
    "message": "无权限",
    "data": null
}
```

---

### 5.5 修改文件

- **路径**: `POST /api/v1/config/{type}/modify`
- **功能**: 修改配置文件内容

**路径参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `type` | String | 是 | 配置类型 | "policy" |

**请求体**

```json
{
    "fileName": "config/app.json",
    "text": "{\"key\": \"new_value\"}"
}
```

**成功响应**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": null
}
```

**失败响应**

```json
{
    "code": 403,
    "message": "无权限",
    "data": null
}
```

---

### 5.6 应用配置

- **路径**: `POST /api/v1/config/{type}/apply`
- **功能**: 应用配置（先保存文件，后执行策略）

**路径参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `type` | String | 是 | 配置类型 | "policy" |

**请求体**

```json
{
    "fileName": "config/app.json",
    "text": "{\"key\": \"new_value\"}"
}
```

**成功响应**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": null
}
```

---

### 5.7 添加文件

- **路径**: `POST /api/v1/config/{type}/add`
- **功能**: 添加新的配置文件

**路径参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `type` | String | 是 | 配置类型 | "policy" |

**请求体**

```json
{
    "fileName": "config/new_file.json",
    "text": "{\"key\": \"value\"}"
}
```

**成功响应**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": null
}
```

**失败响应**

```json
{
    "code": 403,
    "message": "无权限",
    "data": null
}
```

---

### 5.8 修改文件名

- **路径**: `POST /api/v1/config/{type}/rename`
- **功能**: 重命名配置文件

**路径参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `type` | String | 是 | 配置类型 | "policy" |

**请求体**

```json
{
    "originalFileName": "config/old_name.json",
    "fileName": "config/new_name.json"
}
```

**成功响应**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": null
}
```

---

### 5.9 删除文件

- **路径**: `POST /api/v1/config/{type}/delete`
- **功能**: 删除配置文件

**路径参数**

| 参数名 | 类型 | 必填 | 含义 | 示例 |
| :--- | :--- | :--- | :--- | :--- |
| `type` | String | 是 | 配置类型 | "policy" |

**请求体**

```json
{
    "fileName": "config/app.json"
}
```

**成功响应**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": null
}
```

---

## 通用响应格式

所有接口返回统一响应结构：

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {}
}
```

| 字段 | 类型 | 含义 |
| :--- | :--- | :--- |
| `code` | Integer | 状态码，200表示成功，其他表示失败 |
| `message` | String | 提示信息 |
| `data` | Object | 返回的数据，可为任意类型或null |