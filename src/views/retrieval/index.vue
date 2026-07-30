<template>
  <div id="data-list" class="main-content data-retrieval">
    <el-icon class="double-right-outlined" @click="visible = true"><ArrowRight /></el-icon>
    <el-drawer
      v-model="visible"
      direction="ltr"
      :closable="false"
      :with-header="false"
      :modal-append-to-body="false"
      modal-class="rule-drawer-modal"
      class="rule-drawer"
    >
      <el-icon class="double-left-outlined" @click="visible = false"><ArrowLeft /></el-icon>
      <div class="rule-div create-rule-option" @click="enterNewRule">新建过滤器</div>
      <div class="rule-title">规则列表</div>
      <div
        v-for="item in ruleList"
        :key="item.id"
        class="rule-div"
        :class="{ 'active-rule': item.id === activeRule }"
      >
        <div class="rule-name" @click="loadRule(item)">
          <span>{{ item.name }}</span>
          <el-tag v-if="item.status === 'invalid'" type="warning" size="small">
            失效 {{ item.issue_count }}
          </el-tag>
        </div>
        <el-icon class="delete-icon" @click.stop="deleteRule(item)"><Close /></el-icon>
      </div>
    </el-drawer>

    <DataFilter
      v-model="currentFilter"
      :active-rule-name="activeRuleName"
      :entities="entityList"
      :attributes="attributeList"
      :issues="remainingIssues"
      :loading="tableState.loading"
      @entity-change="changeEntity"
      @search="search"
      @save="showSaveDialog"
      @remove-issue="removeInvalidItem"
    />

    <DataList
      :state="tableState"
      @on-change="onTableChange"
      @on-display="changeDisplayColumns"
      @on-click="showJsonData"
    />

    <el-dialog v-model="visibleJson" class="json-data-model" width="800px" title="数据查看">
      <json-viewer :value="deviceData" :expand-depth="1" copyable boxed sort theme="my-json-theme" />
    </el-dialog>

    <el-dialog v-model="visibleSave" title="保存过滤器" @closed="formRef?.resetFields()">
      <el-form ref="formRef" :model="formRule" :rules="formRules" label-width="120px">
        <el-form-item label="过滤器名称" prop="name">
          <el-input v-model="formRule.name" placeholder="请输入过滤器名称" />
          <span class="rule-hint">为此过滤器输入一个名称</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visibleSave = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import axios from 'axios';
import { ArrowLeft, ArrowRight, Close } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { JsonViewer } from 'vue3-json-viewer';
import 'vue3-json-viewer/dist/vue3-json-viewer.css';
import { RetrievalService } from '@/service/api';
import type {
  RetrievalTableChange,
  RetrievalTableColumn,
  RetrievalTableState,
  RetrievalRuleConfig,
  RetrievalRuleIssue,
  RetrievalRuleListItem,
  RetrievalSearchRequest,
  TAttributeListResponse,
  TEntityListResponse,
} from '@/types/type-retrieval';
import { ls } from '@u/local-storage';
import DataFilter from './components/filter.vue';
import DataList from './components/table.vue';

const visible = ref(false);
const visibleSave = ref(false);
const visibleJson = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
const formRule = reactive({ name: '' });
const formRules: FormRules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] };
const RETRIEVAL_ENTITY_CACHE_KEY = '__retrieval_entity__';

const ruleList = ref<RetrievalRuleListItem[]>([]);
const activeRule = ref(0);
const activeRuleName = ref('');
const entityList = ref<TEntityListResponse[]>([]);
const attributeList = ref<TAttributeListResponse[]>([]);
const currentFilter = ref<RetrievalSearchRequest>({
  type: 'normal',
  criteria_logic: 'and',
  criteria_list: [],
  display_list: [],
});
const currentSorter = ref<Pick<RetrievalSearchRequest, 'sort_by' | 'order'>>();
const loadedIssues = ref<RetrievalRuleIssue[]>([]);
const loadedConfig = ref<RetrievalSearchRequest>();
const pendingSave = ref<RetrievalSearchRequest>();
const deviceData = ref<unknown>({});

let loadGeneration = 0;
let dataRequestId = 0;
let dataAbortController: AbortController | undefined;
let loadAbortController: AbortController | undefined;

const tableState = reactive<RetrievalTableState>({
  loading: false,
  sourceColumns: [],
  disabledTitles: [],
  selectedCol: [],
  selectedKeyCol: [],
  columns: [],
  data: [],
  width: 0,
  minWidth: 80,
  pagination: {
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '50', '100'],
    showQuickJumper: true,
  },
});

const remainingIssues = computed(() =>
  loadedIssues.value.filter(issue => {
    const config = currentFilter.value;
    if (issue.code === 'LEGACY_SQL_DISABLED') {
      return config.sql === loadedConfig.value?.sql;
    }
    if (issue.code === 'TYPE_INVALID') {
      return false;
    }
    if (issue.code === 'DISPLAY_EMPTY') {
      return !(config.display_list?.[0]?.attribute_list?.length);
    }
    if (issue.code === 'DISPLAY_FIELD_MISSING') {
      return config.display_list?.some(display => display.attribute_list.includes(issue.attribute || ''));
    }
    if (issue.code === 'CRITERIA_FIELD_MISSING') {
      if (config.type === 'advanced') {
        return config.sql === loadedConfig.value?.sql;
      }
      return config.criteria_list?.some(criteria => criteria.attribute === issue.attribute);
    }
    if (issue.code === 'OPERATOR_MISSING') {
      if (config.type === 'advanced') return config.sql === loadedConfig.value?.sql;
      const currentCriterion = config.criteria_list?.find(criteria => criteria.attribute === issue.attribute);
      const loadedCriterion = loadedConfig.value?.criteria_list?.find(criteria => criteria.attribute === issue.attribute);
      return Boolean(currentCriterion && currentCriterion.operator === loadedCriterion?.operator);
    }
    if (issue.code === 'INVALID_EXPRESSION') {
      return config.sql === loadedConfig.value?.sql;
    }
    if (issue.code === 'ENTITY_MISSING') {
      return !entityList.value.some(entity => entity.name === config.entity);
    }
    if (issue.code === 'DISPLAY_ENTITY_MISMATCH') {
      return config.display_list?.some(display => display.entity !== config.entity);
    }
    if (issue.code === 'RULE_INVALID') return JSON.stringify(config) === JSON.stringify(loadedConfig.value);
    return true;
  }),
);

function clearDataState() {
  dataAbortController?.abort();
  dataRequestId++;
  tableState.loading = false;
  tableState.data = [];
  tableState.pagination.current = 1;
  tableState.pagination.total = 0;
  tableState.entity = undefined;
  tableState.sourceColumns = [];
  tableState.selectedCol = [];
  tableState.selectedKeyCol = [];
  attributeList.value = [];
  currentSorter.value = undefined;
}

async function refreshRules() {
  const response = await RetrievalService.getRule();
  ruleList.value = response.datalist || [];
}

async function enterNewRule() {
  const generation = ++loadGeneration;
  loadAbortController?.abort();
  const loadController = new AbortController();
  loadAbortController = loadController;
  clearDataState();
  activeRule.value = 0;
  activeRuleName.value = '';
  loadedIssues.value = [];
  loadedConfig.value = undefined;
  ls.remove('__rule__');
  visible.value = false;
  try {
    const response = await RetrievalService.getEntity({}, { signal: loadController.signal, silent: true });
    if (generation !== loadGeneration) return;
    entityList.value = response.entity_list || [];
    const cachedEntity = ls.get(RETRIEVAL_ENTITY_CACHE_KEY);
    const selectedEntity = typeof cachedEntity === 'string'
      && entityList.value.some(item => item.name === cachedEntity)
      ? cachedEntity
      : response.selected_entity?.find(name => entityList.value.some(item => item.name === name));
    const entity = selectedEntity || entityList.value[0]?.name;
    if (!entity) {
      ls.remove(RETRIEVAL_ENTITY_CACHE_KEY);
      currentFilter.value = { type: 'normal', criteria_logic: 'and', criteria_list: [], display_list: [] };
      return;
    }
    await loadEntity(entity, generation, true, loadController.signal);
  } catch (error) {
    if (axios.isCancel(error)) return;
    if (generation === loadGeneration) clearDataState();
  }
}

async function loadEntity(entity: string, generation: number, executeAfterLoad: boolean, signal: AbortSignal) {
  clearDataState();
  ls.set(RETRIEVAL_ENTITY_CACHE_KEY, entity);
  currentFilter.value = {
    type: 'normal',
    entity,
    criteria_logic: 'and',
    criteria_list: [],
    display_list: [],
  };
  const response = await RetrievalService.getCol({ entity }, { signal, silent: true });
  if (generation !== loadGeneration) return;
  attributeList.value = response.attribute_list || [];
  const selected = response.select_attribute_list?.map(item => item.name) || [];
  const selectedNames = selected.length ? selected : attributeList.value.slice(0, 1).map(item => item.name);
  currentFilter.value = {
    ...currentFilter.value,
    display_list: [{ entity, attribute_list: selectedNames }],
  };
  applyColumns(entity, attributeList.value, selectedNames);
  if (executeAfterLoad && selectedNames.length) await getData();
}

async function changeEntity(entity: string) {
  const generation = ++loadGeneration;
  loadAbortController?.abort();
  const loadController = new AbortController();
  loadAbortController = loadController;
  loadedIssues.value = [];
  loadedConfig.value = undefined;
  try {
    await loadEntity(entity, generation, true, loadController.signal);
  } catch (error) {
    if (axios.isCancel(error)) return;
    if (generation === loadGeneration) clearDataState();
  }
}

async function loadRule(item: RetrievalRuleListItem, fromStartup = false) {
  const generation = ++loadGeneration;
  loadAbortController?.abort();
  const loadController = new AbortController();
  loadAbortController = loadController;
  clearDataState();
  activeRule.value = 0;
  activeRuleName.value = '';
  loadedIssues.value = [];
  loadedConfig.value = undefined;
  currentFilter.value = { type: 'normal', criteria_logic: 'and', criteria_list: [], display_list: [] };
  ls.remove('__rule__');
  visible.value = false;
  try {
    const detail = await RetrievalService.ruleDetail(
      { id: item.id },
      { signal: loadController.signal, silent: true },
    );
    if (generation !== loadGeneration) return;
    if (fromStartup && detail.status === 'invalid') {
      ls.remove('__rule__');
      await enterNewRule();
      return;
    }
    activeRule.value = detail.id;
    activeRuleName.value = detail.name;
    formRule.name = detail.name;
    entityList.value = detail.entity_list || [];
    attributeList.value = detail.attribute_list || [];
    loadedIssues.value = detail.issues || [];
    const editableConfig = toEditableConfig(detail.config);
    loadedConfig.value = cloneConfig(editableConfig);
    currentFilter.value = cloneConfig(editableConfig);
    ls.set('__rule__', { ruleId: detail.id, ruleName: detail.name });
    ls.set(RETRIEVAL_ENTITY_CACHE_KEY, detail.config.entity);
    const selectedNames = detail.config.display_list?.[0]?.attribute_list || [];
    applyColumns(detail.config.entity, attributeList.value, selectedNames);
    if (detail.status === 'valid') await getData();
  } catch (error) {
    if (axios.isCancel(error)) return;
    if (generation !== loadGeneration) return;
    if (fromStartup) {
      ls.remove('__rule__');
      await enterNewRule();
    } else {
      clearDataState();
    }
  }
}

function cloneConfig(config: RetrievalSearchRequest): RetrievalSearchRequest {
  return {
    ...config,
    criteria_list: (config.criteria_list || []).map(criteria => ({
      ...criteria,
      value_list: [...(criteria.value_list || [])],
    })),
    display_list: (config.display_list || []).map(display => ({
      ...display,
      attribute_list: [...(display.attribute_list || [])],
    })),
  };
}

function toEditableConfig(config: RetrievalRuleConfig): RetrievalSearchRequest {
  return cloneConfig({
    ...config,
    type: config.type === 'advanced' || config.type === 'legacy_sql' ? 'advanced' : 'normal',
    criteria_logic: config.criteria_logic === 'or' ? 'or' : config.criteria_logic === 'expression' ? 'expression' : 'and',
  });
}

function applyColumns(entity: string, attributes: TAttributeListResponse[], selectedNames: string[]) {
  const containerWidth = document.getElementById('data-list')?.offsetWidth || 1200;
  const width = Math.max(120, Math.floor(containerWidth / Math.max(selectedNames.length, 1)));
  const selectedSet = new Set(selectedNames);
  const columns = attributes.map<RetrievalTableColumn>((attribute, index) => ({
    title: attribute.label,
    dataIndex: attribute.name,
    linkTemplate: attribute.link_template,
    copyable: attribute.copyable,
    resizable: true,
    width,
    minWidth: 80,
    fixed: false,
    sorter: true,
    firstIndex: index,
    type: attribute.display_type,
  }));
  tableState.entity = entity;
  tableState.width = width;
  tableState.sourceColumns = columns;
  tableState.selectedCol = columns.filter(column => selectedSet.has(column.dataIndex));
  tableState.selectedKeyCol = tableState.selectedCol.map(column => column.dataIndex);
}

function changeDisplayColumns(value: { entity: string; attributeList: RetrievalTableColumn[] }) {
  if (!value.attributeList.length) {
    ElMessage.warning('至少保留一个展示字段');
    return;
  }
  tableState.selectedCol = value.attributeList;
  tableState.selectedKeyCol = value.attributeList.map(column => column.dataIndex);
  currentFilter.value = {
    ...currentFilter.value,
    display_list: [{ entity: value.entity, attribute_list: [...tableState.selectedKeyCol] }],
  };
  tableState.pagination.current = 1;
  void getData();
}

function search(config: RetrievalSearchRequest) {
  currentFilter.value = cloneConfig(config);
  tableState.pagination.current = 1;
  void getData();
}

async function getData() {
  const config = cloneConfig(currentFilter.value);
  const displayFields = config.display_list?.[0]?.attribute_list || [];
  if (!config.entity || !displayFields.length || remainingIssues.value.length) return;

  dataAbortController?.abort();
  const controller = new AbortController();
  dataAbortController = controller;
  const requestId = ++dataRequestId;
  tableState.loading = true;
  try {
    const response = await RetrievalService.getListByCriteria(
      {
        ...config,
        page: tableState.pagination.current,
        size: tableState.pagination.pageSize,
        ...currentSorter.value,
      },
      { signal: controller.signal, silent: true },
    );
    if (requestId !== dataRequestId) return;
    tableState.data = response.datalist || [];
    tableState.pagination.total = response.total || 0;
  } catch (error) {
    if (requestId !== dataRequestId || axios.isCancel(error)) return;
    const message = (error as { msg?: string; message?: string })?.msg
      || (error as { message?: string })?.message
      || '检索失败';
    ElMessage.error(message);
    tableState.data = [];
    tableState.pagination.total = 0;
  } finally {
    if (requestId === dataRequestId) tableState.loading = false;
  }
}

function onTableChange({ pagination, sorter }: RetrievalTableChange) {
  if (pagination) {
    Object.assign(tableState.pagination, pagination);
  }
  if (sorter) {
    const field = sorter.prop || sorter.field;
    currentSorter.value = sorter.order && field
      ? { sort_by: field, order: sorter.order === 'ascending' || sorter.order === 'ascend' ? 'asc' : 'desc' }
      : undefined;
  }
  void getData();
}

function removeInvalidItem(issue: RetrievalRuleIssue) {
  if (issue.scope === 'display' && issue.attribute) {
    const display = currentFilter.value.display_list?.[0];
    if (display) {
      currentFilter.value = {
        ...currentFilter.value,
        display_list: [{ ...display, attribute_list: display.attribute_list.filter(name => name !== issue.attribute) }],
      };
    }
  }
  if (issue.scope === 'criteria' && issue.attribute && currentFilter.value.type === 'normal') {
    currentFilter.value = {
      ...currentFilter.value,
      criteria_list: currentFilter.value.criteria_list?.filter(criteria => criteria.attribute !== issue.attribute),
    };
  }
}

function showSaveDialog(config: RetrievalSearchRequest) {
  pendingSave.value = cloneConfig(config);
  formRule.name = activeRuleName.value;
  visibleSave.value = true;
}

async function saveRule() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || !pendingSave.value) return;
  saving.value = true;
  try {
    const payload = { ...cloneConfig(pendingSave.value), rule_name: formRule.name };
    const response = activeRule.value
      ? await RetrievalService.updateRule({ ...payload, id: activeRule.value })
      : await RetrievalService.createRule(payload);
    ElMessage.success(activeRule.value ? '更新成功' : '保存成功');
    visibleSave.value = false;
    await refreshRules();
    const saved = ruleList.value.find(item => item.id === response.id);
    if (saved) await loadRule(saved);
  } finally {
    saving.value = false;
  }
}

async function deleteRule(item: RetrievalRuleListItem) {
  try {
    await ElMessageBox.confirm('确认删除此过滤器吗？', '删除', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await RetrievalService.deleteRule({ id: item.id });
    ElMessage.success('删除成功');
    await refreshRules();
    if (item.id === activeRule.value) await enterNewRule();
  } catch (error) {
    if (error === 'cancel' || error === 'close') ElMessage.info('已取消删除');
  }
}

function showJsonData(data: unknown) {
  if (typeof data === 'object' && data !== null) {
    deviceData.value = data;
  } else {
    try {
      deviceData.value = JSON.parse(String(data));
    } catch {
      deviceData.value = { value: data };
    }
  }
  visibleJson.value = true;
}

onMounted(async () => {
  await refreshRules();
  const cached = ls.get('__rule__') as { ruleId?: number } | undefined;
  const cachedRule = cached?.ruleId ? ruleList.value.find(item => item.id === cached.ruleId) : undefined;
  if (cachedRule) await loadRule(cachedRule, true);
  else await enterNewRule();
});

onBeforeUnmount(() => {
  loadGeneration++;
  dataRequestId++;
  loadAbortController?.abort();
  dataAbortController?.abort();
});
</script>

<style lang="scss" scoped>
.data-retrieval {
  position: relative;
  padding-left: 30px;
}

.double-left-outlined,
.double-right-outlined {
  position: absolute;
  color: #bfc1c5;
  cursor: pointer;
  z-index: 99;
}

.double-left-outlined {
  top: 45%;
  right: 10px;
}

.double-right-outlined {
  top: 15px;
  left: 5px;
}

.rule-title,
.rule-div {
  line-height: 35px;
}

.rule-title {
  padding-left: 20px;
  font-size: 16px;
}

.rule-div {
  position: relative;
  width: 85%;
  padding-left: 40px;
  cursor: pointer;
}

.rule-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.delete-icon {
  position: absolute;
  top: 50%;
  right: 0;
  color: #f5222d;
  transform: translateY(-50%);
}

.create-rule-option {
  width: 90%;
  margin: 0 0 10px 20px;
  padding: 0 0 10px;
  border-bottom: 1px solid #e6e7e8;
  font-size: 18px;
}

.active-rule {
  font-weight: 700;
}

.rule-hint {
  color: #7c8087;
  font-size: 12px;
}

:deep(.rule-drawer) {
  top: 60px !important;
  bottom: 20px;
  left: 0 !important;
  margin: 25px 0 0 20px;
}

:global(.rule-drawer-modal) {
  background-color: transparent !important;
}
</style>
