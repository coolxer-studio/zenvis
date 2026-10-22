import assert from 'node:assert/strict';
import test from 'node:test';
import {
  isRagSearchPart,
  normalizeRagSearchStatus,
  ragSearchPartFromEvent,
  upsertRagSearchPart,
} from './rag-search-parts.ts';
import { isStructuredStreamPart, mergeLiveStructuredParts } from './tool-call-parts.ts';

const successEvent = {
  searchId: 'rag-1',
  query: '接入链路',
  mode: 'ask',
  scopeType: 'all',
  knowledgeBaseIds: [1, 2],
  topK: 6,
  status: 'succeeded',
  reason: 'ok',
  hitCount: 1,
  durationMillis: 43,
  results: [{
    documentId: 'doc-1',
    knowledgeBaseId: 1,
    knowledgeBaseName: '产品文档',
    fileName: 'quick-start.md',
    text: '接入链路说明',
  }],
};

test('RAG 检索事件转换为独立消息部件', () => {
  const part = ragSearchPartFromEvent(successEvent);

  assert.ok(part);
  assert.equal(part.type, 'rag-search');
  assert.equal(part.id, 'rag-1');
  assert.equal(part.status, 'succeeded');
  assert.equal(part.metadata?.hitCount, 1);
  assert.equal(part.metadata?.query, '接入链路');
  assert.equal((part.metadata?.results as unknown[]).length, 1);
  assert.match(part.content || '', /1 个来源/);
  assert.equal(isRagSearchPart(part), true);
  assert.equal(isStructuredStreamPart(part), true);
});

test('同一 searchId 的事件只更新一个 RAG 部件', () => {
  const started = upsertRagSearchPart([], {
    ...successEvent,
    status: 'skipped',
    reason: 'embedding_disabled',
    hitCount: 0,
    results: [],
  });
  const completed = upsertRagSearchPart(started, successEvent);

  assert.equal(completed.length, 1);
  assert.equal(completed[0].status, 'succeeded');
  assert.equal(completed[0].metadata?.reason, 'ok');
  assert.equal(completed[0].metadata?.durationMillis, 43);
});

test('无匹配、跳过和失败状态保持独立语义', () => {
  assert.equal(normalizeRagSearchStatus('empty'), 'empty');
  assert.equal(normalizeRagSearchStatus('no_match'), 'empty');
  assert.equal(normalizeRagSearchStatus('skipped'), 'skipped');
  assert.equal(normalizeRagSearchStatus('retrieval_error'), 'failed');

  const failed = ragSearchPartFromEvent({
    ...successEvent,
    status: 'failed',
    reason: 'retrieval_error',
    error: 'redis unavailable',
    results: [],
    hitCount: 0,
  });
  assert.equal(failed?.status, 'failed');
  assert.equal(failed?.metadata?.error, 'redis unavailable');
  assert.match(failed?.content || '', /失败/);
});

test('最终消息缺少 RAG 部件时保留流式检索记录并合并正文', () => {
  const live = [
    { id: 'rag-1', type: 'rag-search', status: 'succeeded', metadata: { hitCount: 1 } },
    { id: 'm1', type: 'markdown', content: '最终回答' },
  ];
  const final = [{ id: 'm2', type: 'markdown', content: '最终回答' }];

  const merged = mergeLiveStructuredParts(live, final);

  assert.ok(merged);
  assert.equal(merged.length, 2);
  assert.equal(merged[0].type, 'rag-search');
  assert.equal(merged[1].type, 'markdown');
});

test('最终消息已有 RAG 部件时按 searchId 合并元数据', () => {
  const live = [{
    id: 'rag-1',
    type: 'rag-search',
    status: 'succeeded',
    metadata: { searchId: 'rag-1', reason: 'ok' },
  }];
  const final = [{
    id: 'rag-1',
    type: 'rag-search',
    status: 'succeeded',
    metadata: { searchId: 'rag-1', hitCount: 2 },
  }, { id: 'm2', type: 'markdown', content: '最终回答' }];

  const merged = mergeLiveStructuredParts(live, final);

  assert.ok(merged);
  assert.equal(merged.length, 2);
  assert.equal(merged[0].metadata?.reason, 'ok');
  assert.equal(merged[0].metadata?.hitCount, 2);
});
