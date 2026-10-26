export const HTML_PREVIEW_SANDBOX =
  'allow-scripts allow-forms allow-modals allow-popups allow-downloads';

export const HTML_PREVIEW_REFERRER_POLICY = 'no-referrer';

const HTML_DOCUMENT_PATTERN = /^(?:<!doctype\s+html\b|<html(?:\s|>))/i;
const HTML_FILE_EXTENSION = /\.html?$/i;

const escapeHtml = (value: string) =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

const leadingDocumentContent = (source: string) =>
  source.replace(/^\uFEFF/, '').replace(/^(?:\s|<!--[\s\S]*?-->)*/, '');

export const isCompleteHtmlDocument = (source = '') =>
  HTML_DOCUMENT_PATTERN.test(leadingDocumentContent(source));

export const buildHtmlDownloadDocument = (source: string, title = 'HTML Preview') => {
  if (isCompleteHtmlDocument(source)) return source;
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${escapeHtml(title || 'HTML Preview')}</title>
</head>
<body>
${source}
</body>
</html>`;
};

export const buildHtmlDownloadFileName = (fileName?: string, timestamp = Date.now()) => {
  const normalized = fileName?.trim() || `html-preview-${timestamp}`;
  const safeName =
    normalized.replace(/[\\/:*?"<>|\s]+/g, '-').replace(/^-+|-+$/g, '') || 'html-preview';
  return HTML_FILE_EXTENSION.test(safeName) ? safeName : `${safeName}.html`;
};

export const downloadHtmlDocument = (
  source: string,
  options: { fileName?: string; title?: string } = {},
) => {
  const blob = new Blob([buildHtmlDownloadDocument(source, options.title)], {
    type: 'text/html;charset=utf-8',
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = buildHtmlDownloadFileName(options.fileName);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
};
