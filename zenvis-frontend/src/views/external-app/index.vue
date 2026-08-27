<template>
  <iframe
    :src="iframeUrl"
    class="iframe-container"
    sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-downloads"
    referrerpolicy="no-referrer"
  />
</template>

<script setup lang="ts">
import { watch, ref } from 'vue';
import { useRoute } from 'vue-router';
import { sanitizeIframeUrl } from '@u/url';

const route = useRoute();
const iframeUrl = ref<string>(sanitizeIframeUrl('')); // 默认值为 404 页面

// 提取路径参数并解码 Base64
function getDecodedUrl(): string {
  // 如果 route.params['menuParams'] 不存在，返回默认值 'default'
  const encodedParam = route.params['menuParams']?.toString() || 'default';
  try {
    // 解码 Base64
    const decodedParam = atob(encodedParam);
    return sanitizeIframeUrl(decodedParam);
  } catch (error) {
    console.error('Base64 解码失败:', error);
    return sanitizeIframeUrl('');
  }
}

// 初始化 iframeUrl
iframeUrl.value = getDecodedUrl();

// 监听路由变化
watch(
  () => route.params['menuParams'], // 只监听 route.params['menuParams'] 的变化
  () => {
    iframeUrl.value = getDecodedUrl();
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
