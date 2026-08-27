<template>
  <div class="header-navigation">
    <el-menu
      :default-active="currentKey"
      mode="horizontal"
      class="primary-navigation"
      :ellipsis="true"
    >
      <template v-for="item in menuList" :key="item.code">
        <el-menu-item v-if="!item.children?.length" :index="item.code" @click="goMenu(item)">
          <span class="menu-label">
            {{ item.name }}
            <sup v-if="item.superscript" class="menu-badge">{{ item.superscript }}</sup>
          </span>
        </el-menu-item>
        <el-sub-menu v-else :index="item.code" popper-class="nav-submenu-popper">
          <template #title>
            <span class="menu-label">
              {{ item.name }}
              <sup v-if="item.superscript" class="menu-badge">{{ item.superscript }}</sup>
            </span>
          </template>
          <el-menu-item
            v-for="child in item.children"
            :key="child.code"
            :index="child.code"
            @click="goMenu(child)"
          >
            <span class="menu-label">
              {{ child.name }}
              <sup v-if="child.superscript" class="menu-badge">{{ child.superscript }}</sup>
            </span>
          </el-menu-item>
        </el-sub-menu>
      </template>
    </el-menu>

    <div class="header-tools">
      <el-dropdown
        trigger="click"
        popper-class="user-dropdown-popper"
        @command="handleUserCommand"
      >
        <button class="user-trigger" type="button" aria-label="打开用户菜单">
          <span class="user-avatar">{{ userInitial }}</span>
          <span class="user-summary">
            <strong>{{ userInfo.name || '当前用户' }}</strong>
            <small>{{ userInfo.email || '安全运营中心' }}</small>
          </span>
          <el-icon class="user-chevron"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled class="user-profile-item">
              <span class="dropdown-avatar">{{ userInitial }}</span>
              <span class="dropdown-profile">
                <strong>{{ userInfo.name || '当前用户' }}</strong>
                <small>{{ userInfo.email || '安全运营中心' }}</small>
              </span>
            </el-dropdown-item>
            <el-dropdown-item command="password" divided>
              <el-icon><Lock /></el-icon>
              修改密码
            </el-dropdown-item>
            <el-dropdown-item command="logout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <Password :show="showPassword" @on-ok="submit" @on-cancel="closeModel" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowDown, Lock, SwitchButton } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import JSEncrypt from 'jsencrypt';

import { UserService } from '@/service/api';
import { clearLoginSession, getPermissionList, getUserInfo } from '@u/auth-session';
import Password from './nav-password.vue';

defineOptions({ name: 'NavMenu' });

type PermissionItem = {
  code: string;
  name: string;
  route?: string;
  params?: string;
  superscript?: string;
  children?: PermissionItem[];
};

const router = useRouter();
const route = useRoute();
const menuList = getPermissionList<PermissionItem[]>() || [];
const userInfo = getUserInfo<{ name?: string; email?: string }>() || {};
const showPassword = ref(false);
const currentKey = ref('');
const userInitial = computed(() =>
  (userInfo.name || userInfo.email || 'U').trim().slice(0, 1).toUpperCase(),
);

const findActiveKey = (items: PermissionItem[], routeName: string, menuParam: string): string => {
  for (const item of items) {
    const matchesRoute = item.route === routeName;
    const matchesParam = !menuParam || String(item.params || '') === menuParam;
    if (matchesRoute && matchesParam) return item.code;

    const childKey = item.children
      ? findActiveKey(item.children, routeName, menuParam)
      : '';
    if (childKey) return childKey;
  }
  return '';
};

watch(
  () => [route.name, route.params.menuParams] as const,
  ([routeName, rawMenuParam]) => {
    const menuParam = Array.isArray(rawMenuParam)
      ? rawMenuParam[0] || ''
      : String(rawMenuParam || '');
    currentKey.value = findActiveKey(menuList, String(routeName || ''), menuParam);
  },
  { immediate: true },
);

const goMenu = (item: PermissionItem) => {
  if (!item.route) return;
  currentKey.value = item.code;
  router.push({
    name: item.route,
    params: item.params === undefined ? undefined : { menuParams: item.params },
  });
};

const logOut = () => {
  ElMessageBox.confirm('确认结束当前会话并返回登录页吗？', '退出登录', {
    confirmButtonText: '确认退出',
    cancelButtonText: '继续使用',
    type: 'warning',
  })
    .then(async () => {
      await UserService.doLogOut();
      clearLoginSession();
      await router.push({ name: 'login' });
    })
    .catch(() => undefined);
};

const updatePassword = () => {
  window.setTimeout(() => {
    showPassword.value = true;
  }, 0);
};

const handleUserCommand = (command: string) => {
  if (command === 'password') {
    updatePassword();
    return;
  }
  if (command === 'logout') {
    logOut();
  }
};

const submit = async (params: Record<string, string>) => {
  const encryptor = new JSEncrypt();
  const res = await UserService.getEncrypyKey();
  encryptor.setPublicKey(res.key);
  const encryptedPassword = encryptor.encrypt(params.password) || '';
  await UserService.editPassword({
    oldPassword: encryptor.encrypt(params.old_password) || '',
    newPassword: encryptedPassword,
    confirmPassword: encryptedPassword,
  });
  ElMessage.success('密码已更新，请重新登录');
  showPassword.value = false;
  logOut();
};

const closeModel = () => {
  showPassword.value = false;
};
</script>

<style lang="scss" scoped>
.header-navigation {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
  height: 100%;
}

.primary-navigation {
  flex: 1;
  min-width: 0;
  height: 100%;
  background: transparent;
  border-bottom: 0 !important;
  transform: translateY(8px);

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    position: relative;
    height: 40px;
    margin: 0 3px;
    padding: 0 14px;
    color: var(--zv-text-secondary);
    font-size: var(--zv-font-size-md);
    font-weight: var(--zv-font-weight-medium);
    line-height: 40px;
    border: 0 !important;
    border-radius: var(--zv-radius-sm);
    outline: none !important;
    transition: color var(--zv-motion-base) var(--zv-ease-standard), background-color var(--zv-motion-base) var(--zv-ease-standard);
  }

  :deep(.el-menu-item:hover),
  :deep(.el-sub-menu__title:hover) {
    color: var(--zv-primary);
    background: var(--zv-bg-subtle);
  }

  :deep(.el-menu-item.is-active),
  :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
    color: var(--zv-primary) !important;
    font-weight: var(--zv-font-weight-semibold);
    background: var(--zv-primary-soft) !important;
    box-shadow: none !important;
    outline: none !important;
  }

  :deep(.el-menu-item.is-active::after),
  :deep(.el-sub-menu.is-active > .el-sub-menu__title::after) {
    position: absolute;
    bottom: 3px;
    left: 50%;
    width: 18px;
    height: 2px;
    background: linear-gradient(90deg, var(--zv-primary), var(--zv-accent));
    border-radius: 999px;
    box-shadow: 0 1px 4px rgb(47 94 229 / 25%);
    content: '';
    transform: translateX(-50%);
  }
}

.menu-label {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}

.menu-badge {
  display: inline-flex;
  align-items: center;
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  color: var(--zv-text-inverse);
  font-size: 10px;
  font-weight: 700;
  line-height: 17px;
  text-align: center;
  background: linear-gradient(135deg, var(--zv-danger), #ea7657);
  border-radius: 999px;
  box-shadow: 0 4px 9px rgb(209 67 91 / 20%);
}

.header-tools {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  height: 100%;
  padding: 0 14px 0 10px;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  height: 46px;
  padding: 5px 8px 5px 5px;
  font: inherit;
  background: transparent;
  border: 0;
  border-radius: var(--zv-radius-lg);
  cursor: pointer;
  transition: background-color var(--zv-motion-base) var(--zv-ease-standard);

  &:hover,
  &:focus-visible {
    background: var(--zv-bg-subtle);
    outline: none;
  }
}

.user-avatar,
.dropdown-avatar {
  display: inline-grid;
  flex: 0 0 auto;
  color: var(--zv-text-inverse);
  font-weight: 700;
  place-items: center;
  background: linear-gradient(135deg, var(--zv-primary), var(--zv-accent));
  border-radius: 50%;
  box-shadow: 0 6px 15px rgb(47 94 229 / 23%);
}

.user-avatar {
  width: 34px;
  height: 34px;
  font-size: 13px;
}

.user-summary,
.dropdown-profile {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;

  strong,
  small {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.user-summary {
  max-width: 142px;
  margin-left: 9px;
  line-height: 1.2;

  strong {
    color: var(--zv-text-primary);
    font-size: 13px;
  }

  small {
    margin-top: 3px;
    color: var(--zv-text-muted);
    font-size: 11px;
  }
}

.user-chevron {
  margin-left: 8px;
  color: var(--zv-text-placeholder);
  font-size: 14px;
}

:global(.nav-submenu-popper.el-popper),
:global(.user-dropdown-popper.el-popper) {
  overflow: hidden;
  border: 1px solid var(--zv-divider);
  border-radius: var(--zv-radius-lg);
  box-shadow: var(--zv-shadow-3);
}

:global(.nav-submenu-popper .el-menu) {
  min-width: 150px;
  padding: 6px;
  border: 0;
}

:global(.nav-submenu-popper .el-menu-item) {
  height: 38px;
  color: var(--zv-text-secondary);
  line-height: 38px;
  border-radius: var(--zv-radius-sm);
}

:global(.nav-submenu-popper .el-menu-item:hover),
:global(.nav-submenu-popper .el-menu-item.is-active) {
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
}

:global(.user-dropdown-popper .el-dropdown-menu) {
  min-width: 230px;
  padding: 7px;
}

:global(.user-dropdown-popper .el-dropdown-menu__item) {
  min-height: 38px;
  border-radius: var(--zv-radius-sm);
}

:global(.user-dropdown-popper .user-profile-item) {
  display: flex;
  padding: 8px 10px;
  opacity: 1;
  cursor: default;
}

.dropdown-avatar {
  width: 38px;
  height: 38px;
  font-size: 14px;
}

.dropdown-profile {
  max-width: 160px;
  margin-left: 10px;

  strong {
    color: var(--zv-text-primary);
    font-size: 13px;
  }

  small {
    margin-top: 4px;
    color: var(--zv-text-muted);
    font-size: 12px;
  }
}

@media (max-width: 1320px) {
  .primary-navigation {
    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      margin-inline: 1px;
      padding-inline: 10px;
    }
  }

  .user-summary {
    display: none;
  }

  .user-chevron {
    margin-left: 5px;
  }
}

@media (max-width: 760px) {
  .header-tools {
    padding-inline: 6px 8px;
  }

  .user-chevron {
    display: none;
  }
}
</style>
