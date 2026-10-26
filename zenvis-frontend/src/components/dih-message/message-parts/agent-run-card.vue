<template>
  <section class="agent-run-card" :class="`is-${statusTone}`">
    <header class="agent-run-header" @click="expanded = !expanded">
      <span class="run-mark" aria-hidden="true">
        <el-icon :class="{ spinning: isActive }">
          <Loading v-if="isActive" />
          <Clock v-else-if="isWaiting" />
          <CircleCheck v-else />
        </el-icon>
      </span>
      <span class="run-title">
        <strong>智能体任务</strong>
        <small>{{ stepProgressText }}</small>
      </span>
      <el-tag size="small" effect="plain" :type="statusTagType">{{ statusText }}</el-tag>
      <el-icon class="run-chevron">
        <ArrowUp v-if="expanded" />
        <ArrowDown v-else />
      </el-icon>
    </header>

    <div v-if="run?.goal" class="run-goal" :class="{ 'is-clamped': !expanded }">
      {{ run.goal }}
    </div>

    <template v-if="expanded">
      <div v-if="steps.length" class="run-steps">
        <div
          v-for="step in steps"
          :key="step.id"
          class="run-step"
          :class="`is-${displayStepStatus(step.status).toLowerCase()}`"
        >
          <span class="step-marker">
            <el-icon v-if="step.status === 'DONE'"><Check /></el-icon>
            <span
              v-else-if="displayStepStatus(step.status) === 'RUNNING'"
              class="step-pulse"
            ></span>
            <el-icon v-else-if="step.status === 'BLOCKED'"><WarningFilled /></el-icon>
            <span v-else class="step-dot"></span>
          </span>
          <span class="step-copy">
            <strong>{{ step.title || step.id }}</strong>
            <small v-if="step.summary">{{ step.summary }}</small>
          </span>
          <span class="step-status">{{ stepStatusText(displayStepStatus(step.status)) }}</span>
        </div>
      </div>

      <div class="run-activity">
        <button
          type="button"
          class="run-activity-toggle"
          :aria-expanded="activityExpanded"
          :aria-controls="activityDetailsId"
          @click.stop="toggleActivity"
        >
          <span class="run-activity-title">
            <el-icon><Operation /></el-icon>
            <strong>执行过程</strong>
          </span>
          <span class="run-activity-summary">
            检索 {{ activitySummary.ragCount }} · 工具 {{ activitySummary.toolCount }} · 压缩 {{ activitySummary.compactionCount }}
          </span>
          <el-icon class="run-activity-chevron">
            <ArrowUp v-if="activityExpanded" />
            <ArrowDown v-else />
          </el-icon>
        </button>
        <div v-if="activityExpanded" :id="activityDetailsId" class="run-activity-body">
          <div v-if="activityLoading" class="run-activity-state">正在加载执行过程…</div>
          <button
            v-else-if="activityError"
            type="button"
            class="run-activity-state is-error"
            @click.stop="loadActivityEvents"
          >{{ activityError }}，点击重试</button>
          <AgentRunActivity v-else :activities="activities" />
        </div>
      </div>

      <div v-if="currentToolText" class="run-current-tool">
        <el-icon><Tools /></el-icon>
        <span>{{ currentToolText }}</span>
      </div>

      <div v-if="run?.status === 'WAITING_USER'" class="run-user-input-callout">
        <span class="run-user-input-copy">
          <strong>需要补充信息</strong>
          <small>{{ waitingQuestion || '当前任务正在等待你补充继续执行所需的信息。' }}</small>
          <small class="run-user-input-hint">请在页面底部输入框补充信息后发送。</small>
        </span>
      </div>

      <div v-else-if="run?.pausedReason" class="run-paused-reason">
        <el-icon><Warning /></el-icon>
        <span>{{ run.pausedReason }}</span>
      </div>

      <div v-if="run?.status === 'PAUSED'" class="run-actions">
        <el-button size="small" type="primary" @click.stop="emit('resume', run.runId)">
          继续任务
        </el-button>
        <span>继续后会补充新的步骤与时长预算</span>
      </div>

      <div class="run-metrics">
        <span>步骤 {{ run?.decisionSteps || 0 }}/{{ run?.maxSteps || 30 }}</span>
        <span>工具 {{ run?.toolCalls || 0 }}/{{ run?.maxToolCalls || 80 }}</span>
        <span>耗时 {{ effectiveTimeText }}</span>
        <span v-if="run?.recoveryCount">恢复 {{ run.recoveryCount }} 次</span>
      </div>
    </template>

  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  ArrowDown,
  ArrowUp,
  Check,
  CircleCheck,
  Clock,
  Loading,
  Operation,
  Tools,
  Warning,
  WarningFilled,
} from '@element-plus/icons-vue';
import { DihService } from '@/service/api';
import type {
  AgentRun,
  AgentRunEvent,
  AgentRunPlanStep,
} from '@/types/type-dih';
import {
  isAgentRunLive,
  resolveAgentRunStepStatus,
  resolveAgentRunWaitingQuestion,
} from '@/views/dih/composables/agent-run-message';
import {
  AGENT_RUN_ACTIVITY_EVENT_TYPES,
  buildAgentRunActivities,
  summarizeAgentRunActivities,
} from '@/views/dih/composables/agent-run-activity';
import AgentRunActivity from './agent-run-activity.vue';

const props = withDefaults(defineProps<{
  run?: AgentRun | null;
  currentTool?: Record<string, unknown> | null;
  activityEvents?: AgentRunEvent[];
}>(), {
  run: null,
  currentTool: null,
  activityEvents: () => [],
});

const emit = defineEmits<{
  (e: 'resume', runId: string): void;
}>();

const expanded = ref(true);
const activityExpanded = ref(false);
const activityLoading = ref(false);
const activityError = ref('');
const loadedRunId = ref('');
const fetchedActivityEvents = ref<AgentRunEvent[]>([]);
const steps = computed<AgentRunPlanStep[]>(() => props.run?.plan?.steps || []);
const isActive = computed(() => isAgentRunLive(props.run?.status));
const isWaiting = computed(() => props.run?.status === 'WAITING_APPROVAL'
  || props.run?.status === 'WAITING_USER'
  || props.run?.status === 'PAUSED');
const completedSteps = computed(() => steps.value.filter(step =>
  step.status === 'DONE' || step.status === 'SKIPPED',
).length);
const stepProgressText = computed(() => {
  if (!steps.value.length) return props.run?.statusLabel || '准备执行';
  return `${completedSteps.value}/${steps.value.length} 个步骤完成`;
});
const activityDetailsId = computed(() => `agent-run-activity-${props.run?.runId || 'unknown'}`);
const allActivityEvents = computed(() => {
  const runId = props.run?.runId || '';
  return [...fetchedActivityEvents.value, ...props.activityEvents]
    .filter(event => !runId || !event.run_id || event.run_id === runId);
});
const activities = computed(() => buildAgentRunActivities(allActivityEvents.value));
const activitySummary = computed(() => summarizeAgentRunActivities(activities.value));
const waitingQuestion = computed(() => resolveAgentRunWaitingQuestion(props.run));

const loadActivityEvents = async () => {
  const runId = props.run?.runId || '';
  if (!runId || activityLoading.value) return;
  activityLoading.value = true;
  activityError.value = '';
  try {
    const events: AgentRunEvent[] = [];
    let after = 0;
    for (let page = 0; page < 20; page += 1) {
      const batch = await DihService.listAgentRunEvents(
        runId,
        after,
        AGENT_RUN_ACTIVITY_EVENT_TYPES,
      );
      events.push(...batch);
      if (batch.length < 500) break;
      after = batch[batch.length - 1].sequence;
    }
    fetchedActivityEvents.value = events;
    loadedRunId.value = runId;
  } catch (error) {
    console.error('加载智能体执行过程失败:', error);
    activityError.value = '执行过程加载失败';
  } finally {
    activityLoading.value = false;
  }
};

const toggleActivity = async () => {
  activityExpanded.value = !activityExpanded.value;
  if (activityExpanded.value && loadedRunId.value !== (props.run?.runId || '')) {
    await loadActivityEvents();
  }
};

watch(() => props.run?.runId, () => {
  activityExpanded.value = false;
  activityLoading.value = false;
  activityError.value = '';
  loadedRunId.value = '';
  fetchedActivityEvents.value = [];
});

const statusText = computed(() => {
  const labels: Record<string, string> = {
    QUEUED: '排队中',
    RUNNING: '执行中',
    WAITING_APPROVAL: '等待审批',
    WAITING_USER: '等待补充信息',
    PAUSED: '已暂停',
    SUCCEEDED: '已完成',
    FAILED: '执行失败',
    CANCELING: '取消中',
    CANCELLED: '已取消',
  };
  return props.run?.status ? (labels[props.run.status] || props.run.statusLabel || props.run.status) : '准备中';
});

const statusTone = computed(() => (props.run?.status || 'QUEUED').toLowerCase());
const statusTagType = computed<'success' | 'warning' | 'info' | 'danger' | 'primary'>(() => {
  const status = props.run?.status;
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'WAITING_APPROVAL' || status === 'WAITING_USER' || status === 'PAUSED') return 'warning';
  if (status === 'RUNNING' || status === 'QUEUED') return 'primary';
  return 'info';
});

const currentToolText = computed(() => {
  const tool = props.currentTool;
  if (!tool) return '';
  const name = String(tool.toolName || '');
  const status = String(tool.status || '');
  if (!name) return '';
  return `当前工具：${name}${status ? `（${status}）` : ''}`;
});

const effectiveTimeText = computed(() => {
  const seconds = Math.max(0, Math.round((props.run?.effectiveMillis || 0) / 1000));
  if (seconds < 60) return `${seconds} 秒`;
  return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`;
});

const stepStatusText = (status?: string) => {
  const labels: Record<string, string> = {
    PENDING: '待执行',
    RUNNING: '进行中',
    PAUSED: '已暂停',
    DONE: '完成',
    SKIPPED: '跳过',
    BLOCKED: '阻塞',
  };
  return labels[String(status || 'PENDING').toUpperCase()] || String(status || '待执行');
};

const displayStepStatus = (status?: string) =>
  resolveAgentRunStepStatus(props.run?.status, status);
</script>

<style lang="scss" scoped>
.agent-run-card {
  box-sizing: border-box;
  width: min(100%, 760px);
  overflow: hidden;
  background: var(--zv-bg-card);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-lg);
  box-shadow: var(--zv-shadow-sm);
}

.agent-run-card.is-running,
.agent-run-card.is-queued {
  border-color: var(--zv-primary-border);
}

.agent-run-card.is-failed {
  border-color: color-mix(in srgb, var(--el-color-danger) 45%, var(--zv-border));
}

.agent-run-header {
  display: flex;
  padding: 12px 14px;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  background: var(--zv-bg-subtle);
}

.run-mark {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  color: var(--zv-primary);
  place-items: center;
  background: var(--zv-primary-soft);
  border-radius: 9px;
}

.spinning {
  animation: run-spin 1.1s linear infinite;
}

@keyframes run-spin {
  to { transform: rotate(360deg); }
}

.run-title {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.run-title strong {
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-sm);
}

.run-title small {
  margin-top: 2px;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}

.run-chevron {
  color: var(--zv-text-muted);
}

.run-goal {
  padding: 10px 14px 0;
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-sm);
  line-height: 1.6;
  white-space: pre-wrap;
}

.run-goal.is-clamped {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.run-steps {
  display: flex;
  padding: 12px 14px 4px;
  flex-direction: column;
  gap: 8px;
}

.run-step {
  display: flex;
  align-items: flex-start;
  gap: 9px;
}

.step-marker {
  display: grid;
  width: 20px;
  height: 20px;
  flex: 0 0 20px;
  margin-top: 1px;
  color: var(--zv-text-muted);
  place-items: center;
}

.run-step.is-done .step-marker {
  color: var(--el-color-success);
}

.run-step.is-blocked .step-marker {
  color: var(--el-color-danger);
}

.step-dot {
  width: 7px;
  height: 7px;
  background: currentColor;
  border-radius: 50%;
  opacity: 0.55;
}

.step-pulse {
  width: 8px;
  height: 8px;
  background: var(--zv-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--zv-primary) 18%, transparent);
}

.step-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.step-copy strong {
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-sm);
  font-weight: var(--zv-font-weight-medium);
}

.step-copy small {
  margin-top: 3px;
  overflow: hidden;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-status {
  flex: 0 0 auto;
  color: var(--zv-text-muted);
  font-size: 11px;
}

.run-current-tool,
.run-paused-reason {
  display: flex;
  margin: 8px 14px 0;
  padding: 8px 10px;
  align-items: center;
  gap: 7px;
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-xs);
  background: var(--zv-bg-subtle);
  border-radius: var(--zv-radius-md);
}

.run-activity {
  margin: 10px 14px 0;
}

.run-activity-toggle {
  display: flex;
  width: 100%;
  padding: 8px 10px;
  align-items: center;
  gap: 8px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--zv-bg-card);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-md);
}

.run-activity-toggle:hover {
  border-color: var(--zv-primary-border);
}

.run-activity-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-xs);
}

.run-activity-summary {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: var(--zv-text-muted);
  font-size: 11px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-activity-chevron {
  flex: 0 0 auto;
  color: var(--zv-text-muted);
}

.run-activity-body {
  padding-top: 9px;
}

.run-activity-state {
  width: 100%;
  padding: 10px 12px;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
  text-align: left;
  background: var(--zv-bg-subtle);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-md);
}

button.run-activity-state {
  cursor: pointer;
}

.run-activity-state.is-error {
  color: var(--el-color-danger);
}

.run-actions {
  display: flex;
  padding: 0 14px 12px;
  align-items: center;
  gap: 10px;
}

.run-actions span {
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}

.run-paused-reason {
  color: var(--el-color-warning-dark-2);
  background: color-mix(in srgb, var(--el-color-warning) 10%, var(--zv-bg-card));
}

.run-user-input-callout {
  display: flex;
  margin: 8px 14px 0;
  padding: 10px 12px;
  align-items: center;
  gap: 12px;
  background: color-mix(in srgb, var(--el-color-warning) 10%, var(--zv-bg-card));
  border: 1px solid color-mix(in srgb, var(--el-color-warning) 28%, var(--zv-border));
  border-radius: var(--zv-radius-md);
}

.run-user-input-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.run-user-input-copy strong {
  color: var(--el-color-warning-dark-2);
  font-size: var(--zv-font-size-xs);
}

.run-user-input-copy small {
  display: -webkit-box;
  overflow: hidden;
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-xs);
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.run-user-input-copy .run-user-input-hint {
  display: block;
  color: var(--zv-text-muted);
  -webkit-line-clamp: unset;
}

.run-metrics {
  display: flex;
  padding: 10px 14px 12px;
  flex-wrap: wrap;
  gap: 6px 14px;
  color: var(--zv-text-muted);
  font-size: 11px;
}
</style>
