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
              <el-button><el-icon><Search /></el-icon></el-button>
            </template>
          </el-input>

          <div class="all-filter">筛选列</div>
          <el-checkbox-group v-model="state.selectedKeyCol" style="width: 100%" @click.stop @change="getSelect">
            <div v-for="item in state.sourceColumns" :key="item.dataIndex" style="height: 35px;line-height: 35px">
              <template v-if="moreSearch">
                <el-checkbox v-show="item.title.includes(moreSearch)" :value="item.dataIndex">{{ item.title }}</el-checkbox>
              </template>
              <template v-else>
                <el-checkbox :value="item.dataIndex">{{ item.title }}</el-checkbox>
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
      v-loading="state.loading"
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
                <template v-if="resolveLink(item, scope.row)">
                  <template v-if="scope.row[item.dataIndex] && scope.row[item.dataIndex].length > 8">
                    <span @click="openLink(item, scope.row)" class="dev-style">
                      {{ scope.row[item.dataIndex].substr(0,4) }}...{{ scope.row[item.dataIndex].substr(scope.row[item.dataIndex].length-4,4) }}
                    </span>
                    <el-icon class="copy-outlined-ico" title="点击复制" @click.stop="touchCopy(scope.row[item.dataIndex])"><DocumentCopy /></el-icon>
                  </template>
                  <template v-else>
                    <span @click="openLink(item, scope.row)" class="dev-style">
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
import { ElMessage } from 'element-plus';
import { ArrowDown, Search, Bottom, ArrowUp, DocumentCopy } from '@element-plus/icons-vue';
import { onBeforeUnmount, onMounted, ref } from 'vue';
import useClipboard from 'vue-clipboard3';
import type {
  RetrievalTableChange,
  RetrievalTableColumn,
  RetrievalTableState,
  RetrievalTableSorter,
} from '@/types/type-retrieval';
import { resolveRetrievalLink } from '@/utils/retrieval-link';

const props = defineProps<{ state: RetrievalTableState }>();
const emit = defineEmits<{
  (event: 'on-display', value: { entity: string; attributeList: RetrievalTableColumn[] }): void;
  (event: 'on-change', value: RetrievalTableChange): void;
  (event: 'on-click', value: unknown): void;
}>();

const { toClipboard } = useClipboard();
const tableKey = ref(0);
const colShow = ref(false);
const moreSearch = ref('');

async function copy(value: string) {
  try {
    await toClipboard(value);
    ElMessage.success(`已复制: ${value}`);
  } catch {
    ElMessage.error('复制失败,请手动复制!');
  }
}

function touchCopy(value: unknown) {
  void copy(String(value));
}

function getMinWidth(title: string): number {
  return Math.max(100 + title.length * 18, 120);
}

function showData(value: unknown) {
  emit('on-click', value);
}

function getSelect(value: string[]) {
  if (!value.length) {
    ElMessage.warning('至少保留一个展示字段');
    props.state.selectedKeyCol = props.state.selectedCol.map(column => column.dataIndex);
    return;
  }
  if (!props.state.entity) return;
  const selected = new Set(value);
  const displayColumns = props.state.sourceColumns.filter(column => selected.has(column.dataIndex));
  emit('on-display', { entity: props.state.entity, attributeList: displayColumns });
  colShow.value = false;
}

function resolveLink(column: RetrievalTableColumn, row: Record<string, unknown>) {
  return resolveRetrievalLink(column.linkTemplate, row, props.state.selectedKeyCol);
}

function openLink(column: RetrievalTableColumn, row: Record<string, unknown>) {
  const link = resolveLink(column, row);
  if (link) window.open(link, '_blank', 'noopener,noreferrer');
}

function handleCurrentChange(value: number) {
  emit('on-change', { pagination: { current: value } });
}

function handleSizeChange(value: number) {
  emit('on-change', { pagination: { pageSize: value, current: 1 } });
}

function handleSortChange(value: RetrievalTableSorter) {
  emit('on-change', { sorter: value });
}

function handleDocumentClick() {
  colShow.value = false;
}

function fixedOption(fixed: RetrievalTableColumn['fixed'], item: RetrievalTableColumn, _index: number) {
  item.fixed = fixed;
  const rank = (column: RetrievalTableColumn) => column.fixed === 'left' ? 0 : column.fixed === 'right' ? 2 : 1;
  props.state.selectedCol.sort((left, right) => rank(left) - rank(right) || left.firstIndex - right.firstIndex);
  tableKey.value++;
}

onMounted(() => document.addEventListener('click', handleDocumentClick));
onBeforeUnmount(() => document.removeEventListener('click', handleDocumentClick));
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
