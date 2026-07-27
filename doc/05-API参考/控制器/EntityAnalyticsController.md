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
      "result_count": 2
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

- 时间预设：`TODAY`、`YESTERDAY`、`LAST_7_DAYS`、`LAST_30_DAYS`、
  `THIS_MONTH`、`CUSTOM`。概览、汇总、分布和值统计额外支持 `ALL_TIME`。
- 时间粒度：`AUTO`、`HOUR`、`DAY`、`WEEK`、`MONTH`，最多 1000 个桶。
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

支持 `COUNT`、`DISTINCT_COUNT`、`SUM`、`AVG`、`MIN`、`MAX`。

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
