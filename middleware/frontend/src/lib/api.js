const KEY_SESSION = "kobser.session";

export function getSession() {
  try {
    return JSON.parse(localStorage.getItem(KEY_SESSION)) || null;
  } catch {
    return null;
  }
}

export function setSession(s) {
  if (s) {
    localStorage.setItem(KEY_SESSION, JSON.stringify(s));
  } else {
    localStorage.removeItem(KEY_SESSION);
  }
}

function sessionId() {
  return getSession()?.sessionId || "";
}

async function call(path, opts = {}) {
  const headers = { ...(opts.headers || {}), "X-Session-Id": sessionId() };
  if (opts.body && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }
  const res = await fetch(path, { ...opts, headers });
  const text = await res.text();
  if (!res.ok) throw new Error(`${res.status}: ${text || res.statusText}`);
  return text ? JSON.parse(text) : null;
}

function unwrap(r) {
  const sr = r?.["subsonic-response"];
  if (!sr) throw new Error("not a subsonic response");
  if (sr.status !== "ok") {
    throw new Error(`subsonic ${sr.error?.code}: ${sr.error?.message}`);
  }
  return sr;
}

export const login = (username, password) =>
  call("/api/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });

export const logout = () => call("/api/logout", { method: "POST" });

export const me = () => call("/api/me");

// Returns { songs: [...], artists: [...] }
export const search = (query, limit = 10, source = "youtube_music") =>
  call("/api/search", {
    method: "POST",
    body: JSON.stringify({ query, limit, source }),
  });

export const download = (videoId, artist, title, source = "youtube_music", album = "", force = false) =>
  call("/api/download", {
    method: "POST",
    body: JSON.stringify({ videoId, artist, title, source, album, force }),
  });

export const searchArtists = (query, limit = 10) =>
  call("/api/search/artists", {
    method: "POST",
    body: JSON.stringify({ query, limit }),
  });

export const getArtist = (channelId) =>
  call(`/api/artist/${encodeURIComponent(channelId)}`);

export const getArtistSongs = (channelId) =>
  call(`/api/artist/${encodeURIComponent(channelId)}/songs`);

export const getAlbum = (browseId) =>
  call(`/api/album/${encodeURIComponent(browseId)}`);

export const status = (jobId) =>
  call(`/api/status/${encodeURIComponent(jobId)}`);

export const rescan = () => call("/api/rescan", { method: "POST" });

export async function library(subpath, params = {}) {
  const qs = Object.entries(params)
    .flatMap(([k, v]) =>
      Array.isArray(v) ? v.map((vi) => [k, String(vi)]) : [[k, String(v)]],
    )
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join("&");
  const r = await call(`/api/library/${subpath}${qs ? "?" + qs : ""}`);
  return unwrap(r);
}

export const deleteTrack = (trackId) =>
  call(`/api/track/${encodeURIComponent(trackId)}`, { method: "DELETE" });

export const listDownloads = () => call("/api/downloads");

export const deleteDownload = (jobId) =>
  call(`/api/downloads/${encodeURIComponent(jobId)}`, { method: "DELETE" });

export const cancelJob = (jobId) =>
  call(`/api/jobs/${encodeURIComponent(jobId)}/cancel`, { method: "POST" });

export const retryDownload = (jobId) =>
  call(`/api/downloads/${encodeURIComponent(jobId)}/retry`, { method: "POST" });

export const getStats = () => call("/api/stats");

export function streamUrl(trackId) {
  return `/api/stream/${encodeURIComponent(trackId)}?session=${encodeURIComponent(sessionId())}`;
}

export function previewUrl(videoId) {
  return `/api/preview/${encodeURIComponent(videoId)}?session=${encodeURIComponent(sessionId())}`;
}

export function coverArtUrl(coverArtId, size = 300) {
  if (!coverArtId) return "";
  const qs = new URLSearchParams({
    id: coverArtId,
    size: String(size),
    session: sessionId(),
  });
  return `/api/library/getCoverArt?${qs}`;
}
