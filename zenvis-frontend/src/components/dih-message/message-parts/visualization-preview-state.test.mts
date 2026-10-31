import assert from 'node:assert/strict';
import test from 'node:test';
import type { ChatMessagePart } from '@/types/type-dih';
import {
  canAddVisualizationChartToLibrary,
  hasBlockedVisualizationChart,
  visualizationChartLibraryAction,
  visualizationChartOption,
  visualizationPreviewSnapshotNotice,
  visualizationPreviewValidationNotice,
} from './visualization-preview-state.ts';

const preview = (metadata: Record<string, unknown>): ChatMessagePart =>
  ({
    type: 'visualization-chart-preview',
    metadata,
  } as ChatMessagePart);

test('参数不一致时提示图表可入库但保留未验证状态', () => {
  const notice = visualizationPreviewValidationNotice(
    preview({
      validationStatus: 'blocked',
      validationCode: 'query_parameter_mismatch',
      validationMessage: '数据查询参数与已确认方案不一致，本次结果未验证',
    }),
  );

  assert.equal(
    notice,
    '数据查询参数与已确认方案不一致，本次结果未验证。当前图表仍可加入图表库，但记录会保留未验证状态；如需重新查询，请调整查询方案或重试当前阶段。',
  );
});

test('其他校验失败提示可入库并保留原始校验状态', () => {
  const notice = visualizationPreviewValidationNotice(
    preview({
      validationStatus: 'failed',
      validationCode: 'missing_echarts_option',
      validationMessage: '数据 MCP 未返回可渲染的 echarts.option',
    }),
  );

  assert.equal(
    notice,
    '数据 MCP 未返回可渲染的 echarts.option。当前图表仍可加入图表库，但记录会保留原始校验状态。',
  );
});

test('本轮缺少查询证据时提示重新执行数据查询', () => {
  const notice = visualizationPreviewValidationNotice(
    preview({
      validationStatus: 'blocked',
      validationCode: 'query_evidence_missing',
      validationMessage: '本轮没有成功执行数据查询，且会话内没有可复用的已验证查询快照',
    }),
  );

  assert.equal(
    notice,
    '本轮没有成功执行数据查询，且会话内没有可复用的已验证查询快照。当前图表仍可加入图表库，但会标记为未验证；如需验证，请重新执行数据查询。',
  );
});

test('复用已验证快照时展示来源查询时间', () => {
  assert.equal(
    visualizationPreviewSnapshotNotice(
      preview({
        validationStatus: 'success',
        evidenceMode: 'reused_snapshot',
        sourceQueriedAt: '2026-10-31T17:31:24+08:00',
      }),
    ),
    '基于 10/31 17:31 的已验证查询快照生成，未重新查询数据。',
  );
});

test('成功实时查询和阻塞图表不会生成快照复用提示', () => {
  assert.equal(
    visualizationPreviewSnapshotNotice(
      preview({
        validationStatus: 'success',
        evidenceMode: 'fresh_query',
        sourceQueriedAt: '2026-10-31T17:31:24+08:00',
      }),
    ),
    '',
  );
  assert.equal(
    hasBlockedVisualizationChart([
      preview({
        step: 'BLOCKED',
        validationStatus: 'blocked',
      }),
    ]),
    true,
  );
  assert.equal(
    hasBlockedVisualizationChart([
      preview({
        step: 'ARTIFACT_READY',
        validationStatus: 'success',
      }),
    ]),
    false,
  );
});

test('校验成功时不显示阻塞提示', () => {
  assert.equal(visualizationPreviewValidationNotice(preview({ validationStatus: 'success' })), '');
});

test('成功卡和阻塞卡只要存在可渲染配置即可加入图表库', () => {
  const success = preview({
    action: 'visualization_chart.add_chart_library',
    workflowId: 'workflow-1',
    validationStatus: 'success',
    echartsOption: { series: [{ type: 'bar', data: [1] }] },
  });
  const blocked = preview({
    blockedAction: 'visualization_chart.add_chart_library',
    workflowId: 'workflow-1',
    validationStatus: 'blocked',
    echartsOption: JSON.stringify({ series: [{ type: 'bar', data: [1] }] }),
  });

  assert.equal(canAddVisualizationChartToLibrary(success), true);
  assert.equal(canAddVisualizationChartToLibrary(blocked), true);
  assert.equal(visualizationChartLibraryAction(blocked), 'visualization_chart.add_chart_library');
  assert.deepEqual(visualizationChartOption(blocked), {
    series: [{ type: 'bar', data: [1] }],
  });
});

test('缺少配置、渲染失败、加载中或已入库的图表不能重复加入', () => {
  const metadata = {
    workflowId: 'workflow-1',
    echartsOption: { series: [{ type: 'bar', data: [1] }] },
  };

  assert.equal(canAddVisualizationChartToLibrary(preview({ workflowId: 'workflow-1' })), false);
  assert.equal(canAddVisualizationChartToLibrary(preview(metadata), true), false);
  assert.equal(
    canAddVisualizationChartToLibrary({ ...preview(metadata), status: 'loading' }),
    false,
  );
  assert.equal(canAddVisualizationChartToLibrary({ ...preview(metadata), status: 'added' }), false);
  assert.equal(canAddVisualizationChartToLibrary({ ...preview(metadata), type: 'confirm' }), false);
});

test('没有工作流或演示标识的普通预览不能加入图表库', () => {
  assert.equal(
    canAddVisualizationChartToLibrary(
      preview({
        echartsOption: { series: [{ type: 'bar', data: [1] }] },
      }),
    ),
    false,
  );
});
