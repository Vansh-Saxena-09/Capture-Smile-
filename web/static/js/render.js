// render.js — turns an /api/analyze result into the on-screen casting reveal.
// Every character shows an always-on CSS emblem (gradient + emoji); a real image, when present,
// is layered on top and fades in only once it loads — so a card is never blank or broken.
import { $$, staggerIn, prefersReducedMotion, escapeHtml, cssColor } from "./ui.js";

const VIBE_EMOJI = { RADIANT: "✨", CONFIDENT: "😎", PLAYFUL: "😜", MYSTERIOUS: "🕵️", WARM: "🤗" };
function vibeEmoji(vibe) { return VIBE_EMOJI[String(vibe || "").toUpperCase()] || "🎬"; }

function gradient(a, b) { return `background:linear-gradient(150deg, ${cssColor(a)}, ${cssColor(b)});`; }

const TMDB_IMG_BASE = "https://image.tmdb.org/t/p/";

/**
 * Normalizes an image value into something an <img> can load. The server already sends complete
 * urls, so this is a guard rather than the primary path: a bare TMDB path like "/abc123.jpg" is
 * not a loadable url, and passing one straight to src is exactly how a poster silently 404s.
 * Full urls and our own /static/ paths pass through untouched.
 */
export function tmdbImageUrl(pathOrUrl, size = "w500") {
  if (!pathOrUrl) return null;
  const v = String(pathOrUrl).trim();
  if (!v) return null;
  if (/^(https?:)?\/\//i.test(v) || v.startsWith("/static/") || v.startsWith("data:")) return v;
  return TMDB_IMG_BASE + size + (v.startsWith("/") ? "" : "/") + v;
}

// Rendered without a src; hydrateImages() attaches load/error handlers before setting it, so a
// cached image can't fire load before we're listening, and a failed one removes itself cleanly.
function realImg(url, cls = "real") {
  const src = tmdbImageUrl(url);
  return src ? `<img class="${cls}" alt="" loading="lazy" decoding="async" data-src="${escapeHtml(src)}">` : "";
}

/**
 * The character plate. Bottom layer is always drawn in CSS — duotone gradient, film sprockets,
 * universe wordmark, monogram, glyph — so every character has a real visual whether or not a photo
 * exists. An in-character still, where one is freely licensed, fades in over the top.
 */
function plateHTML(c, variant, characterImage) {
  const size = variant === "compact" ? "max-width:150px;" : "";
  const mono = escapeHtml(String(c.monogram || "").toUpperCase());
  // The character still only — never the actor's photo. Absent means the CSS plate stands alone.
  const still = characterImage !== undefined ? characterImage : c.characterImageUrl;
  return `<div class="emblem${variant === "compact" ? " compact" : ""}" style="${gradient(c.colorA, c.colorB)}${size}">`
    + `<span class="sprocket left" aria-hidden="true"></span>`
    + `<span class="sprocket right" aria-hidden="true"></span>`
    + `<span class="plate-universe">${escapeHtml(c.universe || "")}</span>`
    + `<span class="plate-mono" aria-hidden="true">${mono}</span>`
    + `<span class="glyph">${escapeHtml(c.emoji || "🎬")}</span>`
    + realImg(still)
    + `</div>`;
}

/** The character's name with the performer's own face inset directly in front of it. */
function nameRowHTML(c, variant, actorImage) {
  // The performer's own face. TMDB's profile_path lands here and nowhere else.
  const face = actorImage !== undefined ? actorImage : c.actorImageUrl;
  const actorImg = face
    ? realImg(face, "real actor-face")
    : `<span class="actor-initials">${escapeHtml(String(c.actor || "?").trim().charAt(0).toUpperCase())}</span>`;
  const cls = variant === "compact" ? "name-row compact" : "name-row";
  return `<div class="${cls}">`
    + `<span class="actor-chip" title="${escapeHtml(c.creditLabel + " " + c.actor)}">${actorImg}</span>`
    + `<span class="char-name">${escapeHtml(c.name)}</span>`
    + `</div>`;
}

function posterHTML(m, moviePoster) {
  // Prefer the structured match's moviePoster; fall back to the movie's own url.
  const poster = moviePoster || m.posterUrl;
  return `<div class="poster">`
    + `<span class="reel">🎬</span>`
    + `<span class="ptitle">${escapeHtml(m.title)}</span>`
    + `<span class="pyear">${escapeHtml(String(m.year || ""))}</span>`
    + realImg(poster)
    + `</div>`;
}

function movieCardHTML(m, moviePoster) {
  return `<div class="movie-card">`
    + posterHTML(m, moviePoster)
    + `<div class="movie-meta">`
    + `<div class="mlabel">Now showing</div>`
    + `<div class="mtitle">${escapeHtml(m.title)}</div>`
    + `<div class="myear">${escapeHtml(String(m.year || ""))}</div>`
    + `<div class="mwhy">${escapeHtml(m.why)}</div>`
    + `</div></div>`;
}

function matchesHTML(list) {
  const rows = (list || []).map((m) => {
    const pct = Number(m.matchPct) || 0;
    // Character still if we have one, otherwise the performer's face — the row always shows a face.
    const thumb = m.characterImageUrl || m.actorImageUrl;
    return `<div class="match-row reveal">`
      + `<div class="match-emblem" style="${gradient(m.colorA, m.colorB)}">${escapeHtml(m.emoji || "🎬")}${realImg(thumb)}</div>`
      + `<div class="match-info"><div class="mn">${escapeHtml(m.name)}</div>`
      +   `<div class="mu">${[m.universe, m.actor].filter(Boolean).map(escapeHtml).join(" · ")}</div></div>`
      + `<div class="match-bar"><div class="fill" data-pct="${pct}"></div></div>`
      + `<div class="match-pct">${pct}%</div>`
      + `</div>`;
  }).join("");
  return `<div class="matches">${rows}</div>`;
}

// The why-box label needs the mono/accent treatment but sits outside .movie-meta (where .mlabel is
// scoped), so it carries its own inline style rather than borrowing a scoped class.
const WHY_LABEL = 'style="font-family:var(--font-mono);letter-spacing:0.2em;text-transform:uppercase;color:var(--ice);font-size:0.7rem;margin-bottom:8px"';

function resultHTML(r) {
  const c = r.character;
  // The structured match object (characterImage / actorImage / moviePoster kept separate).
  const match = r.match || {};
  const movieSection = r.bestMovie
    ? `<div class="divider reveal"><span>The film you belong in</span></div><div class="reveal">${movieCardHTML(r.bestMovie, match.moviePoster)}</div>`
    : "";
  return `<div class="score-block reveal">`
    +   `<div class="score-eyebrow">Your smile scores</div>`
    +   `<div class="score-number" id="score-number" data-score="${Number(r.score) || 0}">0.0<span class="max">/10</span></div>`
    +   `<div class="vibe-chip">${vibeEmoji(r.vibe)} ${escapeHtml(r.vibe)}</div>`
    +   `<p class="smile-quote">${escapeHtml(r.description)}</p>`
    + `</div>`
    + `<div class="divider reveal"><span>Your fictional match</span></div>`
    + `<div class="char-card reveal">`
    +   plateHTML(c, "full", match.characterImage)
    +   nameRowHTML(c, "full", match.actorImage)
    +   `<div class="char-universe">${escapeHtml(c.universe)}</div>`
    +   `<div class="char-credit"><b>${escapeHtml(c.creditLabel)}</b> ${escapeHtml(c.actor)}</div>`
    +   `<div class="match-pill">${Number(c.matchPct) || 0}% MATCH</div>`
    + `</div>`
    + `<div class="why-box reveal"><div ${WHY_LABEL}>Why you resemble them</div><div class="why-text">${escapeHtml(c.why)}</div></div>`
    + movieSection
    + `<div class="divider reveal"><span>Your top matches</span></div>`
    + matchesHTML(r.topMatches);
}

/**
 * A condensed per-person card for the friendly challenge (no score count-up — the podium already
 * shows scores, and no `.reveal` class, because the challenge screens render without animation).
 */
export function compactPersonHTML(name, r, place) {
  const c = r.character;
  const match = r.match || {};
  const medal = ["🥇", "🥈", "🥉"][place - 1] || `#${place}`;
  const movie = r.bestMovie ? `<div style="margin-top:14px">${movieCardHTML(r.bestMovie, match.moviePoster)}</div>` : "";
  return `<div class="person-block">`
    + `<div class="person-head"><span class="podium-medal">${medal}</span>`
    +   `<span class="pname">${escapeHtml(name)}</span>`
    +   `<span class="pscore">${(Number(r.score) || 0).toFixed(1)}/10 · ${escapeHtml(r.vibe)}</span></div>`
    + `<div class="char-card">`
    +   plateHTML(c, "compact", match.characterImage)
    +   nameRowHTML(c, "compact", match.actorImage)
    +   `<div class="char-universe">${escapeHtml(c.universe)}</div>`
    +   `<div class="char-credit"><b>${escapeHtml(c.creditLabel)}</b> ${escapeHtml(c.actor)}</div>`
    +   `<div class="match-pill">${Number(c.matchPct) || 0}% MATCH</div>`
    +   `<div class="why-box" style="margin-top:18px"><div class="why-text">${escapeHtml(c.why)}</div></div>`
    + `</div>${movie}</div>`;
}

/**
 * Attach load/error handlers, then set src, so a broken remote image never leaves a gap.
 * On error the <img> is removed outright — the CSS plate/poster card underneath is already drawn,
 * so what remains is the designed fallback rather than a broken-image icon.
 */
export function hydrateImages(container) {
  $$("img.real[data-src]", container).forEach((img) => {
    const src = img.getAttribute("data-src");
    img.removeAttribute("data-src");
    img.addEventListener("load", () => img.classList.add("loaded"));
    img.addEventListener("error", () => {
      // Hide first (in case something still holds a reference), then drop it.
      img.style.display = "none";
      img.remove();
      if (imageDebug()) console.warn("[images] failed, using fallback:", src);
    });
    img.src = src;
  });
}

/** Opt-in image tracing: `localStorage.setItem("debugImages","1")`, or ?debugImages=1. */
function imageDebug() {
  try {
    if (localStorage.getItem("debugImages") === "1") return true;
  } catch { /* storage blocked — fall through to the query flag */ }
  return new URLSearchParams(location.search).has("debugImages");
}

/**
 * Traces the image pipeline end to end: what the server sent, what url each slot resolved to, and
 * whether it actually loaded. Off unless explicitly enabled, so normal runs stay quiet.
 */
export function traceImages(result) {
  if (!imageDebug()) return;
  const m = result.match || {};
  console.groupCollapsed("[images] pipeline");
  console.log("FINAL MATCH DATA:", m);
  console.log("characterImage (fictional character — local file, else Wikipedia article image):", m.characterImage, "→", tmdbImageUrl(m.characterImage));
  console.log("actorImage (performer; TMDB profile_path lands here):", m.actorImage, "→", tmdbImageUrl(m.actorImage));
  console.log("moviePoster (TMDB poster_path):", m.moviePoster, "→", tmdbImageUrl(m.moviePoster));
  console.log("movie:", m.movieTitle, "id:", m.movieId, "| bestMovie.posterUrl:", result.bestMovie && result.bestMovie.posterUrl);
  console.log("actorName (TMDB-confirmed):", m.actorName, "| catalog actor:", result.character && result.character.actor);
  console.groupEnd();
}

/** Animate each match bar to its width on the next frame (CSS transitions the rest). */
export function fillBars(container) {
  $$(".match-bar .fill[data-pct]", container).forEach((fill) => {
    let pct = parseInt(fill.getAttribute("data-pct"), 10) || 0;
    pct = Math.max(0, Math.min(100, pct));
    requestAnimationFrame(() => { fill.style.width = pct + "%"; });
  });
}

function countUp(el, target, ms) {
  const tail = '<span class="max">/10</span>';
  if (prefersReducedMotion()) { el.innerHTML = target.toFixed(1) + tail; return; }
  const start = performance.now();
  function frame(now) {
    const t = Math.min(1, (now - start) / ms);
    const eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
    el.innerHTML = (target * eased).toFixed(1) + tail;
    if (t < 1) requestAnimationFrame(frame);
    else el.innerHTML = target.toFixed(1) + tail;
  }
  requestAnimationFrame(frame);
}

export function renderResultInto(container, result) {
  container.innerHTML = resultHTML(result);
  traceImages(result);
  hydrateImages(container);
  staggerIn($$(".reveal", container), 90);
  const scoreEl = container.querySelector("#score-number");
  if (scoreEl) countUp(scoreEl, Number(result.score) || 0, 1400);
  fillBars(container);
}

export function soloShareText(r) {
  const c = r.character;
  return `My smile scored ${(Number(r.score) || 0).toFixed(1)}/10 — ${r.vibe} — and I got cast as ${c.name} (${c.universe}). Find your character at Capture Smile.`;
}
