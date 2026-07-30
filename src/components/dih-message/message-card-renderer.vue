<template>
  <div class="rich-message">
    <template v-for="(part, index) in renderParts" :key="part.id || index">
      <TextMessagePart
        v-if="isTextPart(part)"
        :part="part"
        :interactive="interactive"
        @copy-code="emit('copyCode', $event)"
        @select-prompt-suggestion="emit('selectPromptSuggestion', $event)"
      />
      <ApprovalMessagePart
        v-else-if="isApprovalPart(part)"
        :part="part"
        :interactive="interactive"
        @decide-action="emit('decideAction', $event)"
        @submit-info-steps="emit('submitInfoSteps', $event)"
        @decide-mcp-approval="emit('decideMcpApproval', $event)"
      />
      <ConfigMessagePart
        v-else-if="isConfigPart(part)"
        :part="part"
        :interactive="interactive"
        @copy-code="emit('copyCode', $event)"
      />
      <VisualizationMessagePart
        v-else-if="isVisualizationPart(part)"
        :part="part"
        :interactive="interactive"
        @copy-code="emit('copyCode', $event)"
        @add-chart-library="emit('addChartLibrary', $event)"
        @chart-render-failed="emit('chartRenderFailed', $event)"
      />
      <DataAnalysisMessagePart
        v-else-if="part.type === 'data-analysis-record'"
        :part="part"
        :interactive="interactive"
      />
      <DataAccessMessagePart
        v-else-if="isDataAccessPart(part)"
        :part="part"
        :interactive="interactive"
        @choose-data-access-decision="emit('chooseDataAccessDecision', $event)"
      />
      <ConfigRecordMessagePart v-else-if="part.type === 'config-record'" :part="part" />
      <NoticeMessagePart v-else-if="part.type === 'notice'" :part="part" />
      <TextMessagePart
        v-else
        :part="part"
        :interactive="interactive"
        @copy-code="emit('copyCode', $event)"
        @select-prompt-suggestion="emit('selectPromptSuggestion', $event)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, provide } from 'vue';
import type { ChatMessage, ChatMessagePart, McpApprovalDecision } from '@/types/type-dih';
import ApprovalMessagePart from './message-parts/approval-message-part.vue';
import ConfigRecordMessagePart from './message-parts/config-record-message-part.vue';
import ConfigMessagePart from './message-parts/config-message-part.vue';
import DataAnalysisMessagePart from './message-parts/data-analysis-message-part.vue';
import DataAccessMessagePart from './message-parts/data-access-message-part.vue';
import NoticeMessagePart from './message-parts/notice-message-part.vue';
import TextMessagePart from './message-parts/text-message-part.vue';
import VisualizationMessagePart from './message-parts/visualization-message-part.vue';
import { createMarkdownRenderer, markdownRendererKey } from './message-parts/message-part-context';

type InfoStepAnswer = {
  id: string;
  title: string;
  value: string;
  source: 'suggestion' | 'custom';
};

type MessageCardMode = 'interactive' | 'readonly';

const props = withDefaults(
  defineProps<{
    message: ChatMessage;
    mode?: MessageCardMode;
  }>(),
  {
    mode: 'interactive',
  },
);

const emit = defineEmits<{
  (e: 'copyCode', content: string): void;
  (
    e: 'decideAction',
    payload: {
      part: ChatMessagePart;
      decision: 'approved' | 'rejected' | 'revise' | 'retry';
      detail?: string;
    },
  ): void;
  (
    e: 'submitInfoSteps',
    payload: {
      part: ChatMessagePart;
      answers: InfoStepAnswer[];
    },
  ): void;
  (e: 'addChartLibrary', part: ChatMessagePart): void;
  (
    e: 'chartRenderFailed',
    payload: { part: ChatMessagePart; error: string },
  ): void;
  (
    e: 'chooseDataAccessDecision',
    payload: {
      part: ChatMessagePart;
      decision: 'apply_config' | 'abandon' | 'revise' | 'retry';
      detail?: string;
    },
  ): void;
  (
    e: 'decideMcpApproval',
    payload: {
      part: ChatMessagePart;
      decision: McpApprovalDecision;
    },
  ): void;
  (e: 'selectPromptSuggestion', prompt: string): void;
}>();

const markdownRenderer = createMarkdownRenderer();
provide(markdownRendererKey, markdownRenderer);
const interactive = computed(() => props.mode === 'interactive');

const parseFallbackThinkingParts = (content: string): ChatMessagePart[] => {
  const thinkStart = content.indexOf('<think>');
  if (thinkStart === -1) {
    return [
      {
        id: `${props.message.id || 'message'}-content`,
        type: 'markdown',
        content,
      },
    ];
  }

  const parts: ChatMessagePart[] = [];
  const beforeThinking = content.slice(0, thinkStart);
  if (beforeThinking.trim()) {
    parts.push({
      id: `${props.message.id || 'message'}-before-thinking`,
      type: 'markdown',
      content: beforeThinking,
    });
  }

  const thinkEnd = content.indexOf('</think>', thinkStart);
  if (thinkEnd === -1) {
    parts.push({
      id: `${props.message.id || 'message'}-thinking-running`,
      type: 'thinking',
      title: '思考过程',
      content: content.slice(thinkStart + '<think>'.length).trim(),
      status: 'running',
    });
    return parts;
  }

  parts.push({
    id: `${props.message.id || 'message'}-thinking`,
    type: 'thinking',
    title: '思考过程',
    content: content.slice(thinkStart + '<think>'.length, thinkEnd).trim(),
    status: 'completed',
  });

  const afterThinking = content.slice(thinkEnd + '</think>'.length);
  if (afterThinking.trim()) {
    parts.push({
      id: `${props.message.id || 'message'}-after-thinking`,
      type: 'markdown',
      content: afterThinking,
    });
  }

  return parts.length > 0
    ? parts
    : [
        {
          id: `${props.message.id || 'message'}-content`,
          type: 'markdown',
          content,
        },
      ];
};

const renderParts = computed<ChatMessagePart[]>(() => {
  if (props.message.parts && props.message.parts.length > 0) {
    return props.message.parts;
  }
  return parseFallbackThinkingParts(props.message.content);
});

const TEXT_PART_TYPES = new Set(['markdown', 'thinking', 'code', 'prompt-suggestions']);
const APPROVAL_PART_TYPES = new Set(['mcp-approval', 'confirm', 'info-steps']);
const CONFIG_PART_TYPES = new Set(['config', 'report-document']);
const VISUALIZATION_PART_TYPES = new Set([
  'visualization-chart-preview',
  'visualization-chart-record',
  'visualization-config-record',
  'dashboard-config-record',
  'menu-config-record',
  'chart',
]);
const DATA_ACCESS_PART_TYPES = new Set([
  'data-access-decision',
  'metadata-config-record',
  'data-push-service-record',
]);

const isTextPart = (part: ChatMessagePart) => TEXT_PART_TYPES.has(part.type);
const isApprovalPart = (part: ChatMessagePart) => APPROVAL_PART_TYPES.has(part.type);
const isConfigPart = (part: ChatMessagePart) => CONFIG_PART_TYPES.has(part.type);
const isVisualizationPart = (part: ChatMessagePart) => VISUALIZATION_PART_TYPES.has(part.type);
const isDataAccessPart = (part: ChatMessagePart) => DATA_ACCESS_PART_TYPES.has(part.type);

onBeforeUnmount(() => {
  markdownRenderer.clearMarkdownCache();
});
</script>

<style lang="scss" src="./message-parts/message-parts.scss"></style>
