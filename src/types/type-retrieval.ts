export type EntityResponse = {
  entity_list: TEntityListResponse[];
  selected_entity: string[];
}
export type TEntityListResponse = {
  name: string;
  label: string;
  description?: string;
};
export type TCriteriaList = {
  attribute: string;
  operator: string;
  value_list: string[];
}
export type RetrievalLogic = 'and' | 'or' | 'expression';
export type RetrievalDisplay = {
  entity: string;
  attribute_list: string[];
};
export type RetrievalSearchRequest = {
  id?: number | string;
  type?: 'normal' | 'advanced';
  entity?: string;
  criteria_list?: TCriteriaList[];
  criteria_logic?: RetrievalLogic;
  sql?: string;
  display_list?: RetrievalDisplay[];
  rule_name?: string;
  rule_description?: string;
  page?: number;
  size?: number;
  sort_by?: string;
  order?: 'asc' | 'desc';
};
export type AttributeResponse = {
  attribute_list: TAttributeListResponse[];
  select_attribute_list?: SelectAttributeItem[];
  criteria_logic?: RetrievalLogic;
  sql?: string;
  entity?: string;
}
export type SelectAttributeItem = {
  name: string;
  label: string;
  display_type?: string;
  operator_name?: string;
  aggregate_link?: boolean;
  value_list?: string[];
};
export type TAttributeListResponse = {
  display?: string;
  name: string;
  label: string;
  description: string;
  operator_list: OperatorItem[];
  title?: string;
  allName?: string;
  type?: string;
  retrieval_type?: string;
  display_type?: string;
  aggregate_link?: boolean;
};
export type OperatorItem = {
  name: string;
  label: string;
};

// 候选值响应
export type CandidateItem = {
  value: string;
  label: string;
  count?: number;
};
export type CandidateResponse = {
  candidate_list?: CandidateItem[];
  datalist?: string[];
  total?: number;
};

// 显示实体响应
export type DisplayEntityItem = {
  name: string;
  label: string;
  description?: string;
};
export type DisplayEntityResponse = {
  entity_list: DisplayEntityItem[];
};

// 显示属性响应
export type DisplayAttributeItem = {
  name: string;
  label: string;
  display?: boolean;
  type?: string;
  display_type?: string;
  aggregate_link?: boolean;
};
export type DisplayAttributeResponse = {
  attribute_list: DisplayAttributeItem[];
  select_attribute_list?: SelectAttributeItem[];
  entity?: string;
};

// 规则参数
export type RuleParams = RetrievalSearchRequest;

// 规则响应
export type RuleResponse = {
  success: boolean;
  message?: string;
  rule_id?: string;
};

// 规则详情响应
export type RuleDetailResponse = {
  id: string;
  name: string;
  description?: string;
  entity: string;
  criteria_list: TCriteriaList[];
  attribute_list: string[];
  criteria_logic?: RetrievalLogic;
  create_time?: string;
  update_time?: string;
};

// 删除规则参数
export type DeleteRuleParams = {
  rule_id?: string;
  id?: string;
  [key: string]: unknown;
};

// 删除规则响应
export type DeleteRuleResponse = {
  success: boolean;
  message?: string;
};
