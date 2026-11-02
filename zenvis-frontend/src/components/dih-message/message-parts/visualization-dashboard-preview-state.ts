import type { ChatMessagePart } from '@/types/type-dih';

export type VisualizationDashboardPreviewFile = {
  path: string;
  size?: number;
  digest?: string;
};

export type VisualizationDashboardPreviewDiagnostic = {
  code: string;
  message: string;
  severity: string;
};

export type VisualizationDashboardPreviewBlock = {
  blockId: string;
  type: string;
  title: string;
  queryTool: string;
  empty: boolean;
};

export type VisualizationDashboardQuerySample = {
  blockId: string;
  label: string;
  value: string;
  empty: boolean;
};

export type VisualizationDashboardPreviewState = {
  dashboardId: string;
  dashboardName: string;
  dashboardVersion: number;
  mode: string;
  modeLabel: string;
  previousMode: string;
  modeChanged: boolean;
  theme: string;
  refreshIntervalSeconds: number;
  defaultTimeRange: string;
  workflowId: string;
  validationStatus: string;
  validated: boolean;
  canPublish: boolean;
  publishLabel: string;
  candidateDigest: string;
  validationDigest: string;
  specDigest: string;
  metaDigest: string;
  fileDigest: string;
  previewHtmlUrl: string;
  files: VisualizationDashboardPreviewFile[];
  diagnostics: VisualizationDashboardPreviewDiagnostic[];
  blocks: VisualizationDashboardPreviewBlock[];
  querySamples: VisualizationDashboardQuerySample[];
};

const RESOLVED_CARD_STATUSES = new Set([
  'approved',
  'force_approved',
  'rejected',
  'revise',
  'cancelled',
  'completed',
  'saved',
  'submitted',
  'added',
]);

const asRecord = (value: unknown): Record<string, unknown> => value
  && typeof value === 'object'
  && !Array.isArray(value)
  ? value as Record<string, unknown>
  : {};

const text = (value: unknown): string => typeof value === 'string' ? value : '';

const snakeKey = (key: string) => key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`);

const read = (metadata: Record<string, unknown>, key: string, alias = key): unknown => {
  const validation = asRecord(metadata.validation);
  if (validation[key] !== undefined) return validation[key];
  if (validation[alias] !== undefined) return validation[alias];
  return metadata[key] ?? metadata[alias];
};

const readObject = (metadata: Record<string, unknown>, key: string): Record<string, unknown> =>
  asRecord(read(metadata, key, snakeKey(key)));

const readArray = (metadata: Record<string, unknown>, key: string): unknown[] => {
  const alias = snakeKey(key);
  const value = read(metadata, key, alias);
  return Array.isArray(value) ? value : [];
};

const normalizeFiles = (
  metadata: Record<string, unknown>,
): VisualizationDashboardPreviewFile[] => readArray(metadata, 'files')
  .map(asRecord)
  .filter(file => Object.keys(file).length > 0)
  .map(file => ({
    path: text(file.path),
    size: typeof file.size === 'number' ? file.size : undefined,
    digest: text(file.digest),
  }))
  .filter(file => Boolean(file.path));

const normalizeDiagnostics = (
  metadata: Record<string, unknown>,
): VisualizationDashboardPreviewDiagnostic[] => readArray(metadata, 'diagnostics')
  .map(asRecord)
  .filter(item => Object.keys(item).length > 0)
  .map(item => ({
    code: text(item.code),
    message: text(item.message),
    severity: text(item.severity),
  }));

const normalizeBlocks = (
  metadata: Record<string, unknown>,
  samples: Record<string, unknown>,
): VisualizationDashboardPreviewBlock[] => {
  const spec = readObject(metadata, 'spec');
  const rawBlocks = Array.isArray(spec.blocks)
    ? spec.blocks
    : readArray(metadata, 'blocks').length
      ? readArray(metadata, 'blocks')
      : readArray(metadata, 'blockSummary');
  return rawBlocks.map(asRecord)
    .filter(block => Object.keys(block).length > 0)
    .map(block => {
      const query = asRecord(block.query);
      const blockId = text(block.block_id) || text(block.blockId);
      return {
        blockId,
        type: text(block.type),
        title: text(block.title) || blockId,
        queryTool: text(query.tool),
        empty: asRecord(samples[blockId]).empty === true,
      };
    })
    .filter(block => Boolean(block.blockId));
};

const normalizeSamples = (
  metadata: Record<string, unknown>,
  blocks: VisualizationDashboardPreviewBlock[],
): VisualizationDashboardQuerySample[] => {
  const preview = readObject(metadata, 'preview');
  const raw = preview.querySamples || preview.query_samples;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return [];
  const samples = raw as Record<string, unknown>;
  const titles = new Map(blocks.map(block => [block.blockId, block.title]));
  return Object.entries(samples).slice(0, 16).map(([blockId, value]) => {
    const sample = asRecord(value);
    const count = sample.rowCount ?? sample.row_count ?? sample.total ?? sample.returned;
    return {
      blockId,
      label: titles.get(blockId) || text(sample.entity) || blockId,
      value: sample.empty === true ? '空结果' : String(count ?? '-'),
      empty: sample.empty === true,
    };
  });
};

export const resolveVisualizationDashboardPreviewState = (
  part: Pick<ChatMessagePart, 'metadata' | 'status'>,
): VisualizationDashboardPreviewState => {
  const metadata = asRecord(part.metadata);
  const cardStatus = text(part.status).toLowerCase();
  const cardResolved = RESOLVED_CARD_STATUSES.has(cardStatus);
  const actionValue = read(metadata, 'allowedActions', 'allowed_actions');
  const allowedActions = Array.isArray(actionValue) ? actionValue.map(text).filter(Boolean) : [];
  const validationStatus = text(read(metadata, 'validationStatus', 'validation_status'))
    || text(asRecord(metadata.validation).status);
  const normalizedStatus = validationStatus.toLowerCase();
  const blocked = ['blocked', 'failed', 'invalid', 'error', 'rejected'].includes(normalizedStatus);
  const validated = !blocked && (normalizedStatus === 'validated'
    || normalizedStatus === 'success'
    || read(metadata, 'passed') === true);
  const mode = text(read(metadata, 'mode'));
  const previousMode = text(read(metadata, 'previousMode', 'previous_mode'));
  const preview = readObject(metadata, 'preview');
  const spec = readObject(metadata, 'spec');
  const rawSamples = preview.querySamples || preview.query_samples;
  const sampleMap = rawSamples && typeof rawSamples === 'object' && !Array.isArray(rawSamples)
    ? rawSamples as Record<string, unknown>
    : {};
  const blocks = normalizeBlocks(metadata, sampleMap);
  const candidateDigest = text(read(metadata, 'candidateDigest', 'candidate_digest'));
  const validationDigest = text(read(metadata, 'validationDigest', 'validation_digest'));

  return {
    dashboardId: text(read(metadata, 'dashboardId', 'dashboard_id'))
      || text(spec.dashboard_id)
      || text(spec.dashboardId),
    dashboardName: text(read(metadata, 'name'))
      || text(read(metadata, 'dashboardName', 'dashboard_name'))
      || text(spec.name),
    dashboardVersion: Number(read(metadata, 'dashboardVersion', 'dashboard_version') || 1),
    mode,
    modeLabel: mode === 'LOW_CODE_PAGE' ? '低代码大屏' : '静态 HTML 大屏',
    previousMode,
    modeChanged: Boolean(previousMode && mode && previousMode !== mode),
    theme: text(read(metadata, 'theme')) || text(spec.theme) || 'DARK_TECH',
    refreshIntervalSeconds: Number(
      read(metadata, 'refreshIntervalSeconds', 'refresh_interval_seconds')
      || spec.refresh_interval_seconds
      || 60,
    ),
    defaultTimeRange: text(spec.default_time_range) || 'LAST_7_DAYS',
    workflowId: text(read(metadata, 'workflowId', 'workflow_id')),
    validationStatus,
    validated,
    canPublish: !cardResolved
      && validated
      && (!Array.isArray(actionValue) || allowedActions.includes('approve'))
      && Boolean(candidateDigest && validationDigest),
    publishLabel: '确认发布大屏',
    candidateDigest,
    validationDigest,
    specDigest: text(read(metadata, 'specDigest', 'spec_digest')),
    metaDigest: text(read(metadata, 'metaDigest', 'meta_digest')),
    fileDigest: text(read(metadata, 'fileDigest', 'file_digest')),
    previewHtmlUrl: text(preview.htmlUrl) || text(preview.html_url),
    files: normalizeFiles(metadata),
    diagnostics: normalizeDiagnostics(metadata),
    blocks,
    querySamples: normalizeSamples(metadata, blocks),
  };
};
