import { expect, test, type Page, type Route } from '@playwright/test'

const ok = (data: unknown) => ({
  status: 0,
  code: 0,
  msg: 'success',
  data,
})

const fulfillJson = (route: Route, data: unknown) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(data),
})

type AttachmentStatus = 'pending' | 'processing' | 'ready' | 'partial' | 'failed'

type AttachmentMockOptions = {
  uploadStatus: AttachmentStatus
  statusSequence: AttachmentStatus[]
  warning?: string
}

const attachmentData = (
  fileId: string,
  fileName: string,
  status: AttachmentStatus,
  warning?: string,
) => {
  const csv = fileName.endsWith('.csv')
  const legacyOffice = /\.(doc|xls|ppt)$/i.test(fileName)
  return {
    file_id: fileId,
    file_name: fileName,
    file_size: 2_048,
    content_type: csv ? 'text/csv' : legacyOffice ? 'application/octet-stream' : 'application/pdf',
    kind: csv ? 'table' : legacyOffice ? 'file' : 'document',
    parse_status: status,
    parse_progress: status === 'processing' ? 45 : status === 'ready' ? 100 : 0,
    parse_message: warning || '',
    warning: warning || '',
    page_count: csv || legacyOffice ? 0 : 12,
    sheet_count: csv ? 1 : 0,
    row_count: csv ? 320 : 0,
    chunk_count: status === 'ready' ? 18 : 0,
    truncated: status === 'partial',
  }
}

const installMocks = async (page: Page, options: AttachmentMockOptions) => {
  const state = {
    uploadCalls: 0,
    statusCalls: 0,
    reparseCalls: 0,
    deleteCalls: 0,
    downloadCalls: 0,
    chatRequests: [] as Record<string, unknown>[],
  }
  let currentFileId = 'file-annual-report'
  let currentFileName = 'annual-report.pdf'

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
        id: '401',
        session_id: 'e2e-attachment',
        title: '附件验收',
        type: 'ask',
        message_list: [],
        extra_data: '',
      }))
      return
    }
    if (path.endsWith('/dih/upload') && method === 'POST') {
      state.uploadCalls++
      const body = request.postDataBuffer()
      if (body?.includes(Buffer.from('partial.csv'))) {
        currentFileId = 'file-partial'
        currentFileName = 'partial.csv'
      } else if (body?.includes(Buffer.from('legacy.doc'))) {
        currentFileId = 'file-legacy'
        currentFileName = 'legacy.doc'
      } else {
        currentFileId = 'file-annual-report'
        currentFileName = 'annual-report.pdf'
      }
      await new Promise(resolve => setTimeout(resolve, 500))
      await fulfillJson(route, ok(attachmentData(
        currentFileId,
        currentFileName,
        options.uploadStatus,
        options.warning,
      )))
      return
    }
    if (path.endsWith('/dih/upload/file-partial/download') && method === 'GET') {
      state.downloadCalls++
      await route.fulfill({
        status: 200,
        contentType: 'text/csv',
        headers: {
          'Content-Disposition': 'attachment; filename="partial.csv"',
        },
        body: 'quarter,revenue\nQ1,120\n',
      })
      return
    }
    if (path.endsWith('/status') && path.includes('/dih/upload/') && method === 'GET') {
      state.statusCalls++
      const index = Math.min(state.statusCalls - 1, options.statusSequence.length - 1)
      const status = options.statusSequence[Math.max(index, 0)]
      await new Promise(resolve => setTimeout(resolve, 180))
      await fulfillJson(route, ok(attachmentData(
        currentFileId,
        currentFileName,
        status,
        options.warning,
      )))
      return
    }
    if (path.endsWith('/reparse') && method === 'POST') {
      state.reparseCalls++
      state.statusCalls = Math.max(options.statusSequence.length - 1, 0)
      await fulfillJson(route, ok(attachmentData(
        currentFileId,
        currentFileName,
        'processing',
        options.warning,
      )))
      return
    }
    if (path.includes('/dih/upload/') && method === 'DELETE') {
      state.deleteCalls++
      await fulfillJson(route, ok('deleted'))
      return
    }
    if (path.endsWith('/dih/chat') && method === 'POST') {
      state.chatRequests.push(JSON.parse(request.postData() || '{}'))
      const answer = '附件已解析，季度收入保持增长。'
      await route.fulfill({
        contentType: 'application/x-ndjson',
        body: [
          JSON.stringify({ event: 'delta', content: answer }),
          JSON.stringify({
            event: 'done',
            message: {
              id: 'ai-attachment-message',
              sender: 'ai',
              content: answer,
              time: '2026-10-28 10:00:00',
              type: 'text',
            },
          }),
        ].join('\n') + '\n',
      })
      return
    }

    await fulfillJson(route, ok({}))
  })

  return state
}

const chooseFile = async (page: Page, name: string, mimeType: string, content: string) => {
  const chooserPromise = page.waitForEvent('filechooser')
  await page.getByRole('button', { name: '上传附件' }).click()
  const chooser = await chooserPromise
  await chooser.setFiles({
    name,
    mimeType,
    buffer: Buffer.from(content),
  })
}

test('解析完成前禁用发送，完成后携带附件发起长文档追问', async ({ page }) => {
  const state = await installMocks(page, {
    uploadStatus: 'pending',
    statusSequence: ['processing', 'ready'],
  })
  await page.goto('/#/service/dih?type=ask&chatSessionId=e2e-attachment&token=e2e-token')

  const input = page.getByPlaceholder('输入你的问题，帮你深度解答')
  const send = page.getByRole('button', { name: '发送消息' })
  await expect(input).toBeVisible()

  await chooseFile(page, 'annual-report.pdf', 'application/pdf', '%PDF-1.7 attachment fixture')
  await expect(page.getByText(/正在上传/)).toBeVisible()

  const pending = page.locator('.pending-attachment').filter({ hasText: 'annual-report.pdf' })
  await expect(pending).toContainText('解析中 45%')
  await expect(send).toBeDisabled()

  await input.fill('请结合第 3 页总结收入趋势')
  await expect(send).toBeDisabled()
  await expect(pending).toContainText('解析完成', { timeout: 6_000 })
  await expect(send).toBeEnabled()
  await send.click()

  await expect(page.locator('.message-content').filter({ hasText: '季度收入保持增长' })).toBeVisible()
  await expect.poll(() => state.chatRequests.length).toBe(1)
  expect(state.chatRequests[0].response_format).toBe('events')
  expect(state.chatRequests[0].attachments).toEqual([
    expect.objectContaining({
      file_id: 'file-annual-report',
      parse_status: 'ready',
      page_count: 12,
    }),
  ])
  expect(state.statusCalls).toBeGreaterThanOrEqual(2)
})

test('解析失败可重试，完成后删除会清理服务端附件', async ({ page }) => {
  const state = await installMocks(page, {
    uploadStatus: 'pending',
    statusSequence: ['failed', 'ready'],
  })
  await page.goto('/#/service/dih?type=ask&chatSessionId=e2e-attachment&token=e2e-token')

  await chooseFile(page, 'annual-report.pdf', 'application/pdf', '%PDF-1.7 attachment fixture')
  const pending = page.locator('.pending-attachment').filter({ hasText: 'annual-report.pdf' })
  await expect(pending).toContainText('解析失败', { timeout: 6_000 })
  await expect(pending.getByRole('button', { name: '重试' })).toBeVisible()

  await pending.getByRole('button', { name: '重试' }).click()
  await expect(pending).toContainText('解析完成', { timeout: 6_000 })
  expect(state.reparseCalls).toBe(1)

  await pending.locator('.attachment-remove-btn').click()
  await expect(page.locator('.pending-attachment')).toHaveCount(0)
  await expect.poll(() => state.deleteCalls).toBe(1)
  await page.waitForTimeout(250)
  expect(state.downloadCalls).toBe(0)
})

test('部分可用附件展示警告、允许下载并提示仅使用已解析内容', async ({ page }) => {
  const warning = '文档超过解析上限，仅索引前 1,000 页'
  const state = await installMocks(page, {
    uploadStatus: 'partial',
    statusSequence: ['partial'],
    warning,
  })
  await page.goto('/#/service/dih?type=ask&chatSessionId=e2e-attachment&token=e2e-token')

  await chooseFile(page, 'partial.csv', 'text/csv', 'quarter,revenue\nQ1,120\n')
  const pending = page.locator('.pending-attachment').filter({ hasText: 'partial.csv' })
  await expect(pending).toContainText('部分可用')
  await expect(pending).toContainText(warning)

  const downloadPromise = page.waitForEvent('download')
  await pending.locator('.attachment-name').click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('partial.csv')
  expect(state.downloadCalls).toBe(1)

  await page.getByPlaceholder('输入你的问题，帮你深度解答').fill('这个表里哪一季度收入最高？')
  await page.getByRole('button', { name: '发送消息' }).click()
  await expect(page.locator('.el-message').filter({ hasText: '本轮只能使用已解析内容' })).toBeVisible()
  await expect.poll(() => state.chatRequests.length).toBe(1)
  expect(state.chatRequests[0].attachments).toEqual([
    expect.objectContaining({
      file_id: 'file-partial',
      parse_status: 'partial',
      truncated: true,
    }),
  ])
})

test('不支持正文解析的文件仍可发送但只提供元信息', async ({ page }) => {
  const warning = '旧版 Office 文件仅保留元信息，不支持正文解析'
  const state = await installMocks(page, {
    uploadStatus: 'unsupported',
    statusSequence: ['unsupported'],
    warning,
  })
  await page.goto('/#/service/dih?type=ask&chatSessionId=e2e-attachment&token=e2e-token')

  await chooseFile(page, 'legacy.doc', 'application/msword', 'legacy office fixture')
  const pending = page.locator('.pending-attachment').filter({ hasText: 'legacy.doc' })
  await expect(pending).toContainText('仅元信息')
  await expect(pending).toContainText(warning)

  await page.getByPlaceholder('输入你的问题，帮你深度解答').fill('说明这个附件的可用范围')
  await page.getByRole('button', { name: '发送消息' }).click()
  await expect(page.locator('.el-message').filter({ hasText: '本轮只能使用已解析内容' })).toBeVisible()
  await expect.poll(() => state.chatRequests.length).toBe(1)
  expect(state.chatRequests[0].attachments).toEqual([
    expect.objectContaining({
      file_id: 'file-legacy',
      parse_status: 'unsupported',
      kind: 'file',
    }),
  ])
})
