import { streamUrl, coverArtUrl, library as libApi, deleteTrack as apiDeleteTrack } from "./api.js";
import * as contextMenu from "./context_menu.js";
import { showPlaylistPicker } from "./lib_tracks.js";

let audio;
let queue = [];
let index = -1;
let shuffleOn = false;
let shuffledQueue = []; // indices into queue
let shufflePos = -1;

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
  });
  audio.addEventListener("pause", () => {
    if (playIcon) playIcon.className = "ph-fill ph-play text-2xl";
    syncExpandedPlayIcon();
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

function fmt(sec) {
  if (!sec || !isFinite(sec)) return "0:00";
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60).toString().padStart(2, "0");
  return `${m}:${s}`;
}
