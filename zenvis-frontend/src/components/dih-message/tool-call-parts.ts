import type { ChatMessagePart } from '@/types/type-dih';

export type ToolCallStatus = 'running' | 'succeeded' | 'failed';

export type ToolCallEventData = {
  callId: string;
  toolName: string;
  status: ToolCallStatus;
  arguments?: string;
  result?: string;
  error?: string;
  durationMillis?: number;
  startedAt?: string;
  completedAt?: string;
};

const TOOL_CALL_TYPE = 'tool-call';
const STRUCTURED_PART_TYPES = new Set(['mcp-approval', TOOL_CALL_TYPE, 'rag-search']);
const LEGACY_TOOL_HEADER = /\*\*MCP调用(开始|成功|失败)：\*\*\s*`([^`]*)`(?:，耗时\s*(\d+)ms)?/g;
const TOOL_CONTENT_KEYS = new Set(['text', 'type', 'mimeType', 'mime_type']);

type JsonParseResult = {
  parsed: boolean;
  value: unknown;
};

const parseJsonText = (value: string): JsonParseResult => {
  const trimmed = value.trim();
  if (!trimmed || (!trimmed.startsWith('{') && !trimmed.startsWith('['))) {
    return { parsed: false, value };
  }
  try {
    return { parsed: true, value: JSON.parse(trimmed) };
  } catch {
    return { parsed: false, value };
  }
};

const isTextContentBlock = (value: unknown): value is Record<string, unknown> =>
  Boolean(
    value
    && typeof value === 'object'
    && !Array.isArray(value)
    && typeof (value as Record<string, unknown>).text === 'string'
    && Object.keys(value as Record<string, unknown>).every(key => TOOL_CONTENT_KEYS.has(key)),
  );

const unwrapToolContent = (value: unknown, depth = 0): unknown => {
  if (depth >= 6) return value;
  if (isTextContentBlock(value)) {
    const parsed = parseJsonText(String(value.text));
    return parsed.parsed ? unwrapToolContent(parsed.value, depth + 1) : value.text;
  }
  if (Array.isArray(value)) {
    const normalized = value.map(item => unwrapToolContent(item, depth + 1));
    if (value.every(isTextContentBlock)) {
      return value.length === 1 ? normalized[0] : normalized;
    }
    return normalized;
  }
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>;
    const content = record.content;
    if (Array.isArray(content) && content.every(isTextContentBlock)) {
      return unwrapToolContent(content, depth + 1);
    }
  }
  return value;
};

/**
 * Unwraps MCP text content blocks and pretty-prints nested JSON payloads.
 * Plain text and malformed JSON are returned unchanged.
 */
export const formatToolPayload = (value: unknown): string => {
  if (value === undefined || value === null || value === '') return '';
  const parsed = typeof value === 'string' ? parseJsonText(value) : { parsed: false, value };
  const normalized = unwrapToolContent(parsed.parsed ? parsed.value : value);
  if (typeof normalized === 'string') return normalized;
  try {
    return JSON.stringify(normalized, null, 2);
  } catch {
    return String(normalized);
  }
};

const stringifyValue = (value: unknown): string => {
  if (value === undefined || value === null) return '';
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
};

const firstText = (...values: unknown[]): string => {
  for (const value of values) {
    const text = stringifyValue(value);
    if (text.trim()) return text;
  }
  return '';
};

export const normalizeToolCallStatus = (status: unknown): ToolCallStatus => {
  const value = String(status || '').toLowerCase();
  if (value === 'succeeded' || value === 'success' || value === 'completed') return 'succeeded';
  if (value === 'failed' || value === 'error') return 'failed';
  return 'running';
};

export const isToolCallPart = (part?: ChatMessagePart | null): boolean =>
  part?.type === TOOL_CALL_TYPE;

export const isStructuredStreamPart = (part?: ChatMessagePart | null): boolean =>
  Boolean(part && STRUCTURED_PART_TYPES.has(part.type));

export const toolCallPartFromEvent = (
  raw: unknown,
  existing?: ChatMessagePart,
): ChatMessagePart | null => {
  if (!raw || typeof raw !== 'object') return null;
  const source = raw as Record<string, unknown>;
  const toolName = firstText(source.toolName, source.tool_name) || '未知工具';
  const callId =
    firstText(source.callId, source.call_id) ||
    existing?.id ||
    `tool-${toolName.replace(/\s+/g, '-')}`;
  const status = normalizeToolCallStatus(source.status);
  const argumentsText = firstText(source.arguments, existing?.metadata?.arguments);
  const resultText = firstText(source.result, existing?.metadata?.result);
  const errorText = firstText(source.error, existing?.metadata?.error);
  const durationValue = Number(source.durationMillis ?? source.duration_millis);
  const durationMillis = Number.isFinite(durationValue) && durationValue >= 0
    ? durationValue
    : existing?.metadata?.durationMillis;

  return {
    id: callId,
    type: TOOL_CALL_TYPE,
    title: 'MCP 工具调用',
    content: toolName,
    status,
    metadata: {
      ...existing?.metadata,
      callId,
      toolName,
      status,
      arguments: argumentsText,
      result: resultText,
      error: errorText,
      durationMillis,
      startedAt: firstText(source.startedAt, source.started_at, existing?.metadata?.startedAt),
      completedAt: firstText(source.completedAt, source.completed_at, existing?.metadata?.completedAt),
    },
  };
};

export const upsertToolCallPart = (
  parts: ChatMessagePart[] | undefined,
  rawEvent: unknown,
): ChatMessagePart[] => {
  const nextParts = parts ? [...parts] : [];
  const fallbackId = firstText(
    (rawEvent as Record<string, unknown> | undefined)?.callId,
    (rawEvent as Record<string, unknown> | undefined)?.call_id,
  );
  const existingIndex = nextParts.findIndex(
    part => Boolean(fallbackId) && isToolCallPart(part) && part.id === fallbackId,
  );
  const nextPart = toolCallPartFromEvent(
    rawEvent,
    existingIndex >= 0 ? nextParts[existingIndex] : undefined,
  );
  if (!nextPart) return nextParts;
  if (existingIndex >= 0) {
    nextParts[existingIndex] = nextPart;
  } else {
    nextParts.push(nextPart);
  }
  return nextParts;
};

const mergeSpecialPart = (
  finalPart: ChatMessagePart,
  livePart?: ChatMessagePart,
): ChatMessagePart => {
  if (!livePart) return finalPart;
  return {
    ...livePart,
    ...finalPart,
    metadata: {
      ...livePart.metadata,
      ...finalPart.metadata,
      deciding: livePart.metadata?.deciding ?? finalPart.metadata?.deciding,
      decisionInFlight:
        livePart.metadata?.decisionInFlight ?? finalPart.metadata?.decisionInFlight,
    },
  };
};

/**
 * Merge streamed approval/tool state with the authoritative done payload without
 * dropping transient UI state or an event that has not reached persistence yet.
 */
export const mergeLiveStructuredParts = (
  liveParts: ChatMessagePart[] | undefined,
  finalParts: ChatMessagePart[] | undefined,
): ChatMessagePart[] | undefined => {
  const finalList = finalParts ? [...finalParts] : undefined;
  const liveStructured = (liveParts || []).filter(isStructuredStreamPart);
  if (!liveStructured.length) return finalList;
  if (!finalList?.length) return liveParts;
  if (!finalList.some(isStructuredStreamPart)) {
    const merged: ChatMessagePart[] = [];
    let insertedFinalContent = false;
    for (const livePart of liveParts || []) {
      if (isStructuredStreamPart(livePart)) {
        merged.push(livePart);
      } else if (!insertedFinalContent) {
        merged.push(...finalList);
        insertedFinalContent = true;
      }
    }
    if (!insertedFinalContent) merged.push(...finalList);
    return merged;
  }

  const liveById = new Map(liveStructured.map(part => [part.id, part]));
  const finalIds = new Set(finalList.map(part => part.id).filter(Boolean));
  const merged = finalList.map(part => {
    if (!isStructuredStreamPart(part)) return part;
    return mergeSpecialPart(part, liveById.get(part.id));
  });
  for (const livePart of liveStructured) {
    if (livePart.id && !finalIds.has(livePart.id)) merged.push(livePart);
  }
  return merged;
};

const pushMarkdownPart = (parts: ChatMessagePart[], content: string, idSeed: string) => {
  const normalized = content.trim();
  if (!normalized) return;
  const last = parts.at(-1);
  if (last?.type === 'markdown') {
    last.content = `${last.content || ''}\n\n${normalized}`.trim();
    return;
  }
  parts.push({
    id: `${idSeed}-markdown-${parts.length + 1}`,
    type: 'markdown',
    content: normalized,
  });
};

const legacyStatus = (value: string): ToolCallStatus => {
  if (value === '开始') return 'running';
  if (value === '成功') return 'succeeded';
  return 'failed';
};

const legacyToolPart = (
  toolName: string,
  status: ToolCallStatus,
  durationMillis: number | undefined,
  id: string,
): ChatMessagePart => ({
  id,
  type: TOOL_CALL_TYPE,
  title: 'MCP 工具调用',
  content: toolName,
  status,
  metadata: {
    callId: id,
    toolName,
    status,
    arguments: '',
    result: '',
    error: '',
    durationMillis,
  },
});

/**
 * Converts legacy MCP markdown/code part sequences into in-memory tool cards.
 * Persisted messages are never rewritten.
 */
export const collapseLegacyToolCallParts = (
  parts: ChatMessagePart[],
  messageId = 'message',
): ChatMessagePart[] => {
  const hasLegacyHeader = parts.some(
    part => part.type === 'markdown' && /MCP调用(开始|成功|失败)：/.test(part.content || ''),
  );
  if (!hasLegacyHeader) return parts;

  const result: ChatMessagePart[] = [];
  const toolParts: ChatMessagePart[] = [];
  let currentTool: ChatMessagePart | null = null;
  let pendingField: 'arguments' | 'result' | 'error' | null = null;
  let sequence = 0;

  const nextId = () => `${messageId}-legacy-tool-${++sequence}`;

  const findToolToUpdate = (toolName: string) => {
    const candidate = [...toolParts].reverse().find(part => {
      const metadata = part.metadata || {};
      return String(metadata.toolName || '') === toolName && part.status === 'running';
    });
    if (candidate) return candidate;
    if (currentTool && currentTool.metadata?.toolName === toolName) return currentTool;
    const created = legacyToolPart(toolName, 'running', undefined, nextId());
    toolParts.push(created);
    result.push(created);
    return created;
  };

  const handleHeader = (statusText: string, toolName: string, duration?: string) => {
    const status = legacyStatus(statusText);
    const durationMillis = duration ? Number(duration) : undefined;
    if (status === 'running') {
      const part = legacyToolPart(toolName, status, undefined, nextId());
      toolParts.push(part);
      result.push(part);
      currentTool = part;
      pendingField = null;
      return;
    }
    const part = findToolToUpdate(toolName);
    part.status = status;
    part.metadata = { ...part.metadata, status, durationMillis };
    currentTool = part;
    pendingField = null;
  };

  const consumeSegment = (segment: string) => {
    if (!segment) return;
    const nextField = /错误信息[：:]/.test(segment)
      ? 'error'
      : /返回结果[：:]/.test(segment)
        ? 'result'
        : /调用参数[：:]/.test(segment)
          ? 'arguments'
          : null;
    if (nextField) pendingField = nextField;
    const residual = segment
      .replace(/(?:调用参数|返回结果|错误信息)[：:]/g, '')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
    if (residual) pushMarkdownPart(result, residual, messageId);
  };

  for (const part of parts) {
    if (part.type === 'code') {
      const activeTool = currentTool as ChatMessagePart | null;
      if (activeTool && pendingField) {
        activeTool.metadata = {
          ...activeTool.metadata,
          [pendingField]: part.content || '',
        };
        pendingField = null;
        continue;
      }
      result.push(part);
      continue;
    }
    if (part.type !== 'markdown') {
      result.push(part);
      continue;
    }

    const content = part.content || '';
    const matches = [...content.matchAll(LEGACY_TOOL_HEADER)];
    if (!matches.length) {
      consumeSegment(content);
      continue;
    }
    let cursor = 0;
    for (const match of matches) {
      const index = match.index ?? 0;
      consumeSegment(content.slice(cursor, index));
      handleHeader(match[1], match[2], match[3]);
      cursor = index + match[0].length;
    }
    consumeSegment(content.slice(cursor));
  }

  return result;
};
