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
const INTERACTIVE_PART_TYPES = new Set([
  'confirm',
  'info-steps',
  'metadata-config-decision',
  'data-push-service-decision',
  'visualization-chart-decision',
  'visualization-app-decision',
  'visualization-dashboard-decision',
  'mcp-approval',
]);

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
  const prompt = String(run.pendingInteraction?.prompt || '').trim();
  if (prompt) return prompt;
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

/**
 * Restore the pending MCP approval card from the run snapshot even when the
 * approval event has already fallen behind the replay cursor.
 */
export const resolveAgentRunPendingApprovalRequestId = (run?: AgentRun | null): string => {
  if (!run || run.status !== 'WAITING_APPROVAL') return '';
  if (run.pendingInteraction?.kind === 'mcp_approval') {
    return String(run.pendingInteraction.requestId || '').trim();
  }
  const pendingAction = String(run.pendingAction || '');
  if (!pendingAction) return '';
  try {
    const action = JSON.parse(pendingAction) as Record<string, unknown>;
    if (String(action.type || '') !== 'approval') return '';
    return String(action.requestId || '').trim();
  } catch {
    return '';
  }
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

const runIdFromPart = (part: ChatMessagePart): string => {
  const run = part.metadata?.run;
  return run && typeof run === 'object'
    ? String((run as AgentRun).runId || '')
    : '';
};

const isAgentRunPartForRun = (part: ChatMessagePart, runId: string) =>
  part.type === 'agent-run'
  && (part.id === `agent-run-${runId}` || runIdFromPart(part) === runId);

const isLegacyFinalPartForRun = (part: ChatMessagePart, runId: string) =>
  part.id === `agent-run-final-${runId}`;

const messageBelongsToRun = (message: ChatMessage, runId: string) =>
  message.runId === runId
  || (message.parts || []).some(part => isAgentRunPartForRun(part, runId));

const isInteractiveWorkflowMessage = (message: ChatMessage) =>
  (message.parts || []).some(part => INTERACTIVE_PART_TYPES.has(part.type));

const findLatestRunAiMessageIndex = (messages: ChatMessage[], runId: string) => {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (message.sender === 'ai' && messageBelongsToRun(message, runId)) return index;
  }
  return -1;
};

const findLatestServerFinalIndex = (messages: ChatMessage[], runId: string) => {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (message.sender !== 'ai' || !messageBelongsToRun(message, runId)) continue;
    const parts = message.parts || [];
    if (parts.some(part => isLegacyFinalPartForRun(part, runId))) continue;
    if (isInteractiveWorkflowMessage(message)) continue;
    const hasServerPart = parts.some(part => !isAgentRunPartForRun(part, runId));
    if (hasServerPart || Boolean(message.content?.trim())) return index;
  }
  return -1;
};

const removeSupersededSyntheticFinals = (
  messages: ChatMessage[],
  runId: string,
  serverFinalIndex: number,
) => {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (index === serverFinalIndex) continue;
    const message = messages[index];
    if (message.sender !== 'ai' || message.runId !== runId) continue;
    if (!(message.parts || []).some(part => isLegacyFinalPartForRun(part, runId))) continue;
    messages.splice(index, 1);
  }
};

const stripRunDecorationsFromOtherMessages = (
  messages: ChatMessage[],
  runId: string,
  anchorIndex: number,
  stripLegacyFinal: boolean,
) => {
  messages.forEach((message, index) => {
    if (index === anchorIndex || message.sender !== 'ai' || !messageBelongsToRun(message, runId)) {
      return;
    }
    const parts = message.parts || [];
    const nextParts = parts.filter(part =>
      !isAgentRunPartForRun(part, runId)
      && !(stripLegacyFinal && isLegacyFinalPartForRun(part, runId)),
    );
    if (nextParts.length !== parts.length) message.parts = nextParts;
  });
};

export const findAgentRunMessageIndex = (messages: ChatMessage[], runId: string) => {
  const cardIndex = messages.findIndex(message =>
    (message.parts || []).some(part => isAgentRunPartForRun(part, runId)),
  );
  if (cardIndex >= 0) return cardIndex;
  const aiIndex = findLatestRunAiMessageIndex(messages, runId);
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
  const runId = run.runId;
  let serverFinalIndex = run.status === 'SUCCEEDED'
    ? findLatestServerFinalIndex(messages, runId)
    : -1;
  const latestRunAiIndex = findLatestRunAiMessageIndex(messages, runId);
  if (serverFinalIndex >= 0
    && latestRunAiIndex >= 0
    && isInteractiveWorkflowMessage(messages[latestRunAiIndex])) {
    serverFinalIndex = -1;
  }
  if (serverFinalIndex >= 0) {
    removeSupersededSyntheticFinals(messages, runId, serverFinalIndex);
    serverFinalIndex = findLatestServerFinalIndex(messages, runId);
  }

  let index = serverFinalIndex >= 0
    ? serverFinalIndex
    : findLatestRunAiMessageIndex(messages, runId);
  const latestIsInteractive = index >= 0 && isInteractiveWorkflowMessage(messages[index]);
  const needsSeparateOutcomeMessage = latestIsInteractive
    && ((run.status === 'SUCCEEDED' && Boolean(run.finalAnswer))
      || run.status === 'FAILED');
  if (index >= 0 && needsSeparateOutcomeMessage) index = -1;
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
  const isServerFinal = serverFinalIndex >= 0 && index === serverFinalIndex;
  const contentParts = (message.parts || []).filter(part =>
    !isAgentRunPartForRun(part, runId)
    && !isLegacyFinalPartForRun(part, runId),
  );
  let appendedFinal = false;
  if (run.status === 'SUCCEEDED' && run.finalAnswer && !isServerFinal) {
    contentParts.push(finalAnswerPart(run));
    message.content = run.finalAnswer;
    appendedFinal = true;
  } else if (!contentParts.length && message.content?.trim()) {
    contentParts.push({
      id: `${message.id || `agent-run-content-${runId}`}-content`,
      type: 'markdown',
      content: message.content,
    });
  }

  stripRunDecorationsFromOtherMessages(
    messages,
    runId,
    index,
    run.status === 'SUCCEEDED',
  );
  message.parts = [
    agentRunPart(run, options.currentTool || null, options.activityEvents || []),
    ...contentParts,
  ];

  if (run.status === 'SUCCEEDED') {
    message.isError = false;
    if (!isServerFinal && (appendedFinal || !message.content?.trim())) {
      message.content = run.finalAnswer || '';
    }
  }
  if (run.status === 'FAILED') {
    message.isError = true;
    message.content = run.lastError || '智能体任务执行失败';
  }

  message.runId = message.runId || runId;
  message.loading = false;
  return index;
};
