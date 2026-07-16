# HomeBoardController API 接口文档

## 基础信息

| 属性 | 值 |
| :--- | :--- |
| 模块 | dashboard（系统状态看板） |
| Controller | `HomeBoardController` |
| 基础路径 | `/api/v1/dashboard/home` |
| 描述 | 提供平台内容概览、服务健康度、AI任务状态和通用实体上报统计 |

## 查询系统状态概览

- 方法：`GET`
- 路径：`/api/v1/dashboard/home/overview`

响应数据包括：

- `status`、`status_description`：`HEALTHY/正常运行` 或 `DEGRADED/部分异常`。
- `summary`：实体类型数、数据推送任务数、AI分析任务数、业务应用服务数。
- `notices`：业务服务异常、未正常运行的推送任务、等待审批的AI任务。
- `service_health`：业务服务实例健康度和近24小时事件数。
- `business_service_status`：业务应用服务实例按实时有效状态（正常、性能下降、故障、离线）的数量分布。
- `analysis_task_status`：所有AI分析任务状态及数量，没有数据的状态补零。
- `recent_analysis_tasks`：最近更新的10个AI分析任务。
- `push_task_source_available`：数据推送服务是否可访问；不可访问时 `push_task_count` 为 `null`。

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "checked_at": "2026-07-15 09:00:00",
    "status": "HEALTHY",
    "status_description": "正常运行",
    "summary": {
      "entity_count": 2,
      "push_task_count": 4,
      "analysis_task_count": 8,
      "business_service_count": 3
    },
    "notices": [],
    "service_health": {
      "ratio": 75,
      "instance_count": 4,
      "up_count": 3,
      "abnormal_count": 1,
      "event_count_24h": 5
    },
    "business_service_status": [
      {"status": "UP", "description": "正常", "count": 3},
      {"status": "DEGRADED", "description": "性能下降", "count": 1},
      {"status": "DOWN", "description": "故障", "count": 0},
      {"status": "OFFLINE", "description": "离线", "count": 0}
    ],
    "analysis_task_status": [],
    "recent_analysis_tasks": [],
    "push_task_source_available": true
  }
}
```

## 查询实体统计

- 方法：`GET`
- 路径：`/api/v1/dashboard/home/entity-statistics`
- 参数：`range`，可选值为 `TODAY`、`YESTERDAY`、`LAST_7_DAYS`，默认 `TODAY`。

今天和昨天返回完整24小时横轴，最近7天返回7个自然日横轴。接口按所选时段总量展示Top 10实体，并返回未展示和未参与统计的实体信息。

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "range": "TODAY",
    "start_time": "2026-07-15 00:00:00",
    "end_time": "2026-07-15 09:00:00",
    "granularity": "HOUR",
    "x_axis": ["00:00", "01:00", "02:00"],
    "series": [
      {"name": "security_event", "label": "安全事件", "data": [0, 3, 7], "total": 10}
    ],
    "ranking": [
      {"name": "security_event", "label": "安全事件", "count": 10}
    ],
    "omitted_entity_count": 0,
    "skipped_entities": []
  }
}
```

## 实体元数据要求

平台在加载元数据时会为每个实体自动注入以下只读创建时间属性，用户无需在元数据 JSON 中自行配置；若旧配置中存在同名同列字段，系统会忽略该定义并替换为内置定义：

```json
{
  "entity": "security_event",
  "name": "zenvis_insert_time",
  "label": "创建时间",
  "description": "创建时间",
  "column_name": "zenvis_insert_time",
  "column_type": "DateTime64(3)",
  "retrieval_type": "date",
  "operators": ["greatthan", "lessthan", "greatequalthan", "lessequalthan"],
  "display_selected": true,
  "must_candidate": false
}
```

- ClickHouse 自动建表时该列使用 `DateTime64(3) DEFAULT now64(3)`，普通数据写入无需传值。
- 已有实体表会在启动时通过 `ADD COLUMN IF NOT EXISTS` 补充该字段，不覆盖已有表结构。
- 系统看板统一使用 `zenvis_insert_time` 统计实体上报趋势；单个实体缺少物理列或查询失败时会在 `skipped_entities` 中返回原因，不影响其他实体。

## 已下线接口

消息、设备和启动统计相关的 `/speed-stat`、`/summary`、`/status`、`/efficiency`、`/real-info`、`/province-city-stat`、`/manufacture-system-stat`、`/msg-trend` 已移除。
