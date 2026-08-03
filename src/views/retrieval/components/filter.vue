<template>
  <div class="filter-div">
    <div class="filter-header">
      <span>{{ activeRuleName || '搜索' }}</span>
      <el-button type="primary" :disabled="!canExecute" @click="emitSave">
        {{ activeRuleName ? '更新' : '保存为' }}
      </el-button>
    </div>

    <el-alert v-if="issues.length" class="issue-alert" type="warning" :closable="false">
      <template #title>当前过滤器存在 {{ issues.length }} 个失效项，修复后才能执行或保存</template>
      <div v-for="issue in issues" :key="issue.code + ':' + issue.scope + ':' + issue.attribute" class="issue-row">
        <span>{{ issue.message }}</span>
        <el-button
          v-if="issue.attribute && (issue.scope === 'display' || (issue.scope === 'criteria' && draft.type === 'normal'))"
          link
          type="warning"
          @click="emit('remove-issue', issue)"
        >
          移除此项
        </el-button>
      </div>
    </el-alert>

    <div class="toolbar">
      <span>实体：</span>
      <el-select v-model="draft.entity" class="entity-select" @change="handleEntityChange">
        <el-option v-for="entity in entities" :key="entity.name" :value="entity.name" :label="entity.label" />
      </el-select>
      <el-segmented v-model="draft.type" :options="typeOptions" @change="handleTypeChange" />
      <el-button type="primary" :loading="loading" :disabled="!canExecute" @click="emitSearch">
        <el-icon><Search /></el-icon>
        搜索
      </el-button>
    </div>

    <template v-if="draft.type === 'advanced'">
      <el-input
        v-model="draft.sql"
        class="advanced-input"
        type="textarea"
        :rows="3"
        placeholder="输入受限 where 表达式，例如：attack_state > 1 and src_ip like '%10.0%'"
        @input="notifyChange"
      />
    </template>

    <template v-else>
      <div v-if="draft.criteria_list.length > 1" class="logic-row">
        <span>条件关系：</span>
        <el-segmented v-model="draft.criteria_logic" :options="logicOptions" @change="notifyChange" />
      </div>

      <div v-for="(criterion, index) in draft.criteria_list" :key="criterion.key" class="criteria-row">
        <el-select
          v-model="criterion.attribute"
          class="attribute-select"
          placeholder="字段"
          @change="handleAttributeChange(criterion)"
        >
          <el-option
            v-for="attribute in attributeOptions(criterion.attribute)"
            :key="attribute.name"
            :value="attribute.name"
            :label="attribute.label"
          />
        </el-select>
        <el-select
          v-model="criterion.operator"
          class="operator-select"
          placeholder="操作符"
          @change="handleOperatorChange(criterion)"
        >
          <el-option
            v-for="operator in operatorOptions(criterion.attribute)"
            :key="operator.name"
            :value="operator.name"
            :label="operator.label"
          />
        </el-select>

        <template v-if="!isValueless(criterion.operator)">
          <template v-if="criterion.operator === 'between'">
            <el-date-picker
              v-if="isDateOnlyAttribute(criterion.attribute)"
              v-model="criterion.value_list"
              type="daterange"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              @change="value => syncRangeValue(criterion, value)"
            />
            <el-date-picker
              v-else-if="isDateTimeAttribute(criterion.attribute)"
              v-model="criterion.value_list"
              type="datetimerange"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              range-separator="至"
              start-placeholder="开始日期时间"
              end-placeholder="结束日期时间"
              @change="value => syncRangeValue(criterion, value)"
            />
            <div v-else class="range-inputs">
              <el-input
                v-model="criterion.value_list[0]"
                :type="isNumberAttribute(criterion.attribute) ? 'number' : 'text'"
                :inputmode="isNumberAttribute(criterion.attribute) ? 'decimal' : 'text'"
                placeholder="起始值"
                @input="notifyChange"
              />
              <span>至</span>
              <el-input
                v-model="criterion.value_list[1]"
                :type="isNumberAttribute(criterion.attribute) ? 'number' : 'text'"
                :inputmode="isNumberAttribute(criterion.attribute) ? 'decimal' : 'text'"
                placeholder="结束值"
                @input="notifyChange"
              />
            </div>
          </template>
          <el-input
            v-else-if="criterion.operator === 'in'"
            v-model="criterion.value_text"
            class="criteria-value-input"
            type="textarea"
            :rows="2"
            placeholder="每行一个值"
            @input="value => syncValueText(criterion, value)"
          />
          <el-date-picker
            v-else-if="isDateOnlyAttribute(criterion.attribute)"
            v-model="criterion.value_text"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            @change="syncValueText(criterion)"
          />
          <el-date-picker
            v-else-if="isDateTimeAttribute(criterion.attribute)"
            v-model="criterion.value_text"
            type="datetime"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择日期时间"
            @change="syncValueText(criterion)"
          />
          <el-autocomplete
            v-else-if="supportsAutoComplete(criterion)"
            v-model="criterion.value_text"
            class="criteria-value-input"
            :fetch-suggestions="(text, callback) => fetchSuggestions(criterion, text, callback)"
            value-key="label"
            :trigger-on-focus="false"
            placeholder="输入值"
            @input="value => syncValueText(criterion, value)"
            @select="option => handleSuggestionSelect(criterion, option)"
          />
          <el-input
            v-else
            v-model="criterion.value_text"
            class="criteria-value-input"
            :type="isNumberAttribute(criterion.attribute) ? 'number' : 'text'"
            :inputmode="isNumberAttribute(criterion.attribute) ? 'decimal' : 'text'"
            placeholder="输入值"
            @input="value => syncValueText(criterion, value)"
          />
        </template>
        <el-button text type="danger" @click="removeCriterion(index)">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
      <el-button class="add-button" :disabled="draft.criteria_list.length >= 50" @click="addCriterion">
        添加条件
      </el-button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import { Close, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { RetrievalService } from '@/service/api';
import type {
  AutoCompleteOption,
  OperatorItem,
  RetrievalRuleIssue,
  RetrievalSearchRequest,
  RetrievalType,
  TAttributeListResponse,
  TCriteriaList,
  TEntityListResponse,
} from '@/types/type-retrieval';
import {
  isValuelessOperator,
  valuesAfterOperatorChange,
  valuesForRequest,
} from './filter-criterion-values';
import { advancedSqlValue } from './filter-query';

type EditableCriterion = TCriteriaList & {
  key: number;
  value_text: string;
};

type DraftState = {
  entity: string;
  type: RetrievalType;
  criteria_logic: 'and' | 'or';
  criteria_list: EditableCriterion[];
  sql: string;
};

const props = withDefaults(
  defineProps<{
    activeRuleName?: string;
    modelValue?: RetrievalSearchRequest;
    entities?: TEntityListResponse[];
    attributes?: TAttributeListResponse[];
    issues?: RetrievalRuleIssue[];
    loading?: boolean;
  }>(),
  {
    activeRuleName: '',
    modelValue: () => ({}),
    entities: () => [],
    attributes: () => [],
    issues: () => [],
    loading: false,
  },
);

const emit = defineEmits<{
  (event: 'update:modelValue', value: RetrievalSearchRequest): void;
  (event: 'entity-change', value: string): void;
  (event: 'search', value: RetrievalSearchRequest): void;
  (event: 'save', value: RetrievalSearchRequest): void;
  (event: 'remove-issue', value: RetrievalRuleIssue): void;
}>();

let criterionKey = 0;
let applyingProps = false;
const draft = reactive<DraftState>({
  entity: '',
  type: 'normal',
  criteria_logic: 'and',
  criteria_list: [],
  sql: '',
});

const typeOptions = [
  { label: '简单', value: 'normal' },
  { label: '高级', value: 'advanced' },
];
const logicOptions = [
  { label: 'AND', value: 'and' },
  { label: 'OR', value: 'or' },
];

const canExecute = computed(() => {
  const displayFields = props.modelValue.display_list?.[0]?.attribute_list || [];
  return Boolean(draft.entity && displayFields.length && !props.issues.length && isDraftComplete());
});

watch(
  () => props.modelValue,
  value => {
    // The parent writes our own update back through v-model. Rebuilding the
    // criteria array here would remount the active input on every keystroke.
    if (modelMatchesDraft(value)) {
      return;
    }
    applyingProps = true;
    draft.entity = value.entity || '';
    draft.type = value.type || 'normal';
    draft.criteria_logic = value.criteria_logic === 'or' ? 'or' : 'and';
    draft.sql = value.sql || '';
    draft.criteria_list = (value.criteria_list || []).map(toEditableCriterion);
    applyingProps = false;
  },
  { deep: true, immediate: true },
);

function modelMatchesDraft(value: RetrievalSearchRequest): boolean {
  if (
    (value.entity || '') !== draft.entity
    || (value.type || 'normal') !== draft.type
    || (value.criteria_logic === 'or' ? 'or' : 'and') !== draft.criteria_logic
    || (value.sql || '') !== draft.sql
  ) {
    return false;
  }

  const modelCriteria = value.criteria_list || [];
  if (modelCriteria.length !== draft.criteria_list.length) {
    return false;
  }

  return modelCriteria.every((criterion, index) => {
    const editableCriterion = draft.criteria_list[index];
    const modelValues = criterion.value_list || [];
    const draftValues = editableCriterion?.value_list || [];
    return Boolean(
      editableCriterion
      && criterion.attribute === editableCriterion.attribute
      && criterion.operator === editableCriterion.operator
      && (isValueless(criterion.operator)
        || (modelValues.length === draftValues.length
          && modelValues.every((item, valueIndex) => item === draftValues[valueIndex]))),
    );
  });
}

function toEditableCriterion(criterion: TCriteriaList): EditableCriterion {
  return {
    attribute: criterion.attribute,
    operator: criterion.operator,
    value_list: [...(criterion.value_list || [])],
    value_text: criterion.operator === 'in' ? (criterion.value_list || []).join('\n') : criterion.value_list?.[0] || '',
    key: ++criterionKey,
  };
}

function buildQuery(normalizeAdvancedSql = false): RetrievalSearchRequest {
  return {
    ...props.modelValue,
    entity: draft.entity,
    type: draft.type,
    criteria_logic: draft.criteria_logic,
    criteria_list:
      draft.type === 'normal'
        ? draft.criteria_list.map(({ attribute, operator, value_list }) => ({
            attribute,
            operator,
            value_list: valuesForRequest(operator, value_list),
          }))
        : [],
    sql: advancedSqlValue(draft.type, draft.sql, normalizeAdvancedSql),
  };
}

function notifyChange() {
  if (!applyingProps) {
    emit('update:modelValue', buildQuery());
  }
}

function handleEntityChange(entity: string) {
  emit('entity-change', entity);
}

function handleTypeChange() {
  notifyChange();
}

function attributeOptions(selected: string): TAttributeListResponse[] {
  if (!selected || props.attributes.some(attribute => attribute.name === selected)) {
    return props.attributes;
  }
  return [
    ...props.attributes,
    { name: selected, label: `${selected}（已失效）`, operator_list: [] },
  ];
}

function operatorOptions(attributeName: string): OperatorItem[] {
  return props.attributes.find(attribute => attribute.name === attributeName)?.operator_list || [];
}

type SearchInputType = 'date' | 'datetime' | 'number' | 'text';

function searchInputType(attributeName: string): SearchInputType {
  const searchType = props.attributes.find(attribute => attribute.name === attributeName)?.search_type;
  return searchType === 'date' || searchType === 'datetime' || searchType === 'number'
    ? searchType
    : 'text';
}

function isDateOnlyAttribute(attributeName: string): boolean {
  return searchInputType(attributeName) === 'date';
}

function isDateTimeAttribute(attributeName: string): boolean {
  return searchInputType(attributeName) === 'datetime';
}

function isNumberAttribute(attributeName: string): boolean {
  return searchInputType(attributeName) === 'number';
}

function isValueless(operator: string): boolean {
  return isValuelessOperator(operator);
}

function supportsAutoComplete(criterion: EditableCriterion): boolean {
  const attribute = props.attributes.find(item => item.name === criterion.attribute);
  return Boolean(
    attribute?.auto_complete
    && searchInputType(criterion.attribute) === 'text'
    && !['between', 'in', 'isnull', 'isnotnull'].includes(criterion.operator),
  );
}

function addCriterion() {
  const attribute = props.attributes[0];
  if (!attribute) {
    ElMessage.warning('当前实体没有可检索字段');
    return;
  }
  const operator = attribute.operator_list?.[0]?.name || '';
  draft.criteria_list.push({
    key: ++criterionKey,
    attribute: attribute.name,
    operator,
    value_list: isValueless(operator) ? [] : [''],
    value_text: '',
  });
  notifyChange();
}

function removeCriterion(index: number) {
  draft.criteria_list.splice(index, 1);
  notifyChange();
}

function handleAttributeChange(criterion: EditableCriterion) {
  criterion.operator = operatorOptions(criterion.attribute)[0]?.name || '';
  criterion.value_list = isValueless(criterion.operator) ? [] : [''];
  criterion.value_text = '';
  notifyChange();
}

function handleOperatorChange(criterion: EditableCriterion) {
  const values = valuesAfterOperatorChange(criterion.operator, criterion);
  criterion.value_list = values.value_list;
  criterion.value_text = values.value_text;
  notifyChange();
}

function syncValueText(criterion: EditableCriterion, inputValue?: unknown) {
  if (typeof inputValue === 'string' || typeof inputValue === 'number') {
    criterion.value_text = String(inputValue);
  }
  criterion.value_list = criterion.operator === 'in'
    ? criterion.value_text.split(/\r?\n|,/).map(value => value.trim()).filter(Boolean)
    : [criterion.value_text];
  notifyChange();
}

function syncRangeValue(criterion: EditableCriterion, inputValue: unknown) {
  criterion.value_list = Array.isArray(inputValue)
    ? inputValue.map(value => String(value ?? ''))
    : ['', ''];
  criterion.value_text = criterion.value_list[0] || '';
  notifyChange();
}

function fetchSuggestions(
  criterion: EditableCriterion,
  text: string,
  callback: (items: AutoCompleteOption[]) => void,
) {
  if (!draft.entity || !text.trim()) {
    callback([]);
    return;
  }
  RetrievalService.autoComplete({
    entity: draft.entity,
    attribute: criterion.attribute,
    term: text.trim(),
  })
    .then(result => callback(result.options || []))
    .catch(() => callback([]));
}

function handleSuggestionSelect(criterion: EditableCriterion, option: AutoCompleteOption) {
  criterion.value_text = option.value;
  syncValueText(criterion);
}

function isDraftComplete(): boolean {
  if (draft.type === 'advanced') {
    return Boolean(draft.sql.trim());
  }
  return draft.criteria_list.every(criterion => {
    if (!criterion.attribute || !criterion.operator) return false;
    if (isValueless(criterion.operator)) return true;
    if (criterion.operator === 'between') return Boolean(criterion.value_list[0] && criterion.value_list[1]);
    return Boolean(criterion.value_list.length && criterion.value_list.every(Boolean));
  });
}

function emitSearch() {
  if (!canExecute.value) {
    ElMessage.warning('请先修复失效项并完善检索配置');
    return;
  }
  emit('search', buildQuery(true));
}

function emitSave() {
  if (!canExecute.value) {
    ElMessage.warning('请先修复失效项并完善检索配置');
    return;
  }
  emit('save', buildQuery(true));
}
</script>

<style lang="scss" scoped>
.filter-div {
  margin-bottom: 20px;
}

.filter-header,
.toolbar,
.criteria-row,
.logic-row,
.range-inputs {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-header {
  margin-bottom: 16px;
  font-size: 16px;
}

.toolbar {
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.entity-select {
  width: 180px;
}

.issue-alert,
.advanced-input,
.logic-row,
.criteria-row {
  margin-bottom: 12px;
}

.issue-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.criteria-row {
  align-items: flex-start;
}

.attribute-select {
  width: 200px;
}

.operator-select {
  width: 150px;
}

.criteria-row :deep(.el-input),
.criteria-row :deep(.el-textarea),
.range-inputs {
  width: 360px;
}

.criteria-value-input :deep(.el-input__inner),
.criteria-value-input :deep(.el-textarea__inner) {
  color: var(--el-text-color-regular, #606266);
  -webkit-text-fill-color: currentcolor;
}

.range-inputs :deep(.el-input) {
  width: 160px;
}

.add-button {
  margin-top: 4px;
}
</style>
