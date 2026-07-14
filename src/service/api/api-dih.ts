import { request } from '../request-wrapper';
import {
  ChatSession,
  ModelInfo,
  UploadFileResponse,
  SuggestParams,
  ChatParams,
  ChatMessage,
  ChatMessagePart,
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
  PageRowsVo,
  SkillSearchParams,
  SkillVo,
} from '@/types/type-dih';
import { withBaseUrl } from '@u/url';

const prefix = '/api/v1/dih';

const prefixChatSession = '/api/v1/dih/chat-session';

const normalizeAttachment = (item: any) => ({
  file_id: item?.file_id || item?.fileId || '',
  file_name: item?.file_name || item?.fileName || '',
  file_size: item?.file_size ?? item?.fileSize ?? 0,
  content_type: item?.content_type || item?.contentType || '',
  kind: item?.kind || '',
  file_url: item?.file_url || item?.fileUrl || '',
  parse_status: item?.parse_status || item?.parseStatus || '',
  message: item?.message || '',
});

const normalizeMessagePart = (item: any): ChatMessagePart => ({
  id: item?.id || '',
  type: item?.type || 'markdown',
  content: item?.content || '',
  language: item?.language || '',
  title: item?.title || '',
  level: item?.level || '',
  status: item?.status || '',
  metadata: item?.metadata || {},
});

const normalizeMessage = (item: any): ChatMessage => ({
  id: item?.id || '',
  sender: item?.sender || 'ai',
  content: item?.content || '',
  time: item?.time || '',
  type: item?.type || 'text',
  parts: Array.isArray(item?.parts) ? item.parts.map(normalizeMessagePart) : undefined,
  attachments: Array.isArray(item?.attachments) ? item.attachments.map(normalizeAttachment) : undefined,
  loading: item?.loading || false,
  isError: item?.is_error ?? item?.isError,
  effective: item?.effective,
  iframe: item?.iframe,
});

const normalizeMcpApproval = (item: any): McpApprovalData => ({
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
  argumentsSummary: item?.arguments_summary || item?.argumentsSummary || '',
  resultSummary: item?.result_summary || item?.resultSummary || '',
  errorSummary: item?.error_summary || item?.errorSummary || '',
  riskLevel: item?.risk_level || item?.riskLevel || 'warning',
  createTime: item?.create_time || item?.createTime || '',
  expireTime: item?.expire_time || item?.expireTime || '',
  finishTime: item?.finish_time || item?.finishTime || '',
  durationMillis: item?.duration_millis ?? item?.durationMillis,
  decisionComment: item?.decision_comment || item?.decisionComment || '',
});

const normalizeStreamEvent = (event: any): ChatStreamEvent => {
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
    message: event?.message,
    data: event?.event === 'approval_required' || event?.event === 'approval_updated'
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

const normalizeSkill = (item: any): SkillVo => ({
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

const normalizeAgentSkill = (item: any): AgentSkillVo => ({
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

  static async decideMcpApproval(requestId: string, params: McpApprovalDecisionParams): Promise<McpApprovalData> {
    const response = await request<McpApprovalData>(
      `${prefix}/mcp/approvals/${encodeURIComponent(requestId)}/decision`,
      params,
      'POST',
      { silent: true },
    );
    return normalizeMcpApproval(response);
  }

  static async getModelList(): Promise<ModelInfo[]> {
    const response = await request<Array<object>>(`${prefix}/model/list`, '', 'GET');
    // 返回ModelInfo[]对象
    return response.map((item) => ({
      model: (item as any).model || '',
      desc: (item as any).desc || '',
    }));
  }

  static async getSkillList(params: SkillSearchParams = {}): Promise<PageRowsVo<SkillVo>> {
    const response = await request<{ rows: object[]; total: number }>(`${prefix}/skills/list`, params, 'GET');
    return {
      rows: (response.rows || []).map(normalizeSkill),
      total: response.total || 0,
    };
  }

  static async getAgentSkills(enabled = true): Promise<AgentSkillVo[]> {
    const response = await request<Array<object>>(`${prefix}/skills/agents`, { enabled }, 'GET');
    return response.map(normalizeAgentSkill);
  }

  static async getChatSessionForPin(): Promise<ChatSession[]> {
    const response = await request<Array<object>>(`${prefixChatSession}/list/pin`, '', 'GET');
    // 返回ChatSession[]对象
    return response.map((item) => ({
      id: (item as any).id || '',
      sessionId: (item as any).session_id || '',
      title: (item as any).title || '',
      type: (item as any).type || '',
      messageList: ((item as any).message_list || []).map(normalizeMessage),
      extraData: (item as any).extra_data || '',
      deepThink: (item as any).deep_think || false,
      onlineSearch: (item as any).online_search || false,
      updateTime: (item as any).update_time || '',
      pin: (item as any).pin || false,
    }));
  }

  static async getChatSessionPageList(params: ChatSessionPageParams): Promise<ChatSession[]> {
    const requestParams = {
      ...params,
      per_page: params.per_page ?? params.perPage ?? 10,
      perPage: params.perPage ?? params.per_page ?? 10,
    };
    const response = await request<{ rows: object[] }>(`${prefixChatSession}/list`, requestParams, 'GET');
    // 返回ChatSession[]对象
    return response.rows.map((item) => ({
      id: (item as any).id || '',
      sessionId: (item as any).session_id || '',
      title: (item as any).title || '',
      type: (item as any).type || '',
      messageList: ((item as any).message_list || []).map(normalizeMessage),
      extraData: (item as any).extra_data || '',
      deepThink: (item as any).deep_think || false,
      onlineSearch: (item as any).online_search || false,
      updateTime: (item as any).update_time || '',
      pin: (item as any).pin || false,
    }));
  }

  static async updateChatSession(id: string, params: UpdateChatSessionParams): Promise<UpdateChatSessionResponse> {
    return request<UpdateChatSessionResponse>(`${prefixChatSession}/${id}/update`, params);
  }

  static async deleteChatSession(id: string): Promise<DeleteChatSessionResponse> {
    return request<DeleteChatSessionResponse>(`${prefixChatSession}/${id}`, '', 'DELETE');
  }

  static async getChatSession(chatSessionId: string, params: GetChatSessionParams): Promise<ChatSession> {
    const response = await request<Object>(`${prefixChatSession}/${chatSessionId}/session`, params, 'GET');
    return {
      id: (response as any).id || '',
      sessionId: (response as any).session_id || '',
      title: (response as any).title || '',
      type: (response as any).type || '',
      messageList: ((response as any).message_list || []).map(normalizeMessage),
      extraData: (response as any).extra_data || '',
      deepThink: (response as any).deep_think || false,
      onlineSearch: (response as any).online_search || false,
      updateTime: (response as any).update_time || '',
      pin: (response as any).pin || false,
    };
  }
}
