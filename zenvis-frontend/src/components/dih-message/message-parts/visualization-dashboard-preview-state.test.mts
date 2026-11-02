import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveVisualizationDashboardPreviewState } from './visualization-dashboard-preview-state.ts';

test('解析服务端大屏校验预览并保留摘要、区块和真实查询取样', () => {
  const state = resolveVisualizationDashboardPreviewState({
    metadata: {
      workflowId: 'workflow-dashboard-1',
      candidateDigest: 'candidate-digest',
      validationDigest: 'validation-digest',
      validation: {
        dashboardId: 'user-event-dashboard',
        dashboardVersion: 3,
        mode: 'HTML_PAGE',
        previousMode: 'HTML_PAGE',
        validationStatus: 'validated',
        specDigest: 'spec-digest',
        metaDigest: 'meta-digest',
        fileDigest: 'file-digest',
        files: [{ path: 'user-event-dashboard.html', size: 4096 }],
        diagnostics: [{ code: 'REPRESENTATIVE_QUERY_EMPTY', message: '空结果', severity: 'warning' }],
        spec: {
          dashboard_id: 'user-event-dashboard',
          name: '用户事件大屏',
          theme: 'DARK_TECH',
          refresh_interval_seconds: 60,
          default_time_range: 'LAST_7_DAYS',
          blocks: [
            { block_id: 'kpi', type: 'metrics', title: '关键指标', query: { tool: 'entity_summary' } },
            { block_id: 'trend', type: 'chart', title: '事件趋势', query: { tool: 'entity_trend' } },
          ],
        },
        preview: {
          htmlUrl: '/api/v1/dih/chat/workflows/workflow-dashboard-1/dashboard-preview?token=preview',
          querySamples: {
            kpi: { entity: 'user_event', total: 88, empty: false },
            trend: { entity: 'user_event', rowCount: 0, empty: true },
          },
        },
      },
    },
  });

  assert.equal(state.dashboardId, 'user-event-dashboard');
  assert.equal(state.dashboardName, '用户事件大屏');
  assert.equal(state.dashboardVersion, 3);
  assert.equal(state.modeLabel, '静态 HTML 大屏');
  assert.equal(state.modeChanged, false);
  assert.equal(state.validated, true);
  assert.equal(state.canPublish, true);
  assert.equal(state.publishLabel, '确认发布大屏');
  assert.equal(state.refreshIntervalSeconds, 60);
  assert.equal(state.defaultTimeRange, 'LAST_7_DAYS');
  assert.equal(state.fileDigest, 'file-digest');
  assert.equal(state.files[0].path, 'user-event-dashboard.html');
  assert.equal(state.blocks[1].queryTool, 'entity_trend');
  assert.equal(state.blocks[1].empty, true);
  assert.deepEqual(state.querySamples.map(sample => [sample.label, sample.value, sample.empty]), [
    ['关键指标', '88', false],
    ['事件趋势', '空结果', true],
  ]);
});

test('低代码与模式漂移可识别，未验证预览不开放发布', () => {
  const lowCode = resolveVisualizationDashboardPreviewState({
    metadata: {
      dashboard_id: 'user-event-low-code',
      dashboard_version: 2,
      previous_mode: 'HTML_PAGE',
      mode: 'LOW_CODE_PAGE',
      validation_status: 'blocked',
      diagnostics: [{ code: 'DASHBOARD_MODE_LOCKED', message: '模式已锁定', severity: 'error' }],
    },
  });

  assert.equal(lowCode.dashboardId, 'user-event-low-code');
  assert.equal(lowCode.dashboardVersion, 2);
  assert.equal(lowCode.modeLabel, '低代码大屏');
  assert.equal(lowCode.modeChanged, true);
  assert.equal(lowCode.validated, false);
  assert.equal(lowCode.canPublish, false);
  assert.equal(lowCode.diagnostics[0].code, 'DASHBOARD_MODE_LOCKED');
});

test('passed 标记可兼容服务端已验证结果，但阻断状态优先', () => {
  const validated = resolveVisualizationDashboardPreviewState({
    metadata: { passed: true, candidateDigest: 'candidate', validationDigest: 'validation' },
  });
  assert.equal(validated.validated, true);

  const blocked = resolveVisualizationDashboardPreviewState({
    metadata: {
      passed: true,
      validation_status: 'blocked',
      diagnostics: [{ code: 'DASHBOARD_WRITE_FAILED', message: '写入失败', severity: 'error' }],
    },
  });
  assert.equal(blocked.validated, false);
  assert.equal(blocked.canPublish, false);
});

test('服务端 success 预览卡无完整 spec 时仍显示区块摘要并可发布', () => {
  const state = resolveVisualizationDashboardPreviewState({
    metadata: {
      dashboardId: 'user-event-dashboard',
      dashboardVersion: 1,
      mode: 'HTML_PAGE',
      workflowId: 'workflow-dashboard-success',
      validationStatus: 'success',
      candidateDigest: 'candidate-success',
      validationDigest: 'validation-success',
      files: [{ path: 'user-event-dashboard.html', digest: 'file-success', size: 5120 }],
      diagnostics: [],
      blockSummary: [
        {
          block_id: 'kpis',
          type: 'metrics',
          title: '关键指标',
          query: { tool: 'entity_summary' },
        },
        {
          block_id: 'trend',
          type: 'chart',
          title: '事件趋势',
          query: { tool: 'entity_trend' },
        },
      ],
      preview: {
        htmlUrl: '/api/v1/dih/chat/workflows/workflow-dashboard-success/dashboard-preview?token=preview',
        querySamples: {
          kpis: { total: 128, empty: false },
          trend: { row_count: 0, empty: true },
        },
      },
    },
  });

  assert.equal(state.validationStatus, 'success');
  assert.equal(state.validated, true);
  assert.equal(state.canPublish, true);
  assert.deepEqual(state.blocks.map(block => block.blockId), ['kpis', 'trend']);
  assert.deepEqual(state.blocks.map(block => block.queryTool), ['entity_summary', 'entity_trend']);
  assert.deepEqual(state.querySamples.map(sample => [sample.label, sample.value]), [
    ['关键指标', '128'],
    ['事件趋势', '空结果'],
  ]);
});

test('已批准或不再允许 approve 的预览卡不显示可操作发布按钮', () => {
  const metadata = {
    validationStatus: 'validated',
    candidateDigest: 'candidate-published',
    validationDigest: 'validation-published',
    allowedActions: [],
  };

  const approved = resolveVisualizationDashboardPreviewState({
    status: 'approved',
    metadata,
  });
  assert.equal(approved.validated, true);
  assert.equal(approved.canPublish, false);

  const pendingWithoutApprove = resolveVisualizationDashboardPreviewState({
    status: 'pending',
    metadata: { ...metadata, allowedActions: ['reject', 'revise'] },
  });
  assert.equal(pendingWithoutApprove.canPublish, false);

  const pendingWithApprove = resolveVisualizationDashboardPreviewState({
    status: 'pending',
    metadata: { ...metadata, allowedActions: ['approve', 'reject', 'revise'] },
  });
  assert.equal(pendingWithApprove.canPublish, true);
});
