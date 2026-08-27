<template>
  <el-result status="error" title="404" sub-title="Sorry, the page you visited does not exist.">
    <template #extra>
      <el-button type="primary" @click="toHome"> Back Home </el-button>
    </template>
  </el-result>
</template>

<script>
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'ExceptionPage404',
  setup() {
  },
  methods: {
    toHome() {
      this.$router.push({ path: '/' });
    },
  },
});
</script>
