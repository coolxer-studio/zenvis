import assert from 'node:assert/strict';
import test from 'node:test';
import {
  extractLatestMessageReportDocument,
  mergeStoredAndMessageReportDocument,
  resolveReportDocumentPartContent,
  shouldApplyIncomingReportDocument,
} from './report-document-sync.ts';

test('报表消息正文清空后从已保存文档元数据恢复预览内容', () => {
  const content = resolveReportDocumentPartContent({
    type: 'report-document',
    content: '',
    metadata: { content: '# 用户事件数据分析报告\n\n正文' },
  });

  assert.equal(content, '# 用户事件数据分析报告\n\n正文');
});

test('最终消息中的已保存报表正文会补齐 extraData 文档引用', () => {
  const generated = extractLatestMessageReportDocument([{
    sender: 'ai',
    parts: [{
      type: 'report-document',
      status: 'saved',
      content: '',
      metadata: {
        documentId: 'doc-1',
        revision: 2,
        title: '运营周报',
        format: 'markdown',
        content: '# 运营周报\n\n正文',
      },
    }],
  }]);

  const merged = mergeStoredAndMessageReportDocument({
    documentId: 'doc-1',
    revision: 2,
    title: '运营周报',
  }, generated);

  assert.equal(merged.content, '# 运营周报\n\n正文');
  assert.equal(merged.revision, 2);
});

test('同一报表只接受新修订，旧修订不能回灌到编辑器', () => {
  const current = { documentId: 'doc-1', revision: 2, content: '旧正文' };

  assert.equal(shouldApplyIncomingReportDocument(
    current,
    { documentId: 'doc-1', revision: 3, content: 'AI 新正文' },
    true,
  ), true);
  assert.equal(shouldApplyIncomingReportDocument(
    current,
    { documentId: 'doc-1', revision: 2, content: '重复事件' },
    true,
  ), false);
  assert.equal(shouldApplyIncomingReportDocument(
    current,
    { documentId: 'doc-1', revision: 1, content: '过期消息正文' },
    false,
  ), false);
});

test('相同修订和内容的消息、工作区双重同步只应用一次', () => {
  const current = {
    documentId: 'doc-1',
    revision: 1,
    contentHash: 'same-hash',
    content: '相同正文',
  };

  assert.equal(shouldApplyIncomingReportDocument(
    current,
    { ...current },
    false,
  ), false);
});
