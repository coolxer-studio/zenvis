import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import {
  buildHtmlDownloadDocument,
  buildHtmlDownloadFileName,
  isCompleteHtmlDocument,
} from './html-download.ts';

describe('HTML download helpers', () => {
  test('detects complete HTML documents with leading comments and BOM', () => {
    assert.equal(isCompleteHtmlDocument('<!doctype html><html></html>'), true);
    assert.equal(isCompleteHtmlDocument('\uFEFF  <!-- generated -->\n<HTML lang="zh-CN">'), true);
    assert.equal(isCompleteHtmlDocument('<section>片段</section>'), false);
  });

  test('keeps a complete HTML document unchanged', () => {
    const source = '<!doctype html>\n<html><body><script>console.log(1)</script></body></html>';

    assert.equal(buildHtmlDownloadDocument(source, 'ignored'), source);
  });

  test('wraps an HTML fragment in a complete document without removing scripts', () => {
    const source = '<style>body{color:red}</style><p>Hello</p><script>run()</script>';
    const document = buildHtmlDownloadDocument(source, '示例 <页面>');

    assert.match(document, /^<!doctype html>/);
    assert.match(document, /<meta charset="UTF-8" \/>/);
    assert.match(document, /<title>示例 &lt;页面&gt;<\/title>/);
    assert.match(document, /<script>run\(\)<\/script>/);
  });

  test('builds safe HTML file names and appends the extension', () => {
    assert.equal(
      buildHtmlDownloadFileName(undefined, 1700000000000),
      'html-preview-1700000000000.html',
    );
    assert.equal(buildHtmlDownloadFileName('安全 周报/首页'), '安全-周报-首页.html');
    assert.equal(buildHtmlDownloadFileName('index.html'), 'index.html');
    assert.equal(buildHtmlDownloadFileName('page.htm'), 'page.htm');
  });
});
