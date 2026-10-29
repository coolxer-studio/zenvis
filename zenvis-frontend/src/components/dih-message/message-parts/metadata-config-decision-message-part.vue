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
      <div v-if="isPathOperation" class="metadata-operation-panel">
        <div class="metadata-operation-heading">
          <el-icon><WarningFilled /></el-icon>
          <span>{{ operationTitle }}</span>
        </div>
        <div class="metadata-operation-path">
          <span class="metadata-operation-label">源路径</span>
          <code>{{ operationSource }}</code>
          <template v-if="isRenameOperation">
            <span class="metadata-operation-arrow">→</span>
            <code>{{ operationTarget }}</code>
          </template>
        </div>
        <div v-if="isRenameOperation" class="metadata-operation-hint">
          仅会在同一父目录内改名；目标已存在时平台会拒绝覆盖。
        </div>
        <div v-if="operationRecursive" class="metadata-operation-danger">
          将递归删除目录及其全部内容，此操作不会删除 ClickHouse 表或历史数据。
        </div>
        <div v-if="affectedFiles.length" class="metadata-operation-files">
          <span class="metadata-operation-label">受影响文件（{{ affectedFiles.length }}）</span>
          <ul>
            <li v-for="file in affectedFiles" :key="file"><code>{{ file }}</code></li>
          </ul>
        </div>
      </div>
      <div v-if="validationFailed && !isPathOperation" class="metadata-validation-panel">
        <div class="metadata-validation-heading">
          <el-icon><WarningFilled /></el-icon>
          <span>平台校验未通过</span>
        </div>
        <ul v-if="validationMessages.length" class="metadata-validation-list">
          <li v-for="(message, index) in validationMessages" :key="`${message}-${index}`">
            {{ message }}
          </li>
        </ul>
        <div class="metadata-validation-hint">
          建议补充调整要求重新生成；只有明确知悉风险时，才可选择忽略校验并强制写入。
        </div>
      </div>
      <div
        v-if="interactive && hasAvailableAction && isPending"
        class="metadata-config-decision-actions"
      >
        <el-button v-if="canApprove" size="small" type="primary" @click="requestDecision('apply_config')">
          {{ approveLabel }}
        </el-button>
        <el-button v-if="canReject" size="small" @click="requestDecision('abandon')">
          {{ rejectLabel }}
        </el-button>
        <el-button v-if="canRetry" size="small" type="primary" @click="requestDecision('retry')">
          重试当前阶段
        </el-button>
        <el-button
          v-if="canForceApprove"
          size="small"
          type="danger"
          plain
          @click="requestDecision('force_approve')"
        >
          忽略校验并强制写入
        </el-button>
        <el-button v-if="canRevise" size="small" type="warning" plain @click="requestDecision('revise')">
          {{ isPathOperation ? '重新选择目标' : '补充信息继续更新配置' }}
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
          :placeholder="revisePlaceholder"
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
import { CaretBottom, CaretTop, QuestionFilled, WarningFilled } from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import {
  metadataPathOperation,
  metadataPathOperationAffectedFiles,
  metadataPathOperationApproveLabel,
  metadataPathOperationRejectLabel,
  metadataForceApproveAllowed,
  metadataValidationFailed,
  metadataValidationMessages,
} from '../config-decision-actions';
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
      decision: 'apply_config' | 'abandon' | 'revise' | 'retry' | 'force_approve';
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
const pathOperation = computed(() => metadataPathOperation(metadata.value));
const isPathOperation = computed(() => Boolean(pathOperation.value));
const isRenameOperation = computed(() => pathOperation.value === 'rename');
const operationRecursive = computed(() =>
  pathOperation.value === 'delete' && metadata.value.recursive === true,
);
const operationSource = computed(() => String(metadata.value.sourcePath || metadata.value.fileName || '-'));
const operationTarget = computed(() => String(metadata.value.targetPath || '-'));
const affectedFiles = computed(() => metadataPathOperationAffectedFiles(metadata.value));
const operationTitle = computed(() => isRenameOperation.value ? '确认重命名' : '确认删除');
const approveLabel = computed(() => metadataPathOperationApproveLabel(metadata.value));
const rejectLabel = computed(() => metadataPathOperationRejectLabel(metadata.value));
const revisePlaceholder = computed(() => isPathOperation.value
  ? '说明新的源路径、目标名称或删除范围；平台会重新读取配置树确认'
  : '输入需要调整的实体、字段、展示规则或目标表信息');
const validationFailed = computed(() => metadataValidationFailed(metadata.value));
const validationMessages = computed(() => metadataValidationMessages(metadata.value));
const allowedActions = computed(() => {
  const actions = metadata.value.allowedActions;
  return Array.isArray(actions) ? actions.map(String) : [];
});
const canApprove = computed(() => !isWorkflowCard.value || allowedActions.value.includes('approve'));
const canReject = computed(() => !isWorkflowCard.value || allowedActions.value.includes('reject'));
const canRevise = computed(() => !isWorkflowCard.value || allowedActions.value.includes('revise'));
const canRetry = computed(() => isWorkflowCard.value && allowedActions.value.includes('retry'));
const canForceApprove = computed(() =>
  isWorkflowCard.value && metadataForceApproveAllowed(metadata.value),
);
const hasAvailableAction = computed(() =>
  canApprove.value
  || canReject.value
  || canRevise.value
  || canRetry.value
  || canForceApprove.value,
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
  if (validationFailed.value) return 'danger';
  if (isBlocked.value) return 'danger';
  if (props.part.status === 'apply_config' || props.part.status === 'approved') return 'success';
  if (props.part.status === 'force_approved') return 'danger';
  if (props.part.status === 'abandon') return 'info';
  if (props.part.status === 'revise') return 'warning';
  return 'warning';
});
const statusText = computed(() => {
  if (isPathOperation.value && isPending.value) return operationRecursive.value ? '待递归确认' : '待确认';
  if (validationFailed.value) return '校验未通过';
  if (isBlocked.value) return '已阻断';
  if (props.part.status === 'apply_config') return '已选择添加';
  if (props.part.status === 'approved') return '已确认';
  if (props.part.status === 'force_approved') return '已强制确认';
  if (props.part.status === 'abandon') return '已放弃';
  if (props.part.status === 'revise') return '继续更新';
  return '待选择';
});

const requestDecision = async (
  decision: 'apply_config' | 'abandon' | 'revise' | 'retry' | 'force_approve',
) => {
  if (!props.interactive) return;
  if (decision === 'revise') {
    reviseInputVisible.value = true;
    return;
  }
  if (decision === 'force_approve') {
    try {
      await ElMessageBox.confirm(
        '当前候选未通过平台校验。强制写入可能造成元数据不一致、实体不可用或查询失败，是否继续？',
        '校验未通过',
        {
          confirmButtonText: '继续',
          cancelButtonText: '返回修订',
          type: 'error',
        },
      );
      await ElMessageBox.confirm(
        '这是最终确认：写入将跳过静态校验拦截，但仍会执行真实 Meta 加载与读回。确认强制写入？',
        '二次确认',
        {
          confirmButtonText: '确认强制写入',
          cancelButtonText: '取消',
          type: 'error',
        },
      );
      emit('chooseMetadataConfigDecision', { part: props.part, decision });
    } catch {
      // User closed either confirmation dialog.
    }
    return;
  }
  if (isPathOperation.value && decision === 'apply_config') {
    try {
      if (operationRecursive.value) {
        await ElMessageBox.confirm(
          `将递归删除 ${operationSource.value} 及其中 ${affectedFiles.value.length} 个受影响路径。该操作不可通过回滚按钮恢复，是否继续？`,
          '递归删除风险确认',
          {
            confirmButtonText: '继续',
            cancelButtonText: '取消',
            type: 'error',
          },
        );
        await ElMessageBox.confirm(
          `这是最终确认：删除 ${operationSource.value}？Meta 将严格重载，但 ClickHouse 表和历史数据不会删除。`,
          '二次确认',
          {
            confirmButtonText: '确认递归删除',
            cancelButtonText: '取消',
            type: 'error',
          },
        );
      } else {
        const message = isRenameOperation.value
          ? `确认将 ${operationSource.value} 重命名为 ${operationTarget.value}？`
          : `确认删除 ${operationSource.value}？`;
        await ElMessageBox.confirm(message, operationTitle.value, {
          confirmButtonText: approveLabel.value,
          cancelButtonText: '取消',
          type: 'warning',
        });
      }
      emit('chooseMetadataConfigDecision', { part: props.part, decision });
    } catch {
      // User closed the confirmation dialog.
    }
    return;
  }
  const label = decision === 'apply_config'
    ? approveLabel.value
    : decision === 'retry'
      ? '重试当前阶段'
      : rejectLabel.value;
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
