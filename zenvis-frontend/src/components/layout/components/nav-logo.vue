<template>
  <button class="brand" type="button" aria-label="返回仪表盘" @click="goHome">
    <span class="brand-logo" aria-hidden="true">
      <img v-if="logoUrl" :src="logoUrl" alt="" />
      <el-icon v-else><DataAnalysis /></el-icon>
    </span>
    <span class="brand-copy">
      <strong :title="systemInfo?.systemTitle || 'ZenVis'">
        {{ systemInfo?.systemTitle || 'ZenVis' }}
      </strong>
      <small :title="systemInfo?.systemSubtitle || 'Security Operations'">
        {{ systemInfo?.systemSubtitle || 'Security Operations' }}
      </small>
    </span>
  </button>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { DataAnalysis } from '@element-plus/icons-vue';

import { SystemService } from '@/service/api';
import type { SystemInfo } from '@/types/type-system';
import { getAssetUrl } from '@u/url';

defineOptions({ name: 'NavLogo' });

const router = useRouter();
const systemInfo = ref<SystemInfo>();
const logoUrl = computed(() =>
  systemInfo.value?.systemLogo ? getAssetUrl(systemInfo.value.systemLogo) : '',
);

const loadSystemInfo = async () => {
  try {
    systemInfo.value = await SystemService.getSystemInfo();
  } catch (error) {
    console.error('获取系统信息失败:', error);
  }
};

const goHome = () => router.push({ name: 'dashboard' });

onMounted(loadSystemInfo);
</script>

<style lang="scss" scoped>
.brand {
  display: inline-flex;
  flex: 0 0 252px;
  align-items: center;
  min-width: 0;
  height: 100%;
  padding: 0 18px;
  color: var(--zv-text-primary);
  font: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.brand-logo {
  display: grid;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  overflow: hidden;
  color: var(--zv-text-inverse);
  font-size: 22px;
  place-items: center;
  background: linear-gradient(145deg, var(--zv-primary), var(--zv-accent));
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: var(--zv-radius-lg);
  box-shadow: 0 7px 18px rgb(47 94 229 / 22%);

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
}

.brand-copy {
  display: flex;
  min-width: 0;
  margin-left: 11px;
  flex-direction: column;
  line-height: 1.15;

  strong,
  small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: var(--zv-text-primary);
    font-size: var(--zv-font-size-xl);
    font-weight: var(--zv-font-weight-bold);
    letter-spacing: -0.01em;
  }

  small {
    margin-top: 4px;
    color: var(--zv-text-muted);
    font-size: 9px;
    font-weight: 600;
    letter-spacing: 0.13em;
    text-transform: uppercase;
  }
}

@media (max-width: 1320px) {
  .brand {
    flex-basis: 205px;
    padding-inline: 14px;
  }
}

@media (max-width: 920px) {
  .brand {
    flex-basis: 66px;
    padding-inline: 14px;
  }

  .brand-copy {
    display: none;
  }
}
</style>
