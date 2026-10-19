import { fileURLToPath } from "node:url";
import { mkdirSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { resolve, join } from "node:path";
import { tmpdir } from "node:os";
import { execFileSync } from "node:child_process";

// Run from anywhere: node tools/generate_sounds.mjs
// Writes the airliner's sounds as Ogg Vorbis into assets/aerolarge/sounds, with FFmpeg on PATH:
// - engine.ogg: a turbofan running, a seamless loop the engines play, pitched and swelled with thrust;
// - the cockpit callouts, spoken by Windows' own speech synthesis (System.Speech, the "Microsoft David" voice), so
//   this part runs on Windows only. Deterministic apart from the voice, which is whatever Windows ships.
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

// The callouts, as the aircraft's synthetic voice would say them.
const CALLOUTS = {
  callout_v1: "V one.",
  callout_rotate: "Rotate.",
  callout_positive_rate: "Positive rate.",
  callout_100: "One hundred.",
  callout_50: "Fifty.",
  callout_40: "Forty.",
  callout_30: "Thirty.",
  callout_20: "Twenty.",
  callout_10: "Ten.",
  callout_retard: "Retard. Retard.",
  callout_bank_angle: "Bank angle. Bank angle.",
  callout_pull_up: "Pull up. Pull up.",
};
if (process.platform === "win32") {
  for (const [name, text] of Object.entries(CALLOUTS)) {
    const wav = join(scratch, `${name}.wav`);
    const script = [
      "Add-Type -AssemblyName System.Speech",
      "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
      "try { $s.SelectVoice('Microsoft David Desktop') } catch {}",
      "$s.Rate = 1",
      `$s.SetOutputToWaveFile('${wav.replace(/'/g, "''")}')`,
      `$s.Speak('${text.replace(/'/g, "''")}')`,
      "$s.Dispose()",
    ].join("; ");
    execFileSync("powershell", ["-NoProfile", "-Command", script]);
    toOgg(wav, name);
  }
} else {
  console.log("Not on Windows: leaving the callouts as they are");
}

rmSync(scratch, { recursive: true, force: true });
console.log("Sounds written to", output);
