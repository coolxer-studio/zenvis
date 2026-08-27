import type { EChartsOption } from 'echarts';

export type TEntityAnalyticsColumn = {
  name: string;
  label?: string;
  type?: string;
};

export type TEntityAnalyticsResult = {
  columns: TEntityAnalyticsColumn[];
  rows: Record<string, unknown>[];
  [key: string]: unknown;
};

export type TEntityAnalyticsResponse = {
  meta: {
    query_type: string;
    time_zone: string;
    preset: string;
    start_time?: string | null;
    end_time?: string | null;
    comparison: string;
    granularity?: string | null;
    result_count: number;
    [key: string]: unknown;
  };
  result: TEntityAnalyticsResult;
  echarts: {
    chart_type: 'bar' | 'line' | 'graph';
    option: EChartsOption;
  };
};
