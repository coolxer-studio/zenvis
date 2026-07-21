# Plugin 插件接口文档

**基础信息**
- **模块名称**: 插件管理
- **基础路径**: `/api/v1/system/plugin`
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 📋 数据模型定义

### 1. PluginDto (插件传输对象)

```json
{
  "id": 1,                    // Integer - 插件ID（更新时使用，创建时不传）
  "name": "测试插件",         // String - 插件名称（必填）
  "icon": "data:image/png;base64,...", // String - 图标（可选）
  "package_name": "com.example.plugin", // String - 包名（必填）
  "version": "1.0.0",         // String - 版本（必填）
  "description": "插件描述",   // String - 插件简介（可选）
  "author": "作者",           // String - 作者（可选）
  "plugin_path": "/plugins/plugin.jar" // String - 插件包路径（可选）
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| id | Integer | 否 | 插件ID，更新时需传入 |
| name | String | 是 | 插件名称 |
| icon | String | 否 | 图标（Base64编码） |
| package_name | String | 是 | 包名，唯一标识 |
| version | String | 是 | 版本号 |
| description | String | 否 | 插件简介 |
| author | String | 否 | 作者信息 |
| plugin_path | String | 否 | 插件包路径 |

### 2. PluginVo (插件视图对象)

```json
{
  "id": 1,                    // Integer - 插件ID
  "name": "测试插件",         // String - 插件名称
  "icon": "data:image/png;base64,...", // String - 图标
  "package_name": "com.example.plugin", // String - 包名
  "version": "1.0.0",         // String - 版本
  "description": "插件描述",   // String - 插件简介
  "author": "作者",           // String - 作者
  "status": "UNINSTALLED",    // PluginStatusType - 状态（枚举值：INSTALLED/UNINSTALLED）
  "plugin_path": "/plugins/plugin.jar", // String - 插件包路径
  "update_time": "2024-01-01 12:00:00"  // String - 更新时间
}
```

**状态说明**:
| 状态值 | 说明 |
|-------|------|
| INSTALLED | 插件已安装 |
| UNINSTALLED | 插件未安装 |

### 3. PluginSearchDto (插件搜索传输对象)

```json
{
  "name": "插件",             // String - 插件名称（可选）
  "package_name": "com.example", // String - 包名（可选）
  "page": 1,                 // Integer - 页码（继承自SortPageDto）
  "per_page": 10,            // Integer - 每页大小（继承自SortPageDto）
  "order_by": "update_time", // String - 排序字段（继承自SortPageDto）
  "order_dir": "desc"       // String - 排序方向（继承自SortPageDto）
}
```

上例是 JSON 字段命名。该接口列表为 GET Bean 绑定：包名 query 参数使用 `packageName`，分页可使用 `per_page`，排序 query 参数当前使用 `orderBy`、`orderDir`。

### 4. ResponseWrap (统一响应格式)

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
| 1 | POST | `/api/v1/system/plugin/upload` | 上传插件包 | 上传 `.tar.gz` 插件归档，当前最大 300MB |
| 2 | POST | `/api/v1/system/plugin/icon/base64` | 上传图标Base64 | 上传图标并返回Base64编码 |
| 3 | POST | `/api/v1/system/plugin/add` | 创建插件 | 创建新的插件记录 |
| 4 | DELETE | `/api/v1/system/plugin/{id}` | 删除插件 | 根据ID删除单个插件 |
| 5 | DELETE | `/api/v1/system/plugin/bulk/{ids}` | 批量删除插件 | 批量删除多个插件 |
| 6 | POST | `/api/v1/system/plugin/{id}/upgrade` | 升级插件 | 使用已上传的更高版本包升级已安装插件 |
| 7 | POST | `/api/v1/system/plugin/{id}/upgrade/recover` | 恢复旧版本 | 使用持久化升级快照恢复旧版本 |
| 8 | GET | `/api/v1/system/plugin/list` | 查询插件列表 | 分页查询插件列表 |
| 9 | GET | `/api/v1/system/plugin/{id}/view` | 查询插件详情 | 根据ID查询插件详细信息 |
| 10 | GET | `/api/v1/system/plugin/{id}/readme` | 获取README | 获取插件README文档 |
| 11 | GET | `/api/v1/system/plugin/{id}/doc/tree` | 获取文档树 | 获取插件文档目录树 |
| 12 | GET | `/api/v1/system/plugin/{id}/doc/view` | 查看文档 | 查看插件文档内容 |
| 13 | GET | `/api/v1/system/plugin/{id}/export` | 导出插件 | 导出插件包 |
| 14 | GET | `/api/v1/system/plugin/{id}/logs` | 获取操作日志 | 流式获取插件安装、升级、恢复或卸载日志 |
| 15 | POST | `/api/v1/system/plugin/{id}/install` | 安装插件 | 安装插件（后台线程执行） |
| 16 | POST | `/api/v1/system/plugin/{id}/uninstall` | 卸载插件 | 卸载插件（后台线程执行） |

---

## 🔌 接口详情

### 1️⃣ 上传插件包

**接口地址**: `POST /api/v1/system/plugin/upload`

**功能描述**: 上传 ZenVis `.tar.gz` 插件归档到服务器，服务端解析根 `index.json` 并创建/更新插件记录

**请求参数**:
- Content-Type: `multipart/form-data`
- Form参数:
  | 参数名 | 类型 | 必填 | 说明 |
  |-------|------|-----|------|
  | file | MultipartFile | 是 | 插件JAR文件 |

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/plugin/upload \
  -F "file=@com-example-plugin.tar.gz"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "name": "测试插件",
    "package_name": "com.example.plugin",
    "version": "1.0.0",
    "status": "UNINSTALLED"
  }
}
```

---

### 2️⃣ 上传图标Base64

**接口地址**: `POST /api/v1/system/plugin/icon/base64`

**功能描述**: 上传图标文件并返回Base64编码

**请求参数**:
- Content-Type: `multipart/form-data`
- Form参数:
  | 参数名 | 类型 | 必填 | 说明 |
  |-------|------|-----|------|
  | file | MultipartFile | 是 | 图标文件 |

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/plugin/icon/base64 \
  -F "file=@icon.png"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "value": "data:image/png;base64,iVBORw0KGgo..."
  }
}
```

---

### 3️⃣ 创建插件

**接口地址**: `POST /api/v1/system/plugin/add`

**功能描述**: 创建新的插件记录

**请求参数**:
- Content-Type: `application/json`
- Body: PluginDto

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/plugin/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试插件",
    "package_name": "com.example.plugin",
    "version": "1.0.0",
    "description": "这是一个测试插件",
    "author": "测试作者"
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

### 4️⃣ 删除插件

**接口地址**: `DELETE /api/v1/system/plugin/{id}`

**功能描述**: 根据ID删除单个插件

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 插件ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:11001/api/v1/system/plugin/1
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

### 5️⃣ 批量删除插件

**接口地址**: `DELETE /api/v1/system/plugin/bulk/{ids}`

**功能描述**: 批量删除多个插件

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<Long\> | 是 | 插件ID列表，逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:11001/api/v1/system/plugin/bulk/1,2,3
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

### 6️⃣ 升级插件

**接口地址**: `POST /api/v1/system/plugin/{id}/upgrade`

**功能描述**: 对已安装插件执行真正升级。服务端只接收上传接口返回的 `plugin_path`，并重新解析包内 `index.json`，不信任客户端提交的名称、版本或作者等信息。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 插件ID |

**请求参数**:
- Content-Type: `application/json`
- Body: PluginUpgradeDto

| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| plugin_path | String | 是 | `/upload` 返回的候选 `.tar.gz` 路径 |

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/plugin/1/upgrade \
  -H "Content-Type: application/json" \
  -d '{
    "plugin_path": "/data/zenvis/plugins/temp/2026/07/21/example-2.0.0.tar.gz"
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "status": "UPGRADING",
    "pending_upgrade_version": "2.0.0"
  }
}
```

升级要求：候选 `package_name` 与当前插件完全一致，候选版本是严格更高的 SemVer；ClickHouse 仅允许新增实体、表和字段。升级为后台任务，通过 `/{id}/logs` 查看进度。

---

### 7️⃣ 恢复升级前旧版本

**接口地址**: `POST /api/v1/system/plugin/{id}/upgrade/recover`

**功能描述**: 当插件状态为 `UPGRADE_FAILED` 时，使用保留的持久化快照恢复升级前目录、Meta、UI、菜单授权、看板、MCP、API 和推送任务。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 插件ID |

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/plugin/1/upgrade/recover
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "status": "UPGRADING"
  }
}
```

恢复不会删除已经执行的向前 MySQL 迁移，也不会删除升级过程中新增的 ClickHouse 表或字段。

---

### 8️⃣ 查询插件列表

**接口地址**: `GET /api/v1/system/plugin/list`

**功能描述**: 分页查询插件列表

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| name | String | 否 | 插件名称（模糊查询） |
| packageName | String | 否 | 包名（模糊查询） |
| page | Integer | 否 | 页码，默认1 |
| per_page | Integer | 否 | 每页数量，默认 10 |
| orderBy | String | 否 | 排序字段 |
| orderDir | String | 否 | 排序方向（`asc` 或 `desc`） |

**请求示例**:
```bash
curl -X GET "http://localhost:11001/api/v1/system/plugin/list?name=测试&page=1&per_page=10"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "total": 10,
    "rows": [
      {
        "id": 1,
        "name": "测试插件",
        "package_name": "com.example.plugin",
        "version": "1.0.0",
        "status": "UNINSTALLED",
        "update_time": "2024-01-01 12:00:00"
      }
    ]
  }
}
```

---

### 9️⃣ 查询插件详情

**接口地址**: `GET /api/v1/system/plugin/{id}/view`

**功能描述**: 根据ID查询插件详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 插件ID |

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/plugin/1/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "name": "测试插件",
    "package_name": "com.example.plugin",
    "version": "1.0.0",
    "description": "这是一个测试插件",
    "author": "测试作者",
    "status": "UNINSTALLED",
    "plugin_path": "/plugins/plugin-1.0.0.jar",
    "update_time": "2024-01-01 12:00:00"
  }
}
```

---

### 🔟 获取README

**接口地址**: `GET /api/v1/system/plugin/{id}/readme`

**功能描述**: 获取插件的README文档内容

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 插件ID |

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/plugin/1/readme
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "value": "# 插件说明\n\n这是一个测试插件的README文档..."
  }
}
```

---

### 1️⃣1️⃣ 获取文档树

**接口地址**: `GET /api/v1/system/plugin/{id}/doc/tree`

**功能描述**: 获取插件文档目录树结构

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 插件ID |

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/plugin/1/doc/tree
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": [
    {
      "id": "README.md",
      "name": "README.md",
      "children": []
    },
    {
      "id": "docs",
      "name": "docs",
      "children": [
        {
          "id": "docs/api.md",
          "name": "api.md",
          "children": []
        }
      ]
    }
  ]
}
```

---

### 1️⃣2️⃣ 查看文档

**接口地址**: `GET /api/v1/system/plugin/{id}/doc/view?file=README.md`

**功能描述**: 查看指定插件文档内容

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 插件ID |

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| file | String | 是 | 文档文件路径 |

**请求示例**:
```bash
curl -X GET "http://localhost:11001/api/v1/system/plugin/1/doc/view?file=README.md"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "value": "# 插件说明\n\n这是一个测试插件..."
  }
}
```

---

### 1️⃣3️⃣ 导出插件

**接口地址**: `GET /api/v1/system/plugin/{id}/export`

**功能描述**: 导出插件JAR包文件

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 插件ID |

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/plugin/1/export \
  -o plugin.jar
```

**成功响应**: 返回插件JAR文件流

---

### 1️⃣4️⃣ 获取安装日志

**接口地址**: `GET /api/v1/system/plugin/{id}/logs`

**功能描述**: 流式获取插件安装、升级、恢复或卸载日志

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 插件ID |

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/plugin/1/logs
```

**成功响应**: 流式返回日志内容

---

### 1️⃣5️⃣ 安装插件

**接口地址**: `POST /api/v1/system/plugin/{id}/install`

**功能描述**: 安装插件，在后台线程中执行

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 插件ID |

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/plugin/1/install
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

### 1️⃣6️⃣ 卸载插件

**接口地址**: `POST /api/v1/system/plugin/{id}/uninstall`

**功能描述**: 卸载插件，在后台线程中执行

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 插件ID |

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/plugin/1/uninstall
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
2. **安装/升级/恢复/卸载**: 生命周期操作在后台线程中执行，调用后立即返回操作状态，需通过日志接口查看进度
3. **文件上传**: 插件包和图标上传使用 multipart/form-data 格式
4. **日志流式**: 日志接口返回流式响应，需要支持 SSE 或流式读取
5. **升级兼容**: 不支持同版本修复、降级或 ClickHouse 破坏性结构迁移；MySQL 迁移必须只向前且兼容旧运行版本

---
