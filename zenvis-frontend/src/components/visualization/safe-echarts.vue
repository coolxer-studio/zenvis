<template>
  <div class="safe-echarts" :style="{ minHeight }">
    <div v-if="loading" class="safe-echarts-state">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>图表加载中…</span>
    </div>
    <el-result
      v-else-if="displayError"
      icon="error"
      title="图表无法渲染"
      :sub-title="displayError"
    >
      <template #extra>
        <el-button size="small" type="primary" plain @click="render">重试渲染</el-button>
      </template>
    </el-result>
    <el-empty v-else-if="empty" description="查询成功，暂无可展示数据" />
    <div v-show="!loading && !displayError && !empty" ref="container" class="safe-echarts-canvas"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const props = withDefaults(defineProps<{
  option?: unknown
  loading?: boolean
  error?: string
  minHeight?: string
}>(), {
  option: undefined,
  loading: false,
  error: '',
  minHeight: '320px',
})

const emit = defineEmits<{
  (event: 'renderFailed', error: string): void
}>()

const container = ref<HTMLElement | null>(null)
const renderError = ref('')
const lastReportedError = ref('')
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const normalizedOption = computed<Record<string, unknown> | null>(() => {
  let value = props.option
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value)
    } catch {
      return null
    }
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const option = value as Record<string, unknown>
  return Object.keys(option).length > 0 ? option : null
})

const empty = computed(() => {
  const option = normalizedOption.value
  if (!option) return false
  const dataset = option.dataset
  const datasets = Array.isArray(dataset) ? dataset : dataset ? [dataset] : []
  const sources = datasets
    .map(item => item && typeof item === 'object' ? (item as Record<string, unknown>).source : null)
    .filter(value => Array.isArray(value)) as unknown[][]
  if (sources.length > 0) {
    return sources.every(source => source.length === 0)
  }
  const series = Array.isArray(option.series) ? option.series : []
  if (series.length === 0) return false
  const seriesWithData = series.filter(item => item && typeof item === 'object')
  return seriesWithData.length > 0 && seriesWithData.every(item => {
    const data = (item as Record<string, unknown>).data
    return Array.isArray(data) && data.length === 0
  })
})

const displayError = computed(() => {
  if (props.error) return props.error
  if (!normalizedOption.value) return '缺少合法的 ECharts option'
  return renderError.value
})

const render = async () => {
  renderError.value = ''
  if (props.loading || empty.value || !normalizedOption.value) {
    chart?.clear()
    return
  }
  await nextTick()
  if (!container.value) return
  try {
    if (!chart) {
      chart = echarts.init(container.value)
    }
    chart.setOption(normalizedOption.value, true)
    chart.resize()
  } catch (error) {
    chart?.clear()
    renderError.value = error instanceof Error ? error.message : 'ECharts 配置不合法'
  }
}

watch(
  displayError,
  error => {
    if (!error || props.error || error === lastReportedError.value) return
    lastReportedError.value = error
    emit('renderFailed', error)
  },
  { immediate: true },
)

watch(
  () => [props.option, props.loading, props.error],
  () => void render(),
  { deep: true, immediate: true },
)

watch(container, element => {
  resizeObserver?.disconnect()
  if (!element) return
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(element)
  void render()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.safe-echarts {
  position: relative;
  width: 100%;
}

.safe-echarts-canvas {
  width: 100%;
  min-height: inherit;
}

.safe-echarts-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: inherit;
  color: #909399;
}

:deep(.el-result) {
  min-height: inherit;
  padding: 24px;
}
</style>
