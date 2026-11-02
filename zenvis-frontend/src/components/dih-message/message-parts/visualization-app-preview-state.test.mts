import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveVisualizationAppPreviewState } from './visualization-app-preview-state.ts';

test('解析服务端校验预览并保留摘要、页面和菜单信息', () => {
  const state = resolveVisualizationAppPreviewState({
    metadata: {
      workflowId: 'workflow-1',
      candidateDigest: 'candidate-digest',
      validationDigest: 'validation-digest',
      validation: {
        appId: 'user-event-analysis',
        appVersion: 2,
        mode: 'LOW_CODE_APP',
        previousMode: 'LOW_CODE_PAGE',
        specDigest: 'spec-digest',
        metaDigest: 'meta-digest',
        validationStatus: 'validated',
        files: [{ path: 'site.json', size: 2048 }],
        deletedFiles: ['index.json'],
        diagnostics: [{ code: 'INFO', message: '无阻断问题', severity: 'warning' }],
        preview: {
          siteUrl: '/api/v1/dih/chat/workflows/workflow-1/app-preview/site?token=preview',
          pages: [{ page_id: 'overview', title: '总览' }],
          querySamples: {
            records: { entity: 'user_event', total: 12 },
          },
        },
        menu: {
          menu_id: 31,
          menu_name: '用户事件分析',
          menu_type: 'LOW_CODE_APP',
          publish_status: 'published',
        },
      },
    },
  });

  assert.equal(state.appId, 'user-event-analysis');
  assert.equal(state.appVersion, 2);
  assert.equal(state.modeLabel, '多页应用');
  assert.equal(state.modeChanged, true);
  assert.equal(state.validated, true);
  assert.equal(state.canPublish, true);
  assert.equal(state.candidateDigest, 'candidate-digest');
  assert.equal(state.validationDigest, 'validation-digest');
  assert.equal(state.files[0].path, 'site.json');
  assert.equal(state.deletedFiles[0], 'index.json');
  assert.equal(state.pages[0].pageId, 'overview');
  assert.equal(state.menu.menuId, 31);
  assert.equal(state.menuStatusLabel, '已发布并读回');
  assert.deepEqual(state.querySamples, [{ label: 'user_event', value: '12' }]);
});

test('部分成功只开放菜单重试且不要求菜单 ID', () => {
  const state = resolveVisualizationAppPreviewState({
    metadata: {
      appId: 'user-event-analysis',
      validationStatus: 'partial_menu',
      candidateDigest: 'candidate-digest',
      validationDigest: 'validation-digest',
      menu: { publishStatus: 'partial_menu' },
    },
  });

  assert.equal(state.partialMenu, true);
  assert.equal(state.validated, false);
  assert.equal(state.canPublish, true);
  assert.equal(state.publishDecision, 'retry');
  assert.equal(state.publishLabel, '重试菜单发布');
  assert.equal(state.menu.menuId, '');
  assert.equal(state.menuStatusLabel, '部分成功，可重试');
});

test('通过标记不能覆盖部分成功状态，普通预览不具有发布动作', () => {
  const partial = resolveVisualizationAppPreviewState({
    metadata: {
      passed: true,
      validationStatus: 'partial_menu',
      status: 'partial_menu',
    },
  });
  assert.equal(partial.validated, false);
  assert.equal(partial.partialMenu, true);

  const invalid = resolveVisualizationAppPreviewState({
    metadata: {
      validationStatus: 'blocked',
      diagnostics: [{ code: 'APP_WRITE_FAILED', message: '写入失败', severity: 'error' }],
    },
  });
  assert.equal(invalid.validated, false);
  assert.equal(invalid.partialMenu, false);
  assert.equal(invalid.canPublish, false);
  assert.equal(invalid.diagnostics[0].code, 'APP_WRITE_FAILED');
});
