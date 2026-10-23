# RAG 与知识库运维

本文只说明 ZenVis 实际使用的公共 RAG 知识库、插件文档入库、Redis Vector Store 和运维边界。通用 Redis Search 语法请查阅 Redis 官方文档。

## 当前作用域

公共 RAG 服务普通问答、深度思考和业务 Agent / Skill：

```text
用户问题
  → embedding
  → 按知识库范围过滤后的 Redis Vector Store 相似度召回
  → topK 6 文档
  → 作为不可执行参考资料加入系统 Prompt
  → 模型回答
```

- 普通问答和深度思考检索全部未删除知识库。
- 业务 Agent / Skill 只检索当前所选启用 Skill 绑定知识库的并集；未绑定知识库时不执行向量检索，也不回退到全库。
- RAG 参考资料每轮自动注入，不作为 MCP 工具暴露，不占用工具调用预算或审批流程。
- 每次同步检索同时生成一条会话内“知识库检索”记录，包含查询摘要、范围、状态、原因、耗时和命中来源；该记录独立于 MCP 工具活动，以 `searchId` 幂等更新。
- RAG 文档被明确标记为参考资料，不是系统指令。
- 普通问答不会因为文档内容获得 MCP 工具。
- 业务会话不会因为 RAG 绑定获得 MCP 工具之外的权限；实时事实仍必须来自受控 MCP 工具。
- embedding 未启用、Redis 故障、知识库未构建或没有匹配文档时，当前轮继续以无 RAG 模式执行并记录原因。

全库检索先通过 `RagKnowledgeBaseRepository.findActiveIds()` 取得全部未删除知识库 ID，再把 ID 转为 `kb_<knowledge_base_id>` 并作为 Redis `source` 标签过滤条件。业务会话直接使用所选 Skill 绑定知识库的并集，不再查询全库 ID。范围过滤复用现有 `source` 标签，不新增向量字段或数据库迁移。

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

## 插件 RAG 来源与生命周期

插件 RAG 完全由 `08_rag/config.json` 接管。安装器不再把 `00_doc` 自动加载到公共向量索引；没有 `08_rag` 声明的插件可以正常安装，但不会创建插件托管的数据集或知识库。数据集路径可以指向 `00_doc` 下的目录，也可以指向 `08_rag/datasets/<code>/`。

RAG 原始文档与插件数据集统一存储在 `zenvis-rag` Bucket，使用固定对象键：

- 数据集：`rag/datasets/{datasetId}/...`；
- 插件数据集：`rag/plugins/{packageName}/{datasetCode}/{operationId}/...`。

数据库中的 `directory` 仍保存对象键前缀，Bucket 由 RAG 业务域固定选择。旧数据若只存在于 `zenvis-attachments`，不会在运行时回退读取；需要重新创建或导入数据集。MinIO 对象缺失时上传、下载、删除和知识库构建直接失败，不使用本地文件或 MySQL 正文降级。

托管资源使用以下身份：

| 资源 | 托管身份 | 说明 |
| --- | --- | --- |
| 数据集 | `plugin_package + plugin_code` | 名称、来源、标签、描述、目录和文件随插件声明更新 |
| 知识库 | `plugin_package + plugin_code` | 名称、来源、标签、描述和数据集关联随插件声明更新 |
| 知识库构建 | `build_fingerprint` | 记录最近一次成功构建的输入摘要 |

生命周期行为：

- 安装和升级在操作完成前同步应用数据集、知识库和关联关系，并同步构建需要更新的知识库。
- 知识库为 `BUILT` 且指纹一致时跳过重建。指纹包含知识库名称、标签、数据集身份与名称、相对文件路径与内容摘要、Embedding 模型和 Redis 索引标识。
- 升级执行完全声明式对账：新增或更新声明资源，删除不再声明的托管资源；管理员对托管名称、标签、描述、关联关系和文件的修改会被覆盖。
- 管理员通过现有 RAG 接口修改托管数据集或知识库、上传/删除文件或手工触发构建时，关联构建指纹会失效；需要重建的托管知识库会回到 `UNBUILD`。
- 托管数据集仍被非托管知识库引用时，升级会被拒绝，避免破坏外部关联。
- 升级和卸载会尽力清理旧版 `00_doc` 插件来源向量；安装不再执行 `00_doc` 向量加载。
- 卸载只删除该 `plugin_package` 托管的知识库、关系、数据集、MinIO 文件和对应向量，不影响其他 RAG 资源。
- 插件导出从数据库和 MinIO 重建 `08_rag/config.json` 与 `08_rag/datasets/<code>/`，不会把 `00_doc` 复制为知识库源。

配置、路径、文件、数据库或名称冲突错误会中止插件操作并回滚。Embedding/Redis 不可用或向量构建失败只产生告警，数据集和知识库仍会保留：服务不可用时知识库为 `UNBUILD`，构建执行失败时为 `BUILD_FAILED`。修复后可在管理页面手工重建。

运维原则：

- 文档正文以插件包中的原始 Markdown 为来源；
- 向量文档保存 `source` 与知识库、数据集 metadata，不要混用不同插件或客户来源；
- 插件升级后应核对知识库状态、构建指纹、文档数量和来源；
- 管理端对托管资源的临时调整可用于排障，但下一次插件升级会覆盖；
- 删除向量文档不会删除插件包中的原始文件；
- 重新安装或重新入库前确认是否会产生重复来源。

现有管理接口保持可编辑，支持：

- 数据集管理：创建/更新/删除数据集，上传单文件或目录（保留 MinIO 层级），查看文件列表与内容；
- 知识库管理：创建/更新/删除知识库，关联数据集，异步构建并流式查看日志；
- 文档列表和分页，按 ID 查看，按来源、关键词或 `knowledge_base_id` 筛选；
- 单个或批量删除向量文档；
- 相似度搜索验证；
- 检索审计日志查询（`GET /retrieval-logs/list`）。

知识库构建时会在文档 metadata 中写入 `knowledge_base_id`，因此运维上可以通过 `/documents/list?knowledge_base_id={id}` 精确查看某个知识库已入库的文档，而不需要混用全部向量文档。删除知识库会同步清理 Redis 中的向量数据，但不会删除数据集在 MinIO 中的原始文件。插件卸载会先删除本插件托管知识库，再清理没有外部引用的托管数据集。

数据集和知识库的名称唯一性在应用层校验（仅统计 `is_delete=0` 的记录），未加数据库唯一索引：两张表均为逻辑删除，数据库级唯一约束会导致删除后无法重建同名记录。若需排查历史重名数据，可直接查询 `t_rag_dataset` / `t_rag_knowledge_base` 按名称分组统计。

## 检索审计

每次检索（管理端相似度搜索 `MANUAL`、问答或业务 Skill 触发的自动 RAG 检索 `CHAT`）都会写入 `t_rag_retrieval_log` 表（`ddl-auto=update` 自动建表），字段包括请求类型、检索文本、TopK、来源筛选、`knowledge_base_id`、命中数、状态、耗时、命中结果摘要 JSON 和错误信息。

自动检索还会通过 NDJSON `rag_search` 事件和最终消息的 `rag-search` 部件展示同一份内存快照。事件与审计记录复用同一查询、命中摘要和状态，但展示内容中查询与单条命中摘要最多 500 字。历史会话没有关联快照时不回填该记录。

运维要点：

- 日志保存为 best-effort：写库失败仅打 `保存RAG检索日志失败` 警告，不影响检索主流程；若审计无数据，先检查该表是否建表成功。
- 请求未指定知识库时，`knowledge_base_id` 取命中文档 metadata 中出现次数最多的知识库 ID；无命中或旧文档缺少元数据时归属为空。
- `result_json` 中每条命中的正文截断 500 字符，防止大字段膨胀。
- 审计 `status` 只包含 `ok`、`no_match`、`empty_query`、`embedding_disabled`、`retrieval_error`。`no_knowledge_base`、`no_bound_knowledge_base`、`knowledge_base_lookup_error` 等细分原因会归并为 `retrieval_error`，需要结合会话 `rag_search.reason` 或后端 RAG 日志判断具体原因。

完整路径和字段见[DIH 与 AI API](/08-API参考/RestfulAPI/DIH与AI.md#vectorstorequerycontroller)。

## 召回行为

`RagContextService` 当前固定请求 topK 6，并对 `source` 执行 `in` 过滤：

- 空问题不召回；
- embedding 关闭时记录 `embedding_disabled`；
- 普通问答没有未删除知识库时记录 `no_knowledge_base`，不查询向量库；
- 业务会话没有绑定知识库时记录 `no_bound_knowledge_base`，不查询向量库，也不回退全库；
- 知识库 ID 查询失败时审计状态为 `retrieval_error`、细分原因为 `knowledge_base_lookup_error`，会话检索卡显示为跳过并附错误信息；
- 没有结果时记录 `no_match`；
- embedding 或 Redis 异常时记录 `retrieval_error`；
- 只有非空文档才进入 Prompt；
- 每条资料携带 `source`，便于核对来源。

RAG 日志和 LLM 请求日志会记录是否请求、是否实际使用、文档数、耗时和降级原因。原生流式模型路径与 Spring AI 路径都会写入 `rag_requested`、`rag_used` 和 `rag_document_count`。不要仅根据最终回答判断知识库是否生效。

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
5. 选择一个绑定该知识库的 Skill 会话，确认只召回绑定范围并检查 `rag_used=true`；再选择一个未绑定知识库的 Skill，确认日志为 `no_bound_knowledge_base` 且没有向量查询。
6. 禁用 embedding 或模拟 Redis 故障，确认问答和业务会话 fail-open。

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

### 普通问答或业务会话没有使用 RAG

依次检查：

1. `app.ai.embedding.enabled`；
2. embedding 地址、Key、模型；
3. Redis Stack 连接；
4. 索引是否存在；
5. 文档是否按正确来源入库；
6. 普通问答是否至少有一个未删除知识库；
7. 业务会话所选 Skill 是否绑定知识库，且绑定知识库已构建；
8. 日志原因是 `no_knowledge_base`、`no_bound_knowledge_base`、`no_match` 还是 `retrieval_error`。

### 更换模型后出现维度错误

当前索引包含旧模型向量。恢复旧模型配置，或按受控流程重建新索引，不要继续混写。

### 插件文档已经更新，回答仍引用旧内容

检查插件升级日志中的 RAG 同步和构建结果、知识库 `build_fingerprint` 是否更新、数据集 MinIO 目录是否切换到新操作目录，以及向量文档正文和 metadata 是否更新。若知识库状态为 `UNBUILD` 或 `BUILD_FAILED`，修复服务后手工重建。

### 插件没有任何知识库

确认 `08_rag/config.json` 存在且声明非空，数据集路径位于 `00_doc` 或 `08_rag`，目录中至少有一个 `.md` 文件，知识库 `dataset_codes` 引用了本次声明的数据集。没有 `08_rag` 配置是合法的无插件 RAG 状态。

### 删除文档后插件文件仍存在

这是预期行为。向量管理只处理 Redis 中的索引文档，不删除插件归档或展开目录。

### Redis 故障但问答仍返回内容

这是 fail-open 行为。回答此时没有获得 RAG 参考资料，应根据日志中的检索原因、引用来源和文档数判断结果可信度。

### 相似度搜索结果与预期不符

核对 embedding 模型、维度、索引、文档切分、来源、查询语言和实际文档内容。不要仅通过调整 topK 掩盖错误索引或错误模型。

## 运维验收

- embedding 开关与模型服务能力一致；
- Redis Stack 和普通 Session Redis 的职责、地址没有混淆；
- 索引名、前缀、维度和 embedding 模型一致；
- 插件数据集和知识库可按 `plugin_package` 与 `plugin_code` 查询和清理；
- 未变化知识库可通过构建指纹跳过重建，定义或文件变化后可重新构建；
- Embedding/Redis 故障不会回滚插件操作，但会保留明确的 `UNBUILD` 或 `BUILD_FAILED` 状态；
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
