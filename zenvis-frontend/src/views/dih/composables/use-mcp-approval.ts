import type { Ref } from 'vue';
import { ElMessage } from 'element-plus';
import { DihService } from '@/service/api';
import type {
  ChatMessage,
  ChatMessagePart,
  McpApprovalData,
  McpApprovalDecision,
} from '@/types/type-dih';

export type McpApprovalDecisionPayload = {
  part: ChatMessagePart;
  decision: McpApprovalDecision;
};

type UseMcpApprovalOptions = {
  messages: Ref<ChatMessage[]>;
  createMarkdownParts: (content: string) => ChatMessagePart[];
};

export const useMcpApproval = ({
  messages,
  createMarkdownParts,
}: UseMcpApprovalOptions) => {
  const mcpApprovalPart = (data: McpApprovalData): ChatMessagePart => ({
    id: data.requestId,
    type: 'mcp-approval',
    title: `MCP 工具审批：${data.toolName || data.toolKey || '未知工具'}`,
    content: data.description || '该工具需要你的明确许可后才能执行。',
    status: String(data.status || 'pending').toLowerCase(),
    metadata: {
      ...data,
      deciding: false,
      decisionInFlight: '',
    },
  });

  const isMcpApprovalPart = (part: ChatMessagePart) => part.type === 'mcp-approval';

  const upsertMcpApprovalPart = (
    message: ChatMessage,
    data: McpApprovalData,
    accumulatedContent: string,
  ) => {
    if (!data.requestId) return;
    const nextPart = mcpApprovalPart(data);
    const parts = message.parts
      ? [...message.parts]
      : (accumulatedContent ? createMarkdownParts(accumulatedContent) : []);
    const index = parts.findIndex(part => isMcpApprovalPart(part) && part.id === data.requestId);
    if (index >= 0) {
      parts[index] = {
        ...parts[index],
        ...nextPart,
        metadata: {
          ...parts[index].metadata,
          ...nextPart.metadata,
        },
      };
    } else {
      parts.push(nextPart);
    }
    message.parts = parts;
    message.loading = false;
  };

  const mergeFinalApprovalParts = (
    liveParts: ChatMessagePart[] | undefined,
    finalParts: ChatMessagePart[] | undefined,
  ) => {
    if (!liveParts?.some(isMcpApprovalPart)) return finalParts;
    const liveApprovalById = new Map(
      liveParts.filter(isMcpApprovalPart).map(part => [part.id, part]),
    );
    const finalApprovalById = new Map(
      (finalParts || []).filter(isMcpApprovalPart).map(part => [part.id, part]),
    );
    if (finalApprovalById.size > 0) {
      return (finalParts || []).map(part => {
        if (!isMcpApprovalPart(part)) return part;
        const livePart = liveApprovalById.get(part.id);
        if (!livePart) return part;
        return {
          ...livePart,
          ...part,
          metadata: {
            ...livePart.metadata,
            ...part.metadata,
          },
        };
      });
    }
    const finalContentParts = (finalParts || []).filter(part => !isMcpApprovalPart(part));
    const liveContentParts = liveParts.filter(part => !isMcpApprovalPart(part));
    const finalIsPlainContent = finalContentParts.every(part => part.type === 'markdown' || part.type === 'thinking');
    if (!finalIsPlainContent) {
      return [
        ...liveParts.filter(isMcpApprovalPart).map(part => finalApprovalById.get(part.id) || part),
        ...finalContentParts,
      ];
    }
    return liveParts
      .map(part => isMcpApprovalPart(part) ? (finalApprovalById.get(part.id) || part) : part)
      .concat(finalContentParts.length && !liveContentParts.length ? finalContentParts : []);
  };

  const handleMcpApprovalDecision = async (
    message: ChatMessage,
    payload: McpApprovalDecisionPayload,
  ) => {
    const requestId = payload.part.id || String(payload.part.metadata?.requestId || '');
    if (!requestId || payload.part.status !== 'pending' || payload.part.metadata?.deciding === true) return;
    payload.part.metadata = {
      ...payload.part.metadata,
      deciding: true,
      decisionInFlight: payload.decision,
    };
    try {
      const approval = await DihService.decideMcpApproval(requestId, { decision: payload.decision });
      upsertMcpApprovalPart(message, approval, message.content || '');
    } catch (error) {
      payload.part.metadata = { ...payload.part.metadata, deciding: false, decisionInFlight: '' };
      ElMessage.error(error instanceof Error ? error.message : '审批操作失败，请稍后重试');
    }
  };

  const rejectPendingMcpApprovals = async (messageIndex: number | null, comment: string) => {
    if (messageIndex === null || !messages.value[messageIndex]) return;
    const pendingParts = (messages.value[messageIndex].parts || [])
      .filter(part => isMcpApprovalPart(part) && part.status === 'pending' && part.id);
    await Promise.allSettled(pendingParts.map(async part => {
      const approval = await DihService.decideMcpApproval(part.id!, { decision: 'rejected', comment });
      upsertMcpApprovalPart(
        messages.value[messageIndex],
        approval,
        messages.value[messageIndex].content || '',
      );
    }));
  };

  return {
    isMcpApprovalPart,
    upsertMcpApprovalPart,
    mergeFinalApprovalParts,
    handleMcpApprovalDecision,
    rejectPendingMcpApprovals,
  };
};
