import type { ChatMessage, ChatMessagePart } from '@/types/type-dih';

type WorkflowRevisionMap = Record<string, number>;

const workflowId = (part: ChatMessagePart): string => {
  const value = part.metadata?.workflowId;
  return typeof value === 'string' ? value.trim() : '';
};

export const workflowRevision = (part: ChatMessagePart): number | undefined => {
  const raw = part.metadata?.stateRevision;
  const value = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(value) ? value : undefined;
};

/**
 * The workflow checkpoint is stored per conversation. Its revision is the
 * authoritative pointer to the only card that may still mutate the workflow.
 */
export const currentWorkflowRevisions = (
  messages: Array<Pick<ChatMessage, 'parts'>>,
): WorkflowRevisionMap => {
  const revisions: WorkflowRevisionMap = {};
  messages.forEach(message => {
    (message.parts || []).forEach(part => {
      const id = workflowId(part);
      const revision = workflowRevision(part);
      if (!id || revision === undefined) return;
      revisions[id] = Math.max(revisions[id] ?? revision, revision);
    });
  });
  return revisions;
};

export const isCurrentWorkflowPart = (
  part: ChatMessagePart,
  revisions: WorkflowRevisionMap,
): boolean => {
  const id = workflowId(part);
  const current = revisions[id];
  const own = workflowRevision(part);
  if (!id || current === undefined || own === undefined) return true;
  return own >= current;
};

/**
 * Agent Run may persist the same pending query-plan card again after the user
 * makes an entity selection. Keep only the latest workflow revision visible.
 */
export const isSupersededVisualizationPlanCard = (
  part: ChatMessagePart,
  revisions: WorkflowRevisionMap,
): boolean => {
  if (part.type !== 'visualization-chart-decision') return false;
  const action = part.metadata?.action;
  if (typeof action !== 'string' || action !== 'visualization_chart.confirm_query_plan') {
    return false;
  }
  const status = typeof part.status === 'string' ? part.status.toLowerCase() : '';
  if (status !== 'pending') return false;

  const id = workflowId(part);
  const current = id ? revisions[id] : undefined;
  const own = workflowRevision(part);
  return current !== undefined && own !== undefined && own < current;
};
