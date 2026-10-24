# TraceHub 插件对接说明

## 目的

本插件不采集、不存储 TraceHub 情报数据，只提供三类集成能力：

1. 浏览器侧嵌入 TraceHub 管理页；
2. ZenVis 后端侧通过 SSE MCP 调用 TraceHub 的只读查询工具；
3. 通过随包 Markdown、RAG 知识库和研判 Skill 约束安全事件分析流程。

因此插件本身没有 Meta、推送任务和 Dashboard。`02_push-task/config.json` 与 `05_dashboard/config.json` 使用空数组，`01_meta` 和 `04_ui` 不包含业务资源。

## 地址与网络边界

必须区分两个地址：

| 配置 | 使用方 | 默认值 | 说明 |
| --- | --- | --- | --- |
| 菜单 `params` | 用户浏览器 | `http://127.0.0.1:8010/ui` | iframe 直接加载；需为用户可达地址 |
| MCP `base_url` | ZenVis 后端 | `http://127.0.0.1:8010` | 由后端连接 SSE；同机默认值，需按部署改为后端可达地址 |
| MCP `sse_endpoint` | ZenVis 后端 | `/sse` | TraceHub 的 SSE 入口 |

同机部署、容器部署和反向代理部署的地址不同，不应把浏览器地址直接复制到 `base_url`。TraceHub 默认只监听 `:8010`；生产环境应通过受控网络、TLS 和反向代理暴露，并限制 MCP 的浏览器 Origin。

## 认证

TraceHub 的 REST 与 MCP 共用 `TRACEHUB_API_TOKEN`。插件配置使用以下占位值：

```text
Authorization: Bearer replace-with-at-least-16-characters
```

占位值不是有效凭据，允许插件完成安装，但 MCP 查询会因 401 失败。安装后必须在 MCP 管理页把固定请求头替换为与 TraceHub `TRACEHUB_API_TOKEN` 一致的 Bearer Token。Token 至少 16 个字符，不得将真实值写入插件包、知识库或 iframe URL。

TraceHub 管理页不内置 Token。用户在页面中输入后，值仅保存在当前浏览器标签页的 `sessionStorage`，关闭标签页后清除。

## iframe 来源白名单

TraceHub 服务端允许管理页被任意页面通过 iframe 嵌入，CSP 使用 `frame-ancestors *`，且不返回 `X-Frame-Options`。

ZenVis 前端还会对 `EXTERNAL_APP` 地址执行协议校验，只允许 `http/https`。若前端构建环境配置了 `VITE_IFRAME_ALLOWED_ORIGINS`，TraceHub 的完整 Origin 也必须出现在列表中：

```text
VITE_IFRAME_ALLOWED_ORIGINS=https://tracehub.example.com
```

多个 Origin 用逗号分隔，必须精确匹配协议、主机和端口，不支持通配符。未配置 `VITE_IFRAME_ALLOWED_ORIGINS` 时，前端不额外限制 HTTP/HTTPS Origin。

## MCP 工具

| 工具 | 参数 | 返回要点 |
| --- | --- | --- |
| `ioc_lookup` | `value`、可选 `type`、`max_matches` | 单条 IOC 的 `status`、`match_count`、`truncated`、`matches` |
| `ioc_lookups` | `indicators`、可选 `max_matches` | 批量统计和逐项结果，保持输入顺序 |
| `ip_location_lookup` | `ip` | 单个 IP 的国家、省、市、ISP 和状态 |
| `ip_location_lookups` | `ips` | 批量地理结果，非法项和特殊地址逐项返回 |

四个工具均标记为只读、幂等、非破坏性，不修改 TraceHub 快照或 XDB。

## RAG 资源

`tracehub-docs` 数据集只包含本项目 `08_rag/datasets/tracehub-docs/` 下的 Markdown。知识库 `tracehub-kb` 关联该数据集。

安装和升级会以包内声明为准，覆盖管理员对名称、标签、描述、关联关系和文件的修改。Embedding、Redis 或向量构建失败只产生告警，不回滚插件安装。

研判 Skill 在 `skill.json` 中声明 `knowledgeBaseCodes: ["tracehub-kb"]`，运行时按插件包名解析知识库 ID，并在每轮会话中自动注入该知识库范围内的 RAG 参考资料。插件 Skill 的来源自动填充为 `com.coolxer.plugin.tracehub`，管理端可临时编辑，但下一次插件升级或重装会恢复包内声明。

## 卸载影响

卸载插件会删除该包名托管的知识库、知识库与数据集关系、数据集、MinIO 文档和对应向量，并清理旧版 `00_doc` 公共向量来源。它不会删除 TraceHub 服务本身，也不会删除其他插件或管理员自建 RAG 资源。

## 相关文档

- `README.md`：安装、配置、验证与排障；
- `08_rag/datasets/tracehub-docs/01-资源与状态语义.md`：资源范围和状态含义；
- `08_rag/datasets/tracehub-docs/02-查询输入与结果字段.md`：规范化、参数和响应字段；
- `08_rag/datasets/tracehub-docs/03-安全事件研判流程.md`：证据化研判步骤；
- `08_rag/datasets/tracehub-docs/04-限制运维与降级.md`：限制、误判风险和降级策略。
