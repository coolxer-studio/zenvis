<template>
  <VisualizationDecisionMessagePart
    :part="part"
    :interactive="interactive"
    agent-label="可视化应用"
    revise-placeholder="输入需要调整的页面、配置、目标文件或菜单参数"
    @choose-decision="emit('chooseVisualizationAppDecision', $event)"
  />
</template>

<script setup lang="ts">
import VisualizationDecisionMessagePart from './visualization-decision-message-part.vue';
import type { VisualizationDecision } from './visualization-decision-message-part.vue';
import type { ChatMessagePart } from '@/types/type-dih';

defineProps<{ part: ChatMessagePart; interactive?: boolean }>();

const emit = defineEmits<{
  (
    e: 'chooseVisualizationAppDecision',
    payload: { part: ChatMessagePart; decision: VisualizationDecision; detail?: string },
  ): void;
}>();
</script>
