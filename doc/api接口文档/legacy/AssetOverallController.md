# AssetOverall 资产概览接口文档

**基础信息**
- **模块名称**: 资产概览
- **基础路径**: `/api/v1/asset/overall`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. ResponseWrap (统一响应格式)

```json
{
  "status": 0,                // Integer - 响应码(0:成功，其他:失败)
  "msg": "success",           // String - 响应消息
  "data": {}                  // Object - 响应数据
}
```

### 2. 统计数据结构

```json
{
  "totalCount": 100,          // Long - 总数
  "levelStats": {             // Map - 等级统计
    "CRITICAL": 10,
    "HIGH": 20,
    "MEDIUM": 30,
    "LOW": 40
  },
  "typeStats": {              // Map - 类型统计
    "HOST": 50,
    "APP": 30,
    "API": 20
  },
  "trend": [                  // List - 趋势数据
    {"date": "2024-01-01", "count": 10},
    {"date": "2024-01-02", "count": 20}
  ]
}
```

---

## 📊 接口总览

| 序号 | HTTP方法 | 接口路径 | 接口名称 | 功能描述 |
|:---:|:-------:|---------|---------|---------|
| 1 | GET | `/api/v1/asset/overall/count` | 获取资产总数 | 获取所有资产的总数统计 |
| 2 | GET | `/api/v1/asset/overall/trend` | 获取资产趋势 | 获取资产数量的趋势数据 |
| 3 | GET | `/api/v1/asset/overall/level_statistics` | 获取等级统计 | 获取资产等级分布统计 |
| 4 | GET | `/api/v1/asset/overall/type/list` | 获取资产类型列表 | 获取资产类型枚举列表 |
| 5 | GET | `/api/v1/asset/overall/level/list` | 获取资产等级列表 | 获取资产等级枚举列表 |
| 6 | GET | `/api/v1/asset/overall/status/list` | 获取资产状态列表 | 获取资产状态枚举列表 |
| 7 | GET | `/api/v1/asset/overall/host_type/list` | 获取主机类型列表 | 获取主机类型枚举列表 |
| 8 | GET | `/api/v1/asset/overall/host_os/list` | 获取主机操作系统列表 | 获取主机操作系统枚举列表 |
| 9 | GET | `/api/v1/asset/overall/host_architecture/list` | 获取主机架构列表 | 获取主机架构枚举列表 |

---

## 🔌 接口详情

### 1️⃣ 获取资产总数

**接口地址**: `GET /api/v1/asset/overall/count`

**功能描述**: 获取所有资产的总数统计

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/overall/count
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "totalCount": 100
  }
}
```

---

### 2️⃣ 获取资产趋势

**接口地址**: `GET /api/v1/asset/overall/trend`

**功能描述**: 获取资产数量的趋势数据

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/overall/trend
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "trend": [
      {"date": "2024-01-01", "count": 10},
      {"date": "2024-01-02", "count": 20},
      {"date": "2024-01-03", "count": 30}
    ]
  }
}
```

---

### 3️⃣ 获取等级统计

**接口地址**: `GET /api/v1/asset/overall/level_statistics`

**功能描述**: 获取资产等级分布统计

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/overall/level_statistics
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "levelStats": {
      "CRITICAL": 10,
      "HIGH": 20,
      "MEDIUM": 30,
      "LOW": 40
    }
  }
}
```

---

### 4️⃣ 获取资产类型列表

**接口地址**: `GET /api/v1/asset/overall/type/list`

**功能描述**: 获取资产类型枚举列表

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/overall/type/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "主机", "value": "HOST"},
      {"label": "应用", "value": "APP"},
      {"label": "API", "value": "API"},
      {"label": "文件", "value": "FILE"}
    ]
  }
}
```

---

### 5️⃣ 获取资产等级列表

**接口地址**: `GET /api/v1/asset/overall/level/list`

**功能描述**: 获取资产等级枚举列表

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/overall/level/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "严重", "value": "CRITICAL"},
      {"label": "高危", "value": "HIGH"},
      {"label": "中危", "value": "MEDIUM"},
      {"label": "低危", "value": "LOW"}
    ]
  }
}
```

---

### 6️⃣ 获取资产状态列表

**接口地址**: `GET /api/v1/asset/overall/status/list`

**功能描述**: 获取资产状态枚举列表

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/overall/status/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "正常", "value": "NORMAL"},
      {"label": "异常", "value": "ABNORMAL"}
    ]
  }
}
```

---

### 7️⃣ 获取主机类型列表

**接口地址**: `GET /api/v1/asset/overall/host_type/list`

**功能描述**: 获取主机类型枚举列表

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/overall/host_type/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "物理机", "value": "PHYSICAL"},
      {"label": "虚拟机", "value": "VIRTUAL"},
      {"label": "容器", "value": "CONTAINER"}
    ]
  }
}
```

---

### 8️⃣ 获取主机操作系统列表

**接口地址**: `GET /api/v1/asset/overall/host_os/list`

**功能描述**: 获取主机操作系统枚举列表

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/overall/host_os/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "Linux", "value": "LINUX"},
      {"label": "Windows", "value": "WINDOWS"},
      {"label": "Unix", "value": "UNIX"}
    ]
  }
}
```

---

### 9️⃣ 获取主机架构列表

**接口地址**: `GET /api/v1/asset/overall/host_architecture/list`

**功能描述**: 获取主机架构枚举列表

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/asset/overall/host_architecture/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "options": [
      {"label": "x86_64", "value": "x86_64"},
      {"label": "ARM64", "value": "ARM64"},
      {"label": "x86", "value": "x86"}
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
2. **统计数据**: 接口返回的数据为聚合统计结果，可能存在缓存