# QRSend 二维码离线传输插件 — 文档

## 文档目录

本文档为 QRSend 插件的文档入口，介绍插件的整体结构、部署形态与使用方式。

| 文档 | 说明 |
|------|------|
| [README.md](../README.md) | 插件根目录说明，包含概述、插件信息、运行限制、部署步骤与版本历史 |
| [使用说明.md](使用说明.md) | 最终用户操作指南：发送 / 接收 / 续传 / 设备配对 / 高级功能 / 常见问题 |

## 插件定位

QRSend 是一个**纯前端静态工具插件**，不包含数据采集、Meta 实体定义、ClickHouse 表、Vector 推送任务、动态 API 或看板配置。它通过插件 UI 安装流程把 QRSend 单页应用部署到 Zenvis 平台的 `html-page/` 目录，供使用者在浏览器中完成「屏幕 → 摄像头」的离线数据传输。

工具本身不产生结构化数据，也不与平台后端 API 交互；平台仅负责托管静态资源并提供菜单入口。

## 能力概览

| 能力 | 说明 |
|------|------|
| 二维码流传输 | 数据分段、压缩、喷泉编码（RaptorQ）后逐帧渲染为二维码；接收端任意足够子集即可重建 |
| 文本 / 文件 / 文件夹 | 发送文本、单个或多个文件、整个文件夹；接收后可直接保存或打包为 ZIP |
| 断点续传 | 传输可中断、可继续；接收端给出 `QSR1-XXXXXXXX` resume code，发送端据此只补发缺失部分 |
| 设备配对与加密 | 设备密钥对（不可导出）、互相添加设备 ID 并核对指纹后，使用 age 端到端加密与签名清单 |
| 完整性校验 | 每个分段与文件使用 BLAKE3 校验；路径净化、解压有上限 |
| 接收通道 | 摄像头实时扫描、已录制视频文件、屏幕捕获 |
| 发送形态 | 单码 / 多码网格（最多 8×8 或铺满屏幕）、彩色码（红绿蓝三码合一） |
| 双向与提速 | 双向传输（接收端回传缺失信息给发送端）、自动调速、声音反馈、局域网加速（WebRTC 直连） |
| 本地存储 | 传输数据在 OPFS 中流式读写；未完成会话保留在收件箱，「存储」页面可查看与清理 |
| 离线可用 | 以 PWA 形式预缓存资源，加载后可离线使用 |
| 界面语言 | 英文与简体中文，跟随浏览器语言并可在页脚切换 |

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

## 协议与数据流（背景知识）

上游线格式（摘自 QRSend 项目文档，可能随版本变化）：

```text
files ─ concat ─ zstd ─▶ body ─ split ─▶ segments ─┐
manifest (paths, sizes, BLAKE3) ─▶ meta segment ───┴─ RaptorQ ─▶ symbols ─▶ frames ─ Base45 ─▶ QR
```

- 每个二维码承载某个分段的一个 RaptorQ 符号，外加 26 字节帧头；
- 帧使用 Base45 编码，以便落在二维码的字母数字模式内，并兼容只返回文本的解码器；
- 任何约 `K+ε` 个符号即可重建一个分段，因此漏读部分二维码不影响最终结果。

本插件只托管上游构建产物，不修改协议实现；以上内容仅用于理解工具行为。

## 推送任务架构

无推送任务。数据在浏览器端通过屏幕与摄像头传递，不经过服务器，也不进入 Kafka / ClickHouse / DLQ 链路。

## UI 页面

| 页面 | 类型 | 路径 | 说明 |
|------|------|------|------|
| QRSend 二维码流传输工具 | 静态 HTML | `04_ui/html-page/qrsend/index.html` | 基于 Rust/WASM 核心的浏览器端二维码收发界面 |

`04_ui/html-page/` 目录在插件安装时由 UI 层整体拷贝到运行时 `html-page/<package_name>/`，与看板配置解耦：

- 源目录：`04_ui/html-page/qrsend/`
- 运行时目录：`html-page/com.coolxer.plugin.qrsend/qrsend/`
- 访问路径：`/zenvis/html-page/com.coolxer.plugin.qrsend/qrsend/index.html`

页面内所有资源引用（JS、CSS、WASM、Worker、图标、Service Worker）均为相对路径（`./assets/...`、`./sw.js`），适配子目录部署。应用内部使用哈希路由：`#/send`、`#/receive`、`#/inbox`、`#/devices`、`#/preview`、`#/storage`。

随包静态资源：

| 文件 | 说明 |
|------|------|
| `index.html` | 入口页面，含内置 CSP 与 PWA 注册 |
| `assets/index-*.js` / `assets/index-*.css` | 应用主程序与样式 |
| `assets/qrsend_wasm_bg-*.wasm` | QRSend Rust 核心的 WebAssembly 模块（编码、解码、校验、加密） |
| `assets/engine.worker-*.js` | 引擎 Worker（编码/解码与存储读写） |
| `assets/scan.worker-*.js` | 扫描 Worker（Camera / 视频解码） |
| `assets/zxing_reader-*.wasm` | ZXing 条码识别 WASM |
| `assets/tap.worklet-*.js` | AudioWorklet，用于功能预览中的「声音反馈」 |
| `manifest.webmanifest`、`registerSW.js`、`sw.js`、`workbox-*.js` | PWA 清单与 Service Worker |
| `icon.svg`、`icon-192.png`、`icon-512.png` | 应用图标（与插件 `icon.png` 同源视觉） |

## 看板

无看板配置（`05_dashboard/config.json` 为空数组）。本工具通过 `06_menu` 的 `HTML_PAGE` 菜单直接进入，不注册 Dashboard 记录。

## 菜单

| 菜单项 | 类型 | 参数 |
|--------|------|------|
| 二维码离线传输 | `HTML_PAGE` | `/zenvis/html-page/com.coolxer.plugin.qrsend/qrsend/index.html` |

菜单参数包含 `/zenvis` 上下文前缀；前端 `html-page` 视图会对 `params` 做 Base64 解码后直接作为 iframe `src`（配合 `window.location.origin` 解析）。

菜单安装为一级菜单，不自动授予任何角色。安装后需在「角色权限」中为目标角色分配该菜单才能可见。

## 目录结构

```
plugin-qrsend/
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
│       └── qrsend/             # QRSend 静态资源
│           ├── index.html      # 入口页面
│           ├── assets/         # 应用 JS/CSS、Worker、WASM
│           ├── manifest.webmanifest
│           ├── registerSW.js
│           ├── sw.js
│           ├── workbox-*.js
│           └── icon.svg / icon-192.png / icon-512.png
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

- **安全上下文**：WebCrypto、OPFS 与 Service Worker 需要 HTTPS 或 localhost；明文 HTTP 的局域网访问下这些特性不可用。
- **WebAssembly 与 Worker**：依赖 WebAssembly 与 ES Module Worker；**不依赖 `SharedArrayBuffer`**，因此无需 COOP/COEP 跨域隔离响应头。
- **摄像头 / 麦克风**：由浏览器权限控制；iframe 与平台同源，默认授权白名单为同源，可正常申请。
- **页面内置 CSP**：`connect-src 'self'`，页面本身不发起外部网络请求；局域网加速使用 WebRTC 直连，不经过第三方服务器。
- **大文件**：受浏览器 OPFS 配额与可用存储限制；OPFS 不可用时回退内存，受内存限制。
- **数据不经过平台**：传输内容不写入平台服务端存储。

## 安装与使用

1. 运行 `build.sh` 构建插件包：`./build.sh`，产物为 `com-coolxer-plugin-qrsend-1.0.0.tar.gz`（位于仓库根目录）。
2. 在 Zenvis 插件管理页面上传并安装。
3. 在「角色权限」中为角色分配「二维码离线传输」菜单。
4. 从菜单进入 QRSend：
   - 发送端：「发送」→ 选择文本/文件/文件夹 → 播放二维码流；
   - 接收端：「接收」→ 摄像头对准发送端屏幕（或用视频文件 / 屏幕捕获）；
   - 缺失数据时按接收端提示的 resume code 在发送端续传；
   - 如需加密，先在「设备」中双向配对并核对指纹。

## 升级与卸载说明

- **升级**：本插件仅包含静态资源，升级会整体替换运行时 `html-page/com.coolxer.plugin.qrsend/` 目录内容；菜单通过稳定的 `type + params` 匹配键保留菜单 ID 与角色授权。
- **卸载**：会移除该目录下安装的静态资源与插件菜单；本插件不拥有任何 ClickHouse 表或 MySQL 迁移，因此不涉及数据清理或回滚。
- 浏览器侧残留（OPFS / IndexedDB / localStorage / Service Worker 缓存）由用户浏览器持有，卸载插件不会清除；可在 QRSend 的「存储」页面或浏览器站点数据设置中清理。

## 参考

- 上游项目：QRSend（`igarinpiano/qrsend`），Rust 核心 + Svelte Web 前端，Apache-2.0。
- 线格式：上游 `docs/PROTOCOL.md`；抗量子与单次密钥：上游 `docs/POST-QUANTUM.md`。
- 页面能力以随包的 `web/dist` 构建产物为准，本文档不承诺未随包发布的功能细节。
