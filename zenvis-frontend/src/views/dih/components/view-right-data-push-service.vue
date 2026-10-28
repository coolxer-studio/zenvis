<template>
  <div class="panel right-panel">
    <el-empty v-if="!records.length" class="empty-state" description="暂无数据推送服务记录" />
    <el-table v-else :data="records" stripe table-layout="fixed" style="width: 100%">
      <el-table-column label="名称" show-overflow-tooltip>
        <template #default="scope">
          <el-button
            v-if="scope.row.name"
            class="record-link"
            text
            type="primary"
            @click="openDataPushService(scope.row)"
          >
            {{ scope.row.name }}
          </el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="taskId" label="任务ID" width="90" show-overflow-tooltip />
      <el-table-column prop="sourceMark" label="sourceMark" show-overflow-tooltip />
      <el-table-column label="状态" width="92">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)" effect="plain">
            {{ statusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  DATA_PUSH_SERVICE_RECORD_EVENT,
  DATA_PUSH_SERVICE_RECORD_REQUEST_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events';
import type { DataPushServiceRecordEventDetail } from '../events';

type DataPushServiceRecord = Record<string, unknown> & {
  id?: string;
  name?: string;
  status?: string;
  taskId?: string;
  sourceMark?: string;
};

const records = ref<DataPushServiceRecord[]>([]);
const router = useRouter();

const asRecordList = (value: unknown): DataPushServiceRecord[] => Array.isArray(value)
  ? value.filter(item => item && typeof item === 'object').map(item => item as DataPushServiceRecord)
  : [];

const statusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    created: '已创建',
    running: '运行中',
    stopped: '已停止',
    error: '异常',
  };
  return status ? labels[status] || status : '未记录';
};

const statusTagType = (status?: string) => {
  if (status === 'running' || status === 'created') return 'success';
  if (status === 'error') return 'danger';
  if (status === 'stopped') return 'warning';
  return 'info';
};

const openDataPushService = (record: DataPushServiceRecord) => {
  const route = router.resolve({
    name: 'low-code-page',
    params: { menuParams: 'push-task' },
    query: {
      taskId: record.taskId || record.id || '',
      sourceMark: typeof record.sourceMark === 'string' ? record.sourceMark : '',
    },
  });
  window.open(route.href, '_blank', 'noopener,noreferrer');
};

const handleRecordsUpdated = (detail: DataPushServiceRecordEventDetail) => {
  records.value = asRecordList(detail?.records);
};

useDihEventListener(DATA_PUSH_SERVICE_RECORD_EVENT, handleRecordsUpdated);
onMounted(() => emitDihEvent(DATA_PUSH_SERVICE_RECORD_REQUEST_EVENT));
</script>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 10px;
  box-sizing: border-box;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.right-panel {
  background-color: #f5f7fa;
  color: #333;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.empty-state {
  height: 220px;
}

.record-link {
  max-width: 100%;
  height: auto;
  padding: 0;
  vertical-align: baseline;
}

.record-link :deep(span) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
