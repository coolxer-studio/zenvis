# VectorStoreQueryController 插件文档 RAG 管理接口

## 基础信息

- 基础路径：`/api/v1/dih/vectorstore`
- 用途：管理插件安装后加载到 RAG 的 `00_doc` 文档
- 开关：`app.ai.vectorstore.management.enabled=false` 时接口拒绝访问

## 接口列表

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/documents` | 获取插件 RAG 文档列表 |
| GET | `/documents/list` | 分页获取插件 RAG 文档 |
| GET | `/document/{documentId}` | 查看单个 RAG 文档 |
| DELETE | `/document/{documentId}` | 删除单个 RAG 文档 |
| DELETE | `/documents` | 批量删除 RAG 文档 |
| GET/POST | `/search` | 对插件 RAG 文档做相似度搜索 |

## 查询参数

| 参数 | 说明 |
| :--- | :--- |
| `keyword` | 匹配文档 ID、内容、来源或元数据 |
| `source` | 插件文档来源，约定为插件包名中的 `.` 替换为 `_` |
| `query` | 相似度搜索文本 |
| `topK` | 搜索返回数量，范围 1-100 |

## 返回字段

| 字段 | 说明 |
| :--- | :--- |
| `id` | RAG 文档 ID |
| `text` | 文档分片内容 |
| `metadata` | 文档元数据 |
| `source` | 插件来源 |

该接口只管理插件文档 RAG 数据，不提供数据表结构向量构建、数据可视化 Agent 文档清空或 SQL 相关能力。
