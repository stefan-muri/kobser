import { getSession, setSession, me } from "./modules/api.js";
import * as search from "./modules/search.js";
import * as library from "./modules/library.js";
import * as liked from "./modules/liked.js";
import * as artists from "./modules/artists.js";
import * as playlists from "./modules/playlists.js";
import * as jobs from "./modules/jobs.js";
import * as settings from "./modules/settings.js";
import * as player from "./modules/player.js";
import * as auth from "./modules/auth.js";

const views = { search, library, liked, artists, playlists, jobs, settings };

export function showView(name) {
  const root = document.getElementById("view");
  root.innerHTML = "";
  root.scrollTo(0, 0);

  document.querySelectorAll(".nav-btn").forEach((btn) => {
    btn.classList.remove("text-peel-text", "bg-white/5");
    btn.classList.add("text-peel-muted");
  });
  const activeNav = document.getElementById(`nav-${name}`);
  if (activeNav) {
    activeNav.classList.remove("text-peel-muted");
    activeNav.classList.add("text-peel-text", "bg-white/5");
  }

  document.querySelectorAll(".mob-nav-btn").forEach((btn) => {
    btn.classList.remove("text-peel-accent");
    btn.classList.add("text-peel-muted");
    const icon = btn.querySelector("i");
    if (icon) icon.className = icon.className.replace("ph-fill", "ph");
  });
  document.querySelectorAll(".mob-nav-btn").forEach((btn) => {
    if (btn.dataset.view === name) {
      btn.classList.remove("text-peel-muted");
      btn.classList.add("text-peel-accent");
      const icon = btn.querySelector("i");
      if (icon) icon.className = icon.className.replace(/\bph\b(?!-)/, "ph-fill");
    }
  });

  views[name].onShow?.();
  views[name].render(root);
}

function initApp() {
  player.init();

  document.querySelectorAll("#sidebar-nav .nav-btn").forEach((btn) => {
    btn.addEventListener("click", () => showView(btn.dataset.view));
  });
  document.querySelectorAll("#mobile-nav .mob-nav-btn").forEach((btn) => {
    btn.addEventListener("click", () => showView(btn.dataset.view));
  });

  showView("search");
}

function showLogin() {
  const overlay = document.createElement("div");
  overlay.id = "login-overlay";
  document.body.appendChild(overlay);
  auth.render(overlay, () => {
    overlay.remove();
    initApp();
  });
}

async function init() {
  const session = getSession();
  if (!session) {
    showLogin();
    return;
  }
  try {
    await me();
    initApp();
  } catch {
    setSession(null);
    showLogin();
  }
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
