# AI Agents 架构文档

## 概述

ZenVis Backend 集成了多个AI Agent，提供智能数据分析、自然语言查询和可视化能力。基于 Spring AI 和 OpenAI 大语言模型，实现了强大的智能巡检和数据分析功能。

## 核心Agent架构

### 1. InspectionAgent（智能巡检Agent）

**位置**: `com.coolxer.service.dih.agent.InspectionAgent`

**核心功能**:
- 自然语言转SQL查询（NL2SQL）
- 数据查询和结果可视化
- 多轮对话记忆管理
- ECharts图表自动生成
- SQL安全校验和错误重试

**工作流程**:

```
用户自然语言查询
    ↓
Query Rewrite（问题重写）
    ↓
关键词提取和证据增强
    ↓
Schema召回（表和字段）
    ↓
SQL生成（基于历史SQL参考）
    ↓
SQL安全校验
    ↓
执行SQL（失败时自动重试修复）
    ↓
结果可视化（表格或图表）
    ↓
保存到对话记忆
```

**关键特性**:

1. **NL2SQL转换**
   - 支持多轮对话上下文理解
   - 历史SQL参考复用
   - 智能表和字段选择
   - SQL安全校验（防止注入攻击）

2. **对话记忆管理**
   - 基于MySQL的会话持久化
   - 支持多轮对话上下文
   - 自动保存SQL查询历史
   - 智能提取和复用历史SQL

3. **可视化能力**
   - 自动判断是否需要图表展示
   - 支持多种图表类型（柱状图、折线图、饼图等）
   - ECharts数据结构转换
   - 降级处理（图表失败时显示表格）

4. **错误处理**
   - SQL执行失败自动重试（最多2次）
   - 基于错误信息的SQL自动修复
   - 意图分类（闲聊、查询、意图不明确）
   - 友好的错误提示

**技术实现**:

```java
// 主要依赖组件
- RedisNl2sqlService: NL2SQL核心服务
- RedisSchemaService: 数据库Schema管理
- EChartsConverter: 图表数据转换
- LlmService: 大语言模型调用
- ChatMemory: 对话记忆管理
```

### 2. 向量化与工具能力

**核心功能**:
- 向量存储和相似性搜索
- 数据库Schema向量化
- 可扩展的工具机制

## NL2SQL 服务架构

### 核心服务组件

#### 1. RedisNl2sqlService
**位置**: `com.coolxer.service.dih.agent.nl2sql.service.RedisNl2sqlService`

**功能**:
- 自然语言问题重写
- 关键词提取和扩展
- 证据提取
- SQL生成
- SQL执行和结果处理

#### 2. RedisSchemaService
**位置**: `com.coolxer.service.dih.agent.agent.RedisSchemaService`

**功能**:
- 数据库Schema管理
- 表和字段文档生成
- 向量存储和检索
- Schema选择和过滤

#### 3. LlmService
**位置**: `com.coolxer.service.dih.agent.nl2sql.service.LlmService`

**功能**:
- 大语言模型调用封装
- Prompt模板管理
- 流式响应处理
- 模型切换支持

### 数据库连接器

#### ClickHouseAccessor
**位置**: `com.coolxer.service.dih.agent.nl2sql.connector.accessor.ClickHouseAccessor`

**功能**:
- ClickHouse数据库访问
- 元数据提取（表、字段、类型）
- SQL执行和结果集处理
- Markdown表格生成

**支持的数据类型**:
- 基础类型：String, Int, Float, DateTime等
- 复杂类型：Array, Tuple, Map等
- 特殊类型：Nullable, LowCardinality等

## 向量存储架构

### RedisVectorConfiguration
**位置**: `com.coolxer.service.dih.agent.rag.RedisVectorConfiguration`

**功能**:
- Redis向量存储配置
- 向量索引管理
- 相似性搜索接口

### RedisVectorManagementService
**位置**: `com.coolxer.service.dih.agent.RedisVectorManagementService`

**功能**:
- Schema向量化
- 向量检索
- 文档管理
- 索引维护

**向量类型**:
- `table`: 表结构向量
- `column`: 字段信息向量
- `schema`: 完整Schema向量

## Prompt模板系统

### PromptLoader
**位置**: `com.coolxer.service.dih.agent.prompt.PromptLoader`

**功能**:
- Prompt模板加载
- 多语言支持
- 动态参数替换

### PromptConstant
**位置**: `com.coolxer.service.dih.agent.prompt.PromptConstant`

**常量定义**:
- 系统Prompt模板
- SQL生成Prompt
- 图表选择Prompt
- 闲聊处理Prompt

## 数据转换和可视化

### EChartsConverter
**位置**: `com.coolxer.service.dih.agent.converter.EChartsConverter`

**功能**:
- SQL结果集转ECharts数据
- 图表类型智能选择
- 数据聚合和统计
- 坐标轴和系列配置

**支持的图表类型**:
- 柱状图（bar）
- 折线图（line）
- 饼图（pie）
- 散点图（scatter）
- 面积图（area）

### MdTableGenerator
**位置**: `com.coolxer.service.dih.agent.nl2sql.connector.MdTableGenerator`

**功能**:
- 结果集转Markdown表格
- 表格样式美化
- 数据格式化

## 安全和校验

### SqlSafeValidator
**位置**: `com.coolxer.service.dih.agent.nl2sql.util.SqlSafeValidator`

**安全规则**:
- SQL注入检测
- 危险操作拦截（DROP, DELETE等）
- 查询复杂度限制
- 结果集大小限制

### SqlValidationResult
**位置**: `com.coolxer.service.dih.agent.nl2sql.util.SqlValidationResult`

**校验结果**:
- 有效/无效状态
- 拒绝原因
- 安全建议

## 配置和初始化

### AIAgentConfiguration
**位置**: `com.coolxer.service.dih.agent.AIAgentConfiguration`

**配置项**:
- OpenAI API密钥与模型配置
- 模型选择
- 向量存储配置
- 记忆管理配置

### InspectionAgentMemoryConfig
**位置**: `com.coolxer.service.dih.agent.config.InspectionAgentMemoryConfig`

**记忆配置**:
- 会话超时时间
- 最大历史记录数
- 记忆存储策略

## API接口

### ChatController
**路径**: `/api/v1/dih/chat`

**接口**:
- `POST /chat` - 智能对话接口
- `POST /chat/stream` - 流式对话接口

**请求参数**:
```json
{
  "query": "查询最近7天的API调用次数",
  "chatId": "session-123",
  "model": "auto"
}
```

**响应格式**:
```json
{
  "content": "图表数据或文本响应",
  "type": "CHART|TEXT",
  "timestamp": 1234567890
}
```

### DihController
**路径**: `/api/v1/dih/nl2sql`

**接口**:
- `POST /query` - NL2SQL查询
- `POST /schema` - Schema管理
- `POST /vector` - 向量检索

## 使用示例

### 基础查询
```bash
curl -X POST http://localhost:11001/api/v1/dih/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "查询最近7天的API调用次数",
    "chatId": "session-001"
  }'
```

### 多轮对话
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

### 图表查询
```bash
curl -X POST http://localhost:11001/api/v1/dih/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "用折线图展示最近30天的API调用趋势",
    "chatId": "session-003"
  }'
```

## 性能优化

### 缓存策略
- Schema向量缓存
- SQL查询结果缓存
- 对话历史缓存

### 并发处理
- 异步SQL执行
- 流式响应处理
- 连接池管理

### 资源管理
- 向量索引优化
- 记忆清理策略
- 连接复用

## 扩展和定制

### 添加新的数据源
1. 实现`Accessor`接口
2. 配置数据源连接
3. 向量化Schema
4. 注册到NL2SQL服务

### 自定义图表类型
1. 扩展`EChartsConverter`
2. 实现新的转换逻辑
3. 配置图表选择规则

### 添加新的工具
1. 实现`ToolCallback`接口
2. 注册到ReAct Agent
3. 配置工具描述

## 监控和日志

### 关键指标
- NL2SQL转换成功率
- SQL执行时间
- 向量检索准确率
- 对话响应时间

### 日志级别
- INFO: 正常流程记录
- WARN: 重试和降级处理
- ERROR: 异常和错误情况

## 故障排查

### 常见问题

1. **SQL生成失败**
   - 检查Schema是否正确向量化
   - 验证Prompt模板配置
   - 查看LLM API调用状态

2. **图表显示异常**
   - 检查数据转换逻辑
   - 验证ECharts配置
   - 查看前端渲染日志

3. **对话记忆丢失**
   - 检查MySQL连接状态
   - 验证ChatMemory配置
   - 查看会话管理日志

## 未来规划

- [ ] 支持更多数据库类型
- [ ] 增强多模态能力
- [ ] 优化向量检索算法
- [ ] 添加更多图表类型
- [ ] 支持自定义Agent
- [ ] 增强安全校验机制
- [ ] 性能优化和缓存策略
- [ ] 多语言支持

## 相关文档

- [Spring AI文档](https://docs.spring.io/spring-ai/reference/)
- [OpenAI API文档](https://platform.openai.com/docs/)
- [Redis Vector Search](https://redis.io/docs/stack/search/)
- [ECharts文档](https://echarts.apache.org/)

## 贡献指南

欢迎提交Issue和Pull Request来改进AI Agent功能！

## 联系方式

如有问题或建议，请联系：
- 提交Issue
- 发送邮件：<coolxer@163.com>
