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

/** A texture painted pixel by pixel: paint(x, y) returns the colour, with a little noise mixed into opaque ones. */
function texture(folder, name, paint, noise = 4) {
  reseed(name);
  const pixels = [];
  for (let y = 0; y < SIZE; y++) {
    const row = [];
    for (let x = 0; x < SIZE; x++) {
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
// The call button's cap: bright yellow so the ground crew find it.
texture(blockOutput, "airstair_call_button", (x, y) => (x + y) % 5 === 0 ? [0xd0, 0x9f, 0x14] : HAZARD, 3);

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
// Thrust lever: a dark pedestal with the lever slot, a black handle and a pale grip.
texture(blockOutput, "thrust_lever_base", (x, y) => (x >= 7 && x <= 8 && y >= 3 && y <= 12 ? PANEL_EDGE : PANEL_DARK), 3);
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

// Cabin service. The galley: brushed steel cupboards, an oven door with a dark window on the front and a coffee
// machine's spout. The trolley: aluminium with a red stripe and drawer lines, its ends a grid of drawers with
// handles, its top a grey tray. The meals on their trays, a bag of pretzels and a paper cup of coffee.
{
  const ALU = [0xc4, 0xc9, 0xcf];
  const ALU_DARK = [0x8e, 0x94, 0x9b];
  texture(blockOutput, "galley_side", (x, y) => (x === 0 || x === 15 || y === 0 || y === 15 ? ALU_DARK : (x + y * 3) % 7 === 0 ? [0xb4, 0xb9, 0xbf] : ALU));
  texture(blockOutput, "galley_front", (x, y) => {
    if (x === 0 || x === 15 || y === 0 || y === 15 || y === 7) return ALU_DARK;
    if (y > 7 && x >= 3 && x <= 12 && y >= 9 && y <= 13) return x === 3 || x === 12 || y === 9 || y === 13 ? [0x30, 0x32, 0x36] : [0x40, 0x26, 0x18];
    if (y < 7 && x >= 9 && x <= 12 && y >= 2 && y <= 5) return [0x26, 0x28, 0x2c];
    if (y < 7 && x >= 3 && x <= 5 && y >= 2 && y <= 4) return [0x60, 0x40, 0x28];
    return ALU;
  });
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
}

// The control panels' pictures, in the glass cockpit's colours: every setting and button has one, so the panels work
// for players who cannot read yet. The panel's bezel: brushed dark metal, each row of it a little lighter or darker.
// Its close button: an X, white so it can be drawn in any colour. The autopilot's: the airliner from above for its
// title; a compass for the heading (cyan, its needle red to the north); a green hill with an up-and-down arrow for
// the altitude; a speedometer for the speed (green, amber and red, the needle well round); a magenta circle with its
// radius drawn in for the circle's radius. For the mode, a cyan arrow straight ahead to hold the heading and magenta
// arrows round a circle to the left or right, in the colours of the settings each flies. Then the buttons: a green
// airliner climbing off a runway to take off, or flying on level to engage in the air; an amber one settling onto
// the runway to land; a green tick to apply; a red power symbol to switch the autopilot off.
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
}

console.log("Textures written to", blockOutput, "and", itemOutput);
