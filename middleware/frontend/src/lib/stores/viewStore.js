import { writable } from 'svelte/store';

const LAST_VIEW_KEY = 'kobser:last-view';
const VALID_VIEWS = ['search', 'library', 'liked', 'artists', 'playlists', 'jobs', 'settings'];
const MORE_VIEWS = new Set(['liked', 'jobs', 'settings']);

const saved = localStorage.getItem(LAST_VIEW_KEY) || 'search';
const initial = VALID_VIEWS.includes(saved) ? saved : 'search';

const { subscribe, set } = writable(initial);

export const currentView = { subscribe };

export function showView(name) {
  if (!VALID_VIEWS.includes(name)) return;
  localStorage.setItem(LAST_VIEW_KEY, name);
  set(name);
}

export function isMoreView(name) {
  return MORE_VIEWS.has(name);
}
