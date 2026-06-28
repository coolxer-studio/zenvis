# AI 智能分析

ZenVis 集成了基于大语言模型的智能分析能力，支持自然语言查询、数据可视化和智能巡检。

## 核心能力

| 能力 | 说明 |
| :--- | :--- |
| NL2SQL | 自然语言转 SQL 查询 |
| 多轮对话 | 支持上下文理解的智能问答 |
| 图表生成 | 自动识别数据并生成可视化图表 |
| 智能巡检 | 基于 ReAct 模式的智能 Agent |

## 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                      AI 分析架构                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    │
│  │   User      │───▶│  Inspection  │───▶│   LLM       │    │
│  │  (Natural   │    │   Agent      │    │  (OpenAI)   │    │
│  │   Language) │    │              │    │             │    │
│  └─────────────┘    └──────────────┘    └─────────────┘    │
│                            │                     ▲          │
│                            ▼                     │          │
│                     ┌─────────────┐               │          │
│                     │  Redis      │               │          │
│                     │  Vector     │───────────────┘          │
│                     │  Store      │                          │
│                     └─────────────┘                          │
│                            │                                  │
│                            ▼                                  │
│                     ┌─────────────┐                          │
│                     │  ClickHouse │                          │
│                     │  (SQL Exec) │                          │
│                     └─────────────┘                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## InspectionAgent

InspectionAgent 是核心的智能巡检 Agent，基于 Spring AI OpenAI 集成实现。

### 位置

`com.coolxer.service.dih.agent.InspectionAgent`

### 核心功能

- **NL2SQL 转换**：自然语言问题转换为 SQL 查询
- **多轮对话记忆**：基于 MySQL 的会话持久化
- **图表自动生成**：根据数据特征自动选择图表类型
- **SQL 安全校验**：防止 SQL 注入攻击

### 工作流程

```
用户自然语言查询
      │
      ▼
Query Rewrite（问题重写）
      │
      ▼
关键词提取和证据增强
      │
      ▼
Schema召回（表和字段）
      │
      ▼
SQL生成（基于历史SQL参考）
      │
      ▼
SQL安全校验
      │
      ▼
执行SQL（失败时自动重试修复）
      │
      ▼
结果可视化（表格或图表）
      │
      ▼
保存到对话记忆
```

### 核心组件

| 组件 | 类 | 说明 |
| :--- | :--- | :--- |
| NL2SQL服务 | `RedisNl2sqlService` | 自然语言转SQL核心 |
| Schema管理 | `RedisSchemaService` | 数据库Schema管理 |
| 图表转换 | `EChartsConverter` | 数据转ECharts格式 |
| 对话记忆 | `ChatMemory` | 会话持久化 |

## NL2SQL 服务

### RedisNl2sqlService

位置：`com.coolxer.service.dih.agent.nl2sql.service.RedisNl2sqlService`

功能：

- 问题重写和关键词提取
- Schema 召回和 SQL 生成
- SQL 执行和结果处理

### Schema 向量化

数据库表结构信息存储在 Redis Vector 中，支持相似性搜索：

```java
// Schema 向量类型
-table:表结构向量
        -column:字段信息向量
        -schema:完整Schema向量
```

## ReAct Agent

AIAgentService 基于 ReAct（Reasoning + Action）模式实现：

```
Thought → Action → Observation → Thought → ...
```

### 工具调用

Agent 可以调用多种工具：

| 工具 | 功能 |
| :--- | :--- |
| 数据库查询 | 执行 SQL 查询 |
| 向量检索 | 相似性搜索 |
| Schema查询 | 获取表结构信息 |

## 对话 API

### 智能对话

```bash
POST /api/v1/dih/chat
Content-Type: application/json

{
  "query": "查询最近7天的API调用次数",
  "chatId": "session-001"
}
```

响应：

```json
{
  "content": "图表数据或文本响应",
  "type": "CHART|TEXT",
  "timestamp": 1234567890
}
```

### 流式对话

```bash
POST /api/v1/dih/chat/stream
Content-Type: application/json

{
  "query": "查询昨天的API调用次数",
  "chatId": "session-001"
}
```

### 多轮对话示例

```bash
# 第一轮
curl -X POST http://localhost:11001/api/v1/dih/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "查询昨天的API调用次数",
    "chatId": "session-002"
  }'

# 第二轮（基于上下文）
curl -X POST http://localhost:11001/api/v1/dih/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "那前天呢？",
    "chatId": "session-002"
  }'
```

## 可视化能力

### 自动图表生成

Agent 自动判断数据特征，选择合适的图表：

| 图表类型 | 适用场景 |
| :--- | :--- |
| 柱状图 | 分类对比 |
| 折线图 | 时间趋势 |
| 饼图 | 占比分布 |
| 散点图 | 关联分析 |

### 图表查询示例

```bash
curl -X POST http://localhost:11001/api/v1/dih/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "用折线图展示最近30天的API调用趋势",
    "chatId": "session-003"
  }'
```

## 配置说明

### API Key 配置

在 `application.properties` 中配置 OpenAI API Key 和模型：

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=${OPENAI_CHAT_MODEL}
spring.ai.openai.embedding.options.model=${OPENAI_EMBEDDING_MODEL}
```

### 模型配置

支持多种模型：

```properties
# 模型由部署环境配置
# OPENAI_CHAT_MODEL
# OPENAI_EMBEDDING_MODEL
```

## 错误处理

| 错误类型 | 处理方式 |
| :--- | :--- |
| SQL执行失败 | 自动重试（最多2次） |
| LLM调用失败 | 降级返回错误信息 |
| 图表生成失败 | 降级显示表格 |

## 安全机制

### SQL 安全校验

- 注入攻击检测
- 危险操作拦截（DROP/DELETE/TRUNCATE）
- 查询复杂度限制
- 结果集大小限制

## NL2SQL 原理

详见 [DIH/InspectionAgent-Analysis-and-Roadmap.md](../DIH/InspectionAgent-Analysis-and-Roadmap.md)

## Redis 向量命令

详见 [DIH/redis-search-commands-guide.md](../DIH/redis-search-commands-guide.md)

## 下一步

- [插件开发指南](plugin-development.md)
- [API参考](api-reference.md)
