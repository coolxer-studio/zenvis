import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveMcpApprovalState } from './mcp-approval-state.ts';

test('UNKNOWN 审批展示中文状态和真实错误摘要', () => {
  const state = resolveMcpApprovalState({
    id: 'approval-1',
    type: 'mcp-approval',
    status: 'unknown',
    metadata: {
      errorSummary: '配置未通过平台门禁：PUSH_CONFIG_SYNTAX',
    },
  });

  assert.equal(state.statusText, '结果未知');
  assert.equal(state.errorTitle, '执行结果未知');
  assert.equal(state.hasError, true);
  assert.equal(state.expandedByDefault, true);
  assert.match(state.errorSummary, /PUSH_CONFIG_SYNTAX/);
});

test('普通执行失败默认展开且不再允许审批', () => {
  const state = resolveMcpApprovalState({
    id: 'approval-2',
    type: 'mcp-approval',
    status: 'failed',
    metadata: { error_summary: 'Vectum 返回 500' },
  });

  assert.equal(state.statusText, '执行失败');
  assert.equal(state.tagType, 'danger');
  assert.equal(state.expandedByDefault, true);
  assert.equal(state.canDecide, false);
  assert.equal(state.errorSummary, 'Vectum 返回 500');
});

test('等待审批仍显示可决策状态', () => {
  const state = resolveMcpApprovalState({
    id: 'approval-3',
    type: 'mcp-approval',
    status: 'pending',
    metadata: {},
  });

  assert.equal(state.statusText, '等待审批');
  assert.equal(state.canDecide, true);
  assert.equal(state.expandedByDefault, true);
  assert.equal(state.hasError, false);
});
