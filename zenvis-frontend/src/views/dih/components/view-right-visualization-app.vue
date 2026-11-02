<template>
  <VisualizationRecordPanel
    v-if="!applications.length"
    :sections="legacySections"
    :session-record-id="sessionRecordId"
    :session-extra-data="sessionExtraData"
    extra-data-key="visualizationApp"
  />
  <div v-else class="panel right-panel data-app-results">
    <div class="data-app-results__header">
      <div>
        <strong>应用结果</strong>
        <span class="data-app-results__hint">按 appId 聚合，展示发布读回状态</span>
      </div>
      <el-tag size="small" effect="plain">{{ applications.length }} 个应用</el-tag>
    </div>

    <el-table :data="applications" stripe table-layout="fixed" style="width: 100%">
      <el-table-column label="应用" min-width="180">
        <template #default="scope">
          <div class="data-app-results__app">
            <span class="data-app-results__name">{{ appName(scope.row) }}</span>
            <code>{{ appIdOf(scope.row) || '-' }}</code>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="版本" width="78">
        <template #default="scope">
          <el-tag size="small" effect="plain">v{{ scope.row.appVersion || 1 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="页面" min-width="150">
        <template #default="scope">
          <div class="data-app-results__pages">
            <el-tag
              v-for="page in pageList(scope.row).slice(0, 3)"
              :key="page.pageId || page.title"
              size="small"
              effect="plain"
            >
              {{ page.title || page.pageId }}
            </el-tag>
            <span v-if="pageList(scope.row).length > 3" class="data-app-results__muted">
              +{{ pageList(scope.row).length - 3 }}
            </span>
            <span v-if="!pageList(scope.row).length" class="data-app-results__muted">-</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="配置" width="88">
        <template #default="scope">
          <el-tag :type="configStatusType(scope.row)" size="small" effect="plain">
            {{ configStatusLabel(scope.row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="菜单" width="104">
        <template #default="scope">
          <el-tag :type="menuStatusType(scope.row)" size="small" effect="plain">
            {{ menuStatusLabel(scope.row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" class-name="record-actions-column">
        <template #default="scope">
          <div class="data-app-results__actions">
            <el-tooltip content="打开应用页面" placement="top">
              <el-button
                size="small"
                :icon="View"
                circle
                aria-label="打开应用页面"
                :disabled="!canOpenApp(scope.row)"
                @click.stop="openAppPage(scope.row)"
              />
            </el-tooltip>
            <el-tooltip content="查看发布结果" placement="top">
              <el-button
                size="small"
                :icon="Document"
                circle
                aria-label="查看发布结果"
                @click.stop="showDetail(scope.row)"
              />
            </el-tooltip>
            <el-tooltip
              v-if="needsMenuRetry(scope.row)"
              content="重试已锁定版本的菜单发布"
              placement="top"
            >
              <el-button
                size="small"
                type="warning"
                :icon="RefreshRight"
                circle
                aria-label="重试菜单发布"
                @click.stop="requestMenuRetry(scope.row)"
              />
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" title="数据应用发布结果" width="720px">
      <pre class="data-app-results__json">{{ detailText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Document, RefreshRight, View } from '@element-plus/icons-vue';
import VisualizationRecordPanel from './visualization-record-panel.vue';
import type { VisualizationRecord, VisualizationSection } from './visualization-record-panel.vue';
import { buildMenuTargetRoute } from './data-visualization-menu-route';
import {
  DATA_VISUALIZATION_APP_RECORD_EVENT,
  DATA_VISUALIZATION_APP_RECORD_REQUEST_EVENT,
  DATA_VISUALIZATION_APP_RETRY_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events';
import type { VisualizationAppRecordEventDetail } from '../events';

type DataApplicationRecord = VisualizationRecord & {
  appId?: string;
  appVersion?: number | string;
  mode?: string;
  homePageId?: string;
  publishStatus?: string;
  configStatus?: string;
  menuStatus?: string;
  pages?: Array<{ pageId?: string; page_id?: string; title?: string }>;
  menuId?: string | number;
  menuName?: string;
  menuType?: string;
  route?: string;
  params?: string;
  workflowId?: string;
  updatedAt?: string;
  raw?: Record<string, unknown>;
};

const router = useRouter();
const applications = ref<DataApplicationRecord[]>([]);
const configs = ref<VisualizationRecord[]>([]);
const menus = ref<VisualizationRecord[]>([]);
const sessionRecordId = ref('');
const sessionExtraData = ref('');
const detailVisible = ref(false);
const detailText = ref('');

const asRecordList = (value: unknown): DataApplicationRecord[] => Array.isArray(value)
  ? value.filter(item => item && typeof item === 'object')
    .map(item => item as DataApplicationRecord)
    .sort((left, right) => String(right.updatedAt || '').localeCompare(String(left.updatedAt || '')))
  : [];

const legacySections = computed<VisualizationSection[]>(() => [
  {
    name: 'configs',
    label: '应用配置',
    items: configs.value,
    keyProp: 'configType',
    keyLabel: '配置类型',
    emptyText: '暂无可视化应用配置',
  },
  {
    name: 'menus',
    label: '应用菜单',
    items: menus.value,
    keyProp: 'menuId',
    keyLabel: '菜单ID',
    emptyText: '暂无应用菜单记录',
  },
]);

const appIdOf = (record: DataApplicationRecord) => String(record.appId || record.id || '');
const appName = (record: DataApplicationRecord) => String(record.name || appIdOf(record) || '数据应用');
const pageList = (record: DataApplicationRecord) => Array.isArray(record.pages)
  ? record.pages.map(page => ({
    pageId: page.pageId || page.page_id || '',
    title: page.title || '',
  }))
  : [];
const isVerified = (record: DataApplicationRecord) => {
  const status = String(record.publishStatus || record.status || '').toLowerCase();
  return status === 'verified' || status === 'published';
};
const needsMenuRetry = (record: DataApplicationRecord) => {
  const menuStatus = String(record.menuStatus || record.publishStatus || '').toLowerCase();
  return menuStatus === 'partial_menu' || menuStatus === 'partial-menu';
};
const configStatusType = (record: DataApplicationRecord) =>
  String(record.configStatus || '').toLowerCase() === 'verified' ? 'success' : 'warning';
const configStatusLabel = (record: DataApplicationRecord) =>
  String(record.configStatus || '').toLowerCase() === 'verified' ? '已读回' : '待确认';
const menuStatusType = (record: DataApplicationRecord) => {
  if (needsMenuRetry(record)) return 'warning';
  return isVerified(record) ? 'success' : 'info';
};
const menuStatusLabel = (record: DataApplicationRecord) => {
  if (needsMenuRetry(record)) return '部分成功';
  if (isVerified(record)) return '已发布';
  return '待发布';
};

const menuRouteRecord = (record: DataApplicationRecord) => ({
  id: record.menuId,
  name: record.menuName || record.name,
  type: record.menuType,
  menuType: record.menuType,
  route: record.route,
  params: record.params || appIdOf(record),
});
const canOpenApp = (record: DataApplicationRecord) =>
  Boolean(buildMenuTargetRoute(menuRouteRecord(record)));

const openAppPage = (record: DataApplicationRecord) => {
  const target = buildMenuTargetRoute(menuRouteRecord(record));
  if (!target) {
    ElMessage.warning('缺少可用的菜单路由，暂时无法打开应用页面');
    return;
  }
  const route = router.resolve(target);
  window.open(route.href, '_blank', 'noopener,noreferrer');
};

const showDetail = (record: DataApplicationRecord) => {
  detailText.value = JSON.stringify(record, null, 2);
  detailVisible.value = true;
};

const requestMenuRetry = (record: DataApplicationRecord) => {
  const workflowId = String(record.workflowId || record.raw?.workflowId || '');
  if (!workflowId) {
    ElMessage.warning('缺少工作流标识，请在对话中的菜单重试卡片继续');
    return;
  }
  emitDihEvent(DATA_VISUALIZATION_APP_RETRY_EVENT, {
    appId: appIdOf(record),
    workflowId,
  });
};

const handleRecordsUpdated = (detail: VisualizationAppRecordEventDetail) => {
  applications.value = asRecordList(detail?.applications);
  configs.value = Array.isArray(detail?.configs)
    ? detail.configs.filter(item => item && typeof item === 'object') as VisualizationRecord[]
    : [];
  menus.value = Array.isArray(detail?.menus)
    ? detail.menus.filter(item => item && typeof item === 'object') as VisualizationRecord[]
    : [];
  sessionRecordId.value = typeof detail?.sessionRecordId === 'string' ? detail.sessionRecordId : '';
  sessionExtraData.value = typeof detail?.extraData === 'string' ? detail.extraData : '';
};

useDihEventListener(DATA_VISUALIZATION_APP_RECORD_EVENT, handleRecordsUpdated);
onMounted(() => emitDihEvent(DATA_VISUALIZATION_APP_RECORD_REQUEST_EVENT));
</script>

<style scoped>
.data-app-results { min-width: 0; padding: 12px; }
.data-app-results__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.data-app-results__hint { display: block; margin-top: 3px; color: #7a8799; font-size: 12px; }
.data-app-results__app { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.data-app-results__name { overflow: hidden; color: #1f2d3d; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.data-app-results__app code { overflow: hidden; color: #7a8799; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.data-app-results__pages, .data-app-results__actions { display: flex; flex-wrap: wrap; align-items: center; gap: 5px; }
.data-app-results__muted { color: #98a3b3; font-size: 12px; }
.data-app-results__json { max-height: 60vh; margin: 0; overflow: auto; padding: 12px; border-radius: 8px; background: #f6f8fc; color: #344054; font-size: 12px; line-height: 1.55; white-space: pre-wrap; word-break: break-all; }
</style>
