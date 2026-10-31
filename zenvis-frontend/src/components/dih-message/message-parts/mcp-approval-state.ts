import type { ChatMessagePart } from '@/types/type-dih';

type ApprovalTagType = 'success' | 'warning' | 'info' | 'danger';

const metadataText = (part: ChatMessagePart, key: string): string => {
  const value = part.metadata?.[key];
  return typeof value === 'string' ? value.trim() : '';
};

export const resolveMcpApprovalState = (part: ChatMessagePart) => {
  const status = String(part.status || 'pending').toLowerCase();
  const scope = metadataText(part, 'approvalScope').toLowerCase()
    || metadataText(part, 'approval_scope').toLowerCase();
  const errorSummary = metadataText(part, 'errorSummary')
    || metadataText(part, 'error_summary');

  let statusText = '';
  if (scope === 'session' && status === 'approved') statusText = '本会话已允许，等待执行';
  else if (scope === 'session' && status === 'running') statusText = '本会话已允许，执行中';
  else if (scope === 'run' && status === 'approved') statusText = '本任务已允许同类工具，等待执行';
  else if (scope === 'run' && status === 'running') statusText = '本任务已允许同类工具，执行中';
  else {
    statusText = ({
      pending: '等待审批',
      approved: '已允许，等待执行',
      running: '已允许，执行中',
      succeeded: '执行成功',
      failed: '执行失败',
      unknown: '结果未知',
      rejected: '已拒绝',
      denied: '审批规则拒绝',
      expired: '已超时',
      cancelled: '已取消',
    } as Record<string, string>)[status] || status || '等待审批';
  }

  const tagType: ApprovalTagType = status === 'succeeded'
    ? 'success'
    : status === 'failed' || status === 'rejected' || status === 'denied'
      ? 'danger'
      : status === 'pending' || status === 'approved' || status === 'running' || status === 'unknown'
        ? 'warning'
        : 'info';
  const failed = status === 'failed' || status === 'denied';
  const unknown = status === 'unknown';
  return {
    status,
    scope,
    statusText,
    tagType,
    errorSummary,
    hasError: failed || unknown || Boolean(errorSummary),
    errorTitle: unknown ? '执行结果未知' : failed ? '执行失败' : '执行信息',
    expandedByDefault: status === 'pending' || failed || unknown,
    canDecide: status === 'pending',
  };
};
