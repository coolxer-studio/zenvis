import { expect, test, type Frame, type Page, type Route } from '@playwright/test';

const DASHBOARD_TYPE = 'agent_visualization_dashboard';
const WORKFLOW_ID = 'data-dashboard-workflow-1';
const CHAT_ID = 'e2e-data-dashboard';
const TITLE = '用户事件运营大屏';
const PREVIEW_URL =
  `/api/v1/dih/chat/workflows/${WORKFLOW_ID}/dashboard-preview` +
  `?chat_id=${CHAT_ID}&token=preview-token`;

const ok = (data: unknown) => ({ status: 0, code: 0, msg: 'success', data });

const fulfillJson = (route: Route, data: unknown) =>
  route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });

const previewHtml = `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${TITLE}</title>
  <style>
    * { box-sizing: border-box; }
    html, body { margin: 0; min-height: 100%; background: #061426; color: #e7f4ff; font-family: sans-serif; }
    body { overflow-x: hidden; }
    .shell { width: 100%; max-width: 100%; padding: 18px; }
    .top { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-width: 0; }
    .title { min-width: 0; font-size: clamp(18px, 1.8vw, 30px); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .actions { display: flex; gap: 8px; flex: 0 0 auto; }
    .grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: 12px; margin-top: 16px; }
    .card { min-width: 0; padding: 14px; border: 1px solid rgba(77, 163, 255, .32); border-radius: 10px; background: rgba(10, 35, 68, .82); overflow: hidden; }
    .kpi { grid-column: span 4; }
    .kpi strong { display: block; font-size: clamp(24px, 3vw, 54px); color: #5ce1e6; overflow: hidden; text-overflow: ellipsis; }
    .main { grid-column: span 8; min-height: 230px; }
    .side { grid-column: span 4; min-height: 230px; }
    .records { grid-column: span 12; }
    .chart { height: 180px; border-radius: 8px; background: linear-gradient(180deg, rgba(55, 194, 255, .2), rgba(55, 194, 255, .02)); }
    table { width: 100%; border-collapse: collapse; table-layout: fixed; }
    th, td { padding: 7px; border-bottom: 1px solid rgba(77, 163, 255, .18); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    @media (max-width: 900px) {
      .kpi, .main, .side { grid-column: span 12; }
    }
  </style>
</head>
<body>
  <main class="shell">
    <header class="top">
      <div class="title">${TITLE}</div>
      <div class="actions"><span>最近 7 天</span><span>刷新</span></div>
    </header>
    <section class="grid">
      <article class="card kpi"><span>事件总数</span><strong>128,460</strong></article>
      <article class="card kpi"><span>活跃用户</span><strong>3,284</strong></article>
      <article class="card kpi"><span>成功率</span><strong>99.2%</strong></article>
      <article class="card main"><h2>事件趋势</h2><div class="chart"></div></article>
      <article class="card side"><h2>事件类型分布</h2><div class="chart"></div></article>
      <article class="card records">
        <h2>最近事件明细</h2>
        <table><thead><tr><th>时间</th><th>事件类型</th><th>用户</th></tr></thead>
        <tbody><tr><td>2026-11-02 10:00:00</td><td>login</td><td>user-1001</td></tr></tbody></table>
      </article>
    </section>
  </main>
</body>
</html>`;

const previewPart = (options: {
  mode?: 'HTML_PAGE' | 'LOW_CODE_PAGE';
  previousMode?: string;
  validationStatus?: string;
  previewUrl?: string | null;
  diagnostics?: Array<Record<string, unknown>>;
} = {}) => {
  const mode = options.mode || 'HTML_PAGE';
  const validationStatus = options.validationStatus || 'validated';
  return {
    id: 'data-dashboard-preview-part',
    type: 'visualization-dashboard-preview',
    status: 'pending',
    title: TITLE,
    content: '已完成服务端编译、Meta 校验和真实只读查询取样。',
    metadata: {
      validationStatus,
      workflowId: WORKFLOW_ID,
      candidateDigest: 'candidate-digest-1234567890',
      validationDigest: 'validation-digest-1234567890',
      specDigest: 'spec-digest-1234567890',
      metaDigest: 'meta-digest-1234567890',
      fileDigest: 'file-digest-1234567890',
      dashboardId: 'user-event-dashboard',
      name: TITLE,
      dashboardVersion: 2,
      mode,
      previousMode: options.previousMode || '',
      theme: 'DARK_TECH',
      refreshIntervalSeconds: 60,
      diagnostics: options.diagnostics || [],
      files: [
        { path: mode === 'HTML_PAGE' ? 'user-event-dashboard.html' : 'index.json', size: 4096 },
        { path: mode === 'HTML_PAGE' ? 'user-event-dashboard.manifest.json' : '_dashboard.json', size: 1024 },
      ],
      preview: {
        htmlUrl: options.previewUrl === undefined ? PREVIEW_URL : options.previewUrl,
        querySamples: {
          kpis: { rowCount: 1, empty: false },
          trend: { rowCount: 7, empty: false },
          records: { rowCount: 12, empty: false },
        },
      },
      spec: {
        dashboard_id: 'user-event-dashboard',
        name: TITLE,
        mode,
        theme: 'DARK_TECH',
        refresh_interval_seconds: 60,
        default_time_range: 'LAST_7_DAYS',
        blocks: [
          { block_id: 'kpis', type: 'metrics', title: '关键指标', query: { tool: 'entity_summary' } },
          { block_id: 'trend', type: 'chart', title: '事件趋势', query: { tool: 'entity_trend' } },
          { block_id: 'records', type: 'records', title: '最近事件明细' },
        ],
      },
    },
  };
};

const planPart = () => ({
  id: 'data-dashboard-plan-part',
  type: 'visualization-dashboard-decision',
  status: 'pending',
  title: '确认数据大屏方案',
  content: '建议展示事件总量、趋势、类型分布和最近事件明细。',
  metadata: {
    workflowId: WORKFLOW_ID,
    action: 'visualization_dashboard.confirm_dashboard_plan',
    step: 'DASHBOARD_PLAN_CONFIRMATION',
    allowedActions: ['approve', 'reject', 'revise'],
  },
});

const retryPart = () => ({
  id: 'data-dashboard-blocked-part',
  type: 'visualization-dashboard-decision',
  status: 'blocked',
  title: '数据大屏发布被阻断',
  content: 'Dashboard 读回不一致，配置已回滚。',
  metadata: {
    workflowId: WORKFLOW_ID,
    action: 'visualization_dashboard.retry',
    step: 'BLOCKED',
    validationStatus: 'blocked',
    allowedActions: ['retry', 'revise', 'reject'],
  },
});

const installMocks = async (
  page: Page,
  options: { parts: Array<Record<string, unknown>> },
) => {
  const workflowActions: Array<Record<string, unknown>> = [];
  const apiRequests: Array<{ method: string; path: string }> = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    apiRequests.push({ method, path });

    if (path.endsWith('/skills/chat-entries')) {
      await fulfillJson(
        route,
        ok([
          {
            skill_id: 'visualization-dashboard-agent',
            chat_type: DASHBOARD_TYPE,
            agent_type: DASHBOARD_TYPE,
            label: '数据大屏',
            icon: 'operation',
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
          id: 'session-record-data-dashboard',
          session_id: CHAT_ID,
          title: '数据大屏验收',
          type: DASHBOARD_TYPE,
          message_list: [
            {
              id: 'data-dashboard-message-1',
              sender: 'ai',
              content: '数据大屏校验完成。',
              time: '2026-11-02 10:00:00',
              type: 'text',
              parts: options.parts,
            },
          ],
          extra_data: JSON.stringify({
            visualizationDashboard: { configs: [], dashboards: [], menus: [] },
          }),
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
          state: body.action === 'retry' ? 'DASHBOARD_VALIDATING' : 'DASHBOARD_APPLYING',
          part_status: body.action === 'retry' ? 'retrying' : 'approved',
          continuation: {},
          retryable: false,
        }),
      );
      return;
    }
    if (path.endsWith('/dashboard-preview') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'text/html; charset=utf-8',
        body: previewHtml,
      });
      return;
    }
    await fulfillJson(route, ok({}));
  });
  return { workflowActions, apiRequests };
};

const openPage = (page: Page) =>
  page.goto(`/#/service/dih?type=${DASHBOARD_TYPE}&chatSessionId=${CHAT_ID}&token=e2e-token`);

const dashboardFrame = (page: Page): Frame => {
  const frame = page
    .frames()
    .find(candidate => candidate.url().includes(`/workflows/${WORKFLOW_ID}/dashboard-preview`));
  if (!frame) throw new Error('大屏预览 iframe 尚未创建');
  return frame;
};

test('HTML 大屏在四档桌面宽度下无横向溢出且只读取受控预览', async ({ page }) => {
  const mocks = await installMocks(page, { parts: [previewPart()] });
  await openPage(page);

  const card = page.locator('.dashboard-preview');
  await expect(card).toBeVisible({ timeout: 15_000 });
  await expect(card).toContainText(TITLE);
  await expect(card).toContainText('静态 HTML 大屏');
  await expect(card).toContainText('user-event-dashboard.html');
  await expect(card).toContainText('事件趋势');
  await expect(card.getByRole('button', { name: '确认发布大屏' })).toBeVisible();

  const frame = dashboardFrame(page);
  await expect(frame.getByText(TITLE, { exact: true })).toBeVisible({ timeout: 15_000 });

  for (const viewport of [
    { width: 1366, height: 768 },
    { width: 1920, height: 1080 },
    { width: 2560, height: 1440 },
    { width: 3840, height: 2160 },
  ]) {
    await page.setViewportSize(viewport);
    await page.waitForTimeout(120);
    const overflow = await dashboardFrame(page).evaluate(() => ({
      page: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      body: document.body.scrollWidth > document.body.clientWidth,
    }));
    expect(overflow, `${viewport.width}x${viewport.height}`).toEqual({ page: false, body: false });
    await expect(dashboardFrame(page).locator('.title')).toBeVisible();
    await expect(dashboardFrame(page).locator('.kpi').first()).toBeVisible();
  }

  expect(
    mocks.apiRequests.filter(request =>
      /add|update|delete|config_apply|dashboard_create|menu_create/i.test(request.path),
    ),
  ).toEqual([]);
});

test('低代码大屏展示兼容模式和模式漂移提示', async ({ page }) => {
  await installMocks(page, {
    parts: [previewPart({ mode: 'LOW_CODE_PAGE', previousMode: 'HTML_PAGE' })],
  });
  await openPage(page);

  const card = page.locator('.dashboard-preview');
  await expect(card).toBeVisible({ timeout: 15_000 });
  await expect(card).toContainText('低代码大屏');
  await expect(card).toContainText('模式变化：HTML_PAGE → LOW_CODE_PAGE');
  await expect(card).toContainText('_dashboard.json');
  await dashboardFrame(page);
});

test('校验失败或摘要缺失时展示诊断并禁止发布', async ({ page }) => {
  await installMocks(page, {
    parts: [
      previewPart({
        validationStatus: 'invalid',
        previewUrl: null,
        diagnostics: [
          {
            code: 'REPRESENTATIVE_QUERY_FAILED',
            path: 'blocks.trend.query',
            message: '代表性真实查询失败：字段不存在',
            severity: 'error',
          },
        ],
      }),
    ],
  });
  await openPage(page);

  const card = page.locator('.dashboard-preview');
  await expect(card).toBeVisible({ timeout: 15_000 });
  await expect(card).toContainText('待校验');
  await expect(card).toContainText('REPRESENTATIVE_QUERY_FAILED');
  await expect(card).toContainText('暂无可用的受控预览地址');
  await expect(card.getByRole('button', { name: '确认发布大屏' })).toHaveCount(0);
});

test('确认发布大屏提交工作流 approve 动作', async ({ page }) => {
  const mocks = await installMocks(page, { parts: [previewPart({ previewUrl: null })] });
  await openPage(page);

  const card = page.locator('.dashboard-preview');
  await expect(card).toBeVisible({ timeout: 15_000 });
  await card.getByRole('button', { name: '确认发布大屏' }).click();
  const dialog = page.locator('.el-message-box');
  await expect(dialog).toContainText('发布不会创建菜单');
  await dialog.getByRole('button', { name: '确认发布', exact: true }).click();

  await expect.poll(() => mocks.workflowActions.length).toBe(1);
  expect(mocks.workflowActions[0]).toMatchObject({
    chat_id: CHAT_ID,
    message_id: 'data-dashboard-message-1',
    part_id: 'data-dashboard-preview-part',
    workflow_id: WORKFLOW_ID,
    action: 'approve',
  });
});

test('方案修订、发布失败重试均提交工作流动作', async ({ page }) => {
  const mocks = await installMocks(page, { parts: [planPart(), retryPart()] });
  await openPage(page);

  const plan = page.locator('.visualization-decision-part').filter({ hasText: '确认数据大屏方案' });
  await expect(plan).toBeVisible({ timeout: 15_000 });
  await plan.getByRole('button', { name: '补充信息继续调整' }).click();
  await plan.locator('textarea').fill('趋势图改为按小时聚合，并增加成功率指标。');
  await plan.getByRole('button', { name: '继续调整', exact: true }).click();
  await expect.poll(() => mocks.workflowActions.length).toBe(1);
  expect(mocks.workflowActions[0]).toMatchObject({
    action: 'revise',
    revision: '趋势图改为按小时聚合，并增加成功率指标。',
  });

  const retry = page.locator('.visualization-decision-part').filter({ hasText: '发布被阻断' });
  await expect(retry).toBeVisible();
  await retry.getByRole('button', { name: '重试当前阶段' }).click();
  const dialog = page.locator('.el-message-box');
  await dialog.getByRole('button', { name: '确定', exact: true }).click();

  await expect.poll(() => mocks.workflowActions.length).toBe(2);
  expect(mocks.workflowActions[1]).toMatchObject({
    action: 'retry',
    workflow_id: WORKFLOW_ID,
  });
});
