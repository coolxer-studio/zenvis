# AGENTS.md — ZenVis Frontend

## 构建 / 检查 / 测试 命令

```bash
# 安装依赖
yarn install

# 启动开发服务器（端口 8090）
yarn server:dev

# 生产构建
yarn build:pro

# TypeScript 类型检查 + Node 单元测试
yarn test

# 只运行 Node 单元测试
yarn test:unit

# Playwright E2E 测试
yarn test:e2e

# 类型检查 + 生产构建
yarn build:pro-tsc                 # vue-tsc --noEmit && vite build --mode production

# 类型检查 + 开发构建
yarn build:dev-tsc                 # vue-tsc --noEmit && vite build --mode development

# 预览构建产物
yarn preview

# 项目不使用 Jest/Vitest；Node test 文件使用 *.test.mts，E2E 使用 Playwright。
# 格式化通过 Prettier 完成（配置在 prettier.config.js）。
# package.json 提供 lint 脚本，但没有单独的 format 脚本。
```

### 不同环境模式启动

```bash
yarn server:dev    # vite --mode development（默认）
yarn server:pro    # vite --mode production
```

---

## 项目技术栈

- **Vue 3**（Composition API，优先使用 `<script setup>`）
- **TypeScript**（strict 模式，但 `noImplicitAny: false`）
- **Vite 8**（构建工具）
- **Pinia**（状态管理）
- **Vue Router 5**（hash 路由）
- **Element Plus**（UI 框架）
- **ECharts 5**（图表，通过 `vue-echarts`）
- **SCSS / Less**（CSS 预处理器）
- **Prettier**（代码格式化）

---

## 代码风格指南

### Vue 组件

- **优先使用 `<script setup lang="ts">`**（Composition API）。新代码避免使用 `defineComponent` / Options API。
- 组件模板顺序：`<template>` → `<script>` → `<style>`。
- 需要组件名时使用 `defineOptions({ name: 'ComponentName' })`。
- Props：使用 `defineProps<Props>()` + `withDefaults()`。接口定义在 setup 上方。
- Emits：使用 `defineEmits<{ (e: 'event', payload: Type): void }>()`。
- Expose：需要时使用 `defineExpose({ ... })`。

```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';

defineOptions({ name: 'FooWidget' });

interface Props {
  title: string;
  count?: number;
}
const props = withDefaults(defineProps<Props>(), { count: 0 });
const emit = defineEmits<{ (e: 'update', id: string): void }>();
</script>
```

### 文件命名

| 类型            | 命名规则         | 示例                                    |
| --------------- | ---------------- | --------------------------------------- |
| Vue 组件        | `kebab-case.vue` | `base-chart.vue`、`nav-menu.vue`        |
| TypeScript 模块 | `kebab-case.ts`  | `util-time.ts`、`type-user.ts`          |
| 类型定义文件    | `type-<name>.ts` | `type-response.ts`、`type-dashboard.ts` |
| API 服务文件    | `api-<name>.ts`  | `api-user.ts`、`api-system.ts`          |
| Store 模块      | `kebab-case.ts`  | `user.ts`、`dashboard.ts`               |

### 导入规范

- 使用路径别名（定义在 `tsconfig.json` 和 `vite.config.ts` 中）：

  | 别名  | 路径                                           |
  | ----- | ---------------------------------------------- |
  | `@`   | `src/`                                         |
  | `@c/` | `src/components/`                              |
  | `@v/` | `src/views/`                                   |
  | `@u/` | `src/utils/`                                   |
  | `@a/` | `src/assets/`                                  |
  | `@r/` | `src/router/`                                  |
  | `@t/` | `src/types/`（仅 tsconfig 配置）               |

- 同目录下的文件使用相对导入（`./Component`）。
- 导入顺序：
  1. Vue / 框架导入（`vue`、`vue-router`、`pinia`）
  2. 第三方库（`element-plus`、`echarts`、`dayjs` 等）
  3. 内部 `/src` 导入（使用 `@/` 或 `@c/` / `@v/` 等）
  4. 相对导入（`./`、`../`）

```ts
import { ref, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { UserService } from '@/service/api';
import { useUserStore } from '@/stores/modules/user';
import { TStandardResponse } from '@/types/type-response';
import { ls } from '@u/local-storage';
import navMenu from './components/nav-menu.vue';
```

### TypeScript 规范

- 类型和接口使用 PascalCase。响应/实体类型前缀加 `T`（如 `TStandardResponse<T>`、`TLocationStatResponse`）。
- 组件 Props 接口在本地声明或从 `@/types/type-*.ts` 导入。
- 尽可能使用 `type` 关键字而非 `interface`（项目惯例）。
- 在 `request<R>()` 调用中显式使用泛型：`request<LoginResponse>(url, params)`。
- 优先使用 `ref<Type>(initialValue)` 而非无类型 ref。
- `noImplicitAny` 已禁用 —— 容忍显式 `any`，但新代码中尽量避免。

### 命名规范

| 结构             | 命名规则                          | 示例                                     |
| ---------------- | --------------------------------- | ---------------------------------------- |
| 组件             | `defineOptions` 中使用 PascalCase | `BaseChart`、`AmpLogin`                  |
| 组件文件         | kebab-case                        | `base-chart.vue`、`layout-header.vue`    |
| 服务类           | PascalCase + `Service` 后缀       | `UserService`、`SystemService`           |
| Store 组合式函数 | `use` + PascalCase + `Store`      | `useUserStore`、`useAppStore`            |
| API 方法         | `do` + 动词 或 描述性命名         | `doLogin`、`doLogOut`、`getEncrypyKey`   |
| 变量/函数        | camelCase                         | `loginLoading`、`systemInfo`、`onFinish` |
| 常量             | UPPER_SNAKE_CASE                  | —                                        |
| CSS 类名         | kebab-case                        | `app-container`、`layout-content-body`   |

### Store（Pinia）

- 使用 `defineStore('name', { state, getters, actions })`（Options API 风格）或 setup store 风格。
- 通过 `useXxxStore()` 组合式函数访问 store。
- 使用 store 定义中的 `persist` 键进行状态持久化（自定插件，非 `pinia-plugin-persistedstate`）：

```ts
export const useUserStore = defineStore('user', {
  state: (): UserState => ({ ... }),
  getters: { ... },
  actions: { ... },
  persist: { key: 'pinia_user', paths: [...], expire: 2592e8 },
});
```

### API / 服务层

- 每个领域对应 `src/service/api/api-<domain>.ts` 中的一个包含静态方法的类。
- 方法返回 `Promise<T>`，使用 `@/service/request-wrapper` 中的 `request<R>()` 封装。
- `request<R>()` 封装了 axios，支持 GET/POST/DELETE，并通过 `apiResponse()` 解包响应。

```ts
export class UserService {
  static async doLogin(params: LoginParams): Promise<LoginResponse> {
    return request<LoginResponse>(`${prefix}/login/sign-in`, params);
  }
}
```

### 错误处理

- API 错误：在 `request-wrapper.ts` 中捕获并通过 `ElMessage.error()` 显示。
- 更多错误工具函数在 `@/utils/tool.ts` 中：`newMessageError(msg)` 用于创建标准 Error 对象。
- 面向用户的通知使用 `ElMessage.warning()` / `ElMessage.error()`。
- 在 axios 拦截器中使用 `Promise.reject()` 进行错误传播。

### 样式

- **优先使用 SCSS**（`<style lang="scss" scoped>`）编写组件样式。
- Less 也受支持（vite 中配置），主要在遗留代码中使用。
- `src/assets/styles/variable.scss` 中的全局变量在所有 SCSS 文件中自动导入。
- 穿透作用域样式时使用 `:deep()`。
- 全局样式在 `src/assets/styles/` 目录下（reset、sidebar、mixin 等）。
- CSS 类名使用 kebab-case 命名。

### 格式化（Prettier）

```js
// prettier.config.js
module.exports = {
  endOfLine: 'auto',
  printWidth: 100,
  tabWidth: 2,
  useTabs: false,
  singleQuote: true,
  semi: true,
  trailingComma: 'all',
  bracketSpacing: true,
  arrowParens: 'avoid',
};
```

### Git / 贡献

- 分支命名：`fix/xxx`（修复 bug）、`feature/xxx`（新增功能）。
- 提交信息：简洁明了，中英文均可（项目两种语言都在使用）。
- 保持代码风格与现有代码一致。未配置 lint-staged 或 husky。
- Apache 2.0 许可证。

### 需了解的关键依赖

- `element-plus` — UI 框架（ElMessage、ElContainer、ElHeader、ElMain、el-config-provider 等）
- `pinia` — 状态管理，使用自定义 `persist` 插件
- `@element-plus/icons-vue` — 图标组件
- `echarts` + `vue-echarts` — 图表（参见 `@c/echarts/base-chart.vue`）
- `axios` — HTTP 客户端（封装在 `@/service/request-wrapper.ts` 中）
- `vue-router` — 基于 hash 的路由，含导航守卫

### 禁止事项

- 不要另行添加 Jest/Vitest；现有单元测试使用 Node test，E2E 使用 Playwright。
- 不要使用 `pinia-plugin-persistedstate` —— 项目有自定义 persist 插件。
- 未经讨论不要修改 Prettier 配置。
- 不要添加 `.clinerules` —— 该文件已被 gitignore。
