<template>
  <div class="panel right-panel">
    <el-tabs v-model="activeTab" class="right-tabs">
      <el-tab-pane label="日志聚合" name="logAggregation">
        <div class="analysis-section">
          <div class="section-toolbar">
            <div>
              <div class="section-title">本次聚合日志</div>
              <div class="section-subtitle">共 {{ aggregatedLogs.length }} 条</div>
            </div>
            <el-tag :type="aggregatedLogs.length ? 'success' : 'info'" effect="plain">
              {{ aggregatedLogs.length ? '已聚合' : '待聚合' }}
            </el-tag>
          </div>
          <el-empty v-if="!aggregatedLogs.length" description="暂无聚合日志" :image-size="72" />
          <el-table v-else :data="aggregatedLogs" stripe table-layout="fixed" class="log-table">
            <el-table-column
              v-for="column in logColumns"
              :key="column"
              :prop="column"
              :label="column"
              min-width="140"
              show-overflow-tooltip
            >
              <template #default="scope">
                {{ cellText(scope.row[column]) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="沙箱研判" name="sandboxAnalysis">
        <div class="analysis-section">
          <el-empty v-if="!sandboxResults.length" description="暂无沙箱研判结果" :image-size="72" />
          <div v-else class="sandbox-list">
            <div v-for="result in sandboxResults" :key="recordId(result)" class="sandbox-result">
              <div class="sandbox-header">
                <div>
                  <div class="section-title">{{ cellText(result.title, '沙箱研判结果') }}</div>
                  <div v-if="result.taskId" class="section-subtitle">任务ID：{{ result.taskId }}</div>
                </div>
                <el-tag :type="statusTagType(cellText(result.status))" effect="plain">
                  {{ statusText(cellText(result.status)) }}
                </el-tag>
              </div>
              <pre class="json-result">{{ prettyJson(result.result || result.raw || result) }}</pre>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="分析结论" name="analysisConclusion">
        <div class="analysis-section">
          <el-empty v-if="!conclusionTimeline.length" description="暂无分析结论" :image-size="72" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="item in conclusionTimeline"
              :key="recordId(item)"
              :timestamp="cellText(item.time)"
              :type="timelineType(item)"
            >
              <div class="timeline-title">{{ cellText(item.title, '分析结论') }}</div>
              <div class="timeline-content">{{ cellText(item.content) }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { AnalysisRecord } from '@/types/type-dih'
import {
  DATA_ANALYSIS_RECORD_EVENT,
  DATA_ANALYSIS_RECORD_REQUEST_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events'
import type { AnalysisRecordEventDetail } from '../events'

type RowRecord = Record<string, unknown>
type TimelineType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary' | ''

const activeTab = ref('logAggregation')
const records = ref<AnalysisRecord[]>([])
const aggregatedLogs = ref<RowRecord[]>([])
const sandboxResults = ref<RowRecord[]>([])
const conclusionTimeline = ref<RowRecord[]>([])

const preferredLogColumns = ['time', 'timestamp', 'server_time', 'alarmId', 'alarm_id', 'level', 'riskLevel', 'sourceIp', 'src_ip', 'destIp', 'dst_ip', 'host', 'account', 'process', 'eventType', 'event_type', 'message', 'content']

const cellText = (value: unknown, fallback = '') => {
  if (value === undefined || value === null || value === '') return fallback
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  try {
    return JSON.stringify(value)
  } catch {
    return fallback
  }
}

const recordId = (record: RowRecord | AnalysisRecord) => {
  return cellText(record.id || record.recordId || record.taskId || record.title || record.stage || JSON.stringify(record))
}

const logColumns = computed(() => {
  const allColumns = Array.from(new Set(aggregatedLogs.value.flatMap(row => Object.keys(row))))
  const preferred = preferredLogColumns.filter(column => allColumns.includes(column))
  const rest = allColumns.filter(column => !preferred.includes(column) && column !== 'raw')
  return [...preferred, ...rest].slice(0, 12)
})

const statusTagType = (status?: string): TagType => {
  if (status === 'completed' || status === 'success') return 'success'
  if (status === 'failed' || status === 'error') return 'danger'
  if (status === 'running' || status === 'processing') return 'warning'
  if (status === 'pending') return 'info'
  return 'primary'
}

const statusText = (status?: string) => {
  if (status === 'completed' || status === 'success') return '已完成'
  if (status === 'failed' || status === 'error') return '失败'
  if (status === 'running' || status === 'processing') return '进行中'
  if (status === 'pending') return '待开始'
  return status || '未知'
}

const timelineType = (item: RowRecord): TimelineType => {
  const type = cellText(item.type)
  if (['primary', 'success', 'warning', 'danger', 'info'].includes(type)) {
    return type as TimelineType
  }
  const title = cellText(item.title)
  if (title.includes('处置')) return 'warning'
  if (title.includes('结论')) return 'success'
  if (title.includes('过程')) return 'primary'
  return 'info'
}

const prettyJson = (value: unknown) => {
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return cellText(value)
  }
}

const handleAnalysisRecordsUpdated = (detail: AnalysisRecordEventDetail) => {
  detail ||= {}
  records.value = Array.isArray(detail.records) ? detail.records : []
  aggregatedLogs.value = Array.isArray(detail.aggregatedLogs) ? detail.aggregatedLogs : []
  sandboxResults.value = Array.isArray(detail.sandboxResults) ? detail.sandboxResults : []
  conclusionTimeline.value = Array.isArray(detail.conclusionTimeline) ? detail.conclusionTimeline : []
}

useDihEventListener(DATA_ANALYSIS_RECORD_EVENT, handleAnalysisRecordsUpdated)

onMounted(() => {
  emitDihEvent(DATA_ANALYSIS_RECORD_REQUEST_EVENT)
})
</script>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  box-sizing: border-box;
  background: #f5f7fa;
  color: #303133;
  overflow: hidden;
}

.right-panel {
  padding: 0;
}

.right-tabs {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
}

:deep(.el-tabs__content) {
  height: calc(100% - 40px);
  overflow-y: auto;
}

:deep(.el-tabs__nav) {
  padding: 0 12px;
}

:deep(.el-tabs__item) {
  height: 40px;
  line-height: 40px;
}

.analysis-section {
  padding: 12px;
}

.section-toolbar,
.sandbox-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-title,
.timeline-title {
  color: #303133;
  font-weight: 600;
}

.section-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.log-table {
  width: 100%;
}

.sandbox-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sandbox-result {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
}

.json-result {
  max-height: 420px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-radius: 6px;
  background: #f7f8fa;
  color: #303133;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.timeline-content {
  margin-top: 6px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
