<template>
  <div style="position: relative">
    <el-popover :visible="colShow" placement="bottom-end" :width="240" :popper-options="{ modifiers: [{ name: 'preventOverflow', options: { boundary: 'viewport' } }] }">
      <template #reference>
        <div style="cursor: pointer;position: absolute;top: -40px;right: 0" @click.stop="colShow = !colShow">列
          <el-icon v-if="colShow"><ArrowUp /></el-icon>
          <el-icon v-else><Bottom /></el-icon>
        </div>
      </template>
      <div class="more-filter">
        <template v-if="state.sourceColumns">
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

          <div class="all-filter">筛选列</div>
          <el-checkbox-group v-model="state.selectedKeyCol" style="width: 100%" @click.stop @change="getSelect">
            <div v-for="item in state.sourceColumns" style="height: 35px;line-height: 35px">
              <template v-if="moreSearch">
                <el-checkbox :value="(item as any).dataIndex" v-show="(item as any).title.indexOf(moreSearch) != -1">{{(item as any).title}}</el-checkbox>
              </template>
              <template v-else>
                <el-checkbox :value="(item as any).dataIndex">{{(item as any).title}}</el-checkbox>
              </template>
            </div>
          </el-checkbox-group>
        </template>
        <template v-else>
          <div>请先选择实体</div>
        </template>
      </div>
    </el-popover>
    <el-table
      :data="state.data"
      :loading="state.loading"
      :key="tableKey"
      border
      @sort-change="handleSortChange"
    >
      <template v-for="(item, index) in state.selectedCol" :key="item.dataIndex">
        <el-table-column
          :prop="item.dataIndex"
          :label="item.title"
          :min-width="getMinWidth(item.title)"
          :fixed="item.fixed"
          sortable="custom"
        >
          <template #header>
            <div class="header-cell">
              <span class="header-title">{{ item.title }}</span>
              <el-dropdown class="header-menu" trigger="click">
                <el-button class="header-menu-btn" type="text" size="small">
                  <el-icon><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-menu>
                    <el-menu-item v-if="item.fixed != 'left'" @click="fixedOption('left', item, index)">固定居左</el-menu-item>
                    <el-menu-item v-if="item.fixed != 'right'" @click="fixedOption('right', item, index)">固定居右</el-menu-item>
                    <el-menu-item v-if="item.fixed == 'right' || item.fixed == 'left'" @click="fixedOption(false, item, index)">固定取消</el-menu-item>
                  </el-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
          <template #default="scope">
            <div class="cell-style" :title="scope.row[item.dataIndex]">
              <template v-if="item.type == 'json'">
                <el-tooltip content="数据查看" placement="top">
                  <img class="json-svg" src="/src/assets/svg-icon/json.svg" @click="showData(scope.row[item.dataIndex])" alt="">
                </el-tooltip>
              </template>
              <template v-else>
                <template v-if="item.isLink">
                  <template v-if="scope.row[item.dataIndex] && scope.row[item.dataIndex].length > 8">
                    <span @click="goAggregate(item.dataIndex, scope.row[item.dataIndex])" class="dev-style">
                      {{ scope.row[item.dataIndex].substr(0,4) }}...{{ scope.row[item.dataIndex].substr(scope.row[item.dataIndex].length-4,4) }}
                    </span>
                    <el-icon class="copy-outlined-ico" title="点击复制" @click.stop="touchCopy(scope.row[item.dataIndex])"><DocumentCopy /></el-icon>
                  </template>
                  <template v-else>
                    <span @click="goAggregate(item.dataIndex, scope.row[item.dataIndex])" class="dev-style">
                      {{ scope.row[item.dataIndex] }}
                    </span>
                  </template>
                </template>
                <template v-else>
                  {{ scope.row[item.dataIndex] }}
                </template>
              </template>
            </div>
          </template>
        </el-table-column>
      </template>
    </el-table>
    
    <!-- 分页组件 -->
    <div class="pagination-container">
      <span class="total-info">
        共 {{ state.pagination?.total || 0 }} 条记录 第 {{ state.pagination?.current || 1 }}/{{ Math.ceil((state.pagination?.total || 0) / (state.pagination?.pageSize || 10)) }} 页
      </span>
      <el-pagination
        :current-page="state.pagination?.current || 1"
        :page-size="state.pagination?.pageSize || 10"
        :total="state.pagination?.total || 0"
        :page-sizes="[10, 20, 50, 100]"
        layout="prev, pager, next, jumper, ->, sizes, total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
  import {ElMessage, ElMessageBox} from 'element-plus';
  import { ArrowDown, Search, Bottom, ArrowUp, DocumentCopy } from '@element-plus/icons-vue';
  import {onBeforeUnmount, onMounted, ref, toRaw} from "vue";
  import {useRouter} from "vue-router";
  import useClipboard from 'vue-clipboard3'
  import type { TDynamicTableParams, TTable } from '@/types/type-public';

  type RetrievalTableState = Omit<TDynamicTableParams, 'sourceColumns' | 'selectedCol' | 'selectedKeyCol'> & Omit<TTable<any>, 'columns'> & {
    sourceColumns: any[];
    selectedCol: any[];
    selectedKeyCol?: string[];
    columns: any[];
    loading?: boolean;
  };

  const props = withDefaults(defineProps<{
    state?: RetrievalTableState;
  }>(), {
    state: () => ({
      sourceColumns: [],
      disabledTitles: [],
      selectedCol: [],
      selectedKeyCol: [],
      columns: [],
      data: [],
      pagination: {
        current: 1,
        pageSize: 10,
        total: 0,
      },
      loading: false,
    }),
  });
  const router = useRouter();
  const { toClipboard } = useClipboard()
  const copy = async (val: string) => {
    try{
      await toClipboard(val)
      ElMessage.success('已复制' + ': ' + val);
    } catch (e) {
      ElMessage.error('复制失败,请手动复制!');
    }
  }
  const touchCopy = (val: unknown) => {
    copy(String(val))
  }
  const emit = defineEmits({
    'on-display': null,
    'on-edit': null,
    'on-del': null,
    'on-change': null,
    'on-down': null,
    'on-resize': null,
    'on-click': null,
  });
  const tableKey = ref<number>(0)
  const colSelected = ref<any[]>([])
  const colShow = ref<boolean>(false)
  const moreSearch = ref<string>('')
  
  const getMinWidth = (title: string): number => {
    const charCount = title.length
    const baseWidth = 100
    const charWidth = 18
    const minWidth = baseWidth + charCount * charWidth
    return Math.max(minWidth, 120)
  }
  const handleResizeColumn = (w: number, col: unknown) => {
    emit('on-resize', {w, col});
  }

  const down = (id: number) => {
    emit('on-down', id);
  };
const onSearch = () => {

}
  const showData = (val: unknown) => {
    emit('on-click', val);
  }
  const del = (id: number) => {
    ElMessageBox.confirm(
      '删除业务场景后，关联任务信息不会删除',
      '确认要删除此业务场景吗？',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(() => {
      emit('on-del', id);
    }).catch(() => {
    });
  };
  const getSelect = (val: string[]) => {
    const displayCol = [] as any
    props.state?.sourceColumns.map((e: any) => {
      if (val.indexOf(e.dataIndex) != -1) {
        displayCol.push(e)
      }
    })

    emit('on-display', toRaw({entity: props.state?.entity, attributeList: displayCol}));
    colShow.value = false
  }
  const goAggregate = (field: string, value: unknown) => {
    const query = new URLSearchParams({
      entity_name: props.state?.entity || '',
      [field]: String(value),
    });
    window.open(`/#/aggregate/index?${query.toString()}`, '_blank', 'noopener,noreferrer')
    return
  }
  const handleCurrentChange = (val: number) => {
    emit('on-change', { pagination: { current: val } });
  }
  const handleSizeChange = (val: number) => {
    emit('on-change', { pagination: { pageSize: val, current: 1 } });
  }
  const handleSortChange = (val: unknown) => {
    emit('on-change', { sorter: val });
  }
  const handleDocumentClick = () => {
    colShow.value = false
  }

  onMounted(() => {
    document.addEventListener('click', handleDocumentClick)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('click', handleDocumentClick)
  })
  const swapArray = (arr: any[], index1: number, index2: number) => {
    arr[index1] = arr.splice(index2, 1, arr[index1])[0];
    return arr;
  }
  const fixedOption = (str: string | boolean, item: any, index: number) => {
    item.fixed = str
    let index1 = 0
    let index2 = 0
    if (str == 'left') {
      index1 = index
    }
    if (str == 'right') {
      index1 = index
      index2 = props.state?.selectedCol.length - 1
    }
    if (str == '') {
      index1 = index
      index2 = item.firstIndex
    }
    swapArray(props.state?.selectedCol, index1, index2 )
    tableKey.value++
  }
</script>

<style lang="scss" scoped>
  .all-filter{
    border-top: 1px solid #bec1c6;
    height: 35px;
    line-height: 35px;
    font-size: 16px;
    margin-top: 10px;
  }
  .cell-style{
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .dev-style{
    color: #3988ff;
    cursor: pointer;
    text-decoration:underline;
  }
  .copy-outlined-ico{
    cursor: pointer;
    font-size: 18px;
    color: #34a062;
    margin-left: 10px;
  }
  .header-cell {
    display: flex;
    align-items: center;
    flex: 1 1 auto;
    min-width: 0;
    gap: 6px;
    overflow: hidden;
  }
  .header-title {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .header-menu {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
  }
  .header-menu-btn {
    width: 20px;
    height: 20px;
    padding: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
  :deep(.el-table__header-wrapper th .cell) {
    display: flex;
    align-items: center;
    flex-wrap: nowrap;
    gap: 4px;
    min-width: 0;
    line-height: 20px;
    white-space: nowrap;
  }
  :deep(.el-table__header-wrapper th .cell .caret-wrapper) {
    flex: 0 0 auto;
    margin-left: 2px;
    vertical-align: middle;
  }
  .more-filter{
    border-radius: 5px;
    padding: 10px;
    line-height: 35px;
    max-height: 300px;
    overflow-y: auto;
    background-color: #fff;
  }
  .pagination-container {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 15px;
    padding: 12px 20px;
    background: #fff;
    border-top: 1px solid #ebeef5;
    flex-wrap: nowrap;
    min-height: 60px;
  }
  :deep(.el-pagination) {
    display: flex;
    align-items: center;
    flex-wrap: nowrap;
  }
  :deep(.el-pagination .el-pager) {
    display: flex;
    align-items: center;
  }
  :deep(.el-pagination li) {
    margin: 0 2px;
  }
  .total-info {
    font-size: 14px;
    color: #666;
  }
</style>
