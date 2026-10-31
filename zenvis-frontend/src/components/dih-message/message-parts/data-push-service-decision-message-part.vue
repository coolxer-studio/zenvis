<template>
  <div class="data-push-service-decision-part" :class="{ 'is-missing-meta': isMissingMeta }">
    <div class="data-push-service-decision-title">
      <el-icon><QuestionFilled /></el-icon>
      <span class="card-title-text">{{ part.title || '数据推送服务待确认' }}</span>
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
      <div class="data-push-service-decision-content">
        {{ contentText }}
      </div>
      <div class="data-push-validation-panel" :class="{ 'is-failed': validationFailed }">
        <div class="data-push-validation-summary">
          <el-tag size="small" :type="validationTagType" effect="plain">
            {{ validationStatusText }}
          </el-tag>
          <span v-if="vectorVersion">Vector {{ vectorVersion }}</span>
          <span>测试 {{ tests.passed }}/{{ tests.total }} 通过</span>
          <span v-if="tests.present && tests.failed">失败 {{ tests.failed }}</span>
          <span v-if="repairRound > 0">修复轮次 {{ repairRound }}/5</span>
        </div>
        <ul v-if="diagnostics.length" class="data-push-validation-diagnostics">
          <li v-for="(item, index) in diagnostics" :key="`${item.code || 'diag'}-${index}`">
            <span v-if="item.severity" class="diagnostic-severity">{{ item.severity }}</span>
            <code>{{ item.path || '$' }}</code>
            <span>{{ item.message }}</span>
            <span v-if="item.repairHint" class="repair-hint">修复建议：{{ item.repairHint }}</span>
          </li>
        </ul>
        <pre v-if="logExcerpt" class="data-push-run-log">{{ logExcerpt }}</pre>
      </div>
      <div v-if="blockingReasons.length" class="data-push-gate-panel">
        <div class="data-push-gate-heading">
          <el-icon><WarningFilled /></el-icon>
          <span>平台门禁未通过（{{ blockingReasons.length }} 项）</span>
        </div>
        <ul class="data-push-gate-reasons">
          <li v-for="(item, index) in blockingReasons" :key="`${item.code || 'gate'}-${index}`">
            <div class="gate-reason-title">
              <el-tag size="small" type="danger" effect="plain">{{ categoryLabel(item.category) }}</el-tag>
              <code>{{ item.code }}</code>
              <span>{{ item.message }}</span>
            </div>
            <div v-if="item.expected || item.actual" class="gate-reason-digests">
              <span v-if="item.expected">期望 {{ item.expected }}</span>
              <span v-if="item.actual">实际 {{ item.actual }}</span>
            </div>
            <div v-if="item.repairHint" class="gate-reason-hint">处理建议：{{ item.repairHint }}</div>
          </li>
        </ul>
      </div>
      <div v-if="isMissingMeta && showHandoff" class="data-push-service-handoff">
        <el-button size="small" type="primary" @click="openMetadataConfig">
          {{ handoffLabel }}
        </el-button>
      </div>
      <div
        v-else-if="interactive && cardState.actionable"
        class="data-push-service-decision-actions"
      >
        <el-button v-if="canApprove" size="small" type="primary" @click="requestDecision('apply_config')">
          创建并启动服务
        </el-button>
        <el-button v-if="canReject" size="small" @click="requestDecision('abandon')">
          取消创建
        </el-button>
        <el-button v-if="canRetry" size="small" type="primary" @click="requestDecision('retry')">
          重新校验并继续
        </el-button>
        <el-button v-if="canRevise" size="small" type="warning" plain @click="requestDecision('revise')">
          补充信息更新推送配置
        </el-button>
      </div>
      <div v-if="workflowValidationMessage && !blockingReasons.length" class="workflow-validation-message">
        {{ workflowValidationMessage }}
      </div>
      <div
        v-if="interactive && reviseInputVisible && cardState.actionable && !isMissingMeta"
        class="data-push-service-revise-box"
      >
        <el-input
          v-model="decisionInput"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="输入需要调整的数据来源、解析映射、目标表或 Vector 配置内容"
        />
        <div class="data-push-service-revise-actions">
          <el-button size="small" type="primary" @click="submitRevise">继续更新配置</el-button>
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
import { CaretBottom, CaretTop, QuestionFilled, WarningFilled } from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { generateUUID } from '@/utils/util-common';
import { useDefaultExpanded } from './message-part-context';
import { resolveDataPushCardState } from './data-push-card-state';

const props = defineProps<{
  part: ChatMessagePart;
  interactive?: boolean;
}>();

const emit = defineEmits<{
  (
    e: 'chooseDataPushDecision',
    payload: {
      part: ChatMessagePart;
      decision: 'apply_config' | 'abandon' | 'revise' | 'retry';
      detail?: string;
    },
  ): void;
}>();

const router = useRouter();
const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));
const reviseInputVisible = ref(false);
const decisionInput = ref('');

const metadata = computed(() => props.part.metadata || {});
const cardState = computed(() => resolveDataPushCardState(metadata.value, props.part.status));
const validation = computed(() => cardState.value.validation);
const tests = computed(() => cardState.value.tests);
const diagnostics = computed(() => cardState.value.diagnostics);
const blockingReasons = computed(() => cardState.value.blockingReasons);
const repairRound = computed(() => cardState.value.repairRound);
const logExcerpt = computed(() => cardState.value.logExcerpt);
const vectorVersion = computed(() => cardState.value.vectorVersion);
const validationPassed = computed(() => cardState.value.validationPassed);
const validationFailed = computed(() => cardState.value.validationFailed);
const validationTagType = computed(() => validationFailed.value
  ? 'danger'
  : validationPassed.value ? 'success' : 'info');
const validationStatusText = computed(() => validationFailed.value
  ? (validation.value.blocked === true ? '校验服务不可用' : '校验未通过')
  : validationPassed.value ? '校验通过' : '待校验');
const workflowId = computed(() => String(metadata.value.workflowId || ''));
const isWorkflowCard = computed(() => Boolean(workflowId.value));
const isMissingMeta = computed(() => ['missing_meta', 'meta_missing', 'meta_not_found']
  .includes(String(metadata.value.kind || '').toLowerCase().replaceAll('-', '_')));
const handoff = computed(() => {
  const value = metadata.value.handoff;
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
});
const showHandoff = computed(() =>
  isMissingMeta.value && String(handoff.value.targetAgentType || '') === 'agent_metadata_config',
);
const handoffLabel = computed(() => String(handoff.value.label || '前往元数据配置'));
const canApprove = computed(() => cardState.value.canApprove);
const canReject = computed(() => cardState.value.canReject);
const canRevise = computed(() => cardState.value.canRevise);
const canRetry = computed(() => cardState.value.canRetry);
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
const contentText = computed(() => props.part.content
  || (cardState.value.gateBlocked || cardState.value.validationFailed
    ? '当前配置未通过平台门禁，请查看阻断原因后重新校验。'
    : '请确认是否创建并启动锁定的数据推送服务。'));
const statusTagType = computed(() => {
  if (isMissingMeta.value) return 'danger';
  if (props.part.status === 'apply_config' || props.part.status === 'approved') return 'success';
  if (props.part.status === 'abandon') return 'info';
  if (props.part.status === 'revise') return 'warning';
  return cardState.value.statusTagType;
});
const statusText = computed(() => {
  if (isMissingMeta.value) return '缺少 Meta';
  if (props.part.status === 'apply_config' || props.part.status === 'approved') return '已确认创建';
  if (props.part.status === 'abandon') return '已取消';
  if (props.part.status === 'revise') return '更新推送配置';
  if (props.part.status === 'submitted') return '已提交';
  return cardState.value.statusText;
});

const categoryLabels: Record<string, string> = {
  candidate: '候选配置',
  validation: '配置校验',
  meta: 'Meta 引用',
  evidence: '工具证据',
  workflow: '工作流',
};
const categoryLabel = (category: string) => categoryLabels[category] || '门禁';

const requestDecision = async (
  decision: 'apply_config' | 'abandon' | 'revise' | 'retry',
) => {
  if (!props.interactive) return;
  if (decision === 'revise') {
    reviseInputVisible.value = true;
    return;
  }
  const label = decision === 'apply_config'
    ? '创建并启动数据推送服务'
    : decision === 'retry'
      ? '重新校验锁定候选；通过后仍需再次确认，不会直接创建'
      : '取消创建数据推送服务';
  try {
    await ElMessageBox.confirm(`确认${label}？`, '数据推送服务', {
      confirmButtonText: '确定',
      cancelButtonText: '返回',
      type: decision === 'apply_config' ? 'warning' : 'info',
    });
    emit('chooseDataPushDecision', { part: props.part, decision });
  } catch {
    // User closed the confirmation dialog.
  }
};

const submitRevise = () => {
  if (!props.interactive) return;
  emit('chooseDataPushDecision', {
    part: props.part,
    decision: 'revise',
    detail: decisionInput.value.trim(),
  });
};

const openMetadataConfig = () => {
  if (!props.interactive || !showHandoff.value) return;
  const prompt = String(
    handoff.value.prompt
      || `请为当前数据推送任务补齐已发布 Meta。${props.part.content || ''}`,
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
