import type {
  AgentRun,
  AgentRunInteraction,
  AgentRunInteractionResponse,
  AgentRunInteractionResponseParams,
  ChatMessage,
  ChatMessagePart,
} from '@/types/type-dih';

export type PersistedInteractionResponder = (
  runId: string,
  interactionId: string,
  params: AgentRunInteractionResponseParams,
) => Promise<AgentRunInteractionResponse>;

const interactionMismatch = () => new Error('当前交互已处理或已失效，请刷新后重试');

export const resolvePersistedCardInteraction = (
  run: AgentRun | null | undefined,
  message: ChatMessage,
  part: ChatMessagePart,
): AgentRunInteraction => {
  const runId = String(message.runId || '').trim();
  if (!runId || run?.runId !== runId) {
    throw new Error('未找到该卡片对应的当前智能体任务');
  }
  const interaction = run.pendingInteraction;
  if (!interaction
    || interaction.kind !== 'card_decision'
    || interaction.messageId !== message.id
    || interaction.partId !== part.id) {
    throw interactionMismatch();
  }
  return interaction;
};

export const resolvePersistedApprovalInteraction = (
  run: AgentRun | null | undefined,
  message: ChatMessage,
  part: ChatMessagePart,
): AgentRunInteraction => {
  const runId = String(message.runId || '').trim();
  if (!runId || run?.runId !== runId) {
    throw new Error('未找到该审批对应的当前智能体任务');
  }
  const interaction = run.pendingInteraction;
  const requestId = String(part.metadata?.requestId || part.id || '').trim();
  if (!interaction
    || interaction.kind !== 'mcp_approval'
    || !requestId
    || interaction.requestId !== requestId) {
    throw interactionMismatch();
  }
  return interaction;
};

export const executePersistedCardResponse = async ({
  run,
  message,
  part,
  params,
  respond,
}: {
  run: AgentRun | null | undefined;
  message: ChatMessage;
  part: ChatMessagePart;
  params: AgentRunInteractionResponseParams;
  respond: PersistedInteractionResponder;
}) => {
  const interaction = resolvePersistedCardInteraction(run, message, part);
  const response = await respond(run!.runId, interaction.interactionId, {
    ...params,
    stateRevision: interaction.stateRevision,
  });
  return { interaction, response };
};

export const executePersistedApprovalResponse = async ({
  run,
  message,
  part,
  params,
  respond,
}: {
  run: AgentRun | null | undefined;
  message: ChatMessage;
  part: ChatMessagePart;
  params: AgentRunInteractionResponseParams;
  respond: PersistedInteractionResponder;
}) => {
  const interaction = resolvePersistedApprovalInteraction(run, message, part);
  const response = await respond(run!.runId, interaction.interactionId, params);
  return { interaction, response };
};

export const isPersistedRunMessage = (message?: ChatMessage | null) =>
  Boolean(String(message?.runId || '').trim());
