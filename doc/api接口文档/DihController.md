# DihController 数智中心接口文档

**基础信息**
- **模块名称**: 数智中心
- **基础路径**: `/api/v1/dih`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. SuggestDto (建议请求传输对象)

```json
{
  "content": "",              // String - 上下文内容
  "currentLine": ""           // String - 当前行内容
}
```

### 2. ResponseWrap (统一响应格式)

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
| 1 | POST | `/api/v1/dih/suggest` | 补全建议 | 获取AI补全建议 |
| 2 | GET | `/api/v1/dih/model/list` | 获取模型列表 | 获取可用AI模型列表 |
| 3 | GET | `/api/v1/dih/health` | 健康检查 | 检查服务健康状态 |

---

## 🔌 接口详情

### 1️⃣ 补全建议

**接口地址**: `POST /api/v1/dih/suggest`

**功能描述**: 获取AI补全建议

**请求参数**:
- Content-Type: `application/json`
- Body: SuggestDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/dih/suggest \
  -H "Content-Type: application/json" \
  -d '{
    "content": "上下文内容...",
    "currentLine": "当前行内容"
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": "AI建议内容"
}
```

**失败响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": "AI暂无可用建议"
}
```

---

### 2️⃣ 获取模型列表

**接口地址**: `GET /api/v1/dih/model/list`

**功能描述**: 获取可用AI模型列表

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/dih/model/list
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": [
    {"model": "auto", "label": "系统默认模型"}
  ]
}
```

**失败响应**:
```json
{
  "status": -1,
  "msg": "NO_AUTHORITY",
  "data": null
}
```

---

### 3️⃣ 健康检查

**接口地址**: `GET /api/v1/dih/health`

**功能描述**: 检查服务健康状态

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/v1/dih/health
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": "is running......"
}
```

---

## 📊 响应码汇总

| 响应码 | 说明 | 触发场景 |
|--------|------|---------|
| 0 | 请求成功 | 操作成功完成 |
| -1 | 未知错误 | 遇到未定义的异常情况 |
| NO_AUTHORITY | 无权限 | 用户无权限访问模型列表 |

---

## 🔐 注意事项

1. **认证授权**: 需要登录认证
2. **建议生成**: AI建议基于上下文和当前行内容生成
