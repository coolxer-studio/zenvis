# AssetFile 文件资产接口文档

**基础信息**
- **模块名称**: 文件资产管理
- **基础路径**: `/api/v1/asset/file`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. AssetFileDto (文件资产传输对象)

```json
{
  "id": "file-001",           // String - 文件资产ID（更新时使用）
  "fileName": "文件名",        // String - 文件名
  "fileType": "txt",          // String - 文件类型
  "filePath": "/etc/config.txt", // String - 文件路径
  "fileSize": 1024,           // Long - 文件大小
  "description": "描述信息",   // String - 描述信息
  "labels": ["label1"]        // List - 标签列表
}
```

### 2. AssetFileVo (文件资产视图对象)

```json
{
  "id": "file-001",           // String - 文件资产ID
  "fileName": "文件名",        // String - 文件名
  "fileType": "txt",          // String - 文件类型
  "filePath": "/etc/config.txt", // String - 文件路径
  "fileSize": 1024,           // Long - 文件大小
  "description": "描述信息",   // String - 描述信息
  "labels": ["label1"],       // List - 标签列表
  "updateTime": "2024-01-01 12:00:00"  // String - 更新时间
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
| 1 | POST | `/api/v1/asset/file/add` | 添加文件资产 | 添加新的文件资产 |
| 2 | DELETE | `/api/v1/asset/file/{id}` | 删除文件资产 | 根据ID删除单个文件资产 |
| 3 | DELETE | `/api/v1/asset/file/bulk/{ids}` | 批量删除文件资产 | 批量删除多个文件资产 |
| 4 | POST | `/api/v1/asset/file/{id}/update` | 更新文件资产 | 根据ID更新文件资产信息 |
| 5 | POST | `/api/v1/asset/file/{ids}/bulk_update` | 批量更新文件资产 | 批量更新多个文件资产 |
| 6 | GET | `/api/v1/asset/file/list` | 查询文件资产列表 | 分页查询文件资产列表 |
| 7 | GET | `/api/v1/asset/file/{id}/view` | 查询文件资产详情 | 根据ID查询文件资产详细信息 |
| 8 | GET | `/api/v1/asset/file/label/list` | 获取标签列表 | 获取所有不重复的标签 |
| 9 | GET | `/api/v1/asset/file/auto_complete/id` | ID自动补全 | 根据关键词获取相似ID |
| 10 | GET | `/api/v1/asset/file/auto_complete/file_name` | 文件名自动补全 | 根据关键词获取相似文件名 |
| 11 | GET | `/api/v1/asset/file/auto_complete/file_type` | 文件类型自动补全 | 根据关键词获取相似文件类型 |
| 12 | GET | `/api/v1/asset/file/auto_complete/file_path` | 文件路径自动补全 | 根据关键词获取相似文件路径 |

---

## 🔌 接口详情

### 1️⃣ 添加文件资产

**接口地址**: `POST /api/v1/asset/file/add`

**功能描述**: 添加新的文件资产

**请求参数**:
- Content-Type: `application/json`
- Body: AssetFileDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/file/add \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "config.txt",
    "fileType": "txt",
    "filePath": "/etc/config.txt",
    "fileSize": 1024
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

### 2️⃣ 删除文件资产

**接口地址**: `DELETE /api/v1/asset/file/{id}`

**功能描述**: 根据ID删除单个文件资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 文件资产ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/file/file-001
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

### 3️⃣ 批量删除文件资产

**接口地址**: `DELETE /api/v1/asset/file/bulk/{ids}`

**功能描述**: 批量删除多个文件资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<String\> | 是 | 文件资产ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/file/bulk/file-001,file-002
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

### 4️⃣ 更新文件资产

**接口地址**: `POST /api/v1/asset/file/{id}/update`

**功能描述**: 根据ID更新文件资产信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 文件资产ID |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetFileDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/file/file-001/update \
  -H "Content-Type: application/json" \
  -d '{
    "fileSize": 2048
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

### 5️⃣ 批量更新文件资产

**接口地址**: `POST /api/v1/asset/file/{ids}/bulk_update`

**功能描述**: 批量更新多个文件资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | String[] | 是 | 文件资产ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetFileDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/file/file-001,file-002/bulk_update \
  -H "Content-Type: application/json" \
  -d '{
    "description": "批量更新描述"
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

### 6️⃣ 查询文件资产列表

**接口地址**: `GET /api/v1/asset/file/list`

**功能描述**: 分页查询文件资产列表

**请求参数**:
- Query: AssetFileSearchDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/file/list?page=1&size=10"
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

### 7️⃣ 查询文件资产详情

**接口地址**: `GET /api/v1/asset/file/{id}/view`

**功能描述**: 根据ID查询文件资产详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 文件资产ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/file/file-001/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "file-001",
    "fileName": "config.txt",
    "fileType": "txt",
    "filePath": "/etc/config.txt",
    "fileSize": 1024,
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 8️⃣ 获取标签列表

**接口地址**: `GET /api/v1/asset/file/label/list`

**功能描述**: 获取所有不重复的标签

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/file/label/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "label1", "value": "label1"},
      {"label": "label2", "value": "label2"}
    ]
  }
}
```

---

### 9️⃣ ID自动补全

**接口地址**: `GET /api/v1/asset/file/auto_complete/id?term=xxx`

**功能描述**: 根据关键词获取相似文件资产ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/file/auto_complete/id?term=file"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "file-001", "value": "file-001"},
      {"label": "file-002", "value": "file-002"}
    ]
  }
}
```

---

### 🔟 文件名自动补全

**接口地址**: `GET /api/v1/asset/file/auto_complete/file_name?term=xxx`

**功能描述**: 根据关键词获取相似文件名

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/file/auto_complete/file_name?term=config"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "config.txt", "value": "config.txt"}
    ]
  }
}
```

---

### 1️⃣1️⃣ 文件类型自动补全

**接口地址**: `GET /api/v1/asset/file/auto_complete/file_type?term=xxx`

**功能描述**: 根据关键词获取相似文件类型

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/file/auto_complete/file_type?term=txt"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "txt", "value": "txt"}
    ]
  }
}
```

---

### 1️⃣2️⃣ 文件路径自动补全

**接口地址**: `GET /api/v1/asset/file/auto_complete/file_path?term=xxx`

**功能描述**: 根据关键词获取相似文件路径

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/file/auto_complete/file_path?term=/etc"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "/etc/config.txt", "value": "/etc/config.txt"}
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
2. **批量操作**: 批量删除/更新时，ID列表不能为空
3. **自动补全**: term参数为可选，不传则返回全部匹配项