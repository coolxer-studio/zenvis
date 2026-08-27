<template>
  <div class="conversation-sidebar">
    <button class="sidebar-brand" type="button" aria-label="返回仪表盘" @click="goDashboard">
      <span class="brand-mark">
        <img v-if="logoUrl" :src="logoUrl" alt="" />
        <el-icon v-else><DataAnalysis /></el-icon>
      </span>
      <span class="brand-copy">
        <strong :title="systemInfo?.systemTitle || 'ZenVis'">{{
          systemInfo?.systemTitle || 'ZenVis'
        }}</strong>
        <small :title="systemInfo?.systemSubtitle || 'Intelligent Analytics'">
          {{ systemInfo?.systemSubtitle || 'Intelligent Analytics' }}
        </small>
      </span>
    </button>

    <div class="chat-action">
      <el-button class="new-chat-btn" type="primary" :icon="Plus" @click="createNewChat">
        新建会话
      </el-button>
    </div>

    <div class="chat-scroll no-scrollbar">
      <section v-if="chatPinList.length" class="chat-section">
        <div class="section-label">
          <span>置顶会话</span>
          <span>{{ chatPinList.length }}</span>
        </div>
        <div class="chat-list">
          <div
            v-for="(item, index) in chatPinList"
            :key="item.id"
            class="chat-item"
            :class="{ active: isActiveSession(item.sessionId) }"
            role="button"
            tabindex="0"
            @click="selectSession(item)"
            @keydown.enter="selectSession(item)"
          >
            <el-icon class="chat-kind-icon"><Paperclip /></el-icon>
            <span class="chat-title" :title="item.title">{{ item.title }}</span>
            <el-dropdown trigger="click" @command="command => handlePinListCommand(command, index)">
              <el-button
                class="more-btn"
                text
                circle
                :icon="MoreFilled"
                aria-label="置顶会话操作"
                @click.stop
              />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="pinDown" :icon="Bottom">取消置顶</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </section>

      <section class="chat-section history-section">
        <div class="section-label">
          <span>最近会话</span>
          <el-icon><Clock /></el-icon>
        </div>
        <div class="chat-list">
          <div
            v-for="(item, index) in chatHistory"
            :key="item.id"
            class="chat-item"
            :class="{ active: isActiveSession(item.sessionId) }"
            role="button"
            tabindex="0"
            @click="selectSession(item)"
            @keydown.enter="selectSession(item)"
          >
            <el-icon class="chat-kind-icon"><ChatDotRound /></el-icon>
            <span class="chat-title" :title="item.title">{{ item.title }}</span>
            <el-dropdown
              trigger="click"
              @command="command => handleChatListCommand(command, index)"
            >
              <el-button
                class="more-btn"
                text
                circle
                :icon="MoreFilled"
                aria-label="会话操作"
                @click.stop
              />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit" :icon="EditPen">编辑标题</el-dropdown-item>
                  <el-dropdown-item v-if="item.pin" command="pinDown" :icon="Bottom"
                    >取消置顶</el-dropdown-item
                  >
                  <el-dropdown-item v-else command="pinTop" :icon="Top">置顶</el-dropdown-item>
                  <el-dropdown-item command="delete" :icon="Delete" class="delete-item"
                    >删除</el-dropdown-item
                  >
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <el-button v-if="hasMore" class="view-more" text type="primary" @click="viewMoreChats">
            加载更多
          </el-button>
          <el-empty v-if="!chatHistory.length" description="暂无会话" :image-size="54" />
        </div>
      </section>
    </div>

    <footer class="user-footer">
      <span class="user-avatar">{{ userInitial }}</span>
      <span class="user-copy">
        <strong>{{ userInfo?.name || '当前用户' }}</strong>
        <small>{{ userInfo?.email || '智能分析用户' }}</small>
      </span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Bottom,
  ChatDotRound,
  Clock,
  DataAnalysis,
  Delete,
  EditPen,
  MoreFilled,
  Paperclip,
  Plus,
  Top,
} from '@element-plus/icons-vue';
import { ElMessageBox } from 'element-plus';

import { DihService } from '@/service/api';
import type { SystemInfo } from '@/types/type-system';
import { generateUUID } from '@/utils/util-common';
import { getAssetUrl } from '@u/url';
import { NEW_CHAT_CREATED_EVENT, useDihEventListener } from '../events';
import type { DihChatListItem, NewChatCreatedEventDetail } from '../events';

const props = defineProps<{
  systemInfo?: SystemInfo;
  userInfo?: { name?: string; email?: string };
}>();
const emit = defineEmits<{ (event: 'navigate'): void }>();

const router = useRouter();
const route = useRoute();
const chatPinList = ref<DihChatListItem[]>([]);
const chatHistory = ref<DihChatListItem[]>([]);
const pageParams = ref({ page: 1, per_page: 10 });
const hasMore = ref(true);

const logoUrl = computed(() =>
  props.systemInfo?.systemLogo ? getAssetUrl(props.systemInfo.systemLogo) : '',
);
const userInitial = computed(() =>
  (props.userInfo?.name || props.userInfo?.email || 'U').trim().slice(0, 1).toUpperCase(),
);
const currentSessionId = computed(() => String(route.query.chatSessionId || ''));
const isActiveSession = (sessionId?: string) =>
  Boolean(sessionId && sessionId === currentSessionId.value);

const loadPinnedChats = async () => {
  chatPinList.value = await DihService.getChatSessionForPin();
};

const loadChatHistory = async () => {
  const items = await DihService.getChatSessionPageList(pageParams.value);
  chatHistory.value = pageParams.value.page === 1 ? items : [...chatHistory.value, ...items];
  hasMore.value = items.length === pageParams.value.per_page;
};

const goDashboard = () => {
  emit('navigate');
  void router.push({ name: 'dashboard' });
};

const createNewChat = () => {
  emit('navigate');
  void router.push({
    name: 'service-dih',
    query: { type: 'ask', chatSessionId: generateUUID(), createSession: 1 },
  });
};

const selectSession = (item: DihChatListItem) => {
  emit('navigate');
  void router.push({
    name: 'service-dih',
    query: { type: item.type, chatSessionId: item.sessionId },
  });
};

const handlePinListCommand = async (command: string, index: number) => {
  if (command !== 'pinDown') return;
  const item = chatPinList.value[index];
  await DihService.updateChatSession(item.id, { pin: false });
  chatPinList.value.splice(index, 1);
  const historyItem = chatHistory.value.find(history => history.id === item.id);
  if (historyItem) historyItem.pin = false;
};

const handleChatListCommand = async (command: string, index: number) => {
  const item = chatHistory.value[index];
  if (command === 'edit') {
    try {
      const { value } = await ElMessageBox.prompt('请输入新的标题', '编辑标题', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: item.title,
        inputPattern: /\S/,
        inputErrorMessage: '标题不能为空',
      });
      await DihService.updateChatSession(item.id, { title: value });
      item.title = value;
      const pinned = chatPinList.value.find(pin => pin.id === item.id);
      if (pinned) pinned.title = value;
    } catch {
      return;
    }
    return;
  }
  if (command === 'pinTop') {
    await DihService.updateChatSession(item.id, { pin: true });
    item.pin = true;
    if (!chatPinList.value.some(pin => pin.id === item.id)) chatPinList.value.unshift(item);
    return;
  }
  if (command === 'pinDown') {
    await DihService.updateChatSession(item.id, { pin: false });
    item.pin = false;
    chatPinList.value = chatPinList.value.filter(pin => pin.id !== item.id);
    return;
  }
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm(`确定要删除会话“${item.title}”吗？`, '删除确认', {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      });
      await DihService.deleteChatSession(item.id);
      chatHistory.value.splice(index, 1);
      chatPinList.value = chatPinList.value.filter(pin => pin.id !== item.id);
      if (isActiveSession(item.sessionId)) createNewChat();
    } catch {
      return;
    }
  }
};

const viewMoreChats = () => {
  pageParams.value.page += 1;
  void loadChatHistory();
};

const handleNewChatCreated = ({ chatItem }: NewChatCreatedEventDetail) => {
  if (
    !chatHistory.value.some(
      item => item.id === chatItem.id || item.sessionId === chatItem.sessionId,
    )
  ) {
    chatHistory.value.unshift(chatItem);
  }
};

useDihEventListener(NEW_CHAT_CREATED_EVENT, handleNewChatCreated);

onMounted(() => {
  void loadPinnedChats();
  void loadChatHistory();
});
</script>

<style lang="scss" scoped>
.conversation-sidebar {
  display: flex;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: var(--zv-space-4) var(--zv-space-3) var(--zv-space-3);
  overflow: hidden;
  flex-direction: column;
  background: var(--zv-bg-surface);
}

.sidebar-brand {
  display: flex;
  min-width: 0;
  min-height: 54px;
  padding: 4px 8px;
  align-items: center;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.brand-mark {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  overflow: hidden;
  color: var(--zv-text-inverse);
  font-size: 21px;
  place-items: center;
  background: linear-gradient(145deg, var(--zv-primary), var(--zv-accent));
  border-radius: var(--zv-radius-md);
  box-shadow: 0 8px 18px rgb(47 94 229 / 20%);
}

.brand-mark img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.brand-copy,
.user-copy {
  display: flex;
  min-width: 0;
  margin-left: 11px;
  flex-direction: column;
}

.brand-copy strong,
.brand-copy small,
.user-copy strong,
.user-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-copy strong {
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-xl);
  font-weight: var(--zv-font-weight-bold);
}

.brand-copy small {
  margin-top: 4px;
  color: var(--zv-text-muted);
  font-size: 9px;
  font-weight: var(--zv-font-weight-semibold);
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.chat-action {
  margin: var(--zv-space-4) 0 var(--zv-space-5);
}

.new-chat-btn {
  width: 100%;
  height: 40px;
  border-radius: var(--zv-radius-md);
  box-shadow: 0 7px 16px rgb(47 94 229 / 18%);
}

.chat-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.chat-section + .chat-section {
  margin-top: var(--zv-space-5);
  padding-top: var(--zv-space-4);
  border-top: 1px solid var(--zv-divider);
}

.section-label {
  display: flex;
  margin-bottom: var(--zv-space-2);
  padding: 0 var(--zv-space-2);
  align-items: center;
  justify-content: space-between;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
  font-weight: var(--zv-font-weight-semibold);
}

.chat-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.chat-item {
  display: flex;
  min-width: 0;
  min-height: 38px;
  padding: 6px 5px 6px 9px;
  align-items: center;
  gap: var(--zv-space-2);
  color: var(--zv-text-secondary);
  font-size: var(--zv-font-size-sm);
  border: 1px solid transparent;
  border-radius: var(--zv-radius-md);
  cursor: pointer;
  transition: all var(--zv-motion-fast) var(--zv-ease-standard);
}

.chat-item:hover {
  color: var(--zv-text-primary);
  background: var(--zv-bg-subtle);
}

.chat-item.active {
  color: var(--zv-primary);
  background: var(--zv-primary-soft);
  border-color: var(--zv-primary-border);
}

.chat-kind-icon {
  flex: 0 0 auto;
}

.chat-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.more-btn {
  width: 26px;
  height: 26px;
  opacity: 0;
}

.chat-item:hover .more-btn,
.chat-item.active .more-btn,
.more-btn:focus-visible {
  opacity: 1;
}

.view-more {
  align-self: center;
  margin-top: var(--zv-space-2);
  padding-inline: var(--zv-space-2);
  background: transparent !important;
  border: 0 !important;
  box-shadow: none !important;
}

.view-more:hover,
.view-more:focus-visible {
  color: var(--zv-primary-hover);
  background: transparent !important;
}

.user-footer {
  display: flex;
  min-width: 0;
  min-height: 58px;
  margin-top: var(--zv-space-3);
  padding: var(--zv-space-3) var(--zv-space-2) 0;
  align-items: center;
  border-top: 1px solid var(--zv-divider);
}

.user-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  color: var(--zv-text-inverse);
  font-size: var(--zv-font-size-sm);
  font-weight: var(--zv-font-weight-bold);
  place-items: center;
  background: linear-gradient(145deg, var(--zv-primary), var(--zv-accent));
  border-radius: 50%;
}

.user-copy strong {
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-sm);
}

.user-copy small {
  margin-top: 3px;
  color: var(--zv-text-muted);
  font-size: 11px;
}
</style>
