import { streamUrl, coverArtUrl, library as libApi, deleteTrack as apiDeleteTrack } from "./api.js";
import * as contextMenu from "./context_menu.js";
import { showPlaylistPicker } from "./lib_tracks.js";
import { escapeHtml } from "./util.js";

let audio;
let queue = [];
let index = -1;
let shuffleOn = false;
let shuffledQueue = []; // indices into queue
let shufflePos = -1;

const LAST_TRACK_KEY = "peel:last-track";

const $ = (id) => document.getElementById(id);

// ── Expanded player ──────────────────────────────────────────────────────────

function openExpanded() {
  const ep = $("expanded-player");
  if (!ep) return;
  ep.classList.remove("translate-y-full", "pointer-events-none");
  ep.classList.add("translate-y-0", "pointer-events-auto");
  syncExpandedState();
}

function closeExpanded() {
  const ep = $("expanded-player");
  if (!ep) return;
  ep.classList.remove("translate-y-0", "pointer-events-auto");
  ep.classList.add("translate-y-full", "pointer-events-none");
}

function syncExpandedState() {
  const track = queue[index];
  if (!track) return;

  const epCover = $("ep-cover");
  const epBg = $("ep-bg");
  const artUrl = trackArtUrl(track, 512);
  if (epCover) epCover.src = artUrl;
  if (epBg) epBg.style.backgroundImage = artUrl ? `url('${artUrl}')` : "";
  const epTitle = $("ep-title");
  const epArtist = $("ep-artist");
  if (epTitle) epTitle.textContent = track.title || "—";
  if (epArtist) epArtist.textContent = track.artist || "—";

  const epLikeBtn = $("ep-like");
  if (epLikeBtn) epLikeBtn.style.visibility = isPreview(track) ? "hidden" : "";
  const dotsBtn = $("ep-dots");
  if (dotsBtn) dotsBtn.style.visibility = isPreview(track) ? "hidden" : "";

  syncExpandedPlayIcon();
  if (!isPreview(track)) updateExpandedLikeBtn(!!track.starred);
  updateExpandedShuffle();
}

function syncExpandedPlayIcon() {
  const icon = $("ep-play-icon");
  if (!icon) return;
  icon.className = audio.paused
    ? "ph-fill ph-play text-2xl"
    : "ph-fill ph-pause text-2xl";
}

function updateExpandedLikeBtn(liked) {
  const btn = $("ep-like");
  if (!btn) return;
  const icon = btn.querySelector("i");
  if (!icon) return;
  if (liked) {
    icon.className = "ph-fill ph-heart text-2xl";
    btn.classList.add("text-peel-accent");
    btn.classList.remove("text-peel-muted");
  } else {
    icon.className = "ph ph-heart text-2xl";
    btn.classList.remove("text-peel-accent");
    btn.classList.add("text-peel-muted");
  }
  btn.dataset.liked = String(liked);
}

function updateExpandedShuffle() {
  const btn = $("ep-shuffle");
  if (!btn) return;
  if (shuffleOn) {
    btn.classList.add("text-peel-accent");
    btn.classList.remove("text-peel-muted");
  } else {
    btn.classList.remove("text-peel-accent");
    btn.classList.add("text-peel-muted");
  }
}

// ── Queue panel ──────────────────────────────────────────────────────────────

export function addToQueue(track) {
  if (!track) return;
  if (queue.length === 0 || index === -1) {
    playQueue([track], 0);
    return;
  }
  queue.push(track);
  renderQueuePanel();
}

function openQueue() {
  renderQueuePanel();
  const panel = $("queue-panel");
  const backdrop = $("queue-backdrop");
  if (panel) { panel.classList.remove("translate-x-full"); panel.classList.add("translate-x-0"); }
  if (backdrop) { backdrop.classList.remove("hidden"); }
}

function closeQueue() {
  const panel = $("queue-panel");
  const backdrop = $("queue-backdrop");
  if (panel) { panel.classList.remove("translate-x-0"); panel.classList.add("translate-x-full"); }
  if (backdrop) { backdrop.classList.add("hidden"); }
}

function removeFromQueue(qIdx) {
  if (qIdx === index) return;
  queue.splice(qIdx, 1);
  if (qIdx < index) index--;
  renderQueuePanel();
}

function renderQueuePanel() {
  const list = $("queue-list");
  if (!list) return;

  const current = queue[index];
  const upcoming = queue.slice(index + 1);
  let html = "";

  if (current) {
    html += `
      <div class="px-4 pt-5 pb-3">
        <p class="text-[10px] font-semibold text-peel-muted uppercase tracking-widest mb-3">Now Playing</p>
        <div class="flex items-center gap-3 p-2 rounded-xl bg-white/5">
          <img src="${escapeHtml(trackArtUrl(current, 64))}" alt=""
               class="w-10 h-10 rounded-md object-cover flex-shrink-0 bg-peel-bg">
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium truncate text-peel-accent">${escapeHtml(current.title || "—")}</p>
            <p class="text-xs text-peel-muted truncate">${escapeHtml(current.artist || "—")}</p>
          </div>
        </div>
      </div>`;
  }

  if (upcoming.length) {
    html += `
      <div class="px-4 pb-4">
        <div class="flex items-center justify-between mb-3">
          <p class="text-[10px] font-semibold text-peel-muted uppercase tracking-widest">Up Next</p>
          <button id="queue-clear-btn" class="text-xs text-peel-muted hover:text-red-400 transition-colors">Clear</button>
        </div>
        <div class="flex flex-col gap-0.5" id="queue-upcoming-list">
          ${upcoming.map((t, i) => {
            const qIdx = index + 1 + i;
            return `
              <div class="queue-row flex items-center gap-2 p-2 rounded-xl hover:bg-white/5 group cursor-pointer transition-colors"
                   draggable="true" data-qidx="${qIdx}">
                <i class="ph ph-dots-six-vertical text-peel-muted text-lg flex-shrink-0 cursor-grab opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none"></i>
                <img src="${escapeHtml(trackArtUrl(t, 64))}" alt=""
                     class="w-10 h-10 rounded-md object-cover flex-shrink-0 bg-peel-bg pointer-events-none">
                <div class="flex-1 min-w-0 pointer-events-none">
                  <p class="text-sm font-medium truncate">${escapeHtml(t.title || "—")}</p>
                  <p class="text-xs text-peel-muted truncate">${escapeHtml(t.artist || "—")}</p>
                </div>
                <button class="queue-remove-btn w-8 h-8 flex items-center justify-center text-peel-muted hover:text-white rounded-full hover:bg-white/10 transition-all opacity-0 group-hover:opacity-100 flex-shrink-0"
                        data-qidx="${qIdx}">
                  <i class="ph ph-x text-sm pointer-events-none"></i>
                </button>
              </div>`;
          }).join("")}
        </div>
      </div>`;
  } else if (!current) {
    html = `
      <div class="flex flex-col items-center justify-center h-full gap-3 opacity-40 p-8 text-center">
        <i class="ph ph-list text-5xl"></i>
        <p class="text-sm">Queue is empty</p>
      </div>`;
  }

  list.innerHTML = html;

  list.querySelector("#queue-clear-btn")?.addEventListener("click", () => {
    queue = queue.slice(0, index + 1);
    renderQueuePanel();
  });

  // ── Drag-to-reorder ───────────────────────────────────────────────────────
  let dragSrcQIdx = null;

  list.querySelectorAll(".queue-row").forEach((row) => {
    const qIdx = parseInt(row.dataset.qidx);

    row.addEventListener("click", (e) => {
      if (e.target.closest(".queue-remove-btn")) return;
      index = qIdx;
      load();
    });

    row.querySelector(".queue-remove-btn")?.addEventListener("click", (e) => {
      e.stopPropagation();
      removeFromQueue(qIdx);
    });

    row.addEventListener("dragstart", (e) => {
      dragSrcQIdx = qIdx;
      e.dataTransfer.effectAllowed = "move";
      // Delay so the browser snapshot doesn't show the dimmed state
      requestAnimationFrame(() => row.style.opacity = "0.4");
    });

    row.addEventListener("dragend", () => {
      dragSrcQIdx = null;
      row.style.opacity = "";
      list.querySelectorAll(".queue-row").forEach((r) => r.style.borderTop = "");
    });

    row.addEventListener("dragover", (e) => {
      e.preventDefault();
      e.dataTransfer.dropEffect = "move";
      // Show insertion line above the hovered row
      list.querySelectorAll(".queue-row").forEach((r) => r.style.borderTop = "");
      if (dragSrcQIdx !== null && dragSrcQIdx !== qIdx) {
        row.style.borderTop = "2px solid var(--color-peel-accent, #ff9f1c)";
      }
    });

    row.addEventListener("dragleave", () => {
      row.style.borderTop = "";
    });

    row.addEventListener("drop", (e) => {
      e.preventDefault();
      row.style.borderTop = "";
      if (dragSrcQIdx === null || dragSrcQIdx === qIdx) return;
      const dragged = queue.splice(dragSrcQIdx, 1)[0];
      // After removal, adjust target index if dragged was before it
      const insertAt = dragSrcQIdx < qIdx ? qIdx - 1 : qIdx;
      queue.splice(insertAt, 0, dragged);
      renderQueuePanel();
    });
  });
}

// ── Persistence ──────────────────────────────────────────────────────────────

function saveLastTrack() {
  const track = queue[index];
  if (!track || isPreview(track)) { localStorage.removeItem(LAST_TRACK_KEY); return; }
  try {
    localStorage.setItem(LAST_TRACK_KEY, JSON.stringify({ track, currentTime: audio?.currentTime ?? 0 }));
  } catch {}
}

function closePlayer() {
  audio.pause();
  const miniPlayer = $("mini-player");
  if (miniPlayer) { miniPlayer.classList.add("hidden"); miniPlayer.classList.remove("flex"); }
  document.body.classList.remove("player-visible");
  localStorage.removeItem(LAST_TRACK_KEY);
}

function restoreLastTrack() {
  try {
    const saved = localStorage.getItem(LAST_TRACK_KEY);
    if (!saved) return;
    const { track, currentTime } = JSON.parse(saved);
    if (!track?.id) return;

    queue = [track];
    index = 0;

    const coverEl = $("mp-cover");
    if (coverEl) { coverEl.src = trackArtUrl(track, 96); coverEl.classList.toggle("hidden", !coverEl.src); }
    const mpTitle = $("mp-title");
    const mpArtist = $("mp-artist");
    if (mpTitle) mpTitle.textContent = track.title || "—";
    if (mpArtist) mpArtist.textContent = track.artist || "—";

    const miniPlayer = $("mini-player");
    if (miniPlayer) { miniPlayer.classList.remove("hidden"); miniPlayer.classList.add("flex"); }
    document.body.classList.add("player-visible");

    audio.src = streamUrl(track.id);
    if (currentTime > 0) {
      audio.addEventListener("loadedmetadata", () => {
        audio.currentTime = Math.min(currentTime, (audio.duration || currentTime) - 1);
      }, { once: true });
    }

    const likeBtn = $("mp-like");
    if (likeBtn) { likeBtn.style.visibility = ""; updateLikeBtn(!!track.starred); }

    const artUrl = trackArtUrl(track, 512);
    const epCover = $("ep-cover"); if (epCover) epCover.src = artUrl;
    const epBg = $("ep-bg"); if (epBg) epBg.style.backgroundImage = artUrl ? `url('${artUrl}')` : "";
    const epTitle = $("ep-title"); if (epTitle) epTitle.textContent = track.title || "—";
    const epArtist = $("ep-artist"); if (epArtist) epArtist.textContent = track.artist || "—";
  } catch {
    localStorage.removeItem(LAST_TRACK_KEY);
  }
}

// ── Init ─────────────────────────────────────────────────────────────────────

export function init() {
  audio = $("audio");
  const toggle = $("mp-toggle");
  const playIcon = $("playIcon");
  const progressBar = $("progressBar");
  const progressContainer = $("player-progress");

  audio.addEventListener("play", () => {
    if (playIcon) playIcon.className = "ph-fill ph-pause text-2xl";
    syncExpandedPlayIcon();
    document.dispatchEvent(new CustomEvent("peel:playback-state", { detail: { playing: true } }));
  });
  audio.addEventListener("pause", () => {
    if (playIcon) playIcon.className = "ph-fill ph-play text-2xl";
    syncExpandedPlayIcon();
    document.dispatchEvent(new CustomEvent("peel:playback-state", { detail: { playing: false } }));
  });
  audio.addEventListener("ended", () => next());
  audio.addEventListener("timeupdate", () => {
    if (audio.duration && progressBar) {
      progressBar.style.width = `${(audio.currentTime / audio.duration) * 100}%`;
    }
    const timeEl = $("mp-time");
    if (timeEl) timeEl.textContent = `${fmt(audio.currentTime)} / ${fmt(audio.duration)}`;

    // Expanded progress
    const epBar = $("ep-progress-bar");
    if (epBar && audio.duration) {
      epBar.style.width = `${(audio.currentTime / audio.duration) * 100}%`;
    }
    const epCurrent = $("ep-current");
    const epDuration = $("ep-duration");
    if (epCurrent) epCurrent.textContent = fmt(audio.currentTime);
    if (epDuration) epDuration.textContent = fmt(audio.duration);
  });

  toggle.addEventListener("click", () =>
    audio.paused ? audio.play() : audio.pause(),
  );
  $("mp-prev").addEventListener("click", () => prev());
  $("mp-next").addEventListener("click", () => next());

  if (progressContainer) {
    progressContainer.addEventListener("click", (e) => {
      if (!audio.duration) return;
      const rect = progressContainer.getBoundingClientRect();
      audio.currentTime = ((e.clientX - rect.left) / rect.width) * audio.duration;
    });
  }

  // Open expanded player when clicking track info area
  const trackInfo = $("mini-player")?.querySelector(".flex-1");
  if (trackInfo) {
    trackInfo.addEventListener("click", () => {
      if (queue[index]) openExpanded();
    });
    trackInfo.style.cursor = "pointer";
  }

  const shuffleBtn = $("mp-shuffle");
  if (shuffleBtn) shuffleBtn.addEventListener("click", toggleShuffle);

  const likeBtn = $("mp-like");
  if (likeBtn) likeBtn.addEventListener("click", toggleLike);

  // Expanded player controls
  const epClose = $("ep-close");
  if (epClose) epClose.addEventListener("click", closeExpanded);

  const epToggle = $("ep-toggle");
  if (epToggle) epToggle.addEventListener("click", () =>
    audio.paused ? audio.play() : audio.pause(),
  );

  const epPrev = $("ep-prev");
  if (epPrev) epPrev.addEventListener("click", () => prev());

  const epNext = $("ep-next");
  if (epNext) epNext.addEventListener("click", () => next());

  const epLike = $("ep-like");
  if (epLike) epLike.addEventListener("click", toggleLike);

  const epShuffle = $("ep-shuffle");
  if (epShuffle) epShuffle.addEventListener("click", toggleShuffle);

  const epProgressContainer = $("ep-progress-container");
  if (epProgressContainer) {
    epProgressContainer.addEventListener("click", (e) => {
      if (!audio.duration) return;
      const rect = epProgressContainer.getBoundingClientRect();
      audio.currentTime = ((e.clientX - rect.left) / rect.width) * audio.duration;
    });
  }

  // Queue buttons
  const queueBtns = [$("mp-queue-btn"), $("ep-queue")];
  queueBtns.forEach((btn) => btn?.addEventListener("click", openQueue));
  $("queue-close")?.addEventListener("click", closeQueue);
  $("queue-backdrop")?.addEventListener("click", closeQueue);

  $("mp-close")?.addEventListener("click", closePlayer);
  audio.addEventListener("pause", saveLastTrack);
  window.addEventListener("beforeunload", saveLastTrack);

  restoreLastTrack();

  const epDots = $("ep-dots");
  if (epDots) {
    epDots.addEventListener("click", () => {
      const track = queue[index];
      if (!track || isPreview(track)) return;
      const isLiked = $("mp-like")?.dataset.liked === "true";
      contextMenu.show(epDots, [
        {
          label: "Add to playlist",
          icon: "ph ph-playlist-plus",
          action: () => showPlaylistPicker([track.id]),
        },
        {
          label: isLiked ? "Unlike" : "Like",
          icon: isLiked ? "ph-fill ph-heart" : "ph ph-heart",
          action: () => toggleLike(),
        },
        {
          label: "View queue",
          icon: "ph ph-list",
          action: () => openQueue(),
        },
        null,
        {
          label: "Delete from library",
          icon: "ph ph-trash",
          danger: true,
          action: async () => {
            if (!confirm(`Delete "${track.title}"? This cannot be undone.`)) return;
            try {
              await apiDeleteTrack(track.id);
              closeExpanded();
            } catch (err) {
              alert(`Delete failed: ${err.message}`);
            }
          },
        },
      ]);
    });
  }
}

export function playQueue(tracks, startIndex = 0) {
  queue = tracks;
  index = startIndex;
  if (shuffleOn) buildShuffleOrder(startIndex);
  load();
}

export function playPreviewTrack(track) {
  queue = [track];
  index = 0;
  load();
}

export function playShuffled(tracks) {
  if (!tracks.length) return;
  const shuffled = [...tracks];
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  playQueue(shuffled, 0);
}

function buildShuffleOrder(startIdx) {
  // Fisher-Yates shuffle of indices, keeping startIdx first
  const indices = queue.map((_, i) => i).filter((i) => i !== startIdx);
  for (let i = indices.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [indices[i], indices[j]] = [indices[j], indices[i]];
  }
  shuffledQueue = [startIdx, ...indices];
  shufflePos = 0;
}

function toggleShuffle() {
  shuffleOn = !shuffleOn;
  const btn = $("mp-shuffle");
  if (btn) {
    btn.classList.toggle("text-peel-accent", shuffleOn);
    btn.classList.toggle("text-peel-muted", !shuffleOn);
  }
  if (shuffleOn && queue.length) buildShuffleOrder(index);
  updateExpandedShuffle();
}

async function toggleLike() {
  const track = queue[index];
  if (!track) return;
  const btn = $("mp-like");
  if (!btn) return;
  const isLiked = btn.dataset.liked === "true";
  try {
    await libApi(isLiked ? "unstar" : "star", { id: track.id });
    const newLiked = !isLiked;
    track.starred = newLiked ? new Date().toISOString() : undefined;
    updateLikeBtn(newLiked);
  } catch (e) {
    console.error("like toggle failed:", e);
  }
}

function updateLikeBtn(liked) {
  const btn = $("mp-like");
  if (btn) {
    const icon = btn.querySelector("i");
    if (icon) icon.className = liked ? "ph-fill ph-heart text-xl" : "ph ph-heart text-xl";
    btn.classList.toggle("text-peel-accent", liked);
    btn.classList.toggle("text-peel-muted", !liked);
    btn.dataset.liked = String(liked);
  }
  updateExpandedLikeBtn(liked);
}

function isPreview(track) {
  return !!track?._previewUrl;
}

function trackArtUrl(track, size) {
  if (isPreview(track)) return track._previewThumb || "";
  return coverArtUrl(track.coverArt, size);
}

function load() {
  const track = queue[index];
  if (!track) return;

  audio.src = isPreview(track) ? track._previewUrl : streamUrl(track.id);
  audio.play().catch((e) => console.error("play failed:", e));

  const coverEl = $("mp-cover");
  coverEl.src = trackArtUrl(track, 96);
  coverEl.classList.toggle("hidden", !coverEl.src);
  $("mp-title").textContent = track.title || "—";
  $("mp-artist").textContent = track.artist || "—";

  const miniPlayer = $("mini-player");
  miniPlayer.classList.remove("hidden");
  miniPlayer.classList.add("flex");
  document.body.classList.add("player-visible");

  // Like button: hidden for previews (not in library)
  const likeBtn = $("mp-like");
  if (likeBtn) likeBtn.style.visibility = isPreview(track) ? "hidden" : "";
  if (!isPreview(track)) updateLikeBtn(!!track.starred);

  const dotsBtn = $("ep-dots");
  if (dotsBtn) dotsBtn.style.visibility = isPreview(track) ? "hidden" : "";

  // Sync expanded player
  const ep = $("expanded-player");
  const artUrl = trackArtUrl(track, 512);
  const epCover = $("ep-cover");
  const epBg = $("ep-bg");
  if (epCover) epCover.src = artUrl;
  if (epBg) epBg.style.backgroundImage = artUrl ? `url('${artUrl}')` : "";
  const epTitle = $("ep-title");
  const epArtist = $("ep-artist");
  if (epTitle) epTitle.textContent = track.title || "—";
  if (epArtist) epArtist.textContent = track.artist || "—";

  if (ep && !ep.classList.contains("translate-y-full")) {
    syncExpandedState();
  }

  // Refresh queue panel if open
  const qp = $("queue-panel");
  if (qp && !qp.classList.contains("translate-x-full")) renderQueuePanel();

  saveLastTrack();
  document.dispatchEvent(
    new CustomEvent("peel:track-change", { detail: { track, index } }),
  );
}

export function next() {
  if (shuffleOn && shuffledQueue.length) {
    if (shufflePos < shuffledQueue.length - 1) {
      shufflePos++;
      index = shuffledQueue[shufflePos];
      load();
    }
  } else if (index < queue.length - 1) {
    index++;
    load();
  }
}

export function prev() {
  if (audio.currentTime > 3) {
    audio.currentTime = 0;
    return;
  }
  if (shuffleOn && shuffledQueue.length) {
    if (shufflePos > 0) {
      shufflePos--;
      index = shuffledQueue[shufflePos];
      load();
    }
  } else if (index > 0) {
    index--;
    load();
  }
}

export function current() {
  return queue[index];
}

export function isPlaying() {
  return audio ? !audio.paused : false;
}

export function togglePlayPause() {
  if (!audio) return;
  audio.paused ? audio.play() : audio.pause();
}

function fmt(sec) {
  if (!sec || !isFinite(sec)) return "0:00";
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60).toString().padStart(2, "0");
  return `${m}:${s}`;
}
