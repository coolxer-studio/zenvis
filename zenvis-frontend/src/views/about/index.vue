<template>
  <div class="main-content about-page">
    <header class="page-heading">
      <div>
        <h1>关于产品</h1>
        <p>维护产品信息、品牌资源与服务联系方式</p>
      </div>
    </header>
    <div class="page-body">
      <div id="about_it_id" class="product-info">
      <section class="about-overview">
        <div class="overview-text">
          <span class="overview-kicker">系统信息</span>
          <h2>{{ formatValue(systemInfo?.productName) }}</h2>
          <p class="system-title">{{ formatValue(systemInfo?.systemTitle) }}</p>
          <p class="system-subtitle">{{ formatValue(systemInfo?.systemSubtitle) }}</p>
        </div>
        <div class="overview-brand">
          <div class="overview-logo">
            <img
              v-if="systemInfo?.systemLogo"
              :src="getFullUrl(systemInfo.systemLogo)"
              alt="系统Logo"
            />
            <el-icon v-else><Picture /></el-icon>
          </div>
          <div class="overview-meta">
            <span>当前版本</span>
            <strong>{{ formatValue(systemInfo?.productVersion) }}</strong>
          </div>
        </div>
      </section>

      <section class="settings-section">
        <div class="section-heading">
          <h3>品牌资源</h3>
        </div>
        <div class="asset-list">
          <div class="asset-item">
            <div class="asset-preview square-preview">
              <img
                v-if="systemInfo?.systemIcon"
                :src="getFullUrl(systemInfo.systemIcon)"
                alt="产品图标"
              />
              <div v-else class="asset-placeholder">
                <el-icon><Picture /></el-icon>
                <span>暂无图标</span>
              </div>
            </div>
            <div class="asset-info">
              <span class="asset-title">页面 ico</span>
              <span class="asset-format">.ico</span>
            </div>
            <el-button @click="triggerLogoUpload" :icon="Upload" size="small" type="primary"
              >上传图标</el-button
            >
            <input
              ref="logoInput"
              type="file"
              accept=".ico"
              class="logo-upload-input"
              @change="handleIconUpload"
            />
          </div>

          <div class="asset-item">
            <div class="asset-preview square-preview">
              <img
                v-if="systemInfo?.systemLogo"
                :src="getFullUrl(systemInfo.systemLogo)"
                alt="系统Logo"
              />
              <div v-else class="asset-placeholder">
                <el-icon><Picture /></el-icon>
                <span>暂无Logo</span>
              </div>
            </div>
            <div class="asset-info">
              <span class="asset-title">系统 Logo</span>
              <span class="asset-format">.png</span>
            </div>
            <el-button @click="triggerSystemLogoUpload" :icon="Upload" size="small" type="primary"
              >上传Logo</el-button
            >
            <input
              ref="systemLogoInput"
              type="file"
              accept=".png"
              class="logo-upload-input"
              @change="handleLogoUpload"
            />
          </div>

          <div class="asset-item banner-item">
            <div class="asset-preview banner-preview">
              <img
                v-if="systemInfo?.systemBanner"
                :src="getFullUrl(systemInfo.systemBanner)"
                alt="系统Banner"
              />
              <div v-else class="asset-placeholder">
                <el-icon><Picture /></el-icon>
                <span>暂无Banner</span>
              </div>
            </div>
            <div class="asset-info">
              <span class="asset-title">系统 Banner</span>
              <span class="asset-format">.png</span>
            </div>
            <el-button @click="triggerBannerUpload" :icon="Upload" size="small" type="primary"
              >上传Banner</el-button
            >
            <input
              ref="bannerInput"
              type="file"
              accept=".png"
              class="logo-upload-input"
              @change="handleBannerUpload"
            />
          </div>
        </div>
      </section>

      <section class="settings-section">
        <div class="section-heading">
          <h3>基础信息</h3>
        </div>
        <div class="field-list">
          <div
            v-for="field in baseFields"
            :key="field.key"
            class="field-row"
            :class="{ 'is-wide': field.multiline || field.wide }"
          >
            <div class="field-label">{{ field.label }}</div>
            <div class="field-control">
              <template v-if="editingField !== field.key">
                <pre
                  v-if="field.multiline"
                  class="pre-style"
                  v-text="formatValue(systemInfo?.[field.key])"
                ></pre>
                <span v-else class="field-text">{{ formatValue(systemInfo?.[field.key]) }}</span>
              </template>
              <el-input
                v-else-if="field.multiline"
                v-model="editValue"
                type="textarea"
                :autosize="{ minRows: 3, maxRows: 8 }"
                class="edit-textarea"
                @blur="saveField(field.key)"
              />
              <el-input
                v-else
                v-model="editValue"
                size="small"
                class="edit-input"
                @blur="saveField(field.key)"
                @keyup.enter="saveField(field.key)"
              />
            </div>
            <div class="field-action">
              <el-tooltip v-if="editingField !== field.key" content="编辑" placement="top">
                <el-button
                  @click="startFieldEdit(field.key, systemInfo?.[field.key])"
                  aria-label="编辑"
                  circle
                  :icon="Edit"
                  size="small"
                  type="primary"
                />
              </el-tooltip>
            </div>
          </div>
        </div>
      </section>

      <section class="settings-section">
        <div class="section-heading">
          <h3>联系与链接</h3>
        </div>
        <div class="field-list compact-list">
          <div
            v-for="field in contactFields"
            :key="field.key"
            class="field-row"
            :class="{ 'is-wide': field.wide }"
          >
            <div class="field-label">{{ field.label }}</div>
            <div class="field-control">
              <template v-if="editingField !== field.key">
                <span class="field-text">{{ formatValue(systemInfo?.[field.key]) }}</span>
              </template>
              <el-input
                v-else
                v-model="editValue"
                size="small"
                class="edit-input"
                @blur="saveField(field.key)"
                @keyup.enter="saveField(field.key)"
              />
            </div>
            <div class="field-action">
              <el-tooltip v-if="editingField !== field.key" content="编辑" placement="top">
                <el-button
                  @click="startFieldEdit(field.key, systemInfo?.[field.key])"
                  aria-label="编辑"
                  circle
                  :icon="Edit"
                  size="small"
                  type="primary"
                />
              </el-tooltip>
            </div>
          </div>
        </div>
      </section>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, nextTick } from 'vue';
import { Edit, Picture, Upload } from '@element-plus/icons-vue';
import { SystemService } from '@/service/api';
import { SystemInfo } from '@/types/type-system';
import { ElMessage } from 'element-plus';
import { getAssetUrl } from '@u/url';

type EditableFieldKey =
  | 'systemTitle'
  | 'systemSubtitle'
  | 'productName'
  | 'productVersion'
  | 'productIntroduction'
  | 'servicePhone'
  | 'serviceEmail'
  | 'technicalEmail'
  | 'integrateLink'
  | 'copyright';

type EditableField = {
  key: EditableFieldKey;
  label: string;
  multiline?: boolean;
  wide?: boolean;
};

const systemInfo = ref<SystemInfo>();
const editingField = ref<EditableFieldKey | null>(null);
const editValue = ref('');
const logoInput = ref<HTMLInputElement | null>(null);
const systemLogoInput = ref<HTMLInputElement | null>(null);
const bannerInput = ref<HTMLInputElement | null>(null);

const baseFields: EditableField[] = [
  { key: 'systemTitle', label: '系统标题' },
  { key: 'systemSubtitle', label: '系统英文副标题' },
  { key: 'productName', label: '产品名称' },
  { key: 'productVersion', label: '产品版本' },
  { key: 'productIntroduction', label: '产品简介', multiline: true },
];

const contactFields: EditableField[] = [
  { key: 'servicePhone', label: '客服电话' },
  { key: 'serviceEmail', label: '客服邮箱' },
  { key: 'technicalEmail', label: '技术支持' },
  { key: 'integrateLink', label: '接入指南链接' },
  { key: 'copyright', label: '版权信息', wide: true },
];

const formatValue = (value?: string) => value || '-';

const getSystemInfoFun = async () => {
  SystemService.getSystemInfo().then((res: any) => {
    systemInfo.value = res;
  });
};

const startFieldEdit = async (field: EditableFieldKey, value: string | undefined) => {
  editingField.value = field;
  editValue.value = value || '';
  await nextTick();
};

const saveField = async (field: EditableFieldKey) => {
  if (!systemInfo.value || editingField.value !== field) return;

  const updateData: Partial<SystemInfo> = { ...systemInfo.value };
  (updateData as any)[field] = editValue.value;

  try {
    await SystemService.updateSystemInfo(updateData as SystemInfo);
    ElMessage.success('保存成功');
    await getSystemInfoFun();
  } catch (error) {
    console.error('保存失败:', error);
    ElMessage.error('保存失败');
  } finally {
    editingField.value = null;
    editValue.value = '';
  }
};

const triggerLogoUpload = () => {
  logoInput.value?.click();
};

const handleIconUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  if (!file.name.endsWith('.ico')) {
    ElMessage.error('请选择.ico格式文件');
    return;
  }

  try {
    const result = await SystemService.uploadIcon(file);
    ElMessage.success('上传成功');
    await getSystemInfoFun();
  } catch (error) {
    console.error('上传失败:', error);
    ElMessage.error('上传失败');
  }

  target.value = '';
};

const triggerSystemLogoUpload = () => {
  systemLogoInput.value?.click();
};

const handleLogoUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  if (!file.name.endsWith('.png')) {
    ElMessage.error('请选择.png格式文件');
    return;
  }

  try {
    const result = await SystemService.uploadLogo(file);
    ElMessage.success('上传成功');
    await getSystemInfoFun();
  } catch (error) {
    console.error('上传失败:', error);
    ElMessage.error('上传失败');
  }

  target.value = '';
};

const triggerBannerUpload = () => {
  bannerInput.value?.click();
};

const handleBannerUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  if (!file.name.endsWith('.png')) {
    ElMessage.error('请选择.png格式文件');
    return;
  }

  try {
    const result = await SystemService.uploadBanner(file);
    ElMessage.success('上传成功');
    await getSystemInfoFun();
  } catch (error) {
    console.error('上传失败:', error);
    ElMessage.error('上传失败');
  }

  target.value = '';
};

const getFullUrl = (url: string): string => {
  return getAssetUrl(url);
};

getSystemInfoFun();
</script>
<style lang="scss" scoped>
.about-page {
  display: flex;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
  flex-direction: column;
  background: var(--zv-bg-page);
  border: 0;
  border-radius: 0;
  box-sizing: border-box;

  .page-heading {
    display: flex;
    flex: 0 0 64px;
    align-items: center;
    padding: 0 20px;
    background: var(--zv-bg-surface);
    border-bottom: 1px solid var(--zv-divider);

    h1 {
      margin: 0;
      color: var(--zv-text-primary);
      font-size: var(--zv-font-size-xl);
      font-weight: var(--zv-font-weight-semibold);
      line-height: 24px;
    }

    p {
      margin: 3px 0 0;
      color: var(--zv-text-muted);
      font-size: var(--zv-font-size-xs);
      line-height: 17px;
    }
  }

  .page-body {
    flex: 1;
    min-height: 0;
    padding: 14px 16px 20px;
    overflow-x: hidden;
    overflow-y: auto;
  }

  .product-info {
    width: 100%;
    margin: 0;
  }

  .about-overview {
    display: flex;
    min-height: 118px;
    margin: 0;
    padding: 20px 22px;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    background: var(--zv-bg-surface);
    border: 1px solid var(--zv-border);
    border-radius: var(--zv-radius-sm);
    box-shadow: var(--zv-shadow-1);
  }

  .overview-text {
    min-width: 0;
    max-width: 760px;
    padding: 0;
  }

  .overview-kicker {
    display: inline-flex;
    margin-bottom: 7px;
    color: var(--zv-primary);
    font-size: var(--zv-font-size-xs);
    font-weight: var(--zv-font-weight-semibold);
  }

  .overview-text h2 {
    margin: 0;
    overflow: hidden;
    color: var(--zv-text-primary);
    font-size: var(--zv-font-size-2xl);
    font-weight: var(--zv-font-weight-semibold);
    line-height: 30px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .system-title {
    margin: 5px 0 0;
    overflow: hidden;
    color: var(--zv-text-secondary);
    font-size: var(--zv-font-size-md);
    line-height: 20px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .system-subtitle {
    margin: 3px 0 0;
    overflow: hidden;
    color: var(--zv-text-muted);
    font-size: 11px;
    line-height: 16px;
    letter-spacing: 0.06em;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .overview-brand {
    display: flex;
    flex: 0 0 auto;
    min-width: 220px;
    padding: 10px 14px;
    align-items: center;
    gap: 12px;
    background: var(--zv-bg-subtle);
    border: 1px solid var(--zv-divider);
    border-radius: var(--zv-radius-sm);
  }

  .overview-logo {
    display: grid;
    width: 52px;
    height: 52px;
    overflow: hidden;
    color: var(--zv-text-muted);
    font-size: 23px;
    place-items: center;
    background: var(--zv-bg-surface);
    border: 1px solid var(--zv-border);
    border-radius: var(--zv-radius-md);

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
  }

  .overview-meta {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 4px;

    span {
      color: var(--zv-text-muted);
      font-size: 12px;
    }

    strong {
      max-width: 150px;
      overflow: hidden;
      color: var(--zv-text-primary);
      font-size: var(--zv-font-size-lg);
      font-weight: var(--zv-font-weight-semibold);
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .settings-section {
    margin-top: 12px;
    padding: 0;
    overflow: hidden;
    background: var(--zv-bg-surface);
    border: 1px solid var(--zv-border);
    border-radius: var(--zv-radius-sm);
    box-shadow: var(--zv-shadow-1);
  }

  .section-heading {
    display: flex;
    height: 44px;
    margin: 0;
    padding: 0 16px;
    align-items: center;
    background: var(--zv-bg-subtle);
    border-bottom: 1px solid var(--zv-divider);

    h3 {
      position: relative;
      margin: 0;
      padding-left: 10px;
      color: var(--zv-text-secondary);
      font-size: var(--zv-font-size-md);
      font-weight: var(--zv-font-weight-semibold);
      line-height: 20px;

      &::before {
        position: absolute;
        top: 4px;
        bottom: 4px;
        left: 0;
        width: 3px;
        background: var(--zv-primary);
        border-radius: 2px;
        content: '';
      }
    }
  }

  .asset-list {
    display: grid;
    padding: 12px;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
  }

  .asset-item {
    display: grid;
    min-width: 0;
    min-height: 92px;
    padding: 12px;
    align-items: center;
    gap: 11px;
    grid-template-columns: auto minmax(0, 1fr) auto;
    background: var(--zv-bg-surface);
    border: 1px solid var(--zv-divider);
    border-radius: var(--zv-radius-sm);
    transition: border-color var(--zv-motion-base) var(--zv-ease-standard), box-shadow var(--zv-motion-base) var(--zv-ease-standard);

    &:hover {
      border-color: var(--zv-primary-border);
      box-shadow: var(--zv-shadow-2);
    }
  }

  .asset-preview {
    display: grid;
    overflow: hidden;
    place-items: center;
    background: var(--zv-bg-subtle);
    border: 1px solid var(--zv-border);
    border-radius: var(--zv-radius-sm);

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
  }

  .square-preview {
    width: 62px;
    height: 62px;
  }

  .banner-preview {
    width: 96px;
    height: 62px;
  }

  .asset-placeholder {
    display: flex;
    width: 100%;
    height: 100%;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3px;
    color: var(--zv-text-placeholder);
    font-size: 11px;

    .el-icon {
      font-size: 20px;
    }
  }

  .asset-info {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 5px;
  }

  .asset-title {
    overflow: hidden;
    color: var(--zv-text-primary);
    font-size: var(--zv-font-size-md);
    font-weight: var(--zv-font-weight-semibold);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .asset-format {
    color: var(--zv-text-muted);
    font-size: 11px;
  }

  .logo-upload-input {
    display: none;
  }

  .field-list,
  .compact-list {
    display: grid;
    padding: 0;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0;
  }

  .field-row {
    display: grid;
    min-width: 0;
    min-height: 52px;
    padding: 9px 14px;
    align-items: center;
    gap: 12px;
    grid-template-columns: 112px minmax(0, 1fr) 30px;
    background: var(--zv-bg-surface);
    border-right: 1px solid var(--zv-divider);
    border-bottom: 1px solid var(--zv-divider);
    transition: background-color var(--zv-motion-fast) var(--zv-ease-standard);

    &:nth-child(even) {
      border-right: 0;
    }

    &:hover {
      background: var(--zv-bg-subtle);
    }

    &.is-wide {
      grid-column: 1 / -1;
      border-right: 0;
    }
  }

  .field-label {
    color: var(--zv-text-muted);
    font-size: var(--zv-font-size-sm);
    font-weight: var(--zv-font-weight-medium);
  }

  .field-control {
    min-width: 0;
    color: var(--zv-text-secondary);
    font-size: var(--zv-font-size-sm);
    line-height: 22px;
  }

  .field-text,
  .pre-style {
    display: block;
    min-height: 22px;
    margin: 0;
    overflow-wrap: anywhere;
    color: var(--zv-text-secondary);
    font: inherit;
    line-height: 22px;
    white-space: pre-wrap;
  }

  .field-action {
    display: flex;
    justify-content: flex-end;
  }

  :deep(.field-action .el-button) {
    width: 28px;
    height: 28px;
    color: var(--zv-primary);
    background: var(--zv-primary-soft);
    border: 0;

    &:hover {
      color: var(--zv-text-inverse);
      background: var(--zv-primary);
    }
  }

  :deep(.el-button--primary:not(.is-circle)) {
    --el-button-bg-color: var(--zv-primary);
    --el-button-border-color: var(--zv-primary);
    --el-button-hover-bg-color: var(--zv-primary-hover);
    --el-button-hover-border-color: var(--zv-primary-hover);
    border-radius: var(--zv-radius-xs);
  }

  :deep(.el-input__wrapper),
  :deep(.el-textarea__inner) {
    border-radius: var(--zv-radius-xs);
    box-shadow: 0 0 0 1px var(--zv-border) inset;
  }

  :deep(.el-input__wrapper.is-focus),
  :deep(.el-textarea__inner:focus) {
    box-shadow: 0 0 0 1px var(--zv-primary) inset, var(--zv-focus-ring);
  }
}

@media (max-width: 1180px) {
  .about-page {
    .asset-list {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
}

@media (max-width: 820px) {
  .about-page {
    .page-heading {
      padding-inline: 16px;
    }

    .page-body {
      padding: 12px;
    }

    .about-overview {
      align-items: flex-start;
      flex-direction: column;
    }

    .overview-brand {
      width: 100%;
      min-width: 0;
      box-sizing: border-box;
    }

    .asset-list,
    .field-list,
    .compact-list {
      grid-template-columns: 1fr;
    }

    .field-row,
    .field-row:nth-child(even) {
      border-right: 0;
    }
  }
}

@media (max-width: 560px) {
  .about-page {
    .asset-list {
      padding: 10px;
    }

    .asset-item {
      grid-template-columns: auto minmax(0, 1fr);

      > .el-button {
        grid-column: 1 / -1;
      }
    }

    .field-row {
      padding: 11px 12px;
      align-items: start;
      grid-template-columns: minmax(0, 1fr) 30px;
    }

    .field-label {
      grid-column: 1;
    }

    .field-control {
      grid-column: 1 / -1;
      grid-row: 2;
    }

    .field-action {
      grid-column: 2;
      grid-row: 1;
    }
  }
}
</style>
