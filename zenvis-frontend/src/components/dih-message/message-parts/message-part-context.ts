import { computed, inject, ref } from 'vue';
import type { ComputedRef, InjectionKey, Ref } from 'vue';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import type { ChatMessagePart } from '@/types/type-dih';

marked.setOptions({
  gfm: true,
  breaks: true,
});

export type MarkdownRenderer = {
  parseMarkdown: (content: string) => string;
  clearMarkdownCache: () => void;
};

export const markdownRendererKey: InjectionKey<MarkdownRenderer> = Symbol('dih-markdown-renderer');

export const createMarkdownRenderer = (): MarkdownRenderer => {
  const markdownCache = new Map<string, string>();
  const parseMarkdown = (content: string) => {
    const cached = markdownCache.get(content);
    if (cached !== undefined) return cached;
    const sanitized = DOMPurify.sanitize(marked.parse(content) as string);
    if (markdownCache.size >= 50) markdownCache.clear();
    markdownCache.set(content, sanitized);
    return sanitized;
  };
  return {
    parseMarkdown,
    clearMarkdownCache: () => markdownCache.clear(),
  };
};

export const useMarkdownRenderer = () => {
  const renderer = inject(markdownRendererKey);
  if (!renderer) {
    throw new Error('DIH message part must be rendered inside ChatMessageRenderer');
  }
  return renderer;
};

export const partKey = (part: ChatMessagePart) => {
  return part.id || `${part.type}-${part.content || ''}`;
};

export const isTruthyMetadata = (part: ChatMessagePart, key: string) => {
  const value = part.metadata?.[key];
  return value === true || value === 'true';
};

export const metadataText = (part: ChatMessagePart, key: string) => {
  const value = part.metadata?.[key];
  return typeof value === 'string' ? value : '';
};

export const metadataJsonText = (part: ChatMessagePart, key: string) => {
  const value = part.metadata?.[key];
  if (typeof value === 'string') {
    return value;
  }
  if (value && typeof value === 'object') {
    return JSON.stringify(value, null, 2);
  }
  return '';
};

export const useDefaultExpanded = (
  part: Ref<ChatMessagePart>,
): {
  isExpanded: ComputedRef<boolean>;
  toggleExpanded: () => void;
  setExpanded: (value: boolean) => void;
} => {
  const expanded = ref<boolean>();
  const isExpanded = computed(() => {
    return expanded.value ?? !isTruthyMetadata(part.value, 'defaultCollapsed');
  });
  const toggleExpanded = () => {
    expanded.value = !isExpanded.value;
  };
  const setExpanded = (value: boolean) => {
    expanded.value = value;
  };
  return { isExpanded, toggleExpanded, setExpanded };
};
