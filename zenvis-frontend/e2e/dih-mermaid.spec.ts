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

const architectureDiagram = [
  'flowchart TD',
  '  S1[实时指标/会话数据] --> S2[规则命中或<br/>基线偏离]',
  '  S2 --> S3[告警生成<br/>附证据链]',
  '  S3 --> S4[关联聚合与去重<br/>生成事件]',
].join('\n');

const reportDiagram = ['flowchart LR', '  A[采集] --> B[分析]', '  B --> C[告警]'].join('\n');

const invalidDiagram = ['flowchart TD', 'A[开始] -->'].join('\n');

type Scenario = 'valid' | 'invalid';

const messageParts = (scenario: Scenario) => {
  if (scenario === 'invalid') {
    return [
      {
        id: 'invalid-diagram',
        type: 'code',
        language: 'mermaid',
        content: invalidDiagram,
      },
    ];
  }

  return [
    {
      id: 'markdown-diagram',
      type: 'markdown',
      content: `## 总体架构\n\n下面给出可实施方案。\n\n\`\`\`mermaid\n${architectureDiagram}\n\`\`\``,
    },
    {
      id: 'code-diagram',
      type: 'code',
      language: 'Mermaid',
      content: reportDiagram,
    },
    {
      id: 'report-diagram',
      type: 'report-document',
      title: '报表预览',
      content: `## 报表流程图\n\n\`\`\`mermaid\n${reportDiagram}\n\`\`\``,
      metadata: {
        format: 'markdown',
        title: '报表预览',
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
    if (path.endsWith('/chat-session/e2e-mermaid/session') && method === 'GET') {
      await fulfillJson(
        route,
        ok({
          id: '301',
          session_id: 'e2e-mermaid',
          title: 'Mermaid 图表验收',
          type: 'ask',
          message_list: [
            {
              id: 'mermaid-message',
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
  await page.goto('/#/service/dih?type=ask&chatSessionId=e2e-mermaid&token=e2e-token');
  await expect(page.getByPlaceholder('输入你的问题，帮你深度解答')).toBeVisible();
};

test('渲染 Markdown、代码块和报表预览中的 Mermaid，并支持复制及 PNG 下载', async ({ page }) => {
  await openChat(page);

  const cards = page.locator('.mermaid-card');
  await expect(cards).toHaveCount(3);
  await expect(page.locator('.mermaid-diagram-canvas svg')).toHaveCount(3);
  await expect(page.locator('.report-document-preview .mermaid-diagram-canvas svg')).toBeVisible();

  const firstCard = cards.first();
  const firstDiagram = firstCard.locator('.mermaid-diagram-canvas svg');
  await expect(firstDiagram).toBeVisible();
  await expect(firstDiagram).toContainText('规则命中或');

  await firstCard.getByRole('button', { name: '查看源码' }).click();
  await expect(firstCard.locator('.mermaid-source')).toContainText('flowchart TD');
  await firstCard.getByRole('button', { name: '复制 Mermaid 源码' }).click();
  await expect
    .poll(() => page.evaluate(() => navigator.clipboard.readText()))
    .toContain('flowchart TD');

  await firstCard.getByRole('button', { name: '查看图表' }).click();
  await expect(firstDiagram).toBeVisible();

  const downloadPromise = page.waitForEvent('download');
  await firstCard.getByRole('button', { name: '下载 PNG' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/^mermaid-diagram-\d+\.png$/);
  const downloadPath = await download.path();
  expect(downloadPath).not.toBeNull();
  const png = await readFile(downloadPath!);
  expect([...png.subarray(0, 8)]).toEqual([137, 80, 78, 71, 13, 10, 26, 10]);

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(firstDiagram).toBeVisible();
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

test('非法 Mermaid 源码展示错误和源码，并禁用图片下载', async ({ page }) => {
  await openChat(page, 'invalid');

  const card = page.locator('.mermaid-card');
  await expect(card.locator('.mermaid-error')).toBeVisible();
  await expect(card.locator('.mermaid-error')).toContainText('图表渲染失败');
  await expect(card.locator('.mermaid-error-source')).toContainText('A[开始] -->');
  await expect(card.getByRole('button', { name: '下载 PNG' })).toBeDisabled();
});
