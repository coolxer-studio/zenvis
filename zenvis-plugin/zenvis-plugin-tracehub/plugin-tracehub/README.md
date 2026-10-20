# TraceHub 安全事件研判插件

## 概述

本插件把独立运行的 `zenvis-service-tracehub` 接入 ZenVis，用于安全事件中的 IOC 与 IP 地址核验。插件不复制 TraceHub 的情报数据，也不提供写操作；查询结果始终来自 TraceHub 当前加载的只读资源。

能力范围：

- 通过 `EXTERNAL_APP` 菜单在 ZenVis 中嵌入 TraceHub 管理页，进行 IOC 单条查询、批量查询、IP 地理查询和资源状态查看。
- 通过 `07_mcp` 注册 TraceHub SSE MCP 服务，暴露四个只读、幂等工具。
- 通过 `08_rag` 托管 TraceHub 文档数据集和知识库，说明查询语义、结果字段、研判流程与限制。
- 通过 `09_skill` 安装“TraceHub 安全事件研判”Skill，自动绑定知识库检索，按固定流程提取指标、调用 MCP、解释状态并输出证据化结论。

不包含：

- Meta 实体、ClickHouse 表、Kafka Topic 和数据推送任务；
- ZenVis Dashboard 和低代码页面；
- IOC 数据、XDB 文件、真实 Token、客户地址或运行日志；
- 阻断、隔离、封禁、工单提交等写操作。

## 插件信息

| 项目 | 值 |
| --- | --- |
| 插件名称 | TraceHub 安全事件研判 |
| 包名 | `com.coolxer.plugin.tracehub` |
| 版本 | `1.0.0` |
| MCP Code | `tracehub` |
| RAG 数据集 | `tracehub-docs` |
| RAG 知识库 | `tracehub-kb` |
| Skill ID | `tracehub-security-event-analysis` |

## TraceHub 前置条件

TraceHub 需要独立部署并加载 `current.zioc` 与 ip2region XDB 资源。默认监听 `:8010`，关键入口如下：

| 入口 | 默认地址 | 认证 |
| --- | --- | --- |
| 管理页 | `http://127.0.0.1:8010/ui` | 页面内手工输入 Bearer Token |
| IOC REST | `http://127.0.0.1:8010/api/v1/ioc/*` | Bearer Token |
| IP 地理 REST | `http://127.0.0.1:8010/api/v1/ip-location/*` | Bearer Token |
| MCP SSE | `http://127.0.0.1:8010/sse` | Bearer Token |
| 健康检查 | `/health/live`、`/health/ready` | 无 |

启动示例：

```bash
export TRACEHUB_API_TOKEN='<由密钥管理系统注入>'
export TRACEHUB_SERVER_ADDR=127.0.0.1:8010
./zenvis-service-tracehub
```

详细配置见 TraceHub 的 `README.md` 和 `doc/API接口文档.md`。

## UI 入口

`06_menu/config.json` 声明一个 `EXTERNAL_APP` 菜单，默认嵌入：

```text
http://127.0.0.1:8010/ui
```

这是浏览器访问地址，必须能从用户浏览器所在网络直接访问。`127.0.0.1` 只适用于 TraceHub 与浏览器在同一台机器，或本机已完成端口转发的场景；其他部署方式应在安装前把 `params` 改为实际可达地址。若通过 ZenVis 同源反向代理访问，代理必须保留 TraceHub 的 `/ui` 和 `/api/v1` 路径，因为管理页使用 `/ui/app.css`、`/ui/app.js` 等绝对路径。

前端 iframe 会拒绝 `javascript:`、`data:`、`blob:`、`file:` 等不安全协议，并允许 `http/https`。如果 ZenVis 前端构建环境配置了 `VITE_IFRAME_ALLOWED_ORIGINS`，则 TraceHub 的完整 Origin 必须加入该列表，例如：

```text
VITE_IFRAME_ALLOWED_ORIGINS=https://tracehub.example.com
```

白名单按完整 Origin 精确匹配，不包含路径。未配置该变量时，前端不额外限制 HTTP/HTTPS Origin。修改白名单后需要重新构建或重新发布前端。TraceHub 页面不会把 Token 写入 URL；用户需要在页面内输入 Bearer Token，Token 仅保存在当前标签页的 `sessionStorage`。

## MCP 接入

`07_mcp/config.json` 默认配置：

```json
[
  {
    "code": "tracehub",
    "name": "TraceHub 溯源资源查询",
    "description": "查询 TraceHub 内置的只读 IOC 快照和 IP 地理位置资源，提供单条与批量查询工具。",
    "base_url": "http://127.0.0.1:8010",
    "sse_endpoint": "/sse",
    "headers": "{\"Authorization\":\"Bearer replace-with-at-least-16-characters\"}",
    "enabled": true,
    "request_timeout_seconds": 30,
    "connect_timeout_seconds": 10
  }
]
```

说明：

- `base_url` 是 ZenVis 后端访问地址，不是浏览器地址。当前默认值 `http://127.0.0.1:8010` 面向 ZenVis 与 TraceHub 同机部署；Docker 或跨主机部署时必须改为后端实际可达的地址。
- `headers` 中的 `replace-with-at-least-16-characters` 是占位值，不是有效凭据。安装后必须在 MCP 管理页替换为与 TraceHub 的 `TRACEHUB_API_TOKEN` 一致的 Bearer Token，否则 MCP 查询会因 401 失败。
- 安装后也可以由管理员在 MCP 管理页修改地址和 Header。注意升级时，与旧默认值不同的运行期配置会被保留。
- 不要把真实 Token 写入插件包、README、Git 或前端 URL。

四个 MCP 工具均为只读、幂等：

| 工具 | 输入 | 用途 |
| --- | --- | --- |
| `ioc_lookup` | `value`，可选 `type`、`max_matches` | 单条 IOC 精确查询 |
| `ioc_lookups` | `indicators` 数组，可选 `max_matches` | 批量 IOC 查询，默认上限 200 条 |
| `ip_location_lookup` | `ip` | 单个 IPv4/IPv6 地理查询 |
| `ip_location_lookups` | `ips` 数组 | 批量 IP 地理查询，默认上限 200 条 |

IOC 的 `max_matches` 默认 20，实际范围受 TraceHub 的 `IOC_MAX_MATCHES` 限制。批量结果保持输入顺序；非法单项不会中断整批查询。

## RAG 数据集与知识库

`08_rag/config.json` 托管：

| 资源 | 编码 | 内容 |
| --- | --- | --- |
| 数据集 | `tracehub-docs` | 资源与状态语义、查询规范化、结果字段、研判流程、限制与运维 |
| 知识库 | `tracehub-kb` | 关联 `tracehub-docs`，供安全事件研判检索 |

安装和升级会同步文档、创建或更新知识库并尝试构建向量。Embedding 或 Redis 不可用不会回滚插件安装，知识库会保留为 `UNBUILD` 或 `BUILD_FAILED`；修复后在 RAG 管理页手工重建。

数据集内容位于 `08_rag/datasets/tracehub-docs/`，只包含 Markdown 文件。管理员对托管资源的临时修改会在下一次插件升级时被包内声明覆盖。

### 绑定 Skill 与知识库

插件安装时无法预知平台分配的数值知识库 ID，因此 `skill.json` 通过 `knowledgeBaseCodes: ["tracehub-kb"]` 声明逻辑绑定。Skill 加载时会按插件包名 `com.coolxer.plugin.tracehub` 和知识库编码解析当前环境的 ID，并在每轮会话中自动注入该知识库范围内的 RAG 参考资料。

插件 Skill 的 `source` 自动填充为插件包名，`editable: true` 允许管理员在 DIH Skill 管理页临时修改名称、提示词、工具和知识库绑定。插件升级或重装时，包内 `skill.json` 与 `SKILL.md` 会覆盖这些修改；删除插件 Skill 仍应通过卸载插件完成。

如果知识库尚未构建或 RAG 服务不可用，Skill 仍可使用 MCP 工具完成查询，但应在回复中明确说明本轮未获得 `tracehub-kb` 参考资料，不得编造知识库内容。

## Skill 研判流程

`tracehub-security-event-analysis` 遵循以下边界：

1. 只提取事件文本、JSON 或日志中明确存在的指标，不虚构 IP、域名、URL、哈希或邮箱。
2. 非 IP IOC 使用 `ioc_lookups` 批量查询；IP 同时查询 IOC 情报和 IP 地理位置。
3. 逐项解释 `matched`、`not_found`、`not_covered`、`invalid`、`library_unavailable`。
4. `matched` 输出命中字段、记录 ID、来源、标题、威胁类型、严重度、置信度、首次/最后出现时间。
5. `severity` 与 `confidence` 是情报源结论，不是本插件对事件的最终定性。
6. 地理位置只作为上下文，不能单独作为恶意归因；私网、回环、链路本地、组播和文档网段通常为 `not_covered`。
7. `not_found` 不等于安全，`not_covered` 不等于无威胁，`library_unavailable` 表示无法判断。
8. 不声称已阻断、隔离、封禁或提交工单。

完整流程见 `09_skill/tracehub-security-event-analysis/SKILL.md` 和知识库文档。

## 安装与验证

1. 启动 TraceHub，确认 `/health/live` 和 `/health/ready` 正常。
2. 确认 TraceHub 的 `base_url` 对 ZenVis 后端可达，并在 MCP 管理页把占位 Bearer Token 替换为真实值。
3. 安装 `com-coolxer-plugin-tracehub.tar.gz`。
4. 为角色授权菜单“TraceHub 溯源查询”，在浏览器中验证 iframe 可达并完成 Token 连接。
5. 在 MCP 管理页确认 `tracehub` 连接成功且发现四个工具。
6. 在 RAG 管理页确认 `tracehub-kb` 构建状态为 `BUILT`。
7. 在 Skill 详情中确认来源为 `com.coolxer.plugin.tracehub`、可编辑，且知识库显示 `TraceHub 安全事件研判知识库`。
8. 用已知命中和未命中指标各做一次测试，确认状态解释与工具结果一致。

## 故障排查

| 现象 | 排查方向 |
| --- | --- |
| 菜单显示 404 | TraceHub 是否启动；浏览器是否可访问菜单中的 URL；前端来源白名单是否包含该 Origin |
| 页面提示 401 | 页面输入的 Bearer Token 是否与 TraceHub 的 `TRACEHUB_API_TOKEN` 一致 |
| MCP 显示未连接 | 后端是否能访问 `base_url`；`/sse` 是否正确；固定请求头是否仍为占位 Token；查看安装或 MCP 管理日志 |
| 工具不存在 | MCP 连接失败或尚未刷新；确认 TraceHub 版本包含四个 MCP 工具 |
| 知识库为 `UNBUILD` / `BUILD_FAILED` | 检查 Embedding、Redis、向量索引配置，修复后手工重建 |
| 所有查询都是 `not_found` | 先确认 TraceHub 资源状态和快照版本；`not_found` 仅表示当前快照未命中 |

## 安全与限制

- TraceHub 当前资源是不可变快照，不支持热更新；更新情报或 XDB 需要替换运行文件并重启服务。
- 插件不复制情报数据，因此 RAG 文档中不包含真实 IOC。
- 不要将 Token 写入菜单参数、iframe URL、知识库文档或日志。
- “未命中”“未覆盖”“资源不可用”均不能推出“安全”。

## 版本历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| 1.0.0 | 2026-10-20 | 初始版本：外部 UI、四个只读 MCP 工具、RAG 知识库与安全事件研判 Skill |
