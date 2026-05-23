import { library as libApi } from "./api.js";
import { escapeHtml } from "./util.js";
import { renderTrackList } from "./lib_tracks.js";
import * as player from "./player.js";

export async function render(root) {
  root.innerHTML = `
    <div class="w-full max-w-5xl mx-auto p-4 md:p-8">
      <div class="flex items-center justify-between mb-6 flex-wrap gap-3">
        <h2 class="font-london text-3xl pl-1">Favorites</h2>
        <div class="flex items-center gap-2" id="fav-actions"></div>
      </div>
      <div class="mb-4" id="fav-search-wrap" style="display:none">
        <div class="relative">
          <i class="ph ph-magnifying-glass absolute left-4 top-1/2 -translate-y-1/2 text-peel-muted"></i>
          <input type="text" id="fav-search" placeholder="Search favorites…"
                 class="w-full bg-peel-surface text-peel-text placeholder-peel-muted rounded-xl py-2.5 pl-11 pr-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10 text-sm">
        </div>
      </div>
      <div id="lib-list">
        <div class="flex items-center justify-center py-20 opacity-50">
          <i class="ph ph-circle-notch text-4xl animate-spin-slow"></i>
        </div>
      </div>
    </div>
  `;
  const list = root.querySelector("#lib-list");
  const actions = root.querySelector("#fav-actions");
  const searchWrap = root.querySelector("#fav-search-wrap");
  const searchInput = root.querySelector("#fav-search");

  try {
    const r = await libApi("getStarred2");
    const songs = r.starred2?.song || [];
    if (!songs.length) {
      list.innerHTML = `
        <div class="flex flex-col items-center justify-center py-20 opacity-50">
          <i class="ph ph-heart text-6xl mb-4"></i>
          <p class="text-lg">No favorites yet.</p>
          <p class="text-sm mt-1">Heart a track in Library to add it here.</p>
        </div>`;
      return;
    }

    // Action buttons
    actions.innerHTML = `
      <button id="fav-play-all" class="flex items-center gap-1.5 px-4 py-2 bg-peel-accent hover:bg-peel-accentHover text-peel-bg text-sm font-semibold rounded-xl transition-colors">
        <i class="ph-fill ph-play text-sm"></i> Play all
      </button>
      <button id="fav-shuffle" class="flex items-center gap-1.5 px-4 py-2 bg-white/5 hover:bg-white/10 text-sm font-medium rounded-xl transition-colors">
        <i class="ph ph-shuffle text-sm"></i> Shuffle
      </button>
    `;
    actions.querySelector("#fav-play-all").addEventListener("click", () => {
      const visible = currentSongs();
      if (visible.length) player.playQueue(visible, 0);
    });
    actions.querySelector("#fav-shuffle").addEventListener("click", () => {
      const visible = currentSongs();
      if (visible.length) player.playShuffled(visible);
    });

    searchWrap.style.display = "";

    let filteredSongs = songs;
    function currentSongs() { return filteredSongs; }

    function applyFilter(q) {
      const f = q.toLowerCase();
      filteredSongs = f
        ? songs.filter((s) =>
            (s.title || "").toLowerCase().includes(f) ||
            (s.artist || "").toLowerCase().includes(f),
          )
        : songs;
      renderTrackList(list, filteredSongs, {
        allStarred: true,
        onRefresh: () => render(root),
      });
    }

    applyFilter("");
    searchInput.addEventListener("input", (e) => applyFilter(e.target.value.trim()));

  } catch (e) {
    list.innerHTML = `
      <div class="flex flex-col items-center justify-center py-20 text-red-400">
        <i class="ph ph-warning-circle text-5xl mb-4"></i>
        <p>${escapeHtml(e.message)}</p>
      </div>`;
  }
}
