import { fileURLToPath } from "node:url";
import { mkdirSync, readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { resolve, join } from "node:path";
import { tmpdir } from "node:os";
import { execFileSync } from "node:child_process";

// Run from anywhere: node tools/generate_sounds.mjs [<name> ...]
// Writes the airliner's sounds as Ogg Vorbis into assets/aerolarge/sounds, with FFmpeg on PATH (and, where FFmpeg has
// no libvorbis, as Homebrew's FFmpeg 9 has not, oggenc from vorbis-tools, the same encoder). Given sound names, it
// writes only those and leaves the rest as they are:
// - engine.ogg: a turbofan running, a seamless loop the engines play, pitched and swelled with thrust;
// - the flight deck's voice, its callouts and cabin announcements, and air traffic control over the radio, spoken by
//   Windows' own speech synthesis (System.Speech) and then reworked (see VOICE and RADIO), so those parts run on
//   Windows only. Deterministic apart from the voices, which are whatever Windows ships.
const ROOT = fileURLToPath(new URL("..", import.meta.url));
const output = resolve(ROOT, "src/main/resources/assets/aerolarge/sounds");
mkdirSync(output, { recursive: true });
const scratch = mkdtempSync(join(tmpdir(), "aerolarge-sounds-"));

const RATE = 44100;

let seed = 0x41657232;
function random() {
  seed = (seed + 0x6d2b79f5) | 0;
  let value = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  value = (value + Math.imul(value ^ (value >>> 7), 61 | value)) ^ value;
  return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
}

function writeWav(path, samples) {
  const data = Buffer.alloc(samples.length * 2);
  samples.forEach((s, i) => data.writeInt16LE(Math.round(Math.max(-1, Math.min(1, s)) * 32767), i * 2));
  const header = Buffer.alloc(44);
  header.write("RIFF", 0);
  header.writeUInt32LE(36 + data.length, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(1, 22);
  header.writeUInt32LE(RATE, 24);
  header.writeUInt32LE(RATE * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36);
  header.writeUInt32LE(data.length, 40);
  writeFileSync(path, Buffer.concat([header, data]));
}

// The sounds named on the command line, to write only those; every sound is still worked out, in order, so each comes
// out the same either way.
const ONLY = new Set(process.argv.slice(2));
// Whether this FFmpeg encodes Vorbis with libvorbis; if not, oggenc does, at the same quality.
const LIBVORBIS = execFileSync("ffmpeg", ["-hide_banner", "-encoders"]).toString().includes("libvorbis");

function toOgg(wav, name) {
  if (ONLY.size > 0 && !ONLY.has(name)) {
    return;
  }
  const ogg = resolve(output, `${name}.ogg`);
  if (LIBVORBIS) {
    execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", ogg]);
  } else {
    execFileSync("oggenc", ["--quiet", "-q", "5", "-o", ogg, wav]);
  }
}

// The engine: low-passed roar for the exhaust, a band of fan whine with the blades' harmonics wavering slightly, and a
// low rumble. Rendered a little long and the tail crossfaded over the head, so the loop has no seam.
{
  const seconds = 4;
  const fade = Math.round(0.5 * RATE);
  const length = seconds * RATE + fade;
  const samples = new Float64Array(length);
  let low = 0;
  let band = 0;
  let bandLow = 0;
  for (let i = 0; i < length; i++) {
    const t = i / RATE;
    const white = random() * 2 - 1;
    low += 0.04 * (white - low);
    bandLow += 0.25 * (white - bandLow);
    band = bandLow - low;
    const waver = 1 + 0.004 * Math.sin(2 * Math.PI * 0.5 * t);
    const whine = 0.18 * Math.sin(2 * Math.PI * 1860 * waver * t) + 0.08 * Math.sin(2 * Math.PI * 3720 * waver * t)
      + 0.05 * Math.sin(2 * Math.PI * 930 * waver * t);
    const rumble = 0.25 * Math.sin(2 * Math.PI * 55 * t) + 0.12 * Math.sin(2 * Math.PI * 110 * t);
    samples[i] = 2.2 * low + 0.6 * band + whine + rumble;
  }
  const loop = new Float64Array(seconds * RATE);
  for (let i = 0; i < loop.length; i++) {
    loop[i] = samples[i];
  }
  for (let i = 0; i < fade; i++) {
    const w = i / fade;
    loop[i] = samples[seconds * RATE + i] * (1 - w) + samples[i] * w;
  }
  let peak = 0;
  loop.forEach((s) => (peak = Math.max(peak, Math.abs(s))));
  const wav = join(scratch, "engine.wav");
  writeWav(wav, Array.from(loop, (s) => (s / peak) * 0.85));
  toOgg(wav, "engine");
}

/** Peak-normalises samples to a little under full scale, or to the level given, and writes them as the named sound. */
function writeSound(name, samples, level = 0.85) {
  let peak = 0;
  samples.forEach((s) => (peak = Math.max(peak, Math.abs(s))));
  const wav = join(scratch, `${name}.wav`);
  writeWav(wav, Array.from(samples, (s) => (s / peak) * level));
  toOgg(wav, name);
}

/** A struck bell: a few decaying partials of the note, starting at the given time in seconds. */
function bell(samples, start, frequency, decay) {
  const partials = [[1, 1], [2.01, 0.35], [3.02, 0.12]];
  for (let i = Math.round(start * RATE); i < samples.length; i++) {
    const t = i / RATE - start;
    const envelope = Math.min(1, t * 400) * Math.exp(-t / decay);
    for (const [ratio, gain] of partials) {
      samples[i] += gain * envelope * Math.sin(2 * Math.PI * frequency * ratio * t);
    }
  }
}

// The cabin chime before an announcement: two bell tones, high then low. The voice's announcements open with it too.
const CHIME = new Float64Array(Math.round(2.2 * RATE));
bell(CHIME, 0, 659.25, 0.55);
bell(CHIME, 0.42, 523.25, 0.7);
writeSound("pa_chime", CHIME);

// A radio call's crackle: band-passed static that swells in and dies away, with the odd sharp click.
{
  const samples = new Float64Array(Math.round(0.7 * RATE));
  let low = 0;
  let bandLow = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = random() * 2 - 1;
    low += 0.08 * (white - low);
    bandLow += 0.45 * (white - bandLow);
    const envelope = Math.min(1, t * 30) * Math.max(0, 1 - t / 0.7);
    const click = random() < 0.0015 ? (random() * 2 - 1) * 3 : 0;
    samples[i] = envelope * (bandLow - low) + click * envelope;
  }
  writeSound("radio_crackle", samples);
}

// The seatbelt signs coming on: a single soft bell tone.
{
  const samples = new Float64Array(Math.round(1.6 * RATE));
  bell(samples, 0, 987.77, 0.5);
  writeSound("seatbelt_ding", samples);
}

// The window shades' motor: a small electric whirr, rising as it spins up and falling as it stops.
{
  const samples = new Float64Array(Math.round(0.9 * RATE));
  let phase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const speed = Math.sin(Math.PI * t / 0.9);
    phase += 2 * Math.PI * (90 + 60 * speed) / RATE;
    const saw = 2 * ((phase / (2 * Math.PI)) % 1) - 1;
    samples[i] = speed * (0.6 * saw + 0.25 * Math.sin(phase * 4) + 0.1 * (random() * 2 - 1));
  }
  writeSound("shade_motor", samples);
}

// A trolley's wheel squeaking: a thin wavering whistle.
{
  const samples = new Float64Array(Math.round(0.35 * RATE));
  let phase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    phase += 2 * Math.PI * (1700 + 260 * Math.sin(2 * Math.PI * 9 * t)) / RATE;
    samples[i] = Math.sin(Math.PI * t / 0.35) * (Math.sin(phase) + 0.3 * Math.sin(phase * 2.01));
  }
  writeSound("trolley_squeak", samples);
}

// Basketball. A bounce: a deep thud, falling in pitch as it dies, with the slap of the leather on top.
{
  const samples = new Float64Array(Math.round(0.3 * RATE));
  let phase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    phase += 2 * Math.PI * (70 + 110 * Math.exp(-t / 0.04)) / RATE;
    samples[i] = Math.exp(-t / 0.07) * Math.sin(phase) + (t < 0.01 ? (random() * 2 - 1) * (1 - t / 0.01) * 0.5 : 0);
  }
  writeSound("ball_bounce", samples);
}
// A basket: the hiss of the net as the ball drops through it.
{
  const samples = new Float64Array(Math.round(0.6 * RATE));
  let previous = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = random() * 2 - 1;
    const high = white - previous;
    previous = white;
    samples[i] = high * Math.sin(Math.PI * Math.min(1, t / 0.6)) ** 2;
  }
  writeSound("basket_swish", samples);
}
// The scoreboard's buzzer: two harsh square tones together.
{
  const samples = new Float64Array(Math.round(0.9 * RATE));
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const square = (frequency) => (Math.sin(2 * Math.PI * frequency * t) >= 0 ? 1 : -1);
    samples[i] = (square(220) + 0.6 * square(331)) * Math.min(1, t * 200) * Math.min(1, (0.9 - t) * 30);
  }
  writeSound("scoreboard_buzzer", samples);
}
// A springboard's boing: a twanging tone, rich in overtones, its pitch wobbling wide at first and settling as it
// sags and dies away, like a door stop's spring.
{
  const samples = new Float64Array(Math.round(0.7 * RATE));
  let phase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const wobble = 0.35 * Math.exp(-t / 0.25) * Math.sin(2 * Math.PI * 11 * t);
    phase += 2 * Math.PI * 240 * (1 + wobble) * (1 - 0.15 * t) / RATE;
    const envelope = Math.min(1, t * 300) * Math.exp(-t / 0.22);
    samples[i] = envelope * (Math.sin(phase) + 0.4 * Math.sin(2 * phase) + 0.15 * Math.sin(3 * phase));
  }
  writeSound("springboard_boing", samples);
}

// Duty free and the paper plane. A paper plane leaving the hand: a short airy whoosh, a band of noise swelling and
// dying away as it rises a little.
{
  const length = 0.5;
  const samples = new Float64Array(Math.round(length * RATE));
  let low = 0;
  let bandLow = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = random() * 2 - 1;
    low += (0.02 + 0.06 * (t / length)) * (white - low);
    bandLow += 0.3 * (white - bandLow);
    samples[i] = (bandLow - low) * Math.sin((Math.PI * t) / length) ** 2;
  }
  writeSound("paper_plane_whoosh", samples);
}
// The duty-free counter. Its shop's door bell as it opens: two quick bright dings, the second higher.
{
  const samples = new Float64Array(Math.round(1.2 * RATE));
  bell(samples, 0, 1318.51, 0.3);
  bell(samples, 0.11, 1567.98, 0.4);
  writeSound("shop_bell", samples);
}
// Its till ringing up a sale: the clack of the keys and the drawer sliding out, then the bright "ching" of its bell
// and a few coins jingling.
{
  const samples = new Float64Array(Math.round(1.1 * RATE));
  let low = 0;
  for (let i = 0; i < Math.round(0.2 * RATE); i++) {
    const t = i / RATE;
    const white = random() * 2 - 1;
    low += 0.15 * (white - low);
    const clack = t < 0.03 ? (1 - t / 0.03) * white : 0;
    const drawer = t > 0.05 ? 0.6 * Math.sin((Math.PI * (t - 0.05)) / 0.15) * low : 0;
    samples[i] += clack + drawer;
  }
  bell(samples, 0.18, 2093.0, 0.45);
  bell(samples, 0.18, 2637.02, 0.35);
  for (let coin = 0; coin < 5; coin++) {
    bell(samples, 0.28 + random() * 0.3, 3500 + random() * 1500, 0.04);
  }
  writeSound("cash_register", samples);
}

// The pilot's head-up display coming on as the pilot helmet goes on: the visor's soft click down over the eyes, then
// two quick electronic beeps, the second higher. Each beep is a sine with a little of its third harmonic, so it sounds
// like a cockpit's tone rather than a pure whistle, faded in and out over a few milliseconds so it does not click.
{
  const samples = new Float64Array(Math.round(0.4 * RATE));
  let low = 0;
  for (let i = 0; i < Math.round(0.03 * RATE); i++) {
    const t = i / RATE;
    low += 0.3 * (random() * 2 - 1 - low);
    samples[i] += 0.6 * Math.exp(-t / 0.006) * low;
  }
  for (const [start, frequency, length] of [[0.08, 1760, 0.06], [0.17, 2349.32, 0.1]]) {
    for (let i = Math.round(start * RATE); i < Math.round((start + length) * RATE); i++) {
      const t = i / RATE - start;
      const envelope = Math.min(1, t / 0.005, (length - t) / 0.005);
      samples[i] += 0.35 * envelope * (Math.sin(2 * Math.PI * frequency * t) + 0.15 * Math.sin(6 * Math.PI * frequency * t));
    }
  }
  writeSound("hud_on", samples);
}

// The vacuum toilets. A flush: the vacuum snatching the bowl empty, a sharp, loud hiss of rushing air that starts
// all at once, falling in pitch and fading as the pipe empties, over a short roar down the pipe; then the thunk of the
// valve shutting, a low knock with the click of the valve seating.
{
  const length = 0.85;
  const valve = 0.68;
  const samples = new Float64Array(Math.round(length * RATE));
  let low = 0;
  let band = 0;
  let roar = 0;
  let knock = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = random() * 2 - 1;
    // The hiss: white noise through a resonant band sweeping down from a shriek to a whoosh.
    const centre = 1100 + 5200 * Math.exp(-t / 0.22);
    const f = 2 * Math.sin((Math.PI * centre) / RATE);
    const high = white - low - 0.3 * band;
    band += f * high;
    low += f * band;
    roar += 0.03 * (white - roar);
    const rush = Math.min(1, t / 0.003) * (t < valve ? Math.exp(-Math.max(0, t - 0.25) / 0.15) : 0);
    samples[i] = rush * (band + 0.2 * high + 2.5 * roar);
    if (t >= valve) {
      const k = t - valve;
      knock += (2 * Math.PI * (55 + 45 * Math.exp(-k / 0.02))) / RATE;
      samples[i] += 0.9 * Math.exp(-k / 0.05) * Math.sin(knock) + (k < 0.006 ? 0.6 * white * (1 - k / 0.006) : 0);
    }
  }
  writeSound("toilet_flush", samples);
}
// Blue ice landing: the sharp crack of it breaking, a crackle of it splintering, and the tinkle of shards.
{
  const samples = new Float64Array(Math.round(0.7 * RATE));
  let previous = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = random() * 2 - 1;
    const high = white - previous;
    previous = white;
    const crack = t < 0.025 ? 3 * (1 - t / 0.025) * white : 0;
    const crackle = random() < 0.004 * Math.exp(-t / 0.12) ? (random() * 2 - 1) * 4 : 0;
    samples[i] = crack + 1.2 * high * Math.exp(-t / 0.06) + crackle;
  }
  for (let shard = 0; shard < 7; shard++) {
    bell(samples, 0.02 + random() * 0.25, 2800 + random() * 3000, 0.03 + random() * 0.04);
  }
  writeSound("blue_ice_crack", samples);
}
// Blue ice landing on someone: a cartoon bonk, a hollow knock dropping in pitch, with a click as it hits.
{
  const samples = new Float64Array(Math.round(0.35 * RATE));
  let phase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    phase += 2 * Math.PI * (330 + 290 * Math.exp(-t / 0.03)) / RATE;
    const envelope = Math.min(1, t / 0.002) * Math.exp(-t / 0.09);
    samples[i] = envelope * (Math.sin(phase) + 0.3 * Math.sin(2.7 * phase)) + (t < 0.004 ? (random() * 2 - 1) * (1 - t / 0.004) : 0);
  }
  writeSound("blue_ice_bonk", samples);
}

// The plug doors. Their pneumatic assist as a door starts to move: a hiss of air, noise with its lows taken out,
// bursting in and dying away as the pressure goes.
{
  const length = 0.9;
  const samples = new Float64Array(Math.round(length * RATE));
  let low = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = random() * 2 - 1;
    low += 0.35 * (white - low);
    samples[i] = (white - 0.6 * low) * Math.min(1, t * 60) * Math.exp(-t / 0.35);
  }
  writeSound("plug_door_hiss", samples);
}
// Its latches: a dull thud of the door against its stops with a metallic knock on top, a few heavy partials dying fast.
{
  const samples = new Float64Array(Math.round(0.4 * RATE));
  let phase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    phase += 2 * Math.PI * (55 + 70 * Math.exp(-t / 0.03)) / RATE;
    samples[i] = Math.exp(-t / 0.08) * Math.sin(phase) + (t < 0.006 ? (random() * 2 - 1) * (1 - t / 0.006) : 0);
  }
  bell(samples, 0.004, 780, 0.05);
  bell(samples, 0.004, 1290, 0.035);
  writeSound("plug_door_latch", samples);
}
// The door alarm while a door stands open in flight: a warning horn's whoop, twice in the clip, rising from a growl to
// a shriek and cut off, as a ship's or a plant's alarm horn sounds. A full sawtooth and square, all their harmonics up
// to where the horn speaker stops, through that speaker's two honking resonances and driven a little into clipping;
// then the cabin's hard walls echo it back. The echoes wrap round the clip's end, so played again and again it loops
// without a seam. No noise, so it never shifts the sounds after it.
{
  const length = 2.0;
  const whoops = 2;
  const whoop = 0.72;
  const from = 260;
  const to = 820;
  const dry = new Float64Array(Math.round(length * RATE));
  let phase = 0;
  for (let i = 0; i < dry.length; i++) {
    const t = (i / RATE) % (length / whoops);
    if (t >= whoop) {
      continue;
    }
    // Rising fast at first and slowing toward the top, as a horn's rotor spins up.
    const rise = 1 - Math.pow(1 - t / whoop, 2.2);
    const frequency = from + (to - from) * rise;
    phase += 2 * Math.PI * frequency / RATE;
    let tone = 0;
    for (let k = 1; k * frequency < 7000; k++) {
      tone += Math.sin(k * phase) * (k % 2 === 1 ? 1.25 : 0.8) / k;
    }
    const envelope = Math.min(1, t / 0.015) * Math.min(1, (whoop - t) / 0.02);
    dry[i] = envelope * tone;
  }
  // The horn speaker: its two resonances, each a two-pole band-pass, over some of the dry sound.
  const resonance = (input, centre, q) => {
    const out = new Float64Array(input.length);
    const w = 2 * Math.PI * centre / RATE;
    const alpha = Math.sin(w) / (2 * q);
    const [b0, b2, a0, a1, a2] = [alpha, -alpha, 1 + alpha, -2 * Math.cos(w), 1 - alpha];
    let [x1, x2, y1, y2] = [0, 0, 0, 0];
    for (let i = 0; i < input.length; i++) {
      const y = (b0 * input[i] + b2 * x2 - a1 * y1 - a2 * y2) / a0;
      [x2, x1, y2, y1] = [x1, input[i], y1, y];
      out[i] = y;
    }
    return out;
  };
  const honk = resonance(dry, 1150, 3);
  const bite = resonance(dry, 2600, 4);
  const horn = dry.map((s, i) => Math.tanh(1.6 * (0.35 * s + 1.4 * honk[i] + 0.9 * bite[i])));
  // The cabin: a few short echoes off its walls, wrapping round the clip's end.
  const samples = new Float64Array(horn.length);
  const echoes = [[0, 1], [0.019, 0.32], [0.031, 0.24], [0.047, 0.17], [0.071, 0.11], [0.103, 0.07]];
  for (const [delay, gain] of echoes) {
    const shift = Math.round(delay * RATE);
    for (let i = 0; i < samples.length; i++) {
      samples[(i + shift) % samples.length] += gain * horn[i];
    }
  }
  writeSound("door_alarm", samples, 0.8);
}

// Rubber ducks, with their own noise, so they never shift the sounds above. A squeak is air forced through the duck's
// reed: a buzzy tone, its pitch swooping up and back down, rattling and breathy. squeak() adds one to the samples.
const duckNoise = (() => {
  let duckSeed = 0x6475636b;
  return () => {
    duckSeed = (Math.imul(duckSeed, 1664525) + 1013904223) | 0;
    return (duckSeed >>> 0) / 4294967296;
  };
})();
function squeak(samples, start, length, pitch, swoop) {
  let phase = 0;
  let breath = 0;
  for (let i = Math.round(start * RATE); i < Math.min(samples.length, Math.round((start + length) * RATE)); i++) {
    const along = (i / RATE - start) / length;
    phase += (2 * Math.PI * pitch * (1 + swoop * Math.sin(Math.PI * along))) / RATE;
    const buzz = 2 * ((phase / (2 * Math.PI)) % 1) - 1;
    const rattle = 1 + 0.3 * Math.sin(2 * Math.PI * 70 * (i / RATE));
    breath += 0.3 * (duckNoise() * 2 - 1 - breath);
    const envelope = Math.min(1, along * 20) * Math.sqrt(Math.sin(Math.PI * along));
    samples[i] += envelope * (rattle * (0.6 * buzz + 0.5 * Math.sin(phase)) + 0.35 * breath);
  }
}
// The duck's squeak: squeezed and let go, two quick chirps, the second a little lower.
{
  const samples = new Float64Array(Math.round(0.3 * RATE));
  squeak(samples, 0, 0.11, 820, 0.3);
  squeak(samples, 0.15, 0.09, 700, 0.25);
  writeSound("rubber_duck_squeak", samples);
}
// The giant duck's squeak: one long squeeze, deep and huge, a second reed a fifth below it for its size.
{
  const samples = new Float64Array(Math.round(1.0 * RATE));
  squeak(samples, 0, 0.95, 300, 0.45);
  squeak(samples, 0.02, 0.9, 200, 0.4);
  writeSound("giant_rubber_duck_squeak", samples);
}

// The coffee machine, with its own noise, so it never shifts the sounds above. Each sound lasts as long as its step of
// the brew (CoffeeMachineBlock.Brew): 1.5 s grinding, 2.5 s pouring, 2.5 s steaming.
const coffeeNoise = (() => {
  let coffeeSeed = 0x63616665;
  return () => {
    coffeeSeed = (Math.imul(coffeeSeed, 1664525) + 1013904223) | 0;
    return ((coffeeSeed >>> 0) / 4294967296) * 2 - 1;
  };
})();
// The grinder: its motor spinning up to a buzzing whirr, a gear whine over it, and the crunch of the beans cracking,
// thick at first and thinning out as they are ground, the motor running a little faster as its load goes; then it
// spins down.
{
  const length = 1.5;
  const stop = 1.25;
  const samples = new Float64Array(Math.round(length * RATE));
  let phase = 0;
  let crunch = 0;
  let previous = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const spinUp = Math.min(1, t / 0.08);
    const spinDown = t < stop ? 1 : Math.exp(-(t - stop) / 0.07);
    const frequency = (95 + 15 * Math.min(1, t / stop)) * (0.4 + 0.6 * spinUp * spinDown);
    phase += (2 * Math.PI * frequency) / RATE;
    let motor = 0;
    for (let n = 1; n <= 8; n++) motor += Math.sin(n * phase) / n;
    const whine = 0.12 * Math.sin(14 * phase);
    const white = coffeeNoise();
    const high = white - previous;
    previous = white;
    if (t < stop && coffeeNoise() < (2 * 600 * (1 - 0.7 * t / stop)) / RATE - 1) crunch = 0.6 + 0.4 * Math.abs(coffeeNoise());
    crunch *= Math.exp(-1 / (0.003 * RATE));
    samples[i] = spinUp * spinDown * (0.45 * motor + whine) + 1.4 * crunch * high;
  }
  writeSound("coffee_grind", samples);
}
// The pump pouring: a vibratory pump's rattling 50 Hz buzz, hard at first as it primes and settling as the pressure
// builds; the coffee trickling into the cup over it, gurgling, from a little way in; then the pump stops and a last
// drop plinks into the cup.
{
  const length = 2.5;
  const stop = 2.2;
  const samples = new Float64Array(Math.round(length * RATE));
  let buzz = 0;
  let low = 0;
  let band = 0;
  let gurgle = 0;
  let rattle = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = coffeeNoise();
    const pulse = Math.sin(2 * Math.PI * 50 * t);
    buzz += 0.15 * (Math.sign(pulse) - buzz);
    // The pump's rattle: noise kept low, rattling with each stroke.
    rattle += 0.12 * (white - rattle);
    const running = Math.min(1, t / 0.02) * (t < stop ? 1 : Math.exp(-(t - stop) / 0.02));
    const prime = 1 + 0.5 * Math.exp(-t / 0.3);
    const pump = running * prime * (0.5 * buzz + 1.2 * rattle * Math.abs(pulse));
    // The trickle: noise through a band round 1.8 kHz, its loudness wandering as it gurgles.
    const f = 2 * Math.sin((Math.PI * 1800) / RATE);
    const high = white - low - 0.5 * band;
    band += f * high;
    low += f * band;
    gurgle += 0.0015 * (coffeeNoise() - gurgle);
    const trickle = t > 0.5 && t < stop + 0.05 ? Math.min(1, (t - 0.5) / 0.3) * (0.5 + 12 * Math.abs(gurgle)) : 0;
    samples[i] = pump + 0.5 * trickle * band;
  }
  bell(samples, stop + 0.12, 1250, 0.04);
  writeSound("coffee_pour", samples);
}
// The steam wand frothing the milk: a burst of steam, then a steady hiss with the milk's screech and chirp in it as air
// is drawn in, and low bubbling under it; then the valve shuts and the hiss dies.
{
  const length = 2.5;
  const stop = 2.3;
  const samples = new Float64Array(Math.round(length * RATE));
  let previous = 0;
  let low = 0;
  let band = 0;
  let chirp = 0;
  let bubble = 0;
  let bubblePhase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = coffeeNoise();
    const hiss = white - previous;
    previous = white;
    const centre = 1600 + 600 * Math.sin(2 * Math.PI * 3.1 * t) + 300 * Math.sin(2 * Math.PI * 7.3 * t);
    const f = 2 * Math.sin((Math.PI * centre) / RATE);
    const high = white - low - 0.15 * band;
    band += f * high;
    low += f * band;
    chirp += 0.0008 * (coffeeNoise() - chirp);
    if (coffeeNoise() > 1 - (2 * 30) / RATE) bubble = 0.8;
    bubble *= Math.exp(-1 / (0.02 * RATE));
    bubblePhase += (2 * Math.PI * (180 + 120 * bubble)) / RATE;
    const valve = Math.min(1, t / 0.02) * (t < stop ? 1 : Math.exp(-(t - stop) / 0.05));
    const burst = 1 + 0.8 * Math.exp(-t / 0.15);
    samples[i] = valve * (burst * 0.6 * hiss + 0.25 * Math.min(1, t / 0.4) * Math.abs(chirp) * 20 * band + 0.5 * bubble * Math.sin(bubblePhase));
  }
  writeSound("coffee_steam", samples);
}
// A drink ready: three soft rising chimes.
{
  const samples = new Float64Array(Math.round(0.9 * RATE));
  bell(samples, 0, 1046.5, 0.15);
  bell(samples, 0.14, 1318.5, 0.15);
  bell(samples, 0.28, 1568, 0.3);
  writeSound("coffee_ready", samples);
}

// The integral airstairs, with their own noise, so they never shift the sounds above. Their hydraulic power unit, as
// each move starts: the electric motor's hum spinning up and running, the gear pump's whine riding on it at nine times
// its speed, and the hiss of the oil through the valves; it spins down again at the end.
const stairNoise = (() => {
  let stairSeed = 0x73746169;
  return () => {
    stairSeed = (Math.imul(stairSeed, 1664525) + 1013904223) | 0;
    return (stairSeed >>> 0) / 4294967296;
  };
})();
{
  const length = 1.3;
  const samples = new Float64Array(Math.round(length * RATE));
  let phase = 0;
  let low = 0;
  let band = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const speed = Math.min(1, t / 0.15) * Math.min(1, (length - t) / 0.35);
    const hz = 95 + 70 * speed;
    phase += (2 * Math.PI * hz) / RATE;
    const hum = 2 * ((phase / (2 * Math.PI)) % 1) - 1;
    const whine = Math.sin(9 * phase) * (0.6 + 0.4 * Math.sin(phase));
    const white = stairNoise() * 2 - 1;
    low += 0.12 * (white - low);
    band += 0.5 * (white - band);
    samples[i] = speed * (0.45 * hum + 0.18 * whine + 0.35 * (band - low)) + 0.15 * speed * Math.sin(2 * phase);
  }
  writeSound("airstair_motor", samples);
}
// Their latches, as the hatch comes free and shuts and the stairs lock down: the spring-loaded bolt's sharp click, then
// the heavier clunk of the part it holds coming against its stop, ringing a little.
{
  const samples = new Float64Array(Math.round(0.45 * RATE));
  let previous = 0;
  let thump = 0;
  const clunk = 0.045;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = stairNoise() * 2 - 1;
    const high = white - previous;
    previous = white;
    samples[i] = t < 0.008 ? 1.4 * high * (1 - t / 0.008) : 0;
    if (t >= clunk) {
      const k = t - clunk;
      thump += (2 * Math.PI * (62 + 90 * Math.exp(-k / 0.025))) / RATE;
      samples[i] += Math.exp(-k / 0.07) * Math.sin(thump) + (k < 0.005 ? 0.8 * white * (1 - k / 0.005) : 0);
    }
  }
  bell(samples, 0.047, 1420, 0.06);
  bell(samples, 0.047, 2310, 0.04);
  writeSound("airstair_latch", samples);
}

// The evacuation slides, with their own noise, so they never shift the sounds above. Inflating, as long as the slide
// takes to come out: the bang of the gas bottle's valve firing, a deep thump with a crack of noise on it, then the
// roar of the aspirators gulping air into the slide, a loud rush that swells in at once and darkens as the slide fills,
// the fabric flapping as it unrolls, until it is firm with a soft thud and the last of the rush dies away.
const slideNoise = (() => {
  let slideSeed = 0x736c6964;
  return () => {
    slideSeed = (Math.imul(slideSeed, 1664525) + 1013904223) | 0;
    return (slideSeed >>> 0) / 4294967296;
  };
})();
{
  const length = 2.2;
  const samples = new Float64Array(Math.round(length * RATE));
  let low = 0;
  let band = 0;
  let thump = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = slideNoise() * 2 - 1;
    // The rush: darker the fuller the slide gets, its band of noise narrowing down.
    const dark = 0.05 + 0.25 * Math.exp(-t / 0.7);
    low += 0.02 * (white - low);
    band += dark * (white - band);
    const swell = Math.min(1, t / 0.06) * Math.min(1, Math.max(0, (1.95 - t) / 0.55));
    // The fabric flapping as it unrolls, between the bang and the slide coming down onto the ground: a quick
    // irregular beat on the rush.
    const flapping = t > 0.1 && t < 1.3 ? 0.5 * Math.max(0, Math.sin(2 * Math.PI * 14 * t + 3 * Math.sin(2 * Math.PI * 2.3 * t))) ** 4 : 0;
    samples[i] = swell * (1.4 * band + 0.9 * low) * (1 + flapping);
    // The valve's bang, and the thud as the slide comes firm.
    if (t < 0.25) {
      thump += (2 * Math.PI * (45 + 50 * Math.exp(-t / 0.03))) / RATE;
      samples[i] += 0.9 * Math.exp(-t / 0.07) * Math.sin(thump) + (t < 0.02 ? 1.0 * white * (1 - t / 0.02) : 0);
    }
    const settle = t - 1.8;
    if (settle >= 0 && settle < 0.3) {
      samples[i] += 0.7 * Math.exp(-settle / 0.06) * Math.sin(2 * Math.PI * 70 * settle);
    }
  }
  writeSound("slide_inflate", samples);
}
// Deflating: the gas let out, a strong hiss of air through the relief valves falling away as the pressure goes, the
// slide sagging with a few soft flaps of fabric.
{
  const length = 1.8;
  const samples = new Float64Array(Math.round(length * RATE));
  let low = 0;
  let previous = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = slideNoise() * 2 - 1;
    const high = white - previous;
    previous = white;
    low += (0.08 + 0.2 * Math.exp(-t / 0.5)) * (white - low);
    const pressure = Math.min(1, t / 0.04) * Math.exp(-t / 0.6);
    const sag = Math.max(0, Math.sin(2 * Math.PI * 2.5 * t)) ** 6 * Math.exp(-t / 0.8);
    samples[i] = pressure * (0.5 * high + 1.6 * low) + 0.4 * sag * low;
  }
  writeSound("slide_deflate", samples);
}
// Someone setting off down a slide: the swish of them sliding over the fabric, a band of noise sweeping up as they
// pick up speed and fading away down the lane.
{
  const length = 0.8;
  const samples = new Float64Array(Math.round(length * RATE));
  let low = 0;
  let band = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = slideNoise() * 2 - 1;
    const sweep = 0.05 + 0.3 * Math.sin((Math.PI * t) / length);
    low += sweep * (white - low);
    band += (sweep * 0.3) * (low - band);
    const envelope = Math.min(1, t / 0.08) * Math.exp(-Math.max(0, t - 0.25) / 0.2);
    samples[i] = envelope * (low - band);
  }
  writeSound("slide_whoosh", samples);
}

// The landing gear, with its own noise, so it never shifts the sounds above. Heard from the cabin as on a real
// airliner. Going up: the uplocks' clunk as the doors open, the roar of the air past them, the hydraulic pumps moaning
// as they lift the legs, the wheels braked to a stop in a rumble that flutters and dies, the legs thumping into their
// bays and the doors shutting on them, and the airliner gone quiet. Coming down: the clunk of the uplocks letting go,
// the air roaring in through the open doors and buffeting the legs, the dampers hissing as the legs drop, the heavy
// thunk of the downlocks, the doors closing, and the rumble of the gear out in the airstream. Each is timed to the
// legs' swing in GearBayBlockEntity and its renderer: the doors take the first and last 15% of it.
const gearNoise = (() => {
  let gearSeed = 0x67656172;
  return () => {
    gearSeed = (Math.imul(gearSeed, 1664525) + 1013904223) | 0;
    return (gearSeed >>> 0) / 4294967296;
  };
})();
/** A weight of metal striking home at a time in seconds: a low partial falling in pitch as it dies, cracking as it hits. */
function gearThud(samples, start, weight) {
  let phase = 0;
  const begin = Math.round(start * RATE);
  const end = Math.min(samples.length, begin + Math.round(0.6 * RATE));
  for (let i = begin; i < end; i++) {
    const t = (i - begin) / RATE;
    phase += (2 * Math.PI * (38 + 120 * Math.exp(-t / 0.02))) / RATE;
    const crack = t < 0.012 ? (gearNoise() * 2 - 1) * (1 - t / 0.012) : 0;
    samples[i] += weight * (Math.exp(-t / 0.12) * Math.sin(phase) + 0.6 * crack);
  }
}
/** The ring of the struck metal on top of a thud: a few partials of the note dying fast, this loud. */
function gearRing(samples, start, frequency, gain) {
  const begin = Math.round(start * RATE);
  const end = Math.min(samples.length, begin + Math.round(0.4 * RATE));
  for (let i = begin; i < end; i++) {
    const t = (i - begin) / RATE;
    const envelope = Math.min(1, t * 600) * Math.exp(-t / 0.06);
    samples[i] += gain * envelope * (Math.sin(2 * Math.PI * frequency * t) + 0.4 * Math.sin(2 * Math.PI * frequency * 2.76 * t));
  }
}
/**
 * The roar of the air past the open doors and the legs out in it, as loud as level(t) says: low noise with a band of
 * it on top, gusting slowly as the air buffets the legs.
 */
function gearAirflow(samples, level) {
  let low = 0;
  let band = 0;
  let gust = 0.8;
  let gustTo = 0.8;
  for (let i = 0; i < samples.length; i++) {
    const white = gearNoise() * 2 - 1;
    low += 0.03 * (white - low);
    band += 0.2 * (white - band);
    if (i % Math.round(0.05 * RATE) === 0) {
      gustTo = 0.6 + 0.4 * gearNoise();
    }
    gust += 0.0006 * (gustTo - gust);
    samples[i] += level(i / RATE) * gust * (3 * low + 0.35 * (band - low));
  }
}
/**
 * The hydraulic pumps working the legs from one time to another, in seconds: a moaning tone rising from one pitch to
 * another as the pressure builds, with its harmonics and a slight waver, and the hiss of the oil through the valves.
 */
function gearHydraulics(samples, from, to, startHz, endHz, gain) {
  let phase = 0;
  let previous = 0;
  const span = to - from;
  for (let i = Math.round(from * RATE); i < Math.min(samples.length, Math.round(to * RATE)); i++) {
    const t = i / RATE - from;
    const envelope = Math.min(1, t / 0.4) * Math.min(1, (span - t) / 0.6);
    const hz = startHz + (endHz - startHz) * Math.min(1, t / (0.6 * span));
    phase += (2 * Math.PI * hz * (1 + 0.004 * Math.sin(2 * Math.PI * 3.1 * t))) / RATE;
    const tone = Math.sin(phase) + 0.45 * Math.sin(2 * phase) + 0.25 * Math.sin(3 * phase) + 0.12 * Math.sin(5 * phase);
    const white = gearNoise() * 2 - 1;
    const hiss = white - previous;
    previous = white;
    samples[i] += gain * envelope * (0.5 * tone + 0.12 * hiss);
  }
}
/** The wheels braked to a stop as they go up: a low rumble fluttering with each turn of the tires, slowing and dying. */
function gearWheelsBraked(samples, from, to, gain) {
  let low = 0;
  let turn = 0;
  const span = to - from;
  for (let i = Math.round(from * RATE); i < Math.min(samples.length, Math.round(to * RATE)); i++) {
    const t = i / RATE - from;
    const spinning = 1 - t / span;
    turn += (2 * Math.PI * (1 + 14 * spinning)) / RATE;
    low += 0.01 * ((gearNoise() * 2 - 1) - low);
    samples[i] += gain * spinning * spinning * Math.min(1, t / 0.1) * (0.6 + 0.4 * Math.sin(turn)) * 8 * low;
  }
}
/** Rises smoothly from 0 to 1 between two times, in seconds. */
function gearRamp(t, from, to) {
  const k = Math.max(0, Math.min(1, (t - from) / (to - from)));
  return k * k * (3 - 2 * k);
}
{
  // Going up, eight seconds as the legs swing, and a little after for the cabin to settle.
  const samples = new Float64Array(Math.round(8.6 * RATE));
  gearThud(samples, 0, 0.9);
  gearRing(samples, 0.004, 640, 0.25);
  gearAirflow(samples, (t) => 0.12 + 0.88 * gearRamp(t, 0, 1.2) - 0.76 * gearRamp(t, 6.8, 7.9) - 0.24 * gearRamp(t, 7.9, 8.6));
  gearHydraulics(samples, 0.3, 7.0, 330, 470, 0.55);
  gearWheelsBraked(samples, 0.4, 3.0, 0.9);
  gearThud(samples, 6.8, 1.4);
  gearThud(samples, 6.95, 0.8);
  gearRing(samples, 6.804, 410, 0.3);
  gearThud(samples, 7.9, 1.0);
  gearRing(samples, 7.904, 720, 0.2);
  writeSound("gear_retract", samples);
}
{
  // Coming down, six seconds as the legs swing, and the rumble of the gear out in the airstream fading after.
  const samples = new Float64Array(Math.round(6.8 * RATE));
  gearThud(samples, 0, 0.8);
  gearRing(samples, 0.004, 560, 0.25);
  gearAirflow(samples, (t) => 0.15 + 1.15 * gearRamp(t, 0, 0.9) - 0.35 * gearRamp(t, 5.1, 6.0) - 0.95 * gearRamp(t, 6.1, 6.8));
  gearHydraulics(samples, 0.8, 5.2, 300, 240, 0.35);
  gearThud(samples, 5.1, 1.6);
  gearRing(samples, 5.104, 380, 0.35);
  gearThud(samples, 5.25, 0.7);
  gearThud(samples, 6.0, 0.9);
  gearRing(samples, 6.004, 700, 0.2);
  writeSound("gear_extend", samples);
}
// The gear horn on the flight deck, coming down to land with the gear up: a harsh, buzzing beep, short enough to
// sound again and again on the lever's beat (every half second), as a warning horn does.
{
  const samples = new Float64Array(Math.round(0.4 * RATE));
  let phase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    phase += (2 * Math.PI * 720 * (1 + 0.01 * Math.sin(2 * Math.PI * 30 * t))) / RATE;
    const square = Math.sign(Math.sin(phase)) * 0.6 + 0.4 * Math.sin(phase);
    samples[i] = square * Math.min(1, t / 0.01) * Math.min(1, (0.32 - t) / 0.02) * (t < 0.32 ? 1 : 0);
  }
  writeSound("gear_horn", samples, 0.7);
}

// Dropping into an air pocket, with its own noise, so it never shifts the sounds above: the air rushing past as the
// cabin falls away, everything loose in it rattling (the overhead bins, the trolleys, the galley's carts), then the
// thud through the airframe as the air catches it again, and the rattle settling.
const pocketNoise = (() => {
  let pocketSeed = 0x706f636b;
  return () => {
    pocketSeed = (Math.imul(pocketSeed, 1664525) + 1013904223) | 0;
    return (pocketSeed >>> 0) / 4294967296;
  };
})();
{
  const samples = new Float64Array(Math.round(1.8 * RATE));
  const caught = 0.7;
  let low = 0;
  let band = 0;
  let ring = 0;
  let ringPhase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = pocketNoise() * 2 - 1;
    low += 0.04 * (white - low);
    band += 0.3 * (white - band);
    // The rush of air, swelling as the cabin falls and dying once the air has caught it.
    const rush = Math.min(1, t / 0.15) * Math.exp(-Math.max(0, t - caught) / 0.25);
    samples[i] += rush * (1.8 * low + 0.5 * (band - low));
    // The rattle: sharp knocks, thick while it falls, sparser as it settles, each ringing a moment.
    const rattleRate = t < caught ? 70 : 70 * Math.exp(-(t - caught) / 0.3);
    if (pocketNoise() < rattleRate / RATE) {
      ring = 0.5 + 0.5 * pocketNoise();
    }
    ringPhase += (2 * Math.PI * 2300) / RATE;
    samples[i] += 0.35 * ring * Math.sin(ringPhase) + 0.25 * ring * white;
    ring *= 0.996;
  }
  gearThud(samples, caught, 1.6);
  gearRing(samples, caught + 0.004, 300, 0.2);
  writeSound("air_pocket", samples);
}

// The flight deck's voice, for the callouts and the cabin announcements: a calm, very even, faintly synthetic woman's
// voice, the deadpan politeness of a laboratory's AI test instructor. Windows' own speech synthesis says each line;
// its intonation is then mostly flattened and pulled to the semitones around one note, as if tuned by machine (see
// tunePitch), and FFmpeg colours it: a quiet monotone buzz on that note under it, as from a vocoder, crisp high-mids,
// a faint metallic sweep, the short echo of a hard little room and a touch of digital sheen. Tune it all here.
const VOICE = {
  // The speech synthesis voice. Windows PowerShell's System.Speech sees only the desktop voices; Zira is the woman's.
  name: "Microsoft Zira Desktop",
  // How fast it speaks, as SSML prosody: unhurried.
  rate: "-5%",
  // The pause a "|" in a line makes, in milliseconds.
  pause: 650,
  // The note its pitch is centred on, in hertz: B flat below middle C, a little above the voice's own.
  note: 233.08,
  // How much of the voice's own rise and fall is taken out: 0 none, 1 all of it, leaving a robot's monotone.
  flatten: 0.75,
  // How far what is left of it is pulled to the nearest semitone: 0 not at all, 1 into hard steps.
  quantise: 0.7,
  // How loud the monotone buzz is under the voice.
  buzz: 0.35,
  // FFmpeg's filters after the buzz, in order.
  filters: [
    "highpass=f=140",
    // Crisp high-mids.
    "equalizer=f=2800:t=q:w=1.4:g=3",
    // A faint metallic sweep.
    "flanger=delay=0.8:depth=0.5:regen=30:width=35:speed=0.25",
    // A hard little room.
    "aecho=0.9:0.5:13|29:0.18|0.1",
    // Digital sheen.
    "aexciter=amount=1:drive=5:blend=0:freq=6500:ceil=14000",
  ],
};
// The callouts: short and clear, the warnings with a word of manners. Each must be said within CALLOUT_LONGEST.
const CALLOUTS = {
  callout_v1: "V one.",
  callout_rotate: "Rotate.",
  callout_positive_rate: "Positive rate. Well done.",
  callout_100: "One hundred.",
  callout_50: "Fifty.",
  callout_40: "Forty.",
  callout_30: "Thirty.",
  callout_20: "Twenty.",
  callout_10: "Ten.",
  callout_retard: "Retard. Retard.",
  callout_bank_angle: "Bank angle. Please do not do that.",
  callout_pull_up: "Pull up. This is not a test.",
};
// The cabin announcements (see Announcement), each opening with the cabin chime. A "|" is a pause. The ones only the
// flight deck's announcement panel makes are for the youngest passengers: short, plain words, and nothing to worry about.
const ANNOUNCEMENTS = {
  announcement_welcome: "Hello, and welcome aboard. | This is your captain speaking. | Please sit back, relax, and enjoy the flight.",
  announcement_takeoff: "Welcome aboard. | Cabin crew, please be seated for take-off.",
  announcement_turbulence: "We are flying through some bumpy air. | Please sit down and fasten your seatbelt. | Bumps are normal. | Wobbling is also normal.",
  announcement_snacks: "The cabin crew are now serving drinks and snacks. | Today there are cookies. | Lots of cookies.",
  announcement_window: "Please look out of the window. | The world is down there. | Everything is very small. | Wave hello.",
  announcement_playtime: "The seatbelt signs are off. | The pool and the gym are now open. | Please do not run beside the pool.",
  announcement_sleep: "We are dimming the cabin lights. | It is time for a little sleep. | Good night, | and sweet dreams.",
  announcement_landing: "Please return to your seats. | Cabin crew, please be seated for landing.",
  announcement_arrival: "Welcome to your destination. Please remain seated until the aircraft has come to a complete stop. | It has. | You may now leave.",
};
/**
 * The longest a callout may run, in seconds: half a second less than a warning takes to repeat
 * (Callouts.WARNING_REPEAT), for a breath between.
 */
const CALLOUT_LONGEST = 3.5;
/**
 * The longest an announcement may run, chime and all, in seconds: CabinAnnouncements.LONGEST gives each this long.
 * The take-off one has to be over sooner, before "V one" down the runway: at the default rotation speed the autopilot
 * gets to V1 some eight and a half seconds after it is set to take off.
 */
const ANNOUNCEMENT_LONGEST = 15;
const TAKEOFF_LONGEST = 8;
/**
 * Quiet before an announcement's chime, in seconds, so the seatbelt signs switched on with it ding first; and how long
 * after the chime starts the voice comes in, as its second tone dies away.
 */
const ANNOUNCEMENT_LEAD = 0.6;
const ANNOUNCEMENT_VOICE_AT = 1.5;
/**
 * The chime's peak under an announcement, as a share of the voice's, and how long it fades out for, in seconds, where
 * its last ring is cut off.
 */
const ANNOUNCEMENT_CHIME = 0.5;
const ANNOUNCEMENT_CHIME_FADE = 0.3;
/**
 * A line's level, as a share of its peak, under which its start and end count as quiet; the quiet is trimmed off but
 * for this many seconds either side, the soft start of an "f" or "th" kept.
 */
const QUIET = 0.003;
const QUIET_KEPT = 0.05;

/** Reads a 16-bit mono PCM WAV's samples, from -1 to 1, checking it is at the generator's rate. */
function readWav(path) {
  const file = readFileSync(path);
  let samples = null;
  for (let offset = 12; offset + 8 <= file.length;) {
    const id = file.toString("ascii", offset, offset + 4);
    const size = file.readUInt32LE(offset + 4);
    if (id === "fmt " && (file.readUInt16LE(offset + 10) !== 1 || file.readUInt32LE(offset + 12) !== RATE || file.readUInt16LE(offset + 22) !== 16)) {
      throw new Error(`${path}: not 16-bit mono at ${RATE} Hz`);
    }
    if (id === "data") {
      samples = new Float64Array(Math.floor(size / 2));
      for (let i = 0; i < samples.length; i++) {
        samples[i] = file.readInt16LE(offset + 8 + i * 2) / 32768;
      }
    }
    offset += 8 + size + (size % 2);
  }
  return samples;
}

/**
 * The voice's pitch tuned by machine: its own rise and fall mostly taken out and what is left pulled to the semitones
 * round VOICE.note, keeping how long it takes to say and where its formants sit (pitch-synchronous overlap-add). The
 * pitch is found through each voiced stretch as the lag the speech best repeats at, a pitch mark is put on each
 * period's largest peak, and each period, cut out two periods long under a Hann window, is laid down again at the
 * period of the pitch wanted. Unvoiced sounds go through at a steady spacing as they are.
 */
function tunePitch(x) {
  const hop = Math.round(0.01 * RATE);
  const frame = Math.round(0.03 * RATE);
  const shortest = Math.floor(RATE / 450);
  const longest = Math.ceil(RATE / 90);
  let peak = 0;
  x.forEach((s) => (peak = Math.max(peak, Math.abs(s))));
  const found = [];
  for (let start = 0; start + frame + longest < x.length; start += hop) {
    let energy = 0;
    for (let i = 0; i < frame; i++) {
      energy += x[start + i] ** 2;
    }
    let best = 0;
    let bestLag = 0;
    if (Math.sqrt(energy / frame) > 0.03 * peak) {
      for (let lag = shortest; lag <= longest; lag++) {
        let cross = 0;
        let lagged = 0;
        for (let i = 0; i < frame; i++) {
          cross += x[start + i] * x[start + i + lag];
          lagged += x[start + i + lag] ** 2;
        }
        const r = cross / Math.sqrt(energy * lagged + 1e-12);
        if (r > best) {
          best = r;
          bestLag = lag;
        }
      }
    }
    found.push(best > 0.7 ? bestLag : 0);
  }
  // The middle of five neighbouring voiced frames, against slips of an octave.
  const periods = found.map((period, i) => {
    const near = found.slice(Math.max(0, i - 2), i + 3).filter((p) => p).sort((a, b) => a - b);
    return period && near[Math.floor(near.length / 2)];
  });
  const voiced = periods.filter((p) => p).sort((a, b) => a - b);
  if (voiced.length === 0) {
    return x;
  }
  const middle = RATE / voiced[Math.floor(voiced.length / 2)];
  const periodAt = (at) => periods[Math.min(periods.length - 1, Math.max(0, Math.floor((at - frame / 2) / hop)))] ?? 0;

  const unvoicedStep = Math.round(0.006 * RATE);
  const marks = [];
  for (let at = 0; at < x.length;) {
    const period = periodAt(at);
    if (!period) {
      marks.push({ at, period: unvoicedStep, voiced: false });
      at += unvoicedStep;
      continue;
    }
    let mark = at;
    const reach = Math.round(0.3 * period);
    for (let i = Math.max(0, at - reach); i <= Math.min(x.length - 1, at + reach); i++) {
      if (Math.abs(x[i]) > Math.abs(x[mark])) {
        mark = i;
      }
    }
    const last = marks[marks.length - 1];
    if (last && mark <= last.at) {
      mark = last.at + Math.round(period / 2);
    }
    marks.push({ at: mark, period, voiced: true });
    at = mark + period;
  }

  const tuned = new Float64Array(x.length);
  const weight = new Float64Array(x.length);
  let k = 0;
  for (let at = marks[0].at; at < x.length;) {
    while (k + 1 < marks.length && Math.abs(marks[k + 1].at - at) <= Math.abs(marks[k].at - at)) {
      k++;
    }
    const mark = marks[k];
    for (let i = -mark.period; i <= mark.period; i++) {
      const from = mark.at + i;
      const to = at + i;
      if (from >= 0 && from < x.length && to >= 0 && to < x.length) {
        const w = 0.5 + 0.5 * Math.cos((Math.PI * i) / (mark.period + 1));
        tuned[to] += w * x[from];
        weight[to] += w;
      }
    }
    let step = mark.period;
    if (mark.voiced) {
      const semitones = Math.max(-8, Math.min(8, 12 * Math.log2(RATE / mark.period / middle))) * (1 - VOICE.flatten);
      const stepped = semitones + VOICE.quantise * (Math.round(semitones) - semitones);
      step = Math.round(RATE / (VOICE.note * 2 ** (stepped / 12)));
    }
    at += step;
  }
  // Divided by the windows' overlap, so a period laid down more often than it came does not come out louder.
  return tuned.map((s, i) => s / Math.max(1, weight[i]));
}

/** The voice's colouring (see VOICE): the monotone buzz under it on VOICE.note, then the filters. */
function colour(input, output) {
  // A phase vocoder's frames with their phases zeroed ring at the frame rate: hop the frames at the note.
  const hop = Math.round(RATE / VOICE.note);
  const buzz = `afftfilt=real='hypot(re,im)':imag='0':win_size=${4 * hop}:overlap=0.75`;
  const graph = `asplit[voice][robot];[robot]${buzz}[buzz];[voice][buzz]amix=inputs=2:weights='1 ${VOICE.buzz}':normalize=0,${VOICE.filters.join(",")}`;
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", input, "-filter_complex", graph, "-ac", "1", "-ar", `${RATE}`, "-c:a", "pcm_s16le", output]);
}

/** The line with the quiet before and after it cut away, but for a moment either side, and its tail faded out. */
function trim(samples) {
  let peak = 0;
  samples.forEach((s) => (peak = Math.max(peak, Math.abs(s))));
  const margin = Math.round(QUIET_KEPT * RATE);
  const start = Math.max(0, samples.findIndex((s) => Math.abs(s) > QUIET * peak) - margin);
  const end = Math.min(samples.length, samples.findLastIndex((s) => Math.abs(s) > QUIET * peak) + margin);
  const kept = samples.slice(start, end);
  for (let i = 0; i < margin && i < kept.length; i++) {
    kept[kept.length - 1 - i] *= i / margin;
  }
  return kept;
}

/** An announcement: quiet, the chime, then the voice coming in as the chime dies away. */
function announcement(voice) {
  let peak = 0;
  voice.forEach((s) => (peak = Math.max(peak, Math.abs(s))));
  let chimePeak = 0;
  CHIME.forEach((s) => (chimePeak = Math.max(chimePeak, Math.abs(s))));
  const chimeAt = Math.round(ANNOUNCEMENT_LEAD * RATE);
  const voiceAt = chimeAt + Math.round(ANNOUNCEMENT_VOICE_AT * RATE);
  const samples = new Float64Array(Math.max(chimeAt + CHIME.length, voiceAt + voice.length));
  const fade = ANNOUNCEMENT_CHIME_FADE * RATE;
  CHIME.forEach((s, i) => (samples[chimeAt + i] += (s / chimePeak) * ANNOUNCEMENT_CHIME * peak * Math.min(1, (CHIME.length - i) / fade)));
  voice.forEach((s, i) => (samples[voiceAt + i] += s));
  return samples;
}

/**
 * Says every line with the speech synthesis in the given voice (its name, rate and pause, as in VOICE), all in one go,
 * into WAVs in the scratch folder; false, saying what is left as it is, if the voice is not installed.
 */
function speak(lines, voice, what) {
  const ssml = (text) => {
    const escaped = text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    const paused = escaped.replace(/\s*\|\s*/g, ` <break time='${voice.pause}ms'/> `);
    return `<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'><prosody rate='${voice.rate}'>${paused}</prosody></speak>`;
  };
  const said = join(scratch, "lines.json");
  writeFileSync(said, JSON.stringify(Object.entries(lines).map(([name, text]) => ({ wav: join(scratch, `${name}.said.wav`), ssml: ssml(text) }))));
  const quote = (text) => `'${text.replace(/'/g, "''")}'`;
  const script = [
    "$ProgressPreference = 'SilentlyContinue'",
    "Add-Type -AssemblyName System.Speech",
    "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
    `if (-not ($s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Name -eq ${quote(voice.name)} })) { exit 3 }`,
    `$s.SelectVoice(${quote(voice.name)})`,
    `$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(${RATE}, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)`,
    `foreach ($line in (Get-Content -Raw -Encoding UTF8 ${quote(said)} | ConvertFrom-Json)) {`,
    "  $s.SetOutputToWaveFile($line.wav, $format)",
    "  $s.SpeakSsml($line.ssml)",
    "  $s.SetOutputToNull()",
    "}",
    "$s.Dispose()",
  ].join("\n");
  try {
    execFileSync("powershell", ["-NoProfile", "-NonInteractive", "-EncodedCommand", Buffer.from(script, "utf16le").toString("base64")]);
    return true;
  } catch (error) {
    if (error.status === 3) {
      console.log(`The voice "${voice.name}" is not installed: leaving ${what} as they are`);
      return false;
    }
    throw error;
  }
}

if (process.platform !== "win32") {
  console.log("Not on Windows: leaving the callouts and announcements as they are");
} else if (speak({ ...CALLOUTS, ...ANNOUNCEMENTS }, VOICE, "the callouts and announcements")) {
  for (const name of [...Object.keys(CALLOUTS), ...Object.keys(ANNOUNCEMENTS)]) {
    const tuned = join(scratch, `${name}.tuned.wav`);
    const coloured = join(scratch, `${name}.coloured.wav`);
    writeWav(tuned, tunePitch(readWav(join(scratch, `${name}.said.wav`))));
    colour(tuned, coloured);
    const voice = trim(readWav(coloured));
    const samples = name in ANNOUNCEMENTS ? announcement(voice) : voice;
    const longest = name === "announcement_takeoff" ? TAKEOFF_LONGEST : name in ANNOUNCEMENTS ? ANNOUNCEMENT_LONGEST : CALLOUT_LONGEST;
    if (samples.length > longest * RATE) {
      throw new Error(`${name} runs ${(samples.length / RATE).toFixed(1)} s, longer than ${longest} s: shorten it or speed the voice up`);
    }
    writeSound(name, samples);
  }
}

// Air traffic control on the flight deck's radio (see AtcRadio), a man's voice, so it is never taken for the flight
// deck's own, heard through a VHF receiver: each press of the transmit button opens with the squelch's click and a
// breath of the carrier's hiss; the voice is squeezed into the radio's narrow band, compressed and clipped hard as a
// transmitter's audio is and a little bit-crushed, over the hiss, with the odd crackle and the signal fading a touch in
// and out; and as the button is let go the carrier drops, the squelch tail rushes in for a moment and it shuts with a
// click. Other aircraft on the frequency are the same voice pitched up or down, further off: over more hiss and more
// crackle. The carrier clips are presses of the button with nobody speaking. Tune it all here.
const RADIO = {
  // Windows PowerShell's System.Speech sees only the desktop voices; David is the man's. Controllers talk briskly.
  voice: { name: "Microsoft David Desktop", rate: "+12%", pause: 300 },
  // The transmitter's filters on the voice: the radio's band, compression and clipping, a little bit crushing, the
  // band again for what the clipping added, and a nasal honk in the middle of it.
  transmitter: [
    "highpass=f=330:poles=2",
    "highpass=f=330:poles=2",
    "lowpass=f=2800:poles=2",
    "lowpass=f=2800:poles=2",
    "acompressor=threshold=-30dB:ratio=9:attack=2:release=70:makeup=14dB",
    "asoftclip=type=atan:threshold=0.45:oversample=2",
    "acrusher=bits=9:mode=log:aa=1:mix=0.35",
    "highpass=f=300:poles=2",
    "lowpass=f=3100:poles=2",
    "equalizer=f=1600:t=q:w=1.3:g=4",
  ],
  // The receiver's small speaker, over all of it, hiss, crackles and squelch too.
  receiver: ["highpass=f=260:poles=2", "highpass=f=260:poles=2", "lowpass=f=3400:poles=2", "lowpass=f=3400:poles=2"],
  // Who talks: the pitch, in semitones; the carrier's hiss, as a share of the voice's peak; and crackles a second.
  speakers: {
    atc: { pitch: 0, hiss: 0.09, crackles: 2.5 },
    skyhopper: { pitch: 2.5, hiss: 0.15, crackles: 5 },
    cargo: { pitch: -3, hiss: 0.14, crackles: 5 },
    nobody: { pitch: 0, hiss: 0.25, crackles: 12 },
  },
  // A press of the button, in seconds: the squelch opening, the hiss before the voice and after it, and the squelch
  // tail; and a quick click of it, as a pilot acknowledges with.
  timing: {
    press: { open: 0.05, lead: 0.15, after: 0.08, tail: 0.22 },
    click: { open: 0.03, lead: 0.03, after: 0.02, tail: 0.12 },
  },
  // How loud the squelch's clicks and the tail are, as a share of the voice's peak.
  squelch: 0.9,
  tail: 0.75,
  // How far the signal fades in and out, and the quiet between one press and the next, in seconds.
  fading: 0.1,
  gap: 0.45,
  clickGap: 0.12,
  // The peak each clip is written at: the voices as the flight deck's, the carrier clips well under them.
  level: 0.85,
  carrierLevel: 0.4,
};
/**
 * What is said, each clip one or more presses of the button: who talks and what they say (see AtcRadio for when each is
 * said). The call sign is spelt for the voice to say it right.
 */
const ATC = {
  atc_cleared_for_takeoff: [["atc", "Aero large three eight zero, runway two seven, cleared for takeoff."]],
  atc_contact_departure: [["atc", "Aero large three eight zero, contact departure, one two four decimal five. Good day."]],
  atc_radar_contact: [["atc", "Aero large three eight zero, departure, radar contact. Climb and maintain flight level three five zero."]],
  atc_cleared_to_land: [["atc", "Aero large three eight zero, wind two seven zero at eight, runway two seven, cleared to land."]],
  atc_go_around: [["atc", "Aero large three eight zero, roger, go around. Climb straight ahead."]],
  atc_taxi_to_gate: [["atc", "Aero large three eight zero, welcome home! Taxi to the gate via alpha. Thanks for flying with us."]],
  atc_contact_center: [["atc", "Aero large three eight zero, contact center, one three two decimal four five. Good day."]],
  atc_center_check_in: [["atc", "Aero large three eight zero, center, good day. Radar contact, maintain flight level three five zero."]],
  atc_traffic_two_oclock: [["atc", "Aero large three eight zero, traffic, two o'clock, five miles, opposite direction, two thousand feet below."]],
  atc_traffic_ten_oclock: [["atc", "Aero large three eight zero, traffic, ten o'clock, eight miles, crossing left to right, a heavy jet."]],
  atc_light_turbulence: [["atc", "Aero large three eight zero, light turbulence reported ahead. Expect a few bumps."]],
  atc_smooth_ride: [["atc", "Aero large three eight zero, smooth ride reported ahead. Enjoy the view."]],
  atc_geese: [["atc", "Aero large three eight zero, caution, a flock of geese reported at your altitude, heading south for the winter."]],
  atc_balloon: [["atc", "Aero large three eight zero, a hot air balloon, one o'clock, low. The people in the basket are waving at you."]],
  atc_skyhopper_hello: [
    ["skyhopper", "Center, Skyhopper two one, we have the big Aero large in sight. Hello up there!"],
    ["atc", "Skyhopper two one, roger. Aero large three eight zero, Skyhopper says hello."],
  ],
  atc_cargo_climb: [
    ["cargo", "Center, Cargo seven seven, request flight level three niner zero."],
    ["atc", "Cargo seven seven, climb and maintain flight level three niner zero."],
  ],
};
/**
 * The longest each kind of clip may run, in seconds, AtcRadio giving each this long: the take-off clearance, which the
 * autopilot holds the brakes for (so the shorter the better), any other one press, a press and its answer, and a
 * carrier clip.
 */
const ATC_CLEARANCE_LONGEST = 5;
const ATC_LONGEST = 8;
const ATC_EXCHANGE_LONGEST = 13;
const ATC_CARRIER_LONGEST = 2;

// The radio's own noise, so it never shifts the sounds above.
const radioNoise = (() => {
  let radioSeed = 0x76686672;
  return () => {
    radioSeed = (Math.imul(radioSeed, 1664525) + 1013904223) | 0;
    return ((radioSeed >>> 0) / 4294967296) * 2 - 1;
  };
})();

/** A burst of noise dying away over the given seconds, added to the samples from the given sample on. */
function noiseBurst(samples, start, seconds, level) {
  const length = Math.round(seconds * RATE);
  for (let i = 0; i < length && start + i < samples.length; i++) {
    samples[start + i] += level * Math.exp((-4 * i) / length) * radioNoise();
  }
}

/** One press of the transmit button as the receiver hears it, timed as given, around the voice (its peak 1), if any. */
function press(voice, speaker, timing) {
  const open = Math.round(timing.open * RATE);
  const voiceAt = open + Math.round(timing.lead * RATE);
  const carrier = voiceAt + voice.length + Math.round(timing.after * RATE);
  const tail = Math.round(timing.tail * RATE);
  const samples = new Float64Array(carrier + tail);
  let fade = 0;
  for (let i = 0; i < carrier; i++) {
    fade += 0.0004 * (radioNoise() - fade);
    const said = i >= voiceAt && i < voiceAt + voice.length ? voice[i - voiceAt] : 0;
    samples[i] = (1 + RADIO.fading * fade * 30) * (said + speaker.hiss * radioNoise());
  }
  // The squelch opening: its click, and a breath of noise settling into the hiss.
  samples[0] += RADIO.squelch;
  samples[1] -= RADIO.squelch * 0.7;
  noiseBurst(samples, 0, timing.open, RADIO.squelch * 0.6);
  for (let i = 0; i < carrier; i++) {
    if (radioNoise() > 1 - (2 * speaker.crackles) / RATE) {
      noiseBurst(samples, i, 0.002 + 0.006 * Math.abs(radioNoise()), 0.15 + 0.6 * Math.abs(radioNoise()) ** 3);
    }
  }
  // Let go: the squelch tail rushing in as the carrier drops, then the squelch shutting.
  for (let i = 0; i < tail; i++) {
    const t = i / tail;
    samples[carrier + i] += RADIO.tail * Math.min(1, t * 40) * (1 - t ** 3) * radioNoise();
  }
  samples[samples.length - 2] += RADIO.squelch * 0.5;
  return samples;
}

/**
 * Presses of the button one after another, the gap given between, through the receiver's speaker, written as the
 * named sound at the level given; fails if it runs longer than it may.
 */
function receive(name, presses, gap, level, longest) {
  const quiet = Math.round(gap * RATE);
  const joined = new Float64Array(presses.reduce((sum, p) => sum + p.length, 0) + quiet * (presses.length - 1));
  let at = 0;
  for (const p of presses) {
    joined.set(p, at);
    at += p.length + quiet;
  }
  if (joined.length > longest * RATE) {
    throw new Error(`${name} runs ${(joined.length / RATE).toFixed(1)} s, longer than ${longest} s: shorten it or speed the voice up`);
  }
  let peak = 0;
  joined.forEach((s) => (peak = Math.max(peak, Math.abs(s))));
  const sent = join(scratch, `${name}.sent.wav`);
  const heard = join(scratch, `${name}.heard.wav`);
  writeWav(sent, Array.from(joined, (s) => (s / peak) * 0.9));
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", sent, "-af", RADIO.receiver.join(","), "-ac", "1", "-ar", `${RATE}`, "-c:a", "pcm_s16le", heard]);
  writeSound(name, readWav(heard), level);
}

/** A line said, through the transmitter, pitched first for another aircraft's pilot; its peak 1. */
function transmit(name, pitch) {
  const said = trim(readWav(join(scratch, `${name}.said.wav`)));
  let peak = 0;
  said.forEach((s) => (peak = Math.max(peak, Math.abs(s))));
  const input = join(scratch, `${name}.trimmed.wav`);
  const output = join(scratch, `${name}.transmitted.wav`);
  writeWav(input, Array.from(said, (s) => (s / peak) * 0.9));
  const ratio = 2 ** (pitch / 12);
  const shift = pitch ? [`asetrate=${Math.round(RATE * ratio)}`, `aresample=${RATE}`, `atempo=${(1 / ratio).toFixed(5)}`] : [];
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", input, "-af", [...shift, ...RADIO.transmitter].join(","), "-ac", "1", "-ar", `${RATE}`, "-c:a", "pcm_s16le", output]);
  const voice = readWav(output);
  let voicePeak = 0;
  voice.forEach((s) => (voicePeak = Math.max(voicePeak, Math.abs(s))));
  return voice.map((s) => s / voicePeak);
}

// The carrier clips: someone pressing the button and saying nothing, and a pilot's two quick clicks.
receive("atc_static_open", [press(new Float64Array(Math.round(0.6 * RATE)), RADIO.speakers.nobody, RADIO.timing.press)], RADIO.gap,
  RADIO.carrierLevel, ATC_CARRIER_LONGEST);
receive("atc_static_clicks", [0, 1].map(() => press(new Float64Array(0), RADIO.speakers.nobody, RADIO.timing.click)), RADIO.clickGap,
  RADIO.carrierLevel, ATC_CARRIER_LONGEST);
const atcLines = Object.fromEntries(Object.entries(ATC).flatMap(([name, presses]) => presses.map(([, text], i) => [`${name}.${i}`, text])));
if (process.platform !== "win32") {
  console.log("Not on Windows: leaving air traffic control's lines as they are");
} else if (speak(atcLines, RADIO.voice, "air traffic control's lines")) {
  for (const [name, presses] of Object.entries(ATC)) {
    const longest = name === "atc_cleared_for_takeoff" ? ATC_CLEARANCE_LONGEST : presses.length > 1 ? ATC_EXCHANGE_LONGEST : ATC_LONGEST;
    receive(name, presses.map(([who], i) => press(transmit(`${name}.${i}`, RADIO.speakers[who].pitch), RADIO.speakers[who], RADIO.timing.press)),
      RADIO.gap, RADIO.level, longest);
  }
}

rmSync(scratch, { recursive: true, force: true });
console.log("Sounds written to", output);
