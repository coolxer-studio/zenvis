<template>
  <div v-if="part.type === 'mcp-approval'" class="mcp-approval-part" :class="`mcp-approval-${part.status || 'pending'}`">
    <div class="mcp-approval-title">
      <el-icon><Lock /></el-icon>
      <span class="card-title-text">{{ part.title || 'MCP 工具审批' }}</span>
      <el-tag size="small" :type="mcpApprovalTagType" effect="plain">
        {{ mcpApprovalStatusText }}
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
      <div class="mcp-approval-meta">
        <span>来源：{{ mcpApprovalSourceText }}</span>
        <el-tag v-if="mcpApprovalIsSessionGranted" size="small" type="success" effect="plain">
          本会话已授权
        </el-tag>
        <el-tag size="small" :type="mcpApprovalRiskTagType" effect="dark">
          {{ mcpApprovalRiskText }}
        </el-tag>
      </div>
      <div class="mcp-approval-content">{{ part.content || '该工具需要你的明确许可后才能执行。' }}</div>
      <div v-if="mcpApprovalExpiryText" class="mcp-approval-expiry">
        {{ mcpApprovalExpiryText }}
      </div>
      <div v-if="mcpApprovalCanDecide" class="mcp-approval-actions">
        <el-button
          size="small"
          type="primary"
          :loading="mcpApprovalDecisionLoading('approved')"
          :disabled="mcpApprovalIsDeciding"
          @click="requestMcpApprovalDecision('approved')"
        >
          允许本次
        </el-button>
        <el-button
          size="small"
          type="success"
          plain
          :loading="mcpApprovalDecisionLoading('approved_session')"
          :disabled="mcpApprovalIsDeciding"
          @click="requestMcpApprovalDecision('approved_session')"
        >
          本会话始终允许
        </el-button>
        <el-button
          size="small"
          type="danger"
          plain
          :loading="mcpApprovalDecisionLoading('rejected')"
          :disabled="mcpApprovalIsDeciding"
          @click="requestMcpApprovalDecision('rejected')"
        >
          拒绝执行
        </el-button>
      </div>
    </template>
  </div>

  <div v-else-if="part.type === 'confirm'" class="confirm-part">
    <div class="confirm-title">
      <el-icon><QuestionFilled /></el-icon>
      <span class="card-title-text">{{ part.title || '需要确认' }}</span>
      <el-tag size="small" :type="confirmTagType" effect="plain">
        {{ confirmStatusText }}
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
      <div class="confirm-content">{{ part.content }}</div>
      <div class="confirm-actions" v-if="!part.status || part.status === 'pending'">
        <el-button size="small" type="primary" @click="requestDecision('approved')">
          确认执行
        </el-button>
        <el-button size="small" @click="requestDecision('rejected')">取消</el-button>
        <el-button
          v-if="supportsConfirmRevise"
          size="small"
          type="warning"
          plain
          @click="confirmReviseInputVisible = true"
        >
          {{ confirmReviseLabel }}
        </el-button>
      </div>
      <div v-if="confirmReviseInputVisible && (!part.status || part.status === 'pending')" class="confirm-revise-box">
        <el-input
          v-model="confirmDecisionInput"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          :placeholder="confirmRevisePlaceholder"
        />
        <div class="confirm-revise-actions">
          <el-button size="small" type="primary" @click="submitConfirmRevise">继续更新</el-button>
          <el-button size="small" @click="confirmReviseInputVisible = false">取消</el-button>
        </div>
      </div>
    </template>
  </div>

  <div v-else class="info-steps-part">
    <div class="info-steps-title">
      <el-icon><InfoFilled /></el-icon>
      <span class="card-title-text">{{ part.title || '需要补充信息' }}</span>
      <el-tag size="small" :type="infoStepsTagType" effect="plain">
        {{ infoStepsStatusText }}
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
      <div v-if="part.content" class="info-steps-content">{{ part.content }}</div>
      <div class="info-steps-list">
        <div
          v-for="(step, stepIndex) in infoSteps"
          :key="step.id || stepIndex"
          class="info-step-item"
        >
          <div class="info-step-marker">{{ stepIndex + 1 }}</div>
          <div class="info-step-body">
            <div class="info-step-heading">
              <span class="info-step-title">{{ step.title || `补充项 ${stepIndex + 1}` }}</span>
              <el-tag v-if="step.required" size="small" type="danger" effect="plain">必填</el-tag>
            </div>
            <div v-if="step.description" class="info-step-description">{{ step.description }}</div>
            <div class="info-step-suggestions">
              <el-button
                v-for="(suggestion, suggestionIndex) in stepSuggestions(step)"
                :key="suggestionIndex"
                class="info-step-suggestion"
                size="small"
                :type="isSuggestionSelected(step, suggestion) ? 'primary' : 'default'"
                plain
                :disabled="part.status === 'submitted'"
                @click="selectInfoStepSuggestion(step, suggestion)"
              >
                {{ suggestion.label }}
              </el-button>
            </div>
            <el-input
              v-model="infoStepCustomInputs[step.id]"
              class="info-step-input"
              type="textarea"
              :rows="2"
              maxlength="1000"
              show-word-limit
              :disabled="part.status === 'submitted'"
              :placeholder="step.placeholder || '也可以输入自定义内容'"
            />
          </div>
        </div>
      </div>
      <div v-if="!part.status || part.status === 'pending'" class="info-steps-actions">
        <el-button size="small" type="primary" @click="submitInfoSteps">
          {{ infoStepsSubmitLabel }}
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, toRef } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  CaretBottom,
  CaretTop,
  InfoFilled,
  Lock,
  QuestionFilled,
} from '@element-plus/icons-vue';
import { useSecondClock } from '@/composables/use-second-clock';
import type {
  ChatMessagePart,
  McpApprovalDecision,
} from '@/types/type-dih';
import {
  metadataText,
  useDefaultExpanded,
} from './message-part-context';

type InfoStepSuggestion = {
  label: string;
  value: string;
  description?: string;
};

type InfoStepItem = {
  id: string;
  title: string;
  description?: string;
  required?: boolean;
  suggestions?: Array<string | InfoStepSuggestion | Record<string, unknown>>;
  placeholder?: string;
};

type InfoStepAnswer = {
  id: string;
  title: string;
  value: string;
  source: 'suggestion' | 'custom';
};

const props = defineProps<{
  part: ChatMessagePart;
}>();

const emit = defineEmits<{
  (e: 'decideAction', payload: {
    part: ChatMessagePart;
    decision: 'approved' | 'rejected' | 'revise';
    detail?: string;
  }): void;
  (e: 'submitInfoSteps', payload: {
    part: ChatMessagePart;
    answers: InfoStepAnswer[];
  }): void;
  (e: 'decideMcpApproval', payload: {
    part: ChatMessagePart;
    decision: McpApprovalDecision;
  }): void;
}>();

const approvalNow = useSecondClock();
const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));
const confirmReviseInputVisible = ref(false);
const confirmDecisionInput = ref('');
const infoStepSelectedValues = reactive<Record<string, string>>({});
const infoStepCustomInputs = reactive<Record<string, string>>({});

const mcpApprovalScope = computed(() => {
  const value = props.part.metadata?.approvalScope || props.part.metadata?.approval_scope;
  return typeof value === 'string' ? value.toLowerCase() : '';
});

const mcpApprovalIsSessionGranted = computed(() => (
  mcpApprovalScope.value === 'session' && props.part.status !== 'pending'
));

const mcpApprovalStatusText = computed(() => {
  if (mcpApprovalScope.value === 'session') {
    if (props.part.status === 'approved') return '本会话已允许，等待执行';
    if (props.part.status === 'running') return '本会话已允许，执行中';
  }
  return ({
    pending: '等待审批',
    approved: '已允许，等待执行',
    running: '已允许，执行中',
    succeeded: '执行成功',
    failed: '执行失败',
    rejected: '已拒绝',
    denied: '策略禁止',
    expired: '已超时',
    cancelled: '已取消',
  }[props.part.status || 'pending'] || props.part.status || '等待审批');
});

const mcpApprovalTagType = computed<'success' | 'warning' | 'info' | 'danger'>(() => {
  const status = props.part.status;
  if (status === 'succeeded') return 'success';
  if (status === 'failed' || status === 'rejected' || status === 'denied') return 'danger';
  if (status === 'pending' || status === 'approved' || status === 'running') return 'warning';
  return 'info';
});

const mcpApprovalSourceText = computed(() => {
  const serverName = metadataText(props.part, 'serverName');
  const toolKey = metadataText(props.part, 'toolKey');
  const sourceType = metadataText(props.part, 'sourceType');
  if (sourceType.toLowerCase() === 'external' || toolKey.startsWith('external::')) {
    return serverName ? `外部 MCP · ${serverName}` : '外部 MCP';
  }
  return 'ZenVis 本地工具';
});

const mcpApprovalRiskText = computed(() => {
  const risk = metadataText(props.part, 'riskLevel').toLowerCase();
  if (risk === 'danger' || risk === 'high') return '高风险';
  if (risk === 'low' || risk === 'safe') return '低风险';
  return '需确认';
});

const mcpApprovalRiskTagType = computed<'danger' | 'warning' | 'success'>(() => {
  const risk = metadataText(props.part, 'riskLevel').toLowerCase();
  if (risk === 'danger' || risk === 'high') return 'danger';
  if (risk === 'low' || risk === 'safe') return 'success';
  return 'warning';
});

const mcpApprovalExpiryText = computed(() => {
  const raw = props.part.metadata?.expireTime;
  let timestamp = 0;
  if (typeof raw === 'number') {
    timestamp = raw;
  } else if (typeof raw === 'string' && raw) {
    const parsed = Date.parse(raw);
    timestamp = Number.isFinite(parsed) ? parsed : 0;
  }
  if (!timestamp || props.part.status !== 'pending') return '';
  const seconds = Math.max(0, Math.ceil((timestamp - approvalNow.value) / 1000));
  if (seconds <= 0) return '审批请求正在超时处理中';
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `剩余审批时间：${minutes}:${String(rest).padStart(2, '0')}`;
});

const mcpApprovalCanDecide = computed(() => props.part.status === 'pending');
const mcpApprovalIsDeciding = computed(() => props.part.metadata?.deciding === true);
const mcpApprovalDecisionLoading = (decision: McpApprovalDecision) => (
  mcpApprovalIsDeciding.value && props.part.metadata?.decisionInFlight === decision
);

const requestMcpApprovalDecision = (decision: McpApprovalDecision) => {
  if (!mcpApprovalCanDecide.value || mcpApprovalIsDeciding.value) return;
  emit('decideMcpApproval', { part: props.part, decision });
};

const confirmTagType = computed(() => {
  if (props.part.status === 'approved') return 'success';
  if (props.part.status === 'rejected') return 'info';
  if (props.part.status === 'revise') return 'warning';
  return 'warning';
});

const confirmStatusText = computed(() => {
  if (props.part.status === 'approved') return '已确认';
  if (props.part.status === 'rejected') return '已取消';
  if (props.part.status === 'revise') return '继续更新';
  return '待确认';
});

const metadataStringList = (key: string) => {
  const value = props.part.metadata?.[key];
  return Array.isArray(value) ? value.filter(item => typeof item === 'string') as string[] : [];
};

const supportsConfirmRevise = computed(() => metadataStringList('actions').includes('revise'));

const confirmReviseLabel = computed(() => {
  const value = props.part.metadata?.reviseLabel;
  return typeof value === 'string' && value.trim() ? value : '补充信息继续更新';
});

const confirmRevisePlaceholder = computed(() => {
  const action = props.part.metadata?.action;
  if (action === 'analysis.confirm_log_aggregation' || action === 'analysis_demo.confirm_log_aggregation') {
    return '输入需要补充的日志线索，例如：继续关联文件变更记录、补查近 10 分钟网络连接日志';
  }
  if (action === 'analysis.confirm_sandbox_result' || action === 'analysis_demo.confirm_sandbox_result') {
    return '输入需要继续研判的重点，例如：复核文件落地时间、重点确认异常外联是否成功';
  }
  if (action === 'policy.confirm_trial' || action === 'policy_demo.confirm_trial' || action === 'policy_demo.confirm_retry_trial') {
    return '输入需要补充的策略调整要求，例如：增加回滚前置确认、扩大来源匹配范围、降低自动处置强度';
  }
  return '输入需要调整的内容，例如：改成静态 HTML、增加趋势图、调整菜单名称或看板指标';
});

const submitConfirmRevise = () => {
  emit('decideAction', {
    part: props.part,
    decision: 'revise',
    detail: confirmDecisionInput.value.trim(),
  });
};

const requestDecision = async (decision: 'approved' | 'rejected') => {
  const verb = decision === 'approved' ? '执行' : '取消';
  try {
    await ElMessageBox.confirm(`确认${verb}「${props.part.title || '此操作'}」？`, '操作确认', {
      confirmButtonText: '确定',
      cancelButtonText: '返回',
      type: decision === 'approved' ? 'warning' : 'info',
    });
    emit('decideAction', { part: props.part, decision });
  } catch {
    // 用户关闭确认框
  }
};

const infoSteps = computed<InfoStepItem[]>(() => {
  const steps = props.part.metadata?.steps;
  if (!Array.isArray(steps)) {
    return [];
  }
  return steps
    .filter(step => step && typeof step === 'object')
    .map((step, index) => {
      const raw = step as Record<string, unknown>;
      return {
        id: typeof raw.id === 'string' && raw.id.trim() ? raw.id : `step_${index + 1}`,
        title: typeof raw.title === 'string' ? raw.title : '',
        description: typeof raw.description === 'string' ? raw.description : '',
        required: raw.required === true || raw.required === 'true',
        suggestions: Array.isArray(raw.suggestions) ? raw.suggestions : [],
        placeholder: typeof raw.placeholder === 'string' ? raw.placeholder : '',
      };
    });
});

const normalizeSuggestion = (
  suggestion: string | InfoStepSuggestion | Record<string, unknown>,
): InfoStepSuggestion => {
  if (typeof suggestion === 'string') {
    return {
      label: suggestion,
      value: suggestion,
    };
  }
  const label = typeof suggestion.label === 'string' ? suggestion.label : '';
  const value = typeof suggestion.value === 'string' ? suggestion.value : label;
  return {
    label: label || value || '建议项',
    value: value || label,
    description: typeof suggestion.description === 'string' ? suggestion.description : undefined,
  };
};

const stepSuggestions = (step: InfoStepItem) => {
  return (step.suggestions || []).map(normalizeSuggestion);
};

const isSuggestionSelected = (step: InfoStepItem, suggestion: InfoStepSuggestion) => {
  return (infoStepSelectedValues[step.id] || '') === suggestion.value;
};

const selectInfoStepSuggestion = (step: InfoStepItem, suggestion: InfoStepSuggestion) => {
  infoStepSelectedValues[step.id] = suggestion.value;
  infoStepCustomInputs[step.id] = '';
};

const infoStepAnswerValue = (step: InfoStepItem) => {
  const customValue = (infoStepCustomInputs[step.id] || '').trim();
  if (customValue) {
    return {
      value: customValue,
      source: 'custom' as const,
    };
  }
  return {
    value: (infoStepSelectedValues[step.id] || '').trim(),
    source: 'suggestion' as const,
  };
};

const infoStepsSubmitLabel = computed(() => {
  const value = props.part.metadata?.submitLabel;
  return typeof value === 'string' && value.trim() ? value : '提交补充信息';
});

const infoStepsTagType = computed(() => {
  if (props.part.status === 'submitted') return 'success';
  return 'warning';
});

const infoStepsStatusText = computed(() => {
  if (props.part.status === 'submitted') return '已提交';
  return '待补充';
});

const submitInfoSteps = () => {
  const missingStep = infoSteps.value.find(step => step.required && !infoStepAnswerValue(step).value);
  if (missingStep) {
    ElMessage.warning(`请补充「${missingStep.title || '必填项'}」`);
    return;
  }
  const answers = infoSteps.value
    .map(step => {
      const answer = infoStepAnswerValue(step);
      return {
        id: step.id,
        title: step.title || step.id,
        value: answer.value,
        source: answer.source,
      };
    })
    .filter(answer => answer.value);
  emit('submitInfoSteps', { part: props.part, answers });
};
</script>
