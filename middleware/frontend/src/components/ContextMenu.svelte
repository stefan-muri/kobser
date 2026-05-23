<script>
  import { contextMenuState } from '../lib/stores/contextMenuStore.js';
</script>

{#if $contextMenuState.visible}
  <div
    class="fixed z-[200] bg-peel-surface border border-white/10 rounded-xl shadow-2xl py-1 min-w-[196px]"
    style:top="{$contextMenuState.y}px"
    style:left="{$contextMenuState.x}px"
  >
    {#each $contextMenuState.items as item}
      {#if item === null}
        <div class="border-t border-white/5 my-1"></div>
      {:else}
        <button
          class="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-left transition-colors {item.danger ? 'text-red-400 hover:bg-red-500/10' : 'text-peel-text hover:bg-white/5'}"
          on:click={() => { contextMenuState.update(s => ({ ...s, visible: false })); item.action(); }}
        >
          <i class="{item.icon} text-base flex-shrink-0"></i>
          {item.label}
        </button>
      {/if}
    {/each}
  </div>
{/if}
