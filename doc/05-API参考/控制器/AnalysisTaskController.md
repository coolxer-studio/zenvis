# AnalysisTaskController AI分析任务接口

## 1. 基础信息

- 模块：一次性后台 Agent AI分析任务
- 基础路径：`/api/v1/system/analysis-task`
- 数据格式：JSON，wire 字段使用 `snake_case`
- 鉴权：登录用户；任务 MCP 审批仅任务创建人或超级管理员

AI分析任务提交后在后台运行，关闭页面不会中断。任务可以指定一次性计划时间，也可以按优先级进入普通队列。

## 2. 创建/更新模型

```json
{
  "name": "最近7天 API 调用分析",
  "description": "分析调用趋势和异常点",
  "model": "auto",
  "prompt": "分析最近7天调用量、失败率和异常峰值，并给出建议。",
  "priority": 10,
  "scheduled_time": null,
  "approval_mode": "MANUAL",
  "skill_ids": ["analysis-agent"]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | String | 是 | 任务名称 |
| `description` | String | 否 | 任务说明 |
| `model` | String | 否 | 模型名称；`auto` 或空值由系统自动选择 |
| `prompt` | String | 是 | Agent 分析指令 |
| `priority` | Integer | 否 | 越大越先执行，默认 0 |
| `scheduled_time` | Date | 否 | 一次性计划时间；空值表示进入普通队列 |
| `approval_mode` | Enum | 是 | `AUTO` 或 `MANUAL` |
| `skill_ids` | String[] | 否 | 完整替换语义，只允许已扫描且启用的 Skill |

创建和编辑页面的模型来自：

```http
GET /api/v1/dih/model/list
```

Skill 选项来自：

```http
GET /api/v1/dih/skills/options?enabled=true
```

更新接口采用完整替换语义，`scheduled_time` 和 `skill_ids` 可以显式清空，`approval_mode` 不允许为空。

## 3. 返回模型

任务详情示例：

```json
{
  "id": 1,
  "name": "最近7天 API 调用分析",
  "description": "分析调用趋势和异常点",
  "model": "auto",
  "prompt": "分析最近7天调用量、失败率和异常峰值，并给出建议。",
  "result": null,
  "result_parts": [],
  "error_message": null,
  "status": "WAITING_APPROVAL",
  "status_description": "等待审批",
  "priority": 10,
  "approval_mode": "MANUAL",
  "execution_id": "c26a0ce0-7d4c-4a5e-bb84-d205c9c5ac31",
  "skill_ids": ["analysis-agent"],
  "pending_approval_count": 1,
  "scheduled_time": null,
  "start_time": "2026-07-14T15:00:00.000+08:00",
  "finish_time": null,
  "run_count": 1,
  "create_time": "2026-07-14T14:59:00.000+08:00",
  "update_time": "2026-07-14T15:00:05.000+08:00",
  "create_by": 1
}
```

`result` 保留模型原始输出，继续兼容已有调用方。`result_parts` 使用与 DIH 对话
`ChatMessage.parts` 相同的结构化片段协议，仅由 `GET /{id}/view` 详情接口返回：

```json
[
  {
    "id": "5d57a9f1-93c4-43ae-bc17-6422783b74a8",
    "type": "markdown",
    "content": "## 关键结论\n失败率出现异常峰值。"
  },
  {
    "id": "64680479-d40e-4486-8d87-b6fd404c4b5d",
    "type": "visualization-chart-preview",
    "content": "失败率趋势",
    "metadata": {
      "title": "失败率趋势",
      "option": {
        "xAxis": {"data": ["07-13", "07-14"]},
        "series": [{"type": "line", "data": [0.8, 3.2]}]
      }
    }
  }
]
```

| `result_parts` 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String | 片段标识 |
| `type` | String | `markdown`、`code`、`thinking`、`config`、`report-document`、`analysis-record`、`policy-record`、`visualization-chart-preview` 等 DIH 卡片类型 |
| `content` | String | 片段正文或配置内容 |
| `language` | String | 代码、配置或报表语言 |
| `title` | String | 卡片标题 |
| `level` | String | 提示级别 |
| `status` | String | 卡片业务状态 |
| `metadata` | Object | 卡片扩展数据 |

详情查询会通过 `ChatMessagePartParser` 实时解析 `result`，不依赖新增数据库字段，
因此历史任务无需回填也能获得卡片展示能力。列表、队列和任务变更接口不返回
`result_parts`，避免增加轮询响应体积。

### 状态

| 状态 | 说明 | 允许取消 | 允许编辑/删除/重新入队 |
|---|---|---:|---:|
| `PENDING` | 等待计划时间或执行槽 | 是 | 是 |
| `RUNNING` | 后台 Agent 正在运行 | 是 | 否 |
| `WAITING_APPROVAL` | MCP 调用等待人工审批 | 是 | 否 |
| `CANCELING` | 已发出取消请求 | 否 | 否 |
| `SUCCESS` | 执行成功 | 否 | 是 |
| `FAILED` | 执行失败 | 否 | 是 |
| `CANCELED` | 已取消 | 无需再次取消 | 是 |

## 4. 接口总览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/add` | 创建任务并进入队列 |
| POST | `/{id}/update` | 完整更新非活动任务 |
| DELETE | `/{id}` | 删除非活动任务 |
| DELETE | `/bulk/{ids}` | 批量删除非活动任务 |
| GET | `/list` | 分页查询任务 |
| GET | `/{id}/view` | 查询任务详情和结果 |
| POST | `/{id}/enqueue` | 生成新 execution 并重新入队 |
| POST | `/{id}/cancel` | 取消等待、运行或待审批任务 |
| POST | `/queue/run-once` | 认领一个到期任务并异步提交 |
| GET | `/queue/status` | 查询队列、执行槽和挂起容量 |
| GET | `/{id}/approvals/list` | 查询任务当前待审批请求 |
| POST | `/{id}/approvals/{requestId}/decision` | 提交任务 MCP 审批决定 |

所有响应使用统一包装：

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {}
}
```

HTTP 200 不代表业务成功，调用方必须检查 `status === 0`。

## 5. 创建任务

```http
POST /api/v1/system/analysis-task/add
Content-Type: application/json
```

```bash
curl -X POST "http://localhost:11001/api/v1/system/analysis-task/add" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "每日风险分析",
    "description": "后台分析示例",
    "model": "auto",
    "prompt": "分析最近24小时高风险事件并给出建议。",
    "priority": 50,
    "scheduled_time": null,
    "approval_mode": "MANUAL",
    "skill_ids": ["analysis-agent"]
  }'
```

创建时会校验所选 Skill 存在且启用，并生成首个 `execution_id`。任务初始状态为 `PENDING`。

## 6. 更新任务

```http
POST /api/v1/system/analysis-task/{id}/update
```

请求体与创建相同。`RUNNING`、`WAITING_APPROVAL` 和 `CANCELING` 状态不能更新。

## 7. 查询任务

### 分页列表

```http
GET /api/v1/system/analysis-task/list
```

| 查询参数 | 类型 | 说明 |
|---|---|---|
| `name` | String | 任务名称模糊匹配 |
| `status` | Enum | 任务状态 |
| `model` | String | 模型名称 |
| `approvalMode` | Enum | `AUTO` 或 `MANUAL` |
| `page` | Integer | 页码，从 1 开始 |
| `perPage` | Integer | 每页数量 |

```bash
curl "http://localhost:11001/api/v1/system/analysis-task/list?page=1&perPage=10&status=WAITING_APPROVAL&approvalMode=MANUAL"
```

### 任务详情

```http
GET /api/v1/system/analysis-task/{id}/view
```

返回提示词、原始结果 `result`、DIH 结构化卡片 `result_parts`、错误、Skill、审批数量、
executionId 和时间信息。无结果时 `result_parts` 为空数组。

## 8. 重新入队、取消和删除

### 重新入队

```http
POST /api/v1/system/analysis-task/{id}/enqueue
```

重新入队会：

- 校验任务选择的 Skill 当前仍启用。
- 清理旧 execution 的任务工具授权。
- 生成新的 `execution_id`。
- 清空上一次结果、错误和执行时间。
- 将状态改为 `PENDING`。

### 取消

```http
POST /api/v1/system/analysis-task/{id}/cancel
```

- `PENDING` 直接进入 `CANCELED`。
- `RUNNING` 或 `WAITING_APPROVAL` 先进入 `CANCELING`，后台线程退出后进入 `CANCELED`。
- 取消会终止待审批请求并清理当前 execution 的工具授权。
- 已结束任务不需要取消。

### 删除

```http
DELETE /api/v1/system/analysis-task/{id}
DELETE /api/v1/system/analysis-task/bulk/{ids}
```

活动状态任务不能删除。

## 9. 队列接口

### 手动触发一次调度

```http
POST /api/v1/system/analysis-task/queue/run-once
```

接口只负责原子认领一个到期任务并提交后台执行，不等待模型完成。成功认领时通常返回 `RUNNING` 任务；没有到期任务、没有执行槽或挂起容量已满时 `data` 为空。

### 队列状态

```http
GET /api/v1/system/analysis-task/queue/status
```

```json
{
  "running_task": null,
  "next_task": {"id": 2, "name": "每日风险分析", "status": "PENDING"},
  "pending_count": 3,
  "ready_count": 2,
  "running_count": 0,
  "waiting_approval_count": 1,
  "available_slots": 1,
  "max_suspended": 20,
  "checked_at": "2026-07-14T15:00:00.000+08:00"
}
```

调度规则：到期计划任务优先；普通任务按优先级降序、创建时间升序。后台调度间隔由 `app.ai.analysis-task.dispatch-delay-ms` 控制。

## 10. 任务 MCP 审批

### 查询待审批请求

```http
GET /api/v1/system/analysis-task/{id}/approvals/list?page=1&perPage=20
```

只返回该任务当前处于 `PENDING` 的 MCP 调用。任务创建人和超级管理员可以访问。

### 提交决定

```http
POST /api/v1/system/analysis-task/{id}/approvals/{requestId}/decision
Content-Type: application/json
```

```json
{
  "decision": "approved_task",
  "comment": "本次任务允许持续查询此工具"
}
```

| 决定 | 说明 |
|---|---|
| `approved` | 只允许当前 requestId |
| `approved_task` | 当前 execution、精确 toolKey 持续允许 |
| `rejected` | 拒绝当前调用，底层工具不执行，Agent 继续运行 |

`approved_task` 只适用于 AI分析任务接口。任务审批不设置五分钟超时，会一直等待决定或任务取消。

## 11. 审批模式

- `AUTO`：`ALLOW` 直接执行；`ASK` 自动批准并记录 `TASK_AUTO`；`DENY` 禁止。
- `MANUAL`：`ASK` 进入 `WAITING_APPROVAL`；批准后的当前 execution 授权记录为 `TASK_RUN`。

拒绝不会直接把任务标记为失败。MCP 层返回结构化拒绝结果，Agent 可以继续解释并保存最终分析结果。

## 12. Skill 与服务重启

- 创建、编辑、重新入队和实际执行前都校验 Skill。
- 任务只保存 Skill ID，运行时读取最新内容。
- Skill 被停用或删除后，任务会进入 `FAILED`，错误信息列出具体 Skill。
- 服务重启时，`RUNNING` 和 `WAITING_APPROVAL` 任务会生成新 execution 并从头重新入队。
- 旧审批和旧任务授权失效；重启前已执行的外部副作用无法自动回滚。

## 13. 配置

```properties
app.ai.analysis-task.max-concurrency=1
app.ai.analysis-task.max-suspended=20
app.ai.analysis-task.dispatch-delay-ms=5000
```

| 配置 | 说明 |
|---|---|
| `max-concurrency` | 同时占用正常执行槽的任务数 |
| `max-suspended` | 最多允许挂起等待审批的任务数 |
| `dispatch-delay-ms` | 调度轮询间隔 |

## 14. 相关文档

- [MCP 审批与 AI分析任务快速上手](../../07-AI与数据智能/MCP审批与AI分析任务快速上手.md)
- [MCP Client 与业务 Agent 工具集成设计说明](../../07-AI与数据智能/MCP-Client-Agent-Design.md)
- [SkillController](SkillController.md)
- [McpController](McpController.md)
