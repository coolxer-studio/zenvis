# AI 与 MCP 架构

本文说明 DIH 会话、RAG、业务 Agent、Skill、Workflow、MCP、审批审计、分析任务和报表能力之间的关系。

## 能力全景

```text
DIH 前端
  → ChatController
  → DihChatApplicationService
      ├── 普通问答 → AIChatService → RAG → OpenAI 兼容模型
      ├── 深度思考 → 推理模型与 reasoning 消息
      ├── 数据接入 → DataAccessAgent → Workflow Orchestrator
      ├── 数据可视化 → DataVisualizationAgent → 受控 MCP Tool
      ├── 专项 Skill → PromptDrivenAgentRuntime
      └── 报表制作 → ReportAgent → Report Document / Export
                              │
                              ▼
                   Skill · MCP · Approval · Audit
```

DIH 是应用编排层，不是单一模型代理。它先确定会话分支，再为该分支配置上下文、模型、Skill、工具和输出协议。

## 会话与消息

### 两类持久化

| 数据 | 存储 | 用途 |
| --- | --- | --- |
| ZenVis 会话与消息 | MySQL 平台表 | 页面历史、附件、结构化消息和业务状态 |
| Spring AI Chat Memory | MySQL JDBC Chat Memory | 提供给模型的多轮上下文 |

两者不能互相替代。删除或截断模型上下文不应破坏页面历史，页面消息迁移也不能假设模型记忆自动同步。

### 结构化消息

服务端流式返回文本、思考过程、配置记录、审批卡片、数据分析、图表、报表片段等消息部件。前端按 `type` 渲染并根据 `status` 控制交互。

```text
模型/工作流事件
  → 规范化 Message Part
  → NDJSON 流
  → 前端增量合并
  → 会话消息持久化
```

确认、审批和执行结果必须写回消息状态，刷新页面后不能重新出现已完成动作。

## 普通问答与 RAG

普通问答可以使用公共 RAG，但不加载业务 Skill 和 MCP Tool：

```text
用户问题
  → 附件与历史上下文
  → Redis Vector Store 相似度召回
  → Prompt + RAG Context
  → 模型流式回答
```

Redis Stack 中至少区分公共文档和 Agent 结构化索引。文档以来源标识，插件卸载只清理对应来源。

向量能力可通过配置关闭。关闭后普通聊天仍可使用模型，但依赖 RAG 的管理和召回能力不可用。

## 业务 Agent 与 Skill

业务 Agent 只在用户显式选择 Agent 或 Skill 后启用。Skill 提供：

- 适用 Agent 类型。
- 业务提示词和领域约束。
- 本地工具 allowlist。
- 外部 MCP Server 与工具 allowlist。
- 可选的单轮资源上限覆盖。

运行时工具集合取以下范围的交集：

```text
平台已注册工具
  ∩ Agent scope
  ∩ Skill allowlist
  ∩ 当前会话或任务授权
  ∩ 全局工具策略
```

没有工具声明的 Skill 不会自动获得全部工具。显式空列表表示对应范围不允许任何工具。

## Workflow Orchestrator

数据接入和数据可视化使用共享工作流基础设施：

| 组件 | 职责 |
| --- | --- |
| `WorkflowDefinition` | 定义步骤、转移和完成条件 |
| `WorkflowOrchestrator` | 推进步骤并协调 Agent、工具和用户确认 |
| `WorkflowStateStore` | 保存会话工作流状态 |
| `WorkflowEvidenceService` | 保存 MCP 查询证据和严格候选 |
| `WorkflowActionService` | 执行精确、可审计动作 |
| `WorkflowMetrics` | 记录步骤、失败和耗时 |

工作流保存已确认参数、工具证据、失败账本和下一步动作。模型不能通过自由文本跳过必需的确认与审批。

## 数据接入 Agent

```text
需求澄清
  → 识别数据源与目标实体
  → 生成 Meta 候选
  → 生成 Vectum / PushTask 配置
  → 静态校验
  → 用户确认
  → MCP 审批
  → 应用配置并读回
```

Meta 属性、转换输出和 Sink 字段必须一致。确定性示例可以绕过模型生成，但不能绕过配置校验、用户确认和最终读回。

## 数据可视化 Agent

数据可视化 Agent 具有更窄的工具边界：

- 不接入外部 MCP。
- 不执行任意 SQL。
- 不新增、修改或删除动态实体数据。
- 先查询实体和字段 Meta，再调用实体分析白名单。
- 配置、看板和菜单写入默认进入审批。
- 创建后必须通过查看工具读回校验。

图表预览是临时产物，应用配置、看板和菜单是持久化产物。两者在消息模型和生命周期中必须区分。

## 本地与外部 MCP

### MCP Server

ZenVis 内置 MCP Server 通过 SSE 与消息端点暴露本地工具。工具由 `McpServerToolConfiguration` 注册，并通过独立 MCP Bearer Token 认证。

### MCP Client

ZenVis 也可以作为 MCP Client 连接外部 Server：

```text
数据库中的 MCP Server 配置
  → McpClientService 初始化连接
  → 运行期发现工具
  → 原始名称 + Server code 形成稳定身份
  → 进入策略、Agent scope 和审批链路
```

外部工具是动态内容。文档和 Skill 只声明发现与允许机制，不假设运行期工具永远不变。

## 策略、审批与审计

每个工具具有默认策略和风险等级：

| 策略 | 行为 |
| --- | --- |
| `ALLOW` | 满足 scope 和授权后直接执行 |
| `ASK` | 创建审批请求，批准后执行 |
| `DENY` | 拒绝执行，其他授权不能覆盖 |

```text
工具候选
  → 工具身份规范化
  → 全局策略
  → Agent scope / Skill allowlist
  → 会话或任务授权
  → ALLOW / ASK / DENY
  → 执行状态与审计
```

审批支持会话内联决策和后台分析任务决策。请求、批准、拒绝、超时、取消、执行中、成功和失败都需要形成可查询状态。

## AI 分析任务

分析任务把业务 Agent 放入后台调度：

```text
创建任务
  → 选择模型、Agent、Skill 和审批模式
  → 入队
  → 调度器按并发限制领取
  → Agent 执行与工具审批
  → 结果、日志和审计落库
```

自动审批模式只自动批准符合条件的 `ASK` 工具，不覆盖 `DENY`。任务取消、挂起、恢复和失败必须遵循状态机，不能通过直接改数据库跳过。

## 报表文档

报表 Agent 生成或修改版本化文档：

- 会话保存当前报表关联。
- 文档更新使用版本检查避免覆盖并发修改。
- 用户可以归档、恢复和导出。
- 导出服务根据当前文档生成目标格式。
- 报表工具范围独立于数据接入和数据可视化 Agent。

## 资源上限

平台为单轮 Agent 执行限制：

- 工具调用次数。
- 单次与累计工具结果字符数。
- 累计工具结果 Token。
- 重复失败次数。
- 上下文窗口、附件和历史消息预算。
- 审批等待时间和后台任务并发数。

Skill 可以在允许范围内提供正整数覆盖；缺失或非法值回退平台默认。达到上限后应基于已有证据作答或说明限制，不能通过递归和工具改名绕过。

## 关联文档

- [AI 与数据智能](/04-AI与数据智能/README.md)
- [AI 问答与会话](/04-AI与数据智能/AI问答与会话.md)
- [业务智能体与工作流](/04-AI与数据智能/业务智能体与工作流.md)
- [MCP 服务与工具审批](/04-AI与数据智能/MCP服务与工具审批.md)
- [AI 分析任务与 Skill](/04-AI与数据智能/AI分析任务与Skill.md)
- [RAG 与知识库运维](/04-AI与数据智能/RAG与知识库运维.md)
- [MCP Tool 总览](/08-API参考/MCPtool/总览与接入.md)
- [权限审批与运行约束](/08-API参考/MCPtool/权限审批与运行约束.md)
