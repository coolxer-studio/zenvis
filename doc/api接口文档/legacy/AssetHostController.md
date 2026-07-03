# AssetHost 主机资产接口文档

**基础信息**
- **模块名称**: 主机资产管理
- **基础路径**: `/api/v1/asset/host`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. AssetHostDto (主机资产传输对象)

```json
{
  "id": "host-001",           // String - 主机资产ID（更新时使用）
  "hostName": "主机名称",      // String - 主机名称
  "hostType": "VIRTUAL",      // String - 主机类型（PHYSICAL/VIRTUAL/CONTAINER）
  "hostOs": "LINUX",          // String - 操作系统（LINUX/WINDOWS/UNIX）
  "hostArchitecture": "x86_64", // String - 架构（x86_64/ARM64/x86）
  "ipAddress": "192.168.1.1", // String - IP地址
  "macAddress": "00:00:00:00:00:00", // String - MAC地址
  "cpuCore": 8,               // Integer - CPU核心数
  "memorySize": 16,           // Integer - 内存大小（GB）
  "diskSize": 500,            // Integer - 磁盘大小（GB）
  "description": "描述信息",   // String - 描述信息
  "labels": ["label1"]        // List - 标签列表
}
```

### 2. AssetHostVo (主机资产视图对象)

```json
{
  "id": "host-001",           // String - 主机资产ID
  "hostName": "主机名称",      // String - 主机名称
  "hostType": "VIRTUAL",      // String - 主机类型
  "hostOs": "LINUX",          // String - 操作系统
  "hostArchitecture": "x86_64", // String - 架构
  "ipAddress": "192.168.1.1", // String - IP地址
  "macAddress": "00:00:00:00:00:00", // String - MAC地址
  "cpuCore": 8,               // Integer - CPU核心数
  "memorySize": 16,           // Integer - 内存大小（GB）
  "diskSize": 500,            // Integer - 磁盘大小（GB）
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
| 1 | POST | `/api/v1/asset/host/add` | 添加主机资产 | 添加新的主机资产 |
| 2 | DELETE | `/api/v1/asset/host/{id}` | 删除主机资产 | 根据ID删除单个主机资产 |
| 3 | DELETE | `/api/v1/asset/host/bulk/{ids}` | 批量删除主机资产 | 批量删除多个主机资产 |
| 4 | POST | `/api/v1/asset/host/{id}/update` | 更新主机资产 | 根据ID更新主机资产信息 |
| 5 | POST | `/api/v1/asset/host/{ids}/bulk_update` | 批量更新主机资产 | 批量更新多个主机资产 |
| 6 | GET | `/api/v1/asset/host/list` | 查询主机资产列表 | 分页查询主机资产列表 |
| 7 | GET | `/api/v1/asset/host/{id}/view` | 查询主机资产详情 | 根据ID查询主机资产详细信息 |
| 8 | GET | `/api/v1/asset/host/{id}/detail` | 查询主机详情 | 查询主机详细信息（包含服务列表） |
| 9 | GET | `/api/v1/asset/host/{id}/services` | 获取服务列表 | 获取主机关联的服务列表 |
| 10 | GET | `/api/v1/asset/host/label/list` | 获取标签列表 | 获取所有不重复的标签 |
| 11 | GET | `/api/v1/asset/host/auto_complete/id` | ID自动补全 | 根据关键词获取相似ID |
| 12 | GET | `/api/v1/asset/host/auto_complete/host_name` | 主机名称自动补全 | 根据关键词获取相似主机名称 |
| 13 | GET | `/api/v1/asset/host/auto_complete/host_type` | 主机类型自动补全 | 根据关键词获取相似主机类型 |
| 14 | GET | `/api/v1/asset/host/auto_complete/host_os` | 操作系统自动补全 | 根据关键词获取相似操作系统 |
| 15 | GET | `/api/v1/asset/host/auto_complete/host_architecture` | 架构自动补全 | 根据关键词获取相似架构 |
| 16 | GET | `/api/v1/asset/host/auto_complete/ip_address` | IP地址自动补全 | 根据关键词获取相似IP地址 |
| 17 | GET | `/api/v1/asset/host/auto_complete/mac_address` | MAC地址自动补全 | 根据关键词获取相似MAC地址 |

---

## 🔌 接口详情

### 1️⃣ 添加主机资产

**接口地址**: `POST /api/v1/asset/host/add`

**功能描述**: 添加新的主机资产

**请求参数**:
- Content-Type: `application/json`
- Body: AssetHostDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/host/add \
  -H "Content-Type: application/json" \
  -d '{
    "hostName": "server-01",
    "hostType": "VIRTUAL",
    "hostOs": "LINUX",
    "hostArchitecture": "x86_64",
    "ipAddress": "192.168.1.1",
    "cpuCore": 8,
    "memorySize": 16,
    "diskSize": 500
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

### 2️⃣ 删除主机资产

**接口地址**: `DELETE /api/v1/asset/host/{id}`

**功能描述**: 根据ID删除单个主机资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 主机资产ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/host/host-001
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

### 3️⃣ 批量删除主机资产

**接口地址**: `DELETE /api/v1/asset/host/bulk/{ids}`

**功能描述**: 批量删除多个主机资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<String\> | 是 | 主机资产ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/v1/asset/host/bulk/host-001,host-002
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

### 4️⃣ 更新主机资产

**接口地址**: `POST /api/v1/asset/host/{id}/update`

**功能描述**: 根据ID更新主机资产信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 主机资产ID |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetHostDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/host/host-001/update \
  -H "Content-Type: application/json" \
  -d '{
    "memorySize": 32
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

### 5️⃣ 批量更新主机资产

**接口地址**: `POST /api/v1/asset/host/{ids}/bulk_update`

**功能描述**: 批量更新多个主机资产

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | String[] | 是 | 主机资产ID数组，逗号分隔 |

**请求参数**:
- Content-Type: `application/json`
- Body: AssetHostDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/asset/host/host-001,host-002/bulk_update \
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

### 6️⃣ 查询主机资产列表

**接口地址**: `GET /api/v1/asset/host/list`

**功能描述**: 分页查询主机资产列表

**请求参数**:
- Query: AssetHostSearchDto（分页及搜索条件）

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/host/list?page=1&size=10"
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

### 7️⃣ 查询主机资产详情

**接口地址**: `GET /api/v1/asset/host/{id}/view`

**功能描述**: 根据ID查询主机资产详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 主机资产ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/host/host-001/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "host-001",
    "hostName": "server-01",
    "hostType": "VIRTUAL",
    "hostOs": "LINUX",
    "hostArchitecture": "x86_64",
    "ipAddress": "192.168.1.1",
    "cpuCore": 8,
    "memorySize": 16,
    "diskSize": 500,
    "updateTime": "2024-01-01 12:00:00"
  }
}
```

---

### 8️⃣ 查询主机详情

**接口地址**: `GET /api/v1/asset/host/{id}/detail`

**功能描述**: 查询主机详细信息（包含服务列表）

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 主机资产ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/host/host-001/detail
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "host": {
      "id": "host-001",
      "hostName": "server-01"
    },
    "services": []
  }
}
```

---

### 9️⃣ 获取服务列表

**接口地址**: `GET /api/v1/asset/host/{id}/services`

**功能描述**: 获取主机关联的服务列表

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | String | 是 | 主机资产ID |

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/host/host-001/services
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "services": []
  }
}
```

---

### 🔟 获取标签列表

**接口地址**: `GET /api/v1/asset/host/label/list`

**功能描述**: 获取所有不重复的标签

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/host/label/list
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

### 1️⃣1️⃣ ID自动补全

**接口地址**: `GET /api/v1/asset/host/auto_complete/id?term=xxx`

**功能描述**: 根据关键词获取相似主机资产ID

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/host/auto_complete/id?term=host"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "host-001", "value": "host-001"},
      {"label": "host-002", "value": "host-002"}
    ]
  }
}
```

---

### 1️⃣2️⃣ 主机名称自动补全

**接口地址**: `GET /api/v1/asset/host/auto_complete/host_name?term=xxx`

**功能描述**: 根据关键词获取相似主机名称

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/host/auto_complete/host_name?term=server"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "server-01", "value": "server-01"}
    ]
  }
}
```

---

### 1️⃣3️⃣ 主机类型自动补全

**接口地址**: `GET /api/v1/asset/host/auto_complete/host_type?term=xxx`

**功能描述**: 根据关键词获取相似主机类型

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/host/auto_complete/host_type?term=VIR"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "VIRTUAL", "value": "VIRTUAL"}
    ]
  }
}
```

---

### 1️⃣4️⃣ 操作系统自动补全

**接口地址**: `GET /api/v1/asset/host/auto_complete/host_os?term=xxx`

**功能描述**: 根据关键词获取相似操作系统

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/host/auto_complete/host_os?term=LIN"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "LINUX", "value": "LINUX"}
    ]
  }
}
```

---

### 1️⃣5️⃣ 架构自动补全

**接口地址**: `GET /api/v1/asset/host/auto_complete/host_architecture?term=xxx`

**功能描述**: 根据关键词获取相似架构

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/host/auto_complete/host_architecture?term=x86"
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

### 1️⃣6️⃣ IP地址自动补全

**接口地址**: `GET /api/v1/asset/host/auto_complete/ip_address?term=xxx`

**功能描述**: 根据关键词获取相似IP地址

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/host/auto_complete/ip_address?term=192.168"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "192.168.1.1", "value": "192.168.1.1"}
    ]
  }
}
```

---

### 1️⃣7️⃣ MAC地址自动补全

**接口地址**: `GET /api/v1/asset/host/auto_complete/mac_address?term=xxx`

**功能描述**: 根据关键词获取相似MAC地址

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| term | String | 否 | 搜索关键词 |

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/v1/asset/host/auto_complete/mac_address?term=00:00"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "00:00:00:00:00:00", "value": "00:00:00:00:00:00"}
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
4. **主机详情**: detail接口返回主机信息及关联服务列表