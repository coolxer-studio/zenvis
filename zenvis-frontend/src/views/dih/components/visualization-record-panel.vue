<template>
  <div class="panel right-panel">
    <div class="tab-container">
      <el-tabs v-model="activeTab" class="right-tabs">
        <el-tab-pane
          v-for="section in sections"
          :key="section.name"
          :label="section.label"
          :name="section.name"
        >
          <el-empty v-if="!section.items.length" class="empty-state" :description="section.emptyText || '暂无记录'" />
          <el-table v-else :data="section.items" stripe table-layout="fixed" style="width: 100%">
            <el-table-column prop="name" label="名称" show-overflow-tooltip>
              <template #default="scope">
                <span class="record-name">{{ scope.row.name || scope.row.id || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              :prop="section.keyProp"
              :label="section.keyLabel"
              show-overflow-tooltip
            />
            <el-table-column label="状态" width="96">
              <template #default="scope">
                <el-tag :type="statusTagType(scope.row.status)" effect="plain">
                  {{ statusLabel(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="228" class-name="record-actions-column">
              <template #default="scope">
                <div class="record-actions">
                  <el-tooltip
                    v-if="canViewConfig(section.name, scope.row)"
                    :content="configButtonTip(section.name)"
                    placement="top"
                  >
                    <el-button
                      size="small"
                      :icon="Document"
                      circle
                      @click.stop="viewRecordConfig(section.name, scope.row)"
                    />
                  </el-tooltip>
                  <el-tooltip
                    v-if="canPreview(section.name, scope.row)"
                    :content="previewButtonTip(section.name, scope.row)"
                    placement="top"
                  >
                    <el-button
                      size="small"
                      :icon="View"
                      circle
                      @click.stop="previewRecord(section.name, scope.row)"
                    />
                  </el-tooltip>
                  <el-tooltip
                    v-if="section.name === 'chartLibrary'"
                    :content="
                      canRefreshChart(scope.row)
                        ? '按原查询参数刷新真实快照'
                        : '历史记录缺少安全查询参数，不能刷新'
                    "
                    placement="top"
                  >
                    <el-button
                      size="small"
                      :icon="RefreshRight"
                      circle
                      :loading="refreshingRecordId === recordIdentity(scope.row)"
                      :disabled="!canRefreshChart(scope.row)"
                      @click.stop="refreshChartRecord(scope.row)"
                    />
                  </el-tooltip>
                  <el-tooltip
                    v-if="section.name === 'dashboards'"
                    content="打开数据看板页面"
                    placement="top"
                  >
                    <el-button
                      size="small"
                      :icon="View"
                      circle
                      @click.stop="openDashboardPage(scope.row)"
                    />
                  </el-tooltip>
                  <el-tooltip
                    v-if="section.name === 'menus' && canOpenMenuPage(scope.row)"
                    content="打开菜单页面"
                    placement="top"
                  >
                    <el-button
                      size="small"
                      :icon="View"
                      circle
                      @click.stop="openMenuPage(scope.row)"
                    />
                  </el-tooltip>
                  <el-tooltip content="复制记录" placement="top">
                    <el-button
                      size="small"
                      :icon="CopyDocument"
                      circle
                      @click.stop="copyRecord(scope.row)"
                    />
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
    <el-dialog
      v-model="configDialogVisible"
      :title="configDialogTitle"
      width="720px"
      append-to-body
    >
      <pre class="record-config-content"><code>{{ configDialogText }}</code></pre>
    </el-dialog>
    <el-dialog
      v-model="previewDialogVisible"
      :title="previewDialogTitle"
      width="860px"
      append-to-body
    >
      <SafeEcharts
        :option="previewDialogChartOption"
        :error="previewDialogChartError"
        min-height="480px"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { CopyDocument, Document, RefreshRight, View } from '@element-plus/icons-vue';
import { copyTextToClipboard } from '@/utils/clipboard';
import {
  DihService,
  EntityAnalyticsApi,
  isSafeEntityAnalyticsTool,
  SystemService,
} from '@/service/api';
import SafeEcharts from '@/components/visualization/safe-echarts.vue';
import {
  buildMenuTargetRoute,
  buildVisualizationTargetRoute,
  findVisualizationDashboardRecord,
  isVisualizationDashboardRecord,
} from './data-visualization-menu-route';

export type VisualizationRecord = Record<string, unknown> & {
  id?: string | number;
  name?: string;
  status?: string;
  chartType?: string;
  configType?: string;
  fileName?: string;
  configIndex?: string;
  routeName?: string;
  menuParams?: string;
  menuType?: string;
  dashboardId?: string | number;
  code?: string;
  menuId?: string;
  params?: string;
  route?: string;
  type?: string;
  config?: unknown;
  amisConfig?: unknown;
  echartsOption?: unknown;
  query?: unknown;
  queryMeta?: unknown;
  validationStatus?: string;
  queriedAt?: string;
  api?: string;
  htmlPath?: string;
};

export type VisualizationSectionName = 'chartLibrary' | 'configs' | 'dashboards' | 'menus';

export type VisualizationSection = {
  name: VisualizationSectionName;
  label: string;
  items: VisualizationRecord[];
  keyProp: string;
  keyLabel: string;
  emptyText?: string;
};

const props = withDefaults(
  defineProps<{
    sections: VisualizationSection[];
    sessionRecordId?: string;
    sessionExtraData?: string;
    extraDataKey?: 'visualizationChart' | 'visualizationApp' | 'visualizationDashboard';
  }>(),
  {
    sessionRecordId: '',
    sessionExtraData: '',
    extraDataKey: 'visualizationChart',
  },
);

const emit = defineEmits<{
  (e: 'extraDataChanged', extraData: string): void;
}>();

const activeTab = ref<VisualizationSectionName>('chartLibrary');
const router = useRouter();
const configDialogVisible = ref(false);
const configDialogTitle = ref('配置内容');
const configDialogText = ref('');
const previewDialogVisible = ref(false);
const previewDialogTitle = ref('视图效果预览');
const previewDialogChartOption = ref<unknown>();
const previewDialogChartError = ref('');
const refreshingRecordId = ref('');

const dashboardRecords = computed(
  () => props.sections.find(section => section.name === 'dashboards')?.items || [],
);
const menuRecords = computed(
  () => props.sections.find(section => section.name === 'menus')?.items || [],
);

watch(
  () => props.sections,
  sections => {
    if (!sections.some(section => section.name === activeTab.value)) {
      activeTab.value = sections[0]?.name || 'chartLibrary';
      return;
    }
    if (!sections.find(section => section.name === activeTab.value)?.items.length) {
      const firstNonEmpty = sections.find(section => section.items.length);
      if (firstNonEmpty) activeTab.value = firstNonEmpty.name;
    }
  },
  { deep: true, immediate: true },
);

const statusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    temporary: '临时',
    generated: '已生成',
    applied: '已应用',
    created: '已创建',
    error: '异常',
  };
  return status ? labels[status] || status : '未记录';
};

const statusTagType = (status?: string) => {
  if (status === 'applied' || status === 'created') return 'success';
  if (status === 'temporary' || status === 'generated') return 'info';
  if (status === 'error') return 'danger';
  return 'info';
};

const openRouteInNewTab = (routeLocation: Parameters<typeof router.resolve>[0]) => {
  const route = router.resolve(routeLocation);
  window.open(route.href, '_blank', 'noopener,noreferrer');
};

const openVisualizationConfig = (record: VisualizationRecord) => {
  const configType = String(record.configType || record.configIndex || '');
  if (!configType) {
    void copyRecord(record);
    return;
  }
  openRouteInNewTab({
    name: 'policy-config',
    params: { menuParams: configType },
    query: { fileName: String(record.fileName || '') },
  });
};

const openDashboardConfig = (record: VisualizationRecord) => {
  openRouteInNewTab({
    name: 'low-code-page',
    params: { menuParams: 'dashboard' },
    query: {
      id: String(record.dashboardId || record.id || ''),
      name: String(record.name || ''),
      code: String(record.code || ''),
      configIndex: String(record.configIndex || ''),
    },
  });
};

const openDashboardPage = (record: VisualizationRecord) => {
  openRouteInNewTab({
    name: 'dashboard',
    query: {
      id: String(record.dashboardId || record.id || ''),
      name: String(record.name || ''),
      code: String(record.code || ''),
    },
  });
};

const openMenuConfig = (record: VisualizationRecord) => {
  openRouteInNewTab({
    name: 'low-code-page',
    params: { menuParams: 'menu' },
    query: {
      id: String(record.menuId || record.id || ''),
      name: String(record.name || ''),
      params: String(record.params || ''),
    },
  });
};

const canOpenMenuPage = (record: VisualizationRecord) => Boolean(buildMenuTargetRoute(record));

const openMenuPage = (record: VisualizationRecord) => {
  const routeLocation = buildMenuTargetRoute(record);
  if (!routeLocation) {
    ElMessage.warning('缺少菜单类型或参数，无法打开菜单页面');
    return;
  }
  openRouteInNewTab(routeLocation);
};

const resolveVisualizationDashboard = async (record: VisualizationRecord) => {
  const panelDashboard = findVisualizationDashboardRecord(record, dashboardRecords.value);
  if (panelDashboard) return panelDashboard;
  const persistedDashboards = await SystemService.getDashboardList();
  return findVisualizationDashboardRecord(record, persistedDashboards);
};

const openVisualizationPage = async (record: VisualizationRecord) => {
  let dashboard: VisualizationRecord | undefined;
  if (isVisualizationDashboardRecord(record)) {
    try {
      dashboard = await resolveVisualizationDashboard(record);
    } catch (error) {
      console.error('读取数据看板配置失败:', error);
      ElMessage.error('读取数据看板配置失败，无法查看视图效果');
      return;
    }
  } else {
    dashboard = findVisualizationDashboardRecord(record, dashboardRecords.value);
  }
  if (dashboard) {
    openDashboardPage(dashboard);
    return;
  }
  if (isVisualizationDashboardRecord(record)) {
    ElMessage.warning('未找到对应的数据看板配置，无法查看视图效果');
    return;
  }
  const routeLocation = buildVisualizationTargetRoute(record, menuRecords.value);
  if (!routeLocation) {
    ElMessage.warning('缺少对应菜单的页面类型或参数，无法打开页面');
    return;
  }
  openRouteInNewTab(routeLocation);
};

const canViewConfig = (sectionName: VisualizationSectionName, record: VisualizationRecord) => {
  if (sectionName === 'chartLibrary') return Boolean(record.amisConfig || record.config);
  if (sectionName === 'configs') return Boolean(record.configType || record.configIndex);
  return sectionName === 'dashboards' || sectionName === 'menus';
};

const canPreview = (sectionName: VisualizationSectionName, record: VisualizationRecord) => {
  if (sectionName === 'chartLibrary') return true;
  if (sectionName === 'configs') {
    return Boolean(
      isVisualizationDashboardRecord(record) ||
        findVisualizationDashboardRecord(record, dashboardRecords.value) ||
        buildVisualizationTargetRoute(record, menuRecords.value),
    );
  }
  return sectionName === 'dashboards';
};

const previewButtonTip = (sectionName: VisualizationSectionName, record: VisualizationRecord) => {
  if (sectionName === 'configs') return '查看视图效果';
  if (sectionName === 'dashboards') return '打开数据看板';
  return chartOptionFromRecord(record) ? '预览图表快照' : '预览图表（历史记录缺少 ECharts 快照）';
};

const configButtonTip = (sectionName: VisualizationSectionName) => {
  if (sectionName === 'chartLibrary') return '查看 amis 配置';
  if (sectionName === 'configs') return '打开配置管理';
  if (sectionName === 'dashboards') return '打开看板管理';
  if (sectionName === 'menus') return '打开菜单管理';
  return '查看配置';
};

const viewRecordConfig = (sectionName: VisualizationSectionName, record: VisualizationRecord) => {
  if (sectionName === 'chartLibrary') {
    configDialogTitle.value = String(record.name || '图表配置');
    configDialogText.value = JSON.stringify(
      normalizeConfig(record.amisConfig || record.config) || record,
      null,
      2,
    );
    configDialogVisible.value = true;
    return;
  }
  if (sectionName === 'configs') {
    openVisualizationConfig(record);
    return;
  }
  if (sectionName === 'dashboards') {
    openDashboardConfig(record);
    return;
  }
  if (sectionName === 'menus') openMenuConfig(record);
};

const previewRecord = async (sectionName: VisualizationSectionName, record: VisualizationRecord) => {
  if (sectionName === 'chartLibrary') {
    previewChartRecord(record);
    return;
  }
  if (sectionName === 'configs') {
    await openVisualizationPage(record);
    return;
  }
  if (sectionName === 'dashboards') openDashboardPage(record);
};

const previewChartRecord = (record: VisualizationRecord) => {
  const option = chartOptionFromRecord(record);
  previewDialogTitle.value = String(record.name || '图表预览');
  previewDialogChartOption.value = option;
  previewDialogChartError.value = option
    ? ''
    : '该历史记录未保存可直接渲染的 ECharts 快照，请重新生成图表或刷新快照后预览';
  previewDialogVisible.value = true;
};

const chartOptionFromRecord = (record: VisualizationRecord) => {
  const raw = normalizedRecord(record.raw);
  const echarts = normalizedRecord(record.echarts);
  const rawEcharts = normalizedRecord(raw.echarts);
  const amis = normalizedRecord(record.amisConfig || record.config);
  const candidates = [
    record.echartsOption,
    echarts.option,
    record.option,
    raw.echartsOption,
    rawEcharts.option,
    amis.type === 'chart' ? amis.config : undefined,
  ];
  for (const candidate of candidates) {
    const option = normalizeConfig(candidate);
    if (isRecord(option) && Object.keys(option).length > 0) return option;
  }
  return null;
};

const recordIdentity = (record: VisualizationRecord) => String(record.id || record.planId || record.name || '');
const chartQuery = (record: VisualizationRecord) => asRecord(record.query);

const canRefreshChart = (record: VisualizationRecord) => {
  const query = chartQuery(record);
  return Boolean(
    props.sessionRecordId &&
      record.source === 'workflow' &&
      record.validationStatus === 'success' &&
      isSafeEntityAnalyticsTool(String(query.tool || '')) &&
      isRecord(query.request),
  );
};

const chartLibrary = computed(
  () => props.sections.find(section => section.name === 'chartLibrary')?.items || [],
);

const persistChartLibrary = async (records: VisualizationRecord[]) => {
  const extraData = props.sessionExtraData.trim()
    ? asRecord(JSON.parse(props.sessionExtraData))
    : {};
  const section = asRecord(extraData[props.extraDataKey]);
  extraData[props.extraDataKey] = { ...section, chartLibrary: records };
  const nextExtraData = JSON.stringify(extraData);
  await DihService.updateChatSession(props.sessionRecordId, { extra_data: nextExtraData });
  emit('extraDataChanged', nextExtraData);
};

const refreshChartRecord = async (record: VisualizationRecord) => {
  if (!canRefreshChart(record)) {
    ElMessage.warning('历史记录缺少安全查询参数，不能刷新');
    return;
  }
  const query = chartQuery(record);
  const tool = String(query.tool || '');
  if (!isSafeEntityAnalyticsTool(tool)) return;
  const identity = recordIdentity(record);
  refreshingRecordId.value = identity;
  try {
    const response = await EntityAnalyticsApi.query(tool, asRecord(query.request));
    const option = response.echarts?.option;
    if (!isRecord(option)) throw new Error('接口未返回 ECharts option');
    const refreshed: VisualizationRecord = {
      ...record,
      chartType: String(response.echarts?.chart_type || record.chartType || ''),
      queryMeta: response.meta || {},
      echartsOption: option,
      amisConfig: { type: 'chart', config: option },
      config: { type: 'chart', config: option },
      queriedAt: new Date().toISOString(),
      validationStatus: 'success',
      status: 'temporary',
    };
    const nextRecords = chartLibrary.value.map(item =>
      recordIdentity(item) === identity ? refreshed : item,
    );
    await persistChartLibrary(nextRecords);
    if (previewDialogVisible.value) {
      previewDialogChartOption.value = option;
      previewDialogChartError.value = '';
    }
    ElMessage.success('图表快照已刷新');
  } catch (error) {
    console.error('刷新图表快照失败:', error);
    ElMessage.error('刷新失败，已保留原快照');
  } finally {
    refreshingRecordId.value = '';
  }
};

const normalizeConfig = (value: unknown): unknown => {
  if (typeof value === 'string') {
    try {
      return JSON.parse(value);
    } catch {
      return {};
    }
  }
  return value || {};
};

const normalizedRecord = (value: unknown): Record<string, unknown> => {
  const normalized = normalizeConfig(value);
  return isRecord(normalized) ? normalized : {};
};

const isRecord = (value: unknown): value is Record<string, unknown> => {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
};

const asRecord = (value: unknown): Record<string, unknown> => (isRecord(value) ? value : {});

const copyRecord = async (record: VisualizationRecord) => {
  const copied = await copyTextToClipboard(JSON.stringify(record, null, 2));
  if (copied) ElMessage.success('已复制记录');
  else ElMessage.error('复制失败，请手动复制');
};
</script>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 10px;
  box-sizing: border-box;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.right-panel {
  background-color: #f5f7fa;
  color: #333;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.tab-container {
  flex: 1;
  overflow: hidden;
}

.right-tabs {
  height: 100%;
}

:deep(.el-tabs__content) {
  padding: 0;
  height: calc(100% - 40px);
  overflow-y: auto;
}

:deep(.el-tabs__nav) {
  background-color: #fff;
  padding: 0 18px;
  width: 100%;
}

:deep(.el-tabs__item) {
  font-size: 14px;
  height: 40px;
  line-height: 40px;
}

:deep(.el-tabs__item.is-active) {
  font-weight: bold;
}

.empty-state {
  height: 220px;
}

.record-name {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: baseline;
}

:deep(.record-actions-column .cell) {
  overflow: visible;
  white-space: nowrap;
}

.record-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
  min-width: max-content;
  white-space: nowrap;
}

.record-actions :deep(.el-button) {
  width: 30px;
  height: 30px;
  min-width: 30px;
  min-height: 30px;
  padding: 0;
  box-sizing: border-box;
  flex: 0 0 30px;
  margin-left: 0;
  border-radius: 50%;
}

.record-config-content {
  max-height: 560px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #1f2329;
  color: #f5f7fa;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.record-config-content code {
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
}
</style>
