import { defineStore } from 'pinia';

interface AppState {
  // 侧边栏状态
  sidebarCollapsed: boolean;
  // 主题模式
  theme: 'light' | 'dark';
  // 语言
  language: string;
  // 页面加载状态
  isLoading: boolean;
  // 设备类型
  device: 'desktop' | 'mobile';
}

/**
 * 应用全局状态管理
 */
export const useAppStore = defineStore('app', {
  state: (): AppState => ({
    sidebarCollapsed: false,
    theme: 'light',
    language: 'zh-CN',
    isLoading: false,
    device: 'desktop',
  }),

  getters: {
    // 获取当前主题
    currentTheme: (state) => state.theme,

    // 是否为移动设备
    isMobile: (state) => state.device === 'mobile',

    // 是否为桌面设备
    isDesktop: (state) => state.device === 'desktop',
  },

  actions: {
    // 切换侧边栏
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed;
    },

    // 设置侧边栏状态
    setSidebarCollapsed(collapsed: boolean) {
      this.sidebarCollapsed = collapsed;
    },

    // 切换主题
    toggleTheme() {
      this.theme = this.theme === 'light' ? 'dark' : 'light';
    },

    // 设置主题
    setTheme(theme: 'light' | 'dark') {
      this.theme = theme;
    },

    // 设置语言
    setLanguage(language: string) {
      this.language = language;
    },

    // 设置加载状态
    setLoading(loading: boolean) {
      this.isLoading = loading;
    },

    // 设置设备类型
    setDevice(device: 'desktop' | 'mobile') {
      this.device = device;
      // 移动设备自动收起侧边栏
      if (device === 'mobile') {
        this.sidebarCollapsed = true;
      }
    },
  },

  persist: {
    key: 'pinia_app',
    paths: ['sidebarCollapsed', 'theme', 'language', 'device'],
    expire: 2592e8,
  },
});