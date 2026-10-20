# Wireview PCAP 流量分析插件 — 文档

## 文档目录

本文档为 Wireview 插件的文档入口，介绍插件的整体结构与使用方式。

| 文档 | 说明 |
|------|------|
| [README.md](../README.md) | 插件根目录说明，包含概述、插件信息、运行限制与部署步骤 |
| [使用说明.md](使用说明.md) | 最终用户操作指南：打开 PCAP / 显示过滤器 / 流跟踪 / 导出 / 常见问题 |

## 插件定位

Wireview 是一个**纯前端静态工具插件**，不包含数据采集、Meta 实体定义、ClickHouse 表、Vector 推送任务、动态 API 或看板配置。它通过插件 UI 安装流程将 Wireview 单页应用部署到 Zenvis 平台的 `html-page/` 目录，供安全分析人员在浏览器中对本地抓包文件（PCAP/PCAPNG）进行协议解码、逐包查看与流跟踪。

工具本身不产生结构化数据，也不与平台后端 API 交互；平台仅负责托管静态资源并提供菜单入口。

## 数据模型

本插件不定义任何结构化实体，不写入 ClickHouse，不注册推送任务。

| 目录 | 内容 |
|------|------|
| `01_meta/` | 无 Meta 定义 |
| `02_push-task/` | 无推送任务 |
| `03_api/` | 无动态 API / 无 MySQL 迁移 |
| `05_dashboard/` | 无看板配置（空数组） |
| `06_menu/` | HTML_PAGE 菜单注册 |
| `07_mcp/` | 无 MCP 连接（空数组） |
| `08_rag/` | 未声明插件 RAG（仅 `.gitkeep`） |
| `09_skill/` | 无运行时 Skill |

## 推送任务架构

无推送任务。抓包文件在浏览器端本地解析，不经过服务器，也不进入 Kafka / ClickHouse / DLQ 链路。

## UI 页面

| 页面 | 类型 | 路径 | 说明 |
|------|------|------|------|
| Wireview 流量分析工具 | 静态 HTML | `04_ui/html-page/wireview/index.html` | 基于 Wiregasm(WASM) 的浏览器端数据包分析界面 |

`04_ui/html-page/` 目录在插件安装时由 UI 层整体拷贝到运行时 `html-page/<package_name>/`，与看板配置解耦：

- 源目录：`04_ui/html-page/wireview/`
- 运行时目录：`html-page/com.coolxer.plugin.wireview/wireview/`
- 访问路径：`/zenvis/html-page/com.coolxer.plugin.wireview/wireview/index.html`

页面内所有资源引用（JS、CSS、WASM、示例抓包）均为相对路径，适配子目录部署；Wiregasm 引擎通过 ES Module Worker 加载，Worker 与 WASM 的相对基址由页面自身解析，无需额外配置。

## 看板

无看板配置（`05_dashboard/config.json` 为空数组）。本工具通过 `06_menu` 的 `HTML_PAGE` 菜单直接进入，不注册 Dashboard 记录。

## 菜单

| 菜单项 | 类型 | 参数 |
|--------|------|------|
| 流量包分析 | `HTML_PAGE` | `/zenvis/html-page/com.coolxer.plugin.wireview/wireview/index.html` |

菜单参数包含 `/zenvis` 上下文前缀；前端 `html-page` 视图会对 `params` 做 Base64 解码后直接作为 iframe `src`（配合 `window.location.origin` 解析）。

菜单安装为一级菜单，不自动授予任何角色。安装后需在「角色权限」中为目标角色分配该菜单才能可见。

## 目录结构

```
plugin-wireview/
├── index.json                  # 插件描述（名称、包名、版本、图标）
├── README.md                   # 插件说明
├── icon.png                    # 插件图标（100×100 PNG）
├── 00_doc/
│   └── README.md               # 本文档（文档入口）
├── 01_meta/                    # 无 Meta 定义（仅 .gitkeep）
├── 02_push-task/               # 无推送任务（仅 .gitkeep）
├── 03_api/                     # 无动态 API（仅 .gitkeep）
├── 04_ui/
│   └── html-page/
│       └── wireview/           # Wireview 静态资源
│           ├── index.html      # 入口页面
│           ├── assets/         # 应用 JS/CSS 与 Worker
│           ├── wiregasm.js     # WASM 加载器
│           ├── wiregasm.wasm   # Wiregasm 引擎
│           ├── wiregasm.bmp    # 引擎资源
│           ├── shark1.pcapng   # 内置示例抓包文件
│           └── wireshark.svg   # 站点图标
├── 05_dashboard/
│   └── config.json             # 空数组（无看板）
├── 06_menu/
│   └── config.json             # HTML_PAGE 菜单注册
├── 07_mcp/
│   └── config.json             # 空数组（无 MCP）
├── 08_rag/                     # 未声明插件 RAG
│   └── .gitkeep
└── 09_skill/                   # 无 AI Skill（仅 .gitkeep）
    └── .gitkeep
```

## 运行限制

- 依赖 WebAssembly 与 ES Module Worker；不依赖 `SharedArrayBuffer`，因此无需 COOP/COEP 跨域隔离响应头。
- 抓包文件本地处理，不上传服务器；受浏览器内存限制，建议单文件不超过数百 MB。
- 页面引用 Google Fonts，离线环境回退系统字体，不影响核心功能。

## 安装与使用

1. 运行 `build.sh` 构建插件包：`./build.sh`，产物为 `com-coolxer-plugin-wireview.tar.gz`（位于仓库根目录）。
2. 在 Zenvis 插件管理页面上传并安装。
3. 在「角色权限」中为角色分配「流量包分析」菜单。
4. 从菜单进入 Wireview，打开或拖入抓包文件；也可点击首页 “try out an example” 加载内置示例样本。

## 升级与卸载说明

- 升级：本插件仅包含静态资源，升级会整体替换运行时 `html-page/com.coolxer.plugin.wireview/` 目录内容；菜单通过稳定的 `type + params` 匹配键保留菜单 ID 与角色授权。
- 卸载：会移除该目录下安装的静态资源与插件菜单；本插件不拥有任何 ClickHouse 表或 MySQL 迁移，因此不涉及数据清理或回滚。

## 参考

- 上游项目：Wiregasm / Wireview（Wireshark 的 WebAssembly 移植，第三方开源产物）。
- 页面能力以随包的前端资源为准，本文档不承诺未随包发布的字段级功能。
