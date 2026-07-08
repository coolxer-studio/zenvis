# ChatController AI聊天接口文档

**基础信息**
- **模块名称**: AI对答聊天服务
- **基础路径**: `/api/v1/dih`
- **作者**: coolxer
- **协议**: HTTP/HTTPS
- **数据格式**: JSON (SSE流式响应)

---

## 📋 数据模型定义

### 1. ChatDto (聊天请求传输对象)

```json
{
  "message": "你好",          // String - 用户消息内容
  "model": "auto",            // String - AI模型名称（可选，不传或auto时使用配置默认模型）
  "chatId": "chat-001",       // String - 会话ID
  "type": "ask",              // String - 聊天类型（ask/agent_data_visualization等）
  "deepThink": false,         // Boolean - 是否深度思考模式
  "onlineSearch": false       // Boolean - 是否在线搜索
}
```

### 2. Message (消息对象)

```json
{
  "sender": "user",           // String - 发送者（user/ai）
  "content": "消息内容",       // String - 消息内容
  "type": "TEXT",             // String - 消息类型
  "time": "2024-01-01 12:00:00" // String - 发送时间
}
```

### 3. ChatResponse (聊天响应对象)

```json
{
  "content": "响应内容",       // String - AI响应内容
  "type": "TEXT"              // MessageType - 消息类型
}
```

---

## 📊 接口总览

| 序号 | HTTP方法 | 接口路径 | 接口名称 | 功能描述 |
|:---:|:-------:|---------|---------|---------|
| 1 | POST | `/api/v1/dih/chat` | AI聊天 | 与AI进行流式聊天对话 |

---

## 🔌 接口详情

### 1️⃣ AI聊天

**接口地址**: `POST /api/v1/dih/chat`

**功能描述**: 与AI进行流式聊天对话，支持多种聊天类型和模型选择

**请求参数**:
- Content-Type: `application/json`
- Body: ChatDto

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/v1/dih/chat \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "message": "请分析当前系统的风险态势",
    "model": "auto",
    "chatId": "chat-001",
    "type": "ask",
    "deepThink": false
  }'
```

**成功响应**:
```bash
data: 分析结果内容...
data: 继续输出...
```

**失败响应**:
```bash
data: "Input model not support."
```

**权限受限响应**:
```bash
data: "对不起，当前智能体没有开通权限，请联系管理员！"
```

---

## 📊 响应码汇总

| 响应码 | 说明 | 触发场景 |
|--------|------|---------|
| SSE流 | 请求成功 | 返回流式响应数据 |
| Input model not support | 模型不支持 | 指定的模型不在支持列表中 |

---

## 🔐 注意事项

1. **认证授权**: 需要登录认证
2. **流式响应**: 使用SSE(Server-Sent Events)协议返回流式响应
3. **会话管理**: chatId用于维护会话上下文，首次传入时会自动创建会话
4. **模型支持**: 支持的模型通过 OpenAI 兼容 `/v1/models` 接口动态获取；不传或传 `auto` 时由后端按请求场景自动选择模型
5. **智能体权限**: 各 agent 类型需在 Skill 管理中启用后可用
6. **深度思考**: 设置deepThink为true可启用深度思考模式
