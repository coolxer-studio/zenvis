export type VisualizationMenuRecord = Record<string, unknown> & {
  id?: string | number;
  name?: string;
  title?: string;
  code?: string;
  type?: string;
  menuType?: string;
  route?: string;
  routeName?: string;
  params?: string;
  menuParams?: string;
  configIndex?: string;
  configType?: string;
  fileName?: string;
  htmlPath?: string;
};

export type VisualizationMenuTargetRoute =
  | { name: string; params: { menuParams: string } }
  | { path: string };

const encodeUtf8Base64 = (value: string) => {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  bytes.forEach(byte => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
};

export const menuTypeOf = (record: VisualizationMenuRecord) => {
  return String(record.menuType || record.type || '')
    .trim()
    .toUpperCase();
};

export const menuParamsOf = (record: VisualizationMenuRecord) => {
  return String(
    record.params || record.menuParams || record.configIndex || record.htmlPath || '',
  ).trim();
};

export const buildMenuTargetRoute = (
  record: VisualizationMenuRecord,
): VisualizationMenuTargetRoute | null => {
  const menuType = menuTypeOf(record);
  const route = String(record.route || record.routeName || '').trim();
  const params = menuParamsOf(record);
  const routeKey =
    route ||
    {
      LOW_CODE_APP: 'low-code-app',
      LOW_CODE_PAGE: 'low-code-page',
      HTML_PAGE: 'html-page',
      POLICY_CONFIG: 'policy-config',
      EXTERNAL_APP: 'external-app',
    }[menuType];

  if (!routeKey) {
    return null;
  }
  if (routeKey === 'html-page' || routeKey === 'external-app') {
    return params ? { name: routeKey, params: { menuParams: encodeUtf8Base64(params) } } : null;
  }
  if (routeKey === 'low-code-app' || routeKey === 'low-code-page' || routeKey === 'policy-config') {
    return params ? { name: routeKey, params: { menuParams: params } } : null;
  }
  if (routeKey.startsWith('/')) {
    return { path: routeKey };
  }
  return null;
};

export const findVisualizationMenuRecord = (
  record: VisualizationMenuRecord,
  menuRecords: VisualizationMenuRecord[],
) => {
  const type = menuTypeOf(record);
  const params = menuParamsOf(record);
  if (!type || !params) {
    return undefined;
  }
  return menuRecords.find(menu => menuTypeOf(menu) === type && menuParamsOf(menu) === params);
};

const normalizeHtmlPagePath = (value: string) => {
  const path = value.trim();
  if (!path || path.startsWith('/') || /^https?:\/\//i.test(path)) {
    return path;
  }
  return `/html-page/${path.replace(/^html-page\//, '')}`;
};

export const visualizationTargetRecordOf = (
  record: VisualizationMenuRecord,
): VisualizationMenuRecord => {
  const configType = String(record.configType || '').trim();
  const recordType = menuTypeOf(record);
  const targetType =
    configType.toLowerCase() === 'html-page'
      ? 'HTML_PAGE'
      : configType.toLowerCase() === 'user-event-app'
      ? 'LOW_CODE_APP'
      : recordType;
  if (targetType === 'LOW_CODE_APP') {
    return {
      ...record,
      menuType: 'LOW_CODE_APP',
      route: 'low-code-app',
      params: configType || menuParamsOf(record),
    };
  }
  if (targetType === 'HTML_PAGE') {
    const htmlPath = normalizeHtmlPagePath(
      String(record.htmlPath || record.fileName || record.configIndex || ''),
    );
    return {
      ...record,
      menuType: 'HTML_PAGE',
      route: 'html-page',
      params: htmlPath,
    };
  }
  if (targetType === 'LOW_CODE_PAGE') {
    return {
      ...record,
      route: 'low-code-page',
      params: configType || menuParamsOf(record),
    };
  }
  return record;
};

export const buildVisualizationTargetRoute = (
  record: VisualizationMenuRecord,
  menuRecords: VisualizationMenuRecord[],
) => {
  const targetRecord = visualizationTargetRecordOf(record);
  const menuRecord = findVisualizationMenuRecord(targetRecord, menuRecords);
  return buildMenuTargetRoute(menuRecord || targetRecord);
};

export const isVisualizationDashboardRecord = (record: VisualizationMenuRecord) => {
  const identity = [
    record.id,
    record.name,
    record.title,
    record.code,
    record.fileName,
    record.configIndex,
    record.htmlPath,
  ]
    .filter(Boolean)
    .join(' ');
  return /dashboard|看板/i.test(identity);
};

export const findVisualizationDashboardRecord = (
  record: VisualizationMenuRecord,
  dashboardRecords: VisualizationMenuRecord[],
) => {
  const targetRecord = visualizationTargetRecordOf(record);
  const targetType = menuTypeOf(targetRecord);
  const targetParams = menuParamsOf(targetRecord);
  if (!targetType || !targetParams) {
    return undefined;
  }
  const sameTypeDashboards = dashboardRecords.filter(dashboard => {
    const dashboardTarget = visualizationTargetRecordOf(dashboard);
    return menuTypeOf(dashboardTarget) === targetType;
  });
  const exactDashboard = sameTypeDashboards.find(dashboard => {
    return menuParamsOf(visualizationTargetRecordOf(dashboard)) === targetParams;
  });
  if (exactDashboard || !isVisualizationDashboardRecord(record)) {
    return exactDashboard;
  }
  return (
    sameTypeDashboards.find(dashboard => isVisualizationDashboardRecord(dashboard)) ||
    sameTypeDashboards[0]
  );
};
