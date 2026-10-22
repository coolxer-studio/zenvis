import { expect, test, type Page, type Route } from '@playwright/test'

const captureVisuals = process.env.DIH_RAG_VISUAL_CAPTURE === '1'

const ok = (data: unknown) => ({
  status: 0,
  code: 0,
  msg: 'success',
  data,
})

const conclusionMarkdown = [
  '## 检索结论',
  '',
  '知识库中找到了接入链路说明。',
].join('\n')

const fulfillJson = (route: Route, data: unknown) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(data),
})

type RagOutcome = 'succeeded' | 'empty' | 'failed'

const ragData = (outcome: RagOutcome) => {
  if (outcome === 'empty') {
    return {
      searchId: 'rag-empty',
      query: '不存在的接入方式',
      mode: 'ask',
      scopeType: 'all',
      knowledgeBaseIds: [1, 2],
      topK: 6,
      status: 'empty',
      reason: 'no_match',
      hitCount: 0,
      durationMillis: 31,
      startedAt: '2026-10-22T10:00:00Z',
      completedAt: '2026-10-22T10:00:00.031Z',
      results: [],
      error: null,
    }
  }
  if (outcome === 'failed') {
    return {
      searchId: 'rag-failed',
      query: '接入链路',
      mode: 'ask',
      scopeType: 'bound',
      knowledgeBaseIds: [7],
      topK: 6,
      status: 'failed',
      reason: 'retrieval_error',
      hitCount: 0,
      durationMillis: 17,
      startedAt: '2026-10-22T10:00:00Z',
      completedAt: '2026-10-22T10:00:00.017Z',
      results: [],
      error: 'redis unavailable',
    }
  }
  return {
    searchId: 'rag-1',
    query: '接入链路',
    mode: 'ask',
    scopeType: 'all',
    knowledgeBaseIds: [1, 2],
    topK: 6,
    status: 'succeeded',
    reason: 'ok',
    hitCount: 1,
    durationMillis: 43,
    startedAt: '2026-10-22T10:00:00Z',
    completedAt: '2026-10-22T10:00:00.043Z',
    results: [{
      documentId: 'doc-1',
      knowledgeBaseId: 1,
      knowledgeBaseName: '产品文档',
      datasetName: '接入指南',
      fileName: 'quick-start.md',
      source: 'kb_1',
      text: '接入链路使用 MCP 与 RAG 两类能力，调用结果会记录在对话中。',
    }],
    error: null,
  }
}

const ragPart = (outcome: RagOutcome) => {
  const data = ragData(outcome)
  return {
    id: data.searchId,
    type: 'rag-search',
    title: '知识库检索',
    content: outcome === 'succeeded'
      ? '知识库检索 · 1 个来源'
      : outcome === 'empty'
        ? '知识库检索 · 无匹配'
        : '知识库检索 · 失败',
    status: data.status,
    metadata: data,
  }
}

const installMocks = async (page: Page, outcome: RagOutcome, streamError?: string) => {
  const data = ragData(outcome)
  let sent = false
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
      await fulfillJson(route, ok({
        id: '301',
        session_id: 'e2e-rag',
        title: 'RAG 检索验收',
        type: 'ask',
        message_list: sent
          ? [{
              id: 'ai-rag-message',
              sender: 'ai',
              content: conclusionMarkdown,
              time: '2026-10-22 10:00:00',
              type: 'text',
              parts: [
                ragPart(outcome),
                { id: 'conclusion', type: 'markdown', content: conclusionMarkdown },
              ],
            }]
          : [],
        extra_data: '',
      }))
      return
    }
    if (path.endsWith('/dih/chat') && method === 'POST') {
      sent = true
      const events = streamError
        ? [
            JSON.stringify({ event: 'rag_search', data }),
            JSON.stringify({ event: 'error', message: streamError }),
          ]
        : [
            JSON.stringify({ event: 'rag_search', data }),
            JSON.stringify({ event: 'delta', content: conclusionMarkdown }),
            JSON.stringify({
              event: 'done',
              message: {
                id: 'ai-rag-message',
                sender: 'ai',
                content: conclusionMarkdown,
                time: '2026-10-22 10:00:00',
                type: 'text',
                parts: [
                  ragPart(outcome),
                  { id: 'conclusion', type: 'markdown', content: conclusionMarkdown },
                ],
              },
            }),
          ]
      await route.fulfill({
        contentType: 'application/x-ndjson',
        body: events.join('\n') + '\n',
      })
      return
    }

    await fulfillJson(route, ok({}))
  })
}

const sendQuestion = async (page: Page, question: string) => {
  await page.goto('/#/service/dih?type=ask&chatSessionId=e2e-rag&token=e2e-token')
  const input = page.getByPlaceholder('输入你的问题，帮你深度解答')
  await expect(input).toBeVisible()
  await input.fill(question)
  await page.getByRole('button', { name: '发送消息' }).click()
}

test('命中的 RAG 检索只渲染一张默认折叠的独立卡片', async ({ page }) => {
  await installMocks(page, 'succeeded')
  await sendQuestion(page, '请检索接入链路')

  const card = page.locator('.rag-search-part')
  await expect(card).toHaveCount(1)
  await expect(card).toHaveClass(/is-succeeded/)
  await expect(card).toContainText('知识库检索')
  await expect(card).toContainText('接入链路')
  await expect(card).toContainText('1 个来源')
  await expect(card).toContainText('43ms')
  await expect(page.locator('.tool-activity-part')).toHaveCount(0)
  await expect(page.locator('.message-content h2').filter({ hasText: '检索结论' })).toBeVisible()

  const header = card.locator('.rag-search-header')
  await expect(header).toHaveAttribute('aria-expanded', 'false')
  await expect(card.locator('.rag-search-result')).toHaveCount(0)

  await header.click()
  await expect(header).toHaveAttribute('aria-expanded', 'true')
  await expect(card).toContainText('全部知识库 · 2 个')
  await expect(card.locator('.rag-search-result')).toHaveCount(1)
  await expect(card.locator('.rag-search-result')).toContainText('quick-start.md')
  await expect(card.locator('.rag-search-result')).toContainText('产品文档')
  await expect(card.locator('.rag-search-result')).toContainText('接入链路使用 MCP 与 RAG')
  await expect(card.getByRole('button', { name: '复制检索查询' })).toBeVisible()
  await expect(card.getByRole('button', { name: '复制全部命中来源' })).toBeVisible()

  if (captureVisuals) {
    for (const viewport of [
      { width: 1440, height: 1000 },
      { width: 1024, height: 900 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await expect(card).toBeVisible()
      await page.evaluate(() => {
        const header = document.querySelector<HTMLButtonElement>('.rag-search-header')
        if (header?.getAttribute('aria-expanded') === 'true') header.click()
      })
      await expect(page.locator('.rag-search-header')).toHaveAttribute('aria-expanded', 'false')
      await page.screenshot({
        path: `test-results/dih-rag-visual-${viewport.width}-collapsed.png`,
        fullPage: false,
      })
      await page.evaluate(() => {
        const header = document.querySelector<HTMLButtonElement>('.rag-search-header')
        if (header?.getAttribute('aria-expanded') === 'false') header.click()
      })
      await expect(page.locator('.rag-search-header')).toHaveAttribute('aria-expanded', 'true')
      await page.screenshot({
        path: `test-results/dih-rag-visual-${viewport.width}-expanded.png`,
        fullPage: false,
      })
    }
  }
})

test('无匹配时展示空结果而不是伪造来源', async ({ page }) => {
  await installMocks(page, 'empty')
  await sendQuestion(page, '不存在的接入方式')

  const card = page.locator('.rag-search-part')
  await expect(card).toHaveCount(1)
  await expect(card).toHaveClass(/is-empty/)
  await expect(card).toContainText('无匹配')
  await expect(card).toContainText('0 个来源')

  await card.locator('.rag-search-header').click()
  await expect(card.locator('.rag-search-result')).toHaveCount(0)
  await expect(card.locator('.rag-search-empty')).toContainText('未命中相关知识')
})

test('检索失败时保留错误详情和最终正文', async ({ page }) => {
  await installMocks(page, 'failed')
  await sendQuestion(page, '请检索接入链路')

  const card = page.locator('.rag-search-part')
  await expect(card).toHaveCount(1)
  await expect(card).toHaveClass(/is-failed/)
  await expect(card).toContainText('失败')

  await card.locator('.rag-search-header').click()
  await expect(card).toContainText('绑定知识库 · 1 个')
  await expect(card.locator('.rag-search-empty')).toContainText('redis unavailable')
  await expect(page.locator('.message-content h2').filter({ hasText: '检索结论' })).toBeVisible()
})

test('RAG 检索后模型失败时仍展示错误正文', async ({ page }) => {
  const errorMessage = '模型服务拒绝了工具定义，请稍后重试。'
  await installMocks(page, 'succeeded', errorMessage)
  await sendQuestion(page, '生成一个测试报告')

  await expect(page.locator('.rag-search-part')).toHaveCount(1)
  await expect(page.locator('.message-content.markdown-body')).toContainText(errorMessage)
})

test('移动端 RAG 卡片不产生水平溢出', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await installMocks(page, 'succeeded')
  await sendQuestion(page, '请检索接入链路')

  const card = page.locator('.rag-search-part')
  await expect(card).toHaveCount(1)
  await expect(card.locator('.rag-search-header')).toContainText('接入链路')
  await card.locator('.rag-search-header').click()
  await expect(card.locator('.rag-search-result')).toHaveCount(1)

  expect(await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))).toEqual({
    scrollWidth: 390,
    clientWidth: 390,
  })
})
