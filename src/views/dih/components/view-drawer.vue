<template>
  <div class="drawer-container">
    <header class="drawer-header">
      <div class="drawer-title">
        <span>AI分析任务</span>
        <small>后台 Agent 分析队列</small>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">
          {{ activeSection === 'tasks' ? '创建任务' : '创建周期配置' }}
        </el-button>
        <el-button
          v-if="activeSection === 'tasks'"
          :icon="VideoPlay"
          :loading="runningOnce"
          @click="runQueueOnce"
        >
          执行一次队列
        </el-button>
        <el-button :icon="Refresh" :loading="refreshing" @click="refreshAll(false)">刷新</el-button>
        <el-button link :icon="Close" aria-label="关闭AI分析任务抽屉" @click="closeDrawer" />
      </div>
    </header>

    <main class="drawer-content">
      <el-tabs v-model="activeSection" class="drawer-tabs">
        <el-tab-pane label="执行队列" name="tasks" />
        <el-tab-pane label="周期配置" name="schedules" />
      </el-tabs>

      <section v-if="activeSection === 'tasks'" class="queue-section">
        <div class="queue-cards">
          <div v-for="item in queueCards" :key="item.label" class="queue-card">
            <span class="queue-label">{{ item.label }}</span>
            <strong :class="item.tone">{{ item.value }}</strong>
          </div>
        </div>
        <div class="queue-context">
          <span> <b>当前任务：</b>{{ queueStatus.runningTask?.name || '暂无任务' }} </span>
          <span> <b>下一个任务：</b>{{ queueStatus.nextTask?.name || '暂无任务' }} </span>
          <span><b>检查时间：</b>{{ formatTime(queueStatus.checkedAt) }}</span>
        </div>
      </section>

      <section v-if="activeSection === 'tasks'" class="task-section">
        <el-form :model="filters" inline class="filter-form" @submit.prevent="handleSearch">
          <el-form-item label="任务名称">
            <el-input
              v-model="filters.name"
              clearable
              placeholder="通过任务名称搜索"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="任务状态">
            <el-select
              v-model="filters.status"
              clearable
              placeholder="全部状态"
              class="filter-select"
            >
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="审批模式">
            <el-select
              v-model="filters.approvalMode"
              clearable
              placeholder="全部模式"
              class="filter-select"
            >
              <el-option label="自动批准 ASK" value="AUTO" />
              <el-option label="人工审批" value="MANUAL" />
            </el-select>
          </el-form-item>
          <el-form-item label="模型">
            <el-select
              v-model="filters.model"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="全部模型"
              class="model-filter"
            >
              <el-option
                v-for="item in modelOptions"
                :key="item.model"
                :label="item.model"
                :value="item.model"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="filters.scheduleId" label="来源周期">
            <el-tag closable @close="clearScheduleFilter">#{{ filters.scheduleId }}</el-tag>
          </el-form-item>
          <el-form-item class="filter-actions">
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>

        <div class="table-wrap">
          <el-table
            v-loading="tableLoading"
            :data="tasks"
            row-key="id"
            border
            height="100%"
            empty-text="暂无AI分析任务"
          >
            <el-table-column prop="id" label="ID" width="66" fixed="left" />
            <el-table-column
              prop="name"
              label="任务名称"
              min-width="160"
              fixed="left"
              show-overflow-tooltip
            />
            <el-table-column label="状态" width="120" class-name="status-column">
              <template #default="{ row }">
                <el-tag
                  :type="statusType(row.status)"
                  size="small"
                  effect="light"
                  class="status-tag"
                >
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="model" label="模型" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.model || 'auto' }}</template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="80" align="center" />
            <el-table-column label="审批模式" width="118">
              <template #default="{ row }">
                {{ row.approvalMode === 'AUTO' ? '自动批准 ASK' : '人工审批' }}
              </template>
            </el-table-column>
            <el-table-column label="来源周期" width="150">
              <template #default="{ row }">
                <template v-if="row.scheduleId">
                  <el-link type="primary" @click="showSchedule(row.scheduleId)">
                    #{{ row.scheduleId }}
                  </el-link>
                  <div class="source-fire-time">{{ formatTime(row.scheduleFireTime) }}</div>
                </template>
                <span v-else>手动创建</span>
              </template>
            </el-table-column>
            <el-table-column label="Skill" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{
                row.skillIds.length ? row.skillIds.join('、') : '-'
              }}</template>
            </el-table-column>
            <el-table-column label="待审批" width="80" align="center">
              <template #default="{ row }">
                <el-badge v-if="row.pendingApprovalCount" :value="row.pendingApprovalCount" />
                <span v-else>0</span>
              </template>
            </el-table-column>
            <el-table-column prop="prompt" label="提示词" min-width="190">
              <template #default="{ row }">
                <el-popover
                  v-if="row.prompt"
                  trigger="hover"
                  placement="top"
                  :width="720"
                  :show-after="250"
                  :hide-after="100"
                  popper-class="analysis-result-markdown-popover"
                >
                  <template #reference>
                    <div class="markdown-cell-preview">{{ row.prompt }}</div>
                  </template>
                  <div class="result-markdown-preview" v-html="parseMarkdown(row.prompt)"></div>
                </el-popover>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="result" label="返回结果" min-width="190">
              <template #default="{ row }">
                <el-popover
                  v-if="row.result"
                  trigger="hover"
                  placement="top"
                  :width="720"
                  :show-after="250"
                  :hide-after="100"
                  popper-class="analysis-result-markdown-popover"
                >
                  <template #reference>
                    <div class="markdown-cell-preview">{{ row.result }}</div>
                  </template>
                  <div class="result-markdown-preview" v-html="parseMarkdown(row.result)"></div>
                </el-popover>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="runCount" label="执行次数" width="88" align="center" />
            <el-table-column label="计划时间" width="170">
              <template #default="{ row }">
                {{ formatTime(row.scheduledTime, '立即执行') }}
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="248">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-tooltip content="查看详情" placement="top">
                    <el-button
                      class="view-action-btn"
                      link
                      type="primary"
                      :icon="View"
                      @click="openDetail(row)"
                    />
                  </el-tooltip>
                  <el-tooltip
                    v-if="row.pendingApprovalCount > 0"
                    content="处理MCP审批"
                    placement="top"
                  >
                    <el-button link type="warning" :icon="Lock" @click="openApproval(row)" />
                  </el-tooltip>
                  <el-tooltip content="编辑" placement="top">
                    <el-button
                      link
                      :icon="Edit"
                      :disabled="isActive(row.status)"
                      @click="openEdit(row)"
                    />
                  </el-tooltip>
                  <el-tooltip content="重新入队" placement="top">
                    <el-button
                      link
                      type="success"
                      :icon="RefreshRight"
                      :disabled="isActive(row.status) || row.status === 'PENDING'"
                      :loading="isRowLoading(row, 'enqueue')"
                      @click="enqueueTask(row)"
                    />
                  </el-tooltip>
                  <el-tooltip v-if="!isTerminal(row.status)" content="取消任务" placement="top">
                    <el-button
                      link
                      type="warning"
                      :icon="CircleClose"
                      :disabled="row.status === 'CANCELING'"
                      :loading="isRowLoading(row, 'cancel')"
                      @click="cancelTask(row)"
                    />
                  </el-tooltip>
                  <el-tooltip content="删除" placement="top">
                    <el-button
                      link
                      type="danger"
                      :icon="Delete"
                      :disabled="isActive(row.status)"
                      :loading="isRowLoading(row, 'delete')"
                      @click="deleteTask(row)"
                    />
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pagination-row">
          <el-pagination
            v-model:current-page="filters.page"
            v-model:page-size="filters.perPage"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadTasks(false)"
            @size-change="handlePageSizeChange"
          />
        </div>
      </section>

      <section v-else class="schedule-panel">
        <AnalysisTaskScheduleList
          ref="scheduleListRef"
          @edit="openScheduleEdit"
          @view-tasks="viewScheduleTasks"
        />
      </section>
    </main>

    <AnalysisTaskFormDialog
      v-model:visible="formVisible"
      :task="editingTask"
      :schedule="editingSchedule"
      :initial-schedule-type="initialScheduleType"
      :model-options="modelOptions"
      :skill-options="skillOptions"
      @saved="handleFormSaved"
    />
    <AnalysisTaskDetailDialog v-model:visible="detailVisible" :task-id="detailTaskId" />
    <AnalysisTaskApprovalDialog
      v-model:visible="approvalVisible"
      :task="approvalTask"
      @changed="refreshAll(true)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  CircleClose,
  Close,
  Delete,
  Edit,
  Lock,
  Plus,
  Refresh,
  RefreshLeft,
  RefreshRight,
  Search,
  VideoPlay,
  View,
} from '@element-plus/icons-vue';
import { AnalysisTaskService } from '@/service/api';
import { createMarkdownRenderer } from '@/components/dih-message/message-parts/message-part-context';
import type {
  TAnalysisTask,
  TAnalysisTaskModelOption,
  TAnalysisTaskQueue,
  TAnalysisTaskSchedule,
  TAnalysisTaskScheduleType,
  TAnalysisTaskSearch,
  TAnalysisTaskSkillOption,
  TAnalysisTaskStatus,
} from '@/types/type-analysis-task';
import AnalysisTaskApprovalDialog from './analysis-task-approval-dialog.vue';
import AnalysisTaskDetailDialog from './analysis-task-detail-dialog.vue';
import AnalysisTaskFormDialog from './analysis-task-form-dialog.vue';
import AnalysisTaskScheduleList from './analysis-task-schedule-list.vue';

defineOptions({ name: 'ViewDrawer' });

type Props = {
  visible?: boolean;
  queueStatus: TAnalysisTaskQueue;
};

const props = withDefaults(defineProps<Props>(), { visible: true });
const emit = defineEmits<{
  (event: 'close'): void;
  (event: 'refreshQueueStatus'): void;
}>();

const tasks = ref<TAnalysisTask[]>([]);
const total = ref(0);
const tableLoading = ref(false);
const refreshing = ref(false);
const runningOnce = ref(false);
const rowAction = reactive({ id: 0, action: '' });
const filters = reactive<TAnalysisTaskSearch>({
  name: '',
  status: '',
  model: '',
  approvalMode: '',
  page: 1,
  perPage: 10,
});
const activeSection = ref<'tasks' | 'schedules'>('tasks');

const modelOptions = ref<TAnalysisTaskModelOption[]>([]);
const skillOptions = ref<TAnalysisTaskSkillOption[]>([]);
const referenceOptionsLoaded = ref(false);
const formVisible = ref(false);
const editingTask = ref<TAnalysisTask | null>(null);
const editingSchedule = ref<TAnalysisTaskSchedule | null>(null);
const initialScheduleType = ref<TAnalysisTaskScheduleType>('ONCE');
const scheduleListRef = ref<InstanceType<typeof AnalysisTaskScheduleList>>();
const detailVisible = ref(false);
const detailTaskId = ref<number | null>(null);
const approvalVisible = ref(false);
const approvalTask = ref<TAnalysisTask | null>(null);
let pollingTimer: ReturnType<typeof setInterval> | undefined;
let refreshInFlight = false;
const { parseMarkdown, clearMarkdownCache } = createMarkdownRenderer();

const statusOptions: Array<{ label: string; value: TAnalysisTaskStatus }> = [
  { label: '等待执行', value: 'PENDING' },
  { label: '执行中', value: 'RUNNING' },
  { label: '等待审批', value: 'WAITING_APPROVAL' },
  { label: '取消中', value: 'CANCELING' },
  { label: '执行成功', value: 'SUCCESS' },
  { label: '执行失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELED' },
];

const statusMeta: Record<
  TAnalysisTaskStatus,
  { label: string; type: 'primary' | 'success' | 'warning' | 'info' | 'danger' }
> = {
  PENDING: { label: '等待执行', type: 'primary' },
  RUNNING: { label: '执行中', type: 'warning' },
  WAITING_APPROVAL: { label: '等待审批', type: 'warning' },
  CANCELING: { label: '取消中', type: 'info' },
  SUCCESS: { label: '执行成功', type: 'success' },
  FAILED: { label: '执行失败', type: 'danger' },
  CANCELED: { label: '已取消', type: 'info' },
};

const queueCards = computed(() => [
  { label: '等待任务', value: props.queueStatus.pendingCount, tone: 'primary' },
  { label: '到期可执行', value: props.queueStatus.readyCount, tone: 'primary' },
  { label: '执行中', value: props.queueStatus.runningCount, tone: 'warning' },
  { label: '等待审批', value: props.queueStatus.waitingApprovalCount, tone: 'danger' },
  { label: '可用执行槽', value: props.queueStatus.availableSlots, tone: 'success' },
  { label: '最大挂起数', value: props.queueStatus.maxSuspended, tone: 'info' },
]);

const statusLabel = (status: TAnalysisTaskStatus) => statusMeta[status]?.label || status;
const statusType = (status: TAnalysisTaskStatus) => statusMeta[status]?.type || 'info';
const isActive = (status: TAnalysisTaskStatus) =>
  ['RUNNING', 'WAITING_APPROVAL', 'CANCELING'].includes(status);
const isTerminal = (status: TAnalysisTaskStatus) =>
  ['SUCCESS', 'FAILED', 'CANCELED'].includes(status);
const isRowLoading = (task: TAnalysisTask, action: string) =>
  rowAction.id === task.id && rowAction.action === action;

const formatTime = (value: string, emptyText = '-') => {
  if (!value) return emptyText;
  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : value;
};

const loadTasks = async (silent = false) => {
  if (!silent) tableLoading.value = true;
  try {
    const response = await AnalysisTaskService.getList(filters, silent);
    tasks.value = response.rows;
    total.value = response.total;
    if (filters.page > 1 && !response.rows.length && response.total > 0) {
      filters.page = Math.ceil(response.total / filters.perPage);
      await loadTasks(silent);
    }
  } catch (error) {
    if (!silent) console.error('获取AI分析任务列表失败:', error);
  } finally {
    if (!silent) tableLoading.value = false;
  }
};

const refreshAll = async (silent = false, refreshQueueStatus = true) => {
  if (refreshInFlight) return;
  refreshInFlight = true;
  if (!silent) refreshing.value = true;
  if (refreshQueueStatus) emit('refreshQueueStatus');
  try {
    await Promise.all([loadTasks(silent), scheduleListRef.value?.refresh()]);
  } finally {
    refreshInFlight = false;
    if (!silent) refreshing.value = false;
  }
};

const loadReferenceOptions = async () => {
  if (referenceOptionsLoaded.value) return;
  const [models, skills] = await Promise.allSettled([
    AnalysisTaskService.getModelOptions(),
    AnalysisTaskService.getSkillOptions(),
  ]);
  if (models.status === 'fulfilled') modelOptions.value = models.value;
  if (skills.status === 'fulfilled') skillOptions.value = skills.value;
  referenceOptionsLoaded.value = models.status === 'fulfilled' && skills.status === 'fulfilled';
};

const handleSearch = () => {
  filters.page = 1;
  loadTasks(false);
};

const resetFilters = () => {
  Object.assign(filters, {
    name: '',
    status: '',
    model: '',
    approvalMode: '',
    scheduleId: undefined,
    page: 1,
    perPage: filters.perPage,
  });
  loadTasks(false);
};

const handlePageSizeChange = () => {
  filters.page = 1;
  loadTasks(false);
};

const openCreate = () => {
  editingTask.value = null;
  editingSchedule.value = null;
  initialScheduleType.value = activeSection.value === 'schedules' ? 'CRON' : 'ONCE';
  loadReferenceOptions();
  formVisible.value = true;
};

const openEdit = async (task: TAnalysisTask) => {
  if (isActive(task.status)) return;
  try {
    editingTask.value = await AnalysisTaskService.getView(task.id);
    editingSchedule.value = null;
    initialScheduleType.value = 'ONCE';
    await loadReferenceOptions();
    formVisible.value = true;
  } catch (error) {
    console.error('获取AI分析任务详情失败:', error);
  }
};

const openScheduleEdit = async (schedule: TAnalysisTaskSchedule) => {
  editingTask.value = null;
  editingSchedule.value = schedule;
  initialScheduleType.value = 'CRON';
  await loadReferenceOptions();
  formVisible.value = true;
};

const viewScheduleTasks = (schedule: TAnalysisTaskSchedule) => {
  activeSection.value = 'tasks';
  filters.scheduleId = schedule.id;
  filters.page = 1;
  loadTasks(false);
};

const clearScheduleFilter = () => {
  filters.scheduleId = undefined;
  filters.page = 1;
  loadTasks(false);
};

const handleFormSaved = (kind: 'task' | 'schedule') => {
  if (kind === 'schedule') activeSection.value = 'schedules';
  refreshAll(false);
};

const showSchedule = (scheduleId: number) => {
  activeSection.value = 'schedules';
  ElMessage.info(`已切换到周期配置，来源周期 ID：${scheduleId}`);
};

const openDetail = (task: TAnalysisTask) => {
  detailTaskId.value = task.id;
  detailVisible.value = true;
};

const openApproval = (task: TAnalysisTask) => {
  approvalTask.value = task;
  approvalVisible.value = true;
};

const runQueueOnce = async () => {
  try {
    await ElMessageBox.confirm('确认立即取出一个到期任务执行？', '执行一次队列', {
      type: 'warning',
      confirmButtonText: '执行',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  runningOnce.value = true;
  try {
    const task = await AnalysisTaskService.runOnce();
    ElMessage.success(task ? `任务「${task.name}」已提交执行` : '当前暂无可执行任务');
    await refreshAll(true);
  } catch (error) {
    console.error('手动执行AI分析任务队列失败:', error);
  } finally {
    runningOnce.value = false;
  }
};

const executeRowAction = async (
  task: TAnalysisTask,
  action: string,
  request: () => Promise<unknown>,
  successMessage: string,
) => {
  rowAction.id = task.id;
  rowAction.action = action;
  try {
    await request();
    ElMessage.success(successMessage);
    await refreshAll(true);
  } catch (error) {
    console.error(`AI分析任务${action}操作失败:`, error);
  } finally {
    rowAction.id = 0;
    rowAction.action = '';
  }
};

const enqueueTask = async (task: TAnalysisTask) => {
  try {
    await ElMessageBox.confirm(`确认将任务「${task.name}」重新放入队列？`, '重新入队', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await executeRowAction(
    task,
    'enqueue',
    () => AnalysisTaskService.enqueue(task.id),
    '任务已重新入队',
  );
};

const cancelTask = async (task: TAnalysisTask) => {
  try {
    await ElMessageBox.confirm(`确认取消任务「${task.name}」？`, '取消任务', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await executeRowAction(
    task,
    'cancel',
    () => AnalysisTaskService.cancel(task.id),
    '任务取消请求已提交',
  );
};

const deleteTask = async (task: TAnalysisTask) => {
  try {
    await ElMessageBox.confirm(`确认删除AI分析任务「${task.name}」？`, '删除任务', {
      type: 'error',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  await executeRowAction(
    task,
    'delete',
    () => AnalysisTaskService.remove(task.id),
    'AI分析任务已删除',
  );
};

const stopPolling = () => {
  if (pollingTimer) clearInterval(pollingTimer);
  pollingTimer = undefined;
};

const startPolling = () => {
  stopPolling();
  pollingTimer = setInterval(() => refreshAll(true, false), 5000);
};

const closeDrawer = () => emit('close');

watch(
  () => props.visible,
  visible => {
    if (!visible) {
      stopPolling();
      approvalVisible.value = false;
      return;
    }
    refreshAll(false);
    loadReferenceOptions();
    startPolling();
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  stopPolling();
  clearMarkdownCache();
});
</script>

<style scoped lang="scss">
.drawer-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  overflow: hidden;
  color: var(--el-text-color-primary);
  background: #f5f7fa;
}

.drawer-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
}

.drawer-title {
  display: flex;
  align-items: baseline;
  gap: 10px;

  span {
    font-size: 18px;
    font-weight: 600;
  }

  small {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    font-weight: 400;
  }
}

.header-actions {
  display: flex;
  align-items: center;
}

.drawer-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 14px 18px 18px;
  gap: 12px;
}

.drawer-tabs {
  flex-shrink: 0;
  padding: 0 14px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;

  :deep(.el-tabs__header) {
    margin: 0;
  }

  :deep(.el-tabs__content) {
    display: none;
  }
}

.queue-section,
.task-section,
.schedule-panel {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}

.schedule-panel {
  flex: 1;
  min-height: 0;
  padding: 14px;
}

.queue-section {
  flex-shrink: 0;
  padding: 12px 16px;
}

.queue-cards {
  display: grid;
  grid-template-columns: repeat(6, minmax(100px, 1fr));
  gap: 12px;
}

.queue-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  padding: 0 14px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;

  strong {
    font-size: 22px;

    &.primary {
      color: var(--el-color-primary);
    }

    &.warning {
      color: var(--el-color-warning);
    }

    &.danger {
      color: var(--el-color-danger);
    }

    &.success {
      color: var(--el-color-success);
    }

    &.info {
      color: var(--el-color-info);
    }
  }
}

.queue-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.queue-context {
  display: flex;
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  gap: 30px;

  span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  b {
    color: var(--el-text-color-regular);
  }
}

.task-section {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 14px;
}

.filter-form {
  flex-shrink: 0;

  :deep(.el-form-item) {
    margin-right: 14px;
    margin-bottom: 12px;
  }
}

.filter-select {
  width: 142px;
}

.model-filter {
  width: 170px;
}

.filter-actions {
  float: right;
  margin-right: 0 !important;
}

.table-wrap {
  flex: 1;
  min-height: 240px;
}

:deep(.status-column .cell) {
  overflow: visible;
  text-overflow: clip;
  white-space: nowrap;
}

.status-tag {
  max-width: none;

  :deep(.el-tag__content) {
    overflow: visible;
    text-overflow: clip;
    white-space: nowrap;
  }
}

.markdown-cell-preview {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: default;
}

.source-fire-time {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  white-space: nowrap;
}

:global(.analysis-result-markdown-popover.el-popper) {
  max-width: calc(100vw - 32px);
  padding: 0;
}

:global(.analysis-result-markdown-popover .result-markdown-preview) {
  box-sizing: border-box;
  max-height: 420px;
  padding: 16px 18px;
  overflow: auto;
  color: var(--el-text-color-primary);
  line-height: 1.7;
  overflow-wrap: anywhere;
}

:global(.analysis-result-markdown-popover .result-markdown-preview > :first-child) {
  margin-top: 0;
}

:global(.analysis-result-markdown-popover .result-markdown-preview > :last-child) {
  margin-bottom: 0;
}

:global(.analysis-result-markdown-popover .result-markdown-preview h1),
:global(.analysis-result-markdown-popover .result-markdown-preview h2),
:global(.analysis-result-markdown-popover .result-markdown-preview h3),
:global(.analysis-result-markdown-popover .result-markdown-preview h4) {
  margin: 16px 0 8px;
  line-height: 1.35;
}

:global(.analysis-result-markdown-popover .result-markdown-preview p),
:global(.analysis-result-markdown-popover .result-markdown-preview ul),
:global(.analysis-result-markdown-popover .result-markdown-preview ol) {
  margin: 8px 0;
}

:global(.analysis-result-markdown-popover .result-markdown-preview pre) {
  margin: 10px 0;
  padding: 12px 14px;
  overflow: auto;
  color: #d8dee9;
  font-size: 12px;
  line-height: 1.6;
  background: #202630;
  border-radius: 6px;
}

:global(.analysis-result-markdown-popover .result-markdown-preview code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

:global(.analysis-result-markdown-popover .result-markdown-preview :not(pre) > code) {
  padding: 2px 5px;
  color: var(--el-color-danger);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

:global(.analysis-result-markdown-popover .result-markdown-preview table) {
  width: 100%;
  margin: 10px 0;
  border-collapse: collapse;
}

:global(.analysis-result-markdown-popover .result-markdown-preview th),
:global(.analysis-result-markdown-popover .result-markdown-preview td) {
  padding: 7px 9px;
  text-align: left;
  border: 1px solid var(--el-border-color);
}

:global(.analysis-result-markdown-popover .result-markdown-preview blockquote) {
  margin: 10px 0;
  padding: 4px 12px;
  color: var(--el-text-color-secondary);
  border-left: 3px solid var(--el-color-primary-light-5);
}

:global(.analysis-result-markdown-popover .result-markdown-preview img) {
  max-width: 100%;
}

.row-actions {
  display: flex;
  align-items: center;
  min-height: 32px;

  :deep(.el-button + .el-button) {
    margin-left: 8px;
  }
}

.view-action-btn,
.view-action-btn:hover,
.view-action-btn:focus,
.view-action-btn:active {
  padding: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
}

.pagination-row {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  padding-top: 12px;
}

@media (max-width: 1200px) {
  .queue-cards {
    grid-template-columns: repeat(3, 1fr);
  }

  .queue-context {
    flex-wrap: wrap;
    gap: 8px 20px;
  }
}
</style>
