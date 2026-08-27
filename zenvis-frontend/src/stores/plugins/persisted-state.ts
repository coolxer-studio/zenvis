import { ls } from '@/utils/local-storage';
import type { PiniaPluginContext } from 'pinia';

interface PersistOptions {
  key?: string;
  paths?: string[];
  expire?: number;
}

declare module 'pinia' {
  export interface DefineStoreOptionsBase<S, Store> {
    persist?: PersistOptions | false;
  }
}

/**
 * Pinia 持久化插件
 * 使用现有的 localStorage 封装工具
 */
export function createPersistedState() {
  return (context: PiniaPluginContext) => {
    const { store, options } = context;
    const persistOptions = options.persist as PersistOptions | false | undefined;

    if (!persistOptions) return;

    const key = persistOptions.key || `pinia_${store.$id}`;
    const paths = persistOptions.paths;
    const expire = persistOptions.expire;

    // 初始化时从 localStorage 恢复状态
    const savedState = ls.get(key);
    if (savedState) {
      try {
        const parsedState = JSON.parse(savedState);
        if (paths) {
          paths.forEach(path => {
            if (parsedState[path] !== undefined) {
              store.$patch({ [path]: parsedState[path] });
            }
          });
        } else {
          store.$patch(parsedState);
        }
      } catch (e) {
        console.warn(`Failed to restore state for ${store.$id}:`, e);
      }
    }

    // 监听状态变化，自动保存到 localStorage
    store.$subscribe((mutation, state) => {
      try {
        let stateToSave = state;
        if (paths) {
          stateToSave = paths.reduce((acc, path) => {
            if (state[path] !== undefined) {
              acc[path] = state[path];
            }
            return acc;
          }, {} as any);
        }
        ls.set(key, JSON.stringify(stateToSave), expire);
      } catch (e) {
        console.warn(`Failed to persist state for ${store.$id}:`, e);
      }
    });
  };
}
