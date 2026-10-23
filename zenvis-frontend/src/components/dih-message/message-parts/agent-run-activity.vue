<template>
  <section class="agent-run-activity" data-testid="agent-run-activity">
    <div class="activity-summary" role="group" aria-label="智能体执行过程分类">
      <button
        v-for="item in categoryCards"
        :key="item.kind"
        type="button"
        class="activity-summary-item"
        :class="{ 'is-active': activeFilter === item.kind }"
        :aria-pressed="activeFilter === item.kind"
        @click="toggleFilter(item.kind)"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span class="activity-summary-copy">
          <strong>{{ item.label }}</strong>
          <small>{{ item.detail }}</small>
        </span>
      </button>
    </div>

    <div v-if="summary.compactionCount === 0" class="activity-legacy-hint">
      无上下文压缩记录；旧任务可能尚未采集该诊断。
    </div>

    <div v-if="filteredActivities.length" class="activity-timeline">
      <article
        v-for="activity in filteredActivities"
        :key="activity.id"
        class="activity-entry"
        :class="`is-${activity.kind}`"
      >
        <span class="activity-entry-rail" aria-hidden="true">
          <span class="activity-entry-dot"></span>
        </span>
        <div class="activity-entry-content">
          <div class="activity-entry-part">
            <RagSearchPart
              v-if="activity.kind === 'rag'"
              :part="activityPart(activity)"
            />
            <ToolActivityPart
              v-else-if="activity.kind === 'tool'"
              :parts="[activityPart(activity)]"
            />
            <ContextCompactionPart
              v-else
              :part="activityPart(activity)"
            />
          </div>
        </div>
      </article>
    </div>
    <div v-else class="activity-empty">
      <el-icon><InfoFilled /></el-icon>
      <span>{{ activeFilter === 'all' ? '本次任务没有可展示的检索、工具或压缩过程。' : '当前分类暂无记录。' }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { InfoFilled, Refresh, Search, Tools } from '@element-plus/icons-vue';
import type {
  AgentRunActivity,
  AgentRunActivityKind,
  ChatMessagePart,
} from '@/types/type-dih';
import { ragSearchPartFromEvent } from '../rag-search-parts';
import { toolCallPartFromEvent } from '../tool-call-parts';
import ContextCompactionPart from './context-compaction-part.vue';
import RagSearchPart from './rag-search-part.vue';
import ToolActivityPart from './tool-activity-part.vue';
import { summarizeAgentRunActivities } from '@/views/dih/composables/agent-run-activity';

defineOptions({ name: 'AgentRunActivity' });

const props = defineProps<{
  activities: AgentRunActivity[];
}>();

const activeFilter = ref<'all' | AgentRunActivityKind>('all');
const summary = computed(() => summarizeAgentRunActivities(props.activities));

const formatNumber = (value: number) => new Intl.NumberFormat('zh-CN').format(value);

const categoryCards = computed(() => [
  {
    kind: 'rag' as const,
    label: '知识库检索',
    detail: `${summary.value.ragCount} 次 · ${formatNumber(summary.value.ragHits)} 个来源`,
    icon: Search,
  },
  {
    kind: 'tool' as const,
    label: '工具调用',
    detail: `${summary.value.toolCount} 次`
      + (summary.value.toolFailures ? ` · ${summary.value.toolFailures} 次失败` : ''),
    icon: Tools,
  },
  {
    kind: 'compaction' as const,
    label: '上下文压缩',
    detail: summary.value.compactionCount
      ? `${summary.value.compactionCount} 次 · 节省 ${formatNumber(summary.value.savedTokens)} tokens`
      : '0 次',
    icon: Refresh,
  },
]);

const filteredActivities = computed(() => activeFilter.value === 'all'
  ? props.activities
  : props.activities.filter(activity => activity.kind === activeFilter.value));

const toggleFilter = (kind: AgentRunActivityKind) => {
  activeFilter.value = activeFilter.value === kind ? 'all' : kind;
};

const activityPart = (activity: AgentRunActivity): ChatMessagePart => {
  if (activity.kind === 'rag') {
    return ragSearchPartFromEvent(activity.data) || {
      id: activity.id,
      type: 'rag-search',
      title: '知识库检索',
      status: activity.status,
      metadata: { ...activity.data },
    };
  }
  if (activity.kind === 'tool') {
    return toolCallPartFromEvent(activity.data) || {
      id: activity.id,
      type: 'tool-call',
      title: '工具调用',
      content: String(activity.data.toolName || '未知工具'),
      status: activity.status,
      metadata: { ...activity.data },
    };
  }
  return {
    id: activity.id,
    type: 'context-compaction',
    title: '上下文压缩',
    status: activity.status,
    metadata: { ...activity.data },
  };
};
</script>

<style lang="scss" scoped>
.agent-run-activity {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.activity-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.activity-summary-item {
  display: flex;
  min-width: 0;
  padding: 8px 9px;
  align-items: center;
  gap: 7px;
  text-align: left;
  cursor: pointer;
  background: var(--zv-bg-card);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-md);
}

.activity-summary-item.is-active {
  border-color: var(--zv-primary-border);
  background: var(--zv-primary-soft);
}

.activity-summary-item .el-icon {
  flex: 0 0 auto;
  color: var(--zv-primary);
}

.activity-summary-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.activity-summary-copy strong {
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-xs);
  font-weight: var(--zv-font-weight-medium);
}

.activity-summary-copy small {
  overflow: hidden;
  color: var(--zv-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-legacy-hint {
  color: var(--zv-text-muted);
  font-size: 11px;
}

.activity-timeline {
  display: flex;
  flex-direction: column;
}

.activity-entry {
  display: flex;
  min-width: 0;
  gap: 9px;
}

.activity-entry-rail {
  position: relative;
  display: flex;
  width: 14px;
  flex: 0 0 14px;
  justify-content: center;
}

.activity-entry-rail::after {
  position: absolute;
  top: 14px;
  bottom: -4px;
  width: 1px;
  content: '';
  background: var(--zv-border);
}

.activity-entry:last-child .activity-entry-rail::after {
  display: none;
}

.activity-entry-dot {
  width: 7px;
  height: 7px;
  margin-top: 12px;
  background: var(--zv-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 3px var(--zv-primary-soft);
}

.activity-entry.is-rag .activity-entry-dot {
  background: var(--el-color-primary);
}

.activity-entry.is-tool .activity-entry-dot {
  background: var(--el-color-success);
}

.activity-entry.is-compaction .activity-entry-dot {
  background: var(--el-color-warning);
}

.activity-entry-content {
  min-width: 0;
  padding: 5px 0 9px;
  flex: 1;
}

.activity-entry-part {
  min-width: 0;
}

.activity-empty {
  display: flex;
  padding: 10px 12px;
  align-items: center;
  gap: 7px;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
  background: var(--zv-bg-subtle);
  border-radius: var(--zv-radius-md);
}

@media (max-width: 640px) {
  .activity-summary {
    grid-template-columns: 1fr;
  }
}
</style>
