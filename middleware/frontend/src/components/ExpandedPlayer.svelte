<script>
  import {
    currentTrack, playing, shuffleOn, repeatMode, playbackProgress,
    next, prev, togglePlayPause, toggleShuffle, toggleRepeat, toggleLike, seekFraction,
    trackArtUrl, isPreview,
  } from '../lib/stores/playerStore.js';
  import { expandedPlayerOpen, queuePanelOpen } from '../lib/stores/uiStore.js';
  import { showContextMenu } from '../lib/stores/contextMenuStore.js';
  import { showPlaylistPicker } from '../lib/stores/playlistPickerStore.js';
  import { deleteTrack } from '../lib/api.js';
  import { fmtTime } from '../lib/util.js';
  import Marquee from './Marquee.svelte';

  $: track = $currentTrack;
  $: liked = !!track?.starred;
  $: preview = track ? isPreview(track) : false;
  $: artUrl = track ? trackArtUrl(track, 512) : '';
  $: progress = $playbackProgress.duration
    ? ($playbackProgress.currentTime / $playbackProgress.duration) * 100
    : 0;

  function handleProgressClick(e) {
    const rect = e.currentTarget.getBoundingClientRect();
    seekFraction((e.clientX - rect.left) / rect.width);
  }

  let dotsBtn;
  function openDots() {
    if (!track || preview) return;
    showContextMenu(dotsBtn, [
      { label: 'Add to playlist', icon: 'ph ph-music-notes-plus', action: () => showPlaylistPicker([track.id]) },
      { label: liked ? 'Unlike' : 'Like', icon: liked ? 'ph-fill ph-heart' : 'ph ph-heart', action: () => toggleLike() },
      { label: 'View queue', icon: 'ph ph-list', action: () => queuePanelOpen.set(true) },
      null,
      {
        label: 'Delete from library', icon: 'ph ph-trash', danger: true,
        action: async () => {
          if (!confirm(`Delete "${track.title}"? This cannot be undone.`)) return;
          try { await deleteTrack(track.id); expandedPlayerOpen.set(false); }
          catch (err) { alert(`Delete failed: ${err.message}`); }
        },
      },
    ]);
  }
</script>

<div
  class="fixed inset-0 z-[150] flex flex-col pointer-events-none transition-transform duration-300 ease-in-out"
  style:transform={$expandedPlayerOpen ? 'translateY(0)' : 'translateY(100%)'}
  style:pointer-events={$expandedPlayerOpen ? 'auto' : 'none'}
>
  <!-- Blurred background -->
  <div class="absolute inset-0 bg-peel-bg bg-cover bg-center scale-110"
       style:background-image={artUrl ? `url('${artUrl}')` : ''}
       style="filter: blur(48px) brightness(0.25)"></div>
  <div class="absolute inset-0 bg-black/50"></div>

  <!-- Content -->
  <div class="relative flex flex-col h-full w-full max-w-lg mx-auto px-8" style="padding-top: env(safe-area-inset-top, 0px)">
    <!-- Top bar -->
    <div class="flex items-center justify-between py-5 flex-shrink-0">
      <button
        on:click={() => expandedPlayerOpen.set(false)}
        class="w-10 h-10 flex items-center justify-center text-peel-muted hover:text-white transition-colors rounded-full hover:bg-white/10"
      >
        <i class="ph ph-caret-down text-2xl"></i>
      </button>
      <p class="text-sm font-semibold tracking-wide text-peel-muted uppercase">Now Playing</p>
      <button
        bind:this={dotsBtn}
        on:click={openDots}
        class="w-10 h-10 flex items-center justify-center text-peel-muted hover:text-white transition-colors rounded-full hover:bg-white/10"
        style:visibility={preview ? 'hidden' : 'visible'}
      >
        <i class="ph ph-dots-three-vertical text-2xl"></i>
      </button>
    </div>

    <!-- Album art -->
    <div class="flex-1 flex items-center justify-center py-4 min-h-0">
      {#if track}
        <img src={artUrl} alt="" class="w-full max-w-xs aspect-square rounded-2xl shadow-[0_20px_60px_rgba(0,0,0,0.6)] object-cover">
      {/if}
    </div>

    <!-- Track info + like -->
    <div class="flex items-center gap-4 mt-6 mb-5 flex-shrink-0">
      <div class="flex-1 min-w-0">
        <Marquee text={track?.title ?? '—'} cls="text-2xl font-bold" />
        <div class="mt-1">
          <Marquee text={track?.artist ?? '—'} cls="text-peel-muted" />
        </div>
      </div>
      {#if !preview}
        <button
          on:click={toggleLike}
          class="w-11 h-11 flex items-center justify-center rounded-full hover:bg-white/10 transition-colors flex-shrink-0 {liked ? 'text-peel-accent' : 'text-peel-muted'}"
        >
          <i class="{liked ? 'ph-fill' : 'ph'} ph-heart text-2xl"></i>
        </button>
      {/if}
    </div>

    <!-- Progress -->
    <div class="flex-shrink-0 mb-1">
      <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
      <div
        class="w-full h-1.5 bg-white/15 rounded-full cursor-pointer group mb-2"
        on:click={handleProgressClick}
      >
        <div
          class="h-full bg-peel-accent rounded-full relative group-hover:bg-peel-accentHover transition-colors"
          style:width="{progress}%"
        >
          <div class="absolute right-0 top-1/2 -translate-y-1/2 -mr-2 w-4 h-4 bg-white rounded-full shadow opacity-0 group-hover:opacity-100 transition-opacity"></div>
        </div>
      </div>
      <div class="flex justify-between text-xs text-peel-muted">
        <span>{fmtTime($playbackProgress.currentTime)}</span>
        <span>{fmtTime($playbackProgress.duration)}</span>
      </div>
    </div>

    <!-- Controls -->
    <div class="flex items-center justify-between flex-shrink-0 mt-6 mb-10">
      <button
        on:click={toggleShuffle}
        class="w-11 h-11 flex items-center justify-center transition-colors rounded-full hover:bg-white/10 {$shuffleOn ? 'text-peel-accent' : 'text-peel-muted hover:text-white'}"
      >
        <i class="ph ph-shuffle text-2xl"></i>
      </button>
      <button on:click={prev} class="w-11 h-11 flex items-center justify-center text-peel-muted hover:text-white transition-colors">
        <i class="ph-fill ph-skip-back text-3xl"></i>
      </button>
      <button
        on:click={togglePlayPause}
        class="w-16 h-16 rounded-full bg-white text-peel-bg flex items-center justify-center hover:scale-105 transition-transform shadow-xl"
      >
        <i class="ph-fill {$playing ? 'ph-pause' : 'ph-play'} text-2xl"></i>
      </button>
      <button on:click={next} class="w-11 h-11 flex items-center justify-center text-peel-muted hover:text-white transition-colors">
        <i class="ph-fill ph-skip-forward text-3xl"></i>
      </button>
      <button
        on:click={toggleRepeat}
        class="w-11 h-11 flex items-center justify-center transition-colors rounded-full hover:bg-white/10 {$repeatMode !== 'off' ? 'text-peel-accent' : 'text-peel-muted hover:text-white'}"
      >
        <i class="ph {$repeatMode === 'one' ? 'ph-repeat-once' : 'ph-repeat'} text-2xl"></i>
      </button>
    </div>
  </div>
</div>
