<template>
  <div class="view-type chart-shell">
    <div ref="chartRef" class="chart-canvas"></div>
    <div v-if="!series.length" class="chart-empty">暂无实体上报数据</div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { init, type ECharts, type EChartsOption } from 'echarts';

import type { TEntitySeries } from '@/types/type-dashboard';

defineOptions({ name: 'EntityTrendChart' });

const props = withDefaults(
  defineProps<{
    xAxis?: string[];
    series?: TEntitySeries[];
  }>(),
  {
    xAxis: () => [],
    series: () => [],
  },
);

const chartRef = ref<HTMLElement | null>(null);
let chart: ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

const renderChart = () => {
  if (!chart) return;
  const option: EChartsOption = {
    title: {
      top: 10,
      textStyle: { color: '#fff', fontSize: 14, fontWeight: 'normal' },
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#6a7985' } },
    },
    legend: {
      type: 'scroll',
      data: props.series.map(item => item.label),
      top: 10,
      right: 20,
      textStyle: { color: '#fff' },
      pageTextStyle: { color: '#fff' },
    },
    grid: { top: 60, left: 20, right: 20, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: props.xAxis,
      axisLabel: { color: '#fff' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#fff' },
    },
    series: props.series.map(item => ({
      name: item.label,
      type: 'line',
      stack: 'Total',
      data: item.data,
      areaStyle: {},
      emphasis: { focus: 'series' },
    })),
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

watch(() => [props.xAxis, props.series], renderChart, { deep: true });

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  chart?.dispose();
  resizeObserver = null;
  chart = null;
});
</script>
