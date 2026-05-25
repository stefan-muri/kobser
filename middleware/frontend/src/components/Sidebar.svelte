<script>
  import { onMount } from 'svelte';
  import { currentView, showView, isMoreView } from '../lib/stores/viewStore.js';

  const COLLAPSED_KEY = 'kobser:sidebar-collapsed';

  let collapsed = localStorage.getItem(COLLAPSED_KEY) === '1';
  let mounted = false;

  onMount(() => { mounted = true; });

  function toggle() {
    collapsed = !collapsed;
    localStorage.setItem(COLLAPSED_KEY, collapsed ? '1' : '0');
  }

  const NAV = [
    { view: 'search',    icon: 'ph-magnifying-glass', label: 'Search' },
    { view: 'library',   icon: 'ph-music-notes',      label: 'Library' },
    { view: 'liked',     icon: 'ph-heart',             label: 'Favorites' },
    { view: 'artists',   icon: 'ph-microphone-stage',  label: 'Artists' },
    { view: 'playlists', icon: 'ph-playlist',          label: 'Playlists' },
  ];
  const NAV_BOTTOM = [
    { view: 'jobs',     icon: 'ph-download-simple', label: 'Downloads' },
    { view: 'settings', icon: 'ph-gear',             label: 'Settings' },
  ];
</script>

<aside
  class="hidden md:flex flex-col bg-kobser-bg border-r border-white/5 h-full pt-8 pb-28 user-select-none overflow-hidden"
  style:width={collapsed ? '64px' : '256px'}
  style:transition={mounted ? 'width 200ms ease-in-out' : 'none'}
>
  <!-- Logo + toggle -->
  <div class="flex items-center gap-3 px-5 mb-10 min-w-0">
    <button
      on:click={toggle}
      class="w-8 h-8 rounded-full bg-kobser-accent flex-shrink-0 flex items-center justify-center text-kobser-bg shadow-[0_0_15px_rgba(255,159,28,0.4)] hover:bg-kobser-accentHover transition-colors"
      title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
    >
      <i class="ph-fill ph-vinyl-record text-xl"></i>
    </button>
    <h1
      class="font-london text-3xl whitespace-nowrap overflow-hidden"
      style:opacity={collapsed ? '0' : '1'}
      style:max-width={collapsed ? '0' : '200px'}
      style:transition={mounted ? 'opacity 200ms, max-width 200ms' : 'none'}
    >kobser</h1>
  </div>

  <!-- Navigation -->
  <nav class="flex flex-col gap-1 flex-1 px-2">
    {#each NAV as item}
      <button
        on:click={() => showView(item.view)}
        class="flex items-center gap-4 px-3 py-3 rounded-xl transition-all text-left font-medium min-w-0 hover:bg-white/5 hover:text-kobser-text {$currentView === item.view ? 'text-kobser-text bg-white/5' : 'text-kobser-muted'}"
      >
        <i class="ph {item.icon} text-2xl flex-shrink-0"></i>
        <span
          class="whitespace-nowrap overflow-hidden"
          style:opacity={collapsed ? '0' : '1'}
          style:max-width={collapsed ? '0' : '200px'}
          style:transition={mounted ? 'opacity 200ms, max-width 200ms' : 'none'}
        >{item.label}</span>
      </button>
    {/each}

    <div class="flex-1"></div>

    {#each NAV_BOTTOM as item}
      <button
        on:click={() => showView(item.view)}
        class="flex items-center gap-4 px-3 py-3 rounded-xl transition-all text-left font-medium min-w-0 hover:bg-white/5 hover:text-kobser-text {$currentView === item.view ? 'text-kobser-text bg-white/5' : 'text-kobser-muted'}"
      >
        <i class="ph {item.icon} text-2xl flex-shrink-0"></i>
        <span
          class="whitespace-nowrap overflow-hidden"
          style:opacity={collapsed ? '0' : '1'}
          style:max-width={collapsed ? '0' : '200px'}
          style:transition={mounted ? 'opacity 200ms, max-width 200ms' : 'none'}
        >{item.label}</span>
      </button>
    {/each}
  </nav>
</aside>
