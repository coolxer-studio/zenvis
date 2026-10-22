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

      <div v-if="currentToolText" class="run-current-tool">
        <el-icon><Tools /></el-icon>
        <span>{{ currentToolText }}</span>
      </div>

      <div v-if="run?.pausedReason" class="run-paused-reason">
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
import { computed, ref } from 'vue';
import {
  ArrowDown,
  ArrowUp,
  Check,
  CircleCheck,
  Clock,
  Loading,
  Tools,
  Warning,
  WarningFilled,
} from '@element-plus/icons-vue';
import type { AgentRun, AgentRunPlanStep } from '@/types/type-dih';
import { isAgentRunLive, resolveAgentRunStepStatus } from '@/views/dih/composables/agent-run-message';

const props = withDefaults(defineProps<{
  run?: AgentRun | null;
  currentTool?: Record<string, unknown> | null;
}>(), {
  run: null,
  currentTool: null,
});

const emit = defineEmits<{
  (e: 'resume', runId: string): void;
}>();

const expanded = ref(true);
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

.run-metrics {
  display: flex;
  padding: 10px 14px 12px;
  flex-wrap: wrap;
  gap: 6px 14px;
  color: var(--zv-text-muted);
  font-size: 11px;
}
</style>
