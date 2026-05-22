import { library as libApi } from "./api.js";
import { escapeHtml } from "./util.js";
import { renderTrackList } from "./lib_tracks.js";

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
    else if (top.type === "playlist") await loadPlaylist(list, top.id);
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
          ? items
              .map(
                (p) => `
      <div class="group flex items-center gap-4 p-3 rounded-xl hover:bg-white/5 transition-all cursor-pointer"
           data-id="${escapeHtml(p.id)}" data-name="${escapeHtml(p.name)}">
        <div class="w-12 h-12 rounded-xl bg-peel-surface flex items-center justify-center flex-shrink-0">
          <i class="ph-fill ph-playlist text-peel-muted text-2xl"></i>
        </div>
        <div class="flex-1 min-w-0">
          <p class="font-medium truncate">${escapeHtml(p.name)}</p>
          <p class="text-sm text-peel-muted">${p.songCount || 0} track${p.songCount === 1 ? "" : "s"}</p>
        </div>
        <button class="delete-btn w-9 h-9 rounded-full flex items-center justify-center hover:bg-red-500/20 hover:text-red-400 text-peel-muted opacity-0 group-hover:opacity-100 transition-all"
                data-id="${escapeHtml(p.id)}" data-name="${escapeHtml(p.name)}" title="Delete playlist">
          <i class="ph ph-trash text-lg pointer-events-none"></i>
        </button>
        <i class="ph ph-caret-right text-peel-muted opacity-0 group-hover:opacity-100 transition-opacity"></i>
      </div>`,
              )
              .join("")
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
  list.querySelectorAll("[data-id]:not(.delete-btn)").forEach((el) => {
    el.addEventListener("click", () =>
      push({ type: "playlist", id: el.dataset.id, label: el.dataset.name })
    );
  });
  list.querySelectorAll(".delete-btn").forEach((btn) => {
    btn.addEventListener("click", async (e) => {
      e.stopPropagation();
      if (!confirm(`Delete "${btn.dataset.name}"?`)) return;
      await libApi("deletePlaylist", { id: btn.dataset.id });
      await loadPlaylists(list);
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

async function loadPlaylist(list, playlistId) {
  const r = await libApi("getPlaylist", { id: playlistId });
  const songs = r.playlist?.entry || [];
  if (!songs.length) {
    list.innerHTML = `<p class="text-center py-12 text-peel-muted">No tracks in this playlist.</p>`;
    return;
  }
  renderTrackList(list, songs, {
    playlistId,
    onRefresh: () => loadPlaylist(list, playlistId),
  });
}
