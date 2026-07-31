# `deploy` 开发对接指南

## 项目定位

`deploy` 是 ZenVis 完整运行环境的 Docker Compose 编排目录，负责基础设施、后端、前端和 Vectum 的启动关系，以及平台开放配置和持久化目录的挂载。

它面向本地集成验证和单机部署。生产环境可以复用其中的配置契约，但仍应在网关、凭据、资源、备份和高可用方面进行环境化设计。

## 上下游关系

`deploy` 上游接收后端、前端、Vectum 和插件的镜像及配置契约，下游为浏览器访问、业务服务接入和插件数据接入提供统一运行环境。修改端口、服务名、挂载或凭据时，需要同步核对应用配置、健康检查、Nginx 代理和插件任务。

## 技术栈与当前服务

编排基于 Docker Compose，基础设施包含 MySQL、ClickHouse、Redis、Redis Stack 和 Kafka，应用层包含 ZenVis 后端、前端与 Vectum。

`docker-compose.yml` 当前声明 8 个服务：

| 服务 | 容器内职责 | 宿主机入口 |
| --- | --- | --- |
| `zenvis-frontend` | Nginx 和前端静态资源 | `11000` |
| `zenvis-backend` | Spring Boot 平台 API | `11001` |
| `vectum-service` | 推送任务和 Vector 运行服务 | `11002` |
| `mysql-service` | 平台关系数据 | `3306` |
| `clickhouse-service` | 插件实体分析数据 | `8123`、`9000`、`9009` |
| `redis-service` | Session、缓存和运行状态 | `6379` |
| `redis-stack-service` | RAG 向量索引 | `16379` 映射到容器 `6379` |
| `kafka-service` | 数据接入消息队列 | `9094` 映射到外部 listener |

镜像后缀由 `deploy/.env` 中的 `ARCH` 决定，当前支持 `amd64` 和 `arm64` 镜像命名。

## 目录职责

```text
deploy/
├── .env
├── docker-compose.yml
├── config/
│   ├── mysql/
│   ├── redis/
│   ├── redis-stack/
│   ├── zenvis-backend/
│   └── zenvis-frontend/
├── data/
│   ├── mysql/
│   ├── clickhouse/
│   ├── redis/
│   ├── redis-stack/
│   ├── kafka/
│   └── vectum/
└── open_config/
    ├── meta_config/
    ├── plugin-package_config/
    ├── push-task_config/
    ├── skill_config/
    ├── menu_config/
    ├── dashboard_config/
    └── ...
```

- `config/`：容器启动配置，属于部署模板。
- `data/`：数据库、Kafka、Vectum 等运行数据，不是源码。
- `open_config/`：后端读取的开放配置根目录，容器内挂载为 `/config/open_config`。
- `plugin-package_config/`：已安装插件的展开目录和运行时安装状态，不应手工当作插件源码维护。

## 环境与配置

### 1. 检查架构

```bash
cd deploy
uname -m
sed -n '1,20p' .env
```

确保 `ARCH` 与实际镜像架构一致。

### 2. 解析 Compose

```bash
docker compose config --services
docker compose config --images
docker compose config
```

解析失败时先处理缺失变量、路径或 Compose 版本问题，不要直接启动部分未知配置。

### 3. 配置凭据

数据库、REST API、MCP、Vectum 和模型服务凭据应通过部署环境注入或受控配置管理。仓库中的默认值只用于开发基线，生产部署必须替换，并保持：

- Compose 环境变量；
- `config/zenvis-backend/config/application.properties`；
- MySQL 初始化配置；
- ClickHouse 和 Vector sink；
- 外部调用方配置

之间一致。文档和提交记录中不要复制真实值。

## 启动、构建与测试

`deploy` 本身不编译应用源码；镜像构建由各项目负责。本目录的核心验证是 Compose 解析、容器启动和健康检查。

### 启动完整环境

```bash
cd deploy
docker compose up -d
docker compose ps
```

### 只启动本地开发依赖

后端和前端在宿主机运行时，可以只启动基础服务：

```bash
docker compose up -d \
  redis-service redis-stack-service mysql-service \
  clickhouse-service kafka-service vectum-service
```

### 查看状态和日志

```bash
docker compose ps
docker compose logs --tail=200 zenvis-backend
docker compose logs --tail=200 vectum-service
docker compose logs -f zenvis-frontend
```

### 更新镜像

```bash
docker compose pull
docker compose up -d
docker compose ps
```

更新前应备份数据库和 `open_config`，确认插件与目标后端版本兼容。不要在没有备份的情况下删除 `data/` 或执行 `docker compose down -v`。

## 启动关系和健康检查

- 后端等待 Redis、Redis Stack、MySQL 和 ClickHouse 健康后启动。
- 前端依赖后端容器，但 `depends_on` 不等同于业务接口完全可用。
- Kafka 和 Vectum 独立启动；推送任务还需要它们各自健康。
- 后端健康检查访问 `/api/v1/system/about/info`。
- 前端健康检查访问容器内 `http://localhost:11000`。
- Vectum 健康检查访问 `/actuator/health`。

验证命令：

```bash
curl -fsS http://localhost:11000/
curl -fsS http://localhost:11001/api/v1/system/about/info
curl -fsS http://localhost:11002/actuator/health
```

## 核心开发流程

### 开放配置开发

`open_config` 按 `<type>_config` 分类。修改前应先确认对应后端配置 DTO、Schema 和重载逻辑：

1. 读取现有 `index.json` 或配置文件。
2. 保持 JSON 字段、标识和目录名与后端约定一致。
3. 明确配置是静态基线、插件安装产物还是运行时数据。
4. 修改后解析 JSON，并通过后端配置接口或目标功能验证重载。

插件源码不在 `open_config/plugin-package_config` 中直接开发。应在 `zenvis-plugin` 或 `zenvis-plugin-community` 修改、校验和打包，再通过插件生命周期安装。

### 与本地开发的配合

#### 后端

宿主机后端使用 `application-dev.properties` 时，数据库、Redis、Vectum 地址应指向 Compose 暴露的宿主机端口。不要直接复制容器内主机名配置。

#### 前端

前端 `yarn server:dev` 运行在 `8090`，通过 `/zenvis` 代理到 `VITE_BASE_API`，通常为宿主机 `11001`。

#### 插件

插件的 Kafka、ClickHouse 和 Vectum 默认地址通常使用容器网络服务名。宿主机单独验证 Vector 配置时，需要改用可访问的宿主机地址或显式环境变量。

## 扩展点

- 新增服务时同步声明镜像、网络、端口、挂载、环境变量、健康检查和实际依赖条件。
- 新增开放配置类型时同步后端 DTO、Schema、加载/重载逻辑和 `open_config/<type>_config` 基线。
- 调整反向代理时同时核对前端 `/zenvis` 基础路径、Nginx 配置和后端 `/api/v1/**`，不改变既有公开 API。
- 调整持久化布局时提供迁移和回滚说明，不把已有 `data/` 当成可重新生成的临时目录。

## 常见问题

### 镜像无法启动或提示架构不匹配

核对 `uname -m`、`.env` 中的 `ARCH` 和 `docker compose config --images` 输出。

### 后端健康检查失败

依次检查 MySQL、ClickHouse、Redis、Redis Stack 的健康状态，再检查后端外部配置、挂载路径和日志。

### 前端页面可访问但 API 404

检查 Nginx `/zenvis` 代理、后端 `11001`、前端 `VITE_BASE_URL` 以及请求是否重复或遗漏 `/zenvis`。

### 推送任务启动但没有数据

检查 Vectum 健康状态、Kafka listener、任务环境变量、ClickHouse 认证、目标表和 DLQ。

### 修改初始化 SQL 后没有生效

MySQL 初始化脚本通常只在空数据目录首次启动时执行。已有数据环境应使用受控迁移，不要删除数据目录来强制重放。

## 交付检查

- `docker compose config` 可解析；
- 所有服务、端口、挂载和健康检查与文档一致；
- 没有新增真实密码、令牌或客户地址；
- `config` 与 `open_config` 修改有明确归属和回滚方式；
- 没有提交 `data/`、容器日志或临时插件安装产物；
- 升级说明包含备份、兼容性和健康检查步骤。

## 关联文档

- [安装部署与升级](/02-安装部署与升级/README.md)
- [`zenvis-backend` 开发对接指南](/07-开发指南/zenvis-backend-开发对接指南.md)
- [`zenvis-frontend` 开发对接指南](/07-开发指南/zenvis-frontend-开发对接指南.md)
- [`zenvis-plugin` 开发对接指南](/07-开发指南/zenvis-plugin-开发对接指南.md)
