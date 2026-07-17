<template>
  <div v-if="part.type === 'analysis-decision'" class="analysis-decision-part">
    <div class="analysis-decision-title">
      <el-icon><QuestionFilled /></el-icon>
      <span class="card-title-text">{{ part.title || '研判完成，请选择后续处理' }}</span>
      <el-tag size="small" :type="analysisDecisionTagType" effect="plain">
        {{ analysisDecisionStatusText }}
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
      <div class="analysis-decision-content">
        {{ part.content || '请选择下一步处理方式。' }}
      </div>
      <div v-if="!part.status || part.status === 'pending'" class="analysis-decision-actions">
        <el-button size="small" type="primary" @click="requestAnalysisDecision('dispose')">
          执行处置
        </el-button>
        <el-button size="small" @click="requestAnalysisDecision('ignore')">
          忽略告警
        </el-button>
        <el-button size="small" type="warning" plain @click="requestAnalysisDecision('continue')">
          补充信息继续研判
        </el-button>
      </div>
      <div v-if="continueInputVisible && (!part.status || part.status === 'pending')" class="analysis-continue-box">
        <el-input
          v-model="analysisDecisionInput"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="输入需要继续研判的重点，例如：补查近 24 小时同源 IP 登录行为、重点关注横向移动证据"
        />
        <div class="analysis-continue-actions">
          <el-button size="small" type="primary" @click="submitAnalysisContinue">继续研判</el-button>
          <el-button size="small" @click="continueInputVisible = false">取消</el-button>
        </div>
      </div>
    </template>
  </div>

  <div v-else class="notice-part notice-info">
    <div class="notice-title">
      <el-icon><DataAnalysis /></el-icon>
      <span class="card-title-text">{{ analysisRecordTitle }}</span>
      <el-tag size="small" :type="analysisRecordTagType" effect="plain">
        {{ analysisRecordStatusText }}
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
    <div v-if="isExpanded" class="notice-content">
      {{ part.content || metadataText(part, 'description') || '研判阶段记录已同步到右侧面板。' }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, toRef } from 'vue';
import { ElMessageBox } from 'element-plus';
import {
  CaretBottom,
  CaretTop,
  DataAnalysis,
  QuestionFilled,
} from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import {
  metadataText,
  useDefaultExpanded,
} from './message-part-context';

const props = defineProps<{
  part: ChatMessagePart;
}>();

const emit = defineEmits<{
  (e: 'chooseAnalysisDecision', payload: {
    part: ChatMessagePart;
    decision: 'dispose' | 'ignore' | 'continue';
    detail?: string;
  }): void;
}>();

const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));
const continueInputVisible = ref(false);
const analysisDecisionInput = ref('');

const analysisDecisionTagType = computed(() => {
  if (props.part.status === 'dispose') return 'success';
  if (props.part.status === 'ignore') return 'info';
  if (props.part.status === 'continue') return 'warning';
  return 'warning';
});

const analysisDecisionStatusText = computed(() => {
  if (props.part.status === 'dispose') return '已选择处置';
  if (props.part.status === 'ignore') return '已忽略';
  if (props.part.status === 'continue') return '继续研判';
  return '待选择';
});

const requestAnalysisDecision = async (decision: 'dispose' | 'ignore' | 'continue') => {
  if (decision === 'continue') {
    continueInputVisible.value = true;
    return;
  }

  const label = decision === 'dispose' ? '执行处置' : '忽略告警';
  try {
    await ElMessageBox.confirm(`确认${label}？`, '后续处理', {
      confirmButtonText: '确定',
      cancelButtonText: '返回',
      type: decision === 'dispose' ? 'warning' : 'info',
    });
    emit('chooseAnalysisDecision', { part: props.part, decision });
  } catch {
    // 用户关闭确认框
  }
};

const submitAnalysisContinue = () => {
  emit('chooseAnalysisDecision', {
    part: props.part,
    decision: 'continue',
    detail: analysisDecisionInput.value.trim(),
  });
};

const analysisStageText = (stage: string) => {
  if (stage === 'log_aggregation') return '日志聚合';
  if (stage === 'sandbox_analysis') return '研判分析';
  if (stage === 'report_output') return '输出分析结论';
  return '研判记录';
};

const analysisRecordTitle = computed(() => {
  return props.part.title
    || metadataText(props.part, 'title')
    || analysisStageText(metadataText(props.part, 'stage'));
});

const analysisRecordTagType = computed(() => {
  const status = metadataText(props.part, 'status');
  if (status === 'completed' || status === 'success') return 'success';
  if (status === 'failed' || status === 'error') return 'danger';
  if (status === 'running' || status === 'processing') return 'warning';
  return 'info';
});

const analysisRecordStatusText = computed(() => {
  const status = metadataText(props.part, 'status');
  if (status === 'completed' || status === 'success') return '已完成';
  if (status === 'failed' || status === 'error') return '失败';
  if (status === 'running' || status === 'processing') return '进行中';
  return status || '待开始';
});
</script>
