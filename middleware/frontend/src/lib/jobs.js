import { writable, get } from 'svelte/store';
import { status as statusApi } from './api.js';

export const activeJobs = writable([]);

let pollTimer = null;

export function trackJob(jobId, artist, title) {
  activeJobs.update(jobs => [{ id: jobId, artist, title, status: 'pending', error: null }, ...jobs]);
  ensurePolling();
}

function ensurePolling() {
  if (pollTimer) return;
  pollTimer = setInterval(async () => {
    const jobs = get(activeJobs);
    let anyActive = false;
    for (const j of jobs) {
      if (j.status === 'done' || j.status === 'error' || j.status === 'cancelled') continue;
      anyActive = true;
      try {
        const s = await statusApi(j.id);
        activeJobs.update(list => list.map(x => x.id === j.id ? { ...x, status: s.status, error: s.error } : x));
      } catch (e) {
        activeJobs.update(list => list.map(x => x.id === j.id ? { ...x, error: e.message } : x));
      }
    }
    if (!anyActive) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }, 2000);
}
