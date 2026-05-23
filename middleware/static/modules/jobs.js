import { status as statusApi, listDownloads, deleteDownload as apiDeleteDownload, cancelJob as apiCancelJob } from "./api.js";
import { escapeHtml } from "./util.js";

const activeJobs = [];   // in-memory: jobs being polled this session
let pollTimer = null;
let viewRoot = null;
let dbDownloads = [];    // loaded from backend

export function trackJob(jobId, artist, title) {
  activeJobs.unshift({ id: jobId, artist, title, status: "pending", error: null });
  ensurePolling();
  rerender();
}

function ensurePolling() {
  if (pollTimer) return;
  pollTimer = setInterval(async () => {
    let active = false;
    for (const j of activeJobs) {
      if (j.status === "done" || j.status === "error") continue;
      active = true;
      try {
        const s = await statusApi(j.id);
        j.status = s.status;
        j.error = s.error;
      } catch (e) {
        j.error = e.message;
      }
    }
    if (!active) {
      clearInterval(pollTimer);
      pollTimer = null;
      // Reload DB list now that jobs have settled
      loadDbDownloads();
    }
    rerender();
  }, 2000);
}

async function loadDbDownloads() {
  try {
    const r = await listDownloads();
    dbDownloads = r.downloads || [];
  } catch { /* non-critical */ }
  rerender();
}

function mergedList() {
  const activeIds = new Set(activeJobs.map((j) => j.id));
  return [
    ...activeJobs,
    ...dbDownloads.filter((d) => !activeIds.has(d.id)),
  ];
}

function rerender() {
  if (!viewRoot) return;
  const list = viewRoot.querySelector("#job-list");
  if (list) renderList(list);
}

export function render(root) {
  viewRoot = root;
  root.innerHTML = `
    <div class="w-full max-w-3xl mx-auto p-4 md:p-8">
      <h2 class="font-london text-3xl mb-8 pl-2">Downloads</h2>
      <div id="job-list"></div>
    </div>
  `;
  loadDbDownloads();
}

function renderList(list) {
  const all = mergedList();
  if (!all.length) {
    list.innerHTML = `
      <div class="flex flex-col items-center justify-center py-20 opacity-50">
        <i class="ph ph-download-simple text-6xl mb-4"></i>
        <p class="text-lg">No downloads yet. Search and download from the Search tab.</p>
      </div>`;
    return;
  }

  list.innerHTML = `<div class="flex flex-col gap-1">${all.map(rowHtml).join("")}</div>`;

  list.querySelectorAll(".dl-delete-btn").forEach((btn) => {
    btn.addEventListener("click", async (e) => {
      e.stopPropagation();
      const id = btn.dataset.id;
      try {
        await apiDeleteDownload(id);
        dbDownloads = dbDownloads.filter((d) => d.id !== id);
        const idx = activeJobs.findIndex((j) => j.id === id);
        if (idx !== -1) activeJobs.splice(idx, 1);
        rerender();
      } catch (err) {
        alert(`Delete failed: ${err.message}`);
      }
    });
  });

  list.querySelectorAll(".dl-cancel-btn").forEach((btn) => {
    btn.addEventListener("click", async (e) => {
      e.stopPropagation();
      const id = btn.dataset.id;
      btn.disabled = true;
      try {
        await apiCancelJob(id);
        const j = activeJobs.find((j) => j.id === id);
        if (j) j.status = "cancelled";
        rerender();
      } catch (err) {
        btn.disabled = false;
        alert(`Cancel failed: ${err.message}`);
      }
    });
  });
}

const ACTIVE_STATUSES = new Set(["pending", "downloading", "tagging", "scanning"]);

function rowHtml(j) {
  const meta = [];
  if (j.started_at) meta.push(fmtDate(j.started_at));
  if (j.completed_at && j.started_at) meta.push(`took ${fmtDuration(j.completed_at - j.started_at)}`);
  if (j.file_size_bytes) meta.push(fmtBytes(j.file_size_bytes));

  const isActive = ACTIVE_STATUSES.has(j.status);
  const actionBtn = isActive
    ? `<button class="dl-cancel-btn px-3 py-1 rounded-lg text-xs font-medium text-peel-muted hover:text-red-400 hover:bg-red-500/10 transition-colors flex-shrink-0"
              data-id="${escapeHtml(j.id)}" title="Cancel">Cancel</button>`
    : `<button class="dl-delete-btn w-8 h-8 rounded-full flex items-center justify-center text-peel-muted hover:text-red-400 hover:bg-red-500/10 transition-colors flex-shrink-0"
              data-id="${escapeHtml(j.id)}" title="Remove">
        <i class="ph ph-trash text-sm pointer-events-none"></i>
      </button>`;

  return `
    <div class="flex items-center gap-4 p-4 rounded-xl hover:bg-white/5 transition-all border-b border-white/5">
      <div class="min-w-0 flex-1">
        <p class="font-medium truncate">${escapeHtml(j.artist)} — ${escapeHtml(j.title)}</p>
        ${meta.length ? `<p class="text-xs text-peel-muted mt-0.5">${meta.map(escapeHtml).join(" · ")}</p>` : ""}
        ${j.error && j.status !== "cancelled" ? `<p class="text-xs text-red-400 mt-1 truncate"><i class="ph ph-warning mr-1"></i>${escapeHtml(j.error)}</p>` : ""}
      </div>
      <span class="text-xs font-semibold px-3 py-1.5 rounded-full capitalize whitespace-nowrap flex-shrink-0 ${statusClasses(j.status)}">${escapeHtml(j.status)}</span>
      ${actionBtn}
    </div>
  `;
}

function statusClasses(status) {
  switch (status) {
    case "done":     return "bg-peel-success/20 text-peel-success";
    case "error":      return "bg-red-500/20 text-red-400";
    case "cancelled":  return "bg-white/10 text-peel-muted";
    case "downloading":
    case "tagging":
    case "scanning": return "bg-peel-accent/20 text-peel-accent";
    default:         return "bg-white/10 text-peel-muted";
  }
}

function fmtDate(ts) {
  return new Date(ts * 1000).toLocaleDateString(undefined, {
    month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
  });
}

function fmtDuration(secs) {
  if (!secs || secs < 1) return "<1s";
  if (secs < 60) return `${Math.round(secs)}s`;
  return `${Math.floor(secs / 60)}m ${Math.round(secs % 60)}s`;
}

function fmtBytes(bytes) {
  if (!bytes) return "";
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
