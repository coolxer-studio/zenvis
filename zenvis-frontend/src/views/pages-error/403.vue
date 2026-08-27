<template>
  <el-result status="warning" title="403" sub-title="Sorry, you don't have access to this page.">
    <template #extra>
      <el-button type="primary" @click="toHome"> Back Home </el-button>
    </template>
  </el-result>
</template>

<script>
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'ExceptionPage403',
  setup() {
  },
  methods: {
    toHome() {
      this.$router.push({ path: '/' });
    },
  },
});
</script>
