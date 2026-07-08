<template>
  <div class="panel right-panel">
    <!-- 选项卡 -->
    <div class="tab-container">
      <el-tabs v-model="activeTab" class="right-tabs">
        <el-tab-pane label="图表库" name="visualization">
          <!-- 动态图表区域 -->
          <div v-if="chartData.option" class="visualization-container">
            <div class="visualization-header">
              <h4>数据分析图表</h4>
            </div>
            <div class="visualization-content">
              <BaseChart
                ref="dynamicChartRef"
                :option="chartData.option"
                height="300px"
                :loading="chartLoading"
              />
            </div>
            <!-- 原始数据表格 -->
            <div v-if="chartData.rawData && chartData.rawData.length > 0" class="data-table-container">
              <div class="data-table-header">
                <h4>原始数据</h4>
              </div>
              <el-table :data="chartData.rawData" stripe table-layout="fixed" style="width: 100%" max-height="250">
                <el-table-column
                  v-for="col in chartData.columns"
                  :key="col"
                  :prop="col"
                  :label="col"
                  show-overflow-tooltip
                />
              </el-table>
            </div>
          </div>
          <!-- 默认占位图表 -->
          <div
            v-for="(chart, index) in visualizationCharts"
            :key="index"
            class="visualization-container"
          >
            <div class="visualization-header">
              <h4>{{ chart.title }}</h4>
            </div>
            <div class="visualization-content">
              <div class="placeholder-chart">
                <el-icon><component :is="chart.icon" /></el-icon>
                <p>{{ chart.description }}</p>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="可视化页面配置" name="visualPageConfigs">
          <div class="resource-container">
            <el-table :data="visualPageConfigs" stripe table-layout="fixed" style="width: 100%">
              <el-table-column prop="id" label="ID" show-overflow-tooltip />
              <el-table-column prop="name" label="名称" show-overflow-tooltip />
              <el-table-column label="跳转链接" show-overflow-tooltip>
                <template #default="scope">
                  <el-link type="primary" :href="scope.row.jumpLink" :underline="false">
                    {{ scope.row.jumpLink }}
                  </el-link>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="可视化应用配置" name="visualAppConfigs">
          <div class="resource-container">
            <el-table :data="visualAppConfigs" stripe table-layout="fixed" style="width: 100%">
              <el-table-column prop="id" label="ID" show-overflow-tooltip />
              <el-table-column prop="name" label="名称" show-overflow-tooltip />
              <el-table-column label="跳转链接" show-overflow-tooltip>
                <template #default="scope">
                  <el-link type="primary" :href="scope.row.jumpLink" :underline="false">
                    {{ scope.row.jumpLink }}
                  </el-link>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="数据看板配置" name="dashboardConfigs">
          <div class="resource-container">
            <el-table :data="dashboardConfigs" stripe table-layout="fixed" style="width: 100%">
              <el-table-column prop="id" label="ID" show-overflow-tooltip />
              <el-table-column prop="name" label="名称" show-overflow-tooltip />
              <el-table-column label="跳转链接" show-overflow-tooltip>
                <template #default="scope">
                  <el-link type="primary" :href="scope.row.jumpLink" :underline="false">
                    {{ scope.row.jumpLink }}
                  </el-link>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="菜单配置" name="menuConfigs">
          <div class="resource-container">
            <el-table :data="menuConfigs" stripe table-layout="fixed" style="width: 100%">
              <el-table-column prop="id" label="ID" show-overflow-tooltip />
              <el-table-column prop="name" label="名称" show-overflow-tooltip />
              <el-table-column label="跳转链接" show-overflow-tooltip>
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
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import {
  Document, Files, PieChart, Connection
} from '@element-plus/icons-vue'
import * as monaco from 'monaco-editor'
import BaseChart from '@/components/echarts/base-chart.vue'
import { setupMonacoWorkers } from '@u/monaco-workers'

// 定义可视化图表接口
interface VisualizationChart {
  title: string
  description: string
  icon: any
}

// 定义产物接口
interface Artifact {
  name: string
  path: string
  icon: any
}

// 定义资源项接口
interface ResourceItem {
  id: string
  name: string
  jumpLink: string
}

// 当前激活的选项卡
const activeTab = ref('visualization')

// 图表相关
const dynamicChartRef = ref()
const chartLoading = ref(false)

// 图表数据
interface ChartData {
  chartType: string
  option: any | null
  rawData: any[]
  columns: string[]
}

const chartData = reactive<ChartData>({
  chartType: '',
  option: null,
  rawData: [],
  columns: []
})

// 处理图表数据事件
const handleDataVisualizationChartData = (event: CustomEvent) => {
  const { chartType, option, rawData, columns } = event.detail
  chartData.chartType = chartType || 'line'
  chartData.option = option
  chartData.rawData = rawData || []
  chartData.columns = columns || []
}

// Monaco Editor 相关
const editorContainer = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null

// 示例配置文件内容
const configContent = `# frpc.toml 配置文件
serverAddr = "10.106.108.110"
serverPort = 8080
token = "12345678"

[web]
type = "http"
localPort = 80
customDomains = ["example.com"]

[ssh]
type = "tcp"
localIP = "127.0.0.1"
localPort = 22
remotePort = 6000

[plugin.unix_domain_socket]
type = "unix_domain_socket"
remotePort = 6001
socketPath = "/tmp/docker.sock"

[plugin.registry]
type = "tcp"
remotePort = 6002
plugin = "http_proxy"
pluginOpts = { host = "0.0.0.0", port = "9001" }
`

// 可视化图表数据
const visualizationCharts = ref<VisualizationChart[]>([
  {
    title: '网络连接可视化',
    description: '网络连接流量图',
    icon: PieChart
  },
  {
    title: '进程关系图',
    description: '进程调用关系图',
    icon: Connection
  }
])

// 可视化页面配置数据
const visualPageConfigs = ref<ResourceItem[]>([
  {
    id: 'page-config-001',
    name: '主机行为可视化页面配置',
    jumpLink: '/visualization/page-configs/host-behavior'
  },
  {
    id: 'page-config-002',
    name: '网络连接可视化页面配置',
    jumpLink: '/visualization/page-configs/network-connection'
  },
  {
    id: 'page-config-003',
    name: '风险事件可视化页面配置',
    jumpLink: '/visualization/page-configs/risk-analysis'
  }
])

// 可视化应用配置数据
const visualAppConfigs = ref<ResourceItem[]>([
  {
    id: 'app-config-001',
    name: '主机行为可视化应用配置',
    jumpLink: '/visualization/app-configs/host-behavior'
  },
  {
    id: 'app-config-002',
    name: '网络连接可视化应用配置',
    jumpLink: '/visualization/app-configs/network-connection'
  },
  {
    id: 'app-config-003',
    name: '风险事件可视化应用配置',
    jumpLink: '/visualization/app-configs/risk-analysis'
  }
])

// 数据看板配置数据
const dashboardConfigs = ref<ResourceItem[]>([
  {
    id: 'dashboard-001',
    name: '安全态势总览配置',
    jumpLink: '/dashboard/configs/security-overview'
  },
  {
    id: 'dashboard-002',
    name: '数据可视化指标看板配置',
    jumpLink: '/dashboard/configs/data-visualization-metrics'
  },
  {
    id: 'dashboard-003',
    name: '资产风险看板配置',
    jumpLink: '/dashboard/configs/asset-risk'
  }
])

// 菜单配置数据
const menuConfigs = ref<ResourceItem[]>([
  {
    id: 'menu-001',
    name: '数据可视化总览菜单配置',
    jumpLink: '/visualization/menu-configs/overview'
  },
  {
    id: 'menu-002',
    name: '图表库菜单配置',
    jumpLink: '/visualization/menu-configs/chart-library'
  },
  {
    id: 'menu-003',
    name: '数据看板菜单配置',
    jumpLink: '/visualization/menu-configs/dashboard'
  }
])

// 产物数据
const artifacts = ref<Artifact[]>([
  {
    name: 'frpc.toml',
    path: 'c:\\windows\\temp\\frpc.toml',
    icon: Document
  },
  {
    name: '网络连接日志',
    path: 'network_connections.log',
    icon: Files
  },
  {
    name: '进程创建事件',
    path: 'process_creation_events.json',
    icon: Document
  }
])

onMounted(() => {
  // 监听图表数据事件
  window.addEventListener('dataVisualizationChartData', handleDataVisualizationChartData as EventListener)

  // 初始化Monaco编辑器
  if (editorContainer.value) {
    setupMonacoWorkers()
    editor = monaco.editor.create(editorContainer.value, {
      value: configContent,
      language: 'ini',
      theme: 'vs-dark',
      automaticLayout: true,
      minimap: { enabled: true },
      scrollBeyondLastLine: false,
      fontSize: 14,
      tabSize: 2,
      readOnly: false,
    })
  }
})

onBeforeUnmount(() => {
  // 移除事件监听
  window.removeEventListener('dataVisualizationChartData', handleDataVisualizationChartData as EventListener)

  // 销毁编辑器
  if (editor) {
    editor.dispose()
  }
})

// 保存配置
const saveConfig = () => {
  if (editor) {
    const value = editor.getValue()
    console.log('保存配置:', value)
    // 这里可以添加实际的保存逻辑
  }
}

// 重置配置
const resetConfig = () => {
  if (editor) {
    editor.setValue(configContent)
    console.log('重置配置')
    // 这里可以添加实际的重置逻辑
  }
}

// 下载产物
const downloadArtifact = (index: number) => {
  const artifact = artifacts.value[index]
  console.log(`下载产物: ${artifact.name}`)
  // 这里可以添加实际的下载逻辑
}

// 查看产物
const viewArtifact = (index: number) => {
  const artifact = artifacts.value[index]
  console.log(`查看产物: ${artifact.name}`)
  // 这里可以添加实际的查看逻辑
}

</script>

<style scoped>
/* 面板样式 */
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

/* 选项卡样式 */
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
  padding: 0px 30px;
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

/* 可视化面板样式 */
.visualization-container {
  margin: 12px;
  background-color: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.visualization-header {
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
}

.visualization-header h4 {
  margin: 0;
  font-size: 16px;
}

.visualization-content {
  padding: 20px;
  min-height: 200px;
}

.placeholder-chart {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  background-color: #f9f9f9;
  border-radius: 4px;
  color: #909399;
}

.placeholder-chart .el-icon {
  font-size: 48px;
  margin-bottom: 10px;
}

/* 产物面板样式 */
.artifacts-container {
  padding: 12px;
}

.artifact-item {
  display: flex;
  align-items: center;
  padding: 12px;
  margin-bottom: 8px;
  background-color: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}

.artifact-icon {
  font-size: 24px;
  margin-right: 12px;
  color: #409eff;
}

.artifact-info {
  flex: 1;
}

.artifact-name {
  font-weight: 500;
  margin-bottom: 4px;
}

.artifact-path {
  font-size: 12px;
  color: #909399;
}

.artifact-actions {
  display: flex;
  gap: 8px;
}

/* 资源列表样式 */
.resource-container {
  padding: 12px;
}

/* 配置面板样式 */
.config-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0;
}

.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #2d2d30;
  color: #e0e0e0;
}

.config-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
}

.config-actions {
  display: flex;
  gap: 8px;
}

.editor-container {
  flex: 1;
  min-height: 500px;
}

/* 数据表格样式 */
.data-table-container {
  margin-top: 16px;
  border-top: 1px solid #eee;
  padding-top: 16px;
}

.data-table-header {
  padding: 0 16px 12px;
}

.data-table-header h4 {
  margin: 0;
  font-size: 14px;
  color: #606266;
}
</style>
