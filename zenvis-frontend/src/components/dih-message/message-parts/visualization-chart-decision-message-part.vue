<template>
  <VisualizationDecisionMessagePart
    :part="part"
    :interactive="interactive"
    agent-label="临时图表"
    revise-placeholder="输入需要调整的实体、字段、统计口径或查询参数"
    @choose-decision="emit('chooseVisualizationChartDecision', $event)"
  />
</template>

<script setup lang="ts">
import VisualizationDecisionMessagePart from './visualization-decision-message-part.vue';
import type { VisualizationDecision } from './visualization-decision-message-part.vue';
import type { ChatMessagePart } from '@/types/type-dih';

defineProps<{ part: ChatMessagePart; interactive?: boolean }>();

const emit = defineEmits<{
  (
    e: 'chooseVisualizationChartDecision',
    payload: { part: ChatMessagePart; decision: VisualizationDecision; detail?: string },
  ): void;
}>();
</script>
