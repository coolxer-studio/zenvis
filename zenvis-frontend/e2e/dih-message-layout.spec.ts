import { expect, test, type Page, type Route } from '@playwright/test';

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

const session = {
  id: '401',
  session_id: 'e2e-message-layout',
  title: '消息布局验收',
  type: 'ask',
  extra_data: '',
  message_list: [
    {
      id: 'user-message',
      sender: 'user',
      content: '请分析最近一周的转化率，并给出下一步优化建议。',
      time: '2026-10-27 10:00:00',
    },
    {
      id: 'ai-message',
      sender: 'ai',
      content: '',
      time: '2026-10-27 10:00:05',
      parts: [
        {
          id: 'answer',
          type: 'markdown',
          content: '最近一周整体转化率为 **4.8%**。建议先优化信息流落地页，再复盘搜索渠道。',
        },
      ],
    },
  ],
};

const installMocks = async (page: Page) => {
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;

    if (path.endsWith('/skills/chat-entries')) {
      await fulfillJson(
        route,
        ok([
          {
            skill_id: 'ask',
            chat_type: 'ask',
            agent_type: 'ask',
            label: '智能问答',
            icon: 'magic-stick',
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
    if (path.endsWith('/chat-session/e2e-message-layout/session') && request.method() === 'GET') {
      await fulfillJson(route, ok(session));
      return;
    }

    await fulfillJson(route, ok({}));
  });
};

test('用户消息右对齐，助手消息保留整列宽度且操作底栏按需出现', async ({ page }) => {
  await installMocks(page);
  await page.goto('/#/service/dih?type=ask&chatSessionId=e2e-message-layout&token=e2e-token');
  await expect(page.getByPlaceholder('输入你的问题，帮你深度解答')).toBeVisible();

  const userFooter = page.locator('.user-message-footer').first();
  const userActions = userFooter.locator('.message-actions');
  await expect(page.locator('.user-message').first()).toBeVisible();

  const layout = () =>
    page.evaluate(() => {
      const list = document.querySelector('.message-list');
      const user = document.querySelector('.user-message');
      const footer = document.querySelector('.user-message-footer');
      const ai = document.querySelector('.ai-message');
      if (!list || !user || !footer || !ai) return null;
      const rect = (element: Element) => {
        const box = element.getBoundingClientRect();
        return { left: box.left, right: box.right, width: box.width };
      };
      return { list: rect(list), user: rect(user), footer: rect(footer), ai: rect(ai) };
    });

  await expect
    .poll(async () => {
      const current = await layout();
      if (!current) return false;
      return (
        Math.abs(current.user.right - current.list.right) < 2 &&
        Math.abs(current.footer.right - current.list.right) < 2 &&
        current.user.left > current.list.left + current.list.width / 2 &&
        current.ai.width > current.list.width * 0.9
      );
    })
    .toBe(true);

  await expect(userActions).toHaveCSS('opacity', '0');
  await page.locator('.user-message-container').first().hover();
  await expect(userActions).toHaveCSS('opacity', '1');

  await page.setViewportSize({ width: 390, height: 844 });
  await expect
    .poll(async () => {
      const current = await layout();
      if (!current) return false;
      return (
        Math.abs(current.user.right - current.list.right) < 2 &&
        Math.abs(current.footer.right - current.list.right) < 2
      );
    })
    .toBe(true);
});
