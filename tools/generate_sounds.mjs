import { fileURLToPath } from "node:url";
import { mkdirSync, readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { resolve, join } from "node:path";
import { tmpdir } from "node:os";
import { execFileSync } from "node:child_process";

// Run from anywhere: node tools/generate_sounds.mjs [<name> ...]
// Writes the airliner's sounds as Ogg Vorbis into assets/skyliners/sounds, with FFmpeg on PATH (and, where FFmpeg has
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
const output = resolve(ROOT, "src/main/resources/assets/skyliners/sounds");
mkdirSync(output, { recursive: true });
const scratch = mkdtempSync(join(tmpdir(), "skyliners-sounds-"));

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
 * another as the pressure builds, with its harmonics and a slight waver, and the hiss of the oil through the valves,
 * from the gear's noise or the one given.
 */
function gearHydraulics(samples, from, to, startHz, endHz, gain, noise = gearNoise) {
  let phase = 0;
  let previous = 0;
  const span = to - from;
  for (let i = Math.round(from * RATE); i < Math.min(samples.length, Math.round(to * RATE)); i++) {
    const t = i / RATE - from;
    const envelope = Math.min(1, t / 0.4) * Math.min(1, (span - t) / 0.6);
    const hz = startHz + (endHz - startHz) * Math.min(1, t / (0.6 * span));
    phase += (2 * Math.PI * hz * (1 + 0.004 * Math.sin(2 * Math.PI * 3.1 * t))) / RATE;
    const tone = Math.sin(phase) + 0.45 * Math.sin(2 * phase) + 0.25 * Math.sin(3 * phase) + 0.12 * Math.sin(5 * phase);
    const white = noise() * 2 - 1;
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

// The rocket's rover (see Rover and the client's RoverSounds), with its own noise, so it never shifts the sounds above.
// Its motor: a seamless two-second loop of its drive motors' electric whine, a low hum with its second and third
// harmonics wavering a little and a faint high servo note over it, on the crunch of gravel under its six wheels, grains
// of band-passed noise at random, over a low rumble; every tone repeats a whole number of times in the loop. Its beeps
// as its driver takes it over and lets it go: two short tones a fifth apart, rising, then falling. Its horn: two blips
// of a buzzy two-tone note a minor third wide. The warning as it nears the edge of its range: a soft ping dying away.
{
  const noise = ownRandom(0x526f7672);
  const seconds = 2;
  const motor = new Float64Array(Math.round((seconds + 0.3) * RATE));
  let rumble = 0;
  let grain = 0;
  let grainLow = 0;
  let crunch = 0;
  for (let i = 0; i < motor.length; i++) {
    const t = i / RATE;
    const waver = 0.004 * Math.sin(2 * Math.PI * 3 * t);
    const hum = Math.sin(2 * Math.PI * 220 * t + waver * 40) + 0.45 * Math.sin(2 * Math.PI * 440 * t + waver * 80)
      + 0.25 * Math.sin(2 * Math.PI * 660 * t + waver * 120);
    const servo = 0.06 * Math.sin(2 * Math.PI * 1900 * t);
    const white = noise() * 2 - 1;
    rumble += 0.01 * (white - rumble);
    // A grain of gravel now and then, each dying away within a few milliseconds.
    if (noise() < 30 / RATE) {
      crunch = 0.4 + 0.6 * noise();
    }
    crunch *= 0.996;
    grain += 0.45 * (white - grain);
    grainLow += 0.12 * (white - grainLow);
    motor[i] = 0.32 * hum + servo + 3 * rumble + 1.4 * crunch * (grain - grainLow);
  }
  writeSound("rover_motor", seamless(motor, seconds, 0.3), 0.7);
  const beeps = (name, tones) => {
    const samples = new Float64Array(Math.round(0.32 * RATE));
    let at = 0.01;
    for (const [frequency, length] of tones) {
      for (let i = Math.round(at * RATE); i < Math.round((at + length) * RATE); i++) {
        const t = i / RATE - at;
        const envelope = Math.min(1, t / 0.005) * Math.min(1, (length - t) / 0.02);
        samples[i] += envelope * (Math.sin(2 * Math.PI * frequency * t) + 0.18 * Math.sin(2 * Math.PI * 3 * frequency * t));
      }
      at += length + 0.035;
    }
    // Written well under the others' peak: a pure tone sounds far louder than a noise as high.
    writeSound(name, samples, 0.35);
  };
  beeps("rover_start", [[880, 0.08], [1318.5, 0.12]]);
  beeps("rover_stop", [[1318.5, 0.08], [880, 0.12]]);
  const horn = new Float64Array(Math.round(0.6 * RATE));
  for (const [at, length] of [[0.01, 0.16], [0.24, 0.24]]) {
    let low = 0;
    for (let i = Math.round(at * RATE); i < Math.round((at + length) * RATE); i++) {
      const t = i / RATE - at;
      const envelope = Math.min(1, t / 0.008) * Math.min(1, (length - t) / 0.03);
      const buzz = ((440 * t) % 1) + ((523.25 * t) % 1) - 1;
      low += 0.3 * (buzz - low);
      horn[i] += envelope * low;
    }
  }
  writeSound("rover_horn", horn, 0.6);
  const ping = new Float64Array(Math.round(0.5 * RATE));
  for (let i = 0; i < ping.length; i++) {
    const t = i / RATE;
    ping[i] = Math.min(1, t / 0.004) * Math.exp(-t / 0.12) * (Math.sin(2 * Math.PI * 1046.5 * t) + 0.2 * Math.sin(2 * Math.PI * 2093 * t));
  }
  writeSound("rover_range", ping, 0.45);
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

// The rocket's rooms as one stands in them (see RoomAmbience), each from a random sequence of its own so the sounds
// above are unchanged, and kept over 150 hertz, clear of her lift's hum and her hull's rumble. Her greenhouse: air
// moving through the leaves, a soft hush swelling and easing, over a thin trickle of irrigation and now and then a drip
// into a bed, a twelve-second loop; and a bee buzzing past, a sawtooth about 220 hertz throbbing as it flies, swelling,
// and bending down in pitch as it goes by. Her pool: water lapping at its edge in small slaps and its filter gurgling
// into the skimmer, in the short ringing echo of a tiled hall, a twelve-second loop; and now and then a bigger slap of
// water against its side.
{
  const ROOM_LOOP = 12;
  const ROOM_FADE = 1;
  const noiseOf = (own, n) => Float64Array.from({ length: n }, () => own() * 2 - 1);
  // A smoothed random wobble between 0 and 1, changing about so many times a second.
  const wobbleOf = (own, n, rate) => {
    const out = new Float64Array(n);
    let level = own();
    let target = own();
    const step = rate / RATE;
    for (let i = 0; i < n; i++) {
      if (own() < step) target = own();
      level += 8 * step * (target - level);
      out[i] = level;
    }
    return out;
  };
  // A small tiled hall: four short combs, damped, and the dry sound over them.
  const tiledEcho = (dry) => {
    const wet = new Float64Array(dry.length);
    for (const [delay, feedback] of [[0.029, 0.62], [0.037, 0.6], [0.043, 0.58], [0.053, 0.56]]) {
      const gap = Math.round(delay * RATE);
      const line = new Float64Array(dry.length);
      let low = 0;
      for (let i = 0; i < dry.length; i++) {
        low += 0.5 * ((i >= gap ? line[i - gap] : 0) - low);
        line[i] = dry[i] + feedback * low;
        wet[i] += line[i] / 4;
      }
    }
    return dry.map((d, i) => 0.75 * d + 0.4 * wet[i]);
  };

  const garden = ownRandom(0x47617264);
  const n = Math.round((ROOM_LOOP + ROOM_FADE) * RATE);
  const leaves = resonance(noiseOf(garden, n), 1600, 0.6);
  const trickle = resonance(noiseOf(garden, n), 3200, 3);
  const babble = wobbleOf(garden, n, 18);
  const air = new Float64Array(n);
  let nextDrip = 0.8;
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const swell = 0.55 + 0.25 * Math.sin((2 * Math.PI * t) / 6) + 0.2 * Math.sin((2 * Math.PI * t) / 4 + 1);
    air[i] = 0.8 * swell * leaves[i] + 0.22 * (0.3 + 0.7 * babble[i]) * trickle[i];
    if (t >= nextDrip) {
      const pitch = 1500 + 900 * garden();
      for (let k = 0; k < Math.round(0.06 * RATE) && i + k < n; k++) {
        const at = k / RATE;
        air[i + k] += 0.35 * Math.exp(-at / 0.012) * Math.sin(2 * Math.PI * pitch * (1 - 2 * at) * at);
      }
      nextDrip = t + 1.5 + 2.5 * garden();
    }
  }
  writeSound("greenhouse_air", seamlessNoise(air, ROOM_LOOP, ROOM_FADE), 0.5);

  const beeLength = 2.4;
  const bee = new Float64Array(Math.round(beeLength * RATE));
  let beePhase = 0;
  for (let i = 0; i < bee.length; i++) {
    const t = i / RATE;
    const along = t / beeLength;
    beePhase += (232 + 24 * (0.5 - along) + 6 * Math.sin(2 * Math.PI * 7 * t)) / RATE;
    const saw = 2 * (beePhase % 1) - 1;
    const throb = 1 + 0.35 * Math.sin(2 * Math.PI * 5.5 * t);
    bee[i] = Math.sin(Math.PI * along) ** 2 * throb * saw;
  }
  const beeBody = resonance(bee, 700, 0.5);
  const beeEdge = resonance(bee, 1900, 1.5);
  writeSound("greenhouse_bee", beeBody.map((b, i) => b + 0.4 * beeEdge[i]), 0.6);

  const pool = ownRandom(0x506f6f6c);
  const laps = new Float64Array(n);
  let nextLap = 0;
  while (nextLap < ROOM_LOOP + ROOM_FADE) {
    const start = Math.round(nextLap * RATE);
    const decay = 0.15 + 0.15 * pool();
    const gain = 0.4 + 0.6 * pool();
    for (let k = 0; k < Math.round(0.8 * RATE) && start + k < n; k++) {
      const at = k / RATE;
      laps[start + k] += gain * Math.min(1, at / 0.03) * Math.exp(-at / decay) * (pool() * 2 - 1);
    }
    nextLap += 0.35 + 0.6 * pool();
  }
  const lapping = resonance(laps, 600, 1.1);
  const bed = resonance(noiseOf(pool, n), 420, 0.9);
  const gurgle = resonance(noiseOf(pool, n), 950, 4);
  const bubbles = wobbleOf(pool, n, 25);
  const water = lapping.map((l, i) => l + 0.12 * bed[i] + 0.8 * bubbles[i] * bubbles[i] * gurgle[i]);
  writeSound("pool_water", seamlessNoise(tiledEcho(water), ROOM_LOOP, ROOM_FADE), 0.5);

  const slap = new Float64Array(Math.round(1.2 * RATE));
  for (let i = 0; i < Math.round(0.5 * RATE); i++) {
    const t = i / RATE;
    slap[i] = Math.min(1, t / 0.006) * (Math.exp(-t / 0.15) * (pool() * 2 - 1));
  }
  const slapBody = resonance(slap, 420, 0.8);
  const slapSpray = resonance(slap, 3000, 1.2);
  writeSound("pool_slap", tiledEcho(slapBody.map((b, i) => b + 0.25 * slapSpray[i] * Math.exp(-i / (0.05 * RATE)))), 0.7);
}

// The greenhouse's irrigation (see IrrigationValveBlock and SprinklerBlockEntity), each from a random sequence of its
// own so the sounds above are unchanged. The valve: its hand wheel squeaking round in three jerks, a rising, rasping
// chirp each, then the pipe knocking as the water hits it and rushing, bubbling, up it. The sprinklers, as long as the
// valve stays open: an impulse sprinkler's hiss, chopped as its arm ticks round through the jet some six times a
// second, every few seconds the arm rattling quickly back, and drops pattering on the leaves, swelling in and easing off.
{
  const VALVE_LENGTH = 1.4;
  const valve = ownRandom(0x56616c76);
  const squeaks = new Float64Array(Math.round(VALVE_LENGTH * RATE));
  for (const [at, seconds, from, to] of [[0, 0.16, 1250, 1600], [0.22, 0.14, 1350, 1750], [0.42, 0.18, 1200, 1550]]) {
    const start = Math.round(at * RATE);
    let phase = 0;
    for (let k = 0; k < Math.round(seconds * RATE); k++) {
      const along = k / (seconds * RATE);
      phase += (from + (to - from) * along) / RATE;
      const stickSlip = 0.6 + 0.4 * Math.sign(Math.sin((2 * Math.PI * 38 * k) / RATE));
      squeaks[start + k] += Math.sin(Math.PI * along) * stickSlip * (2 * (phase % 1) - 1);
    }
  }
  const squeakEdge = resonance(squeaks, 3100, 2);
  const squeak = resonance(squeaks, 1500, 1.5).map((s, i) => s + 0.3 * squeakEdge[i]);
  const rush = new Float64Array(squeaks.length);
  const knockAt = Math.round(0.62 * RATE);
  const rushNoise = resonance(Float64Array.from({ length: rush.length }, () => valve() * 2 - 1), 900, 1.2);
  for (let i = knockAt; i < rush.length; i++) {
    const t = (i - knockAt) / RATE;
    const knock = Math.exp(-t / 0.09) * Math.sin(2 * Math.PI * 170 * t);
    const swell = Math.min(1, t / 0.25) * Math.min(1, (VALVE_LENGTH - 0.62 - t) / 0.35);
    const bubbles = 0.5 + 0.5 * Math.abs(Math.sin(2 * Math.PI * 11 * t + 3 * Math.sin(2 * Math.PI * 1.7 * t)));
    rush[i] = 0.9 * knock + 0.7 * swell * bubbles * rushNoise[i];
  }
  writeSound("irrigation_valve", squeak.map((s, i) => 0.6 * s + rush[i]), 0.7);

  const SPRAY_LENGTH = 8;
  const spray = ownRandom(0x53707279);
  const n = Math.round(SPRAY_LENGTH * RATE);
  const hiss = resonance(Float64Array.from({ length: n }, () => spray() * 2 - 1), 5200, 0.7);
  const patter = resonance(Float64Array.from({ length: n }, () => spray() * 2 - 1), 2600, 1.4);
  const ticks = new Float64Array(n);
  const chop = new Float64Array(n).fill(1);
  let at = 0.15;
  let returnAt = 2.6;
  while (at < SPRAY_LENGTH - 0.3) {
    const start = Math.round(at * RATE);
    for (let k = 0; k < Math.round(0.03 * RATE) && start + k < n; k++) {
      const t = k / RATE;
      ticks[start + k] += Math.exp(-t / 0.004) * (spray() * 2 - 1);
      chop[start + k] = 0.55 + 0.45 * Math.min(1, t / 0.03);
    }
    const returning = at >= returnAt && at < returnAt + 0.7;
    at += returning ? 0.055 : 0.17 + 0.02 * spray();
    if (at >= returnAt + 0.7) {
      returnAt += 3.2 + spray();
    }
  }
  const tick = resonance(ticks, 3400, 1.5);
  const drops = Float64Array.from({ length: n }, () => (spray() < 0.0006 ? spray() * 2 - 1 : 0));
  const dropping = resonance(drops, 2100, 6);
  const sprinkling = new Float64Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const envelope = Math.min(1, t / 0.3) * Math.min(1, (SPRAY_LENGTH - t) / 0.8);
    sprinkling[i] = envelope * (0.5 * hiss[i] * chop[i] + 0.25 * patter[i] + 1.4 * tick[i] + 2.5 * dropping[i]);
  }
  writeSound("sprinkler_spray", sprinkling, 0.6);
}

// Low gravity aboard a rocket in space (see SpaceGravity): coming on, a soft glide rising an octave over most of a
// second, a sine with a quiet fifth over it shimmering; going, the same glide falling. No random sequence is drawn on,
// so the sounds around it are unchanged.
{
  const glide = (from, to) => {
    const samples = new Float64Array(Math.round(0.9 * RATE));
    let phase = 0;
    for (let i = 0; i < samples.length; i++) {
      const t = i / RATE;
      const along = Math.min(1, t / 0.8);
      const eased = along * along * (3 - 2 * along);
      phase += 2 * Math.PI * from * Math.pow(to / from, eased) / RATE;
      const swell = Math.sin(Math.PI * Math.min(1, t / 0.9)) ** 1.2;
      const shimmer = 1 + 0.2 * Math.sin(2 * Math.PI * 6 * t);
      samples[i] = swell * (Math.sin(phase) + 0.25 * shimmer * Math.sin(1.5 * phase));
    }
    return samples;
  };
  writeSound("space_light", glide(440, 880), 0.5);
  writeSound("space_heavy", glide(880, 440), 0.5);
}

// The lift's music, played in the car as it rides (see the client's LiftSounds), with a random sequence of its own so
// the sounds above are unchanged: sixteen seconds of bossa nova at 120 beats a minute in F, eight bars of Fmaj7, Dm9,
// Gm7 and C9 played twice round, looped for as long as a ride lasts. An electric piano, each key a sine with the bell
// partial of its struck tine ringing over it for a moment, comps the chords on the bossa's clave and plays the tune
// over them, its hook in the first bar and a half, since a hop of one floor lasts under three seconds; a plucked sine
// bass steps between each chord's root and fifth, long then short, as bossa basses do; and a soft shaker ticks the
// eighths, the offbeats a little harder. The players lay back a few milliseconds behind the beat and never quite strike
// two notes alike. Every note is written round the loop and has stopped before its end, and the piano's tremolo turns a
// whole number of times in it, so it plays on without a seam, and each ride's music starts from silence on the hook.
{
  const bossa = ownRandom(0x426f7373);
  const beat = 60 / 120;
  const length = Math.round(8 * 4 * beat * RATE);
  const piano = new Float64Array(length);
  const band = new Float64Array(length);
  // How hard each part plays: a key of the tune, a key of a chord, a string of the bass and a shake of the shaker.
  const [tuneTouch, chordTouch, bassTouch, shakerTouch] = [0.3, 0.12, 0.36, 0.15];
  // The piano's tremolo, in turns a second (72 round the loop) and as a share of its loudness.
  const [tremoloRate, tremoloDepth] = [4.5, 0.12];
  // How hard the mix is driven into a soft tape-like saturation, which rounds off only its rare loudest peaks.
  const drive = 0.9;
  // Writes a voice into a track from a beat on for so many seconds, wrapping round from the loop's end to its head.
  const add = (track, at, seconds, voice) => {
    const from = Math.round(at * beat * RATE);
    for (let i = 0; i < Math.round(seconds * RATE); i++) {
      track[(from + i) % length] += voice(i / RATE);
    }
  };
  const hertz = (note) => 440 * 2 ** ((note - 69) / 12);
  // How late a player plays a note, in beats, up to so many: never early, so nothing struck on the loop's first beat
  // sounds before it.
  const late = (beats) => bossa() * beats;
  // How hard a player strikes a note, as a share of the part's touch: a little different every time.
  const touch = (part) => part * (0.88 + 0.24 * bossa());
  // A key of the electric piano, a MIDI note held from a beat for so many beats and let go over 80 milliseconds: a
  // sine dying slowly away, warmed by its pickup's octave for a moment, and the bell partial of its tine's second mode,
  // 6.27 times its pitch, ringing over it for the first few hundredths of a second.
  const key = (at, beats, note, level) => {
    const frequency = hertz(note);
    const sounding = beats * beat + 0.08;
    add(piano, at + late(0.02), sounding, (t) => {
      const envelope = Math.min(1, t / 0.003) * Math.exp(-t / 1.4) * Math.min(1, (sounding - t) / 0.08);
      return level * envelope * (Math.sin(2 * Math.PI * frequency * t)
        + 0.12 * Math.exp(-t / 0.3) * Math.sin(4 * Math.PI * frequency * t)
        + 0.3 * Math.exp(-t / 0.04) * Math.sin(2 * Math.PI * 6.27 * frequency * t));
    });
  };
  // A string of the bass plucked at a beat and stopped just before the next note: a sine dying away, its octave there
  // only in the pluck's first moment.
  const pluck = (at, beats, note) => {
    const frequency = hertz(note);
    const sounding = beats * beat - 0.01;
    const level = touch(bassTouch);
    add(band, at + late(0.015), sounding, (t) => {
      const envelope = Math.min(1, t / 0.004) * Math.exp(-t / 0.7) * Math.min(1, (sounding - t) / 0.03);
      return level * envelope * (Math.sin(2 * Math.PI * frequency * t)
        + 0.35 * Math.exp(-t / 0.12) * Math.sin(4 * Math.PI * frequency * t));
    });
  };
  // A shake of the shaker at a beat: the beads' hiss, high noise swelling in over 12 milliseconds and dying away.
  const shake = (at, level) => {
    let last = 0;
    add(band, at + late(0.01), 0.15, (t) => {
      const noise = bossa() * 2 - 1;
      const hiss = noise - last;
      last = noise;
      return level * hiss * (t < 0.012 ? t / 0.012 : Math.exp(-(t - 0.012) / 0.035));
    });
  };
  // Each bar's chord, as MIDI notes: the bass's root and the fifth it steps to, and the piano's voicing over them.
  const chords = [
    { root: 41, fifth: 36, voicing: [53, 57, 60, 64] }, // Fmaj7: F A C E over F
    { root: 38, fifth: 45, voicing: [50, 53, 60, 64] }, // Dm9: D F C E over D
    { root: 43, fifth: 38, voicing: [53, 58, 62, 67] }, // Gm7: F Bb D G over G
    { root: 36, fifth: 43, voicing: [52, 58, 62, 67] }, // C9: E Bb D G over C
  ];
  // The bass's steps through each bar, as their beat, length in beats and whether it plays the root or the fifth.
  const bassSteps = [[0, 1.5, "root"], [1.5, 0.5, "fifth"], [2, 1.5, "fifth"], [3.5, 0.5, "root"]];
  // The eighths of each two bars the piano strikes its chord on, each held a beat: the bossa's clave.
  const clave = [0, 3, 6, 10, 13];
  // The tune, as each note's beat, length in beats and MIDI note: its hook falling from the major seventh over the
  // first bar and a half, an answer down through Gm7 and C9, the hook again rising higher at its end, and a climb back
  // up through C9 into the hook at the loop's head.
  const tune = [
    [0, 1.5, 76], [1.5, 0.5, 74], [2, 0.5, 72], [2.5, 1, 69], [3.5, 1.5, 72],
    [5, 0.5, 69], [5.5, 1, 74], [6.5, 1.5, 76],
    [8.5, 0.5, 77], [9, 0.5, 76], [9.5, 1, 74], [10.5, 0.5, 70], [11, 1, 74],
    [12.5, 0.5, 67], [13, 0.5, 70], [13.5, 1, 74], [14.5, 1.5, 72],
    [16, 1.5, 76], [17.5, 0.5, 74], [18, 0.5, 72], [18.5, 1, 69], [19.5, 1.5, 72],
    [21, 0.5, 69], [21.5, 1, 74], [22.5, 1.5, 77],
    [24.5, 0.5, 74], [25, 0.5, 72], [25.5, 1, 70], [26.5, 0.5, 69], [27, 1, 67],
    [28.5, 0.5, 67], [29, 0.5, 69], [29.5, 0.5, 70], [30, 1, 72], [31, 0.75, 74],
  ];
  for (let bar = 0; bar < 8; bar++) {
    const chord = chords[bar % 4];
    for (const [step, beats, which] of bassSteps) {
      pluck(bar * 4 + step, beats, chord[which]);
    }
    for (let eighth = 0; eighth < 8; eighth++) {
      shake(bar * 4 + eighth / 2, touch(shakerTouch) * (eighth % 2 === 1 ? 1 : 0.65));
    }
  }
  // A chord struck on the piano at a beat, its keys all struck alike and held a beat.
  const strike = (at, chord) => {
    const level = touch(chordTouch);
    chord.voicing.forEach((note) => key(at, 1, note, level));
  };
  for (let bar = 0; bar < 8; bar += 2) {
    clave.forEach((eighth) => strike(bar * 4 + eighth / 2, chords[(bar + Math.floor(eighth / 8)) % 4]));
  }
  for (const [at, beats, note] of tune) {
    key(at, beats, note, touch(tuneTouch));
  }
  const tremolo = (i) => 1 + tremoloDepth * Math.sin(2 * Math.PI * tremoloRate * i / RATE);
  const music = piano.map((sample, i) => Math.tanh(drive * (sample * tremolo(i) + band[i])));
  writeSound("lift_music", music, 0.8);
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

  // A boost (see the client's RocketRoar and the autopilot's RocketControls), with a random sequence of its own so the
  // sounds above are unchanged: her engines kicking up over the loop roaring on under it, as her ignition is built. A
  // crack and a deep whump sweeping up from 28 to 68 hertz, and her roar surging in over a quarter of a second, its
  // low-pass flung open past the loop's and its crackle two and a half times as thick, easing back into the loop over
  // two seconds. Written to the loop's scale, and heard aboard through her hull as the rest are.
  const boostRandom = ownRandom(0x4b69636b);
  const kickLength = 2.6;
  const kickSwell = (t) => gearRamp(t, 0, 0.25) * Math.exp(-Math.max(0, t - 0.5) / 0.7) * Math.min(1, (kickLength - t) / 0.3);
  const kicking = rocketRoar(kickLength, boostRandom, (t) => 300 + (1.4 * ROCKET.rumbleCutoff - 300) * gearRamp(t, 0, 0.4),
    (t) => 2.5 * ROCKET.crackles * gearRamp(t, 0.05, 0.3));
  const kick = mix(kicking, kickSwell, (t) => 1.3 * kickSwell(t), (t) => 1.6 * kickSwell(t));
  let kickPhase = 0;
  for (let i = 0; i < kick.length; i++) {
    const t = i / RATE;
    kickPhase += (2 * Math.PI * (28 + 40 * gearRamp(t, 0, 0.3))) / RATE;
    const whump = Math.min(1, t / 0.015) * Math.exp(-t / 0.35) * (Math.sin(kickPhase) + 0.4 * Math.sin(2 * kickPhase));
    const crack = t < 0.01 ? 3 * (boostRandom() * 2 - 1) * Math.exp(-t / 0.003) : 0;
    kick[i] += 3 * whump + crack;
  }
  const kicked = softened(kick);
  writeSound("rocket_boost", kicked, peakOf(kicked) * scale);
  const kickedAboard = hull(kicked);
  writeSound("rocket_boost_aboard", kickedAboard, peakOf(kickedAboard) * aboardScale);
}

// The rocket's centre console (see the autopilot's RocketControls), pure tones, so no sound above shifts. The boost
// button ready again: two bright beeps rising a fifth, each gliding up a little. Her countdown scrubbed: three reedy
// notes falling, as the autopilot's disconnect rises. The alarm while her stability is off: a hard two-tone warble,
// four quick turns between 960 and 720 hertz through a small horn, short enough to sound again and again, quicker the
// further she tilts.
{
  const tone = (samples, start, length, frequency, glide, reed) => {
    let phase = 0;
    for (let i = Math.round(start * RATE); i < Math.round((start + length) * RATE) && i < samples.length; i++) {
      const t = i / RATE - start;
      phase += (2 * Math.PI * frequency * (1 + (glide * t) / length)) / RATE;
      const envelope = Math.min(1, t * 400) * Math.min(1, (length - t) * 150);
      samples[i] += envelope * (Math.sin(phase) + reed * Math.sin(3 * phase) + (reed / 2) * Math.sin(5 * phase));
    }
  };
  const ready = new Float64Array(Math.round(0.42 * RATE));
  tone(ready, 0, 0.13, 880, 0.06, 0.1);
  tone(ready, 0.15, 0.2, 1318.5, 0.06, 0.1);
  writeSound("rocket_boost_ready", ready, 0.6);
  const scrub = new Float64Array(Math.round(0.75 * RATE));
  [1174.7, 880, 587.3].forEach((frequency, k) => tone(scrub, k * 0.2, 0.18, frequency, -0.03, 0.3));
  writeSound("rocket_scrub", scrub, 0.6);
  const alarm = new Float64Array(Math.round(0.5 * RATE));
  for (let turn = 0; turn < 8; turn++) {
    tone(alarm, turn * 0.0625, 0.0625, turn % 2 === 0 ? 960 : 720, 0, 0.6);
  }
  const horn = resonance(alarm, 1800, 2.5);
  writeSound("rocket_stability_alarm", alarm.map((sample, i) => Math.tanh(1.4 * (sample + 1.2 * horn[i]))), 0.7);
}

// A rocket's lift-off heard close by, with a random sequence of its own so the sounds above are unchanged (the
// client's RocketRoar and the autopilot's RocketLaunchBlast). Her roar's thunder near her: the same roar heavier and
// rougher, its rumble lower, its crackle three times as thick and a deeper sub under it, looped to her roar's length,
// played beside it round her feet while she is low over the ground. And, with a random sequence of its own, her
// shockwave as it passes the listener: the crack of its front, the air shoved out and drawn back (an N-wave), a boom
// falling from 110 to 40 hertz, the blast's body of low noise and its roar round 300 hertz, and its rumble rolling away
// for seconds, crackling as it goes.
const ROCKET_NEAR = {
  // The thunder's low-pass, in hertz; its crackles a second and how loud they are beside its rumble, as a share of
  // the rumble's root mean square; and its sub's pitch, a whole number of turns round the loop, and its share.
  rumbleCutoff: 380,
  crackles: 120,
  crackleShare: 0.6,
  sub: 24,
  subShare: 1.1,
};
const ROCKET_SHOCK = {
  // How long the shockwave rings on, in seconds, and the middle of its blast's roar, in hertz: low enough to be heavy,
  // high enough for small speakers to give it.
  seconds: 4.5,
  blastCentre: 300,
  // Its parts' weights before it is levelled, measured to open with the crack and boom some 4 decibels under full and
  // fall 25 under by its tail, and to be as loud above 150 hertz as her roar is (or louder).
  weights: { crack: 8, front: 5, sub: 5, blast: 20, roar: 45, rumble: 12, crackle: 1.2 },
  // How hard it is driven into a soft saturation once levelled, which fattens the boom's harmonics.
  drive: 3,
};
{
  const nearRandom = ownRandom(0x4e656172);
  const near = rocketRoar(ROCKET.loop + ROCKET.crossfade, nearRandom, () => ROCKET_NEAR.rumbleCutoff, () => ROCKET_NEAR.crackles);
  const rumbleGain = 1 / rms(near.rumble);
  const hissGain = 10 ** (-ROCKET.hissUnder / 20) / rms(near.hiss);
  const crackleGain = ROCKET_NEAR.crackleShare / rms(near.crackle);
  const mixed = near.rumble.map((r, i) => rumbleGain * r + hissGain * near.hiss[i] + crackleGain * near.crackle[i]);
  const noise = seamlessNoise(mixed, ROCKET.loop, ROCKET.crossfade);
  const thunder = softened(noise.map((n, i) => n + ROCKET_NEAR.subShare * Math.sin((2 * Math.PI * ROCKET_NEAR.sub * i) / RATE)));
  writeSound("rocket_roar_near", thunder, ROCKET.level);
}
{
  const shockRandom = ownRandom(0x53686f63);
  const shock = new Float64Array(Math.round(ROCKET_SHOCK.seconds * RATE));
  const band = resonance(Float64Array.from(shock, () => shockRandom() * 2 - 1), ROCKET_SHOCK.blastCentre, 0.7);
  let boom = 0;
  let brown = 0;
  let body = 0;
  let rolling = 0;
  for (let i = 0; i < shock.length; i++) {
    const t = i / RATE;
    const white = shockRandom() * 2 - 1;
    // The front: a crack, and the air shoved out at once and drawn back over 70 milliseconds, its two edges sharp.
    const crack = t < 0.015 ? white * Math.exp(-t / 0.004) : 0;
    const front = t < 0.07 ? 1 - (2 * t) / 0.07 : 0;
    boom += (2 * Math.PI * (40 + 70 * Math.exp(-t / 0.25))) / RATE;
    const sub = Math.min(1, t / 0.004) * Math.exp(-t / 0.8) * (Math.sin(boom) + 0.5 * Math.sin(2 * boom) + 0.25 * Math.sin(3 * boom));
    brown = (brown + 0.02 * white) * 0.996;
    body += 0.12 * (brown - body);
    rolling += 0.015 * (brown - rolling);
    const blast = Math.min(1, t / 0.01) * Math.exp(-t / 0.35) * body;
    const roar = Math.min(1, t / 0.005) * Math.exp(-t / 0.45) * band[i];
    const rumble = Math.min(1, t / 0.3) * Math.exp(-t / 1.4) * (1 + 0.3 * Math.sin(2 * Math.PI * 1.7 * t)) * rolling;
    const { weights } = ROCKET_SHOCK;
    shock[i] = weights.crack * crack + weights.front * front + weights.sub * sub + weights.blast * blast + weights.roar * roar + weights.rumble * rumble;
  }
  for (let t = 0.04; t < ROCKET_SHOCK.seconds - 0.5; t += -Math.log(1 - shockRandom()) / (5 + 90 * Math.exp(-t / 0.6))) {
    click(shock, t, 900 + 2600 * shockRandom(), ROCKET_SHOCK.weights.crackle * (0.15 + 0.35 * shockRandom()) * Math.exp(-t / 1.2), shockRandom);
  }
  const peak = peakOf(shock);
  writeSound("rocket_shockwave", shock.map((sample) => Math.tanh((ROCKET_SHOCK.drive * sample) / peak)), 0.95);
}

// The rocket's tardigrade, with a random sequence of its own so the sounds above are unchanged. Its purr: a soft, low
// "mrrp", a closed-mouth hum opening into a voice that rises from 380 to 620 hertz, rolled in its middle as a cat's
// trill is, with a breath under it. Its bloop as it curls up into its tun: a round drop of a note falling from 500 to
// 180 hertz, a little bubble at its start. Its snore asleep: a slow breath in, the soft palate fluttering low as it
// goes, a hum at 95 hertz rattled 26 times a second, and out again in a long soft puff of breath through a warm
// resonance, the two swelling and fading as a sleeper's breaths do. Its slurp sucking in what is thrown into its tank:
// a rush of air through a narrowing mouth, its pitch sweeping up from 300 to 1500 hertz as it swells, ending in a
// gulp. Its burp after a meal: a deep rough voice at about 80 hertz, wobbling and rattled, through a hollow resonance.
// Its jump through the spore drive: a shimmering hum charging up from 200 to 900 hertz, then a deep whump; and its
// return, the same played backwards, whooshing in.
{
  const tardigradeRandom = ownRandom(0x54617264);
  const PURR_SECONDS = 0.45;
  const purr = new Float64Array(Math.round(PURR_SECONDS * RATE));
  let phase = 0;
  let breath = 0;
  for (let i = 0; i < purr.length; i++) {
    const t = i / RATE;
    const along = t / PURR_SECONDS;
    const pitch = 380 + 240 * along ** 1.6;
    phase += (2 * Math.PI * pitch) / RATE;
    const open = Math.min(1, Math.max(0, (t - 0.05) / 0.08));
    const voice = Math.sin(phase) + open * (0.45 * Math.sin(2 * phase) + 0.2 * Math.sin(3 * phase) + 0.08 * Math.sin(4 * phase));
    const roll = t > 0.1 && t < 0.3 ? 0.6 + 0.4 * Math.sin(2 * Math.PI * 28 * t) : 1;
    const envelope = Math.min(1, t / 0.03) * Math.min(1, (PURR_SECONDS - t) / 0.12);
    breath += 0.08 * (tardigradeRandom() * 2 - 1 - breath);
    purr[i] = envelope * (roll * voice + 0.15 * breath);
  }
  const warm = resonance(purr, 900, 1.2);
  writeSound("tardigrade_mrrp", purr.map((s, i) => s + 0.5 * warm[i]), 0.6);
  const BLOOP_SECONDS = 0.35;
  const bloop = new Float64Array(Math.round(BLOOP_SECONDS * RATE));
  let dropPhase = 0;
  let bubblePhase = 0;
  for (let i = 0; i < bloop.length; i++) {
    const t = i / RATE;
    dropPhase += (2 * Math.PI * (180 + 320 * Math.exp(-t / 0.08))) / RATE;
    bubblePhase += (2 * Math.PI * (900 - 500 * Math.min(1, t / 0.05))) / RATE;
    const drop = Math.min(1, t / 0.01) * Math.exp(-t / 0.12) * Math.sin(dropPhase);
    const bubble = t < 0.05 ? 0.25 * Math.sin(Math.PI * t / 0.05) * Math.sin(bubblePhase) : 0;
    bloop[i] = drop + bubble + 0.02 * (tardigradeRandom() * 2 - 1) * Math.exp(-t / 0.03);
  }
  writeSound("tardigrade_bloop", bloop, 0.55);
  const SNORE_SECONDS = 1.9;
  const BREATH_IN = 0.8;
  const BREATH_OUT_FROM = 0.95;
  const BREATH_OUT = 0.9;
  const snore = new Float64Array(Math.round(SNORE_SECONDS * RATE));
  let humPhase = 0;
  let air = 0;
  for (let i = 0; i < snore.length; i++) {
    const t = i / RATE;
    const breathIn = t < BREATH_IN ? Math.sin((Math.PI * t) / BREATH_IN) : 0;
    const breathOut = t > BREATH_OUT_FROM ? Math.sin(Math.PI * Math.min(1, (t - BREATH_OUT_FROM) / BREATH_OUT)) : 0;
    humPhase += (2 * Math.PI * 95) / RATE;
    const rattle = 0.55 + 0.45 * Math.sin(2 * Math.PI * 26 * t);
    const hum = rattle * (Math.sin(humPhase) + 0.5 * Math.sin(2 * humPhase) + 0.2 * Math.sin(3 * humPhase));
    air += 0.1 * (tardigradeRandom() * 2 - 1 - air);
    snore[i] = breathIn * breathIn * (0.6 * hum + 0.35 * air) + breathOut * 0.8 * air;
  }
  const hush = resonance(snore, 420, 1.5);
  writeSound("tardigrade_snore", snore.map((sample, i) => sample + 0.6 * hush[i]), 0.45);
  // A band of noise whose middle sweeps from one pitch to another over a sound, swelling as it goes.
  const sweptAir = (seconds, fromHz, toHz, q) => {
    const out = new Float64Array(Math.round(seconds * RATE));
    let [x1, x2, y1, y2] = [0, 0, 0, 0];
    for (let i = 0; i < out.length; i++) {
      const along = i / out.length;
      const w = (2 * Math.PI * (fromHz + (toHz - fromHz) * along ** 1.5)) / RATE;
      const alpha = Math.sin(w) / (2 * q);
      const x = tardigradeRandom() * 2 - 1;
      const y = (alpha * x - alpha * x2 + 2 * Math.cos(w) * y1 - (1 - alpha) * y2) / (1 + alpha);
      [x2, x1, y2, y1] = [x1, x, y1, y];
      out[i] = y;
    }
    return out;
  };
  const SLURP_SECONDS = 0.7;
  const SUCK = 0.6;
  const slurp = sweptAir(SLURP_SECONDS, 300, 1500, 3);
  let gulpPhase = 0;
  for (let i = 0; i < slurp.length; i++) {
    const t = i / RATE;
    const swell = Math.min(1, t / SUCK) ** 2 * (t < SUCK ? 1 : 0);
    gulpPhase += (2 * Math.PI * (200 + 400 * Math.exp(-Math.max(0, t - SUCK) / 0.03))) / RATE;
    const gulp = t >= SUCK ? Math.exp(-(t - SUCK) / 0.04) * Math.sin(gulpPhase) : 0;
    slurp[i] = swell * slurp[i] * 3 + 0.8 * gulp;
  }
  writeSound("tardigrade_slurp", slurp, 0.5);
  const BURP_SECONDS = 0.8;
  const burp = new Float64Array(Math.round(BURP_SECONDS * RATE));
  let voicePhase = 0;
  let wobble = 0;
  for (let i = 0; i < burp.length; i++) {
    const t = i / RATE;
    wobble += 0.002 * (tardigradeRandom() * 2 - 1 - wobble);
    const pitch = 80 * (1 + 0.12 * Math.sin(2 * Math.PI * 3 * t) + 4 * wobble) * (1 - 0.15 * t / BURP_SECONDS);
    voicePhase += (2 * Math.PI * pitch) / RATE;
    const saw = 2 * ((voicePhase / (2 * Math.PI)) % 1) - 1;
    const rattle = 0.6 + 0.4 * Math.abs(Math.sin(2 * Math.PI * 23 * t + 3 * wobble));
    const envelope = Math.min(1, t / 0.04) * Math.min(1, (BURP_SECONDS - t) / 0.25);
    burp[i] = envelope * rattle * saw;
  }
  const hollow = resonance(burp, 380, 2.5);
  writeSound("tardigrade_burp", burp.map((sample, i) => 0.4 * sample + hollow[i]), 0.6);
  const JUMP_SECONDS = 1.6;
  const CHARGE = 1.1;
  const jump = new Float64Array(Math.round(JUMP_SECONDS * RATE));
  let humPhase2 = 0;
  let whumpPhase = 0;
  for (let i = 0; i < jump.length; i++) {
    const t = i / RATE;
    const charging = Math.min(1, t / CHARGE);
    humPhase2 += (2 * Math.PI * (200 + 700 * charging ** 2)) / RATE;
    const shimmer = 0.6 + 0.4 * Math.sin(2 * Math.PI * (6 + 18 * charging) * t);
    const hum = t < CHARGE ? charging * shimmer * (Math.sin(humPhase2) + 0.3 * Math.sin(2 * humPhase2) + 0.15 * Math.sin(3.01 * humPhase2)) : 0;
    whumpPhase += (2 * Math.PI * (55 + 120 * Math.exp(-Math.max(0, t - CHARGE) / 0.05))) / RATE;
    const whump = t >= CHARGE ? Math.exp(-(t - CHARGE) / 0.18) * (Math.sin(whumpPhase) + 0.3 * (tardigradeRandom() * 2 - 1)) : 0;
    jump[i] = 0.5 * hum + 1.2 * whump;
  }
  writeSound("tardigrade_jump", jump, 0.6);
  writeSound("tardigrade_return", jump.slice().reverse(), 0.6);
}

// The lab's food lab, with a random sequence of its own so the sounds above are unchanged. The bioreactor's harvest:
// its cap's seal letting go in a bright hiss of steam, then the steak drawn out of the broth in a wet, wobbling squelch
// falling from 320 to 110 hertz, and three bubbles blipping up after it. The sample centrifuge's spin, four seconds as
// its block times it: a motor's whine climbing from 80 to 540 hertz over a second and a quarter with the rush of air
// round its rotor, a faint wobble of an unbalanced tube on it, holding, then winding down to a hum. And its ding as it
// stops: a small bell's note at G6 with its inharmonic partials, rung once and ringing away.
{
  const lab = ownRandom(0x4c616221);
  const HARVEST_SECONDS = 1.4;
  const harvest = new Float64Array(Math.round(HARVEST_SECONDS * RATE));
  const hiss = resonance(Float64Array.from({ length: harvest.length }, () => lab() * 2 - 1), 4200, 0.9);
  const slosh = resonance(Float64Array.from({ length: harvest.length }, () => lab() * 2 - 1), 650, 2.5);
  let squelchPhase = 0;
  for (let i = 0; i < harvest.length; i++) {
    const t = i / RATE;
    const steam = Math.min(1, t / 0.008) * Math.exp(-t / 0.22);
    const pull = t - 0.28;
    let squelch = 0;
    if (pull > 0) {
      squelchPhase += (2 * Math.PI * (110 + 210 * Math.exp(-pull / 0.18)) * (1 + 0.06 * Math.sin(2 * Math.PI * 23 * pull))) / RATE;
      const swell = Math.min(1, pull / 0.04) * Math.exp(-pull / 0.25);
      squelch = swell * (0.7 * Math.sin(squelchPhase) + 1.4 * slosh[i] * (0.6 + 0.4 * Math.sin(2 * Math.PI * 17 * pull)));
    }
    harvest[i] = 0.9 * steam * hiss[i] + squelch;
  }
  for (const [at, pitch] of [[0.74, 640], [0.9, 820], [1.05, 720]]) {
    const start = Math.round(at * RATE);
    let phase = 0;
    for (let k = 0; k < Math.round(0.05 * RATE) && start + k < harvest.length; k++) {
      const t = k / RATE;
      phase += (2 * Math.PI * pitch * (1 + 8 * t)) / RATE;
      harvest[start + k] += 0.35 * Math.sin(Math.PI * t / 0.05) * Math.sin(phase);
    }
  }
  writeSound("bioreactor_harvest", harvest, 0.7);

  const SPIN_SECONDS = 4;
  const UP = 1.25;
  const DOWN_FROM = 2.6;
  const spin = new Float64Array(Math.round(SPIN_SECONDS * RATE));
  const air = Float64Array.from({ length: spin.length }, () => lab() * 2 - 1);
  const rush = resonance(air, 2400, 0.7);
  let motorPhase = 0;
  let rotorPhase = 0;
  for (let i = 0; i < spin.length; i++) {
    const t = i / RATE;
    const rising = Math.min(1, t / UP);
    const falling = Math.max(0, (t - DOWN_FROM) / (SPIN_SECONDS - DOWN_FROM));
    const speed = (rising * rising * (3 - 2 * rising)) * (1 - falling * falling * (3 - 2 * falling));
    const pitch = 80 + 460 * speed;
    motorPhase += (2 * Math.PI * pitch) / RATE;
    rotorPhase += (2 * Math.PI * pitch) / 8 / RATE;
    const whine = Math.sin(motorPhase) + 0.45 * Math.sin(2 * motorPhase) + 0.2 * Math.sin(3 * motorPhase);
    const wobble = 1 + 0.12 * Math.sin(rotorPhase);
    const envelope = Math.min(1, t / 0.05) * Math.min(1, (SPIN_SECONDS - t) / 0.3) * (0.35 + 0.65 * speed);
    spin[i] = envelope * wobble * (0.6 * whine + 1.2 * speed * rush[i]);
  }
  writeSound("centrifuge_spin", spin, 0.55);

  const DING_SECONDS = 1.5;
  const ding = new Float64Array(Math.round(DING_SECONDS * RATE));
  const NOTE = 1568;
  for (let i = 0; i < ding.length; i++) {
    const t = i / RATE;
    const strike = Math.min(1, t / 0.003);
    ding[i] = strike * (Math.exp(-t / 0.5) * Math.sin(2 * Math.PI * NOTE * t) + 0.35 * Math.exp(-t / 0.2) * Math.sin(2 * Math.PI * NOTE * 2.76 * t)
      + 0.15 * Math.exp(-t / 0.08) * Math.sin(2 * Math.PI * NOTE * 5.4 * t));
  }
  writeSound("centrifuge_ding", ding, 0.6);
}
// The rocket's airlock panel (see AirlockPanelBlock), with a random sequence of its own so the sounds round it are
// unchanged. Its cycle: the latches clunking home, then air hissing out of the vents, a bright jet over a broad rush,
// swelling and easing off over a few seconds as the pressure falls, a pump droning low under it. Its chime as it
// clears: two clean notes rising a fifth, a soft click before them.
function airlockPanelSounds() {
  const own = ownRandom(0x4169726c);
  const CYCLE_SECONDS = 3.2;
  const cycle = new Float64Array(Math.round(CYCLE_SECONDS * RATE));
  const air = Float64Array.from({ length: cycle.length }, () => own() * 2 - 1);
  const jet = resonance(air, 3400, 2.5);
  const rush = resonance(air, 900, 0.7);
  for (let i = 0; i < cycle.length; i++) {
    const t = i / RATE;
    const swell = Math.min(1, Math.max(0, (t - 0.25) / 0.2)) * Math.exp(-Math.max(0, t - 0.6) / 1.1);
    const pump = Math.min(1, t / 0.4) * Math.min(1, (CYCLE_SECONDS - t) / 0.5) * (Math.sin(2 * Math.PI * 92 * t) + 0.4 * Math.sin(2 * Math.PI * 184 * t));
    cycle[i] = swell * (0.9 * jet[i] + 0.7 * rush[i]) + 0.12 * pump;
  }
  thunk(cycle, 0.02, 70, 0.8, own);
  thunk(cycle, 0.14, 95, 0.5, own);
  writeSound("airlock_cycle", cycle, 0.8);

  const chime = new Float64Array(Math.round(1.1 * RATE));
  click(chime, 0, 2400, 0.3, own);
  bell(chime, 0.05, 659.3, 0.35);
  bell(chime, 0.3, 987.8, 0.45);
  writeSound("airlock_clear", chime, 0.6);
}
airlockPanelSounds();

// The cargo bay's parcel scanner (see ParcelScannerBlock), with a random sequence of its own so the sounds round it are
// unchanged. Its scan: a whine rising through an octave and a half, trembling as its laser sweeps, over the soft whirr
// of its belt's servo. Its verdict on a packed crate: two bright beeps, the second higher, as a till's scanner gives;
// on anything else, two low buzzes, a harsh square tone.
function parcelScannerSounds() {
  const own = ownRandom(0x5363616e);
  const SWEEP_SECONDS = 1.4;
  const sweep = new Float64Array(Math.round(SWEEP_SECONDS * RATE));
  const servo = resonance(Float64Array.from({ length: sweep.length }, () => own() * 2 - 1), 260, 1.5);
  let phase = 0;
  for (let i = 0; i < sweep.length; i++) {
    const t = i / RATE;
    const along = t / SWEEP_SECONDS;
    phase += (2 * Math.PI * (520 * Math.pow(3, along))) / RATE;
    const envelope = Math.min(1, t / 0.08) * Math.min(1, (SWEEP_SECONDS - t) / 0.15);
    const tremble = 0.75 + 0.25 * Math.sin(2 * Math.PI * 14 * t);
    sweep[i] = envelope * (tremble * (Math.sin(phase) + 0.2 * Math.sin(2 * phase)) + 0.6 * servo[i]);
  }
  writeSound("scanner_sweep", sweep, 0.5);

  // A beep or a buzz at a time in seconds, so long, its tone a sine with odd harmonics over it, so much of each.
  const tone = (samples, start, seconds, frequency, harmonics) => {
    const begin = Math.round(start * RATE);
    for (let i = 0; i < Math.round(seconds * RATE) && begin + i < samples.length; i++) {
      const t = i / RATE;
      const envelope = Math.min(1, t / 0.005) * Math.min(1, (seconds - t) / 0.01);
      let wave = 0;
      harmonics.forEach((gain, k) => (wave += (gain * Math.sin(2 * Math.PI * frequency * (2 * k + 1) * t)) / (2 * k + 1)));
      samples[begin + i] += envelope * wave;
    }
  };
  const pass = new Float64Array(Math.round(0.4 * RATE));
  tone(pass, 0, 0.09, 1320, [1, 0.3]);
  tone(pass, 0.13, 0.14, 1760, [1, 0.3]);
  writeSound("scanner_pass", pass, 0.55);
  const fail = new Float64Array(Math.round(0.6 * RATE));
  tone(fail, 0, 0.2, 150, [1, 1, 1, 1, 1, 1]);
  tone(fail, 0.28, 0.24, 140, [1, 1, 1, 1, 1, 1]);
  writeSound("scanner_fail", lowPassed(fail, 2400), 0.6);
}
parcelScannerSounds();

// The galley's fridge (see GalleyFridgeBlock), with a random sequence of its own so the sounds round it are unchanged.
// Opening: the soft pop of its seal letting go, a low thump with a breath of air drawn in, and the hum of its cooling
// swelling up behind it and fading. Shutting: a padded thud, the bottles in its door clinking twice.
function galleyFridgeSounds() {
  const own = ownRandom(0x46726467);
  const OPEN_SECONDS = 1.6;
  const open = new Float64Array(Math.round(OPEN_SECONDS * RATE));
  const breath = resonance(Float64Array.from({ length: open.length }, () => own() * 2 - 1), 700, 1.2);
  for (let i = 0; i < open.length; i++) {
    const t = i / RATE;
    const suck = t < 0.25 ? Math.sin((Math.PI * t) / 0.25) : 0;
    const hum = Math.min(1, Math.max(0, (t - 0.15) / 0.3)) * Math.min(1, (OPEN_SECONDS - t) / 0.6);
    open[i] = 0.5 * suck * breath[i] + 0.22 * hum * (Math.sin(2 * Math.PI * 100 * t) + 0.5 * Math.sin(2 * Math.PI * 200 * t) + 0.2 * Math.sin(2 * Math.PI * 300 * t));
  }
  thunk(open, 0, 85, 0.6, own);
  writeSound("fridge_open", open, 0.6);

  const close = new Float64Array(Math.round(0.5 * RATE));
  thunk(close, 0, 60, 1, own);
  click(close, 0.06, 2900, 0.25, own);
  click(close, 0.11, 3400, 0.18, own);
  writeSound("fridge_close", lowPassed(close, 5000), 0.6);
}
galleyFridgeSounds();

// The pool's wave machine (see WaveMachineBlock), with a random sequence of its own so the sounds round it are
// unchanged: ten seconds, as long as its waves run. Its pumps start with a low whine rising and settling into a drone;
// over it the water surges, a slow swell of low rushing noise every couple of seconds, each breaking in a wash of
// spray, until the pumps wind down and the last wave dies away.
function waveMachineSounds() {
  const own = ownRandom(0x57617665);
  const SECONDS = 10;
  const samples = new Float64Array(SECONDS * RATE);
  const water = Float64Array.from({ length: samples.length }, () => own() * 2 - 1);
  const rush = lowPassed(water, 520);
  const spray = resonance(water, 2600, 0.8);
  const SWELL = 2.2;
  let phase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const pumping = Math.min(1, t / 0.8) * Math.min(1, (SECONDS - t) / 1.5);
    phase += (2 * Math.PI * (55 + 25 * Math.min(1, t / 1.2) - 20 * Math.max(0, (t - (SECONDS - 1.5)) / 1.5))) / RATE;
    const along = ((t - 0.6) % SWELL) / SWELL;
    const surge = t < 0.6 ? 0 : Math.sin(Math.PI * Math.min(1, along / 0.7)) ** 2;
    const breaking = t < 0.6 ? 0 : Math.max(0, Math.sin((Math.PI * (along - 0.55)) / 0.45)) * (along > 0.55 ? 1 : 0);
    const fade = Math.min(1, (SECONDS - t) / 1.2);
    samples[i] = 0.18 * pumping * (Math.sin(phase) + 0.4 * Math.sin(2 * phase)) + fade * (1.4 * surge * rush[i] + 0.35 * breaking * spray[i]);
  }
  writeSound("wave_surge", samples, 0.75);
}
waveMachineSounds();

// The club's fog machine (see FogMachineBlock), with a random sequence of its own so the sounds round it are unchanged:
// five seconds, as long as it pumps. A burst of pressurised hiss as its valve opens, settling into a steady bright jet
// over a broad rush, its pump sputtering under it several times a second, and a sigh as the valve closes.
function fogMachineSounds() {
  const own = ownRandom(0x466f6767);
  const SECONDS = 5;
  const samples = new Float64Array(SECONDS * RATE);
  const air = Float64Array.from({ length: samples.length }, () => own() * 2 - 1);
  const jet = resonance(air, 4200, 1.8);
  const rush = resonance(air, 1300, 0.6);
  const SPUTTER = 7;
  let phase = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const burst = 1 + 0.8 * Math.exp(-t / 0.15);
    const envelope = Math.min(1, t / 0.03) * Math.min(1, (SECONDS - t) / 0.4);
    const sputter = 0.75 + 0.25 * Math.sin(2 * Math.PI * SPUTTER * t) ** 2;
    phase += (2 * Math.PI * 70) / RATE;
    const pump = Math.max(0, Math.sin(2 * Math.PI * SPUTTER * t)) * Math.sin(phase);
    samples[i] = envelope * (burst * sputter * (0.9 * jet[i] + 0.6 * rush[i]) + 0.25 * pump);
  }
  writeSound("fog_hiss", samples, 0.7);
}
fogMachineSounds();

// The DJ deck (see TechnoDeckBlock), with a random sequence of its own so the sounds round it are unchanged: a record
// scratched under the DJ's hand, a quick "wikka-wikka". On the record a sung "ahh", a buzzing voice through the two
// formants of the vowel; the hand rocks it forward and back over a quarter of a second of it, twice and then twice as
// fast, and it is heard at the speed it moves, its pitch rising and falling with the hand and dying at each turn.
function djDeckSounds() {
  const own = ownRandom(0x444a5363);
  const VOICE = 160;
  const record = new Float64Array(Math.round(0.4 * RATE));
  let phase = 0;
  for (let i = 0; i < record.length; i++) {
    phase = (phase + VOICE / RATE) % 1;
    record[i] = 2 * phase - 1 + 0.15 * (own() * 2 - 1);
  }
  const open = resonance(record, 750, 4);
  const front = resonance(record, 1200, 5);
  const sung = open.map((s, i) => s + 0.7 * front[i]);
  const SECONDS = 0.85;
  const SLOW = 4.2;
  const FAST = 7;
  const TURN = 0.48;
  const DEPTH = 0.25;
  const samples = new Float64Array(Math.round(SECONDS * RATE));
  for (let i = 0; i < samples.length; i++) {
    const t = i / RATE;
    const strokes = t < TURN ? SLOW * t : SLOW * TURN + FAST * (t - TURN);
    const at = (0.02 + DEPTH * 0.5 * (1 - Math.cos(2 * Math.PI * strokes))) * RATE;
    const k = Math.min(Math.floor(at), sung.length - 2);
    const read = sung[k] + (sung[k + 1] - sung[k]) * (at - k);
    const speed = Math.abs(Math.sin(2 * Math.PI * strokes)) ** 0.7;
    samples[i] = read * speed * Math.min(1, (SECONDS - t) / 0.05);
  }
  writeSound("dj_scratch", samples, 0.8);
}
djDeckSounds();

// The club's bar (see DrinkTapBlock and NeonSignBlock), each sound with a random sequence of its own so the sounds
// round it are unchanged. The tap's pour, about as long as a pull pours (DrinkTapBlock.POUR_TICKS): the clack of its
// handle, the stream running into the drip tray, a wash of noise with a bubbling gurgle in it, bubbles chirping as they
// rise, and over it all the soda's fizz, a dense crackle of tiny pops that goes on a moment after the stream stops.
// The neon sign switched on: its transformer's clink, then its tubes' buzz, the mains' hum and its harmonics,
// flickering on and off as the tubes strike, each strike ticking, and settling into a steady, softer buzz that fades
// out. Switched off: the clink, and the buzz dying away at once. Tune them here.
const CLUB_BAR = {
  // The pour: how long it runs, and when the stream starts and stops, in seconds; the stream's band, its centre in
  // hertz and its q; how many bubbles a second chirp in it, from and to what pitch, in hertz; and the fizz's pops a
  // second, their pitch, in hertz, and how long the fizz goes on after the stream, in seconds.
  pourSeconds: 1.4,
  streamFrom: 0.05,
  streamTo: 1.0,
  streamHz: 900,
  streamQ: 1.2,
  bubblesPerSecond: 110,
  bubbleFrom: 500,
  bubbleTo: 1900,
  popsPerSecond: 900,
  popHz: 6500,
  fizzAfter: 0.35,
  // The neon sign: how long its switching on and off run, in seconds; the mains' hum its tubes buzz at, in hertz, and
  // how many harmonics are in it; how long they flicker as they strike, and the shortest and longest flicker, in
  // seconds; and the clink's two pitches, in hertz.
  onSeconds: 1.2,
  offSeconds: 0.45,
  humHz: 100,
  harmonics: 14,
  strikeFor: 0.32,
  flickerShortest: 0.018,
  flickerLongest: 0.06,
  clinkHz: [3200, 4700],
};
/** The tubes' buzz: the mains' hum with its harmonics, the odd ones strongest, as a choke's buzz is, t seconds in. */
function neonBuzz(t) {
  let buzz = 0;
  for (let n = 1; n <= CLUB_BAR.harmonics; n++) {
    buzz += Math.sin(2 * Math.PI * CLUB_BAR.humHz * n * t) / (n % 2 === 1 ? n : 2 * n);
  }
  return buzz;
}
/** The transformer's clink as the sign is switched: two short metallic clicks, a high one just after the first. */
function neonClink(samples, own) {
  click(samples, 0, CLUB_BAR.clinkHz[0], 0.6, own);
  click(samples, 0.012, CLUB_BAR.clinkHz[1], 0.3, own);
}
function clubBarSounds() {
  const own = ownRandom(0x43727562);
  const pour = new Float64Array(Math.round(CLUB_BAR.pourSeconds * RATE));
  const water = Float64Array.from({ length: pour.length }, () => own() * 2 - 1);
  const stream = resonance(water, CLUB_BAR.streamHz, CLUB_BAR.streamQ);
  const gurgle = lowPassed(Float64Array.from({ length: pour.length }, () => own() * 2 - 1), 12);
  const pops = resonance(Float64Array.from({ length: pour.length }, () => (own() < CLUB_BAR.popsPerSecond / RATE ? own() * 2 - 1 : 0)),
    CLUB_BAR.popHz, 3);
  for (let i = 0; i < pour.length; i++) {
    const t = i / RATE;
    const running = Math.min(1, Math.max(0, (t - CLUB_BAR.streamFrom) / 0.03)) * Math.min(1, Math.max(0, (CLUB_BAR.streamTo - t) / 0.08));
    const fizzing = Math.min(1, Math.max(0, (t - CLUB_BAR.streamFrom) / 0.15)) * Math.exp(-Math.max(0, t - CLUB_BAR.streamTo) / (CLUB_BAR.fizzAfter / 3));
    pour[i] = running * stream[i] * (0.6 + 4 * Math.abs(gurgle[i])) + 2.5 * fizzing * pops[i];
  }
  for (let at = CLUB_BAR.streamFrom; at < CLUB_BAR.streamTo; at += (-Math.log(1 - own()) / CLUB_BAR.bubblesPerSecond)) {
    bubble(pour, at, CLUB_BAR.bubbleFrom + own() * (CLUB_BAR.bubbleTo - CLUB_BAR.bubbleFrom), 0.15 + 0.2 * own());
  }
  click(pour, 0, 1500, 0.5, own);
  thunk(pour, 0, 180, 0.15, own);
  writeSound("tap_pour", lowPassed(pour, 9000), 0.7);

  const on = new Float64Array(Math.round(CLUB_BAR.onSeconds * RATE));
  neonClink(on, own);
  // When the tubes flicker on and off again as they strike, on at the first, each strike ticking.
  const flickers = [];
  for (let at = 0.04; at < CLUB_BAR.strikeFor; at += CLUB_BAR.flickerShortest + own() * (CLUB_BAR.flickerLongest - CLUB_BAR.flickerShortest)) {
    flickers.push(at);
  }
  for (let k = 0; k < flickers.length; k += 2) {
    click(on, flickers[k], 5200, 0.2, own);
  }
  const struck = (t) => flickers.filter((at) => at <= t).length % 2 === 1;
  for (let i = 0; i < on.length; i++) {
    const t = i / RATE;
    const level = t >= CLUB_BAR.strikeFor ? 0.35 + 0.65 * Math.exp(-(t - CLUB_BAR.strikeFor) / 0.12) : struck(t) ? 1 : 0;
    on[i] += 0.3 * level * Math.min(1, (CLUB_BAR.onSeconds - t) / 0.5) * neonBuzz(t);
  }
  writeSound("neon_on", lowPassed(on, 5000), 0.55);

  const off = new Float64Array(Math.round(CLUB_BAR.offSeconds * RATE));
  neonClink(off, own);
  for (let i = 0; i < off.length; i++) {
    const t = i / RATE;
    off[i] += 0.3 * Math.exp(-t / 0.07) * neonBuzz(t);
  }
  writeSound("neon_off", lowPassed(off, 5000), 0.5);
}
/**
 * A bubble rising through a drink at a time in seconds: a short sine chirping up from its pitch, in hertz, as it
 * shrinks and pops, at the gain given.
 */
function bubble(samples, start, pitch, gain) {
  const begin = Math.round(start * RATE);
  let phase = 0;
  for (let i = 0; i < Math.round(0.03 * RATE) && begin + i < samples.length; i++) {
    const t = i / RATE;
    phase += (2 * Math.PI * pitch * (1 + 8 * t)) / RATE;
    samples[begin + i] += gain * Math.exp(-t / 0.008) * Math.sin(phase);
  }
}
clubBarSounds();

// The space helmet's air, looped in the helmet aboard a rocket in space (see HelmetBreathing), with a random sequence
// of its own so the sounds above are unchanged: a calm, slow breath, a few seconds round. The breath in is air drawn
// through the helmet's valve, a soft hiss in a narrow band over a wider, lower rush, swelling and easing away; a
// moment's rest; the breath out, lower and softer still, the valve's flap fluttering faintly as it lets the air go;
// another rest. Under it all lies the faint, steady hiss of the suit's air supply, crossfaded into a seamless loop;
// the breaths are silent at the loop's seam, so they need none. Each part is levelled by its root mean square, its
// share given beside it. Tune it here.
const BREATH = {
  // The loop's length, and the crossfade the air supply's hiss is made seamless over, in seconds.
  seconds: 4,
  crossfade: 0.4,
  // The breath in and the breath out: when each begins and how long it lasts, in seconds; and how far into it each
  // is at its fullest, as a share of it, the breath in drawn quicker than the breath out is let go.
  inFrom: 0.15,
  inFor: 1.5,
  inPeak: 0.4,
  outFrom: 1.95,
  outFor: 1.8,
  outPeak: 0.3,
  // The breath in: the valve's hiss, its centre in hertz, its q and its share; and the rush under it.
  valve: 2600,
  valveQ: 4,
  valveShare: 0.5,
  rush: 1100,
  rushQ: 0.8,
  rushShare: 1,
  // The breath out, as loud as a share of the breath in: its rush and the valve's flap, a lower band, fluttering this
  // many times a second, this deep.
  out: 620,
  outQ: 0.9,
  flap: 1500,
  flapQ: 2.5,
  flapShare: 0.35,
  outShare: 0.7,
  flutter: 19,
  flutterDepth: 0.25,
  // The air supply's hiss: high-passed noise, from this many hertz up, its share; and every breath softened through a
  // low-pass at this many hertz.
  supplyFrom: 3000,
  supplyShare: 0.06,
  soften: 6500,
  // The peak the loop is written at: quiet, as a breath is.
  level: 0.5,
};
/** How full a breath is a share of the way through it: swelling to its fullest at the peak given, then easing off. */
function breathSwell(along, peak) {
  if (along <= 0 || along >= 1) {
    return 0;
  }
  const phase = along < peak ? along / peak : 1 + (along - peak) / (1 - peak);
  return Math.sin((Math.PI * phase) / 2) ** 2;
}
{
  const breathRandom = ownRandom(0x42726561);
  const length = Math.round(BREATH.seconds * RATE);
  const white = Float64Array.from({ length }, () => breathRandom() * 2 - 1);
  const unit = (samples) => {
    const level = rms(samples);
    return samples.map((s) => s / level);
  };
  const valve = unit(resonance(white, BREATH.valve, BREATH.valveQ));
  const rush = unit(resonance(white, BREATH.rush, BREATH.rushQ));
  const out = unit(resonance(white, BREATH.out, BREATH.outQ));
  const flap = unit(resonance(white, BREATH.flap, BREATH.flapQ));
  const breaths = new Float64Array(length);
  for (let i = 0; i < length; i++) {
    const t = i / RATE;
    const drawn = breathSwell((t - BREATH.inFrom) / BREATH.inFor, BREATH.inPeak);
    const letGo = breathSwell((t - BREATH.outFrom) / BREATH.outFor, BREATH.outPeak);
    const flutter = 1 + BREATH.flutterDepth * Math.sin(2 * Math.PI * BREATH.flutter * t);
    breaths[i] = drawn * (BREATH.valveShare * valve[i] + BREATH.rushShare * rush[i])
      + BREATH.outShare * letGo * (out[i] + BREATH.flapShare * flutter * flap[i]);
  }
  const soft = lowPassed(breaths, BREATH.soften);
  const rendered = Math.round((BREATH.seconds + BREATH.crossfade) * RATE);
  const hiss = Float64Array.from({ length: rendered }, () => breathRandom() * 2 - 1);
  const lowered = lowPassed(hiss, BREATH.supplyFrom);
  const supply = unit(seamlessNoise(hiss.map((s, i) => s - lowered[i]), BREATH.seconds, BREATH.crossfade));
  writeSound("space_helmet_breath", soft.map((s, i) => s + BREATH.supplyShare * supply[i]), BREATH.level);
}

// The rocket's snack machine (see SnackMachineBlock), with a random sequence of its own so the sounds above are
// unchanged. Its whirr, as long as its spiral turns (SnackMachineBlock.TURN_TICKS): a little geared motor spinning up
// to a buzzing whine, the coil it turns ticking round a step at a time, and slowing to a stop as the packet tips off
// its end. Its clunk as the snack drops: the packet's crinkle as it falls, a hollow thump as it lands in the bin behind
// the flap, and the flap knocking twice after it. Tune them here.
const SNACK = {
  // How long the whirr runs and when its motor stops, in seconds; how fast it turns, in hertz, and how far the gear
  // whine is over it, in harmonics.
  whirrSeconds: 1.4,
  motorStops: 1.2,
  motorHz: 210,
  gearHarmonic: 9,
  // The coil's ticks: the first, the gap between, in seconds, and their pitch, in hertz.
  firstTick: 0.12,
  tickGap: 0.16,
  tickHz: 2400,
  // How long the clunk runs and how long the packet falls before it lands, in seconds; the thump's pitch and the
  // knocks' (the bin's, then the flap's two), in hertz, and when the flap knocks, in seconds.
  clunkSeconds: 0.6,
  falls: 0.08,
  thumpHz: 70,
  binHz: 520,
  flapHz: [900, 950],
  flapAt: [0.2, 0.29],
};
{
  const snackRandom = ownRandom(0x536e6163);
  const whirr = new Float64Array(Math.round(SNACK.whirrSeconds * RATE));
  let phase = 0;
  for (let i = 0; i < whirr.length; i++) {
    const t = i / RATE;
    const spinUp = Math.min(1, t / 0.06);
    const spinDown = t < SNACK.motorStops ? 1 : Math.exp(-(t - SNACK.motorStops) / 0.05);
    phase += (2 * Math.PI * SNACK.motorHz * (0.5 + 0.5 * spinUp) * (0.3 + 0.7 * spinDown)) / RATE;
    let motor = 0;
    for (let n = 1; n <= 6; n++) motor += Math.sin(n * phase) / n;
    const gear = 0.25 * Math.sin(SNACK.gearHarmonic * phase);
    whirr[i] = spinUp * spinDown * (0.4 * motor + gear + 0.03 * (snackRandom() * 2 - 1));
  }
  for (let tick = SNACK.firstTick; tick < SNACK.motorStops + SNACK.tickGap / 2; tick += SNACK.tickGap) {
    click(whirr, tick, SNACK.tickHz, 0.35, snackRandom);
  }
  writeSound("snack_machine_whirr", whirr, 0.5);

  const clunk = new Float64Array(Math.round(SNACK.clunkSeconds * RATE));
  let previous = 0;
  let crinkle = 0;
  for (let i = 0; i < Math.round(SNACK.falls * RATE); i++) {
    const white = snackRandom() * 2 - 1;
    const high = white - previous;
    previous = white;
    if (snackRandom() < 0.004) crinkle = 1;
    crinkle *= Math.exp(-1 / (0.002 * RATE));
    clunk[i] += 0.4 * (0.2 + crinkle) * high;
  }
  thunk(clunk, SNACK.falls, SNACK.thumpHz, 1, snackRandom);
  click(clunk, SNACK.falls, SNACK.binHz, 0.6, snackRandom);
  SNACK.flapAt.forEach((at, knock) => click(clunk, at, SNACK.flapHz[knock], 0.25 / (knock + 1), snackRandom));
  writeSound("snack_machine_clunk", clunk, 0.8);
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
  callout_touchdown: "Touchdown.",
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
 * Says every line with Kokoro in VOICE, or the voice given (its Kokoro voice, pause and loudspeakers, as VOICE has
 * them), as the kind of line given (VOICE.callout or VOICE.announcement), each stretch between its pauses on its own
 * with the quiet round it trimmed and the pause put between, and plays it through the loudspeakers and that kind's
 * levelling into a WAV at the generator's rate in the scratch folder.
 */
async function say(speaker, lines, kind, voice = VOICE) {
  for (const [name, text] of Object.entries(lines)) {
    let rate = RATE;
    const parts = [];
    for (const part of text.split("|")) {
      const spoken = await speaker.generate(part.trim(), { voice: voice.voice, speed: kind.pace });
      rate = spoken.sampling_rate;
      parts.push(trim(Float64Array.from(spoken.audio), rate));
    }
    const pause = Math.round((voice.pause / 1000) * rate);
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
    execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", said, "-af", [...voice.loudspeakers, ...kind.levelling].join(","), "-ac", "1", "-ar", `${RATE}`, "-c:a", "pcm_s16le", join(scratch, `${name}.voiced.wav`)]);
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

// The rocket's own arrival announcement, the cabin's with "spacecraft" for "aircraft" (see CabinAnnouncements).
const ROCKET_ARRIVAL = {
  announcement_arrival_rocket: "Welcome to your destination. Please remain seated until the spacecraft has come to a complete stop. | It has. | You may now leave.",
};
if (speaker) {
  await say(speaker, ROCKET_ARRIVAL, VOICE.announcement);
  writeVoiced("announcement_arrival_rocket", announcement(trim(readWav(join(scratch, "announcement_arrival_rocket.voiced.wav")))),
    VOICE.announcement, ANNOUNCEMENT_LONGEST);
}

// The rocket's flight deck announcing her artificial gravity once everyone aboard has been light a while in space
// (see SpaceGravity), made as the welcome is; its second phrase, as the welcome's does, brings it level with the rest.
const ARTIFICIAL_GRAVITY = { announcement_gravity: "Artificial gravity enabled. | Please mind your step." };
if (speaker) {
  await say(speaker, ARTIFICIAL_GRAVITY, VOICE.announcement);
  writeVoiced("announcement_gravity", announcement(trim(readWav(join(scratch, "announcement_gravity.voiced.wav")))), VOICE.announcement,
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

// Launch control counting the rocket down over the radio (see RocketSounds): an American man's voice, Kokoro's
// Michael, so it is never taken for the flight deck's own, one clip a word, each said on its second of her countdown,
// "ten" to "one", and "lift-off" as her engines light. Heard as a space centre's loop is: the radio's narrow band,
// compressed and driven a little, the whole count one transmission, opened by a Quindar tone before "ten" and closed by
// its lower partner after "lift-off", as the keying tones of Apollo's ground stations were. Tune it here.
const LAUNCH = {
  voice: "am_michael",
  pace: 1.2,
  radio: [
    "highpass=f=300:poles=2",
    "highpass=f=300:poles=2",
    "lowpass=f=3200:poles=2",
    "lowpass=f=3200:poles=2",
    "acompressor=threshold=-28dB:ratio=6:attack=2:release=60:makeup=12dB",
    "asoftclip=type=tanh:threshold=0.7",
    "equalizer=f=1700:t=q:w=1.2:g=3",
  ],
  // The Quindar tones opening and closing the transmission, in hertz; how long each sounds and the gap between it and
  // the word, in seconds; and how loud it is beside the voice's peak.
  quindar: { open: 2525, close: 2475, length: 0.15, gap: 0.04, level: 0.45 },
  // The peak it is written at: under the flight deck's callouts', as the radio's compression makes it denser.
  level: 0.7,
};
// Her countdown's words, each a clip; the first opens the transmission and the last closes it.
const LAUNCH_WORDS = {
  launch_10: "Ten.",
  launch_9: "Nine.",
  launch_8: "Eight.",
  launch_7: "Seven.",
  launch_6: "Six.",
  launch_5: "Five.",
  launch_4: "Four.",
  launch_3: "Three.",
  launch_2: "Two.",
  launch_1: "One.",
  launch_liftoff: "Liftoff!",
};
/**
 * The longest a word of the count may run, its Quindar tone and all, in seconds, well within the second before the
 * next; and the longest "lift-off" may, which nothing follows.
 */
const LAUNCH_LONGEST = 0.8;
const LIFTOFF_LONGEST = 1.2;
// Her flight's milestones, launch control calling each as she passes it (see LaunchCallouts), each a transmission of
// its own, opened and closed by the Quindar pair as the whole count is; and its call as her boost button kicks her
// engines up (see the autopilot's rocketControl).
const LAUNCH_MILESTONES = {
  launch_tower_cleared: "Tower cleared.",
  launch_max_q: "Max Q.",
  launch_main_engine_cutoff: "Main engine cut-off.",
  launch_landing_burn: "Landing burn.",
  launch_boost: "Boost!",
};
/** The longest a milestone's transmission may run, both its Quindar tones and all, in seconds (see LaunchCallouts). */
const MILESTONE_LONGEST = 2.5;

/** A Quindar tone of the pitch given, in hertz, as long as LAUNCH.quindar has it, at a peak of one. */
function quindar(frequency) {
  const { length } = LAUNCH.quindar;
  return Float64Array.from({ length: Math.round(length * RATE) }, (_, i) => Math.min(1, i / (0.005 * RATE), (length * RATE - i) / (0.005 * RATE))
    * Math.sin((2 * Math.PI * frequency * i) / RATE));
}

/**
 * Says one of launch control's lines through the radio, the Quindar tone opening a transmission before it if it opens
 * one and the tone closing one after it if it closes one, refusing it if it runs longer than the seconds given.
 */
async function launchLine(name, line, { opens, closes, longest }) {
  const spoken = await speaker.generate(line, { voice: LAUNCH.voice, speed: LAUNCH.pace });
  const trimmed = trim(Float64Array.from(spoken.audio), spoken.sampling_rate);
  const said = join(scratch, `${name}.said.wav`);
  const voiced = join(scratch, `${name}.voiced.wav`);
  writeWav(said, Array.from(trimmed, (sample) => (sample / peakOf(trimmed)) * 0.5), spoken.sampling_rate);
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", said, "-af", LAUNCH.radio.join(","), "-ac", "1", "-ar", `${RATE}`, "-c:a", "pcm_s16le", voiced]);
  const voice = trim(readWav(voiced));
  const opening = opens ? quindar(LAUNCH.quindar.open) : new Float64Array(0);
  const closing = closes ? quindar(LAUNCH.quindar.close) : new Float64Array(0);
  const gap = Math.round(LAUNCH.quindar.gap * RATE);
  const voiceAt = opens ? opening.length + gap : 0;
  const closingAt = voiceAt + voice.length + gap;
  const samples = new Float64Array(closes ? closingAt + closing.length : voiceAt + voice.length);
  samples.set(voice, voiceAt);
  const level = LAUNCH.quindar.level * peakOf(voice);
  opening.forEach((sample, i) => (samples[i] = sample * level));
  closing.forEach((sample, i) => (samples[closingAt + i] = sample * level));
  writeVoiced(name, samples, LAUNCH, longest);
}
if (speaker) {
  const words = Object.keys(LAUNCH_WORDS);
  for (const [name, word] of Object.entries(LAUNCH_WORDS)) {
    const closes = name === words.at(-1);
    await launchLine(name, word, { opens: name === words[0], closes, longest: closes ? LIFTOFF_LONGEST : LAUNCH_LONGEST });
  }
  for (const [name, line] of Object.entries(LAUNCH_MILESTONES)) {
    await launchLine(name, line, { opens: true, closes: true, longest: MILESTONE_LONGEST });
  }
}

// The robot bartender (see RobotBartenderBlock). Its servo as its head turns to a customer: a small motor's whirr,
// spinning up and settling on its mark with a gear's whine over it, and the tick of it stopping. Its serve (see
// RobotBartenderRenderer, whose steps it keeps time with): ice rattling in a steel shaker, two knocks a stroke at six
// strokes a second, the ice's swish between them, for a second and a half; then the pour, a bubbling trickle; and the
// glass set down on the bar, a soft knock and its ring. Tune them here.
const BARTENDER = {
  // The servo's whirr: how long, in seconds; its motor's pitch from and to and where it settles, in hertz; and how far
  // up its harmonics the gear's whine is.
  servoSeconds: 0.45,
  servoFrom: 300,
  servoTop: 520,
  servoSettle: 380,
  gearHarmonic: 9,
  // The serve: how long it runs, when the rattle starts and stops, the strokes a second, the ice's knocks each stroke
  // and their pitches from and to, in hertz.
  serveSeconds: 3,
  rattleFrom: 0.05,
  rattleTo: 1.5,
  strokes: 6,
  knocks: 6,
  knockLow: 2400,
  knockHigh: 4800,
  // The pour, from and to, in seconds, and its bubbles' pitches from and to, in hertz.
  pourFrom: 1.8,
  pourTo: 2.2,
  bubbleLow: 600,
  bubbleHigh: 1300,
  // The glass set down, in seconds, and its knock's and its ring's pitch, in hertz.
  setDown: 2.62,
  knockHz: 1800,
  ringHz: 3300,
};
{
  const bartenderRandom = ownRandom(0x42617274);
  const servo = new Float64Array(Math.round(BARTENDER.servoSeconds * RATE));
  let phase = 0;
  for (let i = 0; i < servo.length; i++) {
    const t = i / RATE / BARTENDER.servoSeconds;
    const pitch = t < 0.4
      ? BARTENDER.servoFrom + (BARTENDER.servoTop - BARTENDER.servoFrom) * Math.sin((Math.PI / 2) * (t / 0.4))
      : BARTENDER.servoSettle + (BARTENDER.servoTop - BARTENDER.servoSettle) * Math.exp(-(t - 0.4) / 0.15);
    phase += (2 * Math.PI * pitch) / RATE;
    let motor = 0;
    for (let n = 1; n <= 5; n++) motor += Math.sin(n * phase) / n;
    const gear = 0.2 * Math.sin(BARTENDER.gearHarmonic * phase);
    const envelope = Math.min(1, t / 0.08) * Math.min(1, (1 - t) / 0.12);
    servo[i] = envelope * (0.45 * motor + gear + 0.03 * (bartenderRandom() * 2 - 1));
  }
  click(servo, BARTENDER.servoSeconds - 0.06, 3000, 0.3, bartenderRandom);
  writeSound("bartender_servo", servo, 0.45);

  const serve = new Float64Array(Math.round(BARTENDER.serveSeconds * RATE));
  let low = 0;
  let band = 0;
  for (let i = Math.round(BARTENDER.rattleFrom * RATE); i < Math.round(BARTENDER.rattleTo * RATE); i++) {
    const t = i / RATE;
    const white = bartenderRandom() * 2 - 1;
    low += 0.05 * (white - low);
    band += 0.4 * (white - band);
    const swing = Math.abs(Math.sin(2 * Math.PI * BARTENDER.strokes * t));
    const fade = Math.min(1, (t - BARTENDER.rattleFrom) / 0.1, (BARTENDER.rattleTo - t) / 0.1);
    serve[i] += 0.25 * fade * swing * (band - low);
  }
  // Two knocks a stroke, at each end of it, each the ice's few cubes striking the tin one after another.
  for (let stroke = BARTENDER.rattleFrom; stroke < BARTENDER.rattleTo - 0.05; stroke += 1 / (2 * BARTENDER.strokes)) {
    for (let knock = 0; knock < BARTENDER.knocks; knock++) {
      const pitch = BARTENDER.knockLow + bartenderRandom() * (BARTENDER.knockHigh - BARTENDER.knockLow);
      click(serve, stroke + bartenderRandom() * 0.03, pitch, 0.5 / (1 + knock * 0.4), bartenderRandom);
    }
  }
  let trickle = 0;
  for (let i = Math.round(BARTENDER.pourFrom * RATE); i < Math.round(BARTENDER.pourTo * RATE); i++) {
    const t = (i / RATE - BARTENDER.pourFrom) / (BARTENDER.pourTo - BARTENDER.pourFrom);
    trickle += 0.08 * (bartenderRandom() * 2 - 1 - trickle);
    serve[i] += 0.5 * Math.sin(Math.PI * t) * trickle;
  }
  for (let bubble = BARTENDER.pourFrom; bubble < BARTENDER.pourTo; bubble += 0.02 + bartenderRandom() * 0.03) {
    const pitch = BARTENDER.bubbleLow + bartenderRandom() * (BARTENDER.bubbleHigh - BARTENDER.bubbleLow);
    for (let i = 0; i < Math.round(0.03 * RATE) && Math.round(bubble * RATE) + i < serve.length; i++) {
      const t = i / RATE;
      serve[Math.round(bubble * RATE) + i] += 0.12 * Math.exp(-t / 0.008) * Math.sin(2 * Math.PI * pitch * (1 + 8 * t) * t);
    }
  }
  click(serve, BARTENDER.setDown, BARTENDER.knockHz, 0.6, bartenderRandom);
  const ring = new Float64Array(serve.length);
  bell(ring, BARTENDER.setDown, BARTENDER.ringHz, 0.08);
  ring.forEach((sample, i) => (serve[i] += 0.12 * sample));
  writeSound("bartender_shake", serve, 0.7);
}

// The robot bartender's voice, greeting a customer as its shop opens and answering each sale, a line of three at
// random each time (see robotBartenderSounds in tools/generate_resources.mjs): a British man's, Kokoro's Fable, so it
// is never taken for the flight deck's or launch control's, made a robot's without losing a word: a faint hum
// modulating it, its metal chest's short comb of echoes, a slow flanger's synthetic sheen and a touch of digital
// grit; and before each greeting its chirp, two quick tones rising. Tune it here.
const BARTENDER_VOICE = {
  voice: "bm_fable",
  pace: 1,
  // The hum it is modulated by, in hertz, and how deep, as a share of the voice.
  hum: 55,
  humDepth: 0.2,
  filters: [
    "highpass=f=170:poles=2",
    "lowpass=f=6800:poles=2",
    "aecho=0.9:0.7:6|11:0.35|0.2",
    "flanger=delay=1.5:depth=1.2:regen=30:speed=0.3",
    "acrusher=bits=11:mode=log:aa=1:mix=0.15",
    "equalizer=f=2400:t=q:w=1.2:g=3",
    "acompressor=threshold=-26dB:ratio=3:attack=3:release=90:makeup=8dB",
  ],
  // The chirp: its tones, in hertz, each so long and the gap after it, in seconds, and how loud beside the voice's
  // peak.
  chirp: [1320, 1760],
  chirpLength: 0.06,
  chirpGap: 0.02,
  chirpLevel: 0.35,
  // The peak it is written at, as the other voices'.
  level: 0.85,
};
const BARTENDER_LINES = {
  bartender_greet_1: "What'll it be?",
  bartender_greet_2: "What can I get you?",
  bartender_greet_3: "Welcome to the bar!",
  bartender_serve_1: "One coming right up.",
  bartender_serve_2: "Shaken, not stirred.",
  bartender_serve_3: "Excellent choice.",
};
/** The longest a line may run, its chirp and all, in seconds: a quick word over the bar. */
const BARTENDER_LONGEST = 2.5;
/**
 * A chirp of quick tones at a peak of one, as the settings given have it (its tones, in hertz, each one's length and
 * the gap after it, in seconds): the bartender's before a greeting, the freighter's computer's before each line.
 */
function toneChirp({ chirp, chirpLength, chirpGap }) {
  const tone = Math.round(chirpLength * RATE);
  const step = tone + Math.round(chirpGap * RATE);
  const samples = new Float64Array(step * chirp.length);
  chirp.forEach((frequency, n) => {
    for (let i = 0; i < tone; i++) {
      samples[n * step + i] = Math.min(1, i / (0.005 * RATE), (tone - i) / (0.005 * RATE)) * Math.sin((2 * Math.PI * frequency * i) / RATE);
    }
  });
  return samples;
}
if (speaker) {
  for (const [name, line] of Object.entries(BARTENDER_LINES)) {
    const spoken = await speaker.generate(line, { voice: BARTENDER_VOICE.voice, speed: BARTENDER_VOICE.pace });
    const rate = spoken.sampling_rate;
    const trimmed = trim(Float64Array.from(spoken.audio), rate);
    const peak = peakOf(trimmed);
    const hummed = trimmed.map((sample, i) => (sample / peak) * (1 - BARTENDER_VOICE.humDepth
      + BARTENDER_VOICE.humDepth * Math.sin((2 * Math.PI * BARTENDER_VOICE.hum * i) / rate)));
    const said = join(scratch, `${name}.said.wav`);
    const voiced = join(scratch, `${name}.voiced.wav`);
    writeWav(said, Array.from(hummed, (sample) => sample * 0.5), rate);
    execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", said, "-af", BARTENDER_VOICE.filters.join(","), "-ac", "1", "-ar", `${RATE}`, "-c:a", "pcm_s16le", voiced]);
    const voice = trim(readWav(voiced));
    if (!name.startsWith("bartender_greet")) {
      writeVoiced(name, voice, BARTENDER_VOICE, BARTENDER_LONGEST);
      continue;
    }
    const chirp = toneChirp(BARTENDER_VOICE);
    const samples = new Float64Array(chirp.length + voice.length);
    const level = BARTENDER_VOICE.chirpLevel * peakOf(voice);
    chirp.forEach((sample, i) => (samples[i] = sample * level));
    samples.set(voice, chirp.length);
    writeVoiced(name, samples, BARTENDER_VOICE, BARTENDER_LONGEST);
  }
}

// The freighter's sounds (see docs/plans/2026-11-06-freighter.md), each from a random sequence of its own so no sound
// above changes. All of them are synthesised here from scratch: nothing is recorded or resampled, and none imitates a
// film's sounds, its music or its creatures' voices.

/** A MIDI note's pitch, in hertz, middle C being 60. */
function noteHertz(note) {
  return 440 * 2 ** ((note - 69) / 12);
}

/** A note's name, as "C#5" or "Bb4", as a MIDI note, middle C being C4 and 60. */
function noteNumber(name) {
  const [, letter, accidental, octave] = name.match(/^([A-G])(#|b)?(-?\d)$/);
  const shift = accidental === "#" ? 1 : accidental === "b" ? -1 : 0;
  return 12 * (Number(octave) + 1) + { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 }[letter] + shift;
}

/**
 * Adds a voice into a loop from a time for so many seconds, both in seconds, wrapping round from the loop's end to its
 * head, so a note struck near the end rings on into the start: voice(t) is its sample t seconds in.
 */
function addRound(track, start, seconds, voice) {
  const from = Math.round(start * RATE);
  const count = Math.round(seconds * RATE);
  for (let i = 0; i < count; i++) {
    track[(from + i) % track.length] += voice(i / RATE);
  }
}

/** Adds samples into a loop from a time in seconds, wrapping round from its end to its head. */
function mixRound(track, start, part) {
  const from = Math.round(start * RATE);
  part.forEach((sample, i) => (track[(from + i) % track.length] += sample));
}

/**
 * What a band-limited oscillator adds round a jump in its wave so the jump does not alias into a hiss of false notes:
 * phase is how far past the jump it is, in turns, and step how far it moves a sample, in turns.
 */
function polyBlep(phase, step) {
  if (phase < step) {
    const x = phase / step;
    return x + x - x * x - 1;
  }
  if (phase > 1 - step) {
    const x = (phase - 1) / step;
    return x * x + x + x + 1;
  }
  return 0;
}

/** A band-limited sawtooth, from -1 to 1, at a phase in turns moving step turns a sample. */
function sawAt(phase, step) {
  return 2 * phase - 1 - polyBlep(phase, step);
}

/** A band-limited pulse wave at a phase in turns moving step turns a sample, high for the share of a turn given. */
function pulseAt(phase, step, duty) {
  const high = phase < duty ? 1 : -1;
  return high + polyBlep(phase, step) - polyBlep((phase - duty + 1) % 1, step) - (2 * duty - 1);
}

/**
 * The samples through a two-pole band-pass whose middle moves as it goes, centreAt(t) hertz t seconds in, its peak's
 * gain one, as resonance()'s is: a wah pedal's, a mouth's formant or a whine's band.
 */
function sweptResonance(input, centreAt, q) {
  const out = new Float64Array(input.length);
  let [x1, x2, y1, y2] = [0, 0, 0, 0];
  for (let i = 0; i < input.length; i++) {
    const w = (2 * Math.PI * centreAt(i / RATE)) / RATE;
    const alpha = Math.sin(w) / (2 * q);
    const y = (alpha * input[i] - alpha * x2 + 2 * Math.cos(w) * y1 - (1 - alpha) * y2) / (1 + alpha);
    [x2, x1, y2, y1] = [x1, input[i], y1, y];
    out[i] = y;
  }
  return out;
}

/** The samples through a two-pole high-pass at the cutoff given, in hertz, its q a Butterworth's. */
function highPassed(input, cutoff) {
  const out = new Float64Array(input.length);
  const w = (2 * Math.PI * cutoff) / RATE;
  const alpha = Math.sin(w) / (2 * Math.SQRT1_2);
  const cos = Math.cos(w);
  const [b0, b1, a0, a1, a2] = [(1 + cos) / 2, -(1 + cos), 1 + alpha, -2 * cos, 1 - alpha];
  let [x1, x2, y1, y2] = [0, 0, 0, 0];
  for (let i = 0; i < input.length; i++) {
    const y = (b0 * input[i] + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2) / a0;
    [x2, x1, y2, y1] = [x1, input[i], y1, y];
    out[i] = y;
  }
  return out;
}

/** So many seconds of white noise, from -1 to 1, from the random sequence given. */
function whiteNoise(seconds, own) {
  return Float64Array.from({ length: Math.round(seconds * RATE) }, () => own() * 2 - 1);
}

// The club's song, "Long Haul Swing" (the plan's appendix writes it out note by note), looped as the techno room's
// track is while anyone is in the club: an up-tempo minor swing of our own at 160 beats a minute, its eighths swung to
// 0.64 of the beat, in D minor with an F major bridge, a 32-bar AABA played twice, 64 bars and 96 seconds. A reedy horn
// leads, doubled a fourth below, over a kalimba comping on two and four, a wah bass walking quarters, a glass bell on
// each downbeat, a ride cymbal, brushes and a feathered kick. In the second chorus the kalimba takes the tune an octave
// up while the horn holds the bell's notes an octave down under it, the horn comes back in the bridge in full fourths,
// and in the last eight bars both play the tune in octaves, ending on a triplet fill of the brushes back into the top.
// The players lay back a hair behind the beat and never strike two notes quite alike. Every note is written round the
// loop, so nothing is cut at its seam. Tune it here.
const CLUB_BAND = {
  // Beats a minute, and where the swung "and" of each beat falls, as a share of the beat.
  tempo: 160,
  swing: 0.64,
  // How hard each part plays: a note of the horn's lead, a key of a kalimba chord, a note of the kalimba's tune, a
  // string of the bass, a strike of the bell, of the ride, of the brushes' slap and of the kick, and the brushes' swish.
  touch: { horn: 0.3, comp: 0.075, kalimba: 0.2, bass: 0.34, bell: 0.09, ride: 0.07, slap: 0.12, kick: 0.15, swish: 0.03 },
  // The horn's double a fourth below, as a share of its lead, in the first chorus and in the second chorus's bridge;
  // and the horn holding the bell's notes under the kalimba's tune, as a share of its lead.
  fourth: 0.3,
  fullFourth: 0.75,
  held: 0.6,
  // The horn: its pulse's duty; how flat each note is scooped up from, in cents, and over how long, in seconds; its
  // vibrato's rate, in hertz, and depth, in cents, and when it comes in and is full, in seconds into a note; and its
  // breath, as a share.
  duty: 0.3,
  scoopCents: 40,
  scoopSeconds: 0.03,
  vibratoRate: 5.5,
  vibratoCents: 12,
  vibratoFrom: 0.18,
  vibratoFull: 0.4,
  breath: 0.05,
  // The kalimba: how far sharp each key is struck, in cents, and its tine's inharmonic partial, as a multiple of its
  // pitch, and how fast that dies, in seconds.
  kalimbaDropCents: 20,
  kalimbaPartial: 5.4,
  kalimbaPartialDies: 0.04,
  // The wah bass's sweep over each note, in hertz: from, up to and back down to.
  wah: [300, 900, 400],
  // The bell's modulator, as a multiple of its pitch; its index at the strike, falling to nothing over so many seconds;
  // and how long it rings, in seconds.
  bellRatio: 3.5,
  bellIndex: 2,
  bellBrightFor: 0.6,
  bellRings: 1.2,
  // How hard the mix is driven into a soft saturation, and the peak it is written at.
  drive: 0.9,
  level: 0.8,
};
// The chords, each with its bass's root, its tones above the root in semitones, and the kalimba's voicing, as MIDI.
const CLUB_CHORDS = {
  Dm6: { root: 38, tones: [0, 3, 7, 9], voicing: [62, 65, 69, 71] },
  Bb7: { root: 34, tones: [0, 4, 7, 10], voicing: [62, 65, 68, 70] },
  Gm6: { root: 43, tones: [0, 3, 7, 9], voicing: [62, 64, 67, 70] },
  A7: { root: 33, tones: [0, 4, 7, 10], voicing: [61, 64, 67, 69] },
  A7b9: { root: 33, tones: [0, 4, 7, 10], voicing: [61, 64, 67, 70] },
  Em7b5: { root: 40, tones: [0, 3, 6, 10], voicing: [62, 64, 67, 70] },
  F6: { root: 41, tones: [0, 4, 7, 9], voicing: [60, 62, 65, 69] },
  D7: { root: 38, tones: [0, 4, 7, 10], voicing: [60, 62, 66, 69] },
  Gm7: { root: 43, tones: [0, 3, 7, 10], voicing: [62, 65, 67, 70] },
  C7: { root: 36, tones: [0, 4, 7, 10], voicing: [60, 64, 67, 70] },
  Bbmaj7: { root: 34, tones: [0, 4, 7, 11], voicing: [58, 62, 65, 69] },
};
// Each bar's chords of an A section and of the bridge, as the chord and the beat it comes in on, from 0; and the bell's
// notes over them, as a note and its beat.
const CLUB_A = {
  chords: [[["Dm6", 0]], [["Bb7", 0]], [["Gm6", 0]], [["A7", 0]], [["Dm6", 0]], [["Bb7", 0]], [["Em7b5", 0], ["A7b9", 2]], [["Dm6", 0]]],
  bell: [[["A5", 0]], [["Ab5", 0]], [["G5", 0]], [["G5", 0]], [["F5", 0]], [["D5", 0]], [["D5", 0], ["C#5", 2]], [["D5", 0]]],
};
const CLUB_BRIDGE = {
  chords: [[["F6", 0]], [["D7", 0]], [["Gm7", 0]], [["C7", 0]], [["F6", 0]], [["Bbmaj7", 0]], [["Em7b5", 0]], [["A7b9", 0]]],
  bell: [[["A5", 0]], [["C6", 0]], [["Bb5", 0]], [["Bb5", 0]], [["A5", 0]], [["A5", 0]], [["G5", 0]], [["G5", 0]]],
};
// The tune of a chorus, as the bars that play each line and its notes, each "name@beat/length" in beats from the bar's
// start.
const CLUB_TUNE = [
  [[1, 5, 9, 13, 25, 29], "A4@0.5/0.5 B4@1/0.5 D5@1.5/1 F5@2.5/0.5 E5@3/1"],
  [[2, 10, 26], "D5@0/0.5 C5@0.5/0.5 Ab4@1/1 F4@2/1"],
  [[3, 11, 27], "G4@0.5/0.5 Bb4@1/0.5 D5@1.5/1 E5@2.5/0.5 D5@3/1"],
  [[4, 12, 28], "C#5@0/0.5 E5@0.5/0.5 G5@1/0.5 Bb5@1.5/1 A5@2.5/0.5 G5@3/0.5 E5@3.5/0.5"],
  [[6, 14, 30], "D5@0/0.5 F5@0.5/0.5 Ab5@1/1 G5@2/0.5 F5@2.5/0.5 D5@3/1"],
  [[7, 31], "Bb4@0/0.5 D5@0.5/0.5 G5@1/1 E5@2/0.5 C#5@2.5/0.5 Bb4@3/0.5 G4@3.5/0.5"],
  [[8, 32], "A4@0/0.5 D5@0.5/2"],
  [[15], "G5@0/0.5 F5@0.5/0.5 E5@1/0.5 D5@1.5/0.5 C#5@2/0.5 E5@2.5/0.5 A5@3/1"],
  [[16], "F5@0/0.5 D5@0.5/1.5 C5@3/0.5 E5@3.5/0.5"],
  [[17], "F5@0/2 D5@2/1 C5@3/1"],
  [[18], "F#5@0/1.5 A5@1.5/0.5 F#5@2/0.5 D5@2.5/1.5"],
  [[19], "G5@0/2 F5@2/1 D5@3/1"],
  [[20], "E5@0/1.5 G5@1.5/0.5 Bb5@2/1 A5@3/0.5 G5@3.5/0.5"],
  [[21], "A5@0/2 F5@2/1 C5@3/1"],
  [[22], "D5@0/1.5 F5@1.5/0.5 A5@2/2"],
  [[23], "G5@0/1 Bb5@1/0.5 G5@1.5/0.5 E5@2/1 D5@3/1"],
  [[24], "C#5@0/1 E5@1/0.5 G5@1.5/0.5 Bb5@2/1"],
];
{
  const band = ownRandom(0x4c6f6e67);
  const { touch } = CLUB_BAND;
  const beat = 60 / CLUB_BAND.tempo;
  const bars = 64;
  const length = Math.round(bars * 4 * beat * RATE);
  const music = new Float64Array(length);
  // Where a beat of the song falls once swung, in beats from its start: the "and" of each beat comes late.
  const swung = (at) => {
    const whole = Math.floor(at);
    const part = at - whole;
    return whole + (part < 0.5 ? (part * CLUB_BAND.swing) / 0.5 : CLUB_BAND.swing + ((part - 0.5) * (1 - CLUB_BAND.swing)) / 0.5);
  };
  // How late a player plays, up to so many beats: never early, so nothing on the first beat sounds before it.
  const late = (beats) => band() * beats;
  // How hard a player strikes a note, as a share of the part's touch: a little different every time.
  const struck = (part) => part * (0.88 + 0.24 * band());
  // A bar of the song, from 0, as its section's bar: its chords and bell, and whether it is in the second chorus.
  const section = (bar) => {
    const inChorus = bar % 32;
    return { ...(inChorus >= 16 && inChorus < 24 ? CLUB_BRIDGE : CLUB_A), barOf: inChorus % 8, inChorus, second: bar >= 32 };
  };
  const chordAt = (bar, at) => {
    const { chords, barOf } = section(bar % bars);
    return CLUB_CHORDS[chords[barOf].findLast(([, from]) => from <= at)[0]];
  };

  // A note of the horn from a beat for so many beats, at a MIDI note and level: a pulse through a low-pass opening as
  // it is blown, scooped up into tune, its vibrato coming in late, over a breath of air.
  const hornNote = (at, beats, note, level) => {
    const frequency = noteHertz(note);
    const sounding = beats * beat * (beats < 1 ? 0.9 : 0.96) + 0.06;
    let phase = 0;
    let filtered = 0;
    let lastNoise = 0;
    addRound(music, (at + late(0.03)) * beat, sounding, (t) => {
      const scoop = -CLUB_BAND.scoopCents * Math.max(0, 1 - t / CLUB_BAND.scoopSeconds);
      const vibratoIn = Math.max(0, Math.min(1, (t - CLUB_BAND.vibratoFrom) / (CLUB_BAND.vibratoFull - CLUB_BAND.vibratoFrom)));
      const cents = scoop + vibratoIn * CLUB_BAND.vibratoCents * Math.sin(2 * Math.PI * CLUB_BAND.vibratoRate * t);
      const step = (frequency * 2 ** (cents / 1200)) / RATE;
      phase = (phase + step) % 1;
      const opening = Math.min(1, t / 0.025) * (0.45 + 0.55 * Math.exp(-t / 0.25));
      const cutoff = Math.min(9000, frequency * (1.5 + 5 * opening));
      filtered += (1 - Math.exp((-2 * Math.PI * cutoff) / RATE)) * (pulseAt(phase, step, CLUB_BAND.duty) - filtered);
      const noise = band() * 2 - 1;
      const breath = (noise - lastNoise) * CLUB_BAND.breath * (1 + 2 * Math.exp(-t / 0.04));
      lastNoise = noise;
      const envelope = Math.min(1, t / 0.012) * (0.85 + 0.15 * Math.exp(-t / 0.2)) * Math.min(1, (sounding - t) / 0.06);
      return level * envelope * (filtered + breath);
    });
  };
  // A key of the kalimba from a beat, at a MIDI note and level: a sine struck a little sharp and dropping into tune, the
  // tine's inharmonic partial dying fast over it, and the click of the thumb.
  const kalimbaKey = (at, note, level) => {
    const frequency = noteHertz(note);
    let phase = 0;
    addRound(music, (at + late(0.015)) * beat, 0.9, (t) => {
      const cents = CLUB_BAND.kalimbaDropCents * Math.exp(-t / 0.02);
      phase += (2 * Math.PI * frequency * 2 ** (cents / 1200)) / RATE;
      const partial = Math.exp(-t / CLUB_BAND.kalimbaPartialDies) * 0.35 * Math.sin(CLUB_BAND.kalimbaPartial * phase);
      const thumb = t < 0.003 ? 0.4 * (band() * 2 - 1) * (1 - t / 0.003) : 0;
      const envelope = Math.min(1, t / 0.001) * Math.exp(-t / 0.35) * Math.min(1, (0.9 - t) / 0.05);
      return level * (envelope * (Math.sin(phase) + partial) + thumb);
    });
  };
  // A chord of the kalimba from a beat, its keys a few milliseconds apart under the thumbs.
  const kalimbaChord = (at, chord) => {
    const level = struck(touch.comp);
    chord.voicing.forEach((note, k) => kalimbaKey(at + k * 0.012, note, level));
  };
  // A string of the wah bass from a beat, a beat long, at a MIDI note: a sawtooth an octave over it through a wah
  // swept up and back over the note, and a sine at the note itself, an octave under the saw.
  const bassNote = (at, note) => {
    const frequency = noteHertz(note);
    const sounding = beat * 0.92;
    const level = struck(touch.bass);
    const saw = new Float64Array(Math.round(sounding * RATE));
    let phase = 0;
    for (let i = 0; i < saw.length; i++) {
      phase = (phase + (2 * frequency) / RATE) % 1;
      saw[i] = sawAt(phase, (2 * frequency) / RATE);
    }
    const [from, top, to] = CLUB_BAND.wah;
    const wah = sweptResonance(saw, (t) => (t < 0.3 * sounding ? from + (top - from) * (t / (0.3 * sounding)) : top + (to - top) * ((t - 0.3 * sounding) / (0.7 * sounding))), 4);
    const part = wah.map((sample, i) => {
      const t = i / RATE;
      const envelope = Math.min(1, t / 0.004) * Math.exp(-t / 0.5) * Math.min(1, (sounding - t) / 0.02);
      return level * envelope * (1.6 * sample + 0.25 * saw[i] + 0.8 * Math.sin(2 * Math.PI * frequency * t));
    });
    mixRound(music, (at + late(0.012)) * beat, part);
  };
  // A strike of the glass bell from a beat, at a MIDI note: a frequency-modulated tone, bright as it is struck and
  // mellowing as it rings.
  const bellNote = (at, note) => {
    const frequency = noteHertz(note);
    const level = struck(touch.bell);
    addRound(music, at * beat, CLUB_BAND.bellRings * 1.5, (t) => {
      const index = CLUB_BAND.bellIndex * Math.max(0, 1 - t / CLUB_BAND.bellBrightFor);
      const envelope = Math.min(1, t / 0.002) * Math.exp((-3 * t) / CLUB_BAND.bellRings);
      return level * envelope * Math.sin(2 * Math.PI * frequency * t + index * Math.sin(2 * Math.PI * CLUB_BAND.bellRatio * frequency * t));
    });
  };
  // A strike of the ride cymbal from a beat: noise ringing at its two bright modes, its bell's two notes over them, a
  // ping dying fast and a wash dying slowly.
  const rideHit = (at, level) => {
    const noise = whiteNoise(0.8, band);
    const low = resonance(noise, 5200, 8);
    const high = resonance(noise, 7900, 8);
    const part = noise.map((_, i) => {
      const t = i / RATE;
      const ping = Math.exp(-t / 0.04);
      const wash = Math.exp(-t / 0.35) * Math.min(1, (0.8 - t) / 0.05);
      const bellTone = 0.25 * Math.sin(2 * Math.PI * 3100 * t) + 0.18 * Math.sin(2 * Math.PI * 4700 * t);
      return level * (wash * (2.2 * low[i] + 1.6 * high[i]) + ping * (bellTone + 0.3 * noise[i]));
    });
    mixRound(music, (at + late(0.01)) * beat, part);
  };
  // The brushes slapped on the snare from a beat: 0.12 seconds of bright noise on the drum's 190 hertz body.
  const slap = (at, level) => {
    const noise = highPassed(whiteNoise(0.15, band), 1500);
    const part = noise.map((sample, i) => {
      const t = i / RATE;
      return level * (Math.min(1, t / 0.003) * Math.max(0, 1 - t / 0.12) ** 1.5 * sample + 0.6 * Math.exp(-t / 0.05) * Math.sin(2 * Math.PI * 190 * t));
    });
    mixRound(music, (at + late(0.01)) * beat, part);
  };
  // The kick, feathered under every beat: a soft thunk at 50 hertz.
  const kick = (at) => {
    const part = new Float64Array(Math.round(0.5 * RATE));
    thunk(part, 0, 50, struck(touch.kick), band);
    mixRound(music, at * beat, part);
  };

  // The tune's notes in each bar of a chorus, from 1, as written.
  const tuneBars = {};
  for (const [lines, notes] of CLUB_TUNE) {
    const parsed = notes.split(" ").map((written) => {
      const [, name, at, beats] = written.match(/^(.+)@([\d.]+)\/([\d.]+)$/);
      return { note: noteNumber(name), at: Number(at), beats: Number(beats) };
    });
    lines.forEach((line) => (tuneBars[line] = parsed));
  }
  // The tune of a bar, from 0: each note's swung start and length in beats from the song's start, and its MIDI note.
  const tuneOf = (bar) => tuneBars[(bar % 32) + 1].map(({ note, at, beats }) => ({
    note, at: bar * 4 + swung(at), beats: swung(at + beats) - swung(at),
  }));
  // Who plays the tune and the bell's notes in a bar: the horn leads the first chorus; in the second the kalimba takes
  // the A sections an octave up over the horn holding the bell's notes, the horn the bridge in full fourths, and both
  // the last A in octaves.
  const playTune = (bar) => {
    const { inChorus, second, bell, barOf } = section(bar);
    const bridge = inChorus >= 16 && inChorus < 24;
    const kalimbaLeads = second && !bridge;
    const hornLeads = !second || bridge || inChorus >= 24;
    for (const { note, at, beats } of tuneOf(bar)) {
      const level = struck(touch.horn);
      if (hornLeads) {
        hornNote(at, beats, note, level);
        hornNote(at, beats, note - 5, level * (second && bridge ? CLUB_BAND.fullFourth : CLUB_BAND.fourth));
      }
      if (kalimbaLeads) {
        kalimbaKey(at, note + 12, struck(touch.kalimba));
      }
    }
    if (kalimbaLeads && !hornLeads) {
      const held = bell[barOf];
      const until = (k) => (k + 1 < held.length ? held[k + 1][1] : 4);
      held.forEach(([name, from], k) => hornNote(bar * 4 + from, until(k) - from, noteNumber(name) - 12, CLUB_BAND.held * touch.horn));
    }
  };
  // The bass's walk through a bar, from the root it starts on, as four MIDI notes and the root the next bar starts on:
  // the root on one, chord tones on two and three, climbing or falling whichever way ends nearer the next root, and a
  // semitone from the next root on four. Roots are taken in whichever octave lies nearest, within the bass's range.
  const [lowestRoot, highestRoot, highestNote] = [31, 47, 50];
  const nearestRoot = (pitchClass, near) => [pitchClass % 12 + 24, pitchClass % 12 + 36, pitchClass % 12 + 48]
    .filter((root) => root >= lowestRoot && root <= highestRoot)
    .reduce((best, root) => (Math.abs(root - near) < Math.abs(best - near) ? root : best));
  const approach = (from, nextRoot) => {
    const side = from > nextRoot ? 1 : -1;
    return nextRoot + side === from ? nextRoot - side : nextRoot + side;
  };
  const walkOf = (bar, root) => {
    const { chords, barOf } = section(bar % bars);
    const [first, second] = chords[barOf].map(([name]) => CLUB_CHORDS[name]);
    const last = bar === bars - 1;
    const nextFrom = (near) => (last ? CLUB_CHORDS.Dm6.root : nearestRoot(chordAt(bar + 1, 0).root, near));
    if (second) {
      const tone = root + first.tones[1] <= highestNote ? root + first.tones[1] : root + first.tones[1] - 12;
      const changed = nearestRoot(second.root, tone);
      const next = nextFrom(changed);
      return { notes: [root, tone, changed, approach(changed, next)], next };
    }
    const climbing = [root, root + first.tones[1], root + first.tones[2]];
    const falling = [root, root + first.tones[3] - 12, root + first.tones[2] - 12];
    const ends = (line) => (line[2] > highestNote || line[2] < lowestRoot - 3 ? Infinity : Math.abs(nextFrom(line[2]) - line[2]));
    const line = ends(falling) < ends(climbing) || (ends(falling) === ends(climbing) && bar % 2 === 1) ? falling : climbing;
    const next = nextFrom(line[2]);
    return { notes: [...line, approach(line[2], next)], next };
  };
  // The band behind the tune in a bar: the kalimba comping, the bell on the downbeat and the drums.
  const playBand = (bar) => {
    const at = bar * 4;
    const { barOf, bell } = section(bar);
    [1, 3].forEach((q) => kalimbaChord(at + q, chordAt(bar, q)));
    if ((bar + 1) % 2 === 0) {
      kalimbaChord(at + swung(3.5), chordAt(bar + 1, 0));
    }
    bell[barOf].forEach(([name, from]) => bellNote(at + from, noteNumber(name)));
    [0, 1, 2, 3].forEach((q) => kick(at + q));
    [0, 1, 1.5, 2, 3, 3.5].forEach((q) => rideHit(at + swung(q), struck(touch.ride) * (q === 1 || q === 3 ? 1.3 : 1)));
    [1, 3].forEach((q) => slap(at + q, struck(touch.slap)));
  };
  let root = CLUB_CHORDS.Dm6.root;
  for (let bar = 0; bar < bars; bar++) {
    playTune(bar);
    playBand(bar);
    const walk = walkOf(bar, root);
    walk.notes.forEach((note, q) => bassNote(bar * 4 + q, note));
    root = walk.next;
  }
  // The triplet fill of the brushes over the last two beats, swelling back into the top.
  for (let k = 0; k < 6; k++) {
    slap((bars - 1) * 4 + 2 + k / 3, touch.slap * (0.5 + 0.12 * k));
  }
  // The brushes' swish, stirred round the drum once a beat: bright noise swelling and easing, crossfaded round the loop.
  const fade = 0.5;
  const swish = seamlessNoise(lowPassed(highPassed(whiteNoise(length / RATE + fade, band), 2000), 7000), length / RATE, fade);
  swish.forEach((sample, i) => {
    const along = ((i / RATE) / beat) % 1;
    music[i] += touch.swish * (0.35 + 0.65 * Math.sin(Math.PI * along) ** 2) * sample;
  });
  writeSound("club_band", music.map((sample) => Math.tanh(CLUB_BAND.drive * sample)), CLUB_BAND.level);
}

// The freighter's boarding ramp (see the plan's "The boarding ramp"), each sound with a random sequence of its own,
// some four seconds together as she lowers it, the airstairs' latch clunking first: a burst of steam as the latches
// let go, hissing away; the plate swinging down on its rams, the hydraulic pump's whine rising as they take its
// weight, its motor humming under it, the plate's mass rumbling and the hinge creaking twice; and the foot lip
// unfolding onto the ground with a heavy thud and its latch clicking home, under her belly's echo. Raising it plays
// them the other way round, the steam last as it seals. Tune them here.
const RAMP = {
  // The steam: how long it lasts and how long it takes to die away, in seconds, and its high-pass, in hertz.
  steamSeconds: 0.7,
  steamDies: 0.6,
  steamCutoff: 2000,
  // The swing, as long as the plate takes (ticks 10 to 80), and the echo after it, in seconds; the pump's whine from
  // and to, in hertz, and the motor's hum, as a share of its pitch.
  swingSeconds: 3.5,
  swingTail: 0.4,
  pumpFrom: 140,
  pumpTo: 210,
  motorShare: 1 / 3,
  // The foot's thud, in hertz; its latch's click, in hertz, and when it clicks, in seconds after the thud.
  thudHz: 45,
  latchHz: 900,
  latchAt: 0.12,
  // How much of the echo under her belly is mixed in.
  echo: 0.15,
};
{
  const steam = ownRandom(0x52616d53);
  const white = whiteNoise(RAMP.steamSeconds, steam);
  const hiss = highPassed(highPassed(white, RAMP.steamCutoff), RAMP.steamCutoff);
  const body = resonance(white, 900, 0.8);
  let flutter = 0;
  const samples = hiss.map((sample, i) => {
    const t = i / RATE;
    flutter += 0.002 * (steam() * 2 - 1 - flutter);
    const burst = Math.min(1, t / 0.004) * Math.exp(-t / 0.05);
    const dying = Math.min(1, t / 0.004) * Math.max(0, 1 - t / RAMP.steamDies) ** 2;
    return (0.6 * burst + dying) * (1 + 8 * flutter) * (sample + 0.35 * Math.exp(-t / 0.15) * body[i]);
  });
  writeSound("ramp_steam", samples, 0.7);
}
{
  const ram = ownRandom(0x52616d48);
  const swing = RAMP.swingSeconds;
  const dry = new Float64Array(Math.round((swing + RAMP.swingTail) * RATE));
  gearHydraulics(dry, 0, swing, RAMP.pumpFrom, RAMP.pumpTo, 1, ram);
  // The pump's motor humming under the whine at a third of its pitch, and the plate's mass rumbling as it swings,
  // most of all halfway down.
  let phase = 0;
  let motor = 0;
  let brown = 0;
  let rumble = 0;
  for (let i = 0; i < Math.round(swing * RATE); i++) {
    const t = i / RATE;
    const running = gearRamp(t, 0, 0.35) * (1 - gearRamp(t, swing - 0.5, swing));
    const step = (RAMP.motorShare * (RAMP.pumpFrom + (RAMP.pumpTo - RAMP.pumpFrom) * Math.min(1, t / (0.6 * swing)))) / RATE;
    phase = (phase + step) % 1;
    motor += 0.08 * (sawAt(phase, step) - motor);
    brown = (brown + 0.02 * (ram() * 2 - 1)) * 0.996;
    rumble += 0.02 * (brown - rumble);
    dry[i] += running * 0.25 * motor + 3 * Math.sin((Math.PI * t) / swing) * rumble;
  }
  girderGroan(dry, 0.9, ram, 25);
  girderGroan(dry, 2.4, ram, 20);
  writeSound("ramp_hydraulics", hullEcho(dry, RAMP.echo), 0.8);
}
{
  const foot = ownRandom(0x52616d54);
  const dry = new Float64Array(Math.round(1.2 * RATE));
  thunk(dry, 0, RAMP.thudHz, 1, foot);
  thunk(dry, 0.07, RAMP.thudHz * 1.4, 0.35, foot);
  gearRing(dry, 0.002, 310, 0.25);
  gearRing(dry, 0.002, 770, 0.12);
  click(dry, RAMP.latchAt, RAMP.latchHz, 0.5, foot);
  click(dry, RAMP.latchAt + 0.015, RAMP.latchHz * 1.6, 0.25, foot);
  writeSound("ramp_thud", hullEcho(dry, RAMP.echo), 0.9);
}

// The freighter's engines (see the plan's "Her engines, impressive and loud"), her show piece outside, each sound with
// a random sequence of its own. Under them all runs her drone, a deep 41 hertz thrum, its second and third harmonics
// throbbing three times a second, driven into a growl; and over it her turbines' whine at 1.1 kilohertz. Her idle, a
// seamless four-second loop heard inside her hull: the drone, a band of air round 400 hertz moving through her ducts,
// and the whine faint over them. Her roar, the seamless six-second loop heard up to 160 blocks off, crossfaded over her
// idle by her thrust and never pitched: the drone heavier, with a sub an octave under it; a deep rumble of exhaust,
// brown noise low-passed near 320 hertz and slowly surging, crackling with her plasma; the roar's body round 420 hertz;
// the exhaust's hiss, a band from 1.2 to 4 kilohertz; and two whines three hertz apart, beating. Every tone turns a
// whole number of times round its loop and the noise is crossfaded round it, so neither loop has a seam. Her lift jets,
// a seamless four-second loop as she lifts off and lands: the rocket's roar cut off at 900 hertz, some ten crackles a
// second, fluttering nine times a second; and their lighting, a crack, a deep thump sweeping up from 25 to 50 hertz and
// their roar surging in as its low-pass opens, her drone rising into tune under it, written to the loop's scale so it
// hands over to it. Her mains spooling up: the whine climbing from 250 hertz to the roar's, its beat quickening, the
// drone and the air's rush rising under it. And her pass at speed: her roar rushing in and going by, its whine falling
// through the pass, the ground's reflection sweeping a comb through it, a gust of air as she goes over, then a long
// rolling thunder dying away, echoing back off the land. Tune them here.
const FREIGHTER_ENGINES = {
  // The idle's, the roar's and the lift jets' loops, and how much longer their noise is rendered to crossfade, in
  // seconds.
  idleLoop: 4,
  roarLoop: 6,
  jetLoop: 4,
  crossfade: 0.5,
  // The drone and its harmonics' throb, the turbines' whine and the beat between the roar's two whines, in hertz; and
  // how hard the drone is driven into its growl.
  drone: 41,
  throb: 3,
  whine: 1100,
  whineBeat: 3,
  growl: 1.8,
  // The idle's band of air, in hertz, and its parts' shares, by root mean square.
  idleBand: 400,
  idleShares: { drone: 1, band: 0.6, whine: 0.06 },
  // The roar: its rumble's low-pass, its body's band and its exhaust's band, in hertz; its crackles a second; and its
  // parts' shares, by root mean square beside the rumble's.
  rumbleCutoff: 320,
  body: 420,
  exhaust: [1200, 4000],
  crackles: 25,
  roarShares: { body: 0.6, exhaust: 0.32, crackle: 0.22, drone: 0.75, sub: 0.4, whine: 0.1 },
  // Past this, as a multiple of a sound's root mean square, its peaks are rounded off, so it stays loud once levelled:
  // a little harder than the rocket's, for her to sound as loud as she is meant to from outside.
  soft: 2.5,
  // The lift jets: their low-pass, in hertz; their crackles a second; their hiss, in decibels under the rumble, and
  // their crackle's share; and their flutter's rate, in hertz, and depth, as a share.
  jetCutoff: 900,
  jetCrackles: 10,
  jetHissUnder: 14,
  jetCrackleShare: 0.5,
  jetFlutter: 9,
  jetFlutterDepth: 0.3,
  // Her lift jets lighting: how long, and when their roar is full, in seconds.
  ignitionSeconds: 2.6,
  ignitionFull: 1.3,
  // Her mains spooling up: how long, in seconds, and the whine's pitch it starts from, in hertz.
  spoolSeconds: 3,
  spoolFrom: 250,
  // Her pass: how long, and when she is nearest, in seconds; how far her whine is raised coming and lowered going, as
  // a share; the longest and shortest delay of the ground's reflection, in seconds; the land's echoes, each so many
  // seconds late, so loud and so dull, in hertz; and how loud the rolling thunder after her is, beside her roar.
  flybySeconds: 3.8,
  flybyPass: 1.3,
  doppler: 0.07,
  reflection: [0.0006, 0.007],
  echoes: [[0.42, 0.35, 700], [0.95, 0.2, 400]],
  thunder: 10,
  // The peak each is written at, and the highest the lighting may reach written to the lift jets' scale, short of
  // what the encoder's ripple would clip.
  level: 0.85,
  loudest: 0.9,
};

/** Her drone at a phase of its fundamental and of its throb, in turns: driven into a growl, from -1 to 1. */
function freighterDrone(phase, throbPhase) {
  const harmonic = (n) => Math.sin(2 * Math.PI * n * phase);
  const throb = Math.sin(2 * Math.PI * throbPhase);
  const mixed = harmonic(1) + 0.55 * (0.6 + 0.4 * throb) * harmonic(2) + 0.4 * (0.6 - 0.4 * throb) * harmonic(3)
    + 0.15 * harmonic(4) + 0.08 * harmonic(5);
  return Math.tanh(FREIGHTER_ENGINES.growl * mixed) / Math.tanh(FREIGHTER_ENGINES.growl);
}

/** Her two turbines' whines at their phases, in turns, beating against each other, their octaves faint over them. */
function freighterWhines(phase, otherPhase) {
  const turn = 2 * Math.PI;
  return Math.sin(turn * phase) + Math.sin(turn * otherPhase) + 0.25 * (Math.sin(2 * turn * phase) + Math.sin(2 * turn * otherPhase));
}

/** Samples scaled to a root mean square of one. */
function levelled(samples) {
  const own = rms(samples);
  return samples.map((sample) => sample / own);
}

/** Rounds off peaks past FREIGHTER_ENGINES.soft, samples levelled to a root mean square of one. */
function roundedOff(samples) {
  const { soft } = FREIGHTER_ENGINES;
  return samples.map((sample) => soft * Math.tanh(sample / soft));
}
{
  const E = FREIGHTER_ENGINES;
  const idleRandom = ownRandom(0x46496430);
  const length = Math.round(E.idleLoop * RATE);
  const band = levelled(seamlessNoise(resonance(whiteNoise(E.idleLoop + E.crossfade, idleRandom), E.idleBand, 1.5), E.idleLoop, E.crossfade));
  const drone = levelled(Float64Array.from({ length }, (_, i) => freighterDrone((E.drone * i) / RATE, (E.throb * i) / RATE)));
  const idle = drone.map((sample, i) => {
    const t = i / RATE;
    const throb = 1 + 0.12 * Math.sin(2 * Math.PI * E.throb * t);
    return E.idleShares.drone * sample + E.idleShares.band * throb * band[i] + E.idleShares.whine * Math.SQRT2 * Math.sin(2 * Math.PI * E.whine * t);
  });
  writeSound("freighter_engine_idle", idle, 0.7);
}
{
  const E = FREIGHTER_ENGINES;
  const roarRandom = ownRandom(0x46526f61);
  const rendered = E.roarLoop + E.crossfade;
  const roar = rocketRoar(rendered, roarRandom, () => E.rumbleCutoff, () => E.crackles);
  const white = whiteNoise(rendered, roarRandom);
  const body = levelled(resonance(white, E.body, 1.2));
  const exhaust = levelled(lowPassed(highPassed(white, E.exhaust[0]), E.exhaust[1]));
  const [rumble, crackle] = [levelled(roar.rumble), levelled(roar.crackle)];
  const shares = E.roarShares;
  const noise = seamlessNoise(rumble.map((sample, i) => sample + shares.body * body[i] + shares.exhaust * exhaust[i] + shares.crackle * crackle[i]),
    E.roarLoop, E.crossfade);
  const length = noise.length;
  const drone = levelled(Float64Array.from({ length }, (_, i) => freighterDrone((E.drone * i) / RATE, (E.throb * i) / RATE)));
  const sub = levelled(Float64Array.from({ length }, (_, i) => Math.sin((Math.PI * E.drone * i) / RATE)));
  const whines = levelled(Float64Array.from({ length }, (_, i) => freighterWhines((E.whine * i) / RATE, ((E.whine + E.whineBeat) * i) / RATE)));
  const mixed = noise.map((sample, i) => sample + shares.drone * drone[i] + shares.sub * sub[i] + shares.whine * whines[i]);
  writeSound("freighter_engine_roar", roundedOff(levelled(mixed)), E.level);
}
{
  const E = FREIGHTER_ENGINES;
  const jetRandom = ownRandom(0x464a6574);
  const jets = rocketRoar(E.jetLoop + E.crossfade, jetRandom, () => E.jetCutoff, () => E.jetCrackles);
  // Levelled once from the steady jets, and the same levels for their lighting: the rumble's root mean square one.
  const gain = {
    rumble: 1 / rms(jets.rumble),
    hiss: 10 ** (-E.jetHissUnder / 20) / rms(jets.hiss),
    crackle: E.jetCrackleShare / rms(jets.crackle),
  };
  const mix = (parts, rumbleAt, hissAt, crackleAt) => parts.rumble.map((sample, i) => {
    const t = i / RATE;
    return rumbleAt(t) * gain.rumble * sample + hissAt(t) * gain.hiss * parts.hiss[i] + crackleAt(t) * gain.crackle * parts.crackle[i];
  });
  const steady = seamlessNoise(mix(jets, () => 1, () => 1, () => 1), E.jetLoop, E.crossfade);
  const loop = roundedOff(steady.map((sample, i) => sample * (1 + E.jetFlutterDepth * Math.sin((2 * Math.PI * E.jetFlutter * i) / RATE))));
  writeSound("freighter_lift_jets", loop, E.level);

  // Their lighting: the crack of them catching, the thump, and their roar swelling in under it as its low-pass opens.
  const lightRandom = ownRandom(0x46496769);
  const seconds = E.ignitionSeconds;
  const swell = (t) => gearRamp(t, 0.02, E.ignitionFull) * Math.min(1, (seconds - t) / 0.3);
  const lighting = rocketRoar(seconds, lightRandom, (t) => 150 + (E.jetCutoff - 150) * gearRamp(t, 0.05, E.ignitionFull), (t) => E.jetCrackles * gearRamp(t, 0.2, 1));
  const lit = mix(lighting, swell, (t) => swell(t) * gearRamp(t, 0.3, E.ignitionFull), swell);
  let thump = 0;
  let fireball = 0;
  let drone = 0;
  let rushLow = 0;
  let rushBand = 0;
  for (let i = 0; i < lit.length; i++) {
    const t = i / RATE;
    const white = lightRandom() * 2 - 1;
    const crack = t < 0.012 ? 4 * white * Math.exp(-t / 0.003) : 0;
    thump += (25 + 25 * gearRamp(t, 0, 0.35)) / RATE;
    const whump = Math.min(1, t / 0.01) * Math.exp(-t / 0.45) * Math.tanh(1.5 * (Math.sin(2 * Math.PI * thump) + 0.4 * Math.sin(4 * Math.PI * thump)));
    fireball += 0.03 * (white - fireball);
    drone += (E.drone * (0.5 + 0.5 * gearRamp(t, 0, E.ignitionFull))) / RATE;
    // The surge: a band of the noise sweeping up from 200 to 1,500 hertz as the jets take hold, a state variable filter.
    const tune = 2 * Math.sin((Math.PI * (200 + 1300 * gearRamp(t, 0.05, E.ignitionFull))) / RATE);
    rushLow += tune * rushBand;
    rushBand += tune * (white - rushLow - 0.7 * rushBand);
    lit[i] += crack + 3 * whump + 10 * fireball * Math.exp(-t / 0.25) + swell(t) * (0.6 * freighterDrone(drone, E.throb * t) + 0.5 * rushBand);
  }
  const roundedLit = roundedOff(lit);
  writeSound("freighter_ignition", roundedLit, Math.min(E.loudest, (peakOf(roundedLit) * E.level) / peakOf(loop)));
}
{
  const E = FREIGHTER_ENGINES;
  const spoolRandom = ownRandom(0x46537030);
  const seconds = E.spoolSeconds;
  const spool = new Float64Array(Math.round(seconds * RATE));
  let [phase, otherPhase, drone, airLow, airBand] = [0, 0, 0, 0, 0];
  for (let i = 0; i < spool.length; i++) {
    const t = i / RATE;
    // How far it has spooled, from 0 to 1: quick at first and settling, as a turbine winds up.
    const spun = (1 - Math.exp(-t / 0.8)) / (1 - Math.exp(-seconds / 0.8));
    const pitch = E.spoolFrom * (E.whine / E.spoolFrom) ** spun;
    phase += pitch / RATE;
    otherPhase += (pitch * (1 + E.whineBeat / E.whine)) / RATE;
    drone += (E.drone * (0.4 + 0.6 * spun)) / RATE;
    const tune = 2 * Math.sin((Math.PI * (300 + 1200 * spun)) / RATE);
    airLow += tune * airBand;
    airBand += tune * (spoolRandom() * 2 - 1 - airLow - 0.8 * airBand);
    const envelope = gearRamp(t, 0, 0.4) * Math.min(1, (seconds - t) / 0.25) * (0.3 + 0.7 * spun);
    spool[i] = envelope * (0.35 * freighterWhines(phase, otherPhase) + 0.7 * spun * freighterDrone(drone, E.throb * t) + 0.6 * airBand);
  }
  writeSound("freighter_spool", spool, 0.8);
}
{
  const E = FREIGHTER_ENGINES;
  const passRandom = ownRandom(0x46506173);
  const seconds = E.flybySeconds;
  const pass = E.flybyPass;
  // How near she is, from nothing far off to one overhead, t seconds in: closing fast and going away slower.
  const nearness = (t) => 1 / (1 + ((t - pass) / (t < pass ? 0.35 : 0.6)) ** 2);
  // How much her pitch is raised coming and lowered going, as she passes.
  const shift = (t) => 1 - E.doppler * Math.tanh((t - pass) / 0.25);
  const roar = rocketRoar(seconds, passRandom, (t) => 180 + 1000 * nearness(t), (t) => 30 * nearness(t));
  const [rumble, hiss, crackle] = [levelled(roar.rumble), levelled(roar.hiss), levelled(roar.crackle)];
  const exhaust = levelled(lowPassed(highPassed(whiteNoise(seconds, passRandom), E.exhaust[0]), E.exhaust[1]));
  const dry = new Float64Array(rumble.length);
  let [drone, phase, otherPhase, gustLow, gustBand, brown, thunder, swellTo, swelling] = [0, 0, 0, 0, 0, 0, 0, 1, 1];
  for (let i = 0; i < dry.length; i++) {
    const t = i / RATE;
    const near = nearness(t);
    drone += (E.drone * shift(t)) / RATE;
    phase += (E.whine * shift(t)) / RATE;
    otherPhase += ((E.whine + E.whineBeat) * shift(t)) / RATE;
    // The gust as she goes over: a band of noise falling from 2,500 to 400 hertz through the pass.
    const white = passRandom() * 2 - 1;
    const tune = 2 * Math.sin((Math.PI * (400 + 2100 * (1 - gearRamp(t, pass - 0.3, pass + 0.5)))) / RATE);
    gustLow += tune * gustBand;
    gustBand += tune * (white - gustLow - 0.6 * gustBand);
    // The rolling thunder after she has gone: low brown noise swelling and easing at random, dying away.
    brown = (brown + 0.02 * white) * 0.997;
    thunder += 0.012 * (brown - thunder);
    if (i % Math.round(0.25 * RATE) === 0) {
      swellTo = 0.5 + passRandom();
    }
    swelling += 0.0002 * (swellTo - swelling);
    const rolling = gearRamp(t, pass - 0.2, pass + 0.5) * Math.exp(-Math.max(0, t - pass) / 1.3) * swelling;
    dry[i] = near ** 0.8 * (rumble[i] + 0.55 * freighterDrone(drone, E.throb * t) + 0.25 * crackle[i])
      + near ** 1.6 * (0.35 * hiss[i] + 0.35 * exhaust[i] + 0.12 * freighterWhines(phase, otherPhase))
      + 1.2 * near * gustBand + E.thunder * rolling * thunder;
  }
  // The ground's reflection, a little later than her sound and sweeping as she nears and goes: a comb through it.
  const [shortest, longest] = E.reflection;
  const reflected = dry.map((sample, i) => {
    const delay = (shortest + (longest - shortest) * (1 - nearness(i / RATE))) * RATE;
    const back = i - delay;
    const whole = Math.floor(back);
    return back < 0 ? sample : sample + 0.6 * (dry[whole] + (back - whole) * (dry[whole + 1] - dry[whole]));
  });
  // The land's echoes, later, quieter and duller.
  const flyby = reflected.slice();
  for (const [late, loud, dull] of E.echoes) {
    const echo = lowPassed(reflected, dull);
    const gap = Math.round(late * RATE);
    for (let i = gap; i < flyby.length; i++) {
      flyby[i] += loud * echo[i - gap];
    }
  }
  const faded = flyby.map((sample, i) => sample * Math.min(1, (flyby.length - i) / (0.4 * RATE)));
  writeSound("freighter_flyby", roundedOff(levelled(faded)), E.level);
}

// The freighter's ball turrets (see the plan's "Turrets"), each sound with a random sequence of its own. A shot: both
// barrels firing, 60 milliseconds apart, each a heavy thunk at 60 hertz, an electric crack of noise ringing at 3.2
// kilohertz and crushed to a few bits, and the capacitor's buzz recharging at 110 hertz, its pitch rising: a
// discharge's crack, never a falling "pew". Its servo, a seamless second-long loop played while the gunner swings it:
// the robot bartender's servo an octave and a half down, its gear's whine over it, wobbling a little, over the rumble
// of the turret's ring on its rollers ticking round. Tune them here.
const BALL_TURRET = {
  // The shot: when each barrel fires, in seconds, and how much higher the second is; the thunk's pitch, the crack's
  // ring and the capacitor's buzz from and to, in hertz; how long it buzzes, in seconds; and the levels the crack is
  // crushed to.
  barrels: [0, 0.06],
  secondPitch: 1.06,
  thunkHz: 60,
  crackHz: 3200,
  buzzFrom: 110,
  buzzTo: 170,
  buzzSeconds: 0.15,
  crushedTo: 12,
  shotSeconds: 0.45,
  // The servo: its loop, in seconds; its motor's pitch, in hertz, the bartender's settling pitch an octave and a half
  // down, a whole number of turns round the loop; the harmonic its gear whines at; its wobble and the ring's rollers
  // ticking, in hertz, each a whole number of turns round it.
  servoLoop: 1,
  servoHz: 134,
  gearHarmonic: 9,
  wobble: 6,
  rollers: 23,
};
{
  const shotRandom = ownRandom(0x42546f74);
  const T = BALL_TURRET;
  const shot = new Float64Array(Math.round(T.shotSeconds * RATE));
  T.barrels.forEach((at, barrel) => {
    const pitch = barrel === 0 ? 1 : T.secondPitch;
    thunk(shot, at, T.thunkHz * pitch, 1, shotRandom);
    const crack = resonance(Float64Array.from({ length: Math.round(0.03 * RATE) }, (_, i) => (shotRandom() * 2 - 1) * Math.exp(-i / (0.006 * RATE))), T.crackHz * pitch, 2);
    let held = 0;
    crack.forEach((sample, i) => {
      held = i % 3 === 0 ? Math.round(sample * 6 * T.crushedTo) / T.crushedTo : held;
      shot[Math.round(at * RATE) + i] += held;
    });
    let phase = 0;
    for (let i = 0; i < Math.round(T.buzzSeconds * RATE); i++) {
      const t = i / RATE;
      const step = (pitch * (T.buzzFrom + (T.buzzTo - T.buzzFrom) * (t / T.buzzSeconds))) / RATE;
      phase = (phase + step) % 1;
      shot[Math.round((at + 0.004) * RATE) + i] += 0.3 * Math.exp(-t / 0.06) * Math.min(1, t / 0.003) * pulseAt(phase, step, 0.5);
    }
  });
  writeSound("ball_turret_shot", lowPassed(shot, 9000), 0.85);
}
{
  const servoRandom = ownRandom(0x42547376);
  const T = BALL_TURRET;
  const length = Math.round(T.servoLoop * RATE);
  const fade = 0.3;
  let brown = 0;
  let low = 0;
  const rumble = levelled(seamlessNoise(Float64Array.from({ length: Math.round((T.servoLoop + fade) * RATE) }, () => {
    brown = (brown + 0.02 * (servoRandom() * 2 - 1)) * 0.996;
    low += 0.02 * (brown - low);
    return low;
  }), T.servoLoop, fade));
  const servo = rumble.map((sample, i) => {
    const t = i / RATE;
    const phase = 2 * Math.PI * T.servoHz * t + 0.08 * Math.sin(2 * Math.PI * T.wobble * t);
    let motor = 0;
    for (let n = 1; n <= 5; n++) {
      motor += Math.sin(n * phase) / n;
    }
    const ticking = 0.6 + 0.4 * Math.max(0, Math.sin(2 * Math.PI * T.rollers * t)) ** 4;
    return 0.45 * motor + 0.2 * Math.sin(T.gearHarmonic * phase) + 0.35 * ticking * sample;
  });
  writeSound("ball_turret_servo", servo, 0.5);
}

// The laser crossbow (see the plan's "The laser crossbow"), each sound with a random sequence of its own. Its shot:
// the glowing coil it has for a string twanging as it is let go, a plucked string at 98 hertz; the bolt crackling off
// it, noise round 2.5 kilohertz chopped by a 220 hertz square, swelling for 80 milliseconds; and the soft thump of the
// stock in the shoulder. Its reload: twelve ratchet clicks quickening as it is cocked, the coil's charge whining up
// from 300 hertz to 2.4 kilohertz over 0.6 seconds, and the latch clacking home as it is ready. Tune them here.
const LASER_CROSSBOW = {
  // The shot: the string's pitch, in hertz, and how much of it is left each time round, some 20 decibels lost a
  // second; the bolt's ring and chop, in hertz, and how long it swells, in seconds.
  stringHz: 98,
  stringRings: 0.975,
  boltHz: 2500,
  chopHz: 220,
  boltSwells: 0.08,
  shotSeconds: 0.7,
  // The reload: its clicks, the first and the last gap between them, in seconds; the charge whine from and to, in
  // hertz, when it starts and how long it rises, in seconds; and when the latch clacks.
  clicks: 12,
  firstGap: 0.07,
  lastGap: 0.025,
  chargeFrom: 300,
  chargeTo: 2400,
  chargeAt: 0.45,
  charging: 0.6,
  latchAt: 1.12,
  reloadSeconds: 1.4,
};
{
  const shotRandom = ownRandom(0x4c435368);
  const C = LASER_CROSSBOW;
  const shot = new Float64Array(Math.round(C.shotSeconds * RATE));
  // The coil twanging: a Karplus-Strong string, a burst of noise round a delay line a period of its pitch long, each
  // turn averaged with its neighbour and losing a little.
  const period = Math.round(RATE / C.stringHz);
  const string = Float64Array.from({ length: shot.length }, (_, i) => (i < period ? shotRandom() * 2 - 1 : 0));
  for (let i = period + 1; i < string.length; i++) {
    string[i] = C.stringRings * 0.5 * (string[i - period] + string[i - period - 1]);
  }
  const bolt = resonance(whiteNoise(C.shotSeconds, shotRandom), C.boltHz, 1.5);
  shot.forEach((_, i) => {
    const t = i / RATE;
    const chop = (C.chopHz * t) % 1 < 0.5 ? 1 : 0.15;
    const swell = t < C.boltSwells ? t / C.boltSwells : Math.exp(-(t - C.boltSwells) / 0.03);
    shot[i] = 0.5 * string[i] * Math.min(1, t / 0.002, (C.shotSeconds - t) / 0.1) + 5 * swell * chop * bolt[i];
  });
  thunk(shot, 0, 80, 0.4, shotRandom);
  writeSound("laser_crossbow_shot", shot, 0.8);
}
{
  const reloadRandom = ownRandom(0x4c43526c);
  const C = LASER_CROSSBOW;
  const reload = new Float64Array(Math.round(C.reloadSeconds * RATE));
  let at = 0;
  for (let k = 0; k < C.clicks; k++) {
    click(reload, at, 2600 + 600 * reloadRandom(), 0.5, reloadRandom);
    click(reload, at + 0.006, 1300, 0.25, reloadRandom);
    at += C.firstGap * (C.lastGap / C.firstGap) ** (k / (C.clicks - 1));
  }
  let phase = 0;
  for (let i = Math.round(C.chargeAt * RATE); i < Math.round(C.latchAt * RATE); i++) {
    const t = i / RATE - C.chargeAt;
    const rising = Math.min(1, t / C.charging);
    phase += (C.chargeFrom * (C.chargeTo / C.chargeFrom) ** rising) / RATE;
    const tone = Math.sin(2 * Math.PI * phase) + 0.3 * Math.sin(4 * Math.PI * phase) + 0.12 * Math.sin(6 * Math.PI * phase);
    const envelope = Math.min(1, t / 0.05) * (0.4 + 0.6 * rising) * Math.min(1, (C.latchAt - C.chargeAt - t) / 0.03);
    reload[i] += 0.3 * envelope * tone * (1 + 0.15 * Math.sin(2 * Math.PI * 31 * t));
  }
  click(reload, C.latchAt, 2000, 0.8, reloadRandom);
  thunk(reload, C.latchAt + 0.004, 150, 0.35, reloadRandom);
  writeSound("laser_crossbow_reload", reload, 0.75);
}

/** The samples through a two-pole low-pass whose cutoff moves as it goes, cutoffAt(t) hertz t seconds in. */
function sweptLowPassed(input, cutoffAt) {
  const out = new Float64Array(input.length);
  let low = 0;
  let band = 0;
  for (let i = 0; i < input.length; i++) {
    const tune = 2 * Math.sin((Math.PI * Math.min(cutoffAt(i / RATE), RATE / 6)) / RATE);
    low += tune * band;
    band += tune * (input[i] - low - Math.SQRT2 * band);
    out[i] = low;
  }
  return out;
}

/** A stack of band-limited sawtooths at a share apart from a middle pitch, pitchAt(t) hertz t seconds in, averaged. */
function sawStack(seconds, pitchAt, tunings) {
  const samples = new Float64Array(Math.round(seconds * RATE));
  const phases = tunings.map(() => 0);
  for (let i = 0; i < samples.length; i++) {
    const pitch = pitchAt(i / RATE);
    for (let k = 0; k < tunings.length; k++) {
      const step = (pitch * tunings[k]) / RATE;
      phases[k] = (phases[k] + step) % 1;
      samples[i] += sawAt(phases[k], step) / tunings.length;
    }
  }
  return samples;
}

// The freighter's jump drive (see the plan's "Flight" and "Ideas of our own"), each sound with a random sequence of its
// own. Its spool-up, three seconds: three sawtooths a little apart sweeping up from 80 hertz to 2.4 kilohertz, beating
// faster as they rise, brightening, over a sub-bass swelling under them and a shimmer of air at the top. Its sputter as
// it fails, two seconds: its whine cut in and out six to twelve times a second, dropping in jerks each time it comes
// back, three backfires popping, sparks crackling off it and its motor's buzz sagging from 400 to 120 hertz under it:
// an electrical fault stuttering, never a smooth wind-down. Its thump as someone punches its panel: a fist on the
// plate, the panel ringing, a loose rattle, a clunk as something drops into place, and the whine catching and rising
// from 200 to 900 hertz. And the jump itself: a rush of air swelling, the whine shooting from 300 hertz to 6
// kilohertz in under half a second, 50 milliseconds of silence, then the techno room's falling boom from 90 to 30
// hertz with a blast and a rush of air going away behind it. Tune them here.
const JUMP_DRIVE = {
  // The spool: how long, in seconds; the saws' sweep from and to, in hertz, and how far apart they are tuned, as a
  // share; and the sub's pitch from and to, in hertz.
  spoolSeconds: 3,
  sweepFrom: 80,
  sweepTo: 2400,
  detune: 0.007,
  subFrom: 36,
  subTo: 48,
  // The sputter: how long, in seconds; the whine's pitch from, in hertz, and what is left of it at each jerk, as a
  // share; how many times a second it cuts in and out, least and most; when its backfires pop, in seconds; how many
  // sparks fly; and its motor's glide from and to, in hertz.
  sputterSeconds: 2,
  sputterFrom: 1600,
  jerk: 0.85,
  cutsLeast: 6,
  cutsMost: 12,
  backfires: [0.35, 0.95, 1.5],
  sparks: 24,
  glideFrom: 400,
  glideTo: 120,
  // The thump: how long, in seconds; the fist's thunk and the panel's ring, in hertz; when the rattle, the clunk and the
  // whine catching come, in seconds; and the whine's pitch from and to, in hertz.
  thumpSeconds: 1.8,
  fistHz: 70,
  panelHz: 330,
  rattleAt: 0.08,
  clunkAt: 0.36,
  catchAt: 0.45,
  catchFrom: 200,
  catchTo: 900,
  // The jump: how long; when the whine shoots up, how long it takes, and the silence after it, in seconds; and the
  // whine's pitch from and to and the boom's, in hertz.
  jumpSeconds: 2.5,
  shootAt: 0.3,
  shooting: 0.4,
  silence: 0.05,
  shootFrom: 300,
  shootTo: 6000,
  boomFrom: 90,
  boomTo: 30,
};
{
  const J = JUMP_DRIVE;
  const spoolRandom = ownRandom(0x4a445370);
  const pitchAt = (t) => J.sweepFrom * (J.sweepTo / J.sweepFrom) ** (t / J.spoolSeconds);
  const saws = sweptLowPassed(sawStack(J.spoolSeconds, pitchAt, [1, 1 + J.detune, 1 - J.detune]), (t) => 5 * pitchAt(t));
  const shimmer = highPassed(whiteNoise(J.spoolSeconds, spoolRandom), 3000);
  let sub = 0;
  const spool = saws.map((sample, i) => {
    const t = i / RATE;
    const along = t / J.spoolSeconds;
    sub += (J.subFrom + (J.subTo - J.subFrom) * along) / RATE;
    const envelope = Math.min(1, t / 0.2, (J.spoolSeconds - t) / 0.03);
    return envelope * ((0.5 + 0.5 * along) * sample + 0.9 * along ** 2 * Math.sin(2 * Math.PI * sub) + 0.08 * along ** 3 * shimmer[i]);
  });
  writeSound("jump_drive_spool", spool, 0.8);
}
{
  const J = JUMP_DRIVE;
  const sputterRandom = ownRandom(0x4a445375);
  const n = Math.round(J.sputterSeconds * RATE);
  // The whine's cuts: each stretch on or off a sixth to a twelfth of a second, its pitch dropping a jerk each time it
  // comes back.
  const gate = new Float64Array(n);
  const stepped = new Float64Array(n);
  let [at, on, pitch] = [0, true, J.sputterFrom];
  while (at < n) {
    const stretch = Math.round(RATE / (J.cutsLeast + (J.cutsMost - J.cutsLeast) * sputterRandom()));
    gate.fill(on ? 1 : 0, at, Math.min(n, at + stretch));
    stepped.fill(pitch, at, Math.min(n, at + stretch));
    pitch *= on ? 1 : J.jerk;
    on = !on;
    at += stretch;
  }
  const whine = lowPassed(sawStack(J.sputterSeconds, (t) => stepped[Math.min(n - 1, Math.round(t * RATE))], [1, 1.006]), 4000);
  let smoothed = 0;
  let glide = 0;
  let buzz = 0;
  const sputter = whine.map((sample, i) => {
    const t = i / RATE;
    smoothed += 0.05 * (gate[i] - smoothed);
    const step = (J.glideFrom * (J.glideTo / J.glideFrom) ** (t / J.sputterSeconds)) / RATE;
    glide = (glide + step) % 1;
    buzz += 0.1 * (pulseAt(glide, step, 0.3) - buzz);
    const fading = Math.min(1, (J.sputterSeconds - t) / 0.15);
    return fading * (0.5 * smoothed * (1 - 0.4 * t / J.sputterSeconds) * sample + 0.3 * (1 - 0.5 * t / J.sputterSeconds) * buzz);
  });
  J.backfires.forEach((when) => thunk(sputter, when, 55, 0.9, sputterRandom));
  for (let k = 0; k < J.sparks; k++) {
    click(sputter, sputterRandom() * (J.sputterSeconds - 0.2), 4000 + 2000 * sputterRandom(), 0.15 + 0.3 * sputterRandom(), sputterRandom);
  }
  writeSound("jump_drive_sputter", sputter, 0.8);
}
{
  const J = JUMP_DRIVE;
  const thumpRandom = ownRandom(0x4a445468);
  const thump = new Float64Array(Math.round(J.thumpSeconds * RATE));
  thunk(thump, 0, J.fistHz, 1, thumpRandom);
  gong(thump, 0, J.panelHz, 0.25, 0.35);
  for (let k = 0; k < 4; k++) {
    click(thump, J.rattleAt + k * 0.045 + 0.015 * thumpRandom(), 1800 + 900 * thumpRandom(), 0.4, thumpRandom);
  }
  thunk(thump, J.clunkAt, 110, 0.6, thumpRandom);
  gearRing(thump, J.clunkAt + 0.002, 520, 0.2);
  // The whine catching: flickering twice as it takes, then rising into tune and holding.
  const caught = J.thumpSeconds - J.catchAt;
  const pitchAt = (t) => J.catchFrom * (J.catchTo / J.catchFrom) ** Math.min(1, t / 0.8);
  const whine = lowPassed(sawStack(caught, pitchAt, [1, 1.006]), 3500);
  whine.forEach((sample, i) => {
    const t = i / RATE;
    const flicker = t < 0.12 ? Math.max(0, Math.sin(2 * Math.PI * 16 * t)) : 1;
    thump[Math.round(J.catchAt * RATE) + i] += 0.4 * flicker * Math.min(1, t / 0.3) * Math.min(1, (caught - t) / 0.2) * sample;
  });
  writeSound("jump_drive_thump", thump, 0.85);
}
{
  const J = JUMP_DRIVE;
  const jumpRandom = ownRandom(0x4a444a6d);
  const jump = new Float64Array(Math.round(J.jumpSeconds * RATE));
  const shot = J.shootAt + J.shooting;
  const boomAt = shot + J.silence;
  // The rush of air swelling up to the shot, a band of noise rising from 300 hertz to 3 kilohertz.
  const rush = sweptResonance(whiteNoise(J.jumpSeconds, jumpRandom), (t) => 300 + 2700 * Math.min(1, t / shot), 1.2);
  let phase = 0;
  let boom = 0;
  let brown = 0;
  let blast = 0;
  for (let i = 0; i < Math.round(shot * RATE); i++) {
    const t = i / RATE;
    const shooting = Math.max(0, Math.min(1, (t - J.shootAt) / J.shooting));
    phase += (J.shootFrom * (J.shootTo / J.shootFrom) ** shooting) / RATE;
    const whine = t >= J.shootAt ? (0.3 + 0.7 * shooting) * (Math.sin(2 * Math.PI * phase) + 0.3 * Math.sin(4 * Math.PI * phase)) : 0;
    jump[i] = (2.5 * (t / shot) ** 2 * rush[i] + 0.35 * whine) * Math.min(1, (shot - t) / 0.004);
  }
  // The boom after the silence: a sub falling from 90 to 30 hertz as the techno room's drop does, a blast of low noise
  // and a crack as it lands, and a rush of air going away behind it, falling from 2 kilohertz.
  const away = sweptResonance(whiteNoise(J.jumpSeconds, jumpRandom), (t) => 200 + 1800 * Math.exp(-Math.max(0, t - boomAt) / 0.4), 1);
  for (let i = Math.round(boomAt * RATE); i < jump.length; i++) {
    const t = i / RATE - boomAt;
    boom += (J.boomTo + (J.boomFrom - J.boomTo) * Math.exp(-t * 2.2)) / RATE;
    const white = jumpRandom() * 2 - 1;
    brown = (brown + 0.02 * white) * 0.996;
    blast += 0.05 * (brown - blast);
    const crack = t < 0.01 ? white * (1 - t / 0.01) : 0;
    const ending = Math.min(1, (J.jumpSeconds - boomAt - t) / 0.2);
    jump[i] = ending * (1.6 * Math.exp(-t * 0.9) * Math.min(1, t * 400) * Math.sin(2 * Math.PI * boom) + 12 * Math.exp(-t / 0.3) * blast
      + 0.6 * crack + 1.2 * Math.exp(-t / 0.5) * away[i]);
  }
  // Driven a little into saturation for weight, and its sharpest edges smoothed, so the encoder does not ring past
  // full scale.
  writeSound("jump_drive_jump", lowPassed(jump.map((sample) => Math.tanh(1.2 * sample)), 10000), 0.8);
}

// The Spookie's voice (see the plan's "The Spookie"), each sound with a random sequence of its own: a big creature's
// throat, a pulse through the three formants of its mouth gliding between "oo" and "ah", wandering a little in pitch,
// with a breath of air through them. Its greeting: a "hoo-HOO", the first hoot rising from 140 to 190 hertz and the
// second, louder, from 190 to 240, and a chuff, two puffs of breath through its nose. Its happy purr: a low "uhh" at
// 120 hertz rolled 24 times a second, rising into a hoot. Its alarm: three pairs of honks through its nose, each pair a
// minor third apart and higher than the last. Its roar: a 65 hertz bellow with a subharmonic under it, its mouth
// sweeping a didgeridoo's wah from 400 hertz up to 1,100 and back to 600, driven hard. Hoots, chuffs, a purr and a
// bellow, its own, and never a growling moan. Tune them here.
const SPOOKIE = {
  // Its mouth's three formants for "oo" and for "ah", in hertz, and each one's sharpness and loudness.
  oo: [300, 870, 2240],
  ah: [730, 1090, 2440],
  formantQ: [5, 7, 9],
  formantGain: [1, 0.55, 0.3],
  // Its throat's pulse, as the share of each turn it is open; the tilt of its spectrum, a low-pass in hertz; how far
  // its pitch wanders, as a share; and its breath, as a share of its voice.
  pulseOpen: 0.25,
  tilt: 500,
  wander: 0.6,
  breath: 0.12,
  // Its nose's resonance as it honks, in hertz, and how loud beside its mouth's.
  nasalHz: 1100,
  nasal: 1.4,
  // The roar: its pitch, in hertz; its wah from, up to and back down to, in hertz; and how hard it is driven.
  roarHz: 65,
  wah: [400, 1100, 600],
  roarDrive: 2.5,
};

/**
 * The Spookie's voice for so many seconds, from a random sequence of its own: its throat's pulse at pitchAt(t) hertz,
 * wandering a little, through its mouth's formants gliding from "oo" to "ah" as openAt(t) goes from 0 to 1, as loud as
 * loudAt(t), with breath; and through its nose too, as loud as nasalAt(t).
 */
function spookieVoice(seconds, own, pitchAt, openAt, loudAt, nasalAt = () => 0) {
  const S = SPOOKIE;
  const source = new Float64Array(Math.round(seconds * RATE));
  let phase = 0;
  let wander = 0;
  for (let i = 0; i < source.length; i++) {
    const t = i / RATE;
    wander += 0.001 * (own() * 2 - 1 - wander);
    const step = (pitchAt(t) * (1 + S.wander * wander)) / RATE;
    phase = (phase + step) % 1;
    source[i] = loudAt(t) * (pulseAt(phase, step, S.pulseOpen) + S.breath * (own() * 2 - 1));
  }
  const throat = lowPassed(source, S.tilt);
  const mouth = S.oo.map((oo, k) => sweptResonance(throat, (t) => oo + (S.ah[k] - oo) * openAt(t), S.formantQ[k]));
  const nose = resonance(throat, S.nasalHz, 6);
  return throat.map((_, i) => mouth.reduce((sum, formant, k) => sum + S.formantGain[k] * formant[i], 0) + S.nasal * nasalAt(i / RATE) * nose[i]);
}

/** How loud a sound is t seconds in that sounds from one time to another, in seconds, swelling in and dying away. */
function spoken(t, from, to) {
  return t < from || t > to ? 0 : Math.min(1, (t - from) / 0.04) * Math.min(1, (to - t) / 0.08);
}
{
  const greetRandom = ownRandom(0x53706b47);
  const seconds = 1.4;
  const hoots = [[0.05, 0.38, 140, 190, 0.7], [0.45, 0.95, 190, 240, 1]];
  const hootOf = (t) => hoots.find(([, to]) => t <= to) ?? hoots[1];
  const greet = spookieVoice(seconds, greetRandom, (t) => {
    const [from, to, low, high] = hootOf(t);
    return low + (high - low) * Math.max(0, Math.min(1, (t - from) / (to - from)));
  }, (t) => 0.1 + 0.25 * Math.sin(Math.PI * Math.max(0, Math.min(1, (t - 0.45) / 0.5))), (t) => {
    const [from, to, , , loud] = hootOf(t);
    return loud * spoken(t, from, to);
  });
  // The chuff: two puffs of breath through its nose.
  const puffs = resonance(whiteNoise(seconds, greetRandom), 900, 2);
  greet.forEach((_, i) => {
    const t = i / RATE;
    const puff = [1.05, 1.17].reduce((sum, at) => sum + (t >= at ? Math.min(1, (t - at) / 0.005) * Math.exp(-(t - at) / 0.035) : 0), 0);
    greet[i] += 1.5 * puff * puffs[i];
  });
  writeSound("spookie_greet", greet, 0.8);
}
{
  const happyRandom = ownRandom(0x53706b48);
  const seconds = 1.5;
  const purring = 0.8;
  const happy = spookieVoice(seconds, happyRandom, (t) => 120 + 140 * gearRamp(t, purring, 1.25),
    (t) => 0.3 - 0.2 * gearRamp(t, purring, 1.1),
    (t) => spoken(t, 0.02, seconds - 0.05) * (t < purring ? 0.55 + 0.45 * Math.max(0, Math.sin(2 * Math.PI * 24 * t)) : 0.7 + 0.3 * gearRamp(t, purring, 1.1)));
  writeSound("spookie_happy", happy, 0.75);
}
{
  const alarmRandom = ownRandom(0x53706b41);
  const seconds = 1.45;
  const honk = 0.12;
  // Each honk's start, in seconds, and pitch, in hertz: three pairs, each a minor third apart, each pair higher.
  const honks = [0, 1, 2].flatMap((pair) => {
    const high = 240 * 1.05 ** pair;
    const at = 0.03 + pair * 0.47;
    return [[at, high], [at + honk + 0.05, high / 2 ** (3 / 12)]];
  });
  const honkOf = (t) => honks.findLast(([at]) => at <= t) ?? honks[0];
  const alarm = spookieVoice(seconds, alarmRandom, (t) => honkOf(t)[1] * (1 + 0.04 * Math.min(1, (t - honkOf(t)[0]) / honk)), () => 0.55,
    (t) => spoken(t, honkOf(t)[0], honkOf(t)[0] + honk), () => 1);
  writeSound("spookie_alarm", alarm.map((sample) => Math.tanh(1.5 * sample)), 0.8);
}
{
  const roarRandom = ownRandom(0x53706b52);
  const S = SPOOKIE;
  const seconds = 2;
  const [from, top, to] = S.wah;
  const wahAt = (t) => (t < 0.8 ? from + (top - from) * gearRamp(t, 0.1, 0.8) : top + (to - top) * gearRamp(t, 0.8, 1.7));
  const loudAt = (t) => Math.min(1, t / 0.15) * Math.min(1, (seconds - t) / 0.5) * (1 + 0.08 * Math.sin(2 * Math.PI * 5 * t));
  // Its throat: the bellow's pulse and, under it, a subharmonic an octave down, so every other pulse comes harder.
  const source = new Float64Array(Math.round(seconds * RATE));
  let phase = 0;
  for (let i = 0; i < source.length; i++) {
    const t = i / RATE;
    const step = S.roarHz / RATE;
    phase = (phase + step) % 2;
    const pulse = pulseAt(phase % 1, step, S.pulseOpen) * (phase < 1 ? 1 : 0.6);
    source[i] = loudAt(t) * (pulse + 0.25 * (roarRandom() * 2 - 1));
  }
  const throat = lowPassed(source, 900);
  const wah = sweptResonance(throat, wahAt, 6);
  const brass = resonance(throat, 2300, 5);
  const roar = throat.map((sample, i) => Math.tanh(S.roarDrive * (3 * wah[i] + 0.5 * brass[i] + 0.3 * sample)));
  writeSound("spookie_roar", lowPassed(roar, 8000), 0.85);
}

// The freighter's robots (see the plan's "Robots aboard"), pure tones, so they draw on no random sequence: short,
// bright electronic beeps of their own. Each beep is a blip of a sine and a soft square wave, struck in two
// milliseconds and dying a little as it sounds, a touch of ring modulation at an uneven ratio giving it a metallic
// edge, through a small speaker's band. The beeps step from note to note, never gliding: no whistles, swoops or
// warbles. The deck bot's chirp as it passes someone: two blips stepping up a fourth. A robot's trill as it answers a
// click: eight quick notes skipping up a pentatonic tune, and a high blip on top. Its alarm: three bursts of a hard,
// buzzier tritone, two notes swapping fast. Its happy beep: a major arpeggio climbing in five blips and a double blip at
// the top. Tune them here.
const ROBOT = {
  // The square wave's share of a blip, and the ring modulation's share and its ratio to the note, an uneven one.
  square: 0.25,
  ring: 0.2,
  ringRatio: 2.41,
  // How long a blip rings on once let go, in seconds.
  release: 0.015,
  // The small speaker's band, in hertz.
  speaker: [400, 7000],
  // Each beep as its blips: a MIDI note, when it starts and how long it sounds, in seconds; and how loud.
  chirp: [[88, 0, 0.06], [93, 0.09, 0.09]],
  trill: [[84, 0], [88, 1], [86, 2], [91, 3], [88, 4], [93, 5], [91, 6], [96, 7]].map(([note, k]) => [note, k * 0.057, 0.045]).concat([[100, 0.5, 0.07]]),
  alarm: [0, 1, 2].flatMap((burst) => [0, 1, 2, 3].map((k) => [k % 2 === 0 ? 95 : 89, burst * 0.28 + k * 0.05, 0.05])),
  happy: [79, 83, 86, 91, 95].map((note, k) => [note, k * 0.07, 0.055]).concat([[98, 0.37, 0.04], [98, 0.43, 0.06]]),
  // The alarm's harder ring and square.
  alarmRing: 0.4,
  alarmSquare: 0.6,
};

/** A robot's beep, its blips as ROBOT gives them, the ring modulation's and the square's shares as given. */
function robotBeep(blips, ring = ROBOT.ring, square = ROBOT.square) {
  const seconds = Math.max(...blips.map(([, at, length]) => at + length)) + ROBOT.release + 0.02;
  const beep = new Float64Array(Math.round(seconds * RATE));
  for (const [note, at, length] of blips) {
    const frequency = noteHertz(note);
    const step = frequency / RATE;
    for (let i = 0; i < Math.round((length + ROBOT.release) * RATE); i++) {
      const t = i / RATE;
      const phase = (step * i) % 1;
      const envelope = Math.min(1, t / 0.002) * (t < length ? 1 - 0.3 * (t / length) : 0.7 * Math.exp(-(t - length) / (ROBOT.release / 4)));
      const tone = Math.sin(2 * Math.PI * phase) + square * pulseAt(phase, step, 0.5);
      beep[Math.round(at * RATE) + i] += envelope * tone * (1 - ring + ring * Math.sin(2 * Math.PI * ROBOT.ringRatio * frequency * t));
    }
  }
  return lowPassed(highPassed(beep, ROBOT.speaker[0]), ROBOT.speaker[1]);
}
writeSound("robot_chirp", robotBeep(ROBOT.chirp), 0.6);
writeSound("robot_trill", robotBeep(ROBOT.trill), 0.6);
writeSound("robot_alarm", robotBeep(ROBOT.alarm, ROBOT.alarmRing, ROBOT.alarmSquare), 0.65);
writeSound("robot_happy", robotBeep(ROBOT.happy), 0.6);

// The freighter's radar dish turning on her top (see the plan's "Ideas of our own"), with a random sequence of its own:
// a seamless loop one turn of the dish long, played faster while her autopilot flies. Its 60 hertz motor humming with
// a whine at its ninth harmonic, the rumble of its gear train, and once a turn its bearing squeaking and its mount
// creaking. Every tone turns a whole number of times round the loop, and the noise and the creak are wrapped round it.
// Tune it here.
const RADAR_DISH = {
  // One turn of the dish, in seconds; its motor, in hertz, and the harmonic its gears whine at.
  turn: 4,
  motorHz: 60,
  whineHarmonic: 9,
  // The bearing's squeak: when in the turn, how long, in seconds, and its pitch and stick-slip, in hertz.
  squeakAt: 1.1,
  squeaking: 0.18,
  squeakHz: 1900,
  slipHz: 85,
  // The mount's creak, when in the turn, in seconds.
  creakAt: 2.7,
};
{
  const R = RADAR_DISH;
  const dishRandom = ownRandom(0x52646172);
  const fade = 0.3;
  let brown = 0;
  let gears = 0;
  const train = levelled(seamlessNoise(Float64Array.from({ length: Math.round((R.turn + fade) * RATE) }, () => {
    brown = (brown + 0.02 * (dishRandom() * 2 - 1)) * 0.995;
    gears += 0.05 * (brown - gears);
    return gears;
  }), R.turn, fade));
  const whirr = train.map((sample, i) => {
    const t = i / RATE;
    const phase = 2 * Math.PI * R.motorHz * t;
    const hum = Math.sin(phase) + 0.5 * Math.sin(2 * phase) + 0.25 * Math.sin(3 * phase);
    const whine = Math.sin(R.whineHarmonic * phase) * (0.8 + 0.2 * Math.sin((2 * Math.PI * 2 * t) / R.turn));
    return 0.35 * hum + 0.12 * whine + 0.25 * sample;
  });
  // The squeak: a high note stuck and slipping some 85 times a second, wavering, swelling in and out.
  addRound(whirr, R.squeakAt, R.squeaking, (t) => {
    const slip = (0.5 + 0.5 * Math.sin(2 * Math.PI * R.slipHz * t)) ** 2;
    const pitch = R.squeakHz * (1 + 0.04 * Math.sin(2 * Math.PI * 7 * t));
    return 0.18 * Math.sin(Math.PI * t / R.squeaking) * slip * Math.sin(2 * Math.PI * pitch * t);
  });
  const creak = new Float64Array(Math.round(1.4 * RATE));
  girderGroan(creak, 0, dishRandom, 12);
  mixRound(whirr, R.creakAt, creak);
  writeSound("radar_dish_whirr", whirr, 0.5);
}

// The hidden compartments under her grates (see the plan's "Ideas of our own"), each sound with a random sequence of
// its own. Opening: two latch clicks at 2.2 kilohertz, 0.3 seconds of pneumatic hiss, the plate sliding away, a band of
// noise round 600 hertz gritty with the dirt in its runners, and a soft thud as it seats. Closing: the other way round,
// the plate sliding back and thudding home, the seal hissing and the latches clicking shut. Tune them here.
const HIDDEN_HATCH = {
  // The latches' pitch, in hertz, and the gap between them, in seconds.
  latchHz: 2200,
  latchGap: 0.06,
  // The hiss, how long, in seconds, and its high-pass, in hertz.
  hissing: 0.3,
  hissCutoff: 2500,
  // The slide, how long, in seconds, and its band, in hertz.
  sliding: 0.6,
  slideHz: 600,
  // The thud as it seats, in hertz.
  seatHz: 90,
  // When each part comes, opening and closing, in seconds; and how long each sound is.
  opening: { latches: 0, hiss: 0.1, slide: 0.42, seat: 1.02 },
  closing: { slide: 0, seat: 0.6, hiss: 0.68, latches: 1.02 },
  seconds: 1.3,
};

/** A hidden compartment's hatch opening or closing, its parts at the times given, from a random sequence of its own. */
function hiddenHatch(times, own) {
  const H = HIDDEN_HATCH;
  const samples = new Float64Array(Math.round(H.seconds * RATE));
  [0, H.latchGap].forEach((gap, k) => {
    click(samples, times.latches + gap, H.latchHz * (1 + 0.05 * k), 1, own);
    thunk(samples, times.latches + gap, 180, 0.15, own);
  });
  const hiss = highPassed(whiteNoise(H.hissing, own), H.hissCutoff);
  hiss.forEach((sample, i) => {
    const t = i / RATE;
    samples[Math.round(times.hiss * RATE) + i] += 1.2 * Math.min(1, t / 0.005) * Math.exp(-t / 0.12) * Math.min(1, (H.hissing - t) / 0.03) * sample;
  });
  const slide = resonance(whiteNoise(H.sliding, own), H.slideHz, 2);
  let grit = 0;
  slide.forEach((sample, i) => {
    const t = i / RATE;
    grit += 0.01 * (own() * 2 - 1 - grit);
    samples[Math.round(times.slide * RATE) + i] += 3 * Math.sin((Math.PI * t) / H.sliding) * (1 + 10 * grit) * sample;
  });
  thunk(samples, times.seat, H.seatHz, 0.5, own);
  return lowPassed(samples, 9000);
}
writeSound("hidden_hatch_open", hiddenHatch(HIDDEN_HATCH.opening, ownRandom(0x48486f70)), 0.7);
writeSound("hidden_hatch_close", hiddenHatch(HIDDEN_HATCH.closing, ownRandom(0x4848636c)), 0.7);

// The freighter's computer (see the plan's "Sound"), said as the flight deck's lines are (see say): an American
// woman's voice, Kokoro's Bella, which no other voice in the mod uses, so it is never taken for the flight deck's or
// launch control's, heard through a small speaker in her cockpit, its narrow band, its boxy, bright middle and the
// cockpit's short reflections, compressed as a ship's announcements are; and before each line a two-tone blip,
// falling. Magpie, the name she answers to on the radio, is spoken as her own. Tune it here.
const COMPUTER = {
  voice: "af_bella",
  pace: 1,
  // The pause a "|" in a line makes, in milliseconds: the beat of a countdown.
  pause: 380,
  // FFmpeg's filters for the cockpit's speaker, in order, and for levelling the line after them.
  loudspeakers: [
    "highpass=f=320:poles=2",
    "lowpass=f=5200:poles=2",
    "equalizer=f=1900:t=q:w=1.4:g=4",
    "aecho=0.9:0.6:4|9:0.25|0.12",
  ],
  levelling: ["acompressor=threshold=-26dB:ratio=3:attack=3:release=90:makeup=8dB"],
  // The blip: its tones, falling, in hertz, each so long and the gap after it, in seconds, and how loud beside the
  // voice's peak.
  chirp: [1568, 1175],
  chirpLength: 0.07,
  chirpGap: 0.03,
  chirpLevel: 0.35,
  // The peak it is written at, as the other voices'.
  level: 0.85,
};
const COMPUTER_LINES = {
  freighter_computer_jump_charged: "Jump drive charged.",
  freighter_computer_jump_fault: "Jump drive fault.",
  freighter_computer_ramp_lowering: "Ramp lowering.",
  freighter_computer_turret_ready: "Turret ready.",
  freighter_computer_jump_countdown: "Jump in three, | two, | one.",
  freighter_computer_magpie_ready: "Magpie ready.",
};
/** The longest a line may run, its blip and all, in seconds; and the countdown, a beat a number. */
const COMPUTER_LONGEST = 2.5;
const COMPUTER_COUNTDOWN_LONGEST = 4;
// Her computer through her hyperspace jump (see FreighterJump), our own lines in the voice of a worn old cargo hauler's
// computer that has seen it all: spooling up as the button is pressed, letting go as she jumps, coming out of it at the
// pin, and, the button pressed when she cannot jump, why not: no pin set, not flying, or the pin too near to bother.
// Said in COMPUTER's voice, its blip first, as her other lines are; a line in two phrases may run longer.
const COMPUTER_JUMP_LINES = {
  freighter_computer_jump_spooling: "Jump drive spooling up. | Hold on to something.",
  freighter_computer_jump_go: "Here goes nothing.",
  freighter_computer_jump_arrived: "Out of the jump. | Welcome to wherever this is.",
  freighter_computer_jump_no_pin: "Nowhere to jump to. | Pick a spot on the map.",
  freighter_computer_jump_grounded: "Not from down here. | Get her flying first.",
  freighter_computer_jump_too_near: "That is practically next door. | Just fly there.",
};
const COMPUTER_JUMP_LONGEST = 4.5;
/** A line of her computer's, said: its blip, then the voice. */
function computerLine(name) {
  const voice = trim(readWav(join(scratch, `${name}.voiced.wav`)));
  const chirp = toneChirp(COMPUTER);
  const samples = new Float64Array(chirp.length + voice.length);
  const level = COMPUTER.chirpLevel * peakOf(voice);
  chirp.forEach((sample, i) => (samples[i] = sample * level));
  samples.set(voice, chirp.length);
  return samples;
}
if (speaker) {
  await say(speaker, COMPUTER_LINES, COMPUTER, COMPUTER);
  for (const name of Object.keys(COMPUTER_LINES)) {
    writeVoiced(name, computerLine(name), COMPUTER, name === "freighter_computer_jump_countdown" ? COMPUTER_COUNTDOWN_LONGEST : COMPUTER_LONGEST);
  }
  await say(speaker, COMPUTER_JUMP_LINES, COMPUTER, COMPUTER);
  for (const name of Object.keys(COMPUTER_JUMP_LINES)) {
    writeVoiced(name, computerLine(name), COMPUTER, COMPUTER_JUMP_LONGEST);
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
