import { fileURLToPath } from "node:url";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { deflateSync } from "node:zlib";

// Run from anywhere: node tools/generate_icon.mjs
// Draws the mod's icon as 64x64 pixel art in the round, cog-framed style of Create and its add-ons: a brass cogwheel
// round a window of sky, the airliner climbing out through it, her tail and wing over the cog. Writes it scaled 8x for
// the CurseForge project and the README (docs/icon.png) and 2x as the logo the mod list shows
// (assets/skyliners/icon.png).
const ROOT = fileURLToPath(new URL("..", import.meta.url));
const outputs = [
  { path: resolve(ROOT, "docs/icon.png"), scale: 8 },
  { path: resolve(ROOT, "src/main/resources/assets/skyliners/icon.png"), scale: 2 },
];
const SIZE = 64;
const CENTRE = 32;

// ---------- PNG ----------
function crcOf(n) {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
}
const crcTable = Array.from({ length: 256 }, (_, n) => crcOf(n));
function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const typeAndData = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(typeAndData));
  return Buffer.concat([length, typeAndData, crc]);
}
function encodePng(image) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(image.w, 0);
  header.writeUInt32BE(image.h, 4);
  header[8] = 8;
  header[9] = 6;
  const stride = image.w * 4;
  const raw = Buffer.alloc((stride + 1) * image.h);
  for (let y = 0; y < image.h; y++) {
    Buffer.from(image.px.buffer, image.px.byteOffset + y * stride, stride).copy(raw, y * (stride + 1) + 1);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

// ---------- drawing ----------
const canvas = (w = SIZE, h = SIZE) => ({ w, h, px: new Uint8Array(w * h * 4) });
// Calls visit(x, y) for every pixel of an image's size, row by row.
function eachPixel(image, visit) {
  for (let y = 0; y < image.h; y++) {
    for (let x = 0; x < image.w; x++) visit(x, y);
  }
}
const hex = (s) => [1, 3, 5].map((i) => parseInt(s.slice(i, i + 2), 16));
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const clamp = (v, lo = 0, hi = 1) => Math.max(lo, Math.min(hi, v));
function put(image, x, y, colour, alpha = 255) {
  x = Math.floor(x);
  y = Math.floor(y);
  if (x < 0 || y < 0 || x >= image.w || y >= image.h) return;
  const i = (y * image.w + x) * 4;
  const t = alpha / 255;
  for (let k = 0; k < 3; k++) image.px[i + k] = Math.round(image.px[i + k] * (1 - t) + colour[k] * t);
  image.px[i + 3] = Math.max(image.px[i + 3], alpha);
}
const outside = (image, x, y) => x < 0 || y < 0 || x >= image.w || y >= image.h;
const alphaAt = (image, x, y) => (outside(image, x, y) ? 0 : image.px[(y * image.w + x) * 4 + 3]);
function overPixel(target, layer, x, y) {
  const i = (y * layer.w + x) * 4;
  if (layer.px[i + 3]) put(target, x, y, [layer.px[i], layer.px[i + 1], layer.px[i + 2]], layer.px[i + 3]);
}
const over = (target, layer) => eachPixel(layer, (x, y) => overPixel(target, layer, x, y));
const touchesOpaque = (layer, x, y) =>
  alphaAt(layer, x - 1, y) || alphaAt(layer, x + 1, y) || alphaAt(layer, x, y - 1) || alphaAt(layer, x, y + 1);
// A dark one-pixel outline round everything opaque in a layer, so it reads at small sizes.
function outline(layer, colour) {
  const result = canvas(layer.w, layer.h);
  eachPixel(layer, (x, y) => !alphaAt(layer, x, y) && touchesOpaque(layer, x, y) && put(result, x, y, colour));
  over(result, layer);
  return result;
}
function shadow(layer, dx, dy, alpha) {
  const result = canvas(layer.w, layer.h);
  eachPixel(layer, (x, y) => alphaAt(layer, x - dx, y - dy) && put(result, x, y, OUTLINE, alpha));
  return result;
}
function noise(x, y, seed = 0) {
  let h = (Math.imul(x, 374761393) + Math.imul(y, 668265263) + Math.imul(seed, 1442695041)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}
const rampAt = (colours, t) => colours[Math.round(clamp(t) * (colours.length - 1))];
// An ordered dither, so the sky's bands blend into each other a pixel at a time, and how many bands it has.
const BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
  .map((row) => row.map((v) => (v + 0.5) / 16));
const SKY_BANDS = 5;
// Where the light comes from: the upper left and out of the picture, as on Create's own icons.
const unit = (v) => v.map((c) => c / Math.hypot(...v));
const LIGHT = unit([-0.5, -0.75, 0.45]);

// Create's brass, its cogs' metal, lit from the upper left; an iron rim inside it.
const BRASS = ["#5a3417", "#7f4c1f", "#a8682a", "#c98a34", "#e2ab45", "#f3cc63", "#fde89a"].map(hex);
const IRON = ["#1f2328", "#33393f", "#4b5258"].map(hex);
const SKY = [[0, hex("#2b5aa8")], [0.55, hex("#5b93d6")], [1, hex("#b5dcf6")]];
const CLOUD = ["#9fb4cc", "#c8d6e6", "#e6eef7", "#ffffff"].map(hex);
// The airliner's own colours: white skin with grey shading, her navy tail and window bands.
const SKIN = ["#7d8794", "#a9b2bd", "#d3d9df", "#f4f6f8", "#ffffff"].map(hex);
const NAVY = ["#0f1f45", "#1a3270", "#27489c"].map(hex);
const WINDOW = hex("#24406e");
const ENGINE = ["#4f5965", "#6f7a87"].map(hex);
const OUTLINE = hex("#140d08");

// ---------- the cogwheel ----------
const TEETH = 12;
const TIP_RADIUS = 31.2;
const ROOT_RADIUS = 27.4;
const WINDOW_RADIUS = 22.6;
const RIM = 1.6;

// Whether a point at this angle and distance lies on a tooth, tapering from its root to its tip.
function onTooth(angle, d) {
  if (d > TIP_RADIUS) return false;
  if (d <= ROOT_RADIUS) return true;
  const pitch = (2 * Math.PI) / TEETH;
  const along = ((angle % pitch) + pitch) % pitch - pitch / 2;
  const half = pitch * (0.3 - 0.08 * (d - ROOT_RADIUS) / (TIP_RADIUS - ROOT_RADIUS));
  return Math.abs(along) <= half;
}
// The cog's colour at a pixel, or null off it: an iron rim round the window, then brass, its inner edge bevelled in
// shadow above and lit below, as a ring sunk round a window would be.
function cogAt(x, y) {
  const dx = x + 0.5 - CENTRE, dy = y + 0.5 - CENTRE, d = Math.hypot(dx, dy);
  if (d <= WINDOW_RADIUS || !onTooth(Math.atan2(dy, dx), d)) return null;
  if (d < WINDOW_RADIUS + RIM) return rampAt(IRON, 0.5 + (dy / d) * 0.5);
  const bevel = d < WINDOW_RADIUS + RIM + 1.2 ? (dy > 0 ? 0.2 : -0.25) : 0;
  const lit = 0.55 - (dx / TIP_RADIUS) * 0.3 - (dy / TIP_RADIUS) * 0.35 + (noise(x, y, 3) - 0.5) * 0.1;
  return rampAt(BRASS, lit + bevel);
}
// A layer drawn by a function giving each pixel's colour, or null where it leaves it clear.
function painted(colourAt) {
  const layer = canvas();
  const paint = (x, y, colour) => colour && put(layer, x, y, colour);
  eachPixel(layer, (x, y) => paint(x, y, colourAt(x, y)));
  return layer;
}
const cog = () => outline(painted(cogAt), OUTLINE);

// ---------- the sky in the window ----------
// The sky's colour at a pixel inside the window, deep blue above and pale near the horizon, banded and dithered.
function skyAt(x, y) {
  if (Math.hypot(x + 0.5 - CENTRE, y + 0.5 - CENTRE) > WINDOW_RADIUS + 0.5) return null;
  const p = clamp((y - (CENTRE - WINDOW_RADIUS)) / (2 * WINDOW_RADIUS));
  let s = 0;
  while (s < SKY.length - 2 && p > SKY[s + 1][0]) s++;
  const t = clamp((p - SKY[s][0]) / (SKY[s + 1][0] - SKY[s][0]));
  return mix(SKY[s][1], SKY[s + 1][1], Math.min(1, Math.floor(t * SKY_BANDS + BAYER[y & 3][x & 3]) / SKY_BANDS));
}
function sky() {
  const layer = painted(skyAt);
  const puffs = [
    { x: 16, y: 44, r: 4.5 }, { x: 21, y: 46, r: 4 }, { x: 12, y: 47, r: 3.5 },
    { x: 44, y: 46, r: 3.5 }, { x: 49, y: 44, r: 4 }, { x: 53, y: 47, r: 3 },
    { x: 38, y: 19, r: 2.5 }, { x: 42, y: 18, r: 3 },
  ];
  puffs.forEach((p, n) => puff(layer, p, n));
  return layer;
}

// A puff of cloud, a ball lit from the upper left, kept inside the window.
function puff(layer, p, seed) {
  for (let y = Math.floor(p.y - p.r); y <= Math.ceil(p.y + p.r); y++) {
    for (let x = Math.floor(p.x - p.r); x <= Math.ceil(p.x + p.r); x++) {
      const dx = x + 0.5 - p.x, dy = y + 0.5 - p.y;
      if (Math.hypot(dx, dy) > p.r || Math.hypot(x + 0.5 - CENTRE, y + 0.5 - CENTRE) > WINDOW_RADIUS) continue;
      const nx = dx / p.r, ny = dy / p.r, nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
      const lit = Math.max(0, nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]);
      put(layer, x, y, rampAt(CLOUD, 0.25 + 0.8 * lit + (noise(x, y, seed) - 0.5) * 0.1));
    }
  }
}

// ---------- the airliner, in side view, nose to the right, climbing ----------
// Her parts in her own frame: x along her from tail to nose, y up, in pixels.
const NOSE = 21;
const TAIL = -21;
function fuselageTop(x) {
  return x <= 15 ? 3.7 : 3.7 * Math.sqrt(Math.max(0, 1 - ((x - 15) / 6.5) ** 2));
}
function fuselageBottom(x) {
  if (x < -12) return -3.7 + (-12 - x) * 0.52;
  return x <= 15 ? -3.7 : -3.7 * Math.sqrt(Math.max(0, 1 - ((x - 15) / 6.5) ** 2)) + 0.4 * (x - 15) / 6.5;
}
function inPolygon(x, y, points) {
  let inside = false;
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
    const [xi, yi] = points[i], [xj, yj] = points[j];
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}
const inEllipse = (x, y, cx, cy, rx, ry) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1;
const FIN = [[-11.5, 3], [-18, 15], [-22.5, 15], [-21.2, 3]];
const TAILPLANE = [[-14.5, -0.2], [-20.5, -2.6], [-22.5, -2.6], [-19.5, -0.2]];
const WING = [[6, -2.4], [-6, -2.8], [-12, -10.5], [-7.5, -10.5]];
const ENGINES = [{ x: 1, y: -5.8, rx: 3.6, ry: 1.9 }, { x: -5.4, y: -8.9, rx: 3.1, ry: 1.6 }];

// The colour of her at a point in her own frame, or null where she is not.
function airlinerAt(x, y) {
  for (const e of ENGINES) {
    if (inEllipse(x, y, e.x, e.y, e.rx, e.ry)) return x > e.x + e.rx - 1.4 ? IRON[0] : ENGINE[y > e.y ? 1 : 0];
  }
  if (inPolygon(x, y, WING)) return rampAt(SKIN, 0.3 + (x + 12) / 30 + (y + 10) / 40);
  if (x >= TAIL && x <= NOSE && y <= fuselageTop(x) && y >= fuselageBottom(x)) return fuselageAt(x, y);
  if (inPolygon(x, y, FIN)) return y > 11 ? NAVY[2] : y > 6 && x < -15.5 - (y - 6) * 0.25 ? NAVY[2] : NAVY[1];
  if (inPolygon(x, y, TAILPLANE)) return SKIN[1];
  return null;
}
function fuselageAt(x, y) {
  if (x > 16.6 && y > 0.9 && y < 2.6 && x < 19.6) return NAVY[0];
  if (x > -12 && x < 14 && ((y > 1.1 && y < 2.0) || (y > -1.1 && y < -0.2))) return WINDOW;
  const share = (y - fuselageBottom(x)) / Math.max(0.1, fuselageTop(x) - fuselageBottom(x));
  return rampAt(SKIN, 0.2 + share * 0.85);
}
// The airliner, her middle at a pixel, climbing at so many degrees: each pixel turned back into her own frame.
function airliner(centreX, centreY, degrees) {
  const angle = (degrees * Math.PI) / 180;
  const cos = Math.cos(angle), sin = Math.sin(angle);
  const at = (dx, dy) => airlinerAt(dx * cos + dy * sin, -dx * sin + dy * cos);
  return outline(painted((x, y) => at(x + 0.5 - centreX, centreY - (y + 0.5))), OUTLINE);
}

function icon() {
  const image = canvas();
  over(image, sky());
  over(image, cog());
  const plane = airliner(31, 33, 17);
  over(image, shadow(plane, 1, 2, 110));
  over(image, plane);
  return image;
}

function upscale(image, k) {
  const result = canvas(image.w * k, image.h * k);
  const source = (x, y) => (Math.floor(y / k) * image.w + Math.floor(x / k)) * 4;
  eachPixel(result, (x, y) => result.px.set(image.px.subarray(source(x, y), source(x, y) + 4), (y * result.w + x) * 4));
  return result;
}

const image = icon();
for (const { path, scale } of outputs) {
  mkdirSync(resolve(path, ".."), { recursive: true });
  writeFileSync(path, encodePng(upscale(image, scale)));
  console.log(`wrote ${path} (${SIZE * scale}x${SIZE * scale})`);
}
