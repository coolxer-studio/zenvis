<template>
  <section class="dashboard-preview">
    <header class="dashboard-preview__header">
      <div class="dashboard-preview__title">
        <el-icon><Monitor /></el-icon>
        <span>{{ part.title || dashboardName || '数据大屏预览' }}</span>
        <el-tag size="small" effect="plain">{{ modeLabel }}</el-tag>
        <el-tag v-if="validated" size="small" type="success" effect="plain">校验通过</el-tag>
        <el-tag v-else size="small" type="warning" effect="plain">待校验</el-tag>
      </div>
      <div class="dashboard-preview__actions">
        <el-button
          v-if="interactive && canPublish"
          size="small"
          type="primary"
          :loading="part.metadata?.deciding === true"
          @click="confirmPublish"
        >
          {{ publishLabel }}
        </el-button>
        <el-button size="small" :icon="RefreshRight" @click="reloadPreview">刷新预览</el-button>
      </div>
    </header>

    <div class="dashboard-preview__meta">
      <span>大屏：{{ dashboardId || '-' }}</span>
      <span>版本：v{{ dashboardVersion || 1 }}</span>
      <span>主题：{{ theme }}</span>
      <span>刷新：{{ refreshIntervalSeconds }} 秒</span>
      <span>时间口径：{{ defaultTimeRange }}</span>
      <span v-if="modeChanged" class="dashboard-preview__warning">
        模式变化：{{ previousMode }} → {{ mode }}
      </span>
    </div>

    <div v-if="part.content" class="dashboard-preview__description">{{ part.content }}</div>

    <div v-if="iframeUrl" class="dashboard-preview__frame-wrap">
      <iframe
        :key="frameKey"
        :src="iframeUrl"
        class="dashboard-preview__frame"
        title="数据大屏真实数据预览"
        sandbox="allow-scripts allow-same-origin"
        referrerpolicy="no-referrer"
      />
    </div>
    <el-empty v-else description="暂无可用的受控预览地址" :image-size="72" />

    <div class="dashboard-preview__grid">
      <section class="dashboard-preview__card">
        <h4>区块与代表性数据</h4>
        <ul class="dashboard-preview__blocks">
          <li v-for="block in blocks" :key="block.blockId">
            <el-tag size="small" effect="plain">{{ blockTypeLabel(block.type) }}</el-tag>
            <span class="dashboard-preview__block-title">{{ block.title }}</span>
            <code>{{ block.queryTool || block.blockId }}</code>
            <el-tag v-if="block.empty" size="small" type="warning" effect="plain">空结果</el-tag>
          </li>
        </ul>
        <p v-if="!blocks.length" class="dashboard-preview__muted">无区块摘要</p>
        <div v-if="querySamples.length" class="dashboard-preview__samples">
          <el-tag
            v-for="sample in querySamples"
            :key="sample.blockId"
            size="small"
            :type="sample.empty ? 'warning' : 'success'"
            effect="plain"
          >
            {{ sample.label }}：{{ sample.value }}
          </el-tag>
        </div>
      </section>

      <section class="dashboard-preview__card">
        <h4>校验与文件摘要</h4>
        <p><span>候选摘要</span><code>{{ shortDigest(candidateDigest) || '-' }}</code></p>
        <p><span>验证摘要</span><code>{{ shortDigest(validationDigest) || '-' }}</code></p>
        <p><span>规格摘要</span><code>{{ shortDigest(specDigest) || '-' }}</code></p>
        <p><span>Meta 摘要</span><code>{{ shortDigest(metaDigest) || '-' }}</code></p>
        <p><span>文件摘要</span><code>{{ shortDigest(fileDigest) || '-' }}</code></p>
        <ul class="dashboard-preview__files">
          <li v-for="file in files" :key="file.path">
            <code>{{ file.path }}</code>
            <span>{{ formatSize(file.size) }}</span>
          </li>
        </ul>
      </section>

      <section class="dashboard-preview__card">
        <h4>诊断</h4>
        <ul v-if="diagnostics.length" class="dashboard-preview__diagnostics">
          <li v-for="(item, index) in diagnostics" :key="`${item.code}-${index}`">
            <el-tag :type="item.severity === 'error' ? 'danger' : 'warning'" size="small">
              {{ item.code }}
            </el-tag>
            <span>{{ item.message }}</span>
          </li>
        </ul>
        <p v-else class="dashboard-preview__ok">未发现阻断问题</p>
        <p class="dashboard-preview__muted">
          只读大屏：明细表仅支持展示与筛选，不包含新增、编辑或删除操作；发布不会创建菜单。
        </p>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Monitor, RefreshRight } from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { sanitizeIframeUrl, withBaseUrl } from '@/utils/url';
import { resolveVisualizationDashboardPreviewState } from './visualization-dashboard-preview-state';

const props = defineProps<{ part: ChatMessagePart; interactive?: boolean }>();
const emit = defineEmits<{
  (e: 'chooseVisualizationDashboardDecision', payload: {
    part: ChatMessagePart;
    decision: 'apply_config';
  }): void;
}>();

const state = computed(() => resolveVisualizationDashboardPreviewState(props.part));
const frameKey = ref(0);
const dashboardId = computed(() => state.value.dashboardId);
const dashboardName = computed(() => state.value.dashboardName);
const dashboardVersion = computed(() => state.value.dashboardVersion);
const modeLabel = computed(() => state.value.modeLabel);
const previousMode = computed(() => state.value.previousMode);
const mode = computed(() => state.value.mode);
const modeChanged = computed(() => state.value.modeChanged);
const theme = computed(() => state.value.theme);
const refreshIntervalSeconds = computed(() => state.value.refreshIntervalSeconds);
const defaultTimeRange = computed(() => state.value.defaultTimeRange);
const validated = computed(() => state.value.validated);
const canPublish = computed(() => state.value.canPublish);
const publishLabel = computed(() => state.value.publishLabel);
const candidateDigest = computed(() => state.value.candidateDigest);
const validationDigest = computed(() => state.value.validationDigest);
const specDigest = computed(() => state.value.specDigest);
const metaDigest = computed(() => state.value.metaDigest);
const fileDigest = computed(() => state.value.fileDigest);
const files = computed(() => state.value.files);
const diagnostics = computed(() => state.value.diagnostics);
const blocks = computed(() => state.value.blocks);
const querySamples = computed(() => state.value.querySamples);

const iframeUrl = computed(() => {
  if (!state.value.previewHtmlUrl) return '';
  return sanitizeIframeUrl(withBaseUrl(state.value.previewHtmlUrl), '');
});

const blockTypeLabel = (type: string) => {
  if (type === 'metrics') return '指标';
  if (type === 'chart') return '图表';
  if (type === 'records') return '明细';
  return type || '区块';
};
const shortDigest = (value: string) => value ? `${value.slice(0, 12)}…` : '';
const formatSize = (value?: number) => value === undefined ? '' : `${Math.max(1, Math.round(value / 1024))} KB`;

const confirmPublish = async () => {
  if (!props.interactive || !canPublish.value) return;
  try {
    await ElMessageBox.confirm(
      '将一次写入大屏配置并创建或更新 Dashboard，随后执行配置、清单和 Dashboard 全量读回。发布不会创建菜单。确认继续？',
      '发布数据大屏',
      { confirmButtonText: '确认发布', cancelButtonText: '返回', type: 'warning' },
    );
    emit('chooseVisualizationDashboardDecision', {
      part: props.part,
      decision: 'apply_config',
    });
  } catch {
    // User cancelled the confirmation dialog.
  }
};

const reloadPreview = () => {
  frameKey.value += 1;
  ElMessage.success('已刷新真实数据预览');
};
</script>

<style scoped>
.dashboard-preview { margin: 12px 0; padding: 16px; border: 1px solid #e3eaf5; border-radius: 12px; background: #fff; }
.dashboard-preview__header, .dashboard-preview__title, .dashboard-preview__actions, .dashboard-preview__meta { display: flex; align-items: center; gap: 8px; }
.dashboard-preview__header { justify-content: space-between; }
.dashboard-preview__title { font-weight: 600; color: #1f2d3d; }
.dashboard-preview__meta { flex-wrap: wrap; margin-top: 8px; color: #7a8799; font-size: 12px; }
.dashboard-preview__description { margin-top: 8px; color: #4e5b70; }
.dashboard-preview__frame-wrap { margin-top: 12px; height: min(68vh, 720px); min-height: 480px; border: 1px solid #dbe6f4; border-radius: 10px; overflow: hidden; background: #061426; }
.dashboard-preview__frame { width: 100%; height: 100%; border: 0; }
.dashboard-preview__grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 12px; margin-top: 12px; }
.dashboard-preview__card { padding: 12px; border: 1px solid #edf1f7; border-radius: 10px; background: #fafcff; min-width: 0; }
.dashboard-preview__card h4 { margin: 0 0 8px; font-size: 13px; color: #26364a; }
.dashboard-preview__card p { display: flex; justify-content: space-between; gap: 8px; margin: 6px 0; color: #56657a; font-size: 12px; }
.dashboard-preview__card code { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-preview__blocks, .dashboard-preview__files, .dashboard-preview__diagnostics { margin: 8px 0 0; padding: 0; list-style: none; max-height: 190px; overflow: auto; }
.dashboard-preview__blocks li, .dashboard-preview__files li { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 5px 0; border-top: 1px dashed #e8edf5; font-size: 12px; }
.dashboard-preview__block-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #26364a; }
.dashboard-preview__samples { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 9px; }
.dashboard-preview__diagnostics li { display: flex; gap: 6px; align-items: flex-start; margin-top: 6px; font-size: 12px; color: #8a5b00; }
.dashboard-preview__muted { color: #98a3b3; font-size: 12px; }
.dashboard-preview__warning { color: #b26b00; }
.dashboard-preview__ok { color: #24865e !important; }
@media (max-width: 720px) {
  .dashboard-preview__frame-wrap { height: 420px; min-height: 360px; }
  .dashboard-preview__header { align-items: flex-start; flex-direction: column; }
}
</style>
