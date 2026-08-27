export type ReportDocumentLike = Record<string, unknown> & {
  id?: string;
  documentId?: string;
  title?: string;
  name?: string;
  content?: string;
  contentHash?: string;
  revision?: number;
};

type MessagePartLike = {
  type?: string;
  title?: string;
  content?: string;
  language?: string;
  status?: string;
  metadata?: Record<string, unknown>;
};

type MessageLike = {
  sender?: string;
  parts?: MessagePartLike[];
};

const asRecord = (value: unknown): Record<string, unknown> => (
  value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
);

const textValue = (value: unknown, fallback = '') => {
  if (typeof value === 'string') return value;
  if (value === undefined || value === null) return fallback;
  return String(value);
};

const numberValue = (value: unknown) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
};

const documentId = (document?: ReportDocumentLike | null) => textValue(
  document?.documentId || document?.document_id || document?.id,
);

const isReportDocumentPart = (part?: MessagePartLike) => {
  if (!part) return false;
  if (part.type === 'report-document') return true;
  return part.type === 'config' && textValue(part.metadata?.configKind) === 'report-document';
};

export const resolveReportDocumentPartContent = (part?: MessagePartLike) => {
  if (!part) return '';
  const directContent = textValue(part.content);
  if (directContent.trim()) return directContent;
  return textValue(part.metadata?.content);
};

/**
 * The backend stores generated report content in the report tables and keeps only a
 * reference in session extraData. The final chat part carries the hydrated document
 * metadata, so use it as an immediate UI synchronization source.
 */
export const extractLatestMessageReportDocument = (
  messages: readonly MessageLike[],
): ReportDocumentLike | undefined => {
  for (let messageIndex = messages.length - 1; messageIndex >= 0; messageIndex -= 1) {
    const message = messages[messageIndex];
    if (message?.sender !== 'ai') continue;
    const parts = message.parts || [];
    for (let partIndex = parts.length - 1; partIndex >= 0; partIndex -= 1) {
      const part = parts[partIndex];
      if (!isReportDocumentPart(part)
          || ['failed', 'conflict', 'blocked'].includes(textValue(part.status).toLowerCase())) {
        continue;
      }
      const metadata = asRecord(part.metadata);
      const id = textValue(metadata.documentId || metadata.document_id || metadata.id);
      const title = textValue(metadata.title || part.title, '报表文档');
      const content = resolveReportDocumentPartContent(part);
      return {
        ...metadata,
        id,
        documentId: id,
        title,
        name: textValue(metadata.name, title),
        format: textValue(metadata.format || part.language, 'markdown'),
        revision: numberValue(metadata.revision),
        version: textValue(metadata.version),
        status: textValue(metadata.status || part.status, 'generated'),
        content,
        contentHash: textValue(metadata.contentHash || metadata.content_hash),
        outline: Array.isArray(metadata.outline) ? metadata.outline : [],
        sourceRefs: Array.isArray(metadata.sourceRefs || metadata.source_refs)
          ? metadata.sourceRefs || metadata.source_refs
          : [],
      };
    }
  }
  return undefined;
};

export const mergeStoredAndMessageReportDocument = (
  stored: ReportDocumentLike,
  messageDocument?: ReportDocumentLike,
): ReportDocumentLike => {
  if (!messageDocument) return stored;
  if (!Object.keys(stored).length) return messageDocument;
  const storedId = documentId(stored);
  const messageId = documentId(messageDocument);
  if (storedId && messageId && storedId !== messageId) return stored;
  return {
    ...messageDocument,
    ...stored,
    content: textValue(stored.content) || textValue(messageDocument.content),
  };
};

export const shouldApplyIncomingReportDocument = (
  current: ReportDocumentLike | null | undefined,
  incoming: ReportDocumentLike | null | undefined,
  dirty: boolean,
) => {
  if (!incoming) return false;
  if (!current) return true;
  const currentId = documentId(current);
  const incomingId = documentId(incoming);
  if (currentId && incomingId && currentId !== incomingId) return true;
  const currentRevision = numberValue(current.revision);
  const incomingRevision = numberValue(incoming.revision);
  if (incomingRevision > currentRevision) return true;
  if (incomingRevision < currentRevision) return false;
  const currentFingerprint = textValue(current.contentHash) || textValue(current.content);
  const incomingFingerprint = textValue(incoming.contentHash) || textValue(incoming.content);
  if (currentFingerprint && currentFingerprint === incomingFingerprint) return false;
  return !dirty;
};
