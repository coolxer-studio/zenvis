import { onScopeDispose, readonly, ref } from 'vue';

const currentTime = ref(Date.now());
let timer: number | undefined;
let subscribers = 0;

const startClock = () => {
  if (timer !== undefined || typeof window === 'undefined') return;
  currentTime.value = Date.now();
  timer = window.setInterval(() => {
    currentTime.value = Date.now();
  }, 1000);
};

const stopClock = () => {
  if (timer === undefined || typeof window === 'undefined') return;
  window.clearInterval(timer);
  timer = undefined;
};

export const useSecondClock = () => {
  subscribers += 1;
  startClock();

  onScopeDispose(() => {
    subscribers = Math.max(0, subscribers - 1);
    if (subscribers === 0) stopClock();
  });

  return readonly(currentTime);
};
