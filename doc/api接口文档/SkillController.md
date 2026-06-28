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
  "id": "inspection-agent",
  "name": "巡检智能体基础能力",
  "description": "为巡检智能体补充能力边界、回答风格和数据分析行为说明。",
  "version": "1.0.0",
  "author": "ZenVis",
  "agentTypes": ["agent_inspect"],
  "tags": ["inspection", "nl2sql", "echarts"],
  "enabled": true,
  "entry": "SKILL.md",
  "path": "inspection-agent",
  "updateTime": "2026-06-28T10:00:00.000+00:00"
}
```

### SkillDetailVo

```json
{
  "id": "inspection-agent",
  "name": "巡检智能体基础能力",
  "content": "# 巡检智能体基础能力\n..."
}
```

### SkillSearchDto

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | String | 否 | 匹配 ID、名称、描述和标签 |
| agentType | String | 否 | 智能体类型，如 `agent_inspect` |
| enabled | Boolean | 否 | 是否启用 |
| page | Integer | 否 | 页码，默认 1 |
| perPage | Integer | 否 | 每页数量，默认 10 |

---

## 接口总览

| HTTP方法 | 接口路径 | 功能描述 |
|----------|----------|----------|
| GET | `/api/v1/dih/skills/list` | 分页查询 Skill 列表 |
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
curl "http://localhost:11001/api/v1/dih/skills/list?page=1&perPage=10&agentType=agent_inspect"
```

**成功响应**:

```json
{
  "status": 0,
  "msg": "请求成功",
  "data": {
    "rows": [
      {
        "id": "inspection-agent",
        "name": "巡检智能体基础能力",
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
curl "http://localhost:11001/api/v1/dih/skills/inspection-agent/view"
```

### 重载 Skill

**接口地址**: `POST /api/v1/dih/skills/reload`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/skills/reload"
```

### 启用 Skill

**接口地址**: `POST /api/v1/dih/skills/{id}/enable`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/skills/inspection-agent/enable"
```

### 停用 Skill

**接口地址**: `POST /api/v1/dih/skills/{id}/disable`

```bash
curl -X POST "http://localhost:11001/api/v1/dih/skills/inspection-agent/disable"
```

### 查看 Agent 加载片段

**接口地址**: `GET /api/v1/dih/skills/agent/{agentType}/prompt`

```bash
curl "http://localhost:11001/api/v1/dih/skills/agent/agent_inspect/prompt"
```

---

## Skill 文件结构

默认目录由 `app.paths.skills` 配置，开发环境为 `deploy/open_config/skill_config`。

```text
skill_config/
  inspection-agent/
    skill.json
    SKILL.md
```

`skill.json` 示例：

```json
{
  "id": "inspection-agent",
  "name": "巡检智能体基础能力",
  "description": "为巡检智能体补充能力边界、回答风格和数据分析行为说明。",
  "version": "1.0.0",
  "author": "ZenVis",
  "agentTypes": ["agent_inspect"],
  "tags": ["inspection", "nl2sql", "echarts"],
  "enabled": true,
  "entry": "SKILL.md"
}
```
