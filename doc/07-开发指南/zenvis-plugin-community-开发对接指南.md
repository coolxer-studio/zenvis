# `zenvis-plugin-community` 开发对接指南

## 项目定位

`zenvis-plugin-community` 聚合社区和客户场景插件。它与 `zenvis-plugin` 使用同一平台安装契约，但每个场景由独立 Git 工作树维护，拥有自己的版本、数据字典、API 适配器、构建脚本和发布节奏。

不要在父目录直接提交两个子仓库的源码变更。进入目标子仓库后再检查状态、测试和提交。

## 上下游关系

- 上游：厂商数据字典、接口规范、补充协议，以及当前后端、前端和插件安装契约。
- 下游：两个独立 Git 工作树生成的插件归档、动态 API、Vectum/Kafka/ClickHouse 数据链路和目标客户环境。
- 协作边界：社区插件复用平台契约，但独立维护版本和发布节奏；客户专属字段、地址和凭据不能反向固化为平台默认值。

## 技术栈与当前仓库

插件描述层使用 JSON、YAML/TOML、Markdown 和 HTML；数据接入使用 Vector/Vectum、Kafka 与 ClickHouse；动态 API 使用 Java、Spring Boot 兼容 API 和 Maven；发布脚本使用 Bash 或 PowerShell。

| Git 工作树 | 插件/构件 | 包名或 artifact | 当前版本 | 作用 |
| --- | --- | --- | --- | --- |
| `zenvis-plugin-jmr` | `plugin-jmr` | `com.coolxer.plugin.jmr` | `3.0.12` | 僵木蠕流量检测、数据接入、IP 统计和安全看板 |
| `zenvis-plugin-jmr` | `extend-jmr` | 动态 API 工程 | 随插件 | JMR 动态 API 薄 Jar |
| `zenvis-plugin-xiangtanhospital` | `plugin-lubinsun` | `com.coolxer.plugin.lubinsun` | `2.1.0` | Lubinsun 任务、审批和 StoneOS 处置 |
| `zenvis-plugin-xiangtanhospital` | `extend-lubinsun` | `com.coolxer.plugin:extend-lubinsun` | `2.1.0` | Lubinsun/StoneOS 动态 API |
| `zenvis-plugin-xiangtanhospital` | `plugin-security-device-data` | `com.coolxer.plugin.security.device.data` | `1.6.0` | 安全设备数据接入、检索和自动 IP 关联 |
| `zenvis-plugin-xiangtanhospital` | `extend-security-device-data` | `com.coolxer.plugin:extend-security-device-data` | `1.6.0` | 跨实体 IP 查询和任务编排 API |

版本以目标插件 `index.json` 和适配器 `pom.xml` 为准。

## 目录职责与 Git 边界

```text
zenvis-plugin-community/
├── zenvis-plugin-jmr/                       # 独立 Git 工作树
│   ├── plugin-jmr/
│   ├── extend-jmr/
│   ├── build.sh
│   └── build.ps1
└── zenvis-plugin-xiangtanhospital/          # 独立 Git 工作树
    ├── plugin-lubinsun/
    ├── extend-lubinsun/
    ├── plugin-security-device-data/
    ├── extend-security-device-data/
    ├── build.sh
    └── build.ps1
```

开始工作前：

```bash
git -C zenvis-plugin-community/zenvis-plugin-jmr status --short
git -C zenvis-plugin-community/zenvis-plugin-xiangtanhospital status --short
```

只在实际目标工作树中修改和验证。

## 与内置插件的关系

社区插件继续遵循 `index.json`、`00_doc` 至 `08_menu` 契约，以及 Meta、Vector、动态 API、UI、看板和菜单的一致性要求。差异主要在：

- 数据规范和接口往往来自外部厂商；
- 可能包含客户环境专用字段、队列和部署顺序；
- API 适配器通常是独立 `extend-*` Maven 工程；
- 凭据、地址和数据字典可能具有更高敏感级别；
- 发布前需要目标环境联调，不能只依赖通用示例。

平台契约仍以当前后端、前端和[插件开发与集成](/03-插件开发与集成/README.md)为准。

## 环境与配置

- 使用 JDK 17、Maven 3.x、Bash/PowerShell 和 tar；完整联调还需要 ZenVis 后端、前端、Vectum、Kafka、ClickHouse 和 MySQL。
- 厂商 API、StoneOS、消息队列和数据库连接通过环境变量或受控部署配置注入。
- `index.json`、Meta、Vector、动态 API、UI、看板与菜单的标识必须在单个插件版本内闭合。
- 构建前分别检查两个子工作树状态，不跨工作树复用未提交 Jar 或归档。

## 启动、构建与测试

社区插件本身没有独立常驻启动命令；动态 API 由 ZenVis 后端安装后加载，数据任务由 Vectum 运行。开发阶段先执行适配器测试，准备发布时再运行目标工作树的打包脚本：

| 目标 | 测试 | 发布构建 |
| --- | --- | --- |
| JMR | `mvn -f extend-jmr/pom.xml test` | `bash build.sh plugin-jmr` |
| Lubinsun | `mvn -f extend-lubinsun/pom.xml test` | `bash build.sh plugin-lubinsun` |
| 安全设备数据 | `mvn -f extend-security-device-data/pom.xml test` | `bash build.sh plugin-security-device-data` |

打包会替换插件 `03_api` 中的 Jar 并生成归档，只在明确准备发布时执行。

## JMR 插件开发

`plugin-jmr` 按僵木蠕 v3.0.1 数据定义建模，主要链路为：

```text
数据文件/来源 → Vector → Kafka → Vector 转换 → ClickHouse
                                  └→ DLQ
ClickHouse → Retrieval/动态 API → 低代码页面、IP 统计和看板
```

专项资料位于：

- `plugin-jmr/README.md`；
- `plugin-jmr/00_doc/数据字典-僵木蠕_v3.0.1.md`；
- `plugin-jmr/00_doc/数据类型映射.md`；
- `plugin-jmr/00_doc/看板配置说明.md`。

修改数据定义时保持数据字典顺序、Entity、表、topic、Vector 字段、详情页、IP 统计和看板同步。

### 测试与构建

```bash
cd zenvis-plugin-community/zenvis-plugin-jmr
mvn -f extend-jmr/pom.xml test
bash build.sh plugin-jmr
```

打包脚本会先构建 `extend-jmr`，复制薄 Jar 到 `plugin-jmr/03_api`，再生成 `com-coolxer-plugin-jmr.tar.gz`。发布前检查归档而不只是源码。

## 湘潭医院插件开发

### 职责划分

- `plugin-lubinsun` 管理智能任务、FIFO 队列、待审批中心和 StoneOS 处置。
- `plugin-security-device-data` 管理 NSG、NS、HET-WAF、STA 等安全数据的接入、存储、检索、详情和 IP 关联。
- 两个插件通过 HTTP API 协作，不共享 Java 进程内状态。
- 两个 `extend-*` 工程分别生成对应插件的动态 API Jar。

专项资料分布在各插件 README、`00_doc` 和适配器 README。处理厂商字段时应以完整对外数据说明和补充协议为准。

### API 测试

```bash
cd zenvis-plugin-community/zenvis-plugin-xiangtanhospital
mvn -f extend-lubinsun/pom.xml test
mvn -f extend-security-device-data/pom.xml test
```

### 构建

```bash
bash build.sh plugin-lubinsun plugin-security-device-data
```

Windows：

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1 `
  plugin-lubinsun plugin-security-device-data
```

脚本构建同名 `extend-*`，复制 Jar 到插件 `03_api`，并生成两个 `.tar.gz`。

### 部署顺序

从旧 `plugin-lubinsun 1.7.1` 升级时：

1. 先升级或卸载旧插件。
2. 确认旧 Meta、推送任务和安全设备表已按迁移说明处理。
3. 再安装 `plugin-security-device-data`。
4. 不同时安装仍包含旧安全设备能力的 Lubinsun 插件和新安全设备插件。

涉及更高版本时以目标插件 README 和升级说明为准，不机械套用历史顺序。

## 外部集成与敏感信息

厂商地址、账号、密码、Token、证书和真实数据样例不得硬编码进：

- Java 源码和资源；
- Vector 配置；
- `index.json`、README 和数据字典；
- 生成的 API Jar 与插件归档；
- Git 日志和问题截图。

使用环境变量、受控部署配置或密钥管理系统。发布归档前搜索敏感值，并检查编译后的资源。

## 核心开发流程

1. 选择唯一目标子仓库并检查状态。
2. 阅读完整数据字典、补充协议和目标插件 README。
3. 建立数据定义、Entity、表、topic、API 和页面契约矩阵。
4. 修改 Meta、Vector、API、UI、看板、菜单和文档。
5. 解析 JSON/YAML，运行适配器测试和 Vector 校验。
6. 提高插件版本，执行构建脚本。
7. 检查归档内容、Jar、迁移和敏感信息。
8. 在兼容的后端、前端和部署环境进行安装或升级验证。

## 扩展点

- 新增社区场景：建立独立 Git 工作树、插件目录、适配器工程、构建脚本和发布说明。
- 新增数据定义：保持一份定义对应 Entity、表、topic、转换、API 与页面，并同步完整数据字典。
- 新增厂商适配：在对应 `extend-*` 工程实现超时、重试、容量、审批和错误脱敏，插件归档仍只携带薄 Jar。
- 新增跨插件协作：使用受控 HTTP API 和明确部署顺序，不依赖同进程状态或重复注册相同 Entity/菜单。

## 常见问题

### 父目录 `git status` 看不到插件修改

两个场景目录是独立 Git 工作树。使用 `git -C <子仓库> status`。

### 打包成功但 API 仍是旧版本

检查脚本是否重新执行 `clean package`、`03_api` 中是否只有一个新 Jar，以及归档内 Jar 的时间和内容。

### 两个插件都安装后出现重复 Entity 或菜单

检查部署顺序和旧版 Lubinsun 是否仍包含已拆分的安全设备能力，同时核对包名、Entity、表和菜单 code。

### 安全设备数据进入错误实体

回到数据定义的一对一映射，比较 Vector transform 输出和 Meta 属性；不要把不同协议或设备定义合并到通用实体。

### 自动 IP 关联任务积压

检查有界队列、下游 Lubinsun API、重试、超时和日志；队列内存状态不等于可靠持久化。

## 交付检查

- 修改只位于目标社区子仓库；
- 数据字典、契约矩阵和所有插件环节一致；
- 适配器测试、JSON/YAML 和 Vector 校验通过；
- 部署顺序、兼容限制和回滚方式已记录；
- 归档没有源码目录、日志、旧包或敏感信息；
- 在目标工作树运行 `git diff --check`。

## 关联文档

- [`zenvis-plugin` 开发对接指南](/07-开发指南/zenvis-plugin-开发对接指南.md)
- [插件开发与集成](/03-插件开发与集成/README.md)
- [`agent-skills` 开发对接指南](/07-开发指南/agent-skills-开发对接指南.md)
- [`deploy` 开发对接指南](/07-开发指南/deploy-开发对接指南.md)
