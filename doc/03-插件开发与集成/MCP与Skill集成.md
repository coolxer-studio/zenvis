# MCP 与 Skill 集成

插件通过 `06_mcp` 声明外部 MCP Server，通过 `07_skill` 安装 DIH 运行时 Skill。两者可以组合，但职责和权限边界不同。

## 能力区别

| 能力 | `06_mcp` | `07_skill` |
| --- | --- | --- |
| 描述对象 | 外部 MCP Server 连接 | 提示词、业务知识和运行时工具范围 |
| 主要文件 | `config.json` | `skill.json`、`SKILL.md` |
| 执行代码 | 由外部 MCP Server 提供 | 通常不直接执行代码 |
| 工具发现 | 连接后动态发现 | 只引用允许的工具名 |
| 权限 | Agent Scope、审批策略、服务状态 | Skill Allowlist 与运行预算 |

`agent-skills/create-zenvis-plugin` 是研发工作流，不会被 `07_skill` 安装；插件内 Skill 才会进入平台运行时注册表。

## 06_mcp/config.json

```json
[
  {
    "code": "example-analysis",
    "name": "示例分析 MCP",
    "description": "提供示例业务的外部分析工具",
    "base_url": "${EXAMPLE_MCP_BASE_URL}",
    "sse_endpoint": "/sse",
    "headers": "{\"Authorization\":\"Bearer ${EXAMPLE_MCP_TOKEN}\"}",
    "enabled": false,
    "request_timeout_seconds": 30,
    "connect_timeout_seconds": 10
  }
]
```

| 字段 | 规则 |
| --- | --- |
| `code` | 全局稳定标识，安装器会规范为字母、数字、点、下划线或连字符，最长 64 个字符 |
| `name` | 面向管理员的名称 |
| `description` | 说明服务用途、数据范围和风险 |
| `base_url` | MCP Server 基础地址，可由运行环境占位符解析 |
| `sse_endpoint` | SSE 端点，通常为 `/sse` |
| `headers` | 固定 HTTP Header 的 JSON 字符串 |
| `enabled` | 安装后的默认启用状态 |
| `request_timeout_seconds` | 单次请求超时 |
| `connect_timeout_seconds` | 连接超时 |

没有外部 MCP 时使用合法空数组：

```json
[]
```

不要把真实 Token 写进插件。使用环境变量或安装后由管理员配置，README 只说明变量名和获取方式。

## MCP 身份与升级

MCP `code` 是资源身份：

- 插件内不能重复；
- 不能占用其他来源的 Code；
- 升级时保持稳定；
- 删除 Code 会删除对应插件来源的连接；
- 新增 Code 会创建新连接。

升级时，平台会比较旧包默认值、管理员当前值和新包默认值。管理员已经修改过的启用状态、地址、Endpoint、Header 和超时优先保留；仍等于旧默认值的字段才采用新默认值。

外部工具在连接后动态发现，文档不应复制一份容易失效的完整 Tool Schema。工具名称以目标 MCP Server 当前返回为准。

## MCP 审批和 Agent Scope

插件 MCP 配置只注册连接，不绕过平台权限。工具是否可见和能否调用还取决于：

```text
全局 MCP 开关
  ∩ Agent 可访问的 MCP Server Scope
  ∩ Skill 声明的 Server / Tool Allowlist
  ∩ 工具审批策略 ALLOW / ASK / DENY
```

- `DENY`：工具不暴露给 Agent。
- `ASK`：调用前必须由用户审批。
- `ALLOW`：在其他边界允许时可以直接调用。

数据可视化 Agent 只允许受控的只读检索工具，不追加任意外部 MCP。详细策略见 [MCP Tool 权限审批与运行约束](/08-API参考/MCPtool/权限审批与运行约束.md)。

## 07_skill 目录

一个插件可以包含一个或多个 Skill：

```text
07_skill/
└── example-investigation/
    ├── skill.json
    ├── SKILL.md
    └── agents/
        └── openai.yaml
```

`07_skill` 本身也可以直接是一套 Skill，但多 Skill 插件推荐每个一级子目录一套。目录需要有 `SKILL.md` 或 manifest 指定的入口文件。

安装后，平台把 Skill 复制到该插件包名对应的隔离目录并重新加载注册表。卸载插件会移除这部分运行时 Skill。

## skill.json

```json
{
  "id": "example-investigation",
  "name": "示例事件研判",
  "description": "使用受控检索工具分析示例事件",
  "version": "1.0.0",
  "author": "Example Team",
  "agentTypes": ["agent_skill"],
  "tags": ["example", "investigation"],
  "chat": {
    "enabled": true,
    "label": "示例研判",
    "icon": "data-analysis",
    "order": 100,
    "agentType": "agent_skill",
    "prologue": "我可以在受控工具范围内分析示例事件。",
    "promptSuggestions": [
      {
        "label": "分析事件",
        "prompt": "请分析事件 ID："
      }
    ]
  },
  "runtime": {
    "promptMode": "skill_only",
    "tools": {
      "local": {
        "retrieval": ["retrieval_search"]
      },
      "mcp": {
        "example-analysis": ["lookup_indicator"]
      }
    },
    "limits": {
      "maxToolCalls": 12,
      "maxRepeatedFailures": 2,
      "maxToolResultChars": 12000,
      "maxAccumulatedToolResultChars": 48000
    }
  },
  "enabled": true,
  "entry": "SKILL.md"
}
```

## Manifest 字段

| 字段 | 说明 |
| --- | --- |
| `id` | 全局 Skill ID；字母或数字开头，可包含点、下划线、连字符，最长 128 个字符 |
| `name`、`description` | 用户可理解的名称和边界 |
| `version`、`author`、`tags` | 版本和检索信息 |
| `agentTypes` | 适用 Agent 类型 |
| `enabled` | 是否进入启用 Skill 范围 |
| `entry` | 提示词入口，默认 `SKILL.md` |
| `chat` | DIH 聊天入口 |
| `runtime` | 提示词模式、工具白名单和调用预算 |

插件 Skill 没有声明 `agentTypes` 时，当前平台会兼容为 `ask`；新建需要工具的 Skill 应显式声明支持的 Agent 类型，避免继承不明确行为。

## DIH 聊天入口

只有 `chat.enabled=true` 的 Skill 才出现在 DIH 输入区。常用字段：

- `label`：入口名称；
- `icon`：图标标识；
- `order`：排序；
- `agentType`：`agent_skill` 或受支持的内置业务 Agent；
- `prologue`：新会话开场说明；
- `promptSuggestions`：预置问题。

仅设置 `enabled=true` 不会自动创建聊天入口。没有指定 `chat.agentType` 时，平台会从 Skill 的 Agent 类型推断；不能唯一推断时使用通用 `agent_skill`。

## 工具白名单

`runtime.tools.local` 使用固定内置服务 code 到本服务工具原名列表的映射，例如
`retrieval → ["retrieval_search"]`。列表可使用唯一值 `"*"` 选择该服务全部工具。
旧数组格式、未知服务、未知工具、错组工具以及 `"*"` 与具体名称混用都会使 Skill
加载失败。`runtime.tools.mcp` 的格式保持不变，使用：

```text
MCP Server code → 该 Server 返回的原始 Tool name 列表
```

选择了 Skill 但没有声明 `runtime.tools` 时，平台按 Fail-Closed 处理，不向模型暴露工具。声明了工具也不代表一定可用，还要经过：

- 全局 MCP 开关；
- Agent Scope；
- Server 是否启用且连接正常；
- 当前发现的 Tool 名称；
- `ALLOW / ASK / DENY` 审批策略。

多个 Skill 同时选择时，工具白名单取并集，正数资源上限取其中最严格的值。

## 运行预算

当前支持：

| 字段 | 约束 |
| --- | --- |
| `maxToolCalls` | 单轮最大工具调用数 |
| `maxRepeatedFailures` | 同类重复失败上限 |
| `maxToolResultChars` | 单次工具结果字符上限 |
| `maxAccumulatedToolResultChars` | 单轮累计工具结果字符上限 |
| `maxAccumulatedToolResultTokens` | 单轮累计工具结果 Token 上限 |

所有值使用正整数。预算是资源边界，不替代业务校验、审批和审计。

## SKILL.md

入口文档应包含：

- Skill 的目标和触发场景；
- 输入格式、必要上下文和拒绝条件；
- 固定步骤与可选分支；
- 每个工具的使用条件；
- 证据、输出格式和完成标准；
- 风险动作的审批要求；
- 无数据、工具失败和预算耗尽时的处理。

不要在提示词中声称拥有 Allowlist 之外的工具，也不要指导模型绕过审批。

## 检查清单

- MCP Code 和 Skill ID 全局稳定、无重复。
- `headers` 不包含真实 Token。
- 外部 MCP 地址可由目标部署环境解析。
- Skill 入口文件存在且大小在平台限制内。
- 需要聊天入口时已设置 `chat.enabled=true`。
- 工具名来自当前本地 Tool 或目标 MCP Server。
- Skill 工具范围不超过 Agent Scope，风险工具保留审批。
- 运行预算是正数且与工作流复杂度匹配。
- 禁用或断开 MCP 时，Skill 有清晰的降级说明。

## 关联文档

- [AI 与 MCP 架构](/06-架构设计/AI与MCP架构.md)
- [MCP Tool 总览与接入](/08-API参考/MCPtool/总览与接入.md)
- [`agent-skills` 开发对接指南](/07-开发指南/agent-skills-开发对接指南.md)
