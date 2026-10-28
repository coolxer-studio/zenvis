# 用户事件数据推送服务

请基于已发布且只读的 `user_event.json` Meta 创建用户事件数据推送服务。

## 数据来源

- 数据源类型：demo_logs
- 输入格式：JSON 文本
- 连接与认证：无，使用定时生成的演示日志

## 解析与映射

- 将输入日志按 JSON 解析。
- 保留 event_type、tags。
- 自动补齐 event_id、user、procid、reliability、detail、server_time。
- 不写入 zenvis_id、zenvis_insert_time。

## 推送目标

- 目标实体：user_event / 用户事件数据
- ClickHouse 表：zenvis.msg_user_event
- 同时输出到 console 便于调试

必须读取并校验现有 Meta，不得创建或修改 Meta。
