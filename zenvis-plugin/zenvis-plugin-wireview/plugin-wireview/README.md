# Wireview PCAP 流量分析插件

## 概述

本插件集成 Wireview——基于 [Wiregasm](https://github.com/wireshark/wiregasm)（Wireshark 的 WebAssembly 移植）的浏览器端数据包分析工具。用户无需在本地安装 Wireshark，即可在 Zenvis 平台中直接打开或拖入抓包文件（`.cap`、`.pcap`、`.pcapng`），完成协议解码与逐包分析。

支持的功能包括：数据包列表与协议着色、数据包详情（协议树）查看、显示过滤器、会话/流跟踪、原始字节与十六进制查看、字段过滤与导出，以及内置示例抓包文件的快速体验。

抓包文件全程在浏览器本地解析，**不会上传到服务器**。

## 插件信息

| 项目 | 值 |
|------|-----|
| 插件名称 | Wireview PCAP 流量分析 |
| 包名 | `com.coolxer.plugin.wireview` |
| 版本 | 1.0.0 |
| 作者 | CoolXer |
| 图标 | `icon.png` |

## 数据模型

本插件为**纯前端静态工具插件**，不涉及结构化数据采集、不定义 Meta 实体、不创建 ClickHouse 表，也不配置 Vector 推送任务。

| 目录 | 内容 |
|------|------|
| `01_meta/` | 无 Meta 定义（仅 `.gitkeep`） |
| `02_push-task/` | 无推送任务（仅 `.gitkeep`） |
| `03_api/` | 无动态 API（仅 `.gitkeep`） |
| `05_dashboard/` | 无看板配置（空数组） |
| `06_menu/` | HTML_PAGE 菜单注册 |
| `07_mcp/` | 无 MCP 配置（空数组） |
| `08_rag/` | 预留占位（仅 `.gitkeep`） |
| `09_skill/` | 无 AI Skill（仅 `.gitkeep`） |

## UI 页面

### 静态 HTML 页面

Wireview 以单页应用（SPA）形式打包，位于 `04_ui/html-page/wireview/`。安装时由插件 UI 安装流程将整个 `04_ui/html-page/` 目录拷贝到运行时的 `html-page/com.coolxer.plugin.wireview/` 路径下，**不依赖看板配置**。

| 项目 | 值 |
|------|-----|
| HTML 源路径 | `04_ui/html-page/wireview/index.html` |
| 运行时访问路径 | `/zenvis/html-page/com.coolxer.plugin.wireview/wireview/index.html` |
| 示例抓包文件 | `04_ui/html-page/wireview/shark1.pcapng`（内置演示样本） |

页面内的资源引用均为相对路径（`./assets/...`、`../wiregasm.js`），可正确工作在 `/zenvis/html-page/<package_name>/wireview/` 这一子目录部署形态下。

### 菜单

| 菜单项 | 类型 | 路由参数 |
|--------|------|----------|
| 流量包分析 | `HTML_PAGE` | `/zenvis/html-page/com.coolxer.plugin.wireview/wireview/index.html` |

菜单点击后，前端以 iframe 方式加载 Wireview 页面，后端自动对 `params` 进行 Base64-URL 编码，前端 `html-page` 页面解码后作为 iframe `src`。`HTML_PAGE` 的 `params` 必须包含 `/zenvis` 上下文前缀。

菜单仅创建入口，**不会自动为角色授权**；安装完成后需在「角色权限」中为相应角色分配该菜单。

## 运行环境与限制

### WebAssembly 与 Web Worker

Wireview 通过 WebAssembly 加载 Wiregasm 引擎，并在 Web Worker 中运行解析逻辑，避免阻塞页面主线程。

- 与 RzWeb 不同，本工具**不依赖 `SharedArrayBuffer`**，因此**不需要** COOP/COEP 跨域隔离响应头，可直接在普通 `html-page` 静态托管下运行。
- 需要浏览器支持 WebAssembly 与 ES Module Worker（Chrome/Edge、Firefox、Safari 现代版本均可）。

### 资源与数据安全

- 页面引用 Google Fonts；离线环境下将回退到系统字体，不影响核心功能。
- 抓包文件在浏览器本地处理，不经过服务器，也不写入平台存储。
- 内置 `shark1.pcapng` 仅为演示样本，点击首页 “try out an example” 时加载。

### 大文件

超大 PCAP 的解析受浏览器可用内存限制。建议单文件控制在数百 MB 以内；更大规模的历史流量分析应使用服务端抓包与结构化入库能力，而非本工具。

## 目录结构

```
plugin-wireview/
├── index.json                  # 插件描述（名称、包名、版本、图标）
├── README.md                   # 插件说明
├── icon.png                    # 插件图标
├── 00_doc/
│   └── README.md               # 文档入口
├── 01_meta/                    # 无 Meta 定义
├── 02_push-task/               # 无推送任务
├── 03_api/                     # 无动态 API
├── 04_ui/
│   └── html-page/
│       └── wireview/           # Wireview 静态资源（index.html + assets + WASM）
├── 05_dashboard/
│   └── config.json             # 空数组（无看板）
├── 06_menu/
│   └── config.json             # HTML_PAGE 菜单注册
├── 07_mcp/
│   └── config.json             # 空数组（无 MCP）
├── 08_rag/                     # 预留占位
│   └── .gitkeep
└── 09_skill/                   # 无 AI Skill
    └── .gitkeep
```

## 部署步骤

1. 执行 `build.sh` 构建插件包：
   ```bash
   cd zenvis-plugin-wireview
   ./build.sh
   ```
2. 在 Zenvis 插件管理页面上传生成的 `com-coolxer-plugin-wireview.tar.gz`。
3. 安装完成后，在「角色权限」中为需要的角色分配「流量包分析」菜单。
4. 从左侧菜单「流量包分析」进入 Wireview，打开或拖入本地抓包文件开始分析。

## 已知问题

- 示例样本 `shark1.pcapng` 随包发布，用于快速验证；如需减小包体可在打包前删除该文件，但首页的 “try out an example” 将不可用。
- 上游 Wiregasm/Wireview 为第三方开源产物，其界面语言与功能以随包资源为准。

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2026-10-13 | 初始版本；从 BTW 插件中独立为 Wireview 专用插件，静态 HTML 工具形态，无需看板配置即可部署；补齐示例抓包文件的相对路径引用以适配子目录部署 |
