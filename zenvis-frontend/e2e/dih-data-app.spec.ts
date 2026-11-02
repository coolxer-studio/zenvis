import { expect, test, type Page, type Route } from '@playwright/test';

const APP_TYPE = 'agent_visualization_app';
const WORKFLOW_ID = 'data-app-workflow-1';
const PARTIAL_WORKFLOW_ID = 'data-app-workflow-2';
const CHAT_ID = 'e2e-data-app';
const PREVIEW_SITE_URL =
  `/api/v1/dih/chat/workflows/${WORKFLOW_ID}/app-preview/site` +
  `?chat_id=${CHAT_ID}&token=preview-token`;
const PREVIEW_FILES_URL =
  `/api/v1/dih/chat/workflows/${WORKFLOW_ID}/app-preview/files` +
  `?chat_id=${CHAT_ID}&token=preview-token`;

const ok = (data: unknown) => ({ status: 0, code: 0, msg: 'success', data });
const fulfillJson = (route: Route, data: unknown) =>
  route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });

const records = Array.from({ length: 25 }, (_, index) => ({
  zenvis_id: String(index + 1),
  event_type: index % 2 === 0 ? 'login' : 'logout',
  event_name: `事件 ${index + 1}`,
  duration: 100 + index,
  server_time: `2026-10-${String((index % 9) + 1).padStart(2, '0')} 10:00:00`,
}));

const site = {
  status: 0,
  msg: '',
  data: {
    pages: [
      { label: 'Home', url: '/', redirect: '/overview' },
      {
        label: '用户事件分析',
        children: [
          {
            label: '总览',
            url: 'overview',
            icon: 'fa-solid fa-chart-line',
            schemaApi: `get:${PREVIEW_FILES_URL}&path=pages%2Foverview.json`,
          },
          {
            label: '明细',
            url: 'details',
            icon: 'fa-solid fa-table-list',
            schemaApi: `get:${PREVIEW_FILES_URL}&path=pages%2Fdetails.json`,
          },
        ],
      },
    ],
  },
};

const overviewPage = {
  type: 'page',
  title: '总览',
  body: [
    {
      type: 'panel',
      title: '关键指标',
      body: {
        type: 'service',
        api: {
          method: 'post',
          url: '/zenvis/api/v1/entity/overview/query',
          data: { entities: ['user_event'], time_range: { preset: 'TODAY' } },
          adaptor:
            'var d=payload&&payload.data?payload.data:{};' +
            'var r=d.result&&d.result.rows&&d.result.rows[0]?d.result.rows[0]:{};' +
            "return {status:0,msg:'',data:{events:r.events===undefined?'':r.events}};",
        },
        body: {
          type: 'grid',
          columns: [
            {
              md: 6,
              body: {
                type: 'panel',
                title: '事件总数',
                body: "<div style='font-size:30px;font-weight:700'>${events}</div>",
              },
            },
          ],
        },
      },
    },
    {
      type: 'panel',
      title: '事件趋势',
      body: {
        type: 'chart',
        height: 280,
        api: {
          method: 'post',
          url: '/zenvis/api/v1/entity/trend/query',
          data: { entities: ['user_event'], time_range: { preset: 'LAST_7_DAYS' } },
          adaptor:
            'var d=payload&&payload.data?payload.data:{};' +
            'var o=d.echarts&&d.echarts.option?d.echarts.option:{};' +
            "return {status:0,msg:'',data:{chart_dataset:o.dataset||{source:[]},chart_series:o.series||[]}};",
        },
        config: {
          title: { text: '事件趋势' },
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category' },
          yAxis: { type: 'value' },
          dataset: '${chart_dataset}',
          series: '${chart_series}',
        },
      },
    },
  ],
};

const recordApi = {
  method: 'post',
  url: '/zenvis/api/v1/retrieval/do',
  data: {
    type: 'normal',
    entity: 'user_event',
    criteria_logic: 'and',
    criteria_list: [],
    display_list: [
      {
        entity: 'user_event',
        attribute_list: ['event_type', 'duration', 'zenvis_id'],
      },
    ],
    page: '${page}',
    size: '${perPage}',
    sortBy: '',
    order: 'desc',
  },
  requestAdaptor:
    'var values=context||{};api.data.criteria_list=[];' +
    "if(values.event_type){api.data.criteria_list=[{attribute:'event_type'," +
    "operator:'equal',value_list:[String(values.event_type)]}];" +
    '} api.data.page=Number(values.page||1);' +
    'api.data.size=Number(api.data.size||20);return api;',
  adaptor:
    'var d=payload&&payload.data?payload.data:(payload||{});' +
    'var rows=d.datalist||d.rows||[];' +
    "return {status:0,msg:'',data:{items:rows,total:Number(d.total||rows.length||0)}};",
};

const detailsPage = {
  type: 'page',
  title: '事件明细',
  body: [
    {
      type: 'crud',
      name: 'records-details',
      title: '事件明细',
      api: recordApi,
      columns: [
        { name: 'event_type', label: '事件类型', sortable: true },
        { name: 'duration', label: '耗时', sortable: true },
        { name: 'zenvis_id', type: 'hidden' },
        {
          type: 'operation',
          label: '操作',
          buttons: [
            {
              type: 'button',
              label: '详情',
              level: 'link',
              actionType: 'dialog',
              dialog: {
                title: '记录详情',
                body: {
                  type: 'service',
                  api: 'get:/zenvis/api/v1/entity/user_event/${zenvis_id}/view',
                  body: {
                    type: 'property',
                    column: 2,
                    items: [
                      { label: '事件类型', content: '${event_type}' },
                      { label: '事件名称', content: '${event_name}' },
                    ],
                  },
                },
              },
            },
          ],
        },
      ],
      filter: {
        type: 'form',
        wrapWithPanel: false,
        body: [{ type: 'input-text', name: 'event_type', label: '事件类型' }],
        submitText: '查询',
        resetAfterSubmit: false,
        actions: [
          { type: 'reset', label: '重置' },
          { type: 'submit', label: '查询', level: 'primary' },
        ],
      },
      perPage: 20,
      pageField: 'page',
      perPageField: 'perPage',
      placeholder: '当前实体暂无数据',
    },
  ],
};

type PreviewPartOptions = {
  id?: string;
  workflowId?: string;
  validationStatus?: 'validated' | 'partial_menu';
  menuPublishStatus?: string;
  previewSiteUrl?: string | null;
};

const previewPart = (options: PreviewPartOptions = {}) => {
  const id = options.id || 'data-app-preview-part';
  const workflowId = options.workflowId || WORKFLOW_ID;
  const validationStatus = options.validationStatus || 'validated';
  const menuPublishStatus = options.menuPublishStatus || 'pending';
  const previewSiteUrl =
    options.previewSiteUrl === undefined ? PREVIEW_SITE_URL : options.previewSiteUrl;
  return {
    id,
    type: 'visualization-app-preview',
    status: 'pending',
    title: '用户事件分析',
    content: '已基于已发布 Meta 完成编译，并取得真实数据查询样本。',
    metadata: {
      workflowId,
      candidateDigest: 'candidate-digest-1234567890',
      validationDigest: 'validation-digest-1234567890',
      validationStatus,
      validation: {
        appId: 'user-event-analysis',
        appVersion: 3,
        mode: 'LOW_CODE_APP',
        previousMode: 'LOW_CODE_PAGE',
        specDigest: 'spec-digest-1234567890',
        metaDigest: 'meta-digest-1234567890',
        validationStatus,
        files: [
          { path: 'pages/overview.json', size: 4096 },
          { path: 'pages/details.json', size: 3072 },
          { path: 'site.json', size: 1024 },
        ],
        deletedFiles: ['index.json'],
        diagnostics: [],
        preview: {
          siteUrl: previewSiteUrl,
          pages: [
            { pageId: 'overview', title: '总览' },
            { pageId: 'details', title: '明细' },
          ],
          querySamples: {
            overview: { entity: 'user_event', total: 42 },
            details: { entity: 'user_event', rowCount: 25 },
          },
        },
        menu: {
          menuId: 31,
          menuName: '用户事件分析',
          menuType: 'LOW_CODE_APP',
          publishStatus: menuPublishStatus,
        },
      },
    },
  };
};

const sessionMessage = (parts: Array<Record<string, unknown>>) => ({
  id: 'data-app-message-1',
  sender: 'ai',
  content: '应用校验完成。',
  time: '2026-11-01 10:00:00',
  type: 'text',
  parts,
});

const extraData = JSON.stringify({
  visualizationApp: {
    applications: [
      {
        appId: 'user-event-analysis',
        name: '用户事件分析',
        appVersion: 3,
        mode: 'LOW_CODE_APP',
        workflowId: WORKFLOW_ID,
        pages: [
          { pageId: 'overview', title: '总览' },
          { pageId: 'details', title: '明细' },
        ],
        configStatus: 'verified',
        menuStatus: 'published',
        publishStatus: 'verified',
        menuId: 31,
        menuName: '用户事件分析',
        menuType: 'LOW_CODE_APP',
        params: 'user-event-analysis',
      },
    ],
    configs: [],
    menus: [],
  },
});

const aggregatedExtraData = JSON.stringify({
  visualizationApp: {
    applications: [
      {
        appId: 'user-event-analysis',
        name: '用户事件分析',
        appVersion: 3,
        mode: 'LOW_CODE_APP',
        workflowId: WORKFLOW_ID,
        pages: [
          { pageId: 'overview', title: '总览' },
          { pageId: 'details', title: '明细' },
        ],
        configStatus: 'verified',
        menuStatus: 'published',
        publishStatus: 'verified',
        menuId: 31,
        menuName: '用户事件分析',
        menuType: 'LOW_CODE_APP',
        params: 'user-event-analysis',
      },
      {
        appId: 'risk-monitor',
        name: '事件风险监控',
        appVersion: 5,
        mode: 'LOW_CODE_APP',
        workflowId: PARTIAL_WORKFLOW_ID,
        pages: [{ pageId: 'risks', title: '风险列表' }],
        configStatus: 'verified',
        menuStatus: 'partial_menu',
        publishStatus: 'partial_menu',
        menuId: 44,
        menuName: '事件风险监控',
        menuType: 'LOW_CODE_APP',
        params: 'risk-monitor',
      },
    ],
    configs: [],
    menus: [],
  },
});

const installMocks = async (
  page: Page,
  options: {
    parts: Array<Record<string, unknown>>;
    extraData?: string;
  },
) => {
  const retrievalBodies: Array<Record<string, unknown>> = [];
  const chatBodies: Array<Record<string, unknown>> = [];
  const workflowActions: Array<Record<string, unknown>> = [];
  const viewedRecordIds: string[] = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path.endsWith('/skills/chat-entries')) {
      await fulfillJson(
        route,
        ok([
          {
            skill_id: 'visualization-app-agent',
            chat_type: APP_TYPE,
            agent_type: APP_TYPE,
            label: '可视化应用',
            icon: 'monitor',
          },
        ]),
      );
      return;
    }
    if (path.endsWith('/model/list') || path.endsWith('/chat-session/list/pin')) {
      await fulfillJson(route, ok([]));
      return;
    }
    if (path.endsWith('/chat-session/list')) {
      await fulfillJson(route, ok({ rows: [], total: 0 }));
      return;
    }
    if (path.includes('/chat-session/') && path.endsWith('/session') && method === 'GET') {
      await fulfillJson(
        route,
        ok({
          id: 'session-record-data-app',
          session_id: CHAT_ID,
          title: '数据应用验收',
          type: APP_TYPE,
          message_list: [sessionMessage(options.parts)],
          extra_data: options.extraData || extraData,
        }),
      );
      return;
    }
    if (path.endsWith('/chat/runs') && method === 'GET') {
      await fulfillJson(route, ok({ rows: [], total: 0 }));
      return;
    }
    if (path.endsWith('/chat/workflow/action') && method === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>;
      workflowActions.push(body);
      await fulfillJson(
        route,
        ok({
          accepted: true,
          workflow_id: body.workflow_id,
          state: body.action === 'retry' ? 'PARTIAL_MENU' : 'APP_APPLYING',
          part_status: body.action === 'retry' ? 'retrying' : 'approved',
          continuation: {},
          retryable: false,
        }),
      );
      return;
    }
    if (path.endsWith('/app-preview/site') && method === 'GET') {
      await fulfillJson(route, site);
      return;
    }
    if (path.endsWith('/app-preview/files') && method === 'GET') {
      const file = url.searchParams.get('path') || '';
      if (file === 'pages/overview.json') {
        await fulfillJson(route, overviewPage);
      } else if (file === 'pages/details.json') {
        await fulfillJson(route, detailsPage);
      } else {
        await route.fulfill({ status: 404, contentType: 'application/json', body: '{}' });
      }
      return;
    }
    if (path.endsWith('/entity/overview/query') && method === 'POST') {
      await fulfillJson(route, ok({ result: { rows: [{ events: 42 }] } }));
      return;
    }
    if (path.endsWith('/entity/trend/query') && method === 'POST') {
      await fulfillJson(
        route,
        ok({
          echarts: {
            option: {
              dataset: {
                source: [
                  { day: '10-01', value: 12 },
                  { day: '10-02', value: 18 },
                ],
              },
              series: [{ type: 'line', encode: { x: 'day', y: 'value' } }],
            },
          },
        }),
      );
      return;
    }
    if (path.endsWith('/retrieval/do') && method === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>;
      retrievalBodies.push(body);
      const pageNumber = Math.max(1, Number(body.page || 1));
      const size = Math.max(1, Number(body.size || 20));
      const rows = records.slice((pageNumber - 1) * size, pageNumber * size);
      await fulfillJson(route, ok({ datalist: rows, total: records.length }));
      return;
    }
    const viewMatch = path.match(/\/entity\/user_event\/([^/]+)\/view$/);
    if (viewMatch && method === 'GET') {
      const id = decodeURIComponent(viewMatch[1]);
      viewedRecordIds.push(id);
      await fulfillJson(route, ok(records.find(row => row.zenvis_id === id) || records[0]));
      return;
    }
    if (path.endsWith('/dih/chat') && method === 'POST') {
      if (request.postDataJSON) {
        chatBodies.push(request.postDataJSON() as Record<string, unknown>);
      }
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body: '',
      });
      return;
    }
    await fulfillJson(route, ok({}));
  });
  return { retrievalBodies, chatBodies, workflowActions, viewedRecordIds };
};

const openPage = (page: Page) =>
  page.goto(`/#/service/dih?type=${APP_TYPE}&chatSessionId=${CHAT_ID}&token=e2e-token`);

test('数据应用预览在真实 AMIS 中完成多页导航、筛选、分页和详情且隐藏写操作', async ({ page }) => {
  const pageErrors: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error') pageErrors.push(`console: ${message.text()}`);
  });
  await page.setViewportSize({ width: 1500, height: 1000 });
  const mocks = await installMocks(page, { parts: [previewPart()] });
  await openPage(page);

  const card = page.locator('.data-app-preview');
  await expect(card).toBeVisible({ timeout: 15_000 });
  await expect(card).toContainText('用户事件分析');
  await expect(card).toContainText('总览');
  await expect(card).toContainText('明细');
  await expect(card).toContainText('pages/overview.json');
  await expect(card.getByRole('button', { name: '确认发布应用' })).toBeVisible();

  const previewFrame = () =>
    page.frames().find(candidate => candidate.url().includes('/amis/app.html'));
  const waitForOverview = async () => {
    const found = previewFrame();
    if (!found) throw new Error('预览 iframe 尚未创建');
    await expect(found.locator('text=事件总数')).toBeVisible({ timeout: 20_000 });
    await expect(found.locator('text=42')).toBeVisible();
    await expect(found.locator('.cxd-Chart').first()).toBeVisible({ timeout: 20_000 });
    await page.waitForTimeout(1500);
    expect(pageErrors).toEqual([]);
    return found;
  };
  let activeFrame = await waitForOverview();

  await activeFrame.locator('a[href="/details"]').evaluate(element => element.click());
  await expect(activeFrame.getByText('事件明细', { exact: true }).first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(activeFrame.getByRole('cell', { name: 'login', exact: true }).first()).toBeVisible();
  await expect(activeFrame.getByRole('button', { name: '详情' }).first()).toBeVisible();
  await expect(activeFrame.getByRole('button', { name: '新建' })).toHaveCount(0);
  await expect(activeFrame.getByRole('button', { name: '编辑' })).toHaveCount(0);
  await expect(activeFrame.getByRole('button', { name: '删除' })).toHaveCount(0);

  const filterInput = activeFrame
    .locator('.cxd-Form-item', { hasText: '事件类型' })
    .locator('input')
    .first();
  await filterInput.fill('login');
  await filterInput.press('Enter');
  await page.waitForTimeout(1000);
  await expect
    .poll(() =>
      mocks.retrievalBodies.some(
        body =>
          Array.isArray(body.criteria_list) &&
          (body.criteria_list as Array<Record<string, unknown>>).some(
            item =>
              item.attribute === 'event_type' &&
              Array.isArray(item.value_list) &&
              (item.value_list as unknown[]).includes('login'),
          ),
      ),
    )
    .toBe(true);

  await activeFrame.locator('.cxd-Pagination-next').click();
  await expect.poll(() => mocks.retrievalBodies.some(body => Number(body.page) === 2)).toBe(true);

  await activeFrame.getByRole('button', { name: '详情' }).first().click();
  await expect(activeFrame.getByText('记录详情', { exact: true })).toBeVisible({ timeout: 15_000 });
  await expect(activeFrame.getByText('事件名称').first()).toBeVisible();
  await expect.poll(() => mocks.viewedRecordIds.length).toBeGreaterThan(0);
});

test('确认发布应用时提交工作流的 approve 动作', async ({ page }) => {
  await page.setViewportSize({ width: 1500, height: 1000 });
  const mocks = await installMocks(page, {
    parts: [previewPart({ previewSiteUrl: null })],
  });
  await openPage(page);

  const card = page.locator('.data-app-preview');
  await expect(card).toBeVisible({ timeout: 15_000 });
  await card.getByRole('button', { name: '确认发布应用' }).click();

  const dialog = page.locator('.el-message-box');
  await expect(dialog).toContainText('发布应用配置与菜单');
  await dialog.getByRole('button', { name: '确认', exact: true }).click();

  await expect.poll(() => mocks.workflowActions.length).toBe(1);
  expect(mocks.workflowActions[0]).toMatchObject({
    chat_id: CHAT_ID,
    message_id: 'data-app-message-1',
    part_id: 'data-app-preview-part',
    workflow_id: WORKFLOW_ID,
    action: 'approve',
  });
});

test('部分成功卡片展示菜单待重试并提交锁定版本的 retry 动作', async ({ page }) => {
  await page.setViewportSize({ width: 1500, height: 1000 });
  const mocks = await installMocks(page, {
    parts: [
      previewPart({
        validationStatus: 'partial_menu',
        menuPublishStatus: 'partial_menu',
        previewSiteUrl: null,
      }),
    ],
  });
  await openPage(page);

  const card = page.locator('.data-app-preview');
  await expect(card).toBeVisible({ timeout: 15_000 });
  await expect(card).toContainText('菜单待重试');
  await expect(card).toContainText('部分成功，可重试');
  await card.getByRole('button', { name: '重试菜单发布' }).click();

  const dialog = page.locator('.el-message-box');
  await expect(dialog).toContainText('仅重试已锁定版本的菜单发布');
  await dialog.getByRole('button', { name: '确认', exact: true }).click();

  await expect.poll(() => mocks.workflowActions.length).toBe(1);
  expect(mocks.workflowActions[0]).toMatchObject({
    message_id: 'data-app-message-1',
    part_id: 'data-app-preview-part',
    workflow_id: WORKFLOW_ID,
    action: 'retry',
  });
});

test('右侧应用结果按 appId 聚合两个应用并可定位工作流重试菜单', async ({ page }) => {
  await page.setViewportSize({ width: 1500, height: 1000 });
  const mocks = await installMocks(page, {
    parts: [
      previewPart({ previewSiteUrl: null }),
      previewPart({
        id: 'partial-preview-part',
        workflowId: PARTIAL_WORKFLOW_ID,
        validationStatus: 'partial_menu',
        menuPublishStatus: 'partial_menu',
        previewSiteUrl: null,
      }),
    ],
    extraData: aggregatedExtraData,
  });
  await openPage(page);

  const results = page.locator('.data-app-results');
  await expect(results).toBeVisible({ timeout: 15_000 });
  await expect(results).toContainText('按 appId 聚合');
  await expect(results).toContainText('2 个应用');
  await expect(
    results.locator('.data-app-results__app', { hasText: 'user-event-analysis' }),
  ).toBeVisible();
  await expect(
    results.locator('.data-app-results__app', { hasText: 'risk-monitor' }),
  ).toBeVisible();

  const partialRow = results.locator('.el-table__row', { hasText: '事件风险监控' });
  await expect(partialRow).toContainText('部分成功');
  await partialRow.getByRole('button', { name: '重试菜单发布' }).click();

  const dialog = page.locator('.el-message-box');
  await expect(dialog).toContainText('仅重试当前锁定版本的菜单发布');
  await dialog.getByRole('button', { name: '确认重试', exact: true }).click();

  await expect.poll(() => mocks.workflowActions.length).toBe(1);
  expect(mocks.workflowActions[0]).toMatchObject({
    part_id: 'partial-preview-part',
    workflow_id: PARTIAL_WORKFLOW_ID,
    action: 'retry',
  });
});
