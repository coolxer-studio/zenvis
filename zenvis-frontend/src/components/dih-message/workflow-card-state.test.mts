import assert from 'node:assert/strict';
import test from 'node:test';
import { currentWorkflowRevisions, isCurrentWorkflowPart } from './workflow-card-state.ts';

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
