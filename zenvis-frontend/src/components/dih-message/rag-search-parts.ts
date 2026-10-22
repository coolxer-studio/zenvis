import type { ChatMessagePart } from '@/types/type-dih';

export type RagSearchStatus = 'succeeded' | 'empty' | 'skipped' | 'failed';

export type RagSearchData = {
  searchId: string;
  query?: string;
  mode?: string;
  scopeType?: 'all' | 'bound' | string;
  knowledgeBaseIds?: number[];
  topK?: number;
  status?: RagSearchStatus | string;
  reason?: string;
  hitCount?: number;
  durationMillis?: number;
  startedAt?: string;
  completedAt?: string;
  results?: RagSearchResult[];
  error?: string;
};

export type RagSearchResult = {
  documentId?: string;
  knowledgeBaseId?: number;
  knowledgeBaseName?: string;
  datasetName?: string;
  fileName?: string;
  source?: string;
  text?: string;
};

const RAG_SEARCH_TYPE = 'rag-search';

const textOf = (value: unknown): string => {
  if (value === undefined || value === null) return '';
  return String(value);
};

export const normalizeRagSearchStatus = (status: unknown): RagSearchStatus => {
  const value = textOf(status).toLowerCase();
  if (value === 'succeeded' || value === 'success' || value === 'ok') return 'succeeded';
  if (value === 'empty' || value === 'no_match') return 'empty';
  if (value === 'failed' || value === 'error' || value === 'retrieval_error') return 'failed';
  return 'skipped';
};

export const isRagSearchPart = (part?: ChatMessagePart | null): boolean =>
  part?.type === RAG_SEARCH_TYPE;

const resultList = (value: unknown): RagSearchResult[] => Array.isArray(value)
  ? value.filter(item => item && typeof item === 'object') as RagSearchResult[]
  : [];

export const ragSearchPartFromEvent = (
  raw: unknown,
  existing?: ChatMessagePart,
): ChatMessagePart | null => {
  if (!raw || typeof raw !== 'object') return null;
  const source = raw as Record<string, unknown>;
  const searchId = textOf(source.searchId ?? source.search_id) || existing?.id;
  if (!searchId) return null;
  const status = normalizeRagSearchStatus(source.status);
  const hitCountValue = Number(source.hitCount ?? source.hit_count ?? 0);
  const hitCount = Number.isFinite(hitCountValue) && hitCountValue >= 0 ? hitCountValue : 0;
  const summary = status === 'succeeded'
    ? `知识库检索 · ${hitCount} 个来源`
    : status === 'empty'
      ? '知识库检索 · 无匹配'
      : status === 'failed'
        ? '知识库检索 · 失败'
        : '知识库检索 · 已跳过';

  return {
    id: searchId,
    type: RAG_SEARCH_TYPE,
    title: '知识库检索',
    content: summary,
    status,
    metadata: {
      ...existing?.metadata,
      searchId,
      query: textOf(source.query),
      mode: textOf(source.mode),
      scopeType: textOf(source.scopeType ?? source.scope_type),
      knowledgeBaseIds: Array.isArray(source.knowledgeBaseIds)
        ? source.knowledgeBaseIds
        : source.knowledge_base_ids,
      topK: source.topK ?? source.top_k,
      status,
      reason: textOf(source.reason),
      hitCount,
      durationMillis: source.durationMillis ?? source.duration_millis,
      startedAt: source.startedAt ?? source.started_at,
      completedAt: source.completedAt ?? source.completed_at,
      results: resultList(source.results),
      error: textOf(source.error),
    },
  };
};

export const upsertRagSearchPart = (
  parts: ChatMessagePart[] | undefined,
  rawEvent: unknown,
): ChatMessagePart[] => {
  const nextParts = parts ? [...parts] : [];
  const raw = rawEvent as Record<string, unknown> | undefined;
  const searchId = textOf(raw?.searchId ?? raw?.search_id);
  const existingIndex = nextParts.findIndex(
    part => Boolean(searchId) && isRagSearchPart(part) && part.id === searchId,
  );
  const nextPart = ragSearchPartFromEvent(
    rawEvent,
    existingIndex >= 0 ? nextParts[existingIndex] : undefined,
  );
  if (!nextPart) return nextParts;
  if (existingIndex >= 0) {
    nextParts[existingIndex] = nextPart;
  } else {
    nextParts.push(nextPart);
  }
  return nextParts;
};
