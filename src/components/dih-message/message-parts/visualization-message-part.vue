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
          :content="isChartLibraryAdded ? '已加入图表库' : '加入图表库'"
          placement="top"
        >
          <el-button
            class="config-copy-btn"
            size="small"
            :icon="isChartLibraryAdded ? CircleCheckFilled : Plus"
            circle
            :disabled="isChartLibraryAdded || !chartLibraryAction"
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
    <SafeEcharts
      :option="chartPreviewOption"
      :loading="part.status === 'loading'"
      :error="chartPreviewError"
      min-height="320px"
      @render-failed="emit('chartRenderFailed', { part, error: $event })"
    />
  </div>

  <div v-else-if="isDataVisualizationRecord" class="notice-part notice-info">
    <div class="notice-title">
      <el-icon><DataAnalysis /></el-icon>
      <span class="card-title-text">{{ dataVisualizationRecordTitle }}</span>
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
        (interactive ? '已记录到右侧数据可视化面板。' : '数据可视化记录')
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
import { computed, toRef } from 'vue';
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

const props = defineProps<{
  part: ChatMessagePart;
  interactive?: boolean;
}>();

const emit = defineEmits<{
  (e: 'copyCode', content: string): void;
  (e: 'addChartLibrary', part: ChatMessagePart): void;
  (e: 'chartRenderFailed', payload: { part: ChatMessagePart; error: string }): void;
}>();

const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));

const chartPreviewConfigText = computed(() => {
  return metadataJsonText(props.part, 'amisConfig') || props.part.content || '';
});

const chartLibraryAction = computed(() => {
  const action = metadataText(props.part, 'action');
  if (action !== 'data_visualization.add_chart_library') {
    return '';
  }
  const source = metadataText(props.part, 'source');
  const demo = metadataText(props.part, 'demoId');
  if (source === 'demo' && demo) {
    return action;
  }
  const workflow = metadataText(props.part, 'workflowId');
  const validationStatus = metadataText(props.part, 'validationStatus');
  return workflow && validationStatus === 'success' ? action : '';
});

const workflowSummary = computed(() => {
  const workflow = metadataText(props.part, 'workflowId');
  if (!workflow) return '';
  const step = metadataText(props.part, 'step') || 'ARTIFACT_READY';
  const validation = metadataText(props.part, 'validationStatus') || 'unverified';
  return `工作流阶段：${step}；产物校验：${validation}`;
});

const isChartLibraryAdded = computed(() => {
  return props.part.status === 'submitted' || props.part.status === 'added';
});

const requestAddChartLibrary = () => {
  if (!props.interactive || !chartLibraryAction.value || isChartLibraryAdded.value) {
    return;
  }
  emit('addChartLibrary', props.part);
};

const chartPreviewOption = computed(() => {
  const metadata = props.part.metadata as Record<string, any> | undefined;
  const value =
    metadata?.echartsOption ||
    metadata?.echarts?.option ||
    metadata?.data?.echarts?.option ||
    metadata?.option ||
    metadata?.amisConfig?.config ||
    metadata?.config?.config;
  if (typeof value === 'string') {
    try {
      return JSON.parse(value);
    } catch {
      return null;
    }
  }
  if (value && typeof value === 'object') {
    return value;
  }
  return null;
});

const chartPreviewError = computed(() => {
  const validationStatus = metadataText(props.part, 'validationStatus');
  if (validationStatus === 'blocked' || validationStatus === 'failed') {
    return metadataText(props.part, 'validationMessage') || '真实数据查询未完成';
  }
  return '';
});

const isDataVisualizationRecord = computed(() => {
  return [
    'visualization-chart-record',
    'visualization-config-record',
    'dashboard-config-record',
    'menu-config-record',
  ].includes(props.part.type);
});

const dataVisualizationRecordTitle = computed(() => {
  if (props.part.type === 'visualization-chart-record') return props.part.title || '图表库记录';
  if (props.part.type === 'visualization-config-record')
    return props.part.title || '可视化配置记录';
  if (props.part.type === 'dashboard-config-record') return props.part.title || '数据看板配置记录';
  if (props.part.type === 'menu-config-record') return props.part.title || '菜单配置记录';
  return props.part.title || '数据可视化记录';
});

</script>
