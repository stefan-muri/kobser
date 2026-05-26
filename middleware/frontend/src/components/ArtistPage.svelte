<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { getArtist, getArtistSongs } from '../lib/api.js';
  import { fmtDuration } from '../lib/util.js';
  import { trackJob } from '../lib/jobs.js';
  import { playPreviewTrack } from '../lib/stores/playerStore.js';
  import { previewUrl } from '../lib/api.js';
  import DownloadDialog from './DownloadDialog.svelte';
  import Marquee from './Marquee.svelte';

  export let channelId = '';
  export let source = 'youtube_music';

  const dispatch = createEventDispatcher();

  let artist = null;
  let loading = true;
  let error = '';
  let allSongs = null;       // null = collapsed (show top 5), array = expanded
  let loadingMore = false;

  let dlOpen = false;
  let dlVideoId = '';
  let dlArtist = '';
  let dlTitle = '';
  let dlAlbum = '';

  onMount(async () => {
    try {
      artist = await getArtist(channelId);
    } catch (e) {
      error = e.message;
    } finally {
      loading = false;
    }
  });

  function openDownload(song) {
    dlVideoId = song.videoId;
    dlArtist = song.artist || artist?.name || '';
    dlTitle = song.title;
    dlAlbum = song.album || '';
    dlOpen = true;
  }

  async function showMoreSongs() {
    if (loadingMore) return;
    loadingMore = true;
    try {
      allSongs = await getArtistSongs(channelId);
    } catch (e) {
      error = e.message;
    } finally {
      loadingMore = false;
    }
  }

  function playPreview(song) {
    playPreviewTrack({
      id: null,
      title: song.title,
      artist: song.artist || artist?.name || 'Unknown',
      coverArt: null,
      _previewUrl: previewUrl(song.videoId),
      _previewThumb: song.thumbnail || '',
      duration: song.duration || 0,
    });
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
      <p>Loading artist…</p>
    </div>
  {:else if error}
    <div class="flex flex-col items-center justify-center py-24 text-red-400">
      <i class="ph ph-warning-circle text-5xl mb-4"></i>
      <p>{error}</p>
    </div>
  {:else if artist}
    <!-- Artist header -->
    <div class="flex items-center gap-6 mb-10">
      {#if artist.thumbnail}
        <img
          src={artist.thumbnail}
          alt={artist.name}
          class="w-24 h-24 rounded-full object-cover shadow-xl shadow-black/40 flex-shrink-0"
          referrerpolicy="no-referrer"
        />
      {:else}
        <div class="w-24 h-24 rounded-full bg-kobser-surface flex items-center justify-center flex-shrink-0">
          <i class="ph ph-user text-3xl text-kobser-muted"></i>
        </div>
      {/if}
      <div>
        <h2 class="text-4xl font-bold text-kobser-text leading-tight">{artist.name}</h2>
        {#if artist.description}
          <p class="text-sm text-kobser-muted mt-1 line-clamp-2 max-w-lg">{artist.description}</p>
        {/if}
      </div>
    </div>

    <!-- Top Songs / All Songs -->
    {#if artist.topSongs?.length}
      {@const songs = allSongs ?? artist.topSongs}
      <section class="mb-10">
        <h3 class="text-lg font-semibold mb-3 pl-1">{allSongs ? 'Songs' : 'Top Songs'}</h3>
        <div class="flex flex-col gap-1">
          {#each songs as song}
            <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
            <div
              class="group flex items-center gap-4 px-3 py-2.5 rounded-xl hover:bg-white/5 transition-all cursor-pointer"
              on:click={() => playPreview(song)}
              role="row"
            >
              <div class="relative w-10 h-10 rounded-md overflow-hidden flex-shrink-0">
                {#if song.thumbnail}
                  <img src={song.thumbnail} alt="" class="w-full h-full object-cover" referrerpolicy="no-referrer">
                {/if}
                <div class="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                  <i class="ph-fill ph-play text-white text-sm pointer-events-none"></i>
                </div>
              </div>
              <div class="flex-1 min-w-0 pr-2">
                <Marquee text={song.title} cls="text-sm font-medium text-kobser-text" />
                {#if song.album}
                  <p class="text-xs text-kobser-muted truncate">{song.album}</p>
                {/if}
              </div>
              {#if song.duration}
                <span class="text-xs text-kobser-muted tabular-nums flex-shrink-0">{fmtDuration(song.duration)}</span>
              {/if}
              <button
                class="w-8 h-8 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 hover:bg-white/10 transition-all text-kobser-muted hover:text-white flex-shrink-0"
                title="Download"
                on:click|stopPropagation={() => openDownload(song)}
              >
                <i class="ph ph-download-simple text-base pointer-events-none"></i>
              </button>
            </div>
          {/each}
        </div>
        {#if !allSongs}
          <button
            class="mt-3 ml-1 text-sm font-medium text-kobser-muted hover:text-kobser-text transition-colors flex items-center gap-1.5 disabled:opacity-50"
            on:click={showMoreSongs}
            disabled={loadingMore}
          >
            {#if loadingMore}
              <i class="ph ph-circle-notch animate-spin-slow"></i>
              Loading…
            {:else}
              <i class="ph ph-caret-down"></i>
              Show all songs
            {/if}
          </button>
        {/if}
      </section>
    {/if}

    <!-- Albums -->
    {#if artist.albums?.length}
      <section class="mb-10">
        <h3 class="text-lg font-semibold mb-3 pl-1">Albums</h3>
        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {#each artist.albums as album}
            <button
              class="group text-left bg-kobser-surface hover:bg-white/5 rounded-xl p-3 transition-all"
              on:click={() => dispatch('album', { browseId: album.browseId, title: album.title })}
            >
              <div class="relative aspect-square rounded-lg overflow-hidden mb-3">
                {#if album.thumbnail}
                  <img src={album.thumbnail} alt={album.title} class="w-full h-full object-cover" referrerpolicy="no-referrer">
                {:else}
                  <div class="w-full h-full bg-white/5 flex items-center justify-center">
                    <i class="ph ph-vinyl-record text-3xl text-kobser-muted"></i>
                  </div>
                {/if}
                <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity rounded-lg">
                  <i class="ph-fill ph-play text-white text-2xl"></i>
                </div>
              </div>
              <p class="text-sm font-medium text-kobser-text truncate">{album.title}</p>
              <p class="text-xs text-kobser-muted">{album.year}</p>
            </button>
          {/each}
        </div>
      </section>
    {/if}

    <!-- Singles & EPs -->
    {#if artist.singles?.length}
      <section class="mb-10">
        <h3 class="text-lg font-semibold mb-3 pl-1">Singles & EPs</h3>
        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {#each artist.singles as single}
            <button
              class="group text-left bg-kobser-surface hover:bg-white/5 rounded-xl p-3 transition-all"
              on:click={() => dispatch('album', { browseId: single.browseId, title: single.title })}
            >
              <div class="relative aspect-square rounded-lg overflow-hidden mb-3">
                {#if single.thumbnail}
                  <img src={single.thumbnail} alt={single.title} class="w-full h-full object-cover" referrerpolicy="no-referrer">
                {:else}
                  <div class="w-full h-full bg-white/5 flex items-center justify-center">
                    <i class="ph ph-music-note text-3xl text-kobser-muted"></i>
                  </div>
                {/if}
                <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity rounded-lg">
                  <i class="ph-fill ph-play text-white text-2xl"></i>
                </div>
              </div>
              <p class="text-sm font-medium text-kobser-text truncate">{single.title}</p>
              <p class="text-xs text-kobser-muted">{single.year}</p>
            </button>
          {/each}
        </div>
      </section>
    {/if}
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
