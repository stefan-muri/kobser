import { getSession, setSession, logout, library as libApi } from "./api.js";
import { escapeHtml } from "./util.js";

export function render(root) {
  const session = getSession();
  root.innerHTML = `
    <div class="w-full max-w-3xl mx-auto p-4 md:p-8">
      <h2 class="text-3xl font-bold mb-8 pl-2">Settings</h2>

      <div class="flex flex-col gap-6">
        <!-- Account -->
        <div class="bg-peel-surface rounded-2xl p-6">
          <h3 class="text-lg font-semibold mb-4 text-peel-accent">Account</h3>
          <div class="flex items-center justify-between py-2">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-full bg-peel-accent/20 flex items-center justify-center">
                <i class="ph-fill ph-user text-peel-accent text-lg"></i>
              </div>
              <div>
                <p class="font-medium">${escapeHtml(session?.username || "—")}</p>
                <p class="text-sm text-peel-muted">Navidrome account</p>
              </div>
            </div>
            <button id="logout-btn"
                    class="px-4 py-2 bg-white/5 hover:bg-red-500/10 hover:text-red-400 rounded-xl text-sm font-medium transition-colors">
              Sign out
            </button>
          </div>
        </div>

        <!-- Server Status -->
        <div class="bg-peel-surface rounded-2xl p-6">
          <h3 class="text-lg font-semibold mb-4 text-peel-accent">Server Status</h3>
          <div id="server-status" class="text-sm text-peel-muted">
            <div class="flex items-center gap-2">
              <i class="ph ph-circle-notch animate-spin-slow"></i> Checking…
            </div>
          </div>
          <button id="refresh-btn" class="mt-4 flex items-center gap-2 text-sm text-peel-muted hover:text-peel-text transition-colors">
            <i class="ph ph-arrows-clockwise"></i> Refresh
          </button>
        </div>

        <!-- Rescan -->
        <div class="bg-peel-surface rounded-2xl p-6">
          <h3 class="text-lg font-semibold mb-2 text-peel-accent">Library</h3>
          <p class="text-sm text-peel-muted mb-4">Trigger a Navidrome library scan to pick up any manually added files.</p>
          <button id="rescan-btn"
                  class="flex items-center gap-2 px-4 py-2 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors">
            <i class="ph ph-magnifying-glass"></i> Scan library
          </button>
          <p id="rescan-msg" class="text-sm mt-3 hidden"></p>
        </div>
      </div>
    </div>
  `;

  checkStatus(root.querySelector("#server-status"));

  root.querySelector("#refresh-btn").addEventListener("click", () =>
    checkStatus(root.querySelector("#server-status"))
  );

  root.querySelector("#logout-btn").addEventListener("click", async () => {
    try { await logout(); } catch { /* ignore */ }
    setSession(null);
    location.reload();
  });

  root.querySelector("#rescan-btn").addEventListener("click", async (e) => {
    const btn = e.currentTarget;
    const msg = root.querySelector("#rescan-msg");
    btn.disabled = true;
    btn.innerHTML = '<i class="ph ph-circle-notch animate-spin-slow mr-2"></i>Scanning…';
    msg.classList.add("hidden");
    try {
      await fetch("/api/rescan", {
        method: "POST",
        headers: { "X-Session-Id": getSession()?.sessionId || "" },
      });
      msg.className = "text-sm mt-3 text-peel-success";
      msg.textContent = "✓ Scan complete.";
      msg.classList.remove("hidden");
    } catch {
      msg.className = "text-sm mt-3 text-red-400";
      msg.textContent = "Scan failed.";
      msg.classList.remove("hidden");
    } finally {
      btn.disabled = false;
      btn.innerHTML = '<i class="ph ph-magnifying-glass mr-2"></i>Scan library';
    }
  });
}

async function checkStatus(el) {
  el.innerHTML = `<div class="flex items-center gap-2 text-sm text-peel-muted"><i class="ph ph-circle-notch animate-spin-slow"></i> Checking…</div>`;
  try {
    const r = await libApi("ping");
    el.innerHTML = `
      <div class="flex items-center gap-3 py-1">
        <div class="w-2.5 h-2.5 rounded-full bg-peel-success shadow-[0_0_8px_rgba(46,196,182,0.6)] flex-shrink-0"></div>
        <div>
          <p class="font-medium text-peel-text text-sm">Navidrome</p>
          <p class="text-xs text-peel-muted">Connected · v${escapeHtml(r.serverVersion || "?")}</p>
        </div>
      </div>`;
  } catch {
    el.innerHTML = `
      <div class="flex items-center gap-3 py-1">
        <div class="w-2.5 h-2.5 rounded-full bg-red-500 flex-shrink-0"></div>
        <div>
          <p class="font-medium text-peel-text text-sm">Navidrome</p>
          <p class="text-xs text-red-400">Unreachable</p>
        </div>
      </div>`;
  }
}
