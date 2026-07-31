<template>
  <div class="tree-container">
    <el-tree
      v-if="treeData.length"
      :data="treeData"
      :props="defaultProps"
      default-expand-all
      :expand-on-click-node="false"
      @node-click="handleNodeClick"
    >
      <template #default="{ node, data }">
        <div class="custom-tree-node">
          <span class="tree-node-title">{{ node.label }}</span>
          <span class="tree-node-actions">
            <!-- 一级节点只显示添加按钮 -->
            <template v-if="node.level === 1">
              <el-icon @click.stop="addNode(data)"><Plus /></el-icon>
            </template>
            <!-- 所有非根节点都支持编辑和删除 -->
            <template v-else>
              <el-icon @click.stop="editNode(data)"><Edit /></el-icon>
              <el-icon @click.stop="deleteNode(data)"><Delete /></el-icon>
            </template>
          </span>
        </div>
      </template>
    </el-tree>
    
    <!-- 添加节点对话框 -->
    <el-dialog
      v-model="addDialogVisible"
      title="添加节点"
      width="400px"
    >
      <el-form
        ref="addFormRef"
        :model="addForm"
        :rules="addRules"
        label-width="80px"
      >
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
          <el-button 
            type="primary" 
            @click="confirmAdd"
            :loading="addLoading"
          >
            确定
          </el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 编辑节点对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑节点"
      width="400px"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="80px"
      >
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
          <el-button 
            type="primary" 
            @click="confirmEdit"
            :loading="editLoading"
          >
            确定
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick } from 'vue';
import { ElMessageBox, ElMessage, ElDialog, ElForm, ElFormItem, ElInput, ElButton } from 'element-plus';
import { Plus, Edit, Delete } from '@element-plus/icons-vue';
import { PropType } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';

// 定义树节点类型
interface TreeNode {
  id: string;
  file_name: string;
  relative_path?: string;
  nodes?: TreeNode[];
  selectable?: boolean;
  [key: string]: any;
}

// 定义组件属性
const props = defineProps({
  treeData: {
    type: Array as PropType<TreeNode[]>,
    default: () => []
  }
});

// 定义事件发射
const emit = defineEmits<{
  (e: 'on-click', fileName: string): void;
  (e: 'on-add', nodeData: { parent: TreeNode, name: string }): void;
  (e: 'on-edit', nodeData: { node: TreeNode, newName: string }): void;
  (e: 'on-delete', nodeData: TreeNode): void;
}>();

// 添加节点对话框相关
const addDialogVisible = ref(false);
const addFormRef = ref<FormInstance>();
const addLoading = ref(false);
const currentParentNode = ref<TreeNode | null>(null);

const addForm = reactive({
  file_name: ''
});

const addRules = reactive<FormRules>({
  file_name: [
    { required: true, message: '请输入节点名称', trigger: 'blur' }
  ]
});

// 编辑节点对话框相关
const editDialogVisible = ref(false);
const editFormRef = ref<FormInstance>();
const editLoading = ref(false);
const currentEditNode = ref<TreeNode | null>(null);

const editForm = reactive({
  file_name: ''
});

const editRules = reactive<FormRules>({
  file_name: [
    { required: true, message: '请输入节点名称', trigger: 'blur' }
  ]
});

// 树组件属性配置
const defaultProps = {
  children: 'nodes',
  label: 'file_name'
};

// 节点点击事件处理
const handleNodeClick = (data: TreeNode) => {
  if (data && !(data.nodes && data.nodes.length)) {
    emit('on-click', data.relative_path || data.file_name);
  }
};

// 添加节点事件处理
const addNode = (nodeData: TreeNode) => {
  currentParentNode.value = nodeData;
  addForm.file_name = '';
  addDialogVisible.value = true;
  
  // 等待DOM更新后聚焦输入框
  nextTick(() => {
    addFormRef.value?.clearValidate();
  });
};

// 编辑节点事件处理
const editNode = (nodeData: TreeNode) => {
  currentEditNode.value = nodeData;
  editForm.file_name = nodeData.file_name;
  editDialogVisible.value = true;
  
  // 等待DOM更新后聚焦输入框
  nextTick(() => {
    editFormRef.value?.clearValidate();
  });
};

// 删除节点事件处理
const deleteNode = (nodeData: TreeNode) => {
  ElMessageBox.confirm(
    `确定要删除"${nodeData.file_name}"吗？`,
    '确认删除',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    emit('on-delete', nodeData);
  }).catch(() => {
    // 用户取消删除
    ElMessage({
      type: 'info',
      message: '已取消删除'
    });
  });
};

// 确认添加节点
const confirmAdd = async () => {
  if (!addFormRef.value) return;
  
  await addFormRef.value.validate((valid) => {
    if (valid) {
      addLoading.value = true;
      
      try {
        emit('on-add', {
          parent: currentParentNode.value!,
          name: addForm.file_name
        });
        
        // 关闭对话框
        addDialogVisible.value = false;
        ElMessage.success('添加成功');
      } catch (error) {
        ElMessage.error('添加失败');
      } finally {
        addLoading.value = false;
      }
    }
  });
};

// 确认编辑节点
const confirmEdit = async () => {
  if (!editFormRef.value) return;
  
  await editFormRef.value.validate((valid) => {
    if (valid) {
      editLoading.value = true;
      
      try {
        emit('on-edit', {
          node: currentEditNode.value!,
          newName: editForm.file_name
        });
        
        // 关闭对话框
        editDialogVisible.value = false;
        ElMessage.success('编辑成功');
      } catch (error) {
        ElMessage.error('编辑失败');
      } finally {
        editLoading.value = false;
      }
    }
  });
};
</script>

<style scoped>
.tree-container {
  overflow-x: auto;
  white-space: nowrap;
}

:deep(.el-tree) {
  min-width: max-content;
  background-color: transparent;
}

:deep(.el-tree-node) {
  white-space: nowrap;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
  width: 100%;
}

.tree-node-title {
  margin-right: 8px;
}

.tree-node-actions {
  opacity: 0;
  transition: opacity 0.3s;
}

:deep(.el-tree-node:hover) .tree-node-actions {
  opacity: 1;
}

.tree-node-actions .el-icon {
  margin-right: 5px;
  cursor: pointer;
  color: #606266;
}

.tree-node-actions .el-icon:hover {
  color: #409eff;
}

.dialog-footer {
  text-align: right;
}
</style>
