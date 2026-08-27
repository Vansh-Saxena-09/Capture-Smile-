// app.js — the entry module. Wires the SPA together: home → capture → scan → result,
// plus the friendly challenge flow and sharing.
import { $, showScreen, toast } from "./ui.js";
import { initCapture, openCapture, stopCamera } from "./capture.js";
import { scanAndAnalyze } from "./scan.js";
import { renderResultInto, soloShareText } from "./render.js";
import { initFriends, openFriendsSetup, shareShowdownText, hasShowdown } from "./showdown.js";

let lastSolo = null;

function init() {
  initCapture();
  initFriends();

  on("#mode-solo", "click", startSolo);
  on("#mode-friends", "click", openFriendsSetup);
  on("#btn-home", "click", goHome);
  on("#capture-back", "click", goHome);
  on("#friends-back", "click", goHome);
  on("#btn-again", "click", startSolo);
  on("#btn-again-showdown", "click", openFriendsSetup);
  on("#btn-share", "click", () => { if (lastSolo) share(soloShareText(lastSolo)); });
  on("#btn-share-showdown", "click", () => { if (hasShowdown()) share(shareShowdownText()); });

  const brand = document.querySelector(".brand");
  if (brand) {
    brand.style.cursor = "pointer";
    brand.setAttribute("role", "button");
    brand.setAttribute("tabindex", "0");
    brand.addEventListener("click", goHome);
    brand.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); goHome(); } });
  }
}

function on(sel, ev, fn) { const el = $(sel); if (el) el.addEventListener(ev, fn); }

function goHome() { stopCamera(); showScreen("screen-home"); }

function startSolo() {
  openCapture({ title: "Show us your smile", who: "Capture yourself", onPhoto: onSoloPhoto });
}

async function onSoloPhoto(blob, dataUrl) {
  try {
    const result = await scanAndAnalyze(blob, dataUrl);
    lastSolo = result;
    renderResultInto($("#result-content"), result);
    showScreen("screen-result");
  } catch (err) {
    toast((err && err.message) || "Something glitched. Give it another go.");
    startSolo();
  }
}

async function share(text) {
  if (navigator.share) {
    try { await navigator.share({ title: "Capture Smile", text }); return; }
    catch (e) { if (e && e.name === "AbortError") return; } // user dismissed the sheet
  }
  try { await navigator.clipboard.writeText(text); toast("Copied — go show them ✨"); }
  catch { toast("Couldn't share automatically — select the text to copy it."); }
}

if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
else init();
