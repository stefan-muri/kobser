<script>
  import { playlistPickerState, closePlaylistPicker } from '../lib/stores/playlistPickerStore.js';
  import { library as libApi } from '../lib/api.js';
  import { showToast } from '../lib/stores/toastStore.js';

  let dialogEl;
  let playlists = [];
  let loading = false;

  $: if ($playlistPickerState.open) {
    if (dialogEl && !dialogEl.open) {
      load().then(() => { if (dialogEl && !dialogEl.open) dialogEl.showModal(); });
    }
  }

  async function load() {
    loading = true;
    try {
      const r = await libApi('getPlaylists');
      playlists = r.playlists?.playlist || [];
    } catch (e) {
      playlists = [];
    } finally {
      loading = false;
    }
  }

  async function pick(playlist) {
    const { trackIds, onDone } = $playlistPickerState;
    dialogEl.close();
    closePlaylistPicker();
    try {
      await libApi('updatePlaylist', { playlistId: playlist.id, songIdToAdd: trackIds });
      showToast(`Added to "${playlist.name}"`);
      onDone?.();
    } catch (e) {
      alert(`Failed: ${e.message}`);
    }
  }

  function handleClose() {
    closePlaylistPicker();
  }
</script>

<dialog
  bind:this={dialogEl}
  on:close={handleClose}
  class="bg-peel-surface text-peel-text border border-white/10 rounded-2xl p-6 min-w-[320px] max-w-[90vw] shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm"
>
  <h3 class="text-lg font-semibold mb-4 flex items-center gap-2">
    <i class="ph-fill ph-playlist-plus text-peel-accent"></i>
    Add to playlist
  </h3>
  {#if loading}
    <div class="flex items-center justify-center py-8 opacity-50">
      <i class="ph ph-circle-notch animate-spin-slow text-2xl"></i>
    </div>
  {:else}
    <div class="flex flex-col gap-1 max-h-72 overflow-y-auto mb-4">
      {#if playlists.length}
        {#each playlists as pl}
          <button
            on:click={() => pick(pl)}
            class="flex items-center gap-3 px-4 py-2.5 rounded-xl hover:bg-white/10 text-sm text-left transition-colors"
          >
            <i class="ph-fill ph-playlist text-peel-muted text-base flex-shrink-0"></i>
            <span class="truncate">{pl.name}</span>
            <span class="ml-auto text-peel-muted text-xs">{pl.songCount || 0}</span>
          </button>
        {/each}
      {:else}
        <p class="text-center text-peel-muted py-6 text-sm">No playlists yet.</p>
      {/if}
    </div>
  {/if}
  <div class="flex justify-end">
    <button
      on:click={() => { dialogEl.close(); closePlaylistPicker(); }}
      class="px-5 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors"
    >Cancel</button>
  </div>
</dialog>
