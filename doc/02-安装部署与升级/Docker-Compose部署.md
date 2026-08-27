# Docker Compose 部署

Compose 编排包含 8 个服务：ZenVis 前后端、Vectum、MySQL、ClickHouse、Redis、Redis Stack 和单节点 Kafka。该模式将数据保存在 `deploy/data/`，适合单机部署和测试环境。

运行环境支持 Docker Compose v2（推荐）或 legacy `docker-compose` 1.29.2；不支持更低版本的 `docker-compose` v1。

## 初始化

在仓库根目录执行：

```bash
./zenvisctl compose init
```

工具执行以下安全初始化：

- 根据主机生成 `ARCH=amd64` 或 `ARCH=arm64`；
- 为数据库、Redis、API、MCP、Vectum 和内置账号生成独立随机凭据；
- 将凭据写入 Git 忽略的 `deploy/.env` 并设置权限 `600`；
- 对已有 `deploy/.env` 保留原值，并自动补齐缺失的监听地址和主机端口；
- 如发现历史数据而 `.env` 丢失，则终止初始化，不猜测旧密码。

需要定制时，在首次启动前编辑 `deploy/.env`。不要把生成文件复制回 `.env.example`。

## 检查与启动

```bash
./zenvisctl compose doctor
./zenvisctl compose up
```

`doctor` 会检查 Docker 服务、Compose 解析、必填环境变量和 CPU 架构。`up` 在后台启动服务并等待全部服务健康，默认最长等待 900 秒；可用 `ZENVIS_STARTUP_TIMEOUT` 调整。

## 日常命令

```bash
./zenvisctl compose status
./zenvisctl compose logs
./zenvisctl compose logs zenvis-backend
./zenvisctl compose down
```

`logs` 持续跟踪日志，可按 `Ctrl+C` 退出，不会停止服务。`down` 只删除容器和网络，不删除绑定挂载的数据；不要手工删除 `deploy/data/`。

## 服务与端口

| 服务 | 容器内地址 | 默认主机监听 |
| --- | --- | --- |
| Web | `zenvis-frontend:11000` | `0.0.0.0:11000` |
| Backend | `zenvis-backend:11001` | `127.0.0.1:11001` |
| Vectum | `vectum-service:11002` | `127.0.0.1:11002` |
| MySQL | `mysql-service:3306` | `127.0.0.1:3306` |
| ClickHouse HTTP/TCP | `clickhouse-service:8123/9000` | `127.0.0.1:8123/9000` |
| Redis | `redis-service:6379` | `127.0.0.1:6379` |
| Redis Stack | `redis-stack-service:6379` | `127.0.0.1:16379` |
| Kafka | `kafka-service:9092` | `127.0.0.1:9094` |

对外发布时仅暴露 Web，使用反向代理终止 TLS。若必须从其他主机访问 API，先设置防火墙和身份认证，再修改 `ZENVIS_API_BIND_ADDRESS`。

## 反向代理

Compose 前端 Nginx 已将 `/zenvis/` 转发到后端，并支持 SPA 路由回退。外层代理至少应：

- 将 HTTPS 请求转发到 `127.0.0.1:11000`；
- 传递 `Host`、`X-Real-IP`、`X-Forwarded-For` 和 `X-Forwarded-Proto`；
- 根据上传需求配置请求体上限和超时；
- 只允许受信网络访问管理入口。

## 停机与卸载

临时停机使用 `./zenvisctl compose down`。永久卸载前先执行备份，将 `deploy/backups/` 异地保存，然后由管理员明确处理 `deploy/data/`、`deploy/open_config/` 和 `deploy/.env`。部署工具不会自动删除这些数据。
