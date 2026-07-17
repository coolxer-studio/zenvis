import { nextTick } from 'vue';
import type { Ref } from 'vue';
import { ElMessage } from 'element-plus';
import { DihService } from '@/service/api';
import type { ChatMessage, ChatMessagePart } from '@/types/type-dih';
import type { DihPanelRecord } from './use-panel-record-sync';
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
  ensureChatSessionRecordId: () => Promise<string>;
  addChartRecordToExtraData: (record: DihPanelRecord) => string;
  buildDisposeAgentPrompt: (detail?: string) => string;
  openDisposeAgentSession: (prompt: string) => Promise<void>;
};

const AUTO_CONFIRM_ACTIONS = new Set([
  'analysis.start',
  'analysis.create_continuous_task',
  'analysis.confirm_log_aggregation',
  'analysis.confirm_sandbox_result',
  'analysis_demo.confirm_log_aggregation',
  'analysis_demo.confirm_sandbox_result',
  'policy.confirm_trial',
  'policy.confirm_apply',
  'policy_demo.confirm_trial',
  'policy_demo.confirm_retry_trial',
  'policy_demo.confirm_apply',
  'policy.apply_to_production',
  'data_access.generate_demo_push_config',
  'data_access.create_demo_push_task',
  'data_visualization.add_chart_library',
  'data_visualization.apply_config',
]);

const AUTO_REJECT_ACTIONS = new Set([
  'analysis.confirm_log_aggregation',
  'analysis.confirm_sandbox_result',
  'analysis_demo.confirm_log_aggregation',
  'analysis_demo.confirm_sandbox_result',
  'policy.confirm_trial',
  'policy.confirm_apply',
  'policy_demo.confirm_trial',
  'policy_demo.confirm_retry_trial',
  'policy_demo.confirm_apply',
  'data_access.generate_demo_push_config',
  'data_access.create_demo_push_task',
  'data_visualization.apply_config',
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

const confirmAction = (part: ChatMessagePart) => {
  const action = part.metadata?.action;
  return typeof action === 'string' ? action : '';
};

const buildChartLibraryRecord = (part: ChatMessagePart): DihPanelRecord => {
  const metadata = asObject(part.metadata);
  const name = textValue(metadata.title || part.title, '临时可视化图表');
  const entity = textValue(metadata.entity, '');
  const chartType = textValue(metadata.chartType, '');
  return {
    id: textValue(metadata.id, `chart:${entity || 'unknown'}:${name}`),
    title: '图表库记录已创建',
    name,
    description: textValue(metadata.content || part.content || metadata.description, ''),
    entity,
    chartType,
    api: textValue(metadata.api, ''),
    status: 'temporary',
    source: 'session',
    config: metadata.amisConfig || metadata.config || {},
  };
};

const autoConfirmMessage = (action: string) => {
  if (action === 'analysis.confirm_log_aggregation' || action === 'analysis_demo.confirm_log_aggregation') {
    return '我已确认日志聚合结果，请进入沙箱研判阶段。';
  }
  if (action === 'analysis.confirm_sandbox_result' || action === 'analysis_demo.confirm_sandbox_result') {
    return '我已确认沙箱研判结果，结果满意，请进入分析结论阶段。';
  }
  if (action === 'policy.confirm_trial' || action === 'policy_demo.confirm_trial') {
    return '我已确认进入试验场验证，请将当前策略记录推送到试验场做测试验证，并输出 zenvis:policy-record 更新验证状态。';
  }
  if (action === 'policy_demo.confirm_retry_trial') {
    return '我已确认重新进入试验场验证，请将修复后的策略记录推送到试验场重新测试验证，并输出 zenvis:policy-record 更新验证状态。';
  }
  if (action === 'policy.confirm_apply' || action === 'policy_demo.confirm_apply') {
    return '我已确认下发策略到系统正式生效，请调用配置管理 MCP 写入并应用策略，成功后输出 zenvis:policy-record 将生效状态更新为 yes。';
  }
  if (action === 'analysis.create_continuous_task') {
    return '我已确认持续分析任务方案，请根据上一条确认卡和配置开始创建数据推送服务与 AI分析任务。';
  }
  if (action === 'policy.apply_to_production') {
    return '我已确认更新生产策略配置，请根据上一条确认卡、模拟测试结果和配置块，通过配置管理 MCP 写入系统配置。';
  }
  if (action === 'data_access.generate_demo_push_config') {
    return '我已确认继续生成用户事件数据推送服务配置。请先生成完整的数据推送服务配置并展示给我确认，不要创建或启动数据推送服务。';
  }
  if (action === 'data_access.create_demo_push_task') {
    return '我已确认创建用户事件数据推送服务，请根据上一条确认卡和数据推送配置创建并启动数据推送服务。';
  }
  if (action === 'data_visualization.apply_config') {
    return '我已确认并授权应用上一轮数据可视化配置。请根据上一条确认卡和已生成的配置内容，按需调用配置、看板和菜单 MCP 工具写入系统；写入或创建成功后，请输出 zenvis:visualization-config-record、zenvis:dashboard-config-record、zenvis:menu-config-record 等记录围栏。';
  }
  if (action === 'data_visualization.add_chart_library') {
    return '我已确认把上一轮临时图表加入图表库，请记录该图表的 amis 配置并输出 zenvis:visualization-chart-record。';
  }
  return '我已确认研判分析方案，请根据上一条确认卡开始执行一次性研判分析。';
};

const autoRejectMessage = (action: string) => {
  if (
    action === 'analysis.confirm_log_aggregation'
    || action === 'analysis.confirm_sandbox_result'
  ) {
    return '我已取消研判流程，请暂停当前研判，不要进入下一阶段。';
  }
  if (action === 'analysis_demo.confirm_log_aggregation' || action === 'analysis_demo.confirm_sandbox_result') {
    return '我已取消研判演示流程，请暂停当前研判演示，不要进入下一阶段。';
  }
  if (action === 'policy.confirm_trial' || action === 'policy.confirm_apply') {
    return '我已取消当前策略控制流程，请暂停当前策略操作，不要进入下一阶段。';
  }
  if (
    action === 'policy_demo.confirm_trial'
    || action === 'policy_demo.confirm_retry_trial'
    || action === 'policy_demo.confirm_apply'
  ) {
    return '我已取消策略控制演示流程，请暂停当前策略控制演示，不要进入下一阶段。';
  }
  if (action === 'data_access.generate_demo_push_config') {
    return '我已取消生成用户事件数据推送服务配置。请记录本次演示到元数据配置阶段结束，不要生成数据推送配置，也不要创建数据推送服务。';
  }
  if (action === 'data_access.create_demo_push_task') {
    return '我已取消创建用户事件数据推送服务。请记录数据推送配置已生成但未添加到系统，不要创建或启动数据推送服务。';
  }
  if (action === 'data_visualization.apply_config') {
    return '我选择放弃本次数据可视化配置。请记录本次配置已放弃，不要写入 open_config，不要创建菜单，也不要创建看板。';
  }
  return '我已取消本次操作。';
};

const dataVisualizationDecisionMessage = (detail?: string) => {
  const focus = detail?.trim() || '请基于上一轮数据可视化配置继续优化展示字段、图表布局、菜单或看板配置。';
  return `我需要补充信息继续更新数据可视化配置。调整要求如下：\n${focus}\n请基于上一轮数据可视化配置重新生成完整配置，并再次展示完整配置和后续选择。`;
};

const dataVisualizationDecisionDisplayMessage = (detail?: string) => {
  const focus = detail?.trim() || '继续优化数据可视化配置。';
  return `我已补充数据可视化配置调整要求：\n${focus}`;
};

const analysisDemoConfirmReviseMessage = (action: string, detail?: string) => {
  const focus = detail?.trim() || '请基于上一阶段结果补充更多关联数据。';
  if (action === 'analysis.confirm_log_aggregation' || action === 'analysis_demo.confirm_log_aggregation') {
    return `我需要补充更多日志聚合数据。补充内容如下：\n${focus}\n请基于上一轮日志聚合结果继续补充相关日志，并再次展示日志聚合结果让我确认。`;
  }
  if (action === 'analysis.confirm_sandbox_result' || action === 'analysis_demo.confirm_sandbox_result') {
    return `我需要补充信息继续沙箱研判。补充研判重点如下：\n${focus}\n请基于上一轮沙箱研判结果继续补充分析，并再次展示沙箱研判结果让我确认。`;
  }
  return focus;
};

const analysisDemoConfirmReviseDisplayMessage = (action: string, detail?: string) => {
  const focus = detail?.trim() || '继续补充研判信息。';
  if (action === 'analysis.confirm_log_aggregation' || action === 'analysis_demo.confirm_log_aggregation') {
    return `我已补充日志聚合数据：\n${focus}`;
  }
  if (action === 'analysis.confirm_sandbox_result' || action === 'analysis_demo.confirm_sandbox_result') {
    return `我已补充沙箱研判信息：\n${focus}`;
  }
  return focus;
};

const policyConfirmReviseMessage = (detail?: string) => {
  const focus = detail?.trim() || '请基于上一轮策略配置继续补充更新。';
  return `我需要补充更新策略配置。调整要求如下：\n${focus}\n请基于上一轮策略记录重新生成策略配置，并再次输出 zenvis:policy-record 后让我确认是否进入试验场验证。`;
};

const policyConfirmReviseDisplayMessage = (detail?: string) => {
  const focus = detail?.trim() || '继续补充更新策略配置。';
  return `我已补充策略更新要求：\n${focus}`;
};

const analysisDecisionMessage = (decision: 'dispose' | 'ignore' | 'continue', detail?: string) => {
  if (decision === 'dispose') {
    return '我选择执行处置。请基于上一轮研判结论、关键证据和处置策略配置，进入处置执行准备流程；先说明拟执行动作、影响范围、回滚方案和需要我确认的配置。';
  }
  if (decision === 'ignore') {
    return '我选择忽略本次告警。请基于上一轮研判结论记录忽略原因、适用条件和后续观察建议，不执行处置动作。';
  }
  const focus = detail?.trim() || '请围绕上一轮尚未闭环的疑点继续补充证据。';
  return `我需要补充信息继续研判。补充研判重点如下：\n${focus}\n请基于上一轮证据继续研判，并说明新增证据、结论变化和下一步建议。`;
};

const dataAccessDecisionMessage = (decision: 'apply_config' | 'abandon' | 'revise', detail?: string) => {
  if (decision === 'apply_config') {
    return '我已确认并授权添加上一轮已生成并展示的 meta 元数据配置到系统。本条消息就是写入授权：请不要再次询问是否添加配置。请立即按顺序调用元数据配置 MCP：1. policy_config_tree(type="meta") 检查目标文件是否存在；2. 如果目标文件不存在，调用 policy_config_add(type="meta", configDto={"fileName":"<目标文件名>"}) 创建文件；3. 调用 policy_config_apply(type="meta", configDto={"fileName":"<目标文件名>","text":"<上一轮完整 meta json>"}) 写入并应用；4. 调用 policy_config_read(type="meta", fileName="<目标文件名>") 读回校验文件确实存在且内容已写入；5. 只有在目标文件已存在且需要覆盖时，才先读取旧文件、说明差异并等待我确认覆盖。只有 MCP 返回成功且读回校验通过后，才用 Markdown 围栏代码块输出 zenvis:meta-config-record 记录；zenvis:meta-config-record 不是工具名，请不要调用它。';
  }
  if (decision === 'abandon') {
    return '我选择放弃本次元数据配置。请记录本次配置已放弃，不要写入系统，也不要继续创建或更新相关配置。';
  }
  const focus = detail?.trim() || '请基于上一轮配置继续优化字段、实体或展示规则。';
  return `我需要补充信息继续更新元数据配置。调整要求如下：\n${focus}\n请基于上一轮 meta 配置重新生成完整配置，并再次展示完整配置和后续选择。`;
};

const dataAccessDecisionDisplayMessage = (decision: 'apply_config' | 'abandon' | 'revise', detail?: string) => {
  if (decision === 'apply_config') {
    return '我已确认添加配置到系统。';
  }
  if (decision === 'abandon') {
    return '我已放弃本次元数据配置。';
  }
  const focus = detail?.trim() || '继续优化元数据配置。';
  return `我已补充配置调整要求：\n${focus}`;
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
  ensureChatSessionRecordId,
  addChartRecordToExtraData,
  buildDisposeAgentPrompt,
  openDisposeAgentSession,
}: UseChatMessageActionsOptions) => {
  const handleInfoStepsSubmit = async (
    message: ChatMessage,
    payload: { part: ChatMessagePart; answers: InfoStepAnswer[] },
  ) => {
    if (!chatSessionId.value || !message.id || !payload.part.id) {
      ElMessage.warning('缺少补充信息卡片标识，无法记录提交结果');
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
    payload: { part: ChatMessagePart; decision: 'approved' | 'rejected' | 'revise'; detail?: string },
  ) => {
    if (!chatSessionId.value || !message.id || !payload.part.id) {
      ElMessage.warning('缺少确认记录标识，无法记录操作结果');
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
    const action = confirmAction(payload.part);
    if (payload.decision === 'revise' && action === 'data_visualization.apply_config') {
      ElMessage.success('已提交配置调整要求');
      await nextTick();
      await sendMessage({
        content: dataVisualizationDecisionDisplayMessage(payload.detail),
        requestContent: dataVisualizationDecisionMessage(payload.detail),
      });
      return;
    }
    if (payload.decision === 'revise' && (
      action === 'analysis.confirm_log_aggregation'
      || action === 'analysis.confirm_sandbox_result'
      || action === 'analysis_demo.confirm_log_aggregation'
      || action === 'analysis_demo.confirm_sandbox_result'
    )) {
      ElMessage.success('已提交补充信息');
      await nextTick();
      await sendMessage({
        content: analysisDemoConfirmReviseDisplayMessage(action, payload.detail),
        requestContent: analysisDemoConfirmReviseMessage(action, payload.detail),
      });
      return;
    }
    if (payload.decision === 'revise' && (
      action === 'policy.confirm_trial'
      || action === 'policy_demo.confirm_trial'
      || action === 'policy_demo.confirm_retry_trial'
    )) {
      ElMessage.success('已提交策略更新要求');
      await nextTick();
      await sendMessage({
        content: policyConfirmReviseDisplayMessage(payload.detail),
        requestContent: policyConfirmReviseMessage(payload.detail),
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

  const handleAddChartLibrary = async (_message: ChatMessage, part: ChatMessagePart) => {
    const action = confirmAction(part);
    if (action !== 'data_visualization.add_chart_library') {
      ElMessage.warning('当前图表不支持加入图表库');
      return;
    }
    if (part.status === 'submitted' || part.status === 'added') {
      ElMessage.info('该图表已加入图表库');
      return;
    }
    const previousExtraData = chatSessionExtraData.value;
    const previousStatus = part.status;
    try {
      const sessionRecordId = await ensureChatSessionRecordId();
      if (!sessionRecordId) {
        ElMessage.warning('当前会话尚未创建完成，无法加入图表库');
        return;
      }
      const record = buildChartLibraryRecord(part);
      const nextExtraData = addChartRecordToExtraData(record);
      part.status = 'added';
      chatSessionExtraData.value = nextExtraData;
      await DihService.updateChatSession(sessionRecordId, { extra_data: nextExtraData });
      ElMessage.success('已加入图表库');
    } catch (error) {
      console.error('加入图表库失败:', error);
      part.status = previousStatus;
      chatSessionExtraData.value = previousExtraData;
      ElMessage.error('加入图表库失败');
    }
  };

  const handleAnalysisDecision = async (
    message: ChatMessage,
    payload: { part: ChatMessagePart; decision: 'dispose' | 'ignore' | 'continue'; detail?: string },
  ) => {
    if (!chatSessionId.value || !message.id || !payload.part.id) {
      ElMessage.warning('缺少研判选择记录标识，无法记录操作结果');
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
      console.error('记录研判后续选择失败:', error);
    }
    payload.part.status = payload.decision;
    const toastMap = {
      dispose: '已选择执行处置',
      ignore: '已选择忽略告警',
      continue: '已提交补充研判重点',
    };
    ElMessage.success(toastMap[payload.decision]);
    await nextTick();
    if (payload.decision === 'dispose') {
      await openDisposeAgentSession(buildDisposeAgentPrompt(payload.detail));
      return;
    }
    await sendMessage({ content: analysisDecisionMessage(payload.decision, payload.detail) });
  };

  const handleDataAccessDecision = async (
    message: ChatMessage,
    payload: { part: ChatMessagePart; decision: 'apply_config' | 'abandon' | 'revise'; detail?: string },
  ) => {
    if (!chatSessionId.value || !message.id || !payload.part.id) {
      ElMessage.warning('缺少数据接入选择记录标识，无法记录操作结果');
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
      console.error('记录数据接入后续选择失败:', error);
    }
    payload.part.status = payload.decision;
    const toastMap = {
      apply_config: '已选择添加配置到系统',
      abandon: '已放弃本次配置',
      revise: '已提交配置调整要求',
    };
    ElMessage.success(toastMap[payload.decision]);
    await nextTick();
    await sendMessage({
      content: dataAccessDecisionDisplayMessage(payload.decision, payload.detail),
      requestContent: dataAccessDecisionMessage(payload.decision, payload.detail),
    });
  };

  return {
    handleInfoStepsSubmit,
    handleActionDecision,
    handleAddChartLibrary,
    handleAnalysisDecision,
    handleDataAccessDecision,
  };
};
