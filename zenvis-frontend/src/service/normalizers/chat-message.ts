import type { ChatMessagePart } from '@/types/type-dih';

export type RawChatMessagePart = {
  id?: string;
  type?: string;
  content?: string;
  language?: string;
  title?: string;
  level?: string;
  status?: string;
  metadata?: Record<string, unknown>;
};

export const normalizeChatMessagePart = (item: RawChatMessagePart): ChatMessagePart => ({
  id: item?.id || '',
  type: item?.type || 'markdown',
  content: item?.content || '',
  language: item?.language || '',
  title: item?.title || '',
  level: item?.level || '',
  status: item?.status || '',
  metadata: item?.metadata || {},
});
