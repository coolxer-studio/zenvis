# MCP、RAG、Skill 与降级

## MCP

服务 code 为 `lab`，SSE 地址为 `/api/v1/plugin/com.coolxer.plugin.lab/mcp/sse`，固定 Bearer 请求头来自平台登录 Token。它只暴露两个只读工具：

- `lab_capability_catalog`：无参数，返回能力矩阵；
- `lab_note_search`：按 `keyword`、`status`、`limit` 检索已保存笔记。

`status` 只接受 `draft`、`published`、`archived`，`limit` 为 1–50。工具不提供写入、SQL、告警或处置能力。

## RAG 与 Skill

数据集 `lab-docs` 由 `08_rag/datasets/lab-docs` 提供，知识库 `lab-kb` 引用该数据集。Skill ID 为 `lab-capability-guide`，通过 `knowledgeBaseCodes` 绑定 `lab-kb`，本地工具只允许 `retrieval_search`，MCP 工具只允许上述两个名称。

Skill 单轮最多 6 次工具调用，同类失败 1 次后停止，单次工具结果 16000 字符、累计 32000 字符。它只做导览和只读查询，不写笔记、不执行 SQL、不触发处置。

## 降级

- 知识库为空或未构建：说明没有可用 `lab-kb` 资料，按 Skill 内置规则回答，不声称引用知识库；
- MCP 未连接：说明能力目录与笔记检索不可用，检查 MCP 开关、URL、Bearer 请求头和网络；
- 认证失败：报告 401/403，不由模型猜测或输出 Token；
- 笔记结果为空：说明当前条件没有匹配记录，不推断数据库故障；
- 工具失败或结果截断：保留已确认信息，列出未完成项，不编造数量；
- 无真实 Vector、MySQL、ClickHouse、MCP 或 RAG 环境：只能确认静态配置和单元测试，不得声称完成线上验收。

