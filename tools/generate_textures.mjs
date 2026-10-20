import { fileURLToPath } from "node:url";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { deflateSync } from "node:zlib";

// Run from anywhere: node tools/generate_textures.mjs
// Writes the 16x16 airframe textures, the item icons and the control panels' pictures. The look is an airliner's:
// white skin with faint panel seams and rivet rows, an Airbus-blue livery, smoked cabin glass in white frames, grey
// cabin carpet.
const ROOT = fileURLToPath(new URL("..", import.meta.url));
const blockOutput = resolve(ROOT, "src/main/resources/assets/aerolarge/textures/block");
const itemOutput = resolve(ROOT, "src/main/resources/assets/aerolarge/textures/item");
mkdirSync(blockOutput, { recursive: true });
mkdirSync(itemOutput, { recursive: true });

const SIZE = 16;
const WHITE = [0xe8, 0xec, 0xf0];
const SEAM = [0xc4, 0xcb, 0xd3];
const RIVET = [0xb0, 0xb8, 0xc1];
const BLUE = [0x1c, 0x3d, 0x8c];
const BLUE_SEAM = [0x15, 0x2e, 0x6b];
const BLUE_RIVET = [0x2b, 0x52, 0xa8];
const GLASS = [0x2a, 0x3c, 0x55, 0xb0];
const GLASS_SHINE = [0x8f, 0xb4, 0xd6, 0xc8];
const COCKPIT_GLASS = [0x1b, 0x24, 0x33, 0xc8];
const FRAME_DARK = [0x3a, 0x40, 0x48];
const CARPET = [0x5a, 0x63, 0x73];
const CARPET_DARK = [0x4b, 0x53, 0x62];
const CARPET_LIGHT = [0x6b, 0x75, 0x87];
const INTAKE = [0x24, 0x27, 0x2c];
const BLADE = [0x55, 0x5b, 0x63];
const SPINNER = [0xc9, 0xcf, 0xd6];
const STEEL = [0x8a, 0x90, 0x97];
const STEEL_DARK = [0x5c, 0x62, 0x69];
const CHROME = [0xd3, 0xd8, 0xdd];
const RUBBER = [0x1d, 0x1e, 0x20];
const RUBBER_LIGHT = [0x33, 0x35, 0x38];
const HUB = [0x9a, 0xa0, 0xa6];
const PAPER = [0x1f, 0x5f, 0xb5];
const PAPER_LINE = [0x3f, 0x7d, 0xcc];
const INK = [0xf2, 0xf6, 0xfb];
const GOLD = [0xf0, 0xc4, 0x3a];
const CLEAR = [0, 0, 0, 0];

// Deterministic noise, reseeded per texture so adding one never changes the others.
let seed = 0;
function reseed(name) {
  seed = 0x41657230;
  for (const char of name) seed = Math.imul(seed ^ char.charCodeAt(0), 0x01000193) | 0;
}
function random() {
  seed = (seed + 0x6d2b79f5) | 0;
  let value = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  value = (value + Math.imul(value ^ (value >>> 7), 61 | value)) ^ value;
  return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
}

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
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
function encodePng(pixels) {
  const height = pixels.length;
  const width = pixels[0].length;
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // bit depth
  header[9] = 6; // RGBA
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0; // filter: none
    for (let x = 0; x < width; x++) {
      const [r, g, b, a = 255] = pixels[y][x];
      raw.set([r, g, b, a], y * (width * 4 + 1) + 1 + x * 4);
    }
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

/**
 * A texture painted pixel by pixel: paint(x, y) returns the colour, with a little noise mixed into opaque ones. A block
 * texture, unless it is given a size of its own.
 */
function texture(folder, name, paint, noise = 4, width = SIZE, height = SIZE) {
  reseed(name);
  const pixels = [];
  for (let y = 0; y < height; y++) {
    const row = [];
    for (let x = 0; x < width; x++) {
      const [r, g, b, a = 255] = paint(x, y);
      const n = a === 255 ? Math.round((random() - 0.5) * 2 * noise) : 0;
      row.push([clamp(r + n), clamp(g + n), clamp(b + n), a]);
    }
    pixels.push(row);
  }
  writeFileSync(resolve(folder, `${name}.png`), encodePng(pixels));
}
const clamp = (value) => Math.max(0, Math.min(255, value));

/** Skin plating: a seam along the top and left edges, a rivet row just inside them. */
function plating(base, seam, rivet) {
  return (x, y) => {
    if (x === 0 || y === 0) return seam;
    if ((y === 2 && x % 3 === 1) || (x === 2 && y % 3 === 1)) return rivet;
    return base;
  };
}

texture(blockOutput, "airframe_panel", plating(WHITE, SEAM, RIVET), 3);
texture(blockOutput, "livery_panel", plating(BLUE, BLUE_SEAM, BLUE_RIVET), 3);

// A rounded cabin window in its white frame, with a highlight across the upper corner; with its shade pulled down,
// the glass filled with the shade's pale plastic and a pull tab at its foot. A pane joined to one above or below runs
// on through that edge, so stacked panes make one tall window: the highlight in its top pane, the tab in its bottom.
for (const [joins, up, down] of [["", false, false], ["_bottom", true, false], ["_top", false, true], ["_middle", true, true]]) {
  const frame = (x, y) => {
    const dx = Math.max(Math.abs(x - 7.5) - 2.5, 0);
    const dy = Math.max(up ? 0 : 4 - y, down ? 0 : y - 11, 0);
    return Math.hypot(dx, dy);
  };
  texture(blockOutput, `cabin_window${joins}`, (x, y) => {
    const corner = frame(x, y);
    if (corner > 2.6) return x === 0 || y === 0 ? SEAM : WHITE;
    if (corner > 1.9) return FRAME_DARK;
    if (!up && x - y > -2 && x - y < 1 && y < 8) return GLASS_SHINE;
    return GLASS;
  });
  texture(blockOutput, `cabin_window${joins}_shaded`, (x, y) => {
    const corner = frame(x, y);
    if (corner > 2.6) return x === 0 || y === 0 ? SEAM : WHITE;
    if (corner > 1.9) return FRAME_DARK;
    if (!down && y >= 11 && x >= 6 && x <= 9) return [0x9c, 0xa0, 0xa6];
    return y % 3 === 0 ? [0xd6, 0xd2, 0xc8] : [0xe2, 0xde, 0xd4];
  });
}

// The windscreen: dark glass edge to edge, thin frame bars along the top and one side.
texture(blockOutput, "cockpit_window", (x, y) => {
  if (y === 0 || x === 0) return FRAME_DARK;
  if (x + y >= 18 && x + y <= 19) return GLASS_SHINE;
  return COCKPIT_GLASS;
});

// The windscreen cut to the nose's curve: dark glass with no frame, its own posts being the hull, lighter toward
// the top with a faint sheen across it, so each facet catches the light a little differently.
texture(blockOutput, "cockpit_glass", (x, y) => {
  const lift = Math.round((15 - y) * 1.2);
  const sheen = x + y >= 6 && x + y <= 8 ? 26 : 0;
  return [COCKPIT_GLASS[0] + lift + sheen, COCKPIT_GLASS[1] + lift + sheen, COCKPIT_GLASS[2] + lift + sheen, COCKPIT_GLASS[3]];
});

// Clear hull glass, for the moon pool's view down: nearly clear with a faint sky-blue tint and a glint across it.
texture(blockOutput, "hull_glass", (x, y) => (x + y >= 9 && x + y <= 10 ? [0xf2, 0xfa, 0xff, 0x70] : [0xc8, 0xe4, 0xf4, 0x30]), 0);

// Cabin carpet: a small diamond pattern on grey-blue.
texture(blockOutput, "cabin_floor", (x, y) => {
  const d = (Math.abs((x % 8) - 4) + Math.abs((y % 8) - 4));
  if (d === 4) return CARPET_LIGHT;
  if ((x + y) % 2 === 0 && d < 2) return CARPET_DARK;
  return CARPET;
}, 6);
// Its underside, a ceiling: smooth white ceiling panels, a groove along two edges with a faint shadow inside it. Its
// sides: the carpet's edge along the top over a brushed steel trim, and below it a white fascia.
texture(blockOutput, "cabin_floor_under", (x, y) => {
  if (x === 0 || y === 0) return [0xc8, 0xcd, 0xd3];
  return x === 1 || y === 1 ? [0xe0, 0xe3, 0xe7] : [0xec, 0xee, 0xf0];
}, 2);
texture(blockOutput, "cabin_floor_edge", (x, y) => {
  if (y <= 1) return x % 4 === 1 ? CARPET_LIGHT : y === 1 ? CARPET_DARK : CARPET;
  if (y === 2) return x % 3 === 0 ? [0xd2, 0xd8, 0xde] : [0xb0, 0xb8, 0xc0];
  return y === 3 ? [0xd8, 0xdc, 0xe1] : [0xe8, 0xec, 0xf0];
}, 2);

// The engine intake: the fan's blades swept around a polished spinner.
texture(blockOutput, "engine_intake", (x, y) => {
  const dx = x - 7.5;
  const dy = y - 7.5;
  const r = Math.hypot(dx, dy);
  if (r < 2.2) return SPINNER;
  const angle = Math.atan2(dy, dx) + r * 0.18;
  const blade = (angle / (Math.PI * 2) * 12 + 12) % 1;
  return blade < 0.35 ? BLADE : INTAKE;
}, 3);

// Landing gear strut: a chromed oleo piston between two steel collars.
texture(blockOutput, "landing_gear", (x, y) => {
  if (y % 8 === 0 || y % 8 === 1) return STEEL_DARK;
  if (x >= 5 && x <= 10) return x === 6 ? INK : CHROME;
  return STEEL;
}, 4);

// Tire tread: rubber with chevron grooves, as seen from the side and from above alike.
texture(blockOutput, "landing_gear_tire", (x, y) => {
  if ((x + Math.abs(y % 6 - 3)) % 6 === 0) return RUBBER_LIGHT;
  if (x >= 6 && x <= 9 && y >= 6 && y <= 9) return HUB;
  return RUBBER;
}, 5);

// The door: a white leaf in a dark frame, a round porthole up top and a recessed handle below.
texture(blockOutput, "airliner_door_top", (x, y) => {
  if (x === 0 || x === 15 || y === 0) return FRAME_DARK;
  const r = Math.hypot(x - 7.5, y - 7.5);
  if (r < 2.7) return r < 1.6 && x < 8 && y < 8 ? GLASS_SHINE : GLASS;
  if (r < 3.5) return SEAM;
  return WHITE;
}, 2);
texture(blockOutput, "airliner_door_bottom", (x, y) => {
  if (x === 0 || x === 15 || y === 15) return FRAME_DARK;
  if (y >= 3 && y <= 5 && x >= 10 && x <= 13) return y === 4 ? FRAME_DARK : STEEL;
  if (y === 12) return BLUE;
  return WHITE;
}, 2);
// The plug doors in the hull's sides: one picture of the whole panel, two blocks wide and two or three tall, which
// their renderer lays across the door's blocks; for a door hinged at its side (drawn hinged on the left, seen from
// outside, and mirrored for one hinged on the right) or along its top. White skin like the plating round the doorway,
// the door's seam (a grey gap, a pale edge inside it) right round the panel, one rounded window in the middle of its
// top framed like the cabin windows, and one flush handle on the edge away from the hinge (low down in the middle of a
// door hinged on top), the red flag that shows the door armed beside it. The inside is the same picture seen from
// behind, the window in the middle and the handle by the same edge, on the off-white plastic of the cabin's lining
// (the overhead bins'): the renderer bulges that lining out of the panel as a padded cushion, the window set deep into
// it and the handle on it, so the window and the handle here are where PlugDoorMesh cuts and raises them. The window
// is a hole right through the panel, so its glass is left clear in both pictures: the renderer glazes it with
// plug_door_glass, laid on a pixel to a pixel of the door from the picture's ninth pixel across and five above the
// window's straight sides, so the window takes its middle.
const ARMED = [0xc8, 0x2c, 0x2c];
const DOOR_LINING = [0xe6, 0xe4, 0xde];
texture(blockOutput, "plug_door_glass", (x, y) => (x - y >= 0 && x - y <= 1 && y >= 4 && y <= 8 ? [0xf2, 0xfa, 0xff, 0x70] : [0xb8, 0xd6, 0xea, 0x40]), 0);
for (const hinge of ["side", "top"]) {
  for (const rows of [2, 3]) {
    const width = 2 * SIZE;
    const height = rows * SIZE;
    // The window's straight sides run down from this row for five more: lower in a three-row door, in the lining's
    // crown. Then the handle's row (below the window's reveal on a door hinged at its side), its first and last pixel
    // across, and the flag's first pixel across.
    const windowTop = 8 + 4 * (rows - 2);
    const handleY = hinge === "side" ? windowTop + 13 : height - 6;
    const [handleFrom, handleTo] = hinge === "side" ? [21, 27] : [12, 19];
    const flagX = hinge === "side" ? handleFrom - 4 : handleTo + 2;
    for (const [side, skin] of [["", WHITE], ["_inside", DOOR_LINING]]) {
      texture(blockOutput, `plug_door_${hinge}_${rows}${side}`, (x, y) => {
        const fromEdge = Math.min(x, width - 1 - x, y, height - 1 - y);
        if (fromEdge === 0) return STEEL_DARK;
        if (fromEdge === 1) return SEAM;
        const dx = Math.max(Math.abs(x - 15.5) - 1.5, 0);
        const dy = Math.max(windowTop - y, y - windowTop - 5, 0);
        const corner = Math.hypot(dx, dy);
        if (corner <= 1.9) return CLEAR;
        if (corner <= 2.6) return FRAME_DARK;
        if (Math.abs(y - handleY) <= 1) {
          if (x >= handleFrom && x <= handleTo) return y === handleY && x > handleFrom && x < handleTo ? STEEL : FRAME_DARK;
          if (x >= flagX && x <= flagX + 1) return ARMED;
        }
        return skin;
      }, 2, width, height);
    }
  }
}
texture(itemOutput, "airliner_door", (x, y) => {
  if (x < 4 || x > 11) return CLEAR;
  if (x === 4 || x === 11 || y === 0 || y === 15) return FRAME_DARK;
  if (Math.hypot(x - 7.5, y - 4) < 1.8) return GLASS;
  if (x === 10 && y >= 9 && y <= 10) return STEEL_DARK;
  if (y === 13) return BLUE;
  return WHITE;
}, 0);

// The airstair controller: a grey service panel in a yellow-and-black hazard border, a white staircase glyph on it.
const HAZARD = [0xe8, 0xb5, 0x1e];
const PANEL_GREY = [0x6a, 0x71, 0x79];
texture(blockOutput, "airstair_controller", (x, y) => {
  if (x === 0 || y === 0 || x === 15 || y === 15 || x === 1 || y === 1 || x === 14 || y === 14) {
    return (x + y) % 4 < 2 ? HAZARD : RUBBER;
  }
  // Five steps rising to the right, each two pixels.
  const step = Math.floor((x - 3) / 2);
  if (x >= 3 && x <= 12 && y <= 12 && y >= 11 - step * 2) return INK;
  return PANEL_GREY;
}, 3);
// Cabin light: a warm white diffuser in a thin skin-coloured frame, brightest in the middle.
texture(blockOutput, "cabin_light", (x, y) => {
  if (x === 0 || y === 0 || x === 15 || y === 15) return SEAM;
  if (x === 1 || y === 1 || x === 14 || y === 14) return WHITE;
  const glow = 1 - Math.hypot(x - 7.5, y - 7.5) / 11;
  return [clamp(240 + glow * 15), clamp(236 + glow * 19), clamp(214 + glow * 30)];
}, 2);
// At night the same diffuser glows a soft blue, a shade dimmer than the strips', in its frame left dark.
texture(blockOutput, "cabin_light_night", (x, y) => {
  if (x === 0 || y === 0 || x === 15 || y === 15) return [0x22, 0x27, 0x30];
  if (x === 1 || y === 1 || x === 14 || y === 14) return [0x2c, 0x33, 0x40];
  const glow = 1 - Math.hypot(x - 7.5, y - 7.5) / 11;
  return [clamp(Math.round(24 + 30 * glow)), clamp(Math.round(40 + 45 * glow)), clamp(Math.round(120 + 90 * glow))];
}, 2);
// The call button's cap: bright yellow so the ground crew find it.
texture(blockOutput, "airstair_call_button", (x, y) => (x + y) % 5 === 0 ? [0xd0, 0x9f, 0x14] : HAZARD, 3);
// The airstair button beside the controller (see AirstairButtonBlock): its cap the call button's amber, lit from
// behind, the middle six pixels the face, with the controller's staircase on it in three steps rising to the right in
// dark amber; pressed, the cap glows brighter, nearly white, the stairs a deeper amber on it. Its bezel: dark steel,
// a lighter line along the top of each row of brushing.
{
  const STAIRS = ["....##", "....##", "..####", "..####", "######", "######"];
  const cap = (glow, glyph) => (x, y) => (STAIRS[y - 5]?.[x - 5] === "#" ? glyph : glow);
  texture(blockOutput, "airstair_button", cap([0xf6, 0xae, 0x22], [0x5a, 0x34, 0x08]), 1);
  texture(blockOutput, "airstair_button_pressed", cap([0xff, 0xec, 0xa8], [0xa8, 0x6c, 0x10]), 1);
  texture(blockOutput, "airstair_button_bezel", (x, y) => (y % 3 === 0 ? [0x6e, 0x74, 0x7c] : [0x4a, 0x50, 0x57]), 2);
}
// The integral airstairs, drawn by their renderer a pixel of texture to a pixel of the airliner. A tread: dark grey
// non-slip plate, ribbed across with raised studs between the ribs; its nosing, safety yellow, a shade darker along
// its front edge. The stringers: brushed aluminium, streaked lengthways, catching the light along their top edge. The
// handrails and their posts: polished aluminium, a bright line along them. The hatch below the door: the hull's white
// skin, two blocks wide, with its seam right round it and a flush latch in each top corner.
{
  const GRIP = [0x3a, 0x3e, 0x44];
  const RIB = [0x58, 0x5d, 0x64];
  const BRUSHED = [0xb4, 0xbc, 0xc4];
  texture(blockOutput, "airstair_tread", (x, y) => {
    if (y % 4 === 0) return RIB;
    if (y % 4 === 2 && x % 4 === (y % 8 === 2 ? 1 : 3)) return RIB;
    return GRIP;
  }, 4);
  texture(blockOutput, "airstair_nosing", (x, y) => (y >= 14 ? [0xc8, 0x98, 0x12] : HAZARD), 3);
  texture(blockOutput, "airstair_stringer", (x, y) => {
    if (y === 0) return [0xd8, 0xde, 0xe3];
    if (y === 15) return [0x8a, 0x93, 0x9b];
    const streak = Math.round(6 * Math.sin(y * 2.3) + 3 * Math.sin(y * 5.1 + x * 0.2));
    return [BRUSHED[0] + streak, BRUSHED[1] + streak, BRUSHED[2] + streak];
  }, 2);
  texture(blockOutput, "airstair_rail", (x, y) => (y % 4 === 0 ? [0xf0, 0xf3, 0xf6] : CHROME), 3);
  texture(blockOutput, "airstair_hatch", (x, y) => {
    const fromEdge = Math.min(x, 31 - x, y, 15 - y);
    if (fromEdge === 0) return STEEL_DARK;
    if (fromEdge === 1) return SEAM;
    const latch = y >= 3 && y <= 4 && ((x >= 4 && x <= 7) || (x >= 24 && x <= 27));
    if (latch) return y === 3 ? FRAME_DARK : STEEL;
    return WHITE;
  }, 2, 2 * SIZE, SIZE);
}

// The evacuation slides, drawn by their renderer a pixel of texture to a pixel of the airliner, across from the lane's
// middle (x 0) to its edge and down it (y). The lane: bright slide yellow, a faint weave across it, the darker seam
// down its middle where its two halves meet and a pale line where it meets the rail. Its underside and the beams under
// the rails: the grey of the slide's coated underside, seams down them. A rail, round it from its top (x 0), outward
// first, to its bottom (x 8) and back up its inside: yellow, shaded underneath, a highlight along its top and the
// silver reflective stripe along its outer side, its panels' seams across it. The girt bar: dark grey webbing with a
// red tag. What is still packed: the grey cover, the yellow of the folded slide showing at its folds, a red pull tab.
// The control in the cabin wall: a red panel in a yellow frame with a dark bezel, someone sliding down a slide in white
// on it, so it needs no reading, the handle's slot down its right side; the handle's red grip, its steel stem, and the
// lamp beside it, dark or lit green.
{
  const SLIDE = [0xf4, 0xc2, 0x1c];
  const SLIDE_DARK = [0xd6, 0xa2, 0x10];
  const SLIDE_LIGHT = [0xfc, 0xda, 0x5a];
  const UNDERSIDE = [0x8c, 0x90, 0x95];
  const UNDERSIDE_DARK = [0x72, 0x76, 0x7b];
  const STRIPE = [0xe4, 0xea, 0xf0];
  const SIGNAL_RED = [0xc8, 0x22, 0x1c];
  texture(blockOutput, "slide_lane", (x, y) => {
    if (x === 0) return SLIDE_DARK;
    if (x === 15) return SLIDE_LIGHT;
    return (x + 2 * y) % 6 === 0 ? [0xe8, 0xb6, 0x14] : SLIDE;
  }, 3);
  texture(blockOutput, "slide_underside", (x, y) => (x === 0 || y % 8 === 0 ? UNDERSIDE_DARK : UNDERSIDE), 3);
  texture(blockOutput, "slide_beam", (x, y) => (y % 8 === 0 || x === 8 ? UNDERSIDE_DARK : UNDERSIDE), 3);
  texture(blockOutput, "slide_rail", (x, y) => {
    if (x === 3 || x === 4) return STRIPE;
    if (y % 8 === 0) return SLIDE_DARK;
    if (x === 0 || x === 15) return SLIDE_LIGHT;
    return x >= 6 && x <= 10 ? SLIDE_DARK : SLIDE;
  }, 3);
  texture(blockOutput, "slide_girt", (x, y) => {
    if (x >= 6 && x <= 8 && y >= 4 && y <= 11) return SIGNAL_RED;
    return (x + y) % 3 === 0 ? [0x4a, 0x4e, 0x54] : [0x34, 0x38, 0x3d];
  }, 2);
  texture(blockOutput, "slide_pack", (x, y) => {
    if (y >= 6 && y <= 9 && x >= 6 && x <= 9) return SIGNAL_RED;
    if (y % 5 === 2) return SLIDE;
    return x % 8 === 0 ? UNDERSIDE_DARK : UNDERSIDE;
  }, 3);
  // The panel's picture, one character a pixel: K the bezel, Y the frame, R the red panel, W someone sitting on the
  // slide, S the slide, D the handle's slot.
  const PANEL = [
    "KKKKKKKKKKKKKKKK",
    "KYYYYYYYYYYYYYYK",
    "KYRWWRRRRRRRRRYK",
    "KYRWWRRRRRRRRRYK",
    "KYWRRRWRRRRRDRYK",
    "KYRWWWRRRRRRDRYK",
    "KYSSWWWRRRRRDRYK",
    "KYRSSWWWRRRRDRYK",
    "KYRRSSSWWRRRDRYK",
    "KYRRRSSSRWRRDRYK",
    "KYRRRRSSSRRRDRYK",
    "KYRRRRRSSSRRDRYK",
    "KYRRRRRRSSSRDRYK",
    "KYRRRRRRRRSRRRYK",
    "KYYYYYYYYYYYYYYK",
    "KKKKKKKKKKKKKKKK",
  ];
  const colours = { K: [0x2a, 0x2d, 0x32], Y: [0xf2, 0xc0, 0x1a], R: [0xc4, 0x26, 0x20], W: [0xf6, 0xf6, 0xf2], S: SLIDE, D: [0x5a, 0x10, 0x0c] };
  texture(blockOutput, "slide_control", (x, y) => colours[PANEL[y][x]], 2);
  texture(blockOutput, "slide_control_grip", (x, y) => (y <= 1 ? [0xe8, 0x4a, 0x40] : SIGNAL_RED), 2);
  texture(blockOutput, "slide_control_stem", (x, y) => (x <= 1 ? CHROME : STEEL), 2);
  texture(blockOutput, "slide_control_lamp", (x, y) => (x <= 1 && y <= 1 ? [0x40, 0x46, 0x40] : [0x1e, 0x24, 0x20]), 0);
  texture(blockOutput, "slide_control_lamp_lit", (x, y) => (x <= 1 && y <= 1 ? [0xc8, 0xff, 0xc8] : [0x3c, 0xe0, 0x5a]), 0);
}

// The windscreen renderer tints this plain white sprite into glass and frame.
texture(blockOutput, "windscreen", () => [255, 255, 255], 0);

// The flight deck. Instrument panel: two glass-cockpit screens on a dark panel, an attitude display (blue sky
// over brown ground) and a navigation display (green arc on black), with a row of amber annunciators below.
const PANEL_DARK = [0x26, 0x29, 0x2e];
const PANEL_EDGE = [0x1a, 0x1c, 0x20];
texture(blockOutput, "instrument_panel", (x, y) => {
  if (y >= 12) return y === 13 && x % 3 === 1 ? [0xf0, 0xa8, 0x30] : PANEL_DARK;
  const screen = x >= 1 && x <= 6 ? 0 : x >= 9 && x <= 14 ? 1 : -1;
  if (screen < 0 || y < 2 || y > 10) return y === 1 || y === 11 ? PANEL_EDGE : PANEL_DARK;
  if (screen === 0) {
    if (y === 6 && x >= 2 && x <= 5) return [0xf2, 0xd0, 0x30];
    return y <= 6 ? [0x3a, 0x7c, 0xd0] : [0x8a, 0x5a, 0x2c];
  }
  const r = Math.hypot(x - 11.5, y - 10);
  if (Math.abs(r - 5) < 0.6) return [0x40, 0xe0, 0x60];
  if (x === 11 && y >= 8) return [0xf0, 0xf0, 0xf0];
  return [0x08, 0x0a, 0x0c];
}, 2);
texture(blockOutput, "panel_side", (x, y) => (x === 0 || y === 0 ? PANEL_EDGE : PANEL_DARK), 3);
// The centre console's casing: dark panel, a light lip along the top of its sides (the top of a module's six pixels)
// and a darker kick along the floor, a seam where one module meets the next. The thrust lever: the quadrant's plate,
// dark with the lever's slot down the middle and a light tick either side of it at each of its five notches, a black
// handle and a pale grip.
const CONSOLE_LIP = 10;
texture(blockOutput, "flight_console", (x, y) => {
  if (y === CONSOLE_LIP) return [0x4a, 0x50, 0x58];
  if (y === 15) return [0x16, 0x18, 0x1c];
  return x === 0 ? PANEL_EDGE : PANEL_DARK;
}, 3);
const NOTCHES = [3, 5, 8, 10, 12];
texture(blockOutput, "thrust_lever_base", (x, y) => {
  if (x >= 7 && x <= 8 && y >= 2 && y <= 13) return PANEL_EDGE;
  if (NOTCHES.includes(y) && (x === 5 || x === 10)) return [0x9a, 0xa0, 0xa8];
  return x === 0 || y === 0 ? PANEL_EDGE : PANEL_DARK;
}, 3);
texture(blockOutput, "thrust_lever_handle", () => [0x14, 0x15, 0x17], 2);
texture(blockOutput, "thrust_lever_knob", (x, y) => (y % 4 === 0 ? [0x9a, 0xa0, 0xa8] : [0xd8, 0xdc, 0xe0]), 2);
// Autopilot panel: a dark screen with a green "AP" and a row of mode lights.
const GLYPHS = [".#..###", "#.#.#.#", "###.###", "#.#.#..", "#.#.#.."];
texture(blockOutput, "autopilot", (x, y) => {
  if (x === 0 || y === 0 || x === 15 || y === 15) return PANEL_EDGE;
  const gx = x - 4;
  const gy = y - 4;
  if (gx >= 0 && gx < 7 && gy >= 0 && gy < 5 && GLYPHS[gy][gx] === "#") return [0x40, 0xe0, 0x60];
  if (y === 12 && x % 3 === 2) return x < 8 ? [0x40, 0xe0, 0x60] : [0xf0, 0xa8, 0x30];
  return [0x08, 0x0a, 0x0c];
}, 0);
// Thrust relay, hidden in the nacelle: a dark box with a red signal line.
texture(blockOutput, "thrust_relay", (x, y) => (y === 7 || y === 8 ? [0xb0, 0x20, 0x20] : PANEL_DARK), 3);

// The pilot seat. Its leather: charcoal, padded in channels, two seams running down it with a row of stitches beside
// each, as the model lays the texture over the seat pan and the back. Its trim: a chrome piping, brightest along its
// top. Its frame: dark steel, the edge of each part catching the light. The harness: navy webbing, lighter round its
// edge.
{
  const LEATHER = [0x26, 0x28, 0x2c];
  const SEAM = [0x18, 0x19, 0x1c];
  const STITCH = [0x3e, 0x41, 0x47];
  texture(blockOutput, "pilot_seat_leather", (x, y) => {
    if (x === 5 || x === 10) return SEAM;
    if ((x === 4 || x === 11) && y % 2 === 0) return STITCH;
    return y % 4 === 3 ? [0x22, 0x24, 0x28] : LEATHER;
  }, 3);
  texture(blockOutput, "pilot_seat_trim", (x, y) => (y % 4 === 0 ? CHROME : y % 4 === 3 ? STEEL_DARK : STEEL), 2);
  texture(blockOutput, "pilot_seat_frame", (x, y) => (x === 0 || y === 0 ? [0x52, 0x57, 0x5e] : [0x34, 0x38, 0x3e]), 3);
  texture(blockOutput, "pilot_seat_harness", (x, y) => (x === 0 || x === 15 || y === 0 || y === 15 ? [0x4a, 0x5c, 0x80] : [0x24, 0x33, 0x52]), 2);
}

// The flight crew's locker. Its cabinet: brushed grey panels, streaked up and down, catching the light along their top.
// Its door, the face the model lays from column 1 (the hinge) to 14 and down to row 14: brushed white in a grey edge,
// and on it, so that someone who cannot read yet knows what is inside, the pilot's helmet as its icon shows it,
// outlined dark: white with the livery's blue stripe over the crown, seen from its left side, the gold visor down over
// the eyes under its dark cover, the airline's roundel on the ear cup and the shell coming down to the nape. Under it
// a vent slot; by the free edge, where the model's handle stands, a shadow under the handle. Inside: dark, the same
// streaks fainter, darkest along the floor. The handle: chrome.
{
  const brushed = (base, depth) => (x, y) => {
    const streak = Math.round(depth * (Math.sin(x * 2.3) + 0.6 * Math.sin(x * 5.1 + y * 0.15)));
    return base.map((v) => v + streak);
  };
  const cabinet = brushed([0xb8, 0xbf, 0xc6], 3);
  texture(blockOutput, "crew_locker_side", (x, y) => (y === 0 ? [0xd8, 0xdd, 0xe2] : cabinet(x, y)), 2);
  const inside = brushed([0x2e, 0x31, 0x36], 1);
  texture(blockOutput, "crew_locker_inside", (x, y) => (y === 15 ? [0x1c, 0x1e, 0x22] : inside(x, y)), 2);
  texture(blockOutput, "crew_locker_handle", (x) => (x % 2 === 0 ? CHROME : [0xb4, 0xbb, 0xc2]), 1);
  // The helmet, from column 2 and row 1 of the door.
  const HELMET = [
    "...ooooo...",
    "..obbbbbo..",
    ".owwbbbwwo.",
    "owwwwwwwwwo",
    "owwwwwwdddo",
    "owwbbwwGyGo",
    "owwbbwwGGGo",
    "owwwwwwgggo",
    "osswwwsooo.",
    ".ossssso...",
  ];
  const HELMET_COLOURS = {
    o: [0x3a, 0x40, 0x48], w: [0xf8, 0xf9, 0xfb], s: [0xc4, 0xcb, 0xd5], b: BLUE, d: [0x26, 0x29, 0x2f],
    G: [0xf0, 0xc0, 0x48], y: [0xfc, 0xe8, 0xa8], g: [0xa8, 0x74, 0x24],
  };
  const door = brushed([0xe6, 0xe9, 0xec], 1.5);
  texture(blockOutput, "crew_locker_door", (x, y) => {
    if (x === 1 || x === 14 || y === 0 || y === 14) return [0xc4, 0xc9, 0xcf];
    const pixel = HELMET[y - 1]?.[x - 2];
    if (pixel !== undefined && pixel !== ".") return HELMET_COLOURS[pixel];
    if (y === 12 && x >= 4 && x <= 11) return [0x5c, 0x62, 0x6a];
    if (x === 13 && y === 9) return [0xb0, 0xb6, 0xbd];
    return door(x, y);
  }, 1);
}

// Exterior lights: a coloured lens with a bright core, one per kind, and the soft round glow their renderer draws.
const LENSES = {
  red: [0xe8, 0x20, 0x18],
  green: [0x18, 0xd8, 0x40],
  white: [0xf4, 0xf4, 0xf0],
  strobe: [0xe0, 0xe8, 0xff],
  beacon: [0xff, 0x30, 0x10],
};
for (const [kind, colour] of Object.entries(LENSES)) {
  texture(blockOutput, `nav_light_${kind}`, (x, y) => {
    const r = Math.hypot(x - 7.5, y - 7.5);
    if (r < 2.5) return [255, 255, 255];
    const fade = Math.min(1, r / 9);
    return colour.map((c) => clamp(Math.round(c * (1 - 0.35 * fade))));
  }, 2);
}
const miscOutput = resolve(ROOT, "src/main/resources/assets/aerolarge/textures/misc");
mkdirSync(miscOutput, { recursive: true });
{
  // 32x32: the glow is a large flare, and a 16x16 one shows its pixels.
  const pixels = [];
  for (let y = 0; y < 32; y++) {
    const row = [];
    for (let x = 0; x < 32; x++) {
      const r = Math.hypot(x - 15.5, y - 15.5) / 16;
      const a = Math.max(0, 1 - r);
      const v = Math.round(255 * a * a);
      row.push([v, v, v, 255]);
    }
    pixels.push(row);
  }
  writeFileSync(resolve(miscOutput, "nav_glow.png"), encodePng(pixels));
}
// The glow deep in a running engine's intake, its renderer's red added over the dark: full round the root of the
// spinner, fading away to nothing at the duct's wall. The texture spans the mouth; the spinner covers its middle.
{
  const SPINNER = 0.3;
  const pixels = [];
  for (let y = 0; y < 32; y++) {
    const row = [];
    for (let x = 0; x < 32; x++) {
      const r = Math.hypot(x - 15.5, y - 15.5) / 16;
      const fade = Math.max(0, Math.min(1, (1 - r) / (1 - SPINNER)));
      const v = Math.round(255 * fade ** 1.5);
      row.push([v, v, v, 255]);
    }
    pixels.push(row);
  }
  writeFileSync(resolve(miscOutput, "intake_glow.png"), encodePng(pixels));
}

// A contrail puff: a soft white round blob, see-through toward its edge.
{
  const particleOutput = resolve(ROOT, "src/main/resources/assets/aerolarge/textures/particle");
  mkdirSync(particleOutput, { recursive: true });
  reseed("contrail");
  const pixels = [];
  for (let y = 0; y < 16; y++) {
    const row = [];
    for (let x = 0; x < 16; x++) {
      const r = Math.hypot(x - 7.5, y - 7.5) / 8;
      const a = Math.max(0, 1 - r * r) * (0.85 + 0.15 * random());
      row.push([250, 250, 252, Math.round(255 * a)]);
    }
    pixels.push(row);
  }
  writeFileSync(resolve(particleOutput, "contrail.png"), encodePng(pixels));
}

// The blueprints: a rolled-out sheet of blue paper with the airliner drawn from above in white; the furnished one
// carries a gold star for the fittings.
function blueprint(name, star) {
  texture(itemOutput, name, (x, y) => {
    if (x < 1 || x > 14 || y < 2 || y > 13) return CLEAR;
    if (star && Math.abs(x - 12.5) + Math.abs(y - 4.5) <= 1.5) return GOLD;
    // Seen from above, nose up: swept wings and tailplane drift back as they reach out.
    const out = Math.abs(x - 7.5);
    const fuselage = out <= 0.5 && y >= 3 && y <= 12;
    const wings = out <= 6.5 && y >= 5.5 + out * 0.35 && y <= 7 + out * 0.35;
    const tail = out <= 2.5 && y >= 10.5 + out * 0.35 && y <= 11.5 + out * 0.35;
    if (fuselage || wings || tail) return INK;
    if (x === 1 || x === 14 || y === 2 || y === 13) return PAPER_LINE;
    if (x % 4 === 0 || y % 4 === 0) return PAPER_LINE;
    return PAPER;
  }, 2);
}
blueprint("airliner_blueprint", false);
blueprint("furnished_airliner_blueprint", true);

// Skydiving. The jump light: a lens dark, red or green, brighter in the middle. The jump switch: a dark panel with a
// red and a green lamp, the one in use lit. The rack: a frame holding packed parachutes. The landing target: red and
// white rings. The parachute icon, and the canopy's texture: red and white halves for the panels, olive below for
// the pack and the cords.
{
  const lens = { off: [0x3a, 0x3d, 0x42], red: [0xe0, 0x28, 0x20], green: [0x30, 0xe0, 0x50] };
  for (const [signal, colour] of Object.entries(lens)) {
    texture(blockOutput, `jump_light_${signal}`, (x, y) => {
      if (x === 0 || y === 0 || x === 15 || y === 15) return FRAME_DARK;
      const glow = signal === "off" ? 0 : Math.max(0, 1 - Math.hypot(x - 7.5, y - 7.5) / 8);
      return colour.map((c) => clamp(Math.round(c + 70 * glow)));
    });
    texture(blockOutput, `jump_switch_${signal}`, (x, y) => {
      const red = Math.hypot(x - 5, y - 6) < 2.5;
      const green = Math.hypot(x - 11, y - 6) < 2.5;
      if (red) return signal === "red" ? [0xff, 0x40, 0x30] : [0x50, 0x18, 0x14];
      if (green) return signal === "green" ? [0x50, 0xff, 0x70] : [0x14, 0x40, 0x1c];
      if (y >= 10 && y <= 12 && x >= 4 && x <= 11) return STEEL;
      return [0x22, 0x25, 0x2a];
    });
  }
  const OLIVE = [0x5a, 0x63, 0x3c];
  const OLIVE_DARK = [0x44, 0x4b, 0x2d];
  texture(blockOutput, "parachute_rack_front", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return STEEL_DARK;
    const slot = Math.floor((x - 1) / 5);
    if ((x - 1) % 5 === 4) return STEEL_DARK;
    return (y + slot) % 4 === 0 ? OLIVE_DARK : OLIVE;
  });
  texture(blockOutput, "parachute_rack_side", (x, y) => (x === 0 || x === 15 || y === 0 || y === 15 ? STEEL_DARK : STEEL));
  texture(blockOutput, "landing_target_top", (x, y) => {
    const ring = Math.floor(Math.hypot(x - 7.5, y - 7.5) / 2);
    return ring % 2 === 0 ? [0xd0, 0x20, 0x20] : [0xf0, 0xf0, 0xf0];
  });
  texture(blockOutput, "landing_target_side", (x, y) => (Math.floor(x / 4) % 2 === 0 ? [0xd0, 0x20, 0x20] : [0xf0, 0xf0, 0xf0]));
  texture(itemOutput, "parachute", (x, y) => {
    const dome = y <= 7 && Math.hypot(x - 7.5, (y - 7.5) * 1.6) < 7.5;
    if (dome) return Math.floor((x + 1) / 3) % 2 === 0 ? [0xe0, 0x30, 0x28] : [0xf2, 0xf2, 0xf2];
    const cord = y > 7 && y < 13 && (Math.abs(x - 7.5 - (x < 8 ? -(13 - y) * 0.8 : (13 - y) * 0.8)) < 0.6);
    if (cord) return [0x30, 0x30, 0x30];
    if (y >= 12 && x >= 6 && x <= 9) return OLIVE;
    return CLEAR;
  }, 0);
  const entityOutput = resolve(ROOT, "src/main/resources/assets/aerolarge/textures/entity");
  mkdirSync(entityOutput, { recursive: true });
  texture(entityOutput, "parachute", (x, y) => {
    if (x >= 8 && y >= 12) return y % 2 === 0 ? OLIVE_DARK : OLIVE;
    return x < 8 ? [0xd8, 0x2c, 0x24] : [0xf0, 0xf0, 0xee];
  }, 3);
}

// Speaking to the cabin and the tower. The handset: a dark cradle, the receiver black with a grey grip line. The
// tower radio: grey metal, a speaker grille on the left of its face, a lit green frequency display and two dials on
// the right; its sides have vents.
{
  texture(blockOutput, "handset_cradle", (x, y) => (x === 0 || y === 0 || x === 15 || y === 15 ? [0x1a, 0x1c, 0x20] : [0x2c, 0x30, 0x36]));
  texture(blockOutput, "handset_receiver", (x, y) => (y === 7 || y === 8 ? [0x4a, 0x4f, 0x56] : [0x14, 0x15, 0x17]));
  const PANEL_GREY = [0x6b, 0x71, 0x78];
  texture(blockOutput, "tower_radio_front", (x, y) => {
    if (x === 0 || y === 0 || x === 15 || y === 15) return STEEL_DARK;
    if (x >= 2 && x <= 7 && y >= 3 && y <= 12) return (x + y) % 2 === 0 ? [0x24, 0x26, 0x29] : [0x3a, 0x3d, 0x41];
    if (x >= 9 && x <= 13 && y >= 3 && y <= 5) return y === 4 && x % 2 === 0 ? [0xb0, 0xff, 0xa0] : [0x30, 0xc0, 0x40];
    if (Math.hypot(x - 10, y - 9.5) < 1.6 || Math.hypot(x - 13, y - 9.5) < 1.6) return [0x1d, 0x1e, 0x20];
    if (x >= 9 && x <= 13 && y === 12) return GOLD;
    return PANEL_GREY;
  });
  texture(blockOutput, "tower_radio_side", (x, y) => {
    if (x === 0 || y === 0 || x === 15 || y === 15) return STEEL_DARK;
    if (y % 3 === 1 && x >= 3 && x <= 12) return [0x3a, 0x3d, 0x41];
    return PANEL_GREY;
  });
}

// Buckling up. The seatbelt sign: a dark panel with a seated figure and its belt beside a crossed-out cigarette,
// glowing amber on white when lit. The seatbelt switch: a dark panel with the same figure on a lamp, amber when on.
{
  const seated = (x, y) =>
    Math.hypot(x - 4.5, y - 4.5) < 1.3 // head
    || (x >= 3 && x <= 5 && y >= 6 && y <= 9) // body
    || (y === 9 && x >= 5 && x <= 7) // thigh
    || (x === 7 && y >= 10 && y <= 12) // shin
    || (x === 2 && y >= 6 && y <= 11); // seat back
  const belt = (x, y) => y === 8 && x >= 2 && x <= 6;
  const cigarette = (x, y) => y === 8 && x >= 9 && x <= 13;
  const crossed = (x, y) => Math.abs(Math.hypot(x - 11, y - 8) - 3.2) < 0.6 || Math.abs((x - 11) - (8 - y)) < 0.6 && Math.hypot(x - 11, y - 8) < 3.2;
  for (const lit of [false, true]) {
    const back = lit ? [0xf4, 0xf0, 0xe0] : [0x2c, 0x30, 0x36];
    const ink = lit ? [0x20, 0x20, 0x24] : [0x48, 0x4d, 0x55];
    const amber = lit ? [0xf0, 0xa0, 0x20] : [0x5a, 0x48, 0x2a];
    const red = lit ? [0xd8, 0x30, 0x28] : [0x50, 0x2c, 0x2a];
    texture(blockOutput, `seatbelt_sign${lit ? "_on" : ""}`, (x, y) => {
      if (x === 0 || y === 0 || x === 15 || y === 15) return FRAME_DARK;
      if (belt(x, y)) return amber;
      if (seated(x, y)) return ink;
      if (crossed(x, y)) return red;
      if (cigarette(x, y)) return ink;
      return back;
    }, lit ? 1 : 3);
    texture(blockOutput, `seatbelt_switch${lit ? "_on" : ""}`, (x, y) => {
      const lamp = Math.hypot(x - 7.5, y - 6.5) < 4.5;
      if (lamp && (seated(x - 3, y - 2) || belt(x - 3, y - 2))) return lit ? [0x30, 0x24, 0x10] : [0x14, 0x15, 0x17];
      if (lamp) return lit ? [0xff, 0xb0, 0x30] : [0x4a, 0x3c, 0x24];
      if (y >= 12 && y <= 13 && x >= 5 && x <= 10) return STEEL;
      return [0x22, 0x25, 0x2a];
    });
  }
}

// The cabin's lining and lights. The LED strip: cool white, brightest along its middle. The light cable: black. The
// pendant's shade: brushed dark grey. The overhead locker: off-white plastic, its door with a rounded edge and a
// latch; open, the dark inside with a blue suitcase; underneath, the passenger service panel with two reading lights
// and an air vent.
{
  texture(blockOutput, "light_strip", (x, y) => {
    const glow = 1 - Math.abs(y - 7.5) / 8;
    return [clamp(Math.round(215 + 40 * glow)), clamp(Math.round(228 + 27 * glow)), 255];
  }, 0);
  texture(blockOutput, "light_strip_night", (x, y) => {
    const glow = 1 - Math.abs(y - 7.5) / 8;
    return [clamp(Math.round(30 + 40 * glow)), clamp(Math.round(50 + 50 * glow)), clamp(Math.round(150 + 90 * glow))];
  }, 0);
  texture(blockOutput, "light_cable", () => [0x18, 0x19, 0x1b], 2);
  texture(blockOutput, "pendant_shade", (x, y) => (y % 4 === 0 ? [0x3c, 0x40, 0x46] : [0x4a, 0x4f, 0x56]), 3);
  const PLASTIC = [0xe6, 0xe4, 0xde];
  const PLASTIC_EDGE = [0xc8, 0xc6, 0xbf];
  texture(blockOutput, "overhead_bin", (x, y) => {
    if (y === 0 || y === 15 || x === 0 || x === 15) return PLASTIC_EDGE;
    if (y === 2 && x >= 6 && x <= 9) return [0x50, 0x54, 0x5a];
    return y > 11 ? [0xdc, 0xda, 0xd3] : PLASTIC;
  }, 2);
  texture(blockOutput, "overhead_bin_open", (x, y) => {
    if (y === 0 || y === 15 || x === 0 || x === 15) return PLASTIC_EDGE;
    const suitcase = x >= 3 && x <= 11 && y >= 6 && y <= 13;
    if (suitcase) return y === 6 || x === 3 || x === 11 ? [0x14, 0x2c, 0x60] : [0x1f, 0x45, 0x94];
    if (x >= 6 && x <= 8 && y === 5) return [0x20, 0x20, 0x22];
    return [0x26, 0x28, 0x2c];
  }, 2);
  texture(blockOutput, "overhead_bin_side", (x, y) => (x === 0 || y === 0 ? PLASTIC_EDGE : PLASTIC), 2);
  texture(blockOutput, "overhead_bin_bottom", (x, y) => {
    if (Math.hypot(x - 4, y - 4) < 1.6 || Math.hypot(x - 11, y - 4) < 1.6) return [0xf8, 0xe0, 0x90];
    if (Math.hypot(x - 7.5, y - 10.5) < 2.2) return Math.hypot(x - 7.5, y - 10.5) < 1 ? [0x30, 0x32, 0x36] : [0x9a, 0xa0, 0xa6];
    if (y === 7) return [0xb0, 0xb4, 0xba];
    return [0xc8, 0xcc, 0xd0];
  }, 2);
}

// The hull's lining: what the skin shows on the inside, wherever it looks into the cabin, the hold or the flight deck,
// in the overhead lockers' off-white plastic, so the walls, the lockers and the doors' padded lining read as one
// moulded cabin. The sidewall: smooth panels with a faint joint down one edge and the rounded edge beside it catching
// the light. The ceiling over the crown: a shade lighter, a groove along two edges like the ceiling panels under a
// deck. Underfoot, where the skin is the floor (the hold's belly, a ledge along the edge of a deck): grey composite
// floor panels with a fine non-slip grain. The dado along the foot of a wall: a warm grey panel under a pale capping
// rail, an air-return grille low down in it. Round a window the lining turns in to the window's frame through a deep
// rounded reveal, a pale lip where it leaves the wall shading down into the recess: down one side of the window, or
// both sides of a pillar between two; over it (the head, facing down, darker); under it (the sill, facing up, lighter),
// over the dado's capping rail where the dado runs under the window.
{
  const LINING = [0xe6, 0xe4, 0xde];
  const JOINT = [0xda, 0xd7, 0xd0];
  const EDGE = [0xee, 0xec, 0xe7];
  // A reveal's pixels from its lip (0) to the window's frame (4).
  const REVEAL = [[0xf3, 0xf1, 0xec], [0xdd, 0xda, 0xd2], [0xd2, 0xcf, 0xc6], [0xc6, 0xc2, 0xb9], [0xb8, 0xb4, 0xaa]];
  const SILL = [[0xf4, 0xf2, 0xed], [0xe7, 0xe4, 0xdd], [0xde, 0xdb, 0xd3], [0xd5, 0xd2, 0xc9], [0xcb, 0xc7, 0xbe]];
  const DEEP = REVEAL.length - 1;
  const wall = (x) => (x === 0 ? JOINT : x === 1 ? EDGE : LINING);
  const dado = (x, y) => {
    if (y <= 4) return wall(x);
    if (y === 5) return EDGE;
    if (y === 6) return [0xa6, 0xa2, 0x99];
    if (y >= 12 && y <= 13 && x >= 2 && x <= 13) return x % 2 === 0 ? [0x5e, 0x5c, 0x57] : [0xa0, 0x9c, 0x93];
    return x === 0 ? [0xaa, 0xa6, 0x9d] : [0xbd, 0xb9, 0xb0];
  };
  texture(blockOutput, "hull_lining", (x) => wall(x), 2);
  texture(blockOutput, "hull_lining_ceiling", (x, y) => {
    if (x === 0 || y === 0) return [0xcc, 0xcb, 0xc6];
    return x === 1 || y === 1 ? [0xe2, 0xe1, 0xdc] : [0xf0, 0xef, 0xeb];
  }, 2);
  texture(blockOutput, "hull_lining_floor", (x, y) => {
    if (x === 0 || y === 0) return [0x5c, 0x60, 0x66];
    return x % 2 === 1 && (x + y) % 4 === 1 ? [0x8a, 0x8e, 0x94] : [0x78, 0x7c, 0x82];
  }, 3);
  texture(blockOutput, "hull_lining_dado", dado, 2);
  texture(blockOutput, "hull_lining_reveal", (x) => (x > 15 - REVEAL.length ? REVEAL[x - (16 - REVEAL.length)] : wall(x)), 2);
  texture(blockOutput, "hull_lining_reveal_pair", (x) => {
    if (x < REVEAL.length) return REVEAL[DEEP - x];
    return x > 15 - REVEAL.length ? REVEAL[x - (16 - REVEAL.length)] : LINING;
  }, 2);
  texture(blockOutput, "hull_lining_reveal_head", (x, y) => (y > 15 - REVEAL.length ? REVEAL[y - (16 - REVEAL.length)] : wall(x)), 2);
  texture(blockOutput, "hull_lining_reveal_sill", (x, y) => (y < SILL.length ? SILL[DEEP - y] : wall(x)), 2);
  texture(blockOutput, "hull_lining_dado_sill", (x, y) => (y < SILL.length ? SILL[DEEP - y] : dado(x, y)), 2);
}

// The signal flare: a red paper tube with a white band, its tip burning yellow-white; and its icon, the tube with a
// wisp of smoke above.
{
  const RED_PAPER = [0xc8, 0x20, 0x1c];
  texture(blockOutput, "signal_flare", (x, y) => (y >= 6 && y <= 7 ? [0xf0, 0xf0, 0xf0] : RED_PAPER), 4);
  texture(blockOutput, "signal_flare_tip", (x, y) => (Math.hypot(x - 7.5, y - 7.5) < 4 ? [0xff, 0xf4, 0xb0] : [0xff, 0xa0, 0x30]), 2);
  texture(itemOutput, "signal_flare", (x, y) => {
    if (x >= 7 && x <= 8 && y >= 7 && y <= 15) return y === 10 ? [0xf0, 0xf0, 0xf0] : RED_PAPER;
    if (x >= 7 && x <= 8 && y === 6) return [0xff, 0xd8, 0x60];
    if (Math.hypot(x - 7.5 - Math.sin(y) * 1.2, y - 3) < 2.2 && y < 6) return [0xe8, 0x50, 0x40, 0xb0];
    return CLEAR;
  }, 0);
}

// The drop bay: two hatch doors meeting down the middle in a yellow-and-black hazard border, or open on a dark
// shaft with rails down its sides. The drop release: a red guarded switch, dark when safe, lit with ARMED stripes.
{
  const HAZARD = (x, y) => ((x + y) % 4 < 2 ? [0xf0, 0xc0, 0x20] : [0x1a, 0x1a, 0x1a]);
  texture(blockOutput, "drop_bay", (x, y) => {
    if (x <= 1 || x >= 14 || y <= 1 || y >= 14) return HAZARD(x, y);
    if (x === 7 || x === 8) return [0x40, 0x44, 0x4a];
    return (y % 4 === 0) ? [0x7a, 0x80, 0x88] : [0x8e, 0x94, 0x9b];
  });
  texture(blockOutput, "drop_bay_open", (x, y) => {
    if (x <= 1 || x >= 14 || y <= 1 || y >= 14) return HAZARD(x, y);
    if (x === 3 || x === 12) return [0x5c, 0x62, 0x69];
    return [0x0c, 0x0d, 0x0f];
  }, 1);
  // The cover over the release's button: red, striped dark across it.
  texture(blockOutput, "drop_release_cover", (x, y) => ((x + y) % 6 < 2 ? [0x5a, 0x10, 0x0e] : [0xc0, 0x24, 0x1c]), 2);
  for (const armed of [false, true]) {
    texture(blockOutput, `drop_release_${armed ? "armed" : "safe"}`, (x, y) => {
      if (x === 0 || y === 0 || x === 15 || y === 15) return armed ? HAZARD(x, y) : STEEL_DARK;
      const guard = x >= 4 && x <= 11 && y >= 4 && y <= 11 && (x === 4 || x === 11 || y === 4);
      const button = Math.hypot(x - 7.5, y - 8) < 2.6;
      if (button) return armed ? [0xff, 0x40, 0x30] : [0x6a, 0x1a, 0x16];
      if (guard) return [0x9a, 0xa0, 0xa6];
      return [0x16, 0x18, 0x1c];
    }, 1);
  }
}

// Cabin service. The galley, its trolley bay (see the galley units below): brushed steel; over the bay its control
// panel, a dark strip of buttons, green and white, and an amber readout of the trolleys stocked. The trolley:
// aluminium with a red stripe and drawer lines, its ends a grid of drawers with handles, its top a grey tray. The
// meals on their trays, a bag of pretzels and a paper cup of coffee.
{
  const ALU = [0xc4, 0xc9, 0xcf];
  const ALU_DARK = [0x8e, 0x94, 0x9b];
  texture(blockOutput, "galley_side", (x, y) => (x === 0 || x === 15 || y === 0 || y === 15 ? ALU_DARK : (x + y * 3) % 7 === 0 ? [0xb4, 0xb9, 0xbf] : ALU));
  texture(blockOutput, "galley_front", (x, y) => {
    if (y === 0 || y === 3) return ALU_DARK;
    if (y > 3) return ALU;
    if (x === 3 || x === 5) return y === 1 ? (x === 3 ? [0x46, 0xe0, 0x70] : [0xf0, 0xf0, 0xea]) : [0x26, 0x29, 0x2e];
    if (x >= 9 && x <= 12) return (x + y) % 2 === 0 ? [0xf2, 0xb8, 0x30] : [0x60, 0x44, 0x10];
    return [0x26, 0x29, 0x2e];
  }, 2);
  texture(itemOutput, "trolley_side", (x, y) => {
    if (y >= 3 && y <= 4) return [0xc8, 0x20, 0x24];
    if (x === 0 || x === 15 || y === 0 || y === 15) return ALU_DARK;
    return y % 4 === 2 ? ALU_DARK : ALU;
  }, 2);
  texture(itemOutput, "trolley_end", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15 || y % 4 === 0) return ALU_DARK;
    if (y % 4 === 2 && x >= 6 && x <= 9) return [0x30, 0x32, 0x36];
    return ALU;
  }, 2);
  texture(itemOutput, "trolley_top", (x, y) => (x <= 1 || x >= 14 || y <= 1 || y >= 14 ? ALU_DARK : [0x9a, 0x9f, 0xa6]), 2);
  const tray = (food) => (x, y) => {
    if (y < 4 || y > 12 || x < 1 || x > 14) return CLEAR;
    if (y === 4 || y === 12 || x === 1 || x === 14) return [0x50, 0x54, 0x5a];
    return food(x, y) ?? [0xe8, 0xe8, 0xe2];
  };
  texture(itemOutput, "chicken_meal", tray((x, y) => (Math.hypot(x - 5.5, y - 8) < 2.6 ? [0xb8, 0x74, 0x34]
    : x >= 9 && x <= 12 && y >= 6 && y <= 10 ? ((x + y) % 2 ? [0x40, 0x9c, 0x30] : [0xe0, 0x90, 0x30]) : null)), 0);
  texture(itemOutput, "pasta_meal", tray((x, y) => (Math.hypot(x - 7.5, y - 8) < 3.4 ? ((x * 3 + y) % 4 === 0 ? [0xc8, 0x30, 0x20] : [0xf0, 0xd0, 0x70]) : null)), 0);
  texture(itemOutput, "pretzels", (x, y) => {
    if (x < 3 || x > 12 || y < 2 || y > 14) return CLEAR;
    if (y <= 3) return [0x90, 0x94, 0x9a];
    const knot = Math.abs(Math.hypot(x - 6, y - 8) - 2) < 0.8 || Math.abs(Math.hypot(x - 9.5, y - 8) - 2) < 0.8;
    return knot ? [0x9c, 0x5a, 0x22] : [0x2a, 0x5c, 0xb0];
  }, 0);
  texture(itemOutput, "airline_coffee", (x, y) => {
    const half = 3 + (y - 3) * 0.2;
    if (y < 3 || y > 14 || Math.abs(x - 7.5) > half) return CLEAR;
    if (y === 3) return [0x4a, 0x2c, 0x18];
    if (y >= 7 && y <= 9) return [0x1c, 0x3d, 0x8c];
    return [0xf4, 0xf4, 0xf0];
  }, 0);
}

// The care package's crate: oak boards in a darker frame; on the sides an orange parachute stencil, on top a red
// cross on white, on the front a door of slats.
{
  const OAK = [0xa8, 0x82, 0x4e];
  const OAK_DARK = [0x7a, 0x5a, 0x32];
  const boards = (x, y) => (x === 0 || x === 15 || y === 0 || y === 15 ? OAK_DARK : y % 5 === 0 ? [0x96, 0x72, 0x42] : OAK);
  texture(blockOutput, "cargo_crate_side", (x, y) => {
    const canopy = y >= 3 && y <= 6 && Math.hypot(x - 7.5, (y - 6.5) * 1.6) < 5.5;
    const cords = y > 6 && y < 10 && (Math.abs(x - 7.5 - (x < 8 ? -(10 - y) : 10 - y)) < 0.6);
    const box = x >= 6 && x <= 9 && y >= 10 && y <= 12;
    return canopy || box ? [0xe0, 0x70, 0x20] : cords ? [0x50, 0x3a, 0x22] : boards(x, y);
  });
  texture(blockOutput, "cargo_crate_top", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return OAK_DARK;
    if (x >= 3 && x <= 12 && y >= 3 && y <= 12) {
      return (x >= 6 && x <= 9) || (y >= 6 && y <= 9) ? [0xd0, 0x20, 0x20] : [0xf0, 0xf0, 0xea];
    }
    return OAK;
  });
  texture(blockOutput, "cargo_crate_front", (x, y) => (x === 0 || x === 15 || y === 0 || y === 15 || x === 1 || x === 14 ? OAK_DARK : x % 3 === 2 ? [0x8a, 0x66, 0x3a] : OAK));
}

// Basketball. The ball: orange with black seams, a curved pair either side of a straight cross, shaded toward the
// lower right. The hoop's rim: bright orange. The net: a white diamond mesh, see-through between the cords. The
// scoreboard: a black face in a steel frame with faint unlit lamps. The ball rack: steel bars holding orange balls.
{
  const ORANGE = [0xe2, 0x6a, 0x1c];
  const ORANGE_DARK = [0xb0, 0x4c, 0x10];
  texture(itemOutput, "basketball", (x, y) => {
    const r = Math.hypot(x - 7.5, y - 7.5);
    if (r > 7) return CLEAR;
    const seam = Math.abs(x - 7.5) < 0.6 || Math.abs(y - 7.5) < 0.6 || Math.abs(Math.hypot(x + 3, y - 7.5) - 7.6) < 0.55
      || Math.abs(Math.hypot(x - 18, y - 7.5) - 7.6) < 0.55;
    if (seam) return [0x1a, 0x12, 0x0c];
    return x + y > 18 ? ORANGE_DARK : ORANGE;
  }, 3);
  texture(blockOutput, "hoop_rim", () => [0xf0, 0x5a, 0x10], 4);
  texture(blockOutput, "hoop_net", (x, y) => ((x + y) % 4 === 0 || (x - y + 16) % 4 === 0 ? [0xf2, 0xf2, 0xf2] : CLEAR), 0);
  texture(blockOutput, "scoreboard_front", (x, y) => {
    if (x === 0 || y === 0 || x === 15 || y === 15) return STEEL_DARK;
    return (x % 3 === 1 && y % 3 === 1) ? [0x26, 0x1c, 0x10] : [0x0e, 0x0f, 0x11];
  }, 1);
  texture(blockOutput, "scoreboard_side", (x, y) => (x === 0 || y === 0 || x === 15 || y === 15 ? STEEL_DARK : [0x1c, 0x1e, 0x22]));
  texture(blockOutput, "ball_rack_front", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15 || y === 7 || y === 8) return STEEL;
    const ball = Math.hypot(((x - 1) % 7) - 3, ((y - 1) % 8) - 3) < 3.2 && (y < 7 || y > 8);
    return ball ? ((x + y) % 5 === 0 ? [0x1a, 0x12, 0x0c] : ORANGE) : [0x2a, 0x2d, 0x31];
  });
  texture(blockOutput, "ball_rack_side", (x, y) => (x === 0 || x === 15 || y === 0 || y === 15 ? STEEL : [0x2a, 0x2d, 0x31]));
}

// The cabin lights' dimmer: a dark face showing its setting, a bulb with rays when bright, a dull amber bulb when
// dimmed, a blue crescent moon at night.
{
  const bulb = (x, y) => Math.hypot(x - 7.5, y - 6.5) < 3.2 || (x >= 6 && x <= 9 && y >= 9 && y <= 11);
  const rays = (x, y) => {
    const r = Math.hypot(x - 7.5, y - 6.5);
    return r > 4.4 && r < 6.2 && (Math.abs(x - 7.5) < 0.6 || Math.abs(y - 6.5) < 0.6 || Math.abs(Math.abs(x - 7.5) - Math.abs(y - 6.5)) < 0.7);
  };
  const face = (paint) => (x, y) => (x === 0 || y === 0 || x === 15 || y === 15 ? STEEL_DARK : paint(x, y) ?? [0x16, 0x18, 0x1c]);
  texture(blockOutput, "cabin_lights_bright", face((x, y) => (bulb(x, y) ? [0xff, 0xe8, 0x80] : rays(x, y) ? [0xff, 0xd0, 0x40] : null)), 1);
  texture(blockOutput, "cabin_lights_dim", face((x, y) => (bulb(x, y) ? [0x9a, 0x70, 0x30] : null)), 1);
  texture(blockOutput, "cabin_lights_night", face((x, y) =>
    (Math.hypot(x - 7.5, y - 7.5) < 4.5 && Math.hypot(x - 9.5, y - 6) > 3.6 ? [0x70, 0x9c, 0xff] : null)), 1);
}

// The window shades' control: a dark face with a little window on it, blue sky when the shades are up, the pale
// shade across it when they are down.
{
  for (const shaded of [false, true]) {
    texture(blockOutput, `window_shades_${shaded ? "closed" : "open"}`, (x, y) => {
      if (x === 0 || y === 0 || x === 15 || y === 15) return STEEL_DARK;
      const frame = x >= 4 && x <= 11 && y >= 3 && y <= 12;
      const pane = x >= 5 && x <= 10 && y >= 4 && y <= 11;
      if (pane) return shaded ? [0xe2, 0xde, 0xd4] : y < 8 ? [0x70, 0xb4, 0xf0] : [0x9c, 0xd0, 0xff];
      if (frame) return [0xd8, 0xdc, 0xe0];
      return [0x16, 0x18, 0x1c];
    }, 1);
  }
}

// Room dividers. The curtain's cloth: a fine weave in grey, which the client tints the dye's colour, so one texture
// serves all sixteen. Four pleats run down it, a column of four pixels each: the first and third lit along their
// crests, the second and fourth shaded in their folds (the model shows a pleat standing forward with the one and a
// pleat set back with the other). Its hem: the same cloth with a darker hem stitched along its foot, the bottom three
// rows the lowest piece shows. The rail and its rings: brushed steel. The band tying back an open curtain: twisted
// brass cord. The partition: a smooth cream bulkhead panel, its seams faint along two edges and no rivets, a match
// for the airframe's white panels without being one.
{
  const CREST = [0.86, 0.97, 1.0, 0.92];
  const FOLD = [0.76, 0.7, 0.66, 0.72];
  const cloth = (x, y) => {
    const pleat = Math.floor(x / 4) % 2 === 0 ? CREST : FOLD;
    const weave = (x + y) % 2 === 0 ? 5 : -5;
    const thread = y % 4 === 0 ? -6 : 0;
    const v = clamp(Math.round(242 * pleat[x % 4]) + weave + thread);
    return [v, v, v];
  };
  texture(blockOutput, "cabin_curtain", cloth, 2);
  texture(blockOutput, "cabin_curtain_hem", (x, y) => {
    if (y < 12) return cloth(x, y);
    const darker = y === 12 && x % 2 === 0 ? 0.42 : 0.6;
    return cloth(x, y).map((v) => Math.round(v * darker));
  }, 2);
  texture(blockOutput, "cabin_curtain_rail", (x, y) => ((x * 7 + y * 3) % 5 === 0 ? [0xd2, 0xd7, 0xdc] : [0xa4, 0xaa, 0xb1]), 3);
  texture(blockOutput, "cabin_curtain_tie", (x, y) => ((x + y) % 4 < 2 ? [0xd8, 0xac, 0x40] : [0xa4, 0x7c, 0x22]), 2);
  texture(blockOutput, "cabin_partition", (x, y) => (x === 0 || y === 0 ? [0xdb, 0xd3, 0xc0] : [0xee, 0xe7, 0xd6]), 2);
}
// The cabin arch round the bulkheads' archways: smooth white panelling, a shade whiter than the partition's cream and
// without its seams, so the arch reads as one moulded piece set into the wall; its moulding's bead whiter still, and
// the groove behind the bead a soft grey, so the moulding shows head on too.
{
  texture(blockOutput, "cabin_arch", () => [0xf7, 0xf5, 0xef], 1);
  texture(blockOutput, "cabin_arch_trim", () => [0xfd, 0xfc, 0xf9], 1);
  texture(blockOutput, "cabin_arch_groove", () => [0xe0, 0xdd, 0xd6], 1);
}

// The exit sign over the doors: on green, a white man running into a white doorway, dark inside, as every airliner's
// exits are marked. The sign shows the middle eight rows, between the first and last columns.
{
  const SIGN = [
    "......WW..WWWW",
    "......WW..WDDW",
    "..WWWWW...WDDW",
    ".W..WW.WW.WDDW",
    "....WW....WDDW",
    "...W..WW..WDDW",
    ".WW....W..WDDW",
    ".......WW.WDDW",
  ];
  const colours = { W: [0xf4, 0xfa, 0xf4], D: [0x0c, 0x5c, 0x2c] };
  texture(blockOutput, "exit_sign", (x, y) => colours[SIGN[y - 4]?.[x - 1]] ?? [0x16, 0x9c, 0x4a], 2);
}

// The bulkhead signs over the archways (see BulkheadSignBlock): each picture printed on a warm white lightbox, the
// fourteen pixels inside the bezel, in the colour of the room it points to, as the curtains are: business class's
// navy seat, reclined, on its legs; the pool's drop of water over the waves, a glint in it; the gym hall's basketball
// with its seams; the lounge's cup on its saucer, steaming; and for the lavatories a man and a woman, in blue and red
// as on every lavatory door. The bezel round it: brushed steel, streaked across.
{
  const LIGHTBOX = [0xf6, 0xf4, 0xec];
  const NAVY = [0x1f, 0x2f, 0x6e];
  const pictures = {
    seat: {
      colours: { N: NAVY },
      rows: [
        "..............",
        "..........##..",
        ".........####.",
        ".........###..",
        "........###...",
        "........###...",
        ".......###....",
        ".......###....",
        ".##########...",
        ".##########...",
        "..##.....##...",
        "..##.....##...",
        ".####...####..",
        "..............",
      ].map((row) => row.replaceAll("#", "N")),
    },
    drop: {
      colours: { C: [0x12, 0x8f, 0xb8], H: [0x9c, 0xdc, 0xf2] },
      rows: [
        "......CC......",
        "......CC......",
        ".....CCCC.....",
        ".....CCCC.....",
        "....CCCCCC....",
        "...CCCCCCCC...",
        "...CHCCCCCC...",
        "...CHCCCCCC...",
        "...CCCCCCCC...",
        "....CCCCCC....",
        ".....CCCC.....",
        "..............",
        ".CC..CC..CC..C",
        "C..CC..CC..CC.",
      ],
    },
    cup: {
      colours: { R: [0xc4, 0x2a, 0x2a], S: [0xa4, 0xa6, 0xaa] },
      rows: [
        "..............",
        "....S...S.....",
        "...S...S......",
        "....S...S.....",
        "...S...S......",
        "..............",
        ".RRRRRRRRR....",
        ".RRRRRRRRR.RR.",
        ".RRRRRRRRR..R.",
        ".RRRRRRRRR.RR.",
        "..RRRRRRR.....",
        "...RRRRR......",
        "RRRRRRRRRRRRR.",
        "..............",
      ],
    },
    lavatory: {
      colours: { M: [0x1f, 0x4f, 0xb0], W: [0xc4, 0x2a, 0x2a] },
      rows: [
        "..............",
        "..MMM....WWW..",
        "..MMM....WWW..",
        "..............",
        ".MMMMM...WWW..",
        ".MMMMM...WWW..",
        ".MMMMM..WWWWW.",
        ".MMMMM..WWWWW.",
        ".MMMMM.WWWWWWW",
        "..M.M..WWWWWWW",
        "..M.M....W.W..",
        "..M.M....W.W..",
        "..M.M....W.W..",
        "..............",
      ],
    },
  };
  for (const [name, { colours, rows }] of Object.entries(pictures)) {
    texture(blockOutput, `bulkhead_sign_${name}`, (x, y) => colours[rows[y - 1]?.[x - 1]] ?? LIGHTBOX, 1);
  }
  // The basketball worked out round its middle, from each pixel's middle: its outline and seams dark, a seam across it
  // and one down it, each a pixel wide, and one curving down either side, bowed in toward the middle.
  texture(blockOutput, "bulkhead_sign_ball", (x, y) => {
    const u = x + 0.5 - 8;
    const v = y + 0.5 - 8;
    const r = Math.hypot(u, v);
    if (r > 6.5) return LIGHTBOX;
    const seam = r > 5.6 || x === 7 || y === 7
      || Math.abs(Math.hypot(u + 10, v) - 6.5) < 0.55 || Math.abs(Math.hypot(u - 10, v) - 6.5) < 0.55;
    return seam ? [0x2a, 0x1a, 0x10] : [0xe8, 0x74, 0x1c];
  }, 1);
  texture(blockOutput, "bulkhead_sign_bezel", (x, y) => (y % 3 === 0 ? [0xc8, 0xcd, 0xd3] : [0xa6, 0xac, 0xb3]), 3);
}

// The WC signs (see WcSignBlock and OverheadWcSignBlock): a toilet seen from the side, its cistern at the back, lit on a
// dark face, green while the WC is free and red while it is taken, when someone sits on it too, in white, so the
// picture says it without the colour. The sign over a WC's door shows it big, the fourteen pixels inside the bulkhead
// bezel. The overhead sign's face is fourteen pixels across and ten up, in the middle of its texture: a white arrow
// the way to the WC (straight on, left, right or down the stairs, or none), and the toilet beside it, smaller. Its
// lightbox's housing: the cabin's off-white plastic, as the overhead lockers'.
{
  const FACE = [0x1a, 0x1d, 0x22];
  const WHITE = [0xf4, 0xf6, 0xf8];
  const FREE = [0x3c, 0xe0, 0x6e];
  const TAKEN = [0xff, 0x3d, 0x2e];
  // T is the toilet, P whoever sits on it, over the toilet.
  const toilet = {
    big: [
      "..............",
      ".TTT..........",
      ".TTT..........",
      ".TTT..........",
      ".TTT..........",
      ".TTT..........",
      ".TTTTTTTTTT...",
      ".TTTTTTTTTT...",
      "..TTTTTTTT....",
      "...TTTTTT.....",
      "....TTTT......",
      ".....TT.......",
      "....TTTT......",
      "..............",
    ],
    bigSitter: [
      "..............",
      "......PP......",
      "......PP......",
      ".....PPP......",
      ".....PPP......",
      ".....PPPPPP...",
      "..........P...",
      "..........P...",
      "..........P...",
      "..........P...",
      "..........PP..",
      "..............",
      "..............",
      "..............",
    ],
    small: [
      "........",
      "........",
      "TT......",
      "TT......",
      "TT......",
      "TTTTTTT.",
      ".TTTTTT.",
      "..TTTT..",
      "...TT...",
      "..TTTT..",
    ],
    smallSitter: [
      "....PP..",
      "....PP..",
      "...PPP..",
      "...PPP..",
      "...PPPPP",
      ".......P",
      ".......P",
      ".......P",
      ".......P",
      "........",
    ],
  };
  const ARROWS = {
    ahead: [".....", "..A..", ".AAA.", "A.A.A", "..A..", "..A..", "..A..", "..A..", "..A..", "....."],
    left: [".....", ".....", "..A..", ".A...", "AAAAA", ".A...", "..A..", ".....", ".....", "....."],
    right: [".....", ".....", "..A..", "...A.", "AAAAA", "...A.", "..A..", ".....", ".....", "....."],
    down: [".....", "..A..", "..A..", "..A..", "..A..", "..A..", "A.A.A", ".AAA.", "..A..", "....."],
    none: Array(10).fill("....."),
  };
  // Whoever sits on the toilet in front of it, the toilet in the state's colour, the rest the dark face.
  const lit = (bowl, sitter, taken) => (col, row) => {
    if (taken && sitter[row]?.[col] === "P") return WHITE;
    return bowl[row]?.[col] === "T" ? (taken ? TAKEN : FREE) : FACE;
  };
  for (const taken of [false, true]) {
    const suffix = taken ? "_occupied" : "";
    const big = lit(toilet.big, toilet.bigSitter, taken);
    texture(blockOutput, `wc_sign${suffix}`, (x, y) => big(x - 1, y - 1), 1);
    const small = lit(toilet.small, toilet.smallSitter, taken);
    for (const [arrow, rows] of Object.entries(ARROWS)) {
      texture(blockOutput, `overhead_wc_sign_${arrow}${suffix}`, (x, y) => {
        const col = x - 1;
        const row = y - 3;
        if (rows[row]?.[col] === "A") return WHITE;
        return col >= 6 ? small(col - 6, row) : FACE;
      }, 1);
    }
  }
  texture(blockOutput, "overhead_wc_sign", (x, y) => (y === 2 || y === 13 ? [0xd2, 0xd0, 0xc9] : [0xe6, 0xe4, 0xde]), 2);
}

// The staircases between the decks (see CabinStairsBlock). A tread: the cabin's blue carpet, in the cabin floor's
// small diamond pattern, between the white tops of the stringers down either side; a landing: the same carpet edge to
// edge. The risers, the stringers and everything under the stairs: smooth white panelling, a riser shaded a little
// just under the nosing that overhangs it. The nosing along each tread's edge: brushed steel, streaked along its
// length, catching the light along its top. The glass rails (see CabinRailBlock): clear glass with a faint cool tint,
// a little brighter in a few streaks running up it, on brushed steel posts under a brushed steel handrail.
{
  const CARPET_BLUE = [0x2e, 0x3f, 0x8c];
  const CARPET_BLUE_DARK = [0x26, 0x34, 0x75];
  const CARPET_BLUE_LIGHT = [0x3d, 0x52, 0xa6];
  const PANELLING = [0xee, 0xf0, 0xf2];
  const carpet = (x, y) => {
    const d = Math.abs((x % 8) - 4) + Math.abs((y % 8) - 4);
    if (d === 4) return CARPET_BLUE_LIGHT;
    if ((x + y) % 2 === 0 && d < 2) return CARPET_BLUE_DARK;
    return CARPET_BLUE;
  };
  const brushed = (base) => (x, y) => {
    if (y === 0) return [0xe2, 0xe7, 0xeb];
    const streak = Math.round(5 * Math.sin(y * 2.3) + 3 * Math.sin(y * 5.1 + x * 0.15));
    return base.map((v) => v + streak);
  };
  texture(blockOutput, "cabin_stairs_tread", (x, y) => (x === 0 || x === 15 ? PANELLING : carpet(x, y)), 5);
  texture(blockOutput, "cabin_landing", carpet, 5);
  texture(blockOutput, "cabin_stairs_panel", () => PANELLING, 2);
  texture(blockOutput, "cabin_stairs_riser", (x, y) => (y % 8 === 1 ? [0xd8, 0xdc, 0xe1] : PANELLING), 2);
  texture(blockOutput, "cabin_stairs_nosing", brushed([0xb4, 0xbc, 0xc4]), 2);
  texture(blockOutput, "cabin_rail_steel", brushed([0xa8, 0xb0, 0xb8]), 2);
  texture(blockOutput, "cabin_rail_glass", (x) => (x === 3 || x === 4 || x === 11 ? [0xe8, 0xf6, 0xfa, 0x5c] : [0xd2, 0xea, 0xf0, 0x42]), 0);
}

// The passengers' seats (see CabinSeatBlock). The upholstery is drawn in greys, light, and tinted on the client to
// the seat's colour: economy's a woven moquette, a fine check with a darker diagonal twill through it; business's
// leather, padded in channels between two seams with a row of stitches beside each. The headrest's cover: white linen
// with the livery's blue band along its top. Economy's shell is graphite plastic, its edges catching the light; on its
// back the tray table folded up, light grey with a rim round it and its latch at the top, over the seat pocket, dark
// fabric under an elastic band with the safety card's corner showing; its legs brushed aluminium. Business's shell is
// warm white, a faint line round each panel, and its armrest consoles dark walnut veneer, the grain along them.
{
  const grey = (v) => [v, v, v];
  texture(blockOutput, "cabin_seat_fabric", (x, y) => grey(((x + y) % 2 === 0 ? 0xe6 : 0xd8) - ((x - y + 16) % 5 === 0 ? 0x18 : 0)), 3);
  texture(blockOutput, "cabin_seat_leather", (x, y) => {
    if (x === 4 || x === 11) return grey(0xa6);
    if ((x === 3 || x === 12) && y % 2 === 0) return grey(0xf4);
    return grey(y % 5 === 4 ? 0xcc : 0xe2);
  }, 2);
  texture(blockOutput, "cabin_seat_cover", (x, y) => (y === 1 || y === 2 ? BLUE : y === 3 ? [0xd8, 0xdd, 0xe6] : [0xf6, 0xf6, 0xf2]), 2);
  texture(blockOutput, "economy_seat_shell", (x, y) => (x === 0 || y === 0 ? [0x5e, 0x63, 0x6b] : [0x46, 0x4a, 0x51]), 2);
  texture(blockOutput, "economy_seat_tray", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return [0x76, 0x7c, 0x84];
    if (y === 1 && x >= 6 && x <= 9) return [0x30, 0x33, 0x38];
    return x === 1 || y === 1 ? [0xb2, 0xb8, 0xbf] : [0x9c, 0xa2, 0xaa];
  }, 2);
  texture(blockOutput, "economy_seat_pocket", (x, y) => {
    if (y <= 1) return [0x1c, 0x1e, 0x22];
    if (y <= 4 && x >= 10 && x <= 13) return y === 2 ? [0xf2, 0xc4, 0x3a] : [0xf0, 0xf0, 0xea];
    return (x + y) % 3 === 0 ? [0x36, 0x3a, 0x40] : [0x2c, 0x2f, 0x35];
  }, 2);
  texture(blockOutput, "cabin_seat_frame", (x, y) => (y % 4 === 0 ? [0xc6, 0xcc, 0xd2] : (x + 3 * y) % 7 === 0 ? [0x8a, 0x91, 0x98] : [0xa4, 0xab, 0xb2]), 2);
  texture(blockOutput, "business_seat_shell", (x, y) => (x === 0 || y === 15 ? [0xd6, 0xd3, 0xcc] : [0xec, 0xea, 0xe4]), 2);
  texture(blockOutput, "business_seat_veneer", (x, y) => {
    const grain = Math.round(6 * Math.sin(y * 1.7 + Math.sin(x * 0.4) * 1.5));
    return [0x5c + grain, 0x3e + grain, 0x2a + grain];
  }, 2);
}

// The cabin carpet (see CabinCarpetBlock), drawn in light greys and tinted on the client to the carpet's colour: a
// fine twill with small diamonds woven into it, every other row of them half a step along, each a lighter ring round a
// dark knot. The aisle's runner: the same pile in fine ribs running along the aisle, a darker band along the edge
// where its lights are set in. The floor path lights: small warm white lights every four pixels in a dim channel, and
// the channel's aluminium sides.
{
  const grey = (v) => [v, v, v];
  texture(blockOutput, "cabin_carpet", (x, y) => {
    const cx = (x + (Math.floor(y / 8) % 2) * 4) % 8;
    const d = Math.abs(cx - 4) + Math.abs((y % 8) - 4);
    if (d === 0) return grey(0xa2);
    if (d === 3) return grey(0xf4);
    return grey((x + 2 * y) % 4 === 0 ? 0xc8 : 0xdc);
  }, 3);
  texture(blockOutput, "aisle_carpet", (x, y) => {
    if (y <= 2) return grey(y === 2 ? 0xb4 : 0xa8);
    return grey(y % 3 === 0 ? 0xc6 : 0xde);
  }, 3);
  texture(blockOutput, "aisle_light", (x) => (x % 4 === 1 || x % 4 === 2 ? [0xff, 0xf2, 0xc8] : [0x8a, 0x80, 0x66]), 0);
  texture(blockOutput, "aisle_light_channel", (x, y) => (y % 4 === 0 ? [0xd2, 0xd8, 0xde] : [0xa2, 0xa9, 0xb0]), 2);
}

// The gym hall's sprung floor (see GymFloorBlock): strips of lacquered maple two pixels wide running up the texture,
// each a shade of its own with a faint grain along it, a darker seam down one side of each and a butt joint across it
// at its own height, so the joints stagger; a glint of lacquer here and there. A court line: white paint over the
// same boards, their seams and joints faint under it.
{
  const strip = (x) => Math.floor(x / 2);
  const tone = (x) => [0, 9, -6, 4, -10, 6, -3, 11][strip(x)];
  const joint = (x, y) => y === (strip(x) * 7 + 3) % 16;
  texture(blockOutput, "gym_floor", (x, y) => {
    if (joint(x, y)) return [0xa6, 0x7e, 0x4e];
    if (x % 2 === 0) return [0xc4 + tone(x), 0x9c + tone(x), 0x66 + tone(x)];
    if ((x * 5 + y * 3) % 23 === 0) return [0xf2, 0xdc, 0xb4];
    const grain = Math.round(3 * Math.sin(y * 1.3 + strip(x)));
    return [0xd8 + tone(x) + grain, 0xb2 + tone(x) + grain, 0x7a + tone(x) + grain];
  }, 2);
  texture(blockOutput, "gym_floor_line", (x, y) => (joint(x, y) || x % 2 === 0 ? [0xe2, 0xe2, 0xda] : [0xf4, 0xf4, 0xee]), 2);
}

// The jet bridge: a steel tread plate in a yellow-and-black hazard border, a white arrow pointing north (the way
// it reaches out); its sides steel with a hazard band along the top.
{
  const stripe = (x, y) => ((x + y) % 4 < 2 ? HAZARD : RUBBER);
  texture(blockOutput, "jet_bridge_top", (x, y) => {
    if (x <= 1 || y <= 1 || x >= 14 || y >= 14) return stripe(x, y);
    const head = y >= 3 && y <= 7 && Math.abs(x - 7.5) <= y - 2.5;
    const shaft = y > 7 && y <= 12 && x >= 6 && x <= 9;
    if (head || shaft) return WHITE;
    return (x + y * 3) % 5 === 0 ? STEEL_DARK : STEEL;
  });
  texture(blockOutput, "jet_bridge_side", (x, y) => (y <= 1 ? stripe(x, y) : x === 0 || x === 15 || y === 15 ? STEEL_DARK : STEEL));
}

// Villager passengers. The check-in desk: a blue counter under a steel worktop, a white boarding pass on its front;
// on top a dark screen and the pass printer's slot. The boarding pass: white, a blue band across the top and a barcode.
{
  const ticket = (x, y) => x >= 3 && x <= 12 && y >= 7 && y <= 12 && !(x === 9 && y % 2 === 0);
  texture(blockOutput, "check_in_desk_front", (x, y) => {
    if (y <= 1) return STEEL;
    if (x === 0 || x === 15 || y === 15) return BLUE_SEAM;
    if (y === 4) return WHITE;
    if (ticket(x, y)) return x > 9 && y >= 9 && y <= 11 && x % 2 === 0 ? RUBBER : WHITE;
    return BLUE;
  });
  texture(blockOutput, "check_in_desk_side", (x, y) => (y <= 1 ? STEEL : x === 0 || x === 15 || y === 15 ? BLUE_SEAM : BLUE));
  texture(blockOutput, "check_in_desk_top", (x, y) => {
    if (x >= 3 && x <= 12 && y >= 2 && y <= 6) return x === 3 || x === 12 || y === 2 || y === 6 ? RUBBER : [0x2a, 0x4a, 0x6a];
    if (x >= 5 && x <= 10 && y === 11) return RUBBER;
    return x === 0 || x === 15 || y === 0 || y === 15 ? STEEL_DARK : STEEL;
  });
  texture(itemOutput, "boarding_pass", (x, y) => {
    if (y < 4 || y > 12 || x < 1 || x > 14) return CLEAR;
    if (y <= 5) return BLUE;
    if (x >= 10 && x <= 13 && y >= 7 && y <= 11) return x % 2 === 0 || x === 13 ? RUBBER : WHITE;
    if (x >= 2 && x <= 7 && (y === 8 || y === 10)) return SEAM;
    return WHITE;
  }, 0);
}

// The passenger screen: a slim display in a thin grey bezel, on the part of the texture its panel shows (x 1 to 14,
// y 3 to 12). Its glass shows the flight information page in miniature, for when it is too far off to be drawn live
// and in the hand: a header band with its bright stripe over two rows of three tiles on dark blue, a footer band
// under them. Its casing is plain dark grey.
{
  const BEZEL = [0x3a, 0x3f, 0x47];
  const BAND = [0x10, 0x25, 0x4a];
  texture(blockOutput, "passenger_screen", (x, y) => {
    if (x <= 1 || x >= 14 || y <= 3 || y >= 12) return BEZEL;
    if (y === 4 || y === 11) return BAND;
    if (y === 5) return [0x46, 0xc8, 0xf0];
    const tile = (y === 6 || y === 7 || y === 9 || y === 10) && x % 4 !== 2;
    return tile ? [0x13, 0x2d, 0x59] : [0x08, 0x12, 0x26];
  }, 1);
  texture(blockOutput, "passenger_screen_casing", () => [0x2c, 0x30, 0x36]);

  // The tail camera display's glass, for when it is too far off to be drawn live: dark, with the bezel along its top
  // and bottom only, so three side by side make one wide screen. In the hand: that screen showing the airliner from
  // behind against the sky and the ground, a red recording light in its corner.
  texture(blockOutput, "tail_camera_display", (x, y) => (y === 0 || y === 15 ? BEZEL : [0x0a, 0x0e, 0x14]), 1);
  texture(itemOutput, "tail_camera_display", (x, y) => {
    if (y < 3 || y > 12) return CLEAR;
    if (x === 0 || x === 15 || y === 3 || y === 12) return BEZEL;
    if (x === 2 && y === 5) return [0xe0, 0x30, 0x30];
    const fin = (x === 7 || x === 8) && y >= 4 && y <= 6;
    const fuselage = x >= 6 && x <= 9 && y >= 7 && y <= 9;
    const wings = x >= 2 && x <= 13 && y === 8;
    if (fin || fuselage || wings) return INK;
    if ((x === 4 || x === 11) && y === 9) return [0x9a, 0xa0, 0xa6];
    return y <= 8 ? [0x6c, 0xa8, 0xe0] : [0x4a, 0x8a, 0x3c];
  }, 0);
  // The autopilot screen's glass, for when it is too far off to be drawn live: the same dark glass, edge to edge.
  texture(blockOutput, "autopilot_screen", () => [0x0a, 0x0e, 0x14], 1);
}

// The control panels' pictures, in the glass cockpit's colours: every setting and button has one, so the panels work
// for players who cannot read yet. The panel's bezel: brushed dark metal, each row of it a little lighter or darker.
// Its close button: an X, white so it can be drawn in any colour. The autopilot's: the airliner from above for its
// title; a compass for the heading (cyan, its needle red to the north); a green hill with an up-and-down arrow for
// the altitude; a speedometer for the speed (green, amber and red, the needle well round); a magenta circle with its
// radius drawn in for the circle's radius. For the mode, a cyan arrow straight ahead to hold the heading and magenta
// arrows round a circle to the left or right, in the colours of the settings each flies. Then the buttons: a green
// airliner climbing off a runway to take off, or flying on level to engage in the air; an amber one settling onto
// the runway to land; a green tick to apply; a red power symbol to switch the autopilot off. For the pilot's HUD, the
// phases those buttons leave out: a green airliner climbing or descending beside a white arrow up or down, and an
// amber one braking along the runway toward a red stop bar; and the height above the ground, an airliner seen from
// the side over the grass with a cyan arrow between them. To hop a stuck airliner free: a cyan airliner seen from the
// side, lifted off the grass on two white arrows, puffs of the jets' exhaust under them.
{
  const guiOutput = resolve(ROOT, "src/main/resources/assets/aerolarge/textures/gui");
  const panelOutput = resolve(guiOutput, "panel");
  const autopilotOutput = resolve(guiOutput, "autopilot");
  mkdirSync(panelOutput, { recursive: true });
  mkdirSync(autopilotOutput, { recursive: true });
  const CYAN = [0x46, 0xc8, 0xf0];
  const CYAN_DARK = [0x2a, 0x8c, 0xb0];
  const MAGENTA = [0xe0, 0x40, 0xe0];
  const GREEN = [0x46, 0xe0, 0x70];
  const GREEN_DARK = [0x2a, 0x9a, 0x48];
  const AMBER = [0xf2, 0xb8, 0x30];
  const RED = [0xe8, 0x38, 0x2c];
  const RED_DARK = [0xa8, 0x22, 0x1a];
  const PALE = [0xc8, 0xce, 0xd6];
  const RUNWAY = [0x6a, 0x71, 0x7b];

  // Whether a pixel's middle lies within half a width of the segment from a to b.
  const onSegment = (x, y, [ax, ay], [bx, by], width) => {
    const dx = bx - ax;
    const dy = by - ay;
    const t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
    return Math.hypot(x - ax - t * dx, y - ay - t * dy) <= width / 2;
  };
  const inTriangle = (x, y, [ax, ay], [bx, by], [cx, cy]) => {
    const side = (px, py, qx, qy) => (x - qx) * (py - qy) - (px - qx) * (y - qy);
    const d1 = side(ax, ay, bx, by);
    const d2 = side(bx, by, cx, cy);
    const d3 = side(cx, cy, ax, ay);
    return !((d1 < 0 || d2 < 0 || d3 < 0) && (d1 > 0 || d2 > 0 || d3 > 0));
  };
  // The airliner from above, its nose on a bearing (degrees clockwise from up), its middle at (cx, cy): a fuselage
  // with swept wings and tailplane, thick enough to stay whole when turned.
  const airliner = (x, y, cx, cy, bearing, size = 1) => {
    const turn = (bearing * Math.PI) / 180;
    const dx = (x - cx) / size;
    const dy = (y - cy) / size;
    const along = dx * Math.sin(turn) - dy * Math.cos(turn);
    const out = Math.abs(dx * Math.cos(turn) + dy * Math.sin(turn));
    const fuselage = out <= 1.1 && along >= -6.8 && along <= 6.8 - out;
    const wings = out <= 6.8 && along <= 1.8 - out * 0.6 && along >= -1.2 - out * 0.6;
    const tail = out <= 3 && along <= -4.4 - out * 0.5 && along >= -6.4 - out * 0.5;
    return fuselage || wings || tail;
  };
  const icon = (folder, name, paint) => texture(folder, name, (x, y) => paint(x, y) ?? CLEAR, 0);

  texture(panelOutput, "bezel", (x, y) => {
    const streak = ((y * 5 + 3) % 7) - 3;
    return [0x2e + streak, 0x33 + streak, 0x3b + streak];
  }, 3);
  icon(panelOutput, "close", (x, y) => (onSegment(x, y, [3, 3], [12, 12], 2.6) || onSegment(x, y, [12, 3], [3, 12], 2.6) ? INK : null));

  icon(autopilotOutput, "airliner", (x, y) => (airliner(x, y, 7.5, 7.5, 0) ? INK : null));
  icon(autopilotOutput, "heading", (x, y) => {
    const r = Math.hypot(x - 7.5, y - 7.5);
    if (r >= 6 && r <= 7.4) return CYAN;
    const half = Math.abs(x - 7.5);
    if (y >= 2 && y <= 7 && half <= (y - 1) * 0.5) return x < 8 ? RED : RED_DARK;
    if (y >= 8 && y <= 13 && half <= (14 - y) * 0.5) return x < 8 ? INK : PALE;
    return null;
  });
  icon(autopilotOutput, "altitude", (x, y) => {
    const half = Math.abs(x - 3.5);
    const head = (y >= 1 && y <= 3 && half <= y - 0.5) || (y >= 10 && y <= 12 && half <= 12.5 - y);
    if (head || (y >= 4 && y <= 9 && half <= 0.5)) return INK;
    if (y === 14) return GREEN_DARK;
    if (x >= 6 && y <= 13 && y >= 4 + Math.abs(x - 10.5) * 1.2) return y <= 6 ? INK : x > 10 ? GREEN_DARK : GREEN;
    return null;
  });
  icon(autopilotOutput, "speed", (x, y) => {
    const r = Math.hypot(x - 7.5, y - 10.5);
    const angle = Math.atan2(10.5 - y, x - 7.5);
    if (r <= 1.2) return INK;
    if (onSegment(x, y, [7.5, 10.5], [7.5 + 5.2 * Math.cos(0.8), 10.5 - 5.2 * Math.sin(0.8)], 1.1)) return INK;
    if (r >= 5.6 && r <= 7.3 && angle >= -0.25 && angle <= Math.PI + 0.25) {
      return angle > (2 * Math.PI) / 3 ? GREEN : angle > Math.PI / 3 ? AMBER : RED;
    }
    if (y === 13 && x >= 2 && x <= 13) return RUNWAY;
    return null;
  });
  icon(autopilotOutput, "radius", (x, y) => {
    const r = Math.hypot(x - 7.5, y - 7.5);
    if (r >= 5.9 && r <= 7.3) return MAGENTA;
    if (r <= 1.6 || onSegment(x, y, [7.5, 7.5], [11.2, 3.8], 1.8)) return INK;
    return null;
  });
  icon(autopilotOutput, "hold_heading", (x, y) => {
    const half = Math.abs(x - 7.5);
    const head = y >= 1 && y <= 7 && half <= y - 0.5;
    const shaft = y >= 8 && y <= 14 && half <= 1.5;
    return head || shaft ? (x < 8 ? CYAN : CYAN_DARK) : null;
  });
  // Round to the left: an arc most of the way round, its arrowhead at the top pointing left; to the right, mirrored.
  for (const [name, mirror] of [["orbit_left", false], ["orbit_right", true]]) {
    icon(autopilotOutput, name, (px, y) => {
      const x = mirror ? 15 - px : px;
      const r = Math.hypot(x - 7.5, y - 8.5);
      const angle = (Math.atan2(8.5 - y, x - 7.5) * 180) / Math.PI;
      if (inTriangle(x, y, [2.2, 3.3], [8, -0.6], [8, 7.2])) return MAGENTA;
      if (r >= 4.2 && r <= 6.3 && !(angle > 92 && angle < 160)) return MAGENTA;
      return null;
    });
  }
  // A runway along the bottom, with its centre line.
  const runway = (x, y, from, to) => (y === 14 && x >= from && x <= to ? (x % 3 === 0 ? INK : RUNWAY) : null);
  icon(autopilotOutput, "take_off", (x, y) => (airliner(x, y, 8.5, 6.5, 45, 0.85) ? GREEN : runway(x, y, 0, 8)));
  icon(autopilotOutput, "land", (x, y) => (airliner(x, y, 6.5, 6.5, 135, 0.85) ? AMBER : runway(x, y, 7, 15)));
  icon(autopilotOutput, "engage", (x, y) => {
    if (airliner(x, y, 9.5, 7.5, 90, 0.82)) return GREEN;
    return x <= 2 && (y === 4 || y === 7 || y === 8 || y === 11) ? INK : null;
  });
  icon(autopilotOutput, "apply", (x, y) => (onSegment(x, y, [2.5, 8.5], [6, 12], 3) || onSegment(x, y, [6, 12], [13, 3.5], 3) ? GREEN : null));
  icon(autopilotOutput, "disengage", (x, y) => {
    const r = Math.hypot(x - 7.5, y - 8.5);
    const ring = r >= 4.4 && r <= 6.4 && !(y < 8 && Math.abs(x - 7.5) < 2.6);
    const bar = Math.abs(x - 7.5) <= 0.5 && y >= 1 && y <= 8;
    return ring || bar ? (x < 8 ? RED : RED_DARK) : null;
  });

  // The pilot's HUD: the phases the autopilot has no button for, and the height above the ground. An arrow two
  // pixels wide down a column, its head three rows deep at the top or the bottom.
  const arrow = (x, y, cx, top, bottom, up) => {
    const half = Math.abs(x - cx);
    const fromHead = up ? y - top : bottom - y;
    if (y < top || y > bottom) return false;
    return fromHead <= 2 ? half <= fromHead + 0.5 : half <= 0.5;
  };
  icon(autopilotOutput, "climb", (x, y) => (airliner(x, y, 10.5, 5.5, 45, 0.75) ? GREEN : arrow(x, y, 2.5, 8, 15, true) ? INK : null));
  icon(autopilotOutput, "descend", (x, y) => (airliner(x, y, 10.5, 9.5, 135, 0.75) ? GREEN : arrow(x, y, 2.5, 0, 7, false) ? INK : null));
  icon(autopilotOutput, "roll_out", (x, y) => {
    if (airliner(x, y, 7, 7, 90, 0.8)) return AMBER;
    if (x >= 14 && y >= 3 && y <= 11) return RED;
    return runway(x, y, 0, 15);
  });
  icon(autopilotOutput, "ground_height", (x, y) => {
    const fin = y <= 2 && x >= 1 && x <= 1 + y;
    const fuselage = (y === 3 || y === 4) && x >= 1 && x <= 13 - (y === 3 ? 1 : 0);
    const nose = y === 4 && x === 14;
    if (fin || fuselage || nose) return INK;
    if (arrow(x, y, 7.5, 6, 9, true) || arrow(x, y, 7.5, 10, 12, false)) return CYAN;
    if (y === 13) return GREEN;
    return y >= 14 ? [0x7a, 0x4e, 0x26] : null;
  });
  icon(autopilotOutput, "unstuck", (x, y) => {
    const fin = y >= 1 && y <= 3 && x >= 1 && x <= y;
    const fuselage = (y === 4 && x >= 1 && x <= 12) || (y === 5 && x >= 1 && x <= 14);
    const engine = y === 6 && x >= 7 && x <= 8;
    if (fin || fuselage || engine) return y === 4 || fin ? CYAN : CYAN_DARK;
    if (arrow(x, y, 3.5, 7, 10, true) || arrow(x, y, 11.5, 7, 10, true)) return INK;
    // Each puff a little cloud of three lobes, lit along its top.
    for (const cx of [3.5, 11.5]) {
      const lobes = [[cx - 1.4, 13.2], [cx + 1.4, 13.2], [cx, 12.2]];
      if (lobes.some(([px, py]) => Math.hypot(x - px, y - py) <= 1.45)) return y <= 11 ? INK : PALE;
    }
    return y === 15 ? GREEN : null;
  });

  // Air traffic control talking (see AtcHeadset): a pilot's headset, its band over the top, a white ear cup either side
  // and the microphone on its boom round to the mouth, with the radio's green lamp lit in the corner.
  icon(autopilotOutput, "radio", (x, y) => {
    const lamp = Math.hypot(x - 14, y - 1.5);
    if (lamp <= 1.6) return lamp <= 0.8 ? GREEN : GREEN_DARK;
    const cup = y >= 8 && y <= 13 && (x <= 2 || x >= 13) && !((x === 0 || x === 15) && (y === 8 || y === 13));
    if (cup) return x === 2 || x === 13 ? RUNWAY : INK;
    const r = Math.hypot(x - 7.5, y - 8.5);
    if (y <= 8 && r >= 5.6 && r <= 7) return PALE;
    if (onSegment(x, y, [2, 13], [6, 14.5], 1.2)) return PALE;
    return Math.hypot(x - 7.5, y - 14.5) <= 1.1 ? RUNWAY : null;
  });
}

// Duty free and the paper plane. The paper plane's icon: a white plane flying up to the right, its far wing and keel
// in shade below the fold, outlined dark so it shows on any background. In flight it is folded from ruled notebook
// paper: the wings from the top half of its texture, the keel, greyer, from the bottom half.
{
  const inTriangle = (x, y, [ax, ay], [bx, by], [cx, cy]) => {
    const side = (px, py, qx, qy) => (x - qx) * (py - qy) - (px - qx) * (y - qy);
    const d1 = side(ax, ay, bx, by);
    const d2 = side(bx, by, cx, cy);
    const d3 = side(cx, cy, ax, ay);
    return !((d1 < 0 || d2 < 0 || d3 < 0) && (d1 > 0 || d2 > 0 || d3 > 0));
  };
  // Shapes drawn by their outline: what the paint gives inside, a dark line round the outside, clear beyond.
  const outlined = (paint) => (x, y) => {
    const inside = paint(x, y);
    if (inside) return inside;
    const edge = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => paint(x + dx, y + dy));
    return edge ? [0x3a, 0x40, 0x48] : CLEAR;
  };
  const nose = [14.5, 1.5];
  const fold = [6.5, 9.5];
  texture(itemOutput, "paper_plane", outlined((x, y) => {
    if (inTriangle(x, y, nose, [1.5, 7.5], fold)) return [0xf8, 0xf8, 0xf4];
    if (inTriangle(x, y, fold, [5, 12.5], [8.5, 14])) return [0x9a, 0xa8, 0xb8];
    if (inTriangle(x, y, nose, fold, [8.5, 14])) return [0xc8, 0xd4, 0xe0];
    return null;
  }), 0);
  const entityOutput = resolve(ROOT, "src/main/resources/assets/aerolarge/textures/entity");
  texture(entityOutput, "paper_plane", (x, y) => {
    const ruled = y % 3 === 1;
    if (y < 8) return ruled ? [0xc8, 0xd8, 0xf0] : [0xf6, 0xf6, 0xf0];
    return ruled ? [0xb4, 0xc2, 0xd8] : [0xdc, 0xdc, 0xd6];
  }, 2);

  // Souvenirs. The pilot's cap: a navy crown of woven cloth, a glossy black band and peak, and gold for its cord and
  // badge. The peak shows a patch from the third row down, that row a line of shine along its edge.
  texture(itemOutput, "pilot_cap_crown", (x, y) => ((x + y) % 4 === 0 ? [0x1a, 0x22, 0x44] : [0x22, 0x2c, 0x56]), 3);
  texture(itemOutput, "pilot_cap_band", (x, y) => (y === 2 ? [0x40, 0x44, 0x4c] : [0x12, 0x13, 0x16]), 1);
  texture(itemOutput, "pilot_cap_gold", (x, y) => ((x + y) % 5 === 0 ? [0xff, 0xe8, 0x90] : GOLD), 2);

  // The pilot's helmet. Its icon: the helmet from its left side, nose to the right, white with the livery's blue
  // stripe over the crown, the airline's roundel on the ear cup, the gold visor down over the eyes under the cover it
  // slides up into, and the microphone on its boom in front of the mouth, outlined dark. Worn, its model shows a white
  // shell darkening a little toward the rim, the stripe edged in gold, the visor gold at the top darkening to smoked
  // bronze with a glint across it, dark trim, and the roundel: a white airliner from above on blue, ringed in gold.
  {
    const mix = (a, b, t) => a.map((c, i) => Math.round(c + (b[i] - c) * Math.min(1, Math.max(0, t))));
    const SHELL = [0xf6, 0xf7, 0xf9];
    const SHELL_SHADE = [0xb8, 0xc0, 0xcc];
    const VISOR_TOP = [0xe8, 0xb4, 0x3c];
    const VISOR_BOTTOM = [0x24, 0x1a, 0x10];
    const VISOR_GLINT = [0xfc, 0xe8, 0xa8];
    const TRIM = [0x26, 0x29, 0x2f];
    const visor = (t) => mix(VISOR_TOP, VISOR_BOTTOM, Math.min(1, Math.max(0, t)) ** 0.8);
    texture(itemOutput, "pilot_helmet", outlined((x, y) => {
      if (x < 0 || y < 0 || x > 15 || y > 15) return null;
      const cx = x + 0.5;
      const cy = y + 0.5;
      const radius = Math.hypot((cx - 7.6) / 6.4, (cy - 7.9) / 6.3);
      const angle = Math.atan2(cy - 7.9, cx - 7.6);
      // The visor bulges a little past the shell.
      if (cx > 9.5 && cy > 6.3 && cy < 10.9 && Math.hypot((cx - 7.6) / 7.0, (cy - 7.9) / 6.6) <= 1) {
        if ((x === 11 && y === 7) || (x === 12 && y === 7) || (x === 11 && y === 8)) return VISOR_GLINT;
        return visor((cy - 6.3) / 4.6);
      }
      if (x >= 13 && x <= 14 && y >= 12 && y <= 13) return [0x10, 0x10, 0x12];
      if (y === 13 && x >= 8 && x <= 12) return [0x6a, 0x70, 0x7a];
      if (radius > 1) return null;
      // The face is open under the visor, and the shell's rim rises from the nape toward it.
      if (cx > 9.5 && cy > 10.9) return null;
      if (cy > 13.3 - (cx - 2) * 0.15) return null;
      if (cx > 9.5 && cy > 4.6 && cy <= 6.3) return cy < 5.5 ? SHELL : mix(SHELL, SHELL_SHADE, 0.6);
      const ear = Math.max(Math.abs(cx - 6.5), Math.abs(cy - 9.5));
      if (ear < 0.5) return [0xf8, 0xf8, 0xf8];
      if (ear < 1.5) return BLUE;
      if (Math.hypot(cx - 6.5, cy - 9.5) < 2.6) return mix(SHELL, SHELL_SHADE, 0.75);
      if (radius > 0.7 && radius < 0.88 && angle < -0.8 && angle > -2.8) return BLUE;
      return mix(SHELL, SHELL_SHADE, (cx * 0.6 + cy - 6) / 12);
    }), 0);
    texture(itemOutput, "pilot_helmet_shell", (x, y) => mix(SHELL, SHELL_SHADE, y / 40), 2);
    texture(itemOutput, "pilot_helmet_stripe", (x, y) => (x === 6 || x === 9 ? GOLD : x === 7 || x === 8 ? BLUE : mix(SHELL, SHELL_SHADE, y / 40)), 2);
    // The visor's top row is the dark seam under its cover.
    texture(itemOutput, "pilot_helmet_visor", (x, y) => {
      if (y === 0) return TRIM;
      const tint = visor((y - 1) / 14);
      const glint = Math.abs(x - 1.4 * y - 1);
      return glint < 1.6 && y < 9 ? mix(VISOR_GLINT, tint, glint / 2.4 + y / 14) : tint;
    }, 1);
    texture(itemOutput, "pilot_helmet_trim", () => TRIM, 3);
    texture(itemOutput, "pilot_helmet_logo", (x, y) => {
      const r = Math.hypot(x - 7.5, y - 7.5);
      if (r > 7.6) return CLEAR;
      if (r > 6.4) return GOLD;
      const across = Math.abs(x - 7.5);
      const wing = across <= 6 && y - 5 >= (across - 1) * 0.5 && y - 5 <= (across - 1) * 0.5 + 2;
      const fuselage = across < 1 && y >= 2 && y <= 12;
      const tail = y >= 11 && y <= 12 && across <= 3 - (y - 11);
      return fuselage || wing || tail ? [0xf8, 0xf8, 0xf8] : BLUE;
    }, 0);
  }

  // The duty-free counter. Its cabinet shows the bottom ten rows of its textures: navy under a gold trim, its front
  // a white shopping bag with a green emerald on it, a picture of what the counter is for. On its top, seen through
  // the glass case, the goods laid out on a white cloth: a gift box, a pilot's cap, a perfume bottle and a paper plane.
  // The glass: faintly blue, a white frame round its edges and a glint across it.
  const EMERALD = [0x2e, 0xc4, 0x5a];
  const EMERALD_LIGHT = [0x9c, 0xf0, 0xb0];
  const cabinet = (paint) => (x, y) => {
    if (y <= 6) return y === 6 ? GOLD : BLUE;
    if (x === 0 || x === 15 || y === 15) return BLUE_SEAM;
    return paint(x, y) ?? BLUE;
  };
  texture(blockOutput, "duty_free_counter_front", cabinet((x, y) => {
    const handle = y >= 7 && y <= 8 && Math.abs(Math.hypot(x - 7.5, y - 9) - 2) < 0.7;
    if (handle) return [0x30, 0x34, 0x3a];
    if (x < 4 || x > 11 || y < 9 || y > 14) return null;
    // An emerald, cut as Minecraft's are: two pixels wide at its tips, four across its middle.
    const gem = Math.abs(x - 7.5) <= (Math.abs(y - 12) >= 2 ? 1 : 2);
    if (gem && y >= 10) return x < 8 && y < 12 ? EMERALD_LIGHT : EMERALD;
    return y === 9 ? [0xb0, 0x88, 0x4c] : [0xd8, 0xb0, 0x70];
  }));
  texture(blockOutput, "duty_free_counter_side", cabinet(() => null));
  texture(blockOutput, "duty_free_counter_top", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return [0xc8, 0xc4, 0xbc];
    if (x >= 2 && x <= 6 && y >= 2 && y <= 6) return x === 4 || y === 4 ? GOLD : [0xd0, 0x28, 0x28];
    if (x >= 9 && x <= 13 && y >= 2 && y <= 5) return y === 5 ? [0x14, 0x15, 0x18] : x === 11 && y === 3 ? GOLD : [0x22, 0x2c, 0x56];
    if (x >= 3 && x <= 5 && y >= 9 && y <= 13) return y === 9 ? [0xc8, 0xcc, 0xd2] : [0xe8, 0x80, 0xc0];
    if (inTriangle(x, y, [13.5, 9], [8.5, 11], [11, 13.5])) return x + y > 22 ? [0xc8, 0xd4, 0xe0] : [0xfc, 0xfc, 0xfc];
    return [0xf0, 0xee, 0xe8];
  }, 2);
  texture(blockOutput, "duty_free_glass", (x, y) => {
    if (x <= 1 || x >= 14 || y <= 1 || y >= 14) return [0xe4, 0xea, 0xf0];
    if (Math.abs(x + y - 12) <= 1) return [0xff, 0xff, 0xff, 0x90];
    return [0xc8, 0xe4, 0xf8, 0x40];
  }, 0);
}

// The furnished cabin's finishing touches (see CabinDecor). The springboard: a pool-blue non-slip grip with a
// pattern of raised dots for the board's top, white fibreglass for the rest of the board, and steel for the coil
// spring under its back end, each turn of it a darker band.
{
  texture(blockOutput, "springboard_grip", (x, y) => ((x + y) % 3 === 0 && (x - y + 16) % 3 === 0 ? [0x5a, 0xb0, 0xf0] : [0x2a, 0x8c, 0xd8]), 2);
  texture(blockOutput, "springboard_board", () => [0xf2, 0xf4, 0xf6], 2);
  texture(blockOutput, "springboard_spring", (x, y) => (y % 2 === 0 ? STEEL_DARK : CHROME), 2);
}
// The moon pool's tiling (see CabinFitOut.pool). The deck tile: sand-coloured tiles a half block square in pale grout,
// each a shade of its own, with a grid of raised dots on top for grip, each dot catching the light on its top left
// and casting a shadow on its bottom right; its sides the same tiles without the dots. The mosaic: small square tiles
// of glass in the pool's blues, each its own shade with a glint in its corner, in pale grout; with a lane line, the
// middle two columns of tiles a deep navy. The coping: white stone with a faint fleck, a highlight along the front
// edge of its top where it rounds over; its front the round's highlight and shadow over a band of dark blue tiles at
// the waterline, over the mosaic. The steps' nosing: the band's dark blue.
{
  const GROUT = [0xd8, 0xd2, 0xc4];
  const SAND = [0xdc, 0xca, 0xa2];
  const sandTone = (x, y) => [0, 6, -5, 3][(Math.floor(x / 8) + 2 * Math.floor(y / 8)) % 4];
  const sandTile = (x, y) => {
    if (x % 8 === 0 || y % 8 === 0) return GROUT;
    return SAND.map((v) => v + sandTone(x, y));
  };
  texture(blockOutput, "pool_deck_tile", (x, y) => {
    const dot = (x % 8) % 3 === 2 && (y % 8) % 3 === 2;
    const shadow = (x % 8) % 3 === 0 && (y % 8) % 3 === 0 && x % 8 !== 0 && y % 8 !== 0;
    if (dot) return SAND.map((v) => v + 22 + sandTone(x, y));
    if (shadow) return SAND.map((v) => v - 26 + sandTone(x, y));
    return sandTile(x, y);
  }, 3);
  texture(blockOutput, "pool_deck_tile_side", sandTile, 3);
  const POOL_BLUES = [[0x3a, 0x9c, 0xd8], [0x48, 0xae, 0xe2], [0x2c, 0x88, 0xc8], [0x5c, 0xbc, 0xe8], [0x34, 0x94, 0xcc]];
  const NAVY = [[0x14, 0x2c, 0x64], [0x1a, 0x34, 0x70], [0x10, 0x26, 0x5a]];
  const MOSAIC_GROUT = [0xd6, 0xe6, 0xee];
  const mosaic = (line) => (x, y) => {
    if (x % 4 === 3 || y % 4 === 3) return MOSAIC_GROUT;
    const column = Math.floor(x / 4);
    const tile = column * 7 + Math.floor(y / 4) * 3;
    const colours = line && (column === 1 || column === 2) ? NAVY : POOL_BLUES;
    const colour = colours[tile % colours.length];
    return x % 4 === 0 && y % 4 === 0 ? colour.map((v) => v + 40) : colour;
  };
  texture(blockOutput, "pool_mosaic", mosaic(false), 3);
  texture(blockOutput, "pool_mosaic_line", mosaic(true), 3);
  const STONE = [0xf2, 0xf1, 0xec];
  const fleck = (x, y) => ((x * 37 + y * 91 + x * y * 7) % 23 === 0 ? [0xdc, 0xdb, 0xd4] : STONE);
  const BAND = [0x18, 0x3c, 0x8c];
  texture(blockOutput, "pool_coping", fleck, 2);
  texture(blockOutput, "pool_coping_top", (x, y) => (y === 0 ? [0xfc, 0xfc, 0xf8] : y === 1 ? [0xe6, 0xe5, 0xe0] : fleck(x, y)), 2);
  texture(blockOutput, "pool_coping_front", (x, y) => {
    if (y === 0) return [0xfc, 0xfc, 0xf8];
    if (y === 1) return STONE;
    if (y === 2) return [0xd2, 0xd1, 0xcc];
    if (y <= 5) return x % 4 === 3 ? [0x2c, 0x4c, 0x90] : BAND;
    return mosaic(false)(x, y);
  }, 2);
  texture(blockOutput, "pool_steps_nosing", (x, y) => ((x + y) % 4 === 0 ? [0x24, 0x4a, 0x9c] : BAND), 2);
  // The pool ladder's treads: stainless steel ribbed across for grip, each rib catching the light.
  texture(blockOutput, "pool_ladder_tread", (x, y) => (y % 3 === 0 ? [0xe4, 0xe8, 0xec] : y % 3 === 1 ? [0x9a, 0xa2, 0xaa] : [0xb8, 0xbe, 0xc6]), 2);
}
// The gym hall's fittings. The wall bars: pale lacquered ash, a fine grain along it and a glint of lacquer here and
// there. The gym mat: blue vinyl with a fine pebbled grain; its sides the same with a seam along their middle; its
// piping a twisted grey cord. The climbing rope: natural hemp, three strands twisting down it. The glass backboard:
// toughened glass, faintly tinted and nearly clear, two streaks of glare across it; its shooter's square in red.
{
  texture(blockOutput, "wall_bars_wood", (x, y) => {
    if ((x * 3 + y * 7) % 19 === 0) return [0xf4, 0xe4, 0xc4];
    const grain = Math.round(5 * Math.sin(x * 1.9 + Math.sin(y * 0.5) * 2));
    return [0xde + grain, 0xc2 + grain, 0x8e + grain];
  }, 2);
  const VINYL = [0x24, 0x52, 0xb0];
  texture(blockOutput, "gym_mat", (x, y) => ((x * 7 + y * 5) % 9 === 0 ? VINYL.map((v) => v + 14) : VINYL), 4);
  texture(blockOutput, "gym_mat_side", (x, y) => (y === 8 ? VINYL.map((v) => v - 22) : VINYL), 3);
  texture(blockOutput, "gym_mat_piping", (x, y) => ((x + y) % 3 === 0 ? [0xa8, 0xae, 0xb6] : [0xd2, 0xd6, 0xdc]), 2);
  texture(blockOutput, "climbing_rope", (x, y) => {
    const strand = (x + y) % 6;
    return strand === 0 ? [0x8c, 0x6c, 0x40] : strand < 3 ? [0xd2, 0xb4, 0x7c] : [0xbc, 0x9c, 0x66];
  }, 3);
  texture(blockOutput, "glass_backboard", (x, y) => {
    const glare = x + y;
    return glare === 9 || glare === 10 || glare === 14 ? [0xf8, 0xfc, 0xff, 0x70] : [0xd8, 0xec, 0xf4, 0x38];
  }, 0);
  texture(blockOutput, "backboard_red", () => [0xd4, 0x22, 0x1e], 3);
}
// The starlight panel: a deep navy night sky with a faint haze across it; and its stars, on their own see-through
// layer, as an animation in which each star swells and fades on its own beat, so the ceiling gently twinkles. Most
// are white, a few warm yellow or pale blue, and two are bigger, with a cross of light round them. The stars keep
// inside the edge, so panels side by side run on as one sky.
{
  const NIGHT_SKY = [0x0c, 0x14, 0x34];
  const HAZE = [0x16, 0x20, 0x4a];
  texture(blockOutput, "starlight_panel", (x, y) => (Math.sin(x * 0.9 + y * 0.5) + Math.sin(y * 1.3 - x * 0.4) > 1.2 ? HAZE : NIGHT_SKY), 3);
  const FRAMES = 8;
  const STAR_COLOURS = [[0xff, 0xff, 0xff], [0xff, 0xf0, 0xb0], [0xc0, 0xdc, 0xff]];
  // Where each star is, its colour, the beat it twinkles on (as a share of the cycle) and whether it is a big one.
  const STARS = [
    [2, 3, 0, 0.0, false], [6, 1, 0, 0.35, false], [11, 2, 2, 0.7, false], [13, 6, 0, 0.15, false],
    [4, 7, 1, 0.55, true], [9, 9, 0, 0.9, false], [1, 11, 2, 0.25, false], [12, 12, 0, 0.6, true],
    [6, 13, 0, 0.8, false], [8, 5, 1, 0.45, false], [14, 14, 0, 0.05, false],
  ];
  const pixels = [];
  for (let frame = 0; frame < FRAMES; frame++) {
    for (let y = 0; y < SIZE; y++) {
      const row = [];
      for (let x = 0; x < SIZE; x++) {
        let pixel = CLEAR;
        for (const [sx, sy, colour, beat, big] of STARS) {
          const glow = 0.45 + 0.55 * (0.5 + 0.5 * Math.cos(2 * Math.PI * (frame / FRAMES + beat)));
          const core = x === sx && y === sy;
          const ray = big && Math.abs(x - sx) + Math.abs(y - sy) === 1;
          if (core || ray) {
            const strength = core ? glow : glow * 0.55;
            pixel = STAR_COLOURS[colour].map((c) => clamp(Math.round(NIGHT_SKY[0] + (c - NIGHT_SKY[0]) * strength)));
          }
        }
        row.push(pixel);
      }
      pixels.push(row);
    }
  }
  writeFileSync(resolve(blockOutput, "starlight_panel_stars.png"), encodePng(pixels));
  writeFileSync(resolve(blockOutput, "starlight_panel_stars.png.mcmeta"),
    JSON.stringify({ animation: { frametime: 5, interpolate: true } }, null, 2) + "\n");
}
// The gymnastic rings: pale polished wood with a faint grain, and a brown leather strap with a pale stitch across it
// every few pixels.
{
  texture(blockOutput, "gym_rings_wood", (x, y) => ((x + 2 * Math.floor(y / 3)) % 5 === 0 ? [0xc8, 0xa8, 0x70] : [0xe0, 0xc4, 0x8c]), 3);
  texture(blockOutput, "gym_rings_strap", (x, y) => (y % 4 === 0 ? [0xd8, 0xc8, 0xa0] : [0x6a, 0x40, 0x22]), 3);
}
// The hide and seek button, its pictures where its model shows them: on the button's top (the middle eight pixels
// square) a wide open eye on bright yellow, or, pressed, a shut eye with its lashes on glowing orange; on the stand's
// front (the bottom half) an open hand on dark blue-grey; its sides the same blue-grey under a yellow band.
{
  const STAND = [0x34, 0x3c, 0x4c];
  const BUTTON_YELLOW = [0xf6, 0xc8, 0x20];
  const BUTTON_ORANGE = [0xff, 0x8c, 0x20];
  const SKIN = [0xf0, 0xc0, 0x98];
  texture(blockOutput, "hide_and_seek_button_side", (x, y) => (y === 8 || y === 9 ? BUTTON_YELLOW : STAND), 3);
  texture(blockOutput, "hide_and_seek_button_front", (x, y) => {
    const finger = y >= 9 && y <= 11 && [5, 7, 9, 11].includes(x);
    const palm = y >= 12 && y <= 15 && x >= 5 && x <= 11;
    const thumb = y >= 11 && y <= 13 && x === 3 || y === 12 && x === 4;
    return finger || palm || thumb ? SKIN : y === 8 ? BUTTON_YELLOW : STAND;
  }, 2);
  texture(blockOutput, "hide_and_seek_button_cap", (x, y) => {
    const dx = x - 7.5;
    const dy = y - 7.5;
    if ((dx / 3.6) ** 2 + (dy / 2.4) ** 2 > 1) return BUTTON_YELLOW;
    const r = Math.hypot(dx, dy);
    if (r <= 0.8) return RUBBER;
    if (r <= 1.8) return [0x2a, 0x7c, 0xe0];
    return INK;
  }, 1);
  texture(blockOutput, "hide_and_seek_button_cap_closed", (x, y) => {
    const dx = x - 7.5;
    // The shut lid: a curve sagging across the middle, three lashes hanging under it.
    const curve = 8 - dx * dx / 6;
    const lid = Math.abs(y - curve) < 0.6 && Math.abs(dx) <= 3.5;
    const lash = [5, 8, 10].includes(x) && y > curve && y <= curve + 2;
    return lid || lash ? RUBBER : BUTTON_ORANGE;
  }, 1);
}

// The hold and the tail. The floor hatch down to the hold: a steel plate in a yellow-and-black hazard border with a
// white ladder on it, so it reads as the way down without a word.
{
  const stripe = (x, y) => ((x + y) % 4 < 2 ? HAZARD : RUBBER);
  texture(blockOutput, "floor_hatch", (x, y) => {
    if (x <= 1 || y <= 1 || x >= 14 || y >= 14) return stripe(x, y);
    if (x === 5 || x === 10 || (x > 5 && x < 10 && y % 3 === 0)) return INK;
    return (x + y * 3) % 5 === 0 ? STEEL_DARK : STEEL;
  });

  // The secret bookshelf door's hint, drawn glowing over vanilla's bookshelf on both faces of its lower half: one
  // gold spine on the lower shelf, its title band darker; the rest of the overlay is clear.
  texture(blockOutput, "secret_bookshelf_book", (x, y) => {
    if ((x === 7 || x === 8) && y >= 9 && y <= 14) return y === 11 ? [0xb0, 0x6a, 0x10] : x === 7 ? [0xff, 0xf4, 0xb0] : [0xff, 0xc8, 0x40];
    return CLEAR;
  }, 0);

  // The ball pit: shiny plastic balls in six colours packed tight, each lit from the upper left, deeper balls showing
  // darker between them; it tiles, the balls running on across a block's edges.
  const BALLS = [[0xe8, 0x30, 0x30], [0xf8, 0xd0, 0x20], [0x30, 0x90, 0xf0], [0x40, 0xc8, 0x50], [0xf0, 0x80, 0x20], [0xb0, 0x50, 0xe0]];
  const CENTRES = [[2.5, 2.5], [8, 2], [13.5, 3], [2, 8], [7.5, 8], [13, 8.5], [3, 13.5], [8.5, 13], [13.5, 13.5]];
  const wrap = (d) => d - 16 * Math.round(d / 16);
  texture(blockOutput, "ball_pit", (x, y) => {
    let nearest = 0;
    let best = Infinity;
    CENTRES.forEach(([cx, cy], i) => {
      const d = Math.hypot(wrap(x - cx), wrap(y - cy));
      if (d < best) {
        best = d;
        nearest = i;
      }
    });
    // Each row of balls shifted two colours on from the one above, so no two neighbours match, tiled or not.
    const [r, g, b] = BALLS[(nearest * 2 + Math.floor(nearest / 3)) % BALLS.length];
    const dx = wrap(x - CENTRES[nearest][0]);
    const dy = wrap(y - CENTRES[nearest][1]);
    if (best > 2.9) return [r * 0.35, g * 0.35, b * 0.35];
    if (Math.hypot(dx + 1, dy + 1) < 0.9) return [0xff, 0xff, 0xff];
    return dx + dy > 1.5 ? [r * 0.75, g * 0.75, b * 0.75] : [r, g, b];
  }, 3);

  // Suitcases: a hard shell in its colour with ridges down it and darker rims; the golden one shines gold, a white
  // star on it so it stands out from every other in the pile.
  const SHELLS = { red: [0xd0, 0x30, 0x30], blue: [0x30, 0x60, 0xd0], green: [0x30, 0xa0, 0x50], purple: [0x90, 0x40, 0xc0] };
  const shell = ([r, g, b], x, y) => {
    if (y <= 3 || y === 15) return [r * 0.6, g * 0.6, b * 0.6];
    return x % 3 === 0 ? [r * 0.8, g * 0.8, b * 0.8] : [r, g, b];
  };
  for (const [colour, rgb] of Object.entries(SHELLS)) {
    texture(blockOutput, `suitcase_${colour}`, (x, y) => shell(rgb, x, y), 4);
  }
  // A five-pointed star, a point straight up: its edge runs from 4.6 pixels out at the points to 2 at the notches.
  const star = (x, y) => {
    const degrees = ((Math.atan2(x - 7.5, 9.5 - y) * 180) / Math.PI + 360) % 72;
    const toPoint = Math.abs(degrees - 36) / 36;
    return Math.hypot(x - 7.5, y - 9.5) <= 2 + 2.6 * toPoint;
  };
  texture(blockOutput, "golden_suitcase", (x, y) => {
    if (star(x, y)) return [0xff, 0xff, 0xf0];
    if (y > 3 && (x + y === 7 || x + y === 8)) return [0xff, 0xf0, 0xa0];
    return shell(GOLD, x, y);
  }, 4);

  // Fairy lights: bulbs in four colours along a dark wire, by column, so each bulb of the model shows one colour
  // whichever face it is seen from; at night the same bulbs glow dim.
  const BULBS = [[0xff, 0x50, 0x50], [0xff, 0xe0, 0x50], [0x60, 0xf0, 0x70], [0x60, 0xa8, 0xff]];
  for (const [name, glow] of [["fairy_lights", 1], ["fairy_lights_night", 0.4]]) {
    texture(blockOutput, name, (x) => {
      const [r, g, b] = BULBS[Math.floor(x / 4)];
      return x % 4 === 1 || x % 4 === 2 ? [r * glow, g * glow, Math.min(255, b * glow + 40 * (1 - glow))] : [0x18, 0x19, 0x1b];
    }, 0);
  }
}

// The den's furniture. The bunk (see BunkBlock): its frame smooth white laminate. The mattress: white, quilted in
// diamonds stitched in pale blue. The blanket and the rail's padding are tinted to the bunk's colour, so they are
// drawn in light greys: the blanket a soft knit in ribs, the padding vinyl with a row of stitches across it every few
// pixels. The pillow: white, shaded at its edges. The reading light's shade: a dark diffuser while off, warm white and
// brightest in the middle while on.
{
  texture(blockOutput, "bunk_frame", () => [0xf3, 0xf1, 0xeb], 2);
  texture(blockOutput, "bunk_mattress", (x, y) => ((x + y) % 8 === 0 || (x - y + 16) % 8 === 0 ? [0xb8, 0xcc, 0xe4] : [0xf2, 0xf5, 0xf8]), 2);
  texture(blockOutput, "bunk_blanket", (x, y) => {
    const v = 236 - (x % 4 === 0 ? 30 : 0) - ((x + 2 * y) % 4 === 1 ? 12 : 0);
    return [v, v, v];
  }, 3);
  texture(blockOutput, "bunk_rail", (x, y) => (y % 4 === 0 && x % 2 === 0 ? [0xb0, 0xb0, 0xb0] : [0xe4, 0xe4, 0xe4]), 3);
  texture(blockOutput, "bunk_pillow", (x, y) => (x === 0 || y === 0 || x === 15 || y === 15 ? [0xde, 0xde, 0xda] : [0xf8, 0xf8, 0xf4]), 2);
  texture(blockOutput, "bunk_lamp", () => [0x5c, 0x60, 0x66], 2);
  // The bean bag (see BeanBagBlock), tinted to its colour, so drawn in light greys: soft nylon panels sewn together
  // along darker seams, a few creases running across them where it slumps, a sheen along the top of each panel. Its
  // carrying strap: dark grey webbing.
  texture(blockOutput, "bean_bag", (x, y) => {
    if (x === 0 || x === 8) return [0xb4, 0xb4, 0xb4];
    const crease = (x + 2 * y) % 9 === 0 ? 18 : 0;
    const v = (x % 8 === 3 ? 248 : 232) - crease;
    return [v, v, v];
  }, 3);
  texture(blockOutput, "bean_bag_strap", (x, y) => (y % 2 === 0 ? [0x34, 0x36, 0x3a] : [0x28, 0x2a, 0x2e]), 2);
  texture(blockOutput, "bunk_lamp_lit", (x, y) => {
    const glow = 1 - Math.hypot(x - 7.5, y - 7.5) / 11;
    return [0xff, clamp(Math.round(232 + 20 * glow)), clamp(Math.round(184 + 50 * glow))];
  }, 1);
}

// The hold's cargo net (see CargoNetBlock): charcoal webbing two pixels wide in a diamond lattice, one diamond to a
// block with its corners at the middles of the block's edges, a shade lighter along one edge of each strap, sewn where
// the straps cross with a pale bar tack, and clear between; the lattice is the same mirrored either way across the
// block, so the net's arms and the nets joined to it run on into each other. Its edge strap: the same webbing stitched
// down both sides. Its fittings: steel, with a bright streak here and there.
{
  const WEBBING = [0x2c, 0x2e, 0x33];
  const WEBBING_LIGHT = [0x40, 0x44, 0x4a];
  const TACK = [0x9a, 0xa0, 0xa8];
  const rising = (x, y) => (x + y) % 16;
  const falling = (x, y) => (x - y + 16) % 16;
  texture(blockOutput, "cargo_net", (x, y) => {
    const up = rising(x, y) === 7 || rising(x, y) === 8;
    const down = falling(x, y) === 7 || falling(x, y) === 8;
    if (up && down) return TACK;
    if (up) return rising(x, y) === 7 ? WEBBING_LIGHT : WEBBING;
    if (down) return falling(x, y) === 7 ? WEBBING_LIGHT : WEBBING;
    return CLEAR;
  }, 3);
  texture(blockOutput, "cargo_net_strap", (x, y) => {
    if (x === 4 || x === 11) return y % 2 === 0 ? TACK : WEBBING;
    return x % 3 === 0 ? WEBBING_LIGHT : WEBBING;
  }, 3);
  texture(blockOutput, "cargo_net_steel", (x, y) => {
    if ((x + 2 * y) % 7 === 0) return [0xd8, 0xdd, 0xe2];
    return y % 4 === 1 ? [0xa4, 0xab, 0xb2] : [0x84, 0x8b, 0x92];
  }, 2);
}

// The hold's floor (see CargoFloorBlock and BallMatBlock). Its plating: dark grey anti-slip plate, raised lentils two
// pixels long in fours, each four turned the other way from the next. The tie-down rail, laid along the texture's top
// three rows: yellow seat track with a dark slot down its middle, widening into a round cut-out every four pixels;
// below them its yellow sides. The roller track's rollers across it in its top four rows: steel rollers two pixels
// wide between dark gaps, a highlight down one side of each, darker at their ends. The ball mat: the plating, plain,
// with the rim of a dark socket round each ball in rows of four, and its balls polished steel, a white glint on each.
{
  const PLATE = [0x50, 0x56, 0x5d];
  const LENTIL = [0x78, 0x7e, 0x86];
  const SLOT = [0x26, 0x24, 0x1c];
  texture(blockOutput, "cargo_floor_plate", (x, y) => {
    const [lx, ly] = [x % 4, y % 4];
    const turned = (Math.floor(x / 4) + Math.floor(y / 4)) % 2 === 1;
    const lentil = lx >= 1 && lx <= 2 && ly >= 1 && ly <= 2 && (turned ? lx + ly === 3 : lx === ly);
    return lentil ? LENTIL : PLATE;
  }, 3);
  texture(blockOutput, "cargo_floor_rail", (x, y) => {
    const cutout = x % 4 === 1 || x % 4 === 2;
    if (y === 1 || (y === 0 || y === 2) && cutout) return SLOT;
    return y <= 2 ? HAZARD : [0xc4, 0x96, 0x16];
  }, 3);
  texture(blockOutput, "cargo_floor_roller", (x, y) => {
    if (x % 3 === 0) return [0x30, 0x33, 0x38];
    if (y === 0 || y === 3) return [0x86, 0x8c, 0x94];
    return x % 3 === 1 ? [0xe0, 0xe4, 0xe8] : [0xb4, 0xba, 0xc0];
  }, 2);
  // A ball stands on the second and third pixels of every four each way; its socket's rim shows round its far sides.
  texture(blockOutput, "ball_mat", (x, y) => {
    const [lx, ly] = [x % 4, y % 4];
    return (lx === 3 && ly >= 1) || (ly === 3 && lx >= 1) ? [0x2c, 0x30, 0x34] : PLATE;
  }, 3);
  texture(blockOutput, "ball_mat_ball", (x, y) => (x % 4 === 1 && y % 4 === 1 ? [0xfa, 0xfc, 0xff] : CHROME), 2);
}

// The ULD container (see UldContainerBlock), an LD3: ribbed aluminium sheet in an extruded frame. Its door, its back
// and its flat side are each one picture over the container's whole face, thirty-two pixels each way (two blocks), as
// seen from outside; its top and contour tile a block's sixteen. The door: a frame round the profile, slanting in at
// the bottom right with the contour, sheet round the opening, a bar across its top, and in the opening the bags packed
// inside behind a black net. The flat side: its id stencilled in black near the top, AKE and the airline's code AL
// over the serial number, one picture for each serial (UldContainerBlock.NUMBER), and one without for its lower blocks.
{
  const FRAME = [0x8a, 0x90, 0x98];
  const FRAME_EDGE = [0x6c, 0x72, 0x7a];
  const SHEET = [0xc2, 0xc7, 0xcd];
  const STENCIL_PAINT = [0x1c, 0x1e, 0x22];
  const BAGS = [[0x9a, 0x2a, 0x2a], [0x2a, 0x4a, 0x9a], [0x2a, 0x7a, 0x40], [0x6a, 0x34, 0x8a], [0x8a, 0x6a, 0x40]];
  const SERIALS = ["23805", "40117", "61932", "08446"];
  // The stencil's letters, three pixels wide and five tall.
  const LETTERS = {
    A: ["###", "#.#", "###", "#.#", "#.#"], K: ["#.#", "#.#", "##.", "#.#", "#.#"], E: ["###", "#..", "##.", "#..", "###"],
    L: ["#..", "#..", "#..", "#..", "###"], 0: ["###", "#.#", "#.#", "#.#", "###"], 1: [".#.", "##.", ".#.", ".#.", "###"],
    2: ["##.", "..#", ".#.", "#..", "###"], 3: ["##.", "..#", ".#.", "..#", "##."], 4: ["#.#", "#.#", "###", "..#", "..#"],
    5: ["###", "#..", "##.", "..#", "##."], 6: [".##", "#..", "###", "#.#", "###"], 7: ["###", "..#", ".#.", ".#.", ".#."],
    8: ["###", "#.#", "###", "#.#", "###"], 9: ["###", "#.#", "###", "..#", "##."],
  };
  // Whether the pixel is painted, for text whose first letter's top left pixel is at (u0, v0).
  const stencil = (text, u0, v0) => (u, v) => {
    const glyph = LETTERS[text[Math.floor((u - u0) / 4)]];
    return u >= u0 && v >= v0 && v < v0 + 5 && (u - u0) % 4 < 3 && glyph !== undefined && glyph[v - v0][(u - u0) % 4] === "#";
  };
  // Sheet ribbed every four pixels, a shadow and a highlight down each rib.
  const ribbed = (u) => (u % 4 === 1 ? [0xa8, 0xae, 0xb5] : u % 4 === 2 ? [0xd8, 0xdc, 0xe0] : SHEET);
  const framed = (u, v, inside) => {
    if (u === 0 || v === 0 || u === 31 || v === 31) return FRAME_EDGE;
    if (u === 1 || v === 1 || u === 30 || v === 30) return FRAME;
    return inside(u, v);
  };
  const side = (painted) => (u, v) => framed(u, v, (x, y) => (painted(x, y) ? STENCIL_PAINT : ribbed(x)));
  texture(blockOutput, "uld_container_side", side(() => false), 3, 32, 32);
  SERIALS.forEach((serial, n) => {
    const type = stencil("AKE", 4, 4);
    const airline = stencil("AL", 21, 4);
    const number = stencil(serial, 4, 11);
    texture(blockOutput, `uld_container_side_${n}`, side((u, v) => type(u, v) || airline(u, v) || number(u, v)), 3, 32, 32);
  });
  texture(blockOutput, "uld_container_back", (u, v) => framed(u, v, ribbed), 3, 32, 32);
  // The door, seen from the north: the container's x runs right to left, the contour on the right; how far in from the
  // contour's slant a pixel is (the slant runs from 5.8 pixels in at the base to the full width 14 pixels up).
  const fromSlant = (u, v) => {
    const x = 31.5 - u;
    const y = 31.5 - v;
    return y >= 14 ? x : x - 5.8 * (1 - y / 14);
  };
  texture(blockOutput, "uld_container_door", (u, v) => {
    const inward = fromSlant(u, v);
    if (u === 0 || v === 0 || v === 31 || inward < 1) return FRAME_EDGE;
    if (u === 1 || v === 1 || v === 30 || inward < 2) return FRAME;
    if (u < 4 || v < 4 || v > 27 || inward < 5) return SHEET;
    if (v === 4) return FRAME;
    if ((u + v) % 8 === 0 || (u - v + 32) % 8 === 0) return STENCIL_PAINT;
    if ((u - 4) % 6 === 5 || (v - 5) % 5 === 4) return [0x24, 0x22, 0x20];
    return BAGS[(Math.floor((u - 4) / 6) * 2 + Math.floor((v - 5) / 5) * 3) % BAGS.length];
  }, 3, 32, 32);
  texture(blockOutput, "uld_container_skin", (x, y) => (y === 0 ? FRAME : ribbed(x)), 3);
  texture(blockOutput, "uld_container_frame", () => FRAME, 3);
}

// The vacuum toilet. Its shroud: white plastic, a touch greyer toward the foot. The rim and the bowl's sides: brushed
// stainless, streaked along. The bowl's floor, where its model shows it (the middle six pixels square, a pixel below
// the middle): stainless round a pool of the blue rinse water, the drain dark in the middle of it. The seat: a little
// greyer than the shroud, so it shows against it; the lid white with a shine across it. The wall panel: warm grey
// laminate with faint seams along two edges and a stainless kick strip along its foot. The flush button: a blue
// square, lit pale and bright while the vacuum builds up again.
{
  const STAINLESS = [0xb8, 0xbe, 0xc4];
  const WHITE_PLASTIC = [0xf2, 0xf2, 0xee];
  const RINSE = [0x2e, 0x8a, 0xe0];
  texture(blockOutput, "vacuum_toilet_shroud", (x, y) => (y >= 12 ? [0xe2, 0xe3, 0xe0] : WHITE_PLASTIC), 2);
  texture(blockOutput, "vacuum_toilet_steel", (x, y) => (y % 3 === 0 ? [0xcc, 0xd2, 0xd8] : (x + 5 * y) % 7 === 0 ? [0xa4, 0xaa, 0xb2] : STAINLESS), 4);
  texture(blockOutput, "vacuum_toilet_bowl", (x, y) => {
    const r = Math.hypot(x - 7.5, y - 8.5);
    if (r < 1) return [0x18, 0x22, 0x30];
    if (r < 2.6) return r < 1.8 ? [0x1e, 0x6a, 0xc0] : RINSE;
    return y % 3 === 0 ? [0xcc, 0xd2, 0xd8] : STAINLESS;
  }, 3);
  texture(blockOutput, "vacuum_toilet_seat", () => [0xe4, 0xe5, 0xe2], 3);
  texture(blockOutput, "vacuum_toilet_lid", (x, y) => (x + y >= 13 && x + y <= 15 ? [0xfe, 0xfe, 0xfe] : WHITE_PLASTIC), 2);
  texture(blockOutput, "vacuum_toilet_panel", (x, y) => {
    if (y >= 13) return y === 13 ? [0x8e, 0x94, 0x9a] : STAINLESS;
    return x === 0 || y === 0 ? [0xc4, 0xc0, 0xb8] : [0xdc, 0xd8, 0xd0];
  }, 2);
  texture(blockOutput, "vacuum_toilet_button", (x, y) => ((x + y) % 4 === 0 ? [0x3c, 0x7e, 0xe8] : [0x24, 0x62, 0xd0]), 2);
  texture(blockOutput, "vacuum_toilet_button_lit", (x, y) => ((x + y) % 4 === 0 ? [0xe8, 0xfa, 0xff] : [0xa0, 0xe4, 0xff]), 2);
}
// The lavatory basin (see LavatoryBasinBlock); its vanity is the toilet's wall panel. Its counter: white solid
// surface with a faint fleck. The basin: white ceramic, a shade greyer toward its sides, round a chrome drain with its
// dark plug hole. Its tap, the soap dispenser's nozzle and the mirror's frame: chrome, streaked bright. The mirror:
// silvered glass with the cabin's blue in it, a few lighter streaks across it. Its light: warm white.
{
  texture(blockOutput, "lavatory_basin_counter", (x, y) => ((x * 7 + y * 3) % 13 === 0 ? [0xe0, 0xdf, 0xda] : [0xf2, 0xf1, 0xec]), 2);
  texture(blockOutput, "lavatory_basin_bowl", (x, y) => {
    const r = Math.hypot(x - 7.5, y - 7.5);
    if (r < 0.8) return [0x3c, 0x40, 0x46];
    if (r < 1.9) return [0xd0, 0xd6, 0xdc];
    return r > 5.5 ? [0xd8, 0xd8, 0xd4] : [0xee, 0xee, 0xea];
  }, 2);
  texture(blockOutput, "lavatory_basin_chrome", (x, y) => ((x + 2 * y) % 5 === 0 ? [0xf4, 0xf8, 0xfc] : [0xc4, 0xcb, 0xd2]), 2);
  texture(blockOutput, "lavatory_basin_mirror", (x, y) => {
    const d = (x - y + 32) % 16;
    return d === 3 || d === 4 || d === 10 ? [0xdc, 0xe6, 0xee] : [0xa8, 0xba, 0xc8];
  }, 1);
  texture(blockOutput, "lavatory_basin_light", () => [0xff, 0xf0, 0xd2], 0);
}
// Blue lav ice: the blue toilet water frozen, light blue and see-through, deeper blue in streaks where it froze in
// layers, with a few white glints in it.
{
  texture(blockOutput, "lav_ice", (x, y) => {
    if (random() < 0.05) return [0xf4, 0xfc, 0xff, 0xf0];
    return (x + 2 * y + Math.floor(random() * 2)) % 6 === 0 ? [0x34, 0x8c, 0xe0, 0xc8] : [0x6c, 0xc4, 0xf4, 0xb0];
  }, 0);
}
// Rubber ducks. The duck: bright yellow plastic. Beside its head, the same yellow with a black eye in the middle of the
// three pixels square the model shows of it. Its beak: orange.
{
  const YELLOW = [0xff, 0xd0, 0x20];
  texture(blockOutput, "rubber_duck", () => YELLOW, 4);
  texture(blockOutput, "rubber_duck_eye", (x, y) => (x === 1 && y === 1 ? [0x10, 0x10, 0x14] : YELLOW), 0);
  texture(blockOutput, "rubber_duck_beak", () => [0xf5, 0x8a, 0x1a], 3);
}

// The coffee machine, a bean-to-cup machine. Its sides: brushed steel, streaked along. Its body: gloss black, a faint
// shine running across it; the front of its head with a chrome trim along its foot, its top with vent slots. The cup
// bay behind the spout: matte charcoal with drip stains. The drip tray: a steel grille. Chrome for the spout and the
// steam wand. The bean hopper: smoked glass full of roasted beans, its lid gloss black. Its display (the part of the
// texture its model shows, eight pixels by five): a picture of what it is doing on black glass, over a progress bar
// along its foot: a green cup when ready, an amber bean grinding, an amber stream pouring into a cup, cyan steam
// frothing the milk, and a green tick when done, the bar full.
{
  const STEEL_LIGHT = [0xc4, 0xca, 0xd0];
  const STEEL_MID = [0xa8, 0xae, 0xb5];
  const BLACK = [0x1c, 0x1d, 0x21];
  const BLACK_SHINE = [0x2c, 0x2e, 0x33];
  const CHARCOAL = [0x2c, 0x2e, 0x32];
  const BEAN = [0x5a, 0x34, 0x1a];
  const BEAN_LIGHT = [0x7a, 0x4a, 0x24];
  const BEAN_DARK = [0x30, 0x1c, 0x10];
  texture(blockOutput, "coffee_machine_steel", (x, y) => (y % 4 === 0 ? STEEL_LIGHT : (x * 3 + y * 7) % 11 === 0 ? STEEL_MID : [0xb6, 0xbc, 0xc2]), 3);
  const gloss = (x, y) => (x + y === 12 || x + y === 13 ? BLACK_SHINE : BLACK);
  texture(blockOutput, "coffee_machine_black", gloss, 2);
  texture(blockOutput, "coffee_machine_front", (x, y) => (y === 8 ? CHROME : y === 9 ? STEEL_DARK : gloss(x, y)), 2);
  texture(blockOutput, "coffee_machine_top", (x, y) => {
    if (x <= 1 || x >= 14 || y <= 1 || y >= 14) return BLACK_SHINE;
    return y % 3 === 0 && x >= 4 && x <= 11 ? [0x08, 0x08, 0x0a] : BLACK;
  }, 2);
  texture(blockOutput, "coffee_machine_bay", (x, y) => ((x * 5 + 3) % 7 === 0 && y > 4 + (x % 3) ? [0x3a, 0x2a, 0x20] : CHARCOAL), 3);
  texture(blockOutput, "coffee_machine_tray", (x, y) => (x % 2 === 1 && y >= 2 && y <= 13 ? [0x30, 0x32, 0x36] : STEEL_LIGHT), 2);
  texture(blockOutput, "coffee_machine_chrome", (x, y) => ((x + y) % 5 === 0 ? [0xf0, 0xf4, 0xf8] : CHROME), 3);
  texture(blockOutput, "coffee_machine_hopper", (x, y) => {
    if ((x + 2 * y) % 9 === 0) return [0x8c, 0x9a, 0xa6];
    const bean = (x + 3 * (y % 2)) % 4;
    return bean === 0 ? BEAN_DARK : bean === 1 ? BEAN_LIGHT : BEAN;
  }, 4);
  texture(blockOutput, "coffee_machine_lid", (x, y) => (y === 1 ? BLACK_SHINE : BLACK), 2);

  const GREEN = [0x46, 0xe0, 0x70];
  const AMBER = [0xf2, 0xb8, 0x30];
  const CYAN = [0x46, 0xc8, 0xf0];
  const GLASS_BLACK = [0x08, 0x0c, 0x10];
  const BAR_OFF = [0x1c, 0x24, 0x2c];
  // Each step's picture, four rows of eight, its colour, and how much of the eight-pixel bar under it is lit.
  const DISPLAYS = {
    ready: [["..#.#...", ".#####..", ".####.#.", "..####.."], GREEN, 0],
    grinding: [["...##...", "..#.##..", "..##.#..", "...##..."], AMBER, 2],
    pouring: [["...#....", "...#....", ".#...#..", "..###..."], AMBER, 4],
    steaming: [["#..#..#.", ".#..#..#", "#..#..#.", ".#..#..#"], CYAN, 6],
    done: [["......#.", ".....#..", "..#.#...", "...#...."], GREEN, 8],
  };
  for (const [step, [rows, colour, bar]] of Object.entries(DISPLAYS)) {
    texture(blockOutput, `coffee_machine_display_${step}`, (x, y) => {
      // The model shows columns 4 to 11 and rows 3 to 7 of it.
      const u = x - 4;
      const v = y - 3;
      if (u < 0 || u > 7 || v < 0 || v > 4) return GLASS_BLACK;
      if (v === 4) return u < bar ? colour : BAR_OFF;
      return rows[v][u] === "#" ? colour : GLASS_BLACK;
    }, 0);
  }
}
// The coffee machine's cups, as its model shows them standing under the spout: white porcelain and its saucer, the
// espresso's golden crema, the cappuccino's milk foam with a dot of cocoa in the middle, the mocha's tall glass with
// chocolate at the bottom, coffee over it and cream on top, its whipped cream drizzled with chocolate, and the stream
// of coffee pouring. Their icons: a small espresso cup on its saucer, a wide cappuccino cup with its foam heaped over
// the rim and a heart of cocoa in it, and the mocha's tall glass under a dome of cream, each with steam rising and
// outlined dark, so they read on any background. The coffee machine's panel's title: a steaming cup, in white.
{
  const PORCELAIN = [0xf4, 0xf4, 0xf0];
  const PORCELAIN_SHADE = [0xd8, 0xd8, 0xd4];
  const CREMA = [0xb0, 0x6a, 0x30];
  const FOAM = [0xf2, 0xe6, 0xcc];
  const COCOA = [0x6a, 0x3a, 0x1a];
  const COFFEE = [0x7a, 0x46, 0x22];
  const CHOCOLATE = [0x4a, 0x26, 0x12];
  const CREAM = [0xfa, 0xf6, 0xee];
  const GLASS_EDGE = [0xc8, 0xd8, 0xe4];
  const OUTLINE = [0x24, 0x1a, 0x14];
  const STEAM = [0xe8, 0xec, 0xf0, 0xb0];
  texture(blockOutput, "coffee_cup", (x, y) => (y >= 14 ? PORCELAIN_SHADE : PORCELAIN), 2);
  texture(blockOutput, "coffee_crema", (x, y) => ((x + y) % 3 === 0 ? [0xc8, 0x84, 0x44] : CREMA), 4);
  texture(blockOutput, "coffee_foam", (x, y) => (Math.hypot(x - 7.5, y - 4.5) < 1 ? COCOA : FOAM), 2);
  // The glass's sides show rows 10 to 15 of it.
  texture(blockOutput, "mocha_glass", (x, y) => {
    if (y <= 11) return CREAM;
    return y <= 13 ? COFFEE : CHOCOLATE;
  }, 3);
  texture(blockOutput, "mocha_cream", (x, y) => ((x * 3 + y * 5) % 7 === 0 ? COCOA : CREAM), 2);
  texture(blockOutput, "coffee_stream", (x) => (x % 2 === 0 ? [0x5a, 0x30, 0x14] : [0x74, 0x40, 0x1c]), 2);

  // A drink's icon: what paint gives inside, a dark line round it, steam (not outlined) where steam gives it.
  const drink = (name, paint, steam) => texture(itemOutput, name, (x, y) => {
    const inside = paint(x, y);
    if (inside) return inside;
    const near = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => paint(x + dx, y + dy));
    if (near) return OUTLINE;
    return steam(x, y) ? STEAM : CLEAR;
  }, 0);
  // Two thin wisps rising, wavering, over columns a and b, between two rows.
  const wisps = (a, b, from, to) => (x, y) => y >= from && y <= to && (x === a + Math.round(Math.sin(y * 1.3)) || x === b + Math.round(Math.sin(y * 1.3 + 2)));
  drink("espresso", (x, y) => {
    if (y === 13 && x >= 3 && x <= 12) return PORCELAIN_SHADE;
    if (y >= 9 && y <= 12 && x >= 5 && x <= 9) return y === 9 ? CREMA : x === 9 ? PORCELAIN_SHADE : PORCELAIN;
    if (x === 11 && y >= 10 && y <= 11) return PORCELAIN;
    if (x === 10 && (y === 10 || y === 11)) return PORCELAIN_SHADE;
    return null;
  }, wisps(6, 8, 3, 7));
  drink("cappuccino", (x, y) => {
    if (y === 14 && x >= 1 && x <= 14) return PORCELAIN_SHADE;
    const half = y >= 12 ? 3.5 : 4.5;
    if (y >= 7 && y <= 13 && Math.abs(x - 6.5) <= half) return x - 6.5 > half - 1 ? PORCELAIN_SHADE : PORCELAIN;
    if (y >= 5 && y <= 6 && Math.abs(x - 6.5) <= 4.5 - (y === 5 ? 1 : 0)) {
      const heart = (y === 6 && (x === 5 || x === 7 || x === 6)) || (y === 5 && (x === 5 || x === 7));
      return heart ? COCOA : FOAM;
    }
    if ((x === 12 || x === 13) && y >= 8 && y <= 11 && !(x === 12 && (y === 9 || y === 10))) return PORCELAIN;
    return null;
  }, wisps(4, 9, 0, 3));
  drink("mocha", (x, y) => {
    if (y === 15 && x >= 4 && x <= 11) return GLASS_EDGE;
    if (y >= 6 && y <= 14 && x >= 4 && x <= 11) {
      if (x === 4 || x === 11) return GLASS_EDGE;
      return y >= 12 ? CHOCOLATE : y >= 8 ? COFFEE : FOAM;
    }
    const dome = Math.hypot((x - 7.5) / 4, (y - 5.5) / 3.2) <= 1 && y <= 5;
    if (dome) return (x + 2 * y) % 5 === 0 ? COCOA : CREAM;
    return null;
  }, () => false);

  const coffeeGui = resolve(ROOT, "src/main/resources/assets/aerolarge/textures/gui/coffee_machine");
  mkdirSync(coffeeGui, { recursive: true });
  texture(coffeeGui, "steaming_cup", (x, y) => {
    const cup = y >= 8 && y <= 12 && x >= 3 && x <= 10 && !(y === 12 && (x === 3 || x === 10));
    const handle = (x === 12 && y >= 9 && y <= 10) || (x === 11 && (y === 8 || y === 11));
    const saucer = y === 14 && x >= 1 && x <= 13;
    const steam = wisps(5, 8, 2, 6)(x, y);
    return cup || handle || saucer || steam ? INK : CLEAR;
  }, 0);
}

// The seat console and the lounge table (see SeatConsoleBlock); their shell, veneer and chrome are the business seat's
// and the basin's. The console's cubby: dark inside, a navy amenity kit with a gold zip lying in it. The reading
// lamp's shade, seen from under it: warm white.
{
  texture(blockOutput, "seat_console_cubby", (x, y) => {
    if (y >= 10 && y <= 12 && x >= 5 && x <= 11) return y === 10 ? GOLD : [0x22, 0x2c, 0x56];
    return (x + y) % 6 === 0 ? [0x30, 0x32, 0x37] : [0x26, 0x28, 0x2c];
  }, 2);
  texture(blockOutput, "seat_console_lamp", () => [0xff, 0xec, 0xc4], 0);
}

// The galley units (see GalleyOvenBlock and GalleyUnitBlock), in the galley's brushed steel, a darker seam round each
// panel. The oven's front: a dark strip of controls along its top, a green light on and an amber one off and a small
// cyan readout, over the door, a steel panel round a dark glass window catching a streak of light; its window lit
// while it cooks, a warm orange with the rack across it. The stowage: two by two standard containers, each with a
// white label card, held shut by red latches. The worktop: three drawers with dark seams between them, and its top
// brushed steel streaked along it. The trolley bay's back wall: dark, louvred for the chiller's air.
{
  const STEEL = [0xc4, 0xc9, 0xcf];
  const STEEL_SEAM = [0x8e, 0x94, 0x9b];
  const STEEL_SHADE = [0xb0, 0xb5, 0xbb];
  const CONTROLS = [0x26, 0x29, 0x2e];
  const LATCH = [0xd0, 0x24, 0x22];
  texture(blockOutput, "galley_oven_front", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return STEEL_SEAM;
    if (y >= 1 && y <= 3) {
      if (y === 2 && x === 3) return [0x46, 0xe0, 0x70];
      if (y === 2 && x === 5) return [0x6a, 0x4c, 0x14];
      if (y === 2 && x >= 9 && x <= 12) return x % 2 === 0 ? [0x46, 0xc8, 0xf0] : [0x18, 0x50, 0x64];
      return CONTROLS;
    }
    if (y === 4) return STEEL;
    if (x === 1 || x === 14 || y === 5 || y === 14) return STEEL_SEAM;
    if (x >= 3 && x <= 12 && y >= 7 && y <= 13) return x + y === 17 || x + y === 18 ? [0x3a, 0x3e, 0x46] : [0x1c, 0x1e, 0x22];
    return STEEL;
  });
  texture(blockOutput, "galley_oven_glow", (x, y) => (y % 4 === 2 ? [0x8a, 0x40, 0x18] : (x + y) % 5 === 0 ? [0xff, 0xc0, 0x60] : [0xf0, 0x90, 0x30]), 2);
  texture(blockOutput, "galley_stowage_front", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15 || x === 7 || x === 8 || y === 7 || y === 8) return STEEL_SEAM;
    const panelY = y % 8;
    const panelX = x % 8;
    if (panelY === 1 && panelX >= 3 && panelX <= 4) return LATCH;
    if (panelY >= 4 && panelY <= 5 && panelX >= 2 && panelX <= 5) return panelY === 4 ? [0xf2, 0xf2, 0xec] : [0xd8, 0xda, 0xd6];
    return panelY === 6 ? STEEL_SHADE : STEEL;
  });
  texture(blockOutput, "galley_latch", (x, y) => (y === 0 ? [0xf0, 0x50, 0x48] : LATCH), 2);
  texture(blockOutput, "galley_worktop_front", (x, y) => {
    if (x === 0 || x === 15 || y === 15 || y === 0 || y === 5 || y === 10) return STEEL_SEAM;
    return y === 4 || y === 9 || y === 14 ? STEEL_SHADE : STEEL;
  });
  texture(blockOutput, "galley_worktop_top", (x, y) => (y % 3 === 0 ? [0xd4, 0xd8, 0xdd] : (x * 5 + y) % 9 === 0 ? STEEL_SHADE : [0xc8, 0xcc, 0xd2]), 3);
  texture(blockOutput, "galley_bay", (x, y) => (y % 3 === 0 ? [0x1a, 0x1b, 0x1e] : [0x30, 0x32, 0x37]), 2);
}

// The planter (see PlanterBlock): its box glossy white, a band of light along the top of each side and a faint shadow
// at its foot; the soil dark and crumbly; clipped greenery, small leaves in three greens; the same in flower, pink and
// white blossoms dotted through it; and the bamboo's canes, green with a pale node every few pixels.
{
  texture(blockOutput, "planter_box", (x, y) => (y <= 1 ? [0xfa, 0xfa, 0xf8] : y >= 14 ? [0xd8, 0xd8, 0xd4] : [0xee, 0xee, 0xea]), 2);
  texture(blockOutput, "planter_soil", (x, y) => ((x * 3 + y * 5) % 7 === 0 ? [0x5a, 0x40, 0x2c] : [0x3a, 0x2a, 0x1e]), 4);
  const LEAVES = [[0x3c, 0x7c, 0x34], [0x4e, 0x96, 0x3e], [0x2c, 0x5e, 0x2a]];
  const leaves = (x, y) => LEAVES[(x * 7 + y * 3 + ((x * y) % 5)) % 3];
  texture(blockOutput, "planter_leaves", leaves, 6);
  texture(blockOutput, "planter_flowers", (x, y) => {
    if ((x * 5 + y * 3) % 11 === 0) return [0xf2, 0x9a, 0xc8];
    if ((x * 3 + y * 7) % 13 === 0) return [0xfa, 0xf4, 0xf6];
    return leaves(x, y);
  }, 6);
  texture(blockOutput, "planter_cane", (x, y) => (y % 5 === 0 ? [0xb8, 0xc8, 0x6a] : x % 2 === 0 ? [0x6a, 0x9c, 0x3a] : [0x58, 0x88, 0x30]), 3);
}

// The lounge's bar (see BarCounterBlock, BarStoolBlock and DisplayShelfBlock). The counter's front: glossy white, a
// faint sheen every few rows; its top dark stone with grey and pale flecks; its plinth near black; and the LED strip
// along its foot cool blue, brightest down its middle. The stool's seat: oxblood leather, a lighter ring of stitching
// round its top. The back bar's cabinet: walnut round two smoked glass doors, bottles dimly behind them; its shelves'
// back a smoked mirror, lit warm from above; the shelves' glass edges pale; and the bottles, green, amber, clear and
// blue glass, each with a pale label and a highlight down one side.
{
  texture(blockOutput, "bar_front", (x, y) => (y % 5 === 1 ? [0xfe, 0xfe, 0xfa] : y === 15 ? [0xd8, 0xd6, 0xd0] : [0xf2, 0xf1, 0xec]), 2);
  texture(blockOutput, "bar_top", (x, y) => {
    const fleck = (x * 7 + y * 13) % 17;
    return fleck === 0 ? [0x8a, 0x8c, 0x90] : fleck === 5 || fleck === 11 ? [0x4c, 0x4e, 0x52] : [0x2a, 0x2b, 0x2e];
  }, 3);
  texture(blockOutput, "bar_plinth", (x, y) => (y % 2 === 0 ? [0x22, 0x23, 0x26] : [0x2a, 0x2c, 0x30]), 2);
  texture(blockOutput, "bar_led", (x, y) => (y >= 6 && y <= 9 ? [0xd6, 0xec, 0xff] : [0x8c, 0xc8, 0xff]), 0);
  texture(blockOutput, "bar_stool_leather", (x, y) => {
    const r = Math.hypot(x - 7.5, y - 7.5);
    if (Math.abs(r - 4.5) < 0.5) return (x + y) % 2 === 0 ? [0xb0, 0x4a, 0x48] : [0x7a, 0x1e, 0x22];
    return r < 3 ? [0x84, 0x26, 0x2a] : [0x6a, 0x16, 0x1a];
  }, 2);
  const WALNUT = [0x5c, 0x3e, 0x2a];
  texture(blockOutput, "back_bar_doors", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15 || x === 7 || x === 8) return WALNUT;
    if (y === 7 && (x === 6 || x === 9)) return [0xd3, 0xd8, 0xdd];
    const bottle = (x % 3 === 1 || x % 3 === 2) && y >= 5;
    if (bottle) return x % 6 < 3 ? [0x3a, 0x2c, 0x1a] : [0x1e, 0x32, 0x22];
    return [0x1a, 0x1c, 0x20];
  }, 2);
  texture(blockOutput, "back_bar_mirror", (x, y) => {
    const v = Math.max(0, 0x70 - y * 5);
    const half = Math.round(v / 2);
    return (x + y) % 9 === 0 ? [0x6a + half, 0x5c + half, 0x48 + half] : [0x3a + v, 0x30 + v, 0x24 + half];
  }, 1);
  texture(blockOutput, "back_bar_glass", (x, y) => (y % 4 === 0 ? [0xe8, 0xf6, 0xfc] : [0xb4, 0xd4, 0xe0]), 1);
  for (const [colour, glass] of [["green", [0x22, 0x6a, 0x30]], ["amber", [0xa0, 0x58, 0x16]], ["clear", [0xc8, 0xdc, 0xe2]], ["blue", [0x2a, 0x52, 0xb0]]]) {
    texture(blockOutput, `bar_bottle_${colour}`, (x, y) => {
      if (y % 8 >= 4 && y % 8 <= 5) return [0xf0, 0xe8, 0xd4];
      return x % 4 === 0 ? glass.map((c) => Math.min(255, c + 0x50)) : glass;
    }, 1);
  }
}

// The duty-free kiosk (see DisplayShelfBlock and AwningBlock). The display's cabinet: the counters' blue under a gold
// line, two drawers with gold handles; its showcase's back softly lit warm white; the perfumes, pink, violet and amber
// glass with a highlight down one side, under gold caps; and the chocolates, boxes in brown and in red, each tied with a
// gold ribbon. The awning: lime and white stripes eight pixels wide with a fine weave, its valance the same with a white
// piping along its top and scalloped along its foot.
{
  texture(blockOutput, "duty_free_display_drawers", (x, y) => {
    if (y === 0 || y === 15 || x === 0 || x === 15) return BLUE_SEAM;
    if (y === 7 || y === 8) return y === 7 ? GOLD : BLUE_SEAM;
    if ((y === 3 || y === 11) && x >= 6 && x <= 9) return GOLD;
    return BLUE;
  }, 2);
  texture(blockOutput, "duty_free_display_back", (x, y) => (y <= 1 ? [0xff, 0xfa, 0xec] : [0xf2, 0xea, 0xdc]), 1);
  for (const [colour, glass] of [["pink", [0xf0, 0x70, 0xb0]], ["violet", [0x8a, 0x4c, 0xd0]], ["amber", [0xe0, 0x98, 0x30]]]) {
    texture(blockOutput, `perfume_${colour}`, (x, y) => (x % 4 === 1 ? glass.map((c) => Math.min(255, c + 0x40)) : glass), 1);
  }
  texture(blockOutput, "perfume_cap", (x, y) => ((x + y) % 4 === 0 ? [0xfc, 0xe8, 0x90] : GOLD), 2);
  for (const [colour, box] of [["brown", [0x5a, 0x34, 0x1e]], ["red", [0xb0, 0x1c, 0x28]]]) {
    texture(blockOutput, `chocolate_box_${colour}`, (x, y) => (x === 7 || x === 8 || y === 7 || y === 8 ? GOLD : box), 2);
  }
  const STRIPE = (x) => (x % 16 < 8 ? [0x7c, 0xd0, 0x2c] : [0xf6, 0xf6, 0xf0]);
  texture(blockOutput, "awning_fabric", (x, y) => STRIPE(x).map((c) => c - ((x + y) % 3 === 0 ? 0x10 : 0)), 2);
  texture(blockOutput, "awning_valance", (x, y) => {
    if (y <= 1) return [0xfc, 0xfc, 0xf8];
    if (y >= 5 && Math.abs((x % 4) - 1.5) > (y === 5 ? 1.5 : 0.5) || y >= 7) return CLEAR;
    return STRIPE(x);
  }, 2);
}

console.log("Textures written to", blockOutput, "and", itemOutput);
