# TraceHub 安全事件研判

> 把独立运行的 TraceHub 溯源能力接入 ZenVis，对安全事件中的 IOC 和 IP 地址做只读、证据化的核验。

## 插件概览

| 项目 | 内容 |
| --- | --- |
| 插件名称 | TraceHub 安全事件研判 |
| 包名 | `com.coolxer.plugin.tracehub` |
| 当前版本 | 1.0.0 |
| 插件形态 | 外部工具集成与 AI 研判型插件 |
| 数据实体 | 无 |
| 主要入口 | 外链菜单“TraceHub 溯源查询”、MCP 工具、知识库与研判 Skill |

## 用途与价值

安全事件研判经常需要查 IOC 情报和 IP 归属，但情报数据往往不适合复制到分析平台，查询过程也需要留下证据链。本插件不复制 TraceHub 的情报数据，也不提供写操作，而是把 TraceHub 现有的只读能力接入 ZenVis：

- **页面直连**：通过外链菜单嵌入 TraceHub 管理页，做 IOC 单条查询、批量查询、IP 地理查询和资源状态查看。
- **Agent 可用**：通过 MCP 暴露四个只读工具，让智能体可以按需查询。
- **研判有依据**：通过 RAG 数据集和知识库说明查询语义、结果字段和研判流程，再由 Skill 按固定步骤输出证据化结论。

**适用对象**

- 安全分析人员：在事件研判中快速核验 IOC 和 IP。
- 应急响应人员：批量查询事件中提取的指标。
- 智能体使用者：通过 Skill 获得有引用、有边界的研判结论。

## 核心能力

- `EXTERNAL_APP` 菜单嵌入 TraceHub 管理页。
- 四个只读、幂等 MCP 工具：
  - `ioc_lookup`：单条 IOC 精确查询。
  - `ioc_lookups`：批量 IOC 查询，默认上限 200 条。
  - `ip_location_lookup`：单个 IPv4/IPv6 地理查询。
  - `ip_location_lookups`：批量 IP 地理查询，默认上限 200 条。
- `tracehub-docs` 数据集和 `tracehub-kb` 知识库，说明查询语义、结果字段、研判流程与限制。
- “TraceHub 安全事件研判”Skill，自动绑定知识库检索，按固定流程提取指标、调用 MCP、解释状态并输出结论。

## 数据与页面

本插件不定义 Meta 实体、不创建 ClickHouse 表、不配置 Kafka 主题和推送任务，也不提供 ZenVis 看板或低代码页面。

| 入口 | 类型 | 说明 |
| --- | --- | --- |
| TraceHub 溯源查询 | 外链菜单 | 默认嵌入 `http://127.0.0.1:8010/ui` |
| `tracehub` MCP 服务 | MCP | 四个只读查询工具 |
| `tracehub-kb` 知识库 | RAG | 研判流程与结果语义 |
| TraceHub 安全事件研判 | Skill | 自动绑定知识库的研判流程 |

## 前置条件

TraceHub 需要独立部署并加载 `current.zioc` 与 ip2region XDB 资源，默认监听 `:8010`：

| 入口 | 默认地址 |
| --- | --- |
| 管理页 | `http://127.0.0.1:8010/ui` |
| IOC REST | `http://127.0.0.1:8010/api/v1/ioc/*` |
| IP 地理 REST | `http://127.0.0.1:8010/api/v1/ip-location/*` |
| MCP SSE | `http://127.0.0.1:8010/sse` |

## 典型使用流程

1. 启动 TraceHub，确认 `/health/live` 和 `/health/ready` 正常。
2. 确认 TraceHub 对 ZenVis 后端可达，并在 MCP 管理页把占位 Bearer Token 替换为真实 Token。
3. 在[插件管理](插件管理.md)中安装插件。
4. 为角色授权“TraceHub 溯源查询”菜单，在浏览器中验证 iframe 可达并完成 Token 连接。
5. 在 MCP 管理页确认 `tracehub` 连接成功并发现四个工具。
6. 在 RAG 管理页确认 `tracehub-kb` 构建状态为 `BUILT`。
7. 在 Skill 详情中确认来源为插件包名、可编辑，且知识库绑定正确。
8. 用已知命中和未命中指标各做一次测试，确认状态解释与工具结果一致。

## 注意事项

- 菜单中的 `127.0.0.1:8010` 是浏览器访问地址，只适用于 TraceHub 与浏览器同机或已完成端口转发的场景；其他部署方式需要在安装前改为实际可达地址。
- MCP 配置中的 `base_url` 是 ZenVis 后端访问地址，不是浏览器地址；Docker 或跨主机部署时必须改为后端可达地址。
- `headers` 中的 `replace-with-at-least-16-characters` 是占位值，安装后必须替换为与 TraceHub `TRACEHUB_API_TOKEN` 一致的 Bearer Token，否则 MCP 查询会返回 401。
- 如果前端构建环境配置了 `VITE_IFRAME_ALLOWED_ORIGINS`，TraceHub 管理页的完整 Origin 必须加入白名单。
- TraceHub 当前资源是不可变快照，不支持热更新；更新情报或 XDB 需要替换运行文件并重启服务。
- 插件不复制情报数据，知识库中不包含真实 IOC。
- `not_found` 不等于安全，`not_covered` 不等于无威胁，`library_unavailable` 表示无法判断。
- 知识库构建失败不会回滚安装，修复 Embedding、Redis 或向量索引后可手工重建。

安装、升级和卸载操作见[插件管理](插件管理.md)。
