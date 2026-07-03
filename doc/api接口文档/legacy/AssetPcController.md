# AssetPc PC资产接口文档

**基础信息**
- **模块名称**: PC资产管理
- **基础路径**: `/api/v1/asset/pc`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. AssetPcDto (PC资产传输对象)

```json
{
  "id": "pc-001",             // String - PC资产ID（更新时使用）
  "manufacturer": "联想",      // String - 制造商
  "model": "ThinkPad X1",     // String - 型号
  "architecture": "x86_64",   // String - 架构
  "systemName": "Windows",    // String - 系统名称
  "systemVersion": "10",      // String - 系统版本
  "cpuModel": "Intel i7",     // String - CPU型号
  "gpuModel": "NVIDIA RTX",   // String - 显卡型号
  "description": "描述信息",   // String - 描述信息
  "labels": ["label1"]        // List - 标签列表
}
```

### 2. AssetPcVo (PC资产视图对象)

```json
{
  "id": "pc-001",             // String - PC资产ID
  "manufacturer": "联想",      // String - 制造商
  "model": "ThinkPad X1",     // String - 型号
  "architecture": "x86_64",   // String - 架构
  "systemName": "Windows",    // String - 系统名称
  "systemVersion": "10",      // String - 系统版本
  "cpuModel": "Intel i7",     // String - CPU型号
  "gpuModel": "NVIDIA RTX",   // String - 显卡型号
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
| 1 | POST | `/api/v1/asset/pc/add` | 添加PC资产 | 添加新的PC资产 |
| 2 | DELETE | `/api/v1/asset/pc/{id}` | 删除PC资产 | 根据ID删除单个PC资产 |
| 3 | DELETE | `/api/v1/asset/pc/bulk/{ids}` | 批量删除PC资产 | 批量删除多个PC资产 |
| 4 | POST | `/api/v1/asset/pc/{id}/update` | 更新PC资产 | 根据ID更新PC资产信息 |
| 5 | POST | `/api/v1/asset/pc/{ids}/bulk_update` | 批量更新PC资产 | 批量更新多个PC资产 |
| 6 | GET | `/api/v1/asset/pc/list` | 查询PC资产列表 | 分页查询PC资产列表 |
| 7 | GET | `/api/v1/asset/pc/{id}/view` | 查询PC资产详情 | 根据ID查询PC资产详细信息 |
| 8 | GET | `/api/v1/asset/pc/label/list` | 获取标签列表 | 获取所有不重复的标签 |
| 9 | GET | `/api/v1/asset/pc/auto_complete/id` | ID自动补全 | 根据关键词获取相似ID |
| 10 | GET | `/api/v1/asset/pc/auto_complete/manufacturer` | 制造商自动补全 | 根据关键词获取相似制造商 |
| 11 | GET | `/api/v1/asset/pc/auto_complete/model` | 型号自动补全 | 根据关键词获取相似型号 |
| 12 | GET | `/api/v1/asset/pc/auto_complete/architecture` | 架构自动补全 | 根据关键词获取相似架构 |
| 13 | GET | `/api/v1/asset/pc/auto_complete/system_name` | 系统名称自动补全 | 根据关键词获取相似系统名称 |
| 14 | GET | `/api/v1/asset/pc/auto_complete/system_version` | 系统版本自动补全 | 根据关键词获取相似系统版本 |
| 15 | GET | `/api/v1/asset/pc/auto_complete/cpu_model` | CPU型号自动补全 | 根据关键词获取相似CPU型号 |
| 16 | GET | `/api/v1/asset/pc/auto_complete/gpu_model` | 显卡型号自动补全 | 根据关键词获取相似显卡型号 |

---

## 🔌 接口详情

### 1️⃣ 添加PC资产

**接口地址**: `POST /api/v1/asset/pc/add`

**功能描述**: 添加新的PC资产

**请求参数**:
- Content-Type: `application/json`
- Body: AssetPcDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/pc/add \
  -H "Content-Type: application/json" \
  -d '{
    "manufacturer": "联想",
    "model": "ThinkPad X1",
    "architecture": "x86_64",
    "systemName": "Windows",
    "systemVersion": "10"
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

### 2️⃣ 删除PC资产

**接口地址**: `DELETE /api/v1/asset/pc/{id}`

**功能描述**: 根据ID删除单个PC资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | PC资产ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/pc/pc-001
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

### 3️⃣ 批量删除PC资产

**接口地址**: `DELETE /api/v1/asset/pc/bulk/{ids}`

**功能描述**: 批量删除多个PC资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<String\> | 是 | PC资产ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/pc/bulk/pc-001,pc-002
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

### 4️⃣ 更新PC资产

**接口地址**: `POST /api/v1/asset/pc/{id}/update`

**功能描述**: 根据ID更新PC资产信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | PC资产ID |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetPcDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/pc/pc-001/update \
  -H "Content-Type: application/json" \
  -d '{
    "model": "ThinkPad X1 Carbon"
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

### 5️⃣ 批量更新PC资产

**接口地址**: `POST /api/v1/asset/pc/{ids}/bulk_update`

**功能描述**: 批量更新多个PC资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | String[] | 是 | PC资产ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetPcDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/pc/pc-001,pc-002/bulk_update \
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

### 6️⃣ 查询PC资产列表

**接口地址**: `GET /api/v1/asset/pc/list`

**功能描述**: 分页查询PC资产列表

**请求参数**:
- Query: AssetPcSearchDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/pc/list?page=1&size=10"
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

### 7️⃣ 查询PC资产详情

**接口地址**: `GET /api/v1/asset/pc/{id}/view`

**功能描述**: 根据ID查询PC资产详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | PC资产ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/pc/pc-001/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "pc-001",
    "manufacturer": "联想",
    "model": "ThinkPad X1",
    "architecture": "x86_64",
    "systemName": "Windows",
    "systemVersion": "10",
    "cpuModel": "Intel i7",
    "gpuModel": "NVIDIA RTX",
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 8️⃣ 获取标签列表

**接口地址**: `GET /api/v1/asset/pc/label/list`

**功能描述**: 获取所有不重复的标签

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/pc/label/list
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

**接口地址**: `GET /api/v1/asset/pc/auto_complete/id?term=xxx`

**功能描述**: 根据关键词获取相似PC资产ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/pc/auto_complete/id?term=pc"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "pc-001", "value": "pc-001"},
      {"label": "pc-002", "value": "pc-002"}
    ]
  }
}
```

---

### 🔟 制造商自动补全

**接口地址**: `GET /api/v1/asset/pc/auto_complete/manufacturer?term=xxx`

**功能描述**: 根据关键词获取相似制造商

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/pc/auto_complete/manufacturer?term=联"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "联想", "value": "联想"}
    ]
  }
}
```

---

### 1️⃣1️⃣ 型号自动补全

**接口地址**: `GET /api/v1/asset/pc/auto_complete/model?term=xxx`

**功能描述**: 根据关键词获取相似型号

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/pc/auto_complete/model?term=Think"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "ThinkPad X1", "value": "ThinkPad X1"}
    ]
  }
}
```

---

### 1️⃣2️⃣ 架构自动补全

**接口地址**: `GET /api/v1/asset/pc/auto_complete/architecture?term=xxx`

**功能描述**: 根据关键词获取相似架构

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/pc/auto_complete/architecture?term=x86"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "x86_64", "value": "x86_64"}
    ]
  }
}
```

---

### 1️⃣3️⃣ 系统名称自动补全

**接口地址**: `GET /api/v1/asset/pc/auto_complete/system_name?term=xxx`

**功能描述**: 根据关键词获取相似系统名称

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/pc/auto_complete/system_name?term=Win"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "Windows", "value": "Windows"}
    ]
  }
}
```

---

### 1️⃣4️⃣ 系统版本自动补全

**接口地址**: `GET /api/v1/asset/pc/auto_complete/system_version?term=xxx`

**功能描述**: 根据关键词获取相似系统版本

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/pc/auto_complete/system_version?term=10"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "10", "value": "10"}
    ]
  }
}
```

---

### 1️⃣5️⃣ CPU型号自动补全

**接口地址**: `GET /api/v1/asset/pc/auto_complete/cpu_model?term=xxx`

**功能描述**: 根据关键词获取相似CPU型号

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/pc/auto_complete/cpu_model?term=Intel"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "Intel i7", "value": "Intel i7"}
    ]
  }
}
```

---

### 1️⃣6️⃣ 显卡型号自动补全

**接口地址**: `GET /api/v1/asset/pc/auto_complete/gpu_model?term=xxx`

**功能描述**: 根据关键词获取相似显卡型号

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/pc/auto_complete/gpu_model?term=NVIDIA"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "NVIDIA RTX", "value": "NVIDIA RTX"}
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