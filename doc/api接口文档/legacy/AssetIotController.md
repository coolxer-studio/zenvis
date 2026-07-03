# AssetIot IoT设备资产接口文档

**基础信息**
- **模块名称**: IoT设备资产管理
- **基础路径**: `/api/v1/asset/iot`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. AssetIotDto (IoT设备资产传输对象)

```json
{
  "id": "iot-001",            // String - IoT设备资产ID（更新时使用）
  "deviceName": "设备名称",     // String - 设备名称
  "deviceType": "传感器",       // String - 设备类型
  "manufacturer": "华为",      // String - 制造商
  "model": "HS100",           // String - 型号
  "protocol": "MQTT",         // String - 协议
  "description": "描述信息",   // String - 描述信息
  "labels": ["label1"]        // List - 标签列表
}
```

### 2. AssetIotVo (IoT设备资产视图对象)

```json
{
  "id": "iot-001",            // String - IoT设备资产ID
  "deviceName": "设备名称",     // String - 设备名称
  "deviceType": "传感器",       // String - 设备类型
  "manufacturer": "华为",      // String - 制造商
  "model": "HS100",           // String - 型号
  "protocol": "MQTT",         // String - 协议
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
| 1 | POST | `/api/v1/asset/iot/add` | 添加IoT设备资产 | 添加新的IoT设备资产 |
| 2 | DELETE | `/api/v1/asset/iot/{id}` | 删除IoT设备资产 | 根据ID删除单个IoT设备资产 |
| 3 | DELETE | `/api/v1/asset/iot/bulk/{ids}` | 批量删除IoT设备资产 | 批量删除多个IoT设备资产 |
| 4 | POST | `/api/v1/asset/iot/{id}/update` | 更新IoT设备资产 | 根据ID更新IoT设备资产信息 |
| 5 | POST | `/api/v1/asset/iot/{ids}/bulk_update` | 批量更新IoT设备资产 | 批量更新多个IoT设备资产 |
| 6 | GET | `/api/v1/asset/iot/list` | 查询IoT设备资产列表 | 分页查询IoT设备资产列表 |
| 7 | GET | `/api/v1/asset/iot/{id}/view` | 查询IoT设备资产详情 | 根据ID查询IoT设备资产详细信息 |
| 8 | GET | `/api/v1/asset/iot/label/list` | 获取标签列表 | 获取所有不重复的标签 |
| 9 | GET | `/api/v1/asset/iot/auto_complete/id` | ID自动补全 | 根据关键词获取相似ID |
| 10 | GET | `/api/v1/asset/iot/auto_complete/device_name` | 设备名称自动补全 | 根据关键词获取相似设备名称 |
| 11 | GET | `/api/v1/asset/iot/auto_complete/device_type` | 设备类型自动补全 | 根据关键词获取相似设备类型 |
| 12 | GET | `/api/v1/asset/iot/auto_complete/manufacturer` | 制造商自动补全 | 根据关键词获取相似制造商 |
| 13 | GET | `/api/v1/asset/iot/auto_complete/model` | 型号自动补全 | 根据关键词获取相似型号 |
| 14 | GET | `/api/v1/asset/iot/auto_complete/protocol` | 协议自动补全 | 根据关键词获取相似协议 |

---

## 🔌 接口详情

### 1️⃣ 添加IoT设备资产

**接口地址**: `POST /api/v1/asset/iot/add`

**功能描述**: 添加新的IoT设备资产

**请求参数**:
- Content-Type: `application/json`
- Body: AssetIotDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/iot/add \
  -H "Content-Type: application/json" \
  -d '{
    "deviceName": "温度传感器",
    "deviceType": "传感器",
    "manufacturer": "华为",
    "model": "HS100",
    "protocol": "MQTT"
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

### 2️⃣ 删除IoT设备资产

**接口地址**: `DELETE /api/v1/asset/iot/{id}`

**功能描述**: 根据ID删除单个IoT设备资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | IoT设备资产ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/iot/iot-001
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

### 3️⃣ 批量删除IoT设备资产

**接口地址**: `DELETE /api/v1/asset/iot/bulk/{ids}`

**功能描述**: 批量删除多个IoT设备资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<String\> | 是 | IoT设备资产ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/iot/bulk/iot-001,iot-002
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

### 4️⃣ 更新IoT设备资产

**接口地址**: `POST /api/v1/asset/iot/{id}/update`

**功能描述**: 根据ID更新IoT设备资产信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | IoT设备资产ID |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetIotDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/iot/iot-001/update \
  -H "Content-Type: application/json" \
  -d '{
    "model": "HS200"
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

### 5️⃣ 批量更新IoT设备资产

**接口地址**: `POST /api/v1/asset/iot/{ids}/bulk_update`

**功能描述**: 批量更新多个IoT设备资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | String[] | 是 | IoT设备资产ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetIotDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/iot/iot-001,iot-002/bulk_update \
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

### 6️⃣ 查询IoT设备资产列表

**接口地址**: `GET /api/v1/asset/iot/list`

**功能描述**: 分页查询IoT设备资产列表

**请求参数**:
- Query: AssetIotSearchDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/iot/list?page=1&size=10"
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

### 7️⃣ 查询IoT设备资产详情

**接口地址**: `GET /api/v1/asset/iot/{id}/view`

**功能描述**: 根据ID查询IoT设备资产详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | IoT设备资产ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/iot/iot-001/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "iot-001",
    "deviceName": "温度传感器",
    "deviceType": "传感器",
    "manufacturer": "华为",
    "model": "HS100",
    "protocol": "MQTT",
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 8️⃣ 获取标签列表

**接口地址**: `GET /api/v1/asset/iot/label/list`

**功能描述**: 获取所有不重复的标签

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/iot/label/list
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

**接口地址**: `GET /api/v1/asset/iot/auto_complete/id?term=xxx`

**功能描述**: 根据关键词获取相似IoT设备资产ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/iot/auto_complete/id?term=iot"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "iot-001", "value": "iot-001"},
      {"label": "iot-002", "value": "iot-002"}
    ]
  }
}
```

---

### 🔟 设备名称自动补全

**接口地址**: `GET /api/v1/asset/iot/auto_complete/device_name?term=xxx`

**功能描述**: 根据关键词获取相似设备名称

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/iot/auto_complete/device_name?term=温度"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "温度传感器", "value": "温度传感器"}
    ]
  }
}
```

---

### 1️⃣1️⃣ 设备类型自动补全

**接口地址**: `GET /api/v1/asset/iot/auto_complete/device_type?term=xxx`

**功能描述**: 根据关键词获取相似设备类型

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/iot/auto_complete/device_type?term=传感"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "传感器", "value": "传感器"}
    ]
  }
}
```

---

### 1️⃣2️⃣ 制造商自动补全

**接口地址**: `GET /api/v1/asset/iot/auto_complete/manufacturer?term=xxx`

**功能描述**: 根据关键词获取相似制造商

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/iot/auto_complete/manufacturer?term=华"
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

### 1️⃣3️⃣ 型号自动补全

**接口地址**: `GET /api/v1/asset/iot/auto_complete/model?term=xxx`

**功能描述**: 根据关键词获取相似型号

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/iot/auto_complete/model?term=HS"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "HS100", "value": "HS100"},
      {"label": "HS200", "value": "HS200"}
    ]
  }
}
```

---

### 1️⃣4️⃣ 协议自动补全

**接口地址**: `GET /api/v1/asset/iot/auto_complete/protocol?term=xxx`

**功能描述**: 根据关键词获取相似协议

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/iot/auto_complete/protocol?term=MQ"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "MQTT", "value": "MQTT"}
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