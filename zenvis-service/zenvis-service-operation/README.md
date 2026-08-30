# zenvis-service-operation

## 项目简介

zenvis-service-operation 是 ZenVis 微服务体系中的**运营事件服务**，基于 Spring Boot 3.2.0 构建。该服务通过消费 Kafka 中的设备探针上报数据（Fact），按平台和消息类型解析，将原始设备事件转化为标准运营事件（启动事件 / 页面事件），并重新发送至 Kafka `operation_all` Topic 供下游消费。

***

## 一、核心能力

| 能力 | 说明 |
| :--- | :--- |
| 多平台事件采集 | 批量消费 Kafka 中各平台的 Fact Topic，异步线程池处理 |
| 平台与消息类型路由 | 根据 Topic 名称自动识别平台（Android/iOS/H5/WeChat/Host）和消息类型（Start/Activity/App） |
| 启动事件转换 | 将 AndroidStart 原始数据转换为 OperationStart 运营事件（设备信息、应用信息、位置信息、网络信息） |
| 页面事件转换 | 将 AndroidActivity 原始数据转换为 OperationPage 运营事件（页面路径、页面名称、来源 Intent、位置信息） |
| 事件分发 | 将转换后的运营事件发送至 Kafka `operation_all` Topic（compact 策略） |
| REST 查询接口 | 提供设备标签查询 API（当前为占位实现） |

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
- Kafka（数据源 + 运营事件下游）
- Redis（预留，当前未实际使用）

***

## 四、配置说明

### 主要配置项

| 配置项 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `server.port` | `11004` | 服务端口 |
| `spring.kafka.consumer.bootstrap-servers` | - | Kafka 消费者连接地址 |
| `spring.kafka.consumer.group-id` | `operation-service` | Kafka 消费者组 ID |
| `spring.kafka.consumer.listen-topics` | - | 监听的 Fact Topic 列表（逗号分隔） |
| `spring.data.redis.host` | - | Redis 主机地址 |
| `spring.data.redis.port` | `6379` | Redis 端口 |
| `spring.data.redis.password` | - | Redis 密码 |
| `operation.rule.config.path` | - | 运营规则配置文件路径（预留，当前未启用加载） |
| `spring.kafka.producer.properties.partitioner.class` | `KeyHashPartitioner` | 自定义 Kafka 分区器 |
| `kafka.topic.partitions` | `1` | Kafka Topic 分区数 |
| `kafka.topic.replicas` | `1` | Kafka Topic 副本数 |
| `spring.task.execution.pool.core-size` | `10` | 异步线程池核心线程数 |
| `spring.task.execution.pool.max-size` | `20` | 异步线程池最大线程数 |
| `spring.task.execution.pool.queue-capacity` | `30` | 异步线程池队列容量 |
| `spring.jackson.date-format` | `yyyy-MM-dd HH:mm:ss` | 全局日期格式 |
| `spring.jackson.time-zone` | `Asia/Shanghai` | 全局时区 |

### 运营规则配置

运营规则配置文件（`operation_rule.json`）当前为空，`ApplicationConfig` 中的配置加载逻辑已预留但未启用，后续支持监听文件变化自动加载。

***

## 五、系统架构

### 数据处理流程

```
Kafka Fact Topics（android_fact_start, android_fact_activity）
        │
        ▼
KafkaConsumerComponent（批量消费 + 异步处理）
        │
        ├──→ 解析 Topic → PlatformEnum（平台） + MsgTypeEnum（消息类型）
        │
        ├──→ START 消息 → processStart()
        │         └──→ Android Start → OperationStart（启动事件）
        │
        └──→ ACTIVITY 消息 → processActivity()
                  └──→ Android Activity → OperationPage（页面事件）
                            │
                            ▼
                  ProductServiceImpl → sendOperation()
                            │
                            ▼
                  Kafka Topic: operation_all（compact）
```

### 消息类型路由

消费端通过 Topic 名称前缀和后缀自动路由：

| Topic 前缀 | 平台 | Topic 后缀 | 消息类型 | 处理方法 |
| :--- | :--- | :--- | :--- | :--- |
| `android` | Android | `_start` | 启动事件 | `processStart()` |
| `android` | Android | `_activity` | 页面事件 | `processActivity()` |
| `ios` | iOS | `_start` | 启动事件 | 预留 |
| `h5` | H5 | - | - | 预留 |
| `wechat` | WeChat | - | - | 预留 |
| `host` | Host | - | - | 预留 |

> 当前仅实现了 Android 平台的 Start 和 Activity 事件处理，其他平台为预留接口。

### Kafka Topic 定义

| Topic | 用途 | 特性 |
| :--- | :--- | :--- |
| `operation_all` | 运营事件下游 Topic | compact 策略（相同 Key 保留最新） |

***

## 六、API 接口

### 基础路径

所有接口前缀：`/operation_index`

### 接口列表

| 接口 | 方法 | 路径 | 说明 |
| :--- | :--- | :--- | :--- |
| 设备标签查询 | GET | `/operation_index/label/{guid}` | 查询设备运营标签（当前为占位实现，返回 null） |

### 接口详情

#### 1. 设备标签查询

```
GET /operation_index/label/{guid}?app_id={appId}&start_id={startId}
GET /operation_index/label/{guid}?app_id={appId}&start_time={startTime}&end_time={endTime}
```

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `guid` | String | 是 | 设备全局唯一ID（路径参数） |
| `app_id` | String | 是 | 应用ID |
| `start_id` | String | 否 | 启动ID（与时间范围二选一） |
| `start_time` | Date | 否 | 查询开始时间（格式：`yyyy-MM-dd HH:mm:ss`） |
| `end_time` | Date | 否 | 查询结束时间（格式：`yyyy-MM-dd HH:mm:ss`） |

### 响应格式

```json
{
  "code": 0,
  "msg": "请求成功",
  "data": null
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
zenvis-service-operation/
├── src/main/java/com/coolxer/operation/
│   ├── Application.java                     # 启动类（EnableScheduling + EnableAsync）
│   ├── controller/
│   │   └── OperationIndexController.java    # 设备标签查询接口
│   ├── service/
│   │   ├── ProductService.java              # 运营事件分发接口
│   │   └── impl/
│   │       └── ProductServiceImpl.java     # 运营事件分发实现（事件转换→Kafka）
│   ├── model/
│   │   ├── FactMsg.java                    # 通用Kafka消息模型（fact + agendas + serverTime）
│   │   ├── AndroidStart.java              # Android启动事件模型（Common设备信息 + Config配置）
│   │   ├── AndroidActivity.java           # Android页面事件模型（Common设备信息 + Activity详情）
│   │   ├── Operation.java                 # 运营事件抽象基类（id + operationType）
│   │   ├── OperationStart.java            # 启动运营事件模型（设备/应用/位置/网络信息）
│   │   ├── OperationPage.java             # 页面运营事件模型（页面路径/名称/来源/位置信息）
│   │   └── vo/
│   │       ├── Result.java                # 统一响应模型
│   │       └── Label.java                 # 标签查询响应模型
│   ├── component/
│   │   ├── KafkaConsumerComponent.java     # Kafka批量消费者（平台+消息类型路由）
│   │   └── StartRunnerComponent.java       # 启动加载器（预留配置加载）
│   ├── configuration/
│   │   ├── ApplicationConfig.java          # 应用配置（运营规则路径，加载逻辑预留）
│   │   ├── KafkaConsumerConfig.java         # Kafka消费者配置（批量监听）
│   │   ├── TopicDefine.java                 # Kafka Topic定义（operation_all）
│   │   ├── KeyHashPartitioner.java         # 自定义Kafka分区器（Key Hash取模）
│   │   ├── AsyncConfig.java                 # 异步线程池配置
│   │   └── JacksonConfig.java             # Jackson序列化/反序列化配置（蛇形命名）
│   ├── commons/
│   │   ├── constants/
│   │   │   └── ConstantUtil.java           # 常量定义（Redis Key格式、分隔符等）
│   │   ├── enums/
│   │   │   ├── PlatformEnum.java           # 平台枚举（ANDROID/IOS/H5/WECHAT/HOST）
│   │   │   ├── MsgTypeEnum.java            # 消息类型枚举（START/ACTIVITY/APP）
│   │   │   └── ResultCodeEnum.java         # 接口返回码枚举
│   │   └── exception/
│   │       └── ApiException.java          # 业务异常类
│   └── utils/
│       ├── JacksonUtil.java                # JSON序列化/反序列化工具
│       ├── DateUtil.java                   # 日期工具（周期计算、日期列表生成）
│       └── CommonUtil.java                 # 通用工具（集合拆分）
├── src/main/resources/
│   ├── application.properties              # 应用配置
│   ├── logback.xml                         # 日志配置
│   └── operation_config/
│       └── operation_rule.json             # 运营规则配置（当前为空）
├── src/test/java/
│   └── com/coolxer/operation/ApplicationTests.java
├── pom.xml                                 # Maven 配置
├── .gitignore
└── README.md
```

***

## 八、构建与运行

### 编译打包

```bash
mvn clean package -DskipTests
```

> 打包后生成 `target/application.jar`，配置文件在打包时被排除，运行时通过外部配置注入。

### 运行

```bash
java -jar target/application.jar --spring.config.location=src/main/resources/application.properties
```

### Kafka 监听 Topic

服务启动后自动监听以下 Fact Topic：

| 平台 | 监听 Topic | 消息类型 |
| :--- | :--- | :--- |
| Android | `android_fact_start` | 启动事件 |
| Android | `android_fact_activity` | 页面事件 |

### 数据转换说明

#### 启动事件（AndroidStart → OperationStart）

| 源字段 | 目标字段 | 说明 |
| :--- | :--- | :--- |
| `common.startId` | `id` | 启动ID |
| `common.userId` | `userId` | 用户ID |
| `common.guid` | `deviceId` | 设备ID |
| `common.platform` | `deviceOs` | 设备系统 |
| `common.model` | `deviceModel` | 设备型号 |
| `common.appId` | `appId` | 应用ID |
| `common.appName` | `appName` | 应用名称 |
| `common.appPackage` | `packageName` | 应用包名 |
| `common.longitude/latitude` | `longitude/latitude` | 经纬度 |
| `common.country/province/city/thoroughfare` | `country/province/city/county` | 地理位置 |
| `common.netType/lanIp/wanIp` | `netType/lanIp/wanIp` | 网络信息 |
| `common.clientTime` | `eventTime` | 事件时间 |
| - | `operationType` | 固定值 `operation_start_event` |

#### 页面事件（AndroidActivity → OperationPage）

| 源字段 | 目标字段 | 说明 |
| :--- | :--- | :--- |
| `common.startId` + 随机串 | `id` | 唯一ID |
| `common.startId` | `startId` | 启动ID |
| `common.userId` | `userId` | 用户ID |
| `activity.className` | `pagePath` | 页面路径 |
| `activity.title` | `pageName` | 页面名称 |
| `activity.intent` | `referrer` | 来源 Intent |
| `common.longitude/latitude` | `longitude/latitude` | 经纬度 |
| `common.country/province/city/county` | `country/province/city/county` | 地理位置 |
| `common.netType/lanIp/wanIp` | `netType/lanIp/wanIp` | 网络信息 |
| `common.clientTime` | `eventTime` | 事件时间 |
| - | `operationType` | 固定值 `operation_page_event` |

### 日志

日志输出到 `./logs/` 目录，按级别分离：

| 文件 | 说明 |
| :--- | :--- |
| `all.log` | 全量日志（按天滚动，保留 15 天） |
| `info.log` | INFO 级别日志 |
| `error.log` | ERROR 级别日志 |
