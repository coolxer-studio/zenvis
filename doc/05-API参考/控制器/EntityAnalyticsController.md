# EntityAnalyticsController

实体统计分析接口统一使用 `POST /api/v1/entity/{analysis}/query`。所有字段名称均为
Meta 中的逻辑字段名，服务端负责校验并解析物理表和列。

## 统一响应

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "meta": {
      "query_type": "distribution",
      "time_zone": "Asia/Shanghai",
      "preset": "ALL_TIME",
      "start_time": null,
      "end_time": null,
      "comparison": "NONE",
      "granularity": null,
      "result_count": 2,
      "fields": ["addr_src"],
      "truncated": false
    },
    "result": {
      "columns": [{"name": "bucket"}, {"name": "value"}],
      "rows": [{"bucket": "192.0.2.1", "value": 12}]
    },
    "echarts": {
      "chart_type": "bar",
      "option": {
        "dataset": {"source": [{"bucket": "192.0.2.1", "value": 12}]},
        "xAxis": {"type": "value"},
        "yAxis": {"type": "category"},
        "series": []
      }
    }
  }
}
```

`echarts.option` 是纯 JSON，可直接传给 ECharts `setOption`。统计接口不返回 JavaScript
函数，也不绑定固定主题或颜色。

## 公共查询能力

- 时间预设：`TODAY`、`YESTERDAY`、`LAST_24_HOURS`、`LAST_7_DAYS`、`LAST_30_DAYS`、
  `THIS_MONTH`、`CUSTOM`。概览、汇总、分布和值统计额外支持 `ALL_TIME`。
- `LAST_24_HOURS` 使用当前时间向前滚动 24 小时，不按自然日截断。
- 时间粒度：`AUTO`、`MINUTE`、`FIVE_MINUTES`、`FIFTEEN_MINUTES`、
  `HOUR`、`DAY`、`WEEK`、`MONTH`、`QUARTER`、`YEAR`，最多 1000 个桶。
- 对比方式：`NONE`、`PREVIOUS_PERIOD`、`YEAR_OVER_YEAR`，用于概览、汇总和趋势。
- 条件格式：`criteria_list[{attribute,operator,value_list}]`，
  `criteria_logic` 仅支持 `and` 或 `or`。
- 时间字段默认使用 `zenvis_insert_time`；可通过请求或映射中的 `time_field`
  指定 Meta 内的其他日期时间逻辑字段。
- 单次最多 50 个实体/映射、20 个指标、50 个条件；自定义时间跨度最大 3 年。
- 分组和关系字段只支持标量类型；数组、Map、JSON、Object 和 Tuple 不支持。

## 多实体概览

`POST /api/v1/entity/overview/query`

```json
{
  "entities": ["asset_host", "asset_pc"],
  "time_range": {"preset": "TODAY"},
  "time_field": "zenvis_insert_time",
  "comparison": "PREVIOUS_PERIOD"
}
```

每个实体返回 `all_time_count`、`current_value`、`comparison_value`、`delta` 和
`delta_rate`。

## 指标汇总

`POST /api/v1/entity/summary/query`

```json
{
  "entity": "traffic",
  "metrics": [
    {"name": "records", "operation": "COUNT", "label": "记录数"},
    {"name": "bytes", "operation": "SUM", "field": "bytes_sent", "label": "发送字节"}
  ],
  "time_range": {"preset": "LAST_30_DAYS"},
  "time_field": "event_time"
}
```

支持 `COUNT`、`DISTINCT_COUNT`、`SUM`、`AVG`、`MIN`、`MAX` 和
`PERCENTILE`。百分位指标必须提供 `(0,1)` 范围内的 `percentile`，例如
`{"operation":"PERCENTILE","field":"latency","percentile":0.95}`。

## 时间趋势

`POST /api/v1/entity/trend/query`

简单计数趋势：

```json
{
  "entities": ["asset_host", "asset_pc"],
  "time_range": {"preset": "LAST_7_DAYS"},
  "granularity": "DAY"
}
```

自定义指标趋势：

```json
{
  "series": [{
    "entity": "traffic",
    "label": "发送流量",
    "metric": {"operation": "SUM", "field": "bytes_sent"},
    "time_field": "event_time"
  }],
  "time_range": {"preset": "LAST_30_DAYS"},
  "granularity": "AUTO"
}
```

## 字段分组 TopN

`POST /api/v1/entity/distribution/query`

```json
{
  "entity": "traffic",
  "dimension": "addr_src",
  "criteria_list": [{
    "attribute": "action",
    "operator": "equal",
    "value_list": ["deny"]
  }],
  "criteria_logic": "and",
  "time_range": {"preset": "LAST_7_DAYS"},
  "limit": 100,
  "include_null": false
}
```

跨实体分组：

```json
{
  "mappings": [
    {"entity": "asset_host", "dimension": "risk", "label": "主机"},
    {"entity": "asset_pc", "dimension": "risk", "label": "终端"}
  ],
  "time_range": {"preset": "ALL_TIME"},
  "limit": 10
}
```

`limit` 默认 10、最大 100，按数量降序和字段值升序稳定排序。

## 多维聚合

`POST /api/v1/entity/aggregate/query`，对应 MCP `entity_aggregate`。

```json
{
  "entity": "traffic",
  "dimensions": [
    {
      "name": "day",
      "field": "event_time",
      "kind": "TIME",
      "granularity": "DAY"
    },
    {
      "name": "action",
      "field": "action",
      "kind": "FIELD",
      "include_null": false
    }
  ],
  "metrics": [
    {"name": "records", "operation": "COUNT", "label": "事件数"},
    {"name": "bytes", "operation": "SUM", "field": "bytes_sent", "label": "发送字节"}
  ],
  "time_range": {"preset": "LAST_30_DAYS"},
  "criteria_list": [],
  "criteria_logic": "and",
  "order_by": {"field": "day", "direction": "asc"},
  "limit": 200,
  "chart_hint": "AREA"
}
```

单实体最多两个维度、20 个指标、50 个条件、1000 个结果单元和 20 个图表序列。
`chart_hint` 支持 `AUTO`、`BAR`、`LINE`、`AREA`、`PIE`、`HEATMAP`。

## 数值直方图

`POST /api/v1/entity/histogram/query`，对应 MCP `entity_histogram`。

```json
{
  "entity": "traffic",
  "field": "bytes_sent",
  "bins": 20,
  "min": 0,
  "max": 100000,
  "time_range": {"preset": "LAST_7_DAYS"},
  "criteria_list": [],
  "criteria_logic": "and"
}
```

`field` 必须是 Meta 数值逻辑字段；`bins` 范围为 5–100。结果返回每个区间的
`lower_bound`、`upper_bound`、`label` 和 `value`，所有桶计数之和与 `total`
一致。

## 散点图与气泡图

`POST /api/v1/entity/scatter/query`，对应 MCP `entity_scatter`。

```json
{
  "entity": "traffic",
  "x_field": "bytes_sent",
  "y_field": "latency",
  "size_field": "packet_count",
  "category_field": "action",
  "label_field": "session_id",
  "time_range": {"preset": "LAST_7_DAYS"},
  "sort_by": "event_time",
  "order": "desc",
  "limit": 500
}
```

X、Y 和气泡大小字段必须是 Meta 数值逻辑字段。默认返回 500、最大 2000 个点；
空 X/Y 点被排除，结果按指定字段及 X/Y 稳定排序，并通过 `has_more` 和
`meta.truncated` 标识截断。分类图表序列最多 20 个。

## 指定值统计

`POST /api/v1/entity/value-statistics/query`

```json
{
  "focus_value": "192.0.2.1",
  "time_range": {"preset": "ALL_TIME"},
  "mappings": [{
    "entity": "traffic",
    "match_fields": ["addr_src", "addr_dst"]
  }]
}
```

同一映射中的 `match_fields` 使用 OR 语义，同一数据行最多计数一次。字段和值不限定为
IP。

## 关系聚合

`POST /api/v1/entity/relations/query`

```json
{
  "focus_value": "192.0.2.1",
  "time_range": {
    "preset": "CUSTOM",
    "start_time": "2026-07-01T00:00:00",
    "end_time": "2026-07-08T00:00:00"
  },
  "limit": 50,
  "mappings": [{
    "entity": "traffic",
    "source_field": "addr_src",
    "target_field": "addr_dst",
    "time_field": "event_time"
  }]
}
```

返回对端值、入向/出向数量、实体明细和 ECharts graph 配置。

## 关系时间轴

`POST /api/v1/entity/relation-timeline/query`

```json
{
  "focus_value": "192.0.2.1",
  "time_range": {"preset": "LAST_7_DAYS"},
  "granularity": "AUTO",
  "category_limit": 10,
  "mappings": [{
    "entity": "security_event",
    "source_field": "src",
    "target_field": "dst",
    "time_field": "event_time",
    "category_field": "event_code",
    "category_extraction": {
      "type": "SUBSTRING",
      "start": 2,
      "length": 6
    }
  }]
}
```

`category_extraction.type` 仅支持 `DIRECT` 和 `SUBSTRING`；分类数量最大 20。

## 条件抽样

条件抽样继续使用 `POST /api/v1/retrieval/do` 或 MCP `retrieval_search`：

```json
{
  "entity": "traffic",
  "criteria_list": [],
  "criteria_logic": "and",
  "display_list": [{
    "entity": "traffic",
    "attribute_list": ["event_time", "addr_src", "addr_dst"]
  }],
  "page": 1,
  "size": 100,
  "sort_by": "event_time",
  "order": "desc"
}
```

`size` 最大为 100。未指定 `sort_by` 时使用实体配置的默认排序字段；抽样不是随机抽样。

## 查询安全

所有分析接口只接受 Meta 逻辑实体名和逻辑字段名。服务端负责解析并校验物理标识符，
请求不接受 SQL、物理表名、物理列名、表达式、任意 URL、amis adaptor 或 JavaScript。

## 数据可视化智能体调用约束

数据可视化智能体不能直接从用户自然语言拼接本接口参数。普通工作流必须先调用实体 Meta，
再使用所选实体准确逻辑 `name` 查询字段 Meta，最后让用户确认锁定的工具和请求。

批准后平台按原请求调用对应 MCP 工具：

| REST | MCP |
| --- | --- |
| `/api/v1/entity/overview/query` | `entity_overview` |
| `/api/v1/entity/summary/query` | `entity_summary` |
| `/api/v1/entity/trend/query` | `entity_trend` |
| `/api/v1/entity/distribution/query` | `entity_distribution` |
| `/api/v1/entity/aggregate/query` | `entity_aggregate` |
| `/api/v1/entity/histogram/query` | `entity_histogram` |
| `/api/v1/entity/scatter/query` | `entity_scatter` |
| `/api/v1/entity/value-statistics/query` | `entity_value_statistics` |
| `/api/v1/entity/relations/query` | `entity_relations` |
| `/api/v1/entity/relation-timeline/query` | `entity_relation_timeline` |

图表库手动刷新也只允许使用受控白名单中的原 `query.tool/query.request`，不得使用记录中
提供的任意 URL。刷新失败保留原 `echartsOption` 快照。
