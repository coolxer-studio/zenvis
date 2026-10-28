import { expect, test, type Page, type Route } from '@playwright/test';

const CHART_TYPE = 'agent_visualization_chart';
const APP_TYPE = 'agent_visualization_app';
const DASHBOARD_TYPE = 'agent_visualization_dashboard';
const METADATA_TYPE = 'agent_metadata_config';

const ok = (data: unknown) => ({
  status: 0,
  code: 0,
  msg: 'success',
  data,
});

const fulfillJson = (route: Route, data: unknown) =>
  route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });

const chatEntries = [
  {
    skill_id: 'visualization-chart-agent',
    chat_type: CHART_TYPE,
    agent_type: CHART_TYPE,
    label: '临时图表',
    icon: 'data-analysis',
  },
  {
    skill_id: 'visualization-app-agent',
    chat_type: APP_TYPE,
    agent_type: APP_TYPE,
    label: '可视化应用',
    icon: 'monitor',
  },
  {
    skill_id: 'visualization-dashboard-agent',
    chat_type: DASHBOARD_TYPE,
    agent_type: DASHBOARD_TYPE,
    label: '数据大屏',
    icon: 'operation',
  },
  {
    skill_id: 'metadata-config-agent',
    chat_type: METADATA_TYPE,
    agent_type: METADATA_TYPE,
    label: '元数据配置',
    icon: 'operation',
  },
  {
    skill_id: 'ask',
    chat_type: 'ask',
    agent_type: 'ask',
    label: '智能问答',
    icon: 'magic-stick',
  },
];

const installMocks = async (
  page: Page,
  options: {
    extraData?: string;
    messagesByType?: Record<string, Array<Record<string, unknown>>>;
  } = {},
) => {
  const chatRequests: string[] = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path.endsWith('/skills/chat-entries')) {
      await fulfillJson(route, ok(chatEntries));
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
      const type = url.searchParams.get('type') || CHART_TYPE;
      const sessionId = decodeURIComponent(path.split('/').at(-2) || 'visualization-session');
      await fulfillJson(
        route,
        ok({
          id: `201-${type}`,
          session_id: sessionId,
          title: '可视化智能体验收',
          type,
          message_list: options.messagesByType?.[type] || [],
          extra_data: options.extraData || '',
        }),
      );
      return;
    }
    if (path.endsWith('/chat/runs') && method === 'GET') {
      await fulfillJson(route, ok({ rows: [], total: 0 }));
      return;
    }
    if (path.endsWith('/dih/chat') && method === 'POST') {
      chatRequests.push(request.url());
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body: '',
      });
      return;
    }
    await fulfillJson(route, ok({}));
  });
  return { chatRequests };
};

const hashQuery = (page: Page, key: string) =>
  page.evaluate(name => {
    const query = window.location.hash.split('?')[1] || '';
    return new URLSearchParams(query).get(name);
  }, key);

test('三个可视化入口独立展示本领域结果且旧入口不再生成结果面板', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  const extraData = JSON.stringify({
    visualizationChart: {
      chartLibrary: [
        {
          id: 'chart-1',
          name: '临时图表快照',
          status: 'temporary',
        },
      ],
    },
    visualizationApp: {
      configs: [
        {
          id: 'app-config-1',
          name: '应用配置快照',
          configType: 'user-event-page',
          status: 'applied',
        },
      ],
      menus: [
        {
          id: 'app-menu-1',
          name: '应用菜单快照',
          menuId: '21',
          status: 'created',
        },
      ],
    },
    visualizationDashboard: {
      configs: [
        {
          id: 'dashboard-config-1',
          name: '大屏配置快照',
          configType: 'user-event-dashboard',
          status: 'applied',
        },
      ],
      dashboards: [
        {
          id: 'dashboard-1',
          name: '数据看板快照',
          code: 'user-event-dashboard',
          status: 'created',
        },
      ],
      menus: [
        {
          id: 'dashboard-menu-1',
          name: '看板菜单快照',
          menuId: '22',
          status: 'created',
        },
      ],
    },
  });
  await installMocks(page, { extraData });

  await page.goto(`/#/service/dih?type=${CHART_TYPE}&chatSessionId=e2e-chart&token=e2e-token`);
  await expect(page.locator('.suggestions')).toBeVisible({ timeout: 15_000 });
  const entries = page.locator('.suggestions .suggestion-btn');
  await expect(entries.filter({ hasText: '临时图表' })).toHaveCount(1);
  await expect(entries.filter({ hasText: '可视化应用' })).toHaveCount(1);
  await expect(entries.filter({ hasText: '数据大屏' })).toHaveCount(1);
  await expect(entries.filter({ hasText: /^数据可视化$/ })).toHaveCount(0);
  await expect(page.locator('.workspace-result')).toContainText('图表库');
  await expect(page.locator('.workspace-result')).toContainText('临时图表快照');
  await expect(page.locator('.workspace-result')).not.toContainText('应用配置快照');
  await expect(page.locator('.workspace-result')).not.toContainText('数据看板快照');

  await page.goto(`/#/service/dih?type=${APP_TYPE}&chatSessionId=e2e-app&token=e2e-token`);
  await expect(page.locator('.workspace-result')).toContainText('可视化应用结果');
  await expect(page.locator('.workspace-result')).toContainText('应用配置快照');
  await expect(page.locator('.workspace-result')).toContainText('应用菜单快照');
  await expect(page.locator('.workspace-result')).not.toContainText('临时图表快照');
  await expect(page.locator('.workspace-result')).not.toContainText('数据看板快照');

  await page.goto(
    `/#/service/dih?type=${DASHBOARD_TYPE}&chatSessionId=e2e-dashboard&token=e2e-token`,
  );
  await expect(page.locator('.workspace-result')).toContainText('数据大屏结果');
  await expect(page.locator('.workspace-result')).toContainText('大屏配置快照');
  await expect(page.locator('.workspace-result')).toContainText('数据看板快照');
  await expect(page.locator('.workspace-result')).toContainText('看板菜单快照');
  await expect(page.locator('.workspace-result')).not.toContainText('临时图表快照');
  await expect(page.locator('.workspace-result')).not.toContainText('应用配置快照');

  await page.goto(
    `/#/service/dih?type=agent_data_visualization&chatSessionId=e2e-legacy&token=e2e-token`,
  );
  await expect(page.locator('.workspace-result')).toHaveCount(0);
  await expect(
    page.locator('.suggestions .suggestion-btn').filter({ hasText: /^数据可视化$/ }),
  ).toHaveCount(0);
});

test('三个可视化智能体的缺失 Meta 深链只预填提示词而不自动发送', async ({ page }) => {
  const cases = [
    [CHART_TYPE, 'visualization-chart-decision', '实体 user_event 缺少字段 event_type'],
    [APP_TYPE, 'visualization-app-decision', '实体 user_event 缺少字段 server_time'],
    [DASHBOARD_TYPE, 'visualization-dashboard-decision', '实体 user_event 缺少字段 tags'],
  ] as const;
  const messagesByType = Object.fromEntries(
    cases.map(([type, partType, requirement], index) => [
      type,
      [
        {
          id: `message-missing-meta-${index}`,
          sender: 'ai',
          content: '缺少已发布 Meta，可视化流程已停止。',
          time: '2026-10-28 10:00:00',
          type: 'text',
          parts: [
            {
              id: `missing-meta-part-${index}`,
              type: partType,
              status: 'pending',
              title: '缺少已发布 Meta',
              content: '请先前往元数据配置智能体完成并发布 Meta。',
              metadata: {
                kind: 'missing_meta',
                handoff: {
                  targetAgentType: METADATA_TYPE,
                  label: '前往元数据配置',
                  prompt: `请为 user_event.json 创建并发布 Meta 配置：${requirement}。`,
                },
                actions: [],
              },
            },
          ],
        },
      ],
    ]),
  ) as Record<string, Array<Record<string, unknown>>>;
  const { chatRequests } = await installMocks(page, { messagesByType });

  for (const [index, [type, , requirement]] of cases.entries()) {
    await page.goto(
      `/#/service/dih?type=${type}&chatSessionId=e2e-missing-${index}&token=e2e-token`,
    );
    await page.getByRole('button', { name: '前往元数据配置' }).click();

    await expect.poll(() => hashQuery(page, 'type')).toBe(METADATA_TYPE);
    await expect.poll(() => hashQuery(page, 'createSession')).toBe('1');
    await expect.poll(() => hashQuery(page, 'msgRef')).toBeTruthy();
    await expect
      .poll(() => page.locator('.input-container textarea').inputValue())
      .toBe(`请为 user_event.json 创建并发布 Meta 配置：${requirement}。`);
  }

  await page.waitForTimeout(300);
  expect(chatRequests).toHaveLength(0);
});
