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

const shortDigest = (value: unknown): string => {
  const text = asText(value);
  return text.length <= 12 ? text : `${text.slice(0, 12)}…`;
};

const RESOLVED_CARD_STATUSES = new Set([
  'approved', 'force_approved', 'rejected', 'revise', 'cancelled',
  'completed', 'saved', 'submitted', 'added',
]);

const blockingReason = (
  code: string,
  category: string,
  message: string,
  repairHint: string,
  expected = '',
  actual = '',
): DataPushBlockingReasonView => ({
  code,
  category,
  message,
  expected,
  actual,
  repairHint,
});

const synthesizeBlockingReasons = (
  metadata: JsonRecord,
  validation: JsonRecord,
  validationPassed: boolean,
  gateBlocked: boolean,
): DataPushBlockingReasonView[] => {
  if (!gateBlocked) return [];
  const reasons: DataPushBlockingReasonView[] = [];
  const candidateDigest = asText(metadata.candidateDigest);
  const validationCandidateDigest = asText(validation.candidateDigest);
  const metaDigest = asText(metadata.metaRef && typeof metadata.metaRef === 'object'
    ? (metadata.metaRef as JsonRecord).contentDigest
    : '');
  const validationMetaDigest = asText(validation.metaDigest);
  if (candidateDigest && validationCandidateDigest && candidateDigest !== validationCandidateDigest) {
    reasons.push(blockingReason(
      'PUSH_CANDIDATE_DIGEST_MISMATCH',
      'candidate',
      '卡片校验对应的候选与平台锁定候选不一致。',
      '使用锁定候选重新校验并生成新的确认卡。',
      shortDigest(candidateDigest),
      shortDigest(validationCandidateDigest),
    ));
  }
  if (metaDigest && validationMetaDigest && metaDigest !== validationMetaDigest) {
    reasons.push(blockingReason(
      'PUSH_META_DIGEST_MISMATCH',
      'meta',
      '卡片校验对应的 Meta 与平台锁定 Meta 不一致。',
      '重新读取最新 Meta 后重新校验。',
      shortDigest(metaDigest),
      shortDigest(validationMetaDigest),
    ));
  }
  const evidence = Array.isArray(metadata.evidenceRefs) ? metadata.evidenceRefs : [];
  const evidenceTools = new Set(evidence.map(item => asText(asRecord(item).tool)));
  const requiredTools = [
    ['config_tree', '读取 Meta 配置树'],
    ['config_read', '读取已发布 Meta 原文'],
    ['push_task_validate_config', '执行配置校验'],
  ] as const;
  for (const [tool, action] of requiredTools) {
    if (!evidenceTools.has(tool)) {
      reasons.push(blockingReason(
        'PUSH_EVIDENCE_MISSING',
        'evidence',
        `缺少“${action}”的成功工具证据（${tool}）。`,
        '重新执行该只读校验或配置校验，再生成确认卡。',
      ));
    }
  }
  if (reasons.length) return reasons;
  if (validation.blocked === true) {
    return [blockingReason(
      'PUSH_VALIDATION_UNAVAILABLE',
      'validation',
      'Vector 配置校验服务不可用，平台按 fail-closed 处理。',
      '恢复校验服务后重新校验；不得跳过门禁。',
    )];
  }
  if (!validationPassed && Object.keys(validation).length > 0) {
    return [blockingReason(
      'PUSH_VALIDATION_FAILED',
      'validation',
      '配置未通过 Vector 原生校验。',
      '按诊断项的路径和修复建议修订完整配置后重新校验。',
    )];
  }
  return [blockingReason(
    'PUSH_WORKFLOW_GATE_BLOCKED',
    'workflow',
    '平台门禁阻断，当前卡片的校验事实尚不能证明锁定候选可执行。',
    '使用锁定候选重新校验并生成新的确认卡。',
  )];
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
  const rawBlockingReasons = Array.isArray(metadata.blockingReasons)
    ? metadata.blockingReasons
    : [];
  const blockingReasons = rawBlockingReasons.length
    ? rawBlockingReasons
      .map(asRecord)
      .filter(item => Object.keys(item).length > 0)
      .map(item => ({
        code: asText(item.code),
        category: asText(item.category),
        message: asText(item.message),
        expected: asText(item.expected),
        actual: asText(item.actual),
        repairHint: asText(item.repairHint),
      }))
    : synthesizeBlockingReasons(metadata, validation, validationPassed, gateBlocked);
  const resolved = RESOLVED_CARD_STATUSES.has(partStatus);
  const workflowOwned = Boolean(asText(metadata.workflowId));
  const actions = asStringList(metadata.allowedActions).length
    ? asStringList(metadata.allowedActions)
    : asStringList(metadata.actions);
  const allows = (action: string) => !workflowOwned || actions.includes(action);
  const requiresGate = workflowOwned || blockingReasons.length > 0;
  const gatePassed = !requiresGate || (!gateBlocked && blockingReasons.length === 0);
  const canApprove = !resolved && allows('approve') && validationPassed && gatePassed;
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
