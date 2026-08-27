/* eslint-disable */
/// <reference types="vite/client" />

declare module 'virtual:svg-icons-register';

interface ImportMetaEnv {
  readonly VITE_BASE_API?: string;
  readonly VITE_BASE_URL?: string;
  readonly VITE_IFRAME_ALLOWED_ORIGINS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types
  const component: DefineComponent<{}, {}, any>;
  export default component;
}
