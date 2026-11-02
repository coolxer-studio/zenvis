import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveDashboardForRoute } from './dashboard-selection.ts';

const dashboard = (
  id: number,
  name: string,
  code: string,
  type: string,
  isDefault = false,
) => ({
  id,
  name,
  code,
  type,
  isDefault,
});

test('同名看板存在时优先按 id 选择并忽略更早的同名记录', () => {
  const oldLowCode = dashboard(2, '学校元数据总览大屏', 'school-info-dashboard', 'LOW_CODE_PAGE');
  const newHtml = dashboard(103, '学校元数据总览大屏', 'school-meta-dashboard', 'HTML_PAGE');

  assert.equal(
    resolveDashboardForRoute([oldLowCode, newHtml], {
      id: '103',
      code: 'school-meta-dashboard',
      name: '学校元数据总览大屏',
    }),
    newHtml,
  );
});

test('缺少 id 时按 code 精确选择，不受同名记录影响', () => {
  const oldLowCode = dashboard(2, '学校元数据总览大屏', 'school-info-dashboard', 'LOW_CODE_PAGE');
  const newHtml = dashboard(103, '学校元数据总览大屏', 'school-meta-dashboard', 'HTML_PAGE');

  assert.equal(
    resolveDashboardForRoute([oldLowCode, newHtml], {
      code: 'school-meta-dashboard',
      name: '学校元数据总览大屏',
    }),
    newHtml,
  );
});

test('同名记录不唯一时不按名称猜测，回退默认看板', () => {
  const oldLowCode = dashboard(2, '学校元数据总览大屏', 'school-info-dashboard', 'LOW_CODE_PAGE');
  const newHtml = dashboard(103, '学校元数据总览大屏', 'school-meta-dashboard', 'HTML_PAGE');
  const defaultDashboard = dashboard(1, '系统状态总览', 'system-board', 'BUILT', true);

  assert.equal(
    resolveDashboardForRoute([oldLowCode, newHtml, defaultDashboard], {
      name: '学校元数据总览大屏',
    }),
    defaultDashboard,
  );
});

test('名称唯一时仍可作为兼容回退，并兼容数组 query 参数', () => {
  const only = dashboard(103, '学校元数据总览大屏', 'school-meta-dashboard', 'HTML_PAGE');

  assert.equal(
    resolveDashboardForRoute([only], { name: ['学校元数据总览大屏'] }),
    only,
  );
});
