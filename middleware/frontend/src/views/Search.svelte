<script>
  import { search as searchApi, previewUrl } from '../lib/api.js';
  import { playPreviewTrack } from '../lib/stores/playerStore.js';
  import { fmtDuration } from '../lib/util.js';
  import { trackJob } from '../lib/jobs.js';
  import DownloadDialog from '../components/DownloadDialog.svelte';
  import Marquee from '../components/Marquee.svelte';

  const HISTORY_KEY = 'kobser:search-history';
  const MAX_HISTORY = 20;

  let query = '';
  let results = [];
  let heading = 'Results';
  let loading = false;
  let error = '';
  let historyVisible = false;
  let inputEl;

  // Download dialog state
  let dlOpen = false;
  let dlVideoId = '';
  let dlArtist = '';
  let dlTitle = '';

  function getHistory() {
    try { return JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]'); } catch { return []; }
  }
  function saveQuery(q) {
    const h = getHistory().filter(x => x !== q);
    h.unshift(q);
    localStorage.setItem(HISTORY_KEY, JSON.stringify(h.slice(0, MAX_HISTORY)));
  }
  function removeQuery(q) {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(getHistory().filter(x => x !== q)));
    history = getHistory();
  }

  let history = getHistory();

  function showHistory() {
    if (!query.trim()) { history = getHistory(); historyVisible = history.length > 0; }
  }
  function hideHistory() { historyVisible = false; }

  async function go() {
    const q = query.trim();
    if (!q) return;
    hideHistory();
    saveQuery(q);
    loading = true;
    error = '';
    heading = `Searching for "${q}"`;
    try {
      results = await searchApi(q, 15);
      heading = results.length ? `Results for "${q}"` : `No results for "${q}"`;
    } catch (e) {
      error = e.message;
      heading = 'Results';
    } finally {
      loading = false;
    }
  }

  function playPreview(result) {
    playPreviewTrack({
      id: null,
      title: result.title || 'Unknown',
      artist: result.channel || 'YouTube',
      coverArt: null,
      _previewUrl: previewUrl(result.videoId),
      _previewThumb: result.thumbnail || '',
      duration: result.duration || 0,
    });
  }

  function openDownload(result) {
    const { artist, title } = parseTitle(result.title || '');
    dlVideoId = result.videoId;
    dlArtist = artist;
    dlTitle = title;
    dlOpen = true;
  }

  function parseTitle(t) {
    const cleaned = t
      .replace(/\(Official[^)]*\)/gi, '').replace(/\[Official[^\]]*\]/gi, '')
      .replace(/\(Lyrics?[^)]*\)/gi, '').replace(/\(Audio\)/gi, '')
      .replace(/\(Video\)/gi, '').replace(/\(HD\)/gi, '')
      .replace(/\(\d{4}\)/g, '').replace(/\s+/g, ' ').trim();
    const parts = cleaned.split(' - ');
    if (parts.length >= 2) return { artist: parts[0].trim(), title: parts.slice(1).join(' - ').trim() };
    return { artist: '', title: cleaned };
  }
</script>

<svelte:window on:click={e => { if (inputEl && !inputEl.contains(e.target)) hideHistory(); }} />

<div class="w-full max-w-5xl mx-auto p-4 md:p-8 animate-slide-up">
  <h2 class="font-london text-3xl mb-6 pl-2">Search</h2>

  <!-- Search bar (sticky) -->
  <div class="sticky top-0 bg-kobser-bg/90 backdrop-blur-md pt-2 pb-6 z-10">
    <div class="relative w-full shadow-lg shadow-black/20 rounded-2xl flex gap-2">
      <div class="relative flex-1">
        <i class="ph ph-magnifying-glass absolute left-5 top-1/2 -translate-y-1/2 text-kobser-muted text-xl"></i>
        <input
          bind:this={inputEl}
          bind:value={query}
          type="text"
          placeholder="Search for artists, albums, or tracks..."
          class="w-full bg-kobser-surface text-kobser-text placeholder-kobser-muted rounded-2xl py-5 pl-14 pr-6 focus:outline-none focus:ring-2 focus:ring-kobser-accent/50 transition-all text-lg font-medium"
          on:focus={showHistory}
          on:input={() => query.trim() ? hideHistory() : showHistory()}
          on:keydown={e => { if (e.key === 'Enter') go(); if (e.key === 'Escape') hideHistory(); }}
        >
        <!-- Search history dropdown -->
        {#if historyVisible && history.length}
          <div class="absolute top-full left-0 right-0 mt-2 bg-kobser-surface border border-white/10 rounded-2xl shadow-2xl z-20 overflow-hidden">
            <div class="p-2">
              <div class="flex items-center justify-between px-3 py-1.5 mb-1">
                <span class="text-xs text-kobser-muted font-semibold uppercase tracking-wide">Recent searches</span>
                <button
                  on:click={() => { localStorage.removeItem(HISTORY_KEY); historyVisible = false; }}
                  class="text-xs text-kobser-muted hover:text-kobser-text transition-colors"
                >Clear all</button>
              </div>
              {#each history as h}
                <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
                <div
                  class="flex items-center gap-3 px-3 py-2.5 rounded-xl hover:bg-white/5 cursor-pointer group"
                  on:click={() => { query = h; historyVisible = false; go(); }}
                  role="option"
                  aria-selected="false"
                >
                  <i class="ph ph-clock-counter-clockwise text-kobser-muted text-base flex-shrink-0"></i>
                  <span class="flex-1 text-sm truncate">{h}</span>
                  <button
                    class="w-6 h-6 flex items-center justify-center rounded-full opacity-0 group-hover:opacity-100 hover:bg-white/10 transition-all flex-shrink-0"
                    on:click|stopPropagation={() => removeQuery(h)}
                  >
                    <i class="ph ph-x text-xs"></i>
                  </button>
                </div>
              {/each}
            </div>
          </div>
        {/if}
      </div>
      <button
        on:click={go}
        class="bg-kobser-accent hover:bg-kobser-accentHover text-kobser-bg font-semibold rounded-2xl px-6 transition-colors flex items-center gap-2 shrink-0"
      >
        <i class="ph-bold ph-magnifying-glass text-lg"></i>
        Search
      </button>
    </div>
  </div>

  <div class="mt-4">
    <h2 class="text-xl font-semibold mb-4 pl-2">{heading}</h2>
    <div class="flex flex-col gap-2">
      {#if loading}
        <div class="flex flex-col items-center justify-center py-20 opacity-50">
          <i class="ph ph-circle-notch text-4xl mb-4 animate-spin-slow"></i>
          <p class="text-lg">Searching…</p>
        </div>
      {:else if error}
        <div class="flex flex-col items-center justify-center py-20 text-red-400">
          <i class="ph ph-warning-circle text-6xl mb-4"></i>
          <p class="text-lg">Search failed: {error}</p>
        </div>
      {:else if !results.length && heading.startsWith('No results')}
        <div class="flex flex-col items-center justify-center py-20 opacity-50">
          <i class="ph ph-magnifying-glass text-6xl mb-4"></i>
          <p class="text-lg">No tracks found. Try a different search.</p>
        </div>
      {:else}
        {#each results as result}
          <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
          <div
            class="group flex items-center gap-4 p-3 rounded-xl hover:bg-white/5 transition-all w-full cursor-pointer"
            on:click={() => playPreview(result)}
            role="row"
          >
            <div class="relative w-14 h-14 rounded-md overflow-hidden flex-shrink-0">
              <img src={result.thumbnail || ''} alt="" class="w-full h-full object-cover" referrerpolicy="no-referrer" loading="lazy">
              <div class="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                <i class="ph-fill ph-play text-white text-xl pointer-events-none"></i>
              </div>
            </div>
            <div class="flex-1 min-w-0 pr-4">
              <Marquee text={result.title || 'Untitled'} cls="font-medium text-base text-kobser-text" />
              <Marquee text="{result.channel || '—'} · {fmtDuration(result.duration)}" cls="text-sm text-kobser-muted" />
            </div>
            <div class="flex items-center gap-3 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity pr-2">
              <button
                class="w-10 h-10 rounded-full flex items-center justify-center hover:bg-white/10 transition-colors text-kobser-muted hover:text-white"
                title="Download to library"
                on:click|stopPropagation={() => openDownload(result)}
              >
                <i class="ph ph-download-simple text-xl pointer-events-none"></i>
              </button>
            </div>
          </div>
        {/each}
      {/if}
    </div>
  </div>
</div>

<DownloadDialog
  bind:open={dlOpen}
  videoId={dlVideoId}
  bind:artist={dlArtist}
  bind:title={dlTitle}
  on:download={e => trackJob(e.detail.jobId, e.detail.artist, e.detail.title)}
/>
