<script>
  import { createEventDispatcher } from 'svelte';
  import { download as downloadApi } from '../lib/api.js';
  import { showToast } from '../lib/stores/toastStore.js';

  const dispatch = createEventDispatcher();

  export let open = false;
  export let videoId = '';
  export let artist = '';
  export let title = '';

  let dialogEl;
  let onDone = null;

  $: if (open && dialogEl) dialogEl.showModal();

  function close() {
    open = false;
    dialogEl?.close();
    dispatch('close');
  }

  async function confirm() {
    const a = artist.trim();
    const t = title.trim();
    if (!a || !t) return;
    close();
    try {
      const { jobId } = await downloadApi(videoId, a, t);
      dispatch('download', { jobId, artist: a, title: t });
      showToast(`Downloading '${t}'...`, 'info');
    } catch (e) {
      showToast(`Download failed: ${e.message}`, 'error');
    }
  }
</script>

<dialog
  bind:this={dialogEl}
  on:close={close}
  class="bg-peel-surface text-peel-text border border-white/10 rounded-2xl p-6 w-[90vw] max-w-md shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm"
>
  <h3 class="text-xl font-semibold mb-5 flex items-center gap-2">
    <i class="ph ph-download-simple text-peel-accent"></i>
    Download to library
  </h3>
  <label class="block mb-4">
    <span class="block text-sm text-peel-muted mb-1.5 font-medium">Artist</span>
    <input
      type="text"
      bind:value={artist}
      required
      placeholder="e.g. M83"
      class="w-full bg-peel-bg text-peel-text placeholder-peel-muted rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 transition-all border border-white/10"
    >
  </label>
  <label class="block mb-4">
    <span class="block text-sm text-peel-muted mb-1.5 font-medium">Title</span>
    <input
      type="text"
      bind:value={title}
      required
      placeholder="e.g. Midnight City"
      class="w-full bg-peel-bg text-peel-text placeholder-peel-muted rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 transition-all border border-white/10"
    >
  </label>
  <div class="flex gap-3 justify-end mt-6">
    <button on:click={close} class="px-5 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors">Cancel</button>
    <button on:click={confirm} class="px-5 py-2.5 bg-peel-accent hover:bg-peel-accentHover text-peel-bg rounded-xl text-sm font-semibold transition-colors">Download</button>
  </div>
</dialog>
