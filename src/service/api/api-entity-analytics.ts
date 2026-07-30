import { request } from '@/service/request-wrapper'

export type SafeEntityAnalyticsTool =
  | 'entity_overview'
  | 'entity_summary'
  | 'entity_trend'
  | 'entity_distribution'
  | 'entity_aggregate'
  | 'entity_histogram'
  | 'entity_scatter'
  | 'entity_value_statistics'
  | 'entity_relations'
  | 'entity_relation_timeline'

export type EntityAnalyticsResponse = {
  meta: Record<string, unknown>
  result: {
    columns?: unknown[]
    rows?: unknown[]
    [key: string]: unknown
  }
  echarts: {
    chart_type?: string
    option?: Record<string, unknown>
  }
}

const SAFE_ENDPOINTS: Record<SafeEntityAnalyticsTool, string> = {
  entity_overview: '/api/v1/entity/overview/query',
  entity_summary: '/api/v1/entity/summary/query',
  entity_trend: '/api/v1/entity/trend/query',
  entity_distribution: '/api/v1/entity/distribution/query',
  entity_aggregate: '/api/v1/entity/aggregate/query',
  entity_histogram: '/api/v1/entity/histogram/query',
  entity_scatter: '/api/v1/entity/scatter/query',
  entity_value_statistics: '/api/v1/entity/value-statistics/query',
  entity_relations: '/api/v1/entity/relations/query',
  entity_relation_timeline: '/api/v1/entity/relation-timeline/query',
}

export const isSafeEntityAnalyticsTool = (tool: string): tool is SafeEntityAnalyticsTool => {
  return Object.prototype.hasOwnProperty.call(SAFE_ENDPOINTS, tool)
}

export class EntityAnalyticsApi {
  static async query(
    tool: SafeEntityAnalyticsTool,
    payload: Record<string, unknown>,
  ): Promise<EntityAnalyticsResponse> {
    return request<EntityAnalyticsResponse>(
      SAFE_ENDPOINTS[tool],
      payload,
      'POST',
      { silent: true },
    )
  }
}
