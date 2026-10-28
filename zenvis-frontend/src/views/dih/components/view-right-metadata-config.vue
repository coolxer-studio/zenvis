<template>
  <div class="panel right-panel">
    <el-empty v-if="!records.length" class="empty-state" description="暂无元数据配置记录" />
    <el-table v-else :data="records" stripe table-layout="fixed" style="width: 100%">
      <el-table-column label="名称" show-overflow-tooltip>
        <template #default="scope">
          <el-button
            v-if="scope.row.fileName"
            class="record-link"
            text
            type="primary"
            @click="openMetaConfig(scope.row)"
          >
            {{ scope.row.name || scope.row.fileName }}
          </el-button>
          <span v-else>{{ scope.row.name || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="fileName" label="文件" show-overflow-tooltip />
      <el-table-column prop="tableName" label="目标表" show-overflow-tooltip />
      <el-table-column prop="fieldCount" label="字段" width="72" />
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
  DATA_METADATA_CONFIG_RECORD_EVENT,
  DATA_METADATA_CONFIG_RECORD_REQUEST_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events';
import type { MetadataConfigRecordEventDetail } from '../events';

type MetadataConfigRecord = Record<string, unknown> & {
  name?: string;
  status?: string;
  fileName?: string;
  tableName?: string;
  fieldCount?: number;
};

const records = ref<MetadataConfigRecord[]>([]);
const router = useRouter();

const asRecordList = (value: unknown): MetadataConfigRecord[] => Array.isArray(value)
  ? value.filter(item => item && typeof item === 'object').map(item => item as MetadataConfigRecord)
  : [];

const statusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    confirmed: '已确认',
    applied: '已应用',
    error: '异常',
  };
  return status ? labels[status] || status : '未记录';
};

const statusTagType = (status?: string) => {
  if (status === 'applied' || status === 'confirmed') return 'success';
  if (status === 'error') return 'danger';
  return 'info';
};

const openMetaConfig = (record: MetadataConfigRecord) => {
  if (!record.fileName) return;
  const route = router.resolve({
    name: 'policy-config',
    params: { menuParams: 'meta' },
    query: { fileName: record.fileName },
  });
  window.open(route.href, '_blank', 'noopener,noreferrer');
};

const handleRecordsUpdated = (detail: MetadataConfigRecordEventDetail) => {
  records.value = asRecordList(detail?.records);
};

useDihEventListener(DATA_METADATA_CONFIG_RECORD_EVENT, handleRecordsUpdated);
onMounted(() => emitDihEvent(DATA_METADATA_CONFIG_RECORD_REQUEST_EVENT));
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
