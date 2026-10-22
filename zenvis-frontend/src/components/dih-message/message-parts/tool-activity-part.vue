<template>
  <section
    class="tool-activity-part"
    :class="`is-${groupStatus}`"
    :data-testid="'tool-activity-' + groupKey"
  >
    <button
      type="button"
      class="tool-activity-header"
      :aria-expanded="expanded"
      :aria-controls="detailsId"
      @click="expanded = !expanded"
    >
      <span class="tool-activity-status-icon" aria-hidden="true">
        <el-icon v-if="groupStatus === 'running'" class="is-spinning"><Loading /></el-icon>
        <el-icon v-else-if="groupStatus === 'failed'"><CircleCloseFilled /></el-icon>
        <el-icon v-else-if="groupStatus === 'succeeded'"><CircleCheckFilled /></el-icon>
        <el-icon v-else><Tools /></el-icon>
      </span>
      <span class="tool-activity-title">
        <strong>{{ single ? toolName(parts[0]) : '工具活动' }}</strong>
        <span v-if="!single" class="tool-activity-count">{{ parts.length }} 次调用</span>
      </span>
      <span class="tool-activity-meta">
        <span>{{ statusLabel }}</span>
        <span v-if="durationLabel" aria-hidden="true">·</span>
        <span v-if="durationLabel">{{ durationLabel }}</span>
      </span>
      <el-icon class="tool-activity-chevron" aria-hidden="true">
        <CaretBottom v-if="expanded" />
        <CaretRight v-else />
      </el-icon>
    </button>

    <div v-if="expanded" :id="detailsId" class="tool-activity-body">
      <template v-if="single">
        <div v-for="section in detailSections(parts[0])" :key="section.key" class="tool-detail">
          <div class="tool-detail-header">
            <span>{{ section.label }}</span>
            <el-tooltip content="复制内容" placement="top">
              <button
                v-if="section.content"
                type="button"
                class="tool-detail-copy"
                :aria-label="`复制${section.label}`"
                @click="copySection(section)"
              >
                <el-icon><CopyDocument /></el-icon>
              </button>
            </el-tooltip>
          </div>
          <pre v-if="section.kind === 'code'" class="tool-detail-code">{{ section.content }}</pre>
          <div v-else class="tool-detail-empty">{{ section.content }}</div>
        </div>
      </template>

      <template v-else>
        <div v-for="part in parts" :key="part.id" class="tool-activity-row">
          <button
            type="button"
            class="tool-activity-row-header"
            :aria-expanded="isCallExpanded(part)"
            :aria-controls="callDetailsId(part)"
            @click="toggleCall(part)"
          >
            <span class="tool-activity-row-status">
              <el-icon v-if="statusOf(part) === 'running'" class="is-spinning"><Loading /></el-icon>
              <el-icon v-else-if="statusOf(part) === 'failed'"><CircleCloseFilled /></el-icon>
              <el-icon v-else><CircleCheckFilled /></el-icon>
            </span>
            <span class="tool-activity-row-name">{{ toolName(part) }}</span>
            <span class="tool-activity-row-meta">
              <template v-if="durationOf(part)">{{ durationOf(part) }}</template>
              <template v-else>{{ statusText(statusOf(part)) }}</template>
            </span>
            <el-icon class="tool-activity-chevron" aria-hidden="true">
              <CaretBottom v-if="isCallExpanded(part)" />
              <CaretRight v-else />
            </el-icon>
          </button>
          <div
            v-if="isCallExpanded(part)"
            :id="callDetailsId(part)"
            class="tool-activity-row-body"
          >
            <div v-for="section in detailSections(part)" :key="section.key" class="tool-detail">
              <div class="tool-detail-header">
                <span>{{ section.label }}</span>
                <el-tooltip content="复制内容" placement="top">
                  <button
                    v-if="section.content"
                    type="button"
                    class="tool-detail-copy"
                    :aria-label="`复制${section.label}`"
                    @click="copySection(section)"
                  >
                    <el-icon><CopyDocument /></el-icon>
                  </button>
                </el-tooltip>
              </div>
              <pre v-if="section.kind === 'code'" class="tool-detail-code">{{ section.content }}</pre>
              <div v-else class="tool-detail-empty">{{ section.content }}</div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  CaretBottom,
  CaretRight,
  CircleCheckFilled,
  CircleCloseFilled,
  CopyDocument,
  Loading,
  Tools,
} from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { copyTextToClipboard } from '@/utils/clipboard';
import {
  formatToolPayload,
  normalizeToolCallStatus,
  type ToolCallStatus,
} from '../tool-call-parts';

defineOptions({ name: 'ToolActivityPart' });

type DetailSection = {
  key: string;
  label: string;
  content: string;
  kind: 'code' | 'empty';
};

const props = defineProps<{
  parts: ChatMessagePart[];
}>();

const expanded = ref(false);
const expandedCalls = ref<Record<string, boolean>>({});
const single = computed(() => props.parts.length === 1);
const groupKey = computed(() => props.parts[0]?.id || 'group');
const detailsId = computed(() => `tool-activity-details-${groupKey.value}`);

const statusOf = (part: ChatMessagePart): ToolCallStatus =>
  normalizeToolCallStatus(part.status || part.metadata?.status);

const groupStatus = computed<ToolCallStatus>(() => {
  const statuses = props.parts.map(statusOf);
  if (statuses.includes('running')) return 'running';
  if (statuses.includes('failed')) return 'failed';
  return 'succeeded';
});

const statusText = (status: ToolCallStatus) => {
  if (status === 'running') return '执行中';
  if (status === 'failed') return '失败';
  return '已完成';
};

const statusLabel = computed(() => {
  if (groupStatus.value === 'running') return '执行中';
  if (groupStatus.value === 'failed') return '有调用失败';
  return '已完成';
});

const toolName = (part: ChatMessagePart) =>
  String(part.metadata?.toolName || part.content || '未知工具');

const formatDuration = (value: unknown) => {
  const duration = Number(value);
  if (!Number.isFinite(duration) || duration < 0) return '';
  if (duration < 1000) return `${Math.round(duration)}ms`;
  return `${(duration / 1000).toFixed(duration < 10_000 ? 1 : 0)}s`;
};

const durationOf = (part: ChatMessagePart) =>
  formatDuration(part.metadata?.durationMillis);

const durationLabel = computed(() => {
  if (props.parts.length > 1) {
    const total = props.parts.reduce(
      (sum, part) => sum + (Number(part.metadata?.durationMillis) || 0),
      0,
    );
    return total ? formatDuration(total) : '';
  }
  return durationOf(props.parts[0]);
});

const detailSections = (part: ChatMessagePart): DetailSection[] => {
  const sections: DetailSection[] = [];
  const argumentsText = formatToolPayload(part.metadata?.arguments);
  const resultText = formatToolPayload(part.metadata?.result);
  const errorText = formatToolPayload(part.metadata?.error);
  if (argumentsText) {
    sections.push({ key: 'arguments', label: '输入', content: argumentsText, kind: 'code' });
  }
  if (resultText) {
    sections.push({ key: 'result', label: '返回', content: resultText, kind: 'code' });
  }
  if (errorText) {
    sections.push({ key: 'error', label: '错误', content: errorText, kind: 'code' });
  }
  if (statusOf(part) === 'running' && !resultText && !errorText) {
    sections.push({
      key: 'progress',
      label: '状态',
      content: '正在执行，等待工具返回结果…',
      kind: 'empty',
    });
  }
  if (!sections.length) {
    sections.push({ key: 'empty', label: '详情', content: '没有可展示的调用内容。', kind: 'empty' });
  }
  return sections;
};

const isCallExpanded = (part: ChatMessagePart) => Boolean(part.id && expandedCalls.value[part.id]);

const toggleCall = (part: ChatMessagePart) => {
  if (!part.id) return;
  expandedCalls.value = {
    ...expandedCalls.value,
    [part.id]: !expandedCalls.value[part.id],
  };
};

const callDetailsId = (part: ChatMessagePart) => `tool-call-details-${part.id || 'unknown'}`;

const copySection = async (section: DetailSection) => {
  const copied = await copyTextToClipboard(section.content);
  if (copied) {
    ElMessage.success(`${section.label}已复制`);
  } else {
    ElMessage.warning('复制失败，请手动选择内容');
  }
};
</script>
