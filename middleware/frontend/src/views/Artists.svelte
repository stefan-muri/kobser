<script>
  import { library as libApi, coverArtUrl } from '../lib/api.js';
  import TrackList from '../components/TrackList.svelte';
  import { playQueue } from '../lib/stores/playerStore.js';

  // Navigation stack: [{ type, id?, label }]
  let stack = [{ type: 'artists', label: 'Artists' }];
  let loading = true;
  let error = '';

  // View data
  let artists = [];
  let artistFilter = '';
  let albums = [];
  let songs = [];

  $: top = stack[stack.length - 1];
  $: filteredArtists = artistFilter
    ? artists.filter(a => a.name.toLowerCase().includes(artistFilter.toLowerCase()))
    : artists;

  $: top, loadCurrent();

  async function loadCurrent() {
    loading = true; error = '';
    try {
      if (top.type === 'artists') await loadArtists();
      else if (top.type === 'artist') await loadArtist(top.id);
      else if (top.type === 'album') await loadAlbum(top.id);
    } catch (e) { error = e.message; }
    finally { loading = false; }
  }

  async function loadArtists() {
    const r = await libApi('getArtists');
    artists = (r.artists?.index || []).flatMap(i => i.artist || []);
    artistFilter = '';
  }

  async function loadArtist(id) {
    const r = await libApi('getArtist', { id });
    albums = r.artist?.album || [];
  }

  async function loadAlbum(id) {
    const r = await libApi('getAlbum', { id });
    songs = r.album?.song || [];
  }

  function push(item) { stack = [...stack, item]; }
  function goTo(idx) { stack = stack.slice(0, idx + 1); }
</script>

<div class="w-full max-w-5xl mx-auto p-4 md:p-8">
  <h2 class="font-london text-3xl mb-6">Artists</h2>

  <!-- Breadcrumb -->
  {#if stack.length > 1}
    <div class="flex items-center gap-2 text-sm text-peel-muted mb-6 flex-wrap">
      {#each stack as crumb, i}
        {#if i < stack.length - 1}
          <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
          <span class="cursor-pointer hover:text-peel-text transition-colors" on:click={() => goTo(i)} role="link" tabindex="0">{crumb.label}</span>
          <i class="ph ph-caret-right text-xs"></i>
        {:else}
          <span class="text-peel-text font-medium">{crumb.label}</span>
        {/if}
      {/each}
    </div>
  {/if}

  {#if loading}
    <div class="flex items-center justify-center py-20 opacity-50">
      <i class="ph ph-circle-notch text-4xl animate-spin-slow"></i>
    </div>
  {:else if error}
    <div class="flex flex-col items-center justify-center py-20 text-red-400">
      <i class="ph ph-warning-circle text-5xl mb-4"></i><p>{error}</p>
    </div>
  {:else if top.type === 'artists'}
    {#if !artists.length}
      <div class="flex flex-col items-center justify-center py-20 opacity-50">
        <i class="ph ph-user-circle text-6xl mb-4"></i>
        <p class="text-lg">No artists yet. Download something from Search.</p>
      </div>
    {:else}
      <div class="relative mb-4">
        <i class="ph ph-magnifying-glass absolute left-4 top-1/2 -translate-y-1/2 text-peel-muted"></i>
        <input type="text" bind:value={artistFilter} placeholder="Search artists…"
          class="w-full bg-peel-surface text-peel-text placeholder-peel-muted rounded-xl py-2.5 pl-11 pr-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10 text-sm">
      </div>
      {#if !filteredArtists.length}
        <p class="text-center py-10 text-peel-muted text-sm">No artists match "{artistFilter}"</p>
      {:else}
        <div class="flex flex-col gap-1">
          {#each filteredArtists as artist}
            <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
            <div
              class="group flex items-center gap-4 p-3 rounded-xl hover:bg-white/5 transition-all cursor-pointer"
              on:click={() => push({ type: 'artist', id: artist.id, label: artist.name })}
              role="row"
            >
              <img class="w-12 h-12 rounded-full object-cover flex-shrink-0 bg-peel-surface"
                   src={coverArtUrl(artist.coverArt, 96)} alt="" loading="lazy">
              <div class="flex-1 min-w-0">
                <p class="font-medium truncate">{artist.name}</p>
                <p class="text-sm text-peel-muted">{artist.albumCount || 0} album{artist.albumCount === 1 ? '' : 's'}</p>
              </div>
              <i class="ph ph-caret-right text-peel-muted opacity-0 group-hover:opacity-100 transition-opacity"></i>
            </div>
          {/each}
        </div>
      {/if}
    {/if}
  {:else if top.type === 'artist'}
    {#if !albums.length}
      <p class="text-center py-12 text-peel-muted">No albums.</p>
    {:else}
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        {#each albums as album}
          <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
          <div class="group cursor-pointer" on:click={() => push({ type: 'album', id: album.id, label: album.name })} role="gridcell">
            <div class="relative w-full aspect-square rounded-xl overflow-hidden mb-3 shadow-lg bg-peel-surface">
              <img src={coverArtUrl(album.coverArt, 300)} alt="" loading="lazy"
                   class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105">
              <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                <div class="w-12 h-12 bg-peel-accent rounded-full flex items-center justify-center shadow-xl translate-y-2 group-hover:translate-y-0 transition-transform">
                  <i class="ph-fill ph-play text-peel-bg text-xl"></i>
                </div>
              </div>
            </div>
            <p class="font-medium truncate text-sm">{album.name}</p>
            <p class="text-xs text-peel-muted">{album.songCount || 0} track{album.songCount === 1 ? '' : 's'}</p>
          </div>
        {/each}
      </div>
    {/if}
  {:else if top.type === 'album'}
    {#if !songs.length}
      <p class="text-center py-12 text-peel-muted">No tracks.</p>
    {:else}
      <TrackList {songs} onRefresh={() => loadAlbum(top.id)} />
    {/if}
  {/if}
</div>
