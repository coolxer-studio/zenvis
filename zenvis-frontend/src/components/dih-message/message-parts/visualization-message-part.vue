<template>
  <div v-if="part.type === 'visualization-chart-preview'" class="visualization-chart-preview-part">
    <div class="visualization-chart-preview-header">
      <div class="visualization-chart-preview-title">
        <el-icon><DataAnalysis /></el-icon>
        <span class="card-title-text">{{ part.title || '临时图表预览' }}</span>
        <el-tag size="small" effect="plain">{{
          metadataText(part, 'chartType') || 'chart'
        }}</el-tag>
      </div>
      <div class="visualization-chart-preview-tools">
        <el-tooltip
          v-if="interactive"
          :content="chartLibraryHint"
          placement="top"
        >
          <el-button
            class="config-copy-btn"
            size="small"
            :icon="isChartLibraryAdded ? CircleCheckFilled : Plus"
            circle
            :disabled="!canAddToChartLibrary"
            @click="requestAddChartLibrary"
          />
        </el-tooltip>
        <el-tooltip content="复制 amis 配置" placement="top">
          <el-button
            class="config-copy-btn"
            size="small"
            :icon="CopyDocument"
            circle
            @click="emit('copyCode', chartPreviewConfigText)"
          />
        </el-tooltip>
      </div>
    </div>
    <div v-if="workflowSummary" class="workflow-source-meta">{{ workflowSummary }}</div>
    <div v-if="part.content" class="visualization-chart-preview-desc">{{ part.content }}</div>
    <div v-if="chartPreviewNotice" class="visualization-preview-validation-message" role="alert">
      {{ chartPreviewNotice }}
    </div>
    <div v-if="interactive && isWorkflowRetryable" class="confirm-actions">
      <el-button
        size="small"
        type="primary"
        :loading="workflowRetryInFlight"
        :disabled="workflowRetryInFlight"
        @click="requestWorkflowRetry"
      >
        重试当前阶段
      </el-button>
    </div>
    <SafeEcharts
      :option="chartPreviewOption"
      :loading="part.status === 'loading'"
      :error="chartPreviewError"
      min-height="320px"
      @render-failed="handleChartRenderFailed"
    />
  </div>

  <div v-else-if="isVisualizationRecord" class="notice-part notice-info">
    <div class="notice-title">
      <el-icon><DataAnalysis /></el-icon>
      <span class="card-title-text">{{ visualizationRecordTitle }}</span>
      <el-tooltip :content="isExpanded ? '折叠' : '展开'" placement="top">
        <el-button
          class="card-toggle-btn"
          size="small"
          :icon="isExpanded ? CaretTop : CaretBottom"
          circle
          @click="toggleExpanded"
        />
      </el-tooltip>
    </div>
    <div v-if="isExpanded" class="notice-content">
      {{
        part.content ||
        metadataText(part, 'description') ||
        (interactive ? '已记录到对应可视化结果面板。' : '可视化记录')
      }}
    </div>
  </div>

  <div v-else class="chart-part">
    <el-icon><DataAnalysis /></el-icon>
    <span>{{
      interactive ? '图表数据已加载，请在右侧面板查看可视化结果。' : '图表数据已生成。'
    }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, toRef, watch } from 'vue';
import {
  CaretBottom,
  CaretTop,
  CircleCheckFilled,
  CopyDocument,
  DataAnalysis,
  Plus,
} from '@element-plus/icons-vue';
import SafeEcharts from '@/components/visualization/safe-echarts.vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { metadataJsonText, metadataText, useDefaultExpanded } from './message-part-context';
import {
  canAddVisualizationChartToLibrary,
  isVisualizationChartAdded,
  visualizationChartOption,
  visualizationPreviewValidationNotice,
} from './visualization-preview-state';
import { workflowRevision } from '../workflow-card-state';

const props = defineProps<{
  part: ChatMessagePart;
  interactive?: boolean;
  workflowCurrentRevision?: number;
}>();

const emit = defineEmits<{
  (e: 'copyCode', content: string): void;
  (e: 'addChartLibrary', part: ChatMessagePart): void;
  (e: 'chartRenderFailed', payload: { part: ChatMessagePart; error: string }): void;
  (
    e: 'decideAction',
    payload: {
      part: ChatMessagePart;
      decision: 'retry';
      detail?: string;
    },
  ): void;
}>();

const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));

const chartPreviewConfigText = computed(() => {
  return metadataJsonText(props.part, 'amisConfig') || props.part.content || '';
});

const workflowSummary = computed(() => {
  const workflow = metadataText(props.part, 'workflowId');
  if (!workflow) return '';
  const step = metadataText(props.part, 'step') || 'ARTIFACT_READY';
  const validation = metadataText(props.part, 'validationStatus') || 'unverified';
  return `工作流阶段：${step}；产物校验：${validation}`;
});

const isWorkflowCurrent = computed(() => {
  const current = props.workflowCurrentRevision;
  const own = workflowRevision(props.part);
  if (current === undefined || own === undefined) return true;
  return own >= current;
});

const workflowAllowedActions = computed(() => {
  const value = props.part.metadata?.allowedActions;
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string')
    : [];
});

const isWorkflowRetryable = computed(
  () =>
    isWorkflowCurrent.value &&
    props.part.type === 'visualization-chart-preview' &&
    workflowAllowedActions.value.includes('retry') &&
    (metadataText(props.part, 'step').toUpperCase() === 'BLOCKED' ||
      metadataText(props.part, 'validationStatus') === 'blocked'),
);

const workflowRetryInFlight = computed(() => props.part.metadata?.deciding === true);

const requestWorkflowRetry = () => {
  if (!props.interactive || !isWorkflowRetryable.value || workflowRetryInFlight.value) return;
  emit('decideAction', {
    part: props.part,
    decision: 'retry',
    detail: metadataText(props.part, 'validationMessage') || '重试当前失败阶段',
  });
};

const chartRenderFailed = ref(false);

const isChartLibraryAdded = computed(() => isVisualizationChartAdded(props.part));

const chartPreviewOption = computed(() => visualizationChartOption(props.part));

const canAddToChartLibrary = computed(() => canAddVisualizationChartToLibrary(
  props.part,
  chartRenderFailed.value,
));

const chartLibraryHint = computed(() => {
  if (isChartLibraryAdded.value) return '已加入图表库';
  if (chartRenderFailed.value) return '图表渲染失败，暂不能加入图表库';
  if (!chartPreviewOption.value) return '图表缺少可渲染配置，暂不能加入图表库';
  if (!canAddToChartLibrary.value) return '缺少图表入库标识，暂不能加入图表库';
  return '加入图表库';
});

const requestAddChartLibrary = () => {
  if (!props.interactive || !canAddToChartLibrary.value) {
    return;
  }
  emit('addChartLibrary', props.part);
};

const chartPreviewNotice = computed(() => visualizationPreviewValidationNotice(props.part));
const chartPreviewError = computed(() => '');

const handleChartRenderFailed = (error: string) => {
  chartRenderFailed.value = true;
  emit('chartRenderFailed', { part: props.part, error });
};

watch(chartPreviewOption, () => {
  chartRenderFailed.value = false;
});

const isVisualizationRecord = computed(() => {
  return [
    'visualization-chart-record',
    'visualization-app-record',
    'visualization-dashboard-record',
  ].includes(props.part.type);
});

const visualizationRecordTitle = computed(() => {
  if (props.part.type === 'visualization-chart-record') return props.part.title || '图表库记录';
  if (props.part.type === 'visualization-app-record')
    return props.part.title || '可视化应用记录';
  if (props.part.type === 'visualization-dashboard-record')
    return props.part.title || '数据大屏记录';
  return props.part.title || '可视化记录';
});
</script>
