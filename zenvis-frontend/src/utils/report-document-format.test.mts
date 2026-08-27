import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  canApplyReportFragment,
  escapeHtml,
  htmlToMarkdown,
} from './report-document-format.ts';

describe('report document format conversion', () => {
  it('preserves GFM tables, links, images, code blocks and nested lists', () => {
    const markdown = htmlToMarkdown(`
      <h1>分析报告</h1>
      <table>
        <thead><tr><th>指标</th><th>值</th></tr></thead>
        <tbody><tr><td>告警</td><td>12</td></tr></tbody>
      </table>
      <p><a href="https://example.com">来源</a></p>
      <p><img src="https://example.com/chart.png" alt="趋势图"></p>
      <pre><code class="language-json">{"count":12}</code></pre>
      <ul><li>一级<ul><li>二级</li></ul></li></ul>
    `);

    assert.match(markdown, /\| 指标 \| 值 \|/);
    assert.ok(markdown.includes('[来源](https://example.com)'));
    assert.ok(markdown.includes('![趋势图](https://example.com/chart.png)'));
    assert.ok(markdown.includes('```json'));
    assert.ok(markdown.includes('{"count":12}'));
    assert.match(markdown, /-\s+一级\s*\n\s+-\s+二级/);
  });

  it('escapes report titles used in exported HTML', () => {
    assert.equal(
      escapeHtml('<周报 & "复盘">'),
      '&lt;周报 &amp; &quot;复盘&quot;&gt;',
    );
  });

  it('rejects a late selection rewrite after the document changes', () => {
    assert.equal(canApplyReportFragment({
      returnedBaseRevision: 4,
      expectedBaseRevision: 4,
      currentRevision: 4,
      returnedSelectionHash: 'selection-a',
      expectedSelectionHash: 'selection-a',
      currentDocumentHash: 'document-after-edit',
      expectedDocumentHash: 'document-before-edit',
    }), false);
    assert.equal(canApplyReportFragment({
      returnedBaseRevision: 4,
      expectedBaseRevision: 4,
      currentRevision: 4,
      returnedSelectionHash: 'selection-a',
      expectedSelectionHash: 'selection-a',
      currentDocumentHash: 'document-before-edit',
      expectedDocumentHash: 'document-before-edit',
    }), true);
  });
});
