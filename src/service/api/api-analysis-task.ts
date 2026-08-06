import { request } from '../request-wrapper';
import type {
  TAnalysisTask,
  TAnalysisTaskDecision,
  TAnalysisTaskForm,
  TAnalysisTaskModelOption,
  TAnalysisTaskQueue,
  TAnalysisTaskSearch,
  TAnalysisTaskSkillOption,
  TMcpInvocation,
  TPageRows,
} from '@/types/type-analysis-task';
import {
  normalizeChatMessagePart,
  type RawChatMessagePart,
} from '@/service/normalizers/chat-message';

const prefix = '/api/v1/system/analysis-task';

type RawData = Record<string, unknown>;

const textValue = (data: RawData, snakeKey: string, camelKey = snakeKey): string => {
  const value = data[snakeKey] ?? data[camelKey];
  return value === null || value === undefined ? '' : String(value);
};

const numberValue = (data: RawData, snakeKey: string, camelKey = snakeKey): number => {
  const value = Number(data[snakeKey] ?? data[camelKey] ?? 0);
  return Number.isFinite(value) ? value : 0;
};

const normalizeTask = (data: RawData): TAnalysisTask => ({
  id: numberValue(data, 'id'),
  name: textValue(data, 'name'),
  description: textValue(data, 'description'),
  model: textValue(data, 'model') || 'auto',
  prompt: textValue(data, 'prompt'),
  result: textValue(data, 'result'),
  resultParts: Array.isArray(data.result_parts ?? data.resultParts)
    ? ((data.result_parts ?? data.resultParts) as RawChatMessagePart[]).map(
        normalizeChatMessagePart,
      )
    : [],
  errorMessage: textValue(data, 'error_message', 'errorMessage'),
  status: (textValue(data, 'status') || 'PENDING') as TAnalysisTask['status'],
  statusDescription: textValue(data, 'status_description', 'statusDescription'),
  priority: numberValue(data, 'priority'),
  approvalMode: (textValue(data, 'approval_mode', 'approvalMode') ||
    'MANUAL') as TAnalysisTask['approvalMode'],
  executionId: textValue(data, 'execution_id', 'executionId'),
  lastExecutionId: textValue(data, 'last_execution_id', 'lastExecutionId'),
  lastRunStatus: textValue(
    data,
    'last_run_status',
    'lastRunStatus',
  ) as TAnalysisTask['lastRunStatus'],
  skillIds: ((data.skill_ids ?? data.skillIds ?? []) as unknown[]).map(String),
  pendingApprovalCount: numberValue(data, 'pending_approval_count', 'pendingApprovalCount'),
  scheduledTime: textValue(data, 'scheduled_time', 'scheduledTime'),
  cronExpression: textValue(data, 'cron_expression', 'cronExpression'),
  startTime: textValue(data, 'start_time', 'startTime'),
  finishTime: textValue(data, 'finish_time', 'finishTime'),
  runCount: numberValue(data, 'run_count', 'runCount'),
  createTime: textValue(data, 'create_time', 'createTime'),
  updateTime: textValue(data, 'update_time', 'updateTime'),
  createBy: numberValue(data, 'create_by', 'createBy') || undefined,
});

const normalizeInvocation = (data: RawData): TMcpInvocation => ({
  requestId: textValue(data, 'request_id', 'requestId'),
  toolKey: textValue(data, 'tool_key', 'toolKey'),
  toolName: textValue(data, 'tool_name', 'toolName'),
  serverName: textValue(data, 'server_name', 'serverName'),
  description: textValue(data, 'description'),
  policy: textValue(data, 'policy'),
  approvalScope: textValue(data, 'approval_scope', 'approvalScope'),
  status: textValue(data, 'status'),
  arguments:
    textValue(data, 'arguments') || textValue(data, 'arguments_summary', 'argumentsSummary'),
  result: textValue(data, 'result') || textValue(data, 'result_summary', 'resultSummary'),
  resultLength:
    data.result_length == null && data.resultLength == null
      ? undefined
      : numberValue(data, 'result_length', 'resultLength'),
  errorSummary: textValue(data, 'error_summary', 'errorSummary'),
  riskLevel: textValue(data, 'risk_level', 'riskLevel'),
  createTime: textValue(data, 'create_time', 'createTime'),
});

const taskPayload = (data: TAnalysisTaskForm) => ({
  name: data.name.trim(),
  description: data.description.trim() || null,
  model: data.model || 'auto',
  prompt: data.prompt.trim(),
  priority: data.priority ?? 0,
  scheduled_time: data.scheduleType === 'ONCE' ? data.scheduledTime || null : null,
  cron_expression: data.scheduleType === 'CRON' ? data.cronExpression.trim() || null : null,
  approval_mode: data.approvalMode,
  skill_ids: data.skillIds,
});

export class AnalysisTaskService {
  static async getList(
    params: TAnalysisTaskSearch,
    silent = false,
  ): Promise<TPageRows<TAnalysisTask>> {
    const response = await request<{ rows?: RawData[]; total?: number }>(
      `${prefix}/list`,
      {
        name: params.name || undefined,
        status: params.status || undefined,
        model: params.model || undefined,
        approvalMode: params.approvalMode || undefined,
        page: params.page,
        perPage: params.perPage,
      },
      'GET',
      { silent },
    );
    return {
      rows: (response.rows || []).map(normalizeTask),
      total: response.total || 0,
    };
  }

  static async getView(id: number, silent = false): Promise<TAnalysisTask> {
    const response = await request<RawData>(`${prefix}/${id}/view`, {}, 'GET', { silent });
    return normalizeTask(response);
  }

  static async create(data: TAnalysisTaskForm): Promise<TAnalysisTask> {
    const response = await request<RawData>(`${prefix}/add`, taskPayload(data), 'POST');
    return normalizeTask(response);
  }

  static async update(id: number, data: TAnalysisTaskForm): Promise<void> {
    await request<unknown>(`${prefix}/${id}/update`, taskPayload(data), 'POST');
  }

  static async remove(id: number): Promise<void> {
    await request<unknown>(`${prefix}/${id}`, {}, 'DELETE');
  }

  static async enqueue(id: number): Promise<TAnalysisTask> {
    const response = await request<RawData>(`${prefix}/${id}/enqueue`, {}, 'POST');
    return normalizeTask(response);
  }

  static async cancel(id: number): Promise<TAnalysisTask> {
    const response = await request<RawData>(`${prefix}/${id}/cancel`, {}, 'POST');
    return normalizeTask(response);
  }

  static async runOnce(): Promise<TAnalysisTask | null> {
    const response = await request<RawData | null>(`${prefix}/queue/run-once`, {}, 'POST');
    return response ? normalizeTask(response) : null;
  }

  static async getQueueStatus(silent = false): Promise<TAnalysisTaskQueue> {
    const response = await request<RawData>(`${prefix}/queue/status`, {}, 'GET', { silent });
    const runningTask = (response.running_task ?? response.runningTask) as
      | RawData
      | null
      | undefined;
    const nextTask = (response.next_task ?? response.nextTask) as RawData | null | undefined;
    return {
      runningTask: runningTask ? normalizeTask(runningTask) : null,
      nextTask: nextTask ? normalizeTask(nextTask) : null,
      pendingCount: numberValue(response, 'pending_count', 'pendingCount'),
      readyCount: numberValue(response, 'ready_count', 'readyCount'),
      runningCount: numberValue(response, 'running_count', 'runningCount'),
      waitingApprovalCount: numberValue(response, 'waiting_approval_count', 'waitingApprovalCount'),
      availableSlots: numberValue(response, 'available_slots', 'availableSlots'),
      maxSuspended: numberValue(response, 'max_suspended', 'maxSuspended'),
      checkedAt: textValue(response, 'checked_at', 'checkedAt'),
    };
  }

  static async getModelOptions(): Promise<TAnalysisTaskModelOption[]> {
    const response = await request<Array<{ model?: string; desc?: string }>>(
      '/api/v1/dih/model/list',
      {},
      'GET',
    );
    return response.map(item => ({ model: item.model || '', desc: item.desc || '' }));
  }

  static async getSkillOptions(): Promise<TAnalysisTaskSkillOption[]> {
    const response = await request<
      Array<{
        label?: string;
        value?: string;
        description?: string;
        agent_types?: string[];
        agentTypes?: string[];
      }>
    >('/api/v1/dih/skills/options', { enabled: true }, 'GET');
    return response.map(item => ({
      label: item.label || item.value || '',
      value: item.value || '',
      description: item.description || '',
      agentTypes: item.agent_types || item.agentTypes || [],
    }));
  }

  static async getPendingApprovals(
    id: number,
    page: number,
    perPage: number,
    silent = false,
  ): Promise<TPageRows<TMcpInvocation>> {
    const response = await request<{ rows?: RawData[]; total?: number }>(
      `${prefix}/${id}/approvals/list`,
      { page, perPage },
      'GET',
      { silent },
    );
    return {
      rows: (response.rows || []).map(normalizeInvocation),
      total: response.total || 0,
    };
  }

  static async decideApproval(
    id: number,
    requestId: string,
    decision: TAnalysisTaskDecision,
  ): Promise<TMcpInvocation> {
    const response = await request<RawData>(
      `${prefix}/${id}/approvals/${encodeURIComponent(requestId)}/decision`,
      { decision },
      'POST',
    );
    return normalizeInvocation(response);
  }

  static async getInvocations(
    id: number,
    executionId: string,
    page: number,
    perPage: number,
    silent = false,
  ): Promise<TPageRows<TMcpInvocation>> {
    const response = await request<{ rows?: RawData[]; total?: number }>(
      '/api/v1/dih/mcp/invocations/list',
      { analysisTaskId: id, executionId: executionId || undefined, page, perPage },
      'GET',
      { silent },
    );
    return {
      rows: (response.rows || []).map(normalizeInvocation),
      total: response.total || 0,
    };
  }
}
