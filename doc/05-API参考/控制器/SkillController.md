# SkillController AI Skill 管理接口文档

**基础信息**
- **模块名称**: AI Skill 管理
- **基础路径**: `/api/v1/dih/skills`
- **协议**: HTTP/HTTPS
- **数据格式**: JSON

---

## 数据模型定义

### SkillVo

```json
{
  "id": "data-visualization-agent",
  "name": "数据可视化智能体基础能力",
  "description": "为数据可视化智能体补充能力边界、回答风格和数据分析行为说明。",
  "version": "1.0.0",
  "author": "ZenVis",
  "agent_types": ["agent_data_visualization"],
  "tags": ["data-visualization", "retrieval"],
  "enabled": true,
  "entry": "SKILL.md",
  "path": "data-visualization-agent",
  "update_time": "2026-06-28T10:00:00.000+00:00"
}
```

### SkillDetailVo

```json
{
  "id": "data-visualization-agent",
  "name": "数据可视化智能体基础能力",
  "content": "# 数据可视化智能体基础能力\n..."
}
```

### SkillSearchDto

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | String | 否 | 匹配 ID、名称、描述和标签 |
| agentType | String | 否 | 智能体类型，如 `agent_data_visualization` |
| enabled | Boolean | 否 | 是否启用 |
| page | Integer | 否 | 页码，默认 1 |
| perPage | Integer | 否 | 每页数量，默认 10 |

---

## 接口总览

| HTTP方法 | 接口路径 | 功能描述 |
|----------|----------|----------|
| GET | `/api/v1/dih/skills/list` | 分页查询 Skill 列表 |
| GET | `/api/v1/dih/skills/agents` | 查询内置 Agent Skill 状态 |
| GET | `/api/v1/dih/skills/chat-entries?enabled=true` | 查询 DIH 输入区可用的 Skill 聊天入口 |
| GET | `/api/v1/dih/skills/options?enabled=true` | 查询 AI分析任务可选的全部启用 Skill |
| GET | `/api/v1/dih/skills/{id}/view` | 查询 Skill 详情和入口文件内容 |
| POST | `/api/v1/dih/skills/reload` | 重新扫描并加载 Skill 目录 |
| POST | `/api/v1/dih/skills/{id}/enable` | 启用 Skill |
| POST | `/api/v1/dih/skills/{id}/disable` | 停用 Skill |
| GET | `/api/v1/dih/skills/agent/{agentType}/prompt` | 查看指定 Agent 当前加载的 Skill 提示词片段 |

---

## 接口详情

### 查询 Skill 列表

**接口地址**: `GET /api/v1/dih/skills/list`

**请求示例**:

```bash
curl "http://localhost:11001/api/v1/dih/skills/list?page=1&perPage=10&agentType=agent_data_visualization"
```

**成功响应**:

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "rows": [
      {
        "id": "data-visualization-agent",
        "name": "数据可视化智能体基础能力",
        "enabled": true,
        "entry": "SKILL.md"
      }
    ],
    "total": 1
  }
}
```

### 查询 Skill 详情

**接口地址**: `GET /api/v1/dih/skills/{id}/view`

```bash
curl "http://localhost:11001/api/v1/dih/skills/data-visualization-agent/view"
```

### 查询 AI分析任务 Skill 选项

**接口地址**：`GET /api/v1/dih/skills/options?enabled=true`

该接口返回所有已扫描且启用的 Skill，不按 `agent_types` 过滤，供 AI分析任务创建和编辑表单使用。`enabled=false` 不受支持。

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": [
    {
      "label": "研判分析",
      "value": "analysis-agent",
      "description": "研判分析任务规则",
      "agent_types": ["agent_analysis"]
    }
  ]
}
```

AI分析任务保存 Skill ID，并在实际执行时读取最新内容。创建、编辑、重新入队和执行前都会重新校验 Skill；已停用或删除的 Skill 会使任务失败，不会被静默跳过。

### 查询内置 Agent Skill 状态

**接口地址**：`GET /api/v1/dih/skills/agents`

可选参数 `enabled=true/false`。该接口用于查看内置智能体入口对应的 Skill 状态，与 AI分析任务的全量启用选项接口用途不同。

### 查询 DIH Skill 聊天入口

**接口地址**：`GET /api/v1/dih/skills/chat-entries?enabled=true`

仅返回同时满足 `skill.enabled=true` 和 `skill.chat.enabled=true` 的入口。内置 Skill 保留原有
`agent_*` 会话类型，插件或自定义 Skill 使用 `skill:<skillId>`：

```json
[
  {
    "skill_id": "jmr-continuous-threat-analysis",
    "chat_type": "skill:jmr-continuous-threat-analysis",
    "agent_type": "agent_analysis",
    "label": "僵木蠕研判",
    "description": "对 JMR 日志执行证据化研判",
    "icon": "data-analysis",
    "order": 60
  }
]
```

### 重载 Skill

**接口地址**: `POST /api/v1/dih/skills/reload`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/skills/reload"
```

### 启用 Skill

**接口地址**: `POST /api/v1/dih/skills/{id}/enable`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/skills/data-visualization-agent/enable"
```

### 停用 Skill

**接口地址**: `POST /api/v1/dih/skills/{id}/disable`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/skills/data-visualization-agent/disable"
```

### 查看 Agent 加载片段

**接口地址**: `GET /api/v1/dih/skills/agent/{agentType}/prompt`

```bash
curl "http://localhost:11001/api/v1/dih/skills/agent/agent_data_visualization/prompt"
```

---

## Skill 文件结构

默认目录由 `app.paths.skills` 配置，开发环境为 `../deploy/open_config/skill_config`（以 `zenvis-backend` 为工作目录）。

```text
skill_config/
  data-visualization-agent/
    skill.json
    SKILL.md
```

`skill.json` 示例：

```json
{
  "id": "data-visualization-agent",
  "name": "数据可视化智能体基础能力",
  "description": "为数据可视化智能体补充能力边界、回答风格和数据分析行为说明。",
  "version": "1.0.0",
  "author": "ZenVis",
  "agent_types": ["agent_data_visualization"],
  "tags": ["data-visualization", "retrieval"],
  "chat": {
    "enabled": true,
    "label": "数据可视化",
    "icon": "monitor",
    "order": 20,
    "agentType": "agent_data_visualization",
    "prologue": "我是数据可视化助手。",
    "promptSuggestions": [
      {
        "label": "生成趋势图",
        "prompt": "请生成最近 24 小时的数据趋势图。"
      }
    ]
  },
  "enabled": true,
  "entry": "SKILL.md"
}
```

`chat` 为可选配置；安装 Skill 本身不会自动创建聊天入口。字段规则：

- `chat.enabled=true` 才会展示；Skill 停用后入口同步消失。
- `label` 默认使用 Skill 名称，`icon` 默认 `magic-stick`，`order` 默认 `1000`。
- `agentType` 未填写时，若 `agentTypes` 只有一个受支持业务 Agent，则自动继承；否则使用无 RAG、无 MCP 的 `agent_skill`。
- 动态会话只加载当前选中的 Skill。关联业务 Agent 时，仅继承该 Agent 已授权的 Prompt、右侧面板和 MCP scope，不会扩大工具权限。
- 显式选择的 Skill 提示词上限由 `app.ai.skill.max-selected-prompt-chars` 控制，默认 `32000` 字符；超限会明确失败，不做静默截断。

## AI分析任务加载规则

AI分析任务运行时会同时加载：

1. 全局启用且适用于 `agent_analysis` 的 Skill。
2. 当前任务明确选择且仍处于启用状态的 Skill。

任务显式选择不受 `agent_types` 限制，但 `enabled=true` 是选择和执行的硬性条件。详细流程见 [MCP 审批与 AI分析任务快速上手](../../07-AI与数据智能/MCP审批与AI分析任务快速上手.md)。
