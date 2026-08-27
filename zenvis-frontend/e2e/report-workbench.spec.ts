import { expect, test, type Page, type Route } from '@playwright/test'

const documentV3 = {
  document_id: 'report-doc-1',
  title: '安全运营周报',
  format: 'markdown',
  revision: 3,
  version: 'v3',
  status: 'edited',
  source: 'editor',
  content: '# 安全运营周报\n\n原始关键结论',
  content_hash: 'hash-v3',
  outline: [{ id: 'heading-1', text: '安全运营周报', level: 1 }],
  source_refs: [],
}

const workspace = (currentDocument = documentV3) => ({
  current_document: currentDocument,
  revisions: [{
    revision: currentDocument.revision,
    version: currentDocument.version,
    title: currentDocument.title,
    format: currentDocument.format,
    content_hash: currentDocument.content_hash,
    source_refs: [],
  }],
  artifacts: [],
  extra_data: JSON.stringify({
    report: {
      documentId: currentDocument.document_id,
      revision: currentDocument.revision,
      version: currentDocument.version,
    },
  }),
})

const ok = (data: unknown) => ({
  status: 0,
  code: 0,
  msg: 'success',
  data,
})

type MockOptions = {
  saveMode?: 'success' | 'conflict'
  onSave?: (payload: Record<string, unknown>) => void
  onChat?: (payload: Record<string, any>, route: Route) => Promise<void>
}

const fulfillJson = (route: Route, data: unknown, status = 200) => route.fulfill({
  status,
  contentType: 'application/json',
  body: JSON.stringify(data),
})

const installApiMocks = async (page: Page, options: MockOptions = {}) => {
  let serverDocument = { ...documentV3 }
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()

    if (path.endsWith('/skills/chat-entries')) {
      await fulfillJson(route, ok([{
        skill_id: 'report-agent',
        chat_type: 'agent_report',
        agent_type: 'agent_report',
        label: '报表制作',
        icon: 'document',
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
    if (path.endsWith('/chat-session/e2e-session/session') && method === 'GET') {
      await fulfillJson(route, ok({
        id: '101',
        session_id: 'e2e-session',
        title: '报表并发验收',
        type: 'agent_report',
        message_list: [],
        extra_data: workspace(serverDocument).extra_data,
      }))
      return
    }
    if (path.endsWith('/chat-session/101/report/materials')) {
      await fulfillJson(route, ok([]))
      return
    }
    if (path.endsWith('/chat-session/101/report') && method === 'GET') {
      await fulfillJson(route, ok(workspace(serverDocument)))
      return
    }
    if (path.endsWith('/chat-session/101/report/save') && method === 'POST') {
      const payload = request.postDataJSON() as Record<string, unknown>
      options.onSave?.(payload)
      if (options.saveMode === 'conflict') {
        serverDocument = {
          ...documentV3,
          revision: 4,
          version: 'v4',
          content: '# 安全运营周报\n\n服务器上的新版本',
        }
        await fulfillJson(route, {
          status: 409,
          msg: '报表已被其他操作更新',
          data: {
            current_document: serverDocument,
          },
        }, 409)
        return
      }
      serverDocument = {
        ...documentV3,
        title: String(payload.title || documentV3.title),
        content: String(payload.content || documentV3.content),
        revision: 4,
        version: 'v4',
        content_hash: 'hash-v4',
      }
      await fulfillJson(route, ok(workspace(serverDocument)))
      return
    }
    if (path.endsWith('/dih/chat') && method === 'POST' && options.onChat) {
      await options.onChat(request.postDataJSON() as Record<string, any>, route)
      return
    }

    await fulfillJson(route, ok({}))
  })
}

const openReportWorkbench = async (page: Page) => {
  await page.goto('/#/service/dih?type=agent_report&chatSessionId=e2e-session&token=e2e-token')
  await expect(page.locator('.report-workbench')).toBeVisible()
  const editor = page.locator('.w-e-text-container [contenteditable="true"]')
  await expect(editor).toBeVisible()
  await page.evaluate(currentDocument => {
    window.dispatchEvent(new CustomEvent('dihReportRecordsUpdated', {
      detail: {
        sessionRecordId: '101',
        currentDocument,
        documents: [currentDocument],
        revisions: [],
        artifacts: [],
        materials: [],
      },
    }))
  }, {
    ...documentV3,
    documentId: documentV3.document_id,
    contentHash: documentV3.content_hash,
    sourceRefs: documentV3.source_refs,
  })
  await expect(page.getByPlaceholder('未命名报表')).toHaveValue('安全运营周报')
  await expect(editor).toContainText('原始关键结论')
}

test('保存携带 baseRevision，成功后递增修订且不会隐式归档', async ({ page }) => {
  const savePayloads: Array<Record<string, unknown>> = []
  const archiveRequests: string[] = []
  await installApiMocks(page, {
    onSave: payload => savePayloads.push(payload),
  })
  page.on('request', request => {
    if (new URL(request.url()).pathname.endsWith('/report/archive')) {
      archiveRequests.push(request.url())
    }
  })
  await openReportWorkbench(page)

  await page.getByPlaceholder('未命名报表').fill('安全运营周报（修订）')
  const saved = page.waitForResponse(response => {
    return new URL(response.url()).pathname.endsWith('/report/save')
  })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await saved

  await expect(page.locator('.report-header .el-tag').first()).toContainText('已保存')
  await expect(page.locator('.report-header')).toContainText('v4')
  expect(savePayloads).toHaveLength(1)
  expect(savePayloads[0]).toMatchObject({
    document_id: 'report-doc-1',
    base_revision: 3,
    title: '安全运营周报（修订）',
  })
  expect(archiveRequests).toHaveLength(0)
})

test('旧版本保存显示冲突并保留本地编辑', async ({ page }) => {
  await installApiMocks(page, { saveMode: 'conflict' })
  await openReportWorkbench(page)

  const title = page.getByPlaceholder('未命名报表')
  await title.fill('保留在本地的标题')
  const conflicted = page.waitForResponse(response => {
    return new URL(response.url()).pathname.endsWith('/report/save')
  })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await conflicted

  await expect(page.getByRole('dialog', { name: '报表版本冲突' })).toBeVisible()
  await page.getByRole('button', { name: '保留本地内容' }).click()
  await expect(page.locator('.report-header .el-tag').first()).toContainText('版本冲突')
  await expect(title).toHaveValue('保留在本地的标题')
})

test('用户继续编辑后，迟到的选区改写只展示预览且不覆盖正文', async ({ page }) => {
  let releaseReply: (() => void) | undefined
  const replyGate = new Promise<void>(resolve => {
    releaseReply = resolve
  })
  let reportAction: Record<string, any> = {}
  await installApiMocks(page, {
    onChat: async (payload, route) => {
      reportAction = payload.report_action || {}
      await replyGate
      await route.fulfill({
        contentType: 'application/x-ndjson',
        body: `${JSON.stringify({
          event: 'done',
          message: {
            id: 'fragment-message',
            sender: 'ai',
            content: '',
            type: 'text',
            parts: [{
              id: 'fragment-part',
              type: 'report-fragment',
              content: '迟到的模型改写内容',
              metadata: {
                document_id: 'report-doc-1',
                base_revision: 3,
                selection_hash: reportAction.selection_hash,
              },
            }],
          },
        })}\n`,
      })
    },
  })
  await openReportWorkbench(page)

  const editor = page.locator('.w-e-text-container [contenteditable="true"]')
  const conclusion = editor.locator('p').filter({ hasText: '原始关键结论' })
  await conclusion.click({ clickCount: 3 })
  await conclusion.click({ button: 'right' })
  await page.getByRole('button', { name: '润色' }).click()
  await expect.poll(() => reportAction.type).toBe('selection_rewrite')
  expect(reportAction).toMatchObject({
    document_id: 'report-doc-1',
    base_revision: 3,
  })
  expect(String(reportAction.selection_hash)).not.toBe('')

  await editor.press('End')
  await editor.type('（用户已继续编辑）')
  releaseReply?.()

  await expect(page.getByRole('dialog', { name: '选区已变化，改写结果未应用' })).toBeVisible()
  await expect(editor).toContainText('用户已继续编辑')
  await expect(editor).not.toContainText('迟到的模型改写内容')
})
