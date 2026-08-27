<template>
  <iframe
    ref="iframeRef"
    :src="iframeUrl"
    frameborder="0"
    class="dashboard-iframe"
    sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-downloads"
    referrerpolicy="no-referrer"
    @load="handleIframeLoad"
  ></iframe>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { sanitizeIframeUrl } from '@u/url';
import { usePluginUiBridge } from '@/composables/use-plugin-ui-bridge';

const props = defineProps({
  data: {
    type: Object,
    default: () => {
      return {};
    },
  },
});

const iframeUrl = computed(() => sanitizeIframeUrl(String(props.data?.url || '')));
const iframeRef = ref<HTMLIFrameElement | null>(null);
const { handleIframeLoad } = usePluginUiBridge(iframeRef, iframeUrl);
</script>

<style lang="scss" scoped>
.dashboard-iframe {
  width: 100%;
  height: 100%;
}
</style>
