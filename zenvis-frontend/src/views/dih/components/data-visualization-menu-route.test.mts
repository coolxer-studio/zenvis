import assert from 'node:assert/strict';
import test from 'node:test';
import {
  buildMenuTargetRoute,
  buildVisualizationTargetRoute,
  findVisualizationDashboardRecord,
  findVisualizationMenuRecord,
  isVisualizationDashboardRecord,
} from './data-visualization-menu-route.ts';

test('低代码页面和应用使用配置索引打开实际页面', () => {
  assert.deepEqual(
    buildVisualizationTargetRoute(
      {
        type: 'LOW_CODE_PAGE',
        configIndex: 'user-event-page',
      },
      [],
    ),
    {
      name: 'low-code-page',
      params: { menuParams: 'user-event-page' },
    },
  );
  assert.deepEqual(
    buildVisualizationTargetRoute(
      {
        type: 'LOW_CODE_APP',
        configIndex: 'user-event-app',
      },
      [],
    ),
    {
      name: 'low-code-app',
      params: { menuParams: 'user-event-app' },
    },
  );
});

test('user-event-app 不受错误的 user-event-page 配置索引影响', () => {
  assert.deepEqual(
    buildVisualizationTargetRoute(
      {
        configType: 'user-event-app',
        type: 'LOW_CODE_PAGE',
        configIndex: 'user-event-page',
        fileName: 'site.json',
      },
      [],
    ),
    {
      name: 'low-code-app',
      params: { menuParams: 'user-event-app' },
    },
  );
});

test('HTML 页面参数使用 UTF-8 Base64 编码', () => {
  const route = buildVisualizationTargetRoute(
    {
      type: 'HTML_PAGE',
      configIndex: '/html-page/用户事件.html',
    },
    [],
  );

  assert.ok(route && 'name' in route);
  assert.equal(route.name, 'html-page');
  assert.equal(
    new TextDecoder().decode(
      Uint8Array.from(atob(route.params.menuParams), char => char.charCodeAt(0)),
    ),
    '/html-page/用户事件.html',
  );
});

test('html-page 根据当前文件名打开页面，不复用 user-event-page', () => {
  const route = buildVisualizationTargetRoute(
    {
      configType: 'html-page',
      type: 'LOW_CODE_PAGE',
      configIndex: 'user-event-page',
      fileName: 'user-event-dashboard.html',
    },
    [],
  );

  assert.ok(route && 'name' in route);
  assert.equal(route.name, 'html-page');
  assert.equal(
    new TextDecoder().decode(
      Uint8Array.from(atob(route.params.menuParams), char => char.charCodeAt(0)),
    ),
    '/html-page/user-event-dashboard.html',
  );
});

test('HTML 看板页面配置能匹配对应的数据看板记录', () => {
  const dashboard = {
    dashboardId: '402',
    code: 'user-event-html-dashboard',
    type: 'HTML_PAGE',
    htmlPath: 'user-event-dashboard.html',
  };

  assert.equal(
    findVisualizationDashboardRecord(
      {
        configType: 'html-page',
        fileName: 'user-event-dashboard.html',
        type: 'HTML_PAGE',
        configIndex: 'user-event-dashboard.html',
      },
      [dashboard],
    ),
    dashboard,
  );
  assert.equal(
    findVisualizationDashboardRecord(
      {
        configType: 'html-page',
        fileName: 'user-event-page.html',
        type: 'HTML_PAGE',
      },
      [dashboard],
    ),
    undefined,
  );
});

test('HTML 看板历史字段不一致时仍强制匹配同类型数据看板', () => {
  const dashboard = {
    dashboardId: '402',
    name: '用户事件 HTML 看板',
    code: 'user-event-html-dashboard',
    type: 'HTML_PAGE',
    htmlPath: 'user-event-dashboard.html',
  };
  const staleVisualizationRecord = {
    name: '用户事件 HTML 看板页面已写入',
    configType: 'html-page',
    fileName: 'user-event-page.html',
    type: 'HTML_PAGE',
    configIndex: 'user-event-page',
  };

  assert.equal(isVisualizationDashboardRecord(staleVisualizationRecord), true);
  assert.equal(findVisualizationDashboardRecord(staleVisualizationRecord, [dashboard]), dashboard);
});

test('优先使用与可视化配置对应的真实菜单路由', () => {
  const configRecord = {
    type: 'LOW_CODE_PAGE',
    configIndex: 'user-event-page',
  };
  const menuRecord = {
    type: 'LOW_CODE_PAGE',
    params: 'user-event-page',
    route: '/custom/user-event',
  };

  assert.equal(findVisualizationMenuRecord(configRecord, [menuRecord]), menuRecord);
  assert.deepEqual(buildVisualizationTargetRoute(configRecord, [menuRecord]), {
    path: '/custom/user-event',
  });
});

test('缺少页面类型或参数时不生成错误链接', () => {
  assert.equal(buildMenuTargetRoute({ configIndex: 'user-event-page' }), null);
  assert.equal(buildMenuTargetRoute({ type: 'LOW_CODE_PAGE' }), null);
});
