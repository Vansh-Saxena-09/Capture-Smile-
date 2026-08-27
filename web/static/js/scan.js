// scan.js — the theatrical "analysing…" beat between capture and the reveal.
// Runs the real POST /api/analyze alongside a minimum dramatic delay so the scan-line,
// cycling status text, and progress ring always get their moment. Returns the result (or throws).
import { $, prefersReducedMotion, showScreen } from "./ui.js";
import { analyzeSmile } from "./api.js";

const STATUS = [
  "Reading your smile…",
  "Measuring radiance…",
  "Scanning for mischief…",
  "Consulting the archives…",
  "Casting your character…",
  "Cueing the projector…",
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Show the scan screen, analyse the blob, hold for the dramatic minimum, and resolve the result. */
export async function scanAndAnalyze(blob, dataUrl) {
  const img = $("#scan-img");
  const status = $("#scan-status");
  const ring = $("#scan-ring");

  if (img && dataUrl) img.src = dataUrl;
  if (ring) ring.style.setProperty("--p", "0");
  showScreen("screen-scanning");

  const reduced = prefersReducedMotion();
  const minMs = reduced ? 500 : 2600;

  let i = 0;
  if (status) status.textContent = STATUS[0];
  const statusTimer = setInterval(() => {
    i = (i + 1) % STATUS.length;
    if (status) status.textContent = STATUS[i];
  }, 700);

  const startedAt = performance.now();
  let raf = 0;
  if (ring && !reduced) {
    const step = (now) => {
      const p = Math.min(100, ((now - startedAt) / minMs) * 100);
      ring.style.setProperty("--p", p.toFixed(0));
      if (p < 100) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
  }

  try {
    const [result] = await Promise.all([analyzeSmile(blob), sleep(minMs)]);
    if (ring) ring.style.setProperty("--p", "100");
    return result;
  } finally {
    clearInterval(statusTimer);
    if (raf) cancelAnimationFrame(raf);
  }
}
