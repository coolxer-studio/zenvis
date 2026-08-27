<template>
  <div ref="chartRef" :style="chartStyle"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, computed, nextTick } from 'vue';
import * as echarts from 'echarts';
import type { EChartsOption, ECharts } from 'echarts';

// 定义组件名称
defineOptions({
  name: 'BaseChart',
});

// Props 定义
interface Props {
  option: EChartsOption;
  width?: string;
  height?: string;
  autoResize?: boolean;
  theme?: string | object;
  loading?: boolean;
  loadingOptions?: object;
}

const props = withDefaults(defineProps<Props>(), {
  width: '100%',
  height: '300px',
  autoResize: true,
  theme: '',
  loading: false,
  loadingOptions: () => ({
    text: '加载中...',
    color: '#409EFF',
    textColor: '#000',
    maskColor: 'rgba(255, 255, 255, 0.8)',
  }),
});

// Emits 定义
const emit = defineEmits<{
  (e: 'chartInit', chart: ECharts): void;
  (e: 'click', params: any): void;
  (e: 'mouseover', params: any): void;
  (e: 'mouseout', params: any): void;
}>();

// 响应式变量
const chartRef = ref<HTMLDivElement | null>(null);
let chartInstance: ECharts | null = null;

// 计算样式
const chartStyle = computed(() => ({
  width: props.width,
  height: props.height,
}));

// 初始化图表
const initChart = () => {
  if (!chartRef.value) return;

  // 如果已存在实例，先销毁
  if (chartInstance) {
    chartInstance.dispose();
  }

  // 创建新实例
  chartInstance = echarts.init(chartRef.value, props.theme);

  // 设置配置
  if (props.option) {
    chartInstance.setOption(props.option);
  }

  // 绑定事件
  bindEvents();

  // 触发初始化事件
  emit('chartInit', chartInstance);
};

// 绑定事件
const bindEvents = () => {
  if (!chartInstance) return;

  chartInstance.on('click', (params) => {
    emit('click', params);
  });

  chartInstance.on('mouseover', (params) => {
    emit('mouseover', params);
  });

  chartInstance.on('mouseout', (params) => {
    emit('mouseout', params);
  });
};

// 更新图表配置
const setOption = (option: EChartsOption, notMerge = false, lazyUpdate = false) => {
  if (chartInstance) {
    chartInstance.setOption(option, notMerge, lazyUpdate);
  }
};

// 调整图表大小
const resize = () => {
  if (chartInstance) {
    chartInstance.resize();
  }
};

// 获取图表实例
const getChartInstance = (): ECharts | null => {
  return chartInstance;
};

// 显示加载动画
const showLoading = () => {
  if (chartInstance) {
    chartInstance.showLoading('default', props.loadingOptions);
  }
};

// 隐藏加载动画
const hideLoading = () => {
  if (chartInstance) {
    chartInstance.hideLoading();
  }
};

// 清空图表
const clear = () => {
  if (chartInstance) {
    chartInstance.clear();
  }
};

// 销毁图表
const dispose = () => {
  if (chartInstance) {
    chartInstance.dispose();
    chartInstance = null;
  }
};

// 监听 option 变化
watch(
  () => props.option,
  (newOption) => {
    if (chartInstance && newOption) {
      chartInstance.setOption(newOption, true);
    }
  },
  { deep: true }
);

// 监听 loading 状态
watch(
  () => props.loading,
  (loading) => {
    if (loading) {
      showLoading();
    } else {
      hideLoading();
    }
  }
);

// 监听主题变化
watch(
  () => props.theme,
  () => {
    // 主题变化需要重新初始化
    nextTick(() => {
      initChart();
    });
  }
);

// 窗口大小变化处理
const handleResize = () => {
  resize();
};

// 生命周期钩子
onMounted(() => {
  nextTick(() => {
    initChart();

    // 自动响应窗口大小变化
    if (props.autoResize) {
      window.addEventListener('resize', handleResize);
    }

    // 处理初始 loading 状态
    if (props.loading) {
      showLoading();
    }
  });
});

onBeforeUnmount(() => {
  if (props.autoResize) {
    window.removeEventListener('resize', handleResize);
  }
  dispose();
});

// 暴露方法给父组件
defineExpose({
  setOption,
  resize,
  getChartInstance,
  showLoading,
  hideLoading,
  clear,
  dispose,
});
</script>

<style scoped>
/* 组件默认样式 */
</style>
