import { library as libApi } from "./api.js";
import { escapeHtml } from "./util.js";
import { renderTrackList } from "./lib_tracks.js";
import * as player from "./player.js";

let allSongs = [];
let sortKey = "added-desc";

export async function render(root) {
  root.innerHTML = `
    <div class="w-full max-w-5xl mx-auto p-4 md:p-8">
      <div class="flex items-center justify-between mb-4 flex-wrap gap-3">
        <h2 class="font-london text-3xl pl-1">Library</h2>
        <div class="flex items-center gap-2" id="lib-actions"></div>
      </div>
      <div class="flex items-center gap-2 mb-4 flex-wrap">
        <div class="relative flex-1 min-w-48">
          <i class="ph ph-magnifying-glass absolute left-4 top-1/2 -translate-y-1/2 text-peel-muted"></i>
          <input type="text" id="lib-search" placeholder="Search library…"
                 class="w-full bg-peel-surface text-peel-text placeholder-peel-muted rounded-xl py-2.5 pl-11 pr-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10 text-sm">
        </div>
        <div id="sort-bar" class="flex items-center gap-2 overflow-x-auto no-scrollbar flex-shrink-0 pb-1 -mb-1"></div>
      </div>
      <div id="lib-list">
        <div class="flex items-center justify-center py-20 opacity-50">
          <i class="ph ph-circle-notch text-4xl animate-spin-slow"></i>
        </div>
      </div>
    </div>
  `;
  const list = root.querySelector("#lib-list");
  const searchInput = root.querySelector("#lib-search");

  try {
    allSongs = await fetchAllSongs();
    if (!allSongs.length) {
      list.innerHTML = `
        <div class="flex flex-col items-center justify-center py-20 opacity-50">
          <i class="ph ph-music-notes text-6xl mb-4"></i>
          <p class="text-lg">Your library is empty.</p>
          <p class="text-sm mt-1">Search YouTube and download tracks to get started.</p>
        </div>`;
      return;
    }

    // Action buttons
    const actions = root.querySelector("#lib-actions");
    actions.innerHTML = `
      <button id="lib-play-all" class="flex items-center gap-1.5 px-4 py-2 bg-peel-accent hover:bg-peel-accentHover text-peel-bg text-sm font-semibold rounded-xl transition-colors">
        <i class="ph-fill ph-play text-sm"></i> Play all
      </button>
      <button id="lib-shuffle" class="flex items-center gap-1.5 px-4 py-2 bg-white/5 hover:bg-white/10 text-sm font-medium rounded-xl transition-colors">
        <i class="ph ph-shuffle text-sm"></i> Shuffle
      </button>
    `;
    actions.querySelector("#lib-play-all").addEventListener("click", () => {
      const visible = currentSongs();
      if (visible.length) player.playQueue(visible, 0);
    });
    actions.querySelector("#lib-shuffle").addEventListener("click", () => {
      const visible = currentSongs();
      if (visible.length) player.playShuffled(visible);
    });

    let filterQuery = "";
    function currentSongs() {
      const f = filterQuery.toLowerCase();
      const base = f
        ? allSongs.filter((s) =>
            (s.title || "").toLowerCase().includes(f) ||
            (s.artist || "").toLowerCase().includes(f),
          )
        : allSongs;
      return sortSongs(base);
    }

    buildSortBar(root, list, currentSongs);
    applySortAndRender(list, currentSongs);

    searchInput.addEventListener("input", (e) => {
      filterQuery = e.target.value.trim();
      applySortAndRender(list, currentSongs);
    });

  } catch (e) {
    list.innerHTML = `
      <div class="flex flex-col items-center justify-center py-20 text-red-400">
        <i class="ph ph-warning-circle text-5xl mb-4"></i>
        <p>${escapeHtml(e.message)}</p>
      </div>`;
  }
}

const SORT_OPTIONS = [
  { key: "added-desc", label: "Recently added" },
  { key: "artist-asc", label: "Artist" },
];

const SORT_TOGGLES = [
  { keys: ["title-asc",    "title-desc"],    labels: ["A → Z",   "Z → A"]  },
  { keys: ["duration-asc", "duration-desc"], labels: ["Shortest", "Longest"] },
];

function sortSongs(songs) {
  const s = [...songs];
  switch (sortKey) {
    case "title-asc":      s.sort((a, b) => (a.title || "").localeCompare(b.title || "")); break;
    case "title-desc":     s.sort((a, b) => (b.title || "").localeCompare(a.title || "")); break;
    case "artist-asc":     s.sort((a, b) => (a.artist || "").localeCompare(b.artist || "") || (a.title || "").localeCompare(b.title || "")); break;
    case "duration-asc":   s.sort((a, b) => (a.duration || 0) - (b.duration || 0)); break;
    case "duration-desc":  s.sort((a, b) => (b.duration || 0) - (a.duration || 0)); break;
    case "added-desc":     s.sort((a, b) => (b.created || "").localeCompare(a.created || "")); break;
  }
  return s;
}

function buildSortBar(root, list, currentSongs) {
  const bar = root.querySelector("#sort-bar");

  const regularBtns = SORT_OPTIONS.map((o) =>
    `<button class="sort-btn flex-shrink-0 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
      sortKey === o.key
        ? "bg-peel-accent text-peel-bg"
        : "bg-white/5 text-peel-muted hover:bg-white/10 hover:text-peel-text"
    }" data-key="${o.key}">${o.label}</button>`
  );

  const toggleBtns = SORT_TOGGLES.map((t) => {
    const activeIdx = t.keys.indexOf(sortKey);
    const isActive = activeIdx !== -1;
    const label = isActive ? t.labels[activeIdx] : t.labels[0];
    const caret = isActive
      ? `<i class="ph ${activeIdx === 0 ? "ph-caret-up" : "ph-caret-down"} text-[9px] ml-0.5"></i>`
      : "";
    return `<button class="sort-toggle-btn flex-shrink-0 flex items-center px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
      isActive
        ? "bg-peel-accent text-peel-bg"
        : "bg-white/5 text-peel-muted hover:bg-white/10 hover:text-peel-text"
    }" data-keys="${t.keys.join(",")}">${label}${caret}</button>`;
  });

  bar.innerHTML = [...regularBtns, ...toggleBtns].join("");

  bar.querySelectorAll(".sort-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      sortKey = btn.dataset.key;
      buildSortBar(root, list, currentSongs);
      applySortAndRender(list, currentSongs);
    });
  });

  bar.querySelectorAll(".sort-toggle-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const keys = btn.dataset.keys.split(",");
      const activeIdx = keys.indexOf(sortKey);
      sortKey = activeIdx === -1 ? keys[0] : keys[(activeIdx + 1) % keys.length];
      buildSortBar(root, list, currentSongs);
      applySortAndRender(list, currentSongs);
    });
  });
}

function applySortAndRender(list, currentSongs) {
  renderTrackList(list, currentSongs(), {
    onRefresh: () => refreshLibrary(list, currentSongs),
  });
}

async function refreshLibrary(list, currentSongs) {
  try {
    allSongs = await fetchAllSongs();
    applySortAndRender(list, currentSongs);
  } catch (e) {
    console.error("library refresh failed:", e);
  }
}

async function fetchAllSongs() {
  const all = [];
  const pageSize = 500;
  let offset = 0;
  while (true) {
    const r = await libApi("search3", {
      query: " ",
      songCount: pageSize,
      songOffset: offset,
      artistCount: 0,
      albumCount: 0,
    });
    const batch = r.searchResult3?.song || [];
    all.push(...batch);
    if (batch.length < pageSize) break;
    offset += pageSize;
  }
  return all;
}
