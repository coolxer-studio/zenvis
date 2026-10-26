<template>
  <section class="mermaid-card">
    <header class="mermaid-card-header">
      <div class="mermaid-card-title">
        <span class="mermaid-language">{{ title || 'mermaid' }}</span>
      </div>
      <div class="mermaid-card-tools">
        <el-tooltip :content="viewMode === 'diagram' ? '查看源码' : '查看图表'" placement="top">
          <el-button
            class="mermaid-icon-btn"
            size="small"
            :icon="viewMode === 'diagram' ? EditPen : View"
            :aria-label="viewMode === 'diagram' ? '查看源码' : '查看图表'"
            circle
            @click="toggleViewMode"
          />
        </el-tooltip>
        <el-tooltip content="复制 Mermaid 源码" placement="top">
          <el-button
            class="mermaid-icon-btn"
            size="small"
            :icon="CopyDocument"
            aria-label="复制 Mermaid 源码"
            circle
            @click="copySource"
          />
        </el-tooltip>
        <el-tooltip content="下载 PNG" placement="top">
          <el-button
            class="mermaid-icon-btn"
            size="small"
            :icon="Download"
            :loading="exporting"
            :disabled="!canDownload || exporting"
            aria-label="下载 PNG"
            circle
            @click="downloadPng"
          />
        </el-tooltip>
        <el-tooltip :content="isExpanded ? '折叠' : '展开'" placement="top">
          <el-button
            class="mermaid-icon-btn"
            size="small"
            :icon="isExpanded ? CaretTop : CaretBottom"
            :aria-label="isExpanded ? '折叠 Mermaid 图表' : '展开 Mermaid 图表'"
            circle
            @click="toggleExpanded"
          />
        </el-tooltip>
      </div>
    </header>

    <div v-if="isExpanded" class="mermaid-card-body">
      <pre v-if="viewMode === 'source'" class="mermaid-source"><code>{{ source }}</code></pre>
      <div v-else class="mermaid-diagram-view">
        <div v-if="isLoading" class="mermaid-state" aria-live="polite">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>Mermaid 图表渲染中…</span>
        </div>
        <div v-else-if="renderError" class="mermaid-error-wrap">
          <div class="mermaid-error" role="alert">
            <el-icon><WarningFilled /></el-icon>
            <div>
              <strong>图表渲染失败</strong>
              <p>{{ renderError }}</p>
            </div>
          </div>
          <pre class="mermaid-source mermaid-error-source"><code>{{ source }}</code></pre>
        </div>
        <div v-else class="mermaid-diagram-canvas" v-html="renderedSvg"></div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  CaretBottom,
  CaretTop,
  CopyDocument,
  Download,
  EditPen,
  Loading,
  View,
  WarningFilled,
} from '@element-plus/icons-vue';
import { copyTextToClipboard } from '@/utils/clipboard';
import { loadMermaid, nextMermaidRenderId, renderMermaid } from './mermaid-loader';

defineOptions({ name: 'MermaidDiagram' });

type Props = {
  source: string;
  title?: string;
  defaultCollapsed?: boolean;
};

type ViewMode = 'diagram' | 'source';

const props = withDefaults(defineProps<Props>(), {
  title: '',
  defaultCollapsed: false,
});

const PNG_PADDING = 24;
const PNG_SCALE = 2;
const PNG_MAX_SIDE = 8192;

const expandedOverride = ref<boolean>();
const viewMode = ref<ViewMode>('diagram');
const renderedSvg = ref('');
const renderError = ref('');
const isLoading = ref(true);
const exporting = ref(false);
let renderGeneration = 0;

const isExpanded = computed(() => expandedOverride.value ?? !props.defaultCollapsed);
const canDownload = computed(() =>
  Boolean(renderedSvg.value && !renderError.value && !isLoading.value),
);

const toggleExpanded = () => {
  expandedOverride.value = !isExpanded.value;
};

const toggleViewMode = () => {
  viewMode.value = viewMode.value === 'diagram' ? 'source' : 'diagram';
};

const copySource = async () => {
  const copied = await copyTextToClipboard(props.source);
  if (copied) {
    ElMessage.success('Mermaid 源码已复制');
  } else {
    ElMessage.warning('复制失败，请手动选择内容');
  }
};

const normalizeError = (error: unknown): string => {
  const message = error instanceof Error ? error.message : String(error || '');
  return message.replace(/^Error:\s*/i, '').trim() || 'Mermaid 源码解析失败';
};

const renderDiagram = async () => {
  const generation = ++renderGeneration;
  renderedSvg.value = '';
  renderError.value = '';

  if (!isExpanded.value) {
    isLoading.value = false;
    return;
  }
  if (!props.source.trim()) {
    renderError.value = 'Mermaid 源码为空';
    isLoading.value = false;
    return;
  }

  isLoading.value = true;
  try {
    await loadMermaid();
    if (generation !== renderGeneration) return;
    if (typeof document !== 'undefined' && document.fonts?.ready) {
      await document.fonts.ready;
    }
    if (generation !== renderGeneration) return;

    const result = await renderMermaid(nextMermaidRenderId(), props.source);
    if (generation !== renderGeneration) return;
    renderedSvg.value = result.svg;
  } catch (error) {
    if (generation !== renderGeneration) return;
    renderedSvg.value = '';
    renderError.value = normalizeError(error);
  } finally {
    if (generation === renderGeneration) {
      isLoading.value = false;
    }
  }
};

const svgDimensions = (svg: SVGSVGElement): { width: number; height: number } => {
  const viewBox = svg
    .getAttribute('viewBox')
    ?.trim()
    .split(/[\s,]+/)
    .map(value => Number(value));
  if (viewBox?.length === 4 && viewBox[2] > 0 && viewBox[3] > 0) {
    return { width: viewBox[2], height: viewBox[3] };
  }

  const rect = svg.getBoundingClientRect();
  return {
    width: rect.width > 0 ? rect.width : 800,
    height: rect.height > 0 ? rect.height : 600,
  };
};

const loadImage = (url: string): Promise<HTMLImageElement> => {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('无法加载 Mermaid SVG'));
    image.src = url;
  });
};

const canvasToBlob = (canvas: HTMLCanvasElement): Promise<Blob> => {
  return new Promise((resolve, reject) => {
    canvas.toBlob(blob => {
      if (blob) {
        resolve(blob);
      } else {
        reject(new Error('PNG 图片生成失败'));
      }
    }, 'image/png');
  });
};

const saveBlob = (blob: Blob) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `mermaid-diagram-${Date.now()}.png`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
};

const downloadPng = async () => {
  if (!canDownload.value || exporting.value) return;
  exporting.value = true;
  try {
    const container = document.createElement('div');
    container.innerHTML = renderedSvg.value;
    const svg = container.querySelector('svg');
    if (!svg) throw new Error('未找到可导出的 Mermaid SVG');

    const { width, height } = svgDimensions(svg);
    svg.setAttribute('width', String(width));
    svg.setAttribute('height', String(height));
    svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
    svg.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');

    const serialized = new XMLSerializer().serializeToString(svg);
    const image = await loadImage(
      `data:image/svg+xml;charset=utf-8,${encodeURIComponent(serialized)}`,
    );
    const rawWidth = width + PNG_PADDING * 2;
    const rawHeight = height + PNG_PADDING * 2;
    const scale = Math.min(PNG_SCALE, PNG_MAX_SIDE / rawWidth, PNG_MAX_SIDE / rawHeight);
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.floor(rawWidth * scale));
    canvas.height = Math.max(1, Math.floor(rawHeight * scale));
    const context = canvas.getContext('2d');
    if (!context) throw new Error('当前浏览器不支持 Canvas 导出');

    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.scale(scale, scale);
    context.drawImage(image, PNG_PADDING, PNG_PADDING, width, height);

    saveBlob(await canvasToBlob(canvas));
    ElMessage.success('Mermaid 图表已下载');
  } catch (error) {
    ElMessage.error(normalizeError(error));
  } finally {
    exporting.value = false;
  }
};

watch(
  [() => props.source, isExpanded],
  () => {
    void renderDiagram();
  },
  { immediate: true },
);
</script>

<style scoped>
.mermaid-card {
  max-width: 100%;
  min-width: 0;
  margin: 0.9em 0;
  overflow: hidden;
  background: var(--zv-bg-surface, #fff);
  border: 1px solid var(--zv-border, #dce4ef);
  border-radius: var(--zv-radius-md, 8px);
}

.mermaid-card-header {
  display: flex;
  min-width: 0;
  padding: 8px 10px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: #cfd3dc;
  background: #2b3037;
}

.mermaid-card-title,
.mermaid-card-tools {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.mermaid-card-tools {
  flex: 0 0 auto;
}

.mermaid-language {
  min-width: 0;
  font-size: 12px;
  line-height: 1;
  overflow-wrap: anywhere;
}

.mermaid-icon-btn {
  width: 28px;
  min-width: 28px;
  max-width: 28px;
  height: 28px;
  min-height: 28px;
  max-height: 28px;
  padding: 0;
  color: #cfd3dc;
  background: transparent;
  border-color: #4c5563;
  border-radius: 50%;
}

.mermaid-icon-btn:not(:disabled):hover,
.mermaid-icon-btn:not(:disabled):focus-visible {
  color: #fff;
  background: rgb(255 255 255 / 10%);
  border-color: #6a7482;
}

.mermaid-icon-btn.is-disabled,
.mermaid-icon-btn.is-disabled:hover {
  color: #7f8792;
  background: transparent;
  border-color: #444b55;
}

.mermaid-card-body {
  min-width: 0;
}

.mermaid-diagram-view {
  min-height: 120px;
  padding: 16px;
}

.mermaid-diagram-canvas {
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
}

.mermaid-diagram-canvas :deep(svg) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 0 auto;
}

.mermaid-state {
  display: flex;
  min-height: 120px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--zv-text-muted, #66758a);
  font-size: 13px;
}

.mermaid-error-wrap {
  min-width: 0;
}

.mermaid-error {
  display: flex;
  padding: 10px 12px;
  align-items: flex-start;
  gap: 8px;
  color: var(--zv-danger, #d1435b);
  font-size: 13px;
  line-height: 1.5;
  background: var(--zv-danger-soft, #fff0f3);
  border: 1px solid color-mix(in srgb, var(--zv-danger, #d1435b) 24%, var(--zv-border, #dce4ef));
  border-radius: var(--zv-radius-sm, 6px);
}

.mermaid-error > .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
}

.mermaid-error p {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
}

.mermaid-card .mermaid-source {
  max-height: 360px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  color: #f5f7fa;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  background: #1f2329;
}

.mermaid-card .mermaid-source code {
  padding: 0;
  color: inherit;
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  background: transparent;
  border: 0;
}

.mermaid-card .mermaid-error-source {
  margin-top: 10px;
  border-radius: var(--zv-radius-sm, 6px);
}

@media (max-width: 640px) {
  .mermaid-card-header {
    padding: 7px 8px;
  }

  .mermaid-card-tools {
    gap: 5px;
  }

  .mermaid-diagram-view {
    padding: 12px;
  }
}
</style>
