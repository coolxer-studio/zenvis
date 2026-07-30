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
          <div class="resource-container">
            <el-empty v-if="!section.items.length" class="empty-state" description="暂无记录" />
            <el-table v-else :data="section.items" stripe table-layout="fixed" style="width: 100%">
              <el-table-column prop="name" label="名称" show-overflow-tooltip>
                <template #default="scope">
                  <span class="record-name">
                    {{ scope.row.name || scope.row.id || '-' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column :prop="section.keyProp" :label="section.keyLabel" show-overflow-tooltip />
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
                    <el-tooltip v-if="canViewConfig(section.name, scope.row)" :content="configButtonTip(section.name)" placement="top">
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
                      :content="canRefreshChart(scope.row) ? '按原查询参数刷新真实快照' : '历史记录缺少安全查询参数，不能刷新'"
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
                    <el-tooltip v-if="section.name === 'dashboardConfigs'" content="打开数据看板页面" placement="top">
                      <el-button
                        size="small"
                        :icon="View"
                        circle
                        @click.stop="openDashboardPage(scope.row)"
                      />
                    </el-tooltip>
                    <el-tooltip v-if="section.name === 'menuConfigs' && canOpenMenuPage(scope.row)" content="打开菜单页面" placement="top">
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
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
    <el-dialog v-model="configDialogVisible" :title="configDialogTitle" width="720px" append-to-body>
      <pre class="record-config-content"><code>{{ configDialogText }}</code></pre>
    </el-dialog>
    <el-dialog v-model="previewDialogVisible" :title="previewDialogTitle" width="860px" append-to-body>
      <SafeEcharts
        :option="previewDialogChartOption"
        :error="previewDialogChartError"
        min-height="480px"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CopyDocument, Document, RefreshRight, View } from '@element-plus/icons-vue'
import { copyTextToClipboard } from '@/utils/clipboard'
import {
  DihService,
  EntityAnalyticsApi,
  isSafeEntityAnalyticsTool,
  SystemService,
} from '@/service/api'
import SafeEcharts from '@/components/visualization/safe-echarts.vue'
import {
  DATA_VISUALIZATION_EXTRA_DATA_CHANGED_EVENT,
  DATA_VISUALIZATION_RECORD_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events'
import type { DataVisualizationRecordEventDetail } from '../events'
import {
  buildMenuTargetRoute,
  buildVisualizationTargetRoute,
  findVisualizationDashboardRecord,
  isVisualizationDashboardRecord,
} from './data-visualization-menu-route'

type VisualizationRecord = Record<string, unknown> & {
  id?: string | number
  name?: string
  status?: string
  chartType?: string
  configType?: string
  fileName?: string
  configIndex?: string
  routeName?: string
  menuParams?: string
  menuType?: string
  dashboardId?: string | number
  code?: string
  menuId?: string
  params?: string
  route?: string
  type?: string
  config?: unknown
  amisConfig?: unknown
  echartsOption?: unknown
  query?: unknown
  queryMeta?: unknown
  validationStatus?: string
  queriedAt?: string
  api?: string
  htmlPath?: string
}

const activeTab = ref('chartLibrary')
const chartLibrary = ref<VisualizationRecord[]>([])
const visualizationConfigs = ref<VisualizationRecord[]>([])
const dashboardConfigs = ref<VisualizationRecord[]>([])
const menuConfigs = ref<VisualizationRecord[]>([])
const router = useRouter()
const configDialogVisible = ref(false)
const configDialogTitle = ref('配置内容')
const configDialogText = ref('')
const previewDialogVisible = ref(false)
const previewDialogTitle = ref('视图效果预览')
const previewDialogChartOption = ref<unknown>()
const previewDialogChartError = ref('')
const refreshingRecordId = ref('')
const sessionRecordId = ref('')
const sessionExtraData = ref('')

const asRecordList = (value: unknown): VisualizationRecord[] => {
  return Array.isArray(value)
    ? value.filter(item => item && typeof item === 'object').map(item => item as VisualizationRecord)
    : []
}

const sections = computed(() => [
  { name: 'chartLibrary', label: '图表库', items: chartLibrary.value, keyProp: 'chartType', keyLabel: '图表类型' },
  { name: 'visualizationConfigs', label: '可视化配置', items: visualizationConfigs.value, keyProp: 'configType', keyLabel: '配置类型' },
  { name: 'dashboardConfigs', label: '数据看板配置', items: dashboardConfigs.value, keyProp: 'code', keyLabel: '看板编码' },
  { name: 'menuConfigs', label: '菜单配置', items: menuConfigs.value, keyProp: 'menuId', keyLabel: '菜单ID' },
])

const statusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    temporary: '临时',
    generated: '已生成',
    applied: '已应用',
    created: '已创建',
    error: '异常',
  }
  return status ? labels[status] || status : '未记录'
}

const statusTagType = (status?: string) => {
  if (status === 'applied' || status === 'created') return 'success'
  if (status === 'temporary' || status === 'generated') return 'info'
  if (status === 'error') return 'danger'
  return 'info'
}

const openRouteInNewTab = (routeLocation: Parameters<typeof router.resolve>[0]) => {
  const route = router.resolve(routeLocation)
  window.open(route.href, '_blank', 'noopener,noreferrer')
}

const openVisualizationConfig = (record: VisualizationRecord) => {
  const configType = String(record.configType || record.configIndex || '')
  if (!configType) {
    copyRecord(record)
    return
  }
  openRouteInNewTab({
    name: 'policy-config',
    params: { menuParams: configType },
    query: { fileName: String(record.fileName || '') },
  })
}

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
  })
}

const openDashboardPage = (record: VisualizationRecord) => {
  openRouteInNewTab({
    name: 'dashboard',
    query: {
      id: String(record.dashboardId || record.id || ''),
      name: String(record.name || ''),
      code: String(record.code || ''),
    },
  })
}

const openMenuConfig = (record: VisualizationRecord) => {
  openRouteInNewTab({
    name: 'low-code-page',
    params: { menuParams: 'menu' },
    query: {
      id: String(record.menuId || record.id || ''),
      name: String(record.name || ''),
      params: String(record.params || ''),
    },
  })
}

const canOpenMenuPage = (record: VisualizationRecord) => {
  return Boolean(buildMenuTargetRoute(record))
}

const openMenuPage = (record: VisualizationRecord) => {
  const routeLocation = buildMenuTargetRoute(record)
  if (!routeLocation) {
    ElMessage.warning('缺少菜单类型或参数，无法打开菜单页面')
    return
  }
  openRouteInNewTab(routeLocation)
}

const resolveVisualizationDashboard = async (record: VisualizationRecord) => {
  const panelDashboard = findVisualizationDashboardRecord(record, dashboardConfigs.value)
  if (panelDashboard) {
    return panelDashboard
  }
  const persistedDashboards = await SystemService.getDashboardList()
  return findVisualizationDashboardRecord(record, persistedDashboards)
}

const openVisualizationPage = async (record: VisualizationRecord) => {
  let dashboard: VisualizationRecord | undefined
  if (isVisualizationDashboardRecord(record)) {
    try {
      dashboard = await resolveVisualizationDashboard(record)
    } catch (error) {
      console.error('读取数据看板配置失败:', error)
      ElMessage.error('读取数据看板配置失败，无法查看视图效果')
      return
    }
  } else {
    dashboard = findVisualizationDashboardRecord(record, dashboardConfigs.value)
  }
  if (dashboard) {
    openDashboardPage(dashboard)
    return
  }
  if (isVisualizationDashboardRecord(record)) {
    ElMessage.warning('未找到对应的数据看板配置，无法查看视图效果')
    return
  }
  const routeLocation = buildVisualizationTargetRoute(record, menuConfigs.value)
  if (!routeLocation) {
    ElMessage.warning('缺少对应菜单的页面类型或参数，无法打开页面')
    return
  }
  openRouteInNewTab(routeLocation)
}

const canViewConfig = (sectionName: string, record: VisualizationRecord) => {
  if (sectionName === 'chartLibrary') return Boolean(record.amisConfig || record.config)
  if (sectionName === 'visualizationConfigs') return Boolean(record.configType || record.configIndex)
  if (sectionName === 'dashboardConfigs') return true
  if (sectionName === 'menuConfigs') return true
  return false
}

const canPreview = (sectionName: string, record: VisualizationRecord) => {
  // 图表库的预览是固定操作。即使历史记录缺少 ECharts 快照，也应保留
  // 入口并在预览框中说明原因，而不是悄悄隐藏按钮。
  if (sectionName === 'chartLibrary') return true
  if (sectionName === 'visualizationConfigs') {
    return Boolean(
      isVisualizationDashboardRecord(record)
      || findVisualizationDashboardRecord(record, dashboardConfigs.value)
      || buildVisualizationTargetRoute(record, menuConfigs.value),
    )
  }
  return false
}

const previewButtonTip = (sectionName: string, record: VisualizationRecord) => {
  if (sectionName === 'visualizationConfigs') return '查看视图效果'
  return chartOptionFromRecord(record)
    ? '预览图表快照'
    : '预览图表（历史记录缺少 ECharts 快照）'
}

const configButtonTip = (sectionName: string) => {
  if (sectionName === 'chartLibrary') return '查看 amis 配置'
  if (sectionName === 'visualizationConfigs') return '打开配置管理'
  if (sectionName === 'dashboardConfigs') return '打开看板管理'
  if (sectionName === 'menuConfigs') return '打开菜单管理'
  return '查看配置'
}

const viewRecordConfig = (sectionName: string, record: VisualizationRecord) => {
  if (sectionName === 'chartLibrary') {
    configDialogTitle.value = String(record.name || '图表配置')
    configDialogText.value = JSON.stringify(
      normalizeConfig(record.amisConfig || record.config) || record,
      null,
      2,
    )
    configDialogVisible.value = true
    return
  }
  if (sectionName === 'visualizationConfigs') {
    openVisualizationConfig(record)
    return
  }
  if (sectionName === 'dashboardConfigs') {
    openDashboardConfig(record)
    return
  }
  if (sectionName === 'menuConfigs') {
    openMenuConfig(record)
  }
}

const previewRecord = async (sectionName: string, record: VisualizationRecord) => {
  if (sectionName === 'chartLibrary') {
    previewChartRecord(record)
    return
  }
  if (sectionName === 'visualizationConfigs') {
    await openVisualizationPage(record)
  }
}

const previewChartRecord = (record: VisualizationRecord) => {
  const option = chartOptionFromRecord(record)
  previewDialogTitle.value = String(record.name || '图表预览')
  previewDialogChartOption.value = option
  previewDialogChartError.value = option
    ? ''
    : '该历史记录未保存可直接渲染的 ECharts 快照，请重新生成图表或刷新快照后预览'
  previewDialogVisible.value = true
}

const chartOptionFromRecord = (record: VisualizationRecord) => {
  const raw = normalizedRecord(record.raw)
  const echarts = normalizedRecord(record.echarts)
  const rawEcharts = normalizedRecord(raw.echarts)
  const amis = normalizedRecord(record.amisConfig || record.config)
  const candidates = [
    record.echartsOption,
    echarts.option,
    record.option,
    raw.echartsOption,
    rawEcharts.option,
    amis.type === 'chart' ? amis.config : undefined,
  ]
  for (const candidate of candidates) {
    const option = normalizeConfig(candidate)
    if (isRecord(option) && Object.keys(option).length > 0) return option
  }
  return null
}

const recordIdentity = (record: VisualizationRecord) => {
  return String(record.id || record.planId || record.name || '')
}

const chartQuery = (record: VisualizationRecord) => asRecord(record.query)

const canRefreshChart = (record: VisualizationRecord) => {
  const query = chartQuery(record)
  return Boolean(
    sessionRecordId.value
    && record.source === 'workflow'
    && record.validationStatus === 'success'
    && isSafeEntityAnalyticsTool(String(query.tool || ''))
    && isRecord(query.request),
  )
}

const persistChartLibrary = async () => {
  const extraData = sessionExtraData.value.trim()
    ? asRecord(JSON.parse(sessionExtraData.value))
    : {}
  const dataVisualization = asRecord(extraData.dataVisualization)
  extraData.dataVisualization = {
    ...dataVisualization,
    chartLibrary: chartLibrary.value,
  }
  const nextExtraData = JSON.stringify(extraData)
  await DihService.updateChatSession(sessionRecordId.value, { extra_data: nextExtraData })
  sessionExtraData.value = nextExtraData
  emitDihEvent(DATA_VISUALIZATION_EXTRA_DATA_CHANGED_EVENT, { extraData: nextExtraData })
}

const refreshChartRecord = async (record: VisualizationRecord) => {
  if (!canRefreshChart(record)) {
    ElMessage.warning('历史记录缺少安全查询参数，不能刷新')
    return
  }
  const query = chartQuery(record)
  const tool = String(query.tool || '')
  if (!isSafeEntityAnalyticsTool(tool)) return
  const identity = recordIdentity(record)
  const previousLibrary = chartLibrary.value
  refreshingRecordId.value = identity
  try {
    const response = await EntityAnalyticsApi.query(tool, asRecord(query.request))
    const option = response.echarts?.option
    if (!isRecord(option)) {
      throw new Error('接口未返回 ECharts option')
    }
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
    }
    chartLibrary.value = chartLibrary.value.map(item =>
      recordIdentity(item) === identity ? refreshed : item)
    await persistChartLibrary()
    if (previewDialogVisible.value) {
      previewDialogChartOption.value = option
      previewDialogChartError.value = ''
    }
    ElMessage.success('图表快照已刷新')
  } catch (error) {
    chartLibrary.value = previousLibrary
    console.error('刷新图表快照失败:', error)
    ElMessage.error('刷新失败，已保留原快照')
  } finally {
    refreshingRecordId.value = ''
  }
}

const normalizeConfig = (value: unknown) => {
  if (typeof value === 'string') {
    try {
      return JSON.parse(value)
    } catch {
      return {}
    }
  }
  return value || {}
}

const normalizedRecord = (value: unknown): Record<string, unknown> => {
  const normalized = normalizeConfig(value)
  return isRecord(normalized) ? normalized : {}
}

const isRecord = (value: unknown): value is Record<string, unknown> => {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

const asRecord = (value: unknown): Record<string, unknown> => {
  return isRecord(value) ? value : {}
}

const copyRecord = async (record: VisualizationRecord) => {
  const copied = await copyTextToClipboard(JSON.stringify(record, null, 2))
  if (copied) {
    ElMessage.success('已复制记录')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}

const handleRecordsUpdated = (detail: DataVisualizationRecordEventDetail) => {
  detail ||= {}
  chartLibrary.value = asRecordList(detail.chartLibrary)
  visualizationConfigs.value = asRecordList(detail.visualizationConfigs)
  dashboardConfigs.value = asRecordList(detail.dashboardConfigs)
  menuConfigs.value = asRecordList(detail.menuConfigs)
  sessionExtraData.value = typeof detail.extraData === 'string' ? detail.extraData : ''
  sessionRecordId.value = typeof detail.sessionRecordId === 'string'
    ? detail.sessionRecordId
    : ''
  const firstNonEmpty = sections.value.find(section => section.items.length)
  if (firstNonEmpty) {
    activeTab.value = firstNonEmpty.name
  }
}

useDihEventListener(DATA_VISUALIZATION_RECORD_EVENT, handleRecordsUpdated)
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

.resource-container {
  padding: 12px;
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
  gap: 8px;
  flex-wrap: nowrap;
  min-width: max-content;
  white-space: nowrap;
}

.record-actions :deep(.el-button) {
  flex: 0 0 auto;
  margin-left: 0;
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

.record-preview-content {
  min-height: 360px;
  max-height: 640px;
  overflow: auto;
  padding: 14px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #f6f8fb;
}

.record-html-preview {
  display: block;
  width: 100%;
  min-height: 560px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
}

.record-preview-content :deep(.dv-preview-page) {
  overflow: hidden;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
}

.record-preview-content :deep(.dv-preview-app) {
  display: grid;
  grid-template-columns: minmax(140px, 180px) minmax(0, 1fr);
  min-height: 360px;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
}

.record-preview-content :deep(.dv-preview-sidebar) {
  padding: 14px 10px;
  border-right: 1px solid #ebeef5;
  background: #fff;
}

.record-preview-content :deep(.dv-preview-nav-item) {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 9px 10px;
  border-radius: 6px;
  color: #606266;
  font-size: 13px;
}

.record-preview-content :deep(.dv-preview-nav-item.active) {
  background: #ecf5ff;
  color: #1d6fd9;
  font-weight: 600;
}

.record-preview-content :deep(.dv-preview-nav-item small) {
  color: #909399;
  font-size: 11px;
  font-weight: 400;
  overflow-wrap: anywhere;
}

.record-preview-content :deep(.dv-preview-app-main) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  min-width: 0;
  padding: 16px;
  background: #f8fafc;
}

.record-preview-content :deep(.dv-preview-header) {
  padding: 14px 16px;
  border-bottom: 1px solid #ebeef5;
}

.record-preview-content :deep(.dv-preview-title),
.record-preview-content :deep(.dv-preview-panel-title) {
  color: #303133;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
}

.record-preview-content :deep(.dv-preview-body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
}

.record-preview-content :deep(.dv-preview-panel),
.record-preview-content :deep(.dv-preview-chart),
.record-preview-content :deep(.dv-preview-empty) {
  min-width: 0;
  padding: 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.record-preview-content :deep(.dv-preview-grid) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.record-preview-content :deep(.dv-preview-api) {
  margin-top: 6px;
  color: #909399;
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.record-preview-content :deep(.dv-preview-table-wrap) {
  max-width: 100%;
  margin-top: 12px;
  overflow-x: auto;
}

.record-preview-content :deep(.dv-preview-table) {
  width: 100%;
  min-width: 480px;
  border-collapse: collapse;
  font-size: 13px;
}

.record-preview-content :deep(.dv-preview-table th),
.record-preview-content :deep(.dv-preview-table td) {
  padding: 9px 10px;
  border-bottom: 1px solid #ebeef5;
  color: #303133;
  text-align: left;
  white-space: nowrap;
}

.record-preview-content :deep(.dv-preview-table th) {
  background: #f7f8fa;
  color: #606266;
  font-weight: 600;
}
</style>
