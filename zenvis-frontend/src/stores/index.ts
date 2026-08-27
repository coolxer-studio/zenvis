import { createPinia } from 'pinia';
import { createPersistedState } from './plugins/persisted-state';

const pinia = createPinia();

// 使用持久化插件
pinia.use(createPersistedState());

export default pinia;

// 导出所有 store
export * from './modules/user';
export * from './modules/app';
