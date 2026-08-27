<template>
  <div class="panel right-panel">
    <!-- 选项卡 -->
    <div class="tab-container">
      <el-tabs v-model="activeTab" class="right-tabs">
        <el-tab-pane label="配置" name="config">
          <div class="config-container">
            <div class="config-header">
              <div class="config-title">
                <el-icon><Document /></el-icon>
                frpc.toml
              </div>
              <div class="config-actions">
                <el-button size="small" type="primary" @click="saveConfig">保存</el-button>
                <el-button size="small" @click="resetConfig">重置</el-button>
              </div>
            </div>
            <div class="editor-container" ref="editorContainer"></div>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="可视化" name="visualization">
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
        
        <el-tab-pane label="产物" name="artifacts">
          <div class="artifacts-container">
            <div 
              v-for="(artifact, index) in artifacts" 
              :key="index" 
              class="artifact-item"
            >
              <div class="artifact-icon">
                <el-icon><component :is="artifact.icon" /></el-icon>
              </div>
              <div class="artifact-info">
                <div class="artifact-name">{{ artifact.name }}</div>
                <div class="artifact-path">{{ artifact.path }}</div>
              </div>
              <div class="artifact-actions">
                <el-button type="primary" size="small" plain @click="downloadArtifact(index)">
                  下载
                </el-button>
                <el-button type="info" size="small" plain @click="viewArtifact(index)">
                  查看
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, reactive } from 'vue'
import { 
  Document, Files, PieChart, Connection
} from '@element-plus/icons-vue'
import * as monaco from 'monaco-editor'
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

// 当前激活的选项卡
const activeTab = ref('config')

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
</style>
