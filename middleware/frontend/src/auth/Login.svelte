<script>
  import { createEventDispatcher } from 'svelte';
  import { login, setSession } from '../lib/api.js';

  const dispatch = createEventDispatcher();

  let username = '';
  let password = '';
  let loading = false;
  let error = '';
  let passEl;

  async function doLogin() {
    if (!username.trim() || !password) return;
    loading = true;
    error = '';
    try {
      const result = await login(username.trim(), password);
      setSession(result);
      dispatch('success');
    } catch {
      error = 'Invalid username or password.';
      loading = false;
    }
  }
</script>

<div class="fixed inset-0 bg-kobser-bg flex items-center justify-center p-4 z-50">
  <div class="w-full max-w-sm">
    <div class="flex items-center gap-3 justify-center mb-10">
      <div class="w-10 h-10 rounded-full bg-kobser-accent flex items-center justify-center text-kobser-bg shadow-[0_0_20px_rgba(255,159,28,0.5)]">
        <i class="ph-fill ph-vinyl-record text-2xl"></i>
      </div>
      <h1 class="font-london text-3xl text-kobser-text">kobser</h1>
    </div>
    <div class="bg-kobser-surface rounded-2xl p-8 border border-white/5 shadow-2xl">
      <h2 class="text-xl font-semibold mb-6 text-kobser-text">Sign in</h2>
      {#if error}
        <div class="mb-4 p-3 rounded-xl bg-red-500/10 text-red-400 text-sm">{error}</div>
      {/if}
      <form on:submit|preventDefault={doLogin}>
        <label class="block mb-4">
          <span class="block text-sm text-kobser-muted mb-1.5 font-medium">Username</span>
          <input
            type="text"
            bind:value={username}
            autocomplete="username"
            placeholder="Username"
            autofocus
            on:keydown={e => { if (e.key === 'Enter') passEl?.focus(); }}
            class="w-full bg-kobser-bg text-kobser-text placeholder-kobser-muted rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-kobser-accent/50 transition-all border border-white/10"
          >
        </label>
        <label class="block mb-6">
          <span class="block text-sm text-kobser-muted mb-1.5 font-medium">Password</span>
          <input
            type="password"
            bind:value={password}
            bind:this={passEl}
            autocomplete="current-password"
            class="w-full bg-kobser-bg text-kobser-text placeholder-kobser-muted rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-kobser-accent/50 transition-all border border-white/10"
          >
        </label>
        <button
          type="submit"
          disabled={loading}
          class="w-full py-3 bg-kobser-accent hover:bg-kobser-accentHover text-kobser-bg font-semibold rounded-xl transition-colors disabled:opacity-70"
        >
          {#if loading}
            <i class="ph ph-circle-notch animate-spin-slow mr-2"></i>Signing in…
          {:else}
            Sign in
          {/if}
        </button>
      </form>
    </div>
    <p class="text-center text-sm text-kobser-muted mt-6">Use your Navidrome credentials</p>
  </div>
</div>
