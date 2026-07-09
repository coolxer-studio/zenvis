<template>
  <div class="dashboard">
    <el-icon class="double-right-outlined" @click="onOpen"><ArrowRight /></el-icon>
    <el-drawer
      placement="left"
      direction="ltr"
      :closable="false"
      v-model="visible"
      :show-mask="false"
      :withHeader="false"
      :modal="false"
      :modal-append-to-body="false"
      class="dashboard-drawer"
      @close="onClose"
    >
      <el-icon class="double-left-outlined" @click="onClose"><ArrowLeft /></el-icon>
      <div>
        <template v-for="(item, index) in dashboardListData">
          <div
            :class="
              'dashboard-div ' + (currentDashboard.code == item.code ? 'active-dashboard' : '')
            "
            @click="setDashboard(item)"
          >
            {{ item.name }}
          </div>
        </template>
      </div>
    </el-drawer>

    <linkBoard v-if="currentDashboard.type == 'LINK'" :data="currentDashboard" />
    <lowCodeBoard v-if="currentDashboard.type == 'LOW_CODE_PAGE'" :data="currentDashboard" />
    <htmlBoard v-if="currentDashboard.type == 'HTML_PAGE'" :data="currentDashboard" />
    <msgBoard v-if="currentDashboard.type == 'BUILT'" :data="currentDashboard" />
  </div>
</template>
<script setup>
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ArrowRight, ArrowLeft } from '@element-plus/icons-vue';

import linkBoard from './components/link-board/index.vue';
import lowCodeBoard from './components/low-code-board/index.vue';
import htmlBoard from './components/html-board/index.vue';
import msgBoard from './components/msg-board/index.vue';

import { SystemService } from '@/service/api';

const visible = ref(false);
const route = useRoute();
const currentDashboard = ref({
  type: '',
  code: '',
});
const dashboardListData = ref([]);

const onOpen = () => {
  visible.value = true;
};

const onClose = () => {
  visible.value = false;
};

const getDashboardList = () => {
  SystemService.getDashboardList()
      .then(data => {
      dashboardListData.value = data;
      selectDashboardFromRoute();
    })
    .finally(() => {});
};

const selectDashboardFromRoute = () => {
  if (!dashboardListData.value.length) {
    currentDashboard.value = { type: '', code: '' };
    return;
  }
  const targetId = route.query.id?.toString() || '';
  const targetCode = route.query.code?.toString() || '';
  const targetName = route.query.name?.toString() || '';
  const matched = dashboardListData.value.find(item => {
    return (targetId && String(item.id) === targetId)
      || (targetCode && item.code === targetCode)
      || (targetName && item.name === targetName);
  });
  currentDashboard.value = matched || dashboardListData.value[0];
};

const setDashboard = item => {
  currentDashboard.value = item;
};

onMounted(() => {
  getDashboardList();
});

watch(
  () => [route.query.id, route.query.code, route.query.name],
  () => {
    selectDashboardFromRoute();
  },
);
</script>

<style lang="scss">
.dashboard {
  background-color: #fff;
  position: relative;
  height: 100%;
  .double-left-outlined {
    position: absolute;
    top: 45%;
    right: 10px;
    font-size: 20px;
    color: #e5e0e0;
    cursor: pointer;
    z-index: 99;
  }
  .double-right-outlined {
    position: absolute;
    top: 10px;
    left: 10px;
    font-size: 20px;
    color: #e5e0e0;
    cursor: pointer;
    z-index: 99;
  }
  .sub-title {
    color: #93d2f3;
  }
  .table-list {
    width: 100%;
    margin: 0 auto;
  }
  .dashboard-div {
    color: #c9cbce;
    height: 40px;
    line-height: 40px;
    padding-left: 20px;
    cursor: pointer;
  }
  .active-dashboard {
    background-color: #3495fa;
    color: #fff;
  }
}

// 重写抽屉el-drawer 底色为透明
.dashboard .dashboard-drawer {
  background-color: transparent !important;
}

.dashboard .dashboard-drawer .el-drawer__body {
  background-color: rgba(0, 0, 0, 0.3) !important;
  padding: 0;
}
</style>
