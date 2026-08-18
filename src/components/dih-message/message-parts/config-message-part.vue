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
            @click="emit('copyCode', reportDocumentContent)"
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
        :srcdoc="reportDocumentContent"
        sandbox="allow-same-origin"
      ></iframe>
      <div
        v-else
        class="message-content markdown-body report-document-preview"
        v-html="parseMarkdown(reportDocumentContent)"
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
import { resolveReportDocumentPartContent } from '@/views/dih/components/report-document-sync';
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
  if (kind === 'report-document') return '报表文档';
  return kind || '配置';
});

const reportDocumentFormat = computed(() => {
  return metadataText(props.part, 'format') || props.part.language || 'markdown';
});

const reportDocumentFormatText = computed(() => {
  return reportDocumentFormat.value === 'html' ? 'HTML 文档' : 'Markdown 文档';
});

const reportDocumentContent = computed(() => resolveReportDocumentPartContent(props.part));

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
  const brand = schema.title || data.title || data.brandName || '低代码应用';
  return `
    <div class="amis-preview-app">
      <aside class="amis-preview-sidebar">
        <div class="amis-preview-brand">${escapeHtml(brand)}</div>
        ${
          menuItems.length > 0
            ? menuItems
                .map(
                  (menu, index) => `
          <div class="amis-preview-nav-item ${index === 0 ? 'active' : ''}">
            <span>${escapeHtml(menu.label)}</span>
            <small>${escapeHtml(menu.url)}</small>
          </div>
        `,
                )
                .join('')
            : '<div class="amis-preview-empty">配置中未提供页面菜单。</div>'
        }
      </aside>
      <main class="amis-preview-app-main">
        <div class="amis-preview-page-title">低代码应用预览</div>
        <div class="amis-preview-grid">
          ${
            menuItems.length > 0
              ? menuItems
                  .map(
                    menu => `
              <section class="amis-preview-panel">
                <div class="amis-preview-panel-title">${escapeHtml(menu.label)}</div>
                <p>${escapeHtml(menu.url || '页面配置')}</p>
              </section>
            `,
                  )
                  .join('')
              : '<div class="amis-preview-empty">暂无可预览页面。</div>'
          }
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
  const data = asRecord(schema.data);
  const rowsFromData = Array.isArray(schema.data)
    ? asRecordArray(schema.data)
    : asRecordArray(data.items).length > 0
      ? asRecordArray(data.items)
      : asRecordArray(data.rows);
  const rows = rowsFromData.slice(0, 2);
  const filters = asRecordArray(asRecord(schema.filter).body).slice(0, 8);
  return `
    <section class="amis-preview-crud">
      <div class="amis-preview-crud-header">
        <div>
          <div class="amis-preview-panel-title">${escapeHtml(schema.title || '数据列表')}</div>
          <div class="amis-preview-api">${escapeHtml(schema.api || '未配置查询 API')}</div>
        </div>
        <button type="button">查询</button>
      </div>
      ${
        filters.length > 0
          ? `<div class="amis-preview-filter">${filters
              .map(filter => `<span>${escapeHtml(filter.label || filter.name)}</span>`)
              .join('')}</div>`
          : ''
      }
      <div class="amis-preview-table-wrap">
        ${
          columns.length > 0
            ? `<table class="amis-preview-table">
                <thead>
                  <tr>${columns
                    .map(
                      column =>
                        `<th>${escapeHtml(
                          column.label ||
                            column.name ||
                            configTypeLabel(stringValue(column.type)),
                        )}</th>`,
                    )
                    .join('')}</tr>
                </thead>
                <tbody>
                  ${
                    rows.length > 0
                      ? rows
                          .map(
                            row =>
                              `<tr>${columns
                                .map(column => {
                                  const name = stringValue(column.name);
                                  return `<td>${escapeHtml(name ? row[name] : '')}</td>`;
                                })
                                .join('')}</tr>`,
                          )
                          .join('')
                      : `<tr><td colspan="${columns.length}">配置预览不填充演示数据</td></tr>`
                  }
                </tbody>
              </table>`
            : '<div class="amis-preview-empty">配置中未提供表格字段。</div>'
        }
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
        title.text || schema.title || '图表',
      )}</div>
      <div class="amis-preview-api">${escapeHtml(schema.api || '未配置查询 API')}</div>
      <div class="amis-preview-chart-canvas">
        <span>此处不填充演示图形；真实数据图表请使用可视化预览。</span>
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
            <input
              readonly
              value="${escapeHtml(field.value ?? field.defaultValue ?? field.default ?? '')}"
            />
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
    .replace(/\$\{[^}]+}/g, '动态字段')
    .replace(/\s+/g, ' ')
    .trim();
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
