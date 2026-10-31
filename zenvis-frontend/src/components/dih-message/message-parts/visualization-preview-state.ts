import type { ChatMessagePart } from '@/types/type-dih';

const metadataText = (part: ChatMessagePart, key: string): string => {
  const value = part.metadata?.[key];
  return typeof value === 'string' ? value.trim() : '';
};

const asRecord = (value: unknown): Record<string, unknown> => {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
};

const chartOptionValue = (value: unknown): Record<string, unknown> | null => {
  let option = value;
  if (typeof option === 'string') {
    try {
      option = JSON.parse(option);
    } catch {
      return null;
    }
  }
  const record = asRecord(option);
  return Object.keys(record).length > 0 ? record : null;
};

export const visualizationChartOption = (
  part: ChatMessagePart,
): Record<string, unknown> | null => {
  const metadata = asRecord(part.metadata);
  const echarts = asRecord(metadata.echarts);
  const data = asRecord(metadata.data);
  const dataEcharts = asRecord(data.echarts);
  const candidates = [
    metadata.echartsOption,
    echarts.option,
    dataEcharts.option,
    metadata.option,
    asRecord(metadata.amisConfig).config,
    asRecord(metadata.config).config,
  ];
  for (const candidate of candidates) {
    const option = chartOptionValue(candidate);
    if (option) return option;
  }
  return null;
};

export const visualizationChartLibraryAction = (part: ChatMessagePart): string => {
  const action = metadataText(part, 'action') || metadataText(part, 'blockedAction');
  return action === 'visualization_chart.add_chart_library' ? action : '';
};

export const isVisualizationChartAdded = (part: ChatMessagePart): boolean => {
  const status = typeof part.status === 'string' ? part.status.toLowerCase() : '';
  return status === 'submitted' || status === 'added';
};

export const canAddVisualizationChartToLibrary = (
  part: ChatMessagePart,
  renderFailed = false,
): boolean => {
  const status = typeof part.status === 'string' ? part.status.toLowerCase() : '';
  if (part.type !== 'visualization-chart-preview'
    || isVisualizationChartAdded(part)
    || status === 'loading'
    || renderFailed
    || !visualizationChartOption(part)) {
    return false;
  }
  return Boolean(metadataText(part, 'workflowId') || metadataText(part, 'demoId'));
};

export const visualizationPreviewValidationNotice = (part: ChatMessagePart): string => {
  const status = metadataText(part, 'validationStatus');
  if (status !== 'blocked' && status !== 'failed') return '';

  const code = metadataText(part, 'validationCode');
  const message = metadataText(part, 'validationMessage') || '真实数据查询未完成';
  const queryMismatch =
    code === 'query_parameter_mismatch' ||
    /查询(参数|方案).*(不一致|未通过|未批准)/.test(message);
  if (queryMismatch) {
    return `${message}。当前图表仍可加入图表库，但记录会保留未验证状态；如需重新查询，请调整查询方案或重试当前阶段。`;
  }
  return `${message}。当前图表仍可加入图表库，但记录会保留原始校验状态。`;
};
