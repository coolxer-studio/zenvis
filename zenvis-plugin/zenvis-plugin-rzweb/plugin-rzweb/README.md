# RzWeb 二进制逆向分析插件

## 概述

本插件集成 RzWeb——基于 [Rizin](https://rizin.re) 反汇编框架的浏览器端逆向分析工具。通过 WebAssembly 将 Rizin 核心引擎移植到浏览器，用户无需本地安装任何逆向工具，即可在 Zenvis 平台中直接上传二进制文件并完成静态分析。

支持的功能包括：反汇编、十六进制查看、反编译（基于 Ghidra Decompiler）、控制流图、字符串分析、导入/导出表、段信息、交叉引用、终端命令行等。

## 插件信息

| 项目 | 值 |
|------|-----|
| 插件名称 | RzWeb 二进制逆向分析 |
| 包名 | `com.coolxer.plugin.rzweb` |
| 版本 | 1.0.0 |
| 作者 | CoolXer |
| 图标 | `icon.png` |

## 数据模型

本插件为纯前端静态工具插件，不涉及结构化数据采集、不定义 Meta 实体、不创建 ClickHouse 表，也不配置 Vector 推送任务。

| 目录 | 内容 |
|------|------|
| `01_meta/` | 无 Meta 定义（仅 `.gitkeep`） |
| `02_push-task/` | 无推送任务（仅 `.gitkeep`） |
| `03_api/` | 无动态 API（仅 `.gitkeep`） |
| `05_dashboard/` | 无看板配置（空数组） |
| `06_menu/` | HTML_PAGE 菜单注册 |
| `07_mcp/` | 无 MCP 配置（空数组） |
| `08_rag/` | 未声明插件 RAG（仅 `.gitkeep`） |
| `09_skill/` | 无 AI Skill（仅 `.gitkeep`） |

## UI 页面

### 静态 HTML 页面

RzWeb 以单页应用（SPA）形式打包，位于 `04_ui/html-page/rzweb/`。安装时由插件 UI 安装流程将整个 `04_ui/html-page/` 目录拷贝到运行时的 `html-page/com.coolxer.plugin.rzweb/` 路径下，**不依赖看板配置**。

| 项目 | 值 |
|------|-----|
| HTML 源路径 | `04_ui/html-page/rzweb/index.html` |
| 运行时访问路径 | `/zenvis/html-page/com.coolxer.plugin.rzweb/rzweb/index.html` |

### 菜单

| 菜单项 | 类型 | 路由参数 |
|--------|------|----------|
| 二进制分析 | `HTML_PAGE` | `/zenvis/html-page/com.coolxer.plugin.rzweb/rzweb/index.html` |

菜单点击后，前端以 iframe 方式加载 RzWeb 页面，后端自动对 `params` 进行 Base64-URL 编码，前端 `html-page` 页面解码后作为 iframe `src`。

## 运行环境与限制

### COOP/COEP 与 SharedArrayBuffer

RzWeb 基于 WebAssembly 多线程构建，运行时依赖 `SharedArrayBuffer`，而 `SharedArrayBuffer` 需要页面设置以下跨域隔离响应头：

- `Cross-Origin-Opener-Policy: same-origin`
- `Cross-Origin-Embedder-Policy: require-corp`

由于 Zenvis 的 `html-page` 静态资源由 Spring `ResourceHandler` 直接提供（运行时完整路径为 `/zenvis/html-page/**`），不会处理目录中的 Netlify 风格 `_headers` 文件，因此无法自动注入上述响应头。

当前的缓解措施：

- `index.html` 引入了 `coi-serviceworker.min.js`，在支持 Service Worker 的环境中通过拦截 fetch 请求动态添加 COOP/COEP 头。
- 该方案要求页面运行在 **安全上下文**（HTTPS 或 localhost）下，且 Service Worker 需成功注册。

**若运行时无法启用跨域隔离，RzWeb 的多线程 WASM 功能将降级或不可用。** 如需稳定支持，需在网关或反向代理层为 `/zenvis/html-page/com.coolxer.plugin.rzweb/rzweb/**` 路径添加 COOP/COEP 响应头。

### 资源与字体

- 页面引用 Google Fonts（Inter、JetBrains Mono），离线环境下将回退到系统等宽字体。
- 二进制文件在浏览器本地处理，不上传至服务器。

## 部署步骤

1. 执行 `build.sh` 构建插件包：
   ```bash
   cd zenvis-plugin-rzweb
   ./build.sh
   ```
2. 在 Zenvis 插件管理页面上传生成的 `com-coolxer-plugin-rzweb-1.0.0.tar.gz`。
3. 安装完成后，从左侧菜单「二进制分析」进入 RzWeb 工具。
4. 如需完整功能，确保部署环境支持 COOP/COEP 响应头（见上文）。

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2026-10-12 | 初始版本，从 BTW 插件中独立为 RzWeb 专用插件；补全 index.json、菜单与文档配置；后端已解耦 html-page 安装与看板配置，无需 dashboard 即可部署静态 HTML 工具 |
