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
    <div v-if="part.content" class="visualization-chart-preview-desc">{{ part.content }}</div>
    <div ref="chartPreviewEl" class="visualization-chart-preview-canvas"></div>
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
import { computed, nextTick, onBeforeUnmount, ref, toRef, watch } from 'vue';
import {
  CaretBottom,
  CaretTop,
  CircleCheckFilled,
  CopyDocument,
  DataAnalysis,
  Plus,
} from '@element-plus/icons-vue';
import * as echarts from 'echarts';
import { useWindowResize } from '@/composables/use-window-resize';
import type { ChatMessagePart } from '@/types/type-dih';
import { metadataJsonText, metadataText, useDefaultExpanded } from './message-part-context';

const props = defineProps<{
  part: ChatMessagePart;
  interactive?: boolean;
}>();

const emit = defineEmits<{
  (e: 'copyCode', content: string): void;
  (e: 'addChartLibrary', part: ChatMessagePart): void;
}>();

const chartPreviewEl = ref<HTMLElement | null>(null);
let chartPreviewInstance: ReturnType<typeof echarts.init> | null = null;
const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));

const chartPreviewConfigText = computed(() => {
  return metadataJsonText(props.part, 'amisConfig') || props.part.content || '';
});

const chartLibraryAction = computed(() => {
  const action = metadataText(props.part, 'action');
  return action === 'data_visualization.add_chart_library' ? action : '';
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

const chartPreviewOption = () => {
  const metadata = props.part.metadata as Record<string, any> | undefined;
  const value =
    metadata?.echarts?.option ||
    metadata?.data?.echarts?.option ||
    metadata?.echartsOption ||
    metadata?.option;
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
};

const renderChartPreview = () => {
  const option = chartPreviewOption();
  if (!chartPreviewEl.value || !option) {
    return;
  }
  if (!chartPreviewInstance) {
    chartPreviewInstance = echarts.init(chartPreviewEl.value);
  }
  chartPreviewInstance.setOption(option, true);
  chartPreviewInstance.resize();
};

useWindowResize(() => chartPreviewInstance?.resize());

watch(
  () => props.part,
  () => {
    void nextTick(renderChartPreview);
  },
  { deep: true, immediate: true },
);

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

onBeforeUnmount(() => {
  chartPreviewInstance?.dispose();
  chartPreviewInstance = null;
});
</script>
