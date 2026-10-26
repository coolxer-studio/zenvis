<template>
  <div class="markdown-content">
    <template v-for="(segment, index) in segments" :key="index">
      <div
        v-if="segment.type === 'markdown'"
        class="markdown-content-segment"
        v-html="renderMarkdown(segment.content)"
      ></div>
      <MermaidDiagram v-else-if="segment.type === 'mermaid'" :source="segment.content" />
      <HtmlPreview v-else :source="segment.content" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import HtmlPreview from './html-preview.vue';
import MermaidDiagram from './mermaid-diagram.vue';
import { splitMarkdownBlocks } from './markdown-blocks';

defineOptions({ name: 'MarkdownContent' });

type Props = {
  content?: string;
  renderMarkdown: (content: string) => string;
};

const props = withDefaults(defineProps<Props>(), {
  content: '',
});

const segments = computed(() => splitMarkdownBlocks(props.content));
</script>

<style scoped>
.markdown-content {
  max-width: 100%;
  min-width: 0;
}

.markdown-content-segment {
  max-width: 100%;
  min-width: 0;
}

.markdown-content :deep(.markdown-content-segment > :first-child) {
  margin-top: 0;
}

.markdown-content :deep(.markdown-content-segment > :last-child) {
  margin-bottom: 0;
}
</style>
