import { onMounted, onUnmounted, toValue, type MaybeRefOrGetter, type Ref } from 'vue';
import {
  buildUiThemeContractV1,
  isUiThemeReadyEvent,
  resolveUiTargetOrigin,
} from '@/theme/plugin-ui-contract';

export function usePluginUiBridge(
  iframeRef: Ref<HTMLIFrameElement | null>,
  iframeUrl: MaybeRefOrGetter<string>,
) {
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
    if (
      !targetOrigin ||
      !isUiThemeReadyEvent(event, iframeRef.value?.contentWindow, targetOrigin)
    ) {
      return;
    }

    sendUiTheme();
  };

  onMounted(() => window.addEventListener('message', handleMessage));
  onUnmounted(() => window.removeEventListener('message', handleMessage));

  return {
    handleIframeLoad: sendUiTheme,
    sendUiTheme,
  };
}
