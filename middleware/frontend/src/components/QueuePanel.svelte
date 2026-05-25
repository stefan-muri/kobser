<script>
  import { queue, currentIndex, currentTrack, trackArtUrl, removeFromQueue, reorderQueue, clearUpcoming } from '../lib/stores/playerStore.js';
  import { queuePanelOpen } from '../lib/stores/uiStore.js';

  $: current = $queue[$currentIndex] ?? null;
  $: upcoming = $queue.slice($currentIndex + 1);

  let dragSrcIdx = null;
  let dragOverIdx = null;

  function onDragStart(e, qIdx) {
    dragSrcIdx = qIdx;
    e.dataTransfer.effectAllowed = 'move';
    requestAnimationFrame(() => { if (e.target) e.target.style.opacity = '0.4'; });
  }

  function onDragEnd(e) {
    dragSrcIdx = null;
    dragOverIdx = null;
    if (e.target) e.target.style.opacity = '';
  }

  function onDragOver(e, qIdx) {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    dragOverIdx = qIdx;
  }

  function onDrop(e, qIdx) {
    e.preventDefault();
    dragOverIdx = null;
    if (dragSrcIdx === null || dragSrcIdx === qIdx) return;
    reorderQueue(dragSrcIdx, qIdx);
    dragSrcIdx = null;
  }
</script>

<!-- Backdrop -->
{#if $queuePanelOpen}
  <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
  <div class="fixed inset-0 z-[155] bg-black/40" on:click={() => queuePanelOpen.set(false)}></div>
{/if}

<!-- Panel -->
<div
  class="fixed inset-y-0 right-0 w-full sm:w-96 z-[160] flex flex-col bg-kobser-surface border-l border-white/10 shadow-2xl transition-transform duration-300 ease-in-out"
  style:transform={$queuePanelOpen ? 'translateX(0)' : 'translateX(100%)'}
>
  <div class="flex items-center justify-between px-5 py-4 border-b border-white/10 flex-shrink-0">
    <h2 class="text-base font-semibold">Queue</h2>
    <button
      on:click={() => queuePanelOpen.set(false)}
      class="w-9 h-9 flex items-center justify-center text-kobser-muted hover:text-white transition-colors rounded-full hover:bg-white/10"
    >
      <i class="ph ph-x text-lg"></i>
    </button>
  </div>

  <div class="flex-1 overflow-y-auto pb-6">
    {#if current}
      <div class="px-4 pt-5 pb-3">
        <p class="text-[10px] font-semibold text-kobser-muted uppercase tracking-widest mb-3">Now Playing</p>
        <div class="flex items-center gap-3 p-2 rounded-xl bg-white/5">
          <img src={trackArtUrl(current, 64)} alt="" class="w-10 h-10 rounded-md object-cover flex-shrink-0 bg-kobser-bg">
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium truncate text-kobser-accent">{current.title ?? '—'}</p>
            <p class="text-xs text-kobser-muted truncate">{current.artist ?? '—'}</p>
          </div>
        </div>
      </div>
    {/if}

    {#if upcoming.length}
      <div class="px-4 pb-4">
        <div class="flex items-center justify-between mb-3">
          <p class="text-[10px] font-semibold text-kobser-muted uppercase tracking-widest">Up Next</p>
          <button on:click={clearUpcoming} class="text-xs text-kobser-muted hover:text-red-400 transition-colors">Clear</button>
        </div>
        <div class="flex flex-col gap-0.5">
          {#each upcoming as track, i}
            {@const qIdx = $currentIndex + 1 + i}
            <!-- svelte-ignore a11y-click-events-have-key-events -->
            <div
              class="flex items-center gap-2 p-2 rounded-xl hover:bg-white/5 group cursor-pointer transition-colors {dragOverIdx === qIdx && dragSrcIdx !== qIdx ? 'border-t-2 border-kobser-accent' : ''}"
              draggable="true"
              on:click={() => { removeFromQueue(qIdx); }}
              on:dragstart={e => onDragStart(e, qIdx)}
              on:dragend={onDragEnd}
              on:dragover={e => onDragOver(e, qIdx)}
              on:dragleave={() => { dragOverIdx = null; }}
              on:drop={e => onDrop(e, qIdx)}
              role="listitem"
            >
              <i class="ph ph-dots-six-vertical text-kobser-muted text-lg flex-shrink-0 cursor-grab opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none"></i>
              <img src={trackArtUrl(track, 64)} alt="" class="w-10 h-10 rounded-md object-cover flex-shrink-0 bg-kobser-bg pointer-events-none">
              <div class="flex-1 min-w-0 pointer-events-none">
                <p class="text-sm font-medium truncate">{track.title ?? '—'}</p>
                <p class="text-xs text-kobser-muted truncate">{track.artist ?? '—'}</p>
              </div>
              <button
                class="w-8 h-8 flex items-center justify-center text-kobser-muted hover:text-white rounded-full hover:bg-white/10 transition-all opacity-0 group-hover:opacity-100 flex-shrink-0"
                on:click|stopPropagation={() => removeFromQueue(qIdx)}
              >
                <i class="ph ph-x text-sm pointer-events-none"></i>
              </button>
            </div>
          {/each}
        </div>
      </div>
    {:else if !current}
      <div class="flex flex-col items-center justify-center h-full gap-3 opacity-40 p-8 text-center">
        <i class="ph ph-list text-5xl"></i>
        <p class="text-sm">Queue is empty</p>
      </div>
    {/if}
  </div>
</div>
