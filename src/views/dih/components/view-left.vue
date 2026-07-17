<template>
  <div class="panel left-panel">
    <!-- 新建会话按钮 -->
    <div class="chat-action">
      <el-button class="new-chat-btn" block @click="createNewChat">
        <div class="btn-content">
          <div class="btn-icon">
            <el-icon><Plus /></el-icon>
          </div>
          <div class="btn-text">新建会话</div>
        </div>
      </el-button>
    </div>

    <div class="chat-container">
      <div class="chat-list">
        <div 
          class="chat-item" 
          v-for="(item, index) in chatPinList" 
          :key="item.id" 
          :class="{ active: index === activePinChat }"
          @click="selectPinChat(index)"
        >
          <div class="chat-title">{{ item.title }}</div>
          <div class="chat-actions">
            <el-dropdown trigger="click" @command="(command) => handlePinListCommand(command, index)">
              <el-button class="more-btn" type="text">
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item  command="pinDown" :icon="Bottom">取消置顶</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    
      <!-- 历史会话列表 -->
      <div class="chat-history">
        <div class="history-header">
          <el-icon><Clock /></el-icon>
          历史会话
        </div>
        
        <div class="chat-list">
          <div 
            class="chat-item" 
            v-for="(item, index) in chatHistory" 
            :key="item.id" 
            :class="{ active: index === activeChat }"
            @click="selectChat(index)"
          >
            <div class="chat-title">{{ item.title }}</div>
            <div class="chat-actions">
              <el-dropdown trigger="click" @command="(command) => handleChatListCommand(command, index)">
                <el-button class="more-btn" type="text">
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit" :icon="EditPen">编辑标题</el-dropdown-item>
                    <el-dropdown-item  v-if="item.pin" command="pinDown" :icon="Bottom">取消置顶</el-dropdown-item>
                    <el-dropdown-item  v-else command="pinTop" :icon="Top">置顶</el-dropdown-item>
                    <el-dropdown-item command="delete" :icon="Delete" class="delete-item">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          
          <div class="view-more" @click="viewMoreChats" v-if="hasMore">
            加载更多
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  Plus, Clock, MoreFilled, EditPen, Top,Bottom, Delete
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { DihService } from '@/service/api'
import { generateUUID } from '@/utils/util-common'
import { NEW_CHAT_CREATED_EVENT, useDihEventListener } from '../events'
import type { DihChatListItem, NewChatCreatedEventDetail } from '../events'

const router = useRouter();

// 当前激活的聊天，默认不选中（置顶列表）
const activePinChat = ref(-1)

// 当前激活的聊天，默认选中第一个(历史列表)
const activeChat = ref(0)

// 置顶的聊天记录
const chatPinList = ref<DihChatListItem[]>([])

// 聊天历史数据
const chatHistory = ref<DihChatListItem[]>([])

// 分页参数
const pageParams = ref({
  page: 1,
  per_page: 10
})

// 是否还有更多数据
const hasMore = ref(true)

// 获取置顶聊天记录
const loadPinnedChats = async () => {
  try {
    chatPinList.value = await DihService.getChatSessionForPin()
  } catch (error) {
    console.error('获取置顶聊天记录失败:', error)
  }
}

// 获取聊天历史数据
const loadChatHistory = async () => {
  try {
    const historyChatList = await DihService.getChatSessionPageList({
      page: pageParams.value.page,
      per_page: pageParams.value.per_page
    })
    // 如果是第一页，直接赋值；否则追加到现有数据
    if (pageParams.value.page === 1) {
      chatHistory.value = historyChatList
    } else {
      chatHistory.value = [...chatHistory.value, ...historyChatList]
    }
    // 判断是否还有更多数据
    hasMore.value = historyChatList.length === pageParams.value.per_page
  } catch (error) {
    console.error('获取聊天历史数据失败:', error)
  }
}

// 创建新聊天
const createNewChat = () => {
  console.log('创建新聊天')
  //取消激活的样式
  activePinChat.value = -1;
  activeChat.value = -1;
  // 这里可以添加实际的创建逻辑
  const chatSessionId = generateUUID();
  router.push({
    name: 'service-dih',
    query: {
      type: 'ask',
      chatSessionId: chatSessionId,
      createSession: 1
    }
  });
}

// 选择聊天(置顶)
const selectPinChat = (index: number) => {
  activeChat.value = -1
  activePinChat.value = index;
  const sessionId = chatPinList.value[index].sessionId;
  const sessionType = chatPinList.value[index].type;
  console.log(`选择聊天项: ${sessionId}`)
  // 跳转到当前路由并传递sessionId作为查询参数
  router.push({
    name: 'service-dih',
    query: {
      type: sessionType,
      chatSessionId: sessionId
    }
  });
}
// 选择聊天（历史列表）
const selectChat = (index: number) => {
  activeChat.value = index
  activePinChat.value = -1;
  const sessionId = chatHistory.value[index].sessionId;
  const sessionType = chatHistory.value[index].type;
  console.log(`选择聊天项: ${sessionId}`)
  // 跳转到当前路由并传递sessionId作为查询参数
  router.push({
    name: 'service-dih',
    query: {
      type: sessionType,
      chatSessionId: sessionId
    }
  });
}

// 处理下拉菜单命令
const handlePinListCommand = (command: string, index: number) => {
  const item = chatPinList.value[index]
  switch (command) {
      case 'pinDown':
      // 取消置顶
      DihService.updateChatSession(item.id, { pin: false })
        .then(() => {
          // 从历史列表中的获取当前项，修改置顶状态
          chatHistory.value.forEach(item => { 
            if(item.id === chatPinList.value[index].id){
              item.pin = false
            }
          })
          // 从列表中移除取消置顶的项
          chatPinList.value.splice(index, 1)
        })
        .catch((error) => {
          console.error('取消置顶操作失败:', error);
        });
      break
  }
}
// 处理下拉菜单命令
const handleChatListCommand = (command: string, index: number) => {
  const item = chatHistory.value[index]
  switch (command) {
    case 'edit':
      // 编辑标题逻辑
      ElMessageBox.prompt('请输入新的标题', '编辑标题', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: item.title,
        inputPattern: /\S/,
        inputErrorMessage: '标题不能为空',
      })
        .then(async ({ value }) => {
          try {
            await DihService.updateChatSession(item.id, { title: value })
            // 更新本地数据
            item.title = value
            // 置顶的也需要更新 
            if(item.pin){
              chatPinList.value.forEach(itemPin => {
                if(itemPin.id === item.id){
                  itemPin.title = value
                }
              })
            }
            console.log(`标题已更新为: ${value}`)
          } catch (error) {
            console.error('更新失败:', error)
          }
        })
        .catch(() => {
          console.log('取消编辑')
        })
      break
    case 'pinTop':
      // 置顶
      DihService.updateChatSession(item.id, { pin: true })
        .then(() => {
          // 更新本地数据
          loadChatHistory()
          loadPinnedChats()
          // 从列表中修改
          item.pin = true;
          // 添加到置顶列表
          chatPinList.value.push(item);
        })
        .catch((error) => {
          console.error('置顶操作失败:', error);
        });
      break

    case 'pinDown':
      // 取消置顶
      DihService.updateChatSession(item.id, { pin: false })
        .then(() => {
          //修改置顶状态
          item.pin = false
          // 从置顶列表中查询出，并且移除取消置顶的项
          for (let i = 0; i < chatPinList.value.length; i++) {
            if(chatPinList.value[i].id === item.id){
              chatPinList.value.splice(i, 1);
              break;
            }
          }
        })
        .catch((error) => {
          console.error('取消置顶操作失败:', error);
        });
      break
    
    case 'delete':
      // 删除逻辑
      ElMessageBox.confirm(
        `确定要删除会话"${item.title}"吗？`,
        '删除确认',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        }
      )
      .then(async () => {
        try {
          await DihService.deleteChatSession(item.id)
          // 从列表中移除已删除的项
          chatHistory.value.splice(index, 1)
          // 置顶的也需要从列表中移除已删除的项
            if(item.pin){
              chatPinList.value = chatPinList.value.filter(pinItem => pinItem.id !== item.id);
            }
          // 如果删除的是当前激活的聊天，重置激活项
          if (index === activeChat.value) {
            activeChat.value = 0
          }
          console.log(`删除: ${item.title}`)
        } catch (error) {
          console.error('删除失败:', error)
        }
      })
      .catch(() => {
        // 用户取消删除
        console.log('取消删除')
      })
      break
  }
}

// 查看更多聊天记录（分页加载）
const viewMoreChats = () => {
  if (hasMore.value) {
    pageParams.value.page++
    loadChatHistory()
  }
}

// 组件挂载时加载数据
onMounted(() => {
  loadPinnedChats()
  loadChatHistory()
})

// 处理新聊天创建事件
const handleNewChatCreated = ({ chatItem }: NewChatCreatedEventDetail) => {
  // 将新的聊天项添加到聊天历史列表的开头
  console.log(chatItem);
  // 判断是否已经存在
  if (chatHistory.value.some(item => item.id === chatItem.id || item.sessionId === chatItem.sessionId)) {
    return;
  }
  // 不存在添加进去
  chatHistory.value.unshift(chatItem);
}

useDihEventListener(NEW_CHAT_CREATED_EVENT, handleNewChatCreated)

</script>

<style scoped>
/* 面板容器 */
.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 10px;
  box-sizing: border-box;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.left-panel {
  background-color: #f5f7fa;
  margin-left: 0;
  padding: 10px;
  display: flex;
  flex-direction: column;
}

/* 左侧面板聊天列表样式 */
.chat-action {
  margin-bottom: 15px;
  width: 100%;
  flex-shrink: 0;
}

.new-chat-btn {
  border-radius: 20px;
  padding: 8px 15px;
  font-size: 14px;
  border: 1px solid #dcdfe6;
  background-color: white;
  width: 100%;
}

.btn-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.btn-icon {
  display: flex;
  align-items: left;
}

.btn-text {
  flex-grow: 1;
  text-align: center;
  margin: 0 10px;
}

.chat-container {
  flex: 1;
  overflow-y: auto;
}

.chat-history {
  display: flex;
  flex-direction: column;
}

.history-header {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 14px;
  color: #606266;
  margin-bottom: 10px;
  padding-left: 5px;
}

.chat-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.chat-item {
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #303133;
  transition: background-color 0.2s;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-item:hover {
  background-color: #e9ecef;
}

.chat-item.active {
  background-color: #e6f1fc;
}

.chat-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-actions {
  display: none;
}

.chat-item:hover .chat-actions,
.chat-item.active .chat-actions {
  display: block;
}

.more-btn {
  padding: 2px;
  font-size: 16px;
  color: #909399;
}

.more-btn:hover {
  color: #606266;
}

:deep(.el-dropdown-menu__item.delete-item) {
  color: #f56c6c;
}

:deep(.el-dropdown-menu__item i) {
  margin-right: 5px;
}

.view-more {
  text-align: center;
  color: #409eff;
  font-size: 14px;
  margin-top: 10px;
  cursor: pointer;
  padding: 5px;
}

.view-more:hover {
  text-decoration: underline;
}
</style>









