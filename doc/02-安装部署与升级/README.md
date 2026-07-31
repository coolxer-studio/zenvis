# 安装、部署与升级

本章面向平台管理员和运维人员，覆盖从首次安装到备份、升级、回滚和故障排查的完整路径。生产部署以仓库根目录的 `zenvisctl` 为统一入口；不建议绕过该工具直接修改持久化数据。

## 选择部署方式

| 场景 | 推荐方式 | 依赖形态 | 入口 |
| --- | --- | --- | --- |
| 单机体验、演示、规模较小的内部环境 | Docker Compose | MySQL、ClickHouse、Redis、Redis Stack、Kafka 全部内置 | [Docker Compose 部署](Docker-Compose部署.md) |
| Kubernetes 测试环境 | Helm 内置依赖模式 | 所有依赖随 Chart 部署 | [Kubernetes 部署](Kubernetes部署.md#测试环境内置依赖) |
| Kubernetes 生产环境 | Helm 外部依赖模式 | 使用受管或独立的数据库与中间件 | [Kubernetes 部署](Kubernetes部署.md#生产环境外部依赖) |
| 修改前后端源码 | 本地开发环境 | 基础设施可复用 Compose | [开发指南](../07-开发指南/README.md) |

当前 Helm 基线限定后端与 Vectum 为单副本，持久卷采用 `ReadWriteOnce`。它适合建立规范化的 Kubernetes 部署，但不宣称应用层高可用；需要扩容前应先完成共享存储、并发任务和数据库容量验证。

## 推荐阅读顺序

1. [快速开始](快速开始.md)：用最短路径完成新环境安装和验收。
2. [配置参考](配置参考.md)：理解端口、凭据、镜像、AI 和外部服务配置。
3. [备份与恢复](备份与恢复.md)：在承载正式数据前建立可验证的备份。
4. [升级与回滚](升级与回滚.md)：使用固定版本、升级前备份和显式回滚。
5. [运维与故障排查](运维与故障排查.md)：日常检查、日志和常见问题定位。

## 统一管理入口

```bash
./zenvisctl compose init|doctor|up|status|logs|down
./zenvisctl compose backup
./zenvisctl compose upgrade --version <固定版本>
./zenvisctl compose rollback --backup <备份ID> --confirm <备份ID>

./zenvisctl k8s init|doctor|install|status|logs
./zenvisctl k8s backup --external-ref <外部备份引用>
./zenvisctl k8s upgrade --version <固定版本> --external-ref <外部备份引用>
./zenvisctl k8s rollback --backup <备份ID> --confirm <备份ID>
```

`zenvisctl` 默认在 Linux、macOS 和 Windows WSL 的 Bash 环境运行。所有生产密码和令牌均由环境文件或 Kubernetes Secret 注入，不在仓库内提供通用默认值。

## 安全边界

- `deploy/.env`、`deploy/backups/` 和 `deploy/.zenvis/` 已被 Git 忽略，仍应限制主机文件权限并纳入密钥管理制度。
- Web 默认监听所有网卡；API、Vectum 和基础设施端口默认只监听 `127.0.0.1`。
- 对外服务应由 TLS 入口代理暴露，不应直接开放 MySQL、ClickHouse、Redis 或 Kafka。
- 初始化密码只在账号不存在时生效，不会覆盖已有账号。首次登录后必须修改密码。
- `latest` 适合体验环境；正式升级必须使用不可变的固定版本标签。

## 目录与数据归属

| 路径 | 内容 | 是否备份 |
| --- | --- | --- |
| `deploy/.env` | Compose 凭据与运行参数 | 必须，按密钥材料保护 |
| `deploy/data/` | Compose 数据库、中间件与 Vectum 数据 | 必须 |
| `deploy/open_config/` | 元数据、页面、插件、Skill 等开放配置 | 必须 |
| `deploy/config/` | 应用与中间件配置 | 必须 |
| `deploy/helm/zenvis/` | Helm Chart | 纳入 Git |
| `deploy/backups/` | 工具生成的本地备份 | 必须异地复制 |

安装问题先执行对应平台的 `doctor`，再参阅[运维与故障排查](运维与故障排查.md)。
