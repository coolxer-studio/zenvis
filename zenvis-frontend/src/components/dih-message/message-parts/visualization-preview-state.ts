import type { ChatMessagePart } from '@/types/type-dih';

const metadataText = (part: ChatMessagePart, key: string): string => {
  const value = part.metadata?.[key];
  return typeof value === 'string' ? value.trim() : '';
};

const asRecord = (value: unknown): Record<string, unknown> => {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
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

export const visualizationChartOption = (part: ChatMessagePart): Record<string, unknown> | null => {
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
  if (
    part.type !== 'visualization-chart-preview' ||
    isVisualizationChartAdded(part) ||
    status === 'loading' ||
    renderFailed ||
    !visualizationChartOption(part)
  ) {
    return false;
  }
  return Boolean(metadataText(part, 'workflowId') || metadataText(part, 'demoId'));
};

export const visualizationPreviewValidationNotice = (part: ChatMessagePart): string => {
  const status = metadataText(part, 'validationStatus');
  if (status !== 'blocked' && status !== 'failed') return '';

  const code = metadataText(part, 'validationCode');
  const message = metadataText(part, 'validationMessage') || '真实数据查询未完成';
  const evidenceMissing = new Set([
    'query_evidence_missing',
    'query_snapshot_expired',
    'query_data_mismatch',
    'query_data_unverifiable',
  ]).has(code);
  if (evidenceMissing) {
    return `${message}。当前图表仍可加入图表库，但会标记为未验证；如需验证，请重新执行数据查询。`;
  }
  const planNeedsAdjustment = new Set([
    'query_plan_missing',
    'query_plan_not_approved',
    'query_plan_invalid',
    'query_tool_mismatch',
    'query_tool_unsupported',
  ]).has(code);
  if (code === 'query_parameter_mismatch' || planNeedsAdjustment) {
    return `${message}。当前图表仍可加入图表库，但记录会保留未验证状态；如需重新查询，请调整查询方案或重试当前阶段。`;
  }
  return `${message}。当前图表仍可加入图表库，但记录会保留原始校验状态。`;
};

const formatSnapshotTime = (value: string): string => {
  if (!value) return '';
  const time = new Date(value);
  if (Number.isNaN(time.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(time);
};

export const visualizationPreviewSnapshotNotice = (part: ChatMessagePart): string => {
  if (metadataText(part, 'validationStatus') !== 'success') return '';
  if (metadataText(part, 'evidenceMode') !== 'reused_snapshot') return '';
  const queriedAt = metadataText(part, 'sourceQueriedAt') || metadataText(part, 'queriedAt');
  const formatted = formatSnapshotTime(queriedAt);
  return formatted
    ? `基于 ${formatted} 的已验证查询快照生成，未重新查询数据。`
    : '基于已验证查询快照生成，未重新查询数据。';
};

export const hasBlockedVisualizationChart = (parts: ChatMessagePart[]): boolean =>
  parts.some(
    part =>
      part.type === 'visualization-chart-preview' &&
      metadataText(part, 'step').toUpperCase() === 'BLOCKED' &&
      ['blocked', 'failed'].includes(metadataText(part, 'validationStatus')),
  );
