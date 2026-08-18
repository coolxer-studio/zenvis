<template>
  <div class="panel right-panel report-workbench">
    <div class="report-header">
      <div class="report-title-wrap">
        <div class="report-kicker">报表文档</div>
        <el-input
          data-testid="report-title"
          v-model="reportTitle"
          class="report-title-input"
          placeholder="未命名报表"
          @input="markDirty"
        />
      </div>
      <el-tag
        data-testid="report-save-status"
        size="small"
        :type="saveConflict ? 'danger' : isDirty ? 'warning' : 'success'"
        effect="plain"
      >
        {{ saveConflict ? '版本冲突' : isSaving ? '保存中…' : isDirty ? '未保存' : '已保存' }}
      </el-tag>
      <el-tag v-if="currentDocument?.revision" size="small" effect="plain">
        {{ currentDocument.version || `v${currentDocument.revision}` }}
      </el-tag>
      <el-tag
        v-if="currentDocument?.source === 'demo'
          || currentDocument?.raw?.demo
          || currentDocument?.sourceRefs?.some(ref => ref.type === 'demo')"
        size="small"
        type="warning"
      >
        演示数据
      </el-tag>
    </div>

    <div class="quick-actions">
      <el-tooltip
        v-for="action in quickActions"
        :key="action.key"
        :content="action.description"
        placement="top"
      >
        <el-button size="small" :icon="action.icon" @click="requestQuickAction(action)">
          {{ action.label }}
        </el-button>
      </el-tooltip>
    </div>

    <el-tabs v-model="activeTab" class="right-tabs">
      <el-tab-pane label="文档" name="document">
        <div class="document-layout">
          <aside class="catalogue">
            <div class="catalogue-title">大纲</div>
            <el-empty v-if="!outline.length" description="暂无标题" :image-size="54" />
            <ul v-else class="outline-list">
              <li
                v-for="(item, index) in outline"
                :key="item.id"
                :class="{ active: activeOutlineIndex === index }"
                :style="{ paddingLeft: `${12 + (item.level - 1) * 12}px` }"
                @click="scrollToHeading(index)"
              >
                {{ item.text }}
              </li>
            </ul>
            <div class="catalogue-title">来源</div>
            <el-empty
              v-if="!currentDocument?.sourceRefs?.length"
              description="暂无来源"
              :image-size="42"
            />
            <button
              v-for="source in currentDocument?.sourceRefs || []"
              :key="materialKey(source)"
              type="button"
              class="source-ref"
              @click="openSourceRef(source)"
            >
              <span>{{ source.name || source.type || source.id || '来源' }}</span>
              <el-tag
                v-if="sourceNeedsConfirmation(source)"
                size="small"
                type="warning"
              >
                待确认
              </el-tag>
            </button>
          </aside>

          <section
            ref="editorShellRef"
            class="editor-shell"
            @contextmenu.capture="handleEditorContextMenu"
          >
            <div class="editor-actions">
              <el-button
                data-testid="report-save"
                type="primary"
                size="small"
                :icon="Finished"
                :loading="isSaving"
                @click="saveDocument()"
              >
                保存
              </el-button>
              <el-button size="small" :icon="RefreshLeft" @click="undoEdit">
                撤销
              </el-button>
              <el-button size="small" :icon="Collection" @click="materialDialogVisible = true">
                素材 {{ selectedMaterialIds.length }}
              </el-button>
              <el-button size="small" :icon="Files" :disabled="isDirty" @click="archiveDocument">
                归档
              </el-button>
              <el-button size="small" :icon="CopyDocument" @click="copyDocument">
                复制
              </el-button>
              <el-button size="small" :icon="Download" @click="downloadMarkdown">
                Markdown
              </el-button>
              <el-button size="small" :icon="Download" @click="downloadHtml">
                HTML
              </el-button>
              <el-button size="small" :icon="Download" @click="downloadServerExport('docx')">
                DOCX
              </el-button>
              <el-button size="small" :icon="Download" @click="downloadServerExport('pdf')">
                PDF
              </el-button>
            </div>

            <Toolbar class="toolbar" :editor="editorRef" :defaultConfig="toolbarConfig" />
            <Editor
              class="editor"
              v-model="valueHtml"
              :defaultConfig="editorConfig"
              @onCreated="handleCreated"
              @onChange="handleEditorChange"
            />
            <div
              v-if="selectionMenuVisible"
              class="selection-context-menu"
              :style="{
                left: `${selectionMenuPosition.x}px`,
                top: `${selectionMenuPosition.y}px`,
              }"
              @mousedown.prevent
              @contextmenu.prevent
            >
              <button
                v-for="action in selectionRewriteActions"
                :key="action.key"
                type="button"
                class="selection-menu-item"
                @click="requestSelectionRewrite(action)"
              >
                <el-icon><component :is="action.icon" /></el-icon>
                <span>{{ action.label }}</span>
              </button>
            </div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="归档" name="artifacts">
        <div class="artifacts-container">
          <el-empty v-if="!artifacts.length" description="暂无归档版本" />
          <div v-for="artifact in artifacts" :key="artifactKey(artifact)" class="artifact-item">
            <div class="artifact-icon">
              <el-icon><Document /></el-icon>
            </div>
            <div class="artifact-info">
              <div class="artifact-name">{{ artifact.name || artifact.title || '报表文档' }}</div>
              <div class="artifact-meta">
                来源修订 {{ artifact.version || '未标记' }} · {{ artifact.createdAt || '未记录时间' }}
              </div>
            </div>
            <div class="artifact-actions">
              <el-button size="small" plain :icon="View" @click="compareArtifact(artifact)">
                对比
              </el-button>
              <el-button size="small" plain :icon="RefreshLeft" @click="restoreArtifact(artifact)">
                恢复
              </el-button>
              <el-button size="small" plain :icon="Download" @click="downloadArtifact(artifact)">
                下载
              </el-button>
              <el-button size="small" plain :icon="EditPen" @click="renameArtifact(artifact)" />
              <el-button size="small" plain type="danger" :icon="Delete" @click="deleteArtifact(artifact)" />
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="materialDialogVisible" title="选择报表素材" width="620px">
      <el-empty v-if="!materials.length" description="当前会话暂无可引用素材" />
      <el-checkbox-group v-else v-model="selectedMaterialIds" class="material-list">
        <el-checkbox
          v-for="material in materials"
          :key="materialKey(material)"
          :value="materialKey(material)"
          class="material-item"
        >
          <span>{{ material.name || material.title || material.id || '未命名素材' }}</span>
          <el-tag size="small" effect="plain">{{ material.type || 'material' }}</el-tag>
          <span v-if="material.sessionTitle" class="material-session">
            {{ material.sessionTitle }}
          </span>
          <el-tag
            v-if="sourceNeedsConfirmation(material)"
            size="small"
            type="warning"
          >
            待确认
          </el-tag>
        </el-checkbox>
      </el-checkbox-group>
    </el-dialog>

    <el-dialog v-model="compareDialogVisible" title="当前版本与归档对比" width="88%">
      <div class="version-compare">
        <section>
          <h4>当前 {{ currentDocument?.version || '' }}</h4>
          <pre>{{ canonicalDocumentContent }}</pre>
        </section>
        <section>
          <h4>归档 {{ comparedArtifact?.version || '' }}</h4>
          <pre>{{ comparedArtifact?.content || '' }}</pre>
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Collection,
  CopyDocument,
  Delete,
  Document,
  Download,
  EditPen,
  Files,
  Finished,
  MagicStick,
  Refresh,
  RefreshLeft,
  View,
} from '@element-plus/icons-vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { DihService } from '@/service/api'
import { copyTextToClipboard } from '@/utils/clipboard'
import { withBaseUrl } from '@/utils/url'
import type {
  ReportArtifact,
  ReportDocument,
  ReportRevision,
  ReportSourceRef,
  ReportWorkspace,
} from '@/types/type-dih'
import {
  buildSafeHtmlDownload,
  canApplyReportFragment,
  documentContentToEditorHtml,
  editorHtmlToCanonical,
  htmlToMarkdown,
  sanitizeHtml,
  sha256Text,
} from '@/utils/report-document-format'
import {
  DATA_REPORT_RECORD_EVENT,
  DATA_REPORT_RECORD_REQUEST_EVENT,
  REPORT_EXTRA_DATA_CHANGED_EVENT,
  REPORT_QUICK_ACTION_EVENT,
  REPORT_SELECTION_REWRITE_COMPLETED_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events'
import { shouldApplyIncomingReportDocument } from './report-document-sync'
import type {
  ReportRecordEventDetail,
  SelectionRewriteCompletedEventDetail,
} from '../events'

type OutlineItem = {
  id: string
  text: string
  level: number
}

type QuickAction = {
  key: string
  label: string
  description: string
  icon: unknown
  prompt: string
}

type SelectionRewriteAction = QuickAction

type PendingSelectionRewrite = {
  id: string
  actionKey: string
  selectedText: string
  selectedHtml: string
  baseRevision: number
  selectionHash: string
  documentHash: string
  editorRange?: unknown
  browserRange?: Range
}

const activeTab = ref('document')
const editorRef = shallowRef<any>(null)
const editorShellRef = ref<HTMLElement | null>(null)
const valueHtml = ref('')
const reportTitle = ref('未命名报表')
const currentDocument = ref<ReportDocument | null>(null)
const documents = ref<ReportDocument[]>([])
const artifacts = ref<ReportArtifact[]>([])
const revisions = ref<ReportRevision[]>([])
const materials = ref<ReportSourceRef[]>([])
const selectedMaterialIds = ref<string[]>([])
const outline = ref<OutlineItem[]>([])
const activeOutlineIndex = ref(0)
const extraDataText = ref('')
const sessionRecordId = ref('')
const isDirty = ref(false)
const isSaving = ref(false)
const saveConflict = ref(false)
const isApplyingExternalDocument = ref(false)
const persistedEditorHtml = ref('')
const persistedReportTitle = ref('未命名报表')
const workspaceHydrated = ref(false)
const selectionMenuVisible = ref(false)
const selectionMenuPosition = ref({ x: 0, y: 0 })
const pendingSelectionRewrite = ref<PendingSelectionRewrite | null>(null)
const materialDialogVisible = ref(false)
const compareDialogVisible = ref(false)
const comparedArtifact = ref<ReportArtifact | null>(null)
let autoSaveTimer: ReturnType<typeof setTimeout> | undefined

const quickActions: QuickAction[] = [
  {
    key: 'draft',
    label: '生成初稿',
    description: '根据当前会话和附件生成完整报表',
    icon: MagicStick,
    prompt: '请基于当前会话、附件和可用素材生成一份结构完整、可交付的专业分析报表。',
  },
  {
    key: 'continue',
    label: '继续写',
    description: '延续当前文档继续补充正文',
    icon: EditPen,
    prompt: '请延续当前报表继续补充内容，保持原有结构和正式文风。',
  },
  {
    key: 'formal',
    label: '正式语气',
    description: '改成正式报告文风',
    icon: Finished,
    prompt: '请把当前报表改写为正式、客观、适合归档交付的报告语气。',
  },
  {
    key: 'summary',
    label: '摘要',
    description: '生成或优化摘要部分',
    icon: Document,
    prompt: '请为当前报表生成一段高质量摘要，并把摘要整合进完整报表。',
  },
  {
    key: 'title',
    label: '标题',
    description: '优化标题和章节命名',
    icon: Document,
    prompt: '请优化当前报表标题和章节标题，使其更准确、清晰、专业。',
  },
  {
    key: 'conclusion',
    label: '结论',
    description: '补强结论和建议',
    icon: Finished,
    prompt: '请补强当前报表的结论与建议，确保建议明确、可执行、可跟踪。',
  },
]

const selectionRewriteActions: SelectionRewriteAction[] = [
  {
    key: 'polish',
    label: '润色',
    description: '只润色当前选中的文档内容',
    icon: MagicStick,
    prompt: '请润色选中的报表片段，提升表达、逻辑连贯性、专业性和可读性，不改变核心事实。',
  },
  {
    key: 'shorten',
    label: '缩写',
    description: '只缩写当前选中的文档内容',
    icon: Refresh,
    prompt: '请缩写选中的报表片段，压缩篇幅并保留关键背景、发现、结论和建议。',
  },
  {
    key: 'expand',
    label: '扩写',
    description: '只扩写当前选中的文档内容',
    icon: EditPen,
    prompt: '请扩写选中的报表片段，补充必要背景、分析依据、影响说明和可执行建议。',
  },
]

const toolbarConfig = {}
const editorConfig = {
  placeholder: 'AI 生成的报表会自动同步到这里，也可以直接编辑。',
}

const hasMeaningfulEditorContent = (html: string) => {
  const text = html
    .replace(/<br\s*\/?>/gi, '')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;|&#160;/gi, ' ')
    .trim()
  return Boolean(text) || /<(?:img|table|pre|hr)\b/i.test(html)
}

const hasDocument = computed(() => hasMeaningfulEditorContent(valueHtml.value))
const canonicalFormat = computed<'markdown' | 'html'>(() => {
  return currentDocument.value?.format === 'html' ? 'html' : 'markdown'
})
const canonicalDocumentContent = computed(() => {
  return editorHtmlToCanonical(valueHtml.value, canonicalFormat.value)
})
const selectedSourceRefs = computed(() => {
  const selected = new Set(selectedMaterialIds.value)
  return materials.value.filter(material => selected.has(materialKey(material)))
})

const asObject = (value: unknown): Record<string, unknown> => {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

const artifactKey = (artifact: ReportArtifact) => {
  return artifact.id || artifact.artifactId || artifact.documentId || artifact.name || artifact.title || 'artifact'
}

const materialKey = (material: ReportSourceRef) => {
  const id = material.id || material.auditId || material.partId || material.name || ''
  return `${String(material.type || 'material')}:${String(id)}`
}

const sourceNeedsConfirmation = (source: ReportSourceRef) => {
  const status = String(source.status || '').toLowerCase()
  const parseStatus = String(source.parseStatus || '').toLowerCase()
  return Boolean(source.truncated)
    || ['failed', 'conflict', 'missing', 'unavailable', 'blocked'].includes(status)
    || ['failed', 'partial', 'truncated'].includes(parseStatus)
}

const mergeMaterials = (...sources: ReportSourceRef[][]) => {
  const merged = new Map<string, ReportSourceRef>()
  sources.flat().forEach(material => {
    const key = materialKey(material)
    if (key && !key.endsWith('::')) merged.set(key, material)
  })
  return [...merged.values()]
}

const openSourceRef = (source: ReportSourceRef) => {
  const explicitUrl = String(source.url || source.fileUrl || source.file_url || '')
  if (explicitUrl) {
    window.open(explicitUrl, '_blank', 'noopener')
    return
  }
  if (source.type === 'attachment' && source.id) {
    window.open(
      withBaseUrl(`/api/v1/dih/upload/${encodeURIComponent(source.id)}/preview`),
      '_blank',
      'noopener',
    )
    return
  }
  ElMessage.info('该来源已记录审计标识，可在对应消息、图表库或分析任务中查看')
}

const isHtmlDocument = (document?: ReportDocument | ReportArtifact | null) => {
  const format = document?.format || ''
  const content = document?.content || ''
  return format === 'html' || /^\s*(<!doctype html|<html[\s>])/i.test(content)
}

const documentToHtml = (document?: ReportDocument | ReportArtifact | null) => {
  if (!document?.content) {
    return ''
  }
  const format = isHtmlDocument(document) ? 'html' : 'markdown'
  return documentContentToEditorHtml(document.content, format)
}

const htmlToPlainText = (html: string) => {
  const doc = new DOMParser().parseFromString(html || '', 'text/html')
  return doc.body.textContent || ''
}

const markdownOrHtmlToEditorHtml = (content: string) => {
  const normalized = content.trim()
  if (!normalized) {
    return ''
  }
  if (/^\s*(<!doctype html|<html[\s>]|<[a-z][\s\S]*>)/i.test(normalized)) {
    return sanitizeHtml(normalized)
  }
  return documentContentToEditorHtml(normalized, 'markdown')
}

const stripCodeFence = (content: string) => {
  const trimmed = content.trim()
  const match = trimmed.match(/^```(?:[\w:-]+)?\s*\n?([\s\S]*?)\n?```$/)
  return (match?.[1] || trimmed).trim()
}

const normalizeRewriteResult = (content = '') => {
  return stripCodeFence(content)
    .replace(/^以下是(?:润色|缩写|扩写|改写|处理)?后的(?:片段|内容|文本)[:：]\s*/i, '')
    .trim()
}

const generateOutline = () => {
  const doc = new DOMParser().parseFromString(valueHtml.value || '', 'text/html')
  outline.value = Array.from(doc.querySelectorAll('h1, h2, h3, h4, h5, h6')).map((heading, index) => ({
    id: `heading-${index + 1}`,
    text: heading.textContent?.trim() || `标题 ${index + 1}`,
    level: Number(heading.tagName.slice(1)),
  }))
}

const applyDocument = (document: ReportDocument | ReportArtifact) => {
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer)
    autoSaveTimer = undefined
  }
  isApplyingExternalDocument.value = true
  const nextTitle = document.title || document.name || '未命名报表'
  const nextHtml = documentToHtml(document)
  currentDocument.value = {
    ...document,
    title: nextTitle,
    name: nextTitle,
  }
  reportTitle.value = nextTitle
  valueHtml.value = nextHtml
  persistedReportTitle.value = nextTitle
  persistedEditorHtml.value = nextHtml
  generateOutline()
  isDirty.value = false
  saveConflict.value = false
  selectedMaterialIds.value = (document.sourceRefs || []).map(materialKey).filter(Boolean)
  activeTab.value = 'document'
  nextTick(() => {
    persistedReportTitle.value = reportTitle.value
    persistedEditorHtml.value = valueHtml.value
    isDirty.value = false
    isApplyingExternalDocument.value = false
  })
}

const applyWorkspace = (workspace: ReportWorkspace, applyIncomingDocument = true) => {
  artifacts.value = workspace.artifacts || []
  revisions.value = workspace.revisions || []
  if (workspace.extraData) {
    extraDataText.value = workspace.extraData
    emitDihEvent(REPORT_EXTRA_DATA_CHANGED_EVENT, { extraData: workspace.extraData })
  }
  if (applyIncomingDocument
      && workspace.currentDocument?.content
      && shouldApplyIncomingReportDocument(
        currentDocument.value,
        workspace.currentDocument,
        isDirty.value,
      )) {
    applyDocument(workspace.currentDocument)
  } else if (workspace.currentDocument) {
    currentDocument.value = {
      ...currentDocument.value,
      ...workspace.currentDocument,
    }
  }
}

const loadWorkspace = async (applyIncomingDocument = true) => {
  if (!sessionRecordId.value) return
  try {
    const [workspace, availableMaterials] = await Promise.all([
      DihService.getReportWorkspace(sessionRecordId.value),
      DihService.getReportMaterials(sessionRecordId.value).catch(() => []),
    ])
    materials.value = mergeMaterials(materials.value, availableMaterials)
    applyWorkspace(workspace, applyIncomingDocument)
  } catch (error) {
    console.warn('读取报表工作区失败，将使用会话摘要:', error)
  } finally {
    workspaceHydrated.value = true
  }
}

const handleReportRecordsUpdated = async (detail: ReportRecordEventDetail) => {
  detail ||= {}
  sessionRecordId.value = detail.sessionRecordId || sessionRecordId.value
  extraDataText.value = detail.extraData || extraDataText.value
  documents.value = detail.documents || []
  artifacts.value = detail.artifacts || []
  revisions.value = detail.revisions || []
  materials.value = mergeMaterials(materials.value, detail.materials || [])
  const incomingDocument = detail.currentDocument
  const shouldApplyIncoming = shouldApplyIncomingReportDocument(
    currentDocument.value,
    incomingDocument,
    isDirty.value,
  )
  if (incomingDocument?.content) {
    if (shouldApplyIncoming) {
      applyDocument(incomingDocument)
    }
  }
  await loadWorkspace(true)
}

const handleCreated = (editor: any) => {
  editorRef.value = editor
  generateOutline()
}

const handleEditorChange = () => {
  generateOutline()
  if (!workspaceHydrated.value && !hasMeaningfulEditorContent(valueHtml.value)) {
    return
  }
  if (isApplyingExternalDocument.value) {
    persistedEditorHtml.value = valueHtml.value
    persistedReportTitle.value = reportTitle.value
    isDirty.value = false
    return
  }
  markDirty()
}

const markDirty = () => {
  if (!isApplyingExternalDocument.value) {
    isDirty.value = valueHtml.value !== persistedEditorHtml.value
      || reportTitle.value !== persistedReportTitle.value
    saveConflict.value = false
    if (isDirty.value) scheduleAutoSave()
  }
}

const getEditableContainer = () => {
  const editor = editorRef.value
  return (editorShellRef.value?.querySelector('[contenteditable="true"]')
    || editor?.getEditableContainer?.()) as HTMLElement | undefined
}

const isSelectionInsideEditor = () => {
  const container = getEditableContainer()
  const selection = window.getSelection()
  if (!container || !selection || selection.rangeCount === 0) {
    return false
  }
  const { anchorNode, focusNode } = selection
  return !!anchorNode && !!focusNode && container.contains(anchorNode) && container.contains(focusNode)
}

const getSelectedHtml = () => {
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) {
    return ''
  }
  const wrapper = document.createElement('div')
  wrapper.appendChild(selection.getRangeAt(0).cloneContents())
  return wrapper.innerHTML
}

const cloneEditorRange = () => {
  const range = editorRef.value?.selection
  if (!range) {
    return undefined
  }
  try {
    return JSON.parse(JSON.stringify(range))
  } catch {
    return range
  }
}

const clampMenuPosition = (x: number, y: number) => {
  const menuWidth = 132
  const menuHeight = 124
  return {
    x: Math.min(x, window.innerWidth - menuWidth - 8),
    y: Math.min(y, window.innerHeight - menuHeight - 8),
  }
}

const hideSelectionMenu = () => {
  selectionMenuVisible.value = false
}

const handleEditorContextMenu = (event: MouseEvent) => {
  if (!isSelectionInsideEditor()) {
    hideSelectionMenu()
    return
  }
  const editor = editorRef.value
  const selectedText = (editor?.getSelectionText?.() || window.getSelection()?.toString() || '').trim()
  if (!selectedText) {
    hideSelectionMenu()
    return
  }

  event.preventDefault()
  event.stopPropagation()

  const browserSelection = window.getSelection()
  const browserRange = browserSelection?.rangeCount ? browserSelection.getRangeAt(0).cloneRange() : undefined
  pendingSelectionRewrite.value = {
    id: `selection-${Date.now()}`,
    actionKey: '',
    selectedText,
    selectedHtml: getSelectedHtml(),
    baseRevision: currentDocument.value?.revision || 0,
    selectionHash: '',
    documentHash: '',
    editorRange: cloneEditorRange(),
    browserRange,
  }
  selectionMenuPosition.value = clampMenuPosition(event.clientX, event.clientY)
  selectionMenuVisible.value = true
}

const requestSelectionRewrite = async (action: SelectionRewriteAction) => {
  const pending = pendingSelectionRewrite.value
  if (!pending?.selectedText) {
    ElMessage.warning('请先选中需要处理的文档内容')
    hideSelectionMenu()
    return
  }
  const selectedMarkdown = htmlToMarkdown(pending.selectedHtml) || pending.selectedText
  const selectionHash = await sha256Text(selectedMarkdown)
  const documentHash = await sha256Text(valueHtml.value)
  pendingSelectionRewrite.value = {
    ...pending,
    actionKey: action.key,
    baseRevision: currentDocument.value?.revision || 0,
    selectionHash,
    documentHash,
  }
  const requestContent = [
    action.prompt,
    '',
    '只处理下面选中的文档片段，不要改写、补全或重排未选中的任何内容。',
    '只输出处理后的片段正文，不要输出完整报表，不要解释，不要添加代码围栏。',
    '',
    '选中的文档片段如下：',
    `\`\`\`markdown\n${selectedMarkdown}\n\`\`\``,
  ].join('\n')
  hideSelectionMenu()
  emitDihEvent(REPORT_QUICK_ACTION_EVENT, {
    target: 'selection',
    actionKey: action.key,
    selectionId: pending.id,
    reportAction: {
      type: 'selection_rewrite',
      document_id: currentDocument.value?.documentId || currentDocument.value?.id,
      base_revision: currentDocument.value?.revision || 0,
      selection_id: pending.id,
      selection_hash: selectionHash,
      source_refs: selectedSourceRefs.value,
    },
    displayContent: `请${action.label}选中的报表片段。`,
    requestContent,
  })
}

const replaceSelectionWithContent = (content: string) => {
  const pending = pendingSelectionRewrite.value
  const editor = editorRef.value
  const normalizedContent = normalizeRewriteResult(content)
  if (!pending || !editor || !normalizedContent) {
    return false
  }

  const html = markdownOrHtmlToEditorHtml(normalizedContent)
  try {
    editor.focus?.()
    if (pending.editorRange && editor.select) {
      editor.select(pending.editorRange)
    } else if (editor.restoreSelection) {
      editor.restoreSelection()
    }
    if (editor.deleteFragment) {
      editor.deleteFragment()
    } else if (pending.browserRange) {
      pending.browserRange.deleteContents()
    }
    if (editor.dangerouslyInsertHtml) {
      editor.dangerouslyInsertHtml(html)
    } else if (editor.insertText) {
      editor.insertText(htmlToPlainText(html) || normalizedContent)
    }
    valueHtml.value = editor.getHtml?.() || valueHtml.value
    generateOutline()
    isDirty.value = true
    scheduleAutoSave()
    pendingSelectionRewrite.value = null
    ElMessage.success('已更新选中内容')
    return true
  } catch (error) {
    console.error('替换选中内容失败:', error)
    ElMessage.error('替换选中内容失败，请重试')
    return false
  }
}

const showRewriteConflictPreview = async (content: string) => {
  await ElMessageBox.alert(
    content,
    '选区已变化，改写结果未应用',
    {
      confirmButtonText: '已查看',
      customClass: 'report-rewrite-preview',
    },
  )
}

const handleSelectionRewriteCompleted = async (detail: SelectionRewriteCompletedEventDetail) => {
  detail ||= {}
  const pending = pendingSelectionRewrite.value
  if (!pending || detail.selectionId !== pending.id) {
    return
  }
  const content = detail.content || ''
  if (!content.trim()) {
    ElMessage.warning('未获取到选区改写结果')
    return
  }
  const currentDocumentHash = await sha256Text(valueHtml.value)
  if (!canApplyReportFragment({
    returnedBaseRevision: detail.baseRevision,
    expectedBaseRevision: pending.baseRevision,
    currentRevision: currentDocument.value?.revision || 0,
    returnedSelectionHash: detail.selectionHash,
    expectedSelectionHash: pending.selectionHash,
    currentDocumentHash,
    expectedDocumentHash: pending.documentHash,
  })) {
    pendingSelectionRewrite.value = null
    await showRewriteConflictPreview(content)
    return
  }
  replaceSelectionWithContent(content)
}

const scrollToHeading = (index: number) => {
  activeOutlineIndex.value = index
  const editor = editorRef.value
  const container = editor?.getEditableContainer?.()
  const heading = container?.querySelectorAll?.('h1, h2, h3, h4, h5, h6')?.[index]
  if (heading instanceof HTMLElement) {
    heading.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const buildDocumentFromEditor = (status = 'edited'): ReportDocument => {
  const id = currentDocument.value?.id || currentDocument.value?.documentId || `report-${Date.now()}`
  const title = reportTitle.value.trim() || '未命名报表'
  const revision = currentDocument.value?.revision || 0
  return {
    ...currentDocument.value,
    id,
    documentId: id,
    title,
    name: title,
    format: canonicalFormat.value,
    revision,
    version: currentDocument.value?.version || `v${revision}`,
    status,
    source: currentDocument.value?.source || 'editor',
    updatedAt: new Date().toISOString(),
    content: canonicalDocumentContent.value,
    outline: outline.value,
    sourceRefs: selectedSourceRefs.value,
  }
}

const conflictDocumentFromError = (error: unknown): ReportDocument | undefined => {
  const payload = asObject(error)
  const data = asObject(payload.data)
  const current = asObject(data.current_document || data.currentDocument)
  if (!Object.keys(current).length) return undefined
  return {
    ...current,
    id: String(current.id || current.documentId || current.document_id || ''),
    documentId: String(current.documentId || current.document_id || current.id || ''),
    revision: Number(current.revision || 0),
    content: String(current.content || ''),
    format: String(current.format || 'markdown'),
  }
}

const handleSaveConflict = async (error: unknown, silent: boolean) => {
  saveConflict.value = true
  const remote = conflictDocumentFromError(error)
  if (silent) {
    ElMessage.warning('自动保存遇到版本冲突，编辑内容仍保留在本地')
    return
  }
  try {
    await ElMessageBox.confirm(
      '服务器上的报表已有新版本。刷新会保留服务器版本并放弃当前未保存内容；取消可继续复制或手工合并。',
      '报表版本冲突',
      {
        confirmButtonText: '刷新服务器版本',
        cancelButtonText: '保留本地内容',
        type: 'warning',
      },
    )
    if (remote?.content) {
      applyDocument(remote)
    } else {
      const workspace = await DihService.getReportWorkspace(sessionRecordId.value)
      applyWorkspace(workspace, true)
    }
  } catch {
    // 用户选择保留本地内容，不做覆盖。
  }
}

const saveDocument = async (options: { silent?: boolean } = {}) => {
  if (!hasDocument.value) {
    if (!options.silent) ElMessage.warning('暂无可保存的报表内容')
    return
  }
  if (!sessionRecordId.value) {
    if (!options.silent) ElMessage.warning('会话尚未创建完成，无法保存报表')
    return
  }
  if (isSaving.value) return
  const editorSnapshot = valueHtml.value
  const titleSnapshot = reportTitle.value
  isSaving.value = true
  try {
    const document = buildDocumentFromEditor('edited')
    const workspace = await DihService.saveReportDocument(sessionRecordId.value, {
      document_id: document.documentId,
      base_revision: document.revision || 0,
      title: document.title || '未命名报表',
      format: canonicalFormat.value,
      content: document.content || '',
      outline: document.outline,
      source_refs: document.sourceRefs,
    })
    applyWorkspace(workspace, false)
    if (workspace.currentDocument) {
      currentDocument.value = workspace.currentDocument
      documents.value = [workspace.currentDocument]
    }
    persistedEditorHtml.value = editorSnapshot
    persistedReportTitle.value = titleSnapshot
    isDirty.value = valueHtml.value !== persistedEditorHtml.value
      || reportTitle.value !== persistedReportTitle.value
    saveConflict.value = false
    if (!options.silent) ElMessage.success('报表已保存')
    if (isDirty.value) scheduleAutoSave()
  } catch (error) {
    console.error('保存报表失败:', error)
    const payload = asObject(error)
    if (Number(payload.status) === 409) {
      await handleSaveConflict(error, Boolean(options.silent))
    } else if (!options.silent) {
      ElMessage.error(String(payload.msg || '保存报表失败'))
    }
  } finally {
    isSaving.value = false
  }
}

const scheduleAutoSave = () => {
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => {
    autoSaveTimer = undefined
    if (isDirty.value && !saveConflict.value) {
      void saveDocument({ silent: true })
    }
  }, 1200)
}

const archiveDocument = async () => {
  if (!hasDocument.value) {
    ElMessage.warning('暂无可归档的报表内容')
    return
  }
  if (!currentDocument.value?.documentId || !currentDocument.value.revision) {
    ElMessage.warning('请先保存报表，再创建归档')
    return
  }
  try {
    const workspace = await DihService.archiveReportDocument(sessionRecordId.value, {
      document_id: currentDocument.value.documentId,
      base_revision: currentDocument.value.revision,
      name: reportTitle.value.trim() || '未命名报表',
    })
    applyWorkspace(workspace, false)
    activeTab.value = 'artifacts'
    ElMessage.success('报表已归档')
  } catch (error) {
    console.error('归档报表失败:', error)
    const payload = asObject(error)
    if (Number(payload.status) === 409) {
      await handleSaveConflict(error, false)
    } else {
      ElMessage.error(String(payload.msg || '归档报表失败'))
    }
  }
}

const undoEdit = () => {
  editorRef.value?.undo?.()
}

const copyDocument = async () => {
  const copied = await copyTextToClipboard(canonicalDocumentContent.value)
  if (copied) {
    ElMessage.success('已复制报表内容')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}

const safeFileName = () => {
  return (reportTitle.value || 'report').replace(/[\\/:*?"<>|\s]+/g, '-').replace(/^-+|-+$/g, '') || 'report'
}

const downloadFile = (fileName: string, content: string, type: string) => {
  const blob = new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

const downloadMarkdown = () => {
  if (!hasDocument.value) {
    ElMessage.warning('暂无可下载的报表内容')
    return
  }
  const markdown = canonicalFormat.value === 'markdown'
    ? canonicalDocumentContent.value
    : htmlToMarkdown(valueHtml.value)
  downloadFile(`${safeFileName()}.md`, markdown, 'text/markdown;charset=utf-8')
}

const fullHtml = () => {
  return buildSafeHtmlDownload(reportTitle.value, valueHtml.value)
}

const downloadHtml = () => {
  if (!hasDocument.value) {
    ElMessage.warning('暂无可下载的报表内容')
    return
  }
  downloadFile(`${safeFileName()}.html`, fullHtml(), 'text/html;charset=utf-8')
}

const downloadServerExport = async (format: 'docx' | 'pdf') => {
  if (!sessionRecordId.value || !currentDocument.value?.revision) {
    ElMessage.warning('请先保存报表，再导出交付文件')
    return
  }
  if (isDirty.value) {
    await saveDocument()
    if (isDirty.value || saveConflict.value) return
  }
  window.open(
    withBaseUrl(
      `/api/v1/dih/chat-session/${encodeURIComponent(sessionRecordId.value)}/report/export/${format}`,
    ),
    '_blank',
    'noopener',
  )
}

const compareArtifact = (artifact: ReportArtifact) => {
  comparedArtifact.value = artifact
  compareDialogVisible.value = true
}

const restoreArtifact = async (artifact: ReportArtifact) => {
  if (!artifact.artifactId || !currentDocument.value?.revision) return
  try {
    await ElMessageBox.confirm(
      `恢复 ${artifact.version || '该归档'} 会创建一个新的工作修订，不会修改原归档。`,
      '恢复归档',
      { type: 'warning' },
    )
    const workspace = await DihService.restoreReportArtifact(
      sessionRecordId.value,
      artifact.artifactId,
      {
        document_id: currentDocument.value.documentId,
        base_revision: currentDocument.value.revision,
      },
    )
    applyWorkspace(workspace, true)
    ElMessage.success('已从归档创建新修订')
  } catch (error) {
    const payload = asObject(error)
    if (Number(payload.status) === 409) await handleSaveConflict(error, false)
  }
}

const renameArtifact = async (artifact: ReportArtifact) => {
  if (!artifact.artifactId || !currentDocument.value?.revision) return
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入新的归档名称',
      '重命名归档',
      { inputValue: artifact.name || artifact.title || '' },
    )
    const workspace = await DihService.renameReportArtifact(
      sessionRecordId.value,
      artifact.artifactId,
      {
        base_revision: currentDocument.value.revision,
        name: value,
      },
    )
    applyWorkspace(workspace, false)
  } catch {
    // 用户取消。
  }
}

const deleteArtifact = async (artifact: ReportArtifact) => {
  if (!artifact.artifactId || !currentDocument.value?.revision) return
  try {
    await ElMessageBox.confirm(
      `确认删除归档「${artifact.name || artifact.title || artifact.version}」？此操作不可恢复。`,
      '删除归档',
      { type: 'warning' },
    )
    const workspace = await DihService.deleteReportArtifact(
      sessionRecordId.value,
      artifact.artifactId,
      currentDocument.value.revision,
    )
    applyWorkspace(workspace, false)
    ElMessage.success('归档已删除')
  } catch {
    // 用户取消。
  }
}

const downloadArtifact = (artifact: ReportArtifact) => {
  const name = (artifact.name || artifact.title || 'report').replace(/[\\/:*?"<>|\s]+/g, '-')
  const content = artifact.content || ''
  if (artifact.format === 'html' || /^\s*(<!doctype html|<html[\s>])/i.test(content)) {
    downloadFile(
      `${name}.html`,
      buildSafeHtmlDownload(artifact.title || artifact.name || '报表', content),
      'text/html;charset=utf-8',
    )
    return
  }
  downloadFile(`${name}.md`, content, 'text/markdown;charset=utf-8')
}

const requestQuickAction = async (action: QuickAction) => {
  if (isDirty.value) {
    await saveDocument()
    if (isDirty.value || saveConflict.value) return
  }
  const currentContent = canonicalDocumentContent.value
  const hasContent = currentContent.trim().length > 0
  const format = canonicalFormat.value
  const requestContent = [
    action.prompt,
    '',
    hasContent
      ? '当前右侧报表内容如下，请基于它返回完整修改后的报表：'
      : '当前右侧还没有报表正文，请基于本会话上下文和附件生成完整报表：',
    hasContent ? `\n\`\`\`${format}\n${currentContent}\n\`\`\`` : '',
    '',
    '请返回完整报表正文，并在回答末尾输出一个 zenvis:report-document-config 围栏代码块，围栏内只放最终 Markdown 或 HTML 正文。',
  ].join('\n')
  emitDihEvent(REPORT_QUICK_ACTION_EVENT, {
    target: 'document',
    actionKey: action.key,
    displayContent: `请${action.label}右侧报表。`,
    requestContent,
    reportAction: {
      type: hasContent ? 'full_rewrite' : 'full_generate',
      document_id: currentDocument.value?.documentId || currentDocument.value?.id,
      base_revision: currentDocument.value?.revision || 0,
      source_refs: selectedSourceRefs.value,
    },
  })
}

useDihEventListener(DATA_REPORT_RECORD_EVENT, handleReportRecordsUpdated)
useDihEventListener(REPORT_SELECTION_REWRITE_COMPLETED_EVENT, handleSelectionRewriteCompleted)

onMounted(() => {
  emitDihEvent(DATA_REPORT_RECORD_REQUEST_EVENT)
  window.addEventListener('click', hideSelectionMenu)
})

onBeforeUnmount(() => {
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  window.removeEventListener('click', hideSelectionMenu)
  if (editorRef.value && !editorRef.value.isDestroyed) {
    editorRef.value.destroy()
  }
  editorRef.value = null
})
</script>

<style lang="scss" scoped>
.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  box-sizing: border-box;
  border-radius: 4px;
  background: #f5f7fa;
  color: #303133;
  overflow: hidden;
}

.right-panel {
  padding: 0;
}

.report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.report-title-wrap {
  flex: 1;
  min-width: 0;
}

.report-kicker {
  margin-bottom: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1;
}

.report-title-input {
  :deep(.el-input__wrapper) {
    padding-left: 0;
    box-shadow: none;
    background: transparent;
  }

  :deep(.el-input__inner) {
    color: #1f2329;
    font-size: 16px;
    font-weight: 600;
  }
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.quick-actions .el-button {
  margin-left: 0;
}

.right-tabs {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
}

:deep(.el-tabs__header) {
  margin: 0;
}

:deep(.el-tabs__nav) {
  padding: 0 18px;
}

:deep(.el-tabs__content) {
  height: calc(100% - 40px);
}

:deep(.el-tab-pane) {
  height: 100%;
}

.document-layout {
  display: flex;
  height: 100%;
  min-height: 0;
}

.catalogue {
  width: 180px;
  min-width: 160px;
  border-right: 1px solid #e4e7ed;
  background: #fbfcff;
  overflow: auto;
}

.catalogue-title {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 11px 14px;
  border-bottom: 1px solid #ebeef5;
  background: #fbfcff;
  font-size: 13px;
  font-weight: 600;
}

.outline-list {
  list-style: none;
  margin: 0;
  padding: 8px 0;
}

.outline-list li {
  padding-top: 7px;
  padding-right: 10px;
  padding-bottom: 7px;
  border-left: 3px solid transparent;
  color: #606266;
  cursor: pointer;
  font-size: 13px;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outline-list li:hover,
.outline-list li.active {
  border-left-color: #409eff;
  background: #ecf5ff;
  color: #1f2329;
}

.source-ref {
  display: flex;
  width: calc(100% - 16px);
  min-height: 32px;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  margin: 4px 8px;
  padding: 5px 7px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #606266;
  cursor: pointer;
  font-size: 12px;
  text-align: left;

  &:hover {
    background: #ecf5ff;
    color: #409eff;
  }
}

.editor-shell {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  background: #fff;
}

.editor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
}

.editor-actions .el-button {
  margin-left: 0;
}

.toolbar {
  border-bottom: 1px solid #ebeef5;
}

.editor {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.selection-context-menu {
  position: fixed;
  z-index: 3000;
  width: 124px;
  padding: 6px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(31, 35, 41, 0.14);
}

.selection-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 32px;
  padding: 0 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #303133;
  cursor: pointer;
  font-size: 13px;
  line-height: 32px;
  text-align: left;
}

.selection-menu-item:hover {
  background: #ecf5ff;
  color: #409eff;
}

:deep(.w-e-text-container) {
  height: 100% !important;
}

:deep(.w-e-scroll) {
  padding: 18px 22px;
}

.artifacts-container {
  height: 100%;
  padding: 12px;
  overflow: auto;
  background: #f5f7fa;
}

.artifact-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
}

.artifact-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 6px;
  background: #ecf5ff;
  color: #409eff;
  flex: 0 0 auto;
}

.artifact-info {
  flex: 1;
  min-width: 0;
}

.artifact-name {
  color: #303133;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.artifact-meta {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.artifact-actions {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}

.material-list {
  display: flex;
  max-height: 420px;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
}

.material-item {
  display: flex;
  width: 100%;
  min-height: 38px;
  align-items: center;
  margin-right: 0;
  padding: 6px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;

  :deep(.el-checkbox__label) {
    display: flex;
    flex: 1;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }
}

.material-session {
  min-width: 0;
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;

  section {
    min-width: 0;
  }

  pre {
    max-height: 62vh;
    margin: 0;
    padding: 12px;
    border: 1px solid #ebeef5;
    border-radius: 6px;
    background: #f7f8fa;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

@media (max-width: 900px) {
  .document-layout {
    flex-direction: column;
  }

  .catalogue {
    width: 100%;
    max-height: 160px;
    border-right: 0;
    border-bottom: 1px solid #e4e7ed;
  }

  .version-compare {
    grid-template-columns: 1fr;
  }
}
</style>
