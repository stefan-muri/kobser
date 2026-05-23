import { writable, derived, get } from 'svelte/store';
import { streamUrl, coverArtUrl, library as libApi } from '../api.js';

const LAST_TRACK_KEY = 'peel:last-track';

export const queue = writable([]);
export const currentIndex = writable(-1);
export const playing = writable(false);
export const shuffleOn = writable(false);
export const playbackProgress = writable({ currentTime: 0, duration: 0 });

// Derived: current track
export const currentTrack = derived(
  [queue, currentIndex],
  ([$queue, $index]) => ($index >= 0 ? $queue[$index] ?? null : null),
);

let _audio = null;
let _shuffledQueue = [];
let _shufflePos = -1;

export function isPreview(track) {
  return !!track?._previewUrl;
}

export function trackArtUrl(track, size) {
  if (!track) return '';
  if (isPreview(track)) return track._previewThumb || '';
  return coverArtUrl(track.coverArt, size);
}

export function initAudio(audioEl) {
  _audio = audioEl;

  _audio.addEventListener('play', () => playing.set(true));
  _audio.addEventListener('pause', () => {
    playing.set(false);
    saveLastTrack();
  });
  _audio.addEventListener('ended', () => next());
  _audio.addEventListener('timeupdate', () => {
    playbackProgress.set({
      currentTime: _audio.currentTime,
      duration: _audio.duration || 0,
    });
  });

  window.addEventListener('beforeunload', saveLastTrack);
  restoreLastTrack();
}

function _load() {
  const $queue = get(queue);
  const $index = get(currentIndex);
  const track = $queue[$index];
  if (!track || !_audio) return;
  _audio.src = isPreview(track) ? track._previewUrl : streamUrl(track.id);
  _audio.play().catch(e => console.error('play failed:', e));
  saveLastTrack();
}

export function playQueue(tracks, startIndex = 0) {
  queue.set(tracks);
  currentIndex.set(startIndex);
  if (get(shuffleOn)) _buildShuffleOrder(startIndex, tracks.length);
  _load();
}

export function playPreviewTrack(track) {
  queue.set([track]);
  currentIndex.set(0);
  _load();
}

export function playShuffled(tracks) {
  if (!tracks.length) return;
  const shuffled = [...tracks];
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  playQueue(shuffled, 0);
}

export function next() {
  if (get(shuffleOn) && _shuffledQueue.length) {
    if (_shufflePos < _shuffledQueue.length - 1) {
      _shufflePos++;
      currentIndex.set(_shuffledQueue[_shufflePos]);
      _load();
    }
  } else {
    const $index = get(currentIndex);
    if ($index < get(queue).length - 1) {
      currentIndex.update(i => i + 1);
      _load();
    }
  }
}

export function prev() {
  if (_audio && _audio.currentTime > 3) {
    _audio.currentTime = 0;
    return;
  }
  if (get(shuffleOn) && _shuffledQueue.length) {
    if (_shufflePos > 0) {
      _shufflePos--;
      currentIndex.set(_shuffledQueue[_shufflePos]);
      _load();
    }
  } else {
    const $index = get(currentIndex);
    if ($index > 0) {
      currentIndex.update(i => i - 1);
      _load();
    }
  }
}

export function togglePlayPause() {
  if (!_audio) return;
  _audio.paused ? _audio.play() : _audio.pause();
}

export function seekFraction(fraction) {
  if (_audio && _audio.duration) {
    _audio.currentTime = fraction * _audio.duration;
  }
}

export function toggleShuffle() {
  const on = !get(shuffleOn);
  shuffleOn.set(on);
  if (on) {
    const $q = get(queue);
    _buildShuffleOrder(get(currentIndex), $q.length);
  }
}

export async function toggleLike() {
  const track = get(currentTrack);
  if (!track || isPreview(track)) return;
  const isLiked = !!track.starred;
  try {
    await libApi(isLiked ? 'unstar' : 'star', { id: track.id });
    const $index = get(currentIndex);
    queue.update($q => {
      const updated = [...$q];
      updated[$index] = {
        ...updated[$index],
        starred: isLiked ? undefined : new Date().toISOString(),
      };
      return updated;
    });
  } catch (e) {
    console.error('like toggle failed:', e);
  }
}

export function addToQueue(track) {
  if (!track) return;
  const $q = get(queue);
  if ($q.length === 0 || get(currentIndex) === -1) {
    playQueue([track], 0);
    return;
  }
  queue.update(q => [...q, track]);
}

export function removeFromQueue(qIdx) {
  const $index = get(currentIndex);
  if (qIdx === $index) return;
  queue.update(q => {
    const updated = [...q];
    updated.splice(qIdx, 1);
    return updated;
  });
  if (qIdx < $index) currentIndex.update(i => i - 1);
}

export function reorderQueue(fromIdx, toIdx) {
  queue.update($q => {
    const updated = [...$q];
    const [item] = updated.splice(fromIdx, 1);
    const insertAt = fromIdx < toIdx ? toIdx - 1 : toIdx;
    updated.splice(insertAt, 0, item);
    return updated;
  });
}

export function clearUpcoming() {
  queue.update($q => $q.slice(0, get(currentIndex) + 1));
}

export function saveLastTrack() {
  const track = get(currentTrack);
  if (!track || isPreview(track)) {
    localStorage.removeItem(LAST_TRACK_KEY);
    return;
  }
  try {
    localStorage.setItem(
      LAST_TRACK_KEY,
      JSON.stringify({ track, currentTime: _audio?.currentTime ?? 0 }),
    );
  } catch {}
}

export function closePlayer() {
  if (_audio) _audio.pause();
  localStorage.removeItem(LAST_TRACK_KEY);
  queue.set([]);
  currentIndex.set(-1);
  playbackProgress.set({ currentTime: 0, duration: 0 });
}

function restoreLastTrack() {
  try {
    const saved = localStorage.getItem(LAST_TRACK_KEY);
    if (!saved) return;
    const { track, currentTime } = JSON.parse(saved);
    if (!track?.id) return;
    queue.set([track]);
    currentIndex.set(0);
    if (_audio) {
      _audio.src = streamUrl(track.id);
      if (currentTime > 0) {
        _audio.addEventListener(
          'loadedmetadata',
          () => {
            _audio.currentTime = Math.min(currentTime, (_audio.duration || currentTime) - 1);
          },
          { once: true },
        );
      }
    }
  } catch {
    localStorage.removeItem(LAST_TRACK_KEY);
  }
}

function _buildShuffleOrder(startIdx, length) {
  const indices = Array.from({ length }, (_, i) => i).filter(i => i !== startIdx);
  for (let i = indices.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [indices[i], indices[j]] = [indices[j], indices[i]];
  }
  _shuffledQueue = [startIdx, ...indices];
  _shufflePos = 0;
}
