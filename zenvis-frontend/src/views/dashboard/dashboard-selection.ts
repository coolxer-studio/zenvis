import type { Dashboard } from '@/types/type-system';

export type DashboardRouteQuery = {
  id?: unknown;
  code?: unknown;
  name?: unknown;
};

type DashboardSelectionItem = Pick<Dashboard, 'id' | 'name' | 'code' | 'isDefault'>;

const queryText = (value: unknown) => {
  const first = Array.isArray(value) ? value[0] : value;
  return first === undefined || first === null ? '' : String(first).trim();
};

export const resolveDashboardForRoute = <T extends DashboardSelectionItem>(
  dashboards: T[],
  query: DashboardRouteQuery,
): T | undefined => {
  if (!dashboards.length) return undefined;

  const targetId = queryText(query.id);
  if (targetId) {
    const byId = dashboards.find(item => String(item.id) === targetId);
    if (byId) return byId;
  }

  const targetCode = queryText(query.code);
  if (targetCode) {
    const byCode = dashboards.find(item => String(item.code || '').trim() === targetCode);
    if (byCode) return byCode;
  }

  const targetName = queryText(query.name);
  if (targetName) {
    const byName = dashboards.filter(
      item => String(item.name || '').trim() === targetName,
    );
    if (byName.length === 1) return byName[0];
  }

  return dashboards.find(item => item.isDefault) || dashboards[0];
};
