# AI 分析任务与 Skill

本文说明后台 AI 分析任务的创建、调度、审批和恢复，以及 Skill 的加载、聊天入口、工具边界和资源预算。

## 先区分两个概念

| 概念 | 作用 |
| --- | --- |
| AI 分析任务 | 在后台排队或按计划执行一次分析，保存状态、结果、错误和工具审计 |
| Skill | 向 Agent 提供工作规范、提示词、工具 allowlist、聊天入口和可选资源上限 |

Skill 不是工具权限本身，也不是后台任务。任务可以选择已启用 Skill；工具最终仍受平台注册、Agent scope、Skill allowlist 和 MCP 策略共同限制。

## AI 分析任务

### 创建任务

任务主要配置：

| 配置 | 说明 |
| --- | --- |
| 名称、描述 | 用于列表和运维识别 |
| 模型 | 与 Chat 模型列表一致；`auto` 由运行时选择 |
| Prompt | 本次后台分析目标 |
| 优先级 | 普通就绪任务按优先级降序执行 |
| `scheduled_time` | 可选的一次性计划时间 |
| `approval_mode` | 必填，`AUTO` 或 `MANUAL` |
| Skill | 当前已扫描且启用的一个或多个 Skill |

`scheduled_time` 为空时任务立即进入队列。任务是一次性执行实例，完成后保持终态，不承担周期续排。

### 周期配置

周期配置保存任务模板、`cron_expression`、启用状态、上下次触发时间、生成次数和最近错误。Cron 使用部署实例时区和包含秒的 Spring 6 段格式。新建或重新启用时从下一个匹配时间开始，不立即创建任务。

调度器默认每 5 秒扫描一次。到点后在数据库锁保护的事务中创建新的 `AnalysisTask`，复制名称、Prompt、模型、优先级、审批模式、Skill 和创建人，并写入 `schedule_id`、`schedule_fire_time`。同一周期触发时间有唯一约束；上一任务未完成也不妨碍下一周期创建，由队列控制并发。

默认误触发宽限为 60 秒，超过宽限的历史触发会跳过并推进到下一个未来时间。Skill 不可用或任务生成失败时不反复热重试，而是记录错误并推进周期。停用或删除配置只阻止未来投递，不修改或取消已生成任务。

### 调度顺序

```text
到期的计划任务
  → 普通就绪任务
      → 优先级降序
      → 创建时间升序
```

`run-once` 只负责原子认领并提交后台线程，不在 HTTP 请求线程中完成整次分析。关闭浏览器不会中断已提交任务。

当前生产基线：

| 配置 | 默认值 | 说明 |
| --- | ---: | --- |
| `app.ai.analysis-task.max-concurrency` | `1` | 同时运行的普通任务数 |
| `app.ai.analysis-task.max-suspended` | `20` | 等待审批任务容量 |
| `app.ai.analysis-task.dispatch-delay-ms` | `5000` | 调度扫描间隔 |
| `app.ai.analysis-task.schedule-dispatch-delay-ms` | `5000` | 周期配置扫描间隔 |
| `app.ai.analysis-task.schedule-misfire-grace-ms` | `60000` | 周期误触发宽限 |

等待人工审批的任务释放普通执行槽，但仍占用挂起容量。

### 状态

| 状态 | 含义 | 可执行操作 |
| --- | --- | --- |
| `PENDING` | 等待计划时间或执行槽 | 编辑、删除、取消、重新入队 |
| `RUNNING` | 后台执行中 | 查看、取消 |
| `WAITING_APPROVAL` | 等待 MCP 人工审批 | 审批、拒绝、取消 |
| `CANCELING` | 已请求取消，等待执行线程退出 | 查看 |
| `SUCCESS` | 执行成功 | 查看结果、重新入队 |
| `FAILED` | 执行失败 | 查看错误、修正后重新入队 |
| `CANCELED` | 已取消 | 查看、重新入队 |

活动任务不能编辑或删除。不要直接修改数据库状态绕过取消和执行清理。

### `execution_id`

每个新任务、重新入队或恢复执行都使用新的 `execution_id`。它用于隔离：

- 本次工具调用与审计；
- 本次待审批请求；
- `approved_task` 工具授权；
- 本次结果、错误和执行时间。

重新入队会清理旧结果并创建新的 execution。旧 execution 的审批和工具授权不能复用。周期配置每次生成不同的任务 ID 和 execution ID，因此各轮结果、错误和 MCP 审计天然独立。

### 审批模式

#### `AUTO`

- 有效策略为 `ALLOW`：直接执行；
- 有效策略为 `ASK`：自动批准并记录 `TASK_AUTO`；
- 有效策略为 `DENY`：始终拒绝。

`AUTO` 适合工具范围已经过管理员审查、任务可无人值守执行的场景。它不是绕过全局策略的超级权限。

#### `MANUAL`

有效策略为 `ASK` 时：

1. 任务进入 `WAITING_APPROVAL`；
2. 当前普通执行槽被释放；
3. 任务创建人或超级管理员处理；
4. 批准后任务继续，拒绝后 Agent 获得结构化拒绝结果；
5. 任务完成或取消后清理 execution 授权。

任务审批支持：

| 决定 | 作用 |
| --- | --- |
| `approved` | 只允许当前请求 |
| `approved_task` | 当前 execution 内持续允许精确工具 |
| `rejected` | 拒绝当前请求 |

任务人工审批不会使用普通 Chat 审批超时；它可以持续等待决定或任务取消。

### 服务重启

后端启动时会检查活动任务：

- 原 `RUNNING` 或 `WAITING_APPROVAL` 任务生成新的 `execution_id`；
- 任务从头重新入队；
- 旧 execution 的审批和授权失效；
- 已经对外部系统产生的副作用不会自动回滚。

因此任务使用写工具时必须依赖业务幂等标识和读回，不应假设重启后能从任意工具调用中间点继续。

## Skill

### 来源与目录

Skill 根目录由：

```properties
app.paths.skills=${app.paths.config.base:.}/skill_config
```

决定。开放配置中的典型结构：

```text
skill_config/
├── index.json
├── data-access-agent/
│   ├── skill.json
│   └── SKILL.md
├── data-visualization-agent/
│   ├── skill.json
│   └── SKILL.md
└── report-agent/
    ├── skill.json
    └── SKILL.md
```

插件安装的运行时 Skill 也会进入受控 Skill 根目录。根仓库 `agent-skills` 是研发侧 Codex Skill，不是 ZenVis 运行时配置。

### `skill.json`

`skill.json` 负责机器可读配置：

- 稳定 `id`、名称、说明和启用状态；
- 适用的 `agentTypes`；
- 是否提供聊天入口、显示名称、图标和顺序；
- `runtime.tools.local` 本地工具 allowlist；
- `runtime.tools.mcp` 外部 MCP Server 与原始工具名 allowlist；
- 可选 `runtime.limits`。

`SKILL.md` 保存实际工作规范、业务边界、步骤和输出要求。任务只保存 Skill ID，执行前读取最新启用状态与内容。

### 启用、停用和重载

- 启动时扫描 Skill 根目录；
- 管理页可查看列表和详情；
- 重载会重新扫描目录；
- 启用/停用会写回对应配置；
- 未找到、无效或已停用 Skill 不会被静默忽略；
- 任务执行前发现 Skill 已删除或停用时进入失败。

修改 Skill 文件后，应执行重载并重新打开聊天入口或任务表单验证。

### 聊天入口

内置入口使用固定 Agent 类型：

- `agent_data_access`
- `agent_data_visualization`
- `agent_report`

插件或自定义 Skill 可以暴露 `skill:<skillId>`。未绑定内置智能体的入口使用通用 Skill 运行时，只加载当前 Skill。

没有启用聊天入口的 Skill 仍可以用于后台任务；是否可聊天与是否可用于任务是不同配置维度。

### 工具边界

运行时工具集合取以下范围的交集：

```text
平台已注册且可用工具
  ∩ Agent scope
  ∩ Skill allowlist
  ∩ 专属 Agent 限制
  ∩ 全局工具策略
  ∩ 当前会话或 execution 授权
```

- Skill 未声明 `runtime.tools` 时按 fail-closed 处理，不自动获得全部工具。
- 显式空列表表示不给该 Skill 对应工具。
- 外部 MCP allowlist 的 key 使用 Server `code`，工具使用外部原始名称。
- 全局 `DENY` 不能被 Skill、会话授权或任务授权覆盖。

修改工具范围后，可以通过 `/api/v1/dih/mcp/agent/prompt` 查看目标 Agent 的实际工具提示词。

### 资源预算

平台默认：

| 配置 | 默认值 |
| --- | ---: |
| `app.ai.dih.agent.default-limits.max-tool-calls` | `8` |
| `app.ai.dih.agent.default-limits.max-repeated-failures` | `2` |
| `app.ai.dih.agent.default-limits.max-tool-result-chars` | `8000` |
| `app.ai.dih.agent.default-limits.max-accumulated-tool-result-chars` | `24000` |
| `app.ai.dih.agent.default-limits.max-accumulated-tool-result-tokens` | `12000` |
| `app.ai.skill.max-selected-prompt-chars` | `32000` |

Skill 可以在平台允许范围内提供正整数覆盖。缺失、非法或非正数值回退平台默认。达到预算后 Agent 应使用已有证据作答或明确停止，不能通过重复调用、递归或工具改名绕过。

## 常见问题

### 创建任务时没有可选模型

检查 `/api/v1/dih/model/list`、默认模型和 OpenAI 兼容模型目录。`auto` 存在不代表模型服务可用。

### 创建任务时没有可选 Skill

确认 Skill 已被扫描、配置有效且处于启用状态，然后执行 Skill 重载。任务不能选择未启用 Skill。

### 任务一直是 `PENDING`

检查：

- `scheduled_time` 是否到期；
- 是否达到最大并发；
- 调度器是否运行；
- 优先级和更早到期任务；
- 队列状态接口中的就绪和运行数量。

### 任务一直是 `WAITING_APPROVAL`

检查任务详情中的待审批请求、任务所有者或超级管理员权限，以及挂起容量。任务审批不会按 Chat 超时自动过期。

### 取消后长期停在 `CANCELING`

检查后台执行线程、外部工具是否支持及时取消以及工具超时。强制重启前应确认外部副作用和后续重入幂等性。

### 重新入队后旧授权无效

这是预期行为。重新入队生成新的 `execution_id`，旧 execution 的 `approved_task` 不再生效。

### Skill 在列表中但聊天入口没有出现

检查 `chat.enabled`、显示配置和 Skill 启用状态。用于后台任务不等于自动暴露聊天入口。

### Skill 声明了工具但 Agent 看不到

检查工具是否注册/发现、Agent scope、Server `code`、工具原始名称、专属限制和全局 `DENY`。

## 管理员验收

1. Skill 列表、详情、重载、启用和停用正常。
2. 三个内置入口与对应 Skill 状态一致。
3. 自定义聊天 Skill 使用 `skill:<skillId>`，停用后不能继续创建新执行。
4. 未声明工具的 Skill 不会获得全部工具。
5. 任务按计划时间、优先级和并发限制调度。
6. `AUTO` 不能覆盖 `DENY`。
7. `MANUAL` 能进入等待审批，并支持单次或 execution 授权。
8. 取消、重新入队和服务重启都会正确更新 `execution_id`。
9. 达到挂起容量或工具预算时有明确错误，不静默丢任务。
10. 任务结果、错误和 MCP 审计可以按 execution 关联。

## 关联文档

- [AI 与数据智能](/04-AI与数据智能/README.md)
- [业务智能体与工作流](/04-AI与数据智能/业务智能体与工作流.md)
- [MCP 服务与工具审批](/04-AI与数据智能/MCP服务与工具审批.md)
- [DIH 与 AI API](/08-API参考/RestfulAPI/DIH与AI.md)
- [MCP Tool 权限、审批与运行约束](/08-API参考/MCPtool/权限审批与运行约束.md)
