# AI 分析任务 MCP 工具

`AnalysisTaskMcpTool` 注册 10 个工具，用于由 Agent 创建、维护和检查后台 AI 分析任务。工具与 [AnalysisTask REST API](/08-API参考/RestfulAPI/DIH与AI.md#analysistaskcontroller) 使用同一服务和数据模型。

## 工具清单

| 工具名 | 参数 | 返回 | 默认策略 | 风险 | 用途 |
| --- | --- | --- | --- | --- | --- |
| `analysis_task_create` | `request: AnalysisTaskDto` | `AnalysisTaskVo` | `ASK` | `HIGH` | 创建并按计划时间/优先级排队 |
| `analysis_task_update` | `id: Long`、`request: AnalysisTaskDto` | Boolean | `ASK` | `HIGH` | 更新非活动任务 |
| `analysis_task_delete` | `id: Long` | Boolean | `ASK` | `HIGH` | 删除非活动任务 |
| `analysis_task_bulk_delete` | `ids: List<Long>` | Boolean | `ASK` | `HIGH` | 批量删除非活动任务 |
| `analysis_task_list` | `request: AnalysisTaskSearchDto` | `PageRowsVo<AnalysisTaskVo>` | `ALLOW` | `LOW` | 分页查询任务 |
| `analysis_task_view` | `id: Long` | `AnalysisTaskVo` | `ALLOW` | `LOW` | 查询任务详情 |
| `analysis_task_enqueue` | `id: Long` | `AnalysisTaskVo` | `ASK` | `HIGH` | 清理旧执行结果并重新入队 |
| `analysis_task_cancel` | `id: Long` | `AnalysisTaskVo` | `ASK` | `HIGH` | 取消等待、执行或待审批任务 |
| `analysis_task_run_once` | 无 | `AnalysisTaskVo` 或空 | `ASK` | `HIGH` | 提交下一个到期任务，不等待完成 |
| `analysis_task_queue_status` | 无 | `AnalysisTaskQueueVo` | `ALLOW` | `LOW` | 查询执行槽、队列和挂起容量 |

## 任务请求

```json
{
  "request": {
    "name": "每日指标分析",
    "description": "后台分析示例",
    "model": "auto",
    "prompt": "分析最近24小时业务指标变化并给出结论。",
    "priority": 50,
    "scheduled_time": null,
    "approval_mode": "MANUAL",
    "skill_ids": ["continuous-analysis"]
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `name` | 任务名称，必填 |
| `description` | 任务说明 |
| `model` | 模型名称；`auto` 或空由平台选择 |
| `prompt` | Agent 分析指令，必填 |
| `priority` | 越大越先执行 |
| `scheduled_time` | 一次性计划时间；空表示普通队列 |
| `approval_mode` | 必填，`AUTO` 或 `MANUAL` |
| `skill_ids` | 已扫描且启用的 Skill ID |

更新是完整替换语义，`scheduled_time` 和 `skill_ids` 可以显式清空，`approval_mode` 不能省略。

## 查询参数

`analysis_task_list.request` 支持：

```json
{
  "request": {
    "page": 1,
    "per_page": 20,
    "name": "指标",
    "status": "WAITING_APPROVAL",
    "model": "auto",
    "approval_mode": "MANUAL"
  }
}
```

任务详情包含状态、提示词、原始结果、结构化 `result_parts`、错误、执行 ID、Skill、审批数量和时间信息。

## 生命周期

| 状态 | 含义 | 可编辑/删除 |
| --- | --- | --- |
| `PENDING` | 等待计划时间或执行槽 | 是 |
| `RUNNING` | 后台 Agent 执行中 | 否 |
| `WAITING_APPROVAL` | MCP 调用等待人工审批 | 否 |
| `CANCELING` | 已请求取消 | 否 |
| `SUCCESS` | 成功 | 是 |
| `FAILED` | 失败 | 是 |
| `CANCELED` | 已取消 | 是 |

`analysis_task_enqueue` 会：

1. 重新校验所选 Skill。
2. 清除旧 execution 工具授权。
3. 生成新的 `execution_id`。
4. 清空结果、错误和执行时间。
5. 将任务恢复为 `PENDING`。

`analysis_task_cancel` 会终止仍在等待的 MCP 审批，并让执行线程进入取消流程。外部工具已经产生的副作用不会自动回滚。

## 审批模式

- `AUTO`：工具策略 `ALLOW` 直接执行；`ASK` 自动批准并记录任务自动审批；`DENY` 禁止。
- `MANUAL`：`ASK` 将任务置为 `WAITING_APPROVAL`，由任务创建人或超级管理员通过 REST 审批接口处理。

这里的 MCP 工具本身也受平台审批策略控制。例如调用 `analysis_task_create` 需要当前 Agent 工具调用审批，而新建任务运行后又可能对其内部 MCP 工具产生任务审批，两层审批不能混淆。

## 调用示例

查询队列：

```json
{}
```

取消任务：

```json
{
  "id": 42
}
```

重新入队：

```json
{
  "id": 42
}
```

## 安全约束

- 创建前向用户展示任务目标、模型、计划时间、审批模式和 Skill。
- 不允许用 `run_once` 绕过正常队列或并发上限。
- 活动任务不能更新或删除，必须先按状态选择取消或等待结束。
- 批量删除前列出精确任务 ID。
- Skill 被停用或删除后任务可能失败，重新入队前必须重新校验。
