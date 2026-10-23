import assert from 'node:assert/strict';
import test from 'node:test';
import {
  findAgentRunMessageIndex,
  isAgentRunLive,
  isAgentRunRestorable,
  isAgentRunSubscribable,
  resolveAgentRunStepStatus,
  resolveAgentRunWaitingQuestion,
  upsertAgentRunMessage,
} from './agent-run-message.ts';

const run = (overrides: Record<string, unknown> = {}) => ({
  runId: 'run-1',
  chatId: 'chat-1',
  chatType: 'agent_report',
  agentType: 'agent_report',
  status: 'SUCCEEDED',
  decisionSteps: 1,
  toolCalls: 1,
  effectiveMillis: 1000,
  maxSteps: 30,
  maxDurationSeconds: 1800,
  maxToolCalls: 80,
  lastEventSequence: 8,
  attemptCount: 1,
  recoveryCount: 0,
  truncationCount: 0,
  finalAnswer: '最终答复',
  ...overrides,
});

test('历史成功任务恢复运行卡时同时恢复最终答复', () => {
  const messages: any[] = [];

  const index = upsertAgentRunMessage(messages, run() as any, { time: '2026-10-22 10:00:00' });

  assert.equal(index, 0);
  assert.equal(messages[0].content, '最终答复');
  assert.equal(messages[0].parts[0].type, 'agent-run');
  assert.equal(messages[0].parts[1].type, 'markdown');
  assert.equal(messages[0].parts[1].content, '最终答复');
});

test('已存在运行卡时补齐最终答复且重复恢复不产生重复正文', () => {
  const messages: any[] = [{
    sender: 'ai',
    content: '',
    runId: 'run-1',
    parts: [{ id: 'agent-run-run-1', type: 'agent-run', metadata: {} }],
  }];

  upsertAgentRunMessage(messages, run() as any);
  upsertAgentRunMessage(messages, run({ finalAnswer: '更新后的最终答复' }) as any);

  assert.equal(messages[0].parts.length, 2);
  assert.equal(messages[0].parts[1].id, 'agent-run-final-run-1');
  assert.equal(messages[0].parts[1].content, '更新后的最终答复');
  assert.equal(messages[0].content, '更新后的最终答复');
});

test('服务端已保存的最终答复消息不会被恢复出第二份正文', () => {
  const messages: any[] = [{
    sender: 'ai',
    content: '最终答复',
    runId: 'run-1',
    parts: [{ id: 'server-markdown', type: 'markdown', content: '最终答复' }],
  }];

  upsertAgentRunMessage(messages, run() as any);

  assert.equal(messages[0].parts.length, 2);
  assert.equal(messages[0].parts[0].type, 'agent-run');
  assert.equal(messages[0].parts[1].id, 'agent-run-final-run-1');
  assert.equal(messages[0].parts[1].content, '最终答复');
});

test('会话恢复后优先定位 AI 运行消息而不是同 runId 的用户消息', () => {
  const messages: any[] = [
    { sender: 'user', content: '生成报表', runId: 'run-1' },
    { sender: 'ai', content: '最终答复', runId: 'run-1', parts: [] },
  ];

  assert.equal(findAgentRunMessageIndex(messages, 'run-1'), 1);

  upsertAgentRunMessage(messages, run() as any);

  assert.equal(messages[0].parts, undefined);
  assert.equal(messages[1].parts[0].type, 'agent-run');
});

test('多轮会话按 runId 为每一轮恢复独立任务卡', () => {
  const messages: any[] = [
    { sender: 'user', content: '第一轮', runId: 'run-1' },
    { sender: 'ai', content: '第一轮答复', runId: 'run-1', parts: [] },
    { sender: 'user', content: '第二轮', runId: 'run-2' },
    { sender: 'ai', content: '第二轮答复', runId: 'run-2', parts: [] },
  ];

  upsertAgentRunMessage(messages, run() as any);
  upsertAgentRunMessage(messages, run({
    runId: 'run-2',
    finalAnswer: '第二轮答复',
  }) as any);

  assert.equal(messages[0].parts, undefined);
  assert.equal(messages[1].parts[0].id, 'agent-run-run-1');
  assert.equal(messages[1].parts[0].metadata.run.finalAnswer, '最终答复');
  assert.equal(messages[2].parts, undefined);
  assert.equal(messages[3].parts[0].id, 'agent-run-run-2');
  assert.equal(messages[3].parts[0].metadata.run.finalAnswer, '第二轮答复');
});

test('只有用户消息时不把任务卡挂到用户气泡上', () => {
  const messages: any[] = [
    { sender: 'user', content: '新任务', runId: 'run-1' },
  ];

  const index = upsertAgentRunMessage(messages, run() as any);

  assert.equal(index, 1);
  assert.equal(messages[0].parts, undefined);
  assert.equal(messages[1].sender, 'ai');
  assert.equal(messages[1].parts[0].type, 'agent-run');
});

test('失败任务恢复时展示错误信息而不是伪造最终答复', () => {
  const messages: any[] = [];

  upsertAgentRunMessage(messages, run({
    status: 'FAILED',
    finalAnswer: undefined,
    lastError: '模型调用失败',
  }) as any);

  assert.equal(messages[0].isError, true);
  assert.equal(messages[0].content, '模型调用失败');
  assert.equal(messages[0].parts.some((part: any) => part.type === 'markdown'), false);
});

test('暂停任务不显示为运行中，但重新打开时仍可恢复运行卡', () => {
  assert.equal(isAgentRunLive('PAUSED'), false);
  assert.equal(isAgentRunSubscribable('PAUSED'), false);
  assert.equal(isAgentRunRestorable('PAUSED'), true);
  assert.equal(isAgentRunLive('RUNNING'), true);
  assert.equal(isAgentRunSubscribable('WAITING_APPROVAL'), true);
});

test('运行暂停后当前步骤显示为已暂停而不是进行中', () => {
  assert.equal(resolveAgentRunStepStatus('PAUSED', 'RUNNING'), 'PAUSED');
  assert.equal(resolveAgentRunStepStatus('RUNNING', 'RUNNING'), 'RUNNING');
  assert.equal(resolveAgentRunStepStatus('PAUSED', 'DONE'), 'DONE');
});

test('等待用户时优先从 pendingAction 恢复完整问题而不是截断原因', () => {
  const question = '请补充推送任务配置中的 Source、Topic、消费组和 DLQ Topic 实际值。';
  const waitingRun = run({
    status: 'WAITING_USER',
    pausedReason: '请补充推送任务配置中的 Source、Topic、消费组和 DLQ Topic 实…',
    pendingAction: JSON.stringify({ type: 'user', question }),
  });

  assert.equal(resolveAgentRunWaitingQuestion(waitingRun as any), question);
  assert.equal(resolveAgentRunWaitingQuestion(run({
    status: 'WAITING_USER',
    pausedReason: '旧任务只保留了原因',
    pendingAction: 'not-json',
  }) as any), '旧任务只保留了原因');
  assert.equal(resolveAgentRunWaitingQuestion(run({ status: 'RUNNING' }) as any), '');
});

test('运行卡元数据持续携带当前 Run 的活动事件供执行过程展示', () => {
  const messages: any[] = [];
  const activityEvents = [{
    sequence: 3,
    run_id: 'run-1',
    type: 'context.compacted',
    data: { compactionId: 'compact-1', savedTokens: 120 },
  }];

  upsertAgentRunMessage(messages, run({ status: 'RUNNING' }) as any, {
    activityEvents,
  });
  upsertAgentRunMessage(messages, run({ status: 'RUNNING', decisionSteps: 2 }) as any, {
    activityEvents,
  });

  assert.equal(messages[0].parts.length, 1);
  assert.deepEqual(messages[0].parts[0].metadata.activityEvents, activityEvents);
  assert.equal(messages[0].parts[0].metadata.run.decisionSteps, 2);
});
