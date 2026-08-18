<template>
  <el-container class="app-container">
    <el-header v-show="isShow" class="header" height="var(--zv-header-height)">
      <nav-logo></nav-logo>
      <nav-menu></nav-menu>
    </el-header>
    <el-container class="drawer-body" id="drawer-body">
      <TopRight v-show="isShow" class="toggle-icon" @click="hideHeader"/>
      <BottomLeft v-show="!isShow" class="toggle-icon" @click="showHeader"/>
      <el-main class="app-body">
        <div class="layout-content-body no-scrollbar">
          <router-view></router-view>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
  import {ref, onMounted, onUnmounted} from "vue";
  import { TopRight, BottomLeft } from '@element-plus/icons-vue';


  import navMenu from './components/nav-menu.vue';
  import navLogo from './components/nav-logo.vue';
  const isShow = ref<boolean>(true)
  const hideHeader = () => {
    isShow.value = false

    // 全屏
    const el = document.querySelector('#drawer-body');
    if (el?.requestFullscreen) {
      el.requestFullscreen();
    }
  }
  const showHeader = () => {
    isShow.value = true

    // 退出全屏
    if (document.exitFullscreen) {
      document.exitFullscreen();
    }
  }
  
  const handleFullscreenChange = () => {
    if (!document.fullscreenElement) {
      isShow.value = true;
    }
  }
  
  onMounted(() => {
    document.addEventListener('fullscreenchange', handleFullscreenChange);
  })
  
  onUnmounted(() => {
    document.removeEventListener('fullscreenchange', handleFullscreenChange);
  })
</script>

<style lang="scss" scoped>
  .app-container {
    width: 100%;
    height: 100%;
    overflow: hidden;
    position: relative;
    .header {
      position: relative;
      z-index: 1000;
      display: flex;
      flex: 0 0 var(--zv-header-height);
      align-items: center;
      padding: 0;
      background: rgb(255 255 255 / 97%);
      border-bottom: 1px solid var(--zv-divider);
      box-shadow: var(--zv-shadow-2);
      backdrop-filter: blur(16px) saturate(140%);
    }
  }
  
  .drawer-body {
    position: relative;
    min-height: 0;
    .toggle-icon {
      position: absolute;
      top: 10px;
      right: 10px;
      color: var(--zv-text-muted);
      font-size: 20px;
      z-index: 9999;
      width: 25px;
      height: 25px;
    }
  }

  .app-body {
    height: 100%;
    box-sizing: border-box;
    overflow: hidden;
    background-color: var(--zv-bg-page);
    padding: 0;
    position: relative;
  }

  .layout-content-body {
    box-sizing: border-box;
    height: 100%;
    overflow-y: auto;
  }
</style>
