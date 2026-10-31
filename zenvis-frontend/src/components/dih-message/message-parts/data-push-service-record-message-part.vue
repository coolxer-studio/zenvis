<template>
  <div class="notice-part notice-info data-push-service-record-part">
    <div class="notice-title">
      <el-icon><Connection /></el-icon>
      <span class="card-title-text">{{ recordTitle }}</span>
      <el-tag size="small" :type="recordState.statusTagType" effect="plain">
        {{ recordState.statusText }}
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
      <div class="notice-content">
        {{ part.content || '数据推送任务已通过平台校验、启动和本轮日志诊断。' }}
      </div>
      <div class="data-push-record-identity">
        <span v-if="recordState.taskId">taskId {{ recordState.taskId }}</span>
        <span v-if="recordState.runId">runId {{ recordState.runId }}</span>
        <span v-if="recordState.validationDigest">
          validation {{ shortDigest(recordState.validationDigest) }}
        </span>
      </div>
      <div class="data-push-validation-panel" :class="{ 'is-failed': recordState.validationFailed }">
        <div class="data-push-validation-summary">
          <span v-if="recordState.vectorVersion">Vector {{ recordState.vectorVersion }}</span>
          <span>测试 {{ recordState.tests.passed }}/{{ recordState.tests.total }} 通过</span>
          <span v-if="recordState.tests.present && recordState.tests.failed">
            失败 {{ recordState.tests.failed }}
          </span>
          <span v-if="recordState.repairRound > 0">
            修复轮次 {{ recordState.repairRound }}/5
          </span>
        </div>
        <ul v-if="recordState.diagnostics.length" class="data-push-validation-diagnostics">
          <li
            v-for="(item, index) in recordState.diagnostics"
            :key="`${item.code || 'diag'}-${index}`"
          >
            <code>{{ item.path || '$' }}</code>
            <span>{{ item.message }}</span>
            <span v-if="item.repairHint" class="repair-hint">
              修复建议：{{ item.repairHint }}
            </span>
          </li>
        </ul>
        <pre v-if="recordState.logExcerpt" class="data-push-run-log">{{ recordState.logExcerpt }}</pre>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, toRef } from 'vue';
import { CaretBottom, CaretTop, Connection } from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { resolveDataPushRecordState } from './data-push-card-state';
import { metadataText, useDefaultExpanded } from './message-part-context';

const props = defineProps<{
  part: ChatMessagePart;
}>();

const { isExpanded, toggleExpanded } = useDefaultExpanded(toRef(props, 'part'));
const recordState = computed(() => resolveDataPushRecordState(props.part.metadata));
const recordTitle = computed(() =>
  props.part.title || metadataText(props.part, 'title') || '数据推送服务记录',
);

const shortDigest = (value: string) => value.length > 16 ? `${value.slice(0, 16)}…` : value;
</script>
