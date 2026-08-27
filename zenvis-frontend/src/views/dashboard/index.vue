<template>
  <div class="dashboard">
    <button
      class="dashboard-drawer-trigger"
      type="button"
      aria-label="打开看板列表"
      title="打开看板列表"
      @click="onOpen"
    >
      <el-icon><ArrowRight /></el-icon>
    </button>

    <el-drawer
      v-model="visible"
      direction="ltr"
      size="320px"
      :closable="false"
      :close-on-click-modal="true"
      :close-on-press-escape="true"
      :with-header="false"
      :modal="true"
      :modal-append-to-body="false"
      modal-class="dashboard-drawer-modal"
      class="dashboard-drawer"
      @close="onClose"
    >
      <aside class="dashboard-drawer-shell">
        <header class="dashboard-drawer-header">
          <div class="drawer-heading">
            <span class="drawer-heading-icon"><DataBoard /></span>
            <div class="drawer-heading-copy">
              <div class="drawer-title-row">
                <h2>看板列表</h2>
                <span class="drawer-total">{{ dashboardListData.length }}</span>
              </div>
              <p :title="currentDashboardName">当前：{{ currentDashboardName }}</p>
            </div>
          </div>
          <button class="drawer-close-btn" type="button" aria-label="关闭看板列表" @click="onClose">
            <el-icon><Close /></el-icon>
          </button>
        </header>

        <div class="dashboard-drawer-tools">
          <el-input
            v-model="dashboardKeyword"
            clearable
            :prefix-icon="Search"
            placeholder="搜索看板名称"
          />
          <div class="dashboard-list-summary">
            <span>{{
              dashboardKeyword ? `找到 ${filteredDashboardList.length} 个` : '全部看板'
            }}</span>
            <span>{{ filteredDashboardList.length }} / {{ dashboardListData.length }}</span>
          </div>
        </div>

        <div class="dashboard-list">
          <template v-if="filteredDashboardList.length">
            <button
              v-for="item in filteredDashboardList"
              :key="item.id"
              type="button"
              class="dashboard-item"
              :class="{ 'is-active': isCurrentDashboard(item) }"
              @click="setDashboard(item)"
            >
              <span class="dashboard-item-icon"><DataBoard /></span>
              <span class="dashboard-item-copy">
                <el-tooltip :content="item.name" placement="right" :show-after="500">
                  <strong>{{ item.name }}</strong>
                </el-tooltip>
                <small>{{ dashboardTypeLabel(item.type) }}</small>
              </span>
              <el-icon v-if="isCurrentDashboard(item)" class="dashboard-item-check"
                ><Check
              /></el-icon>
            </button>
          </template>

          <div v-else class="dashboard-list-empty">
            <span class="empty-icon"><DataBoard /></span>
            <strong>{{ dashboardListData.length ? '未找到匹配看板' : '暂无可用看板' }}</strong>
            <p>{{ dashboardListData.length ? '请尝试其他关键词' : '请先在系统中配置大屏看板' }}</p>
            <el-button v-if="dashboardKeyword" link type="primary" @click="dashboardKeyword = ''">
              清除搜索
            </el-button>
          </div>
        </div>
      </aside>
    </el-drawer>

    <linkBoard v-if="currentDashboard.type == 'LINK'" :data="currentDashboard" />
    <lowCodeBoard v-if="currentDashboard.type == 'LOW_CODE_PAGE'" :data="currentDashboard" />
    <htmlBoard v-if="currentDashboard.type == 'HTML_PAGE'" :data="currentDashboard" />
    <component
      v-if="currentDashboard.type == 'BUILT' && currentBuiltInDashboard"
      :is="currentBuiltInDashboard"
      :data="currentDashboard"
    />
    <div v-else-if="currentDashboard.type == 'BUILT'" class="dashboard-empty">
      内置看板不存在或未配置
    </div>
  </div>
</template>
<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ArrowRight, Check, Close, DataBoard, Search } from '@element-plus/icons-vue';

import linkBoard from './components/link-board/index.vue';
import lowCodeBoard from './components/low-code-board/index.vue';
import htmlBoard from './components/html-board/index.vue';
import systemBoard from './components/system-board/index.vue';

import { SystemService } from '@/service/api';

const builtInDashboardMap = {
  'system-board': systemBoard,
  'msg-board': systemBoard,
};

const visible = ref(false);
const route = useRoute();
const router = useRouter();
const currentDashboard = ref({
  id: '',
  type: '',
  code: '',
});
const dashboardListData = ref([]);
const dashboardKeyword = ref('');
const currentBuiltInDashboard = computed(
  () => builtInDashboardMap[currentDashboard.value?.code || ''] || null,
);
const currentDashboardName = computed(() => currentDashboard.value?.name || '未选择看板');
const filteredDashboardList = computed(() => {
  const keyword = dashboardKeyword.value.trim().toLocaleLowerCase();
  if (!keyword) return dashboardListData.value;
  return dashboardListData.value.filter(item =>
    String(item.name || '')
      .toLocaleLowerCase()
      .includes(keyword),
  );
});

const dashboardTypeLabels = {
  LINK: '链接看板',
  LOW_CODE_PAGE: '低代码看板',
  HTML_PAGE: 'HTML 看板',
  BUILT: '内置看板',
};

const dashboardTypeLabel = type => dashboardTypeLabels[type] || '看板';

const onOpen = () => {
  dashboardKeyword.value = '';
  visible.value = true;
};

const onClose = () => {
  visible.value = false;
};

const getDashboardList = () => {
  SystemService.getDashboardList()
    .then(data => {
      dashboardListData.value = data;
      selectDashboardFromRoute();
    })
    .finally(() => {});
};

const selectDashboardFromRoute = () => {
  if (!dashboardListData.value.length) {
    currentDashboard.value = { id: '', type: '', code: '' };
    return;
  }
  const targetId = route.query.id?.toString() || '';
  const targetCode = route.query.code?.toString() || '';
  const targetName = route.query.name?.toString() || '';
  const matched = dashboardListData.value.find(item => {
    return (
      (targetId && String(item.id) === targetId) ||
      (targetCode && item.code === targetCode) ||
      (targetName && item.name === targetName)
    );
  });
  const defaultDashboard = dashboardListData.value.find(item => item.isDefault);
  currentDashboard.value = matched || defaultDashboard || dashboardListData.value[0];
};

const setDashboard = item => {
  currentDashboard.value = item;
  visible.value = false;
  void router.replace({
    query: {
      ...route.query,
      id: String(item.id ?? ''),
      code: String(item.code ?? ''),
      name: String(item.name ?? ''),
    },
  });
};

const isCurrentDashboard = item => {
  return String(currentDashboard.value?.id ?? '') === String(item?.id ?? '');
};

onMounted(() => {
  getDashboardList();
});

watch(
  () => [route.query.id, route.query.code, route.query.name],
  () => {
    selectDashboardFromRoute();
  },
);
</script>

<style lang="scss">
.dashboard {
  position: relative;
  height: 100%;
  overflow: hidden;
  background-color: var(--zv-bg-surface);

  .sub-title {
    color: var(--zv-accent);
  }

  .table-list {
    width: 100%;
    margin: 0 auto;
  }

  .dashboard-empty {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--zv-text-muted);
    font-size: var(--zv-font-size-md);
  }
}

.dashboard-drawer-trigger {
  position: absolute;
  top: 50%;
  left: 0;
  z-index: 99;
  display: flex;
  width: 24px;
  min-width: 24px;
  height: 52px;
  padding: 0;
  justify-content: center;
  align-items: center;
  overflow: hidden;
  color: rgb(47 94 229 / 48%);
  appearance: none;
  background: transparent !important;
  background-image: none !important;
  border: 1px solid rgb(133 170 224 / 30%);
  border-left: 0;
  border-radius: 0 9px 9px 0;
  box-shadow: none;
  cursor: pointer;
  backdrop-filter: none;
  transform: translateY(-50%);
  transition: width 160ms ease, color 160ms ease, background-color 160ms ease, box-shadow 160ms ease,
    transform 160ms ease;

  > .el-icon {
    width: 16px;
    height: 16px;
    flex: 0 0 16px;
    color: inherit;
    font-size: 16px;
  }

  &:hover,
  &:focus-visible {
    color: var(--zv-primary, #2f5ee5);
    width: 28px;
    background: rgb(255 255 255 / 12%) !important;
    border-color: var(--zv-primary-border, #a9bdf5);
    box-shadow: 3px 0 14px rgb(47 94 229 / 14%);
    outline: none;
    transform: translateY(-50%) translateX(2px);
  }
}

.drawer-total {
  display: inline-grid;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  color: var(--zv-primary, #2f5ee5);
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  background: var(--zv-primary-soft, #eef3ff);
  border: 1px solid var(--zv-primary-border, #c5d2f7);
  border-radius: 999px;
  place-items: center;
}

.dashboard .dashboard-drawer {
  overflow: hidden;
  background: rgb(255 255 255 / 96%) !important;
  border-right: 1px solid var(--zv-border, #d8e0eb);
  box-shadow: 18px 0 48px rgb(21 35 58 / 16%);
  backdrop-filter: blur(18px) saturate(110%);
  transition-duration: 220ms !important;
}

.dashboard .dashboard-drawer .el-drawer__body {
  height: 100%;
  padding: 0;
  overflow: hidden;
  background: transparent !important;
}

.dashboard-drawer-modal {
  background: rgb(21 35 58 / 16%) !important;
  backdrop-filter: blur(2px);
}

.dashboard-drawer-shell {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  color: var(--zv-text-primary, #15233a);
}

.dashboard-drawer-header {
  display: flex;
  min-height: 76px;
  padding: 14px 12px 12px 16px;
  align-items: flex-start;
  justify-content: space-between;
  flex: 0 0 auto;
  background: linear-gradient(180deg, var(--zv-primary-soft, #eef3ff), rgb(255 255 255 / 35%));
  border-bottom: 1px solid var(--zv-divider, #e8edf4);
}

.drawer-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.drawer-heading-icon,
.dashboard-item-icon,
.empty-icon {
  display: grid;
  place-items: center;
}

.drawer-heading-icon {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  color: var(--zv-primary, #2f5ee5);
  font-size: 23px;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.drawer-heading-copy {
  min-width: 0;
}

.drawer-title-row {
  display: flex;
  align-items: center;
  gap: 8px;

  h2 {
    margin: 0;
    color: var(--zv-text-primary, #15233a);
    font-size: 16px;
    font-weight: 650;
    letter-spacing: 0.02em;
  }
}

.drawer-heading-copy p {
  max-width: 194px;
  margin: 5px 0 0;
  overflow: hidden;
  color: var(--zv-text-muted, #66758a);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-close-btn {
  display: grid;
  width: 30px;
  height: 30px;
  padding: 0;
  flex: 0 0 30px;
  color: var(--zv-text-muted, #66758a);
  background: transparent;
  border: 0;
  border-radius: 9px;
  cursor: pointer;
  place-items: center;
  transition: color 160ms ease, background-color 160ms ease;

  &:hover,
  &:focus-visible {
    color: var(--zv-primary, #2f5ee5);
    background: var(--zv-primary-soft, #eef3ff);
    outline: none;
  }
}

.dashboard-drawer-tools {
  padding: 14px 16px 8px;
  flex: 0 0 auto;

  .el-input__wrapper {
    min-height: 34px;
    color: var(--zv-text-primary, #15233a);
    background: var(--zv-bg-subtle, #f7f9fc);
    border: 1px solid var(--zv-border, #d8e0eb);
    border-radius: 9px;
    box-shadow: none;

    &:hover,
    &.is-focus {
      border-color: var(--zv-primary-border, #a9bdf5);
      box-shadow: 0 0 0 3px rgb(47 94 229 / 10%);
    }
  }

  .el-input__inner {
    color: var(--zv-text-primary, #15233a);

    &::placeholder {
      color: var(--zv-text-placeholder, #8b98aa);
    }
  }

  .el-input__prefix,
  .el-input__suffix {
    color: var(--zv-text-muted, #66758a);
  }
}

.dashboard-list-summary {
  display: flex;
  margin-top: 9px;
  padding: 0 2px;
  justify-content: space-between;
  color: var(--zv-text-muted, #66758a);
  font-size: 11px;
}

.dashboard-list {
  min-height: 0;
  padding: 4px 10px 16px;
  overflow-x: hidden;
  overflow-y: auto;
  flex: 1;
  scrollbar-color: rgb(102 117 138 / 30%) transparent;
  scrollbar-width: thin;
}

.dashboard-item {
  position: relative;
  display: flex;
  width: 100%;
  min-height: 54px;
  margin: 4px 0;
  padding: 8px 11px;
  align-items: center;
  color: var(--zv-text-secondary, #47556b);
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: color 160ms ease, background-color 160ms ease, border-color 160ms ease,
    transform 160ms ease;
  gap: 10px;

  &::before {
    position: absolute;
    top: 12px;
    bottom: 12px;
    left: 0;
    width: 2px;
    content: '';
    background: transparent;
    border-radius: 0 2px 2px 0;
  }

  &:hover,
  &:focus-visible {
    color: var(--zv-primary, #2f5ee5);
    background: var(--zv-bg-subtle, #f7f9fc);
    border-color: var(--zv-border, #d8e0eb);
    outline: none;
    transform: translateX(2px);
  }

  &.is-active {
    color: var(--zv-primary, #2f5ee5);
    background: linear-gradient(90deg, var(--zv-primary-soft, #eef3ff), #f8faff);
    border-color: var(--zv-primary-border, #c5d2f7);
    box-shadow: 0 5px 16px rgb(47 94 229 / 10%);

    &::before {
      background: var(--zv-primary, #2f5ee5);
      box-shadow: 0 0 8px rgb(47 94 229 / 32%);
    }

    .dashboard-item-icon {
      color: var(--zv-primary, #2f5ee5);
      background: transparent;
    }
  }
}

.dashboard-item-icon {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  color: var(--zv-text-muted, #66758a);
  font-size: 20px;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.dashboard-item-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;

  strong {
    display: block;
    overflow: hidden;
    font-size: 13px;
    font-weight: 560;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    color: var(--zv-text-muted, #66758a);
    font-size: 10px;
  }
}

.dashboard-item-check {
  flex: 0 0 auto;
  color: var(--zv-primary, #2f5ee5);
  font-size: 15px;
}

.dashboard-list-empty {
  display: flex;
  min-height: 230px;
  padding: 30px 16px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--zv-text-muted, #66758a);
  text-align: center;

  .empty-icon {
    width: 48px;
    height: 48px;
    margin-bottom: 14px;
    color: var(--zv-primary, #2f5ee5);
    font-size: 24px;
    background: var(--zv-primary-soft, #eef3ff);
    border: 1px solid var(--zv-primary-border, #c5d2f7);
    border-radius: 14px;
  }

  strong {
    color: var(--zv-text-secondary, #47556b);
    font-size: 13px;
  }

  p {
    margin: 6px 0 2px;
    font-size: 11px;
  }
}

@media (max-width: 600px) {
  .dashboard .dashboard-drawer {
    width: 100% !important;
  }

  .dashboard-drawer-trigger {
    width: 22px;
    min-width: 22px;
    height: 48px;
  }

  .drawer-heading-copy p {
    max-width: min(230px, 60vw);
  }
}

@media (prefers-reduced-motion: reduce) {
  .dashboard .dashboard-drawer,
  .dashboard-drawer-trigger,
  .dashboard-item,
  .drawer-close-btn {
    transition-duration: 1ms !important;
  }
}
</style>
