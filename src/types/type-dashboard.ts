export type TEntityStatisticsRange = 'TODAY' | 'YESTERDAY' | 'LAST_7_DAYS';

export type TSystemOverviewSummary = {
  entity_count: number;
  push_task_count: number | null;
  analysis_task_count: number;
  business_service_count: number;
};

export type TSystemNotice = {
  key: string;
  count: number;
  info: string;
  level: 'NORMAL' | 'WARNING' | 'ERROR';
};

export type TServiceHealth = {
  ratio: number | null;
  instance_count: number;
  up_count: number;
  abnormal_count: number;
  event_count_24h: number;
};

export type TAnalysisTaskStatus = {
  status: string;
  description: string;
  count: number;
};

export type TBusinessServiceStatus = {
  status: 'UP' | 'DEGRADED' | 'DOWN' | 'OFFLINE';
  description: string;
  count: number;
};

export type TRecentAnalysisTask = {
  id: number;
  name: string;
  status: string | null;
  status_description: string | null;
  update_time: string | null;
};

export type TSystemOverviewResponse = {
  checked_at: string;
  status: 'HEALTHY' | 'DEGRADED';
  status_description: string;
  summary: TSystemOverviewSummary;
  notices: TSystemNotice[];
  service_health: TServiceHealth;
  business_service_status: TBusinessServiceStatus[];
  analysis_task_status: TAnalysisTaskStatus[];
  recent_analysis_tasks: TRecentAnalysisTask[];
  push_task_source_available: boolean;
};

export type TEntitySeries = {
  name: string;
  label: string;
  data: number[];
  total: number;
};

export type TEntityRanking = {
  name: string;
  label: string;
  count: number;
};

export type TSkippedEntity = {
  name: string;
  label: string;
  reason: string;
  message: string;
};

export type TEntityStatisticsResponse = {
  range: TEntityStatisticsRange;
  start_time: string;
  end_time: string;
  granularity: 'HOUR' | 'DAY';
  x_axis: string[];
  series: TEntitySeries[];
  ranking: TEntityRanking[];
  omitted_entity_count: number;
  skipped_entities: TSkippedEntity[];
};
