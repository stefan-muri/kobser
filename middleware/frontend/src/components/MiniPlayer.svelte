<script>
  import {
    currentTrack, playing, shuffleOn, playbackProgress,
    next, prev, togglePlayPause, toggleShuffle, toggleLike, seekFraction, closePlayer,
    trackArtUrl, isPreview,
  } from '../lib/stores/playerStore.js';
  import { expandedPlayerOpen, queuePanelOpen } from '../lib/stores/uiStore.js';
  import { fmtTime } from '../lib/util.js';

  $: track = $currentTrack;
  $: visible = !!track;
  $: liked = !!track?.starred;
  $: preview = track ? isPreview(track) : false;
  $: progress = $playbackProgress.duration
    ? ($playbackProgress.currentTime / $playbackProgress.duration) * 100
    : 0;

  function handleProgressClick(e) {
    const rect = e.currentTarget.getBoundingClientRect();
    seekFraction((e.clientX - rect.left) / rect.width);
  }
</script>

<div
  id="mini-player"
  class="fixed bottom-0 w-full h-[90px] bg-peel-surface border-t border-white/5 z-50 items-center px-4 md:px-6 shadow-[0_-10px_30px_rgba(0,0,0,0.5)] user-select-none {visible ? 'flex' : 'hidden'}"
>
  <!-- Progress Bar -->
  <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
  <div class="absolute top-0 left-0 w-full h-1 bg-peel-bg group cursor-pointer" on:click={handleProgressClick}>
    <div class="h-full bg-peel-accent group-hover:bg-peel-accentHover transition-colors relative" style:width="{progress}%">
      <div class="absolute right-0 top-1/2 -translate-y-1/2 w-3 h-3 bg-white rounded-full opacity-0 group-hover:opacity-100 shadow-md"></div>
    </div>
  </div>

  <!-- Left: Track info (click to expand) -->
  <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
  <div class="flex items-center gap-3 flex-1 min-w-0 mr-4 cursor-pointer" on:click={() => expandedPlayerOpen.set(true)}>
    {#if track}
      <img src={trackArtUrl(track, 96)} alt="" class="w-12 h-12 md:w-14 md:h-14 rounded-md object-cover shadow-md flex-shrink-0 hidden sm:block">
    {/if}
    <div class="min-w-0">
      <h4 class="text-sm font-semibold text-white truncate">{track?.title ?? 'Not Playing'}</h4>
      <p class="text-xs text-peel-muted truncate">{track?.artist ?? 'Select a track'}</p>
    </div>
  </div>

  <!-- Center: Controls -->
  <div class="flex items-center gap-4 flex-shrink-0">
    <button on:click={prev} class="text-peel-muted hover:text-white transition-colors">
      <i class="ph-fill ph-skip-back text-2xl"></i>
    </button>
    <button
      on:click={togglePlayPause}
      class="w-11 h-11 md:w-12 md:h-12 rounded-full bg-white text-peel-bg flex items-center justify-center hover:scale-105 transition-transform"
    >
      <i class="ph-fill {$playing ? 'ph-pause' : 'ph-play'} text-xl md:text-2xl"></i>
    </button>
    <button on:click={next} class="text-peel-muted hover:text-white transition-colors">
      <i class="ph-fill ph-skip-forward text-2xl"></i>
    </button>
  </div>

  <!-- Right: Controls + Time + Close -->
  <div class="flex items-center justify-end gap-3 md:w-52">
    {#if !preview}
      <button
        on:click={toggleLike}
        class="hidden md:flex transition-colors {liked ? 'text-peel-accent' : 'text-peel-muted hover:text-peel-accent'}"
        title="Like"
      >
        <i class="{liked ? 'ph-fill' : 'ph'} ph-heart text-xl"></i>
      </button>
    {/if}
    <button
      on:click={toggleShuffle}
      class="hidden md:flex transition-colors {$shuffleOn ? 'text-peel-accent' : 'text-peel-muted hover:text-white'}"
      title="Shuffle"
    >
      <i class="ph ph-shuffle text-xl"></i>
    </button>
    <button
      on:click={() => queuePanelOpen.set(true)}
      class="hidden md:flex text-peel-muted hover:text-white transition-colors"
      title="Queue"
    >
      <i class="ph ph-list text-xl"></i>
    </button>
    <span class="hidden md:inline text-sm text-peel-muted tabular-nums">
      {fmtTime($playbackProgress.currentTime)} / {fmtTime($playbackProgress.duration)}
    </span>
    <button on:click={closePlayer} class="text-peel-muted hover:text-white transition-colors flex-shrink-0" title="Close player">
      <i class="ph ph-x text-xl"></i>
    </button>
  </div>
</div>
