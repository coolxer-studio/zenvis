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
};

const AUTO_CONFIRM_ACTIONS = new Set([
  'analysis.create_continuous_task',
  'analysis.confirm_dataset',
  'analysis.confirm_service_result',
  'config.confirm_trial',
  'config.confirm_apply',
  'data_access.generate_demo_push_config',
  'data_access.create_demo_push_task',
  'data_visualization.add_chart_library',
  'data_visualization.apply_config',
]);

const AUTO_REJECT_ACTIONS = new Set([
  'analysis.confirm_dataset',
  'analysis.confirm_service_result',
  'config.confirm_trial',
  'config.confirm_apply',
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
  return '我已确认本次操作，请继续执行下一阶段。';
};

const autoRejectMessage = (action: string) => {
  if (action === 'analysis.confirm_dataset' || action === 'analysis.confirm_service_result') {
    return '我已取消当前数据分析流程，请暂停处理，不要进入下一阶段。';
  }
  if (action === 'config.confirm_trial' || action === 'config.confirm_apply') {
    return '我已取消当前配置管理流程，请暂停配置操作，不要进入下一阶段。';
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

const dataAccessDecisionMessage = (decision: 'apply_config' | 'abandon' | 'revise', detail?: string) => {
  if (decision === 'apply_config') {
    return '我已确认并授权添加上一轮已生成并展示的 meta 元数据配置到系统。本条消息就是写入授权：请不要再次询问是否添加配置。请立即按顺序调用元数据配置 MCP：1. config_tree(type="meta") 检查目标文件是否存在；2. 如果目标文件不存在，调用 config_add(type="meta", configDto={"fileName":"<目标文件名>"}) 创建文件；3. 调用 config_apply(type="meta", configDto={"fileName":"<目标文件名>","text":"<上一轮完整 meta json>"}) 写入并应用；4. 调用 config_read(type="meta", fileName="<目标文件名>") 读回校验文件确实存在且内容已写入；5. 只有在目标文件已存在且需要覆盖时，才先读取旧文件、说明差异并等待我确认覆盖。只有 MCP 返回成功且读回校验通过后，才用 Markdown 围栏代码块输出 zenvis:meta-config-record 记录；zenvis:meta-config-record 不是工具名，请不要调用它。';
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
    handleDataAccessDecision,
  };
};
