import { request } from '@/service/request-wrapper';
import type {
  TEntityStatisticsRange,
  TEntityStatisticsResponse,
  TSystemOverviewResponse,
} from '@/types/type-dashboard';

const prefix = '/api/v1/dashboard/home';

type TDashboardRequestOptions = {
  silent?: boolean;
  signal?: AbortSignal;
};

export class HomeService {
  static async getOverview(
    options: TDashboardRequestOptions = {},
  ): Promise<TSystemOverviewResponse> {
    return request<TSystemOverviewResponse>(`${prefix}/overview`, {}, 'GET', options);
  }

  static async getEntityStatistics(
    range: TEntityStatisticsRange,
    options: TDashboardRequestOptions = {},
  ): Promise<TEntityStatisticsResponse> {
    return request<TEntityStatisticsResponse>(
      `${prefix}/entity-statistics`,
      { range },
      'GET',
      options,
    );
  }
}
