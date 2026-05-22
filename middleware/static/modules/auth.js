import { login, setSession } from "./api.js";

export function render(container, onSuccess) {
  container.innerHTML = `
    <div class="fixed inset-0 bg-peel-bg flex items-center justify-center p-4 z-50">
      <div class="w-full max-w-sm">
        <div class="flex items-center gap-3 justify-center mb-10">
          <div class="w-10 h-10 rounded-full bg-peel-accent flex items-center justify-center text-peel-bg shadow-[0_0_20px_rgba(255,159,28,0.5)]">
            <i class="ph-fill ph-vinyl-record text-2xl"></i>
          </div>
          <h1 class="text-3xl font-bold tracking-tight">peel</h1>
        </div>
        <div class="bg-peel-surface rounded-2xl p-8 border border-white/5 shadow-2xl">
          <h2 class="text-xl font-semibold mb-6">Sign in</h2>
          <div id="login-error" class="hidden mb-4 p-3 rounded-xl bg-red-500/10 text-red-400 text-sm"></div>
          <form id="login-form" onsubmit="return false">
            <label class="block mb-4">
              <span class="block text-sm text-peel-muted mb-1.5 font-medium">Username</span>
              <input type="text" id="login-user" autocomplete="username" placeholder="admin"
                     class="w-full bg-peel-bg text-peel-text placeholder-peel-muted rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 transition-all border border-white/10">
            </label>
            <label class="block mb-6">
              <span class="block text-sm text-peel-muted mb-1.5 font-medium">Password</span>
              <input type="password" id="login-pass" autocomplete="current-password"
                     class="w-full bg-peel-bg text-peel-text placeholder-peel-muted rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 transition-all border border-white/10">
            </label>
            <button id="login-btn" type="submit"
                    class="w-full py-3 bg-peel-accent hover:bg-peel-accentHover text-peel-bg font-semibold rounded-xl transition-colors">
              Sign in
            </button>
          </form>
        </div>
        <p class="text-center text-sm text-peel-muted mt-6">Use your Navidrome credentials</p>
      </div>
    </div>
  `;

  const userInput = container.querySelector("#login-user");
  const passInput = container.querySelector("#login-pass");
  const btn = container.querySelector("#login-btn");
  const errEl = container.querySelector("#login-error");

  userInput.focus();

  async function doLogin() {
    const username = userInput.value.trim();
    const password = passInput.value;
    if (!username || !password) return;

    btn.disabled = true;
    btn.innerHTML = '<i class="ph ph-circle-notch animate-spin-slow mr-2"></i>Signing in…';
    errEl.classList.add("hidden");

    try {
      const result = await login(username, password);
      setSession(result);
      onSuccess();
    } catch {
      errEl.textContent = "Invalid username or password.";
      errEl.classList.remove("hidden");
      btn.disabled = false;
      btn.textContent = "Sign in";
    }
  }

  container.querySelector("#login-form").addEventListener("submit", doLogin);
  userInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") passInput.focus();
  });
}
