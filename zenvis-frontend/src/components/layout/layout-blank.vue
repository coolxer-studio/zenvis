<template>
  <el-container class="app-container">
    <el-container class="drawer-body" id="drawer-body">
      <el-main class="app-body">
        <div class="layout-content-body no-scrollbar">
          <router-view></router-view>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style lang="scss" scoped>
.app-container {
  width: 100%;
  height: 100%;
  min-height: 100vh;
}

:deep(.el-container) {
  width: 100%;
  height: 100%;
}

:deep(.el-main) {
  padding: 0;
  margin: 0;
}

.app-body {
  height: 100%;
  box-sizing: border-box;
  overflow: hidden;
  background-color: #fff;
  padding: 0;
  margin: 0;
}

.layout-content-body {
  box-sizing: border-box;
  height: 100%;
  overflow-y: auto;
}
</style>
