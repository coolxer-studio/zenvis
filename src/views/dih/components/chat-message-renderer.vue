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

      <div v-else-if="part.type === 'prompt-suggestions'" class="prompt-suggestions-part">
        <div v-if="part.title" class="prompt-suggestions-title">{{ part.title }}</div>
        <div class="prompt-suggestion-list">
          <el-button
            v-for="(example, exampleIndex) in promptSuggestionExamples(part)"
            :key="`${partKey(part)}-${exampleIndex}`"
            class="prompt-suggestion-bubble"
            size="small"
            round
            @click="selectPromptSuggestion(example.prompt)"
          >
            {{ example.label }}
          </el-button>
        </div>
      </div>

      <div v-else-if="part.type === 'code'" class="code-part">
        <div class="code-header">
          <div class="code-title">
            <span class="code-language">{{ part.title || part.language || 'plaintext' }}</span>
          </div>
          <div class="code-tools">
            <el-tooltip content="复制代码" placement="top">
              <el-button
                class="code-copy-btn"
                size="small"
                :icon="CopyDocument"
                circle
                @click="copyPart(part.content || '')"
              />
            </el-tooltip>
            <el-tooltip :content="isCodeExpanded(part) ? '折叠' : '展开'" placement="top">
              <el-button
                class="code-copy-btn"
                size="small"
                :icon="isCodeExpanded(part) ? CaretTop : CaretBottom"
                circle
                @click="toggleCode(part)"
              />
            </el-tooltip>
          </div>
        </div>
        <pre v-if="isCodeExpanded(part)" class="code-content"><code>{{ part.content }}</code></pre>
      </div>

      <div v-else-if="part.type === 'report-document'" class="config-part report-document-part">
        <div class="config-card-header">
          <div class="config-card-title">
            <el-icon><Document /></el-icon>
            <span class="config-card-name">{{ part.title || metadataText(part, 'title') || '报表文档' }}</span>
            <el-tag size="small" effect="plain">{{ reportDocumentFormatText(part) }}</el-tag>
          </div>
          <div class="config-card-tools">
            <el-tooltip content="复制文档" placement="top">
              <el-button
                class="config-copy-btn"
                size="small"
                :icon="CopyDocument"
                circle
                @click="copyPart(part.content || '')"
              />
            </el-tooltip>
            <el-tooltip :content="isZenvisCardExpanded(part) ? '折叠' : '展开'" placement="top">
              <el-button
                class="card-toggle-btn"
                size="small"
                :icon="isZenvisCardExpanded(part) ? CaretTop : CaretBottom"
                circle
                @click="toggleZenvisCard(part)"
              />
            </el-tooltip>
          </div>
        </div>
        <template v-if="isZenvisCardExpanded(part)">
          <div class="config-card-meta">
            <span>已同步到右侧报表编辑器</span>
          </div>
          <iframe
            v-if="isReportDocumentHtml(part)"
            class="config-html-preview"
            :srcdoc="part.content || ''"
            sandbox="allow-same-origin"
          ></iframe>
          <div
            v-else
            class="message-content markdown-body report-document-preview"
            v-html="parseMarkdown(part.content || '')"
          ></div>
        </template>
      </div>

      <div v-else-if="part.type === 'config'" class="config-part">
        <div class="config-card-header">
          <div class="config-card-title">
            <el-icon><Document /></el-icon>
            <span class="config-card-name">{{ part.title || '配置文件' }}</span>
            <el-tag size="small" effect="plain">{{ configKindText(part) }}</el-tag>
          </div>
          <div class="config-card-tools">
            <el-tooltip v-if="isConfigPreviewable(part)" :content="isConfigPreviewMode(part) ? '查看源码' : '预览最终效果'" placement="top">
              <el-button
                class="config-copy-btn"
                size="small"
                :icon="configPreviewButtonIcon(part)"
                circle
                @click="toggleConfigPreview(part)"
              />
            </el-tooltip>
            <el-tooltip content="复制配置" placement="top">
              <el-button
                class="config-copy-btn"
                size="small"
                :icon="CopyDocument"
                circle
                @click="copyPart(part.content || '')"
              />
            </el-tooltip>
            <el-tooltip :content="isZenvisCardExpanded(part) ? '折叠' : '展开'" placement="top">
              <el-button
                class="card-toggle-btn"
                size="small"
                :icon="isZenvisCardExpanded(part) ? CaretTop : CaretBottom"
                circle
                @click="toggleZenvisCard(part)"
              />
            </el-tooltip>
          </div>
        </div>
        <template v-if="isZenvisCardExpanded(part)">
          <div class="config-card-meta">
            <span>默认文件：{{ defaultConfigFileName(part) }}</span>
          </div>
          <div v-if="isConfigPreviewMode(part)" class="config-preview">
            <iframe
              v-if="isHtmlConfig(part)"
              class="config-html-preview"
              :srcdoc="part.content || ''"
              sandbox="allow-scripts allow-forms allow-same-origin"
            ></iframe>
            <div v-else class="config-low-code-preview" v-html="lowCodePreviewHtml(part)"></div>
          </div>
          <pre v-else class="config-card-content"><code>{{ part.content }}</code></pre>
        </template>
      </div>

      <div v-else-if="part.type === 'notice'" class="notice-part" :class="noticeClass(part)">
        <div class="notice-title">
          <el-icon><component :is="noticeIcon(part)" /></el-icon>
          <span class="card-title-text">{{ part.title || '提示' }}</span>
          <el-tooltip :content="isZenvisCardExpanded(part) ? '折叠' : '展开'" placement="top">
            <el-button
              class="card-toggle-btn"
              size="small"
              :icon="isZenvisCardExpanded(part) ? CaretTop : CaretBottom"
              circle
              @click="toggleZenvisCard(part)"
            />
          </el-tooltip>
        </div>
        <div v-if="isZenvisCardExpanded(part)" class="notice-content">{{ part.content }}</div>
      </div>

      <div v-else-if="part.type === 'confirm'" class="confirm-part">
        <div class="confirm-title">
          <el-icon><QuestionFilled /></el-icon>
          <span class="card-title-text">{{ part.title || '需要确认' }}</span>
          <el-tag size="small" :type="confirmTagType(part.status)" effect="plain">
            {{ confirmStatusText(part.status) }}
          </el-tag>
          <el-tooltip :content="isZenvisCardExpanded(part) ? '折叠' : '展开'" placement="top">
            <el-button
              class="card-toggle-btn"
              size="small"
              :icon="isZenvisCardExpanded(part) ? CaretTop : CaretBottom"
              circle
              @click="toggleZenvisCard(part)"
            />
          </el-tooltip>
        </div>
        <template v-if="isZenvisCardExpanded(part)">
          <div class="confirm-content">{{ part.content }}</div>
          <div class="confirm-actions" v-if="!part.status || part.status === 'pending'">
            <el-button size="small" type="primary" @click="requestDecision(part, 'approved')">
              确认执行
            </el-button>
            <el-button size="small" @click="requestDecision(part, 'rejected')">取消</el-button>
            <el-button
              v-if="supportsConfirmRevise(part)"
              size="small"
              type="warning"
              plain
              @click="showConfirmRevise(part)"
            >
              {{ confirmReviseLabel(part) }}
            </el-button>
          </div>
          <div v-if="isConfirmReviseInputVisible(part) && (!part.status || part.status === 'pending')" class="confirm-revise-box">
            <el-input
              v-model="confirmDecisionInputs[partKey(part)]"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              :placeholder="confirmRevisePlaceholder(part)"
            />
            <div class="confirm-revise-actions">
              <el-button size="small" type="primary" @click="submitConfirmRevise(part)">继续更新</el-button>
              <el-button size="small" @click="hideConfirmRevise(part)">取消</el-button>
            </div>
          </div>
        </template>
      </div>

      <div v-else-if="part.type === 'info-steps'" class="info-steps-part">
        <div class="info-steps-title">
          <el-icon><InfoFilled /></el-icon>
          <span class="card-title-text">{{ part.title || '需要补充信息' }}</span>
          <el-tag size="small" :type="infoStepsTagType(part.status)" effect="plain">
            {{ infoStepsStatusText(part.status) }}
          </el-tag>
          <el-tooltip :content="isZenvisCardExpanded(part) ? '折叠' : '展开'" placement="top">
            <el-button
              class="card-toggle-btn"
              size="small"
              :icon="isZenvisCardExpanded(part) ? CaretTop : CaretBottom"
              circle
              @click="toggleZenvisCard(part)"
            />
          </el-tooltip>
        </div>
        <template v-if="isZenvisCardExpanded(part)">
          <div v-if="part.content" class="info-steps-content">{{ part.content }}</div>
          <div class="info-steps-list">
            <div
              v-for="(step, stepIndex) in infoSteps(part)"
              :key="step.id || stepIndex"
              class="info-step-item"
            >
              <div class="info-step-marker">{{ stepIndex + 1 }}</div>
              <div class="info-step-body">
                <div class="info-step-heading">
                  <span class="info-step-title">{{ step.title || `补充项 ${stepIndex + 1}` }}</span>
                  <el-tag v-if="step.required" size="small" type="danger" effect="plain">必填</el-tag>
                </div>
                <div v-if="step.description" class="info-step-description">{{ step.description }}</div>
                <div class="info-step-suggestions">
                  <el-button
                    v-for="(suggestion, suggestionIndex) in stepSuggestions(step)"
                    :key="suggestionIndex"
                    class="info-step-suggestion"
                    size="small"
                    :type="isSuggestionSelected(part, step, suggestion) ? 'primary' : 'default'"
                    plain
                    :disabled="part.status === 'submitted'"
                    @click="selectInfoStepSuggestion(part, step, suggestion)"
                  >
                    {{ suggestion.label }}
                  </el-button>
                </div>
                <el-input
                  v-model="infoStepCustomInputs[infoStepKey(part, step)]"
                  class="info-step-input"
                  type="textarea"
                  :rows="2"
                  maxlength="1000"
                  show-word-limit
                  :disabled="part.status === 'submitted'"
                  :placeholder="step.placeholder || '也可以输入自定义内容'"
                />
              </div>
            </div>
          </div>
          <div v-if="!part.status || part.status === 'pending'" class="info-steps-actions">
            <el-button size="small" type="primary" @click="submitInfoSteps(part)">
              {{ infoStepsSubmitLabel(part) }}
            </el-button>
          </div>
        </template>
      </div>

      <div v-else-if="part.type === 'analysis-decision'" class="analysis-decision-part">
        <div class="analysis-decision-title">
          <el-icon><QuestionFilled /></el-icon>
          <span class="card-title-text">{{ part.title || '研判完成，请选择后续处理' }}</span>
          <el-tag size="small" :type="analysisDecisionTagType(part.status)" effect="plain">
            {{ analysisDecisionStatusText(part.status) }}
          </el-tag>
          <el-tooltip :content="isZenvisCardExpanded(part) ? '折叠' : '展开'" placement="top">
            <el-button
              class="card-toggle-btn"
              size="small"
              :icon="isZenvisCardExpanded(part) ? CaretTop : CaretBottom"
              circle
              @click="toggleZenvisCard(part)"
            />
          </el-tooltip>
        </div>
        <template v-if="isZenvisCardExpanded(part)">
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
        </template>
      </div>

      <div v-else-if="part.type === 'data-access-decision'" class="data-access-decision-part">
        <div class="data-access-decision-title">
          <el-icon><QuestionFilled /></el-icon>
          <span class="card-title-text">{{ part.title || '元数据配置已生成，请选择后续处理' }}</span>
          <el-tag size="small" :type="dataAccessDecisionTagType(part.status)" effect="plain">
            {{ dataAccessDecisionStatusText(part.status) }}
          </el-tag>
          <el-tooltip :content="isZenvisCardExpanded(part) ? '折叠' : '展开'" placement="top">
            <el-button
              class="card-toggle-btn"
              size="small"
              :icon="isZenvisCardExpanded(part) ? CaretTop : CaretBottom"
              circle
              @click="toggleZenvisCard(part)"
            />
          </el-tooltip>
        </div>
        <template v-if="isZenvisCardExpanded(part)">
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
        </template>
      </div>

      <div v-else-if="part.type === 'visualization-chart-preview'" class="visualization-chart-preview-part">
        <div class="visualization-chart-preview-header">
          <div class="visualization-chart-preview-title">
            <el-icon><DataAnalysis /></el-icon>
            <span class="card-title-text">{{ part.title || '临时图表预览' }}</span>
            <el-tag size="small" effect="plain">{{ metadataText(part, 'chartType') || 'chart' }}</el-tag>
          </div>
          <div class="visualization-chart-preview-tools">
            <el-tooltip :content="isChartLibraryAdded(part) ? '已加入图表库' : '加入图表库'" placement="top">
              <el-button
                class="config-copy-btn"
                size="small"
                :icon="isChartLibraryAdded(part) ? CircleCheckFilled : Plus"
                circle
                :disabled="isChartLibraryAdded(part) || !chartLibraryAction(part)"
                @click="requestAddChartLibrary(part)"
              />
            </el-tooltip>
            <el-tooltip content="复制 amis 配置" placement="top">
              <el-button
                class="config-copy-btn"
                size="small"
                :icon="CopyDocument"
                circle
                @click="copyPart(chartPreviewConfigText(part))"
              />
            </el-tooltip>
          </div>
        </div>
        <div v-if="part.content" class="visualization-chart-preview-desc">{{ part.content }}</div>
        <div :ref="(el) => setChartPreviewRef(part, el)" class="visualization-chart-preview-canvas"></div>
      </div>

      <div v-else-if="isDataVisualizationRecord(part)" class="notice-part notice-info">
        <div class="notice-title">
          <el-icon><DataAnalysis /></el-icon>
          <span class="card-title-text">{{ dataVisualizationRecordTitle(part) }}</span>
          <el-tooltip :content="isZenvisCardExpanded(part) ? '折叠' : '展开'" placement="top">
            <el-button
              class="card-toggle-btn"
              size="small"
              :icon="isZenvisCardExpanded(part) ? CaretTop : CaretBottom"
              circle
              @click="toggleZenvisCard(part)"
            />
          </el-tooltip>
        </div>
        <div v-if="isZenvisCardExpanded(part)" class="notice-content">
          {{ part.content || metadataText(part, 'description') || '已记录到右侧数据可视化面板。' }}
        </div>
      </div>

      <div v-else-if="part.type === 'analysis-record'" class="notice-part notice-info">
        <div class="notice-title">
          <el-icon><DataAnalysis /></el-icon>
          <span class="card-title-text">{{ analysisRecordTitle(part) }}</span>
          <el-tag size="small" :type="analysisRecordTagType(part)" effect="plain">
            {{ analysisRecordStatusText(part) }}
          </el-tag>
          <el-tooltip :content="isZenvisCardExpanded(part) ? '折叠' : '展开'" placement="top">
            <el-button
              class="card-toggle-btn"
              size="small"
              :icon="isZenvisCardExpanded(part) ? CaretTop : CaretBottom"
              circle
              @click="toggleZenvisCard(part)"
            />
          </el-tooltip>
        </div>
        <div v-if="isZenvisCardExpanded(part)" class="notice-content">
          {{ part.content || metadataText(part, 'description') || '研判阶段记录已同步到右侧面板。' }}
        </div>
      </div>

      <div v-else-if="part.type === 'policy-record'" class="notice-part notice-info">
        <div class="notice-title">
          <el-icon><DataAnalysis /></el-icon>
          <span class="card-title-text">{{ policyRecordTitle(part) }}</span>
          <el-tag size="small" :type="policyRecordTagType(part)" effect="plain">
            {{ policyRecordStatusText(part) }}
          </el-tag>
          <el-tooltip :content="isZenvisCardExpanded(part) ? '折叠' : '展开'" placement="top">
            <el-button
              class="card-toggle-btn"
              size="small"
              :icon="isZenvisCardExpanded(part) ? CaretTop : CaretBottom"
              circle
              @click="toggleZenvisCard(part)"
            />
          </el-tooltip>
        </div>
        <div v-if="isZenvisCardExpanded(part)" class="notice-content">
          {{ part.content || metadataText(part, 'changeDescription') || '策略记录已同步到右侧面板。' }}
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
import { computed, nextTick, onBeforeUnmount, reactive, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
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
  Plus,
  QuestionFilled,
  View,
  WarningFilled,
} from '@element-plus/icons-vue';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import * as echarts from 'echarts';
import type { ChatMessage, ChatMessagePart } from '@/types/type-dih';

type InfoStepSuggestion = {
  label: string;
  value: string;
  description?: string;
};

type InfoStepItem = {
  id: string;
  title: string;
  description?: string;
  required?: boolean;
  suggestions?: Array<string | InfoStepSuggestion | Record<string, unknown>>;
  placeholder?: string;
};

type InfoStepAnswer = {
  id: string;
  title: string;
  value: string;
  source: 'suggestion' | 'custom';
};

type PromptSuggestionExample = {
  label: string;
  prompt: string;
};

marked.setOptions({
  gfm: true,
  breaks: true,
});

const props = defineProps<{
  message: ChatMessage;
}>();

const emit = defineEmits<{
  (e: 'copyCode', content: string): void;
  (e: 'decideAction', payload: { part: ChatMessagePart; decision: 'approved' | 'rejected' | 'revise'; detail?: string }): void;
  (e: 'submitInfoSteps', payload: { part: ChatMessagePart; answers: InfoStepAnswer[] }): void;
  (e: 'addChartLibrary', part: ChatMessagePart): void;
  (e: 'chooseAnalysisDecision', payload: { part: ChatMessagePart; decision: 'dispose' | 'ignore' | 'continue'; detail?: string }): void;
  (e: 'chooseDataAccessDecision', payload: { part: ChatMessagePart; decision: 'apply_config' | 'abandon' | 'revise'; detail?: string }): void;
  (e: 'selectPromptSuggestion', prompt: string): void;
}>();

const expandedThinking = reactive<Record<string, boolean>>({});
const hiddenThinking = reactive<Record<string, boolean>>({});
const expandedCode = reactive<Record<string, boolean>>({});
const expandedZenvisCards = reactive<Record<string, boolean>>({});
const configPreviewModes = reactive<Record<string, boolean>>({});
const infoStepSelectedValues = reactive<Record<string, string>>({});
const infoStepCustomInputs = reactive<Record<string, string>>({});
const continueInputVisible = reactive<Record<string, boolean>>({});
const analysisDecisionInputs = reactive<Record<string, string>>({});
const confirmReviseInputVisible = reactive<Record<string, boolean>>({});
const confirmDecisionInputs = reactive<Record<string, string>>({});
const dataAccessReviseInputVisible = reactive<Record<string, boolean>>({});
const dataAccessDecisionInputs = reactive<Record<string, string>>({});
const chartPreviewEls = new Map<string, HTMLElement>();
const chartPreviewInstances = new Map<string, ReturnType<typeof echarts.init>>();

const renderParts = computed<ChatMessagePart[]>(() => {
  if (props.message.parts && props.message.parts.length > 0) {
    return props.message.parts;
  }
  return parseFallbackThinkingParts(props.message.content);
});

const partKey = (part: ChatMessagePart) => part.id || `${part.type}-${part.content || ''}`;

const setChartPreviewRef = (part: ChatMessagePart, el: unknown) => {
  if (el instanceof HTMLElement) {
    chartPreviewEls.set(partKey(part), el);
    void nextTick(renderChartPreviews);
  }
};

const isTruthyMetadata = (part: ChatMessagePart, key: string) => {
  const value = part.metadata?.[key];
  return value === true || value === 'true';
};

const isCodeExpanded = (part: ChatMessagePart) => {
  const key = partKey(part);
  if (expandedCode[key] === undefined) {
    return !isTruthyMetadata(part, 'defaultCollapsed');
  }
  return expandedCode[key] === true;
};

const toggleCode = (part: ChatMessagePart) => {
  const key = partKey(part);
  expandedCode[key] = !isCodeExpanded(part);
};

const isZenvisCardExpanded = (part: ChatMessagePart) => {
  const key = partKey(part);
  if (expandedZenvisCards[key] === undefined) {
    return !isTruthyMetadata(part, 'defaultCollapsed');
  }
  return expandedZenvisCards[key] === true;
};

const toggleZenvisCard = (part: ChatMessagePart) => {
  const key = partKey(part);
  expandedZenvisCards[key] = !isZenvisCardExpanded(part);
};

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

const promptSuggestionExamples = (part: ChatMessagePart): PromptSuggestionExample[] => {
  const examples = part.metadata?.examples;
  if (!Array.isArray(examples)) {
    return [];
  }
  return examples
    .filter(example => example && typeof example === 'object')
    .map(example => {
      const raw = example as Record<string, unknown>;
      const prompt = typeof raw.prompt === 'string' ? raw.prompt : '';
      const label = typeof raw.label === 'string' ? raw.label : prompt;
      return {
        label: label || '示例',
        prompt,
      };
    })
    .filter(example => example.prompt);
};

const selectPromptSuggestion = (prompt: string) => {
  emit('selectPromptSuggestion', prompt);
};

const metadataText = (part: ChatMessagePart, key: string) => {
  const value = part.metadata?.[key];
  return typeof value === 'string' ? value : '';
};

const metadataJsonText = (part: ChatMessagePart, key: string) => {
  const value = part.metadata?.[key];
  if (typeof value === 'string') {
    return value;
  }
  if (value && typeof value === 'object') {
    return JSON.stringify(value, null, 2);
  }
  return '';
};

const chartPreviewConfigText = (part: ChatMessagePart) => {
  return metadataJsonText(part, 'amisConfig') || part.content || '';
};

const chartLibraryAction = (part: ChatMessagePart) => {
  const action = metadataText(part, 'action');
  return action === 'data_visualization.add_chart_library' ? action : '';
};

const isChartLibraryAdded = (part: ChatMessagePart) => {
  return part.status === 'submitted' || part.status === 'added';
};

const requestAddChartLibrary = (part: ChatMessagePart) => {
  if (!chartLibraryAction(part) || isChartLibraryAdded(part)) {
    return;
  }
  emit('addChartLibrary', part);
};

const chartPreviewOption = (part: ChatMessagePart) => {
  const value = part.metadata?.echartsOption || part.metadata?.option;
  if (typeof value === 'string') {
    try {
      return JSON.parse(value);
    } catch {
      return null;
    }
  }
  if (value && typeof value === 'object') {
    return value;
  }
  return null;
};

const renderChartPreview = (part: ChatMessagePart) => {
  const el = chartPreviewEls.get(partKey(part));
  const option = chartPreviewOption(part);
  if (!el || !option) {
    return;
  }
  let instance = chartPreviewInstances.get(partKey(part));
  if (!instance) {
    instance = echarts.init(el);
    chartPreviewInstances.set(partKey(part), instance);
  }
  instance.setOption(option, true);
  instance.resize();
};

const renderChartPreviews = () => {
  renderParts.value
    .filter(part => part.type === 'visualization-chart-preview')
    .forEach(renderChartPreview);
};

const resizeChartPreviews = () => {
  chartPreviewInstances.forEach(instance => instance.resize());
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

const reportDocumentFormat = (part: ChatMessagePart) => {
  return metadataText(part, 'format') || part.language || 'markdown';
};

const reportDocumentFormatText = (part: ChatMessagePart) => {
  return reportDocumentFormat(part) === 'html' ? 'HTML 文档' : 'Markdown 文档';
};

const isReportDocumentHtml = (part: ChatMessagePart) => {
  return reportDocumentFormat(part) === 'html';
};

const defaultConfigFileName = (part: ChatMessagePart) => {
  return metadataText(part, 'defaultFileName') || '-';
};

const configKind = (part: ChatMessagePart) => metadataText(part, 'configKind');

const isHtmlConfig = (part: ChatMessagePart) => configKind(part) === 'html-page';

const isLowCodeConfig = (part: ChatMessagePart) => {
  return ['low-code-page', 'low-code-app'].includes(configKind(part));
};

const isConfigPreviewable = (part: ChatMessagePart) => {
  return isHtmlConfig(part) || isLowCodeConfig(part);
};

const isConfigPreviewMode = (part: ChatMessagePart) => {
  return configPreviewModes[partKey(part)] === true;
};

const toggleConfigPreview = (part: ChatMessagePart) => {
  const key = partKey(part);
  configPreviewModes[key] = !isConfigPreviewMode(part);
  if (configPreviewModes[key]) {
    expandedZenvisCards[key] = true;
  }
};

const configPreviewButtonIcon = (part: ChatMessagePart) => {
  return isConfigPreviewMode(part) ? Document : View;
};

const isRecord = (value: unknown): value is Record<string, unknown> => {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
};

const asRecord = (value: unknown): Record<string, unknown> => {
  return isRecord(value) ? value : {};
};

const asRecordArray = (value: unknown): Record<string, unknown>[] => {
  return Array.isArray(value) ? value.filter(isRecord) : [];
};

const stringValue = (value: unknown) => {
  return typeof value === 'string' ? value : '';
};

const escapeHtml = (value: unknown) => {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  };
  return String(value ?? '').replace(/[&<>"']/g, char => map[char] || char);
};

const parseLowCodeConfig = (part: ChatMessagePart) => {
  try {
    return JSON.parse(part.content || '{}');
  } catch {
    return null;
  }
};

const lowCodePreviewHtml = (part: ChatMessagePart) => {
  const schema = parseLowCodeConfig(part);
  if (!schema) {
    return '<div class="amis-preview-empty">配置内容不是有效 JSON，无法生成预览。</div>';
  }
  return DOMPurify.sanitize(renderLowCodePreview(schema, configKind(part)));
};

const renderLowCodePreview = (schema: unknown, kind: string): string => {
  if (kind === 'low-code-app') {
    return renderLowCodeAppPreview(asRecord(schema));
  }
  return renderLowCodeNode(asRecord(schema));
};

const renderLowCodeAppPreview = (schema: Record<string, unknown>) => {
  const data = asRecord(schema.data);
  const pages = asRecordArray(data.pages);
  const menuItems = pages.flatMap(page => {
    const children = asRecordArray(page.children);
    return children.length > 0 ? children : [page];
  }).filter(page => stringValue(page.label));
  const menus = menuItems.length > 0 ? menuItems : [
    { label: '首页', url: 'index' },
    { label: '管理页面', url: 'manage' },
  ];
  return `
    <div class="amis-preview-app">
      <aside class="amis-preview-sidebar">
        <div class="amis-preview-brand">用户事件应用</div>
        ${menus.map((menu, index) => `
          <div class="amis-preview-nav-item ${index === 0 ? 'active' : ''}">
            <span>${escapeHtml(menu.label)}</span>
            <small>${escapeHtml(menu.url)}</small>
          </div>
        `).join('')}
      </aside>
      <main class="amis-preview-app-main">
        <div class="amis-preview-page-title">低代码应用预览</div>
        <div class="amis-preview-grid">
          <section class="amis-preview-panel">
            <div class="amis-preview-panel-title">首页</div>
            <p>展示用户事件总览、上报趋势和常用入口。</p>
          </section>
          <section class="amis-preview-panel">
            <div class="amis-preview-panel-title">管理页面</div>
            <p>提供用户事件查询、新增、编辑和删除操作。</p>
          </section>
        </div>
      </main>
    </div>
  `;
};

const renderLowCodeNode = (node: unknown): string => {
  if (Array.isArray(node)) {
    return node.map(renderLowCodeNode).join('');
  }
  const schema = asRecord(node);
  const type = stringValue(schema.type);
  if (!type && Object.keys(schema).length === 0) {
    return '<div class="amis-preview-empty">暂无可预览内容。</div>';
  }
  if (type === 'page') {
    return `
      <div class="amis-preview-page">
        <header class="amis-preview-page-header">
          <div class="amis-preview-page-title">${escapeHtml(schema.title || '低代码页面')}</div>
          ${renderLowCodeToolbar(schema.toolbar)}
        </header>
        <div class="amis-preview-page-body">${renderLowCodeNode(schema.body)}</div>
      </div>
    `;
  }
  if (type === 'crud') {
    return renderCrudPreview(schema);
  }
  if (type === 'chart') {
    return renderChartSchemaPreview(schema);
  }
  if (type === 'grid') {
    const columns = asRecordArray(schema.columns);
    return `
      <div class="amis-preview-grid">
        ${columns.map(column => `<section class="amis-preview-panel">${renderLowCodeNode(column.body || column)}</section>`).join('')}
      </div>
    `;
  }
  if (type === 'service') {
    return `
      <section class="amis-preview-service">
        <div class="amis-preview-api">${escapeHtml(schema.api || 'service api')}</div>
        ${renderLowCodeNode(schema.body)}
      </section>
    `;
  }
  if (type === 'panel') {
    return `
      <section class="amis-preview-panel">
        <div class="amis-preview-panel-title">${escapeHtml(schema.title || '面板')}</div>
        ${renderLowCodeNode(schema.body)}
      </section>
    `;
  }
  if (type === 'form') {
    return renderFormPreview(schema);
  }
  if (type === 'tpl' || type === 'static') {
    return `<div class="amis-preview-text">${escapeHtml(stripTemplateText(schema.tpl || schema.value || schema.label || '文本内容'))}</div>`;
  }
  if (type === 'divider') {
    return '<div class="amis-preview-divider"></div>';
  }
  return `
    <section class="amis-preview-panel">
      <div class="amis-preview-panel-title">${escapeHtml(configTypeLabel(type))}</div>
      ${renderLowCodeNode(schema.body)}
    </section>
  `;
};

const renderLowCodeToolbar = (toolbar: unknown) => {
  const buttons = asRecordArray(toolbar);
  if (buttons.length === 0) {
    return '';
  }
  return `
    <div class="amis-preview-toolbar">
      ${buttons.map(button => `<button type="button">${escapeHtml(button.label || configTypeLabel(stringValue(button.type)))}</button>`).join('')}
    </div>
  `;
};

const renderCrudPreview = (schema: Record<string, unknown>) => {
  const columns = asRecordArray(schema.columns).slice(0, 8);
  const visibleColumns = columns.length > 0 ? columns : [
    { name: 'id', label: '事件ID' },
    { name: 'user', label: '用户' },
    { name: 'event_type', label: '事件类型' },
    { name: 'reliability', label: '可信度' },
    { name: 'server_time', label: '入库时间' },
  ];
  return `
    <section class="amis-preview-crud">
      <div class="amis-preview-crud-header">
        <div>
          <div class="amis-preview-panel-title">用户事件列表</div>
          <div class="amis-preview-api">${escapeHtml(schema.api || '/zenvis/api/v1/entity/user-event/list')}</div>
        </div>
        <button type="button">查询</button>
      </div>
      <div class="amis-preview-filter">
        <span>用户</span>
        <span>事件类型</span>
        <span>入库时间</span>
      </div>
      <div class="amis-preview-table-wrap">
        <table class="amis-preview-table">
          <thead>
            <tr>${visibleColumns.map(column => `<th>${escapeHtml(column.label || column.name || configTypeLabel(stringValue(column.type)))}</th>`).join('')}</tr>
          </thead>
          <tbody>
            <tr>${visibleColumns.map(column => `<td>${escapeHtml(sampleColumnValue(column))}</td>`).join('')}</tr>
            <tr>${visibleColumns.map(column => `<td>${escapeHtml(sampleColumnValue(column, true))}</td>`).join('')}</tr>
          </tbody>
        </table>
      </div>
    </section>
  `;
};

const renderChartSchemaPreview = (schema: Record<string, unknown>) => {
  const config = asRecord(schema.config);
  const title = asRecord(config.title);
  return `
    <section class="amis-preview-chart">
      <div class="amis-preview-chart-title">${escapeHtml(title.text || schema.title || '用户事件上报趋势')}</div>
      <div class="amis-preview-api">${escapeHtml(schema.api || '/zenvis/api/v1/retrieval/aggregate/trend')}</div>
      <div class="amis-preview-chart-canvas">
        <span class="amis-preview-bar bar-1"></span>
        <span class="amis-preview-bar bar-2"></span>
        <span class="amis-preview-bar bar-3"></span>
        <span class="amis-preview-bar bar-4"></span>
        <span class="amis-preview-bar bar-5"></span>
        <span class="amis-preview-bar bar-6"></span>
      </div>
    </section>
  `;
};

const renderFormPreview = (schema: Record<string, unknown>) => {
  const fields = asRecordArray(schema.body).slice(0, 8);
  return `
    <section class="amis-preview-form">
      <div class="amis-preview-panel-title">${escapeHtml(schema.title || '表单')}</div>
      <div class="amis-preview-form-grid">
        ${fields.map(field => `
          <label>
            <span>${escapeHtml(field.label || field.name || configTypeLabel(stringValue(field.type)))}</span>
            <input readonly value="${escapeHtml(sampleColumnValue(field))}" />
          </label>
        `).join('')}
      </div>
    </section>
  `;
};

const stripTemplateText = (value: unknown) => {
  return String(value ?? '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\$\{[^}]+}/g, '示例值')
    .replace(/\s+/g, ' ')
    .trim();
};

const sampleColumnValue = (column: Record<string, unknown>, secondRow = false) => {
  const name = stringValue(column.name).toLowerCase();
  const label = stringValue(column.label);
  if (stringValue(column.type) === 'operation' || label === '操作') {
    return secondRow ? '编辑 / 删除' : '查看 / 编辑';
  }
  if (name.includes('id')) return secondRow ? 'evt-2026070902' : 'evt-2026070901';
  if (name.includes('procid')) return secondRow ? '108' : '101';
  if (name.includes('user')) return secondRow ? 'operator-b' : 'demo-user';
  if (name.includes('event_type')) return secondRow ? '点击' : '登录';
  if (name.includes('reliability')) return secondRow ? '7.6' : '8.8';
  if (name.includes('server_time') || name.includes('time')) return secondRow ? '2026-07-09 11:20:00' : '2026-07-09 10:00:00';
  if (name.includes('tag')) return secondRow ? '运营' : '演示, 可视化';
  if (name.includes('detail')) return secondRow ? '{"path":"/event"}' : '{"method":"POST"}';
  return secondRow ? '示例值 B' : '示例值 A';
};

const configTypeLabel = (type: string) => {
  const labels: Record<string, string> = {
    button: '按钮',
    input: '输入框',
    'input-text': '文本输入',
    select: '选择器',
    textarea: '多行文本',
    mapping: '映射',
    operation: '操作',
  };
  return labels[type] || type || '组件';
};

const infoSteps = (part: ChatMessagePart): InfoStepItem[] => {
  const steps = part.metadata?.steps;
  if (!Array.isArray(steps)) {
    return [];
  }
  return steps
    .filter(step => step && typeof step === 'object')
    .map((step, index) => {
      const raw = step as Record<string, unknown>;
      return {
        id: typeof raw.id === 'string' && raw.id.trim() ? raw.id : `step_${index + 1}`,
        title: typeof raw.title === 'string' ? raw.title : '',
        description: typeof raw.description === 'string' ? raw.description : '',
        required: raw.required === true || raw.required === 'true',
        suggestions: Array.isArray(raw.suggestions) ? raw.suggestions : [],
        placeholder: typeof raw.placeholder === 'string' ? raw.placeholder : '',
      };
    });
};

const normalizeSuggestion = (suggestion: string | InfoStepSuggestion | Record<string, unknown>): InfoStepSuggestion => {
  if (typeof suggestion === 'string') {
    return {
      label: suggestion,
      value: suggestion,
    };
  }
  const label = typeof suggestion.label === 'string' ? suggestion.label : '';
  const value = typeof suggestion.value === 'string' ? suggestion.value : label;
  return {
    label: label || value || '建议项',
    value: value || label,
    description: typeof suggestion.description === 'string' ? suggestion.description : undefined,
  };
};

const stepSuggestions = (step: InfoStepItem): InfoStepSuggestion[] => {
  return (step.suggestions || []).map(normalizeSuggestion);
};

const infoStepKey = (part: ChatMessagePart, step: InfoStepItem) => `${partKey(part)}::${step.id}`;

const selectedInfoStepValue = (part: ChatMessagePart, step: InfoStepItem) => {
  return infoStepSelectedValues[infoStepKey(part, step)] || '';
};

const isSuggestionSelected = (part: ChatMessagePart, step: InfoStepItem, suggestion: InfoStepSuggestion) => {
  return selectedInfoStepValue(part, step) === suggestion.value;
};

const selectInfoStepSuggestion = (part: ChatMessagePart, step: InfoStepItem, suggestion: InfoStepSuggestion) => {
  const key = infoStepKey(part, step);
  infoStepSelectedValues[key] = suggestion.value;
  infoStepCustomInputs[key] = '';
};

const infoStepAnswerValue = (part: ChatMessagePart, step: InfoStepItem) => {
  const key = infoStepKey(part, step);
  const customValue = (infoStepCustomInputs[key] || '').trim();
  if (customValue) {
    return {
      value: customValue,
      source: 'custom' as const,
    };
  }
  return {
    value: (infoStepSelectedValues[key] || '').trim(),
    source: 'suggestion' as const,
  };
};

const infoStepsSubmitLabel = (part: ChatMessagePart) => {
  const value = part.metadata?.submitLabel;
  return typeof value === 'string' && value.trim() ? value : '提交补充信息';
};

const submitInfoSteps = (part: ChatMessagePart) => {
  const steps = infoSteps(part);
  const missingStep = steps.find(step => step.required && !infoStepAnswerValue(part, step).value);
  if (missingStep) {
    ElMessage.warning(`请补充「${missingStep.title || '必填项'}」`);
    return;
  }
  const answers = steps
    .map(step => {
      const answer = infoStepAnswerValue(part, step);
      return {
        id: step.id,
        title: step.title || step.id,
        value: answer.value,
        source: answer.source,
      };
    })
    .filter(answer => answer.value);
  emit('submitInfoSteps', { part, answers });
};

const metadataStringList = (part: ChatMessagePart, key: string) => {
  const value = part.metadata?.[key];
  return Array.isArray(value) ? value.filter(item => typeof item === 'string') as string[] : [];
};

const supportsConfirmRevise = (part: ChatMessagePart) => {
  return metadataStringList(part, 'actions').includes('revise');
};

const confirmReviseLabel = (part: ChatMessagePart) => {
  const value = part.metadata?.reviseLabel;
  return typeof value === 'string' && value.trim() ? value : '补充信息继续更新';
};

const confirmRevisePlaceholder = (part: ChatMessagePart) => {
  const action = part.metadata?.action;
  if (action === 'analysis.confirm_log_aggregation' || action === 'analysis_demo.confirm_log_aggregation') {
    return '输入需要补充的日志线索，例如：继续关联文件变更记录、补查近 10 分钟网络连接日志';
  }
  if (action === 'analysis.confirm_sandbox_result' || action === 'analysis_demo.confirm_sandbox_result') {
    return '输入需要继续研判的重点，例如：复核文件落地时间、重点确认异常外联是否成功';
  }
  if (action === 'policy.confirm_trial' || action === 'policy_demo.confirm_trial' || action === 'policy_demo.confirm_retry_trial') {
    return '输入需要补充的策略调整要求，例如：增加回滚前置确认、扩大来源匹配范围、降低自动处置强度';
  }
  return '输入需要调整的内容，例如：改成静态 HTML、增加趋势图、调整菜单名称或看板指标';
};

const showConfirmRevise = (part: ChatMessagePart) => {
  confirmReviseInputVisible[partKey(part)] = true;
};

const isConfirmReviseInputVisible = (part: ChatMessagePart) => {
  return confirmReviseInputVisible[partKey(part)] === true;
};

const hideConfirmRevise = (part: ChatMessagePart) => {
  confirmReviseInputVisible[partKey(part)] = false;
};

const submitConfirmRevise = (part: ChatMessagePart) => {
  emit('decideAction', {
    part,
    decision: 'revise',
    detail: (confirmDecisionInputs[partKey(part)] || '').trim(),
  });
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
  if (status === 'revise') return 'warning';
  return 'warning';
};

const confirmStatusText = (status?: string) => {
  if (status === 'approved') return '已确认';
  if (status === 'rejected') return '已取消';
  if (status === 'revise') return '继续更新';
  return '待确认';
};

const infoStepsTagType = (status?: string) => {
  if (status === 'submitted') return 'success';
  return 'warning';
};

const infoStepsStatusText = (status?: string) => {
  if (status === 'submitted') return '已提交';
  return '待补充';
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

const isDataVisualizationRecord = (part: ChatMessagePart) => {
  return [
    'visualization-chart-record',
    'visualization-config-record',
    'dashboard-config-record',
    'menu-config-record',
  ].includes(part.type);
};

const dataVisualizationRecordTitle = (part: ChatMessagePart) => {
  if (part.type === 'visualization-chart-record') return part.title || '图表库记录';
  if (part.type === 'visualization-config-record') return part.title || '可视化配置记录';
  if (part.type === 'dashboard-config-record') return part.title || '数据看板配置记录';
  if (part.type === 'menu-config-record') return part.title || '菜单配置记录';
  return part.title || '数据可视化记录';
};

const analysisStageText = (stage: string) => {
  if (stage === 'log_aggregation') return '日志聚合';
  if (stage === 'sandbox_analysis') return '研判分析';
  if (stage === 'report_output') return '输出分析结论';
  return '研判记录';
};

const analysisRecordTitle = (part: ChatMessagePart) => {
  return part.title || metadataText(part, 'title') || analysisStageText(metadataText(part, 'stage'));
};

const analysisRecordTagType = (part: ChatMessagePart) => {
  const status = metadataText(part, 'status');
  if (status === 'completed' || status === 'success') return 'success';
  if (status === 'failed' || status === 'error') return 'danger';
  if (status === 'running' || status === 'processing') return 'warning';
  return 'info';
};

const analysisRecordStatusText = (part: ChatMessagePart) => {
  const status = metadataText(part, 'status');
  if (status === 'completed' || status === 'success') return '已完成';
  if (status === 'failed' || status === 'error') return '失败';
  if (status === 'running' || status === 'processing') return '进行中';
  return status || '待开始';
};

const policyTypeText = (type: string) => {
  if (type === 'collection') return '采集策略';
  if (type === 'tagging') return '标记策略';
  if (type === 'disposal') return '处置策略';
  return '策略记录';
};

const policyRecordTitle = (part: ChatMessagePart) => {
  return part.title || metadataText(part, 'title') || policyTypeText(metadataText(part, 'policyType'));
};

const policyRecordTagType = (part: ChatMessagePart) => {
  const status = metadataText(part, 'validationStatus');
  if (status === 'success') return 'success';
  if (status === 'failed') return 'danger';
  return 'info';
};

const policyRecordStatusText = (part: ChatMessagePart) => {
  const status = metadataText(part, 'validationStatus');
  if (status === 'success') return '验证成功';
  if (status === 'failed') return '验证失败';
  return '未验证';
};

watch(
  renderParts,
  () => {
    void nextTick(renderChartPreviews);
  },
  { deep: true, immediate: true },
);

window.addEventListener('resize', resizeChartPreviews);

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChartPreviews);
  chartPreviewInstances.forEach(instance => instance.dispose());
  chartPreviewInstances.clear();
  chartPreviewEls.clear();
});
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

.prompt-suggestions-part {
  max-width: 100%;
  min-width: 0;
}

.prompt-suggestions-title {
  margin-bottom: 8px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

.prompt-suggestion-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.prompt-suggestion-bubble {
  max-width: 100%;
  white-space: normal;
  height: auto;
  min-height: 32px;
  padding: 7px 12px;
  line-height: 1.35;
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

.code-title,
.code-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.code-tools {
  flex: 0 0 auto;
}

.code-language {
  min-width: 0;
  font-size: 12px;
  line-height: 1;
  overflow-wrap: anywhere;
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

.config-card-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
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

.config-preview {
  margin: 10px 12px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #ffffff;
  overflow: hidden;
}

.config-html-preview {
  display: block;
  width: 100%;
  min-height: 520px;
  border: 0;
  background: #ffffff;
}

.report-document-part {
  border-color: #c8d5ef;
  background: #fbfcff;
}

.report-document-preview {
  margin: 10px 12px 12px;
  padding: 14px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #ffffff;
}

.config-low-code-preview {
  padding: 14px;
  background: #f6f8fb;
}

.config-low-code-preview :deep(.amis-preview-page),
.config-low-code-preview :deep(.amis-preview-app) {
  overflow: hidden;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #ffffff;
}

.config-low-code-preview :deep(.amis-preview-page-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #ebeef5;
  background: #ffffff;
}

.config-low-code-preview :deep(.amis-preview-page-title),
.config-low-code-preview :deep(.amis-preview-brand),
.config-low-code-preview :deep(.amis-preview-panel-title),
.config-low-code-preview :deep(.amis-preview-chart-title) {
  min-width: 0;
  color: #303133;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
  word-break: break-word;
}

.config-low-code-preview :deep(.amis-preview-page-body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
}

.config-low-code-preview :deep(.amis-preview-toolbar) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.config-low-code-preview :deep(button) {
  height: 30px;
  padding: 0 12px;
  border: 1px solid #409eff;
  border-radius: 6px;
  background: #409eff;
  color: #ffffff;
  font-size: 13px;
  cursor: default;
}

.config-low-code-preview :deep(.amis-preview-app) {
  display: grid;
  grid-template-columns: minmax(140px, 180px) minmax(0, 1fr);
  min-height: 360px;
}

.config-low-code-preview :deep(.amis-preview-sidebar) {
  padding: 14px 10px;
  border-right: 1px solid #ebeef5;
  background: #ffffff;
}

.config-low-code-preview :deep(.amis-preview-brand) {
  margin: 0 8px 14px;
}

.config-low-code-preview :deep(.amis-preview-nav-item) {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 9px 10px;
  border-radius: 6px;
  color: #606266;
  font-size: 13px;
}

.config-low-code-preview :deep(.amis-preview-nav-item.active) {
  background: #ecf5ff;
  color: #1d6fd9;
  font-weight: 600;
}

.config-low-code-preview :deep(.amis-preview-nav-item small) {
  color: #909399;
  font-size: 11px;
  font-weight: 400;
  overflow-wrap: anywhere;
}

.config-low-code-preview :deep(.amis-preview-app-main) {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 16px;
  background: #f8fafc;
}

.config-low-code-preview :deep(.amis-preview-grid) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.config-low-code-preview :deep(.amis-preview-panel),
.config-low-code-preview :deep(.amis-preview-service),
.config-low-code-preview :deep(.amis-preview-crud),
.config-low-code-preview :deep(.amis-preview-chart),
.config-low-code-preview :deep(.amis-preview-form),
.config-low-code-preview :deep(.amis-preview-empty) {
  min-width: 0;
  padding: 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
}

.config-low-code-preview :deep(.amis-preview-panel p),
.config-low-code-preview :deep(.amis-preview-text),
.config-low-code-preview :deep(.amis-preview-empty) {
  margin: 8px 0 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.config-low-code-preview :deep(.amis-preview-api) {
  margin-top: 6px;
  color: #909399;
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.config-low-code-preview :deep(.amis-preview-crud-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.config-low-code-preview :deep(.amis-preview-filter) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.config-low-code-preview :deep(.amis-preview-filter span) {
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #f7f8fa;
  color: #606266;
  font-size: 12px;
}

.config-low-code-preview :deep(.amis-preview-table-wrap) {
  max-width: 100%;
  overflow-x: auto;
}

.config-low-code-preview :deep(.amis-preview-table) {
  width: 100%;
  min-width: 560px;
  border-collapse: collapse;
  font-size: 13px;
}

.config-low-code-preview :deep(.amis-preview-table th),
.config-low-code-preview :deep(.amis-preview-table td) {
  padding: 9px 10px;
  border-bottom: 1px solid #ebeef5;
  color: #303133;
  text-align: left;
  white-space: nowrap;
}

.config-low-code-preview :deep(.amis-preview-table th) {
  background: #f7f8fa;
  color: #606266;
  font-weight: 600;
}

.config-low-code-preview :deep(.amis-preview-chart-canvas) {
  display: flex;
  align-items: end;
  gap: 14px;
  height: 220px;
  margin-top: 14px;
  padding: 12px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.config-low-code-preview :deep(.amis-preview-bar) {
  flex: 1;
  min-width: 18px;
  border-radius: 6px 6px 0 0;
  background: linear-gradient(180deg, #67c23a 0%, #409eff 100%);
}

.config-low-code-preview :deep(.amis-preview-bar.bar-1) {
  height: 38%;
}

.config-low-code-preview :deep(.amis-preview-bar.bar-2) {
  height: 52%;
}

.config-low-code-preview :deep(.amis-preview-bar.bar-3) {
  height: 76%;
}

.config-low-code-preview :deep(.amis-preview-bar.bar-4) {
  height: 88%;
}

.config-low-code-preview :deep(.amis-preview-bar.bar-5) {
  height: 68%;
}

.config-low-code-preview :deep(.amis-preview-bar.bar-6) {
  height: 47%;
}

.config-low-code-preview :deep(.amis-preview-form-grid) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.config-low-code-preview :deep(.amis-preview-form-grid label) {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #606266;
  font-size: 12px;
}

.config-low-code-preview :deep(.amis-preview-form-grid input) {
  height: 30px;
  min-width: 0;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 0 9px;
  background: #f7f8fa;
  color: #303133;
}

.config-low-code-preview :deep(.amis-preview-divider) {
  height: 1px;
  margin: 10px 0;
  background: #ebeef5;
}

@media (max-width: 640px) {
  .config-low-code-preview :deep(.amis-preview-app) {
    grid-template-columns: 1fr;
  }

  .config-low-code-preview :deep(.amis-preview-sidebar) {
    border-right: 0;
    border-bottom: 1px solid #ebeef5;
  }

  .config-low-code-preview :deep(.amis-preview-page-header),
  .config-low-code-preview :deep(.amis-preview-crud-header) {
    align-items: flex-start;
    flex-direction: column;
  }

  .config-html-preview {
    min-height: 420px;
  }
}

.notice-part,
.confirm-part,
.info-steps-part,
.analysis-decision-part,
.data-access-decision-part,
.visualization-chart-preview-part,
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
.info-steps-title,
.analysis-decision-title,
.data-access-decision-title,
.visualization-chart-preview-header,
.chart-part {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.visualization-chart-preview-header {
  justify-content: space-between;
}

.visualization-chart-preview-title,
.visualization-chart-preview-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.visualization-chart-preview-desc {
  margin: 8px 0 10px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

.visualization-chart-preview-canvas {
  width: 100%;
  height: 320px;
  min-height: 260px;
}

.card-title-text {
  min-width: 0;
  overflow-wrap: anywhere;
}

.card-toggle-btn {
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  margin-left: auto;
  background: transparent;
}

.notice-content,
.confirm-content,
.info-steps-content,
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
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.confirm-revise-box {
  margin-top: 12px;
}

.confirm-revise-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.info-steps-part {
  border-color: #d9ecff;
  background: #f8fbff;
}

.info-steps-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-top: 12px;
}

.info-step-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 10px;
  position: relative;
  padding-bottom: 14px;
}

.info-step-item:not(:last-child)::before {
  content: '';
  position: absolute;
  top: 28px;
  bottom: 0;
  left: 13px;
  width: 2px;
  background: #d9ecff;
}

.info-step-marker {
  position: relative;
  z-index: 1;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  line-height: 28px;
  text-align: center;
}

.info-step-body {
  min-width: 0;
}

.info-step-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.info-step-title {
  font-weight: 600;
  color: #303133;
  word-break: break-word;
}

.info-step-description {
  margin-top: 4px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.info-step-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.info-step-suggestion {
  max-width: 100%;
}

.info-step-suggestion :deep(span) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-step-input {
  margin-top: 10px;
}

.info-steps-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
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
