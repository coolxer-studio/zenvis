<template>
  <div class="ai-float-ball-wrapper" :style="wrapperStyle">
    <transition name="float-ball">
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

    <transition name="chat-pop">
      <div v-if="isExpanded" class="ai-chat-window-container">
        <div class="chat-header">
          <div class="chat-header-brand">
            <span class="chat-header-logo">
              <img :src="copilotImg" alt="" />
            </span>
            <span class="chat-header-copy">
              <strong>智能助手</strong>
              <small>AI Copilot</small>
            </span>
          </div>
          <div class="chat-header-actions">
            <button
              class="action-btn"
              aria-label="在新页面打开对话"
              title="在新页面打开对话"
              @click="openChatPage"
            >
              <el-icon><FullScreen /></el-icon>
            </button>
            <button
              class="action-btn is-close"
              aria-label="关闭对话"
              title="关闭对话"
              @click="closeWindow"
            >
              <el-icon><CloseBold /></el-icon>
            </button>
          </div>
        </div>
        <view-center
          :suggestions="[]"
          :chat-session-id="chatSessionId"
          chat-session-type="ask"
          variant="compact"
        />
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { CloseBold, FullScreen } from '@element-plus/icons-vue';
import { generateUUID } from '@/utils/util-common';
import copilotImg from '@/assets/images/ai-assistant-avatar.png';

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
  transition: opacity 0.3s ease, transform 180ms cubic-bezier(0.2, 0.8, 0.2, 1),
    box-shadow 0.2s ease, background 0.2s ease;
  opacity: 0.8;

  &:hover {
    transform: translateY(-2px) scale(1.1);
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
  transform-origin: calc(100% - 28px) 100%;
  will-change: transform, opacity, filter;

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
    overflow-x: hidden;
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
    width: 100%;
    min-width: 0;
    box-sizing: border-box;
    gap: 20px;
    padding: 10px;
  }

  :deep(.message-item),
  :deep(.ai-message-container),
  :deep(.user-message-container),
  :deep(.message-content),
  :deep(.markdown-body) {
    min-width: 0;
    max-width: 100%;
  }

  :deep(.message-content),
  :deep(.markdown-body) {
    word-break: break-word;
    overflow-wrap: anywhere;
  }

  :deep(.message-bubble) {
    max-width: 90%;
  }
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 54px;
  padding: 7px 10px 7px 12px;
  color: var(--zv-text-primary, #15233a);
  background: radial-gradient(circle at 12% 0%, rgb(47 94 229 / 10%), transparent 42%),
    linear-gradient(135deg, #fff 0%, var(--zv-bg-secondary, #f7f9fc) 100%);
  border-bottom: 1px solid var(--zv-border-light, #e5eaf2);
}

.chat-header-brand,
.chat-header-actions {
  display: flex;
  align-items: center;
}

.chat-header-brand {
  min-width: 0;
  gap: 9px;
}

.chat-header-logo {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  overflow: hidden;
  background: linear-gradient(145deg, var(--zv-primary, #2f5ee5), var(--zv-accent, #0f9fa3));
  border: 1px solid rgb(255 255 255 / 80%);
  border-radius: 10px;
  box-shadow: 0 5px 14px rgb(47 94 229 / 20%);
  place-items: center;
}

.chat-header-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.chat-header-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  line-height: 1.2;
}

.chat-header-copy strong {
  overflow: hidden;
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-header-copy small {
  margin-top: 3px;
  color: var(--zv-text-tertiary, #66758a);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.chat-header-actions {
  flex: 0 0 auto;
  gap: 4px;
}

.action-btn {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 9px;
  color: var(--zv-text-secondary, #47556b);
  cursor: pointer;
  transition: color 160ms ease, background-color 160ms ease, transform 160ms ease;

  &:hover {
    color: var(--zv-primary, #2f5ee5);
    background: rgb(47 94 229 / 9%);
    transform: translateY(-1px);
  }

  &:focus-visible {
    outline: 2px solid rgb(47 94 229 / 25%);
    outline-offset: 1px;
  }

  &.is-close:hover {
    color: var(--zv-danger, #d1435b);
    background: rgb(209 67 91 / 9%);
  }

  .el-icon {
    font-size: 16px;
  }
}

.float-ball-enter-active {
  transition: opacity 180ms ease-out, transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.float-ball-leave-active {
  transition: opacity 120ms ease-in, transform 150ms ease-in;
}

.float-ball-enter-from {
  opacity: 0;
  transform: scale(0.72) rotate(7deg);
}

.float-ball-leave-to {
  opacity: 0;
  transform: scale(0.82);
}

.chat-pop-enter-active {
  transition: opacity 220ms ease-out, transform 280ms cubic-bezier(0.16, 1, 0.3, 1),
    filter 220ms ease-out;
}

.chat-pop-leave-active {
  transition: opacity 150ms ease-in, transform 180ms cubic-bezier(0.4, 0, 1, 1),
    filter 150ms ease-in;
}

.chat-pop-enter-from {
  opacity: 0;
  filter: blur(5px);
  transform: translate3d(18px, 18px, 0) scale(0.86);
}

.chat-pop-leave-to {
  opacity: 0;
  filter: blur(3px);
  transform: translate3d(14px, 16px, 0) scale(0.9);
}

@media (prefers-reduced-motion: reduce) {
  .float-ball-enter-active,
  .float-ball-leave-active,
  .chat-pop-enter-active,
  .chat-pop-leave-active {
    transition-duration: 1ms;
  }

  .float-ball-enter-from,
  .float-ball-leave-to,
  .chat-pop-enter-from,
  .chat-pop-leave-to {
    filter: none;
    transform: none;
  }
}
</style>
