import { onMounted, onUnmounted, toValue, type MaybeRefOrGetter, type Ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  buildUiThemeContractV1,
  isUiThemeReadyEvent,
  resolveUiNavigationEvent,
  resolveUiTargetOrigin,
} from '@/theme/plugin-ui-contract';

export function usePluginUiBridge(
  iframeRef: Ref<HTMLIFrameElement | null>,
  iframeUrl: MaybeRefOrGetter<string>,
) {
  const router = useRouter();

  const sendUiTheme = (): boolean => {
    if (typeof window === 'undefined') {
      return false;
    }

    const iframeWindow = iframeRef.value?.contentWindow;
    const targetOrigin = resolveUiTargetOrigin(toValue(iframeUrl), window.location.origin);
    if (!iframeWindow || !targetOrigin) {
      return false;
    }

    iframeWindow.postMessage(buildUiThemeContractV1(), targetOrigin);
    return true;
  };

  const handleMessage = (event: MessageEvent) => {
    const targetOrigin = resolveUiTargetOrigin(toValue(iframeUrl), window.location.origin);
    if (!targetOrigin) {
      return;
    }

    const iframeWindow = iframeRef.value?.contentWindow;
    if (isUiThemeReadyEvent(event, iframeWindow, targetOrigin)) {
      sendUiTheme();
      return;
    }

    const navigationTarget = resolveUiNavigationEvent(event, iframeWindow, targetOrigin);
    if (navigationTarget) {
      void router.push(navigationTarget);
    }
  };

  onMounted(() => window.addEventListener('message', handleMessage));
  onUnmounted(() => window.removeEventListener('message', handleMessage));

  return {
    handleIframeLoad: sendUiTheme,
    sendUiTheme,
  };
}
