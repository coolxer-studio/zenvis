import type {
  AgentRunActivity,
  AgentRunActivityKind,
  AgentRunEvent,
} from '@/types/type-dih';

export const AGENT_RUN_ACTIVITY_EVENT_TYPES = [
  'rag.search',
  'tool.started',
  'tool.succeeded',
  'tool.failed',
  'tool.approval_required',
  'tool.approval_updated',
  'tool.approval_executed',
  'tool.approval_unavailable',
  'context.compacted',
] as const;

const ACTIVITY_EVENT_TYPES = new Set<string>(AGENT_RUN_ACTIVITY_EVENT_TYPES);

const text = (value: unknown): string => value === undefined || value === null
  ? '' : String(value);

export const isAgentRunActivityEvent = (event: AgentRunEvent): boolean =>
  ACTIVITY_EVENT_TYPES.has(event.type);

const statusForEvent = (event: AgentRunEvent): string => {
  const data = event.data || {};
  if (event.type === 'tool.started') return 'running';
  if (event.type === 'tool.succeeded') return 'succeeded';
  if (event.type === 'tool.failed') return 'failed';
  if (event.type === 'tool.approval_unavailable') return 'failed';
  if (event.type === 'tool.approval_required') return 'waiting';
  if (event.type === 'context.compacted') return 'completed';
  return text(data.status) || (event.type === 'tool.approval_executed'
    ? 'succeeded' : event.type.startsWith('tool.') ? 'running' : 'completed');
};

const identity = (event: AgentRunEvent): { id: string; kind: AgentRunActivityKind } | null => {
  const data = event.data || {};
  if (event.type === 'rag.search') {
    return {
      id: text(data.searchId ?? data.search_id) || `rag-${event.sequence}`,
      kind: 'rag',
    };
  }
  if (event.type === 'context.compacted') {
    return {
      id: text(data.compactionId ?? data.compaction_id) || `compaction-${event.sequence}`,
      kind: 'compaction',
    };
  }
  if (event.type.startsWith('tool.')) {
    const callId = text(data.callId ?? data.call_id);
    const requestId = text(data.requestId ?? data.request_id);
    const fallback = event.type.startsWith('tool.approval')
      ? `approval-${requestId || event.sequence}`
      : `tool-${event.sequence}`;
    return { id: callId || fallback, kind: 'tool' };
  }
  return null;
};

export const buildAgentRunActivities = (events: AgentRunEvent[]): AgentRunActivity[] => {
  const byId = new Map<string, AgentRunActivity>();
  const seenSequences = new Set<number>();

  for (const event of [...events].sort((left, right) => left.sequence - right.sequence)) {
    if (!isAgentRunActivityEvent(event) || seenSequences.has(event.sequence)) continue;
    seenSequences.add(event.sequence);
    const key = identity(event);
    if (!key) continue;
    const existing = byId.get(key.id);
    const data = { ...(event.data || {}) };
    if (!existing) {
      byId.set(key.id, {
        id: key.id,
        kind: key.kind,
        firstSequence: event.sequence,
        lastSequence: event.sequence,
        stepId: event.step_id,
        createdAt: event.created_at,
        status: statusForEvent(event),
        eventTypes: [event.type],
        data,
      });
      continue;
    }
    existing.data = { ...existing.data, ...data };
    existing.lastSequence = Math.max(existing.lastSequence, event.sequence);
    existing.status = statusForEvent(event);
    if (!existing.eventTypes.includes(event.type)) existing.eventTypes.push(event.type);
    if (!existing.stepId && event.step_id) existing.stepId = event.step_id;
  }

  return [...byId.values()].sort((left, right) =>
    left.firstSequence - right.firstSequence || left.id.localeCompare(right.id));
};

export type AgentRunActivitySummary = {
  ragCount: number;
  ragHits: number;
  toolCount: number;
  toolFailures: number;
  compactionCount: number;
  savedTokens: number;
};

export const summarizeAgentRunActivities = (
  activities: AgentRunActivity[],
): AgentRunActivitySummary => activities.reduce<AgentRunActivitySummary>((summary, activity) => {
  if (activity.kind === 'rag') {
    summary.ragCount += 1;
    const hitCount = Number(activity.data.hitCount ?? activity.data.hit_count);
    const results = Array.isArray(activity.data.results) ? activity.data.results.length : 0;
    summary.ragHits += Number.isFinite(hitCount) ? hitCount : results;
  } else if (activity.kind === 'tool') {
    if (activity.eventTypes.some(type => type.startsWith('tool.')
      && !type.startsWith('tool.approval'))) {
      summary.toolCount += 1;
    }
    if (activity.status === 'failed') summary.toolFailures += 1;
  } else if (activity.kind === 'compaction') {
    summary.compactionCount += 1;
    const saved = Number(activity.data.savedTokens ?? activity.data.saved_tokens);
    if (Number.isFinite(saved) && saved > 0) summary.savedTokens += saved;
  }
  return summary;
}, {
  ragCount: 0,
  ragHits: 0,
  toolCount: 0,
  toolFailures: 0,
  compactionCount: 0,
  savedTokens: 0,
});
