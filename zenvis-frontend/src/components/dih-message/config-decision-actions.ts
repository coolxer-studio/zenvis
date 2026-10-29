export type ConfigCardDecision =
  | 'apply_config'
  | 'abandon'
  | 'revise'
  | 'retry'
  | 'force_approve';

export type WorkflowCardAction =
  | 'submit'
  | 'approve'
  | 'force_approve'
  | 'reject'
  | 'revise'
  | 'retry'
  | 'add_to_library';

export type MetadataPathOperation = 'rename' | 'delete';

export const workflowActionForConfigDecision = (
  decision: ConfigCardDecision,
): WorkflowCardAction => {
  const actions: Record<ConfigCardDecision, WorkflowCardAction> = {
    apply_config: 'approve',
    abandon: 'reject',
    revise: 'revise',
    retry: 'retry',
    force_approve: 'force_approve',
  };
  return actions[decision];
};

export const metadataValidationFailed = (
  metadata: Record<string, unknown> | undefined,
): boolean => metadata?.validationStatus === 'failed';

export const metadataForceApproveAllowed = (
  metadata: Record<string, unknown> | undefined,
): boolean => {
  const actions = metadata?.allowedActions;
  return metadata?.validationStatus === 'failed'
    && metadata?.forceAllowed === true
    && Array.isArray(actions)
    && actions.map(String).includes('force_approve');
};

export const metadataValidationMessages = (
  metadata: Record<string, unknown> | undefined,
): string[] => {
  const raw = metadata?.validationErrors;
  if (!Array.isArray(raw)) return [];
  return raw
    .map(item => {
      if (typeof item === 'string') return item.trim();
      if (!item || typeof item !== 'object') return '';
      const value = item as Record<string, unknown>;
      const path = typeof value.path === 'string' ? value.path.trim() : '';
      const message = typeof value.message === 'string' ? value.message.trim() : '';
      const code = typeof value.code === 'string' ? value.code.trim() : '';
      const detail = [path, message].filter(Boolean).join('：') || code;
      return code && detail ? `[${code}] ${detail}` : detail;
    })
    .filter(Boolean);
};

export const metadataPathOperation = (
  metadata: Record<string, unknown> | undefined,
): MetadataPathOperation | undefined => {
  const operation = metadata?.operation;
  return operation === 'rename' || operation === 'delete' ? operation : undefined;
};

export const metadataPathOperationApproveLabel = (
  metadata: Record<string, unknown> | undefined,
): string => {
  const operation = metadataPathOperation(metadata);
  if (operation === 'rename') return '确认重命名';
  if (operation === 'delete') {
    return metadata?.recursive === true ? '确认递归删除' : '确认删除';
  }
  return metadata?.overwrite === true ? '确认覆盖配置' : '添加配置到系统';
};

export const metadataPathOperationRejectLabel = (
  metadata: Record<string, unknown> | undefined,
): string => metadataPathOperation(metadata) ? '取消本次操作' : '放弃本次配置';

export const metadataPathOperationAffectedFiles = (
  metadata: Record<string, unknown> | undefined,
): string[] => {
  const raw = metadata?.affectedFiles ?? metadata?.affectedPaths;
  if (!Array.isArray(raw)) return [];
  return raw.map(item => String(item || '').trim()).filter(Boolean);
};
