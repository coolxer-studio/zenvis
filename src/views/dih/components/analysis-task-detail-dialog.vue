<template>
  <el-dialog
    :model-value="visible"
    title="AI分析任务详情"
    width="92vw"
    top="3vh"
    destroy-on-close
    append-to-body
    class="analysis-task-detail-dialog"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="detail-body">
      <template v-if="task">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="任务ID">{{ task.id }}</el-descriptions-item>
          <el-descriptions-item label="任务名称">{{ task.name }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(task.status)" effect="light">
              {{ task.statusDescription || statusLabel(task.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="模型">{{ task.model || 'auto' }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ task.priority }}</el-descriptions-item>
          <el-descriptions-item label="审批模式">
            {{ task.approvalMode === 'AUTO' ? '自动批准 ASK' : '人工审批' }}
          </el-descriptions-item>
          <el-descriptions-item label="Skill">
            {{ task.skillIds.length ? task.skillIds.join('、') : '未选择' }}
          </el-descriptions-item>
          <el-descriptions-item label="待审批">{{
            task.pendingApprovalCount
          }}</el-descriptions-item>
          <el-descriptions-item label="Execution ID" :span="2">
            <span class="mono-text">{{ task.executionId || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="来源周期">
            {{ task.scheduleId ? `#${task.scheduleId}` : '手工创建' }}
          </el-descriptions-item>
          <el-descriptions-item label="周期触发时间">
            {{ formatTime(task.scheduleFireTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="执行次数">{{ task.runCount }}</el-descriptions-item>
          <el-descriptions-item label="计划时间">
            {{ formatTime(task.scheduledTime, '立即执行') }}
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">
            {{ formatTime(task.startTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="结束时间">
            {{ formatTime(task.finishTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatTime(task.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ formatTime(task.updateTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="4">
            <div class="detail-long-text">{{ task.description || '暂无描述' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="提示词" :span="4">
            <div class="detail-long-text prompt-text">{{ task.prompt || '-' }}</div>
          </el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="返回结果" name="result">
            <div v-if="resultMessage" class="analysis-result">
              <MessageCardRenderer
                :message="resultMessage"
                mode="readonly"
                @copy-code="copyResultContent"
              />
            </div>
            <el-empty v-else description="暂无结果" :image-size="72" />
          </el-tab-pane>
          <el-tab-pane label="MCP调用审计" name="audit">
            <el-table v-loading="auditLoading" :data="auditRows" border height="330">
              <el-table-column
                prop="requestId"
                label="请求 ID"
                min-width="210"
                show-overflow-tooltip
              />
              <el-table-column prop="toolKey" label="工具" min-width="180" show-overflow-tooltip />
              <el-table-column prop="policy" label="策略" width="90" />
              <el-table-column prop="approvalScope" label="审批范围" width="110">
                <template #default="{ row }">{{ row.approvalScope || '无需审批' }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="110" />
              <el-table-column label="参数" min-width="220">
                <template #default="{ row }">
                  <el-popover placement="left" :width="520" trigger="click">
                    <template #reference>
                      <el-button link type="primary">查看参数</el-button>
                    </template>
                    <pre class="json-content">{{ formatJson(row.arguments) }}</pre>
                  </el-popover>
                </template>
              </el-table-column>
              <el-table-column label="结果" min-width="220">
                <template #default="{ row }">
                  <el-popover placement="left" :width="520" trigger="click">
                    <template #reference>
                      <el-button link type="primary" :disabled="!row.result">查看结果</el-button>
                    </template>
                    <pre class="json-content">{{ formatJson(row.result) }}</pre>
                  </el-popover>
                </template>
              </el-table-column>
              <el-table-column
                prop="errorSummary"
                label="错误"
                min-width="180"
                show-overflow-tooltip
              />
              <el-table-column label="创建时间" width="170">
                <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
              </el-table-column>
            </el-table>
            <div class="pagination-row">
              <el-pagination
                v-model:current-page="auditPage"
                v-model:page-size="auditPageSize"
                :total="auditTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="loadAudit"
                @size-change="handleAuditSizeChange"
              />
            </div>
          </el-tab-pane>
          <el-tab-pane label="错误信息" name="error">
            <pre v-if="task.errorMessage" class="code-content error-content">{{
              task.errorMessage
            }}</pre>
            <el-empty v-else description="暂无错误" :image-size="72" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';
import MessageCardRenderer from '@/components/dih-message/message-card-renderer.vue';
import { AnalysisTaskService } from '@/service/api';
import type { ChatMessage } from '@/types/type-dih';
import type {
  TAnalysisTask,
  TAnalysisTaskStatus,
  TMcpInvocation,
} from '@/types/type-analysis-task';
import { copyTextToClipboard } from '@/utils/clipboard';

defineOptions({ name: 'AnalysisTaskDetailDialog' });

type Props = {
  visible: boolean;
  taskId: number | null;
};

const props = defineProps<Props>();
const emit = defineEmits<{ (event: 'update:visible', value: boolean): void }>();

const task = ref<TAnalysisTask | null>(null);
const loading = ref(false);
const activeTab = ref('result');
const auditRows = ref<TMcpInvocation[]>([]);
const auditTotal = ref(0);
const auditPage = ref(1);
const auditPageSize = ref(20);
const auditLoading = ref(false);

const resultMessage = computed<ChatMessage | null>(() => {
  if (!task.value?.result) return null;
  return {
    id: `analysis-task-${task.value.id}-${task.value.executionId || 'result'}`,
    sender: 'ai',
    content: task.value.result,
    time: task.value.finishTime || task.value.updateTime,
    type: 'text',
    parts: task.value.resultParts.length ? task.value.resultParts : undefined,
  };
});

const statusMap: Record<
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

const statusLabel = (status: TAnalysisTaskStatus) => statusMap[status]?.label || status;
const statusType = (status: TAnalysisTaskStatus) => statusMap[status]?.type || 'info';

const formatTime = (value: string, emptyText = '-') => {
  if (!value) return emptyText;
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

const copyResultContent = async (content: string) => {
  const copied = await copyTextToClipboard(content);
  if (copied) {
    ElMessage.success('已复制');
  } else {
    ElMessage.error('复制失败');
  }
};

const loadTask = async () => {
  if (!props.taskId) return;
  loading.value = true;
  try {
    task.value = await AnalysisTaskService.getView(props.taskId);
  } catch (error) {
    console.error('获取AI分析任务详情失败:', error);
  } finally {
    loading.value = false;
  }
};

const loadAudit = async () => {
  if (!task.value || auditLoading.value) return;
  auditLoading.value = true;
  try {
    const response = await AnalysisTaskService.getInvocations(
      task.value.id,
      task.value.executionId,
      auditPage.value,
      auditPageSize.value,
    );
    auditRows.value = response.rows;
    auditTotal.value = response.total;
  } catch (error) {
    console.error('获取MCP调用审计失败:', error);
  } finally {
    auditLoading.value = false;
  }
};

const handleAuditSizeChange = () => {
  auditPage.value = 1;
  loadAudit();
};

watch(
  () => props.visible,
  visible => {
    if (!visible) return;
    activeTab.value = 'result';
    auditPage.value = 1;
    auditRows.value = [];
    auditTotal.value = 0;
    loadTask();
  },
);

watch(activeTab, tab => {
  if (tab === 'audit' && props.visible) loadAudit();
});
</script>

<style scoped lang="scss">
.detail-body {
  min-height: 460px;
}

.detail-tabs {
  margin-top: 18px;
}

.mono-text,
.code-content,
.json-content {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.mono-text {
  word-break: break-all;
}

.detail-long-text {
  max-height: 120px;
  overflow: auto;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.prompt-text {
  color: var(--el-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.code-content,
.json-content {
  box-sizing: border-box;
  max-height: 430px;
  margin: 0;
  padding: 16px;
  overflow: auto;
  color: #d8dee9;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: #202630;
  border-radius: 6px;
}

.error-content {
  color: #ffd2d2;
  background: #3a2427;
}

.analysis-result {
  max-height: 430px;
  padding: 4px 2px;
  overflow: auto;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
