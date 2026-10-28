<template>
  <VisualizationRecordPanel
    :sections="sections"
    :session-record-id="sessionRecordId"
    :session-extra-data="sessionExtraData"
    extra-data-key="visualizationDashboard"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import VisualizationRecordPanel from './visualization-record-panel.vue';
import type { VisualizationRecord, VisualizationSection } from './visualization-record-panel.vue';
import {
  DATA_VISUALIZATION_DASHBOARD_RECORD_EVENT,
  DATA_VISUALIZATION_DASHBOARD_RECORD_REQUEST_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events';
import type { VisualizationDashboardRecordEventDetail } from '../events';

const configs = ref<VisualizationRecord[]>([]);
const dashboards = ref<VisualizationRecord[]>([]);
const menus = ref<VisualizationRecord[]>([]);
const sessionRecordId = ref('');
const sessionExtraData = ref('');

const asRecordList = (value: unknown): VisualizationRecord[] => Array.isArray(value)
  ? value.filter(item => item && typeof item === 'object').map(item => item as VisualizationRecord)
  : [];

const sections = computed<VisualizationSection[]>(() => [
  {
    name: 'configs',
    label: '看板配置',
    items: configs.value,
    keyProp: 'configType',
    keyLabel: '配置类型',
    emptyText: '暂无数据大屏配置',
  },
  {
    name: 'dashboards',
    label: '数据看板',
    items: dashboards.value,
    keyProp: 'code',
    keyLabel: '看板编码',
    emptyText: '暂无已创建的数据看板',
  },
  {
    name: 'menus',
    label: '菜单入口',
    items: menus.value,
    keyProp: 'menuId',
    keyLabel: '菜单ID',
    emptyText: '暂无看板菜单入口',
  },
]);

const handleRecordsUpdated = (detail: VisualizationDashboardRecordEventDetail) => {
  configs.value = asRecordList(detail?.configs);
  dashboards.value = asRecordList(detail?.dashboards);
  menus.value = asRecordList(detail?.menus);
  sessionRecordId.value = typeof detail?.sessionRecordId === 'string' ? detail.sessionRecordId : '';
  sessionExtraData.value = typeof detail?.extraData === 'string' ? detail.extraData : '';
};

useDihEventListener(DATA_VISUALIZATION_DASHBOARD_RECORD_EVENT, handleRecordsUpdated);
onMounted(() => emitDihEvent(DATA_VISUALIZATION_DASHBOARD_RECORD_REQUEST_EVENT));
</script>
