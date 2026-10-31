import assert from 'node:assert/strict';
import test from 'node:test';
import {
  findAgentRunMessageIndex,
  isAgentRunLive,
  isAgentRunRestorable,
  isAgentRunSubscribable,
  resolveAgentRunPendingApprovalRequestId,
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
  assert.equal(messages[0].parts[1].id, 'server-markdown');
  assert.equal(messages[0].parts[1].content, '最终答复');
});

test('成功运行卡迁移到最终图表消息且不改写查询方案确认消息', () => {
  const rawFinalAnswer = [
    '已按批准的查询方案执行 entity_distribution。',
    '```zenvis:visualization-chart-preview',
    '{"title":"风险等级分布"}',
    '```',
  ].join('\n');
  const confirmation = {
    id: 'confirm-1',
    type: 'confirm',
    content: '请确认查询方案',
    status: 'approved',
  };
  const chart = {
    id: 'chart-1',
    type: 'visualization-chart-preview',
    content: '风险等级分布',
    metadata: { planId: 'plan-1' },
  };
  const messages: any[] = [
    { sender: 'user', content: '用饼图展示风险等级', runId: 'run-1' },
    {
      sender: 'ai',
      content: '查询方案如下',
      runId: 'run-1',
      parts: [confirmation],
    },
    { sender: 'user', content: '已确认', runId: 'run-1' },
    {
      sender: 'ai',
      content: '已按批准的查询方案执行。',
      runId: 'run-1',
      parts: [
        { id: 'final-markdown-1', type: 'markdown', content: '真实图表如下' },
        chart,
      ],
    },
  ];

  const index = upsertAgentRunMessage(messages, run({ finalAnswer: rawFinalAnswer }) as any);

  assert.equal(index, 3);
  assert.equal(messages[1].content, '查询方案如下');
  assert.deepEqual(messages[1].parts, [confirmation]);
  assert.equal(messages[3].parts[0].type, 'agent-run');
  assert.equal(messages[3].parts[1].id, 'final-markdown-1');
  assert.equal(messages[3].parts[2], chart);
  assert.equal(messages[3].content, '已按批准的查询方案执行。');
  assert.equal(messages[3].parts.some((part: any) => part.id === 'agent-run-final-run-1'), false);
  assert.equal(messages[3].parts.some((part: any) =>
    part.type === 'markdown' && String(part.content).includes('```zenvis:')), false);
});

test('交互确认消息之后没有服务端最终消息时创建独立最终答复', () => {
  const confirmation = {
    id: 'confirm-1',
    type: 'confirm',
    content: '请确认查询方案',
    status: 'approved',
  };
  const messages: any[] = [{
    sender: 'ai',
    content: '查询方案如下',
    runId: 'run-1',
    parts: [confirmation],
  }];

  const index = upsertAgentRunMessage(messages, run({ finalAnswer: '最终图表答复' }) as any);

  assert.equal(index, 1);
  assert.equal(messages.length, 2);
  assert.equal(messages[0].content, '查询方案如下');
  assert.deepEqual(messages[0].parts, [confirmation]);
  assert.equal(messages[1].runId, 'run-1');
  assert.equal(messages[1].content, '最终图表答复');
  assert.equal(messages[1].parts[0].type, 'agent-run');
  assert.equal(messages[1].parts[1].type, 'markdown');
  assert.equal(messages[1].parts[1].content, '最终图表答复');
});

test('最新消息仍是交互确认时不把更早的同 Run 文本当作最终结果', () => {
  const messages: any[] = [
    {
      sender: 'ai',
      content: '更早的说明文本',
      runId: 'run-1',
      parts: [{ id: 'old-markdown', type: 'markdown', content: '更早的说明文本' }],
    },
    {
      sender: 'ai',
      content: '请确认最新查询方案',
      runId: 'run-1',
      parts: [{ id: 'confirm-latest', type: 'confirm', content: '请确认最新查询方案' }],
    },
  ];

  const index = upsertAgentRunMessage(messages, run({ finalAnswer: '真正最终答复' }) as any);

  assert.equal(index, 2);
  assert.equal(messages[1].content, '请确认最新查询方案');
  assert.deepEqual(messages[1].parts, [{
    id: 'confirm-latest',
    type: 'confirm',
    content: '请确认最新查询方案',
  }]);
  assert.equal(messages[2].content, '真正最终答复');
  assert.equal(messages[2].parts[0].type, 'agent-run');
});

test('服务端最终消息存在时清理旧版合成最终答复和重复运行卡', () => {
  const messages: any[] = [
    {
      sender: 'ai',
      content: '查询方案如下',
      runId: 'run-1',
      parts: [{
        id: 'confirm-1',
        type: 'confirm',
        content: '请确认查询方案',
      }],
    },
    {
      sender: 'ai',
      content: '服务端最终答复',
      runId: 'run-1',
      parts: [
        { id: 'server-markdown-1', type: 'markdown', content: '服务端最终答复' },
        { id: 'chart-1', type: 'visualization-chart-preview', content: '图表' },
      ],
    },
    {
      sender: 'ai',
      content: '旧版原始答复',
      runId: 'run-1',
      parts: [
        { id: 'agent-run-run-1', type: 'agent-run', metadata: { run: run() } },
        { id: 'agent-run-final-run-1', type: 'markdown', content: '旧版原始答复' },
      ],
    },
  ];

  const index = upsertAgentRunMessage(messages, run({ finalAnswer: '旧版原始答复' }) as any);
  upsertAgentRunMessage(messages, run({ finalAnswer: '旧版原始答复' }) as any);

  assert.equal(index, 1);
  assert.equal(messages.length, 2);
  assert.equal(messages[0].parts.length, 1);
  assert.equal(messages[0].parts[0].type, 'confirm');
  assert.equal(messages[1].parts[0].type, 'agent-run');
  assert.equal(messages[1].parts[1].id, 'server-markdown-1');
  assert.equal(messages[1].parts[2].type, 'visualization-chart-preview');
  assert.equal(messages.flatMap(message => message.parts || [])
    .filter((part: any) => part.type === 'agent-run').length, 1);
  assert.equal(messages.flatMap(message => message.parts || [])
    .some((part: any) => part.id === 'agent-run-final-run-1'), false);
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

test('等待用户时以 pendingInteraction 作为唯一交互提示来源', () => {
  const waitingRun = run({
    status: 'WAITING_USER',
    pausedReason: '旧原因',
    pendingAction: JSON.stringify({ type: 'user', question: '旧问题' }),
    pendingInteraction: {
      interactionId: 'interaction-1',
      kind: 'user_input',
      title: '需要补充信息',
      prompt: '请补充新的 Topic 与消费组',
      allowedActions: [],
    },
  });

  assert.equal(resolveAgentRunWaitingQuestion(waitingRun as any), '请补充新的 Topic 与消费组');
});

test('等待审批时从 pendingAction 恢复审批请求以便重连后展示审批卡', () => {
  const waitingRun = run({
    status: 'WAITING_APPROVAL',
    pendingAction: JSON.stringify({
      type: 'approval',
      requestId: 'approval-request-1',
    }),
  });

  assert.equal(resolveAgentRunPendingApprovalRequestId(waitingRun as any), 'approval-request-1');
  assert.equal(resolveAgentRunPendingApprovalRequestId(run({
    status: 'RUNNING',
    pendingAction: JSON.stringify({ type: 'approval', requestId: 'approval-request-1' }),
  }) as any), '');
  assert.equal(resolveAgentRunPendingApprovalRequestId(run({
    status: 'WAITING_APPROVAL',
    pendingAction: 'not-json',
  }) as any), '');
});

test('等待审批时优先使用 pendingInteraction.requestId', () => {
  const waitingRun = run({
    status: 'WAITING_APPROVAL',
    pendingAction: JSON.stringify({ type: 'approval', requestId: 'legacy-request' }),
    pendingInteraction: {
      interactionId: 'approval-interaction-1',
      kind: 'mcp_approval',
      requestId: 'current-request',
      title: 'MCP 工具审批',
      prompt: '需要许可',
      allowedActions: [],
    },
  });

  assert.equal(resolveAgentRunPendingApprovalRequestId(waitingRun as any), 'current-request');
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
