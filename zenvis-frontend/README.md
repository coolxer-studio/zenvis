# ZenVis — 数据分析应用框架

![图标](doc/banner.jpg)

**ZenVis** 是一个基于配置实现的数据存储、可视化及业务扩展的框架平台，实现在通用的数据分析框架之上构建业务应用。提供智能分析能力，全方位满足数据处理、展示、扩展与深度分析需求。

> ZenVis 跨模块的产品、架构、部署、使用和 API 文档统一维护在[根文档中心](../doc/README.md)。本 README 保留前端模块的独立说明。

---

## 项目简介

ZenVis Frontend 是基于 Vue 3 + TypeScript + Vite 构建的ZenVis的前端项目，提供仪表盘、数据检索、策略管理、数据集成等核心功能模块。  
项目详细介绍请参阅后端仓库：[zenvis-backend](https://gitee.com/coolxer-studio/zenvis-backend)

## 技术栈

| 分类 | 技术 | 版本 |
| :--- | :--- | :--- |
| 框架 | Vue | ^3.5.39 |
| 语言 | TypeScript | ^6.0.3 |
| 构建工具 | Vite | ^8.1.0 |
| 状态管理 | Pinia | ^3.0.4 |
| 路由 | Vue Router | ^5.1.0 |
| UI 框架 | Element Plus | ^2.14.2 |
| 图表库 | ECharts | ^5.6.0 |
| 富文本编辑器 | WangEditor | ^5.1.23 |
| 编辑器 | Monaco Editor | ^0.52.2 |

## 项目结构

```
src/
├── assets/          # 静态资源
│   ├── font/        # 字体文件
│   ├── images/      # 图片资源
│   ├── styles/      # 全局样式
│   └── svg-icon/    # SVG 图标
├── components/      # 公共组件
│   ├── echarts/     # 图表组件
│   └── layout/      # 布局组件
│       └── components/  # 布局子组件
├── router/          # 路由配置
├── service/         # API 服务
│   ├── api/         # 接口定义
│   └── request-wrapper.ts  # 请求封装
├── stores/          # 状态管理
│   ├── modules/     # Store 模块
│   └── plugins/     # Pinia 插件
├── types/           # 全局类型定义
├── utils/           # 工具函数
└── views/           # 页面视图
    ├── about/       # 关于页面
    ├── dashboard/   # 仪表盘
    ├── dih/         # 数据集成处理
    ├── external-app/ # 外部应用
    ├── integrated/  # 集成页面
    ├── login/       # 登录页面
    ├── low-code-app/ # 低代码应用
    ├── low-code-page/ # 低代码页面
    ├── pages-error/ # 错误页面
    ├── policy/      # 策略管理
    └── retrieval/   # 数据检索
```

## 快速开始

### 环境要求

- **Node.js**: ^20.19.0 或 >= 22.12.0
- **Yarn**: 推荐使用 Yarn 作为包管理器

### 安装依赖

```bash
# 安装 Yarn（如未安装）
npm install -g yarn --registry=https://registry.npmmirror.com

# 安装项目依赖
yarn install
```

### 开发模式

```bash
# 启动开发服务器
yarn server:dev
```

访问 http://localhost:8090 查看应用。

### 生产构建

```bash
# 构建生产版本
yarn build:pro
```

#### Docker 构建启动

```bash
# 构建镜像
docker build -t zenvis-frontend:latest .

# 运行容器
docker run -d -p 11000:11000 zenvis-frontend:latest
```

#### 使用构建脚本

```bash
# 使用构建脚本构建（默认不推送镜像）
./build.sh

# 构建并推送镜像到 Docker Registry
PUSH_IMAGE=true ./build.sh

```

## 可用脚本

| 脚本 | 说明 |
| :--- | :--- |
| `yarn server:dev` | 启动开发服务器（开发模式） |
| `yarn server:pro` | 启动开发服务器（生产模式） |
| `yarn build` | TypeScript 检查 + 构建 |
| `yarn build:dev` | 构建开发版本 |
| `yarn build:dev-tsc` | TypeScript 检查 + 构建开发版本 |
| `yarn build:pro` | 构建生产版本 |
| `yarn build:pro-tsc` | TypeScript 检查 + 构建生产版本 |
| `yarn lint` | ESLint 检查 `src` 下的 Vue/TypeScript 文件 |
| `yarn preview` | 预览构建结果 |
| `yarn test` | TypeScript 类型检查 + Node 单元测试 |
| `yarn test:unit` | 只运行 Node 单元测试 |
| `yarn test:e2e` | 运行 Playwright E2E 测试 |

## 环境变量

项目使用 `.env` 文件管理环境变量：

| 变量名 | 说明 |
| :--- | :--- |
| `VITE_BASE_URL` | 浏览器侧统一代理前缀；当前为 `/zenvis` |
| `VITE_BASE_API` | API 目标地址 |

### 环境配置文件

- `.env` - 默认环境变量
- `.env.development` - 开发环境配置
- `.env.production` - 生产环境配置

## 代码规范

### Prettier

项目使用 Prettier 进行代码格式化（配置在 `prettier.config.js`）。

### ESLint

项目使用 ESLint 进行代码质量检查，组合 Vue、TypeScript 和 Prettier 插件规则。

## 构建配置

### Vite 配置要点

- **开发服务器端口**: 8090
- **开发代理**: `/zenvis` 转发至 `VITE_BASE_API`，并在转发前去掉该前缀
- **构建输出目录**: `dist/`
- **资源目录**: `static/`
- **代码压缩**: Terser
- **Gzip 压缩**: 自动生成 `.gz` 文件

### 路径别名

```typescript
@  -> src/
@c -> src/components/
@a -> src/assets/
@r -> src/router/
@t -> src/types/
@v -> src/views/
@u -> src/utils/
```

## 贡献指南

欢迎提交 Issue 和 Pull Request！  
贡献指南参考 [CONTRIBUTING.md](CONTRIBUTING.md)

---

## 许可证

Apache 2.0 License

---

## 联系方式

如有问题或建议，欢迎通过以下方式联系：

- 提交 Issue
- 发送邮件：<coolxer@163.com>

---
