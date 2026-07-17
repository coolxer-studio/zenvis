<template>
  <div class="notice-part notice-info">
    <div class="notice-title">
      <el-icon><DataAnalysis /></el-icon>
      <span class="card-title-text">{{ policyRecordTitle }}</span>
      <el-tag size="small" :type="policyRecordTagType" effect="plain">
        {{ policyRecordStatusText }}
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
      {{ part.content || metadataText(part, 'changeDescription') || '策略记录已同步到右侧面板。' }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, toRef } from 'vue';
import {
  CaretBottom,
  CaretTop,
  DataAnalysis,
} from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import {
  metadataText,
  useDefaultExpanded,
} from './message-part-context';

const props = defineProps<{
  part: ChatMessagePart;
}>();

const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));

const policyTypeText = (type: string) => {
  if (type === 'collection') return '采集策略';
  if (type === 'tagging') return '标记策略';
  if (type === 'disposal') return '处置策略';
  return '策略记录';
};

const policyRecordTitle = computed(() => {
  return props.part.title
    || metadataText(props.part, 'title')
    || policyTypeText(metadataText(props.part, 'policyType'));
});

const policyRecordTagType = computed(() => {
  const status = metadataText(props.part, 'validationStatus');
  if (status === 'success') return 'success';
  if (status === 'failed') return 'danger';
  return 'info';
});

const policyRecordStatusText = computed(() => {
  const status = metadataText(props.part, 'validationStatus');
  if (status === 'success') return '验证成功';
  if (status === 'failed') return '验证失败';
  return '未验证';
});
</script>
