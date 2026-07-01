<template>
  <div class="container">
    <!-- 顶部下拉标签 -->
    <div class="drawer-trigger" @click="toggleDrawer">
      <el-icon v-if="drawerVisible"><ArrowUp /></el-icon>
      <el-icon v-else><ArrowDown /></el-icon>
    </div>
    
    <!-- 三个面板 -->
    <div class="panels-container">
      <el-splitter direction="horizontal">
        <el-splitter-panel collapsible :size="20" min="10">
          <ViewLeft />
        </el-splitter-panel>
        <el-splitter-panel collapsible :size="50" min="20">
          <ViewCenter :suggestions="mySuggestions" />
        </el-splitter-panel>
        <el-splitter-panel v-if="showRightPanel" collapsible :size="30" min="20">
          <ViewRightInspect v-if="route.query.type && route.query.type === 'agent_inspect'" />
          <ViewRightAnalysis v-if="route.query.type && route.query.type === 'agent_analysis'" />
          <ViewRightDispose v-if="route.query.type && route.query.type === 'agent_dispose'" />
          <ViewRightDataAccess v-if="route.query.type && route.query.type === 'agent_data_access'" />
          <ViewRightReport v-if="route.query.type && route.query.type === 'agent_report'" />
        </el-splitter-panel>
      </el-splitter>
    </div>
    
    <!-- 自定义下拉抽屉 -->
    <div class="custom-drawer" :class="{ 'drawer-open': drawerVisible }">
      <ViewDrawer @close="toggleDrawer" />
    </div>
  </div>
</template>

<script setup lang="ts">
import ViewLeft from './components/view-left.vue'
import ViewCenter from './components/view-center.vue'
import ViewRightInspect from './components/view-right-inspect.vue'
import ViewRightAnalysis from './components/view-right-analysis.vue'
import ViewRightDispose from './components/view-right-dispose.vue'
import ViewRightDataAccess from './components/view-right-data-access.vue'
import ViewRightReport from './components/view-right-report.vue'

import ViewDrawer from './components/view-drawer.vue'
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  ArrowDown, ArrowUp, Connection, DataAnalysis, Document, Monitor, Operation
} from '@element-plus/icons-vue'

const route = useRoute()
const rightPanelTypes = ['agent_inspect', 'agent_analysis', 'agent_dispose', 'agent_data_access', 'agent_report']
const showRightPanel = computed(() => rightPanelTypes.includes(String(route.query.type || '')))

// 抽屉显示状态
const drawerVisible = ref(false)

// 切换抽屉显示/隐藏
const toggleDrawer = () => {
  drawerVisible.value = !drawerVisible.value
}

// 定义建议接口
interface Suggestion {
  type: string
  label: string
  icon: any
}
// 建议数据
const mySuggestions = ref<Suggestion[]>([
  { type: 'agent_data_access', label: '数据接入', icon: Connection },
  { type: 'agent_inspect', label: '智能巡检', icon: Monitor },
  { type: 'agent_analysis', label: '研判分析', icon: DataAnalysis },
  { type: 'agent_dispose', label: '策略控制', icon: Operation },
  { type: 'agent_report', label: '报表制作', icon: Document },
])

</script>

<style scoped>
.container {
  height: 100%;
  width: 100%;
  padding: 10px;
  box-sizing: border-box;
  box-shadow: var(--el-border-color-light) 0px 0px 10px;
  position: relative;
  overflow: hidden;
}

/* 抽屉触发器样式 */
.drawer-trigger {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  background-color: var(--el-color-primary);
  color: white;
  padding: 5px 15px;
  border-radius: 0 0 8px 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 5px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.drawer-trigger .el-icon {
  transition: transform 0.3s;
}

/* 自定义抽屉样式 */
.custom-drawer {
  position: absolute;
  top: -100%;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: white;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  z-index: 5;
  transition: top 0.3s ease;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.custom-drawer.drawer-open {
  top: 0;
}

/* 面板容器 */
.panels-container {
  height: 100%;
  position: relative;
  z-index: 1;
}
</style>
