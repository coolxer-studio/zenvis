# InspectionAgent 深度分析与迭代规划

## Context

对 `InspectionAgent` 及其 NL2SQL 管道进行全面代码审计，识别设计缺陷、架构瓶颈、缺失功能，并提出可落地的迭代方向。

---

## 一、架构现状总结

当前 Agent 是一个 **Pipeline 式 NL2SQL 系统**，而非真正的 ReAct Agent：

```
用户问题 → Query Rewrite → Evidence/Keyword 提取 → Schema RAG → Fine Select → SQL 生成 → SQL 执行(含重试) → 图表意图识别 → 结果渲染
```

核心文件：
- `InspectionAgent.java` — 主编排入口（275行）
- `BaseNl2SqlService.java` — NL2SQL 基础流程（396行）
- `RedisNl2sqlService.java` — Redis 向量存储实现（67行）
- `LlmService.java` — LLM 调用封装，ThreadLocal 模型切换（134行）
- `BaseSchemaService.java` — Schema RAG 检索（285行）
- `EChartsConverter.java` — 图表数据转换（269行）
- `AIAgentService.java` — 原始 ReAct 实验死代码已移除

---

## 二、设计优化点（按优先级排列）

### P0 — 架构缺陷

#### 1. AIAgentService 与 InspectionAgent 双轨并行，职责混乱
- 原 `AIAgentService.java` 使用已废弃的 ReAct 实验实现，但**工具全部被注释掉**，已移除
- `InspectionAgent.java` 实际承载了所有业务逻辑，但它不是 Agent，只是一个 Pipeline
- **问题**：历史双轨实现导致开发者困惑，后续如需 ReAct 能力应基于 Spring AI 工具调用重新设计

#### 2. 无对话记忆（Conversational Memory）
- `InspectionAgent.chat()` 是完全无状态的，每次调用独立
- `init-rewrite.txt` 设计了多轮对话指代消解（"这个"、"那个"），但**没有传入历史对话**
- **问题**：多轮对话能力完全缺失，用户的追问无法被正确理解

#### 3. LlmService 的 ThreadLocal 模型切换存在线程安全隐患
```java
private static final ThreadLocal<String> CURRENT_MODEL = new ThreadLocal<>();
```
- Spring Boot 默认使用线程池处理请求，ThreadLocal 在异步/响应式场景下会泄漏
- `finally` 中的 `clearModel()` 只在 `InspectionAgent.chat()` 中有保护，其他调用点没有
- **问题**：在高并发或 WebFlux 场景下会出现模型串用

### P1 — 功能缺陷

#### 4. Controller 层信息丢失严重
```java
// AgentController.java:30
public String chatAgent(@RequestParam("query") String query) {
    ChatResponse chatResponse = inspectionAgent.chat(query);
    resp = chatResponse.getContent(); // ← 丢失了 type 信息（TEXT/CHART）
}
```
- `ChatResponse.type`（CHART/TEXT）被完全丢弃，前端无法区分是文本还是图表
- 异常时返回 `null`，前端无法优雅处理
- 不支持 `model` 参数透传
- 不支持 SSE 流式响应

#### 5. SQL 安全防护不充分
- 仅检查 SQL 是否以 `SELECT`/`WITH` 开头（正则匹配）
- **未防护**：子查询注入（`SELECT ... FROM (DELETE FROM ...)`）、ClickHouse 特有的危险操作（`DROP`、`TRUNCATE`、`ALTER`、`KILL`）
- ClickHouse 支持 `SELECT ... INTO OUTFILE` 可写入文件，也未被防护

#### 6. ECharts 图表类型有限且智能化不足
- 仅支持 bar/line/pie 三种类型
- 缺少：scatter（散点图）、radar（雷达图）、heatmap（热力图）、treemap、funnel（漏斗图）
- 图表类型完全依赖 LLM 一次判断，无数据特征感知（如：时间序列自动识别为折线图）
- 不支持多图表组合、仪表盘

#### 7. Planner 模板已就绪但未实现
- `planner.txt` 已定义完整的多步骤执行计划模板（SQL_EXECUTE_NODE → PYTHON_GENERATE_NODE → REPORT_GENERATOR_NODE）
- `Constant.java` 已定义所有节点常量（PLANNER_NODE, PYTHON_GENERATE_NODE 等）
- **但没有任何执行引擎**来解析和运行这个计划

### P2 — 可优化项

#### 8. LLM 调用无缓存和降级
- 相同问题每次都重新调用 LLM，无缓存层
- LLM 调用无超时控制，无降级策略
- 多次串行调用（rewrite → keywords → select → sql → chart），延迟叠加

#### 9. 向量检索质量可控性差
- Schema 检索使用固定的 `topK`，无动态调整
- 无检索结果质量评估（如相似度阈值过滤）
- evidence（业务知识）检索与 schema 检索相互独立，缺乏联合优化
- `mixRagForAgent()` 和 `getTableDocumentsForAgent()` 实际上没有使用 `agentId` 参数

#### 10. 日志过度
- 每一步都有 `logger.info/debug` 详细输出，包含完整的 Prompt 和 Schema 信息
- 在生产环境中会产生大量日志，影响性能和存储
- 缺少结构化日志（如请求 ID 关联）

#### 11. Gson 和 Jackson 混用
- `AIAgentConfiguration` 配置了 `Gson` Bean
- `RedisNl2sqlService` 使用 `ObjectMapper`（Jackson）
- `JacksonUtil.toJson()` 用于序列化 ECharts 数据
- 应统一序列化框架

---

## 三、更好的实现方案

### 方案 A：增强现有 Pipeline（推荐短期方案）

在现有 Pipeline 架构基础上进行增量优化：

1. **引入对话记忆**
   - 复用项目已有的 `MempChatMemoryRepository`（MySQL 存储）
   - 在 `InspectionAgent.chat()` 中注入历史对话
   - 让 `init-rewrite.txt` 的指代消解能力真正生效

2. **修复 Controller 层**
   - `chatAgent` 返回完整的 `ChatResponse` 对象（包含 type）
   - 增加异常处理的统一返回格式
   - 支持 `model` 参数透传

3. **增强 SQL 安全**
   - 增加黑名单关键词检查（`DROP`, `TRUNCATE`, `ALTER`, `KILL`, `INTO OUTFILE`）
   - 增加子查询深度限制
   - 增加结果集大小限制（防止内存溢出）

4. **引入 LLM 调用缓存**
   - 对高频相同问题增加 Redis 缓存层
   - 设置合理的 TTL

### 方案 B：演进为真正的 ReAct Agent（推荐中期方案）

基于 Spring AI 工具调用能力，将 Pipeline 步骤改造为可调用的 Tool：

1. **定义 Tool 集合**
   ```
   @Tool: schemaSearch    — 向量检索相关 Schema
   @Tool: sqlGenerator    — 基于 Schema 和问题生成 SQL
   @Tool: sqlExecutor     — 执行 SQL 并返回结果
   @Tool: chartRenderer   — 将结果转换为图表数据
   @Tool: dataAnalyzer    — 多步骤数据分析（Python）
   ```

2. **Agent 自主决策**
   - Agent 根据用户问题自主决定调用哪些 Tool
   - 复杂分析场景（如趋势分析）可多次调用 sqlExecutor
   - 简单查询场景只调用 sqlGenerator + sqlExecutor

3. **实现 Planner 执行引擎**
   - 解析 `planner.txt` 生成的 JSON 计划
   - 按步骤执行 SQL_EXECUTE_NODE / PYTHON_GENERATE_NODE / REPORT_GENERATOR_NODE
   - 支持步骤间的数据传递

### 方案 C：多 Agent 协作（推荐长期方案）

根据 `planner.txt` 的设计思路，实现多 Agent 协作架构：

1. **Planner Agent** — 接收用户问题，生成执行计划
2. **SQL Agent** — 负责所有 SQL 相关操作（生成、执行、修复）
3. **Analysis Agent** — 负责 Python 代码生成和数据分析
4. **Report Agent** — 负责整合分析结果，生成报告
5. **Orchestrator** — 协调各 Agent 的执行顺序和数据传递

---

## 四、未实现的必要功能点

### 必须实现（阻塞正常使用）

| 功能 | 说明 | 当前状态 |
|------|------|---------|
| **对话记忆** | 多轮对话、指代消解 | 完全缺失 |
| **Controller 完整返回** | 返回 type（CHART/TEXT）、异常信息 | type 被丢弃 |
| **SQL 结果集限制** | 防止大量数据导致 OOM | 完全缺失 |
| **LLM 调用超时** | 防止长时间无响应 | 完全缺失 |

### 应当实现（影响用户体验）

| 功能 | 说明 | 当前状态 |
|------|------|---------|
| **流式响应** | SSE 返回部分结果，提升交互体验 | LlmService 支持 Flux，但 Agent 层未使用 |
| **Planner 执行引擎** | 多步骤分析计划 | 模板已定义，引擎未实现 |
| **Python 代码执行** | 复杂数据分析 | Prompt 已定义，执行器未实现 |
| **报告生成** | 自动化分析报告 | Prompt 已定义，流程未接入 |
| **图表类型扩展** | scatter/radar/heatmap/funnel/treemap | 仅 bar/line/pie |
| **数据缓存** | 相同问题不重复调用 LLM | 完全缺失 |

### 值得实现（提升竞争力）

| 功能 | 说明 | 当前状态 |
|------|------|---------|
| **意图路由** | 区分简单查询 vs 复杂分析，走不同 Pipeline | 所有问题走同一 Pipeline |
| **SQL 解释** | 向用户解释生成的 SQL 含义 | 完全缺失 |
| **数据洞察** | 自动发现数据异常和趋势 | 完全缺失 |
| **权限控制** | 不同用户可查询不同表 | 完全缺失 |
| **审计日志** | 记录所有查询和结果 | 完全缺失 |
| **向量检索质量评估** | 评估召回的 Schema 是否足够 | 有 `isRecallInfoSatisfyRequirement` 方法但未被调用 |
| **多数据源支持** | 同时查询 ClickHouse 和 MySQL | 架构预留（DbConfig）但未实现 |

---

## 五、关键文件清单

| 文件 | 需要修改的原因 |
|------|--------------|
| `InspectionAgent.java` | 核心编排，需加入对话记忆、流式、安全增强 |
| `AgentController.java` | 修复返回结构、支持 SSE、支持 model 参数 |
| `LlmService.java` | 替换 ThreadLocal，增加超时和缓存 |
| `BaseNl2SqlService.java` | 重构多步骤调用链，支持并行化 |
| `EChartsConverter.java` | 扩展图表类型，增加数据感知 |
| `AIAgentService.java` | 已移除死代码，后续按需重建工具调用式 Agent |
| `AIAgentConfiguration.java` | 统一配置管理，清理硬编码 |
| `RedisSchemaService.java` | 实现 agentId 隔离逻辑 |

---

## 六、验证策略

1. **对话记忆**：连续发送多轮相关问题（"查所有主机" → "其中高危的有多少"），验证指代消解
2. **Controller 修复**：发送图表类问题，验证返回的 type=CHART 且 content 包含合法 JSON
3. **SQL 安全**：尝试注入 `DROP TABLE`、`SELECT ... INTO OUTFILE`，验证被拦截
4. **超时控制**：模拟 LLM 响应超时，验证优雅降级
5. **流式响应**：使用 curl 或前端 SSE 验证逐步返回
6. **Planner**：发送复杂分析问题，验证多步骤计划被正确执行
