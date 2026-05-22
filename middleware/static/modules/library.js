import { library as libApi } from "./api.js";
import { escapeHtml } from "./util.js";
import { renderTrackList } from "./lib_tracks.js";

let allSongs = [];
let sortKey = "title-asc";

export async function render(root) {
  root.innerHTML = `
    <div class="w-full max-w-5xl mx-auto p-4 md:p-8">
      <div class="flex items-center justify-between mb-6 flex-wrap gap-3">
        <h2 class="text-3xl font-bold pl-1">Library</h2>
        <div id="sort-bar" class="flex items-center gap-2 overflow-x-auto no-scrollbar pb-1 -mb-1"></div>
      </div>
      <div id="lib-list">
        <div class="flex items-center justify-center py-20 opacity-50">
          <i class="ph ph-circle-notch text-4xl animate-spin-slow"></i>
        </div>
      </div>
    </div>
  `;
  const list = root.querySelector("#lib-list");
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
    buildSortBar(root, list);
    applySortAndRender(list);
  } catch (e) {
    list.innerHTML = `
      <div class="flex flex-col items-center justify-center py-20 text-red-400">
        <i class="ph ph-warning-circle text-5xl mb-4"></i>
        <p>${escapeHtml(e.message)}</p>
      </div>`;
  }
}

const SORT_OPTIONS = [
  { key: "title-asc",    label: "A → Z" },
  { key: "title-desc",   label: "Z → A" },
  { key: "artist-asc",   label: "Artist" },
  { key: "duration-asc", label: "Shortest" },
  { key: "duration-desc",label: "Longest" },
  { key: "added-desc",   label: "Recently added" },
];

function buildSortBar(root, list) {
  const bar = root.querySelector("#sort-bar");
  bar.innerHTML = SORT_OPTIONS.map((o) =>
    `<button class="sort-btn flex-shrink-0 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
      sortKey === o.key
        ? "bg-peel-accent text-peel-bg"
        : "bg-white/5 text-peel-muted hover:bg-white/10 hover:text-peel-text"
    }" data-key="${o.key}">${o.label}</button>`
  ).join("");

  bar.querySelectorAll(".sort-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      sortKey = btn.dataset.key;
      buildSortBar(root, list);
      applySortAndRender(list);
    });
  });
}

function applySortAndRender(list) {
  const songs = [...allSongs];
  switch (sortKey) {
    case "title-asc":
      songs.sort((a, b) => (a.title || "").localeCompare(b.title || ""));
      break;
    case "title-desc":
      songs.sort((a, b) => (b.title || "").localeCompare(a.title || ""));
      break;
    case "artist-asc":
      songs.sort((a, b) => (a.artist || "").localeCompare(b.artist || "") || (a.title || "").localeCompare(b.title || ""));
      break;
    case "duration-asc":
      songs.sort((a, b) => (a.duration || 0) - (b.duration || 0));
      break;
    case "duration-desc":
      songs.sort((a, b) => (b.duration || 0) - (a.duration || 0));
      break;
    case "added-desc":
      songs.sort((a, b) => (b.created || "").localeCompare(a.created || ""));
      break;
  }
  renderTrackList(list, songs, {
    onRefresh: () => refreshLibrary(list),
  });
}

async function refreshLibrary(list) {
  try {
    allSongs = await fetchAllSongs();
    applySortAndRender(list);
  } catch (e) {
    console.error("library refresh failed:", e);
  }
}

async function fetchAllSongs() {
  let r = await libApi("search3", {
    query: "",
    songCount: 500,
    artistCount: 0,
    albumCount: 0,
  });
  let songs = r.searchResult3?.song || [];
  if (!songs.length) {
    r = await libApi("search3", {
      query: " ",
      songCount: 500,
      artistCount: 0,
      albumCount: 0,
    });
    songs = r.searchResult3?.song || [];
  }
  return songs;
}
