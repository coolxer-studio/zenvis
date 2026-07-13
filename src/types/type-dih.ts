export type ChatMessagePartType = 'markdown' | 'code' | 'config' | 'report-document' | 'notice' | 'confirm' | 'info-steps' | 'analysis-record' | 'analysis-decision' | 'data-access-decision' | 'metadata-config-record' | 'data-push-service-record' | 'visualization-chart-preview' | 'visualization-chart-record' | 'visualization-config-record' | 'dashboard-config-record' | 'menu-config-record' | 'prompt-suggestions' | 'chart' | 'thinking';

export type ChatMessagePartStatus = 'pending' | 'approved' | 'rejected' | string;

export type ChatMessagePart = {
  id?: string;
  type: ChatMessagePartType | string;
  content?: string;
  language?: string;
  title?: string;
  level?: string;
  status?: ChatMessagePartStatus;
  metadata?: Record<string, unknown>;
};

export type ChatAttachment = {
  file_id?: string;
  fileId?: string;
  file_name?: string;
  fileName?: string;
  file_size?: number;
  fileSize?: number;
  content_type?: string;
  contentType?: string;
  kind?: 'image' | 'text' | 'file' | string;
  file_url?: string;
  fileUrl?: string;
  parse_status?: string;
  parseStatus?: string;
  message?: string;
};

export type ChatMessage = {
  id?: string;
  sender: 'user' | 'ai'
  content: string
  time: string
  type?: 'text' | 'chart' | 'code' | 'table' | 'image' | string
  parts?: ChatMessagePart[]
  attachments?: ChatAttachment[]
  loading?: boolean
  isError?: boolean
  effective?: boolean
  iframe?: string
};

export type ChatStreamEvent = {
  event: 'delta' | 'done' | 'error' | string;
  content?: string;
  message?: ChatMessage | string;
};

export type ChatSession = {
  id: string;
  sessionId: string;
  title: string;
  type: string;
  messageList: ChatMessage[];
  extraData?: string;
  deepThink: boolean;
  onlineSearch: boolean;
  updateTime: string;
  pin: boolean;
};

export type ReportDocument = {
  id?: string;
  documentId?: string;
  title?: string;
  name?: string;
  format?: 'markdown' | 'html' | string;
  version?: string;
  status?: string;
  source?: string;
  updatedAt?: string;
  content?: string;
  outline?: Array<Record<string, unknown>>;
  sourceAttachments?: Array<Record<string, unknown>>;
  raw?: Record<string, unknown>;
};

export type ReportArtifact = {
  id?: string;
  artifactId?: string;
  documentId?: string;
  name?: string;
  title?: string;
  format?: 'markdown' | 'html' | string;
  version?: string;
  status?: string;
  createdAt?: string;
  content?: string;
};

export type AnalysisStage = 'log_aggregation' | 'sandbox_analysis' | 'report_output' | string;

export type AnalysisRecord = {
  id?: string;
  recordId?: string;
  stage?: AnalysisStage;
  status?: string;
  title?: string;
  content?: string;
  startedAt?: string;
  completedAt?: string;
  alarm?: Record<string, unknown>;
  evidenceCount?: number;
  riskLevel?: string;
  confidence?: number | string;
  keyFindings?: unknown[];
  recommendations?: unknown[];
  sandboxTaskId?: string;
  toolNames?: unknown[];
  raw?: Record<string, unknown>;
  [key: string]: unknown;
};

export type AnalysisExtraData = {
  records?: AnalysisRecord[];
  aggregatedLogs?: Array<Record<string, unknown>>;
  sandboxResults?: Array<Record<string, unknown>>;
  conclusionTimeline?: Array<Record<string, unknown>>;
};

export type ModelInfo = {
  model: string;
  desc: string;
};

export type SkillVo = {
  id: string;
  name: string;
  description?: string;
  version?: string;
  author?: string;
  agentTypes: string[];
  tags: string[];
  enabled: boolean;
  entry: string;
  path?: string;
  updateTime?: string;
};

export type AgentSkillVo = {
  skillId: string;
  agentType: string;
  label: string;
  name?: string;
  description?: string;
  enabled: boolean;
  order: number;
  path?: string;
  updateTime?: string;
};

export type SkillSearchParams = {
  keyword?: string;
  agentType?: string;
  enabled?: boolean;
  page?: number;
  perPage?: number;
};

export type PageRowsVo<T> = {
  rows: T[];
  total: number;
};

// 上传文件响应
export type UploadFileResponse = ChatAttachment & {
  success?: boolean;
};

// 建议请求参数
export type SuggestParams = {
  query?: string;
  content?: string;
  current_line?: string;
  context?: string;
  limit?: number;
  [key: string]: unknown;
};

// 聊天请求参数
export type ChatParams = {
  session_id?: string;
  chat_id?: string;
  type?: string;
  message: string;
  model?: string;
  deep_think?: boolean;
  online_search?: boolean;
  response_format?: 'text' | 'events';
  context?: string[];
  attachments?: ChatAttachment[];
  [key: string]: unknown;
};

export type ChatActionDecisionParams = {
  chat_id: string;
  message_id: string;
  part_id: string;
  decision: 'approved' | 'rejected' | 'dispose' | 'ignore' | 'continue' | 'apply_config' | 'abandon' | 'revise' | 'submitted';
};

// 聊天会话分页列表参数
export type ChatSessionPageParams = {
  page?: number;
  per_page?: number;
  perPage?: number;
  type?: string;
  keyword?: string;
};

// 更新聊天会话参数
export type UpdateChatSessionParams = {
  title?: string;
  extra_data?: string;
  pin?: boolean;
  deep_think?: boolean;
  online_search?: boolean;
};

// 更新聊天会话响应
export type UpdateChatSessionResponse = {
  success: boolean;
  message?: string;
};

// 删除聊天会话响应
export type DeleteChatSessionResponse = {
  success: boolean;
  message?: string;
};

// 获取聊天会话参数
export type GetChatSessionParams = {
  session_id?: string;
  type?: string;
  [key: string]: unknown;
};
