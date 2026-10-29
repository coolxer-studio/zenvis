import { nextTick } from 'vue';
import type { Ref } from 'vue';
import { ElMessage } from 'element-plus';
import { DihService } from '@/service/api';
import type { ChatMessage, ChatMessagePart } from '@/types/type-dih';
import type {
  WorkflowActionName,
  WorkflowActionResult,
} from '@/types/type-dih';
import { workflowActionForConfigDecision } from '@/components/dih-message/config-decision-actions';
import type { SendMessageOptions } from './use-chat-stream';

export type InfoStepAnswer = {
  id: string;
  title: string;
  value: string;
  source: 'suggestion' | 'custom';
};

type UseChatMessageActionsOptions = {
  chatSessionId: Ref<string>;
  chatSessionExtraData: Ref<string>;
  sendMessage: (options?: SendMessageOptions) => Promise<void>;
};

const AUTO_CONFIRM_ACTIONS = new Set([
  'analysis.create_continuous_task',
  'analysis.confirm_dataset',
  'analysis.confirm_service_result',
  'config.confirm_trial',
  'config.confirm_apply',
  'visualization_chart.confirm_query_plan',
  'visualization_app.confirm_query_plan',
  'visualization_dashboard.confirm_query_plan',
  'visualization_app.confirm_plan',
  'visualization_dashboard.confirm_plan',
  'visualization_app.confirm_overwrite',
  'visualization_dashboard.confirm_overwrite',
  'visualization_app.confirm_menu',
  'visualization_dashboard.confirm_menu',
]);

const AUTO_REJECT_ACTIONS = new Set([
  'analysis.confirm_dataset',
  'analysis.confirm_service_result',
  'config.confirm_trial',
  'config.confirm_apply',
  'visualization_chart.confirm_query_plan',
  'visualization_app.confirm_query_plan',
  'visualization_dashboard.confirm_query_plan',
  'visualization_app.confirm_plan',
  'visualization_dashboard.confirm_plan',
  'visualization_app.confirm_overwrite',
  'visualization_dashboard.confirm_overwrite',
  'visualization_app.confirm_menu',
  'visualization_dashboard.confirm_menu',
]);

const asObject = (value: unknown): Record<string, unknown> => {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
};

const textValue = (value: unknown, fallback = '') => {
  if (typeof value === 'string') {
    return value;
  }
  if (value === undefined || value === null) {
    return fallback;
  }
  return String(value);
};

const errorMessage = (error: unknown, fallback: string) => {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  const payload = asObject(error);
  const nested = asObject(payload.data);
  return textValue(
    payload.msg
      || payload.message
      || payload.error
      || nested.msg
      || nested.message
      || nested.error,
    fallback,
  );
};

const confirmAction = (part: ChatMessagePart) => {
  const action = part.metadata?.action || part.metadata?.blockedAction;
  return typeof action === 'string' ? action : '';
};

const workflowId = (part: ChatMessagePart) => textValue(part.metadata?.workflowId);
const demoId = (part: ChatMessagePart) => textValue(part.metadata?.demoId);
const metadataPathOperation = (part: ChatMessagePart) => {
  const operation = textValue(part.metadata?.operation);
  return operation === 'rename' || operation === 'delete' ? operation : '';
};

const autoConfirmMessage = (action: string) => {
  if (action === 'analysis.confirm_dataset') {
    return '我已确认当前数据集。请将分析目标、字段说明、查询条件和聚合数据提交给具备机器学习或统计分析能力的外部 MCP；若缺少合适能力，请明确说明并停止。';
  }
  if (action === 'analysis.confirm_service_result') {
    return '我已确认分析服务结果。请生成包含分析目标、分析过程和分析结论三个节点的可编辑分析报告。';
  }
  if (action === 'config.confirm_trial') {
    return '我已确认进入试验场验证。请调用 config_validate 完成通用校验，并按需调用专项验证 MCP；完成后输出 zenvis:config-record 更新验证状态，缺少能力时标记 blocked。';
  }
  if (action === 'config.confirm_apply') {
    return '我已确认将验证成功的配置正式下发。请经过高风险 MCP 审批后写入并调用 config_read 读回核验；仅在审批成功且读回一致时输出 zenvis:config-record 将 effectiveStatus 更新为 yes。';
  }
  if (action === 'analysis.create_continuous_task') {
    return '我已确认持续分析任务方案，请根据上一条确认卡和配置开始创建数据推送服务与通用数据分析任务。';
  }
  if (action === 'visualization_chart.confirm_query_plan') {
    return '我已确认上一轮 Meta 查询得到的实体、字段角色、统计口径和查询参数。请严格按该 planId 调用卡片中的数据工具；成功后仅使用真实返回的 echarts.option 生成临时图表预览，不得入库、写配置或改用示例数据。';
  }
  if (action === 'visualization_app.confirm_query_plan'
    || action === 'visualization_dashboard.confirm_query_plan') {
    return '我已确认本轮 Meta 查询方案。请严格按锁定 planId 和数据工具参数执行只读查询；查询结果只用于生成配置，不得写入临时图表库。';
  }
  if (action === 'visualization_app.confirm_plan'
    || action === 'visualization_app.confirm_overwrite') {
    return '我已确认写入锁定的可视化应用配置。请按确认卡中的目标文件和内容执行配置写入与读回校验；配置成功后再单独确认菜单发布，不得在本轮直接创建菜单，也不得创建 Dashboard。';
  }
  if (action === 'visualization_dashboard.confirm_plan'
    || action === 'visualization_dashboard.confirm_overwrite') {
    return '我已确认写入锁定的大屏配置。请按确认卡执行配置写入与读回，再由平台按锁定 Dashboard 参数创建并读回 Dashboard；配置或 Dashboard 参数变化时必须重新确认。';
  }
  if (action === 'visualization_app.confirm_menu'
    || action === 'visualization_dashboard.confirm_menu') {
    return '我已确认发布锁定菜单。请调用 menu_create 并接受高风险 MCP 审批，随后调用 menu_view 读回校验；只在读回一致时输出菜单记录。';
  }
  return '我已确认本次操作，请继续执行下一阶段。';
};

const autoRejectMessage = (action: string) => {
  if (action === 'analysis.confirm_dataset' || action === 'analysis.confirm_service_result') {
    return '我已取消当前数据分析流程，请暂停处理，不要进入下一阶段。';
  }
  if (action === 'config.confirm_trial' || action === 'config.confirm_apply') {
    return '我已取消当前配置管理流程，请暂停配置操作，不要进入下一阶段。';
  }
  if (action === 'visualization_chart.confirm_query_plan') {
    return '我取消当前临时图表查询方案。请不要调用数据查询工具，也不要生成或保存图表。';
  }
  if (action.startsWith('visualization_app.')) {
    return '我取消当前可视化应用步骤。不要创建或修改配置、Dashboard 或菜单，也不要写入临时图表库。';
  }
  if (action.startsWith('visualization_dashboard.')) {
    return '我取消当前数据大屏步骤。不要创建或修改配置、Dashboard 或菜单，也不要写入临时图表库。';
  }
  return '我已取消本次操作。';
};


const analysisConfirmReviseMessage = (action: string, detail?: string) => {
  const focus = detail?.trim() || '请基于上一阶段结果补充必要信息。';
  if (action === 'analysis.confirm_dataset') {
    return `我需要调整分析数据集。调整要求如下：\n${focus}\n请重新关联和查询实体数据，输出更新后的 zenvis:data-analysis-record，并再次展示数据集让我确认。`;
  }
  if (action === 'analysis.confirm_service_result') {
    return `我需要补充或调整分析服务任务。要求如下：\n${focus}\n请基于已确认的数据集重新调用合适的分析服务，并再次输出 zenvis:data-analysis-record 让我确认结果。`;
  }
  return focus;
};

const analysisConfirmReviseDisplayMessage = (action: string, detail?: string) => {
  const focus = detail?.trim() || '继续补充数据分析信息。';
  if (action === 'analysis.confirm_dataset') {
    return `我已补充数据集调整要求：\n${focus}`;
  }
  if (action === 'analysis.confirm_service_result') {
    return `我已补充分析服务调整要求：\n${focus}`;
  }
  return focus;
};

const configConfirmReviseMessage = (detail?: string) => {
  const focus = detail?.trim() || '请基于上一轮配置继续补充更新。';
  return `我需要调整配置。调整要求如下：\n${focus}\n请基于上一轮配置记录重新生成完整配置，并再次输出 zenvis:config-record 后让我确认是否进入试验场验证。`;
};

const configConfirmReviseDisplayMessage = (detail?: string) => {
  const focus = detail?.trim() || '继续补充配置调整要求。';
  return `我已补充配置调整要求：\n${focus}`;
};

const metadataConfigDecisionMessage = (
  decision: 'apply_config' | 'abandon' | 'revise' | 'force_approve',
  detail?: string,
  part?: ChatMessagePart,
) => {
  const operation = part ? metadataPathOperation(part) : '';
  if (operation && decision === 'apply_config') {
    const sourcePath = textValue(part?.metadata?.sourcePath, '锁定的源路径');
    if (operation === 'rename') {
      return `我已确认执行锁定的元数据重命名操作：${sourcePath} → ${textValue(part?.metadata?.targetPath)}。请只使用服务端锁定参数调用 config_rename，不得改写正文、跨目录移动或覆盖已有目标；平台完成路径核验和 Meta 严格重载后再输出摘要。`;
    }
    return `我已确认执行锁定的元数据删除操作：${sourcePath}。请只使用服务端锁定参数调用 config_delete${part?.metadata?.recursive === true ? ' 并传递 recursive=true' : ''}；不得调用 config_add/config_apply 或重写正文。平台完成路径核验和 Meta 严格重载后再输出摘要。`;
  }
  if (operation && decision === 'abandon') {
    return '我选择取消本次元数据重命名或删除操作。不要执行路径变更，也不要调用 config_add/config_apply。';
  }
  if (operation && decision === 'revise') {
    const focus = detail?.trim() || '请重新确认源路径和目标。';
    return `我需要调整本次元数据目录操作：\n${focus}\n请重新调用 config_tree(type="meta") 读取真实配置树，再输出新的元数据操作确认卡；不得执行旧目标。`;
  }
  if (decision === 'apply_config') {
    return '我已确认并授权添加上一轮已生成并展示的 meta 元数据配置到系统。本条消息就是写入授权：请不要再次询问是否添加配置。请立即按顺序调用元数据配置 MCP：1. config_tree(type="meta") 检查目标文件是否存在；2. 如果目标文件不存在，调用 config_add(type="meta", configDto={"fileName":"<目标文件名>"}) 创建文件；3. 调用 config_apply(type="meta", configDto={"fileName":"<目标文件名>","text":"<上一轮完整 meta json>"}) 写入并应用；4. 调用 config_read(type="meta", fileName="<目标文件名>") 读回校验文件确实存在且内容已写入；5. 只有在目标文件已存在且需要覆盖时，才先读取旧文件、说明差异并等待我确认覆盖。只有 MCP 返回成功且读回校验通过后，才用 Markdown 围栏代码块输出 zenvis:meta-config-record 记录；zenvis:meta-config-record 不是工具名，请不要调用它。';
  }
  if (decision === 'abandon') {
    return '我选择放弃本次元数据配置。请记录本次配置已放弃，不要写入系统，也不要继续创建或更新相关配置。';
  }
  if (decision === 'force_approve') {
    return '我已明确知悉校验风险并确认强制写入上一轮锁定的元数据配置。请只使用工作流锁定候选调用 config_apply，并传入 force=true；写入后必须执行真实 Meta 加载与 config_read 读回，失败时不得输出成功记录。';
  }
  const focus = detail?.trim() || '请基于上一轮配置继续优化字段、实体或展示规则。';
  return `我需要补充信息继续更新元数据配置。调整要求如下：\n${focus}\n请基于上一轮 meta 配置重新生成完整配置，并再次展示完整配置和后续选择。`;
};

const metadataConfigDecisionDisplayMessage = (
  decision: 'apply_config' | 'abandon' | 'revise' | 'force_approve',
  detail?: string,
  part?: ChatMessagePart,
) => {
  const operation = part ? metadataPathOperation(part) : '';
  if (operation && decision === 'apply_config') {
    return operation === 'rename' ? '我已确认执行元数据重命名。' : '我已确认执行元数据删除。';
  }
  if (operation && decision === 'abandon') {
    return '我已取消本次元数据目录操作。';
  }
  if (operation && decision === 'revise') {
    return `我已补充元数据目录操作调整要求：\n${detail?.trim() || '重新确认源路径和目标。'}`;
  }
  if (decision === 'apply_config') {
    return '我已确认添加配置到系统。';
  }
  if (decision === 'abandon') {
    return '我已放弃本次元数据配置。';
  }
  if (decision === 'force_approve') {
    return '我已确认忽略校验并强制写入。';
  }
  const focus = detail?.trim() || '继续优化元数据配置。';
  return `我已补充配置调整要求：\n${focus}`;
};

const dataPushDecisionMessage = (
  decision: 'apply_config' | 'abandon' | 'revise',
  detail?: string,
) => {
  if (decision === 'apply_config') {
    return '我已确认创建并启动上一轮展示的数据推送服务配置。当前只允许读取已发布 Meta，不得调用 config_add、config_apply 或任何修改 Meta 的工具。请调用 push_task_detect_format，按锁定 sourceMark 检查冲突，再调用 push_task_create_and_start 并接受平台 MCP 审批；审批通过后必须查询任务状态和 system 日志，仅在任务真实运行成功后输出 zenvis:vectum-task-record。';
  }
  if (decision === 'abandon') {
    return '我已取消创建本次数据推送服务。不要创建或启动 PushTask，也不要调用任何写工具。';
  }
  const focus = detail?.trim() || '请基于上一轮完整数据推送配置继续调整。';
  return `我需要补充信息继续更新数据推送配置。调整要求如下：\n${focus}\n请保留已确认的只读 Meta 引用，重新展示完整 PushTask/Vectum 配置并再次让我确认；不得创建或修改 Meta。`;
};

const dataPushDecisionDisplayMessage = (
  decision: 'apply_config' | 'abandon' | 'revise',
  detail?: string,
) => {
  if (decision === 'apply_config') {
    return '我已确认创建并启动数据推送服务。';
  }
  if (decision === 'abandon') {
    return '我已取消创建本次数据推送服务。';
  }
  const focus = detail?.trim() || '继续优化数据推送配置。';
  return `我已补充数据推送配置调整要求：\n${focus}`;
};

type VisualizationDomain = 'chart' | 'app' | 'dashboard';

const visualizationDomainLabel = (domain: VisualizationDomain) => {
  if (domain === 'chart') return '临时图表';
  if (domain === 'app') return '可视化应用';
  return '数据大屏';
};

const visualizationDomainFromAction = (action: string): VisualizationDomain | undefined => {
  if (action.startsWith('visualization_chart.')) return 'chart';
  if (action.startsWith('visualization_app.')) return 'app';
  if (action.startsWith('visualization_dashboard.')) return 'dashboard';
  return undefined;
};

const isWorkflowOwnedAction = (action: string) =>
  action.startsWith('metadata_config.')
  || action.startsWith('data_push.')
  || Boolean(visualizationDomainFromAction(action));

const visualizationDecisionMessage = (
  domain: VisualizationDomain,
  decision: 'apply_config' | 'abandon' | 'revise',
  detail?: string,
) => {
  const label = visualizationDomainLabel(domain);
  if (decision === 'apply_config') {
    if (domain === 'chart') {
      return '我已确认并授权按上一轮查询方案生成临时图表。请执行锁定查询并仅使用真实 echarts.option 生成预览，不要写入图表库、配置、Dashboard 或菜单。';
    }
    if (domain === 'app') {
      return '我已确认并授权应用上一轮可视化应用配置。请按锁定目标文件和内容写入配置并读回；配置成功后单独请求菜单发布确认，不要创建 Dashboard，也不要写入临时图表库。';
    }
    return '我已确认并授权应用上一轮数据大屏配置。请按锁定目标文件和内容写入配置并读回，再按锁定参数创建并读回 Dashboard；菜单发布必须单独确认，不要写入临时图表库。';
  }
  if (decision === 'abandon') {
    return `我选择放弃本次${label}配置。请记录本次操作已放弃，不要执行任何写入或创建动作。`;
  }
  const focus = detail?.trim() || `请基于上一轮${label}方案继续调整。`;
  return `我需要补充信息继续更新${label}配置。调整要求如下：\n${focus}\n请重新校验 Meta 并生成新的完整方案与确认卡；Meta 摘要变化时必须重新确认。`;
};

const visualizationDecisionDisplayMessage = (
  domain: VisualizationDomain,
  decision: 'apply_config' | 'abandon' | 'revise',
  detail?: string,
) => {
  const label = visualizationDomainLabel(domain);
  if (decision === 'apply_config') return `我已确认${label}当前步骤。`;
  if (decision === 'abandon') return `我已取消${label}当前步骤。`;
  return `我已补充${label}调整要求：\n${detail?.trim() || '继续调整当前方案。'}`;
};

const infoStepsDisplayMessage = (part: ChatMessagePart, answers: InfoStepAnswer[]) => {
  const title = part.title || '需要补充信息';
  if (!answers.length) {
    return `我已补充「${title}」所需信息。`;
  }
  return `我已补充以下信息：\n${answers.map(answer => `- ${answer.title}：${answer.value}`).join('\n')}`;
};

const infoStepsRequestMessage = (part: ChatMessagePart, answers: InfoStepAnswer[]) => {
  return [
    '我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。',
    '',
    JSON.stringify({
      title: part.title || '需要补充信息',
      content: part.content || '',
      answers,
    }, null, 2),
  ].join('\n');
};

export const useChatMessageActions = ({
  chatSessionId,
  chatSessionExtraData,
  sendMessage,
}: UseChatMessageActionsOptions) => {
  const continueWorkflow = async (result: WorkflowActionResult) => {
    if (result.extraData) {
      chatSessionExtraData.value = result.extraData;
    }
    const display = textValue(result.continuation?.display);
    const requestContent = textValue(result.continuation?.request);
    if (!display && !requestContent) {
      return;
    }
    await nextTick();
    try {
      await sendMessage({
        content: display || requestContent,
        requestContent: requestContent || display,
      });
    } catch (error) {
      console.error('工作流动作已生效，但自动续跑失败:', error);
      ElMessage.warning(errorMessage(
        error,
        '操作已生效，但自动续跑失败，请发送“继续”恢复任务',
      ));
    }
  };

  const performWorkflowAction = async (
    message: ChatMessage,
    part: ChatMessagePart,
    action: WorkflowActionName,
    options: {
      answers?: InfoStepAnswer[];
      revision?: string;
    } = {},
  ) => {
    const id = workflowId(part);
    if (!chatSessionId.value || !message.id || !part.id || !id) {
      throw new Error('缺少工作流动作定位信息');
    }
    if (part.metadata?.deciding === true) {
      throw new Error('工作流动作正在处理中，请稍候');
    }
    part.metadata = {
      ...(part.metadata || {}),
      deciding: true,
      decisionInFlight: action,
    };
    try {
      const result = await DihService.workflowAction({
        chat_id: chatSessionId.value,
        message_id: message.id,
        part_id: part.id,
        workflow_id: id,
        action,
        answers: options.answers,
        revision: options.revision,
      });
      if (!result.accepted) {
        throw new Error('工作流动作未被受理，请刷新后重试');
      }
      if (!result.partStatus) {
        throw new Error('工作流动作未返回卡片状态，请刷新后重试');
      }
      part.status = result.partStatus;
      await nextTick();
      await continueWorkflow(result);
      return result;
    } finally {
      part.metadata = {
        ...(part.metadata || {}),
        deciding: false,
        decisionInFlight: '',
      };
    }
  };

  const handleInfoStepsSubmit = async (
    message: ChatMessage,
    payload: { part: ChatMessagePart; answers: InfoStepAnswer[] },
  ) => {
    if (!chatSessionId.value || !message.id || !payload.part.id) {
      ElMessage.warning('缺少补充信息卡片标识，无法记录提交结果');
      return;
    }

    if (workflowId(payload.part)) {
      try {
        await performWorkflowAction(message, payload.part, 'submit', {
          answers: payload.answers,
        });
        ElMessage.success('已提交补充信息');
      } catch (error) {
        console.error('提交工作流补充信息失败:', error);
        ElMessage.error(errorMessage(error, '提交补充信息失败'));
      }
      return;
    }
    const action = confirmAction(payload.part);
    if (
      !demoId(payload.part)
      && isWorkflowOwnedAction(action)
    ) {
      ElMessage.error('普通业务卡片缺少共享工作流标识，请重新发起流程');
      return;
    }

    try {
      await DihService.recordActionDecision({
        chat_id: chatSessionId.value,
        message_id: message.id,
        part_id: payload.part.id,
        decision: 'submitted',
      });
    } catch (error) {
      console.error('记录补充信息提交失败:', error);
    }
    payload.part.status = 'submitted';
    ElMessage.success('已提交补充信息');
    await nextTick();
    await sendMessage({
      content: infoStepsDisplayMessage(payload.part, payload.answers),
      requestContent: infoStepsRequestMessage(payload.part, payload.answers),
    });
  };

  const handleActionDecision = async (
    message: ChatMessage,
    payload: {
      part: ChatMessagePart;
      decision: 'approved' | 'rejected' | 'revise' | 'retry';
      detail?: string;
    },
  ) => {
    if (!chatSessionId.value || !message.id || !payload.part.id) {
      ElMessage.warning('缺少确认记录标识，无法记录操作结果');
      return;
    }

    if (workflowId(payload.part)) {
      const actionMap: Record<typeof payload.decision, WorkflowActionName> = {
        approved: 'approve',
        rejected: 'reject',
        revise: 'revise',
        retry: 'retry',
      };
      try {
        await performWorkflowAction(
          message,
          payload.part,
          actionMap[payload.decision],
          { revision: payload.detail },
        );
        ElMessage.success(
          payload.decision === 'approved'
            ? '已确认执行'
            : payload.decision === 'rejected'
              ? '已取消操作'
              : payload.decision === 'retry'
                ? '已开始重试'
                : '已提交调整要求',
        );
      } catch (error) {
        console.error('执行工作流确认动作失败:', error);
        ElMessage.error(errorMessage(error, '执行工作流动作失败'));
      }
      return;
    }

    if (payload.decision === 'retry') {
      ElMessage.warning('历史卡片不支持工作流重试，请重新发起查询');
      return;
    }

    const action = confirmAction(payload.part);
    if (
      !demoId(payload.part)
      && isWorkflowOwnedAction(action)
    ) {
      ElMessage.error('普通业务卡片缺少共享工作流标识，请重新发起流程');
      return;
    }

    try {
      await DihService.recordActionDecision({
        chat_id: chatSessionId.value,
        message_id: message.id,
        part_id: payload.part.id,
        decision: payload.decision,
      });
    } catch (error) {
      console.error('记录确认结果失败:', error);
    }
    payload.part.status = payload.decision;
    if (payload.decision === 'revise' && action.endsWith('.confirm_query_plan')) {
      ElMessage.success('已提交查询方案调整要求');
      await nextTick();
      const detail = payload.detail?.trim() || '请重新选择实体、字段角色或统计口径。';
      await sendMessage({
        content: `我已补充查询方案调整要求：\n${detail}`,
        requestContent: `我需要调整上一轮可视化查询方案：\n${detail}\n请重新调用实体和字段 Meta MCP 核验，并输出新的 ${action} 确认卡；本轮不要调用数据工具，也不得复用旧查询结果。`,
      });
      return;
    }
    if (payload.decision === 'revise' && (
      action.endsWith('.confirm_plan')
      || action.endsWith('.confirm_overwrite')
      || action.endsWith('.confirm_menu')
    )) {
      const domain = visualizationDomainFromAction(action);
      if (domain) {
        ElMessage.success('已提交调整要求');
        await nextTick();
        await sendMessage({
          content: visualizationDecisionDisplayMessage(domain, 'revise', payload.detail),
          requestContent: visualizationDecisionMessage(domain, 'revise', payload.detail),
        });
        return;
      }
    }
    if (payload.decision === 'revise' && (
      action === 'analysis.confirm_dataset'
      || action === 'analysis.confirm_service_result'
    )) {
      ElMessage.success('已提交补充信息');
      await nextTick();
      await sendMessage({
        content: analysisConfirmReviseDisplayMessage(action, payload.detail),
        requestContent: analysisConfirmReviseMessage(action, payload.detail),
      });
      return;
    }
    if (payload.decision === 'revise' && (
      action === 'config.confirm_trial'
      || action === 'config.confirm_apply'
    )) {
      ElMessage.success('已提交配置调整要求');
      await nextTick();
      await sendMessage({
        content: configConfirmReviseDisplayMessage(payload.detail),
        requestContent: configConfirmReviseMessage(payload.detail),
      });
      return;
    }
    ElMessage.success(payload.decision === 'approved' ? '已确认执行' : '已取消操作');
    if (payload.decision === 'approved' && AUTO_CONFIRM_ACTIONS.has(action)) {
      await nextTick();
      await sendMessage({ content: autoConfirmMessage(action) });
    } else if (payload.decision === 'rejected' && AUTO_REJECT_ACTIONS.has(action)) {
      await nextTick();
      await sendMessage({ content: autoRejectMessage(action) });
    }
  };

  const handleAddChartLibrary = async (message: ChatMessage, part: ChatMessagePart) => {
    const action = confirmAction(part);
    if (action !== 'visualization_chart.add_chart_library') {
      ElMessage.warning('当前图表不支持加入图表库');
      return;
    }
    if (part.status === 'submitted' || part.status === 'added') {
      ElMessage.info('该图表已加入图表库');
      return;
    }
    if (demoId(part)) {
      const previousStatus = part.status;
      try {
        part.status = 'submitted';
        await nextTick();
        await sendMessage({
          content: '我已确认把上一轮临时图表加入图表库。',
          requestContent: '我已确认把上一轮临时图表加入图表库。',
        });
      } catch (error) {
        part.status = previousStatus;
        ElMessage.error(errorMessage(error, '演示图表加入图表库失败'));
      }
      return;
    }
    const metadata = asObject(part.metadata);
    const query = asObject(metadata.query);
    const echartsOption = asObject(metadata.echartsOption);
    if (textValue(metadata.validationStatus) !== 'success'
      || !textValue(metadata.planId)
      || !textValue(query.tool)
      || Object.keys(asObject(query.request)).length === 0
      || Object.keys(echartsOption).length === 0) {
      ElMessage.warning('当前预览尚未通过真实查询验证，不能加入图表库');
      return;
    }
    if (workflowId(part)) {
      try {
        await performWorkflowAction(message, part, 'add_to_library');
        ElMessage.success('已加入图表库');
      } catch (error) {
        console.error('服务端加入图表库失败:', error);
        ElMessage.error(error instanceof Error ? error.message : '加入图表库失败');
      }
      return;
    }
    ElMessage.error('普通图表缺少共享工作流标识，不能加入图表库');
  };

  type LegacyConfigDecision = 'apply_config' | 'abandon' | 'revise' | 'retry';
  type MetadataConfigDecision = LegacyConfigDecision | 'force_approve';
  type ConfigDecisionPayload = {
    part: ChatMessagePart;
    decision: LegacyConfigDecision;
    detail?: string;
  };
  type MetadataConfigDecisionPayload = Omit<ConfigDecisionPayload, 'decision'> & {
    decision: MetadataConfigDecision;
  };

  const handleConfigDecision = async (
    message: ChatMessage,
    payload: ConfigDecisionPayload | MetadataConfigDecisionPayload,
    domain: 'metadata' | 'push',
  ) => {
    const decision = payload.decision;
    const domainLabel = domain === 'metadata' ? '元数据配置' : '数据推送服务';
    if (!chatSessionId.value || !message.id || !payload.part.id) {
      ElMessage.warning(`缺少${domainLabel}选择记录标识，无法记录操作结果`);
      return;
    }

    if (workflowId(payload.part)) {
      if (domain !== 'metadata' && decision === 'force_approve') {
        ElMessage.error('只有元数据配置候选支持强制写入');
        return;
      }
      try {
        await performWorkflowAction(
          message,
          payload.part,
          workflowActionForConfigDecision(decision),
          { revision: payload.detail },
        );
        const operation = domain === 'metadata'
          ? metadataPathOperation(payload.part) : '';
        ElMessage.success(
          decision === 'apply_config'
            ? domain === 'push'
              ? '已确认创建数据推送服务'
              : operation === 'rename'
                ? '已确认重命名，正在执行路径核验与 Meta 重载'
                : operation === 'delete'
                  ? '已确认删除，正在执行路径核验与 Meta 重载'
                  : '已确认应用元数据配置'
            : decision === 'force_approve'
              ? '已强制确认写入，正在执行真实加载与读回'
            : decision === 'abandon'
              ? `已取消当前${domainLabel}流程`
              : decision === 'retry'
                ? '已开始重试当前阶段'
                : '已提交配置调整要求',
        );
      } catch (error) {
        console.error(`执行${domainLabel}工作流动作失败:`, error);
        ElMessage.error(errorMessage(error, `执行${domainLabel}工作流失败`));
      }
      return;
    }

    if (decision === 'retry' || decision === 'force_approve') {
      ElMessage.error('只有共享工作流卡片支持重试或强制写入');
      return;
    }

    if (!demoId(payload.part)) {
      ElMessage.error(`普通${domainLabel}卡片缺少共享工作流标识，请重新发起流程`);
      return;
    }

    try {
      await DihService.recordActionDecision({
        chat_id: chatSessionId.value,
        message_id: message.id,
        part_id: payload.part.id,
        decision,
      });
    } catch (error) {
      console.error(`记录${domainLabel}后续选择失败:`, error);
    }
    payload.part.status = decision;
    const toastMap = domain === 'push'
      ? {
          apply_config: '已确认创建数据推送服务',
          abandon: '已取消创建数据推送服务',
          revise: '已提交数据推送配置调整要求',
        }
      : {
          apply_config: '已选择添加配置到系统',
          abandon: '已放弃本次配置',
          revise: '已提交配置调整要求',
        };
    ElMessage.success(toastMap[decision]);
    await nextTick();
    await sendMessage({
      content: domain === 'push'
        ? dataPushDecisionDisplayMessage(decision, payload.detail)
        : metadataConfigDecisionDisplayMessage(decision, payload.detail, payload.part),
      requestContent: domain === 'push'
        ? dataPushDecisionMessage(decision, payload.detail)
        : metadataConfigDecisionMessage(decision, payload.detail, payload.part),
    });
  };

  const handleMetadataConfigDecision = (message: ChatMessage, payload: MetadataConfigDecisionPayload) =>
    handleConfigDecision(message, payload, 'metadata');

  const handleDataPushDecision = (message: ChatMessage, payload: ConfigDecisionPayload) =>
    handleConfigDecision(message, payload, 'push');

  const handleVisualizationDecision = async (
    message: ChatMessage,
    payload: ConfigDecisionPayload,
    domain: VisualizationDomain,
  ) => {
    const label = visualizationDomainLabel(domain);
    if (!chatSessionId.value || !message.id || !payload.part.id) {
      ElMessage.warning(`缺少${label}选择记录标识，无法记录操作结果`);
      return;
    }

    if (workflowId(payload.part)) {
      const actionMap: Record<typeof payload.decision, WorkflowActionName> = {
        apply_config: 'approve',
        abandon: 'reject',
        revise: 'revise',
        retry: 'retry',
      };
      try {
        await performWorkflowAction(
          message,
          payload.part,
          actionMap[payload.decision],
          { revision: payload.detail },
        );
        ElMessage.success(
          payload.decision === 'apply_config'
            ? `已确认${label}当前步骤`
            : payload.decision === 'abandon'
              ? `已取消${label}当前步骤`
              : payload.decision === 'retry'
                ? '已开始重试当前阶段'
                : '已提交调整要求',
        );
      } catch (error) {
        console.error(`执行${label}工作流动作失败:`, error);
        ElMessage.error(errorMessage(error, `执行${label}工作流失败`));
      }
      return;
    }

    if (payload.decision === 'retry') {
      ElMessage.error('只有共享工作流阻塞卡支持重试');
      return;
    }
    if (!demoId(payload.part)) {
      ElMessage.error(`普通${label}卡片缺少共享工作流标识，请重新发起流程`);
      return;
    }

    try {
      await DihService.recordActionDecision({
        chat_id: chatSessionId.value,
        message_id: message.id,
        part_id: payload.part.id,
        decision: payload.decision,
      });
    } catch (error) {
      console.error(`记录${label}选择失败:`, error);
    }
    payload.part.status = payload.decision;
    ElMessage.success(
      visualizationDecisionDisplayMessage(domain, payload.decision, payload.detail),
    );
    await nextTick();
    await sendMessage({
      content: visualizationDecisionDisplayMessage(domain, payload.decision, payload.detail),
      requestContent: visualizationDecisionMessage(domain, payload.decision, payload.detail),
    });
  };

  const handleVisualizationChartDecision = (
    message: ChatMessage,
    payload: ConfigDecisionPayload,
  ) => handleVisualizationDecision(message, payload, 'chart');

  const handleVisualizationAppDecision = (
    message: ChatMessage,
    payload: ConfigDecisionPayload,
  ) => handleVisualizationDecision(message, payload, 'app');

  const handleVisualizationDashboardDecision = (
    message: ChatMessage,
    payload: ConfigDecisionPayload,
  ) => handleVisualizationDecision(message, payload, 'dashboard');

  const handleChartRenderFailure = async (
    payload: { part: ChatMessagePart; error: string },
  ) => {
    const id = workflowId(payload.part);
    if (!chatSessionId.value || !id) return;
    try {
      await DihService.workflowTelemetry({
        chat_id: chatSessionId.value,
        workflow_id: id,
        event: 'chart_render_failed',
        detail: payload.error.slice(0, 500),
      });
    } catch (error) {
      console.debug('记录图表渲染失败指标未成功:', error);
    }
  };

  return {
    handleInfoStepsSubmit,
    handleActionDecision,
    handleAddChartLibrary,
    handleMetadataConfigDecision,
    handleDataPushDecision,
    handleVisualizationChartDecision,
    handleVisualizationAppDecision,
    handleVisualizationDashboardDecision,
    handleChartRenderFailure,
  };
};
