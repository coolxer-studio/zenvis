import assert from 'node:assert/strict';
import test from 'node:test';
import { currentWorkflowRevisions, isCurrentWorkflowPart } from './workflow-card-state.ts';
import {
  metadataPathOperation,
  metadataPathOperationAffectedFiles,
  metadataPathOperationApproveLabel,
  metadataPathOperationRejectLabel,
  metadataForceApproveAllowed,
  metadataValidationMessages,
  workflowActionForConfigDecision,
} from './config-decision-actions.ts';

test('工作流只把最高 stateRevision 的卡片识别为当前卡片', () => {
  const oldPlan = {
    type: 'confirm',
    metadata: { workflowId: 'workflow-1', stateRevision: 6 },
  };
  const currentArtifact = {
    type: 'visualization-chart-preview',
    metadata: { workflowId: 'workflow-1', stateRevision: 8 },
  };
  const messages = [{ parts: [oldPlan, currentArtifact] }];

  const revisions = currentWorkflowRevisions(messages);

  assert.deepEqual(revisions, { 'workflow-1': 8 });
  assert.equal(isCurrentWorkflowPart(oldPlan, revisions), false);
  assert.equal(isCurrentWorkflowPart(currentArtifact, revisions), true);
});

test('不同工作流分别维护当前检查点，历史缺失版本的卡片保持兼容', () => {
  const legacy = { type: 'confirm', metadata: { workflowId: 'workflow-legacy' } };
  const first = {
    type: 'confirm',
    metadata: { workflowId: 'workflow-a', stateRevision: 2 },
  };
  const second = {
    type: 'confirm',
    metadata: { workflowId: 'workflow-b', stateRevision: 4 },
  };

  const revisions = currentWorkflowRevisions([{ parts: [legacy, first, second] }]);

  assert.deepEqual(revisions, { 'workflow-a': 2, 'workflow-b': 4 });
  assert.equal(isCurrentWorkflowPart(legacy, revisions), true);
  assert.equal(isCurrentWorkflowPart(first, revisions), true);
  assert.equal(isCurrentWorkflowPart(second, revisions), true);
});

test('元数据失败候选映射强制动作且正常候选不暴露强制入口', () => {
  const failed = {
    validationStatus: 'failed',
    forceAllowed: true,
    allowedActions: ['force_approve', 'reject', 'revise'],
    validationErrors: [
      {
        code: 'META_COLUMN_TYPE_INVALID',
        path: '$.attribute[0].column_type',
        message: '不支持的 ClickHouse 字段类型',
      },
    ],
  };
  const passed = {
    validationStatus: 'success',
    forceAllowed: false,
    allowedActions: ['approve', 'reject', 'revise'],
    validationErrors: [],
  };

  assert.equal(workflowActionForConfigDecision('force_approve'), 'force_approve');
  assert.equal(workflowActionForConfigDecision('apply_config'), 'approve');
  assert.equal(metadataForceApproveAllowed(failed), true);
  assert.equal(metadataForceApproveAllowed(passed), false);
  assert.deepEqual(metadataValidationMessages(failed), [
    '[META_COLUMN_TYPE_INVALID] $.attribute[0].column_type：不支持的 ClickHouse 字段类型',
  ]);
  assert.deepEqual(metadataValidationMessages(passed), []);
});

test('摘要缺失或动作列表未授权时不显示强制写入', () => {
  assert.equal(metadataForceApproveAllowed({
    validationStatus: 'failed',
    forceAllowed: true,
    allowedActions: ['reject', 'revise'],
  }), false);
  assert.equal(metadataForceApproveAllowed({
    validationStatus: 'success',
    forceAllowed: true,
    allowedActions: ['force_approve'],
  }), false);
});

test('元数据目录操作生成重命名与删除确认文案且不暴露强制入口', () => {
  const rename = {
    operation: 'rename',
    sourcePath: 'archive/old.json',
    targetPath: 'archive/new.json',
    recursive: false,
    validationStatus: 'success',
    forceAllowed: false,
    allowedActions: ['approve', 'reject', 'revise'],
  };
  const recursiveDelete = {
    operation: 'delete',
    sourcePath: 'archive',
    recursive: true,
    affectedFiles: ['archive/a.json', 'archive/nested/b.json'],
    validationStatus: 'success',
    forceAllowed: false,
    allowedActions: ['approve', 'reject', 'revise'],
  };

  assert.equal(metadataPathOperation(rename), 'rename');
  assert.equal(metadataPathOperationApproveLabel(rename), '确认重命名');
  assert.equal(metadataPathOperationApproveLabel(recursiveDelete), '确认递归删除');
  assert.equal(metadataPathOperationRejectLabel(recursiveDelete), '取消本次操作');
  assert.deepEqual(metadataPathOperationAffectedFiles(recursiveDelete), [
    'archive/a.json',
    'archive/nested/b.json',
  ]);
  assert.equal(metadataForceApproveAllowed(rename), false);
  assert.equal(metadataForceApproveAllowed(recursiveDelete), false);
  assert.equal(workflowActionForConfigDecision('apply_config'), 'approve');
});
