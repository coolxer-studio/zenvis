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
      <div class="data-access-decision-content">
        {{ part.content || defaultDecisionContent }}
      </div>
      <div
        v-if="interactive && (!part.status || part.status === 'pending')"
        class="data-access-decision-actions"
      >
        <el-button size="small" type="primary" @click="requestDataAccessDecision('apply_config')">
          {{ applyButtonText }}
        </el-button>
        <el-button size="small" @click="requestDataAccessDecision('abandon')">
          {{ abandonButtonText }}
        </el-button>
        <el-button size="small" type="warning" plain @click="requestDataAccessDecision('revise')">
          {{ reviseButtonText }}
        </el-button>
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
      decision: 'apply_config' | 'abandon' | 'revise';
      detail?: string;
    },
  ): void;
}>();

const { parseMarkdown } = useMarkdownRenderer();
const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));
const reviseInputVisible = ref(false);
const decisionInput = ref('');

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
  if (props.part.status === 'apply_config') return 'success';
  if (props.part.status === 'abandon') return 'info';
  if (props.part.status === 'revise') return 'warning';
  return 'warning';
});

const dataAccessDecisionStatusText = computed(() => {
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

const requestDataAccessDecision = async (decision: 'apply_config' | 'abandon' | 'revise') => {
  if (!props.interactive) return;
  if (decision === 'revise') {
    reviseInputVisible.value = true;
    return;
  }

  const label = decision === 'apply_config' ? applyButtonText.value : abandonButtonText.value;
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
