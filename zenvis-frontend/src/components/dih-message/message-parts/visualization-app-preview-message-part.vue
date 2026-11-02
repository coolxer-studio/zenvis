<template>
  <section class="data-app-preview">
    <header class="data-app-preview__header">
      <div class="data-app-preview__title">
        <el-icon><Monitor /></el-icon>
        <span>{{ part.title || appName || '数据应用预览' }}</span>
        <el-tag size="small" effect="plain">{{ modeLabel }}</el-tag>
        <el-tag v-if="partialMenu" size="small" type="warning" effect="plain">菜单待重试</el-tag>
        <el-tag v-else-if="validated" size="small" type="success" effect="plain">校验通过</el-tag>
      </div>
      <div class="data-app-preview__actions">
        <el-button
          v-if="interactive && (validated || partialMenu)"
          size="small"
          type="primary"
          :loading="part.metadata?.deciding === true"
          @click="confirmPublish"
        >
          {{ partialMenu ? '重试菜单发布' : '确认发布应用' }}
        </el-button>
        <el-button size="small" :icon="RefreshRight" @click="reloadPreview">刷新预览</el-button>
      </div>
    </header>

    <div class="data-app-preview__meta">
      <span>应用：{{ appId || '-' }}</span>
      <span>版本：v{{ appVersion || 1 }}</span>
      <span>工作流：{{ workflowId || '-' }}</span>
      <span v-if="modeChanged">类型变化：{{ previousMode }} → {{ mode }}</span>
    </div>

    <div v-if="part.content" class="data-app-preview__description">{{ part.content }}</div>

    <div v-if="iframeUrl" class="data-app-preview__frame-wrap">
      <iframe
        :key="frameKey"
        :src="iframeUrl"
        class="data-app-preview__frame"
        title="数据应用真实数据预览"
        sandbox="allow-scripts allow-same-origin allow-forms"
        referrerpolicy="no-referrer"
      />
    </div>
    <el-empty v-else description="暂无可用的受控预览地址" :image-size="72" />

    <div class="data-app-preview__grid">
      <section class="data-app-preview__card">
        <h4>页面与文件</h4>
        <div class="data-app-preview__pages">
          <el-tag v-for="page in pages" :key="page.pageId" size="small" effect="plain">
            {{ page.title || page.pageId }}
          </el-tag>
          <span v-if="!pages.length" class="data-app-preview__muted">无页面信息</span>
        </div>
        <ul class="data-app-preview__files">
          <li v-for="file in files" :key="file.path">
            <code>{{ file.path }}</code>
            <span>{{ formatSize(file.size) }}</span>
          </li>
        </ul>
        <p v-if="deletedFiles.length" class="data-app-preview__muted">
          将删除：{{ deletedFiles.join('、') }}
        </p>
      </section>

      <section class="data-app-preview__card">
        <h4>校验摘要</h4>
        <p><span>候选摘要</span><code>{{ shortDigest(candidateDigest) || '-' }}</code></p>
        <p><span>验证摘要</span><code>{{ shortDigest(validationDigest) || '-' }}</code></p>
        <p><span>规格摘要</span><code>{{ shortDigest(specDigest) || '-' }}</code></p>
        <p><span>Meta 摘要</span><code>{{ shortDigest(metaDigest) || '-' }}</code></p>
        <div v-if="querySamples.length" class="data-app-preview__samples">
          <el-tag v-for="sample in querySamples" :key="sample.label" size="small" effect="plain">
            {{ sample.label }}：{{ sample.value }}
          </el-tag>
        </div>
        <ul v-if="diagnostics.length" class="data-app-preview__diagnostics">
          <li v-for="(item, index) in diagnostics" :key="`${item.code}-${index}`">
            <el-tag :type="item.severity === 'error' ? 'danger' : 'warning'" size="small">
              {{ item.code }}
            </el-tag>
            <span>{{ item.message }}</span>
          </li>
        </ul>
        <p v-else class="data-app-preview__ok">未发现阻断问题</p>
      </section>

      <section class="data-app-preview__card">
        <h4>菜单方案</h4>
        <p><span>名称</span>{{ menuName || '-' }}</p>
        <p><span>类型</span>{{ menuType || mode || '-' }}</p>
        <p><span>菜单 ID</span>{{ menuId || '发布后生成' }}</p>
        <p><span>来源</span><code>data-app:{{ appId || '-' }}</code></p>
        <p><span>发布状态</span>{{ menuStatusLabel }}</p>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Monitor, RefreshRight } from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { sanitizeIframeUrl } from '@/utils/url';
import { resolveVisualizationAppPreviewState } from './visualization-app-preview-state';

const props = defineProps<{ part: ChatMessagePart; interactive?: boolean }>();
const emit = defineEmits<{
  (e: 'chooseVisualizationAppDecision', payload: {
    part: ChatMessagePart;
    decision: 'apply_config' | 'retry';
  }): void;
}>();

const state = computed(() => resolveVisualizationAppPreviewState(props.part));
const frameKey = ref(0);
const appId = computed(() => state.value.appId);
const appName = computed(() => state.value.appName);
const appVersion = computed(() => state.value.appVersion);
const previousMode = computed(() => state.value.previousMode);
const mode = computed(() => state.value.mode);
const workflowId = computed(() => state.value.workflowId);
const candidateDigest = computed(() => state.value.candidateDigest);
const validationDigest = computed(() => state.value.validationDigest);
const specDigest = computed(() => state.value.specDigest);
const metaDigest = computed(() => state.value.metaDigest);
const validated = computed(() => state.value.validated);
const partialMenu = computed(() => state.value.partialMenu);
const modeLabel = computed(() => state.value.modeLabel);
const modeChanged = computed(() => state.value.modeChanged);
const files = computed(() => state.value.files);
const deletedFiles = computed(() => state.value.deletedFiles);
const diagnostics = computed(() => state.value.diagnostics);
const pages = computed(() => state.value.pages);
const menuName = computed(() => state.value.menu.menuName);
const menuType = computed(() => state.value.menu.menuType);
const menuId = computed(() => state.value.menu.menuId);
const menuStatusLabel = computed(() => state.value.menuStatusLabel);
const querySamples = computed(() => state.value.querySamples);

const iframeUrl = computed(() => {
  const siteUrl = state.value.previewSiteUrl;
  if (!siteUrl) return '';
  const params = new URLSearchParams({
    siteApi: siteUrl,
    baseUrl: (import.meta.env.VITE_BASE_URL || '').replace(/\/+$/, ''),
  });
  return sanitizeIframeUrl(`/amis/app.html?${params.toString()}`);
});

const shortDigest = (value: string) => value ? `${value.slice(0, 12)}…` : '';
const formatSize = (value?: number) => value === undefined ? '' : `${Math.max(1, Math.round(value / 1024))} KB`;

const confirmPublish = async () => {
  if (!props.interactive) return;
  const label = partialMenu.value ? '重试菜单发布' : '发布应用配置与菜单';
  try {
    await ElMessageBox.confirm(
      partialMenu.value
        ? '仅重试已锁定版本的菜单发布，不会重写应用文件。确认继续？'
        : '将一次写入应用配置并发布菜单，随后执行全量读回。确认继续？',
      label,
      { confirmButtonText: '确认', cancelButtonText: '返回', type: 'warning' },
    );
    emit('chooseVisualizationAppDecision', {
      part: props.part,
      decision: state.value.publishDecision,
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
.data-app-preview { margin: 12px 0; padding: 16px; border: 1px solid #e3eaf5; border-radius: 12px; background: #fff; }
.data-app-preview__header, .data-app-preview__title, .data-app-preview__actions, .data-app-preview__meta { display: flex; align-items: center; gap: 8px; }
.data-app-preview__header { justify-content: space-between; }
.data-app-preview__title { font-weight: 600; color: #1f2d3d; }
.data-app-preview__meta { flex-wrap: wrap; margin-top: 8px; color: #7a8799; font-size: 12px; }
.data-app-preview__description { margin-top: 8px; color: #4e5b70; }
.data-app-preview__frame-wrap { margin-top: 12px; height: 520px; border: 1px solid #e8edf5; border-radius: 10px; overflow: hidden; background: #f6f8fc; }
.data-app-preview__frame { width: 100%; height: 100%; border: 0; }
.data-app-preview__grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: 12px; margin-top: 12px; }
.data-app-preview__card { padding: 12px; border: 1px solid #edf1f7; border-radius: 10px; background: #fafcff; min-width: 0; }
.data-app-preview__card h4 { margin: 0 0 8px; font-size: 13px; color: #26364a; }
.data-app-preview__card p { display: flex; justify-content: space-between; gap: 8px; margin: 6px 0; color: #56657a; font-size: 12px; }
.data-app-preview__card code { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.data-app-preview__pages, .data-app-preview__samples { display: flex; flex-wrap: wrap; gap: 6px; }
.data-app-preview__files { margin: 8px 0 0; padding: 0; list-style: none; max-height: 150px; overflow: auto; }
.data-app-preview__files li { display: flex; justify-content: space-between; gap: 8px; padding: 4px 0; border-top: 1px dashed #e8edf5; font-size: 12px; }
.data-app-preview__diagnostics { margin: 8px 0 0; padding: 0; list-style: none; }
.data-app-preview__diagnostics li { display: flex; gap: 6px; align-items: flex-start; margin-top: 6px; font-size: 12px; color: #8a5b00; }
.data-app-preview__muted { color: #98a3b3; font-size: 12px; }
.data-app-preview__ok { color: #24865e !important; }
@media (max-width: 720px) {
  .data-app-preview__frame-wrap { height: 360px; }
  .data-app-preview__header { align-items: flex-start; flex-direction: column; }
}
</style>
