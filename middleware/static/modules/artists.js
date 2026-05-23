import { library as libApi, coverArtUrl } from "./api.js";
import { escapeHtml } from "./util.js";
import { renderTrackList } from "./lib_tracks.js";

const stack = [{ type: "artists", label: "Artists" }];

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
    if (top.type === "artists")     await loadArtists(list);
    else if (top.type === "artist") await loadArtist(list, top.id);
    else if (top.type === "album")  await loadAlbum(list, top.id);
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

async function loadArtists(list) {
  const r = await libApi("getArtists");
  const artists = (r.artists?.index || []).flatMap((i) => i.artist || []);
  if (!artists.length) {
    list.innerHTML = `
      <div class="flex flex-col items-center justify-center py-20 opacity-50">
        <i class="ph ph-user-circle text-6xl mb-4"></i>
        <p class="text-lg">No artists yet. Download something from Search.</p>
      </div>`;
    return;
  }

  list.innerHTML = `
    <div class="relative mb-4">
      <i class="ph ph-magnifying-glass absolute left-4 top-1/2 -translate-y-1/2 text-peel-muted"></i>
      <input type="text" id="artists-search" placeholder="Search artists…"
             class="w-full bg-peel-surface text-peel-text placeholder-peel-muted rounded-xl py-2.5 pl-11 pr-4 focus:outline-none focus:ring-2 focus:ring-peel-accent/50 border border-white/10 text-sm">
    </div>
    <div id="artists-list" class="flex flex-col gap-1"></div>`;

  function renderArtists(filter) {
    const f = filter.toLowerCase();
    const filtered = f ? artists.filter((a) => a.name.toLowerCase().includes(f)) : artists;
    const container = list.querySelector("#artists-list");
    if (!filtered.length) {
      container.innerHTML = `<p class="text-center py-10 text-peel-muted text-sm">No artists match "${escapeHtml(filter)}"</p>`;
      return;
    }
    container.innerHTML = filtered.map((a) => `
      <div class="group flex items-center gap-4 p-3 rounded-xl hover:bg-white/5 transition-all cursor-pointer"
           data-id="${escapeHtml(a.id)}" data-name="${escapeHtml(a.name)}">
        <img class="w-12 h-12 rounded-full object-cover flex-shrink-0 bg-peel-surface"
             src="${escapeHtml(coverArtUrl(a.coverArt, 96))}" alt="" loading="lazy">
        <div class="flex-1 min-w-0">
          <p class="font-medium truncate">${escapeHtml(a.name)}</p>
          <p class="text-sm text-peel-muted">${a.albumCount || 0} album${a.albumCount === 1 ? "" : "s"}</p>
        </div>
        <i class="ph ph-caret-right text-peel-muted opacity-0 group-hover:opacity-100 transition-opacity"></i>
      </div>`).join("");
    container.querySelectorAll("[data-id]").forEach((el) => {
      el.addEventListener("click", () =>
        push({ type: "artist", id: el.dataset.id, label: el.dataset.name })
      );
    });
  }

  renderArtists("");
  list.querySelector("#artists-search").addEventListener("input", (e) =>
    renderArtists(e.target.value.trim())
  );
}

async function loadArtist(list, artistId) {
  const r = await libApi("getArtist", { id: artistId });
  const albums = r.artist?.album || [];
  if (!albums.length) {
    list.innerHTML = `<p class="text-center py-12 text-peel-muted">No albums.</p>`;
    return;
  }
  list.innerHTML = `
    <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
      ${albums
        .map(
          (a) => `
      <div class="group cursor-pointer" data-id="${escapeHtml(a.id)}" data-name="${escapeHtml(a.name)}">
        <div class="relative w-full aspect-square rounded-xl overflow-hidden mb-3 shadow-lg bg-peel-surface">
          <img src="${escapeHtml(coverArtUrl(a.coverArt, 300))}" alt="" loading="lazy"
               class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105">
          <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
            <div class="w-12 h-12 bg-peel-accent rounded-full flex items-center justify-center shadow-xl translate-y-2 group-hover:translate-y-0 transition-transform">
              <i class="ph-fill ph-play text-peel-bg text-xl"></i>
            </div>
          </div>
        </div>
        <p class="font-medium truncate text-sm">${escapeHtml(a.name)}</p>
        <p class="text-xs text-peel-muted">${a.songCount || 0} track${a.songCount === 1 ? "" : "s"}</p>
      </div>`,
        )
        .join("")}
    </div>`;
  list.querySelectorAll("[data-id]").forEach((el) => {
    el.addEventListener("click", () =>
      push({ type: "album", id: el.dataset.id, label: el.dataset.name })
    );
  });
}

async function loadAlbum(list, albumId) {
  const r = await libApi("getAlbum", { id: albumId });
  const songs = r.album?.song || [];
  if (!songs.length) {
    list.innerHTML = `<p class="text-center py-12 text-peel-muted">No tracks.</p>`;
    return;
  }
  renderTrackList(list, songs, {
    onRefresh: () => loadAlbum(list, albumId),
  });
}
