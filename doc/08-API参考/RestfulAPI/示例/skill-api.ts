// ZenVis RESTful API TypeScript example: Skill management.
export interface ResponseWrap<T> {
  status: number;
  msg: string;
  data: T;
}

export interface PageRowsVo<T> {
  rows: T[];
  total: number;
}

export interface SkillVo {
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
}

export interface SkillDetailVo extends SkillVo {
  content: string;
}

export interface AgentSkillVo {
  skillId: string;
  agentType: string;
  label: string;
  name?: string;
  description?: string;
  enabled: boolean;
  order: number;
  path?: string;
  updateTime?: string;
}

export interface SkillSearchParams {
  keyword?: string;
  agentType?: string;
  enabled?: boolean;
  page?: number;
  perPage?: number;
}

export interface SingleValueVo {
  value: string;
}

const BASE_URL = "/api/v1/dih/skills";

function buildQuery(params?: object) {
  const query = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      query.append(key, String(value));
    }
  });
  const queryString = query.toString();
  return queryString ? `?${queryString}` : "";
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers || {})
    },
    ...init
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const body = (await response.json()) as ResponseWrap<T>;
  if (body.status !== 0) {
    throw new Error(body.msg || "请求失败");
  }
  return body.data;
}

export const skillApi = {
  list(params?: SkillSearchParams) {
    return request<PageRowsVo<SkillVo>>(`${BASE_URL}/list${buildQuery(params)}`);
  },

  view(id: string) {
    return request<SkillDetailVo>(`${BASE_URL}/${encodeURIComponent(id)}/view`);
  },

  reload() {
    return request<SkillVo[]>(`${BASE_URL}/reload`, { method: "POST" });
  },

  enable(id: string) {
    return request<SkillVo>(`${BASE_URL}/${encodeURIComponent(id)}/enable`, { method: "POST" });
  },

  disable(id: string) {
    return request<SkillVo>(`${BASE_URL}/${encodeURIComponent(id)}/disable`, { method: "POST" });
  },

  agents(enabled?: boolean) {
    return request<AgentSkillVo[]>(`${BASE_URL}/agents${buildQuery({ enabled })}`);
  },

  agentPrompt(agentType: string) {
    return request<SingleValueVo>(`${BASE_URL}/agent/${encodeURIComponent(agentType)}/prompt`);
  }
};
