# RAG 与知识库运维

本文只说明 ZenVis 实际使用的公共 RAG 知识库、插件文档入库、Redis Vector Store 和运维边界。通用 Redis Search 语法请查阅 Redis 官方文档。

## 当前作用域

公共 RAG 只服务普通问答和深度思考：

```text
用户问题
  → embedding
  → Redis Vector Store 相似度召回
  → topK 6 文档
  → 作为不可执行参考资料加入系统 Prompt
  → 模型回答
```

- RAG 文档被明确标记为参考资料，不是系统指令。
- 普通问答不会因为文档内容获得 MCP 工具。
- 数据接入、数据可视化、报表和专项 Skill 使用显式工作规范与工具，不读取公共 RAG。
- embedding 未启用、Redis 故障或没有匹配文档时，普通问答继续以无 RAG 模式执行。

## 配置

### embedding

```properties
app.ai.embedding.enabled=false
spring.ai.openai.embedding.options.model=<EMBEDDING_MODEL>
```

embedding 默认按部署配置选择是否启用。启用前确认模型服务实际提供 embedding 接口。

### Redis Vector Store

```properties
spring.ai.vectorstore.redis.host=<REDIS_STACK_HOST>
spring.ai.vectorstore.redis.port=6379
spring.ai.vectorstore.redis.password=<REDIS_PASSWORD>
spring.ai.vectorstore.redis.initialize-schema=true
spring.ai.vectorstore.redis.index=index_dih_rag_vector
spring.ai.vectorstore.redis.prefix=dih_rag_vector
```

Redis Stack 与普通 Session Redis 不是同一职责。Compose 部署中应连接 `redis-stack-service` 对应实例。

### 管理接口

```properties
app.ai.vectorstore.management.enabled=true
```

代码默认在未配置时关闭管理接口；当前生产配置基线显式开启。面向非管理员用户时应结合登录、角色和网络边界限制访问。

## 文档来源与生命周期

插件安装后，`00_doc` 中平台支持的文档可以进入公共向量索引。每个向量文档保存来源信息，用于检索展示和插件资源清理。

运维原则：

- 文档正文以插件包中的原始文件为来源；
- 向量文档保存 `source`，不要混用不同插件或客户来源；
- 插件升级后应核对新增、更新和删除文档；
- 插件卸载只清理该插件来源的向量文档；
- 删除向量文档不会删除插件包中的原始文件；
- 重新安装或重新入库前确认是否会产生重复来源。

管理接口支持：

- 文档列表和分页；
- 按 ID 查看；
- 按来源或关键词筛选；
- 单个或批量删除；
- 相似度搜索验证。

完整路径和字段见[DIH 与 AI API](/08-API参考/RestfulAPI/DIH与AI.md#vectorstorequerycontroller)。

## 召回行为

`RagContextService` 当前固定请求 topK 6：

- 空问题不召回；
- embedding 关闭时记录 `embedding_disabled`；
- 没有结果时记录 `no_match`；
- embedding 或 Redis 异常时记录 `retrieval_error`；
- 只有非空文档才进入 Prompt；
- 每条资料携带 `source`，便于核对来源。

RAG 日志会记录是否请求、是否实际使用、文档数、耗时和降级原因。不要仅根据最终回答判断知识库是否生效。

## 索引与 embedding 一致性

向量索引与 embedding 模型必须保持：

- 向量维度一致；
- 数据类型和距离算法一致；
- 索引名、Key 前缀与当前部署配置一致；
- 新写入文档和查询向量使用同一模型版本。

修改 embedding 模型或维度后，旧向量不能直接与新向量混用。应建立受控重建流程：

1. 记录当前模型、维度、索引、前缀、文档数和来源分布。
2. 备份 Redis 数据和插件原始文档。
3. 使用独立索引或维护窗口重新生成全部向量。
4. 验证文档数、来源和代表性查询。
5. 切换配置并重启/刷新相关服务。
6. 观察召回错误和结果质量。
7. 确认回滚窗口后再清理旧索引。

不要在同一索引中逐步混入不同维度或模型的向量。

## 日常检查

### 服务

```bash
docker compose -f deploy/docker-compose.yml ps redis-stack-service
```

检查：

- Redis Stack 容器健康；
- 后端配置指向正确主机和端口；
- 密码一致；
- embedding 服务可用；
- 后端日志没有初始化或维度错误。

### 索引

使用 Redis CLI 时只执行必要的只读检查：

```text
FT._LIST
FT.INFO index_dih_rag_vector
FT.SEARCH index_dih_rag_vector "*" LIMIT 0 10
```

实际索引名以当前 `spring.ai.vectorstore.redis.index` 为准。生产环境的向量查询还需要与索引字段和维度匹配的二进制 query vector，不应从通用示例复制未知向量。

### 应用验证

1. 通过向量管理接口按 `source` 查找目标插件文档。
2. 对一个明确出现在文档中的问题执行相似度搜索。
3. 确认返回文档、来源和内容正确。
4. 再执行普通问答，检查 RAG 日志中的 `rag_used=true` 和文档数。
5. 禁用 embedding 或模拟 Redis 故障，确认普通问答 fail-open。

## 删除与重建安全

- 删除单个向量文档前记录文档 ID、来源和原始文件。
- 批量删除必须先按来源核对数量，防止跨插件清理。
- 删除索引属于高风险运维操作，应先备份并准备重建和回滚。
- 不要在不理解参数时使用会同时删除关联文档的索引删除选项。
- 不要把清空 Redis 数据目录当作正常索引迁移手段。
- Redis 备份和恢复需要与 MySQL 中的插件安装状态、插件包和 embedding 配置同时考虑。

通用命令语法、索引结构和向量查询规则以官方文档为准：

- [Redis Search 官方文档](https://redis.io/docs/latest/develop/ai/search-and-query/)
- [Redis 向量检索官方说明](https://redis.io/docs/latest/develop/ai/search-and-query/vectors/)
- [`FT.INFO` 官方命令参考](https://redis.io/docs/latest/commands/ft.info/)
- [`FT.SEARCH` 官方命令参考](https://redis.io/docs/latest/commands/ft.search/)

## 常见问题

### 普通问答没有使用 RAG

依次检查：

1. `app.ai.embedding.enabled`；
2. embedding 地址、Key、模型；
3. Redis Stack 连接；
4. 索引是否存在；
5. 文档是否按正确来源入库；
6. 日志原因是 `no_match` 还是 `retrieval_error`。

### 更换模型后出现维度错误

当前索引包含旧模型向量。恢复旧模型配置，或按受控流程重建新索引，不要继续混写。

### 插件文档已经更新，回答仍引用旧内容

检查插件升级是否重新处理文档、旧来源是否清理、向量文档正文和 metadata 是否更新，以及问答实际命中的 source。

### 删除文档后插件文件仍存在

这是预期行为。向量管理只处理 Redis 中的索引文档，不删除插件归档或展开目录。

### Redis 故障但问答仍返回内容

这是 fail-open 行为。回答此时没有使用公共 RAG，应根据日志、引用来源和文档数判断结果可信度。

### 相似度搜索结果与预期不符

核对 embedding 模型、维度、索引、文档切分、来源、查询语言和实际文档内容。不要仅通过调整 topK 掩盖错误索引或错误模型。

## 运维验收

- embedding 开关与模型服务能力一致；
- Redis Stack 和普通 Session Redis 的职责、地址没有混淆；
- 索引名、前缀、维度和 embedding 模型一致；
- 插件文档可按来源查询和清理；
- 代表性相似度搜索返回正确来源；
- 普通问答日志能区分 `ok`、`no_match`、`embedding_disabled` 和 `retrieval_error`；
- embedding 或 Redis 故障时普通问答可以 fail-open；
- 删除、重建和模型升级都有备份、回滚和验证步骤；
- 文档只维护 ZenVis 运维内容，通用 Redis 语法链接官方资料。

## 关联文档

- [AI 与数据智能](/04-AI与数据智能/README.md)
- [AI 问答与会话](/04-AI与数据智能/AI问答与会话.md)
- [插件开发与集成](/03-插件开发与集成/README.md)
- [DIH 与 AI API](/08-API参考/RestfulAPI/DIH与AI.md)
- [AI 与 MCP 架构](/06-架构设计/AI与MCP架构.md)
