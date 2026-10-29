<template>
  <div class="rich-message">
    <template v-for="(part, index) in renderParts" :key="part.id || index">
      <RagSearchPart v-if="isRagSearchPart(part)" :part="part" />
      <ToolActivityPart
        v-else-if="isToolCallPart(part) && !isToolCallPart(renderParts[index - 1])"
        :parts="toolGroupAt(index)"
      />
      <template v-else-if="!isToolCallPart(part)">
        <AgentRunCard
          v-if="part.type === 'agent-run'"
          :run="agentRunFromPart(part)"
          :current-tool="agentRunToolFromPart(part)"
          :activity-events="agentRunEventsFromPart(part)"
          @resume="emit('resumeAgentRun', $event)"
          @reply="emit('replyAgentRun', $event)"
        />
        <TextMessagePart
          v-else-if="isTextPart(part)"
          :part="part"
          :interactive="interactive"
          @copy-code="emit('copyCode', $event)"
          @select-prompt-suggestion="emit('selectPromptSuggestion', $event)"
        />
        <ApprovalMessagePart
          v-else-if="isApprovalPart(part)"
          :part="part"
          :interactive="interactive"
          :workflow-current-revision="workflowCurrentRevision(part)"
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
          :workflow-current-revision="workflowCurrentRevision(part)"
          @copy-code="emit('copyCode', $event)"
          @add-chart-library="emit('addChartLibrary', $event)"
          @chart-render-failed="emit('chartRenderFailed', $event)"
          @decide-action="emit('decideAction', $event)"
        />
        <DataAnalysisMessagePart
          v-else-if="part.type === 'data-analysis-record'"
          :part="part"
          :interactive="interactive"
        />
        <MetadataConfigDecisionMessagePart
          v-else-if="part.type === 'metadata-config-decision'"
          :part="part"
          :interactive="interactive"
          @choose-metadata-config-decision="emit('chooseMetadataConfigDecision', $event)"
        />
        <DataPushServiceDecisionMessagePart
          v-else-if="part.type === 'data-push-service-decision'"
          :part="part"
          :interactive="interactive"
          @choose-data-push-decision="emit('chooseDataPushDecision', $event)"
        />
        <VisualizationChartDecisionMessagePart
          v-else-if="part.type === 'visualization-chart-decision'"
          :part="part"
          :interactive="interactive"
          @choose-visualization-chart-decision="emit('chooseVisualizationChartDecision', $event)"
        />
        <VisualizationAppDecisionMessagePart
          v-else-if="part.type === 'visualization-app-decision'"
          :part="part"
          :interactive="interactive"
          @choose-visualization-app-decision="emit('chooseVisualizationAppDecision', $event)"
        />
        <VisualizationDashboardDecisionMessagePart
          v-else-if="part.type === 'visualization-dashboard-decision'"
          :part="part"
          :interactive="interactive"
          @choose-visualization-dashboard-decision="emit('chooseVisualizationDashboardDecision', $event)"
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
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, provide } from 'vue';
import type { ChatMessage, ChatMessagePart, McpApprovalDecision } from '@/types/type-dih';
import ApprovalMessagePart from './message-parts/approval-message-part.vue';
import AgentRunCard from './message-parts/agent-run-card.vue';
import ConfigRecordMessagePart from './message-parts/config-record-message-part.vue';
import ConfigMessagePart from './message-parts/config-message-part.vue';
import DataAnalysisMessagePart from './message-parts/data-analysis-message-part.vue';
import MetadataConfigDecisionMessagePart from './message-parts/metadata-config-decision-message-part.vue';
import DataPushServiceDecisionMessagePart from './message-parts/data-push-service-decision-message-part.vue';
import VisualizationAppDecisionMessagePart from './message-parts/visualization-app-decision-message-part.vue';
import VisualizationChartDecisionMessagePart from './message-parts/visualization-chart-decision-message-part.vue';
import VisualizationDashboardDecisionMessagePart from './message-parts/visualization-dashboard-decision-message-part.vue';
import NoticeMessagePart from './message-parts/notice-message-part.vue';
import RagSearchPart from './message-parts/rag-search-part.vue';
import TextMessagePart from './message-parts/text-message-part.vue';
import ToolActivityPart from './message-parts/tool-activity-part.vue';
import VisualizationMessagePart from './message-parts/visualization-message-part.vue';
import { createMarkdownRenderer, markdownRendererKey } from './message-parts/message-part-context';
import { collapseLegacyToolCallParts, isToolCallPart } from './tool-call-parts';
import { isRagSearchPart } from './rag-search-parts';
import type { AgentRun } from '@/types/type-dih';
import type { AgentRunEvent } from '@/types/type-dih';

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
    workflowRevisions?: Record<string, number>;
  }>(),
  {
    mode: 'interactive',
    workflowRevisions: () => ({}),
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
  (e: 'chartRenderFailed', payload: { part: ChatMessagePart; error: string }): void;
  (e: 'resumeAgentRun', runId: string): void;
  (e: 'replyAgentRun', payload: { runId: string; message: string }): void;
  (
    e: 'chooseMetadataConfigDecision',
    payload: {
      part: ChatMessagePart;
      decision: 'apply_config' | 'abandon' | 'revise' | 'retry' | 'force_approve';
      detail?: string;
    },
  ): void;
  (
    e: 'chooseDataPushDecision',
    payload: {
      part: ChatMessagePart;
      decision: 'apply_config' | 'abandon' | 'revise' | 'retry';
      detail?: string;
    },
  ): void;
  (
    e: 'chooseVisualizationChartDecision',
    payload: {
      part: ChatMessagePart;
      decision: 'apply_config' | 'abandon' | 'revise' | 'retry';
      detail?: string;
    },
  ): void;
  (
    e: 'chooseVisualizationAppDecision',
    payload: {
      part: ChatMessagePart;
      decision: 'apply_config' | 'abandon' | 'revise' | 'retry';
      detail?: string;
    },
  ): void;
  (
    e: 'chooseVisualizationDashboardDecision',
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

const workflowCurrentRevision = (part: ChatMessagePart): number | undefined => {
  const workflowId = part.metadata?.workflowId;
  return typeof workflowId === 'string' ? props.workflowRevisions[workflowId] : undefined;
};

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
  const parts =
    props.message.parts && props.message.parts.length > 0
      ? props.message.parts
      : parseFallbackThinkingParts(props.message.content);
  const rendered = collapseLegacyToolCallParts(parts, props.message.id || 'message');
  const errorContent = props.message.isError ? (props.message.content || '').trim() : '';
  if (
    !errorContent ||
    rendered.some(part => part.type === 'markdown' && (part.content || '').trim() === errorContent)
  ) {
    return rendered;
  }
  return [
    ...rendered,
    {
      id: `${props.message.id || 'message'}-stream-error`,
      type: 'markdown',
      content: errorContent,
    },
  ];
});

const toolGroupAt = (index: number): ChatMessagePart[] => {
  const parts: ChatMessagePart[] = [];
  for (let cursor = index; cursor < renderParts.value.length; cursor += 1) {
    const part = renderParts.value[cursor];
    if (!isToolCallPart(part)) break;
    parts.push(part);
  }
  return parts;
};

const TEXT_PART_TYPES = new Set(['markdown', 'thinking', 'code', 'prompt-suggestions']);
const APPROVAL_PART_TYPES = new Set(['mcp-approval', 'confirm', 'info-steps']);
const CONFIG_PART_TYPES = new Set(['config', 'report-document']);
const VISUALIZATION_PART_TYPES = new Set([
  'visualization-chart-preview',
  'visualization-chart-record',
  'visualization-app-record',
  'visualization-dashboard-record',
  'chart',
]);
const isTextPart = (part: ChatMessagePart) => TEXT_PART_TYPES.has(part.type);
const isApprovalPart = (part: ChatMessagePart) => APPROVAL_PART_TYPES.has(part.type);
const isConfigPart = (part: ChatMessagePart) => CONFIG_PART_TYPES.has(part.type);
const isVisualizationPart = (part: ChatMessagePart) => VISUALIZATION_PART_TYPES.has(part.type);
const agentRunFromPart = (part: ChatMessagePart): AgentRun | null => {
  const run = part.metadata?.run;
  return run && typeof run === 'object' ? (run as AgentRun) : null;
};

const agentRunToolFromPart = (part: ChatMessagePart): Record<string, unknown> | null => {
  const tool = part.metadata?.currentTool;
  return tool && typeof tool === 'object' ? (tool as Record<string, unknown>) : null;
};

const agentRunEventsFromPart = (part: ChatMessagePart): AgentRunEvent[] => {
  const events = part.metadata?.activityEvents;
  return Array.isArray(events) ? events as AgentRunEvent[] : [];
};

onBeforeUnmount(() => {
  markdownRenderer.clearMarkdownCache();
});
</script>

<style lang="scss" src="./message-parts/message-parts.scss"></style>
