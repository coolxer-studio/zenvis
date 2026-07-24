import { computed, nextTick, onUnmounted, ref } from 'vue';
import type { Ref } from 'vue';
import type { Router } from 'vue-router';
import { ElMessage } from 'element-plus';
import { DihService } from '@/service/api';
import type {
  ChatAttachment,
  ChatMessage,
  ChatMessagePart,
  ChatSession,
  McpApprovalData,
} from '@/types/type-dih';
import { generateUUID } from '@/utils/util-common';
import { getCurrentFormattedDate } from '@/utils/util-time';
import {
  NEW_CHAT_CREATED_EVENT,
  emitDihEvent,
} from '../events';

export type SendMessageOptions = {
  content?: string;
  requestContent?: string;
};

type UseChatStreamOptions = {
  router: Router;
  messages: Ref<ChatMessage[]>;
  inputMessage: Ref<string>;
  pendingAttachments: Ref<ChatAttachment[]>;
  isUploadingAttachment: Ref<boolean>;
  isDeepThinking: Ref<boolean>;
  selectedModel: Ref<string>;
  isChatUnavailable: Ref<boolean>;
  chatSessionId: Ref<string>;
  chatSessionRecordId: Ref<string>;
  chatSessionType: Ref<string>;
  chatSessionExtraData: Ref<string>;
  scrollToBottom: () => Promise<void>;
  processMessageFormat: (message: ChatMessage) => void;
  refreshChatSessionExtraData: () => Promise<void>;
  isMcpApprovalPart: (part: ChatMessagePart) => boolean;
  upsertMcpApprovalPart: (message: ChatMessage, data: McpApprovalData, accumulatedContent: string) => void;
  mergeFinalApprovalParts: (
    liveParts: ChatMessagePart[] | undefined,
    finalParts: ChatMessagePart[] | undefined,
  ) => ChatMessagePart[] | undefined;
  rejectPendingMcpApprovals: (messageIndex: number | null, comment: string) => Promise<void>;
};

export const useChatStream = ({
  router,
  messages,
  inputMessage,
  pendingAttachments,
  isUploadingAttachment,
  isDeepThinking,
  selectedModel,
  isChatUnavailable,
  chatSessionId,
  chatSessionRecordId,
  chatSessionType,
  chatSessionExtraData,
  scrollToBottom,
  processMessageFormat,
  refreshChatSessionExtraData,
  isMcpApprovalPart,
  upsertMcpApprovalPart,
  mergeFinalApprovalParts,
  rejectPendingMcpApprovals,
}: UseChatStreamOptions) => {
  const isStreamingResponse = ref(false);
  const currentChatAbortController = ref<AbortController | null>(null);
  const currentStreamingMessageIndex = ref<number | null>(null);
  const isUserStoppingChat = ref(false);
  const canSendMessage = computed(() => {
    return !isChatUnavailable.value
      && !isUploadingAttachment.value
      && !isStreamingResponse.value
      && (inputMessage.value.trim().length > 0 || pendingAttachments.value.length > 0);
  });

  const createMarkdownParts = (content: string): ChatMessagePart[] => [
    {
      id: generateUUID(),
      type: 'markdown',
      content,
    },
  ];

  const createThinkingPart = (status: 'running' | 'completed' = 'running'): ChatMessagePart => ({
    id: generateUUID(),
    type: 'thinking',
    title: '思考过程',
    content: status === 'running' ? '正在深度思考，请稍候...' : '已完成深度思考。',
    status,
  });

  const hasThinkTag = (content: string) => content.includes('<think>');

  const createDeepThinkingStreamingParts = (content: string): ChatMessagePart[] => {
    const parts: ChatMessagePart[] = [createThinkingPart('running')];
    if (content.trim()) {
      parts.push({
        id: generateUUID(),
        type: 'markdown',
        content,
      });
    }
    return parts;
  };

  const appendStreamingDelta = (message: ChatMessage, delta: string, accumulatedContent: string) => {
    const containsApproval = message.parts?.some(isMcpApprovalPart) === true;
    if (!containsApproval) {
      message.parts = isDeepThinking.value && !hasThinkTag(accumulatedContent)
        ? createDeepThinkingStreamingParts(accumulatedContent)
        : undefined;
      return;
    }
    if (!delta) return;
    const parts = message.parts ? [...message.parts] : [];
    const last = parts.at(-1);
    if (last?.type === 'markdown') {
      last.content = `${last.content || ''}${delta}`;
    } else {
      parts.push({ id: generateUUID(), type: 'markdown', content: delta });
    }
    message.parts = parts;
  };

  const markMessageStopped = (messageIndex: number | null) => {
    if (messageIndex === null || !messages.value[messageIndex]) {
      return;
    }
    const message = messages.value[messageIndex];
    message.loading = false;
    message.isError = false;
    if (message.content?.includes('[已停止生成]')) {
      return;
    }
    message.content = message.content?.trim()
      ? `${message.content}\n\n[已停止生成]`
      : '已停止生成';
    const approvalParts = (message.parts || []).filter(isMcpApprovalPart);
    message.parts = approvalParts.length
      ? [...approvalParts, ...createMarkdownParts(message.content)]
      : undefined;
  };

  const stopCurrentChat = async () => {
    if (!currentChatAbortController.value || !isStreamingResponse.value) {
      return;
    }
    isUserStoppingChat.value = true;
    await rejectPendingMcpApprovals(currentStreamingMessageIndex.value, '用户停止生成');
    currentChatAbortController.value.abort();
    markMessageStopped(currentStreamingMessageIndex.value);
    ElMessage.info('已停止生成');
    void scrollToBottom();
  };

  const sendMessage = async (options: SendMessageOptions = {}) => {
    if (isChatUnavailable.value) {
      ElMessage.warning('当前 Skill 已停用或不存在，请选择其他可用技能');
      return;
    }
    const explicitMessage = options.content?.trim();
    const explicitRequestMessage = options.requestContent?.trim();
    const canSend = explicitMessage
      ? !isUploadingAttachment.value && !isStreamingResponse.value
      : canSendMessage.value;

    if (canSend) {
      const currentInputMessage = inputMessage.value.trim();
      const requestMessage = explicitRequestMessage || explicitMessage || currentInputMessage;
      const messageAttachments = explicitMessage ? [] : pendingAttachments.value.slice();
      const displayMessage = explicitMessage || currentInputMessage || '请分析上传的附件内容。';
      if (!explicitMessage) {
        inputMessage.value = '';
        pendingAttachments.value = [];
      }
      messages.value.push({
        sender: 'user',
        content: displayMessage,
        time: getCurrentFormattedDate(),
        attachments: messageAttachments,
      });

      const aiMessageIndex = messages.value.length;
      messages.value.push({
        sender: 'ai',
        content: '',
        time: getCurrentFormattedDate(),
        loading: true,
        parts: isDeepThinking.value ? [createThinkingPart('running')] : undefined,
      });

      void scrollToBottom();

      const abortController = new AbortController();
      currentChatAbortController.value = abortController;
      currentStreamingMessageIndex.value = aiMessageIndex;
      isUserStoppingChat.value = false;
      isStreamingResponse.value = true;

      try {
        let accumulatedContent = '';
        const streamOk = await DihService.chatEvents({
          type: chatSessionType.value,
          message: requestMessage,
          model: selectedModel.value,
          deep_think: chatSessionType.value === 'ask' && isDeepThinking.value,
          chat_id: chatSessionId.value,
          attachments: messageAttachments,
        }, async event => {
          if (event.event === 'delta') {
            const delta = event.content || '';
            accumulatedContent += delta;
            if (messages.value[aiMessageIndex].loading) {
              messages.value[aiMessageIndex].loading = false;
            }
            messages.value[aiMessageIndex].content = accumulatedContent;
            appendStreamingDelta(messages.value[aiMessageIndex], delta, accumulatedContent);
            await nextTick();
            void scrollToBottom();
            return;
          }

          if ((event.event === 'approval_required' || event.event === 'approval_updated') && event.data) {
            upsertMcpApprovalPart(
              messages.value[aiMessageIndex],
              event.data as McpApprovalData,
              accumulatedContent,
            );
            await nextTick();
            void scrollToBottom();
            return;
          }

          if (event.event === 'done') {
            if (event.message && typeof event.message !== 'string') {
              const liveParts = messages.value[aiMessageIndex].parts;
              messages.value[aiMessageIndex] = {
                ...event.message,
                parts: mergeFinalApprovalParts(liveParts, event.message.parts),
                loading: false,
              };
              processMessageFormat(messages.value[aiMessageIndex]);
              await refreshChatSessionExtraData();
            } else {
              messages.value[aiMessageIndex].loading = false;
              messages.value[aiMessageIndex].content = accumulatedContent;
            }
            await nextTick();
            void scrollToBottom();
            return;
          }

          if (event.event === 'error') {
            messages.value[aiMessageIndex].loading = false;
            messages.value[aiMessageIndex].isError = true;
            messages.value[aiMessageIndex].content = typeof event.message === 'string'
              ? event.message
              : '抱歉，回复失败，请稍后重试~';
            await nextTick();
            void scrollToBottom();
          }
        }, { signal: abortController.signal });

        if (!streamOk) {
          if (abortController.signal.aborted || isUserStoppingChat.value) {
            markMessageStopped(aiMessageIndex);
            return;
          }
          messages.value[aiMessageIndex].loading = false;
          messages.value[aiMessageIndex].isError = true;
          messages.value[aiMessageIndex].content = '抱歉，回复失败，请稍后重试~';
          return;
        }

        const isNewChat = router.currentRoute.value.query.createSession;
        if (isNewChat && messages.value.length >= 2) {
          let createdSession: ChatSession | null = null;
          try {
            createdSession = await DihService.getChatSession(chatSessionId.value, { type: chatSessionType.value });
            chatSessionRecordId.value = createdSession?.id || chatSessionRecordId.value;
            chatSessionExtraData.value = createdSession?.extraData || chatSessionExtraData.value;
          } catch (error) {
            console.warn('获取新会话真实ID失败，将使用sessionId作为临时ID:', error);
          }
          const newChatItem = {
            id: createdSession?.id || chatSessionId.value,
            type: createdSession?.type || chatSessionType.value,
            sessionId: createdSession?.sessionId || chatSessionId.value,
            title: createdSession?.title || `${displayMessage.substring(0, 20)}${displayMessage.length > 20 ? '...' : ''}`,
            pin: createdSession?.pin || false,
          };
          emitDihEvent(NEW_CHAT_CREATED_EVENT, { chatItem: newChatItem });
          const nextQuery = { ...router.currentRoute.value.query };
          delete nextQuery.createSession;
          void router.replace({ name: 'service-dih', query: nextQuery });
        }
      } catch (error) {
        if (abortController.signal.aborted || isUserStoppingChat.value) {
          markMessageStopped(aiMessageIndex);
          return;
        }
        console.error('聊天接口调用失败:', error);
        messages.value[aiMessageIndex].loading = false;
        messages.value[aiMessageIndex].isError = true;
        messages.value[aiMessageIndex].content = '抱歉，回复失败，请稍后重试~';
      } finally {
        if (currentChatAbortController.value === abortController) {
          currentChatAbortController.value = null;
        }
        if (currentStreamingMessageIndex.value === aiMessageIndex) {
          currentStreamingMessageIndex.value = null;
        }
        isStreamingResponse.value = false;
        isUserStoppingChat.value = false;
      }
    }
  };

  onUnmounted(() => {
    isUserStoppingChat.value = true;
    void rejectPendingMcpApprovals(currentStreamingMessageIndex.value, '聊天页面已关闭');
    currentChatAbortController.value?.abort();
  });

  return {
    isStreamingResponse,
    canSendMessage,
    sendMessage,
    stopCurrentChat,
  };
};
