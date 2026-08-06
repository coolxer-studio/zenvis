import type { ChatMessagePart } from '@/types/type-dih';

export type TAnalysisTaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'WAITING_APPROVAL'
  | 'CANCELING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELED';

export type TAnalysisTaskApprovalMode = 'AUTO' | 'MANUAL';

export type TAnalysisTaskScheduleType = 'ONCE' | 'CRON';

export type TAnalysisTask = {
  id: number;
  name: string;
  description: string;
  model: string;
  prompt: string;
  result: string;
  resultParts: ChatMessagePart[];
  errorMessage: string;
  status: TAnalysisTaskStatus;
  statusDescription: string;
  priority: number;
  approvalMode: TAnalysisTaskApprovalMode;
  executionId: string;
  lastExecutionId: string;
  lastRunStatus: TAnalysisTaskStatus | '';
  skillIds: string[];
  pendingApprovalCount: number;
  scheduledTime: string;
  cronExpression: string;
  startTime: string;
  finishTime: string;
  runCount: number;
  createTime: string;
  updateTime: string;
  createBy?: number;
};

export type TAnalysisTaskForm = {
  name: string;
  description: string;
  model: string;
  prompt: string;
  priority: number;
  scheduleType: TAnalysisTaskScheduleType;
  scheduledTime: string;
  cronExpression: string;
  approvalMode: TAnalysisTaskApprovalMode;
  skillIds: string[];
};

export type TAnalysisTaskSearch = {
  name?: string;
  status?: TAnalysisTaskStatus | '';
  model?: string;
  approvalMode?: TAnalysisTaskApprovalMode | '';
  page: number;
  perPage: number;
};

export type TAnalysisTaskQueue = {
  runningTask: TAnalysisTask | null;
  nextTask: TAnalysisTask | null;
  pendingCount: number;
  readyCount: number;
  runningCount: number;
  waitingApprovalCount: number;
  availableSlots: number;
  maxSuspended: number;
  checkedAt: string;
};

export type TAnalysisTaskModelOption = {
  model: string;
  desc: string;
};

export type TAnalysisTaskSkillOption = {
  label: string;
  value: string;
  description: string;
  agentTypes: string[];
};

export type TMcpInvocation = {
  requestId: string;
  toolKey: string;
  toolName: string;
  serverName: string;
  description: string;
  policy: string;
  approvalScope: string;
  status: string;
  arguments: string;
  result: string;
  resultLength?: number;
  errorSummary: string;
  riskLevel: string;
  createTime: string;
};

export type TPageRows<T> = {
  rows: T[];
  total: number;
};

export type TAnalysisTaskDecision = 'approved' | 'approved_task' | 'rejected';
