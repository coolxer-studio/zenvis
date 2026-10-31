type JsonRecord = Record<string, unknown>;

export type DataPushDiagnosticView = {
  code: string;
  severity: string;
  source: string;
  path: string;
  message: string;
  repairHint: string;
};

export type DataPushBlockingReasonView = {
  code: string;
  category: string;
  message: string;
  expected: string;
  actual: string;
  repairHint: string;
};

export type DataPushTestView = {
  present: boolean;
  total: number;
  passed: number;
  failed: number;
};

const asRecord = (value: unknown): JsonRecord =>
  value && typeof value === 'object' && !Array.isArray(value)
    ? (value as JsonRecord)
    : {};

const asNumber = (value: unknown): number => {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
};

const asText = (value: unknown): string => String(value ?? '');

const asStringList = (value: unknown): string[] => Array.isArray(value)
  ? value.map(item => asText(item).trim()).filter(Boolean)
  : [];

const RESOLVED_CARD_STATUSES = new Set([
  'approved', 'force_approved', 'rejected', 'revise', 'cancelled',
  'completed', 'saved', 'submitted', 'added',
]);

const normalizeBlockingReasons = (
  metadata: JsonRecord,
  gateBlocked: boolean,
): DataPushBlockingReasonView[] => {
  const rawReasons = Array.isArray(metadata.blockingReasons)
    ? metadata.blockingReasons
    : [];
  const reasons: DataPushBlockingReasonView[] = [];
  const seen = new Set<string>();
  for (const raw of rawReasons) {
    const item = asRecord(raw);
    const code = asText(item.code).trim();
    if (!code || seen.has(code)) continue;
    seen.add(code);
    reasons.push({
      code,
      category: asText(item.category),
      message: asText(item.message),
      expected: asText(item.expected),
      actual: asText(item.actual),
      repairHint: asText(item.repairHint),
    });
  }
  if (reasons.length) return reasons;
  if (!gateBlocked) return [];
  return [{
    code: 'PUSH_WORKFLOW_GATE_BLOCKED',
    category: 'workflow',
    message: '平台门禁阻断，当前方案尚不能执行。',
    expected: '',
    actual: '',
    repairHint: '请重新校验或修订配置后再继续。',
  }];
};

export const resolveDataPushCardState = (metadataValue: unknown, partStatusValue?: unknown) => {
  const metadata = asRecord(metadataValue);
  const validation = asRecord(metadata.validation);
  const testRecord = asRecord(validation.tests ?? metadata.tests);
  const tests: DataPushTestView = {
    present: Boolean(testRecord.present),
    total: asNumber(testRecord.total),
    passed: asNumber(testRecord.passed),
    failed: asNumber(testRecord.failed),
  };
  const rawDiagnostics = Array.isArray(metadata.diagnostics)
    ? metadata.diagnostics
    : Array.isArray(validation.diagnostics)
      ? validation.diagnostics
      : [];
  const diagnostics: DataPushDiagnosticView[] = rawDiagnostics
    .map(asRecord)
    .filter(item => Object.keys(item).length > 0)
    .map(item => ({
      code: asText(item.code),
      severity: asText(item.severity),
      source: asText(item.source),
      path: asText(item.path) || '$',
      message: asText(item.message),
      repairHint: asText(item.repairHint),
    }));
  const partStatus = asText(partStatusValue).toLowerCase();
  const configValidationFailed =
    validation.blocked === true
    || (Object.keys(validation).length > 0 && validation.passed !== true);
  const validationPassed =
    validation.passed === true && validation.blocked !== true;
  const gateBlocked =
    asText(metadata.validationStatus) === 'blocked'
    || asText(metadata.step) === 'BLOCKED'
    || partStatus === 'blocked';
  const blockingReasons = normalizeBlockingReasons(metadata, gateBlocked);
  const resolved = RESOLVED_CARD_STATUSES.has(partStatus);
  const workflowOwned = Boolean(asText(metadata.workflowId));
  const actions = asStringList(metadata.allowedActions).length
    ? asStringList(metadata.allowedActions)
    : asStringList(metadata.actions);
  const allows = (action: string) => !workflowOwned || actions.includes(action);
  const requiresGate = workflowOwned || blockingReasons.length > 0;
  const gatePassed = !requiresGate || (!gateBlocked && blockingReasons.length === 0);
  const canApprove = !resolved && gatePassed
    && (workflowOwned ? allows('approve') : validationPassed);
  const canReject = !resolved && allows('reject');
  const canRevise = !resolved && allows('revise');
  const canRetry = !resolved && workflowOwned && allows('retry');
  const hasAvailableAction = canApprove || canReject || canRevise || canRetry;
  const statusTagType = configValidationFailed || gateBlocked || blockingReasons.length
    ? 'danger'
    : validationPassed ? 'success' : 'info';
  const statusText = configValidationFailed
    ? (validation.blocked === true ? '校验服务不可用' : '校验未通过')
    : gateBlocked || blockingReasons.length
      ? '门禁未通过'
      : validationPassed
        ? '校验通过'
        : '待校验';
  return {
    validation,
    tests,
    diagnostics,
    blockingReasons,
    repairRound: asNumber(metadata.repairRound ?? validation.repairRound),
    logExcerpt: asText(metadata.logExcerpt),
    vectorVersion: asText(validation.vectorVersion ?? metadata.vectorVersion),
    validationPassed,
    validationFailed: configValidationFailed,
    gateBlocked,
    gatePassed,
    resolved,
    canApprove,
    canReject,
    canRevise,
    canRetry,
    hasAvailableAction,
    actionable: !resolved && hasAvailableAction,
    statusTagType,
    statusText,
  };
};

export const resolveDataPushRecordState = (metadataValue: unknown) => {
  const metadata = asRecord(metadataValue);
  const card = resolveDataPushCardState(metadata);
  const taskStatus = asText(metadata.taskStatus ?? metadata.status);
  const taskId = asText(metadata.taskId);
  const runId = asText(metadata.runId);
  const validationDigest = asText(metadata.validationDigest);
  const running = taskStatus === 'running';
  const verified = running && Boolean(taskId && runId && validationDigest);
  return {
    ...card,
    taskId,
    runId,
    taskStatus,
    validationDigest,
    verified,
    statusTagType: verified ? 'success' : running ? 'warning' : 'danger',
    statusText: verified ? '运行已验证' : running ? '待运行验证' : '运行异常',
  };
};
