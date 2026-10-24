# Nginx日志监控

> 集中收集 Nginx 访问日志和错误日志，提供统一检索、详情查看和 IP 命中调查。

## 插件概览

| 项目 | 内容 |
| --- | --- |
| 插件名称 | Nginx日志监控 |
| 包名 | `com.coolxer.plugin.nginx` |
| 当前版本 | 1.0.0 |
| 插件形态 | 日志接入与分析型插件（Syslog → Kafka → ClickHouse） |
| 数据实体 | 2 个：访问日志、错误日志 |
| 主要入口 | 左侧菜单“Nginx日志监控” |
| 安装包 | [com-coolxer-plugin-nginx-1.0.0.tar.gz](https://gitee.com/coolxer-studio/zenvis/releases/download/zenvis-plugin-1.0.0/com-coolxer-plugin-nginx-1.0.0.tar.gz) |

## 用途与价值

Nginx 日志通常留在各台服务器的本地文件里，排查问题时需要逐台登录、逐个文件 grep，既耗时又难以做跨主机统计。本插件把访问日志和错误日志统一采集到平台：

- 访问日志结构化后可按 URI、状态码、耗时、上游地址等字段筛选。
- 错误日志按级别、进程和连接 ID 检索，快速定位异常。
- 两类日志中的客户端 IP、服务端 IP 都可以直接进入跨实体 IP 命中调查。

**适用对象**

- 运维人员：排查 5xx、上游超时、连接异常和错误峰值。
- 安全运营人员：按 IP 检索访问行为，结合其他实体做关联调查。
- 应用研发人员：分析接口耗时、状态码分布和访问来源。

## 核心能力

- 通过 UDP `514` 接收 Nginx Syslog，并按 `nginx_access` / `nginx_error` 标签分流。
- 访问日志使用 JSON 格式解析，错误日志使用正则解析。
- 两类日志分别写入 ClickHouse，解析失败进入独立死信主题。
- 监控概览展示访问与错误日志总量、今日新增、近 7 天趋势、HTTP 状态码分布和错误级别分布。
- 访问日志与错误日志列表支持排序、筛选、详情跳转和 IP 命中调查。
- 访问日志记录请求行、方法、URI、状态码、响应大小、来源页、User-Agent、请求耗时和上游信息。
- 错误日志记录客户端与服务端地址、错误消息、级别、时间、PID、线程 ID 和连接 ID。

## 数据与页面

**数据实体**

| 实体 | 说明 | 存储 |
| --- | --- | --- |
| `nginx_access_log` | Nginx 访问日志 | `zenvis.nginx_access_log` |
| `nginx_error_log` | Nginx 错误日志 | `zenvis.nginx_error_log` |

**菜单与页面**

| 入口 | 类型 | 说明 |
| --- | --- | --- |
| Nginx日志监控 | 低代码应用 | 监控概览、访问日志、错误日志、IP 命中调查和两类详情页 |

本插件不注册独立 HTML 看板，监控概览由低代码应用首页提供。

## 典型使用流程

1. 在生产 Nginx 上配置 JSON 格式的访问日志和默认格式的错误日志。
2. 配置 Syslog 转发，把两类日志发送到 ZenVis 的 `zenvis-service-vectum:514/UDP`。
3. 确认 Nginx 所在网络可以访问采集端 UDP 端口。
4. 在[插件管理](插件管理.md)中安装插件。
5. 在[数据推送服务](../服务管理/数据推送服务.md)中启用“Nginx日志Syslog采集”和“Nginx日志解析入库”任务。
6. 从菜单进入“Nginx日志监控”，检查概览、列表和详情是否出现数据。

## 注意事项

- 采集端按 Syslog 标签路由：`nginx_access` 进入访问日志队列，`nginx_error` 进入错误日志队列，其他标签只输出到控制台。
- 访问日志必须按插件文档配置 JSON 格式，否则解析可能失败。
- 两类日志解析失败的消息分别进入 `nginx-access-dead-letter` 和 `nginx-error-dead-letter`，不会写入 ClickHouse。
- 插件只负责接收和解析，不修改 Nginx 配置，也不负责打通网络。
- 菜单安装后不会自动授权，需要在角色权限中分配。

安装、升级和卸载操作见[插件管理](插件管理.md)。
