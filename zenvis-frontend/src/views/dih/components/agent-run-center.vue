<template>
  <div class="agent-run-center">
    <header class="run-center-header">
      <div>
        <h2>智能体任务</h2>
        <p>持久化任务、审批与事件记录</p>
      </div>
      <div class="run-center-actions">
        <el-select v-model="statusFilter" clearable placeholder="全部状态" class="status-filter">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button :icon="Refresh" :loading="loading" @click="loadRuns">刷新</el-button>
      </div>
    </header>

    <el-table
      v-loading="loading"
      :data="runs"
      class="run-table"
      height="calc(100vh - 230px)"
      empty-text="暂无智能体任务"
      @row-click="openRun"
    >
      <el-table-column label="任务" min-width="250">
        <template #default="{ row }">
          <div class="run-title-cell">
            <strong>{{ runGoal(row) }}</strong>
            <small>{{ row.chatType || row.agentType }} · {{ row.runId }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="112">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="statusTagType(row.status)">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="100">
        <template #default="{ row }">
          <span class="muted">{{ completedSteps(row) }}/{{ row.plan?.steps?.length || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="工具" width="82">
        <template #default="{ row }">
          <span class="muted">{{ row.toolCalls }}/{{ row.maxToolCalls }}</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="166">
        <template #default="{ row }">{{ formatTime(row.updateTime || row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="232" fixed="right">
        <template #default="{ row }">
          <div class="row-actions" @click.stop>
            <el-button size="small" text type="primary" @click="openChat(row)">会话</el-button>
            <el-button
              v-if="['PAUSED', 'WAITING_USER'].includes(row.status)"
              size="small"
              text
              type="primary"
              :loading="runAction.id === row.runId && runAction.action === 'resume'"
              @click="resumeRun(row)"
            >继续</el-button>
            <el-button
              v-else-if="!isTerminal(row.status) && row.status !== 'CANCELING'"
              size="small"
              text
              :loading="runAction.id === row.runId && runAction.action === 'pause'"
              @click="pauseRun(row)"
            >暂停</el-button>
            <el-button
              v-if="!isTerminal(row.status) && row.status !== 'CANCELING'"
              size="small"
              text
              type="danger"
              :loading="runAction.id === row.runId && runAction.action === 'cancel'"
              @click="cancelRun(row)"
            >取消</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <footer class="run-center-footer">
      <span>共 {{ total }} 个任务</span>
      <el-pagination
        v-model:current-page="page"
        :page-size="perPage"
        layout="prev, pager, next"
        :total="total"
        @current-change="loadRuns"
      />
    </footer>

    <el-dialog v-model="detailVisible" width="min(920px, 92vw)" class="run-detail-dialog">
      <template #header>
        <div class="detail-header">
          <strong>任务详情</strong>
          <el-tag v-if="detail" size="small" effect="plain" :type="statusTagType(detail.status)">
            {{ statusLabel(detail.status) }}
          </el-tag>
        </div>
      </template>
      <div v-if="detail" class="detail-body">
        <div class="detail-goal">{{ runGoal(detail) }}</div>
        <div class="detail-meta">
          <span>Run ID：{{ detail.runId }}</span>
          <span>步骤：{{ detail.decisionSteps }}/{{ detail.maxSteps }}</span>
          <span>工具：{{ detail.toolCalls }}/{{ detail.maxToolCalls }}</span>
          <span>有效耗时：{{ formatDuration(detail.effectiveMillis) }}</span>
        </div>
        <el-alert
          v-if="detail.pausedReason || detail.lastError"
          :title="detail.pausedReason || detail.lastError"
          :type="detail.status === 'FAILED' ? 'error' : 'warning'"
          :closable="false"
          show-icon
        />

        <section v-if="detail.plan?.steps?.length" class="detail-section">
          <h3>执行计划</h3>
          <div class="detail-steps">
            <div v-for="step in detail.plan.steps" :key="step.id" class="detail-step">
              <el-tag size="small" effect="plain" :type="stepTagType(step.status)">
                {{ step.status || 'PENDING' }}
              </el-tag>
              <span class="detail-step-copy">
                <strong>{{ step.title || step.id }}</strong>
                <small v-if="step.summary">{{ step.summary }}</small>
              </span>
            </div>
          </div>
        </section>

        <section v-if="approval" class="detail-section approval-section">
          <h3>待审批工具</h3>
          <p><strong>{{ approval.toolName || approval.toolKey }}</strong>：{{ approval.description }}</p>
          <div class="approval-actions">
            <el-button type="primary" size="small" :loading="approvalDeciding === 'approved'" @click="decideApproval('approved')">允许本次</el-button>
            <el-button v-if="approval.runApprovalAllowed" type="success" plain size="small" :loading="approvalDeciding === 'approved_run'" @click="decideApproval('approved_run')">本任务同类工具始终允许</el-button>
            <el-button type="danger" plain size="small" :loading="approvalDeciding === 'rejected'" @click="decideApproval('rejected')">拒绝</el-button>
          </div>
        </section>

        <section class="detail-section">
          <div class="section-title-row">
            <h3>事件记录</h3>
            <el-button text size="small" :icon="Refresh" @click="loadEvents(detail.runId)">刷新</el-button>
          </div>
          <div class="event-list">
            <div v-for="event in runEvents" :key="event.sequence" class="event-row">
              <span class="event-seq">#{{ event.sequence }}</span>
              <el-tag size="small" effect="plain">{{ event.type }}</el-tag>
              <span class="event-time">{{ formatTime(event.created_at) }}</span>
              <span class="event-summary">{{ eventSummary(event) }}</span>
            </div>
            <el-empty v-if="!runEvents.length" description="暂无事件" :image-size="48" />
          </div>
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { DihService } from '@/service/api';
import type {
  AgentRun,
  AgentRunEvent,
  AgentRunStatus,
  McpApprovalData,
} from '@/types/type-dih';

const emit = defineEmits<{
  (event: 'open-chat', run: AgentRun): void;
}>();

const loading = ref(false);
const runs = ref<AgentRun[]>([]);
const total = ref(0);
const page = ref(1);
const perPage = 20;
const statusFilter = ref<AgentRunStatus | ''>('');
const detailVisible = ref(false);
const detail = ref<AgentRun | null>(null);
const runEvents = ref<AgentRunEvent[]>([]);
const approval = ref<McpApprovalData | null>(null);
const approvalDeciding = ref('');
const runAction = ref<{ id: string; action: string }>({ id: '', action: '' });
let refreshTimer: number | null = null;

const statusOptions = [
  { value: 'QUEUED', label: '等待执行' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'WAITING_APPROVAL', label: '等待审批' },
  { value: 'WAITING_USER', label: '等待用户' },
  { value: 'PAUSED', label: '已暂停' },
  { value: 'SUCCEEDED', label: '已完成' },
  { value: 'FAILED', label: '已失败' },
  { value: 'CANCELING', label: '取消中' },
  { value: 'CANCELLED', label: '已取消' },
];

const statusLabelMap = Object.fromEntries(statusOptions.map(item => [item.value, item.label])) as Record<string, string>;
const statusLabel = (status?: string) => (status ? statusLabelMap[status] || status : '-');
const isTerminal = (status?: string) => ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(String(status));
const statusTagType = (status?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' => {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'PAUSED' || status === 'WAITING_APPROVAL' || status === 'WAITING_USER') return 'warning';
  if (status === 'RUNNING' || status === 'QUEUED') return 'primary';
  return 'info';
};
const stepTagType = (status?: string): 'success' | 'warning' | 'danger' | 'info' => {
  if (status === 'DONE' || status === 'SKIPPED') return 'success';
  if (status === 'RUNNING') return 'warning';
  if (status === 'BLOCKED') return 'danger';
  return 'info';
};
const runGoal = (run: AgentRun) => run.goal || run.finalAnswer || '未命名任务';
const completedSteps = (run: AgentRun) => (run.plan?.steps || [])
  .filter(step => step.status === 'DONE' || step.status === 'SKIPPED').length;
const formatTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
};
const formatDuration = (millis: number) => {
  const seconds = Math.max(0, Math.round((millis || 0) / 1000));
  if (seconds < 60) return `${seconds} 秒`;
  return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`;
};

const eventSummary = (event: AgentRunEvent) => {
  const data = event.data || {};
  const text = data.message || data.reason || data.content || data.toolName
    || data.question || data.status || data.sectionKey || '';
  return String(text).slice(0, 160);
};

const loadRuns = async () => {
  loading.value = true;
  try {
    const response = await DihService.listAgentRuns({
      status: statusFilter.value || undefined,
      page: page.value,
      perPage,
    });
    runs.value = response.rows;
    total.value = response.total;
    if (detail.value) {
      const refreshed = response.rows.find(item => item.runId === detail.value?.runId);
      if (refreshed) {
        detail.value = refreshed;
        await loadApproval(refreshed);
      }
    }
  } catch (error) {
    console.error('加载智能体任务失败:', error);
  } finally {
    loading.value = false;
  }
};

const loadEvents = async (runId: string) => {
  try {
    runEvents.value = await DihService.listAgentRunEvents(runId, 0);
  } catch (error) {
    console.error('加载任务事件失败:', error);
  }
};

const loadApproval = async (run: AgentRun) => {
  approval.value = null;
  if (!run.pendingAction) return;
  try {
    const parsed = JSON.parse(run.pendingAction) as Record<string, unknown>;
    const requestId = String(parsed.requestId || '');
    if (!requestId) return;
    approval.value = await DihService.getMcpApproval(requestId);
  } catch (error) {
    console.warn('加载任务审批失败:', error);
  }
};

const openRun = async (run: AgentRun) => {
  detail.value = run;
  detailVisible.value = true;
  await Promise.all([loadEvents(run.runId), loadApproval(run)]);
};

const openChat = (run: AgentRun) => {
  emit('open-chat', run);
};

const setRun = (updated: AgentRun) => {
  runs.value = runs.value.map(item => item.runId === updated.runId ? updated : item);
  if (detail.value?.runId === updated.runId) detail.value = updated;
};

const pauseRun = async (run: AgentRun) => {
  runAction.value = { id: run.runId, action: 'pause' };
  try {
    setRun(await DihService.pauseAgentRun(run.runId));
    ElMessage.success('任务已暂停');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '暂停任务失败');
  } finally {
    runAction.value = { id: '', action: '' };
  }
};

const resumeRun = async (run: AgentRun) => {
  runAction.value = { id: run.runId, action: 'resume' };
  try {
    setRun(await DihService.resumeAgentRun(run.runId));
    ElMessage.success('任务已继续');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '继续任务失败');
  } finally {
    runAction.value = { id: '', action: '' };
  }
};

const cancelRun = async (run: AgentRun) => {
  try {
    await ElMessageBox.confirm('取消后已完成的外部写入不会回滚，确定停止该任务？', '停止任务', {
      confirmButtonText: '停止',
      cancelButtonText: '返回',
      type: 'warning',
    });
  } catch {
    return;
  }
  runAction.value = { id: run.runId, action: 'cancel' };
  try {
    setRun(await DihService.cancelAgentRun(run.runId));
    ElMessage.success('已请求停止任务');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '停止任务失败');
  } finally {
    runAction.value = { id: '', action: '' };
  }
};

const decideApproval = async (decision: 'approved' | 'approved_run' | 'rejected') => {
  if (!approval.value?.requestId) return;
  approvalDeciding.value = decision;
  try {
    await DihService.decideMcpApproval(approval.value.requestId, { decision });
    approval.value = null;
    ElMessage.success(decision === 'rejected' ? '已拒绝工具调用' : '已批准工具调用');
    await loadRuns();
    if (detail.value) await Promise.all([loadEvents(detail.value.runId), DihService.getAgentRun(detail.value.runId).then(setRun)]);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审批操作失败');
  } finally {
    approvalDeciding.value = '';
  }
};

watch(statusFilter, () => {
  page.value = 1;
  void loadRuns();
});

onMounted(() => {
  void loadRuns();
  refreshTimer = window.setInterval(() => {
    if (!detailVisible.value) void loadRuns();
  }, 5000);
});

onUnmounted(() => {
  if (refreshTimer !== null) window.clearInterval(refreshTimer);
});
</script>

<style lang="scss" scoped>
.agent-run-center {
  display: flex;
  box-sizing: border-box;
  height: 100%;
  padding: 16px 18px 12px;
  flex-direction: column;
  background: var(--zv-bg-card);
}

.run-center-header,
.run-center-footer,
.detail-header,
.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.run-center-header h2 {
  margin: 0;
  color: var(--zv-text-primary);
  font-size: 18px;
}

.run-center-header p {
  margin: 4px 0 0;
  color: var(--zv-text-muted);
  font-size: 12px;
}

.run-center-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-filter {
  width: 150px;
}

.run-table {
  margin-top: 14px;
  border-radius: var(--zv-radius-lg);
}

.run-title-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.run-title-cell strong {
  overflow: hidden;
  color: var(--zv-text-primary);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-title-cell small,
.muted {
  overflow: hidden;
  color: var(--zv-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-actions {
  display: flex;
  align-items: center;
}

.run-center-footer {
  margin-top: auto;
  padding-top: 10px;
  color: var(--zv-text-muted);
  font-size: 12px;
}

.detail-body {
  display: flex;
  max-height: 72vh;
  overflow-y: auto;
  flex-direction: column;
  gap: 14px;
}

.detail-goal {
  color: var(--zv-text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
  white-space: pre-wrap;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  color: var(--zv-text-muted);
  font-size: 12px;
}

.detail-section {
  padding-top: 12px;
  border-top: 1px solid var(--zv-divider);
}

.detail-section h3 {
  margin: 0 0 10px;
  color: var(--zv-text-primary);
  font-size: 14px;
}

.detail-steps,
.event-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-step,
.event-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
}

.detail-step-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.detail-step-copy strong {
  color: var(--zv-text-secondary);
  font-size: 13px;
}

.detail-step-copy small {
  color: var(--zv-text-muted);
  font-size: 11px;
}

.approval-section p {
  margin: 0 0 10px;
  color: var(--zv-text-secondary);
  line-height: 1.6;
}

.approval-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.event-row {
  padding: 7px 9px;
  align-items: center;
  background: var(--zv-bg-subtle);
  border-radius: var(--zv-radius-md);
  font-size: 12px;
}

.event-seq {
  width: 48px;
  flex: 0 0 48px;
  color: var(--zv-text-muted);
  font-variant-numeric: tabular-nums;
}

.event-time {
  width: 138px;
  flex: 0 0 138px;
  color: var(--zv-text-muted);
}

.event-summary {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: var(--zv-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .run-center-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .run-center-actions {
    width: 100%;
  }

  .status-filter {
    flex: 1;
  }

  .event-time {
    display: none;
  }
}
</style>
