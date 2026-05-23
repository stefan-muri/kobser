<script>
  import { library as libApi } from '../lib/api.js';
  import { playQueue, playShuffled } from '../lib/stores/playerStore.js';
  import TrackList from '../components/TrackList.svelte';

  const SORT_OPTIONS = [
    { key: 'added-desc', label: 'Recently added' },
    { key: 'artist-asc', label: 'Artist' },
  ];
  const SORT_TOGGLES = [
    { keys: ['title-asc', 'title-desc'],       labels: ['A → Z', 'Z → A'] },
    { keys: ['duration-asc', 'duration-desc'], labels: ['Shortest', 'Longest'] },
  ];

  let songs = [];
  let filterQuery = '';
  let sortKey = 'added-desc';
  let loading = true;
  let error = '';

  $: filtered = filterQuery
    ? songs.filter(s =>
        (s.title || '').toLowerCase().includes(filterQuery.toLowerCase()) ||
        (s.artist || '').toLowerCase().includes(filterQuery.toLowerCase()))
    : songs;

  $: sorted = sortSongs(filtered, sortKey);

  function sortSongs(list, key) {
    const s = [...list];
    switch (key) {
      case 'title-asc':     s.sort((a, b) => (a.title || '').localeCompare(b.title || '')); break;
      case 'title-desc':    s.sort((a, b) => (b.title || '').localeCompare(a.title || '')); break;
      case 'artist-asc':    s.sort((a, b) => (a.artist || '').localeCompare(b.artist || '') || (a.title || '').localeCompare(b.title || '')); break;
      case 'duration-asc':  s.sort((a, b) => (a.duration || 0) - (b.duration || 0)); break;
      case 'duration-desc': s.sort((a, b) => (b.duration || 0) - (a.duration || 0)); break;
      case 'added-desc':    s.sort((a, b) => (b.starred || '').localeCompare(a.starred || '')); break;
    }
    return s;
  }

  function setSortKey(key) { sortKey = key; }
  function toggleSort(keys) {
    const idx = keys.indexOf(sortKey);
    sortKey = idx === -1 ? keys[0] : keys[(idx + 1) % keys.length];
  }

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
  <h2 class="font-london text-3xl pl-1 mb-4">Favorites</h2>

  {#if songs.length || loading}
    <div class="flex items-center gap-2 mb-3">
      <div class="relative flex-1 min-w-0">
        <i class="ph ph-magnifying-glass absolute left-4 top-1/2 -translate-y-1/2 text-peel-muted"></i>
        <input
          type="text"
          bind:value={filterQuery}
          placeholder="Search favorites…"
          class="w-full bg-peel-surface text-peel-text placeholder-peel-muted rounded-xl py-2.5 pl-11 pr-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10 text-sm"
        >
      </div>
      {#if sorted.length}
        <button on:click={() => playQueue(sorted, 0)} class="flex-shrink-0 flex items-center gap-1.5 px-4 py-2 bg-peel-accent hover:bg-peel-accentHover text-peel-bg text-sm font-semibold rounded-xl transition-colors">
          <i class="ph-fill ph-play text-sm"></i> Play all
        </button>
        <button on:click={() => playShuffled(sorted)} class="flex-shrink-0 flex items-center gap-1.5 px-4 py-2 bg-white/5 hover:bg-white/10 text-sm font-medium rounded-xl transition-colors">
          <i class="ph ph-shuffle text-sm"></i> Shuffle
        </button>
      {/if}
    </div>

    <div class="flex items-center gap-2 overflow-x-auto no-scrollbar pb-1 mb-3">
      {#each SORT_OPTIONS as opt}
        <button
          on:click={() => setSortKey(opt.key)}
          class="flex-shrink-0 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors {sortKey === opt.key ? 'bg-peel-accent text-peel-bg' : 'bg-white/5 text-peel-muted hover:bg-white/10 hover:text-peel-text'}"
        >{opt.label}</button>
      {/each}
      {#each SORT_TOGGLES as tog}
        {@const activeIdx = tog.keys.indexOf(sortKey)}
        {@const isActive = activeIdx !== -1}
        <button
          on:click={() => toggleSort(tog.keys)}
          class="flex-shrink-0 flex items-center px-3 py-1.5 rounded-lg text-xs font-medium transition-colors {isActive ? 'bg-peel-accent text-peel-bg' : 'bg-white/5 text-peel-muted hover:bg-white/10 hover:text-peel-text'}"
        >
          {isActive ? tog.labels[activeIdx] : tog.labels[0]}
          {#if isActive}
            <i class="ph {activeIdx === 0 ? 'ph-caret-up' : 'ph-caret-down'} text-[9px] ml-0.5"></i>
          {/if}
        </button>
      {/each}
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
    <TrackList songs={sorted} allStarred={true} onRefresh={load} />
  {/if}
</div>
