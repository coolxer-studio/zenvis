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
              <el-table-column label="操作" width="188" class-name="record-actions-column">
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
                    <el-tooltip v-if="canPreview(section.name, scope.row)" content="预览视图效果" placement="top">
                      <el-button
                        size="small"
                        :icon="View"
                        circle
                        @click.stop="previewRecord(section.name, scope.row)"
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
      <iframe
        v-if="previewDialogMode === 'html'"
        class="record-html-preview"
        :srcdoc="previewDialogSrcdoc"
        sandbox="allow-scripts allow-forms allow-same-origin"
      ></iframe>
      <div v-else class="record-preview-content" v-html="previewDialogHtml"></div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CopyDocument, Document, View } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { copyTextToClipboard } from '@/utils/clipboard'
import { PolicyService } from '@/service/api'

type VisualizationRecord = Record<string, unknown> & {
  id?: string
  name?: string
  status?: string
  chartType?: string
  configType?: string
  fileName?: string
  configIndex?: string
  routeName?: string
  menuParams?: string
  menuType?: string
  dashboardId?: string
  code?: string
  menuId?: string
  params?: string
  route?: string
  type?: string
  config?: unknown
  api?: string
  htmlPath?: string
}

type DataVisualizationRecordEventDetail = {
  chartLibrary?: unknown[]
  visualizationConfigs?: unknown[]
  dashboardConfigs?: unknown[]
  menuConfigs?: unknown[]
}

const DATA_VISUALIZATION_RECORD_EVENT = 'dihDataVisualizationRecordsUpdated'

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
const previewDialogHtml = ref('')
const previewDialogMode = ref<'low-code' | 'html'>('low-code')
const previewDialogSrcdoc = ref('')

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

type RouteLocation = Parameters<typeof router.resolve>[0]

const encodeBase64 = (value: string) => {
  try {
    return window.btoa(value)
  } catch {
    return window.btoa(unescape(encodeURIComponent(value)))
  }
}

const menuTypeOf = (record: VisualizationRecord) => {
  return String(record.menuType || record.type || '').toUpperCase()
}

const menuParamsOf = (record: VisualizationRecord) => {
  return String(record.params || record.menuParams || '').trim()
}

const buildMenuTargetRoute = (record: VisualizationRecord): RouteLocation | null => {
  const menuType = menuTypeOf(record)
  const route = String(record.route || '').trim()
  const params = menuParamsOf(record)
  const routeKey = route || {
    LOW_CODE_APP: 'low-code-app',
    LOW_CODE_PAGE: 'low-code-page',
    HTML_PAGE: 'html-page',
    POLICY_CONFIG: 'policy-config',
    EXTERNAL_APP: 'external-app',
  }[menuType]

  if (!routeKey) {
    return null
  }
  if (routeKey === 'low-code-app') {
    return params ? { name: 'low-code-app', params: { menuParams: params } } : null
  }
  if (routeKey === 'low-code-page') {
    return params ? { name: 'low-code-page', params: { menuParams: params } } : null
  }
  if (routeKey === 'html-page') {
    return params ? { name: 'html-page', params: { menuParams: encodeBase64(params) } } : null
  }
  if (routeKey === 'policy-config') {
    return params ? { name: 'policy-config', params: { menuParams: params } } : null
  }
  if (routeKey === 'external-app') {
    return params ? { name: 'external-app', params: { menuParams: encodeBase64(params) } } : null
  }
  if (routeKey.startsWith('/')) {
    return { path: routeKey }
  }
  return null
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

const canViewConfig = (sectionName: string, record: VisualizationRecord) => {
  if (sectionName === 'chartLibrary') return Boolean(record.config)
  if (sectionName === 'visualizationConfigs') return Boolean(record.configType || record.configIndex)
  if (sectionName === 'dashboardConfigs') return true
  if (sectionName === 'menuConfigs') return true
  return false
}

const canPreview = (sectionName: string, record: VisualizationRecord) => {
  if (sectionName === 'chartLibrary') return Boolean(record.config)
  if (sectionName === 'visualizationConfigs') return Boolean(record.configIndex || record.configType)
  return false
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
    configDialogText.value = JSON.stringify(normalizeConfig(record.config) || record, null, 2)
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
    await previewVisualizationConfig(record)
  }
}

const previewChartRecord = (record: VisualizationRecord) => {
  previewDialogTitle.value = String(record.name || '图表预览')
  previewDialogMode.value = 'low-code'
  previewDialogSrcdoc.value = ''
  previewDialogHtml.value = DOMPurify.sanitize(renderLowCodePreview(normalizeConfig(record.config)))
  previewDialogVisible.value = true
}

const previewVisualizationConfig = async (record: VisualizationRecord) => {
  const configType = String(record.configType || configTypeFromRecord(record) || '')
  const fileName = configFileName(record)
  if (!configType || !fileName) {
    ElMessage.warning('缺少配置类型或文件名，无法预览')
    return
  }
  try {
    const content = await PolicyService.textContent(configType, { file_name: fileName })
    previewDialogTitle.value = `${record.name || '可视化配置'}预览`
    if (isHtmlRecord(record) || looksLikeHtml(content)) {
      previewDialogMode.value = 'html'
      previewDialogSrcdoc.value = content
      previewDialogHtml.value = ''
    } else {
      previewDialogMode.value = 'low-code'
      previewDialogSrcdoc.value = ''
      previewDialogHtml.value = DOMPurify.sanitize(renderLowCodePreview(normalizeConfig(content)))
    }
    previewDialogVisible.value = true
  } catch (error) {
    console.error('读取可视化配置失败:', error)
    ElMessage.error('读取配置失败，无法预览')
  }
}

const configTypeFromRecord = (record: VisualizationRecord) => {
  const type = String(record.type || '').toUpperCase()
  if (type === 'HTML_PAGE') return 'html-page'
  return String(record.configIndex || '')
}

const configFileName = (record: VisualizationRecord) => {
  const explicitFileName = String(record.fileName || '').trim()
  if (explicitFileName) return explicitFileName
  const type = String(record.type || '').toUpperCase()
  if (type === 'LOW_CODE_APP') return 'site.json'
  if (type === 'LOW_CODE_PAGE') return 'index.json'
  if (type === 'HTML_PAGE') {
    const path = String(record.htmlPath || record.configIndex || '').trim()
    const segments = path.split('/').filter(Boolean)
    return segments.length ? segments[segments.length - 1] : ''
  }
  return ''
}

const isHtmlRecord = (record: VisualizationRecord) => {
  return String(record.type || '').toUpperCase() === 'HTML_PAGE'
}

const looksLikeHtml = (content: string) => {
  const normalized = content.trim().toLowerCase()
  return normalized.startsWith('<!doctype html') || normalized.startsWith('<html')
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

const isRecord = (value: unknown): value is Record<string, unknown> => {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

const asRecord = (value: unknown): Record<string, unknown> => {
  return isRecord(value) ? value : {}
}

const asRecordArray = (value: unknown): Record<string, unknown>[] => {
  return Array.isArray(value) ? value.filter(isRecord) : []
}

const escapeHtml = (value: unknown) => {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }
  return String(value ?? '').replace(/[&<>"']/g, char => map[char] || char)
}

const renderLowCodePreview = (schema: unknown): string => {
  const record = asRecord(schema)
  if (record.status !== undefined && asRecordArray(asRecord(record.data).pages).length > 0) {
    return renderLowCodeAppPreview(record)
  }
  return renderLowCodeNode(asRecord(schema))
}

const renderLowCodeAppPreview = (schema: Record<string, unknown>) => {
  const pages = asRecordArray(asRecord(schema.data).pages)
  const menus = pages.flatMap(page => {
    const children = asRecordArray(page.children)
    return children.length ? children : [page]
  }).filter(page => typeof page.label === 'string' && page.label)
  return `
    <div class="dv-preview-app">
      <aside class="dv-preview-sidebar">
        <div class="dv-preview-title">低代码应用</div>
        ${menus.map((menu, index) => `
          <div class="dv-preview-nav-item ${index === 0 ? 'active' : ''}">
            <span>${escapeHtml(menu.label)}</span>
            <small>${escapeHtml(menu.url || '')}</small>
          </div>
        `).join('')}
      </aside>
      <main class="dv-preview-app-main">
        <section class="dv-preview-panel">
          <div class="dv-preview-panel-title">应用首页</div>
          <div class="dv-preview-text">展示应用入口、数据概览和常用操作。</div>
        </section>
        <section class="dv-preview-panel">
          <div class="dv-preview-panel-title">数据管理</div>
          <div class="dv-preview-text">提供查询、新增、编辑和删除等数据操作。</div>
        </section>
      </main>
    </div>
  `
}

const renderLowCodeNode = (node: unknown): string => {
  if (Array.isArray(node)) {
    return node.map(renderLowCodeNode).join('')
  }
  const schema = asRecord(node)
  const type = typeof schema.type === 'string' ? schema.type : ''
  if (!type && Object.keys(schema).length === 0) {
    return '<div class="dv-preview-empty">暂无可预览内容。</div>'
  }
  if (type === 'page') {
    return `
      <div class="dv-preview-page">
        <header class="dv-preview-header">
          <div class="dv-preview-title">${escapeHtml(schema.title || '可视化页面')}</div>
        </header>
        <div class="dv-preview-body">${renderLowCodeNode(schema.body)}</div>
      </div>
    `
  }
  if (type === 'chart') {
    return renderChartPreview(schema)
  }
  if (type === 'crud') {
    return renderCrudPreview(schema)
  }
  if (type === 'grid') {
    const columns = asRecordArray(schema.columns)
    return `<div class="dv-preview-grid">${columns.map(column => `<section class="dv-preview-panel">${renderLowCodeNode(column.body || column)}</section>`).join('')}</div>`
  }
  if (type === 'service' || type === 'panel') {
    return `
      <section class="dv-preview-panel">
        <div class="dv-preview-panel-title">${escapeHtml(schema.title || (type === 'service' ? '服务组件' : '面板'))}</div>
        ${schema.api ? `<div class="dv-preview-api">${escapeHtml(schema.api)}</div>` : ''}
        ${renderLowCodeNode(schema.body)}
      </section>
    `
  }
  if (type === 'tpl') {
    return `<div class="dv-preview-text">${escapeHtml(stripTemplateText(schema.tpl || '文本内容'))}</div>`
  }
  return `
    <section class="dv-preview-panel">
      <div class="dv-preview-panel-title">${escapeHtml(type || '组件')}</div>
      ${renderLowCodeNode(schema.body)}
    </section>
  `
}

const renderChartPreview = (schema: Record<string, unknown>) => {
  const config = asRecord(schema.config)
  const title = asRecord(config.title)
  return `
    <section class="dv-preview-chart">
      <div class="dv-preview-panel-title">${escapeHtml(title.text || schema.title || '图表预览')}</div>
      <div class="dv-preview-api">${escapeHtml(schema.api || '/zenvis/api/v1/entity/trend')}</div>
      <div class="dv-preview-bars">
        <span style="height:42%"></span>
        <span style="height:58%"></span>
        <span style="height:82%"></span>
        <span style="height:72%"></span>
        <span style="height:64%"></span>
        <span style="height:48%"></span>
      </div>
    </section>
  `
}

const renderCrudPreview = (schema: Record<string, unknown>) => {
  const columns = asRecordArray(schema.columns).slice(0, 6)
  const visibleColumns = columns.length > 0 ? columns : [
    { name: 'id', label: '事件ID' },
    { name: 'user', label: '用户' },
    { name: 'event_type', label: '事件类型' },
    { name: 'server_time', label: '入库时间' },
  ]
  return `
    <section class="dv-preview-panel">
      <div class="dv-preview-panel-title">数据列表</div>
      <div class="dv-preview-api">${escapeHtml(schema.api || '/zenvis/api/v1/entity/user-event/list')}</div>
      <div class="dv-preview-table-wrap">
        <table class="dv-preview-table">
          <thead><tr>${visibleColumns.map(column => `<th>${escapeHtml(column.label || column.name || '-')}</th>`).join('')}</tr></thead>
          <tbody>
            <tr>${visibleColumns.map(column => `<td>${escapeHtml(sampleValue(column))}</td>`).join('')}</tr>
            <tr>${visibleColumns.map(column => `<td>${escapeHtml(sampleValue(column, true))}</td>`).join('')}</tr>
          </tbody>
        </table>
      </div>
    </section>
  `
}

const sampleValue = (column: Record<string, unknown>, secondRow = false) => {
  const name = String(column.name || '').toLowerCase()
  if (name.includes('id')) return secondRow ? 'evt-002' : 'evt-001'
  if (name.includes('user')) return secondRow ? 'operator-b' : 'demo-user'
  if (name.includes('event_type')) return secondRow ? '点击' : '登录'
  if (name.includes('time')) return secondRow ? '2026-07-09 11:20:00' : '2026-07-09 10:00:00'
  if (name.includes('reliability')) return secondRow ? '7.6' : '8.8'
  return secondRow ? '示例值 B' : '示例值 A'
}

const stripTemplateText = (value: unknown) => {
  return String(value ?? '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\$\{[^}]+}/g, '示例值')
    .replace(/\s+/g, ' ')
    .trim()
}

const copyRecord = async (record: VisualizationRecord) => {
  const copied = await copyTextToClipboard(JSON.stringify(record, null, 2))
  if (copied) {
    ElMessage.success('已复制记录')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}

const handleRecordsUpdated = (event: Event) => {
  const detail = (event as CustomEvent<DataVisualizationRecordEventDetail>).detail || {}
  chartLibrary.value = asRecordList(detail.chartLibrary)
  visualizationConfigs.value = asRecordList(detail.visualizationConfigs)
  dashboardConfigs.value = asRecordList(detail.dashboardConfigs)
  menuConfigs.value = asRecordList(detail.menuConfigs)
  const firstNonEmpty = sections.value.find(section => section.items.length)
  if (firstNonEmpty) {
    activeTab.value = firstNonEmpty.name
  }
}

onMounted(() => {
  window.addEventListener(DATA_VISUALIZATION_RECORD_EVENT, handleRecordsUpdated)
})

onUnmounted(() => {
  window.removeEventListener(DATA_VISUALIZATION_RECORD_EVENT, handleRecordsUpdated)
})
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

.record-preview-content :deep(.dv-preview-bars) {
  display: flex;
  align-items: end;
  gap: 14px;
  height: 220px;
  margin-top: 14px;
  padding: 12px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.record-preview-content :deep(.dv-preview-bars span) {
  flex: 1;
  min-width: 18px;
  border-radius: 6px 6px 0 0;
  background: linear-gradient(180deg, #67c23a 0%, #409eff 100%);
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
