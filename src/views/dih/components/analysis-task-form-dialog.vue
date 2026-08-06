<template>
  <el-dialog
    :model-value="visible"
    :title="task ? '编辑AI分析任务' : '创建AI分析任务'"
    width="760px"
    destroy-on-close
    append-to-body
    @closed="resetForm"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form
      ref="formRef"
      v-loading="submitting"
      :model="form"
      :rules="rules"
      label-width="116px"
      class="analysis-task-form"
    >
      <el-form-item label="任务名称" prop="name">
        <el-input v-model="form.name" maxlength="100" clearable placeholder="请输入任务名称" />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="模型" prop="model">
            <el-select
              v-model="form.model"
              filterable
              placeholder="请选择可用模型"
              class="full-width"
            >
              <el-option
                v-for="item in normalizedModels"
                :key="item.model"
                :label="item.desc ? `${item.model}（${item.desc}）` : item.model"
                :value="item.model"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="优先级" prop="priority">
            <el-input-number
              v-model="form.priority"
              :min="0"
              :max="2147483647"
              controls-position="right"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="执行方式" prop="scheduleType">
        <el-radio-group v-model="form.scheduleType">
          <el-radio value="ONCE">单次执行</el-radio>
          <el-radio value="CRON">周期执行</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="form.scheduleType === 'ONCE'" label="计划执行时间" prop="scheduledTime">
        <el-date-picker
          v-model="form.scheduledTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm:ss"
          placeholder="留空表示立即进入队列"
          clearable
          class="full-width"
        />
      </el-form-item>

      <el-form-item v-else label="执行周期" prop="cronExpression">
        <el-select
          v-model="form.cronExpression"
          filterable
          allow-create
          default-first-option
          placeholder="选择常用周期或输入 6 段 Cron 表达式"
          class="full-width"
        >
          <el-option
            v-for="item in cronOptions"
            :key="item.value"
            :label="`${item.label}（${item.value}）`"
            :value="item.value"
          />
        </el-select>
        <div class="form-help">
          使用 Spring Cron 格式：秒 分 时 日 月
          周。创建后将在下一次匹配时间执行，成功或失败后继续下一周期。
        </div>
      </el-form-item>

      <el-form-item label="MCP审批模式" prop="approvalMode">
        <el-radio-group v-model="form.approvalMode">
          <el-radio value="MANUAL">需要人工审批</el-radio>
          <el-radio value="AUTO">无需人工审批</el-radio>
        </el-radio-group>
        <div class="form-help">无需人工审批会自动批准全局 ASK 工具，但不会覆盖 DENY 策略。</div>
      </el-form-item>

      <el-form-item label="使用 Skill" prop="skillIds">
        <el-select
          v-model="form.skillIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          clearable
          placeholder="可选，仅显示已启用 Skill"
          class="full-width"
        >
          <el-option
            v-for="item in skillOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          >
            <div class="skill-option">
              <span>{{ item.label }}</span>
              <small>{{ item.description }}</small>
            </div>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="描述说明" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="请输入任务说明（可选）"
        />
      </el-form-item>

      <el-form-item label="分析提示词" prop="prompt">
        <el-input
          v-model="form.prompt"
          type="textarea"
          :rows="9"
          resize="vertical"
          placeholder="请输入希望 Agent 执行的分析指令，支持 Markdown"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitForm">
        {{ task ? '保存修改' : '创建并入队' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { AnalysisTaskService } from '@/service/api';
import type {
  TAnalysisTask,
  TAnalysisTaskForm,
  TAnalysisTaskModelOption,
  TAnalysisTaskSkillOption,
} from '@/types/type-analysis-task';

defineOptions({ name: 'AnalysisTaskFormDialog' });

type Props = {
  visible: boolean;
  task: TAnalysisTask | null;
  modelOptions: TAnalysisTaskModelOption[];
  skillOptions: TAnalysisTaskSkillOption[];
};

const props = defineProps<Props>();
const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void;
  (event: 'saved'): void;
}>();

const emptyForm = (): TAnalysisTaskForm => ({
  name: '',
  description: '',
  model: 'auto',
  prompt: '',
  priority: 0,
  scheduleType: 'ONCE',
  scheduledTime: '',
  cronExpression: '',
  approvalMode: 'MANUAL',
  skillIds: [],
});

const formRef = ref<FormInstance>();
const form = reactive<TAnalysisTaskForm>(emptyForm());
const submitting = ref(false);

const cronOptions = [
  { label: '每10分钟', value: '0 */10 * * * *' },
  { label: '每小时整点', value: '0 0 * * * *' },
  { label: '每天09:00', value: '0 0 9 * * *' },
  { label: '每周一09:00', value: '0 0 9 * * MON' },
  { label: '每月1日09:00', value: '0 0 9 1 * *' },
];

const rules: FormRules<TAnalysisTaskForm> = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  model: [{ required: true, message: '请选择模型', trigger: 'change' }],
  approvalMode: [{ required: true, message: '请选择MCP审批模式', trigger: 'change' }],
  cronExpression: [
    {
      validator: (_rule, value, callback) => {
        if (form.scheduleType !== 'CRON') return callback();
        const fields = String(value || '')
          .trim()
          .split(/\s+/);
        return fields.length === 6
          ? callback()
          : callback(new Error('请输入包含秒的 6 段 Cron 表达式'));
      },
      trigger: ['blur', 'change'],
    },
  ],
  prompt: [{ required: true, message: '请输入分析提示词', trigger: 'blur' }],
};

const normalizedModels = computed<TAnalysisTaskModelOption[]>(() => {
  const models = props.modelOptions.filter(item => item.model);
  if (models.some(item => item.model === 'auto')) return models;
  return [{ model: 'auto', desc: '系统自动选择' }, ...models];
});

const fillForm = () => {
  const task = props.task;
  Object.assign(
    form,
    task
      ? {
          name: task.name,
          description: task.description,
          model: task.model || 'auto',
          prompt: task.prompt,
          priority: task.priority,
          scheduleType: task.cronExpression ? 'CRON' : 'ONCE',
          scheduledTime: task.scheduledTime,
          cronExpression: task.cronExpression,
          approvalMode: task.approvalMode,
          skillIds: [...task.skillIds],
        }
      : emptyForm(),
  );
  nextTick(() => formRef.value?.clearValidate());
};

const resetForm = () => {
  Object.assign(form, emptyForm());
  formRef.value?.clearValidate();
};

const submitForm = async () => {
  if (!formRef.value || submitting.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    if (props.task) {
      await AnalysisTaskService.update(props.task.id, form);
      ElMessage.success('AI分析任务已更新');
    } else {
      await AnalysisTaskService.create(form);
      ElMessage.success('AI分析任务已创建并进入队列');
    }
    emit('update:visible', false);
    emit('saved');
  } catch (error) {
    console.error(`保存AI分析任务失败:`, error);
  } finally {
    submitting.value = false;
  }
};

watch(
  () => props.visible,
  visible => {
    if (visible) fillForm();
  },
);
</script>

<style scoped lang="scss">
.analysis-task-form {
  padding-right: 12px;
}

.full-width {
  width: 100%;
}

.form-help {
  width: 100%;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.skill-option {
  display: flex;
  justify-content: space-between;
  gap: 20px;

  small {
    max-width: 360px;
    overflow: hidden;
    color: var(--el-text-color-secondary);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

:deep(.el-input-number) {
  width: 100%;
}
</style>
