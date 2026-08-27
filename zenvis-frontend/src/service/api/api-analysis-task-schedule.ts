import { request } from '../request-wrapper';
import type {
  TAnalysisTaskForm,
  TAnalysisTaskSchedule,
  TAnalysisTaskScheduleSearch,
  TPageRows,
} from '@/types/type-analysis-task';

const prefix = '/api/v1/system/analysis-task-schedule';
type RawData = Record<string, unknown>;

const textValue = (data: RawData, snakeKey: string, camelKey = snakeKey): string => {
  const value = data[snakeKey] ?? data[camelKey];
  return value === null || value === undefined ? '' : String(value);
};

const numberValue = (data: RawData, snakeKey: string, camelKey = snakeKey): number => {
  const value = Number(data[snakeKey] ?? data[camelKey] ?? 0);
  return Number.isFinite(value) ? value : 0;
};

const booleanValue = (data: RawData, snakeKey: string, camelKey = snakeKey): boolean =>
  Boolean(data[snakeKey] ?? data[camelKey]);

const normalizeSchedule = (data: RawData): TAnalysisTaskSchedule => ({
  id: numberValue(data, 'id'),
  name: textValue(data, 'name'),
  description: textValue(data, 'description'),
  model: textValue(data, 'model') || 'auto',
  prompt: textValue(data, 'prompt'),
  priority: numberValue(data, 'priority'),
  approvalMode: (textValue(data, 'approval_mode', 'approvalMode') ||
    'MANUAL') as TAnalysisTaskSchedule['approvalMode'],
  cronExpression: textValue(data, 'cron_expression', 'cronExpression'),
  enabled: booleanValue(data, 'enabled'),
  skillIds: ((data.skill_ids ?? data.skillIds ?? []) as unknown[]).map(String),
  nextFireTime: textValue(data, 'next_fire_time', 'nextFireTime'),
  lastFireTime: textValue(data, 'last_fire_time', 'lastFireTime'),
  generatedCount: numberValue(data, 'generated_count', 'generatedCount'),
  lastError: textValue(data, 'last_error', 'lastError'),
  createTime: textValue(data, 'create_time', 'createTime'),
  updateTime: textValue(data, 'update_time', 'updateTime'),
  createBy: numberValue(data, 'create_by', 'createBy') || undefined,
});

const schedulePayload = (data: TAnalysisTaskForm) => ({
  name: data.name.trim(),
  description: data.description.trim() || null,
  model: data.model || 'auto',
  prompt: data.prompt.trim(),
  priority: data.priority ?? 0,
  cron_expression: data.cronExpression.trim(),
  enabled: data.enabled,
  approval_mode: data.approvalMode,
  skill_ids: data.skillIds,
});

export class AnalysisTaskScheduleService {
  static async getList(
    params: TAnalysisTaskScheduleSearch,
    silent = false,
  ): Promise<TPageRows<TAnalysisTaskSchedule>> {
    const response = await request<{ rows?: RawData[]; total?: number }>(
      `${prefix}/list`,
      {
        name: params.name || undefined,
        enabled: params.enabled === '' ? undefined : params.enabled,
        page: params.page,
        perPage: params.perPage,
      },
      'GET',
      { silent },
    );
    return {
      rows: (response.rows || []).map(normalizeSchedule),
      total: response.total || 0,
    };
  }

  static async create(data: TAnalysisTaskForm): Promise<TAnalysisTaskSchedule> {
    const response = await request<RawData>(`${prefix}/add`, schedulePayload(data), 'POST');
    return normalizeSchedule(response);
  }

  static async update(id: number, data: TAnalysisTaskForm): Promise<void> {
    await request<unknown>(`${prefix}/${id}/update`, schedulePayload(data), 'POST');
  }

  static async setEnabled(id: number, enabled: boolean): Promise<TAnalysisTaskSchedule> {
    const response = await request<RawData>(`${prefix}/${id}/enabled`, { enabled }, 'POST');
    return normalizeSchedule(response);
  }

  static async remove(id: number): Promise<void> {
    await request<unknown>(`${prefix}/${id}`, {}, 'DELETE');
  }
}
