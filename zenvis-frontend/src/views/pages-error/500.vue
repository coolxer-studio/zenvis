<template>
  <el-result status="error" title="500" sub-title="Sorry, the server is reporting an error.">
    <template #extra>
      <el-button type="primary" @click="toHome"> Back Home </el-button>
    </template>
  </el-result>
</template>

<script>
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'ExceptionPage500',
  setup() {
  },
  methods: {
    toHome() {
      this.$router.push({ path: '/' });
    },
  },
});
</script>
