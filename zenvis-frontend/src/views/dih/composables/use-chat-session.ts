import { onMounted, ref, watch } from 'vue';
import type { Router } from 'vue-router';
import { DihService } from '@/service/api';
import type { ChatMessage } from '@/types/type-dih';
import { generateUUID } from '@/utils/util-common';
import { getCurrentFormattedDate } from '@/utils/util-time';
import {
  normalizeChatSessionId,
  resolveChatSessionId,
  shouldCreateChatSession,
} from './chat-session-id';

type ChatSessionSource = {
  chatSessionId?: string;
  chatSessionType?: string;
};

type UseChatSessionOptions = {
  props: ChatSessionSource;
  router: Router;
  processMessageFormat: (message: ChatMessage) => void;
  scrollToBottom: () => Promise<void>;
};

const createWelcomeMessage = (): ChatMessage => ({
  sender: 'ai',
  content: '嘿！我是你的人工智能助手。有什么问题尽管问我吧！',
  time: getCurrentFormattedDate(),
});

export const useChatSession = ({
  props,
  router,
  processMessageFormat,
  scrollToBottom,
}: UseChatSessionOptions) => {
  const messages = ref<ChatMessage[]>([createWelcomeMessage()]);
  const showSuggestionBtn = ref(true);
  const chatSessionTitle = ref('新的会话');
  const chatSessionId = ref('');
  const chatSessionRecordId = ref('');
  const chatSessionType = ref('');
  const chatSessionExtraData = ref('');
  let ensureChatSessionIdPromise: Promise<string> | null = null;
  let loadGeneration = 0;

  const queryText = (value: unknown): string => normalizeChatSessionId(value);

  const resolveChatSessionType = (): string => {
    const propType = queryText(props.chatSessionType);
    if (propType) return propType;
    return queryText(router.currentRoute.value.query.type) || 'ask';
  };

  const resetSession = () => {
    messages.value = [createWelcomeMessage()];
    chatSessionExtraData.value = '';
    chatSessionRecordId.value = '';
    chatSessionTitle.value = '新的会话';
  };

  const ensureChatSessionRecordId = async () => {
    if (chatSessionRecordId.value) {
      return chatSessionRecordId.value;
    }
    if (!chatSessionId.value) {
      return '';
    }
    const data = await DihService.getChatSession(chatSessionId.value, { type: chatSessionType.value });
    chatSessionRecordId.value = data.id || '';
    if (data.extraData && !chatSessionExtraData.value) {
      chatSessionExtraData.value = data.extraData;
    }
    return chatSessionRecordId.value;
  };

  const refreshChatSessionExtraData = async () => {
    if (!chatSessionId.value) {
      chatSessionExtraData.value = '';
      return;
    }
    try {
      const data = await DihService.getChatSession(chatSessionId.value, { type: chatSessionType.value });
      chatSessionRecordId.value = data.id || chatSessionRecordId.value;
      chatSessionExtraData.value = data.extraData || '';
    } catch (error) {
      console.error('刷新会话附加数据失败:', error);
    }
  };

  const ensureChatSessionId = async (): Promise<string> => {
    const currentSessionId = normalizeChatSessionId(chatSessionId.value);
    if (!chatSessionType.value) {
      chatSessionType.value = resolveChatSessionType();
    }
    if (currentSessionId) {
      return currentSessionId;
    }
    if (ensureChatSessionIdPromise) {
      return ensureChatSessionIdPromise;
    }

    const route = router.currentRoute.value;
    const context = {
      currentSessionId,
      propsSessionId: props.chatSessionId,
      routeSessionId: route.query.chatSessionId,
    };
    const sessionId = resolveChatSessionId(context, generateUUID);
    const createSession = shouldCreateChatSession(context)
      || queryText(route.query.createSession) === '1';
    chatSessionId.value = sessionId;

    ensureChatSessionIdPromise = (async () => {
      if (!createSession && queryText(route.query.chatSessionId) === sessionId) {
        return sessionId;
      }
      const query = {
        ...route.query,
        type: chatSessionType.value,
        chatSessionId: sessionId,
        ...(createSession ? { createSession: 1 } : {}),
      };
      try {
        await router.replace({ name: 'service-dih', query });
      } catch (error) {
        console.warn('同步会话ID到路由失败:', error);
      }
      return sessionId;
    })();

    try {
      return await ensureChatSessionIdPromise;
    } finally {
      ensureChatSessionIdPromise = null;
    }
  };

  const loadChatSession = async () => {
    const generation = ++loadGeneration;
    chatSessionType.value = resolveChatSessionType();
    const route = router.currentRoute.value;
    const routeSessionId = normalizeChatSessionId(route.query.chatSessionId);
    const propsSessionId = normalizeChatSessionId(props.chatSessionId);

    if (propsSessionId) {
      chatSessionId.value = propsSessionId;
    } else if (routeSessionId) {
      chatSessionId.value = routeSessionId;
    } else {
      await ensureChatSessionId();
      if (generation !== loadGeneration) return;
    }

    if (!chatSessionId.value) {
      resetSession();
      showSuggestionBtn.value = true;
      void scrollToBottom();
      return;
    }

    if (queryText(route.query.createSession) === '1') {
      resetSession();
    }

    try {
      const data = await DihService.getChatSession(chatSessionId.value, { type: chatSessionType.value });
      if (generation !== loadGeneration) return;
      chatSessionRecordId.value = data.id || '';
      messages.value = data.messageList;
      chatSessionExtraData.value = data.extraData || '';

      window.setTimeout(() => {
        messages.value.forEach(processMessageFormat);
        void scrollToBottom();
      }, 500);

      chatSessionTitle.value = data.title || '新的会话';
      void scrollToBottom();
    } catch (error) {
      if (generation !== loadGeneration) return;
      console.error('获取聊天会话数据失败:', error);
      resetSession();
    }

    showSuggestionBtn.value = messages.value.length <= 1;
    void scrollToBottom();
  };

  onMounted(() => {
    void loadChatSession();
  });

  watch(
    () => router.currentRoute.value.query.chatSessionId,
    () => {
      void loadChatSession();
    },
  );

  return {
    messages,
    showSuggestionBtn,
    chatSessionTitle,
    chatSessionId,
    chatSessionRecordId,
    chatSessionType,
    chatSessionExtraData,
    ensureChatSessionRecordId,
    refreshChatSessionExtraData,
    ensureChatSessionId,
    loadChatSession,
  };
};
