<script>
  import { library as libApi } from '../lib/api.js';
  import { playQueue, playShuffled } from '../lib/stores/playerStore.js';
  import TrackList from '../components/TrackList.svelte';

  let songs = [];
  let filterQuery = '';
  let loading = true;
  let error = '';

  $: filtered = filterQuery
    ? songs.filter(s =>
        (s.title || '').toLowerCase().includes(filterQuery.toLowerCase()) ||
        (s.artist || '').toLowerCase().includes(filterQuery.toLowerCase()))
    : songs;

  async function load() {
    loading = true;
    error = '';
    try {
      const r = await libApi('getStarred2');
      songs = r.starred2?.song || [];
    } catch (e) {
      error = e.message;
    } finally {
      loading = false;
    }
  }

  load();
</script>

<div class="w-full max-w-5xl mx-auto p-4 md:p-8">
  <div class="flex items-center justify-between mb-6 flex-wrap gap-3">
    <h2 class="font-london text-3xl pl-1">Favorites</h2>
    {#if songs.length}
      <div class="flex items-center gap-2">
        <button on:click={() => playQueue(filtered, 0)} class="flex items-center gap-1.5 px-4 py-2 bg-peel-accent hover:bg-peel-accentHover text-peel-bg text-sm font-semibold rounded-xl transition-colors">
          <i class="ph-fill ph-play text-sm"></i> Play all
        </button>
        <button on:click={() => playShuffled(filtered)} class="flex items-center gap-1.5 px-4 py-2 bg-white/5 hover:bg-white/10 text-sm font-medium rounded-xl transition-colors">
          <i class="ph ph-shuffle text-sm"></i> Shuffle
        </button>
      </div>
    {/if}
  </div>

  {#if songs.length}
    <div class="mb-4">
      <div class="relative">
        <i class="ph ph-magnifying-glass absolute left-4 top-1/2 -translate-y-1/2 text-peel-muted"></i>
        <input
          type="text"
          bind:value={filterQuery}
          placeholder="Search favorites…"
          class="w-full bg-peel-surface text-peel-text placeholder-peel-muted rounded-xl py-2.5 pl-11 pr-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10 text-sm"
        >
      </div>
    </div>
  {/if}

  {#if loading}
    <div class="flex items-center justify-center py-20 opacity-50">
      <i class="ph ph-circle-notch text-4xl animate-spin-slow"></i>
    </div>
  {:else if error}
    <div class="flex flex-col items-center justify-center py-20 text-red-400">
      <i class="ph ph-warning-circle text-5xl mb-4"></i>
      <p>{error}</p>
    </div>
  {:else if !songs.length}
    <div class="flex flex-col items-center justify-center py-20 opacity-50">
      <i class="ph ph-heart text-6xl mb-4"></i>
      <p class="text-lg">No favorites yet.</p>
      <p class="text-sm mt-1">Heart a track in Library to add it here.</p>
    </div>
  {:else}
    <TrackList songs={filtered} allStarred={true} onRefresh={load} />
  {/if}
</div>
