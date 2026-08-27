import { defineStore } from 'pinia';
import type { LoginResponse } from '@/types/type-user';

interface UserState {
  token: string;
  username: string;
  role: string;
  permissions: string[];
  isLoggedIn: boolean;
}

/**
 * 用户状态管理
 */
export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: '',
    username: '',
    role: '',
    permissions: [],
    isLoggedIn: false,
  }),

  getters: {
    // 获取用户信息
    userInfo: (state) => ({
      username: state.username,
      role: state.role,
      permissions: state.permissions,
    }),

    // 检查是否有特定权限
    hasPermission: (state) => (permission: string) => {
      return state.permissions.includes(permission);
    },

    // 检查是否有任意一个权限
    hasAnyPermission: (state) => (permissions: string[]) => {
      return permissions.some(permission => state.permissions.includes(permission));
    },

    // 检查是否有所有权限
    hasAllPermissions: (state) => (permissions: string[]) => {
      return permissions.every(permission => state.permissions.includes(permission));
    },
  },

  actions: {
    // 设置用户信息
    setUserInfo(userInfo: LoginResponse) {
      this.token = userInfo.token;
      this.username = userInfo.username;
      this.role = userInfo.role || '';
      this.permissions = userInfo.permissions || [];
      this.isLoggedIn = true;
    },

    // 清除用户信息
    clearUserInfo() {
      this.token = '';
      this.username = '';
      this.role = '';
      this.permissions = [];
      this.isLoggedIn = false;
    },

    // 更新用户权限
    updatePermissions(permissions: string[]) {
      this.permissions = permissions;
    },

    // 更新用户角色
    updateRole(role: string) {
      this.role = role;
    },
  },

  persist: {
    key: 'pinia_user',
    paths: ['token', 'username', 'role', 'permissions', 'isLoggedIn'],
    expire: 2592e8, // 30天过期
  },
});