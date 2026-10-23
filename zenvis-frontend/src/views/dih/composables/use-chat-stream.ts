import { computed, nextTick, onUnmounted, ref } from 'vue';
import type { Ref } from 'vue';
import type { Router } from 'vue-router';
import { ElMessage } from 'element-plus';
import { DihService } from '@/service/api';
import {
  isStructuredStreamPart,
  mergeLiveStructuredParts,
  upsertToolCallPart,
} from '@/components/dih-message/tool-call-parts';
import { upsertRagSearchPart } from '@/components/dih-message/rag-search-parts';
import type {
  AgentRun,
  AgentRunEvent,
  ChatAttachment,
  ChatMessage,
  ChatMessagePart,
  ChatSession,
  McpApprovalData,
  ReportAction,
} from '@/types/type-dih';
import { generateUUID } from '@/utils/util-common';
import { getCurrentFormattedDate } from '@/utils/util-time';
import {
  NEW_CHAT_CREATED_EVENT,
  emitDihEvent,
} from '../events';
import { useAgentRun } from './use-agent-run';
import { findAgentRunMessageIndex, upsertAgentRunMessage } from './agent-run-message';

export type SendMessageOptions = {
  content?: string;
  requestContent?: string;
  reportAction?: ReportAction;
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
  reloadChatSessionMessages: () => Promise<void>;
  ensureChatSessionId: () => Promise<string>;
  isMcpApprovalPart: (part: ChatMessagePart) => boolean;
  upsertMcpApprovalPart: (message: ChatMessage, data: McpApprovalData, accumulatedContent: string) => void;
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
  reloadChatSessionMessages,
  ensureChatSessionId,
  isMcpApprovalPart,
  upsertMcpApprovalPart,
  rejectPendingMcpApprovals,
}: UseChatStreamOptions) => {
  const isStreamingResponse = ref(false);
  const currentChatAbortController = ref<AbortController | null>(null);
  const currentStreamingMessageIndex = ref<number | null>(null);
  const isUserStoppingChat = ref(false);
  const activeAgentRunMessageId = ref('');
  const isAgentChat = computed(() => {
    const type = chatSessionType.value || '';
    return type.startsWith('agent_') || type.startsWith('skill:');
  });
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

  const defaultReportAction = (): ReportAction | undefined => {
    if (chatSessionType.value !== 'agent_report') return undefined;
    try {
      const extraData = chatSessionExtraData.value.trim()
        ? JSON.parse(chatSessionExtraData.value) as Record<string, unknown>
        : {};
      const report = extraData.report && typeof extraData.report === 'object'
        ? extraData.report as Record<string, unknown>
        : {};
      const document = report.currentDocument && typeof report.currentDocument === 'object'
        ? report.currentDocument as Record<string, unknown>
        : {};
      const documentId = String(document.documentId || document.document_id || document.id || '');
      const revision = Number(document.revision || 0);
      return {
        type: documentId ? 'full_rewrite' : 'full_generate',
        document_id: documentId || undefined,
        base_revision: revision,
        source_refs: Array.isArray(document.sourceRefs || document.source_refs)
          ? (document.sourceRefs || document.source_refs) as ReportAction['source_refs']
          : [],
      };
    } catch {
      return {
        type: 'full_generate',
        base_revision: 0,
      };
    }
  };

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
    const hasStructuredParts = message.parts?.some(isStructuredStreamPart) === true;
    if (!hasStructuredParts) {
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
    const structuredParts = (message.parts || []).filter(isStructuredStreamPart);
    message.parts = structuredParts.length
      ? [...structuredParts, ...createMarkdownParts(message.content)]
      : undefined;
  };

  const isAgentRunUnavailable = (error: unknown) => {
    if (!error || typeof error !== 'object') return false;
    const value = error as Record<string, unknown>;
    return Number(value.status ?? value.code) === 503
      || /尚未启用持久化任务/.test(String(value.msg ?? value.message ?? ''));
  };

  const finalizeNewChatSession = async (displayMessage: string, sessionId: string) => {
    const isNewChat = router.currentRoute.value.query.createSession;
    if (!isNewChat || messages.value.length < 2) return;
    let createdSession: ChatSession | null = null;
    try {
      createdSession = await DihService.getChatSession(sessionId, {
        type: chatSessionType.value,
      });
      chatSessionRecordId.value = createdSession?.id || chatSessionRecordId.value;
      chatSessionExtraData.value = createdSession?.extraData || chatSessionExtraData.value;
    } catch (error) {
      console.warn('获取新会话真实ID失败，将使用sessionId作为临时ID:', error);
    }
    const newChatItem = {
      id: createdSession?.id || sessionId,
      type: createdSession?.type || chatSessionType.value,
      sessionId: createdSession?.sessionId || sessionId,
      title: createdSession?.title
        || `${displayMessage.substring(0, 20)}${displayMessage.length > 20 ? '...' : ''}`,
      pin: createdSession?.pin || false,
    };
    emitDihEvent(NEW_CHAT_CREATED_EVENT, { chatItem: newChatItem });
    const nextQuery = { ...router.currentRoute.value.query };
    delete nextQuery.createSession;
    void router.replace({ name: 'service-dih', query: nextQuery });
  };

  const streamLegacyResponse = async ({
    requestMessage,
    messageAttachments,
    displayMessage,
    reportAction,
    sessionId,
  }: {
    requestMessage: string;
    messageAttachments: ChatAttachment[];
    displayMessage: string;
    reportAction?: ReportAction;
    sessionId: string;
  }) => {
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
        chat_id: sessionId,
        attachments: messageAttachments,
        report_action: reportAction || defaultReportAction(),
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

        if (event.event === 'rag_search' && event.data) {
          const message = messages.value[aiMessageIndex];
          message.parts = upsertRagSearchPart(message.parts, event.data);
          message.loading = false;
          await nextTick();
          void scrollToBottom();
          return;
        }

        if (event.event === 'tool_call' && event.data) {
          const message = messages.value[aiMessageIndex];
          message.parts = upsertToolCallPart(message.parts, event.data);
          message.loading = false;
          await nextTick();
          void scrollToBottom();
          return;
        }

        if (event.event === 'done') {
          if (event.message && typeof event.message !== 'string') {
            const liveParts = messages.value[aiMessageIndex].parts;
            messages.value[aiMessageIndex] = {
              ...event.message,
              parts: mergeLiveStructuredParts(liveParts, event.message.parts),
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

      await finalizeNewChatSession(displayMessage, sessionId);
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
  };

  const ensureAgentRunMessage = (
    run: AgentRun,
    currentTool: Record<string, unknown> | null = null,
    activityEvents: AgentRunEvent[] = [],
  ) => {
    const index = upsertAgentRunMessage(messages.value, run, {
      currentTool,
      activityEvents,
      time: getCurrentFormattedDate(),
    });
    if (index >= 0) activeAgentRunMessageId.value = run.runId;
    return index;
  };

  const syncAgentRunMessage = (runId: string) => {
    const run = agentRun.run.value;
    if (!run || run.runId !== runId) return;
    ensureAgentRunMessage(run, agentRun.currentTool.value, agentRun.activityEvents.value);
  };

  const handleAgentRunEvent = async (event: AgentRunEvent) => {
    syncAgentRunMessage(event.run_id);
    if (event.type === 'run.waiting_user' || event.type === 'run.succeeded') {
      await reloadChatSessionMessages();
      await restoreActiveAgentRun();
      syncAgentRunMessage(event.run_id);
    }
    if (event.type === 'artifact.updated'
      || event.type === 'run.waiting_user'
      || event.type === 'run.succeeded') {
      await refreshChatSessionExtraData();
    }
    await nextTick();
    void scrollToBottom();
  };

  const agentRun = useAgentRun({
    onEvent: handleAgentRunEvent,
    onApproval: async approval => {
      const runId = approval.agentRunId || agentRun.run.value?.runId || '';
      if (!runId) return;
      const run = agentRun.run.value;
      if (run) ensureAgentRunMessage(run);
      const index = findAgentRunMessageIndex(messages.value, runId);
      if (index < 0) return;
      upsertMcpApprovalPart(messages.value[index], approval, messages.value[index].content || '');
      await nextTick();
      void scrollToBottom();
    },
  });

  const isAgentRunActive = computed(() => isAgentChat.value && agentRun.isActive.value);
  const canStopCurrent = computed(() => isStreamingResponse.value || isAgentRunActive.value);

  let restoreRunGeneration = 0;

  const listChatAgentRuns = async (chatId: string) => {
    const runs: AgentRun[] = [];
    let pageNumber = 1;
    let total = 0;
    while (true) {
      const page = await DihService.listAgentRuns({
        chatId,
        page: pageNumber,
        perPage: 50,
      });
      total = page.total;
      runs.push(...page.rows);
      if (!page.rows.length || runs.length >= total) break;
      pageNumber += 1;
    }
    return [...new Map(runs.map(run => [run.runId, run])).values()]
      .sort((left, right) => String(left.createTime || left.updateTime || '')
        .localeCompare(String(right.createTime || right.updateTime || '')));
  };

  const restoreActiveAgentRun = async () => {
    if (!isAgentChat.value || !chatSessionId.value) return;
    const generation = ++restoreRunGeneration;
    try {
      const runs = await listChatAgentRuns(chatSessionId.value);
      if (generation !== restoreRunGeneration) return;
      const runIdsInMessages = new Set(messages.value
        .map(message => String(message.runId || ''))
        .filter(Boolean));
      runs.forEach(run => {
        if (runIdsInMessages.has(run.runId)) ensureAgentRunMessage(run);
      });
      const active = runs.find(run => run.status
        && ['QUEUED', 'RUNNING', 'WAITING_APPROVAL', 'WAITING_USER', 'PAUSED', 'CANCELING']
          .includes(run.status));
      if (!active) return;
      ensureAgentRunMessage(active);
      await agentRun.attach(active, { after: 0 });
    } catch (error) {
      console.warn('恢复智能体任务失败:', error);
    }
  };

  const sendAgentMessage = async (
    options: SendMessageOptions = {},
    ensuredSessionId?: string,
  ) => {
    const explicitMessage = options.content?.trim();
    const explicitRequestMessage = options.requestContent?.trim();
    const canSend = explicitMessage
      ? !isUploadingAttachment.value
      : canSendMessage.value;
    if (!canSend) return;

    const currentInputMessage = inputMessage.value.trim();
    const requestMessage = explicitRequestMessage || explicitMessage || currentInputMessage;
    const messageAttachments = explicitMessage ? [] : pendingAttachments.value.slice();
    const displayMessage = explicitMessage || currentInputMessage || '请分析上传的附件内容。';
    const sessionId = ensuredSessionId || await ensureChatSessionId();
    if (!sessionId) {
      ElMessage.error('会话初始化失败，请刷新页面后重试');
      return;
    }
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
    void scrollToBottom();

    try {
      const active = agentRun.run.value && agentRun.isActive.value
        ? agentRun.run.value
        : await agentRun.findActive(sessionId);
      if (active) {
        if (agentRun.run.value?.runId !== active.runId) {
          ensureAgentRunMessage(active);
          await agentRun.attach(active, { after: 0 });
        }
        const steered = await agentRun.steer(active.runId, {
          message: requestMessage,
          attachments: messageAttachments,
          clientMessageId: generateUUID(),
        });
        ensureAgentRunMessage(steered);
        ElMessage.success('已排队，将在当前步骤结束后应用');
        return;
      }
      const created = await agentRun.create({
        type: chatSessionType.value,
        message: requestMessage,
        model: selectedModel.value,
        chatId: sessionId,
        attachments: messageAttachments,
        reportAction: options.reportAction || defaultReportAction(),
        clientMessageId: generateUUID(),
      });
      ensureAgentRunMessage(created);
      await finalizeNewChatSession(displayMessage, sessionId);
      await nextTick();
      void scrollToBottom();
    } catch (error) {
      if (isAgentRunUnavailable(error)) {
        await streamLegacyResponse({
          requestMessage,
          messageAttachments,
          displayMessage,
          reportAction: options.reportAction || defaultReportAction(),
          sessionId,
        });
        return;
      }
      console.error('创建智能体任务失败:', error);
      const errorValue = error as Record<string, unknown> | null;
      ElMessage.error(String(errorValue?.msg || errorValue?.message || '智能体任务创建失败'));
    }
  };

  const stopCurrentChat = async () => {
    if (isAgentChat.value && agentRun.isActive.value) {
      try {
        await agentRun.cancel();
        ElMessage.info('已请求停止智能体任务');
      } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : '停止任务失败');
      }
      return;
    }
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

  const resumeAgentRun = async (runId: string) => {
    const updated = await agentRun.resumeRun(runId);
    if (updated) ensureAgentRunMessage(updated, agentRun.currentTool.value);
    return updated;
  };

  const replyToWaitingAgentRun = async (runId: string, rawMessage: string) => {
    const message = rawMessage.trim();
    if (!runId || !message) return null;
    const steered = await agentRun.steer(runId, {
      message,
      clientMessageId: generateUUID(),
    });
    messages.value.push({
      sender: 'user',
      content: message,
      time: getCurrentFormattedDate(),
    });
    ensureAgentRunMessage(steered, agentRun.currentTool.value, agentRun.activityEvents.value);
    await agentRun.attach(steered, { after: agentRun.lastSequence.value });
    await nextTick();
    void scrollToBottom();
    ElMessage.success('补充信息已提交，任务将继续执行');
    return steered;
  };

  const sendMessage = async (options: SendMessageOptions = {}) => {
    if (isChatUnavailable.value) {
      ElMessage.warning('当前 Skill 已停用或不存在，请选择其他可用技能');
      return;
    }
    const explicitMessage = options.content?.trim();
    if (!explicitMessage && !canSendMessage.value) return;

    const sessionId = await ensureChatSessionId();
    if (!sessionId) {
      ElMessage.error('会话初始化失败，请刷新页面后重试');
      return;
    }
    if (isAgentChat.value) {
      await sendAgentMessage(options, sessionId);
      return;
    }
    const explicitRequestMessage = options.requestContent?.trim();
    const canSend = explicitMessage
      ? !isUploadingAttachment.value && !isStreamingResponse.value
      : canSendMessage.value;
    if (!canSend) return;

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

    await streamLegacyResponse({
      requestMessage,
      messageAttachments,
      displayMessage,
      reportAction: options.reportAction,
      sessionId,
    });
  };

  onUnmounted(() => {
    isUserStoppingChat.value = true;
    if (!isAgentChat.value) {
      void rejectPendingMcpApprovals(currentStreamingMessageIndex.value, '聊天页面已关闭');
    }
    currentChatAbortController.value?.abort();
  });

  return {
    isStreamingResponse,
    isAgentRunActive,
    canStopCurrent,
    canSendMessage,
    sendMessage,
    stopCurrentChat,
    resumeAgentRun,
    replyToWaitingAgentRun,
    restoreActiveAgentRun,
  };
};
