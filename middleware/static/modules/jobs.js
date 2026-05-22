import { status as statusApi } from "./api.js";
import { escapeHtml } from "./util.js";

const jobs = [];
let pollTimer = null;
let viewRoot = null;

export function trackJob(jobId, artist, title) {
  jobs.unshift({ id: jobId, artist, title, status: "pending", error: null });
  ensurePolling();
  rerender();
}

function ensurePolling() {
  if (pollTimer) return;
  pollTimer = setInterval(async () => {
    let active = false;
    for (const j of jobs) {
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
    }
    rerender();
  }, 2000);
}

function rerender() {
  if (!viewRoot) return;
  const list = viewRoot.querySelector("#job-list");
  if (list) list.innerHTML = renderList();
}

export function render(root) {
  viewRoot = root;
  root.innerHTML = `
    <div class="w-full max-w-3xl mx-auto p-4 md:p-8">
      <h2 class="text-3xl font-bold mb-8 pl-2">Downloads</h2>
      <div id="job-list"></div>
    </div>
  `;
  root.querySelector("#job-list").innerHTML = renderList();
}

function statusClasses(status) {
  switch (status) {
    case "done":
      return "bg-peel-success text-peel-bg shadow-[0_0_8px_rgba(46,196,182,0.6)]";
    case "error":
      return "bg-red-500 text-white";
    case "downloading":
    case "tagging":
    case "scanning":
      return "bg-peel-accent text-peel-bg shadow-[0_0_8px_rgba(255,159,28,0.4)]";
    default:
      return "bg-white/10 text-peel-muted";
  }
}

function renderList() {
  if (!jobs.length) {
    return '<div class="flex flex-col items-center justify-center py-20 opacity-50"><i class="ph ph-download-simple text-6xl mb-4"></i><p class="text-lg">No downloads yet. Search and download from the Search tab.</p></div>';
  }
  return `<div class="flex flex-col gap-1">${jobs
    .map(
      (j) => `
    <div class="flex items-center justify-between p-4 rounded-xl hover:bg-white/5 transition-all border-b border-white/5">
      <div class="min-w-0 flex-1 mr-4">
        <p class="font-medium truncate">${escapeHtml(j.artist)} — ${escapeHtml(j.title)}</p>
        ${j.error ? `<p class="text-sm text-red-400 mt-1 truncate"><i class="ph ph-warning mr-1"></i>${escapeHtml(j.error)}</p>` : ""}
      </div>
      <span class="text-xs font-semibold px-4 py-1.5 rounded-full capitalize whitespace-nowrap ${statusClasses(j.status)}">${j.status}</span>
    </div>
  `,
    )
    .join("")}</div>`;
}
