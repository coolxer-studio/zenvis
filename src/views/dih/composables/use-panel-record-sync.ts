import { nextTick, onMounted, watch } from 'vue';
import type { Ref } from 'vue';
import type { Router } from 'vue-router';
import { ElMessage } from 'element-plus';
import type {
  AnalysisRecord,
  ChatMessage,
  PolicyRecord,
} from '@/types/type-dih';
import { generateUUID } from '@/utils/util-common';
import {
  DATA_ACCESS_RECORD_EVENT,
  DATA_ANALYSIS_RECORD_EVENT,
  DATA_ANALYSIS_RECORD_REQUEST_EVENT,
  DATA_REPORT_RECORD_EVENT,
  DATA_REPORT_RECORD_REQUEST_EVENT,
  DATA_VISUALIZATION_RECORD_EVENT,
  POLICY_RECORD_ACTION_EVENT,
  POLICY_RECORD_EVENT,
  POLICY_RECORD_REQUEST_EVENT,
  REPORT_EXTRA_DATA_CHANGED_EVENT,
  REPORT_QUICK_ACTION_EVENT,
  REPORT_SELECTION_REWRITE_COMPLETED_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events';
import type {
  PolicyRecordActionEventDetail,
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
  router: Router;
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
  const id = String(record.id || record.fileName || record.taskId || record.name || '');
  if (!id) {
    return [...items, record];
  }
  const next = items.filter(item => String(item.id || item.fileName || item.taskId || item.name || '') !== id);
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

const truncateText = (value: string, maxLength = 4000) => {
  return value.length > maxLength ? `${value.slice(0, maxLength)}\n...` : value;
};

export const usePanelRecordSync = ({
  router,
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
    };
  };

  const extractAnalysisRecords = () => {
    const records: AnalysisRecord[] = [];
    const analysis = asObject(parseSessionExtraData().analysis);
    asRecordList(analysis.records).forEach(record => {
      upsertInto(records as DihPanelRecord[], record);
    });
    return {
      records,
      aggregatedLogs: asRecordList(analysis.aggregatedLogs),
      sandboxResults: asRecordList(analysis.sandboxResults),
      conclusionTimeline: asRecordList(analysis.conclusionTimeline),
    };
  };

  const extractPolicyRecords = () => {
    const records: PolicyRecord[] = [];
    const policy = asObject(parseSessionExtraData().policy);
    asRecordList(policy.records).forEach(record => {
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

  const publishAnalysisRecords = () => {
    emitDihEvent(DATA_ANALYSIS_RECORD_EVENT, extractAnalysisRecords());
  };

  const publishPolicyRecords = () => {
    emitDihEvent(POLICY_RECORD_EVENT, extractPolicyRecords());
  };

  const publishReportRecords = () => {
    emitDihEvent(DATA_REPORT_RECORD_EVENT, extractReportRecords());
  };

  watch(chatSessionExtraData, () => {
    publishDataAccessRecords();
    publishDataVisualizationRecords();
    publishAnalysisRecords();
    publishPolicyRecords();
    publishReportRecords();
  });

  const latestRecord = (records: Record<string, unknown>[]) => {
    return records.length ? records[records.length - 1] : {};
  };

  const findTimelineContent = (timeline: Record<string, unknown>[], keywords: string[]) => {
    const matched = [...timeline].reverse().find(item => {
      const title = textValue(item.title).toLowerCase();
      const id = textValue(item.id).toLowerCase();
      return keywords.some(keyword => title.includes(keyword.toLowerCase()) || id.includes(keyword.toLowerCase()));
    });
    return prettyTextValue(matched?.content);
  };

  const findLatestDisposalStrategyConfig = () => {
    const disposalPart = [...messages.value]
      .reverse()
      .flatMap(message => [...(message.parts || [])].reverse())
      .find(part => part.type === 'config' && textValue(part.metadata?.configKind) === 'disposal-strategy');
    return disposalPart?.content?.trim() || '';
  };

  const buildDisposeAgentPrompt = (detail?: string) => {
    const analysisData = extractAnalysisRecords();
    const timeline = analysisData.conclusionTimeline;
    const reportRecord = latestRecord(
      analysisData.records.filter(record => textValue(record.stage) === 'report_output') as Record<string, unknown>[],
    );
    const sandboxRecord = latestRecord(analysisData.sandboxResults);
    const disposalSuggestion = findTimelineContent(timeline, ['处置建议', 'disposal', 'recommendation'])
      || prettyTextValue((reportRecord.recommendations as unknown[] | undefined)?.join?.('\n'))
      || '请基于上一轮研判结论生成处置方案。';
    const analysisTarget = findTimelineContent(timeline, ['分析目标', 'target']);
    const analysisProcess = findTimelineContent(timeline, ['分析过程', 'process']);
    const analysisConclusion = findTimelineContent(timeline, ['分析结论', 'conclusion']);
    const disposalStrategyConfig = findLatestDisposalStrategyConfig();
    const extraDetail = detail?.trim();

    return truncateText([
      '请基于以下研判分析结果进入策略控制流程，生成可执行前需确认的处置方案。',
      '',
      '## 处置建议',
      disposalSuggestion,
      '',
      '## 研判上下文',
      analysisTarget ? `分析目标：${analysisTarget}` : '',
      analysisProcess ? `分析过程：${analysisProcess}` : '',
      analysisConclusion ? `分析结论：${analysisConclusion}` : '',
      `聚合日志数量：${analysisData.aggregatedLogs.length}`,
      sandboxRecord.result ? `沙箱研判结果：\n${prettyTextValue(sandboxRecord.result)}` : '',
      disposalStrategyConfig ? `处置策略建议配置：\n${disposalStrategyConfig}` : '',
      extraDetail ? `用户补充要求：\n${extraDetail}` : '',
      '',
      '## 输出要求',
      '1. 先说明拟执行处置动作、影响范围、前置检查和回滚方案。',
      '2. 生成策略控制智能体可确认的处置配置或策略配置。',
      '3. 不要直接执行写入、发布、阻断、隔离等副作用动作，必须先等待用户确认。',
    ].filter(Boolean).join('\n'));
  };

  const openDisposeAgentSession = async (prompt: string) => {
    const nextChatSessionId = generateUUID();
    const promptRef = generateUUID();
    try {
      window.sessionStorage?.setItem(`dih:prefill:${promptRef}`, prompt);
    } catch {
      // ignore storage failures and fall back to query string below
    }
    let storedPrompt = false;
    try {
      storedPrompt = window.sessionStorage?.getItem(`dih:prefill:${promptRef}`) === prompt;
    } catch {
      storedPrompt = false;
    }
    await router.push({
      name: 'service-dih',
      query: {
        type: 'agent_dispose',
        chatSessionId: nextChatSessionId,
        createSession: 1,
        ...(storedPrompt ? { msgRef: promptRef } : { msg: encodeURIComponent(prompt) }),
      },
    });
  };

  const policyRecordLabel = (record?: PolicyRecord) => {
    return textValue(record?.fileName || record?.id || record?.recordId, '当前策略记录');
  };

  const policyRecordActionMessage = (action: 'trial' | 'apply', record: PolicyRecord) => {
    const recordId = textValue(record.id || record.recordId);
    const configText = prettyTextValue(record.newConfig);
    if (action === 'trial') {
      const retry = textValue(record.id || record.recordId).includes('v2') || record.changeMode === 'modify';
      const firstLine = retry ? '我已确认重新进入试验场验证。' : '我已确认进入试验场验证。';
      return [
        firstLine,
        `请基于策略记录 ${recordId || policyRecordLabel(record)} 执行试验场验证，并在验证完成后输出 zenvis:policy-record 更新验证状态。`,
        '',
        JSON.stringify({
          recordId,
          policyType: record.policyType,
          configType: record.configType,
          fileName: record.fileName,
          newConfig: record.newConfig,
        }, null, 2),
        configText ? `\n策略配置：\n${configText}` : '',
      ].filter(Boolean).join('\n');
    }
    return [
      '我已确认下发策略到系统正式生效。',
      `请基于策略记录 ${recordId || policyRecordLabel(record)} 调用配置管理 MCP 写入并应用策略，成功后输出 zenvis:policy-record 将生效状态更新为 yes。`,
      '',
      JSON.stringify({
        recordId,
        policyType: record.policyType,
        configType: record.configType,
        fileName: record.fileName,
        validationStatus: record.validationStatus,
        newConfig: record.newConfig,
      }, null, 2),
    ].join('\n');
  };

  const handlePolicyRecordActionRequested = async (detail: PolicyRecordActionEventDetail) => {
    detail ||= {};
    const action = detail.action;
    const record = detail.record;
    if (!action || !record) {
      ElMessage.warning('缺少策略记录，无法执行操作');
      return;
    }
    if (isStreamingResponse.value) {
      ElMessage.warning('当前正在生成，请稍后再试');
      return;
    }
    await sendMessage({
      content: action === 'trial'
        ? `我已确认将「${policyRecordLabel(record)}」推送到试验场验证。`
        : `我已确认下发「${policyRecordLabel(record)}」到系统正式生效。`,
      requestContent: policyRecordActionMessage(action, record),
    });
  };

  const handleReportExtraDataChanged = (detail: ReportExtraDataChangedEventDetail) => {
    detail ||= {};
    if (typeof detail.extraData === 'string') {
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

  useDihEventListener(DATA_ANALYSIS_RECORD_REQUEST_EVENT, publishAnalysisRecords);
  useDihEventListener(POLICY_RECORD_REQUEST_EVENT, publishPolicyRecords);
  useDihEventListener(POLICY_RECORD_ACTION_EVENT, handlePolicyRecordActionRequested);
  useDihEventListener(DATA_REPORT_RECORD_REQUEST_EVENT, publishReportRecords);
  useDihEventListener(REPORT_EXTRA_DATA_CHANGED_EVENT, handleReportExtraDataChanged);
  useDihEventListener(REPORT_QUICK_ACTION_EVENT, handleReportQuickActionRequested);

  onMounted(() => {
    void nextTick(() => publishAnalysisRecords());
    void nextTick(() => publishPolicyRecords());
    void nextTick(() => publishReportRecords());
  });

  return {
    addChartRecordToExtraData,
    buildDisposeAgentPrompt,
    openDisposeAgentSession,
    extractDataAccessRecords,
    extractDataVisualizationRecords,
    extractAnalysisRecords,
    extractPolicyRecords,
    extractReportRecords,
    publishDataAccessRecords,
    publishDataVisualizationRecords,
    publishAnalysisRecords,
    publishPolicyRecords,
    publishReportRecords,
  };
};
