import { readFile } from 'node:fs/promises';
import { expect, test, type Page, type Route } from '@playwright/test';

test.use({ permissions: ['clipboard-read', 'clipboard-write'] });

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

const scriptedHtml = (id: string, label: string) =>
  `<!doctype html><html><body><p id="${id}">pending</p><script>document.getElementById('${id}').textContent='${label}';document.body.dataset.ready='yes';</script></body></html>`;

const markdownHtml = scriptedHtml('markdown-status', 'Markdown 脚本已执行');
const codeHtml = scriptedHtml('code-status', '代码脚本已执行');
const reportHtml = scriptedHtml('report-status', '报表脚本已执行');
const configHtml = scriptedHtml('config-status', '配置脚本已执行');

type Scenario = 'valid' | 'empty';

const messageParts = (scenario: Scenario) => {
  if (scenario === 'empty') {
    return [
      {
        id: 'empty-html',
        type: 'code',
        language: 'HTML',
        content: '',
      },
      {
        id: 'plain-code',
        type: 'code',
        language: 'text',
        content: ['```html', '<p>not a preview</p>', '```'].join('\n'),
      },
    ];
  }

  return [
    {
      id: 'markdown-html',
      type: 'markdown',
      content: `## HTML 页面\n\n\`\`\`html\n${markdownHtml}\n\`\`\``,
    },
    {
      id: 'code-html',
      type: 'code',
      language: 'Html',
      content: codeHtml,
    },
    {
      id: 'report-html',
      type: 'report-document',
      title: 'HTML 报表',
      content: reportHtml,
      metadata: {
        format: 'html',
        title: 'HTML 报表',
      },
    },
    {
      id: 'config-html',
      type: 'config',
      title: '静态页面',
      content: configHtml,
      metadata: {
        configKind: 'html-page',
        defaultFileName: 'demo-page.html',
      },
    },
  ];
};

const installMocks = async (page: Page, scenario: Scenario = 'valid') => {
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();

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
    if (path.endsWith('/chat-session/e2e-html/session') && method === 'GET') {
      await fulfillJson(
        route,
        ok({
          id: '401',
          session_id: 'e2e-html',
          title: 'HTML 预览验收',
          type: 'ask',
          message_list: [
            {
              id: 'html-message',
              sender: 'ai',
              content: '',
              time: '2026-10-27 10:00:00',
              type: 'text',
              parts: messageParts(scenario),
            },
          ],
          extra_data: '',
        }),
      );
      return;
    }

    await fulfillJson(route, ok({}));
  });
};

const openChat = async (page: Page, scenario: Scenario = 'valid') => {
  await installMocks(page, scenario);
  await page.goto('/#/service/dih?type=ask&chatSessionId=e2e-html&token=e2e-token');
  await expect(page.getByPlaceholder('输入你的问题，帮你深度解答')).toBeVisible();
};

const expectSandboxedFrame = async (frame: ReturnType<Page['frameLocator']>) => {
  await expect(frame.locator('body')).toBeVisible();
};

test('预览 Markdown、代码、报表和配置中的 HTML，并支持脚本、复制及下载', async ({ page }) => {
  await openChat(page);

  const cards = page.locator('.html-preview-card');
  await expect(cards).toHaveCount(2);

  const summaryCard = cards.first();
  const summaryFrame = summaryCard.frameLocator('iframe');
  await expect(summaryFrame.locator('#markdown-status')).toHaveText('Markdown 脚本已执行');
  await expectSandboxedFrame(summaryFrame);
  expect(await summaryCard.locator('iframe').getAttribute('sandbox')).toBe(
    'allow-scripts allow-forms allow-modals allow-popups allow-downloads',
  );
  expect(await summaryCard.locator('iframe').getAttribute('referrerpolicy')).toBe('no-referrer');

  const codeCard = cards.nth(1);
  await expect(codeCard.frameLocator('iframe').locator('#code-status')).toHaveText(
    '代码脚本已执行',
  );

  const reportPart = page.locator('.report-document-part');
  await expect(reportPart.locator('iframe')).toBeVisible();
  await expect(reportPart.frameLocator('iframe').locator('#report-status')).toHaveText(
    '报表脚本已执行',
  );
  expect(await reportPart.locator('iframe').getAttribute('sandbox')).not.toContain(
    'allow-same-origin',
  );
  expect(await reportPart.locator('iframe').getAttribute('referrerpolicy')).toBe('no-referrer');

  const configPart = page.locator('.config-part').filter({ hasText: '静态页面' });
  await configPart.getByRole('button', { name: '预览最终效果' }).click();
  await expect(configPart.locator('iframe')).toBeVisible();
  await expect(configPart.frameLocator('iframe').locator('#config-status')).toHaveText(
    '配置脚本已执行',
  );
  expect(await configPart.locator('iframe').getAttribute('sandbox')).not.toContain(
    'allow-same-origin',
  );

  await summaryCard.getByRole('button', { name: '查看源码' }).click();
  await expect(summaryCard.locator('.html-preview-source')).toContainText('markdown-status');
  await summaryCard.getByRole('button', { name: '复制 HTML 源码' }).click();
  await expect
    .poll(() => page.evaluate(() => navigator.clipboard.readText()))
    .toContain('markdown-status');
  await summaryCard.getByRole('button', { name: '查看页面' }).click();
  await expect(summaryCard.locator('iframe')).toBeVisible();

  const summaryDownloadPromise = page.waitForEvent('download');
  await summaryCard.getByRole('button', { name: '下载 HTML' }).click();
  const summaryDownload = await summaryDownloadPromise;
  expect(summaryDownload.suggestedFilename()).toMatch(/^html-preview-\d+\.html$/);
  const summaryPath = await summaryDownload.path();
  expect(summaryPath).not.toBeNull();
  expect(await readFile(summaryPath!, 'utf8')).toContain('markdown-status');

  const reportDownloadPromise = page.waitForEvent('download');
  await reportPart.getByRole('button', { name: '下载 HTML' }).click();
  const reportDownload = await reportDownloadPromise;
  expect(reportDownload.suggestedFilename()).toBe('HTML-报表.html');
  const reportPath = await reportDownload.path();
  expect(reportPath).not.toBeNull();
  expect(await readFile(reportPath!, 'utf8')).toContain('report-status');

  const configDownloadPromise = page.waitForEvent('download');
  await configPart.getByRole('button', { name: '下载 HTML' }).click();
  const configDownload = await configDownloadPromise;
  expect(configDownload.suggestedFilename()).toBe('demo-page.html');
  const configPath = await configDownload.path();
  expect(configPath).not.toBeNull();
  expect(await readFile(configPath!, 'utf8')).toContain('config-status');

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(summaryFrame.locator('body')).toBeVisible();
  expect(
    await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    })),
  ).toEqual({
    scrollWidth: 390,
    clientWidth: 390,
  });
});

test('空 HTML 显示错误并禁用下载，普通代码块保持源码展示', async ({ page }) => {
  await openChat(page, 'empty');

  const card = page.locator('.html-preview-card');
  await expect(card).toHaveCount(1);
  await expect(card.locator('.html-preview-error')).toContainText('HTML 页面为空');
  await expect(card.getByRole('button', { name: '下载 HTML' })).toBeDisabled();
  await expect(page.locator('.code-part')).toContainText('not a preview');
});
