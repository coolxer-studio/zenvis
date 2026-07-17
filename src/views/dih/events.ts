import { onMounted, onUnmounted } from 'vue';
import type {
  AnalysisExtraData,
  ChatSession,
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
export const NEW_CHAT_CREATED_EVENT = 'newChatCreated';
export const DATA_VISUALIZATION_CHART_DATA_EVENT = 'dataVisualizationChartData';

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
  actionKey?: string;
  content?: string;
};

export type DihChatListItem = Pick<ChatSession, 'id' | 'sessionId' | 'title' | 'type' | 'pin'>
  & Partial<Omit<ChatSession, 'id' | 'sessionId' | 'title' | 'type' | 'pin'>>;

export type NewChatCreatedEventDetail = {
  chatItem: DihChatListItem;
};

export type DataVisualizationChartDataEventDetail = {
  chartType: string;
  option: unknown;
  rawData?: unknown;
  columns?: unknown;
};

export type DihEventPayloadMap = {
  [DATA_ACCESS_RECORD_EVENT]: DataAccessRecordEventDetail;
  [DATA_VISUALIZATION_RECORD_EVENT]: DataVisualizationRecordEventDetail;
  [DATA_ANALYSIS_RECORD_EVENT]: AnalysisRecordEventDetail;
  [DATA_ANALYSIS_RECORD_REQUEST_EVENT]: undefined;
  [POLICY_RECORD_EVENT]: PolicyRecordEventDetail;
  [POLICY_RECORD_REQUEST_EVENT]: undefined;
  [POLICY_RECORD_ACTION_EVENT]: PolicyRecordActionEventDetail;
  [DATA_REPORT_RECORD_EVENT]: ReportRecordEventDetail;
  [DATA_REPORT_RECORD_REQUEST_EVENT]: undefined;
  [REPORT_QUICK_ACTION_EVENT]: ReportQuickActionEventDetail;
  [REPORT_EXTRA_DATA_CHANGED_EVENT]: ReportExtraDataChangedEventDetail;
  [REPORT_SELECTION_REWRITE_COMPLETED_EVENT]: SelectionRewriteCompletedEventDetail;
  [NEW_CHAT_CREATED_EVENT]: NewChatCreatedEventDetail;
  [DATA_VISUALIZATION_CHART_DATA_EVENT]: DataVisualizationChartDataEventDetail;
};

type DihEventName = keyof DihEventPayloadMap;
type DihEventHandler<K extends DihEventName> = (
  detail: DihEventPayloadMap[K],
) => void | Promise<void>;
type DihEventArgs<K extends DihEventName> = DihEventPayloadMap[K] extends undefined
  ? []
  : [detail: DihEventPayloadMap[K]];

export const emitDihEvent = <K extends DihEventName>(
  eventName: K,
  ...args: DihEventArgs<K>
): void => {
  window.dispatchEvent(new CustomEvent(eventName, { detail: args[0] }));
};

export const onDihEvent = <K extends DihEventName>(
  eventName: K,
  handler: DihEventHandler<K>,
): (() => void) => {
  const listener = (event: Event) => {
    void handler((event as CustomEvent<DihEventPayloadMap[K]>).detail);
  };
  window.addEventListener(eventName, listener);
  return () => window.removeEventListener(eventName, listener);
};

export const useDihEventListener = <K extends DihEventName>(
  eventName: K,
  handler: DihEventHandler<K>,
): void => {
  let stopListening: (() => void) | undefined;
  onMounted(() => {
    stopListening = onDihEvent(eventName, handler);
  });
  onUnmounted(() => {
    stopListening?.();
    stopListening = undefined;
  });
};
