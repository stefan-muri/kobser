<script>
  import { toasts } from '../lib/stores/toastStore.js';
  import { fly } from 'svelte/transition';
</script>

{#each $toasts as toast (toast.id)}
  <div
    in:fly={{ y: -10, duration: 200 }}
    out:fly={{ y: -10, duration: 200 }}
    class="bg-kobser-surface border border-white/10 shadow-2xl rounded-xl px-4 py-3 flex items-center gap-3 max-w-sm pointer-events-auto"
  >
    {#if toast.type === 'success'}
      <div class="w-8 h-8 rounded-full bg-kobser-success/20 flex items-center justify-center text-kobser-success flex-shrink-0">
        <i class="ph-bold ph-check"></i>
      </div>
    {:else if toast.type === 'error'}
      <div class="w-8 h-8 rounded-full bg-red-500/20 flex items-center justify-center text-red-400 flex-shrink-0">
        <i class="ph-bold ph-warning"></i>
      </div>
    {:else}
      <div class="w-8 h-8 rounded-full bg-kobser-accent/20 flex items-center justify-center text-kobser-accent flex-shrink-0">
        <i class="ph-bold ph-info"></i>
      </div>
    {/if}
    <p class="text-sm font-medium text-white">{toast.message}</p>
  </div>
{/each}
