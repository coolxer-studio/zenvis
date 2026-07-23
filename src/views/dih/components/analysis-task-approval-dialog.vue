<template>
  <el-dialog
    :model-value="visible"
    :title="`MCP工具审批${task ? ` - ${task.name}` : ''}`"
    width="90vw"
    top="5vh"
    destroy-on-close
    append-to-body
    @closed="stopPolling"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-alert
      title="审批仅对当前任务 execution 生效；拒绝后 Agent 会在不执行该工具的情况下继续。"
      type="warning"
      :closable="false"
      show-icon
      class="approval-alert"
    />

    <el-table
      v-loading="loading"
      :data="rows"
      border
      height="430"
      empty-text="暂无待审批的MCP工具调用"
    >
      <el-table-column prop="toolName" label="工具" min-width="170">
        <template #default="{ row }">{{ row.toolName || row.toolKey }}</template>
      </el-table-column>
      <el-table-column prop="serverName" label="来源" min-width="140">
        <template #default="{ row }">{{ row.serverName || 'ZenVis内置工具' }}</template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="riskLevel" label="风险" width="90">
        <template #default="{ row }">
          <el-tag :type="riskType(row.riskLevel)" size="small">{{
            riskLabel(row.riskLevel)
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="调用参数" min-width="180">
        <template #default="{ row }">
          <el-popover placement="left" :width="520" trigger="click">
            <template #reference>
              <el-button link type="primary">查看参数</el-button>
            </template>
            <pre class="json-content">{{ formatJson(row.argumentsSummary) }}</pre>
          </el-popover>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="审批" fixed="right" width="310">
        <template #default="{ row }">
          <div class="approval-actions">
            <el-button
              size="small"
              type="primary"
              :loading="decisionRequestId === row.requestId"
              @click="decide(row, 'approved')"
            >
              允许本次
            </el-button>
            <el-button
              size="small"
              type="success"
              :loading="decisionRequestId === row.requestId"
              @click="decide(row, 'approved_task')"
            >
              本任务一直允许
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :loading="decisionRequestId === row.requestId"
              @click="decide(row, 'rejected')"
            >
              拒绝
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadApprovals"
        @size-change="handleSizeChange"
      />
    </div>

    <template #footer>
      <el-button :loading="loading" @click="loadApprovals(false)">刷新</el-button>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';
import { AnalysisTaskService } from '@/service/api';
import type {
  TAnalysisTask,
  TAnalysisTaskDecision,
  TMcpInvocation,
} from '@/types/type-analysis-task';

defineOptions({ name: 'AnalysisTaskApprovalDialog' });

type Props = {
  visible: boolean;
  task: TAnalysisTask | null;
};

const props = defineProps<Props>();
const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void;
  (event: 'changed'): void;
}>();

const rows = ref<TMcpInvocation[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const decisionRequestId = ref('');
let pollingTimer: ReturnType<typeof setInterval> | undefined;
let loadInFlight = false;

const formatTime = (value: string) => {
  if (!value) return '-';
  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : value;
};

const formatJson = (value: string) => {
  if (!value) return '-';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
};

const riskType = (risk: string) => {
  const value = risk.toLowerCase();
  if (value === 'high' || value === 'critical') return 'danger';
  if (value === 'medium') return 'warning';
  if (value === 'low') return 'success';
  return 'info';
};

const riskLabel = (risk: string) => {
  const labels: Record<string, string> = {
    critical: '严重',
    high: '高',
    medium: '中',
    low: '低',
    unknown: '未知',
  };
  return labels[risk.toLowerCase()] || risk || '未知';
};

const loadApprovals = async (silent = false) => {
  if (!props.task || loadInFlight) return;
  loadInFlight = true;
  if (!silent) loading.value = true;
  try {
    const response = await AnalysisTaskService.getPendingApprovals(
      props.task.id,
      page.value,
      pageSize.value,
      silent,
    );
    rows.value = response.rows;
    total.value = response.total;
  } catch (error) {
    if (!silent) console.error('获取AI分析任务审批列表失败:', error);
  } finally {
    loadInFlight = false;
    if (!silent) loading.value = false;
  }
};

const handleSizeChange = () => {
  page.value = 1;
  loadApprovals();
};

const decisionCopy: Record<
  TAnalysisTaskDecision,
  { confirm: string; success: string; type: 'warning' | 'error' }
> = {
  approved: {
    confirm: '确认允许本次MCP工具调用？',
    success: '已允许本次工具调用',
    type: 'warning',
  },
  approved_task: {
    confirm: '本次执行后续调用相同工具将不再询问，确认继续？',
    success: '当前任务 execution 已持续允许该工具',
    type: 'warning',
  },
  rejected: {
    confirm: '确认拒绝本次MCP工具调用？分析 Agent 会在不执行该工具的情况下继续。',
    success: '已拒绝本次工具调用',
    type: 'error',
  },
};

const decide = async (row: TMcpInvocation, decision: TAnalysisTaskDecision) => {
  if (!props.task || decisionRequestId.value) return;
  const copy = decisionCopy[decision];
  try {
    await ElMessageBox.confirm(copy.confirm, 'MCP工具审批', {
      type: copy.type,
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  decisionRequestId.value = row.requestId;
  try {
    await AnalysisTaskService.decideApproval(props.task.id, row.requestId, decision);
    ElMessage.success(copy.success);
    await loadApprovals(true);
    emit('changed');
  } catch (error) {
    console.error('提交MCP工具审批决定失败:', error);
  } finally {
    decisionRequestId.value = '';
  }
};

const stopPolling = () => {
  if (pollingTimer) clearInterval(pollingTimer);
  pollingTimer = undefined;
};

const startPolling = () => {
  stopPolling();
  pollingTimer = setInterval(() => loadApprovals(true), 3000);
};

watch(
  () => props.visible,
  visible => {
    if (!visible) {
      stopPolling();
      return;
    }
    page.value = 1;
    rows.value = [];
    total.value = 0;
    loadApprovals();
    startPolling();
  },
);

onBeforeUnmount(stopPolling);
</script>

<style scoped lang="scss">
.approval-alert {
  margin-bottom: 14px;
}

.approval-actions {
  display: flex;
  flex-wrap: nowrap;
}

.json-content {
  box-sizing: border-box;
  max-height: 420px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  color: #d8dee9;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: #202630;
  border-radius: 6px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
