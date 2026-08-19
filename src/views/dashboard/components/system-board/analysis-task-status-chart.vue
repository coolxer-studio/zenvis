<template>
  <div class="view-device chart-shell">
    <div ref="chartRef" class="chart-canvas"></div>
    <div v-if="!hasData" class="chart-empty">暂无AI分析任务</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { init, type ECharts, type EChartsOption } from 'echarts';

import type { TAnalysisTaskStatus } from '@/types/type-dashboard';

defineOptions({ name: 'AnalysisTaskStatusChart' });

const props = withDefaults(defineProps<{
  data?: TAnalysisTaskStatus[];
  theme?: 'dark' | 'light';
}>(), {
  data: () => [],
  theme: 'dark',
});

const chartRef = ref<HTMLElement | null>(null);
const hasData = computed(() => props.data.some(item => item.count > 0));
let chart: ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

const renderChart = () => {
  if (!chart) return;
  const textColor = props.theme === 'light' ? '#24496f' : '#fff';
  const option: EChartsOption = {
    title: {
      text: 'AI分析任务状态分布',
      top: 10,
      textStyle: { color: textColor, fontSize: 14, fontWeight: 'normal' },
    },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { top: 70, left: 0, right: 30, bottom: 10, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: textColor },
    },
    yAxis: {
      type: 'category',
      data: props.data.map(item => item.description),
      axisLabel: { color: textColor },
    },
    series: [
      {
        name: '任务数',
        type: 'bar',
        data: props.data.map(item => item.count),
        emphasis: { focus: 'series' },
      },
    ],
  };
  chart.setOption(option, true);
};

onMounted(() => {
  if (!chartRef.value) return;
  chart = init(chartRef.value);
  resizeObserver = new ResizeObserver(() => chart?.resize());
  resizeObserver.observe(chartRef.value);
  renderChart();
});

watch(() => [props.data, props.theme], renderChart, { deep: true });

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  chart?.dispose();
  resizeObserver = null;
  chart = null;
});
</script>
