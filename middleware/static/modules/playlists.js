import { library as libApi, coverArtUrl } from "./api.js";
import { escapeHtml } from "./util.js";
import { renderTrackList } from "./lib_tracks.js";
import * as contextMenu from "./context_menu.js";

const stack = [{ type: "playlists", label: "Playlists" }];

export function onShow() {
  stack.length = 1;
}

export async function render(root) {
  root.innerHTML = `
    <div class="w-full max-w-5xl mx-auto p-4 md:p-8">
      <div id="lib-breadcrumb" class="flex items-center gap-2 text-sm text-peel-muted mb-6 flex-wrap min-h-[1.75rem]"></div>
      <div id="lib-list">
        <div class="flex items-center justify-center py-20 opacity-50">
          <i class="ph ph-circle-notch text-4xl animate-spin-slow"></i>
        </div>
      </div>
    </div>
  `;
  renderBreadcrumb(root);
  await renderCurrent(root);
}

function renderBreadcrumb(root) {
  const bc = root.querySelector("#lib-breadcrumb");
  if (stack.length <= 1) { bc.innerHTML = ""; return; }
  bc.innerHTML = stack
    .map((s, i) =>
      i === stack.length - 1
        ? `<span class="text-peel-text font-medium">${escapeHtml(s.label)}</span>`
        : `<a class="cursor-pointer hover:text-peel-text transition-colors" data-idx="${i}">${escapeHtml(s.label)}</a>`,
    )
    .join('<i class="ph ph-caret-right text-xs mx-1"></i>');
  bc.querySelectorAll("a[data-idx]").forEach((a) => {
    a.addEventListener("click", () => {
      stack.splice(Number(a.dataset.idx) + 1);
      render(document.getElementById("view"));
    });
  });
}

async function renderCurrent(root) {
  const top = stack[stack.length - 1];
  const list = root.querySelector("#lib-list");
  try {
    if (top.type === "playlists")  await loadPlaylists(list);
    else if (top.type === "playlist") await loadPlaylist(list, top.id, top.label);
  } catch (e) {
    list.innerHTML = `
      <div class="flex flex-col items-center justify-center py-20 text-red-400">
        <i class="ph ph-warning-circle text-5xl mb-4"></i>
        <p>${escapeHtml(e.message)}</p>
      </div>`;
  }
}

function push(item) {
  stack.push(item);
  render(document.getElementById("view"));
}

async function loadPlaylists(list) {
  const r = await libApi("getPlaylists");
  const items = r.playlists?.playlist || [];
  list.innerHTML = `
    <div class="flex justify-end mb-4">
      <button id="create-playlist-btn"
              class="flex items-center gap-2 px-4 py-2 bg-peel-accent hover:bg-peel-accentHover text-peel-bg text-sm font-semibold rounded-xl transition-colors">
        <i class="ph-bold ph-plus"></i> New playlist
      </button>
    </div>
    <div id="playlist-list" class="flex flex-col gap-2">
      ${
        items.length
          ? items.map((p) => `
      <div class="group flex items-center gap-4 p-3 rounded-xl hover:bg-white/5 transition-all cursor-pointer"
           data-id="${escapeHtml(p.id)}" data-name="${escapeHtml(p.name)}">
        <div class="w-12 h-12 rounded-xl bg-peel-surface flex items-center justify-center flex-shrink-0">
          <i class="ph-fill ph-playlist text-peel-muted text-2xl"></i>
        </div>
        <div class="flex-1 min-w-0">
          <p class="font-medium truncate">${escapeHtml(p.name)}</p>
          <p class="text-sm text-peel-muted">${p.songCount || 0} track${p.songCount === 1 ? "" : "s"}</p>
        </div>
        <button class="dots-btn w-9 h-9 rounded-full flex items-center justify-center hover:bg-white/10 text-peel-muted opacity-0 group-hover:opacity-100 transition-all flex-shrink-0"
                data-id="${escapeHtml(p.id)}" data-name="${escapeHtml(p.name)}" title="More options">
          <i class="ph ph-dots-three-vertical text-lg pointer-events-none"></i>
        </button>
        <i class="ph ph-caret-right text-peel-muted opacity-0 group-hover:opacity-100 transition-opacity"></i>
      </div>`).join("")
          : `<div class="flex flex-col items-center justify-center py-20 opacity-50">
               <i class="ph ph-playlist text-6xl mb-4"></i>
               <p class="text-lg">No playlists yet.</p>
             </div>`
      }
    </div>
  `;

  list.querySelector("#create-playlist-btn").addEventListener("click", () =>
    showCreateDialog(list)
  );

  list.querySelectorAll("[data-id]:not(.dots-btn)").forEach((el) => {
    el.addEventListener("click", () =>
      push({ type: "playlist", id: el.dataset.id, label: el.dataset.name })
    );
  });

  list.querySelectorAll(".dots-btn").forEach((btn) => {
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      contextMenu.show(btn, [
        {
          label: "Add songs",
          icon: "ph ph-plus-circle",
          action: () => showSongPicker(btn.dataset.id, btn.dataset.name, () => loadPlaylists(list)),
        },
        {
          label: "Rename",
          icon: "ph ph-pencil",
          action: () => showRenameDialog(btn.dataset.id, btn.dataset.name, list),
        },
        null,
        {
          label: "Delete",
          icon: "ph ph-trash",
          danger: true,
          action: async () => {
            if (!confirm(`Delete "${btn.dataset.name}"?`)) return;
            await libApi("deletePlaylist", { id: btn.dataset.id });
            await loadPlaylists(list);
          },
        },
      ]);
    });
  });
}

function showCreateDialog(list) {
  const existing = document.getElementById("create-pl-dialog");
  if (existing) existing.remove();
  const d = document.createElement("dialog");
  d.id = "create-pl-dialog";
  d.className =
    "bg-peel-surface text-peel-text border border-white/10 rounded-2xl p-6 min-w-[320px] max-w-[90vw] shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm";
  d.innerHTML = `
    <h3 class="text-lg font-semibold mb-4 flex items-center gap-2">
      <i class="ph-fill ph-playlist text-peel-accent"></i> New playlist
    </h3>
    <input type="text" id="pl-name" placeholder="Playlist name"
           class="w-full bg-peel-bg text-peel-text placeholder-peel-muted rounded-xl py-3 px-4 mb-5 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10">
    <div class="flex gap-3 justify-end">
      <button id="pl-cancel" class="px-5 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors">Cancel</button>
      <button id="pl-create" class="px-5 py-2.5 bg-peel-accent hover:bg-peel-accentHover text-peel-bg rounded-xl text-sm font-semibold transition-colors">Create</button>
    </div>
  `;
  document.body.appendChild(d);
  d.showModal();
  d.querySelector("#pl-name").focus();
  d.querySelector("#pl-cancel").addEventListener("click", () => d.close());
  d.addEventListener("close", () => d.remove());
  async function create() {
    const name = d.querySelector("#pl-name").value.trim();
    if (!name) return;
    await libApi("createPlaylist", { name });
    d.close();
    await loadPlaylists(list);
  }
  d.querySelector("#pl-create").addEventListener("click", create);
  d.querySelector("#pl-name").addEventListener("keydown", (e) => {
    if (e.key === "Enter") create();
  });
}

function showRenameDialog(playlistId, currentName, list) {
  const existing = document.getElementById("rename-pl-dialog");
  if (existing) existing.remove();
  const d = document.createElement("dialog");
  d.id = "rename-pl-dialog";
  d.className =
    "bg-peel-surface text-peel-text border border-white/10 rounded-2xl p-6 min-w-[320px] max-w-[90vw] shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm";
  d.innerHTML = `
    <h3 class="text-lg font-semibold mb-4">Rename playlist</h3>
    <input type="text" id="rename-pl-name" value="${escapeHtml(currentName)}"
           class="w-full bg-peel-bg text-peel-text placeholder-peel-muted rounded-xl py-3 px-4 mb-5 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10">
    <div class="flex gap-3 justify-end">
      <button id="rename-pl-cancel" class="px-5 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors">Cancel</button>
      <button id="rename-pl-save" class="px-5 py-2.5 bg-peel-accent hover:bg-peel-accentHover text-peel-bg rounded-xl text-sm font-semibold transition-colors">Save</button>
    </div>
  `;
  document.body.appendChild(d);
  d.showModal();
  const input = d.querySelector("#rename-pl-name");
  input.focus();
  input.select();
  d.querySelector("#rename-pl-cancel").addEventListener("click", () => d.close());
  d.addEventListener("close", () => d.remove());
  async function save() {
    const name = input.value.trim();
    if (!name || name === currentName) { d.close(); return; }
    await libApi("updatePlaylist", { playlistId, name });
    d.close();
    await loadPlaylists(list);
  }
  d.querySelector("#rename-pl-save").addEventListener("click", save);
  input.addEventListener("keydown", (e) => { if (e.key === "Enter") save(); });
}

async function loadPlaylist(list, playlistId, playlistName) {
  const r = await libApi("getPlaylist", { id: playlistId });
  const songs = r.playlist?.entry || [];

  list.innerHTML = `
    <div class="flex justify-end mb-2">
      <button id="pl-add-songs-btn"
              class="flex items-center gap-2 px-4 py-2 bg-white/5 hover:bg-white/10 text-sm font-medium rounded-xl transition-colors">
        <i class="ph ph-plus text-sm"></i> Add songs
      </button>
    </div>
    <div id="pl-tracks"></div>
  `;

  list.querySelector("#pl-add-songs-btn").addEventListener("click", () => {
    showSongPicker(playlistId, playlistName || "playlist", () => loadPlaylist(list, playlistId, playlistName));
  });

  const tracksContainer = list.querySelector("#pl-tracks");
  if (!songs.length) {
    tracksContainer.innerHTML = `<p class="text-center py-12 text-peel-muted">No tracks yet. Add some!</p>`;
    return;
  }
  renderTrackList(tracksContainer, songs, {
    playlistId,
    onRefresh: () => loadPlaylist(list, playlistId, playlistName),
  });
}

async function showSongPicker(playlistId, playlistName, onDone) {
  const existing = document.getElementById("song-picker-dialog");
  if (existing) existing.remove();

  const d = document.createElement("dialog");
  d.id = "song-picker-dialog";
  d.className =
    "bg-peel-surface text-peel-text border border-white/10 rounded-2xl p-0 w-[90vw] max-w-lg shadow-2xl backdrop:bg-black/70 backdrop:backdrop-blur-sm flex flex-col overflow-hidden";
  d.style.maxHeight = "80vh";
  d.innerHTML = `
    <div class="p-5 border-b border-white/10 flex-shrink-0">
      <h3 class="text-base font-semibold mb-3 flex items-center gap-2">
        <i class="ph-fill ph-playlist-plus text-peel-accent"></i>
        Add songs to "${escapeHtml(playlistName)}"
      </h3>
      <input type="text" id="sp-search" placeholder="Search by title or artist…"
             class="w-full bg-peel-bg text-peel-text placeholder-peel-muted rounded-xl py-2.5 px-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10 text-sm">
    </div>
    <div id="sp-list" class="overflow-y-auto flex-1">
      <div class="flex items-center justify-center py-16 opacity-50">
        <i class="ph ph-circle-notch text-3xl animate-spin-slow"></i>
      </div>
    </div>
    <div class="p-4 border-t border-white/10 flex items-center justify-between gap-3 flex-shrink-0">
      <span id="sp-count" class="text-sm text-peel-muted">0 selected</span>
      <div class="flex gap-2">
        <button id="sp-cancel" class="px-4 py-2 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors">Cancel</button>
        <button id="sp-confirm" disabled
                class="px-4 py-2 bg-peel-accent hover:bg-peel-accentHover text-peel-bg rounded-xl text-sm font-semibold transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
          Add
        </button>
      </div>
    </div>
  `;
  document.body.appendChild(d);
  d.showModal();
  d.querySelector("#sp-search").focus();

  const selected = new Set();
  let allSongs = [];

  try {
    // Paginate through all songs
    let offset = 0;
    while (true) {
      const r = await libApi("search3", { query: " ", songCount: 500, songOffset: offset, artistCount: 0, albumCount: 0 });
      const batch = r.searchResult3?.song || [];
      allSongs.push(...batch);
      if (batch.length < 500) break;
      offset += 500;
    }
    allSongs.sort((a, b) => (a.artist || "").localeCompare(b.artist || "") || (a.title || "").localeCompare(b.title || ""));
  } catch (e) {
    d.querySelector("#sp-list").innerHTML = `<p class="text-center py-8 text-red-400 text-sm px-4">${escapeHtml(e.message)}</p>`;
  }

  function renderList(filter) {
    const f = (filter || "").toLowerCase();
    const songs = f
      ? allSongs.filter((s) =>
          (s.title || "").toLowerCase().includes(f) ||
          (s.artist || "").toLowerCase().includes(f),
        )
      : allSongs;

    const spList = d.querySelector("#sp-list");
    if (!songs.length) {
      spList.innerHTML = `<p class="text-center py-10 text-peel-muted text-sm">No songs found.</p>`;
      return;
    }
    spList.innerHTML = songs.map((s) => `
      <label class="flex items-center gap-3 px-4 py-2.5 hover:bg-white/5 cursor-pointer transition-colors">
        <input type="checkbox" class="sp-cb w-4 h-4 rounded accent-peel-accent flex-shrink-0 cursor-pointer"
               data-id="${escapeHtml(s.id)}" ${selected.has(s.id) ? "checked" : ""}>
        <div class="flex-1 min-w-0">
          <p class="text-sm font-medium truncate">${escapeHtml(s.title || "—")}</p>
          <p class="text-xs text-peel-muted truncate">${escapeHtml(s.artist || "—")}</p>
        </div>
      </label>
    `).join("");

    spList.querySelectorAll(".sp-cb").forEach((cb) => {
      cb.addEventListener("change", () => {
        if (cb.checked) selected.add(cb.dataset.id);
        else selected.delete(cb.dataset.id);
        updateCount();
      });
    });
  }

  function updateCount() {
    const n = selected.size;
    d.querySelector("#sp-count").textContent = `${n} selected`;
    const btn = d.querySelector("#sp-confirm");
    btn.disabled = n === 0;
    btn.textContent = n ? `Add ${n} song${n !== 1 ? "s" : ""}` : "Add";
  }

  renderList("");

  d.querySelector("#sp-search").addEventListener("input", (e) => renderList(e.target.value));
  d.querySelector("#sp-cancel").addEventListener("click", () => d.close());
  d.addEventListener("close", () => d.remove());

  d.querySelector("#sp-confirm").addEventListener("click", async () => {
    const ids = [...selected];
    if (!ids.length) return;
    try {
      await libApi("updatePlaylist", { playlistId, songIdToAdd: ids });
      d.close();
      onDone?.();
    } catch (e) {
      alert(`Failed to add songs: ${e.message}`);
    }
  });
}
