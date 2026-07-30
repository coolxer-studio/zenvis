# API 文档入口

完整 REST API 文档已融合到根文档中心：

- [API 参考总览](README.md)
- [DIH Chat、共享工作流与 MCP 审批](控制器/ChatController.md)
- [实体统计分析接口](控制器/EntityAnalyticsController.md)
- [全局检索 Controller](控制器/RetrievalController.md)
- [Retrieval 产品与技术说明](../03-架构设计/retrieval-module.md)
- [后端模块 README](../../zenvis-backend/README.md)

前端对接 Retrieval 前请先阅读[全局检索前端开发指南](../04-开发指南/全局检索模块开发指南.md)。
DIH 普通数据接入和数据可视化卡片调用 `DihService.workflowAction`，图表刷新调用
`EntityAnalyticsApi.query`，渲染统一使用 `SafeEcharts`。接口 JSON 统一使用 `snake_case`；
具体 TypeScript 契约以 `src/types/type-dih.ts`、`src/service/api/api-dih.ts` 和
`src/service/api/api-entity-analytics.ts` 为准。
