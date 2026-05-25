<script>
  import { onMount } from 'svelte';
  import { activeJobs } from '../lib/jobs.js';
  import { listDownloads, deleteDownload, cancelJob } from '../lib/api.js';

  let dbDownloads = [];
  let loading = true;

  onMount(async () => {
    await loadDb();
  });

  async function loadDb() {
    loading = true;
    try {
      const r = await listDownloads();
      dbDownloads = r.downloads || [];
    } catch {}
    finally { loading = false; }
  }

  $: activeIds = new Set($activeJobs.map(j => j.id));
  $: all = [...$activeJobs, ...dbDownloads.filter(d => !activeIds.has(d.id))];

  // Reload DB downloads when active jobs settle
  $: if ($activeJobs.length && $activeJobs.every(j => ['done','error','cancelled'].includes(j.status))) {
    loadDb();
  }

  async function remove(id) {
    try {
      await deleteDownload(id);
      dbDownloads = dbDownloads.filter(d => d.id !== id);
      activeJobs.update(jobs => jobs.filter(j => j.id !== id));
    } catch (err) { alert(`Delete failed: ${err.message}`); }
  }

  async function cancel(id) {
    try {
      await cancelJob(id);
      activeJobs.update(jobs => jobs.map(j => j.id === id ? { ...j, status: 'cancelled' } : j));
    } catch (err) { alert(`Cancel failed: ${err.message}`); }
  }

  const ACTIVE_STATUSES = new Set(['pending','downloading','tagging','scanning']);

  function statusClasses(status) {
    switch (status) {
      case 'done':       return 'bg-kobser-success/20 text-kobser-success';
      case 'error':      return 'bg-red-500/20 text-red-400';
      case 'cancelled':  return 'bg-white/10 text-kobser-muted';
      case 'downloading':
      case 'tagging':
      case 'scanning':   return 'bg-kobser-accent/20 text-kobser-accent';
      default:           return 'bg-white/10 text-kobser-muted';
    }
  }

  function fmtDate(ts) {
    return new Date(ts * 1000).toLocaleDateString(undefined, {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  }

  function fmtDur(secs) {
    if (!secs || secs < 1) return '<1s';
    if (secs < 60) return `${Math.round(secs)}s`;
    return `${Math.floor(secs / 60)}m ${Math.round(secs % 60)}s`;
  }

  function fmtBytes(bytes) {
    if (!bytes) return '';
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
</script>

<div class="w-full max-w-3xl mx-auto p-4 md:p-8">
  <h2 class="font-london text-3xl mb-8 pl-2">Downloads</h2>

  {#if loading && !all.length}
    <div class="flex items-center justify-center py-20 opacity-50">
      <i class="ph ph-circle-notch text-4xl animate-spin-slow"></i>
    </div>
  {:else if !all.length}
    <div class="flex flex-col items-center justify-center py-20 opacity-50">
      <i class="ph ph-download-simple text-6xl mb-4"></i>
      <p class="text-lg">No downloads yet. Search and download from the Search tab.</p>
    </div>
  {:else}
    <div class="flex flex-col gap-1">
      {#each all as job (job.id)}
        {@const isActive = ACTIVE_STATUSES.has(job.status)}
        <div class="flex items-center gap-4 p-4 rounded-xl hover:bg-white/5 transition-all border-b border-white/5">
          <div class="min-w-0 flex-1">
            <p class="font-medium truncate">{job.artist} — {job.title}</p>
            <p class="text-xs text-kobser-muted mt-0.5">
              {#if job.started_at}{fmtDate(job.started_at)}{/if}
              {#if job.completed_at && job.started_at} · took {fmtDur(job.completed_at - job.started_at)}{/if}
              {#if job.file_size_bytes} · {fmtBytes(job.file_size_bytes)}{/if}
            </p>
            {#if job.error && job.status !== 'cancelled'}
              <p class="text-xs text-red-400 mt-1 truncate"><i class="ph ph-warning mr-1"></i>{job.error}</p>
            {/if}
          </div>
          <span class="text-xs font-semibold px-3 py-1.5 rounded-full capitalize whitespace-nowrap flex-shrink-0 {statusClasses(job.status)}">{job.status}</span>
          {#if isActive}
            <button
              on:click={() => cancel(job.id)}
              class="px-3 py-1 rounded-lg text-xs font-medium text-kobser-muted hover:text-red-400 hover:bg-red-500/10 transition-colors flex-shrink-0"
            >Cancel</button>
          {:else}
            <button
              on:click={() => remove(job.id)}
              class="w-8 h-8 rounded-full flex items-center justify-center text-kobser-muted hover:text-red-400 hover:bg-red-500/10 transition-colors flex-shrink-0"
              title="Remove"
            >
              <i class="ph ph-trash text-sm pointer-events-none"></i>
            </button>
          {/if}
        </div>
      {/each}
    </div>
  {/if}
</div>
