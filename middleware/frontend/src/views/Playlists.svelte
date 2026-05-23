<script>
  import { library as libApi } from '../lib/api.js';
  import TrackList from '../components/TrackList.svelte';
  import { showContextMenu } from '../lib/stores/contextMenuStore.js';

  let stack = [{ type: 'playlists', label: 'Playlists' }];
  let playlists = [];
  let playlistFilter = '';
  let songs = [];
  let loading = true;
  let error = '';

  // Dialogs
  let createOpen = false;
  let createName = '';
  let createDialogEl;
  let renameOpen = false;
  let renameId = '';
  let renameCurrentName = '';
  let renameName = '';
  let renameDialogEl;
  let songPickerOpen = false;
  let songPickerPlaylistId = '';
  let songPickerPlaylistName = '';
  let spAllSongs = [];
  let spFilter = '';
  let spSelected = new Set();
  let spDialogEl;
  let spLoading = false;

  $: top = stack[stack.length - 1];
  $: filteredPlaylists = playlistFilter
    ? playlists.filter(p => p.name.toLowerCase().includes(playlistFilter.toLowerCase()))
    : playlists;
  $: spFiltered = spFilter
    ? spAllSongs.filter(s => (s.title || '').toLowerCase().includes(spFilter.toLowerCase()) || (s.artist || '').toLowerCase().includes(spFilter.toLowerCase()))
    : spAllSongs;

  $: top, loadCurrent();

  async function loadCurrent() {
    loading = true; error = '';
    try {
      if (top.type === 'playlists') await loadPlaylists();
      else if (top.type === 'playlist') await loadPlaylist(top.id);
    } catch (e) { error = e.message; }
    finally { loading = false; }
  }

  async function loadPlaylists() {
    const r = await libApi('getPlaylists');
    playlists = r.playlists?.playlist || [];
    playlistFilter = '';
  }

  async function loadPlaylist(id) {
    const r = await libApi('getPlaylist', { id });
    songs = r.playlist?.entry || [];
  }

  function push(item) { stack = [...stack, item]; }
  function goTo(idx) { stack = stack.slice(0, idx + 1); }

  function openDots(e, p) {
    e.stopPropagation();
    showContextMenu(e.currentTarget, [
      {
        label: 'Add songs', icon: 'ph ph-plus-circle',
        action: () => openSongPicker(p.id, p.name),
      },
      {
        label: 'Rename', icon: 'ph ph-pencil',
        action: () => { renameId = p.id; renameCurrentName = p.name; renameName = p.name; renameOpen = true; renameDialogEl?.showModal(); },
      },
      null,
      {
        label: 'Delete', icon: 'ph ph-trash', danger: true,
        action: async () => {
          if (!confirm(`Delete "${p.name}"?`)) return;
          await libApi('deletePlaylist', { id: p.id });
          await loadPlaylists();
        },
      },
    ]);
  }

  async function createPlaylist() {
    const name = createName.trim();
    if (!name) return;
    await libApi('createPlaylist', { name });
    createDialogEl?.close();
    createOpen = false;
    createName = '';
    await loadPlaylists();
  }

  async function renamePlaylist() {
    const name = renameName.trim();
    if (!name || name === renameCurrentName) { renameDialogEl?.close(); renameOpen = false; return; }
    await libApi('updatePlaylist', { playlistId: renameId, name });
    renameDialogEl?.close();
    renameOpen = false;
    await loadPlaylists();
  }

  async function openSongPicker(plId, plName) {
    songPickerPlaylistId = plId;
    songPickerPlaylistName = plName;
    spSelected = new Set();
    spFilter = '';
    spLoading = true;
    songPickerOpen = true;
    spDialogEl?.showModal();
    try {
      let all = [];
      let offset = 0;
      while (true) {
        const r = await libApi('search3', { query: ' ', songCount: 500, songOffset: offset, artistCount: 0, albumCount: 0 });
        const batch = r.searchResult3?.song || [];
        all.push(...batch);
        if (batch.length < 500) break;
        offset += 500;
      }
      all.sort((a, b) => (a.artist || '').localeCompare(b.artist || '') || (a.title || '').localeCompare(b.title || ''));
      spAllSongs = all;
    } catch (e) { spAllSongs = []; }
    finally { spLoading = false; }
  }

  async function confirmSongPicker() {
    const ids = [...spSelected];
    if (!ids.length) return;
    try {
      await libApi('updatePlaylist', { playlistId: songPickerPlaylistId, songIdToAdd: ids });
      spDialogEl?.close();
      songPickerOpen = false;
      if (top.type === 'playlist' && top.id === songPickerPlaylistId) await loadPlaylist(top.id);
    } catch (e) { alert(`Failed to add songs: ${e.message}`); }
  }
</script>

<div class="w-full max-w-5xl mx-auto p-4 md:p-8">
  <h2 class="font-london text-3xl mb-6">Playlists</h2>

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
  {:else if top.type === 'playlists'}
    <div class="flex items-center gap-2 mb-4 flex-wrap">
      <div class="relative flex-1 min-w-48">
        <i class="ph ph-magnifying-glass absolute left-4 top-1/2 -translate-y-1/2 text-peel-muted"></i>
        <input type="text" bind:value={playlistFilter} placeholder="Search playlists…"
          class="w-full bg-peel-surface text-peel-text placeholder-peel-muted rounded-xl py-2.5 pl-11 pr-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10 text-sm">
      </div>
      <button on:click={() => { createName = ''; createOpen = true; createDialogEl?.showModal(); }}
        class="flex items-center gap-2 px-4 py-2 bg-peel-accent hover:bg-peel-accentHover text-peel-bg text-sm font-semibold rounded-xl transition-colors flex-shrink-0">
        <i class="ph-bold ph-plus"></i> New playlist
      </button>
    </div>
    <div class="flex flex-col gap-2">
      {#if !filteredPlaylists.length}
        <div class="flex flex-col items-center justify-center py-20 opacity-50">
          <i class="ph ph-playlist text-6xl mb-4"></i>
          <p class="text-lg">No playlists yet.</p>
        </div>
      {:else}
        {#each filteredPlaylists as p}
          <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
          <div class="group flex items-center gap-4 p-3 rounded-xl hover:bg-white/5 transition-all cursor-pointer"
               on:click={() => push({ type: 'playlist', id: p.id, label: p.name })} role="row">
            <div class="w-12 h-12 rounded-xl bg-peel-surface flex items-center justify-center flex-shrink-0">
              <i class="ph-fill ph-playlist text-peel-muted text-2xl"></i>
            </div>
            <div class="flex-1 min-w-0">
              <p class="font-medium truncate">{p.name}</p>
              <p class="text-sm text-peel-muted">{p.songCount || 0} track{p.songCount === 1 ? '' : 's'}</p>
            </div>
            <button class="dots-btn w-9 h-9 rounded-full flex items-center justify-center hover:bg-white/10 text-peel-muted opacity-0 group-hover:opacity-100 transition-all flex-shrink-0"
                    on:click={e => openDots(e, p)} title="More options">
              <i class="ph ph-dots-three-vertical text-lg pointer-events-none"></i>
            </button>
            <i class="ph ph-caret-right text-peel-muted opacity-0 group-hover:opacity-100 transition-opacity"></i>
          </div>
        {/each}
      {/if}
    </div>
  {:else if top.type === 'playlist'}
    <div class="flex justify-end mb-2">
      <button on:click={() => openSongPicker(top.id, top.label)}
        class="flex items-center gap-2 px-4 py-2 bg-white/5 hover:bg-white/10 text-sm font-medium rounded-xl transition-colors">
        <i class="ph ph-plus text-sm"></i> Add songs
      </button>
    </div>
    {#if !songs.length}
      <p class="text-center py-12 text-peel-muted">No tracks yet. Add some!</p>
    {:else}
      <TrackList {songs} playlistId={top.id} onRefresh={() => loadPlaylist(top.id)} />
    {/if}
  {/if}
</div>

<!-- Create playlist dialog -->
<dialog bind:this={createDialogEl} on:close={() => { createOpen = false; }}
  class="bg-peel-surface text-peel-text border border-white/10 rounded-2xl p-6 min-w-[320px] max-w-[90vw] shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm">
  <h3 class="text-lg font-semibold mb-4 flex items-center gap-2">
    <i class="ph-fill ph-playlist text-peel-accent"></i> New playlist
  </h3>
  <input type="text" bind:value={createName} placeholder="Playlist name" autofocus
    on:keydown={e => { if (e.key === 'Enter') createPlaylist(); }}
    class="w-full bg-peel-bg text-peel-text placeholder-peel-muted rounded-xl py-3 px-4 mb-5 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10">
  <div class="flex gap-3 justify-end">
    <button on:click={() => createDialogEl?.close()} class="px-5 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors">Cancel</button>
    <button on:click={createPlaylist} class="px-5 py-2.5 bg-peel-accent hover:bg-peel-accentHover text-peel-bg rounded-xl text-sm font-semibold transition-colors">Create</button>
  </div>
</dialog>

<!-- Rename dialog -->
<dialog bind:this={renameDialogEl} on:close={() => { renameOpen = false; }}
  class="bg-peel-surface text-peel-text border border-white/10 rounded-2xl p-6 min-w-[320px] max-w-[90vw] shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm">
  <h3 class="text-lg font-semibold mb-4">Rename playlist</h3>
  <input type="text" bind:value={renameName}
    on:keydown={e => { if (e.key === 'Enter') renamePlaylist(); }}
    class="w-full bg-peel-bg text-peel-text placeholder-peel-muted rounded-xl py-3 px-4 mb-5 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10">
  <div class="flex gap-3 justify-end">
    <button on:click={() => renameDialogEl?.close()} class="px-5 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors">Cancel</button>
    <button on:click={renamePlaylist} class="px-5 py-2.5 bg-peel-accent hover:bg-peel-accentHover text-peel-bg rounded-xl text-sm font-semibold transition-colors">Save</button>
  </div>
</dialog>

<!-- Song picker dialog -->
<dialog bind:this={spDialogEl} on:close={() => { songPickerOpen = false; }}
  class="bg-peel-surface text-peel-text border border-white/10 rounded-2xl p-0 w-[90vw] max-w-lg shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm overflow-hidden">
  <div class="flex flex-col max-h-[80vh]">
  <div class="p-5 border-b border-white/10 flex-shrink-0">
    <h3 class="text-base font-semibold mb-3 flex items-center gap-2">
      <i class="ph-fill ph-playlist-plus text-peel-accent"></i>
      Add songs to "{songPickerPlaylistName}"
    </h3>
    <input type="text" bind:value={spFilter} placeholder="Search by title or artist…"
      class="w-full bg-peel-bg text-peel-text placeholder-peel-muted rounded-xl py-2.5 px-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10 text-sm">
  </div>
  <div class="overflow-y-auto flex-1">
    {#if spLoading}
      <div class="flex items-center justify-center py-16 opacity-50"><i class="ph ph-circle-notch text-3xl animate-spin-slow"></i></div>
    {:else if !spFiltered.length}
      <p class="text-center py-10 text-peel-muted text-sm">No songs found.</p>
    {:else}
      {#each spFiltered as s (s.id)}
        <label class="flex items-center gap-3 px-4 py-2.5 hover:bg-white/5 cursor-pointer transition-colors">
          <input type="checkbox" checked={spSelected.has(s.id)}
            on:change={e => { const sel = new Set(spSelected); e.target.checked ? sel.add(s.id) : sel.delete(s.id); spSelected = sel; }}
            class="w-4 h-4 rounded accent-peel-accent flex-shrink-0 cursor-pointer">
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium truncate">{s.title || '—'}</p>
            <p class="text-xs text-peel-muted truncate">{s.artist || '—'}</p>
          </div>
        </label>
      {/each}
    {/if}
  </div>
  <div class="p-4 border-t border-white/10 flex items-center justify-between gap-3 flex-shrink-0">
    <span class="text-sm text-peel-muted">{spSelected.size} selected</span>
    <div class="flex gap-2">
      <button on:click={() => spDialogEl?.close()} class="px-4 py-2 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors">Cancel</button>
      <button on:click={confirmSongPicker} disabled={spSelected.size === 0}
        class="px-4 py-2 bg-peel-accent hover:bg-peel-accentHover text-peel-bg rounded-xl text-sm font-semibold transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
        {spSelected.size ? `Add ${spSelected.size} song${spSelected.size !== 1 ? 's' : ''}` : 'Add'}
      </button>
    </div>
  </div>
  </div>
</dialog>
