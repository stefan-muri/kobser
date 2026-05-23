<script>
  import { moreSheetOpen } from '../lib/stores/moreSheetStore.js';
  import { showView } from '../lib/stores/viewStore.js';

  const ITEMS = [
    { view: 'liked',    icon: 'ph-heart',          label: 'Favorites',  accent: true },
    { view: 'jobs',     icon: 'ph-download-simple', label: 'Downloads',  accent: false },
    { view: 'settings', icon: 'ph-gear',             label: 'Settings',   accent: false },
  ];

  function navigate(view) {
    moreSheetOpen.set(false);
    showView(view);
  }
</script>

<!-- Backdrop -->
{#if $moreSheetOpen}
  <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
  <div
    class="md:hidden fixed inset-0 z-[55] bg-black/60 transition-opacity"
    on:click={() => moreSheetOpen.set(false)}
  ></div>
{/if}

<!-- Sheet -->
<div
  class="md:hidden fixed inset-x-0 bottom-0 z-[56] bg-peel-surface rounded-t-3xl border-t border-white/10 shadow-2xl transition-transform duration-300"
  style:transform={$moreSheetOpen ? 'translateY(0)' : 'translateY(100%)'}
  style="padding-bottom: env(safe-area-inset-bottom, 0px)"
>
  <div class="w-10 h-1 bg-white/20 rounded-full mx-auto mt-3 mb-1"></div>
  <div class="px-3 pb-3">
    {#each ITEMS as item}
      <button
        on:click={() => navigate(item.view)}
        class="w-full flex items-center gap-4 px-4 py-3 rounded-2xl hover:bg-white/5 active:bg-white/10 transition-colors text-left"
      >
        <div class="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 {item.accent ? 'bg-peel-accent/15' : 'bg-white/5'}">
          <i class="ph {item.icon} text-xl {item.accent ? 'text-peel-accent' : 'text-peel-muted'}"></i>
        </div>
        <span class="font-medium">{item.label}</span>
      </button>
    {/each}
  </div>
</div>
