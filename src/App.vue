<template>
  <el-config-provider :locale="locale">
    <router-view></router-view>
    <ai-float-ball />
  </el-config-provider>
</template>

<script lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { SystemService } from '@/service/api';
import AiFloatBall from './components/ai-float-ball.vue';
import { getAssetUrl } from '@u/url';

dayjs.locale('zh-cn');

export default {
  name: 'RootApp',
  components: { AiFloatBall },
  setup() {
    const updateFavicon = (iconUrl: string) => {
      const favicon = document.querySelector('link[rel="icon"]') as HTMLLinkElement;
      if (favicon && iconUrl) {
        favicon.href = getAssetUrl(iconUrl);
      }
    };

    const loadSystemInfo = async () => {
      try {
        const res = await SystemService.getSystemInfo();
        if (res?.systemIcon) {
          updateFavicon(res.systemIcon);
        }
      } catch (error) {
        console.error('获取系统信息失败:', error);
      }
    };

    loadSystemInfo();

    return { locale: zhCn };
  },
};
</script>

<style lang="scss"></style>
