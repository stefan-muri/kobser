<script>
  import { onMount } from 'svelte';
  import { getSession, setSession, me } from './lib/api.js';
  import { currentView } from './lib/stores/viewStore.js';
  import { initAudio } from './lib/stores/playerStore.js';
  import { currentTrack } from './lib/stores/playerStore.js';

  import Login from './auth/Login.svelte';
  import Sidebar from './components/Sidebar.svelte';
  import MobileNav from './components/MobileNav.svelte';
  import MoreSheet from './components/MoreSheet.svelte';
  import MiniPlayer from './components/MiniPlayer.svelte';
  import ExpandedPlayer from './components/ExpandedPlayer.svelte';
  import QueuePanel from './components/QueuePanel.svelte';
  import ContextMenu from './components/ContextMenu.svelte';
  import Toasts from './components/Toasts.svelte';
  import PlaylistPicker from './components/PlaylistPicker.svelte';

  import Search from './views/Search.svelte';
  import Library from './views/Library.svelte';
  import Liked from './views/Liked.svelte';
  import Artists from './views/Artists.svelte';
  import Playlists from './views/Playlists.svelte';
  import Downloads from './views/Downloads.svelte';
  import Settings from './views/Settings.svelte';

  const VIEWS = {
    search: Search, library: Library, liked: Liked, artists: Artists,
    playlists: Playlists, jobs: Downloads, settings: Settings,
  };

  let authed = false;
  let checking = true;
  let audioEl;
  let audioInitialized = false;

  onMount(async () => {
    const session = getSession();
    if (!session) { checking = false; return; }
    try {
      await me();
      authed = true;
    } catch {
      setSession(null);
    }
    checking = false;
  });

  function onLoginSuccess() { authed = true; }

  $: if (authed && audioEl && !audioInitialized) {
    audioInitialized = true;
    initAudio(audioEl);
  }

  // Toggle body class so CSS can adjust view padding when player is visible
  $: if (typeof document !== 'undefined') {
    document.body.classList.toggle('player-visible', !!$currentTrack);
  }
</script>

{#if checking}
  <div class="fixed inset-0 bg-kobser-bg flex items-center justify-center">
    <div class="w-10 h-10 rounded-full bg-kobser-accent flex items-center justify-center shadow-[0_0_20px_rgba(255,159,28,0.5)] animate-pulse">
      <i class="ph-fill ph-vinyl-record text-2xl text-kobser-bg"></i>
    </div>
  </div>
{:else if !authed}
  <Login on:success={onLoginSuccess} />
{:else}
  <div class="bg-kobser-bg text-kobser-text font-sans h-screen w-full overflow-hidden flex flex-col selection:bg-kobser-accent selection:text-white antialiased">
    <div class="flex-1 flex overflow-hidden">
      <Sidebar />
      <main class="flex-1 overflow-y-auto h-full md:pb-28 relative w-full" id="view">
        <svelte:component this={VIEWS[$currentView]} />
      </main>
    </div>

    <MobileNav />
    <MoreSheet />
    <MiniPlayer />
    <ExpandedPlayer />
    <QueuePanel />

    <ContextMenu />
    <PlaylistPicker />

    <div id="toast-container" class="fixed top-4 right-4 z-[300] flex flex-col gap-2 pointer-events-none">
      <Toasts />
    </div>

    <audio bind:this={audioEl} preload="metadata" id="audio"></audio>
  </div>
{/if}

<style>
  :global(body) { margin: 0; padding: 0; }
  :global(#app) { height: 100vh; overflow: hidden; }
</style>
