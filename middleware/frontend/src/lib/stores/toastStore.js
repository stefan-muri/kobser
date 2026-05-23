import { writable } from 'svelte/store';

export const toasts = writable([]);

export function showToast(message, type = 'info') {
  const id = Date.now() + Math.random();
  toasts.update(t => [{ id, message, type }, ...t]);
  setTimeout(() => {
    toasts.update(t => t.filter(x => x.id !== id));
  }, 3300);
}
