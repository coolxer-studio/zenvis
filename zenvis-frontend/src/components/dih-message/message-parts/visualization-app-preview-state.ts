import type { ChatMessagePart } from '@/types/type-dih';

export type VisualizationAppPreviewFile = {
  path: string;
  size?: number;
  digest?: string;
};

export type VisualizationAppPreviewDiagnostic = {
  code: string;
  message: string;
  severity: string;
};

export type VisualizationAppPreviewPage = {
  pageId: string;
  title: string;
};

export type VisualizationAppPreviewMenu = {
  menuName: string;
  menuType: string;
  menuId: number | string | '';
  publishStatus: string;
};

export type VisualizationAppQuerySample = {
  label: string;
  value: string;
};

export type VisualizationAppPreviewState = {
  appId: string;
  appName: string;
  appVersion: number;
  mode: string;
  modeLabel: string;
  previousMode: string;
  modeChanged: boolean;
  workflowId: string;
  validationStatus: string;
  validated: boolean;
  partialMenu: boolean;
  canPublish: boolean;
  publishDecision: 'apply_config' | 'retry';
  publishLabel: string;
  candidateDigest: string;
  validationDigest: string;
  specDigest: string;
  metaDigest: string;
  previewSiteUrl: string;
  files: VisualizationAppPreviewFile[];
  deletedFiles: string[];
  diagnostics: VisualizationAppPreviewDiagnostic[];
  pages: VisualizationAppPreviewPage[];
  menu: VisualizationAppPreviewMenu;
  menuStatusLabel: string;
  querySamples: VisualizationAppQuerySample[];
};

const asRecord = (value: unknown): Record<string, unknown> => value
  && typeof value === 'object'
  && !Array.isArray(value)
  ? value as Record<string, unknown>
  : {};

const text = (value: unknown): string => typeof value === 'string' ? value : '';

const read = (metadata: Record<string, unknown>, key: string, snakeKey = key): unknown => {
  const validation = asRecord(metadata.validation);
  if (validation[key] !== undefined) return validation[key];
  if (validation[snakeKey] !== undefined) return validation[snakeKey];
  return metadata[key] ?? metadata[snakeKey];
};

const readObject = (metadata: Record<string, unknown>, key: string): Record<string, unknown> => {
  const snakeKey = key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`);
  return asRecord(read(metadata, key, snakeKey));
};

const readArray = (metadata: Record<string, unknown>, key: string): unknown[] => {
  const snakeKey = key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`);
  const value = read(metadata, key, snakeKey);
  return Array.isArray(value) ? value : [];
};

const normalizeFiles = (metadata: Record<string, unknown>): VisualizationAppPreviewFile[] =>
  readArray(metadata, 'files')
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
): VisualizationAppPreviewDiagnostic[] =>
  readArray(metadata, 'diagnostics')
    .map(asRecord)
    .filter(item => Object.keys(item).length > 0)
    .map(item => ({
      code: text(item.code),
      message: text(item.message),
      severity: text(item.severity),
    }));

const normalizePages = (metadata: Record<string, unknown>): VisualizationAppPreviewPage[] => {
  const preview = readObject(metadata, 'preview');
  return Array.isArray(preview.pages)
    ? preview.pages.map(asRecord)
      .filter(page => Object.keys(page).length > 0)
      .map(page => ({
        pageId: text(page.pageId) || text(page.page_id),
        title: text(page.title),
      }))
    : [];
};

const normalizeMenu = (metadata: Record<string, unknown>): VisualizationAppPreviewMenu => {
  const menu = readObject(metadata, 'menu');
  return {
    menuName: text(menu.menuName) || text(menu.menu_name) || text(menu.name),
    menuType: text(menu.menuType) || text(menu.menu_type),
    menuId: typeof menu.menuId === 'number' || typeof menu.menuId === 'string'
      ? menu.menuId
      : typeof menu.menu_id === 'number' || typeof menu.menu_id === 'string'
        ? menu.menu_id
        : '',
    publishStatus: text(menu.publishStatus) || text(menu.publish_status),
  };
};

const normalizeQuerySamples = (
  metadata: Record<string, unknown>,
): VisualizationAppQuerySample[] => {
  const preview = readObject(metadata, 'preview');
  const samples = preview.querySamples || preview.query_samples;
  if (!samples || typeof samples !== 'object' || Array.isArray(samples)) return [];
  return Object.entries(samples as Record<string, unknown>).slice(0, 8).map(([key, value]) => {
    const item = asRecord(value);
    const label = text(item.entity) || text(item.label) || key;
    const count = item.total ?? item.rowCount ?? item.row_count ?? item.returned ?? '-';
    return { label, value: String(count) };
  });
};

export const resolveVisualizationAppPreviewState = (
  part: Pick<ChatMessagePart, 'metadata'>,
): VisualizationAppPreviewState => {
  const metadata = asRecord(part.metadata);
  const validationStatus = text(read(metadata, 'validationStatus', 'validation_status'))
    || text(asRecord(metadata.validation).status);
  const partialMenu = validationStatus === 'partial_menu'
    || text(read(metadata, 'status')) === 'partial_menu';
  const validated = validationStatus === 'validated'
    || (read(metadata, 'passed') === true && !partialMenu);
  const mode = text(read(metadata, 'mode'));
  const previousMode = text(read(metadata, 'previousMode', 'previous_mode'));
  const preview = readObject(metadata, 'preview');
  const menu = normalizeMenu(metadata);
  const canPublish = validated || partialMenu;
  const publishDecision = partialMenu ? 'retry' : 'apply_config';
  const menuStatusLabel = menu.publishStatus === 'partial_menu'
    ? '部分成功，可重试'
    : menu.publishStatus === 'published'
      ? '已发布并读回'
      : partialMenu
        ? '菜单发布失败'
        : menu.publishStatus || '待发布';

  return {
    appId: text(read(metadata, 'appId', 'app_id')),
    appName: text(read(metadata, 'appName', 'app_name'))
      || text(readObject(metadata, 'spec').name)
      || text(read(metadata, 'name')),
    appVersion: Number(read(metadata, 'appVersion', 'app_version') || 1),
    mode,
    modeLabel: mode === 'LOW_CODE_APP' ? '多页应用' : '单页应用',
    previousMode,
    modeChanged: Boolean(previousMode && mode && previousMode !== mode),
    workflowId: text(read(metadata, 'workflowId', 'workflow_id')),
    validationStatus,
    validated,
    partialMenu,
    canPublish,
    publishDecision,
    publishLabel: partialMenu ? '重试菜单发布' : '确认发布应用',
    candidateDigest: text(read(metadata, 'candidateDigest', 'candidate_digest')),
    validationDigest: text(read(metadata, 'validationDigest', 'validation_digest')),
    specDigest: text(read(metadata, 'specDigest', 'spec_digest')),
    metaDigest: text(read(metadata, 'metaDigest', 'meta_digest')),
    previewSiteUrl: text(preview.siteUrl) || text(preview.site_url),
    files: normalizeFiles(metadata),
    deletedFiles: readArray(metadata, 'deletedFiles').map(String),
    diagnostics: normalizeDiagnostics(metadata),
    pages: normalizePages(metadata),
    menu,
    menuStatusLabel,
    querySamples: normalizeQuerySamples(metadata),
  };
};
