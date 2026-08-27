<template>
  <div class="notice-part notice-info">
    <div class="notice-title">
      <el-icon><DataAnalysis /></el-icon>
      <span class="card-title-text">{{ configRecordTitle }}</span>
      <el-tag size="small" :type="configRecordTagType" effect="plain">
        {{ configRecordStatusText }}
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
      {{ part.content || metadataText(part, 'changeDescription') || '配置记录已同步到右侧面板。' }}
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
}>();

const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));

const configRecordTitle = computed(() => {
  return (
    props.part.title ||
    metadataText(props.part, 'title') ||
    metadataText(props.part, 'configType') ||
    '配置记录'
  );
});

const configRecordTagType = computed(() => {
  const status = metadataText(props.part, 'validationStatus');
  if (status === 'success') return 'success';
  if (status === 'failed') return 'danger';
  if (status === 'blocked') return 'warning';
  return 'info';
});

const configRecordStatusText = computed(() => {
  const status = metadataText(props.part, 'validationStatus');
  if (status === 'success') return '验证成功';
  if (status === 'failed') return '验证失败';
  if (status === 'blocked') return '验证阻塞';
  return '未验证';
});
</script>
