import { request } from '../request-wrapper';
import {
  ChatSession,
  ModelInfo,
  UploadFileResponse,
  SuggestParams,
  ChatParams,
  ChatMessage,
  ChatStreamEvent,
  ChatActionDecisionParams,
  McpApprovalData,
  McpApprovalDecisionParams,
  ChatSessionPageParams,
  UpdateChatSessionParams,
  UpdateChatSessionResponse,
  DeleteChatSessionResponse,
  GetChatSessionParams,
  AgentSkillVo,
  ChatSkillEntryVo,
  PageRowsVo,
  SkillSearchParams,
  SkillVo,
} from '@/types/type-dih';
import { withBaseUrl } from '@u/url';
import {
  normalizeChatMessagePart,
  type RawChatMessagePart,
} from '@/service/normalizers/chat-message';

const prefix = '/api/v1/dih';

const prefixChatSession = '/api/v1/dih/chat-session';

type RawAttachment = {
  file_id?: string;
  fileId?: string;
  file_name?: string;
  fileName?: string;
  file_size?: number;
  fileSize?: number;
  content_type?: string;
  contentType?: string;
  kind?: string;
  file_url?: string;
  fileUrl?: string;
  parse_status?: string;
  parseStatus?: string;
  message?: string;
};

type RawMessage = {
  id?: string;
  sender?: string;
  content?: string;
  time?: string;
  type?: string;
  parts?: RawChatMessagePart[];
  attachments?: RawAttachment[];
  loading?: boolean;
  is_error?: boolean;
  isError?: boolean;
  effective?: boolean;
  iframe?: string;
};

type RawMcpApproval = Record<string, unknown> & {
  request_id?: string;
  requestId?: string;
  tool_key?: string;
  toolKey?: string;
  tool_name?: string;
  toolName?: string;
  source_type?: string;
  sourceType?: string;
  server_name?: string;
  serverName?: string;
  description?: string;
  channel?: string;
  policy?: string;
  approval_scope?: string;
  approvalScope?: string;
  status?: string;
  arguments?: string;
  result?: string;
  result_length?: number;
  resultLength?: number;
  // 兼容历史聊天消息和滚动升级期间的旧响应。
  arguments_summary?: string;
  argumentsSummary?: string;
  result_summary?: string;
  resultSummary?: string;
  error_summary?: string;
  errorSummary?: string;
  risk_level?: string;
  riskLevel?: string;
  create_time?: string;
  createTime?: string;
  expire_time?: string;
  expireTime?: string;
  finish_time?: string;
  finishTime?: string;
  duration_millis?: number;
  durationMillis?: number;
  decision_comment?: string;
  decisionComment?: string;
};

type RawStreamEvent = {
  event?: string;
  content?: string;
  message?: RawMessage | string;
  data?: RawMcpApproval | Record<string, unknown>;
};

type RawSkill = {
  id?: string;
  name?: string;
  description?: string;
  version?: string;
  author?: string;
  agent_types?: string[];
  agentTypes?: string[];
  tags?: string[];
  enabled?: boolean;
  entry?: string;
  path?: string;
  update_time?: string;
  updateTime?: string;
};

type RawAgentSkill = {
  skill_id?: string;
  skillId?: string;
  agent_type?: string;
  agentType?: string;
  label?: string;
  name?: string;
  description?: string;
  enabled?: boolean;
  order?: number;
  path?: string;
  update_time?: string;
  updateTime?: string;
};

type RawChatSkillEntry = {
  skill_id?: string;
  skillId?: string;
  chat_type?: string;
  chatType?: string;
  agent_type?: string;
  agentType?: string;
  label?: string;
  description?: string;
  icon?: string;
  order?: number;
};

type RawChatSession = {
  id?: string;
  session_id?: string;
  title?: string;
  type?: string;
  message_list?: RawMessage[];
  extra_data?: string;
  deep_think?: boolean;
  online_search?: boolean;
  update_time?: string;
  pin?: boolean;
};

const normalizeAttachment = (item: RawAttachment) => ({
  file_id: item?.file_id || item?.fileId || '',
  file_name: item?.file_name || item?.fileName || '',
  file_size: item?.file_size ?? item?.fileSize ?? 0,
  content_type: item?.content_type || item?.contentType || '',
  kind: item?.kind || '',
  file_url: item?.file_url || item?.fileUrl || '',
  parse_status: item?.parse_status || item?.parseStatus || '',
  message: item?.message || '',
});

const normalizeMessage = (item: RawMessage): ChatMessage => ({
  id: item?.id || '',
  sender: (item?.sender || 'ai') as ChatMessage['sender'],
  content: item?.content || '',
  time: item?.time || '',
  type: item?.type || 'text',
  parts: Array.isArray(item?.parts) ? item.parts.map(normalizeChatMessagePart) : undefined,
  attachments: Array.isArray(item?.attachments)
    ? item.attachments.map(normalizeAttachment)
    : undefined,
  loading: item?.loading || false,
  isError: item?.is_error ?? item?.isError,
  effective: item?.effective,
  iframe: item?.iframe,
});

const normalizeMcpApproval = (item: RawMcpApproval): McpApprovalData => ({
  ...item,
  requestId: item?.request_id || item?.requestId || '',
  toolKey: item?.tool_key || item?.toolKey || '',
  toolName: item?.tool_name || item?.toolName || '',
  sourceType: item?.source_type || item?.sourceType || '',
  serverName: item?.server_name || item?.serverName || '',
  description: item?.description || '',
  channel: item?.channel || '',
  policy: item?.policy || '',
  approvalScope: String(item?.approval_scope || item?.approvalScope || '').toLowerCase(),
  status: String(item?.status || 'pending').toLowerCase(),
  arguments: item?.arguments ?? item?.arguments_summary ?? item?.argumentsSummary ?? '',
  result: item?.result ?? item?.result_summary ?? item?.resultSummary ?? '',
  resultLength: item?.result_length ?? item?.resultLength,
  errorSummary: item?.error_summary || item?.errorSummary || '',
  riskLevel: item?.risk_level || item?.riskLevel || 'warning',
  createTime: item?.create_time || item?.createTime || '',
  expireTime: item?.expire_time || item?.expireTime || '',
  finishTime: item?.finish_time || item?.finishTime || '',
  durationMillis: item?.duration_millis ?? item?.durationMillis,
  decisionComment: item?.decision_comment || item?.decisionComment || '',
});

const normalizeStreamEvent = (event: RawStreamEvent): ChatStreamEvent => {
  if (event?.event === 'done' && event?.message && typeof event.message === 'object') {
    return {
      event: event.event,
      content: event.content,
      message: normalizeMessage(event.message),
      data: event.data,
    };
  }
  return {
    event: event?.event || 'error',
    content: event?.content,
    message: event?.message as ChatStreamEvent['message'],
    data:
      event?.event === 'approval_required' || event?.event === 'approval_updated'
        ? normalizeMcpApproval(event?.data || {})
        : event?.data,
  };
};

const parseStreamLine = (line: string): ChatStreamEvent => {
  try {
    return normalizeStreamEvent(JSON.parse(line));
  } catch (error) {
    console.error('聊天事件解析失败:', error, line);
    return {
      event: 'error',
      message: '聊天响应解析失败，请稍后重试~',
    };
  }
};

const normalizeSkill = (item: RawSkill): SkillVo => ({
  id: item?.id || '',
  name: item?.name || '',
  description: item?.description || '',
  version: item?.version || '',
  author: item?.author || '',
  agentTypes: item?.agent_types || item?.agentTypes || [],
  tags: item?.tags || [],
  enabled: item?.enabled || false,
  entry: item?.entry || 'SKILL.md',
  path: item?.path || '',
  updateTime: item?.update_time || item?.updateTime || '',
});

const normalizeAgentSkill = (item: RawAgentSkill): AgentSkillVo => ({
  skillId: item?.skill_id || item?.skillId || '',
  agentType: item?.agent_type || item?.agentType || '',
  label: item?.label || '',
  name: item?.name || '',
  description: item?.description || '',
  enabled: item?.enabled || false,
  order: item?.order ?? 0,
  path: item?.path || '',
  updateTime: item?.update_time || item?.updateTime || '',
});

const normalizeChatSkillEntry = (item: RawChatSkillEntry): ChatSkillEntryVo => ({
  skillId: item?.skill_id || item?.skillId || '',
  chatType: item?.chat_type || item?.chatType || '',
  agentType: item?.agent_type || item?.agentType || '',
  label: item?.label || '',
  description: item?.description || '',
  icon: item?.icon || 'magic-stick',
  order: item?.order ?? 1000,
});

const normalizeChatSession = (item: RawChatSession): ChatSession => ({
  id: item.id || '',
  sessionId: item.session_id || '',
  title: item.title || '',
  type: item.type || '',
  messageList: (item.message_list || []).map(normalizeMessage),
  extraData: item.extra_data || '',
  deepThink: item.deep_think || false,
  onlineSearch: item.online_search || false,
  updateTime: item.update_time || '',
  pin: item.pin || false,
});

export class DihService {
  /**
   * 上传文件接口
   * @param file 文件对象
   * @returns 上传结果
   */
  static async uploadFile(file: File): Promise<UploadFileResponse> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await request<UploadFileResponse>(`${prefix}/upload`, formData, 'POST');
    return normalizeAttachment(response) as UploadFileResponse;
  }

  /**
   * 获取建议接口
   * @param params 参数
   * @returns 建议结果
   */
  static async suggest(params: SuggestParams): Promise<string> {
    return request<string>(`${prefix}/suggest`, params, 'POST');
  }

  /**
   * 聊天接口
   * @param params 聊天参数
   * @returns 流式响应
   */
  static async chat(params: ChatParams): Promise<ReadableStreamDefaultReader<Uint8Array> | null> {
    try {
      const response = await fetch(withBaseUrl(`${prefix}/chat`), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json;charset=UTF-8',
        },
        credentials: 'include',
        body: JSON.stringify(params),
      });

      if (!response.ok || !response.body) {
        return null;
      }

      return response.body.getReader();
    } catch (error) {
      console.error('聊天接口调用失败:', error);
      return null;
    }
  }

  static async chatEvents(
    params: ChatParams,
    onEvent: (event: ChatStreamEvent) => void | Promise<void>,
    options: { signal?: AbortSignal } = {},
  ): Promise<boolean> {
    try {
      const response = await fetch(withBaseUrl(`${prefix}/chat`), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json;charset=UTF-8',
        },
        credentials: 'include',
        signal: options.signal,
        body: JSON.stringify({
          ...params,
          response_format: 'events',
        }),
      });

      if (!response.ok || !response.body) {
        return false;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (value) {
          buffer += decoder.decode(value, { stream: !done });
          const lines = buffer.split(/\r?\n/);
          buffer = lines.pop() || '';
          for (const line of lines) {
            const trimmedLine = line.trim();
            if (!trimmedLine) continue;
            await onEvent(parseStreamLine(trimmedLine));
          }
        }
        if (done) {
          break;
        }
      }

      const lastLine = buffer.trim();
      if (lastLine) {
        await onEvent(parseStreamLine(lastLine));
      }

      return true;
    } catch (error) {
      if (options.signal?.aborted) {
        return false;
      }
      console.error('聊天事件流调用失败:', error);
      return false;
    }
  }

  static async recordActionDecision(params: ChatActionDecisionParams): Promise<string> {
    return request<string>(`${prefix}/chat/action-decision`, params, 'POST', { silent: true });
  }

  static async decideMcpApproval(
    requestId: string,
    params: McpApprovalDecisionParams,
  ): Promise<McpApprovalData> {
    const response = await request<McpApprovalData>(
      `${prefix}/mcp/approvals/${encodeURIComponent(requestId)}/decision`,
      params,
      'POST',
      { silent: true },
    );
    return normalizeMcpApproval(response);
  }

  static async getModelList(): Promise<ModelInfo[]> {
    const response = await request<Array<{ model?: string; desc?: string }>>(
      `${prefix}/model/list`,
      '',
      'GET',
    );
    return response.map(item => ({
      model: item.model || '',
      desc: item.desc || '',
    }));
  }

  static async getSkillList(params: SkillSearchParams = {}): Promise<PageRowsVo<SkillVo>> {
    const response = await request<{ rows: RawSkill[]; total: number }>(
      `${prefix}/skills/list`,
      params,
      'GET',
    );
    return {
      rows: (response.rows || []).map(normalizeSkill),
      total: response.total || 0,
    };
  }

  static async getAgentSkills(enabled = true): Promise<AgentSkillVo[]> {
    const response = await request<RawAgentSkill[]>(`${prefix}/skills/agents`, { enabled }, 'GET');
    return response.map(normalizeAgentSkill);
  }

  static async getChatSkillEntries(enabled = true): Promise<ChatSkillEntryVo[]> {
    const response = await request<RawChatSkillEntry[]>(
      `${prefix}/skills/chat-entries`,
      { enabled },
      'GET',
    );
    return response.map(normalizeChatSkillEntry);
  }

  static async getChatSessionForPin(): Promise<ChatSession[]> {
    const response = await request<RawChatSession[]>(`${prefixChatSession}/list/pin`, '', 'GET');
    return response.map(normalizeChatSession);
  }

  static async getChatSessionPageList(params: ChatSessionPageParams): Promise<ChatSession[]> {
    const requestParams = {
      ...params,
      per_page: params.per_page ?? params.perPage ?? 10,
      perPage: params.perPage ?? params.per_page ?? 10,
    };
    const response = await request<{ rows: RawChatSession[] }>(
      `${prefixChatSession}/list`,
      requestParams,
      'GET',
    );
    return response.rows.map(normalizeChatSession);
  }

  static async updateChatSession(
    id: string,
    params: UpdateChatSessionParams,
  ): Promise<UpdateChatSessionResponse> {
    return request<UpdateChatSessionResponse>(`${prefixChatSession}/${id}/update`, params);
  }

  static async deleteChatSession(id: string): Promise<DeleteChatSessionResponse> {
    return request<DeleteChatSessionResponse>(`${prefixChatSession}/${id}`, '', 'DELETE');
  }

  static async getChatSession(
    chatSessionId: string,
    params: GetChatSessionParams,
  ): Promise<ChatSession> {
    const response = await request<RawChatSession>(
      `${prefixChatSession}/${chatSessionId}/session`,
      params,
      'GET',
    );
    return normalizeChatSession(response);
  }
}
