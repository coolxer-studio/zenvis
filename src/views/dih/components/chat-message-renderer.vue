<template>
  <div class="rich-message">
    <template v-for="(part, index) in renderParts" :key="part.id || index">
      <div v-if="part.type === 'thinking' && !isThinkingHidden(part)" class="thinking-part">
        <div class="thinking-header" @click="toggleThinking(part)">
          <div class="thinking-title">
            <el-icon><Loading /></el-icon>
            <span>{{ part.title || '思考过程' }}</span>
            <el-tag size="small" type="info" effect="plain">{{ thinkingStatusText(part.status) }}</el-tag>
          </div>
          <div class="thinking-tools">
            <el-tooltip :content="isThinkingExpanded(part) ? '折叠' : '展开'" placement="top">
              <el-button
                class="thinking-icon-btn"
                size="small"
                :icon="isThinkingExpanded(part) ? CaretTop : CaretBottom"
                circle
                @click.stop="toggleThinking(part)"
              />
            </el-tooltip>
            <el-tooltip content="关闭思考过程" placement="top">
              <el-button
                class="thinking-icon-btn"
                size="small"
                :icon="Close"
                circle
                @click.stop="hideThinking(part)"
              />
            </el-tooltip>
          </div>
        </div>
        <div v-if="isThinkingExpanded(part)" class="thinking-content">
          {{ part.content }}
        </div>
      </div>

      <div
        v-else-if="part.type === 'markdown'"
        class="message-content markdown-body"
        v-html="parseMarkdown(part.content || '')"
      ></div>

      <div v-else-if="part.type === 'code'" class="code-part">
        <div class="code-header">
          <span class="code-language">{{ part.language || 'plaintext' }}</span>
          <el-tooltip content="复制代码" placement="top">
            <el-button
              class="code-copy-btn"
              size="small"
              :icon="CopyDocument"
              circle
              @click="copyPart(part.content || '')"
            />
          </el-tooltip>
        </div>
        <pre class="code-content"><code>{{ part.content }}</code></pre>
      </div>

      <div v-else-if="part.type === 'config'" class="config-part">
        <div class="config-card-header">
          <div class="config-card-title">
            <el-icon><Document /></el-icon>
            <span class="config-card-name">{{ part.title || '配置文件' }}</span>
            <el-tag size="small" effect="plain">{{ configKindText(part) }}</el-tag>
          </div>
          <el-tooltip content="复制配置" placement="top">
            <el-button
              class="config-copy-btn"
              size="small"
              :icon="CopyDocument"
              circle
              @click="copyPart(part.content || '')"
            />
          </el-tooltip>
        </div>
        <div class="config-card-meta">
          <span>默认文件：{{ defaultConfigFileName(part) }}</span>
        </div>
        <pre class="config-card-content"><code>{{ part.content }}</code></pre>
      </div>

      <div v-else-if="part.type === 'notice'" class="notice-part" :class="noticeClass(part)">
        <div class="notice-title">
          <el-icon><component :is="noticeIcon(part)" /></el-icon>
          <span>{{ part.title || '提示' }}</span>
        </div>
        <div class="notice-content">{{ part.content }}</div>
      </div>

      <div v-else-if="part.type === 'confirm'" class="confirm-part">
        <div class="confirm-title">
          <el-icon><QuestionFilled /></el-icon>
          <span>{{ part.title || '需要确认' }}</span>
          <el-tag size="small" :type="confirmTagType(part.status)" effect="plain">
            {{ confirmStatusText(part.status) }}
          </el-tag>
        </div>
        <div class="confirm-content">{{ part.content }}</div>
        <div class="confirm-actions" v-if="!part.status || part.status === 'pending'">
          <el-button size="small" type="primary" @click="requestDecision(part, 'approved')">
            确认执行
          </el-button>
          <el-button size="small" @click="requestDecision(part, 'rejected')">取消</el-button>
        </div>
      </div>

      <div v-else-if="part.type === 'analysis-decision'" class="analysis-decision-part">
        <div class="analysis-decision-title">
          <el-icon><QuestionFilled /></el-icon>
          <span>{{ part.title || '研判完成，请选择后续处理' }}</span>
          <el-tag size="small" :type="analysisDecisionTagType(part.status)" effect="plain">
            {{ analysisDecisionStatusText(part.status) }}
          </el-tag>
        </div>
        <div class="analysis-decision-content">
          {{ part.content || '请选择下一步处理方式。' }}
        </div>
        <div v-if="!part.status || part.status === 'pending'" class="analysis-decision-actions">
          <el-button size="small" type="primary" @click="requestAnalysisDecision(part, 'dispose')">
            执行处置
          </el-button>
          <el-button size="small" @click="requestAnalysisDecision(part, 'ignore')">
            忽略告警
          </el-button>
          <el-button size="small" type="warning" plain @click="requestAnalysisDecision(part, 'continue')">
            补充信息继续研判
          </el-button>
        </div>
        <div v-if="isContinueInputVisible(part) && (!part.status || part.status === 'pending')" class="analysis-continue-box">
          <el-input
            v-model="analysisDecisionInputs[partKey(part)]"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="输入需要继续研判的重点，例如：补查近 24 小时同源 IP 登录行为、重点关注横向移动证据"
          />
          <div class="analysis-continue-actions">
            <el-button size="small" type="primary" @click="submitAnalysisContinue(part)">继续研判</el-button>
            <el-button size="small" @click="hideContinueInput(part)">取消</el-button>
          </div>
        </div>
      </div>

      <div v-else-if="part.type === 'data-access-decision'" class="data-access-decision-part">
        <div class="data-access-decision-title">
          <el-icon><QuestionFilled /></el-icon>
          <span>{{ part.title || '元数据配置已生成，请选择后续处理' }}</span>
          <el-tag size="small" :type="dataAccessDecisionTagType(part.status)" effect="plain">
            {{ dataAccessDecisionStatusText(part.status) }}
          </el-tag>
        </div>
        <div class="data-access-decision-content">
          {{ part.content || '可以添加配置到系统、放弃本次配置，或补充调整要求继续更新配置。' }}
        </div>
        <div v-if="!part.status || part.status === 'pending'" class="data-access-decision-actions">
          <el-button size="small" type="primary" @click="requestDataAccessDecision(part, 'apply_config')">
            添加配置到系统
          </el-button>
          <el-button size="small" @click="requestDataAccessDecision(part, 'abandon')">
            放弃本次配置
          </el-button>
          <el-button size="small" type="warning" plain @click="requestDataAccessDecision(part, 'revise')">
            补充信息继续更新配置
          </el-button>
        </div>
        <div v-if="isDataAccessReviseInputVisible(part) && (!part.status || part.status === 'pending')" class="data-access-revise-box">
          <el-input
            v-model="dataAccessDecisionInputs[partKey(part)]"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="输入需要调整的内容，例如：增加 server_time 字段、修改实体中文名、补充 IP 字段展示类型"
          />
          <div class="data-access-revise-actions">
            <el-button size="small" type="primary" @click="submitDataAccessRevise(part)">继续更新配置</el-button>
            <el-button size="small" @click="hideDataAccessReviseInput(part)">取消</el-button>
          </div>
        </div>
      </div>

      <div v-else-if="part.type === 'chart'" class="chart-part">
        <el-icon><DataAnalysis /></el-icon>
        <span>图表数据已加载，请在右侧面板查看可视化结果。</span>
      </div>

      <div v-else class="message-content markdown-body" v-html="parseMarkdown(part.content || '')"></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue';
import { ElMessageBox } from 'element-plus';
import {
  CaretBottom,
  CaretTop,
  CircleCheckFilled,
  Close,
  CopyDocument,
  DataAnalysis,
  Document,
  InfoFilled,
  Loading,
  QuestionFilled,
  WarningFilled,
} from '@element-plus/icons-vue';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import type { ChatMessage, ChatMessagePart } from '@/types/type-dih';

marked.setOptions({
  gfm: true,
  breaks: true,
});

const props = defineProps<{
  message: ChatMessage;
}>();

const emit = defineEmits<{
  (e: 'copyCode', content: string): void;
  (e: 'decideAction', payload: { part: ChatMessagePart; decision: 'approved' | 'rejected' }): void;
  (e: 'chooseAnalysisDecision', payload: { part: ChatMessagePart; decision: 'dispose' | 'ignore' | 'continue'; detail?: string }): void;
  (e: 'chooseDataAccessDecision', payload: { part: ChatMessagePart; decision: 'apply_config' | 'abandon' | 'revise'; detail?: string }): void;
}>();

const expandedThinking = reactive<Record<string, boolean>>({});
const hiddenThinking = reactive<Record<string, boolean>>({});
const continueInputVisible = reactive<Record<string, boolean>>({});
const analysisDecisionInputs = reactive<Record<string, string>>({});
const dataAccessReviseInputVisible = reactive<Record<string, boolean>>({});
const dataAccessDecisionInputs = reactive<Record<string, string>>({});

const renderParts = computed<ChatMessagePart[]>(() => {
  if (props.message.parts && props.message.parts.length > 0) {
    return props.message.parts;
  }
  return parseFallbackThinkingParts(props.message.content);
});

const partKey = (part: ChatMessagePart) => part.id || `${part.type}-${part.content || ''}`;

const parseFallbackThinkingParts = (content: string): ChatMessagePart[] => {
  const thinkStart = content.indexOf('<think>');
  if (thinkStart === -1) {
    return [
      {
        id: `${props.message.id || 'message'}-content`,
        type: 'markdown',
        content,
      },
    ];
  }

  const parts: ChatMessagePart[] = [];
  const beforeThinking = content.slice(0, thinkStart);
  if (beforeThinking.trim()) {
    parts.push({
      id: `${props.message.id || 'message'}-before-thinking`,
      type: 'markdown',
      content: beforeThinking,
    });
  }

  const thinkEnd = content.indexOf('</think>', thinkStart);
  if (thinkEnd === -1) {
    parts.push({
      id: `${props.message.id || 'message'}-thinking-running`,
      type: 'thinking',
      title: '思考过程',
      content: content.slice(thinkStart + '<think>'.length).trim(),
      status: 'running',
    });
    return parts;
  }

  parts.push({
    id: `${props.message.id || 'message'}-thinking`,
    type: 'thinking',
    title: '思考过程',
    content: content.slice(thinkStart + '<think>'.length, thinkEnd).trim(),
    status: 'completed',
  });

  const afterThinking = content.slice(thinkEnd + '</think>'.length);
  if (afterThinking.trim()) {
    parts.push({
      id: `${props.message.id || 'message'}-after-thinking`,
      type: 'markdown',
      content: afterThinking,
    });
  }

  return parts.length > 0 ? parts : [
    {
      id: `${props.message.id || 'message'}-content`,
      type: 'markdown',
      content,
    },
  ];
};

const parseMarkdown = (content: string) => {
  return DOMPurify.sanitize(marked.parse(content) as string);
};

const copyPart = (content: string) => {
  emit('copyCode', content);
};

const metadataText = (part: ChatMessagePart, key: string) => {
  const value = part.metadata?.[key];
  return typeof value === 'string' ? value : '';
};

const configKindText = (part: ChatMessagePart) => {
  const kind = metadataText(part, 'configKind');
  if (kind === 'low-code-page') return '低代码页面';
  if (kind === 'low-code-app') return '低代码应用';
  if (kind === 'html-page') return '静态 HTML';
  if (kind === 'continuous-analysis-task') return '持续分析任务';
  if (kind === 'meta-config') return '元数据配置';
  if (kind === 'disposal-strategy') return '处置策略';
  if (kind === 'collection-policy') return '采集策略';
  if (kind === 'tagging-policy') return '标记评分策略';
  if (kind === 'disposal-policy') return '处置策略';
  if (kind === 'report-document') return '报表文档';
  return kind || '配置';
};

const defaultConfigFileName = (part: ChatMessagePart) => {
  return metadataText(part, 'defaultFileName') || '-';
};

const requestDecision = async (part: ChatMessagePart, decision: 'approved' | 'rejected') => {
  const verb = decision === 'approved' ? '执行' : '取消';
  try {
    await ElMessageBox.confirm(`确认${verb}「${part.title || '此操作'}」？`, '操作确认', {
      confirmButtonText: '确定',
      cancelButtonText: '返回',
      type: decision === 'approved' ? 'warning' : 'info',
    });
    emit('decideAction', { part, decision });
  } catch {
    // 用户关闭确认框
  }
};

const requestAnalysisDecision = async (part: ChatMessagePart, decision: 'dispose' | 'ignore' | 'continue') => {
  if (decision === 'continue') {
    continueInputVisible[partKey(part)] = true;
    return;
  }

  const label = decision === 'dispose' ? '执行处置' : '忽略告警';
  try {
    await ElMessageBox.confirm(`确认${label}？`, '后续处理', {
      confirmButtonText: '确定',
      cancelButtonText: '返回',
      type: decision === 'dispose' ? 'warning' : 'info',
    });
    emit('chooseAnalysisDecision', { part, decision });
  } catch {
    // 用户关闭确认框
  }
};

const isContinueInputVisible = (part: ChatMessagePart) => {
  return continueInputVisible[partKey(part)] === true;
};

const hideContinueInput = (part: ChatMessagePart) => {
  continueInputVisible[partKey(part)] = false;
};

const submitAnalysisContinue = (part: ChatMessagePart) => {
  emit('chooseAnalysisDecision', {
    part,
    decision: 'continue',
    detail: (analysisDecisionInputs[partKey(part)] || '').trim(),
  });
};

const requestDataAccessDecision = async (part: ChatMessagePart, decision: 'apply_config' | 'abandon' | 'revise') => {
  if (decision === 'revise') {
    dataAccessReviseInputVisible[partKey(part)] = true;
    return;
  }

  const label = decision === 'apply_config' ? '添加配置到系统' : '放弃本次配置';
  try {
    await ElMessageBox.confirm(`确认${label}？`, '后续处理', {
      confirmButtonText: '确定',
      cancelButtonText: '返回',
      type: decision === 'apply_config' ? 'warning' : 'info',
    });
    emit('chooseDataAccessDecision', { part, decision });
  } catch {
    // 用户关闭确认框
  }
};

const isDataAccessReviseInputVisible = (part: ChatMessagePart) => {
  return dataAccessReviseInputVisible[partKey(part)] === true;
};

const hideDataAccessReviseInput = (part: ChatMessagePart) => {
  dataAccessReviseInputVisible[partKey(part)] = false;
};

const submitDataAccessRevise = (part: ChatMessagePart) => {
  emit('chooseDataAccessDecision', {
    part,
    decision: 'revise',
    detail: (dataAccessDecisionInputs[partKey(part)] || '').trim(),
  });
};

const isThinkingExpanded = (part: ChatMessagePart) => {
  const key = partKey(part);
  if (expandedThinking[key] === undefined) {
    return part.status === 'running';
  }
  return expandedThinking[key] === true;
};

const toggleThinking = (part: ChatMessagePart) => {
  const key = partKey(part);
  expandedThinking[key] = !expandedThinking[key];
};

const hideThinking = (part: ChatMessagePart) => {
  hiddenThinking[partKey(part)] = true;
};

const isThinkingHidden = (part: ChatMessagePart) => {
  return hiddenThinking[partKey(part)] === true;
};

const thinkingStatusText = (status?: string) => {
  if (status === 'running') return '思考中';
  return '已完成';
};

const noticeClass = (part: ChatMessagePart) => {
  const level = part.level || 'info';
  return [`notice-${level}`];
};

const noticeIcon = (part: ChatMessagePart) => {
  if (part.level === 'warning' || part.level === 'error') {
    return WarningFilled;
  }
  if (part.level === 'success') {
    return CircleCheckFilled;
  }
  return InfoFilled;
};

const confirmTagType = (status?: string) => {
  if (status === 'approved') return 'success';
  if (status === 'rejected') return 'info';
  return 'warning';
};

const confirmStatusText = (status?: string) => {
  if (status === 'approved') return '已确认';
  if (status === 'rejected') return '已取消';
  return '待确认';
};

const analysisDecisionTagType = (status?: string) => {
  if (status === 'dispose') return 'success';
  if (status === 'ignore') return 'info';
  if (status === 'continue') return 'warning';
  return 'warning';
};

const analysisDecisionStatusText = (status?: string) => {
  if (status === 'dispose') return '已选择处置';
  if (status === 'ignore') return '已忽略';
  if (status === 'continue') return '继续研判';
  return '待选择';
};

const dataAccessDecisionTagType = (status?: string) => {
  if (status === 'apply_config') return 'success';
  if (status === 'abandon') return 'info';
  if (status === 'revise') return 'warning';
  return 'warning';
};

const dataAccessDecisionStatusText = (status?: string) => {
  if (status === 'apply_config') return '已选择添加';
  if (status === 'abandon') return '已放弃';
  if (status === 'revise') return '继续更新';
  return '待选择';
};
</script>

<style scoped>
.rich-message {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.message-content {
  max-width: 100%;
  min-width: 0;
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.message-content :deep(p),
.message-content :deep(li),
.message-content :deep(blockquote),
.message-content :deep(a),
.message-content :deep(span),
.message-content :deep(strong),
.message-content :deep(em),
.message-content :deep(code) {
  max-width: 100%;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.message-content :deep(p) {
  white-space: pre-wrap;
}

.message-content :deep(pre) {
  max-width: 100%;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  overflow-x: auto;
}

.message-content :deep(pre code) {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.message-content :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
}

.thinking-part {
  max-width: 100%;
  min-width: 0;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #f7f8fa;
  overflow: hidden;
}

.thinking-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  cursor: pointer;
  color: #606266;
}

.thinking-title,
.thinking-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.thinking-title {
  font-size: 14px;
  font-weight: 600;
}

.thinking-icon-btn {
  width: 24px;
  height: 24px;
  background: transparent;
}

.thinking-content {
  padding: 0 12px 12px;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.code-part {
  max-width: 100%;
  min-width: 0;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  background: #1f2329;
}

.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  background: #2b3037;
  color: #cfd3dc;
}

.code-language {
  font-size: 12px;
  line-height: 1;
}

.code-copy-btn {
  color: #cfd3dc;
  background: transparent;
  border-color: #4c5563;
}

.code-content {
  margin: 0;
  padding: 12px;
  overflow-x: auto;
  color: #f5f7fa;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.code-content code {
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.config-part {
  max-width: 100%;
  min-width: 0;
  border: 1px solid #b3d8ff;
  border-radius: 8px;
  overflow: hidden;
  background: #f8fbff;
}

.config-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  background: #ecf5ff;
  border-bottom: 1px solid #d9ecff;
}

.config-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.config-card-name {
  min-width: 0;
  overflow-wrap: anywhere;
}

.config-copy-btn {
  flex: 0 0 auto;
}

.config-card-meta {
  padding: 8px 12px 0;
  color: #606266;
  font-size: 12px;
}

.config-card-content {
  margin: 0;
  padding: 10px 12px 12px;
  overflow-x: auto;
  color: #1f2329;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.config-card-content code {
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.notice-part,
.confirm-part,
.analysis-decision-part,
.data-access-decision-part,
.chart-part {
  max-width: 100%;
  min-width: 0;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #ffffff;
  padding: 12px;
  overflow: hidden;
}

.notice-title,
.confirm-title,
.analysis-decision-title,
.data-access-decision-title,
.chart-part {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.notice-content,
.confirm-content,
.analysis-decision-content,
.data-access-decision-content {
  margin-top: 8px;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.notice-info {
  border-color: #b3d8ff;
  background: #ecf5ff;
}

.notice-warning {
  border-color: #f5dab1;
  background: #fdf6ec;
}

.notice-error {
  border-color: #fab6b6;
  background: #fef0f0;
}

.notice-success {
  border-color: #b3e19d;
  background: #f0f9eb;
}

.confirm-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.analysis-decision-part {
  border-color: #d9ecff;
  background: #f8fbff;
}

.data-access-decision-part {
  border-color: #d9ecff;
  background: #f8fbff;
}

.analysis-decision-actions,
.data-access-decision-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.analysis-continue-box,
.data-access-revise-box {
  margin-top: 12px;
}

.analysis-continue-actions,
.data-access-revise-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.chart-part {
  color: #409eff;
}
</style>
