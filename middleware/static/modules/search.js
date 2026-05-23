import { search as searchApi, download as downloadApi, previewUrl } from "./api.js";
import * as player from "./player.js";
import { trackJob } from "./jobs.js";
import { escapeHtml, fmtDuration } from "./util.js";

let lastResults = [];

const HISTORY_KEY = "peel:search-history";
const MAX_HISTORY = 20;

function getHistory() {
  try { return JSON.parse(localStorage.getItem(HISTORY_KEY) || "[]"); } catch { return []; }
}
function saveQuery(q) {
  const h = getHistory().filter((x) => x !== q);
  h.unshift(q);
  localStorage.setItem(HISTORY_KEY, JSON.stringify(h.slice(0, MAX_HISTORY)));
}
function removeQuery(q) {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(getHistory().filter((x) => x !== q)));
}

export function render(root) {
  root.innerHTML = `
    <div class="w-full max-w-5xl mx-auto p-4 md:p-8 animate-slide-up">
      <div class="sticky top-0 bg-peel-bg/90 backdrop-blur-md pt-2 pb-6 z-10">
        <div class="relative w-full shadow-lg shadow-black/20 rounded-2xl flex gap-2">
          <div class="relative flex-1">
            <i class="ph ph-magnifying-glass absolute left-5 top-1/2 transform -translate-y-1/2 text-peel-muted text-xl"></i>
            <input type="text" id="search-q" placeholder="Search for artists, albums, or tracks..."
                   class="w-full bg-peel-surface text-peel-text placeholder-peel-muted rounded-2xl py-5 pl-14 pr-6 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 transition-all text-lg font-medium">
            <div id="search-history-drop"
                 class="hidden absolute top-full left-0 right-0 mt-2 bg-peel-surface border border-white/10 rounded-2xl shadow-2xl z-20 overflow-hidden">
            </div>
          </div>
          <button id="search-btn" class="bg-peel-accent hover:bg-peel-accentHover text-peel-bg font-semibold rounded-2xl px-6 transition-colors flex items-center gap-2 shrink-0">
            <i class="ph-bold ph-magnifying-glass text-lg"></i>
            Search
          </button>
        </div>
      </div>

      <div class="mt-4">
        <h2 class="text-xl font-semibold mb-4 pl-2" id="search-heading">Results</h2>
        <div class="flex flex-col gap-2" id="search-results"></div>
      </div>
    </div>
  `;

  const input = root.querySelector("#search-q");
  const btn = root.querySelector("#search-btn");
  const out = root.querySelector("#search-results");
  const histDrop = root.querySelector("#search-history-drop");

  function renderHistoryDrop() {
    const h = getHistory();
    if (!h.length) { histDrop.classList.add("hidden"); return; }
    histDrop.innerHTML = `
      <div class="p-2">
        <div class="flex items-center justify-between px-3 py-1.5 mb-1">
          <span class="text-xs text-peel-muted font-semibold uppercase tracking-wide">Recent searches</span>
          <button id="hist-clear-all" class="text-xs text-peel-muted hover:text-peel-text transition-colors">Clear all</button>
        </div>
        ${h.map((q) => `
          <div class="hist-item flex items-center gap-3 px-3 py-2.5 rounded-xl hover:bg-white/5 cursor-pointer group" data-q="${escapeHtml(q)}">
            <i class="ph ph-clock-counter-clockwise text-peel-muted text-base flex-shrink-0"></i>
            <span class="flex-1 text-sm truncate">${escapeHtml(q)}</span>
            <button class="hist-del w-6 h-6 flex items-center justify-center rounded-full opacity-0 group-hover:opacity-100 hover:bg-white/10 transition-all flex-shrink-0" data-q="${escapeHtml(q)}">
              <i class="ph ph-x text-xs pointer-events-none"></i>
            </button>
          </div>`).join("")}
      </div>`;
    histDrop.classList.remove("hidden");

    histDrop.querySelectorAll(".hist-item").forEach((item) => {
      item.addEventListener("click", (e) => {
        if (e.target.closest(".hist-del")) return;
        input.value = item.dataset.q;
        histDrop.classList.add("hidden");
        go();
      });
    });
    histDrop.querySelectorAll(".hist-del").forEach((b) => {
      b.addEventListener("click", (e) => {
        e.stopPropagation();
        removeQuery(b.dataset.q);
        renderHistoryDrop();
      });
    });
    histDrop.querySelector("#hist-clear-all")?.addEventListener("click", () => {
      localStorage.removeItem(HISTORY_KEY);
      histDrop.classList.add("hidden");
    });
  }

  function showHistory() { if (!input.value.trim()) renderHistoryDrop(); }
  function hideHistory() { histDrop.classList.add("hidden"); }

  input.addEventListener("focus", showHistory);
  input.addEventListener("input", () => { input.value.trim() ? hideHistory() : showHistory(); });
  input.addEventListener("keydown", (e) => { if (e.key === "Escape") hideHistory(); });
  document.addEventListener("click", (e) => {
    if (!histDrop.contains(e.target) && e.target !== input) hideHistory();
  });

  async function go() {
    const q = input.value.trim();
    if (!q) return;
    hideHistory();
    saveQuery(q);
    out.innerHTML = '<div class="flex flex-col items-center justify-center py-20 opacity-50"><i class="ph ph-circle-notch text-4xl mb-4 animate-spin-slow"></i><p class="text-lg">Searching…</p></div>';
    root.querySelector("#search-heading").textContent = `Searching for "${q}"`;
    try {
      lastResults = await searchApi(q, 15);
      root.querySelector("#search-heading").textContent = lastResults.length
        ? `Results for "${q}"`
        : `No results for "${q}"`;
      out.innerHTML =
        lastResults.map(renderResult).join("") ||
        '<div class="flex flex-col items-center justify-center py-20 opacity-50"><i class="ph ph-magnifying-glass text-6xl mb-4"></i><p class="text-lg">No tracks found. Try a different search.</p></div>';

      out.querySelectorAll(".search-row").forEach((row, i) => {
        row.addEventListener("click", (e) => {
          if (e.target.closest(".dl-btn")) return;
          playPreview(lastResults[i]);
        });
      });
      out.querySelectorAll("[data-vid]").forEach((el) => {
        el.addEventListener("click", (e) => {
          e.stopPropagation();
          openDialog(el.dataset.vid);
        });
      });
    } catch (e) {
      out.innerHTML = `<div class="flex flex-col items-center justify-center py-20 text-red-400"><i class="ph ph-warning-circle text-6xl mb-4"></i><p class="text-lg">Search failed: ${escapeHtml(e.message)}</p></div>`;
    }
  }

  btn.addEventListener("click", go);
  input.addEventListener("keydown", (e) => { if (e.key === "Enter") go(); });
  input.focus();
}

function renderResult(r) {
  return `
    <div class="search-row group flex items-center gap-4 p-3 rounded-xl hover:bg-white/5 transition-all w-full cursor-pointer">
      <div class="relative w-14 h-14 rounded-md overflow-hidden flex-shrink-0">
        <img src="${escapeHtml(r.thumbnail || "")}" alt="" class="w-full h-full object-cover" referrerpolicy="no-referrer" loading="lazy">
        <div class="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
          <i class="ph-fill ph-play text-white text-xl pointer-events-none"></i>
        </div>
      </div>
      <div class="flex-1 min-w-0 pr-4">
        <h3 class="font-medium text-base truncate text-peel-text">${escapeHtml(r.title || "Untitled")}</h3>
        <p class="text-sm text-peel-muted truncate">${escapeHtml(r.channel || "—")} · ${fmtDuration(r.duration)}</p>
      </div>
      <div class="flex items-center gap-3 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity pr-2">
        <button class="dl-btn w-10 h-10 rounded-full flex items-center justify-center hover:bg-white/10 transition-colors text-peel-muted hover:text-white"
                data-vid="${escapeHtml(r.videoId)}" title="Download to library">
          <i class="ph ph-download-simple text-xl pointer-events-none"></i>
        </button>
      </div>
    </div>
  `;
}

function playPreview(result) {
  // Build a fake "track" so the player can show title/art and play the preview stream.
  const fakeTrack = {
    id: null,
    title: result.title || "Unknown",
    artist: result.channel || "YouTube",
    coverArt: null,
    _previewUrl: previewUrl(result.videoId),
    _previewThumb: result.thumbnail || "",
    duration: result.duration || 0,
  };
  player.playPreviewTrack(fakeTrack);
}

function openDialog(videoId) {
  const result = lastResults.find((r) => r.videoId === videoId);
  if (!result) return;
  const { artist, title } = parseTitle(result.title || "");
  const d = document.getElementById("download-dialog");
  const artistInput = d.querySelector("#dl-artist");
  const titleInput = d.querySelector("#dl-title");
  artistInput.value = artist;
  titleInput.value = title;
  d.querySelector("#dl-confirm").onclick = (e) => {
    const a = artistInput.value.trim();
    const t = titleInput.value.trim();
    if (!a || !t) {
      e.preventDefault();
      return;
    }
    triggerDownload(videoId, a, t);
  };
  d.showModal();
}

async function triggerDownload(videoId, artist, title) {
  try {
    const { jobId } = await downloadApi(videoId, artist, title);
    trackJob(jobId, artist, title);
    showToast(`Downloading '${title}'...`, "info");
  } catch (e) {
    showToast(`Download failed: ${e.message}`, "error");
  }
}

function parseTitle(t) {
  const cleaned = t
    .replace(/\(Official[^)]*\)/gi, "")
    .replace(/\[Official[^\]]*\]/gi, "")
    .replace(/\(Lyrics?[^)]*\)/gi, "")
    .replace(/\(Audio\)/gi, "")
    .replace(/\(Video\)/gi, "")
    .replace(/\(HD\)/gi, "")
    .replace(/\(\d{4}\)/g, "")
    .replace(/\s+/g, " ")
    .trim();
  const parts = cleaned.split(" - ");
  if (parts.length >= 2) {
    return {
      artist: parts[0].trim(),
      title: parts.slice(1).join(" - ").trim(),
    };
  }
  return { artist: "", title: cleaned };
}

function showToast(message, type = "info") {
  const container = document.getElementById("toast-container");
  if (!container) return;

  const toast = document.createElement("div");
  toast.className = "bg-peel-surface border border-white/10 shadow-2xl rounded-xl px-4 py-3 flex items-center gap-3 animate-slide-up max-w-sm pointer-events-auto";

  let iconHtml = "";
  if (type === "success") {
    iconHtml = '<div class="w-8 h-8 rounded-full bg-peel-success/20 flex items-center justify-center text-peel-success"><i class="ph-bold ph-check"></i></div>';
  } else if (type === "error") {
    iconHtml = '<div class="w-8 h-8 rounded-full bg-red-500/20 flex items-center justify-center text-red-400"><i class="ph-bold ph-warning"></i></div>';
  } else {
    iconHtml = '<div class="w-8 h-8 rounded-full bg-peel-accent/20 flex items-center justify-center text-peel-accent"><i class="ph-bold ph-info"></i></div>';
  }

  toast.innerHTML = `${iconHtml}<p class="text-sm font-medium text-white">${escapeHtml(message)}</p>`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.classList.add("toast-exit");
    setTimeout(() => {
      if (container.contains(toast)) container.removeChild(toast);
    }, 300);
  }, 3000);
}
