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
} from "@/types/type-retrieval";
import { listResponse } from "@/types/type-public";

const prefix = '/api/v1/retrieval';

export class RetrievalService {
  static async getEntity(params: { entity?: string }): Promise<EntityResponse> {
    return request<EntityResponse>(`${prefix}/entity/list`, params, 'GET');
  }

  static async getAttribute(params: { entity?: string }): Promise<AttributeResponse> {
    return request<AttributeResponse>(`${prefix}/attribute/list`, params, 'GET');
  }

  static async getCandidate(params: { entity?: string; attribute?: string; text?: string }): Promise<CandidateResponse> {
    return request<CandidateResponse>(`${prefix}/candidate/list`, params, 'GET');
  }

  static async getDisplayEntity(): Promise<DisplayEntityResponse> {
    return request<DisplayEntityResponse>(`${prefix}/display/entity/list`, '', 'GET');
  }

  static async getDisplayAttribute(): Promise<DisplayAttributeResponse> {
    return request<DisplayAttributeResponse>(`${prefix}/display/attribute/list`, '', 'GET');
  }

  static async getListByCriteria(params: RetrievalSearchRequest): Promise<listResponse<Record<string, unknown>>> {
    return request<listResponse<any>>(`${prefix}/do`, params);
  }

  static async createRule(params: RuleParams): Promise<RuleResponse> {
    return request<RuleResponse>(`${prefix}/rule/create`, params);
  }

  static async updateRule(params: RuleParams & { rule_id?: string; id?: string | number }): Promise<RuleResponse> {
    return request<RuleResponse>(`${prefix}/rule/update`, params);
  }

  static async ruleDetail(params: { rule_id?: string }): Promise<RuleDetailResponse> {
    return request<RuleDetailResponse>(`${prefix}/rule/get`, params, 'GET');
  }

  static async getRule(): Promise<listResponse<RuleDetailResponse>> {
    return request<listResponse<RuleDetailResponse>>(`${prefix}/rule/list`, '', 'GET');
  }

  static async getCol(params: { entity?: string; rule_id?: string | number }): Promise<DisplayAttributeResponse> {
    return request<DisplayAttributeResponse>(`${prefix}/display/attribute/list`, params, 'GET');
  }

  static async deleteRule(params: DeleteRuleParams): Promise<DeleteRuleResponse> {
    return request<DeleteRuleResponse>(`${prefix}/rule/delete`, params);
  }
}
