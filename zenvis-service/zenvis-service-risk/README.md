# zenvis-service-risk

## 项目简介

zenvis-service-risk 是 ZenVis 微服务体系中的**设备威胁指数服务**，基于 Spring Boot 3.2.0 构建。该服务通过消费 Kafka 中的设备探针上报数据（Fact），实时聚合多平台（Android / iOS / H5 / Host / WeChat）设备的风险标签，按照可配置的评分策略计算设备威胁指数分值，并将结果存储至 Redis 供外部查询。

***

## 一、核心能力

| 能力 | 说明 |
| :--- | :--- |
| 实时风险数据采集 | 批量消费 Kafka 中各平台的 Fact Topic，异步线程池处理 |
| 风险标签聚合 | 按设备启动（startId）和时间维度双路聚合风险标签 |
| 威胁指数评分 | 基于可配置评分策略（基础分 + 叠加分 + 封顶分）实时计算威胁指数 |
| 风险等级判定 | 按策略中的等级区间规则（低风险/中风险/高风险）将分值映射为风险等级 |
| 定时矫正评分 | 每日凌晨 1 点全量重新评分，消除实时计算误差 |
| 风险事件分发 | 识别特定风险标签后，构建 RiskBaseLine 对象发送至 Kafka `risk_all` Topic |
| REST 查询接口 | 提供设备标签查询和威胁指数评分查询 API |

***

## 二、技术栈

| 分类 | 技术 | 版本 |
| :--- | :--- | :--- |
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.2.0 |
| 消息队列 | Spring Kafka | - |
| 缓存 | Spring Data Redis | - |
| API 文档 | Springfox Swagger | 3.0.0 |
| 工具库 | Lombok / Commons Lang3 | - |
| 构建工具 | Maven | 3.8+ |

***

## 三、环境要求

- JDK 17+
- Maven 3.8+
- Kafka（数据源 + 风险事件下游）
- Redis（数据表 + 评分表存储）

***

## 四、配置说明

### 主要配置项

| 配置项 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `server.port` | `11005` | 服务端口 |
| `spring.kafka.consumer.bootstrap-servers` | - | Kafka 消费者连接地址 |
| `spring.kafka.consumer.group-id` | `risk-service-2` | Kafka 消费者组 ID |
| `spring.kafka.consumer.listen-topics` | - | 监听的 Fact Topic 列表（逗号分隔） |
| `spring.data.redis.host` | - | Redis 主机地址 |
| `spring.data.redis.port` | `6379` | Redis 端口 |
| `spring.data.redis.password` | - | Redis 密码 |
| `rating.data.expire.days` | `30` | 风险数据表过期天数 |
| `rating.rule.config.path` | - | 评分策略配置文件路径 |
| `spring.kafka.producer.properties.partitioner.class` | `KeyHashPartitioner` | 自定义 Kafka 分区器 |
| `kafka.topic.partitions` | `1` | Kafka Topic 分区数 |
| `kafka.topic.replicas` | `1` | Kafka Topic 副本数 |
| `spring.task.execution.pool.core-size` | `10` | 异步线程池核心线程数 |
| `spring.task.execution.pool.max-size` | `20` | 异步线程池最大线程数 |
| `spring.task.execution.pool.queue-capacity` | `30` | 异步线程池队列容量 |
| `spring.jackson.date-format` | `yyyy-MM-dd HH:mm:ss` | 全局日期格式 |
| `spring.jackson.time-zone` | `Asia/Shanghai` | 全局时区 |

### 评分策略配置

评分策略通过 JSON 文件配置（`rating_rule.json`），启动时由 `ApplicationConfig` 加载到内存。配置结构如下：

```json
[{
  "name": "默认策略",
  "app_id": "1",
  "code": "default",
  "computation_period": 7,
  "grade_rules": {
    "低风险": { "from": 0, "to": 50 },
    "中风险": { "from": 50, "to": 100 },
    "高风险": { "from": 100, "to": null }
  },
  "status": 1,
  "score_rules": [{
    "tag": "ROOT:HIGH",
    "basic_score": 40,
    "superposition_score": 4,
    "top_score": 80
  }]
}]
```

**评分计算规则**：

- **基础分（basic_score）**：标签首次命中时记录的初始分值
- **叠加分（superposition_score）**：每多命中一次，分值递增叠加分
- **封顶分（top_score）**：单个标签的分值上限
- **总分**：所有命中标签分值之和，上限为 99999

***

## 五、系统架构

### 数据处理流程

```
Kafka Fact Topics
        │
        ▼
KafkaConsumerComponent（批量消费 + 异步处理）
        │
        ├──→ FactMsg → ProductService.sendFactMsg()
        │         └──→ 识别风险标签 → 构建 RiskBaseLine → 发送至 risk_all Topic
        │
        └──→ FactMsg.toRatingData() → 按维度聚合
                  ├──→ 按 startId 分组 → updateLastStart()（更新最近启动数据）
                  └──→ 按 日期 分组 → rating()
                            ├──→ updateRatingData()（合并历史数据，写入 Redis Hash）
                            └──→ calculateScoreAndSave()（按策略算分，写入 Redis ZSet）
```

### Redis 数据结构

| 数据类型 | Key 格式 | Hash/ZSet 结构 | 说明 |
| :--- | :--- | :--- | :--- |
| 风险数据表（Hash） | `rating-data-{appId}:{guid}` | hashKey=`yyyyMMdd`，value=标签Map JSON | 按天存储设备风险标签 |
| 最近启动数据（Hash） | `rating-data-{appId}:{guid}` | hashKey=`last-start`，value=RatingData JSON | 缓存最近一次启动的聚合数据 |
| 评分表（ZSet） | `rating-score-{appId}:{code}` | member=`guid`，score=威胁指数分值 | 按策略存储设备评分 |

### 定时任务

| 任务 | Cron 表达式 | 说明 |
| :--- | :--- | :--- |
| 全量矫正评分 | `0 0 1 * * *` | 每天凌晨 1:00 执行，按策略周期重新查询数据表并重算所有设备评分 |

***

## 六、API 接口

### 基础路径

所有接口前缀：`/risk_index`

### 接口列表

| 接口 | 方法 | 路径 | 说明 |
| :--- | :--- | :--- | :--- |
| 设备标签查询 | GET | `/risk_index/label/{guid}` | 查询设备风险标签，支持按启动ID或时间范围查询 |
| 威胁指数评分 | GET | `/risk_index/rating/{guid}` | 查询设备威胁指数分值和等级 |

### 接口详情

#### 1. 设备标签查询

```
GET /risk_index/label/{guid}?app_id={appId}&start_id={startId}
GET /risk_index/label/{guid}?app_id={appId}&start_time={startTime}&end_time={endTime}
```

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `guid` | String | 是 | 设备全局唯一ID（路径参数） |
| `app_id` | String | 是 | 应用ID |
| `start_id` | String | 否 | 启动ID（与时间范围二选一） |
| `start_time` | Date | 否 | 查询开始时间（格式：`yyyy-MM-dd HH:mm:ss`） |
| `end_time` | Date | 否 | 查询结束时间（格式：`yyyy-MM-dd HH:mm:ss`） |

#### 2. 威胁指数评分

```
GET /risk_index/rating/{guid}?app_id={appId}&rating_code={ratingCode}
```

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `guid` | String | 是 | 设备全局唯一ID（路径参数） |
| `app_id` | String | 是 | 应用ID |
| `rating_code` | String | 是 | 评分策略编码（如 `default`） |

### 响应格式

```json
{
  "code": 0,
  "msg": "请求成功",
  "data": { ... }
}
```

| code | 说明 |
| :--- | :--- |
| `0` | 请求成功 |
| `1` | 请求失败（内部错误） |
| `-1` | 未知错误 |
| `101` | 非法参数 |
| `102` | 未知的评分规则 |

***

## 七、项目结构

```
zenvis-service-risk/
├── src/main/java/com/coolxer/risk/
│   ├── Application.java                     # 启动类（EnableScheduling + EnableAsync）
│   ├── controller/
│   │   └── RiskIndexController.java         # 风险标签和评分查询接口
│   ├── service/
│   │   ├── RatingCalculateService.java      # 威胁指数计算接口
│   │   ├── RatingQueryService.java          # 威胁指数查询接口
│   │   ├── ProductService.java              # 风险事件分发接口
│   │   └── impl/
│   │       ├── RatingCalculateServiceImpl.java  # 评分计算实现（数据聚合、算分、矫正）
│   │       ├── RatingQueryServiceImpl.java     # 评分查询实现（Redis读取）
│   │       └── ProductServiceImpl.java         # 风险事件分发实现（构建RiskBaseLine→Kafka）
│   ├── model/
│   │   ├── FactMsg.java                     # Kafka消息模型（fact + agendas + serverTime）
│   │   ├── RatingData.java                  # 威胁指数数据（标签Map + 分区Key生成 + 合并逻辑）
│   │   ├── RatingRule.java                  # 评分策略模型（ScoreRule + GradeRule）
│   │   ├── RatingScore.java                 # 评分结果模型（score + grade）
│   │   ├── Risk.java                        # 风险事件抽象基类
│   │   ├── RiskBaseLine.java                # 基线风险事件模型
│   │   └── vo/
│   │       ├── Result.java                  # 统一响应模型
│   │       └── Label.java                   # 标签查询响应模型
│   ├── component/
│   │   ├── KafkaConsumerComponent.java      # Kafka批量消费者（异步处理Fact消息）
│   │   └── StartRunnerComponent.java        # 启动加载器（加载评分策略配置）
│   ├── configuration/
│   │   ├── ApplicationConfig.java           # 应用配置（策略路径、数据过期天数、策略加载）
│   │   ├── KafkaConsumerConfig.java         # Kafka消费者配置（批量监听）
│   │   ├── TopicDefine.java                 # Kafka Topic定义（risk_all）
│   │   ├── KeyHashPartitioner.java          # 自定义Kafka分区器（Key Hash取模）
│   │   ├── AsyncConfig.java                 # 异步线程池配置
│   │   └── JacksonConfig.java              # Jackson序列化/反序列化配置（蛇形命名）
│   ├── schedule/
│   │   └── ScheduleTask.java                # 定时任务（每日全量矫正评分）
│   ├── commons/
│   │   ├── constants/
│   │   │   └── ConstantUtil.java            # 常量定义（Redis Key格式、分隔符等）
│   │   ├── enums/
│   │   │   └── ResultCodeEnum.java          # 接口返回码枚举
│   │   └── exception/
│   │       └── ApiException.java           # 业务异常类
│   └── utils/
│       ├── JacksonUtil.java                 # JSON序列化/反序列化工具
│       ├── DateUtil.java                    # 日期工具（周期计算、日期列表生成）
│       └── CommonUtil.java                  # 通用工具（集合拆分）
├── src/main/resources/
│   ├── application.properties               # 应用配置
│   ├── logback.xml                          # 日志配置
│   └── rating_config/
│       └── rating_rule.json                 # 评分策略配置
├── src/test/java/
│   └── com/coolxer/risk/ApplicationTests.java
├── pom.xml                                  # Maven 配置
├── .gitignore
└── README.md
```

***

## 八、构建与运行

### 编译打包

```bash
mvn clean package -DskipTests
```

> 打包后生成 `target/application.jar`，配置文件和 `rating_config` 目录在打包时被排除，运行时通过外部配置注入。

### 运行

```bash
java -jar target/application.jar --spring.config.location=src/main/resources/application.properties
```

### Kafka 监听 Topic

服务启动后自动监听以下平台的 Fact Topic：

| 平台 | 监听 Topic |
| :--- | :--- |
| Android | `android_fact_start`, `android_fact_device`, `android_fact_net`, `android_fact_self-app`, `android_fact_file`, `android_fact_runtime`, `android_fact_app`, `android_fact_location`, `android_fact_inject`, `android_fact_event`, `android_fact_java`, `android_fact_native`, `android_fact_anr`, `android_fact_activity`, `android_fact_url`, `android_fact_user`, `android_fact_privacy` |
| H5 | `h5_fact_start`, `h5_fact_device`, `h5_fact_self_app`, `h5_fact_runtime`, `h5_fact_location`, `h5_fact_event`, `h5_fact_error`, `h5_fact_dev_tool`, `h5_fact_debug`, `h5_fact_user` |
| iOS | `ios_fact_start`, `ios_fact_device`, `ios_fact_net`, `ios_fact_self-app`, `ios_fact_file`, `ios_fact_runtime`, `ios_fact_app`, `ios_fact_location`, `ios_fact_inject`, `ios_fact_view`, `ios_fact_signal`, `ios_fact_objc`, `ios_fact_user` |
| Host | `host_fact_start`, `host_fact_device`, `host_fact_file`, `host_fact_net`, `host_fact_runtime`, `host_fact_event`, `host_fact_user` |
| WeChat | `wechat_fact_start`, `wechat_fact_device`, `wechat_fact_user` |

### 日志

日志输出到 `./logs/` 目录，按级别分离：

| 文件 | 说明 |
| :--- | :--- |
| `all.log` | 全量日志（按天滚动，保留 15 天） |
| `info.log` | INFO 级别日志 |
| `error.log` | ERROR 级别日志 |
