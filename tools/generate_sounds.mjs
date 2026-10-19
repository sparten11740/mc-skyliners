import { fileURLToPath } from "node:url";
import { mkdirSync, readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { resolve, join } from "node:path";
import { tmpdir } from "node:os";
import { execFileSync } from "node:child_process";

// Run from anywhere: node tools/generate_sounds.mjs
// Writes the airliner's sounds as Ogg Vorbis into assets/aerolarge/sounds, with FFmpeg on PATH:
// - engine.ogg: a turbofan running, a seamless loop the engines play, pitched and swelled with thrust;
// - the flight deck's voice, its callouts and cabin announcements, spoken by Windows' own speech synthesis
//   (System.Speech) and then reworked (see VOICE), so this part runs on Windows only. Deterministic apart from the
//   voice, which is whatever Windows ships.
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

function toOgg(wav, name) {
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", resolve(output, `${name}.ogg`)]);
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

/** Peak-normalises samples to a little under full scale and writes them as the named sound. */
function writeSound(name, samples) {
  let peak = 0;
  samples.forEach((s) => (peak = Math.max(peak, Math.abs(s))));
  const wav = join(scratch, `${name}.wav`);
  writeWav(wav, Array.from(samples, (s) => (s / peak) * 0.85));
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
// The cabin announcements (see CabinAnnouncements), each opening with the cabin chime. A "|" is a pause.
const ANNOUNCEMENTS = {
  announcement_takeoff: "Welcome aboard. | Cabin crew, please be seated for take-off.",
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
 * Says every line with the speech synthesis, all in one go, into WAVs in the scratch folder; false, saying why, if
 * the voice is not installed.
 */
function speak(lines) {
  const ssml = (text) => {
    const escaped = text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    const paused = escaped.replace(/\s*\|\s*/g, ` <break time='${VOICE.pause}ms'/> `);
    return `<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'><prosody rate='${VOICE.rate}'>${paused}</prosody></speak>`;
  };
  const said = join(scratch, "lines.json");
  writeFileSync(said, JSON.stringify(Object.entries(lines).map(([name, text]) => ({ wav: join(scratch, `${name}.said.wav`), ssml: ssml(text) }))));
  const quote = (text) => `'${text.replace(/'/g, "''")}'`;
  const script = [
    "$ProgressPreference = 'SilentlyContinue'",
    "Add-Type -AssemblyName System.Speech",
    "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
    `if (-not ($s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Name -eq ${quote(VOICE.name)} })) { exit 3 }`,
    `$s.SelectVoice(${quote(VOICE.name)})`,
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
      console.log(`The voice "${VOICE.name}" is not installed: leaving the callouts and announcements as they are`);
      return false;
    }
    throw error;
  }
}

if (process.platform !== "win32") {
  console.log("Not on Windows: leaving the callouts and announcements as they are");
} else if (speak({ ...CALLOUTS, ...ANNOUNCEMENTS })) {
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

rmSync(scratch, { recursive: true, force: true });
console.log("Sounds written to", output);
