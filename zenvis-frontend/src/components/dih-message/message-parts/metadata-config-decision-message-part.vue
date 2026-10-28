<template>
  <div class="metadata-config-decision-part">
    <div class="metadata-config-decision-title">
      <el-icon><QuestionFilled /></el-icon>
      <span class="card-title-text">{{ part.title || '元数据配置待确认' }}</span>
      <el-tag size="small" :type="statusTagType" effect="plain">
        {{ statusText }}
      </el-tag>
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
      <div class="metadata-config-decision-content">
        {{ part.content || '请选择是否将锁定的元数据配置添加到系统。' }}
      </div>
      <div
        v-if="interactive && hasAvailableAction && isPending"
        class="metadata-config-decision-actions"
      >
        <el-button v-if="canApprove" size="small" type="primary" @click="requestDecision('apply_config')">
          {{ isOverwrite ? '确认覆盖配置' : '添加配置到系统' }}
        </el-button>
        <el-button v-if="canReject" size="small" @click="requestDecision('abandon')">
          放弃本次配置
        </el-button>
        <el-button v-if="canRetry" size="small" type="primary" @click="requestDecision('retry')">
          重试当前阶段
        </el-button>
        <el-button v-if="canRevise" size="small" type="warning" plain @click="requestDecision('revise')">
          补充信息继续更新配置
        </el-button>
      </div>
      <div v-if="workflowValidationMessage" class="workflow-validation-message">
        {{ workflowValidationMessage }}
      </div>
      <div v-if="interactive && reviseInputVisible && isPending" class="metadata-config-revise-box">
        <el-input
          v-model="decisionInput"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="输入需要调整的实体、字段、展示规则或目标表信息"
        />
        <div class="metadata-config-revise-actions">
          <el-button size="small" type="primary" @click="submitRevise">继续更新配置</el-button>
          <el-button size="small" @click="reviseInputVisible = false">取消</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, toRef } from 'vue';
import { ElMessageBox } from 'element-plus';
import { CaretBottom, CaretTop, QuestionFilled } from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { useDefaultExpanded } from './message-part-context';

const props = defineProps<{
  part: ChatMessagePart;
  interactive?: boolean;
}>();

const emit = defineEmits<{
  (
    e: 'chooseMetadataConfigDecision',
    payload: {
      part: ChatMessagePart;
      decision: 'apply_config' | 'abandon' | 'revise' | 'retry';
      detail?: string;
    },
  ): void;
}>();

const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));
const reviseInputVisible = ref(false);
const decisionInput = ref('');

const metadata = computed(() => props.part.metadata || {});
const workflowId = computed(() => String(metadata.value.workflowId || ''));
const isWorkflowCard = computed(() => Boolean(workflowId.value));
const isPending = computed(() => !props.part.status || props.part.status === 'pending');
const isOverwrite = computed(() => metadata.value.overwrite === true);
const allowedActions = computed(() => {
  const actions = metadata.value.allowedActions;
  return Array.isArray(actions) ? actions.map(String) : [];
});
const canApprove = computed(() => !isWorkflowCard.value || allowedActions.value.includes('approve'));
const canReject = computed(() => !isWorkflowCard.value || allowedActions.value.includes('reject'));
const canRevise = computed(() => !isWorkflowCard.value || allowedActions.value.includes('revise'));
const canRetry = computed(() => isWorkflowCard.value && allowedActions.value.includes('retry'));
const hasAvailableAction = computed(() =>
  canApprove.value || canReject.value || canRevise.value || canRetry.value,
);
const workflowSummary = computed(() => {
  if (!workflowId.value) return '';
  const step = String(metadata.value.step || '-');
  const refs = Array.isArray(metadata.value.evidenceRefs)
    ? metadata.value.evidenceRefs.length
    : 0;
  return `共享工作流 ${workflowId.value} · 阶段 ${step} · 证据 ${refs} 条`;
});
const workflowValidationMessage = computed(() =>
  isWorkflowCard.value ? String(metadata.value.validationMessage || '') : '',
);
const isBlocked = computed(() =>
  metadata.value.validationStatus === 'blocked'
  || metadata.value.step === 'BLOCKED'
  || props.part.status === 'blocked',
);
const statusTagType = computed(() => {
  if (isBlocked.value) return 'danger';
  if (props.part.status === 'apply_config') return 'success';
  if (props.part.status === 'abandon') return 'info';
  if (props.part.status === 'revise') return 'warning';
  return 'warning';
});
const statusText = computed(() => {
  if (isBlocked.value) return '已阻断';
  if (props.part.status === 'apply_config') return '已选择添加';
  if (props.part.status === 'abandon') return '已放弃';
  if (props.part.status === 'revise') return '继续更新';
  return '待选择';
});

const requestDecision = async (
  decision: 'apply_config' | 'abandon' | 'revise' | 'retry',
) => {
  if (!props.interactive) return;
  if (decision === 'revise') {
    reviseInputVisible.value = true;
    return;
  }
  const label = decision === 'apply_config'
    ? (isOverwrite.value ? '确认覆盖配置' : '添加配置到系统')
    : decision === 'retry'
      ? '重试当前阶段'
      : '放弃本次配置';
  try {
    await ElMessageBox.confirm(`确认${label}？`, '元数据配置', {
      confirmButtonText: '确定',
      cancelButtonText: '返回',
      type: decision === 'apply_config' ? 'warning' : 'info',
    });
    emit('chooseMetadataConfigDecision', { part: props.part, decision });
  } catch {
    // User closed the confirmation dialog.
  }
};

const submitRevise = () => {
  if (!props.interactive) return;
  emit('chooseMetadataConfigDecision', {
    part: props.part,
    decision: 'revise',
    detail: decisionInput.value.trim(),
  });
};
</script>
