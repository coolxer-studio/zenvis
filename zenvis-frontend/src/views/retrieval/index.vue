<template>
  <div id="data-list" class="main-content data-retrieval">
    <el-tooltip content="管理过滤器" placement="right">
      <el-badge :value="ruleList.length" :hidden="ruleList.length === 0" class="rule-trigger-badge">
        <el-button class="rule-trigger" aria-label="打开过滤器管理" @click="visible = true">
          <el-icon><Filter /></el-icon>
        </el-button>
      </el-badge>
    </el-tooltip>
    <el-drawer
      v-model="visible"
      direction="ltr"
      size="360px"
      :closable="false"
      :with-header="false"
      :modal-append-to-body="false"
      modal-class="rule-drawer-modal"
      class="rule-drawer"
    >
      <div class="rule-drawer-shell">
        <header class="rule-drawer-header">
          <div class="rule-drawer-heading">
            <span class="rule-drawer-icon">
              <el-icon><Filter /></el-icon>
            </span>
            <div>
              <h2>过滤器管理</h2>
              <p>保存并快速复用检索条件</p>
            </div>
          </div>
          <el-button
            text
            circle
            class="rule-close"
            aria-label="关闭过滤器管理"
            @click="visible = false"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </header>

        <section class="rule-drawer-tools">
          <el-button type="primary" class="create-rule-button" @click="enterNewRule">
            <el-icon><Plus /></el-icon>
            新建过滤器
          </el-button>

          <div class="rule-summary" aria-label="过滤器统计">
            <span class="summary-item">
              <b>{{ ruleList.length }}</b>
              全部
            </span>
            <span class="summary-divider"></span>
            <span class="summary-item" :class="{ warning: invalidRuleCount > 0 }">
              <b>{{ invalidRuleCount }}</b>
              失效
            </span>
            <span v-if="ruleKeyword" class="summary-match"
              >匹配 {{ filteredRuleList.length }} 项</span
            >
          </div>

          <el-input
            v-model="ruleKeyword"
            class="rule-search"
            clearable
            placeholder="搜索过滤器名称"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </section>

        <section class="rule-list-section">
          <div class="rule-list-heading">
            <span>规则列表</span>
            <span>{{ filteredRuleList.length }}</span>
          </div>

          <div v-if="filteredRuleList.length" class="rule-list" role="list">
            <div
              v-for="item in filteredRuleList"
              :key="item.id"
              class="rule-item"
              :class="{
                'active-rule': item.id === activeRule,
                'invalid-rule': item.status === 'invalid',
              }"
              role="listitem"
            >
              <button type="button" class="rule-item-main" @click="loadRule(item)">
                <span class="rule-item-icon">
                  <el-icon><Document /></el-icon>
                </span>
                <span class="rule-item-copy">
                  <span class="rule-item-title-row">
                    <el-tooltip :content="item.name" placement="top" :show-after="500">
                      <span class="rule-name">{{ item.name }}</span>
                    </el-tooltip>
                    <el-tag
                      :type="item.status === 'invalid' ? 'warning' : 'success'"
                      size="small"
                      effect="light"
                    >
                      {{ item.status === 'invalid' ? `失效 ${item.issue_count}` : '可用' }}
                    </el-tag>
                  </span>
                  <span class="rule-time">{{
                    formatRuleTime(item.update_time || item.create_time)
                  }}</span>
                </span>
              </button>

              <el-tooltip content="删除过滤器" placement="top">
                <el-button
                  text
                  circle
                  type="danger"
                  class="delete-rule-button"
                  :aria-label="`删除过滤器 ${item.name}`"
                  @click.stop="deleteRule(item)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>

          <div v-else-if="ruleList.length === 0" class="rule-empty-state">
            <el-empty :image-size="72" description="还没有保存的过滤器">
              <el-button type="primary" plain @click="enterNewRule">新建过滤器</el-button>
            </el-empty>
          </div>

          <div v-else class="rule-empty-state">
            <el-empty :image-size="72" description="未找到匹配的过滤器">
              <el-button @click="ruleKeyword = ''">清除搜索</el-button>
            </el-empty>
          </div>
        </section>
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
      <json-viewer
        :value="deviceData"
        :expand-depth="1"
        copyable
        boxed
        sort
        theme="my-json-theme"
      />
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
import { Close, Delete, Document, Filter, Plus, Search } from '@element-plus/icons-vue';
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
const ruleKeyword = ref('');
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
      return !config.display_list?.[0]?.attribute_list?.length;
    }
    if (issue.code === 'DISPLAY_FIELD_MISSING') {
      return config.display_list?.some(display =>
        display.attribute_list.includes(issue.attribute || ''),
      );
    }
    if (issue.code === 'CRITERIA_FIELD_MISSING') {
      if (config.type === 'advanced') {
        return config.sql === loadedConfig.value?.sql;
      }
      return config.criteria_list?.some(criteria => criteria.attribute === issue.attribute);
    }
    if (issue.code === 'OPERATOR_MISSING') {
      if (config.type === 'advanced') return config.sql === loadedConfig.value?.sql;
      const currentCriterion = config.criteria_list?.find(
        criteria => criteria.attribute === issue.attribute,
      );
      const loadedCriterion = loadedConfig.value?.criteria_list?.find(
        criteria => criteria.attribute === issue.attribute,
      );
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
    if (issue.code === 'RULE_INVALID')
      return JSON.stringify(config) === JSON.stringify(loadedConfig.value);
    return true;
  }),
);

const invalidRuleCount = computed(
  () => ruleList.value.filter(rule => rule.status === 'invalid').length,
);

const filteredRuleList = computed(() => {
  const keyword = ruleKeyword.value.trim().toLocaleLowerCase();
  if (!keyword) return ruleList.value;
  return ruleList.value.filter(rule => rule.name.toLocaleLowerCase().includes(keyword));
});

function formatRuleTime(value?: string) {
  if (!value) return '未记录更新时间';
  return `更新于 ${value.replace('T', ' ').slice(0, 16)}`;
}

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
    const response = await RetrievalService.getEntity(
      {},
      { signal: loadController.signal, silent: true },
    );
    if (generation !== loadGeneration) return;
    entityList.value = response.entity_list || [];
    const cachedEntity = ls.get(RETRIEVAL_ENTITY_CACHE_KEY);
    const selectedEntity =
      typeof cachedEntity === 'string' && entityList.value.some(item => item.name === cachedEntity)
        ? cachedEntity
        : response.selected_entity?.find(name => entityList.value.some(item => item.name === name));
    const entity = selectedEntity || entityList.value[0]?.name;
    if (!entity) {
      ls.remove(RETRIEVAL_ENTITY_CACHE_KEY);
      currentFilter.value = {
        type: 'normal',
        criteria_logic: 'and',
        criteria_list: [],
        display_list: [],
      };
      return;
    }
    await loadEntity(entity, generation, true, loadController.signal);
  } catch (error) {
    if (axios.isCancel(error)) return;
    if (generation === loadGeneration) clearDataState();
  }
}

async function loadEntity(
  entity: string,
  generation: number,
  executeAfterLoad: boolean,
  signal: AbortSignal,
) {
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
  const selectedNames = selected.length
    ? selected
    : attributeList.value.slice(0, 1).map(item => item.name);
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
  currentFilter.value = {
    type: 'normal',
    criteria_logic: 'and',
    criteria_list: [],
    display_list: [],
  };
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
    criteria_logic:
      config.criteria_logic === 'or'
        ? 'or'
        : config.criteria_logic === 'expression'
        ? 'expression'
        : 'and',
  });
}

function applyColumns(
  entity: string,
  attributes: TAttributeListResponse[],
  selectedNames: string[],
) {
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
    const message =
      (error as { msg?: string; message?: string })?.msg ||
      (error as { message?: string })?.message ||
      '检索失败';
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
    currentSorter.value =
      sorter.order && field
        ? {
            sort_by: field,
            order: sorter.order === 'ascending' || sorter.order === 'ascend' ? 'asc' : 'desc',
          }
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
        display_list: [
          {
            ...display,
            attribute_list: display.attribute_list.filter(name => name !== issue.attribute),
          },
        ],
      };
    }
  }
  if (issue.scope === 'criteria' && issue.attribute && currentFilter.value.type === 'normal') {
    currentFilter.value = {
      ...currentFilter.value,
      criteria_list: currentFilter.value.criteria_list?.filter(
        criteria => criteria.attribute !== issue.attribute,
      ),
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
  const cachedRule = cached?.ruleId
    ? ruleList.value.find(item => item.id === cached.ruleId)
    : undefined;
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
  padding-top: var(--zv-space-4);
  padding-left: var(--zv-space-4);
}

.rule-trigger-badge {
  position: absolute;
  top: 50%;
  left: 0;
  z-index: 20;
  transform: translateY(-50%);
}

.rule-trigger-badge :deep(.el-badge__content) {
  top: 1px;
  right: 5px;
  height: 16px;
  min-width: 16px;
  padding: 0 4px;
  font-size: 10px;
  line-height: 14px;
  background: var(--zv-primary);
  border: 2px solid var(--zv-bg-page);
  box-shadow: none;
}

.rule-trigger {
  width: 36px;
  height: 44px;
  min-height: 44px;
  padding: 0;
  color: var(--zv-text-secondary);
  background: var(--zv-bg-surface);
  border-color: var(--zv-border);
  border-left: 0;
  border-radius: 0 var(--zv-radius-md) var(--zv-radius-md) 0;
  box-shadow: var(--zv-shadow-2);
}

.rule-trigger:hover,
.rule-trigger:focus-visible {
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
  border-color: var(--zv-primary-border);
}

.rule-drawer-shell {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  background: var(--zv-bg-surface);
}

.rule-drawer-header {
  display: flex;
  flex: 0 0 auto;
  min-height: 76px;
  padding: var(--zv-space-4) var(--zv-space-4) var(--zv-space-3);
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--zv-space-3);
  border-bottom: 1px solid var(--zv-divider);
}

.rule-drawer-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--zv-space-3);
}

.rule-drawer-icon {
  display: inline-flex;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  color: var(--zv-primary);
  font-size: var(--zv-font-size-lg);
  background: var(--zv-primary-soft);
  border-radius: var(--zv-radius-md);
}

.rule-drawer-heading h2 {
  margin: 0;
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-lg);
  font-weight: var(--zv-font-weight-semibold);
  line-height: var(--zv-line-height-tight);
}

.rule-drawer-heading p {
  margin-top: 3px;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
  line-height: var(--zv-line-height-normal);
}

.rule-close {
  flex: 0 0 auto;
  margin-top: 1px;
  color: var(--zv-text-muted);
  border-radius: var(--zv-radius-round);
}

.rule-close:hover,
.rule-close:focus-visible {
  color: var(--zv-text-primary);
  background: var(--zv-bg-subtle);
}

.rule-drawer-tools {
  display: grid;
  flex: 0 0 auto;
  padding: var(--zv-space-4);
  gap: var(--zv-space-3);
  background: var(--zv-bg-subtle);
  border-bottom: 1px solid var(--zv-divider);
}

.create-rule-button {
  width: 100%;
  min-height: 36px;
  border-radius: var(--zv-radius-md);
}

.rule-summary {
  display: flex;
  min-height: 20px;
  align-items: center;
  gap: var(--zv-space-2);
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}

.summary-item {
  display: inline-flex;
  align-items: baseline;
  gap: var(--zv-space-1);
}

.summary-item b {
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-sm);
  font-weight: var(--zv-font-weight-semibold);
}

.summary-item.warning,
.summary-item.warning b {
  color: var(--zv-warning);
}

.summary-divider {
  width: 1px;
  height: 12px;
  background: var(--zv-border);
}

.summary-match {
  margin-left: auto;
  color: var(--zv-primary);
}

.rule-search :deep(.el-input__wrapper) {
  min-height: 36px;
  background: var(--zv-bg-surface);
  border-radius: var(--zv-radius-md);
  box-shadow: 0 0 0 1px var(--zv-border) inset;
}

.rule-list-section {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: var(--zv-space-4) var(--zv-space-3) var(--zv-space-3);
}

.rule-list-heading {
  display: flex;
  flex: 0 0 auto;
  padding: 0 var(--zv-space-1) var(--zv-space-2);
  align-items: center;
  justify-content: space-between;
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-sm);
  font-weight: var(--zv-font-weight-semibold);
}

.rule-list-heading span:last-child {
  display: inline-flex;
  min-width: 22px;
  height: 20px;
  padding: 0 6px;
  align-items: center;
  justify-content: center;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
  font-weight: var(--zv-font-weight-medium);
  background: var(--zv-bg-subtle);
  border-radius: var(--zv-radius-round);
}

.rule-list {
  min-height: 0;
  padding: var(--zv-space-1);
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-width: thin;
}

.rule-item {
  position: relative;
  display: flex;
  min-height: 64px;
  margin-bottom: var(--zv-space-2);
  overflow: hidden;
  align-items: stretch;
  background: var(--zv-bg-surface);
  border: 1px solid transparent;
  border-radius: var(--zv-radius-md);
  transition: background-color var(--zv-motion-fast) var(--zv-ease-standard),
    border-color var(--zv-motion-fast) var(--zv-ease-standard),
    box-shadow var(--zv-motion-fast) var(--zv-ease-standard);
}

.rule-item:hover,
.rule-item:focus-within {
  background: var(--zv-bg-subtle);
  border-color: var(--zv-border);
}

.rule-item.active-rule {
  background: var(--zv-primary-soft);
  border-color: var(--zv-primary-border);
  box-shadow: 0 3px 10px rgb(47 94 229 / 8%);
}

.rule-item.active-rule::before {
  position: absolute;
  top: 10px;
  bottom: 10px;
  left: 0;
  width: 3px;
  content: '';
  background: var(--zv-primary);
  border-radius: 0 var(--zv-radius-round) var(--zv-radius-round) 0;
}

.rule-item-main {
  display: flex;
  min-width: 0;
  padding: 10px 6px 10px 10px;
  flex: 1;
  align-items: center;
  gap: 10px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  outline: 0;
}

.rule-item-main:focus-visible {
  box-shadow: inset 0 0 0 2px rgb(47 94 229 / 18%);
}

.rule-item-icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  align-items: center;
  justify-content: center;
  color: var(--zv-text-muted);
  background: var(--zv-bg-subtle);
  border-radius: var(--zv-radius-md);
}

.active-rule .rule-item-icon {
  color: var(--zv-primary);
  background: var(--zv-bg-surface);
}

.invalid-rule .rule-item-icon {
  color: var(--zv-warning);
  background: var(--zv-warning-soft);
}

.rule-item-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.rule-item-title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--zv-space-2);
}

.rule-name {
  min-width: 0;
  overflow: hidden;
  flex: 1;
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-sm);
  font-weight: var(--zv-font-weight-medium);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.active-rule .rule-name {
  color: var(--zv-primary);
  font-weight: var(--zv-font-weight-semibold);
}

.rule-item :deep(.el-tag) {
  height: 20px;
  padding-inline: 6px;
  flex: 0 0 auto;
  border-radius: var(--zv-radius-round);
  font-size: 11px;
}

.rule-time {
  overflow: hidden;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-rule-button {
  align-self: center;
  width: 30px;
  height: 30px;
  min-height: 30px;
  margin-right: 6px;
  flex: 0 0 30px;
  opacity: 0;
  transform: translateX(4px);
  transition: opacity var(--zv-motion-fast) var(--zv-ease-standard),
    transform var(--zv-motion-fast) var(--zv-ease-standard),
    background-color var(--zv-motion-fast) var(--zv-ease-standard);
}

.rule-item:hover .delete-rule-button,
.delete-rule-button:focus-visible {
  opacity: 1;
  transform: translateX(0);
}

.rule-empty-state {
  display: flex;
  min-height: 260px;
  flex: 1;
  align-items: flex-start;
  justify-content: center;
}

.rule-empty-state :deep(.el-empty) {
  padding-top: var(--zv-space-8);
}

.rule-hint {
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}

:deep(.rule-drawer) {
  top: calc(var(--zv-header-height) + var(--zv-space-3)) !important;
  bottom: var(--zv-space-3);
  left: var(--zv-space-3) !important;
  height: auto !important;
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-lg);
  box-shadow: var(--zv-shadow-3);
}

:deep(.rule-drawer .el-drawer__body) {
  height: 100%;
  padding: 0;
  overflow: hidden;
}

:global(.rule-drawer-modal) {
  background: rgb(15 28 48 / 12%) !important;
  backdrop-filter: blur(1px);
}

@media (max-width: 640px) {
  .data-retrieval {
    padding-top: var(--zv-space-3);
    padding-left: var(--zv-space-3);
  }

  .rule-trigger-badge {
    top: 50%;
    left: 0;
  }

  :deep(.rule-drawer) {
    top: var(--zv-header-height) !important;
    bottom: 0;
    left: 0 !important;
    width: 100% !important;
    border-right: 0;
    border-bottom: 0;
    border-left: 0;
    border-radius: 0;
  }

  .rule-drawer-header,
  .rule-drawer-tools {
    padding-inline: var(--zv-space-3);
  }

  .rule-list-section {
    padding-inline: var(--zv-space-2);
  }
}

@media (prefers-reduced-motion: reduce) {
  .rule-item,
  .delete-rule-button {
    transition: none;
  }
}
</style>
