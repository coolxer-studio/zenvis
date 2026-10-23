import assert from 'node:assert/strict';
import test from 'node:test';
import {
  buildAgentRunActivities,
  isAgentRunActivityEvent,
  summarizeAgentRunActivities,
} from './agent-run-activity.ts';

const event = (
  sequence: number,
  type: string,
  data: Record<string, unknown>,
  stepId = 'step-1',
) => ({
  sequence,
  run_id: 'run-1',
  type,
  step_id: stepId,
  created_at: `2026-10-23T10:00:0${sequence}Z`,
  data,
});

test('归并 RAG 与工具生命周期并忽略重复 sequence', () => {
  const events = [
    event(1, 'rag.search', {
      searchId: 'rag-1',
      query: '接入链路',
      status: 'succeeded',
      hitCount: 2,
      results: [{ fileName: 'a.md' }, { fileName: 'b.md' }],
    }),
    event(2, 'tool.started', {
      callId: 'call-1',
      toolName: 'config_tree',
      status: 'started',
      arguments: '{"type":"meta"}',
    }),
    event(2, 'tool.started', {
      callId: 'call-1',
      toolName: 'config_tree',
      status: 'started',
    }),
    event(3, 'tool.succeeded', {
      callId: 'call-1',
      toolName: 'config_tree',
      status: 'succeeded',
      result: '{"ok":true}',
      durationMillis: 79,
    }),
    event(4, 'assistant.delta', { content: '忽略' }),
  ] as any[];

  const activities = buildAgentRunActivities(events);

  assert.equal(activities.length, 2);
  assert.equal(activities[0].kind, 'rag');
  assert.equal(activities[0].status, 'succeeded');
  assert.equal(activities[1].kind, 'tool');
  assert.equal(activities[1].status, 'succeeded');
  assert.equal(activities[1].data.arguments, '{"type":"meta"}');
  assert.equal(activities[1].data.result, '{"ok":true}');
  assert.equal(activities[1].data.durationMillis, 79);
  assert.equal(activities[1].eventTypes.join(','), 'tool.started,tool.succeeded');
  assert.equal(isAgentRunActivityEvent(events[4]), false);
});

test('工具失败与压缩事件可汇总为任务卡指标', () => {
  const activities = buildAgentRunActivities([
    event(1, 'rag.search', { searchId: 'rag-1', status: 'empty', hitCount: 0 }),
    event(2, 'rag.search', { searchId: 'rag-2', status: 'succeeded', hitCount: 3 }),
    event(3, 'tool.started', { callId: 'call-1', toolName: 'query_data' }),
    event(4, 'tool.failed', { callId: 'call-1', toolName: 'query_data', error: 'timeout' }),
    event(5, 'context.compacted', {
      compactionId: 'compact-1',
      stage: 'history-window',
      reason: 'history_window_summary',
      savedTokens: 6200,
      summaryPreview: '较早对话摘要',
    }),
  ] as any[]);

  const summary = summarizeAgentRunActivities(activities);

  assert.equal(summary.ragCount, 2);
  assert.equal(summary.ragHits, 3);
  assert.equal(summary.toolCount, 1);
  assert.equal(summary.toolFailures, 1);
  assert.equal(summary.compactionCount, 1);
  assert.equal(summary.savedTokens, 6200);
  assert.equal(activities[2].data.error, 'timeout');
  assert.equal(activities[2].status, 'failed');
  assert.equal(activities[3].kind, 'compaction');
});

test('混合事件按首次发生顺序排列并合并同一压缩记录', () => {
  const activities = buildAgentRunActivities([
    event(3, 'tool.started', { callId: 'call-1', toolName: 'query_data' }, 'step-2'),
    event(1, 'context.compacted', {
      compactionId: 'compact-1',
      stage: 'model-input',
      reason: 'prompt_token_budget',
      savedTokens: 100,
    }),
    event(2, 'rag.search', { searchId: 'rag-1', hitCount: 1 }),
    event(4, 'context.compacted', {
      compactionId: 'compact-1',
      stage: 'model-input',
      reason: 'prompt_token_budget',
      savedTokens: 120,
    }),
  ] as any[]);

  assert.deepEqual(activities.map(item => item.kind), ['compaction', 'rag', 'tool']);
  assert.equal(activities[0].data.savedTokens, 120);
  assert.equal(activities[2].stepId, 'step-2');
});

test('审批记录按 requestId 归并，工具不可用时保留失败状态', () => {
  const activities = buildAgentRunActivities([
    event(1, 'tool.approval_required', {
      requestId: 'approval-1',
      toolName: 'write_config',
      status: 'pending',
    }),
    event(2, 'tool.approval_unavailable', {
      requestId: 'approval-1',
      toolName: 'write_config',
    }),
  ] as any[]);

  assert.equal(activities.length, 1);
  assert.equal(activities[0].kind, 'tool');
  assert.equal(activities[0].status, 'failed');
  assert.deepEqual(activities[0].eventTypes, [
    'tool.approval_required',
    'tool.approval_unavailable',
  ]);
});
