import { request } from '@/service/request-wrapper';
import {
  EntityResponse,
  AttributeResponse,
  CandidateResponse,
  DisplayEntityResponse,
  DisplayAttributeResponse,
  RuleParams,
  RuleResponse,
  RuleDetailResponse,
  DeleteRuleParams,
  DeleteRuleResponse,
  RetrievalSearchRequest,
  AutoCompleteResponse,
  RetrievalRuleListItem,
} from '@/types/type-retrieval';
import { listResponse } from '@/types/type-public';

const prefix = '/api/v1/retrieval';

export class RetrievalService {
  static async getEntity(
    params: { rule_id?: number } = {},
    options: { signal?: AbortSignal; silent?: boolean } = {},
  ): Promise<EntityResponse> {
    return request<EntityResponse>(`${prefix}/entity/list`, params, 'GET', options);
  }

  static async getAttribute(params: { entity?: string; rule_id?: number }): Promise<AttributeResponse> {
    return request<AttributeResponse>(`${prefix}/attribute/list`, params, 'GET');
  }

  static async getCandidate(params: { attributeId?: number; entity?: string; attribute?: string; text?: string }): Promise<CandidateResponse> {
    return request<CandidateResponse>(`${prefix}/candidate/list`, params, 'GET');
  }

  static async autoComplete(params: { entity: string; attribute: string; term?: string }): Promise<AutoCompleteResponse> {
    const { entity, attribute, term } = params;
    const url = `/api/v1/entity/${encodeURIComponent(entity)}/${encodeURIComponent(attribute)}/auto-complete`;
    return request<AutoCompleteResponse>(url, { term }, 'GET', { silent: true });
  }

  static async getDisplayEntity(): Promise<DisplayEntityResponse> {
    return request<DisplayEntityResponse>(`${prefix}/display/entity/list`, '', 'GET');
  }

  static async getDisplayAttribute(): Promise<DisplayAttributeResponse> {
    return request<DisplayAttributeResponse>(`${prefix}/display/attribute/list`, '', 'GET');
  }

  static async getListByCriteria(
    params: RetrievalSearchRequest,
    options: { signal?: AbortSignal; silent?: boolean } = {},
  ): Promise<listResponse<Record<string, unknown>>> {
    return request<listResponse<Record<string, unknown>>>(`${prefix}/do`, params, 'POST', options);
  }

  static async createRule(params: RuleParams): Promise<RuleResponse> {
    return request<RuleResponse>(`${prefix}/rule/create`, params);
  }

  static async updateRule(params: RuleParams & { id: string | number }): Promise<RuleResponse> {
    return request<RuleResponse>(`${prefix}/rule/update`, params);
  }

  static async ruleDetail(
    params: { id: number },
    options: { signal?: AbortSignal; silent?: boolean } = {},
  ): Promise<RuleDetailResponse> {
    return request<RuleDetailResponse>(`${prefix}/rule/detail`, params, 'GET', options);
  }

  static async getRule(): Promise<listResponse<RetrievalRuleListItem>> {
    return request<listResponse<RetrievalRuleListItem>>(`${prefix}/rule/list`, {}, 'GET');
  }

  static async getCol(
    params: { entity?: string; rule_id?: string | number },
    options: { signal?: AbortSignal; silent?: boolean } = {},
  ): Promise<DisplayAttributeResponse> {
    return request<DisplayAttributeResponse>(`${prefix}/display/attribute/list`, params, 'GET', options);
  }

  static async deleteRule(params: DeleteRuleParams): Promise<DeleteRuleResponse> {
    return request<DeleteRuleResponse>(`${prefix}/rule/delete`, params);
  }
}
