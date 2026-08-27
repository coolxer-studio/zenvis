import DOMPurify from 'dompurify';
import { marked } from 'marked';
import TurndownService from 'turndown';
import { gfm } from 'turndown-plugin-gfm';

const createTurndownService = () => {
  const service = new TurndownService({
    headingStyle: 'atx',
    bulletListMarker: '-',
    codeBlockStyle: 'fenced',
    emDelimiter: '*',
    strongDelimiter: '**',
  });
  service.use(gfm);
  service.keep(['details', 'summary']);
  return service;
};

export const markdownToSafeHtml = (content = '') => {
  const html = marked.parse(content, { async: false, gfm: true }) as string;
  return DOMPurify.sanitize(html);
};

export const sanitizeHtml = (content = '') => DOMPurify.sanitize(content);

export const htmlToMarkdown = (html = '') => createTurndownService().turndown(html).trim();

export const editorHtmlToCanonical = (
  html: string,
  format: 'markdown' | 'html',
) => format === 'html' ? sanitizeHtml(html) : htmlToMarkdown(html);

export const documentContentToEditorHtml = (
  content: string,
  format: string = 'markdown',
) => format === 'html' ? sanitizeHtml(content) : markdownToSafeHtml(content);

export const escapeHtml = (value = '') => value
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;');

export const buildSafeHtmlDownload = (title: string, editorHtml: string) => {
  const safeBody = sanitizeHtml(editorHtml);
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${escapeHtml(title)}</title>
  <style>
    body { max-width: 880px; margin: 40px auto; padding: 0 24px; color: #1f2329; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 1.75; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #dcdfe6; padding: 8px 10px; }
    blockquote { margin: 16px 0; padding: 8px 14px; border-left: 4px solid #409eff; background: #f5f7fa; }
    img { max-width: 100%; height: auto; }
    pre { padding: 12px; overflow: auto; background: #f5f7fa; }
  </style>
</head>
<body>
${safeBody}
</body>
</html>`;
};

export const sha256Text = async (value: string) => {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest))
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('');
};

export const canApplyReportFragment = (state: {
  returnedBaseRevision?: number;
  expectedBaseRevision: number;
  currentRevision: number;
  returnedSelectionHash?: string;
  expectedSelectionHash: string;
  currentDocumentHash: string;
  expectedDocumentHash: string;
}) => {
  return state.returnedBaseRevision === state.expectedBaseRevision
    && state.currentRevision === state.expectedBaseRevision
    && state.returnedSelectionHash === state.expectedSelectionHash
    && state.currentDocumentHash === state.expectedDocumentHash;
};
