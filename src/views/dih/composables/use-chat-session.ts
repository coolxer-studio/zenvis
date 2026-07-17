import { onMounted, ref, watch } from 'vue';
import type { Router } from 'vue-router';
import { DihService } from '@/service/api';
import type { ChatMessage } from '@/types/type-dih';
import { generateUUID } from '@/utils/util-common';
import { getCurrentFormattedDate } from '@/utils/util-time';

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

  const loadChatSession = async () => {
    if (props.chatSessionType) {
      chatSessionType.value = props.chatSessionType;
    } else {
      const currentSessionType = router.currentRoute.value.query.type;
      chatSessionType.value = currentSessionType ? currentSessionType.toString() : 'ask';
    }

    if (props.chatSessionId) {
      chatSessionId.value = props.chatSessionId;
    } else {
      const currentChatSessionId = router.currentRoute.value.query.chatSessionId;
      if (currentChatSessionId) {
        chatSessionId.value = currentChatSessionId.toString();
      } else {
        chatSessionId.value = generateUUID();
        router.push({
          name: 'service-dih',
          query: {
            type: chatSessionType.value,
            chatSessionId: chatSessionId.value,
            createSession: 1,
          },
        });
      }
    }

    if (chatSessionId.value) {
      try {
        const data = await DihService.getChatSession(chatSessionId.value, { type: chatSessionType.value });
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
        console.error('获取聊天会话数据失败:', error);
        resetSession();
      }
    } else {
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
    loadChatSession,
  };
};
