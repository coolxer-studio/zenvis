<template>
  <div class="notice-part" :class="noticeClass">
    <div class="notice-title">
      <el-icon><component :is="noticeIcon" /></el-icon>
      <span class="card-title-text">{{ part.title || '提示' }}</span>
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
    <div v-if="isExpanded" class="notice-content">{{ part.content }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, toRef } from 'vue';
import {
  CaretBottom,
  CaretTop,
  CircleCheckFilled,
  InfoFilled,
  WarningFilled,
} from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { useDefaultExpanded } from './message-part-context';

const props = defineProps<{
  part: ChatMessagePart;
}>();

const partRef = toRef(props, 'part');
const { isExpanded, toggleExpanded } = useDefaultExpanded(partRef);

const noticeClass = computed(() => [`notice-${props.part.level || 'info'}`]);

const noticeIcon = computed(() => {
  if (props.part.level === 'warning' || props.part.level === 'error') {
    return WarningFilled;
  }
  if (props.part.level === 'success') {
    return CircleCheckFilled;
  }
  return InfoFilled;
});
</script>
