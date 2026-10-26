<template>
  <section class="html-preview-card">
    <header class="html-preview-header">
      <div class="html-preview-title">
        <span class="html-preview-language">{{ title || 'html' }}</span>
      </div>
      <div class="html-preview-tools">
        <el-tooltip :content="viewMode === 'preview' ? '查看源码' : '查看页面'" placement="top">
          <el-button
            class="html-preview-icon-btn"
            size="small"
            :icon="viewMode === 'preview' ? EditPen : View"
            :aria-label="viewMode === 'preview' ? '查看源码' : '查看页面'"
            circle
            @click="toggleViewMode"
          />
        </el-tooltip>
        <el-tooltip content="复制 HTML 源码" placement="top">
          <el-button
            class="html-preview-icon-btn"
            size="small"
            :icon="CopyDocument"
            aria-label="复制 HTML 源码"
            circle
            @click="copySource"
          />
        </el-tooltip>
        <el-tooltip content="下载 HTML" placement="top">
          <el-button
            class="html-preview-icon-btn"
            size="small"
            :icon="Download"
            :disabled="!canDownload"
            aria-label="下载 HTML"
            circle
            @click="downloadSource"
          />
        </el-tooltip>
        <el-tooltip :content="isExpanded ? '折叠' : '展开'" placement="top">
          <el-button
            class="html-preview-icon-btn"
            size="small"
            :icon="isExpanded ? CaretTop : CaretBottom"
            :aria-label="isExpanded ? '折叠 HTML 预览' : '展开 HTML 预览'"
            circle
            @click="toggleExpanded"
          />
        </el-tooltip>
      </div>
    </header>

    <div v-if="isExpanded" class="html-preview-body">
      <pre
        v-if="viewMode === 'source' && source.trim()"
        class="html-preview-source"
      ><code>{{ source }}</code></pre>
      <div v-else-if="!source.trim()" class="html-preview-state">
        <div class="html-preview-error" role="alert">
          <el-icon><WarningFilled /></el-icon>
          <div>
            <strong>HTML 页面为空</strong>
            <p>请提供可预览的 HTML 内容。</p>
          </div>
        </div>
      </div>
      <iframe
        v-else
        class="html-preview-frame"
        :srcdoc="source"
        :sandbox="HTML_PREVIEW_SANDBOX"
        :referrerpolicy="HTML_PREVIEW_REFERRER_POLICY"
        title="HTML 页面预览"
      ></iframe>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  CaretBottom,
  CaretTop,
  CopyDocument,
  Download,
  EditPen,
  View,
  WarningFilled,
} from '@element-plus/icons-vue';
import { copyTextToClipboard } from '@/utils/clipboard';
import {
  HTML_PREVIEW_REFERRER_POLICY,
  HTML_PREVIEW_SANDBOX,
  downloadHtmlDocument,
} from '@/utils/html-download';

defineOptions({ name: 'HtmlPreview' });

type Props = {
  source: string;
  title?: string;
  defaultCollapsed?: boolean;
  fileName?: string;
};

type ViewMode = 'preview' | 'source';

const props = withDefaults(defineProps<Props>(), {
  title: '',
  defaultCollapsed: false,
  fileName: '',
});

const expandedOverride = ref<boolean>();
const viewMode = ref<ViewMode>('preview');

const isExpanded = computed(() => expandedOverride.value ?? !props.defaultCollapsed);
const canDownload = computed(() => Boolean(props.source.trim()));

const toggleExpanded = () => {
  expandedOverride.value = !isExpanded.value;
};

const toggleViewMode = () => {
  viewMode.value = viewMode.value === 'preview' ? 'source' : 'preview';
};

const copySource = async () => {
  const copied = await copyTextToClipboard(props.source);
  if (copied) {
    ElMessage.success('HTML 源码已复制');
  } else {
    ElMessage.warning('复制失败，请手动选择内容');
  }
};

const normalizeError = (error: unknown): string => {
  const message = error instanceof Error ? error.message : String(error || '');
  return message.replace(/^Error:\s*/i, '').trim() || 'HTML 文件下载失败';
};

const downloadSource = () => {
  if (!canDownload.value) return;
  try {
    downloadHtmlDocument(props.source, {
      fileName: props.fileName,
      title: props.title,
    });
    ElMessage.success('HTML 文件已下载');
  } catch (error) {
    ElMessage.error(normalizeError(error));
  }
};
</script>

<style scoped>
.html-preview-card {
  max-width: 100%;
  min-width: 0;
  margin: 0.9em 0;
  overflow: hidden;
  background: var(--zv-bg-surface, #fff);
  border: 1px solid var(--zv-border, #dce4ef);
  border-radius: var(--zv-radius-md, 8px);
}

.html-preview-header {
  display: flex;
  min-width: 0;
  padding: 8px 10px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: #cfd3dc;
  background: #2b3037;
}

.html-preview-title,
.html-preview-tools {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.html-preview-tools {
  flex: 0 0 auto;
}

.html-preview-language {
  min-width: 0;
  font-size: 12px;
  line-height: 1;
  overflow-wrap: anywhere;
}

.html-preview-icon-btn {
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

.html-preview-icon-btn:not(:disabled):hover,
.html-preview-icon-btn:not(:disabled):focus-visible {
  color: #fff;
  background: rgb(255 255 255 / 10%);
  border-color: #6a7482;
}

.html-preview-icon-btn.is-disabled,
.html-preview-icon-btn.is-disabled:hover {
  color: #7f8792;
  background: transparent;
  border-color: #444b55;
}

.html-preview-body {
  min-width: 0;
}

.html-preview-frame {
  display: block;
  width: 100%;
  height: 480px;
  border: 0;
  background: #fff;
}

.html-preview-state {
  padding: 16px;
}

.html-preview-error {
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

.html-preview-error > .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
}

.html-preview-error p {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
}

.html-preview-card .html-preview-source {
  max-height: 480px;
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

.html-preview-card .html-preview-source code {
  padding: 0;
  color: inherit;
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  background: transparent;
  border: 0;
}

@media (max-width: 640px) {
  .html-preview-header {
    padding: 7px 8px;
  }

  .html-preview-tools {
    gap: 5px;
  }
}
</style>
