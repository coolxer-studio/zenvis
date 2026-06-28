# AnalysisTask AI分析任务接口文档

**基础信息**
- **模块名称**: AI分析任务
- **基础路径**: `/api/v1/system/analysis-task`
- **协议**: HTTP/HTTPS
- **数据格式**: JSON
- **默认端口**: `11002`

---

## 数据模型定义

### 1. AnalysisTaskDto (AI分析任务传输对象)

```json
{
  "name": "最近7天API调用分析",
  "description": "分析接口调用趋势和异常点",
  "model": "auto",
  "prompt": "请分析最近7天API调用次数、失败率和异常峰值，并给出优化建议。",
  "priority": 10,
  "scheduledTime": "2026-06-28T10:00:00.000+08:00"
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| name | String | 是 | 任务名称 |
| description | String | 否 | 任务描述 |
| model | String | 否 | 模型名称，为空或 `auto` 时使用系统默认模型 |
| prompt | String | 是 | 分析提示词，会作为AI分析任务的核心输入存储 |
| priority | Integer | 否 | 队列优先级，数值越大越先执行，默认 `0` |
| scheduledTime | Date | 否 | 计划执行时间，为空表示立即进入待执行队列 |

### 2. AnalysisTaskVo (AI分析任务视图对象)

```json
{
  "id": 1,
  "name": "最近7天API调用分析",
  "description": "分析接口调用趋势和异常点",
  "model": "auto",
  "prompt": "请分析最近7天API调用次数、失败率和异常峰值，并给出优化建议。",
  "result": "AI分析返回结果",
  "errorMessage": null,
  "status": "SUCCESS",
  "statusDescription": "执行成功",
  "priority": 10,
  "scheduledTime": "2026-06-28T10:00:00.000+08:00",
  "startTime": "2026-06-28T10:00:05.000+08:00",
  "finishTime": "2026-06-28T10:00:20.000+08:00",
  "runCount": 1,
  "createTime": "2026-06-28T09:58:00.000+08:00",
  "updateTime": "2026-06-28T10:00:20.000+08:00",
  "createBy": 1
}
```

**状态说明**:
| 状态值 | 说明 | 是否可重新入队 | 是否可取消 | 是否可删除 |
|-------|------|---------------|-----------|-----------|
| PENDING | 等待执行 | 是 | 是 | 是 |
| RUNNING | 执行中 | 否 | 否 | 否 |
| SUCCESS | 执行成功 | 是 | 是 | 是 |
| FAILED | 执行失败 | 是 | 是 | 是 |
| CANCELED | 已取消 | 是 | 是 | 是 |

### 3. AnalysisTaskSearchDto (AI分析任务搜索对象)

```json
{
  "name": "API",
  "status": "PENDING",
  "model": "gpt-4.1",
  "page": 1,
  "perPage": 10,
  "orderBy": "updateTime",
  "orderDir": "desc"
}
```

**查询字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| name | String | 否 | 按任务名称模糊搜索 |
| status | AnalysisTaskStatus | 否 | 按任务状态过滤 |
| model | String | 否 | 按模型名称过滤 |
| page | Integer | 否 | 页码，默认 `1` |
| perPage | Integer | 否 | 每页条数，默认 `10` |
| orderBy | String | 否 | 当前接口保留字段 |
| orderDir | String | 否 | 当前接口保留字段 |

### 4. AnalysisTaskQueueVo (队列状态对象)

```json
{
  "runningTask": null,
  "nextTask": {
    "id": 1,
    "name": "最近7天API调用分析",
    "status": "PENDING"
  },
  "pendingCount": 3,
  "readyCount": 2,
  "runningCount": 0,
  "checkedAt": "2026-06-28T10:00:00.000+08:00"
}
```

### 5. ResponseWrap (统一响应格式)

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {}
}
```

---

## 接口总览

| 序号 | HTTP方法 | 接口路径 | 接口名称 | 功能描述 |
|:---:|:-------:|---------|---------|---------|
| 1 | POST | `/api/v1/system/analysis-task/add` | 创建分析任务 | 创建任务并进入等待队列 |
| 2 | DELETE | `/api/v1/system/analysis-task/{id}` | 删除分析任务 | 删除非执行中的任务 |
| 3 | DELETE | `/api/v1/system/analysis-task/bulk/{ids}` | 批量删除分析任务 | 批量删除非执行中的任务 |
| 4 | POST | `/api/v1/system/analysis-task/{id}/update` | 更新分析任务 | 更新非执行中的任务 |
| 5 | GET | `/api/v1/system/analysis-task/list` | 查询任务列表 | 分页查询任务 |
| 6 | GET | `/api/v1/system/analysis-task/{id}/view` | 查询任务详情 | 查询任务提示词、结果和状态 |
| 7 | POST | `/api/v1/system/analysis-task/{id}/enqueue` | 重新入队 | 将任务状态重置为 `PENDING` |
| 8 | POST | `/api/v1/system/analysis-task/{id}/cancel` | 取消任务 | 取消非执行中的任务 |
| 9 | POST | `/api/v1/system/analysis-task/queue/run-once` | 手动执行一次 | 立即尝试取出一个到期任务执行 |
| 10 | GET | `/api/v1/system/analysis-task/queue/status` | 查询队列状态 | 查询当前执行任务、下一个任务和队列数量 |

---

## 接口详情

### 1. 创建分析任务

**接口地址**: `POST /api/v1/system/analysis-task/add`

**功能描述**: 创建一个AI分析任务，任务默认进入 `PENDING` 状态。定时调度器每分钟扫描一次，到期后按优先级取出执行。

**请求参数**:
- Content-Type: `application/json`
- Body: AnalysisTaskDto

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/analysis-task/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "最近7天API调用分析",
    "description": "分析接口调用趋势和异常点",
    "model": "auto",
    "prompt": "请分析最近7天API调用次数、失败率和异常峰值，并给出优化建议。",
    "priority": 10
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "id": 1,
    "name": "最近7天API调用分析",
    "status": "PENDING",
    "statusDescription": "等待执行",
    "priority": 10,
    "runCount": 0
  }
}
```

---

### 2. 删除分析任务

**接口地址**: `DELETE /api/v1/system/analysis-task/{id}`

**功能描述**: 根据ID删除任务。`RUNNING` 状态任务不允许删除。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求示例**:
```bash
curl -X DELETE http://localhost:11001/api/v1/system/analysis-task/1
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "请求成功",
  "data": "删除成功"
}
```

---

### 3. 批量删除分析任务

**接口地址**: `DELETE /api/v1/system/analysis-task/bulk/{ids}`

**功能描述**: 批量删除任务。任一任务处于 `RUNNING` 状态时，该任务不允许删除。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| ids | List\<Long\> | 是 | 任务ID列表，多个ID用英文逗号分隔 |

**请求示例**:
```bash
curl -X DELETE http://localhost:11001/api/v1/system/analysis-task/bulk/1,2,3
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "请求成功",
  "data": "删除成功"
}
```

---

### 4. 更新分析任务

**接口地址**: `POST /api/v1/system/analysis-task/{id}/update`

**功能描述**: 更新非执行中的任务信息。`RUNNING` 状态任务不允许更新。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求参数**:
- Content-Type: `application/json`
- Body: AnalysisTaskDto

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/analysis-task/1/update \
  -H "Content-Type: application/json" \
  -d '{
    "name": "最近30天API调用分析",
    "description": "扩大统计窗口，补充趋势分析",
    "model": "auto",
    "prompt": "请分析最近30天API调用趋势、失败率、异常峰值和优化建议。",
    "priority": 20
  }'
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "请求成功",
  "data": "修改成功"
}
```

---

### 5. 查询任务列表

**接口地址**: `GET /api/v1/system/analysis-task/list`

**功能描述**: 分页查询AI分析任务，支持按名称、状态、模型过滤。

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| name | String | 否 | 任务名称模糊搜索 |
| status | AnalysisTaskStatus | 否 | 状态过滤，如 `PENDING`、`SUCCESS` |
| model | String | 否 | 模型过滤 |
| page | Integer | 否 | 页码，默认 `1` |
| perPage | Integer | 否 | 每页条数，默认 `10` |

**请求示例**:
```bash
curl -X GET "http://localhost:11001/api/v1/system/analysis-task/list?page=1&perPage=10&status=PENDING"
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "rows": [
      {
        "id": 1,
        "name": "最近7天API调用分析",
        "status": "PENDING",
        "statusDescription": "等待执行",
        "priority": 10,
        "runCount": 0
      }
    ],
    "total": 1
  }
}
```

---

### 6. 查询任务详情

**接口地址**: `GET /api/v1/system/analysis-task/{id}/view`

**功能描述**: 根据ID查询任务详情，包括提示词、AI返回结果、错误信息和执行时间。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/analysis-task/1/view
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "id": 1,
    "name": "最近7天API调用分析",
    "prompt": "请分析最近7天API调用次数、失败率和异常峰值，并给出优化建议。",
    "result": "AI分析返回结果",
    "errorMessage": null,
    "status": "SUCCESS",
    "runCount": 1
  }
}
```

---

### 7. 重新入队

**接口地址**: `POST /api/v1/system/analysis-task/{id}/enqueue`

**功能描述**: 将任务重新放回队列，状态重置为 `PENDING`，并清空上一次结果、错误和执行时间。`RUNNING` 状态任务不允许重新入队。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/analysis-task/1/enqueue
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "id": 1,
    "status": "PENDING",
    "result": null,
    "errorMessage": null
  }
}
```

---

### 8. 取消任务

**接口地址**: `POST /api/v1/system/analysis-task/{id}/cancel`

**功能描述**: 取消非执行中的任务，状态变为 `CANCELED`。`RUNNING` 状态任务不允许取消。

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|-------|------|-----|------|
| id | Long | 是 | 任务ID |

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/analysis-task/1/cancel
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "id": 1,
    "status": "CANCELED",
    "statusDescription": "已取消"
  }
}
```

---

### 9. 手动执行一次队列

**接口地址**: `POST /api/v1/system/analysis-task/queue/run-once`

**功能描述**: 立即尝试从队列中取出一个到期的 `PENDING` 任务执行。若当前已有 `RUNNING` 任务，或没有到期任务，则返回成功但 `data` 为空。

**请求示例**:
```bash
curl -X POST http://localhost:11001/api/v1/system/analysis-task/queue/run-once
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "id": 1,
    "status": "SUCCESS",
    "result": "AI分析返回结果",
    "runCount": 1
  }
}
```

---

### 10. 查询队列状态

**接口地址**: `GET /api/v1/system/analysis-task/queue/status`

**功能描述**: 查询当前队列状态，包括当前执行任务、下一个等待任务、等待任务数、到期可执行任务数和执行中任务数。

**请求示例**:
```bash
curl -X GET http://localhost:11001/api/v1/system/analysis-task/queue/status
```

**成功响应**:
```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "runningTask": null,
    "nextTask": {
      "id": 1,
      "name": "最近7天API调用分析",
      "status": "PENDING"
    },
    "pendingCount": 1,
    "readyCount": 1,
    "runningCount": 0,
    "checkedAt": "2026-06-28T10:00:00.000+08:00"
  }
}
```

---

## 接口测试

以下脚本覆盖创建、列表、队列状态、手动执行、详情、重新入队、取消和删除。若环境开启登录认证，请先登录并将 Cookie 写入 `COOKIE` 变量。

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:11001}"
COOKIE="${COOKIE:-}"

AUTH_ARGS=()
if [ -n "$COOKIE" ]; then
  AUTH_ARGS=(-b "$COOKIE")
fi

echo "1. 创建立即执行的分析任务"
CREATE_RESPONSE=$(curl -sS -X POST "$BASE_URL/api/v1/system/analysis-task/add" \
  "${AUTH_ARGS[@]}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "接口测试-分析任务",
    "description": "接口文档自动测试任务",
    "model": "auto",
    "prompt": "请用三句话分析测试数据：总调用量1000，失败量25，平均响应时间120ms。",
    "priority": 100
  }')
echo "$CREATE_RESPONSE"

TASK_ID=$(echo "$CREATE_RESPONSE" | sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p' | head -1)
if [ -z "$TASK_ID" ]; then
  echo "创建任务失败，未解析到任务ID"
  exit 1
fi

echo "2. 查询任务列表"
curl -sS -X GET "$BASE_URL/api/v1/system/analysis-task/list?page=1&perPage=10&name=接口测试" \
  "${AUTH_ARGS[@]}"
echo

echo "3. 查询队列状态"
curl -sS -X GET "$BASE_URL/api/v1/system/analysis-task/queue/status" \
  "${AUTH_ARGS[@]}"
echo

echo "4. 手动执行一次队列"
curl -sS -X POST "$BASE_URL/api/v1/system/analysis-task/queue/run-once" \
  "${AUTH_ARGS[@]}"
echo

echo "5. 查询任务详情，检查 result 或 errorMessage"
curl -sS -X GET "$BASE_URL/api/v1/system/analysis-task/$TASK_ID/view" \
  "${AUTH_ARGS[@]}"
echo

echo "6. 重新入队"
curl -sS -X POST "$BASE_URL/api/v1/system/analysis-task/$TASK_ID/enqueue" \
  "${AUTH_ARGS[@]}"
echo

echo "7. 取消任务"
curl -sS -X POST "$BASE_URL/api/v1/system/analysis-task/$TASK_ID/cancel" \
  "${AUTH_ARGS[@]}"
echo

echo "8. 删除任务"
curl -sS -X DELETE "$BASE_URL/api/v1/system/analysis-task/$TASK_ID" \
  "${AUTH_ARGS[@]}"
echo

echo "分析任务接口测试完成"
```

**测试说明**:
- `queue/run-once` 会真实调用AI模型；如果本地未配置有效 OpenAI API Key，项目的本地 fallback 模型会返回“AI 对话暂不可用”的提示，该结果仍会写入任务 `result`。
- 定时任务每隔1分钟自动执行一次队列；接口测试中使用 `queue/run-once` 是为了立即验证执行链路。
- `RUNNING` 状态任务不能更新、删除、取消或重新入队。

---

## 响应码汇总

| 响应码 | 说明 | 触发场景 |
|--------|------|---------|
| 0 | 请求成功 | 接口调用成功 |
| 1 | 请求失败 | 通用服务端失败 |
| 99 | 不支持的操作 | 执行中的任务被更新、删除、取消或重新入队 |
| 301 | 必填字段不能为空 | 创建或更新时 `name`、`prompt` 为空 |

---

## 注意事项

1. **队列调度**: 系统启动后每60秒扫描一次到期 `PENDING` 任务。
2. **执行顺序**: 队列按 `priority DESC`、`scheduledTime ASC`、`createTime ASC`、`id ASC` 取下一个任务。
3. **单任务执行**: 同一应用实例内使用执行锁保护，同一时间只执行一个AI分析任务。
4. **结果持久化**: 任务提示词存储在 `prompt`，AI返回结果存储在 `result`，异常信息存储在 `errorMessage`。
5. **启动恢复**: 服务启动时若发现历史 `RUNNING` 任务，会标记为 `FAILED`，可通过重新入队再次执行。
