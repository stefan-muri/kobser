import { writable } from 'svelte/store';

export const playlistPickerState = writable({ open: false, trackIds: [], onDone: null });

export function showPlaylistPicker(trackIds, onDone = null) {
  playlistPickerState.set({ open: true, trackIds, onDone });
}

export function closePlaylistPicker() {
  playlistPickerState.update(s => ({ ...s, open: false }));
}
