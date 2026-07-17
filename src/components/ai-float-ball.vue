<template>
  <div class="ai-float-ball-wrapper" :style="wrapperStyle">
    <transition name="fade">
      <div
        v-show="isVisible"
        class="ai-float-ball"
        :class="{ 'is-dragging': isDragging }"
        @pointerdown="onDragStart"
        @pointermove="onDragMove"
        @pointerup="onDragEnd"
        @pointercancel="onDragEnd"
        @click="onBallClick"
      >
        <img
          :src="copilotImg"
          alt="copilot"
          class="ai-float-ball-img"
          draggable="false"
          @dragstart.prevent
        />
      </div>
    </transition>

    <transition name="slide-up">
      <div v-if="isExpanded" class="ai-chat-window-container">
        <div class="chat-header">
          <button
            class="action-btn"
            aria-label="在新页面打开对话"
            title="在新页面打开对话"
            @click="openChatPage"
          >
            <el-icon><TopRight /></el-icon>
          </button>
          <button class="action-btn" aria-label="关闭" title="关闭" @click="closeWindow">
            <el-icon><Close /></el-icon>
          </button>
        </div>
        <view-center :suggestions="[]" :chat-session-id="chatSessionId" chat-session-type="ask" />
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { Close, TopRight } from '@element-plus/icons-vue';
import { generateUUID } from '@/utils/util-common';
import copilotImg from '@/assets/images/copilot.png';

const ViewCenter = defineAsyncComponent(() => import('@/views/dih/components/view-center.vue'));
const route = useRoute();
const isExpanded = ref(false);
const chatSessionId = ref('');

// 拖拽相关状态
const isDragging = ref(false);
const justDragged = ref(false);
const ballPosition = ref({ right: 30, bottom: 30 });
let dragEndTimeout: ReturnType<typeof setTimeout>;

type DragState = {
  pointerId: number;
  offsetX: number;
  offsetY: number;
  width: number;
  height: number;
  startX: number;
  startY: number;
};

const dragState = ref<DragState | null>(null);

const wrapperStyle = computed(() => ({
  right: `${ballPosition.value.right}px`,
  bottom: `${ballPosition.value.bottom}px`,
}));

const isLoginOrDihPage = computed(() => {
  return route.path === '/user/login' || route.path === '/service/dih';
});
const hiddenByScroll = ref(false);

const isVisible = computed(() => {
  return !isLoginOrDihPage.value && !isExpanded.value && !hiddenByScroll.value;
});

const toggleExpand = () => {
  isExpanded.value = !isExpanded.value;
  if (isExpanded.value && !chatSessionId.value) {
    chatSessionId.value = generateUUID();
  }
};

const closeWindow = () => {
  isExpanded.value = false;
};

const openChatPage = () => {
  if (!chatSessionId.value) {
    chatSessionId.value = generateUUID();
  }

  const baseUrl = window.location.origin + window.location.pathname;
  const chatPageUrl = `${baseUrl}#/service/dih?type=ask&chatSessionId=${chatSessionId.value}`;
  window.open(chatPageUrl, '_blank', 'noopener,noreferrer');
};

const clamp = (value: number, min: number, max: number) => {
  return Math.min(Math.max(value, min), max);
};

// 拖拽开始
const onDragStart = (e: PointerEvent) => {
  if (e.pointerType === 'mouse' && e.button !== 0) return;
  e.preventDefault();
  const target = e.currentTarget as HTMLElement;
  const rect = target.getBoundingClientRect();
  isDragging.value = true;
  justDragged.value = false;
  dragState.value = {
    pointerId: e.pointerId,
    offsetX: e.clientX - rect.left,
    offsetY: e.clientY - rect.top,
    width: rect.width,
    height: rect.height,
    startX: e.clientX,
    startY: e.clientY,
  };
  target.setPointerCapture(e.pointerId);
};

// 拖拽移动
const onDragMove = (e: PointerEvent) => {
  const state = dragState.value;
  if (!isDragging.value || !state || e.pointerId !== state.pointerId) return;
  e.preventDefault();

  const movedX = e.clientX - state.startX;
  const movedY = e.clientY - state.startY;
  if (movedX * movedX + movedY * movedY > 9) {
    justDragged.value = true;
  }

  const maxLeft = Math.max(0, window.innerWidth - state.width);
  const maxTop = Math.max(0, window.innerHeight - state.height);
  const left = clamp(e.clientX - state.offsetX, 0, maxLeft);
  const top = clamp(e.clientY - state.offsetY, 0, maxTop);
  ballPosition.value = {
    right: window.innerWidth - left - state.width,
    bottom: window.innerHeight - top - state.height,
  };
};

// 拖拽结束
const onDragEnd = (e?: PointerEvent) => {
  const state = dragState.value;
  if (state && e && e.pointerId !== state.pointerId) return;
  if (state && e?.pointerId === state.pointerId) {
    const target = e.currentTarget as HTMLElement;
    if (target.hasPointerCapture(state.pointerId)) {
      target.releasePointerCapture(state.pointerId);
    }
  }
  isDragging.value = false;
  dragState.value = null;
  clearTimeout(dragEndTimeout);
  dragEndTimeout = setTimeout(() => {
    justDragged.value = false;
  }, 300);
};

// 点击事件（仅在非拖拽时触发）
const onBallClick = () => {
  if (!isDragging.value && !justDragged.value) {
    toggleExpand();
  }
};

let scrollTimeout: ReturnType<typeof setTimeout>;
const handleScroll = () => {
  if (isLoginOrDihPage.value) return;
  clearTimeout(scrollTimeout);
  hiddenByScroll.value = true;
  scrollTimeout = setTimeout(() => {
    if (!isLoginOrDihPage.value) {
      hiddenByScroll.value = false;
    }
  }, 2000);
};

onMounted(() => {
  window.addEventListener('scroll', handleScroll);
});

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll);
  clearTimeout(scrollTimeout);
  clearTimeout(dragEndTimeout);
});
</script>

<style lang="scss" scoped>
.ai-float-ball-wrapper {
  position: fixed;
  z-index: 9999;
}

.ai-float-ball {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #3988ff;
  border-radius: 50%;
  box-shadow: 0 8px 25px rgba(57, 136, 255, 0.35);
  cursor: pointer;
  touch-action: none;
  user-select: none;
  transition: opacity 0.3s ease, box-shadow 0.2s ease, background 0.2s ease;
  opacity: 0.8;

  &:hover {
    transform: translateY(-2px);
    background: #2d7df0;
    box-shadow: 0 12px 35px rgba(57, 136, 255, 0.45);
  }

  &.is-dragging {
    cursor: grabbing;
    transition: none;

    &:hover {
      transform: none;
    }
  }

  .ai-float-ball-img {
    width: 56px;
    height: 56px;
    pointer-events: none;
    user-select: none;
    -webkit-user-drag: none;
  }
}

.ai-chat-window-container {
  width: 450px;
  max-height: 600px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin-bottom: 15px;

  :deep(.center-panel) {
    flex: 1;
    border-radius: 0;
    box-shadow: none;
    min-height: 0;
  }

  :deep(.center-header) {
    display: none;
  }

  :deep(.chat-content) {
    padding: 12px;
    flex: 1;
    overflow-y: auto;
    min-height: 0;
  }

  :deep(.input-area) {
    padding: 10px;
    border-top: 1px solid #f0f0f0;
    flex-shrink: 0;
  }

  :deep(.suggestions) {
    display: none;
  }

  :deep(.message-list) {
    gap: 20px;
    padding: 10px;
  }

  :deep(.message-bubble) {
    max-width: 90%;
  }
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 40px;
  padding: 5px 10px;
  background: #3988ff;
  color: #fff;
}

.action-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 8px;
  color: #fff;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
  }

  .el-icon {
    font-size: 15px;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
