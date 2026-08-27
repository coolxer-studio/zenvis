<template>
  <div class="tree-container">
    <header class="tree-header">
      <div class="tree-heading">
        <strong>配置文件</strong>
        <span>{{ fileCount }} 个文件</span>
      </div>
      <el-input
        v-model="filterText"
        :prefix-icon="Search"
        clearable
        placeholder="搜索文件名"
        aria-label="搜索配置文件"
      />
    </header>

    <div class="tree-scroll">
      <el-tree
        v-if="treeData.length"
        ref="treeRef"
        :data="treeData"
        :props="defaultProps"
        :filter-node-method="filterNode"
        :empty-text="filterText ? '未找到匹配文件' : '暂无配置文件'"
        node-key="id"
        default-expand-all
        highlight-current
        :expand-on-click-node="false"
        @node-click="handleNodeClick"
      >
        <template #default="{ node, data }">
          <div class="custom-tree-node">
            <span class="tree-node-title" :title="node.label">{{ node.label }}</span>
            <span class="tree-node-actions">
              <template v-if="node.level === 1">
                <el-tooltip content="添加配置" placement="top">
                  <el-button
                    class="tree-action-button"
                    link
                    circle
                    aria-label="添加配置"
                    @click.stop="addNode(data)"
                  >
                    <el-icon><Plus /></el-icon>
                  </el-button>
                </el-tooltip>
              </template>
              <template v-else>
                <el-tooltip content="重命名" placement="top">
                  <el-button
                    class="tree-action-button"
                    link
                    circle
                    aria-label="重命名配置"
                    @click.stop="editNode(data)"
                  >
                    <el-icon><Edit /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <el-button
                    class="tree-action-button is-danger"
                    link
                    circle
                    aria-label="删除配置"
                    @click.stop="deleteNode(data)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
              </template>
            </span>
          </div>
        </template>
      </el-tree>
      <el-empty v-else description="暂无配置文件" :image-size="72" />
    </div>

    <el-dialog v-model="addDialogVisible" title="添加节点" width="min(400px, calc(100vw - 32px))">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="80px">
        <el-form-item label="节点名称" prop="file_name">
          <el-input
            v-model="addForm.file_name"
            placeholder="请输入节点名称"
            @keyup.enter="confirmAdd"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="addDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="addLoading" @click="confirmAdd">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="编辑节点" width="min(400px, calc(100vw - 32px))">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
        <el-form-item label="节点名称" prop="file_name">
          <el-input
            v-model="editForm.file_name"
            placeholder="请输入节点名称"
            @keyup.enter="confirmEdit"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="editLoading" @click="confirmEdit">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue';
import { Delete, Edit, Plus, Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';

type TreeNode = {
  id: string;
  file_name: string;
  relative_path?: string;
  nodes?: TreeNode[];
  selectable?: boolean;
  [key: string]: any;
};

type TreeFilterExpose = {
  filter: (value: string) => void;
};

const props = withDefaults(
  defineProps<{
    treeData?: TreeNode[];
  }>(),
  {
    treeData: () => [],
  },
);

const emit = defineEmits<{
  (e: 'on-click', fileName: string): void;
  (e: 'on-add', nodeData: { parent: TreeNode; name: string }): void;
  (e: 'on-edit', nodeData: { node: TreeNode; newName: string }): void;
  (e: 'on-delete', nodeData: TreeNode): void;
}>();

const treeRef = ref<TreeFilterExpose>();
const filterText = ref<string>('');

const countFiles = (nodes: TreeNode[]): number =>
  nodes.reduce((total, node) => {
    if (node.nodes?.length) return total + countFiles(node.nodes);
    return total + 1;
  }, 0);

const fileCount = computed(() => countFiles(props.treeData));

const filterNode = (value: string, data: TreeNode) => {
  const keyword = value.trim().toLocaleLowerCase();
  return !keyword || data.file_name.toLocaleLowerCase().includes(keyword);
};

watch(filterText, value => treeRef.value?.filter(value));

const addDialogVisible = ref<boolean>(false);
const addFormRef = ref<FormInstance>();
const addLoading = ref<boolean>(false);
const currentParentNode = ref<TreeNode | null>(null);
const addForm = reactive({ file_name: '' });
const addRules = reactive<FormRules>({
  file_name: [{ required: true, message: '请输入节点名称', trigger: 'blur' }],
});

const editDialogVisible = ref<boolean>(false);
const editFormRef = ref<FormInstance>();
const editLoading = ref<boolean>(false);
const currentEditNode = ref<TreeNode | null>(null);
const editForm = reactive({ file_name: '' });
const editRules = reactive<FormRules>({
  file_name: [{ required: true, message: '请输入节点名称', trigger: 'blur' }],
});

const defaultProps = {
  children: 'nodes',
  label: 'file_name',
};

const handleNodeClick = (data: TreeNode) => {
  if (!data.nodes?.length) {
    emit('on-click', data.relative_path || data.file_name);
  }
};

const addNode = (nodeData: TreeNode) => {
  currentParentNode.value = nodeData;
  addForm.file_name = '';
  addDialogVisible.value = true;
  nextTick(() => addFormRef.value?.clearValidate());
};

const editNode = (nodeData: TreeNode) => {
  currentEditNode.value = nodeData;
  editForm.file_name = nodeData.file_name;
  editDialogVisible.value = true;
  nextTick(() => editFormRef.value?.clearValidate());
};

const deleteNode = (nodeData: TreeNode) => {
  ElMessageBox.confirm(`确定要删除“${nodeData.file_name}”吗？`, '确认删除', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => emit('on-delete', nodeData))
    .catch(() => {
      ElMessage.info('已取消删除');
    });
};

const confirmAdd = async () => {
  if (!addFormRef.value) return;

  await addFormRef.value.validate(valid => {
    if (!valid || !currentParentNode.value) return;
    addLoading.value = true;
    try {
      emit('on-add', {
        parent: currentParentNode.value,
        name: addForm.file_name,
      });
      addDialogVisible.value = false;
    } finally {
      addLoading.value = false;
    }
  });
};

const confirmEdit = async () => {
  if (!editFormRef.value) return;

  await editFormRef.value.validate(valid => {
    if (!valid || !currentEditNode.value) return;
    editLoading.value = true;
    try {
      emit('on-edit', {
        node: currentEditNode.value,
        newName: editForm.file_name,
      });
      editDialogVisible.value = false;
    } finally {
      editLoading.value = false;
    }
  });
};
</script>

<style lang="scss" scoped>
.tree-container {
  display: flex;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  flex-direction: column;
  background: var(--zv-bg-surface);
  border-right: 1px solid var(--zv-divider);
}

.tree-header {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: var(--zv-space-3);
  padding: var(--zv-space-4);
  background: var(--zv-bg-subtle);
  border-bottom: 1px solid var(--zv-divider);
}

.tree-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--zv-space-3);
}

.tree-heading strong {
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-md);
  font-weight: var(--zv-font-weight-semibold);
}

.tree-heading span {
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
}

.tree-scroll {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 10px 8px 18px;
  overflow: auto;
}

:deep(.el-tree) {
  min-width: 100%;
  color: var(--zv-text-secondary);
  background: transparent;
}

:deep(.el-tree-node) {
  white-space: nowrap;
}

:deep(.el-tree-node__content) {
  height: 36px;
  margin: 2px 0;
  border-radius: var(--zv-radius-md);
  transition: color var(--zv-motion-fast) var(--zv-ease-standard),
    background-color var(--zv-motion-fast) var(--zv-ease-standard);
}

:deep(.el-tree-node__content:hover) {
  color: var(--zv-text-primary);
  background: var(--zv-primary-soft);
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
}

.custom-tree-node {
  display: flex;
  flex: 1;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--zv-space-1);
  padding-right: var(--zv-space-1);
  font-size: var(--zv-font-size-sm);
}

.tree-node-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node-actions {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  opacity: 0;
  transition: opacity var(--zv-motion-fast) var(--zv-ease-standard);
}

:deep(.el-tree-node__content:hover) .tree-node-actions,
.custom-tree-node:focus-within .tree-node-actions {
  opacity: 1;
}

.tree-action-button {
  width: 26px;
  height: 26px;
  min-height: 26px;
  padding: 0;
  color: var(--zv-text-muted);
  border-radius: var(--zv-radius-sm);
}

.tree-action-button:hover,
.tree-action-button:focus-visible {
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
}

.tree-action-button.is-danger:hover,
.tree-action-button.is-danger:focus-visible {
  color: var(--zv-danger);
  background: var(--zv-danger-soft);
}

.dialog-footer {
  text-align: right;
}

@media (max-width: 959px) {
  .tree-container {
    border-right: 0;
    border-bottom: 1px solid var(--zv-divider);
  }

  .tree-header {
    display: grid;
    padding: 10px 12px;
    align-items: center;
    grid-template-columns: auto minmax(180px, 1fr);
  }
}

@media (max-width: 520px) {
  .tree-header {
    display: flex;
    align-items: stretch;
  }
}
</style>
