# QRSend 二维码离线传输插件

## 概述

本插件集成 [QRSend](https://github.com/igarinpiano/qrsend)——一个**通过二维码流传输任意数据**的工具。发送方把文本、文件或文件夹切分、压缩并做喷泉编码（RaptorQ）后，在屏幕上持续输出二维码流；接收方使用摄像头（或已录制的视频、屏幕捕获）读取其中任意足够大的子集，即可完整重建原始数据。

整个链路由屏幕与摄像头承载，**不需要网络、账号或配对服务器**：适用于隔离网、内外网之间、临时工位等无法建立数据通道的场景。

支持的能力包括：

- **任意大小与可续传**：数据分段并做喷泉编码，传输可以持续很久、可以被中断，并可在之后继续；接收方给出 *resume code*，发送方据此只补发缺失部分。
- **抗丢帧**：漏读部分二维码不影响结果，任意约 `K+ε` 个符号即可重建一个分段。
- **隐私与校验**：设备配对后使用端到端加密（age）与签名清单，每个分段和文件都用 BLAKE3 校验，路径做净化、解压有上限。
- **多种收发通道**：摄像头、视频文件、屏幕捕获接收；发送端支持每帧多个二维码、彩色码、双向回传、局域网加速等。
- **浏览器本地处理**：数据在浏览器私有存储（OPFS）中流式读写，**不上传服务器**；设备私钥为不可导出的 WebCrypto 密钥。
- **中英双语界面**：跟随浏览器语言，可在页脚切换。

## 插件信息

| 项目 | 值 |
|------|-----|
| 插件名称 | QRSend 二维码离线传输 |
| 包名 | `com.coolxer.plugin.qrsend` |
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

QRSend 以单页应用（SPA）形式打包，位于 `04_ui/html-page/qrsend/`。安装时由插件 UI 安装流程将整个 `04_ui/html-page/` 目录拷贝到运行时的 `html-page/com.coolxer.plugin.qrsend/` 路径下，**不依赖看板配置**。

| 项目 | 值 |
|------|-----|
| HTML 源路径 | `04_ui/html-page/qrsend/index.html` |
| 运行时访问路径 | `/zenvis/html-page/com.coolxer.plugin.qrsend/qrsend/index.html` |
| 应用内页面 | `#/send` 发送、`#/receive` 接收、`#/inbox` 收件箱、`#/devices` 设备、`#/preview` 功能预览、`#/storage` 存储 |

页面内的资源引用均为相对路径（`./assets/...`、`./icon.svg`、`./sw.js`），可正确工作在 `/zenvis/html-page/<package_name>/qrsend/` 这一子目录部署形态下。

### 菜单

| 菜单项 | 类型 | 路由参数 |
|--------|------|----------|
| 二维码离线传输 | `HTML_PAGE` | `/zenvis/html-page/com.coolxer.plugin.qrsend/qrsend/index.html` |

菜单点击后，前端以 iframe 方式加载 QRSend 页面，后端自动对 `params` 进行 Base64-URL 编码，前端 `html-page` 页面解码后作为 iframe `src`。`HTML_PAGE` 的 `params` 必须包含 `/zenvis` 上下文前缀。

菜单仅创建入口，**不会自动为角色授权**；安装完成后需在「角色权限」中为相应角色分配该菜单。

## 运行环境与限制

### 安全上下文（HTTPS 或 localhost）

QRSend 依赖浏览器安全上下文中才能使用的特性：WebCrypto（设备密钥）、OPFS（大文件流式存储）、Service Worker（离线缓存）。因此：

- 通过 **HTTPS** 访问 Zenvis（或 localhost 开发环境）时功能完整；
- 若通过明文 HTTP 的局域网 IP 访问，浏览器会禁用上述特性，加密、设备配对与大文件流式存储将不可用。

平台通过 Spring `ResourceHandler` 直接提供 `/zenvis/html-page/**` 静态资源，可用性取决于部署网关的协议，不由插件控制。

### 摄像头与麦克风

- **摄像头**用于 `#/receive` 的实时扫描，**麦克风**用于功能预览中的「声音反馈」。
- 页面运行在平台 `html-page` iframe 中，iframe 使用 `sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-downloads"`，且与平台同源；浏览器对 `camera` / `microphone` 的默认授权白名单为同源，因此同源 iframe 内可正常申请权限。
- 首次使用需在浏览器中授予摄像头/麦克风权限；被拒绝时接收页会给出提示，可改用「从视频文件接收」或「从屏幕捕获接收」。

### 主线程之外的处理与 Worker/WASM

- 引擎在 Web Worker 中运行，扫描在独立 Worker 中运行，避免阻塞界面。
- WASM 通过 `script-src 'self' 'wasm-unsafe-eval'`（页面内置 CSP）加载，**不依赖 `SharedArrayBuffer`**，因此**不需要** COOP/COEP 跨域隔离响应头。
- 需要浏览器支持 WebAssembly、ES Module Worker 与 AudioWorklet（声音反馈可选，缺失时仅该功能不可用）。

### 数据与存储

- 传输内容在浏览器本地处理，**不经过 Zenvis 服务端**，也不写入平台存储。
- 大文件通过 OPFS 流式读写；OPFS 不可用时（如部分隐私模式）回退到内存，受浏览器内存限制。
- 「存储」页面可查看并清理浏览器保留的接收会话、遗留文件与日志；设备 ID 与已信任设备不在清理范围内。
- 「Copy log」导出的诊断日志只含步骤、时序、计数与连接状态，**不含文件名、内容、设备 ID 或网络地址**。

### 页面内置 CSP 与网络访问

`index.html` 内置 `Content-Security-Policy`，其中 `connect-src 'self'`、`default-src 'self'`，意味着页面本身**不发起任何外部网络请求**。局域网加速使用 WebRTC 在两端之间直连，不经过第三方服务器；该能力由功能预览开关控制，默认关闭。

## 目录结构

```
plugin-qrsend/
├── index.json                  # 插件描述（名称、包名、版本、图标）
├── README.md                   # 插件说明
├── icon.png                    # 插件图标（100×100 PNG）
├── 00_doc/
│   └── README.md               # 文档入口
├── 01_meta/                    # 无 Meta 定义
├── 02_push-task/               # 无推送任务
├── 03_api/                     # 无动态 API
├── 04_ui/
│   └── html-page/
│       └── qrsend/             # QRSend 静态资源（index.html + assets + WASM + PWA）
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

## 使用方式

1. **发送**：进入「发送」，选择文本、文件或文件夹；可选加密到已信任设备。发送端持续播放二维码流。
2. **接收**：在另一台设备进入「接收」，用摄像头对准发送端屏幕（或用已录制视频 / 屏幕捕获）。
3. **续传**：若接收端提示缺少数据，会给出 `QSR1-XXXXXXXX` 形式的 resume code；在发送端填入后只补发缺失部分。
4. **配对（可选）**：在「设备」中互相添加对方的设备 ID 并核对指纹，之后即可端到端加密传输。
5. **高级能力**：页脚「功能预览」可开启双向传输、自动调速、声音反馈、局域网加速、彩色码、屏幕接收等实验特性，默认全部关闭。

## 部署步骤

1. 执行 `build.sh` 构建插件包：
   ```bash
   cd zenvis-plugin-qrsend
   ./build.sh
   ```
2. 在 Zenvis 插件管理页面上传生成的 `com-coolxer-plugin-qrsend.tar.gz`。
3. 安装完成后，在「角色权限」中为需要的角色分配「二维码离线传输」菜单。
4. 从左侧菜单「二维码离线传输」进入 QRSend。

## 升级与卸载

- **升级**：本插件仅包含静态资源，升级会整体替换运行时 `html-page/com.coolxer.plugin.qrsend/` 目录内容；菜单通过稳定的 `type + params` 匹配键保留菜单 ID 与角色授权。
- **卸载**：会移除该目录下安装的静态资源与插件菜单；本插件不拥有任何 ClickHouse 表或 MySQL 迁移，因此不涉及数据清理或回滚。
- 浏览器侧残留在用户的浏览器存储（OPFS / IndexedDB / localStorage）中，卸载插件不会清除；如需彻底清理，可在 QRSend 的「存储」页面删除，或清理该站点数据。

## 已知问题

- 上游 QRSend 处于早期开发（v0.1），线格式定义见其 `docs/PROTOCOL.md`，可能仍然变化；不同版本之间的互通以随包资源为准。
- 随包资源为上游 `web/dist` 的构建产物，界面语言与功能以其为准，本文档不承诺未随包发布的字段级功能。
- 上游 `dist` 含 PWA Service Worker，其预缓存作用域为 `./`，即 `html-page/com.coolxer.plugin.qrsend/qrsend/`，不会影响平台其他页面。
- 明文 HTTP 部署下 WebCrypto/OPFS 不可用（见上文「安全上下文」）。

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2026-10-15 | 初始版本；纯前端静态 HTML 工具形态，集成上游 `web/dist` 构建产物（QRSend Web v0.1.13），注册单个 `HTML_PAGE` 菜单，无需看板、Meta、推送任务或动态 API |
