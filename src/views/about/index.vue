<template>
  <div class="main-content about_it">
    <div id="about_it_id" class="product_info">
      <section class="about-overview">
        <div class="overview-text">
          <span class="overview-kicker">系统信息</span>
          <h2>{{ formatValue(systemInfo?.productName) }}</h2>
          <p>{{ formatValue(systemInfo?.systemTitle) }}</p>
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
            :class="{ 'is-textarea': field.multiline }"
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
          <div v-for="field in contactFields" :key="field.key" class="field-row">
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
};

const systemInfo = ref<SystemInfo>();
const editingField = ref<EditableFieldKey | null>(null);
const editValue = ref('');
const logoInput = ref<HTMLInputElement | null>(null);
const systemLogoInput = ref<HTMLInputElement | null>(null);
const bannerInput = ref<HTMLInputElement | null>(null);

const baseFields: EditableField[] = [
  { key: 'systemTitle', label: '系统标题' , multiline: true },
  { key: 'productName', label: '产品名称' },
  { key: 'productVersion', label: '产品版本' },
  { key: 'productIntroduction', label: '产品简介', multiline: true },
];

const contactFields: EditableField[] = [
  { key: 'servicePhone', label: '客服电话' },
  { key: 'serviceEmail', label: '客服邮箱' },
  { key: 'technicalEmail', label: '技术支持' },
  { key: 'integrateLink', label: '官网链接' },
  { key: 'copyright', label: '版权信息' },
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
.about_it {
  background: #f5f7fb;
  border-top: 2px solid #3988ff;
  padding: 22px;

  .product_info {
    width: min(1180px, 100%);
    margin: 0 auto;
  }

  .about-overview {
    position: relative;
    display: flex;
    min-height: 158px;
    margin-bottom: 18px;
    padding: 26px 30px;
    overflow: hidden;
    align-items: center;
    justify-content: space-between;
    border: 1px solid #dce8ff;
    border-radius: 8px;
    background: linear-gradient(120deg, rgba(57, 136, 255, 0.12), rgba(72, 184, 132, 0.1)),
      #fff no-repeat right 24px center;
    background-size: auto 126px;
    box-shadow: 0 16px 36px rgba(37, 65, 110, 0.08);
  }

  .overview-text {
    max-width: 560px;
    padding-right: 24px;
  }

  .overview-kicker {
    display: inline-flex;
    margin-bottom: 10px;
    color: #3988ff;
    font-size: 13px;
    font-weight: 600;
  }

  h2 {
    margin: 0;
    color: #1f2d3d;
    font-size: 26px;
    font-weight: 700;
    line-height: 1.25;
  }

  p {
    margin: 8px 0 0;
    color: #5f6f86;
    font-size: 15px;
    line-height: 1.6;
  }

  .overview-brand {
    display: flex;
    min-width: 230px;
    padding: 14px 16px;
    align-items: center;
    gap: 14px;
    border: 1px solid rgba(57, 136, 255, 0.18);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.82);
    backdrop-filter: blur(4px);
  }

  .overview-logo {
    display: flex;
    width: 58px;
    height: 58px;
    flex: 0 0 58px;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    border: 1px solid #d8e5f8;
    border-radius: 8px;
    background: #f7fbff;
    color: #87a8d8;
    font-size: 26px;

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
      color: #7b8da6;
      font-size: 12px;
    }

    strong {
      color: #26384f;
      font-size: 18px;
      line-height: 1.2;
      word-break: break-word;
    }
  }

  .settings-section {
    margin-top: 14px;
    padding: 20px 22px;
    border: 1px solid #e4ebf5;
    border-radius: 8px;
    background: #fff;
  }

  .section-heading {
    display: flex;
    margin-bottom: 16px;
    align-items: center;
    justify-content: space-between;

    h3 {
      position: relative;
      margin: 0;
      padding-left: 12px;
      color: #25364d;
      font-size: 17px;
      font-weight: 700;
      line-height: 24px;

      &::before {
        position: absolute;
        top: 4px;
        bottom: 4px;
        left: 0;
        width: 4px;
        border-radius: 4px;
        background: #3988ff;
        content: '';
      }
    }
  }

  .asset-list {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
  }

  .asset-item {
    display: grid;
    min-height: 108px;
    padding: 14px;
    align-items: center;
    gap: 12px;
    grid-template-columns: auto minmax(0, 1fr) auto;
    border: 1px solid #e6edf7;
    border-radius: 8px;
    background: #fbfdff;
  }

  .asset-preview {
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    border: 1px solid #dbe5f2;
    border-radius: 8px;
    background: linear-gradient(135deg, rgba(57, 136, 255, 0.06), rgba(72, 184, 132, 0.06)), #fff;

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
  }

  .square-preview {
    width: 72px;
    height: 72px;
  }

  .banner-preview {
    width: 72px;
    height: 72px;
  }

  .asset-placeholder {
    display: flex;
    width: 100%;
    height: 100%;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    color: #91a3ba;
    font-size: 12px;

    .el-icon {
      font-size: 22px;
    }
  }

  .asset-info {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 6px;
  }

  .asset-title {
    color: #27384d;
    font-size: 15px;
    font-weight: 600;
  }

  .asset-format {
    color: #8798ad;
    font-size: 12px;
  }

  .logo-upload-input {
    display: none;
  }

  .field-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .compact-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .field-row {
    display: grid;
    min-height: 54px;
    padding: 12px 14px;
    align-items: center;
    gap: 12px;
    grid-template-columns: 96px minmax(0, 1fr) 34px;
    border: 1px solid #e8eef6;
    border-radius: 8px;
    background: #fbfcfe;
    transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;

    &:hover {
      border-color: #c9dcfb;
      background: #fff;
      box-shadow: 0 8px 20px rgba(48, 82, 130, 0.08);
    }

    &.is-textarea {
      grid-column: 1 / -1;
      align-items: start;

      .field-label {
        padding-top: 5px;
      }
    }
  }

  .field-label {
    color: #62748d;
    font-size: 14px;
    font-weight: 600;
  }

  .field-control {
    min-width: 0;
    color: #26384f;
    font-size: 14px;
    line-height: 24px;
  }

  .field-text {
    display: block;
    min-height: 24px;
    overflow-wrap: anywhere;
  }

  .field-action {
    display: flex;
    justify-content: flex-end;
  }

  .pre-style {
    margin: 0;
    color: #26384f;
    font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei',
      Arial, sans-serif;
    line-height: 1.7;
    overflow-wrap: anywhere;
    white-space: pre-wrap;
  }

  .edit-input,
  .edit-textarea {
    width: 100%;
  }

  :deep(.el-button.is-circle) {
    width: 30px;
    height: 30px;
  }

  :deep(.el-input__wrapper),
  :deep(.el-textarea__inner) {
    border-radius: 6px;
    box-shadow: 0 0 0 1px #d8e3f0 inset;
  }

  :deep(.el-input__wrapper.is-focus),
  :deep(.el-textarea__inner:focus) {
    box-shadow: 0 0 0 1px #3988ff inset;
  }
}

@media (max-width: 1180px) {
  .about_it {
    .asset-list,
    .field-list,
    .compact-list {
      grid-template-columns: 1fr;
    }
  }
}

@media (max-width: 760px) {
  .about_it {
    padding: 14px;

    .about-overview {
      padding: 20px;
      align-items: flex-start;
      flex-direction: column;
      background-position: right 14px top 16px;
      background-size: auto 92px;
    }

    .overview-text {
      padding-right: 0;
    }

    .overview-brand {
      width: 100%;
      min-width: 0;
    }

    .asset-item,
    .field-row {
      grid-template-columns: 1fr;
      align-items: start;
    }

    .field-action {
      justify-content: flex-start;
    }
  }
}
</style>
