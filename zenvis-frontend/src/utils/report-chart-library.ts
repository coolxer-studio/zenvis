import type { ReportChartLibraryItem, ReportSourceRef } from '@/types/type-dih';

export const CHART_IMAGE_MAX_DATA_URL_LENGTH = 800_000;
export const REPORT_CONTENT_MAX_CHARS = 2_000_000;

const asRecord = (value: unknown): Record<string, unknown> =>
  value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};

export const normalizeChartOption = (value: unknown): Record<string, unknown> | null => {
  let candidate = value;
  if (typeof candidate === 'string') {
    try {
      candidate = JSON.parse(candidate);
    } catch {
      return null;
    }
  }
  const option = asRecord(candidate);
  return Object.keys(option).length > 0 ? option : null;
};

export const chartOptionFromLibraryItem = (chart?: ReportChartLibraryItem | null) => {
  return normalizeChartOption(chart?.echartsOption);
};

export const chartTypeLabel = (chartType?: string) => {
  const labels: Record<string, string> = {
    bar: '柱状图',
    line: '折线图',
    pie: '饼图',
    scatter: '散点图',
    area: '面积图',
    radar: '雷达图',
    gauge: '仪表盘',
    funnel: '漏斗图',
    map: '地图',
    chart: '图表',
  };
  const type = String(chartType || '').toLowerCase();
  return labels[type] || chartType || '图表';
};

const escapeChartHtml = (value: unknown) =>
  String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

export const buildChartImageHtml = (
  chart: ReportChartLibraryItem | ReportSourceRef,
  imageDataUrl: string,
) => {
  if (!/^data:image\/(?:png|jpeg);base64,/i.test(imageDataUrl)) {
    throw new Error('图表快照格式不受支持');
  }
  const name = String(chart.name || chart.title || '图表').trim() || '图表';
  const chartId = String(chart.id || chart.artifactId || name);
  return [
    `<p data-report-chart-id="${escapeChartHtml(chartId)}">`,
    `<img src="${imageDataUrl}" alt="${escapeChartHtml(name)}" />`,
    '</p>',
    `<p>图表：${escapeChartHtml(name)}</p>`,
  ].join('');
};

export const chartSourceRef = (
  chart: ReportChartLibraryItem | ReportSourceRef,
): ReportSourceRef => {
  const source: Record<string, unknown> = {
    type: 'chart',
    origin: 'chart_library',
    status: 'available',
  };
  const fields = [
    'id',
    'artifactId',
    'name',
    'title',
    'chartType',
    'description',
    'source',
    'addedAt',
    'queriedAt',
    'sessionRecordId',
    'chatSessionId',
    'sessionTitle',
    'agentType',
  ] as const;
  fields.forEach(field => {
    const value = chart[field];
    if (value !== undefined && value !== null && String(value).trim()) {
      source[field] = value;
    }
  });
  return source as ReportSourceRef;
};

export const mergeReportSourceRefs = (
  ...sources: Array<ReportSourceRef[] | undefined>
): ReportSourceRef[] => {
  const merged = new Map<string, ReportSourceRef>();
  sources
    .flatMap(source => source || [])
    .forEach(source => {
      if (!source) return;
      const key = `${String(source.type || 'material')}:${String(source.id || source.name || '')}`;
      if (!key.endsWith(':')) merged.set(key, source);
    });
  return [...merged.values()];
};

export const chooseChartImageDataUrl = (png: string, jpeg?: string) => {
  if (png.length <= CHART_IMAGE_MAX_DATA_URL_LENGTH) return png;
  if (jpeg && jpeg.startsWith('data:image/jpeg') && jpeg.length < png.length) return jpeg;
  return png;
};

export const chartImageFitsReport = (reportContent: string, imageDataUrl: string, caption = '') => {
  return (
    String(reportContent || '').length +
      String(imageDataUrl || '').length +
      String(caption || '').length +
      256 <=
    REPORT_CONTENT_MAX_CHARS
  );
};
