# About 系统信息接口文档

**基础信息**
- **模块名称**: 系统信息
- **基础路径**: `/api/v1/system/about`
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. SystemInfoDto (系统信息传输对象)

```json
{
  "systemTitle": "系统标题",       // String - 系统标题（必填）
  "productName": "产品名称",       // String - 产品名称（必填）
  "productVersion": "1.0.0",      // String - 产品版本（必填）
  "productIntroduction": "产品介绍", // String - 产品介绍（可选）
  "servicePhone": "400-123-4567", // String - 服务电话（可选）
  "serviceEmail": "service@example.com", // String - 服务邮箱（可选）
  "technicalEmail": "tech@example.com",  // String - 技术支持邮箱（可选）
  "integrateLink": "https://example.com/integrate", // String - 集成链接（可选）
  "copyright": "2024 Example Inc." // String - 版权信息（可选）
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| systemTitle | String | 是 | 系统标题 |
| productName | String | 是 | 产品名称 |
| productVersion | String | 是 | 产品版本 |
| productIntroduction | String | 否 | 产品介绍 |
| servicePhone | String | 否 | 服务电话 |
| serviceEmail | String | 否 | 服务邮箱 |
| technicalEmail | String | 否 | 技术支持邮箱 |
| integrateLink | String | 否 | 集成链接 |
| copyright | String | 否 | 版权信息 |

### 2. SystemInfoVo (系统信息视图对象)

```json
{
  "systemTitle": "系统标题",       // String - 系统标题
  "systemIcon": "/uploads/icon.png", // String - 系统图标路径
  "systemLogo": "/uploads/logo.png", // String - 系统Logo路径
  "systemBanner": "/uploads/banner.png", // String - 系统Banner路径
  "productName": "产品名称",       // String - 产品名称
  "productVersion": "1.0.0",      // String - 产品版本
  "productIntroduction": "产品介绍", // String - 产品介绍
  "servicePhone": "400-123-4567", // String - 服务电话
  "serviceEmail": "service@example.com", // String - 服务邮箱
  "technicalEmail": "tech@example.com",  // String - 技术支持邮箱
  "integrateLink": "https://example.com/integrate", // String - 集成链接
  "copyright": "2024 Example Inc." // String - 版权信息
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
| 1 | GET | `/api/v1/system/about/info` | 获取系统信息 | 获取系统基本信息 |
| 2 | PUT | `/api/v1/system/about/info/update` | 更新系统信息 | 修改系统基本信息 |
| 3 | POST | `/api/v1/system/about/icon/upload` | 上传Icon图标 | 上传并保存系统Icon图标 |
| 4 | POST | `/api/v1/system/about/logo/upload` | 上传Logo图标 | 上传并保存系统Logo图标 |
| 5 | POST | `/api/v1/system/about/banner/upload` | 上传Banner图标 | 上传并保存系统Banner图标 |

---

## 🔌 接口详情

### 1️⃣ 获取系统信息

**接口地址**: `GET /api/v1/system/about/info`

**功能描述**: 获取系统基本信息，包括系统标题、产品信息、联系方式等

**请求参数**: 无

**请求示例**:
```bash
curl -X GET http://localhost:11002/api/v1/system/about/info
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "systemTitle": "ZenVis系统",
    "systemIcon": "/uploads/icon.png",
    "systemLogo": "/uploads/logo.png",
    "systemBanner": "/uploads/banner.png",
    "productName": "ZenVis",
    "productVersion": "1.0.0",
    "productIntroduction": "一站式数据分析平台",
    "servicePhone": "400-123-4567",
    "serviceEmail": "service@example.com",
    "technicalEmail": "tech@example.com",
    "integrateLink": "https://example.com/integrate",
    "copyright": "2024 ZenVis Inc."
  }
}
```

---

### 2️⃣ 更新系统信息

**接口地址**: `PUT /api/v1/system/about/info/update`

**功能描述**: 修改系统基本信息

**请求参数**:
- Content-Type: `application/json`
- Body: SystemInfoDto

**请求示例**:
```bash
curl -X PUT http://localhost:11002/api/v1/system/about/info/update \
  -H "Content-Type: application/json" \
  -d '{
    "systemTitle": "更新后的系统标题",
    "productName": "ZenVis",
    "productVersion": "2.0.0",
    "productIntroduction": "更新后的产品介绍",
    "servicePhone": "400-999-8888",
    "serviceEmail": "support@example.com",
    "technicalEmail": "tech@example.com",
    "integrateLink": "https://example.com/new-integrate",
    "copyright": "2024 ZenVis Inc."
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

### 3️⃣ 上传Icon图标

**接口地址**: `POST /api/v1/system/about/icon/upload`

**功能描述**: 上传并保存系统Icon图标到服务器

**请求参数**:
- Content-Type: `multipart/form-data`
- Form参数:
  | 参数名 | 类型 | 必填 | 说明 |
  |-------|------|-----|------|
  | file | MultipartFile | 是 | Icon图标文件 |

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/about/icon/upload \
  -F "file=@icon.png"
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

### 4️⃣ 上传Logo图标

**接口地址**: `POST /api/v1/system/about/logo/upload`

**功能描述**: 上传并保存系统Logo图标到服务器

**请求参数**:
- Content-Type: `multipart/form-data`
- Form参数:
  | 参数名 | 类型 | 必填 | 说明 |
  |-------|------|-----|------|
  | file | MultipartFile | 是 | Logo图标文件 |

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/about/logo/upload \
  -F "file=@logo.png"
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

### 5️⃣ 上传Banner图标

**接口地址**: `POST /api/v1/system/about/banner/upload`

**功能描述**: 上传并保存系统Banner图标到服务器

**请求参数**:
- Content-Type: `multipart/form-data`
- Form参数:
  | 参数名 | 类型 | 必填 | 说明 |
  |-------|------|-----|------|
  | file | MultipartFile | 是 | Banner图标文件 |

**请求示例**:
```bash
curl -X POST http://localhost:11002/api/v1/system/about/banner/upload \
  -F "file=@banner.png"
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
2. **文件上传**: 图标上传接口使用 multipart/form-data 格式
3. **文件格式**: 建议上传 PNG、JPG 等常见图片格式
4. **文件大小**: 建议控制文件大小，避免过大影响加载速度

---