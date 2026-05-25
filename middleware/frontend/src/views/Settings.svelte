<script>
  import { getSession, setSession, logout, library as libApi, getStats } from '../lib/api.js';

  const session = getSession();
  let serverStatus = 'loading';
  let serverVersion = '';
  let stats = null;
  let statsError = '';
  let statsLoading = true;
  let rescanMsg = '';
  let rescanState = '';
  let rescanLoading = false;

  async function checkStatus() {
    serverStatus = 'loading';
    try {
      const r = await libApi('ping');
      serverVersion = r.serverVersion || '?';
      serverStatus = 'ok';
    } catch {
      serverStatus = 'error';
    }
  }

  async function loadStats() {
    statsLoading = true;
    statsError = '';
    try { stats = await getStats(); }
    catch (e) { statsError = e.message; }
    finally { statsLoading = false; }
  }

  async function refreshAll() {
    await Promise.all([checkStatus(), loadStats()]);
  }

  checkStatus();
  loadStats();

  async function doLogout() {
    try { await logout(); } catch {}
    setSession(null);
    location.reload();
  }

  async function rescan() {
    rescanLoading = true;
    rescanMsg = '';
    rescanState = '';
    try {
      await fetch('/api/rescan', { method: 'POST', headers: { 'X-Session-Id': session?.sessionId || '' } });
      rescanMsg = '✓ Scan complete.';
      rescanState = 'success';
    } catch {
      rescanMsg = 'Scan failed.';
      rescanState = 'error';
    } finally {
      rescanLoading = false;
    }
  }

  function statBar(label, used, total, icon) {
    const pct = total > 0 ? (used / total) * 100 : 0;
    const color = pct > 85 ? 'bg-red-400' : pct > 65 ? 'bg-yellow-400' : 'bg-kobser-accent';
    return { label, used, total, pct, color, icon };
  }

  function fmtBytes(bytes) {
    if (!bytes) return '0 B';
    const units = ['B','KB','MB','GB','TB'];
    let i = 0, v = bytes;
    while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
    return `${v.toFixed(i > 1 ? 1 : 0)} ${units[i]}`;
  }

  $: diskBar = stats ? statBar('Storage', stats.disk.used, stats.disk.total, 'ph-hard-drive') : null;
  $: ramBar  = stats ? statBar('Memory',  stats.ram.used,  stats.ram.total,  'ph-cpu')        : null;
</script>

<div class="w-full max-w-3xl mx-auto p-4 md:p-8">
  <h2 class="font-london text-3xl mb-8 pl-2">Settings</h2>

  <div class="flex flex-col gap-6">
    <!-- Account -->
    <div class="bg-kobser-surface rounded-2xl p-6">
      <h3 class="text-lg font-semibold mb-4 text-kobser-accent">Account</h3>
      <div class="flex items-center justify-between py-2">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-full bg-kobser-accent/20 flex items-center justify-center">
            <i class="ph-fill ph-user text-kobser-accent text-lg"></i>
          </div>
          <div>
            <p class="font-medium">{session?.username ?? '—'}</p>
            <p class="text-sm text-kobser-muted">Navidrome account</p>
          </div>
        </div>
        <button on:click={doLogout} class="px-4 py-2 bg-white/5 hover:bg-red-500/10 hover:text-red-400 rounded-xl text-sm font-medium transition-colors">
          Sign out
        </button>
      </div>
    </div>

    <!-- Server + System -->
    <div class="bg-kobser-surface rounded-2xl p-6">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-semibold text-kobser-accent">Server</h3>
        <button on:click={refreshAll} class="flex items-center gap-1.5 text-sm text-kobser-muted hover:text-kobser-text transition-colors">
          <i class="ph ph-arrows-clockwise"></i> Refresh
        </button>
      </div>

      <!-- Navidrome connection -->
      {#if serverStatus === 'loading'}
        <div class="flex items-center gap-2 text-sm text-kobser-muted mb-5">
          <i class="ph ph-circle-notch animate-spin-slow"></i> Checking…
        </div>
      {:else if serverStatus === 'ok'}
        <div class="flex items-center gap-3 py-1 mb-5">
          <div class="w-2.5 h-2.5 rounded-full bg-kobser-success shadow-[0_0_8px_rgba(46,196,182,0.6)] flex-shrink-0"></div>
          <div>
            <p class="font-medium text-kobser-text text-sm">Navidrome</p>
            <p class="text-xs text-kobser-muted">Connected · v{serverVersion}</p>
          </div>
        </div>
      {:else}
        <div class="flex items-center gap-3 py-1 mb-5">
          <div class="w-2.5 h-2.5 rounded-full bg-red-500 flex-shrink-0"></div>
          <div>
            <p class="font-medium text-kobser-text text-sm">Navidrome</p>
            <p class="text-xs text-red-400">Unreachable</p>
          </div>
        </div>
      {/if}

      <!-- System stats -->
      <div class="border-t border-white/5 pt-5">
        {#if statsLoading}
          <div class="flex items-center gap-2 text-sm text-kobser-muted"><i class="ph ph-circle-notch animate-spin-slow"></i> Loading…</div>
        {:else if statsError}
          <p class="text-sm text-red-400">Could not load stats: {statsError}</p>
        {:else if stats}
          <div class="flex flex-col gap-5">
            {#each [diskBar, ramBar] as bar}
              {#if bar}
                <div>
                  <div class="flex items-center justify-between mb-1.5">
                    <span class="flex items-center gap-1.5 text-sm font-medium">
                      <i class="ph {bar.icon} text-kobser-muted"></i> {bar.label}
                    </span>
                    <span class="text-sm font-semibold">{fmtBytes(bar.used)} / {fmtBytes(bar.total)}</span>
                  </div>
                  <div class="w-full h-2 bg-white/10 rounded-full overflow-hidden">
                    <div class="h-full rounded-full transition-all {bar.color}" style:width="{bar.pct.toFixed(1)}%"></div>
                  </div>
                  <p class="text-xs text-kobser-muted mt-1">{fmtBytes(bar.total - bar.used)} free</p>
                </div>
              {/if}
            {/each}
            <div>
              <div class="flex items-center justify-between mb-1.5">
                <span class="flex items-center gap-1.5 text-sm font-medium">
                  <i class="ph ph-activity text-kobser-muted"></i> CPU
                </span>
                <span class="text-sm font-semibold">{stats.cpu_percent.toFixed(1)}%</span>
              </div>
              <div class="w-full h-2 bg-white/10 rounded-full overflow-hidden">
                <div class="h-full rounded-full transition-all {stats.cpu_percent > 80 ? 'bg-red-400' : 'bg-kobser-accent'}"
                     style:width="{Math.min(stats.cpu_percent, 100).toFixed(1)}%"></div>
              </div>
            </div>
          </div>
        {/if}
      </div>
    </div>

    <!-- Library Rescan -->
    <div class="bg-kobser-surface rounded-2xl p-6">
      <h3 class="text-lg font-semibold mb-2 text-kobser-accent">Library</h3>
      <p class="text-sm text-kobser-muted mb-4">Trigger a Navidrome library scan to pick up any manually added files.</p>
      <button
        on:click={rescan}
        disabled={rescanLoading}
        class="flex items-center gap-2 px-4 py-2 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors disabled:opacity-50"
      >
        {#if rescanLoading}
          <i class="ph ph-circle-notch animate-spin-slow"></i> Scanning…
        {:else}
          <i class="ph ph-magnifying-glass"></i> Scan library
        {/if}
      </button>
      {#if rescanMsg}
        <p class="text-sm mt-3 {rescanState === 'success' ? 'text-kobser-success' : 'text-red-400'}">{rescanMsg}</p>
      {/if}
    </div>
  </div>
</div>
