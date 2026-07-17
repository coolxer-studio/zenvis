<template>
  <el-menu mode="horizontal" class="login-header-nav" v-model:active-index="current">
    <template v-for="item in menuList" :key="item.code">
      <el-menu-item v-if="!item.children" :index="item.code" class="menu-item-with-superscript" @click="goMenu(item)">
        <span class="nav-menu-link">
          {{item.name}}
          <sup v-if="item.superscript" class="menu-superscript">{{ item.superscript }}</sup>
        </span>
        <div class="nav_triangle"></div>
      </el-menu-item>
      <el-sub-menu v-else :index="item.code" class="menu-item-with-superscript" popper-class="nav-submenu-popper">
        <template #title>
          <span style="position: relative; display: inline-block;">
            {{item.name}}
            <sup v-if="item.superscript" class="menu-superscript">{{ item.superscript }}</sup>
            <div class="nav_triangle"></div>
          </span>
        </template>
        <el-menu-item v-for="child in item.children" :key="child.code" :index="child.code" @click="goMenu(child)">
          <span>{{child.name}}</span>
        </el-menu-item>
      </el-sub-menu>
    </template>
  </el-menu>
  <el-dropdown class="user-option" trigger="click">
    <span class="el-dropdown-link">
      <el-icon><User /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item>
          <div style="display: flex; flex-direction: column; width: 100%;">
            <div style="display: flex; align-items: center; color: #7c8087;">
              <el-icon><User /></el-icon>
              <span style="margin-left: 4px;">{{userInfo.name}}</span>
            </div>
            <div style="height: 1px; background-color: #d5d7d7; margin: 4px 0; width: 100%;"></div>
            <div style="color: #7c8087;">{{userInfo.email}}</div>
          </div>
        </el-dropdown-item>
        <el-dropdown-item @click="updatePassword">
          <el-icon><Dish /></el-icon>
          <span>修改密码</span>
        </el-dropdown-item>
        <el-dropdown-item @click="logOut">
          <el-icon><SwitchButton /></el-icon>
          <span>退出</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <Password :show="showPassword" @on-ok="submit" @on-cancel="closeModel" />
</template>
<script setup lang="ts">
  import { ref } from 'vue';
  import { User, SwitchButton, Dish } from '@element-plus/icons-vue';
  import { UserService } from '@/service/api';
  import { useRouter } from "vue-router";
  import Password from './nav-password.vue';
  import JSEncrypt from 'jsencrypt'
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { clearLoginSession, getPermissionList, getUserInfo } from '@u/auth-session';
  const router = useRouter();
  const menuList = getPermissionList<any[]>() || []
  let showPassword = ref<boolean>(false)
  const userInfo = getUserInfo<{ name?: string; email?: string }>() || {}
  const current = ref<string>(router.currentRoute.value.name as string);
  const goMenu = (item: any) => {
    if (!item?.route) return;
    router.push({
      name: item.route,
      params: { menuParams: item.params },
    });
  }
  const logOut = () => {
    ElMessageBox.confirm('请确认是否退出登录？', '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    }).then(() => {
      UserService.doLogOut().then(() => {
        clearLoginSession();
        router.push({
          name: 'login',
        });
      });
    }).catch(() => {});
  }
  const updatePassword = () => {
    showPassword.value = true
  }
  const submit = (params: any) => {
    const encryptor = new JSEncrypt()
    UserService.getEncrypyKey().then((res: any) => {
      encryptor.setPublicKey(res.key)
      UserService.editPassword({...params, old_password: encryptor.encrypt(params.old_password), password: encryptor.encrypt(params.password)}).then((res: any) => {
        ElMessage.success('修改成功');
        showPassword.value = false
        logOut()
      })
    });
  }
  const closeModel = () => {
    showPassword.value = false
  }
</script>
<style lang="less" scoped>
  .login-header-nav{
    background-color: unset;
    color: #fff;
    font-size: 32px;
    font-weight: 600;
    border-bottom: none !important;
    
    :deep(.el-menu-item.is-active) {
      background-color: rgba(255, 255, 255, 0.2) !important;
      color: #fff !important;
      border-bottom: 2px solid #fff !important;
      
      a {
        color: #fff !important;
      }
    }
    
    :deep(.el-sub-menu.is-active) {
      color: #fff !important;

      .el-sub-menu__title {
        background-color: rgba(255, 255, 255, 0.2) !important;
        color: #fff !important;
        border-bottom: 2px solid #fff !important;

        a {
          color: #fff !important;
        }
      }
    }
  }
  .user-option{
    position: absolute;
    top: 20px;
    right: 20px;
    .el-icon{
      font-size: 26px;
      color: #fff;
      margin-left: 10px;
    }
  }
  .menu-superscript {
    position: absolute;
    top: 10px;
    right: -30px;
    color: #fff;
    padding: 0 6px;
    font-size: 12px;
    z-index: 2;
    line-height: 18px;
    min-width: 18px;
    text-align: center;
    pointer-events: none;
    animation: blink 3s infinite;
    text-shadow:
                0 0 5px #ff3100,
                0 0 10px #ff3100,
                0 0 20px #ff3100,
                0 0 40px #ff3100,
                0 0 80px #ff3100,
                0 0 120px #ff3100,
                0 0 200px #ff3100;
                
  }
  .nav-menu-link {
    position: relative;
    display: inline-block;
  }
  @keyframes blink {
            0%, 100% { opacity: 1; }
            50% { opacity: 0; }
        }
</style>
