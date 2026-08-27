<template>
  <div class="notice-part notice-info">
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
      {{
        part.content ||
        metadataText(part, 'description') ||
        (interactive ? '数据分析阶段记录已同步到右侧面板。' : '数据分析阶段记录')
      }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, toRef } from 'vue';
import { CaretBottom, CaretTop, DataAnalysis } from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { metadataText, useDefaultExpanded } from './message-part-context';

const props = defineProps<{
  part: ChatMessagePart;
  interactive?: boolean;
}>();

const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));

const analysisStageText = (stage: string) => {
  if (stage === 'dataset_preparation') return '数据集准备';
  if (stage === 'service_analysis') return '分析服务';
  if (stage === 'report_output') return '分析报告';
  return '数据分析记录';
};

const analysisRecordTitle = computed(() => {
  return (
    props.part.title ||
    metadataText(props.part, 'title') ||
    analysisStageText(metadataText(props.part, 'stage'))
  );
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
