import { writable } from 'svelte/store';
import { importStatus } from '../api.js';

// Holds the currently-tracked playlist import (or null). Lives at module scope
// so it survives the import dialog closing and view/tab changes.
export const activeImport = writable(null);

let timer = null;

export function startImport(importId, name, total) {
  activeImport.set({
    importId, name, status: 'running', total,
    current: 0, downloaded: 0, existing: 0, failed: 0, failures: [],
  });
  if (timer) clearInterval(timer);
  timer = setInterval(async () => {
    try {
      const s = await importStatus(importId);
      activeImport.set(s);
      if (s.status !== 'running') { clearInterval(timer); timer = null; }
    } catch {
      clearInterval(timer); timer = null;
    }
  }, 1500);
}

export function clearImport() {
  if (timer) { clearInterval(timer); timer = null; }
  activeImport.set(null);
}
