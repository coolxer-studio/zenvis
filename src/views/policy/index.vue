<template>
  <div class="main-content rule-div">
    <el-splitter class="policy-splitter" :layout="isNarrow ? 'vertical' : 'horizontal'">
      <el-splitter-panel :size="isNarrow ? 34 : 24" :min="isNarrow ? 22 : 18">
        <LeftConfig
          :tree-data="configList"
          @on-click="getTextContent"
          @on-add="handleAddNode"
          @on-edit="handleEditNode"
          @on-delete="handleDeleteNode"
        />
      </el-splitter-panel>
      <el-splitter-panel :size="isNarrow ? 66 : 76" :min="isNarrow ? 42 : 45">
        <RightEdit
          :schema-type="configType"
          :model-value="fileContext"
          width="100%"
          height="100%"
          :file-name="currentFileName"
          :language="language"
          @on-apply="apply"
          @on-click="saveText"
          @editor-mounted="editorMounted"
        />
      </el-splitter-panel>
    </el-splitter>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import * as monaco from 'monaco-editor';
import { useRoute } from 'vue-router';

import { PolicyService } from '@/service/api';
import LeftConfig from './components/leftConfig.vue';
import RightEdit from './components/rightEdit.vue';

type TreeNode = {
  id: string;
  file_name: string;
  relative_path?: string;
  nodes?: TreeNode[];
  selectable?: boolean;
  [key: string]: any;
};

const route = useRoute();
const configType = ref<string>(route.params['menuParams']?.toString() || '');
const configList = ref<TreeNode[]>([]);
const fileContext = ref<string>('');
const currentFileName = ref<string>('');
const language = ref<string>('javascript');
const isNarrow = ref<boolean>(false);

const updateViewport = () => {
  isNarrow.value = window.innerWidth < 960;
};

const nodePath = (node: TreeNode) => node.relative_path || node.file_name;

const renamedNodePath = (node: TreeNode, newName: string) => {
  const originalPath = nodePath(node);
  const separatorIndex = originalPath.lastIndexOf('/');
  return separatorIndex === -1
    ? newName
    : `${originalPath.substring(0, separatorIndex + 1)}${newName}`;
};

const routeFileName = () => {
  const fileName = route.query.fileName || route.query.file_name;
  return Array.isArray(fileName) ? fileName[0] || '' : fileName?.toString() || '';
};

const editorMounted = (_editor: monaco.editor.IStandaloneCodeEditor) => undefined;

const getTextContent = (path: string) => {
  if (!path || !configType.value) return;
  fileContext.value = '';
  currentFileName.value = '';

  PolicyService.textContent(configType.value, { file_name: path }).then((res: string) => {
    fileContext.value = res;
    currentFileName.value = path;

    const dotIndex = currentFileName.value.lastIndexOf('.');
    language.value = dotIndex === -1 ? 'plaintext' : currentFileName.value.slice(dotIndex + 1);
  });
};

const apply = (json: string) => {
  PolicyService.applyJsonText(configType.value, {
    file_name: currentFileName.value,
    text: json,
  }).then(() => {
    ElMessage.success('应用成功');
  });
};

const saveText = (json: string) => {
  PolicyService.saveJsonText(configType.value, {
    file_name: currentFileName.value,
    text: json,
  }).then(() => {
    ElMessage.success('保存成功');
  });
};

const setSelectable = (data: TreeNode[]): TreeNode[] =>
  data.map(node => {
    if (node.nodes?.length) {
      node.selectable = false;
      node.nodes = setSelectable(node.nodes);
    }
    return node;
  });

const getListConfig = () => {
  if (!configType.value) return;

  PolicyService.getConfig(configType.value).then((res: TreeNode[]) => {
    configList.value = setSelectable(res);
  });
};

const handleAddNode = (nodeData: { parent: TreeNode; name: string }) => {
  PolicyService.addNode(configType.value, {
    parent_id: nodeData.parent.id,
    file_name: nodeData.name,
  })
    .then(() => {
      ElMessage.success('添加成功');
      getListConfig();
    })
    .catch(() => {
      ElMessage.error('添加失败');
    });
};

const handleEditNode = (nodeData: { node: TreeNode; newName: string }) => {
  PolicyService.renameNode(configType.value, {
    id: nodeData.node.id,
    original_file_name: nodePath(nodeData.node),
    file_name: renamedNodePath(nodeData.node, nodeData.newName),
  })
    .then(() => {
      ElMessage.success('更新成功');
      getListConfig();
    })
    .catch(() => {
      ElMessage.error('更新失败');
    });
};

const handleDeleteNode = (nodeData: TreeNode) => {
  PolicyService.deleteNode(configType.value, {
    id: nodeData.id,
    file_name: nodePath(nodeData),
  })
    .then(() => {
      ElMessage.success('删除成功');
      getListConfig();

      if (currentFileName.value === nodePath(nodeData)) {
        fileContext.value = '';
        currentFileName.value = '';
      }
    })
    .catch(() => {
      ElMessage.error('删除失败');
    });
};

getListConfig();
if (routeFileName()) {
  getTextContent(routeFileName());
}

watch(
  () => [route.params['menuParams'], route.query.fileName, route.query.file_name],
  ([newParams]) => {
    const newConfigType = newParams?.toString() || '';
    if (newConfigType && newConfigType !== configType.value) {
      configType.value = newConfigType;
      getListConfig();
      fileContext.value = '';
      currentFileName.value = '';
    }

    const nextFileName = routeFileName();
    if (nextFileName && nextFileName !== currentFileName.value) {
      getTextContent(nextFileName);
    }
  },
  { deep: true },
);

onMounted(() => {
  updateViewport();
  window.addEventListener('resize', updateViewport, { passive: true });
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewport);
});
</script>

<style lang="scss" scoped>
.rule-div {
  display: flex;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  padding: var(--zv-space-4);
  overflow: hidden;
  flex-direction: column;
  background: var(--zv-bg-page);
}

.policy-splitter {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--zv-bg-surface);
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-lg);
  box-shadow: var(--zv-shadow-1);
}

.policy-splitter :deep(.el-splitter__pane) {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.policy-splitter :deep(.el-splitter-bar__dragger) {
  background: var(--zv-border);
  transition: background-color var(--zv-motion-fast) var(--zv-ease-standard);
}

.policy-splitter :deep(.el-splitter-bar__dragger:hover) {
  background: var(--zv-primary);
}

@media (max-width: 959px) {
  .rule-div {
    padding: var(--zv-space-3);
  }
}
</style>
