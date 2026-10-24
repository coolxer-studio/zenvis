# 插件能力实验室教程

`插件能力实验室` 是一个可安装、可运行、可持续扩展的 ZenVis 插件全栈样例。它不是单纯的字段清单，而是把一次完整的插件交付拆成可以分别观察、验证和替换的能力层：

```text
Meta 数据模型
  -> demo_logs -> 双 Kafka Topic -> Kafka 消费与 DLQ
  -> ClickHouse 实体数据
  -> 低代码应用、详情页、IP 调查与看板
  -> 动态 REST API、MySQL 迁移、MCP
  -> RAG 数据集、知识库与运行时 Skill
```

## 基本信息

| 项目 | 值 |
| --- | --- |
| 显示名称 | 插件能力实验室 |
| 包名 | `com.coolxer.plugin.lab` |
| 版本 | `1.0.0` |
| 作者 | CoolXer |
| 安装身份 | 全新安装，不兼容 `com.coolxer.plugin.user.event` |
| 发布包 | `com-coolxer-plugin-lab-1.0.0.tar.gz` |
| 插件 API 前缀 | `/api/v1/plugin/com.coolxer.plugin.lab` |

所有事件、IP、规则、风险分和笔记均为合成数据。两个实体之间的 IP 关联只演示查询方式，不代表真实攻击链或因果归属；风险等级也不是生产环境的安全结论。

## 快速开始

1. 构建插件包：

   ```bash
   cd zenvis-plugin/zenvis-plugin-lab
   ./build.sh
   ```

2. 在 ZenVis 插件管理中上传 `com-coolxer-plugin-lab-1.0.0.tar.gz` 并升级安装。
3. 确认“实验室演示数据生成”和“实验室 Kafka 接入与死信”两个任务均生效。访问事件约每 5 秒、风险线索约每 9 秒由生成器写入 Kafka，再由消费任务落库。
4. 打开“插件能力实验室”菜单，依次查看能力总览、访问事件、风险线索和实验室笔记。
5. 打开“实验室 IP 关联调查”，输入 `10.24.8.15`、`10.24.16.20` 等演示地址进行跨实体查询。
6. 在数据看板中查看 HTML 大屏和低代码数据观察台。
7. 在 MCP 管理页确认 `lab` 服务发现 `lab_capability_catalog` 与 `lab_note_search`，再在 Skill 管理页检查“插件能力实验室导览”。

## 文档导航

| 文件 | 内容 |
| --- | --- |
| `01-能力地图.md` | 每项插件能力对应的目录、文件、页面和验证入口 |
| `02-数据模型.md` | 双实体、字段类型、字典、链接、分区与存储契约 |
| `03-接入链路与DLQ.md` | `demo_logs` 生产、Kafka 消费入库、校验、环境变量和死信重放 |
| `04-REST-API与MySQL.md` | 笔记 CRUD、分页、状态统计、迁移、事务与校验 |
| `05-UI与看板.md` | 应用、详情、IP 调查、HTML 大屏与低代码看板 |
| `06-MCP-RAG-Skill.md` | MCP 工具、RAG 数据集/知识库、Skill 白名单与降级 |
| `07-验证与排障.md` | 静态检查、单元测试、浏览器检查和部署验收 |
| `08-旧功能对照.md` | 旧 `user-event` 示例到新实验室示例的映射 |

## 目录结构

```text
zenvis-plugin-lab/
├── plugin-lab/
│   ├── 00_doc/                随包教程与图标源文件
│   ├── 01_meta/               双实体 Meta
│   ├── 02_push-task/          演示消息生产与 Kafka/DLQ 消费链路
│   ├── 03_api/                迁移脚本与构建后的动态 JAR
│   ├── 04_ui/                 低代码应用、详情、IP 调查、HTML 大屏
│   ├── 05_dashboard/          HTML 与低代码看板注册
│   ├── 06_menu/               三种菜单入口
│   ├── 07_mcp/                只读 MCP 注册
│   ├── 08_rag/                数据集与知识库声明
│   └── 09_skill/              插件导览 Skill
└── extend-lab/                动态 API 与 MCP 的 Maven 工程
```

## 设计边界

- 不使用旧包名、旧实体名、旧表名或旧测试代码。
- 不定义 `zenvis_id` 和 `zenvis_insert_time`，两者由平台维护。
- 分区统一使用 `toYYYYMM(zenvis_insert_time)`。
- MCP 只提供只读工具；笔记写入只通过 REST API 或低代码页面完成。
- Skill 只允许 `retrieval_search`、`lab_capability_catalog` 和 `lab_note_search`。
- 不修改 ZenVis 平台后端或前端；插件必须通过现有安装、查询与 Agent 契约运行。

