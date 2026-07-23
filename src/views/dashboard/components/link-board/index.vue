<template>
  <iframe
    :src="iframeUrl"
    frameborder="0"
    class="dashboard-iframe"
    sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-downloads"
    referrerpolicy="no-referrer"
  ></iframe>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { sanitizeIframeUrl } from '@u/url';

const props = defineProps({
  data: {
    type: Object,
    default: () => {
      return {};
    },
  },
});

const iframeUrl = computed(() => sanitizeIframeUrl(String(props.data?.url || '')));
</script>

<style lang="scss" scoped>
.dashboard-iframe {
  width: 100%;
  height: 100%;
}
</style>
