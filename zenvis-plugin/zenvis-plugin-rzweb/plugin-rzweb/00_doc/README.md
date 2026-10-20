# RzWeb 二进制逆向分析插件 — 文档

## 文档目录

本文档为 RzWeb 插件的文档入口，介绍插件的整体结构与使用方式。

| 文档 | 说明 |
|------|------|
| [README.md](../README.md) | 插件根目录说明，包含概述、插件信息、运行限制与部署步骤 |
| [使用说明.md](使用说明.md) | 最终用户操作指南：打开文件 / 反汇编 / 反编译 / 终端命令 / 常见问题 |

## 插件定位

RzWeb 是一个**纯前端静态工具插件**，不包含数据采集、Meta 实体定义、ClickHouse 表、Vector 推送任务、动态 API 或看板配置。它通过插件 UI 安装流程将 RzWeb 单页应用部署到 Zenvis 平台的 `html-page/` 目录，供安全分析人员在浏览器中对二进制文件进行静态逆向分析。

## 数据模型

本插件不定义任何结构化实体，不写入 ClickHouse。

## 推送任务架构

无推送任务。二进制文件在浏览器端本地处理，不经过服务器。

## UI 页面

| 页面 | 类型 | 路径 | 说明 |
|------|------|------|------|
| RzWeb 逆向分析工具 | 静态 HTML | `04_ui/html-page/rzweb/index.html` | 基于 Rizin WASM 的浏览器端反汇编器 |

`04_ui/html-page/` 目录在插件安装时由 UI 层整体拷贝到运行时 `html-page/<package_name>/`，不依赖看板配置。

## 看板

无看板配置（`05_dashboard/config.json` 为空数组）。

## 菜单

| 菜单项 | 类型 | 参数 |
|--------|------|------|
| 二进制分析 | `HTML_PAGE` | `/zenvis/html-page/com.coolxer.plugin.rzweb/rzweb/index.html` |

## 目录结构

```
plugin-rzweb/
├── index.json                  # 插件描述（名称、包名、版本、图标）
├── README.md                   # 插件说明
├── icon.png                    # 插件图标
├── 00_doc/
│   └── README.md               # 本文档（文档入口）
├── 01_meta/                    # 无 Meta 定义
├── 02_push-task/               # 无推送任务
├── 03_api/                     # 无动态 API
├── 04_ui/
│   └── html-page/
│       └── rzweb/              # RzWeb 静态资源（index.html + assets + WASM）
├── 05_dashboard/
│   └── config.json             # 空数组（无看板）
├── 06_menu/
│   └── config.json             # HTML_PAGE 菜单注册
├── 07_mcp/
│   └── config.json             # 空数组（无 MCP）
├── 08_rag/                     # 未声明插件 RAG
│   └── .gitkeep
└── 09_skill/                   # 无 AI Skill
    └── .gitkeep
```

## 运行限制

RzWeb 的 WebAssembly 多线程功能依赖 `SharedArrayBuffer`，需要 COOP/COEP 跨域隔离响应头。详见 [README.md](../README.md#运行环境与限制)。

## 安装与使用

1. 运行 `build.sh` 构建插件包。
2. 在 Zenvis 插件管理页面上传并安装。
3. 从菜单「二进制分析」进入工具。
