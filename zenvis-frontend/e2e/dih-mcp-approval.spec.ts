import { expect, test, type Page, type Route } from '@playwright/test'

const RUN_ID = 'approval-run-1'
const CHAT_ID = 'e2e-mcp-approval'
const CHAT_TYPE = 'agent_metadata_config'
const REQUEST_ID = 'approval-request-1'

const ok = (data: unknown) => ({ status: 0, code: 0, msg: 'success', data })
const fulfillJson = (route: Route, data: unknown) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(data),
})

const run = (status: string, lastEventSequence: number) => ({
  run_id: RUN_ID,
  chat_id: CHAT_ID,
  chat_type: CHAT_TYPE,
  agent_type: CHAT_TYPE,
  status,
  goal: '验证 MCP 工具审批卡展示',
  plan: { goal: '验证 MCP 工具审批卡展示', steps: [{ id: '1', title: '执行工具', status: 'RUNNING' }] },
  last_event_sequence: lastEventSequence,
  pending_action: status === 'WAITING_APPROVAL'
    ? JSON.stringify({ type: 'approval', requestId: REQUEST_ID })
    : null,
  pending_interaction: status === 'WAITING_APPROVAL'
    ? {
        interaction_id: 'approval-interaction-1',
        kind: 'mcp_approval',
        request_id: REQUEST_ID,
        title: 'MCP 工具审批',
        prompt: '该工具需要你的明确许可后才能执行。',
        allowed_actions: [],
      }
    : null,
  paused_reason: null,
  final_answer: '',
  create_time: '2026-10-30T13:49:00+08:00',
  update_time: '2026-10-30T13:49:00+08:00',
})

const approval = {
  request_id: REQUEST_ID,
  tool_key: 'local::config_add',
  tool_name: 'config_add',
  description: '在指定配置类型目录下创建空配置文件；正文由 config_apply 写入',
  status: 'PENDING',
  risk_level: 'high',
  agent_run_id: RUN_ID,
  run_approval_allowed: true,
}

const installMocks = async (
  page: Page,
  options: {
    initialRun: Record<string, unknown>
    streamEvents?: Array<Record<string, unknown>>
  },
) => {
  const interactionPayloads: Array<Record<string, unknown>> = []
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()
    if (path.endsWith('/skills/chat-entries')) {
      await fulfillJson(route, ok([{
        skill_id: 'metadata-config-agent',
        chat_type: CHAT_TYPE,
        agent_type: CHAT_TYPE,
        label: '元数据配置',
        icon: 'operation',
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
        id: 'session-record-1',
        session_id: CHAT_ID,
        title: 'MCP 工具审批任务',
        type: CHAT_TYPE,
        message_list: [{
          id: 'user-message-1',
          sender: 'user',
          content: '生成一个教师信息的元数据配置',
          time: '2026-10-30 13:49:00',
          run_id: RUN_ID,
        }],
        extra_data: '',
      }))
      return
    }
    if (path.endsWith(`/chat/runs/${RUN_ID}/events/list`)) {
      await fulfillJson(route, ok([]))
      return
    }
    if (path.endsWith(`/chat/runs/${RUN_ID}/events`)) {
      await route.fulfill({
        status: 200,
        contentType: 'application/x-ndjson;charset=UTF-8',
        body: (options.streamEvents || []).map(event => JSON.stringify(event)).join('\n'),
      })
      return
    }
    if (path.endsWith(`/chat/runs/${RUN_ID}`) && method === 'GET') {
      await fulfillJson(route, ok(options.initialRun))
      return
    }
    if (path.endsWith('/chat/runs') && method === 'GET') {
      await fulfillJson(route, ok({ rows: [options.initialRun], total: 1 }))
      return
    }
    if (path.endsWith(`/mcp/approvals/${REQUEST_ID}/view`) && method === 'GET') {
      await fulfillJson(route, ok(approval))
      return
    }
    if (path.endsWith(`/chat/runs/${RUN_ID}/interactions/approval-interaction-1/respond`)
      && method === 'POST') {
      interactionPayloads.push(request.postDataJSON() as Record<string, unknown>)
      await fulfillJson(route, ok({
        accepted: true,
        duplicate: false,
        interaction_id: 'approval-interaction-1',
        part_status: 'approved',
        summary: 'approved',
        run: run('QUEUED', 5),
      }))
      return
    }
    await fulfillJson(route, ok({}))
  })
  return { interactionPayloads }
}

const openPage = (page: Page) => page.goto(
  `/#/service/dih?type=${CHAT_TYPE}&chatSessionId=${CHAT_ID}&token=tmp-token`,
)

test('snapshot WAITING_APPROVAL restores approval card', async ({ page }) => {
  const mocks = await installMocks(page, { initialRun: run('WAITING_APPROVAL', 4), streamEvents: [] })
  await openPage(page)
  await expect(page.locator('.agent-run-card').first()).toBeVisible({ timeout: 15_000 })
  const approvalCard = page.locator('.mcp-approval-part').filter({ hasText: 'config_add' })
  await expect(approvalCard).toBeVisible({ timeout: 15_000 })
  await expect(approvalCard).toContainText('等待审批')
  await expect(approvalCard.getByRole('button', { name: '允许本次' })).toBeVisible()
  const userMessagesBefore = await page.locator('.user-message').count()
  await approvalCard.getByRole('button', { name: '允许本次' }).click()
  await expect.poll(() => mocks.interactionPayloads.length).toBe(1)
  expect(mocks.interactionPayloads[0]).toMatchObject({ decision: 'approved' })
  await expect(page.locator('.user-message')).toHaveCount(userMessagesBefore)
})

test('live approval event renders approval card', async ({ page }) => {
  await installMocks(page, {
    initialRun: run('RUNNING', 1),
    streamEvents: [
      {
        sequence: 2,
        run_id: RUN_ID,
        type: 'approval.required',
        data: { requestId: REQUEST_ID },
      },
      {
        sequence: 3,
        run_id: RUN_ID,
        type: 'tool.approval_required',
        data: approval,
      },
    ],
  })
  await openPage(page)
  const approvalCard = page.locator('.mcp-approval-part').filter({ hasText: 'config_add' })
  await expect(approvalCard).toBeVisible({ timeout: 15_000 })
  await expect(approvalCard).toContainText('等待审批')
  await expect(approvalCard.getByRole('button', { name: '允许本次' })).toBeVisible()
})
