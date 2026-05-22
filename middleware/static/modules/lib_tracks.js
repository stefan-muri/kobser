import { library as libApi, deleteTrack as apiDeleteTrack, coverArtUrl } from "./api.js";
import * as player from "./player.js";
import * as contextMenu from "./context_menu.js";
import { escapeHtml, fmtDuration } from "./util.js";

/**
 * Renders a flat list of Subsonic song objects into `list`.
 * opts:
 *   allStarred  – treat all rows as starred (liked songs view)
 *   playlistId  – if set, "Remove from playlist" appears in context menu
 *   onRefresh   – called with no args after destructive actions (remove/delete)
 */
export function renderTrackList(list, songs, { allStarred = false, playlistId = null, onRefresh = null } = {}) {
  list.innerHTML = `
    <div id="tl-header" class="flex items-center justify-end mb-2 px-1 gap-2 min-h-[36px]">
      <button id="tl-select-btn"
              class="flex items-center gap-1.5 px-3 py-1.5 bg-white/5 hover:bg-white/10 rounded-lg text-xs font-medium text-peel-muted transition-colors">
        <i class="ph ph-check-square text-sm"></i> Select
      </button>
    </div>
    <div id="tl-action-bar"
         class="hidden flex items-center gap-2 mb-2 px-1 flex-wrap">
      <span id="tl-sel-count" class="text-sm text-peel-muted mr-1"></span>
      <button id="tl-sel-add"
              class="flex items-center gap-1.5 px-3 py-1.5 bg-white/10 hover:bg-white/20 rounded-lg text-xs font-medium transition-colors">
        <i class="ph ph-playlist-plus text-sm"></i> Add to playlist
      </button>
      ${playlistId ? `
      <button id="tl-sel-remove"
              class="flex items-center gap-1.5 px-3 py-1.5 bg-white/10 hover:bg-white/20 rounded-lg text-xs font-medium transition-colors">
        <i class="ph ph-minus-circle text-sm"></i> Remove selected
      </button>` : ""}
      <button id="tl-sel-delete"
              class="flex items-center gap-1.5 px-3 py-1.5 bg-red-500/20 hover:bg-red-500/30 text-red-400 rounded-lg text-xs font-medium transition-colors">
        <i class="ph ph-trash text-sm"></i> Delete
      </button>
      <button id="tl-sel-cancel"
              class="ml-auto flex items-center gap-1.5 px-3 py-1.5 bg-white/5 hover:bg-white/10 rounded-lg text-xs font-medium transition-colors">
        Cancel
      </button>
    </div>
    <div class="flex flex-col gap-1" id="track-list"></div>
  `;

  const container = list.querySelector("#track-list");
  let selectMode = false;
  const selected = new Set(); // indices

  function getSelectedIds() {
    return [...selected].map((i) => songs[i].id);
  }

  function updateActionBar() {
    const n = selected.size;
    list.querySelector("#tl-sel-count").textContent = `${n} selected`;
    ["tl-sel-add", "tl-sel-delete"].forEach((id) => {
      const el = list.querySelector(`#${id}`);
      if (el) el.disabled = n === 0;
    });
    const removeBtn = list.querySelector("#tl-sel-remove");
    if (removeBtn) removeBtn.disabled = n === 0;
  }

  function enterSelectMode() {
    selectMode = true;
    selected.clear();
    list.querySelector("#tl-header").classList.add("hidden");
    list.querySelector("#tl-action-bar").classList.remove("hidden");
    list.querySelector("#tl-action-bar").classList.add("flex");
    container.querySelectorAll(".tl-checkbox-wrap").forEach((w) => w.classList.remove("hidden"));
    updateActionBar();
  }

  function exitSelectMode() {
    selectMode = false;
    selected.clear();
    list.querySelector("#tl-header").classList.remove("hidden");
    list.querySelector("#tl-action-bar").classList.add("hidden");
    list.querySelector("#tl-action-bar").classList.remove("flex");
    container.querySelectorAll(".tl-checkbox-wrap").forEach((w) => w.classList.add("hidden"));
    container.querySelectorAll(".tl-checkbox").forEach((cb) => { cb.checked = false; });
  }

  // Build rows
  songs.forEach((s, i) => {
    const starred = allStarred || !!s.starred;
    const row = document.createElement("div");
    row.className =
      "group flex items-center gap-3 p-3 rounded-xl hover:bg-white/5 transition-all cursor-pointer";
    row.innerHTML = `
      <div class="tl-checkbox-wrap hidden flex-shrink-0 w-5 flex items-center justify-center">
        <input type="checkbox" class="tl-checkbox w-4 h-4 rounded accent-peel-accent cursor-pointer">
      </div>
      <div class="relative w-12 h-12 rounded-md overflow-hidden flex-shrink-0 bg-peel-surface">
        <img src="${escapeHtml(coverArtUrl(s.coverArt, 64))}" alt="" loading="lazy"
             class="w-full h-full object-cover">
        <div class="tl-play-overlay absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
          <i class="ph-fill ph-play text-white text-lg pointer-events-none"></i>
        </div>
      </div>
      <div class="flex-1 min-w-0">
        <p class="font-medium truncate">${escapeHtml(s.title)}</p>
        <p class="text-sm text-peel-muted truncate">${escapeHtml(s.artist || "—")} · ${fmtDuration(s.duration)}</p>
      </div>
      <button class="star-btn w-9 h-9 rounded-full flex items-center justify-center hover:bg-white/10 transition-all opacity-100 md:opacity-0 group-hover:opacity-100"
              data-id="${escapeHtml(s.id)}" data-starred="${starred}" title="${starred ? "Unlike" : "Like"}">
        <i class="${starred ? "ph-fill ph-heart text-peel-accent" : "ph ph-heart text-peel-muted"} text-lg pointer-events-none"></i>
      </button>
      <button class="dots-btn w-9 h-9 rounded-full flex items-center justify-center hover:bg-white/10 transition-all opacity-100 md:opacity-0 group-hover:opacity-100"
              title="More options">
        <i class="ph ph-dots-three-vertical text-peel-muted text-lg pointer-events-none"></i>
      </button>
    `;

    // Row click → play (unless in select mode or clicking a button)
    row.addEventListener("click", (e) => {
      if (e.target.closest(".star-btn") || e.target.closest(".dots-btn")) return;
      if (selectMode) {
        const cb = row.querySelector(".tl-checkbox");
        cb.checked = !cb.checked;
        if (cb.checked) selected.add(i); else selected.delete(i);
        updateActionBar();
        return;
      }
      player.playQueue(songs, i);
    });

    row.querySelector(".star-btn").addEventListener("click", (e) => {
      e.stopPropagation();
      toggleStar(e.currentTarget);
    });

    row.querySelector(".dots-btn").addEventListener("click", (e) => {
      e.stopPropagation();
      const isStarred = row.querySelector(".star-btn").dataset.starred === "true";
      contextMenu.show(e.currentTarget, [
        {
          label: "Play",
          icon: "ph ph-play",
          action: () => player.playQueue(songs, i),
        },
        {
          label: "Add to playlist",
          icon: "ph ph-playlist-plus",
          action: () => showPlaylistPicker([s.id]),
        },
        {
          label: isStarred ? "Unlike" : "Like",
          icon: isStarred ? "ph-fill ph-heart" : "ph ph-heart",
          action: () => toggleStar(row.querySelector(".star-btn")),
        },
        ...(playlistId
          ? [
              {
                label: "Remove from playlist",
                icon: "ph ph-minus-circle",
                action: async () => {
                  await libApi("updatePlaylist", { playlistId, songIndexToRemove: i });
                  onRefresh?.();
                },
              },
            ]
          : []),
        null,
        {
          label: "Delete from library",
          icon: "ph ph-trash",
          danger: true,
          action: async () => {
            if (!confirm(`Delete "${s.title}"? This cannot be undone.`)) return;
            try {
              await apiDeleteTrack(s.id);
              onRefresh?.();
            } catch (err) {
              alert(`Delete failed: ${err.message}`);
            }
          },
        },
      ]);
    });

    row.querySelector(".tl-checkbox").addEventListener("change", (e) => {
      if (e.target.checked) selected.add(i); else selected.delete(i);
      updateActionBar();
    });

    container.appendChild(row);
  });

  // Header: enter select mode
  list.querySelector("#tl-select-btn").addEventListener("click", enterSelectMode);

  // Action bar: cancel
  list.querySelector("#tl-sel-cancel").addEventListener("click", exitSelectMode);

  // Action bar: add to playlist
  list.querySelector("#tl-sel-add").addEventListener("click", () => {
    const ids = getSelectedIds();
    if (!ids.length) return;
    showPlaylistPicker(ids, exitSelectMode);
  });

  // Action bar: remove from playlist (playlist view only)
  const selRemove = list.querySelector("#tl-sel-remove");
  if (selRemove) {
    selRemove.addEventListener("click", async () => {
      const indices = [...selected].sort((a, b) => b - a); // descending so indices stay valid
      if (!indices.length) return;
      for (const idx of indices) {
        await libApi("updatePlaylist", { playlistId, songIndexToRemove: idx });
      }
      exitSelectMode();
      onRefresh?.();
    });
  }

  // Action bar: delete from library
  list.querySelector("#tl-sel-delete").addEventListener("click", async () => {
    const ids = getSelectedIds();
    if (!ids.length) return;
    if (!confirm(`Delete ${ids.length} track${ids.length > 1 ? "s" : ""}? This cannot be undone.`)) return;
    for (const id of ids) {
      try { await apiDeleteTrack(id); } catch { /* continue */ }
    }
    exitSelectMode();
    onRefresh?.();
  });
}

export async function toggleStar(btn) {
  const trackId = btn.dataset.id;
  const isStarred = btn.dataset.starred === "true";
  try {
    await libApi(isStarred ? "unstar" : "star", { id: trackId });
    btn.dataset.starred = String(!isStarred);
    const icon = btn.querySelector("i");
    if (!isStarred) {
      icon.className = "ph-fill ph-heart text-peel-accent text-lg pointer-events-none";
      btn.title = "Unlike";
    } else {
      icon.className = "ph ph-heart text-peel-muted text-lg pointer-events-none";
      btn.title = "Like";
    }
  } catch (e) {
    console.error("star/unstar failed:", e);
  }
}

export async function showPlaylistPicker(trackIds, onDone = null) {
  let playlists = [];
  try {
    const r = await libApi("getPlaylists");
    playlists = r.playlists?.playlist || [];
  } catch (e) {
    alert(`Could not load playlists: ${e.message}`);
    return;
  }

  const existing = document.getElementById("pl-picker-dialog");
  if (existing) existing.remove();

  const d = document.createElement("dialog");
  d.id = "pl-picker-dialog";
  d.className =
    "bg-peel-surface text-peel-text border border-white/10 rounded-2xl p-6 min-w-[320px] max-w-[90vw] shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm";
  d.innerHTML = `
    <h3 class="text-lg font-semibold mb-4 flex items-center gap-2">
      <i class="ph-fill ph-playlist-plus text-peel-accent"></i>
      Add to playlist
    </h3>
    <div id="pl-picker-list" class="flex flex-col gap-1 max-h-72 overflow-y-auto mb-4">
      ${
        playlists.length
          ? playlists
              .map(
                (p) =>
                  `<button class="pl-pick-item flex items-center gap-3 px-4 py-2.5 rounded-xl hover:bg-white/10 text-sm text-left transition-colors"
                          data-id="${escapeHtml(p.id)}" data-name="${escapeHtml(p.name)}">
                    <i class="ph-fill ph-playlist text-peel-muted text-base flex-shrink-0"></i>
                    <span class="truncate">${escapeHtml(p.name)}</span>
                    <span class="ml-auto text-peel-muted text-xs">${p.songCount || 0}</span>
                  </button>`,
              )
              .join("")
          : `<p class="text-center text-peel-muted py-6 text-sm">No playlists yet.</p>`
      }
    </div>
    <div class="flex justify-end">
      <button id="pl-pick-cancel" class="px-5 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors">Cancel</button>
    </div>
  `;
  document.body.appendChild(d);
  d.showModal();

  d.querySelector("#pl-pick-cancel").addEventListener("click", () => d.close());
  d.addEventListener("close", () => d.remove());

  d.querySelectorAll(".pl-pick-item").forEach((btn) => {
    btn.addEventListener("click", async () => {
      d.close();
      try {
        await libApi("updatePlaylist", {
          playlistId: btn.dataset.id,
          songIdToAdd: trackIds,
        });
        showToast(`Added to "${btn.dataset.name}"`);
        onDone?.();
      } catch (e) {
        alert(`Failed: ${e.message}`);
      }
    });
  });
}

function showToast(msg) {
  const container = document.getElementById("toast-container");
  if (!container) return;
  const t = document.createElement("div");
  t.className =
    "pointer-events-auto bg-peel-surface border border-white/10 text-peel-text text-sm px-4 py-3 rounded-xl shadow-xl flex items-center gap-2 animate-slide-up";
  t.innerHTML = `<i class="ph-fill ph-check-circle text-peel-success text-base"></i>${escapeHtml(msg)}`;
  container.appendChild(t);
  setTimeout(() => t.remove(), 3000);
}
