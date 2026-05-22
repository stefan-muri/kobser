import { library as libApi } from "./api.js";
import { escapeHtml } from "./util.js";
import { renderTrackList } from "./lib_tracks.js";

export async function render(root) {
  root.innerHTML = `
    <div class="w-full max-w-5xl mx-auto p-4 md:p-8">
      <h2 class="text-3xl font-bold mb-8 pl-1">Liked Songs</h2>
      <div id="lib-list">
        <div class="flex items-center justify-center py-20 opacity-50">
          <i class="ph ph-circle-notch text-4xl animate-spin-slow"></i>
        </div>
      </div>
    </div>
  `;
  const list = root.querySelector("#lib-list");
  try {
    const r = await libApi("getStarred2");
    const songs = r.starred2?.song || [];
    if (!songs.length) {
      list.innerHTML = `
        <div class="flex flex-col items-center justify-center py-20 opacity-50">
          <i class="ph ph-heart text-6xl mb-4"></i>
          <p class="text-lg">No liked songs yet.</p>
          <p class="text-sm mt-1">Heart a track in Library to add it here.</p>
        </div>`;
      return;
    }
    renderTrackList(list, songs, {
      allStarred: true,
      onRefresh: () => render(root),
    });
  } catch (e) {
    list.innerHTML = `
      <div class="flex flex-col items-center justify-center py-20 text-red-400">
        <i class="ph ph-warning-circle text-5xl mb-4"></i>
        <p>${escapeHtml(e.message)}</p>
      </div>`;
  }
}
