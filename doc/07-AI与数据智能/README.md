# AI 与数据智能

本主题保留 AI、DIH、MCP、报表和检索实现的完整资料：

- [AI 会话实现设计](ai-chat-session-implementation.md)
- [DIH 数据接入与数据可视化工作流](DIH数据接入与可视化工作流.md)
- [MCP Client 与业务 Agent 设计](MCP-Client-Agent-Design.md)
- [MCP 审批与 AI 分析任务快速上手](MCP审批与AI分析任务快速上手.md)
- [报表制作智能体快速上手](报表制作智能体快速上手.md)
- [Redis Search 命令完整指南](redis-search-commands-guide.md)

## 能力组成

ZenVis 的 AI 能力由会话、模型、RAG、Skill、MCP 工具、业务 Agent、后台任务和报表工作台组成。

| 能力 | 作用 |
| --- | --- |
| Chat Session | 保存前端历史、会话标题、类型和结构化消息 |
| Spring AI Chat Memory | 给模型提供多轮上下文 |
| RAG | 为普通问答检索插件公共文档 |
| Skill | 为业务 Agent 注入显式工作规范 |
| MCP | 暴露或连接工具，并提供策略、审批和审计 |
| Analysis Task | 在后台排队或定时执行 Agent |
| Report Workspace | 编辑、归档和导出报表文档 |

## 会话链路

```text
POST /api/v1/dih/chat
  → 校验消息、模型、附件和 Agent 类型
  → 创建或读取当前用户会话
  → 先保存用户消息
  → 选择普通问答、深度思考或业务 Agent
  → 合并模型增量与 MCP 审批事件
  → 解析结构化 parts
  → 保存最终 AI 消息
```

前端历史与模型记忆不是同一份数据：

- `ChatSession.messages` 用于界面展示；
- Spring AI JDBC Chat Memory 用于模型上下文。

排查历史或上下文问题时需要同时检查两者。

## 模型与附件

模型列表综合默认模型和 OpenAI 兼容 `/v1/models`，并提供 `auto`。`auto` 由后端在运行时选择可用模型。

附件处理：

- 文本读取 UTF-8 前 80,000 字符并追加到 Prompt；
- 图片作为 OpenAI 兼容 `image_url` 输入；
- 其他文件只提供元信息；
- 用户历史保存原消息和附件元信息，不保存拼接后的完整 Prompt。

## 普通问答与 RAG

普通问答和深度思考：

- 可以使用公共 Redis Vector Store；
- RAG 默认检索 topK 6；
- embedding 或 Redis 失败时 fail-open，继续无 RAG 问答；
- 不加载 Skill；
- 不解析或调用 MCP 工具。

业务 Agent 不检索公共 RAG，避免文档内容改变执行流程。

## 业务 Agent

### 数据接入

内置示例在模型校验前进入确定性路由；普通请求进入共享状态机。Meta 候选配置在用户确认后
按完整内容和摘要锁定，依次完成写前检查、MCP 审批、应用和 `config_read` 语义校验。
PushTask 分支执行格式检测、`sourceMark` 冲突检查、创建或复用、状态及 system 日志读回。

### 数据可视化

普通请求先通过 MCP 查询实体和字段 Meta，并使用严格候选卡确认准确逻辑名称；查询方案批准后
由平台执行锁定的实体分析工具和参数。图表统一保存标准数据、查询元信息和纯 JSON
`echartsOption`，对话与图表库复用同一渲染组件。配置、看板和菜单写入必须审批并读回。
Agent 不直接访问数据库、不生成任意 SQL，也不加载外部 MCP。

所有业务 Agent 都不检索公共 RAG；请求中的 `deep_think=true` 对 Agent 会被忽略。只有 `type=ask` 允许 RAG 和深度思考，且始终不获得 MCP 工具或 Skill。

### 专项 Skill

插件或自定义 Skill 可通过独立的 `skill:<skillId>` 会话入口执行专项任务。通用 Skill 只有在 manifest 中显式声明 `runtime.tools` 时才获得对应的本地或外部 MCP 工具，并始终受工具策略、审批和调用预算约束。

### 报表制作

报表 Agent 在回复末尾使用 `zenvis:report-document-config` 围栏返回最终 Markdown 或 HTML。后端解析为 `report-document` part，并同步到会话 `extra_data.report`。

右侧工作台支持整篇生成和选区改写；选区改写只返回片段，不要求重新生成完整文档配置。

## 结构化消息

AI 回复会解析为多个 part：

| 输入 | part | 用途 |
| --- | --- | --- |
| Markdown | `markdown` | 普通正文 |
| 代码围栏 | `code` | 带语言的代码 |
| `<think>` | `thinking` | 思考过程 |
| `zenvis:notice` | `notice` | 结构化通知 |
| `zenvis:confirm` | `confirm` | 业务确认 |
| MCP 内部标记 | `mcp-approval` | 工具审批卡片 |
| 报表围栏 | `report-document` | 报表正文与元数据 |
| 提示建议 | `prompt-suggestions` | 可点击开场提示 |

非法的 notice/confirm JSON 会回退成普通 Markdown。

## MCP Server 与 Client

ZenVis 同时具备：

- MCP Server：通过 `/sse` 和 `/mcp/message` 暴露本地工具；
- MCP Client：连接数据库中配置的外部 MCP Server；
- 工具上下文：按 Agent scope 注入本地和外部工具；
- 权限网关：统一策略、审批、授权和调用审计。

外部服务配置包含 code、名称、地址、SSE endpoint、Headers、启用状态和超时。初始化失败不阻断后端启动，但服务会标记为未连接并记录错误。

## 工具策略与审批

工具唯一键：

```text
local::<toolName>
external::<serverId>::<originalToolName>
```

策略：

| 策略 | 行为 |
| --- | --- |
| `ALLOW` | 直接竞争执行权并运行 |
| `ASK` | 创建 pending 请求，等待用户或任务决策 |
| `DENY` | 不触达底层工具，记录 denied |

外部工具只有明确 `readOnlyHint=true` 时默认允许，其他默认询问。人工覆盖优先于默认策略，全局 DENY 始终优先于会话或任务授权。

调用状态包括 `pending`、`approved`、`running`、`succeeded`、`failed`、`rejected`、`denied`、`expired` 和 `cancelled`。条件更新保证审批和执行最多生效一次。

参数和结果不脱敏并默认完整写入 `LONGTEXT`，仅超过数据库类型容量时按 UTF-8 字节边界截取；结果长度保存截取前的 UTF-8 字节数。错误摘要仍会递归脱敏、截断；参数另存 SHA-256，用于两阶段调用校验。

## Chat 内联审批

聊天使用事件流时，命中 ASK 后：

1. 返回 `approval_required`，当前流保持打开；
2. 前端在当前消息显示审批卡片；
3. 用户允许本次、本会话允许或拒绝；
4. 返回 `approval_updated`；
5. 原工具回调继续，模型完成本轮回答。

本会话授权只对当前用户、chat ID 和精确 tool key 生效。停止生成或网络取消会清理本轮仍等待的审批。

确定性数据接入和数据可视化演示对默认 `ASK` 写工具强制逐次审批，已有 `ALLOW` 覆盖和
会话授权不会跳过演示审批；演示卡不提供“本会话始终允许”。全局 `DENY` 仍然优先。

## AI 分析任务

任务支持：

- 模型和 `auto` 选择；
- 任意已扫描且启用的 Skill；
- 立即排队或一次性 `scheduled_time`；
- 优先级排序；
- 最大并发和最大挂起数；
- `AUTO` 或 `MANUAL` 审批模式；
- 取消与队列状态查询。

到期计划任务优先于普通任务，普通任务按优先级降序、创建时间升序执行。HTTP `run-once` 只负责认领并提交后台线程，不在请求线程完成整个任务。

服务重启后，`RUNNING` 或 `WAITING_APPROVAL` 任务使用新的 execution ID 重新入队，旧执行的审批和授权失效。

## Skill 生命周期

Skill 从 `app.paths.skills` 扫描，支持列表、详情、重载、启用与停用。任务只保存 Skill ID，执行前读取最新内容；Skill 被删除或停用时任务失败，而不是静默忽略。

Skill 说明工作方法，不替代工具权限。是否能调用工具仍由 Agent scope 和 MCP 策略决定。

## RAG 文档

插件安装后，`00_doc` 中支持的文档可进入公共向量索引。Vector Store 管理接口用于查看、搜索和删除文档。

- RAG 内容只作为普通问答参考，不作为指令；
- embedding 模型或维度变化后应重建索引；
- 删除向量文档不会删除插件包原始文件；
- 业务 Agent 通过 Skill 和工具获取业务上下文，不读取公共 RAG。

### Redis Search 运维检查

Redis Stack 的向量索引可以使用 `redis-cli` 检查：

```text
FT._LIST
FT.INFO index_dih_rag_vector
FT.SEARCH index_dih_rag_vector "*" LIMIT 0 10
```

向量相似度查询需要使用与索引维度和字段定义匹配的二进制 query vector。不要在不知道索引定义和数据保留策略时执行 `FT.DROPINDEX ... DD`；`DD` 会同时删除索引关联文档。

排查时重点检查索引是否存在、文档数、字段定义、向量维度、距离算法和 embedding 模型是否一致。模型或维度变化应走受控重建，不要尝试让新旧向量混用。

## 配置与排障

主要配置：

| 配置 | 说明 |
| --- | --- |
| `spring.ai.openai.base-url` | OpenAI 兼容地址 |
| `spring.ai.openai.api-key` | API Key |
| `spring.ai.openai.chat.options.model` | 默认模型 |
| `app.ai.embedding.enabled` | 是否启用 RAG |
| `spring.ai.vectorstore.redis.*` | Redis Vector Store |
| `app.ai.mcp.*` | MCP 开关、scope、URL 和审批超时 |
| `app.ai.analysis-task.*` | 后台任务并发、挂起和调度间隔 |

排障顺序：

1. `/api/v1/dih/health` 和模型列表；
2. OpenAI 兼容地址、Key、模型名；
3. Redis Stack、embedding 与向量索引；
4. Skill 启用状态；
5. MCP 服务连接、工具策略、待审批和调用审计；
6. 会话消息与 Spring AI Chat Memory；
7. 后台任务 execution ID、状态和错误。
