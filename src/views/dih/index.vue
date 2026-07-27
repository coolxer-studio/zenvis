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
          <ViewCenter
            :suggestions="mySuggestions"
            :skill-entry-unavailable="skillEntryUnavailable"
          />
        </el-splitter-panel>
        <el-splitter-panel v-if="showRightPanel" collapsible :size="30" min="20">
          <ViewRightDataVisualization
            v-if="activeAgentType === 'agent_data_visualization'"
          />
          <ViewRightDataAnalysis v-if="activeAgentType === 'agent_data_analysis'" />
          <ViewRightConfigManagement v-if="activeAgentType === 'agent_config_management'" />
          <ViewRightDataAccess
            v-if="activeAgentType === 'agent_data_access'"
          />
          <ViewRightReport v-if="activeAgentType === 'agent_report'" />
        </el-splitter-panel>
      </el-splitter>
    </div>

    <!-- 自定义下拉抽屉 -->
    <div class="custom-drawer" :class="{ 'drawer-open': drawerVisible }">
      <ViewDrawer :visible="drawerVisible" @close="toggleDrawer" />
    </div>
  </div>
</template>

<script setup lang="ts">
import ViewLeft from './components/view-left.vue';
import ViewCenter from './components/view-center.vue';
import ViewRightDataVisualization from './components/view-right-data-visualization.vue';
import ViewRightDataAnalysis from './components/view-right-data-analysis.vue';
import ViewRightConfigManagement from './components/view-right-config-management.vue';
import ViewRightDataAccess from './components/view-right-data-access.vue';
import ViewRightReport from './components/view-right-report.vue';

import ViewDrawer from './components/view-drawer.vue';
import { computed, onActivated, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import {
  ArrowDown,
  ArrowUp,
  Connection,
  DataAnalysis,
  Document,
  MagicStick,
  Monitor,
  Operation,
} from '@element-plus/icons-vue';
import { DihService } from '@/service/api';
import type { ChatSkillEntryVo } from '@/types/type-dih';

const route = useRoute();

// 抽屉显示状态
const drawerVisible = ref(false);

// 切换抽屉显示/隐藏
const toggleDrawer = () => {
  drawerVisible.value = !drawerVisible.value;
};

// 定义建议接口
interface Suggestion {
  type: string;
  agentType: string;
  label: string;
  icon: any;
}

const agentIconMap: Record<string, any> = {
  agent_data_access: Connection,
  agent_data_visualization: Monitor,
  agent_data_analysis: DataAnalysis,
  agent_config_management: Operation,
  agent_report: Document,
};

const configuredIconMap: Record<string, any> = {
  connection: Connection,
  monitor: Monitor,
  'data-analysis': DataAnalysis,
  operation: Operation,
  document: Document,
  'magic-stick': MagicStick,
};

const mySuggestions = ref<Suggestion[]>([]);
const entriesLoaded = ref(false);
const routeChatType = computed(() => String(route.query.type || 'ask'));
const activeSuggestion = computed(() =>
  mySuggestions.value.find(item => item.type === routeChatType.value),
);
const activeAgentType = computed(() => {
  if (activeSuggestion.value?.agentType) {
    return activeSuggestion.value.agentType;
  }
  return agentIconMap[routeChatType.value] ? routeChatType.value : '';
});
const showRightPanel = computed(() => Boolean(agentIconMap[activeAgentType.value]));
const skillEntryUnavailable = computed(() =>
  entriesLoaded.value
  && routeChatType.value.startsWith('skill:')
  && !activeSuggestion.value,
);

const toSuggestion = (entry: ChatSkillEntryVo): Suggestion => ({
  type: entry.chatType,
  agentType: entry.agentType,
  label: entry.label || entry.skillId,
  icon: configuredIconMap[entry.icon] || agentIconMap[entry.agentType] || MagicStick,
});

const loadChatSkillEntries = async () => {
  try {
    const entries = await DihService.getChatSkillEntries(true);
    mySuggestions.value = entries.map(toSuggestion);
    entriesLoaded.value = true;
  } catch (error) {
    console.error('获取 Skill 聊天入口失败:', error);
  }
};

const handleWindowFocus = () => {
  void loadChatSkillEntries();
};

onMounted(() => {
  void loadChatSkillEntries();
  window.addEventListener('focus', handleWindowFocus);
});

onActivated(() => {
  void loadChatSkillEntries();
});

onBeforeUnmount(() => {
  window.removeEventListener('focus', handleWindowFocus);
});
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
