# 数据资产管理
---

## 基本信息

- **插件名称**: 资产管理
- **包名**: com.coolxer.plugin.asset
- **版本**: 1.3.1
- **作者**: CoolXer
- **描述**: 通过自动化手段对接探针采集的资产数据，并对这些资产进行精细化管理。系统涵盖了硬件类资产（如服务器主机、移动端设备、PC机、IOT设备）、软件类资产（如探针SDK、APP应用、系统服务、API接口）以及数据类资产（如日志、文件）。通过资产归类、标记、上下线管理、重要性评定和风险等级评定等功能，系统能够为企业提供全面、清晰且有序的数据资产视图，从而提升数据资产的利用效率和管理效能。
---


## 功能介绍
---

### 运行依赖

插件复用 Zenvis 平台内置的 Meta、EntityCore、低代码页面和 HTML_PAGE 能力，
不再依赖单独部署的“数据资产分析服务”。安装前需要配置可用的 ClickHouse 数据源；
资产数据源的生产接入协议、鉴权和样例报文尚未明确；插件内置的自动任务仅用于生成模拟资产，不能作为生产数据接入方案。

---

### 元数据配置

# 资产数据模型说明文档

本文档基于提供的建表元数据，定义并说明了 ClickHouse 中 10 张核心资产表的字段含义、数据类型、支持的操作符及默认展示行为，便于后续开发、运维与数据分析工作快速查阅。

---

#### 1. 资产总览

| 实体(entity) | 中文名 | 表名 | 数据源 |
| --- | --- | --- | --- |
| asset_host | 主机资产 | asset_host | clickhouse |
| asset_pc | PC终端资产 | asset_pc | clickhouse |
| asset_iot | IoT设备资产 | asset_iot | clickhouse |
| asset_mobile | 移动端设备资产 | asset_mobile | clickhouse |
| asset_probe | 数据探针SDK资产 | asset_probe | clickhouse |
| asset_app | APP应用程序资产 | asset_app | clickhouse |
| asset_service | 系统服务资产 | asset_service | clickhouse |
| asset_api | RESTful API接口资产 | asset_api | clickhouse |
| asset_log | 日志资产 | asset_log | clickhouse |
| asset_file | 文件资产 | asset_file | clickhouse |

> 所有表均包含一组“公共字段”（见下一节），再叠加各实体特有字段。

---

#### 2. 公共字段说明

> 以下字段在 **所有资产表** 中含义一致，便于统一查询与权限控制。

| 字段名 | 类型 | 含义 | 支持操作符 | 默认展示 |
| --- | --- | --- | --- | --- |
| id | String | 来源系统业务标识 | 文本类操作符* | ✔ |
| source | String | 数据来源 | 文本类操作符* | ✔ |
| type | String | 资产类型 | 文本类操作符* | ✔ |
| owner | String | 资产归属 | 文本类操作符* | ✔ |
| status | String | 资产状态 | 文本类操作符* | ✔ |
| label | Array(String) | 业务标签 | 文本类操作符* | ✔ |
| access | UInt8 | 是否可访问（0/1） | 数值类操作符* | ✔ |
| level | String | 重要等级 | 文本类操作符* | ✔ |
| risk | String | 风险等级 | 文本类操作符* | ✔ |
| risk_info | String | 风险描述 | 文本类操作符* | ✖ |
| info | json | 扩展信息（JSON） | 文本类操作符* | ✖ |
| update_time | DateTime64(3) | 来源记录更新时间 | 日期类操作符* | ✔ |
| insert_time | DateTime64(3) | 来源记录入库时间 | 日期类操作符* | ✖ |

> 文本类操作符：`equal / notequal / isnull / isnotnull / in / match`；数值类在
> `equal / notequal / isnull / isnotnull / in` 基础上增加大小比较和 `between`；
> 日期类操作符：`equal / notequal / isnull / isnotnull / greatthan /
> greatequalthan / lessthan / lessequalthan / between`。

---

#### 3. 实体特有字段

##### 3.1 主机资产表 `asset_host`

| 字段名 | 类型 | 含义 | 默认展示 |
| --- | --- | --- | --- |
| area_code | String | 行政区域代码 | ✖ |
| country / province / city / county | String | 国家/省/市/区县 | ✔ / ✔ / ✔ / ✖ |
| net_type | String | 网络类型 | ✔ |
| lan_ip / wan_ip | IPv4 | 内网/外网 IPv4 地址 | ✔ |
| room | String | 所在机房 | ✔ |
| cabinet_no / position_no | String | 机柜号/机柜内位置 | ✖ |
| manufacturer / model | String | 硬件厂商/型号 | ✔ |
| architecture | String | CPU 架构 | ✔ |
| system_name / system_version | String | 操作系统名称/版本 | ✔ |
| cpu_model | String | CPU 型号 | ✔ |
| cpu_cores | UInt32 | CPU 核数 | ✔ |
| memory_size | UInt32 | 内存大小(MB) | ✔ |
| disk_size | UInt32 | 磁盘大小(GB) | ✔ |

##### 3.2 PC终端资产表 `asset_pc`

除公共字段外，额外包含：

| 字段名 | 类型 | 含义 |
| --- | --- | --- |
| gpu_model / gpu_brand / gpu_memory_size / gpu_memory_type |  | GPU 相关信息 |
| monitor_brand / monitor_model / monitor_resolution / monitor_interface |  | 显示器相关信息 |
| 其余字段与主机资产一致（如 cpu_cores、memory_size 等） |

##### 3.3 IoT设备资产表 `asset_iot`

| 字段名 | 类型 | 含义 |
| --- | --- | --- |
| device_name | String | 设备名称/别名 |
| device_type | String | IoT设备分类（传感器、摄像头…） |
| serial_number | String | 设备序列号 |
| firmware_version | String | 当前固件版本 |
| firmware_update_time | DateTime64(3) | 最近一次固件升级时间 |
| power_type | String | 供电方式 |
| battery_level | UInt8 | 电池电量(0-100) |
| sensor_info | String | 传感器列表及状态(json) |
| communication_protocol | String | 通信协议(MQTT/CoAP/…) |

##### 3.4 移动端设备资产表 `asset_mobile`

| 字段名 | 类型 | 含义 |
| --- | --- | --- |
| brand / model / manufacturer | String | 设备品牌/型号/制造商 |
| system_name / system_version | String | OS 名称/版本 |
| android_id / imei / imsi | String | Android 设备唯一标识/IMEI/IMSI |
| wifi_mac / bluetooth_mac / network_mac | String | 各类 MAC 地址 |
| screen_resolution | String | 屏幕分辨率 |
| carrier_type | String | 运营商类型 |
| 更多字段如 gyroscope_info、device_fingerprint 等用于刻画设备指纹 |

##### 3.5 数据探针SDK资产表 `asset_probe`

| 字段名 | 类型 | 含义 |
| --- | --- | --- |
| probe_name / probe_version | String | 探针名称/版本 |
| probe_type | String | 探针类型(移动端/后端/前端) |
| language / framework | String | 开发语言/框架 |
| data_collection_types | String | 采集数据类型(逗号拼接) |
| encryption_method / authentication_method / data_transmission_protocol | String | 加密、认证、传输协议 |
| file_md5 / certificate_md5 | String | 安装包及证书 MD5 |

##### 3.6 APP应用程序资产表 `asset_app`

| 字段名 | 类型 | 含义 |
| --- | --- | --- |
| app_name / app_version | String | 应用名称/版本 |
| app_type | String | 应用分类(原生/H5/小程序) |
| platform | String | 目标平台(iOS/Android/Web) |
| package_name | String | 包名/Bundle ID |
| developer | String | 开发者 |
| publish_time | DateTime64(3) | 发布时间 |
| min_os_version / target_os_version | String | 最低/推荐系统版本 |
| permissions / dependencies | String | 权限清单、依赖库(逗号拼接) |
| file_md5 / certificate_md5 | String | 安装包及证书 MD5 |

##### 3.7 系统服务资产表 `asset_service`

| 字段名 | 类型 | 含义 |
| --- | --- | --- |
| service_name / service_version | String | 服务名称/版本 |
| service_type | String | 服务分类(微服务/单体) |
| runtime_environment | String | 运行环境(JVM/容器…) |
| deployment_type | String | 部署方式(K8s/裸机) |
| port | UInt16 | 监听端口 |
| process_name / process_id | String | 进程名/ID |
| dependencies | String | 依赖服务(逗号拼接) |
| resource_usage | String | 资源使用(json) |

##### 3.8 RESTful API接口资产表 `asset_api`

| 字段名 | 类型 | 含义 |
| --- | --- | --- |
| api_name / api_version / api_path | String | 接口名称/版本/URL 路径 |
| http_method | String | HTTP 方法(GET/POST/PUT/DELETE) |
| content_type | String | 请求/响应 Content-Type |
| authentication_type | String | 认证方式(OAuth/JWT/…) |
| rate_limit | UInt32 | 每秒最大请求次数(QPS) |
| is_deprecated | UInt8 | 是否已废弃(0/1) |
| service_id | String | 所属服务系统ID |
| documentation_url | String | 在线文档地址 |

##### 3.9 日志资产表 `asset_log`

| 字段名 | 类型 | 含义 |
| --- | --- | --- |
| log_name / log_path | String | 日志文件名称/路径 |
| log_type | String | 日志业务类型 |
| log_format | String | 日志格式(JSON/CSV/…) |
| log_time | DateTime64(3) | 日志产生时间 |
| log_level | String | 日志级别(INFO/ERROR/…) |
| process | String | 产生日志的进程或服务 |
| log_message | String | 日志正文 |

##### 3.10 文件资产表 `asset_file`

| 字段名 | 类型 | 含义 |
| --- | --- | --- |
| file_name / file_path | String | 文件名称/存储路径 |
| file_type / file_format | String | 业务类型/文件后缀 |
| file_size | Int64 | 文件大小(字节) |
| creation_time / modification_time | DateTime64(3) | 文件创建/最后修改时间 |
| source_system | String | 来源业务系统 |
| file_owner | String | 文件所有者 |
| permissions | String | 权限/ACL |
| is_encrypted / is_compressed | UInt8 | 是否已加密/压缩(0/1) |
| file_hash | String | 文件哈希(MD5/SHA256) |

---

### 数据推送服务

发布包内置“资产模拟数据生成与入库”测试任务，默认每 5 秒随机生成一条覆盖十类
资产的完整样例并写入 ClickHouse。数据库连接和生成间隔使用环境变量配置；不需要时
应在“数据推送服务”页面停用任务。该任务不经过 Kafka，仅用于开发、演示和页面验收。

接入真实数据源时仍应按“两阶段”方式配置：接收/拉取服务只负责写入 Kafka，独立
消费服务再按实体分流写入 ClickHouse，同时配置重试与死信队列。字段映射、唯一键、
环境变量和时间语义见《契约矩阵与运维》。
---

### API接口

资产规则 API 由本插件动态提供，运行时前缀为
`/api/v1/plugin/com.coolxer.plugin.asset`：

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| POST | `/rule/add` | 新增资产规则 |
| DELETE | `/rule/{id}` | 删除规则 |
| DELETE | `/rule/bulk/{ids}` | 批量删除规则 |
| POST | `/rule/{id}/update` | 更新规则 |
| POST | `/rule/{ids}/bulk_update` | 批量更新规则 |
| GET | `/rule/list` | 分页查询规则 |
| GET | `/rule/asset/list` | 查询规则表单使用的资产类型选项 |
| GET | `/rule/{id}/view` | 查询规则详情 |
| POST | `/rule/{id}/activate` | 保留接口，当前版本不切换状态 |
| POST | `/rule/{id}/deactivate` | 保留接口，当前版本不切换状态 |
| GET | `/rule/action/list` | 规则动作选项 |
| GET | `/rule/status/list` | 规则状态选项 |

API 源码位于仓库根目录的 `extend-asset/`，发布包只包含
`03_api/extend-asset-1.3.1.jar`。`V001__create_asset_rule.sql` 接管既有
`t_asset_rule`，安装升级不搬迁历史记录，卸载插件也不会删除该表。

原核心路径 `/api/v1/asset/**` 已移除。资产查询、查看、更新和删除统一使用平台
`/api/v1/entity/{entityCode}/**` 接口；聚合调查使用
`/api/v1/entity/value-statistics/query`。实体数据操作的主键是平台 UUID
`zenvis_id`，业务资产编号仍为字段 `id`，两者不可混用。

---

### 可视化配置

插件提供资产管理低代码应用、10 个实体详情页、通用治理属性维护页、资产 IP 命中调查页，以及
“资产治理态势”HTML 看板。看板通过平台 EntityCore 接口实时读取总量、当日新增、
在线和高风险分布，并包含加载、空数据、错误与手动刷新状态。

---

### 菜单项

插件提供以下菜单项：

1. 设备指纹策略（配置菜单）
2. 资管可视化配置（配置菜单）
3. 资产管理（应用app）
4. 资产 IP 命中调查（低代码页面）
---


## 使用说明

1. 创建插件：登录->系统管理->插件管理->创建插件->上传插件包。
2. 安装插件：插件列表找到已经安装的插件->点击安装
3. 菜单调整：安装完成后自动创建菜单，默认都是一级菜单，可在菜单管理中调整菜单位置。
4. 插件修改：根据业务需求配置相关插件参数即可
5. 插件导出：插件列表找到已经安装的插件->点击导出（包含已经修改后的插件配置文件、元数据文件、API接口文件、可视化页面文件、菜单项文件、插件图标文件）
6. 卸载插件：插件列表找到已经安装的插件->点击卸载
7. 删除插件：插件列表找到已经安装的插件->点击删除
---
