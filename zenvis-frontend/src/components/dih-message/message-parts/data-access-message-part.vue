<template>
  <div v-if="part.type === 'data-access-decision'" class="data-access-decision-part">
    <div class="data-access-decision-title">
      <el-icon><QuestionFilled /></el-icon>
      <span class="card-title-text">{{ part.title || defaultDecisionTitle }}</span>
      <el-tag size="small" :type="dataAccessDecisionTagType" effect="plain">
        {{ dataAccessDecisionStatusText }}
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
      <div class="data-access-decision-content">
        {{ part.content || defaultDecisionContent }}
      </div>
      <div
        v-if="interactive && hasAvailableAction && (!part.status || part.status === 'pending')"
        class="data-access-decision-actions"
      >
        <el-button
          v-if="canApprove"
          size="small"
          type="primary"
          @click="requestDataAccessDecision('apply_config')"
        >
          {{ applyButtonText }}
        </el-button>
        <el-button
          v-if="canReject"
          size="small"
          @click="requestDataAccessDecision('abandon')"
        >
          {{ abandonButtonText }}
        </el-button>
        <el-button
          v-if="canRetry"
          size="small"
          type="primary"
          @click="requestDataAccessDecision('retry')"
        >
          重试当前阶段
        </el-button>
        <el-button
          v-if="canRevise"
          size="small"
          type="warning"
          plain
          @click="requestDataAccessDecision('revise')"
        >
          {{ reviseButtonText }}
        </el-button>
      </div>
      <div v-if="workflowValidationMessage" class="workflow-validation-message">
        {{ workflowValidationMessage }}
      </div>
      <div
        v-if="interactive && reviseInputVisible && (!part.status || part.status === 'pending')"
        class="data-access-revise-box"
      >
        <el-input
          v-model="decisionInput"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          :placeholder="revisePlaceholder"
        />
        <div class="data-access-revise-actions">
          <el-button size="small" type="primary" @click="submitDataAccessRevise"
            >继续更新配置</el-button
          >
          <el-button size="small" @click="reviseInputVisible = false">取消</el-button>
        </div>
      </div>
    </template>
  </div>

  <div
    v-else
    class="message-content markdown-body"
    v-html="parseMarkdown(part.content || '')"
  ></div>
</template>

<script setup lang="ts">
import { computed, ref, toRef } from 'vue';
import { ElMessageBox } from 'element-plus';
import { CaretBottom, CaretTop, QuestionFilled } from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { useDefaultExpanded, useMarkdownRenderer } from './message-part-context';

const props = defineProps<{
  part: ChatMessagePart;
  interactive?: boolean;
}>();

const emit = defineEmits<{
  (
    e: 'chooseDataAccessDecision',
    payload: {
      part: ChatMessagePart;
      decision: 'apply_config' | 'abandon' | 'revise' | 'retry';
      detail?: string;
    },
  ): void;
}>();

const { parseMarkdown } = useMarkdownRenderer();
const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));
const reviseInputVisible = ref(false);
const decisionInput = ref('');

const workflowSummary = computed(() => {
  const id = String(props.part.metadata?.workflowId || '');
  if (!id) return '';
  const step = String(props.part.metadata?.step || '-');
  const refs = Array.isArray(props.part.metadata?.evidenceRefs)
    ? props.part.metadata?.evidenceRefs as Array<Record<string, unknown>>
    : [];
  const tools = refs.map(item => String(item.tool || '')).filter(Boolean);
  return tools.length
    ? `工作流阶段：${step}；MCP 证据：${Array.from(new Set(tools)).join('、')}（成功）`
    : `工作流阶段：${step}`;
});

const workflowId = computed(() => String(props.part.metadata?.workflowId || ''));
const allowedActions = computed(() => {
  const actions = props.part.metadata?.allowedActions;
  return Array.isArray(actions) ? actions.map(action => String(action)) : [];
});
const isWorkflowCard = computed(() => Boolean(workflowId.value));
const canApprove = computed(() =>
  !isWorkflowCard.value || allowedActions.value.includes('approve'));
const canReject = computed(() =>
  !isWorkflowCard.value || allowedActions.value.includes('reject'));
const canRevise = computed(() =>
  !isWorkflowCard.value || allowedActions.value.includes('revise'));
const canRetry = computed(() =>
  isWorkflowCard.value && allowedActions.value.includes('retry'));
const hasAvailableAction = computed(() =>
  canApprove.value || canReject.value || canRevise.value || canRetry.value);
const workflowValidationMessage = computed(() => {
  if (!isWorkflowCard.value) return '';
  return String(props.part.metadata?.validationMessage || '');
});

const isPushTaskDecision = computed(() => {
  const explicitKind = String(props.part.metadata?.configKind || '')
    .trim()
    .toLowerCase()
    .replaceAll('-', '_');
  if (explicitKind === 'meta' || explicitKind === 'meta_config') return false;
  if (['push_task', 'pushtask', 'vectum', 'vector'].includes(explicitKind)) return true;
  return /(数据推送|push\s*task|vectum|vector)/i.test(
    `${props.part.title || ''}\n${props.part.content || ''}`,
  );
});

const defaultDecisionTitle = computed(() =>
  isPushTaskDecision.value ? '数据推送配置已生成，请确认创建' : '元数据配置已生成，请选择后续处理',
);
const defaultDecisionContent = computed(() =>
  isPushTaskDecision.value
    ? '可以创建并启动数据推送服务、取消本次创建，或补充调整要求继续更新配置。'
    : '可以添加配置到系统、放弃本次配置，或补充调整要求继续更新配置。',
);
const applyButtonText = computed(() =>
  isPushTaskDecision.value ? '创建并启动服务' : '添加配置到系统',
);
const abandonButtonText = computed(() => (isPushTaskDecision.value ? '取消创建' : '放弃本次配置'));
const reviseButtonText = computed(() =>
  isPushTaskDecision.value ? '补充信息更新推送配置' : '补充信息继续更新配置',
);
const revisePlaceholder = computed(() =>
  isPushTaskDecision.value
    ? '输入需要调整的数据来源、解析映射、目标表或 Vector 配置内容'
    : '输入需要调整的内容，例如：增加 server_time 字段、修改实体中文名、补充 IP 字段展示类型',
);

const dataAccessDecisionTagType = computed(() => {
  if (props.part.metadata?.validationStatus === 'blocked'
    || props.part.metadata?.step === 'BLOCKED') return 'danger';
  if (props.part.status === 'apply_config') return 'success';
  if (props.part.status === 'abandon') return 'info';
  if (props.part.status === 'revise') return 'warning';
  return 'warning';
});

const dataAccessDecisionStatusText = computed(() => {
  if (props.part.metadata?.validationStatus === 'blocked'
    || props.part.metadata?.step === 'BLOCKED') return '已阻断';
  if (props.part.status === 'apply_config') {
    return isPushTaskDecision.value ? '已确认创建' : '已选择添加';
  }
  if (props.part.status === 'abandon') {
    return isPushTaskDecision.value ? '已取消' : '已放弃';
  }
  if (props.part.status === 'revise') {
    return isPushTaskDecision.value ? '更新推送配置' : '继续更新';
  }
  return '待选择';
});

const requestDataAccessDecision = async (
  decision: 'apply_config' | 'abandon' | 'revise' | 'retry',
) => {
  if (!props.interactive) return;
  if (decision === 'revise') {
    reviseInputVisible.value = true;
    return;
  }

  const label = decision === 'apply_config'
    ? applyButtonText.value
    : decision === 'retry'
      ? '重试当前阶段'
      : abandonButtonText.value;
  try {
    await ElMessageBox.confirm(`确认${label}？`, '后续处理', {
      confirmButtonText: '确定',
      cancelButtonText: '返回',
      type: decision === 'apply_config' ? 'warning' : 'info',
    });
    emit('chooseDataAccessDecision', { part: props.part, decision });
  } catch {
    // 用户关闭确认框
  }
};

const submitDataAccessRevise = () => {
  if (!props.interactive) return;
  emit('chooseDataAccessDecision', {
    part: props.part,
    decision: 'revise',
    detail: decisionInput.value.trim(),
  });
};
</script>
