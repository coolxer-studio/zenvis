# Vectum — 基于 Vector 的可视化数据管道管理工具

![图标](doc/slogan.png)

**Vectum** 是一款基于 **Vector** 封装的轻量级数据管道管理工具，面向运维/DevOps 团队，提供**可视化界面 + RESTful API + MCP 协议**三位一体能力，用于统一管理、调度、监控多实例 Vector 数据采集任务，支持日志、指标、安全审计数据的全链路采集、转换与转发。

---

## 快速运行 Vectum 服务

### 1. docker-compose 运行

```bash
cd vectum/deploy
export VECTUM_AUTH_TOKEN='change-me'
docker-compose up -d
```
### 2. 服务访问

| 服务 | 地址 |
| :--- | :--- |
| WEB/API 服务 | `http://<ip>:11002` |
| Swagger 文档 | `http://<ip>:11002/swagger-ui/index.html` |
| MCP 接口 | `http://<ip>:11002/sse` |

### 3. API应用示例
参考 [API 文档](doc/api.md)

### 4. web ui 应用示例
![web ui 应用示例](doc/show-web.gif)

### 5. MCP 应用示例
mcp配置参数:  
```
{
  "mcpServers": {
    "vectum-http-mcp": {
      "url": "http://<ip>:11002/sse",
      "headers": {
        "Authorization": "Bearer <VECTUM_AUTH_TOKEN>"
      }
    }
  }
}

```
![mcp 应用示例](doc/show-mcp.gif)

## 一、产品定位

Vectum = **Vector 多实例编排 + 可视化运维 + API 化管理 + MCP 智能体**

### 解决原生 Vector 的痛点

| 原生 Vector 痛点 | Vectum 解决方案 |
| :--- | :--- |
| 纯命令行操作，运维门槛高 | 提供可视化 Web UI |
| 多实例管理困难 | 进程隔离，独立管理 |
| 配置编写成本高 | AI 自动生成配置 |
| 无统一监控与日志入口 | 统一监控面板 |

---

## 二、核心能力

### 1. 多实例 Vector 管理
- 每个任务对应 **独立 Vector 进程**
- 支持并行运行任意数量采集任务
- 进程隔离、配置隔离、日志隔离

### 2. RESTful API 能力
- **任务管理**：创建、更新、删除任务配置
- **启停控制**：启动 / 停止 / 重启指定任务
- **日志查询**：实时查看任务运行日志、错误日志
- **状态监控**：获取任务运行状态、进程 PID

### 3. 可视化界面（Web UI）
- **表单创建任务**：提交一次 = 创建一个独立 Vector 进程
- **任务列表**：展示所有任务，支持启动/停止/编辑/删除
- **实时监控**：查看运行状态、进程信息、资源占用
- **日志查看**：实时流查看任务日志，支持搜索过滤

### 4. MCP 协议支持（对接 AI）
- 暴露标准 MCP 接口，支持 AI 客户端调用
- 自然语言生成 Vector 配置
- 一键部署 AI 生成的配置为任务

---

## 三、典型使用场景

| 场景 | 描述 |
| :--- | :--- |
| 服务器日志采集 | Nginx、Syslog、应用日志统一采集 |
| 云原生数据采集 | K8s 日志、指标、审计数据采集 |
| 安全日志转发 | 统一转发到 Elasticsearch、Splunk |
| 异构数据源处理 | 文件、Kafka、MySQL、Redis 统一处理 |
| AI 自动化配置 | 自然语言生成数据管道配置 |

---

## 四、系统架构

![系统架构图](doc/architecture.png)

### 架构分层

#### 1. 底层：实例运行层
- 每个实例拥有独立文件隔离空间（配置、日志、脚本）
- 通过命令行指令启动，支持多实例并行运行

#### 2. 中间层：平台核心管理层
| 模块 | 核心能力 |
| :--- | :--- |
| 工作空间管理 | 实例独立空间生命周期管理 |
| 命令行管理 | 封装底层命令行操作 |
| 调度管理 | 任务调度、资源分配、状态监控 |
| RESTful API 封装 | 标准化 HTTP 接口 |

#### 3. 上层：接入层
- **业务系统对接**：通过 API 集成
- **AI 智能体对接**：通过 MCP 协议
- **可视化操作**：Web UI 界面

---

## 五、快速上手

### 1. 技术栈

| 分类 | 技术 | 版本 |
| :--- | :--- | :--- |
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.2.0 |
| 数据管道 | Vector | 0.35+ |
| API 文档 | SpringDoc OpenAPI | 2.3.0 |

### 2. 环境要求

- JDK 17+
- Maven 3.8+
- Vector 0.35+（需提前安装）

### 3. 启动方式

#### 开发态运行

```bash
# 进入项目目录
cd vectum

# 编译项目
mvn clean compile

# 运行项目
mvn spring-boot:run
```

#### 打包构建

```bash
# 打包
mvn clean package

# 运行打包后的 Jar
VECTUM_AUTH_TOKEN='change-me' java -jar target/application.jar --spring.config.location=src/main/resources/application.properties
```

#### Docker 构建 启动

```bash
# 使用构建脚本构建（默认不推送镜像）
./build.sh

# 构建并推送镜像到 Docker Registry
PUSH_IMAGE=true ./build.sh

# 运行容器
docker run -d -p 11002:11002 -e VECTUM_AUTH_TOKEN='change-me' crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/vectum:latest-{arch}
```

**构建参数说明：**

| 参数 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `PUSH_IMAGE` | `false` | 是否推送镜像到 Docker Registry |

**镜像标签说明：**

| 标签格式 | 示例 | 说明 |
| :--- | :--- | :--- |
| `latest-{arch}` | `latest-amd64`, `latest-arm64` | 最新版本标签 |
| `{YYYYMMDD}-{arch}` | `20240115-amd64` | 日期版本标签 |

**支持架构：**

- `amd64`（x86_64）
- `arm64`（aarch64）

---

## 六、配置说明

### 配置文件

### 主要配置项

| 配置项 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `server.port` | `11002` | 服务端口 |
| `vector.home` | `/vector/` | Vector 安装目录 |
| `task.workspace` | `/workspace/` | 任务工作空间目录 |
| `task.file` | `/tasks.json` | 任务数据存储文件 |
| `spring.task.execution.pool.core-size` | `10` | 异步线程池核心大小 |
| `spring.task.execution.pool.max-size` | `20` | 异步线程池最大大小 |
| `ZENVIS_BUSINESS_SERVICE_ENABLED` | `true` | 是否启用 Zenvis 服务注册、心跳与事件上报 |
| `ZENVIS_BUSINESS_SERVICE_BASE_URL` | 开发环境 `http://localhost:11001` | Zenvis 后端根地址，不包含 `/api/v1` |
| `ZENVIS_BUSINESS_SERVICE_INSTANCE_ID` | 自动推导 | 实例唯一标识，生产多实例部署时建议显式设置 |

### Zenvis 业务服务对接

Vectum 通过 `zenvis-business-service-spring-boot-starter` 自动向 Zenvis 注册实例、发送心跳，并在应用正常关闭时上报停止状态。任务创建、更新、删除、启动和停止也会作为异步业务事件上报；Zenvis 暂时不可用不会影响 Vectum 的任务操作。

构建前请确认 `com.coolxer.zenvis:zenvis-business-service-spring-boot-starter:0.1.0-SNAPSHOT` 已发布到 Maven 仓库，或已安装到本机 Maven 仓库。

生产环境至少需要配置 Zenvis 后端根地址：

```bash
export ZENVIS_BUSINESS_SERVICE_BASE_URL=http://zenvis-backend:11001
```

多个 Vectum 实例并行部署时，应为每个实例配置不同的 `ZENVIS_BUSINESS_SERVICE_INSTANCE_ID`。如需关闭对接，可设置 `ZENVIS_BUSINESS_SERVICE_ENABLED=false`。

### Vector 配置说明

每个任务的配置支持 **YAML、TOML、JSON** 三种格式，系统会自动识别格式类型：

**TOML 格式示例：**
```toml
[sources.my_demo_log]
type = "demo_logs"
format = "apache_common"

[sinks.my_sink]
type = "console"
encoding.codec = "json"
inputs = [ "my_demo_log" ]
```

**YAML 格式示例：**
```yaml
sources:
  my_demo_log:
    type: demo_logs
    format: apache_common

sinks:
  my_sink:
    type: console
    encoding:
      codec: json
    inputs:
      - my_demo_log
```

**JSON 格式示例：**
```json
{
	"sources": {
		"my_demo_log": {
			"type": "demo_logs",
			"format": "apache_common"
		}
	},
	"sinks": {
		"my_sink": {
			"type": "console",
			"encoding": {
				"codec": "json"
			},
			"inputs": [
				"my_demo_log"
			]
		}
	}
}
```

---

## 七、项目结构

```
vectum/
├── src/main/java/com/coolxer/
│   ├── Application.java              # 启动类
│   ├── controller/                   # REST API 控制器
│   │   ├── IndexController.java      # 首页控制器
│   │   └── TaskController.java       # 任务管理接口
│   ├── service/                      # 业务服务层
│   │   ├── TaskService.java          # 任务服务接口
│   │   ├── VectorService.java        # Vector 进程管理接口
│   │   ├── MonitorService.java       # 日志监控接口
│   │   ├── McpTaskTools.java         # MCP 工具类
│   │   └── impl/                     # 服务实现类
│   ├── model/                        # 数据模型
│   │   ├── dto/                      # 数据传输对象
│   │   │   └── TaskDto.java          # 任务传输对象
│   │   ├── vo/                       # 视图对象
│   │   │   ├── ResponseWrap.java     # 统一响应包装
│   │   │   └── TaskVo.java           # 任务视图对象
│   │   ├── Task.java                 # 任务实体
│   │   ├── PushTask.java             # 推送任务
│   │   ├── LuaFile.java              # Lua 文件模型
│   │   └── Result.java               # 结果模型
│   ├── dao/                          # 数据访问层
│   │   ├── TaskRepository.java       # 任务数据访问接口
│   │   └── TaskRepositoryImpl.java   # 任务数据访问实现
│   ├── config/                       # 配置类
│   │   ├── AsyncConfig.java          # 异步线程池配置
│   │   ├── JacksonConfig.java        # Jackson 配置
│   │   ├── McpConfig.java            # MCP 协议配置
│   │   └── OpenApiConfig.java        # OpenAPI 文档配置
│   ├── commons/                      # 公共组件
│   │   ├── enums/                    # 枚举类
│   │   │   ├── ResultCodeEnum.java   # 结果码枚举
│   │   │   └── TaskSourceEnum.java   # 任务来源枚举
│   │   └── exception/                # 异常处理
│   │       └── ApiException.java     # API 异常类
│   ├── component/                    # Spring 组件
│   │   └── StartRunnerComponent.java # 启动时执行组件
│   └── utils/                        # 工具类
│       └── FileUtil.java             # 文件工具类
├── src/main/resources/
│   ├── static/                       # 静态资源
│   │   ├── images/                   # 图片资源
│   │   └── sdk/                      # SDK 静态文件
│   ├── templates/                    # 模板文件
│   │   └── index.html                # 首页模板
│   ├── application.properties        # 应用配置
│   ├── application-dev.properties    # 开发环境配置
│   ├── application-prod.properties   # 生产环境配置
│   └── logback.xml                   # 日志配置
├── src/test/                         # 测试代码
├── deploy/                           # 部署配置
│   ├── config/                       # 配置文件
│   ├── docker-compose.yml            # Docker Compose 配置
│   └── docker-compose.example.yml    # Docker Compose 示例
├── doc/                              # 文档资源
│   ├── API接口.md                    # API 接口文档
│   ├── architecture.png              # 架构图
│   └── slogan.png                    # 图标
├── pom.xml                           # Maven 依赖
├── Dockerfile                        # Docker 配置
├── build.sh                          # 构建脚本
├── LICENSE                           # 许可证
├── CONTRIBUTING.md                   # 贡献指南
└── README.md                         # 项目文档
```

---

## 八、API 接口文档

### 基础路径

所有 API 接口前缀：`/vectum/api/v1/task`

### 接口列表

| HTTP 方法 | 路径 | 功能 | 参数 |
| :--- | :--- | :--- | :--- |
| POST | `/add` | 创建任务 | `TaskDto` JSON 对象 |
| PUT | `/{id}` | 更新任务 | `id`, `TaskDto` |
| DELETE | `/{id}` | 删除任务 | `id` (路径参数) |
| DELETE | `/batch` | 批量删除任务 | `ids` (逗号分隔) |
| PUT | `/batch` | 批量更新任务 | `ids`, `TaskDto` |
| GET | `/all` | 查询所有任务 | 无 |
| GET | `/{id}/view` | 查询任务详情 | `id` (路径参数) |
| POST | `/{id}/toggle` | 启动/停止任务 | `id` (路径参数) |
| GET | `/{id}/log` | 获取任务日志 | `id`, `log_type` |

详细API接口说明参考 [API接口.md](doc/API接口.md)


---

## 九、优势

| 特性 | 说明 |
| :--- | :--- |
| 轻量无依赖 | 底层 Vector 单二进制，无复杂依赖 |
| 一键部署 | Docker / 二进制均可快速部署 |
| 多实例隔离 | 任务之间互不影响，稳定性高 |
| 全栈可视化 | 无需懂 Vector 配置也能使用 |
| AI 驱动 | 自然语言生成配置，零门槛 |
| 企业级 API | 可深度集成到业务系统 |

---

## 十、适用人群

- 运维工程师
- DevOps 工程师
- 数据平台团队
- 需要统一日志采集的业务团队

---

## 十一、贡献指南

欢迎提交 Issue 和 Pull Request！
贡献指南参考 [CONTRIBUTING.md](CONTRIBUTING.md)

---

## 十二、许可证

Apache 2.0 License

---

## 十三、联系方式

如有问题或建议，欢迎通过以下方式联系：

- 提交 Issue
- 发送邮件：<coolxer@163.com>

---

**Vectum** — 让数据管道管理更简单
