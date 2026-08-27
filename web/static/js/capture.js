// capture.js — camera preview + photo capture, with a graceful upload fallback.
// Emits the captured JPEG (Blob) plus a data URL (for the scan preview) through an onPhoto
// callback, so this module imports no flow module and nothing can cycle through it.
import { $, showScreen } from "./ui.js";

let stream = null;
let onPhotoCb = null;
let wired = false;
let video, placeholder, captureBtn, uploadBtn, fileInput, titleEl, whoEl;

/** Wire the capture controls once. Safe to call more than once. */
export function initCapture() {
  if (wired) return;
  video = $("#cam");
  placeholder = $("#cam-placeholder");
  captureBtn = $("#btn-capture");
  uploadBtn = $("#btn-upload");
  fileInput = $("#file-input");
  titleEl = $("#capture-title");
  whoEl = $("#capture-who");

  if (captureBtn) captureBtn.addEventListener("click", takePhoto);
  if (uploadBtn) uploadBtn.addEventListener("click", () => fileInput && fileInput.click());
  if (fileInput) fileInput.addEventListener("change", onFilePicked);
  wired = true;
}

/** Open the capture screen for one person and start the camera. `onPhoto(blob, dataUrl)` fires once. */
export function openCapture({ title, who, onPhoto } = {}) {
  onPhotoCb = onPhoto || null;
  if (titleEl) titleEl.textContent = title || "Show us your smile";
  if (whoEl) {
    if (who) { whoEl.textContent = who; whoEl.hidden = false; }
    else { whoEl.hidden = true; whoEl.textContent = ""; }
  }
  if (fileInput) fileInput.value = "";
  showScreen("screen-capture");
  startCamera();
}

async function startCamera() {
  stopCamera();
  if (captureBtn) captureBtn.disabled = false;
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { cameraUnavailable(); return; }
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 1280 } },
      audio: false,
    });
    if (video) {
      video.srcObject = stream;
      video.style.transform = "scaleX(-1)"; // mirror, like a selfie
      video.style.display = "block";
      await video.play().catch(() => {});
    }
    if (placeholder) placeholder.style.display = "none";
  } catch (e) {
    cameraUnavailable(); // no camera / denied → upload still works
  }
}

function cameraUnavailable() {
  if (captureBtn) captureBtn.disabled = true;
  if (video) video.style.display = "none";
  if (placeholder) placeholder.style.display = "flex";
}

/** Stop the camera and release the device light. Called on capture, back, and between friends. */
export function stopCamera() {
  if (stream) { stream.getTracks().forEach((t) => t.stop()); stream = null; }
  if (video) video.srcObject = null;
}

function takePhoto() {
  if (!stream || !video) return;
  const vw = video.videoWidth, vh = video.videoHeight;
  if (!vw || !vh) return;
  const side = Math.min(vw, vh);
  const sx = (vw - side) / 2, sy = (vh - side) / 2;
  const size = 720;
  const canvas = document.createElement("canvas");
  canvas.width = size; canvas.height = size;
  const ctx = canvas.getContext("2d");
  ctx.translate(size, 0); ctx.scale(-1, 1); // match the mirrored preview
  ctx.drawImage(video, sx, sy, side, side, 0, 0, size, size);
  const dataUrl = canvas.toDataURL("image/jpeg", 0.9);
  canvas.toBlob((blob) => { if (blob) deliver(blob, dataUrl); }, "image/jpeg", 0.9);
}

function onFilePicked(e) {
  const file = e.target.files && e.target.files[0];
  if (!file) return;
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.onload = () => {
    URL.revokeObjectURL(url);
    // Downscale big phone photos so we stay well under the server's size cap.
    const maxD = 1280;
    const scale = Math.min(1, maxD / Math.max(img.naturalWidth, img.naturalHeight));
    const w = Math.max(1, Math.round(img.naturalWidth * scale));
    const h = Math.max(1, Math.round(img.naturalHeight * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w; canvas.height = h;
    canvas.getContext("2d").drawImage(img, 0, 0, w, h);
    const dataUrl = canvas.toDataURL("image/jpeg", 0.9);
    canvas.toBlob((blob) => deliver(blob || file, dataUrl), "image/jpeg", 0.9);
  };
  img.onerror = () => { URL.revokeObjectURL(url); deliver(file, null); };
  img.src = url;
}

function deliver(blob, dataUrl) {
  stopCamera();
  const cb = onPhotoCb;
  onPhotoCb = null; // one-shot, so a stray second click can't double-fire
  if (cb) cb(blob, dataUrl);
}
