<template>
  <div class="panel right-panel">
    <div class="tab-container">
      <el-tabs v-model="activeTab" class="right-tabs">
        <el-tab-pane
          v-for="section in configSections"
          :key="section.name"
          :label="section.label"
          :name="section.name"
        >
          <div class="config-table-container">
            <el-empty v-if="!section.items.length" class="empty-state" description="暂无记录" />
            <el-table v-else :data="section.items" stripe style="width: 100%">
              <el-table-column prop="id" label="ID" min-width="130" show-overflow-tooltip />
              <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
              <el-table-column label="状态" min-width="90">
                <template #default="scope">
                  <el-tag :type="statusTagType(scope.row.status)" effect="plain">
                    {{ statusLabel(scope.row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <template v-if="section.name === 'metadataConfigs'">
                <el-table-column prop="fileName" label="文件" min-width="150" show-overflow-tooltip />
                <el-table-column prop="tableName" label="目标表" min-width="150" show-overflow-tooltip />
                <el-table-column prop="fieldCount" label="字段" width="76" />
              </template>
              <template v-else>
                <el-table-column prop="taskId" label="任务ID" min-width="130" show-overflow-tooltip />
                <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
              </template>
              <el-table-column label="JSON" width="86" fixed="right">
                <template #default="scope">
                  <el-button size="small" text type="primary" @click="openJsonDialog(scope.row)">
                    查看
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="jsonDialogVisible" title="记录 JSON" width="620px">
      <pre class="json-preview">{{ selectedRecordJson }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

type ConsoleRecord = Record<string, unknown> & {
  id?: string
  name?: string
  status?: string
  fileName?: string
  tableName?: string
  fieldCount?: number
  taskId?: string
  description?: string
}

type DataAccessRecordEventDetail = {
  metadataConfigs?: unknown[]
  dataPushServices?: unknown[]
}

const DATA_ACCESS_RECORD_EVENT = 'dihDataAccessRecordsUpdated'

const activeTab = ref('metadataConfigs')
const metadataConfigs = ref<ConsoleRecord[]>([])
const dataPushServices = ref<ConsoleRecord[]>([])
const jsonDialogVisible = ref(false)
const selectedRecordJson = ref('')

const asRecordList = (value: unknown): ConsoleRecord[] => {
  return Array.isArray(value)
    ? value.filter(item => item && typeof item === 'object').map(item => item as ConsoleRecord)
    : []
}

const configSections = computed(() => [
  { name: 'metadataConfigs', label: '元数据配置操作台', items: metadataConfigs.value },
  { name: 'dataPushServices', label: '数据推送服务', items: dataPushServices.value },
])

const statusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    confirmed: '已确认',
    applied: '已应用',
    created: '已创建',
    running: '运行中',
    stopped: '已停止',
    error: '异常',
  }
  return status ? labels[status] || status : '未记录'
}

const statusTagType = (status?: string) => {
  if (status === 'running' || status === 'applied' || status === 'confirmed') {
    return 'success'
  }
  if (status === 'error') {
    return 'danger'
  }
  if (status === 'stopped') {
    return 'warning'
  }
  return 'info'
}

const openJsonDialog = (record: ConsoleRecord) => {
  selectedRecordJson.value = JSON.stringify(record.config || record.raw || record, null, 2)
  jsonDialogVisible.value = true
}

const handleRecordsUpdated = (event: Event) => {
  const detail = (event as CustomEvent<DataAccessRecordEventDetail>).detail || {}
  metadataConfigs.value = asRecordList(detail.metadataConfigs)
  dataPushServices.value = asRecordList(detail.dataPushServices)
  if (!metadataConfigs.value.length && dataPushServices.value.length) {
    activeTab.value = 'dataPushServices'
  }
}

onMounted(() => {
  window.addEventListener(DATA_ACCESS_RECORD_EVENT, handleRecordsUpdated)
})

onUnmounted(() => {
  window.removeEventListener(DATA_ACCESS_RECORD_EVENT, handleRecordsUpdated)
})
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

.tab-container {
  flex: 1;
  overflow: hidden;
}

.right-tabs {
  height: 100%;
}

:deep(.el-tabs__content) {
  padding: 0;
  height: calc(100% - 40px);
  overflow-y: auto;
}

:deep(.el-tabs__nav) {
  background-color: #fff;
  padding: 0 18px;
  width: 100%;
}

:deep(.el-tabs__item) {
  font-size: 14px;
  height: 40px;
  line-height: 40px;
}

:deep(.el-tabs__item.is-active) {
  font-weight: bold;
}

.config-table-container {
  padding: 12px;
}

.empty-state {
  height: 220px;
}

.json-preview {
  max-height: 520px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  background: #f6f8fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}
</style>
