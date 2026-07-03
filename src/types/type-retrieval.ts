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
  valueList: string[];
}
export type AttributeResponse = {
  attribute_list: TAttributeListResponse[];
  select_attribute_list: object[];
}
export type TAttributeListResponse = {
  display?: string;
  name: string;
  label: string;
  description: string;
  operator_list: object[];
  title?: string;
  allName?: string;
  type?: string;
  retrieval_type?: string;
};

// 候选值响应
export type CandidateItem = {
  value: string;
  label: string;
  count?: number;
};
export type CandidateResponse = {
  candidate_list: CandidateItem[];
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
};
export type DisplayAttributeResponse = {
  attribute_list: DisplayAttributeItem[];
};

// 规则参数
export type RuleParams = {
  name?: string;
  description?: string;
  entity?: string;
  criteria_list?: TCriteriaList[];
  attribute_list?: string[];
  [key: string]: unknown;
};

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
