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
        <div v-for="(message, index) in messages" :key="index" class="message-item">
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
              <chat-message-renderer
                v-else
                :message="message"
                @copy-code="copyMessage"
                @decide-action="handleActionDecision(message, $event)"
                @submit-info-steps="handleInfoStepsSubmit(message, $event)"
                @add-chart-library="handleAddChartLibrary(message, $event)"
                @choose-analysis-decision="handleAnalysisDecision(message, $event)"
                @choose-data-access-decision="handleDataAccessDecision(message, $event)"
                @decide-mcp-approval="handleMcpApprovalDecision(message, $event)"
                @select-prompt-suggestion="fillPromptSuggestion"
              />
              <div class="message-time">{{ message.time }}</div>
              <!-- 新增：AI消息的交互按钮 -->
              <div class="message-actions" v-show="isHoveredAiMessage === index">
                <el-button @click="copyMessage(message.content)" size="small" icon="CopyDocument">复制</el-button>
                <el-button @click="shareMessage(message.content)" size="small" icon="Share">分享</el-button>
                <el-button @click="likeMessage(index)" size="small" icon="Sunny">准确</el-button>
                <el-button @click="dislikeMessage(index)" size="small" icon="Lightning">不准确</el-button>
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
                <el-button @click="copyMessage(message.content)" size="small" icon="CopyDocument">复制</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
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
          <el-tooltip content="深度思考" placement="top">
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
import { computed, ref, reactive, watch, onMounted, onUnmounted, nextTick } from 'vue'
import {
  Close, Loading, Monitor, Paperclip, Position, Opportunity
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { DihService } from '@/service/api'
import { useRouter } from 'vue-router'
import { generateUUID } from '@/utils/util-common'
import {getCurrentFormattedDate} from '@/utils/util-time'
import { copyTextToClipboard } from '@/utils/clipboard';
import { withBaseUrl } from '@/utils/url';
import ChatMessageRenderer from './chat-message-renderer.vue';
import type { AnalysisRecord, ChatAttachment, ChatMessage, ChatMessagePart, ChatSession, McpApprovalData, McpApprovalDecision, PolicyRecord } from '@/types/type-dih';

const router = useRouter();

const chatContentRef = ref<HTMLElement | null>(null);

const scrollToBottom = async (): Promise<void> => {
  await nextTick();
  if (chatContentRef.value) {
    chatContentRef.value.scrollTop = chatContentRef.value.scrollHeight;
  }
};

// 定义基础信息接口
interface BasicInfo {
  label: string
  value: string
}

// 定义调查结果接口
interface InvestigationResult {
  title: string
  time: string
}

// 定义任务接口
interface Task {
  title: string
  detail: string
  expanded: boolean
}

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
}

type SendMessageOptions = {
  content?: string;
  requestContent?: string;
};

type DataAccessRecord = Record<string, unknown> & {
  id?: string;
  name?: string;
  status?: string;
};

type InfoStepAnswer = {
  id: string;
  title: string;
  value: string;
  source: 'suggestion' | 'custom';
};

const DATA_ACCESS_RECORD_EVENT = 'dihDataAccessRecordsUpdated';
const DATA_VISUALIZATION_RECORD_EVENT = 'dihDataVisualizationRecordsUpdated';
const DATA_ANALYSIS_RECORD_EVENT = 'dihAnalysisRecordsUpdated';
const DATA_ANALYSIS_RECORD_REQUEST_EVENT = 'dihAnalysisRecordsRequested';
const POLICY_RECORD_EVENT = 'dihPolicyRecordsUpdated';
const POLICY_RECORD_REQUEST_EVENT = 'dihPolicyRecordsRequested';
const POLICY_RECORD_ACTION_EVENT = 'dihPolicyRecordActionRequested';
const DATA_REPORT_RECORD_EVENT = 'dihReportRecordsUpdated';
const DATA_REPORT_RECORD_REQUEST_EVENT = 'dihReportRecordsRequested';
const REPORT_QUICK_ACTION_EVENT = 'dihReportQuickActionRequested';
const REPORT_EXTRA_DATA_CHANGED_EVENT = 'dihReportExtraDataChanged';
const REPORT_SELECTION_REWRITE_COMPLETED_EVENT = 'dihReportSelectionRewriteCompleted';
const MAX_UPLOAD_BYTES = 30 * 1024 * 1024;
const MAX_FILES_PER_PICK = 10;
const UPLOAD_CONCURRENCY = 3;

const props = defineProps<Props>()

// 添加深度思考状态
const isDeepThinking = ref(false)

// 定义选择框结构
interface SelectData {
  period: string
  periodOptions: string[]
}

// 输入消息
const inputMessage = ref('')
const pendingAttachments = ref<ChatAttachment[]>([])
const isUploadingAttachment = ref(false)
const isStreamingResponse = ref(false)
const currentChatAbortController = ref<AbortController | null>(null)
const currentStreamingMessageIndex = ref<number | null>(null)
const isUserStoppingChat = ref(false)
const canSendMessage = computed(() => {
  return !isUploadingAttachment.value
    && !isStreamingResponse.value
    && (inputMessage.value.trim().length > 0 || pendingAttachments.value.length > 0)
})

const AUTO_CONFIRM_ACTIONS = new Set([
  'analysis.start',
  'analysis.create_continuous_task',
  'analysis.confirm_log_aggregation',
  'analysis.confirm_sandbox_result',
  'analysis_demo.confirm_log_aggregation',
  'analysis_demo.confirm_sandbox_result',
  'policy.confirm_trial',
  'policy.confirm_apply',
  'policy_demo.confirm_trial',
  'policy_demo.confirm_retry_trial',
  'policy_demo.confirm_apply',
  'policy.apply_to_production',
  'data_access.generate_demo_push_config',
  'data_access.create_demo_push_task',
  'data_visualization.add_chart_library',
  'data_visualization.apply_config',
])

const AUTO_REJECT_ACTIONS = new Set([
  'analysis.confirm_log_aggregation',
  'analysis.confirm_sandbox_result',
  'analysis_demo.confirm_log_aggregation',
  'analysis_demo.confirm_sandbox_result',
  'policy.confirm_trial',
  'policy.confirm_apply',
  'policy_demo.confirm_trial',
  'policy_demo.confirm_retry_trial',
  'policy_demo.confirm_apply',
  'data_access.generate_demo_push_config',
  'data_access.create_demo_push_task',
  'data_visualization.apply_config',
])

// 添加一个变量来跟踪Enter按键次数
const enterPressCount = ref(0)
const enterPressTimer = ref<number | null>(null)
// 移除了 showEnterTip 变量，因为我们不再使用页面内元素显示提示

// 面板展开状态
const panelStates = reactive({
  main: true,
  info: true,
  tasks: true
})

// 基础信息数据
const basicInfo = ref<BasicInfo[]>([
  { label: '源IP', value: '10.108.108.23' },
  { label: '目的IP', value: '10.106.108.110' },
  { label: '目的端口', value: '8080' },
  { label: '进程命令行', value: 'c:\\windows\\temp\\frpc.exe -c c:\\windows\\temp\\frpc.toml' }
])

// 调查结果数据
const investigationResult = ref<InvestigationResult>({
  title: '调查结果1：IP地址10.108.108.23在短时间内高频（11次）向目标主机的one.jsp文件发起POST请求，可能存在利用WebShell执行命令或传输恶意数据的行为，需要调查该IP的网络行为是否为攻击源。',
  time: '调查时间：2025-04-18 16:00:00 - 2025-04-18 20:00:00'
})

// 任务数据
const tasks = ref<Task[]>([
  {
    title: '分析主机c39842ce43b5ebc8上的网络连接调查frpc.exe(7628)的网络连接情况，特别是到10.106.108.110:8080的连接行为',
    detail: '这里是任务1的详细信息和结果...',
    expanded: false
  },
  {
    title: '分析主机c39842ce43b5ebc8上的DNS访问调查frpc.exe(7628)请求过的DNS域名',
    detail: '这里是任务2的详细信息和结果...',
    expanded: false
  },
  {
    title: '分析主机c39842ce43b5ebc8上的进程创建调查frpc.exe(7628)创建过哪些子进程',
    detail: '这里是任务3的详细信息和结果...',
    expanded: false
  }
])

// 计算已完成任务数
const completedTasks = ref(0)

// 总任务数
const totalTasks = ref(3)


// 消息列表
const messages = ref<ChatMessage[]>([
  {
    sender: 'ai',
    content: '嘿！我是你的人工智能助手。有什么问题尽管问我吧！',
    time: getCurrentFormattedDate()
  }
])
const chatSessionExtraData = ref('');

const asObject = (value: unknown): Record<string, unknown> => {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
};

const asRecordList = (value: unknown): DataAccessRecord[] => {
  return Array.isArray(value)
    ? value.filter(item => item && typeof item === 'object').map(item => item as DataAccessRecord)
    : [];
};

const upsertById = (items: DataAccessRecord[], record: DataAccessRecord) => {
  const id = String(record.id || record.fileName || record.taskId || record.name || '');
  if (!id) {
    return [...items, record];
  }
  const next = items.filter(item => String(item.id || item.fileName || item.taskId || item.name || '') !== id);
  next.push(record);
  return next;
};

const upsertInto = (items: DataAccessRecord[], record: DataAccessRecord) => {
  items.splice(0, items.length, ...upsertById(items, record));
};

const parseSessionExtraData = () => {
  if (!chatSessionExtraData.value.trim()) {
    return {};
  }
  try {
    return asObject(JSON.parse(chatSessionExtraData.value));
  } catch {
    return {};
  }
};

const textValue = (value: unknown, fallback = '') => {
  if (typeof value === 'string') {
    return value;
  }
  if (value === undefined || value === null) {
    return fallback;
  }
  return String(value);
};

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

const prettyTextValue = (value: unknown, fallback = '') => {
  if (typeof value === 'string') {
    return value.trim() || fallback;
  }
  if (value === undefined || value === null || value === '') {
    return fallback;
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
};

const truncateText = (value: string, maxLength = 4000) => {
  return value.length > maxLength ? `${value.slice(0, maxLength)}\n...` : value;
};

const buildChartLibraryRecord = (part: ChatMessagePart): DataAccessRecord => {
  const metadata = asObject(part.metadata);
  const name = textValue(metadata.title || part.title, '临时可视化图表');
  const entity = textValue(metadata.entity, '');
  const chartType = textValue(metadata.chartType, '');
  return {
    id: textValue(metadata.id, `chart:${entity || 'unknown'}:${name}`),
    title: '图表库记录已创建',
    name,
    description: textValue(metadata.content || part.content || metadata.description, ''),
    entity,
    chartType,
    api: textValue(metadata.api, ''),
    status: 'temporary',
    source: 'session',
    config: metadata.amisConfig || metadata.config || {},
  };
};

const ensureChatSessionRecordId = async () => {
  if (chatSessionRecordId.value) {
    return chatSessionRecordId.value;
  }
  if (!chatSessionId.value) {
    return '';
  }
  const data = await DihService.getChatSession(chatSessionId.value, { type: chatSessionType.value });
  chatSessionRecordId.value = data.id || '';
  if (data.extraData && !chatSessionExtraData.value) {
    chatSessionExtraData.value = data.extraData;
  }
  return chatSessionRecordId.value;
};

const addChartRecordToExtraData = (record: DataAccessRecord) => {
  const extraData = asObject(parseSessionExtraData());
  const dataVisualization = asObject(extraData.dataVisualization);
  extraData.dataVisualization = {
    ...dataVisualization,
    chartLibrary: upsertById(asRecordList(dataVisualization.chartLibrary), record),
  };
  return JSON.stringify(extraData);
};

const extractDataAccessRecords = () => {
  const metadataConfigs: DataAccessRecord[] = [];
  const dataPushServices: DataAccessRecord[] = [];
  const dataAccess = asObject(parseSessionExtraData().dataAccess);
  asRecordList(dataAccess.metadataConfigs).forEach(record => {
    upsertInto(metadataConfigs, record);
  });
  asRecordList(dataAccess.dataPushServices).forEach(record => {
    upsertInto(dataPushServices, record);
  });
  return {
    metadataConfigs,
    dataPushServices,
  };
};

const extractDataVisualizationRecords = () => {
  const dataVisualization = asObject(parseSessionExtraData().dataVisualization);
  return {
    chartLibrary: asRecordList(dataVisualization.chartLibrary),
    visualizationConfigs: asRecordList(dataVisualization.visualizationConfigs),
    dashboardConfigs: asRecordList(dataVisualization.dashboardConfigs),
    menuConfigs: asRecordList(dataVisualization.menuConfigs),
  };
};

const extractAnalysisRecords = () => {
  const records: AnalysisRecord[] = [];
  const analysis = asObject(parseSessionExtraData().analysis);
  asRecordList(analysis.records).forEach(record => {
    upsertInto(records as DataAccessRecord[], record);
  });
  return {
    records,
    aggregatedLogs: asRecordList(analysis.aggregatedLogs),
    sandboxResults: asRecordList(analysis.sandboxResults),
    conclusionTimeline: asRecordList(analysis.conclusionTimeline),
  };
};

const extractPolicyRecords = () => {
  const records: PolicyRecord[] = [];
  const policy = asObject(parseSessionExtraData().policy);
  asRecordList(policy.records).forEach(record => {
    upsertInto(records as DataAccessRecord[], record);
  });
  return { records };
};

const latestRecord = (records: Record<string, unknown>[]) => records.length ? records[records.length - 1] : {};

const findTimelineContent = (timeline: Record<string, unknown>[], keywords: string[]) => {
  const matched = [...timeline].reverse().find(item => {
    const title = textValue(item.title).toLowerCase();
    const id = textValue(item.id).toLowerCase();
    return keywords.some(keyword => title.includes(keyword.toLowerCase()) || id.includes(keyword.toLowerCase()));
  });
  return prettyTextValue(matched?.content);
};

const findLatestDisposalStrategyConfig = () => {
  const disposalPart = [...messages.value]
    .reverse()
    .flatMap(message => [...(message.parts || [])].reverse())
    .find(part => part.type === 'config' && textValue(part.metadata?.configKind) === 'disposal-strategy');
  return disposalPart?.content?.trim() || '';
};

const buildDisposeAgentPrompt = (detail?: string) => {
  const analysisData = extractAnalysisRecords();
  const timeline = analysisData.conclusionTimeline;
  const reportRecord = latestRecord(analysisData.records.filter(record => textValue(record.stage) === 'report_output') as Record<string, unknown>[]);
  const sandboxRecord = latestRecord(analysisData.sandboxResults);
  const disposalSuggestion = findTimelineContent(timeline, ['处置建议', 'disposal', 'recommendation'])
    || prettyTextValue((reportRecord.recommendations as unknown[] | undefined)?.join?.('\n'))
    || '请基于上一轮研判结论生成处置方案。';
  const analysisTarget = findTimelineContent(timeline, ['分析目标', 'target']);
  const analysisProcess = findTimelineContent(timeline, ['分析过程', 'process']);
  const analysisConclusion = findTimelineContent(timeline, ['分析结论', 'conclusion']);
  const disposalStrategyConfig = findLatestDisposalStrategyConfig();
  const extraDetail = detail?.trim();

  return truncateText([
    '请基于以下研判分析结果进入策略控制流程，生成可执行前需确认的处置方案。',
    '',
    '## 处置建议',
    disposalSuggestion,
    '',
    '## 研判上下文',
    analysisTarget ? `分析目标：${analysisTarget}` : '',
    analysisProcess ? `分析过程：${analysisProcess}` : '',
    analysisConclusion ? `分析结论：${analysisConclusion}` : '',
    `聚合日志数量：${analysisData.aggregatedLogs.length}`,
    sandboxRecord.result ? `沙箱研判结果：\n${prettyTextValue(sandboxRecord.result)}` : '',
    disposalStrategyConfig ? `处置策略建议配置：\n${disposalStrategyConfig}` : '',
    extraDetail ? `用户补充要求：\n${extraDetail}` : '',
    '',
    '## 输出要求',
    '1. 先说明拟执行处置动作、影响范围、前置检查和回滚方案。',
    '2. 生成策略控制智能体可确认的处置配置或策略配置。',
    '3. 不要直接执行写入、发布、阻断、隔离等副作用动作，必须先等待用户确认。',
  ].filter(Boolean).join('\n'));
};

const openDisposeAgentSession = async (prompt: string) => {
  const nextChatSessionId = generateUUID();
  const promptRef = generateUUID();
  try {
    window.sessionStorage?.setItem(`dih:prefill:${promptRef}`, prompt);
  } catch {
    // ignore storage failures and fall back to query string below
  }
  let storedPrompt = false;
  try {
    storedPrompt = window.sessionStorage?.getItem(`dih:prefill:${promptRef}`) === prompt;
  } catch {
    storedPrompt = false;
  }
  await router.push({
    name: 'service-dih',
    query: {
      type: 'agent_dispose',
      chatSessionId: nextChatSessionId,
      createSession: 1,
      ...(storedPrompt ? { msgRef: promptRef } : { msg: encodeURIComponent(prompt) }),
    },
  });
};

const extractReportRecords = () => {
  const report = asObject(parseSessionExtraData().report);
  return {
    currentDocument: asObject(report.currentDocument),
    documents: asRecordList(report.documents),
    artifacts: asRecordList(report.artifacts),
    extraData: chatSessionExtraData.value,
    sessionRecordId: chatSessionRecordId.value,
    sessionId: chatSessionId.value,
  };
};

const publishDataAccessRecords = () => {
  window.dispatchEvent(new CustomEvent(DATA_ACCESS_RECORD_EVENT, {
    detail: extractDataAccessRecords(),
  }));
};

const publishDataVisualizationRecords = () => {
  window.dispatchEvent(new CustomEvent(DATA_VISUALIZATION_RECORD_EVENT, {
    detail: extractDataVisualizationRecords(),
  }));
};

const publishAnalysisRecords = () => {
  window.dispatchEvent(new CustomEvent(DATA_ANALYSIS_RECORD_EVENT, {
    detail: extractAnalysisRecords(),
  }));
};

const publishPolicyRecords = () => {
  window.dispatchEvent(new CustomEvent(POLICY_RECORD_EVENT, {
    detail: extractPolicyRecords(),
  }));
};

const publishReportRecords = () => {
  window.dispatchEvent(new CustomEvent(DATA_REPORT_RECORD_EVENT, {
    detail: extractReportRecords(),
  }));
};

watch(chatSessionExtraData, () => {
  publishDataAccessRecords();
  publishDataVisualizationRecords();
  publishAnalysisRecords();
  publishPolicyRecords();
  publishReportRecords();
});

const showSuggestionBtn = ref(true);
const chatSessionTitle = ref('新的会话');
const chatSessionId = ref('');
const chatSessionRecordId = ref('');
const chatSessionType = ref('');

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

const createThinkingPart = (status: 'running' | 'completed' = 'running'): ChatMessagePart => ({
  id: generateUUID(),
  type: 'thinking',
  title: '思考过程',
  content: status === 'running' ? '正在深度思考，请稍候...' : '已完成深度思考。',
  status,
});

const hasThinkTag = (content: string) => content.includes('<think>');

const attachmentFileId = (attachment: ChatAttachment) => {
  return attachment.file_id || attachment.fileId || attachment.file_name || attachment.fileName || 'attachment';
};

const attachmentFileName = (attachment: ChatAttachment) => {
  return attachment.file_name || attachment.fileName || '未命名文件';
};

const formatFileSize = (size?: number) => {
  if (!size || size <= 0) return '';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
};

const isImageAttachment = (attachment: ChatAttachment) => {
  const contentType = attachment.content_type || attachment.contentType || '';
  const fileName = attachmentFileName(attachment).toLowerCase();
  return attachment.kind === 'image'
    || ['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'image/bmp'].includes(contentType.toLowerCase())
    || /\.(png|jpe?g|webp|gif|bmp)$/i.test(fileName);
};

const attachmentPreviewUrl = (attachment: ChatAttachment) => {
  const url = attachment.file_url || attachment.fileUrl || '';
  return url ? withBaseUrl(url) : '';
};

const openAttachmentPreview = (attachment: ChatAttachment) => {
  const url = attachmentPreviewUrl(attachment);
  if (url) {
    window.open(url, '_blank', 'noopener,noreferrer');
  }
};

const removePendingAttachment = (index: number) => {
  pendingAttachments.value.splice(index, 1);
};

onUnmounted(() => {
  isUserStoppingChat.value = true;
  void rejectPendingMcpApprovals(currentStreamingMessageIndex.value, '聊天页面已关闭');
  currentChatAbortController.value?.abort();
});

const createDeepThinkingStreamingParts = (content: string): ChatMessagePart[] => {
  const parts: ChatMessagePart[] = [createThinkingPart('running')];
  if (content.trim()) {
    parts.push({
      id: generateUUID(),
      type: 'markdown',
      content,
    });
  }
  return parts;
};

const mcpApprovalPart = (data: McpApprovalData): ChatMessagePart => ({
  id: data.requestId,
  type: 'mcp-approval',
  title: `MCP 工具审批：${data.toolName || data.toolKey || '未知工具'}`,
  content: data.description || '该工具需要你的明确许可后才能执行。',
  status: String(data.status || 'pending').toLowerCase(),
  metadata: {
    ...data,
    deciding: false,
    decisionInFlight: '',
  },
});

const isMcpApprovalPart = (part: ChatMessagePart) => part.type === 'mcp-approval';

const upsertMcpApprovalPart = (message: ChatMessage, data: McpApprovalData, accumulatedContent: string) => {
  if (!data.requestId) return;
  const nextPart = mcpApprovalPart(data);
  const parts = message.parts ? [...message.parts] : (accumulatedContent ? createMarkdownParts(accumulatedContent) : []);
  const index = parts.findIndex(part => isMcpApprovalPart(part) && part.id === data.requestId);
  if (index >= 0) {
    parts[index] = {
      ...parts[index],
      ...nextPart,
      metadata: {
        ...parts[index].metadata,
        ...nextPart.metadata,
      },
    };
  } else {
    parts.push(nextPart);
  }
  message.parts = parts;
  message.loading = false;
};

const appendStreamingDelta = (message: ChatMessage, delta: string, accumulatedContent: string) => {
  const containsApproval = message.parts?.some(isMcpApprovalPart) === true;
  if (!containsApproval) {
    message.parts = isDeepThinking.value && !hasThinkTag(accumulatedContent)
      ? createDeepThinkingStreamingParts(accumulatedContent)
      : undefined;
    return;
  }
  if (!delta) return;
  const parts = message.parts ? [...message.parts] : [];
  const last = parts.at(-1);
  if (last?.type === 'markdown') {
    last.content = `${last.content || ''}${delta}`;
  } else {
    parts.push({ id: generateUUID(), type: 'markdown', content: delta });
  }
  message.parts = parts;
};

const mergeFinalApprovalParts = (liveParts: ChatMessagePart[] | undefined, finalParts: ChatMessagePart[] | undefined) => {
  if (!liveParts?.some(isMcpApprovalPart)) return finalParts;
  const liveApprovalById = new Map(
    liveParts.filter(isMcpApprovalPart).map(part => [part.id, part]),
  );
  const finalApprovalById = new Map(
    (finalParts || []).filter(isMcpApprovalPart).map(part => [part.id, part]),
  );
  if (finalApprovalById.size > 0) {
    return (finalParts || []).map(part => {
      if (!isMcpApprovalPart(part)) return part;
      const livePart = liveApprovalById.get(part.id);
      if (!livePart) return part;
      return {
        ...livePart,
        ...part,
        metadata: {
          ...livePart.metadata,
          ...part.metadata,
        },
      };
    });
  }
  const finalContentParts = (finalParts || []).filter(part => !isMcpApprovalPart(part));
  const liveContentParts = liveParts.filter(part => !isMcpApprovalPart(part));
  const finalIsPlainContent = finalContentParts.every(part => part.type === 'markdown' || part.type === 'thinking');
  if (!finalIsPlainContent) {
    return [...liveParts.filter(isMcpApprovalPart).map(part => finalApprovalById.get(part.id) || part), ...finalContentParts];
  }
  return liveParts.map(part => isMcpApprovalPart(part) ? (finalApprovalById.get(part.id) || part) : part)
    .concat(finalContentParts.length && !liveContentParts.length ? finalContentParts : []);
};

const handleMcpApprovalDecision = async (
  message: ChatMessage,
  payload: { part: ChatMessagePart; decision: McpApprovalDecision },
) => {
  const requestId = payload.part.id || String(payload.part.metadata?.requestId || '');
  if (!requestId || payload.part.status !== 'pending' || payload.part.metadata?.deciding === true) return;
  payload.part.metadata = {
    ...payload.part.metadata,
    deciding: true,
    decisionInFlight: payload.decision,
  };
  try {
    const approval = await DihService.decideMcpApproval(requestId, { decision: payload.decision });
    upsertMcpApprovalPart(message, approval, message.content || '');
  } catch (error) {
    payload.part.metadata = { ...payload.part.metadata, deciding: false, decisionInFlight: '' };
    ElMessage.error(error instanceof Error ? error.message : '审批操作失败，请稍后重试');
  }
};

const rejectPendingMcpApprovals = async (messageIndex: number | null, comment: string) => {
  if (messageIndex === null || !messages.value[messageIndex]) return;
  const pendingParts = (messages.value[messageIndex].parts || [])
    .filter(part => isMcpApprovalPart(part) && part.status === 'pending' && part.id);
  await Promise.allSettled(pendingParts.map(async part => {
    const approval = await DihService.decideMcpApproval(part.id!, { decision: 'rejected', comment });
    upsertMcpApprovalPart(messages.value[messageIndex], approval, messages.value[messageIndex].content || '');
  }));
};

const getChartContent = (message: ChatMessage) => {
  const chartPart = message.parts?.find(part => part.type === 'chart');
  return chartPart?.content || message.content;
};

const markChartAsLoaded = (message: ChatMessage) => {
  message.content = '图表数据已加载，请在右侧面板查看可视化结果。';
  message.parts = createMarkdownParts(message.content);
};

const dispatchChartData = (jsonData: any) => {
  window.dispatchEvent(new CustomEvent('dataVisualizationChartData', {
    detail: {
      chartType: jsonData.chart_type || 'line',
      option: jsonData.option || jsonData,
      rawData: jsonData.raw_data,
      columns: jsonData.columns
    }
  }));
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

const refreshChatSessionExtraData = async () => {
  if (!chatSessionId.value) {
    chatSessionExtraData.value = '';
    return;
  }
  try {
    const data = await DihService.getChatSession(chatSessionId.value, { type: chatSessionType.value });
    chatSessionRecordId.value = data.id || chatSessionRecordId.value;
    chatSessionExtraData.value = data.extraData || '';
  } catch (error) {
    console.error('刷新会话附加数据失败:', error);
  }
};

const getChatSession = async () => {
  // 优先使用 props 传入的参数（悬浮窗模式），否则从路由获取
  if (props.chatSessionType) {
    chatSessionType.value = props.chatSessionType;
  } else {
    const currentSessionType = router.currentRoute.value.query.type;
    chatSessionType.value = currentSessionType ? currentSessionType.toString() : 'ask';
  }
  
  // 优先使用 props 传入的 chatSessionId（悬浮窗模式）
  if (props.chatSessionId) {
    chatSessionId.value = props.chatSessionId;
  } else {
    const currentChatSessionId = router.currentRoute.value.query.chatSessionId;
    if(currentChatSessionId){
      chatSessionId.value = currentChatSessionId.toString();
    }else{
      // 参数没有传递chattSessionId
      chatSessionId.value = generateUUID();
      router.push({
      name: 'service-dih',
      query: {
        type: chatSessionType.value,
        chatSessionId: chatSessionId.value,
        createSession: 1
      }
    });
    }
  }
  
  // 获取聊天会话数据
  if (chatSessionId.value) {
    try {
      const data = await DihService.getChatSession(chatSessionId.value,{type:chatSessionType.value});
      chatSessionRecordId.value = data.id || '';
      messages.value = data.messageList;
      chatSessionExtraData.value = data.extraData || '';
      
      // 延迟处理消息格式，确保右侧组件已经完全挂载
      setTimeout(() => {
        messages.value.forEach(msg => {
          processMessageFormat(msg);
        });
        scrollToBottom();
      }, 500);

      chatSessionTitle.value = data.title || '新的会话';
      scrollToBottom();
    } catch (error) {
      console.error("获取聊天会话数据失败:", error);
      messages.value = [{
        sender: 'ai',
        content: '嘿！我是你的人工智能助手。有什么问题尽管问我吧！',
        time: getCurrentFormattedDate()
      }];
      chatSessionExtraData.value = '';
      chatSessionRecordId.value = '';
      chatSessionTitle.value = '新的会话';
    }
  } else {
    messages.value = [{
        sender: 'ai',
        content: '嘿！我是你的人工智能助手。有什么问题尽管问我吧！',
        time: getCurrentFormattedDate()
      }];
    chatSessionExtraData.value = '';
    chatSessionRecordId.value = '';
    chatSessionTitle.value = '新的会话';
  }

  showSuggestionBtn.value = messages.value.length <= 1;
  scrollToBottom();
};



onMounted(() => {
  getChatSession();
  // 获取模型列表
  fetchModelList();
});

// 监听路由参数chatSessionId的变化
watch(
  () => router.currentRoute.value.query.chatSessionId,
  (newChatSessionId, oldChatSessionId) => {
    // 当chatSessionId发生变化时，重新获取聊天会话
    getChatSession();
  }
);

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

const markMessageStopped = (messageIndex: number | null) => {
  if (messageIndex === null || !messages.value[messageIndex]) {
    return;
  }
  const message = messages.value[messageIndex];
  message.loading = false;
  message.isError = false;
  if (message.content?.includes('[已停止生成]')) {
    return;
  }
  message.content = message.content?.trim()
    ? `${message.content}\n\n[已停止生成]`
    : '已停止生成';
  const approvalParts = (message.parts || []).filter(isMcpApprovalPart);
  message.parts = approvalParts.length
    ? [...approvalParts, ...createMarkdownParts(message.content)]
    : undefined;
};

const stopCurrentChat = async () => {
  if (!currentChatAbortController.value || !isStreamingResponse.value) {
    return;
  }
  isUserStoppingChat.value = true;
  await rejectPendingMcpApprovals(currentStreamingMessageIndex.value, '用户停止生成');
  currentChatAbortController.value.abort();
  markMessageStopped(currentStreamingMessageIndex.value);
  ElMessage.info('已停止生成');
  scrollToBottom();
};

// 发送消息
const sendMessage = async (options: SendMessageOptions = {}) => {
  const explicitMessage = options.content?.trim();
  const explicitRequestMessage = options.requestContent?.trim();
  const canSend = explicitMessage
    ? !isUploadingAttachment.value && !isStreamingResponse.value
    : canSendMessage.value;

  if (canSend) {
    // 清空输入框 
    const currentInputMessage = inputMessage.value.trim();
    const sendMessage = explicitRequestMessage || explicitMessage || currentInputMessage;
    const messageAttachments = explicitMessage ? [] : pendingAttachments.value.slice();
    const displayMessage = explicitMessage || currentInputMessage || '请分析上传的附件内容。';
    if (!explicitMessage) {
      inputMessage.value = ''
      pendingAttachments.value = []
    }
    // 添加用户消息
    messages.value.push({
      sender: 'user',
      content: displayMessage,
      time: getCurrentFormattedDate(),
      attachments: messageAttachments,
    })

    // 添加AI回复占位消息
    const aiMessageIndex = messages.value.length;
    messages.value.push({
      sender: 'ai',
      content: '',
      time: getCurrentFormattedDate(),
      loading: true,
      parts: isDeepThinking.value ? [createThinkingPart('running')] : undefined,
    })

    scrollToBottom();

    
    const abortController = new AbortController();
    currentChatAbortController.value = abortController;
    currentStreamingMessageIndex.value = aiMessageIndex;
    isUserStoppingChat.value = false;
    isStreamingResponse.value = true;

    try {
      let accumulatedContent = '';
      const streamOk = await DihService.chatEvents({
        type: chatSessionType.value,
        message: sendMessage,
        model: modelSelectData.value.period,
        deep_think: isDeepThinking.value,
        chat_id: chatSessionId.value, // 使用正确的chatSessionId
        attachments: messageAttachments,
      }, async event => {
        if (event.event === 'delta') {
          const delta = event.content || '';
          accumulatedContent += delta;
          if (messages.value[aiMessageIndex].loading) {
            messages.value[aiMessageIndex].loading = false;
          }
          messages.value[aiMessageIndex].content = accumulatedContent;
          appendStreamingDelta(messages.value[aiMessageIndex], delta, accumulatedContent);
          await nextTick();
          scrollToBottom();
          return;
        }

        if ((event.event === 'approval_required' || event.event === 'approval_updated') && event.data) {
          upsertMcpApprovalPart(messages.value[aiMessageIndex], event.data as McpApprovalData, accumulatedContent);
          await nextTick();
          scrollToBottom();
          return;
        }

        if (event.event === 'done') {
          if (event.message && typeof event.message !== 'string') {
            const liveParts = messages.value[aiMessageIndex].parts;
            messages.value[aiMessageIndex] = {
              ...event.message,
              parts: mergeFinalApprovalParts(liveParts, event.message.parts),
              loading: false,
            };
            processMessageFormat(messages.value[aiMessageIndex]);
            await refreshChatSessionExtraData();
          } else {
            messages.value[aiMessageIndex].loading = false;
            messages.value[aiMessageIndex].content = accumulatedContent;
          }
          await nextTick();
          scrollToBottom();
          return;
        }

        if (event.event === 'error') {
          messages.value[aiMessageIndex].loading = false;
          messages.value[aiMessageIndex].isError = true;
          messages.value[aiMessageIndex].content = typeof event.message === 'string'
            ? event.message
            : '抱歉，回复失败，请稍后重试~';
          await nextTick();
          scrollToBottom();
        }
      }, { signal: abortController.signal });

      if (!streamOk) {
        if (abortController.signal.aborted || isUserStoppingChat.value) {
          markMessageStopped(aiMessageIndex);
          return;
        }
        messages.value[aiMessageIndex].loading = false;
        messages.value[aiMessageIndex].isError = true;
        messages.value[aiMessageIndex].content = '抱歉，回复失败，请稍后重试~';
        return;
      }
      
      // 判断是否为新聊天会话
      const isNewChat = router.currentRoute.value.query.createSession;
      // 如果是新聊天且至少有一条完整的消息交互，则添加到聊天列表
      if (isNewChat && messages.value.length >= 2) {
        let createdSession: ChatSession | null = null;
        try {
          createdSession = await DihService.getChatSession(chatSessionId.value, { type: chatSessionType.value });
          chatSessionRecordId.value = createdSession?.id || chatSessionRecordId.value;
          chatSessionExtraData.value = createdSession?.extraData || chatSessionExtraData.value;
        } catch (error) {
          console.warn('获取新会话真实ID失败，将使用sessionId作为临时ID:', error);
        }
        // 创建新的聊天记录项
        const newChatItem = {
          id: createdSession?.id || chatSessionId.value,
          type: createdSession?.type || chatSessionType.value,
          sessionId: createdSession?.sessionId || chatSessionId.value,
          title: createdSession?.title || `${displayMessage.substring(0, 20)}${displayMessage.length > 20 ? '...' : ''}`,
          pin: createdSession?.pin || false,
        };
        // 触发事件通知父组件添加新的聊天项
        // 这里可以通过emit或者其他方式通知view-left组件
        window.dispatchEvent(new CustomEvent('newChatCreated', { 
          detail: { chatItem: newChatItem } 
        }));
        const nextQuery = { ...router.currentRoute.value.query };
        delete nextQuery.createSession;
        router.replace({ name: 'service-dih', query: nextQuery });
      }
    } catch (error) {
      if (abortController.signal.aborted || isUserStoppingChat.value) {
        markMessageStopped(aiMessageIndex);
        return;
      }
      console.error('聊天接口调用失败:', error);
      messages.value[aiMessageIndex].loading = false;
      messages.value[aiMessageIndex].isError = true;
      messages.value[aiMessageIndex].content = '抱歉，回复失败，请稍后重试~';
    } finally {
      if (currentChatAbortController.value === abortController) {
        currentChatAbortController.value = null;
      }
      if (currentStreamingMessageIndex.value === aiMessageIndex) {
        currentStreamingMessageIndex.value = null;
      }
      isStreamingResponse.value = false;
      isUserStoppingChat.value = false;
    }
  }
}

type ReportQuickActionEventDetail = {
  displayContent?: string;
  requestContent?: string;
  target?: 'document' | 'selection';
  actionKey?: string;
  selectionId?: string;
};

type ReportExtraDataChangedEventDetail = {
  extraData?: string;
};

type PolicyRecordActionEventDetail = {
  action?: 'trial' | 'apply';
  record?: PolicyRecord;
};

const handleReportRecordsRequested = () => {
  publishReportRecords();
};

const handleAnalysisRecordsRequested = () => {
  publishAnalysisRecords();
};

const handlePolicyRecordsRequested = () => {
  publishPolicyRecords();
};

const policyRecordLabel = (record?: PolicyRecord) => {
  return textValue(record?.fileName || record?.id || record?.recordId, '当前策略记录');
};

const policyRecordActionMessage = (action: 'trial' | 'apply', record: PolicyRecord) => {
  const recordId = textValue(record.id || record.recordId);
  const configText = prettyTextValue(record.newConfig);
  if (action === 'trial') {
    const retry = textValue(record.id || record.recordId).includes('v2') || record.changeMode === 'modify';
    const firstLine = retry ? '我已确认重新进入试验场验证。' : '我已确认进入试验场验证。';
    return [
      firstLine,
      `请基于策略记录 ${recordId || policyRecordLabel(record)} 执行试验场验证，并在验证完成后输出 zenvis:policy-record 更新验证状态。`,
      '',
      JSON.stringify({
        recordId,
        policyType: record.policyType,
        configType: record.configType,
        fileName: record.fileName,
        newConfig: record.newConfig,
      }, null, 2),
      configText ? `\n策略配置：\n${configText}` : '',
    ].filter(Boolean).join('\n');
  }
  return [
    '我已确认下发策略到系统正式生效。',
    `请基于策略记录 ${recordId || policyRecordLabel(record)} 调用配置管理 MCP 写入并应用策略，成功后输出 zenvis:policy-record 将生效状态更新为 yes。`,
    '',
    JSON.stringify({
      recordId,
      policyType: record.policyType,
      configType: record.configType,
      fileName: record.fileName,
      validationStatus: record.validationStatus,
      newConfig: record.newConfig,
    }, null, 2),
  ].join('\n');
};

const handlePolicyRecordActionRequested = async (event: Event) => {
  const detail = (event as CustomEvent<PolicyRecordActionEventDetail>).detail || {};
  const action = detail.action;
  const record = detail.record;
  if (!action || !record) {
    ElMessage.warning('缺少策略记录，无法执行操作');
    return;
  }
  if (isStreamingResponse.value) {
    ElMessage.warning('当前正在生成，请稍后再试');
    return;
  }
  await sendMessage({
    content: action === 'trial'
      ? `我已确认将「${policyRecordLabel(record)}」推送到试验场验证。`
      : `我已确认下发「${policyRecordLabel(record)}」到系统正式生效。`,
    requestContent: policyRecordActionMessage(action, record),
  });
};

const handleReportExtraDataChanged = (event: Event) => {
  const detail = (event as CustomEvent<ReportExtraDataChangedEventDetail>).detail || {};
  if (typeof detail.extraData === 'string') {
    chatSessionExtraData.value = detail.extraData;
  }
};

const stripSelectionRewriteFence = (content = '') => {
  const trimmed = content.trim();
  const match = trimmed.match(/^```(?:[\w:-]+)?\s*\n?([\s\S]*?)\n?```$/);
  return (match?.[1] || trimmed).trim();
};

const extractSelectionRewriteContent = (message?: ChatMessage) => {
  if (!message) {
    return '';
  }
  const preferredPart = message.parts?.find(part => {
    return ['report-document', 'markdown', 'code'].includes(part.type) && !!part.content?.trim();
  });
  return stripSelectionRewriteFence(preferredPart?.content || message.content || '');
};

const handleReportQuickActionRequested = async (event: Event) => {
  const detail = (event as CustomEvent<ReportQuickActionEventDetail>).detail || {};
  const requestContent = detail.requestContent?.trim();
  if (!requestContent) {
    ElMessage.warning('快捷写作指令为空');
    return;
  }
  if (isStreamingResponse.value) {
    ElMessage.warning('当前正在生成，请稍后再试');
    return;
  }
  const messageStartIndex = messages.value.length;
  await sendMessage({
    content: detail.displayContent || '请根据右侧文档执行 AI 写作操作。',
    requestContent,
  });
  if (detail.target === 'selection') {
    const responseMessage = [...messages.value.slice(messageStartIndex)]
      .reverse()
      .find(message => message.sender === 'ai' && !message.loading && !message.isError);
    window.dispatchEvent(new CustomEvent(REPORT_SELECTION_REWRITE_COMPLETED_EVENT, {
      detail: {
        selectionId: detail.selectionId,
        actionKey: detail.actionKey,
        content: extractSelectionRewriteContent(responseMessage),
      },
    }));
  }
};

onMounted(() => {
  window.addEventListener(DATA_ANALYSIS_RECORD_REQUEST_EVENT, handleAnalysisRecordsRequested);
  window.addEventListener(POLICY_RECORD_REQUEST_EVENT, handlePolicyRecordsRequested);
  window.addEventListener(POLICY_RECORD_ACTION_EVENT, handlePolicyRecordActionRequested);
  window.addEventListener(DATA_REPORT_RECORD_REQUEST_EVENT, handleReportRecordsRequested);
  window.addEventListener(REPORT_EXTRA_DATA_CHANGED_EVENT, handleReportExtraDataChanged);
  window.addEventListener(REPORT_QUICK_ACTION_EVENT, handleReportQuickActionRequested);
  nextTick(() => publishAnalysisRecords());
  nextTick(() => publishPolicyRecords());
  nextTick(() => publishReportRecords());
});

onUnmounted(() => {
  window.removeEventListener(DATA_ANALYSIS_RECORD_REQUEST_EVENT, handleAnalysisRecordsRequested);
  window.removeEventListener(POLICY_RECORD_REQUEST_EVENT, handlePolicyRecordsRequested);
  window.removeEventListener(POLICY_RECORD_ACTION_EVENT, handlePolicyRecordActionRequested);
  window.removeEventListener(DATA_REPORT_RECORD_REQUEST_EVENT, handleReportRecordsRequested);
  window.removeEventListener(REPORT_EXTRA_DATA_CHANGED_EVENT, handleReportExtraDataChanged);
  window.removeEventListener(REPORT_QUICK_ACTION_EVENT, handleReportQuickActionRequested);
});

const confirmAction = (part: ChatMessagePart) => {
  const action = part.metadata?.action;
  return typeof action === 'string' ? action : '';
};

const autoConfirmMessage = (action: string) => {
  if (action === 'analysis.confirm_log_aggregation' || action === 'analysis_demo.confirm_log_aggregation') {
    return '我已确认日志聚合结果，请进入沙箱研判阶段。';
  }
  if (action === 'analysis.confirm_sandbox_result' || action === 'analysis_demo.confirm_sandbox_result') {
    return '我已确认沙箱研判结果，结果满意，请进入分析结论阶段。';
  }
  if (action === 'policy.confirm_trial' || action === 'policy_demo.confirm_trial') {
    return '我已确认进入试验场验证，请将当前策略记录推送到试验场做测试验证，并输出 zenvis:policy-record 更新验证状态。';
  }
  if (action === 'policy_demo.confirm_retry_trial') {
    return '我已确认重新进入试验场验证，请将修复后的策略记录推送到试验场重新测试验证，并输出 zenvis:policy-record 更新验证状态。';
  }
  if (action === 'policy.confirm_apply' || action === 'policy_demo.confirm_apply') {
    return '我已确认下发策略到系统正式生效，请调用配置管理 MCP 写入并应用策略，成功后输出 zenvis:policy-record 将生效状态更新为 yes。';
  }
  if (action === 'analysis.create_continuous_task') {
    return '我已确认持续分析任务方案，请根据上一条确认卡和配置开始创建数据推送服务与 AI 分析任务。';
  }
  if (action === 'policy.apply_to_production') {
    return '我已确认更新生产策略配置，请根据上一条确认卡、模拟测试结果和配置块，通过配置管理 MCP 写入系统配置。';
  }
  if (action === 'data_access.generate_demo_push_config') {
    return '我已确认继续生成用户事件数据推送服务配置。请先生成完整的数据推送服务配置并展示给我确认，不要创建或启动数据推送服务。';
  }
  if (action === 'data_access.create_demo_push_task') {
    return '我已确认创建用户事件数据推送服务，请根据上一条确认卡和数据推送配置创建并启动数据推送服务。';
  }
  if (action === 'data_visualization.apply_config') {
    return '我已确认并授权应用上一轮数据可视化配置。请根据上一条确认卡和已生成的配置内容，按需调用配置、看板和菜单 MCP 工具写入系统；写入或创建成功后，请输出 zenvis:visualization-config-record、zenvis:dashboard-config-record、zenvis:menu-config-record 等记录围栏。';
  }
  if (action === 'data_visualization.add_chart_library') {
    return '我已确认把上一轮临时图表加入图表库，请记录该图表的 amis 配置并输出 zenvis:visualization-chart-record。';
  }
  return '我已确认研判分析方案，请根据上一条确认卡开始执行一次性研判分析。';
};

const autoRejectMessage = (action: string) => {
  if (
    action === 'analysis.confirm_log_aggregation'
    || action === 'analysis.confirm_sandbox_result'
  ) {
    return '我已取消研判流程，请暂停当前研判，不要进入下一阶段。';
  }
  if (action === 'analysis_demo.confirm_log_aggregation' || action === 'analysis_demo.confirm_sandbox_result') {
    return '我已取消研判演示流程，请暂停当前研判演示，不要进入下一阶段。';
  }
  if (action === 'policy.confirm_trial' || action === 'policy.confirm_apply') {
    return '我已取消当前策略控制流程，请暂停当前策略操作，不要进入下一阶段。';
  }
  if (
    action === 'policy_demo.confirm_trial'
    || action === 'policy_demo.confirm_retry_trial'
    || action === 'policy_demo.confirm_apply'
  ) {
    return '我已取消策略控制演示流程，请暂停当前策略控制演示，不要进入下一阶段。';
  }
  if (action === 'data_access.generate_demo_push_config') {
    return '我已取消生成用户事件数据推送服务配置。请记录本次演示到元数据配置阶段结束，不要生成数据推送配置，也不要创建数据推送服务。';
  }
  if (action === 'data_access.create_demo_push_task') {
    return '我已取消创建用户事件数据推送服务。请记录数据推送配置已生成但未添加到系统，不要创建或启动数据推送服务。';
  }
  if (action === 'data_visualization.apply_config') {
    return '我选择放弃本次数据可视化配置。请记录本次配置已放弃，不要写入 open_config，不要创建菜单，也不要创建看板。';
  }
  return '我已取消本次操作。';
};

const dataVisualizationDecisionMessage = (decision: 'revise', detail?: string) => {
  const focus = detail?.trim() || '请基于上一轮数据可视化配置继续优化展示字段、图表布局、菜单或看板配置。';
  return `我需要补充信息继续更新数据可视化配置。调整要求如下：\n${focus}\n请基于上一轮数据可视化配置重新生成完整配置，并再次展示完整配置和后续选择。`;
};

const dataVisualizationDecisionDisplayMessage = (decision: 'revise', detail?: string) => {
  const focus = detail?.trim() || '继续优化数据可视化配置。';
  return `我已补充数据可视化配置调整要求：\n${focus}`;
};

const analysisDemoConfirmReviseMessage = (action: string, detail?: string) => {
  const focus = detail?.trim() || '请基于上一阶段结果补充更多关联数据。';
  if (action === 'analysis.confirm_log_aggregation' || action === 'analysis_demo.confirm_log_aggregation') {
    return `我需要补充更多日志聚合数据。补充内容如下：\n${focus}\n请基于上一轮日志聚合结果继续补充相关日志，并再次展示日志聚合结果让我确认。`;
  }
  if (action === 'analysis.confirm_sandbox_result' || action === 'analysis_demo.confirm_sandbox_result') {
    return `我需要补充信息继续沙箱研判。补充研判重点如下：\n${focus}\n请基于上一轮沙箱研判结果继续补充分析，并再次展示沙箱研判结果让我确认。`;
  }
  return focus;
};

const analysisDemoConfirmReviseDisplayMessage = (action: string, detail?: string) => {
  const focus = detail?.trim() || '继续补充研判信息。';
  if (action === 'analysis.confirm_log_aggregation' || action === 'analysis_demo.confirm_log_aggregation') {
    return `我已补充日志聚合数据：\n${focus}`;
  }
  if (action === 'analysis.confirm_sandbox_result' || action === 'analysis_demo.confirm_sandbox_result') {
    return `我已补充沙箱研判信息：\n${focus}`;
  }
  return focus;
};

const policyConfirmReviseMessage = (detail?: string) => {
  const focus = detail?.trim() || '请基于上一轮策略配置继续补充更新。';
  return `我需要补充更新策略配置。调整要求如下：\n${focus}\n请基于上一轮策略记录重新生成策略配置，并再次输出 zenvis:policy-record 后让我确认是否进入试验场验证。`;
};

const policyConfirmReviseDisplayMessage = (detail?: string) => {
  const focus = detail?.trim() || '继续补充更新策略配置。';
  return `我已补充策略更新要求：\n${focus}`;
};

const analysisDecisionMessage = (decision: 'dispose' | 'ignore' | 'continue', detail?: string) => {
  if (decision === 'dispose') {
    return '我选择执行处置。请基于上一轮研判结论、关键证据和处置策略配置，进入处置执行准备流程；先说明拟执行动作、影响范围、回滚方案和需要我确认的配置。';
  }
  if (decision === 'ignore') {
    return '我选择忽略本次告警。请基于上一轮研判结论记录忽略原因、适用条件和后续观察建议，不执行处置动作。';
  }
  const focus = detail?.trim() || '请围绕上一轮尚未闭环的疑点继续补充证据。';
  return `我需要补充信息继续研判。补充研判重点如下：\n${focus}\n请基于上一轮证据继续研判，并说明新增证据、结论变化和下一步建议。`;
};

const dataAccessDecisionMessage = (decision: 'apply_config' | 'abandon' | 'revise', detail?: string) => {
  if (decision === 'apply_config') {
    return '我已确认并授权添加上一轮已生成并展示的 meta 元数据配置到系统。本条消息就是写入授权：请不要再次询问是否添加配置。请立即按顺序调用元数据配置 MCP：1. policy_config_tree(type="meta") 检查目标文件是否存在；2. 如果目标文件不存在，调用 policy_config_add(type="meta", configDto={"fileName":"<目标文件名>"}) 创建文件；3. 调用 policy_config_apply(type="meta", configDto={"fileName":"<目标文件名>","text":"<上一轮完整 meta json>"}) 写入并应用；4. 调用 policy_config_read(type="meta", fileName="<目标文件名>") 读回校验文件确实存在且内容已写入；5. 只有在目标文件已存在且需要覆盖时，才先读取旧文件、说明差异并等待我确认覆盖。只有 MCP 返回成功且读回校验通过后，才用 Markdown 围栏代码块输出 zenvis:meta-config-record 记录；zenvis:meta-config-record 不是工具名，请不要调用它。';
  }
  if (decision === 'abandon') {
    return '我选择放弃本次元数据配置。请记录本次配置已放弃，不要写入系统，也不要继续创建或更新相关配置。';
  }
  const focus = detail?.trim() || '请基于上一轮配置继续优化字段、实体或展示规则。';
  return `我需要补充信息继续更新元数据配置。调整要求如下：\n${focus}\n请基于上一轮 meta 配置重新生成完整配置，并再次展示完整配置和后续选择。`;
};

const dataAccessDecisionDisplayMessage = (decision: 'apply_config' | 'abandon' | 'revise', detail?: string) => {
  if (decision === 'apply_config') {
    return '我已确认添加配置到系统。';
  }
  if (decision === 'abandon') {
    return '我已放弃本次元数据配置。';
  }
  const focus = detail?.trim() || '继续优化元数据配置。';
  return `我已补充配置调整要求：\n${focus}`;
};

// 切换任务折叠状态
const toggleTask = (index: number) => {
  tasks.value[index].expanded = !tasks.value[index].expanded
}

// 切换面板展开状态
const togglePanel = (panelName: string) => {
  // @ts-ignore
  panelStates[panelName] = !panelStates[panelName]
}

// 展开更多信息
const expandInfo = () => {
}

// 可选择的模型
const modelSelectData = ref<SelectData>({
  periodOptions: ['qianwen-max', 'deepseek-R1', 'deepseek-V3'],
  period: 'qianwen-max',
})

// 上传文件
const validateFilesBeforeUpload = (files: File[]) => {
  const selectedFiles = files.slice(0, MAX_FILES_PER_PICK);
  if (files.length > MAX_FILES_PER_PICK) {
    ElMessage.warning(`单次最多选择 ${MAX_FILES_PER_PICK} 个附件，已自动忽略多余文件`);
  }
  const validFiles = selectedFiles.filter(file => file.size <= MAX_UPLOAD_BYTES);
  const oversizedFiles = selectedFiles.filter(file => file.size > MAX_UPLOAD_BYTES);
  if (oversizedFiles.length) {
    ElMessage.error(`已忽略 ${oversizedFiles.length} 个超过 30MB 的附件`);
  }
  return validFiles;
};

const uploadFilesWithConcurrency = async (files: File[]) => {
  const attachments: ChatAttachment[] = [];
  for (let index = 0; index < files.length; index += UPLOAD_CONCURRENCY) {
    const batch = files.slice(index, index + UPLOAD_CONCURRENCY);
    const batchAttachments = await Promise.all(batch.map(file => DihService.uploadFile(file)));
    attachments.push(...batchAttachments);
  }
  return attachments;
};

const uploadFile = () => {
  if (isUploadingAttachment.value) {
    return;
  }
  // 创建文件输入元素
  const fileInput = document.createElement('input');
  fileInput.type = 'file';
  fileInput.multiple = true;
  fileInput.style.display = 'none';
  fileInput.onchange = async (event) => {
    const target = event.target as HTMLInputElement;
    if (target.files && target.files.length > 0) {
      const files = validateFilesBeforeUpload(Array.from(target.files));
      if (!files.length) {
        if (fileInput.parentNode) {
          document.body.removeChild(fileInput);
        }
        return;
      }
      isUploadingAttachment.value = true;
      try {
        const attachments = await uploadFilesWithConcurrency(files);
        pendingAttachments.value.push(...attachments);
        ElMessage.success(files.length > 1 ? `已添加 ${files.length} 个附件` : `已添加附件「${files[0].name}」`);
      } catch (err) {
        console.error('文件上传失败', err);
        ElMessage.error('文件上传失败，请重试');
      } finally {
        isUploadingAttachment.value = false;
        if (fileInput.parentNode) {
          document.body.removeChild(fileInput);
        }
      }
    } else if (fileInput.parentNode) {
      document.body.removeChild(fileInput);
    }
  };
  document.body.appendChild(fileInput);
  fileInput.click();
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

const infoStepsDisplayMessage = (part: ChatMessagePart, answers: InfoStepAnswer[]) => {
  const title = part.title || '需要补充信息';
  if (!answers.length) {
    return `我已补充「${title}」所需信息。`;
  }
  return `我已补充以下信息：\n${answers.map(answer => `- ${answer.title}：${answer.value}`).join('\n')}`;
};

const infoStepsRequestMessage = (part: ChatMessagePart, answers: InfoStepAnswer[]) => {
  return [
    '我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。',
    '',
    JSON.stringify({
      title: part.title || '需要补充信息',
      content: part.content || '',
      answers,
    }, null, 2),
  ].join('\n');
};

const handleInfoStepsSubmit = async (
  message: ChatMessage,
  payload: { part: ChatMessagePart; answers: InfoStepAnswer[] }
) => {
  if (!chatSessionId.value || !message.id || !payload.part.id) {
    ElMessage.warning('缺少补充信息卡片标识，无法记录提交结果');
    return;
  }

  try {
    await DihService.recordActionDecision({
      chat_id: chatSessionId.value,
      message_id: message.id,
      part_id: payload.part.id,
      decision: 'submitted',
    });
  } catch (error) {
    console.error('记录补充信息提交失败:', error);
  }
  payload.part.status = 'submitted';
  ElMessage.success('已提交补充信息');
  await nextTick();
  await sendMessage({
    content: infoStepsDisplayMessage(payload.part, payload.answers),
    requestContent: infoStepsRequestMessage(payload.part, payload.answers),
  });
};

const handleActionDecision = async (
  message: ChatMessage,
  payload: { part: ChatMessagePart; decision: 'approved' | 'rejected' | 'revise'; detail?: string }
) => {
  if (!chatSessionId.value || !message.id || !payload.part.id) {
    ElMessage.warning('缺少确认记录标识，无法记录操作结果');
    return;
  }

  try {
    await DihService.recordActionDecision({
      chat_id: chatSessionId.value,
      message_id: message.id,
      part_id: payload.part.id,
      decision: payload.decision,
    });
  } catch (error) {
    console.error('记录确认结果失败:', error);
  }
  payload.part.status = payload.decision;
  const action = confirmAction(payload.part);
  if (payload.decision === 'revise' && action === 'data_visualization.apply_config') {
    ElMessage.success('已提交配置调整要求');
    await nextTick();
    await sendMessage({
      content: dataVisualizationDecisionDisplayMessage(payload.decision, payload.detail),
      requestContent: dataVisualizationDecisionMessage(payload.decision, payload.detail),
    });
    return;
  }
  if (payload.decision === 'revise' && (
    action === 'analysis.confirm_log_aggregation'
    || action === 'analysis.confirm_sandbox_result'
    || action === 'analysis_demo.confirm_log_aggregation'
    || action === 'analysis_demo.confirm_sandbox_result'
  )) {
    ElMessage.success('已提交补充信息');
    await nextTick();
    await sendMessage({
      content: analysisDemoConfirmReviseDisplayMessage(action, payload.detail),
      requestContent: analysisDemoConfirmReviseMessage(action, payload.detail),
    });
    return;
  }
  if (payload.decision === 'revise' && (
    action === 'policy.confirm_trial'
    || action === 'policy_demo.confirm_trial'
    || action === 'policy_demo.confirm_retry_trial'
  )) {
    ElMessage.success('已提交策略更新要求');
    await nextTick();
    await sendMessage({
      content: policyConfirmReviseDisplayMessage(payload.detail),
      requestContent: policyConfirmReviseMessage(payload.detail),
    });
    return;
  }
  ElMessage.success(payload.decision === 'approved' ? '已确认执行' : '已取消操作');
  if (payload.decision === 'approved' && AUTO_CONFIRM_ACTIONS.has(action)) {
    await nextTick();
    await sendMessage({ content: autoConfirmMessage(action) });
  } else if (payload.decision === 'rejected' && AUTO_REJECT_ACTIONS.has(action)) {
    await nextTick();
    await sendMessage({ content: autoRejectMessage(action) });
  }
};

const handleAddChartLibrary = async (_message: ChatMessage, part: ChatMessagePart) => {
  const action = confirmAction(part);
  if (action !== 'data_visualization.add_chart_library') {
    ElMessage.warning('当前图表不支持加入图表库');
    return;
  }
  if (part.status === 'submitted' || part.status === 'added') {
    ElMessage.info('该图表已加入图表库');
    return;
  }
  const previousExtraData = chatSessionExtraData.value;
  const previousStatus = part.status;
  try {
    const sessionRecordId = await ensureChatSessionRecordId();
    if (!sessionRecordId) {
      ElMessage.warning('当前会话尚未创建完成，无法加入图表库');
      return;
    }
    const record = buildChartLibraryRecord(part);
    const nextExtraData = addChartRecordToExtraData(record);
    part.status = 'added';
    chatSessionExtraData.value = nextExtraData;
    await DihService.updateChatSession(sessionRecordId, { extra_data: nextExtraData });
    ElMessage.success('已加入图表库');
  } catch (error) {
    console.error('加入图表库失败:', error);
    part.status = previousStatus;
    chatSessionExtraData.value = previousExtraData;
    ElMessage.error('加入图表库失败');
  }
};

const handleAnalysisDecision = async (
  message: ChatMessage,
  payload: { part: ChatMessagePart; decision: 'dispose' | 'ignore' | 'continue'; detail?: string }
) => {
  if (!chatSessionId.value || !message.id || !payload.part.id) {
    ElMessage.warning('缺少研判选择记录标识，无法记录操作结果');
    return;
  }

  try {
    await DihService.recordActionDecision({
      chat_id: chatSessionId.value,
      message_id: message.id,
      part_id: payload.part.id,
      decision: payload.decision,
    });
  } catch (error) {
    console.error('记录研判后续选择失败:', error);
  }
  payload.part.status = payload.decision;
  const toastMap = {
    dispose: '已选择执行处置',
    ignore: '已选择忽略告警',
    continue: '已提交补充研判重点',
  };
  ElMessage.success(toastMap[payload.decision]);
  await nextTick();
  if (payload.decision === 'dispose') {
    await openDisposeAgentSession(buildDisposeAgentPrompt(payload.detail));
    return;
  }
  await sendMessage({ content: analysisDecisionMessage(payload.decision, payload.detail) });
};

const handleDataAccessDecision = async (
  message: ChatMessage,
  payload: { part: ChatMessagePart; decision: 'apply_config' | 'abandon' | 'revise'; detail?: string }
) => {
  if (!chatSessionId.value || !message.id || !payload.part.id) {
    ElMessage.warning('缺少数据接入选择记录标识，无法记录操作结果');
    return;
  }

  try {
    await DihService.recordActionDecision({
      chat_id: chatSessionId.value,
      message_id: message.id,
      part_id: payload.part.id,
      decision: payload.decision,
    });
  } catch (error) {
    console.error('记录数据接入后续选择失败:', error);
  }
  payload.part.status = payload.decision;
  const toastMap = {
    apply_config: '已选择添加配置到系统',
    abandon: '已放弃本次配置',
    revise: '已提交配置调整要求',
  };
  ElMessage.success(toastMap[payload.decision]);
  await nextTick();
  await sendMessage({
    content: dataAccessDecisionDisplayMessage(payload.decision, payload.detail),
    requestContent: dataAccessDecisionMessage(payload.decision, payload.detail),
  });
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
