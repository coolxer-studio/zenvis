# MCP、RAG 与 Skill

## MCP

`07_mcp/config.json` 注册代码为 `lab` 的 SSE 服务：

```text
base_url:     http://127.0.0.1:${server.port}
sse_endpoint: /api/v1/plugin/com.coolxer.plugin.lab/mcp/sse
Authorization: Bearer ${app.security.api.bearer-token}
```

服务由动态 JAR 中的 `LabMcpController` 提供，暴露两个只读工具：

| 工具 | 参数 | 返回 |
| --- | --- | --- |
| `lab_capability_catalog` | 无 | 能力 ID、名称、分类、说明、演示入口、只读标记 |
| `lab_note_search` | `keyword`、`status`、`limit` | `PageRows<LabNote>`；关键字最多 100 字符，状态限三种，`limit` 为 1–50 |

两个工具都带只读注解，不提供创建、修改、删除、SQL 或处置能力。MCP 的最终可见性还受平台全局 MCP 开关、Agent Scope、连接状态和工具审批策略约束。

## RAG

`08_rag/config.json` 声明：

- 数据集 `lab-docs`，路径 `08_rag/datasets/lab-docs`；
- 知识库 `lab-kb`，引用 `lab-docs`；
- `source` 均为 `com.coolxer.plugin.lab`。

数据集递归导入 Markdown 文件。安装或升级会同步创建/更新数据集、知识库和关联关系；内容变化时重建索引。Embedding 或 Redis 不可用不会回滚插件安装，但知识库可能停留在 `UNBUILD` 或 `BUILD_FAILED`。

## Skill

Skill ID：`lab-capability-guide`

`09_skill/lab-capability-guide/skill.json` 关键约束：

- `knowledgeBaseCodes: ["lab-kb"]`；
- `chat.enabled: true`，提供中文开场白和三条建议问题；
- 本地工具仅 `retrieval_search`；
- MCP 工具仅 `lab` 服务的 `lab_capability_catalog`、`lab_note_search`；
- 单轮最多 6 次工具调用，同类失败 1 次后停止；
- 单次和累计结果字符数分别限制为 16000 与 32000。

`SKILL.md` 明确：

- 只读边界，不写笔记、不执行 SQL、不触发处置；
- 知识库说明、工具实时结果和未验证部署事实必须分开表述；
- `lab-kb` 不可用、MCP 未连接、认证失败、空结果和结果截断时的降级方式；
- IP 关联仅为合成数据查询示例，不能作为真实归因依据。

## 三层边界

| 层 | 负责 | 不负责 |
| --- | --- | --- |
| RAG | 提供设计、字段、接入、页面和排障语义 | 实时笔记、实时事件或工具调用 |
| MCP | 返回本次能力目录与笔记检索实时结果 | 写入笔记、SQL、告警或处置 |
| Skill | 组合提示、知识库和只读工具白名单 | 绕过平台权限或扩大工具范围 |

