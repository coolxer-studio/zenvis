<template>
  <div class="panel right-panel">
    <el-tabs v-model="activeTab" class="right-tabs">
      <el-tab-pane label="数据集准备" name="datasetPreparation">
        <template #default>
          <div class="section-toolbar">
            <div>
              <div class="section-title">分析数据集</div>
              <div class="section-subtitle">共 {{ datasetRecords.length }} 条</div>
            </div>
            <el-tag :type="datasetRecords.length ? 'success' : 'info'" effect="plain">
              {{ datasetRecords.length ? '已准备' : '待准备' }}
            </el-tag>
          </div>
          <el-empty v-if="!datasetRecords.length" description="暂无数据集" :image-size="72" />
          <el-table v-else :data="datasetRecords" stripe table-layout="fixed" class="data-table">
            <el-table-column
              v-for="column in datasetColumns"
              :key="column"
              :prop="column"
              :label="column"
              min-width="140"
              show-overflow-tooltip
            >
              <template #default="scope">{{ cellText(scope.row[column]) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>

      <el-tab-pane label="分析服务" name="serviceAnalysis">
        <template #default>
          <el-empty v-if="!serviceResults.length" description="暂无分析服务结果" :image-size="72" />
          <div v-else class="result-list">
            <div v-for="result in serviceResults" :key="recordId(result)" class="result-item">
              <div class="result-header">
                <div>
                  <div class="section-title">{{ cellText(result.title, '分析服务结果') }}</div>
                  <div v-if="result.serviceTaskId" class="section-subtitle">
                    任务 ID：{{ result.serviceTaskId }}
                  </div>
                </div>
                <el-tag :type="statusTagType(cellText(result.status))" effect="plain">
                  {{ statusText(cellText(result.status)) }}
                </el-tag>
              </div>
              <pre class="json-result">{{
                prettyJson(result.analysisResult || result.result || result)
              }}</pre>
            </div>
          </div>
        </template>
      </el-tab-pane>

      <el-tab-pane label="分析报告" name="reportOutput">
        <template #default>
          <el-empty v-if="!reportTimeline.length" description="暂无分析报告" :image-size="72" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="item in reportTimeline"
              :key="recordId(item)"
              :timestamp="cellText(item.time)"
              :type="timelineType(item)"
            >
              <div class="timeline-title">{{ cellText(item.title, '分析报告') }}</div>
              <div class="timeline-content">{{ cellText(item.content) }}</div>
            </el-timeline-item>
          </el-timeline>
        </template>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { DataAnalysisRecord } from '@/types/type-dih';
import {
  DATA_ANALYSIS_RECORD_EVENT,
  DATA_ANALYSIS_RECORD_REQUEST_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events';
import type { DataAnalysisRecordEventDetail } from '../events';

type RowRecord = Record<string, unknown>;
type TimelineType = 'primary' | 'success' | 'warning' | 'danger' | 'info';
type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary' | '';

const activeTab = ref('datasetPreparation');
const records = ref<DataAnalysisRecord[]>([]);
const datasetRecords = ref<RowRecord[]>([]);
const serviceResults = ref<RowRecord[]>([]);
const reportTimeline = ref<RowRecord[]>([]);

const cellText = (value: unknown, fallback = '') => {
  if (value === undefined || value === null || value === '') return fallback;
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return fallback;
  }
};

const recordId = (record: RowRecord | DataAnalysisRecord) =>
  cellText(
    record.id ||
      record.recordId ||
      record.serviceTaskId ||
      record.title ||
      record.stage ||
      JSON.stringify(record),
  );

const datasetColumns = computed(() => {
  const allColumns = Array.from(new Set(datasetRecords.value.flatMap(row => Object.keys(row))));
  return allColumns.filter(column => column !== 'raw').slice(0, 12);
});

const statusTagType = (status?: string): TagType => {
  if (status === 'completed' || status === 'success') return 'success';
  if (status === 'failed' || status === 'error') return 'danger';
  if (status === 'running' || status === 'processing') return 'warning';
  if (status === 'pending') return 'info';
  return 'primary';
};

const statusText = (status?: string) => {
  if (status === 'completed' || status === 'success') return '已完成';
  if (status === 'failed' || status === 'error') return '失败';
  if (status === 'running' || status === 'processing') return '进行中';
  if (status === 'pending') return '待开始';
  return status || '未知';
};

const timelineType = (item: RowRecord): TimelineType => {
  const type = cellText(item.type);
  if (['primary', 'success', 'warning', 'danger', 'info'].includes(type))
    return type as TimelineType;
  const title = cellText(item.title);
  if (title.includes('结论')) return 'success';
  if (title.includes('过程')) return 'primary';
  return 'info';
};

const prettyJson = (value: unknown) => {
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return cellText(value);
  }
};

const handleRecordsUpdated = (detail: DataAnalysisRecordEventDetail) => {
  detail ||= {};
  records.value = Array.isArray(detail.records) ? detail.records : [];
  datasetRecords.value = Array.isArray(detail.datasetRecords) ? detail.datasetRecords : [];
  serviceResults.value = Array.isArray(detail.serviceResults) ? detail.serviceResults : [];
  reportTimeline.value = Array.isArray(detail.reportTimeline) ? detail.reportTimeline : [];
};

useDihEventListener(DATA_ANALYSIS_RECORD_EVENT, handleRecordsUpdated);

onMounted(() => emitDihEvent(DATA_ANALYSIS_RECORD_REQUEST_EVENT));
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
.section-toolbar,
.result-header {
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
.data-table {
  width: 100%;
}
.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.result-item {
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
