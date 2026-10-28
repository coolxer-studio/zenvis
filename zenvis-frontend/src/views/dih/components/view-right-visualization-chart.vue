<template>
  <VisualizationRecordPanel
    :sections="sections"
    :session-record-id="sessionRecordId"
    :session-extra-data="sessionExtraData"
    extra-data-key="visualizationChart"
    @extra-data-changed="handleExtraDataChanged"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import VisualizationRecordPanel from './visualization-record-panel.vue';
import type { VisualizationRecord, VisualizationSection } from './visualization-record-panel.vue';
import {
  DATA_VISUALIZATION_CHART_EXTRA_DATA_CHANGED_EVENT,
  DATA_VISUALIZATION_CHART_RECORD_EVENT,
  DATA_VISUALIZATION_CHART_RECORD_REQUEST_EVENT,
  emitDihEvent,
  useDihEventListener,
} from '../events';
import type { VisualizationChartRecordEventDetail } from '../events';

const chartLibrary = ref<VisualizationRecord[]>([]);
const sessionRecordId = ref('');
const sessionExtraData = ref('');

const asRecordList = (value: unknown): VisualizationRecord[] => Array.isArray(value)
  ? value.filter(item => item && typeof item === 'object').map(item => item as VisualizationRecord)
  : [];

const sections = computed<VisualizationSection[]>(() => [
  {
    name: 'chartLibrary',
    label: '图表库',
    items: chartLibrary.value,
    keyProp: 'chartType',
    keyLabel: '图表类型',
    emptyText: '暂无临时图表记录',
  },
]);

const handleRecordsUpdated = (detail: VisualizationChartRecordEventDetail) => {
  chartLibrary.value = asRecordList(detail?.chartLibrary);
  sessionRecordId.value = typeof detail?.sessionRecordId === 'string' ? detail.sessionRecordId : '';
  sessionExtraData.value = typeof detail?.extraData === 'string' ? detail.extraData : '';
};

const handleExtraDataChanged = (extraData: string) => {
  sessionExtraData.value = extraData;
  emitDihEvent(DATA_VISUALIZATION_CHART_EXTRA_DATA_CHANGED_EVENT, { extraData });
};

useDihEventListener(DATA_VISUALIZATION_CHART_RECORD_EVENT, handleRecordsUpdated);
onMounted(() => emitDihEvent(DATA_VISUALIZATION_CHART_RECORD_REQUEST_EVENT));
</script>
