<template>
  <div class="visualization-decision-part" :class="{ 'is-missing-meta': isMissingMeta }">
    <div class="visualization-decision-title">
      <el-icon><QuestionFilled /></el-icon>
      <span class="card-title-text">{{ part.title || `${agentLabel}待确认` }}</span>
      <el-tag size="small" :type="statusTagType" effect="plain">{{ statusText }}</el-tag>
      <el-tooltip :content="isExpanded ? '折叠' : '展开'" placement="top">
        <el-button
          class="card-toggle-btn"
          size="small"
          :icon="isExpanded ? CaretTop : CaretBottom"
          circle
          @click="toggleExpanded"
        />
      </el-tooltip>
    </div>
    <template v-if="isExpanded">
      <div v-if="workflowSummary" class="workflow-source-meta">{{ workflowSummary }}</div>
      <div class="visualization-decision-content">
        {{ part.content || `请确认${agentLabel}的当前步骤。` }}
      </div>
      <div v-if="isMissingMeta && showHandoff" class="visualization-decision-handoff">
        <el-button size="small" type="primary" @click="openMetadataConfig">
          {{ handoffLabel }}
        </el-button>
      </div>
      <div
        v-else-if="interactive && hasAvailableAction && isPending"
        class="visualization-decision-actions"
      >
        <el-button v-if="canApprove" size="small" type="primary" @click="requestDecision('apply_config')">
          {{ approveLabel }}
        </el-button>
        <el-button v-if="canReject" size="small" @click="requestDecision('abandon')">
          取消本步骤
        </el-button>
        <el-button v-if="canRetry" size="small" type="primary" @click="requestDecision('retry')">
          重试当前阶段
        </el-button>
        <el-button v-if="canRevise" size="small" type="warning" plain @click="requestDecision('revise')">
          补充信息继续调整
        </el-button>
      </div>
      <div v-if="workflowValidationMessage" class="workflow-validation-message">
        {{ workflowValidationMessage }}
      </div>
      <div
        v-if="interactive && reviseInputVisible && isPending && !isMissingMeta"
        class="visualization-decision-revise-box"
      >
        <el-input
          v-model="decisionInput"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          :placeholder="revisePlaceholder"
        />
        <div class="visualization-decision-revise-actions">
          <el-button size="small" type="primary" @click="submitRevise">继续调整</el-button>
          <el-button size="small" @click="reviseInputVisible = false">取消</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, toRef } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { CaretBottom, CaretTop, QuestionFilled } from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { generateUUID } from '@/utils/util-common';
import { useDefaultExpanded } from './message-part-context';

export type VisualizationDecision = 'apply_config' | 'abandon' | 'revise' | 'retry';

const props = withDefaults(
  defineProps<{
    part: ChatMessagePart;
    interactive?: boolean;
    agentLabel: string;
    revisePlaceholder?: string;
  }>(),
  {
    interactive: false,
    revisePlaceholder: '输入需要调整的查询、目标文件或发布参数',
  },
);

const emit = defineEmits<{
  (
    e: 'chooseDecision',
    payload: { part: ChatMessagePart; decision: VisualizationDecision; detail?: string },
  ): void;
}>();

const router = useRouter();
const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));
const reviseInputVisible = ref(false);
const decisionInput = ref('');

const metadata = computed(() => props.part.metadata || {});
const workflowId = computed(() => String(metadata.value.workflowId || ''));
const isWorkflowCard = computed(() => Boolean(workflowId.value));
const isPending = computed(() => !props.part.status || props.part.status === 'pending');
const isMissingMeta = computed(() =>
  ['missing_meta', 'meta_missing', 'meta_not_found'].includes(
    String(metadata.value.kind || '').toLowerCase().replaceAll('-', '_'),
  ),
);
const handoff = computed(() => {
  const value = metadata.value.handoff;
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
});
const showHandoff = computed(
  () =>
    isMissingMeta.value &&
    String(handoff.value.targetAgentType || '') === 'agent_metadata_config',
);
const handoffLabel = computed(() => String(handoff.value.label || '前往元数据配置'));
const allowedActions = computed(() => {
  const actions = metadata.value.allowedActions;
  return Array.isArray(actions) ? actions.map(String) : [];
});
const canApprove = computed(() => !isWorkflowCard.value || allowedActions.value.includes('approve'));
const canReject = computed(() => !isWorkflowCard.value || allowedActions.value.includes('reject'));
const canRevise = computed(() => !isWorkflowCard.value || allowedActions.value.includes('revise'));
const canRetry = computed(() => isWorkflowCard.value && allowedActions.value.includes('retry'));
const hasAvailableAction = computed(
  () => canApprove.value || canReject.value || canRevise.value || canRetry.value,
);
const workflowSummary = computed(() => {
  if (!workflowId.value) return '';
  const step = String(metadata.value.step || '-');
  const refs = Array.isArray(metadata.value.evidenceRefs) ? metadata.value.evidenceRefs.length : 0;
  return `共享工作流 ${workflowId.value} · 阶段 ${step} · 证据 ${refs} 条`;
});
const workflowValidationMessage = computed(() =>
  isWorkflowCard.value ? String(metadata.value.validationMessage || '') : '',
);
const isBlocked = computed(
  () =>
    metadata.value.validationStatus === 'blocked' ||
    metadata.value.step === 'BLOCKED' ||
    props.part.status === 'blocked',
);
const action = computed(() => String(metadata.value.action || ''));
const approveLabel = computed(() => {
  if (action.value.endsWith('.confirm_query_plan')) return '确认查询方案';
  if (action.value.endsWith('.confirm_overwrite')) return '确认覆盖配置';
  if (action.value.endsWith('.confirm_menu')) return '确认发布菜单';
  if (action.value.endsWith('.confirm_plan')) return '确认写入配置';
  if (action.value.endsWith('.select_entity_from_meta')) return '确认实体';
  return '确认执行';
});
const statusTagType = computed(() => {
  if (isMissingMeta.value || isBlocked.value) return 'danger';
  if (props.part.status === 'apply_config' || props.part.status === 'approved') return 'success';
  if (props.part.status === 'abandon' || props.part.status === 'rejected') return 'info';
  if (props.part.status === 'revise') return 'warning';
  return 'warning';
});
const statusText = computed(() => {
  if (isMissingMeta.value) return '缺少 Meta';
  if (isBlocked.value) return '已阻断';
  if (props.part.status === 'apply_config' || props.part.status === 'approved') return '已确认';
  if (props.part.status === 'abandon' || props.part.status === 'rejected') return '已取消';
  if (props.part.status === 'revise') return '继续调整';
  return '待选择';
});

const requestDecision = async (decision: VisualizationDecision) => {
  if (!props.interactive) return;
  if (decision === 'revise') {
    reviseInputVisible.value = true;
    return;
  }
  const label =
    decision === 'apply_config'
      ? approveLabel.value
      : decision === 'retry'
        ? '重试当前阶段'
        : '取消当前步骤';
  try {
    await ElMessageBox.confirm(`确认${label}？`, props.agentLabel, {
      confirmButtonText: '确定',
      cancelButtonText: '返回',
      type: decision === 'apply_config' ? 'warning' : 'info',
    });
    emit('chooseDecision', { part: props.part, decision });
  } catch {
    // User closed the confirmation dialog.
  }
};

const submitRevise = () => {
  if (!props.interactive) return;
  emit('chooseDecision', {
    part: props.part,
    decision: 'revise',
    detail: decisionInput.value.trim(),
  });
};

const openMetadataConfig = () => {
  if (!props.interactive || !showHandoff.value) return;
  const prompt = String(
    handoff.value.prompt || `请补齐${props.agentLabel}所需的已发布 Meta。${props.part.content || ''}`,
  ).trim();
  if (!prompt) {
    ElMessage.warning('缺少可预填的元数据配置提示词');
    return;
  }
  const msgRef = generateUUID();
  try {
    window.sessionStorage?.setItem(`dih:prefill:${msgRef}`, prompt);
  } catch {
    ElMessage.warning('浏览器存储不可用，将在新会话中通过链接预填提示词');
  }
  void router.push({
    name: 'service-dih',
    query: {
      type: 'agent_metadata_config',
      createSession: '1',
      chatSessionId: generateUUID(),
      msgRef,
      msg: encodeURIComponent(prompt),
    },
  });
};
</script>
