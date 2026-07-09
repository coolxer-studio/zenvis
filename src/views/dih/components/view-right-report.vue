<template>
  <div class="panel right-panel report-workbench">
    <div class="report-header">
      <div class="report-title-wrap">
        <div class="report-kicker">报表文档</div>
        <el-input
          v-model="reportTitle"
          class="report-title-input"
          placeholder="未命名报表"
          @input="markDirty"
        />
      </div>
      <el-tag size="small" :type="isDirty ? 'warning' : 'success'" effect="plain">
        {{ isDirty ? '未保存' : '已保存' }}
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
          </aside>

          <section class="editor-shell">
            <div class="editor-actions">
              <el-button type="primary" size="small" :icon="Finished" @click="saveDocument">
                保存
              </el-button>
              <el-button size="small" :icon="Files" @click="archiveDocument">
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
            </div>

            <Toolbar class="toolbar" :editor="editorRef" :defaultConfig="toolbarConfig" />
            <Editor
              class="editor"
              v-model="valueHtml"
              :defaultConfig="editorConfig"
              @onCreated="handleCreated"
              @onChange="handleEditorChange"
              @contextmenu.capture="handleEditorContextMenu"
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
                {{ artifact.version || 'v1.0.0' }} · {{ artifact.createdAt || '未记录时间' }}
              </div>
            </div>
            <div class="artifact-actions">
              <el-button size="small" plain :icon="View" @click="viewArtifact(artifact)">
                查看
              </el-button>
              <el-button size="small" plain :icon="Download" @click="downloadArtifact(artifact)">
                下载
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import {
  CopyDocument,
  Document,
  Download,
  EditPen,
  Files,
  Finished,
  MagicStick,
  Refresh,
  View,
} from '@element-plus/icons-vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { DihService } from '@/service/api'
import { copyTextToClipboard } from '@/utils/clipboard'
import type { ReportArtifact, ReportDocument } from '@/types/type-dih'

type ReportRecordEventDetail = {
  currentDocument?: ReportDocument
  documents?: ReportDocument[]
  artifacts?: ReportArtifact[]
  extraData?: string
  sessionRecordId?: string
  sessionId?: string
}

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
  editorRange?: unknown
  browserRange?: Range
}

type SelectionRewriteCompletedEventDetail = {
  selectionId?: string
  content?: string
}

const DATA_REPORT_RECORD_EVENT = 'dihReportRecordsUpdated'
const DATA_REPORT_RECORD_REQUEST_EVENT = 'dihReportRecordsRequested'
const REPORT_QUICK_ACTION_EVENT = 'dihReportQuickActionRequested'
const REPORT_EXTRA_DATA_CHANGED_EVENT = 'dihReportExtraDataChanged'
const REPORT_SELECTION_REWRITE_COMPLETED_EVENT = 'dihReportSelectionRewriteCompleted'

const activeTab = ref('document')
const editorRef = shallowRef<any>(null)
const valueHtml = ref('')
const reportTitle = ref('未命名报表')
const currentDocument = ref<ReportDocument | null>(null)
const documents = ref<ReportDocument[]>([])
const artifacts = ref<ReportArtifact[]>([])
const outline = ref<OutlineItem[]>([])
const activeOutlineIndex = ref(0)
const extraDataText = ref('')
const sessionRecordId = ref('')
const isDirty = ref(false)
const isApplyingExternalDocument = ref(false)
const selectionMenuVisible = ref(false)
const selectionMenuPosition = ref({ x: 0, y: 0 })
const pendingSelectionRewrite = ref<PendingSelectionRewrite | null>(null)

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

const hasDocument = computed(() => valueHtml.value.trim().length > 0)

const asObject = (value: unknown): Record<string, unknown> => {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

const asArray = <T,>(value: unknown): T[] => {
  return Array.isArray(value) ? value.filter(item => item && typeof item === 'object') as T[] : []
}

const parseExtraData = () => {
  if (!extraDataText.value.trim()) {
    return {}
  }
  try {
    return asObject(JSON.parse(extraDataText.value))
  } catch {
    return {}
  }
}

const recordKey = (record: Record<string, unknown>) => {
  return String(record.id || record.documentId || record.artifactId || record.name || record.title || '')
}

const upsertRecord = <T extends Record<string, unknown>>(items: T[], record: T) => {
  const key = recordKey(record)
  if (!key) {
    return [...items, record]
  }
  return [...items.filter(item => recordKey(item) !== key), record]
}

const artifactKey = (artifact: ReportArtifact) => {
  return artifact.id || artifact.artifactId || artifact.documentId || artifact.name || artifact.title || 'artifact'
}

const markdownToHtml = (content = '') => {
  const html = marked.parse(content, { async: false }) as string
  return DOMPurify.sanitize(html)
}

const normalizeHtmlDocument = (content = '') => {
  return DOMPurify.sanitize(content)
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
  return isHtmlDocument(document)
    ? normalizeHtmlDocument(document.content)
    : markdownToHtml(document.content)
}

const htmlToMarkdown = (html: string) => {
  const doc = new DOMParser().parseFromString(html || '', 'text/html')
  const lines: string[] = []
  const append = (line = '') => {
    const normalized = line.replace(/\s+/g, ' ').trim()
    if (normalized) {
      lines.push(normalized)
    }
  }
  Array.from(doc.body.children).forEach(node => {
    const tag = node.tagName.toLowerCase()
    const text = node.textContent || ''
    if (/^h[1-6]$/.test(tag)) {
      append(`${'#'.repeat(Number(tag.slice(1)))} ${text}`)
    } else if (tag === 'ul' || tag === 'ol') {
      node.querySelectorAll('li').forEach(item => append(`- ${item.textContent || ''}`))
    } else if (tag === 'blockquote') {
      append(`> ${text}`)
    } else if (tag === 'pre') {
      lines.push('```')
      lines.push(text.trim())
      lines.push('```')
    } else {
      append(text)
    }
  })
  return lines.join('\n\n')
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
    return normalizeHtmlDocument(normalized)
  }
  return markdownToHtml(normalized)
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
  isApplyingExternalDocument.value = true
  const nextTitle = document.title || document.name || '未命名报表'
  currentDocument.value = {
    ...document,
    title: nextTitle,
    name: nextTitle,
  }
  reportTitle.value = nextTitle
  valueHtml.value = documentToHtml(document)
  generateOutline()
  isDirty.value = false
  activeTab.value = 'document'
  nextTick(() => {
    isApplyingExternalDocument.value = false
  })
}

const handleReportRecordsUpdated = (event: Event) => {
  const detail = (event as CustomEvent<ReportRecordEventDetail>).detail || {}
  sessionRecordId.value = detail.sessionRecordId || sessionRecordId.value
  extraDataText.value = detail.extraData || extraDataText.value
  documents.value = detail.documents || []
  artifacts.value = detail.artifacts || []
  const incomingDocument = detail.currentDocument
  if (incomingDocument?.content) {
    if (pendingSelectionRewrite.value) {
      const replaced = replaceSelectionWithContent(incomingDocument.content)
      if (replaced) {
        nextTick(() => {
          void saveDocument()
        })
      }
      return
    }
    const incomingId = incomingDocument.id || incomingDocument.documentId
    const currentId = currentDocument.value?.id || currentDocument.value?.documentId
    if (!isDirty.value || incomingId !== currentId) {
      applyDocument(incomingDocument)
    }
  }
}

const handleCreated = (editor: any) => {
  editorRef.value = editor
  generateOutline()
}

const handleEditorChange = () => {
  generateOutline()
  if (!isApplyingExternalDocument.value) {
    isDirty.value = true
  }
}

const markDirty = () => {
  if (!isApplyingExternalDocument.value) {
    isDirty.value = true
  }
}

const getEditableContainer = () => {
  const editor = editorRef.value
  return editor?.getEditableContainer?.() as HTMLElement | undefined
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
    editorRange: cloneEditorRange(),
    browserRange,
  }
  selectionMenuPosition.value = clampMenuPosition(event.clientX, event.clientY)
  selectionMenuVisible.value = true
}

const requestSelectionRewrite = (action: SelectionRewriteAction) => {
  const pending = pendingSelectionRewrite.value
  if (!pending?.selectedText) {
    ElMessage.warning('请先选中需要处理的文档内容')
    hideSelectionMenu()
    return
  }
  pendingSelectionRewrite.value = {
    ...pending,
    actionKey: action.key,
  }
  const selectedMarkdown = htmlToMarkdown(pending.selectedHtml) || pending.selectedText
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
  window.dispatchEvent(new CustomEvent(REPORT_QUICK_ACTION_EVENT, {
    detail: {
      target: 'selection',
      actionKey: action.key,
      selectionId: pending.id,
      displayContent: `请${action.label}选中的报表片段。`,
      requestContent,
    },
  }))
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
    pendingSelectionRewrite.value = null
    ElMessage.success('已更新选中内容')
    return true
  } catch (error) {
    console.error('替换选中内容失败:', error)
    ElMessage.error('替换选中内容失败，请重试')
    return false
  }
}

const handleSelectionRewriteCompleted = (event: Event) => {
  const detail = (event as CustomEvent<SelectionRewriteCompletedEventDetail>).detail || {}
  const pending = pendingSelectionRewrite.value
  if (!pending || detail.selectionId !== pending.id) {
    return
  }
  const content = detail.content || ''
  if (!content.trim()) {
    ElMessage.warning('未获取到选区改写结果')
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
  return {
    ...currentDocument.value,
    id,
    documentId: id,
    title,
    name: title,
    format: 'html',
    version: currentDocument.value?.version || 'v1.0.0',
    status,
    source: currentDocument.value?.source || 'editor',
    updatedAt: new Date().toISOString(),
    content: valueHtml.value,
    outline: outline.value,
  }
}

const buildExtraDataWithReport = (document: ReportDocument, nextArtifacts = artifacts.value) => {
  const extraData = parseExtraData()
  const report = asObject(extraData.report)
  const nextDocuments = upsertRecord(asArray<ReportDocument>(report.documents), document)
  extraData.report = {
    ...report,
    currentDocument: document,
    documents: nextDocuments,
    artifacts: nextArtifacts,
  }
  return JSON.stringify(extraData)
}

const persistExtraData = async (nextExtraData: string) => {
  if (!sessionRecordId.value) {
    ElMessage.warning('会话尚未创建完成，无法保存报表')
    return false
  }
  await DihService.updateChatSession(sessionRecordId.value, { extra_data: nextExtraData })
  extraDataText.value = nextExtraData
  window.dispatchEvent(new CustomEvent(REPORT_EXTRA_DATA_CHANGED_EVENT, {
    detail: { extraData: nextExtraData },
  }))
  return true
}

const saveDocument = async () => {
  if (!hasDocument.value) {
    ElMessage.warning('暂无可保存的报表内容')
    return
  }
  try {
    const document = buildDocumentFromEditor('edited')
    const nextExtraData = buildExtraDataWithReport(document)
    const saved = await persistExtraData(nextExtraData)
    if (!saved) return
    currentDocument.value = document
    documents.value = upsertRecord(documents.value, document)
    isDirty.value = false
    ElMessage.success('报表已保存')
  } catch (error) {
    console.error('保存报表失败:', error)
    ElMessage.error('保存报表失败')
  }
}

const archiveDocument = async () => {
  if (!hasDocument.value) {
    ElMessage.warning('暂无可归档的报表内容')
    return
  }
  try {
    const document = buildDocumentFromEditor('archived')
    const artifactId = `artifact-${Date.now()}`
    const artifact: ReportArtifact = {
      id: artifactId,
      artifactId,
      documentId: document.documentId,
      name: document.title,
      title: document.title,
      format: document.format,
      version: document.version,
      status: 'archived',
      createdAt: new Date().toLocaleString('zh-CN'),
      content: document.content,
    }
    const nextArtifacts = upsertRecord(artifacts.value, artifact)
    const nextExtraData = buildExtraDataWithReport(document, nextArtifacts)
    const saved = await persistExtraData(nextExtraData)
    if (!saved) return
    currentDocument.value = document
    artifacts.value = nextArtifacts
    documents.value = upsertRecord(documents.value, document)
    isDirty.value = false
    activeTab.value = 'artifacts'
    ElMessage.success('报表已归档')
  } catch (error) {
    console.error('归档报表失败:', error)
    ElMessage.error('归档报表失败')
  }
}

const copyDocument = async () => {
  const copied = await copyTextToClipboard(htmlToMarkdown(valueHtml.value))
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
  downloadFile(`${safeFileName()}.md`, htmlToMarkdown(valueHtml.value), 'text/markdown;charset=utf-8')
}

const fullHtml = () => {
  const html = valueHtml.value || ''
  if (/^\s*(<!doctype html|<html[\s>])/i.test(html)) {
    return html
  }
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${reportTitle.value}</title>
  <style>
    body { max-width: 880px; margin: 40px auto; padding: 0 24px; color: #1f2329; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 1.75; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #dcdfe6; padding: 8px 10px; }
    blockquote { margin: 16px 0; padding: 8px 14px; border-left: 4px solid #409eff; background: #f5f7fa; }
  </style>
</head>
<body>
${html}
</body>
</html>`
}

const downloadHtml = () => {
  if (!hasDocument.value) {
    ElMessage.warning('暂无可下载的报表内容')
    return
  }
  downloadFile(`${safeFileName()}.html`, fullHtml(), 'text/html;charset=utf-8')
}

const viewArtifact = (artifact: ReportArtifact) => {
  applyDocument({
    id: artifact.documentId || artifact.id,
    documentId: artifact.documentId || artifact.id,
    title: artifact.title || artifact.name,
    name: artifact.name || artifact.title,
    format: artifact.format,
    version: artifact.version,
    status: artifact.status,
    updatedAt: artifact.createdAt,
    content: artifact.content,
  })
}

const downloadArtifact = (artifact: ReportArtifact) => {
  const name = (artifact.name || artifact.title || 'report').replace(/[\\/:*?"<>|\s]+/g, '-')
  const content = artifact.content || ''
  if (artifact.format === 'html' || /^\s*(<!doctype html|<html[\s>])/i.test(content)) {
    downloadFile(`${name}.html`, content, 'text/html;charset=utf-8')
    return
  }
  downloadFile(`${name}.md`, content, 'text/markdown;charset=utf-8')
}

const requestQuickAction = (action: QuickAction) => {
  const currentContent = htmlToMarkdown(valueHtml.value)
  const hasContent = currentContent.trim().length > 0
  const requestContent = [
    action.prompt,
    '',
    hasContent
      ? '当前右侧报表内容如下，请基于它返回完整修改后的报表：'
      : '当前右侧还没有报表正文，请基于本会话上下文和附件生成完整报表：',
    hasContent ? `\n\`\`\`markdown\n${currentContent}\n\`\`\`` : '',
    '',
    '请返回完整报表正文，并在回答末尾输出一个 zenvis:report-document-config 围栏代码块，围栏内只放最终 Markdown 或 HTML 正文。',
  ].join('\n')
  window.dispatchEvent(new CustomEvent(REPORT_QUICK_ACTION_EVENT, {
    detail: {
      displayContent: `请${action.label}右侧报表。`,
      requestContent,
    },
  }))
}

onMounted(() => {
  window.addEventListener(DATA_REPORT_RECORD_EVENT, handleReportRecordsUpdated)
  window.dispatchEvent(new CustomEvent(DATA_REPORT_RECORD_REQUEST_EVENT))
  window.addEventListener(REPORT_SELECTION_REWRITE_COMPLETED_EVENT, handleSelectionRewriteCompleted)
  window.addEventListener('click', hideSelectionMenu)
})

onBeforeUnmount(() => {
  window.removeEventListener(DATA_REPORT_RECORD_EVENT, handleReportRecordsUpdated)
  window.removeEventListener(REPORT_SELECTION_REWRITE_COMPLETED_EVENT, handleSelectionRewriteCompleted)
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
}
</style>
