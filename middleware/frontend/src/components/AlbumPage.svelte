<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { getAlbum, previewUrl, download as downloadApi } from '../lib/api.js';
  import { playPreviewTrack } from '../lib/stores/playerStore.js';
  import { fmtDuration } from '../lib/util.js';
  import { trackJob } from '../lib/jobs.js';
  import { showToast } from '../lib/stores/toastStore.js';
  import DownloadDialog from './DownloadDialog.svelte';

  export let browseId = '';
  export let source = 'youtube_music';

  const dispatch = createEventDispatcher();

  let album = null;
  let loading = true;
  let error = '';
  let downloadingAll = false;

  let dlOpen = false;
  let dlVideoId = '';
  let dlArtist = '';
  let dlTitle = '';
  let dlAlbum = '';

  onMount(async () => {
    try {
      album = await getAlbum(browseId);
    } catch (e) {
      error = e.message;
    } finally {
      loading = false;
    }
  });

  function openDownload(track) {
    dlVideoId = track.videoId;
    dlArtist = track.artist;
    dlTitle = track.title;
    dlAlbum = album?.title || '';
    dlOpen = true;
  }

  function playPreview(track) {
    playPreviewTrack({
      id: null,
      title: track.title,
      artist: track.artist,
      coverArt: null,
      _previewUrl: previewUrl(track.videoId),
      _previewThumb: album?.thumbnail || '',
      duration: track.duration || 0,
    });
  }

  async function downloadAlbum() {
    if (!album?.tracks?.length || downloadingAll) return;
    downloadingAll = true;
    showToast(`Downloading album '${album.title}' (${album.tracks.length} tracks)…`, 'info');
    try {
      for (const t of album.tracks) {
        try {
          const { jobId } = await downloadApi(t.videoId, t.artist, t.title, source, album.title);
          trackJob(jobId, t.artist, t.title);
        } catch (e) {
          showToast(`Failed '${t.title}': ${e.message}`, 'error');
        }
      }
    } finally {
      downloadingAll = false;
    }
  }
</script>

<div class="w-full animate-slide-up">
  <!-- Back -->
  <button
    on:click={() => dispatch('back')}
    class="flex items-center gap-2 text-kobser-muted hover:text-kobser-text transition-colors mb-6 group"
  >
    <i class="ph ph-arrow-left text-lg group-hover:-translate-x-0.5 transition-transform"></i>
    <span class="text-sm font-medium">Back</span>
  </button>

  {#if loading}
    <div class="flex flex-col items-center justify-center py-24 opacity-50">
      <i class="ph ph-circle-notch text-4xl mb-4 animate-spin-slow"></i>
      <p>Loading album…</p>
    </div>
  {:else if error}
    <div class="flex flex-col items-center justify-center py-24 text-red-400">
      <i class="ph ph-warning-circle text-5xl mb-4"></i>
      <p>{error}</p>
    </div>
  {:else if album}
    <!-- Album header -->
    <div class="flex gap-6 mb-8 items-end flex-wrap">
      {#if album.thumbnail}
        <img
          src={album.thumbnail}
          alt={album.title}
          class="w-40 h-40 rounded-xl object-cover shadow-2xl shadow-black/40 flex-shrink-0"
          referrerpolicy="no-referrer"
        />
      {/if}
      <div class="min-w-0 flex-1">
        <p class="text-xs uppercase tracking-widest text-kobser-muted mb-1 font-semibold">{album.type || 'Album'}</p>
        <h2 class="text-3xl font-bold text-kobser-text mb-1 leading-tight">{album.title}</h2>
        <p class="text-kobser-muted text-sm mb-4">{album.artist}{album.year ? ` · ${album.year}` : ''} · {album.tracks?.length || 0} tracks</p>
        <button
          on:click={downloadAlbum}
          disabled={downloadingAll}
          class="inline-flex items-center gap-2 px-4 py-2 bg-kobser-accent hover:bg-kobser-accentHover text-kobser-bg font-semibold rounded-full text-sm transition-colors disabled:opacity-60"
        >
          {#if downloadingAll}
            <i class="ph ph-circle-notch animate-spin-slow"></i>
            Queuing…
          {:else}
            <i class="ph-bold ph-download-simple"></i>
            Download album
          {/if}
        </button>
      </div>
    </div>

    <!-- Track list -->
    <div class="flex flex-col gap-1">
      {#each album.tracks as track, i}
        <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
        <div
          class="group flex items-center gap-4 px-3 py-2.5 rounded-xl hover:bg-white/5 transition-all cursor-pointer"
          on:click={() => playPreview(track)}
          role="row"
        >
          <span class="w-6 text-right text-sm text-kobser-muted flex-shrink-0 tabular-nums group-hover:hidden">{track.trackNumber || i + 1}</span>
          <span class="w-6 flex-shrink-0 hidden group-hover:flex items-center justify-center text-kobser-text">
            <i class="ph-fill ph-play text-sm"></i>
          </span>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-kobser-text truncate">{track.title}</p>
            {#if track.artist !== album.artist}
              <p class="text-xs text-kobser-muted truncate">{track.artist}</p>
            {/if}
          </div>
          {#if track.duration}
            <span class="text-xs text-kobser-muted tabular-nums flex-shrink-0">{fmtDuration(track.duration)}</span>
          {/if}
          <button
            class="w-8 h-8 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 hover:bg-white/10 transition-all text-kobser-muted hover:text-white flex-shrink-0"
            title="Download"
            on:click|stopPropagation={() => openDownload(track)}
          >
            <i class="ph ph-download-simple text-base pointer-events-none"></i>
          </button>
        </div>
      {/each}
    </div>
  {/if}
</div>

<DownloadDialog
  bind:open={dlOpen}
  videoId={dlVideoId}
  bind:artist={dlArtist}
  bind:title={dlTitle}
  bind:album={dlAlbum}
  {source}
  on:download={e => trackJob(e.detail.jobId, e.detail.artist, e.detail.title)}
/>
