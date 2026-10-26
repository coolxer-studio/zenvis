import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import { splitMarkdownBlocks } from './markdown-blocks.ts';

describe('splitMarkdownBlocks', () => {
  test('keeps ordinary Markdown as one segment', () => {
    const content = '## 结论\n\n普通段落。';

    assert.deepEqual(splitMarkdownBlocks(content), [{ type: 'markdown', content }]);
  });

  test('recognizes Mermaid fences case-insensitively', () => {
    const segments = splitMarkdownBlocks(
      ['```MERMAID', 'flowchart TD', 'A --> B', '```'].join('\n'),
    );

    assert.deepEqual(segments, [{ type: 'mermaid', content: 'flowchart TD\nA --> B' }]);
  });

  test('recognizes HTML fences case-insensitively', () => {
    const segments = splitMarkdownBlocks(
      ['```HTML', '<!doctype html>', '<html><body>Hello</body></html>', '```'].join('\n'),
    );

    assert.deepEqual(segments, [
      {
        type: 'html',
        content: '<!doctype html>\n<html><body>Hello</body></html>',
      },
    ]);
  });

  test('splits multiple preview blocks while preserving surrounding Markdown', () => {
    const segments = splitMarkdownBlocks(
      [
        '## 总体架构',
        '',
        '```mermaid',
        'flowchart TD',
        'A --> B',
        '```',
        '',
        '中间说明',
        '',
        '````html',
        '<p>页面</p>',
        '````',
        '',
        '结尾说明',
      ].join('\n'),
    );

    assert.deepEqual(
      segments.map(segment => segment.type),
      ['markdown', 'mermaid', 'markdown', 'html', 'markdown'],
    );
    assert.match(segments[0].content, /总体架构/);
    assert.equal(segments[1].content, 'flowchart TD\nA --> B');
    assert.match(segments[2].content, /中间说明/);
    assert.equal(segments[3].content, '<p>页面</p>');
    assert.match(segments[4].content, /结尾说明/);
  });

  test('does not split preview text inside another code fence', () => {
    const content = [
      '````text',
      'Here is a mermaid example:',
      '```mermaid',
      '```',
      '```html',
      '```',
      '````',
    ].join('\n');

    assert.deepEqual(splitMarkdownBlocks(content), [{ type: 'markdown', content }]);
  });

  test('keeps unclosed preview fences as Markdown', () => {
    const content = ['前言', '```mermaid', 'flowchart TD', 'A --> B', '```html', '<p>x</p>'].join(
      '\n',
    );

    assert.deepEqual(splitMarkdownBlocks(content), [{ type: 'markdown', content }]);
  });

  test('supports indented and tilde preview fences', () => {
    const segments = splitMarkdownBlocks(
      [
        '  ```mermaid',
        '  flowchart TD',
        '  A --> B',
        '  ```',
        '~~~html',
        '<p>Hello</p>',
        '~~~',
      ].join('\n'),
    );

    assert.deepEqual(segments, [
      { type: 'mermaid', content: '  flowchart TD\n  A --> B' },
      { type: 'html', content: '<p>Hello</p>' },
    ]);
  });
});
