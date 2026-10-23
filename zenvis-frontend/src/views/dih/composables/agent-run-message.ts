import type {
  AgentRun,
  AgentRunEvent,
  ChatMessage,
  ChatMessagePart,
} from '@/types/type-dih';

const LIVE_STATUSES = new Set(['QUEUED', 'RUNNING', 'CANCELING']);
const SUBSCRIBABLE_STATUSES = new Set([
  'QUEUED',
  'RUNNING',
  'WAITING_APPROVAL',
  'WAITING_USER',
  'CANCELING',
]);
const RESTORABLE_STATUSES = new Set([...SUBSCRIBABLE_STATUSES, 'PAUSED']);

export const isAgentRunLive = (status?: string) =>
  Boolean(status && LIVE_STATUSES.has(status));

export const isAgentRunSubscribable = (status?: string) =>
  Boolean(status && SUBSCRIBABLE_STATUSES.has(status));

export const isAgentRunRestorable = (status?: string) =>
  Boolean(status && RESTORABLE_STATUSES.has(status));

export const resolveAgentRunStepStatus = (runStatus: string | undefined, stepStatus?: string) => {
  const normalizedStep = String(stepStatus || 'PENDING').toUpperCase();
  if (runStatus === 'PAUSED' && normalizedStep === 'RUNNING') return 'PAUSED';
  return normalizedStep;
};

export const resolveAgentRunWaitingQuestion = (run?: AgentRun | null): string => {
  if (!run || run.status !== 'WAITING_USER') return '';
  const pendingAction = String(run.pendingAction || '');
  if (pendingAction) {
    try {
      const action = JSON.parse(pendingAction) as Record<string, unknown>;
      const question = String(action.question || '').trim();
      if (question) return question;
    } catch {
      // Fall back to the persisted reason for malformed or legacy pending actions.
    }
  }
  return String(run.pausedReason || '').trim();
};

type AgentRunMessageOptions = {
  currentTool?: Record<string, unknown> | null;
  activityEvents?: AgentRunEvent[];
  time?: string;
};

const agentRunPart = (
  run: AgentRun,
  currentTool: Record<string, unknown> | null,
  activityEvents: AgentRunEvent[],
): ChatMessagePart => ({
  id: `agent-run-${run.runId}`,
  type: 'agent-run',
  title: '智能体任务',
  status: run.status,
  metadata: {
    run,
    currentTool,
    activityEvents,
  },
});

const finalAnswerPart = (run: AgentRun): ChatMessagePart => ({
  id: `agent-run-final-${run.runId}`,
  type: 'markdown',
  content: run.finalAnswer || '',
});

export const findAgentRunMessageIndex = (messages: ChatMessage[], runId: string) => {
  const cardIndex = messages.findIndex(message =>
    (message.parts || []).some(part => part.type === 'agent-run'
      && part.id === `agent-run-${runId}`),
  );
  if (cardIndex >= 0) return cardIndex;
  const aiIndex = messages.findIndex(message =>
    message.sender === 'ai' && message.runId === runId,
  );
  if (aiIndex >= 0) return aiIndex;
  return messages.findIndex(message =>
    message.sender !== 'user' && message.runId === runId,
  );
};

export const upsertAgentRunMessage = (
  messages: ChatMessage[],
  run: AgentRun,
  options: AgentRunMessageOptions = {},
) => {
  if (!run?.runId) return -1;
  let index = findAgentRunMessageIndex(messages, run.runId);
  if (index < 0) {
    messages.push({
      sender: 'ai',
      content: '',
      time: options.time || '',
      runId: run.runId,
      loading: false,
      parts: [],
    });
    index = messages.length - 1;
  }

  const message = messages[index];
  const parts = [...(message.parts || [])];
  const runPartId = `agent-run-${run.runId}`;
  const partIndex = parts.findIndex(part => part.type === 'agent-run'
    && (part.id === runPartId
      || String((part.metadata?.run as AgentRun | undefined)?.runId || '') === run.runId));
  if (partIndex >= 0) {
    parts[partIndex] = agentRunPart(
      run, options.currentTool || null, options.activityEvents || []);
  } else {
    parts.unshift(agentRunPart(
      run, options.currentTool || null, options.activityEvents || []));
  }

  if (run.status === 'SUCCEEDED' && run.finalAnswer) {
    const finalIndex = parts.findIndex(part => part.id === `agent-run-final-${run.runId}`);
    if (finalIndex >= 0) {
      parts[finalIndex] = finalAnswerPart(run);
    } else {
      const markdownIndex = parts.findIndex(part => part.type === 'markdown'
        && Boolean(part.content?.trim()));
      if (markdownIndex >= 0) {
        parts[markdownIndex] = finalAnswerPart(run);
      } else {
        parts.push(finalAnswerPart(run));
      }
    }
    message.content = run.finalAnswer;
    message.isError = false;
  }
  if (run.status === 'FAILED') {
    message.isError = true;
    message.content = run.lastError || '智能体任务执行失败';
  }

  message.parts = parts;
  message.runId = message.runId || run.runId;
  message.loading = false;
  return index;
};
