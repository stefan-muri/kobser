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

  let allSongs = [];
  let filterQuery = '';
  let sortKey = 'added-desc';
  let loading = true;
  let error = '';

  $: filtered = filterQuery
    ? allSongs.filter(s =>
        (s.title || '').toLowerCase().includes(filterQuery.toLowerCase()) ||
        (s.artist || '').toLowerCase().includes(filterQuery.toLowerCase()))
    : allSongs;

  $: sorted = sortSongs(filtered, sortKey);

  function sortSongs(songs, key) {
    const s = [...songs];
    switch (key) {
      case 'title-asc':     s.sort((a, b) => (a.title || '').localeCompare(b.title || '')); break;
      case 'title-desc':    s.sort((a, b) => (b.title || '').localeCompare(a.title || '')); break;
      case 'artist-asc':    s.sort((a, b) => (a.artist || '').localeCompare(b.artist || '') || (a.title || '').localeCompare(b.title || '')); break;
      case 'duration-asc':  s.sort((a, b) => (a.duration || 0) - (b.duration || 0)); break;
      case 'duration-desc': s.sort((a, b) => (b.duration || 0) - (a.duration || 0)); break;
      case 'added-desc':    s.sort((a, b) => (b.created || '').localeCompare(a.created || '')); break;
    }
    return s;
  }

  async function load() {
    loading = true;
    error = '';
    try { allSongs = await fetchAll(); }
    catch (e) { error = e.message; }
    finally { loading = false; }
  }

  async function fetchAll() {
    const all = [];
    const pageSize = 500;
    let offset = 0;
    while (true) {
      const r = await libApi('search3', { query: ' ', songCount: pageSize, songOffset: offset, artistCount: 0, albumCount: 0 });
      const batch = r.searchResult3?.song || [];
      all.push(...batch);
      if (batch.length < pageSize) break;
      offset += pageSize;
    }
    return all;
  }

  load();

  function setSortKey(key) { sortKey = key; }
  function toggleSort(keys) {
    const idx = keys.indexOf(sortKey);
    sortKey = idx === -1 ? keys[0] : keys[(idx + 1) % keys.length];
  }
</script>

<div class="w-full max-w-5xl mx-auto p-4 md:p-8">
  <div class="flex items-center justify-between mb-4 flex-wrap gap-3">
    <h2 class="font-london text-3xl pl-1">Library</h2>
    {#if sorted.length}
      <div class="flex items-center gap-2">
        <button on:click={() => playQueue(sorted, 0)} class="flex items-center gap-1.5 px-4 py-2 bg-peel-accent hover:bg-peel-accentHover text-peel-bg text-sm font-semibold rounded-xl transition-colors">
          <i class="ph-fill ph-play text-sm"></i> Play all
        </button>
        <button on:click={() => playShuffled(sorted)} class="flex items-center gap-1.5 px-4 py-2 bg-white/5 hover:bg-white/10 text-sm font-medium rounded-xl transition-colors">
          <i class="ph ph-shuffle text-sm"></i> Shuffle
        </button>
      </div>
    {/if}
  </div>

  <div class="flex items-center gap-2 mb-4 flex-wrap">
    <div class="relative flex-1 min-w-48">
      <i class="ph ph-magnifying-glass absolute left-4 top-1/2 -translate-y-1/2 text-peel-muted"></i>
      <input
        type="text"
        bind:value={filterQuery}
        placeholder="Search library…"
        class="w-full bg-peel-surface text-peel-text placeholder-peel-muted rounded-xl py-2.5 pl-11 pr-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10 text-sm"
      >
    </div>
    <div class="flex items-center gap-2 overflow-x-auto no-scrollbar flex-shrink-0 pb-1 -mb-1">
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
  </div>

  {#if loading}
    <div class="flex items-center justify-center py-20 opacity-50">
      <i class="ph ph-circle-notch text-4xl animate-spin-slow"></i>
    </div>
  {:else if error}
    <div class="flex flex-col items-center justify-center py-20 text-red-400">
      <i class="ph ph-warning-circle text-5xl mb-4"></i>
      <p>{error}</p>
    </div>
  {:else if !allSongs.length}
    <div class="flex flex-col items-center justify-center py-20 opacity-50">
      <i class="ph ph-music-notes text-6xl mb-4"></i>
      <p class="text-lg">Your library is empty.</p>
      <p class="text-sm mt-1">Search YouTube and download tracks to get started.</p>
    </div>
  {:else}
    <TrackList songs={sorted} onRefresh={load} />
  {/if}
</div>
