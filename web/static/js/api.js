// api.js — the two backend calls. Both return parsed JSON or throw an Error with a friendly message.

async function post(url, body, contentType) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": contentType },
    body,
  });
  const text = await res.text();
  let data = null;
  try {
    data = JSON.parse(text);
  } catch {
    throw new Error("The studio sent something we couldn't read. Try again.");
  }
  // Success payloads have no `ok` field; only error payloads set `ok:false`.
  if (!res.ok || (data && data.ok === false)) {
    throw new Error((data && data.error) || "Something glitched. Give it another go.");
  }
  return data;
}

/** POST the raw image bytes; the server hashes, analyses, and discards them. */
export function analyzeSmile(blob) {
  return post("/api/analyze", blob, "application/octet-stream");
}

/** POST the tab-delimited squad summaries (never photos) and get back ranking + awards. */
export function computeShowdown(tsv) {
  return post("/api/showdown", tsv, "text/plain; charset=utf-8");
}
