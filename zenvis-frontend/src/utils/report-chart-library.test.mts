import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  CHART_IMAGE_MAX_DATA_URL_LENGTH,
  REPORT_CONTENT_MAX_CHARS,
  buildChartImageHtml,
  chartImageFitsReport,
  chartOptionFromLibraryItem,
  chartSourceRef,
  chartTypeLabel,
  chooseChartImageDataUrl,
  mergeReportSourceRefs,
  normalizeChartOption,
} from './report-chart-library.ts';

describe('report chart library', () => {
  it('normalizes object and JSON options and rejects invalid snapshots', () => {
    assert.deepEqual(normalizeChartOption({ series: [{ type: 'bar' }] }), {
      series: [{ type: 'bar' }],
    });
    assert.deepEqual(normalizeChartOption('{"xAxis":{"type":"category"}}'), {
      xAxis: { type: 'category' },
    });
    assert.equal(normalizeChartOption('{broken'), null);
    assert.equal(normalizeChartOption({}), null);
    assert.deepEqual(
      chartOptionFromLibraryItem({ type: 'chart', id: 'chart-1', echartsOption: '{"series":[]}' }),
      { series: [] },
    );
  });

  it('labels chart types in the library picker', () => {
    assert.equal(chartTypeLabel('bar'), '柱状图');
    assert.equal(chartTypeLabel('line'), '折线图');
    assert.equal(chartTypeLabel('custom'), 'custom');
    assert.equal(chartTypeLabel(), '图表');
  });

  it('builds escaped static chart HTML with a compact caption', () => {
    const html = buildChartImageHtml(
      {
        type: 'chart',
        id: 'chart<1>',
        name: '<趋势 & "复盘">',
      },
      'data:image/png;base64,AAAA',
    );

    assert.ok(html.includes('data-report-chart-id="chart&lt;1&gt;"'));
    assert.ok(html.includes('alt="&lt;趋势 &amp; &quot;复盘&quot;&gt;"'));
    assert.ok(html.includes('图表：&lt;趋势 &amp; &quot;复盘&quot;&gt;'));
    assert.ok(!html.includes('<趋势'));
    assert.throws(
      () => buildChartImageHtml({ type: 'chart', id: 'chart-1' }, 'https://example.com/chart.png'),
      /格式不受支持/,
    );
  });

  it('keeps new chart source refs compact and merges duplicates by newest value', () => {
    const source = chartSourceRef({
      type: 'chart',
      id: 'chart-1',
      artifactId: 'artifact-1',
      name: '趋势图',
      chartType: 'line',
      sessionRecordId: 101,
      echartsOption: { series: [{ type: 'line' }] },
    });

    assert.equal(source.type, 'chart');
    assert.equal(source.origin, 'chart_library');
    assert.equal(source.artifactId, 'artifact-1');
    assert.equal('echartsOption' in source, false);
    assert.deepEqual(
      mergeReportSourceRefs(
        [{ type: 'attachment', id: 'file-1', name: '附件' }],
        [{ type: 'chart', id: 'chart-1', name: '旧名称' }],
        [source],
      ),
      [{ type: 'attachment', id: 'file-1', name: '附件' }, source],
    );
  });

  it('prefers PNG and falls back to a smaller JPEG for large snapshots', () => {
    const png = 'data:image/png;base64,small';
    const jpeg = 'data:image/jpeg;base64,small';
    assert.equal(chooseChartImageDataUrl(png, jpeg), png);

    const largePng = `data:image/png;base64,${'a'.repeat(CHART_IMAGE_MAX_DATA_URL_LENGTH)}`;
    assert.equal(chooseChartImageDataUrl(largePng, jpeg), jpeg);
    assert.equal(chooseChartImageDataUrl(largePng), largePng);
  });

  it('blocks images that exceed the report content capacity', () => {
    assert.equal(chartImageFitsReport('正文', 'data:image/png;base64,AAAA', '图表：趋势'), true);
    assert.equal(
      chartImageFitsReport(
        'a'.repeat(REPORT_CONTENT_MAX_CHARS - 100),
        'data:image/png;base64,AAAA',
        '图表：趋势',
      ),
      false,
    );
  });
});
