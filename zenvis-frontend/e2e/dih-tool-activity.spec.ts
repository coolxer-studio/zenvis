import { expect, test, type Page, type Route } from '@playwright/test'

const captureVisuals = process.env.DIH_VISUAL_CAPTURE === '1'

const ok = (data: unknown) => ({
  status: 0,
  code: 0,
  msg: 'success',
  data,
})

const conclusionMarkdown = [
  '## 结论',
  '',
  '没有匹配记录。',
  '',
  '| 标题 | 状态 | 更新时间 |',
  '| --- | --- | --- |',
  '| 最后验证 Agent 能力 | draft | 2026-10-21 |',
].join('\n')

const wrappedToolResult = JSON.stringify([{
  text: JSON.stringify({ rows: [], total: 0 }),
}])

const fulfillJson = (route: Route, data: unknown) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(data),
})

const toolPart = {
  id: 'call-1',
  type: 'tool-call',
  title: 'MCP 工具调用',
  content: 'lab_note_search',
  status: 'succeeded',
  metadata: {
    callId: 'call-1',
    toolName: 'lab_note_search',
    arguments: '{"keyword":"接入","limit":20}',
    result: wrappedToolResult,
    durationMillis: 41,
  },
}

const failedToolPart = {
  ...toolPart,
  status: 'failed',
  metadata: {
    ...toolPart.metadata,
    result: '',
    error: '工具执行超时',
    durationMillis: 17,
  },
}

const installMocks = async (
  page: Page,
  legacy = false,
  toolOutcome: 'succeeded' | 'failed' = 'succeeded',
) => {
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()

    if (path.endsWith('/skills/chat-entries')) {
      await fulfillJson(route, ok([{
        skill_id: 'ask',
        chat_type: 'ask',
        agent_type: 'ask',
        label: '智能问答',
        icon: 'magic-stick',
      }]))
      return
    }
    if (path.endsWith('/model/list') || path.endsWith('/chat-session/list/pin')) {
      await fulfillJson(route, ok([]))
      return
    }
    if (path.endsWith('/chat-session/list')) {
      await fulfillJson(route, ok({ rows: [], total: 0 }))
      return
    }
    if (path.includes('/chat-session/') && path.endsWith('/session') && method === 'GET') {
      const legacyParts = [
        { type: 'markdown', content: '**MCP调用开始：** `lab_note_search`\n\n调用参数：' },
        { type: 'code', language: 'json', content: '{"keyword":"接入","limit":20}' },
        { type: 'markdown', content: '**MCP调用成功：** `lab_note_search`，耗时 41ms\n\n返回结果：' },
        { type: 'code', language: 'json', content: '{"rows":[],"total":0}' },
        {
          type: 'markdown',
          content: [
            '## 结论',
            '',
            '没有匹配记录。',
            '',
            '| 标题 | 状态 | 更新时间 |',
            '| --- | --- | --- |',
            '| 最后验证 Agent 能力 | draft | 2026-10-21 |',
          ].join('\n'),
        },
      ]
      await fulfillJson(route, ok({
        id: '201',
        session_id: legacy ? 'legacy-tool' : 'e2e-tool',
        title: '工具活动验收',
        type: 'ask',
        message_list: legacy
          ? [{
              id: 'legacy-ai',
              sender: 'ai',
              content: '## 结论\n\n没有匹配记录。',
              time: '2026-10-22 09:12:37',
              type: 'text',
              parts: legacyParts,
            }]
          : [],
        extra_data: '',
      }))
      return
    }
    if (path.endsWith('/dih/chat') && method === 'POST') {
      const toolEvents = toolOutcome === 'failed'
        ? [
            {
              event: 'tool_call',
              data: {
                callId: 'call-1',
                toolName: 'lab_note_search',
                status: 'running',
                arguments: '{"keyword":"接入","limit":20}',
              },
            },
            {
              event: 'tool_call',
              data: {
                callId: 'call-1',
                toolName: 'lab_note_search',
                status: 'failed',
                arguments: '{"keyword":"接入","limit":20}',
                error: '工具执行超时',
                durationMillis: 17,
              },
            },
          ]
        : [
            {
              event: 'tool_call',
              data: {
                callId: 'call-1',
                toolName: 'lab_note_search',
                status: 'running',
                arguments: '{"keyword":"接入","limit":20}',
              },
            },
            {
              event: 'tool_call',
              data: {
                callId: 'call-1',
                toolName: 'lab_note_search',
                status: 'succeeded',
                arguments: '{"keyword":"接入","limit":20}',
                result: wrappedToolResult,
                durationMillis: 41,
              },
            },
          ]
      await route.fulfill({
        contentType: 'application/x-ndjson',
        body: [
          ...toolEvents.map(JSON.stringify),
          JSON.stringify({
            event: 'delta',
            content: conclusionMarkdown,
          }),
          JSON.stringify({
            event: 'done',
            message: {
              id: 'ai-tool-message',
              sender: 'ai',
              content: conclusionMarkdown,
              time: '2026-10-22 09:12:37',
              type: 'text',
              parts: [
                toolOutcome === 'failed' ? failedToolPart : toolPart,
                { id: 'conclusion', type: 'markdown', content: conclusionMarkdown },
              ],
            },
          }),
        ].join('\n') + '\n',
      })
      return
    }

    await fulfillJson(route, ok({}))
  })
}

test('结构化工具事件只渲染一张默认折叠的工具卡', async ({ page }) => {
  await installMocks(page)
  await page.goto('/#/service/dih?type=ask&chatSessionId=e2e-tool&token=e2e-token')
  await expect(page.getByPlaceholder('输入你的问题，帮你深度解答')).toBeVisible()

  await page.getByPlaceholder('输入你的问题，帮你深度解答').fill('请检索接入链路')
  await page.getByRole('button', { name: '发送消息' }).click()

  const activity = page.locator('.tool-activity-part')
  await expect(activity).toHaveCount(1)
  await expect(activity).toContainText('lab_note_search')
  await expect(activity).toContainText('已完成')
  await expect(activity).toContainText('41ms')
  await expect(page.locator('.message-content h2').filter({ hasText: '结论' })).toBeVisible()
  await expect(page.locator('.message-content table')).toBeVisible()

  const header = activity.locator('.tool-activity-header')
  await expect(header).toHaveAttribute('aria-expanded', 'false')
  await expect(activity.locator('.tool-detail-code')).toHaveCount(0)

  await header.click()
  await expect(header).toHaveAttribute('aria-expanded', 'true')
  await expect(activity.locator('.tool-detail')).toHaveCount(2)
  await expect(activity.locator('.tool-detail-code').first()).toContainText('"keyword": "接入"')
  await expect(activity.locator('.tool-detail-code').nth(1)).toContainText('"total": 0')
  await expect(activity.locator('.tool-detail-code').nth(1)).not.toContainText('"text"')
  await expect(activity.locator('.tool-detail-code').nth(1)).not.toContainText('\\"rows\\"')
})

test('失败工具事件只渲染一张折叠卡片并展示错误详情', async ({ page }) => {
  await installMocks(page, false, 'failed')
  await page.goto('/#/service/dih?type=ask&chatSessionId=e2e-tool&token=e2e-token')
  await expect(page.getByPlaceholder('输入你的问题，帮你深度解答')).toBeVisible()

  await page.getByPlaceholder('输入你的问题，帮你深度解答').fill('请检索接入链路')
  await page.getByRole('button', { name: '发送消息' }).click()

  const activity = page.locator('.tool-activity-part')
  await expect(activity).toHaveCount(1)
  await expect(activity).toHaveClass(/is-failed/)
  await expect(activity).toContainText('lab_note_search')
  await expect(activity).toContainText('有调用失败')
  await expect(activity).toContainText('17ms')
  await expect(activity.locator('.tool-detail-code')).toHaveCount(0)

  await activity.locator('.tool-activity-header').click()
  await expect(activity.locator('.tool-detail-code').first()).toContainText('"keyword": "接入"')
  await expect(activity.locator('.tool-detail-code').nth(1)).toContainText('工具执行超时')
  await expect(page.locator('.message-content h2').filter({ hasText: '结论' })).toBeVisible()
})

test('历史 MCP Markdown 日志在展示层转换为单张工具卡', async ({ page }) => {
  await installMocks(page, true)
  await page.goto('/#/service/dih?type=ask&chatSessionId=legacy-tool&token=e2e-token')

  const activity = page.locator('.tool-activity-part')
  await expect(activity).toHaveCount(1)
  await expect(activity).toContainText('lab_note_search')
  await expect(activity.locator('.tool-detail-code')).toHaveCount(0)
  await expect(page.locator('.message-content h2').filter({ hasText: '结论' })).toBeVisible()

  await activity.locator('.tool-activity-header').click()
  await expect(activity.locator('.tool-detail-code').first()).toContainText('"keyword": "接入"')
  await expect(activity.locator('.tool-detail-code').nth(1)).toContainText('"total": 0')

  if (captureVisuals) {
    for (const viewport of [
      { width: 1440, height: 1000 },
      { width: 1024, height: 900 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await expect(activity).toBeVisible()
      await expect(page.locator('.message-content h2').filter({ hasText: '结论' })).toBeVisible()
      const header = activity.locator('.tool-activity-header')
      if (await header.getAttribute('aria-expanded') === 'true') {
        await header.click()
      }
      await expect(header).toHaveAttribute('aria-expanded', 'false')
      await page.screenshot({
        path: `test-results/dih-visual-${viewport.width}-collapsed.png`,
        fullPage: false,
      })
      await header.click()
      await expect(header).toHaveAttribute('aria-expanded', 'true')
      await page.screenshot({
        path: `test-results/dih-visual-${viewport.width}-expanded.png`,
        fullPage: false,
      })
    }
  }
})

test('移动端工具卡默认折叠且不产生水平溢出', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await installMocks(page, true)
  await page.goto('/#/service/dih?type=ask&chatSessionId=legacy-tool&token=e2e-token')

  const activity = page.locator('.tool-activity-part')
  await expect(activity).toHaveCount(1)
  await expect(activity.locator('.tool-detail-code')).toHaveCount(0)
  await expect(page.locator('.message-content table')).toBeVisible()

  expect(await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))).toEqual({
    scrollWidth: 390,
    clientWidth: 390,
  })

  await activity.locator('.tool-activity-header').click()
  await expect(activity.locator('.tool-detail-code').first()).toBeVisible()
})
