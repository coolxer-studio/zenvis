import { createApp } from 'vue';

// 引入 Element Plus
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';

import 'virtual:svg-icons-register';

import RootApp from './App.vue';
import router from './router';
import pinia from './stores';

import setupComponent from '@c/index';

import '@a/font/index.css';
import '@a/styles/index.scss';
async function initApp() {
  // 创建实例
  const app = createApp(RootApp)
    .use(pinia)
    .use(router)
    .use(ElementPlus); // 使用 Element Plus

  if (import.meta.env.PROD) {
    app.config.warnHandler = () => null;
  }

  // 注册所有组件
  setupComponent(app);

  // 挂载Dom
  app.mount('#app');
}

initApp();
