import { expect, test, type Page, type Route } from '@playwright/test'

const RUN_ID = 'run-activity-1'
const CHAT_ID = 'e2e-agent-activity'
const PUSH_CHAT_ID = 'e2e-agent-push-activity'
const METADATA_CONFIG_TYPE = 'agent_metadata_config'
const DATA_PUSH_SERVICE_TYPE = 'agent_data_push_service'

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

const baseRun = (lastEventSequence: number, status = 'RUNNING') => ({
  run_id: RUN_ID,
  chat_id: CHAT_ID,
  chat_type: METADATA_CONFIG_TYPE,
  agent_type: METADATA_CONFIG_TYPE,
  status,
  status_label: status === 'SUCCEEDED'
    ? '已完成'
    : (status === 'WAITING_USER' ? '等待补充信息' : (status === 'QUEUED' ? '等待执行' : '执行中')),
  goal: '核对元数据配置链路并给出结论',
  plan: {
    goal: '核对元数据配置链路并给出结论',
    steps: [{
      id: '1',
      title: '检索接入规范并核验配置',
      status: status === 'SUCCEEDED' ? 'DONE' : 'RUNNING',
    }],
  },
  plan_revision: 1,
  decision_steps: 1,
  tool_calls: 1,
  effective_millis: 2300,
  max_steps: 30,
  max_duration_seconds: 1800,
  max_tool_calls: 80,
  last_event_sequence: lastEventSequence,
  attempt_count: 1,
  recovery_count: 0,
  truncation_count: 0,
  final_answer: status === 'SUCCEEDED' ? '接入链路核验完成。' : '',
  create_time: '2026-10-23T10:00:00+08:00',
  update_time: '2026-10-23T10:00:08+08:00',
})

const waitingUserRun = (question: string) => ({
  ...baseRun(4, 'WAITING_USER'),
  paused_reason: `${question.slice(0, 24)}…`,
  pending_action: JSON.stringify({ type: 'user', question }),
})

const mixedEvents = (includeCompaction = true) => {
  const events: Array<Record<string, unknown>> = [
    {
      sequence: 1,
      run_id: RUN_ID,
      type: 'rag.search',
      step_id: '1',
      created_at: '2026-10-23T10:00:01+08:00',
      data: {
        searchId: 'rag-1',
        query: '元数据配置规范',
        mode: 'agent',
        scopeType: 'bound',
        status: 'succeeded',
        hitCount: 2,
        durationMillis: 43,
        results: [
          { fileName: 'ingestion.md', title: '元数据配置规范', score: 0.92 },
          { fileName: 'policy.md', title: '接入策略', score: 0.81 },
        ],
      },
    },
    {
      sequence: 2,
      run_id: RUN_ID,
      type: 'tool.started',
      step_id: '1',
      created_at: '2026-10-23T10:00:02+08:00',
      data: {
        callId: 'call-1',
        toolName: 'config_tree',
        status: 'running',
        arguments: '{"type":"data_source"}',
      },
    },
    {
      sequence: 3,
      run_id: RUN_ID,
      type: 'tool.succeeded',
      step_id: '1',
      created_at: '2026-10-23T10:00:03+08:00',
      data: {
        callId: 'call-1',
        toolName: 'config_tree',
        status: 'succeeded',
        arguments: '{"type":"data_source"}',
        result: '{"items":[{"name":"mysql-main"}]}',
        durationMillis: 79,
      },
    },
  ]
  if (includeCompaction) {
    events.push({
      sequence: 4,
      run_id: RUN_ID,
      type: 'context.compacted',
      step_id: '1',
      created_at: '2026-10-23T10:00:04+08:00',
      data: {
        compactionId: 'compact-1',
        stage: 'model-input',
        reason: 'prompt_token_budget',
        beforeTokens: 8000,
        afterTokens: 600,
        savedTokens: 7400,
        beforeChars: 32000,
        afterChars: 2400,
        budgetTokens: 600,
        budgetChars: 2400,
        summaryPreview: '保留用户目标、当前计划和最近工具结果。',
      },
    })
  }
  return events
}

const installMocks = async (
  page: Page,
  options: {
    lastEventSequence: number
    streamEvents: Array<Record<string, unknown>>
    historyEvents: Array<Record<string, unknown>>
    initialRun?: Record<string, unknown>
    runs?: Array<Record<string, unknown>>
    messageList?: Array<Record<string, unknown>>
    chatType?: string
    title?: string
    extraData?: string
  },
) => {
  const activityListRequests: string[] = []
  const steerPayloads: Array<Record<string, any>> = []
  let detailRun = options.initialRun || baseRun(options.lastEventSequence, 'SUCCEEDED')
  let listRun = options.initialRun || baseRun(options.lastEventSequence)
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()

    if (path.endsWith('/skills/chat-entries')) {
      await fulfillJson(route, ok([
        {
          skill_id: 'metadata-config-agent',
          chat_type: METADATA_CONFIG_TYPE,
          agent_type: METADATA_CONFIG_TYPE,
          label: '元数据配置',
          icon: 'operation',
        },
        {
          skill_id: 'data-push-service-agent',
          chat_type: DATA_PUSH_SERVICE_TYPE,
          agent_type: DATA_PUSH_SERVICE_TYPE,
          label: '数据推送服务',
          icon: 'connection',
        },
        {
          skill_id: 'ask',
          chat_type: 'ask',
          agent_type: 'ask',
          label: '智能问答',
          icon: 'magic-stick',
        },
      ]))
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
        id: '201',
        session_id: CHAT_ID,
        title: options.title || '元数据配置任务验收',
        type: options.chatType || METADATA_CONFIG_TYPE,
        message_list: options.messageList || [],
        extra_data: options.extraData || '',
      }))
      return
    }
    if (path.endsWith(`/chat/runs/${RUN_ID}/events/list`)) {
      activityListRequests.push(request.url())
      await fulfillJson(route, ok(options.historyEvents))
      return
    }
    if (path.endsWith(`/chat/runs/${RUN_ID}/events`)) {
      await route.fulfill({
        status: 200,
        contentType: 'application/x-ndjson;charset=UTF-8',
        body: options.streamEvents.map(event => JSON.stringify(event)).join('\n')
          + (options.streamEvents.length ? '\n' : ''),
      })
      return
    }
    if (path.endsWith(`/chat/runs/${RUN_ID}`) && method === 'GET') {
      await fulfillJson(route, ok(detailRun))
      return
    }
    if (path.endsWith(`/chat/runs/${RUN_ID}/steer`) && method === 'POST') {
      steerPayloads.push(request.postDataJSON() as Record<string, any>)
      detailRun = {
        ...baseRun(options.lastEventSequence + 1, 'QUEUED'),
        paused_reason: '',
        pending_action: null,
      }
      listRun = detailRun
      await fulfillJson(route, ok(detailRun))
      return
    }
    if (path.endsWith('/chat/runs') && method === 'GET') {
      await fulfillJson(route, ok({
        rows: options.runs || [listRun],
        total: (options.runs || [listRun]).length,
      }))
      return
    }
    await fulfillJson(route, ok({}))
  })
  return { activityListRequests, steerPayloads }
}

const openRunWorkspace = async (page: Page) => {
  await page.goto(`/#/service/dih?type=${METADATA_CONFIG_TYPE}&chatSessionId=${CHAT_ID}&token=e2e-token`)
  await expect(page.locator('.agent-run-card').first()).toBeVisible({ timeout: 15_000 })
}

const hashQuery = (page: Page, key: string) => page.evaluate((name) => {
  const query = window.location.hash.split('?')[1] || ''
  return new URLSearchParams(query).get(name)
}, key)

test('底部入口只展示两个独立智能体且结果面板互不混入记录', async ({ page }) => {
  const extraData = JSON.stringify({
    metadataConfig: {
      records: [{
        id: 'user_event.json',
        name: '用户事件数据',
        fileName: 'user_event.json',
        tableName: 'zenvis.msg_user_event',
        fieldCount: 8,
        status: 'applied',
      }],
    },
    dataPushService: {
      records: [{
        id: '12',
        taskId: '12',
        name: '用户事件推送服务',
        sourceMark: 'data-push-demo:user-event:e2e',
        status: 'running',
      }],
    },
  })
  await installMocks(page, {
    lastEventSequence: 0,
    streamEvents: [],
    historyEvents: [],
    extraData,
    runs: [],
  })

  await page.goto(`/#/service/dih?type=${METADATA_CONFIG_TYPE}&chatSessionId=${CHAT_ID}&token=e2e-token`)
  await expect(page.locator('.suggestions')).toBeVisible({ timeout: 15_000 })
  const entries = page.locator('.suggestions .suggestion-btn')
  await expect(entries.filter({ hasText: '元数据配置' })).toHaveCount(1, { timeout: 15_000 })
  await expect(entries.filter({ hasText: '数据推送服务' })).toHaveCount(1, { timeout: 15_000 })
  await expect(entries.filter({ hasText: /^数据接入$/ })).toHaveCount(0, { timeout: 15_000 })
  await expect(page.locator('.workspace-result')).toContainText('元数据配置结果')
  await expect(page.locator('.workspace-result')).toContainText('user_event.json')
  await expect(page.locator('.workspace-result')).not.toContainText('用户事件推送服务')

  await page.goto(`/#/service/dih?type=${DATA_PUSH_SERVICE_TYPE}&chatSessionId=${PUSH_CHAT_ID}&token=e2e-token`)
  await expect(page.locator('.workspace-result')).toContainText('数据推送服务结果')
  await expect(page.locator('.workspace-result')).toContainText('用户事件推送服务')
  await expect(page.locator('.workspace-result')).not.toContainText('user_event.json')
})

test('推送缺失 Meta 卡通过新会话深链预填元数据配置提示词', async ({ page }) => {
  const prompt = '请为 user_event.json 创建并发布 Meta 配置：实体 user_event，目标表 zenvis.msg_user_event。'
  await installMocks(page, {
    lastEventSequence: 0,
    streamEvents: [],
    historyEvents: [],
    chatType: DATA_PUSH_SERVICE_TYPE,
    title: '数据推送服务验收',
    runs: [],
    messageList: [{
      id: 'message-missing-meta',
      sender: 'ai',
      content: '缺少已发布 Meta，数据推送服务流程已停止。',
      time: '2026-10-23 10:00:00',
      type: 'text',
      parts: [{
        id: 'missing-meta-part',
        type: 'data-push-service-decision',
        status: 'pending',
        title: '缺少已发布 Meta，无法创建数据推送服务',
        content: '请先前往元数据配置智能体完成并发布 Meta。',
        metadata: {
          kind: 'missing_meta',
          handoff: {
            targetAgentType: METADATA_CONFIG_TYPE,
            label: '前往元数据配置',
            prompt,
          },
          actions: [],
        },
      }],
    }],
  })

  await page.goto(`/#/service/dih?type=${DATA_PUSH_SERVICE_TYPE}&chatSessionId=${PUSH_CHAT_ID}&token=e2e-token`)
  await page.getByRole('button', { name: '前往元数据配置' }).click()

  await expect.poll(() => hashQuery(page, 'type'))
    .toBe(METADATA_CONFIG_TYPE)
  await expect.poll(() => hashQuery(page, 'createSession'))
    .toBe('1')
  await expect.poll(() => hashQuery(page, 'msgRef'))
    .toBeTruthy()
  await expect.poll(() => page.locator('.input-container textarea').inputValue())
    .toBe(prompt)
})

test('运行中的任务卡实时汇总检索、工具与压缩活动', async ({ page }) => {
  await installMocks(page, {
    lastEventSequence: 0,
    streamEvents: mixedEvents(),
    historyEvents: mixedEvents(),
  })
  await openRunWorkspace(page)

  const card = page.locator('.agent-run-card')
  await expect(card.locator('.run-activity-summary')).toContainText('检索 1 · 工具 1 · 压缩 1')
  await expect(card.locator('.run-activity-toggle')).toHaveAttribute('aria-expanded', 'false')
  await expect(card.locator('.agent-run-activity')).toHaveCount(0)
})

test('多轮会话为每一轮智能体任务恢复独立卡片', async ({ page }) => {
  const firstRun = {
    ...baseRun(5, 'SUCCEEDED'),
    run_id: 'run-history-1',
    goal: '第一轮：核对接入配置',
    final_answer: '第一轮任务完成。',
    create_time: '2026-10-23T09:00:00+08:00',
    update_time: '2026-10-23T09:01:00+08:00',
  }
  const secondRun = {
    ...baseRun(8, 'SUCCEEDED'),
    run_id: 'run-history-2',
    goal: '第二轮：核对推送配置',
    final_answer: '第二轮任务完成。',
    create_time: '2026-10-23T10:00:00+08:00',
    update_time: '2026-10-23T10:01:00+08:00',
  }
  const messageList = [
    {
      id: 'message-user-1',
      sender: 'user',
      content: '核对第一轮接入配置',
      time: '2026-10-23 09:00:00',
      run_id: firstRun.run_id,
    },
    {
      id: 'message-ai-1',
      sender: 'ai',
      content: firstRun.final_answer,
      time: '2026-10-23 09:01:00',
      type: 'text',
      run_id: firstRun.run_id,
    },
    {
      id: 'message-user-2',
      sender: 'user',
      content: '继续核对第二轮推送配置',
      time: '2026-10-23 10:00:00',
      run_id: secondRun.run_id,
    },
    {
      id: 'message-ai-2',
      sender: 'ai',
      content: secondRun.final_answer,
      time: '2026-10-23 10:01:00',
      type: 'text',
      run_id: secondRun.run_id,
    },
  ]

  await installMocks(page, {
    lastEventSequence: 8,
    streamEvents: [],
    historyEvents: [],
    runs: [secondRun, firstRun],
    messageList,
  })
  await openRunWorkspace(page)

  const aiMessages = page.locator('.message-item.is-ai-message-item')
  await expect(aiMessages).toHaveCount(2)
  await expect(aiMessages.nth(0).locator('.agent-run-card')).toContainText('第一轮：核对接入配置')
  await expect(aiMessages.nth(0)).toContainText('第一轮任务完成。')
  await expect(aiMessages.nth(1).locator('.agent-run-card')).toContainText('第二轮：核对推送配置')
  await expect(aiMessages.nth(1)).toContainText('第二轮任务完成。')
})

test('展开执行过程时按类型恢复历史，详情默认折叠且刷新后仍可恢复', async ({ page }) => {
  const mocks = await installMocks(page, {
    lastEventSequence: 8,
    streamEvents: [],
    historyEvents: mixedEvents(),
  })
  await openRunWorkspace(page)

  const card = page.locator('.agent-run-card')
  await card.locator('.run-activity-toggle').click()

  const activity = card.getByTestId('agent-run-activity')
  await expect(activity).toBeVisible()
  await expect(card.locator('.run-activity-summary')).toContainText('检索 1 · 工具 1 · 压缩 1')
  await expect(activity.locator('.activity-summary-item').filter({ hasText: '知识库检索' }))
    .toContainText('2 个来源')
  await expect(activity.locator('.activity-summary-item').filter({ hasText: '工具调用' }))
    .toContainText('1 次')
  await expect(activity.locator('.activity-summary-item').filter({ hasText: '上下文压缩' }))
    .toContainText('节省 7,400 tokens')

  await expect(activity.locator('.rag-search-header')).toHaveAttribute('aria-expanded', 'false')
  await expect(activity.locator('.tool-activity-header')).toHaveAttribute('aria-expanded', 'false')
  await expect(activity.locator('.compaction-header')).toHaveAttribute('aria-expanded', 'false')
  await activity.locator('.compaction-header').click()
  await expect(activity.locator('.compaction-body')).toContainText('Token 8,000 → 600')
  await expect(activity.locator('.compaction-body')).toContainText('字符预算 2,400')
  await expect(activity.locator('.compaction-body')).toContainText('保留用户目标、当前计划和最近工具结果。')

  const activityRequests = mocks.activityListRequests
  expect(activityRequests.length).toBeGreaterThan(0)
  for (const url of activityRequests) {
    const types = new URL(url).searchParams.get('types') || ''
    expect(types).toContain('rag.search')
    expect(types).toContain('tool.started')
    expect(types).toContain('context.compacted')
  }

  await page.reload()
  await expect(page.locator('.agent-run-card')).toBeVisible()
  await page.locator('.agent-run-card .run-activity-toggle').click()
  await expect(page.locator('.agent-run-card').getByTestId('agent-run-activity'))
    .toContainText('节省 7,400 tokens')
})

test('无活动时显示空状态，任务中心保留折叠的原始事件', async ({ page }) => {
  await installMocks(page, {
    lastEventSequence: 0,
    streamEvents: [],
    historyEvents: [],
  })
  await openRunWorkspace(page)

  const card = page.locator('.agent-run-card')
  await card.locator('.run-activity-toggle').click()
  const activity = card.getByTestId('agent-run-activity')
  await expect(activity).toContainText('本次任务没有可展示的检索、工具或压缩过程。')
  await expect(activity).toContainText('无上下文压缩记录；旧任务可能尚未采集该诊断。')

  await page.getByRole('button', { name: '智能体运行' }).click()
  const drawer = page.locator('.agent-run-drawer')
  await expect(drawer).toBeVisible()
  await drawer.getByText('核对元数据配置链路并给出结论').first().click()

  const detail = page.locator('.run-detail-dialog')
  await expect(detail).toBeVisible()
  await expect(detail.getByTestId('agent-run-activity')).toContainText('本次任务没有可展示的检索、工具或压缩过程。')
  const rawToggle = detail.locator('.raw-events-toggle')
  await expect(rawToggle).toHaveAttribute('aria-expanded', 'false')
  await expect(detail.locator('.event-list')).toHaveCount(0)
  await rawToggle.click()
  await expect(detail.locator('.event-list')).toBeVisible()
  await expect(detail.locator('.event-list')).toContainText('暂无事件')
})

test('等待补充信息时自动弹出输入框，提交后任务继续执行', async ({ page }) => {
  const question = [
    '要完成“按环境变量执行配置写入”，需要你补充两类信息：',
    '1）推送任务配置中的 Source、Topic、消费组和 DLQ Topic 实际值；',
    '2）KAFKA_BOOTSTRAP_SERVERS、LAB_ACCESS_TOPIC、LAB_RISK_TOPIC 的实际值。',
    '请勿提供任何密码、Token 或 Cookie。',
  ].join('\n')
  const mocks = await installMocks(page, {
    lastEventSequence: 4,
    streamEvents: [],
    historyEvents: [],
    initialRun: waitingUserRun(question),
  })
  await openRunWorkspace(page)

  const card = page.locator('.agent-run-card').first()
  await expect(card).toContainText('需要补充信息')
  await expect(card).toContainText(question)
  const dialog = page.locator('.agent-run-user-input-dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('任务需要补充信息')
  await expect(dialog).toContainText(question)

  await dialog.getByPlaceholder('请输入需要补充的信息').fill(
    'Source=lab-access；Topic=lab-access-topic；DLQ=lab-dlq',
  )
  await dialog.getByRole('button', { name: '提交并继续' }).click()

  await expect.poll(() => mocks.steerPayloads.length).toBe(1)
  expect(mocks.steerPayloads[0]).toMatchObject({
    message: 'Source=lab-access；Topic=lab-access-topic；DLQ=lab-dlq',
  })
  await expect(dialog).not.toBeVisible()
  await expect(page.locator('.agent-run-card').first()).toContainText('排队中')
})
