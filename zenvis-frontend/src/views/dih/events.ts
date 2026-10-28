import { onMounted, onUnmounted } from 'vue';
import type {
  ConfigurationExtraData,
  ChatSession,
  ConfigRecord,
  DataAnalysisExtraData,
  ReportArtifact,
  ReportDocument,
  ReportRevision,
  ReportSourceRef,
  ReportAction,
} from '@/types/type-dih';

export const DATA_METADATA_CONFIG_RECORD_EVENT = 'dihMetadataConfigRecordsUpdated';
export const DATA_METADATA_CONFIG_RECORD_REQUEST_EVENT = 'dihMetadataConfigRecordsRequested';
export const DATA_PUSH_SERVICE_RECORD_EVENT = 'dihDataPushServiceRecordsUpdated';
export const DATA_PUSH_SERVICE_RECORD_REQUEST_EVENT = 'dihDataPushServiceRecordsRequested';
export const DATA_VISUALIZATION_CHART_RECORD_EVENT = 'dihVisualizationChartRecordsUpdated';
export const DATA_VISUALIZATION_CHART_RECORD_REQUEST_EVENT =
  'dihVisualizationChartRecordsRequested';
export const DATA_VISUALIZATION_CHART_EXTRA_DATA_CHANGED_EVENT =
  'dihVisualizationChartExtraDataChanged';
export const DATA_VISUALIZATION_APP_RECORD_EVENT = 'dihVisualizationAppRecordsUpdated';
export const DATA_VISUALIZATION_APP_RECORD_REQUEST_EVENT =
  'dihVisualizationAppRecordsRequested';
export const DATA_VISUALIZATION_DASHBOARD_RECORD_EVENT =
  'dihVisualizationDashboardRecordsUpdated';
export const DATA_VISUALIZATION_DASHBOARD_RECORD_REQUEST_EVENT =
  'dihVisualizationDashboardRecordsRequested';
export const DATA_ANALYSIS_RECORD_EVENT = 'dihDataAnalysisRecordsUpdated';
export const DATA_ANALYSIS_RECORD_REQUEST_EVENT = 'dihDataAnalysisRecordsRequested';
export const CONFIG_RECORD_EVENT = 'dihConfigRecordsUpdated';
export const CONFIG_RECORD_REQUEST_EVENT = 'dihConfigRecordsRequested';
export const CONFIG_RECORD_ACTION_EVENT = 'dihConfigRecordActionRequested';
export const DATA_REPORT_RECORD_EVENT = 'dihReportRecordsUpdated';
export const DATA_REPORT_RECORD_REQUEST_EVENT = 'dihReportRecordsRequested';
export const REPORT_QUICK_ACTION_EVENT = 'dihReportQuickActionRequested';
export const REPORT_EXTRA_DATA_CHANGED_EVENT = 'dihReportExtraDataChanged';
export const REPORT_SELECTION_REWRITE_COMPLETED_EVENT = 'dihReportSelectionRewriteCompleted';
export const NEW_CHAT_CREATED_EVENT = 'newChatCreated';
export const VISUALIZATION_CHART_DATA_EVENT = 'visualizationChartData';

export type MetadataConfigRecordEventDetail = {
  records?: unknown[];
};

export type DataPushServiceRecordEventDetail = {
  records?: unknown[];
};

export type VisualizationChartRecordEventDetail = {
  chartLibrary?: unknown[];
  extraData?: string;
  sessionRecordId?: string;
};

export type VisualizationAppRecordEventDetail = {
  configs?: unknown[];
  menus?: unknown[];
  extraData?: string;
  sessionRecordId?: string;
};

export type VisualizationDashboardRecordEventDetail = {
  configs?: unknown[];
  dashboards?: unknown[];
  menus?: unknown[];
  extraData?: string;
  sessionRecordId?: string;
};

export type VisualizationChartExtraDataChangedEventDetail = {
  extraData?: string;
};

export type DataAnalysisRecordEventDetail = DataAnalysisExtraData;

export type ConfigRecordEventDetail = ConfigurationExtraData;

export type ConfigRecordActionEventDetail = {
  action?: 'trial' | 'apply';
  record?: ConfigRecord;
};

export type ReportRecordEventDetail = {
  currentDocument?: ReportDocument;
  documents?: ReportDocument[];
  artifacts?: ReportArtifact[];
  revisions?: ReportRevision[];
  materials?: ReportSourceRef[];
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
  reportAction?: ReportAction;
};

export type ReportExtraDataChangedEventDetail = {
  extraData?: string;
};

export type SelectionRewriteCompletedEventDetail = {
  selectionId?: string;
  actionKey?: string;
  content?: string;
  documentId?: string;
  baseRevision?: number;
  selectionHash?: string;
  contentHash?: string;
};

export type DihChatListItem = Pick<ChatSession, 'id' | 'sessionId' | 'title' | 'type' | 'pin'>
  & Partial<Omit<ChatSession, 'id' | 'sessionId' | 'title' | 'type' | 'pin'>>;

export type NewChatCreatedEventDetail = {
  chatItem: DihChatListItem;
};

export type VisualizationChartDataEventDetail = {
  chartType: string;
  option: unknown;
  rawData?: unknown;
  columns?: unknown;
};

export type DihEventPayloadMap = {
  [DATA_METADATA_CONFIG_RECORD_EVENT]: MetadataConfigRecordEventDetail;
  [DATA_METADATA_CONFIG_RECORD_REQUEST_EVENT]: undefined;
  [DATA_PUSH_SERVICE_RECORD_EVENT]: DataPushServiceRecordEventDetail;
  [DATA_PUSH_SERVICE_RECORD_REQUEST_EVENT]: undefined;
  [DATA_VISUALIZATION_CHART_RECORD_EVENT]: VisualizationChartRecordEventDetail;
  [DATA_VISUALIZATION_CHART_RECORD_REQUEST_EVENT]: undefined;
  [DATA_VISUALIZATION_CHART_EXTRA_DATA_CHANGED_EVENT]:
    VisualizationChartExtraDataChangedEventDetail;
  [DATA_VISUALIZATION_APP_RECORD_EVENT]: VisualizationAppRecordEventDetail;
  [DATA_VISUALIZATION_APP_RECORD_REQUEST_EVENT]: undefined;
  [DATA_VISUALIZATION_DASHBOARD_RECORD_EVENT]: VisualizationDashboardRecordEventDetail;
  [DATA_VISUALIZATION_DASHBOARD_RECORD_REQUEST_EVENT]: undefined;
  [DATA_ANALYSIS_RECORD_EVENT]: DataAnalysisRecordEventDetail;
  [DATA_ANALYSIS_RECORD_REQUEST_EVENT]: undefined;
  [CONFIG_RECORD_EVENT]: ConfigRecordEventDetail;
  [CONFIG_RECORD_REQUEST_EVENT]: undefined;
  [CONFIG_RECORD_ACTION_EVENT]: ConfigRecordActionEventDetail;
  [DATA_REPORT_RECORD_EVENT]: ReportRecordEventDetail;
  [DATA_REPORT_RECORD_REQUEST_EVENT]: undefined;
  [REPORT_QUICK_ACTION_EVENT]: ReportQuickActionEventDetail;
  [REPORT_EXTRA_DATA_CHANGED_EVENT]: ReportExtraDataChangedEventDetail;
  [REPORT_SELECTION_REWRITE_COMPLETED_EVENT]: SelectionRewriteCompletedEventDetail;
  [NEW_CHAT_CREATED_EVENT]: NewChatCreatedEventDetail;
  [VISUALIZATION_CHART_DATA_EVENT]: VisualizationChartDataEventDetail;
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
