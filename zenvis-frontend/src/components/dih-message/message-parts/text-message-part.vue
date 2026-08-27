<template>
  <div v-if="part.type === 'thinking' && !thinkingHidden" class="thinking-part">
    <div class="thinking-header" @click="toggleThinking">
      <div class="thinking-title">
        <el-icon><Loading /></el-icon>
        <span>{{ part.title || '思考过程' }}</span>
        <el-tag size="small" type="info" effect="plain">{{
          thinkingStatusText(part.status)
        }}</el-tag>
      </div>
      <div class="thinking-tools">
        <el-tooltip :content="thinkingExpanded ? '折叠' : '展开'" placement="top">
          <el-button
            class="thinking-icon-btn"
            size="small"
            :icon="thinkingExpanded ? CaretTop : CaretBottom"
            circle
            @click.stop="toggleThinking"
          />
        </el-tooltip>
        <el-tooltip content="关闭思考过程" placement="top">
          <el-button
            class="thinking-icon-btn"
            size="small"
            :icon="Close"
            circle
            @click.stop="thinkingHidden = true"
          />
        </el-tooltip>
      </div>
    </div>
    <div v-if="thinkingExpanded" class="thinking-content">
      {{ part.content }}
    </div>
  </div>

  <div
    v-else-if="part.type === 'markdown'"
    class="message-content markdown-body"
    v-html="parseMarkdown(part.content || '')"
  ></div>

  <div v-else-if="part.type === 'prompt-suggestions'" class="prompt-suggestions-part">
    <div v-if="part.title" class="prompt-suggestions-title">{{ part.title }}</div>
    <div v-if="interactive" class="prompt-suggestion-list">
      <el-button
        v-for="(example, exampleIndex) in promptSuggestionExamples"
        :key="`${partKey(part)}-${exampleIndex}`"
        class="prompt-suggestion-bubble"
        size="small"
        round
        @click="emit('selectPromptSuggestion', example.prompt)"
      >
        {{ example.label }}
      </el-button>
    </div>
    <div v-else class="prompt-suggestion-list">
      <el-tag
        v-for="(example, exampleIndex) in promptSuggestionExamples"
        :key="`${partKey(part)}-readonly-${exampleIndex}`"
        size="small"
        effect="plain"
      >
        {{ example.label }}
      </el-tag>
    </div>
  </div>

  <div v-else-if="part.type === 'code'" class="code-part">
    <div class="code-header">
      <div class="code-title">
        <span class="code-language">{{ part.title || part.language || 'plaintext' }}</span>
      </div>
      <div class="code-tools">
        <el-tooltip content="复制代码" placement="top">
          <el-button
            class="code-copy-btn"
            size="small"
            :icon="CopyDocument"
            circle
            @click="emit('copyCode', part.content || '')"
          />
        </el-tooltip>
        <el-tooltip :content="codeExpanded ? '折叠' : '展开'" placement="top">
          <el-button
            class="code-copy-btn"
            size="small"
            :icon="codeExpanded ? CaretTop : CaretBottom"
            circle
            @click="toggleCode"
          />
        </el-tooltip>
      </div>
    </div>
    <pre v-if="codeExpanded" class="code-content"><code>{{ part.content }}</code></pre>
  </div>

  <div
    v-else
    class="message-content markdown-body"
    v-html="parseMarkdown(part.content || '')"
  ></div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { CaretBottom, CaretTop, Close, CopyDocument, Loading } from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { isTruthyMetadata, partKey, useMarkdownRenderer } from './message-part-context';

type PromptSuggestionExample = {
  label: string;
  prompt: string;
};

const props = defineProps<{
  part: ChatMessagePart;
  interactive?: boolean;
}>();

const emit = defineEmits<{
  (e: 'copyCode', content: string): void;
  (e: 'selectPromptSuggestion', prompt: string): void;
}>();

const { parseMarkdown } = useMarkdownRenderer();
const thinkingHidden = ref(false);
const thinkingExpandedOverride = ref<boolean>();
const codeExpandedOverride = ref<boolean>();

const thinkingExpanded = computed(() => {
  return thinkingExpandedOverride.value ?? props.part.status === 'running';
});

const codeExpanded = computed(() => {
  return codeExpandedOverride.value ?? !isTruthyMetadata(props.part, 'defaultCollapsed');
});

const toggleThinking = () => {
  thinkingExpandedOverride.value = !thinkingExpanded.value;
};

const toggleCode = () => {
  codeExpandedOverride.value = !codeExpanded.value;
};

const thinkingStatusText = (status?: string) => {
  if (status === 'running') return '思考中';
  return '已完成';
};

const promptSuggestionExamples = computed<PromptSuggestionExample[]>(() => {
  const examples = props.part.metadata?.examples;
  if (!Array.isArray(examples)) {
    return [];
  }
  return examples
    .filter(example => example && typeof example === 'object')
    .map(example => {
      const raw = example as Record<string, unknown>;
      const prompt = typeof raw.prompt === 'string' ? raw.prompt : '';
      const label = typeof raw.label === 'string' ? raw.label : prompt;
      return {
        label: label || '示例',
        prompt,
      };
    })
    .filter(example => example.prompt);
});
</script>
