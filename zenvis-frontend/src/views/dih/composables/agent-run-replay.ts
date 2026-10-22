import type { AgentRun, AgentRunEvent } from '@/types/type-dih';

export const AGENT_RUN_CURSOR_PREFIX = 'dih:agent-run:cursor:';

type CursorStorage = Pick<Storage, 'getItem' | 'setItem'>;

const browserStorage = (): CursorStorage | null => {
  try {
    return typeof window === 'undefined' ? null : window.localStorage;
  } catch {
    return null;
  }
};

export const readAgentRunCursor = (
  runId: string,
  storage: CursorStorage | null = browserStorage(),
) => {
  if (!runId || !storage) return 0;
  try {
    return Math.max(0, Number(storage.getItem(`${AGENT_RUN_CURSOR_PREFIX}${runId}`) || 0) || 0);
  } catch {
    return 0;
  }
};

export const writeAgentRunCursor = (
  runId: string,
  sequence: number,
  storage: CursorStorage | null = browserStorage(),
) => {
  if (!runId || !storage || !Number.isFinite(sequence)) return;
  try {
    storage.setItem(`${AGENT_RUN_CURSOR_PREFIX}${runId}`, String(Math.max(0, sequence)));
  } catch {
    // Cursor persistence is an optimization; server sequence still deduplicates this session.
  }
};

export const resolveAgentRunReplayCursor = (
  run: AgentRun | null,
  runId: string,
  requestedAfter = 0,
  storedSequence = readAgentRunCursor(runId),
) => Math.max(
  0,
  Number(requestedAfter || 0),
  Number(storedSequence || 0),
  run?.runId === runId ? Number(run.lastEventSequence || 0) : 0,
);

export const shouldAcceptAgentRunEvent = (
  lastSequence: number,
  event: AgentRunEvent | null | undefined,
) => Boolean(event && Number.isFinite(event.sequence) && event.sequence > lastSequence);
