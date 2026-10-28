<template>
  <VisualizationDecisionMessagePart
    :part="part"
    :interactive="interactive"
    agent-label="数据大屏"
    revise-placeholder="输入需要调整的看板、配置、Dashboard 或菜单参数"
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
