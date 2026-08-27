<template>
  <iframe
    ref="iframeRef"
    :src="iframeUrl"
    class="iframe-container"
    sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-downloads"
    referrerpolicy="no-referrer"
    @load="handleIframeLoad"
  />
</template>

<script setup lang="ts">
import { watch, ref } from 'vue';
import { useRoute } from 'vue-router';
import { appBaseUrl, sanitizeIframeUrl } from '@u/url';
import { usePluginUiBridge } from '@/composables/use-plugin-ui-bridge';
const route = useRoute();
const baseUrl = ref<string>('/amis/app.html');
// 提取路径参数并转换为字符串
function getConfigType(): string {
  // 如果 route.params['menuParams'] 不存在，返回默认值 'default'
  return route.params['menuParams']?.toString() || 'default';
}

const buildIframeUrl = () => {
  const params = new URLSearchParams({
    config: getConfigType(),
    baseUrl: appBaseUrl,
  });
  return sanitizeIframeUrl(`${baseUrl.value}?${params.toString()}`);
};

// 初始化 iframeUrl
const iframeUrl = ref<string>(buildIframeUrl());
const iframeRef = ref<HTMLIFrameElement | null>(null);
const { handleIframeLoad } = usePluginUiBridge(iframeRef, iframeUrl);

// 监听路由变化
watch(
  () => route.params['menuParams'], // 只监听 route.params['menuParams'] 的变化
  () => {
    iframeUrl.value = buildIframeUrl();
  },
  { deep: true }
);
</script>

<style lang="scss" scoped>
.iframe-container {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
