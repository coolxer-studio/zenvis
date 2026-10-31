import assert from 'node:assert/strict';
import test from 'node:test';
import {
  resolveDataPushCardState,
  resolveDataPushRecordState,
} from './data-push-card-state.ts';

test('数据推送确认卡仅在真实验证通过后允许创建', () => {
  const state = resolveDataPushCardState({
    validationStatus: 'passed',
    validation: {
      passed: true,
      candidateDigest: 'candidate',
      validationDigest: 'validation',
      vectorVersion: 'vector 0.55.0',
      tests: { present: true, total: 2, passed: 2, failed: 0 },
      diagnostics: [],
    },
  });

  assert.equal(state.validationPassed, true);
  assert.equal(state.validationFailed, false);
  assert.equal(state.canApprove, true);
  assert.equal(state.tests.total, 2);
  assert.equal(state.vectorVersion, 'vector 0.55.0');
  assert.equal(state.statusText, '校验通过');
});

test('校验失败隐藏创建动作并保留错误路径、建议和修复轮次', () => {
  const state = resolveDataPushCardState({
    validation: {
      passed: false,
      blocked: false,
      tests: { present: true, total: 2, passed: 1, failed: 1 },
      diagnostics: [{
        code: 'VECTOR_UNKNOWN_FIELD',
        path: '$sinks.ch.batch_size',
        message: 'unknown field batch_size',
        repairHint: '使用 batch.max_bytes',
      }],
    },
    repairRound: 2,
    logExcerpt: 'ERROR clickhouse connection refused',
  });

  assert.equal(state.canApprove, false);
  assert.equal(state.validationFailed, true);
  assert.equal(state.statusTagType, 'danger');
  assert.equal(state.diagnostics[0].path, '$sinks.ch.batch_size');
  assert.equal(state.diagnostics[0].repairHint, '使用 batch.max_bytes');
  assert.equal(state.tests.failed, 1);
  assert.equal(state.repairRound, 2);
  assert.match(state.logExcerpt, /connection refused/);
});

test('缺少校验结果时工作流卡片不能创建任务', () => {
  const state = resolveDataPushCardState({ workflowId: 'workflow-1' });

  assert.equal(state.validationPassed, false);
  assert.equal(state.canApprove, false);
  assert.equal(state.statusText, '待校验');
});

test('门禁阻断仍展示服务端允许的重新校验动作且隐藏创建动作', () => {
  const state = resolveDataPushCardState({
    workflowId: 'workflow-1',
    step: 'BLOCKED',
    validationStatus: 'blocked',
    candidateDigest: 'locked-candidate-123456',
    validation: {
      passed: true,
      candidateDigest: 'other-candidate-123456',
      tests: { present: true, total: 2, passed: 2, failed: 0 },
      diagnostics: [],
    },
    allowedActions: ['retry', 'revise'],
    blockingReasons: [{
      code: 'PUSH_CANDIDATE_DIGEST_MISMATCH',
      category: 'candidate',
      message: '卡片校验对应的候选与平台锁定候选不一致。',
      expected: 'locked-cand…',
      actual: 'other-candi…',
      repairHint: '使用锁定候选重新校验。',
    }],
  }, 'blocked');

  assert.equal(state.gateBlocked, true);
  assert.equal(state.validationPassed, true);
  assert.equal(state.canApprove, false);
  assert.equal(state.canRetry, true);
  assert.equal(state.canRevise, true);
  assert.equal(state.actionable, true);
  assert.equal(state.statusText, '门禁未通过');
  assert.equal(state.diagnostics.length, 0);
  assert.equal(state.blockingReasons[0].code, 'PUSH_CANDIDATE_DIGEST_MISMATCH');
});

test('历史阻断卡可从候选摘要差异生成兼容原因', () => {
  const state = resolveDataPushCardState({
    workflowId: 'workflow-1',
    step: 'BLOCKED',
    validationStatus: 'blocked',
    candidateDigest: 'locked-candidate',
    metaRef: { contentDigest: 'locked-meta' },
    validation: {
      passed: true,
      candidateDigest: 'stale-candidate',
      metaDigest: 'locked-meta',
    },
  });

  assert.equal(state.gateBlocked, true);
  assert.equal(state.blockingReasons[0].code, 'PUSH_CANDIDATE_DIGEST_MISMATCH');
  assert.equal(state.blockingReasons[0].expected, 'locked-candi…');
  assert.equal(state.blockingReasons[0].actual, 'stale-candid…');
});

test('配置测试通过但工作流门禁失败时不显示创建动作', () => {
  const state = resolveDataPushCardState({
    workflowId: 'workflow-1',
    step: 'BLOCKED',
    validationStatus: 'blocked',
    candidateDigest: 'locked-candidate',
    metaRef: { contentDigest: 'locked-meta' },
    validation: {
      passed: true,
      blocked: false,
      candidateDigest: 'locked-candidate',
      metaDigest: 'locked-meta',
      validationDigest: 'validation-1',
    },
    allowedActions: ['retry', 'revise'],
  }, 'blocked');

  assert.equal(state.validationPassed, true);
  assert.equal(state.validationFailed, false);
  assert.equal(state.gateBlocked, true);
  assert.equal(state.canApprove, false);
  assert.equal(state.canRetry, true);
  assert.equal(state.statusText, '门禁未通过');
  assert.ok(state.blockingReasons.length > 0);
});

test('已处理卡片不再展示任何操作', () => {
  const state = resolveDataPushCardState({
    workflowId: 'workflow-1',
    step: 'BLOCKED',
    validationStatus: 'blocked',
    validation: { passed: true },
    allowedActions: ['retry', 'revise'],
  }, 'approved');

  assert.equal(state.resolved, true);
  assert.equal(state.actionable, false);
  assert.equal(state.canRetry, false);
  assert.equal(state.canRevise, false);
});

test('成功记录展示真实 taskId、runId、测试和本轮日志摘要', () => {
  const state = resolveDataPushRecordState({
    status: 'running',
    taskId: '31',
    runId: 'run-v1',
    validationDigest: 'validation-v1',
    vectorVersion: 'vector 0.55.0',
    tests: { present: true, total: 2, passed: 2, failed: 0 },
    repairRound: 1,
    logExcerpt: 'component ready',
  });

  assert.equal(state.verified, true);
  assert.equal(state.taskId, '31');
  assert.equal(state.runId, 'run-v1');
  assert.equal(state.validationDigest, 'validation-v1');
  assert.equal(state.tests.total, 2);
  assert.equal(state.repairRound, 1);
  assert.equal(state.logExcerpt, 'component ready');
  assert.equal(state.statusText, '运行已验证');
});

test('缺少本 runId 或校验摘要的记录不能标记为运行已验证', () => {
  const state = resolveDataPushRecordState({
    status: 'running',
    taskId: '31',
    runId: '',
    validationDigest: 'validation-v1',
  });

  assert.equal(state.verified, false);
  assert.equal(state.statusTagType, 'warning');
  assert.equal(state.statusText, '待运行验证');
});
