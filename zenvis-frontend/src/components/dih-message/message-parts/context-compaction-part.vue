<template>
  <section class="context-compaction-part" :class="`is-${tone}`">
    <button
      type="button"
      class="compaction-header"
      :aria-expanded="expanded"
      :aria-controls="detailsId"
      @click="expanded = !expanded"
    >
      <span class="compaction-icon" aria-hidden="true">
        <el-icon><Refresh /></el-icon>
      </span>
      <span class="compaction-title">
        <strong>上下文压缩</strong>
        <span>{{ stageLabel }} · {{ reasonLabel }}</span>
      </span>
      <span class="compaction-meta">
        <template v-if="savedTokens > 0">节省 {{ formatNumber(savedTokens) }} tokens</template>
        <template v-else>{{ summaryText || '已应用预算限制' }}</template>
      </span>
      <el-icon class="compaction-chevron" aria-hidden="true">
        <CaretBottom v-if="expanded" />
        <CaretRight v-else />
      </el-icon>
    </button>

    <div v-if="expanded" :id="detailsId" class="compaction-body">
      <div class="compaction-metrics">
        <span v-if="hasNumber(data.beforeTokens) || hasNumber(data.afterTokens)">
          Token {{ formatNumber(data.beforeTokens) }} → {{ formatNumber(data.afterTokens) }}
        </span>
        <span v-if="hasNumber(data.beforeChars) || hasNumber(data.afterChars)">
          字符 {{ formatNumber(data.beforeChars) }} → {{ formatNumber(data.afterChars) }}
        </span>
        <span v-if="hasNumber(data.budgetTokens)">Token 预算 {{ formatNumber(data.budgetTokens) }}</span>
        <span v-if="hasNumber(data.budgetChars)">字符预算 {{ formatNumber(data.budgetChars) }}</span>
        <span v-if="hasNumber(data.omittedMessages)">省略消息 {{ formatNumber(data.omittedMessages) }}</span>
        <span v-if="hasNumber(data.retainedMessages)">保留消息 {{ formatNumber(data.retainedMessages) }}</span>
        <span v-if="hasNumber(data.truncatedMessages)">截断消息 {{ formatNumber(data.truncatedMessages) }}</span>
        <span v-if="hasNumber(data.omittedAttachments)">省略附件正文 {{ formatNumber(data.omittedAttachments) }}</span>
        <span v-if="hasNumber(data.includedAttachments)">保留附件引用 {{ formatNumber(data.includedAttachments) }}</span>
      </div>

      <div v-if="summaryText" class="compaction-preview">
        <div class="compaction-preview-title">保留摘要预览</div>
        <pre>{{ summaryText }}</pre>
      </div>
      <div v-else class="compaction-empty">本阶段没有生成摘要，只应用了窗口或预算限制。</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { CaretBottom, CaretRight, Refresh } from '@element-plus/icons-vue';
import type { ChatMessagePart, ContextCompactionEventData } from '@/types/type-dih';

defineOptions({ name: 'ContextCompactionPart' });

const props = defineProps<{
  part: ChatMessagePart;
}>();

const expanded = ref(false);
const data = computed<ContextCompactionEventData>(() => props.part.metadata || {});
const detailsId = computed(() => `context-compaction-details-${props.part.id || 'unknown'}`);
const summaryText = computed(() => String(data.value.summaryPreview || '').trim());
const savedTokens = computed(() => Number(data.value.savedTokens || 0));
const tone = computed(() => String(props.part.status || 'completed').toLowerCase());

const stageLabels: Record<string, string> = {
  'run-context': '任务上下文',
  'model-input': '模型输入',
  'history-window': '聊天记忆窗口',
  'attachment-memory': '附件记忆',
};

const reasonLabels: Record<string, string> = {
  recent_history_window: '保留最近对话',
  recent_history_truncated: '最近对话与长消息裁剪',
  prompt_char_limit: '任务提示词字符上限',
  checkpoint_truncated: '步骤检查点截断',
  prompt_token_budget: '模型 Token 预算',
  history_window_budget: '历史窗口预算',
  history_window_summary: '较早对话摘要',
  attachment_body_removed: '附件正文移出长期记忆',
};

const stageLabel = computed(() => stageLabels[String(data.value.stage || '')]
  || String(data.value.stage || '上下文'));
const reasonLabel = computed(() => reasonLabels[String(data.value.reason || '')]
  || String(data.value.reason || '已压缩'));
const hasNumber = (value: unknown) => Number.isFinite(Number(value)) && value !== undefined;
const formatNumber = (value: unknown) => {
  const number = Number(value);
  if (!Number.isFinite(number)) return '0';
  return new Intl.NumberFormat('zh-CN').format(number);
};
</script>

<style lang="scss" scoped>
.context-compaction-part {
  overflow: hidden;
  background: var(--zv-bg-card);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-md);
}

.compaction-header {
  display: flex;
  width: 100%;
  padding: 9px 11px;
  align-items: center;
  gap: 9px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--zv-bg-subtle);
  border: 0;
}

.compaction-icon {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  color: var(--zv-primary);
  place-items: center;
  background: var(--zv-primary-soft);
  border-radius: 7px;
}

.compaction-title {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 1px;
}

.compaction-title strong {
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-sm);
}

.compaction-title span,
.compaction-meta {
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}

.compaction-meta {
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compaction-chevron {
  flex: 0 0 auto;
  color: var(--zv-text-muted);
}

.compaction-body {
  padding: 10px 12px 12px;
  border-top: 1px solid var(--zv-border);
}

.compaction-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-xs);
}

.compaction-preview {
  margin-top: 10px;
}

.compaction-preview-title {
  margin-bottom: 5px;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}

.compaction-preview pre {
  max-height: 220px;
  margin: 0;
  padding: 9px 10px;
  overflow: auto;
  color: var(--zv-text-secondary);
  font-family: var(--zv-font-family-code, monospace);
  font-size: var(--zv-font-size-xs);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--zv-bg-subtle);
  border-radius: var(--zv-radius-sm);
}

.compaction-empty {
  margin-top: 8px;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}
</style>
