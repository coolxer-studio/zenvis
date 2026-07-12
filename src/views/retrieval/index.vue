<template>
  <div class="main-content data-retrieval" id="data-list">
    <el-icon class="double-right-outlined" @click="onOpen"><ArrowRight /></el-icon>
    <el-drawer
      placement="left"
      direction="ltr"
      :closable="false"
      v-model="visible"
      :close-on-click-modal="true"
      :withHeader="false"
      :modal="true"
      :modal-append-to-body="false"
      modal-class="rule-drawer-modal"
      class="rule-drawer"
      @close="onClose"
    >
      <el-icon class="double-left-outlined" @click="onClose"><ArrowLeft /></el-icon>
      <div>
        <div class="rule-div create-rule-option" @click="resetFilter">新建过滤器</div>
        <div class="rule-title">规则列表</div>
        <div v-for="item in ruleList" :class="'rule-div ' + ((item as any).id == activeRule ? 'active-rule' : '')">
          <div @click="getRuleDetail((item as any))">{{(item as any).name}}</div>
          <el-icon class="delete-icon" @click="delRule((item as any).id)"><Close /></el-icon>
        </div>
      </div>
    </el-drawer>
    <DataFilter
      :activeRule="activeRule"
      :activeRuleName="activeRuleName"
      :ruleDetailNum="ruleDetailNum"
      :resetNum="resetNum"
      @on-query="getQuery"
      @on-query1="getQueryAndData"
      @on-create="createRuleShow"
      @on-col="getTableCol"
      @on-update="updateRuleShow"
      @on-query2="getQueryAndData"
    />
    <DataList
      :state="tableState"
      @on-change="onChange"
      @on-resize="onResize"
      @on-display="getDisplayCol"
      @on-click="onClickData"
    />
    <el-dialog class="json-data-model" width="800px" v-model="visibleJson" title="数据查看" @close="handleOk">
      <json-viewer :value="deviceData" :expand-depth="1" copyable boxed sort theme="my-json-theme" ></json-viewer>
    </el-dialog>
    <el-dialog v-model="visibleCreateRule" title="保存过滤器">
      <el-form
        ref="formRef"
        :model="formRule"
        :rules="formRules"
        name="login"
        autocomplete="off"
        label-width="120px"
      >
        <el-form-item label="过滤器名称" prop="name">
          <el-input class="login-input" placeholder="请输入过滤器名称" v-model="formRule.name">
          </el-input>
          <span class="rule-span">为此过滤器输入一个名称</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleCancel">取消</el-button>
          <el-button type="primary" @click="createRuleFun">确认</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
  import {reactive, ref, toRaw} from "vue";
  import DataList from './components/table.vue';
  import DataFilter from './components/filter.vue';
  import { ArrowRight, ArrowLeft, ArrowDown, Close } from '@element-plus/icons-vue';
  import {listResponse, TDynamicTableParams, TPagination, TTable} from "@/types/type-public";
  import { RetrievalService } from '@/service/api';
  import { merge } from 'lodash-es';
  import {EntityResponse, RetrievalSearchRequest} from "@/types/type-retrieval";
  import {ElMessage, ElMessageBox} from "element-plus";
  import type { FormInstance, FormRules } from 'element-plus';
  import {getContainer} from "@/types/type-modal";
  import {ls} from "@u/local-storage";
  interface IRuleFrom {
    name: string;
  }
  type ITableState = TTable<any> & {
    loading: boolean;
    query: any;
  } & TDynamicTableParams;
  const filter = ls.get('__filter__') || {}
  const rule = ls.get('__rule__') || {}
  const visible = ref<boolean>(false)
  const visibleCreateRule = ref<boolean>(false)
  const visibleJson = ref<boolean>(false)
  const isEdit = ref<boolean>(false)
  const onOpen = () => {
    visible.value = true
  }
  const formRef = ref<FormInstance>();
  const formRule = reactive<IRuleFrom>({
    name: ''
  })
  const formRules = reactive<FormRules>({
    name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  });
  const ruleList = ref<[]>()
  const activeRule = ref<number>(rule.ruleId || 0)
  const activeRuleName= ref<string>(rule.ruleName || '')
  const resetNum = ref<number>(0)
  const ruleDetailNum = ref<number>(0)
  const onClose = () => {
    visible.value = false
  }
  const tableState: ITableState = reactive<ITableState>({
    loading: false,
    query: {
      laneId: null,
      name: '',
      riskLevel: '',
      riskType: '',
      operatingSystem: '',
      startAlarm_time: '',
      endAlarm_time: '',
      operatingVersion: '',
      number: '',
      ipAddress: ''
    },
    sourceColumns: [],
    entity: filter.entity,
    disabledTitles: [],
    selectedCol: filter.col ? filter.col : [],
    selectedKeyCol: filter.colKey ? filter.colKey : [],
    columns: [],
    data: [],
    width: 0,
    minWidth: 0,
    pagination: {
      current: 1,
      pageSize: 10,
      total: 0,
      showSizeChanger: true,
      pageSizeOptions: ['10', '20', '30', '40', '50'],
      showTotal: (total: number) =>
        `共 ${total} 条记录 第${tableState.pagination.current}/${Math.ceil(
          tableState.pagination.total / tableState.pagination.pageSize,
        )}页`,
      showQuickJumper: true,
    },
  });
  const currentFilter = ref<RetrievalSearchRequest>()
  const currentSorter = ref<Pick<RetrievalSearchRequest, 'sort_by' | 'order'>>()
  const deviceData = ref<object>({})
  interface ICol {
    entity: string;
    attributeList: object[]
  }
  let currentCol = reactive<ICol>({
    entity: '',
    attributeList: []
  })
  const onResize = (obj) => {
    obj.col.width = obj.w
  }
  const onSearch = () => {

  }
  const getDisplayCol = (val) => {
    currentCol.entity = val.entity
    currentCol.attributeList = val.attributeList
    tableState.selectedCol = val.attributeList
    getData(currentFilter.value)

    /*currentCol.value.entity = val.state.entity as string
    currentCol.value.displayCol = val.col.map((e) => {
      return e.dataIndex
    })*/
  }
  const delRule = (id) => {
    ElMessageBox.confirm(
      '确认删除此过滤器吗？',
      '删除',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(() => {
      RetrievalService.deleteRule({id: id}).then((res: any) => {
        ElMessage.success('删除成功');
        getRuleFun();
      });
    }).catch(() => {
      ElMessage.info('已取消删除');
    });
  }
  const updateRuleShow = (params) => {
    visibleCreateRule.value = true
    currentFilter.value = params
  }
  const createRuleShow = (params) => {
    visibleCreateRule.value = true
    currentFilter.value = params
  }
  const handleCancel = () => {
    visibleCreateRule.value = false
    formRef.value?.resetFields()
  }
  const resetFilter = () => {
    //ls.remove('__filter__')
    ls.remove('__rule__')
    activeRule.value = 0
    activeRuleName.value = ''
    resetNum.value++
    onClose()
  }
  const createRuleFun = () => {
    formRef.value?.validate((valid: boolean) => {
      if (valid) {
        const currentColData = {
          entity: currentCol.entity as string,
          attribute_list: currentCol.attributeList.map((e:any) => {
            return e.dataIndex
          })
        }
        if (activeRule.value) {
          RetrievalService.updateRule({rule_name: formRule.name, ...currentFilter.value, display_list: [currentColData], id: activeRule.value}).then((res: any) => {
            ElMessage.success('更新成功');
            getRuleFun()
            getRuleDetail({id: activeRule.value, name: formRule.name})
            visibleCreateRule.value = false
            formRef.value?.resetFields()
          })
        } else {
          RetrievalService.createRule({rule_name: formRule.name, ...currentFilter.value, display_list: [currentColData]}).then((res: any) => {
            ElMessage.success('保存成功');
            getRuleFun()
            visibleCreateRule.value = false
            formRef.value?.resetFields()
          })
        }
      }
    })
  }
  const getTableCol = (params) => {
    tableState.pagination.current = 1
    const allWidth = (document.getElementById('data-list') as any).offsetWidth
    RetrievalService.getCol({entity: params.entity, rule_id: params.rule_id}).then((res: any) => {
      tableState.entity = params.entity
      const num = res.select_attribute_list ? res.select_attribute_list.length : res.attribute_list.length
      const width = Math.floor(allWidth / num)
      tableState.width = width
      tableState.minWidth = 80

      if (res.select_attribute_list) {
        tableState.selectedKeyCol = []
        tableState.selectedCol = res.select_attribute_list.map((e:any, index) => {
          tableState.selectedKeyCol?.push(e.name)
          return {
            title: e.label,
            dataIndex: e.name,
            isLink:e.aggregate_link,
            resizable: true,
            width: width,
            minWidth: 80,
            fixed: false,
            sorter: true,
            firstIndex: index,
            type: e.display_type
          }
        })
      }

      tableState.sourceColumns = res.attribute_list.map((e:any, index) => {
        const currentItem = tableState.selectedCol.find((_e) => _e.dataIndex === e.name) || {};
        return {
          title: e.label,
          dataIndex: e.name,
          isLink:e.aggregate_link,
          resizable: true,
          width: width,
          minWidth: 80,
          fixed: false,
          sorter: true,
          firstIndex: index,
          type: e.display_type,
          ...currentItem
        }
      })
      currentCol.entity = params.entity
      currentCol.attributeList = tableState.selectedCol || []
      getData(currentFilter.value)
    })
  }
  const getRuleDetail = (params) => {
    ruleDetailNum.value++
    activeRule.value = params.id
    activeRuleName.value = params.name
    formRule.name = activeRuleName.value
    ls.set('__rule__', {ruleName: activeRuleName.value, ruleId: activeRule.value} as any)
    onClose()
    //ls.set('__filter__',{...ls.get('__filter__'), ruleName: activeRuleName.value, ruleId: activeRule.value } as any)
    /*ruleDetail(params).then((res: any) => {
      activeRule.value = res
    })*/
  }
  const getRuleFun = () => {
    RetrievalService.getRule().then((res: any) => {
      ruleList.value = res.datalist
    })
  }
  const onChange = ({ pagination, sorter }: { pagination: TPagination, sorter: any }): void => {
    merge(tableState.pagination, pagination);
    if (sorter?.prop || sorter?.field) {
      const sortField = sorter.prop || sorter.field
      if (sorter.order) {
        currentSorter.value = {
          sort_by: sortField,
          order: sorter.order === 'ascending' || sorter.order === 'ascend' ? 'asc' : 'desc',
        }
      } else {
        currentSorter.value = undefined
      }
    }
    getData(currentFilter.value)
};
  const getQuery = (params: RetrievalSearchRequest) => {
    currentFilter.value = params
  }
  const getQueryAndData = (params: RetrievalSearchRequest) => {
    currentFilter.value = params
    tableState.pagination.current = 1
    getData(currentFilter.value)
  }
 const getData = (params?: RetrievalSearchRequest) => {
   if (!params?.entity || !currentCol.attributeList.length) {
     return
   }
   const pageParams = {
     size: tableState.pagination.pageSize,
     page: tableState.pagination.current,
   }
   const currentColData = {
     entity: params.entity as string,
     attribute_list: currentCol.attributeList.map((e:any) => {
       return e.dataIndex
     })
   }
   RetrievalService.getListByCriteria({...pageParams, ...params, display_list: [currentColData], ...currentSorter.value}).then((res: listResponse<any>) => {
     tableState.data = res.datalist
     tableState.pagination.total = res.total || 0;
  })
 }
  getRuleFun()
  const handleOk = () => {
    visibleJson.value = false
  }
  const onClickData = (data) => {
    visibleJson.value = true
    // 判断是否是对象类型
    if (typeof data === 'object') {
      deviceData.value = data
    }else {
      deviceData.value = JSON.parse(data)
    }
  }
</script>
<style lang="scss" scoped>
  .data-retrieval{
    position: relative;
    padding-left: 30px;
    .double-left-outlined{
      position: absolute;
      top: 45%;
      right: 10px;
      font-size: 20px;
      color: #bfbbbb;
      cursor: pointer;
      z-index: 99;
    }
    .double-right-outlined{
      position: absolute;
      top: 15px;
      left: 5px;
      font-size: 20px;
      color: #bfbbbb;
      cursor: pointer;
      z-index: 99;
    }
    :deep(.rule-drawer) {
      left: 0 !important;
      top: 60px !important;
      bottom: 20px;
      box-shadow: 2px 0 10px rgba(0, 0, 0, 0.1);
      margin: 25px 0 0 20px;
    }
  }
  .rule-span{
    color: #7c8087;
    font-size: 12px;
  }
  .rule-div{
    padding-left: 40px;
    line-height: 35px;
    cursor: pointer;
    position: relative;
    width: 80%;
    .delete-icon {
      position: absolute;
      right: 5px;
      top: 50%;
      transform: translateY(-50%);
      color: #f5222d;
      cursor: pointer;
      font-size: 14px;
    }
  }
  .create-rule-option{
    font-size: 18px;
    padding-bottom: 10px;
    border-bottom: 1px solid #e6e7e8;
    margin-bottom: 10px;
    padding-left: 0px;
    margin-left: 20px;
    width:90%;
  }
  .active-rule{
    font-weight: bold;
  }
  .rule-title{
    font-size: 16px;
    padding-left: 20px;
    line-height: 35px;
  }

  :global(.rule-drawer-modal) {
    background-color: transparent !important;
  }

</style>
