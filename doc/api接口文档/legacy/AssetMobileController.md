# AssetMobile 移动设备资产接口文档

**基础信息**
- **模块名称**: 移动设备资产管理
- **基础路径**: `/api/v1/asset/mobile`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. AssetMobileDto (移动设备资产传输对象)

```json
{
  "id": "mobile-001",         // String - 移动设备资产ID（更新时使用）
  "brand": "华为",            // String - 品牌
  "model": "Mate 60",         // String - 型号
  "os": "HarmonyOS",          // String - 操作系统
  "osVersion": "4.0",         // String - 操作系统版本
  "screenResolution": "1080x2400", // String - 屏幕分辨率
  "cpuModel": "麒麟9000S",    // String - CPU型号
  "description": "描述信息",   // String - 描述信息
  "labels": ["label1"]        // List - 标签列表
}
```

### 2. AssetMobileVo (移动设备资产视图对象)

```json
{
  "id": "mobile-001",         // String - 移动设备资产ID
  "brand": "华为",            // String - 品牌
  "model": "Mate 60",         // String - 型号
  "os": "HarmonyOS",          // String - 操作系统
  "osVersion": "4.0",         // String - 操作系统版本
  "screenResolution": "1080x2400", // String - 屏幕分辨率
  "cpuModel": "麒麟9000S",    // String - CPU型号
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
| 1 | POST | `/api/v1/asset/mobile/add` | 添加移动设备资产 | 添加新的移动设备资产 |
| 2 | DELETE | `/api/v1/asset/mobile/{id}` | 删除移动设备资产 | 根据ID删除单个移动设备资产 |
| 3 | DELETE | `/api/v1/asset/mobile/bulk/{ids}` | 批量删除移动设备资产 | 批量删除多个移动设备资产 |
| 4 | POST | `/api/v1/asset/mobile/{id}/update` | 更新移动设备资产 | 根据ID更新移动设备资产信息 |
| 5 | POST | `/api/v1/asset/mobile/{ids}/bulk_update` | 批量更新移动设备资产 | 批量更新多个移动设备资产 |
| 6 | GET | `/api/v1/asset/mobile/list` | 查询移动设备资产列表 | 分页查询移动设备资产列表 |
| 7 | GET | `/api/v1/asset/mobile/{id}/view` | 查询移动设备资产详情 | 根据ID查询移动设备资产详细信息 |
| 8 | GET | `/api/v1/asset/mobile/label/list` | 获取标签列表 | 获取所有不重复的标签 |
| 9 | GET | `/api/v1/asset/mobile/auto_complete/id` | ID自动补全 | 根据关键词获取相似ID |
| 10 | GET | `/api/v1/asset/mobile/auto_complete/brand` | 品牌自动补全 | 根据关键词获取相似品牌 |
| 11 | GET | `/api/v1/asset/mobile/auto_complete/model` | 型号自动补全 | 根据关键词获取相似型号 |
| 12 | GET | `/api/v1/asset/mobile/auto_complete/os` | 操作系统自动补全 | 根据关键词获取相似操作系统 |
| 13 | GET | `/api/v1/asset/mobile/auto_complete/os_version` | 操作系统版本自动补全 | 根据关键词获取相似操作系统版本 |
| 14 | GET | `/api/v1/asset/mobile/auto_complete/screen_resolution` | 屏幕分辨率自动补全 | 根据关键词获取相似屏幕分辨率 |
| 15 | GET | `/api/v1/asset/mobile/auto_complete/cpu_model` | CPU型号自动补全 | 根据关键词获取相似CPU型号 |

---

## 🔌 接口详情

### 1️⃣ 添加移动设备资产

**接口地址**: `POST /api/v1/asset/mobile/add`

**功能描述**: 添加新的移动设备资产

**请求参数**:
- Content-Type: `application/json`
- Body: AssetMobileDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/mobile/add \
  -H "Content-Type: application/json" \
  -d '{
    "brand": "华为",
    "model": "Mate 60",
    "os": "HarmonyOS",
    "osVersion": "4.0"
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

### 2️⃣ 删除移动设备资产

**接口地址**: `DELETE /api/v1/asset/mobile/{id}`

**功能描述**: 根据ID删除单个移动设备资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 移动设备资产ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/mobile/mobile-001
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

### 3️⃣ 批量删除移动设备资产

**接口地址**: `DELETE /api/v1/asset/mobile/bulk/{ids}`

**功能描述**: 批量删除多个移动设备资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<String\> | 是 | 移动设备资产ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/mobile/bulk/mobile-001,mobile-002
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

### 4️⃣ 更新移动设备资产

**接口地址**: `POST /api/v1/asset/mobile/{id}/update`

**功能描述**: 根据ID更新移动设备资产信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 移动设备资产ID |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetMobileDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/mobile/mobile-001/update \
  -H "Content-Type: application/json" \
  -d '{
    "model": "Mate 60 Pro"
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

### 5️⃣ 批量更新移动设备资产

**接口地址**: `POST /api/v1/asset/mobile/{ids}/bulk_update`

**功能描述**: 批量更新多个移动设备资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | String[] | 是 | 移动设备资产ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetMobileDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/mobile/mobile-001,mobile-002/bulk_update \
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

### 6️⃣ 查询移动设备资产列表

**接口地址**: `GET /api/v1/asset/mobile/list`

**功能描述**: 分页查询移动设备资产列表

**请求参数**:
- Query: AssetMobileSearchDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/mobile/list?page=1&size=10"
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

### 7️⃣ 查询移动设备资产详情

**接口地址**: `GET /api/v1/asset/mobile/{id}/view`

**功能描述**: 根据ID查询移动设备资产详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 移动设备资产ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/mobile/mobile-001/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "mobile-001",
    "brand": "华为",
    "model": "Mate 60",
    "os": "HarmonyOS",
    "osVersion": "4.0",
    "screenResolution": "1080x2400",
    "cpuModel": "麒麟9000S",
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 8️⃣ 获取标签列表

**接口地址**: `GET /api/v1/asset/mobile/label/list`

**功能描述**: 获取所有不重复的标签

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/mobile/label/list
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

**接口地址**: `GET /api/v1/asset/mobile/auto_complete/id?term=xxx`

**功能描述**: 根据关键词获取相似移动设备资产ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/mobile/auto_complete/id?term=mobile"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "mobile-001", "value": "mobile-001"},
      {"label": "mobile-002", "value": "mobile-002"}
    ]
  }
}
```

---

### 🔟 品牌自动补全

**接口地址**: `GET /api/v1/asset/mobile/auto_complete/brand?term=xxx`

**功能描述**: 根据关键词获取相似品牌

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/mobile/auto_complete/brand?term=华"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "华为", "value": "华为"}
    ]
  }
}
```

---

### 1️⃣1️⃣ 型号自动补全

**接口地址**: `GET /api/v1/asset/mobile/auto_complete/model?term=xxx`

**功能描述**: 根据关键词获取相似型号

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/mobile/auto_complete/model?term=Mate"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "Mate 60", "value": "Mate 60"}
    ]
  }
}
```

---

### 1️⃣2️⃣ 操作系统自动补全

**接口地址**: `GET /api/v1/asset/mobile/auto_complete/os?term=xxx`

**功能描述**: 根据关键词获取相似操作系统

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/mobile/auto_complete/os?term=Harm"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "HarmonyOS", "value": "HarmonyOS"}
    ]
  }
}
```

---

### 1️⃣3️⃣ 操作系统版本自动补全

**接口地址**: `GET /api/v1/asset/mobile/auto_complete/os_version?term=xxx`

**功能描述**: 根据关键词获取相似操作系统版本

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/mobile/auto_complete/os_version?term=4"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "4.0", "value": "4.0"}
    ]
  }
}
```

---

### 1️⃣4️⃣ 屏幕分辨率自动补全

**接口地址**: `GET /api/v1/asset/mobile/auto_complete/screen_resolution?term=xxx`

**功能描述**: 根据关键词获取相似屏幕分辨率

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/mobile/auto_complete/screen_resolution?term=1080"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "1080x2400", "value": "1080x2400"}
    ]
  }
}
```

---

### 1️⃣5️⃣ CPU型号自动补全

**接口地址**: `GET /api/v1/asset/mobile/auto_complete/cpu_model?term=xxx`

**功能描述**: 根据关键词获取相似CPU型号

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/mobile/auto_complete/cpu_model?term=麒麟"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "麒麟9000S", "value": "麒麟9000S"}
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