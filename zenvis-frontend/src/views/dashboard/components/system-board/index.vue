<template>
  <div class="dashboard-wrap" :class="`theme-${boardTheme}`">
    <div class="title-bar">系统运行总览</div>

    <div class="theme-switch" role="group" aria-label="大屏主题色">
      <button
        v-for="item in themeOptions"
        :key="item.value"
        type="button"
        :class="{ active: boardTheme === item.value }"
        :aria-pressed="boardTheme === item.value"
        @click="boardTheme = item.value"
      >
        {{ item.label }}
      </button>
    </div>

    <div class="message-bar">
      <span v-for="item in summaryCards" :key="item.key" class="message-bar-item">
        <b class="animation-1"></b>
        <b class="animation-2"></b>
        <b class="animation-3"></b>
        <p>{{ item.label }}</p>
        <strong>{{ item.value }}</strong>
        <small v-if="item.hint">{{ item.hint }}</small>
      </span>

      <div class="time-bar">
        <h2>
          <strong>{{ overview?.status_description || '状态未知' }}</strong>
          <span>系统综合运行状态</span>
          <b class="logoline"></b>
          <b class="logoline1"></b>
          <b class="logoline2"></b>
          <b class="logoline3"></b>
        </h2>
        <div class="date-timer">
          <div>
            <strong>{{ timeData.hours }}</strong>
            <strong>{{ timeData.minutes }}</strong>
          </div>
          <em>{{ timeData.weekday }}</em>
          <ul>
            <li>{{ timeData.year }}</li>
            <li>{{ timeData.month }}</li>
            <li>{{ timeData.day }}</li>
          </ul>
        </div>
      </div>
    </div>

    <div class="middle">
      <div class="left-area">
        <ul>
          <li v-for="notice in notices" :key="notice.key">
            <span class="submenu-item">
              <b>{{ notice.count }}</b>
              <span>{{ notice.info }}<em></em></span>
            </span>
          </li>
        </ul>
      </div>

      <div class="center-area">
        <div class="area-button">
          <button
            v-for="item in rangeOptions"
            :key="item.value"
            class="transparent-border-button"
            :class="{ 'button-active': activeRange === item.value }"
            type="button"
            @click="changeRange(item.value)"
          >
            {{ item.label }}
          </button>
        </div>
        <div class="pandect-area">
          <div class="pandect-area-center">
            <entity-trend-chart
              :x-axis="statistics?.x_axis || []"
              :series="statistics?.series || []"
              :theme="boardTheme"
            />
          </div>
        </div>
        <span v-if="statisticsHint" class="statistics-hint">{{ statisticsHint }}</span>
        <div v-if="statisticsLoading" class="board-state">统计数据加载中...</div>
        <div v-else-if="statisticsError" class="board-state error">实体统计加载失败</div>
      </div>

      <div class="right-area">
        <h3>业务服务健康度</h3>
        <div class="area-inbox-1">
          <dl>
            <dt>正常实例</dt>
            <dd class="font12">
              <span>{{ serviceHealth.up_count }}</span
              ><b></b>
            </dd>

            <dt class="ml-20">异常实例</dt>
            <dd class="font-red ml-20">
              <span>{{ serviceHealth.abnormal_count }}</span
              ><b></b>
            </dd>

            <dt>近 24 小时事件</dt>
            <dd>
              <span>{{ serviceHealth.event_count_24h }}</span
              ><b></b>
            </dd>
          </dl>

          <div class="round-1"></div>
          <div class="round-2"></div>
          <div class="round-3">{{ healthRatioText }}</div>
          <div class="round-4"></div>
        </div>
      </div>
    </div>

    <div class="chart-bar">
      <div class="chart-bar-item details1-area">
        <div class="details1-area-center">
          <business-service-status-chart
            :data="overview?.business_service_status || []"
            :theme="boardTheme"
          />
        </div>
        <div v-if="overviewLoading" class="board-state">系统概览加载中...</div>
        <div v-else-if="overviewError" class="board-state error">系统概览加载失败</div>
      </div>
      <div class="chart-bar-item details2-area">
        <div class="details2-area-center">
          <analysis-task-status-chart
            :data="overview?.analysis_task_status || []"
            :theme="boardTheme"
          />
        </div>
        <div v-if="overviewLoading" class="board-state">系统概览加载中...</div>
        <div v-else-if="overviewError" class="board-state error">系统概览加载失败</div>
      </div>
    </div>

    <div class="bottom-area">
      <div class="area-text">
        <div class="ambient-layer" aria-hidden="true">
          <i></i>
          <i></i>
          <i></i>
        </div>
        <div class="msg-content">
          <div class="feed-heading">
            <span class="live-dot"></span>
            <h4>运行动态</h4>
            <small>实时更新</small>
          </div>
          <div
            ref="textOuterRef"
            class="text-outer"
            @mouseenter="stopScroll"
            @mouseleave="startScroll"
          >
            <div ref="textInnerRef" class="text-inner">
              <template v-if="tickerItems.length">
                <template v-for="copy in tickerItems.length > 1 ? 2 : 1" :key="'ticker-' + copy">
                  <span v-for="item in tickerItems" :key="copy + '-' + item.id" class="text-item">
                    {{ item.text }}
                  </span>
                </template>
              </template>
              <span v-else class="text-item">暂无最新运行动态</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';

import dayjs from 'dayjs';

import { HomeService } from '@/service/api';
import type {
  TEntityStatisticsRange,
  TEntityStatisticsResponse,
  TServiceHealth,
  TSystemNotice,
  TSystemOverviewResponse,
} from '@/types/type-dashboard';
import AnalysisTaskStatusChart from './analysis-task-status-chart.vue';
import BusinessServiceStatusChart from './business-service-status-chart.vue';
import EntityTrendChart from './entity-trend-chart.vue';

defineOptions({ name: 'SystemBoard' });

const rangeOptions: Array<{ label: string; value: TEntityStatisticsRange }> = [
  { label: '今日', value: 'TODAY' },
  { label: '昨天', value: 'YESTERDAY' },
  { label: '最近7天', value: 'LAST_7_DAYS' },
];

type TBoardTheme = 'dark' | 'light';

const themeOptions: Array<{ label: string; value: TBoardTheme }> = [
  { label: '深色', value: 'dark' },
  { label: '浅色', value: 'light' },
];

const boardTheme = ref<TBoardTheme>('light');

const EMPTY_HEALTH: TServiceHealth = {
  ratio: null,
  instance_count: 0,
  up_count: 0,
  abnormal_count: 0,
  event_count_24h: 0,
};

const DEFAULT_NOTICES: TSystemNotice[] = [
  { key: 'business_service', count: 0, info: '业务服务状态未知', level: 'WARNING' },
  { key: 'push_task', count: 0, info: '数据推送状态未知', level: 'WARNING' },
  { key: 'analysis_approval', count: 0, info: 'AI任务状态未知', level: 'WARNING' },
];

const activeRange = ref<TEntityStatisticsRange>('TODAY');
const overview = ref<TSystemOverviewResponse | null>(null);
const statistics = ref<TEntityStatisticsResponse | null>(null);
const overviewLoading = ref(true);
const statisticsLoading = ref(true);
const overviewError = ref(false);
const statisticsError = ref(false);
const textOuterRef = ref<HTMLElement | null>(null);
const textInnerRef = ref<HTMLElement | null>(null);

const timeData = reactive({
  year: '',
  month: '',
  day: '',
  weekday: '',
  hours: '',
  minutes: '',
});

const formatCount = (value: number | null | undefined) =>
  value == null ? '--' : new Intl.NumberFormat('zh-CN').format(value);

const summaryCards = computed(() => {
  const summary = overview.value?.summary;
  return [
    { key: 'entity', label: '实体类型', value: formatCount(summary?.entity_count), hint: '' },
    {
      key: 'push-task',
      label: '数据推送任务',
      value: overview.value?.push_task_source_available
        ? formatCount(summary?.push_task_count)
        : '--',
      hint: overview.value && !overview.value.push_task_source_available ? '服务不可用' : '',
    },
    {
      key: 'analysis-task',
      label: 'AI 分析任务',
      value: formatCount(summary?.analysis_task_count),
      hint: '',
    },
    {
      key: 'business-service',
      label: '业务应用服务',
      value: formatCount(summary?.business_service_count),
      hint: '',
    },
  ];
});

const notices = computed(() => overview.value?.notices || DEFAULT_NOTICES);
const serviceHealth = computed(() => overview.value?.service_health || EMPTY_HEALTH);
const healthRatioText = computed(() =>
  serviceHealth.value.ratio == null ? '--' : serviceHealth.value.ratio + '%',
);
const statisticsHint = computed(() => {
  if (!statistics.value) return '';
  const messages: string[] = [];
  if (statistics.value.omitted_entity_count > 0) {
    messages.push('另有' + statistics.value.omitted_entity_count + '个实体未展示');
  }
  if (statistics.value.skipped_entities.length > 0) {
    messages.push(statistics.value.skipped_entities.length + '个实体未参与统计');
  }
  return messages.join('，');
});
const tickerItems = computed(() => {
  const items: Array<{ id: string | number; text: string }> = [];
  if (overview.value && !overview.value.push_task_source_available) {
    items.push({ id: 'push-unavailable', text: '数据推送服务当前不可用' });
  }
  if (statisticsHint.value) {
    items.push({ id: 'statistics-hint', text: statisticsHint.value });
  }
  for (const item of overview.value?.recent_analysis_tasks || []) {
    const taskName = item.name || '#' + item.id;
    const updateTime = item.update_time
      ? ' · ' + dayjs(item.update_time).format('MM-DD HH:mm:ss')
      : '';
    items.push({
      id: item.id,
      text: '任务「' + taskName + '」：' + (item.status_description || '状态未知') + updateTime,
    });
  }
  return items;
});

let clockTimer: number | undefined;
let overviewTimer: number | undefined;
let statisticsTimer: number | undefined;
let scrollTimer: number | undefined;
let scrollRestartTimer: number | undefined;
let overviewController: AbortController | null = null;
let statisticsController: AbortController | null = null;

const updateClock = () => {
  const now = dayjs();
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
  timeData.year = now.format('YYYY年');
  timeData.month = now.format('M月');
  timeData.day = now.format('D日');
  timeData.weekday = weekdays[now.day()];
  timeData.hours = now.format('HH:');
  timeData.minutes = now.format('mm');
};

const isCanceled = (error: unknown) => {
  const canceled = error as { code?: string; name?: string };
  return canceled?.code === 'ERR_CANCELED' || canceled?.name === 'AbortError';
};

const loadOverview = async (silent = false) => {
  overviewController?.abort();
  overviewController = new AbortController();
  if (!overview.value) overviewLoading.value = true;
  try {
    overview.value = await HomeService.getOverview({
      silent,
      signal: overviewController.signal,
    });
    overviewError.value = false;
  } catch (error) {
    if (!isCanceled(error)) overviewError.value = true;
  } finally {
    overviewLoading.value = false;
  }
};

const loadEntityStatistics = async (silent = false) => {
  statisticsController?.abort();
  statisticsController = new AbortController();
  const requestedRange = activeRange.value;
  statisticsLoading.value = true;
  try {
    const data = await HomeService.getEntityStatistics(requestedRange, {
      silent,
      signal: statisticsController.signal,
    });
    if (activeRange.value === requestedRange) {
      statistics.value = data;
      statisticsError.value = false;
    }
  } catch (error) {
    if (!isCanceled(error)) statisticsError.value = true;
  } finally {
    if (activeRange.value === requestedRange) statisticsLoading.value = false;
  }
};

const changeRange = (range: TEntityStatisticsRange) => {
  if (activeRange.value === range) return;
  activeRange.value = range;
  loadEntityStatistics();
};

const stopScroll = () => {
  if (scrollTimer) {
    window.clearInterval(scrollTimer);
    scrollTimer = undefined;
  }
};

const startScroll = () => {
  stopScroll();
  const outer = textOuterRef.value;
  const inner = textInnerRef.value;
  if (!outer || !inner || inner.offsetHeight <= outer.offsetHeight) return;
  scrollTimer = window.setInterval(() => {
    const resetHeight = tickerItems.value.length > 1 ? inner.offsetHeight / 2 : inner.offsetHeight;
    if (outer.scrollTop >= resetHeight) outer.scrollTop = 0;
    outer.scrollTop += 1;
  }, 100);
};

const restartScroll = async () => {
  await nextTick();
  stopScroll();
  if (scrollRestartTimer) window.clearTimeout(scrollRestartTimer);
  scrollRestartTimer = window.setTimeout(startScroll, 100);
};

watch(tickerItems, restartScroll, { deep: true });
watch(boardTheme, value => window.localStorage.setItem('system-board-theme', value));

onMounted(() => {
  const savedTheme = window.localStorage.getItem('system-board-theme');
  if (savedTheme === 'dark' || savedTheme === 'light') boardTheme.value = savedTheme;
  updateClock();
  loadOverview();
  loadEntityStatistics();
  clockTimer = window.setInterval(updateClock, 1000);
  overviewTimer = window.setInterval(() => loadOverview(true), 30_000);
  statisticsTimer = window.setInterval(() => {
    if (activeRange.value !== 'YESTERDAY') loadEntityStatistics(true);
  }, 60_000);
  restartScroll();
});

onBeforeUnmount(() => {
  overviewController?.abort();
  statisticsController?.abort();
  stopScroll();
  if (clockTimer) window.clearInterval(clockTimer);
  if (overviewTimer) window.clearInterval(overviewTimer);
  if (statisticsTimer) window.clearInterval(statisticsTimer);
  if (scrollRestartTimer) window.clearTimeout(scrollRestartTimer);
});
</script>

<style lang="less" scoped src="./style.less"></style>
