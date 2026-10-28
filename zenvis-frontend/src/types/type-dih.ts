export type ChatMessagePartType = 'markdown' | 'rag-search' | 'code' | 'config' | 'report-document' | 'report-fragment' | 'notice' | 'confirm' | 'mcp-approval' | 'tool-call' | 'agent-run' | 'context-compaction' | 'info-steps' | 'data-analysis-record' | 'metadata-config-decision' | 'data-push-service-decision' | 'visualization-chart-decision' | 'visualization-app-decision' | 'visualization-dashboard-decision' | 'metadata-config-record' | 'data-push-service-record' | 'visualization-chart-preview' | 'visualization-chart-record' | 'visualization-app-record' | 'visualization-dashboard-record' | 'config-record' | 'prompt-suggestions' | 'chart' | 'thinking';

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
  parse_progress?: number;
  parseProgress?: number;
  parse_message?: string;
  parseMessage?: string;
  download_url?: string;
  downloadUrl?: string;
  page_count?: number;
  pageCount?: number;
  sheet_count?: number;
  sheetCount?: number;
  slide_count?: number;
  slideCount?: number;
  row_count?: number;
  rowCount?: number;
  text_chars?: number;
  textChars?: number;
  chunk_count?: number;
  chunkCount?: number;
  truncated?: boolean;
  warning?: string;
  message?: string;
};

export type ChatMessage = {
  id?: string;
  sender: 'user' | 'ai'
  content: string
  time: string
  runId?: string
  type?: 'text' | 'chart' | 'code' | 'table' | 'image' | string
  parts?: ChatMessagePart[]
  attachments?: ChatAttachment[]
  loading?: boolean
  isError?: boolean
  effective?: boolean
  iframe?: string
};

export type McpApprovalStatus = 'pending' | 'approved' | 'running' | 'succeeded' | 'failed'
  | 'rejected' | 'denied' | 'expired' | 'cancelled' | string;

export type McpApprovalDecision = 'approved' | 'approved_session' | 'approved_run' | 'rejected';
export type McpApprovalScope = 'once' | 'session' | 'run' | 'task_run' | string;

export type McpApprovalData = {
  requestId: string;
  toolKey?: string;
  toolName?: string;
  sourceType?: string;
  serverName?: string;
  description?: string;
  channel?: string;
  policy?: string;
  approvalScope?: McpApprovalScope;
  sessionApprovalAllowed?: boolean;
  runApprovalAllowed?: boolean;
  agentRunId?: string;
  status?: McpApprovalStatus;
  arguments?: string;
  result?: string;
  resultLength?: number;
  errorSummary?: string;
  riskLevel?: string;
  createTime?: string;
  expireTime?: string;
  finishTime?: string;
  durationMillis?: number;
  decisionComment?: string;
  [key: string]: unknown;
};

export type ChatStreamEvent = {
  event: 'delta' | 'done' | 'error' | 'tool_call' | 'rag_search' | string;
  content?: string;
  message?: ChatMessage | string;
  data?: McpApprovalData | Record<string, unknown>;
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

export type AgentRunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'WAITING_APPROVAL'
  | 'WAITING_USER'
  | 'PAUSED'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELING'
  | 'CANCELLED';

export type AgentRunStepStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'SKIPPED' | 'BLOCKED' | string;

export type AgentRunPlanStep = {
  id: string;
  title: string;
  acceptance?: string;
  status?: AgentRunStepStatus;
  summary?: string;
  evidenceRefs?: Array<Record<string, unknown>>;
};

export type AgentRunPlan = {
  goal?: string;
  steps: AgentRunPlanStep[];
};

export type AgentRun = {
  runId: string;
  chatId: string;
  chatType: string;
  agentType: string;
  model?: string;
  status: AgentRunStatus;
  statusLabel?: string;
  goal?: string;
  plan?: AgentRunPlan;
  planRevision?: number;
  currentStepId?: string;
  decisionSteps: number;
  toolCalls: number;
  effectiveMillis: number;
  maxSteps: number;
  maxDurationSeconds: number;
  maxToolCalls: number;
  lastEventSequence: number;
  lastError?: string;
  finalAnswer?: string;
  pausedReason?: string;
  pendingAction?: string;
  attemptCount: number;
  recoveryCount: number;
  truncationCount: number;
  startedAt?: string;
  finishedAt?: string;
  createTime?: string;
  updateTime?: string;
  skillIds?: string[];
  steered?: boolean;
};

export type AgentRunEvent = {
  sequence: number;
  run_id: string;
  type: string;
  step_id?: string;
  created_at?: string;
  data?: Record<string, unknown>;
};

export type ContextCompactionStage =
  | 'run-context'
  | 'model-input'
  | 'history-window'
  | 'attachment-memory'
  | string;

export type ContextCompactionEventData = {
  compactionId?: string;
  stage?: ContextCompactionStage;
  reason?: string;
  beforeTokens?: number;
  afterTokens?: number;
  savedTokens?: number;
  beforeChars?: number;
  afterChars?: number;
  budgetTokens?: number;
  budgetChars?: number;
  omittedMessages?: number;
  retainedMessages?: number;
  truncatedMessages?: number;
  omittedAttachments?: number;
  includedAttachments?: number;
  summaryPreview?: string;
};

export type AgentRunActivityKind = 'rag' | 'tool' | 'compaction';

export type AgentRunActivity = {
  id: string;
  kind: AgentRunActivityKind;
  firstSequence: number;
  lastSequence: number;
  stepId?: string;
  createdAt?: string;
  status: string;
  eventTypes: string[];
  data: Record<string, unknown>;
};

export type AgentRunCreateParams = {
  chatId: string;
  type: string;
  message: string;
  model?: string;
  attachments?: ChatAttachment[];
  reportAction?: ReportAction;
  clientMessageId?: string;
};

export type AgentRunSteerParams = {
  message: string;
  attachments?: ChatAttachment[];
  clientMessageId?: string;
};

export type AgentRunListParams = {
  chatId?: string;
  status?: AgentRunStatus;
  page?: number;
  perPage?: number;
};

export type ReportDocument = {
  id?: string;
  documentId?: string;
  title?: string;
  name?: string;
  format?: 'markdown' | 'html' | string;
  revision?: number;
  version?: string;
  status?: string;
  source?: string;
  updatedAt?: string;
  content?: string;
  outline?: Array<Record<string, unknown>>;
  contentHash?: string;
  sourceRefs?: ReportSourceRef[];
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
  revision?: number;
  version?: string;
  status?: string;
  createdAt?: string;
  content?: string;
  contentHash?: string;
  sourceRefs?: ReportSourceRef[];
  outline?: Array<Record<string, unknown>>;
};

export type ReportSourceRef = Record<string, unknown> & {
  type?: 'attachment' | 'message' | 'chart' | 'analysis_task' | 'mcp_audit' | string;
  id?: string;
  artifactId?: string;
  name?: string;
  title?: string;
  chartType?: string;
  description?: string;
  status?: string;
  parseStatus?: string;
  truncated?: boolean;
  sessionTitle?: string;
  source?: string;
  origin?: string;
  addedAt?: string;
  queriedAt?: string;
  dataTime?: string;
};

export type ReportChartLibraryItem = Record<string, unknown> & {
  type: 'chart';
  id: string;
  artifactId?: string;
  name?: string;
  title?: string;
  chartType?: string;
  description?: string;
  source?: string;
  addedAt?: string;
  queriedAt?: string;
  sessionRecordId?: string | number;
  chatSessionId?: string;
  sessionTitle?: string;
  echartsOption?: unknown;
};

export type ReportRevision = {
  revision: number;
  version?: string;
  title?: string;
  format?: string;
  contentHash?: string;
  createdAt?: string;
  sourceRefs?: ReportSourceRef[];
};

export type ReportWorkspace = {
  currentDocument?: ReportDocument;
  revisions: ReportRevision[];
  artifacts: ReportArtifact[];
  extraData?: string;
};

export type ReportActionType = 'full_generate' | 'full_rewrite' | 'selection_rewrite';

export type ReportAction = {
  type: ReportActionType;
  document_id?: string;
  base_revision?: number;
  selection_id?: string;
  selection_hash?: string;
  source_refs?: ReportSourceRef[];
};

export type ReportDocumentSaveParams = {
  document_id?: string;
  base_revision: number;
  title: string;
  format: 'markdown' | 'html';
  content: string;
  outline?: Array<Record<string, unknown>>;
  source_refs?: ReportSourceRef[];
};

export type ReportArchiveParams = {
  document_id?: string;
  base_revision: number;
  name?: string;
};

export type ReportArtifactRenameParams = {
  base_revision: number;
  name: string;
};

export type DataAnalysisStage = 'dataset_preparation' | 'service_analysis' | 'report_output';

export type DataAnalysisRecord = {
  id?: string;
  recordId?: string;
  stage?: DataAnalysisStage;
  status?: string;
  title?: string;
  content?: string;
  startedAt?: string;
  completedAt?: string;
  analysisTarget?: string;
  datasetSummary?: string;
  datasetRecords?: Array<Record<string, unknown>>;
  serviceTaskId?: string;
  analysisResult?: unknown;
  timeline?: Array<Record<string, unknown>>;
  toolNames?: unknown[];
  raw?: Record<string, unknown>;
  [key: string]: unknown;
};

export type DataAnalysisExtraData = {
  records?: DataAnalysisRecord[];
  datasetRecords?: Array<Record<string, unknown>>;
  serviceResults?: Array<Record<string, unknown>>;
  reportTimeline?: Array<Record<string, unknown>>;
};

export type ConfigChangeMode = 'add' | 'modify';
export type ConfigValidationStatus = 'unverified' | 'success' | 'failed' | 'blocked';
export type ConfigEffectiveStatus = 'yes' | 'no';

export type ConfigRecord = {
  id?: string;
  recordId?: string;
  changeDescription?: string;
  changeMode?: ConfigChangeMode;
  configType?: string;
  fileName?: string;
  format?: string;
  oldConfig?: unknown;
  newConfig?: unknown;
  validationStatus?: ConfigValidationStatus;
  effectiveStatus?: ConfigEffectiveStatus;
  validationResult?: unknown;
  applyResult?: unknown;
  updatedAt?: string;
  [key: string]: unknown;
};

export type ConfigurationExtraData = {
  records?: ConfigRecord[];
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

export type ChatSkillEntryVo = {
  skillId: string;
  chatType: string;
  agentType: string;
  label: string;
  description?: string;
  icon: string;
  order: number;
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
  report_action?: ReportAction;
  [key: string]: unknown;
};

export type ChatActionDecisionParams = {
  chat_id: string;
  message_id: string;
  part_id: string;
  decision: 'approved' | 'rejected' | 'apply_config' | 'abandon' | 'revise' | 'submitted';
};

export type WorkflowActionName =
  | 'submit'
  | 'approve'
  | 'reject'
  | 'revise'
  | 'retry'
  | 'add_to_library';

export type WorkflowActionParams = {
  chat_id: string;
  message_id: string;
  part_id: string;
  workflow_id: string;
  action: WorkflowActionName;
  answers?: Array<Record<string, unknown>>;
  revision?: string;
};

export type WorkflowActionResult = {
  accepted: boolean;
  workflowId: string;
  state: string;
  partStatus: string;
  continuation: {
    display?: string;
    request?: string;
    [key: string]: unknown;
  };
  retryable: boolean;
  extraData?: string;
};

export type WorkflowTelemetryParams = {
  chat_id: string;
  workflow_id: string;
  event: 'chart_render_failed';
  detail?: string;
};

export type McpApprovalDecisionParams = {
  decision: McpApprovalDecision;
  comment?: string;
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
