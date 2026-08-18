<template>
  <div class="panel center-panel" :class="`is-${variant}`">
    <header v-if="variant === 'workspace'" class="center-header">
      <div class="workspace-title-wrap">
        <div class="mobile-header-actions">
          <el-button
            v-if="showSessionToggle"
            text
            circle
            :icon="Menu"
            aria-label="打开会话列表"
            @click="emit('openSessions')"
          />
        </div>
        <div class="workspace-title-copy">
          <div class="workspace-title-line">
            <h1 class="center-title">智能分析工作台</h1>
            <el-tag class="current-skill-tag" size="small" effect="light" round>
              {{ activeSkillLabel }}
            </el-tag>
          </div>
          <p class="current-session-title" :title="chatSessionTitle">{{ chatSessionTitle }}</p>
        </div>
      </div>
      <div class="center-header-actions">
        <el-button v-if="showResultToggle" :icon="DataAnalysis" @click="emit('openResults')">
          分析结果
        </el-button>

        <el-skeleton-item
          v-if="taskQueueLoading && !taskQueueAvailable"
          class="task-status-skeleton"
          variant="text"
        />
        <el-tooltip v-else :content="taskQueueTooltip" placement="bottom" :show-after="300">
          <button
            type="button"
            class="task-status-summary"
            :class="{
              'is-idle': taskQueueIdle,
              'is-unavailable': !taskQueueAvailable,
            }"
            :aria-label="taskQueueAriaLabel"
            @click="emit('openAnalysisTasks')"
          >
            <template v-if="!taskQueueAvailable">
              <el-icon class="task-status-unavailable-icon"><WarningFilled /></el-icon>
              <span>状态暂不可用</span>
            </template>
            <template v-else-if="taskQueueIdle">
              <el-icon class="task-status-idle-icon"><CircleCheckFilled /></el-icon>
              <span>队列空闲</span>
            </template>
            <template v-else>
              <span
                v-for="item in taskQueueItems"
                :key="item.key"
                class="task-status-item"
                :class="`is-${item.tone}`"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <span class="task-status-label">{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </span>
            </template>
          </button>
        </el-tooltip>

        <el-badge
          class="analysis-task-badge"
          :value="taskQueueTotal"
          :hidden="!taskQueueAvailable || taskQueueTotal === 0"
        >
          <el-button class="analysis-task-button" :icon="Clock" @click="emit('openAnalysisTasks')">
            <span class="analysis-task-label">分析任务</span>
          </el-button>
        </el-badge>
      </div>
    </header>
    <div v-else class="compact-header">
      <strong>{{ chatSessionTitle }}</strong>
    </div>

    <!-- 聊天内容区域 -->
    <div class="chat-content" ref="chatContentRef">
      <!-- Enter提示信息 -->
      <!-- 已移除原来的 enter-tip 元素，改为使用 ElMessage 实现 toast 提示 -->
      <div class="message-list">
        <div
          v-for="(message, index) in messages"
          :key="messageRenderKey(message)"
          class="message-item"
          :class="message.sender === 'ai' ? 'is-ai-message-item' : 'is-user-message-item'"
        >
          <div
            v-if="message.sender === 'ai'"
            class="ai-message-container"
            @mouseenter="handleMouseEnter('ai', index)"
            @mouseleave="handleMouseLeave('ai')"
          >
            <div class="avatar ai-avatar">
              <img v-if="systemLogo" :src="systemLogo" alt="" />
              <el-icon v-else :size="20"><Monitor /></el-icon>
            </div>
            <div class="message-bubble ai-message">
              <!-- 加载动画 -->
              <div v-if="message.loading && !message.parts?.length" class="typing-indicator">
                <span class="typing-dot"></span>
                <span class="typing-dot"></span>
                <span class="typing-dot"></span>
              </div>
              <message-card-renderer
                v-else
                :message="message"
                @copy-code="copyMessage"
                @decide-action="handleActionDecision(message, $event)"
                @submit-info-steps="handleInfoStepsSubmit(message, $event)"
                @add-chart-library="handleAddChartLibrary(message, $event)"
                @chart-render-failed="handleChartRenderFailure($event)"
                @choose-data-access-decision="handleDataAccessDecision(message, $event)"
                @decide-mcp-approval="handleMcpApprovalDecision(message, $event)"
                @select-prompt-suggestion="fillPromptSuggestion"
              />
              <div class="message-time">{{ message.time }}</div>
              <!-- 新增：AI消息的交互按钮 -->
              <div class="message-actions" v-show="isHoveredAiMessage === index">
                <el-button @click="copyMessage(message.content)" size="small" :icon="CopyDocument"
                  >复制</el-button
                >
                <el-button @click="shareMessage(message.content)" size="small" :icon="Share"
                  >分享</el-button
                >
                <el-button @click="likeMessage(index)" size="small" :icon="Sunny">准确</el-button>
                <el-button @click="dislikeMessage(index)" size="small" :icon="Lightning"
                  >不准确</el-button
                >
              </div>
            </div>
          </div>
          <div
            v-else
            class="user-message-container"
            @mouseenter="handleMouseEnter('user', index)"
            @mouseleave="handleMouseLeave('user')"
          >
            <div class="message-bubble user-message">
              <div class="message-content user-content">{{ message.content }}</div>
              <div v-if="message.attachments?.length" class="message-attachments">
                <div
                  v-for="attachment in message.attachments"
                  :key="attachmentFileId(attachment)"
                  class="message-attachment"
                  :class="{ 'image-attachment': isImageAttachment(attachment) }"
                >
                  <img
                    v-if="isImageAttachment(attachment)"
                    class="attachment-image-preview"
                    :src="attachmentPreviewUrl(attachment)"
                    :alt="attachmentFileName(attachment)"
                    @click="openAttachmentPreview(attachment)"
                  />
                  <template v-else>
                    <el-icon><Paperclip /></el-icon>
                    <span class="attachment-name">{{ attachmentFileName(attachment) }}</span>
                    <span class="attachment-size">{{
                      formatFileSize(attachment.file_size ?? attachment.fileSize)
                    }}</span>
                  </template>
                </div>
              </div>
              <div class="message-time">{{ message.time }}</div>
              <!-- 新增：用户消息的交互按钮 -->
              <div class="message-actions" v-show="isHoveredUserMessage === index">
                <el-button @click="copyMessage(message.content)" size="small" :icon="CopyDocument"
                  >复制</el-button
                >
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
      <el-alert
        v-if="skillEntryUnavailable"
        class="skill-unavailable-alert"
        title="当前 Skill 已停用或不存在，请选择其他可用技能后继续。"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="input-container">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder="输入你的问题，帮你深度解答"
          @keydown.enter.exact.prevent="handleEnterPress"
          @keydown.enter.shift.exact.prevent="insertLineBreak"
        ></el-input>

        <div v-if="pendingAttachments.length || isUploadingAttachment" class="pending-attachments">
          <div
            v-for="(attachment, index) in pendingAttachments"
            :key="attachmentFileId(attachment)"
            class="pending-attachment"
            :class="{ 'pending-image-attachment': isImageAttachment(attachment) }"
          >
            <img
              v-if="isImageAttachment(attachment)"
              class="pending-image-preview"
              :src="attachmentPreviewUrl(attachment)"
              :alt="attachmentFileName(attachment)"
              @click="openAttachmentPreview(attachment)"
            />
            <template v-else>
              <el-icon><Paperclip /></el-icon>
              <span class="attachment-name">{{ attachmentFileName(attachment) }}</span>
              <span class="attachment-size">{{
                formatFileSize(attachment.file_size ?? attachment.fileSize)
              }}</span>
            </template>
            <el-tooltip content="移除附件" placement="top">
              <el-button
                class="attachment-remove-btn"
                :icon="Close"
                circle
                @click="removePendingAttachment(index)"
              />
            </el-tooltip>
          </div>
          <div v-if="isUploadingAttachment" class="pending-attachment uploading">
            <el-icon><Loading /></el-icon>
            <span>正在上传...</span>
          </div>
        </div>

        <div class="input-actions">
          <div class="input-action-group input-action-primary">
            <el-select
              v-model="modelSelectData.period"
              class="model-select"
              :style="modelSelectStyle"
              :popper-class="[
                'dih-model-select-popper',
                { 'dih-model-select-popper--compact': props.variant === 'compact' },
              ]"
              :teleported="true"
              size="small"
              placeholder="选择模型"
            >
              <el-option
                v-for="item in modelSelectData.periodOptions"
                :key="item"
                :label="item"
                :value="item"
              />
            </el-select>

            <!-- 新增深度思考按钮 -->
            <el-tooltip v-if="chatSessionType === 'ask'" content="切换深度思考" placement="top">
              <el-button
                class="action-btn deep-thinking-btn"
                :class="{ 'deep-thinking-active': isDeepThinking }"
                @click="toggleDeepThinking"
              >
                <el-icon><Opportunity /></el-icon>
                <span>深度思考</span>
              </el-button>
            </el-tooltip>
          </div>

          <div class="input-action-group">
            <el-tooltip content="上传文件" placement="top">
              <el-button
                class="action-btn attachment-btn"
                circle
                aria-label="上传附件"
                :disabled="isUploadingAttachment"
                @click="uploadFile"
              >
                <el-icon>
                  <Paperclip />
                </el-icon>
              </el-button>
            </el-tooltip>

            <el-tooltip v-if="isStreamingResponse" content="停止生成" placement="top">
              <el-button
                class="action-btn stop-btn"
                circle
                aria-label="停止生成"
                @click="stopCurrentChat"
              >
                <span class="stop-icon-square" aria-hidden="true"></span>
              </el-button>
            </el-tooltip>

            <el-tooltip v-else content="发送" placement="top">
              <el-button
                class="action-btn send-btn"
                circle
                aria-label="发送消息"
                :disabled="!canSendMessage"
                @click="sendMessage"
              >
                <el-icon>
                  <Position />
                </el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </div>
      </div>

      <!-- 快捷操作按钮 -->
      <div class="suggestions" v-if="showSuggestionBtn">
        <el-button
          v-for="(suggestion, index) in suggestions"
          :key="index"
          class="suggestion-btn"
          size="small"
          :class="{ active: router.currentRoute.value.query.type === suggestion.type }"
          @click="selectSuggestion(index)"
        >
          <el-icon class="suggestion-icon">
            <component :is="suggestion.icon" />
          </el-icon>
          {{ suggestion.label }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, nextTick } from 'vue';
import {
  CircleCheckFilled,
  Clock,
  Close,
  CopyDocument,
  DataAnalysis,
  Lightning,
  Loading,
  Lock,
  Menu,
  Monitor,
  Opportunity,
  Paperclip,
  Position,
  Share,
  Sunny,
  VideoPlay,
  WarningFilled,
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { DihService } from '@/service/api';
import { useRouter } from 'vue-router';
import { generateUUID } from '@/utils/util-common';
import { copyTextToClipboard } from '@/utils/clipboard';
import MessageCardRenderer from '@/components/dih-message/message-card-renderer.vue';
import { useChatAttachments } from '../composables/use-chat-attachments';
import { useChatMessageActions } from '../composables/use-chat-message-actions';
import { useChatSession } from '../composables/use-chat-session';
import { useChatStream } from '../composables/use-chat-stream';
import { useMcpApproval } from '../composables/use-mcp-approval';
import { usePanelRecordSync } from '../composables/use-panel-record-sync';
import { DATA_VISUALIZATION_CHART_DATA_EVENT, emitDihEvent } from '../events';
import type { ChatMessage, ChatMessagePart } from '@/types/type-dih';
import type { TAnalysisTaskQueue } from '@/types/type-analysis-task';

const router = useRouter();

const chatContentRef = ref<HTMLElement | null>(null);
const messageRenderKeys = new WeakMap<ChatMessage, string>();
let nextMessageRenderKey = 0;

const messageRenderKey = (message: ChatMessage): string => {
  const existingKey = messageRenderKeys.get(message);
  if (existingKey) return existingKey;
  let key = message.id;
  if (!key) {
    nextMessageRenderKey += 1;
    key = `client-message-${nextMessageRenderKey}`;
  }
  messageRenderKeys.set(message, key);
  return key;
};

const scrollToBottom = async (): Promise<void> => {
  await nextTick();
  if (chatContentRef.value) {
    chatContentRef.value.scrollTop = chatContentRef.value.scrollHeight;
  }
};

// 定义建议接口
interface Suggestion {
  type: string;
  label: string;
  icon: any;
}

interface Props {
  suggestions: Suggestion[];
  chatSessionId?: string;
  chatSessionType?: string;
  skillEntryUnavailable?: boolean;
  variant?: 'workspace' | 'compact';
  systemLogo?: string;
  taskQueueStatus?: TAnalysisTaskQueue;
  taskQueueLoading?: boolean;
  taskQueueAvailable?: boolean;
  showSessionToggle?: boolean;
  showResultToggle?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  skillEntryUnavailable: false,
  variant: 'workspace',
  systemLogo: '',
  taskQueueLoading: false,
  taskQueueAvailable: false,
  showSessionToggle: false,
  showResultToggle: false,
});
const emit = defineEmits<{
  (event: 'openAnalysisTasks'): void;
  (event: 'openSessions'): void;
  (event: 'openResults'): void;
}>();

const activeSkillLabel = computed(() => {
  const type = String(router.currentRoute.value.query.type || 'ask');
  if (type === 'ask') return '智能问答';
  return props.suggestions.find(item => item.type === type)?.label || '专业分析';
});

const taskQueueItems = computed(() => [
  {
    key: 'running',
    label: '执行中',
    value: props.taskQueueStatus?.runningCount || 0,
    tone: 'warning',
    icon: VideoPlay,
  },
  {
    key: 'approval',
    label: '待审批',
    value: props.taskQueueStatus?.waitingApprovalCount || 0,
    tone: 'danger',
    icon: Lock,
  },
  {
    key: 'pending',
    label: '排队中',
    value: props.taskQueueStatus?.pendingCount || 0,
    tone: 'primary',
    icon: Clock,
  },
]);

const taskQueueTotal = computed(() =>
  taskQueueItems.value.reduce((total, item) => total + item.value, 0),
);
const taskQueueIdle = computed(
  () => props.taskQueueAvailable && !props.taskQueueLoading && taskQueueTotal.value === 0,
);
const taskQueueAriaLabel = computed(() => {
  if (!props.taskQueueAvailable) return '分析任务状态暂不可用，打开分析任务面板';
  if (taskQueueIdle.value) return '分析任务队列空闲，打开分析任务面板';
  return `${taskQueueItems.value
    .map(item => `${item.label}${item.value}项`)
    .join('，')}，打开分析任务面板`;
});
const taskQueueTooltip = computed(() => {
  if (!props.taskQueueAvailable) return '暂时无法获取分析任务状态，点击打开任务面板';
  const runningTaskName = props.taskQueueStatus?.runningTask?.name;
  if (runningTaskName) return `当前任务：${runningTaskName}`;
  if (taskQueueIdle.value) return '当前没有等待或执行中的分析任务';
  return taskQueueItems.value.map(item => `${item.label} ${item.value}`).join(' · ');
});

const {
  pendingAttachments,
  isUploadingAttachment,
  attachmentFileId,
  attachmentFileName,
  formatFileSize,
  isImageAttachment,
  attachmentPreviewUrl,
  openAttachmentPreview,
  removePendingAttachment,
  uploadFile,
} = useChatAttachments();

const {
  messages,
  showSuggestionBtn,
  chatSessionTitle,
  chatSessionId,
  chatSessionRecordId,
  chatSessionType,
  chatSessionExtraData,
  ensureChatSessionRecordId,
  refreshChatSessionExtraData,
} = useChatSession({
  props,
  router,
  processMessageFormat: message => processMessageFormat(message),
  scrollToBottom,
});

// 添加深度思考状态
const isDeepThinking = ref(false);

watch(
  chatSessionType,
  sessionType => {
    if (sessionType !== 'ask') {
      isDeepThinking.value = false;
    }
  },
  { immediate: true },
);

// 定义选择框结构
interface SelectData {
  period: string;
  periodOptions: string[];
}

// 输入消息
const inputMessage = ref('');
const modelSelectData = ref<SelectData>({
  periodOptions: ['qianwen-max', 'deepseek-R1', 'deepseek-V3'],
  period: 'qianwen-max',
});

const modelSelectStyle = computed(() => {
  const label = modelSelectData.value.period || '选择模型';
  const textWidth = Array.from(label).reduce((width, character) => {
    if (/^[\u3400-\u9fff]$/.test(character)) return width + 13;
    if (/^[A-Z0-9]$/.test(character)) return width + 8;
    if (/^[mwMW]$/.test(character)) return width + 9;
    if (/^[ilI1|]$/.test(character)) return width + 4;
    return width + 7;
  }, 0);
  const maxWidth = props.variant === 'compact' ? 148 : 200;
  const width = Math.min(maxWidth, Math.max(88, Math.ceil(textWidth + 40)));

  return {
    width: `${width}px`,
    flex: `0 0 ${width}px`,
  };
});

// 添加一个变量来跟踪Enter按键次数
const enterPressCount = ref(0);
const enterPressTimer = ref<number | null>(null);
// 移除了 showEnterTip 变量，因为我们不再使用页面内元素显示提示

const queryTextValue = (value: unknown) => {
  const raw = Array.isArray(value) ? value[0] : value;
  if (typeof raw !== 'string') {
    return '';
  }
  try {
    return decodeURIComponent(raw);
  } catch {
    return raw;
  }
};

const queryPromptValue = (query: Record<string, unknown>) => {
  const msgRef = queryTextValue(query.msgRef);
  if (msgRef) {
    const storageKey = `dih:prefill:${msgRef}`;
    try {
      const stored = window.sessionStorage?.getItem(storageKey) || '';
      if (stored) {
        window.sessionStorage?.removeItem(storageKey);
        return stored;
      }
    } catch {
      // ignore storage failures and fall back to msg query
    }
  }
  return queryTextValue(query.msg);
};

/**
 * 健壮的 JSON 解析工具方法
 * 支持处理包含转义字符、双重序列化等情况的 JSON 数据
 * @param content 待解析的字符串
 * @returns 解析后的对象，失败返回 null
 */
const parseJsonContent = (content: string) => {
  if (!content) return null;

  let cleanContent = content.trim();
  // 0. 处理 BOM 头 (ufeff)
  if (cleanContent.charCodeAt(0) === 0xfeff) {
    cleanContent = cleanContent.slice(1);
  }

  const tryParse = (str: string) => {
    try {
      return JSON.parse(str);
    } catch (e) {
      return null;
    }
  };

  // 方法1: 直接尝试解析
  let result = tryParse(cleanContent);
  if (result) {
    return result;
  }

  // 方法2: 尝试提取 Markdown 代码块 (```json ... ```)
  const markdownMatch = cleanContent.match(/```(?:json)?\s*([\s\S]*?)\s*```/);
  if (markdownMatch && markdownMatch[1]) {
    result = tryParse(markdownMatch[1]);
    if (result) {
      return result;
    }
  }

  // 方法3: 尝试寻找最外层的 {} 或 [] (提取JSON子串)
  const firstBrace = cleanContent.indexOf('{');
  const lastBrace = cleanContent.lastIndexOf('}');
  const firstBracket = cleanContent.indexOf('[');
  const lastBracket = cleanContent.lastIndexOf(']');
  let start = -1;
  let end = -1;
  // 优先匹配最外层的对象或数组
  if (firstBrace !== -1 && (firstBracket === -1 || firstBrace < firstBracket)) {
    if (lastBrace > firstBrace) {
      start = firstBrace;
      end = lastBrace;
    }
  } else if (firstBracket !== -1) {
    if (lastBracket > firstBracket) {
      start = firstBracket;
      end = lastBracket;
    }
  }
  if (start !== -1 && end !== -1) {
    result = tryParse(cleanContent.substring(start, end + 1));
    if (result) {
      return result;
    }
  }

  // 方法4: 尝试反转义解析 (处理双重序列化)
  try {
    const unescaped = JSON.parse(cleanContent);
    if (typeof unescaped === 'string') {
      // 递归调用以处理 unescaped 字符串中的 markdown 或其他情况
      result = parseJsonContent(unescaped);
      if (result) {
        return result;
      }
    } else if (typeof unescaped === 'object' && unescaped !== null) {
      return unescaped;
    }
  } catch (e) {
    /* ignore */
  }

  // 方法5: 尝试补充引号解析 (针对 {\"a\": 1} 这种缺少外层引号的单行内容)
  // 这比直接替换更安全，因为它遵循JSON转义规则
  // 这一步才成功
  try {
    const wrapped = '"' + cleanContent + '"';
    const unescaped = JSON.parse(wrapped);
    if (typeof unescaped === 'string') {
      result = tryParse(unescaped);
      if (result) {
        return result;
      }
    }
  } catch (e) {
    /* ignore */
  }

  // 方法6: 暴力替换转义引号 (兼容旧逻辑 - 最后的兜底)
  try {
    const cleaned = cleanContent.replace(/\\"/g, '"');
    result = JSON.parse(cleaned);
    return result;
  } catch (e) {
    /* ignore */
  }

  return null;
};

// 判断消息是否为chart格式并处理
const createMarkdownParts = (content: string): ChatMessagePart[] => [
  {
    id: generateUUID(),
    type: 'markdown',
    content,
  },
];

const {
  isMcpApprovalPart,
  upsertMcpApprovalPart,
  mergeFinalApprovalParts,
  handleMcpApprovalDecision,
  rejectPendingMcpApprovals,
} = useMcpApproval({
  messages,
  createMarkdownParts,
});

const getChartContent = (message: ChatMessage) => {
  const chartPart = message.parts?.find(part => part.type === 'chart');
  return chartPart?.content || message.content;
};

const markChartAsLoaded = (message: ChatMessage) => {
  message.content = '图表数据已加载，请在右侧面板查看可视化结果。';
  message.parts = createMarkdownParts(message.content);
};

const dispatchChartData = (jsonData: any) => {
  emitDihEvent(DATA_VISUALIZATION_CHART_DATA_EVENT, {
    chartType: jsonData.chart_type || 'line',
    option: jsonData.option || jsonData,
    rawData: jsonData.raw_data,
    columns: jsonData.columns,
  });
};

const processMessageFormat = (message: ChatMessage) => {
  if (message.sender === 'ai' && message.content) {
    // 如果消息类型已经是 text，直接返回，不进行 JSON 解析
    if (message.type === 'text') {
      return;
    }

    // 如果消息类型已经是 chart，发送图表数据到右侧组件
    if (message.type === 'chart') {
      const jsonData = parseJsonContent(getChartContent(message));
      if (jsonData && jsonData.option) {
        dispatchChartData(jsonData);
        markChartAsLoaded(message);
      } else {
        console.warn('processMessageFormat - jsonData 存在但没有 option 字段:', jsonData);
      }
      return;
    }

    const jsonData = parseJsonContent(message.content);
    // 判断是否为图表数据格式
    if (jsonData && (jsonData.option || jsonData.chart_type)) {
      message.type = 'chart';
      dispatchChartData(jsonData);
      markChartAsLoaded(message);
    } else {
      message.type = 'text';
    }
  }
};

const { isStreamingResponse, canSendMessage, sendMessage, stopCurrentChat } = useChatStream({
  router,
  messages,
  inputMessage,
  pendingAttachments,
  isUploadingAttachment,
  isDeepThinking,
  selectedModel: computed(() => modelSelectData.value.period),
  chatSessionId,
  chatSessionRecordId,
  chatSessionType,
  chatSessionExtraData,
  scrollToBottom,
  processMessageFormat,
  refreshChatSessionExtraData,
  isMcpApprovalPart,
  upsertMcpApprovalPart,
  mergeFinalApprovalParts,
  rejectPendingMcpApprovals,
  isChatUnavailable: computed(() => props.skillEntryUnavailable),
});

onMounted(() => {
  void fetchModelList();
});

usePanelRecordSync({
  messages,
  chatSessionExtraData,
  chatSessionRecordId,
  chatSessionId,
  isStreamingResponse,
  sendMessage,
});

const {
  handleInfoStepsSubmit,
  handleActionDecision,
  handleAddChartLibrary,
  handleDataAccessDecision,
  handleChartRenderFailure,
} = useChatMessageActions({
  chatSessionId,
  chatSessionExtraData,
  sendMessage,
});

const fetchModelList = async () => {
  try {
    const modelList = await DihService.getModelList();
    if (modelList && modelList.length > 0) {
      modelSelectData.value.periodOptions = modelList.map(modelInfo => modelInfo.model);
      modelSelectData.value.period = modelList[0].model;
    }
  } catch (error) {}
};

// 监听query参数的变化
watch(
  () => router.currentRoute.value.query,
  (newQuery, oldQuery) => {
    inputMessage.value = queryPromptValue(newQuery as Record<string, unknown>);
  },
  // 首次进入组件时也执行一次
  { immediate: true },
);

// 切换深度思考模式
const toggleDeepThinking = () => {
  isDeepThinking.value = !isDeepThinking.value;
};

// 处理Enter按键
const handleEnterPress = () => {
  enterPressCount.value++;

  // 如果这是第一次按下Enter，启动计时器
  if (enterPressCount.value === 1) {
    enterPressTimer.value = window.setTimeout(() => {
      // 如果在300ms内没有再次按下Enter，则重置计数器
      enterPressCount.value = 0;
      insertLineBreak();
      // 显示 toast 提示信息
      ElMessage({
        message: '快速按下两次Enter发送消息',
        type: 'info',
        duration: 2000,
      });
    }, 300);
  }
  // 如果这是第二次按下Enter，在300ms内
  else if (enterPressCount.value === 2) {
    // 清除计时器
    if (enterPressTimer.value) {
      clearTimeout(enterPressTimer.value);
      enterPressTimer.value = null;
    }

    // 重置计数器
    enterPressCount.value = 0;

    // 发送消息
    sendMessage();
  }
};

// 插入换行符
const insertLineBreak = () => {
  const textarea = document.querySelector('.input-container textarea') as HTMLTextAreaElement;
  if (textarea) {
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const text = inputMessage.value;
    inputMessage.value = text.substring(0, start) + '\n' + text.substring(end);

    // 在下一次DOM更新后设置光标位置
    nextTick(() => {
      textarea.selectionStart = start + 1;
      textarea.selectionEnd = start + 1;
    });
  }
};

// 选择建议
const selectSuggestion = (index: number) => {
  const suggestion = props.suggestions[index];

  // 如果点击的是xx开启新的chatSession
  if (suggestion.type) {
    chatSessionId.value = generateUUID();
    router.push({
      name: 'service-dih',
      query: {
        type: suggestion.type,
        chatSessionId: chatSessionId.value,
        createSession: 1,
      },
    });
  }
};

const isHoveredAiMessage = ref(-1);
const isHoveredUserMessage = ref(-1);

// 处理鼠标悬停事件
const handleMouseEnter = (type: string, index: number) => {
  if (type === 'ai') {
    isHoveredAiMessage.value = index;
  } else {
    isHoveredUserMessage.value = index;
  }
};

const handleMouseLeave = (type: string) => {
  if (type === 'ai') {
    isHoveredAiMessage.value = -1;
  } else {
    isHoveredUserMessage.value = -1;
  }
};

// 复制消息到剪贴板
const copyMessage = async (content: string) => {
  const copied = await copyTextToClipboard(content);
  if (copied) {
    ElMessage.success('已复制到剪贴板');
  } else {
    ElMessage.error('复制失败，请手动复制');
  }
};

const fillPromptSuggestion = (prompt: string) => {
  inputMessage.value = prompt;
};

// 分享消息（示例）
const shareMessage = (content: string) => {
  // 显示 toast 提示信息
  ElMessage({
    message: '当前用户暂不支持分享',
    type: 'info',
    duration: 2000,
  });
};

// 点赞消息
const likeMessage = (index: number) => {};

// 点踩消息
const dislikeMessage = (index: number) => {};
</script>

<style lang="scss" scoped>
.center-panel {
  display: flex;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  flex-direction: column;
  background: var(--zv-bg-surface);
  border-radius: 0;
  box-shadow: none;
  container-type: inline-size;
}

.center-header {
  display: flex;
  height: 72px;
  min-height: 72px;
  padding: 10px 18px;
  align-items: center;
  justify-content: space-between;
  gap: var(--zv-space-3);
  text-align: left;
  background: var(--zv-bg-surface);
  border-bottom: 1px solid var(--zv-divider);
}

.workspace-title-wrap,
.center-header-actions,
.mobile-header-actions,
.input-action-group {
  display: flex;
  align-items: center;
}

.workspace-title-wrap {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.mobile-header-actions {
  margin-right: var(--zv-space-2);
}

.mobile-header-actions:empty {
  display: none;
  margin: 0;
}

.workspace-title-copy {
  min-width: 0;
  overflow: hidden;
}

.workspace-title-line {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--zv-space-2);
}

.center-title {
  margin: 0;
  color: var(--zv-text-primary);
  font-size: 20px;
  font-weight: var(--zv-font-weight-bold);
  line-height: var(--zv-line-height-tight);
  white-space: nowrap;
}

.current-skill-tag {
  --el-tag-bg-color: var(--zv-primary-soft);
  --el-tag-border-color: transparent;
  --el-tag-text-color: var(--zv-primary);

  max-width: min(220px, 24cqw);
  flex: 0 1 auto;
  font-weight: var(--zv-font-weight-medium);
}

.current-skill-tag :deep(.el-tag__content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-session-title {
  max-width: min(420px, 44cqw);
  margin: var(--zv-space-1) 0 0;
  overflow: hidden;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.center-header-actions {
  flex: 0 0 auto;
  gap: var(--zv-space-2);
}

.task-status-skeleton {
  width: 230px;
  height: 32px;
  border-radius: var(--zv-radius-md);
}

.task-status-summary {
  display: inline-flex;
  min-height: 32px;
  margin: 0;
  padding: 0;
  align-items: center;
  overflow: hidden;
  color: var(--zv-text-secondary);
  font: inherit;
  white-space: nowrap;
  background: var(--zv-bg-subtle);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-md);
  cursor: pointer;
  transition: color var(--zv-motion-fast) var(--zv-ease-standard),
    background-color var(--zv-motion-fast) var(--zv-ease-standard),
    border-color var(--zv-motion-fast) var(--zv-ease-standard),
    box-shadow var(--zv-motion-fast) var(--zv-ease-standard);
}

.task-status-summary:hover {
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
  border-color: var(--zv-primary-border);
}

.task-status-summary:focus-visible {
  outline: none;
  box-shadow: var(--zv-focus-ring);
}

.task-status-summary.is-idle,
.task-status-summary.is-unavailable {
  padding: 0 var(--zv-space-3);
  gap: var(--zv-space-2);
  font-size: var(--zv-font-size-xs);
  font-weight: var(--zv-font-weight-medium);
}

.task-status-summary.is-idle {
  color: var(--zv-success);
  background: var(--zv-success-soft);
  border-color: color-mix(in srgb, var(--zv-success) 26%, transparent);
}

.task-status-summary.is-unavailable {
  color: var(--zv-text-muted);
}

.task-status-idle-icon,
.task-status-unavailable-icon {
  font-size: var(--zv-font-size-lg);
}

.task-status-item {
  display: inline-flex;
  min-width: 0;
  height: 30px;
  padding: 0 9px;
  align-items: center;
  gap: var(--zv-space-1);
  font-size: var(--zv-font-size-xs);
}

.task-status-item + .task-status-item {
  border-left: 1px solid var(--zv-divider);
}

.task-status-item .el-icon {
  font-size: var(--zv-font-size-md);
}

.task-status-item strong {
  min-width: 1ch;
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-sm);
  font-variant-numeric: tabular-nums;
}

.task-status-item.is-warning .el-icon {
  color: var(--zv-warning);
}

.task-status-item.is-danger .el-icon {
  color: var(--zv-danger);
}

.task-status-item.is-primary .el-icon {
  color: var(--zv-primary);
}

.analysis-task-badge {
  flex: 0 0 auto;
}

.analysis-task-badge :deep(.el-badge__content) {
  display: none;
}

.analysis-task-button {
  margin: 0;
}

.compact-header {
  padding: 9px 14px;
  color: var(--zv-text-primary);
  background: var(--zv-bg-surface);
  border-bottom: 1px solid var(--zv-divider);
}

.chat-content {
  flex: 1;
  min-height: 0;
  padding: var(--zv-space-6) clamp(18px, 4vw, 54px);
  overflow-y: auto;
  background: var(--zv-bg-surface);
}

.message-list {
  width: min(100%, 840px);
  margin: 0 auto;
  gap: 0;
}

.message-item {
  width: 100%;
}

.message-item + .message-item {
  margin-top: var(--zv-space-10);
}

.is-ai-message-item + .message-item {
  margin-top: 72px;
}

.ai-message-container,
.user-message-container {
  gap: var(--zv-space-3);
}

.ai-message-container {
  align-items: flex-start;
  justify-content: flex-start;
}

.user-message-container {
  flex-direction: row;
  justify-content: flex-end;
}

.avatar {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  overflow: hidden;
  color: var(--zv-text-inverse);
  place-items: center;
  background: linear-gradient(145deg, var(--zv-primary), var(--zv-accent));
  border: 1px solid color-mix(in srgb, var(--zv-bg-surface) 75%, transparent);
  border-radius: var(--zv-radius-md);
  box-shadow: 0 7px 16px color-mix(in srgb, var(--zv-primary) 18%, transparent);
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.message-bubble {
  min-width: 0;
  border-radius: var(--zv-radius-lg);
}

.ai-message {
  width: calc(100% - 48px);
  max-width: calc(100% - 48px);
  padding: 5px 0 var(--zv-space-1);
  color: var(--zv-text-primary);
  background: transparent;
  box-shadow: none;
}

.user-message {
  width: auto;
  max-width: 82%;
  padding: 13px 16px 9px;
  color: var(--zv-text-primary);
  background: linear-gradient(135deg, var(--zv-primary-soft), var(--zv-bg-subtle));
  border: 1px solid var(--zv-primary-border);
  border-bottom-right-radius: var(--zv-radius-xs);
}

.message-time {
  margin-top: var(--zv-space-2);
  color: var(--zv-text-placeholder);
  font-size: 11px;
}

.message-actions {
  gap: var(--zv-space-1);
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.message-actions :deep(.el-button) {
  margin: 0;
  color: var(--zv-text-muted);
  background: transparent;
  border-color: transparent;
  box-shadow: none;
}

.message-actions :deep(.el-button:hover),
.message-actions :deep(.el-button:focus-visible) {
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
  border-color: transparent;
  box-shadow: none;
}

.input-area {
  flex: 0 0 auto;
  padding: var(--zv-space-3) clamp(18px, 4vw, 54px) var(--zv-space-4);
  background: linear-gradient(
    to bottom,
    color-mix(in srgb, var(--zv-bg-surface) 72%, transparent),
    var(--zv-bg-surface) 22%
  );
  border-top: 0;
}

.skill-unavailable-alert,
.input-container,
.suggestions {
  width: min(100%, 840px);
  margin-inline: auto;
}

.skill-unavailable-alert {
  margin-bottom: var(--zv-space-2);
  border-radius: var(--zv-radius-lg);
}

.input-container {
  position: relative;
  overflow: hidden;
  background: var(--zv-bg-surface);
  border: 1px solid var(--zv-border);
  border-radius: 16px;
  box-shadow: var(--zv-shadow-1);
  transition: border-color var(--zv-motion-fast) var(--zv-ease-standard),
    box-shadow var(--zv-motion-fast) var(--zv-ease-standard);
}

.input-container:focus-within {
  border-color: var(--zv-primary-border);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--zv-primary) 7%, transparent), var(--zv-shadow-2);
}

.input-container :deep(.el-textarea__inner) {
  min-height: 64px !important;
  padding: 14px 16px 4px;
  color: var(--zv-text-primary);
  line-height: 1.6;
  background: transparent;
  border: 0;
  resize: none;
  box-shadow: none;
}

.input-container :deep(.el-textarea__inner::placeholder) {
  color: var(--zv-text-placeholder);
}

.input-actions {
  position: static;
  display: flex;
  min-height: 44px;
  padding: 4px 8px 8px 10px;
  align-items: center;
  justify-content: space-between;
  gap: var(--zv-space-3);
  border-top: 0;
}

.input-action-group {
  min-width: 0;
  gap: var(--zv-space-2);
}

.input-action-primary {
  flex: 1;
}

.model-select {
  min-width: 88px;
  max-width: min(200px, 38vw);
}

.model-select :deep(.el-select__wrapper) {
  min-height: 32px;
  padding-inline: 10px;
  background: transparent;
  border: 0;
  border-radius: var(--zv-radius-round);
  box-shadow: none;
  transition: color var(--zv-motion-fast) var(--zv-ease-standard),
    background-color var(--zv-motion-fast) var(--zv-ease-standard),
    box-shadow var(--zv-motion-fast) var(--zv-ease-standard);
}

.model-select :deep(.el-select__wrapper:hover) {
  color: var(--zv-text-primary);
  background: var(--zv-bg-subtle);
}

.model-select :deep(.el-select__wrapper.is-focused) {
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
  box-shadow: none;
}

.model-select :deep(.el-select__selected-item) {
  max-width: calc(100% - 22px);
  color: inherit;
  font-size: var(--zv-font-size-sm);
  font-weight: var(--zv-font-weight-medium);
}

.model-select :deep(.el-select__caret) {
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}

:global(.dih-model-select-popper.el-popper) {
  padding: var(--zv-space-1);
  overflow: hidden;
  background: var(--zv-bg-surface);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-lg);
  box-shadow: var(--zv-shadow-2);
}

:global(.dih-model-select-popper--compact.el-popper) {
  z-index: 10001 !important;
  min-width: 176px !important;
}

:global(.dih-model-select-popper .el-select-dropdown__list) {
  padding: 0;
}

:global(.dih-model-select-popper .el-select-dropdown__item) {
  height: 34px;
  margin: 2px 0;
  padding: 0 var(--zv-space-3);
  color: var(--zv-text-secondary);
  line-height: 34px;
  border-radius: var(--zv-radius-md);
}

:global(.dih-model-select-popper .el-select-dropdown__item.is-hovering) {
  color: var(--zv-text-primary);
  background: var(--zv-bg-subtle);
}

:global(.dih-model-select-popper .el-select-dropdown__item.is-selected) {
  color: var(--zv-primary);
  font-weight: var(--zv-font-weight-semibold);
  background: var(--zv-primary-soft);
}

:global(.dih-model-select-popper .el-popper__arrow) {
  display: none;
}

.action-btn {
  min-width: 32px;
  min-height: 32px;
  padding: 0 9px;
  color: var(--zv-text-muted);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--zv-radius-round);
  box-shadow: none;
}

.deep-thinking-btn {
  gap: 5px;
}

.action-btn:hover,
.deep-thinking-active {
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
  border-color: transparent;
}

.action-btn:focus-visible {
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
  border-color: transparent;
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--zv-primary) 18%, transparent);
}

.send-btn,
.stop-btn {
  width: 36px;
  height: 36px;
  min-height: 36px;
  padding: 0;
  color: var(--zv-text-inverse);
  background: var(--zv-primary);
  border: 0;
  border-radius: 50%;
  box-shadow: 0 4px 10px color-mix(in srgb, var(--zv-primary) 22%, transparent);
}

.send-btn:hover,
.send-btn:focus {
  color: var(--zv-text-inverse);
  background: var(--zv-primary-hover);
}

.stop-btn,
.stop-btn:hover,
.stop-btn:focus {
  color: var(--zv-text-inverse);
  background: var(--zv-danger);
  box-shadow: 0 4px 10px color-mix(in srgb, var(--zv-danger) 20%, transparent);
}

.send-btn.is-disabled,
.send-btn.is-disabled:hover {
  color: var(--el-text-color-disabled);
  background: var(--el-fill-color);
  box-shadow: none;
}

.pending-attachments {
  display: flex;
  padding: 2px 12px 6px;
  flex-wrap: wrap;
  gap: var(--zv-space-2);
}

.pending-attachment,
.message-attachment {
  display: inline-flex;
  min-height: 28px;
  max-width: 260px;
  padding: var(--zv-space-1) var(--zv-space-2);
  align-items: center;
  gap: 6px;
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-xs);
  line-height: 1.2;
  background: var(--zv-bg-subtle);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-round);
}

.pending-image-attachment {
  position: relative;
  width: 66px;
  height: 66px;
  padding: 0;
  overflow: hidden;
  border-radius: var(--zv-radius-md);
}

.pending-image-preview {
  display: block;
  width: 66px;
  height: 66px;
  object-fit: cover;
  cursor: pointer;
}

.pending-image-attachment .attachment-remove-btn {
  position: absolute;
  top: 3px;
  right: 3px;
  color: var(--zv-text-secondary);
  background: color-mix(in srgb, var(--zv-bg-surface) 86%, transparent);
}

.pending-attachment.uploading {
  color: var(--zv-primary);
}

.attachment-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-size {
  flex-shrink: 0;
  color: var(--zv-text-placeholder);
}

.attachment-remove-btn {
  width: 20px;
  height: 20px;
  min-height: 20px;
  padding: 0;
  flex-shrink: 0;
  color: var(--zv-text-placeholder);
  background: transparent;
  border: 0;
  box-shadow: none;
}

.message-attachments {
  display: flex;
  margin-top: var(--zv-space-2);
  flex-wrap: wrap;
  gap: 6px;
}

.user-message .message-attachment {
  color: var(--zv-text-secondary);
  background: color-mix(in srgb, var(--zv-bg-surface) 72%, transparent);
  border-color: color-mix(in srgb, var(--zv-bg-surface) 60%, transparent);
}

.message-attachment.image-attachment {
  max-width: none;
  padding: 0;
  overflow: hidden;
  background: transparent;
}

.attachment-image-preview {
  display: block;
  width: min(220px, 58vw);
  max-height: 220px;
  object-fit: cover;
  cursor: zoom-in;
}

.message-content {
  min-width: 0;
  max-width: 100%;
  margin: 0;
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-md);
  overflow-wrap: anywhere;
}

.user-content {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.typing-indicator {
  display: flex;
  height: 24px;
  padding: var(--zv-space-1) var(--zv-space-2);
  align-items: center;
  gap: var(--zv-space-1);
}

.typing-dot {
  width: 6px;
  height: 6px;
  background: var(--zv-text-placeholder);
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-dot:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes typing {
  0%,
  80%,
  100% {
    transform: scale(0);
  }

  40% {
    transform: scale(1);
  }
}

.stop-icon-square {
  display: block;
  width: 8px;
  height: 8px;
  background: currentColor;
  border-radius: 2px;
}

.suggestion-icon {
  width: 1em;
  height: 1em;
  margin-right: 5px;
}

.suggestions {
  display: flex;
  margin-top: var(--zv-space-3);
  flex-wrap: wrap;
  gap: var(--zv-space-2);
}

.suggestion-btn {
  position: relative;
  display: inline-flex;
  height: 30px;
  margin: 0;
  padding: 0 var(--zv-space-3);
  align-items: center;
  color: var(--zv-text-secondary);
  background: var(--zv-bg-subtle);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-round);
  box-shadow: none;
  transition: color var(--zv-duration-fast) ease, background-color var(--zv-duration-fast) ease,
    border-color var(--zv-duration-fast) ease, box-shadow var(--zv-duration-fast) ease,
    transform var(--zv-duration-fast) ease;
}

.suggestion-btn:hover {
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
  border-color: var(--zv-primary-border);
}

.suggestion-btn.active {
  color: var(--zv-primary);
  font-weight: 600;
  background: linear-gradient(180deg, var(--zv-bg-card), var(--zv-primary-soft));
  border-color: var(--zv-border);
  box-shadow: 0 3px 10px color-mix(in srgb, var(--zv-primary) 12%, transparent);
}

.suggestion-btn.active::after {
  position: absolute;
  bottom: 2px;
  left: 50%;
  width: 16px;
  height: 2px;
  content: '';
  background: var(--zv-primary);
  border-radius: var(--zv-radius-round);
  transform: translateX(-50%);
}

.suggestion-btn:active {
  transform: translateY(1px);
}

.is-compact .chat-content {
  padding: var(--zv-space-3);
}

.is-compact .input-area {
  padding: var(--zv-space-2);
}

.is-compact .input-container {
  border-radius: 14px;
}

.is-compact .input-container :deep(.el-textarea__inner) {
  min-height: 52px !important;
  padding: 10px 12px 2px;
}

.is-compact .input-actions {
  min-height: 40px;
  padding: 3px 6px 6px 8px;
}

.is-compact .workspace-title-copy,
.is-compact .suggestions,
.is-compact .deep-thinking-btn span {
  display: none;
}

@container (max-width: 900px) {
  .task-status-item {
    padding-inline: var(--zv-space-2);
  }

  .task-status-label {
    display: none;
  }
}

@container (max-width: 680px) {
  .task-status-summary,
  .task-status-skeleton {
    display: none;
  }

  .analysis-task-badge :deep(.el-badge__content) {
    display: inline-flex;
  }

  .current-skill-tag {
    max-width: 24cqw;
  }
}

@container (max-width: 520px) {
  .current-skill-tag {
    display: none;
  }

  .current-session-title {
    max-width: 46cqw;
  }
}

@media (max-width: 720px) {
  .center-header {
    min-height: 68px;
    padding: 10px 12px;
  }

  .center-title {
    font-size: var(--zv-font-size-lg);
  }

  .center-header-actions :deep(.el-button span) {
    display: none;
  }

  .chat-content {
    padding: var(--zv-space-4) var(--zv-space-3);
  }

  .message-item + .message-item {
    margin-top: var(--zv-space-8);
  }

  .is-ai-message-item + .message-item {
    margin-top: 64px;
  }

  .input-area {
    padding: var(--zv-space-2) var(--zv-space-3) var(--zv-space-3);
  }

  .ai-message {
    width: calc(100% - 44px);
    max-width: calc(100% - 44px);
  }

  .user-message {
    max-width: 90%;
  }

  .input-actions {
    align-items: center;
    gap: var(--zv-space-2);
  }

  .deep-thinking-btn span {
    display: none;
  }
}

@media (max-width: 420px) {
  .current-session-title {
    display: none;
  }

  .center-header-actions {
    gap: 0;
  }

  .center-header-actions :deep(.el-button) {
    margin-left: 0;
    padding-inline: 8px;
  }

  .input-actions {
    gap: 4px;
  }

  .input-action-group {
    gap: 4px;
  }

  .send-btn,
  .stop-btn {
    width: 34px;
    height: 34px;
    min-height: 34px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .input-container,
  .chat-item,
  .action-btn {
    transition: none;
  }
}
</style>
