import assert from 'node:assert/strict';
import test from 'node:test';
import {
  executePersistedApprovalResponse,
  executePersistedCardResponse,
  isPersistedRunMessage,
  resolvePersistedCardInteraction,
} from './agent-run-interaction.ts';

const run = (overrides: Record<string, unknown> = {}) => ({
  runId: 'run-1',
  chatId: 'chat-1',
  chatType: 'agent_metadata_config',
  agentType: 'agent_metadata_config',
  status: 'WAITING_USER',
  decisionSteps: 1,
  toolCalls: 0,
  effectiveMillis: 1000,
  maxSteps: 30,
  maxDurationSeconds: 1800,
  maxToolCalls: 80,
  lastEventSequence: 4,
  attemptCount: 1,
  recoveryCount: 0,
  truncationCount: 0,
  pendingInteraction: {
    interactionId: 'interaction-1',
    kind: 'card_decision',
    messageId: 'message-1',
    partId: 'part-1',
    workflowId: 'workflow-1',
    stateRevision: 6,
    title: '确认写入',
    prompt: '请确认',
    allowedActions: ['approve', 'reject'],
  },
  ...overrides,
}) as any;

const message = (overrides: Record<string, unknown> = {}) => ({
  id: 'message-1',
  sender: 'ai',
  content: '',
  time: '',
  runId: 'run-1',
  ...overrides,
}) as any;

const part = (overrides: Record<string, unknown> = {}) => ({
  id: 'part-1',
  type: 'confirm',
  status: 'pending',
  metadata: { workflowId: 'workflow-1', stateRevision: 6 },
  ...overrides,
}) as any;

test('持久化卡片动作只能使用当前 Run 的 card_decision 交互', () => {
  const selected = resolvePersistedCardInteraction(run(), message(), part());

  assert.equal(selected.interactionId, 'interaction-1');
  assert.throws(
    () => resolvePersistedCardInteraction(run({ pendingInteraction: null }), message(), part()),
    /已处理或已失效/,
  );
  assert.throws(
    () => resolvePersistedCardInteraction(run(), message(), part({ id: 'other-part' })),
    /已处理或已失效/,
  );
});

test('统一卡片响应携带服务端 revision 且不修改消息集合', async () => {
  const messages: any[] = [{ ...message(), parts: [part()] }];
  const before = JSON.stringify(messages);
  const calls: unknown[] = [];
  const response = await executePersistedCardResponse({
    run: run(),
    message: message(),
    part: part(),
    params: { action: 'approve' },
    respond: async (runId, interactionId, params) => {
      calls.push({ runId, interactionId, params });
      return {
        accepted: true,
        duplicate: false,
        interactionId,
        partStatus: 'approved',
        summary: '',
        run: run({ status: 'QUEUED', pendingInteraction: null }),
      };
    },
  });

  assert.equal(JSON.stringify(messages), before);
  assert.deepEqual(calls, [{
    runId: 'run-1',
    interactionId: 'interaction-1',
    params: { action: 'approve', stateRevision: 6 },
  }]);
  assert.equal(response.response.partStatus, 'approved');
  assert.equal(response.response.run.pendingInteraction, null);
});

test('持久化 MCP 审批按 requestId 匹配统一交互', async () => {
  const approvalRun = run({
    status: 'WAITING_APPROVAL',
    pendingInteraction: {
      interactionId: 'approval-interaction-1',
      kind: 'mcp_approval',
      requestId: 'approval-1',
      title: 'MCP 工具审批',
      prompt: '需要许可',
      allowedActions: [],
    },
  });
  const approvalPart = part({
    id: 'approval-1',
    type: 'mcp-approval',
    metadata: { requestId: 'approval-1' },
  });
  const calls: unknown[] = [];

  await executePersistedApprovalResponse({
    run: approvalRun,
    message: message(),
    part: approvalPart,
    params: { decision: 'approved_run' },
    respond: async (runId, interactionId, params) => {
      calls.push({ runId, interactionId, params });
      return {
        accepted: true,
        duplicate: false,
        interactionId,
        partStatus: 'approved_run',
        summary: '',
        run: approvalRun,
      };
    },
  });

  assert.deepEqual(calls, [{
    runId: 'run-1',
    interactionId: 'approval-interaction-1',
    params: { decision: 'approved_run' },
  }]);
  await assert.rejects(() => executePersistedApprovalResponse({
    run: approvalRun,
    message: message(),
    part: part({ id: 'approval-2', type: 'mcp-approval', metadata: {} }),
    params: { decision: 'approved' },
    respond: async () => ({
      accepted: true,
      duplicate: false,
      interactionId: 'unused',
      partStatus: '',
      summary: '',
      run: approvalRun,
    }),
  }), /已处理或已失效/);
});

test('只有带 runId 的消息进入持久化交互链路', () => {
  assert.equal(isPersistedRunMessage(message()), true);
  assert.equal(isPersistedRunMessage(message({ runId: '' })), false);
  assert.equal(isPersistedRunMessage(null), false);
});
