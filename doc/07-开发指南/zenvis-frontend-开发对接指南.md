# `zenvis-frontend` 开发对接指南

## 项目定位

`zenvis-frontend` 是 ZenVis 管理控制台和业务应用承载层，提供登录认证、权限菜单、首页与看板、全局检索、配置管理、低代码应用、静态 HTML、外部应用和 DIH 工作台。

前端不直接访问数据库，也不自行推导插件后端契约。页面通过后端 `/api/v1/**`、插件动态 API 和受控静态配置工作。

## 上下游关系

- 上游：`zenvis-backend` 的 `/api/v1/**`、插件动态 API、菜单、看板和 `open_config` 页面配置。
- 下游：浏览器用户、嵌入式 AMIS/静态 HTML/外部应用，以及 Playwright 自动化场景。
- 变更边界：请求字段或流式事件变化需要同步后端；菜单、配置索引或详情路由变化需要同步插件；代理和基础路径变化需要同步 `deploy`。

## 技术基线

| 分类 | 当前版本或方案 |
| --- | --- |
| Vue | `^3.5.39`，Composition API |
| TypeScript | `^6.0.3` |
| Vite | `^8.1.0` |
| Vue Router | `^5.1.0`，Hash 路由 |
| Pinia | `^3.0.4`，项目自定义持久化插件 |
| Element Plus | `^2.14.2` |
| ECharts | `^5.6.0` |
| Node.js | `^20.19.0 || >=22.12.0` |
| 代码检查 | ESLint 8、Prettier |
| 测试 | `vue-tsc`、Node test、Playwright |

版本和脚本以 `package.json` 为准。

## 目录职责

```text
zenvis-frontend/
├── package.json
├── vite.config.ts
├── playwright.config.ts
├── e2e/                              # Playwright 场景
├── public/
│   └── amis/                         # AMIS 运行时静态资源
└── src/
    ├── assets/
    ├── components/                   # 公共组件和布局
    ├── composables/
    ├── router/                       # 路由和守卫
    ├── service/
    │   ├── api/                      # 领域 API 类
    │   └── request-wrapper.ts        # Axios、响应解包和错误处理
    ├── stores/                       # Pinia Store 和持久化插件
    ├── types/                        # 请求、响应和领域类型
    ├── utils/
    └── views/
        ├── dashboard/
        ├── dih/
        ├── external-app/
        ├── login/
        ├── low-code-app/
        ├── low-code-page/
        ├── retrieval/
        └── system/
```

页面状态放在所属页面或 Store，跨页面可复用逻辑放在 composable；不要在公共组件中隐藏业务请求。

## 环境与启动

```bash
cd zenvis-frontend
yarn install
yarn server:dev
```

开发服务器默认使用 `8090`。环境变量：

| 变量 | 说明 |
| --- | --- |
| `VITE_BASE_API` | 开发代理目标，通常为 `http://localhost:11001` |
| `VITE_BASE_URL` | 浏览器统一前缀，当前为 `/zenvis` |

Vite 把浏览器 `/zenvis/api/v1/...` 请求转发到后端，并在代理时移除 `/zenvis`。业务 API 定义仍写 `/api/v1/...`。

## 构建、检查与测试

```bash
# 开发/生产模式服务器
yarn server:dev
yarn server:pro

# 类型检查 + 当前 Node 单元测试
yarn test

# 只运行 Node 单元测试
yarn test:unit

# ESLint
yarn lint

# 生产构建
yarn build:pro

# 类型检查 + 生产构建
yarn build:pro-tsc

# Playwright E2E
yarn test:e2e

# 预览 dist
yarn preview
```

当前 `yarn test` 先执行 `vue-tsc --noEmit`，再运行报表文档格式和数据可视化菜单路由的 Node test。Playwright 独立运行，可能需要浏览器和被测服务。

## 核心开发流程与代码约定

### Vue 与 TypeScript

- 新组件优先使用 `<script setup lang="ts">`。
- Vue 和 TypeScript 文件使用 kebab-case；类型使用 PascalCase。
- Props 使用 `defineProps` 与 `withDefaults`，事件使用带类型的 `defineEmits`。
- 请求和响应类型优先放入 `src/types/type-*.ts`，不要在多个组件中复制近似接口。
- 新代码避免无理由使用 `any`；请求通过 `request<R>()` 显式声明响应类型。

### API 与错误

- 每个领域在 `src/service/api/api-<domain>.ts` 中维护 API 类。
- GET 参数放在 `config.params`，其他请求体放在 `config.data`。
- 通用错误由 `request-wrapper.ts` 和 `ElMessage` 处理。
- 取消请求属于正常控制流时使用 silent 模式，不弹通用错误。
- URL、iframe 和静态资源地址通过 `src/utils/url.ts` 处理，拒绝不安全协议。

### Store 与样式

- 继续使用项目现有 Pinia `persist` 配置，不引入 `pinia-plugin-persistedstate`。
- 新样式优先使用 scoped SCSS，穿透作用域使用 `:deep()`。
- 格式以 `prettier.config.js` 为准，不在单个功能中修改全局格式规则。

### 路径别名

| 别名 | 目录 |
| --- | --- |
| `@` | `src/` |
| `@c` | `src/components/` |
| `@a` | `src/assets/` |
| `@r` | `src/router/` |
| `@t` | `src/types/` |
| `@v` | `src/views/` |
| `@u` | `src/utils/` |

## 全局检索开发

### 页面职责

| 文件 | 职责 |
| --- | --- |
| `src/views/retrieval/index.vue` | 唯一完整查询状态源；加载实体和规则、发起检索、保存删除规则、处理并发 |
| `src/views/retrieval/components/filter.vue` | 编辑普通条件或高级表达式；不自行加载规则或请求数据 |
| `src/views/retrieval/components/table.vue` | 表格、展示列、分页和排序事件 |
| `src/service/api/api-retrieval.ts` | Retrieval API 和取消请求参数 |
| `src/types/type-retrieval.ts` | 请求、规则详情、失效项和表格状态类型 |
| `src/service/request-wrapper.ts` | `AbortSignal` 和静默错误处理 |

不要在 `filter.vue` 或 `table.vue` 再维护一份完整查询配置。子组件只保存编辑所需的短生命周期草稿。

### 核心状态

| 状态 | 含义 |
| --- | --- |
| `currentFilter` | 当前实体、模式、条件、表达式和展示字段 |
| `activeRule` / `activeRuleName` | 当前保存规则；`0` 表示新建状态 |
| `entityList` / `attributeList` | 当前可用 Meta |
| `loadedConfig` / `loadedIssues` | 规则原始配置和问题 |
| `currentSorter` | 当前实体的排序字段和方向 |
| `tableState` | 表格列、数据、loading 和分页 |

### 初始化与规则加载

新建状态：

```text
GET /retrieval/entity/list
        ↓
GET /retrieval/display/attribute/list?entity=...
        ↓
POST /retrieval/do
```

页面使用第一个实体和默认展示列；如果没有默认展示列，至少选择一个有效字段。

已保存规则：

```text
GET /retrieval/rule/detail?id=...
        ↓
原子应用 config + entity_list + attribute_list + issues
        ↓
有效规则：POST /retrieval/do
失效规则：停留在编辑态
```

不要恢复并行调用多个带 `rule_id` 的实体和属性接口。响应顺序不确定，会把不同规则或实体的状态混合。

### 并发和取消

- `loadGeneration` 防止旧实体或规则详情覆盖新选择。
- `dataRequestId` 防止旧数据响应写入最新表格。
- `AbortController` 主动取消旧请求。
- 取消请求使用 `{ silent: true }`，并先检查 `axios.isCancel(error)`。
- 旧请求的 `finally` 不能关闭新请求的 loading。
- 切换实体或规则前调用 `clearDataState()`，清除旧数据、列、排序和分页。

### 可编辑条件

输入状态回路：

```text
输入字符
  → 更新 draft
  → emit update:modelValue
  → 父组件更新 currentFilter
  → props.modelValue 回写 filter.vue
```

当回写配置与草稿相同时，`modelMatchesDraft()` 必须直接返回，不能重建整个 `criteria_list`。否则输入组件会卸载重建，造成文字消失、焦点丢失和自动补全异常。可编辑行使用稳定 key，不使用数组下标。

### 展示列、排序和字段

- 表格 `dataIndex` 和响应键使用 Meta 逻辑字段 `name`。
- 不使用 `label`、`display_name` 或物理 `column_name` 取值。
- 展示字段至少保留一个，变更后回到第一页。
- 实体切换时清空排序，查询使用 `sort_by` 和 `order`。
- 分页使用 `page`、`size`，前端选项为 10、20、50、100，当前后端上限为 100。
- `search_type` 为 `date`、`datetime`、`number` 时使用对应输入控件。
- `auto_complete` 字段调用自动补全接口。
- `json` 和 `array` 按结构化展示约定渲染。

### 失效规则

规则详情包括 `config`、`status`、`issues`、`entity_list` 和 `attribute_list`。有未处理问题时禁止搜索和保存：

- 缺失条件和展示字段可以移除，但必须保留有效展示字段。
- 高级表达式缺字段时保留原文，用户修改后再重新判断。
- `LEGACY_SQL_DISABLED` 需要重新编辑旧表达式。
- 缓存规则不存在、无权访问或失效时，清除 `__rule__` 并进入新建状态。

### 保存与删除

- 新建：`POST /retrieval/rule/create`；
- 更新：`POST /retrieval/rule/update`，标准字段为 `id`，后端兼容旧 `rule_id`；
- 删除：`POST /retrieval/rule/delete`，提交 `{ id }`。

`criteria_list: []` 表示清空普通条件，`display_list: []` 非法。保存成功后同步规则列表、当前规则和 `__rule__` 缓存；删除当前规则时同时清除三处状态。

### Retrieval 回归

1. 有效规则只有一次详情请求和一次数据请求。
2. 失效规则不自动发起数据请求。
3. 快速切换规则只显示最后一次选择。
4. 取消请求不弹错误，loading 正确结束。
5. 条件输入的文字、焦点和行 key 稳定。
6. 新建、更新、删除、恢复、分页、排序、展示列和自动补全正常。

常见错误：

| 改动 | 后果 |
| --- | --- |
| 子组件在 Meta 回调中查询 | 一个规则触发多次 `/do` |
| 并行加载规则的实体和属性 | 新旧状态混合 |
| 不检查 generation/request ID | 慢响应覆盖最新选择 |
| 所有 `finally` 都关闭 loading | 旧请求提前关闭新请求 |
| 每次 props 回写都重建条件 | 输入和焦点丢失 |
| 表格使用物理列或展示名 | 表格空列 |
| 切换实体后保留排序字段 | 后端返回字段不存在 |
| 自动执行失效缓存规则 | 启动即报错 |

## DIH 与富消息开发

DIH 包含流式消息、会话、附件、审批、图表、HTML 预览、数据接入、数据可视化和报表工作台。修改时必须保持：

- 新旧会话与中止生成；
- 流式文本和结构化事件顺序；
- 工具审批与会话授权；
- `SafeEcharts` 图表快照和固定刷新接口；
- HTML/Markdown 清理和 iframe 安全；
- 工作流卡片的精确 action 定位。

### 富消息手工测试

以下提示词可在 `/service/dih` 或右下角 AI 浮窗验证渲染。

代码块：

````text
请严格按下面内容原样回复，不要额外解释：

这是代码块测试。

```typescript
type User = {
  id: string;
  name: string;
};

function greet(user: User) {
  return `Hello, ${user.name}`;
}
```
````

提示卡：

````text
请严格按下面内容原样回复，不要额外解释：

```zenvis:notice
{"title":"配置检查提醒","content":"当前操作涉及插件配置变更，请确认名称、菜单路径和推送任务配置是否正确。","level":"warning"}
```
````

确认卡：

````text
请严格按下面内容原样回复，不要额外解释：

```zenvis:confirm
{"title":"是否生成插件产物","content":"确认后只记录已确认状态，不会真正执行生成或导出动作。","action":"plugin.generate.preview","target":"demo-plugin"}
```
````

混合消息：

````text
请严格按下面内容原样回复，不要额外解释：

下面是本次任务摘要：

- 已生成配置草案
- 已准备预览步骤
- 等待用户确认

```json
{
  "pluginName": "demo-plugin",
  "menu": "/service/demo",
  "enabled": true
}
```

```zenvis:notice
{"title":"预览可用","content":"配置草案已准备完成，可以先预览再确认。","level":"success"}
```

```zenvis:confirm
{"title":"确认应用配置草案","content":"确认后仅记录状态，不会修改真实系统配置。","action":"plugin.config.approve","target":"demo-plugin"}
```
````

非法 JSON 回退：

````text
请严格按下面内容原样回复，不要额外解释：

```zenvis:confirm
{"title":
```
````

预期：非法 JSON 不渲染成确认卡，而是回退为普通 Markdown/代码内容，且页面不崩溃。

历史文档还使用过以下深度思考提示词：

```text
请开启深度思考，分析 17*23 等于多少。先展示思考过程，最后只给出最终结果。
```

当前实现应验证 `deep_think` 请求和最终响应，不应依赖模型暴露隐藏推理过程。

## 前端性能与无感优化

### 行为基线

性能优化不得改变：

- Hash 路由、路由名、菜单参数和前进后退；
- 登录、外部 Token、退出、权限菜单和登录失效跳转；
- 看板、检索、策略和系统配置；
- 低代码应用、低代码页面、外部应用、静态 HTML 和 AMIS 地址；
- DIH 会话、流式响应、附件、审批、图表和 HTML 预览；
- API URL、方法、字段、状态含义和事件时序。

### 2026-07-17 历史基线

以下数字是当时优化记录，用于比较方法，不代表当前每次构建必须得到相同结果：

- Node.js 要求：`^20.19.0 || >=22.12.0`；
- 优化前 `dist` 约 95 MB，`public/amis` 源资源约 49 MB；
- 优化前入口预加载 JavaScript 约 2.07 MB gzip；
- 优化前 Monaco、Element Plus、Vendor、ECharts 为主要入口预加载项；
- 优化后 `dist` 仍约 95 MB，兼容资源未删除；
- 优化后入口仅预加载约 0.42 KB gzip 的运行时；
- 应用入口连同预加载运行时约 423.50 KB gzip；
- 相比历史优化前入口下降约 79.5%；
- Monaco、ECharts 和 DIH 主体保留独立异步分包。

历史优化前构建约 15.07 秒，优化后约 11.69 秒。时间和体积受机器、依赖缓存和当前代码影响，新的优化必须重新记录环境、命令和产物。

### 性能回归

1. 运行 `yarn test`、`yarn lint` 和 `yarn build:pro`。
2. 检查 `dist/index.html` 的 `modulepreload`，普通首屏不应预加载 Monaco、ECharts 或 DIH 主体。
3. 检查登录、看板、检索、低代码、外部 iframe 和 DIH。
4. 比较控制台错误、请求数量、参数和事件顺序。
5. 不以体积下降单独判断成功，必须验证行为和安全边界。

## 扩展点

- 新增平台页面：在 `src/views` 建立领域目录，通过路由、菜单权限、API 类和类型定义接入。
- 新增插件页面：优先使用插件 `04_ui`、`05_dashboard` 和 `08_menu` 契约，不把客户页面硬编码进平台前端。
- 新增 API：在 `src/service/api` 集中封装，在 `src/types` 定义契约，并保持 `/api/v1/**`、`snake_case` 与统一错误处理。
- 新增 DIH 富消息：同步后端事件格式、前端解析器、渲染组件、非法载荷降级和对应单元/浏览器测试。
- 新增性能分包：保持懒加载边界和现有行为，记录当前机器、命令、产物与回归结果。

## 常见问题

### `yarn test` 与文档描述不一致

以 `package.json` 为准。当前命令包含 TypeScript 检查和两个 Node test，不再只是 `vue-tsc`。

### API 直接访问后端成功，前端却 404

检查 `VITE_BASE_URL`、`VITE_BASE_API` 和请求封装。业务代码使用后端路径，浏览器请求由代理增加 `/zenvis` 上下文。

### 路由跳转后页面或菜单不匹配

检查 Hash 路由名称、菜单 `params`、低代码 `config_index` 和浏览器前进后退，不要用硬刷新掩盖状态问题。

### iframe 或静态页面无法加载

检查 URL 规范化、允许来源、后端静态路径和 CSP。不要放宽 `javascript:`、`data:`、`blob:`、`file:` 或协议相对 URL。

## 交付检查

- `yarn test`、`yarn lint`、`yarn build:pro` 通过；
- 需要浏览器流程时运行相应 Playwright 或手工回归；
- API 类型、snake_case 字段和服务层封装保持一致；
- 登录、路由、菜单、检索、看板、低代码和 DIH 受影响路径已验证；
- 没有提交 `dist`、`node_modules`、`.DS_Store` 或本机环境文件；
- 在 `zenvis-frontend` 独立工作树检查 `git status` 与 `git diff --check`。

## 关联文档

- [全局检索产品说明](/01-产品理念与使用/全局检索.md)
- [数据与检索架构](/06-架构设计/数据与检索架构.md)
- [数据检索与实体 REST API](/08-API参考/RestfulAPI/数据检索与实体.md)
- [AI 与数据智能](/04-AI与数据智能/README.md)
- [`zenvis-backend` 开发对接指南](/07-开发指南/zenvis-backend-开发对接指南.md)
