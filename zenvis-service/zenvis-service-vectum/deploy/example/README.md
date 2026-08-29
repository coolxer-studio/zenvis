# Vectum 案例说明

本文档描述基于 Vectum 实现的数据管道传输案例，实现从 SFTP 获取数据 → Kafka 消息队列 → ClickHouse 数据存储的完整批量数据传输链路。

> **注意：**    
> 本案例仅用于演示和测试，不建议在生产环境中直接使用。  
> vectur不支持sftp的source，当前案例通过共享数据卷的方式实现。  
> 整个流程拆分成两个任务独立执行，第一个任务从SFTP读取数据写入kafka，第二个任务将kafka中的数据写入clickhouse。  

## 环境要求

- Docker 20.10+
- Docker Compose 2.0+
- 最低配置：2核4G内存

## 快速启动

```bash
cd vectum/deploy/example
docker-compose up -d
```

## 快速测试

### 1. 准备测试数据

创建测试数据文件用于上传到 SFTP 服务，测试数据管道功能：

```bash
# 创建测试数据文件（格式：姓名|年龄|学校|专业）
echo "张三|28|清华大学|信息工程专业" > file.txt
echo "李四|25|北京大学|计算机科学" >> file.txt
echo "王五|23|复旦大学|软件工程" >> file.txt

# 连接SFTP服务并上传文件
sftp -o StrictHostKeyChecking=no -P 2222 vectum@localhost
# 密码: vectum123

sftp> cd upload
sftp> put file.txt
sftp> quit
```

**也可以使用 upload\_test\_data.sh 脚本快速上传测试数据**。

```bash
chmod +x upload_test_data.sh
./upload_test_data.sh
```

### 2. 任务1（SFTP → Kafka）

#### 任务配置

创建任务，添加配置如下，验证数据管道功能：

```toml
# 输入源：读取SFTP上传目录
sources:
  sftp_files:
    type: file
    include:
      - /sftp-upload/*
    read_from: beginning
    line_delimiter: "\n"
    fingerprint:
      strategy: device_and_inode

# 数据转换：解析CSV格式并过滤有效数据
transforms:
  parse_csv:
    type: remap
    inputs:
      - sftp_files
    drop_on_error: false
    source: |
      parts = split!(.message, "|")
      if length(parts) < 4 {
        return null
      }
      .name = parts[0]
      .school = parts[2]
      .major = parts[3]
      .age = parse_int!(parts[1])

  filter_valid:
    type: filter
    inputs:
      - parse_csv
    condition: exists(.name)

# 输出目标：控制台 + Kafka
sinks:
  console_test:
    type: console
    inputs:
      - parse_csv
    encoding:
      codec: json

  kafka_output:
    type: kafka
    inputs:
      - filter_valid
    bootstrap_servers: kafka:9092
    topic: vectum-logs
    compression: lz4
    encoding:
      codec: json
```

#### 任务验证

##### 1.参考测试数据发送到 SFTP 服务目录

##### 2.验证 Kafka 数据

```bash
# 进入Kafka容器查看主题数据
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic vectum-logs --from-beginning
```

### 3. 任务2（Kafka → ClickHouse）

#### 任务配置

创建任务，添加配置如下，验证数据管道功能：

```toml
# 输入源配置
[sources.source_kafka_msg]
type = "kafka"
bootstrap_servers = "kafka:9092"
auto_offset_reset = "earliest"
group_id = "kafka_source"
topics = [ "vectum-logs" ]

# JSON解析转换
[transforms.parse_json]
inputs        = ["source_kafka_msg"]
type          = "remap"
drop_on_error = false
source        = '''
    . = parse_json!(string!(.message))
'''

# 数据路由：区分有效数据和异常数据
[transforms.route]
type = "route"
inputs = ["parse_json"]
route."non_empty_or_blank" = 'exists(.name)'

# 输出目标：ClickHouse
[sinks.my_clickhouse_sink]
type = "clickhouse"
inputs = ["route.non_empty_or_blank"]
endpoint = "http://clickhouse:8123"
database = "vectum"
table = "file_data"
auth.strategy = "basic"
auth.user = "default"
auth.password = "vectum123"
skip_unknown_fields = true

# 输出异常数据到控制台
[sinks.console]
inputs = ["route._unmatched"]
type = "console"
encoding.codec = "json"
```

#### 任务验证

##### 1.参考测试数据发送到 SFTP 服务目录

##### 2.验证 ClickHouse 数据

```bash
# 查询ClickHouse中的数据
docker exec -it clickhouse clickhouse-client -u default --password vectum123 -q "SELECT * FROM vectum.file_data LIMIT 5;"
```

## 服务列表

| 服务         | 镜像                                   | 端口             | 说明             |
| ---------- | ------------------------------------ | -------------- | -------------- |
| vectum     | coolxer-studio/vectum:latest         | 11002          | Vectum 主应用     |
| sftp       | coolxer-studio/atmoz-sftp:latest     | 2222           | SFTP 文件传输服务    |
| zookeeper  | coolxer-studio/bitnami-zookeeper:3.8 | 2181           | ZooKeeper 协调服务 |
| kafka      | coolxer-studio/bitnami-kafka:3.2.0   | 9092           | Kafka 消息队列     |
| clickhouse | coolxer-studio/clickhouse:22.8.16    | 8123/9000/9009 | ClickHouse 数据库 |

## 服务详情

### Vectum 主应用

主服务端口 11002，提供 RESTful API 和 Web UI 界面。

- **端口**: 11002
- **依赖**: Kafka、ClickHouse、SFTP
- **工作空间**: `workspace-data` 挂载到容器内 `/workspace`
- **vector缓存目录**: `vectum-data` 挂载到容器内 `/var/lib/vector`
- **健康检查**: `curl http://localhost:11002/actuator/health`

### SFTP 服务

用于 Vectum 任务配置文件的传输和管理。

- **端口**: 2222
- **认证用户**: `vectum / vectum123`
- **上传目录**: `/home/vectum/upload`
- **健康检查**: SFTP 连接测试

### ZooKeeper 服务

Kafka 集群协调服务。

- **端口**: 2181
- **健康检查**: `zkServer.sh status`

### Kafka 服务

分布式消息队列，用于 Vectum 任务间消息传递。

- **端口**: 9092
- **依赖**: ZooKeeper
- **消息最大字节**: 1MB
- **健康检查**: `kafka-topics.sh --list`

### ClickHouse 服务

列式数据库，用于 Vectum 日志存储和分析。

- **端口**:
  - 8123: HTTP 接口
  - 9000: Native TCP
  - 9009: 副本连接
- **认证**: `default / vectum123`
- **健康检查**: `clickhouse-client --query "SELECT 1"`

## 数据持久化

| 卷名              | 用途              | 驱动    |
| --------------- | --------------- | ----- |
| sftp-data       | SFTP 用户数据       | local |
| vector-data     | vector缓存数据      | local |
| workspace-data  | 工作空间数据          | local |
| zookeeper-data  | ZooKeeper 数据目录  | local |
| kafka-data      | Kafka 数据目录      | local |
| clickhouse-data | ClickHouse 数据目录 | local |
| clickhouse-logs | ClickHouse 日志   | local |

## 网络配置

- **网络名称**: vectum-network
- **驱动**: bridge

## 环境变量

| 服务         | 变量                                | 值                      |
| ---------- | --------------------------------- | ---------------------- |
| 全局         | TZ                                | Asia/Shanghai          |
| SFTP       | SFTP\_USERS                       | vectum:vectum123:1001  |
| ClickHouse | CLICKHOUSE\_DB                    | default                |
| ClickHouse | CLICKHOUSE\_USER                  | default                |
| ClickHouse | CLICKHOUSE\_PASSWORD              | vectum123              |
| Kafka      | KAFKA\_BROKER\_ID                 | 1                      |
| Kafka      | KAFKA\_CFG\_LISTENERS             | PLAINTEXT://:9092      |
| Kafka      | KAFKA\_CFG\_ADVERTISED\_LISTENERS | PLAINTEXT://kafka:9092 |
| Kafka      | KAFKA\_CFG\_ZOOKEEPER\_CONNECT    | zookeeper:2181         |

## 访问地址

| 服务              | 地址                                             |
| --------------- | ---------------------------------------------- |
| Vectum Web UI   | <http://localhost:11002>                       |
| Vectum API      | <http://localhost:11002/vectum/api/v1>         |
| Swagger 文档      | <http://localhost:11002/swagger-ui/index.html> |
| SFTP            | sftp\://localhost:2222                         |
| ClickHouse HTTP | <http://localhost:8123>                        |
| Kafka           | localhost:9092                                 |

## 停止服务

```bash
docker-compose down
```

## 清理数据

```bash
docker-compose down -v
```

## 健康检查

所有服务均配置了健康检查机制，Vectum 依赖服务启动完成后才启动：

- `depends_on` + `condition: service_healthy` 确保依赖服务健康
- 启动间隔 `start_period: 60s` 给予足够初始化时间

