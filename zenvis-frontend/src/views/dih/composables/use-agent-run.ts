import { computed, onUnmounted, ref } from 'vue';
import { DihService } from '@/service/api';
import type {
  AgentRun,
  AgentRunCreateParams,
  AgentRunEvent,
  AgentRunPlanStep,
  AgentRunSteerParams,
  McpApprovalData,
} from '@/types/type-dih';
import {
  readAgentRunCursor,
  resolveAgentRunReplayCursor,
  shouldAcceptAgentRunEvent,
  writeAgentRunCursor,
} from './agent-run-replay';
import { isAgentRunActivityEvent } from './agent-run-activity';
import {
  isAgentRunLive,
  isAgentRunRestorable,
  isAgentRunSubscribable,
} from './agent-run-message';

const TERMINAL_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED']);
type UseAgentRunOptions = {
  onEvent?: (event: AgentRunEvent) => void | Promise<void>;
  onApproval?: (approval: McpApprovalData) => void | Promise<void>;
  onTerminal?: (run: AgentRun) => void | Promise<void>;
};

const eventData = (event: AgentRunEvent) =>
  event.data && typeof event.data === 'object' ? event.data : {};

const approvalFromEvent = (data: Record<string, unknown>): McpApprovalData | null => {
  const requestId = String(data.requestId || data.request_id || '');
  if (!requestId) return null;
  return {
    ...data,
    requestId,
    toolKey: String(data.toolKey || data.tool_key || ''),
    toolName: String(data.toolName || data.tool_name || ''),
    description: String(data.description || ''),
    status: String(data.status || 'pending').toLowerCase(),
    riskLevel: String(data.riskLevel || data.risk_level || 'warning'),
    approvalScope: String(data.approvalScope || data.approval_scope || '').toLowerCase(),
    runApprovalAllowed: Boolean(data.runApprovalAllowed ?? data.run_approval_allowed ?? false),
    agentRunId: String(data.agentRunId || data.agent_run_id || ''),
  };
};

export const useAgentRun = (options: UseAgentRunOptions = {}) => {
  const run = ref<AgentRun | null>(null);
  const events = ref<AgentRunEvent[]>([]);
  const activityEvents = ref<AgentRunEvent[]>([]);
  const lastSequence = ref(0);
  const subscribed = ref(false);
  const currentTool = ref<Record<string, unknown> | null>(null);
  const pendingApproval = ref<McpApprovalData | null>(null);
  const lastArtifact = ref<Record<string, unknown> | null>(null);
  const liveAnswer = ref('');
  const currentRunId = ref('');

  let abortController: AbortController | null = null;
  let reconnectTimer: number | null = null;
  let subscriptionGeneration = 0;

  const isActive = computed(() => isAgentRunLive(run.value?.status));
  const isTerminal = computed(() =>
    Boolean(run.value && TERMINAL_STATUSES.has(run.value.status)),
  );

  const replaceStep = (step: Record<string, unknown>) => {
    if (!run.value?.plan) return;
    const id = String(step.id || '');
    if (!id) return;
    const steps = [...run.value.plan.steps];
    const index = steps.findIndex(item => item.id === id);
    const normalized = step as unknown as AgentRunPlanStep;
    if (index >= 0) steps[index] = { ...steps[index], ...normalized };
    else steps.push(normalized);
    run.value = { ...run.value, plan: { ...run.value.plan, steps } };
  };

  const mergeSnapshot = (snapshot: AgentRun) => {
    if (!snapshot?.runId) return;
    if (currentRunId.value && currentRunId.value !== snapshot.runId) {
      events.value = [];
      activityEvents.value = [];
      currentTool.value = null;
      pendingApproval.value = null;
      lastArtifact.value = null;
      liveAnswer.value = '';
      lastSequence.value = 0;
    }
    run.value = snapshot;
    currentRunId.value = snapshot.runId;
    lastSequence.value = Math.max(lastSequence.value, snapshot.lastEventSequence || 0);
  };

  const refreshRun = async (runId = currentRunId.value) => {
    if (!runId) return null;
    try {
      const snapshot = await DihService.getAgentRun(runId);
      mergeSnapshot(snapshot);
      return snapshot;
    } catch {
      return null;
    }
  };

  const applyEvent = async (event: AgentRunEvent) => {
    const data = eventData(event);
    switch (event.type) {
      case 'run.created':
      case 'run.snapshot': {
        const snapshot = data.run;
        if (snapshot && typeof snapshot === 'object') {
          await refreshRun(event.run_id);
        }
        break;
      }
      case 'plan.updated': {
        const plan = data.plan && typeof data.plan === 'object'
          ? data.plan as AgentRun['plan']
          : null;
        if (run.value && plan) {
          run.value = {
            ...run.value,
            plan,
            planRevision: Number(data.revision || run.value.planRevision || 0),
          };
        }
        break;
      }
      case 'step.updated': {
        const step = data.step && typeof data.step === 'object'
          ? data.step as Record<string, unknown>
          : {};
        replaceStep(step);
        break;
      }
      case 'tool.started':
      case 'tool.succeeded':
      case 'tool.failed':
      case 'tool.approval_executed':
        currentTool.value = data;
        break;
      case 'tool.approval_required': {
        const approval = approvalFromEvent(data);
        if (approval) {
          pendingApproval.value = approval;
          if (run.value) run.value = { ...run.value, status: 'WAITING_APPROVAL' };
          await options.onApproval?.(approval);
        }
        break;
      }
      case 'tool.approval_updated': {
        const approval = approvalFromEvent(data);
        if (approval) {
          pendingApproval.value = approval.status === 'pending' ? approval : null;
          if (run.value && approval.status !== 'pending') {
            run.value = { ...run.value, status: 'QUEUED' };
          }
          await options.onApproval?.(approval);
        }
        break;
      }
      case 'approval.required': {
        const approval = approvalFromEvent(data);
        if (approval) {
          pendingApproval.value = approval;
          if (run.value) run.value = { ...run.value, status: 'WAITING_APPROVAL' };
          await options.onApproval?.(approval);
        }
        break;
      }
      case 'approval.updated': {
        const approval = approvalFromEvent(data);
        if (approval) {
          pendingApproval.value = null;
          if (run.value) run.value = { ...run.value, status: 'QUEUED' };
          await options.onApproval?.(approval);
        }
        break;
      }
      case 'artifact.updated':
        lastArtifact.value = data;
        break;
      case 'assistant.delta':
        liveAnswer.value += String(data.content || '');
        break;
      case 'steer.queued':
        if (run.value) run.value = { ...run.value, steered: true };
        break;
      case 'run.queued':
        if (run.value) run.value = { ...run.value, status: 'QUEUED', pausedReason: '' };
        await refreshRun(event.run_id);
        break;
      case 'run.paused':
        if (run.value) {
          run.value = {
            ...run.value,
            status: 'PAUSED',
            pausedReason: String(data.reason || run.value.pausedReason || '任务已暂停'),
          };
        }
        await refreshRun(event.run_id);
        break;
      case 'run.error':
        if (run.value) {
          run.value = {
            ...run.value,
            lastError: String(data.message || run.value.lastError || ''),
          };
        }
        break;
      case 'run.waiting_user':
        if (run.value) {
          run.value = {
            ...run.value,
            status: 'WAITING_USER',
            pausedReason: String(data.question || '等待用户补充信息'),
          };
        }
        break;
      case 'run.succeeded':
        if (run.value) {
          run.value = {
            ...run.value,
            status: 'SUCCEEDED',
            finalAnswer: String(data.answer || run.value.finalAnswer || liveAnswer.value),
          };
        }
        await refreshRun(event.run_id);
        break;
      case 'run.failed':
        if (run.value) {
          run.value = {
            ...run.value,
            status: 'FAILED',
            lastError: String(data.error || run.value.lastError || '任务执行失败'),
          };
        }
        await refreshRun(event.run_id);
        break;
      case 'run.canceling':
        if (run.value) run.value = { ...run.value, status: 'CANCELING' };
        break;
      case 'run.cancelled':
        if (run.value) run.value = { ...run.value, status: 'CANCELLED' };
        await refreshRun(event.run_id);
        break;
      default:
        break;
    }
  };

  const acceptEvent = async (event: AgentRunEvent) => {
    if (!shouldAcceptAgentRunEvent(lastSequence.value, event)) return;
    lastSequence.value = event.sequence;
    writeAgentRunCursor(event.run_id || currentRunId.value, event.sequence);
    events.value = [...events.value.slice(-499), event];
    if (isAgentRunActivityEvent(event)) {
      activityEvents.value = [...activityEvents.value.slice(-499), event];
    }
    await applyEvent(event);
    await options.onEvent?.(event);
  };

  const stopSubscription = () => {
    subscriptionGeneration += 1;
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    abortController?.abort();
    abortController = null;
    subscribed.value = false;
  };

  const scheduleReconnect = (runId: string, generation: number, delay = 1200) => {
    if (generation !== subscriptionGeneration) return;
    if (reconnectTimer !== null) window.clearTimeout(reconnectTimer);
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null;
      if (generation !== subscriptionGeneration) return;
      void subscribe(runId, { after: lastSequence.value });
    }, delay);
  };

  const subscribe = async (
    runId: string,
    subscribeOptions: { after?: number; resetEvents?: boolean } = {},
  ) => {
    stopSubscription();
    if (!runId) return;
    currentRunId.value = runId;
    const generation = subscriptionGeneration;
    if (subscribeOptions.resetEvents) {
      events.value = [];
      activityEvents.value = [];
    }
    lastSequence.value = resolveAgentRunReplayCursor(
      run.value,
      runId,
      subscribeOptions.after,
      readAgentRunCursor(runId),
    );
    abortController = new AbortController();
    subscribed.value = true;
    const streamOk = await DihService.streamAgentRunEvents(
      runId,
      lastSequence.value,
      async event => {
        if (generation !== subscriptionGeneration) return;
        await acceptEvent(event);
      },
      { signal: abortController.signal },
    );
    if (generation !== subscriptionGeneration) return;
    subscribed.value = false;
    await refreshRun(runId);
    if (abortController?.signal.aborted) return;
    if (isTerminal.value) {
      await options.onTerminal?.(run.value as AgentRun);
      return;
    }
    if (isAgentRunSubscribable(run.value?.status)) {
      scheduleReconnect(runId, generation, streamOk ? 900 : 2200);
    }
  };

  const create = async (params: AgentRunCreateParams): Promise<AgentRun> => {
    const created = await DihService.createAgentRun(params);
    mergeSnapshot(created);
    void subscribe(created.runId, { after: Math.max(created.lastEventSequence || 0, 0) });
    return created;
  };

  const steer = async (runId: string, params: AgentRunSteerParams): Promise<AgentRun> => {
    const updated = await DihService.steerAgentRun(runId, params);
    mergeSnapshot(updated);
    return updated;
  };

  const pause = async (reason?: string) => {
    if (!currentRunId.value) return null;
    const updated = await DihService.pauseAgentRun(currentRunId.value, reason);
    mergeSnapshot(updated);
    return updated;
  };

  const resumeRun = async (runId = currentRunId.value) => {
    if (!runId) return null;
    const updated = await DihService.resumeAgentRun(runId);
    mergeSnapshot(updated);
    void subscribe(updated.runId, { after: lastSequence.value });
    return updated;
  };

  const resume = async () => resumeRun();

  const cancelRun = async (runId = currentRunId.value) => {
    if (!runId) return null;
    const updated = await DihService.cancelAgentRun(runId);
    mergeSnapshot(updated);
    return updated;
  };

  const cancel = async () => cancelRun();

  const findActive = async (chatId: string) => {
    const page = await DihService.listAgentRuns({ chatId, page: 1, perPage: 20 });
    return page.rows.find(item => isAgentRunRestorable(item.status)) || null;
  };

  const attach = async (snapshot: AgentRun, subscribeOptions: { after?: number } = {}) => {
    mergeSnapshot(snapshot);
    void subscribe(snapshot.runId, subscribeOptions);
  };

  onUnmounted(() => {
    stopSubscription();
  });

  return {
    run,
    events,
    activityEvents,
    subscribed,
    isActive,
    isTerminal,
    currentTool,
    pendingApproval,
    lastArtifact,
    liveAnswer,
    lastSequence,
    create,
    steer,
    pause,
    resume,
    resumeRun,
    cancel,
    cancelRun,
    refreshRun,
    findActive,
    attach,
    subscribe,
    stopSubscription,
  };
};
