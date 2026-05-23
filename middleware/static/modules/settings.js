import { getSession, setSession, logout, library as libApi, getStats } from "./api.js";
import { escapeHtml } from "./util.js";

export function render(root) {
  const session = getSession();
  root.innerHTML = `
    <div class="w-full max-w-3xl mx-auto p-4 md:p-8">
      <h2 class="font-london text-3xl mb-8 pl-2">Settings</h2>

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

        <!-- System Stats -->
        <div class="bg-peel-surface rounded-2xl p-6">
          <div class="flex items-center justify-between mb-5">
            <h3 class="text-lg font-semibold text-peel-accent">System</h3>
            <button id="stats-refresh-btn" class="flex items-center gap-1.5 text-sm text-peel-muted hover:text-peel-text transition-colors">
              <i class="ph ph-arrows-clockwise"></i> Refresh
            </button>
          </div>
          <div id="system-stats">
            <div class="flex items-center gap-2 text-sm text-peel-muted">
              <i class="ph ph-circle-notch animate-spin-slow"></i> Loading…
            </div>
          </div>
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
  loadStats(root.querySelector("#system-stats"));

  root.querySelector("#refresh-btn").addEventListener("click", () =>
    checkStatus(root.querySelector("#server-status"))
  );

  root.querySelector("#stats-refresh-btn").addEventListener("click", () =>
    loadStats(root.querySelector("#system-stats"))
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

async function loadStats(el) {
  el.innerHTML = `<div class="flex items-center gap-2 text-sm text-peel-muted"><i class="ph ph-circle-notch animate-spin-slow"></i> Loading…</div>`;
  try {
    const s = await getStats();
    el.innerHTML = `
      <div class="flex flex-col gap-5">
        ${statBar("Storage", s.disk.used, s.disk.total, "ph-hard-drive")}
        ${statBar("Memory", s.ram.used, s.ram.total, "ph-cpu")}
        <div>
          <div class="flex items-center justify-between mb-1.5">
            <span class="flex items-center gap-1.5 text-sm font-medium">
              <i class="ph ph-activity text-peel-muted"></i> CPU
            </span>
            <span class="text-sm font-semibold">${s.cpu_percent.toFixed(1)}%</span>
          </div>
          <div class="w-full h-2 bg-white/10 rounded-full overflow-hidden">
            <div class="h-full rounded-full transition-all ${s.cpu_percent > 80 ? "bg-red-400" : "bg-peel-accent"}"
                 style="width:${Math.min(s.cpu_percent, 100).toFixed(1)}%"></div>
          </div>
        </div>
      </div>`;
  } catch (e) {
    el.innerHTML = `<p class="text-sm text-red-400">Could not load stats: ${escapeHtml(e.message)}</p>`;
  }
}

function statBar(label, used, total, icon) {
  const pct = total > 0 ? (used / total) * 100 : 0;
  const color = pct > 85 ? "bg-red-400" : pct > 65 ? "bg-yellow-400" : "bg-peel-accent";
  return `
    <div>
      <div class="flex items-center justify-between mb-1.5">
        <span class="flex items-center gap-1.5 text-sm font-medium">
          <i class="ph ${icon} text-peel-muted"></i> ${label}
        </span>
        <span class="text-sm font-semibold">${fmtBytes(used)} / ${fmtBytes(total)}</span>
      </div>
      <div class="w-full h-2 bg-white/10 rounded-full overflow-hidden">
        <div class="h-full rounded-full transition-all ${color}" style="width:${pct.toFixed(1)}%"></div>
      </div>
      <p class="text-xs text-peel-muted mt-1">${fmtBytes(total - used)} free</p>
    </div>`;
}

function fmtBytes(bytes) {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let i = 0;
  let v = bytes;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return `${v.toFixed(i > 1 ? 1 : 0)} ${units[i]}`;
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
