export type RetrievalType = 'normal' | 'advanced';
export type RetrievalPersistedType = RetrievalType | 'legacy_sql' | 'invalid';
export type RetrievalLogic = 'and' | 'or' | 'expression';

export type TEntityListResponse = {
  name: string;
  label: string;
  description?: string;
};

export type EntityResponse = {
  entity_list: TEntityListResponse[];
  selected_entity: string[];
};

export type TCriteriaList = {
  attribute: string;
  operator: string;
  value_list: string[];
};

export type RetrievalDisplay = {
  entity: string;
  attribute_list: string[];
};

export type RetrievalSearchRequest = {
  id?: number | string;
  type?: RetrievalType;
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

export type OperatorItem = {
  name: string;
  label: string;
};

export type TAttributeListResponse = {
  name: string;
  label: string;
  description?: string;
  operator_list: OperatorItem[];
  retrieval_type?: string;
  search_type?: string;
  display_type?: string;
  link_template?: string;
  copyable?: boolean;
  auto_complete?: boolean;
};

export type SelectAttributeItem = {
  name: string;
  label: string;
  display_type?: string;
  operator_name?: string;
  link_template?: string;
  copyable?: boolean;
  value_list?: string[];
};

export type AttributeResponse = {
  attribute_list: TAttributeListResponse[];
  select_attribute_list?: SelectAttributeItem[];
  criteria_logic?: RetrievalLogic;
  sql?: string;
  entity?: string;
};

export type AutoCompleteOption = {
  label: string;
  value: string;
};

export type AutoCompleteResponse = {
  options: AutoCompleteOption[];
};

export type CandidateResponse = {
  datalist?: string[];
  total?: number;
};

export type DisplayEntityResponse = {
  entity_list: TEntityListResponse[];
};

export type DisplayAttributeResponse = AttributeResponse;

export type RetrievalRuleIssue = {
  code: string;
  scope: 'rule' | 'entity' | 'criteria' | 'display' | string;
  entity?: string;
  attribute?: string;
  message: string;
};

export type RetrievalRuleConfig = {
  type: RetrievalPersistedType;
  entity: string;
  criteria_list: TCriteriaList[];
  criteria_logic: RetrievalLogic;
  sql?: string;
  display_list: RetrievalDisplay[];
};

export type RetrievalRuleListItem = {
  id: number;
  name: string;
  description?: string;
  create_time?: string;
  update_time?: string;
  status: 'valid' | 'invalid';
  issue_count: number;
};

export type RetrievalRuleDetail = {
  id: number;
  name: string;
  description?: string;
  create_time?: string;
  update_time?: string;
  config: RetrievalRuleConfig;
  status: 'valid' | 'invalid';
  issues: RetrievalRuleIssue[];
  entity_list: TEntityListResponse[];
  attribute_list: TAttributeListResponse[];
};

export type RuleParams = RetrievalSearchRequest;
export type RuleResponse = { id: number };
export type RuleDetailResponse = RetrievalRuleDetail;
export type DeleteRuleParams = { id: number | string };
export type DeleteRuleResponse = void;

export type RetrievalTableColumn = {
  title: string;
  dataIndex: string;
  linkTemplate?: string;
  copyable?: boolean;
  resizable: boolean;
  width: number;
  minWidth: number;
  fixed: false | 'left' | 'right';
  sorter: boolean;
  firstIndex: number;
  type?: string;
};

export type RetrievalTableState = {
  loading: boolean;
  sourceColumns: RetrievalTableColumn[];
  disabledTitles: string[];
  selectedCol: RetrievalTableColumn[];
  selectedKeyCol: string[];
  columns: RetrievalTableColumn[];
  data: Record<string, unknown>[];
  entity?: string;
  width: number;
  minWidth: number;
  pagination: import('./type-public').TPagination;
};

export type RetrievalTableSorter = {
  prop?: string;
  field?: string;
  order?: 'ascending' | 'descending' | 'ascend' | 'descend' | null;
};

export type RetrievalTableChange = {
  pagination?: Partial<import('./type-public').TPagination>;
  sorter?: RetrievalTableSorter;
};
