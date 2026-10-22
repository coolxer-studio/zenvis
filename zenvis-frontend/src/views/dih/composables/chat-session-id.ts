export type ChatSessionIdContext = {
  currentSessionId?: string | null;
  propsSessionId?: string | null;
  routeSessionId?: unknown;
};

export const normalizeChatSessionId = (value: unknown): string => {
  const raw = Array.isArray(value) ? value[0] : value;
  return typeof raw === 'string' ? raw.trim() : '';
};

export const resolveChatSessionId = (
  context: ChatSessionIdContext,
  createId: () => string,
): string => normalizeChatSessionId(context.currentSessionId)
  || normalizeChatSessionId(context.propsSessionId)
  || normalizeChatSessionId(context.routeSessionId)
  || createId().trim();

export const shouldCreateChatSession = (context: ChatSessionIdContext): boolean =>
  !normalizeChatSessionId(context.propsSessionId)
  && !normalizeChatSessionId(context.routeSessionId);
