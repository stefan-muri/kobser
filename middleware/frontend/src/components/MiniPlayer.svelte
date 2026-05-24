<script>
  import {
    currentTrack, playing, shuffleOn, repeatMode, playbackProgress,
    next, prev, togglePlayPause, toggleShuffle, toggleRepeat, toggleLike, seekFraction, closePlayer,
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
  class="fixed bottom-0 w-full h-[90px] bg-peel-surface border-t border-white/5 z-50 items-center px-4 md:px-6 gap-4 shadow-[0_-10px_30px_rgba(0,0,0,0.5)] user-select-none {visible ? 'flex' : 'hidden'}"
>
  <!-- Progress bar -->
  <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
  <div class="absolute top-0 left-0 w-full h-1 bg-peel-bg group cursor-pointer" on:click={handleProgressClick}>
    <div class="h-full bg-peel-accent group-hover:bg-peel-accentHover transition-colors relative" style:width="{progress}%">
      <div class="absolute right-0 top-1/2 -translate-y-1/2 w-3 h-3 bg-white rounded-full opacity-0 group-hover:opacity-100 shadow-md"></div>
    </div>
  </div>

  <!-- Vinyl record + tonearm -->
  <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
  <div class="vinyl-wrap flex-shrink-0 relative cursor-pointer" on:click={() => expandedPlayerOpen.set(true)}>
    <!-- Disc -->
    <div class="vinyl-disc" class:spinning={$playing}>
      <!-- Album art label -->
      <div class="vinyl-label">
        {#if track}<img src={trackArtUrl(track, 96)} alt="" class="w-full h-full object-cover">{/if}
      </div>
      <!-- Spindle hole -->
      <div class="vinyl-hole"></div>
    </div>
    <!-- Tonearm -->
    <div
      class="tonearm"
      style="transform-origin: top center; transition: transform 0.4s cubic-bezier(0.34,1.56,0.64,1); transform: {$playing ? 'rotate(24deg)' : 'rotate(-20deg)'};"
    ></div>
  </div>

  <!-- Track info -->
  <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
  <div class="flex-1 min-w-0 cursor-pointer" on:click={() => expandedPlayerOpen.set(true)}>
    <h4 class="text-sm font-semibold text-white truncate">{track?.title ?? 'Not Playing'}</h4>
    <p class="text-xs text-peel-muted truncate">{track?.artist ?? 'Select a track'}</p>
  </div>

  <!-- Controls -->
  <div class="flex items-center gap-3 flex-shrink-0">
    <button on:click={prev} class="hidden sm:flex text-peel-muted hover:text-white transition-colors">
      <i class="ph-fill ph-skip-back text-xl"></i>
    </button>
    <button on:click={togglePlayPause} class="play-btn rounded-full flex items-center justify-center">
      <i class="ph-fill {$playing ? 'ph-pause' : 'ph-play'} text-lg"></i>
    </button>
    <button on:click={next} class="text-peel-muted hover:text-white transition-colors">
      <i class="ph-fill ph-skip-forward text-xl"></i>
    </button>
    {#if !preview}
      <button on:click={toggleLike}
        class="hidden md:flex transition-colors {liked ? 'text-peel-accent' : 'text-peel-muted hover:text-peel-accent'}">
        <i class="{liked ? 'ph-fill' : 'ph'} ph-heart text-xl"></i>
      </button>
    {/if}
    <button on:click={toggleShuffle}
      class="hidden md:flex transition-colors {$shuffleOn ? 'text-peel-accent' : 'text-peel-muted hover:text-white'}">
      <i class="ph ph-shuffle text-xl"></i>
    </button>
    <button on:click={toggleRepeat}
      class="hidden md:flex transition-colors {$repeatMode !== 'off' ? 'text-peel-accent' : 'text-peel-muted hover:text-white'}">
      <i class="ph {$repeatMode === 'one' ? 'ph-repeat-once' : 'ph-repeat'} text-xl"></i>
    </button>
    <button on:click={() => queuePanelOpen.set(true)}
      class="hidden md:flex text-peel-muted hover:text-white transition-colors">
      <i class="ph ph-list text-xl"></i>
    </button>
    <span class="hidden md:inline text-sm text-peel-muted tabular-nums">
      {fmtTime($playbackProgress.currentTime)} / {fmtTime($playbackProgress.duration)}
    </span>
    <button on:click={closePlayer} class="text-peel-muted hover:text-white transition-colors flex-shrink-0">
      <i class="ph ph-x text-xl"></i>
    </button>
  </div>
</div>

<style>
  /* ── Vinyl wrap ───────────────────────────────── */
  .vinyl-wrap {
    width: 80px;
    height: 68px;
  }

  /* ── Record disc ──────────────────────────────── */
  .vinyl-disc {
    position: absolute;
    left: 0;
    top: 0;
    width: 68px;
    height: 68px;
    border-radius: 50%;
    background: radial-gradient(circle,
      #1c1c1f 0% 29%,
      #4b4b52 29% 31%,
      #2c2c31 31% 39%,
      #4b4b52 39% 41%,
      #2c2c31 41% 48%,
      #4b4b52 48% 50%,
      #2c2c31 50% 100%
    );
    box-shadow:
      0 4px 20px rgba(0, 0, 0, 0.85),
      0 0 0 2px rgba(255, 255, 255, 0.18);
  }

  .vinyl-disc.spinning {
    animation: vinyl-spin 3s linear infinite;
  }

  @keyframes vinyl-spin {
    to { transform: rotate(360deg); }
  }

  /* ── Album art label ──────────────────────────── */
  .vinyl-label {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 36px;
    height: 36px;
    border-radius: 50%;
    overflow: hidden;
    background: #09090b;
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1);
  }

  /* ── Spindle hole ─────────────────────────────── */
  .vinyl-hole {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #09090b;
    box-shadow: inset 0 1px 3px rgba(0, 0, 0, 1);
    z-index: 1;
  }

  /* ── Tonearm ──────────────────────────────────── */
  .tonearm {
    position: absolute;
    top: 1px;
    right: 1px;
    width: 3px;
    height: 34px;
    border-radius: 2px;
    background: linear-gradient(to bottom, #a1a1aa 0%, #71717a 65%, #f59e0b 82%, #d97706 100%);
  }

  /* Pivot head */
  .tonearm::before {
    content: '';
    position: absolute;
    top: -5px;
    left: 50%;
    transform: translateX(-50%);
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: radial-gradient(circle, #d4d4d8 0%, #71717a 100%);
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.12), 0 2px 4px rgba(0, 0, 0, 0.6);
  }

  /* ── Play / Pause button ──────────────────────── */
  .play-btn {
    width: 40px;
    height: 40px;
    border: 2px solid #f59e0b;
    color: #f59e0b;
    transition: background-color 0.15s ease, color 0.15s ease;
  }

  .play-btn:hover {
    background-color: #f59e0b;
    color: #09090b;
  }
</style>
