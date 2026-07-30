import { nextTick, onMounted, watch } from 'vue';
import type { Ref } from 'vue';
import { ElMessage } from 'element-plus';
import type {
  ChatMessage,
  ConfigRecord,
  DataAnalysisRecord,
} from '@/types/type-dih';
import {
  CONFIG_RECORD_ACTION_EVENT,
  CONFIG_RECORD_EVENT,
  CONFIG_RECORD_REQUEST_EVENT,
  DATA_ACCESS_RECORD_EVENT,
  DATA_ANALYSIS_RECORD_EVENT,
  DATA_ANALYSIS_RECORD_REQUEST_EVENT,
  DATA_REPORT_RECORD_EVENT,
  DATA_REPORT_RECORD_REQUEST_EVENT,
  DATA_VISUALIZATION_RECORD_EVENT,
  DATA_VISUALIZATION_EXTRA_DATA_CHANGED_EVENT,
  REPORT_EXTRA_DATA_CHANGED_EVENT,
  REPORT_QUICK_ACTION_EVENT,
  REPORT_SELECTION_REWRITE_COMPLETED_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events';
import type {
  ConfigRecordActionEventDetail,
  ReportExtraDataChangedEventDetail,
  ReportQuickActionEventDetail,
} from '../events';
import type { SendMessageOptions } from './use-chat-stream';

export type DihPanelRecord = Record<string, unknown> & {
  id?: string;
  name?: string;
  status?: string;
};

type UsePanelRecordSyncOptions = {
  messages: Ref<ChatMessage[]>;
  chatSessionExtraData: Ref<string>;
  chatSessionRecordId: Ref<string>;
  chatSessionId: Ref<string>;
  isStreamingResponse: Ref<boolean>;
  sendMessage: (options?: SendMessageOptions) => Promise<void>;
};

const asObject = (value: unknown): Record<string, unknown> => {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
};

const asRecordList = (value: unknown): DihPanelRecord[] => {
  return Array.isArray(value)
    ? value.filter(item => item && typeof item === 'object').map(item => item as DihPanelRecord)
    : [];
};

const upsertById = (items: DihPanelRecord[], record: DihPanelRecord) => {
  const id = String(record.id || record.recordId || record.fileName || record.serviceTaskId || record.taskId || record.name || '');
  if (!id) {
    return [...items, record];
  }
  const next = items.filter(item => String(item.id || item.recordId || item.fileName || item.serviceTaskId || item.taskId || item.name || '') !== id);
  next.push(record);
  return next;
};

const upsertInto = (items: DihPanelRecord[], record: DihPanelRecord) => {
  items.splice(0, items.length, ...upsertById(items, record));
};

const textValue = (value: unknown, fallback = '') => {
  if (typeof value === 'string') {
    return value;
  }
  if (value === undefined || value === null) {
    return fallback;
  }
  return String(value);
};

const prettyTextValue = (value: unknown, fallback = '') => {
  if (typeof value === 'string') {
    return value.trim() || fallback;
  }
  if (value === undefined || value === null || value === '') {
    return fallback;
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
};

export const usePanelRecordSync = ({
  messages,
  chatSessionExtraData,
  chatSessionRecordId,
  chatSessionId,
  isStreamingResponse,
  sendMessage,
}: UsePanelRecordSyncOptions) => {
  const parseSessionExtraData = () => {
    if (!chatSessionExtraData.value.trim()) {
      return {};
    }
    try {
      return asObject(JSON.parse(chatSessionExtraData.value));
    } catch {
      return {};
    }
  };

  const addChartRecordToExtraData = (record: DihPanelRecord) => {
    const extraData = asObject(parseSessionExtraData());
    const dataVisualization = asObject(extraData.dataVisualization);
    extraData.dataVisualization = {
      ...dataVisualization,
      chartLibrary: upsertById(asRecordList(dataVisualization.chartLibrary), record),
    };
    return JSON.stringify(extraData);
  };

  const extractDataAccessRecords = () => {
    const metadataConfigs: DihPanelRecord[] = [];
    const dataPushServices: DihPanelRecord[] = [];
    const dataAccess = asObject(parseSessionExtraData().dataAccess);
    asRecordList(dataAccess.metadataConfigs).forEach(record => {
      upsertInto(metadataConfigs, record);
    });
    asRecordList(dataAccess.dataPushServices).forEach(record => {
      upsertInto(dataPushServices, record);
    });
    return {
      metadataConfigs,
      dataPushServices,
    };
  };

  const extractDataVisualizationRecords = () => {
    const dataVisualization = asObject(parseSessionExtraData().dataVisualization);
    return {
      chartLibrary: asRecordList(dataVisualization.chartLibrary),
      visualizationConfigs: asRecordList(dataVisualization.visualizationConfigs),
      dashboardConfigs: asRecordList(dataVisualization.dashboardConfigs),
      menuConfigs: asRecordList(dataVisualization.menuConfigs),
      extraData: chatSessionExtraData.value,
      sessionRecordId: chatSessionRecordId.value,
    };
  };

  const extractDataAnalysisRecords = () => {
    const records: DataAnalysisRecord[] = [];
    const dataAnalysis = asObject(parseSessionExtraData().dataAnalysis);
    asRecordList(dataAnalysis.records).forEach(record => {
      upsertInto(records as DihPanelRecord[], record);
    });
    return {
      records,
      datasetRecords: asRecordList(dataAnalysis.datasetRecords),
      serviceResults: asRecordList(dataAnalysis.serviceResults),
      reportTimeline: asRecordList(dataAnalysis.reportTimeline),
    };
  };

  const extractConfigurationRecords = () => {
    const records: ConfigRecord[] = [];
    const configuration = asObject(parseSessionExtraData().configuration);
    asRecordList(configuration.records).forEach(record => {
      upsertInto(records as DihPanelRecord[], record);
    });
    return { records };
  };

  const extractReportRecords = () => {
    const report = asObject(parseSessionExtraData().report);
    return {
      currentDocument: asObject(report.currentDocument),
      documents: asRecordList(report.documents),
      artifacts: asRecordList(report.artifacts),
      extraData: chatSessionExtraData.value,
      sessionRecordId: chatSessionRecordId.value,
      sessionId: chatSessionId.value,
    };
  };

  const publishDataAccessRecords = () => {
    emitDihEvent(DATA_ACCESS_RECORD_EVENT, extractDataAccessRecords());
  };

  const publishDataVisualizationRecords = () => {
    emitDihEvent(DATA_VISUALIZATION_RECORD_EVENT, extractDataVisualizationRecords());
  };

  const publishDataAnalysisRecords = () => {
    emitDihEvent(DATA_ANALYSIS_RECORD_EVENT, extractDataAnalysisRecords());
  };

  const publishConfigurationRecords = () => {
    emitDihEvent(CONFIG_RECORD_EVENT, extractConfigurationRecords());
  };

  const publishReportRecords = () => {
    emitDihEvent(DATA_REPORT_RECORD_EVENT, extractReportRecords());
  };

  watch(chatSessionExtraData, () => {
    publishDataAccessRecords();
    publishDataVisualizationRecords();
    publishDataAnalysisRecords();
    publishConfigurationRecords();
    publishReportRecords();
  });

  const configRecordLabel = (record?: ConfigRecord) => {
    return textValue(record?.fileName || record?.id || record?.recordId, '当前配置记录');
  };

  const configRecordActionMessage = (action: 'trial' | 'apply', record: ConfigRecord) => {
    const recordId = textValue(record.id || record.recordId);
    const configText = prettyTextValue(record.newConfig);
    if (action === 'trial') {
      return [
        '我已确认进入试验场验证。',
        `请基于配置记录 ${recordId || configRecordLabel(record)} 调用 config_validate 执行格式和可用 schema 校验；如果运行效果需要专项验证，则调用对应验证 MCP。`,
        '验证完成后输出 zenvis:config-record，准确更新 validationStatus 和 validationResult；缺少专项能力时标记 blocked，不得假定验证成功。',
        '',
        JSON.stringify({
          recordId,
          configType: record.configType,
          fileName: record.fileName,
          format: record.format,
          newConfig: record.newConfig,
        }, null, 2),
        configText ? `\n待验证配置：\n${configText}` : '',
      ].filter(Boolean).join('\n');
    }
    return [
      '我已确认将配置正式下发到系统生效。',
      `请基于配置记录 ${recordId || configRecordLabel(record)}，在 validationStatus 为 success 的前提下调用 config_ensure_root、config_add 或 config_apply。`,
      '高风险操作仍须经过平台审批；写入后调用 config_read 读回核验。只有审批成功且读回一致时，才能输出 zenvis:config-record 将 effectiveStatus 更新为 yes。',
      '',
      JSON.stringify({
        recordId,
        configType: record.configType,
        fileName: record.fileName,
        format: record.format,
        validationStatus: record.validationStatus,
        newConfig: record.newConfig,
      }, null, 2),
    ].join('\n');
  };

  const handleConfigRecordActionRequested = async (detail: ConfigRecordActionEventDetail) => {
    detail ||= {};
    const action = detail.action;
    const record = detail.record;
    if (!action || !record) {
      ElMessage.warning('缺少配置记录，无法执行操作');
      return;
    }
    if (isStreamingResponse.value) {
      ElMessage.warning('当前正在生成，请稍后再试');
      return;
    }
    await sendMessage({
      content: action === 'trial'
        ? `我已确认将「${configRecordLabel(record)}」推送到试验场验证。`
        : `我已确认下发「${configRecordLabel(record)}」到系统正式生效。`,
      requestContent: configRecordActionMessage(action, record),
    });
  };

  const handleReportExtraDataChanged = (detail: ReportExtraDataChangedEventDetail) => {
    detail ||= {};
    if (typeof detail.extraData === 'string') {
      chatSessionExtraData.value = detail.extraData;
    }
  };

  const handleDataVisualizationExtraDataChanged = (
    detail: { extraData?: string },
  ) => {
    if (typeof detail?.extraData === 'string') {
      chatSessionExtraData.value = detail.extraData;
    }
  };

  const stripSelectionRewriteFence = (content = '') => {
    const trimmed = content.trim();
    const match = trimmed.match(/^```(?:[\w:-]+)?\s*\n?([\s\S]*?)\n?```$/);
    return (match?.[1] || trimmed).trim();
  };

  const extractSelectionRewriteContent = (message?: ChatMessage) => {
    if (!message) {
      return '';
    }
    const preferredPart = message.parts?.find(part => {
      return ['report-document', 'markdown', 'code'].includes(part.type) && !!part.content?.trim();
    });
    return stripSelectionRewriteFence(preferredPart?.content || message.content || '');
  };

  const handleReportQuickActionRequested = async (detail: ReportQuickActionEventDetail) => {
    detail ||= {};
    const requestContent = detail.requestContent?.trim();
    if (!requestContent) {
      ElMessage.warning('快捷写作指令为空');
      return;
    }
    if (isStreamingResponse.value) {
      ElMessage.warning('当前正在生成，请稍后再试');
      return;
    }
    const messageStartIndex = messages.value.length;
    await sendMessage({
      content: detail.displayContent || '请根据右侧文档执行 AI 写作操作。',
      requestContent,
    });
    if (detail.target === 'selection') {
      const responseMessage = [...messages.value.slice(messageStartIndex)]
        .reverse()
        .find(message => message.sender === 'ai' && !message.loading && !message.isError);
      emitDihEvent(REPORT_SELECTION_REWRITE_COMPLETED_EVENT, {
        selectionId: detail.selectionId,
        actionKey: detail.actionKey,
        content: extractSelectionRewriteContent(responseMessage),
      });
    }
  };

  useDihEventListener(DATA_ANALYSIS_RECORD_REQUEST_EVENT, publishDataAnalysisRecords);
  useDihEventListener(CONFIG_RECORD_REQUEST_EVENT, publishConfigurationRecords);
  useDihEventListener(CONFIG_RECORD_ACTION_EVENT, handleConfigRecordActionRequested);
  useDihEventListener(DATA_REPORT_RECORD_REQUEST_EVENT, publishReportRecords);
  useDihEventListener(REPORT_EXTRA_DATA_CHANGED_EVENT, handleReportExtraDataChanged);
  useDihEventListener(
    DATA_VISUALIZATION_EXTRA_DATA_CHANGED_EVENT,
    handleDataVisualizationExtraDataChanged,
  );
  useDihEventListener(REPORT_QUICK_ACTION_EVENT, handleReportQuickActionRequested);

  onMounted(() => {
    void nextTick(() => publishDataAnalysisRecords());
    void nextTick(() => publishConfigurationRecords());
    void nextTick(() => publishReportRecords());
  });

  return {
    addChartRecordToExtraData,
    extractDataAccessRecords,
    extractDataVisualizationRecords,
    extractDataAnalysisRecords,
    extractConfigurationRecords,
    extractReportRecords,
    publishDataAccessRecords,
    publishDataVisualizationRecords,
    publishDataAnalysisRecords,
    publishConfigurationRecords,
    publishReportRecords,
  };
};
