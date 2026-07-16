# AggregateController API 接口文档

## 基础信息

- 基础路径：`/api/v1/retrieval/aggregate`
- 说明：基于通用实体元数据提供标签和趋势聚合。

## 接口

### 实体聚合标签

- `GET /msg/tag`
- 查询参数中通过 `active` 指定实体，并传入所需时间和实体筛选条件。

### 实体数据趋势

- `GET /msg/trend`
- 查询参数中通过 `active` 指定实体，并传入 `start_time`、`end_time` 和实体筛选条件。

以上两个路径暂时保留原有 `msg` 命名以兼容前端和MCP调用，底层实现不依赖固定 `msg` 表。

原 `POST /detail/{id}` 数据链详情接口直接依赖固定 `msg` 表，现已下线；实体明细统一使用 `/api/v1/entity/{entity_name}/list`。
