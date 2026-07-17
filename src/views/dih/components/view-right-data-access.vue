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
            <el-table v-else :data="section.items" stripe table-layout="fixed" style="width: 100%">
              <el-table-column v-if="section.name !== 'metadataConfigs'" prop="id" label="ID" show-overflow-tooltip />
              <el-table-column label="名称" show-overflow-tooltip>
                <template #default="scope">
                  <el-button
                    v-if="section.name === 'dataPushServices' && scope.row.name"
                    class="record-link"
                    text
                    type="primary"
                    @click="openDataPushService(scope.row)"
                  >
                    {{ scope.row.name }}
                  </el-button>
                  <span v-else>{{ scope.row.name || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态">
                <template #default="scope">
                  <el-tag :type="statusTagType(scope.row.status)" effect="plain">
                    {{ statusLabel(scope.row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <template v-if="section.name === 'metadataConfigs'">
                <el-table-column label="文件" show-overflow-tooltip>
                  <template #default="scope">
                    <el-button
                      v-if="scope.row.fileName"
                      class="record-link"
                      text
                      type="primary"
                      @click="openMetaConfig(scope.row)"
                    >
                      {{ scope.row.fileName }}
                    </el-button>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="tableName" label="目标表" show-overflow-tooltip />
                <el-table-column prop="fieldCount" label="字段" show-overflow-tooltip />
              </template>
              <template v-else>
                <el-table-column prop="taskId" label="任务ID" show-overflow-tooltip />
                <el-table-column prop="description" label="描述" show-overflow-tooltip />
              </template>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { DATA_ACCESS_RECORD_EVENT, useDihEventListener } from '../events'
import type { DataAccessRecordEventDetail } from '../events'

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

const activeTab = ref('metadataConfigs')
const metadataConfigs = ref<ConsoleRecord[]>([])
const dataPushServices = ref<ConsoleRecord[]>([])
const router = useRouter()

const asRecordList = (value: unknown): ConsoleRecord[] => {
  return Array.isArray(value)
    ? value.filter(item => item && typeof item === 'object').map(item => item as ConsoleRecord)
    : []
}

const metadataConfigRows = computed<ConsoleRecord[]>(() => metadataConfigs.value.map(record => {
  const row = { ...record }
  delete row.id
  return row
}))

const configSections = computed(() => [
  { name: 'metadataConfigs', label: '元数据配置', items: metadataConfigRows.value },
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

const openRouteInNewTab = (routeLocation: Parameters<typeof router.resolve>[0]) => {
  const route = router.resolve(routeLocation)
  window.open(route.href, '_blank', 'noopener,noreferrer')
}

const openMetaConfig = (record: ConsoleRecord) => {
  if (!record.fileName) {
    return
  }
  openRouteInNewTab({
    name: 'policy-config',
    params: { menuParams: 'meta' },
    query: { fileName: record.fileName },
  })
}

const openDataPushService = (record: ConsoleRecord) => {
  openRouteInNewTab({
    name: 'low-code-page',
    params: { menuParams: 'push-task' },
    query: {
      taskId: record.taskId || record.id || '',
      sourceMark: typeof record.sourceMark === 'string' ? record.sourceMark : '',
    },
  })
}

const handleRecordsUpdated = (detail: DataAccessRecordEventDetail) => {
  detail ||= {}
  metadataConfigs.value = asRecordList(detail.metadataConfigs)
  dataPushServices.value = asRecordList(detail.dataPushServices)
  if (!metadataConfigs.value.length && dataPushServices.value.length) {
    activeTab.value = 'dataPushServices'
  }
}

useDihEventListener(DATA_ACCESS_RECORD_EVENT, handleRecordsUpdated)
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
