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
import { appBaseUrl, sanitizeIframeUrl } from '@u/url';
const route = useRoute();
const baseUrl = ref<string>('/amis/page.html');
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
  Object.entries(route.query).forEach(([key, value]) => {
    if (key === 'config' || key === 'baseUrl') {
      return;
    }
    const normalizedValue = Array.isArray(value) ? value[0] : value;
    if (normalizedValue !== undefined && normalizedValue !== null) {
      params.set(key, String(normalizedValue));
    }
  });
  return sanitizeIframeUrl(`${baseUrl.value}?${params.toString()}`);
};

// 初始化 iframeUrl
const iframeUrl = ref<string>(buildIframeUrl());

// 监听路由变化
watch(
  () => [route.params['menuParams'], route.query], // 监听路径参数和透传查询参数
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
