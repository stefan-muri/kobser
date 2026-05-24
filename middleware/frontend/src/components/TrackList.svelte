<script>
  import { afterUpdate } from 'svelte';
  import {
    currentTrack, playing,
    playQueue, addToQueue, togglePlayPause,
    trackArtUrl,
  } from '../lib/stores/playerStore.js';
  import { library as libApi, deleteTrack as apiDeleteTrack, coverArtUrl } from '../lib/api.js';
  import { fmtDuration } from '../lib/util.js';
  import { showContextMenu } from '../lib/stores/contextMenuStore.js';
  import { showPlaylistPicker } from '../lib/stores/playlistPickerStore.js';

  export let songs = [];
  export let allStarred = false;
  export let playlistId = null;
  export let onRefresh = null;

  let selectMode = false;
  let selected = new Set();
  let starredOverrides = {};

  // Reset selection when songs list changes
  $: songs, resetSelect();
  function resetSelect() { selectMode = false; selected = new Set(); }

  function isStarred(song) {
    return song.id in starredOverrides ? starredOverrides[song.id] : (allStarred || !!song.starred);
  }

  async function toggleStar(song, e) {
    e?.stopPropagation();
    const was = isStarred(song);
    starredOverrides = { ...starredOverrides, [song.id]: !was };
    try {
      await libApi(was ? 'unstar' : 'star', { id: song.id });
    } catch {
      starredOverrides = { ...starredOverrides, [song.id]: was };
    }
  }

  function handleRowClick(e, song, i) {
    if (selectMode) {
      toggleSelect(i);
      return;
    }
    if ($currentTrack?.id === song.id) {
      togglePlayPause();
      return;
    }
    playQueue(songs, i);
  }

  function toggleSelect(i) {
    const s = new Set(selected);
    if (s.has(i)) s.delete(i); else s.add(i);
    selected = s;
  }

  function enterSelectMode(i = null) {
    selectMode = true;
    selected = i !== null ? new Set([i]) : new Set();
  }
  function exitSelectMode() { selectMode = false; selected = new Set(); }

  function getSelectedIds() { return [...selected].map(i => songs[i].id); }

  async function bulkDelete() {
    const ids = getSelectedIds();
    if (!ids.length || !confirm(`Delete ${ids.length} track${ids.length > 1 ? 's' : ''}? This cannot be undone.`)) return;
    const indices = [...selected];
    for (const idx of indices) {
      try { await apiDeleteTrack(songs[idx].id); } catch {}
    }
    exitSelectMode();
    onRefresh?.();
  }

  async function bulkRemoveFromPlaylist() {
    const indices = [...selected].sort((a, b) => b - a);
    for (const idx of indices) {
      await libApi('updatePlaylist', { playlistId, songIndexToRemove: idx });
    }
    exitSelectMode();
    onRefresh?.();
  }

  function openDots(e, song, i) {
    e.stopPropagation();
    const starred = isStarred(song);
    showContextMenu(e.currentTarget, [
      { label: 'Play', icon: 'ph ph-play', action: () => playQueue(songs, i) },
      { label: 'Add to queue', icon: 'ph ph-plus-circle', action: () => addToQueue(song) },
      { label: 'Add to playlist', icon: 'ph ph-music-notes-plus', action: () => showPlaylistPicker([song.id]) },
      { label: starred ? 'Unlike' : 'Like', icon: starred ? 'ph-fill ph-heart' : 'ph ph-heart', action: () => toggleStar(song) },
      { label: 'Select', icon: 'ph ph-check-square', action: () => enterSelectMode(i) },
      ...(playlistId ? [{
        label: 'Remove from playlist', icon: 'ph ph-minus-circle',
        action: async () => { await libApi('updatePlaylist', { playlistId, songIndexToRemove: i }); onRefresh?.(); },
      }] : []),
      null,
      {
        label: 'Delete from library', icon: 'ph ph-trash', danger: true,
        action: async () => {
          if (!confirm(`Delete "${song.title}"? This cannot be undone.`)) return;
          try { await apiDeleteTrack(song.id); onRefresh?.(); }
          catch (err) { alert(`Delete failed: ${err.message}`); }
        },
      },
    ]);
  }

  // Marquee: check for overflow after each render
  let rowEls = [];
  afterUpdate(() => {
    rowEls.forEach(el => {
      if (!el) return;
      const span = el.querySelector('.tl-title');
      const wrap = span?.parentElement;
      if (!span || !wrap) return;
      const overflow = span.scrollWidth - wrap.clientWidth;
      if (overflow > 2) {
        wrap.style.setProperty('--marquee-px', `-${overflow}px`);
        span.classList.add('marquee-active');
      } else {
        span.classList.remove('marquee-active');
      }
    });
  });
</script>

{#if selectMode}
  <div class="flex items-center gap-2 pb-2 px-1 overflow-x-auto no-scrollbar flex-nowrap">
    <span class="text-sm text-peel-muted mr-1">{selected.size} selected</span>
    <button
      on:click={() => showPlaylistPicker(getSelectedIds(), exitSelectMode)}
      disabled={selected.size === 0}
      class="flex items-center gap-1.5 px-3 py-1.5 bg-white/10 hover:bg-white/20 rounded-lg text-xs font-medium transition-colors disabled:opacity-40"
    >
      <i class="ph ph-music-notes-plus text-sm"></i> Add to playlist
    </button>
    {#if playlistId}
      <button
        on:click={bulkRemoveFromPlaylist}
        disabled={selected.size === 0}
        class="flex items-center gap-1.5 px-3 py-1.5 bg-white/10 hover:bg-white/20 rounded-lg text-xs font-medium transition-colors disabled:opacity-40"
      >
        <i class="ph ph-minus-circle text-sm"></i> Remove selected
      </button>
    {/if}
    <button
      on:click={bulkDelete}
      disabled={selected.size === 0}
      class="flex items-center gap-1.5 px-3 py-1.5 bg-red-500/20 hover:bg-red-500/30 text-red-400 rounded-lg text-xs font-medium transition-colors disabled:opacity-40"
    >
      <i class="ph ph-trash text-sm"></i> Delete
    </button>
    <button
      on:click={exitSelectMode}
      class="ml-auto flex items-center gap-1.5 px-3 py-1.5 bg-white/5 hover:bg-white/10 rounded-lg text-xs font-medium transition-colors"
    >Cancel</button>
  </div>
{/if}

<!-- Track rows -->
<div class="flex flex-col gap-1">
  {#each songs as song, i (song.id)}
    {@const isActive = $currentTrack?.id === song.id}
    {@const starred = song.id in starredOverrides ? starredOverrides[song.id] : (allStarred || !!song.starred)}
    <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
    <div
      bind:this={rowEls[i]}
      class="group flex items-center gap-3 p-3 rounded-xl hover:bg-white/5 transition-all cursor-pointer"
      on:click={e => handleRowClick(e, song, i)}
      role="row"
    >
      <!-- Checkbox (select mode) -->
      {#if selectMode}
        <div class="flex-shrink-0 w-5 flex items-center justify-center">
          <input
            type="checkbox"
            checked={selected.has(i)}
            on:change={() => toggleSelect(i)}
            on:click|stopPropagation
            class="w-4 h-4 rounded accent-peel-accent cursor-pointer"
          >
        </div>
      {/if}

      <!-- Art / now-playing indicator -->
      <div class="relative w-12 h-12 rounded-md overflow-hidden flex-shrink-0 bg-peel-surface">
        <img src={coverArtUrl(song.coverArt, 64)} alt="" loading="lazy" class="w-full h-full object-cover">
        <!-- Hover play/pause overlay -->
        <div class="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity {isActive ? 'opacity-100' : ''}">
          <i class="ph-fill {isActive && $playing ? 'ph-pause' : 'ph-play'} text-white text-lg pointer-events-none"></i>
        </div>
        <!-- Now-playing animated bars -->
        {#if isActive}
          <div class="absolute inset-0 bg-black/60 flex items-end justify-center gap-[3px] pb-[9px] {$playing ? '' : 'opacity-60'}">
            <span class="block w-[3px] bg-peel-accent rounded-sm origin-bottom {$playing ? 'animate-bar1' : ''}" style="height:12px"></span>
            <span class="block w-[3px] bg-peel-accent rounded-sm origin-bottom {$playing ? 'animate-bar2' : ''}" style="height:12px"></span>
            <span class="block w-[3px] bg-peel-accent rounded-sm origin-bottom {$playing ? 'animate-bar3' : ''}" style="height:12px"></span>
          </div>
        {/if}
      </div>

      <!-- Title / artist -->
      <div class="flex-1 min-w-0 pr-2">
        <div class="overflow-hidden">
          <span class="tl-title font-medium whitespace-nowrap">{song.title ?? '—'}</span>
        </div>
        <p class="text-sm text-peel-muted truncate">{song.artist ?? '—'} · {fmtDuration(song.duration)}</p>
      </div>

      <!-- Star button -->
      <button
        class="w-9 h-9 rounded-full flex items-center justify-center hover:bg-white/10 transition-all opacity-100 md:opacity-0 group-hover:opacity-100 flex-shrink-0"
        title={starred ? 'Unlike' : 'Like'}
        on:click={e => toggleStar(song, e)}
      >
        <i class="{starred ? 'ph-fill ph-heart text-peel-accent' : 'ph ph-heart text-peel-muted'} text-lg pointer-events-none"></i>
      </button>

      <!-- Dots menu button -->
      <button
        class="w-9 h-9 rounded-full flex items-center justify-center hover:bg-white/10 transition-all opacity-100 md:opacity-0 group-hover:opacity-100 flex-shrink-0"
        title="More options"
        on:click={e => openDots(e, song, i)}
      >
        <i class="ph ph-dots-three-vertical text-peel-muted text-lg pointer-events-none"></i>
      </button>
    </div>
  {/each}
</div>
