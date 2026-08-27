<template>
  <div class="panel right-panel">
    <el-tabs v-model="activeTab" class="right-tabs">
      <el-tab-pane label="配置记录" name="records">
        <template #default>
          <div class="section-toolbar">
            <div>
              <div class="section-title">配置变更记录</div>
              <div class="section-subtitle">共 {{ records.length }} 条</div>
            </div>
            <el-tag :type="records.length ? 'success' : 'info'" effect="plain">
              {{ records.length ? '已记录' : '暂无记录' }}
            </el-tag>
          </div>

          <el-empty v-if="!records.length" description="暂无配置记录" :image-size="72" />
          <el-table v-else :data="records" stripe table-layout="fixed" class="record-table">
            <el-table-column label="配置类型" min-width="110" show-overflow-tooltip>
              <template #default="scope">{{ cellText(scope.row.configType, '-') }}</template>
            </el-table-column>
            <el-table-column label="文件" min-width="140" show-overflow-tooltip>
              <template #default="scope">{{ cellText(scope.row.fileName, '-') }}</template>
            </el-table-column>
            <el-table-column label="变更说明" min-width="180" show-overflow-tooltip>
              <template #default="scope">{{ cellText(scope.row.changeDescription) }}</template>
            </el-table-column>
            <el-table-column label="变更方式" width="88">
              <template #default="scope">{{ changeModeText(scope.row.changeMode) }}</template>
            </el-table-column>
            <el-table-column label="差异" min-width="220" show-overflow-tooltip>
              <template #default="scope">{{ diffSummary(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="验证状态" width="104">
              <template #default="scope">
                <el-tag
                  size="small"
                  :type="validationTagType(scope.row.validationStatus)"
                  effect="plain"
                >
                  {{ validationStatusText(scope.row.validationStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="生效状态" width="92">
              <template #default="scope">
                <el-tag
                  size="small"
                  :type="effectiveTagType(scope.row.effectiveStatus)"
                  effect="plain"
                >
                  {{ effectiveStatusText(scope.row.effectiveStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="224" fixed="right">
              <template #default="scope">
                <div class="record-actions">
                  <el-button size="small" @click="showDiff(scope.row)">对比</el-button>
                  <el-button
                    size="small"
                    type="primary"
                    plain
                    :disabled="isEffective(scope.row)"
                    @click="requestTrial(scope.row)"
                    >试验</el-button
                  >
                  <el-button
                    size="small"
                    type="success"
                    plain
                    :disabled="!canApply(scope.row)"
                    @click="requestApply(scope.row)"
                    >下发</el-button
                  >
                </div>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>

      <el-tab-pane label="试验场验证" name="trial">
        <template #default>
          <el-empty v-if="!trialRecords.length" description="暂无试验结果" :image-size="72" />
          <div v-else class="trial-list">
            <div v-for="record in trialRecords" :key="recordKey(record)" class="trial-item">
              <div class="trial-header">
                <div>
                  <div class="section-title">{{ cellText(record.fileName, '配置验证') }}</div>
                  <div class="section-subtitle">{{ cellText(record.changeDescription) }}</div>
                </div>
                <el-tag :type="validationTagType(record.validationStatus)" effect="plain">
                  {{ validationStatusText(record.validationStatus) }}
                </el-tag>
              </div>
              <pre class="json-result">{{ prettyJson(record.validationResult || {}) }}</pre>
            </div>
          </div>
        </template>
      </el-tab-pane>

      <el-tab-pane label="正式生效" name="effective">
        <template #default>
          <el-empty v-if="!applicationRecords.length" description="暂无下发结果" :image-size="72" />
          <div v-else class="trial-list">
            <div v-for="record in applicationRecords" :key="recordKey(record)" class="trial-item">
              <div class="trial-header">
                <div>
                  <div class="section-title">{{ cellText(record.fileName, '配置下发') }}</div>
                  <div class="section-subtitle">{{ cellText(record.changeDescription) }}</div>
                </div>
                <el-tag :type="effectiveTagType(record.effectiveStatus)" effect="plain">
                  {{ effectiveStatusText(record.effectiveStatus) }}
                </el-tag>
              </div>
              <pre class="json-result">{{ prettyJson(record.applyResult || {}) }}</pre>
            </div>
          </div>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="diffVisible"
      title="配置差异对比"
      width="86vw"
      class="diff-dialog"
      @opened="renderDiffEditor"
      @closed="disposeDiffEditor"
    >
      <div class="diff-meta">
        <span>旧配置：{{ diffFileName }}</span>
        <span>新配置：{{ diffFileName }}</span>
      </div>
      <div ref="diffEditorContainer" class="diff-editor"></div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import * as monaco from 'monaco-editor';
import type { ConfigRecord } from '@/types/type-dih';
import { setupMonacoWorkers } from '@u/monaco-workers';
import {
  CONFIG_RECORD_ACTION_EVENT,
  CONFIG_RECORD_EVENT,
  CONFIG_RECORD_REQUEST_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events';
import type { ConfigRecordEventDetail } from '../events';

const activeTab = ref('records');
const records = ref<ConfigRecord[]>([]);
const diffVisible = ref(false);
const diffFileName = ref('config.json');
const diffLanguage = ref('plaintext');
const oldConfigText = ref('');
const newConfigText = ref('');
const diffEditorContainer = ref<HTMLElement | null>(null);

let diffEditor: monaco.editor.IStandaloneDiffEditor | null = null;
let originalModel: monaco.editor.ITextModel | null = null;
let modifiedModel: monaco.editor.ITextModel | null = null;

const trialRecords = computed(() =>
  records.value.filter(
    record => record.validationStatus && record.validationStatus !== 'unverified',
  ),
);
const applicationRecords = computed(() =>
  records.value.filter(record => {
    if (record.effectiveStatus === 'yes') return true;
    const result = record.applyResult;
    return typeof result === 'string'
      ? !!result.trim()
      : !!result && typeof result === 'object' && Object.keys(result).length > 0;
  }),
);

const cellText = (value: unknown, fallback = '') => {
  if (value === undefined || value === null || value === '') return fallback;
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return fallback;
  }
};

const prettyJson = (value: unknown) => {
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return cellText(value);
  }
};

const compactConfig = (value: unknown) => {
  const text = prettyJson(value);
  if (!text || text === '""') return '-';
  return text.replace(/\s+/g, ' ').slice(0, 80);
};

const diffSummary = (record: ConfigRecord) => {
  const oldValue = compactConfig(record.oldConfig);
  const newValue = compactConfig(record.newConfig);
  if (record.changeMode === 'add') return `新增：${newValue}`;
  if (oldValue === newValue) return '内容无变化';
  return `${oldValue} → ${newValue}`;
};

const recordKey = (record: ConfigRecord) =>
  cellText(record.id || record.recordId || record.fileName || record.updatedAt);

const changeModeText = (mode?: string) => {
  if (mode === 'add') return '新增';
  if (mode === 'modify') return '修改';
  return mode || '-';
};

const validationStatusText = (status?: string) => {
  if (status === 'success') return '验证成功';
  if (status === 'failed') return '验证失败';
  if (status === 'blocked') return '验证阻塞';
  return '未验证';
};

const validationTagType = (status?: string) => {
  if (status === 'success') return 'success';
  if (status === 'failed') return 'danger';
  if (status === 'blocked') return 'warning';
  return 'info';
};

const effectiveStatusText = (status?: string) => (status === 'yes' ? '是' : '否');

const effectiveTagType = (status?: string) => (status === 'yes' ? 'success' : 'info');

const isEffective = (record: ConfigRecord) => record.effectiveStatus === 'yes';

const canApply = (record: ConfigRecord) =>
  record.validationStatus === 'success' && record.effectiveStatus !== 'yes';

const editorLanguage = (record: ConfigRecord) => {
  const format = cellText(record.format).toLowerCase();
  if (format === 'json') return 'json';
  if (format === 'xml') return 'xml';
  if (format === 'properties' || format === 'conf') return 'ini';
  if (format === 'csv') return 'plaintext';
  return 'plaintext';
};

const configText = (value: unknown) => {
  if (value === undefined || value === null || value === '') return '';
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
};

const disposeDiffModels = () => {
  originalModel?.dispose();
  modifiedModel?.dispose();
  originalModel = null;
  modifiedModel = null;
};

const disposeDiffEditor = () => {
  diffEditor?.dispose();
  diffEditor = null;
  disposeDiffModels();
};

const renderDiffEditor = async () => {
  await nextTick();
  if (!diffEditorContainer.value) return;

  setupMonacoWorkers();
  disposeDiffEditor();

  originalModel = monaco.editor.createModel(oldConfigText.value, diffLanguage.value);
  modifiedModel = monaco.editor.createModel(newConfigText.value, diffLanguage.value);
  diffEditor = monaco.editor.createDiffEditor(diffEditorContainer.value, {
    automaticLayout: true,
    readOnly: true,
    originalEditable: false,
    renderSideBySide: true,
    enableSplitViewResizing: true,
    ignoreTrimWhitespace: false,
    renderIndicators: true,
    scrollBeyondLastLine: false,
    minimap: { enabled: false },
    fontSize: 13,
    lineNumbersMinChars: 3,
    diffAlgorithm: 'advanced',
  });
  diffEditor.setModel({
    original: originalModel,
    modified: modifiedModel,
  });
};

const showDiff = (record: ConfigRecord) => {
  diffFileName.value = record.fileName || 'config.json';
  diffLanguage.value = editorLanguage(record);
  oldConfigText.value = configText(record.oldConfig);
  newConfigText.value = configText(record.newConfig);
  diffVisible.value = true;
  if (diffEditor) renderDiffEditor();
};

const requestTrial = (record: ConfigRecord) => {
  activeTab.value = 'trial';
  emitDihEvent(CONFIG_RECORD_ACTION_EVENT, { action: 'trial', record });
};

const requestApply = (record: ConfigRecord) => {
  emitDihEvent(CONFIG_RECORD_ACTION_EVENT, { action: 'apply', record });
};

const handleRecordsUpdated = (detail: ConfigRecordEventDetail) => {
  detail ||= {};
  records.value = Array.isArray(detail.records) ? detail.records : [];
};

useDihEventListener(CONFIG_RECORD_EVENT, handleRecordsUpdated);

onMounted(() => {
  emitDihEvent(CONFIG_RECORD_REQUEST_EVENT);
});

onBeforeUnmount(() => {
  disposeDiffEditor();
});
</script>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  box-sizing: border-box;
  background: #f5f7fa;
  color: #303133;
  overflow: hidden;
}

.right-panel {
  padding: 0;
}

.right-tabs {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
}

:deep(.el-tabs__content) {
  height: calc(100% - 40px);
  overflow-y: auto;
}

:deep(.el-tabs__nav) {
  padding: 0 12px;
}

:deep(.el-tabs__item) {
  height: 40px;
  line-height: 40px;
}

.section-toolbar,
.trial-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-title {
  color: #303133;
  font-weight: 600;
}

.section-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.record-table {
  width: 100%;
}

.record-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.record-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.trial-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trial-item {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
}

.json-result {
  max-height: 420px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-radius: 6px;
  background: #f7f8fa;
  color: #303133;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.diff-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  margin-bottom: 8px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
  color: #606266;
  font-size: 12px;
}

.diff-meta span {
  padding: 8px 10px;
  overflow: hidden;
  background: #f5f7fa;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.diff-editor {
  height: min(68vh, 640px);
  min-height: 420px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
}

:deep(.diff-dialog .el-dialog__body) {
  padding-top: 10px;
}
</style>
