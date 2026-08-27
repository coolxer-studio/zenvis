<template>
  <div
    class="dih-workspace"
    :class="{
      'has-inline-result': showInlineResult,
      'is-result-fullscreen': resultFullscreen,
    }"
  >
    <el-splitter v-if="!isMobile" class="workspace-splitter" direction="horizontal">
      <el-splitter-panel
        collapsible
        :resizable="true"
        :size="sidebarPanelSize"
        :min="showInlineResult ? '12%' : '16%'"
        :max="showInlineResult ? '28%' : '34%'"
      >
        <aside class="workspace-sidebar">
          <ViewLeft :system-info="systemInfo" :user-info="userInfo" />
        </aside>
      </el-splitter-panel>

      <el-splitter-panel
        collapsible
        :resizable="true"
        :size="conversationPanelSize"
        :min="showInlineResult ? '38%' : '50%'"
      >
        <main class="workspace-conversation">
          <ViewCenter
            :suggestions="mySuggestions"
            :skill-entry-unavailable="skillEntryUnavailable"
            :system-logo="systemLogo"
            :task-queue-status="taskQueueStatus"
            :task-queue-loading="taskQueueLoading"
            :task-queue-available="taskQueueAvailable"
            variant="workspace"
            :show-session-toggle="false"
            :show-result-toggle="hasRightPanel && !showInlineResult"
            @open-analysis-tasks="drawerVisible = true"
            @open-sessions="sessionDrawerVisible = true"
            @open-results="resultDrawerVisible = true"
          />
        </main>
      </el-splitter-panel>

      <el-splitter-panel
        v-if="showInlineResult"
        collapsible
        :resizable="true"
        :size="rightPanelSize"
        min="22%"
        max="42%"
      >
        <section class="workspace-result">
          <div class="result-shell">
            <header class="result-header">
              <div>
                <strong>{{ rightPanelTitle }}</strong>
                <small>{{ rightPanelDescription }}</small>
              </div>
              <div class="result-header-actions">
                <el-tooltip
                  :content="resultFullscreen ? '退出全屏' : '全屏查看'"
                  placement="bottom"
                >
                  <el-button
                    text
                    circle
                    :icon="resultFullscreen ? CloseBold : FullScreen"
                    :aria-label="resultFullscreen ? '退出结果全屏' : '全屏查看结果'"
                    @click="resultFullscreen = !resultFullscreen"
                  />
                </el-tooltip>
              </div>
            </header>
            <component :is="activeRightComponent" class="result-component" />
          </div>
        </section>
      </el-splitter-panel>
    </el-splitter>

    <main v-else class="workspace-conversation workspace-conversation-mobile">
      <ViewCenter
        :suggestions="mySuggestions"
        :skill-entry-unavailable="skillEntryUnavailable"
        :system-logo="systemLogo"
        :task-queue-status="taskQueueStatus"
        :task-queue-loading="taskQueueLoading"
        :task-queue-available="taskQueueAvailable"
        variant="workspace"
        :show-session-toggle="true"
        :show-result-toggle="hasRightPanel"
        @open-analysis-tasks="drawerVisible = true"
        @open-sessions="sessionDrawerVisible = true"
        @open-results="resultDrawerVisible = true"
      />
    </main>

    <el-drawer
      v-model="sessionDrawerVisible"
      class="dih-side-drawer"
      direction="ltr"
      :size="isMobile ? '86%' : '320px'"
      :with-header="false"
      append-to-body
    >
      <ViewLeft
        :system-info="systemInfo"
        :user-info="userInfo"
        @navigate="sessionDrawerVisible = false"
      />
    </el-drawer>

    <el-drawer
      v-model="resultDrawerVisible"
      class="dih-result-drawer"
      direction="rtl"
      :size="isMobile ? '94%' : '520px'"
      :with-header="false"
      append-to-body
    >
      <div class="result-shell drawer-result-shell">
        <header class="result-header">
          <div>
            <strong>{{ rightPanelTitle }}</strong>
            <small>{{ rightPanelDescription }}</small>
          </div>
          <el-button
            text
            circle
            :icon="Close"
            aria-label="关闭结果面板"
            @click="resultDrawerVisible = false"
          />
        </header>
        <component v-if="hasRightPanel" :is="activeRightComponent" class="result-component" />
      </div>
    </el-drawer>

    <div class="analysis-drawer" :class="{ 'is-open': drawerVisible }" aria-live="polite">
      <ViewDrawer
        :visible="drawerVisible"
        :queue-status="taskQueueStatus"
        @close="drawerVisible = false"
        @refresh-queue-status="loadTaskQueueStatus"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  ref,
  watch,
} from 'vue';
import { useRoute } from 'vue-router';
import { Close, CloseBold, FullScreen } from '@element-plus/icons-vue';
import {
  Connection,
  DataAnalysis,
  Document,
  MagicStick,
  Monitor,
  Operation,
} from '@element-plus/icons-vue';

import { AnalysisTaskService, DihService, SystemService } from '@/service/api';
import type { ChatSkillEntryVo } from '@/types/type-dih';
import type { TAnalysisTaskQueue } from '@/types/type-analysis-task';
import type { SystemInfo } from '@/types/type-system';
import { getUserInfo } from '@u/auth-session';
import { getAssetUrl } from '@u/url';
import ViewCenter from './components/view-center.vue';
import ViewDrawer from './components/view-drawer.vue';
import ViewLeft from './components/view-left.vue';
import ViewRightConfigManagement from './components/view-right-config-management.vue';
import ViewRightDataAccess from './components/view-right-data-access.vue';
import ViewRightDataAnalysis from './components/view-right-data-analysis.vue';
import ViewRightDataVisualization from './components/view-right-data-visualization.vue';
import ViewRightReport from './components/view-right-report.vue';
import {
  CONFIG_RECORD_EVENT,
  DATA_ACCESS_RECORD_EVENT,
  DATA_ANALYSIS_RECORD_EVENT,
  DATA_REPORT_RECORD_EVENT,
  DATA_VISUALIZATION_RECORD_EVENT,
  useDihEventListener,
} from './events';

type Suggestion = {
  type: string;
  agentType: string;
  label: string;
  icon: any;
};

type ResultAgentType =
  | 'agent_data_visualization'
  | 'agent_data_analysis'
  | 'agent_config_management'
  | 'agent_data_access'
  | 'agent_report';

const route = useRoute();
const drawerVisible = ref(false);
const sessionDrawerVisible = ref(false);
const resultDrawerVisible = ref(false);
const resultFullscreen = ref(false);
const viewportWidth = ref(window.innerWidth);
const mySuggestions = ref<Suggestion[]>([]);
const entriesLoaded = ref(false);
const systemInfo = ref<SystemInfo>();
const transientResultType = ref<ResultAgentType | ''>('');
const replayingEvent = ref('');
const userInfo = getUserInfo<{ name?: string; email?: string }>() || {};
const taskQueueStatus = ref<TAnalysisTaskQueue>({
  runningTask: null,
  nextTask: null,
  pendingCount: 0,
  readyCount: 0,
  runningCount: 0,
  waitingApprovalCount: 0,
  availableSlots: 0,
  maxSuspended: 0,
  checkedAt: '',
});
const taskQueueLoading = ref(true);
const taskQueueAvailable = ref(false);
let taskQueuePollingTimer: ReturnType<typeof setInterval> | undefined;
let taskQueueRefreshInFlight = false;

const agentIconMap: Record<string, any> = {
  agent_data_access: Connection,
  agent_data_visualization: Monitor,
  agent_data_analysis: DataAnalysis,
  agent_config_management: Operation,
  agent_report: Document,
};

const configuredIconMap: Record<string, any> = {
  connection: Connection,
  monitor: Monitor,
  'data-analysis': DataAnalysis,
  operation: Operation,
  document: Document,
  'magic-stick': MagicStick,
};

const rightComponentMap: Record<ResultAgentType, any> = {
  agent_data_visualization: ViewRightDataVisualization,
  agent_data_analysis: ViewRightDataAnalysis,
  agent_config_management: ViewRightConfigManagement,
  agent_data_access: ViewRightDataAccess,
  agent_report: ViewRightReport,
};

const rightPanelMeta: Record<ResultAgentType, { title: string; description: string }> = {
  agent_data_visualization: { title: '可视化结果', description: '图表、看板与菜单配置' },
  agent_data_analysis: { title: '分析结果', description: '数据集、服务结果与分析报告' },
  agent_config_management: { title: '配置工作区', description: '配置记录、试验和正式生效' },
  agent_data_access: { title: '数据接入结果', description: '元数据与数据推送服务' },
  agent_report: { title: '报表工作台', description: '文档、素材与交付产物' },
};

const routeChatType = computed(() => String(route.query.type || 'ask'));
const activeSuggestion = computed(() =>
  mySuggestions.value.find(item => item.type === routeChatType.value),
);
const routedAgentType = computed<ResultAgentType | ''>(() => {
  const type = activeSuggestion.value?.agentType || routeChatType.value;
  return type in rightComponentMap ? (type as ResultAgentType) : '';
});
const resolvedResultType = computed<ResultAgentType | ''>(
  () => routedAgentType.value || transientResultType.value,
);
const hasRightPanel = computed(() => Boolean(resolvedResultType.value));
const showInlineResult = computed(() => hasRightPanel.value && viewportWidth.value >= 1280);
const isMobile = computed(() => viewportWidth.value < 960);
const sidebarPanelSize = computed(() => {
  if (showInlineResult.value) return '17%';
  return viewportWidth.value < 1280 ? '22%' : '18%';
});
const rightPanelSize = computed(() => '26%');
const conversationPanelSize = computed(() =>
  showInlineResult.value ? '57%' : viewportWidth.value < 1280 ? '78%' : '82%',
);
const activeRightComponent = computed(() =>
  resolvedResultType.value ? rightComponentMap[resolvedResultType.value] : null,
);
const rightPanelTitle = computed(() =>
  resolvedResultType.value ? rightPanelMeta[resolvedResultType.value].title : '分析结果',
);
const rightPanelDescription = computed(() =>
  resolvedResultType.value ? rightPanelMeta[resolvedResultType.value].description : '暂无结果',
);
const systemLogo = computed(() =>
  systemInfo.value?.systemLogo ? getAssetUrl(systemInfo.value.systemLogo) : '',
);
const skillEntryUnavailable = computed(
  () => entriesLoaded.value && routeChatType.value.startsWith('skill:') && !activeSuggestion.value,
);

const toSuggestion = (entry: ChatSkillEntryVo): Suggestion => ({
  type: entry.chatType,
  agentType: entry.agentType,
  label: entry.label || entry.skillId,
  icon: configuredIconMap[entry.icon] || agentIconMap[entry.agentType] || MagicStick,
});

const loadWorkspaceData = async () => {
  const [entries, info] = await Promise.allSettled([
    DihService.getChatSkillEntries(true),
    SystemService.getSystemInfo(),
  ]);
  if (entries.status === 'fulfilled') {
    mySuggestions.value = entries.value.map(toSuggestion);
    entriesLoaded.value = true;
  }
  if (info.status === 'fulfilled') systemInfo.value = info.value;
};

const loadTaskQueueStatus = async () => {
  if (taskQueueRefreshInFlight) return;
  taskQueueRefreshInFlight = true;
  if (!taskQueueAvailable.value) taskQueueLoading.value = true;
  try {
    taskQueueStatus.value = await AnalysisTaskService.getQueueStatus(true);
    taskQueueAvailable.value = true;
  } catch {
    if (!taskQueueAvailable.value) taskQueueAvailable.value = false;
  } finally {
    taskQueueLoading.value = false;
    taskQueueRefreshInFlight = false;
  }
};

const stopTaskQueuePolling = () => {
  if (taskQueuePollingTimer) clearInterval(taskQueuePollingTimer);
  taskQueuePollingTimer = undefined;
};

const startTaskQueuePolling = () => {
  stopTaskQueuePolling();
  if (document.visibilityState !== 'visible') return;
  taskQueuePollingTimer = setInterval(() => void loadTaskQueueStatus(), 5000);
};

const handleVisibilityChange = () => {
  if (document.visibilityState === 'visible') {
    void loadTaskQueueStatus();
    startTaskQueuePolling();
    return;
  }
  stopTaskQueuePolling();
};

const hasPayload = (detail: unknown) => {
  if (!detail || typeof detail !== 'object') return false;
  return Object.entries(detail as Record<string, unknown>).some(([key, value]) => {
    if (['sessionId', 'sessionRecordId', 'extraData'].includes(key)) return false;
    if (Array.isArray(value)) return value.length > 0;
    if (value && typeof value === 'object') return Object.keys(value as object).length > 0;
    return Boolean(value);
  });
};

const revealResult = <T>(eventName: string, agentType: ResultAgentType, detail: T) => {
  if (replayingEvent.value === eventName) {
    replayingEvent.value = '';
    return;
  }
  if (routeChatType.value !== 'ask' || !hasPayload(detail)) return;
  const shouldReplay = !transientResultType.value;
  transientResultType.value = agentType;
  if (viewportWidth.value < 1280) resultDrawerVisible.value = true;
  if (shouldReplay) {
    void nextTick(() => {
      replayingEvent.value = eventName;
      window.dispatchEvent(new CustomEvent(eventName, { detail }));
    });
  }
};

useDihEventListener(DATA_VISUALIZATION_RECORD_EVENT, detail =>
  revealResult(DATA_VISUALIZATION_RECORD_EVENT, 'agent_data_visualization', detail),
);
useDihEventListener(DATA_ANALYSIS_RECORD_EVENT, detail =>
  revealResult(DATA_ANALYSIS_RECORD_EVENT, 'agent_data_analysis', detail),
);
useDihEventListener(CONFIG_RECORD_EVENT, detail =>
  revealResult(CONFIG_RECORD_EVENT, 'agent_config_management', detail),
);
useDihEventListener(DATA_ACCESS_RECORD_EVENT, detail =>
  revealResult(DATA_ACCESS_RECORD_EVENT, 'agent_data_access', detail),
);
useDihEventListener(DATA_REPORT_RECORD_EVENT, detail =>
  revealResult(DATA_REPORT_RECORD_EVENT, 'agent_report', detail),
);

const handleResize = () => {
  viewportWidth.value = window.innerWidth;
  if (viewportWidth.value >= 1280) resultDrawerVisible.value = false;
  if (viewportWidth.value >= 960) sessionDrawerVisible.value = false;
};

watch(
  () => route.query.chatSessionId,
  () => {
    transientResultType.value = '';
    resultDrawerVisible.value = false;
    resultFullscreen.value = false;
  },
);

watch(hasRightPanel, available => {
  if (!available) resultDrawerVisible.value = false;
});

onMounted(() => {
  void loadWorkspaceData();
  void loadTaskQueueStatus();
  startTaskQueuePolling();
  window.addEventListener('resize', handleResize, { passive: true });
  document.addEventListener('visibilitychange', handleVisibilityChange);
});

onActivated(() => {
  void loadWorkspaceData();
  void loadTaskQueueStatus();
  startTaskQueuePolling();
});

onDeactivated(stopTaskQueuePolling);

onBeforeUnmount(() => {
  stopTaskQueuePolling();
  window.removeEventListener('resize', handleResize);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});
</script>

<style lang="scss" scoped>
.dih-workspace {
  position: relative;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--zv-bg-page);
}

.workspace-splitter {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
}

.workspace-splitter :deep(.el-splitter-panel) {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.workspace-splitter :deep(.el-splitter-bar) {
  z-index: 4;
  width: 1px !important;
  background: var(--zv-divider);
  cursor: col-resize;
}

.workspace-splitter :deep(.el-splitter-bar__dragger-horizontal) {
  width: 12px;
  height: 100%;
  cursor: col-resize;
  touch-action: none;
}

.workspace-splitter :deep(.el-splitter-bar__dragger-horizontal::before) {
  width: 1px;
  background: var(--zv-divider);
  transition: background-color var(--zv-motion-fast) var(--zv-ease-standard);
}

.workspace-splitter :deep(.el-splitter-bar__dragger-horizontal:hover::before),
.workspace-splitter :deep(.el-splitter-bar__dragger-active::before) {
  width: 2px;
  background: var(--zv-primary);
}

.workspace-splitter :deep(.el-splitter-bar__collapse-icon) {
  width: 18px;
  height: 28px;
  color: var(--zv-text-secondary);
  opacity: 0;
  background: var(--zv-bg-surface);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-sm);
  box-shadow: var(--zv-shadow-1);
  cursor: pointer;
  pointer-events: none;
  transition: color var(--zv-motion-fast) var(--zv-ease-standard),
    opacity var(--zv-motion-fast) var(--zv-ease-standard),
    background-color var(--zv-motion-fast) var(--zv-ease-standard),
    border-color var(--zv-motion-fast) var(--zv-ease-standard);
}

.workspace-splitter :deep(.el-splitter-bar:hover .el-splitter-bar__collapse-icon),
.workspace-splitter :deep(.el-splitter-bar__collapse-icon:focus-visible) {
  opacity: 0.92;
  pointer-events: auto;
}

.workspace-splitter :deep(.el-splitter-bar__horizontal-collapse-icon-start),
.workspace-splitter :deep(.el-splitter-bar__horizontal-collapse-icon-end) {
  top: 88px;
}

.workspace-splitter :deep(.el-splitter-bar__collapse-icon:hover) {
  color: var(--zv-primary);
  opacity: 1;
  background: var(--zv-primary-soft);
  border-color: var(--zv-primary-border);
}

.workspace-sidebar,
.workspace-conversation,
.workspace-result {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--zv-bg-surface);
}

.result-shell {
  display: flex;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  flex-direction: column;
  background: var(--zv-bg-subtle);
}

.result-header {
  display: flex;
  min-height: 72px;
  padding: 14px 16px;
  align-items: center;
  justify-content: space-between;
  gap: var(--zv-space-3);
  background: var(--zv-bg-surface);
  border-bottom: 1px solid var(--zv-divider);
}

.result-header strong,
.result-header small {
  display: block;
}

.result-header strong {
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-lg);
  font-weight: var(--zv-font-weight-semibold);
}

.result-header small {
  margin-top: var(--zv-space-1);
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}

.result-component {
  flex: 1;
  min-height: 0;
}

.is-result-fullscreen .workspace-result {
  position: fixed;
  z-index: 30;
  inset: var(--zv-space-3);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-lg);
  box-shadow: var(--zv-shadow-3);
}

.analysis-drawer {
  position: absolute;
  z-index: 50;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  opacity: 0;
  background: var(--zv-bg-surface);
  transform: translateY(-18px);
  transition: opacity var(--zv-motion-normal) var(--zv-ease-standard),
    transform var(--zv-motion-normal) var(--zv-ease-standard);
}

.analysis-drawer.is-open {
  pointer-events: auto;
  opacity: 1;
  transform: translateY(0);
}

@media (max-width: 1279px) {
  .workspace-splitter :deep(.el-splitter-bar__collapse-icon) {
    opacity: 0;
  }

  .workspace-splitter :deep(.el-splitter-bar:hover .el-splitter-bar__collapse-icon) {
    opacity: 0.92;
  }
}

@media (max-width: 959px) {
  .workspace-conversation-mobile {
    width: 100%;
    height: 100%;
    border-right: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .analysis-drawer {
    transition: none;
  }
}
</style>

<style lang="scss">
.dih-side-drawer .el-drawer__body,
.dih-result-drawer .el-drawer__body {
  padding: 0;
  overflow: hidden;
}

.dih-side-drawer,
.dih-result-drawer {
  background: var(--zv-bg-surface);
}

.drawer-result-shell {
  height: 100%;
}

.dih-workspace .result-shell > .right-panel,
.dih-result-drawer .result-shell > .right-panel {
  display: flex;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  flex-direction: column;
  color: var(--zv-text-primary);
  background: var(--zv-bg-subtle);
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.dih-workspace .right-tabs,
.dih-result-drawer .right-tabs {
  min-height: 0;
  background: var(--zv-bg-subtle);
}

.dih-workspace .right-tabs > .el-tabs__header,
.dih-result-drawer .right-tabs > .el-tabs__header {
  margin: 0;
  padding: 0 14px;
  background: var(--zv-bg-surface);
  border-bottom: 1px solid var(--zv-divider);
}

.dih-workspace .right-tabs .el-tabs__nav-wrap::after,
.dih-result-drawer .right-tabs .el-tabs__nav-wrap::after {
  display: none;
}

.dih-workspace .right-tabs .el-tabs__item,
.dih-result-drawer .right-tabs .el-tabs__item {
  height: 42px;
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-sm);
  line-height: 42px;
}

.dih-workspace .right-tabs .el-tabs__item.is-active,
.dih-result-drawer .right-tabs .el-tabs__item.is-active {
  color: var(--zv-primary);
  font-weight: var(--zv-font-weight-semibold);
}

.dih-workspace .right-tabs > .el-tabs__content,
.dih-result-drawer .right-tabs > .el-tabs__content {
  box-sizing: border-box;
  height: calc(100% - 43px);
  padding: var(--zv-space-3);
  overflow: auto;
}

.dih-workspace .right-panel .el-table,
.dih-result-drawer .right-panel .el-table {
  background: var(--zv-bg-surface);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-md);
  box-shadow: var(--zv-shadow-1);
}

.dih-workspace .analysis-drawer .drawer-container {
  width: 100%;
  height: 100%;
  min-height: 0;
  background: var(--zv-bg-page);
}

.dih-workspace .analysis-drawer .drawer-header {
  min-height: 66px;
  padding: 12px 18px;
  background: var(--zv-bg-surface);
  border-bottom: 1px solid var(--zv-divider);
  box-shadow: var(--zv-shadow-1);
}

.dih-workspace .analysis-drawer .drawer-content {
  padding: var(--zv-space-4);
}
</style>
