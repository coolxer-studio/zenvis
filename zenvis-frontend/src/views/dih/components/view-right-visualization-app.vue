<template>
  <VisualizationRecordPanel
    :sections="sections"
    :session-record-id="sessionRecordId"
    :session-extra-data="sessionExtraData"
    extra-data-key="visualizationApp"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import VisualizationRecordPanel from './visualization-record-panel.vue';
import type { VisualizationRecord, VisualizationSection } from './visualization-record-panel.vue';
import {
  DATA_VISUALIZATION_APP_RECORD_EVENT,
  DATA_VISUALIZATION_APP_RECORD_REQUEST_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events';
import type { VisualizationAppRecordEventDetail } from '../events';

const configs = ref<VisualizationRecord[]>([]);
const menus = ref<VisualizationRecord[]>([]);
const sessionRecordId = ref('');
const sessionExtraData = ref('');

const asRecordList = (value: unknown): VisualizationRecord[] => Array.isArray(value)
  ? value.filter(item => item && typeof item === 'object').map(item => item as VisualizationRecord)
  : [];

const sections = computed<VisualizationSection[]>(() => [
  {
    name: 'configs',
    label: '应用配置',
    items: configs.value,
    keyProp: 'configType',
    keyLabel: '配置类型',
    emptyText: '暂无可视化应用配置',
  },
  {
    name: 'menus',
    label: '应用菜单',
    items: menus.value,
    keyProp: 'menuId',
    keyLabel: '菜单ID',
    emptyText: '暂无应用菜单记录',
  },
]);

const handleRecordsUpdated = (detail: VisualizationAppRecordEventDetail) => {
  configs.value = asRecordList(detail?.configs);
  menus.value = asRecordList(detail?.menus);
  sessionRecordId.value = typeof detail?.sessionRecordId === 'string' ? detail.sessionRecordId : '';
  sessionExtraData.value = typeof detail?.extraData === 'string' ? detail.extraData : '';
};

useDihEventListener(DATA_VISUALIZATION_APP_RECORD_EVENT, handleRecordsUpdated);
onMounted(() => emitDihEvent(DATA_VISUALIZATION_APP_RECORD_REQUEST_EVENT));
</script>
