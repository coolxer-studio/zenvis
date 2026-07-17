import type {
  AnalysisExtraData,
  PolicyRecord,
  ReportArtifact,
  ReportDocument,
} from '@/types/type-dih';

export const DATA_ACCESS_RECORD_EVENT = 'dihDataAccessRecordsUpdated';
export const DATA_VISUALIZATION_RECORD_EVENT = 'dihDataVisualizationRecordsUpdated';
export const DATA_ANALYSIS_RECORD_EVENT = 'dihAnalysisRecordsUpdated';
export const DATA_ANALYSIS_RECORD_REQUEST_EVENT = 'dihAnalysisRecordsRequested';
export const POLICY_RECORD_EVENT = 'dihPolicyRecordsUpdated';
export const POLICY_RECORD_REQUEST_EVENT = 'dihPolicyRecordsRequested';
export const POLICY_RECORD_ACTION_EVENT = 'dihPolicyRecordActionRequested';
export const DATA_REPORT_RECORD_EVENT = 'dihReportRecordsUpdated';
export const DATA_REPORT_RECORD_REQUEST_EVENT = 'dihReportRecordsRequested';
export const REPORT_QUICK_ACTION_EVENT = 'dihReportQuickActionRequested';
export const REPORT_EXTRA_DATA_CHANGED_EVENT = 'dihReportExtraDataChanged';
export const REPORT_SELECTION_REWRITE_COMPLETED_EVENT = 'dihReportSelectionRewriteCompleted';

export type DataAccessRecordEventDetail = {
  metadataConfigs?: unknown[];
  dataPushServices?: unknown[];
};

export type DataVisualizationRecordEventDetail = {
  chartLibrary?: unknown[];
  visualizationConfigs?: unknown[];
  dashboardConfigs?: unknown[];
  menuConfigs?: unknown[];
};

export type AnalysisRecordEventDetail = AnalysisExtraData;

export type PolicyRecordEventDetail = {
  records?: PolicyRecord[];
};

export type PolicyRecordActionEventDetail = {
  action?: 'trial' | 'apply';
  record?: PolicyRecord;
};

export type ReportRecordEventDetail = {
  currentDocument?: ReportDocument;
  documents?: ReportDocument[];
  artifacts?: ReportArtifact[];
  extraData?: string;
  sessionRecordId?: string;
  sessionId?: string;
};

export type ReportQuickActionEventDetail = {
  displayContent?: string;
  requestContent?: string;
  target?: 'document' | 'selection';
  actionKey?: string;
  selectionId?: string;
};

export type ReportExtraDataChangedEventDetail = {
  extraData?: string;
};

export type SelectionRewriteCompletedEventDetail = {
  selectionId?: string;
  content?: string;
};
