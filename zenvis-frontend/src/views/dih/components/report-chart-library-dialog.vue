<template>
  <el-dialog
    :model-value="modelValue"
    class="chart-library-dialog"
    data-testid="chart-library-dialog"
    title="插入图表库图表"
    width="980px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="chart-library-layout">
      <aside class="chart-library-sidebar">
        <div class="chart-library-toolbar">
          <el-input
            v-model="keyword"
            :prefix-icon="Search"
            clearable
            placeholder="搜索图表名称、会话或类型"
          />
          <el-button :icon="Refresh" :loading="loading" @click="emit('refresh')"> 刷新 </el-button>
        </div>
        <div class="chart-library-summary">
          图表库 {{ charts.length }} 项
          <span v-if="loading">· 更新中</span>
        </div>
        <el-alert
          v-if="error"
          class="chart-library-error"
          type="error"
          :closable="false"
          show-icon
          :title="error"
        />
        <el-empty
          v-if="!loading && !charts.length"
          :image-size="68"
          description="图表库暂无图表，请先在临时图表智能体中加入图表库"
        />
        <el-empty
          v-else-if="!filteredCharts.length"
          :image-size="54"
          description="没有匹配的图表"
        />
        <div v-else class="chart-library-list">
          <button
            v-for="chart in filteredCharts"
            :key="chartKey(chart)"
            :data-testid="`chart-library-item-${chartKey(chart)}`"
            type="button"
            class="chart-library-item"
            :class="{ active: chartKey(chart) === selectedId }"
            @click="selectedId = chartKey(chart)"
          >
            <span class="chart-library-item-icon">
              <el-icon><DataAnalysis /></el-icon>
            </span>
            <span class="chart-library-item-main">
              <span class="chart-library-item-name">{{
                chart.name || chart.title || '未命名图表'
              }}</span>
              <span class="chart-library-item-meta">
                {{ chartTypeLabel(chart.chartType) }} · {{ chart.sessionTitle || '未知来源会话' }}
              </span>
              <span v-if="chart.addedAt" class="chart-library-item-time">
                加入于 {{ formatTime(chart.addedAt) }}
              </span>
            </span>
            <el-tag v-if="!chartOptionFromLibraryItem(chart)" size="small" type="warning">
              缺少快照
            </el-tag>
          </button>
        </div>
      </aside>

      <section class="chart-library-preview">
        <template v-if="selectedChart">
          <header class="chart-library-preview-header">
            <div>
              <h4>{{ selectedChart.name || selectedChart.title || '未命名图表' }}</h4>
              <p>
                {{ chartTypeLabel(selectedChart.chartType) }}
                <span v-if="selectedChart.sessionTitle"> · {{ selectedChart.sessionTitle }}</span>
              </p>
            </div>
            <el-tag size="small" type="success" effect="plain">图表库</el-tag>
          </header>
          <SafeEcharts
            v-if="selectedOption"
            :option="selectedOption"
            min-height="390px"
            @render-failed="handleRenderFailed"
          />
          <el-alert
            v-else
            class="chart-library-preview-error"
            type="warning"
            :closable="false"
            show-icon
            title="该图表没有可渲染的 ECharts 快照，无法插入报表"
          />
          <p v-if="selectedChart.description" class="chart-library-description">
            {{ selectedChart.description }}
          </p>
        </template>
        <el-empty v-else :image-size="74" description="从左侧选择一张图表预览" />
      </section>
    </div>

    <template #footer>
      <div class="chart-library-footer">
        <span>{{
          renderError
            ? '该图表无法渲染，暂不能插入'
            : selectedChart
            ? `将插入：${selectedChart.name || '未命名图表'}`
            : '请选择图表'
        }}</span>
        <div>
          <el-button @click="emit('update:modelValue', false)">取消</el-button>
          <el-button
            type="primary"
            :disabled="!canInsert"
            :loading="inserting"
            @click="canInsert && selectedChart && emit('insert', selectedChart)"
          >
            插入到光标
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { DataAnalysis, Refresh, Search } from '@element-plus/icons-vue';
import SafeEcharts from '@/components/visualization/safe-echarts.vue';
import type { ReportChartLibraryItem } from '@/types/type-dih';
import { chartOptionFromLibraryItem, chartTypeLabel } from '@/utils/report-chart-library';

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    charts: ReportChartLibraryItem[];
    loading?: boolean;
    inserting?: boolean;
    error?: string;
  }>(),
  {
    loading: false,
    inserting: false,
    error: '',
  },
);

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void;
  (event: 'refresh'): void;
  (event: 'insert', chart: ReportChartLibraryItem): void;
}>();

const keyword = ref('');
const selectedId = ref('');
const renderError = ref('');

const chartKey = (chart: ReportChartLibraryItem) =>
  String(chart.id || chart.artifactId || chart.name || '');

const filteredCharts = computed(() => {
  const term = keyword.value.trim().toLowerCase();
  if (!term) return props.charts;
  return props.charts.filter(chart =>
    [
      chart.name,
      chart.title,
      chart.sessionTitle,
      chart.chartType,
      chartTypeLabel(chart.chartType),
    ].some(value =>
      String(value || '')
        .toLowerCase()
        .includes(term),
    ),
  );
});

const selectedChart = computed(
  () =>
    filteredCharts.value.find(chart => chartKey(chart) === selectedId.value) ||
    props.charts.find(chart => chartKey(chart) === selectedId.value) ||
    null,
);

const selectedOption = computed(() => chartOptionFromLibraryItem(selectedChart.value));
const canInsert = computed(() =>
  Boolean(selectedChart.value && selectedOption.value && !renderError.value),
);

const handleRenderFailed = (error: string) => {
  renderError.value = error || '图表无法渲染';
};

const formatTime = (value: string) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
};

watch(
  () => [props.modelValue, filteredCharts.value] as const,
  ([open, charts]) => {
    if (!open) return;
    if (charts.some(chart => chartKey(chart) === selectedId.value)) return;
    const preferred = charts.find(chart => chartOptionFromLibraryItem(chart)) || charts[0];
    selectedId.value = preferred ? chartKey(preferred) : '';
  },
  { immediate: true },
);

watch(
  () => props.modelValue,
  open => {
    if (open) {
      keyword.value = '';
      renderError.value = '';
    }
  },
);

watch(selectedOption, () => {
  renderError.value = '';
});
</script>

<style lang="scss" scoped>
.chart-library-layout {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 18px;
  min-height: 500px;
}

.chart-library-sidebar {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding-right: 16px;
  border-right: 1px solid #ebeef5;
}

.chart-library-toolbar {
  display: flex;
  gap: 8px;

  .el-input {
    flex: 1;
  }
}

.chart-library-summary {
  padding: 9px 2px 8px;
  color: #909399;
  font-size: 12px;
}

.chart-library-error {
  margin-bottom: 8px;
}

.chart-library-list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
}

.chart-library-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  color: #303133;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, background-color 0.15s ease;

  &:hover,
  &.active {
    border-color: #409eff;
    background: #f5f9ff;
  }
}

.chart-library-item-icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #f0f9eb;
  color: #67c23a;
  flex: 0 0 auto;
}

.chart-library-item-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.chart-library-item-name,
.chart-library-item-meta,
.chart-library-item-time {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-library-item-name {
  font-size: 13px;
  font-weight: 600;
}

.chart-library-item-meta,
.chart-library-item-time {
  color: #909399;
  font-size: 12px;
}

.chart-library-preview {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 2px 2px 0;
}

.chart-library-preview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;

  h4 {
    margin: 0;
    color: #303133;
    font-size: 16px;
  }

  p {
    margin: 5px 0 0;
    color: #909399;
    font-size: 12px;
  }
}

.chart-library-preview-error {
  margin-top: 60px;
}

.chart-library-description {
  margin: 10px 0 0;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
}

.chart-library-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 900px) {
  .chart-library-layout {
    grid-template-columns: 1fr;
  }

  .chart-library-sidebar {
    max-height: 300px;
    padding-right: 0;
    border-right: 0;
    border-bottom: 1px solid #ebeef5;
  }
}
</style>
