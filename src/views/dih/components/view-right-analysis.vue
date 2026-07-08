<template>
  <div class="panel right-panel">
    <div class="tab-container">
      <el-tabs v-model="activeTab" class="right-tabs">
        <el-tab-pane label="目标定义" name="goal">
          <div class="analysis-section">
            <div
              v-for="(item, index) in goalDefinitions"
              :key="index"
              class="analysis-item"
            >
              <div class="item-label">{{ item.label }}</div>
              <div class="item-content">{{ item.content }}</div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="过程记录" name="process">
          <div class="analysis-section">
            <el-timeline>
              <el-timeline-item
                v-for="(record, index) in processRecords"
                :key="index"
                :timestamp="record.time"
                :type="record.type"
              >
                <div class="timeline-title">{{ record.title }}</div>
                <div class="timeline-content">{{ record.content }}</div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-tab-pane>

        <el-tab-pane label="分析结论" name="conclusion">
          <div class="analysis-section">
            <div class="conclusion-summary">
              <div class="summary-item">
                <div class="summary-label">分析状态</div>
                <div class="summary-value success">已完成</div>
              </div>
              <div class="summary-item">
                <div class="summary-label">风险等级</div>
                <div class="summary-value high-risk">高危</div>
              </div>
              <div class="summary-item">
                <div class="summary-label">置信度</div>
                <div class="summary-value">86%</div>
              </div>
            </div>

            <el-table :data="conclusions" stripe table-layout="fixed" style="width: 100%">
              <el-table-column prop="name" label="结论项" show-overflow-tooltip />
              <el-table-column prop="evidence" label="关键依据" show-overflow-tooltip />
              <el-table-column prop="result" label="判断结果" show-overflow-tooltip />
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="建议策略" name="strategy">
          <div class="analysis-section">
            <div
              v-for="(strategy, index) in suggestedStrategies"
              :key="index"
              class="strategy-item"
            >
              <div class="strategy-header">
                <div class="strategy-title">{{ strategy.title }}</div>
                <el-tag :type="strategy.priorityType">{{ strategy.priority }}</el-tag>
              </div>
              <div class="strategy-content">{{ strategy.content }}</div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="数据推送服务" name="dataPushServices">
          <div class="analysis-section">
            <el-table :data="dataPushServices" stripe table-layout="fixed" style="width: 100%">
              <el-table-column prop="id" label="服务ID" show-overflow-tooltip />
              <el-table-column prop="name" label="服务名称" show-overflow-tooltip />
              <el-table-column prop="target" label="推送目标" show-overflow-tooltip />
              <el-table-column prop="mode" label="推送方式" show-overflow-tooltip />
              <el-table-column label="状态">
                <template #default="scope">
                  <el-tag :type="scope.row.statusType">{{ scope.row.status }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="数据分析任务" name="dataAnalysisTasks">
          <div class="analysis-section">
            <el-table :data="dataAnalysisTasks" stripe table-layout="fixed" style="width: 100%">
              <el-table-column prop="id" label="任务ID" show-overflow-tooltip />
              <el-table-column prop="name" label="任务名称" show-overflow-tooltip />
              <el-table-column prop="type" label="分析类型" show-overflow-tooltip />
              <el-table-column prop="schedule" label="调度周期" show-overflow-tooltip />
              <el-table-column label="状态">
                <template #default="scope">
                  <el-tag :type="scope.row.statusType">{{ scope.row.status }}</el-tag>
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

type TimelineType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

interface GoalDefinition {
  label: string
  content: string
}

interface ProcessRecord {
  time: string
  title: string
  content: string
  type: TimelineType
}

interface Conclusion {
  name: string
  evidence: string
  result: string
}

interface SuggestedStrategy {
  title: string
  content: string
  priority: string
  priorityType: TagType
}

interface DataPushService {
  id: string
  name: string
  target: string
  mode: string
  status: string
  statusType: TagType
}

interface DataAnalysisTask {
  id: string
  name: string
  type: string
  schedule: string
  status: string
  statusType: TagType
}

const activeTab = ref('goal')

const goalDefinitions = ref<GoalDefinition[]>([
  {
    label: '分析对象',
    content: '围绕指定主机、账号与网络连接记录开展关联研判。'
  },
  {
    label: '分析范围',
    content: '覆盖进程行为、网络访问、文件操作、登录事件与威胁情报命中情况。'
  },
  {
    label: '判定目标',
    content: '识别异常行为链路，确认风险等级，并输出可执行的处置建议。'
  }
])

const processRecords = ref<ProcessRecord[]>([
  {
    time: '2026-06-29 10:00:00',
    title: '数据聚合',
    content: '已汇总主机行为、流量日志和资产基线信息。',
    type: 'primary'
  },
  {
    time: '2026-06-29 10:08:00',
    title: '特征匹配',
    content: '发现可疑外联、异常进程链和高频访问特征。',
    type: 'warning'
  },
  {
    time: '2026-06-29 10:16:00',
    title: '证据校验',
    content: '完成关键事件交叉验证，形成可追溯证据链。',
    type: 'success'
  }
])

const conclusions = ref<Conclusion[]>([
  {
    name: '外联风险',
    evidence: '目标主机多次访问异常地址并存在周期性连接行为。',
    result: '疑似异常通信'
  },
  {
    name: '进程行为',
    evidence: '检测到非常规路径进程启动并派生子进程。',
    result: '存在可疑链路'
  },
  {
    name: '综合评级',
    evidence: '网络、进程和情报命中结果存在一致性。',
    result: '高危'
  }
])

const suggestedStrategies = ref<SuggestedStrategy[]>([
  {
    title: '隔离高风险主机',
    content: '对命中异常行为链路的主机进行临时隔离，保留现场数据用于后续取证。',
    priority: '高优先级',
    priorityType: 'danger'
  },
  {
    title: '阻断异常外联',
    content: '将异常目的地址加入阻断策略，并持续观察同类连接是否复现。',
    priority: '高优先级',
    priorityType: 'danger'
  },
  {
    title: '补充检测规则',
    content: '基于本次行为特征补充进程链、访问频次与情报命中规则。',
    priority: '中优先级',
    priorityType: 'warning'
  }
])

const dataPushServices = ref<DataPushService[]>([
  {
    id: 'push-001',
    name: '异常外联事件推送服务',
    target: '安全运营平台',
    mode: '实时推送',
    status: '运行中',
    statusType: 'success'
  },
  {
    id: 'push-002',
    name: '主机风险画像推送服务',
    target: '资产风险中心',
    mode: '定时推送',
    status: '运行中',
    statusType: 'success'
  },
  {
    id: 'push-003',
    name: '研判结果归档推送服务',
    target: '事件归档库',
    mode: '任务完成后推送',
    status: '待执行',
    statusType: 'info'
  }
])

const dataAnalysisTasks = ref<DataAnalysisTask[]>([
  {
    id: 'task-001',
    name: '异常外联聚合分析',
    type: '行为关联',
    schedule: '每小时',
    status: '运行中',
    statusType: 'success'
  },
  {
    id: 'task-002',
    name: '进程链路风险识别',
    type: '链路研判',
    schedule: '实时',
    status: '运行中',
    statusType: 'success'
  },
  {
    id: 'task-003',
    name: '情报命中复核任务',
    type: '威胁情报',
    schedule: '每日',
    status: '待执行',
    statusType: 'info'
  }
])
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

.analysis-section {
  padding: 12px;
}

.analysis-item,
.strategy-item {
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  margin-bottom: 12px;
  padding: 14px;
}

.item-label,
.strategy-title,
.timeline-title {
  color: #303133;
  font-weight: 600;
  margin-bottom: 6px;
}

.item-content,
.strategy-content,
.timeline-content {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.conclusion-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.summary-item {
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  padding: 12px;
}

.summary-label {
  color: #909399;
  font-size: 12px;
  margin-bottom: 6px;
}

.summary-value {
  color: #303133;
  font-weight: 600;
}

.summary-value.success {
  color: #67c23a;
}

.summary-value.high-risk {
  color: #f56c6c;
}

.strategy-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
</style>
