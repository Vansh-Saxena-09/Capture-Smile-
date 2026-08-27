// showdown.js — the friendly challenge: capture a squad one-by-one through the exact same pipeline
// (that's the fairness guarantee), then rank them and hand out awards. Photos never leave the
// browser — only the derived numbers are POSTed to /api/showdown.
//
// These screens render statically, with no reveal animation: a group is usually crowded around one
// phone, so results should be readable the instant they land.
import { $, $$, DIMENSIONS, showScreen, toast, escapeHtml } from "./ui.js";
import { computeShowdown } from "./api.js";
import { openCapture } from "./capture.js";
import { scanAndAnalyze } from "./scan.js";
import { compactPersonHTML, hydrateImages } from "./render.js";

const MIN = 2, MAX = 10;
let count = 3;
let names = [];
let entries = [];   // [{ name, result }] — kept only in memory, for this session
let idx = 0;
let lastShowdown = null;

export function initFriends() {
  buildCountGrid();
  buildNameInputs();
  const start = $("#btn-start-showdown");
  if (start) start.addEventListener("click", startShowdown);
}

export function openFriendsSetup() {
  showScreen("screen-friends-setup");
}

function buildCountGrid() {
  const grid = $("#count-grid");
  if (!grid) return;
  grid.innerHTML = "";
  for (let n = MIN; n <= MAX; n++) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "count-btn" + (n === count ? " active" : "");
    b.textContent = String(n);
    b.setAttribute("aria-label", `${n} friends`);
    b.addEventListener("click", () => {
      count = n;
      $$(".count-btn", grid).forEach((x) => x.classList.remove("active"));
      b.classList.add("active");
      buildNameInputs();
    });
    grid.appendChild(b);
  }
}

function buildNameInputs() {
  const wrap = $("#names");
  if (!wrap) return;
  const prev = $$(".name-input", wrap).map((i) => i.value); // keep what's already typed
  wrap.innerHTML = "";
  for (let k = 0; k < count; k++) {
    const inp = document.createElement("input");
    inp.type = "text";
    inp.className = "name-input";
    inp.maxLength = 24;
    inp.placeholder = `Friend ${k + 1}`;
    inp.setAttribute("aria-label", `Name of friend ${k + 1}`);
    if (prev[k]) inp.value = prev[k];
    wrap.appendChild(inp);
  }
}

function currentNames() {
  return $$(".name-input", $("#names")).map((inp, k) => inp.value.trim() || `Friend ${k + 1}`);
}

function startShowdown() {
  names = currentNames();
  entries = [];
  idx = 0;
  captureNext();
}

function captureNext() {
  if (idx >= count) { finish(); return; }
  openCapture({
    title: `${names[idx]}, show us your smile`,
    who: `Player ${idx + 1} of ${count}`,
    onPhoto: onFriendPhoto,
  });
}

async function onFriendPhoto(blob, dataUrl) {
  try {
    const result = await scanAndAnalyze(blob, dataUrl);
    entries.push({ name: names[idx], result });
    idx++;
    captureNext();
  } catch (err) {
    toast((err && err.message) || "That one didn't take — let's retake it.");
    captureNext(); // idx unchanged → retake the same player
  }
}

function sanitize(s) {
  // Names go into a tab/newline-delimited body, so strip those delimiters out.
  return String(s == null ? "" : s).replace(/[\t\n\r]+/g, " ").trim() || "Friend";
}

async function finish() {
  const tsv = entries.map(({ name, result }) => {
    const dims = DIMENSIONS.map((d) => (Number(result.dimensions[d]) || 0).toFixed(3)).join(",");
    return [
      sanitize(name),
      (Number(result.score) || 0).toFixed(1),
      String(result.character.matchPct),
      sanitize(result.vibe),
      sanitize(result.character.name),
      dims,
    ].join("\t");
  }).join("\n");

  try {
    const data = await computeShowdown(tsv);
    lastShowdown = data;
    renderShowdownInto($("#showdown-content"), data);
    showScreen("screen-showdown");
  } catch (err) {
    toast((err && err.message) || "Couldn't tally the challenge. Try again.");
    showScreen("screen-friends-setup");
  }
}

function renderShowdownInto(container, data) {
  const ranking = data.ranking || [];
  const awards = data.awards || [];
  const medals = ["🥇", "🥈", "🥉"];
  const champ = ranking.find((r) => r.place === 1);

  const podium = ranking.map((r) =>
    `<div class="podium-row${r.place === 1 ? " gold" : ""}">`
    + `<span class="podium-medal">${medals[r.place - 1] || "#" + r.place}</span>`
    + `<div style="flex:1;min-width:0"><div class="podium-name">${escapeHtml(r.name)}</div>`
    + `<div class="podium-sub">${escapeHtml(r.vibe)} · ${escapeHtml(r.characterName)}</div></div>`
    + `<span class="podium-score">${(Number(r.score) || 0).toFixed(1)}</span>`
    + `</div>`
  ).join("");

  const awardCards = awards.map((a) =>
    `<div class="award">`
    + `<div class="aemoji">${escapeHtml(a.emoji)}</div>`
    + `<div class="atitle">${escapeHtml(a.title)}</div>`
    + `<div class="awinner">${escapeHtml(a.winnerName)}</div>`
    + `<div class="adetail">${escapeHtml(a.detail)}</div>`
    + `</div>`
  ).join("");

  // Render each person's full card in ranking order, matching a ranking row to its stored entry.
  const pool = entries.slice();
  const people = ranking.map((r) => {
    let i = pool.findIndex((e) => e.name === r.name);
    if (i < 0) i = 0;
    const entry = pool.splice(i, 1)[0];
    return entry ? compactPersonHTML(entry.name, entry.result, r.place) : "";
  }).join("");

  container.innerHTML =
    `<div class="capture-head"><p class="who">Friendly challenge</p>`
    + `<h2>${champ ? escapeHtml(champ.name) + " takes the crown" : "The results"}</h2></div>`
    + `<div class="podium">${podium}</div>`
    + `<div class="divider"><span>Superlatives</span></div>`
    + `<div class="awards">${awardCards}</div>`
    + `<div class="divider"><span>Every smile, cast</span></div>`
    + people;

  hydrateImages(container);
}

export function shareShowdownText() {
  if (!lastShowdown) return "We just ran a friendly smile challenge at Capture Smile.";
  const ranking = lastShowdown.ranking || [];
  const champ = ranking.find((r) => r.place === 1);
  return champ
    ? `🏆 ${champ.name} wins our friendly smile challenge with ${(Number(champ.score) || 0).toFixed(1)}/10! We ranked ${ranking.length} smiles at Capture Smile.`
    : `We ranked ${ranking.length} smiles in a friendly challenge at Capture Smile.`;
}

export function hasShowdown() { return !!lastShowdown; }
