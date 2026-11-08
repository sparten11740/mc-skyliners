import { fileURLToPath } from "node:url";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { deflateSync } from "node:zlib";

// Run from anywhere: node tools/generate_textures.mjs
// Writes the 16x16 airframe textures, the item icons and the control panels' pictures. The look is an airliner's:
// white skin with faint panel seams and rivet rows, an Airbus-blue livery, smoked cabin glass in white frames, grey
// cabin carpet.
const ROOT = fileURLToPath(new URL("..", import.meta.url));
const blockOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/block");
const itemOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/item");
mkdirSync(blockOutput, { recursive: true });
mkdirSync(itemOutput, { recursive: true });

const SIZE = 16;
// A rocket's airlock doors' pictures' size (see airlockDoorTextures): the whole doorway, three blocks each way.
const AIRLOCK_DOOR = 48;
const WHITE = [0xe8, 0xec, 0xf0];
const SEAM = [0xc4, 0xcb, 0xd3];
const RIVET = [0xb0, 0xb8, 0xc1];
const BLUE = [0x1c, 0x3d, 0x8c];
const BLUE_SEAM = [0x15, 0x2e, 0x6b];
const BLUE_RIVET = [0x2b, 0x52, 0xa8];
const GLASS = [0x2a, 0x3c, 0x55, 0xb0];
const GLASS_SHINE = [0x8f, 0xb4, 0xd6, 0xc8];
const COCKPIT_GLASS = [0x5e, 0x80, 0x9c, 0x80];
const SMOKED_GLASS = [0x10, 0x12, 0x14, 0x70];
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
/**
 * An item icon outlined so it reads on any background: what paint gives inside, a line of the outline's colour round
 * it, and round that what loose gives (steam, sparkles), not outlined, where it gives a colour.
 */
function outlinedIcon(name, paint, outline, loose) {
  texture(itemOutput, name, (x, y) => {
    const inside = paint(x, y);
    if (inside) return inside;
    const near = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => paint(x + dx, y + dy));
    if (near) return outline;
    return loose(x, y) ?? CLEAR;
  }, 0);
}
// The rocket's oval windows' pieces, each with whether it is in the window's lowest row, on the floor, and where its
// inner face is wall round the oval (not its hole or its lip), for the rooms' finishes to paint that wall in each
// room's own (see the rooms' finishes, below).
const OVAL_WALLS = [];

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

// The windscreen as a block of its own: the same lightly tinted glass edge to edge, thin frame bars along the top and
// one side.
texture(blockOutput, "cockpit_window", (x, y) => {
  if (y === 0 || x === 0) return FRAME_DARK;
  if (x + y >= 18 && x + y <= 19) return GLASS_SHINE;
  return COCKPIT_GLASS;
});

// The windscreen cut to the nose's curve: clear glass with a light steel-blue tint and no frame, its own posts being
// the hull, even all over, so it looks the same however many panes a look passes through.
texture(blockOutput, "cockpit_glass", () => COCKPIT_GLASS);

// The rocket's smoked glass round her cockpit: an even near-black grey, darker than the windscreen's blue but clear
// enough to see out of by day. Each of its faces is drawn, so a look passes through two of them as through any glass:
// together they are about as dark as one pane of the windscreen's tint at 0xA8.
texture(blockOutput, "smoked_glass", () => SMOKED_GLASS);
// The freighter's dimming glass (see DimmingGlassBlock) at each darkness past clear, which is the smoked glass itself:
// darker and denser each step, the darkest all but black, a faint glint left across it so it still reads as glass.
const DIMMING_GLASS = [[0x18, 0x1c, 0x24, 0x90], [0x0e, 0x10, 0x16, 0xc0], [0x06, 0x07, 0x0a, 0xe8]];
DIMMING_GLASS.forEach((pane, step) => texture(blockOutput, `dimming_glass_${step + 1}`, (x, y) =>
  (x + y === 9 ? [0x6a, 0x74, 0x84, pane[3]] : pane), 0));

// Clear hull glass, for the moon pool's view down: nearly clear with a faint sky-blue tint and a glint across it.
texture(blockOutput, "hull_glass", (x, y) => (x + y >= 9 && x + y <= 10 ? [0xf2, 0xfa, 0xff, 0x70] : [0xc8, 0xe4, 0xf4, 0x30]), 0);

// The rocket's lift glass: smoky, a dark blue-grey that still shows the car inside, a faint glint across it and a
// darker line round its edge where one pane meets the next.
texture(blockOutput, "lift_glass", (x, y) => {
  if (x === 0 || y === 0) return [0x10, 0x14, 0x1a, 0xb0];
  if (x + y >= 11 && x + y <= 12) return [0x9c, 0xb4, 0xc8, 0x70];
  return [0x1c, 0x24, 0x30, 0x90];
}, 0);

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

// A retractable gear leg's bay, seen from below: the gear doors shut, two white leaves meeting down the middle in a
// seam, with a seam all round where they meet the belly.
texture(blockOutput, "gear_bay_bottom", (x, y) => {
  if (x === 0 || y === 0 || x === 15 || y === 15 || x === 7 || x === 8) return SEAM;
  if ((y === 3 || y === 12) && (x === 2 || x === 13)) return STEEL;
  return WHITE;
}, 3);

// The laser path clearer. Its hatch in the belly under the nose, seen from below: the white skin with a round hatch
// in it, a seam round it and a dark slot across it where the turret drops out. Its emitter, the glowing red tip of its
// barrel, brighter in the middle. (The top of its switch is with the console's panels, below.)
texture(blockOutput, "laser_hatch", (x, y) => {
  const r = Math.hypot(x - 7.5, y - 7.5);
  if (r > 6.5 && r < 7.5) return SEAM;
  if (r <= 6.5 && y >= 7 && y <= 8) return STEEL;
  return WHITE;
}, 3);
// The amber its switch's button glows while, low down, the laser holds its fire at blocks: a lens like the jump
// light's, brighter in the middle.
texture(blockOutput, "laser_amber", (x, y) => {
  if (x === 0 || y === 0 || x === 15 || y === 15) return FRAME_DARK;
  const glow = Math.max(0, 1 - Math.hypot(x - 7.5, y - 7.5) / 8);
  return [0xf0, 0xa0, 0x20].map((c) => clamp(Math.round(c + 60 * glow)));
});
texture(blockOutput, "laser_emitter", (x, y) => {
  const glow = Math.max(0, 1 - Math.hypot(x - 7.5, y - 7.5) / 8);
  return [clamp(Math.round(0xd0 + 0x2f * glow)), clamp(Math.round(0x20 + 0xb0 * glow * glow)), clamp(Math.round(0x20 + 0xa0 * glow * glow))];
}, 0);

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
// The cockpit door: one picture of the whole panel, two blocks wide and three or four tall, which its renderer lays
// across both its faces (drawn hinged on the left, seen from the aisle, and mirrored for one hinged on the right).
// Pale grey, its top corners rounded off (clear, cut to the arc CockpitDoorRenderer draws its edge round) inside a
// darker edge, a rounded seam inset all round, a brushed steel kick plate along the floor; high in its middle a round
// porthole, a hole through the panel in a steel ring, which the renderer glazes; and a round chrome handle rose by the
// edge away from the hinge, a block up, where the renderer puts its lever. The porthole's middle is a block below the
// top and its hole six pixels across from it; the corners' radius twelve pixels; the rose's middle at 27.5 across.
// Edges and the porthole's reveal are cockpit_door_edge.
const COCKPIT_DOOR = [0xd6, 0xd5, 0xd0];
const COCKPIT_DOOR_EDGE = [0xa9, 0xa8, 0xa3];
texture(blockOutput, "cockpit_door_edge", () => COCKPIT_DOOR_EDGE, 3);
for (const rows of [3, 4]) {
  const width = 2 * SIZE;
  const height = rows * SIZE;
  texture(blockOutput, `cockpit_door_${rows}`, (x, y) => {
    const px = x + 0.5;
    const py = y + 0.5;
    // How far in from the panel's outline, round its rounded top corners.
    const cornerX = Math.max(12 - px, px - (width - 12), 0);
    const cornerY = Math.max(12 - py, 0);
    const inset = cornerX > 0 && cornerY > 0 ? 12 - Math.hypot(cornerX, cornerY) : Math.min(px, width - px, py, height - py);
    if (inset < 0) return CLEAR;
    if (inset < 1) return COCKPIT_DOOR_EDGE;
    const porthole = Math.hypot(px - 16, py - 16);
    if (porthole < 6) return CLEAR;
    if (porthole < 7) return STEEL_DARK;
    if (porthole < 8) return CHROME;
    const rose = Math.hypot(px - 27.5, py - (height - 16));
    if (rose < 2.5) return CHROME;
    if (rose < 3.3) return STEEL_DARK;
    if (y >= height - 4) return y === height - 4 ? STEEL_DARK : STEEL;
    if (inset >= 3 && inset < 4) return SEAM;
    return COCKPIT_DOOR;
  }, 2, width, height);
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
// For a door open in flight the diffuser glows an alarm red, brightest in the middle, in its frame left dark.
texture(blockOutput, "cabin_light_alarm", (x, y) => {
  if (x === 0 || y === 0 || x === 15 || y === 15) return [0x30, 0x14, 0x14];
  if (x === 1 || y === 1 || x === 14 || y === 14) return [0x48, 0x1a, 0x1a];
  const glow = 1 - Math.hypot(x - 7.5, y - 7.5) / 11;
  return [clamp(Math.round(200 + 55 * glow)), clamp(Math.round(20 + 60 * glow)), clamp(Math.round(16 + 40 * glow))];
}, 2);
// The call button's cap: bright yellow so the ground crew find it.
texture(blockOutput, "airstair_call_button", (x, y) => (x + y) % 5 === 0 ? [0xd0, 0x9f, 0x14] : HAZARD, 3);
// The lit push-buttons' caps (see LitButtonBlock): lit from behind, the middle six pixels the face, with a picture on
// it in a dark shade of the cap's colour, a row of the picture to a row of pixels, "#" where it is drawn.
const litCap = (picture) => (glow, glyph) => (x, y) => (picture[y - 5]?.[x - 5] === "#" ? glyph : glow);
// The airstair button beside the controller (see AirstairButtonBlock): its cap the call button's amber, with the
// controller's staircase on it in three steps rising to the right in dark amber; pressed, the cap glows brighter,
// nearly white, the stairs a deeper amber on it. Its bezel: dark steel, a lighter line along the top of each row of
// brushing.
{
  const cap = litCap(["....##", "....##", "..####", "..####", "######", "######"]);
  texture(blockOutput, "airstair_button", cap([0xf6, 0xae, 0x22], [0x5a, 0x34, 0x08]), 1);
  texture(blockOutput, "airstair_button_pressed", cap([0xff, 0xec, 0xa8], [0xa8, 0x6c, 0x10]), 1);
  texture(blockOutput, "airstair_button_bezel", (x, y) => (y % 3 === 0 ? [0x6e, 0x74, 0x7c] : [0x4a, 0x50, 0x57]), 2);
}
// The freighter's ramp call button (see RampButtonBlock), in the airstair button's bezel and amber: on its cap her belly
// along the top and her ramp let down from it to the right in dark amber; pressed, the cap glows nearly white.
{
  const cap = litCap(["######", "##....", ".##...", "..##..", "...##.", "....##"]);
  texture(blockOutput, "ramp_button", cap([0xf6, 0xae, 0x22], [0x5a, 0x34, 0x08]), 1);
  texture(blockOutput, "ramp_button_pressed", cap([0xff, 0xec, 0xa8], [0xa8, 0x6c, 0x10]), 1);
}
// The door button beside each hull door (see DoorButtonBlock), in the airstair button's bezel: its cap sky blue, unlike
// the airstairs' amber, with a door on it in dark blue, a tall slab with its handle by its right edge, where the cap's
// light shows through; pressed, the cap glows brighter, nearly white, the door a deeper blue on it.
{
  const cap = litCap([".####.", ".####.", ".####.", ".##.#.", ".####.", ".####."]);
  texture(blockOutput, "door_button", cap([0x3c, 0xb4, 0xf0], [0x0a, 0x2a, 0x5c]), 1);
  texture(blockOutput, "door_button_pressed", cap([0xd2, 0xf2, 0xff], [0x1a, 0x5c, 0xa8]), 1);
}
// A lift's call button (see LiftCallButtonBlock), in the airstair button's bezel: its cap a pale steel with an arrow up
// over an arrow down on it in dark grey, as a lift's call button has; pressed, called, it glows amber, the arrows a
// deep brown on it, until the car comes.
{
  const cap = litCap(["..##..", ".####.", "......", "......", ".####.", "..##.."]);
  texture(blockOutput, "lift_call_button", cap([0xd6, 0xdc, 0xe2], [0x34, 0x3a, 0x42]), 1);
  texture(blockOutput, "lift_call_button_pressed", cap([0xff, 0xc4, 0x48], [0x6a, 0x3c, 0x06]), 1);
}
// A zeppelin's speed boost button (see BoostButtonBlock), in the airstair button's bezel: its cap a fiery red, with a
// fast-forward arrow on it in dark red, two bold chevrons pointing the way she goes; pressed, the cap glows brighter,
// nearly white, the arrow a deeper red on it.
{
  const cap = litCap(["#..#..", "##.##.", ".##.##", ".##.##", "##.##.", "#..#.."]);
  texture(blockOutput, "boost_button", cap([0xf0, 0x48, 0x20], [0x50, 0x0c, 0x04]), 1);
  texture(blockOutput, "boost_button_pressed", cap([0xff, 0xd0, 0xa8], [0xb0, 0x28, 0x0c]), 1);
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
// The top of the laser path clearer's switch on the console: a dark panel pictured with a little turret firing a red
// beam out to the right, so it reads as the laser without a word.
texture(blockOutput, "laser_switch_top", (x, y) => {
  if (x === 0 || y === 0 || x === 15 || y === 15) return PANEL_EDGE;
  if (x >= 2 && x <= 4 && y >= 6 && y <= 9) return STEEL;
  if (x >= 5 && x <= 6 && y >= 7 && y <= 8) return STEEL;
  if (x >= 7 && y === 7) return [0xff, 0x30, 0x30];
  if (x >= 7 && (y === 6 || y === 8)) return [0x90, 0x18, 0x18];
  return PANEL_DARK;
}, 2);
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
// The flight deck's curved desk under its screens (see CockpitCasing), drawn as one moulded console round the seats:
// its body a plain blue-grey a step lighter than the screens' black, so they stand out from it, with no seams, so it
// runs on unbroken round the turns; its sloped top a shade lighter, brushed along its length in fine streaks that
// carry on from one panel to the next; and the light steel trim along the slope's edges.
const DESK_GREY = [0x3a, 0x41, 0x4b];
texture(blockOutput, "cockpit_desk", () => DESK_GREY, 3);
texture(blockOutput, "cockpit_desk_top", (x, y) => {
  const streak = y % 3 === 0 ? 6 : y % 3 === 1 ? 0 : -3;
  return [DESK_GREY[0] + 14 + streak, DESK_GREY[1] + 15 + streak, DESK_GREY[2] + 16 + streak];
}, 2);
texture(blockOutput, "cockpit_trim", (x, y) => (y % 4 === 1 ? [0xb4, 0xba, 0xc2] : [0x8e, 0x95, 0x9e]), 2);
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

// The flight crew's locker. Its cabinet: brushed grey-blue panels, streaked up and down, catching the light along their
// top, quiet enough beside the instruments that the door's picture stands out rather than the box. Its door, the face
// the model lays from column 1 (the hinge) to 14 and down to row 14: brushed pale grey-blue in a grey edge, and on it,
// so that someone who cannot read yet knows what is inside, a pilot's peaked cap seen from the front, outlined dark:
// its navy crown with the gold wings badge, the black band with its gold braid and the black peak. Under it a vent
// slot; by the free edge, where the model's handle stands, a shadow under the handle. Inside: dark, the same streaks
// fainter, darkest along the floor. The handle: chrome.
{
  const brushed = (base, depth) => (x, y) => {
    const streak = Math.round(depth * (Math.sin(x * 2.3) + 0.6 * Math.sin(x * 5.1 + y * 0.15)));
    return base.map((v) => v + streak);
  };
  const cabinet = brushed([0x96, 0xa2, 0xb0], 3);
  texture(blockOutput, "crew_locker_side", (x, y) => (y === 0 ? [0xb6, 0xc0, 0xcc] : cabinet(x, y)), 2);
  const inside = brushed([0x2e, 0x31, 0x36], 1);
  texture(blockOutput, "crew_locker_inside", (x, y) => (y === 15 ? [0x1c, 0x1e, 0x22] : inside(x, y)), 2);
  texture(blockOutput, "crew_locker_handle", (x) => (x % 2 === 0 ? CHROME : [0xb4, 0xbb, 0xc2]), 1);
  // The pilot's cap, from column 2 and row 1 of the door.
  const CAP = [
    "..ooooooo..",
    ".onnnnnnno.",
    "onnnnnnnnno",
    "onnnnGnnnno",
    "onnGGyGGnno",
    "onnnnGnnnno",
    "obbbbbbbbbo",
    "oGGGGGGGGGo",
    ".oppppppho.",
    "..ooooooo..",
  ];
  const CAP_COLOURS = {
    o: [0x2a, 0x2e, 0x36], n: [0x1e, 0x2e, 0x5c], b: [0x16, 0x18, 0x1c], p: [0x22, 0x25, 0x2b], h: [0x4a, 0x50, 0x5a],
    G: [0xf0, 0xc0, 0x48], y: [0xfc, 0xe8, 0xa8],
  };
  const door = brushed([0xc4, 0xcc, 0xd6], 1.5);
  texture(blockOutput, "crew_locker_door", (x, y) => {
    if (x === 1 || x === 14 || y === 0 || y === 14) return [0xa8, 0xb2, 0xbe];
    const pixel = CAP[y - 1]?.[x - 2];
    if (pixel !== undefined && pixel !== ".") return CAP_COLOURS[pixel];
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
  bulb: [0xff, 0xd0, 0x80],
  fairy_red: [0xf0, 0x30, 0x28],
  fairy_green: [0x28, 0xe0, 0x50],
  fairy_gold: [0xff, 0xc0, 0x28],
  fairy_blue: [0x30, 0x68, 0xf0],
  star: [0xff, 0xd0, 0x40],
};
for (const [kind, colour] of Object.entries(LENSES)) {
  texture(blockOutput, `nav_light_${kind}`, (x, y) => {
    const r = Math.hypot(x - 7.5, y - 7.5);
    if (r < 2.5) return [255, 255, 255];
    const fade = Math.min(1, r / 9);
    return colour.map((c) => clamp(Math.round(c * (1 - 0.35 * fade))));
  }, 2);
}
const miscOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/misc");
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
  // The same flare across a running engine's nozzle, behind its flame: seen far bigger, so drawn smoothed.
  writeFileSync(resolve(miscOutput, "exhaust_glow.png"), encodePng(pixels));
  writeFileSync(resolve(miscOutput, "exhaust_glow.png.mcmeta"), JSON.stringify({ texture: { blur: true, clamp: true } }, null, 2) + "\n");
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
// The flame behind a running engine, as light its renderer adds over what is behind it: a white-hot core from the
// nozzle, with the faint bright rings a jet's exhaust shows along it, inside a cyan flame going blue as it swells a
// little, narrows and fades to nothing at its tip. Across the picture is across the flame, down it away from the
// nozzle; it is drawn smoothed rather than in pixels, and not repeated past its edges.
{
  const WIDTH = 32;
  const LENGTH = 64;
  // How much of the flame's length its core reaches, and how many bright rings the core shows along that.
  const CORE = 0.5;
  const RINGS = 4;
  const FLAME = [60, 150, 255];
  const HOT = [120, 230, 255];
  const pixels = [];
  for (let y = 0; y < LENGTH; y++) {
    const along = (y + 0.5) / LENGTH;
    // The flame's half-width as a share of the picture's: the nozzle's, swelling a little, then narrowing round to
    // its tip. The core narrows to a point where it ends.
    const half = (0.75 + 1.2 * along) * (1 - along);
    const coreLeft = Math.max(0, 1 - along / CORE);
    const rings = 0.7 + 0.3 * Math.cos((2 * Math.PI * RINGS * along) / CORE);
    const row = [];
    for (let x = 0; x < WIDTH; x++) {
      const across = Math.abs(((x + 0.5) / WIDTH) * 2 - 1);
      const flame = Math.max(0, 1 - (across / half) ** 2) * (1 - along) ** 1.5;
      const core = coreLeft > 0 ? Math.max(0, 1 - (across / (0.35 * coreLeft)) ** 2) * coreLeft * rings : 0;
      row.push([0, 1, 2].map((i) => clamp(Math.round(FLAME[i] * flame + HOT[i] * flame ** 2 + 255 * core))));
    }
    pixels.push(row);
  }
  writeFileSync(resolve(miscOutput, "exhaust_flame.png"), encodePng(pixels));
  writeFileSync(resolve(miscOutput, "exhaust_flame.png.mcmeta"), JSON.stringify({ texture: { blur: true, clamp: true } }, null, 2) + "\n");
}

// A contrail puff: a soft white round blob, see-through toward its edge.
{
  const particleOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/particle");
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
// carries a gold star for the fittings. The zeppelin's shows it from the side, the festive one's drawn in red. The
// control tower's shows the tower from the side: its shaft under the wider cab, the cab's windows a band of the
// paper, and the mast on its roof.
function blueprint(name, star, drawn = airlinerFromAbove, ink = INK) {
  texture(itemOutput, name, (x, y) => {
    if (x < 1 || x > 14 || y < 2 || y > 13) return CLEAR;
    if (star && Math.abs(x - 12.5) + Math.abs(y - 4.5) <= 1.5) return GOLD;
    if (drawn(x, y)) return ink;
    if (x === 1 || x === 14 || y === 2 || y === 13) return PAPER_LINE;
    if (x % 4 === 0 || y % 4 === 0) return PAPER_LINE;
    return PAPER;
  }, 2);
}
// Seen from above, nose up: swept wings and tailplane drift back as they reach out.
function airlinerFromAbove(x, y) {
  const out = Math.abs(x - 7.5);
  const fuselage = out <= 0.5 && y >= 3 && y <= 12;
  const wings = out <= 6.5 && y >= 5.5 + out * 0.35 && y <= 7 + out * 0.35;
  const tail = out <= 2.5 && y >= 10.5 + out * 0.35 && y <= 11.5 + out * 0.35;
  return fuselage || wings || tail;
}
// A runway from above, running up the sheet: its edges, its dashed centre line and the piano keys at either end.
function runwayFromAbove(x, y) {
  if (x < 4 || x > 11 || y < 3 || y > 12) return false;
  const keys = (y === 3 || y === 12) && x % 2 === 0;
  return x === 4 || x === 11 || keys || (x === 7 && y % 3 !== 0);
}
// A zeppelin from the side, nose to the left: its long cigar of a hull, the fins at its tail and the car under its bow.
function zeppelinFromTheSide(x, y) {
  const along = (x - 7) / 6;
  const taper = x > 8 ? 1 - (x - 8) / 9 : 1;
  const hull = along * along < 1 && Math.abs(y - 7) <= 2.6 * Math.sqrt(1 - along * along) * taper;
  const fins = x >= 11 && x <= 13 && Math.abs(y - 7) <= (x - 10) * 1.2;
  const car = y === 10 && x >= 3 && x <= 5;
  return hull || fins || car;
}
// A half-size zeppelin from the side: the same, half as long and a little slimmer, in the middle of the sheet, so a
// blueprint shows its size by its picture.
function halfZeppelinFromTheSide(x, y) {
  const along = (x - 7.5) / 3.5;
  const taper = x > 8 ? 1 - (x - 8) / 6 : 1;
  const hull = along * along < 1 && Math.abs(y - 7) <= 1.6 * Math.sqrt(1 - along * along) * taper;
  const fins = x >= 9 && x <= 10 && Math.abs(y - 7) <= (x - 8) * 1.1;
  const car = y === 9 && x >= 5 && x <= 6;
  return hull || fins || car;
}
// The rocket standing, nose up: a slim body under a pointed nose, a porthole, and a fin out either side at its foot.
function rocketFromTheSide(x, y) {
  const body = y >= 5 && y <= 11 && x >= 6 && x <= 9 && !(y === 7 && x >= 7 && x <= 8);
  const nose = y >= 2 && y <= 4 && Math.abs(x - 7.5) <= (y - 1) * 0.6;
  const fins = y >= 9 && y <= 13 && (x === 5 || x === 10 || (y >= 11 && (x === 4 || x === 11)));
  return body || nose || fins;
}
// A launch pad from the side: a flat deck, a small rocket standing in its middle and the service tower beside her.
function padFromTheSide(x, y) {
  const deck = y === 12 && x >= 2 && x <= 13;
  const rocket = x >= 5 && x <= 6 && y >= 5 && y <= 10;
  const nose = y === 4 && x >= 5 && x <= 6;
  const fins = y === 11 && (x === 4 || x === 7);
  const tower = x === 10 && y >= 3 && y <= 11;
  const arm = y === 6 && x >= 8 && x <= 9;
  return deck || rocket || nose || fins || tower || arm;
}
// The freighter from above, prongs up: her round disc, the loading gap between her two prongs, her cockpit's tube and
// pod out on her right, and her top turret's ring in the middle.
function freighterFromAbove(x, y) {
  const r = Math.hypot(x - 7.5, y - 8.5);
  const disc = r <= 5 && !(r <= 1 && r > 0.4) && !(Math.abs(x - 7.5) <= 0.5 && y <= 6);
  const prongs = y >= 2 && y <= 5 && ((x >= 5 && x <= 6) || (x >= 9 && x <= 10));
  const cockpit = x >= 11 && x <= 12 && y >= 3 && y <= 6;
  return disc || prongs || cockpit;
}
function towerFromTheSide(x, y) {
  const mast = x === 10 && y === 3;
  const roof = y === 4 && x >= 4 && x <= 11;
  const cab = y >= 5 && y <= 7 && x >= 5 && x <= 10 && !(y === 6 && x >= 6 && x <= 9);
  const shaft = y >= 8 && y <= 12 && x >= 6 && x <= 9;
  return mast || roof || cab || shaft;
}
blueprint("airliner_blueprint", false);
blueprint("furnished_airliner_blueprint", true);
blueprint("zeppelin_blueprint", false, zeppelinFromTheSide);
blueprint("furnished_zeppelin_blueprint", true, zeppelinFromTheSide);
blueprint("festive_zeppelin_blueprint", true, zeppelinFromTheSide, [0xe0, 0x2a, 0x32]);
blueprint("half_zeppelin_blueprint", false, halfZeppelinFromTheSide);
blueprint("half_furnished_zeppelin_blueprint", true, halfZeppelinFromTheSide);
blueprint("half_festive_zeppelin_blueprint", true, halfZeppelinFromTheSide, [0xe0, 0x2a, 0x32]);
blueprint("rocket_blueprint", false, rocketFromTheSide);
blueprint("launch_pad_blueprint", false, padFromTheSide);
blueprint("freighter_blueprint", false, freighterFromAbove);
blueprint("control_tower_blueprint", false, towerFromTheSide);
blueprint("runway_blueprint", false, runwayFromAbove);

// Skydiving. The jump light: a lens dark, red or green, brighter in the middle. The jump switch: a dark panel with a
// red and a green lamp, the one in use lit. The rack: a frame holding packed parachutes under a parachute sign, and the
// sign big on its sides, so it reads as the parachutes' store from any side: a red and white canopy, its cords running
// down to a jumper hanging under it. The landing target: red and
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
  const CANOPY_RED = [0xd8, 0x2a, 0x22];
  const CANOPY_WHITE = [0xf4, 0xf4, 0xf0];
  const JUMPER = [0x22, 0x25, 0x2a];
  // The parachute sign round a middle (cx, cy), the canopy's half width r: the canopy a dome in red and white panels,
  // two cords from its rim down to the jumper under its middle; null off it.
  const parachuteSign = (x, y, cx, cy, r) => {
    const dome = (y - cy) / (r * 0.75);
    if (y <= cy && Math.hypot((x - cx) / r, dome) <= 1) return Math.floor((x - cx + r) / (r / 2.5)) % 2 === 0 ? CANOPY_RED : CANOPY_WHITE;
    const drop = r * 0.9;
    if (y > cy && y < cy + drop) {
      const along = (y - cy) / drop;
      const cord = r * (1 - along);
      if (Math.abs(Math.abs(x - cx) - cord) < 0.5) return JUMPER;
    }
    if (Math.abs(x - cx) <= 0.5 && y >= cy + drop - 0.5 && y <= cy + drop + 1.5) return JUMPER;
    return null;
  };
  texture(blockOutput, "parachute_rack_front", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return STEEL_DARK;
    if (y <= 6) return parachuteSign(x, y, 7.5, 3, 4) ?? STEEL;
    if (y === 7) return STEEL_DARK;
    const slot = Math.floor((x - 1) / 5);
    if ((x - 1) % 5 === 4) return STEEL_DARK;
    return (y + slot) % 4 === 0 ? OLIVE_DARK : OLIVE;
  });
  texture(blockOutput, "parachute_rack_side", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return STEEL_DARK;
    return parachuteSign(x, y, 7.5, 6.5, 5.5) ?? STEEL;
  });
  texture(blockOutput, "parachute_rack_top", (x, y) => (x === 0 || x === 15 || y === 0 || y === 15 ? STEEL_DARK : STEEL));
  // The paper plane box: a crate of pale boards, a white paper plane stencilled on its front and sides, its open top
  // full of folded planes.
  const BOARD = [0xc9, 0xa4, 0x6c];
  const BOARD_SEAM = [0x8e, 0x6b, 0x3d];
  const PAPER = [0xf6, 0xf6, 0xf0];
  const PAPER_FOLD = [0xc4, 0xc9, 0xcf];
  const boards = (x, y) => (x === 0 || x === 15 || y === 0 || y === 15 || y % 5 === 0 ? BOARD_SEAM : BOARD);
  // A paper plane from above, its nose up and to the right: two wings folded either side of a keel, the far wing
  // in the fold's shade.
  const paperPlane = (x, y, cx, cy, size) => {
    const along = ((x - cx) + (cy - y)) / Math.SQRT2 / size;
    const out = ((x - cx) - (cy - y)) / Math.SQRT2 / size;
    if (along < -1 || along > 1 || Math.abs(out) > (1 - along) * 0.45) return null;
    return out > 0 ? PAPER_FOLD : PAPER;
  };
  texture(blockOutput, "paper_plane_box_front", (x, y) => paperPlane(x, y, 7.5, 7.5, 6) ?? boards(x, y));
  texture(blockOutput, "paper_plane_box_side", (x, y) => paperPlane(x, y, 7.5, 7.5, 5) ?? boards(x, y));
  texture(blockOutput, "paper_plane_box_top", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return BOARD_SEAM;
    if (x === 1 || x === 14 || y === 1 || y === 14) return BOARD;
    return paperPlane(x, y, 5, 10, 4) ?? paperPlane(x, y, 10.5, 5.5, 4) ?? (y % 3 === 0 ? PAPER_FOLD : PAPER);
  });
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
  const entityOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/entity");
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

// The control tower. The transponder's panel, read from its face: at the back the black glass of the little screen
// its picture is drawn on (see TransponderRenderer), at the front its four-digit code in green. The radar screen's
// monitor: its face a dark bezel round the black glass the scope is drawn on (see TowerRadarRenderer), its casing
// dark grey.
{
  const SCREEN = [0x08, 0x0c, 0x10];
  const CODE = [0x30, 0xc0, 0x40];
  texture(blockOutput, "transponder_top", (x, y) => {
    if (x === 0 || y === 0 || x === 15 || y === 15) return PANEL_EDGE;
    if (x >= 4 && x <= 11 && y >= 2 && y <= 9) return SCREEN;
    if (y >= 11 && y <= 12 && x >= 3 && x <= 12) return (x - 3) % 3 === 2 ? PANEL_DARK : CODE;
    return PANEL_DARK;
  }, 2);
  texture(blockOutput, "tower_radar_screen", (x, y) => (x >= 1 && x <= 14 && y >= 2 && y <= 12 ? SCREEN : PANEL_DARK), 2);
  texture(blockOutput, "tower_radar_casing", (x, y) => (x === 0 || y === 0 ? PANEL_EDGE : PANEL_DARK), 2);
}

// The flight strip board in the hand: a wide dark screen, a blue band along its top, and under it three strips, each
// with a bar of its transponder's colour at its left and a line of white writing.
{
  const BEZEL = [0x3a, 0x3f, 0x47];
  const BARS = [[0xf2, 0x8c, 0x28], [0xf2, 0xf2, 0xf2], [0x46, 0xc8, 0xf0]];
  texture(itemOutput, "flight_strip_board", (x, y) => {
    if (y < 3 || y > 13) return CLEAR;
    if (x === 0 || x === 15 || y === 3 || y === 13) return BEZEL;
    if (y === 4) return [0x10, 0x25, 0x4a];
    const strip = Math.floor((y - 5) / 3);
    if ((y - 5) % 3 === 2) return [0x0a, 0x0e, 0x14];
    if (x === 1 || x === 2) return BARS[strip];
    return x >= 4 && x <= 6 + strip * 2 ? INK : [0x1a, 0x22, 0x30];
  }, 0);
}

// The wind sock. The sock's cloth, laid out flat along its length (x from the mouth to the tail, y round it): five
// bands, orange at the mouth and the tail and white between, as an airfield's are, a faint weave across it. Its pole:
// pale galvanised steel, shaded down one side. In the hand: the pole with the sock flying out from its top.
{
  const SOCK_ORANGE = [0xf0, 0x6a, 0x1e];
  const SOCK_WHITE = [0xf2, 0xf2, 0xee];
  const band = (x) => Math.min(4, Math.floor(x / 3.2)) % 2 === 0 ? SOCK_ORANGE : SOCK_WHITE;
  texture(blockOutput, "wind_sock", (x, y) => {
    const [r, g, b] = band(x);
    return y % 4 === 3 ? [r - 14, g - 14, b - 14] : [r, g, b];
  }, 3);
  texture(blockOutput, "wind_sock_pole", (x) => (x < 5 ? CHROME : x < 11 ? STEEL : STEEL_DARK), 2);
  texture(itemOutput, "wind_sock", (x, y) => {
    if (x >= 2 && x <= 3 && y >= 2) return x === 2 ? CHROME : STEEL_DARK;
    const half = 2.5 - (x - 4) * 0.15;
    if (x >= 4 && x <= 14 && Math.abs(y - 4.5) <= half) return band(Math.round((x - 4) * 1.6));
    return CLEAR;
  }, 0);
}

// The motion sensor over an automatic door: the face of a slim white housing (the model shows only x 3-12, y 6-9 of
// it), seamed at its ends, a band of dark smoked lens across it with a glint, and beside the lens a little lamp, dark,
// or lit red while it sees someone. Its sides and top are plain white.
{
  const HOUSING = [0xee, 0xf0, 0xf2];
  const HOUSING_SEAM = [0xc6, 0xcb, 0xd1];
  const LENS = [0x1a, 0x1d, 0x22];
  const sensor = (lit) => (x, y) => {
    if (x === 3 || x === 12) return HOUSING_SEAM;
    if (y < 7 || y > 8) return HOUSING;
    if (x >= 4 && x <= 9) return x === 5 && y === 7 ? [0x8a, 0x90, 0x98] : LENS;
    if (x === 11) return lit ? [0xff, 0x30, 0x24] : [0x50, 0x18, 0x14];
    return HOUSING;
  };
  texture(blockOutput, "motion_sensor_front", sensor(false), 2);
  texture(blockOutput, "motion_sensor_front_on", sensor(true), 2);
  texture(blockOutput, "motion_sensor_side", () => HOUSING, 2);
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
  // The strips' and the pendants' glow for a door open in flight: alarm red.
  texture(blockOutput, "light_strip_alarm", (x, y) => {
    const glow = 1 - Math.abs(y - 7.5) / 8;
    return [clamp(Math.round(205 + 50 * glow)), clamp(Math.round(24 + 50 * glow)), clamp(Math.round(20 + 36 * glow))];
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

  // The bay's screen (see DropBayScreen): vanilla's dispenser panel, light grey under a bevelled edge, the bay's three
  // by three slots in the middle and the player's inventory under them. Either side of the bay's slots a picture is
  // sunk into the panel, of what the bay does, for players who cannot read yet: on the left the airliner's belly
  // overhead, its bay doors swung open in their hazard stripes and a care package dropping out, an arrow pointing on
  // down; on the right the package floating down under its parachute to the grass.
  const guiContainer = resolve(ROOT, "src/main/resources/assets/skyliners/textures/gui/container");
  mkdirSync(guiContainer, { recursive: true });
  const WIDTH = 176;
  const HEIGHT = 166;
  const SLOT_SIZE = 18;
  const PANEL = [0xc6, 0xc6, 0xc6];
  const LIT = [0xff, 0xff, 0xff];
  const SHADED = [0x55, 0x55, 0x55];
  const OUTLINE = [0x00, 0x00, 0x00];
  const SUNK = [0x8b, 0x8b, 0x8b];
  const SUNK_SHADOW = [0x37, 0x37, 0x37];
  const SKY_HIGH = [0x5a, 0xa6, 0xe8];
  const SKY_LOW = [0xb8, 0xdc, 0xf8];
  const BELLY_SHADE = [0xa8, 0xb0, 0xba];
  const SHAFT = [0x0c, 0x0d, 0x0f];
  const STREAK = [0xf4, 0xfa, 0xff];
  const ARROW = [0xf0, 0xc0, 0x20];
  const ARROW_EDGE = [0x6a, 0x4a, 0x08];
  // The care package's oak (see its crate below), and the parachute's red and white panels and dark cords.
  const OAK = [0xa8, 0x82, 0x4e];
  const OAK_DARK = [0x7a, 0x5a, 0x32];
  const OAK_SLAT = [0x8a, 0x66, 0x3a];
  const CANOPY_RED = [0xe0, 0x30, 0x28];
  const CANOPY_WHITE = [0xf2, 0xf2, 0xf2];
  const CORD = [0x30, 0x30, 0x30];
  const GRASS = [0x5c, 0xa0, 0x3c];
  const GRASS_LIGHT = [0x7c, 0xc0, 0x50];
  const DIRT = [0x86, 0x60, 0x3a];
  const DIRT_DARK = [0x6a, 0x4c, 0x28];
  const mix = (from, to, t) => from.map((c, i) => Math.round(c + (to[i] - c) * t));
  // Whether a point is within reach of the segment from one point to another.
  const nearSegment = (u, v, [x0, y0], [x1, y1], reach) => {
    const dx = x1 - x0;
    const dy = y1 - y0;
    const t = Math.max(0, Math.min(1, ((u - x0) * dx + (v - y0) * dy) / (dx * dx + dy * dy)));
    return Math.hypot(u - x0 - t * dx, v - y0 - t * dy) <= reach;
  };
  // The panel's edge: an outline round its rounded corners, lit along its top and left, shaded along its bottom and
  // right; null inside it.
  const edge = (x, y) => {
    const right = WIDTH - 1 - x;
    const bottom = HEIGHT - 1 - y;
    if (x + y < 2 || right + bottom < 2 || right + y < 3 || x + bottom < 3) return CLEAR;
    if (x === 0 || y === 0 || right === 0 || bottom === 0 || x + y === 2 || right + bottom === 2 || right + y === 3 || x + bottom === 3) {
      return OUTLINE;
    }
    if ((x <= 2 || y <= 2 || (x === 3 && y === 3)) && right >= 3 && bottom >= 3) return LIT;
    if ((right <= 2 || bottom <= 2 || (right === 3 && bottom === 3)) && x >= 3 && y >= 3) return SHADED;
    return null;
  };
  // A box sunk into the panel at (left, top), across by down: shadowed along its top and left, lit along its bottom
  // and right, a corner of each where they meet; inside, what paint gives, from the box's inner corner. Null outside it.
  const sunk = (left, top, across, down, paint) => (x, y) => {
    const u = x - left;
    const v = y - top;
    if (u < 0 || v < 0 || u >= across || v >= down) return null;
    if ((u === across - 1 && v === 0) || (u === 0 && v === down - 1)) return SUNK;
    if (u === 0 || v === 0) return SUNK_SHADOW;
    if (u === across - 1 || v === down - 1) return LIT;
    return paint(u - 1, v - 1);
  };
  const slot = (left, top) => sunk(left, top, SLOT_SIZE, SLOT_SIZE, () => SUNK);
  // The pictures' sky, darker overhead; and the care package's crate, its corner at (left, top): slats in a frame.
  const PICTURE_ACROSS = 50;
  const PICTURE_DOWN = 54;
  const sky = (v) => mix(SKY_HIGH, SKY_LOW, v / (PICTURE_DOWN - 3));
  const crate = (u, v, left, top) => {
    const a = u - left;
    const b = v - top;
    if (a < 0 || b < 0 || a > 9 || b > 8) return null;
    if (a === 0 || b === 0 || a === 9 || b === 8) return OAK_DARK;
    return a % 3 === 0 ? OAK_SLAT : OAK;
  };
  // The arrow under the falling package, pointing down: a shaft, then a head narrowing to its tip.
  const inArrow = (u, v) => v >= 38 && v <= 49 && Math.abs(u - 23.5) <= (v <= 42 ? 2 : 49.5 - v);
  const dropping = (u, v) => {
    // The belly, side on: white skin with its windows over the blue cheatline, shaded underneath, the bay open in it.
    if (v <= 11) {
      if (v >= 10 && u >= 14 && u <= 33) return SHAFT;
      if (v === 11) return BELLY_SHADE;
      if (v === 10) return SEAM;
      if (v === 7) return BLUE;
      return v >= 3 && v <= 5 && u % 6 >= 2 && u % 6 <= 3 ? GLASS.slice(0, 3) : WHITE;
    }
    // The doors, hanging open from either side of the bay.
    if (nearSegment(u, v, [14, 12], [11, 21], 1) || nearSegment(u, v, [33, 12], [36, 21], 1)) return HAZARD(u, v);
    // Streaks of its fall between the doors, the package under them, and an arrow on down.
    if ((u === 20 || u === 24 || u === 27) && v >= 17 + (u % 3) && v <= 24) return STREAK;
    const box = crate(u, v, 19, 26);
    if (box) return box;
    if (inArrow(u, v)) return ARROW;
    if ([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([du, dv]) => inArrow(u + du, v + dv))) return ARROW_EDGE;
    return sky(v);
  };
  const floating = (u, v) => {
    // The canopy, red and white panels, its rim shaded; its cords down to the package; the grass it comes down to.
    const inCanopy = v <= 15 && Math.hypot((u - 23.5) / 16, (v - 15.5) / 11) <= 1;
    if (inCanopy) {
      const panel = Math.floor((u - 7.5) / 4) % 2 === 0 ? CANOPY_RED : CANOPY_WHITE;
      return v === 15 ? mix(panel, OUTLINE, 0.25) : panel;
    }
    const cords = [[[9, 16], [19, 29]], [[17, 16], [21, 29]], [[30, 16], [26, 29]], [[38, 16], [28, 29]]];
    if (v < 29 && cords.some(([from, to]) => nearSegment(u, v, from, to, 0.5))) return CORD;
    const box = crate(u, v, 19, 29);
    if (box) return box;
    if (v >= 46) return v <= 47 ? ((u * 7) % 5 === 0 ? GRASS_LIGHT : GRASS) : (u * 3 + v) % 7 === 0 ? DIRT_DARK : DIRT;
    if (v === 45 && u % 5 === 2) return GRASS_LIGHT;
    return sky(v);
  };
  const GRID = { left: 61, top: 16 };
  const INVENTORY = { left: 7, top: 83, hotbar: 141 };
  const parts = [
    sunk(7, 16, PICTURE_ACROSS, PICTURE_DOWN, dropping),
    sunk(WIDTH - 7 - PICTURE_ACROSS, 16, PICTURE_ACROSS, PICTURE_DOWN, floating),
    ...Array.from({ length: 9 }, (_, i) => slot(GRID.left + (i % 3) * SLOT_SIZE, GRID.top + Math.floor(i / 3) * SLOT_SIZE)),
    ...Array.from({ length: 27 }, (_, i) => slot(INVENTORY.left + (i % 9) * SLOT_SIZE, INVENTORY.top + Math.floor(i / 9) * SLOT_SIZE)),
    ...Array.from({ length: 9 }, (_, i) => slot(INVENTORY.left + i * SLOT_SIZE, INVENTORY.hotbar)),
  ];
  texture(guiContainer, "drop_bay", (x, y) => {
    const rim = edge(x, y);
    if (rim) return rim;
    for (const part of parts) {
      const colour = part(x, y);
      if (colour) return colour;
    }
    return PANEL;
  }, 0, WIDTH, HEIGHT);
}

// Cabin service. The galley, its trolley bay (see the galley units below): brushed steel; over the bay its control
// panel, a dark strip of buttons, green and white, and an amber readout of the trolleys stocked. The trolley:
// aluminium with a red stripe and drawer lines, its ends a grid of drawers with handles, its top a grey tray. The
// meals on their trays, a bag of pretzels, a paper cup of coffee, and an ice cream cone: a waffle cone, a chocolate
// scoop and a strawberry one on it and a cherry on top, outlined dark so it reads on any background.
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
  {
    const WAFFLE = [0xd8, 0xa0, 0x58];
    const WAFFLE_DARK = [0xa8, 0x70, 0x34];
    const CHOCOLATE = [0x7a, 0x48, 0x28];
    const STRAWBERRY = [0xf4, 0x9c, 0xb8];
    const STRAWBERRY_LIGHT = [0xfc, 0xc8, 0xd8];
    const CHERRY = [0xd0, 0x18, 0x28];
    const OUTLINE = [0x2a, 0x1c, 0x16];
    const iceCream = (x, y) => {
      if (y >= 9 && y <= 15 && Math.abs(x - 7.5) <= (15 - y) * 0.55 + 0.5) return (x + y) % 3 === 0 || (x - y + 30) % 3 === 0 ? WAFFLE_DARK : WAFFLE;
      if (Math.hypot(x - 7.5, y - 8) <= 3.6 && y <= 9) return CHOCOLATE;
      if (Math.hypot(x - 7.5, y - 4.5) <= 3) return x <= 6 && y <= 4 ? STRAWBERRY_LIGHT : STRAWBERRY;
      if (Math.hypot(x - 8, y - 1) <= 1.2) return CHERRY;
      return null;
    };
    texture(itemOutput, "ice_cream", (x, y) => {
      const inside = iceCream(x, y);
      if (inside) return inside;
      return [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => iceCream(x + dx, y + dy)) ? OUTLINE : CLEAR;
    }, 0);
  }
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

  // The lift panel's glass, on the part of the texture its plate shows (x 3 to 12, y 1 to 14), for when it is too far
  // off to be drawn live and in the hand: black in the bezel, an amber figure in the indicator across the top with an
  // amber line under it, and under that two rows of green floors, the lower one lit amber.
  const AMBER = [0xff, 0xb0, 0x20];
  const LED_BLACK = [0x04, 0x06, 0x04];
  texture(blockOutput, "lift_panel", (x, y) => {
    if (x <= 3 || x >= 12 || y <= 1 || y >= 14) return BEZEL;
    if (y === 6) return AMBER;
    if (y >= 2 && y <= 5) return (x === 7 || x === 8) && y >= 3 && y <= 4 ? AMBER : [0x0a, 0x0d, 0x07];
    const text = x >= 5 && x <= 10 && x !== 6;
    if (y === 8) return text ? [0x3c, 0xf0, 0x7a] : LED_BLACK;
    if (y === 10) return text ? AMBER : [0x3a, 0x28, 0x04];
    return LED_BLACK;
  }, 1);
}

// The control panels' pictures, in the glass cockpit's colours: every setting and button has one, so the panels work
// for players who cannot read yet. The panel's bezel: brushed dark metal, each row of it a little lighter or darker.
// Its close button: an X, white so it can be drawn in any colour. The autopilot's: the airliner from above for its
// title, and from the side, white so it can be drawn in any colour, to fly in the sky the altitude is set on; a compass
// for the heading (cyan, its needle red to the north); a green hill with an up-and-down arrow for the altitude; a
// speedometer for the speed (green, amber and red, the needle well round). For the way it flies, like road signs, the
// path flown from the nose of a small airliner: straight on in cyan, or round a half circle to the left or right in
// magenta, gently or tightly, an arrowhead at its end. Then the buttons: a green
// airliner climbing off a runway to take off, or flying on level to engage in the air; an amber one settling onto
// the runway to land; a green tick to apply; a red power symbol to switch the autopilot off. For the pilot's HUD, the
// phases those buttons leave out: a green airliner climbing or descending beside a white arrow up or down, and an
// amber one braking along the runway toward a red stop bar; and the height above the ground, an airliner seen from
// the side over the grass with a cyan arrow between them. To hop a stuck airliner free: a cyan airliner seen from the
// side, lifted off the grass on two white arrows, puffs of the jets' exhaust under them.
{
  const guiOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/gui");
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
  icon(autopilotOutput, "airliner_side", (x, y) => {
    const fin = y >= 2 && y <= 6 && x >= 1 && x <= y - 1;
    const fuselage = y >= 7 && y <= 9 && x >= 1 && x <= (y === 8 ? 14 : 13);
    const wing = y === 10 && x >= 5 && x <= 9;
    if (fin || fuselage || wing) return y === 9 || wing ? PALE : INK;
    return null;
  });
  // The way it flies: the path from the nose of a small airliner at the bottom, an arrowhead at its end. Straight on;
  // or round a half circle of a radius, to the right, or mirrored to the left. The airliner is drawn pixel by pixel,
  // too small for the shape the larger pictures share to hold together; so is the zeppelin, a cigar with its tailplanes,
  // in its own set of these.
  const SMALL_AIRLINER = ["..##..", ".####.", "######", "..##..", ".####."];
  const SMALL_ZEPPELIN = ["..##..", ".####.", ".####.", "#.##.#", "..##.."];
  const SMALL_ROCKET = ["..##..", ".####.", ".####.", ".####.", "#.##.#"];
  // The freighter from above, prongs up: her two prongs, her disc and her cockpit pod out on her right.
  const SMALL_FREIGHTER = [".#.#..", ".#.##.", "####..", "####..", ".##..."];
  const path = (x, y, points) => points.slice(1).some((point, i) => onSegment(x, y, points[i], point, 1.7));
  const TURN_FROM = 3.5;
  const TURN_MIDDLE = 6.5;
  const steering = (prefix, sprite) => {
    const small = (x, y, cx) => sprite[y - 11]?.[x - Math.round(cx - 3)] === "#";
    icon(autopilotOutput, `${prefix}straight_on`, (x, y) => {
      if (small(x, y, 7.5)) return INK;
      return path(x, y, [[7.5, 10], [7.5, 4]]) || inTriangle(x, y, [4.6, 4.8], [10.4, 4.8], [7.5, 0.6]) ? CYAN : null;
    });
    for (const [size, radius] of [["gentle", 4], ["tight", 2.5]]) {
      for (const [side, mirror] of [["left", true], ["right", false]]) {
        const end = TURN_FROM + 2 * radius;
        const arc = Array.from({ length: 13 }, (_, k) => {
          const angle = Math.PI - (Math.PI * k) / 12;
          return [TURN_FROM + radius + radius * Math.cos(angle), TURN_MIDDLE - radius * Math.sin(angle)];
        });
        const points = [[TURN_FROM, 10], [TURN_FROM, TURN_MIDDLE], ...arc, [end, TURN_MIDDLE + 1.5]];
        icon(autopilotOutput, `${prefix}turn_${size}_${side}`, (px, y) => {
          const x = mirror ? 15 - px : px;
          if (small(x, y, TURN_FROM)) return INK;
          const head = inTriangle(x, y, [end - 2.6, TURN_MIDDLE + 1.4], [end + 2.6, TURN_MIDDLE + 1.4], [end, TURN_MIDDLE + 4.6]);
          return path(x, y, points) || head ? MAGENTA : null;
        });
      }
    }
  };
  steering("", SMALL_AIRLINER);
  steering("zeppelin_", SMALL_ZEPPELIN);
  steering("rocket_", SMALL_ROCKET);
  steering("freighter_", SMALL_FREIGHTER);
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
  // Under a craft at the top, how high it is: an arrow up to it and one down to the ground, grass over earth.
  const EARTH = [0x7a, 0x4e, 0x26];
  const heightOverGround = (x, y) => {
    if (arrow(x, y, 7.5, 6, 9, true) || arrow(x, y, 7.5, 10, 12, false)) return CYAN;
    if (y === 13) return GREEN;
    return y >= 14 ? EARTH : null;
  };
  icon(autopilotOutput, "ground_height", (x, y) => {
    const fin = y <= 2 && x >= 1 && x <= 1 + y;
    const fuselage = (y === 3 || y === 4) && x >= 1 && x <= 13 - (y === 3 ? 1 : 0);
    const nose = y === 4 && x === 14;
    return fin || fuselage || nose ? INK : heightOverGround(x, y);
  });
  // Fly to a pin: a map pin, a round red head with a white dot in it on a point, over a magenta ring round its foot,
  // the circle flown round it.
  icon(autopilotOutput, "destination", (x, y) => {
    const head = Math.hypot(x - 7.5, y - 5.5);
    if (head <= 1.3) return INK;
    if (head <= 4.2) return x < 8 && y < 6 ? [0xf0, 0x50, 0x44] : [0xd0, 0x30, 0x28];
    if (y >= 9 && y <= 12 && Math.abs(x - 7.5) <= (12 - y) * 0.9 + 0.2) return [0xb0, 0x24, 0x1e];
    const ring = Math.hypot((x - 7.5) / 7, (y - 13) / 2.2);
    return ring >= 0.75 && ring <= 1.05 ? MAGENTA : null;
  });
  // Land at the pin: the amber airliner settling onto the runway, as Land has it but smaller, by a small red map pin
  // standing at the runway's far end.
  const smallPin = (x, y) => {
    const head = Math.hypot(x - 12, y - 3.5);
    if (head <= 0.9) return INK;
    if (head <= 2.6) return x < 12 && y < 4 ? [0xf0, 0x50, 0x44] : [0xd0, 0x30, 0x28];
    return y >= 6 && y <= 9 && Math.abs(x - 12) <= (9 - y) * 0.6 + 0.2 ? [0xb0, 0x24, 0x1e] : null;
  };
  icon(autopilotOutput, "land_at_pin", (x, y) => smallPin(x, y) ?? (airliner(x, y, 5.5, 7, 135, 0.7) ? AMBER : runway(x, y, 1, 15)));
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

  // The zeppelin's autopilot's pictures: the airliner's, the zeppelin in the airliner's place. Its hull, as long as a
  // picture is wide, blunt at the nose and running to a point at the tail, and a fin out from its tail either side,
  // swept back to a tip short of that point, so that it reads as an airship and not a rocket: whether a point so far
  // along it from its middle (forward positive) and so far out from its axis lies within them.
  const ZEPPELIN_LENGTH = 7.2;
  const zeppelinShape = (along, out, breadth, span) => {
    if (Math.abs(along) > ZEPPELIN_LENGTH) return false;
    const t = Math.abs(along) / ZEPPELIN_LENGTH;
    const hull = along >= 0 ? t ** 3 + (out / breadth) ** 2 <= 1 : out <= breadth * Math.sqrt(1 - t ** 1.6);
    const fin = out <= span && along >= -6.2 + (out - 1.5) * 0.15 && along <= -3.8 - (out - 1.5) * 0.75;
    return hull || fin;
  };
  // From above, its nose on a bearing (degrees clockwise from up), its middle at (cx, cy): a little broader than from
  // the side, to read as a body seen whole, its fins the horizontal ones.
  const zeppelin = (x, y, cx, cy, bearing, size = 1) => {
    const turn = (bearing * Math.PI) / 180;
    const dx = (x - cx) / size;
    const dy = (y - cy) / size;
    const along = dx * Math.sin(turn) - dy * Math.cos(turn);
    const out = Math.abs(dx * Math.cos(turn) + dy * Math.sin(turn));
    return zeppelinShape(along, out, 3, 4.6);
  };
  // From the side, its nose to the right and raised by a tilt (degrees), its middle at (cx, cy): the hull, a fin above
  // and below its tail and the control car under its bow.
  const zeppelinSide = (x, y, cx, cy, tilt, size = 1) => {
    const turn = (tilt * Math.PI) / 180;
    const dx = (x - cx) / size;
    const dy = (y - cy) / size;
    const along = dx * Math.cos(turn) - dy * Math.sin(turn);
    const up = -dx * Math.sin(turn) - dy * Math.cos(turn);
    const car = along >= 2 && along <= 4 && up >= -3.2 && up <= -2;
    return zeppelinShape(along, Math.abs(up), 2.4, 3.8) || car;
  };
  // The ground a zeppelin rises from and settles onto anywhere, needing no runway: grass over earth along the bottom.
  const ground = (x, y, from, to) => (x < from || x > to || y < 14 ? null : y === 14 ? GREEN_DARK : EARTH);
  icon(autopilotOutput, "zeppelin", (x, y) => (zeppelin(x, y, 7.5, 7.5, 0) ? INK : null));
  icon(autopilotOutput, "zeppelin_side", (x, y) => (zeppelinSide(x, y, 7.5, 7.5, 0) ? (y >= 9 ? PALE : INK) : null));
  // Take off: the green zeppelin rising off the ground, an arrow up under it. Land: the amber one settling, the arrow
  // down.
  icon(autopilotOutput, "zeppelin_take_off", (x, y) => {
    if (zeppelinSide(x, y, 8, 5, 10, 0.85)) return GREEN;
    return arrow(x, y, 7.5, 9, 13, true) ? INK : ground(x, y, 0, 15);
  });
  icon(autopilotOutput, "zeppelin_land", (x, y) => {
    if (zeppelinSide(x, y, 7.5, 5.5, 0, 0.85)) return AMBER;
    return arrow(x, y, 7.5, 9, 13, false) ? INK : ground(x, y, 0, 15);
  });
  icon(autopilotOutput, "zeppelin_engage", (x, y) => {
    if (zeppelinSide(x, y, 9, 7.5, 0, 0.85)) return GREEN;
    return x <= 1 && (y === 4 || y === 7 || y === 8 || y === 11) ? INK : null;
  });
  icon(autopilotOutput, "zeppelin_climb", (x, y) => (zeppelinSide(x, y, 9.5, 6.5, 20, 0.8) ? GREEN : arrow(x, y, 2.5, 8, 15, true) ? INK : null));
  icon(autopilotOutput, "zeppelin_descend", (x, y) => (zeppelinSide(x, y, 9.5, 8.5, -20, 0.8) ? GREEN : arrow(x, y, 2.5, 0, 7, false) ? INK : null));
  icon(autopilotOutput, "zeppelin_land_at_pin", (x, y) => {
    const pin = smallPin(x, y);
    if (pin || zeppelinSide(x, y, 5.5, 6.5, 0, 0.7)) return pin ?? AMBER;
    return arrow(x, y, 5.5, 10, 13, false) ? INK : ground(x, y, 1, 15);
  });
  icon(autopilotOutput, "zeppelin_ground_height", (x, y) => (zeppelinSide(x, y, 7.5, 2.5, 0) ? INK : heightOverGround(x, y)));

  // The rocket upright, its nose up, its middle at (cx, cy), leaning a tilt (degrees, its nose to the right): a round
  // body with a porthole under a pointed nose, a swept fin out either side at its foot.
  const rocketSide = (x, y, cx, cy, tilt = 0, size = 1) => {
    const turn = (tilt * Math.PI) / 180;
    const dx = (x - cx) / size;
    const dy = (y - cy) / size;
    const across = dx * Math.cos(turn) + dy * Math.sin(turn);
    const up = dx * Math.sin(turn) - dy * Math.cos(turn);
    const porthole = Math.hypot(across, up - 1) <= 0.8;
    const body = up >= -4.5 && up <= 2.5 && Math.abs(across) <= 2.1 && !porthole;
    const nose = up > 2.5 && up <= 7 && Math.abs(across) <= 2.3 * (1 - (up - 2.5) / 4.5);
    const fin = up >= -6.5 && up <= -2 && Math.abs(across) > 2.1 && Math.abs(across) <= 2.1 + 1.7 * Math.min(1, (-up - 2) / 2.5);
    return body || nose || fin;
  };
  // Its flame, a fan of fire under its foot, so far under its middle.
  const FLAME = [0xff, 0x9a, 0x2a];
  const flame = (x, y, cx, top, length) => y >= top && y <= top + length && Math.abs(x - cx) <= 1.6 * (1 - (y - top) / (length + 1));
  icon(autopilotOutput, "rocket", (x, y) => {
    const r = Math.hypot(x - 7.5, y - 7.5);
    const fin = (Math.abs(x - y) <= 1 || Math.abs(x + y - 15) <= 1) && r <= 7.2;
    return r <= 4.2 || fin ? INK : null;
  });
  icon(autopilotOutput, "rocket_side", (x, y) => (rocketSide(x, y, 7.5, 7.5) ? (x >= 8 ? PALE : INK) : null));
  // Take off: the green rocket rising on its flame off the ground. Land: the amber one settling on a short flame.
  icon(autopilotOutput, "rocket_take_off", (x, y) => {
    if (rocketSide(x, y, 7.5, 5.5, 0, 0.75)) return GREEN;
    return flame(x, y, 7.5, 10.5, 3) ? FLAME : ground(x, y, 0, 15);
  });
  icon(autopilotOutput, "rocket_land", (x, y) => {
    if (rocketSide(x, y, 7.5, 7, 0, 0.75)) return AMBER;
    return flame(x, y, 7.5, 12, 1) ? FLAME : ground(x, y, 0, 15);
  });
  icon(autopilotOutput, "rocket_engage", (x, y) => {
    if (rocketSide(x, y, 9, 7.5, 0, 0.85)) return GREEN;
    return x <= 1 && (y === 4 || y === 7 || y === 8 || y === 11) ? INK : null;
  });
  icon(autopilotOutput, "rocket_climb", (x, y) => (rocketSide(x, y, 9.5, 7.5, 10, 0.8) ? GREEN : arrow(x, y, 2.5, 8, 15, true) ? INK : null));
  icon(autopilotOutput, "rocket_descend", (x, y) => (rocketSide(x, y, 9.5, 7.5, 0, 0.8) ? GREEN : arrow(x, y, 2.5, 0, 7, false) ? INK : null));
  icon(autopilotOutput, "rocket_land_at_pin", (x, y) => {
    const pin = smallPin(x, y);
    if (pin || rocketSide(x, y, 5.5, 6.5, 0, 0.7)) return pin ?? AMBER;
    return flame(x, y, 5.5, 11, 1) ? FLAME : ground(x, y, 1, 15);
  });
  icon(autopilotOutput, "rocket_ground_height", (x, y) => (rocketSide(x, y, 7.5, 5, 0, 0.6) ? INK : heightOverGround(x, y)));

  // The freighter from the side, her prongs to the left, her middle at (cx, cy): a flat lens of a saucer, a low dome on
  // top, her cockpit's pod bulging ahead under its glass, her legs under her when they are down.
  // Which part of her a pixel is, "dome" or "hull", or null: the dome is shaded apart so she reads as one ship.
  const freighterSide = (x, y, cx, cy, size = 1, legs = false) => {
    const dx = (x - cx) / size;
    const dy = (cy - y) / size;
    const lens = Math.abs(dx) <= 6.5 && Math.abs(dy) <= 1.9 * (1 - (dx * dx) / 46) + 0.5;
    const dome = Math.hypot(dx - 0.5, dy - 1.7) <= 1.1;
    const pod = Math.hypot(dx + 5.2, dy - 0.4) <= 1.3;
    const leg = legs && dy < -1 && dy >= -3.5 && (Math.abs(dx + 3.5) <= 0.5 || Math.abs(dx - 3.5) <= 0.5);
    if (dome) return "dome";
    return lens || pod || leg ? "hull" : null;
  };
  // A saucer in a colour, her dome a shade darker.
  const shaded = (part, colour, dark) => (part === "dome" ? dark : part ? colour : null);
  const AMBER_DARK = [0xb0, 0x7c, 0x18];
  // Her thrusters' blue glow under her as she lifts off or lands.
  const GLOW = [0x5a, 0xc8, 0xff];
  const glow = (x, y, cx, top, length) => y >= top && y <= top + length && Math.abs(x - cx) <= 4.5 && (x + y) % 2 === 0;
  // The freighter from above, nose up, drawn by hand so she reads at sixteen pixels: her disc with its dome, her two
  // prongs either side of the loading gap, and her cockpit's pod out past her right bow, its glass lit.
  const FREIGHTER_FROM_ABOVE = [
    "................",
    "....##..##......",
    "....##..##...cc.",
    "...###..###.cccc",
    "...###..###.####",
    "...###..###..##.",
    "..####..####.#..",
    "..###########...",
    "..##########....",
    ".############...",
    ".#####pp#####...",
    ".#####pp#####...",
    "..##########....",
    "..##########....",
    "...########.....",
    ".....####.......",
  ];
  icon(autopilotOutput, "freighter", (x, y) => ({ "#": INK, p: PALE, c: CYAN })[FREIGHTER_FROM_ABOVE[y][x]] ?? null);
  icon(autopilotOutput, "freighter_side", (x, y) => (freighterSide(x, y, 7.5, 8) ? (y <= 7 ? PALE : INK) : null));
  // Take off: the green freighter rising straight up on her glow off the ground. Land: the amber one settling on hers.
  icon(autopilotOutput, "freighter_take_off", (x, y) => {
    const part = freighterSide(x, y, 7.5, 5.5, 0.85, true);
    if (part) return shaded(part, GREEN, GREEN_DARK);
    return glow(x, y, 7.5, 10, 2) ? GLOW : ground(x, y, 0, 15);
  });
  icon(autopilotOutput, "freighter_land", (x, y) => {
    const part = freighterSide(x, y, 7.5, 9, 0.85, true);
    if (part) return shaded(part, AMBER, AMBER_DARK);
    return glow(x, y, 7.5, 13, 0) ? GLOW : ground(x, y, 0, 15);
  });
  icon(autopilotOutput, "freighter_engage", (x, y) => {
    const part = freighterSide(x, y, 9, 7.5, 0.85);
    if (part) return shaded(part, GREEN, GREEN_DARK);
    return x <= 1 && (y === 4 || y === 7 || y === 8 || y === 11) ? INK : null;
  });
  icon(autopilotOutput, "freighter_climb", (x, y) => shaded(freighterSide(x, y, 9.5, 7.5, 0.8), GREEN, GREEN_DARK) ?? (arrow(x, y, 2.5, 8, 15, true) ? INK : null));
  icon(autopilotOutput, "freighter_descend", (x, y) => shaded(freighterSide(x, y, 9.5, 7.5, 0.8), GREEN, GREEN_DARK) ?? (arrow(x, y, 2.5, 0, 7, false) ? INK : null));
  icon(autopilotOutput, "freighter_land_at_pin", (x, y) => {
    const pin = smallPin(x, y);
    if (pin) return pin;
    return shaded(freighterSide(x, y, 6.5, 9, 0.6, true), AMBER, AMBER_DARK) ?? ground(x, y, 1, 15);
  });
  icon(autopilotOutput, "freighter_ground_height", (x, y) => (freighterSide(x, y, 7.5, 4, 0.6, true) ? INK : heightOverGround(x, y)));

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

  // The transponders' markers (see MarkerIcon), drawn white so each takes its transponder's colour, kept to the middle
  // so they fit the round marker they sit in: a control tower, its glass cab over the shaft, the windows showing
  // through; an airliner from above; a house with its door; a five-pointed star; a flag on its pole; a heart; a cut gem,
  // its girdle showing through; a round tree on its trunk; an anchor; a pickaxe; a zeppelin from the side, its round
  // bow ahead, tapering to its fins at the tail, its car under the bow.
  const markerOutput = resolve(guiOutput, "marker");
  mkdirSync(markerOutput, { recursive: true });
  const markers = {
    tower: (x, y) => {
      const cab = y >= 3 && y <= 5 && x >= 4 && x <= 11 && !(y === 4 && x >= 5 && x <= 10);
      const shaft = y >= 6 && y <= 13 && x >= 6 && x <= 9;
      return cab || shaft || (y === 2 && x >= 3 && x <= 12) || (y === 13 && x >= 5 && x <= 10) || (x === 8 && y <= 1);
    },
    airliner: (x, y) => airliner(x, y, 7.5, 7.5, 0, 0.95),
    house: (x, y) => {
      const roof = y >= 2 && y <= 7 && Math.abs(x - 7.5) <= y - 1;
      const walls = y >= 8 && y <= 13 && x >= 3 && x <= 12 && !(x >= 7 && x <= 8 && y >= 10);
      return roof || walls;
    },
    star: (x, y) => {
      const r = Math.hypot(x - 7.5, y - 8);
      const point = (2 * Math.PI) / 5;
      const share = ((((Math.atan2(x - 7.5, 8 - y) % point) + point) % point) / point) * 2 - 1;
      return r <= 2.8 + 3.9 * Math.abs(share);
    },
    flag: (x, y) => (x === 4 && y >= 2 && y <= 14) || (x >= 5 && x <= 12 && y >= 2 + (x % 4 === 0 ? 1 : 0) && y <= 8 + (x % 4 === 0 ? 1 : 0)),
    heart: (x, y) => {
      const u = (x - 7.5) / 6;
      const v = -(y - 7) / 6;
      return (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0;
    },
    gem: (x, y) => {
      const crown = y >= 3 && y <= 5 && Math.abs(x - 7.5) <= 3.5 + (y - 3);
      const pavilion = y >= 7 && y <= 13 && Math.abs(x - 7.5) <= (13.5 - y) * 0.9;
      return crown || pavilion;
    },
    tree: (x, y) => Math.hypot(x - 7.5, y - 6) <= 5 || (x >= 7 && x <= 8 && y >= 10 && y <= 14),
    anchor: (x, y) => {
      const ring = Math.hypot(x - 7.5, y - 2.5);
      if (ring >= 0.9 && ring <= 2.1) return true;
      const arms = Math.hypot(x - 7.5, y - 7);
      return (x >= 7 && x <= 8 && y >= 4 && y <= 13) || (y === 6 && x >= 4 && x <= 11) || (y >= 9 && arms >= 5 && arms <= 6.4);
    },
    pickaxe: (x, y) => onSegment(x, y, [3, 13], [10, 6], 1.8) || onSegment(x, y, [10, 6], [4.5, 2.5], 2.2) || onSegment(x, y, [10, 6], [13.5, 11.5], 2.2),
    airship: (x, y) => {
      const along = (x - 6.5) / 5;
      const taper = x > 7 ? 1 - (x - 7) / 10 : 1;
      const body = along * along < 1 && Math.abs(y - 7) <= 2.4 * Math.sqrt(1 - along * along) * taper;
      const fins = x >= 10 && x <= 12 && Math.abs(y - 7) <= (x - 9) * 1.15;
      const car = y === 10 && x >= 4 && x <= 6;
      return body || fins || car;
    },
  };
  for (const [name, shape] of Object.entries(markers)) {
    icon(markerOutput, name, (x, y) => (shape(x, y) ? INK : null));
  }

  // The announcement panel's tiles (see Announcement), each a picture a child knows without reading, 32 by 32 so it
  // stays clear across a whole tile: someone waving hello beside yellow waves of the hand; a white airliner from the
  // side climbing off a runway on a green trail; a grey cloud over orange bumps over a buckled seatbelt; a paper cup
  // with a straw beside a chocolate-chip cookie; a cabin window looking out on a blue sky, a cloud, the sun and green
  // mountains; an orange basketball over the pool's blue water, its chrome ladder beside it; a yellow crescent moon
  // with sleepy Zs and stars; the airliner coming down onto a runway on an amber trail; and a red heart over the
  // airliner parked on the ground.
  const announcementOutput = resolve(guiOutput, "announcement");
  mkdirSync(announcementOutput, { recursive: true });
  const BIG = 32;
  const SKIN = [0xf2, 0xc0, 0x96];
  const HAIR = [0x6a, 0x3a, 0x1a];
  const SHIRT = [0x3c, 0x8c, 0xff];
  const TROUSERS = [0x2a, 0x3c, 0x6a];
  const YELLOW = [0xff, 0xd8, 0x4a];
  const ORANGE = [0xff, 0x8a, 0x3c];
  const LIVERY = [0x3c, 0x6c, 0xd8];
  const DARK = [0x24, 0x30, 0x40];
  const CLOUD = [0xd8, 0xde, 0xe6];
  const SKY = [0x6c, 0xa8, 0xe0];
  const HILL = [0x4a, 0xa8, 0x4c];
  const HILL_DARK = [0x32, 0x7a, 0x36];
  const WATER = [0x2c, 0x7c, 0xe8];
  const WATER_LIGHT = [0x8c, 0xc8, 0xff];
  const COOKIE = [0xd0, 0x8c, 0x48];
  const COOKIE_LIGHT = [0xe4, 0xa8, 0x64];
  const CHIP = [0x4a, 0x26, 0x12];
  const BALL = [0xf0, 0x7a, 0x28];
  const BALL_SEAM = [0x5a, 0x24, 0x08];
  const HEART_LIGHT = [0xff, 0x7a, 0x70];
  const bigIcon = (name, paint) => texture(announcementOutput, name, (x, y) => paint(x, y) ?? CLEAR, 0, BIG, BIG);
  const disc = (x, y, cx, cy, r) => Math.hypot(x - cx, y - cy) <= r;
  // Within r of the rectangle from (x0, y0) to (x1, y1): a rectangle with rounded corners, grown by r.
  const rounded = (x, y, x0, y0, x1, y1, r) => Math.hypot(x - Math.max(x0, Math.min(x1, x)), y - Math.max(y0, Math.min(y1, y))) <= r;
  // A runway along a row, its centre line dashed.
  const strip = (x, y, row, from, to) => (y >= row && y <= row + 1 && x >= from && x <= to ? (y === row && x % 4 < 2 ? INK : RUNWAY) : null);
  // A dotted trail along a segment, every other pixel.
  const trail = (x, y, a, b, colour) => (onSegment(x, y, a, b, 1.6) && (x + y) % 3 !== 0 ? colour : null);
  // The airliner from the side, nose to the right, its middle at (cx, cy), its nose raised by the angle in degrees
  // (lowered if negative): a white fuselage with a row of windows, the flight deck's dark glass at the nose, the blue
  // fin, the wing and an engine under it.
  const sidePlane = (x, y, cx, cy, angle, size = 1) => {
    const turn = (angle * Math.PI) / 180;
    const dx = (x - cx) / size;
    const up = -(y - cy) / size;
    const u = dx * Math.cos(turn) + up * Math.sin(turn);
    const v = -dx * Math.sin(turn) + up * Math.cos(turn);
    if (inTriangle(u, v, [-12.5, 1.5], [-8, 1.5], [-12.5, 9])) return LIVERY;
    const body = Math.hypot(u - Math.max(-10, Math.min(9, u)), v) <= 2.6;
    if (body && u >= 8 && v >= 0.3) return DARK;
    if (body && Math.abs(v - 0.8) <= 0.6 && u >= -7 && u <= 6 && Math.floor(u) % 2 === 0) return CYAN_DARK;
    if (body) return v < -1.3 ? PALE : INK;
    if (Math.hypot(u - Math.max(-1.5, Math.min(1.5, u)), v + 4.6) <= 1.3) return STEEL;
    return inTriangle(u, v, [-3, -1], [3, -1], [-4.5, -5.5]) ? PALE : null;
  };

  bigIcon("welcome", (x, y) => {
    const wave = Math.hypot(x - 21, y - 7);
    const angle = Math.atan2(y - 7, x - 21);
    if (angle > -1.4 && angle < 0.7 && ((wave >= 4.6 && wave <= 5.8) || (wave >= 7.6 && wave <= 8.8))) return YELLOW;
    if (disc(x, y, 21, 6.5, 2.4) || disc(x, y, 5, 24, 1.8)) return SKIN;
    if (onSegment(x, y, [16, 15], [20.5, 9], 3.2) || onSegment(x, y, [8, 15], [5, 22], 3.2)) return SHIRT;
    if (disc(x, y, 12, 8, 4.6)) {
      if (y <= 5.5) return HAIR;
      const eye = (x === 10 || x === 14) && y === 8;
      const smile = y === 10 && x >= 11 && x <= 13;
      return eye || smile ? DARK : SKIN;
    }
    if (rounded(x, y, 8, 15, 16, 24, 1.5)) return SHIRT;
    return y >= 26 && y <= 30 && ((x >= 8 && x <= 10) || (x >= 14 && x <= 16)) ? TROUSERS : null;
  });
  bigIcon("takeoff", (x, y) => sidePlane(x, y, 17, 13, 25, 0.95) ?? trail(x, y, [2, 27], [8, 21], GREEN) ?? strip(x, y, 29, 0, 20));
  bigIcon("turbulence", (x, y) => {
    const puff = disc(x, y, 10, 9, 5) || disc(x, y, 17, 6.5, 6) || disc(x, y, 23.5, 9.5, 4.5) || rounded(x, y, 7, 10, 25, 12, 2);
    if (puff) return y <= 6 ? INK : CLOUD;
    if (x >= 4 && x <= 27 && Math.abs(y - (18 + 1.6 * Math.sin(x * 0.7))) <= 0.9) return ORANGE;
    if (rounded(x, y, 12, 23, 19, 27, 1.5)) {
      return x >= 14 && x <= 17 && y >= 24 && y <= 26 ? DARK : PALE;
    }
    return y >= 23 && y <= 27 && (x <= 10 || x >= 21) ? AMBER : null;
  });
  bigIcon("snacks", (x, y) => {
    if (onSegment(x, y, [10.5, 9], [14, 2], 1.6)) return RED;
    if (y >= 7 && y <= 8 && x >= 3 && x <= 15) return PALE;
    const half = 6 - (y - 9) * 0.12;
    if (y >= 9 && y <= 26 && Math.abs(x - 9) <= half) return y >= 15 && y <= 17 ? RED : INK;
    const cookie = Math.hypot(x - 23, y - 20);
    if (cookie > 7.5) return null;
    const chips = [[20, 17], [25, 18], [22, 23], [26, 23], [19, 21], [23, 15]];
    if (chips.some(([cx, cy]) => disc(x, y, cx, cy, 0.9))) return CHIP;
    return cookie >= 6.5 ? COOKIE_LIGHT : COOKIE;
  });
  bigIcon("window", (x, y) => {
    if (!rounded(x, y, 13, 9, 18, 22, 8)) return null;
    if (!rounded(x, y, 14, 10, 17, 21, 5.5)) return PALE;
    if (disc(x, y, 19, 9, 2.6)) return YELLOW;
    if (disc(x, y, 11, 12, 2.4) || disc(x, y, 14, 11, 2.8) || rounded(x, y, 10, 12, 15, 13, 1)) return INK;
    const peak = (cx, height) => y >= 25 - height + Math.abs(x - cx) * 1.1;
    if (peak(12, 10) || peak(20, 8)) return y <= 17 ? INK : x < 15 ? HILL : HILL_DARK;
    return y >= 24 ? HILL : SKY;
  });
  bigIcon("playtime", (x, y) => {
    const ladder = (x === 24 || x === 28) && y >= 9 && y <= 22;
    const rung = y % 4 === 1 && y >= 12 && y <= 21 && x > 24 && x < 28;
    const rail = (x >= 24 && x <= 28) && Math.hypot(x - 26, y - 9) >= 1.4 && Math.hypot(x - 26, y - 9) <= 2.6 && y <= 9;
    if (ladder || rung || rail) return STEEL;
    const ball = Math.hypot(x - 11, y - 10);
    if (ball <= 7.5) {
      const seam = Math.abs(x - 11) <= 0.5 || Math.abs(y - 10) <= 0.5 || Math.abs(Math.hypot(x - 2, y - 10) - 6.5) <= 0.6
        || Math.abs(Math.hypot(x - 20, y - 10) - 6.5) <= 0.6;
      return seam ? BALL_SEAM : BALL;
    }
    if (y < 21) return null;
    return Math.abs(y - (23 + Math.sin(x * 0.8))) <= 0.6 || Math.abs(y - (27 + Math.sin(x * 0.8 + 2))) <= 0.6 ? WATER_LIGHT : WATER;
  });
  bigIcon("sleep", (x, y) => {
    if (disc(x, y, 13, 17, 10) && !disc(x, y, 19, 12, 9)) return YELLOW;
    const bigZ = (y === 11 || y === 17) && x >= 20 && x <= 26 || onSegment(x, y, [26, 11], [20, 17], 1.4) && y > 11 && y < 17;
    const smallZ = (y === 3 || y === 7) && x >= 25 && x <= 29 || onSegment(x, y, [29, 3], [25, 7], 1.2) && y > 3 && y < 7;
    if (bigZ || smallZ) return INK;
    const stars = [[5, 4], [27, 25], [20, 29]];
    return stars.some(([cx, cy]) => (x === cx && Math.abs(y - cy) <= 1) || (y === cy && Math.abs(x - cx) <= 1)) ? YELLOW : null;
  });
  bigIcon("landing", (x, y) => sidePlane(x, y, 15, 13, -18, 0.95) ?? trail(x, y, [1, 3], [3, 8], AMBER) ?? strip(x, y, 28, 8, 31));
  bigIcon("arrival", (x, y) => {
    const u = (x - 15.5) / 8;
    const v = -(y - 11) / 8;
    if ((u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0) return disc(x, y, 11, 7, 1.8) ? HEART_LIGHT : RED;
    return sidePlane(x, y, 16, 25, 0, 0.7) ?? strip(x, y, 29, 0, 31);
  });

  // The flight deck's systems tiles and keys (see CockpitKeysArt), 32 by 32 like the announcement tiles, each a
  // picture a child knows without reading. The seatbelt signs: someone sitting in a seat, a big amber belt buckled
  // across their lap. The cabin lights: a light bulb with long rays (bright), the same in amber with short ones
  // (dimmed), or the bulb dim blue under a crescent moon (the night glow). The jump lights: a skydiver under a canopy
  // beside a lamp, its red and green halves both dark (off), red (stand by) or green (go). The window shades: a cabin
  // window on the sky with its striped shade a third of the way down, or pulled all the way down over it. The laser:
  // the turret under the nose, light metal, firing a red beam, sparks where it strikes. The drop bay: a crate on the
  // bay's closed doors, or the doors swung open and the crate floating down under a parachute.
  const cockpitOutput = resolve(guiOutput, "cockpit");
  mkdirSync(cockpitOutput, { recursive: true });
  const cockpitIcon = (name, paint) => texture(cockpitOutput, name, (x, y) => paint(x, y) ?? CLEAR, 0, BIG, BIG);
  const ellipse = (x, y, cx, cy, rx, ry) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1;
  const NIGHT_MOON = [0xa8, 0xc8, 0xff];
  const NIGHT_GLASS = [0x5c, 0x7e, 0xd0];
  const LAMP_RED_OFF = [0x6a, 0x26, 0x26];
  const LAMP_RED_OFF_LIGHT = [0x8c, 0x3c, 0x3c];
  const LAMP_GREEN_OFF = [0x22, 0x58, 0x34];
  const SHADE = [0xb4, 0xba, 0xc4];
  const SHADE_DARK = [0x86, 0x8d, 0x98];

  cockpitIcon("seatbelts", (x, y) => {
    if (rounded(x, y, 15, 16, 18, 20, 0.8)) return x >= 16 && x <= 17 && y >= 17 && y <= 19 ? INK : STEEL;
    if (y >= 17 && y <= 19 && x >= 9 && x <= 23) return AMBER;
    if (disc(x, y, 14, 6, 3.3)) return y <= 4 ? HAIR : SKIN;
    if (rounded(x, y, 11, 11, 15, 19, 1.4)) return SHIRT;
    if (rounded(x, y, 14, 19, 23, 21, 1.4) || rounded(x, y, 22, 21, 23, 27, 1.4)) return TROUSERS;
    if (rounded(x, y, 21, 28, 27, 28, 1)) return DARK;
    if (rounded(x, y, 6, 8, 8, 22, 1.2) || rounded(x, y, 7, 23, 22, 24, 0.6)) return STEEL_DARK;
    return x >= 13 && x <= 15 && y >= 25 && y <= 29 ? STEEL_DARK : null;
  });
  // A light bulb, lit in a colour, its rays reaching so far out.
  const bulb = (x, y, glass, rays) => {
    const r = Math.hypot(x - 16, y - 12);
    if (r <= 7) return r <= 3 ? INK : glass;
    if (x >= 13 && x <= 18 && y >= 18 && y <= 21) return glass;
    if (x >= 13 && x <= 18 && y >= 22 && y <= 26) return y % 2 === 0 ? STEEL : STEEL_DARK;
    if (x >= 15 && x <= 16 && y === 27) return DARK;
    const angle = Math.atan2(y - 12, x - 16);
    const ray = Math.abs(((angle / (Math.PI / 4)) % 1 + 1) % 1 - 0.5) > 0.38 && angle < 0.6;
    return ray && r >= 9 && r <= rays ? glass : null;
  };
  cockpitIcon("cabin_lights_bright", (x, y) => bulb(x, y, YELLOW, 14.5));
  cockpitIcon("cabin_lights_dim", (x, y) => bulb(x, y, AMBER, 10.6));
  cockpitIcon("cabin_lights_night", (x, y) => {
    if (disc(x, y, 26, 6, 4.5) && !disc(x, y, 28.5, 4, 3.8)) return NIGHT_MOON;
    return bulb(x, y, NIGHT_GLASS, 0);
  });
  // A skydiver under a striped canopy, the lines down to the harness, beside the jump light in its colour, or in two
  // colours, its upper half's and its lower half's.
  const skydiver = (x, y, lamp, lampLight, lampBelow = lamp) => {
    const lampR = Math.hypot(x - 25, y - 22);
    if (lampR <= 4.5) return lampR >= 3.6 ? STEEL : disc(x, y, 24, 21, 1.3) ? lampLight : y <= 22 ? lamp : lampBelow;
    if (ellipse(x, y, 12, 10, 10, 6.5) && y <= 10) return Math.floor((x + 1) / 4) % 2 === 0 ? ORANGE : INK;
    if (onSegment(x, y, [3, 10], [11, 20], 0.7) || onSegment(x, y, [21, 10], [13, 20], 0.7) || onSegment(x, y, [12, 10], [12, 19], 0.7)) return PALE;
    if (disc(x, y, 12, 21, 2)) return SKIN;
    if (rounded(x, y, 11, 24, 13, 27, 0.9)) return SHIRT;
    return onSegment(x, y, [11, 28], [9, 31], 1.2) || onSegment(x, y, [13, 28], [15, 31], 1.2) ? TROUSERS : null;
  };
  cockpitIcon("jump_lights_off", (x, y) => skydiver(x, y, LAMP_RED_OFF, LAMP_RED_OFF_LIGHT, LAMP_GREEN_OFF));
  cockpitIcon("jump_lights_red", (x, y) => skydiver(x, y, RED, HEART_LIGHT));
  cockpitIcon("jump_lights_green", (x, y) => skydiver(x, y, GREEN, [0xb4, 0xff, 0xc8]));
  // The cabin window, its frame round the sky with the sun and a cloud, its shade down to a row.
  const shadedWindow = (x, y, shadeTo) => {
    if (!rounded(x, y, 12, 8, 19, 23, 8)) return null;
    if (!rounded(x, y, 13, 9, 18, 22, 5.5)) return PALE;
    if (y <= shadeTo) return y === shadeTo ? SHADE_DARK : y % 3 === 0 ? SHADE_DARK : SHADE;
    if (y === shadeTo + 1 && x >= 15 && x <= 16) return DARK;
    if (disc(x, y, 19, 13, 2.6)) return YELLOW;
    if (disc(x, y, 12, 17, 2.4) || disc(x, y, 15, 16, 2.8) || rounded(x, y, 11, 17, 17, 18, 1)) return INK;
    return y >= 25 ? HILL : SKY;
  };
  cockpitIcon("shades_open", (x, y) => shadedWindow(x, y, 12));
  cockpitIcon("shades_closed", (x, y) => {
    const window = shadedWindow(x, y, BIG);
    return window !== null && window !== PALE && y === 26 && x >= 15 && x <= 16 ? DARK : window;
  });
  cockpitIcon("laser", (x, y) => {
    if (y >= 3 && y <= 6 && x >= 1 && x <= 30) return y === 6 ? STEEL : PALE;
    const sparks = [[29, 26], [27, 29], [30, 23], [26, 27]];
    if (sparks.some(([cx, cy]) => x === cx && y === cy)) return YELLOW;
    if (onSegment(x, y, [19, 16], [28, 25], 0.9)) return HEART_LIGHT;
    if (onSegment(x, y, [19, 16], [28, 25], 2.4)) return RED;
    if (onSegment(x, y, [13, 10], [19, 16], 2.6)) return STEEL;
    return disc(x, y, 11, 7, 6) && y >= 7 ? (disc(x, y, 9, 9, 1.5) ? DARK : PALE) : null;
  });
  // A care package: a wooden crate, its straps across it.
  const crate = (x, y, x0, y0, x1, y1) => {
    if (!rounded(x, y, x0, y0, x1, y1, 0.8)) return null;
    const midX = Math.round((x0 + x1) / 2);
    const midY = Math.round((y0 + y1) / 2);
    return x === midX || y === midY ? CHIP : x === x0 || y === y0 ? COOKIE_LIGHT : COOKIE;
  };
  cockpitIcon("drop_safe", (x, y) => {
    if (y >= 3 && y <= 5 && x >= 2 && x <= 29) return PALE;
    if (y >= 22 && y <= 24 && x >= 5 && x <= 26) return x === 15 || x === 16 ? DARK : STEEL;
    return crate(x, y, 10, 9, 21, 20);
  });
  cockpitIcon("drop_armed", (x, y) => {
    if (y >= 3 && y <= 5 && x >= 2 && x <= 29) return PALE;
    if (onSegment(x, y, [6, 6], [10, 13], 2) || onSegment(x, y, [25, 6], [21, 13], 2)) return STEEL;
    if (ellipse(x, y, 16, 17, 7, 4.5) && y <= 17) return Math.floor((x + 1) / 3) % 2 === 0 ? RED : INK;
    if (onSegment(x, y, [9.5, 17], [14, 23], 0.6) || onSegment(x, y, [22.5, 17], [18, 23], 0.6)) return PALE;
    return crate(x, y, 13, 23, 19, 29);
  });

  // The announcement panel's glass, for when it is too far off to be drawn live: a dark tile in its grey rim. In the
  // hand: the panel's nine tiles, each in its own colour, on the dark casing.
  texture(blockOutput, "announcement_panel", (x, y) => (x === 0 || x === 15 || y === 0 || y === 15 ? [0x4a, 0x51, 0x5b] : [0x0b, 0x10, 0x17]), 1);
  const TILE_COLOURS = [YELLOW, GREEN, ORANGE, [0xff, 0x6f, 0xae], CYAN, SHIRT, [0xa5, 0x8c, 0xff], AMBER, RED];
  texture(itemOutput, "announcement_panel", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return [0x2c, 0x30, 0x36];
    const column = Math.floor((x - 1) / 5);
    const row = Math.floor((y - 1) / 5);
    return (x - 1) % 5 === 4 || (y - 1) % 5 === 4 ? [0x15, 0x17, 0x1b] : TILE_COLOURS[row * 3 + column];
  }, 0);
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
  const entityOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/entity");
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

  // The rocket crew's space helmet. Its icon: the round white helmet a little from its left, outlined dark, the big
  // gold visor over the face in its pale gasket, mirrored, with the sun's glint across its top and the blue planet's
  // rim curving across its foot, the mission patch on its left side and the dark ring at its neck. Worn, its model
  // shows a white shell darkening a little toward its foot, the gasket, the neck ring with a highlight along its top
  // and the studs of its latch, the visor gold from its top to deep amber at its foot, darker toward its sides as it
  // curves away, the blue planet's rim reflected across its foot, and the patch: a little white rocket with a red nose
  // and fins climbing out of its flame on navy, a pale blue orbit round it and stars, ringed in gold.
  {
    const mix = (a, b, t) => a.map((c, i) => Math.round(c + (b[i] - c) * Math.min(1, Math.max(0, t))));
    const SHELL = [0xf8, 0xf8, 0xfa];
    const SHELL_SHADE = [0xb4, 0xbc, 0xc8];
    const GASKET = [0xb0, 0xb6, 0xc0];
    const RING = [0x2a, 0x30, 0x3c];
    const RING_LIGHT = [0x5c, 0x64, 0x72];
    const STUD = [0x9a, 0xa2, 0xae];
    const VISOR_TOP = [0xfc, 0xdc, 0x78];
    const VISOR_MIDDLE = [0xe8, 0xac, 0x30];
    const VISOR_BOTTOM = [0xa8, 0x6c, 0x18];
    const VISOR_GLINT = [0xff, 0xf6, 0xd0];
    const PLANET = [0x34, 0x7c, 0xd4];
    const AIR_GLOW = [0x9c, 0xd8, 0xff];
    const PATCH_RED = [0xd8, 0x30, 0x30];
    const FLAME = [0xff, 0x9c, 0x20];
    const WHITE_PAINT = [0xf8, 0xf8, 0xf8];
    // The visor's gold from its top (0) to its foot (1), through its brightest gold a third of the way down.
    const gold = (t) => (t < 0.35 ? mix(VISOR_TOP, VISOR_MIDDLE, t / 0.35) : mix(VISOR_MIDDLE, VISOR_BOTTOM, (t - 0.35) / 0.65));
    texture(itemOutput, "space_helmet", outlined((x, y) => {
      if (x < 0 || y < 0 || x > 15 || y > 15) return null;
      const cx = x + 0.5;
      const cy = y + 0.5;
      if (y >= 13 && y <= 14 && Math.abs(cx - 7.5) <= (y === 13 ? 5 : 4)) {
        if (y === 13) return x === 7 || x === 8 ? STUD : RING_LIGHT;
        return RING;
      }
      if (Math.hypot(cx - 7.5, cy - 6.9) > 6.3) return null;
      const visor = Math.hypot((cx - 6.6) / 4.4, (cy - 7.2) / 3.6);
      if (visor <= 1) {
        const down = (cy - 3.6) / 7.2;
        if ((x === 4 && y === 5) || (x === 5 && y === 4) || (x === 3 && y === 6)) return VISOR_GLINT;
        const horizon = 8.6 + 0.07 * (cx - 6.6) ** 2;
        if (Math.abs(cy - horizon) < 0.5) return mix(gold(down), AIR_GLOW, 0.55);
        return cy > horizon ? mix(gold(down), PLANET, 0.65) : gold(down);
      }
      if (Math.hypot((cx - 6.6) / 5.3, (cy - 7.2) / 4.5) <= 1) return GASKET;
      const patch = Math.hypot(cx - 12.1, cy - 8.3);
      if (patch < 0.8) return PATCH_RED;
      if (patch < 1.6) return BLUE;
      return mix(SHELL, SHELL_SHADE, ((cx - 3) * 0.5 + cy - 3) / 13);
    }), 0);
    texture(itemOutput, "space_helmet_shell", (x, y) => mix(SHELL, SHELL_SHADE, y / 40), 2);
    texture(itemOutput, "space_helmet_gasket", () => GASKET, 2);
    // The ring's sides show its top two rows: the highlight along its top, then its latch's studs along the dark.
    texture(itemOutput, "space_helmet_ring", (x, y) => {
      if (y === 0) return RING_LIGHT;
      return y === 1 && x % 4 === 1 ? STUD : RING;
    }, 2);
    // The visor, stretched over its wide face: its corners rounded off, clear to show the gasket under them.
    texture(itemOutput, "space_helmet_visor", (x, y) => {
      const across = (x - 7.5) / 8;
      if (Math.abs(across) ** 3 + Math.abs((y - 7.5) / 8) ** 3 > 1) return CLEAR;
      const curving = 1 - 0.3 * across ** 4;
      const tint = gold(y / 15).map((c) => Math.round(c * curving));
      const horizon = 12.6 - 2 * (1 - across ** 2);
      if (Math.abs(y - horizon) < 0.6) return mix(tint, AIR_GLOW, 0.5);
      if (y > horizon) return mix(tint, PLANET, 0.65);
      const glint = Math.abs(x - 1.2 * y - 1.5);
      if (glint < 1.3 && y < 7) return mix(VISOR_GLINT, tint, glint / 2 + y / 12);
      return Math.abs(x - 1.2 * y - 5) < 0.5 && y < 6 ? mix(VISOR_GLINT, tint, 0.45) : tint;
    }, 1);
    texture(itemOutput, "space_helmet_patch", (x, y) => {
      const r = Math.hypot(x - 7.5, y - 7.5);
      if (r > 7.6) return CLEAR;
      if (r > 6.4) return GOLD;
      if (y === 2 && (x === 7 || x === 8)) return PATCH_RED;
      if (y >= 3 && y <= 10 && (x === 7 || x === 8)) return y === 5 ? BLUE : WHITE_PAINT;
      if (y >= 8 && y <= 10 && (x === 6 || x === 9)) return PATCH_RED;
      if (y >= 11 && y <= 12 && (x === 7 || x === 8)) return FLAME;
      if (Math.abs(Math.hypot((x - 7.5) / 5.6, (y - 8) / 2.4) - 1) < 0.12) return AIR_GLOW;
      const star = (x === 3 && y === 5) || (x === 12 && y === 4) || (x === 4 && y === 11) || (x === 11 && y === 11);
      return star ? WHITE_PAINT : BLUE;
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

// The den's disco ball (see DiscoBallBlock). A mirror tile, which its renderer lays over each of the ball's facets:
// cool bright silver, shading off from its top left, a white gleam across its middle, inside a line of dark grout.
{
  texture(blockOutput, "disco_ball", (x, y) => {
    if (x === 0 || y === 0 || x === 15 || y === 15) return [0x3a, 0x3e, 0x46];
    if (Math.abs(x - y) <= 1 && x + y >= 8 && x + y <= 18) return [0xfa, 0xfc, 0xff];
    const v = 238 - 2.5 * (x + y);
    return [v - 6, v - 2, v + 6];
  }, 3);
  // A tile's glint: a four-pointed star, white on black, its renderer adding it over the tile in the light's colour;
  // 32x32, as the nav lights' glow is, so its long thin points do not show their pixels.
  const pixels = [];
  for (let y = 0; y < 32; y++) {
    const row = [];
    for (let x = 0; x < 32; x++) {
      const dx = x - 15.5;
      const dy = y - 15.5;
      const point = (along, across) => Math.max(0, 1 - Math.abs(along) / 16) ** 2 * Math.max(0, 1 - Math.abs(across) / 1.8);
      const core = Math.max(0, 1 - Math.hypot(dx, dy) / 6) ** 2;
      const v = Math.round(255 * Math.min(1, Math.max(point(dx, dy), point(dy, dx)) + core));
      row.push([v, v, v, 255]);
    }
    pixels.push(row);
  }
  writeFileSync(resolve(miscOutput, "disco_glint.png"), encodePng(pixels));
  // In the hand: the ball hanging from its cable by a chrome cap, a grid of mirror tiles in dark grout, lit from its
  // top left, where one tile gleams white, and darkening to its rim; a few tiles catch red, green, blue, yellow and
  // pink, and a sparkle twinkles off it.
  const FLECKS = { "2,3": [0xf0, 0x40, 0x40], "3,4": [0x40, 0xd0, 0x50], "1,4": [0x40, 0x80, 0xf0], "3,2": [0xf0, 0xd0, 0x30], "4,3": [0xf0, 0x50, 0xc0] };
  texture(itemOutput, "disco_ball", (x, y) => {
    if (x === 13 && y === 3) return [0xff, 0xff, 0xff];
    if (Math.abs(x - 13) + Math.abs(y - 3) === 1) return [0xff, 0xf0, 0xa0];
    if (y <= 2) return x === 7 ? [0x50, 0x52, 0x58] : x === 8 ? [0x30, 0x32, 0x36] : CLEAR;
    if (y === 3 && x >= 6 && x <= 9) return [0xc8, 0xcc, 0xd2];
    const nx = (x - 7.5) / 6.1;
    const ny = (y - 9.5) / 6.1;
    const r = Math.hypot(nx, ny);
    if (r > 1) return CLEAR;
    const lit = Math.max(0.4, 1 - 0.25 * (nx + ny) - 0.25 * r ** 4);
    if ((x + 1) % 3 === 0 || y % 3 === 0) return [0x7c, 0x80, 0x8a].map((c) => clamp(Math.round(c * lit)));
    const tile = `${Math.floor((x + 1) / 3)},${Math.floor(y / 3)}`;
    if (tile === "1,2") return [0xff, 0xff, 0xff];
    const shade = (Math.floor((x + 1) / 3) * 5 + Math.floor(y / 3) * 3) % 4 === 0 ? 0.85 : 1;
    return (FLECKS[tile] ?? [0xe0, 0xe4, 0xec]).map((c) => clamp(Math.round(c * lit * shade)));
  }, 0);
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
// of coffee pouring, and the babycino's little glass of frothed milk, its froth dusted with chocolate and topped with
// pink and white marshmallows. Their icons: a small espresso cup on its saucer, a wide cappuccino cup with its foam heaped over
// the rim and a heart of cocoa in it, the mocha's tall glass under a dome of cream, and the babycino's little glass
// of milk, froth heaped over it with two marshmallows on top, each with steam rising and
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
  const MILK = [0xfa, 0xf8, 0xf2];
  const MARSHMALLOW_PINK = [0xf4, 0xa8, 0xc4];
  // The glass's sides show rows 12 to 15 of it: froth over milk, the glass's edge down each side.
  texture(blockOutput, "babycino_glass", (x, y) => {
    if (x === 0 || x === 15) return GLASS_EDGE;
    return y <= 12 ? FOAM : MILK;
  }, 2);
  texture(blockOutput, "babycino_froth", (x, y) => {
    if (x >= 3 && x <= 6 && y >= 4 && y <= 7) return MARSHMALLOW_PINK;
    if (x >= 9 && x <= 12 && y >= 8 && y <= 11) return CREAM;
    return (x * 5 + y * 3) % 7 === 0 ? COCOA : FOAM;
  }, 2);

  // A drink's icon: what paint gives inside, a dark line round it, steam (not outlined) where steam gives it.
  const drink = (name, paint, steam) => outlinedIcon(name, paint, OUTLINE, (x, y) => (steam(x, y) ? STEAM : null));
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
  drink("babycino", (x, y) => {
    if (y === 14 && x >= 4 && x <= 11) return GLASS_EDGE;
    if (y >= 9 && y <= 13 && x >= 4 && x <= 11) return x === 4 || x === 11 ? GLASS_EDGE : MILK;
    if (y >= 4 && y <= 5 && x >= 5 && x <= 6) return MARSHMALLOW_PINK;
    if (y >= 4 && y <= 5 && x >= 9 && x <= 10) return CREAM;
    if (y >= 6 && y <= 8 && Math.abs(x - 7.5) <= (y === 6 ? 3.5 : 4.5)) return (x * 2 + y) % 5 === 0 ? COCOA : FOAM;
    return null;
  }, wisps(6, 9, 0, 3));

  const coffeeGui = resolve(ROOT, "src/main/resources/assets/skyliners/textures/gui/coffee_machine");
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
// round its top. The back bar's cabinet: a dark front, two smoked glass doors in graphite frames, bottles dimly behind
// them (its chrome rail is a part of its model). Its shelves' back: a smoked mirror glowing warm from its foot up, as
// the LED strip under each tier of bottles lights it, the strips themselves warm white; the shelves' glass edges pale;
// and the bottles, green, amber and clear glass with a pale label, and blue, red and violet liqueurs with none, each
// with a highlight down one side, brightest at the foot where the strip under it lights it; and their corks.
{
  const mix = (a, b, t) => a.map((c, i) => Math.round(c + (b[i] - c) * t));
  const LED_WARM = [0xff, 0xdc, 0xa0];
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
  const GRAPHITE = [0x26, 0x27, 0x2b];
  texture(blockOutput, "back_bar_doors", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15 || x === 7 || x === 8) return GRAPHITE;
    const bottle = (x % 3 === 1 || x % 3 === 2) && y >= 6;
    if (bottle) return x % 6 < 3 ? [0x30, 0x24, 0x18] : [0x1a, 0x28, 0x1e];
    return (x + y) % 7 === 0 ? [0x1c, 0x1e, 0x24] : [0x12, 0x13, 0x16];
  }, 2);
  // Dark smoke down to row 10, warming over the rows under it to the strip's glow at the foot, row 15: each tier's
  // panel shows the lowest rows, as many as the tier is tall, about five.
  texture(blockOutput, "back_bar_mirror", (x, y) => {
    const glow = mix([0x1c, 0x18, 0x16], [0xd0, 0x98, 0x58], Math.max(0, (y - 10) / 5) ** 1.5);
    return (x * 5 + y * 3) % 11 === 0 ? glow.map((c) => Math.min(255, c + 0x14)) : glow;
  }, 1);
  texture(blockOutput, "back_bar_led", () => LED_WARM, 0);
  texture(blockOutput, "back_bar_glass", (x, y) => (y % 4 === 0 ? [0xe8, 0xf6, 0xfc] : [0xb4, 0xd4, 0xe0]), 1);
  // A bottle's glass from its foot, row 15, up: lit warm along its lowest row, and its label, if it has one, in the
  // row over that.
  const BOTTLES = [["green", [0x22, 0x6a, 0x30], true], ["amber", [0xa0, 0x58, 0x16], true], ["clear", [0xc8, 0xdc, 0xe2], true],
    ["blue", [0x2a, 0x52, 0xb0], false], ["red", [0xb4, 0x1e, 0x30], false], ["violet", [0x6c, 0x36, 0xb0], false]];
  for (const [colour, glass, labelled] of BOTTLES) {
    texture(blockOutput, `bar_bottle_${colour}`, (x, y) => {
      if (labelled && y === 14) return x % 4 === 1 ? [0xd0, 0xc4, 0xa8] : [0xe8, 0xdc, 0xc0];
      const shine = x % 4 === 1 ? mix(glass, [0xff, 0xff, 0xff], 0.35) : glass;
      return y === 15 ? mix(shine, LED_WARM, 0.45) : shine;
    }, 1);
  }
  texture(blockOutput, "bar_bottle_cork", (x, y) => ((x * 3 + y * 5) % 7 === 0 ? [0x8a, 0x62, 0x38] : [0xb8, 0x8c, 0x58]), 2);
}

// The robot bartender (see RobotBartenderBlock): its glossy white shell, a faint seam along each panel's top and the
// panel a touch brighter just under it; its chrome, bright and dark bands down it; the graphite of its joints, ribbed;
// its visor's black glass, a sheen across it; its eyes, a cyan strip glowing brightest in two eyes; the cyan glow round
// its foot and on its chest; and what it holds: a glass's pale glass with a highlight down it, the Nebula Fizz it
// pours, violet with bubbles rising, and its polishing cloth, white with a blue stripe. Its mocktails' icons, each in a
// glass of its own and outlined dark, so they read on any background: Nebula Fizz, violet over blue and fizzing in a
// tall highball, sparkles round it; Rocket Fuel, red, orange and amber in a short rocks glass, a flame rising off it;
// Zero-G Float, lime in a footed float glass under a scoop of vanilla ice cream, a red and white straw in it; Supernova
// Sunrise, red, orange and yellow in layers in a footed glass, an orange wheel on its rim and a star bursting beside
// it; and Moon Milk, silvery white with gold stars in it in a round glass, a crescent moon over it.
{
  texture(blockOutput, "robot_bartender_white", (x, y) => {
    if (y === 0) return [0xd4, 0xda, 0xe0];
    const sheen = Math.max(0, 6 - y) * 2;
    return [0xea + sheen, 0xee + sheen, 0xf2 + sheen / 2];
  }, 1);
  texture(blockOutput, "robot_bartender_chrome", (x) => {
    const band = x % 6;
    if (band === 1) return [0xf4, 0xf8, 0xfc];
    return band === 4 ? [0x8c, 0x94, 0x9e] : [0xc4, 0xcc, 0xd4];
  }, 2);
  texture(blockOutput, "robot_bartender_dark", (x, y) => (y % 2 === 0 ? [0x2c, 0x30, 0x36] : [0x3c, 0x42, 0x4a]), 2);
  texture(blockOutput, "robot_bartender_visor", (x, y) => ((x + y) % 9 <= 1 ? [0x34, 0x40, 0x50] : [0x0a, 0x0e, 0x14]), 1);
  // Squeezed onto the visor's narrow glow from side to side, so only its columns show.
  texture(blockOutput, "robot_bartender_eyes", (x) => {
    if ((x >= 2 && x <= 5) || (x >= 10 && x <= 13)) return [0xd8, 0xfa, 0xff];
    return x === 1 || x === 6 || x === 9 || x === 14 ? [0x5c, 0xe4, 0xff] : [0x1c, 0x9c, 0xd0];
  }, 0);
  texture(blockOutput, "robot_bartender_glow", (x, y) => ((x + y) % 4 === 0 ? [0xc8, 0xf8, 0xff] : [0x50, 0xdc, 0xff]), 0);
  texture(blockOutput, "robot_bartender_glass", (x) => (x % 4 === 1 ? [0xf4, 0xfc, 0xff] : [0xc4, 0xe2, 0xee]), 1);
  texture(blockOutput, "robot_bartender_drink", (x, y) => ((x * 5 + y * 3) % 11 === 0 ? [0xec, 0xe4, 0xff] : [0x8a, 0x4a, 0xe8]), 3);
  texture(blockOutput, "robot_bartender_cloth", (x, y) => (y % 6 === 2 ? [0x3a, 0x6c, 0xc8] : [0xf6, 0xf6, 0xf2]), 2);

  const OUTLINE = [0x1c, 0x1a, 0x26];
  const RIM = [0xc8, 0xd8, 0xe4];
  const mix = (from, to, share) => from.map((c, i) => Math.round(c + (to[i] - c) * share));
  // A little star of four points round a pixel, a burst with its corners too.
  const star = (x, y, cx, cy) => (x === cx && Math.abs(y - cy) <= 1) || (y === cy && Math.abs(x - cx) <= 1);
  const burst = (x, y, cx, cy) => {
    const [dx, dy] = [Math.abs(x - cx), Math.abs(y - cy)];
    return (dy === 0 && dx <= 2) || (dx === 0 && dy <= 2) || (dx === 1 && dy === 1);
  };

  const NEBULA_VIOLET = [0xa0, 0x4c, 0xf0];
  const NEBULA_BLUE = [0x34, 0x4a, 0xe0];
  const FIZZ = [0xe8, 0xe0, 0xff];
  const SPARKLE = [0xff, 0xf2, 0xa8];
  const BUBBLES = new Set(["7,11", "8,8", "6,6", "8,5", "7,9"]);
  outlinedIcon("nebula_fizz", (x, y) => {
    if (x < 5 || x > 10 || y < 3 || y > 14) return null;
    if (x === 5 || x === 10 || y === 14) return RIM;
    if (y === 3 || BUBBLES.has(`${x},${y}`)) return FIZZ;
    return mix(NEBULA_VIOLET, NEBULA_BLUE, (y - 4) / 9);
  }, OUTLINE, (x, y) => (star(x, y, 12, 2) || star(x, y, 2, 5) ? SPARKLE : null));

  const FUEL_RED = [0xd8, 0x2c, 0x18];
  const FUEL_ORANGE = [0xf6, 0x7c, 0x1a];
  const FUEL_AMBER = [0xff, 0xbc, 0x34];
  const FLAME_YELLOW = [0xff, 0xe6, 0x5a];
  // The flame's rows, each from and to the columns given, yellow inside its edges from the third row down.
  const FLAME = { 0: [8, 8], 1: [7, 8], 2: [7, 9], 3: [6, 9], 4: [6, 9], 5: [6, 9] };
  outlinedIcon("rocket_fuel", (x, y) => {
    if (y >= 7 && y <= 14 && x >= 3 && x <= 12) {
      if (x === 3 || x === 12 || y >= 13) return RIM;
      return y >= 11 ? FUEL_RED : y >= 9 ? FUEL_ORANGE : FUEL_AMBER;
    }
    const row = FLAME[y];
    if (!row || x < row[0] || x > row[1]) return null;
    return y >= 3 && x > row[0] && x < row[1] ? FLAME_YELLOW : FUEL_ORANGE;
  }, OUTLINE, () => null);

  const LIME = [0x8e, 0xe0, 0x30];
  const LIME_BUBBLE = [0xc8, 0xf4, 0x80];
  const SCOOP = [0xfa, 0xf6, 0xe6];
  const SCOOP_SHADE = [0xe0, 0xd8, 0xc0];
  const STRAW_RED = [0xe0, 0x30, 0x38];
  const STRAW = { "12,0": STRAW_RED, "12,1": SCOOP, "11,2": STRAW_RED, "11,3": SCOOP, "11,4": STRAW_RED };
  // The float glass narrows from four and a half pixels either side of its middle at its rim to two and a half.
  const floatHalf = (y) => 4.5 - ((y - 6) * 2) / 6;
  outlinedIcon("zero_g_float", (x, y) => {
    if (STRAW[`${x},${y}`]) return STRAW[`${x},${y}`];
    if (y <= 6 && Math.hypot((x - 7.5) / 4, (y - 5.5) / 3) <= 1) return y === 6 || x >= 9 ? SCOOP_SHADE : SCOOP;
    if (y === 13) return x >= 7 && x <= 8 ? RIM : null;
    if (y === 14) return x >= 5 && x <= 10 ? RIM : null;
    if (y < 6 || y > 12 || Math.abs(x - 7.5) > floatHalf(y)) return null;
    if (Math.abs(x - 7.5) > floatHalf(y) - 1) return RIM;
    return (x + y) % 5 === 0 ? LIME_BUBBLE : LIME;
  }, OUTLINE, () => null);

  const SUN_RED = [0xdc, 0x22, 0x34];
  const SUN_ORANGE = [0xf6, 0x84, 0x22];
  const SUN_YELLOW = [0xff, 0xd8, 0x3c];
  const WHEEL_PEEL = [0xf0, 0x90, 0x20];
  const WHEEL_FLESH = [0xff, 0xd8, 0x90];
  const BURST = [0xff, 0xf6, 0xc0];
  outlinedIcon("supernova_sunrise", (x, y) => {
    const wheel = Math.hypot(x - 11, y - 4);
    if (wheel <= 2.2) return wheel > 1.2 ? WHEEL_PEEL : WHEEL_FLESH;
    if (y === 13) return x >= 7 && x <= 8 ? RIM : null;
    if (y === 14) return x >= 5 && x <= 10 ? RIM : null;
    if (x < 5 || x > 10 || y < 4 || y > 12) return null;
    if (x === 5 || x === 10 || y === 12) return RIM;
    return y >= 9 ? SUN_RED : y >= 7 ? SUN_ORANGE : SUN_YELLOW;
  }, OUTLINE, (x, y) => (burst(x, y, 2, 3) ? BURST : null));

  const MILK = [0xea, 0xee, 0xf6];
  const MILK_TOP = [0xfa, 0xfc, 0xff];
  const STAR_GOLD = [0xf2, 0xd0, 0x5a];
  const MOON = [0xff, 0xec, 0x9a];
  const MILK_STARS = new Set(["6,9", "9,11", "9,8", "5,11", "7,12"]);
  // A round glass, its bowl cut flat across its wide mouth.
  const inGlass = (x, y) => y >= 7 && y <= 13 && Math.hypot((x - 7.5) / 4.6, (y - 9.8) / 4.2) <= 1;
  outlinedIcon("moon_milk", (x, y) => {
    if (Math.hypot(x - 3.5, y - 3) <= 2.5 && Math.hypot(x - 4.8, y - 2.2) > 2.1) return MOON;
    if (y === 14) return x >= 7 && x <= 8 ? RIM : null;
    if (y === 15) return x >= 5 && x <= 10 ? RIM : null;
    if (!inGlass(x, y)) return null;
    if (!inGlass(x - 1, y) || !inGlass(x + 1, y) || !inGlass(x, y + 1)) return RIM;
    if (!inGlass(x, y - 1)) return MILK_TOP;
    return MILK_STARS.has(`${x},${y}`) ? STAR_GOLD : MILK;
  }, OUTLINE, () => null);
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

// The laser pointer (see LaserPointerItem): a slim pen of dark steel lying from the lower left up to the right, lit
// along its top edge, a red button a little past its middle and a chrome cap at its tip with the red of the laser
// glowing out of it, outlined dark so it shows on any background.
{
  const from = [2.5, 13.5];
  const to = [12.5, 3.5];
  const length = Math.hypot(to[0] - from[0], to[1] - from[1]);
  const along = [(to[0] - from[0]) / length, (to[1] - from[1]) / length];
  const pen = (x, y) => {
    const px = x + 0.5 - from[0];
    const py = y + 0.5 - from[1];
    const t = px * along[0] + py * along[1];
    const side = px * along[1] - py * along[0];
    if (t > length && t <= length + 1.6 && Math.abs(side) < 0.8) return [0xff, 0x30, 0x24];
    if (t < 0 || t > length || Math.abs(side) > 1.2) return null;
    if (t > length - 2) return CHROME;
    if (Math.abs(t - length * 0.6) < 0.8 && side > -0.3) return [0xd8, 0x1c, 0x18];
    return side > 0.3 ? STEEL : STEEL_DARK;
  };
  texture(itemOutput, "laser_pointer", (x, y) => {
    const inside = pen(x, y);
    if (inside) return inside;
    const edge = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => pen(x + dx, y + dy));
    return edge ? FRAME_DARK : CLEAR;
  }, 2);
}

// The self-destruct button (see SelfDestructButtonBlock), drawn so it reads as the button not to press without a word:
// its plate a broad yellow-and-black hazard border round dark steel, its flip lid clear plastic in a frame striped the
// same, so the button shows through it, and the button a deep red dome with a shine on its upper left; lit while armed,
// red-hot with a white-hot middle. In the hand, the whole of it seen from the front.
{
  const stripes = (x, y) => ((x + y) % 4 < 2 ? HAZARD : RUBBER);
  const STEEL_PLATE = [0x3a, 0x3e, 0x44];
  texture(blockOutput, "self_destruct_plate", (x, y) => {
    if (x <= 2 || y <= 2 || x >= 13 || y >= 13) return stripes(x, y);
    if (x === 3 || y === 3 || x === 12 || y === 12) return STEEL_DARK;
    return STEEL_PLATE;
  }, 3);
  texture(blockOutput, "self_destruct_cover", (x, y) => (x <= 2 || y <= 2 || x >= 13 || y >= 13 ? stripes(x, y) : [0xd8, 0xe6, 0xee, 0x48]), 3);
  const dome = (lit) => (x, y) => {
    const out = Math.hypot(x - 7.5, y - 7.5) / 8;
    if (lit) return out < 0.35 ? [0xff, 0xe8, 0xd0] : out < 0.7 ? [0xff, 0x50, 0x30] : [0xe8, 0x20, 0x18];
    if (Math.hypot(x - 5.5, y - 5.5) < 1.6) return [0xe0, 0x70, 0x68];
    return out < 0.75 ? [0xa8, 0x14, 0x12] : [0x78, 0x0c, 0x0c];
  };
  texture(blockOutput, "self_destruct_button", dome(false), 3);
  texture(blockOutput, "self_destruct_button_lit", dome(true), 2);
  texture(itemOutput, "self_destruct_button", (x, y) => {
    if (x < 1 || y < 1 || x > 14 || y > 14) return CLEAR;
    if (x <= 2 || y <= 2 || x >= 13 || y >= 13) return stripes(x, y);
    const out = Math.hypot(x - 7.5, y - 7.5);
    if (Math.hypot(x - 6, y - 6) < 1.2) return [0xff, 0xa0, 0x98];
    return out < 4.1 ? [0xd8, 0x18, 0x14] : STEEL_PLATE;
  }, 2);
}

// A puff of an airliner blowing up (see BlastPuffParticle): a soft, lumpy white cloud, see-through toward its edges, a
// ball in the middle with a few smaller ones bulging out of it, which each kind of puff tints: fire, smoke and dust.
{
  const particleOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/particle");
  mkdirSync(particleOutput, { recursive: true });
  reseed("blast_puff");
  const PUFF = 32;
  const LUMPS = 7;
  const lumps = [[0, 0, 0.6]];
  for (let i = 0; i < LUMPS; i++) {
    const angle = (i + random() * 0.6) * (2 * Math.PI / LUMPS);
    const out = 0.3 + 0.15 * random();
    lumps.push([Math.cos(angle) * out, Math.sin(angle) * out, 0.28 + 0.1 * random()]);
  }
  const pixels = [];
  for (let y = 0; y < PUFF; y++) {
    const row = [];
    for (let x = 0; x < PUFF; x++) {
      const u = ((x + 0.5) / PUFF) * 2 - 1;
      const v = ((y + 0.5) / PUFF) * 2 - 1;
      const density = Math.max(...lumps.map(([cx, cy, r]) => 1 - ((u - cx) ** 2 + (v - cy) ** 2) / (r * r)));
      const a = Math.max(0, Math.min(1, density * 1.6)) * (0.85 + 0.15 * random());
      const shade = Math.round(236 + 19 * Math.max(0, Math.min(1, density)));
      row.push([shade, shade, shade, Math.round(255 * a)]);
    }
    pixels.push(row);
  }
  writeFileSync(resolve(particleOutput, "blast_puff.png"), encodePng(pixels));

  // A puff of a fog machine's fog: a round, smooth fall-off with no grain and no lumps' edges, as the puffs are thin
  // and overlap, and any grain or edge would show through them as a pattern.
  const FOG_PUFF = 64;
  const fogPixels = [];
  for (let y = 0; y < FOG_PUFF; y++) {
    const row = [];
    for (let x = 0; x < FOG_PUFF; x++) {
      const u = ((x + 0.5) / FOG_PUFF) * 2 - 1;
      const v = ((y + 0.5) / FOG_PUFF) * 2 - 1;
      const inside = Math.max(0, 1 - (u * u + v * v));
      row.push([255, 255, 255, Math.round(255 * inside * inside)]);
    }
    fogPixels.push(row);
  }
  writeFileSync(resolve(particleOutput, "fog_puff.png"), encodePng(fogPixels));
}

// The zeppelin. Its cover is cotton doped silver with aluminium powder, as the Hindenburg's was: a soft pale grey with
// the seams of its panels running across it and a faint sheen down the middle of each, finer than the airliner's
// plating. Its rings and struts are duralumin girders lacquered blue, seen through as a lattice: a frame round each
// face braced corner to corner. Its catwalks are aluminium grating, square holes in a plate, open to look down
// through. The propeller's hub is polished steel round a darker boss.
{
  const SILVER = [0xc6, 0xcb, 0xd1];
  const SILVER_SEAM = [0xa9, 0xaf, 0xb7];
  const SILVER_SHEEN = [0xd6, 0xda, 0xdf];
  const fabric = (x, y) => {
    if (y === 0 || y === 8) return SILVER_SEAM;
    if (x === 0) return SILVER_SEAM;
    if (x >= 6 && x <= 9) return SILVER_SHEEN;
    return SILVER;
  };
  texture(blockOutput, "zeppelin_fabric", fabric, 3);
  // The same weave a shade lighter, undoped, for a livery to tint (see Livery): silver tints it back to the cover's own
  // grey, any other livery to its colour.
  const UNDOPED = 1.2;
  const undoped = (x, y) => fabric(x, y).map((c) => clamp(Math.round(c * UNDOPED)));
  texture(blockOutput, "zeppelin_skin", undoped, 3);
  freighterSkinTextures(fabric, UNDOPED, { silver: SILVER, seam: SILVER_SEAM, sheen: SILVER_SHEEN });
  // A rocket's airlock doors' skin outside (see RocketStairsRenderer), tinted as her cover is: the same weave, block
  // for block, over the whole doorway, both leaves side by side as seen from outside, so they are of a piece with her
  // skin round it; each leaf's edges sunk in a dark groove, its top and left catching the light and its foot and right
  // in shadow, so the two read as panels set into her.
  const DOOR = AIRLOCK_DOOR;
  const DOOR_GROOVE = [0x6a, 0x70, 0x78];
  const DOOR_LIT = [0xf2, 0xf4, 0xf6];
  const DOOR_SHADOW = [0xa2, 0xa8, 0xb0];
  texture(blockOutput, "airlock_door_skin", (x, y) => {
    if (y === 0 || y === DOOR - 1 || x === 0 || x === DOOR - 1 || x === DOOR / 2 - 1 || x === DOOR / 2) return DOOR_GROOVE;
    if (y === 1 || x === 1 || x === DOOR / 2 + 1) return DOOR_LIT;
    if (y === DOOR - 2 || x === DOOR - 2 || x === DOOR / 2 - 2) return DOOR_SHADOW;
    return undoped(x % SIZE, y % SIZE);
  }, 3, DOOR, DOOR);
  // The rocket's oval window (see OvalWindowBlock), three blocks wide and four tall. Its glass is a dome (see
  // OvalWindowModel, whose oval this is), drawn from one texture over the whole window at each step of its dimming, at
  // twice a block's pixels: clear in its middle to look out of, it catches more of the sky toward its edge, where it
  // turns away, and a soft highlight sits high on its upper left, as on a bubble. Its glass is a neutral smoked grey,
  // as the cockpit's is, and dimmed it alone darkens a step at a time to near-black. Its steel collar outside, with the
  // dark gasket the glass sits in, and the steel lip round the hole inside are the model's own rings, smooth round the
  // oval: the cover's weave round it outside and the wall round its hole inside, one texture for each block of it from
  // the top left as seen from outside, are cut away under them, and the reveal lining the hole between them is the
  // collar's steel, darkening as it goes in. A window of another size is drawn the same way for its own size.
  const OVAL_RIM = [0xa4, 0xac, 0xb6];
  const OVAL_RIM_LIT = [0xdc, 0xe2, 0xe8];
  const OVAL_DIMS = [[0xb8, 0xc4, 0xcc, 0x38], [0x50, 0x57, 0x5e, 0x88], [0x24, 0x28, 0x2c, 0xc0], [0x0c, 0x0e, 0x10, 0xe8]];
  // How many of the glass texture's pixels a window pixel is across: enough for the highlight's edge to read smooth on
  // the rocket's window; a smaller window, seen as close, takes more of them (its "glassScale").
  const GLASS_SCALE = 4;
  // The sky the glass catches toward its edge, and how much of it at the very edge.
  const OVAL_SKY = [0xdc, 0xec, 0xf8];
  const OVAL_SKY_EDGE = 0.55;
  // The power the dome's height falls by toward its foot, as OvalWindowPieceBlock has it for every window.
  const DOME_PROFILE = 0.6;
  // The highlight: the sun's glint off the dome, from high on the left in front of it, where the dome faces halfway
  // between the sun and whoever looks at it, bright at its heart and fainter round it, each fading in over a soft edge
  // (how squarely the dome faces there, from the edge's outside to its inside).
  const SUN = [-0.5, -0.62, 0.6];
  const GLINT = 0.985;
  const GLINT_HALO = 0.955;
  const GLINT_SOFT = 0.006;
  const HIGHLIGHT = [0xf4, 0xfa, 0xff];
  const HALO_ALPHA = 0x88;
  const GLINT_ALPHA = 0xd8;
  // The windows' sizes, each kept in step with OvalWindowPieceBlock.Size's of the same name, with its textures' names:
  // how many blocks wide and tall, the oval's half width and half height, how far the dome bulges at its middle, and
  // how far outside the oval's edge, in pixels, the weave outside and the wall inside begin: halfway across the collar
  // outside (half its rim) and the lip inside (its hole and half its lip), which OvalWindowModel draws out to its rim
  // and its hole and lip, so every pixel cut away lies under them and every one left stands clear of the glass and the
  // hole.
  const ROCKET_WINDOW = { name: "oval_window", columns: 3, rows: 4, wide: 21, tall: 29, bulge: 6, frameCut: 1.3, insideCut: 2.05 };
  // The porthole (see PortholeBlock), a block across and round, its glass drawn twice as fine, as it is seen close up.
  const PORTHOLE_WINDOW = { name: "porthole", columns: 1, rows: 1, wide: 5.5, tall: 5.5, bulge: 2, frameCut: 0.8, insideCut: 1.75, glassScale: 8 };
  const mix = (a, b, share) => a.map((c, i) => Math.round(c + (b[i] - c) * share));
  const halfway = (() => {
    const sun = Math.hypot(...SUN);
    const h = [SUN[0] / sun, SUN[1] / sun, SUN[2] / sun + 1];
    const length = Math.hypot(...h);
    return h.map((c) => c / length);
  })();
  // A window's glass at each step of its dimming, and the frame and the inner face of each of its blocks, named by its
  // column and row from the top left as seen from outside where it has more than one; gives each block's column, row
  // and where its inner face is wall, for the rocket's rooms to paint.
  const ovalWindowTextures = ({ name, columns, rows, wide, tall, bulge, frameCut, insideCut, glassScale = GLASS_SCALE }) => {
    const middleX = (columns * SIZE) / 2;
    const middleY = (rows * SIZE) / 2;
    // How far a pixel lies outside the oval's edge, in pixels, negative inside: the oval's equation over its slope.
    const outsideOval = (x, y) => {
      const dx = x + 0.5 - middleX;
      const dy = y + 0.5 - middleY;
      const level = (dx / wide) ** 2 + (dy / tall) ** 2 - 1;
      const slope = 2 * Math.hypot(dx / wide ** 2, dy / tall ** 2);
      return level / Math.max(slope, 1e-6);
    };
    // How squarely the dome at a point faces halfway between the sun and the eye: 1 where the glint is brightest.
    const glint = (dx, dy) => {
      const within = Math.max(1 - (dx / wide) ** 2 - (dy / tall) ** 2, 0.05);
      const slope = bulge * DOME_PROFILE * within ** (DOME_PROFILE - 1);
      const normal = [2 * slope * dx / wide ** 2, 2 * slope * dy / tall ** 2, 1];
      const length = Math.hypot(...normal);
      return normal.reduce((sum, c, i) => sum + (c / length) * halfway[i], 0);
    };
    // The glass at a point of the window, in its pixels from its top left as seen from outside; past the oval's edge,
    // where the model draws none, as at the edge, so nothing else bleeds into it.
    const domeGlass = (x, y, tint) => {
      const across = (x - middleX) / wide;
      const down = (y - middleY) / tall;
      const edge = Math.min(1, across ** 2 + down ** 2) ** 2;
      const [r, g, b, a] = tint;
      const glass = [...mix([r, g, b], OVAL_SKY, edge * OVAL_SKY_EDGE), a + (0xe0 - a) * edge * OVAL_SKY_EDGE];
      const facing = glint(x - middleX, y - middleY);
      const halo = Math.min(1, Math.max(0, (facing - GLINT_HALO) / GLINT_SOFT + 0.5));
      const heart = Math.min(1, Math.max(0, (facing - GLINT) / GLINT_SOFT + 0.5));
      const alpha = glass[3] + (HALO_ALPHA - glass[3]) * halo + (GLINT_ALPHA - HALO_ALPHA) * heart;
      const lit = Math.max(halo, heart);
      return [...mix(glass.slice(0, 3), HIGHLIGHT, lit), Math.round(Math.max(glass[3], alpha))];
    };
    OVAL_DIMS.forEach((tint, dim) => texture(blockOutput, `${name}_glass_dim${dim}`,
      (x, y) => domeGlass((x + 0.5) / glassScale, (y + 0.5) / glassScale, tint), 0, columns * SIZE * glassScale, rows * SIZE * glassScale));
    const pieces = [];
    for (let column = 0; column < columns; column++) {
      for (let row = 0; row < rows; row++) {
        // The weave round the oval, outside and in, is the cover's undoped weave for the livery's tint to colour, as
        // the cover's own.
        const piece = columns * rows > 1 ? `${name}_${column}_${row}` : name;
        const out = (x, y) => outsideOval(column * SIZE + x, row * SIZE + y);
        texture(blockOutput, `${piece}_frame`, (x, y) => (out(x, y) > frameCut ? undoped(x, y) : CLEAR), 3);
        texture(blockOutput, `${piece}_inside`, (x, y) => (out(x, y) > insideCut ? undoped(x, y) : CLEAR), 3);
        pieces.push({ column, row, lowest: row === rows - 1, wall: (x, y) => out(x, y) > insideCut });
      }
    }
    return pieces;
  };
  OVAL_WALLS.push(...ovalWindowTextures(ROCKET_WINDOW));
  ovalWindowTextures(PORTHOLE_WINDOW);
  // The collar from its inner edge down (the dark rubber gasket the glass sits in, then polished steel catching the
  // light and dulling toward the skin), the lip round the hole inside from the reveal out, and the reveal from the
  // inner face in, each the same all the way round.
  texture(blockOutput, "oval_window_rim", (x, y) => {
    if (y < 3) return FRAME_DARK;
    if (y === 3) return mix(FRAME_DARK, OVAL_RIM, 0.5);
    return mix(OVAL_RIM_LIT, OVAL_RIM, Math.abs(y - 6) / 9);
  }, 2);
  texture(blockOutput, "oval_window_lip", (x, y) => mix(OVAL_RIM, OVAL_RIM_LIT, y / (SIZE - 1) * 0.6), 2);
  texture(blockOutput, "oval_window_reveal", (x, y) => mix(OVAL_RIM, FRAME_DARK, (y / (SIZE - 1)) * 0.6), 2);
  // In the hand: the window seen from outside, in its steel collar lit along its upper left, the dark gasket inside
  // it, and the smoked glass catching the sky toward its edge with the glint high on its left, outlined dark so it shows
  // on any background.
  const ICON_WIDE = 5.2;
  const ICON_TALL = 7;
  outlinedIcon("oval_window", (x, y) => {
    const dx = x + 0.5 - SIZE / 2;
    const dy = y + 0.5 - SIZE / 2;
    const out = Math.hypot(dx / ICON_WIDE, dy / ICON_TALL);
    if (out > 1) return null;
    if (out > 0.8) return dx + dy < 0 ? OVAL_RIM_LIT : OVAL_RIM;
    if (out > 0.66) return FRAME_DARK;
    if (Math.hypot(dx + 1.4, dy + 2.6) < 1.2) return HIGHLIGHT;
    return mix([0x50, 0x60, 0x6e], OVAL_SKY, (out / 0.66) ** 2 * OVAL_SKY_EDGE);
  }, FRAME_DARK, () => null);
  // In the hand, the Porthole: round, in a collar thicker for its size than the Oval Window's, its glint high on its
  // left.
  const PORTHOLE_ICON = 6.5;
  outlinedIcon("porthole", (x, y) => {
    const dx = x + 0.5 - SIZE / 2;
    const dy = y + 0.5 - SIZE / 2;
    const out = Math.hypot(dx, dy) / PORTHOLE_ICON;
    if (out > 1) return null;
    if (out > 0.72) return dx + dy < 0 ? OVAL_RIM_LIT : OVAL_RIM;
    if (out > 0.58) return FRAME_DARK;
    if (Math.hypot(dx + 1.2, dy + 1.4) < 1) return HIGHLIGHT;
    return mix([0x50, 0x60, 0x6e], OVAL_SKY, (out / 0.58) ** 2 * OVAL_SKY_EDGE);
  }, FRAME_DARK, () => null);
  // The livery board in the control car: six painted swatches on dark wood in a lighter frame, two rows of three as
  // Livery lists them (silver, cream, navy grey; the airliner's white with its blue cheatline, polar red with its white
  // fins, midnight blue with gold stars), each where LiveryBoardBlock lays out its touch panel's tiles to light them.
  const BOARD = [0x5e, 0x3b, 0x1c];
  const BOARD_FRAME = [0x9a, 0x6a, 0x3c];
  const swatches = [
    [(x, y) => (x === 2 ? SILVER_SHEEN : SILVER), (x, y) => [0xef, 0xe0, 0xbc], (x, y) => [0x7e, 0x87, 0x91]],
    [(x, y) => (y === 2 ? [0x2f, 0x5b, 0xc0] : [0xf4, 0xf6, 0xf8]), (x, y) => (y === 4 ? [0xf4, 0xf4, 0xf4] : [0xcc, 0x3a, 0x2e]),
      (x, y) => ((x === 1 && y === 1) || (x === 3 && y === 3) ? [0xff, 0xd8, 0x66] : [0x26, 0x33, 0x5e])],
  ];
  const SWATCH_COLUMNS = [1, 6, 11];
  const SWATCH_ROWS = [2, 9];
  texture(blockOutput, "livery_board_front", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return BOARD_FRAME;
    const column = SWATCH_COLUMNS.findIndex((left) => x >= left && x < left + 4);
    const row = SWATCH_ROWS.findIndex((top) => y >= top && y < top + 5);
    return column >= 0 && row >= 0 ? swatches[row][column](x - SWATCH_COLUMNS[column], y - SWATCH_ROWS[row]) : BOARD;
  }, 2);
  texture(blockOutput, "livery_board_side", (x, y) => (y % 4 === 0 ? BOARD : BOARD_FRAME), 3);
  // The livery selector in the hand: a wide board of the same wood in its frame, and on it a little zeppelin on sky in
  // each livery, in the board's two rows of three: silver, cream and navy grey; white with its blue cheatline, red with
  // a white fin, and midnight blue with a gold star.
  const SELECTOR_SKY = [0xbf, 0xdd, 0xf5];
  const hulls = [[SILVER, [0xef, 0xe0, 0xbc], [0x7e, 0x87, 0x91]], [[0xf4, 0xf6, 0xf8], [0xcc, 0x3a, 0x2e], [0x26, 0x33, 0x5e]]];
  const marks = [[null, null, null], [(x, y) => (y === 2 ? [0x2f, 0x5b, 0xc0] : null), (x, y) => (x === 0 && y === 0 ? [0xf4, 0xf4, 0xf4] : null),
    (x, y) => (x === 1 && y === 1 ? [0xff, 0xd8, 0x66] : null)]];
  texture(itemOutput, "livery_selector", (x, y) => {
    if (y < 3 || y > 12) return CLEAR;
    if (x === 0 || x === 15 || y === 3 || y === 12) return BOARD_FRAME;
    const column = [1, 6, 11].findIndex((left) => x >= left && x < left + 4);
    const row = [4, 8].findIndex((top) => y >= top && y < top + 3);
    if (column < 0 || row < 0) return BOARD;
    const cx = x - [1, 6, 11][column];
    const cy = y - [4, 8][row];
    const hull = cy === 1 || (cx >= 1 && cx <= 2) || (cx === 0 && cy === 0);
    if (!hull) return SELECTOR_SKY;
    const mark = marks[row][column] ? marks[row][column](cx, cy) : null;
    return mark || hulls[row][column];
  }, 0);
  // A festive zeppelin's wreath: evergreen garland, sprigs of dark and light needles with gaps between, a red berry
  // here and there and a glint of gold tinsel; and the red satin of its bow, a sheen across its folds.
  texture(blockOutput, "festive_garland", (x, y) => {
    const sprig = (x * 7 + y * 13 + ((x * y) % 5)) % 11;
    if ((x === 3 && y === 4) || (x === 11 && y === 9) || (x === 6 && y === 13)) return [0xd2, 0x1f, 0x2a];
    if ((x + 2 * y) % 17 === 0) return [0xf0, 0xc4, 0x40];
    if (sprig === 0 || sprig === 5) return CLEAR;
    return sprig % 3 === 0 ? [0x2f, 0x6e, 0x38] : sprig % 3 === 1 ? [0x1d, 0x4f, 0x29] : [0x3e, 0x84, 0x44];
  }, 6);
  texture(blockOutput, "festive_bow", (x, y) => {
    const fold = Math.abs(((x + y) % 8) - 4);
    return fold <= 1 ? [0xf0, 0x5a, 0x5e] : fold <= 2 ? [0xd0, 0x22, 0x2c] : [0xa8, 0x14, 0x1e];
  }, 3);
  // The Christening Bottle: a bottle of wine in dark green glass with a shine down its side, its neck and shoulders
  // wrapped in gold foil, a red ribbon tied round its neck with its tails hanging, a dark line round it all.
  const BOTTLE_GLASS = [0x1f, 0x5a, 0x2e];
  const BOTTLE_SHINE = [0x6f, 0xb0, 0x7a];
  const RIBBON = [0xc8, 0x20, 0x2a];
  const bottle = (x, y) => {
    const across = Math.abs(x + 0.5 - 8);
    if (y >= 1 && y <= 4 && across <= 1) return y === 4 ? RIBBON : GOLD;
    if (y >= 5 && y <= 6 && across <= y - 3) return GOLD;
    if (y >= 7 && y <= 14 && across <= 3) return x === 6 && y <= 12 ? BOTTLE_SHINE : BOTTLE_GLASS;
    if ((x === 10 && y >= 4 && y <= 6) || (x === 11 && y >= 6 && y <= 8)) return RIBBON;
    return null;
  };
  texture(itemOutput, "christening_bottle", (x, y) => {
    const inside = bottle(x, y);
    if (inside) return inside;
    return [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => bottle(x + dx, y + dy)) ? [0x14, 0x24, 0x18] : CLEAR;
  }, 2);
  // The doves released at a christening: white birds seen from the side, in two frames of a wingbeat, wings up and
  // wings down, a grey beak and the underside of the body shaded.
  {
    const particleOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/particle");
    const DOVE = [0xf6, 0xf6, 0xf4];
    const DOVE_SHADE = [0xc4, 0xca, 0xd2];
    const body = (x, y) => ((x - 8) / 3.6) ** 2 + ((y - 8.5) / 1.6) ** 2 <= 1 || Math.hypot(x - 11.6, y - 7) <= 1.3;
    const tail = (x, y) => x >= 3 && x <= 5 && Math.abs(y - 8.5 + (5 - x) * 0.3) <= 0.6 + (5 - x) * 0.35;
    const wing = (up, x, y) => {
      const t = up ? (8 - y) / 6 : (y - 9) / 4;
      return t >= 0 && t <= 1 && Math.abs(x - (8 - t * 1.5)) <= 2.2 * (1 - t) + 0.5;
    };
    for (const [name, up] of [["dove_up", true], ["dove_down", false]]) {
      texture(particleOutput, name, (x, y) => {
        const [cx, cy] = [x + 0.5, y + 0.5];
        if (x === 13 && y === 7) return [0x80, 0x80, 0x84];
        if (body(cx, cy) || tail(cx, cy)) return cy > 9.2 ? DOVE_SHADE : DOVE;
        if (wing(up, cx, cy)) return up ? DOVE : DOVE_SHADE;
        return CLEAR;
      }, 0);
    }
  }
// The "z" rising from a sleeping tardigrade (see SleepParticle): a bold letter in pale blue-white, outlined in a night
// blue so it reads against anything behind it.
{
  const particleOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/particle");
  const Z = ["#######", ".....##", "....##.", "...##..", "..##...", ".##....", "#######"];
  texture(particleOutput, "sleep_z", (x, y) => {
    const at = (px, py) => Z[py - 1]?.[px - 1] === "#";
    if (at(x, y)) return [0xf0, 0xf4, 0xff];
    const near = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]].some(([dx, dy]) => at(x + dx, y + dy));
    return near ? [0x2a, 0x34, 0x6a] : CLEAR;
  }, 0, 9, 9);
  // A glowing spore of the tardigrade's spore drive (see SporeParticle): a round speck, white at its heart, its rim
  // the blue it is tinted.
  texture(particleOutput, "spore", (x, y) => {
    const r = Math.hypot(x - 2, y - 2);
    if (r > 2.3) return CLEAR;
    return r < 1.2 ? [0xff, 0xff, 0xff] : [0xb8, 0xe8, 0xff];
  }, 0, 5, 5);
}
  const GIRDER = [0x2f, 0x5f, 0xb3];
  const GIRDER_DARK = [0x23, 0x48, 0x8a];
  texture(blockOutput, "zeppelin_girder", (x, y) => {
    if (x <= 1 || y <= 1 || x >= 14 || y >= 14) return x === 0 || y === 0 ? GIRDER_DARK : GIRDER;
    if (Math.abs(x - y) <= 1 || Math.abs(x + y - 15) <= 1) return GIRDER;
    return CLEAR;
  }, 3);
  const GRATING = [0xa8, 0xae, 0xb4];
  const GRATING_EDGE = [0x8c, 0x92, 0x99];
  texture(blockOutput, "zeppelin_catwalk", (x, y) => {
    if (x === 0 || y === 0 || x === 15 || y === 15) return GRATING_EDGE;
    if (x % 4 === 0 || y % 4 === 0) return GRATING;
    return CLEAR;
  }, 2);
  // The flying carrier's flight deck on the crown: dark grey non-slip plating, a faint seam round each plate, and the
  // same with a white line down its middle, for its edges and its dashed middle line.
  const DECK = [0x3c, 0x40, 0x46];
  const DECK_SEAM = [0x30, 0x33, 0x38];
  const DECK_LINE = [0xe8, 0xe8, 0xe2];
  const deck = (x, y) => (x === 0 || y === 0 ? DECK_SEAM : DECK);
  texture(blockOutput, "flight_deck", deck, 6);
  texture(blockOutput, "flight_deck_stripe", (x, y) => (x >= 5 && x <= 10 && y > 0 ? DECK_LINE : deck(x, y)), 4);
  // The freighter's engineering floor: a grid of dark steel bars, four slots to a block, lit along their top edges,
  // each slot dark down in it but for a small warm amber glow in its middle, as of her engines' heat below.
  const BAR = [0x46, 0x4c, 0x54];
  const BAR_LIT = [0x6a, 0x72, 0x7c];
  texture(blockOutput, "engine_grating", (x, y) => {
    if (x % 8 === 0 || y % 8 === 0) return y % 8 === 0 && x % 8 !== 0 ? BAR_LIT : BAR;
    const off = Math.max(Math.abs(x % 8 - 4), Math.abs(y % 8 - 4));
    return off === 0 ? [0xe0, 0x80, 0x28] : off === 1 ? [0x8a, 0x40, 0x14] : [0x1e, 0x17, 0x13];
  }, 3);
  // The secret techno room: black speaker cabinets, a big cone and a little horn on their fronts, a grille on their
  // sides; the DJ deck's top with its two turntables and the mixer between them, its sides black with a line of neon;
  // the dance floor's black glass tiles; and the walls' black foam, a pattern of little pyramids.
  const CAB = [0x16, 0x16, 0x1a];
  texture(blockOutput, "speaker_front", (x, y) => {
    const cone = Math.hypot(x + 0.5 - 8, y + 0.5 - 10);
    const horn = Math.hypot(x + 0.5 - 8, y + 0.5 - 3.5);
    if (cone < 1.2) return [0x60, 0x62, 0x6a];
    if (cone < 5.5) return Math.floor(cone) % 2 === 0 ? [0x2a, 0x2a, 0x30] : [0x3a, 0x3a, 0x42];
    if (horn < 1.8) return [0x48, 0x4a, 0x52];
    return x === 0 || y === 0 || x === 15 || y === 15 ? [0x26, 0x26, 0x2c] : CAB;
  }, 1);
  texture(blockOutput, "speaker_side", (x, y) => ((x + y) % 2 === 0 ? [0x20, 0x20, 0x26] : CAB), 1);
  texture(blockOutput, "techno_deck_top", (x, y) => {
    const left = Math.hypot(x + 0.5 - 4, y + 0.5 - 8);
    const right = Math.hypot(x + 0.5 - 12, y + 0.5 - 8);
    const disc = Math.min(left, right);
    if (disc < 0.8) return [0xd0, 0xd0, 0xd8];
    if (disc < 3.3) return Math.floor(disc * 2) % 2 === 0 ? [0x14, 0x14, 0x18] : [0x22, 0x22, 0x28];
    if (x === 8 && y >= 4 && y <= 12) return [0x40, 0x40, 0x48];
    if ((x === 7 || x === 9) && y % 3 === 0 && y >= 4 && y <= 12) return y % 2 === 0 ? [0xff, 0x2b, 0xd6] : [0x2b, 0xe6, 0xff];
    return CAB;
  }, 1);
  texture(blockOutput, "techno_deck_side", (x, y) => (y === 3 ? [0x2b, 0xe6, 0xff] : CAB), 1);
  texture(blockOutput, "dance_floor", (x, y) => (x === 0 || y === 0 ? [0x30, 0x30, 0x3a] : [0x10, 0x10, 0x16]), 1);
  // The freighter's padded bulkhead: off-white quilting, each pad sewn into a diamond with a button at its middle, a
  // grey rib standing up along its left edge, scuffed here and there.
  texture(blockOutput, "padded_bulkhead", (x, y) => {
    if (x === 0) return [0x7c, 0x80, 0x86];
    if (x === 1) return [0x5c, 0x60, 0x66];
    const u = (x + y) % 8;
    const v = (x - y + 16) % 8;
    if (u === 0 || v === 0) return [0xa8, 0xa2, 0x94];
    if (u === 4 && v === 4) return [0x96, 0x90, 0x82];
    const shade = Math.min(u, 8 - u, v, 8 - v);
    return shade >= 2 ? [0xe2, 0xdc, 0xcc] : [0xcc, 0xc6, 0xb6];
  }, 3);
  // The freighter's engine grille (see EngineGrilleBlock): steel slats across a dark frame, the gaps between them dark
  // at rest, glowing blue as her engines idle, and blazing blue-white, the slats lit by it, as her mains push her.
  const GRILLE_GLOWS = [
    { name: "engine_grille", slat: [0x50, 0x54, 0x5c], gap: [0x18, 0x1c, 0x24], core: [0x22, 0x26, 0x30] },
    { name: "engine_grille_idle", slat: [0x30, 0x38, 0x48], gap: [0x16, 0x3a, 0x90], core: [0x2a, 0x62, 0xd0] },
    { name: "engine_grille_full", slat: [0x3c, 0x50, 0x70], gap: [0x3c, 0xb4, 0xff], core: [0xd8, 0xf8, 0xff] },
  ];
  for (const { name, slat, gap, core } of GRILLE_GLOWS) {
    texture(blockOutput, name, (x, y) => {
      if (x === 0 || x === 15) return [0x3a, 0x3e, 0x46];
      const row = y % 4;
      if (row === 0 || row === 1) return row === 0 ? slat : slat.map((c) => Math.round(c * 0.8));
      return row === 2 ? core : gap;
    }, 1);
  }
  // The freighter's deck robot (see DeckRobotBlock): a white body with a teal band round its middle and panel lines;
  // its head teal over a white face; its wheels, chassis, neck and antenna dark grey; its visor a cyan glow, brightest
  // along its middle.
  texture(blockOutput, "deck_robot_body", (x, y) => {
    if (y >= 6 && y <= 9) return y === 6 || y === 9 ? [0x1c, 0x86, 0x8c] : [0x2a, 0xb4, 0xb8];
    if (x === 0 || x === 15 || y === 0 || y === 15) return [0xb8, 0xbe, 0xc4];
    return [0xec, 0xf0, 0xf2];
  }, 2);
  // Its head's faces read rows 2 to 5 of its texture (it stands 10 to 14 pixels up): a teal cap over the top row, white
  // below it, round its visor.
  texture(blockOutput, "deck_robot_head", (x, y) => (y < 3 ? [0x2a, 0xb4, 0xb8] : x === 0 || x === 15 ? [0xb8, 0xbe, 0xc4] : [0xec, 0xf0, 0xf2]), 2);
  texture(blockOutput, "deck_robot_dark", (x, y) => ((x + 2 * y) % 5 === 0 ? [0x30, 0x34, 0x3a] : [0x40, 0x44, 0x4c]), 2);
  texture(blockOutput, "deck_robot_visor", (x, y) => (y >= 7 && y <= 8 ? [0xd8, 0xfc, 0xff] : [0x4c, 0xe0, 0xf4]), 0);
  texture(blockOutput, "club_wall", (x, y) => {
    const u = x % 4;
    const v = y % 4;
    return u + v < 3 ? [0x2a, 0x2a, 0x30] : u > v ? [0x1c, 0x1c, 0x22] : [0x14, 0x14, 0x18];
  }, 1);
  texture(blockOutput, "propeller_hub", (x, y) => {
    const r = Math.hypot(x - 7.5, y - 7.5);
    if (r < 3) return STEEL_DARK;
    if (r < 3.8) return STEEL;
    return x === 0 || y === 0 ? STEEL : CHROME;
  }, 3);
}

// The zeppelin's control car's hand controls: polished brass and varnished wood; the engine telegraph's dial, read
// without words, five wedges from left to right, each a colour and a picture: blue with an arrow pointing back
// (astern), red with a bar across (stop), yellow with one dot (slow), lime with two (half) and green with three
// (full); its base plates red (port) and green (starboard); the burner lever's flame, from a small blue flame at the
// bottom to a big red one at the top; and a ship's wheel for the wheels' items.
{
  const BRASS = [0xd9, 0xb2, 0x4c];
  const BRASS_DARK = [0xa8, 0x82, 0x2c];
  const BRASS_SHINE = [0xf2, 0xd7, 0x84];
  texture(blockOutput, "zeppelin_brass", (x, y) => (x + y) % 7 === 0 ? BRASS_SHINE : y % 5 === 0 ? BRASS_DARK : BRASS, 3);
  const WOOD = [0x8a, 0x5a, 0x33];
  const WOOD_GRAIN = [0x6b, 0x44, 0x26];
  texture(blockOutput, "zeppelin_wood", (x, y) => (y * 3 + Math.round(Math.sin(x / 2) * 2)) % 5 === 0 ? WOOD_GRAIN : WOOD, 4);
  texture(blockOutput, "telegraph_port", (x, y) => x === 0 || y === 0 || x === 15 || y === 15 ? [0x7a, 0x14, 0x14] : [0xc4, 0x24, 0x24], 3);
  texture(blockOutput, "telegraph_starboard", (x, y) => x === 0 || y === 0 || x === 15 || y === 15 ? [0x12, 0x5c, 0x22] : [0x22, 0xa0, 0x3a], 3);
  const WEDGES = [[0x2f, 0x6f, 0xd6], [0xd2, 0x3b, 0x2e], [0xf0, 0xc4, 0x3a], [0x9b, 0xd6, 0x2c], [0x2e, 0xa8, 0x46]];
  const INK = [0xf6, 0xf2, 0xe6];
  const RIM = [0x6b, 0x52, 0x1c];
  // The handle swings in steps of 22.5 degrees, as far as a model's part may turn, from astern 45 degrees to the left
  // to full 45 degrees to the right: each wedge is as wide as a step, round where the handle points.
  const dots = (along, across, count) => [3.2, 4.6, 6].slice(0, count).some((at) => Math.abs(along - at) < 0.6 && Math.abs(across) < 0.6);
  texture(blockOutput, "telegraph_dial", (x, y) => {
    const dx = x + 0.5 - 8;
    const dy = 13 - (y + 0.5);
    const r = Math.hypot(dx, dy);
    if (dy < -1.5 || r > 7.5) return CLEAR;
    if (r > 6.8 || dy < -0.5 || r < 1.5) return RIM;
    const angle = (Math.atan2(dx, Math.max(dy, 0.01)) * 180) / Math.PI;
    if (Math.abs(angle) > 56.25) return RIM;
    const wedge = Math.max(0, Math.min(4, Math.round((angle + 45) / 22.5)));
    const middle = ((-45 + 22.5 * wedge) * Math.PI) / 180;
    const along = dx * Math.sin(middle) + dy * Math.cos(middle);
    const across = dx * Math.cos(middle) - dy * Math.sin(middle);
    const picture = [
      // An arrow pointing back in, toward the hub.
      () => (Math.abs(across) < 0.5 && along > 3 && along < 6.2) || (Math.abs(Math.abs(across) - 0.9) < 0.5 && Math.abs(along - 3.9) < 0.5),
      // A bar across: stop.
      () => Math.abs(along - 4.6) < 0.6 && Math.abs(across) < 1.1,
      () => dots(along, across, 1),
      () => dots(along, across, 2),
      () => dots(along, across, 3),
    ][wedge]();
    return picture ? INK : WEDGES[wedge];
  }, 0);
  texture(blockOutput, "burner_flame", (x, y) => {
    const up = 15 - y;
    const width = 1 + up / 4;
    const dx = Math.abs(x + 0.5 - 8);
    if (dx > width) return [0x2a, 0x2a, 0x2e];
    const t = up / 15;
    return [Math.round(0x30 + 0xcf * t), Math.round(0x70 + 0x40 * (1 - Math.abs(t - 0.5) * 2)), Math.round(0xe0 * (1 - t) + 0x20)];
  }, 2);
  texture(itemOutput, "ships_wheel", (x, y) => {
    const dx = x + 0.5 - 8;
    const dy = y + 0.5 - 8;
    const r = Math.hypot(dx, dy);
    if (r < 1.6) return BRASS;
    if (r > 4.6 && r < 6) return WOOD;
    const a = Math.atan2(dy, dx);
    const spoke = Math.abs(Math.sin(a * 4)) < 0.35 && r < 7.4;
    return spoke ? (r > 6 ? WOOD_GRAIN : WOOD) : CLEAR;
  }, 2);
}

// The picture board on a zeppelin's upper deck: a dark frame, the little screen along its top (its picture drawn by
// its renderer), and six buttons in two rows under it, four pixels wide, the top row three tall and the bottom four,
// where PictureBoardBlock lays out its touch panel's tiles, each painted with what it shows: a landscape for the
// posters, a flag, a map, a face, an arrow climbing, and a red power sign for off.
{
  const FRAME = [0x2a, 0x2f, 0x36];
  const GLASS = [0x14, 0x18, 0x1e];
  const KEY = [0x46, 0x4e, 0x58];
  const SKIN = [0xe8, 0xb8, 0x90];
  const icons = [
    [(x, y) => (y === 0 ? [0x6a, 0xb0, 0xf0] : y === 1 ? (x === 2 ? [0xf0, 0xd0, 0x40] : [0x6a, 0xb0, 0xf0]) : [0x50, 0xa0, 0x50]),
      (x, y) => (x === 0 ? [0xd8, 0xd8, 0xd8] : y === 0 ? [0xd0, 0x30, 0x30] : y === 1 ? [0x30, 0x60, 0xd0] : null),
      (x, y) => ((x === 1 && y === 1) || (x === 2 && y >= 1 && y <= 2) ? [0x8a, 0x5a, 0x2a] : [0xe8, 0xd8, 0xa8])],
    [(x, y) => (y === 0 ? [0x6a, 0x4a, 0x2a] : (y === 1 || y === 3) && (x === 1 || x === 2) ? [0x24, 0x28, 0x40] : SKIN),
      (x, y) => (y === 1 || x === 1 || x === 2) && (y !== 0 || x === 1 || x === 2) ? [0x5b, 0xe0, 0x7a] : null,
      (x, y) => ((x === 0 || x === 3) && y >= 1) || (y === 3 && x >= 1 && x <= 2) || ((x === 1 || x === 2) && y === 0) ? [0xe0, 0x40, 0x40] : null],
  ];
  texture(blockOutput, "picture_board_front", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15 || y === 6 || y === 10) return FRAME;
    if (y < 6) return GLASS;
    if (x % 5 === 0) return FRAME;
    const icon = icons[y < 10 ? 0 : 1][Math.floor(x / 5)];
    return icon((x - 1) % 5, (y - 7) % 4 === 3 && y > 10 ? 3 : y < 10 ? y - 7 : y - 11) ?? KEY;
  }, 1);
  texture(blockOutput, "picture_board_side", () => FRAME, 2);
}

// The flank screens' posters, 128 by 40 pixels, in the flat colours of the travel posters of the airship age: four
// journeys, each with the zeppelin over its destination, and four adverts. The bottom of each is kept plain, as the
// screen sets its headline there in a dark band.
{
  const WIDTH = 128;
  const HEIGHT = 40;
  const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * Math.max(0, Math.min(1, t))));
  const SILVER = [0xd0, 0xd6, 0xde];
  const SILVER_SHADE = [0x98, 0xa0, 0xaa];
  // A zeppelin seen from the side, its nose to the right, so long, its middle at a point: hull, car and fins.
  const zeppelin = (x, y, cx, cy, length) => {
    const dx = (x - cx) / (length / 2);
    const dy = (y - cy) / (length / 7);
    if (dx * dx + dy * dy <= 1) return dy > 0.35 ? SILVER_SHADE : SILVER;
    if (Math.abs(x - (cx + length / 5)) <= length / 14 && y > cy && y <= cy + length / 7 + 1) return SILVER_SHADE;
    const tail = cx - length / 2;
    return x >= tail - 1 && x <= tail + length / 8 && Math.abs(y - cy) <= (tail + length / 8 - x) * 0.6 + 0.5 ? SILVER_SHADE : null;
  };
  const poster = (name, paint) => {
    reseed(`poster_${name}`);
    const pixels = [];
    for (let y = 0; y < HEIGHT; y++) {
      const row = [];
      for (let x = 0; x < WIDTH; x++) {
        row.push([...paint(x, y), 255]);
      }
      pixels.push(row);
    }
    writeFileSync(resolve(miscOutput, `poster_${name}.png`), encodePng(pixels));
  };
  const stars = new Set(Array.from({ length: 60 }, (_, k) => ((k * 7919) % (WIDTH * 26))));
  poster("nether", (x, y) => {
    const ship = zeppelin(x, y, 40, 9, 34);
    if (ship) return ship;
    const ghast = x >= 94 && x < 106 && y >= 4 && y < 16;
    if (ghast) return (y === 8 && (x === 97 || x === 102)) || (y === 12 && x >= 98 && x <= 101) ? [0x50, 0x50, 0x58] : [0xf2, 0xf2, 0xf2];
    if (y >= 16 && y < 22 && (x === 96 || x === 99 || x === 102 || x === 105) && y - 16 < 3 + (x % 3)) return [0xe0, 0xe0, 0xe0];
    const cliff = 30 - Math.abs(Math.sin(x * 0.31) * 6) - (x < 24 ? 12 - x / 2 : 0);
    if (y > cliff && x < 34) return [0x6a, 0x16, 0x16];
    if (y >= 24) return Math.sin(x * 0.4 + y * 1.3) > 0.6 ? [0xff, 0xd0, 0x50] : [0xf0, 0x7a, 0x18];
    return mix([0x3c, 0x08, 0x0c], [0xa8, 0x2c, 0x14], y / 24);
  });
  poster("end", (x, y) => {
    const ship = zeppelin(x, y, 30, 8, 30);
    if (ship) return ship;
    const tower = [[84, 10], [92, 4], [100, 12]].find(([left, top]) => x >= left && x < left + 6 && y >= top);
    if (tower && y < 29) return (x - tower[0]) % 3 === 1 && y % 4 === 1 ? [0xe8, 0xc0, 0xf8] : [0x9a, 0x64, 0xa0];
    const wing = Math.abs(x - 58) < 14 - Math.abs(y - 12) * 2.2 && y >= 8 && y <= 16;
    if (wing) return (x === 66 || x === 67) && y === 12 ? [0xd0, 0x40, 0xf0] : [0x12, 0x10, 0x16];
    if (y >= 27 - Math.abs(Math.sin(x * 0.07)) * 2) return y % 3 === 0 && x % 5 === 0 ? [0xc8, 0xc2, 0x8c] : [0xdc, 0xd8, 0xa0];
    return stars.has(y * WIDTH + x) ? [0xf0, 0xf0, 0xff] : mix([0x0c, 0x06, 0x18], [0x30, 0x18, 0x48], y / 27);
  });
  poster("jungle", (x, y) => {
    const ship = zeppelin(x, y, 100, 7, 30);
    if (ship) return ship;
    const step = Math.floor((y - 8) / 3);
    if (y >= 8 && y < 26 && Math.abs(x - 58) <= 4 + step * 3) return (x + y) % 5 === 0 ? [0x5c, 0x80, 0x48] : [0x86, 0x8c, 0x78];
    if ((x === 44 && y === 12) || (x === 72 && y === 10)) return [0xe8, 0x30, 0x30];
    if ((x === 47 && y === 9) || (x === 76 && y === 14)) return [0x30, 0x70, 0xe8];
    if (y >= 22 + Math.round(Math.sin(x * 0.5) * 2)) return (x * 3 + y * 7) % 4 === 0 ? [0x2c, 0x7c, 0x30] : y > 30 ? [0x1c, 0x5a, 0x24] : [0x3a, 0x98, 0x3c];
    return mix([0x88, 0xcc, 0xee], [0xd0, 0xf0, 0xc8], y / 22);
  });
  poster("peaks", (x, y) => {
    const ship = zeppelin(x, y, 28, 8, 30);
    if (ship) return ship;
    const peak = [[62, 6, 26], [92, 10, 22], [118, 14, 18]].find(([cx, top, half]) => y >= top && Math.abs(x - cx) <= (y - top) * half / 20);
    if ((x === 74 && y === 21) || (x === 75 && y === 20) || (x === 104 && y === 24) || (x === 105 && y === 23)) return [0xfa, 0xfa, 0xfa];
    if (peak) return y < peak[1] + 7 ? [0xfa, 0xfa, 0xff] : x < peak[0] ? [0x8a, 0x98, 0xb0] : [0xa8, 0xb4, 0xc8];
    if (y >= 28) return [0xe8, 0xf0, 0xf8];
    return mix([0x6c, 0xa8, 0xe8], [0xd0, 0xe4, 0xf8], y / 28);
  });
  poster("creeper_insurance", (x, y) => {
    const umbrella = Math.hypot(x - 76, y - 14) <= 22 && y <= 14;
    if (umbrella) return Math.floor(Math.atan2(y - 14, x - 76) * 3) % 2 === 0 ? [0x3c, 0x6c, 0xc8] : [0xf4, 0xf4, 0xf4];
    if (x === 76 && y > 14 && y < 18) return [0x50, 0x40, 0x30];
    if (y >= 18 && y < 22 && Math.abs(x - 76) <= y - 14) return [0xb0, 0x40, 0x30];
    if (y >= 22 && y < 30 && Math.abs(x - 76) <= 8) return x === 74 && y >= 25 ? [0x60, 0x40, 0x28] : [0xd8, 0xc0, 0x90];
    if (x >= 16 && x < 28 && y >= 12 && y < 30) {
      const fx = Math.floor((x - 16) / 3);
      const fy = Math.floor((y - 12) / 3);
      const face = (fy === 1 && (fx === 0 || fx === 3)) || (fy === 2 && (fx === 1 || fx === 2)) || (fy === 3 && fx >= 0 && fx <= 3 && fx !== 1 && fx !== 2 ? false : fy === 3 && (fx === 1 || fx === 2));
      return face ? [0x10, 0x20, 0x10] : (x + y) % 4 === 0 ? [0x6a, 0xb8, 0x4c] : [0x58, 0xa0, 0x40];
    }
    return y >= 30 ? [0x6c, 0xa8, 0x48] : mix([0xfa, 0xe0, 0x90], [0xf4, 0xc8, 0x70], y / 30);
  });
  poster("villager_bank", (x, y) => {
    if (x < 2 || x >= WIDTH - 2 || y < 2) return [0xe0, 0xb8, 0x40];
    const emerald = Math.abs(x - 50) / 12 + Math.abs(y - 15) / 12 <= 1;
    if (emerald) return x < 50 && y < 15 ? [0xa8, 0xf8, 0xc0] : x >= 50 && y >= 15 ? [0x18, 0x90, 0x48] : [0x40, 0xd8, 0x78];
    const vault = Math.hypot(x - 94, y - 15);
    if (vault <= 11) return vault > 9.5 || Math.abs(Math.sin(Math.atan2(y - 15, x - 94) * 3)) < 0.18 || vault < 2 ? [0x60, 0x64, 0x6c] : [0xa0, 0xa6, 0xae];
    return mix([0x10, 0x50, 0x34], [0x18, 0x68, 0x40], y / 40);
  });
  poster("golem_guards", (x, y) => {
    if (x >= 52 && x < 68 && y >= 9 && y < 24) return x >= 57 && x < 63 && y >= 9 && y < 13 ? [0x80, 0x84, 0x7c] : [0xc0, 0xc0, 0xb4];
    if (x >= 56 && x < 64 && y >= 2 && y < 9) return y === 5 && (x === 58 || x === 61) ? [0x60, 0x10, 0x10] : x >= 59 && x < 61 && y >= 6 ? [0xa0, 0x8c, 0x78] : [0xc8, 0xc8, 0xbc];
    if ((x >= 48 && x < 52 && y >= 10 && y < 26) || (x >= 68 && x < 72 && y >= 10 && y < 22)) return [0xb0, 0xb0, 0xa4];
    if ((x >= 54 && x < 58 && y >= 24 && y < 30) || (x >= 62 && x < 66 && y >= 24 && y < 30)) return [0xb0, 0xb0, 0xa4];
    if (Math.hypot(x - 73, y - 20) <= 1.6) return [0xe0, 0x20, 0x24];
    if (x === 73 && y > 21 && y < 26) return [0x30, 0x90, 0x30];
    if (y >= 30) return [0x6c, 0xa0, 0x48];
    return mix([0xf0, 0xec, 0xe0], [0xd8, 0xd0, 0xb8], y / 30);
  });
  poster("cake_by_air", (x, y) => {
    const ship = zeppelin(x, y, 52, 7, 34);
    if (ship) return ship;
    const canopy = Math.hypot(x - 84, y - 15) <= 7 && y <= 15;
    if (canopy) return Math.floor((x - 77) / 3) % 2 === 0 ? [0xe0, 0x30, 0x30] : [0xf8, 0xf8, 0xf8];
    if (y > 15 && y < 21 && (x === 78 + Math.round((y - 15) * 0.5) || x === 90 - Math.round((y - 15) * 0.5))) return [0x60, 0x60, 0x60];
    if (y >= 21 && y < 26 && Math.abs(x - 84) <= 4) return y === 21 ? ((x - 84) % 2 === 0 ? [0xe0, 0x20, 0x30] : [0xfc, 0xf4, 0xf0]) : y === 22 ? [0xfc, 0xf4, 0xf0] : [0xa0, 0x60, 0x30];
    if (y >= 30) return [0x00, 0x01, 0x02].includes((x * 5) % 11) && y === 30 ? [[0xe0, 0x40, 0x40], [0x40, 0x80, 0xe0], [0xf0, 0xc0, 0x30]][x % 3] : [0x68, 0xb0, 0x48];
    return mix([0x7c, 0xbc, 0xf0], [0xc8, 0xe8, 0xfc], y / 30);
  });
}

// A searchlight's lens (see SearchlightBlock): rings of thick glass round a bright middle, warm white and glowing lit,
// dark glass with a faint glint switched off.
{
  const mix = (from, to, t) => from.map((c, i) => Math.round(c + (to[i] - c) * t));
  const lens = (bright) => (x, y) => {
    const r = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
    if (r > 7.5) return [0x1a, 0x1a, 0x1c];
    const ring = Math.floor(r) % 3 === 0;
    if (bright) return ring ? [0xff, 0xe6, 0xaa] : mix([0xff, 0xfb, 0xee], [0xff, 0xec, 0xbb], r / 7.5);
    return ring ? [0x5a, 0x60, 0x68] : (x === 5 && y === 5 ? [0xd0, 0xd8, 0xe0] : [0x2c, 0x32, 0x3a]);
  };
  texture(blockOutput, "searchlight_lens", lens(true), 2);
  texture(blockOutput, "searchlight_lens_off", lens(false), 2);
}

// The skywriting board (see SkywritingBoardBlock, which lays out its touch panel's tiles where they are): four picture
// buttons on deep blue in a wooden frame, each a shape drawn in smoke-white: a circle, a heart, a figure of eight and a
// wave, its top left first. And a puff of the skywriting's smoke (see SkyTrails): a soft round cloud, white so it takes
// any colour, fading out to its edge.
{
  const BLUE = [0x1c, 0x3a, 0x6e];
  const WOOD = [0x5a, 0x40, 0x28];
  const SMOKE = [0xf2, 0xf2, 0xee];
  const ring = (u, v, cu, cv, radius) => Math.abs(Math.hypot(u - cu, v - cv) - radius) < 0.8;
  const heart = (u, v) => {
    const x = (u - 6.5) / 4.6;
    const y = (7.4 - v) / 4.6;
    const inside = (x * x + y * y - 1) ** 3 - x * x * y * y * y <= 0;
    const near = [[-1, 0], [1, 0], [0, -1], [0, 1]].some(([du, dv]) => {
      const nx = (u + du - 6.5) / 4.6;
      const ny = (7.4 - (v + dv)) / 4.6;
      return (nx * nx + ny * ny - 1) ** 3 - nx * nx * ny * ny * ny > 0;
    });
    return inside && near;
  };
  const icons = [
    (u, v) => ring(u, v, 6.5, 6.5, 4.5),
    heart,
    (u, v) => ring(u, v, 4, 6.5, 2.8) || ring(u, v, 9.5, 6.5, 2.8),
    (u, v) => Math.abs(v - (6.5 - 3.5 * Math.sin(((u - 1) / 11) * 3 * Math.PI))) < 0.9 && u >= 1 && u <= 12,
  ];
  texture(blockOutput, "skywriting_board_front", (x, y) => {
    if (x < 2 || x > 29 || y < 2 || y > 29) return WOOD;
    if (x === 15 || x === 16 || y === 15 || y === 16) return WOOD;
    const icon = icons[(y < 16 ? 0 : 2) + (x < 16 ? 0 : 1)];
    return icon((x - 2) % 14, (y - 2) % 14) ? SMOKE : BLUE;
  }, 2, 32, 32);
  texture(blockOutput, "skywriting_board_side", () => WOOD, 3);
  texture(miscOutput, "sky_puff", (x, y) => {
    const r = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
    const a = Math.max(0, 1 - r * r) ** 1.5;
    return [0xff, 0xff, 0xff, Math.round(a * 255)];
  }, 0, 32, 32);
}

// The lift's picture boards' icons (see the lift's textures, below), white on clear, by name: here, for the rocket's
// centre console to draw her lift's arrow up on its flight mode knob's picture plate too.
const LIFT_ICONS = {
  entry: ["................", "................", "....########....", "...#........#...", "...#........#...", "...#........#...",
    "...#........#...", "...#......#.#...", "...#........#...", "...#........#...", "...#........#...", "...#........#...",
    "...#........#...", "..############..", "................", "................"],
  cargo: ["................", "................", "..############..", "..#..........#..", "..#.#......#.#..", "..#..#....#..#..",
    "..#...#..#...#..", "..#....##....#..", "..#....##....#..", "..#...#..#...#..", "..#..#....#..#..", "..#.#......#.#..",
    "..#..........#..", "..############..", "................", "................"],
  galley: ["................", "...#.#.#....##..", "...#.#.#...###..", "...#.#.#...###..", "...#####...###..", "....###....###..",
    ".....#.....###..", ".....#......##..", ".....#.......#..", ".....#.......#..", ".....#.......#..", ".....#.......#..",
    ".....#.......#..", ".....#.......#..", "................", "................"],
  bunks: ["................", "................", "................", "..#.............", "..#.............", "..#.###.........",
    "..#.###########.", "..#.###########.", "..##############", "..##############", "..#............#", "..#............#",
    "................", "................", "................", "................"],
  greenhouse: ["................", "........###.....", ".......#####....", "......######....", ".###..#####.....", "#####.####......",
    "######.#........", ".#####.#........", "...###.#........", ".......#........", ".......#........", "....#######.....",
    "....#######.....", ".....#####......", ".....#####......", "................"],
  pool: ["................", "................", "................", "................", ".##...##...##...", "#..#.#..#.#..#..",
    "....#....#....#.", "................", ".##...##...##...", "#..#.#..#.#..#..", "....#....#....#.", "................",
    ".##...##...##...", "#..#.#..#.#..#..", "....#....#....#.", "................"],
  club: ["................", "........######..", "........######..", "........#....#..", "........#....#..", "........#....#..",
    "........#....#..", "........#....#..", "........#....#..", "........#....#..", "....###.#..###..", "...#####..#####.",
    "...#####..#####.", "....###....###..", "................", "................"],
  lab: ["................", ".....######.....", "......#..#......", "......#..#......", "......#..#......", "......#..#......",
    ".....#....#.....", "....#......#....", "...#........#...", "...#.######.#...", "..#.########.#..", "..#.########.#..",
    "..#.########.#..", "...##########...", "................", "................"],
  observation: ["................", "................", "............##..", "..........####..", "........####....", "......####......",
    "....####........", "..###.##........", "..##..#.#.......", "......#..#......", ".....#....#.....", "....#......#....",
    "...#........#...", "................", "................", "................"],
  cockpit: ["................", ".......##.......", "......####......", ".....######.....", ".....######.....", "....##....##....",
    "....#......#....", "....#......#....", "....########....", "....########....", "....########....", "...##########...",
    "..###.####.###..", "..##..####..##..", "......####......", "................"],
  up: ["................", "................", ".......##.......", "......####......", ".....######.....", "....########....",
    "...##########...", "..############..", ".......##.......", ".......##.......", ".......##.......", ".......##.......",
    ".......##.......", "................", "................", "................"],
  rocket: ["................", ".......##.......", "......####......", "......####......", ".....######.....", ".....######.....",
    ".....##..##.....", ".....##..##.....", ".....######.....", ".....######.....", "....########....", "...##########...",
    "...###.##.###...", "...##..##..##...", "..##...##...##..", "................"],
  star: ["................", ".......##.......", ".......##.......", "......####......", "..############..", "...##########...",
    "....########....", ".....######.....", "....###..###....", "....##....##....", "...##......##...", "................",
    "................", "................", "................", "................"],
  down: ["................", "................", "................", ".......##.......", ".......##.......", ".......##.......",
    ".......##.......", ".......##.......", "..############..", "...##########...", "....########....", ".....######.....",
    "......####......", ".......##.......", "................", "................"],
};

// The rocket's lift (see LiftMachineBlockEntity). Its machine's housing: brushed steel banded with hazard stripes,
// bolted at its corners. Its landing doors: a pane of smoked glass in a brushed steel frame, the frame along the
// lower half's foot and the upper half's head and up both their sides, so the two read as one tall door. Its picture
// boards' fronts, a near-black under what they show, and the backs they share with her LED panels, brushed steel in a
// darker frame. And the boards' icons, white on clear, one for each of her rooms and two arrows: a doorway, a crate, a
// fork and knife, a bed, a sprout, waves, a note, a flask, a telescope, her nose, up and down.
{
  const STEEL = [0x4a, 0x50, 0x58];
  const STEEL_LIGHT = [0x9a, 0xa3, 0xad];
  const STEEL_DARK = [0x2a, 0x2e, 0x33];
  const PANE = [0x14, 0x16, 0x1a, 0x90];
  // Brushed steel, its grain running across in short strokes.
  const BRUSHED = (x, y) => {
    const v = 0xa4 + ((y * 7 + (x >> 2) * 3) % 5) * 4;
    return [v - 6, v, v + 6];
  };
  // The lift's machine: a casing of brushed steel round a band of hazard stripes, its corners bolted.
  texture(blockOutput, "lift_machine", (x, y) => {
    if (x === 0 || y === 0 || x === 15 || y === 15) return STEEL;
    if ((x === 2 || x === 13) && (y === 2 || y === 13)) return STEEL_DARK;
    if (y >= 6 && y <= 9) return (x + y) % 4 < 2 ? [0xe8, 0xb4, 0x20] : [0x1e, 0x1e, 0x22];
    return BRUSHED(x, y);
  }, 3);
  // The backs and edges of her lift's boards and of her LED panels and console, which show through her glass shaft:
  // brushed steel in a darker steel frame, as her space posters are framed.
  texture(blockOutput, "rocket_panel_back", (x, y) => (x === 0 || y === 0 || x === 15 || y === 15 ? STEEL : BRUSHED(x, y)), 3);
  texture(blockOutput, "lift_door_frame", () => STEEL_LIGHT, 2);
  for (const [half, footOrHead] of [["lower", 15], ["upper", 0]]) {
    texture(blockOutput, `lift_door_${half}`, (x, y) => (x <= 1 || x >= 14 || y === footOrHead || (half === "lower" ? y === 14 : y === 1) ? STEEL_LIGHT : PANE), 2);
  }
  texture(blockOutput, "lift_board_casing", () => [0x15, 0x17, 0x1b], 2);
  const liftOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/gui/lift");
  mkdirSync(liftOutput, { recursive: true });
  for (const [name, rows] of Object.entries(LIFT_ICONS)) {
    texture(liftOutput, name, (x, y) => (rows[y][x] === "#" ? [0xff, 0xff, 0xff, 0xff] : [0, 0, 0, 0]), 0);
  }
  // The rover panel's screen (see RoverPanelRenderer): its buttons' pictures in white, as the lift's are, the remote's
  // little screen lit green: the rover over an arrow down to the ground, an arrow up over the rover, and the remote.
  const roverOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/gui/rover");
  mkdirSync(roverOutput, { recursive: true });
  const ROVER = ["............##..", ".............#..", "..############..", "...##########...", "..##...##...##..", "..##...##...##.."];
  const ROVER_PICTURES = {
    deploy: ["................", ...ROVER, "................", ".......##.......", ".......##.......", "....########....", ".....######.....",
      "......####......", ".......##.......", "################", "................"],
    home: ["................", ".......##.......", "......####......", ".....######.....", "....########....", ".......##.......",
      ".......##.......", "................", ...ROVER, "................", "................"],
    remote: ["..........#.....", ".........#......", "........#.......", ".....######.....", ".....#gggg#.....", ".....#gggg#.....",
      ".....######.....", ".....#.##.#.....", ".....######.....", ".....##.#.#.....", ".....######.....", ".....#.##.#.....",
      ".....######.....", ".....######.....", "................", "................"],
  };
  for (const [name, rows] of Object.entries(ROVER_PICTURES)) {
    texture(roverOutput, name, (x, y) => (rows[y][x] === "#" ? [0xff, 0xff, 0xff, 0xff] : rows[y][x] === "g" ? [0x40, 0xe0, 0x60, 0xff] : [0, 0, 0, 0]), 0);
  }
  // The freighter's ramp panel (see BoardingRampRenderer): its button's two pictures, each a little scene in colour, so
  // it says what pressing it does without a word. Both show her grey belly across the top, green ground across the
  // bottom and her ramp, a yellow plate with a dark rim, hinged under her belly at the left. Lower: the ramp reaches
  // down to the ground, and a white arrow sweeps down along its swing. Raise: the ramp swings up toward her belly, a
  // faint ghost where it lay on the ground, and the arrow sweeps up into her.
  const rampOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/gui/ramp");
  mkdirSync(rampOutput, { recursive: true });
  const RAMP_SIZE = 32;
  const HULL = [0xc8, 0xcc, 0xd4, 0xff];
  const HULL_EDGE = [0x6a, 0x70, 0x7a, 0xff];
  const GRASS = [0x5c, 0xc2, 0x4a, 0xff];
  const GRASS_EDGE = [0x3a, 0x8a, 0x30, 0xff];
  const PLATE = [0xf6, 0xd2, 0x3c, 0xff];
  const RIM = [0x3a, 0x30, 0x10, 0xff];
  const GHOST = [0xff, 0xff, 0xff, 0x50];
  const ARROW = [0xff, 0xff, 0xff, 0xff];
  const HINGE = [5, 6];
  // How far a point is from a segment of a line, in pixels.
  const fromSegment = (x, y, [ax, ay], [bx, by]) => {
    const dx = bx - ax;
    const dy = by - ay;
    const t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
    return Math.hypot(x - ax - t * dx, y - ay - t * dy);
  };
  // A point round a middle, so far out, at an angle in degrees (0 to the right, 90 down).
  const round = ([mx, my], radius, angle) => [mx + radius * Math.cos((angle * Math.PI) / 180), my + radius * Math.sin((angle * Math.PI) / 180)];
  // Whether a point is inside a triangle.
  const inTriangle = (x, y, [a, b, c]) => {
    const side = (p, q) => (q[0] - p[0]) * (y - p[1]) - (q[1] - p[1]) * (x - p[0]);
    const s1 = side(a, b);
    const s2 = side(b, c);
    const s3 = side(c, a);
    return (s1 >= 0 && s2 >= 0 && s3 >= 0) || (s1 <= 0 && s2 <= 0 && s3 <= 0);
  };
  // Whether a point is on a curved arrow round the hinge, so far out, from one angle round to another, its head at
  // the second: the way the ramp swings.
  const onArrow = (x, y, radius, from, to) => {
    const way = Math.sign(to - from);
    const angle = (Math.atan2(y - HINGE[1], x - HINGE[0]) * 180) / Math.PI;
    const shaftEnd = to - way * 5;
    const onShaft = Math.abs(Math.hypot(x - HINGE[0], y - HINGE[1]) - radius) < 1.2
      && angle >= Math.min(from, shaftEnd) && angle <= Math.max(from, shaftEnd);
    const head = [round(HINGE, radius, to + way * 3), round(HINGE, radius - 3.5, shaftEnd - way * 3), round(HINGE, radius + 3.5, shaftEnd - way * 3)];
    return onShaft || inTriangle(x, y, head);
  };
  // The ramp, a plate from its hinge to its foot: yellow, a dark rim round it.
  const plate = (x, y, foot) => {
    const off = fromSegment(x, y, HINGE, foot);
    return off < 1.6 ? PLATE : off < 2.5 ? RIM : null;
  };
  // Her belly over the scene and the ground under it.
  const scene = (x, y) => {
    if (y <= 3) return HULL;
    if (y === 4) return HULL_EDGE;
    if (y >= RAMP_SIZE - 3) return y === RAMP_SIZE - 3 ? GRASS_EDGE : GRASS;
    return null;
  };
  const LOWERED = [24, 28];
  const RAISED = [26, 9];
  const lower = (x, y) => {
    const [px, py] = [x + 0.5, y + 0.5];
    return scene(x, y) ?? (onArrow(px, py, 23, 3, 36) ? ARROW : null) ?? plate(px, py, LOWERED) ?? [0, 0, 0, 0];
  };
  const raise = (x, y) => {
    const [px, py] = [x + 0.5, y + 0.5];
    const ghost = fromSegment(px, py, HINGE, LOWERED) < 1.6;
    return scene(x, y) ?? (onArrow(px, py, 25, 44, 9) ? ARROW : null) ?? plate(px, py, RAISED) ?? (ghost ? GHOST : null) ?? [0, 0, 0, 0];
  };
  texture(rampOutput, "lower", lower, 0, RAMP_SIZE, RAMP_SIZE);
  texture(rampOutput, "raise", raise, 0, RAMP_SIZE, RAMP_SIZE);
}

// The rocket's LED strips (see LedStripBlock): a milky diffuser over a row of LEDs, white so that the strip's tint
// gives it its story's colour, a little brighter over each LED, in a pale channel along its edges; its model shows the
// row through the middle. And the glow a lit strip casts on its wall (see LedPanelRenderer): white at the strip, along
// its top edge, fading away to nothing, which the glow's colour tints as it is added onto the wall.
{
  texture(blockOutput, "led_strip", (x, y) => {
    if (y === 0 || y === 15) return [0xc8, 0xcc, 0xd2];
    const middle = 1 - Math.abs(y - 7.5) / 8;
    const led = x % 2 === 0 ? 1 : 0.94;
    const v = Math.round(0xd8 + 0x27 * middle * led);
    return [v, v, v];
  }, 2);
  texture(miscOutput, "led_wash", (x, y) => {
    const v = Math.round(255 * (1 - y / 63) ** 2.2);
    return [v, v, v];
  }, 0, 4, 64);
}

// The space posters on the rocket's lift shaft (see SpacePosterBlock), one texture for each block of a poster, its
// column from the left and its row from the top, in the flat, glowing colours of a science illustration and without a
// word on them: a black hole, its accretion disk running edge-on across it and bent by its gravity into an arch over
// its top and a loop under it; the solar system, half the sun at its left and the planets in a row, sized to be told
// apart rather than to scale; a quasar, its blazing core in a tilted disk, its jets running up and down the poster into
// violet clouds; and Earthrise, a half-lit Earth over the Moon's grey horizon behind a rocket standing on it. Their
// glows are flat colours in steps, ordered-dithered where one runs into the next, each poster in a thin steel frame.
// Its back is plain grey board, and each poster rolled up has an icon of its own picture.
{
  const hex = (value) => [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
  const ramp = (...values) => values.map(hex);
  const unit = (value) => Math.max(0, Math.min(1, value));
  // A 4 by 4 ordered dither: how far a shade must reach past a step at a pixel to take the next one up.
  const BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]];
  const threshold = (x, y) => (BAYER[y & 3][x & 3] + 0.5) / 16;
  // The step of a ramp of flat colours for a shade from 0 to 1 at a pixel, dithered between the two steps round it.
  const shade = (colours, t, x, y) => {
    const level = unit(t) * (colours.length - 1);
    const below = Math.floor(level);
    return colours[Math.min(colours.length - 1, below + (level - below > threshold(x, y) ? 1 : 0))];
  };
  const FRAME_LIGHT = hex(0xb6bcc4);
  const FRAME_SHADE = hex(0x6c727a);
  // Stars scattered over a poster, the same each run: most a single pixel, the brightest with a little cross, the
  // faint ones and the crosses' arms dimmed toward a night blue, so they never look like grey specks.
  const STAR_COLOURS = ramp(0xffffff, 0xd2deff, 0xfff0d8, 0xaab8f0);
  const FAINT = [0.42, 0.48, 0.66];
  const ARMS = [0.5, 0.56, 0.74];
  const dimmed = (colour, by) => colour.map((c, i) => Math.round(c * by[i]));
  const starfield = (name, width, height, count, crosses) => {
    reseed(`space_poster_${name}_stars`);
    const stars = new Map();
    for (let k = 0; k < count; k++) {
      const x = Math.floor(random() * width);
      const y = Math.floor(random() * height);
      const colour = STAR_COLOURS[Math.floor(random() * STAR_COLOURS.length)];
      const faint = random() < 0.55;
      stars.set(y * width + x, faint ? dimmed(colour, FAINT) : colour);
      if (k < crosses && x > 0 && y > 0 && x < width - 1 && y < height - 1) {
        for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) stars.set((y + dy) * width + x + dx, dimmed(colour, ARMS));
      }
    }
    return (x, y) => stars.get(y * width + x);
  };
  // A soft value noise from 0 to 1, the same each run, for land, cloud and the Moon's dust.
  const lattice = (i, j, salt) => {
    let h = Math.imul(i * 374761393 + j * 668265263 + salt * 2147483647, 1274126177);
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
  };
  const noise = (u, v, salt) => {
    const i = Math.floor(u);
    const j = Math.floor(v);
    const fu = u - i;
    const fv = v - j;
    const su = fu * fu * (3 - 2 * fu);
    const sv = fv * fv * (3 - 2 * fv);
    const top = lattice(i, j, salt) + (lattice(i + 1, j, salt) - lattice(i, j, salt)) * su;
    const bottom = lattice(i, j + 1, salt) + (lattice(i + 1, j + 1, salt) - lattice(i, j + 1, salt)) * su;
    return top + (bottom - top) * sv;
  };
  // A poster's icon, as its item shows it rolled up: the picture inside its frame shrunk to fit the icon's frame, each
  // pixel the picture at its middle, so its colours stay as flat as the poster's. Its short side is rounded up, so a
  // picture's middle row is not missed between two of the icon's.
  const icon = (item, width, height, paint, frameLight = FRAME_LIGHT, frameShade = FRAME_SHADE) => {
    const fit = (SIZE - 2) / Math.max(width - 2, height - 2);
    const across = Math.ceil((width - 2) * fit);
    const down = Math.ceil((height - 2) * fit);
    const left = Math.floor((SIZE - across - 2) / 2);
    const top = Math.floor((SIZE - down - 2) / 2);
    texture(itemOutput, item, (x, y) => {
      const u = x - left - 1;
      const v = y - top - 1;
      if (u < -1 || v < -1 || u > across || v > down) return CLEAR;
      if (u === -1 || v === -1) return u === across || v === down ? frameShade : frameLight;
      if (u === across || v === down) return frameShade;
      return paint(1 + Math.floor((u + 0.5) * (width - 2) / across), 1 + Math.floor((v + 0.5) * (height - 2) / down));
    }, 0);
  };
  // A picture painted whole at 16 pixels a block, its frame and all, cut into its blocks.
  const cut = (name, columns, rows, framed) => {
    for (let column = 0; column < columns; column++) {
      for (let row = 0; row < rows; row++) {
        texture(blockOutput, `space_poster_${name}_${column}_${row}`, (x, y) => framed(column * SIZE + x, row * SIZE + y), 0);
      }
    }
  };
  // A poster so many blocks wide and tall, in its thin steel frame, and its icon.
  const poster = (name, columns, rows, paint) => {
    const width = columns * SIZE;
    const height = rows * SIZE;
    cut(name, columns, rows, (x, y) => {
      if (x === 0 || y === 0) return x === width - 1 || y === height - 1 ? FRAME_SHADE : FRAME_LIGHT;
      if (x === width - 1 || y === height - 1) return FRAME_SHADE;
      return paint(x, y);
    });
    icon(`${name}_poster`, width, height, paint);
  };
  texture(blockOutput, "space_poster_back", () => [0x9a, 0x9e, 0xa4], 3);

  // The black hole, four blocks by three. Its shadow is a black disc in a thin white photon ring. Its disk lies
  // edge-on, a band of fire crossing in front of the shadow a little below its middle; the far side of the disk, behind
  // the shadow, is bent by its gravity up over the top into a broad arch and under the bottom into a thinner loop. The
  // band burns white-hot in the middle, through orange to a deep red at its ends, and is brighter on the left, the side
  // coming toward us. Round it a warm violet haze fades into a deep blue-violet sky of stars and soft glows.
  {
    const W = 64;
    const H = 48;
    const CX = 32;
    const CY = 23;
    // The shadow's radius and the photon ring's outer edge, in pixels.
    const SHADOW = 8.4;
    const RING = 9.5;
    // The band: how far it runs out to either side, how thick it is at its middle and how far below the shadow's
    // middle that is, in pixels.
    const BAND_LONG = 30;
    const BAND_HALF = 2.1;
    const BAND_DROP = 1;
    // How broad the arch over the top and the loop under the bottom are at their middles, in pixels, and how bright
    // their inner edges are, as shades of the disk, so the photon ring shows white against them.
    const ARCH = 7.5;
    const LOOP = 2.6;
    const ARCH_BRIGHT = 0.78;
    const LOOP_BRIGHT = 0.6;
    // How much brighter the disk's left end is than its middle, and its right end the dimmer.
    const BEAMING = 0.35;
    // How far the haze reaches out from the photon ring and from the band before it is half gone, in pixels.
    const HAZE_RING = 6;
    const HAZE_BAND = 3;
    const SKY = ramp(0x090722, 0x0f0b30, 0x171044, 0x22145a, 0x34186c, 0x4c1a70, 0x6a1c68, 0x8a2460);
    const DISK = ramp(0x4a0c34, 0x7c1430, 0xae2222, 0xd84616, 0xf4781a, 0xffa830, 0xffd468, 0xfff0c0, 0xffffff);
    const SHADOW_BLACK = hex(0x030208);
    const PHOTON = hex(0xfff8e8);
    const GLOWS = [[9, 9, 9], [56, 39, 11], [52, 6, 6], [11, 41, 7]];
    const star = starfield("black_hole", W, H, 90, 6);
    const beamed = (dx) => 1 - BEAMING * dx / BAND_LONG;
    // How bright the band is at a point from the shadow's middle, as a shade of the disk; 0 off it.
    const band = (dx, dy) => {
      const along = Math.abs(dx) / BAND_LONG;
      const half = BAND_HALF * Math.sqrt(Math.max(0, 1 - along * along));
      const off = Math.abs(dy - BAND_DROP) / Math.max(half, 0.01);
      if (along > 1 || off > 1) return 0;
      return unit((1 - along) ** 0.75 * (1 - off * off * 0.4) * beamed(dx) + 0.06);
    };
    // How bright the far side bent round the shadow is at a point, across the arch or the loop; 0 off them.
    const lensed = (r, dx, dy) => {
      const up = dy < 0;
      const broad = (up ? ARCH : LOOP) * Math.abs(dy / r) ** 0.75;
      const across = (r - RING) / Math.max(broad, 0.01);
      if (across < 0 || across > 1) return 0;
      return unit((1 - across) ** 0.9 * (up ? ARCH_BRIGHT : LOOP_BRIGHT) * beamed(dx) + 0.06);
    };
    poster("black_hole", 4, 3, (x, y) => {
      const dx = x + 0.5 - CX;
      const dy = y + 0.5 - CY;
      const r = Math.hypot(dx, dy);
      const fire = band(dx, dy);
      if (fire > 0) return shade(DISK, fire, x, y);
      if (r <= SHADOW) return SHADOW_BLACK;
      if (r <= RING) return PHOTON;
      const bent = lensed(r, dx, dy);
      if (bent > 0) return shade(DISK, bent, x, y);
      const haze = 0.85 * 0.5 ** ((r - RING) / HAZE_RING) + 0.5 * 0.5 ** (Math.abs(dy - BAND_DROP) / HAZE_BAND) * unit(1 - Math.abs(dx) / 40);
      const glow = GLOWS.reduce((most, [gx, gy, size]) => Math.max(most, 1 - Math.hypot(x - gx, y - gy) / size), 0);
      const sky = 0.08 + y / H * 0.14 + unit(glow) * 0.28 + haze;
      return (haze < 0.3 ? star(x, y) : null) ?? shade(SKY, sky, x, y);
    });
  }

  // The solar system, five blocks by two: half the sun at the left, cut by the frame, in a ring of corona, then
  // Mercury, Venus, Earth and Mars, the asteroid belt, banded Jupiter with its red spot, Saturn in its rings, Uranus
  // and Neptune, each lit from the sun's side, their orbits dotted arcs round it on a deep blue sky of stars.
  {
    const W = 80;
    const H = 32;
    const MIDDLE = 16;
    // The sun's middle, just past the frame, and its radius, in pixels, and how far its corona reaches past it.
    const SUN_X = -1.5;
    const SUN = 14;
    const CORONA = 5;
    const SKY = ramp(0x070b1e, 0x0b1028, 0x101634);
    const ORBIT = hex(0x18204a);
    const SUN_FACE = ramp(0xd8381a, 0xf0661c, 0xff962a, 0xffc04a, 0xffe48a, 0xfff6d2);
    const CORONA_GLOW = ramp(0x101634, 0x3a1a34, 0x6a2a2a, 0xa8441c);
    // Each planet: the column its left edge is in and how many pixels across it is, its lit and its shaded colour.
    const PLANETS = [
      [14, 3, 0xa8a098, 0x5c5652],
      [19, 5, 0xecc88a, 0x96744a],
      [25, 5, 0x3c7ee8, 0x1a3a7a],
      [31, 4, 0xd8622c, 0x7a3016],
      [38, 11, 0xe6cca0, 0x8a6a48],
      [53, 9, 0xe2bc72, 0x8a6c3c],
      [67, 5, 0x9ce2ea, 0x4a8a9a],
      [73, 5, 0x4a6ee8, 0x22327a],
    ];
    const EARTH = 2;
    const JUPITER = 4;
    const SATURN = 5;
    const JUPITER_BANDS = ramp(0xe6cca0, 0xc4905c, 0xf0e0c0, 0xb07848, 0xe6cca0);
    const RED_SPOT = hex(0xc4482a);
    const LAND = hex(0x4aa04a);
    const CLOUD = hex(0xf0f4f8);
    // Saturn's rings: how far out they reach and where they begin, where their darker outer band begins, how open they
    // are seen and how far they are tipped, in pixels and radians.
    const RING_OUT = 7.6;
    const RING_IN = 5.1;
    const RING_OUTER = 6.5;
    const RING_OPEN = 0.5;
    const RING_TIP = -0.2;
    const RINGS = hex(0xf2e4bc);
    const RING_SHADE = hex(0xb4a074);
    const BELT = [[35, 9], [37, 13], [34, 18], [36, 23], [35, 26], [37, 6], [36, 15], [34, 28], [36, 11], [37, 20], [35, 3], [34, 7], [37, 25], [36, 2]];
    const star = starfield("solar_system", W, H, 46, 3);
    // A planet's middle: across a pixel's middle if it is an odd number of pixels across, between two if even, and so
    // down; and the radius its disc is cut to, a little inside its half-width, so a small one is round, not square.
    const middle = ([left, across]) => [left + across / 2, MIDDLE + (across % 2) / 2];
    const cut = ([, across]) => across / 2 - (across % 2 === 1 ? 0.2 : 0.1);
    // How a planet's disc looks at a point from its middle: its own markings, lit on the sun's side.
    const planetAt = (index, u, v, x, y) => {
      const [, across, lit, dark] = PLANETS[index];
      const radius = across / 2;
      let colour = hex(lit);
      if (index === JUPITER) colour = JUPITER_BANDS[Math.min(4, Math.floor((v / radius + 1) * 2.5))];
      if (index === JUPITER && Math.hypot((u - 1.5) / 1.6, v - 1.6) < 1) colour = RED_SPOT;
      if (index === EARTH && noise(u * 1.4 + 3, v * 1.4 + 1, 7) > 0.58) colour = LAND;
      if (index === EARTH && Math.abs(v + 1.5) < 0.5 && u < 1) colour = CLOUD;
      const shaded = hex(dark).map((c, i) => Math.round((c + colour[i] * 0.35) / 1.35));
      return u / radius > 0.15 + threshold(x, y) * 0.4 ? shaded : colour;
    };
    // Saturn's rings at a point from its middle: which band of them is there and whether it is the half behind the
    // planet, or nothing.
    const ringAt = (u, v) => {
      const along = u * Math.cos(RING_TIP) + v * Math.sin(RING_TIP);
      const up = -u * Math.sin(RING_TIP) + v * Math.cos(RING_TIP);
      const out = Math.hypot(along, up / RING_OPEN);
      if (out < RING_IN || out > RING_OUT) return null;
      return { colour: out > RING_OUTER ? RING_SHADE : RINGS, behind: up < 0 };
    };
    const planetHere = (px, py) => PLANETS.findIndex((planet) => {
      const [cx, cy] = middle(planet);
      return Math.hypot(px - cx, py - cy) <= cut(planet);
    });
    const orbit = (sun, x, y) => (x + y) % 2 === 0 && PLANETS.some((planet) => Math.abs(sun - (middle(planet)[0] - SUN_X)) < 0.5);
    poster("solar_system", 5, 2, (x, y) => {
      const px = x + 0.5;
      const py = y + 0.5;
      const sun = Math.hypot(px - SUN_X, py - MIDDLE);
      if (sun <= SUN) return shade(SUN_FACE, 1 - (sun / SUN) ** 2 * 0.95 + (noise(px * 0.9, py * 0.9, 3) - 0.5) * 0.25, x, y);
      const [sx, sy] = middle(PLANETS[SATURN]);
      const ring = ringAt(px - sx, py - sy);
      if (ring && !ring.behind) return ring.colour;
      const planet = planetHere(px, py);
      if (planet >= 0) {
        const [cx, cy] = middle(PLANETS[planet]);
        return planetAt(planet, px - cx, py - cy, x, y);
      }
      if (ring) return ring.colour;
      if (BELT.some(([bx, by]) => bx === x && by === y)) return hex(0x8a847c);
      if (sun <= SUN + CORONA) return shade(CORONA_GLOW, 1 - (sun - SUN) / CORONA, x, y);
      return star(x, y) ?? (orbit(sun, x, y) ? ORBIT : shade(SKY, y / H * 0.6 + unit(1 - (sun - SUN) / 30) * 0.4, x, y));
    });
  }

  // The quasar, three blocks by four, upright: a blazing white core with a four-pointed glint, in a disk tilted across
  // it, white-hot within and red at its rim; its two jets run out of the core up and down the poster, blue-white
  // fading to violet, each ending in a billowing violet cloud, brightest where the jet strikes it. Far off behind it, a
  // few small galaxies among the stars.
  {
    const W = 48;
    const H = 64;
    // The core's middle, on a pixel's middle so its glint runs down a single row and column.
    const CX = 23.5;
    const CY = 31.5;
    // How far the disk is tilted from level, in radians, and its size and flatness, in pixels.
    const TILT = -0.26;
    const DISK_IN = 2.5;
    const DISK_OUT = 14;
    const FLAT = 0.24;
    // The jets: how far out they run from the core, and how wide they are at it and at their ends, in pixels.
    const JET = 20;
    const JET_ROOT = 0.9;
    const JET_END = 2.4;
    // The core's blaze, its glow and its glint, in pixels.
    const CORE = 2.3;
    const GLOW = 9;
    const GLINT = 11;
    const SKY = ramp(0x05061a, 0x090a24, 0x0e0e30);
    const DISK = ramp(0x5a1030, 0x9a1c28, 0xd2401a, 0xf6801e, 0xffbc40, 0xffe490, 0xfffbe8);
    const JETS = ramp(0x241a5e, 0x3c2c9a, 0x4c5ad8, 0x62a0f2, 0xa8e0ff, 0xf0fcff);
    const LOBE = ramp(0x24145a, 0x3c1a78, 0x5c2496, 0x8434b0, 0xb05cc8, 0xe4a4ec);
    const CORE_GLOW = ramp(0x0e0e30, 0x1e2a6a, 0x3a5ab8, 0x7ab4f0, 0xd0f0ff, 0xffffff);
    const GALAXY = hex(0x3a2e66);
    const GALAXY_CORE = hex(0xc8b8f0);
    // The clouds: each puff's middle along its jet, out from the core, and across it, and its size, in pixels; they
    // run together where they meet, as soft clouds do.
    const PUFFS = [[23, 0, 3.2], [25.5, -2.8, 2.8], [25.8, 2.8, 3], [21.4, -3.6, 2.4], [21.6, 3.8, 2.4], [27.6, 0.2, 2.6], [23.4, -5.4, 2], [24, 5.6, 2.1]];
    // Each galaxy: its middle and how far it is turned, in radians.
    const GALAXIES = [[8, 9, 0.5], [40, 51, -0.8], [39, 16, 1.2], [8, 55, -0.3]];
    const star = starfield("quasar", W, H, 60, 4);
    const along = [-Math.sin(TILT), Math.cos(TILT)];
    const level = [Math.cos(TILT), Math.sin(TILT)];
    // How thick the clouds are at a point, out from the core along a jet and across it: past a half, it is cloud.
    const cloud = (s, t) => {
      const reach = Math.abs(s);
      const across = t * Math.sign(s);
      return PUFFS.reduce((sum, [ps, pt, size]) => sum + Math.exp(-((reach - ps) ** 2 + (across - pt) ** 2) / (size * size)), 0);
    };
    // Whether a point is a far galaxy's: its bright middle, or the haze round it, or neither.
    const galaxy = (x, y) => {
      for (const [gx, gy, turn] of GALAXIES) {
        if (x === gx && y === gy) return GALAXY_CORE;
        const u = (x - gx) * Math.cos(turn) + (y - gy) * Math.sin(turn);
        const v = -(x - gx) * Math.sin(turn) + (y - gy) * Math.cos(turn);
        if (Math.hypot(u / 2.6, v / 1.1) <= 1) return GALAXY;
      }
      return null;
    };
    const diskShade = (inPlane) => 1 - (inPlane - DISK_IN) / (DISK_OUT - DISK_IN) * 0.95;
    poster("quasar", 3, 4, (x, y) => {
      const dx = x + 0.5 - CX;
      const dy = y + 0.5 - CY;
      const r = Math.hypot(dx, dy);
      const s = dx * along[0] + dy * along[1];
      const t = dx * level[0] + dy * level[1];
      const inPlane = Math.hypot(t, s / FLAT);
      const disk = inPlane >= DISK_IN && inPlane <= DISK_OUT;
      if (r <= CORE) return CORE_GLOW[5];
      if (disk && s > 0) return shade(DISK, diskShade(inPlane), x, y);
      const glint = Math.max(unit(1 - Math.abs(dx) / GLINT) * (Math.abs(dy) < 0.6), unit(1 - Math.abs(dy) / GLINT) * (Math.abs(dx) < 0.6));
      const glow = Math.max(unit(1 - r / GLOW) ** 1.4, glint * 0.85);
      if (glow > 0.25) return shade(CORE_GLOW, glow, x, y);
      if (disk) return shade(DISK, diskShade(inPlane), x, y);
      const reach = Math.abs(s);
      const wide = JET_ROOT + (JET_END - JET_ROOT) * reach / JET;
      if (reach <= JET && Math.abs(t) <= wide) return shade(JETS, (1 - reach / JET) ** 0.7 * (1 - (t / wide) ** 2 * 0.5) + 0.12, x, y);
      const thick = cloud(s, t);
      const struck = Math.exp(-((reach - JET - 1) ** 2 + t * t) / 9);
      if (thick > 0.5) return shade(LOBE, unit((thick - 0.5) / 1.6) * 0.62 + struck * 0.55 + 0.08, x, y);
      return galaxy(x, y) ?? star(x, y) ?? shade(SKY, glow * 2 + y / H * 0.3, x, y);
    });
  }

  // Earthrise, four blocks by three: the Moon's grey, cratered horizon across the bottom and the Earth rising behind
  // it, lit from the right, blue sea, green and sandy land and swirls of white cloud on its day side, a few city lights
  // on its night side and a thin blue rim of air round its lit edge; and in front of it the rocket, standing on her
  // fins on the Moon, a dark silhouette lit down her sunward edge, red at her nose, a warm light in her porthole and her
  // shadow stretching away over the dust.
  {
    const W = 64;
    const H = 48;
    // The Earth's middle and radius, in pixels, and where its day side begins, from its middle out to its left limb.
    const EX = 34;
    const EY = 20;
    const EARTH = 13;
    const TERMINATOR = -0.12;
    // The horizon: its highest row, the column it is highest at, how far it falls away over the width, in pixels.
    const HORIZON = 32;
    const HORIZON_TOP = 26;
    const HORIZON_FALL = 5;
    // The rocket, drawn from her nose down to her feet: # her, o her porthole. She stands with the column of her middle
    // over this one, her feet on the horizon.
    const ROCKET = [
      "....#....",
      "....#....",
      "...###...",
      "...###...",
      "...###...",
      "...#o#...",
      "...###...",
      "...###...",
      "...###...",
      "...###...",
      "..#####..",
      ".#######.",
      "##.###.##",
      "#...#...#",
    ];
    const ROCKET_X = 40;
    // How many of her rows from the top are her red nose, and how far her shadow reaches over the dust to her left, in
    // pixels.
    const NOSE = 4;
    const SHADOW = 11;
    const SKY = ramp(0x03040a, 0x060812, 0x0a0d1c);
    const SEA = ramp(0x143680, 0x1e4ea8, 0x2a66c8, 0x3c80e0);
    const LAND = ramp(0x2e7a36, 0x4a9644, 0x7aa04e, 0xb8a46a);
    const CLOUD = hex(0xf4f8fc);
    const CLOUD_EDGE = hex(0xbcd0e6);
    const NIGHT = ramp(0x080c1c, 0x0c1630, 0x122048);
    const CITY = hex(0xd8b058);
    const AIR = hex(0x8ccaff);
    const DUST = ramp(0x2a2a30, 0x3e3e46, 0x54545c, 0x6c6c74, 0x86868e, 0xa2a2aa, 0xbcbcc4);
    const HULL = hex(0x0a0b10);
    const HULL_EDGE = hex(0xb8bcc4);
    const NOSE_EDGE = hex(0xd84a3a);
    const PORTHOLE = hex(0xffd878);
    // The craters: each one's middle and radius across, in pixels; they look flatter the nearer the horizon.
    const CRATERS = [[9, 41, 5.5], [29, 44.5, 4], [53, 41.5, 5], [36, 36.5, 2.6], [17, 35.5, 2.2], [59, 36, 1.8], [4, 36.5, 1.8]];
    const star = starfield("earthrise", W, H, 26, 2);
    const horizon = (x) => HORIZON + ((x + 0.5 - HORIZON_TOP) / (W / 2 + 6)) ** 2 * HORIZON_FALL;
    const foot = Math.floor(horizon(ROCKET_X));
    const top = foot - ROCKET.length + 1;
    const half = Math.floor(ROCKET[0].length / 2);
    // What of the rocket is at a pixel: her dark side, her lit right edge, her porthole, or nothing.
    const rocket = (x, y) => {
      const row = ROCKET[y - top];
      const at = row?.[x - ROCKET_X + half];
      if (at === undefined || at === ".") return null;
      if (at === "o") return PORTHOLE;
      if (row[x - ROCKET_X + half + 1] === "#") return HULL;
      return y - top < NOSE ? NOSE_EDGE : HULL_EDGE;
    };
    const earthAt = (u, v, x, y) => {
      const z = Math.sqrt(Math.max(0, 1 - u * u - v * v));
      const day = u + 0.1 * v - TERMINATOR;
      const land = noise(u * 1.9 + 2.2, v * 1.9 + 6.1, 13);
      if (day < (threshold(x, y) - 0.5) * 0.16) {
        return land > 0.58 && lattice(x, y, 31) > 0.88 ? CITY : shade(NIGHT, 0.3 + unit(1 + day * 2.5) * 0.7, x, y);
      }
      const swirl = noise(u * 2.4 + 14, v * 4.2 + 5 + Math.sin(u * 3) * 0.6, 21);
      if (swirl > 0.64) return CLOUD;
      if (swirl > 0.6) return CLOUD_EDGE;
      if (land > 0.56) return shade(LAND, (land - 0.56) * 4, x, y);
      return shade(SEA, z * 0.85 + unit(day) * 0.25, x, y);
    };
    // How bright the dust is in a crater at a point: the sun being to the right, its far right inner wall in shadow,
    // its left inner wall and its right outer rim lit; null outside every crater.
    const craterAt = (px, py) => {
      for (const [cx, cy, radius] of CRATERS) {
        const flat = 0.3 + (cy - HORIZON) / (H - HORIZON) * 0.3;
        const u = (px - cx) / radius;
        const v = (py - cy) / (radius * flat);
        const out = Math.hypot(u, v);
        if (out <= 1) return Math.hypot(u + 0.4, v) <= 1 ? 0.5 : 0.1;
        if (out <= 1.35 && u > 0.2) return 0.92;
      }
      return null;
    };
    const ground = (x, y, px, py) => {
      const crater = craterAt(px, py);
      if (crater !== null) return shade(DUST, crater, x, y);
      if (y === foot && x < ROCKET_X - half && x >= ROCKET_X - half - SHADOW) return DUST[1];
      const depth = (py - horizon(x)) / (H - HORIZON);
      return shade(DUST, 0.8 - depth * 0.42 + (noise(px * 0.22, py * 0.6, 5) - 0.5) * 0.18, x, y);
    };
    poster("earthrise", 4, 3, (x, y) => {
      const px = x + 0.5;
      const py = y + 0.5;
      const ship = rocket(x, y);
      if (ship) return ship;
      if (py >= horizon(x)) return ground(x, y, px, py);
      const u = (px - EX) / EARTH;
      const v = (py - EY) / EARTH;
      const r = Math.hypot(u, v);
      if (r <= 1) return earthAt(u, v, x, y);
      if (r <= 1 + 1.1 / EARTH && u + 0.1 * v > TERMINATOR + 0.1) return AIR;
      return star(x, y) ?? shade(SKY, py / H, x, y);
    });
  }

  // The fleet's paintings, each ship hung aboard the other two (see SpacePoster): oils rather than a science
  // illustration's flat colours, in a walnut moulding round a gilt slip, and as without words as the posters. The
  // airliner climbs out through a sunset over gold-lit cloud, her back catching the sun, her two rows of windows, her
  // blue fin and her cheatline as she flies them, two contrails drawn out behind her. The zeppelin rides a pale dawn
  // over violet mountains and dark hills, silver with her rings showing, the row of lamps along her back lit, her
  // control car under her bow and her engine cars under her flank. The rocket lifts off at night in her midnight blue,
  // her portholes and gold stars on her, from a pad lit by her own cyan flame and the smoke rolling out from under her,
  // the tower at her side.
  const WALNUT = hex(0x3a2414);
  const GILT = hex(0xd6a846);
  const GILT_SHADE = hex(0x8c6824);
  // How far a brush's dab strays from the shade it was meant to be, as a share of its whole ramp of colours.
  const BRUSH = 0.2;
  const painting = (name, columns, rows, paint) => {
    const width = columns * SIZE;
    const height = rows * SIZE;
    cut(name, columns, rows, (x, y) => {
      const fromEdge = Math.min(x, y, width - 1 - x, height - 1 - y);
      if (fromEdge === 0) return WALNUT;
      if (fromEdge === 1) return x === width - 2 || y === height - 2 ? GILT_SHADE : GILT;
      return paint(x, y);
    });
    icon(`${name}_painting`, width, height, paint, GILT, WALNUT);
  };
  // A dab of paint: the step of a ramp for a shade, strayed along long level brushstrokes rather than dithered.
  const dab = (colours, t, x, y, salt) => colours[Math.round(unit(t + (noise(x * 0.14, y * 0.55, salt) - 0.5) * BRUSH) * (colours.length - 1))];
  // Whether a point lies inside a shape drawn as its corners in turn.
  const within = (corners, s, v) => {
    let inside = false;
    for (let i = 0, j = corners.length - 1; i < corners.length; j = i++) {
      const [si, vi] = corners[i];
      const [sj, vj] = corners[j];
      if (vi > v !== vj > v && s < sj + (si - sj) * (v - vj) / (vi - vj)) inside = !inside;
    }
    return inside;
  };

  // The airliner, five blocks by three, climbing away to the right. She is drawn along her own length, s toward her
  // nose from her middle and v down from her axis, turned up by her climb.
  {
    const H = 48;
    // Her middle, in pixels.
    const CX = 41;
    const CY = 19;
    // How steeply she climbs, in radians.
    const CLIMB = 0.13;
    // How far her nose's rounding begins and her nose's tip lie from her middle, where her tail begins to narrow and
    // her tail's end, how deep she is either side of her axis, and how far her tail sweeps up over its length, in
    // pixels.
    const NOSE_FROM = 22;
    const NOSE_TIP = 29;
    const TAIL_FROM = -14;
    const TAIL_END = -30;
    const DEEP = 4.6;
    const SWEEP = 3.2;
    const SKY = ramp(0x2c2f6c, 0x47407f, 0x6e5290, 0xa2608e, 0xd47678, 0xee9468, 0xf8b46c, 0xfcd48e);
    const CLOUD = ramp(0xcf8a8a, 0xeaa98e, 0xf8cca4, 0xfee8c8, 0xfff8ea);
    const SKIN = ramp(0xfff4e0, 0xe8e6e2, 0xc2c8d2, 0x98a2b4, 0x737d92);
    const WINDOW = hex(0x2a3c58);
    const COCKPIT = hex(0x1c2636);
    const CHEATLINE = hex(0x2f5bc0);
    const FIN = ramp(0x3f6fd8, 0x2a52b4, 0x1a3684);
    const WING = ramp(0xf6efe2, 0xd4d4d6, 0xa4aab8);
    const FAR_WING = hex(0x8a7f98);
    const NACELLE = ramp(0xf4f0e8, 0xb8bec8, 0x7c8596);
    const INTAKE = hex(0x20242c);
    const TRAIL = hex(0xfff2dc);
    const NEAR_WING = [[8, 3], [-6, 3.8], [-21, 14], [-18.5, 14]];
    const OTHER_WING = [[1, -3.6], [-6, -3.6], [-16, -9.5], [-14, -9.5]];
    const FIN_SHAPE = [[-17, -3.6], [-25.5, -2.6], [-32.5, -13.5], [-27.5, -13.5]];
    const TAILPLANE = [[-23, -1.4], [-28, -1.8], [-34, 2.4], [-31.5, 2.4]];
    // Her near wing's engines: where each one's middle is, and half its length and depth, in pixels.
    const ENGINES = [[0.5, 8.4], [-8.5, 12]];
    const ENGINE_LONG = 3.6;
    const ENGINE_DEEP = 1.6;
    const axis = (s) => (s < TAIL_FROM ? -((TAIL_FROM - s) / (TAIL_FROM - TAIL_END)) * SWEEP : 0);
    const depth = (s) => {
      if (s > NOSE_TIP || s < TAIL_END) return 0;
      if (s > NOSE_FROM) return DEEP * Math.sqrt(1 - ((s - NOSE_FROM) / (NOSE_TIP - NOSE_FROM)) ** 2);
      return s < TAIL_FROM ? DEEP * (1 - ((TAIL_FROM - s) / (TAIL_FROM - TAIL_END)) * 0.72) : DEEP;
    };
    // What of her body is at a point: her cockpit, a window, her cheatline, or her skin shaded round her.
    const body = (s, v, x, y) => {
      const half = depth(s);
      const across = v - axis(s);
      if (half === 0 || Math.abs(across) > half) return null;
      if (s > 23.5 && s < 27.2 && across > -2.6 && across < -0.7) return COCKPIT;
      const inWindows = s > -19 && s < 21 && Math.floor(s + 40) % 2 === 0;
      if (inWindows && (Math.abs(across + 1.9) < 0.5 || Math.abs(across - 0.8) < 0.5)) return WINDOW;
      if (s < 21 && across > 1.6 && across < 2.9) return CHEATLINE;
      return dab(SKIN, 0.5 + across / half * 0.5, x, y, 41);
    };
    const engine = (s, v) => {
      for (const [middle, level] of ENGINES) {
        const along = (s - middle) / ENGINE_LONG;
        if (Math.abs(along) <= 1 && Math.abs(v - level) <= ENGINE_DEEP * Math.sqrt(1 - along * along * 0.6)) {
          if (along > 0.72) return INTAKE;
          return NACELLE[Math.round(unit((v - level + ENGINE_DEEP) / (ENGINE_DEEP * 2)) * 2)];
        }
      }
      return null;
    };
    // A contrail's strength at a point, drawn out behind an engine and fading as it goes, or 0.
    const trail = (s, v) => {
      let strength = 0;
      for (const [middle, level] of ENGINES) {
        const behind = middle - ENGINE_LONG - s;
        if (behind > 1 && Math.abs(v - level) <= 0.5) strength = Math.max(strength, 1.3 - behind / 34);
      }
      return strength;
    };
    const sky = (x, y) => {
      const height = (y + 0.5) / H;
      const sun = Math.exp(-(((x - 66) / 26) ** 2 + ((y - 37) / 15) ** 2));
      const billow = noise(x * 0.085 + 3, y * 0.24, 7) + noise(x * 0.2, y * 0.5, 9) * 0.32;
      const cloud = billow + (height - 0.68) * 1.7;
      if (cloud > 0.82) return dab(CLOUD, 0.25 + (cloud - 0.82) * 1.5 + sun * 0.4 - (height - 0.7) * 0.4, x, y, 3);
      return dab(SKY, height ** 1.1 + sun * 0.2, x, y, 5);
    };
    painting("airliner", 5, 3, (x, y) => {
      const dx = x + 0.5 - CX;
      const dy = y + 0.5 - CY;
      const s = dx * Math.cos(CLIMB) - dy * Math.sin(CLIMB);
      const v = dx * Math.sin(CLIMB) + dy * Math.cos(CLIMB);
      const nacelle = engine(s, v);
      if (nacelle) return nacelle;
      if (within(NEAR_WING, s, v)) return WING[Math.round(unit((8 - s - v * 0.4) / 13) * 2)];
      if (within(TAILPLANE, s, v)) return WING[1];
      const skin = body(s, v, x, y);
      if (skin) return skin;
      if (within(FIN_SHAPE, s, v)) return FIN[Math.round(unit((-s - 17 - (v + 13.5) * 0.4) / 12) * 2)];
      if (within(OTHER_WING, s, v)) return FAR_WING;
      const streak = trail(s, v);
      if (streak > threshold(x, y)) return TRAIL;
      return sky(x, y);
    });
  }

  // The zeppelin, five blocks by three, nose to the left, seen from a little under her.
  {
    const H = 48;
    // The columns of her nose and her tail, the row of her axis and how deep she is either side of it, in pixels.
    const NOSE = 5;
    const TAIL = 70;
    const SPINE = 17;
    const DEEP = 8;
    // How far along her she is at her deepest, as a share of her length.
    const DEEPEST = 0.34;
    const SKY = ramp(0x4a7cb0, 0x6c9cc4, 0x9cbcd0, 0xd0d4cc, 0xf2d8b6, 0xf8c8a0);
    const FAR_HILLS = ramp(0x8f8fb6, 0x77759e);
    const NEAR_HILLS = ramp(0x4a5e62, 0x33484a, 0x24383a);
    const SILVER = ramp(0xfbf8ee, 0xe2e4e4, 0xc2c8ce, 0x9aa2ae, 0x747e8e, 0x586274);
    // How much darker a ring of her frame shows, as a share of that ramp, and how many columns apart they stand.
    const RING = 0.13;
    const RING_SPACING = 6;
    const FIN = ramp(0x8a94a4, 0x5c6678);
    const FIN_TIP = hex(0xc4382c);
    const BAND = hex(0xc4382c);
    const LAMP = hex(0xfff2b4);
    const CAR = ramp(0x8c95a2, 0x5a6476);
    const CAR_WINDOW = hex(0xfff0c0);
    const SHADOW = hex(0x2c3442);
    const PROPELLER = hex(0xd8dce0);
    // Her control car: its first and last columns, and its last row, the one above that being its windows'.
    const CAR_FROM = 13;
    const CAR_TO = 21;
    const CAR_LOW = 28;
    // Her fins' shapes and her engine cars' first columns, in pixels.
    const TOP_FIN = [[TAIL - 17, SPINE - 4], [TAIL - 8, SPINE - 1], [TAIL + 1, SPINE], [TAIL + 2, SPINE - 9], [TAIL - 5, SPINE - 9]];
    const LOW_FIN = [[TAIL - 17, SPINE + 4], [TAIL - 8, SPINE + 1], [TAIL + 1, SPINE], [TAIL + 2, SPINE + 9], [TAIL - 5, SPINE + 9]];
    const ENGINE_CARS = [40, 51];
    const half = (x) => {
      const along = (x - NOSE) / (TAIL - NOSE);
      if (along < 0 || along > 1) return 0;
      if (along < DEEPEST) return DEEP * Math.sqrt(1 - (1 - along / DEEPEST) ** 2);
      return DEEP * (1 - ((along - DEEPEST) / (1 - DEEPEST)) ** 2 * 0.94);
    };
    // A range of hills' top at a column: the higher a row's number, the lower it stands.
    const ridge = (x, base, rise, grain, salt) => base - noise(x * grain, salt, salt) * rise - noise(x * grain * 2.7, salt, salt + 1) * rise * 0.35;
    const land = (x, y) => {
      if (y >= ridge(x, H + 1, 13, 0.1, 21)) return dab(NEAR_HILLS, (y - 34) / 13, x, y, 22);
      if (y >= ridge(x, H - 5, 11, 0.075, 23)) return dab(FAR_HILLS, (y - 30) / 10, x, y, 24);
      return null;
    };
    const hull = (x, y) => {
      const reach = half(x);
      const across = y + 0.5 - SPINE;
      if (reach === 0 || Math.abs(across) > reach) return null;
      const down = across / reach;
      if (Math.abs(across + reach) < 0.8 && x % 3 === 0) return LAMP;
      if (x > 20 && x < 38 && down > 0.58 && down < 0.86) return BAND;
      const ring = (x + 1) % RING_SPACING === 0 ? RING : 0;
      return dab(SILVER, 0.2 + (down + 0.55) * 0.5 + ring, x, y, 25);
    };
    const car = (x, y) => {
      const keel = Math.ceil(SPINE + half(x));
      if (x >= CAR_FROM && x <= CAR_TO && y >= keel && y <= CAR_LOW) {
        return y === CAR_LOW - 1 && x > CAR_FROM && x < CAR_TO && x % 2 === 0 ? CAR_WINDOW : CAR[y === CAR_LOW ? 1 : 0];
      }
      for (const left of ENGINE_CARS) {
        const row = Math.ceil(SPINE + half(left + 2)) + 2;
        if (x >= left && x <= left + 3 && y >= row && y <= row + 1) return CAR[y - row];
        if (x === left + 1 && y >= row - 2 && y < row) return SHADOW;
        if (x === left + 4 && y >= row - 1 && y <= row + 2) return PROPELLER;
      }
      return null;
    };
    painting("zeppelin", 5, 3, (x, y) => {
      const px = x + 0.5;
      const py = y + 0.5;
      const silver = hull(x, y);
      if (silver) return silver;
      const below = car(x, y);
      if (below) return below;
      if (within(TOP_FIN, px, py) || within(LOW_FIN, px, py)) return px > TAIL ? FIN_TIP : FIN[py > SPINE ? 1 : 0];
      return land(x, y) ?? dab(SKY, (py / H) ** 0.9 * 1.15, x, y, 26);
    });
  }

  // The rocket, five blocks by three, lifting off at night: the sky shading up from a burning horizon under a low
  // moon, the smoke in puffs rolling out across the pad from under her, lit by her flame near her.
  {
    const W = 80;
    const H = 48;
    // The column of her axis, the rows of her nose's tip, of where her nose meets her body, of her foot and of the
    // horizon, and how far her body reaches out from her axis, in pixels.
    const AXIS = 30;
    const TIP = 3;
    const SHOULDER = 11;
    const FOOT = 33;
    const HORIZON = 41;
    const WIDE = 5;
    const SKY = ramp(0x05071a, 0x0a0e2c, 0x141742, 0x2a2058, 0x54306c, 0x8c4470, 0xd0664e, 0xf49a4c);
    const HULL = ramp(0x6880cc, 0x4458a4, 0x2e3c7c, 0x1e2a5e, 0x141c44);
    const FINS = ramp(0x4a60ac, 0x263468, 0x18224e);
    const BAND = hex(0x080a14);
    const BAND_GLASS = hex(0x7c9cc8);
    const PORTHOLE = hex(0xe6e6d8);
    const STAR_GOLD = hex(0xeec25a);
    const FLAME = ramp(0xffffff, 0xc8fbff, 0x5ae8f4, 0x2098c8);
    const SMOKE = ramp(0xfff8e6, 0xf6dcb4, 0xd8b8a0, 0xa8908c, 0x78707e, 0x4e4c66);
    const STEEL = ramp(0xc8ccd4, 0x8a92a4, 0x565c78);
    const TOWER_TOP = hex(0xc4382c);
    const GROUND = ramp(0x2a2438, 0x1a1828);
    const MOON = ramp(0xfff6d8, 0xe6dcc0);
    // Her fins' shapes, drawn out from her axis.
    const NEAR_FIN = [[-1.2, 24], [1.2, 24], [1.5, 38], [-1.5, 38]];
    const LEFT_FIN = [[-4.5, 20], [-4.5, 32], [-11, 38], [-12, 33]];
    const RIGHT_FIN = [[4.5, 20], [4.5, 32], [11, 38], [12, 33]];
    // Her tower: its first and last columns, the row it rises to, how many rows its red top takes, and the row of the
    // arm reaching over to her.
    const TOWER_LEFT = 42;
    const TOWER_RIGHT = 48;
    const TOWER_FROM = 5;
    const TOWER_TOP_ROWS = 3;
    const ARM = 13;
    // The moon's middle and radius, in pixels, and how far across it the shadow that makes it a crescent lies.
    const MOON_X = 66;
    const MOON_Y = 11;
    const MOON_SIZE = 5;
    const MOON_SHADOW = 2.6;
    // Her portholes and gold stars, across her from -1 to 1 and in rows; and the smoke's puffs: each one's middle out
    // from her and row, and its radius, in pixels.
    const PORTHOLES = [[-0.5, 14], [0.5, 18], [-0.5, 22], [0.5, 26], [-0.5, 30]];
    const STARS = [[0.3, 16], [-0.3, 20], [0.3, 24]];
    const PUFFS = [
      [-26, 44, 5.5], [-18, 42, 6], [-10, 41, 5.5], [-3, 43, 5], [5, 41, 5.5], [13, 42, 6], [21, 43, 5.5], [30, 44, 6],
      [39, 45, 5.5], [47, 45, 5.5], [-22, 47, 5], [-8, 47, 5.5], [6, 47, 5.5], [20, 47, 5.5], [34, 48, 5.5], [46, 48, 5],
    ];
    const star = starfield("rocket_painting", W, H, 34, 3);
    const radius = (y) => {
      if (y < TIP || y > FOOT) return 0;
      if (y < SHOULDER) return WIDE * (1 - (1 - (y - TIP) / (SHOULDER - TIP)) ** 2) ** 0.75;
      return WIDE * (1 - (y - SHOULDER) / (FOOT - SHOULDER) * 0.12);
    };
    const near = (spots, u, y, r, size) => spots.some(([across, row]) => Math.abs(y - row) + Math.abs(u - across) * r <= size);
    const hull = (x, y, px, py) => {
      const r = radius(py);
      const u = (px - AXIS) / (r || 1);
      if (r === 0 || Math.abs(u) > 1) return null;
      if (y === 7 || y === 10) return Math.abs(u) < 0.5 && y === 10 ? BAND_GLASS : BAND;
      if (near(PORTHOLES, u, y, r, 1)) return PORTHOLE;
      if (near(STARS, u, y, r, 0.5)) return STAR_GOLD;
      return dab(HULL, 0.05 + (u + 1) * 0.46, x, y, 31);
    };
    // How bright her flame is at a point, from where it leaves her down into the smoke, or 0 outside it.
    const flame = (px, py) => {
      const down = (py - FOOT) / (HORIZON + 2 - FOOT);
      if (down < 0 || down > 1) return 0;
      const wide = 3 - down * 1.2;
      return Math.abs(px - AXIS) <= wide ? 1 - Math.abs(px - AXIS) / wide * 0.65 - down * 0.2 : 0;
    };
    // What of the smoke is at a point: the nearest puff to the viewer that holds it, lit from over it and near her
    // flame, the later puffs lying in front; or nothing.
    const smoke = (x, y, px, py) => {
      for (let k = PUFFS.length - 1; k >= 0; k--) {
        const [out, row, size] = PUFFS[k];
        const across = px - AXIS - out;
        const down = py - row;
        if (Math.hypot(across, down) <= size) {
          const glow = Math.exp(-(((px - AXIS) / 12) ** 2));
          return dab(SMOKE, 0.5 + (down - across * 0.3) / size * 0.42 - glow * 0.45, x, y, 33 + k);
        }
      }
      return null;
    };
    const tower = (x, y) => {
      if (x === TOWER_LEFT && y === ARM) return STEEL[0];
      if (x < TOWER_LEFT || x > TOWER_RIGHT || y < TOWER_FROM) return null;
      if (y < TOWER_FROM + TOWER_TOP_ROWS) return TOWER_TOP;
      if (x === TOWER_LEFT || x === TOWER_RIGHT) return STEEL[x === TOWER_LEFT ? 0 : 1];
      return (x + y) % 4 === 0 || (x - y + H) % 4 === 0 ? STEEL[2] : null;
    };
    const moon = (px, py) => {
      const lit = Math.hypot(px - MOON_X, py - MOON_Y) <= MOON_SIZE;
      return lit && Math.hypot(px - MOON_X - MOON_SHADOW, py - MOON_Y) > MOON_SIZE ? MOON[py < MOON_Y ? 0 : 1] : null;
    };
    painting("rocket", 5, 3, (x, y) => {
      const px = x + 0.5;
      const py = y + 0.5;
      const lit = flame(px, py);
      const puff = lit > 0 && py < HORIZON ? null : smoke(x, y, px, py);
      if (puff) return puff;
      if (lit > 0) return shade(FLAME, 1 - lit, x, y);
      const body = hull(x, y, px, py);
      if (body) return body;
      if (within(NEAR_FIN, px - AXIS, py)) return FINS[2];
      if (within(LEFT_FIN, px - AXIS, py) || within(RIGHT_FIN, px - AXIS, py)) return FINS[px < AXIS ? 0 : 1];
      if (y === ARM && x > AXIS + WIDE - 1 && x < TOWER_LEFT) return STEEL[1];
      const frame = tower(x, y);
      if (frame) return frame;
      if (py >= HORIZON) return GROUND[y % 2];
      const crescent = moon(px, py);
      if (crescent) return crescent;
      const dusk = (py / HORIZON) ** 2.4;
      return (dusk < 0.3 ? star(x, y) : null) ?? dab(SKY, dusk, x, y, 47);
    });
  }

  // The freighter, five blocks by three, what is to come after the rocket: a battered saucer of a ship coming toward
  // us and to the left, weathered off-white plate over dark recesses, her two prongs reaching forward, her cockpit out
  // on a tube at her right side, her gun turret on her crown and a dish beside it, blue engine light behind her and the
  // stars drawn out into streaks by her speed. She is modelled rather than drawn, and each pixel looks down onto her,
  // so her plates shade as they would in the light. In her model x runs to her right, z toward her nose and y up, all
  // in lengths of her saucer's radius.
  {
    const W = 80;
    const H = 48;
    // Where her saucer's middle lies, in pixels, and how many pixels her saucer's radius spans.
    const CX = 48;
    const CY = 19;
    const SCALE = 21;
    // How far she is turned from facing us, her nose to the left, and how steeply we look down on her, in radians.
    const TURN = 0.62;
    const LOOK = 0.66;
    // How far forward her prongs reach, how far out their outer edge lies at her middle and at their tips, how far out
    // the slot between them lies and where it ends going aft.
    const TIP = 1.62;
    const PRONG_ROOT = 0.86;
    const PRONG_TIP = 0.31;
    const SLOT = 0.16;
    const SLOT_END = 0.66;
    // How high her saucer's rim, her saucer's crown, her prongs, her turret and the glass on it stand.
    const RIM = 0.075;
    const CROWN = 0.235;
    const PRONG_TOP = 0.135;
    const TURRET = 0.3;
    const BUBBLE = 0.36;
    // How far out her turret and the glass on it reach.
    const TURRET_OUT = 0.21;
    const BUBBLE_OUT = 0.055;
    // Her cockpit's tube, from where it leaves her saucer to its end, how thick it is, how high its axis runs, how far
    // along it the wider cockpit begins, how thick that is, and how far along it the cockpit's glass begins.
    const TUBE_FROM = [0.6, 0.3];
    const TUBE_TO = [1.28, 1.02];
    const TUBE_THICK = 0.1;
    const TUBE_Y = 0.085;
    const POD_FROM = 0.7;
    const POD_THICK = 0.17;
    const GLASS_FROM = 0.93;
    // Her dish, where it stands, how wide it is and how high.
    const DISH = [0.42, -0.14];
    const DISH_OUT = 0.135;
    const DISH_TOP = 0.3;
    // The raised plates running out from her crown, each by its bearing from her nose, in radians, and how far out its
    // edge lies from its middle; and how high they stand over the plates between them.
    const SPOKES = [[0, 0.125], [0.85, 0.07], [-0.85, 0.07], [1.95, 0.07], [-1.95, 0.07], [Math.PI, 0.09]];
    const SPOKE_RISE = 0.022;
    // Where the seams run round her saucer, and how far she is cut into plates round it, to a turn.
    const RINGS = [0.42, 0.62, 0.82, 1.01];
    const SECTORS = 18;
    // How wide a seam is, how much darker it is and how far a plate's shade strays from its neighbour's, as shares of
    // the ramp; how many of her plates are open machinery, the higher the fewer; and how much of her is rust, likewise.
    const SEAM = 0.017;
    const SEAM_DARK = 0.3;
    const PLATE_STRAY = 0.2;
    const MACHINERY_FROM = 0.84;
    const RUST_FROM = 0.8;
    // The light falling on her, from over her nose and to her right, as a direction.
    const LIGHT = [0.3, 0.86, 0.42];
    // How finely a pixel's line of sight is walked through her, and from how far either side of her middle.
    const WALK = 0.008;
    const WALK_FROM = 2.4;
    // Where the stars' streaks run out from, in pixels, behind her; how many there are; and how near and how long they
    // run from there.
    const VANISH_X = 58;
    const VANISH_Y = 10;
    const STREAKS = 40;
    const STREAK_NEAR = 16;
    const STREAK_LONG = [4, 13];
    // Where the glow behind her lies, in pixels, how far it reaches and how strong it is at its middle.
    const GLOW_X = 62;
    const GLOW_Y = 12;
    const GLOW_REACH = 24;
    const GLOW_STRONG = 0.5;
    const HULL = ramp(0xf0ecdf, 0xdcd8cb, 0xc0beb3, 0xa2a29a, 0x82847f, 0x62656a, 0x474a52);
    const RUST = ramp(0xb1906e, 0x8e6f52, 0x6a5240);
    const RECESS = hex(0x24262c);
    const WALL = ramp(0x8a8c8c, 0x6a6c70, 0x4a4d54, 0x303238);
    const PORT = hex(0x12161e);
    const GLASS = ramp(0x8cc8e8, 0x3e6a8a, 0x1a2a3e);
    const SPACE = ramp(0x04050d, 0x070a18, 0x0b1026, 0x121a3a, 0x1c2a56, 0x2c4a86);
    const STREAK = ramp(0x1c2a56, 0x34497f, 0x6683c4, 0xb4c8f0);
    const star = starfield("freighter_painting", W, H, 26, 2);
    reseed("space_poster_freighter_streaks");
    const streaks = new Map();
    for (let k = 0; k < STREAKS; k++) {
      const bearing = random() * Math.PI * 2;
      const from = STREAK_NEAR + random() * Math.hypot(W, H);
      const long = STREAK_LONG[0] + random() * (STREAK_LONG[1] - STREAK_LONG[0]);
      for (let along = 0; along <= long; along += 0.5) {
        const x = Math.round(VANISH_X + Math.cos(bearing) * (from + along));
        const y = Math.round(VANISH_Y + Math.sin(bearing) * (from + along));
        streaks.set(y * W + x, STREAK[Math.round(along / long * (STREAK.length - 1))]);
      }
    }
    // Where along her cockpit's tube a point lies and how far off its axis, or null off it.
    const tube = (x, z) => {
      const dx = TUBE_TO[0] - TUBE_FROM[0];
      const dz = TUBE_TO[1] - TUBE_FROM[1];
      const long = Math.hypot(dx, dz);
      const along = ((x - TUBE_FROM[0]) * dx + (z - TUBE_FROM[1]) * dz) / (long * long);
      if (along < 0 || along > 1) return null;
      const off = Math.abs((x - TUBE_FROM[0]) * dz - (z - TUBE_FROM[1]) * dx) / long;
      const thick = along > POD_FROM ? POD_THICK : TUBE_THICK;
      return off > thick ? null : { along, rise: Math.sqrt(thick * thick - off * off) };
    };
    const inProngs = (x, z) => z >= 0 && z <= TIP && Math.abs(x) <= PRONG_ROOT + (PRONG_TIP - PRONG_ROOT) * z / TIP;
    const inSlot = (x, z) => Math.abs(x) < SLOT && z > SLOT_END;
    // The raised plate a point lies on, or -1: within its edge of the line out from her crown along its bearing.
    const spokeAt = (x, z) => {
      for (let k = 0; k < SPOKES.length; k++) {
        const [bearing, edge] = SPOKES[k];
        const out = z * Math.cos(bearing) + x * Math.sin(bearing);
        if (out > 0 && Math.abs(x * Math.cos(bearing) - z * Math.sin(bearing)) < edge) return k;
      }
      return -1;
    };
    const saucerTop = (x, z, out) => {
      if (out < BUBBLE_OUT) return BUBBLE;
      if (out < TURRET_OUT) return TURRET;
      if (Math.hypot(x - DISH[0], z - DISH[1]) < DISH_OUT) return DISH_TOP;
      return RIM + (CROWN - RIM) * (1 - out * out) ** 0.7 + (spokeAt(x, z) >= 0 ? SPOKE_RISE : 0);
    };
    // How high her top stands over a point, or null off her.
    const topOf = (x, z) => {
      if (inSlot(x, z)) return null;
      const out = Math.hypot(x, z);
      const pod = tube(x, z);
      const heights = [
        out <= 1 ? saucerTop(x, z, out) : null,
        inProngs(x, z) && out > 0.7 ? PRONG_TOP + (spokeAt(x, z) === 0 ? SPOKE_RISE : 0) : null,
        pod ? TUBE_Y + pod.rise : null,
      ].filter((height) => height !== null);
      return heights.length ? Math.max(...heights) : null;
    };
    // How deep her underside lies under a point: her belly mirrors her back, but for her cockpit's tube out beyond it.
    const underOf = (x, z, top) => {
      const pod = tube(x, z);
      return pod && Math.hypot(x, z) > 1 && !inProngs(x, z) ? TUBE_Y - pod.rise : -Math.min(top, PRONG_TOP);
    };
    // How much light falls on the top of her at a point, by the slope there.
    const lightOn = (x, z, top) => {
      const reach = 0.014;
      const slopeX = ((topOf(x + reach, z) ?? top) - (topOf(x - reach, z) ?? top)) / (2 * reach);
      const slopeZ = ((topOf(x, z + reach) ?? top) - (topOf(x, z - reach) ?? top)) / (2 * reach);
      const length = Math.hypot(slopeX, 1, slopeZ);
      return unit((-slopeX * LIGHT[0] + LIGHT[1] - slopeZ * LIGHT[2]) / length);
    };
    // A plate of her saucer's top: the raised ones run clean and pale; between them each has its own wear, a seam round
    // or across it is darker, the odd one is open machinery and the odd patch is rust.
    const plate = (x, z, px, py, light) => {
      const out = Math.hypot(x, z);
      const across = ((Math.atan2(x, z) / (Math.PI * 2) + 1) % 1) * SECTORS;
      const ring = RINGS.findIndex((seam) => out < seam);
      const dull = 0.5 * (1 - light);
      if (spokeAt(x, z) >= 0) return dab(HULL, dull - 0.04, px, py, 77);
      if (lattice(ring, Math.floor(across), 72) > MACHINERY_FROM) return dab(WALL, 0.45 + noise(x * 16, z * 16, 74) * 0.6, px, py, 76);
      const onSeam = RINGS.some((seam) => Math.abs(out - seam) < SEAM) || Math.abs(across - Math.round(across)) * (Math.PI * 2 / SECTORS) * out < SEAM * 0.6;
      const wear = (lattice(ring, Math.floor(across), 71) - 0.5) * PLATE_STRAY;
      if (noise(x * 9, z * 9, 73) > RUST_FROM) return dab(RUST, dull + 0.2 + wear, px, py, 75);
      return dab(HULL, dull + 0.17 + wear + (onSeam ? SEAM_DARK : 0), px, py, 77);
    };
    // Her top where a line of sight comes down on it.
    const onTop = (x, z, top, px, py) => {
      const out = Math.hypot(x, z);
      const light = lightOn(x, z, top);
      const pod = tube(x, z);
      if (pod && top === TUBE_Y + pod.rise) {
        if (pod.along > GLASS_FROM) return dab(GLASS, 0.5 - pod.rise * 2, px, py, 79);
        if (pod.along > POD_FROM && Math.round(pod.along * 28) % 3 === 0) return RECESS;
        return dab(HULL, 0.7 - light * 0.75, px, py, 81);
      }
      if (out < BUBBLE_OUT) return dab(GLASS, 0.75 - light * 0.8, px, py, 83);
      if (out < TURRET_OUT) return dab(HULL, 0.8 - light * 0.75, px, py, 85);
      if (Math.hypot(x - DISH[0], z - DISH[1]) < DISH_OUT) {
        return Math.hypot(x - DISH[0], z - DISH[1]) < DISH_OUT * 0.35 ? RECESS : dab(HULL, 0.5 - light * 0.5, px, py, 87);
      }
      if (out > 1 || (inProngs(x, z) && out > 0.97)) {
        const bay = Math.abs(Math.abs(x) - (PRONG_TIP + SLOT) / 2) < 0.045 && z > 1.05 && z < 1.3;
        return bay ? RECESS : dab(HULL, 0.5 * (1 - light) + 0.12 + noise(x * 7, z * 7, 89) * 0.2, px, py, 91);
      }
      return plate(x, z, px, py, light);
    };
    // Her side where a line of sight meets it: her saucer's rim, her prongs' flanks with their row of round ports and
    // their square open ends, or the slot between them.
    const onSide = (x, y, z, px, py) => {
      const out = Math.hypot(x, z);
      if (z >= TIP - WALK * 3) return Math.abs(y) < PRONG_TOP * 0.55 && Math.abs(Math.abs(x) - (PRONG_TIP + SLOT) / 2) < 0.07 ? PORT : WALL[1];
      if (out > 0.99 && z > 0.25) {
        const port = Math.hypot(((z * 5.2) % 1 + 1) % 1 - 0.5, y * 7) < 0.34;
        return port ? PORT : dab(WALL, 0.55, px, py, 93);
      }
      const facing = out > 0.5 ? (x * LIGHT[0] + z * LIGHT[2]) / out : 0;
      const louvre = Math.round(Math.atan2(x, z) * 19) % 2 === 0 && Math.abs(y) < RIM * 0.5;
      return louvre ? PORT : dab(WALL, 0.75 - facing * 0.9, px, py, 95);
    };
    // What a line of sight sees of her at a pixel, or null past her: it is walked down from over her until it is inside
    // her, on her top if it came in through that and otherwise on her side.
    const ship = (px, py) => {
      const across = (px + 0.5 - CX) / SCALE;
      const down = (py + 0.5 - CY) / SCALE;
      for (let near = WALK_FROM; near > -WALK_FROM; near -= WALK) {
        const toward = down * Math.sin(LOOK) + near * Math.cos(LOOK);
        const y = near * Math.sin(LOOK) - down * Math.cos(LOOK);
        const x = -across * Math.cos(TURN) - toward * Math.sin(TURN);
        const z = toward * Math.cos(TURN) - across * Math.sin(TURN);
        const top = topOf(x, z);
        if (top !== null && y <= top && y >= underOf(x, z, top)) {
          return top - y < WALK * 3 ? onTop(x, z, top, px, py) : onSide(x, y, z, px, py);
        }
      }
      return null;
    };
    painting("freighter", 5, 3, (x, y) => {
      const body = ship(x, y);
      if (body) return body;
      const glow = GLOW_STRONG * Math.max(0, 1 - Math.hypot(x - GLOW_X, y - GLOW_Y) / GLOW_REACH) ** 2;
      const light = 0.14 + (y / H) * 0.14 + glow;
      return streaks.get(y * W + x) ?? star(x, y) ?? dab(SPACE, light, x, y, 97);
    });
  }
}

// The rocket's rover (see RoverModel), its bay's panel (see RoverPanelBlock) and its remote (see RoverRemoteItem).
// The rover: one texture its model's boxes take their faces from, each box's faces laid out where its texture offset
// in RoverModel says, as vanilla lays a box's out (its top and bottom in the first rows, then its west side, front,
// east side and back). Its body white, its seams faint, a band of crinkled gold foil round its lower half; its deck
// dark blue solar cells in a fine silver grid; its legs and mast pale aluminium; its wheels aluminium with dark tread
// across them and spoked hubs; its camera head white with a dark face and lenses; its dish white; its beacon amber.
// The panel's face: a lamp strip along its top in the colour of how the bay stands, and three picture buttons under
// it, the rover over an arrow pointing down, an arrow pointing up over the rover, and a remote. The remote: a dark
// handset with a screen, a thumbstick and a button, its antenna's tip red, or green once paired.
{
  const entityOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/entity");
  mkdirSync(entityOutput, { recursive: true });
  const ROVER_WHITE = [0xee, 0xf0, 0xf2];
  const ROVER_SEAM = [0xc6, 0xcb, 0xd2];
  const UNDERSIDE = [0x5a, 0x5e, 0x64];
  const FOIL = [[0xe8, 0xb8, 0x40], [0xd4, 0x9c, 0x28], [0xf6, 0xd2, 0x6a], [0xc0, 0x88, 0x20]];
  const CELL = [0x1a, 0x26, 0x5c];
  const CELL_SHEEN = [0x24, 0x34, 0x78];
  const CELL_LINE = [0x9c, 0xa8, 0xb8];
  const DECK_EDGE = [0xb8, 0xbe, 0xc4];
  const ALUMINIUM = [0xb4, 0xba, 0xc0];
  const ALUMINIUM_SHADE = [0x8c, 0x92, 0x99];
  const TREAD = [0x4a, 0x4e, 0x55];
  const HUB_DARK = [0x30, 0x33, 0x38];
  const FACE_DARK = [0x2a, 0x2d, 0x33];
  const LENS = [0x20, 0x40, 0x60];
  const HAZCAM = [0x30, 0x80, 0xe0];
  const POST = [0x40, 0x44, 0x4a];
  const AMBER = [0xff, 0xa8, 0x20];
  const AMBER_BRIGHT = [0xff, 0xe0, 0x80];
  const foil = (x, y) => FOIL[(x * 31 + y * 17 + ((x * y) >> 1)) % FOIL.length];
  const body = ({ face, x, y }) => {
    if (face === "top") return ROVER_WHITE;
    if (face === "bottom") return UNDERSIDE;
    if (y >= 2) return foil(x, y);
    return face === "side" && x % 6 === 5 ? ROVER_SEAM : ROVER_WHITE;
  };
  const deck = ({ face, x, y, w, h }) => {
    if (face === "bottom") return UNDERSIDE;
    if (face !== "top") return DECK_EDGE;
    if (x === 0 || y === 0 || x === w - 1 || y === h - 1) return DECK_EDGE;
    if (x % 4 === 0 || y % 3 === 0) return CELL_LINE;
    return (Math.floor(x / 4) + Math.floor(y / 3)) % 3 === 0 ? CELL_SHEEN : CELL;
  };
  const sensorBar = ({ face, x }) => (face === "front" && (x === 1 || x === 5) ? HAZCAM : FACE_DARK);
  const aluminium = ({ face }) => (face === "bottom" || face === "side" ? ALUMINIUM_SHADE : ALUMINIUM);
  const head = ({ face, x }) => {
    if (face === "front") return FACE_DARK;
    return face === "top" && x === 2 ? ROVER_SEAM : ROVER_WHITE;
  };
  const lens = ({ face }) => (face === "front" ? LENS : FACE_DARK);
  const post = () => POST;
  const beacon = ({ x, y }) => (x === 0 && y === 0 ? AMBER_BRIGHT : AMBER);
  const dish = ({ face, x, y }) => {
    if (face !== "front") return ALUMINIUM_SHADE;
    return x === 1 && y === 1 ? FACE_DARK : ROVER_WHITE;
  };
  const wheel = ({ face, x, y }) => {
    if (face !== "side") return y % 2 === 0 ? TREAD : ALUMINIUM;
    if (x === 2 && y === 2) return HUB_DARK;
    if (x === 0 || y === 0 || x === 4 || y === 4) return TREAD;
    return x === 2 || y === 2 ? ALUMINIUM : ALUMINIUM_SHADE;
  };
  // Each box of the model: its texture offset and size (width, height, depth), as RoverModel gives them.
  const boxes = [
    { u: 0, v: 0, w: 11, h: 5, d: 16, paint: body },
    { u: 0, v: 21, w: 16, h: 1, d: 22, paint: deck },
    { u: 56, v: 10, w: 7, h: 1, d: 1, paint: sensorBar },
    { u: 78, v: 21, w: 1, h: 1, d: 20, paint: aluminium },
    { u: 72, v: 0, w: 1, h: 5, d: 1, paint: aluminium },
    { u: 76, v: 0, w: 1, h: 3, d: 1, paint: aluminium },
    { u: 80, v: 0, w: 5, h: 2, d: 2, paint: head },
    { u: 94, v: 0, w: 1, h: 1, d: 1, paint: lens },
    { u: 98, v: 0, w: 1, h: 1, d: 1, paint: post },
    { u: 102, v: 0, w: 2, h: 2, d: 2, paint: beacon },
    { u: 110, v: 0, w: 3, h: 3, d: 1, paint: dish },
    { u: 118, v: 0, w: 1, h: 2, d: 1, paint: post },
    { u: 56, v: 0, w: 3, h: 5, d: 5, paint: wheel },
  ];
  // Which face of a box a pixel of its laid-out faces is, and where on that face; null for a pixel it leaves bare.
  const faceAt = ({ u, v, w, h, d }, px, py) => {
    const fx = px - u;
    const fy = py - v;
    if (fx < 0 || fy < 0 || fx >= 2 * (w + d) || fy >= d + h) return null;
    if (fy < d) {
      if (fx >= d && fx < d + w) return { face: "top", x: fx - d, y: fy, w, h: d };
      if (fx >= d + w && fx < d + 2 * w) return { face: "bottom", x: fx - d - w, y: fy, w, h: d };
      return null;
    }
    const sy = fy - d;
    if (fx < d) return { face: "side", x: fx, y: sy, w: d, h };
    if (fx < d + w) return { face: "front", x: fx - d, y: sy, w, h };
    if (fx < 2 * d + w) return { face: "side", x: fx - d - w, y: sy, w: d, h };
    return { face: "back", x: fx - 2 * d - w, y: sy, w, h };
  };
  texture(entityOutput, "rover", (x, y) => {
    for (const box of boxes) {
      const at = faceAt(box, x, y);
      if (at) return box.paint(at);
    }
    return CLEAR;
  }, 3, 128, 64);

  const BEZEL = [0x1e, 0x20, 0x24];
  const PANEL_FACE = [0x3a, 0x3e, 0x45];
  const LAMP_RIM = [0x14, 0x15, 0x18];
  const BUTTON = [0x58, 0x5e, 0x68];
  const BUTTON_TOP = [0x70, 0x77, 0x82];
  const BUTTON_BOTTOM = [0x2c, 0x2f, 0x35];
  const LIT_GREEN = [0x40, 0xe0, 0x60];
  const LAMPS = { ready: LIT_GREEN, busy: [0xff, 0xb0, 0x20], out: [0x40, 0x90, 0xff], refused: [0xe8, 0x30, 0x30] };
  const lighter = (colour) => colour.map((c) => Math.min(255, c + 0x50));
  // The buttons' pictures, a row of the picture to a row of pixels, "#" drawn in white and "g" in the lit green.
  const ROVER_ICON = ["##......", ".#......", "########", ".######.", "##.##.##"];
  const ICONS = [
    [...ROVER_ICON, "........", "...##...", "...##...", ".######.", "..####..", "...##..."],
    ["...##...", "..####..", ".######.", "...##...", "...##...", "........", ...ROVER_ICON],
    ["......#.", ".....#..", "....#...", "..####..", "..#gg#..", "..####..", "..#..#..", "..####..", "..#.##..", "..####..", "..####.."],
  ];
  const BUTTONS_FROM = [2, 12, 22];
  const BUTTON_WIDTH = 8;
  const BUTTONS_TOP = 10;
  const BUTTONS_BOTTOM = 29;
  const ICON_TOP = 14;
  const button = (x, y) => {
    const index = BUTTONS_FROM.findIndex((from) => x >= from && x < from + BUTTON_WIDTH);
    if (index < 0 || y < BUTTONS_TOP || y > BUTTONS_BOTTOM) return null;
    const row = ICONS[index][y - ICON_TOP];
    const mark = row === undefined ? "." : row[x - BUTTONS_FROM[index]];
    if (mark === "#") return INK;
    if (mark === "g") return LIT_GREEN;
    if (y === BUTTONS_TOP) return BUTTON_TOP;
    return y === BUTTONS_BOTTOM ? BUTTON_BOTTOM : BUTTON;
  };
  for (const [status, lamp] of Object.entries(LAMPS)) {
    texture(blockOutput, `rover_panel_front_${status}`, (x, y) => {
      if (x === 0 || y === 0 || x === 31 || y === 31) return BEZEL;
      if (y >= 1 && y <= 7 && x >= 2 && x <= 29) {
        if (y === 1 || y === 7 || x === 2 || x === 29) return LAMP_RIM;
        return y === 2 ? lighter(lamp) : lamp;
      }
      return button(x, y) ?? PANEL_FACE;
    }, 2, 32, 32);
  }
  texture(blockOutput, "rover_panel_side", (x, y) => (x === 0 || y === 0 ? BEZEL : PANEL_FACE), 2);

  const HANDSET = [0x2c, 0x30, 0x36];
  const HANDSET_EDGE = [0x14, 0x16, 0x1a];
  const SCREEN = [0x18, 0x3a, 0x6a];
  const SCREEN_SHINE = [0x50, 0x80, 0xc0];
  const STICK = [0xb0, 0xb6, 0xbe];
  const remote = (tip) => (x, y) => {
    if (x >= 12 && x <= 13 && y <= 1) return tip;
    if ((x === 11 && (y === 2 || y === 3)) || (x === 10 && y === 4)) return STEEL_DARK;
    if (x < 4 || x > 11 || y < 5) return CLEAR;
    if (x === 4 || x === 11 || y === 5 || y === 15) return HANDSET_EDGE;
    if (y >= 7 && y <= 9 && x >= 6 && x <= 9) return x === 6 && y === 7 ? SCREEN_SHINE : SCREEN;
    if ((x === 7 && y >= 11 && y <= 13) || (y === 12 && x >= 6 && x <= 8)) return STICK;
    if (x === 9 && y === 12) return [0xe0, 0x70, 0x20];
    return HANDSET;
  };
  texture(itemOutput, "rover_remote", remote([0xd0, 0x30, 0x30]), 0);
  texture(itemOutput, "rover_remote_paired", remote(LIT_GREEN), 0);
}

// The freighter's laser crossbow's icon (see LaserCrossbowItem), as its model is seen from above, pointing up and to the
// right as the game's own crossbow does: a dark-wood stock and grip from its butt at the bottom left, a gunmetal body on
// to the glowing emitter at its muzzle, its coil round the prod, two gunmetal arms across its front, swept back to the
// glowing pods at their tips, and its three heat lamps along the body. Resting, its string lies straight between the
// pods, a dull amber, and its lamps are dark; charging, it glows warmer and then hot; charged, its string is drawn back to
// its latch in a V, glowing near white, and its lamps are green; venting, its lamps are red and wisps of steam rise off
// its arms, so each look differs in shape or colour, never in text alone.
{
  const CLEAR = [0, 0, 0, 0];
  const WOOD = [0x4a, 0x30, 0x20];
  const WOOD_LIGHT = [0x6e, 0x48, 0x2e];
  const GUNMETAL = [0x34, 0x39, 0x40];
  const GUNMETAL_LIGHT = [0x5e, 0x66, 0x70];
  // A line of pixels from one to the other, both ends in, as a set of "x,y".
  const line = (x0, y0, x1, y1) => {
    const steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    return new Set(Array.from({ length: steps + 1 }, (_, i) => `${Math.round(x0 + (x1 - x0) * i / steps)},${Math.round(y0 + (y1 - y0) * i / steps)}`));
  };
  // Its arms bow forward from the coil and curl back to their pods, mirrored across the stock.
  const upperArm = new Set(["9,3", "8,2", "7,2", "6,2"]);
  const lowerArm = new Set(["13,7", "14,8", "14,9", "14,10"]);
  const pods = new Set(["5,3", "13,11"]);
  const coil = new Set(["10,4", "11,5", "11,4", "12,5", "11,3", "12,4"]);
  const emitter = new Set(["14,1", "15,1"]);
  const slack = line(5, 3, 13, 11);
  const drawn = new Set([...line(5, 3, 7, 9), ...line(13, 11, 7, 9)]);
  const lamps = ["4,12", "5,11", "6,10"];
  const grip = new Set(["6,11", "7,11", "6,12"]);
  const STEAM = [0xe8, 0xec, 0xf0, 0xc8];
  const steam = new Set(["4,0", "3,1", "9,1", "10,0", "15,5", "15,4"]);
  const body = (x, y) => {
    const across = x + y;
    if (across === 15 && x >= 1 && x <= 13) return x <= 5 ? WOOD_LIGHT : GUNMETAL_LIGHT;
    if (across === 16 && x >= 1 && x <= 14) return x <= 5 ? WOOD : GUNMETAL;
    if (across === 14 && x === 0) return GUNMETAL;
    return null;
  };
  // A look: the colour its coil, string, pods and emitter glow, its string's shape, its lamps' colour, and anything
  // drawn over it.
  const crossbow = (glow, string, lampColour, extra = new Map()) => (x, y) => {
    const at = `${x},${y}`;
    if (extra.has(at)) return extra.get(at);
    if (lamps.includes(at)) return lampColour;
    if (coil.has(at) || emitter.has(at) || pods.has(at)) return glow;
    if (string.has(at)) return glow;
    if (upperArm.has(at)) return GUNMETAL_LIGHT;
    if (lowerArm.has(at)) return GUNMETAL;
    if (grip.has(at)) return WOOD;
    return body(x, y) ?? CLEAR;
  };
  const LAMP_DARK = [0x26, 0x28, 0x2c];
  const GLOWS = { "": [0xb8, 0x6a, 0x1c], warm: [0xe0, 0x88, 0x24], hot: [0xff, 0xb4, 0x3c], charged: [0xff, 0xf0, 0xb0] };
  for (const [look, glow] of Object.entries(GLOWS)) {
    const name = look ? `laser_crossbow_${look}` : "laser_crossbow";
    texture(itemOutput, name, crossbow(glow, look === "charged" ? drawn : slack, look === "charged" ? [0x50, 0xf0, 0x60] : LAMP_DARK), 0);
  }
  texture(itemOutput, "laser_crossbow_venting", crossbow(GLOWS[""], slack, [0xf0, 0x30, 0x20], new Map([...steam].map((at) => [at, STEAM]))), 0);
}

// The laser crossbow's model's parts (see generate_resources.mjs): gunmetal with faint panel seams for its body, prod,
// arms and butt plate, a paler steel for its rail, sights and trigger, dark wood with a grain for its stock and grip,
// copper for the windings round its coil, and the amber of its coil, string, channel and emitters as it glows brighter
// charging, from a dull ember resting through warm and hot to near white charged; and its three heat lamps, dark, green
// or red.
{
  const METAL = [0x4a, 0x50, 0x5a];
  const METAL_SEAM = [0x38, 0x3d, 0x45];
  const TRIM = [0x8e, 0x96, 0xa0];
  const TRIM_EDGE = [0xb4, 0xbb, 0xc4];
  const WOOD = [0x4c, 0x31, 0x21];
  const WOOD_GRAIN = [0x3a, 0x25, 0x18];
  const COPPER = [0xb8, 0x6a, 0x30];
  const COPPER_DARK = [0x86, 0x4a, 0x20];
  texture(itemOutput, "laser_crossbow_metal", (x, y) => (y === 7 || x === 11 ? METAL_SEAM : METAL), 3);
  texture(itemOutput, "laser_crossbow_trim", (x, y) => (y % 8 === 0 ? TRIM_EDGE : TRIM), 3);
  texture(itemOutput, "laser_crossbow_wood", (x, y) => ((y * 5 + Math.floor(x / 4)) % 7 === 0 ? WOOD_GRAIN : WOOD), 4);
  texture(itemOutput, "laser_crossbow_copper", (x, y) => ((x + y) % 2 === 0 ? COPPER : COPPER_DARK), 3);
  const GLOWS = { rest: [0x8a, 0x4c, 0x14], warm: [0xc8, 0x74, 0x1c], hot: [0xf4, 0xa8, 0x30], charged: [0xff, 0xec, 0xa8] };
  for (const [look, colour] of Object.entries(GLOWS)) {
    texture(itemOutput, `laser_crossbow_glow_${look}`, () => colour, 2);
  }
  const LAMPS = { dark: [0x26, 0x28, 0x2c], green: [0x50, 0xf0, 0x60], red: [0xf0, 0x30, 0x20] };
  for (const [look, colour] of Object.entries(LAMPS)) {
    texture(itemOutput, `laser_crossbow_lamp_${look}`, () => colour, 0);
  }
}

// The freighter's Spookie (see SpookieModel, whose boxes take their faces from the places here): slate blue-grey fur
// flecked with silver tips, a moss-green cast down its back and lichen-pale dapples on its forearms; a cream mane over
// its shoulders; a bare charcoal face with big amber eyes, a wide flat nose and two small ivory tusks; long ears, dusky
// inside, tufted silver; a hi-vis orange harness with a reflective stripe and a belt; leather bracers over charcoal
// hands; brass goggles with blue lenses on a leather strap; a leather satchel with a brass buckle and a steel wrench.
// Its eyes again on a sheet of their own, alone and bright, for the glow drawn over it. And its hammock's canvas,
// orange and cream stripes along it with a dark hem.
{
  const entityOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/entity");
  const FUR = [0x5d, 0x6b, 0x7a];
  const TIP = [0xa8, 0xb4, 0xc0];
  const MOSS = [0x5f, 0x74, 0x5a];
  const DAPPLE = [0xb8, 0xc4, 0xa8];
  const DARK_FUR = [0x3a, 0x44, 0x50];
  const MANE = [0xe8, 0xdc, 0xc0];
  const MANE_STREAK = [0xcf, 0xc0, 0x9e];
  const FACE = [0x2e, 0x2c, 0x2e];
  const NOSE = [0x16, 0x14, 0x16];
  const EYE = [0xff, 0xb0, 0x30];
  const PUPIL = [0x24, 0x14, 0x08];
  const TUSK = [0xee, 0xe6, 0xcc];
  const EAR_INSIDE = [0x6a, 0x50, 0x58];
  const TUFT = [0xd0, 0xd8, 0xe0];
  const HI_VIS = [0xff, 0x7a, 0x10];
  const REFLECTIVE = [0xd8, 0xe0, 0xe8];
  const LEATHER = [0x6b, 0x45, 0x28];
  const BRASS = [0xc8, 0x9a, 0x3c];
  const LENS = [0x7f, 0xc8, 0xd8];
  const STEEL = [0x9a, 0xa4, 0xae];
  const SOLE = [0x3a, 0x36, 0x38];
  const EYE_GLOW = [0xff, 0xc0, 0x40, 0xff];
  // A smooth-ish noise from 0 to 1 of its own, so the textures above are unchanged.
  const speck = (x, y) => {
    let h = Math.imul(x * 2654435761 + y * 40503, 0x9e3779b1) ^ 0x51ed;
    h = Math.imul(h ^ (h >>> 13), 0x85ebca6b);
    return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
  };
  const mix = (a, b, t) => a.map((c, i) => Math.round(c + (b[i] - c) * t));
  const fur = (x, y, base = FUR) => (speck(x, y) > 0.94 ? mix(base, TIP, 0.6) : speck(x + 7, y + 3) < 0.06 ? mix(base, DARK_FUR, 0.4) : base);
  // A box's faces on the texture, as the game lays them out from its corner: its top and bottom, then its right side,
  // front, left side and back, each as a rectangle and where a pixel falls in it.
  const faceOf = ([u, v, w, h, d], x, y) => {
    const faces = [
      ["up", u + d, v, w, d], ["down", u + d + w, v, w, d],
      ["right", u, v + d, d, h], ["front", u + d, v + d, w, h], ["left", u + d + w, v + d, d, h], ["back", u + 2 * d + w, v + d, w, h],
    ];
    for (const [name, fu, fv, fw, fh] of faces) {
      if (x >= fu && x < fu + fw && y >= fv && y < fv + fh) return { name, fx: x - fu, fy: y - fv, fw, fh };
    }
    return null;
  };
  const torso = (face, x, y) => {
    const strap = face.fx === 3 || face.fx === 4 || face.fx === face.fw - 4 || face.fx === face.fw - 5;
    const harnessed = face.name === "front" || face.name === "back";
    if (harnessed && face.fy >= 11 && face.fy <= 12) return face.fy === 11 ? HI_VIS : LEATHER;
    if (harnessed && strap) return face.fy === 9 ? REFLECTIVE : HI_VIS;
    return face.name === "back" ? fur(x, y, mix(FUR, MOSS, 0.55)) : fur(x, y);
  };
  const mane = (face, x, y) => {
    if (face.name === "back" || face.name === "down") return fur(x, y, mix(FUR, MOSS, 0.55));
    return speck(x, y) < 0.2 || face.fx % 3 === 1 && face.fy > face.fh - 3 ? MANE_STREAK : MANE;
  };
  const head = (face, x, y) => {
    if (face.name !== "front") return fur(x, y);
    const eye = face.fy >= 4 && face.fy <= 5 && (face.fx === 1 || face.fx === 2 || face.fx === 6 || face.fx === 7);
    if (eye) return face.fy === 5 && (face.fx === 2 || face.fx === 6) ? PUPIL : EYE;
    return face.fy >= 4 ? FACE : fur(x, y);
  };
  const snout = (face) => {
    if (face.name === "front" && face.fy === 0 && (face.fx === 1 || face.fx === 3)) return NOSE;
    if (face.name === "front" && face.fy === face.fh - 1) return NOSE;
    return face.name === "up" ? NOSE : FACE;
  };
  const ear = (face, x, y) => (face.name === "front" && face.fy >= 1 ? EAR_INSIDE : fur(x, y));
  const arm = (face, x, y) => {
    if (face.name === "down" || face.fy >= 18) return face.fy >= 21 && face.fx % 2 === 0 ? NOSE : FACE;
    if (face.fy >= 14) return face.fy === 15 && face.fx === 1 ? BRASS : LEATHER;
    if (face.fy >= 8 && speck(x + 3, y + 11) < 0.25) return DAPPLE;
    return fur(x, y);
  };
  const leg = (face, x, y) => (face.fy >= 11 ? fur(x, y, mix(FUR, DARK_FUR, 0.45)) : fur(x, y));
  const satchel = (face) => (face.name === "left" && face.fy === 2 && face.fx >= 1 && face.fx <= 2 ? BRASS : LEATHER);
  const lens = (face) => (face.name === "front" ? (face.fx === 0 && face.fy === 0 ? REFLECTIVE : LENS) : BRASS);
  // Each box: its corner on the texture, its width, height and depth, and how it is painted, as SpookieModel has them.
  const BOXES = [
    [[0, 0, 14, 15, 8], torso], [[44, 0, 16, 8, 10], mane], [[28, 44, 3, 6, 4], satchel], [[42, 44, 2, 9, 1], () => STEEL],
    [[0, 25, 9, 10, 9], head], [[36, 25, 5, 4, 2], snout], [[36, 31, 9, 2, 1], () => DARK_FUR], [[56, 25, 1, 2, 1], () => TUSK],
    [[56, 31, 10, 2, 1], () => LEATHER], [[78, 34, 2, 2, 1], lens], [[78, 25, 3, 5, 1], ear], [[86, 25, 1, 2, 1], (face, x, y) => fur(x, y)],
    [[90, 25, 1, 2, 1], () => TUFT],
    [[96, 0, 4, 24, 5], arm], [[88, 31, 6, 16, 6], leg], [[0, 44, 6, 2, 8], () => SOLE],
  ];
  const painted = (x, y) => {
    for (const [box, paint] of BOXES) {
      const face = faceOf(box, x, y);
      if (face) return { face, paint };
    }
    return null;
  };
  texture(entityOutput, "spookie", (x, y) => {
    const found = painted(x, y);
    return found ? found.paint(found.face, x, y) : CLEAR;
  }, 3, 128, 64);
  texture(entityOutput, "spookie_eyes", (x, y) => {
    const found = painted(x, y);
    return found && found.paint === head && head(found.face, x, y) === EYE ? EYE_GLOW : CLEAR;
  }, 0, 128, 64);
  texture(blockOutput, "spookie_hammock", (x, y) => {
    if (y === 0 || y === 15) return [0x8a, 0x4a, 0x1a];
    const stripe = Math.floor((y - 1) / 3) % 2 === 0 ? [0xe8, 0x6a, 0x1a] : [0xee, 0xe2, 0xc8];
    return (x + y) % 4 === 0 ? mix(stripe, [0x40, 0x30, 0x20], 0.12) : stripe;
  }, 3);
}

// The rocket's lab. Her tardigrade (see TardigradeModel, which takes its boxes' faces from these patches): soft
// peach skin all over, mottled in soft blotches with fine darker bumps, as a water bear's cuticle is under a
// microscope; its folds between the segments a darker tan with creases across them; its claws pale ivory; its mouth a
// small dark ring in a lip at the tip of its lighter snout; its eye spots near black. The tank's glass, all but
// clear with a faint streak; the steel kerb its lowest row stands on and the soft light along its top; the feeding
// hatch's steel frame and its flap. The instruments: white enamel, charcoal and chrome; the vials in their five
// colours; the screens, each a loop: the scan sweeping down a wireframe of the specimen, and its vitals, a pulse
// tracing under its picture, slow and blue under a barrel's while it is curled up in its tun. The pool's water,
// rippling.
{
  const entityOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/entity");
  mkdirSync(entityOutput, { recursive: true });
  // The rocket's in peach and tan, and the freighter's small one (see Tardigrade.setSize) in pale mint, its folds teal.
  const PEACH = {
    skin: [0xe8, 0xc9, 0xa8], bump: [0xd9, 0xb7, 0x94], sheen: [0xf3, 0xde, 0xc6], fold: [0xc9, 0xa5, 0x84], crease: [0xb0, 0x8b, 0x67],
    lip: [0xb9, 0x8f, 0x6e], mouth: [0x3a, 0x24, 0x18], snout: [0xee, 0xd4, 0xb8],
  };
  const MINT = {
    skin: [0xc6, 0xe9, 0xd6], bump: [0xae, 0xd9, 0xc2], sheen: [0xe0, 0xf6, 0xea], fold: [0x62, 0xb5, 0xa6], crease: [0x3e, 0x93, 0x86],
    lip: [0x7e, 0xb6, 0x9f], mouth: [0x1c, 0x33, 0x2b], snout: [0xd9, 0xf2, 0xe4],
  };
  const CLAW = [0xf3, 0xec, 0xdf];
  const EYE = [0x1e, 0x16, 0x12];
  // The patches' places, as TardigradeModel has them: the folds' rows, and the claws', lip's, eyes' and snout's corner.
  const FOLD_V = 80;
  const CLAW_V = 112;
  const LIP_U = 32;
  const EYE_U = 48;
  const SNOUT_U = 64;
  // A smooth value noise from 0 to 1, of its own so the textures above are unchanged, over cells so many pixels wide.
  const hash = (x, y) => {
    let h = Math.imul(x * 374761393 + y * 668265263, 0x27d4eb2d) ^ 0x5a17;
    h = Math.imul(h ^ (h >>> 15), 0x2c1b3c6d);
    return ((h ^ (h >>> 13)) >>> 0) / 4294967296;
  };
  const smooth = (x, y, cell) => {
    const [cx, cy] = [Math.floor(x / cell), Math.floor(y / cell)];
    const [fx, fy] = [x / cell - cx, y / cell - cy];
    const [sx, sy] = [fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)];
    const top = hash(cx, cy) * (1 - sx) + hash(cx + 1, cy) * sx;
    const bottom = hash(cx, cy + 1) * (1 - sx) + hash(cx + 1, cy + 1) * sx;
    return top * (1 - sy) + bottom * sy;
  };
  const shade = ([r, g, b], by) => [r * by, g * by, b * by].map((c) => Math.round(Math.max(0, Math.min(255, c))));
  const skin = (x, y, base, colours) => {
    const blotch = 0.94 + 0.09 * smooth(x, y, 7) + 0.04 * smooth(x + 31, y + 17, 3);
    const speck = hash(x + 101, y + 37);
    if (speck < 0.025) return shade(colours.bump, blotch);
    if (speck > 0.985) return shade(colours.sheen, blotch);
    return shade(base, blotch);
  };
  const bear = (colours) => (x, y) => {
    if (y >= FOLD_V && y < CLAW_V) {
      return x % 3 === 1 ? shade(colours.crease, 0.97 + 0.06 * smooth(x, y, 4)) : shade(colours.fold, 0.95 + 0.08 * smooth(x, y, 5));
    }
    if (y >= CLAW_V && x < LIP_U) return CLAW;
    if (y >= CLAW_V && x < EYE_U) {
      // The middle of the lip's front face, a pixel in from its patch's corner, five pixels across and four down.
      const round = Math.hypot(x + 0.5 - (LIP_U + 3.5), y + 0.5 - (CLAW_V + 3));
      if (round < 1.3) return colours.mouth;
      return round < 2.2 ? shade(colours.lip, 0.82) : colours.lip;
    }
    if (y >= CLAW_V && x < EYE_U + 8) return EYE;
    if (y >= CLAW_V && x >= SNOUT_U && x < SNOUT_U + 32) return skin(x, y, colours.snout, colours);
    return skin(x, y, colours.skin, colours);
  };
  texture(entityOutput, "tardigrade", bear(PEACH), 2, 128, 128);
  texture(entityOutput, "tardigrade_small", bear(MINT), 2, 128, 128);

  const GLASS_TINT = [0xd8, 0xf0, 0xf4, 0x2c];
  texture(blockOutput, "habitat_glass", (x, y) => (Math.abs(x - y - 4) < 1.2 || Math.abs(x - y + 7) < 0.6 ? [0xff, 0xff, 0xff, 0x48] : GLASS_TINT), 0);
  const KERB = [0x4a, 0x50, 0x57];
  texture(blockOutput, "habitat_kerb", (x, y) => (y === 0 ? [0x6e, 0x76, 0x7f] : KERB), 2);
  texture(blockOutput, "habitat_light", (x, y) => shade([0xff, 0xf6, 0xe6], 1 - 0.04 * Math.abs(y - 7.5) / 7.5), 0);
  texture(blockOutput, "habitat_hatch_frame", (x, y) => (x === 0 || y === 0 || x === 15 || y === 15 ? [0x3c, 0x42, 0x48] : [0x8e, 0x97, 0xa0]), 2);
  texture(blockOutput, "habitat_hatch_flap", (x, y) => {
    if (y >= 6 && y <= 9 && x >= 5 && x <= 10) return y === 6 ? [0xc8, 0xcf, 0xd6] : [0x2c, 0x30, 0x35];
    return y % 3 === 0 ? [0x7c, 0x84, 0x8c] : [0x9a, 0xa3, 0xad];
  }, 2);

  texture(blockOutput, "lab_white", (x, y) => (y === 15 ? [0xc8, 0xce, 0xd4] : [0xec, 0xef, 0xf2]), 2);
  texture(blockOutput, "lab_dark", () => [0x2a, 0x2e, 0x33], 2);
  texture(blockOutput, "lab_chrome", (x, y) => shade([0xc9, 0xd0, 0xd7], 0.85 + 0.2 * Math.abs(Math.sin((x + y) / 3))), 1);
  // The vials, five across as SAMPLES stands them, each its liquid under a glass top and a cap, its glass's edge
  // bright.
  const VIALS = [[0x4f, 0xd6, 0xe0], [0xf0, 0xa8, 0x30], [0xd0, 0x4a, 0xb8], [0x8a, 0xdb, 0x4a], [0xe8, 0xc9, 0xa8]];
  texture(blockOutput, "lab_vials", (x, y) => {
    const vial = VIALS[Math.max(0, Math.min(4, Math.floor((x - 1) / 3)))];
    if (y < 3) return [0xe0, 0xe6, 0xec];
    if (y < 6) return [0xcc, 0xe8, 0xf0, 0x90];
    return (x - 1) % 3 === 0 ? shade(vial, 1.15) : vial;
  }, 0);

  const SCREEN = [0x06, 0x0e, 0x10];
  const GRID = [0x0c, 0x24, 0x22];
  const BEZEL = [0x1c, 0x1f, 0x23];
  const FRAMES = 16;
  const animated = (name, frametime, paint) => {
    texture(blockOutput, name, (x, y) => paint(x, y % SIZE, Math.floor(y / SIZE)), 0, SIZE, SIZE * FRAMES);
    writeFileSync(resolve(blockOutput, `${name}.png.mcmeta`), JSON.stringify({ animation: { frametime } }, null, 2) + "\n");
  };
  const onScreen = (x, y) => x >= 1 && x <= 14 && y >= 1 && y <= 14;
  // The specimen side on, a humped body over four stubby legs, as rows of its outline; and the barrel of its tun.
  const SPECIMEN = ["..........", "...#####..", ".##.....#.", "#........#", "#........#", ".##########", ".#.#..#.#."];
  const TUN = [".####.", "#....#", "#.##.#", "#....#", ".####."];
  const outline = (rows, x, y, left, top) => rows[y - top]?.[x - left] === "#";
  animated("lab_screen_scan", 3, (x, y, frame) => {
    if (!onScreen(x, y)) return BEZEL;
    const sweep = 2 + Math.floor(frame * 12 / FRAMES);
    const lit = outline(SPECIMEN, x, y, 3, 5);
    if (y === sweep) return [0x6f, 0xf0, 0xff];
    if (lit) return Math.abs(y - sweep) <= 2 ? [0x9f, 0xf8, 0xff] : [0x2a, 0xa8, 0xb8];
    return x % 4 === 0 || y % 4 === 0 ? GRID : SCREEN;
  });
  // A pulse every sixteen pixels, scrolling a pixel each frame: a quick rise and fall from the line along its foot.
  const PULSE = [0, 0, 0, -1, -4, 2, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0];
  const trace = (x, frame, beat) => 11 + beat[(x + frame) % beat.length];
  animated("lab_screen_vitals", 3, (x, y, frame) => {
    if (!onScreen(x, y)) return BEZEL;
    if (outline([".####.", "######", "#.##.#"], x, y, 2, 2)) return PEACH.skin;
    if (y === trace(x, frame, PULSE)) return [0x5c, 0xf0, 0x8a];
    if (y > 4 && Math.abs(y - trace(x, frame, PULSE)) === 1 && PULSE[(x + frame) % 16] !== 0) return [0x1f, 0x6a, 0x3c];
    return x % 4 === 0 || y % 4 === 0 ? GRID : SCREEN;
  });
  // Asleep: the creature lying low and long under a crescent moon, its pulse slow and gentle, a night blue round it.
  const LYING = ["..######..", ".########.", "##########"];
  const MOON = ["..##", ".#..", ".#..", "..##"];
  const SLOW = [0, 0, 0, 0, 0, -1, -2, -1, 0, 0, 0, 0, 0, 0, 0, 0];
  animated("lab_screen_asleep", 6, (x, y, frame) => {
    if (!onScreen(x, y)) return BEZEL;
    if (outline(MOON, x, y, 10, 2)) return [0xf4, 0xe2, 0x8c];
    if (outline(LYING, x, y, 2, 6)) return PEACH.skin;
    if (y === trace(x, frame, SLOW)) return [0x7c, 0xb8, 0xf0];
    return x % 4 === 0 || y % 4 === 0 ? [0x12, 0x14, 0x30] : [0x08, 0x08, 0x1c];
  });
  const STILL = [0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0];
  animated("lab_screen_dormant", 8, (x, y, frame) => {
    if (!onScreen(x, y)) return BEZEL;
    if (outline(TUN, x, y, 2, 1)) return [0xd6, 0xbe, 0x98];
    if (y === trace(x, frame, STILL)) return [0x58, 0xa8, 0xff];
    return x % 4 === 0 || y % 4 === 0 ? [0x0c, 0x18, 0x2a] : SCREEN;
  });
  // Away through the spore drive: stars streaming out from the middle of the screen against the dark, spore blue going
  // white as they near its edge, each ray at its own place along the loop, so it reads as a jump without a word.
  const STAR_RAYS = 14;
  const rayAt = (ray, frame) => {
    const angle = (2 * Math.PI * ray) / STAR_RAYS + ray * 0.37;
    const out = ((frame / FRAMES + (ray * 5) / STAR_RAYS) % 1) * 7;
    return { angle, out };
  };
  animated("lab_screen_away", 2, (x, y, frame) => {
    if (!onScreen(x, y)) return BEZEL;
    for (let ray = 0; ray < STAR_RAYS; ray++) {
      const { angle, out } = rayAt(ray, frame);
      for (let back = 0; back <= out * 0.45; back += 0.5) {
        const r = out - back;
        if (Math.round(7.5 + Math.cos(angle) * r) === x && Math.round(7.5 + Math.sin(angle) * r) === y) {
          return r > 4.5 ? [0xe8, 0xf6, 0xff] : [0x58, 0xc8, 0xff];
        }
      }
    }
    return [0x06, 0x0a, 0x1c];
  });
  // Shallow clear water over the pool's bed: blue-green, ripples drifting across it, glints where they crest.
  // Each ripple drifts a whole number of its own lengths over the loop and a block, so neither the loop nor the pool's
  // blocks side by side show a seam.
  animated("habitat_pool", 4, (x, y, frame) => {
    const turn = (2 * Math.PI * frame) / FRAMES;
    const across = (2 * Math.PI) / SIZE;
    const ripple = Math.sin(across * (2 * x + y) + turn) + Math.sin(across * (3 * y - x) - 2 * turn);
    if (ripple > 1.6) return [0xe8, 0xff, 0xff, 0xa0];
    return [0x3c, 0x9a + Math.round(ripple * 6), 0xa8 + Math.round(ripple * 8), 0x88];
  });
}

// The rocket's halo light (see HaloLightBlock): its ring's light, bright in the middle of its band and a little paler
// at its edges, warm white or cool blue; and its hub and spokes, dark brushed metal.
{
  const glow = (core, edge) => (x, y) => (y >= 5 && y <= 10 ? core : edge);
  texture(blockOutput, "halo_light_warm", glow([0xff, 0xf2, 0xd8], [0xff, 0xe0, 0xb0]), 2);
  texture(blockOutput, "halo_light_cool", glow([0xc8, 0xdc, 0xff], [0x96, 0xb4, 0xf0]), 2);
  texture(blockOutput, "halo_light_pink", glow([0xff, 0xc8, 0xee], [0xff, 0x6a, 0xd8]), 2);
  texture(blockOutput, "halo_light_hub", (x, y) => [0x2c + (y % 3) * 3, 0x2e + (y % 3) * 3, 0x34 + (y % 3) * 3], 2);
  // The rocket's steel girder: gunmetal, brushed along its length; its flanges' faces riveted down both edges, a darker
  // seam inside each row of rivets; and its cut ends darker still.
  const GUNMETAL = [0x4a, 0x50, 0x58];
  const dimmed = (colour, by) => colour.map((c) => Math.round(Math.max(0, Math.min(255, c * by))));
  const brushed = (x) => dimmed(GUNMETAL, 1 + 0.05 * Math.sin(x * 2.3) + (x % 5 === 0 ? 0.04 : 0));
  texture(blockOutput, "steel_girder", (x) => brushed(x), 3);
  texture(blockOutput, "steel_girder_flange", (x, y) => {
    const rivet = (x === 2 || x === 13) && y % 4 === 1;
    if (rivet) return [0x8a, 0x92, 0x9c];
    if ((x === 2 || x === 13) && y % 4 === 2) return [0x24, 0x28, 0x2e];
    if (x === 0 || x === 15) return dimmed(GUNMETAL, 0.7);
    return brushed(x);
  }, 3);
  texture(blockOutput, "steel_girder_end", (x, y) => dimmed(GUNMETAL, 0.62 + 0.06 * ((x + y) % 2)), 2);
}
// The wall washer's fan of light up the wall (see WallLightBlock): narrow and bright at its foot, over the lamp,
// widening as it rises and fading out toward the top and its sides.
{
  const wash = (colour) => (x, y) => {
    const up = (15 - y) / 15;
    const half = 1.5 + up * 6.5;
    const off = Math.abs(x + 0.5 - 8);
    if (off > half) return [0, 0, 0, 0];
    const alpha = Math.round(0xd8 * (1 - up) ** 1.1 * (1 - off / half) ** 0.6);
    return [...colour, alpha];
  };
  texture(blockOutput, "wall_light_wash_warm", wash([0xff, 0xe4, 0xb8]), 0);
  texture(blockOutput, "wall_light_wash_cool", wash([0xa8, 0xc4, 0xff]), 0);
  texture(blockOutput, "wall_light_wash_pink", wash([0xff, 0x6a, 0xd8]), 0);
}

// The rocket's rooms finished inside (see RocketFinish), each its own way: for every room its wall, the dado along the
// wall's foot, a ledge where the wall turns level, its ceiling and its floor. Painted from a few materials (boards,
// tiles, bricks, quilting, foam, plating, parquet, padding, carpet), each block's own boards, tiles or bricks a shade
// apart from their neighbours' by a hash of where they lie, so a wall reads as made of many, not stamped from one.
{
  const hex = (value) => [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
  const shade = ([r, g, b], by) => [r + by, g + by, b + by];
  const hash = (a, b) => (((a * 73856093) ^ (b * 19349663)) >>> 0) % 1000 / 1000;
  // Boards running up (or across), so many pixels wide, a dark seam between them and a faint grain along each.
  const boards = (base, seam, width, across = false) => (x, y) => {
    const [along, over] = across ? [x, y] : [y, x];
    const board = Math.floor(over / width);
    if (over % width === 0) return seam;
    const grain = (along + board * 5) % 7 === 0 ? -10 : 0;
    return shade(base, Math.round((hash(board, 3) - 0.5) * 18) + grain);
  };
  // Square tiles of a size, grout between them, each tile its own shade; a glint in a corner for glazed ones.
  const tiles = (base, grout, size, glaze = false) => (x, y) => {
    if (x % size === 0 || y % size === 0) return grout;
    const tile = shade(base, Math.round((hash(Math.floor(x / size), Math.floor(y / size)) - 0.5) * 16));
    return glaze && x % size === 1 && y % size === 1 ? shade(tile, 28) : tile;
  };
  // Bricks in running bond, four high and eight long, mortar between, each brick its own shade.
  const bricks = (base, mortar) => (x, y) => {
    const course = Math.floor(y / 4);
    const shifted = x + (course % 2) * 4;
    if (y % 4 === 3 || shifted % 8 === 0) return mortar;
    return shade(base, Math.round((hash(Math.floor(shifted / 8), course) - 0.5) * 20));
  };
  // Quilted fabric: a diamond of stitching, puffed light in the middle of each diamond and shadowed at its seams.
  const quilted = (base, stitch) => (x, y) => {
    const u = (x + y) % 8;
    const v = (x - y + 16) % 8;
    if (u === 0 || v === 0) return stitch;
    const puff = 4 - Math.max(Math.abs(u - 4), Math.abs(v - 4));
    return shade(base, puff * 4 - 6);
  };
  // Acoustic foam: pyramids four pixels across, lit from the top left.
  const foam = (base) => (x, y) => {
    const u = x % 4;
    const v = y % 4;
    const lit = u + v < 3 ? 10 : u + v > 3 ? -12 : 0;
    return shade(base, lit + Math.round((hash(Math.floor(x / 4), Math.floor(y / 4)) - 0.5) * 6));
  };
  // Steel plating: a seam round the panel, rivets just inside it, fine brushed lines across.
  const plating = (base, seam, rivet) => (x, y) => {
    if (x === 0 || y === 0) return seam;
    if ((y === 2 || y === 13) && x % 4 === 2) return rivet;
    return shade(base, y % 3 === 0 ? -4 : 2);
  };
  // Parquet in herringbone: boards two wide and six long, turning each way in turn.
  const herringbone = (base, seam) => (x, y) => {
    const strip = Math.floor(x / 2);
    const along = (y + (strip % 2 === 0 ? strip * 2 : 16 - strip * 2)) % 6;
    if (along === 0 || x % 2 === 0 && strip % 2 === 0) return seam;
    return shade(base, Math.round((hash(strip, Math.floor((y + strip * 2) / 6)) - 0.5) * 22));
  };
  // Tufted padding: a button sunk in each square of eight, the padding rounding up between.
  const padded = (base, button) => (x, y) => {
    const u = (x + 4) % 8;
    const v = (y + 4) % 8;
    if (u === 4 && v === 4) return button;
    const rise = 4 - Math.max(Math.abs(u - 4), Math.abs(v - 4));
    return shade(base, -rise * 3 + 4);
  };
  // Carpet or vinyl: its colour with flecks of a second.
  const flecked = (base, fleck, every) => (x, y) => (hash(x * 3 + 1, y * 5 + 2) < every ? fleck : base);
  // A dado band: its colour with a lighter rail along its top and a darker shadow at its foot.
  const band = (base, rail) => (x, y) => (y <= 1 ? rail : y >= 14 ? shade(base, -16) : base);
  // Hazard stripes running down to the left, yellow and near-black.
  const hazard = (x, y) => ((x + y) % 8 < 4 ? [0xe8, 0xb4, 0x20] : [0x22, 0x22, 0x26]);
  // A club's carpet: deep plum, a lattice of darker diagonals across it and a neon dot where they cross.
  const lattice = (base, line, dot) => (x, y) => {
    const up = (x + y) % 8;
    const down = (x - y + 16) % 8;
    if (up === 0 && down === 0) return dot;
    if (up === 0 || down === 0) return line;
    return hash(x * 7 + 3, y * 11 + 5) < 0.12 ? shade(base, 8) : base;
  };
  // Studded rubber: round studs on a dark mat.
  const studded = (base) => (x, y) => {
    const u = x % 4 - 1.5;
    const v = y % 4 - 1.5;
    return u * u + v * v < 1.5 ? shade(base, 18) : base;
  };
  // Raised diamond tread on steel.
  const diamondPlate = (base) => (x, y) => {
    const u = (x + (Math.floor(y / 4) % 2) * 2) % 4;
    return (u === 1 && y % 4 === 1) || (u === 2 && y % 4 === 2) ? shade(base, 26) : base;
  };
  // Corrugated sheet: ridges running up, lit on one flank.
  const corrugated = (base) => (x) => shade(base, [10, 4, -6, -12][x % 4]);
  // A ceiling of square panels in a light grid, with a lamp dot now and then.
  const ceilingPanels = (base, grid) => (x, y) => (x % 8 === 0 || y % 8 === 0 ? grid : base);
  // A night ceiling: deep colour, a few faint stars.
  const night = (base, star) => (x, y) => (hash(x * 7 + 3, y * 11 + 5) < 0.03 ? star : base);
  // Plain plaster: its colour, the noise doing the rest.
  const plaster = (base) => () => base;
  // Perforated metal: holes in a grid.
  const perforated = (base) => (x, y) => (x % 3 === 1 && y % 3 === 1 ? shade(base, -40) : base);
  // The dome's night sky: deep blue, stars of every brightness, a few of them warm or cool, and the faint glow of the
  // galaxy's band drifting across.
  const starry = (base) => (x, y) => {
    const star = hash(x * 13 + 7, y * 17 + 3);
    if (star < 0.01) return [255, 255, 255];
    if (star < 0.025) return [200, 214, 255];
    if (star < 0.032) return [255, 214, 170];
    if (star < 0.06) return shade(base, 40);
    return Math.abs(((x + y * 2) % 16) - 8) < 2 ? shade(base, 10) : base;
  };
  // Walnut panelling: boards running up with a fine brass line across at the top.
  const walnutPanel = (base, seam) => (x, y) => (y === 1 ? [0xc8, 0xa0, 0x4a] : boards(base, seam, 4)(x, y));

  const OAK = hex(0xc08a50);
  const WALNUT = hex(0x5a3a24);
  const FINISHES = {
    entry: {
      wall: plating(hex(0xb4bcc4), hex(0x7c848c), hex(0x5c646c)), dado: hazard, ledge: plating(hex(0xa4acb4), hex(0x7c848c), hex(0x5c646c)),
      ceiling: perforated(hex(0xc8ccd0)), floor: studded(hex(0x34383c)),
    },
    cargo: {
      wall: boards(hex(0xc8a070), hex(0x8a6a44), 8), dado: plating(hex(0x5c646c), hex(0x3c4248), hex(0x9aa2aa)), ledge: boards(hex(0xc8a070), hex(0x8a6a44), 8),
      ceiling: corrugated(hex(0x9ca4ac)), floor: diamondPlate(hex(0x6c747c)),
    },
    mess: {
      wall: boards(OAK, shade(OAK, -40), 4), dado: band(WALNUT, shade(WALNUT, 30)), ledge: boards(OAK, shade(OAK, -40), 4, true),
      ceiling: plaster(hex(0xf0e4cc)), floor: herringbone(hex(0xa87444), hex(0x6c4426)),
    },
    bunks: {
      wall: quilted(hex(0x6a84a8), hex(0x46587a)), dado: band(hex(0x2a3654), hex(0x46587a)), ledge: plaster(hex(0x2a3654)),
      ceiling: night(hex(0x1c2440), hex(0xc8d4ff)), floor: flecked(hex(0x2c6a70), hex(0x4a8a8a), 0.18),
    },
    greenhouse: {
      wall: bricks(hex(0xece6dc), hex(0xb8b0a4)), dado: tiles(hex(0xc06a40), hex(0x8a4a2c), 8), ledge: tiles(hex(0xc06a40), hex(0x8a4a2c), 8),
      ceiling: ceilingPanels(hex(0xf4f6f0), hex(0xb4c0b0)), floor: tiles(hex(0xb8643c), hex(0x7c4228), 8),
    },
    pool: {
      wall: tiles(hex(0x3a8ac8), hex(0xd8eef8), 4, true), dado: tiles(hex(0x1c4a8a), hex(0xd8eef8), 4, true), ledge: tiles(hex(0xe8f2f6), hex(0xb8c8d0), 8),
      ceiling: tiles(hex(0xf2f6f8), hex(0xc8d4dc), 8), floor: tiles(hex(0xd8d0c0), hex(0xa8a090), 8),
    },
    club: {
      wall: foam(hex(0x2a2430)), dado: band(hex(0x141218), hex(0x7a2aa0)), ledge: plaster(hex(0x18161c)),
      ceiling: night(hex(0x100e14), hex(0xff4ac8)), floor: lattice(hex(0x2c1434), hex(0x1c0c22), hex(0xff4ac8)),
    },
    lab: {
      wall: tiles(hex(0x9cc0a4), hex(0xe8f0ea), 4, true), dado: band(hex(0xeef2ee), hex(0x4ab89a)), ledge: plaster(hex(0xeef2ee)),
      ceiling: ceilingPanels(hex(0xf4f6f6), hex(0xc4ccca)), floor: flecked(hex(0x9aa8a0), hex(0x5c6c64), 0.22),
    },
    lounge: {
      wall: walnutPanel(WALNUT, shade(WALNUT, -24)), dado: band(shade(WALNUT, -16), hex(0xc8a04a)), ledge: boards(WALNUT, shade(WALNUT, -24), 4, true),
      ceiling: plaster(hex(0x22224a)), floor: herringbone(hex(0x6a442a), hex(0x3c2616)),
    },
    cockpit: {
      wall: padded(hex(0x4a4e56), hex(0x24262a)), dado: band(hex(0x24262a), hex(0x5a5e66)), ledge: plaster(hex(0x3a3e46)),
      ceiling: padded(hex(0x3e424a), hex(0x1c1e22)), floor: studded(hex(0x2a2c30)),
    },
    dome: {
      wall: starry(hex(0x0e1636)), dado: starry(hex(0x0b1230)), ledge: starry(hex(0x0e1636)), ceiling: starry(hex(0x0c1434)),
      floor: studded(hex(0x2a2c30)),
    },
  };
  for (const [finish, parts] of Object.entries(FINISHES)) {
    for (const [part, paint] of Object.entries(parts)) {
      texture(blockOutput, `rocket_${part}_${finish}`, paint, 3);
    }
    // No window is set in the dome.
    if (finish === "dome") {
      continue;
    }
    // The wall round each oval window's oval inside, in the room's own wall, the hole and the lip left clear; its
    // lowest row, on the floor, the dado along the wall's foot, as the wall beside it is.
    for (const { column, row, lowest, wall } of OVAL_WALLS) {
      const paint = lowest ? parts.dado : parts.wall;
      texture(blockOutput, `oval_window_${column}_${row}_inside_${finish}`, (x, y) => (wall(x, y) ? paint(x, y) : [0, 0, 0, 0]), 3);
    }
  }
}

foodLabTextures();

orreryTextures();

// The rocket's orrery's mount (see OrreryBlock): polished brass, lit from its top left, for its model.
function orreryTextures() {
  const blend = (from, to, t) => from.map((c, i) => Math.round(c + (to[i] - c) * Math.max(0, Math.min(1, t))));
  const BRASS = [200, 154, 72];
  const brass = (x, y) => (x + y < 9 ? blend(BRASS, [244, 214, 140], 0.6) : x + y > 24 ? blend(BRASS, [120, 84, 30], 0.5) : BRASS);
  texture(blockOutput, "orrery_brass", brass, 4);
}

domeSkyTexture();
orreryGlowTexture();
orrerySheetTexture();

// Smooth noise over the sky, for the planetarium's clouds and mottling: value noise on a lattice in three dimensions,
// so a pattern sampled round a sphere has no seam, and its sum over a few octaves.
function latticeValue(x, y, z) {
  let h = Math.imul(x, 0x27d4eb2d) ^ Math.imul(y, 0x165667b1) ^ Math.imul(z, 0x61c88647);
  h = Math.imul(h ^ (h >>> 15), 0x2c1b3c6d);
  h = Math.imul(h ^ (h >>> 12), 0x297a2d39);
  return ((h ^ (h >>> 15)) >>> 0) / 4294967296;
}
function valueNoise(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z);
  const ease = (t) => t * t * (3 - 2 * t);
  const fx = ease(x - xi), fy = ease(y - yi), fz = ease(z - zi);
  const mix = (a, b, t) => a + (b - a) * t;
  const at = (dx, dy, dz) => latticeValue(xi + dx, yi + dy, zi + dz);
  const near = mix(mix(at(0, 0, 0), at(1, 0, 0), fx), mix(at(0, 1, 0), at(1, 1, 0), fx), fy);
  const far = mix(mix(at(0, 0, 1), at(1, 0, 1), fx), mix(at(0, 1, 1), at(1, 1, 1), fx), fy);
  return mix(near, far, fz);
}
function cloudNoise([x, y, z], scale, octaves) {
  let sum = 0;
  let weight = 0.5;
  let total = 0;
  for (let octave = 0; octave < octaves; octave++) {
    const f = scale * 2.03 ** octave;
    sum += weight * valueNoise(x * f + octave * 17.3, y * f + octave * 5.1, z * f + octave * 11.7);
    total += weight;
    weight /= 2;
  }
  return sum / total;
}

// The planetarium's sky round the rocket's dome (see PlanetariumShell), its width once round the dome and its height
// from the tip of her nose (the top row) down to the dome's foot over her cockpit (the bottom row), each pixel painted
// where it lies on the sky, so nothing repeats: deep navy at the foot darkening to near black at the tip, the Milky Way
// arching over in clouds of violet, teal and rose with a dark lane of dust along it and its stars crowded thick, stars
// of every brightness in blue-white, white, yellow and orange, the brightest few a little cross of light, Orion, the
// Plough, Cassiopeia and Cygnus joined by faint gold lines, the pink glow of Orion's nebula, and the pole star at the very
// tip. It is the real northern sky, squeezed so that it reaches from the pole at the tip down past Orion at the foot,
// and laid as it is seen from inside, not mirrored.
function domeSkyTexture() {
  // How many pixels the sky has for each of a 512 by 256 picture's: fine enough that a star is a point of light even
  // in the biggest rocket's dome, and the clouds of the Milky Way are smooth.
  const DETAIL = 2;
  const WIDTH = 512 * DETAIL;
  const HEIGHT = 256 * DETAIL;
  const DEGREE = Math.PI / 180;
  // How much of the dome's height, tip to foot, a degree of the sky's from the pole takes: under 1, so the dome's foot
  // reaches south past the celestial equator, down to Orion and beyond.
  const SQUEEZE = 0.7;
  // How big the constellations are drawn, in pixels for a degree of the sky either way: the dome's pixels are about
  // square on it, each row's as long as it is round there, so a figure keeps its shape at any height.
  const FIGURE = 1.6 * DETAIL;
  const FOOT = [0x0f, 0x1a, 0x3a];
  const TIP = [0x04, 0x06, 0x0e];
  const LINE = [0x7a, 0x62, 0x30];
  const NEBULA = [0xd0, 0x60, 0x8a];
  const VIOLET = [0x6e, 0x50, 0xaa];
  const TEAL = [0x32, 0x96, 0xa0];
  const ROSE = [0xbe, 0x5f, 0x87];
  const STAR_COLOURS = [[200, 216, 255], [255, 255, 255], [255, 240, 190], [255, 196, 140]];
  // How likely each of those colours is, in the same order.
  const STAR_SHARES = [0.4, 0.35, 0.15, 0.1];
  // How many stars are scattered all over, and how many more are tried along the Milky Way, kept as thickly as it glows.
  const SCATTERED = 2000;
  const BAND_TRIED = 1800;
  // The share of the stars, the brightest, drawn as a little cross, and the next brightest, as a smaller one.
  const CROSSED = 0.01;
  const GLINTING = 0.05;
  // How bright the faintest star is, a share of the brightest.
  const FAINTEST = 0.2;
  // The north galactic pole, in degrees of right ascension and declination: the Milky Way runs round its great circle.
  const GALACTIC_POLE = [192.86, 27.13];
  // How strongly the gold lines and the nebula show over the sky, a share.
  const LINE_STRENGTH = 0.6;
  const NEBULA_STRENGTH = 0.55;
  // The constellations: each star's right ascension, declination and brightness, and the lines between them.
  const FIGURES = [
    {
      // Orion: Betelgeuse, Bellatrix, Meissa, his belt (Mintaka, Alnilam, Alnitak), Saiph and Rigel; his nebula under
      // the belt.
      centre: [84, 0],
      stars: [[88.8, 7.4, 1], [81.3, 6.35, 0.85], [83.8, 9.9, 0.55], [83.0, -0.3, 0.75], [84.05, -1.2, 0.8], [85.2, -1.9, 0.8],
        [86.9, -9.7, 0.75], [78.6, -8.2, 1]],
      lines: [[0, 2], [2, 1], [0, 5], [1, 3], [3, 4], [4, 5], [5, 6], [3, 7]],
      nebula: [83.8, -5.4],
    },
    {
      // The Plough: its bowl, Dubhe, Merak, Phecda and Megrez, and its handle, Alioth, Mizar and Alkaid.
      centre: [186, 56],
      stars: [[165.9, 61.75, 0.9], [165.5, 56.4, 0.8], [178.5, 53.7, 0.75], [183.9, 57.0, 0.6], [193.5, 55.96, 0.9], [200.98, 54.9, 0.85],
        [206.9, 49.3, 0.85]],
      lines: [[0, 1], [1, 2], [2, 3], [3, 0], [3, 4], [4, 5], [5, 6]],
    },
    {
      // Cassiopeia's W.
      centre: [15, 60],
      stars: [[2.3, 59.15, 0.8], [10.1, 56.5, 0.85], [14.2, 60.7, 0.85], [21.45, 60.2, 0.75], [28.6, 63.7, 0.7]],
      lines: [[0, 1], [1, 2], [2, 3], [3, 4]],
    },
    {
      // Cygnus, the swan's cross along the Milky Way: Deneb, Sadr, Albireo, Gienah and Fawaris.
      centre: [303, 38],
      stars: [[310.4, 45.3, 1], [305.6, 40.3, 0.8], [292.7, 27.96, 0.7], [311.6, 33.97, 0.75], [296.2, 45.1, 0.7]],
      lines: [[0, 1], [1, 2], [4, 1], [1, 3]],
    },
  ];
  reseed("dome_sky");
  const mix = (a, b, t) => a.map((c, i) => c + (b[i] - c) * t);
  const smoothstep = (from, to, t) => {
    const s = Math.max(0, Math.min(1, (t - from) / (to - from)));
    return s * s * (3 - 2 * s);
  };
  // Where a point of the dome is, so far round (0 to 1) and so far up from its foot (radians), on a sphere for the noise.
  const direction = (u, lat) => [Math.cos(lat) * Math.cos(2 * Math.PI * u), Math.sin(lat), Math.cos(lat) * Math.sin(2 * Math.PI * u)];
  // How far a point of the dome is from the Milky Way's middle, in radians, from where it is in the sky.
  const galactic = (u, lat) => {
    const ra = 360 * (1 - u) * DEGREE;
    const dec = (90 - (90 - lat / DEGREE) / SQUEEZE) * DEGREE;
    const [poleRa, poleDec] = GALACTIC_POLE.map((angle) => angle * DEGREE);
    return Math.asin(Math.sin(dec) * Math.sin(poleDec) + Math.cos(dec) * Math.cos(poleDec) * Math.cos(ra - poleRa));
  };
  const bandGlow = (b) => Math.exp(-((b / 0.2) ** 2)) + 0.35 * Math.exp(-((b / 0.45) ** 2));

  const sky = [];
  for (let py = 0; py < HEIGHT; py++) {
    const lat = (1 - (py + 0.5) / HEIGHT) * Math.PI / 2;
    const row = [];
    for (let px = 0; px < WIDTH; px++) {
      row.push(skyPixel((px + 0.5) / WIDTH, lat, (py + 0.5) / HEIGHT));
    }
    sky.push(row);
  }

  // The sky behind the stars at a point: its colour down the dome, faintly mottled, and the Milky Way's glow over it.
  function skyPixel(u, lat, down) {
    const at = direction(u, lat);
    const base = mix(TIP, FOOT, down ** 0.8).map((c) => c * (0.9 + 0.2 * cloudNoise(at, 6, 3)));
    const b = galactic(u, lat);
    const clouds = Math.max(0, (cloudNoise(at, 5, 5) - 0.35) / 0.65) ** 1.3;
    const hue = cloudNoise(at.map((c) => c + 3.7), 2.5, 3);
    const colour = mix(mix(VIOLET, TEAL, smoothstep(0.5, 0.62, hue)), ROSE, smoothstep(0.42, 0.3, hue));
    const laneMiddle = 0.04 * (cloudNoise(at.map((c) => c - 2.2), 4, 3) - 0.5);
    const lane = Math.exp(-(((b - laneMiddle) / 0.035) ** 2)) * (0.5 + 0.6 * cloudNoise(at, 7, 3));
    const glow = bandGlow(b) * (0.25 + clouds) * (1 - Math.min(0.85, lane)) * 0.45;
    return base.map((c, i) => c + colour[i] * glow);
  }

  // The constellations' gold lines, over the sky.
  const placed = FIGURES.map((figure) => figure.stars.map(([ra, dec, bright]) => [...place(figure.centre, ra, dec), bright]));
  const lines = new Float32Array(WIDTH * HEIGHT);
  FIGURES.forEach((figure, f) => figure.lines.forEach(([from, to]) => line(placed[f][from], placed[f][to])));
  for (let i = 0; i < lines.length; i++) {
    const [x, y] = [i % WIDTH, Math.floor(i / WIDTH)];
    sky[y][x] = mix(sky[y][x], LINE, LINE_STRENGTH * lines[i]);
  }

  // Where a star of a constellation goes, in pixels: the constellation's middle where it is in the squeezed sky, and the
  // star round it as far and in the same direction as it is in the real sky, at the figures' size, so its shape is kept;
  // laid on the inside, as it is seen, so east is toward the left of the picture.
  function place(centre, ra, dec) {
    const middleX = (((1 - centre[0] / 360) % 1) + 1) % 1 * WIDTH;
    const middleY = ((90 - centre[1]) * SQUEEZE / 90) * HEIGHT;
    const east = ((((ra - centre[0]) % 360) + 540) % 360 - 180) * Math.cos(centre[1] * DEGREE);
    const y = middleY - (dec - centre[1]) * FIGURE;
    const lat = (1 - y / HEIGHT) * 90;
    return [middleX - (east * FIGURE) / Math.cos(lat * DEGREE), y];
  }

  // A gold line between two stars, round the shorter way, stopping short of each, as many pixels wide as the detail.
  function line([x0, y0], [x1, y1]) {
    let dx = x1 - x0;
    if (Math.abs(dx) > WIDTH / 2) dx -= Math.sign(dx) * WIDTH;
    const dy = y1 - y0;
    const length = Math.hypot(dx, dy);
    const GAP = 2.5 * DETAIL;
    for (let s = GAP; s <= length - GAP; s += 0.3) {
      lineDot(Math.floor(x0 + (dx * s) / length), Math.floor(y0 + (dy * s) / length));
    }
  }

  // A dot of a gold line, as many pixels across as the detail, from its top left corner.
  function lineDot(left, top) {
    for (let dot = 0; dot < DETAIL * DETAIL; dot++) {
      const x = ((left + (dot % DETAIL)) % WIDTH + WIDTH) % WIDTH;
      const y = top + Math.floor(dot / DETAIL);
      if (y >= 0 && y < HEIGHT) lines[y * WIDTH + x] = 1;
    }
  }

  // Orion's nebula: a soft pink glow, a little wider than tall, its clouds uneven.
  const orion = FIGURES[0];
  const [nebulaX, nebulaY] = place(orion.centre, ...orion.nebula);
  const NEBULA_SIZE = 7 * DETAIL;
  const nebulaWide = 4 * NEBULA_SIZE + 1;
  for (let at = 0; at < nebulaWide * (2 * NEBULA_SIZE + 1); at++) {
    const dx = (at % nebulaWide) - 2 * NEBULA_SIZE;
    const dy = Math.floor(at / nebulaWide) - NEBULA_SIZE;
    const r = Math.hypot(dx / 1.3, dy) / NEBULA_SIZE;
    const x = ((Math.round(nebulaX) + dx) % WIDTH + WIDTH) % WIDTH;
    const y = Math.round(nebulaY) + dy;
    if (r >= 1 || y < 0 || y >= HEIGHT) continue;
    const strength = NEBULA_STRENGTH * (1 - r) ** 2 * (0.6 + 0.6 * cloudNoise([x / (9 * DETAIL), y / (9 * DETAIL), 1.5], 1, 3));
    sky[y][x] = sky[y][x].map((c, i) => c + NEBULA[i] * strength);
  }

  // The stars, scattered evenly over the dome (as much in each row's length as in any other's) and crowded along the
  // Milky Way, each drawn no dimmer than what is under it.
  const stars = [];
  const randomStar = () => {
    const lat = Math.asin(random());
    const u = random();
    const pick = random();
    let colour = 0;
    for (let share = STAR_SHARES[0]; pick > share && colour < STAR_SHARES.length - 1; share += STAR_SHARES[++colour]);
    return { u, lat, bright: random() ** 4, colour: STAR_COLOURS[colour] };
  };
  for (let i = 0; i < SCATTERED; i++) stars.push(randomStar());
  for (let i = 0; i < BAND_TRIED; i++) {
    const star = randomStar();
    if (random() < Math.min(1, bandGlow(galactic(star.u, star.lat)))) stars.push(star);
  }
  stars.sort((a, b) => b.bright - a.bright);
  const crossed = Math.round(stars.length * CROSSED);
  const glinting = Math.round(stars.length * GLINTING);
  stars.forEach((star, i) => {
    const x = Math.floor(star.u * WIDTH);
    const y = Math.min(HEIGHT - 1, Math.floor((1 - star.lat / (Math.PI / 2)) * HEIGHT));
    const arms = i < crossed ? DETAIL : i < glinting ? 1 : 0;
    starAt(x, y, star.colour, i < crossed ? 1 : FAINTEST + (1 - FAINTEST) * star.bright, arms);
  });
  placed.flat().forEach(([x, y, bright]) => starAt(Math.floor(x), Math.floor(y), STAR_COLOURS[1], bright, bright >= 0.8 ? DETAIL : 1));

  // A star: a point of its colour at its brightness, with a little cross of arms so many pixels long, fading out.
  function starAt(x, y, colour, bright, arms) {
    const light = (px, py, share) => {
      if (py < 0 || py >= HEIGHT) return;
      const wrapped = (px % WIDTH + WIDTH) % WIDTH;
      sky[py][wrapped] = sky[py][wrapped].map((c, i) => Math.max(c, colour[i] * share));
    };
    light(x, y, bright);
    for (let out = 1; out <= arms; out++) {
      const share = bright * 0.55 ** out;
      [[out, 0], [-out, 0], [0, out], [0, -out]].forEach(([dx, dy]) => light(x + dx, y + dy, share));
    }
  }

  // The pole star at the very tip, where the top rows close round to a point.
  for (let x = 0; x < WIDTH; x++) {
    for (let row = 0; row < 2 * DETAIL; row++) {
      sky[row][x] = row < DETAIL ? [235, 240, 255] : mix(sky[row][x], [235, 240, 255], 0.6);
    }
  }
  writeFileSync(resolve(miscOutput, "dome_sky.png"), encodePng(sky.map((row) => row.map((c) => c.map((v) => clamp(Math.round(v)))))));
}

// The sheet the orrery's renderer draws the solid parts of the orrery and its dome from (see OrrerySheet), 256 by 128,
// each picture at its place on it. The sun and the planets are each painted once round (left to right) from the north
// pole (top) to the south (bottom), every pixel where it lies on the globe so it wraps without a seam: the sun's
// boiling granules, orange in the lanes between them and white-hot in their middles, with a few dark sunspots in their
// grey penumbrae, at 0, 0, 64 by 32; Jupiter's cream, tan and brown bands, wavy where they meet, and his Great Red Spot,
// at 64, 0; Saturn's soft pale gold bands, at 128, 0; then, 32 by 16, the Earth's blue seas, green and sandy continents,
// white poles and drifting clouds, at 192, 0; Venus's cream clouds swirled into chevrons, at 224, 0; rusty Mars with his
// darker lands and white polar caps, at 192, 16; cratered grey Mercury, at 224, 16; and the Moon's grey with its dark
// seas and bright craters, at 0, 32. Saturn's ring, from its inner edge (left) to its outer, 128 by 8 at 32, 32: the
// faint inner ring, the bright broad one, the dark gap of the Cassini division, the outer ring with its thin gap, and
// clear past it. Polished brass, brushed along, at 160, 32; cool brushed steel, at 192, 32; the dark gunmetal of the
// machined ring round the hole over the cockpit, fine bright lines turned round it, at 224, 32. The orrery's two gears,
// brass discs with their teeth round them and windows between their spokes, clear outside, 64 across: the big one with
// twenty-four teeth at 0, 64, the small one with sixteen at 64, 64. A lamp's window in the machined ring, smoked glass
// in a bright bezel, at 128, 64, 16 across.
function orrerySheetTexture() {
  const WIDTH = 256;
  const HEIGHT = 128;
  reseed("orrery_sheet");
  const pixels = Array.from({ length: HEIGHT }, () => Array.from({ length: WIDTH }, () => [0, 0, 0, 0]));
  // A picture so many pixels across and down with its top left corner at a point, painted pixel by pixel from there.
  const picture = (left, top, width, height, paint) => {
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        const [r, g, b, a = 255] = paint(x, y);
        pixels[top + y][left + x] = [clamp(Math.round(r)), clamp(Math.round(g)), clamp(Math.round(b)), a];
      }
    }
  };
  // A globe's picture: each pixel painted from where it lies on the globe, the way out to it and how far north it is,
  // in radians, and how far round, a share.
  const globePicture = (left, top, width, height, paint) => picture(left, top, width, height, (x, y) => {
    const round = (x + 0.5) / width;
    const lat = Math.PI * (0.5 - (y + 0.5) / height);
    const lon = 2 * Math.PI * round;
    return paint([Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)], lat, round, x, y);
  });
  const mix = (a, b, t) => a.map((c, i) => c + (b[i] - c) * Math.max(0, Math.min(1, t)));
  const smoothstep = (from, to, t) => {
    const s = Math.max(0, Math.min(1, (t - from) / (to - from)));
    return s * s * (3 - 2 * s);
  };
  // How far apart two points of a globe are, in radians, by the way out to each.
  const apart = (a, b) => Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2])));
  const toward = (latDegrees, round) => {
    const lat = (latDegrees * Math.PI) / 180;
    const lon = 2 * Math.PI * round;
    return [Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)];
  };

  // The sun: granules, and a few spots, each its latitude in degrees, how far round, and how big, in radians.
  const SUNSPOTS = [[14, 0.12, 0.13], [-18, 0.4, 0.1], [9, 0.63, 0.15], [-11, 0.86, 0.09], [21, 0.7, 0.08]];
  globePicture(0, 0, 64, 32, (at) => {
    const granules = cloudNoise(at, 9, 3);
    let colour = mix([246, 150, 44], [255, 250, 220], smoothstep(0.22, 0.58, granules));
    for (const [lat, round, size] of SUNSPOTS) {
      const off = apart(at, toward(lat, round));
      colour = mix(colour, [176, 82, 24], smoothstep(size, size * 0.6, off));
      colour = mix(colour, [62, 22, 8], smoothstep(size * 0.55, size * 0.3, off));
    }
    return colour;
  });

  // Jupiter: bands by how far north, the edges between them wavy, and the Great Red Spot.
  const JUPITER_BANDS = [[234, 222, 196], [206, 168, 122], [238, 226, 200], [168, 116, 78], [226, 206, 168], [196, 140, 94], [236, 222, 192],
    [180, 128, 86], [222, 198, 160], [204, 176, 136]];
  globePicture(64, 0, 64, 32, (at, lat, round) => {
    const wavy = lat + 0.07 * (cloudNoise(at, 5, 3) - 0.5) + 0.03 * Math.sin(round * 2 * Math.PI * 7 + lat * 9);
    const band = (wavy / Math.PI + 0.5) * JUPITER_BANDS.length;
    const index = Math.max(0, Math.min(JUPITER_BANDS.length - 1, Math.floor(band)));
    const next = Math.min(JUPITER_BANDS.length - 1, index + 1);
    let colour = mix(JUPITER_BANDS[index], JUPITER_BANDS[next], smoothstep(0.75, 1, band - index));
    const spot = Math.hypot((((round - 0.3 + 1.5) % 1) - 0.5) / 0.075, (lat + 0.38) / 0.09);
    colour = mix(colour, [236, 214, 186], smoothstep(1.35, 1.05, spot));
    return mix(colour, [196, 84, 58], smoothstep(1.0, 0.6, spot));
  });

  // Saturn: soft pale gold bands, a little darker toward his poles.
  globePicture(128, 0, 64, 32, (at, lat) => {
    const bands = Math.sin(lat * 14 + 0.6 * cloudNoise(at, 4, 2)) * 0.5 + 0.5;
    const pale = mix([214, 186, 128], [240, 220, 168], bands);
    return mix(pale, [176, 156, 112], smoothstep(0.9, 1.4, Math.abs(lat)));
  });

  // The Earth's continents, once round from the date line eastward and from the north pole down, a cell for each
  // eleven and a quarter degrees: land, sea and ice.
  const EARTH = [
    "********************************",
    ".#########.***....##############",
    ".#######.#.*..#.################",
    "...########....##############.#.",
    "....######.....#.#############..",
    ".....####.....##############....",
    ".......##.....########.#.###....",
    "........###....######....##.....",
    "........#####...####.....#####..",
    ".........####...#####.....####..",
    ".........###....###.......####..",
    ".........##...................#.",
    ".........#......................",
    "..........*.....................",
    "**********.********.************",
    "********************************",
  ];
  // Where the land is desert: the Sahara and Arabia, and the middle of Australia.
  const DESERT = (column, row) => (row >= 4 && row <= 6 && column >= 14 && column <= 22) || (row >= 9 && row <= 10 && column >= 27 && column <= 28);
  globePicture(192, 0, 32, 16, (at, lat, round, x, y) => {
    const cell = EARTH[y][x];
    const shade = cloudNoise(at, 6, 2) - 0.5;
    let colour = cell === "*" ? [236, 242, 248] : cell === "#" ? (DESERT(x, y) ? [196, 168, 108] : [64, 138, 70]) : [30, 78, 168];
    colour = colour.map((c) => c * (1 + shade * 0.25));
    return mix(colour, [246, 248, 252], 0.85 * smoothstep(0.58, 0.72, cloudNoise(at.map((c) => c + 4.1), 4, 3)));
  });

  // Venus: thick cream clouds, swirled into chevrons pointing along her equator.
  globePicture(224, 0, 32, 16, (at, lat) => {
    const swirled = [at[0] + Math.abs(lat) * 0.9, at[1], at[2] - Math.abs(lat) * 0.6];
    return mix([212, 176, 116], [244, 228, 182], cloudNoise(swirled, 3, 3) * 1.3 - 0.15);
  });

  // Mars: rust, darker lands across him, and white caps at his poles.
  globePicture(192, 16, 32, 16, (at, lat) => {
    const rust = mix([198, 96, 52], [124, 60, 40], smoothstep(0.5, 0.65, cloudNoise(at, 3, 3)));
    return mix(rust, [244, 240, 234], smoothstep(1.05, 1.2, Math.abs(lat)));
  });

  // Craters: on a globe's grey, each a dark floor in a bright rim, scattered by the noise.
  const cratered = (base, dark, seas) => (at) => {
    let colour = mix(base, dark, seas * smoothstep(0.5, 0.62, cloudNoise(at, 2, 3)));
    const pits = cloudNoise(at.map((c) => c + 7.3), 10, 2);
    colour = mix(colour, colour.map((c) => c * 0.62), smoothstep(0.66, 0.74, pits));
    return mix(colour, colour.map((c) => Math.min(255, c * 1.25)), smoothstep(0.6, 0.66, pits) - smoothstep(0.66, 0.7, pits));
  };
  globePicture(224, 16, 32, 16, cratered([150, 144, 138], [104, 100, 96], 0.5));
  globePicture(0, 32, 32, 16, cratered([196, 196, 192], [112, 112, 112], 1));

  // Saturn's ring, out from its inner edge: each band's outer edge as a share of the way out and its colour, clear for
  // a gap.
  const RING = [[0.16, [122, 108, 84]], [0.2, [150, 132, 100]], [0.62, [226, 206, 162]], [0.69, null], [0.88, [204, 184, 142]], [0.9, null],
    [0.97, [196, 176, 136]], [1.01, null]];
  picture(32, 32, 128, 8, (x) => {
    const out = (x + 0.5) / 128;
    const [, colour] = RING.find(([edge]) => out < edge);
    if (colour === null) return [0, 0, 0, 0];
    const ripple = Math.sin(out * 170) * 7 + Math.sin(out * 61) * 5;
    return colour.map((c) => c + ripple);
  });

  const BRASS = [200, 156, 74];
  const brushed = (base, depth) => (x, y) => {
    const streak = (random() - 0.5) * depth + Math.sin(y * 1.7 + Math.sin(x * 0.3) * 0.6) * depth * 0.4;
    return [...base.map((c) => c + streak), 255];
  };
  picture(160, 32, 32, 32, (x, y) => {
    const [r, g, b, a] = brushed(BRASS, 14)(x, y);
    const shine = Math.exp(-(((y - 10) / 6) ** 2)) * 46;
    return [r + shine, g + shine * 0.9, b + shine * 0.6, a];
  });
  picture(192, 32, 32, 32, (x, y) => {
    const [r, g, b, a] = brushed([150, 158, 168], 12)(x, y);
    const shine = Math.exp(-(((y - 20) / 5) ** 2)) * 40;
    return [r + shine, g + shine, b + shine, a];
  });
  const GUNMETAL = [58, 64, 74];
  picture(224, 32, 32, 32, (x, y) => {
    const [r, g, b, a] = brushed(GUNMETAL, 8)(x, y);
    const groove = y % 8 === 0 ? -14 : y % 8 === 1 ? 10 : 0;
    return [r + groove, g + groove, b + groove + 2, a];
  });

  // A gear: brass turned bright round its hub, its teeth round its rim, windows between its spokes and a hole for its
  // axle, clear outside.
  const gear = (teeth, spokes) => (x, y) => {
    const out = Math.hypot(x - 31.5, y - 31.5) / 32;
    const angle = Math.atan2(y - 31.5, x - 31.5);
    const tooth = ((((angle / (2 * Math.PI)) * teeth) % 1) + 1) % 1;
    const toothTip = 0.86 + 0.13 * smoothstep(0.38, 0.3, Math.abs(tooth - 0.5));
    const spoke = ((((angle / (2 * Math.PI)) * spokes) % 1) + 1) % 1;
    if (out > toothTip || out < 0.07 || (out > 0.3 && out < 0.7 && spoke > 0.16 && spoke < 0.84)) return [0, 0, 0, 0];
    const turned = 0.85 + 0.15 * Math.cos(angle * 2 - 0.8);
    const engraved = Math.abs(out - 0.76) < 0.02 || Math.abs(out - 0.26) < 0.02 ? 0.72 : 1;
    return BRASS.map((c) => c * turned * engraved * (out > 0.86 ? 0.9 : 1));
  };
  picture(0, 64, 64, 64, gear(24, 6));
  picture(64, 64, 64, 64, gear(16, 4));

  picture(128, 64, 16, 16, (x, y) => {
    const edge = Math.min(x, y, 15 - x, 15 - y);
    if (edge === 0) return [34, 38, 44, 255];
    if (edge === 1) return [168, 174, 182, 255];
    const glint = x + y < 12 && x - y > -3 && x - y < 3 ? 26 : 0;
    return [18 + glint, 22 + glint, 28 + glint, 255];
  });
  writeFileSync(resolve(miscOutput, "orrery_sheet.png"), encodePng(pixels));
}

// The light the orrery's renderer adds over its dome and its sun (see OrreryRenderer), 256 by 128, smoothed and not
// repeated past its edges, each picture white on black for the renderer to tint, a black margin round it: the sun's
// corona, bright at its edge and fading out in soft streamers, at 0, 0, 64 across; the wide soft glow round it, at
// 64, 0; its eight rays, long and short by turns, at 128, 0; the sparkle of a twinkling star, a soft core with four thin
// points, at 192, 0, 32 across; the light in one of the oculus ring's lamps, a soft-edged rounded oblong, at 224, 0;
// plain white, for the orbits' tracks and the Earth's air, at 192, 32; and the sun's boiling skin, bright granules
// round it with dark lanes between, painted where each pixel lies on its globe so it wraps without a seam, 128 by 64 at
// 0, 64.
function orreryGlowTexture() {
  const WIDTH = 256;
  const HEIGHT = 128;
  const pixels = Array.from({ length: HEIGHT }, () => Array.from({ length: WIDTH }, () => [0, 0, 0, 255]));
  const grey = (v) => {
    const level = clamp(Math.round(255 * Math.max(0, Math.min(1, v))));
    return [level, level, level, 255];
  };
  // A picture so many pixels across with its top left corner at a point, painted from -1 to 1 each way from its middle.
  const picture = (left, top, size, paint) => {
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        pixels[top + y][left + x] = grey(paint(((x + 0.5) / size) * 2 - 1, ((y + 0.5) / size) * 2 - 1));
      }
    }
  };
  const fadeOut = (r) => Math.max(0, Math.min(1, (0.98 - r) / 0.2));
  // The sun's edge in the corona's picture, which is 2.4 times as wide as the sun, and in the glow's, 5 times.
  const CORONA_LIMB = 1 / 2.4;
  picture(0, 0, 64, (x, y) => {
    const r = Math.hypot(x, y);
    const streamers = 0.8 + 0.2 * Math.cos(Math.atan2(y, x) * 5) * Math.cos(Math.atan2(y, x) * 3);
    return (r < CORONA_LIMB ? 1 : Math.exp(-(r - CORONA_LIMB) / (0.13 * streamers))) * fadeOut(r);
  });
  picture(64, 0, 64, (x, y) => {
    const r = Math.hypot(x, y);
    return Math.exp(-((r / 0.45) ** 2)) * fadeOut(r);
  });
  picture(128, 0, 64, (x, y) => {
    const r = Math.hypot(x, y);
    const turn = ((Math.atan2(y, x) / (2 * Math.PI)) * 8 + 8.5) % 8;
    const ray = Math.floor(turn);
    const across = r * Math.sin(((turn - ray - 0.5) * 2 * Math.PI) / 8);
    const length = ray % 2 === 0 ? 0.98 : 0.66;
    return Math.exp(-((across / 0.035) ** 2)) * Math.max(0, 1 - r / length) ** 1.4 * fadeOut(r);
  });
  picture(192, 0, 32, (x, y) => {
    const core = Math.exp(-((Math.hypot(x, y) / 0.18) ** 2));
    const point = (across, along) => Math.exp(-((across / 0.06) ** 2)) * Math.max(0, 1 - Math.abs(along) / 0.9) ** 2.5;
    return core + point(x, y) + point(y, x);
  });
  picture(224, 0, 32, (x, y) => {
    const outside = Math.hypot(Math.max(0, Math.abs(x) - 0.45), Math.max(0, Math.abs(y) - 0.3));
    return Math.max(0, 1 - outside / 0.4) ** 2 * (1 - 0.25 * Math.hypot(x, y));
  });
  picture(192, 32, 32, (x, y) => (Math.max(Math.abs(x), Math.abs(y)) < 0.75 ? 1 : 0));
  for (let y = 0; y < 64; y++) {
    const lat = Math.PI * (0.5 - (y + 0.5) / 64);
    for (let x = 0; x < 128; x++) {
      const lon = (2 * Math.PI * (x + 0.5)) / 128;
      const at = [Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)];
      const granules = cloudNoise(at.map((c) => c + 2.9), 11, 3);
      pixels[64 + y][x] = grey(Math.max(0, (granules - 0.48) / 0.3) ** 1.5);
    }
  }
  writeFileSync(resolve(miscOutput, "orrery_glow.png"), encodePng(pixels));
  writeFileSync(resolve(miscOutput, "orrery_glow.png.mcmeta"), JSON.stringify({ texture: { blur: true, clamp: true } }, null, 2) + "\n");
}

// The lab's food lab (see foodLabModels in generate_resources.mjs). The bioreactor's broth, pink and glowing, bubbles
// rising through it a pixel a frame, a streak of the glass down one edge; the clear glass of its headspace; the steak,
// muscle pink striped along its fibres and marbled with fat; and its panel at each stage of growth, a bar more lit green
// for each, by its pink lamp. The hydroponic rack's violet grow light, its lettuces' frilled leaves, and its basil and
// strawberry plants. The algae column's green, swirling and bubbling. The centrifuge's lid, its window over the rotor's
// tubes in the vials' six colours, or spinning, a blur with a glint going round; and its panel's dial and lamp, green or
// amber. The partition's glass, and its frosted band with a green sprout on it, the food lab's sign. The pipe's brushed
// steel and its joint's green band. The specimen jars' cork, their liquids, a pickled tentacle, an eye and a potato
// sprouting. The microscope's eyepiece, a round field in the dark with a fine crosshair and a scale bar, and its four
// slides, each two frames: a water bear paddling, moss in its gut; algae cells jostling; muscle fibres twitching; and
// diatoms' glass shells shimmering on a dark field. And the Lab-Grown Steak in its petri dish.
function foodLabTextures() {
  const guiOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/gui/microscope");
  mkdirSync(guiOutput, { recursive: true });
  // A random sequence and a smooth value noise of the food lab's own, so the textures above are unchanged.
  let state = 0x466f6f64;
  const next = () => {
    state = (state + 0x6d2b79f5) | 0;
    let value = Math.imul(state ^ (state >>> 15), 1 | state);
    value = (value + Math.imul(value ^ (value >>> 7), 61 | value)) ^ value;
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
  const hash = (x, y) => {
    let h = Math.imul(x * 2654435761 + y * 1597334677, 0x2c1b3c6d) ^ 0x466c;
    h = Math.imul(h ^ (h >>> 15), 0x297a2d39);
    return ((h ^ (h >>> 13)) >>> 0) / 4294967296;
  };
  const smooth = (x, y, cell) => {
    const [cx, cy] = [Math.floor(x / cell), Math.floor(y / cell)];
    const [fx, fy] = [x / cell - cx, y / cell - cy];
    const [sx, sy] = [fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)];
    const top = hash(cx, cy) * (1 - sx) + hash(cx + 1, cy) * sx;
    const bottom = hash(cx, cy + 1) * (1 - sx) + hash(cx + 1, cy + 1) * sx;
    return top * (1 - sy) + bottom * sy;
  };
  const shade = ([r, g, b, a], by) => [...[r, g, b].map((c) => Math.round(Math.max(0, Math.min(255, c * by)))), ...(a === undefined ? [] : [a])];
  const mix = (from, to, t) => from.map((c, i) => Math.round(c + ((to[i] ?? c) - c) * Math.max(0, Math.min(1, t))));
  const FRAMES = 16;
  const animated = (name, frames, frametime, paint) => {
    texture(blockOutput, name, (x, y) => paint(x, y % SIZE, Math.floor(y / SIZE)), 0, SIZE, SIZE * frames);
    writeFileSync(resolve(blockOutput, `${name}.png.mcmeta`), JSON.stringify({ animation: { frametime } }, null, 2) + "\n");
  };

  // Bubbles in a liquid: columns of them, each its own way across and its own start up the block, rising a pixel a
  // frame so a loop of sixteen frames brings each back where it began.
  const bubbleColumns = (columns) => columns.map(([x, start]) => ({ x, start }));
  const bubbleAt = (columns, x, y, frame) => columns.some((column) => column.x === x && (column.start - frame - y + 64) % 8 === 0);
  const BROTH = [0xf8, 0x9c, 0xbc, 0x68];
  const BROTH_BUBBLE = [0xff, 0xe4, 0xee, 0xd0];
  const brothBubbles = bubbleColumns([[3, 0], [5, 5], [8, 2], [10, 6], [12, 3]]);
  animated("bioreactor_broth", FRAMES, 3, (x, y, frame) => {
    if (x === 2) return [0xff, 0xd8, 0xe6, 0xb0];
    if (bubbleAt(brothBubbles, x, y, frame)) return BROTH_BUBBLE;
    return shade(BROTH, 0.94 + 0.12 * smooth(x, y + frame, 4));
  });
  texture(blockOutput, "bioreactor_glass", (x, y) => (Math.abs(x - y - 3) < 1 ? [0xff, 0xff, 0xff, 0x50] : [0xe4, 0xf2, 0xf6, 0x30]), 0);
  const MEAT = [0xc8, 0x3c, 0x50];
  const FAT = [0xf6, 0xe2, 0xd8];
  texture(blockOutput, "bioreactor_meat", (x, y) => {
    const marbling = smooth(x * 1.6, y * 0.7, 3);
    if (marbling > 0.78) return FAT;
    if (marbling > 0.7) return mix(MEAT, FAT, 0.5);
    return shade(MEAT, x % 2 === 0 ? 0.92 : 1.04);
  }, 3);
  const PANEL = [0x1c, 0x1f, 0x23];
  const BAR_LIT = [0x5c, 0xf0, 0x8a];
  const BAR_OFF = [0x22, 0x44, 0x30];
  for (let growth = 0; growth <= 3; growth++) {
    texture(blockOutput, `bioreactor_panel_${growth}`, (x, y) => {
      if (y < 1 || y > 2 || x < 1 || x > 10) return PANEL;
      if (x >= 9) return [0xff, 0x7a, 0xa8];
      const bar = Math.floor((x - 1) / 3);
      return (x - 1) % 3 === 2 ? PANEL : bar < growth ? BAR_LIT : BAR_OFF;
    }, 0);
  }

  const VIOLET = [0xc8, 0x5a, 0xff];
  texture(blockOutput, "hydroponic_light", (x, y) => (y % 4 === 1 ? [0xf2, 0xd0, 0xff] : VIOLET), 0);
  const LEAF = [0x7c, 0xc4, 0x4c];
  texture(blockOutput, "hydroponic_lettuce", (x, y) => {
    const frill = (y + Math.round(1.5 * Math.sin(x * 1.3))) % 4;
    if (frill === 0) return [0x4c, 0x8c, 0x30];
    if (x % 3 === 1) return [0xb8, 0xe8, 0x8c];
    return shade(LEAF, 0.94 + 0.1 * smooth(x, y, 3));
  }, 3);
  // A plant seen from the side: a stem up its middle, pairs of leaves along it, and for the strawberries white flowers
  // and red berries with yellow seeds hanging among them.
  const plant = (berries) => (x, y) => {
    const stem = Math.abs(x + 0.5 - 8) < 0.8 && y >= 8;
    const leaves = [[4, 6, 3], [11, 6, 3], [7.5, 4, 3.4], [5, 9.5, 2.6], [10, 9.5, 2.6], [7.5, 8, 2.8]].some(([lx, ly, r]) => (x + 0.5 - lx) ** 2 + ((y + 0.5 - ly) * 1.3) ** 2 <= r * r);
    const berry = [[3.5, 12], [12, 12.5], [9, 13.5]].some(([bx, by]) => (x + 0.5 - bx) ** 2 + (y + 0.5 - by) ** 2 <= 3.2);
    if (berries && berry) return (x + y) % 3 === 0 ? [0xf8, 0xe0, 0x50] : [0xe0, 0x2c, 0x38];
    if (berries && [[4, 4], [12, 3]].some(([fx, fy]) => (x + 0.5 - fx) ** 2 + (y + 0.5 - fy) ** 2 <= 1.6)) return [0xfa, 0xfa, 0xf2];
    if (leaves) return smooth(x * 2, y * 2, 3) > 0.62 ? [0x6c, 0xc8, 0x5c] : [0x3c, 0x9a, 0x3c];
    return stem ? [0x4a, 0x84, 0x2c] : CLEAR;
  };
  texture(blockOutput, "hydroponic_herbs", plant(false), 2);
  texture(blockOutput, "hydroponic_berries", plant(true), 2);

  const ALGAE = [0x58, 0xdc, 0x4c, 0xc8];
  const algaeBubbles = bubbleColumns([[5, 1], [7, 6], [9, 3], [11, 0]]);
  animated("algae_tube", FRAMES, 2, (x, y, frame) => {
    if (x === 4) return [0xd8, 0xff, 0xc8, 0xd8];
    if (bubbleAt(algaeBubbles, x, y, frame)) return [0xec, 0xff, 0xe0, 0xe8];
    return shade(ALGAE, 0.85 + 0.25 * smooth(x + 3, y + frame, 5));
  });

  // The centrifuge's lid: white round a window over the rotor, its six tubes standing round its hub, or spinning.
  const LID = [0xec, 0xef, 0xf2];
  const TUBES = [[0x4f, 0xd6, 0xe0], [0xf0, 0xa8, 0x30], [0xd0, 0x4a, 0xb8], [0x8a, 0xdb, 0x4a], [0xe8, 0xc9, 0xa8], [0x60, 0x80, 0xf0]];
  const lid = (window) => (x, y) => {
    const [dx, dy] = [x + 0.5 - 8, y + 0.5 - 8];
    const out = Math.hypot(dx, dy);
    if (x <= 2 || y <= 2 || x >= 13 || y >= 13) return [0xc8, 0xce, 0xd4];
    if (out > 4.6) return LID;
    if (out > 4) return [0x8a, 0x92, 0x9a];
    return window(dx, dy, out);
  };
  texture(blockOutput, "centrifuge_lid", lid((dx, dy, out) => {
    if (out < 1.2) return [0xd3, 0xd8, 0xdd];
    const tube = Math.round(((Math.atan2(dy, dx) / (2 * Math.PI)) * 6 + 6)) % 6;
    return out > 2 && out < 3.6 ? TUBES[tube] : [0x22, 0x28, 0x30];
  }), 0);
  animated("centrifuge_lid_spinning", 8, 1, (x, y, frame) => lid((dx, dy, out) => {
    const turn = (Math.atan2(dy, dx) / (2 * Math.PI) + 1 + frame / 8) % 1;
    if (out < 1.2) return [0xd3, 0xd8, 0xdd];
    if (out > 2 && out < 3.6) return turn < 0.12 ? [0xf4, 0xf0, 0xff] : [0xa8, 0x96, 0xb8];
    return [0x22, 0x28, 0x30];
  })(x, y));
  const centrifugePanel = (lamp) => (x, y) => {
    if (y < 11 || x < 4 || x > 11) return [0x2a, 0x2e, 0x33];
    if ((x - 6) ** 2 + (y - 12.5) ** 2 <= 1.6) return x === 6 && y === 12 ? [0x30, 0x34, 0x3a] : [0xc9, 0xd0, 0xd7];
    if (x >= 9 && x <= 10 && y >= 12 && y <= 13) return lamp;
    return [0x2a, 0x2e, 0x33];
  };
  texture(blockOutput, "centrifuge_panel", centrifugePanel([0x46, 0xe0, 0x70]), 0);
  texture(blockOutput, "centrifuge_panel_spinning", centrifugePanel([0xf2, 0xa8, 0x30]), 0);

  const PARTITION_GLASS = [0xd8, 0xee, 0xf4, 0x38];
  const streak = (x, y) => Math.abs(x - y - 5) < 1.2 || Math.abs(x - y + 6) < 0.6;
  texture(blockOutput, "lab_partition_glass", (x, y) => (streak(x, y) ? [0xff, 0xff, 0xff, 0x58] : PARTITION_GLASS), 0);
  const SPROUT = ["..#...#..", ".###.###.", "..##.##..", "....#....", "....#....", "...###..."];
  texture(blockOutput, "lab_partition_frosted", (x, y) => {
    if (y < 4 || y > 11) return streak(x, y) ? [0xff, 0xff, 0xff, 0x58] : PARTITION_GLASS;
    if (SPROUT[y - 5]?.[x - 4] === "#") return [0x3c, 0xb0, 0x5a, 0xf0];
    return [0xee, 0xf4, 0xf8, 0xb8];
  }, 0);
  texture(blockOutput, "lab_pipe", (x, y) => shade([0xb4, 0xbc, 0xc4], 0.9 + 0.12 * Math.abs(Math.sin(y * 0.9))), 2);
  texture(blockOutput, "lab_pipe_joint", (x, y) => (y >= 6 && y <= 9 ? [0x3c, 0xb0, 0x5a] : [0x7c, 0x84, 0x8c]), 2);

  texture(blockOutput, "lab_cork", (x, y) => shade([0xb0, 0x84, 0x58], hash(x + 7, y + 3) < 0.2 ? 0.8 : 1), 4);
  const liquid = (colour) => (x, y) => (x === 1 || Math.abs(x - y) < 0.8 ? [0xff, 0xff, 0xff, 0x90] : colour);
  texture(blockOutput, "lab_jar_yellow", liquid([0xf0, 0xd8, 0x70, 0x90]), 0);
  texture(blockOutput, "lab_jar_green", liquid([0x9c, 0xe8, 0xa0, 0x88]), 0);
  texture(blockOutput, "lab_jar_blue", liquid([0xa8, 0xd8, 0xf0, 0x78]), 0);
  texture(blockOutput, "lab_tentacle", (x, y) => ((x + 2 * y) % 5 === 0 ? [0xf0, 0xc0, 0xe0] : [0xb8, 0x60, 0xa8]), 3);
  texture(blockOutput, "lab_eye", (x, y) => {
    const out = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
    if (out < 2.5) return [0x10, 0x10, 0x14];
    if (out < 5.5) return out < 4 ? [0x3a, 0x8c, 0xd8] : [0x2a, 0x60, 0xa8];
    return (x * 7 + y * 3) % 11 === 0 ? [0xd8, 0x50, 0x50] : [0xf4, 0xf0, 0xe8];
  }, 0);
  texture(blockOutput, "lab_potato", (x, y) => shade([0xc8, 0x9c, 0x5c], hash(x + 1, y + 9) < 0.15 ? 0.75 : 1), 4);
  texture(blockOutput, "lab_sprout", () => [0x7c, 0xd0, 0x50], 2);

  // The microscope's eyepiece: open in its round middle, but for a fine crosshair with ticks and a scale bar, darkening
  // softly toward its edge, and black all round it.
  const EYEPIECE = 256;
  texture(guiOutput, "eyepiece", (x, y) => {
    const [dx, dy] = [x + 0.5 - EYEPIECE / 2, y + 0.5 - EYEPIECE / 2];
    const out = Math.hypot(dx, dy) / (EYEPIECE / 2);
    if (out > 0.96) return [0, 0, 0, 255];
    const vignette = Math.max(0, Math.min(1, (out - 0.78) / 0.18));
    const onCross = (Math.abs(dx) < 0.5 || Math.abs(dy) < 0.5) && out > 0.06 && out < 0.82;
    const onTick = Math.abs(dy) < 2.5 && Math.abs(dx) % 16 < 0.5 && out < 0.7;
    const onBar = y >= 196 && y <= 197 && x >= 150 && x <= 190;
    const barEnd = (x === 150 || x === 190) && y >= 192 && y <= 201;
    if (onBar || barEnd) return [0xf4, 0xf4, 0xf0, 0xd0];
    if (onCross || onTick) return [0x10, 0x14, 0x18, Math.round(0x70 + 0x8f * vignette)];
    return [0, 0, 0, Math.round(255 * vignette * vignette)];
  }, 0, EYEPIECE, EYEPIECE);

  const PICTURE = 128;
  const slide = (name, paint) => texture(guiOutput, name, (x, y) => paint(x, y % PICTURE, Math.floor(y / PICTURE)), 2, PICTURE, PICTURE * 2);
  // A bright field, a little brighter in its middle, specks of debris drifting in it.
  const brightField = (base, x, y) => {
    if (hash(x + 311, y + 17) < 0.004) return shade(base, 0.7);
    return shade(base, 1.02 - 0.08 * Math.hypot(x - 64, y - 64) / 64);
  };
  const inEllipse = (x, y, cx, cy, rx, ry) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2;

  // The water bear seen from above, its head to the right: four segments, a stubby leg out to either side of each,
  // paddling (each pair's leg forward or back, frame by frame), with pale claws; moss green in its gut, pale storage
  // cells, the bulb of its throat and its two eye spots.
  const SEGMENTS = [[34, 15, 18], [52, 17, 21], [70, 17, 21], [87, 15, 18]];
  const BEAR = [0xe8, 0xc0, 0x90];
  const BEAR_EDGE = [0x9c, 0x70, 0x48];
  const storageCells = Array.from({ length: 22 }, () => [30 + next() * 66, 50 + next() * 28]);
  const bear = (x, y, frame) => {
    let body = Math.min(...SEGMENTS.map(([cx, rx, ry]) => inEllipse(x, y, cx, 64, rx, ry)), inEllipse(x, y, 101, 64, 12, 13));
    let leg = Infinity;
    SEGMENTS.forEach(([cx], pair) => {
      const swing = ((pair + frame) % 2 === 0 ? 3 : -3);
      for (const side of [-1, 1]) leg = Math.min(leg, inEllipse(x, y, cx + swing, 64 + side * 23, 6, 7));
    });
    const claw = SEGMENTS.some(([cx], pair) => [-1, 1].some((side) => {
      const tip = 64 + side * 29;
      const swing = ((pair + frame) % 2 === 0 ? 3 : -3);
      return Math.abs(y - tip) < 2 && [-3, 0, 3].some((dx) => Math.abs(x - (cx + swing + dx)) < 0.8);
    }));
    if (claw && body > 1) return [0xf4, 0xec, 0xdc];
    body = Math.min(body, leg);
    if (body > 1) return null;
    if (body > 0.8) return BEAR_EDGE;
    if (inEllipse(x, y, 101, 57, 1.8, 1.8) <= 1 || inEllipse(x, y, 101, 71, 1.8, 1.8) <= 1) return [0x30, 0x20, 0x18];
    if (Math.abs(inEllipse(x, y, 111, 64, 3, 3) - 1) < 0.5) return [0x6a, 0x48, 0x30];
    if (inEllipse(x, y, 97, 64, 5, 4) <= 1) return [0xc0, 0x8c, 0x5c];
    if (inEllipse(x, y, 62, 64, 24, 7) <= 1) return hash(x, y) < 0.25 ? [0x4a, 0x68, 0x2c] : [0x7a, 0x98, 0x48];
    if (storageCells.some(([cx, cy]) => inEllipse(x, y, cx, cy, 2.2, 2.2) <= 1)) return [0xf6, 0xe0, 0xb8];
    return shade(BEAR, 1.06 - 0.12 * body);
  };
  slide("microscope_tardigrade", (x, y, frame) => bear(x, y, frame) ?? brightField([0xe6, 0xec, 0xe4], x, y));

  // Algae cells, round green beads jostling a pixel between frames, each with its membrane, its cup of chloroplast and
  // a pale pyrenoid.
  const cells = [];
  for (let tries = 0; tries < 400 && cells.length < 30; tries++) {
    const cell = { x: 8 + next() * 112, y: 8 + next() * 112, r: 6 + next() * 7, turn: next() * Math.PI * 2, jiggle: [next() < 0.5 ? -1 : 1, next() < 0.5 ? -1 : 1] };
    if (cells.every((other) => Math.hypot(other.x - cell.x, other.y - cell.y) > other.r + cell.r - 1)) cells.push(cell);
  }
  const alga = (x, y, frame) => {
    for (const cell of cells) {
      const [cx, cy] = [cell.x + frame * cell.jiggle[0], cell.y + frame * cell.jiggle[1]];
      const out = Math.hypot(x - cx, y - cy);
      if (out > cell.r) continue;
      if (out > cell.r - 1.5) return [0x2e, 0x7a, 0x2e];
      const [ox, oy] = [cx + Math.cos(cell.turn) * cell.r * 0.35, cy + Math.sin(cell.turn) * cell.r * 0.35];
      if (Math.hypot(x - ox, y - oy) < cell.r * 0.25) return [0xd8, 0xf0, 0xb8];
      if (Math.hypot(x - ox, y - oy) > cell.r * 0.55) return [0x3c, 0x9c, 0x3a];
      return [0x7c, 0xc8, 0x5c];
    }
    return null;
  };
  slide("microscope_algae", (x, y, frame) => alga(x, y, frame) ?? brightField([0xe8, 0xf0, 0xe0], x, y));

  // Muscle from the bioreactor, stained: fibres running at a slant, banded across, dark and light, a pixel further
  // contracted in the second frame, purple nuclei along their edges, and a few round droplets of fat.
  const FIBRE = 15;
  const nuclei = Array.from({ length: 14 }, () => [next() * 128, next() * 128]);
  const fats = Array.from({ length: 4 }, () => [96 + next() * 28, 4 + next() * 40, 6 + next() * 5]);
  const muscle = (x, y, frame) => {
    if (fats.some(([cx, cy, r]) => Math.hypot(x - cx, y - cy) < r)) return fats.some(([cx, cy, r]) => Math.abs(Math.hypot(x - cx, y - cy) - r) < 1) ? [0xc8, 0xa0, 0xb4] : [0xfc, 0xf4, 0xf6];
    const along = x * 0.94 + y * 0.34;
    const across = -x * 0.34 + y * 0.94 + 128 + 2 * Math.sin(along / 18);
    const inFibre = across % FIBRE;
    if (inFibre < 1.2) return [0xf4, 0xe4, 0xec];
    if (nuclei.some(([nx, ny]) => inEllipse(x, y, nx, ny, 4, 1.6) <= 1)) return [0x50, 0x30, 0x80];
    const band = (along + frame) % 5;
    return band < 2.5 ? [0xc0, 0x40, 0x70] : [0xe8, 0x90, 0xb0];
  };
  slide("microscope_muscle", muscle);

  // Diatoms on a dark field, their glass shells lit: a wheel with its ribs, a boat with its midline and cross ribs, a
  // rounded triangle dotted with pores, and two small boats, their colours shimmering between the frames.
  const glow = (colour, frame) => shade(colour, frame === 0 ? 1 : 0.86);
  const diatom = (x, y, frame) => {
    const wheel = Math.hypot(x - 40, y - 46);
    const spoke = Math.abs(((Math.atan2(y - 46, x - 40) * 180) / Math.PI + 360) % 15 - 7.5) > 6.6;
    if (Math.abs(wheel - 18) < 1.2 || Math.abs(wheel - 10) < 0.8 || wheel < 2 || (wheel < 18 && spoke)) return glow([0x80, 0xf0, 0xff], frame);
    const [bx, by] = [(x - 84) * 0.87 + (y - 82) * 0.5, -(x - 84) * 0.5 + (y - 82) * 0.87];
    const boat = inEllipse(bx, by, 0, 0, 34, 9);
    if (Math.abs(boat - 1) < 0.12 || (boat < 1 && (Math.abs(by) < 0.7 || Math.abs(bx % 4) < 0.6))) return glow([0xff, 0xd8, 0x70], 1 - frame);
    const [tx, ty] = [x - 88, y - 30];
    const triangle = Math.max(-ty - 14, ty * 0.5 + tx * 0.866 - 7, ty * 0.5 - tx * 0.866 - 7);
    if (Math.abs(triangle) < 1 || (triangle < 0 && tx % 4 === 0 && ty % 4 === 0)) return glow([0xff, 0x90, 0xd0], frame);
    for (const [cx, cy, turn] of [[30, 100, 0.4], [104, 112, -0.6]]) {
      const [sx, sy] = [(x - cx) * Math.cos(turn) + (y - cy) * Math.sin(turn), -(x - cx) * Math.sin(turn) + (y - cy) * Math.cos(turn)];
      if (Math.abs(inEllipse(sx, sy, 0, 0, 10, 3) - 1) < 0.25) return glow([0xc0, 0xff, 0xb0], frame);
    }
    return hash(x + 5, y + 71) < 0.006 ? [0x40, 0x48, 0x60] : [0x08, 0x0c, 0x18];
  };
  slide("microscope_diatoms", diatom);

  // The steak in its petri dish: the dish's glass rim round a pale floor, the steak pink and marbled, a rind of fat
  // along one side, edged dark, and a little blue label on the rim.
  const steak = (x, y) => inEllipse(x + 0.5, y + 0.5, 7.6, 8.4, 5.2, 3.9) <= 1 && inEllipse(x + 0.5, y + 0.5, 12.4, 5.6, 2, 1.8) > 1;
  texture(itemOutput, "lab_grown_steak", (x, y) => {
    const out = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
    if (x >= 2 && x <= 4 && y >= 12 && y <= 13) return y === 12 ? [0xf2, 0xf6, 0xfb] : [0x30, 0x7a, 0xd8];
    if (steak(x, y)) {
      if (!steak(x, y - 1) || !steak(x - 1, y)) return [0xf4, 0xe4, 0xcc];
      if (Math.abs(((x + 2 * y) % 5) - 2) < 0.5 && hash(x, y) < 0.6) return [0xf6, 0xd0, 0xc8];
      return y > 9 ? [0xb8, 0x38, 0x48] : [0xd8, 0x4c, 0x5c];
    }
    if ([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => steak(x + dx, y + dy))) return [0x5a, 0x18, 0x20];
    if (out > 7.5) return CLEAR;
    if (out > 6.4) return x + y < 12 ? [0xec, 0xf6, 0xfa] : [0xa8, 0xc4, 0xd2];
    return [0xdc, 0xec, 0xf2];
  }, 0);
}

// The rocket's snack machine (see SnackMachineBlock), its parts as its models show them, a block's front seen from
// outside with its left edge at the texture's left. Its cabinet: midnight-blue gloss with a faint sheen across it, its
// top a vent grille, a chrome kick plate along its foot; the front of its upper block crowned with an orange band
// carrying its badge, a little white ringed planet; the front of its lower block with a dark recess round the flap.
// Its showcase, lit: dark glass over rows of bright packets and bars standing in their chrome spirals, a glint across
// it, the upper block's two rows and the lower block's one their own, under that the dark of the drop. The flap: smoked
// under a chrome pull, or lit green with the dropped packet lying in it. Its lamp: cyan while it is ready, blinking
// amber while the spiral turns, green with a snack in the flap. Its keypad: buttons in the packets' colours on a dark
// panel; its card reader: a small cyan screen with a tap's waves on it.
{
  const NAVY = [0x1a, 0x26, 0x52];
  const NAVY_SHINE = [0x24, 0x34, 0x6a];
  const NAVY_DARK = [0x0e, 0x14, 0x2c];
  const ORANGE = [0xf2, 0x7a, 0x22];
  const BADGE = [0xf6, 0xf4, 0xee];
  const BADGE_RING = [0xff, 0xd8, 0x5a];
  const GLASS_DARK = [0x0a, 0x10, 0x1c];
  const GLASS_GLINT = [0x2c, 0x3c, 0x58];
  const SPIRAL = [0xc8, 0xd0, 0xd8];
  const SPIRAL_DARK = [0x6c, 0x74, 0x7c];
  const GREEN = [0x46, 0xe0, 0x70];
  const AMBER = [0xf2, 0xb8, 0x30];
  const AMBER_DIM = [0x6a, 0x48, 0x14];
  const CYAN = [0x46, 0xc8, 0xf0];
  // The packets' wrappers, bright as a vending machine's are: crisps red, a chocolate bar purple, sweets yellow, a
  // cereal bar green, biscuits orange, mints sky blue.
  const WRAPPERS = [[0xe0, 0x30, 0x3a], [0x8a, 0x3c, 0xc8], [0xf4, 0xd0, 0x2a], [0x3c, 0xb8, 0x4a], [0xf0, 0x86, 0x2a], [0x50, 0xb4, 0xf0]];
  const lighter = (colour) => colour.map((c) => Math.min(255, c + 0x40));
  const gloss = (x, y) => ((x + y) % 13 === 0 || (x + y) % 13 === 1 ? NAVY_SHINE : NAVY);
  texture(blockOutput, "snack_machine_side", gloss, 2);
  texture(blockOutput, "snack_machine_top", (x, y) => (x >= 3 && x <= 12 && y >= 3 && y <= 12 && y % 2 === 1 ? NAVY_DARK : NAVY), 2);
  texture(blockOutput, "snack_machine_trim", (x, y) => ((x + y) % 5 === 0 ? [0xf0, 0xf4, 0xf8] : [0xc4, 0xca, 0xd0]), 2);
  // The badge in the band along the top of the upper block's front, rows of seven over its middle: a white planet,
  // its yellow ring across it ("r").
  const PLANET = ["..###..", "rr###rr", "..###.."];
  texture(blockOutput, "snack_machine_front_upper", (x, y) => {
    if (y <= 3) {
      const mark = PLANET[y]?.[x - 5];
      if (mark === "#") return BADGE;
      if (mark === "r") return BADGE_RING;
      return y === 3 ? [0xc8, 0x5c, 0x14] : ORANGE;
    }
    return gloss(x, y);
  }, 2);
  texture(blockOutput, "snack_machine_front_lower", (x, y) => (x >= 1 && x <= 11 && y >= 10 && y <= 15 ? NAVY_DARK : gloss(x, y)), 2);
  // A row of packets two pixels wide standing in their spirals, from columns 2, 5 and 8, the row's tallest reaching up
  // to a row, every other one a bar a pixel lower; their spirals' coil along the row under them.
  const packets = (x, y, top, row) => {
    if (y === top + 4) return (x + y) % 2 === 0 ? SPIRAL : SPIRAL_DARK;
    const packet = Math.floor((x - 2) / 3);
    const height = (packet + row) % 2 === 0 ? 4 : 3;
    if (x < 2 || y < top + 4 - height || y > top + 3 || (x - 2) % 3 === 2) return null;
    const wrapper = WRAPPERS[(packet * 5 + row * 2) % WRAPPERS.length];
    return y === top + 4 - height ? lighter(wrapper) : wrapper;
  };
  const glass = (x, y) => ((x - y + 32) % 11 === 0 ? GLASS_GLINT : GLASS_DARK);
  // The upper block shows columns 2 to 9 and rows 4 to 15 of its showcase; the lower block columns 2 to 9, rows 0 to 8.
  texture(blockOutput, "snack_machine_showcase_upper", (x, y) => packets(x, y, 5, 0) ?? packets(x, y, 11, 1) ?? glass(x, y), 0);
  texture(blockOutput, "snack_machine_showcase_lower", (x, y) => packets(x, y, 1, 2) ?? (y >= 6 ? NAVY_DARK : glass(x, y)), 0);
  // The flap: columns 2 to 9, rows 11 to 13.
  texture(blockOutput, "snack_machine_flap", (x, y) => (y === 11 ? SPIRAL : [0x14, 0x18, 0x20]), 2);
  texture(blockOutput, "snack_machine_flap_lit", (x, y) => {
    if (y === 11) return SPIRAL;
    if (y >= 12 && x >= 4 && x <= 7) return y === 12 ? lighter(WRAPPERS[4]) : WRAPPERS[4];
    return GREEN;
  }, 0);
  // The lamp: columns 11 to 13, rows 5 and 6, a highlight in its corner. Turning, it blinks: two frames, lit and dim.
  const lamp = (colour) => (x, y) => (x === 11 && y === 5 ? lighter(colour) : colour);
  texture(blockOutput, "snack_machine_lamp_ready", lamp(CYAN), 0);
  texture(blockOutput, "snack_machine_lamp_dropped", lamp(GREEN), 0);
  texture(blockOutput, "snack_machine_lamp_turning", (x, y) => lamp(y < SIZE ? AMBER : AMBER_DIM)(x, y % SIZE), 0, SIZE, SIZE * 2);
  writeFileSync(resolve(blockOutput, "snack_machine_lamp_turning.png.mcmeta"), JSON.stringify({ animation: { frametime: 4 } }, null, 2) + "\n");
  // The keypad: columns 11 to 13, rows 8 to 13, a button in every other pixel.
  texture(blockOutput, "snack_machine_keypad", (x, y) => ((x + y) % 2 === 1 ? WRAPPERS[(x + y * 2) % WRAPPERS.length] : [0x20, 0x24, 0x2c]), 0);
  // The card reader: columns 11 to 13, rows 3 to 5, a tap's waves in white on cyan.
  texture(blockOutput, "snack_machine_reader", (x, y) => (x === 12 || (x === 13 && y === 4) ? [0xf0, 0xfa, 0xff] : CYAN), 0);
}

// The greenhouse's grow light's LEDs as seen from under it (see GrowLightBlock): red ones most, a blue and a white among
// them, in staggered rows on a magenta glow, as horticultural lights look lit.
{
  const GLOW = [0x86, 0x22, 0x80];
  const LEDS = [[0xff, 0x40, 0x62], [0xff, 0x40, 0x62], [0x62, 0x72, 0xff], [0xff, 0x40, 0x62], [0xff, 0xf0, 0xff]];
  texture(blockOutput, "grow_light", (x, y) => {
    const row = Math.floor(y / 2);
    const led = y % 2 === 1 && (x + row) % 3 === 1;
    return led ? LEDS[(Math.floor(x / 3) + row) % LEDS.length] : GLOW;
  }, 3);
}
// The rocket's rooms' furniture (see rocketRoomFurniture in generate_resources.mjs, which says which patch of each
// texture each part shows). The shelf unit's birch, its steel plinth, and what each room keeps on it, each texture a
// sheet of patches; the pallet rack's blue uprights punched for their beams, its orange beams, its pine pallet and its
// loads.
function rocketRoomTextures() {
  const scaled = ([r, g, b], by) => [r * by, g * by, b * by].map((c) => Math.round(Math.max(0, Math.min(255, c))));
  const BIRCH = [0xe2, 0xd2, 0xaa];
  texture(blockOutput, "shelf_wood", (x, y) => (y === 0 || y === 15 ? scaled(BIRCH, 0.86) : scaled(BIRCH, (x * 3 + (y >> 2)) % 3 === 0 ? 0.95 : 1)), 3);
  texture(blockOutput, "shelf_steel", (x, y) => (y === 0 ? [0x5a, 0x60, 0x68] : [0x3a, 0x3f, 0x46]), 2);

  // Boots: a charcoal pair banded orange and a white pair banded blue, their shafts over their feet, a grey toe cap and
  // a black sole; a helmet's front, its gold visor; and its plain white side.
  const WHITE = [0xee, 0xf1, 0xf4];
  texture(blockOutput, "shelf_boots", (x, y) => {
    const white = x >= 3 && x < 6;
    if (x < 6 && y >= 9 && y < 13) return y === 10 ? (white ? [0x2a, 0x6c, 0xd0] : [0xf0, 0x7a, 0x1e]) : white ? [0xdc, 0xe0, 0xe4] : [0x34, 0x38, 0x3e];
    if (x < 6 && y === 13) return [0x9a, 0xa0, 0xa8];
    if (x < 6 && y === 14) return [0x16, 0x17, 0x19];
    if (x >= 9 && x <= 12 && y >= 1 && y <= 3) return y === 1 && x === 10 ? [0xff, 0xf2, 0xc0] : [0xe0, 0xa8, 0x2c];
    if (x >= 8 && y < 5) return x === 8 || y === 4 ? scaled(WHITE, 0.88) : WHITE;
    return [0x30, 0x33, 0x38];
  }, 2);

  // Book spines, each column its own cloth with gold bands across it, a terracotta pot and the fronds of a fern.
  const CLOTH = [[0x7a, 0x1c, 0x22], [0x7a, 0x1c, 0x22], [0x1e, 0x2e, 0x5c], [0x1e, 0x2e, 0x5c], [0x24, 0x5a, 0x34], [0xc8, 0x9a, 0x2a],
    [0xc8, 0x9a, 0x2a], [0x5c, 0x2a, 0x5e], [0x1c, 0x6e, 0x70], [0x5c, 0x2a, 0x5e], [0x5c, 0x2a, 0x5e], [0xa8, 0x4a, 0x1e], [0xa8, 0x4a, 0x1e],
    [0x2e, 0x46, 0x5a], [0x4a, 0x50, 0x58], [0x4a, 0x50, 0x58]];
  texture(blockOutput, "shelf_books", (x, y) => {
    if (y >= 14 && x < 3) return y === 14 ? [0xb0, 0x58, 0x30] : [0x94, 0x48, 0x26];
    if (y >= 12 && x >= 4 && x < 6) return (x + y) % 2 === 0 ? [0x3e, 0x8a, 0x3a] : [0x2c, 0x6a, 0x2c];
    const cloth = CLOTH[x];
    if (y % 8 === 1 || y % 8 === 4) return [0xd8, 0xb0, 0x48];
    return y % 8 === 0 ? scaled(cloth, 0.75) : cloth;
  }, 3);

  // Four jars, four pixels across each: a lid over its glass, tomatoes, honey, pickles and plum jam, each with a white
  // label; and a tin, silver with a red label.
  const PRESERVES = [[0xc8, 0x2c, 0x1e], [0xe8, 0xa8, 0x24], [0x6a, 0x8e, 0x2a], [0x6c, 0x1e, 0x4c]];
  const LIDS = [[0xc8, 0x30, 0x30], [0xd8, 0xdc, 0xe0], [0x2e, 0x6a, 0x2e], [0xd8, 0xb0, 0x48]];
  texture(blockOutput, "shelf_jars", (x, y) => {
    if (y >= 8 && y < 11 && x < 2) return y === 9 ? [0xc0, 0x24, 0x24] : [0xc8, 0xcc, 0xd2];
    if (y >= 8) return [0x30, 0x33, 0x38];
    const jar = Math.floor(x / 4);
    if (y === 0) return LIDS[jar];
    if (y === 2 || y === 3) return x % 4 === 0 ? scaled(PRESERVES[jar], 1.2) : [0xf2, 0xee, 0xe2];
    return x % 4 === 0 ? scaled(PRESERVES[jar], 1.3) : PRESERVES[jar];
  }, 3);

  // Towels in three colours, five pixels across each: a fat roll's spiral end, round, its turns shading darker inward
  // to a dark hollow; a folded towel's folds; and a towel's terry side, a white stripe near one end.
  const TOWELS = [[0xf0, 0x7a, 0x66], [0x2c, 0xb0, 0xb8], [0xf2, 0xd2, 0x8a]];
  texture(blockOutput, "shelf_towels", (x, y) => {
    const towel = Math.floor(x / 5);
    if (towel > 2) return [0x30, 0x33, 0x38];
    const colour = TOWELS[towel];
    if (y < 5) {
      const round = Math.hypot(x % 5 - 2, y - 2);
      if (round > 2.4) return [0x30, 0x33, 0x38];
      if (round < 0.6) return scaled(colour, 0.45);
      return Math.round(round * 1.5) % 2 === 0 ? colour : scaled(colour, 0.72);
    }
    if (y >= 6 && y < 8) return y === 7 ? scaled(colour, 0.8) : colour;
    if (y >= 10 && y < 15) return y === 12 ? [0xf8, 0xf6, 0xf0] : (x + y) % 3 === 0 ? scaled(colour, 0.9) : colour;
    return [0x30, 0x33, 0x38];
  }, 2);

  // A red toolbox, its black latch and handle; a yellow case, its latches; a coil of orange cable and its top; and
  // three spray cans, blue, orange and green, their caps white.
  texture(blockOutput, "shelf_tools", (x, y) => {
    if (y === 0 && x < 3) return [0x1a, 0x1b, 0x1e];
    if (x < 7 && y >= 2 && y < 6) return y === 3 && (x === 1 || x === 5) ? [0x1a, 0x1b, 0x1e] : y === 2 ? [0xe0, 0x3a, 0x30] : [0xc0, 0x26, 0x22];
    if (x >= 8 && x < 14 && y >= 1 && y < 6) return y === 2 && (x === 9 || x === 12) ? [0x1a, 0x1b, 0x1e] : [0xf0, 0xc0, 0x24];
    if (x < 5 && y >= 8 && y < 13) return (x + y) % 2 === 0 ? [0xf0, 0x80, 0x24] : [0xb8, 0x58, 0x18];
    const can = [[8, 9, [0x2a, 0x5c, 0xc8]], [10, 11, [0xf0, 0x80, 0x24]], [12, 13, [0x3a, 0x9a, 0x3e]]].find(([from, to]) => x >= from && x <= to);
    if (can && y >= 8 && y < 13) return y === 8 ? [0xe8, 0xec, 0xf0] : can[2];
    return [0x30, 0x33, 0x38];
  }, 2);

  // The pallet rack: uprights punched with a slot every few pixels, beams with a darker lip, a pallet of pine boards,
  // cardboard taped across its middle, ribbed drums and a crate of boards and battens.
  texture(blockOutput, "rack_upright", (x, y) => (x >= 6 && x <= 9 && y % 4 === 1 ? [0x16, 0x2a, 0x50] : [0x2c, 0x5f, 0xb8]), 3);
  texture(blockOutput, "rack_beam", (x, y) => (y >= 13 ? [0xb8, 0x58, 0x14] : [0xee, 0x7c, 0x1e]), 3);
  texture(blockOutput, "rack_pallet", (x, y) => (y % 4 === 3 ? [0x7a, 0x60, 0x3c] : [0xd2, 0xb0, 0x78]), 5);
  texture(blockOutput, "rack_cardboard", (x, y) => {
    if (x >= 6 && x <= 9) return [0xe2, 0xc8, 0x92];
    return x === 0 || y === 0 ? [0xa8, 0x80, 0x4c] : [0xc8, 0x9c, 0x60];
  }, 4);
  const drum = (colour) => (x, y) => (y === 0 || y === 15 || y === 4 || y === 11 ? scaled(colour, 1.25) : x % 8 === 0 ? scaled(colour, 0.85) : colour);
  texture(blockOutput, "rack_drum_blue", drum([0x24, 0x58, 0xa8]), 3);
  texture(blockOutput, "rack_drum_yellow", drum([0xe0, 0xae, 0x20]), 3);
  texture(blockOutput, "rack_crate", (x, y) => {
    if (x <= 1 || x >= 14 || y <= 1 || y >= 14) return [0x7a, 0x5a, 0x32];
    return y % 4 === 0 ? [0x96, 0x72, 0x42] : [0xb8, 0x90, 0x58];
  }, 5);
}
rocketRoomTextures();
// The rocket's girder decorations (see girderDecor in generate_resources.mjs, which says which patch of each texture
// each part shows). The entry's two mission patches, each a round embroidered patch on a navy banner trimmed in gold,
// its foot cut in a swallowtail: a white rocket climbing on its flame past the moon and the stars inside a red ring,
// and a ringed planet in deep space inside a gold one. The chain hoist's red casing ribbed across, its trolley's steel
// wheels and its yellow hook. The pot rack's polished copper, a highlight down it and its rim along its foot, and a
// pan's round face turned in rings to its darker rim. The fairy lights' dark wire and their bulbs, warm white and
// amber. The hanging basket's wicker, woven over and under, its tuft of leaves and the stems trailing over its sides.
// The bunting's twisted cord and its pennants, turquoise, white, navy and coral, folded over the cord along their tops.
// The spotlights' black cans, finned, their lenses glowing pink or cyan, hottest in the middle, and their beams,
// widening and fading as they fall. The cable tray's galvanised steel punched with holes, and its cables, two pixels
// across each: red, blue, yellow, green, black, orange, grey and the white of the ties. And the iron they hang by.
function girderDecorTextures() {
  const mix = (a, b, t) => a.map((c, i) => Math.round(c + (b[i] - c) * t));
  const scaled = ([r, g, b], by) => [r, g, b].map((c) => Math.round(Math.max(0, Math.min(255, c * by))));
  // A pixel's own shade of a texture, the same every time it is drawn.
  const speck = (x, y) => (((x * 73856093) ^ (y * 19349663)) >>> 0) % 7;

  // The banner: navy cloth, its sides and its swallowtail's edges trimmed in gold, the patch's round field inside its
  // ring, the cloth's fold over the rod along its top.
  const NAVY = [0x1a, 0x24, 0x4c];
  const TRIM = [0xd8, 0xb0, 0x48];
  const notched = (x, y) => x < 2 || x > 13 || y < 1 || y > 14 || Math.abs(x + 0.5 - 8) < (y - 11) * 1.6;
  const banner = (ring, field) => (x, y) => {
    if (notched(x, y)) return CLEAR;
    const nearNotch = notched(x - 1, y) || notched(x + 1, y) || notched(x, y + 1);
    if (y === 1) return scaled(NAVY, 0.7);
    if (nearNotch) return TRIM;
    const across = Math.hypot(x - 7.5, y - 6.5);
    if (across > 4.6) return (x + y) % 4 === 0 ? scaled(NAVY, 1.15) : NAVY;
    return across > 3.8 ? ring : field(x, y);
  };
  // A rocket on its flame past the moon, in the patch's field, the stars about it.
  const ROCKET = {
    "7,3": [0xd0, 0xd8, 0xe0], "8,3": [0xb8, 0xc0, 0xc8],
    "7,4": [0xf4, 0xf6, 0xf8], "8,4": [0xd8, 0xdc, 0xe2], "7,5": [0x3c, 0x9c, 0xe8], "8,5": [0xd8, 0xdc, 0xe2],
    "7,6": [0xf4, 0xf6, 0xf8], "8,6": [0xd8, 0xdc, 0xe2], "7,7": [0xf4, 0xf6, 0xf8], "8,7": [0xd8, 0xdc, 0xe2],
    "6,7": [0xd8, 0x30, 0x30], "9,7": [0xb0, 0x24, 0x24], "6,8": [0xd8, 0x30, 0x30], "9,8": [0xb0, 0x24, 0x24],
    "7,8": [0x8a, 0x90, 0x98], "8,8": [0x70, 0x76, 0x7e], "7,9": [0xff, 0xe0, 0x50], "8,9": [0xff, 0xd0, 0x40],
    "7,10": [0xff, 0x8a, 0x2c], "8,10": [0xf0, 0x6a, 0x20],
    "4,4": [0xd8, 0xdc, 0xe4], "5,4": [0xc0, 0xc4, 0xcc], "4,5": [0xc0, 0xc4, 0xcc], "5,5": [0x98, 0x9c, 0xa6],
    "10,4": [0xff, 0xff, 0xff], "11,7": [0xff, 0xf4, 0xc0], "4,8": [0xff, 0xff, 0xff], "10,10": [0xff, 0xf4, 0xc0],
  };
  texture(blockOutput, "girder_decor_patch_a", banner([0xc8, 0x2a, 0x2a], (x, y) => ROCKET[`${x},${y}`] ?? mix([0x24, 0x5c, 0xc0], [0x10, 0x2c, 0x78], (y - 2) / 9)), 0);
  // A ringed planet in deep space, banded and lit from the upper left, its tilted ring round it, behind it over its
  // middle and in front of it under, the stars about it.
  const STARS = new Set(["5,3", "10,3", "4,9", "11,9"]);
  texture(blockOutput, "girder_decor_patch_b", banner(TRIM, (x, y) => {
    const ring = Math.hypot((x - 7.5) / 3.5, (y - 6.5 - 0.35 * (x - 7.5)) / 1.2);
    const planet = Math.hypot(x - 7.5, y - 6.5) <= 2;
    const front = y - 6.5 - 0.35 * (x - 7.5) > 0;
    if (Math.abs(ring - 1) < 0.3 && (front || !planet)) return x < 8 ? [0xf4, 0xec, 0xd4] : [0xd8, 0xcc, 0xa8];
    if (planet) return Math.hypot(x - 6.5, y - 5.5) < 1 ? [0xf8, 0xc4, 0x78] : y === 7 ? [0xa8, 0x52, 0x1c] : [0xe0, 0x84, 0x34];
    return STARS.has(`${x},${y}`) ? [0xff, 0xff, 0xff] : [0x14, 0x10, 0x2c];
  }), 0);

  // The chain hoist's casing, its trolley's wheels and its hook; the iron everything hangs by.
  texture(blockOutput, "girder_decor_hoist", (x, y) => (y % 4 === 0 ? [0x8e, 0x2c, 0x12] : x === 0 || x === 15 ? [0xa8, 0x3a, 0x18] : [0xcc, 0x4c, 0x1e]), 3);
  texture(blockOutput, "girder_decor_steel", (x, y) => (Math.hypot(x - 7.5, y - 7.5) < 2.5 ? [0x50, 0x56, 0x5e] : [0x9a, 0xa0, 0xa8]), 3);
  texture(blockOutput, "girder_decor_yellow", (x, y) => (x === 0 || y === 15 ? [0xc8, 0x98, 0x1a] : [0xf6, 0xc6, 0x26]), 3);
  texture(blockOutput, "girder_decor_iron", (x, y) => scaled([0x30, 0x33, 0x38], 1 + 0.03 * speck(x, y)), 2);

  // Polished copper, a highlight down it and its rim's band along its foot; a pan's face, turned in rings.
  const COPPER = [0xc8, 0x6e, 0x3a];
  texture(blockOutput, "girder_decor_copper", (x, y) => {
    if (y >= 14) return y === 14 ? [0xe8, 0x96, 0x5a] : [0x96, 0x4c, 0x24];
    if (x === 4 || x === 5) return [0xf4, 0xb0, 0x7a];
    return x <= 1 || x >= 14 ? scaled(COPPER, 0.78) : COPPER;
  }, 3);
  texture(blockOutput, "girder_decor_pan", (x, y) => {
    const round = Math.hypot(x - 7.5, y - 7.5);
    if (round > 7.6) return CLEAR;
    if (round > 6.4) return [0x8a, 0x44, 0x20];
    if (Math.abs(x - y + 3) <= 1 && round < 5.5) return [0xf4, 0xb4, 0x80];
    return Math.round(round) % 3 === 0 ? scaled(COPPER, 0.88) : COPPER;
  }, 2);

  // The fairy lights' wire, and their bulbs, warm white on the left and amber on the right, brightest in the middle.
  texture(blockOutput, "girder_decor_wire", () => [0x1e, 0x22, 0x1e], 2);
  texture(blockOutput, "girder_decor_bulbs", (x, y) => {
    const core = Math.abs((x % 8) - 3.5) < 2 && y >= 4 && y < 12;
    if (x < 8) return core ? [0xff, 0xfa, 0xe8] : [0xff, 0xe6, 0xb8];
    return core ? [0xff, 0xd8, 0x90] : [0xff, 0xae, 0x48];
  }, 0);

  // The basket's wicker, woven over and under; its tuft of leaves; and its stems trailing down, each its own length,
  // leaves along them by turns.
  texture(blockOutput, "girder_decor_wicker", (x, y) => {
    const over = ((x >> 1) + (y >> 1)) % 2 === 0;
    if (over ? y % 2 === 1 : x % 2 === 1) return [0x7e, 0x58, 0x2c];
    return over ? [0xd2, 0xa4, 0x62] : [0xb8, 0x88, 0x4a];
  }, 3);
  const LEAVES = [[0x3c, 0x8c, 0x34], [0x2c, 0x6e, 0x28], [0x58, 0xa8, 0x3c], [0x24, 0x58, 0x22]];
  texture(blockOutput, "girder_decor_foliage", (x, y) => {
    const edge = Math.hypot((x - 7.5) / 8, (y - 9) / 9);
    if (edge > 1 || (edge > 0.7 && speck(x, y) < 3)) return CLEAR;
    return LEAVES[speck(x, y) % LEAVES.length];
  }, 2);
  // The stems hang from a fringe of leaves spilling over the rim, each stem in its column as long as it is given, a
  // leaf off it every third pixel down, to one side and then the other.
  const STEMS = new Map([[1, 12], [5, 16], [9, 10], [13, 14]]);
  const stemBeside = (x, y) => [x - 1, x + 1].find((stem) => STEMS.has(stem) && y < STEMS.get(stem) && (y + stem) % 3 === 0 && (x > stem) === (y % 2 === 0));
  texture(blockOutput, "girder_decor_trailing", (x, y) => {
    if (y < 2) return speck(x, y) < 5 ? LEAVES[(x + y) % LEAVES.length] : CLEAR;
    if (STEMS.has(x) && y < STEMS.get(x)) return [0x2c, 0x5a, 0x26];
    return stemBeside(x, y) !== undefined ? LEAVES[(x + y) % 3 === 0 ? 2 : 0] : CLEAR;
  }, 2);

  // The bunting's cord, twisted; and its four pennants, two to each half of the texture's height, each folded over the
  // cord along its top and coming to a point.
  texture(blockOutput, "girder_decor_cord", (x, y) => ((x + y) % 4 === 0 ? [0xb8, 0xb2, 0xa2] : [0xec, 0xe8, 0xdc]), 2);
  const PENNANTS = [[0x2c, 0xc4, 0xc8], [0xf2, 0xf4, 0xf6], [0x1c, 0x3c, 0x8c], [0xf0, 0x6a, 0x50]];
  texture(blockOutput, "girder_decor_pennants", (x, y) => {
    const colour = PENNANTS[(x >> 3) + 2 * (y >> 3)];
    const down = y % 8;
    const half = 4 * (1 - (down + 0.5) / 8) + 0.3;
    if (Math.abs((x % 8) + 0.5 - 4) >= half) return CLEAR;
    if (down === 0) return scaled(colour, 0.72);
    return colour === PENNANTS[1] && Math.abs((x % 8) + 0.5 - 4) >= half - 1 ? [0xc8, 0xcc, 0xd2] : colour;
  }, 2);

  // A spotlight's finned can; its lens, hottest in the middle; and its beam, a cone of its light widening and fading as
  // it falls from the lens.
  texture(blockOutput, "girder_decor_can", (x, y) => (y % 2 === 0 ? [0x2c, 0x2e, 0x34] : [0x18, 0x19, 0x1c]), 1);
  const SPOTS = { pink: [0xff, 0x4a, 0xc4], cyan: [0x3c, 0xec, 0xff] };
  for (const [name, colour] of Object.entries(SPOTS)) {
    texture(blockOutput, `girder_decor_lens_${name}`, (x, y) => mix([0xff, 0xff, 0xff], colour, Math.min(1, Math.hypot(x - 7.5, y - 7.5) / 6)), 0);
    texture(blockOutput, `girder_decor_beam_${name}`, (x, y) => {
      const half = 2.6 + y * 0.34;
      const off = Math.abs(x + 0.5 - 8) / half;
      if (off >= 1) return CLEAR;
      const alpha = Math.round(0xa8 * (1 - y / 16) ** 1.4 * (1 - off * off));
      return [...mix(colour, [0xff, 0xff, 0xff], 0.25 * (1 - off)), alpha];
    }, 0);
  }

  // The cable tray's galvanised steel, punched with holes; and the cables, a colour to each two pixels across, a darker
  // pixel down the far side of each.
  texture(blockOutput, "girder_decor_tray", (x, y) => {
    if (x % 4 === 1 && y % 4 === 1) return [0x3c, 0x40, 0x46];
    return x === 0 || x === 15 ? [0x8a, 0x90, 0x96] : [0xb0, 0xb6, 0xbc];
  }, 3);
  const CABLES = [[0xc8, 0x2c, 0x2c], [0x2a, 0x5c, 0xc8], [0xf0, 0xc8, 0x28], [0x2e, 0x9a, 0x3e], [0x1c, 0x1d, 0x20], [0xf0, 0x80, 0x24],
    [0x8a, 0x90, 0x96], [0xe8, 0xec, 0xf0]];
  texture(blockOutput, "girder_decor_cables", (x) => (x % 2 === 1 ? scaled(CABLES[x >> 1], 0.8) : CABLES[x >> 1]), 2);
}
girderDecorTextures();

// The rocket's airlock panel (see airlockPanel in generate_resources.mjs): its brushed steel casing and darker post, and
// its screen's three pictures, in the middle band of each texture, which is all the screen shows. Under a lamp bar
// along the top, a door in its frame: sealed, shut and locked under a red bar; cycling, shut, air streaming out past it
// either side and amber chevrons sweeping along the bar, a frame at a time; clear, slid open under a green bar, the
// doorway between its panels glowing green.
function airlockPanelTextures() {
  const FRAMES = 8;
  const BACK = [0x0c, 0x12, 0x1a];
  const FRAME = [0xc8, 0xd0, 0xd8];
  const PANEL = [0x8e, 0x98, 0xa4];
  const BAR = { sealed: [0xe8, 0x34, 0x30], clear: [0x3c, 0xe0, 0x6a] };
  const AMBER = [0xf8, 0xb0, 0x28];
  const AMBER_DIM = [0x6a, 0x46, 0x10];
  texture(blockOutput, "airlock_casing", (x, y) => (x === 0 || y === 0 ? [0x7a, 0x82, 0x8b] : [0x9c, 0xa4, 0xad]), 3);
  texture(blockOutput, "airlock_post", () => [0x58, 0x5e, 0x66], 2);
  // The door's frame round its opening, and whether a spot of the band is in the opening.
  const frame = (x, y) => (x === 5 || x === 10) && y >= 6 || y === 6 && x >= 5 && x <= 10;
  const opening = (x, y) => x >= 6 && x <= 9 && y >= 7;
  const screen = (bar, door) => (x, y, frame0 = 0) => {
    if (y < 4 || y > 11) return BACK;
    if (y === 4) return x >= 1 && x <= 14 ? bar(x, frame0) : BACK;
    if (frame(x, y)) return FRAME;
    return door(x, y, frame0) ?? BACK;
  };
  const shut = (x, y) => (opening(x, y) ? (x === 7 ? [0x5a, 0x62, 0x6c] : PANEL) : null);
  texture(blockOutput, "airlock_screen_sealed", screen(() => BAR.sealed, (x, y) => (x === 8 && y === 9 ? BAR.sealed : shut(x, y))), 0);
  texture(blockOutput, "airlock_screen_clear", screen(() => BAR.clear, (x, y) => {
    if (!opening(x, y)) return null;
    return x === 6 || x === 9 ? PANEL : [0x9c, 0xf0, 0xb8];
  }), 0);
  const cycling = screen((x, at) => ((x + at) % 4 < 2 ? AMBER : AMBER_DIM), (x, y, at) => {
    const streaming = y >= 7 && y <= 10 && (x <= 3 || x >= 12);
    if (streaming) return (Math.abs(x - 7.5) + at + y) % 3 < 1 ? [0xb8, 0xe4, 0xff] : null;
    return shut(x, y);
  });
  texture(blockOutput, "airlock_screen_cycling", (x, y) => cycling(x, y % SIZE, FRAMES - 1 - Math.floor(y / SIZE)), 0, SIZE, SIZE * FRAMES);
  writeFileSync(resolve(blockOutput, "airlock_screen_cycling.png.mcmeta"), JSON.stringify({ animation: { frametime: 2 } }, null, 2) + "\n");
}
airlockPanelTextures();

// A rocket's sliding airlock doors (see RocketStairsRenderer), each picture the whole doorway, both leaves side by side
// as seen from its own side, three blocks wide and three tall, each leaf mirroring the other about their meeting edge;
// their skin outside is her cover's weave (airlock_door_skin, with the zeppelin's). Outside, a stripe round the doorway
// tinted as her fins are, and over it the parts in their own colours: a black rubber seal down the meeting edge between
// steel edging, two latch dogs across it, a tall slim window beside the seal in each leaf, dark glass glinting in a
// steel frame, and a band of hazard chevrons along the foot pointing in to the seam; no handles, as the doors work
// themselves. Inside, brushed steel stiffened by two ribs across, the same windows and seal, a grab bar under each
// window and a hazard kick plate along the foot.
function airlockDoorTextures() {
  const DOOR = AIRLOCK_DOOR;
  const LEAF = DOOR / 2;
  const RUBBER = [0x1c, 0x1d, 0x20];
  const EDGING = [0xb8, 0xbe, 0xc6];
  const STEEL_LIGHT = [0xc4, 0xca, 0xd0];
  const STEEL_SHINE = [0xdc, 0xe0, 0xe4];
  const STEEL_SHADE = [0x8e, 0x96, 0x9e];
  const LIP = [0x3a, 0x3f, 0x45];
  const YELLOW = [0xf0, 0xbe, 0x22];
  const BLACK = [0x22, 0x22, 0x26];
  // Across a leaf from the doorway's side, toward the seal at LEAF - 1, whichever leaf a column is in.
  const across = (x) => (x < LEAF ? x : DOOR - 1 - x);
  // The window's glass, across a leaf and down from the top, from first to last pixel; its frame and dark lip round
  // it, the lip's corners cut off so it reads rounded.
  const GLASS = { left: 15, right: 19, top: 9, bottom: 24 };
  const window = (x, y) => {
    const a = across(x);
    const out = Math.max(GLASS.left - a, a - GLASS.right, GLASS.top - y, y - GLASS.bottom, 0);
    const corner = (a < GLASS.left || a > GLASS.right) && (y < GLASS.top || y > GLASS.bottom);
    if (out > 2 || out === 2 && corner) return null;
    if (out === 2) return LIP;
    if (out === 1) {
      if (a < GLASS.left || y < GLASS.top) return corner ? STEEL_LIGHT : STEEL_SHINE;
      return STEEL_SHADE;
    }
    const along = a - GLASS.left + y - GLASS.top;
    if (along === 3 || along === 4 || along === 8) return [0xb4, 0xd8, 0xf0];
    const down = (y - GLASS.top) / (GLASS.bottom - GLASS.top);
    return [0x26 - 0x14 * down, 0x48 - 0x22 * down, 0x64 - 0x28 * down].map(Math.round);
  };
  // The seal down the meeting edge between its steel edging.
  const seal = (x) => (x === LEAF - 1 || x === LEAF ? RUBBER : x === LEAF - 2 || x === LEAF + 1 ? EDGING : null);
  // A band of chevrons from row top to row bottom, outlined in black, across a leaf short of its edges.
  const chevrons = (x, y, top, bottom) => {
    const a = across(x);
    if (y < top - 1 || y > bottom + 1 || a < 2 || a > LEAF - 3) return null;
    if (y === top - 1 || y === bottom + 1) return BLACK;
    return (a + y) % 6 < 3 ? YELLOW : BLACK;
  };
  // Outside: the stripe round the doorway, for her fins' tint, two pixels wide three in from its edges, broken at the
  // seam.
  const STRIPE_IN = 3;
  texture(blockOutput, "airlock_door_stripe", (x, y) => {
    const a = across(x);
    const band = (at) => at === STRIPE_IN || at === STRIPE_IN + 1;
    const inFrame = a >= STRIPE_IN && y >= STRIPE_IN && y <= DOOR - 1 - STRIPE_IN && a < LEAF - STRIPE_IN;
    return inFrame && (band(a) || band(y) || band(DOOR - 1 - y)) ? [0xf4, 0xf4, 0xf4] : CLEAR;
  }, 3, DOOR, DOOR);
  // Outside: a latch dog across the seam, so many rows down.
  const latch = (x, y, top) => {
    const a = across(x);
    if (a < LEAF - 3 || y < top || y > top + 2) return null;
    return y === top ? STEEL_SHINE : STEEL_DARK;
  };
  texture(blockOutput, "airlock_door_trim", (x, y) =>
    latch(x, y, 4) ?? latch(x, y, 32) ?? seal(x) ?? window(x, y) ?? chevrons(x, y, 38, 41) ?? CLEAR, 2, DOOR, DOOR);
  // Inside: brushed steel, its grain along the rows; a darker edge round each leaf; two ribs across, light over dark,
  // short of the windows; a grab bar under each window standing off on brackets, its shadow under it; and the kick
  // plate.
  const BRUSHED = [0x9c, 0xa4, 0xad];
  const brushed = (y) => BRUSHED.map((c) => c + (((y * 37) % 7) - 3) * 2);
  const rib = (x, y) => {
    const a = across(x);
    if (a < 3 || a > LEAF - 4) return null;
    if (y === 15 || y === 33) return [0xc8, 0xce, 0xd4];
    return y === 16 || y === 34 ? [0x6c, 0x74, 0x7c] : null;
  };
  const grabBar = (x, y) => {
    const a = across(x);
    if (a < 9 || a > 20 || y < 28 || y > 30) return null;
    if (a < 11 || a > 18) return y === 30 ? null : STEEL_DARK;
    if (y === 28) return STEEL_SHINE;
    return y === 29 ? STEEL_LIGHT : [0x50, 0x56, 0x5c];
  };
  const leafEdge = (x, y) => {
    const a = across(x);
    if (a === 0 || y === 0 || y === DOOR - 1) return STEEL_DARK;
    if (a === 1 || y === 1) return EDGING;
    return a === LEAF - 3 || y === DOOR - 2 ? [0x7a, 0x82, 0x8b] : null;
  };
  texture(blockOutput, "airlock_door_inside", (x, y) =>
    seal(x) ?? leafEdge(x, y) ?? window(x, y) ?? grabBar(x, y) ?? chevrons(x, y, 38, 44) ?? rib(x, y) ?? brushed(y), 3, DOOR, DOOR);
}
airlockDoorTextures();

// The cargo bay's parcel scanner (see parcelScanner in generate_resources.mjs): its graphite cabinet with a vent grille,
// its steel rollers and its black belt ridged across its run, its gantry striped yellow and black, and its lamp in each
// step's colour: dim blue ready, red scanning, green for a packed crate, red blinking for anything else. Its laser
// curtain, translucent red, bright lines running down it. Its screen's pictures, a crate drawn in outline: ready, grey;
// scanning, a red line sweeping down it; packed, a green tick over it; empty, a red cross.
function parcelScannerTextures() {
  const FRAMES = 8;
  const animated = (name, frametime, paint, noise = 0) => {
    texture(blockOutput, name, (x, y) => paint(x, y % SIZE, Math.floor(y / SIZE)), noise, SIZE, SIZE * FRAMES);
    writeFileSync(resolve(blockOutput, `${name}.png.mcmeta`), JSON.stringify({ animation: { frametime } }, null, 2) + "\n");
  };
  const RED = [0xff, 0x3c, 0x30];
  const GREEN = [0x40, 0xf0, 0x70];
  texture(blockOutput, "scanner_casing", (x, y) => {
    if (y === 0) return [0x50, 0x56, 0x5e];
    return y >= 10 && y <= 13 && x >= 2 && x <= 13 && y % 2 === 0 ? [0x1a, 0x1c, 0x20] : [0x34, 0x38, 0x3e];
  }, 2);
  texture(blockOutput, "scanner_roller", (x, y) => (x % 4 === 0 ? [0x5a, 0x60, 0x68] : [0x9a, 0xa2, 0xaa]), 2);
  texture(blockOutput, "scanner_belt", (x, y) => (x % 3 === 0 ? [0x30, 0x33, 0x37] : [0x1c, 0x1e, 0x21]), 1);
  texture(blockOutput, "scanner_gantry", (x, y) => ((x + y) % 8 < 4 ? [0xf0, 0xc0, 0x20] : [0x1c, 0x1d, 0x20]), 2);
  texture(blockOutput, "scanner_lamp_ready", () => [0x2a, 0x5a, 0xa0], 0);
  texture(blockOutput, "scanner_lamp_scanning", () => RED, 0);
  texture(blockOutput, "scanner_lamp_packed", () => GREEN, 0);
  animated("scanner_lamp_empty", 4, (x, y, frame) => (frame % 2 === 0 ? [0xf0, 0x30, 0x30] : [0x50, 0x10, 0x10]));
  animated("scanner_laser", 1, (x, y, frame) => ((y + frame * 2) % 4 === 0 ? [0xff, 0x90, 0x80, 0xc0] : [0xff, 0x30, 0x30, 0x50]));
  const SCREEN = [0x0a, 0x0e, 0x12];
  // A crate in outline, a brace across it from corner to corner.
  const crate = (x, y) => (x >= 3 && x <= 12 && y >= 4 && y <= 12) && (x === 3 || x === 12 || y === 4 || y === 12 || x - 3 === y - 4);
  const OUTLINE = [0x8a, 0x92, 0x9c];
  const DIM = [0x40, 0x46, 0x4e];
  texture(blockOutput, "scanner_screen_ready", (x, y) => (crate(x, y) ? OUTLINE : SCREEN), 0);
  animated("scanner_screen_scanning", 2, (x, y, frame) => {
    if (y === 3 + frame + Math.floor(frame / 2) && x >= 1 && x <= 14) return RED;
    return crate(x, y) ? OUTLINE : SCREEN;
  });
  const tick = (x, y) => (x >= 3 && x <= 6 && (y - x === 5 || y - x === 4)) || (x >= 6 && x <= 12 && (x + y === 17 || x + y === 16));
  texture(blockOutput, "scanner_screen_packed", (x, y) => (tick(x, y) ? GREEN : crate(x, y) ? DIM : SCREEN), 0);
  const cross = (x, y) => x >= 3 && x <= 12 && y >= 3 && y <= 12 && (x - y === 0 || x + y === 15 || x - y === 1 || x + y === 16);
  texture(blockOutput, "scanner_screen_empty", (x, y) => (cross(x, y) ? RED : crate(x, y) ? DIM : SCREEN), 0);
}
parcelScannerTextures();

// The galley's fridge (see galleyFridge in generate_resources.mjs): brushed stainless steel; its door, brushed too, a
// blue light by its handle; its white inside; its door's inside, white bins holding bottles; and its food sheet, a patch
// four pixels square for each thing kept in it: milk, cheese, greens, tomatoes, orange juice, a pink cake, a green
// bottle and a brown one, and a carton.
function galleyFridgeTextures() {
  const STEEL = [0xc4, 0xca, 0xd0];
  const brushed = (x, y) => (y % 3 === 0 ? [0xb4, 0xba, 0xc1] : STEEL);
  texture(blockOutput, "fridge_steel", brushed, 2);
  texture(blockOutput, "fridge_door", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return [0x9a, 0xa1, 0xa9];
    if (x === 13 && y >= 3 && y <= 4) return [0x40, 0xa8, 0xff];
    return brushed(x, y);
  }, 2);
  texture(blockOutput, "fridge_inside", (x, y) => (y % 5 === 0 ? [0xe2, 0xea, 0xf0] : [0xf6, 0xfa, 0xfc]), 1);
  const BOTTLES = [[0x3a, 0x9a, 0x4a], [0xf0, 0xa0, 0x30], [0xe8, 0xe8, 0xe0], [0x8a, 0x4a, 0x22]];
  texture(blockOutput, "fridge_door_inside", (x, y) => {
    if (y === 6 || y === 12) return [0xd8, 0xe2, 0xea];
    if ((y > 1 && y < 6 || y > 7 && y < 12) && x % 4 !== 0) return BOTTLES[(Math.floor(x / 4) + Math.floor(y / 6)) % BOTTLES.length];
    return [0xf6, 0xfa, 0xfc];
  }, 1);
  // Each thing's colour, and its label or mark, row by row of the sheet's four-pixel patches.
  const FOOD = [
    [[0xf4, 0xf6, 0xf8], [0x3a, 0x6c, 0xd0]], [[0xf0, 0xc8, 0x40], [0xd0, 0xa0, 0x20]], [[0x4a, 0xa8, 0x3a], [0x2e, 0x80, 0x28]],
    [[0xd8, 0x30, 0x24], [0xf2, 0xee, 0xe2]], [[0xf8, 0x9a, 0x20], [0xf2, 0xee, 0xe2]], [[0xf0, 0x9a, 0xb8], [0xfa, 0xf0, 0xf4]],
    [[0x2e, 0x8a, 0x3e], [0x1e, 0x5a, 0x28]], [[0x7a, 0x44, 0x1e], [0xe8, 0xc8, 0x80]], [[0xf0, 0xf2, 0xf4], [0x2a, 0x7a, 0xd8]],
  ];
  texture(blockOutput, "fridge_food", (x, y) => {
    const patch = FOOD[Math.floor(y / 4) * 4 + Math.floor(x / 4)];
    if (!patch) return [0xf6, 0xfa, 0xfc];
    return y % 4 === 1 || y % 4 === 2 && x % 4 === 1 ? patch[1] : patch[0];
  }, 2);
}
galleyFridgeTextures();

// The pool's wave machine (see waveMachine in generate_resources.mjs): its white enamel, a blue band round its head,
// and its chrome foot; the ring round its button, soft blue idle and bright running; and the button, blue with a white
// wave across it, the wave rolling a frame at a time while it runs.
function waveMachineTextures() {
  const FRAMES = 8;
  texture(blockOutput, "wave_machine_body", (x, y) => (y >= 6 && y <= 8 ? [0x2a, 0x7a, 0xd0] : [0xf2, 0xf5, 0xf8]), 2);
  texture(blockOutput, "wave_machine_chrome", (x, y) => ((x + y) % 5 === 0 ? [0xe8, 0xec, 0xf0] : [0xb8, 0xc0, 0xc8]), 1);
  texture(blockOutput, "wave_ring", () => [0x3a, 0x7a, 0xc0], 0);
  texture(blockOutput, "wave_ring_running", () => [0x70, 0xe0, 0xff], 0);
  // The wave: a crest running across the button, its height a sine of how far across, rolled on a pixel a frame.
  const wave = (x, y, at) => Math.abs(y - (8 + 2.5 * Math.sin((2 * Math.PI * (x + at)) / 8))) < 1;
  texture(blockOutput, "wave_button", (x, y) => (wave(x, y, 0) ? [0xf8, 0xfc, 0xff] : [0x1e, 0x5e, 0xc8]), 0);
  texture(blockOutput, "wave_button_running", (x, y) => (wave(x, y % SIZE, Math.floor(y / SIZE)) ? [0xff, 0xff, 0xff] : [0x30, 0xa8, 0xf0]),
    0, SIZE, SIZE * FRAMES);
  writeFileSync(resolve(blockOutput, "wave_button_running.png.mcmeta"), JSON.stringify({ animation: { frametime: 2 } }, null, 2) + "\n");
}
waveMachineTextures();

// The club's fog machine (see fogMachine in generate_resources.mjs): its black flight case, aluminium round its edges
// and a ball corner at each; its aluminium handle; its chrome nozzle, a dark ring of a mouth, or that ring glowing white
// as it pumps; and its little light, green, white or amber.
function fogMachineTextures() {
  const ALUMINIUM = [0xb4, 0xba, 0xc2];
  texture(blockOutput, "fog_case", (x, y) => {
    const edge = x === 0 || x === 15 || y === 0 || y === 15;
    if (edge && (x === 0 || x === 15) && (y === 0 || y === 15)) return [0xe0, 0xe4, 0xe8];
    return edge ? ALUMINIUM : (x + 2 * y) % 7 === 0 ? [0x24, 0x25, 0x28] : [0x18, 0x19, 0x1b];
  }, 2);
  texture(blockOutput, "fog_trim", () => ALUMINIUM, 2);
  texture(blockOutput, "fog_nozzle", (x, y) => (y % 4 === 0 ? [0xe8, 0xec, 0xf0] : [0xa8, 0xb0, 0xb8]), 1);
  const ring = (x, y) => Math.abs(Math.hypot(x - 7.5, y - 7.5) - 4.5) < 1.5;
  texture(blockOutput, "fog_mouth", (x, y) => (ring(x, y) ? [0x1a, 0x1b, 0x1e] : [0xa8, 0xb0, 0xb8]), 1);
  texture(blockOutput, "fog_mouth_hot", (x, y) => (ring(x, y) ? [0xf8, 0xf4, 0xff] : Math.hypot(x - 7.5, y - 7.5) < 3 ? [0xd8, 0xd0, 0xf0] : [0xa8, 0xb0, 0xb8]), 1);
  texture(blockOutput, "fog_light_ready", () => [0x40, 0xf0, 0x70], 0);
  texture(blockOutput, "fog_light_fogging", () => [0xf8, 0xf8, 0xff], 0);
  texture(blockOutput, "fog_light_heating", () => [0xf8, 0xa8, 0x28], 0);
}
fogMachineTextures();

// The club's bar (see the club's bar in generate_resources.mjs). The neon signs' tubes, lit each colour bright and a
// little whitened, as a hot tube is, and off a dark glass with a tint of its colour and a glint here and there; their
// black acrylic backing board, a lighter edge round it and a screw in each corner. The drink tap's drip tray grate,
// chrome bars over dark slots; the little badge on its column, a gold sparkle on violet in its top left corner, three
// pixels square; and its stream of Star Fizz, a glowing violet you can half see through, bubbles and the odd gold
// spark running down it a frame at a time. The cocktail table's top: black glass, a streak of sheen across it. And
// Star Fizz's picture: a bottle of glowing violet soda under a gold cap, a gold star on it, bubbles in it and a few
// rising from it.
function clubBarTextures() {
  const mix = (a, b, t) => a.map((c, i) => Math.round(c + (b[i] - c) * t));
  const NEON = {
    pink: [0xff, 0x4a, 0xc4], cyan: [0x3c, 0xec, 0xff], lime: [0x9a, 0xff, 0x3c], red: [0xff, 0x34, 0x4c],
    violet: [0xb4, 0x64, 0xff], amber: [0xff, 0x9c, 0x2c], yellow: [0xff, 0xec, 0x48], white: [0xf2, 0xf6, 0xff],
  };
  const DARK_GLASS = [0x26, 0x28, 0x2e];
  for (const [name, colour] of Object.entries(NEON)) {
    texture(blockOutput, `neon_${name}`, () => mix(colour, [0xff, 0xff, 0xff], 0.2), 0);
    texture(blockOutput, `neon_${name}_off`, (x, y) => mix(colour, DARK_GLASS, (x + 2 * y) % 5 === 0 ? 0.55 : 0.8), 1);
  }
  texture(blockOutput, "neon_backer", (x, y) => {
    if ((x === 1 || x === 14) && (y === 1 || y === 14)) return [0x9a, 0xa0, 0xa8];
    return x === 0 || x === 15 || y === 0 || y === 15 ? [0x24, 0x24, 0x2a] : [0x0e, 0x0e, 0x12];
  }, 1);

  // The hub in the middle of a launch pad's grate: a dark steel plate under a yellow cross, a bolt in each corner.
  texture(blockOutput, "launch_pad_hub", (x, y) => {
    const cross = Math.abs(x - 7.5) <= 1 || Math.abs(y - 7.5) <= 1;
    const bolt = (x === 2 || x === 13) && (y === 2 || y === 13);
    if (cross) return [0xe6, 0xb8, 0x1e];
    return bolt ? [0x8c, 0x92, 0x98] : [0x2c, 0x2f, 0x34];
  }, 2);
  texture(blockOutput, "drink_tap_grate", (x, y) => (y % 2 === 0 ? [0x9a, 0xa2, 0xaa] : [0x18, 0x19, 0x1c]), 1);
  const VIOLET = [0x5a, 0x2c, 0xc0];
  const SPARKLE = [[VIOLET, GOLD, VIOLET], [GOLD, [0xff, 0xfa, 0xe0], GOLD], [VIOLET, GOLD, VIOLET]];
  texture(blockOutput, "drink_tap_badge", (x, y) => (y < SPARKLE.length && x < 3 ? SPARKLE[y][x] : VIOLET), 0);
  const FRAMES = 8;
  // Each frame a little further down: bubbles every eight rows and sparks every sixteen, two rows a frame, so the
  // eighth frame runs on into the first.
  texture(blockOutput, "star_fizz_stream", (x, y) => {
    const row = y % SIZE;
    const down = 2 * Math.floor(y / SIZE);
    if ((row + 3 * x - down + 64) % 8 === 0) return [0xf0, 0xea, 0xff, 0xf0];
    if ((row + 5 * x - down + 64) % 16 === 9) return [0xff, 0xe0, 0x70, 0xff];
    return [0x8a, 0x5c, 0xff, 0xc8];
  }, 0, SIZE, SIZE * FRAMES);
  writeFileSync(resolve(blockOutput, "star_fizz_stream.png.mcmeta"), JSON.stringify({ animation: { frametime: 1 } }, null, 2) + "\n");

  texture(blockOutput, "cocktail_table_glass", (x, y) => {
    const streak = (x - y + SIZE) % SIZE;
    if (streak === 3 || streak === 4) return [0x40, 0x44, 0x50];
    return streak === 7 ? [0x26, 0x28, 0x30] : [0x12, 0x13, 0x18];
  }, 1);

  // The bottle, row by row, each letter a colour of INK: its cap, its neck, the soda in it with its bubbles and the
  // star on it; and fizz rising from it, not outlined.
  const BOTTLE = [
    "................",
    ".......YY.......",
    "......yYYy......",
    ".......gg.......",
    ".......gg.......",
    ".......hs.......",
    "......hsbs......",
    ".....hssssb.....",
    ".....hsssss.....",
    ".....hss*ss.....",
    ".....h*****.....",
    ".....hs***s.....",
    ".....hs*s*s.....",
    ".....hsbsss.....",
    ".....dddddd.....",
    "................",
  ];
  const FIZZ = [[10, 1], [11, 3], [4, 2]];
  const INK = {
    Y: GOLD, y: [0xb8, 0x8a, 0x22], g: [0xc0, 0xd8, 0xe4], h: [0xc4, 0xa8, 0xff], s: [0x7c, 0x4c, 0xf0], b: [0xe8, 0xe0, 0xff],
    "*": [0xff, 0xe0, 0x50], d: [0x4a, 0x30, 0x9c],
  };
  const inBottle = (x, y) => (x >= 0 && x < SIZE && y >= 0 && y < SIZE ? INK[BOTTLE[y][x]] : undefined);
  texture(itemOutput, "star_fizz", (x, y) => {
    const ink = inBottle(x, y);
    if (ink) return ink;
    if ([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => inBottle(x + dx, y + dy))) return [0x1a, 0x12, 0x30];
    return FIZZ.some(([fx, fy]) => fx === x && fy === y) ? [0xd8, 0xcc, 0xff] : CLEAR;
  }, 0);
}
clubBarTextures();

// The decorations on the outside of the rocket's lift shaft (see ShaftDecorBlock), each of her rooms' its own, with no
// words in any of them. The observation deck's curtains: deep blue velvet, its folds' crests catching a soft sheen
// and its hollows deep, gold braid for its tiebacks and a gold fringe; the pelmet of its valance box-pleated over a
// gold braid, and its swags draped in curved folds. The entry's departures board, lit on a dark panel: under a rocket
// lifting off and a clock, a row for each destination, a clock face telling when, the Moon, Mars, a ringed planet or
// a space station, split flaps in the destination's colour, the gate's coloured door and a lamp, green for boarding,
// amber for soon and red for held. The cargo bay's load chart, black pictograms on white, each with a green tick or a
// red cross: one crate, two and three on a pallet, a crate strapped down, a crate sliding loose, a forklift; and its
// corner guards' rubber striped yellow and black. The mess's chalkboard, one dish in chalk on each block and coins
// under it for what it costs: a burger, a bowl of noodles, a slice of pizza, a carrot, a cupcake and a mug of cocoa.
// The lab's whiteboard: a molecule of balls and sticks, an arrow and a graph climbing past a dashed line, the ghosts of
// old sums wiped off it, and a sticky note with a water bear. The greenhouse's ivy, two layers of leaves over its
// stems, climbing, trailing along the top and hanging from it. The bunk room's warm bulbs on their dark wire, the
// pool's life ring red and white, and the boards' frames: graphite, oak, aluminium. And its item, a curtain tied back.
function shaftDecorTextures() {
  const hex = (value) => [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
  const mix = (a, b, t) => a.map((c, i) => Math.round(c + (b[i] - c) * t));
  const part = (name) => `shaft_decor_${name}`;
  // A blank picture so many pixels wide and tall, each pixel a colour or nothing; a pixel put on it, where it is on it;
  // and a sprite stamped on it, rows of letters each a colour of its palette, its top left at a spot, turned over
  // left to right if asked.
  const blank = (width, height, fill = null) => Array.from({ length: height }, () => Array(width).fill(fill));
  const put = (picture, x, y, colour) => {
    if (colour && y >= 0 && y < picture.length && x >= 0 && x < picture[0].length) picture[y][x] = colour;
  };
  const stamp = (picture, rows, palette, left, top, flipped = false) => {
    rows.forEach((row, y) => [...row].forEach((letter, x) => put(picture, left + (flipped ? row.length - 1 - x : x), top + y, palette[letter])));
  };
  const save = (name, picture, noise = 0) => texture(blockOutput, name, (x, y) => picture[y][x] ?? CLEAR, noise, picture[0].length, picture.length);
  // A board three blocks wide and two tall painted whole and cut into its blocks, by their place across from the left
  // and their row, the top or the bottom.
  const board = (kind, picture, noise = 0) => {
    for (const [column, along] of ["left", "middle", "right"].entries()) {
      for (const [row, place] of ["top", "bottom"].entries()) {
        const piece = picture.slice(row * SIZE, (row + 1) * SIZE).map((line) => line.slice(column * SIZE, (column + 1) * SIZE));
        save(part(`${kind}_${along}_${place}`), piece, noise);
      }
    }
  };
  // A deterministic sequence of its own, from 0 to 1, for where the ivy's leaves fall and the chalk's dust.
  const sequence = (salt) => {
    let state = salt >>> 0;
    return () => {
      state = (state + 0x6d2b79f5) >>> 0;
      let value = Math.imul(state ^ (state >>> 15), 1 | state);
      value = (value + Math.imul(value ^ (value >>> 7), 61 | value)) ^ value;
      return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
    };
  };

  // The velvet and its gold.
  const VELVET = hex(0x1c2a70);
  const VELVET_DEEP = hex(0x0f174a);
  const VELVET_MID = hex(0x2c3f98);
  const VELVET_SHEEN = hex(0x4560c0);
  const GOLD_BRAID = hex(0xd6a63e);
  const GOLD_LIGHT = hex(0xf8dc80);
  const GOLD_DARK = hex(0x96681c);
  texture(blockOutput, part("velvet"), (x) => mix(VELVET_DEEP, VELVET, 0.55 + 0.45 * ((x * 7) % 5) / 4), 3);
  texture(blockOutput, part("velvet_crest"), (x, y) => (x % 2 === 0 ? mix(VELVET_SHEEN, VELVET_MID, (y % 5) / 10) : VELVET_MID), 3);
  texture(blockOutput, part("valance"), (x, y) => {
    if (y === 0) return VELVET_DEEP;
    if (y === 4) return (x + y) % 2 === 0 ? GOLD_LIGHT : GOLD_BRAID;
    if (x % 4 === 0) return VELVET_DEEP;
    return x % 4 === 1 ? VELVET_MID : VELVET;
  }, 3);
  // The swag's folds follow its hem, which dips to its lowest in the middle of its block.
  texture(blockOutput, part("swag"), (u, v) => {
    const x = 15.5 - u;
    const hem = 6 + 5 * ((x - 8) / 8) ** 2;
    const over = 16 - v - hem;
    if (over > 3) return VELVET_DEEP;
    return Math.floor(over / 1.25) % 2 === 0 ? VELVET_MID : VELVET;
  }, 3);
  texture(blockOutput, part("gold"), (x, y) => {
    if ((x + y) % 3 === 0) return GOLD_LIGHT;
    return (x - y + 18) % 3 === 0 ? GOLD_DARK : GOLD_BRAID;
  }, 3);
  texture(blockOutput, part("fringe"), (x) => {
    if (x % 2 === 1) return CLEAR;
    return x % 4 === 0 ? GOLD_BRAID : GOLD_LIGHT;
  }, 0);

  // The rubber of the cargo bay's corner guards, and the frames of the boards.
  texture(blockOutput, part("hazard"), (x, y) => ((x + y) % 8 < 4 ? [0xf0, 0xbe, 0x22] : [0x22, 0x22, 0x26]), 4);
  texture(blockOutput, part("casing"), (x, y) => (y === 0 || x === 0 ? [0x46, 0x4c, 0x56] : [0x2a, 0x2e, 0x34]), 2);
  texture(blockOutput, part("aluminium"), (x, y) => mix([0xb8, 0xbe, 0xc6], [0xdc, 0xe0, 0xe6], (y * 5 + (x % 3)) % 4 / 3), 2);
  texture(blockOutput, part("oak"), (x, y) => ((y * 3 + Math.floor(x / 5)) % 7 === 0 ? [0x76, 0x4e, 0x2a] : [0x9c, 0x6c, 0x3e]), 4);
  // What the boards' ledges hold, a patch of each: across the top, red, blue and black markers and white chalk; across
  // the bottom, an eraser's grey felt and its blue back.
  const MARKERS = [[0xd2, 0x3a, 0x3a], [0x2a, 0x5a, 0xd2], [0x24, 0x26, 0x2a], [0xf4, 0xf2, 0xea]];
  texture(blockOutput, part("markers"), (x, y) => (y < 8 ? MARKERS[Math.floor(x / 4)] : x < 8 ? [0x6a, 0x6e, 0x76] : [0x30, 0x60, 0xc8]), 2);

  // The bunk room's bulbs, warm and brightest in the middle, and their wire; the pool's life ring.
  texture(blockOutput, part("bulb"), (x, y) => (Math.hypot(x - 7.5, y - 7.5) < 4 ? [0xff, 0xf2, 0xc8] : [0xff, 0xc8, 0x6a]), 0);
  texture(blockOutput, part("wire"), () => [0x1e, 0x2c, 0x22], 2);
  texture(blockOutput, part("ring_red"), (x, y) => ((x + y) % 6 === 0 ? [0xf0, 0x5c, 0x3a] : [0xdc, 0x42, 0x28]), 3);
  texture(blockOutput, part("ring_white"), (x, y) => ((x + y) % 6 === 0 ? [0xd8, 0xdc, 0xe0] : [0xf2, 0xf2, 0xee]), 3);

  // The departures board: a dark panel lit row by row, as an LED board is.
  {
    const PANEL = hex(0x0c1118);
    const PANEL_LINE = hex(0x121822);
    const HEADER = hex(0x1a2433);
    const RULE = hex(0x2e3a4c);
    const W = hex(0xe8eef6);
    const AMBER = hex(0xffb030);
    const picture = blank(48, 32);
    for (let y = 0; y < 32; y++) {
      for (let x = 0; x < 48; x++) put(picture, x, y, y % 2 === 0 ? PANEL : PANEL_LINE);
    }
    for (let y = 1; y <= 5; y++) {
      for (let x = 1; x <= 46; x++) put(picture, x, y, HEADER);
    }
    for (let x = 1; x <= 46; x++) put(picture, x, 6, RULE);
    // A rocket lifting off, its flame under it; the clock on the header's right; amber dots running between them.
    stamp(picture, ["..w..", ".www.", ".wbw.", "wwwww", ".o.o.", ".yoy."], { w: W, b: hex(0x58a8ff), o: hex(0xff7a2a), y: hex(0xffd84a) }, 3, 0);
    stamp(picture, [".www.", "w.a.w", "w.aaw", "w...w", ".www."], { w: W, a: AMBER }, 41, 1);
    for (let x = 11; x <= 37; x += 2) put(picture, x, 3, mix(AMBER, HEADER, 0.45));
    const destinations = [
      { // The Moon, grey and cratered.
        sprite: [".www.", "wwgww", "wwwgw", "wgwww", ".www."], palette: { w: hex(0xd8dce4), g: hex(0x9aa0ac) },
        flap: hex(0xb8bec8), gate: hex(0x3ce0ff), lamp: hex(0x40e060), hands: [[2, 1], [3, 2]],
      },
      { // Mars, rusty, a white cap at its pole.
        sprite: [".www.", "rrrRr", "rRrrr", "rrrRr", ".rrr."], palette: { w: hex(0xf4f0ec), r: hex(0xd8643a), R: hex(0x9a3c22) },
        flap: hex(0xcc5a2e), gate: hex(0xff5ad0), lamp: hex(0x40e060), hands: [[2, 1], [1, 2]],
      },
      { // A ringed planet, its ring across it.
        sprite: ["..ttt..", ".ttttt.", "yyyyyyy", ".ttttt.", "..ttt.."], palette: { t: hex(0xe0b878), y: hex(0xfff0b0) },
        flap: hex(0xdcb46e), gate: hex(0xffe04a), lamp: hex(0xffb030), hands: [[2, 3], [3, 2]],
      },
      { // A space station, its solar wings either side of its modules.
        sprite: ["bb...bb", "bb.w.bb", "bwwwwwb", "bb.w.bb", "bb...bb"], palette: { b: hex(0x4a7ae8), w: W },
        flap: hex(0x4878d8), gate: hex(0x9aff4a), lamp: hex(0xff4048), hands: [[2, 1], [2, 3]],
      },
    ];
    destinations.forEach((destination, row) => {
      const top = 8 + row * 6;
      for (let x = 1; x <= 46; x++) put(picture, x, top + 5, RULE);
      // A clock face telling when it goes, its two hands set differently for each.
      stamp(picture, [".www.", "w...w", "w.a.w", "w...w", ".www."], { w: mix(W, PANEL, 0.25), a: AMBER }, 2, top);
      for (const [x, y] of destination.hands) put(picture, 2 + x, top + y, AMBER);
      const width = destination.sprite[0].length;
      stamp(picture, destination.sprite, destination.palette, 9 + Math.floor((7 - width) / 2), top);
      // Split flaps in its colour, each split across its middle.
      for (let flap = 0; flap < 4; flap++) {
        for (let y = 0; y < 5; y++) {
          for (let x = 0; x < 3; x++) put(picture, 19 + flap * 4 + x, top + y, y === 2 ? mix(destination.flap, PANEL, 0.6) : destination.flap);
        }
      }
      // The gate, a door in its colour, and the lamp.
      stamp(picture, ["gggg", "g..g", "g.wg", "g..g", "gggg"], { g: destination.gate, w: W }, 36, top);
      stamp(picture, [".ll.", "lLll", "llll", ".ll."], { l: destination.lamp, L: mix(destination.lamp, W, 0.6) }, 42, top);
    });
    board("entry", picture);
    // Its small version, a whole board twelve pixels wide and eight tall for a stand or to hang (see the models), its
    // front in the texture's top half and its back in the bottom half: over each side, the header with a rocket and
    // the clock, then two rows, each its destination in a few pixels of its own colours, two split flaps in its colour
    // and its lamp; the Moon and Mars on the front, the ringed planet and the space station on the back.
    const TINY = [["wg", "gw"], ["wr", "rR"], ["yty", ".t."], ["bwb", "bwb"]];
    const small = blank(SIZE, SIZE);
    for (let y = 0; y < SIZE; y++) {
      for (let x = 0; x < SIZE; x++) put(small, x, y, y % 2 === 0 ? PANEL : PANEL_LINE);
    }
    for (const side of [0, 1]) {
      const top = side * 8;
      for (let x = 0; x < 12; x++) {
        put(small, x, top, HEADER);
        put(small, x, top + 1, HEADER);
        put(small, x, top + 2, RULE);
        put(small, x, top + 5, mix(RULE, PANEL, 0.5));
      }
      stamp(small, [".w.", "wow"], { w: W, o: hex(0xff7a2a) }, 1, top);
      stamp(small, ["ww", "wa"], { w: W, a: AMBER }, 10, top);
      for (const x of [5, 7]) put(small, x, top + 1, mix(AMBER, HEADER, 0.45));
      for (const row of [0, 1]) {
        const destination = destinations[side * 2 + row];
        const y = top + 3 + row * 3;
        stamp(small, TINY[side * 2 + row], destination.palette, 1, y);
        for (const x of [5, 6, 8, 9]) {
          put(small, x, y, destination.flap);
          put(small, x, y + 1, mix(destination.flap, PANEL, 0.6));
        }
        put(small, 11, y, mix(destination.lamp, W, 0.5));
        put(small, 11, y + 1, destination.lamp);
      }
    }
    save(part("entry_small"), small);
  }

  // The load chart: a pictogram on white in each block, and a tick in a green ring or a cross in a red one.
  {
    const WHITE_PANEL = hex(0xf0f2f4);
    const INK = hex(0x22262c);
    const CRATE = { c: hex(0xc8843a), d: hex(0x8a5420), k: INK, p: hex(0x8c6a48) };
    const OK = ["..ggg..", ".gwwwg.", "gwwwgwg", "gwgwgwg", "gwwgwwg", ".gwwwg.", "..ggg.."];
    const NO = ["..rrr..", ".rwwwr.", "rwrwrwr", "rwwrwwr", "rwrwrwr", ".rwwwr.", "..rrr.."];
    const BADGE = { g: hex(0x2aa04a), r: hex(0xd8302c), w: hex(0xffffff) };
    const box = ["kkkkk", "kcccd", "kcdcd", "kccdd", "kkkkk"];
    const pallet = ["pppppppppp", "p.pp.pp.pp"];
    const picture = blank(48, 32, WHITE_PANEL);
    // A pallet in a block, so many crates stacked on it, and the badge given in the block's top right corner.
    const loaded = (column, row, crates, badge) => {
      const left = column * SIZE;
      const top = row * SIZE;
      stamp(picture, pallet, CRATE, left + 1, top + 13);
      for (let crate = 0; crate < crates; crate++) stamp(picture, box, CRATE, left + 2, top + 9 - crate * 4);
      stamp(picture, badge, BADGE, left + 9, top + 1);
      return [left, top];
    };
    loaded(0, 0, 1, OK);
    loaded(1, 0, 2, OK);
    loaded(2, 0, 3, NO);
    // A crate strapped down to its pallet; a crate sliding loose off one, its speed lines behind it; a forklift with a
    // crate on its forks.
    {
      const [left, top] = loaded(0, 1, 1, OK);
      const STRAP = hex(0x2a60c8);
      for (let y = 8; y <= 13; y++) {
        put(picture, left + 3, top + y, STRAP);
        put(picture, left + 5, top + y, STRAP);
      }
      for (let x = 2; x <= 7; x++) put(picture, left + x, top + 8, STRAP);
    }
    {
      const [left, top] = loaded(1, 1, 0, NO);
      stamp(picture, box, CRATE, left + 6, top + 8);
      stamp(picture, ["kkk", "...", "kkkk", "...", "kk."], { k: INK }, left + 1, top + 7);
    }
    {
      const [left, top] = [2 * SIZE, SIZE];
      stamp(picture, [
        "k.........",
        "k...yyyy..",
        "k...yhhy..",
        "k...yhhy..",
        "kccyyyyyy.",
        "kccyyyyyyy",
        "kkkkkkkkkk",
        "...kk..kk.",
      ], { k: INK, y: hex(0xf0b020), h: hex(0x9ad0ff), c: hex(0xc8843a) }, left + 1, top + 7);
      stamp(picture, OK, BADGE, left + 9, top + 1);
    }
    board("cargo", picture);
  }

  // The chalkboard: dusty slate, a dish in chalk in each block, the coins it costs in yellow chalk under it, and a
  // wavy line of chalk round the board's edge.
  {
    const SLATE = hex(0x24302b);
    const DUST = hex(0x34423c);
    const dust = sequence(0x6d657373);
    const picture = blank(48, 32);
    for (let y = 0; y < 32; y++) {
      for (let x = 0; x < 48; x++) put(picture, x, y, dust() < 0.08 ? DUST : SLATE);
    }
    const CHALK = {
      w: hex(0xeef0e8), y: hex(0xf4e27a), o: hex(0xf0a050), r: hex(0xf07070), g: hex(0x9ee07a), b: hex(0xc89a6a), p: hex(0xf4a0c8),
      c: hex(0xa8d8f0),
    };
    const BORDER = mix(CHALK.w, SLATE, 0.35);
    for (let x = 2; x <= 45; x++) {
      put(picture, x, 2 + Math.round(Math.sin(x / 1.5)), BORDER);
      put(picture, x, 29 + Math.round(Math.sin(x / 1.5)), BORDER);
    }
    const dishes = [
      { coins: 3, sprite: [ // A burger: bun, lettuce, its patty and the bun under it.
        "..oooo..", ".owooow.", "oooooooo", "gggggggg", "bbbbbbbb", "rrrrrrrr", ".oooooo.",
      ] },
      { coins: 2, sprite: [ // A bowl of noodles, steaming, chopsticks in it.
        ".w..w..b", "..w..wb.", "....wb..", "yyyyyyyy", "wyyyyyyw", ".wwwwww.", "..wwww..",
      ] },
      { coins: 2, sprite: [ // A slice of pizza, pepperoni on it.
        "bbbbbbbb", "yryyyryy", ".yyyryy.", ".yryyy..", "..yyry..", "..yyy...", "...y....",
      ] },
      { coins: 1, sprite: [ // A carrot, its leaves up.
        ".....g.g", "......gg", ".....oo.", "....ooo.", "...oo...", "..oo....", ".o......",
      ] },
      { coins: 2, sprite: [ // A cupcake, a cherry on its frosting, its paper case.
        "...r....", "..pppp..", ".pppppp.", "pppppppp", ".wcwcwc.", ".wcwcwc.", "..wcwc..",
      ] },
      { coins: 1, sprite: [ // A mug of cocoa, steaming.
        "..w..w..", "...w..w.", "........", "wwwwww..", "wbbbbwww", "wbbbbw.w", ".wwww...",
      ] },
    ];
    dishes.forEach((dish, at) => {
      const left = (at % 3) * SIZE;
      const top = Math.floor(at / 3) * SIZE;
      stamp(picture, dish.sprite, CHALK, left + 4, top + (at < 3 ? 4 : 1));
      for (let coin = 0; coin < dish.coins; coin++) {
        stamp(picture, ["yy", "yy"], CHALK, left + 8 - Math.floor((dish.coins * 3 - 1) / 2) + coin * 3, top + (at < 3 ? 12 : 9));
      }
    });
    board("mess", picture);
    // Its small versions, a dish each and the coins it costs, on slate as dusty as the board's: the sandwich board's,
    // ten pixels wide and twelve tall, the burger over its three coins; and the hung board's, twelve wide and nine
    // tall, the noodles and their two coins stacked beside them.
    const slate = (salt) => {
      const speck = sequence(salt);
      const small = blank(SIZE, SIZE);
      for (let y = 0; y < SIZE; y++) {
        for (let x = 0; x < SIZE; x++) put(small, x, y, speck() < 0.08 ? DUST : SLATE);
      }
      return small;
    };
    const stand = slate(0x7374616e);
    stamp(stand, dishes[0].sprite, CHALK, 1, 1);
    for (let coin = 0; coin < dishes[0].coins; coin++) stamp(stand, ["yy", "yy"], CHALK, 1 + coin * 3, 9);
    save(part("mess_stand"), stand);
    const hung = slate(0x68756e67);
    stamp(hung, dishes[1].sprite, CHALK, 1, 1);
    for (let coin = 0; coin < dishes[1].coins; coin++) stamp(hung, ["yy", "yy"], CHALK, 10, 2 + coin * 3);
    save(part("mess_hung"), hung);
  }

  // The whiteboard: a molecule, an arrow and a graph in marker, the ghosts of old sums half wiped off round them, and a
  // yellow sticky note in its corner with a water bear on it.
  {
    const BOARD = hex(0xeef1f4);
    const GHOST = hex(0xdde2e8);
    const BLACK = hex(0x20242a);
    const RED = hex(0xd23a3a);
    const BLUE = hex(0x2a5ad2);
    const GREEN = hex(0x2a9a4a);
    const picture = blank(48, 32, BOARD);
    for (const [x0, y0, length] of [[3, 27, 9], [30, 3, 7], [20, 28, 6], [36, 28, 8], [5, 4, 5]]) {
      for (let x = x0; x < x0 + length; x++) put(picture, x, y0 + (x % 3 === 0 ? 1 : 0), GHOST);
    }
    // A line of marker from one pixel to another.
    const line = (x0, y0, x1, y1, colour) => {
      const steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
      for (let step = 0; step <= steps; step++) {
        put(picture, Math.round(x0 + ((x1 - x0) * step) / steps), Math.round(y0 + ((y1 - y0) * step) / steps), colour);
      }
    };
    // The molecule: a ring of six atoms joined by bonds, one of them doubled, a red atom and a blue one off it.
    const ring = [[12, 9], [17, 12], [17, 18], [12, 21], [7, 18], [7, 12]];
    ring.forEach(([x, y], at) => line(x, y, ...ring[(at + 1) % ring.length], BLACK));
    line(9, 13, 9, 17, BLACK);
    line(17, 12, 21, 9, BLACK);
    line(7, 18, 4, 22, BLACK);
    const atom = [".k.", "kck", ".k."];
    ring.forEach(([x, y]) => stamp(picture, atom, { k: BLACK, c: hex(0x60666e) }, x - 1, y - 1));
    stamp(picture, [".rr.", "rRrr", "rrrr", ".rr."], { r: RED, R: hex(0xff9a9a) }, 20, 6);
    stamp(picture, [".bb.", "bBbb", "bbbb", ".bb."], { b: BLUE, B: hex(0x9abaff) }, 2, 21);
    // The arrow over to the graph.
    line(22, 15, 27, 15, GREEN);
    stamp(picture, ["g.", "gg", "g."], { g: GREEN }, 27, 14);
    // The graph: its axes, a dashed line to reach, the points measured and the curve climbing through them past it.
    line(31, 4, 31, 26, BLACK);
    line(31, 26, 45, 26, BLACK);
    stamp(picture, [".k.", "k.k"], { k: BLACK }, 30, 3);
    stamp(picture, ["k.", ".k", "k."], { k: BLACK }, 45, 25);
    for (let x = 33; x <= 44; x += 3) line(x, 11, x + 1, 11, GREEN);
    const curve = (x) => Math.round(25 - 18 * ((x - 32) / 12) ** 2);
    for (let x = 32; x < 44; x++) line(x, curve(x), x + 1, curve(x + 1), RED);
    for (const [x, y] of [[34, 22], [37, 24], [40, 15], [42, 13]]) stamp(picture, ["bb", "bb"], { b: BLUE }, x, y);
    // The sticky note and its water bear, plump and peach, its four legs under it.
    stamp(picture, [
      "nnnnnnn",
      "nnnnnnn",
      "n.ppp.n",
      "npppppn",
      "pppppen",
      "nlnlnln",
      "nnnnnnn",
    ].map((row) => row.replace(/\./g, "n")), { n: hex(0xffe86a), p: hex(0xf0b896), e: BLACK, l: hex(0xc88a6a) }, 1, 1);
    board("lab", picture);
    // Its small version, a whole board twelve pixels wide and eight tall for the mobile whiteboard, its front in the
    // texture's top half: the molecule's ring with its red atom and its blue one, and the graph's curve climbing past
    // its dashed line; its back, in the bottom half, wiped clean but for the ghosts of old sums.
    const small = blank(SIZE, SIZE, BOARD);
    stamp(small, [
      ".....Rrk...r",
      "....krrk...r",
      ".kkk...kggrg",
      "k...k..k..r.",
      "k...k..k.r..",
      ".kkk...kr...",
      "..k....kkkkk",
      ".Bb.........",
    ], { k: BLACK, r: RED, R: hex(0xff9a9a), g: GREEN, b: BLUE, B: hex(0x9abaff) }, 0, 0);
    for (const [x0, y0, length] of [[1, 9, 5], [7, 11, 4], [2, 13, 6], [8, 14, 3]]) {
      for (let x = x0; x < x0 + length; x++) put(small, x, y0 + (x % 3 === 0 ? 1 : 0), GHOST);
    }
    save(part("lab_small"), small);
  }

  // The ivy, in two layers: its stems and the leaves along them behind, and more leaves a pixel out in front of them.
  // Its columns climb up the corner girders, the left one drawn here and the right one its mirror; along the top it
  // trails across the glass between them, strands hanging from it into the row under it, in the same places as the
  // strands of the row under it go on from.
  {
    const DARK = hex(0x1e4e26);
    const MID = hex(0x2e7234);
    const LIGHT = hex(0x4c9a44);
    const VEIN = hex(0x9cc87a);
    const WOOD = hex(0x5c4a2c);
    const STEM = hex(0x3a5a28);
    const LEAF = { d: DARK, m: MID, l: LIGHT, v: VEIN, s: STEM };
    const LEAVES = [
      ["..l..", ".mlm.", "mmlmm", ".dvd.", "..s.."],
      [".l.l.", "mmlmm", "mmvmm", ".dvd.", "..s.."],
      [".m.m.", "mlmlm", ".mvm.", "..d.."],
    ];
    // Where a column's two stems are across its block at each row, wavering, the same at its top as at its bottom, so
    // its rows run on into one another; and where the trailing stem along the top is down its block at each column.
    const climbing = (stem, v) => [4, 10][stem] + Math.round(Math.sin((2 * Math.PI * v) / SIZE + stem * Math.PI));
    const trailing = (u) => 4 + Math.round(1.2 * Math.sin((2 * Math.PI * u) / SIZE));
    // A layer of ivy: what the stems take, and the leaves, at each place a leaf grows, one of them, upright, hanging or
    // turned over, chosen by the sequence.
    const layer = (stems, places, salt, hanging) => {
      const random = sequence(salt);
      const picture = blank(SIZE, SIZE);
      stems(picture);
      for (const [x, y] of places) {
        const leaf = LEAVES[Math.floor(random() * LEAVES.length)];
        const rows = hanging || random() < 0.25 ? [...leaf].reverse() : leaf;
        stamp(picture, rows, LEAF, x - 2, y - 2, random() < 0.5);
      }
      return picture;
    };
    const climb = (picture, from, to) => {
      for (let v = from; v <= to; v++) {
        for (let stem = 0; stem < 2; stem++) put(picture, climbing(stem, v), v, STEM);
      }
    };
    // Where a column's leaves grow, so many of them up its block, by turns off either stem, a little to one side of it.
    const columnLeaves = (salt, count) => {
      const random = sequence(salt);
      return Array.from({ length: count }, (_, k) => {
        const v = Math.round(1 + (k * SIZE) / count + random() * 2);
        return [climbing(k % 2, v) + (random() < 0.5 ? -2 : 2), v];
      });
    };
    const saveLayers = (name, back, front) => {
      save(part(`ivy_${name}`), back);
      save(part(`ivy_${name}_front`), front);
    };
    // The middle of a column, its stems all the way up; its foot, woody where they rise from the floor; its top, where
    // they end, one curling and the other turning in along the top to run on into the trailing stem.
    saveLayers("column_middle", layer((p) => climb(p, 0, 15), columnLeaves(1, 6), 11), layer(() => {}, columnLeaves(2, 4), 12));
    saveLayers("column_bottom", layer((p) => {
      climb(p, 0, 9);
      for (let v = 10; v <= 15; v++) {
        put(p, 6 + Math.round((v - 10) / 3), v, WOOD);
        put(p, 7 + Math.round((v - 10) / 3), v, WOOD);
        put(p, climbing(0, v), v, v < 13 ? STEM : WOOD);
        put(p, climbing(1, v), v, v < 13 ? STEM : WOOD);
      }
    }, columnLeaves(3, 6).slice(0, 4), 13), layer(() => {}, columnLeaves(4, 4).slice(0, 2), 14));
    saveLayers("column_top", layer((p) => {
      climb(p, 6, 15);
      for (let u = climbing(1, 6); u < SIZE; u++) put(p, u, trailing(SIZE), STEM);
      for (let v = trailing(SIZE); v < 6; v++) put(p, climbing(1, 6), v, STEM);
      stamp(p, ["ss.", "s.s", "..s"], { s: STEM }, climbing(0, 6) - 1, 3);
    }, [[climbing(0, 9) - 2, 9], [climbing(1, 12) + 2, 12], [12, 1], [3, 7]], 15), layer(() => {}, [[climbing(0, 13) + 2, 13], [9, 6]], 16));
    // The trailing stem along the top and the strands hanging from it, by where across each block they hang and how
    // far down from it they reach, many running on into the row under it; and that row, the strands going on down.
    const STRANDS = { left: [[3, 20], [11, 14]], middle: [[7, 26], [13, 9]], right: [[5, 16], [12, 22]] };
    for (const [along, strands] of Object.entries(STRANDS)) {
      const salt = along.length * 31;
      const hangingLeaves = (top, bottom, offset) => strands.flatMap(([x, length]) => {
        const end = trailing(x) + length;
        const leaves = [];
        for (let y = trailing(x) + 3 + offset; y <= Math.min(end, bottom); y += 4) {
          if (y >= top) leaves.push([x + (y % 8 < 4 ? -2 : 2), y - top]);
        }
        return leaves;
      });
      const strandStems = (top, bottom) => (p) => {
        for (const [x, length] of strands) {
          for (let y = Math.max(top, trailing(x)); y <= Math.min(bottom, trailing(x) + length); y++) put(p, x, y - top, STEM);
        }
      };
      const garland = layer((p) => {
        strandStems(0, 15)(p);
        for (let u = 0; u < SIZE; u++) put(p, u, trailing(u), STEM);
      }, [[2, trailing(2) - 2], [7, trailing(7) + 2], [12, trailing(12) - 2], ...hangingLeaves(0, 15, 0)], salt + 1);
      const garlandFront = layer(() => {}, [[5, trailing(5)], [10, trailing(10) - 1], [14, trailing(14) + 1]], salt + 2);
      saveLayers(`garland_${along}`, garland, garlandFront);
      saveLayers(`trail_${along}`, layer(strandStems(16, 31), hangingLeaves(16, 31, 0), salt + 3, true),
        layer(() => {}, hangingLeaves(16, 31, 2), salt + 4, true));
    }
  }

  // The item: a window between velvet curtains drawn back to either side with gold, the valance over them.
  outlinedIcon("shaft_decor", (x, y) => {
    const rows = [
      "gggggggggggggg",
      "VVVVVVVVVVVVVV",
      "VvVvVvVvVvVvVv",
      "VvVvVxxxxvVvVv",
      "VvVvxxxxxxvVvV",
      "VvVxxxxxxxxvVv",
      "VvVxxxxxxxxvVv",
      "VvxxxxxxxxxxvV",
      "GGxxxxxxxxxxGG",
      "Vvxxxxxxxxxxvv",
      "VvVxxxxxxxxvVv",
      "VvVvxxxxxxvVvV",
      "VvVvVxxxxvVvVv",
    ];
    const palette = { g: GOLD_BRAID, G: GOLD_LIGHT, V: VELVET_MID, v: VELVET, x: [0x1c, 0x24, 0x30] };
    return y >= 2 && y < 2 + rows.length && x >= 1 && x < 15 ? palette[rows[y - 2][x - 1]] : undefined;
  }, VELVET_DEEP, () => undefined);
}
shaftDecorTextures();

// The hands-on controls on the rocket's centre console (see RocketControlBlock and its kinds), each drawn as the pilots
// see it from behind the console. The boost button's cap, the lamp it is, seen from above: a round lens, brightest at
// its middle and darker to its rim, the corners outside the round darker still, so the square cap reads as round; unlit
// orange while it cannot boost, green ready, red resting, and pulsing from orange to yellow while it boosts, its sides
// the cap's colour alone; at 32 pixels, so the round stays smooth. Lamps blue and orange as the jump light's lenses.
// The yellow-and-black hazard stripes painted round the stability switch, its yellow lid, a darker rim round it, and
// the abort handle's red grip, ridged across. The flight display's pictures of the controls, white to be tinted (see
// FlightDisplayRenderer): the boost's flame, a rocket with her side jets puffing, a warning triangle, the arc and the
// snail, the lift's own arrow standing for straight up. And each module's picture plate, the same on every module so
// the console reads as one: in the top half of a 64-pixel texture, a dark glass in a frame, the control's picture on it
// glowing in its colour, or unlit grey while it waits. The flame for the boost button; the rocket with her jets puffing
// for the stability switch, flashing to the warning triangle while it is off; the arrow up, the arc and the snail in a
// row for the flight mode knob, the one it points at lit; a U-turn arrow for the abort handle; and a rocket over her
// flame for the launch key.
function rocketControlTextures() {
  const BIG = 32;
  const CAP_FRAMES = 8;
  const PLATE = 64;
  const GREEN = [0x46, 0xe0, 0x70];
  const ORANGE = [0xff, 0x9a, 0x2a];
  const RED = [0xff, 0x44, 0x34];
  const AMBER = [0xf0, 0xa8, 0x30];
  const BLUE = [0x4c, 0x9a, 0xff];
  const capTop = (base) => (x, y) => {
    const out = Math.hypot(x - 15.5, y - 15.5) / 15.5;
    const shade = out > 1 ? 0.6 : 1.2 - 0.35 * out;
    return base.map((c) => clamp(Math.round(c * shade)));
  };
  const CAPS = { dark: [0xd8, 0x60, 0x1c], ready: [0x2a, 0xb8, 0x4a], resting: [0xc4, 0x22, 0x1a] };
  for (const [look, base] of Object.entries(CAPS)) {
    texture(blockOutput, `rocket_boost_cap_${look}`, capTop(base), 2, BIG, BIG);
    texture(blockOutput, `rocket_boost_cap_side_${look}`, (x, y) => base.map((c) => Math.round(c * (y === 0 ? 1.15 : 1))).map(clamp), 2);
  }
  // Boosting, pulsing: orange to yellow and back over its frames.
  const pulse = (frame) => (1 - Math.cos((2 * Math.PI * frame) / CAP_FRAMES)) / 2;
  const boostingBase = (frame) => [0xff, Math.round(0x70 + 0x60 * pulse(frame)), Math.round(0x10 + 0x40 * pulse(frame))];
  texture(blockOutput, "rocket_boost_cap_boosting", (x, y) => capTop(boostingBase(Math.floor(y / BIG)))(x, y % BIG), 0, BIG, BIG * CAP_FRAMES);
  writeFileSync(resolve(blockOutput, "rocket_boost_cap_boosting.png.mcmeta"), JSON.stringify({ animation: { frametime: 2 } }, null, 2) + "\n");
  texture(blockOutput, "rocket_boost_cap_side_boosting", (x, y) => boostingBase(Math.floor(y / SIZE)), 0, SIZE, SIZE * CAP_FRAMES);
  writeFileSync(resolve(blockOutput, "rocket_boost_cap_side_boosting.png.mcmeta"), JSON.stringify({ animation: { frametime: 2 } }, null, 2) + "\n");

  const lens = (colour) => (x, y) => {
    if (x === 0 || y === 0 || x === 15 || y === 15) return FRAME_DARK;
    const glow = Math.max(0, 1 - Math.hypot(x - 7.5, y - 7.5) / 8);
    return colour.map((c) => clamp(Math.round(c + 70 * glow)));
  };
  texture(blockOutput, "rocket_lamp_blue", lens([0x30, 0x70, 0xe8]));
  texture(blockOutput, "rocket_lamp_orange", lens([0xf0, 0x70, 0x18]));

  const HAZARD_YELLOW = [0xf2, 0xc0, 0x1a];
  const HAZARD_BLACK = [0x1c, 0x1d, 0x20];
  texture(blockOutput, "rocket_hazard_stripes", (x, y) => ((x + y) % 8 < 4 ? HAZARD_YELLOW : HAZARD_BLACK), 2);
  const LID_YELLOW = [0xf2, 0xc0, 0x1a];
  texture(blockOutput, "rocket_switch_lid", (x, y) => LID_YELLOW.map((c) => Math.round(c * (x === 0 || y === 0 || x === 15 || y === 15 ? 0.75 : 1))), 3);
  const GRIP_RED = [0xd0, 0x2a, 0x22];
  texture(blockOutput, "rocket_abort_grip", (x, y) => GRIP_RED.map((c) => Math.round(c * (y % 4 === 3 ? 0.65 : 1))), 3);

  const controlsOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/gui/rocket_controls");
  mkdirSync(controlsOutput, { recursive: true });
  const ICONS = {
    flame: ["................", ".......#........", ".......##.......", "......###.......", "......####......", ".....#####..#...",
      ".....######.##..", "....##########..", "....##########..", "...###########..", "...####..#####..", "...###....####..",
      "....##....###...", ".....##..###....", "......#####.....", "................"],
    steady: ["................", ".......##.......", "......####......", "......####......", ".##...####...##.", "###...####...###",
      ".##...####...##.", "......####......", "......####......", "......####......", ".....######.....", ".##.########.##.",
      "###.###..###.###", ".##.##....##.##.", "................", "................"],
    warning: ["................", ".......##.......", "......####......", "......####......", ".....##..##.....", ".....##..##.....",
      "....###..###....", "....###..###....", "...####..####...", "...##########...", "..#####..#####..", "..############..",
      ".##############.", "................", "................", "................"],
    arc: ["................", "............###.", "..........######", ".........###.##.", "........##......", ".......##.......",
      "......##........", ".....##.........", ".....##.........", "....##..........", "....##..........", "...##...........",
      "...##...........", "...##...........", "..##............", "................"],
    gentle: ["................", "................", "................", ".#..#...........", ".#..#....####...", ".#..#...#....#..",
      ".####..#..##..#.", ".####..#.#..#.#.", ".####..#.#.##.#.", ".####..#..#...#.", ".####...#....#..", "#######..####...",
      "################", "................", "................", "................"],
  };
  for (const [name, rows] of Object.entries(ICONS)) {
    texture(controlsOutput, name, (x, y) => (rows[y][x] === "#" ? [0xff, 0xff, 0xff, 0xff] : [0, 0, 0, 0]), 0);
  }
  // The pictures only the plates show: the abort handle's U-turn, up its right, over the top and down its left to a
  // point, and the launch key's rocket over her flame.
  const PLATE_ICONS = {
    uturn: ["................", "................", ".....######.....", "....########....", "...###....###...", "...##......##...",
      "...##......##...", "...##......##...", ".######....##...", "..####.....##...", "...##......##...", "...........##...",
      "...........##...", "...........##...", "................", "................"],
    launch: ["................", ".......##.......", "......####......", ".....######.....", ".....##..##.....", ".....######.....",
      ".....######.....", "....########....", "...###.##.###...", "...##..##..##...", "................", "......####......",
      "......####......", ".......##.......", ".......##.......", "................"],
  };
  // A plate's frame: its pictures side by side, each its rows and its colour (unlit without one), one picture at twice
  // the size of a row of them.
  const GLASS = [0x0e, 0x10, 0x14];
  const UNLIT = [0x4a, 0x4e, 0x56];
  const plate = (pictures) => {
    const scale = pictures.length === 1 ? 2 : 1;
    const step = PLATE / pictures.length;
    const top = (PLATE / 2 - 16 * scale) / 2;
    return (x, y) => {
      if (y >= PLATE / 2) return GLASS;
      if (x < 2 || y < 2 || x >= PLATE - 2 || y >= PLATE / 2 - 2) return FRAME_DARK;
      const k = Math.floor(x / step);
      const [rows, colour] = pictures[k];
      const u = Math.floor((x - Math.round(k * step + (step - 16 * scale) / 2)) / scale);
      const v = Math.floor((y - top) / scale);
      return u >= 0 && u < 16 && v >= 0 && v < 16 && rows[v][u] === "#" ? colour ?? UNLIT : GLASS;
    };
  };
  // Each plate by name, its frames, more than one flashing between them.
  const PLATES = {
    boost_dark: [[[ICONS.flame]]],
    boost_ready: [[[ICONS.flame, GREEN]]],
    boost_boosting: [[[ICONS.flame, ORANGE]]],
    boost_resting: [[[ICONS.flame, RED]]],
    stability_holding: [[[ICONS.steady, GREEN]]],
    stability_off: [[[ICONS.steady, RED]], [[ICONS.warning, RED]]],
    stability_locked: [[[ICONS.steady, AMBER]]],
    stability_refused: [[[ICONS.steady, RED]]],
    abort_dark: [[[PLATE_ICONS.uturn]]],
    abort_lit: [[[PLATE_ICONS.uturn, RED]]],
    key_off: [[[PLATE_ICONS.launch, AMBER]]],
    key_on: [[[PLATE_ICONS.launch, GREEN]]],
  };
  const MODES = { vertical: [LIFT_ICONS.up, BLUE], arc: [ICONS.arc, ORANGE], gentle: [ICONS.gentle, GREEN] };
  for (const mode of Object.keys(MODES)) {
    for (const refused of [false, true]) {
      const row = Object.entries(MODES).map(([each, [rows, colour]]) => [rows, each !== mode ? undefined : refused ? RED : colour]);
      PLATES[`mode_${mode}${refused ? "_refused" : ""}`] = [row];
    }
  }
  for (const [name, frames] of Object.entries(PLATES)) {
    const paints = frames.map(plate);
    texture(blockOutput, `rocket_plate_${name}`, (x, y) => paints[Math.floor(y / PLATE)](x, y % PLATE), 0, PLATE, PLATE * frames.length);
    if (frames.length > 1) {
      writeFileSync(resolve(blockOutput, `rocket_plate_${name}.png.mcmeta`), JSON.stringify({ animation: { frametime: 6 } }, null, 2) + "\n");
    }
  }
}
rocketControlTextures();

// Item icons of what is drawn live or by its renderer, or too faint to see, whose block models show too little to know
// them by: the LED strip, a bar of little lights running through her colours; a story's LED panel, its swatches over
// its suns; the cockpit's LED console, her stories' bands over its swatches; a lift's picture board, its floors two to
// a row; the orrery, a sun on a brass rod from its rose, a planet either side of it; and a tank's glass, a pale pane on
// its steel kerb, a glint across it.
{
  const SWATCHES = [[0xff, 0x30, 0x40], [0xff, 0x8a, 0x20], [0xff, 0xd8, 0x40], [0x80, 0xff, 0x50], [0x30, 0xe0, 0xa0], [0x40, 0xc8, 0xff],
    [0x40, 0x70, 0xff], [0xa0, 0x50, 0xff], [0xff, 0x60, 0xc0], [0xf0, 0xf4, 0xff]];
  const CASING = [0x15, 0x17, 0x1c];
  const GLOSS = [0x09, 0x0a, 0x0d];
  const swatch = (at, of) => SWATCHES[Math.floor((at * SWATCHES.length) / of) % SWATCHES.length];
  texture(itemOutput, "led_strip", (x, y) => {
    if (y < 6 || y > 9) return CLEAR;
    if (y === 6 || y === 9) return CASING;
    return x % 2 === 0 ? swatch(x, 16) : swatch(x, 16).map((value) => Math.round(value * 0.75));
  }, 0);
  texture(itemOutput, "led_panel", (x, y) => {
    if (x < 1 || x > 14 || y < 1 || y > 14) return CLEAR;
    if (x === 1 || x === 14 || y === 1 || y === 14) return CASING;
    if (y >= 3 && y <= 5 && x >= 3 && x <= 12) return swatch(x - 3, 10);
    if (y >= 7 && y <= 8 && x >= 3 && x <= 12) return swatch(x - 3 + 5, 10);
    if (y >= 10 && y <= 12 && (x === 4 || x === 7 || x === 8 || x === 11)) return [0xf0, 0xf4, 0xff];
    return GLOSS;
  }, 0);
  texture(itemOutput, "led_console", (x, y) => {
    if (y < 3 || y > 12) return CLEAR;
    if (x === 0 || x === 15 || y === 3 || y === 12) return CASING;
    if (y >= 5 && y <= 7 && x >= 2 && x <= 13) return swatch(x - 2, 12);
    if (y >= 9 && y <= 10 && x >= 2 && x <= 13 && x % 2 === 0) return swatch(x - 2 + 3, 12);
    return GLOSS;
  }, 0);
  // A story's window dimmer: its four oval windows two over two in their steel rims, clear and light over medium and
  // dark, the sky through each dimming as LedControlsArt draws it (WINDOW_SKY scaled by WINDOW_LIGHT), the sun high on
  // the right of each.
  const DIMMER_SKY = [0x8c, 0xcb, 0xf0];
  const DIMMER_SUN = [0xff, 0xf1, 0xb8];
  const DIMMER_RIM = [0x8f, 0x96, 0xa1];
  const DIMMER_LIGHT = [1, 0.55, 0.3, 0.1];
  const dimmerWindow = (x, y) => {
    const column = x < 8 ? 0 : 1;
    const row = y < 8 ? 0 : 1;
    const dx = x + 0.5 - (column === 0 ? 5 : 11);
    const dy = y + 0.5 - (row === 0 ? 4.5 : 11.5);
    const out = Math.hypot(dx / 2.1, dy / 2.6);
    if (out > 1) return null;
    const light = DIMMER_LIGHT[row * 2 + column];
    if (out > 0.62) return DIMMER_RIM;
    const sun = dx > 0 && dy < 0;
    return (sun ? DIMMER_SUN : DIMMER_SKY).map((value) => Math.round(value * light));
  };
  texture(itemOutput, "window_dimmer", (x, y) => {
    if (x < 1 || x > 14 || y < 1 || y > 14) return CLEAR;
    if (x === 1 || x === 14 || y === 1 || y === 14) return CASING;
    return dimmerWindow(x, y) ?? GLOSS;
  }, 0);
  texture(itemOutput, "lift_board", (x, y) => {
    if (x < 3 || x > 12) return CLEAR;
    if (x === 3 || x === 12 || y === 0 || y === 15) return CASING;
    const across = Math.floor((x - 4) / 4);
    const down = Math.floor((y - 1) / 3);
    const inButton = (x - 4) % 4 < 3 && (y - 1) % 3 < 2 && down < 5;
    return inButton ? swatch(down * 2 + across, 10) : GLOSS;
  }, 0);
  texture(itemOutput, "habitat_glass", (x, y) => {
    if (x < 2 || x > 13 || y < 1) return CLEAR;
    if (y >= 13) return [0x6a, 0x71, 0x79];
    if (x === 2 || x === 13 || y === 1) return [0xa8, 0xc8, 0xd8];
    return Math.abs(x - 12 + y) < 2 || Math.abs(x - 6 + y) < 1 ? [0xe8, 0xf6, 0xff] : [0xb8, 0xdc, 0xec];
  }, 0);
  const BRASS = [0xc8, 0x98, 0x40];
  const BRASS_DARK = [0x7a, 0x58, 0x20];
  texture(itemOutput, "orrery", (x, y) => {
    if (y <= 1 && x >= 5 && x <= 10) return y === 0 ? BRASS : BRASS_DARK;
    if (Math.hypot(x - 7.5, y - 8.5) < 3) return [0xff, 0xd8, 0x60];
    if (x >= 7 && x <= 8 && y <= 6) return BRASS;
    if (y === 8 && (x <= 4 || x >= 11)) return BRASS_DARK;
    if (Math.hypot(x - 1.5, y - 8.5) < 1.6) return [0x60, 0xa0, 0xff];
    if (Math.hypot(x - 14, y - 8.5) < 1.6) return [0xd0, 0x60, 0x50];
    return CLEAR;
  }, 0);
}
// The round floor window (see FloorWindowBlock): its item's picture, a steel ring round faintly blue glass with a glint
// across it, set into a square of warm floor; its panel's sloping face, brushed steel round a big round button painted
// with the window, a dark eye looking down through its glass, the lamp round the button green while the window is open
// and amber while it is shut; and the panel's steel post.
function floorWindowTextures() {
  const STEEL = [0x9a, 0xa1, 0xa8];
  const STEEL_DARK = [0x5c, 0x63, 0x6b];
  const GLASS = [0xa8, 0xd8, 0xf0];
  const GLINT = [0xe8, 0xf8, 0xff];
  const FLOOR = [0xc8, 0xa8, 0x78];
  texture(itemOutput, "floor_window", (x, y) => {
    const out = Math.hypot(x - 7.5, y - 7.5);
    if (out > 7.2) return FLOOR;
    if (out > 6) return out > 6.8 ? STEEL_DARK : STEEL;
    return Math.abs(x - y) < 1.2 && x > 3 && x < 10 ? GLINT : GLASS;
  }, 0);
  const face = (lamp) => (x, y) => {
    const out = Math.hypot(x - 7.5, y - 7.5);
    if (out > 6.6) return (x + y) % 5 === 0 ? STEEL_DARK : STEEL;
    if (out > 5.4) return lamp;
    if (out > 4.6) return STEEL_DARK;
    const eye = Math.abs(y - 8) < 1.6 - Math.abs(x - 7.5) * 0.35;
    if (eye) return Math.hypot(x - 7.5, y - 8) < 1 ? [0x14, 0x18, 0x22] : [0xf4, 0xf6, 0xfa];
    return Math.abs(x - y) < 1 && x < 6 ? GLINT : GLASS;
  };
  texture(blockOutput, "floor_window_panel_open", face([0x40, 0xf0, 0x70]), 0);
  texture(blockOutput, "floor_window_panel_shut", face([0xf8, 0xa8, 0x28]), 0);
  texture(blockOutput, "floor_window_panel_post", (x) => (x % 4 === 0 ? STEEL_DARK : STEEL), 2);
}
floorWindowTextures();

// The freighter's beach in her lounge (see the beach in generate_resources.mjs): warm sand with the odd pale shell; a
// palm's ringed trunk, its pot of terracotta with a lighter rim, and a frond, a midrib out from the trunk at its left
// edge and leaflets either side of it, tapering to its tip, clear between them, and one crossing over the others,
// tapering to both its ends; a sun lounger's cushion, striped turquoise and white along it; a parasol's canopy, red and
// white stripes, paler underneath; and a tiki torch's flame, glowing yellow at its heart to orange at its edge.
function beachTextures() {
  texture(blockOutput, "beach_sand", (x, y) => {
    if ((x * 7 + y * 13) % 41 === 0) return [0xf8, 0xe8, 0xe0];
    return (x * 3 + y * 5) % 11 === 0 ? [0xd8, 0xbc, 0x84] : [0xe8, 0xd2, 0x9c];
  }, 6);
  texture(blockOutput, "palm_trunk", (x, y) => (y % 3 === 0 ? [0x6a, 0x48, 0x2a] : x === 0 || x === 15 ? [0x7a, 0x56, 0x34] : [0x9a, 0x72, 0x48]), 4);
  texture(blockOutput, "palm_pot", () => [0xb8, 0x5c, 0x38], 4);
  texture(blockOutput, "palm_pot_rim", () => [0xd0, 0x76, 0x4c], 3);
  texture(blockOutput, "palm_frond", (x, y) => {
    const off = Math.abs(y - 7.5);
    const reach = 7.5 * (1 - x / 17);
    if (off < 0.8) return [0x4a, 0x7a, 0x28];
    if (off > reach || (x + Math.floor(off)) % 3 === 0) return CLEAR;
    return off > reach - 1.2 ? [0x3c, 0x8c, 0x30] : [0x58, 0xb0, 0x3c];
  }, 0);
  texture(blockOutput, "palm_frond_crossing", (x, y) => {
    const off = Math.abs(y - 7.5);
    const reach = 7 * (1 - Math.abs(x - 7.5) / 8.5);
    if (off < 0.8) return [0x4a, 0x7a, 0x28];
    if (off > reach || (Math.abs(x - 7.5) + off) % 3 < 1) return CLEAR;
    return off > reach - 1.2 ? [0x3c, 0x8c, 0x30] : [0x58, 0xb0, 0x3c];
  }, 0);
  texture(blockOutput, "lounger_cushion", (x) => (Math.floor(x / 2) % 2 ? [0xf4, 0xf6, 0xf2] : [0x30, 0xc4, 0xbc]), 2);
  texture(blockOutput, "parasol_canopy", (x, y) => (Math.floor((x + y) / 3) % 2 ? [0xf4, 0xf0, 0xe8] : [0xe0, 0x3c, 0x34]), 2);
  texture(blockOutput, "parasol_underside", (x, y) => (Math.floor((x + y) / 3) % 2 ? [0xf8, 0xf4, 0xec] : [0xf0, 0x80, 0x70]), 1);
  texture(blockOutput, "tiki_flame", (x, y) => {
    const out = Math.hypot(x - 7.5, y - 7.5);
    return out < 3 ? [0xff, 0xf0, 0x80] : out < 5.5 ? [0xff, 0xb8, 0x30] : [0xf0, 0x70, 0x18];
  }, 0);
}
beachTextures();

// The freighter's cockpit console (see ConsoleCasing and ConsoleArt), our own design: its casing worn dark gunmetal,
// scuffed, a panel seam near its top and its foot and a screw head in two corners; its desk top a darker panel ruled
// into squares by fine lighter lines, as of plates bolted down, one scratched; each module's screen as its item shows
// it, a little picture of what it shows on dark glass; and the pictures its screens and plates are drawn with, white to
// be tinted, so none needs reading: chevrons for speed, a peak and an arrow up for height, an arrow up for a climb,
// three arrows up for her lift, a flame for her mains, a fat double chevron for the boost, a starburst of streaks out
// from a bright middle for the hyperspace jump, and the power sign.
function consoleTextures() {
  const GUNMETAL = [0x3c, 0x41, 0x47];
  texture(blockOutput, "console_casing", (x, y) => {
    if (y === 1 || y === 14) return [0x2a, 0x2e, 0x33];
    if ((x === 2 && y === 3) || (x === 13 && y === 12)) return [0x74, 0x7a, 0x82];
    const scuff = (x * 7 + y * 3) % 23 === 0 ? 10 : 0;
    return GUNMETAL.map((c) => c + scuff);
  }, 5);
  texture(blockOutput, "console_deck", (x, y) => {
    if (x % 8 === 0 || y % 8 === 0) return [0x3e, 0x43, 0x4a];
    if (x === y + 3 && x > 9) return [0x48, 0x4e, 0x55];
    return [0x26, 0x29, 0x2e];
  }, 3);
  const GLASS = [0x06, 0x10, 0x0c];
  const GREEN = [0x4c, 0xf0, 0x8a];
  const AMBER = [0xff, 0xb2, 0x38];
  const RED = [0xff, 0x4a, 0x3a];
  const CYAN = [0x56, 0xd8, 0xff];
  // The climb boost's picture: an arrow pointing straight up over three tongues of flame.
  const CLIMB_ROWS = [
    "................",
    ".......##.......",
    "......####......",
    ".....######.....",
    "....########....",
    "...##########...",
    "......####......",
    "......####......",
    "......####......",
    "......####......",
    "................",
    "...#...##...#...",
    "..##..####..##..",
    "..###.####.###..",
    "...##########...",
    "................",
  ];
  const PICTURES = {
    readouts: (x, y) => (y === 5 && x > 2 && x < 13) || (y > 8 && y < 12 && x > 2 && x < 3 + (x % 3) * 3) ? GREEN : null,
    scanner: (x, y) => {
      const out = Math.hypot(x - 7.5, y - 7.5);
      if (Math.abs(out - 5.5) < 0.6 || (out < 5.5 && Math.abs(x - y) < 0.7 && x > 7)) return GREEN;
      return (x === 5 && y === 6) || (x === 10 && y === 9) ? AMBER : null;
    },
    engines: (x, y) => (x % 4 === 2 && y > 13 - [7, 10, 4, 8][Math.floor(x / 4)] && y < 14 ? [CYAN, AMBER, RED, GREEN][Math.floor(x / 4)] : null),
    switches: (x, y) => (x % 3 === 1 && y > 9 && y < 13 ? [0xb0, 0xb6, 0xbe] : x % 3 === 1 && y === 5 ? [GREEN, AMBER, RED, CYAN, GREEN][Math.floor(x / 3)] : null),
    boost: (x, y) => (Math.hypot(x - 7.5, y - 7.5) < 5 ? RED : Math.hypot(x - 7.5, y - 7.5) < 6 ? [0xf2, 0xc0, 0x1a] : null),
    jump: (x, y) => {
      const out = Math.hypot(x - 7.5, y - 7.5);
      if (out < 1.6) return [0xea, 0xf6, 0xff];
      return out < 6.5 && (Math.abs(x - 7.5) < 0.6 || Math.abs(y - 7.5) < 0.6 || Math.abs(Math.abs(x - 7.5) - Math.abs(y - 7.5)) < 0.6) ? CYAN : null;
    },
    climb: (x, y) => (CLIMB_ROWS[y][x] !== "#" ? null : y < 10 ? [0xff, 0x8a, 0x1e] : y < 13 ? RED : AMBER),
  };
  for (const [kind, paint] of Object.entries(PICTURES)) {
    texture(blockOutput, `console_${kind}_screen`, (x, y) => {
      if (x === 0 || y === 0 || x === 15 || y === 15) return GUNMETAL;
      return paint(x, y) ?? GLASS;
    }, 1);
  }
  // Her take-off held by someone on her ramp: her hull's edge at the left, her ramp sloping down from under it to the
  // ground at the right, and someone standing on it, their arms out.
  const HELD_RAMP_PERSON = [[8, 2], [9, 2], [8, 3], [9, 3], [7, 4], [8, 4], [9, 4], [10, 4], [6, 5], [8, 5], [9, 5], [11, 5], [6, 6], [8, 6],
    [9, 6], [11, 6], [8, 7], [9, 7], [8, 8], [9, 8], [7, 9], [10, 9], [7, 10], [10, 10], [10, 11], [10, 12]];
  const HELD_RAMP_ROWS = Array.from({ length: SIZE }, (_, y) => Array.from({ length: SIZE }, (_, x) => {
    const rampTop = 9 + Math.floor(x / 2.5);
    const ramp = y >= rampTop && y <= rampTop + 1;
    const hull = x <= 1 && y >= 4 && y <= 8;
    return ramp || hull || HELD_RAMP_PERSON.some(([px, py]) => px === x && py === y) ? "#" : ".";
  }).join(""));
  const iconsOutput = resolve(ROOT, "src/main/resources/assets/skyliners/textures/gui/console");
  mkdirSync(iconsOutput, { recursive: true });
  const ICONS = {
    speed: ["................", "................", "..##.....##.....", "...##.....##....", "....##.....##...", ".....##.....##..",
      "......##.....##.", ".......##.....##", ".......##.....##", "......##.....##.", ".....##.....##..", "....##.....##...",
      "...##.....##....", "..##.....##.....", "................", "................"],
    height: ["................", "...........#....", "..........###...", ".........#####..", "...........#....", "....#......#....",
      "...###.....#....", "..#####.........", ".#######........", "#########.......", "##########......", "###########.....",
      "############....", "#############...", "################", "................"],
    climb: ["................", ".......##.......", "......####......", ".....######.....", "....########....", "...##########...",
      "..############..", ".......##.......", ".......##.......", ".......##.......", ".......##.......", ".......##.......",
      ".......##.......", ".......##.......", "................", "................"],
    lift: ["................", "..#....#....#...", ".###..###..###..", "..#....#....#...", "..#....#....#...", "..#....#....#...",
      "..#....#....#...", "..#....#....#...", "................", "################", "################", ".##..........##.",
      ".##..........##.", "................", "................", "................"],
    mains: ["................", ".......#........", ".......##.......", "......###.......", "......####......", ".....#####..#...",
      ".....######.##..", "....##########..", "....##########..", "...###########..", "...####..#####..", "...###....####..",
      "....##....###...", ".....##..###....", "......#####.....", "................"],
    boost: ["................", "................", ".###.....###....", "..###.....###...", "...###.....###..", "....###.....###.",
      ".....###.....###", "......###.....##", "......###.....##", ".....###.....###", "....###.....###.", "...###.....###..",
      "..###.....###...", ".###.....###....", "................", "................"],
    jump: ["................", ".......##.......", "..#....##....#..", "...#...##...#...", "....#..##..#....", ".....#.##.#.....",
      "......####......", "################", "################", "......####......", ".....#.##.#.....", "....#..##..#....",
      "...#...##...#...", "..#....##....#..", ".......##.......", "................"],
    climb_boost: CLIMB_ROWS,
    held_ramp: HELD_RAMP_ROWS,
    power: ["................", ".......##.......", ".......##.......", "...##..##..##...", "..##...##...##..", ".##....##....##.",
      ".##....##....##.", ".##..........##.", ".##..........##.", ".##..........##.", "..##........##..", "...##......##...",
      "....########....", ".....######.....", "................", "................"],
  };
  for (const [name, rows] of Object.entries(ICONS)) {
    texture(iconsOutput, name, (x, y) => (rows[y][x] === "#" ? [0xff, 0xff, 0xff, 0xff] : [0, 0, 0, 0]), 0);
  }
}
consoleTextures();
// The freighter's outside fittings (see the outside fittings in generate_resources.mjs), weathered, working metal:
// mid-grey steel panels edged in a darker seam with a rivet at each corner; dark gunmetal for their bases, collars and
// frames, scuffed along a few scratches; copper pipe ringed by darker bands with a bright line down it; an amber
// status lamp; a satellite dish's face, off-white in rings round a darker hub, and its back, grey ribbed out from its
// middle; an antenna's beacon, red, blinking a second on and three off (two frames, the dark one held longer); a sensor
// dome's cream radome in faint panels, and its band's cyan glow; heat-sink fins, light and dark by turns, and the
// orange heat glowing at their roots; an exhaust grate's dark slots between bright bars; and a floodlight's lens,
// warm white over the rings of its reflector.
function hullFittingTextures() {
  texture(blockOutput, "hull_fitting_steel", (x, y) => {
    if (x === 0 || y === 0) return [0x6c, 0x72, 0x7a];
    if ((x === 2 || x === 13) && (y === 2 || y === 13)) return [0xb0, 0xb6, 0xbe];
    return y === 15 || x === 15 ? [0x7a, 0x80, 0x88] : [0x8c, 0x93, 0x9b];
  }, 4);
  texture(blockOutput, "hull_fitting_dark", (x, y) => {
    if (x === 0 || y === 0) return [0x2c, 0x30, 0x36];
    if ((x + 2 * y) % 17 === 0 || (3 * x - y + 32) % 23 === 0) return [0x52, 0x58, 0x60];
    return [0x3c, 0x41, 0x48];
  }, 4);
  texture(blockOutput, "hull_pipe", (x, y) => {
    if (y % 8 === 0 || y % 8 === 1) return [0x7c, 0x44, 0x26];
    return x % 8 === 3 ? [0xe0, 0x9a, 0x68] : [0xb8, 0x6a, 0x3c];
  }, 4);
  texture(blockOutput, "hull_status_lamp", (x, y) => (Math.hypot(x - 7.5, y - 7.5) < 4 ? [0xff, 0xe0, 0x90] : [0xff, 0xae, 0x2c]), 0);
  texture(blockOutput, "satellite_dish_face", (x, y) => {
    const out = Math.hypot(x - 7.5, y - 7.5);
    if (out < 2) return [0x9a, 0xa0, 0xa6];
    return Math.floor(out) % 3 === 0 ? [0xc6, 0xca, 0xc6] : [0xea, 0xec, 0xe6];
  }, 2);
  texture(blockOutput, "satellite_dish_back", (x, y) => {
    const round = Math.atan2(y - 7.5, x - 7.5) / (Math.PI / 4);
    return Math.abs(round - Math.round(round)) < 0.12 ? [0x8e, 0x94, 0x9a] : [0xb6, 0xbc, 0xc2];
  }, 3);
  const BEACON_FRAMES = [[[0xff, 0x46, 0x34], [0xff, 0xd0, 0xc0]], [[0x58, 0x12, 0x10], [0x70, 0x1c, 0x18]]];
  texture(blockOutput, "antenna_lamp", (x, y) => {
    const [rim, core] = BEACON_FRAMES[Math.floor(y / SIZE)];
    return Math.hypot(x - 7.5, (y % SIZE) - 7.5) < 4 ? core : rim;
  }, 0, SIZE, SIZE * BEACON_FRAMES.length);
  const beat = { animation: { frames: [{ index: 0, time: 20 }, { index: 1, time: 60 }] } };
  writeFileSync(resolve(blockOutput, "antenna_lamp.png.mcmeta"), JSON.stringify(beat, null, 2) + "\n");
  texture(blockOutput, "sensor_dome", (x, y) => (x % 8 === 0 || y % 8 === 0 ? [0xd0, 0xce, 0xc4] : [0xea, 0xe8, 0xe0]), 2);
  texture(blockOutput, "sensor_glow", (x, y) => (y % 4 === 1 || y % 4 === 2 ? [0xc8, 0xfc, 0xff] : [0x4c, 0xe0, 0xf4]), 0);
  texture(blockOutput, "heat_sink_fin", (x, y) => {
    if (y === 0 || y === 15) return [0x5c, 0x62, 0x6a];
    return x % 2 === 0 ? [0x9c, 0xa2, 0xaa] : [0x80, 0x86, 0x8e];
  }, 3);
  texture(blockOutput, "heat_glow", (x, y) => ((x + y) % 4 === 0 ? [0xff, 0xd0, 0x70] : [0xff, 0x86, 0x2a]), 0);
  texture(blockOutput, "exhaust_grate", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15) return [0x40, 0x44, 0x4a];
    return y % 3 === 0 ? [0x76, 0x7c, 0x84] : [0x14, 0x16, 0x1a];
  }, 2);
  texture(blockOutput, "floodlight_lens", (x, y) => {
    const out = Math.hypot(x - 7.5, y - 7.5);
    return Math.floor(out) % 3 === 2 ? [0xf2, 0xe2, 0xba] : [0xff, 0xf8, 0xe8];
  }, 0);
}
hullFittingTextures();

// The freighter's hull plates (see FreighterPlating), at her skins' fineness, 32 pixels, so they sit with them: each
// with a seam along its top and its left edge only, as her skins have, so plates side by side share one, an edge a shade
// lighter just inside it, and rivets with a shadow under each. A dark gunmetal plate, rivets along its top and its foot,
// a scuff here and there and a fine scratch; a riveted seam in her cover's pale grey, two rows of rivets either side of
// a darker joint down its middle; a welded patch, a dull plate set into her pale grey, its weld bead rippling round it,
// a rivet at each corner and a scorch across it; and hazard stripes, yellow and black on the slant, worn here and
// there to grey.
function hullPlateTextures() {
  const PLATE = 32;
  // How far apart rivets are along a row, in pixels; how much darker a seam, and how much lighter the edge inside it,
  // is than its plate, in steps.
  const RIVET_EVERY = 5;
  const SEAM_DARK = 26;
  const EDGE_LIGHT = 8;
  // A plate's own seam and edge on its top and left, or nothing there.
  const edged = (x, y, colour) => {
    if (x === 0 || y === 0) return lighten(colour, -SEAM_DARK);
    if (x === 1 || y === 1) return lighten(colour, EDGE_LIGHT);
    return null;
  };
  // A rivet in a row at a pixel, its head lit and its shadow under it to the right; or nothing there.
  const rivet = (x, y, head, colour, inRow) => {
    if (inRow(x, y)) return head;
    if (inRow(x - 1, y - 1)) return lighten(colour, -14);
    return null;
  };
  const DARK = [0x5a, 0x61, 0x6a];
  texture(blockOutput, "hull_plate_dark", (x, y) => {
    const along = (rx, ry) => (ry === 4 || ry === 27) && rx >= 4 && rx <= 29 && (rx - 4) % RIVET_EVERY === 0;
    const scratch = y > 9 && y < 14 && x - 2 * (y - 10) === 19;
    return edged(x, y, DARK) ?? rivet(x, y, [0x80, 0x88, 0x92], DARK, along)
      ?? ((x * 5 + y * 3) % 41 === 0 ? [0x6a, 0x72, 0x7c] : scratch ? lighten(DARK, 9) : DARK);
  }, 4, PLATE, PLATE);
  const PALE = [0xc6, 0xcb, 0xd1];
  texture(blockOutput, "hull_plate_riveted", (x, y) => {
    const either = (rx, ry) => (rx === 9 || rx === 22) && ry >= 3 && (ry - 3) % RIVET_EVERY === 0;
    if (x === 15 || x === 16) return [0xa4, 0xaa, 0xb2];
    if (x === 17) return lighten(PALE, 6);
    return edged(x, y, PALE) ?? rivet(x, y, [0x9a, 0xa0, 0xa8], PALE, either) ?? PALE;
  }, 3, PLATE, PLATE);
  const PATCHED = [0xc4, 0xc9, 0xcf];
  const PATCH = [0x9a, 0x98, 0x8c];
  texture(blockOutput, "hull_plate_patched", (x, y) => {
    const inPatch = x >= 6 && x <= 25 && y >= 8 && y <= 25;
    const onBead = inPatch && (x === 6 || x === 25 || y === 8 || y === 25);
    if (onBead) return (x + y) % 2 === 0 ? [0x6e, 0x6a, 0x62] : [0x80, 0x7c, 0x72];
    const corner = (rx, ry) => (rx === 9 || rx === 22) && (ry === 11 || ry === 22);
    const scorch = Math.abs(x - y - 1);
    if (inPatch) return rivet(x, y, [0x7c, 0x78, 0x70], PATCH, corner) ?? (scorch <= 1 ? [0x6a, 0x66, 0x5c] : scorch <= 3 ? [0x82, 0x7f, 0x74] : PATCH);
    return edged(x, y, PATCHED) ?? PATCHED;
  }, 5, PLATE, PLATE);
  texture(blockOutput, "hazard_plate", (x, y) => {
    const stripe = Math.floor((x + y) / 8) % 2 === 0 ? [0xf0, 0xbc, 0x1c] : [0x24, 0x24, 0x26];
    const worn = (x * 7 + y * 11) % 89 === 0 || (x * 7 + y * 11) % 89 === 1;
    if (x === 0 || y === 0) return mixColour(stripe, [0x24, 0x24, 0x26], 0.5);
    return worn ? [0x8a, 0x86, 0x7a] : stripe;
  }, 5, PLATE, PLATE);
}
hullPlateTextures();

// The freighter's hull (see the freighter_cover block): the zeppelin's doped silver at twice its fineness, laid as
// riveted plates of many sizes. Each skin is one of five layouts of plates whose seams run along the skin's top and left
// edges only, so that blocks side by side share one seam; each plate has a tone of its own, an edge a shade lighter just
// inside its seam, a rivet row along its top and its left, a soft sheen down its middle, faint grain, the odd darker
// speck and a fine scratch or two. Now and then a skin carries a detail besides: an inspection hatch on its hinges, a
// welded patch, a doubler riveted on, a faint scorch, a little vent or a stencilled arrow. Each skin is evened out to
// the zeppelin skin's mean colour, so she looks as she did from afar and finer close up, and undoped as that is, for the
// livery's silver to tint back. Her blockstate picks a skin for each block by where it is, the details seldom.
function freighterSkinTextures(fabric, undope, palette) {
  const SKIN = 32;
  // Her plate layouts, each plate a rectangle [left, top, right, bottom) in the skin's pixels, the plates tiling it.
  const LAYOUTS = [
    [[0, 0, 20, 12], [20, 0, 32, 12], [0, 12, 11, 32], [11, 12, 32, 22], [11, 22, 32, 32]],
    [[0, 0, 32, 9], [0, 9, 14, 32], [14, 9, 32, 25], [14, 25, 32, 32]],
    [[0, 0, 9, 18], [9, 0, 32, 18], [0, 18, 24, 32], [24, 18, 32, 32]],
    [[0, 0, 16, 32], [16, 0, 32, 14], [16, 14, 32, 32]],
    [[0, 0, 13, 7], [13, 0, 32, 7], [0, 7, 26, 20], [26, 7, 32, 20], [0, 20, 7, 32], [7, 20, 19, 32], [19, 20, 32, 32]],
  ];
  // Each skin: its name, its layout and the detail on it, if any, where on the skin. Its blockstate weights are in
  // generate_resources.mjs (FREIGHTER_SKINS).
  const SKINS = [
    ["1", 0], ["2", 1], ["3", 2], ["4", 3], ["5", 4],
    ["hatch", 1, skinHatch, [18, 13, 28, 21]],
    ["patch", 2, skinPatch, [13, 4, 24, 13]],
    ["rivets", 0, skinDoubler, [15, 14, 28, 21]],
    ["scorch", 3, skinScorch, [6, 3, 16, 30]],
    ["vent", 4, skinVent, [9, 10, 18, 18]],
    ["arrow", 1, skinArrow, [3, 13, 11, 28]],
  ];
  const target = [0, 1, 2].map((c) => meanOver(16, 16, (x, y) => fabric(x, y)[c]));
  for (const [name, layout, detail, where] of SKINS) {
    reseed(`freighter_skin_${name}/plates`);
    const pixels = skinPlates(SKIN, LAYOUTS[layout], palette);
    if (detail) detail(pixels, where, palette);
    evenOut(pixels, target);
    texture(blockOutput, `freighter_skin_${name}`, (x, y) => pixels[y][x].map((c) => clamp(Math.round(c * undope))), 3, SKIN, SKIN);
  }
}
/** The mean of what paint gives over a width and a height of pixels. */
function meanOver(width, height, paint) {
  let sum = 0;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) sum += paint(x, y);
  }
  return sum / (width * height);
}
/** Shifts a picture's colours, channel by channel, so its mean is the target's. */
function evenOut(pixels, target) {
  const size = pixels.length;
  const shift = [0, 1, 2].map((c) => target[c] - meanOver(pixels[0].length, size, (x, y) => pixels[y][x][c]));
  for (const pixel of pixels.flat()) {
    for (let c = 0; c < 3; c++) pixel[c] += shift[c];
  }
}
/** Two colours mixed, the given share of the second. */
function mixColour(a, b, share) {
  return a.map((value, c) => value + (b[c] - value) * share);
}
/** A colour made lighter, or darker for a negative amount, by so many steps. */
function lighten(colour, amount) {
  return colour.map((value) => value + amount);
}
/**
 * A freighter skin's plates, as floating-point colours before evening out: each plate's tone strays from the silver
 * by up to PLATE_TONE steps (see platePixel); then specks and scratches over the whole.
 */
function skinPlates(size, plates, palette) {
  const PLATE_TONE = 5;
  const tones = plates.map(() => (random() * 2 - 1) * PLATE_TONE);
  const pixels = Array.from({ length: size }, (_, y) => Array.from({ length: size }, (_, x) => platePixel(plates, tones, x, y, palette)));
  skinWear(pixels);
  return pixels;
}
/**
 * A pixel of a skin's plates: the plate's seam on its top and left, a lighter edge just inside it, a rivet every
 * RIVET_EVERY pixels along both, and a sheen down its middle, full at its heart and fading either side.
 */
function platePixel(plates, tones, x, y, { silver, seam, sheen }) {
  const RIVET_EVERY = 5;
  const RIVET_DARK = 20;
  // The sheen's full and fading half-widths, as shares of a plate's width, each at least a pixel or a pixel and a half.
  const SHEEN_FULL = 0.12;
  const SHEEN_FADE = 0.2;
  const k = plates.findIndex(([a, b, c, d]) => x >= a && x < c && y >= b && y < d);
  const [a, b, c, d] = plates[k];
  const off = Math.abs(x + 0.5 - (a + c) / 2);
  const glow = off <= Math.max(1, (c - a) * SHEEN_FULL) ? 1 : off <= Math.max(1.5, (c - a) * SHEEN_FADE) ? 0.5 : 0;
  let colour = lighten(mixColour(silver, sheen, glow), tones[k]);
  if (x === a || y === b) colour = [...seam];
  else if (x === a + 1 || y === b + 1) colour = mixColour(colour, sheen, 0.5);
  const alongTop = y === b + 2 && x >= a + 2 && x < c - 1 && (x - a - 2) % RIVET_EVERY === 0;
  const alongLeft = x === a + 2 && y >= b + 2 && y < d - 1 && (y - b - 2) % RIVET_EVERY === 0;
  return alongTop || alongLeft ? lighten(colour, -RIVET_DARK) : colour;
}
/** Specks, a pixel in SPECKS darker by up to SPECK_DARK steps, and SCRATCHES fine light scratches a few pixels long. */
function skinWear(pixels) {
  const SPECKS = 0.04;
  const SPECK_DARK = 10;
  const SCRATCHES = 2;
  const size = pixels.length;
  for (const row of pixels) {
    for (let x = 0; x < size; x++) row[x] = random() < SPECKS ? lighten(row[x], -(4 + random() * (SPECK_DARK - 4))) : row[x];
  }
  for (let n = 0; n < SCRATCHES; n++) {
    skinScratch(pixels, 2 + random() * (size - 8), 2 + random() * (size - 8), 3 + random() * 3, random() < 0.5 ? 1 : -1);
  }
}
/** A fine scratch, bare metal a few steps lighter, running right from a point and half as fast up or down. */
function skinScratch(pixels, x0, y0, length, slope) {
  const SCRATCH_LIGHT = 6;
  for (let t = 0; t < length; t++) {
    const [x, y] = [Math.floor(x0 + t), Math.floor(y0 + t * slope * 0.5)];
    if (y > 1 && y < pixels.length) pixels[y][x] = lighten(pixels[y][x], SCRATCH_LIGHT);
  }
}
/** Calls paint for every pixel of a rectangle [left, top, right, bottom), with whether it is on its edge. */
function eachIn([x0, y0, x1, y1], paint) {
  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) paint(x, y, x === x0 || x === x1 - 1 || y === y0 || y === y1 - 1);
  }
}
/**
 * An inspection hatch: a groove round it darker than the seams, its top and left catching the light and its foot and
 * right in shadow, two hinge knuckles over its left groove, each lit along its top, and a latch slot on its right.
 */
function skinHatch(pixels, where, { seam, sheen }) {
  const [x0, y0, x1, y1] = where;
  eachIn(where, (x, y, edge) => {
    if (edge) pixels[y][x] = lighten(seam, -22);
    else if (x === x0 + 1 || y === y0 + 1) pixels[y][x] = mixColour(pixels[y][x], sheen, 0.6);
    else if (x === x1 - 2 || y === y1 - 2) pixels[y][x] = lighten(pixels[y][x], -7);
  });
  for (const knuckle of [y0 + 1, y1 - 3]) {
    eachIn([x0 - 1, knuckle, x0 + 1, knuckle + 2], (x, y) => {
      pixels[y][x] = y === knuckle ? mixColour(seam, sheen, 0.5) : lighten(seam, -16);
    });
  }
  const latch = Math.floor((y0 + y1) / 2);
  pixels[latch][x1 - 3] = lighten(seam, -30);
  pixels[latch][x1 - 4] = lighten(seam, -30);
}
/**
 * A welded patch: a duller plate, its grind marks on the slant, set in a rippled weld bead, a faint straw-coloured heat
 * tint round it.
 */
function skinPatch(pixels, where, { silver, seam }) {
  const [x0, y0, x1, y1] = where;
  const HEAT = [3, 0, -5];
  eachIn([x0 - 1, y0 - 1, x1 + 1, y1 + 1], (x, y, edge) => {
    if (edge) pixels[y][x] = pixels[y][x].map((value, c) => value + HEAT[c] - 2);
  });
  eachIn(where, (x, y, edge) => {
    if (edge) pixels[y][x] = lighten(seam, (x + y) % 2 === 0 ? -24 : -12);
    else pixels[y][x] = lighten(silver, (x - y) % 4 === 0 ? -5 : -9);
  });
}
/**
 * A doubler riveted over a plate: a raised plate, lit along its top and left and shadowed along its foot and right,
 * with rivets close together just inside its edges.
 */
function skinDoubler(pixels, where) {
  eachIn(where, (x, y, edge) => {
    pixels[y][x] = lighten(pixels[y][x], doublerShade(where, x, y, edge));
  });
}
/** How much lighter a doubler leaves a pixel of it: its lit or shadowed edge, or a rivet. */
function doublerShade([x0, y0, x1, y1], x, y, edge) {
  const inRow = (y === y0 + 1 || y === y1 - 2) && x > x0 && x < x1 - 1 && (x - x0 - 1) % 2 === 0;
  const inColumn = (x === x0 + 1 || x === x1 - 2) && y > y0 && y < y1 - 1 && (y - y0 - 1) % 2 === 0;
  if (edge) return x === x0 || y === y0 ? 7 : -9;
  return inRow || inColumn ? -18 : 0;
}
/**
 * A faint scorch: a sooty streak running down from under a seam, darkest where it starts, widening and fading as it
 * goes and drifting a little aside, warmer than grey, its edges broken by the grain.
 */
function skinScorch(pixels, where) {
  const [x0, y0, x1, y1] = where;
  const DARKEST = 30;
  for (let y = y0; y < y1; y++) {
    const t = (y - y0) / (y1 - y0);
    const middle = x0 + 0.5 + t * 2.5;
    const width = 1.2 + t * 3;
    for (let x = Math.max(0, x0 - 6); x < x1; x++) {
      const dark = DARKEST * Math.pow(1 - t, 1.3) * Math.exp(-(((x + 0.5 - middle) / width) ** 2)) * (0.75 + random() * 0.5);
      pixels[y][x] = [pixels[y][x][0] - dark * 0.85, pixels[y][x][1] - dark, pixels[y][x][2] - dark * 1.15];
    }
  }
}
/** A little vent: three slits in a darker frame, the lip under each catching the light. */
function skinVent(pixels, where, { seam }) {
  const [x0, y0, x1, y1] = where;
  eachIn(where, (x, y, edge) => {
    if (edge) {
      pixels[y][x] = lighten(pixels[y][x], -9);
    } else if (x > x0 + 1 && x < x1 - 2 && (y - y0) % 2 === 1 && y < y1 - 2) {
      pixels[y][x] = lighten(seam, -55);
    } else if (x > x0 + 1 && x < x1 - 2 && (y - y0) % 2 === 0) {
      pixels[y][x] = lighten(pixels[y][x], 6);
    }
  });
}
/**
 * A stencilled arrow pointing up her skin, in dark slate: its head a widening triangle, a bridge left between head and
 * shaft as a stencil leaves one, and the paint worn through here and there.
 */
function skinArrow(pixels, where) {
  const [x0, y0, x1] = where;
  const SLATE = [0x4a, 0x55, 0x62];
  const middle = (x0 + x1) / 2;
  const head = y0 + Math.floor((x1 - x0) / 2);
  const half = (y) => (y < head ? y - y0 + 1 : y === head ? 0 : 1);
  eachIn(where, (x, y) => {
    if (Math.abs(x + 0.5 - middle) < half(y)) pixels[y][x] = mixColour(pixels[y][x], SLATE, random() < 0.15 ? 0.25 : 0.55);
  });
}
// The freighter's smugglers' hold (see FreighterSmugglerDen): the secret panel, her riveted seam plate but a shade
// darker, a hairline seam down either side and scuffs slashed across it near both edges, where it has swung open many
// times; and the loose rivet's brass, a bright crown in its middle.
function smugglerHoldTextures() {
  texture(blockOutput, "secret_panel", (x, y) => {
    if (x === 0 || x === 15) return [0x6a, 0x70, 0x78];
    if ((x <= 3 || x >= 12) && (x + 2 * y) % 7 === 0) return [0x98, 0x9c, 0xa0];
    if (x === 7 || x === 8) return [0x9c, 0xa2, 0xaa];
    if ((x === 4 || x === 11) && y % 4 === 1) return [0x92, 0x98, 0xa0];
    return [0xb8, 0xbd, 0xc3];
  }, 3);
  texture(blockOutput, "loose_rivet", (x, y) => {
    if (Math.hypot(x - 7.5, y - 7.5) < 1.5) return [0xff, 0xe8, 0xa0];
    return x + y < 15 ? [0xe0, 0xb0, 0x48] : [0xa8, 0x78, 0x28];
  }, 3);
}
smugglerHoldTextures();

// The freighter's leg bays (see LegBayBlock), where each landing leg meets her belly: on its sides a housing of dark
// gunmetal, riveted, with a band of hazard stripes along its foot, as round anything that moves; and from below its
// socket, a steel collar round a dark well the strut's sleeves draw up into, a glint of chrome on its far wall.
function legBayTextures() {
  texture(blockOutput, "leg_bay_side", (x, y) => {
    if (y >= 12) return Math.floor((x + y) / 3) % 2 === 0 ? [0xf0, 0xbc, 0x1c] : [0x24, 0x24, 0x26];
    if (y === 11 || x === 0) return [0x34, 0x3a, 0x42];
    if (y === 2 && x % 4 === 2) return [0x80, 0x88, 0x92];
    return [0x4c, 0x53, 0x5c];
  }, 4);
  texture(blockOutput, "leg_bay_bottom", (x, y) => {
    const out = Math.hypot(x - 7.5, y - 7.5);
    if (out < 4.5) return x + y < 12 && out > 3 ? [0x9a, 0xa2, 0xac] : [0x16, 0x18, 0x1c];
    if (out < 6.5) return out < 5.5 ? [0x8a, 0x90, 0x97] : [0x5c, 0x62, 0x69];
    if (x === 0 || y === 0 || x === 15 || y === 15) return [0x34, 0x3a, 0x42];
    return [0x4c, 0x53, 0x5c];
  }, 3);
}
legBayTextures();

// The freighter's outside lights (see FreighterLights): her light band, a bright cyan-white line along the middle of a
// dark plate; and a light strip's glowing diffuser.
function hullLightTextures() {
  texture(blockOutput, "hull_light_band", (x, y) => {
    if (y === 7 || y === 8) return [0xec, 0xfe, 0xff];
    if (y === 6 || y === 9) return [0x8a, 0xec, 0xff];
    if (y === 5 || y === 10) return [0x4a, 0x56, 0x62];
    return x === 0 ? [0x2a, 0x30, 0x38] : [0x34, 0x3a, 0x44];
  }, 2);
  texture(blockOutput, "hull_light_strip", (x, y) => (y >= 6 && y <= 9 ? [0xec, 0xfe, 0xff] : [0x8a, 0xec, 0xff]), 0);
}
hullLightTextures();
// The freighter's tractor beam and its pad (see TractorBeamBlock and BeamPadBlock): the beam's housing of dark
// gunmetal, riveted round its plate; its body ringed in hazard stripes under a row of vent slots; the rim round its
// lens, ribbed steel; and its lens, seen from below, a wide round glass in a steel ring, dark green with rings in it,
// or lit, glowing green to a white-hot middle. The pad's top is the rings of a target round a round plate in a frame of
// deck plate, hazard stripes in its corners: dull while its beam is dark, and while it is lit glowing green, a bright
// ring pulsing in from its edge to its middle over and over, so it reads at a glance as the place things come and go;
// its sides are deck plate with a green line under its top.
function tractorBeamTextures() {
  const GUNMETAL = [0x4c, 0x53, 0x5c];
  const GUNMETAL_DARK = [0x34, 0x3a, 0x42];
  const RIVET = [0x8a, 0x92, 0x9c];
  const STEEL = [0x9a, 0xa1, 0xa8];
  const STEEL_DARK = [0x5c, 0x63, 0x6b];
  const YELLOW = [0xf0, 0xbc, 0x1c];
  const BLACK = [0x24, 0x24, 0x26];
  const GREEN = [0x58, 0xff, 0x8c];
  const stripes = (x, y) => (Math.floor((x + y) / 3) % 2 === 0 ? YELLOW : BLACK);
  texture(blockOutput, "tractor_beam_housing", (x, y) => {
    if (x === 0 || y === 0 || x === 15 || y === 15) return GUNMETAL_DARK;
    if ((x === 2 || x === 13) && (y === 2 || y === 13)) return RIVET;
    return (x + y * 3) % 17 === 0 ? [0x56, 0x5e, 0x68] : GUNMETAL;
  }, 3);
  texture(blockOutput, "tractor_beam_side", (x, y) => {
    if (y >= 9) return stripes(x, y);
    if (y === 8 || y === 0) return GUNMETAL_DARK;
    if (y >= 3 && y <= 5 && x % 4 !== 0) return y === 3 ? [0x18, 0x1a, 0x1e] : [0x22, 0x26, 0x2c];
    return GUNMETAL;
  }, 3);
  texture(blockOutput, "tractor_beam_rim", (x, y) => (x % 3 === 0 ? STEEL_DARK : y === 0 || y === 15 ? [0x7a, 0x82, 0x8a] : STEEL), 2);
  const lens = (glass) => (x, y) => {
    const out = Math.hypot(x - 7.5, y - 7.5);
    if (out > 7.2) return STEEL_DARK;
    if (out > 5.6) return out > 6.6 || Math.round(Math.atan2(y - 7.5, x - 7.5) * 4) % 2 === 0 ? STEEL : [0xb4, 0xba, 0xc0];
    return glass(out, x, y);
  };
  texture(blockOutput, "tractor_beam_lens", lens((out, x, y) => {
    if (x - y > 2 && x - y < 4 && out > 2) return [0x9c, 0xd8, 0xb0];
    return Math.round(out) % 2 === 0 ? [0x1c, 0x4a, 0x2c] : [0x24, 0x5c, 0x38];
  }), 0);
  texture(blockOutput, "tractor_beam_lens_lit", lens((out) => {
    if (out < 1.6) return [0xf4, 0xff, 0xf6];
    if (out < 3) return [0xb4, 0xff, 0xcc];
    return Math.round(out) % 2 === 0 ? GREEN : [0x8c, 0xff, 0xac];
  }), 0);
  // The pad's top: a frame of deck plate with hazard stripes in its corners, a steel ring, then the target's rings
  // round its round middle plate, each ring's brightness given by how far it is from the middle.
  const pad = (ring) => (x, y) => {
    const corner = (x < 3 || x > 12) && (y < 3 || y > 12);
    if (corner) return stripes(x, y);
    if (x === 0 || y === 0 || x === 15 || y === 15) return GUNMETAL_DARK;
    const out = Math.hypot(x - 7.5, y - 7.5);
    if (out > 7) return GUNMETAL;
    if (out > 6.2) return STEEL;
    return ring(out);
  };
  const dull = (out) => (Math.round(out) % 2 === 0 ? [0x3c, 0x56, 0x46] : GUNMETAL_DARK);
  texture(blockOutput, "beam_pad_top", pad(dull), 2);
  // How many frames the lit pad's pulse takes to come in from its edge to its middle.
  const FRAMES = 8;
  const glowing = (frame) => (out) => {
    const pulse = 6 * (1 - frame / FRAMES);
    if (Math.abs(out - pulse) < 0.8) return [0xf0, 0xff, 0xf4];
    if (out < 1.6) return [0xc8, 0xff, 0xd8];
    return Math.round(out) % 2 === 0 ? GREEN : [0x1c, 0x6a, 0x38];
  };
  texture(blockOutput, "beam_pad_top_lit", (x, y) => pad(glowing(Math.floor(y / SIZE)))(x, y % SIZE), 0, SIZE, SIZE * FRAMES);
  writeFileSync(resolve(blockOutput, "beam_pad_top_lit.png.mcmeta"), JSON.stringify({ animation: { frametime: 2 } }, null, 2) + "\n");
  texture(blockOutput, "beam_pad_side", (x, y) => {
    if (y === 1) return [0x3c, 0xd8, 0x6c];
    if (y === 0 || y === 15) return GUNMETAL_DARK;
    if (y === 8 && x % 5 === 2) return RIVET;
    return GUNMETAL;
  }, 3);
}
tractorBeamTextures();
console.log("Textures written to", blockOutput, "and", itemOutput);
