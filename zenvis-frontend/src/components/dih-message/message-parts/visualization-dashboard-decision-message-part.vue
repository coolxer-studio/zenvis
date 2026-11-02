<template>
  <VisualizationDecisionMessagePart
    :part="part"
    :interactive="interactive"
    agent-label="数据大屏"
    revise-placeholder="输入需要调整的主题、实体、指标、时间口径、图表类型或布局"
    @choose-decision="emit('chooseVisualizationDashboardDecision', $event)"
  />
</template>

<script setup lang="ts">
import VisualizationDecisionMessagePart from './visualization-decision-message-part.vue';
import type { VisualizationDecision } from './visualization-decision-message-part.vue';
import type { ChatMessagePart } from '@/types/type-dih';

defineProps<{ part: ChatMessagePart; interactive?: boolean }>();

const emit = defineEmits<{
  (
    e: 'chooseVisualizationDashboardDecision',
    payload: { part: ChatMessagePart; decision: VisualizationDecision; detail?: string },
  ): void;
}>();
</script>
