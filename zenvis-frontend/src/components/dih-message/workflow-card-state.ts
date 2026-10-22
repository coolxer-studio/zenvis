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
