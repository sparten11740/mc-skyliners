#!/usr/bin/env node
// Plays the things that have gone wrong before, in a world of its own in the dev client, through Blockpilot: real keys
// and clicks, and what the player then sees, hears and holds. There are no unit tests because almost nothing that goes
// wrong here can be seen without the game. A bug found in play gets a check here where one can catch it.
//
//   node tools/playtest.mjs               the quick checks, about five minutes
//   node tools/playtest.mjs --all         those and the slow ones, about thirteen minutes
//   node tools/playtest.mjs fog-hiss ...  only these
//   node tools/playtest.mjs --list        what there is
//
// It needs Blockpilot's jar in run/mods. With no dev client running it starts one from the code as it stands and closes
// it again; a client already running is used as it is, so it tests what that client was started with. It makes a flat
// world, skyliners-playtest, and deletes it afterwards, unless something failed: then the world stays to be looked at
// and the next run replaces it. It exits 0 if every check held, 1 if any did not and 2 if it could not reach the game.
// Each check is written as a player would find the fault, not from how the code happens to work.

import { spawn, spawnSync } from "node:child_process";
import { existsSync, rmSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const LAUNCHER = path.join(ROOT, "run/blockpilot/blockpilot");
const WORLD = "skyliners-playtest";
// How long to wait for the game to come up, for a reply and for a ship to be laid down, in seconds.
const GAME_UP_SECONDS = 300;
const REPLY_SECONDS = 60;
const BUILD_SECONDS = 280;
// How far apart the ships are laid down, in blocks, so none stands in another's way, and how far the steps of any
// ship's stairs are looked for round where it was laid down: out to either side, ahead of the player and behind.
const SITE_APART = 1200;
const STAIRS_ACROSS = 64;
const STAIRS_AHEAD = 196;
const STAIRS_BEHIND = 64;
// How many rows over the ground the lowest steps are looked for in: more than any flight should stop short by.
const STAIRS_ROWS = 4;
// The most blocks Blockpilot reads in one go is 4096, so a region is read in tiles this wide, a row at a time.
const TILE = 60;
// How long a ship's own stairs are given to come down once it is laid, in ticks.
const STAIRS_DOWN_TICKS = 200;
// How long the chunks round the player are given to load after going somewhere, in ticks.
const CHUNKS_TICKS = 40;
// A walk up the stairs: how far out from the bottom step it starts, how long the walk key is held in ticks, and how
// high it must have climbed by then, in blocks. A jump is never pressed: nobody should need one.
const WALK_START = 5;
const WALK_TICKS = 70;
const CLIMB_WANTED = 3;
// How far round the foot of the stairs the ground is looked over once they have gone, in blocks.
const STAIRS_MARGIN = 4;
// How long after a flight starts its stairs have folded and it has started to roll, in ticks.
const FOLDED_TICKS = 170;
const ROLLING_TICKS = 80;
// A flight: how high to fly over the ground and how fast, in blocks and blocks a second; how long it is given to get
// up there and how far up it must then be, and how long to land, in ticks.
const FLIGHT_HEIGHT = 100;
const FLIGHT_SPEED = 50;
const CLIMB_OUT_TICKS = 1400;
const FLIGHT_HEIGHT_WANTED = 60;
const LANDING_TICKS = 2400;
// How often something being waited for is looked for, in ticks.
const LOOK_EVERY = 20;
// A painting: how many pieces the freighter has, and how far out in front of it must be clear, in blocks.
const FREIGHTER_PIECES = 15;
const IN_FRONT_CLEAR = 3;
const SEE_THROUGH = new Set(["water", "light"]);
// Whether anything stands in the way there: not nothing, what the eye sees through, or cloth on a floor.
const seenPast = (state) => !state || SEE_THROUGH.has(state.split("[")[0]) || state.split("[")[0].endsWith("carpet");
// The fog machine's hiss: how far off the player stands, in blocks; a look well into its five seconds and one just past
// its end, and how soon it must have stopped after the machine goes, all in ticks.
const FOG_STAND_OFF = 4;
const FOG_STILL_PLAYING = 80;
const FOG_ENDED_BY = 110;
const FOG_STOPPED_BY = 3;
const TNT_FUSE = 5;
const TNT_DONE_TICKS = 15;
// The tardigrade: how long it is given to come to the hatch and burp, and for each step after, in ticks; what the
// massive burp sounds at and in how many voices, and how many puffs and streaks it must have drawn.
const BURP_WITHIN_TICKS = 400;
const NEXT_WITHIN_TICKS = 200;
const BACK_AGAIN_TICKS = 120;
const BIG_BURP_PITCH = 0.5;
const BIG_BURP_VOICES = 2;
const BIG_BURP_PUFFS = 70;
const STREAKS = 60;
// The shake, by how alike one frame is to the next while looking at the empty horizon: frames, how far apart in ticks,
// how far out from the rocket to stand, in blocks; in decibels how alike still frames are at least and how alike a
// moved one is at most, and how many frames must have moved.
const SHAKE_FRAMES = 60;
const SHAKE_EVERY = 4;
const SHAKE_STAND_OFF = 20;
const STILL_DECIBELS = 40;
const SHAKEN_DECIBELS = 32;
const SHAKEN_FRAMES = 4;
// The way a flight climbs, by the way its steps face, on the ground and as a player's yaw.
const CLIMBS = { south: [0, 1, 0], west: [-1, 0, 90], north: [0, -1, 180], east: [1, 0, -90] };
// Blocks in the rocket nothing can be aimed at, so the mouse wheel never meets them: drawn by nothing, or water.
const NEVER_AIMED_AT = new Set(["light", "water", "skyliners:airstair_step", "skyliners:dome_limit", "skyliners:lift_floor"]);
// The block states that give a different item, so each of their values is clicked and not only one of them.
const PICKED_APART_BY = new Set(["kind", "poster", "design", "plant", "tier", "color", "tone"]);
// How many of each kind of block are tried, and from how far out from a face the eye looks at it, in blocks.
const PICK_TRIES = 14;
const EYE_OUT = 1.3;
const EYE_HEIGHT = 1.62;
// Fewer kinds of block than this in the rocket means she was not there to read, and the check would pass on nothing.
const FEWEST_KINDS = 150;
// How far in from the middle of a block its face is looked at, and the points round the middle of a face looked at
// when its middle shows nothing, in blocks.
const FACE_DEPTH = 0.3;
const FACE_OFFSETS = [[0, 0], [0.3, 0.3], [-0.3, 0.3], [0.3, -0.3], [-0.3, -0.3]];
const PICK_FACES = [[0, 1, 0], [1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, -1, 0]];

class GameGone extends Error {}

const failures = [];
let ground = 0;
let nextSite = 1;
const built = new Map();

// One script sent to the game; the commands' replies. A command that fails does not throw: see must.
function send(script, seconds = REPLY_SECONDS) {
  const done = spawnSync(LAUNCHER, ["--json", "--timeout", String(seconds), script], { encoding: "utf8", maxBuffer: 1 << 28 });
  if (done.status === 2 || done.error || !done.stdout.trim()) {
    throw new GameGone(`could not reach the game: ${done.stderr || done.error}`);
  }
  return JSON.parse(done.stdout).results;
}

// One script sent to the game that has to work; the last command's reply.
function must(script, seconds) {
  const replies = send(script, seconds);
  const failed = replies.find((reply) => reply.ok === false);
  if (failed) {
    const touched = failed.err === "interrupted" ? `: something touched the game's window (${failed.input ?? "input"}), so leave it alone while this runs` : "";
    throw new Error(`${script}: ${failed.err} ${failed.arg ?? ""}${touched}`);
  }
  return replies.at(-1);
}

function expect(held, what, detail = "") {
  console.log(`  ${held ? "ok  " : "FAIL"} ${what}${detail ? `: ${detail}` : ""}`);
  if (!held) {
    failures.push(what);
  }
}

const creative = () => must("server gamemode creative @p; fly on");
const survival = () => must("server gamemode survival @p");
const emptyHands = () => send("?server clear @p; select 0");
const round = (value) => Math.round(value * 100) / 100;
const now = () => must("messages limit=1").tick;
const key = (x, y, z) => `${x},${y},${z}`;
const idOf = (state) => state.split("[")[0];

// A block state's properties, as Blockpilot leaves those at their defaults off.
function propsOf(state) {
  const inside = state.match(/\[(.*)]/);
  return Object.fromEntries(inside ? inside[1].split(",").map((pair) => pair.split("=")) : []);
}

// The blocks of a region from the server, but for air, by position.
function read(from, to) {
  const blocks = new Map();
  for (let y = from[1]; y <= to[1]; y++) {
    readRow(blocks, from, to, y);
  }
  return blocks;
}

function readRow(blocks, from, to, y) {
  for (let z = from[2]; z <= to[2]; z += TILE) {
    for (let x = from[0]; x <= to[0]; x += TILE) {
      readTile(blocks, [x, y, z], [Math.min(x + TILE - 1, to[0]), y, Math.min(z + TILE - 1, to[2])]);
    }
  }
}

function readTile(blocks, from, to) {
  const reply = must(`blocks ${from.join(",")} ${to.join(",")} grid side=server`);
  const states = Object.fromEntries(Object.entries(reply.palette ?? {}).map(([state, symbol]) => [symbol, state]));
  for (const row of reply.rows) {
    const cells = row.cells.map((symbol, across) => [key(from[0] + across, row.y, row.z), states[symbol]]);
    cells.filter(([, state]) => state).forEach(([at, state]) => blocks.set(at, state));
  }
}

// Lays a ship down on a site of its own, the player facing north, and gives its stairs time to come down.
function layDown(what) {
  const site = { x: nextSite++ * SITE_APART, z: 0, what };
  creative();
  must(`server tp @p ${site.x} ${ground} ${site.z}; turn 180 0; wait 40t`);
  must(`skyliners:build ${what}`, BUILD_SECONDS);
  must(`wait ${STAIRS_DOWN_TICKS}t`);
  return site;
}

// A ship to look at, laid down once, the player back at it: far off, its blocks are not there to read and a check of
// them would pass on nothing. Anything that flies a ship away lays down its own.
function ship(what) {
  if (!built.has(what)) {
    built.set(what, layDown(what));
  }
  const site = built.get(what);
  creative();
  must(`server tp @p ${site.x} ${ground + 5} ${site.z}; wait ${CHUNKS_TICKS}t`);
  return site;
}

// The steps of a ship's stairs in the lowest row they reach, as columns to walk up.
function bottomOfStairs(site) {
  const near = read([site.x - STAIRS_ACROSS, ground, site.z - STAIRS_AHEAD], [site.x + STAIRS_ACROSS, ground + STAIRS_ROWS, site.z + STAIRS_BEHIND]);
  const steps = [...near].filter(([, state]) => idOf(state) === "skyliners:airstair_step" && (propsOf(state).part ?? "step") === "step")
    .map(([at, state]) => ({ at: at.split(",").map(Number), facing: propsOf(state).facing ?? "north" }));
  const lowest = Math.min(...steps.map((step) => step.at[1]));
  return steps.filter((step) => step.at[1] === lowest);
}

// Walks at a point from the ground the way it climbs, in survival, with the walk key alone. How high it got, in blocks.
function walkFrom(x, z, yaw) {
  survival();
  must(`server tp @p ${x} ${ground} ${z}; turn ${yaw} 0; wait 25t`);
  const there = must(`move forward ${WALK_TICKS}t; status`);
  creative();
  return { climbed: round(there.pos[1] - ground), pos: there.pos.map(round).join(",") };
}

// Whether a ship's stairs, as they stand, can be walked up from the ground in the middle of each column.
function walksUp(what) {
  const site = ship(what);
  const bottom = bottomOfStairs(site);
  expect(bottom.length > 0, `${what}: its stairs have come down`);
  for (const step of bottom) {
    const [dx, dz, yaw] = CLIMBS[step.facing];
    const walked = walkFrom(step.at[0] + 0.5 - dx * WALK_START, step.at[2] + 0.5 - dz * WALK_START, yaw);
    expect(walked.climbed >= CLIMB_WANTED, `${what}: walked up from the ground at ${step.at[0]},${step.at[2]}`,
      `climbed ${walked.climbed} blocks, ending at ${walked.pos}; the bottom step is at y ${step.at[1]}, the ground at y ${ground}`);
  }
}

// Stands aboard an airliner laid down facing north, in the cabin just aft of its autopilot.
function board() {
  const [autopilot] = must("find skyliners:autopilot 128 1").rows;
  const [x, y, z] = autopilot.at_abs;
  must(`fly off; server tp @p ${x} ${y + 1} ${z - 3}; wait 20t`);
}

// Waits for a sound to have been played since a tick; the first of them, or undefined.
function heard(match, since, within) {
  for (let waited = 0; waited <= within; waited += LOOK_EVERY) {
    const [row] = must(`wait ${LOOK_EVERY}t; sounds since=${since} match=${match} limit=1`).rows;
    if (row) {
      return row;
    }
  }
  return undefined;
}

const playing = (match) => must(`sounds playing match=${match}`).count;

function checkAirstairsReachTheGround() {
  walksUp("furnished");
}

function checkZeppelinStairs() {
  walksUp("furnished_zeppelin");
}

function checkRocketStairs() {
  walksUp("rocket");
}

function checkNoStepsLeftBehind() {
  const site = layDown("airliner");
  const bottom = bottomOfStairs(site);
  expect(bottom.length > 0, "airliner: its stairs are down before it flies");
  const xs = bottom.map((step) => step.at[0]);
  const zs = bottom.map((step) => step.at[2]);
  board();
  must(`skyliners:autopilot fly altitude=${ground + FLIGHT_HEIGHT} speed=${FLIGHT_SPEED}`, BUILD_SECONDS);
  must(`wait ${FOLDED_TICKS}t`);
  const [fromX, toX, fromZ, toZ] = [Math.min(...xs) - STAIRS_MARGIN, Math.max(...xs) + STAIRS_MARGIN, Math.min(...zs) - STAIRS_MARGIN, Math.max(...zs) + STAIRS_MARGIN];
  const area = [...read([fromX, ground - 1, fromZ], [toX, ground + 1, toZ])];
  const left = area.filter(([, state]) => idOf(state) === "skyliners:airstair_step").map(([at]) => at);
  expect(left.length === 0, "airliner: no steps stay on the ground once it has assembled and folded them", left.join(" "));
  const earth = area.filter(([at]) => Number(at.split(",")[1]) === ground - 1).length;
  expect(earth === (toX - fromX + 1) * (toZ - fromZ + 1), "airliner: the ground under its stairs is not carried off with it", `${earth} blocks of it left`);
  const rolling = must(`wait ${ROLLING_TICKS}t; sable:info here brief`);
  expect(rolling.speed > 0, "airliner: it rolls away with its stairs folded", `at ${rolling.speed} blocks a second`);
  must(`server tp @p 0 ${ground} 0`);
  creative();
}

// The freighter on a ship: all of it there, nothing in front of it, and the mouse wheel gives the painting.
function checkFreighterOn(what) {
  ship(what);
  const pieces = must(`find skyliners:space_poster[poster=freighter] 128 ${FREIGHTER_PIECES + 1}`).rows.map((row) => row.at_abs);
  expect(pieces.length === FREIGHTER_PIECES, `${what}: the freighter hangs whole`, `${pieces.length} pieces of ${FREIGHTER_PIECES}`);
  if (pieces.length !== FREIGHTER_PIECES) {
    return;
  }
  const { facing } = must(`block ${pieces[0].join(",")}`).props;
  const [dx, dz] = CLIMBS[facing];
  const blocked = pieces.flatMap((piece) => standingInFront(piece, dx, dz));
  expect(blocked.length === 0, `${what}: nothing stands in front of it`, [...new Set(blocked)].join(" "));
  const middle = [0, 1, 2].map((axis) => [...pieces.map((piece) => piece[axis])].sort((a, b) => a - b)[Math.floor(pieces.length / 2)]);
  emptyHands();
  must(`server tp @p ${middle[0] + 0.5 + dx * 3} ${middle[1] + 0.5 - EYE_HEIGHT} ${middle[2] + 0.5 + dz * 3}; wait 5t; face ${middle.join(",")}`);
  const [held] = must("mouse click middle; wait 4t; inv").rows;
  expect(held?.item === "skyliners:freighter_painting", `${what}: the mouse wheel gives the painting`, held?.item ?? "nothing");
}

function standingInFront(piece, dx, dz) {
  const row = [];
  for (let out = 1; out <= IN_FRONT_CLEAR; out++) {
    const at = [piece[0] + dx * out, piece[1], piece[2] + dz * out];
    const [state] = read(at, at).values();
    if (state && !SEE_THROUGH.has(idOf(state))) {
      row.push(`${idOf(state)} at ${at.join(",")}`);
    }
  }
  return row;
}

function checkFreighterComesDownWhole() {
  ship("rocket");
  const pieces = must(`find skyliners:space_poster[poster=freighter] 128 ${FREIGHTER_PIECES + 1}`).rows.map((row) => row.at_abs);
  if (pieces.length !== FREIGHTER_PIECES) {
    return;
  }
  const lowest = Math.min(...pieces.map((piece) => piece[1]));
  const low = pieces.filter((piece) => piece[1] === lowest).sort((a, b) => a[2] - b[2] || a[0] - b[0])[2];
  const [dx, dz] = CLIMBS[must(`block ${low.join(",")}`).props.facing];
  emptyHands();
  must(`server tp @p ${low[0] + 0.5 + dx * 2} ${lowest - 2} ${low[2] + 0.5 + dz * 2}; wait 20t`);
  survival();
  must(`wait 20t; mine ${low.join(",")} timeout=10s; wait 40t`);
  const held = must("inv").rows.some((row) => row.item === "skyliners:freighter_painting");
  const dropped = must("find skyliners:freighter_painting 16 1").rows.length > 0;
  const given = held || dropped;
  creative();
  const left = must(`find skyliners:space_poster[poster=freighter] 128 ${FREIGHTER_PIECES + 1}`).rows.length;
  expect(left === 0 && given, "rocket: broken, the freighter comes down whole and is given back", `${left} pieces left, given back: ${given}`);
}

function checkFreighterOnTheShips() {
  ["furnished", "furnished_zeppelin", "rocket"].forEach(checkFreighterOn);
  checkFreighterComesDownWhole();
}

function checkFogHissStops() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  const machine = `${site.x} ${ground} ${site.z}`;
  const other = `${site.x} ${ground} ${site.z + 2}`;
  const machineAt = machine.replaceAll(" ", ",");
  const standing = `server tp @p ${site.x + FOG_STAND_OFF} ${ground} ${site.z}`;
  const setUp = (at) => must(`server setblock ${at} skyliners:fog_machine[facing=east]; wait 5t`);
  creative();
  must(`${standing}; wait 40t`);

  setUp(machine);
  must(`use ${machineAt}; wait ${FOG_STILL_PLAYING}t`);
  expect(playing("*fog_hiss*") === 1, "fog machine: left alone, the hiss is still playing near the end of its pump");
  must(`wait ${FOG_ENDED_BY - FOG_STILL_PLAYING}t`);
  expect(playing("*fog_hiss*") === 0, "fog machine: left alone, the hiss ends by itself");

  setUp(machine);
  must(`use ${machineAt}; wait 20t`);
  const brokenBefore = playing("*fog_hiss*");
  must(`mine ${machineAt}; wait ${FOG_STOPPED_BY}t`);
  expect(brokenBefore === 1 && playing("*fog_hiss*") === 0, "fog machine: broken by hand, the hiss stops at once");

  setUp(machine);
  must(`use ${machineAt}; wait 20t; server setblock ${machine} air; wait ${FOG_STOPPED_BY}t`);
  expect(playing("*fog_hiss*") === 0, "fog machine: removed by a command, the hiss stops at once");

  setUp(machine);
  setUp(other);
  must(`server setblock ${machine} skyliners:fog_machine[facing=east,fog=fogging]; server setblock ${other} skyliners:fog_machine[facing=east,fog=fogging]; wait 20t`);
  const bothBefore = playing("*fog_hiss*");
  must(`server setblock ${machine} air; wait ${FOG_STOPPED_BY}t`);
  expect(bothBefore === 2 && playing("*fog_hiss*") === 1, "fog machine: of two, breaking one leaves the other's hiss");
  must(`server setblock ${other} air`);

  setUp(machine);
  must(`${standing}; use ${machineAt}; wait 10t; server tp @p ${site.x + 12} ${ground} ${site.z}; wait 2t; server summon tnt ${machine} {fuse:${TNT_FUSE}}; wait ${TNT_DONE_TICKS}t`);
  const stillThere = read([site.x, ground, site.z], [site.x, ground, site.z]).size > 0;
  expect(stillThere || playing("*fog_hiss*") === 0, "fog machine: blown up, the hiss stops at once", stillThere ? "the machine survived the blast" : "");
}

// Feeds it through the hatch, trying again while it is away on a jump of its own, which refuses the food. The tick it
// was taken at.
function feedThroughHatch(hatch) {
  for (let tries = 0; tries < 8; tries++) {
    const before = must("inv").rows.find((row) => row.item === "skyliners:ice_cream")?.count ?? 0;
    const since = now();
    must(`use ${hatch}; wait ${LOOK_EVERY}t`);
    const after = must("inv").rows.find((row) => row.item === "skyliners:ice_cream")?.count ?? 0;
    if (after < before) {
      return since;
    }
    must("wait 100t");
  }
  throw new Error("it never took the ice cream");
}

// How alike two frames are, in decibels.
function decibels(first, second) {
  const done = spawnSync("ffmpeg", ["-hide_banner", "-i", first, "-i", second, "-lavfi", "psnr", "-f", "null", "-"], { encoding: "utf8" });
  const average = done.stderr.match(/average:(\S+)/);
  return average && average[1] !== "inf" ? Number(average[1]) : Infinity;
}

const median = (values) => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)];

// Feeds it, then stands on the ground at the rocket's foot looking at the empty horizon, clouds off: nothing can change
// one frame from the next there but the view itself. It is measured from there because a look at a wall by the hatch
// passed with the shake taken out: the burp's cloud drifts across anything near it. And it is felt that far from it.
function checkTheShipShakes(hatch, site) {
  if (spawnSync("ffmpeg", ["-version"]).error) {
    console.log("  skip the view shaking: it takes FFmpeg to measure");
    return;
  }
  must(`face ${hatch}; use ${hatch}; option renderClouds off; server tp @p ${site.x} ${ground} ${site.z + SHAKE_STAND_OFF}; turn 0 -10; wait 10t`);
  const frames = must(`screenshot 640 burst=${SHAKE_FRAMES} every=${SHAKE_EVERY}t hud=off`).rows;
  const changes = frames.slice(1).map((frame, index) => decibels(frames[index].file, frame.file));
  frames.forEach((frame) => rmSync(frame.file, { force: true }));
  send("?option reset");
  const shaken = changes.filter((alike) => alike <= SHAKEN_DECIBELS).length;
  expect(median(changes) >= STILL_DECIBELS && shaken >= SHAKEN_FRAMES, "tardigrade: the view shakes with the burp, as far off as the rocket's foot",
    `${shaken} frames moved of ${changes.length}, the rest typically ${round(median(changes))} dB alike`);
}

function checkTardigradeIceCream() {
  const site = ship("rocket");
  const [hatchRow] = must("find skyliners:habitat_hatch 128 1").rows;
  const hatch = hatchRow.at_abs.join(",");
  const { facing } = must(`block ${hatch}`).props;
  const [dx, dz] = CLIMBS[facing];
  const [hx, hy, hz] = hatchRow.at_abs;
  emptyHands();
  must(`server give @p skyliners:ice_cream 3; select 0; server tp @p ${hx + 0.5 + dx * 2.5} ${hy - 1} ${hz + 0.5 + dz * 2.5}; wait 20t; face ${hatch}`);
  survival();

  const since = feedThroughHatch(hatch);
  const left = must("inv").rows.find((row) => row.item === "skyliners:ice_cream")?.count;
  expect(left === 2, "tardigrade: fed through the hatch in survival, one ice cream is used up", `${left} left of 3`);
  const burp = heard("*tardigrade_burp*", since, BURP_WITHIN_TICKS);
  const voices = must(`sounds since=${since} match=*tardigrade_burp*`).rows.map((row) => row.pitch);
  expect(voices.includes(BIG_BURP_PITCH) && voices.length >= BIG_BURP_VOICES, "tardigrade: a massive burp, in two voices, an octave down", `pitches ${voices.join(" ")}`);
  const jump = heard("*tardigrade_jump*", since, NEXT_WITHIN_TICKS);
  expect(jump && burp && jump.tick > burp.tick, "tardigrade: it goes to hyperspace after the burp, not before", jump ? `${jump.tick - (burp?.tick ?? 0)} ticks after` : "no jump");
  const back = heard("*tardigrade_return*", since, NEXT_WITHIN_TICKS);
  expect(Boolean(back), "tardigrade: it comes back");
  const particles = Object.fromEntries(must(`particles since=${since}`).rows.map((row) => [row.type, row.count]));
  expect(particles.poof >= BIG_BURP_PUFFS, "tardigrade: a great cloud with the burp", `${particles.poof ?? 0} puffs`);
  expect(particles.end_rod >= STREAKS, "tardigrade: a streak of light as it goes", `${particles.end_rod ?? 0} streaks`);
  creative();
  must(`wait 60t; server tp @p ${hx + 0.5 + dx * 2.5} ${hy - 1} ${hz + 0.5 + dz * 2.5}`);
  checkTheShipShakes(hatch, site);

  must(`server tp @p ${hx + 0.5 + dx * 2.5} ${hy - 1} ${hz + 0.5 + dz * 2.5}; wait ${BACK_AGAIN_TICKS}t`);
  const [creature] = must("find skyliners:tardigrade 128 1").rows;
  const thrown = now();
  must(`server summon item ${creature.at_abs[0]} ${creature.at_abs[1] + 2} ${creature.at_abs[2]} {Item:{id:"skyliners:ice_cream",count:1}}`);
  expect(Boolean(heard("*tardigrade_burp*", thrown, BURP_WITHIN_TICKS)), "tardigrade: thrown into its tank, an ice cream does the same");
}

function checkControlSurfacesDefault() {
  const setting = must("config skyliners server autopilot.controlSurfaces");
  expect(setting.default === true, "control surfaces: on by default", `default ${setting.default}`);
}

// Flies an airliner up and back down, on its control surfaces, and walks up its stairs where it came to rest: there it
// stands on its suspension, not as it was built, and off square, so it is not the same walk as a new one's.
function checkFlightAndLanding() {
  try {
    flyAndLand();
  } finally {
    send("?config reset");
  }
}

function flyAndLand() {
  layDown("airliner");
  board();
  must("config skyliners server autopilot.controlSurfaces true");
  must(`skyliners:autopilot fly altitude=${ground + FLIGHT_HEIGHT} speed=${FLIGHT_SPEED}`, BUILD_SECONDS);
  must(`wait ${CLIMB_OUT_TICKS}t`, BUILD_SECONDS);
  const cruising = must("sable:info here brief");
  expect(cruising.agl >= FLIGHT_HEIGHT_WANTED, "flight: it takes off and climbs", `${cruising.agl} blocks over the ground at ${cruising.speed} blocks a second`);
  const since = now();
  must("skyliners:autopilot land");
  const landed = waitForMessage("*Landed*", since, LANDING_TICKS);
  expect(landed, "flight: it lands and stops");
  if (landed) {
    walkUpFlownStairs();
  }
  must(`server tp @p 0 ${ground} 0`);
  creative();
}

function waitForMessage(match, since, within) {
  for (let waited = 0; waited <= within; waited += LOOK_EVERY) {
    if (must(`wait ${LOOK_EVERY}t; messages since=${since} match=${match} limit=1`).count > 0) {
      return true;
    }
  }
  return false;
}

// Lowers the stairs of the airliner the player stands on and walks up them from the ground, up the middle.
function walkUpFlownStairs() {
  const [controller] = must("sable:find here skyliners:airstair_controller 1").rows;
  must(`sable:tp ${controller.at}; wait 10t; use ${controller.at}; wait 150t`);
  const steps = must("sable:find here skyliners:airstair_step[part=step] 60 side=server").rows.map((row) => row.world)
    .sort((a, b) => a[1] - b[1]);
  expect(steps.length > 0, "flight: its stairs come down where it landed");
  if (steps.length === 0) {
    return;
  }
  const bottom = steps.filter((world) => world[1] < steps[0][1] + 0.5);
  const up = steps.find((world) => world[1] > steps[0][1] + 0.5);
  const [x, z] = [0, 2].map((axis) => bottom.reduce((sum, world) => sum + world[axis], 0) / bottom.length);
  const [dx, dz] = [up[0] - bottom[0][0], up[2] - bottom[0][2]];
  const length = Math.hypot(dx, dz);
  const yaw = round(Math.atan2(-dx, dz) * 180 / Math.PI);
  const walked = walkFrom(round(x - dx / length * WALK_START), round(z - dz / length * WALK_START), yaw);
  expect(walked.climbed >= CLIMB_WANTED, "flight: walked up its stairs from the ground where it landed", `climbed ${walked.climbed} blocks, ending at ${walked.pos}`);
}

// What sets a kind of block apart for the mouse wheel: its id and any property that changes what it gives.
function kindOf(state) {
  const picked = Object.entries(propsOf(state)).filter(([name]) => PICKED_APART_BY.has(name)).map(([name, value]) => `${name}=${value}`);
  return idOf(state) + (picked.length ? `[${picked.join(",")}]` : "");
}

// Tries the wheel on one block from one open side, at one point; what it gave, or undefined if the crosshair met
// something else.
function pickFrom(blocks, at, face, point) {
  const eye = at.map((value, axis) => value + 0.5 + face[axis] * EYE_OUT);
  emptyHands();
  const aim = must(`server tp @p ${eye[0]} ${eye[1] - EYE_HEIGHT} ${eye[2]}; wait 3t; face ${point}`);
  const relative = aim.aim_pos?.match(/~(-?\d+),(-?\d+),(-?\d+)/);
  if (!relative) {
    return undefined;
  }
  const hit = [eye[0], eye[1] - EYE_HEIGHT, eye[2]].map((value, axis) => Math.floor(value) + Number(relative[axis + 1]));
  if (kindOf(blocks.get(key(...hit)) ?? "") !== kindOf(blocks.get(key(...at)))) {
    return undefined;
  }
  return must("mouse click middle; wait 4t; inv").rows[0]?.item ?? "nothing";
}

// Where to look on a block from a side: at the block, then round that face, as its shape may miss the middle (a
// lifebuoy is a ring, and a wall light hangs to one side).
function aimPoints(at, face) {
  const points = [at.join(",")];
  for (const [first, second] of FACE_OFFSETS) {
    const across = [first, second];
    const point = at.map((value, axis) => value + 0.5 + (face[axis] ? face[axis] * FACE_DEPTH : across.shift()));
    points.push(point.join(","));
  }
  return points;
}

const openSides = (blocks, at) => PICK_FACES.filter((face) => seenPast(blocks.get(key(at[0] + face[0], at[1] + face[1], at[2] + face[2]))));

function pickFace(blocks, at, face) {
  for (const point of aimPoints(at, face)) {
    const given = pickFrom(blocks, at, face, point);
    if (given !== undefined) {
      return given;
    }
  }
  return undefined;
}

function pickSpot(blocks, at) {
  for (const face of openSides(blocks, at)) {
    const given = pickFace(blocks, at, face);
    if (given !== undefined) {
      return given;
    }
  }
  return undefined;
}

// The wheel on one kind of block: each of a few of them, those with the most open sides first.
function pickKind(blocks, spots) {
  const tried = [...spots].sort((a, b) => openSides(blocks, b).length - openSides(blocks, a).length).slice(0, PICK_TRIES);
  for (const at of tried) {
    const given = pickSpot(blocks, at);
    if (given !== undefined) {
      return given;
    }
  }
  return undefined;
}

function checkRocketMiddleClick() {
  const site = ship("rocket");
  const blocks = read([site.x - 24, ground, site.z - 60], [site.x + 24, ground + 150, site.z + 8]);
  const kinds = new Map();
  for (const [at, state] of blocks) {
    const kind = kindOf(state);
    kinds.set(kind, [...(kinds.get(kind) ?? []), at.split(",").map(Number)]);
  }
  const nothing = [];
  const neverAimed = [];
  for (const [kind, spots] of kinds) {
    if (NEVER_AIMED_AT.has(idOf(kind))) {
      continue;
    }
    const door = idOf(kind) === "skyliners:airlock_door";
    if (door) {
      shutAirlockDoor(blocks, spots[0]);
    }
    const given = pickKind(blocks, door ? spots.slice(0, 1) : spots);
    if (given === undefined) {
      neverAimed.push(kind);
    } else if (given === "nothing") {
      nothing.push(kind);
    }
  }
  expect(kinds.size >= FEWEST_KINDS, "rocket: all of her was read", `${kinds.size} kinds of block`);
  expect(nothing.length === 0, "rocket: the mouse wheel gives something for every block it can meet", nothing.join(" "));
  expect(neverAimed.length === 0, "rocket: every block but the unseen ones can be aimed at", neverAimed.join(" "));
}

// An open airlock door has no shape to aim at, as it is not there to see: one is shut to try it.
function shutAirlockDoor(blocks, at) {
  const { facing } = propsOf(blocks.get(key(...at)));
  must(`server setblock ${at.join(" ")} skyliners:airlock_door[facing=${facing},open=false]`);
}

const CHECKS = {
  "airstairs-reach-ground": { run: checkAirstairsReachTheGround, about: "a newly built airliner's stairs can be walked up from the ground" },
  "airstairs-leave-no-steps": { run: checkNoStepsLeftBehind, about: "taking off from a new build leaves no steps behind and takes no ground along" },
  "zeppelin-stairs": { run: checkZeppelinStairs, about: "a zeppelin's stairs can be walked up from the ground" },
  "rocket-stairs": { run: checkRocketStairs, about: "a rocket's stairs can be walked up from the ground" },
  "freighter-painting": { run: checkFreighterOnTheShips, about: "the freighter hangs whole and unhidden on each ship, and the wheel and breaking it work" },
  "fog-hiss": { run: checkFogHissStops, about: "a fog machine's hiss plays out, and stops when the machine goes however it goes" },
  "tardigrade-ice-cream": { run: checkTardigradeIceCream, about: "an ice cream: gulp, massive burp, the ship shakes, hyperspace, back" },
  "control-surfaces": { run: checkControlSurfacesDefault, about: "control surfaces are on by default" },
  "flight": { run: checkFlightAndLanding, about: "take-off, cruise, landing, and walking up the stairs after it", slow: true },
  "rocket-middle-click": { run: checkRocketMiddleClick, about: "the mouse wheel gives something for every kind of block in the rocket", slow: true },
};

// Whether the game answers; starts it if not and it was asked to. Whether it was started here.
function reachGame() {
  const answers = () => existsSync(LAUNCHER) && spawnSync(LAUNCHER, ["--json", "status"], { encoding: "utf8" }).status !== 2;
  if (answers()) {
    return false;
  }
  console.log("No dev client is running: starting one.");
  if (process.platform === "darwin") {
    spawn("caffeinate", ["-u", "-t", "3600"], { detached: true, stdio: "ignore" }).unref();
  }
  spawn("bash", ["./gradlew", "runClient", "--console=plain"], { cwd: ROOT, detached: true, stdio: "ignore" }).unref();
  for (let waited = 0; waited < GAME_UP_SECONDS; waited += 5) {
    spawnSync("sleep", ["5"]);
    if (existsSync(LAUNCHER) && spawnSync(LAUNCHER, ["--json", "--wait-ready", "5", "status"], { encoding: "utf8" }).status === 0) {
      return true;
    }
  }
  throw new GameGone("the dev client did not come up");
}

// A fresh flat world, the player standing on its ground; a world already open is saved and left first.
function freshWorld() {
  send("?leave", BUILD_SECONDS);
  send(`?delete world ${WORLD}`);
  must(`create world ${WORLD} flat peaceful rule:doDaylightCycle=false rule:doMobSpawning=false`, BUILD_SECONDS);
  must("wait 40t");
  ground = Math.floor(must("status").pos[1]);
}

function main() {
  const args = process.argv.slice(2);
  if (args.includes("--list")) {
    Object.entries(CHECKS).forEach(([name, check]) => console.log(`${name.padEnd(26)}${check.slow ? "slow  " : "      "}${check.about}`));
    return 0;
  }
  const named = args.filter((arg) => !arg.startsWith("--"));
  const unknown = named.filter((name) => !CHECKS[name]);
  if (unknown.length) {
    console.error(`No such check: ${unknown.join(", ")}. See --list.`);
    return 2;
  }
  const chosen = Object.keys(CHECKS).filter((name) => (named.length ? named.includes(name) : args.includes("--all") || !CHECKS[name].slow));
  let started = false;
  try {
    started = reachGame();
    freshWorld();
    for (const name of chosen) {
      console.log(`\n${name}: ${CHECKS[name].about}`);
      runCheck(name);
    }
    send("?camera reset");
    send("?leave", BUILD_SECONDS);
    if (failures.length === 0) {
      send(`?delete world ${WORLD}`);
    }
  } catch (error) {
    if (error instanceof GameGone) {
      console.error(error.message);
      return 2;
    }
    throw error;
  }
  if (started) {
    closeGame();
  }
  console.log(failures.length === 0 ? "\nEvery check held." : `\n${failures.length} failed, and ${WORLD} stays to be looked at:\n  ${failures.join("\n  ")}`);
  return failures.length === 0 ? 0 : 1;
}

// Closes a game started here: one that goes before it has replied has done what it was asked.
function closeGame() {
  try {
    send("quit", REPLY_SECONDS);
  } catch (error) {
    if (!(error instanceof GameGone)) {
      throw error;
    }
  }
}

// A check that throws has failed, but the others still run; only losing the game itself ends it all.
function runCheck(name) {
  try {
    CHECKS[name].run();
  } catch (error) {
    if (error instanceof GameGone) {
      throw error;
    }
    expect(false, `${name} could not finish`, error.message);
    send("?server gamemode creative @p");
  }
}

process.exit(main());
