# Docker Compose 部署

Compose 编排包含 9 个服务：ZenVis 前后端、Vectum、MySQL、ClickHouse、Redis、Redis Stack、MinIO 和单节点 Kafka。该模式将数据保存在 `deploy/data/`，适合单机部署和测试环境。

运行环境支持 Docker Compose v2（推荐）或 legacy `docker-compose` 1.29.2；不支持更低版本的 `docker-compose` v1。

## 初始化

在仓库根目录执行：

```bash
./zenvisctl compose init
```

工具执行以下安全初始化：

- 根据主机生成 `ARCH=amd64` 或 `ARCH=arm64`；
- 为数据库、Redis、MinIO、API、MCP、Vectum 和内置账号生成独立随机凭据；
- 将凭据写入 Git 忽略的 `deploy/.env` 并设置权限 `600`；
- 对已有 `deploy/.env` 保留原值，并自动补齐缺失的监听地址和主机端口；
- 如发现历史数据而 `.env` 丢失，则终止初始化，不猜测旧密码。

需要定制时，在首次启动前编辑 `deploy/.env`。不要把生成文件复制回 `.env.example`。

## 离线部署包

目标机无法访问镜像仓库时，先在可联网且已具备目标架构镜像的构建机生成离线包：

```bash
./package-offline.sh --arch arm64
```

默认输出到 `deploy/offline/zenvis-offline-<版本>-<架构>-<时间>.tar.gz`。可用 `--output` 指定文件或目录，`--pull` 在镜像缺失或架构不符时按目标架构拉取，`--force` 覆盖同名输出。一个离线包只包含一种 CPU 架构，`amd64` 和 `arm64` 需要分别打包。

压缩包包含：

- `quick-deploy.sh`、`zenvisctl` 和离线安装说明；
- `deploy/docker-compose.yml`、`.env.example`、`config/` 和 Git 跟踪的 `open_config/` 基线；
- `offline/images.tar`、镜像清单和 manifest。

离线包不包含 `deploy/.env`、`data/`、备份、插件展开缓存、本机自定义 HTML 页面和 Helm Chart。

目标机解压后直接执行：

```bash
tar -xzf zenvis-offline-<版本>-<架构>-<时间>.tar.gz
cd zenvis-offline-<版本>-<架构>-<时间>
./quick-deploy.sh
```

`quick-deploy.sh` 会校验离线包架构，先执行 `docker load` 导入 `offline/images.tar`，确认清单中的全部镜像存在，再执行 `compose init` 和 `compose up`。安装过程重新生成 `deploy/.env` 和全部随机凭据，不会沿用压缩包外部的生产密钥。

迁移已有环境时不要直接初始化空数据目录：先恢复数据库、对象存储和 `open_config`，再单独迁移原 `deploy/.env`，然后执行 `./zenvisctl compose doctor` 和 `./zenvisctl compose up`。

## 预置插件（可选）

首次部署通常需要安装插件才能看到业务效果。可在首次启动前，将插件包放入 `deploy/open_config/plugin-package_config/bootstrap/` 目录：

```bash
cp /path/to/plugin-xxx.tar.gz deploy/open_config/plugin-package_config/bootstrap/
```

首次启动时 zenvis-backend 会自动注册并安装目录中的全部插件包，正式包写入 `zenvis-plugins` Bucket，处理完成后生成标记文件 `.bootstrap-install.flag`，此后重启不再自动安装（卸载或删除插件也不会复活）。安装失败可在「插件管理」界面重试；需要重新触发时删除标记文件后重启。详见 `deploy/open_config/plugin-package_config/bootstrap/index.md`。

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
| Vectum | `zenvis-service-vectum:11002` | `127.0.0.1:11002` |
| MySQL | `mysql-service:3306` | `127.0.0.1:3306` |
| ClickHouse HTTP/TCP | `clickhouse-service:8123/9000` | `127.0.0.1:8123/9000` |
| Redis | `redis-service:6379` | `127.0.0.1:6379` |
| Redis Stack | `redis-stack-service:6379` | `127.0.0.1:16379` |
| MinIO API / Console | `minio-service:9000/9001` | `127.0.0.1:19000/19001` |
| Kafka | `kafka-service:9092` | `127.0.0.1:9094` |

后端通过 `MINIO_ENDPOINT`、`MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD` 连接 MinIO，并使用 `MINIO_BUCKET`、`MINIO_RAG_BUCKET`、`MINIO_CHAT_BUCKET`、`MINIO_PLUGINS_BUCKET`、`MINIO_REPORTS_BUCKET` 选择默认 Bucket 和四个业务 Bucket。启动时自动创建缺失 Bucket；创建失败或名称重复会使后端启动失败。业务下载经后端鉴权，不直接暴露 MinIO 端口给浏览器。

对外发布时仅暴露 Web，使用反向代理终止 TLS。若必须从其他主机访问 API，先设置防火墙和身份认证，再修改 `ZENVIS_API_BIND_ADDRESS`。

## 反向代理

Compose 前端 Nginx 已将 `/zenvis/` 转发到后端，并支持 SPA 路由回退。外层代理至少应：

- 将 HTTPS 请求转发到 `127.0.0.1:11000`；
- 传递 `Host`、`X-Real-IP`、`X-Forwarded-For` 和 `X-Forwarded-Proto`；
- 根据上传需求配置请求体上限和超时；
- 只允许受信网络访问管理入口。

## 停机与卸载

临时停机使用 `./zenvisctl compose down`。永久卸载前先执行备份，将 `deploy/backups/` 异地保存，然后由管理员明确处理 `deploy/data/`、`deploy/open_config/` 和 `deploy/.env`。部署工具不会自动删除这些数据。
