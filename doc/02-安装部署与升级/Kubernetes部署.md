# Kubernetes 部署

仓库在 `deploy/helm/zenvis/` 提供 Helm Chart。它支持测试环境内置依赖和生产环境外部依赖两种模式，应用基线为单副本。

## 前置检查

```bash
kubectl config current-context
kubectl cluster-info
helm version
```

确认当前 context 指向目标集群，并准备：

- Kubernetes 1.28+、Helm 3、可用的默认或指定 StorageClass；
- 节点架构与 `global.arch` 一致；
- 集群可拉取 `global.imageRegistry` 下的镜像；
- 生产环境的 TLS、DNS、NetworkPolicy 和数据库备份设施。

## 测试环境：内置依赖

```bash
./zenvisctl k8s install \
  --namespace zenvis \
  --release zenvis \
  -f deploy/helm/zenvis/values-dev.yaml
```

工具会创建 Namespace、随机 Secret、开放配置种子 ConfigMap 和 Helm release。内置依赖均为单实例并使用 PVC，不适合作为高可用生产数据库。

访问方式：

```bash
kubectl -n zenvis port-forward svc/zenvis-frontend 11000:11000
```

## 生产环境：外部依赖

复制示例到仓库外并填写真实值：

```bash
cp deploy/helm/zenvis/values-production.example.yaml /secure/path/zenvis-production.yaml
```

生产 values 至少配置：

- 固定的 `global.version`，不能使用 `latest`；
- 私有镜像仓库凭据 `global.imagePullSecrets`；
- MySQL、ClickHouse、Redis、Redis Stack 和 Kafka 的内部地址；
- StorageClass、Ingress、TLS 和必要的 NetworkPolicy；
- 与目标节点一致的 `global.arch`。

外部依赖应预先创建 `zenvis` 数据库和最小权限账号，保证集群网络与 DNS 可达，并由各依赖自身的备份系统负责一致性备份。部署命令：

```bash
./zenvisctl k8s install \
  --namespace zenvis-prod \
  --release zenvis \
  -f /secure/path/zenvis-production.yaml
```

默认情况下工具创建 `<release>-secrets`。若要复用预先创建的 Secret，请给所有 `zenvisctl k8s` 命令增加 `--secret-name <名称>`；命令行参数会覆盖 values 中的 `secrets.existingSecret`。Secret 应包含：

```text
MYSQL_ROOT_PASSWORD
MYSQL_PASSWORD
CLICKHOUSE_PASSWORD
REDIS_PASSWORD
KAFKA_CLUSTER_ID
API_BEARER_TOKEN
MCP_BEARER_TOKEN
VECTUM_AUTH_TOKEN
ZENVIS_BOOTSTRAP_SUPER_ADMIN_PASSWORD
ZENVIS_BOOTSTRAP_ADMIN_PASSWORD
```

其中 `MYSQL_ROOT_PASSWORD` 仅供内置 MySQL 使用，外部 MySQL 模式仍可保留该键以统一 Secret 结构。不要把 Secret 明文写入 values 或 Git。

## 资源与持久化

Chart 为后端开放配置和 Vectum 工作区创建独立 PVC；内置依赖模式还会创建数据库与消息组件的 PVC。大小由 `persistence.*.size` 配置。

后端和 Vectum 采用 `Recreate`，Chart schema 将副本数限制为 1。若要扩容，应另行设计共享文件存储、任务调度互斥、会话和数据库连接容量，不要直接解除限制。

## Ingress 与网络

设置 `ingress.enabled=true` 后，Ingress 将 Web 根路径交给前端，前端再代理 `/zenvis/` 到后端。生产环境应启用 TLS。`networkPolicy.enabled=true` 提供基础隔离，但具体 DNS、监控、入口控制器和外部数据库网段仍需按集群 CNI 调整。

## 状态与日志

```bash
./zenvisctl k8s status --namespace zenvis-prod --release zenvis \
  -f /secure/path/zenvis-production.yaml

./zenvisctl k8s logs backend --namespace zenvis-prod --release zenvis \
  -f /secure/path/zenvis-production.yaml
```

日志组件名可使用 `backend`、`frontend` 或 `vectum`。数据库和中间件日志在内置模式下使用 `kubectl logs statefulset/<release>-<component>` 查看。
