import { streamUrl, coverArtUrl } from "./api.js";
import { library as libApi } from "./api.js";

let audio;
let queue = [];
let index = -1;
let shuffleOn = false;
let shuffledQueue = []; // indices into queue
let shufflePos = -1;

const $ = (id) => document.getElementById(id);

export function init() {
  audio = $("audio");
  const toggle = $("mp-toggle");
  const playIcon = $("playIcon");
  const progressBar = $("progressBar");
  const progressContainer = $("player-progress");

  audio.addEventListener("play", () => {
    if (playIcon) playIcon.className = "ph-fill ph-pause text-2xl";
  });
  audio.addEventListener("pause", () => {
    if (playIcon) playIcon.className = "ph-fill ph-play text-2xl";
  });
  audio.addEventListener("ended", () => next());
  audio.addEventListener("timeupdate", () => {
    if (audio.duration && progressBar) {
      progressBar.style.width = `${(audio.currentTime / audio.duration) * 100}%`;
    }
    const timeEl = $("mp-time");
    if (timeEl) timeEl.textContent = `${fmt(audio.currentTime)} / ${fmt(audio.duration)}`;
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

  const shuffleBtn = $("mp-shuffle");
  if (shuffleBtn) {
    shuffleBtn.addEventListener("click", toggleShuffle);
  }

  const likeBtn = $("mp-like");
  if (likeBtn) {
    likeBtn.addEventListener("click", toggleLike);
  }
}

export function playQueue(tracks, startIndex = 0) {
  queue = tracks;
  index = startIndex;
  if (shuffleOn) buildShuffleOrder(startIndex);
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
  if (!btn) return;
  if (shuffleOn) {
    btn.classList.add("text-peel-accent");
    btn.classList.remove("text-peel-muted");
    if (queue.length) buildShuffleOrder(index);
  } else {
    btn.classList.remove("text-peel-accent");
    btn.classList.add("text-peel-muted");
  }
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
    btn.dataset.liked = String(newLiked);
    updateLikeBtn(newLiked);
    track.starred = newLiked ? new Date().toISOString() : undefined;
  } catch (e) {
    console.error("like toggle failed:", e);
  }
}

function updateLikeBtn(liked) {
  const btn = $("mp-like");
  if (!btn) return;
  const icon = btn.querySelector("i");
  if (!icon) return;
  if (liked) {
    icon.className = "ph-fill ph-heart text-xl";
    btn.classList.add("text-peel-accent");
    btn.classList.remove("text-peel-muted");
  } else {
    icon.className = "ph ph-heart text-xl";
    btn.classList.remove("text-peel-accent");
    btn.classList.add("text-peel-muted");
  }
  btn.dataset.liked = String(liked);
}

function load() {
  const track = queue[index];
  if (!track) return;
  audio.src = streamUrl(track.id);
  audio.play().catch((e) => console.error("play failed:", e));

  $("mp-cover").src = coverArtUrl(track.coverArt, 96);
  $("mp-cover").classList.remove("hidden");
  $("mp-title").textContent = track.title || "—";
  $("mp-artist").textContent = track.artist || "—";

  const player = $("mini-player");
  player.classList.remove("hidden");
  player.classList.add("flex");

  updateLikeBtn(!!track.starred);

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
