<script>
  import { createEventDispatcher } from 'svelte';
  import { download as downloadApi } from '../lib/api.js';
  import { showToast } from '../lib/stores/toastStore.js';

  const dispatch = createEventDispatcher();

  export let open = false;
  export let videoId = '';
  export let artist = '';
  export let title = '';
  export let album = '';
  export let source = 'youtube_music';

  let dialogEl;
  let localArtist = '';
  let localTitle = '';
  let localAlbum = '';
  let showDuplicateWarning = false;

  $: if (open) {
    localArtist = artist;
    localTitle = title;
    localAlbum = album;
    showDuplicateWarning = false;
    if (dialogEl) dialogEl.showModal();
  }

  function close() {
    open = false;
    showDuplicateWarning = false;
    dialogEl?.close();
    dispatch('close');
  }

  async function confirm(force = false) {
    const a = localArtist.trim();
    const t = localTitle.trim();
    if (!a || !t) return;
    try {
      const { jobId } = await downloadApi(videoId, a, t, source, localAlbum.trim(), force);
      close();
      dispatch('download', { jobId, artist: a, title: t });
      showToast(`Downloading '${t}'...`, 'info');
    } catch (e) {
      if (e.message?.includes('409') && e.message?.includes('already in library')) {
        showDuplicateWarning = true;
      } else {
        close();
        showToast(`Download failed: ${e.message}`, 'error');
      }
    }
  }
</script>

<dialog
  bind:this={dialogEl}
  on:close={close}
  class="bg-kobser-surface text-kobser-text border border-white/10 rounded-2xl p-6 w-[90vw] max-w-md shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm"
>
  <h3 class="text-xl font-semibold mb-5 flex items-center gap-2">
    <i class="ph ph-download-simple text-kobser-accent"></i>
    Download to library
  </h3>
  <label class="block mb-4">
    <span class="block text-sm text-kobser-muted mb-1.5 font-medium">Artist</span>
    <input
      type="text"
      bind:value={localArtist}
      required
      placeholder="e.g. M83"
      class="w-full bg-kobser-bg text-kobser-text placeholder-kobser-muted rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-kobser-accent/50 transition-all border border-white/10"
    >
  </label>
  <label class="block mb-4">
    <span class="block text-sm text-kobser-muted mb-1.5 font-medium">Title</span>
    <input
      type="text"
      bind:value={localTitle}
      required
      placeholder="e.g. Midnight City"
      class="w-full bg-kobser-bg text-kobser-text placeholder-kobser-muted rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-kobser-accent/50 transition-all border border-white/10"
    >
  </label>
  <label class="block mb-4">
    <span class="block text-sm text-kobser-muted mb-1.5 font-medium">Album <span class="text-kobser-muted/50 font-normal">(optional)</span></span>
    <input
      type="text"
      bind:value={localAlbum}
      placeholder="e.g. Hurry Up, We're Dreaming"
      class="w-full bg-kobser-bg text-kobser-text placeholder-kobser-muted rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-kobser-accent/50 transition-all border border-white/10"
    >
  </label>
  {#if showDuplicateWarning}
    <div class="mt-4 p-3 bg-yellow-500/10 border border-yellow-500/30 rounded-xl text-sm text-yellow-300">
      <strong>Already in library.</strong> This song might already exist in your library. Download anyway?
    </div>
    <div class="flex gap-3 justify-end mt-4">
      <button on:click={close} class="px-5 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors">Cancel</button>
      <button on:click={() => confirm(true)} class="px-5 py-2.5 bg-kobser-accent hover:bg-kobser-accentHover text-kobser-bg rounded-xl text-sm font-semibold transition-colors">Download anyway</button>
    </div>
  {:else}
    <div class="flex gap-3 justify-end mt-6">
      <button on:click={close} class="px-5 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors">Cancel</button>
      <button on:click={() => confirm()} class="px-5 py-2.5 bg-kobser-accent hover:bg-kobser-accentHover text-kobser-bg rounded-xl text-sm font-semibold transition-colors">Download</button>
    </div>
  {/if}
</dialog>
