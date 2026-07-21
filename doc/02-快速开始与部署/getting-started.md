# 快速入门

本指南将帮助你在 5 分钟内快速部署并运行 ZenVis 服务。

## 前置要求

| 要求 | 版本 |
| :--- | :--- |
| Docker | 20.10+ |
| Docker Compose | 2.0+ |
| 系统内存 | 4GB+ |

## 快速启动（推荐）

### 1. 进入项目

```bash
cd zenvis/zenvis-backend
```

### 2. 一键启动

```bash
cd deploy
docker compose up -d
```

### 3. 验证服务

等待 1-2 分钟服务启动后，访问以下地址：

| 服务 | 地址 | 说明 |
| :--- | :--- | :--- |
| Web前端 | http://localhost:11000 | 用户界面 |
| API服务 | http://localhost:11001 | 后端接口 |
| Swagger文档 | http://localhost:11001/swagger-ui/index.html | API在线文档 |

## 初始化账号

初始化账号和密码由当前部署配置决定。首次登录后应立即修改初始化密码；生产文档和脚本中不要固定真实凭据。

## 快速体验 AI 智能分析

服务启动后，可通过 API 接口体验自然语言查询：

```bash
curl -X POST http://localhost:11001/api/v1/dih/chat \
  -H "Authorization: Bearer <API_BEARER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "chat_id": "session-001",
    "model": "auto",
    "type": "ask",
    "message": "查询最近7天的API调用次数",
    "response_format": "events"
  }'
```

DIH Chat 调用需审批的 MCP 工具时，会在当前流式消息中返回审批卡片事件。耗时分析可创建后台 AI分析任务，关闭页面后仍继续执行。

## 第三方系统快速对接

第三方系统调用普通 REST API 推荐使用 Bearer Token，而不是模拟验证码登录。部署时配置：

```properties
API_BEARER_TOKEN=your_api_token
API_BEARER_USER=admin@admin.com
```

调用示例：

```bash
curl -H "Authorization: Bearer <API_BEARER_TOKEN>" \
  "http://localhost:11001/api/v1/system/user/list?page=1&per_page=10"
```

更多接口地图、数据检索示例和排障说明见 [第三方 REST API 对接指南](../05-API参考/third-party-api-integration.md)。

## 目录结构

```
zenvis-backend/
├── deploy/              # 部署配置
│   ├── docker-compose.yml
│   ├── config/          # 服务配置
│   └── data/           # 数据持久化目录
├── doc/                 # 仅保留模块 README 与 banner；整体文档位于 ../doc
├── src/                 # 源代码
└── pom.xml
```

## 下一步

- [详细安装部署](installation.md)
- [系统架构设计](../03-架构设计/architecture.md)
- [API 参考](../05-API参考/api-reference.md)
- [第三方 REST API 对接指南](../05-API参考/third-party-api-integration.md)
- [MCP 审批与 AI分析任务快速上手](../07-AI与数据智能/MCP审批与AI分析任务快速上手.md)
- [MCP Client 与业务 Agent 设计](../07-AI与数据智能/MCP-Client-Agent-Design.md)
