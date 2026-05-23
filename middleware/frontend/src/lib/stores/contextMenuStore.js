import { writable } from 'svelte/store';

export const contextMenuState = writable({ visible: false, x: 0, y: 0, items: [] });

export function showContextMenu(anchorEl, items) {
  const rect = anchorEl.getBoundingClientRect();
  const mw = 200;
  const mh = items.filter(i => i !== null).length * 42 + 16;
  let top = rect.bottom + 4;
  let left = rect.right - mw;
  if (top + mh > window.innerHeight - 8) top = rect.top - mh - 4;
  if (left < 8) left = 8;
  contextMenuState.set({ visible: true, x: left, y: top, items });
  setTimeout(() => document.addEventListener('click', closeContextMenu, { once: true }), 0);
  document.addEventListener('keydown', _escHandler, { once: true });
}

function _escHandler(e) {
  if (e.key === 'Escape') closeContextMenu();
}

export function closeContextMenu() {
  contextMenuState.update(s => ({ ...s, visible: false }));
}
