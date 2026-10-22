<template>
  <section
    class="rag-search-part"
    :class="`is-${status}`"
    data-testid="rag-search-card"
  >
    <button
      type="button"
      class="rag-search-header"
      :aria-expanded="expanded"
      :aria-controls="detailsId"
      @click="expanded = !expanded"
    >
      <span class="rag-search-icon" aria-hidden="true">
        <el-icon><Search /></el-icon>
      </span>
      <span class="rag-search-title">
        <strong>知识库检索</strong>
        <span v-if="queryText" class="rag-search-query">{{ queryText }}</span>
        <span v-else class="rag-search-query">{{ reasonLabel }}</span>
      </span>
      <span class="rag-search-meta">
        <span>{{ statusLabel }}</span>
        <span aria-hidden="true">·</span>
        <span>{{ hitLabel }}</span>
        <template v-if="durationLabel">
          <span aria-hidden="true">·</span>
          <span>{{ durationLabel }}</span>
        </template>
      </span>
      <el-icon class="rag-search-chevron" aria-hidden="true">
        <CaretRight v-if="!expanded" />
        <CaretBottom v-else />
      </el-icon>
    </button>

    <div v-if="expanded" :id="detailsId" class="rag-search-body">
      <div class="rag-search-overview">
        <div class="rag-search-field rag-search-query-field">
          <div class="rag-search-field-header">
            <span>查询</span>
            <el-tooltip content="复制查询" placement="top">
              <button
                type="button"
                class="rag-search-copy"
                :disabled="!queryText"
                aria-label="复制检索查询"
                @click="copyQuery"
              >
                <el-icon><CopyDocument /></el-icon>
              </button>
            </el-tooltip>
          </div>
          <p>{{ queryText || '未提供查询内容' }}</p>
        </div>
        <div class="rag-search-facts">
          <div class="rag-search-fact">
            <span>范围</span>
            <strong>{{ scopeLabel }}</strong>
          </div>
          <div class="rag-search-fact">
            <span>状态</span>
            <strong>{{ statusLabel }}</strong>
          </div>
          <div class="rag-search-fact">
            <span>耗时</span>
            <strong>{{ durationLabel || '—' }}</strong>
          </div>
          <div class="rag-search-fact">
            <span>Top K</span>
            <strong>{{ topK || '—' }}</strong>
          </div>
        </div>
      </div>

      <div v-if="results.length" class="rag-search-results">
        <div class="rag-search-section-header">
          <span>命中来源</span>
          <span class="rag-search-count">{{ results.length }}</span>
          <el-tooltip content="复制全部命中来源" placement="top">
            <button
              type="button"
              class="rag-search-copy"
              aria-label="复制全部命中来源"
              @click="copyResults"
            >
              <el-icon><CopyDocument /></el-icon>
            </button>
          </el-tooltip>
        </div>
        <article
          v-for="(result, index) in results"
          :key="result.documentId || result.source || index"
          class="rag-search-result"
        >
          <div class="rag-search-result-title">
            <el-icon aria-hidden="true"><Document /></el-icon>
            <strong>{{ resultTitle(result, index) }}</strong>
            <span v-if="result.knowledgeBaseName || result.knowledgeBaseId">
              {{ result.knowledgeBaseName || `知识库 ${result.knowledgeBaseId}` }}
            </span>
          </div>
          <div v-if="resultMeta(result)" class="rag-search-result-meta">
            {{ resultMeta(result) }}
          </div>
          <pre v-if="result.text" class="rag-search-result-text">{{ result.text }}</pre>
        </article>
      </div>

      <div v-else class="rag-search-empty">
        <el-icon aria-hidden="true"><InfoFilled /></el-icon>
        <span>{{ emptyMessage }}</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  CaretBottom,
  CaretRight,
  CopyDocument,
  Document,
  InfoFilled,
  Search,
} from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { copyTextToClipboard } from '@/utils/clipboard';
import {
  normalizeRagSearchStatus,
  type RagSearchResult,
} from '../rag-search-parts';

defineOptions({ name: 'RagSearchPart' });

const props = defineProps<{
  part: ChatMessagePart;
}>();

const expanded = ref(false);
const detailsId = computed(() => `rag-search-details-${props.part.id || 'unknown'}`);
const status = computed(() => normalizeRagSearchStatus(props.part.status || props.part.metadata?.status));
const queryText = computed(() => String(props.part.metadata?.query || '').trim());
const results = computed<RagSearchResult[]>(() => {
  const value = props.part.metadata?.results;
  return Array.isArray(value)
    ? value.filter(item => item && typeof item === 'object') as RagSearchResult[]
    : [];
});
const knowledgeBaseIds = computed<number[]>(() => {
  const value = props.part.metadata?.knowledgeBaseIds;
  return Array.isArray(value)
    ? value.map(item => Number(item)).filter(item => Number.isFinite(item))
    : [];
});
const scopeType = computed(() => String(props.part.metadata?.scopeType || ''));
const reason = computed(() => String(props.part.metadata?.reason || ''));
const topK = computed(() => Number(props.part.metadata?.topK) || 0);
const errorText = computed(() => String(props.part.metadata?.error || '').trim());

const statusLabel = computed(() => {
  if (status.value === 'succeeded') return '已完成';
  if (status.value === 'empty') return '无匹配';
  if (status.value === 'failed') return '失败';
  return '已跳过';
});

const reasonLabel = computed(() => {
  const labels: Record<string, string> = {
    ok: '检索完成',
    no_match: '未命中相关知识',
    empty_query: '查询内容为空',
    no_knowledge_base: '未找到可用知识库',
    no_bound_knowledge_base: '当前 Skill 未绑定知识库',
    embedding_disabled: '知识库检索未启用',
    retrieval_error: '检索服务异常',
    knowledge_base_lookup_error: '知识库信息读取失败',
  };
  return labels[reason.value] || '未执行检索';
});

const hitLabel = computed(() => `${results.value.length} 个来源`);

const durationLabel = computed(() => {
  const duration = Number(props.part.metadata?.durationMillis);
  if (!Number.isFinite(duration) || duration < 0) return '';
  if (duration < 1000) return `${Math.round(duration)}ms`;
  return `${(duration / 1000).toFixed(duration < 10_000 ? 1 : 0)}s`;
});

const scopeLabel = computed(() => {
  if (scopeType.value === 'bound') {
    return knowledgeBaseIds.value.length
      ? `绑定知识库 · ${knowledgeBaseIds.value.length} 个`
      : '绑定知识库';
  }
  return knowledgeBaseIds.value.length
    ? `全部知识库 · ${knowledgeBaseIds.value.length} 个`
    : '全部知识库';
});

const emptyMessage = computed(() => {
  if (status.value === 'failed') {
    return errorText.value || `${statusLabel.value}，${reasonLabel.value}。`;
  }
  return `${statusLabel.value}，${reasonLabel.value}。`;
});

const resultTitle = (result: RagSearchResult, index: number) =>
  result.fileName || result.datasetName || result.knowledgeBaseName
    || result.source || `来源 ${index + 1}`;

const resultMeta = (result: RagSearchResult) => [
  result.datasetName,
  result.fileName,
  result.source,
].filter(Boolean).join(' · ');

const copyQuery = async () => {
  if (!queryText.value) return;
  const copied = await copyTextToClipboard(queryText.value);
  if (copied) {
    ElMessage.success('检索查询已复制');
  } else {
    ElMessage.warning('复制失败，请手动选择内容');
  }
};

const copyResults = async () => {
  const content = results.value.map((result, index) => {
    const heading = resultTitle(result, index);
    const metadata = resultMeta(result);
    return [heading, metadata, result.text || ''].filter(Boolean).join('\n');
  }).join('\n\n');
  const copied = await copyTextToClipboard(content);
  if (copied) {
    ElMessage.success('命中来源已复制');
  } else {
    ElMessage.warning('复制失败，请手动选择内容');
  }
};
</script>
