<template>
  <div class="filter-div">
    <div class="filter-div1">
      {{activeRuleName? activeRuleName : '搜索'}}
      <el-button type="primary" @click="ruleCreate" style="margin-left: 20px">{{activeRuleName? '更新' : '保存为'}}</el-button></div>
    <el-row class="filter-toolbar">
      <el-col :span="3" class="entity-col">
        实体：<el-select
        ref="select"
        v-model="entitySelected"
        style="width: 120px"
        @change="handleChangeEntity"
        placeholder="请选择实体"
      >
        <el-option v-for="item in entityListData" :value="(item as any).name" :label="(item as any).label"></el-option>
      </el-select>
      </el-col>
      <el-col :span="1" class="more-filter-col" v-if="!senior">
        <el-popover :visible="moreFilterShow" placement="bottom-start" :width="240">
          <template #reference>
            <div style="cursor: pointer" @click.stop="moreFilterShow = !moreFilterShow">更多
              <el-icon v-if="moreFilterShow"><ArrowUp /></el-icon>
              <el-icon v-else><ArrowDown /></el-icon>
            </div>
          </template>
          <div class="more-filter">
            <template v-if="entitySelected">
              <el-input
                v-model="moreSearch"
                placeholder=""
                style="margin-bottom: 10px"
                @click.stop
              >
                <template #append>
                  <el-button @click="onSearch"><el-icon><Search /></el-icon></el-button>
                </template>
              </el-input>

              <div class="all-filter">所有条件</div>
              <el-checkbox-group v-model="filterKeySelected" style="width: 100%" @change="getSelect">
                <div v-for="item in AttributeListData" style="height: 35px;line-height: 35px">
                  <template v-if="moreSearch">
                    <el-checkbox :value="(item as any).name" v-show="(item as any).label.indexOf(moreSearch) != -1">{{(item as any).label}}</el-checkbox>
                  </template>
                  <template v-else>
                    <el-checkbox :value="(item as any).name">{{(item as any).label}}</el-checkbox>
                  </template>
                </div>
              </el-checkbox-group>
            </template>
            <template v-else>
              <div>请先选择实体</div>
            </template>
          </div>
        </el-popover>

      </el-col>
      <template v-if="senior">
        <el-col :span="18" class="advanced-input-col">
          <el-input v-model="sql" placeholder="请输入内容" clearable/>
        </el-col>
      </template>

      <el-col :span="3" class="action-col">
        <el-button class="margin-l" @click="sendQuery1">
          <el-icon><Search /></el-icon>
        </el-button>
        <span class="margin-l option" v-if="!senior" @click="toSenior">高级</span>
        <span class="margin-l option" v-if="senior" @click="cancelSenior">简单</span>
      </el-col>
    </el-row>
    <div v-if="!senior">
      <div class="condition-logic-bar" v-if="filterSelected.length > 1">
        <span class="condition-logic-label">关系：</span>
        <el-segmented
          v-model="criteriaLogic"
          :options="criteriaLogicOptions"
          size="small"
          @change="handleCriteriaLogicChange"
        />
      </div>
      <div class="add-filter" v-for="(item, index) in filterSelected">
        <el-popover :visible="criteriaObject[(item as any).name + 'Visible']" placement="bottom-start" trigger="manual">
          <template #reference>
            <div class="filter-tag" @click.stop="togglePopover(item)">
              <span class="tag-label">{{(item as any).label}}</span>
              <span class="tag-operator">{{getSelectedOperatorLabel(item as TAttributeListResponse)}}</span>
              <span class="tag-value" v-if="!isValuelessOperator(criteriaObject[(item as any).name + 'Operator'])">{{criteriaObject[(item as any).name + 'Value'] ? (criteriaObject[(item as any).name + 'Value'] + '').substring(0, 10) : ''}}</span>
              <el-icon v-if="!criteriaObject[(item as any).name + 'Visible'] == false" class="tag-arrow"><ArrowUp /></el-icon>
              <el-icon v-else class="tag-arrow"><ArrowDown /></el-icon>
              <el-icon class="tag-close" @click.stop="deleteFilter(index, item)"><Close /></el-icon>
            </div>
          </template>

          <div class="popover-content" @click.stop>
            <div class="popover-body">
              <div class="form-row">
                <div class="form-item operator-item">
                  <el-select
                    ref="select"
                    v-model="criteriaObject[(item as any).name + 'Operator']"
                    placeholder="请选择操作符"
                    class="form-select"
                    @change="handleOperatorChange(item as TAttributeListResponse)"
                  >
                    <el-option v-for="item1 in (item as any).operator_list" :value="(item1 as any).name" :label="(item1 as any).label"></el-option>
                  </el-select>
                </div>
                <div class="form-item value-item" v-show="criteriaObject[(item as any).name + 'Operator'] && !isValuelessOperator(criteriaObject[(item as any).name + 'Operator'])">
                  <template v-if="criteriaObject[(item as any).name + 'Operator'] == 'between'">
                    <template v-if="item.retrieval_type == 'date'">
                      <el-date-picker
                        v-model="criteriaObject[(item as any).name + 'Value']"
                        type="daterange"
                        :disabled-date="disabledDate"
                        show-time
                        format="YYYY-MM-DD HH:mm:ss"
                        range-separator="至"
                        start-placeholder="开始日期"
                        end-placeholder="结束日期"
                        class="form-date-picker"/>
                    </template>
                    <template v-else>
                      <div class="range-input-group">
                        <el-input v-model="criteriaObject[(item as any).name + 'Value']" placeholder="起始值" class="form-input" />
                        <span class="range-separator">至</span>
                        <el-input v-model="criteriaObject[(item as any).name + 'Value1']" placeholder="结束值" class="form-input" />
                      </div>
                    </template>
                  </template>
                  <template v-else>
                    <template v-if="item.retrieval_type == 'date'">
                      <el-date-picker
                        v-model="criteriaObject[(item as any).name + 'Value']"
                        type="datetime"
                        show-time
                        :disabled-date="disabledDate"
                        class="form-date-picker"/>
                    </template>
                    <template v-else>
                      <el-autocomplete
                        v-if="shouldUseAutoComplete(item as TAttributeListResponse)"
                        v-model="criteriaObject[(item as any).name + 'Value']"
                        :fetch-suggestions="(queryString, callback) => fetchAutoCompleteSuggestions(item as TAttributeListResponse, queryString, callback)"
                        placeholder="请输入值"
                        class="form-input"
                        value-key="label"
                        :trigger-on-focus="false"
                        clearable
                        @select="(option) => handleAutoCompleteSelect(item as TAttributeListResponse, option)"
                      />
                      <el-input
                        v-else
                        v-model="criteriaObject[(item as any).name + 'Value']"
                        placeholder="请输入值"
                        class="form-input"
                      />
                    </template>
                  </template>
                </div>
              </div>
            </div>
            <div class="popover-footer">
              <el-button size="small" class="btn-cancel" @click="cancelCriteria(item)">取消</el-button>
              <el-button type="primary" size="small" class="btn-confirm" @click="saveCriteria(item)">确定</el-button>
            </div>
          </div>
        </el-popover>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
  import {reactive, ref, toRaw, watch, onMounted, onBeforeUnmount} from "vue";
  import {ls} from "@u/local-storage";
  import { Search, ArrowDown, ArrowUp , Close } from '@element-plus/icons-vue';
  import {
    EntityResponse,
    TEntityListResponse,
    AttributeResponse,
    TCriteriaList,
    TAttributeListResponse,
    RetrievalSearchRequest,
    SelectAttributeItem,
    RetrievalLogic,
    AutoCompleteOption,
  } from "@/types/type-retrieval";
  import { RetrievalService } from '@/service/api';
  import {ElMessage} from 'element-plus';
  import dayjs, {Dayjs} from 'dayjs';
  import { debounce } from 'lodash-es';
  const props = defineProps({
    activeRule: {
      type: Number,
      default: () => {
        return 0
      }
    },
    resetNum: {
      type: Number,
      default: () => {
        return 0
      }
    },
    activeRuleName: {
      type: String,
      default: () => {
        return ''
      }
    },
    ruleDetailNum: {
      type: Number,
      default: () => {
        return 0
      }
    },
  });
  const emit = defineEmits({
    'on-query': null,
    'on-create': null,
    'on-col': null,
    'on-update': null,
    'on-query1': null,
    'on-query2': null
  });
  const filter = ls.get('__filter__') || {}
  const rule = ls.get('__rule__') || {}
  const ruleId = ref<number>(rule.ruleId)
  const entitySelected = ref<string>()
  const entityListData = ref<TEntityListResponse[]>()
  const AttributeListData = ref<TAttributeListResponse[]>([])
  const AttributeListDataCopy = ref<TAttributeListResponse[]>([])
  const criteriaList = ref<TCriteriaList[]>()
  const criteriaObject = ref<object>(filter.criteriaObject || {})
  const senior = ref<boolean>(false)
  const criteriaLogic = ref<Exclude<RetrievalLogic, 'expression'>>(filter.criteriaLogic === 'or' ? 'or' : 'and')
  const criteriaLogicOptions = [
    { label: 'AND', value: 'and' },
    { label: 'OR', value: 'or' },
  ]
  const moreSearch = ref<string>('')
  const inputSearch = ref<string>('')
  const sql = ref<string>('')
  const moreFilterShow = ref<boolean>(false)
  const filterList = ref<object[]>([{
    key: 1,
    name: 1
  }])
  const filterKeySelected = ref<string[]>(filter.filterKeySelected || [])
  const filterSelected = ref<TAttributeListResponse[]>(filter.filterSelected || [])
  const filterKeySelectedCopy = ref<string[]>(filter.filterKeySelected || [])
  type AutoCompleteCallback = (items: AutoCompleteOption[]) => void
  type AutoCompleteFetcher = (queryString: string, callback: AutoCompleteCallback) => void
  const singleValueAutoCompleteOperators = [
    'equal',
    'notequal',
    'match',
    'greatthan',
    'lessthan',
    'greatequalthan',
    'lessequalthan',
  ]
  const autoCompleteFetchers = new Map<string, AutoCompleteFetcher>()
  const disabledDate = (current: Dayjs) => {
    return current && current >= dayjs().endOf('day');
  };
  const onResize = (obj) => {
    obj.col.width = obj.w
  }
  watch(
    () => filterKeySelected.value,
    (newVal, oldVal) => {
      filterSelected.value = []
      AttributeListData.value.map((e) => {
        if (newVal.indexOf(e.name) != -1) {
          filterSelected.value.push(e)
        }
      })
      filterKeySelectedCopy.value = JSON.parse(JSON.stringify(filterKeySelected.value))
    },
    {
      deep: true
    }
  )
  const deleteFilter = (index, item) => {
    criteriaObject.value[(item as any).name + 'Visible'] = false
    filterKeySelected.value.splice(index, 1)
    delete criteriaObject.value[(item as any).name + 'Visible']
    delete criteriaObject.value[(item as any).name + 'Operator']
    delete criteriaObject.value[(item as any).name + 'Value']
    delete criteriaObject.value[(item as any).name + 'Value1']
    delete criteriaObject.value[(item as any).name + 'Title']
    delete criteriaObject.value[(item as any).name + 'AllName']
  }
  const onSearch = (val) => {
  }
  const toSenior = () => {
    senior.value = true
  }
  const cancelSenior = () => {
    senior.value = false
  }
  const getSelect = () => {
    const selectedLength = filterKeySelected.value.length
    const previousSelectedLength = filterKeySelectedCopy.value.length
    if (filterKeySelectedCopy.value.length > filterKeySelected.value.length) {
      filterKeySelectedCopy.value.map((e:any) => {
        if (filterKeySelected.value.indexOf(e) == -1) {
          delete criteriaObject.value[e + 'Visible']
          delete criteriaObject.value[e + 'Operator']
          delete criteriaObject.value[e + 'Value']
          delete criteriaObject.value[e + 'Value1']
          delete criteriaObject.value[e + 'Title']
          delete criteriaObject.value[e + 'AllName']
        }
      })
    }
    if (selectedLength !== previousSelectedLength) {
      closeMoreFilter()
    }
  }
  const isValuelessOperator = (operator?: string): boolean => {
    return operator === 'isnull' || operator === 'isnotnull'
  }
  const isSingleValueAutoCompleteOperator = (operator?: string): boolean => {
    return singleValueAutoCompleteOperators.includes(operator || '')
  }
  const shouldUseAutoComplete = (item: TAttributeListResponse): boolean => {
    return Boolean(
      item.auto_complete &&
      entitySelected.value &&
      isSingleValueAutoCompleteOperator(criteriaObject.value[item.name + 'Operator'])
    )
  }
  const getAutoCompleteFetcher = (item: TAttributeListResponse): AutoCompleteFetcher => {
    const key = `${entitySelected.value || ''}:${item.name}`
    const existing = autoCompleteFetchers.get(key)
    if (existing) {
      return existing
    }
    const fetcher = debounce((queryString: string, callback: AutoCompleteCallback) => {
      const term = queryString.trim()
      if (!entitySelected.value || !term) {
        callback([])
        return
      }
      RetrievalService.autoComplete({
        entity: entitySelected.value,
        attribute: item.name,
        term,
      }).then((res) => {
        callback(res.options || [])
      }).catch(() => {
        callback([])
      })
    }, 250) as AutoCompleteFetcher
    autoCompleteFetchers.set(key, fetcher)
    return fetcher
  }
  const fetchAutoCompleteSuggestions = (
    item: TAttributeListResponse,
    queryString: string,
    callback: AutoCompleteCallback,
  ) => {
    getAutoCompleteFetcher(item)(queryString, callback)
  }
  const handleAutoCompleteSelect = (item: TAttributeListResponse, option: AutoCompleteOption) => {
    criteriaObject.value[item.name + 'Value'] = option.value
  }
  const handleOperatorChange = (item: TAttributeListResponse) => {
    if (isValuelessOperator(criteriaObject.value[item.name + 'Operator'])) {
      delete criteriaObject.value[item.name + 'Value']
      delete criteriaObject.value[item.name + 'Value1']
    }
  }
  const hasCriteriaValue = (item: TAttributeListResponse): boolean => {
    const operator = criteriaObject.value[item.name + 'Operator']
    const value = criteriaObject.value[item.name + 'Value']
    const value1 = criteriaObject.value[item.name + 'Value1']
    if (!operator) {
      return false
    }
    if (isValuelessOperator(operator)) {
      return true
    }
    if (operator === 'between') {
      if (item.retrieval_type === 'date') {
        return Array.isArray(value) && Boolean(value[0]) && Boolean(value[1])
      }
      return Boolean(value) && Boolean(value1)
    }
    return Boolean(value)
  }
  const getSelectedOperatorLabel = (item: TAttributeListResponse): string => {
    const operatorName = criteriaObject.value[item.name + 'Operator']
    const operator = item.operator_list?.find((operatorItem) => operatorItem.name === operatorName)
    if (operator) {
      return operator.label
    }
    if (operatorName === '==') {
      return '等于'
    }
    if (operatorName === 'contains') {
      return '包含'
    }
    return operatorName || ''
  }
  const hasCompleteSelectedCriteria = (): boolean => {
    return filterKeySelected.value.every((key) => {
      const item = AttributeListData.value.find(attribute => attribute.name === key)
      return Boolean(item && hasCriteriaValue(item))
    })
  }
  const resetCriteriaState = () => {
    senior.value = false
    sql.value = ''
    filterSelected.value = []
    filterKeySelected.value = []
    filterKeySelectedCopy.value = []
    criteriaObject.value = {}
    criteriaLogic.value = 'and'
  }
  const emitCurrentQuery = (search: boolean) => {
    if (senior.value) {
      const advancedSql = sql.value.trim()
      if (!advancedSql) {
        return
      }
      const queryData: RetrievalSearchRequest = {
        entity: entitySelected.value,
        type: 'advanced',
        sql: advancedSql,
      }
      if (search) {
        emit('on-query2', queryData)
      } else {
        emit('on-query', queryData)
      }
      return
    }
    const queryData = getQueryData()
    if (search) {
      emit('on-query1', toRaw(queryData))
    } else {
      emit('on-query', toRaw(queryData))
    }
  }
  const handleCriteriaLogicChange = () => {
    if (filterKeySelected.value.length && hasCompleteSelectedCriteria()) {
      sendQuery1()
    }
  }
  const getQueryData = (): RetrievalSearchRequest => {
    const queryData: RetrievalSearchRequest = {
      entity: entitySelected.value,
      criteria_list: [] as any,
      criteria_logic: criteriaLogic.value,
      type: 'normal'
    }
    filterSelected.value.map((e: TAttributeListResponse) => {
      if (hasCriteriaValue(e)) {
        let arr = [] as any
        if (isValuelessOperator(criteriaObject.value[e.name + 'Operator'])) {
          arr = []
        }
        if (!isValuelessOperator(criteriaObject.value[e.name + 'Operator']) && criteriaObject.value[e.name + 'Operator'] != 'between' && e.retrieval_type != 'date') {
          arr = criteriaObject.value[e.name + 'Value'].split('\n')
        }
        if (!isValuelessOperator(criteriaObject.value[e.name + 'Operator']) && criteriaObject.value[e.name + 'Operator'] != 'between' && e.retrieval_type == 'date') {
          arr.push(dayjs(criteriaObject.value[e.name + 'Value']).format('YYYY-MM-DD HH:mm:ss'))
        }
        if (criteriaObject.value[e.name + 'Operator'] == 'between' && e.retrieval_type != 'date') {
          arr.push(criteriaObject.value[e.name + 'Value'])
          arr.push(criteriaObject.value[e.name + 'Value1'])
        }
        if (criteriaObject.value[e.name + 'Operator'] == 'between' && e.retrieval_type == 'date') {
          arr.push(dayjs(criteriaObject.value[e.name + 'Value'][0]).format('YYYY-MM-DD HH:mm:ss'))
          arr.push(dayjs(criteriaObject.value[e.name + 'Value'][1]).format('YYYY-MM-DD HH:mm:ss'))

        }
        queryData.criteria_list?.push({
          attribute: e.name,
          operator: criteriaObject.value[e.name + 'Operator'],
          value_list: arr
        })
      }
    })
    return queryData
  }
  const serializeAdvancedValue = (value: string): string => {
    if (/^-?\d+(\.\d+)?$/.test(value) || /^[A-Za-z0-9_.:/-]+$/.test(value)) {
      return value
    }
    return "'" + value.replace(/'/g, "''") + "'"
  }
  const serializeAdvancedCondition = (condition: TCriteriaList): string => {
    const values = condition.value_list || []
    switch (condition.operator) {
      case 'equal':
        return `${condition.attribute} = ${serializeAdvancedValue(values[0] || '')}`
      case 'notequal':
        return `${condition.attribute} != ${serializeAdvancedValue(values[0] || '')}`
      case 'greatthan':
        return `${condition.attribute} > ${serializeAdvancedValue(values[0] || '')}`
      case 'lessthan':
        return `${condition.attribute} < ${serializeAdvancedValue(values[0] || '')}`
      case 'greatequalthan':
        return `${condition.attribute} >= ${serializeAdvancedValue(values[0] || '')}`
      case 'lessequalthan':
        return `${condition.attribute} <= ${serializeAdvancedValue(values[0] || '')}`
      case 'between':
        return `${condition.attribute} between ${serializeAdvancedValue(values[0] || '')} and ${serializeAdvancedValue(values[1] || '')}`
      case 'in':
        return `${condition.attribute} in (${values.map(serializeAdvancedValue).join(', ')})`
      case 'match':
        return `${condition.attribute} like ${serializeAdvancedValue(values[0] || '')}`
      case 'isnull':
        return `${condition.attribute} is null`
      case 'isnotnull':
        return `${condition.attribute} is not null`
      default:
        return `${condition.attribute} = ${serializeAdvancedValue(values[0] || '')}`
    }
  }
  const serializeWhereExpression = (filter: { logic: 'and' | 'or'; conditions: TCriteriaList[] }): string => {
    return filter.conditions.map(serializeAdvancedCondition).join(` ${filter.logic} `)
  }
  const getAdvancedQueryData = (): RetrievalSearchRequest | null => {
    const advancedSql = sql.value.trim()
    if (!advancedSql) {
      ElMessage.warning('请完善搜索条件！');
      return null
    }
    return {
      entity: entitySelected.value,
      type: 'advanced',
      sql: advancedSql,
    }
  }
  const sendQuery = () => {
    const queryData = getQueryData()
    emit('on-query', toRaw(queryData));
  }
  const sendQuery1 = () => {
    if (senior.value) {
      const queryData = getAdvancedQueryData()
      if (queryData) {
        emit('on-query2', queryData);
      }
    } else {
      let status = false
      for (let i = 0; i < filterKeySelected.value.length; i++) {
        const item = AttributeListData.value.find(attribute => attribute.name === filterKeySelected.value[i])
        if (!item || !hasCriteriaValue(item)) {
          ElMessage.warning('请完善搜索条件！');
          status = true
          break
        }
      }
      if (!status) {
        const queryData = getQueryData()
        emit('on-query1', toRaw(queryData));
      }
    }

  }
  const closeMoreFilter = () => {
    moreFilterShow.value = false
  }
  const ruleCreate = () => {
    const queryData = senior.value ? getAdvancedQueryData() : getQueryData()
    if (!queryData) {
      return
    }
    if (props.activeRule) {
      emit('on-update', toRaw(queryData));
    } else {
      emit('on-create', toRaw(queryData));
    }

  }
  const togglePopover = (obj) => {
    criteriaObject.value[obj.name + 'Visible'] = !criteriaObject.value[obj.name + 'Visible']
  }
  const cancelCriteria = (obj) => {
    criteriaObject.value[obj.name + 'Visible'] = false
  }
  const saveCriteria = (obj) => {
    if (hasCriteriaValue(obj)) {
      let operatorLabel = ''
      obj.operator_list.map((e:any) => {
        if (e.name == criteriaObject.value[obj.name + 'Operator']) {
          operatorLabel = e.label
        }
      })
      if (isValuelessOperator(criteriaObject.value[obj.name + 'Operator'])) {
        criteriaObject.value[obj.name + 'Title'] = obj.label + ' ' + operatorLabel
        criteriaObject.value[obj.name + 'AllName'] = '<span style="color: #3988ff">' + obj.label + '</span> ' + operatorLabel
      } else if (criteriaObject.value[obj.name + 'Operator'] == 'between') {
        if (obj.retrieval_type == 'date') {
          criteriaObject.value[obj.name + 'Title'] = obj.label + '介于' + dayjs(criteriaObject.value[obj.name + 'Value'][0]).format('YYYY-MM-DD HH:mm:ss') + '、' + dayjs(criteriaObject.value[obj.name + 'Value'][1]).format('YYYY-MM-DD HH:mm:ss') + operatorLabel
          criteriaObject.value[obj.name + 'AllName'] = '<span style="color: #3988ff">' + obj.label + '</span> ' + '介于' + ' <span style="color: #42ac66">' + dayjs(criteriaObject.value[obj.name + 'Value'][0]).format('YYYY-MM-DD HH:mm:ss') + '、' + dayjs(criteriaObject.value[obj.name + 'Value'][1]).format('YYYY-MM-DD HH:mm:ss') + operatorLabel + '</span>'
        } else {
          criteriaObject.value[obj.name + 'Title'] = obj.label + '介于' + criteriaObject.value[obj.name + 'Value'] + '、' + criteriaObject.value[obj.name + 'Value1'] + operatorLabel
          criteriaObject.value[obj.name + 'AllName'] = '<span style="color: #3988ff">' + obj.label + '</span> ' + '介于' + ' <span style="color: #42ac66">' + criteriaObject.value[obj.name + 'Value'] + '、' + criteriaObject.value[obj.name + 'Value1'] + operatorLabel + '</span>'
        }
      } else {
        if (obj.retrieval_type == 'date') {
          criteriaObject.value[obj.name + 'Title'] = obj.label + ' ' + operatorLabel + ' ' + dayjs(criteriaObject.value[obj.name + 'Value']).format('YYYY-MM-DD HH:mm:ss')
          criteriaObject.value[obj.name + 'AllName'] = '<span style="color: #3988ff">' + obj.label + '</span> ' + operatorLabel + ' <span style="color: #42ac66">' + dayjs(criteriaObject.value[obj.name + 'Value']).format('YYYY-MM-DD HH:mm:ss') + '</span>'
        } else {
          criteriaObject.value[obj.name + 'Title'] = obj.label + ' ' + operatorLabel + ' ' + criteriaObject.value[obj.name + 'Value'].split('\n')
          criteriaObject.value[obj.name + 'AllName'] = '<span style="color: #3988ff">' + obj.label + '</span> ' + operatorLabel + ' <span style="color: #42ac66">' + criteriaObject.value[obj.name + 'Value'].split('\n') + '</span>'
        }
      }

      criteriaObject.value[obj.name + 'Visible'] = false

      sendQuery1();
    } else {
      ElMessage.warning('请完善搜索条件！');
    }
  }
  const getAttributeList = (val, searchAfterLoad = false, resetBeforeLoad = false) => {
    let params = {}
    if (ruleId.value) {
      params = {entity: val, rule_id: ruleId.value}
    } else {
      params = {entity: val}
    }
    return RetrievalService.getAttribute(params).then((res: AttributeResponse) => {
      AttributeListData.value = res.attribute_list
      AttributeListDataCopy.value = res.attribute_list
      criteriaLogic.value = res.criteria_logic === 'or' ? 'or' : 'and'
      if (resetBeforeLoad) {
        resetCriteriaState()
      }
      criteriaLogic.value = res.criteria_logic === 'or' ? 'or' : 'and'
      if (res.select_attribute_list && res.select_attribute_list.length && res.attribute_list && res.attribute_list.length) {
        if (res.criteria_logic === 'expression' && res.sql) {
          senior.value = true
          sql.value = res.sql
          return
        }
        res.select_attribute_list.map((e: SelectAttributeItem) => {
          res.attribute_list.map((a: any) => {
            if (e.name == a.name) {
              const valueList = e.value_list || []
              criteriaObject.value[a.name + 'Visible'] = false
              criteriaObject.value[a.name + 'Operator'] = e.operator_name
              let operatorLabel = ''
              a.operator_list.map((e:any) => {
                if (e.name == criteriaObject.value[a.name + 'Operator']) {
                  operatorLabel = e.label
                }
              })
              if (isValuelessOperator(criteriaObject.value[a.name + 'Operator'])) {
                delete criteriaObject.value[a.name + 'Value']
                delete criteriaObject.value[a.name + 'Value1']
                criteriaObject.value[a.name + 'Title'] = a.label + ' ' + operatorLabel
                criteriaObject.value[a.name + 'AllName'] = '<span style="color: #3988ff">' + a.label + '</span> ' + operatorLabel
              } else if (criteriaObject.value[a.name + 'Operator'] == 'between') {
                if (a.retrieval_type == 'date') {
                  criteriaObject.value[a.name + 'Value'] = valueList
                  criteriaObject.value[a.name + 'Title'] = a.label + '介于' + criteriaObject.value[a.name + 'Value'][0] + '、' + criteriaObject.value[a.name + 'Value'][1] + operatorLabel
                  criteriaObject.value[a.name + 'AllName'] = '<span style="color: #3988ff">' + a.label + '</span> ' + '介于' + ' <span style="color: #42ac66">' + criteriaObject.value[a.name + 'Value'][0] + '、' + criteriaObject.value[a.name + 'Value'][1] + operatorLabel + '</span>'
                } else {
                  criteriaObject.value[a.name + 'Value'] = valueList[0]
                  criteriaObject.value[a.name + 'Value1'] = valueList[1]
                  criteriaObject.value[a.name + 'Title'] = a.label + '介于' + criteriaObject.value[a.name + 'Value'] + '、' + criteriaObject.value[a.name + 'Value1'] + operatorLabel
                  criteriaObject.value[a.name + 'AllName'] = '<span style="color: #3988ff">' + a.label + '</span> ' + '介于' + ' <span style="color: #42ac66">' + criteriaObject.value[a.name + 'Value'] + '、' + criteriaObject.value[a.name + 'Value1'] + operatorLabel + '</span>'
                }
              } else {
                if (a.retrieval_type == 'date') {
                  criteriaObject.value[a.name + 'Value'] = valueList[0]
                  criteriaObject.value[a.name + 'Title'] = a.label + ' ' + operatorLabel + ' ' + criteriaObject.value[a.name + 'Value']
                  criteriaObject.value[a.name + 'AllName'] = '<span style="color: #3988ff">' + a.label + '</span> ' + operatorLabel + ' <span style="color: #42ac66">' + criteriaObject.value[a.name + 'Value'] + '</span>'
                } else {
                  criteriaObject.value[a.name + 'Value'] = valueList.join('\n')
                  criteriaObject.value[a.name + 'Title'] = a.label + ' ' + operatorLabel + ' ' + criteriaObject.value[a.name + 'Value'].split('\n')
                  criteriaObject.value[a.name + 'AllName'] = '<span style="color: #3988ff">' + a.label + '</span> ' + operatorLabel + ' <span style="color: #42ac66">' + criteriaObject.value[a.name + 'Value'].split('\n') + '</span>'
                }

              }
              filterKeySelected.value.push(a.name)
              filterSelected.value.push(a)
            }
          })
        })
      }
    }).then(() => {
      if (searchAfterLoad) {
        emitCurrentQuery(true)
      }
    })
  }
  const handleChangeEntity = (val) => {
    autoCompleteFetchers.clear()
    resetCriteriaState()
    getAttributeList(val)
    sendQuery()
    if (ruleId.value) {
      emit('on-col', {entity: val, rule_id: ruleId.value});
    } else {
      emit('on-col', {entity: val});
    }
    const currentLs = ls.get('__filter__')
    if (currentLs.ruleId) {
    } else {
    }
  }
  const getEntityList = () => {
    let params = {}
    if (ruleId.value) {
      params = {rule_id: ruleId.value}
    }
    RetrievalService.getEntity(params).then((res: EntityResponse) => {
      entityListData.value = res.entity_list
      const currentLs = ls.get('__filter__') || {}
      entitySelected.value = ruleId.value
        ? ((res.selected_entity && res.selected_entity.length) ? res.selected_entity[0] : res.entity_list[0].name)
        : (currentLs.entity ? currentLs.entity : ((res.selected_entity && res.selected_entity.length) ? res.selected_entity[0] : res.entity_list[0].name))
      getAttributeList(entitySelected.value, Boolean(ruleId.value), Boolean(ruleId.value))
      if (!ruleId.value) {
        sendQuery()
      }
      if (ruleId.value) {
        emit('on-col', {entity: entitySelected.value, rule_id: ruleId.value});
      } else {
        emit('on-col', {entity: entitySelected.value});
      }

    })
  }
  watch(
    () => props.ruleDetailNum,
    (newVal, oldVal) => {
      ruleId.value = props.activeRule
      getEntityList()
    }
  )
  watch(
    () => props.resetNum,
    (newVal, oldVal) => {
      ruleId.value = 0
      resetCriteriaState()
      getEntityList()
    }
  )

  const closeAllPopovers = () => {
    for (const key in criteriaObject.value) {
      if (key.endsWith('Visible')) {
        criteriaObject.value[key] = false
      }
    }
  }
  const handleDocumentClick = () => {
    closeMoreFilter()
    closeAllPopovers()
  }

  onMounted(() => {
    getEntityList()
    document.addEventListener('click', handleDocumentClick)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('click', handleDocumentClick)
    autoCompleteFetchers.clear()
  })
</script>
<style lang="scss" scoped>
  .filter-div{
    margin-bottom: 20px;
  }
  .filter-div1{
    margin-bottom: 20px;
  }
  .margin-l{
    margin-left: 8px;
  }
  .filter-toolbar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
  }
  .filter-toolbar .entity-col,
  .filter-toolbar .more-filter-col,
  .filter-toolbar .action-col {
    flex: 0 0 auto;
    max-width: none;
    width: auto;
  }
  .filter-toolbar .entity-col,
  .filter-toolbar .action-col {
    display: inline-flex;
    align-items: center;
  }
  .filter-toolbar .advanced-input-col {
    flex: 1 1 420px;
    max-width: 720px;
  }
  .more-filter-col{
    position: relative;
    line-height: 35px;
  }
  .more-filter{
    border-radius: 5px;
    padding: 10px;
    line-height: 35px;
    max-height: 300px;
    overflow-y: auto;
    background-color: #fff;
  }
  .all-filter {
    border-top: 1px solid #bec1c6;
    height: 35px;
    line-height: 35px;
    font-size: 16px;
    margin-top: 10px;
  }
  .option{
    color: #3988ff;
    cursor: pointer;
  }
  .condition-logic-bar {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-top: 10px;
    margin-right: 16px;
    height: 32px;
    vertical-align: top;
  }
  .condition-logic-label {
    color: #606266;
    font-size: 14px;
    white-space: nowrap;
  }
  .cur-po{
    cursor: pointer;
  }
  .add-filter{
    display: inline-block;
    margin-right: 20px;
    margin-top: 10px;
  }
  .filter-tag{
    display: inline-flex;
    align-items: center;
    background: #fff;
    border: 1px solid #d5d7db;
    border-radius: 4px;
    padding: 4px 6px;
    cursor: pointer;
  }
  .tag-label{
    color: #409eff;
    font-weight: 500;
    margin-right: 4px;
  }
  .tag-operator{
    margin-right: 4px;
  }
  .tag-value{
    margin-right: 4px;
    max-width: 80px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .tag-arrow{
    font-size: 14px;
    margin-right: 4px;
    color: #999;
  }
  .tag-close{
    color: #fff;
    background: #ff4d4f;
    border-radius: 50%;
    width: 16px;
    height: 16px;
    line-height: 14px;
    font-size: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-left: 4px;
    cursor: pointer;
  }
  .popover-content {
    padding: 8px;
    min-width: 500px;
    background: #fff;
    border-radius: 4px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    border: 1px solid #dcdfe6;
    overflow: hidden;
  }
  .popover-body {
    padding: 0;
  }
  .form-row {
    display: flex;
    align-items: stretch;
    gap: 8px;
  }
  .form-item {
    display: flex;
    flex-direction: column;
  }
  .form-item.operator-item {
    width: 120px;
    border-right: none;
  }
  .form-item.value-item {
    flex: 1;
  }
  .form-select {
    width: 100%;
    border-radius: 4px 0 0 4px;
    border-right: none;
  }
  .form-input {
    width: 100%;
    border-radius: 0 4px 4px 0;
  }
  .form-date-picker {
    width: 100%;
    border-radius: 0 4px 4px 0;
  }
  .range-input-group {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
  }
  .range-input-group .form-input {
    flex: 1;
    border-radius: 4px;
  }
  .range-separator {
    font-size: 14px;
    color: #999;
    font-weight: 500;
  }
  .popover-footer {
    padding: 8px 0 0;
    border-top: none;
    background: transparent;
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
    border-top: 1px solid #f5f7fa;
  }
  .btn-cancel {
    color: #606266;
    border-color: #dcdfe6;
    background: #fff;
  }
  .btn-confirm {
    background: #409eff;
    border-color: #409eff;
  }
</style>
