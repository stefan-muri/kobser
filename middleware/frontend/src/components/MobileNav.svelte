<script>
  import { currentView, showView, isMoreView } from '../lib/stores/viewStore.js';
  import { moreSheetOpen } from '../lib/stores/moreSheetStore.js';

  const TABS = [
    { view: 'search',    icon: 'ph-magnifying-glass', label: 'Search' },
    { view: 'library',   icon: 'ph-music-notes',      label: 'Library' },
    { view: 'artists',   icon: 'ph-microphone-stage',  label: 'Artists' },
    { view: 'playlists', icon: 'ph-playlist',          label: 'Playlists' },
  ];

  $: moreActive = isMoreView($currentView);
</script>

<nav
  class="md:hidden fixed bottom-0 inset-x-0 z-40 bg-peel-bg/95 backdrop-blur-md border-t border-white/5 user-select-none"
  style="padding-bottom: env(safe-area-inset-bottom, 0px)"
>
  <div class="flex items-stretch h-[60px]">
    {#each TABS as tab}
      {@const active = $currentView === tab.view}
      <button
        on:click={() => showView(tab.view)}
        class="flex flex-col items-center justify-center gap-0.5 flex-1 transition-colors {active ? 'text-peel-accent' : 'text-peel-muted'}"
      >
        <i class="{active ? 'ph-fill' : 'ph'} {tab.icon} text-xl"></i>
        <span class="text-[9px] font-medium">{tab.label}</span>
      </button>
    {/each}
    <button
      on:click={() => moreSheetOpen.set(true)}
      class="flex flex-col items-center justify-center gap-0.5 flex-1 transition-colors {moreActive ? 'text-peel-accent' : 'text-peel-muted'}"
    >
      <i class="ph ph-dots-three text-xl"></i>
      <span class="text-[9px] font-medium">More</span>
    </button>
  </div>
</nav>
