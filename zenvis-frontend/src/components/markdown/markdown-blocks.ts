export type MarkdownContentSegment =
  | { type: 'markdown'; content: string }
  | { type: 'mermaid'; content: string }
  | { type: 'html'; content: string };

type OpeningFence = {
  marker: '`' | '~';
  length: number;
  info: string;
};

type PreviewLanguage = Extract<MarkdownContentSegment['type'], 'mermaid' | 'html'>;

const OPENING_FENCE_PATTERN = /^( {0,3})(`{3,}|~{3,})([^\r\n]*)$/;
const CLOSING_FENCE_PATTERN = /^ {0,3}(`{3,}|~{3,})[ \t]*$/;
const PREVIEW_LANGUAGES = new Set<PreviewLanguage>(['mermaid', 'html']);

const parseOpeningFence = (line: string): OpeningFence | null => {
  const match = OPENING_FENCE_PATTERN.exec(line);
  if (!match) return null;
  const markerSequence = match[2];
  const info = match[3] || '';
  if (markerSequence[0] === '`' && info.includes('`')) return null;
  return {
    marker: markerSequence[0] as OpeningFence['marker'],
    length: markerSequence.length,
    info,
  };
};

const isClosingFence = (line: string, opening: OpeningFence): boolean => {
  const match = CLOSING_FENCE_PATTERN.exec(line);
  if (!match) return false;
  const markerSequence = match[1];
  return markerSequence[0] === opening.marker && markerSequence.length >= opening.length;
};

const previewLanguage = (info: string): PreviewLanguage | null => {
  const language = info.trim().split(/\s+/)[0]?.toLowerCase() || '';
  return PREVIEW_LANGUAGES.has(language as PreviewLanguage) ? (language as PreviewLanguage) : null;
};

/**
 * Splits closed Mermaid and HTML fences from Markdown while leaving every other
 * fence untouched so the regular Markdown renderer keeps its existing output.
 */
export const splitMarkdownBlocks = (content = ''): MarkdownContentSegment[] => {
  const lines = content.split(/\r?\n/);
  const segments: MarkdownContentSegment[] = [];
  const markdownLines: string[] = [];
  let cursor = 0;

  const flushMarkdown = () => {
    if (!markdownLines.length) return;
    const markdown = markdownLines.join('\n');
    if (markdown.trim()) {
      segments.push({ type: 'markdown', content: markdown });
    }
    markdownLines.length = 0;
  };

  while (cursor < lines.length) {
    const opening = parseOpeningFence(lines[cursor]);
    if (!opening) {
      markdownLines.push(lines[cursor]);
      cursor += 1;
      continue;
    }

    let closingIndex = -1;
    for (let probe = cursor + 1; probe < lines.length; probe += 1) {
      if (isClosingFence(lines[probe], opening)) {
        closingIndex = probe;
        break;
      }
    }

    if (closingIndex === -1) {
      markdownLines.push(...lines.slice(cursor));
      break;
    }

    const language = previewLanguage(opening.info);
    if (language) {
      flushMarkdown();
      segments.push({
        type: language,
        content: lines.slice(cursor + 1, closingIndex).join('\n'),
      });
    } else {
      markdownLines.push(...lines.slice(cursor, closingIndex + 1));
    }
    cursor = closingIndex + 1;
  }

  flushMarkdown();
  return segments;
};
