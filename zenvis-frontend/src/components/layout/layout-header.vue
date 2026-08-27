<template>
  <el-container class="app-container">
    <el-header class="header" height="var(--zv-header-height)">
      <nav-logo></nav-logo>
      <nav-menu></nav-menu>
    </el-header>
    <el-container class="drawer-body" id="drawer-body">
      <el-main class="app-body">
        <div class="layout-content-body no-scrollbar">
          <router-view></router-view>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
  import navMenu from './components/nav-menu.vue';
  import navLogo from './components/nav-logo.vue';
</script>

<style lang="scss" scoped>
  .app-container {
    width: 100%;
    height: 100%;
    overflow: hidden;
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
    min-height: 0;
  }

  .app-body {
    height: 100%;
    box-sizing: border-box;
    overflow: hidden;
    background-color: var(--zv-bg-page);
    padding: 0;
  }

  .layout-content-body {
    box-sizing: border-box;
    height: 100%;
    overflow-y: auto;
  }
</style>
