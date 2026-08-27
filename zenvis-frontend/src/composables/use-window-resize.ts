import { onScopeDispose } from 'vue';

type ResizeHandler = () => void;

const handlers = new Set<ResizeHandler>();
let listening = false;

const dispatchResize = () => {
  handlers.forEach(handler => handler());
};

const startListening = () => {
  if (listening || typeof window === 'undefined') return;
  window.addEventListener('resize', dispatchResize);
  listening = true;
};

const stopListening = () => {
  if (!listening || handlers.size > 0 || typeof window === 'undefined') return;
  window.removeEventListener('resize', dispatchResize);
  listening = false;
};

export const useWindowResize = (handler: ResizeHandler) => {
  handlers.add(handler);
  startListening();

  onScopeDispose(() => {
    handlers.delete(handler);
    stopListening();
  });
};
