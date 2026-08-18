<template>
  <el-dialog
    :model-value="props.show"
    title="修改密码"
    width="520px"
    append-to-body
    destroy-on-close
    :before-close="handleBeforeClose"
    @closed="resetForm"
  >
    <el-form
      ref="formRef"
      :model="formPassword"
      name="login"
      autocomplete="off"
      label-width="100px"
    >
      <el-form-item label="原密码" prop="old_password" :rules="formRules.old_password">
        <el-input
          class="login-input"
          type="password"
          placeholder="请输入原密码"
          v-model="formPassword.old_password"
          show-password
        />
      </el-form-item>
      <el-form-item label="新密码" prop="password" :rules="formRules.password">
        <el-input
          class="login-input"
          type="password"
          placeholder="请输入密码"
          v-model="formPassword.password"
          show-password
        />
      </el-form-item>
      <el-form-item label="新密码确认" prop="password_copy" :rules="formRules.password_copy">
        <el-input
          class="login-input"
          type="password"
          placeholder="请输入密码"
          v-model="formPassword.password_copy"
          show-password
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" @click="handleOk">确定</el-button>
      </span>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
  import { reactive, ref } from 'vue';
  import type { FormInstance } from 'element-plus';
  import { rules } from '@u/tool';
  interface IUserFrom {
    old_password: string;
    password: string;
    password_copy: string;
  }
  const props = defineProps({
    show: {
      type: Boolean,
      default: false
    }
  });
  const emit = defineEmits({
    'on-ok': null,
    'on-cancel': null,
  });
  const formRef = ref<FormInstance>();
  let formPassword = reactive<IUserFrom>({
    old_password: '',
    password: '',
    password_copy: ''
  });
  const newPass = (rule: any, value: string, callback: any) => {
    if (value) {
      if (!rules.passwordRule(value)) {
        callback(new Error('请输入8~16位由大小写英文、数字、@!#$组成的密码！'));
      } else {
        callback();
      }
    } else if (value === '') {
      callback(new Error('请输入密码'));
    } else {
      callback();
    }
  };
  const reNewPass = (rule: any, value: string, callback: any) => {
    if (value) {
      if (value === formPassword.password) {
        callback();
      } else {
        callback(new Error('两次输入密码不一致!'));
      }
    } else if (value === '') {
      callback(new Error('请输入密码'));
    } else {
      callback();
    }
  };
  const formRules = reactive({
    old_password: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
    password: [{ required: true, validator: newPass, trigger: 'blur' }],
    password_copy: [{ required: true, validator: reNewPass, trigger: 'blur' }],
  });
  const resetForm = () => {
    if (formRef.value) {
      formRef.value.resetFields();
    }
  };
  const handleCancel = () => {
    emit('on-cancel');
  };
  const handleBeforeClose = (done: () => void) => {
    emit('on-cancel');
    done();
  };
  const handleOk = () => {
    formRef.value?.validate((valid: boolean) => {
      if (valid) {
        const { password_copy, ...data } = formPassword;
        emit('on-ok', data);
      }
    });
  };
</script>
