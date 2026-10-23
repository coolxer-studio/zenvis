<template>
  <el-dialog
    v-model="visible"
    width="min(560px, 92vw)"
    class="agent-run-user-input-dialog"
    append-to-body
    :close-on-click-modal="false"
    data-testid="agent-run-user-input-dialog"
    @opened="focusInput"
    @closed="emit('closed')"
  >
    <template #header>
      <div class="user-input-heading">
        <span class="heading-mark" aria-hidden="true">
          <el-icon><ChatDotRound /></el-icon>
        </span>
        <div>
          <strong>任务需要补充信息</strong>
          <small>提交后任务会带上该信息继续执行</small>
        </div>
      </div>
    </template>

    <div class="user-input-body">
      <div class="question-block">
        <span class="question-label">任务说明</span>
        <p>{{ question || '请补充任务继续执行所需的信息。' }}</p>
      </div>
      <el-input
        ref="inputRef"
        v-model="answer"
        type="textarea"
        :rows="5"
        :maxlength="8000"
        show-word-limit
        resize="vertical"
        placeholder="请输入需要补充的信息"
        @keydown.meta.enter.prevent="submit"
        @keydown.ctrl.enter.prevent="submit"
      />
      <p class="input-hint">支持多行输入。可使用 Ctrl/⌘ + Enter 快速提交。</p>
    </div>

    <template #footer>
      <div class="user-input-footer">
        <el-button :disabled="submitting" @click="visible = false">稍后处理</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交并继续</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { ChatDotRound } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

defineOptions({ name: 'AgentRunUserInputDialog' });

const props = withDefaults(defineProps<{
  modelValue: boolean;
  question?: string;
  submitting?: boolean;
}>(), {
  question: '',
  submitting: false,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'submit', message: string): void;
  (e: 'closed'): void;
}>();

const answer = ref('');
const inputRef = ref<{ focus?: () => void } | null>(null);
const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
});

watch(() => props.question, () => {
  answer.value = '';
});

watch(() => props.modelValue, value => {
  if (value) {
    nextTick(() => focusInput());
  }
});

const focusInput = () => {
  inputRef.value?.focus?.();
};

const submit = () => {
  const message = answer.value.trim();
  if (!message) {
    ElMessage.warning('请输入需要补充的信息');
    return;
  }
  emit('submit', message);
};
</script>

<style lang="scss" scoped>
.user-input-heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.heading-mark {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  color: var(--el-color-warning-dark-2);
  place-items: center;
  background: color-mix(in srgb, var(--el-color-warning) 14%, var(--zv-bg-card));
  border-radius: 9px;
}

.user-input-heading > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-input-heading strong {
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-sm);
}

.user-input-heading small {
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}

.user-input-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-block {
  padding: 10px 12px;
  background: var(--zv-bg-subtle);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-md);
}

.question-label {
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}

.question-block p {
  max-height: 160px;
  margin: 5px 0 0;
  overflow: auto;
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-sm);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.input-hint {
  margin: -4px 0 0;
  color: var(--zv-text-muted);
  font-size: 11px;
}

.user-input-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
