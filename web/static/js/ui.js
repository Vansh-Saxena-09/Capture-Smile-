// ui.js — leaf helpers shared across modules (no imports, so nothing can cycle through here).

/** The five smile dimensions, in the exact order the /api/showdown request expects them. */
export const DIMENSIONS = ["radiance", "confidence", "playfulness", "mystery", "warmth"];

export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

export function prefersReducedMotion() {
  return window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/** Minimal HTML escaping for any value that ends up inside markup we build with template strings. */
export function escapeHtml(value) {
  return String(value == null ? "" : value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

/** Only allow well-formed hex colours from the API into inline styles; fall back to brand ice-blue. */
export function cssColor(hex) {
  return /^#[0-9a-fA-F]{3,8}$/.test(hex || "") ? hex : "#7FB6E8";
}

/**
 * Ambient and decorative motion (background drift, hero entrance) is scoped to the front page
 * through `body.on-home`, so the friendly-challenge screens stay perfectly still.
 */
function syncAmbientMotion(activeId) {
  document.body.classList.toggle("on-home", activeId === "screen-home");
}

/**
 * Home is shown on every screen except home itself. Driven from here rather than from each flow,
 * because showScreen is the one place a screen change happens — so no screen can be reached
 * without the button's state being correct, including the ones that strand you mid-flow.
 */
function syncHomeButton(activeId) {
  const btn = document.getElementById("btn-home");
  if (btn) btn.hidden = activeId === "screen-home";
}

let screenTimer = null;
/** Cross-fade to a screen: briefly play the current screen's exit, then activate the next one. */
export function showScreen(id) {
  const next = document.getElementById(id);
  if (!next) return;
  const current = document.querySelector(".screen.active");
  if (current === next) return;

  const reveal = () => {
    $$(".screen").forEach((s) => s.classList.remove("active", "leaving"));
    next.classList.add("active");
    syncAmbientMotion(id);
    syncHomeButton(id);
    window.scrollTo({ top: 0, behavior: "auto" });
  };

  if (current && !prefersReducedMotion()) {
    current.classList.add("leaving");
    clearTimeout(screenTimer);
    screenTimer = setTimeout(reveal, 240);
  } else {
    reveal();
  }
}

/** Add the `.in` reveal class to a list of nodes, optionally staggered. */
export function staggerIn(nodes, step = 90) {
  const reduced = prefersReducedMotion();
  nodes.forEach((node, i) => {
    if (reduced) {
      node.classList.add("in");
    } else {
      setTimeout(() => node.classList.add("in"), i * step);
    }
  });
}

let toastTimer = null;
export function toast(message) {
  const t = document.getElementById("toast");
  if (!t) return;
  t.textContent = message;
  t.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove("show"), 2800);
}
