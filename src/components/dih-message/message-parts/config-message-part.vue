<template>
  <div v-if="part.type === 'report-document'" class="config-part report-document-part">
    <div class="config-card-header">
      <div class="config-card-title">
        <el-icon><Document /></el-icon>
        <span class="config-card-name">{{
          part.title || metadataText(part, 'title') || '报表文档'
        }}</span>
        <el-tag size="small" effect="plain">{{ reportDocumentFormatText }}</el-tag>
      </div>
      <div class="config-card-tools">
        <el-tooltip content="复制文档" placement="top">
          <el-button
            class="config-copy-btn"
            size="small"
            :icon="CopyDocument"
            circle
            @click="emit('copyCode', part.content || '')"
          />
        </el-tooltip>
        <el-tooltip :content="isExpanded ? '折叠' : '展开'" placement="top">
          <el-button
            class="card-toggle-btn"
            size="small"
            :icon="isExpanded ? CaretTop : CaretBottom"
            circle
            @click="toggleExpanded"
          />
        </el-tooltip>
      </div>
    </div>
    <template v-if="isExpanded">
      <div class="config-card-meta">
        <span>{{ interactive ? '已同步到右侧报表编辑器' : '只读报表预览' }}</span>
      </div>
      <iframe
        v-if="isReportDocumentHtml"
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

  <div v-else class="config-part">
    <div class="config-card-header">
      <div class="config-card-title">
        <el-icon><Document /></el-icon>
        <span class="config-card-name">{{ part.title || '配置文件' }}</span>
        <el-tag size="small" effect="plain">{{ configKindText }}</el-tag>
      </div>
      <div class="config-card-tools">
        <el-tooltip
          v-if="isConfigPreviewable"
          :content="isConfigPreviewMode ? '查看源码' : '预览最终效果'"
          placement="top"
        >
          <el-button
            class="config-copy-btn"
            size="small"
            :icon="configPreviewButtonIcon"
            circle
            @click="toggleConfigPreview"
          />
        </el-tooltip>
        <el-tooltip content="复制配置" placement="top">
          <el-button
            class="config-copy-btn"
            size="small"
            :icon="CopyDocument"
            circle
            @click="emit('copyCode', part.content || '')"
          />
        </el-tooltip>
        <el-tooltip :content="isExpanded ? '折叠' : '展开'" placement="top">
          <el-button
            class="card-toggle-btn"
            size="small"
            :icon="isExpanded ? CaretTop : CaretBottom"
            circle
            @click="toggleExpanded"
          />
        </el-tooltip>
      </div>
    </div>
    <template v-if="isExpanded">
      <div class="config-card-meta">
        <span>默认文件：{{ defaultConfigFileName }}</span>
      </div>
      <div v-if="isConfigPreviewMode" class="config-preview">
        <iframe
          v-if="isHtmlConfig"
          class="config-html-preview"
          :srcdoc="part.content || ''"
          sandbox="allow-scripts allow-forms allow-same-origin"
        ></iframe>
        <div v-else class="config-low-code-preview" v-html="lowCodePreviewHtml"></div>
      </div>
      <pre v-else class="config-card-content"><code>{{ part.content }}</code></pre>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, toRef } from 'vue';
import DOMPurify from 'dompurify';
import { CaretBottom, CaretTop, CopyDocument, Document, View } from '@element-plus/icons-vue';
import type { ChatMessagePart } from '@/types/type-dih';
import { metadataText, useDefaultExpanded, useMarkdownRenderer } from './message-part-context';

const props = defineProps<{
  part: ChatMessagePart;
  interactive?: boolean;
}>();

const emit = defineEmits<{
  (e: 'copyCode', content: string): void;
}>();

const { parseMarkdown } = useMarkdownRenderer();
const { isExpanded, toggleExpanded, setExpanded } = useDefaultExpanded(toRef(props, 'part'));
const configPreviewModeOverride = ref<boolean>();

const configKind = computed(() => metadataText(props.part, 'configKind'));

const configKindText = computed(() => {
  const kind = configKind.value;
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
});

const reportDocumentFormat = computed(() => {
  return metadataText(props.part, 'format') || props.part.language || 'markdown';
});

const reportDocumentFormatText = computed(() => {
  return reportDocumentFormat.value === 'html' ? 'HTML 文档' : 'Markdown 文档';
});

const isReportDocumentHtml = computed(() => reportDocumentFormat.value === 'html');

const defaultConfigFileName = computed(() => {
  return metadataText(props.part, 'defaultFileName') || '-';
});

const isHtmlConfig = computed(() => configKind.value === 'html-page');
const isLowCodeConfig = computed(() =>
  ['low-code-page', 'low-code-app'].includes(configKind.value),
);
const isConfigPreviewable = computed(() => isHtmlConfig.value || isLowCodeConfig.value);

const isConfigPreviewMode = computed(() => {
  return configPreviewModeOverride.value === true;
});

const toggleConfigPreview = () => {
  if (!isConfigPreviewable.value) {
    return;
  }
  configPreviewModeOverride.value = !isConfigPreviewMode.value;
  if (configPreviewModeOverride.value) {
    setExpanded(true);
  }
};

const configPreviewButtonIcon = computed(() => {
  return isConfigPreviewMode.value ? Document : View;
});

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

const parseLowCodeConfig = () => {
  try {
    return JSON.parse(props.part.content || '{}');
  } catch {
    return null;
  }
};

const lowCodePreviewHtml = computed(() => {
  const schema = parseLowCodeConfig();
  if (!schema) {
    return '<div class="amis-preview-empty">配置内容不是有效 JSON，无法生成预览。</div>';
  }
  return DOMPurify.sanitize(renderLowCodePreview(schema, configKind.value));
});

const renderLowCodePreview = (schema: unknown, kind: string): string => {
  if (kind === 'low-code-app') {
    return renderLowCodeAppPreview(asRecord(schema));
  }
  return renderLowCodeNode(asRecord(schema));
};

const renderLowCodeAppPreview = (schema: Record<string, unknown>) => {
  const data = asRecord(schema.data);
  const pages = asRecordArray(data.pages);
  const menuItems = pages
    .flatMap(page => {
      const children = asRecordArray(page.children);
      return children.length > 0 ? children : [page];
    })
    .filter(page => stringValue(page.label));
  const menus =
    menuItems.length > 0
      ? menuItems
      : [
          { label: '首页', url: 'index' },
          { label: '管理页面', url: 'manage' },
        ];
  return `
    <div class="amis-preview-app">
      <aside class="amis-preview-sidebar">
        <div class="amis-preview-brand">用户事件应用</div>
        ${menus
          .map(
            (menu, index) => `
          <div class="amis-preview-nav-item ${index === 0 ? 'active' : ''}">
            <span>${escapeHtml(menu.label)}</span>
            <small>${escapeHtml(menu.url)}</small>
          </div>
        `,
          )
          .join('')}
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
        ${columns
          .map(
            column =>
              `<section class="amis-preview-panel">${renderLowCodeNode(
                column.body || column,
              )}</section>`,
          )
          .join('')}
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
    return `<div class="amis-preview-text">${escapeHtml(
      stripTemplateText(schema.tpl || schema.value || schema.label || '文本内容'),
    )}</div>`;
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
      ${buttons
        .map(
          button =>
            `<button type="button">${escapeHtml(
              button.label || configTypeLabel(stringValue(button.type)),
            )}</button>`,
        )
        .join('')}
    </div>
  `;
};

const renderCrudPreview = (schema: Record<string, unknown>) => {
  const columns = asRecordArray(schema.columns).slice(0, 8);
  const visibleColumns =
    columns.length > 0
      ? columns
      : [
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
          <div class="amis-preview-api">${escapeHtml(
            schema.api || '/zenvis/api/v1/entity/user-event/list',
          )}</div>
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
            <tr>${visibleColumns
              .map(
                column =>
                  `<th>${escapeHtml(
                    column.label || column.name || configTypeLabel(stringValue(column.type)),
                  )}</th>`,
              )
              .join('')}</tr>
          </thead>
          <tbody>
            <tr>${visibleColumns
              .map(column => `<td>${escapeHtml(sampleColumnValue(column))}</td>`)
              .join('')}</tr>
            <tr>${visibleColumns
              .map(column => `<td>${escapeHtml(sampleColumnValue(column, true))}</td>`)
              .join('')}</tr>
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
      <div class="amis-preview-chart-title">${escapeHtml(
        title.text || schema.title || '用户事件上报趋势',
      )}</div>
      <div class="amis-preview-api">${escapeHtml(schema.api || '/zenvis/api/v1/entity/trend')}</div>
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
        ${fields
          .map(
            field => `
          <label>
            <span>${escapeHtml(
              field.label || field.name || configTypeLabel(stringValue(field.type)),
            )}</span>
            <input readonly value="${escapeHtml(sampleColumnValue(field))}" />
          </label>
        `,
          )
          .join('')}
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
  if (name.includes('server_time') || name.includes('time'))
    return secondRow ? '2026-07-09 11:20:00' : '2026-07-09 10:00:00';
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
</script>
