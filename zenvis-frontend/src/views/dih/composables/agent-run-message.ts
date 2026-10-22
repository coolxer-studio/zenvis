import type { AgentRun, ChatMessage, ChatMessagePart } from '@/types/type-dih';

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

type AgentRunMessageOptions = {
  currentTool?: Record<string, unknown> | null;
  time?: string;
};

const agentRunPart = (
  run: AgentRun,
  currentTool: Record<string, unknown> | null,
): ChatMessagePart => ({
  id: `agent-run-${run.runId}`,
  type: 'agent-run',
  title: '智能体任务',
  status: run.status,
  metadata: {
    run,
    currentTool,
  },
});

const finalAnswerPart = (run: AgentRun): ChatMessagePart => ({
  id: `agent-run-final-${run.runId}`,
  type: 'markdown',
  content: run.finalAnswer || '',
});

export const findAgentRunMessageIndex = (messages: ChatMessage[], runId: string) =>
  messages.findIndex(message =>
    message.runId === runId
    || (message.parts || []).some(part => part.type === 'agent-run'
      && part.id === `agent-run-${runId}`),
  );

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
  const partIndex = parts.findIndex(part => part.type === 'agent-run');
  if (partIndex >= 0) {
    parts[partIndex] = agentRunPart(run, options.currentTool || null);
  } else {
    parts.unshift(agentRunPart(run, options.currentTool || null));
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
