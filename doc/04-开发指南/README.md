# 开发指南

本主题合并后端、前端、检索和性能研发资料：

- [完整后端开发说明](development.md)
- [前端全局检索模块开发指南](全局检索模块开发指南.md)
- [前端无感优化基线](前端无感优化基线.md)
- [测试记录](test.md)

## 工程结构

```text
zenvis/
├── README.md
├── doc/                                      # ZenVis 整体文档
├── zenvis-backend/                           # Spring Boot 平台服务
├── zenvis-frontend/                          # Vue 管理控制台
├── zenvis-plugin/                            # 插件源码与打包工具
├── zenvis-plugin-community/                  # 社区与客户场景插件集合
├── zenvis-business-service-spring-boot-starter/
└── agent-skills/                             # Agent Skill
```

后端、前端、内置插件及部分社区插件目录拥有独立 Git 工作树。提交前应从实际 `.git` 边界分别检查根仓库和受影响子仓库，避免把其他工作树的未提交内容混入变更。

## 技术栈基线

### 后端

| 项目 | 当前基线 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.2.0 |
| Spring AI | 1.1.0-M4 |
| OpenAPI | SpringDoc 2.3.0 |
| 数据 | MySQL、ClickHouse、Redis、Redis Stack |
| 构建 | Maven |

### 前端

| 项目 | 当前清单 |
| --- | --- |
| Vue | `^3.5.39` |
| TypeScript | `^6.0.3` |
| Vite | `^8.1.0` |
| Vue Router | `^5.1.0` |
| Pinia | `^3.0.4` |
| Element Plus | `^2.14.2` |
| ECharts | `^5.6.0` |
| Node.js | `^20.19.0 || >=22.12.0` |

版本以各模块的 `pom.xml` 和 `package.json` 为准。

## 后端开发

### 分层

```text
controller → service → repository/DAO → MySQL 或 ClickHouse
                     └→ 外部服务、MCP、模型、Vectum
```

- Controller 负责 HTTP、参数校验和响应封装；
- Service 负责业务规则、权限边界和事务；
- Repository/DAO 负责持久化；
- DTO/VO 使用明确类型，JSON 统一转为 `snake_case`；
- 所有业务响应使用 `ResponseWrap<T>`。

### 常用命令

```bash
cd zenvis-backend
mvn clean compile
mvn test
mvn spring-boot:run
mvn clean package -DskipTests
```

后端测试使用 Spring Boot Test 和 H2 等测试依赖。涉及插件、Retrieval、MCP、权限或迁移的改动应运行相应服务测试，而不是只编译。

### 配置

本地默认激活 `dev` profile。敏感值通过环境变量覆盖。新增配置时：

1. 为开发与生产环境提供一致的键；
2. 给安全相关配置选择安全默认值；
3. 更新整体文档与部署模板；
4. 为配置绑定或关键分支增加测试。

## 前端开发

### 目录职责

| 目录 | 作用 |
| --- | --- |
| `src/views` | 页面与业务组件 |
| `src/components` | 公共组件和布局 |
| `src/service/api` | 领域 API 封装 |
| `src/service/request-wrapper.ts` | Axios、错误处理和响应解包 |
| `src/stores` | Pinia 状态 |
| `src/types` | 请求、响应和领域类型 |
| `src/utils` | URL、认证、存储等工具 |
| `public/amis` | AMIS 运行时静态资源 |

### 常用命令

```bash
cd zenvis-frontend
yarn install
yarn test
yarn lint
yarn build:pro
yarn server:dev
```

项目没有 Jest/Vitest。`yarn test` 是 `vue-tsc --noEmit`，功能回归需结合生产构建和浏览器检查。

### 代码约定

- Vue 组件优先使用 `<script setup lang="ts">`；
- 组件和 TypeScript 文件使用 kebab-case；
- 内部模块使用 `@`、`@c`、`@v`、`@u` 等别名；
- API 类型集中在 `src/types`，不要在组件中重复声明近似结构；
- 新接口通过 `src/service/api` 和 `request<R>()` 访问；
- 面向用户的异常使用统一消息组件；
- 新 SCSS 优先使用 scoped 样式和 `:deep()`；
- 不替换项目现有 Pinia persist 插件。

### URL 与 API

前端业务接口写后端路径，例如 `/api/v1/retrieval/do`。Axios `baseURL` 为 `/zenvis`，最终浏览器请求为 `/zenvis/api/v1/retrieval/do`。

静态资源和 iframe URL 应通过 `src/utils/url.ts` 处理。禁止 `javascript:`、`data:`、`blob:`、`file:` 和协议相对 URL；跨源 iframe 应配置允许来源。

## Retrieval 开发

### 状态所有权

`src/views/retrieval/index.vue` 是唯一完整查询状态源：

- `filter.vue` 只编辑条件草稿；
- `table.vue` 只展示数据并发出分页、排序和列变更；
- API、类型和取消请求分别位于 service、types 和 request wrapper。

不要让子组件各自加载规则或触发查询，否则会产生重复请求和过期状态覆盖。

### 初始化流程

新查询：

```text
entity/list → display/attribute/list → retrieval/do
```

已保存规则：

```text
rule/detail → 原子应用 config/Meta/issues
            → 有效规则执行 retrieval/do
            → 失效规则停留在编辑态
```

### 并发控制

- `loadGeneration` 防止旧实体或规则详情覆盖新选择；
- `dataRequestId` 防止旧数据响应覆盖最新查询；
- `AbortController` 主动取消旧请求；
- 取消使用 silent 模式，不弹出错误；
- 旧请求的 `finally` 不能关闭新请求 loading。

### 可编辑条件

父组件回写值与草稿相同时，不要重建条件数组。否则输入组件会卸载重建，导致文字消失、焦点丢失和自动补全异常。可编辑行使用稳定 key，不使用数组下标。

## AI 与 MCP 开发

- 普通问答保持无工具边界；
- 业务 Agent 通过 `AgentMcpToolService` 获取工具；
- 新本地工具必须声明默认审批策略与风险；
- 只读工具和写入工具使用不同策略；
- 参数、结果与错误在审计前脱敏和截断；
- 流式响应的审批、完成和错误事件必须保持协议兼容；
- 数据可视化 Agent 不直接访问数据库、不生成任意 SQL、不加载外部 MCP；变更其本地工具白名单时必须区分查询工具与受控配置/看板/菜单写工具，并为写操作保留 `ASK/HIGH` 审批。

详细设计见 [AI 与数据智能](../07-AI与数据智能/README.md)。

## 插件开发

插件代码、Meta、Vector、UI、看板和文档需要同步演进。平台通用契约见[插件开发与集成](../06-插件开发与集成/README.md)，插件专项说明以对应 `plugin-*/README.md` 与 `00_doc/` 为准。

## 数据库变更

### 平台表

平台基础表由后端 JPA 管理。修改实体前确认多环境兼容和已有数据迁移风险。

### 插件表

插件 MySQL 迁移位于 `03_api/migrations/mysql/`：

- 使用 `Vnnn__description.sql`；
- 已执行版本不得修改；
- 新变更必须增加更高版本；
- 卸载插件不删除表和迁移历史。

ClickHouse 表结构由 Meta `auto_create` 和实际部署共同决定，属性、表列和写入任务需要一一校验。

## 前端性能基线

AMIS、Monaco、ECharts 和 DIH 体积较大，优化应保持按需分包，不将异步模块重新放进入口预加载。性能修改至少验证：

- Hash 路由、菜单参数和浏览器前进后退；
- 登录、外部 Token、退出和权限菜单；
- 看板、检索、低代码、外部 iframe 与 DIH；
- API 路径、方法、请求字段和事件时序；
- `yarn test`、`yarn lint` 与 `yarn build:pro`。

不要仅以构建体积下降判断成功，必须确认行为和安全边界不变。

## 提交前检查

### 后端

```bash
mvn test
```

### 前端

```bash
yarn test
yarn lint
yarn build:pro
```

### 通用

1. 检查 `git diff --check`；
2. 确认没有提交凭据、运行数据、构建产物和 `.DS_Store`；
3. 更新 API、配置、部署或用户行为对应的整体文档；
4. 分别检查根仓库及受影响子仓库状态；
5. 对登录、检索、看板、插件和 DIH 的相关路径做定向回归。
