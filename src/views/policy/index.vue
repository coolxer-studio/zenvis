<template>
  <div class="main-content rule-div">
    <el-splitter class="policy-splitter" style="height: 100%">
      <el-splitter-panel :size="20" min="10">
        <LeftConfig 
          :treeData="configList" 
          @on-click="getTextContent" 
          @on-add="handleAddNode" 
          @on-edit="handleEditNode" 
          @on-delete="handleDeleteNode"
        />
      </el-splitter-panel>
      <el-splitter-panel :size="80" min="20">
        <RightEdit 
          :schemaType="configType"
          :modelValue="fileContext" 
          width="100%" 
          height="90%" 
          :fileName="currentFileName" 
          @on-apply="apply"
          @on-click="saveText" 
          :language="language" 
          @editor-mounted="editorMounted" 
        />
      </el-splitter-panel>
    </el-splitter>
  </div>
</template>

<script setup lang="ts">
  import { watch, ref } from 'vue';
  import { useRoute } from 'vue-router';
  import { ElMessage } from 'element-plus';
  import * as monaco from 'monaco-editor';
  
  import LeftConfig from './components/leftConfig.vue';
  import RightEdit from './components/rightEdit.vue';
  
  import { PolicyService } from '@/service/api';

  // 类型定义
  interface TreeNode {
    id: string;
    file_name: string;
    nodes?: TreeNode[];
    selectable?: boolean;
    [key: string]: any;
  }

  // 添加 JSON Schema 类型定义
  interface JsonSchema {
    [key: string]: any;
  }

  // 响应式数据
  const route = useRoute();
  const configType = ref<string>(route.params['menuParams']?.toString() || '');
  const configList = ref<TreeNode[]>([]);
  const fileContext = ref<string>('');
  const currentFileName = ref<string>('');
  const language = ref<string>('javascript');

  const routeFileName = () => {
    const fileName = route.query.fileName || route.query.file_name;
    return Array.isArray(fileName) ? fileName[0] || '' : fileName?.toString() || '';
  };

  // 编辑器挂载回调
  const editorMounted = (editor: monaco.editor.IStandaloneCodeEditor) => {
  };

  // 获取文件内容
  const getTextContent = (path: string) => {
    if (!path || !configType.value) return;
    fileContext.value = '';
    currentFileName.value = '';
    
    PolicyService.textContent(configType.value, { file_name: path }).then((res: string) => {
      fileContext.value = res;
      currentFileName.value = path;
      
      const dotIndex = currentFileName.value.lastIndexOf('.');
      if (dotIndex !== -1) {
        language.value = currentFileName.value.substring(dotIndex + 1);
      }
    });
  };

  // 保存文件内容
  const apply = (json: string) => {
    PolicyService.applyJsonText(configType.value, { 
      file_name: currentFileName.value, 
      text: json 
    }).then(() => {
      ElMessage.success('应用成功');
    });
  };
  // 保存文件内容
  const saveText = (json: string) => {
    PolicyService.saveJsonText(configType.value, { 
      file_name: currentFileName.value, 
      text: json 
    }).then(() => {
      ElMessage.success('保存成功');
    });
  };

  // 设置节点可选择性
  const setSelectable = (data: TreeNode[]): TreeNode[] => {
    return data.map(node => {
      if (node.nodes && node.nodes.length) {
        node.selectable = false;
        node.nodes = setSelectable(node.nodes);
      }
      return node;
    });
  };

  // 获取配置列表
  const getListConfig = () => {
    if (!configType.value) return;
    
    PolicyService.getConfig(configType.value).then((res: TreeNode[]) => {
      configList.value = setSelectable(res);
    });
  };

  // 处理添加节点事件
  const handleAddNode = (nodeData: { parent: TreeNode, name: string }) => {
    const params = {
      parent_id: nodeData.parent.id,
      file_name: nodeData.name
    };
    
    PolicyService.addNode(configType.value, params).then(() => {
      ElMessage.success('添加成功');
      getListConfig(); // 重新加载树
    }).catch(() => {
      ElMessage.error('添加失败');
    });
  };

  // 处理编辑节点事件
  const handleEditNode = (nodeData: { node: TreeNode, newName: string }) => {
    const params = {
      id: nodeData.node.id,
      original_file_name: nodeData.node.file_name,
      file_name: nodeData.newName
    };
    
    PolicyService.renameNode(configType.value, params).then(() => {
      ElMessage.success('更新成功');
      getListConfig(); // 重新加载树
    }).catch(() => {
      ElMessage.error('更新失败');
    });
  };

  // 处理删除节点事件
  const handleDeleteNode = (nodeData: TreeNode) => {
    const params = {
      id: nodeData.id,
      file_name: nodeData.file_name
    };
    
    PolicyService.deleteNode(configType.value, params).then(() => {
      ElMessage.success('删除成功');
      getListConfig(); // 重新加载树
      
      // 如果删除的是当前正在编辑的文件，则清空编辑器
      if (currentFileName.value === nodeData.file_name) {
        fileContext.value = '';
        currentFileName.value = '';
      }
    }).catch(() => {
      ElMessage.error('删除失败');
    });
  };

  // 初始化数据
  getListConfig();
  if (routeFileName()) {
    getTextContent(routeFileName());
  }

  // 监听路由变化
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
    { deep: true }
  );
</script>

<style lang="scss" scoped>
.rule-div {
  height: 100%;
}

.policy-splitter {
  height: 100%;
}

.policy-splitter :deep(.el-splitter__pane) {
  overflow: auto;
}
</style>
