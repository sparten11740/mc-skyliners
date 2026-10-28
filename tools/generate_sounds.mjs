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
// - the self-destruct's siren and bang, from recordings kept in tools/audio (see AUDIO), as Ultrasplosive makes its
//   nuke's;
// - the flight deck's voice, its callouts and cabin announcements, spoken by Kokoro, an open speech synthesis model,
//   and played through the cabin's loudspeakers (see VOICE), on any system once kokoro-js is installed with npm
//   install --prefix tools (the model itself is fetched on the first run);
// - air traffic control over the radio, spoken by Windows' own speech synthesis (System.Speech) and then reworked (see
//   RADIO), so that part runs on Windows only.
// Deterministic apart from the voices: Windows' are whatever it ships, and Kokoro's may differ by a hair from one kind
// of processor to another.
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

/**
 * A random sequence of its own, from a seed, for a sound added after the others: drawing from the shared one would
 * change every sound after it in the file.
 */
function ownRandom(start) {
  let own = start;
  return () => {
    own = (own + 0x6d2b79f5) | 0;
    let value = Math.imul(own ^ (own >>> 15), 1 | own);
    value = (value + Math.imul(value ^ (value >>> 7), 61 | value)) ^ value;
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

/** Writes samples, from -1 to 1, as a 16-bit mono PCM WAV at the generator's rate or the one given, in hertz. */
function writeWav(path, samples, rate = RATE) {
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
  header.writeUInt32LE(rate, 24);
  header.writeUInt32LE(rate * 2, 28);
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

/** A resonance of a horn's throat or speaker: the samples through a two-pole band-pass round its centre, in hertz. */
function resonance(input, centre, q) {
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
  // The horn speaker: its two resonances over some of the dry sound.
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

// The zeppelin's horn: a warship's menacing blast. Two low reeds a tritone apart, the most unsettled of intervals,
// and another a hair off the lower one, beating against it in a slow throb; every harmonic up to where the trumpet
// stops, through its throat's honking resonances and driven hard into clipping, so it snarls, with a reed's growl
// fluttering through it; and an octave under them a rumble, felt more than heard. It swells in slowly on a rush of
// air, holds, and ends in a foghorn's grunt, its pitch sagging away as the air runs out. Its echo off the ground far
// below is the same blast muffled, softer and smeared, a tail on its end as from rolling country.
{
  const hornRandom = ownRandom(0x486f726e);
  const length = 3.6;
  const notes = [61.7, 63.6, 87.3];
  const voices = [1, 0.7, 0.8];
  const swell = 0.5;
  const grunt = 2.75;
  const gruntLength = 0.85;
  const blast = (t) => Math.min(1, t / swell) ** 1.5 * (t < grunt ? 1 : Math.max(0, 1 - (t - grunt) / gruntLength) ** 2);
  // Flat as it catches, up to pitch over the swell, then sagging a fifth by the end of the grunt.
  const pitch = (t) => (t < swell ? 0.95 + 0.05 * (t / swell) : 1) * (t < grunt ? 1 : 1 - 0.33 * Math.min(1, (t - grunt) / gruntLength));
  const dry = new Float64Array(Math.round(length * RATE));
  const rumble = new Float64Array(dry.length);
  const phases = notes.map(() => 0);
  let air = 0;
  for (let i = 0; i < dry.length; i++) {
    const t = i / RATE;
    const bend = pitch(t) * (1 + 0.006 * Math.sin(2 * Math.PI * 3.2 * t));
    let reed = 0;
    notes.forEach((note, n) => {
      phases[n] += (2 * Math.PI * note * bend) / RATE;
      for (let k = 1; k * note < 4000; k++) {
        reed += (voices[n] * Math.sin(phases[n] * k)) / k ** 0.9;
      }
    });
    const growl = 1 + 0.3 * Math.sin(2 * Math.PI * 27 * t);
    air += 0.08 * (hornRandom() * 2 - 1 - air);
    dry[i] = reed * growl * blast(t) + air * (0.6 * Math.exp(-t / 0.4) + 0.15 * blast(t));
    rumble[i] = Math.sin(phases[0] / 2) * blast(t);
  }
  const throat = resonance(dry, 420, 2.5);
  const bite = resonance(dry, 1500, 3);
  const horn = dry.map((s, i) => Math.tanh(0.35 * s + 0.9 * throat[i] + 0.8 * bite[i]) + 0.25 * rumble[i]);
  writeSound("zeppelin_horn", horn);
  const echoLength = 4.4;
  const echo = new Float64Array(Math.round(echoLength * RATE));
  let low = 0;
  for (let i = 0; i < echo.length; i++) {
    const at = [0, 0.18, 0.41].reduce((sum, delay, k) => {
      const j = i - Math.round(delay * RATE);
      return sum + (j >= 0 && j < horn.length ? horn[j] * [1, 0.45, 0.2][k] : 0);
    }, 0);
    low += 0.06 * (at - low);
    echo[i] = low;
  }
  writeSound("zeppelin_horn_echo", echo, 0.6);
}

// Inside a zeppelin's hull (see the client's HullAmbience): the great hollow space's own sound, a seamless loop. Wind
// over the cover booms low through the hull, swelling and easing in slow gusts; the air inside rings at a few deep
// notes, as a cathedral's does; the cover's fabric flutters in the gusts; now and then, far off, a girder groans; and
// all of it echoes long off the cover and the cells. Every part is worked round the loop twice and the second turn
// kept, so its end runs on into its start without a seam. And the girders' groans heard on their own, nearer, from
// somewhere round whoever is aboard: three of them, each a stick-slip of metal on metal ringing through the hull.
{
  const hullRandom = ownRandom(0x48756c6c);
  const length = 24;
  const n = Math.round(length * RATE);
  const noise = Float64Array.from({ length: 2 * n }, (_, i) => (i < n ? hullRandom() * 2 - 1 : 0));
  noise.copyWithin(n, 0, n);
  // The gusts: a slow swell made of whole turns round the loop, so it meets itself at the seam.
  const gust = (t) => 0.62 + 0.24 * Math.sin((2 * Math.PI * 2 * t) / length + 0.4) + 0.14 * Math.sin((2 * Math.PI * 5 * t) / length + 1.3);
  const lowPass = (signal, cutoff) => {
    const share = 1 - Math.exp((-2 * Math.PI * cutoff) / RATE);
    let low = 0;
    return signal.map((s) => (low += share * (s - low)));
  };
  const rumble = lowPass(lowPass(noise, 140), 140);
  const modes = [[41, 1], [58, 0.8], [73, 0.6], [97, 0.45]].map(([note, gain]) => resonance(noise, note, 40).map((s) => s * gain));
  const fabric = resonance(noise, 900, 1.5);
  const dry = new Float64Array(2 * n);
  for (let i = 0; i < 2 * n; i++) {
    const t = (i % n) / RATE;
    const flutter = Math.max(0, gust(t) - 0.66) * (1 + 0.5 * Math.sin(2 * Math.PI * 13 * t));
    const ringing = modes.reduce((sum, mode) => sum + mode[i], 0);
    dry[i] = gust(t) * (2.2 * rumble[i] + 3 * ringing) + 0.25 * flutter * fabric[i];
  }
  for (const [at, gain] of [[6.2, 0.05], [15.8, 0.035]]) {
    girderGroan(dry, at, ownRandom(0x47726f61 + Math.round(at * 10)), gain);
    girderGroan(dry, at + length, ownRandom(0x47726f61 + Math.round(at * 10)), gain);
  }
  writeSound("zeppelin_hull", hullEcho(dry, 0.45).slice(n), 0.6);
  for (let variant = 1; variant <= 3; variant++) {
    const groan = new Float64Array(Math.round(3.5 * RATE));
    girderGroan(groan, 0.05, ownRandom(0x43726561 + variant), 1);
    writeSound(`zeppelin_creak_${variant}`, hullEcho(groan, 0.4), 0.7);
  }
}

/**
 * A girder groaning in a zeppelin's hull, from a time in seconds: metal slipping on metal in a quickening and then
 * slowing run of jerks, each ringing the girder at two of its notes, the run's length, rate and notes its own.
 */
function girderGroan(samples, start, own, gain) {
  const length = 0.7 + own() * 0.6;
  const rateFrom = 9 + own() * 8;
  const rateTo = 25 + own() * 25;
  const note = 260 + own() * 320;
  const jerks = new Float64Array(samples.length);
  let phase = 0;
  for (let i = Math.round(start * RATE); i < Math.min(samples.length, Math.round((start + length) * RATE)); i++) {
    const share = (i / RATE - start) / length;
    phase += (rateFrom + (rateTo - rateFrom) * Math.sin(Math.PI * share)) / RATE;
    if (phase >= 1) {
      phase -= 1;
      jerks[i] = Math.sin(Math.PI * share) * (0.6 + 0.4 * own());
    }
  }
  const low = resonance(jerks, note, 9);
  const high = resonance(jerks, note * 2.37, 12);
  for (let i = 0; i < samples.length; i++) {
    samples[i] += gain * (low[i] + 0.5 * high[i]);
  }
}

/**
 * The echo of a zeppelin's hull: four long, damped echoes off the cover and the cells, the high notes dying first,
 * smeared by two diffusers, mixed over so much of the dry sound. Some four seconds before it dies away.
 */
function hullEcho(dry, wet) {
  const combs = [[0.113, 0.83], [0.137, 0.81], [0.161, 0.79], [0.187, 0.77]];
  const damping = 0.45;
  const echo = new Float64Array(dry.length);
  for (const [delay, feedback] of combs) {
    const gap = Math.round(delay * RATE);
    const line = new Float64Array(dry.length);
    let low = 0;
    for (let i = 0; i < dry.length; i++) {
      low += damping * ((i >= gap ? line[i - gap] : 0) - low);
      line[i] = dry[i] + feedback * low;
      echo[i] += line[i] / combs.length;
    }
  }
  let diffused = echo;
  for (const [delay, spread] of [[0.031, 0.7], [0.011, 0.7]]) {
    const gap = Math.round(delay * RATE);
    const input = diffused;
    const out = new Float64Array(input.length);
    for (let i = 0; i < input.length; i++) {
      out[i] = -spread * input[i] + (i >= gap ? input[i - gap] + spread * out[i - gap] : 0);
    }
    diffused = out;
  }
  return dry.map((s, i) => s * (1 - wet) + diffused[i] * wet);
}

// A zeppelin's speed boost (see BoostButtonBlockEntity): her engines roaring up to full ahead. A whoosh of air sweeping
// up as they take hold, a deep roar of rushing exhaust under it swelling in a second and easing off over three, and
// the engines' own growl rising in pitch as they wind up, all of it echoing off the hull.
{
  const boostRandom = ownRandom(0x426f6f73);
  const length = 4;
  const n = Math.round(length * RATE);
  const noise = Float64Array.from({ length: n }, () => boostRandom() * 2 - 1);
  const swell = (t) => Math.min(1, t / 0.9) ** 1.5 * Math.exp(-Math.max(0, t - 0.9) / 1.4) * Math.min(1, (length - t) / 0.8);
  let low = 0;
  let sweepLow = 0;
  let sweepBand = 0;
  let phase = 0;
  const dry = new Float64Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    low += 0.02 * (noise[i] - low);
    // A band of the noise sweeping up from 300 to 2,500 Hz over the first second, a two-pole state variable filter.
    const centre = 300 + 2200 * Math.min(1, t / 1.1) ** 0.7;
    const tune = 2 * Math.sin((Math.PI * centre) / RATE);
    sweepLow += tune * sweepBand;
    const high = noise[i] - sweepLow - 0.5 * sweepBand;
    sweepBand += tune * high;
    const whoosh = sweepBand * Math.exp(-Math.max(0, t - 0.6) / 0.5) * Math.min(1, t / 0.15);
    phase += (2 * Math.PI * (55 + 45 * Math.min(1, t / 1.2))) / RATE;
    const growl = Math.tanh(2.5 * (Math.sin(phase) + 0.5 * Math.sin(2 * phase) + 0.3 * Math.sin(3 * phase))) * (1 + 0.3 * Math.sin(2 * Math.PI * 31 * t));
    dry[i] = swell(t) * (3.2 * low + 0.35 * growl) + 0.6 * whoosh;
  }
  writeSound("speed_boost", hullEcho(dry, 0.25), 0.85);
}

// The sounds of the night's sound pass, each with a noise of its own so none shifts another's: the zeppelin's engine
// cars, her telegraphs and wheels, the controls' end stops, the sky crane, the flight deck's clamps, a parachute
// opening, the airliner's touchdown, the trim car, the overhead bins and the searchlights.
/** A heavy strike at a time in seconds: a low partial falling in pitch as it dies, with a crack of noise as it hits. */
function thunk(samples, start, low, weight, noise) {
  let phase = 0;
  const begin = Math.round(start * RATE);
  const end = Math.min(samples.length, begin + Math.round(0.5 * RATE));
  for (let i = begin; i < end; i++) {
    const t = (i - begin) / RATE;
    phase += (2 * Math.PI * (low + 3 * low * Math.exp(-t / 0.02))) / RATE;
    const crack = t < 0.01 ? (noise() * 2 - 1) * (1 - t / 0.01) : 0;
    samples[i] += weight * (Math.exp(-t / 0.1) * Math.sin(phase) + 0.5 * crack);
  }
}
/** A short metallic click at a time in seconds: a few milliseconds of noise through a resonance round its pitch. */
function click(samples, start, pitch, gain, noise) {
  const burst = new Float64Array(Math.round(0.025 * RATE));
  for (let i = 0; i < Math.round(0.004 * RATE); i++) {
    burst[i] = (noise() * 2 - 1) * (1 - i / (0.004 * RATE));
  }
  const rung = resonance(burst, pitch, 6);
  const begin = Math.round(start * RATE);
  for (let i = 0; i < rung.length && begin + i < samples.length; i++) {
    samples[begin + i] += gain * rung[i] * 6;
  }
}
/** A brass gong struck at a time in seconds: its note with a bell's inharmonic partials, dying away. */
function gong(samples, start, frequency, decay, gain) {
  for (let i = Math.round(start * RATE); i < samples.length; i++) {
    const t = i / RATE - start;
    const envelope = Math.min(1, t * 800) * Math.exp(-t / decay);
    samples[i] += gain * envelope * (Math.sin(2 * Math.PI * frequency * t) + 0.5 * Math.sin(2 * Math.PI * frequency * 2.76 * t) * Math.exp(-t / 0.15)
      + 0.25 * Math.sin(2 * Math.PI * frequency * 5.4 * t) * Math.exp(-t / 0.06));
  }
}
/** Crossfades a sound rendered a little long, its tail over its head, into a seamless loop this many seconds long. */
function seamless(samples, seconds, fadeSeconds) {
  const length = Math.round(seconds * RATE);
  const fade = Math.round(fadeSeconds * RATE);
  const loop = samples.slice(0, length);
  for (let i = 0; i < fade; i++) {
    const w = i / fade;
    loop[i] = samples[length + i] * (1 - w) + samples[i] * w;
  }
  return loop;
}
// A zeppelin's engine car (see ZeppelinEngineSounds): a big Maybach diesel thrumming, its cylinders' firing a pulse
// train at 75 a second through its exhaust stack's resonances, a deep sub of the crankshaft under it, and the
// propeller's beat, a whup of air nine times a second over a hiss of propwash. Every part repeats a whole number of
// times in its four seconds, so the loop has no seam.
{
  const noise = ownRandom(0x4d617962);
  const seconds = 4;
  const n = Math.round((seconds + 0.5) * RATE);
  const pulses = new Float64Array(n);
  const firing = 75;
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const within = (t * firing) % 1;
    pulses[i] = Math.exp(-within * 9) * (1 + 0.15 * Math.sin(2 * Math.PI * 1.75 * t));
  }
  const stack = resonance(pulses, 180, 3);
  const bark = resonance(pulses, 420, 5);
  const samples = new Float64Array(n);
  let wash = 0;
  let warm = 0;
  let warmer = 0;
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    wash += 0.05 * ((noise() * 2 - 1) - wash);
    const beat = 0.65 + 0.35 * Math.sin(2 * Math.PI * 9 * t);
    const sub = Math.sin(2 * Math.PI * 37.5 * t) + 0.4 * Math.sin(2 * Math.PI * 75 * t);
    // Through two one-pole low-passes at some 1.2 kHz: a big diesel thrums, it does not hiss.
    warm += 0.16 * (Math.tanh(1.6 * stack[i] + 0.7 * bark[i]) + 1.8 * wash * beat - warm);
    warmer += 0.16 * (warm - warmer);
    samples[i] = warmer + 0.45 * sub;
  }
  writeSound("zeppelin_engine", seamless(samples, seconds, 0.5), 0.8);
}
// An engine-order telegraph rung (see TelegraphBlock): its handle's detents clacking over, then its brass gong struck
// twice, ding-ding; and its answer from an engine car, the repeater's single lower ring.
{
  const noise = ownRandom(0x54656c65);
  const samples = new Float64Array(Math.round(1.4 * RATE));
  click(samples, 0, 3000, 0.6, noise);
  click(samples, 0.06, 2600, 0.5, noise);
  gong(samples, 0.12, 1250, 0.5, 1);
  gong(samples, 0.4, 1250, 0.6, 1);
  writeSound("telegraph_ring", samples, 0.8);
  const answer = new Float64Array(Math.round(1.2 * RATE));
  click(answer, 0, 2400, 0.5, noise);
  gong(answer, 0.05, 940, 0.6, 1);
  writeSound("telegraph_answer", answer, 0.7);
}
// A ship's wheel turned a notch (see HelmBlock): two hollow knocks of its wooden spokes, and its steering chain's
// rattle.
{
  const noise = ownRandom(0x48656c6d);
  const samples = new Float64Array(Math.round(0.45 * RATE));
  const knock = new Float64Array(samples.length);
  for (const at of [0, 0.07]) {
    for (let i = 0; i < Math.round(0.015 * RATE); i++) {
      knock[Math.round(at * RATE) + i] += (noise() * 2 - 1) * (1 - i / (0.015 * RATE));
    }
  }
  const wood = resonance(knock, 700, 8);
  for (let i = 0; i < samples.length; i++) {
    samples[i] = 4 * wood[i];
  }
  for (let k = 0; k < 10; k++) {
    click(samples, 0.1 + k * 0.025, 1800 + 300 * noise(), 0.25 * (1 - k / 10), noise);
  }
  writeSound("helm_notch", samples, 0.7);
}
// A control pushed past its last notch, a wheel, telegraph, lever or winch at its stop: a dull wooden-and-iron thud.
{
  const noise = ownRandom(0x53746f70);
  const samples = new Float64Array(Math.round(0.5 * RATE));
  thunk(samples, 0, 110, 1, noise);
  click(samples, 0, 900, 0.3, noise);
  writeSound("control_stop", samples, 0.7);
}
// The sky crane's winch turning (see CraneWinchBlockEntity): a quarter second of its ratchet, three pawl clicks over
// the drum's low rumble and the rope's creak, played over and over while it hoists or lowers; and its hook's latch,
// catching or letting go, an iron clunk ringing, and a jingle of chain.
{
  const noise = ownRandom(0x52617463);
  const samples = new Float64Array(Math.round(0.25 * RATE));
  let rumble = 0;
  let creak = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    rumble += 0.01 * ((noise() * 2 - 1) - rumble);
    creak = Math.sin(2 * Math.PI * 300 * t + 2 * Math.sin(2 * Math.PI * 23 * t));
    samples[i] = 6 * rumble * (0.7 + 0.3 * Math.sin(2 * Math.PI * 40 * t)) + 0.05 * creak;
  }
  for (const at of [0.01, 0.093, 0.176]) {
    click(samples, at, 2400, 0.9, noise);
  }
  writeSound("crane_ratchet", samples, 0.7);
  const latch = new Float64Array(Math.round(0.9 * RATE));
  thunk(latch, 0, 80, 1, noise);
  gong(latch, 0, 900, 0.12, 0.3);
  for (let k = 0; k < 8; k++) {
    click(latch, 0.05 + 0.03 * k + 0.02 * noise(), 3200 + 800 * noise(), 0.35 * (1 - k / 8), noise);
  }
  writeSound("crane_latch", latch, 0.8);
}
// The flight deck's docking clamps (see DeckClampBlockEntity): locking, a hydraulic hiss of their rams into the steel
// thunk of their jaws closing; releasing, the jaws knocked open and the hiss letting the pressure go.
{
  const noise = ownRandom(0x436c616d);
  const hiss = (samples, from, to, rising) => {
    let previous = 0;
    for (let i = Math.round(from * RATE); i < Math.round(to * RATE); i++) {
      const t = (i / RATE - from) / (to - from);
      const white = noise() * 2 - 1;
      const high = white - previous;
      previous = white;
      samples[i] += 0.5 * high * (rising ? Math.min(1, t * 3) : Math.min(1, (1 - t) * 3)) * Math.min(1, t * 20);
    }
  };
  const lock = new Float64Array(Math.round(1.1 * RATE));
  hiss(lock, 0, 0.4, true);
  thunk(lock, 0.38, 70, 1, noise);
  gong(lock, 0.38, 520, 0.15, 0.3);
  gong(lock, 0.38, 1180, 0.08, 0.15);
  writeSound("clamp_lock", lock, 0.85);
  const release = new Float64Array(Math.round(1.1 * RATE));
  thunk(release, 0, 90, 0.8, noise);
  gong(release, 0, 640, 0.1, 0.2);
  hiss(release, 0.05, 0.75, false);
  writeSound("clamp_release", release, 0.8);
}
// A parachute's canopy opening (see Parachute): the rustle of it unpacking, sweeping up, then the whump of the air
// filling it with a crack of cloth, and two flaps as it settles.
{
  const noise = ownRandom(0x43616e6f);
  const samples = new Float64Array(Math.round(1.2 * RATE));
  let low = 0;
  let band = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = noise() * 2 - 1;
    const centre = 400 + 2100 * Math.min(1, t / 0.35);
    const tune = 2 * Math.sin((Math.PI * centre) / RATE);
    low += tune * band;
    band += tune * (white - low - 0.6 * band);
    samples[i] = 0.5 * band * Math.min(1, t / 0.05) * Math.max(0, 1 - t / 0.4);
  }
  thunk(samples, 0.38, 60, 1.1, noise);
  for (const [at, gain] of [[0.62, 0.45], [0.86, 0.3]]) {
    const flap = new Float64Array(Math.round(0.08 * RATE));
    for (let i = 0; i < flap.length; i++) {
      flap[i] = (noise() * 2 - 1) * Math.sin(Math.PI * i / flap.length);
    }
    const cloth = resonance(flap, 350, 1.5);
    for (let i = 0; i < cloth.length; i++) {
      samples[Math.round(at * RATE) + i] += gain * 3 * cloth[i];
    }
  }
  writeSound("canopy_open", samples, 0.8);
}
// The airliner's main gear touching down (see CameraShake): the thump of the legs taking her weight and the tyres'
// chirp as they spin up, sweeping down from 2.4 to 1.2 kHz, with a short rumble of the runway after it.
{
  const noise = ownRandom(0x546f7563);
  const samples = new Float64Array(Math.round(1.3 * RATE));
  thunk(samples, 0, 55, 1, noise);
  let phase = 0;
  let rumble = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    if (t < 0.18) {
      phase += (2 * Math.PI * (2400 - 1200 * (t / 0.18))) / RATE;
      samples[i] += 0.35 * Math.sin(phase) * Math.sin(Math.PI * t / 0.18) * (1 + 0.5 * (noise() * 2 - 1));
    }
    rumble += 0.02 * ((noise() * 2 - 1) - rumble);
    samples[i] += 4 * rumble * Math.min(1, t / 0.1) * Math.exp(-t / 0.6);
  }
  writeSound("touchdown", samples, 0.85);
}
// The trim car rolling along its rail a block (see TrimWinchBlockEntity): a rumble of its wheels bumping over the
// rail's joints, and the clink of its haul chain.
{
  const noise = ownRandom(0x5472696d);
  const samples = new Float64Array(Math.round(0.6 * RATE));
  let low = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    low += 0.015 * ((noise() * 2 - 1) - low);
    samples[i] = 6 * low * (0.6 + 0.4 * Math.max(0, Math.sin(2 * Math.PI * 6 * t))) * Math.sin(Math.PI * t / 0.6);
  }
  for (const at of [0.05, 0.22, 0.39]) {
    click(samples, at, 3000, 0.4, noise);
  }
  writeSound("trim_car_roll", samples, 0.7);
}
// An overhead bin (see OverheadBinBlockEntity): opening, its plastic latch snapping and the lid swinging up on its
// damped hinge; closing, the lid's hollow thunk shut and the latch catching.
{
  const noise = ownRandom(0x42696e73);
  const open = new Float64Array(Math.round(0.6 * RATE));
  click(open, 0, 4000, 0.8, noise);
  let previous = 0;
  for (let i = Math.round(0.03 * RATE); i < open.length; i++) {
    const t = i / RATE - 0.03;
    const white = noise() * 2 - 1;
    open[i] += 0.12 * (white - previous) * Math.sin(Math.PI * Math.min(1, t / 0.45));
    previous = white;
  }
  writeSound("bin_open", open, 0.6);
  const close = new Float64Array(Math.round(0.5 * RATE));
  thunk(close, 0, 250, 0.9, noise);
  click(close, 0.02, 4200, 0.6, noise);
  writeSound("bin_close", close, 0.6);
}
// A searchlight switched on (see SearchlightHandleBlockEntity): its contactor's clunk, the arc striking up in a
// crackle, settling into a hum at the mains' hundred hertz with its harmonics.
{
  const noise = ownRandom(0x53656172);
  const samples = new Float64Array(Math.round(1.4 * RATE));
  thunk(samples, 0, 90, 0.8, noise);
  for (let i = Math.round(0.05 * RATE); i < samples.length; i++) {
    const t = i / RATE - 0.05;
    const crackle = noise() < 0.02 ? (noise() * 2 - 1) * Math.exp(-t / 0.3) : 0;
    const hum = Math.min(1, t / 0.4) * (Math.sin(2 * Math.PI * 100 * t) + 0.4 * Math.sin(2 * Math.PI * 200 * t) + 0.2 * Math.sin(2 * Math.PI * 300 * t));
    samples[i] += 0.8 * crackle + 0.15 * hum * Math.min(1, (1.35 - t) / 0.3);
  }
  writeSound("searchlight_on", samples, 0.7);
}
// A zeppelin's boosters cutting out after a speed boost (see BoostButtonBlockEntity): their roar falling away in a
// downward whoosh, a pop as the flames go out, and a hiss of the thrusters cooling.
{
  const noise = ownRandom(0x43757420);
  const samples = new Float64Array(Math.round(2.2 * RATE));
  let low = 0;
  let band = 0;
  let previous = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const white = noise() * 2 - 1;
    const centre = 2000 * Math.exp(-t / 0.4) + 150;
    const tune = 2 * Math.sin((Math.PI * centre) / RATE);
    low += tune * band;
    band += tune * (white - low - 0.7 * band);
    const fall = Math.exp(-t / 0.5);
    const hiss = (white - previous) * 0.06 * Math.min(1, t / 0.3) * Math.exp(-t / 1.2);
    previous = white;
    samples[i] = 0.8 * band * fall + hiss;
  }
  thunk(samples, 0.25, 70, 0.5, noise);
  writeSound("booster_cutout", samples, 0.75);
}

// A finger drawn across a misted window (see MistyWindows): the squeak of skin on wet glass, a stick-slip chirp
// wavering round two kilohertz, with the softest hiss of the film it wipes.
{
  const noise = ownRandom(0x47617373);
  const samples = new Float64Array(Math.round(0.22 * RATE));
  let phase = 0;
  let previous = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const pitch = 1900 + 350 * Math.sin(2 * Math.PI * 7 * t) + 200 * noise();
    phase += (2 * Math.PI * pitch) / RATE;
    const stick = 0.5 + 0.5 * Math.sign(Math.sin(2 * Math.PI * 38 * t));
    const white = noise() * 2 - 1;
    const hiss = (white - previous) * 0.05;
    previous = white;
    samples[i] = (0.4 * Math.sin(phase) * stick + hiss) * Math.sin(Math.PI * t / 0.22);
  }
  writeSound("glass_squeak", samples, 0.5);
}
// The autopilot letting go (see AutopilotBlockEntity): its disconnect warning, three quick rising tones played twice, a
// little reedy, as an airliner's flight deck sounds when its autopilot is switched off.
{
  const samples = new Float64Array(Math.round(1.15 * RATE));
  const tones = [784, 988, 1175];
  for (let round = 0; round < 2; round++) {
    tones.forEach((frequency, k) => {
      const start = round * 0.5 + k * 0.13;
      for (let i = Math.round(start * RATE); i < Math.round((start + 0.12) * RATE); i++) {
        const t = i / RATE - start;
        const envelope = Math.min(1, t * 300) * Math.min(1, (0.12 - t) * 200);
        const reed = Math.sin(2 * Math.PI * frequency * t) + 0.3 * Math.sin(2 * Math.PI * 3 * frequency * t) + 0.15 * Math.sin(2 * Math.PI * 5 * frequency * t);
        samples[i] += envelope * reed;
      }
    });
  }
  writeSound("autopilot_off", samples, 0.6);
}

// The airliner's landing gear lever thrown (see GearLeverBlockEntity): pulled out of its detent with a click, swung
// through its gate and dropped home with a heavy clunk of the knob's stop.
{
  const noise = ownRandom(0x4765616c);
  const samples = new Float64Array(Math.round(0.6 * RATE));
  click(samples, 0, 2200, 0.5, noise);
  let previous = 0;
  for (let i = Math.round(0.02 * RATE); i < Math.round(0.16 * RATE); i++) {
    const white = noise() * 2 - 1;
    samples[i] += 0.05 * (white - previous);
    previous = white;
  }
  thunk(samples, 0.16, 140, 1, noise);
  click(samples, 0.16, 1500, 0.4, noise);
  writeSound("gear_lever", samples, 0.75);
}

// Sleigh bells, rung from a festive zeppelin's engines as they turn: a strap of little round bells shaken twice, each
// shake a quick scatter of bright high strikes, a few bells a little out of tune with each other.
{
  const jingle = ownRandom(0x4a696e67);
  const samples = new Float64Array(Math.round(1.1 * RATE));
  const bells = [2350, 2620, 2890, 3170, 3480];
  for (const shake of [0, 0.42]) {
    for (let strike = 0; strike < 14; strike++) {
      const at = shake + jingle() * 0.16;
      bell(samples, at, bells[Math.floor(jingle() * bells.length)] * (0.99 + jingle() * 0.02), 0.09 + jingle() * 0.05);
    }
  }
  writeSound("sleigh_bells", samples, 0.7);
}

// The flag halyard's pulley as the colours run up the staff, hand over hand: each pull a rustle of rope through the
// hands and a squeak of the sheave at the top of the staff, a little higher or lower each time.
{
  const pulley = ownRandom(0x666c6167);
  const length = 2.1;
  const pulls = 8;
  const pullLength = 0.22;
  const samples = new Float64Array(Math.round(length * RATE));
  const pull = (start) => {
    const pitch = 1050 + pulley() * 350;
    const squeak = 0.09 + pulley() * 0.05;
    let rope = 0;
    let phase = 0;
    const from = Math.round(start * RATE);
    for (let i = 0; i < Math.round(pullLength * RATE) && from + i < samples.length; i++) {
      const t = i / RATE;
      rope += (pulley() * 2 - 1 - rope) * 0.35;
      phase += (2 * Math.PI * pitch * (1 + 0.12 * Math.min(1, t / squeak) + 0.02 * Math.sin(2 * Math.PI * 38 * t))) / RATE;
      const sheave = t < squeak ? (Math.sin(phase) + 0.35 * Math.sin(2 * phase) + 0.15 * Math.sin(3 * phase)) * Math.sin((Math.PI * t) / squeak) : 0;
      samples[from + i] += rope * 0.18 * Math.sin(Math.PI * (t / pullLength)) + sheave * 0.3;
    }
  };
  for (let k = 0; k < pulls; k++) {
    pull((k * (length - pullLength)) / pulls + pulley() * 0.03);
  }
  writeSound("flag_halyard", samples, 0.8);
}

// The secret techno room's track, a minute long at 128 beats a minute, looped while anyone is in the room: eight bars
// of kick, hats and a rolling bass; eight bars of build, a snare roll quickening from eighths to thirty-seconds and a
// noise sweep rising over it, the kick dropping out for the last two bars and half a beat of silence before the drop;
// then the drop itself, on the downbeat of bar seventeen, half a minute in: a huge sub-bass boom falling from 90 to 32
// hertz under a full kick, an offbeat saw bass through a minor progression, chord stabs on the offbeats, claps on two
// and four and open hats, for sixteen bars, back round to the start.
{
  const techno = ownRandom(0x7465636b);
  const beat = 60 / 128;
  const bar = beat * 4;
  const length = bar * 32;
  const samples = new Float64Array(Math.round(length * RATE));
  const add = (start, duration, voice) => {
    const from = Math.round(start * RATE);
    const count = Math.min(Math.round(duration * RATE), samples.length - from);
    for (let i = 0; i < count; i++) {
      samples[from + i] += voice(i / RATE);
    }
  };
  const kick = (start, punch) =>
    add(start, 0.45, (t) => {
      const phase = 2 * Math.PI * (45 * t + (110 / 18) * (1 - Math.exp(-18 * t)));
      return punch * (Math.sin(phase) * Math.exp(-t * 7) + (t < 0.004 ? (techno() * 2 - 1) * 0.6 : 0));
    });
  const hat = (start, open, level) => {
    let last = 0;
    add(start, open ? 0.22 : 0.05, (t) => {
      const noise = techno() * 2 - 1;
      const high = noise - last;
      last = noise;
      return high * level * Math.exp(-t * (open ? 14 : 70));
    });
  };
  const snare = (start, level, pitch) =>
    add(start, 0.16, (t) => level * (((techno() * 2 - 1) * 0.7 + Math.sin(2 * Math.PI * pitch * t) * 0.4) * Math.exp(-t * 26)));
  const clap = (start) =>
    add(start, 0.2, (t) => (techno() * 2 - 1) * 0.55 * (Math.exp(-((t % 0.012) * 300)) * (t < 0.036 ? 1 : 0) + Math.exp(-t * 18) * (t >= 0.036 ? 1 : 0)));
  // A saw note through a one-pole low-pass filter, its cutoff opening and closing over the note.
  const sawNote = (start, duration, frequency, level, bright) => {
    let filtered = 0;
    add(start, duration, (t) => {
      const saw = 2 * ((frequency * t) % 1) - 1;
      const cutoff = 0.04 + bright * Math.exp(-t * 9);
      filtered += cutoff * (saw - filtered);
      return filtered * level * Math.min(1, t * 300) * Math.min(1, (duration - t) * 200);
    });
  };
  // A chord stab: five detuned saws for each note, opening bright and closing fast.
  const stab = (start, notes, level) => {
    for (const note of notes) {
      for (const detune of [-0.012, -0.005, 0, 0.005, 0.012]) {
        sawNote(start, beat * 0.45, note * (1 + detune), level / 5, 0.5);
      }
    }
  };
  const roots = [55, 43.65, 65.41, 49];
  const chords = [[220, 261.63, 329.63], [174.61, 220, 261.63], [261.63, 329.63, 392], [196, 246.94, 293.66]];
  for (let b = 0; b < 32; b++) {
    const at = b * bar;
    const drop = b >= 16;
    const build = b >= 8 && b < 16;
    const chord = Math.floor(b / 2) % 4;
    for (let q = 0; q < 4; q++) {
      const t = at + q * beat;
      if (!(build && b >= 14) && !(b === 15 && q === 3)) {
        kick(t, drop ? 1 : 0.8);
      }
      hat(t + beat / 2, drop, drop ? 0.22 : 0.16);
      if (!build || b < 14) {
        sawNote(t + beat / 2, beat * 0.4, roots[chord] * (drop ? 1 : 2), drop ? 0.55 : 0.3, drop ? 0.35 : 0.12);
        sawNote(t + beat * 0.75, beat * 0.2, roots[chord] * (drop ? 1 : 2), drop ? 0.4 : 0.22, drop ? 0.25 : 0.08);
      }
      if (drop) {
        stab(t + beat / 2, chords[chord], 0.5);
      }
      if (drop && (q === 1 || q === 3)) {
        clap(t);
      }
    }
    if (build) {
      const steps = b < 12 ? 8 : b < 14 ? 16 : 32;
      for (let k = 0; k < steps; k++) {
        const loud = 0.15 + 0.5 * ((b - 8) * steps + k) / (8 * steps);
        snare(at + (k * bar) / steps, loud, 180 + (b - 8) * 40);
      }
    }
  }
  // The rise into the drop: noise swelling over the last four bars of the build, a sine sweeping up under it.
  let phase = 0;
  add(12 * bar, 4 * bar - beat / 2, (t) => {
    const share = t / (4 * bar);
    phase += (2 * Math.PI * (200 + 1800 * share * share)) / RATE;
    return ((techno() * 2 - 1) * 0.25 + Math.sin(phase) * 0.15) * share * share;
  });
  // The drop's boom: a sub-bass falling from 90 to 32 hertz, ringing on for three seconds.
  let boom = 0;
  add(16 * bar, 3.2, (t) => {
    boom += (2 * Math.PI * (32 + 58 * Math.exp(-t * 2.2))) / RATE;
    return Math.sin(boom) * 1.6 * Math.exp(-t * 0.9) * Math.min(1, t * 400);
  });
  writeSound("techno_track", samples.map((v) => Math.tanh(v * 1.4)), 0.9);
}

// The ship's bell struck twice as a lookout marks a spot through the Lookout's Glass, heard at the glass and in the
// control car: a bronze bell's partials, a hum an octave down, the minor third and the fifth over the note and the
// octave above, each dying away faster the higher it is, and the clapper's bright clack at each strike, ding-ding, as
// a watch's bells are rung in pairs. Its own noise, so it never shifts the sounds above.
{
  const shipsBell = ownRandom(0x62656c6c);
  const NOTE = 784;
  const PARTIALS = [[0.5, 0.25, 2.2], [1, 1, 1.6], [1.19, 0.35, 1.0], [1.5, 0.3, 0.8], [2, 0.45, 0.6], [2.51, 0.15, 0.35], [3.01, 0.1, 0.25]];
  const STRIKES = [0, 0.42];
  const CLACK = 0.006;
  const samples = new Float64Array(Math.round(3 * RATE));
  for (const start of STRIKES) {
    for (let i = Math.round(start * RATE); i < samples.length; i++) {
      const t = i / RATE - start;
      for (const [ratio, gain, decay] of PARTIALS) {
        samples[i] += gain * Math.min(1, t * 2000) * Math.exp(-t / decay) * Math.sin(2 * Math.PI * NOTE * ratio * t);
      }
      if (t < CLACK) {
        samples[i] += 0.6 * (shipsBell() * 2 - 1) * (1 - t / CLACK);
      }
    }
  }
  writeSound("lookout_bell", samples);
}

// A zeppelin's take-off on her ground handlers' lines (see BallastValveBlockEntity), each with its own noise so it
// never shifts the sounds above. The ground officer's pea whistle: a shrill note trilled by the pea rattling in it,
// over a breath of air, a short blast to hold fast and a long one to let go. Her bow's water ballast: a heavy cascade
// drumming down, swelling in and dying away over three seconds, a low roar under the splashing.
{
  const whistle = (name, seconds, seed) => {
    const breath = ownRandom(seed);
    const samples = new Float64Array(Math.round(seconds * RATE));
    let phase = 0;
    let airLow = 0;
    for (let i = 0; i < samples.length; i++) {
      const t = i / RATE;
      const envelope = Math.min(1, t / 0.02) * Math.min(1, (seconds - t) / 0.06);
      const pitch = 2900 + 140 * Math.sin(2 * Math.PI * 31 * t) + 80 * Math.min(1, t / 0.15);
      phase += (2 * Math.PI * pitch) / RATE;
      const trill = 0.65 + 0.35 * Math.abs(Math.sin(2 * Math.PI * 15.5 * t));
      const air = breath() * 2 - 1;
      airLow += 0.5 * (air - airLow);
      samples[i] = envelope * (trill * Math.sin(phase) + 0.12 * (air - airLow));
    }
    // Written well under the others' peak: a pure, shrill note sounds far louder than a noise as high.
    writeSound(name, samples, 0.45);
  };
  whistle("handler_whistle_short", 0.35, 0x77687331);
  whistle("handler_whistle_long", 1.4, 0x77687332);
  const water = ownRandom(0x77617472);
  const samples = new Float64Array(Math.round(3.2 * RATE));
  let roar = 0;
  let splashLow = 0;
  let splashBand = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const envelope = Math.min(1, t / 0.25) * Math.min(1, (3.2 - t) / 0.8);
    const white = water() * 2 - 1;
    roar += 0.02 * (white - roar);
    splashBand += 0.35 * (white - splashBand);
    splashLow += 0.06 * (white - splashLow);
    const drumming = 0.7 + 0.3 * Math.sin(2 * Math.PI * 7 * t + Math.sin(2 * Math.PI * 1.3 * t));
    samples[i] = envelope * (6 * roar + drumming * (splashBand - splashLow));
  }
  writeSound("water_ballast", samples, 0.85);
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

// The laser path clearer, with its own noise, so it never shifts the sounds above. A bolt: a sharp zap, a bright tone
// sweeping down fast with a buzz on it, over a hiss of noise that bursts in and dies away, short enough for five a
// second to run together into a rattle of zaps. The turret charging as it is armed: a whine rising from low to high
// over a second, buzzing, with a soft click at the top as it locks.
const laserNoise = (() => {
  let laserSeed = 0x6c617372;
  return () => {
    laserSeed = (Math.imul(laserSeed, 1664525) + 1013904223) | 0;
    return (laserSeed >>> 0) / 4294967296;
  };
})();
{
  const samples = new Float64Array(Math.round(0.22 * RATE));
  let phase = 0;
  let previous = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    phase += (2 * Math.PI * (700 + 2600 * Math.exp(-t / 0.035))) / RATE;
    const zap = Math.sin(phase) + 0.3 * Math.sign(Math.sin(3 * phase));
    const white = laserNoise() * 2 - 1;
    const hiss = white - previous;
    previous = white;
    samples[i] = Math.min(1, t * 800) * (Math.exp(-t / 0.05) * zap + 0.6 * Math.exp(-t / 0.09) * hiss);
  }
  writeSound("laser_hiss", samples, 0.75);
}
{
  const length = 1.1;
  const samples = new Float64Array(Math.round(length * RATE));
  let phase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    phase += (2 * Math.PI * (180 + 1400 * Math.pow(t / length, 1.6))) / RATE;
    const whine = Math.sin(phase) + 0.25 * Math.sin(2 * phase) + 0.15 * Math.sign(Math.sin(phase));
    samples[i] = Math.min(1, t / 0.1) * Math.min(1, (length - t) / 0.08) * whine * 0.7;
  }
  gearRing(samples, length - 0.12, 1800, 0.4);
  writeSound("laser_charge", samples);
}

// The self-destruct, the sounds of Ultrasplosive's nuke (the same author's mod), made the same way and with its own
// noise, so it never shifts the sounds above. The siren and the bang are recordings, kept as they were in tools/audio:
// - civil-defense-siren-waver.ogg: a civil defence siren in St. Paul, Minnesota, recorded in 2008 by Wikimedia Commons
//   user Techtonic (https://commons.wikimedia.org/wiki/File:Civil-defense-siren-waver.ogg), in the public domain;
// - red-library-R09-52-huge-explosion-with-long-decay.flac: "R09-52 Huge Explosion with Long Decay", from the Red
//   Library sound effects tapes (https://archive.org/details/Red_Library_Explosions), under CC0 1.0.
// The siren, while the button is armed: its spin-up from standstill and its first wail, the ten seconds the button
// stays armed, the rumble of the wind taken out and its quiet start brought up. The bang: slowed a tenth for weight,
// its lows lifted, its long decay evened out and its tape tail trimmed, fourteen seconds. The rumble after it,
// synthesised: low brown noise and a wandering deep tone under it, swelling in over a second and a half and dying
// away, the odd distant crack in it.
const AUDIO = resolve(ROOT, "tools/audio");

/** A recording in tools/audio through FFmpeg's filters, read back as samples at the generator's rate. */
function recorded(file, filters) {
  const heard = join(scratch, `${file}.wav`);
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", resolve(AUDIO, file), "-af", filters.join(","), "-ac", "1", "-ar", `${RATE}`, "-c:a", "pcm_s16le", heard]);
  return readWav(heard);
}
writeSound("self_destruct_siren", recorded("civil-defense-siren-waver.ogg", [
  // The motor starts about 1.9 s in.
  "atrim=start=1.8:end=11.8", "asetpts=PTS-STARTPTS", "highpass=f=70",
  "acompressor=threshold=0.08:ratio=3:attack=15:release=400:makeup=9", "alimiter=limit=0.95:level=0",
  "afade=t=in:st=0:d=0.02", "afade=t=out:st=9.5:d=0.5",
]));
writeSound("self_destruct_blast", recorded("red-library-R09-52-huge-explosion-with-long-decay.flac", [
  // The 48 kHz tape played at 43.2 kHz: a tenth slower and a little over a semitone lower; the hit is about 0.38 s in.
  "asetrate=43200", `aresample=${RATE}`, "atrim=start=0.33:end=14.6", "asetpts=PTS-STARTPTS", "highpass=f=28", "bass=g=5:f=90:w=0.6",
  "acompressor=threshold=0.3:ratio=2:attack=10:release=400:makeup=4", "alimiter=limit=0.95:level=0",
  "afade=t=in:st=0:d=0.01", "afade=t=out:st=11.6:d=2.6",
]), 0.95);
const blastNoise = (() => {
  let blastSeed = 0x6e756b65;
  return () => {
    blastSeed = (Math.imul(blastSeed, 1664525) + 1013904223) | 0;
    return (blastSeed >>> 0) / 4294967296 * 2 - 1;
  };
})();
{
  const seconds = 9;
  const samples = new Float64Array(seconds * RATE);
  const lowCut = 1 - Math.exp((-2 * Math.PI * 90) / RATE);
  const highCut = Math.exp((-2 * Math.PI * 20) / RATE);
  let brown = 0;
  let low = 0;
  let high = 0;
  let lastLow = 0;
  let phase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    brown = Math.max(-1, Math.min(1, brown + blastNoise() * 0.08));
    low += lowCut * (brown - low);
    high = highCut * (high + low - lastLow);
    lastLow = low;
    phase += (2 * Math.PI * (32 + 14 * Math.sin(t * 0.8) + 6 * Math.sin(t * 3.7))) / RATE;
    const crack = Math.abs(blastNoise()) < 0.0008 ? blastNoise() * 0.6 : 0;
    samples[i] = Math.min(1, t / 1.5) * Math.pow(1 - t / seconds, 1.6) * (3 * high + 0.45 * Math.sin(phase) + crack);
  }
  writeSound("self_destruct_rumble", samples, 0.9);
}

// The rocket's lift, with a random sequence of its own so the sounds above are unchanged. Its doors: a smooth slide,
// a soft rush of air over the rumble of their rollers, ending in a soft thunk as they meet their stops. Its motor
// setting off: the hum of its mains-fed motor coming up, with the whine of its drive rising as it gathers speed. Its
// hum as the car rides: that motor running, a low 50, 100 and 150 hertz under a quiet whine, every part a whole number
// of turns round the two-second loop so it plays on without a seam. Its chimes: one bright tone as the car comes in
// going up, two falling as it comes in going down.
{
  const liftRandom = ownRandom(0x4c696674);
  const door = new Float64Array(Math.round(0.9 * RATE));
  let low = 0;
  let band = 0;
  for (let i = 0; i < door.length; i++) {
    const t = i / RATE;
    const white = liftRandom() * 2 - 1;
    low += 0.04 * (white - low);
    band += 0.25 * (white - band);
    const slide = Math.sin(Math.PI * Math.min(1, t / 0.8)) ** 1.5;
    const rollers = 0.25 * Math.sin(2 * Math.PI * 140 * t) * slide;
    const thunkAt = t - 0.8;
    const thunk = thunkAt > 0 ? Math.exp(-thunkAt / 0.03) * (0.9 * Math.sin(2 * Math.PI * 70 * thunkAt) + 0.3 * (liftRandom() * 2 - 1)) : 0;
    door[i] = slide * 0.6 * (band - low) + rollers + thunk;
  }
  writeSound("lift_door", door, 0.6);
  const hum = (t, level) => level * (0.5 * Math.sin(2 * Math.PI * 50 * t) + 0.35 * Math.sin(2 * Math.PI * 100 * t) + 0.2 * Math.sin(2 * Math.PI * 150 * t));
  const start = new Float64Array(Math.round(0.8 * RATE));
  let whinePhase = 0;
  for (let i = 0; i < start.length; i++) {
    const t = i / RATE;
    const rising = Math.min(1, t / 0.6);
    whinePhase += 2 * Math.PI * (380 + 240 * rising) / RATE;
    start[i] = hum(t, rising) + 0.12 * rising * Math.sin(whinePhase) + 0.04 * rising * (liftRandom() * 2 - 1);
  }
  writeSound("lift_start", start, 0.5);
  const loop = new Float64Array(Math.round(2 * RATE));
  for (let i = 0; i < loop.length; i++) {
    const t = i / RATE;
    const wobble = 1 + 0.15 * Math.sin(2 * Math.PI * 0.5 * t);
    loop[i] = hum(t, 1) + 0.1 * wobble * Math.sin(2 * Math.PI * 600 * t);
  }
  writeSound("lift_hum", loop, 0.45);
  const up = new Float64Array(Math.round(1.4 * RATE));
  bell(up, 0, 1318.51, 0.45);
  writeSound("lift_chime_up", up, 0.6);
  const down = new Float64Array(Math.round(1.8 * RATE));
  bell(down, 0, 1318.51, 0.45);
  bell(down, 0.35, 1046.5, 0.55);
  writeSound("lift_chime_down", down, 0.6);
}

// The rocket's engines (see the client's RocketRoar and the autopilot's RocketSounds), each sound with a random
// sequence of its own, so the sounds above are unchanged. Her roar, a seamless loop played while her bells fire: a deep
// rumble of exhaust, brown noise low-passed near 700 hertz and slowly surging; the hiss of the gas, a band round 2.5
// kilohertz twelve decibels under the rumble; the crackle of the shock waves in her plumes, some forty sharp pops a
// second; and a 32 hertz sub, felt more than heard. Aboard, the same roar comes through her hull as a rumble, its hiss
// and crackle gone. As her engines light at lift-off: the crack of them catching, a deep whoomp sweeping up from 30 to
// 55 hertz, and the roar swelling in as its low-pass opens, handing over to the loop as the client fades it in. As
// they stop: the roar falling away as its low-pass closes, a pop as the flames go out, and her hot bells ticking as
// they cool. Each of these is written to the loop's own scale, so it is as loud beside the roar as it should be, and
// heard aboard through her hull as the roar is. A side jet firing: a short pssht of gas round 3 kilohertz. Touching
// down: a heavy thunk round 45 hertz as her fins take her weight, a clank of their feet, and the hiss and groan of
// their dampers as she settles. Tune it all here.
const ROCKET = {
  // The loop, and how much longer it is rendered to crossfade its tail over its head, in seconds.
  loop: 4,
  crossfade: 0.5,
  // The rumble's low-pass, the hiss's band and how far under the rumble the hiss is, in hertz and decibels; and how
  // loud the crackle is beside the rumble, as a share of the rumble's root mean square.
  rumbleCutoff: 700,
  hissCentre: 2500,
  hissUnder: 12,
  crackleShare: 0.35,
  // Crackles a second, and the sub's pitch, in hertz, and how loud it is beside the rumble, as a share.
  crackles: 40,
  sub: 32,
  subShare: 0.8,
  // The surge: how far the rumble swells and eases, as a share, at a few whole turns round the loop.
  surge: [[3, 0.12, 0.7], [7, 0.08, 2.1]],
  // The hull's low-pass on the roar heard aboard, in hertz, taken twice.
  aboardCutoff: 220,
  // Past this, as a multiple of the rumble's root mean square, the peaks are rounded off, so the crackle and the crack
  // do not leave the roar quiet once it is normalised.
  soft: 3,
  // The peak the loop is written at; the ignition and the cut-off share its scale.
  level: 0.85,
  // The client's handover at lift-off (RocketRoar's IGNITION_LEAD and IGNITION_RISE): the loop rises in so many
  // seconds after the ignition starts, over so many more.
  handoverAt: 0.75,
  handover: 1.25,
  // The client's fade of the loop at cut-off (RocketRoar's CUTOFF_FADE), in seconds.
  fadeOut: 0.2,
};

/** The highest any sample reaches either way. */
function peakOf(samples) {
  let peak = 0;
  samples.forEach((s) => (peak = Math.max(peak, Math.abs(s))));
  return peak;
}

/** The root mean square of samples. */
function rms(samples) {
  let sum = 0;
  samples.forEach((s) => (sum += s * s));
  return Math.sqrt(sum / samples.length);
}

/** The samples through a two-pole low-pass at the cutoff given, in hertz, its q a Butterworth's. */
function lowPassed(input, cutoff) {
  const out = new Float64Array(input.length);
  const w = (2 * Math.PI * cutoff) / RATE;
  const alpha = Math.sin(w) / (2 * Math.SQRT1_2);
  const cos = Math.cos(w);
  const [b0, b1, a0, a1, a2] = [(1 - cos) / 2, 1 - cos, 1 + alpha, -2 * cos, 1 - alpha];
  let [x1, x2, y1, y2] = [0, 0, 0, 0];
  for (let i = 0; i < input.length; i++) {
    const y = (b0 * input[i] + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2) / a0;
    [x2, x1, y2, y1] = [x1, input[i], y1, y];
    out[i] = y;
  }
  return out;
}

/** Crossfades noise rendered a little long, its tail over its head at an even power, into a seamless loop. */
function seamlessNoise(samples, seconds, fadeSeconds) {
  const length = Math.round(seconds * RATE);
  const fade = Math.round(fadeSeconds * RATE);
  const loop = samples.slice(0, length);
  for (let i = 0; i < fade; i++) {
    const w = ((i / fade) * Math.PI) / 2;
    loop[i] = samples[length + i] * Math.cos(w) + samples[i] * Math.sin(w);
  }
  return loop;
}

/**
 * A rocket's roar for so many seconds, from a random sequence of its own, its three parts apart, none yet levelled:
 * the rumble, brown noise through a low-pass at cutoff(t) hertz, surging; the hiss, a band of noise round
 * ROCKET.hissCentre; and the crackle, pops coming at crackles(t) a second, each a sharp push of air and its smaller
 * pull back, mostly outward. Times t in seconds.
 */
function rocketRoar(seconds, own, cutoff, crackles) {
  const n = Math.round(seconds * RATE);
  const rumble = new Float64Array(n);
  const white = new Float64Array(n);
  const crackle = new Float64Array(n);
  const pop = Float64Array.from({ length: Math.round(0.003 * RATE) }, (_, i) => Math.exp(-i / (0.00025 * RATE)) - 0.45 * Math.exp(-i / (0.0009 * RATE)));
  let brown = 0;
  let low = 0;
  let band = 0;
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    white[i] = own() * 2 - 1;
    // Brown noise, its slow drift let leak away under some 35 hertz.
    brown = (brown + 0.02 * white[i]) * 0.995;
    // A state variable low-pass, so its cutoff may move as it goes.
    const tune = 2 * Math.sin((Math.PI * cutoff(t)) / RATE);
    low += tune * band;
    band += tune * (brown - low - Math.SQRT2 * band);
    const surge = ROCKET.surge.reduce((sum, [turns, depth, phase]) => sum + depth * Math.sin((2 * Math.PI * turns * t) / ROCKET.loop + phase), 1);
    rumble[i] = low * surge;
    if (own() < crackles(t) / RATE) {
      const strength = (0.4 + 0.6 * own() ** 2) * (own() < 0.85 ? 1 : -1);
      for (let k = 0; k < pop.length && i + k < n; k++) {
        crackle[i + k] += strength * pop[k];
      }
    }
  }
  return { rumble, hiss: resonance(white, ROCKET.hissCentre, 0.9), crackle };
}

/** Rounds off peaks past ROCKET.soft, a multiple of the rumble's root mean square. */
function softened(samples) {
  return samples.map((s) => ROCKET.soft * Math.tanh(s / ROCKET.soft));
}
{
  const roarRandom = ownRandom(0x526f6172);
  const rendered = ROCKET.loop + ROCKET.crossfade;
  const steady = rocketRoar(rendered, roarRandom, () => ROCKET.rumbleCutoff, () => ROCKET.crackles);
  // Levelled once from the steady roar, and the same levels for the ignition and the cut-off: the rumble's root mean
  // square one, the hiss and the crackle set beside it.
  const gain = {
    rumble: 1 / rms(steady.rumble),
    hiss: 10 ** (-ROCKET.hissUnder / 20) / rms(steady.hiss),
    crackle: ROCKET.crackleShare / rms(steady.crackle),
  };
  const mix = (parts, rumbleAt, hissAt, crackleAt) => {
    const mixed = new Float64Array(parts.rumble.length);
    for (let i = 0; i < mixed.length; i++) {
      const t = i / RATE;
      mixed[i] = rumbleAt(t) * gain.rumble * parts.rumble[i] + hissAt(t) * gain.hiss * parts.hiss[i] + crackleAt(t) * gain.crackle * parts.crackle[i];
    }
    return mixed;
  };
  const noise = seamlessNoise(mix(steady, () => 1, () => 1, () => 1), ROCKET.loop, ROCKET.crossfade);
  // The sub, a whole number of turns round the loop, so it needs no crossfade.
  const loop = softened(noise.map((s, i) => s + ROCKET.subShare * (Math.sin((2 * Math.PI * ROCKET.sub * i) / RATE) + 0.3 * Math.sin((4 * Math.PI * ROCKET.sub * i) / RATE))));
  const scale = ROCKET.level / peakOf(loop);
  writeSound("rocket_roar", loop, ROCKET.level);
  // Aboard: filtered round the loop twice and the second turn kept, so the filter carries over its seam. The ignition
  // and the cut-off heard aboard go through the same hull, written to the muffled loop's scale.
  const hull = (samples) => lowPassed(lowPassed(samples, ROCKET.aboardCutoff), ROCKET.aboardCutoff);
  const twice = new Float64Array(2 * loop.length);
  twice.set(loop);
  twice.set(loop, loop.length);
  const aboard = hull(twice).slice(loop.length);
  const aboardScale = ROCKET.level / peakOf(aboard);
  writeSound("rocket_roar_aboard", aboard, ROCKET.level);

  // The ignition. The loop rises in over the handover: the ignition's roar falls away under it at an even power.
  const ignitionLength = ROCKET.handoverAt + ROCKET.handover + 0.2;
  const handedOver = (t) => Math.max(0, Math.min(1, (t - ROCKET.handoverAt) / ROCKET.handover));
  const swell = (t) => gearRamp(t, 0, 0.5) * Math.sqrt(1 - handedOver(t) ** 2);
  const lighting = rocketRoar(ignitionLength, roarRandom, (t) => 120 + (ROCKET.rumbleCutoff - 120) * gearRamp(t, 0.05, 1.0), (t) => ROCKET.crackles * gearRamp(t, 0.25, 0.9));
  const ignition = mix(lighting, swell, (t) => swell(t) * gearRamp(t, 0.3, 1.0), swell);
  let phase = 0;
  let fireball = 0;
  for (let i = 0; i < ignition.length; i++) {
    const t = i / RATE;
    const white = roarRandom() * 2 - 1;
    const crack = t < 0.012 ? 5 * white * Math.exp(-t / 0.003) : 0;
    phase += (2 * Math.PI * (30 + 25 * gearRamp(t, 0, 0.4))) / RATE;
    const whoomp = Math.min(1, t / 0.02) * Math.exp(-Math.max(0, t - 0.08) / 0.4) * (Math.sin(phase) + 0.35 * Math.sin(2 * phase) + 0.12 * Math.sin(3 * phase));
    fireball += 0.02 * (white - fireball);
    ignition[i] = (ignition[i] + crack + 3.5 * whoomp + 12 * fireball * Math.exp(-t / 0.3)) * Math.min(1, (ignitionLength - t) / 0.05);
  }
  const lit = softened(ignition);
  writeSound("rocket_ignition", lit, peakOf(lit) * scale);
  const litAboard = hull(lit);
  writeSound("rocket_ignition_aboard", litAboard, peakOf(litAboard) * aboardScale);

  // The cut-off. The loop fades out over its first moment: the cut-off's roar comes in under it at an even power, then
  // falls away as its low-pass closes.
  const cutoffLength = 3.2;
  const fading = (t) => Math.max(0, 1 - t / ROCKET.fadeOut);
  const falling = (t) => Math.sqrt(1 - fading(t) ** 2) * Math.exp(-Math.max(0, t - ROCKET.fadeOut) / 0.35);
  const dying = rocketRoar(cutoffLength, roarRandom, (t) => 90 + (ROCKET.rumbleCutoff - 90) * Math.exp(-t / 0.3), (t) => ROCKET.crackles * Math.exp(-t / 0.2));
  const cutoff = mix(dying, falling, (t) => falling(t) * Math.exp(-t / 0.15), falling);
  thunk(cutoff, 0.38, 50, 2.5, roarRandom);
  let vent = 0;
  for (let i = Math.round(0.3 * RATE); i < cutoff.length; i++) {
    const t = i / RATE;
    const white = roarRandom() * 2 - 1;
    cutoff[i] += 0.12 * (white - vent) * Math.exp(-(t - 0.3) / 1.0);
    vent = white;
  }
  for (let t = 0.7; t < cutoffLength - 0.1; t += -Math.log(1 - roarRandom()) / (0.8 + 8 * Math.exp(-(t - 0.7) / 0.8))) {
    const pitch = 2600 + 3400 * roarRandom();
    click(cutoff, t, pitch, 0.3 + 0.4 * roarRandom(), roarRandom);
    gearRing(cutoff, t + 0.001, pitch * 1.13, 0.15 * roarRandom());
  }
  const out = softened(cutoff);
  writeSound("rocket_cutoff", out, peakOf(out) * scale);
  const outAboard = hull(out);
  writeSound("rocket_cutoff_aboard", outAboard, peakOf(outAboard) * aboardScale);

  // A side jet firing: about 120 milliseconds of gas round 3 kilohertz, bursting in, and its valve's click.
  const puff = new Float64Array(Math.round(0.15 * RATE));
  const gas = resonance(Float64Array.from(puff, () => roarRandom() * 2 - 1), 3000, 1.3);
  let previous = 0;
  for (let i = 0; i < puff.length; i++) {
    const t = i / RATE;
    const white = roarRandom() * 2 - 1;
    const envelope = Math.min(1, t / 0.004) * Math.max(0, 1 - t / 0.12) ** 1.5;
    puff[i] = envelope * (3 * gas[i] + 0.25 * (white - previous));
    previous = white;
  }
  click(puff, 0, 1800, 0.3, roarRandom);
  writeSound("rocket_jet_puff", puff, 0.8);

  // Touching down: the thunk of her weight coming onto her fins, falling to some 45 hertz with a crack as they meet the
  // ground; a clank of their feet; and their dampers taking it up, a hiss of oil through their valves, fluttering, and
  // the groan of the struts as they sink.
  const touchdown = new Float64Array(Math.round(1.5 * RATE));
  let thump = 0;
  let grit = 0;
  let oil = 0;
  let strut = 0;
  for (let i = 0; i < touchdown.length; i++) {
    const t = i / RATE;
    const white = roarRandom() * 2 - 1;
    thump += (2 * Math.PI * (45 + 60 * Math.exp(-t / 0.03))) / RATE;
    const weight = Math.min(1, t / 0.002) * Math.exp(-t / 0.25) * Math.tanh(1.6 * (Math.sin(thump) + 0.4 * Math.sin(2 * thump) + 0.15 * Math.sin(3 * thump)));
    grit += 0.3 * (white - grit);
    const crack = t < 0.012 ? 0.6 * grit * (1 - t / 0.012) : 0;
    oil += 0.25 * (white - oil);
    const hiss = t > 0.06 ? Math.min(1, (t - 0.06) / 0.04) * Math.exp(-(t - 0.06) / 0.5) * (1 + 0.15 * Math.sin(2 * Math.PI * 17 * t)) * (white - oil) : 0;
    strut += (2 * Math.PI * (130 - 40 * Math.min(1, t / 0.8))) / RATE;
    const groan = t > 0.05 && t < 1.0 ? Math.sin((Math.PI * (t - 0.05)) / 0.95) * (Math.sin(strut) + 0.3 * Math.sin(2 * strut) + 0.1 * Math.sin(3 * strut)) : 0;
    touchdown[i] = weight + crack + 0.28 * hiss + 0.12 * groan;
  }
  gearRing(touchdown, 0.003, 240, 0.12);
  gearRing(touchdown, 0.003, 590, 0.08);
  writeSound("rocket_touchdown", touchdown, 0.9);
}

// The flight deck's voice, for the callouts and the cabin announcements: a calm, warm British woman's, as an airline's
// recorded announcements are. Kokoro (see say) says each stretch of a line between its pauses, and FFmpeg plays it
// through the cabin's loudspeakers, their band and the bright middle they give a voice and the speakers further along
// the cabin overlapping it softly, and evens its level as a broadcaster's. Tune it all here.
const VOICE = {
  // Kokoro's model on Hugging Face, fetched once into tools/node_modules.
  model: "onnx-community/Kokoro-82M-v1.0-ONNX",
  // Kokoro's voice: Emma, its clearest British woman's.
  voice: "bf_emma",
  // The pause a "|" in a line makes, in milliseconds.
  pause: 500,
  // FFmpeg's filters for the cabin's loudspeakers, in order.
  loudspeakers: [
    // The cabin loudspeakers' band.
    "highpass=f=220:poles=2",
    "lowpass=f=7500:poles=2",
    // Their bright middle.
    "equalizer=f=2500:t=q:w=1.2:g=3",
    // The speakers further along the cabin, each a little later and quieter.
    "aecho=0.85:0.8:19|38|61:0.22|0.13|0.07",
  ],
  // How each kind of line is said: how fast, Kokoro's own pace being 1; FFmpeg's filters after the loudspeakers', for
  // an even level; and the peak it is written at, under the other sounds' as levelled speech is denser, so that it
  // sounds no louder beside them.
  // A callout crisply, compressed hard and limited, loud and even as a warning: too short for the loudness normaliser,
  // which levels only from three seconds long.
  callout: {
    pace: 1,
    levelling: ["acompressor=threshold=-30dB:ratio=3:attack=3:release=100:makeup=12dB", "alimiter=limit=0.4:attack=2:release=50:level=false"],
    level: 0.8,
  },
  // An announcement unhurried, through FFmpeg's loudness normaliser, the same from one to the next, its peaks held
  // well under.
  announcement: { pace: 0.92, levelling: ["loudnorm=I=-18:TP=-6:LRA=6"], level: 0.72 },
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
 * The line, at the generator's rate or the one given in hertz, with the quiet before and after it cut away, but for a
 * moment either side, and its tail faded out.
 */
function trim(samples, rate = RATE) {
  let peak = 0;
  samples.forEach((s) => (peak = Math.max(peak, Math.abs(s))));
  const margin = Math.round(QUIET_KEPT * rate);
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
 * Says every line with the speech synthesis in the given voice (its name, rate and pause, as in RADIO.voice), all in
 * one go, into WAVs in the scratch folder; false, saying what is left as it is, if the voice is not installed.
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

/** Kokoro, ready to speak; null, saying what is left as it is, if kokoro-js is not installed. */
async function kokoro() {
  let KokoroTTS;
  try {
    ({ KokoroTTS } = await import("kokoro-js"));
  } catch (error) {
    if (error.code !== "ERR_MODULE_NOT_FOUND") {
      throw error;
    }
    console.log("Kokoro is not installed (npm install --prefix tools): leaving the callouts and announcements as they are");
    return null;
  }
  return KokoroTTS.from_pretrained(VOICE.model, { dtype: "fp32", device: "cpu" });
}

/**
 * Says every line with Kokoro in VOICE as the kind of line given (VOICE.callout or VOICE.announcement), each stretch
 * between its pauses on its own with the quiet round it trimmed and the pause put between, and plays it through the
 * loudspeakers and that kind's levelling into a WAV at the generator's rate in the scratch folder.
 */
async function say(speaker, lines, kind) {
  for (const [name, text] of Object.entries(lines)) {
    let rate = RATE;
    const parts = [];
    for (const part of text.split("|")) {
      const spoken = await speaker.generate(part.trim(), { voice: VOICE.voice, speed: kind.pace });
      rate = spoken.sampling_rate;
      parts.push(trim(Float64Array.from(spoken.audio), rate));
    }
    const pause = Math.round((VOICE.pause / 1000) * rate);
    const joined = new Float64Array(parts.reduce((sum, p) => sum + p.length, 0) + pause * (parts.length - 1));
    let at = 0;
    for (const p of parts) {
      joined.set(p, at);
      at += p.length + pause;
    }
    let peak = 0;
    joined.forEach((s) => (peak = Math.max(peak, Math.abs(s))));
    const said = join(scratch, `${name}.said.wav`);
    writeWav(said, Array.from(joined, (s) => (s / peak) * 0.5), rate);
    execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", said, "-af", [...VOICE.loudspeakers, ...kind.levelling].join(","), "-ac", "1", "-ar", `${RATE}`, "-c:a", "pcm_s16le", join(scratch, `${name}.voiced.wav`)]);
  }
}

/**
 * Writes a line said in VOICE as the named sound at the kind of line's level, refusing one that runs longer than the
 * seconds given.
 */
function writeVoiced(name, samples, kind, longest) {
  if (samples.length > longest * RATE) {
    throw new Error(`${name} runs ${(samples.length / RATE).toFixed(1)} s, longer than ${longest} s: shorten it or speed the voice up`);
  }
  writeSound(name, samples, kind.level);
}

const speaker = await kokoro();
if (speaker) {
  await say(speaker, CALLOUTS, VOICE.callout);
  for (const name of Object.keys(CALLOUTS)) {
    writeVoiced(name, trim(readWav(join(scratch, `${name}.voiced.wav`))), VOICE.callout, CALLOUT_LONGEST);
  }
  await say(speaker, ANNOUNCEMENTS, VOICE.announcement);
  for (const name of Object.keys(ANNOUNCEMENTS)) {
    const longest = name === "announcement_takeoff" ? TAKEOFF_LONGEST : ANNOUNCEMENT_LONGEST;
    writeVoiced(name, announcement(trim(readWav(join(scratch, `${name}.voiced.wav`)))), VOICE.announcement, longest);
  }
}

// The rocket's flight deck welcoming everyone aboard to space as she climbs past its edge (see EdgeOfSpace): made as a
// cabin announcement is, the chime and then the voice, but not one of the announcement panel's. Its second phrase also
// makes it long enough for the loudness normaliser, which levels only from three seconds long, to bring it level with
// the announcements.
const SPACE_WELCOME = { announcement_space: "Welcome to space. | Enjoy the view." };
if (speaker) {
  await say(speaker, SPACE_WELCOME, VOICE.announcement);
  writeVoiced("announcement_space", announcement(trim(readWav(join(scratch, "announcement_space.voiced.wav")))), VOICE.announcement,
    ANNOUNCEMENT_LONGEST);
}

// The ground officer's call as a zeppelin is let go from her handlers' lines (see BallastValveBlockEntity): a British
// man's voice, Kokoro's George, shouted through a megaphone, its horn's narrow band and hard middle, and the field's
// echo after it. Tune it here.
const OFFICER = {
  voice: "bm_george",
  pace: 0.9,
  megaphone: [
    "highpass=f=450:poles=2",
    "lowpass=f=3600:poles=2",
    "equalizer=f=1600:t=q:w=1:g=6",
    "acompressor=threshold=-28dB:ratio=4:attack=2:release=80:makeup=10dB",
    "aecho=0.8:0.55:170|330:0.28|0.12",
  ],
  level: 0.9,
};
if (speaker) {
  const spoken = await speaker.generate("Up ship!", { voice: OFFICER.voice, speed: OFFICER.pace });
  const shouted = trim(Float64Array.from(spoken.audio), spoken.sampling_rate);
  let peak = 0;
  shouted.forEach((sample) => (peak = Math.max(peak, Math.abs(sample))));
  const said = join(scratch, "up_ship_call.said.wav");
  const voiced = join(scratch, "up_ship_call.voiced.wav");
  writeWav(said, Array.from(shouted, (sample) => (sample / peak) * 0.5), spoken.sampling_rate);
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", said, "-af", OFFICER.megaphone.join(","), "-ac", "1", "-ar", `${RATE}`, "-c:a", "pcm_s16le", voiced]);
  writeSound("up_ship_call", readWav(voiced), OFFICER.level);
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
