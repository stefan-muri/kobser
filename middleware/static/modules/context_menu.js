import { escapeHtml } from "./util.js";

let active = null;

/**
 * Show a context menu anchored to `anchorEl`.
 * items: Array of { label, icon, action, danger? } or null (divider).
 */
export function show(anchorEl, items) {
  close();

  const menu = document.createElement("div");
  menu.className =
    "fixed z-[60] bg-peel-surface border border-white/10 rounded-xl shadow-2xl py-1 min-w-[196px]";

  for (const item of items) {
    if (item === null) {
      const sep = document.createElement("div");
      sep.className = "border-t border-white/5 my-1";
      menu.appendChild(sep);
      continue;
    }
    const btn = document.createElement("button");
    btn.className = `w-full flex items-center gap-3 px-4 py-2.5 text-sm text-left transition-colors ${
      item.danger
        ? "text-red-400 hover:bg-red-500/10"
        : "text-peel-text hover:bg-white/5"
    }`;
    btn.innerHTML = `<i class="${escapeHtml(item.icon)} text-base flex-shrink-0"></i>${escapeHtml(item.label)}`;
    btn.addEventListener("click", () => {
      close();
      item.action();
    });
    menu.appendChild(btn);
  }

  document.body.appendChild(menu);
  active = menu;

  // Position below/above the anchor, right-aligned to it.
  const rect = anchorEl.getBoundingClientRect();
  const mw = 200;
  const mh = items.filter((i) => i !== null).length * 42 + 16;
  let top = rect.bottom + 4;
  let left = rect.right - mw;
  if (top + mh > window.innerHeight - 8) top = rect.top - mh - 4;
  if (left < 8) left = 8;
  menu.style.top = `${top}px`;
  menu.style.left = `${left}px`;

  // Close on next outside click or Escape.
  setTimeout(() => {
    document.addEventListener("click", close, { once: true });
  }, 0);
  document.addEventListener(
    "keydown",
    (e) => { if (e.key === "Escape") close(); },
    { once: true },
  );
}

export function close() {
  if (active) { active.remove(); active = null; }
}
