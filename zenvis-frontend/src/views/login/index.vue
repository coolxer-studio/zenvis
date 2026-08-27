<template>
  <main class="login-page">
    <div class="ambient ambient-one"></div>
    <div class="ambient ambient-two"></div>

    <section class="brand-panel" :style="bannerStyle">
      <div class="brand-overlay"></div>
      <div class="brand-content">
        <h1>{{ systemInfo?.systemTitle || 'ZenVis' }}</h1>
        <h2>{{ systemInfo?.productName || '数据分析应用框架' }}</h2>
        <p>
          {{
            systemInfo?.productIntroduction ||
            '一个基于配置实现的数据存储、可视化及业务扩展的框架平台，实现在通用的数据分析框架之上构建业务应用。提供智能分析能力，全方位满足数据处理、展示、扩展与深度分析需求。'
          }}
        </p>
        <div class="feature-grid">
          <div class="feature-item">
            <span class="feature-icon">01</span>
            <div><strong>统一态势</strong><small>跨域数据实时汇聚</small></div>
          </div>
          <div class="feature-item">
            <span class="feature-icon">02</span>
            <div><strong>智能协同</strong><small>Agent 驱动调查处置</small></div>
          </div>
          <div class="feature-item">
            <span class="feature-icon">03</span>
            <div><strong>闭环运营</strong><small>从发现到复核可追踪</small></div>
          </div>
        </div>
      </div>
    </section>

    <section class="auth-panel">
      <div class="login-card">
        <div class="login-brand">
          <img v-if="systemInfo?.systemLogo" :src="logoUrl" class="login-logo" alt="系统标识" />
          <span v-else class="login-logo-fallback"><span></span></span>
          <div>
            <strong>{{ systemInfo?.systemTitle || 'ZenVis' }}</strong>
            <small>{{ systemInfo?.systemSubtitle || 'Unified Situation Awareness' }}</small>
          </div>
        </div>

        <el-form
          ref="formRef"
          :model="formLogin"
          :rules="formRules"
          label-position="top"
          size="large"
          autocomplete="off"
          @submit.prevent="onFinish"
        >
          <el-form-item prop="user_name" label="账号">
            <el-input v-model="formLogin.user_name" placeholder="请输入账号" clearable>
              <template #prefix
                ><el-icon><User /></el-icon
              ></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password" label="密码">
            <el-input
              v-model="formLogin.password"
              type="password"
              placeholder="请输入密码"
              show-password
            >
              <template #prefix
                ><el-icon><Lock /></el-icon
              ></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="auth_code" label="验证码">
            <div class="captcha-row">
              <el-input v-model="formLogin.auth_code" placeholder="输入图中字符" maxlength="12">
                <template #prefix
                  ><el-icon><Key /></el-icon
                ></template>
              </el-input>
              <button
                class="captcha-button"
                type="button"
                title="点击刷新验证码"
                @click="refreshCaptcha"
              >
                <img :src="captchaUrl" alt="验证码" />
                <span>换一张</span>
              </button>
            </div>
          </el-form-item>

          <el-button
            class="login-button"
            type="primary"
            size="large"
            native-type="submit"
            :loading="loginLoading"
          >
            登录平台
          </el-button>
        </el-form>

        <div class="login-support">
          <span>登录即代表你同意遵守组织安全策略</span>
          <a
            v-if="systemInfo?.integrateLink"
            :href="systemInfo.integrateLink"
            target="_blank"
            rel="noopener noreferrer"
            >接入指南</a
          >
        </div>
      </div>

      <footer>{{ systemInfo?.copyright || '© ZenVis Security Operations' }}</footer>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Key, Lock, User } from '@element-plus/icons-vue';
import type { FormInstance, FormRules } from 'element-plus';
import JSEncrypt from 'jsencrypt';

import { SystemService, UserService } from '@/service/api';
import type { SystemInfo } from '@/types/type-system';
import { setLoginSession } from '@u/auth-session';
import { getAssetUrl, withBaseUrl, withCacheBuster } from '@u/url';
import loginBanner from '@a/images/login-banner.png';

defineOptions({ name: 'AmpLogin' });

type LoginForm = {
  user_name: string;
  password: string;
  auth_code: string;
};

const router = useRouter();
const formRef = ref<FormInstance>();
const loginLoading = ref(false);
const timestamp = ref(Date.now());
const systemInfo = ref<SystemInfo>();
const formLogin = reactive<LoginForm>({ user_name: '', password: '', auth_code: '' });
const formRules: FormRules<LoginForm> = {
  user_name: [{ required: true, message: '请输入账号', trigger: ['blur', 'change'] }],
  password: [{ required: true, message: '请输入密码', trigger: ['blur', 'change'] }],
  auth_code: [{ required: true, message: '请输入验证码', trigger: ['blur', 'change'] }],
};

const captchaUrl = computed(
  () => `${withCacheBuster(withBaseUrl('/api/v1/system/login/kaptcha'))}&_=${timestamp.value}`,
);
const logoUrl = computed(() =>
  systemInfo.value?.systemLogo ? getAssetUrl(systemInfo.value.systemLogo) : '',
);
const bannerStyle = computed(() => ({
  '--login-banner': `url(${
    systemInfo.value?.systemBanner ? getAssetUrl(systemInfo.value.systemBanner) : loginBanner
  })`,
}));

const refreshCaptcha = () => {
  timestamp.value = Date.now();
};

const onFinish = async () => {
  if (loginLoading.value) return;

  const isValid = await formRef.value?.validate().catch(() => false);
  if (!isValid) {
    await nextTick();
    document.querySelector<HTMLInputElement>('.login-card .el-form-item.is-error input')?.focus();
    return;
  }

  try {
    loginLoading.value = true;

    const encryptor = new JSEncrypt();
    const keyResponse = await UserService.getEncrypyKey();
    encryptor.setPublicKey(keyResponse.key);
    const loginResponse = await UserService.doLogin({
      ...formLogin,
      password: encryptor.encrypt(formLogin.password) || '',
    });

    setLoginSession(loginResponse);
    await router.push({ name: 'dashboard' });
  } catch (error: any) {
    if (error?.code === 102) refreshCaptcha();
  } finally {
    loginLoading.value = false;
  }
};

const loadSystemInfo = async () => {
  try {
    systemInfo.value = await SystemService.getSystemInfo();
  } catch (error) {
    console.error('获取系统信息失败:', error);
  }
};

onMounted(() => {
  document.documentElement.classList.add('login-page-active');
  loadSystemInfo();
  refreshCaptcha();
});

onUnmounted(() => {
  document.documentElement.classList.remove('login-page-active');
});
</script>

<style lang="scss" scoped>
:global(html.login-page-active),
:global(html.login-page-active body),
:global(html.login-page-active #app) {
  height: 100%;
  overflow: hidden;
}

:global(html.login-page-active #app) {
  min-width: 0;
}

.login-page {
  --login-primary: var(--zv-primary);
  --login-cyan: var(--zv-accent);
  --login-bg: var(--zv-bg-page);
  --login-bg-elevated: var(--zv-bg-surface);
  --login-bg-subtle: var(--zv-bg-subtle);
  --login-border: var(--zv-border);
  --login-text: var(--zv-text-primary);
  --login-text-muted: var(--zv-text-muted);
  --login-shadow-lg: var(--zv-shadow-3);
  --login-motion: cubic-bezier(0.2, 0.8, 0.2, 1);

  position: relative;
  display: grid;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  grid-template-columns: minmax(480px, 1.15fr) minmax(440px, 0.85fr);
  background: var(--login-bg);
}

.ambient {
  position: absolute;
  pointer-events: none;
  border-radius: 50%;
  filter: blur(2px);
}

.ambient-one {
  top: -14vw;
  right: -10vw;
  width: 32vw;
  height: 32vw;
  background: radial-gradient(circle, rgba(79, 110, 247, 0.09), transparent 68%);
}

.ambient-two {
  bottom: -15vw;
  left: 34%;
  width: 34vw;
  height: 34vw;
  background: radial-gradient(circle, rgba(34, 184, 207, 0.06), transparent 70%);
}

.brand-panel {
  position: relative;
  z-index: 1;
  display: flex;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: clamp(44px, 6vw, 88px);
  color: #fff;
  flex-direction: column;
  justify-content: center;
  background: linear-gradient(145deg, rgba(7, 16, 39, 0.96), rgba(24, 42, 92, 0.88)),
    var(--login-banner) center / cover;
}

.brand-panel::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  background-image: linear-gradient(rgba(128, 150, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(128, 150, 255, 0.04) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(to bottom right, #000, transparent 85%);
}

.brand-overlay {
  position: absolute;
  top: 12%;
  right: 2%;
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(34, 184, 207, 0.12), transparent 68%);
  border: 1px solid rgba(128, 150, 255, 0.1);
  border-radius: 50%;
  box-shadow: inset 0 0 80px rgba(79, 110, 247, 0.08);
}

.brand-content,
.brand-footer {
  position: relative;
  z-index: 2;
}

.product-chip {
  display: inline-flex;
  align-self: flex-start;
  align-items: center;
  height: 32px;
  padding-inline: 13px;
  color: #dce8ff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.11em;
  text-transform: uppercase;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  backdrop-filter: blur(10px);
}

.status-dot {
  width: 7px;
  height: 7px;
  margin-right: 8px;
  background: #32d69b;
  border-radius: 50%;
  box-shadow: 0 0 12px #32d69b;
}

.brand-content h1,
.brand-content h2 {
  display: -webkit-box;
  max-width: 680px;
  overflow: hidden;
  margin: 28px 0 18px;
  font-size: clamp(38px, 4.5vw, 64px);
  font-weight: 760;
  line-height: 1.12;
  letter-spacing: -0.035em;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.brand-content h2 {
  margin-top: -8px;
  font-size: clamp(24px, 2.5vw, 38px);
  font-weight: var(--zv-font-weight-semibold);
  letter-spacing: -0.02em;
}

.brand-content > p {
  display: -webkit-box;
  max-width: 620px;
  overflow: hidden;
  color: rgba(230, 239, 255, 0.74);
  font-size: clamp(15px, 1.4vw, 18px);
  line-height: 1.8;
  white-space: normal;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.feature-grid {
  display: grid;
  max-width: 710px;
  margin-top: 46px;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.feature-item {
  display: flex;
  min-width: 0;
  padding: 15px;
  align-items: center;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 12px;
  backdrop-filter: blur(8px);
  transition: transform 200ms var(--login-motion), background-color 200ms ease;
}

.feature-item:hover {
  background: rgba(255, 255, 255, 0.065);
  transform: translateY(-2px);
}

.feature-icon {
  display: grid;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  color: #aebcff;
  font-size: 11px;
  font-weight: 800;
  place-items: center;
  background: rgba(128, 150, 255, 0.12);
  border-radius: 10px;
}

.feature-item > div {
  display: flex;
  min-width: 0;
  margin-left: 10px;
  flex-direction: column;
}

.feature-item strong {
  font-size: 13px;
}

.feature-item small {
  margin-top: 3px;
  overflow: hidden;
  color: rgba(230, 239, 255, 0.52);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-footer {
  position: absolute;
  right: clamp(36px, 5vw, 76px);
  bottom: 30px;
  left: clamp(36px, 5vw, 76px);
  display: flex;
  color: rgba(230, 239, 255, 0.46);
  font-size: 11px;
  justify-content: space-between;
}

.brand-footer span:first-child {
  display: inline-flex;
  align-items: center;
}

.brand-footer i {
  width: 6px;
  height: 6px;
  margin-right: 7px;
  background: #32d69b;
  border-radius: 50%;
}

.auth-panel {
  position: relative;
  z-index: 2;
  display: flex;
  min-width: 0;
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 48px clamp(30px, 5vw, 86px) 52px;
  align-items: center;
  flex-direction: column;
  justify-content: center;
}

.login-card {
  box-sizing: border-box;
  width: min(100%, 456px);
  padding: 34px 36px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(225, 231, 240, 0.75);
  border-radius: 16px;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.09);
  backdrop-filter: blur(18px);
}

.login-brand {
  position: relative;
  display: flex;
  width: 100%;
  padding-bottom: 16px;
  align-items: center;
  justify-content: center;
}

.login-brand::after {
  position: absolute;
  right: 50%;
  bottom: 0;
  width: min(72%, 220px);
  height: 2px;
  content: '';
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(79, 110, 247, 0.22) 20%,
    var(--zv-primary) 42%,
    #5ee7f2 50%,
    var(--zv-primary) 58%,
    rgba(79, 110, 247, 0.22) 80%,
    transparent 100%
  );
  background-size: 220% 100%;
  border-radius: 999px;
  box-shadow: 0 0 10px rgba(79, 110, 247, 0.28);
  transform: translateX(50%);
  animation: login-brand-flow 3.2s linear infinite;
}

@keyframes login-brand-flow {
  from {
    background-position: 110% 0;
  }

  to {
    background-position: -110% 0;
  }
}

.login-logo,
.login-logo-fallback {
  width: 64px;
  height: 64px;
  object-fit: contain;
  border-radius: 14px;
}

.login-logo-fallback {
  display: grid;
  place-items: center;
  background: linear-gradient(145deg, var(--login-primary), var(--login-cyan));
  box-shadow: 0 10px 24px rgba(79, 110, 247, 0.25);
}

.login-logo-fallback span {
  width: 12px;
  height: 12px;
  border: 3px solid #fff;
  border-radius: 50%;
}

.login-brand > div {
  display: flex;
  min-width: 0;
  margin-left: 12px;
  flex-direction: column;
}

.login-brand strong {
  overflow: hidden;
  color: var(--login-text);
  font-size: 18px;
  font-weight: 760;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.login-brand small {
  margin-top: 3px;
  color: var(--login-text-muted);
  font-size: 10px;
  font-weight: 650;
  letter-spacing: 0.09em;
}

.login-heading {
  margin: 38px 0 24px;
}

.login-heading > span {
  color: var(--login-primary);
  font-size: 12px;
  font-weight: 700;
}

.login-heading h2 {
  margin: 8px 0 7px;
  color: var(--login-text);
  font-size: 28px;
  font-weight: 760;
  letter-spacing: -0.03em;
}

.login-heading p,
.login-support {
  color: var(--login-text-muted);
  font-size: 13px;
}

.login-card :deep(.el-form-item) {
  margin-bottom: 24px;
}

.login-card :deep(.el-form-item__label) {
  height: auto;
  padding-bottom: 7px;
  color: var(--login-text);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.4;
}

.login-card :deep(.el-form-item__error) {
  padding-top: 5px;
  font-size: 12px;
  line-height: 1.35;
}

.login-card :deep(.el-input__wrapper) {
  min-height: 42px;
  padding-inline: 13px;
  background: var(--login-bg-subtle);
  border: 1px solid var(--login-border);
  border-radius: 10px;
  box-shadow: none;
  transition: border-color 180ms ease, box-shadow 180ms ease, background-color 180ms ease;
}

.login-card :deep(.el-input__wrapper:hover) {
  border-color: #b9c4dc;
}

.login-card :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  border-color: var(--login-primary);
  box-shadow: 0 0 0 3px rgba(79, 110, 247, 0.1);
}

.login-card :deep(.el-input__prefix) {
  color: #8f9ab0;
}

.captcha-row {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(0, 1fr) 126px;
  gap: 10px;
}

.captcha-button {
  position: relative;
  height: 42px;
  overflow: hidden;
  padding: 0;
  background: var(--login-bg-subtle);
  border: 1px solid var(--login-border);
  border-radius: 10px;
  cursor: pointer;
}

.captcha-button img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-button span {
  position: absolute;
  inset: auto 4px 3px auto;
  padding: 1px 5px;
  color: #fff;
  font-size: 9px;
  background: rgba(11, 17, 32, 0.62);
  border-radius: 5px;
  opacity: 0;
  transition: opacity 160ms ease;
}

.captcha-button:hover span,
.captcha-button:focus-visible span {
  opacity: 1;
}

.login-button {
  width: 100%;
  height: 44px;
  margin-top: 6px;
  font-weight: 650;
  background: linear-gradient(135deg, #4d72e8, var(--zv-primary));
  border: none;
  border-radius: 10px;
  box-shadow: 0 10px 24px rgba(79, 110, 247, 0.22);
}

.login-support {
  display: flex;
  margin-top: 22px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.login-support span {
  min-width: 0;
}

.login-support a {
  flex: 0 0 auto;
  color: var(--login-primary);
  font-weight: 650;
  text-decoration: none;
}

.auth-panel footer {
  position: absolute;
  right: 24px;
  bottom: 22px;
  left: 24px;
  overflow: hidden;
  color: var(--login-text-muted);
  font-size: 11px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 980px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }

  .auth-panel {
    padding-inline: 24px;
  }
}

@media (max-width: 520px) {
  .auth-panel {
    padding: 28px 16px 54px;
    justify-content: flex-start;
  }

  .login-card {
    width: 100%;
    padding: 24px 20px;
    background: transparent;
    border-color: transparent;
    border-radius: 0;
    box-shadow: none;
    backdrop-filter: none;
  }

  .login-heading {
    margin-top: 30px;
  }

  .login-heading h2 {
    font-size: 24px;
  }

  .captcha-row {
    grid-template-columns: minmax(0, 1fr) 112px;
  }

  .login-support {
    align-items: flex-start;
  }

  .auth-panel footer {
    position: static;
    width: 100%;
    margin-top: 28px;
    white-space: normal;
  }
}

@media (max-height: 760px) {
  .brand-panel {
    padding-block: 32px;
  }

  .brand-content h1,
  .brand-content h2 {
    margin-block: 18px 12px;
  }

  .feature-grid {
    margin-top: 24px;
  }

  .brand-footer {
    bottom: 16px;
  }

  .auth-panel {
    padding-block: 14px;
  }

  .login-card {
    padding-block: 16px;
  }

  .login-brand {
    padding-bottom: 10px;
  }

  .login-heading {
    margin-block: 12px 14px;
  }

  .login-card :deep(.el-form-item) {
    margin-bottom: 17px;
  }

  .login-card :deep(.el-form-item__label) {
    padding-bottom: 4px;
  }

  .login-support {
    margin-top: 14px;
  }

  .auth-panel footer {
    bottom: 10px;
  }
}

@media (max-width: 520px) and (max-height: 760px) {
  .auth-panel {
    padding: 14px 16px 10px;
  }

  .login-card {
    padding-block: 10px;
  }

  .login-heading {
    margin-block: 12px;
  }

  .login-heading h2 {
    margin-block: 6px 4px;
  }

  .login-card :deep(.el-form-item) {
    margin-bottom: 15px;
  }

  .login-support {
    margin-top: 12px;
  }

  .auth-panel footer {
    margin-top: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-brand::after {
    animation: none;
  }
}
</style>
