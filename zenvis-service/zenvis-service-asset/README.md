# zenvis-service-asset

## 项目简介

zenvis-service-asset 是 ZenVis 微服务体系中的**设备资产管理服务**，基于 Spring Boot 3.2.0 构建。该服务通过消费 Kafka 中多平台（Android / iOS / H5 / Host / WeChat）的设备探针上报数据（Fact），完成两大核心职能：一是将原始设备数据转化为标准资产对象（探针资产 / 移动端设备资产 / 应用资产）并分发至 Kafka `asset_all` Topic；二是基于可配置的设备指纹规则，通过多维度设备标识因子聚合计算设备指纹（deviceId），实现跨会话设备归因。

***

## 一、核心能力

| 能力 | 说明 |
| :--- | :--- |
| 多平台事件采集 | 批量消费 Kafka 中 5 个平台的 Start / Device / App Fact Topic，异步线程池处理 |
| 平台与消息类型路由 | 根据 Topic 名称自动识别平台和消息类型（Start / Device / App） |
| 探针资产管理 | 将 AndroidStart 数据转化为 AssetProbe 探针资产对象 |
| 移动端设备资产管理 | 将 AndroidDevice 数据转化为 AssetMobileDevice 设备资产对象（含品牌、型号、系统、网络等） |
| 应用资产管理 | 将 AndroidApp 安装列表逐个转化为 AssetApp 应用资产对象（含包名、版本、证书等） |
| 资产事件分发 | 将转化后的资产对象发送至 Kafka `asset_all` Topic（compact 策略） |
| 设备指纹计算 | 基于可配置规则（keyList + 相似度阈值），通过 Redis ZSet 聚合多维度标识因子，生成唯一设备指纹 |
| 设备指纹 ID 预生成 | 启动时异步线程持续生成 UUID 放入队列备用 |
| GUID 与指纹互查 | 支持 guid → deviceId 和 deviceId → guid[] 双向查询 |
| 设备信息与应用列表存储 | 将设备详情和应用列表 JSON 存储至 Redis 供查询 |
| REST 查询接口 | 提供设备信息、应用信息、设备指纹、GUID 反查 4 个 API |

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
- Kafka（数据源 + 资产事件下游）
- Redis（设备指纹存储 + 设备信息缓存）

***

## 四、配置说明

### 主要配置项

| 配置项 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `server.port` | `11003` | 服务端口 |
| `spring.kafka.consumer.bootstrap-servers` | - | Kafka 消费者连接地址 |
| `spring.kafka.consumer.group-id` | `asset-service` | Kafka 消费者组 ID |
| `spring.kafka.consumer.listen-topics` | - | 监听的 Fact Topic 列表（逗号分隔） |
| `spring.data.redis.database` | `1` | Redis 数据库索引 |
| `spring.data.redis.host` | - | Redis 主机地址 |
| `spring.data.redis.port` | `6379` | Redis 端口 |
| `spring.data.redis.password` | - | Redis 密码 |
| `device.id.rule.config.path` | - | 设备指纹规则配置文件路径 |
| `spring.kafka.producer.properties.partitioner.class` | `KeyHashPartitioner` | 自定义 Kafka 分区器 |
| `kafka.topic.partitions` | `1` | Kafka Topic 分区数 |
| `kafka.topic.replicas` | `1` | Kafka Topic 副本数 |
| `spring.task.execution.pool.core-size` | `10` | 异步线程池核心线程数 |
| `spring.task.execution.pool.max-size` | `20` | 异步线程池最大线程数 |
| `spring.task.execution.pool.queue-capacity` | `30` | 异步线程池队列容量 |
| `spring.jackson.date-format` | `yyyy-MM-dd HH:mm:ss` | 全局日期格式 |
| `spring.jackson.time-zone` | `Asia/Shanghai` | 全局时区 |

### 设备指纹规则配置

设备指纹规则通过 JSON 文件配置（`device_id_rule.json`），启动时由 `ApplicationConfig` 加载到内存。配置按平台定义参与计算的标识因子和相似度阈值：

```json
{
  "android": {
    "key_list": ["macWlan0", "macWlan1", "imei", "serial", "androidId", "oaid", "bootId", "guid", ...],
    "rate": 100
  },
  "ios": {
    "key_list": ["cfuuid", "idfa", "idfv", "guid"],
    "rate": 100
  },
  "h5": {
    "key_list": ["guid"],
    "rate": 100
  }
}
```

| 字段 | 说明 |
| :--- | :--- |
| `key_list` | 参与指纹计算的标识因子列表（通过反射从 Uuid 对象获取属性值） |
| `rate` | 相似度阈值（百分比），用于判断设备是否为同一台设备 |

***

## 五、系统架构

### 数据处理流程

```
Kafka Fact Topics（5 平台 × Start/Device/App）
        │
        ▼
KafkaConsumerComponent（批量消费 + 异步处理）
        │
        ├──→ START 消息 → processStart()
        │         └──→ AndroidStart → AssetProbe（探针资产）→ Kafka asset_all
        │
        ├──→ DEVICE 消息 → processDevice()
        │         ├──→ AndroidDevice → AssetMobileDevice（设备资产）→ Kafka asset_all
        │         └──→ 各平台 Device → toDeviceModel() → DeviceModel
        │                   └──→ DeviceIdService.updateDevice()
        │                             ├──→ Redis: device-info:{guid}（设备详情）
        │                             ├──→ Redis ZSet 聚合 → 判断是否已有指纹
        │                             ├──→ 已有 → 返回现有 deviceId
        │                             └──→ 新设备 → 取预生成 UUID → 更新 ZSet + 双向映射
        │
        └──→ APP 消息 → processApp()
                  ├──→ AndroidApp → AssetApp[]（应用资产）→ Kafka asset_all
                  └──→ 各平台 App → toAppModel() → AppModel
                            └──→ DeviceIdService.updateDeviceApp()
                                      └──→ Redis: device-app:{guid}（应用列表）
```

### 设备指纹计算逻辑

1. 从 `DeviceIdRule` 获取平台的 `keyList` 和 `rate`
2. 通过反射从设备 `Uuid` 对象中提取各标识因子的值
3. 为每个非空因子生成 Redis Key：`field-{platform}-{attributeName}:{attributeValue}`
4. 使用 ZSet 聚合查询，判断是否已有设备匹配相同因子
5. 若匹配成功，返回已有 deviceId；否则从预生成队列取新 UUID，更新所有因子 ZSet 和双向映射

### Redis 数据结构

| 数据类型 | Key 格式 | 说明 |
| :--- | :--- | :--- |
| 设备信息（String） | `device-info:{guid}` | 设备详情 JSON |
| 设备应用列表（String） | `device-app:{guid}` | 已安装应用列表 JSON |
| 指纹因子索引（ZSet） | `field-{platform}-{attributeName}:{attributeValue}` | member=deviceId，score=匹配权重 |
| 指纹映射（String） | `device-id:{guid}` | guid → deviceId |
| GUID 反查（Set） | `device-guid:{deviceId}` | deviceId → guid[] |
| ID 预生成队列（内存） | `DEVICE_ID_QUEUE` | LinkedBlockingQueue，容量 10000 |

### Kafka Topic 定义

| Topic | 用途 | 特性 |
| :--- | :--- | :--- |
| `asset_all` | 资产事件下游 Topic | compact 策略（相同 Key 保留最新） |

***

## 六、API 接口

### 基础路径

所有接口前缀：`/device`

### 接口列表

| 接口 | 方法 | 路径 | 说明 |
| :--- | :--- | :--- | :--- |
| 设备信息查询 | GET | `/device/info/{guid}` | 查询设备详细信息（JSON） |
| 设备应用查询 | GET | `/device/app/{guid}` | 查询设备已安装应用列表（JSON） |
| 设备指纹查询 | GET | `/device/id/{guid}` | 根据 GUID 查询设备指纹（deviceId） |
| GUID 反查 | GET | `/device/guid/{id}` | 根据设备指纹反查关联的 GUID 列表 |

### 接口详情

#### 1. 设备信息查询

```
GET /device/info/{guid}
```

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `guid` | String | 是 | 设备全局唯一ID（路径参数） |

#### 2. 设备应用查询

```
GET /device/app/{guid}
```

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `guid` | String | 是 | 设备全局唯一ID（路径参数） |

#### 3. 设备指纹查询

```
GET /device/id/{guid}
```

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `guid` | String | 是 | 设备全局唯一ID（路径参数） |

#### 4. GUID 反查

```
GET /device/guid/{id}
```

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `id` | String | 是 | 设备指纹 deviceId（路径参数） |

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

***

## 七、资产枚举体系

服务内置完整的资产分类枚举，用于资产对象标准化：

| 枚举 | 值 | 说明 |
| :--- | :--- | :--- |
| Asset | HOST / MOBILE / PC / IOT / PROBE / APP / SERVICE / API / LOG / FILE | 资产类型 |
| AssetSource | MANUAL / AGENT / PROBE / THIRD_PARTY / OTHER | 资产来源 |
| AssetType | BUSINESS / SUPPORT | 资产分类（业务类/支撑类） |
| AssetOwner | ENTERPRISE / CUSTOMER | 资产属主（企业/终端客户） |
| AssetStatus | ONLINE / DISABLED / OFFLINE / DELETED | 资产状态 |
| AssetLevel | AUXILIARY / GENERAL / MINOR / IMPORTANT / CRITICAL | 资产等级 |
| AssetRiskLevel | NONE / LOW / MEDIUM / HIGH / EXTREME | 资产风险等级 |
| AssetRuleAction | MERGE / MARK | 资产规则动作（合并/打标记） |
| AssetRuleStatus | INACTIVE / ACTIVE / EXPIRED / UNAVAILABLE | 资产规则状态 |

***

## 八、项目结构

```
zenvis-service-asset/
├── src/main/java/com/coolxer/asset/
│   ├── Application.java                     # 启动类（EnableAsync）
│   ├── controller/
│   │   └── DeviceIdController.java           # 设备信息/应用/指纹/GUID 查询接口
│   ├── service/
│   │   ├── DeviceIdService.java             # 设备指纹服务接口
│   │   ├── ProductService.java              # 资产事件分发接口
│   │   └── impl/
│   │       ├── DeviceIdServiceImpl.java      # 指纹服务实现（Redis ZSet 聚合 + 双向映射）
│   │       └── ProductServiceImpl.java        # 资产事件分发实现（→ asset_all Topic）
│   ├── model/
│   │   ├── Asset.java                       # 资产抽象基类（id/type/source/owner/status/level/risk）
│   │   ├── AssetProbe.java                  # 探针资产模型
│   │   ├── AssetMobileDevice.java            # 移动端设备资产模型
│   │   ├── AssetApp.java                    # 应用资产模型
│   │   ├── DeviceModel.java                 # 设备指纹数据模型（rate/guid/unionKeys/deviceInfo）
│   │   ├── AppModel.java                    # 应用数据模型
│   │   ├── DeviceIdRule.java               # 设备指纹规则（各平台 keyList + rate）
│   │   ├── AndroidStart.java               # Android 启动事件模型
│   │   ├── AndroidDevice.java              # Android 设备信息模型（含 toDeviceModel 转换）
│   │   ├── AndroidApp.java                 # Android 应用列表模型（含 toAppModel 转换）
│   │   ├── IosDevice.java                   # iOS 设备信息模型
│   │   ├── IosApp.java                     # iOS 应用列表模型
│   │   ├── H5Device.java                    # H5 设备信息模型
│   │   ├── WechatDevice.java               # 微信小程序设备信息模型
│   │   ├── HostDevice.java                 # Host 设备信息模型
│   │   └── vo/
│   │       └── Result.java                 # 统一响应模型
│   ├── component/
│   │   ├── KafkaConsumerComponent.java      # Kafka 批量消费者（平台+消息类型路由）
│   │   └── StartRunnerComponent.java        # 启动加载器（加载指纹规则 + 启动 ID 预生成）
│   ├── configuration/
│   │   ├── ApplicationConfig.java            # 应用配置（指纹规则加载）
│   │   ├── KafkaConsumerConfig.java          # Kafka 消费者配置（批量监听）
│   │   ├── TopicDefine.java                 # Kafka Topic 定义（asset_all）
│   │   ├── KeyHashPartitioner.java          # 自定义 Kafka 分区器
│   │   ├── AsyncConfig.java                 # 异步线程池配置
│   │   └── JacksonConfig.java              # Jackson 序列化/反序列化配置（蛇形命名）
│   ├── commons/
│   │   ├── constants/
│   │   │   └── ConstantUtil.java            # 常量定义（Redis Key 格式、ID 队列）
│   │   ├── enums/
│   │   │   ├── asset/
│   │   │   │   ├── Asset.java               # 资产类型枚举
│   │   │   │   ├── AssetSource.java         # 资产来源枚举
│   │   │   │   ├── AssetType.java           # 资产分类枚举
│   │   │   │   ├── AssetOwner.java          # 资产属主枚举
│   │   │   │   ├── AssetStatus.java         # 资产状态枚举
│   │   │   │   ├── AssetLevel.java          # 资产等级枚举
│   │   │   │   ├── AssetRiskLevel.java      # 资产风险等级枚举
│   │   │   │   ├── AssetRuleAction.java     # 资产规则动作枚举
│   │   │   │   └── AssetRuleStatus.java     # 资产规则状态枚举
│   │   │   ├── PlatformEnum.java           # 平台枚举（ANDROID/IOS/H5/WECHAT/HOST）
│   │   │   ├── MsgTypeEnum.java            # 消息类型枚举（START/DEVICE/APP）
│   │   │   └── ResultCodeEnum.java         # 接口返回码枚举
│   │   └── exception/
│   │       └── ApiException.java           # 业务异常类
│   └── utils/
│       ├── JacksonUtil.java                # JSON 序列化/反序列化工具
│       ├── CommonUtil.java                 # 通用工具（反射获取属性值）
│       └── DateUtil.java                   # 日期工具
├── src/main/resources/
│   ├── application.properties              # 应用配置
│   ├── logback.xml                         # 日志配置
│   └── deviceid_config/
│       └── device_id_rule.json             # 设备指纹规则配置
├── src/test/java/
│   └── com/coolxer/asset/ApplicationTests.java
├── pom.xml                                 # Maven 配置
└── README.md
```

***

## 九、构建与运行

### 编译打包

```bash
mvn clean package -DskipTests
```

> 打包后生成 `target/application.jar`，配置文件和 `deviceid_config` 目录在打包时被排除，运行时通过外部配置注入。

### 运行

```bash
java -jar target/application.jar --spring.config.location=src/main/resources/application.properties
```

### Kafka 监听 Topic

服务启动后自动监听以下平台的 Fact Topic：

| 平台 | 监听 Topic | 消息类型 |
| :--- | :--- | :--- |
| Android | `android_fact_start` | 启动事件 |
| Android | `android_fact_device` | 设备信息 |
| Android | `android_fact_app` | 应用列表 |
| iOS | `ios_fact_device` | 设备信息 |
| iOS | `ios_fact_app` | 应用列表 |
| H5 | `h5_fact_device` | 设备信息 |
| Host | `host_fact_device` | 设备信息 |
| WeChat | `wechat_fact_device` | 设备信息 |

### 消息类型路由

消费端通过 Topic 名称前缀和后缀自动路由：

| Topic 前缀 | 平台 | Topic 后缀 | 消息类型 | 处理方法 |
| :--- | :--- | :--- | :--- | :--- |
| `android` | Android | `_start` | 启动事件 | `processStart()` → 探针资产 |
| `android` | Android | `_device` | 设备信息 | `processDevice()` → 设备资产 + 指纹 |
| `android` | Android | `_app` | 应用列表 | `processApp()` → 应用资产 + 应用存储 |
| `ios` | iOS | `_device` | 设备信息 | `processDevice()` → 指纹 |
| `ios` | iOS | `_app` | 应用列表 | `processApp()` → 应用存储 |
| `h5` | H5 | `_device` | 设备信息 | `processDevice()` → 指纹 |
| `host` | Host | `_device` | 设备信息 | `processDevice()` → 指纹 |
| `wechat` | WeChat | `_device` | 设备信息 | `processDevice()` → 指纹 |

> 资产事件分发（AssetProbe / AssetMobileDevice / AssetApp）当前仅实现 Android 平台，其他平台预留接口。

### 各平台指纹因子

| 平台 | 标识因子 |
| :--- | :--- |
| Android | macWlan0, macWlan1, macP2p0, imei, imsi, iccid, serial, fileUidData, fileUidSystem, fileUidCache, fileUidVendor, androidId, widevineId, oaid, bootId, guid |
| iOS | cfuuid, idfa, idfv, guid（IDFA 全零时跳过） |
| H5 | guid |
| Host | guid |
| WeChat | guid |

### 日志

日志输出到 `./logs/` 目录，按级别分离：

| 文件 | 说明 |
| :--- | :--- |
| `all.log` | 全量日志（按天滚动，保留 15 天） |
| `info.log` | INFO 级别日志 |
| `error.log` | ERROR 级别日志 |
