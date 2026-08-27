<template>
  <div class="view-location chart-shell">
    <div ref="chartRef" class="chart-canvas"></div>
    <div v-if="!hasData" class="chart-empty">暂无业务应用服务实例</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { init, type ECharts, type EChartsOption } from 'echarts';

import type { TBusinessServiceStatus } from '@/types/type-dashboard';

defineOptions({ name: 'BusinessServiceStatusChart' });

const props = withDefaults(
  defineProps<{
    data?: TBusinessServiceStatus[];
    theme?: 'dark' | 'light';
  }>(),
  {
    data: () => [],
    theme: 'dark',
  },
);

const chartRef = ref<HTMLElement | null>(null);
const hasData = computed(() => props.data.some(item => item.count > 0));
let chart: ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

const renderChart = () => {
  if (!chart) return;
  const textColor = props.theme === 'light' ? '#47556b' : '#b8c4dc';
  const rows = [...props.data].reverse();
  const option: EChartsOption = {
    title: {
      text: '业务应用服务状态',
      top: 10,
      textStyle: { color: textColor, fontSize: 14, fontWeight: 'normal' },
    },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { top: 60, left: 30, right: 20, bottom: 10, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: textColor },
    },
    yAxis: {
      type: 'category',
      data: rows.map(item => item.description),
      axisLabel: { color: textColor },
    },
    series: [
      {
        name: '实例数',
        type: 'bar',
        itemStyle: { color: '#2f5ee5', borderRadius: [0, 4, 4, 0] },
        data: rows.map(item => item.count),
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
