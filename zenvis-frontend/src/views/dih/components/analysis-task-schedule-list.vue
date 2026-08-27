<template>
  <section class="schedule-section">
    <el-form :model="filters" inline class="filter-form" @submit.prevent="search">
      <el-form-item label="配置名称">
        <el-input
          v-model="filters.name"
          clearable
          placeholder="通过配置名称搜索"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item label="启用状态">
        <el-select v-model="filters.enabled" clearable placeholder="全部状态" class="filter-select">
          <el-option label="已启用" :value="true" />
          <el-option label="已停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="table-wrap">
      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        border
        height="100%"
        empty-text="暂无AI分析周期任务"
      >
        <el-table-column prop="id" label="ID" width="66" fixed="left" />
        <el-table-column
          prop="name"
          label="配置名称"
          min-width="170"
          fixed="left"
          show-overflow-tooltip
        />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '已启用' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cronExpression" label="Cron" min-width="160" />
        <el-table-column label="下次触发" width="170">
          <template #default="{ row }">{{ formatTime(row.nextFireTime) }}</template>
        </el-table-column>
        <el-table-column label="上次触发" width="170">
          <template #default="{ row }">{{ formatTime(row.lastFireTime) }}</template>
        </el-table-column>
        <el-table-column prop="generatedCount" label="已生成" width="88" align="center" />
        <el-table-column prop="model" label="模型" min-width="120">
          <template #default="{ row }">{{ row.model || 'auto' }}</template>
        </el-table-column>
        <el-table-column label="审批模式" width="118">
          <template #default="{ row }">
            {{ row.approvalMode === 'AUTO' ? '自动批准 ASK' : '人工审批' }}
          </template>
        </el-table-column>
        <el-table-column prop="lastError" label="最近错误" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastError || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="250">
          <template #default="{ row }">
            <div class="schedule-actions">
              <el-button link type="primary" @click="emit('view-tasks', row)">生成记录</el-button>
              <el-button link @click="emit('edit', row)">编辑</el-button>
              <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggle(row)">
                {{ row.enabled ? '停用' : '启用' }}
              </el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="filters.page"
        v-model:page-size="filters.perPage"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load(false)"
        @size-change="handlePageSizeChange"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';
import { RefreshLeft, Search } from '@element-plus/icons-vue';
import { AnalysisTaskScheduleService } from '@/service/api';
import type {
  TAnalysisTaskSchedule,
  TAnalysisTaskScheduleSearch,
} from '@/types/type-analysis-task';

defineOptions({ name: 'AnalysisTaskScheduleList' });

const emit = defineEmits<{
  (event: 'edit', schedule: TAnalysisTaskSchedule): void;
  (event: 'view-tasks', schedule: TAnalysisTaskSchedule): void;
}>();

const rows = ref<TAnalysisTaskSchedule[]>([]);
const total = ref(0);
const loading = ref(false);
const filters = reactive<TAnalysisTaskScheduleSearch>({
  name: '',
  enabled: '',
  page: 1,
  perPage: 10,
});

const load = async (silent = false) => {
  if (loading.value) return;
  loading.value = true;
  try {
    const response = await AnalysisTaskScheduleService.getList(filters, silent);
    rows.value = response.rows;
    total.value = response.total;
  } catch (error) {
    console.error('获取AI分析周期任务失败:', error);
  } finally {
    loading.value = false;
  }
};

const search = () => {
  filters.page = 1;
  load(false);
};

const reset = () => {
  Object.assign(filters, { name: '', enabled: '', page: 1 });
  load(false);
};

const handlePageSizeChange = () => {
  filters.page = 1;
  load(false);
};

const toggle = async (schedule: TAnalysisTaskSchedule) => {
  try {
    await AnalysisTaskScheduleService.setEnabled(schedule.id, !schedule.enabled);
    ElMessage.success(schedule.enabled ? '周期任务已停用' : '周期任务已启用');
    load(true);
  } catch (error) {
    console.error('切换AI分析周期任务状态失败:', error);
  }
};

const remove = async (schedule: TAnalysisTaskSchedule) => {
  try {
    await ElMessageBox.confirm(
      `确定删除周期配置“${schedule.name}”吗？已经生成的任务不会被删除或取消。`,
      '删除周期配置',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    );
    await AnalysisTaskScheduleService.remove(schedule.id);
    ElMessage.success('周期配置已删除');
    if (rows.value.length === 1 && filters.page > 1) filters.page -= 1;
    load(true);
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      console.error('删除AI分析周期任务失败:', error);
    }
  }
};

const formatTime = (value: string) => {
  if (!value) return '-';
  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : value;
};

onMounted(() => load(true));
defineExpose({ refresh: () => load(true) });
</script>

<style scoped lang="scss">
.schedule-section {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.filter-form {
  flex: none;
}

.filter-select {
  width: 150px;
}

.table-wrap {
  min-height: 0;
  flex: 1;
}

.pagination-row {
  display: flex;
  flex: none;
  justify-content: flex-end;
  padding-top: 12px;
}

.schedule-actions {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;

  :deep(.el-button) {
    flex-shrink: 0;
  }
}
</style>
