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
            <el-table :data="section.items" stripe style="width: 100%">
              <el-table-column prop="id" label="ID" min-width="100" />
              <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
              <el-table-column label="跳转链接" min-width="180" show-overflow-tooltip>
                <template #default="scope">
                  <el-link type="primary" :href="scope.row.jumpLink" :underline="false">
                    {{ scope.row.jumpLink }}
                  </el-link>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface ConfigItem {
  id: string
  name: string
  jumpLink: string
}

interface ConfigSection {
  name: string
  label: string
  items: ConfigItem[]
}

const activeTab = ref('metadataConfigs')

const dataPushServices: ConfigItem[] = [
  {
    id: 'push-001',
    name: '主机行为数据推送服务',
    jumpLink: '/dih/data-access/push-service/host-behavior'
  },
  {
    id: 'push-002',
    name: '威胁情报数据推送服务',
    jumpLink: '/dih/data-access/push-service/threat-intel'
  },
  {
    id: 'push-003',
    name: '资产基线数据推送服务',
    jumpLink: '/dih/data-access/push-service/asset-baseline'
  }
]

const metadataConfigs: ConfigItem[] = [
  {
    id: 'meta-001',
    name: '主机行为元数据配置',
    jumpLink: '/dih/data-access/metadata/host-behavior'
  },
  {
    id: 'meta-002',
    name: '威胁情报元数据配置',
    jumpLink: '/dih/data-access/metadata/threat-intel'
  },
  {
    id: 'meta-003',
    name: '资产基线元数据配置',
    jumpLink: '/dih/data-access/metadata/asset-baseline'
  }
]

const configSections: ConfigSection[] = [
  { name: 'metadataConfigs', label: '元数据配置', items: metadataConfigs },
  { name: 'dataPushServices', label: '数据推送服务', items: dataPushServices }
]
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
  padding: 0 30px;
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
</style>
