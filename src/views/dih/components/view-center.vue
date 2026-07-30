<template>
  <div class="panel center-panel">
    <!-- 中间面板标题 -->
    <div class="center-header">
      <h1 class="center-title">{{ chatSessionTitle }}</h1>
    </div>

    <!-- 聊天内容区域 -->
    <div class="chat-content" ref="chatContentRef">
      <!-- Enter提示信息 -->
      <!-- 已移除原来的 enter-tip 元素，改为使用 ElMessage 实现 toast 提示 -->
      <div class="message-list">
        <div v-for="(message, index) in messages" :key="messageRenderKey(message)" class="message-item">
          <div v-if="message.sender === 'ai'" class="ai-message-container"
            @mouseenter="handleMouseEnter('ai', index)" 
            @mouseleave="handleMouseLeave('ai')">
            <div class="avatar ai-avatar">
              <el-icon :size="20"><Monitor /></el-icon>
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
                <el-button @click="copyMessage(message.content)" size="small" :icon="CopyDocument">复制</el-button>
                <el-button @click="shareMessage(message.content)" size="small" :icon="Share">分享</el-button>
                <el-button @click="likeMessage(index)" size="small" :icon="Sunny">准确</el-button>
                <el-button @click="dislikeMessage(index)" size="small" :icon="Lightning">不准确</el-button>
              </div>
            </div>
          </div>
          <div v-else class="user-message-container"
            @mouseenter="handleMouseEnter('user', index)" 
            @mouseleave="handleMouseLeave('user')">
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
                    <span class="attachment-size">{{ formatFileSize(attachment.file_size ?? attachment.fileSize) }}</span>
                  </template>
                </div>
              </div>
              <div class="message-time">{{ message.time }}</div>
              <!-- 新增：用户消息的交互按钮 -->
              <div class="message-actions" v-show="isHoveredUserMessage === index">
                <el-button @click="copyMessage(message.content)" size="small" :icon="CopyDocument">复制</el-button>
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
        <el-input v-model="inputMessage" type="textarea" :rows="1" :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder="输入你的问题，帮你深度解答" @keydown.enter.exact.prevent="handleEnterPress" 
          @keydown.enter.shift.exact.prevent="insertLineBreak"></el-input>

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
              <span class="attachment-size">{{ formatFileSize(attachment.file_size ?? attachment.fileSize) }}</span>
            </template>
            <el-tooltip content="移除附件" placement="top">
              <el-button class="attachment-remove-btn" :icon="Close" circle @click="removePendingAttachment(index)" />
            </el-tooltip>
          </div>
          <div v-if="isUploadingAttachment" class="pending-attachment uploading">
            <el-icon><Loading /></el-icon>
            <span>正在上传...</span>
          </div>
        </div>

        <div class="input-actions">
          <el-select
            v-model="modelSelectData.period"
            class="model-select"
            popper-class="dih-model-select-popper"
            size="small"
            filterable
            placeholder="选择模型"
            no-match-text="未找到模型"
          >
            <el-option
              v-for="item in modelSelectData.periodOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
          
          <!-- 新增深度思考按钮 -->
          <el-tooltip v-if="chatSessionType === 'ask'" content="深度思考" placement="top">
            <el-button 
              class="action-btn" 
              :class="{ 'deep-thinking-active': isDeepThinking }"
              @click="toggleDeepThinking">
              <el-icon><Opportunity /></el-icon>
            </el-button>
          </el-tooltip>
          <!-- 结束新增 -->

          <el-tooltip content="上传文件" placement="top">
            <el-button class="action-btn" :disabled="isUploadingAttachment" @click="uploadFile">
              <el-icon>
                <Paperclip />
              </el-icon>
            </el-button>
          </el-tooltip>

          <el-tooltip v-if="isStreamingResponse" content="停止生成" placement="top">
            <el-button class="action-btn stop-btn" aria-label="停止生成" @click="stopCurrentChat">
              <span class="stop-icon-square" aria-hidden="true"></span>
            </el-button>
          </el-tooltip>

          <el-tooltip v-else content="发送" placement="top">
            <el-button class="action-btn send-btn" :disabled="!canSendMessage" @click="sendMessage">
              <el-icon>
                <Position />
              </el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>

      <!-- 快捷操作按钮 -->
      <div class="suggestions" v-if="showSuggestionBtn">
        <el-button v-for="(suggestion, index) in suggestions" :key="index" class="suggestion-btn" size="small"
          :class="{ 'active': router.currentRoute.value.query.type ===  suggestion.type}"
          @click="selectSuggestion(index)">
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
import { computed, ref, watch, onMounted, nextTick } from 'vue'
import {
  Close, CopyDocument, Lightning, Loading, Monitor, Opportunity, Paperclip, Position, Share, Sunny
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { DihService } from '@/service/api'
import { useRouter } from 'vue-router'
import { generateUUID } from '@/utils/util-common'
import { copyTextToClipboard } from '@/utils/clipboard';
import MessageCardRenderer from '@/components/dih-message/message-card-renderer.vue';
import { useChatAttachments } from '../composables/use-chat-attachments';
import { useChatMessageActions } from '../composables/use-chat-message-actions';
import { useChatSession } from '../composables/use-chat-session';
import { useChatStream } from '../composables/use-chat-stream';
import { useMcpApproval } from '../composables/use-mcp-approval';
import { usePanelRecordSync } from '../composables/use-panel-record-sync';
import {
  DATA_VISUALIZATION_CHART_DATA_EVENT,
  emitDihEvent,
} from '../events';
import type { ChatMessage, ChatMessagePart } from '@/types/type-dih';

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
  type: string
  label: string
  icon: any
}

interface Props {
  suggestions: Suggestion[]
  chatSessionId?: string
  chatSessionType?: string
  skillEntryUnavailable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  skillEntryUnavailable: false,
})

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
const isDeepThinking = ref(false)

watch(
  chatSessionType,
  sessionType => {
    if (sessionType !== 'ask') {
      isDeepThinking.value = false
    }
  },
  { immediate: true },
)

// 定义选择框结构
interface SelectData {
  period: string
  periodOptions: string[]
}

// 输入消息
const inputMessage = ref('')
const modelSelectData = ref<SelectData>({
  periodOptions: ['qianwen-max', 'deepseek-R1', 'deepseek-V3'],
  period: 'qianwen-max',
})

// 添加一个变量来跟踪Enter按键次数
const enterPressCount = ref(0)
const enterPressTimer = ref<number | null>(null)
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
  if (cleanContent.charCodeAt(0) === 0xFEFF) {
    cleanContent = cleanContent.slice(1);
  }

  const tryParse = (str: string) => {
    try { return JSON.parse(str); } catch (e) { return null; }
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
    if (lastBrace > firstBrace) { start = firstBrace; end = lastBrace; }
  } else if (firstBracket !== -1) {
    if (lastBracket > firstBracket) { start = firstBracket; end = lastBracket; }
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
  } catch (e) { /* ignore */ }

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
  } catch (e) { /* ignore */ }

  // 方法6: 暴力替换转义引号 (兼容旧逻辑 - 最后的兜底)
  try {
    const cleaned = cleanContent.replace(/\\"/g, '"');
    result = JSON.parse(cleaned);
    return result;
  } catch (e) { /* ignore */ }

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

const {
  isStreamingResponse,
  canSendMessage,
  sendMessage,
  stopCurrentChat,
} = useChatStream({
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
  } catch (error) {
  }
};

// 监听query参数的变化
watch(
  () => router.currentRoute.value.query,
  (newQuery, oldQuery) => {
    inputMessage.value = queryPromptValue(newQuery as Record<string, unknown>)
  },
  // 首次进入组件时也执行一次
  { immediate: true }     
)

// 切换深度思考模式
const toggleDeepThinking = () => {
  isDeepThinking.value = !isDeepThinking.value
}

// 处理Enter按键
const handleEnterPress = () => {
  enterPressCount.value++
  
  // 如果这是第一次按下Enter，启动计时器
  if (enterPressCount.value === 1) {
    enterPressTimer.value = window.setTimeout(() => {
      // 如果在300ms内没有再次按下Enter，则重置计数器
      enterPressCount.value = 0
      insertLineBreak();
      // 显示 toast 提示信息
      ElMessage({
        message: '快速按下两次Enter发送消息',
        type: 'info',
        duration: 2000
      });
    }, 300)
  } 
  // 如果这是第二次按下Enter，在300ms内
  else if (enterPressCount.value === 2) {
    // 清除计时器
    if (enterPressTimer.value) {
      clearTimeout(enterPressTimer.value)
      enterPressTimer.value = null
    }
    
    // 重置计数器
    enterPressCount.value = 0
    
    // 发送消息
    sendMessage()
  }
}

// 插入换行符
const insertLineBreak = () => {
  const textarea = document.querySelector('.input-container textarea') as HTMLTextAreaElement
  if (textarea) {
    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const text = inputMessage.value
    inputMessage.value = text.substring(0, start) + '\n' + text.substring(end)
    
    // 在下一次DOM更新后设置光标位置
    nextTick(() => {
      textarea.selectionStart = start + 1
      textarea.selectionEnd = start + 1
    })
  }
}

// 选择建议
const selectSuggestion = (index: number) => {
  const suggestion = props.suggestions[index]
  
  // 如果点击的是xx开启新的chatSession
  if (suggestion.type) {
    chatSessionId.value = generateUUID();
    router.push({
    name: 'service-dih',
    query: {
      type: suggestion.type,
      chatSessionId: chatSessionId.value,
      createSession: 1
    }
  });
}
}

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
        duration: 2000
      });
};

// 点赞消息
const likeMessage = (index: number) => {
};

// 点踩消息
const dislikeMessage = (index: number) => {
};

</script>

<style scoped>
.container {
  height: 100%;
  width: 100%;
  padding: 10px;
  box-sizing: border-box;
  box-shadow: var(--el-border-color-light) 0px 0px 10px;
  position: relative;
  overflow: hidden;
}

/* 抽屉触发器样式 */
.drawer-trigger {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  background-color: var(--el-color-primary);
  color: white;
  padding: 5px 15px;
  border-radius: 0 0 8px 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 5px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.drawer-trigger .el-icon {
  transition: transform 0.3s;
}

/* 自定义抽屉样式 */
.custom-drawer {
  position: absolute;
  top: -100%;
  /* 初始位置在容器外部上方 */
  left: 0;
  width: 100%;
  height: 100%;
  /* 填充满整个父容器的高度 */
  background-color: white;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  z-index: 5;
  transition: top 0.3s ease;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.custom-drawer.drawer-open {
  top: 0;
  /* 打开时移动到容器顶部 */
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  border-bottom: 1px solid #e4e7ed;
  font-size: 16px;
  font-weight: bold;
  flex-shrink: 0;
  /* 防止头部被压缩 */
}

.drawer-content {
  padding: 20px;
  overflow-y: auto;
  flex-grow: 1;
  /* 内容区域自动填充剩余空间 */
}

/* 面板容器 */
.panels-container {
  height: 100%;
  position: relative;
  z-index: 1;
}

/* 面板样式 */
.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 10px;
  box-sizing: border-box;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.left-panel {
  background-color: #f5f7fa;
  margin-left: 0;
  padding: 10px;
  overflow-y: auto;
}

.center-panel {
  background-color: #ffffff;
  display: flex;
  flex-direction: column;
  padding: 0;
}

.right-panel {
  background-color: #f5f7fa;
  color: #333;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

/* 左侧面板聊天列表样式 */
.chat-action {
  margin-bottom: 15px;
  width: 100%;
}

.new-chat-btn {
  border-radius: 20px;
  padding: 8px 15px;
  font-size: 14px;
  border: 1px solid #dcdfe6;
  background-color: white;
  width: 100%;
}

.btn-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.btn-icon {
  display: flex;
  align-items: left;
}

.btn-text {
  flex-grow: 1;
  text-align: center;
  margin: 0 10px;
}

.btn-shortcut {
  display: flex;
  align-items: center;
}

.shortcut-key {
  background-color: #f2f2f2;
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
}

.chat-history {
  display: flex;
  flex-direction: column;
}

.history-header {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 14px;
  color: #606266;
  margin-bottom: 10px;
  padding-left: 5px;
}

.chat-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.chat-item {
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #303133;
  transition: background-color 0.2s;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-item:hover {
  background-color: #e9ecef;
}

.chat-item.active {
  background-color: #e6f1fc;
}

.chat-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-actions {
  display: none;
}

.chat-item:hover .chat-actions,
.chat-item.active .chat-actions {
  display: block;
}

.more-btn {
  padding: 2px;
  font-size: 16px;
  color: #909399;
}

.more-btn:hover {
  color: #606266;
}

:deep(.el-dropdown-menu__item.delete-item) {
  color: #f56c6c;
}

:deep(.el-dropdown-menu__item i) {
  margin-right: 5px;
}

.view-all {
  text-align: center;
  color: #409eff;
  font-size: 14px;
  margin-top: 10px;
  cursor: pointer;
  padding: 5px;
}

.view-all:hover {
  text-decoration: underline;
}

.center-header {
  text-align: center;
  padding: 5px 0;
  border-bottom: 1px solid #f0f0f0;
}

.center-title {
  margin: 0;
  font-size: 18px;
  font-weight: bold;
}

.enter-tip {
  text-align: center;
  padding: 5px;
  background-color: #f0f9eb;
  color: #67c23a;
  border: 1px solid #e1f3d8;
  border-radius: 4px;
  margin: 10px auto;
  width: fit-content;
}

.chat-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.input-area {
  padding: 15px;
  border-top: 1px solid #f0f0f0;
}

.skill-unavailable-alert {
  margin-bottom: 10px;
}

.input-container {
  position: relative;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background-color: #fff;
}

.input-container:focus-within {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

:deep(.el-textarea__inner) {
  border: none;
  padding: 12px min(220px, 48%) 12px 12px;
  resize: none;
  box-shadow: none;
}

:deep(.el-textarea__inner:focus) {
  box-shadow: none;
}

.input-actions {
  position: absolute;
  right: 10px;
  bottom: 8px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.model-select {
  width: 60px;
}

:deep(.model-select .el-select__wrapper) {
  min-height: 28px;
  padding: 0 6px;
  background: transparent;
  box-shadow: none;
  border: none;
  border-radius: 6px;
}

:deep(.model-select .el-select__wrapper:hover),
:deep(.model-select .el-select__wrapper.is-focused) {
  box-shadow: none;
}

:deep(.model-select .el-select__selected-item) {
  max-width: 28px;
}

:global(.dih-model-select-popper) {
  max-width: 320px;
}

:global(.dih-model-select-popper .el-select-dropdown__wrap) {
  max-height: 260px;
}

:global(.dih-model-select-popper .el-select-dropdown__item) {
  max-width: 300px;
}

.pending-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 min(220px, 48%) 10px 12px;
}

.pending-attachment,
.message-attachment {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 260px;
  min-height: 28px;
  padding: 4px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background-color: #f7f8fa;
  color: #606266;
  font-size: 12px;
  line-height: 1.2;
}

.pending-image-attachment {
  position: relative;
  width: 66px;
  height: 66px;
  padding: 0;
  overflow: hidden;
}

.pending-image-preview {
  width: 66px;
  height: 66px;
  object-fit: cover;
  cursor: pointer;
  display: block;
}

.pending-image-attachment .attachment-remove-btn {
  position: absolute;
  top: 3px;
  right: 3px;
  color: #606266;
  background-color: rgba(255, 255, 255, 0.86);
}

.pending-attachment.uploading {
  color: #409eff;
}

.attachment-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-size {
  flex-shrink: 0;
  color: #909399;
}

.attachment-remove-btn {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  min-height: 20px;
  padding: 0;
  border: none;
  background: transparent;
  color: #909399;
}

.message-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.user-message .message-attachment {
  background-color: rgba(255, 255, 255, 0.7);
  border-color: rgba(255, 255, 255, 0.6);
  color: #2f4156;
}

.message-attachment.image-attachment {
  max-width: none;
  padding: 0;
  overflow: hidden;
  background-color: transparent;
}

.attachment-image-preview {
  display: block;
  width: min(220px, 58vw);
  max-height: 220px;
  object-fit: cover;
  cursor: zoom-in;
}

.action-btn {
  border: none;
  background: transparent;
  padding: 4px;
  color: #909399;
}

.deep-thinking-active {
  color: #409eff;
  background-color: rgba(64, 158, 255, 0.1);
}

.action-btn:hover {
  color: #606266;
}

.send-btn {
  color: #409eff;
}

.send-btn:hover {
  color: #66b1ff;
}

.stop-btn {
  width: 28px;
  height: 28px;
  min-height: 28px;
  padding: 0;
  border-radius: 50%;
  color: #fff;
  background-color: #ff4d4f;
  box-shadow: 0 4px 10px rgba(255, 77, 79, 0.28);
  transition: background-color 0.2s, box-shadow 0.2s, transform 0.2s;
}

.stop-btn:hover,
.stop-btn:focus {
  color: #fff;
  background-color: #ff6b6d;
  box-shadow: 0 6px 14px rgba(255, 77, 79, 0.36);
}

.stop-btn:active {
  transform: scale(0.94);
  background-color: #e94749;
}

.stop-icon-square {
  display: block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background-color: currentColor;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.suggestion-btn {
  border-radius: 20px;
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  color: #606266;
  white-space: nowrap;
  display: flex;
  align-items: center;
}

.suggestion-btn:hover {
  background-color: #e9ecef;
  color: #409eff;
}

.suggestion-btn.active {
  background-color: #409eff;
  color: white;
  border-color: #409eff;
}

.suggestion-icon {
  margin-right: 5px;
  width: 1em;
  height: 1em;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #333;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
}

.collapse-btn {
  color: #909399;
}

.info-card {
  margin: 12px;
  background-color: rgb(137, 137, 143);
  border-radius: 6px;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #a9a3a3;
  font-size: 14px;
}

.card-header .el-icon {
  margin-right: 8px;
}

.card-content {
  padding: 12px 16px;
}

.info-item {
  display: flex;
  margin-bottom: 12px;
  font-size: 14px;
}

.info-label {
  width: 100px;
  color: rgb(0, 0, 0);
}

.info-value {
  flex: 1;
  word-break: break-all;
}

.expand-btn {
  color: #409eff;
  padding: 0;
  font-size: 14px;
  margin-top: 8px;
}

.task-result {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #333;
}

.result-title {
  margin: 0 0 8px;
  line-height: 1.5;
}

.result-time {
  margin: 0;
  color: rgb(32, 95, 222);
  font-size: 12px;
}

.task-item {
  margin-bottom: 8px;
  border-radius: 4px;
  overflow: hidden;
}

.task-header {
  display: flex;
  align-items: flex-start;
  padding: 12px;
  background-color: rgb(121, 118, 118);
  cursor: pointer;
}

.task-icon {
  color: #409eff;
  margin-right: 8px;
  margin-top: 2px;
}

.task-title {
  flex: 1;
  font-size: 14px;
  line-height: 1.5;
}

.task-arrow {
  margin-left: 8px;
}

.task-arrow .is-rotate {
  transform: rotate(180deg);
}

.task-detail {
  padding: 12px;
  color: rgb(242, 244, 247);
  background-color: rgb(55, 59, 64);
  font-size: 14px;
}

/* 选项卡样式 */
.tab-container {
  flex: 1;
  overflow: hidden;
}

.right-tabs {
  height: 100%;
}

:deep(.el-tabs__content) {
  padding: 0;
  height: calc(100% - 40px);
  overflow-y: auto;
}

:deep(.el-tabs__nav) {
  background-color: #fff;
  padding: 0px 30px;
  width: 100%;
}

:deep(.el-tabs__item) {
  font-size: 14px;
  height: 40px;
  line-height: 40px;
}

:deep(.el-tabs__item.is-active) {
  font-weight: bold;
}

/* 可视化面板样式 */
.visualization-container {
  margin: 12px;
  background-color: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.visualization-header {
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
}

.visualization-header h4 {
  margin: 0;
  font-size: 16px;
}

.visualization-content {
  padding: 20px;
  min-height: 200px;
}

.placeholder-chart {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  background-color: #f9f9f9;
  border-radius: 4px;
  color: #909399;
}

.placeholder-chart .el-icon {
  font-size: 48px;
  margin-bottom: 10px;
}

/* 产物面板样式 */
.artifacts-container {
  padding: 12px;
}

.artifact-item {
  display: flex;
  align-items: center;
  padding: 12px;
  margin-bottom: 8px;
  background-color: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}

.artifact-icon {
  font-size: 24px;
  margin-right: 12px;
  color: #409eff;
}

.artifact-info {
  flex: 1;
}

.artifact-name {
  font-weight: 500;
  margin-bottom: 4px;
}

.artifact-path {
  font-size: 12px;
  color: #909399;
}

.artifact-actions {
  display: flex;
  gap: 8px;
}

/* 配置面板样式 */
.config-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0;
}

.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #2d2d30;
  color: #e0e0e0;
}

.config-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
}

.config-actions {
  display: flex;
  gap: 8px;
}

.editor-container {
  flex: 1;
  min-height: 500px;
}

.overview-container {
  padding: 10px 5px;
}

/* 状态卡片样式 */
.status-card {
  display: flex;
  background-color: #ffffff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  height: 100%;
  transition: all 0.3s ease;
}

.status-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.status-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  margin-right: 12px;
  font-size: 24px;
}

.status-icon.success {
  background-color: rgba(103, 194, 58, 0.1);
  color: #67c23a;
}

.status-icon.warning {
  background-color: rgba(230, 162, 60, 0.1);
  color: #e6a23c;
}

.status-icon.info {
  background-color: rgba(64, 158, 255, 0.1);
  color: #409eff;
}

.status-icon.primary {
  background-color: rgba(103, 119, 239, 0.1);
  color: #6777ef;
}

.status-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.status-info h4 {
  margin: 0 0 5px;
  font-size: 14px;
  color: #606266;
  font-weight: normal;
}

.status-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.status-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.text-danger {
  color: #f56c6c;
}

/* 标题样式 */
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 25px 0 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.section-title h3 {
  font-size: 18px;
  margin: 0;
  font-weight: 600;
  color: #303133;
}

/* 图表容器样式 */
.chart-container {
  background-color: #ffffff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  height: 100%;
  margin-bottom: 20px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.chart-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.chart-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 180px;
  background-color: #f9f9f9;
  border-radius: 6px;
  color: #909399;
  margin-bottom: 12px;
}

.chart-placeholder .el-icon {
  font-size: 40px;
  margin-bottom: 10px;
  opacity: 0.7;
}

.chart-legend {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding-top: 10px;
}

.legend-item {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #606266;
}

.legend-color {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
  margin-right: 6px;
}

.legend-color.high {
  background-color: #f56c6c;
}

.legend-color.medium {
  background-color: #e6a23c;
}

.legend-color.low {
  background-color: #67c23a;
}

/* 快速访问区样式 */
.quick-access {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 20px;
}

.access-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100px;
  height: 100px;
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.3s ease;
}

.access-item:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.access-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background-color: #f0f2f5;
  margin-bottom: 8px;
}

.access-icon .el-icon {
  font-size: 24px;
  color: #409eff;
}

.access-text {
  font-size: 14px;
  color: #606266;
}

/* 最近活动样式 */
.recent-activities {
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}

.activity-item {
  display: flex;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #ebeef5;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-time {
  width: 50px;
  color: #909399;
  font-size: 13px;
}

.activity-content {
  flex: 1;
  margin: 0 12px;
}

.activity-title {
  font-weight: 500;
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
}

.activity-desc {
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
}

.activity-action {
  margin-left: 12px;
}


.message-list {
  display: flex;
  flex-direction: column;
  gap: 40px;
}

.message-item {
  display: flex;
  justify-content: flex-start;
  min-width: 0;
}

.message-bubble {
  max-width: 80%;
  min-width: 0;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-break: break-word;
  overflow-wrap: anywhere;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  position: relative;
}

.user-message {
  max-width: 80%;
  min-width: 0;
  background-color: #e6f1fc;
  align-self: flex-end;
  border-bottom-right-radius: 4px;
}

.ai-message {
  max-width: 80%;
  min-width: 0;
  background-color: #f5f7fa;
  align-self: flex-start;
  border-bottom-left-radius: 4px;
}

.user-message-container {
  display: flex;
  flex-direction: row-reverse;
  width: 100%;
  min-width: 0;
}

.ai-message-container {
  display: flex;
  width: 100%;
  min-width: 0;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 10px;
  flex-shrink: 0;
}

.ai-avatar {
  background-color: #409eff;
  color: white;
}

.message-content {
  margin: 0;
  font-size: 14px;
  color: #303133;
  max-width: 100%;
  min-width: 0;
  overflow-wrap: anywhere;
}

/* 添加用户消息内容样式，支持换行 */
.user-content {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

/* 添加Markdown内容样式 */
.markdown-body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial,
    sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji';
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
}

.markdown-body::before {
  display: table;
  content: "";
}

.markdown-body::after {
  display: table;
  clear: both;
  content: "";
}

.markdown-body>*:first-child {
  margin-top: 0 !important;
}

.markdown-body>*:last-child {
  margin-bottom: 0 !important;
}

.markdown-body a {
  color: #409eff;
  text-decoration: none;
}

.markdown-body a:hover {
  text-decoration: underline;
}

.markdown-body a:active,
.markdown-body a:hover {
  outline-width: 0;
}

.markdown-body strong {
  font-weight: 600;
}

.markdown-body h1 {
  font-size: 2em;
  margin: 0.67em 0;
}

.markdown-body h2 {
  font-size: 1.5em;
  margin: 0.83em 0;
}

.markdown-body h3 {
  font-size: 1.25em;
  margin: 1em 0;
}

.markdown-body h4 {
  font-size: 1em;
  margin: 1.33em 0;
}

.markdown-body h5 {
  font-size: 0.875em;
  margin: 1.67em 0;
}

.markdown-body h6 {
  font-size: 0.85em;
  color: #6a737d;
  margin: 2em 0;
}

.markdown-body p {
  margin-top: 0;
  margin-bottom: 10px;
}

.markdown-body blockquote {
  margin: 0;
  padding: 0 1em;
  color: #6a737d;
  border-left: 0.25em solid #dfe2e5;
}

.markdown-body ul,
.markdown-body ol {
  padding-left: 2em;
  margin-top: 0;
  margin-bottom: 16px;
}

.markdown-body ul ul,
.markdown-body ul ol,
.markdown-body ol ol,
.markdown-body ol ul {
  margin-top: 0;
  margin-bottom: 0;
}

.markdown-body li {
  word-wrap: break-all;
}

.markdown-body li>p {
  margin-top: 16px;
}

.markdown-body li+li {
  margin-top: 0.25em;
}

.markdown-body code {
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  background-color: rgba(27, 31, 35, 0.05);
  border-radius: 3px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.markdown-body pre {
  word-wrap: normal;
  background-color: #f6f8fa;
  border-radius: 3px;
  padding: 16px;
  overflow: auto;
  font-size: 85%;
  line-height: 1.45;
}

.markdown-body pre code {
  background: transparent;
  word-break: normal;
  overflow-wrap: normal;
}

.markdown-body pre code,
.markdown-body pre tt {
  display: inline;
  max-width: auto;
  padding: 0;
  margin: 0;
  overflow: visible;
  line-height: inherit;
  word-wrap: normal;
  background-color: transparent;
  border: 0;
}

.markdown-body hr {
  height: 0.25em;
  padding: 0;
  margin: 24px 0;
  background-color: #e1e4e8;
  border: 0;
}

.markdown-body table {
  border-spacing: 0;
  border-collapse: collapse;
  display: block;
  width: max-content;
  max-width: 100%;
  overflow: auto;
}

.markdown-body td,
.markdown-body th {
  padding: 6px 13px;
  border: 1px solid #dfe2e5;
}

.markdown-body th {
  font-weight: 600;
}

.markdown-body tr {
  background-color: #fff;
  border-top: 1px solid #c6cbd1;
}

.markdown-body tr:nth-child(2n) {
  background-color: #f6f8fa;
}

.markdown-body img {
  max-width: 100%;
  box-sizing: content-box;
  background-color: #fff;
}

/* 确保AI消息容器中的Markdown内容样式正确应用 */
.ai-message .message-content {
  color: #303133;
  background-color: transparent;
}

/* 确保AI消息容器中的Markdown内容样式正确应用 */
.ai-message .markdown-body {
  color: #303133;
  background-color: transparent;
}

.message-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  text-align: right;
}

.message-actions {
  display: flex;
  gap: 8px;
  position: absolute;
  bottom: 0;
  left: 0;
  transform: translateY(100%);
  background-color: rgba(255, 255, 255, 0.3);
  padding: 4px;
  border-radius: 4px;
  z-index: 10;
}

.message-actions .el-button {
  padding: 4px 8px;
  font-size: 12px;
  border: none;
  background: transparent;
}

.message-actions .el-button:hover {
  background-color: #f2f6fc;
}

/* 图表消息样式 */
.message-chart {
  width: 100%;
  min-height: 200px;
  background-color: #fff;
  border-radius: 4px;
  padding: 8px;
  margin-bottom: 8px;
}

/* 打字机动画效果 */
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  height: 24px;
}

.typing-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #909399;
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-dot:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes typing {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}
</style>
