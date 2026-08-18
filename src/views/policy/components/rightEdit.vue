<template>
  <div v-show="fileName" class="code-box-div">
    <div class="editor-toolbar">
      <div class="editor-file-meta">
        <span class="editor-file-icon" aria-hidden="true">
          <el-icon><Document /></el-icon>
        </span>
        <div>
          <strong :title="fileName">{{ fileName }}</strong>
          <small>{{ language.toUpperCase() }} · 支持 Ctrl/Cmd + S 快速保存</small>
        </div>
      </div>
      <el-space>
        <el-button type="success" plain @click="apply">
          <el-icon><Check /></el-icon>
          应用
        </el-button>
        <el-button type="primary" @click="submit">
          <el-icon><DocumentChecked /></el-icon>
          保存
        </el-button>
      </el-space>
    </div>
    <div ref="codeEditBox" class="codeEditBox"></div>
  </div>
  <div v-show="!fileName" class="no-file-tip">
    <span class="empty-code-icon" aria-hidden="true">
      <el-icon><Document /></el-icon>
    </span>
    <strong>选择一个配置文件开始编辑</strong>
    <p>从左侧文件树中选择配置，编辑器会自动加载内容与校验规则。</p>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as monaco from 'monaco-editor';
import type { PropType } from 'vue';
import { Check, Document, DocumentChecked } from '@element-plus/icons-vue';
import { DihService, PolicyService } from '@/service/api';
import { useRouter } from 'vue-router';
import { generateUUID } from '@/utils/util-common';
import { setupMonacoWorkers } from '@u/monaco-workers';

// 定义类型
type Theme = 'vs' | 'hc-black' | 'vs-dark';
type FoldingStrategy = 'auto' | 'indentation';
type RenderLineHighlight = 'all' | 'line' | 'none' | 'gutter';

interface Options {
  automaticLayout: boolean;
  foldingStrategy: FoldingStrategy;
  renderLineHighlight: RenderLineHighlight;
  selectOnLineNumbers: boolean;
  minimap: {
    enabled: boolean;
  };
  readOnly: boolean;
  fontSize: number;
  scrollBeyondLastLine: boolean;
  overviewRulerBorder: boolean;
}

// 定义 props
const props = defineProps({
  fileName: {
    type: String,
    default: '',
  },
  schemaType: {
    type: String,
    default: null,
  },
  modelValue: {
    type: String,
    default: null,
  },
  width: {
    type: [String, Number],
    default: '100%',
  },
  height: {
    type: [String, Number],
    default: '100%',
  },
  language: {
    type: String,
    default: 'javascript',
  },
  theme: {
    type: String as PropType<Theme>,
    validator(value: string): boolean {
      return ['vs', 'hc-black', 'vs-dark'].includes(value);
    },
    default: 'vs',
  },
  options: {
    type: Object as PropType<Options>,
    default: () => ({
      automaticLayout: true,
      foldingStrategy: 'indentation',
      renderLineHighlight: 'all',
      selectOnLineNumbers: true,
      minimap: {
        enabled: true,
      },
      readOnly: false,
      fontSize: 16,
      scrollBeyondLastLine: false,
      overviewRulerBorder: false,
    }),
  },
});

// 定义 emits
const emit = defineEmits<{
  (e: 'on-apply', value: string): void;
  (e: 'on-click', value: string): void;
  (e: 'update:modelValue', value: string): void;
  (e: 'change', value: string): void;
  (e: 'editor-mounted', editor: monaco.editor.IStandaloneCodeEditor): void;
}>();

const router = useRouter();
const codeEditBox = ref<HTMLElement | null>(null);
let editor: monaco.editor.IStandaloneCodeEditor | null = null;
let documentationCommandId: string | null = null;
let inlineSuggestionProvider: monaco.IDisposable | null = null;
let saveKeyBinding: string | null = null;

// 补全建议的格式标记
const suggestionFormatFlg = ref(false);
// 补全建议的行号
const suggestionFlgLine = ref(1);

// 应用事件处理
const apply = () => {
  if (editor) {
    emit('on-apply', editor.getValue());
  }
};

// 提交事件处理
const submit = () => {
  if (editor) {
    emit('on-click', editor.getValue());
  }
};

const QA_WIN_NAME = 'question_with_dih';

let chatSessionId = ref('');
// AI问答功能
const dihQuestion = () => {
  // 获取当前选中文本
  if (editor) {
    const selection = editor.getSelection();
    if (selection) {
      const model = editor.getModel();
      if (model) {
        const selectedText = model.getValueInRange(selection);
        if (selectedText) {
          // 示例：打开新窗口AI对话
          // 1. 把"路由对象"解析成完整 URL
          // 2. 打开新窗口
          // 尝试获取或创建窗口
          let win = window.open('', QA_WIN_NAME);
          if (win) {
            win.opener = null;
            win.focus();
            // 优化：如果win.location.href有值，则只对msg参数进行修改后更新href
            if (win.location.href && win.location.href !== 'about:blank') {
              const prompt =
                '模块：' +
                router.currentRoute.value.path +
                '\n' +
                '配置：' +
                selectedText +
                '\n请帮我解释一下这个配置的作用';
              const routeData = router.resolve({
                name: 'service-dih',
                query: {
                  chatSessionId: chatSessionId.value,
                  msg: encodeURIComponent(prompt),
                },
              });
              win.location.href = routeData.href;
            } else {
              chatSessionId.value = generateUUID();
              const prompt =
                '模块：' +
                router.currentRoute.value.path +
                '\n' +
                '配置：' +
                selectedText +
                '\n请帮我解释一下这个配置的作用';
              const routeData = router.resolve({
                name: 'service-dih',
                query: {
                  chatSessionId: chatSessionId.value,
                  createSession: 1,
                  msg: encodeURIComponent(prompt),
                },
              });
              win.location.href = routeData.href;
            }
          }
        }
      }
    }
  }
};

// 获取提示信息的API调用
const getSuggestions = async (fullContent: string, currentLine: string): Promise<string[]> => {
  try {
    const res = await DihService.suggest({
      content: fullContent,
      current_line: currentLine,
    });
    return [res];
  } catch (error) {
    console.error('获取提示信息失败:', error);
    return ['获取提示信息失败:' + error];
  }
};

// 初始化编辑器
const init = () => {
  if (!codeEditBox.value) return;
  setupMonacoWorkers();

  monaco.languages.typescript.javascriptDefaults.setDiagnosticsOptions({
    noSemanticValidation: true,
    noSyntaxValidation: false,
  });

  monaco.languages.typescript.javascriptDefaults.setCompilerOptions({
    target: monaco.languages.typescript.ScriptTarget.ES2020,
    allowNonTsExtensions: true,
  });

  editor = monaco.editor.create(codeEditBox.value, {
    value: props.modelValue || '',
    language: props.language,
    theme: props.theme,
    ...props.options,
  });

  // 为 JSON 语言配置 Schema（延迟到 editor 初始化之后）
  if (props.language === 'json') {
    configureJsonSchema();
  }

  // 添加自定义右键菜单项
  documentationCommandId = editor.addCommand(
    0,
    () => {
      dihQuestion();
    },
    '',
  );

  // 注册右键菜单项
  editor.addAction({
    id: 'dih-question-action',
    label: 'AI问答',
    contextMenuGroupId: 'navigation',
    contextMenuOrder: 1.5,
    run: () => {
      dihQuestion();
      return undefined;
    },
  });

  // 添加Ctrl+S快捷键绑定
  saveKeyBinding = editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
    submit();
  });

  // 注册@@触发的内联提示功能
  registerInlineSuggestionProvider();

  editor.onDidChangeModelContent(() => {
    const value = editor!.getValue();
    emit('update:modelValue', value);
    emit('change', value);
  });

  emit('editor-mounted', editor);
};

// 注册内联提示提供者
const registerInlineSuggestionProvider = () => {
  // 先释放已存在的提供者
  if (inlineSuggestionProvider) {
    inlineSuggestionProvider.dispose();
  }

  inlineSuggestionProvider = monaco.languages.registerInlineCompletionsProvider(props.language, {
    provideInlineCompletions: async (model, position) => {
      const currentLine = model.getLineContent(position.lineNumber);

      const suggestionFlgLineText = model.getLineContent(suggestionFlgLine.value);
      const atIndex = suggestionFlgLineText.indexOf('@@');
      // 处理 @@ 替换逻辑
      if (atIndex !== -1 && suggestionFormatFlg.value) {
        suggestionFormatFlg.value = false;

        // 创建替换范围（@@的起始位置和结束位置）
        const range = {
          startLineNumber: suggestionFlgLine.value,
          endLineNumber: suggestionFlgLine.value,
          startColumn: atIndex + 1, // Monaco的列是从1开始的
          endColumn: atIndex + 3, // @@占2个字符
        };

        // 执行替换
        if (editor) {
          editor.executeEdits('replace-at', [
            {
              range: range,
              text: '',
            },
          ]);
        }
      }

      // 提供内联补全建议
      if (currentLine.endsWith('@@')) {
        suggestionFormatFlg.value = true;
        suggestionFlgLine.value = position.lineNumber;
        try {
          const fullContent = model.getValue();
          const currentLine = model.getLineContent(position.lineNumber);
          const suggestions = await getSuggestions(fullContent, currentLine);

          return {
            items: suggestions.map((suggestion: string, index: number) => ({
              insertText: suggestion,
              range: {
                startLineNumber: position.lineNumber,
                startColumn: position.column,
                endLineNumber: position.lineNumber,
                endColumn: position.column,
              },
              command: {
                id: 'editor.action.triggerSuggest',
                title: 'Trigger Suggest',
              },
            })),
          };
        } catch (error) {
          console.error('获取提示信息失败:', error);
          return {
            items: [],
          };
        }
      } else {
        suggestionFormatFlg.value = false;
      }

      return { items: [] };
    },
    freeInlineCompletions: () => {},
  });
};

// 配置 JSON Schema
const configureJsonSchema = () => {
  // 获取文件对应的JSON Schema
  PolicyService.schema(props.schemaType, { file_name: props.fileName })
    .then((res: Object) => {
      // 配置 JSON 语言的诊断选项
      monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
        validate: true,
        schemas: [
          {
            uri: 'http://example.com/config-schema.json',
            fileMatch: ['*'], // 匹配所有 JSON 文件
            schema: res,
          },
        ],
      });
    })
    .catch(error => {
      console.error('获取JSON Schema失败:', error);
    });
};

// 监听 fileName 变化
watch(
  () => props.fileName,
  newValue => {
    configureJsonSchema();
  },
);

// 监听 modelValue 变化
watch(
  () => props.modelValue,
  newValue => {
    if (editor) {
      const value = editor.getValue();
      if (newValue !== value) {
        editor.setValue(newValue || '');
      }
    }
  },
);

// 监听 options 变化
watch(
  () => props.options,
  newValue => {
    if (editor) {
      editor.updateOptions(newValue);
    }
  },
  { deep: true },
);

// 监听 language 变化
watch(
  () => props.language,
  newValue => {
    if (editor && editor.getModel()) {
      monaco.editor.setModelLanguage(editor.getModel()!, newValue);
      // 重新注册内联提示提供者
      registerInlineSuggestionProvider();

      // 如果语言变更为 JSON，配置 JSON Schema
      if (newValue === 'json') {
        configureJsonSchema();
      }
    }
  },
);

// 组件卸载前清理
onBeforeUnmount(() => {
  if (editor) {
    editor.dispose();
    editor = null;
  }
  documentationCommandId = null;
  saveKeyBinding = null;
  if (inlineSuggestionProvider) {
    inlineSuggestionProvider.dispose();
    inlineSuggestionProvider = null;
  }
});

// 组件挂载后初始化
onMounted(() => {
  init();
});
</script>

<style lang="scss" scoped>
.code-box-div {
  display: flex;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  flex-direction: column;
  background: var(--zv-bg-surface);
}

.editor-toolbar {
  display: flex;
  flex: 0 0 auto;
  min-width: 0;
  min-height: 68px;
  padding: 10px 14px 10px 16px;
  align-items: center;
  justify-content: space-between;
  gap: var(--zv-space-4);
  background: var(--zv-bg-surface);
  border-bottom: 1px solid var(--zv-divider);
}

.editor-file-meta {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--zv-space-3);
}

.editor-file-meta > div {
  min-width: 0;
}

.editor-file-meta strong,
.editor-file-meta small {
  display: block;
}

.editor-file-meta strong {
  overflow: hidden;
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-sm);
  font-weight: var(--zv-font-weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-file-meta small {
  margin-top: var(--zv-space-1);
  color: var(--zv-text-muted);
  font-size: 11px;
}

.editor-file-icon,
.empty-code-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
  border: 1px solid var(--zv-primary-border);
  border-radius: var(--zv-radius-md);
}

.editor-file-icon {
  width: 36px;
  height: 36px;
  font-size: var(--zv-font-size-lg);
}

.codeEditBox {
  flex: 1;
  width: v-bind(width);
  min-width: 0;
  min-height: 0;
  height: auto;
  overflow: hidden;
}

.no-file-tip {
  display: flex;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: var(--zv-space-8);
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-md);
  text-align: center;
  background: var(--zv-bg-surface);
}

.empty-code-icon {
  width: 58px;
  height: 58px;
  margin-bottom: var(--zv-space-4);
  font-size: var(--zv-font-size-2xl);
  box-shadow: var(--zv-shadow-2);
}

.no-file-tip strong {
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-lg);
  font-weight: var(--zv-font-weight-semibold);
}

.no-file-tip p {
  max-width: 430px;
  margin: var(--zv-space-2) 0 0;
  line-height: var(--zv-line-height-normal);
}

@media (max-width: 720px) {
  .editor-toolbar {
    min-height: 58px;
    padding: 8px 10px;
  }

  .editor-file-meta small {
    display: none;
  }

  .editor-toolbar :deep(.el-button) {
    min-height: 32px;
    padding-inline: 10px;
  }
}

@media (max-width: 460px) {
  .editor-file-icon {
    display: none;
  }

  .editor-toolbar {
    gap: var(--zv-space-2);
  }

  .editor-file-meta strong {
    max-width: 120px;
  }
}
</style>
