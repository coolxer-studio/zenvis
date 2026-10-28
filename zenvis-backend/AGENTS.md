# AI Agents 架构文档

## 概述

ZenVis Backend 的 AI Agent 基于 Spring AI、MCP 工具调用和 Skill 配置构建。当前数据可视化智能体不生成 SQL、不执行数据库查询；分析过程只通过只读 retrieval MCP 工具获取数据并输出文本或 Markdown 分析。

## DIH 执行边界

- 普通问答 `ask` 不加载 Skill，不解析或调用业务 MCP/本地工具，并在所有未删除知识库中自动执行 RAG。
- `ask` 仅在本轮存在附件时加载 `attachment_*` 只读附件工具；工具访问范围严格限制为当前用户、当前会话和本轮明确引用的附件，不改变 `ask` 不加载业务 MCP 的边界。
- `ask + deep_think=true` 与普通问答使用同一全库 RAG 范围，并保留原生模型 reasoning 流。
- 业务 Agent / Skill 只加载显式绑定且已启用的 Skill，并在 Skill 绑定知识库的并集内自动执行 RAG；未绑定知识库时不检索，也不回退全库。
- RAG 参考资料由平台在每轮请求自动注入，不占用 MCP 工具名额、工具调用预算或审批流程。
- Agent 请求携带 `deep_think=true` 时按 `false` 执行。

## 核心 Agent

### DataVisualizationAgent（数据可视化 Agent）

**位置**: `com.coolxer.service.dih.agent.DataVisualizationAgent`

**核心能力**:
- 使用 `PromptDrivenAgentRuntime` 驱动对话
- 只调用只读 retrieval MCP 工具
- 输出数据概览、统计说明、可视化建议和后续建议
- 复用统一聊天流式响应与会话保存逻辑

**工具边界**:
- 允许读取 retrieval 查询、列表、统计、趋势、详情类工具
- 禁止规则写入、实体写入、菜单、看板、配置、任务等有副作用工具
- 不追加外部 MCP tools
- 尊重 `app.ai.mcp.enabled=false` 的全局禁用

### 其他业务 Agent

- 数据接入和报表 Agent 通过各自类型接入 `PromptDrivenAgentRuntime`
- 动态 `agent_skill` 会话仅在 Skill manifest 显式声明 `runtime.tools` 时获得对应工具白名单
- MCP 工具由 `AgentMcpToolService` 按 agent 类型解析
- Skill 由 `SkillService` 加载后拼入 system prompt

## 通用 AI 服务

### AgentLlmService

**位置**: `com.coolxer.service.dih.AgentLlmService`

**功能**:
- 封装 Spring AI `ChatClient`
- 支持按线程设置模型
- 支持注入 MCP tool callback 和工具 system prompt
- 供 AI分析任务等非会话流程复用

## RAG 与向量能力

RAG 相关能力位于 `com.coolxer.service.dih.rag`，服务于普通问答、深度问答和业务 Agent / Skill。插件通过 `08_rag/config.json` 声明托管数据集和知识库；安装、升级、卸载和导出由 `PluginRagService` 处理，`00_doc` 不再自动加载到 RAG。可通过 `VectorStoreQueryController` 管理这些插件资源。普通问答检索全部未删除知识库，业务会话只检索所选 Skill 绑定的知识库。

## 开发注意事项

- 数据可视化 Agent 新能力应优先通过 retrieval MCP 工具扩展，而不是在 Agent 内直接访问数据库。
- 不要恢复 SQL 生成、SQL 执行或 SQL 校验链路。
- 新增工具时需要在 `AgentMcpToolService` 中明确 agent 可见范围。
- 会产生副作用的工具必须避免暴露给 `agent_data_visualization`。
