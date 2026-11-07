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
// A painting: how many pieces each of the fleet's has, and how far out in front of it must be clear, in blocks.
const PAINTING_PIECES = 15;
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
// A crowd of bartenders, 256 of them, to load the limit on their whirrs: it stands in for whatever set off a racket of
// them in play, which was never found, as that rocket had only the one bartender. How many across, how high over them
// to fly and how far short of them to start, in blocks; how many flights across them and how long each is, in ticks;
// and how far apart one whirr should be from the next at least, in ticks, as the renderer has it.
const BARTENDERS_ACROSS = 16;
const BARTENDER_FLY_UP = 3;
const BARTENDER_RUN_UP = 6;
const BARTENDER_PASSES = 6;
const BARTENDER_PASS_TICKS = 40;
const BARTENDER_WHIRR_APART = 40;
// A rocket leaning over as she flies to a pin: how high and fast, in blocks and blocks a second; how far off the pin is
// each way, in blocks; how long she is given to lean, in ticks, and how far, as her pitch and roll added up, in
// degrees; where to stand from the bartender, in blocks, and for how long, in ticks; and how many whirrs that may bring
// in all.
// With the fault, and the limit on whirrs, it brought four or five at each of these places in that time.
const LEANING_HEIGHT = 200;
const LEANING_SPEED = 30;
const LEANING_PIN = 900;
const LEANING_WITHIN_TICKS = 2000;
const LEANING_LEAST = 6;
const LEANING_STANDS = [[2, 1], [4, 3]];
const LEANING_STILL_TICKS = 200;
const LEANING_WHIRRS = 2;
// A curtain to walk through: how many blocks across and high, wider than anyone parts; how far off to start, in blocks;
// how long the walk key is held to have the player's middle in it, and how long after they have gone it should be shut
// by, in ticks; and how long a ship is given to assemble and settle, in ticks.
const CURTAIN_ACROSS = 7;
const CURTAIN_UP = 3;
const CURTAIN_RUN_UP = 2;
const CURTAIN_WALK_TICKS = 9;
const CURTAIN_SHUTS_TICKS = 60;
const CURTAIN_ASSEMBLED_TICKS = 60;
// A rocket coming back down where she rose, the ground changed under her while she was away: how far out to stand from
// her control head, in blocks; how far above her resting place she is sent, in blocks; how far round her control head
// the ground is dug away, in blocks, short of her fins' feet, and the world's bottom it is dug down to; how far round
// the leaves reach and how high over the ground they hang, in blocks; how far off the height she is left standing at
// may be from where she came to rest, in blocks; and how long she is given to fly up and to be switched off again, in
// ticks.
const HOP_STAND_OFF = 60;
const HOP_HEIGHT = 70;
const HOLLOW_ACROSS = 4;
const WORLD_BOTTOM = -64;
const LEAVES_ACROSS = 40;
const LEAVES_UP = 8;
const HOP_REST_WITHIN = 1;
const HOP_UP_TICKS = 1500;
const HOP_DOWN_TICKS = 1800;
// The freighter's flight: how far above her control head she is sent, in blocks, past the 40 her autopilot sends her up
// at the least; how long she is given to get there and
// to come down by herself with nobody aboard, in ticks; how near the height set she must hover, in blocks, and how far
// she may lean, in degrees; how long she is watched hovering, in ticks; and how far off the player goes and how long
// for, in blocks and ticks, past where this client keeps her, to see her again as someone walking up to her would.
const FREIGHTER_CLIMB = 50;
const FREIGHTER_UP_TICKS = 900;
const FREIGHTER_DOWN_TICKS = 2400;
const FREIGHTER_HOLD_WITHIN = 1.5;
const FREIGHTER_LEVEL_WITHIN = 2;
const FREIGHTER_WATCH_TICKS = 200;
const FREIGHTER_AWAY = 400;
const FREIGHTER_AWAY_TICKS = 60;
// A freighter that has learnt far too much drag: how much, as her pilot keeps it; the speed she is set to cruise at
// and how fast she may go at most meanwhile, half as fast again as she unlearns it, in blocks a second; how long she is watched, in ticks; how near the
// speed set she must be by then, in blocks a second; and how much drag she may still have learnt then.
const DRAG_TOO_MUCH = 0.04;
const DRAG_CRUISE = 20;
const DRAG_FASTEST = 30;
const DRAG_WATCH_SAMPLES = 60;
const DRAG_SETTLED_WITHIN = 3;
const DRAG_UNLEARNT = 0.02;
// A freighter set to land at a pin with a block of stone on it: how far off the pin is from where she is built, in
// blocks; how far the stone reaches round it and how high it stands, in blocks, higher than anything under her
// hull; how long she is given to come down, in ticks; how far she may lean on her legs, in degrees; and how far from
// the pin she may come down, in blocks (PadSearch's REACH).
const PAD_PIN_AHEAD = 150;
const PAD_STONE_REACH = 5;
const PAD_STONE_HIGH = 9;
const PAD_DOWN_TICKS = 2400;
const PAD_LEVEL_WITHIN = 2;
const PAD_REACH = 64;
// A freighter set to land at a pin in a lake: how far round the pin the water reaches, past where she may come down,
// in blocks; how long she is given to say she finds no ground, in ticks; and how near the pin she must hold, in blocks.
const LAKE_REACH = 70;
const LAKE_TICKS = 1600;
const LAKE_HOLD_WITHIN = 8;
// A freighter's boarding ramp: how long it is given to come down by itself once she stands, in ticks; how far it
// falls, in blocks, which walking up it climbs, and how long the walk up from just past its foot takes, in ticks; how
// many blocks up from its foot someone stands on it to hold her take-off; and how long she is watched held, in ticks.
const RAMP_DOWN_TICKS = 300;
// How far into its way down her ramp is pressed again to turn it round, in ticks: halfway through its swing.
const RAMP_HALFWAY_TICKS = 40;
const RAMP_RISE = 7;
const RAMP_WALK_TICKS = 110;
const RAMP_STAND_UP = 3;
const RAMP_HELD_TICKS = 200;
// How fast blocks are ticked at random while a freighter's ramp's deck is watched, as the game rule has it, and for
// how long, in ticks: fast enough that every block of it looks whether it is a leftover several times.
const FAST_RANDOM_TICKS = 4096;
const RAMP_TICKED = 60;
// How long after her ramp folds away its call button is pressed again, and how long her take-off may be held before
// she calls it off, with a margin, in ticks.
const CALL_AGAIN_TICKS = 20;
const TAKEOFF_CALLED_OFF_TICKS = 700;
// A freighter's top turret: how long the walk up her core's ladder takes, in ticks; how far up and ahead of her cannon
// a creeper is hung to shoot at, in blocks; and how long attack is held, in ticks, several bolts' worth.
const LADDER_CLIMB_TICKS = 100;
const TURRET_TARGET_UP = 10;
const TURRET_TARGET_AHEAD = 10;
const TURRET_FIRE_TICKS = 30;
// How far a creeper's middle stands over its feet, and a seated player's eyes over where they are, in blocks, for the
// gunner to look at it: the cannon aims at what the gunner looks at.
const CREEPER_MIDDLE = 0.85;
const SEATED_EYES = 1.62;
// The laser crossbow: how long use is held to charge it, in ticks, longer than it takes, and how long to see that held
// past its click it does not fire by itself; how far in front of the
// player its targets stand, in blocks, and how far down the player looks at them, in degrees; a creeper's and a cow's
// full health, in half hearts; and how long it is left to cool after venting, in ticks.
const CROSSBOW_CHARGE_TICKS = 30;
const CROSSBOW_HELD_LONG_TICKS = 60;
const CROSSBOW_TARGET_AHEAD = 6;
const CROSSBOW_AIM_DOWN = 8;
const CREEPER_HEALTH = 20;
const COW_HEALTH = 10;
const CROSSBOW_COOLED_TICKS = 100;
// A freighter's lights: how many wall washers she has at least, and how bright her club is before its stage at least,
// as a block's light.
const FREIGHTER_LIGHTS = 24;
// How many bunks her crew quarters have at least, how many seats her lounge's booth, and how many any table of hers;
// how far each seat stands from its table at least, a block of leg room between, and at most, so as to be one of its
// seats, in blocks.
const QUARTERS_BUNKS = 3;
const LOUNGE_SEATED = 3;
const TABLE_SEATED = 2;
const LOUNGE_SEAT_OUT = 2;
const BOOTH_REACH = 3;
// Her small tardigrade: how many times the rocket's one's size it is, how long it may take to come to its hatch and
// munch moss fed through it, in ticks, turning round on the way, and the highest the big one munches, as a pitch.
const SMALL_TARDIGRADE = 0.25;
const SMALL_COMES_WITHIN = 600;
const BIG_MUNCH_HIGHEST = 0.9;
// How far from her small tardigrade's hatch the pad of moss beside it stands at most, in blocks.
const MOSS_PAD_NEAR = 3;
// How bright the middle of each of her sisters' paintings is at least, as a block's light.
const PAINTING_LIT = 8;
// Her Spookie: how far from it someone stands to be greeted, in blocks; how long it may take to answer, in ticks; how
// long after greeting someone it greets anyone again, in ticks; its health, which nothing takes; how long its rounds
// are watched, in ticks, and how far it walks in that time at least, in blocks, its feet on her deck all the while.
const SPOOKIE_NEAR = 3;
const SPOOKIE_ANSWERS = 60;
const SPOOKIE_GREETS_AGAIN = 1200;
const SPOOKIE_HEALTH = 60;
const SPOOKIE_WATCHED = 900;
const SPOOKIE_WALKS = 3;
// Her LED strips: how many rooms she has, each with its panel, and how many strips she has at least.
const FREIGHTER_ROOMS = 6;
const FREIGHTER_STRIPS = 300;
// How many different floors her six rooms have: each its own, her crew quarters keeping her carpet.
const OWN_FLOORS = 6;
// Her jump drive: how long it may take to spool up and fire or sputter once used, and to fire once thumped, in ticks.
const DRIVE_SPOOLS = 100;
const DRIVE_THUMPED = 40;
// Her breaker board: where its switch for engineering's lights is on its face, as the use command aims (west to east,
// up, north to south as drawn facing north): the bottom row's left, the viewer's left being its east side.
const ENGINEERING_SWITCH = "0.77,0.31,0.88";
// Her coolant valve: how many turns of its wheel bring it to full, where its wheel is to turn, as the use command aims,
// and how long it takes to let the pressure off once venting, at most, in ticks.
const VALVE_TURNS = 4;
const VALVE_WHEEL = "0.5,0.4,0.55";
const VALVE_VENTS = 120;
// A round floor window: how many blocks it has at least; how long its iris is given to open or shut, in ticks; how
// many loungers and palms her beach has at least; and how far past the foot of its shaft her belly's glass is looked
// for, in blocks.
const FLOOR_WINDOW_BLOCKS = 60;
// How far round her middle a freighter is looked over for unlit spots, in blocks, and how far astern of it her jump
// drive stands, to find her middle by.
const LIT_REACH = 40;
const JUMP_DRIVE_ASTERN = 13;
// How far before a passenger screen's face the player stands to press it, in blocks.
const SCREEN_STAND = 2;
const IRIS_TICKS = 40;
const BEACH_LOUNGERS = 2;
const BEACH_PALMS = 2;
const BELLY_BELOW = 3;
// Her rooms filled out: for each kind of furniture, how many of its blocks she has at least, room by room, a two-block
// piece counting twice and a baggage container four times.
const FURNISHINGS = {
  "cargo hold": { "skyliners:uld_container": 8, "skyliners:mail_sack": 1, "skyliners:parachute_rack": 1 },
  "entry hall": { "skyliners:crew_locker": 6, "skyliners:economy_seat": 8, "skyliners:check_in_desk": 1, "skyliners:atis_screen": 1 },
  lounge: { "skyliners:snack_machine": 4, "skyliners:shelf_unit": 3, "skyliners:sun_lounger": 2 },
  engineering: { "skyliners:engine_telegraph": 1, "minecraft:crafting_table": 1, "minecraft:grindstone": 1 },
  "crew quarters": { "skyliners:lab_instrument": 2, "skyliners:lounge_table": 2, "skyliners:business_seat": 12 },
  club: { "skyliners:cocktail_table": 2, "skyliners:neon_sign": 3, "skyliners:speaker": 4, "skyliners:fog_machine": 1 },
};
// How many pallet rack bays and crates her cargo hold has at least.
const HOLD_RACKS = 8;
const HOLD_CRATES = 2;
const CLUB_LIT = 5;
// How far out from her axis the middle of her ring corridor is, in blocks, and how bright it is there at least.
const CORRIDOR_MIDDLE = 4;
const CORRIDOR_LIT = 6;
// A freighter's club band: how far in front of its stage the player stands to hear it, how far off they go to leave it,
// in blocks, and how long its song is given to start and to fade away, in ticks.
const BAND_NEAR = 3;
// How many blocks of counter her club's bar has at least.
const CLUB_COUNTERS = 5;
const BAND_AWAY = 40;
const BAND_TICKS = 60;
// A freighter's deck robots: how many she is built with, how far in front of one the player walks up to, and how far
// off they start, in blocks; and how long its hello and its trill are given to be heard, in ticks.
const FREIGHTER_ROBOTS = 2;
const ROBOT_FRONT = 2;
const ROBOT_AWAY = 30;
const ROBOT_HEARD_TICKS = 80;
// A freighter's outside: how many of each piece of it she is built with at least, by block id, her fittings, her
// plates and her lights; the outside fittings put up by hand on a floor, a wall and under a ceiling; how far south of where she was laid down the stand they are
// put up on is, how far apart along it they go, and how high it is, in blocks; and how far above her control head she
// is sent to look for any of her outside left on the ground, in blocks.
const EXTERIOR_PIECES = [
  ["big_satellite_dish", 1], ["satellite_dish", 2], ["antenna_mast", 6], ["sensor_dome", 3], ["heat_sink", 12], ["exhaust_vent", 5],
  ["hull_machinery", 20], ["hull_floodlight", 6],
];
const FITTINGS_PUT_UP = [
  "satellite_dish", "big_satellite_dish", "antenna_mast", "sensor_dome", "heat_sink", "exhaust_vent", "hull_machinery", "hull_floodlight",
];
const STAND_AWAY = 24;
const STAND_APART = 3;
const STAND_HIGH = 5;
const EXTERIOR_CLIMB = 30;
const STEPS = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] };
// A rocket's self-destruct button: how long she is given to assemble and arm, and for a blast to be heard, in ticks;
// how long to leave it armed before pressing it again, longer than it waits to take a press, in ticks; how far above
// her resting place she is flying as it is pressed, in blocks; and how long whoever bailed out is given to float down,
// in ticks, and how close to the ground they count as down, in blocks.
const DESTRUCT_ARMED_TICKS = 400;
const DESTRUCT_SETTLE_TICKS = 30;
const DESTRUCT_HEIGHT = 120;
const DESTRUCT_FALL_TICKS = 4000;
const DESTRUCT_DOWN_WITHIN = 2;
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

// The sounds matching a pattern played in this world since a tick, after waiting so long first. The client's log keeps
// sounds from a world played before, whose ticks can run past this one's, so those later than now are left out.
function played(match, since, waitFirst = "") {
  const reply = must(`${waitFirst}sounds since=${since} match=${match} limit=1000`);
  return reply.rows.filter((sound) => sound.tick <= reply.tick);
}

// Waits for a sound to have been played since a tick; the first of them, or undefined.
function heard(match, since, within) {
  for (let waited = 0; waited <= within; waited += LOOK_EVERY) {
    const [row] = played(match, since, `wait ${LOOK_EVERY}t; `);
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
  const pieces = must(`find skyliners:space_poster[poster=freighter] 128 ${PAINTING_PIECES + 1}`).rows.map((row) => row.at_abs);
  expect(pieces.length === PAINTING_PIECES, `${what}: the freighter hangs whole`, `${pieces.length} pieces of ${PAINTING_PIECES}`);
  if (pieces.length !== PAINTING_PIECES) {
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
  const pieces = must(`find skyliners:space_poster[poster=freighter] 128 ${PAINTING_PIECES + 1}`).rows.map((row) => row.at_abs);
  if (pieces.length !== PAINTING_PIECES) {
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
  const left = must(`find skyliners:space_poster[poster=freighter] 128 ${PAINTING_PIECES + 1}`).rows.length;
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
  const voices = played("*tardigrade_burp*", since).map((row) => row.pitch);
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

// Flies back and forth over a crowd of bartenders, every one of which turns to look as the player comes into its reach.
function checkBartendersAreNoRacket() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  const [fromX, toX] = [site.x, site.x + BARTENDERS_ACROSS - 1];
  const [fromZ, toZ] = [site.z, site.z + BARTENDERS_ACROSS - 1];
  creative();
  must(`server tp @p ${fromX - BARTENDER_RUN_UP} ${ground + BARTENDER_FLY_UP} ${fromZ + BARTENDERS_ACROSS / 2}; turn -90 0; wait ${CHUNKS_TICKS}t`);
  must(`server fill ${fromX} ${ground} ${fromZ} ${toX} ${ground} ${toZ} skyliners:robot_bartender[facing=north]`);
  must(`server fill ${fromX} ${ground + 1} ${fromZ} ${toX} ${ground + 1} ${toZ} skyliners:robot_bartender[facing=north,half=upper]`);
  must("wait 40t");
  const since = now();
  for (let pass = 0; pass < BARTENDER_PASSES; pass++) {
    must(`turn ${pass % 2 ? 90 : -90} 0; move forward ${BARTENDER_PASS_TICKS}t`);
  }
  const whirrs = played("*bartender_servo*", since).length;
  const within = now() - since;
  const allowed = Math.ceil(within / BARTENDER_WHIRR_APART) + 1;
  expect(whirrs >= 1, "bartenders: a crowd of them still whirrs", `${whirrs} whirrs`);
  expect(whirrs <= allowed, "bartenders: a crowd of them is no racket", `${whirrs} whirrs in ${within} ticks, at most ${allowed} wanted`);
  must(`server fill ${fromX} ${ground} ${fromZ} ${toX} ${ground + 1} ${toZ} air`);
}

// Stands still at the bar of a rocket leaning over as she flies to a pin. A bartender working out where a player is by
// inverting her matrix had them jumping ten blocks from tick to tick once she leaned, out of its reach and back, each
// return someone new to whirr at. She has to get there by flying: one flying straight up, or turned round by a command,
// never showed it.
function checkBartenderOnALeaningRocket() {
  const site = layDown("rocket");
  const [bartender] = must("find skyliners:robot_bartender 128 1").rows;
  const [x, y, z] = bartender.at_abs;
  must(`fly off; server tp @p ${x + 0.5} ${y} ${z + 3.5}; wait 20t`);
  must(`skyliners:autopilot fly altitude=${ground + LEANING_HEIGHT} speed=${LEANING_SPEED} pin=${site.x + LEANING_PIN},${site.z + LEANING_PIN}`, BUILD_SECONDS);
  const rocket = findRocketFlying();
  const leaning = waitForLean(rocket);
  expect(leaning, "bartender: the rocket leans over as she flies to the pin");
  if (!leaning) {
    return;
  }
  const { at } = must(`sable:find ${rocket} skyliners:robot_bartender 1 side=server`).rows[0];
  let whirrs = 0;
  for (const [back, side] of LEANING_STANDS) {
    const since = must(`sable:tp ${at[0] - back},${at[1]},${at[2] + side}; wait 40t; messages limit=1`).tick;
    whirrs += played("*bartender_servo*", since, `wait ${LEANING_STILL_TICKS}t; `).length;
  }
  expect(whirrs <= LEANING_WHIRRS, "bartender: standing still at the bar of a leaning rocket, it does not keep whirring", `${whirrs} whirrs, at most ${LEANING_WHIRRS} wanted`);
  must(`server tp @p 0 ${ground} 0`);
  creative();
}

// The rocket that has just been assembled, the one sub-level with a bartender aboard; other ships may be flying yet.
// The sub-level of the ship flying near that carries a block only she does: her id, once she has assembled.
function findShipFlying(marker) {
  for (let waited = 0; waited <= BUILD_SECONDS * 20; waited += LOOK_EVERY) {
    const ids = must(`wait ${LOOK_EVERY}t; sable:list`).rows.map((row) => row.id);
    const ship = ids.find((id) => must(`sable:find ${id} ${marker} 1 side=server`).rows.length > 0);
    if (ship) {
      return ship;
    }
  }
  throw new Error("she never assembled");
}

const findRocketFlying = () => findShipFlying("skyliners:robot_bartender");

function waitForLean(rocket) {
  for (let waited = 0; waited <= LEANING_WITHIN_TICKS; waited += LOOK_EVERY) {
    const { pitch, roll } = must(`wait ${LOOK_EVERY}t; sable:info ${rocket} brief side=server`);
    if (Math.abs(pitch) + Math.abs(roll) >= LEANING_LEAST) {
      return true;
    }
  }
  return false;
}

// What a rocket's autopilot is doing; undefined while there is no report, as for a moment while she assembles.
function rocketReport() {
  const [reply] = send("skyliners:rocket");
  return reply.ok === false ? undefined : reply;
}

function freighterReport() {
  const [reply] = send("skyliners:freighter");
  return reply.ok === false ? undefined : reply;
}

// Waits for a ship's report to pass a test, looking once a second; the report, or undefined if it never did.
function waitForReport(read, test, within) {
  for (let waited = 0; waited <= within; waited += LOOK_EVERY) {
    must(`wait ${LOOK_EVERY}t`);
    const report = read();
    if (report && test(report)) {
      return report;
    }
  }
  return undefined;
}

const waitForRocket = (test, within) => waitForReport(rocketReport, test, within);

// Sends a rocket up and back down where she rose, changes the ground under her once she is clear of it, and says
// whether she switches herself off where she comes to rest. Standing on her fins with the ground lower under her
// middle, as over a launch pad's flame trench, or held up by what the ground reading cannot see, she read as still in
// the air: her engines burnt on and neither she nor her pilot could switch them off.
function hopChanging(head, what, change) {
  must(`skyliners:autopilot fly altitude=${head[1] + HOP_HEIGHT}`, BUILD_SECONDS);
  const clear = waitForRocket((report) => report.phase !== "off" && report.phase !== "takeoff", HOP_UP_TICKS);
  expect(clear, `rocket: she flies up, to come back ${what}`);
  if (!clear) {
    return undefined;
  }
  must(change);
  const off = waitForRocket((report) => report.phase === "off", HOP_DOWN_TICKS);
  const last = off ?? rocketReport();
  expect(off, `rocket: coming down ${what}, she switches herself off`, `phase ${last?.phase}, at height ${last?.altitude}, climbing at ${last?.climb}`);
  if (off) {
    expect(off.thrust === 0, `rocket: coming down ${what}, her engines are let go`, `thrust ${off.thrust}`);
  }
  return off;
}

// Flies a rocket down over a hollow under her control head and then onto a sheet of leaves, which the ground reading
// looks through, seeing each time that she switches off where she comes to rest.
function checkRocketSwitchesOffWhereSheRests() {
  const site = layDown("rocket");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`server gamerule doFireTick false; server tp @p ${head[0] + HOP_STAND_OFF} ${ground + 2} ${head[2]}`);
  const [x, , z] = head;
  const hollow = `server fill ${x - HOLLOW_ACROSS} ${WORLD_BOTTOM} ${z - HOLLOW_ACROSS} ${x + HOLLOW_ACROSS} ${ground - 1} ${z + HOLLOW_ACROSS} air`;
  const overHollow = hopChanging(head, "with the ground dug away under her middle", hollow);
  if (overHollow) {
    expect(Math.abs(overHollow.altitude - (head[1] + 0.5)) <= HOP_REST_WITHIN, "rocket: she stands on her fins beside the hollow, not down in it", `control head at ${overHollow.altitude}`);
  }
  const leaves = `server fill ${x - LEAVES_ACROSS} ${ground + LEAVES_UP} ${z - LEAVES_ACROSS} ${x + LEAVES_ACROSS} ${ground + LEAVES_UP} ${z + LEAVES_ACROSS} oak_leaves[persistent=true]`;
  const onLeaves = hopChanging(head, "onto a sheet of leaves", leaves);
  if (onLeaves) {
    expect(onLeaves.altitude > head[1] + LEAVES_UP - HOP_REST_WITHIN, "rocket: she rests on the leaves, not through them", `control head at ${onLeaves.altitude}`);
  }
  must(`server gamerule doFireTick true; server tp @p ${site.x} ${ground} ${site.z}`);
}

// Lifts the cover of a self-destruct button and arms it, at the place given; the tick it was armed at.
function armButton(at) {
  emptyHands();
  must(`use ${at}`);
  const since = now();
  must(`wait 10t; use ${at}`);
  return since;
}

// Whether a sub-level is still held by the server.
const stillThere = (id) => must("sable:list 2048 side=server").rows.some((row) => row.id === id);

// Whether the player wears the parachute everyone aboard is given.
function wearsParachute() {
  const [reply] = send("server clear @p skyliners:parachute 0");
  return reply.ok !== false;
}

// A rocket standing on the ground: the button is there as she is built, arming it puts her together, and a third press
// blows her up.
function checkRocketSelfDestructOnTheGround() {
  layDown("rocket");
  const [found] = must("find skyliners:self_destruct_button 128 1").rows;
  expect(found, "rocket: she is built with a self-destruct button");
  if (!found) {
    return;
  }
  const [x, y, z] = found.at_abs;
  must(`fly off; server tp @p ${x + 1.5} ${y} ${z + 0.5}; wait 20t`);
  const since = armButton(`${x},${y},${z}`);
  expect(heard("*self_destruct_siren*", since, DESTRUCT_ARMED_TICKS), "rocket: standing as blocks, arming the button puts her together and sounds the alarm");
  const rocket = findRocketFlying();
  const { at } = must(`sable:find ${rocket} skyliners:self_destruct_button 1 side=server`).rows[0];
  const pressed = must(`wait ${DESTRUCT_SETTLE_TICKS}t; messages limit=1`).tick;
  must(`use ${at.join(",")}`);
  expect(heard("*self_destruct_blast*", pressed, DESTRUCT_ARMED_TICKS), "rocket: pressed again, she blows up");
  expect(!stillThere(rocket), "rocket: blown up on the ground, she is gone");
  expect(wearsParachute(), "rocket: whoever stood aboard is given a parachute");
  must(`server tp @p 0 ${ground} 0`);
  creative();
}

// The freighter, watched from the ground: she lifts straight off her legs and climbs to the height set, hovers there
// level, roars from outside, and is not heard spooling up as she merely holds her spot, nor as someone comes back to
// her; then, nobody aboard, she comes down by herself and switches off on her legs. Hovering, her mains flickered on
// and off holding her spot, and their whine spooled up every few seconds.
function checkFreighterFlight() {
  const site = layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const height = head[1] + FREIGHTER_CLIMB;
  must(`skyliners:autopilot fly altitude=${height} speed=0`, BUILD_SECONDS);
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise" && Math.abs(report.altitude - height) < FREIGHTER_HOLD_WITHIN,
    FREIGHTER_UP_TICKS);
  expect(up, "freighter: she lifts off her legs and climbs to the height set", up ? `at ${up.altitude}` : "");
  if (!up) {
    return;
  }
  const since = now();
  must(`wait ${FREIGHTER_WATCH_TICKS}t`);
  const hover = freighterReport();
  expect(Math.abs(hover.altitude - height) < FREIGHTER_HOLD_WITHIN, "freighter: she hovers at it", `at ${hover.altitude}, set ${height}`);
  const lean = Math.max(Math.abs(hover.tilt_front), Math.abs(hover.tilt_right));
  expect(lean < FREIGHTER_LEVEL_WITHIN, "freighter: she hovers level", `leaning ${lean} degrees`);
  expect(playing("skyliners:freighter_engine_roar") > 0, "freighter: her engines roar, heard from the ground");
  must(`server tp @p ${site.x + FREIGHTER_AWAY} ${ground} ${site.z}; wait ${FREIGHTER_AWAY_TICKS}t`);
  const back = now();
  must(`server tp @p ${site.x} ${ground} ${site.z}; wait ${FREIGHTER_AWAY_TICKS}t`);
  const lit = ["skyliners:freighter_spool", "skyliners:freighter_ignition"].filter((sound) => heard(sound, back, 0));
  expect(lit.length === 0, "freighter: walking back up to her hovering, no engine is heard lighting", lit.join(", "));
  expect(!heard("skyliners:freighter_spool", since, 0), "freighter: hovering all that while, her mains are never heard spooling up");
  expect(playing("skyliners:freighter_engine_roar") > 0, "freighter: walking back up to her, her roar is heard again");
  const down = waitForReport(freighterReport, (report) => report.phase === "off", FREIGHTER_DOWN_TICKS);
  expect(down, "freighter: nobody aboard, she comes down by herself and switches off", down ? `at ${down.altitude}` : "");
  if (down) {
    expect(Math.abs(down.altitude - head[1]) < FREIGHTER_HOLD_WITHIN, "freighter: she stands on her legs again",
      `control head at ${down.altitude}, built at ${head[1]}`);
  }
}

// A freighter's boarding ramp: laid down, it comes down by itself and is walked up from the ground with the walk key
// alone, as a player boards her, its deck staying laid however often its blocks look whether they are leftovers; her
// call button on her leg folds it away and brings it down again; set to fly with someone standing on it, she waits on
// her legs, the ramp chiming for them to step off, and calls her take-off off after a while, the ramp left down; set
// to fly again with nobody on it, it folds away and she lifts off.
function checkFreighterRamp() {
  layDown("freighter");
  const [panel] = must("find skyliners:ramp_panel 128 1").rows;
  expect(panel, "freighter: she is built with her ramp's panel");
  if (!panel) {
    return;
  }
  const lamp = () => must(`block ${panel.at_abs.join(",")} side=server`).props?.status;
  const lampTurns = (status, within) => waitForReport(() => ({ status: lamp() }), (read) => read.status === status, within);
  expect(lampTurns("down", RAMP_DOWN_TICKS), "freighter: laid down, her ramp comes down by itself", `lamp ${lamp()}`);
  const deck = must("find skyliners:ramp_deck 128 200 side=server").rows.map((row) => row.at_abs);
  if (deck.length === 0) {
    return;
  }
  const lowest = Math.min(...deck.map(([, y]) => y));
  const facing = must(`block ${deck.find(([, y]) => y === lowest).join(",")} side=server`).props?.facing ?? "north";
  const [dx, dz, yaw] = CLIMBS[facing];
  const outermost = Math.min(...deck.map(([x, , z]) => x * dx + z * dz));
  const foot = deck.filter(([x, y, z]) => y === lowest && x * dx + z * dz === outermost).sort((a, b) => a[0] - b[0] || a[2] - b[2]);
  const [footX, , footZ] = foot[Math.floor(foot.length / 2)];
  survival();
  must(`server tp @p ${footX + 0.5 - dx} ${ground} ${footZ + 0.5 - dz}; turn ${yaw} 0; wait 25t`);
  const there = must(`move forward ${RAMP_WALK_TICKS}t; status`);
  creative();
  const climbed = round(there.pos[1] - ground);
  expect(climbed >= RAMP_RISE, "freighter: her ramp is walked up from the ground with the walk key alone", `climbed ${climbed}, to ${there.pos.map(round).join(",")}`);
  must(`server gamerule randomTickSpeed ${FAST_RANDOM_TICKS}; wait ${RAMP_TICKED}t; server gamerule randomTickSpeed 3`);
  const stays = must("find skyliners:ramp_deck 128 200 side=server").rows.length;
  expect(stays === deck.length, "freighter: her ramp's deck stays laid, however often its blocks look whether they are leftovers",
    `${stays} of ${deck.length} blocks`);
  callRampFromTheGround(lamp, lampTurns);
  holdFreighterOnHerRamp(deck, footX + dx * 2 * RAMP_STAND_UP, lowest + RAMP_STAND_UP, footZ + dz * 2 * RAMP_STAND_UP, lamp);
}

// Presses a freighter's ramp call button on her leg from the ground: her ramp folds away, and comes down again.
function callRampFromTheGround(lamp, lampTurns) {
  const [button] = must("find skyliners:ramp_button 128 1").rows;
  expect(button, "freighter: she is built with her ramp's call button on her leg");
  if (!button) {
    return;
  }
  const [bx, , bz] = button.at_abs;
  const [fx, fz] = STEPS[must(`block ${button.at_abs.join(",")}`).props?.facing ?? "north"];
  must(`server tp @p ${bx + 0.5 + fx * 2} ${ground} ${bz + 0.5 + fz * 2}; wait 20t; use ${button.at_abs.join(",")}`);
  expect(lampTurns("ready", RAMP_DOWN_TICKS), "freighter: her call button folds her ramp away", `lamp ${lamp()}`);
  must(`wait ${CALL_AGAIN_TICKS}t; use ${button.at_abs.join(",")}; wait ${RAMP_HALFWAY_TICKS}t; use ${button.at_abs.join(",")}`);
  expect(lampTurns("ready", RAMP_DOWN_TICKS), "freighter: pressed again halfway down, her ramp turns round and folds away again", `lamp ${lamp()}`);
  must(`wait ${CALL_AGAIN_TICKS}t; use ${button.at_abs.join(",")}`);
  expect(lampTurns("down", RAMP_DOWN_TICKS), "freighter: pressed again, it brings her ramp back down", `lamp ${lamp()}`);
}

// Sets a freighter to fly with the player standing on her ramp at a block of its deck: she waits on her legs, chiming,
// and calls her take-off off after a while, her ramp left down; set to fly again from aboard, she lifts off.
function holdFreighterOnHerRamp(deck, x, y, z, lamp) {
  if (!deck.some(([atX, atY, atZ]) => atX === x && atY === y && atZ === z)) {
    return;
  }
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`server tp @p ${x + 0.5} ${y + 1} ${z + 0.5}; wait 20t`);
  const since = now();
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0; wait ${RAMP_HELD_TICKS}t`, BUILD_SECONDS);
  const held = freighterReport();
  expect(held?.phase === "takeoff" && held.lift === 0, "freighter: set to fly with someone on her ramp, she waits on her legs",
    `${held?.phase}, lift ${held?.lift}`);
  expect(heard("block.note_block.bell", since, 0), "freighter: her ramp chimes for them to step off");
  const freighter = findShipFlying("skyliners:engine_grille");
  const [{ at: panelAboard }] = must(`sable:find ${freighter} skyliners:ramp_panel 1 side=server`).rows;
  const lampAboard = () => must(`block ${panelAboard.join(",")} side=server`).props?.status;
  const off = waitForReport(freighterReport, (report) => report.phase === "off", TAKEOFF_CALLED_OFF_TICKS);
  expect(off && lampAboard() === "down", "freighter: held a long while, she calls her take-off off, her ramp left down",
    `${freighterReport()?.phase}, lamp ${lampAboard()}`);
  must(`sable:tp ${freighter} skyliners:autopilot; wait 20t; skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`);
  const up = waitForReport(freighterReport, (report) => report.phase === "climb" || report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(up, "freighter: set to fly again with nobody on it, her ramp folds away and she lifts off", up ? "" : `still ${freighterReport()?.phase}`);
  must("skyliners:autopilot off");
}

// A freighter's top turret: the ladder up her core is climbed from her deck with the walk key alone to the gunner's
// seat under her dome, where her computer says the turret is ready; holding attack fires her laser cannon where the
// gunner looks, striking a creeper in the sky, and breaks nothing, the dome's glass over the gunner whole after.
function checkFreighterTurret() {
  layDown("freighter");
  const [cannon] = must("find skyliners:laser_cannon 128 1").rows;
  expect(cannon, "freighter: she is built with a laser cannon on her top dome");
  if (!cannon) {
    return;
  }
  const [x, y, z] = cannon.at_abs;
  const seat = must("find skyliners:pilot_seat 128 20").rows.map((row) => row.at_abs).filter(([seatX, , seatZ]) => seatX === x && seatZ === z)
    .sort((a, b) => a[1] - b[1])[0];
  const ladder = must("find skyliners:crew_ladder 128 20").rows.map((row) => row.at_abs).sort((a, b) => a[1] - b[1])[0];
  if (!seat || !ladder) {
    expect(false, "freighter: she has a gunner's seat under her dome and a ladder up to it");
    return;
  }
  const [climbX, climbZ, yaw] = CLIMBS[must(`block ${ladder.join(",")}`).props?.facing === "north" ? "south" : "north"];
  survival();
  must(`server tp @p ${ladder[0] + 0.5} ${ladder[1]} ${ladder[2] + 0.5 - climbZ * 0.2}; turn ${yaw} 0; wait 20t`);
  const there = must(`move forward ${LADDER_CLIMB_TICKS}t; status`);
  creative();
  expect(there.pos[1] >= seat[1], "freighter: her core's ladder is climbed from her deck to her gunner's seat", `reached ${round(there.pos[1])}, seat at ${seat[1]}`);
  const sat = now();
  must(`use ${seat.join(",")}; wait 20t`);
  expect(heard("skyliners:freighter_computer_turret_ready", sat, 0), "freighter: sat at her cannon, her computer says the turret is ready");
  const glass = must(`block ${x},${y - 1},${z}`).id;
  must(`server difficulty easy; server summon creeper ${x + 0.5} ${y + TURRET_TARGET_UP} ${z + 0.5 - TURRET_TARGET_AHEAD} {NoAI:1b,PersistenceRequired:1b}`);
  const { pos: [eyeX, feetY, eyeZ] } = must("status");
  const [toX, toY, toZ] = [x + 0.5 - eyeX, y + TURRET_TARGET_UP + CREEPER_MIDDLE - feetY - SEATED_EYES, z + 0.5 - TURRET_TARGET_AHEAD - eyeZ];
  const toward = Math.round(Math.atan2(-toX, toZ) * 180 / Math.PI);
  const up = Math.round(Math.atan2(toY, Math.hypot(toX, toZ)) * 180 / Math.PI);
  must(`turn ${toward} ${-up}; wait 20t; key down key.attack; wait ${TURRET_FIRE_TICKS}t; key up key.attack; wait 5t`);
  const [reply] = send(`server data get entity @e[type=creeper,limit=1,sort=nearest] Health`);
  const health = reply.ok === false ? 0 : Number(/(-?[0-9.]+)f/.exec(JSON.stringify(reply))?.[1] ?? CREEPER_HEALTH);
  must("server kill @e[type=creeper]; server difficulty peaceful; key left_shift 5t");
  expect(health < CREEPER_HEALTH, "freighter: holding attack, her cannon strikes a creeper in the sky where the gunner looks", `health ${health} of ${CREEPER_HEALTH}`);
  expect(must(`block ${x},${y - 1},${z}`).id === glass, "freighter: firing, the gunner breaks nothing, the dome's glass whole");
}

// The laser crossbow: charged by holding use, it fires an amber bolt that strikes a creeper hard and leaves a cow
// unharmed; three quick shots and it vents, and will not fire again until it has cooled. The creeper is shot with the
// world on easy, as a peaceful world takes hostile mobs away at once.
function checkLaserCrossbow() {
  const x = nextSite++ * SITE_APART;
  creative();
  must(`server tp @p ${x} ${ground} 0; turn 0 0; wait 40t; server item replace entity @p weapon.mainhand with skyliners:laser_crossbow`);
  const held = now();
  must(`turn 0 0; use hold=${CROSSBOW_HELD_LONG_TICKS}t; wait 5t`);
  const charged = /laser_crossbow_charged\W*1b/.test(JSON.stringify(send("server data get entity @p SelectedItem")[0]));
  expect(charged && !heard("skyliners:laser_crossbow_shot", held, 0), "laser crossbow: held long past its click, it charges and does not fire by itself",
    charged ? "charged" : "not charged");
  must("use; wait 10t");
  must("server difficulty easy");
  const struck = shootAt("creeper", x);
  must("server difficulty peaceful");
  expect(struck < CREEPER_HEALTH, "laser crossbow: charged and fired, its bolt strikes a creeper", `health ${struck} of ${CREEPER_HEALTH}`);
  const spared = shootAt("cow", x);
  expect(spared === COW_HEALTH, "laser crossbow: its bolt never hurts a cow", `health ${spared} of ${COW_HEALTH}`);
  const since = now();
  must(`use hold=${CROSSBOW_CHARGE_TICKS}t; wait 5t; use; wait 10t`);
  expect(!heard("skyliners:laser_crossbow_shot", since, 0), "laser crossbow: after three quick shots it vents, and does not fire");
  must(`wait ${CROSSBOW_COOLED_TICKS}t`);
  const cooled = now();
  must(`use hold=${CROSSBOW_CHARGE_TICKS}t; wait 5t; use; wait 10t`);
  expect(heard("skyliners:laser_crossbow_shot", cooled, 0), "laser crossbow: once cooled, it charges and fires again");
}

// Summons a mob that stands still in front of the player, charges the laser crossbow and fires it at the mob: its
// health after, the mob then taken away.
function shootAt(mob, x) {
  must(`server summon ${mob} ${x + 0.5} ${ground} ${CROSSBOW_TARGET_AHEAD + 0.5} {NoAI:1b,PersistenceRequired:1b}; wait 10t`);
  must(`turn 0 ${CROSSBOW_AIM_DOWN}; use hold=${CROSSBOW_CHARGE_TICKS}t; wait 5t; use; wait 10t`);
  const [reply] = send(`server data get entity @e[type=${mob},limit=1,sort=nearest] Health`);
  must(`server kill @e[type=${mob}]; wait 5t`);
  const health = Number(/(-?[0-9.]+)f/.exec(JSON.stringify(reply))?.[1]);
  return Number.isNaN(health) ? 0 : health;
}

// A freighter that has learnt far too much drag, as one did once she had hunted round her height: set to cruise at 20
// blocks a second, her mains pushed her on to almost three times that, and as her drag was learnt only near the speed
// set, nothing could unlearn it. Ridden along, so she stays loaded flying away: she must stay near the speed set and
// unlearn it.
function checkFreighterUnlearnsDrag() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const height = head[1] + FREIGHTER_CLIMB;
  must(`skyliners:autopilot fly altitude=${height} speed=0`, BUILD_SECONDS);
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(up, "freighter: she lifts off to cruise");
  if (!up) {
    return;
  }
  const freighter = findShipFlying("skyliners:engine_grille");
  const { at } = must(`sable:find ${freighter} skyliners:autopilot 1 side=server`).rows[0];
  must(`sable:tp ${at.join(",")}; wait 20t`);
  must(`server data modify block ${at.join(" ")} Freighter.Drag set value ${DRAG_TOO_MUCH}d`);
  must(`skyliners:autopilot fly altitude=${height} speed=${DRAG_CRUISE} heading=${up.heading}`);
  const rows = must(`sample skyliners:freighter every 20t x${DRAG_WATCH_SAMPLES}`, BUILD_SECONDS).rows;
  const fastest = Math.max(...rows.map((row) => row.ahead));
  const last = rows[rows.length - 1];
  expect(fastest <= DRAG_FASTEST, "freighter: having learnt too much drag, she is not pushed on far past the speed set", `fastest ${fastest}`);
  expect(Math.abs(last.ahead - DRAG_CRUISE) <= DRAG_SETTLED_WITHIN, "freighter: she settles back near the speed set", `at ${last.ahead}`);
  expect(last.drag <= DRAG_UNLEARNT, "freighter: she unlearns the drag", `drag ${last.drag}`);
  must("skyliners:autopilot land");
}

// A freighter set to land at a pin with a block of stone standing on it, taller than anything under her hull: she
// comes down beside it, level on her legs and near the pin, rather than onto it, leaning on it with her legs in the
// air, as she would landing on the pin itself. Sent back to the same pin once a block has been built where she stood,
// she looks again rather than come down on her old spot.
function checkFreighterPad() {
  const site = layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const [x, z] = [site.x, site.z - PAD_PIN_AHEAD];
  must(`server fill ${x - PAD_STONE_REACH} ${ground} ${z - PAD_STONE_REACH} ${x + PAD_STONE_REACH} ${ground + PAD_STONE_HIGH - 1} ${z + PAD_STONE_REACH} stone`);
  if (!rideFreighterUp(head)) {
    return;
  }
  const first = landFreighterAt(head, x, z, "beside the stone");
  if (!first) {
    return;
  }
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`);
  waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  const [px, pz] = String(first.pad).split(",").map(Number);
  must(`server fill ${px - PAD_STONE_REACH} ${ground} ${pz - PAD_STONE_REACH} ${px + PAD_STONE_REACH} ${ground + PAD_STONE_HIGH - 1} ${pz + PAD_STONE_REACH} stone`);
  const second = landFreighterAt(head, x, z, "beside the stone built where she stood");
  if (second) {
    expect(second.pad !== first.pad, "freighter: sent back to the same pin, she looks again", `first ${first.pad}, then ${second.pad}`);
  }
}

// A freighter set to land at a pin in a lake, with no dry ground within reach of it: she holds over the pin and says
// so, with the low note, rather than come down in the water.
function checkFreighterLake() {
  const site = layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const [x, z] = [site.x, site.z - PAD_PIN_AHEAD];
  must(`server fill ${x - LAKE_REACH} ${ground - 1} ${z - LAKE_REACH} ${x + LAKE_REACH} ${ground - 1} ${z + LAKE_REACH} water`);
  if (!rideFreighterUp(head)) {
    return;
  }
  const since = now();
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=${DRAG_CRUISE} pin=${x},${z} land=true`);
  expect(waitForMessage("*holding*", since, LAKE_TICKS), "freighter: set to land in a lake, she says she finds no ground");
  expect(heard("minecraft:block.note_block.pling", since, 0), "freighter: and the low note says it to those who cannot read");
  const near = (report) => Math.hypot(...String(report.middle).split(",").map(Number).map((at, i) => at - [x, z][i]));
  const holding = waitForReport(freighterReport, (report) => near(report) <= LAKE_HOLD_WITHIN, LAKE_TICKS) ?? freighterReport();
  must("wait 200t");
  const still = freighterReport();
  expect(still.phase === "cruise" && near(still) <= LAKE_HOLD_WITHIN, "freighter: she holds over the pin, not coming down",
    `${still.phase}, ${near(still).toFixed(1)} blocks from it, earlier ${near(holding).toFixed(1)}`);
}

// A freighter's rooms: her crew quarters have their bunks, reading lights on, her lounge its booth, a table among
// seats with leg room before it, and its galley, and her cargo hold its racks of cargo and crates.
function checkFreighterRooms() {
  ship("freighter");
  const bunks = must("find skyliners:bunk 128 20").rows.length;
  expect(bunks >= QUARTERS_BUNKS * 2, "freighter: her crew quarters have their bunks", `${bunks / 2} bunks`);
  const table = must("find skyliners:lounge_table 128 2").rows.length;
  const seats = must("find skyliners:business_seat 128 20").rows.length / 2;
  expect(table > 0 && seats >= LOUNGE_SEATED, "freighter: her lounge has its booth, a table among seats", `${table} table, ${seats} seats`);
  checkLegRoom();
  const racks = must("find skyliners:pallet_rack 128 40").rows.length;
  const crates = must("find skyliners:cargo_crate 128 10").rows.length;
  expect(racks >= HOLD_RACKS && crates >= HOLD_CRATES, "freighter: her cargo hold has its racks of cargo and crates", `${racks} rack bays, ${crates} crates`);
  const galley = ["skyliners:galley_fridge", "skyliners:galley_oven", "skyliners:coffee_machine"].filter((block) => must(`find ${block} 128 2`).rows.length === 0);
  expect(galley.length === 0, "freighter: her lounge has its galley, a fridge, an oven and a coffee machine", galley.length ? `no ${galley.join(", ")}` : "");
  checkCargoScanner();
}

// A freighter's cargo hold's parcel scanner, a crate waiting on its belt: used, it sweeps the crate with its lasers.
function checkCargoScanner() {
  const [scanner] = must("find skyliners:parcel_scanner 128 1").rows;
  const crate = scanner && must(`block ${scanner.at_abs[0]},${scanner.at_abs[1] + 1},${scanner.at_abs[2]}`).id === "skyliners:cargo_crate";
  expect(Boolean(crate), "freighter: her cargo hold has the parcel scanner, a crate waiting on its belt");
  if (!crate) {
    return;
  }
  const at = scanner.at_abs.join(",");
  const [dx, dz] = STEPS[must(`block ${at}`).props.facing];
  must(`server tp @p ${scanner.at_abs[0] + 0.5 + dx * 2} ${scanner.at_abs[1]} ${scanner.at_abs[2] + 0.5 + dz * 2}; wait 5t`);
  const used = now();
  must(`use ${at}`);
  expect(Boolean(heard("*scanner_sweep*", used, LOOK_EVERY)), "freighter: used, the parcel scanner sweeps its crate");
}

// A freighter's lights: she is built with wall washers round her corridor and rooms, her club is lit before its stage
// and her ring corridor all round, so nobody walks her deck in the dark.
function checkFreighterLights() {
  ship("freighter");
  const lights = must("find skyliners:wall_light 128 100").rows.length;
  expect(lights >= FREIGHTER_LIGHTS, "freighter: she is built with wall washers round her corridor and rooms", `${lights} of them`);
  const [stand] = must("find skyliners:bandstand 128 1").rows;
  if (!stand) {
    return;
  }
  const [x, y, z] = stand.at_abs;
  const [dx, dz] = STEPS[must(`block ${x},${y},${z}`).props?.facing ?? "north"];
  const lit = must(`block ${x + dx * BAND_NEAR},${y + 1},${z + dz * BAND_NEAR} light`).block_light ?? 0;
  expect(lit >= CLUB_LIT, "freighter: her club is lit before its stage", `block light ${lit}`);
  const [{ at_abs: [axisX, , axisZ] }] = must("find skyliners:laser_cannon 128 1").rows;
  const corridor = [[1, 0], [-1, 0], [0, 1], [0, -1]].map(([ax, az]) =>
    must(`block ${axisX + ax * CORRIDOR_MIDDLE},${y + 1},${axisZ + az * CORRIDOR_MIDDLE} light`).block_light ?? 0);
  expect(Math.min(...corridor) >= CORRIDOR_LIT, "freighter: her ring corridor is lit all round", `block light ${corridor.join(", ")}`);
}

// A freighter's club: a bar with the robot bartender behind its counter, and a band; standing before its bandstand,
// her song plays, and gone well away from it, it falls silent.
function checkFreighterBand() {
  ship("freighter");
  const counters = must("find skyliners:bar_counter 128 20").rows.length;
  const bartender = must("find skyliners:robot_bartender 128 2").rows.length > 0;
  expect(counters >= CLUB_COUNTERS && bartender, "freighter: her club has a bar, the robot bartender behind its counter",
    `${counters} blocks of counter, ${bartender ? "a" : "no"} bartender`);
  const [stand] = must("find skyliners:bandstand 128 1").rows;
  expect(stand, "freighter: she is built with a bandstand in her club");
  if (!stand) {
    return;
  }
  const [x, y, z] = stand.at_abs;
  const [dx, dz] = STEPS[must(`block ${x},${y},${z}`).props?.facing ?? "north"];
  must(`server tp @p ${x + 0.5 + dx * BAND_NEAR} ${y} ${z + 0.5 + dz * BAND_NEAR}; wait ${BAND_TICKS}t`);
  expect(playing("skyliners:club_band") > 0, "freighter: standing before her bandstand, her band's song plays");
  must(`server tp @p ${x + 0.5 + dx * BAND_AWAY} ${y + BAND_AWAY} ${z + 0.5 + dz * BAND_AWAY}; wait ${BAND_TICKS}t`);
  expect(playing("skyliners:club_band") === 0, "freighter: gone well away from it, the song falls silent");
}

// A freighter's tables, her lounge's booth and her crew quarters' table: their seats stand a block of leg room back
// from them, not jammed up against them, and her lounge's table has its seats round it.
function checkLegRoom() {
  const seats = must("find skyliners:business_seat[half=lower] 128 16").rows.map((row) => row.at_abs);
  const apartFrom = ([tx, , tz]) => seats.map(([x, , z]) => Math.max(Math.abs(x - tx), Math.abs(z - tz))).filter((blocks) => blocks <= BOOTH_REACH);
  const tables = must("find skyliners:lounge_table 128 8").rows.map((row) => apartFrom(row.at_abs));
  if (tables.length === 0) {
    return;
  }
  const seated = Math.max(...tables.map((apart) => apart.length)) >= LOUNGE_SEATED && tables.every((apart) => apart.length >= TABLE_SEATED);
  const roomy = tables.every((apart) => apart.every((blocks) => blocks >= LOUNGE_SEAT_OUT));
  expect(seated && roomy, "freighter: her tables' seats leave leg room before them",
    tables.map((apart) => `seats ${apart.join(", ")} blocks from a table`).join("; "));
}

// A freighter's small tardigrade: a quarter the size of the rocket's, in the tank in her lounge, it comes to its hatch
// to munch moss fed through it, munching higher than the big one.
function checkFreighterTardigrade() {
  ship("freighter");
  const [hatchRow] = must("find skyliners:habitat_hatch 128 1").rows;
  expect(Boolean(hatchRow), "freighter: she is built with a tank in her lounge, a feeding hatch on it");
  if (!hatchRow) {
    return;
  }
  const scale = must("server execute as @e[type=skyliners:tardigrade] run attribute @s minecraft:generic.scale get").rows[0]?.line ?? "";
  const size = Number(scale.split(" ").at(-1));
  expect(size === SMALL_TARDIGRADE, "freighter: a small tardigrade lives in it, a quarter the rocket's one's size", `size ${size}`);
  const pad = must("find minecraft:moss_carpet 128 20").rows.find(({ at_abs: [x, y, z] }) =>
    y === hatchRow.at_abs[1] && Math.max(Math.abs(x - hatchRow.at_abs[0]), Math.abs(z - hatchRow.at_abs[2])) <= MOSS_PAD_NEAR);
  expect(Boolean(pad), "freighter: a pad of moss stands beside its hatch, to show what it eats", pad ? `at ${pad.at_abs.join(",")}` : "none");
  const hatch = hatchRow.at_abs.join(",");
  const [dx, dz] = CLIMBS[must(`block ${hatch}`).props.facing];
  const [hx, hy, hz] = hatchRow.at_abs;
  emptyHands();
  must(`server give @p minecraft:moss_carpet 4; select 0; server tp @p ${hx + 0.5 + dx * 2.5} ${hy - 1} ${hz + 0.5 + dz * 2.5}; wait 20t; face ${hatch}`);
  const since = now();
  must(`use ${hatch}`);
  const munch = heard("*sniffer.eat*", since, SMALL_COMES_WITHIN);
  expect(Boolean(munch), "freighter: fed moss through the hatch, it comes to the hatch and munches it",
    munch ? `${munch.tick - since} ticks after` : "it never munched");
  expect(munch?.pitch > BIG_MUNCH_HIGHEST, "freighter: it munches higher than the big one", `pitch ${munch?.pitch}`);
}

// The freighter carries her sisters' paintings, as each of them carries hers: the airliner, the zeppelin and the
// rocket each hang whole on her deck, and each is lit, the zeppelin's in her pink club too.
function checkFleetOnTheFreighter() {
  ship("freighter");
  const sisters = ["airliner", "zeppelin", "rocket"];
  const piecesOf = (poster) => must(`find skyliners:space_poster[poster=${poster}] 128 ${PAINTING_PIECES + 1}`).rows.length;
  const hung = sisters.map((poster) => [poster, piecesOf(poster)]);
  expect(hung.every(([, pieces]) => pieces === PAINTING_PIECES), "freighter: her sisters' paintings hang whole on her deck",
    hung.map(([poster, pieces]) => `${poster} ${pieces} of ${PAINTING_PIECES}`).join(", "));
  const middleOf = (poster) => must(`find skyliners:space_poster[poster=${poster},row=1,column=2] 128 1`).rows[0]?.at_abs.join(",");
  const lightAt = (at) => (at ? must(`block ${at} light`).block_light ?? 0 : 0);
  const lit = sisters.map((poster) => [poster, lightAt(middleOf(poster))]);
  expect(lit.every(([, light]) => light >= PAINTING_LIT), "freighter: each of her sisters' paintings is lit",
    lit.map(([poster, light]) => `${poster} ${light}`).join(", "));
}

// A freighter's Spookie: it lives aboard, kept by its hammock in her crew quarters; walked up to, it greets them; touched,
// it purrs; fed a cookie, it eats it; struck, it honks and is never hurt; and it walks its rounds over her deck.
function checkFreighterSpookie() {
  ship("freighter");
  const hammock = must("find skyliners:spookie_hammock 128 1").rows.length > 0;
  expect(hammock, "freighter: she is built with the Spookie's hammock in her crew quarters");
  const spookie = () => must("find skyliners:spookie 128 1").rows[0];
  waitOutGreeting();
  const found = spookie();
  expect(Boolean(found), "freighter: her Spookie lives aboard");
  if (!found) {
    return;
  }
  const beside = () => {
    const [x, y, z] = spookie()?.at_abs ?? found.at_abs;
    must(`server tp @p ${x + SPOOKIE_NEAR} ${y} ${z}; wait 2t; face ${found.ref}`);
  };
  emptyHands();
  beside();
  const greeted = now();
  expect(Boolean(heard("*spookie_greet*", greeted - LOOK_EVERY, SPOOKIE_ANSWERS)), "freighter: walked up to, her Spookie hoots hello");
  const touched = now();
  must(`use ${found.ref}`);
  expect(Boolean(heard("*spookie_happy*", touched, SPOOKIE_ANSWERS)), "freighter: touched, it purrs and hoots");
  survival();
  must("server give @p minecraft:cookie 2; select 0");
  beside();
  const fed = now();
  must(`use ${found.ref}`);
  const stack = must("inv").rows.find((row) => row.item === "cookie" || row.item === "minecraft:cookie");
  const cookies = stack ? stack.count ?? 1 : 0;
  expect(cookies === 1 && Boolean(heard("*generic.eat*", fed, SPOOKIE_ANSWERS)), "freighter: fed a cookie, it eats it", `${cookies} of 2 left`);
  beside();
  const struck = now();
  must("attack x1");
  const honked = heard("*spookie_alarm*", struck, SPOOKIE_ANSWERS);
  const health = spookie()?.hp;
  expect(Boolean(honked) && health === SPOOKIE_HEALTH, "freighter: struck, it honks in alarm and is never hurt", `health ${health}`);
  creative();
  const from = spookie().at_abs;
  must(`wait ${SPOOKIE_WATCHED}t`);
  const to = spookie()?.at_abs ?? from;
  const walked = Math.hypot(to[0] - from[0], to[2] - from[2]);
  expect(walked >= SPOOKIE_WALKS && Math.abs(to[1] - from[1]) < 0.5, "freighter: it walks its rounds over her deck",
    `${walked.toFixed(1)} blocks, from y ${from[1]} to ${to[1]}`);
}

// Waits until a minute has gone by since the Spookie last greeted anyone, as on an earlier check's visit, since it
// greets nobody again until then.
function waitOutGreeting() {
  const since = now() - SPOOKIE_GREETS_AGAIN;
  const greetings = played("*spookie_greet*", since).map((row) => row.tick);
  if (greetings.length > 0) {
    must(`wait ${Math.max(...greetings) + SPOOKIE_GREETS_AGAIN - now() + LOOK_EVERY}t`, SPOOKIE_GREETS_AGAIN / 20 + 30);
  }
}

// A freighter's rooms told apart: each has its ring of LED strips and its panel by the doorway, each set to a colour of
// its own, and a floor of its own, so no two rooms look alike.
function checkFreighterLeds() {
  ship("freighter");
  const panels = must(`find skyliners:led_panel 128 ${FREIGHTER_ROOMS + 1}`).rows.map((row) => row.at_abs);
  const strips = must("find skyliners:led_strip 128 1000").rows.length;
  expect(panels.length === FREIGHTER_ROOMS && strips >= FREIGHTER_STRIPS, "freighter: each of her rooms has its LED strips and their panel",
    `${panels.length} panels, ${strips} strips`);
  const colourOf = (at) => must(`server data get block ${at.join(" ")} Setting.Colour`).rows[0]?.line.split(" ").at(-1);
  const colours = new Set(panels.map(colourOf));
  expect(colours.size === panels.length, "freighter: each room's strips are a colour of their own", [...colours].join(", "));
  const floors = new Set(panels.map(([x, y, z]) => must(`block ${x},${y - 2},${z}`).id));
  expect(floors.size >= OWN_FLOORS, "freighter: her rooms have floors of their own", [...floors].join(", "));
}

// A freighter's jump drive in engineering: used, it spools up and fires, or sputters until thumped, when it fires; and
// it shows which by its block, running or sputtering, as its rings do.
function checkFreighterJumpDrive() {
  ship("freighter");
  const [drive] = must("find skyliners:jump_drive 128 1").rows;
  expect(Boolean(drive), "freighter: she is built with her jump drive in engineering");
  if (!drive) {
    return;
  }
  const [x, y, z] = drive.at_abs;
  const at = drive.at_abs.join(",");
  must(`server tp @p ${x + 0.5} ${y} ${z - 2.5} 0 20; wait 5t`);
  const used = now();
  must(`use ${at}`);
  expect(Boolean(heard("*jump_drive_spool*", used, LOOK_EVERY)), "freighter: used, her jump drive spools up");
  const fired = heard("*jump_drive_jump*", used, DRIVE_SPOOLS);
  if (!fired) {
    const sputtered = must(`block ${at}`).props?.drive === "sputtering";
    expect(sputtered, "freighter: spooled up, it fires or sputters", must(`block ${at}`).props?.drive);
    const thumped = now();
    must(`use ${at}`);
    expect(Boolean(heard("*jump_drive_thump*", thumped, LOOK_EVERY)) && Boolean(heard("*jump_drive_jump*", thumped, DRIVE_THUMPED)),
      "freighter: sputtering, thumped, it kicks back in and fires");
  }
  expect(must(`block ${at}`).props?.drive === "running", "freighter: fired, it runs", must(`block ${at}`).props?.drive);
}

// A freighter's breaker board in engineering: flipping engineering's switch puts the room's LED strips out, and flipping
// it again brings them back as bright as they were.
function checkFreighterBreakerBoard() {
  ship("freighter");
  const [board] = must("find skyliners:breaker_board 128 1").rows;
  expect(Boolean(board), "freighter: she is built with her breaker board in engineering");
  if (!board) {
    return;
  }
  const [x, y, z] = board.at_abs;
  const [drive] = must("find skyliners:jump_drive 128 1").rows;
  const panel = must("find skyliners:led_panel 128 6").rows.map((row) => row.at_abs)
    .reduce((nearest, at) => (Math.hypot(at[0] - drive.at_abs[0], at[2] - drive.at_abs[2]) < Math.hypot(nearest[0] - drive.at_abs[0], nearest[2] - drive.at_abs[2]) ? at : nearest));
  const brightness = () => Number(must(`server data get block ${panel.join(" ")} Setting.Brightness`).rows[0]?.line.split(" ").at(-1));
  const before = brightness();
  const [dx, dz] = STEPS[must(`block ${board.at_abs.join(",")}`).props.facing];
  must(`server tp @p ${x + 0.5 + dx * 2.5} ${y - 1} ${z + 0.5 + dz * 2.5}; wait 5t; use ${board.at_abs.join(",")} at ${ENGINEERING_SWITCH}; wait 5t`);
  const out = brightness();
  expect(out === 0, "freighter: flipping engineering's switch puts its lights out", `brightness ${before} then ${out}`);
  must(`use ${board.at_abs.join(",")} at ${ENGINEERING_SWITCH}; wait 5t`);
  const back = brightness();
  expect(back === before, "freighter: flipped again, they come back as bright as they were", `brightness ${back}, was ${before}`);
}

// A freighter's coolant valve in engineering: each turn of its wheel squeaks and raises its pressure; at full it blasts
// steam and lets the pressure off again.
function checkFreighterCoolantValve() {
  ship("freighter");
  const [valve] = must("find skyliners:coolant_valve 128 1").rows;
  expect(Boolean(valve), "freighter: she is built with her coolant valve in engineering");
  if (!valve) {
    return;
  }
  const at = valve.at_abs.join(",");
  const [dx, dz] = STEPS[must(`block ${at}`).props.facing];
  must(`server tp @p ${valve.at_abs[0] + 0.5 + dx * 2.5} ${valve.at_abs[1] - 1} ${valve.at_abs[2] + 0.5 + dz * 2.5}; wait 5t`);
  const turned = now();
  for (let turn = 1; turn < VALVE_TURNS; turn++) {
    must(`use ${at} at ${VALVE_WHEEL}; wait 4t`);
  }
  const rising = must(`block ${at}`).props;
  expect(Number(rising.pressure) === VALVE_TURNS - 1 && Boolean(heard("*irrigation_valve*", turned, LOOK_EVERY)),
    "freighter: each turn of its wheel squeaks and raises its pressure", `pressure ${rising.pressure}`);
  const blasted = now();
  must(`use ${at} at ${VALVE_WHEEL}; wait 2t`);
  expect(must(`block ${at}`).props.venting === "true" && Boolean(heard("*ramp_steam*", blasted, LOOK_EVERY)), "freighter: at full, it blasts steam");
  must(`wait ${VALVE_VENTS}t`);
  const after = must(`block ${at}`).props;
  expect(after.venting === "false" && Number(after.pressure) === 0, "freighter: venting, it lets the pressure off", `pressure ${after.pressure}`);
}

// A freighter's crossbow rack in her entry hall: an empty hand takes the laser crossbow down, and holding it racks it
// again.
function checkFreighterCrossbowRack() {
  ship("freighter");
  const [rack] = must("find skyliners:crossbow_rack 128 1").rows;
  expect(Boolean(rack), "freighter: she is built with the laser crossbow's rack in her entry hall");
  if (!rack) {
    return;
  }
  const at = rack.at_abs.join(",");
  const [dx, dz] = STEPS[must(`block ${at}`).props.facing];
  emptyHands();
  must(`server tp @p ${rack.at_abs[0] + 0.5 + dx * 2.5} ${rack.at_abs[1] - 1} ${rack.at_abs[2] + 0.5 + dz * 2.5}; wait 5t; use ${at}; wait 5t`);
  const held = () => must("inv").rows.some((row) => row.item === "skyliners:laser_crossbow");
  expect(held() && must(`block ${at}`).props.loaded === "false", "freighter: an empty hand takes the crossbow down off its rack");
  survival();
  must(`use ${at}; wait 5t`);
  expect(!held() && must(`block ${at}`).props.loaded === "true", "freighter: holding it, it is racked again");
  creative();
}

// A freighter's rooms filled out with furniture of their own, so none stands bare.
function checkFreighterFurnishings() {
  ship("freighter");
  for (const [room, pieces] of Object.entries(FURNISHINGS)) {
    const short = Object.entries(pieces).map(([block, least]) => [block, least, must(`find ${block} 128 ${least + 20}`).rows.length])
      .filter(([, least, found]) => found < least);
    expect(short.length === 0, `freighter: her ${room} is furnished`, short.map(([block, least, found]) => `${block} ${found} of ${least}`).join(", "));
  }
}

// A freighter lit all through: no spot inside her that someone could stand in is left unlit, where monsters could
// spawn, as there were by her thrusters before, and none of her passages looks black.
function checkFreighterLit() {
  ship("freighter");
  const [{ at_abs: [x, y, z] }] = must("find skyliners:jump_drive 128 1").rows;
  must(`server tp @p ${x + 0.5} ${y} ${z + 0.5 - JUMP_DRIVE_ASTERN}; wait 40t`);
  const unlit = must(`skyliners:dark radius=${LIT_REACH} least=1`);
  expect(Number(unlit.dark) === 0, "freighter: no spot inside her is left unlit, where monsters could spawn", `${unlit.dark} unlit, first ${unlit.first || "none"}`);
}

// A freighter's passenger screens, used aboard her once she flies: one shows the view from her outside cameras, where
// pressing one once crashed the game reaching for a camera she did not have.
function checkFreighterScreens() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  const [screen] = must(`sable:find ${freighter} skyliners:passenger_screen 1 side=server`).rows;
  expect(Boolean(screen), "freighter: aboard her flying, she has passenger screens");
  if (!screen) {
    return;
  }
  const at = screen.at.join(",");
  const [dx, dz] = STEPS[must(`block ${at}`).props?.facing ?? "north"];
  const [sx, sy, sz] = screen.at;
  emptyHands();
  must(`sable:tp ${sx + dx * SCREEN_STAND},${sy - 1},${sz + dz * SCREEN_STAND}; wait 20t; face ${at}`);
  const since = now();
  send(`use ${at}`);
  const click = heard("*ui.button.click*", since, LOOK_EVERY);
  const thud = played("*note_block.bass*", since).length;
  expect(Boolean(click) && thud === 0 && Boolean(must("wait 40t; status").pos), "freighter: pressed, a screen shows her outside camera and the game runs on",
    `${click ? "a click" : "no click"}, ${thud} thuds`);
  must("key left_shift 5t; wait 10t");
}

// A round floor window laid from its item into the ground anywhere: every block of it blends into the grass it took
// the place of; its panel, set down by it, opens and shuts it; and broken, it all comes out, the grass put back where
// it was and the window given back.
function checkFloorWindow() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  const [x, z] = [site.x, site.z];
  const middle = `${x},${ground - 1},${z}`;
  creative();
  emptyHands();
  must(`server tp @p ${x + 0.5} ${ground} ${z - 3.5} 0 40; wait 40t`);
  must("server give @p skyliners:floor_window 1; server give @p skyliners:floor_window_panel 1; wait 5t; select 0");
  // The window lays itself into the ground under where it is aimed, which Blockpilot's place does not look for.
  send(`place skyliners:floor_window ${x},${ground},${z}`);
  must("wait 10t");
  const laid = must("find skyliners:floor_window 12 100").rows.length;
  const floor = must(`server data get block ${x + 2} ${ground - 1} ${z + 1} Floor.Name`).rows[0]?.line ?? "";
  expect(laid >= FLOOR_WINDOW_BLOCKS && floor.includes("grass_block"), "floor window: laid into the ground, the whole of it blends into the grass",
    `${laid} blocks, ${floor}`);
  must(`server tp @p ${x + 0.5} ${ground} ${z - 8.5} 0 30; wait 10t; select 1; place skyliners:floor_window_panel ${x},${ground},${z - 6}; wait 10t`);
  const panel = `${x},${ground},${z - 6}`;
  must(`use ${panel}; wait ${IRIS_TICKS}t`);
  expect(must(`block ${middle}`).props?.open === "true", "floor window: its panel set down by it opens it");
  must(`use ${panel}; wait ${IRIS_TICKS}t`);
  expect(must(`block ${middle}`).props?.open === "false", "floor window: pressed again, it shuts");
  survival();
  must(`select 2; server tp @p ${x + 0.5} ${ground} ${z - 5.5} 0 70; wait 10t; mine ${x},${ground - 1},${z - 4}; wait 20t`);
  const left = must("find skyliners:floor_window 12 100").rows.length;
  const back = must(`block ${middle}`).id;
  const given = must("server data get entity @e[type=item,limit=1,sort=nearest] Item.id").rows[0]?.line ?? "";
  expect(left === 0 && back.endsWith("grass_block") && given.includes("floor_window"), "floor window: broken, it all comes out, the grass put back and the window given back",
    `${left} blocks left, ${back} in its middle, ${given || "nothing"} dropped`);
  creative();
}

// A freighter's beach in her lounge: her round floor window in the middle of its sand, blending into it, loungers,
// palms, tiki torches and a parasol round it, and its panel, which opens the window onto its shaft down to the glass in
// her belly.
function checkFreighterBeach() {
  ship("freighter");
  const windows = must("find skyliners:floor_window 128 100").rows.map((row) => row.at_abs);
  expect(windows.length >= FLOOR_WINDOW_BLOCKS, "freighter: her lounge has its round floor window", `${windows.length} blocks`);
  const count = (id) => must(`find skyliners:${id} 128 10`).rows.length;
  const pieces = { loungers: count("sun_lounger"), palms: count("potted_palm"), torches: count("tiki_torch"), parasols: count("parasol") };
  expect(pieces.loungers >= BEACH_LOUNGERS && pieces.palms >= BEACH_PALMS && pieces.torches >= 1 && pieces.parasols >= 1,
    "freighter: round it, her beach's loungers, palms, tiki torches and parasol", JSON.stringify(pieces));
  const [panel] = must("find skyliners:floor_window_panel 128 1").rows;
  expect(Boolean(panel) && windows.length > 0, "freighter: her window's panel stands by it");
  if (!panel || windows.length === 0) {
    return;
  }
  const middle = [0, 1, 2].map((axis) => Math.round(windows.reduce((sum, at) => sum + at[axis], 0) / windows.length));
  const data = (path) => must(`server data get block ${middle.join(" ")} ${path}`).rows[0]?.line ?? "";
  const shaft = Number(data("Shaft").split(" ").at(-1));
  expect(data("Floor.Name").includes("beach_sand") && shaft > 0, "freighter: shut, it blends into the sand, its shaft running down to her belly",
    `${data("Floor.Name")}; shaft ${shaft}`);
  const [dx, dz] = STEPS[must(`block ${panel.at_abs.join(",")}`).props?.facing ?? "north"];
  const [px, py, pz] = panel.at_abs;
  must(`server tp @p ${px + 0.5 + dx * 1.5} ${py} ${pz + 0.5 + dz * 1.5}; wait 10t; use ${panel.at_abs.join(",")}; wait ${IRIS_TICKS}t`);
  expect(must(`block ${middle.join(",")}`).props?.open === "true", "freighter: pressed, her window's panel opens it");
  const below = [...Array(shaft + BELLY_BELOW).keys()].map((down) => must(`block ${middle[0]},${middle[1] - 1 - down},${middle[2]}`).id);
  expect(below.includes("skyliners:smoked_glass"), "freighter: down its shaft, her belly is glass", below.join(", "));
  must(`use ${panel.at_abs.join(",")}; wait ${IRIS_TICKS}t`);
  expect(must(`block ${middle.join(",")}`).props?.open === "false", "freighter: pressed again, it shuts");
}

// A freighter's outside: she is built with every piece of it, as many as she should have of each at least; and each
// outside fitting, put up by hand on a floor, on a wall and under a ceiling, takes the shape for where it went, as a
// player clicking there would expect.
function checkFreighterExterior() {
  const site = ship("freighter");
  for (const [id, least] of EXTERIOR_PIECES) {
    const count = found(id);
    expect(count >= least, `freighter: she is built with ${id.replaceAll("_", " ")}, ${least} or more`, `${count} of them`);
  }
  const [x0, z0] = [site.x - Math.floor((FITTINGS_PUT_UP.length * STAND_APART) / 2), site.z + STAND_AWAY];
  const x1 = x0 + FITTINGS_PUT_UP.length * STAND_APART;
  must(`server fill ${x0 - 1} ${ground} ${z0 + 3} ${x1} ${ground + STAND_HIGH + 1} ${z0 + 3} stone`);
  must(`server fill ${x0 - 1} ${ground + STAND_HIGH} ${z0} ${x1} ${ground + STAND_HIGH} ${z0 + 2} stone`);
  FITTINGS_PUT_UP.forEach((id, i) => putUpEveryWay(id, x0 + i * STAND_APART, z0));
}

// How many blocks of the mod's of an id stand in the world round the player: Blockpilot gives the total only when there
// are more than it lists.
function found(id) {
  const reply = must(`find skyliners:${id} 128 1`);
  return reply.count ?? reply.rows.length;
}

// One fitting put up by hand as a player would: standing on the ground, on the stand's wall and under its overhang;
// whether each took the shape for where it went.
function putUpEveryWay(id, x, z) {
  emptyHands();
  must(`server give @p skyliners:${id} 3; wait 5t; select 0`);
  const spots = [["floor", x, ground, z - 3], ["wall", x, ground + 2, z + 2], ["ceiling", x, ground + STAND_HIGH - 1, z]];
  for (const [mount, sx, sy, sz] of spots) {
    must(`server tp @p ${sx + 0.5} ${ground} ${sz - 2.5} 0 20; wait 10t`);
    send(`place skyliners:${id} ${sx},${sy},${sz}`);
    const reply = must(`block ${sx},${sy},${sz} side=server`);
    const put = reply.id ?? "";
    const as = reply.props?.mount ?? "floor";
    expect(put.includes(id) && as === mount, `${id.replaceAll("_", " ")}: put up on a ${mount}, it takes its ${mount} shape`, `${put} mount=${as}`);
  }
}

// A freighter lifting off takes all of her outside along: no piece of it is left on the ground where she stood.
function checkFreighterExteriorLiftOff() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + EXTERIOR_CLIMB} speed=0`, BUILD_SECONDS);
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(up, "freighter: she lifts off her legs", up ? `at ${up.altitude}` : "");
  const left = EXTERIOR_PIECES.filter(([id]) => found(id) > 0).map(([id]) => id);
  expect(left.length === 0, "freighter: lifting off, she leaves none of her outside behind", left.join(", "));
}

// A freighter's deck robot: walked up to, it chirps hello; touched, it trills back.
function checkFreighterRobot() {
  ship("freighter");
  const robots = must(`find skyliners:deck_robot 128 ${FREIGHTER_ROBOTS + 1}`).rows;
  const floored = robots.filter((row) => must(`block ${row.at_abs[0]},${row.at_abs[1] - 1},${row.at_abs[2]}`).id !== "minecraft:air");
  expect(robots.length === FREIGHTER_ROBOTS && floored.length === FREIGHTER_ROBOTS,
    "freighter: she is built with a deck robot in each of two rooms, each standing on the deck",
    `${robots.length} robots, ${floored.length} on the deck`);
  const [robot] = robots;
  if (!robot) {
    return;
  }
  const [x, y, z] = robot.at_abs;
  const facing = must(`block ${x},${y},${z}`).props?.facing ?? "north";
  const [dx, dz] = STEPS[facing] ?? STEPS.north;
  must(`server tp @p ${x + 0.5} ${y + ROBOT_AWAY} ${z + 0.5}; wait 40t`);
  const since = now();
  must(`server tp @p ${x + 0.5 + dx * ROBOT_FRONT} ${y} ${z + 0.5 + dz * ROBOT_FRONT}`);
  expect(heard("skyliners:robot_chirp", since, ROBOT_HEARD_TICKS), "freighter: walked up to, her deck robot chirps hello");
  const touched = now();
  must(`use ${x},${y},${z}`);
  expect(heard("skyliners:robot_trill", touched, ROBOT_HEARD_TICKS), "freighter: touched, her deck robot trills back");
}

// Lifts a freighter laid down off her legs to cruise, and puts the player aboard her, so she stays loaded flying off.
function rideFreighterUp(head) {
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(up, "freighter: she lifts off to fly to the pin");
  if (!up) {
    return false;
  }
  const freighter = findShipFlying("skyliners:engine_grille");
  const { at } = must(`sable:find ${freighter} skyliners:autopilot 1 side=server`).rows[0];
  must(`sable:tp ${at.join(",")}; wait 20t`);
  return true;
}

// Sends a freighter to land at a pin and says whether she came down level near it; her report once down.
function landFreighterAt(head, x, z, where) {
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=${DRAG_CRUISE} pin=${x},${z} land=true`);
  const down = waitForReport(freighterReport, (report) => report.phase === "off", PAD_DOWN_TICKS);
  const last = down ?? freighterReport();
  expect(down, `freighter: set to land at the pin, she comes down ${where} and switches off`,
    down ? "" : `still ${last?.phase}, pad ${last?.pad}, middle ${last?.middle}, heading ${last?.heading}, going ${last?.ahead},${last?.across}`);
  if (!down) {
    return undefined;
  }
  const lean = Math.max(Math.abs(down.tilt_front), Math.abs(down.tilt_right));
  expect(lean < PAD_LEVEL_WITHIN, `freighter: she stands level on her legs ${where}`, `leaning ${lean} degrees, middle ${down.middle}, pad ${down.pad}`);
  const [mx, mz] = String(down.middle).split(",").map(Number);
  expect(Math.hypot(mx - x, mz - z) <= PAD_REACH + 2, "freighter: she comes down near the pin", `${Math.hypot(mx - x, mz - z).toFixed(1)} blocks from it`);
  return down;
}

// A rocket in flight: the countdown runs out, her engines fall silent with her, and whoever stood aboard floats down
// unhurt under their parachute.
function checkRocketSelfDestructInFlight() {
  layDown("rocket");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + DESTRUCT_HEIGHT}`, BUILD_SECONDS);
  const rocket = findRocketFlying();
  const climbing = waitForRocket((report) => report.phase === "cruise" || report.phase === "descent", HOP_UP_TICKS);
  expect(climbing, "rocket: she climbs to where the self-destruct is to be pressed");
  const { at } = must(`sable:find ${rocket} skyliners:self_destruct_button 1 side=server`).rows[0];
  survival();
  must(`sable:tp ${at.join(",")}; wait 20t; move left 4t; wait 10t`);
  const since = armButton(at.join(","));
  expect(heard("*self_destruct_blast*", since, DESTRUCT_ARMED_TICKS), "rocket: in flight, the countdown runs out and she blows up");
  expect(!stillThere(rocket), "rocket: blown up in flight, she is gone");
  expect(wearsParachute(), "rocket: whoever stood aboard her in flight is given a parachute");
  expect(must("wait 40t; sounds playing match=*rocket_roar*").count === 0, "rocket: her roar stops with her");
  let status = must("status");
  for (let waited = 0; waited <= DESTRUCT_FALL_TICKS && status.pos[1] > ground + DESTRUCT_DOWN_WITHIN; waited += LOOK_EVERY) {
    status = must(`wait ${LOOK_EVERY}t; status`);
  }
  expect(status.pos[1] <= ground + DESTRUCT_DOWN_WITHIN, "rocket: whoever bailed out reaches the ground", `at height ${round(status.pos[1])}, ground ${ground}`);
  expect(String(status.hp).startsWith("20"), "rocket: whoever bailed out lands unhurt", `health ${status.hp}`);
  must(`server tp @p 0 ${ground} 0`);
  creative();
}

// A curtain's piece as a command puts it up: one of a wall of them, facing north, joined to the rest.
function curtainPiece(across, up, open) {
  const joined = { left: across > 0, right: across < CURTAIN_ACROSS - 1, down: up > 0, up: up < CURTAIN_UP - 1 };
  const props = Object.entries(joined).map(([name, value]) => `${name}=${value}`).join(",");
  return `skyliners:cabin_curtain[facing=north,open=${open},${props}]`;
}

// Hangs a curtain at the site, wider than anyone parts, and gives the column to walk through.
function hangCurtain(site, open) {
  for (let across = 0; across < CURTAIN_ACROSS; across++) {
    for (let up = 0; up < CURTAIN_UP; up++) {
      must(`server setblock ${site.x + across} ${ground + up} ${site.z} ${curtainPiece(across, up, open)}`);
    }
  }
  return site.x + Math.floor(CURTAIN_ACROSS / 2);
}

function takeDownCurtain(site) {
  must(`server fill ${site.x} ${ground} ${site.z} ${site.x + CURTAIN_ACROSS - 1} ${ground + CURTAIN_UP - 1} ${site.z} air`);
}

const curtainOpen = (x, y, z) => read([x, y, z], [x, y, z]).get(key(x, y, z))?.includes("open=true") ?? false;

// Walks at a curtain from the south in survival mode, and stops with the player's middle in it.
function walkInto(x, z) {
  must(`server gamemode survival @p; server tp @p ${x + 0.5} ${ground} ${z + CURTAIN_RUN_UP + 0.5}; turn 180 0; wait 20t`);
  must(`move forward ${CURTAIN_WALK_TICKS}t`);
}

// On the ground: it parts round whoever walks through and nowhere else, with a swish, and shuts behind them; drawn open
// by hand it stays open. Aboard an airliner too, where its blocks and the player are in different frames.
function checkCurtainParts() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  creative();
  must(`server tp @p ${site.x} ${ground} ${site.z + CURTAIN_RUN_UP}; wait ${CHUNKS_TICKS}t`);
  const middle = hangCurtain(site, false);
  const since = now();
  walkInto(middle, site.z);
  const at = must("status").pos.map(round).join(",");
  expect(curtainOpen(middle, ground, site.z), "curtain: it parts as someone walks through it", `standing at ${at}`);
  expect(!curtainOpen(site.x, ground, site.z), "curtain: it stays shut away from them");
  const swishes = played("*wool.place*", since).length;
  expect(swishes >= 1, "curtain: it swishes as it parts", `${swishes} swishes`);
  must(`move forward ${CURTAIN_WALK_TICKS}t; wait ${CURTAIN_SHUTS_TICKS}t`);
  expect(!curtainOpen(middle, ground, site.z), "curtain: it shuts again behind them");
  creative();
  takeDownCurtain(site);
  const openMiddle = hangCurtain(site, true);
  walkInto(openMiddle, site.z);
  must(`move forward ${CURTAIN_WALK_TICKS}t; wait ${CURTAIN_SHUTS_TICKS}t`);
  expect(curtainOpen(openMiddle, ground, site.z), "curtain: one drawn open by hand stays open after someone walks through");
  creative();
  takeDownCurtain(site);
  checkCurtainSwitchedOff(site);
  creative();
  checkCurtainPartsAboard();
}

function checkCurtainSwitchedOff(site) {
  must("config skyliners server cabin.curtainsPart false");
  try {
    const middle = hangCurtain(site, false);
    walkInto(middle, site.z);
    expect(!curtainOpen(middle, ground, site.z), "curtain: with its switch off, it stays as it hangs");
  } finally {
    send("?config reset");
  }
}

// Walks through a curtain of a furnished airliner assembled and standing still, its blocks in Sable's plot.
function checkCurtainPartsAboard() {
  layDown("furnished");
  board();
  must(`skyliners:autopilot fly altitude=${ground + FLIGHT_HEIGHT} speed=${FLIGHT_SPEED}`, BUILD_SECONDS);
  must(`wait ${CURTAIN_ASSEMBLED_TICKS}t; skyliners:autopilot off; wait ${CURTAIN_ASSEMBLED_TICKS}t`);
  const pieces = must("sable:find here skyliners:cabin_curtain 400 side=server").rows;
  const low = Math.min(...pieces.map((row) => row.at[1]));
  const lowest = pieces.filter((row) => row.at[1] === low);
  const piece = lowest[Math.floor(lowest.length / 2)];
  const at = piece.at.join(",");
  const { props } = must(`block ${at}`);
  const { facing } = props;
  const [dx, dz, yaw] = CLIMBS[facing];
  must(`sable:tp ${piece.at[0] + dx * CURTAIN_RUN_UP},${piece.at[1]},${piece.at[2] + dz * CURTAIN_RUN_UP}; wait 10t`);
  const shut = Object.entries({ ...props, open: "false" }).map(([name, value]) => `${name}=${value}`).join(",");
  if (props.open !== "false") {
    must(`server setblock ${piece.at.join(" ")} skyliners:cabin_curtain[${shut}]; wait 5t`);
  }
  const turned = must("sable:info here brief").yaw;
  survival();
  must(`turn ${round(yaw + 180 + turned)} 0; wait 10t; move forward ${CURTAIN_WALK_TICKS}t`);
  const parted = must(`block ${at}`).props.open === "true";
  expect(parted, "curtain: aboard an assembled airliner, it parts too", `at ${at}, player at ${must("status").pos.map(round).join(",")}`);
  must(`move forward ${CURTAIN_WALK_TICKS}t; wait ${CURTAIN_SHUTS_TICKS}t`);
  expect(must(`block ${at}`).props.open === "false", "curtain: aboard, it shuts again behind them");
  creative();
  must(`server tp @p 0 ${ground} 0`);
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
  "curtain-parts": { run: checkCurtainParts, about: "a curtain parts as someone walks through it and shuts behind them" },
  "bartender-leaning-rocket": { run: checkBartenderOnALeaningRocket, about: "a bartender on a leaning rocket does not keep whirring at someone standing still" },
  "bartender-racket": { run: checkBartendersAreNoRacket, about: "a crowd of robot bartenders is not a constant racket of servos" },
  "airstairs-reach-ground": { run: checkAirstairsReachTheGround, about: "a newly built airliner's stairs can be walked up from the ground" },
  "airstairs-leave-no-steps": { run: checkNoStepsLeftBehind, about: "taking off from a new build leaves no steps behind and takes no ground along" },
  "zeppelin-stairs": { run: checkZeppelinStairs, about: "a zeppelin's stairs can be walked up from the ground" },
  "rocket-stairs": { run: checkRocketStairs, about: "a rocket's stairs can be walked up from the ground" },
  "freighter-painting": { run: checkFreighterOnTheShips, about: "the freighter hangs whole and unhidden on each ship, and the wheel and breaking it work" },
  "fog-hiss": { run: checkFogHissStops, about: "a fog machine's hiss plays out, and stops when the machine goes however it goes" },
  "tardigrade-ice-cream": { run: checkTardigradeIceCream, about: "an ice cream: gulp, massive burp, the ship shakes, hyperspace, back" },
  "control-surfaces": { run: checkControlSurfacesDefault, about: "control surfaces are on by default" },
  "flight": { run: checkFlightAndLanding, about: "take-off, cruise, landing, and walking up the stairs after it", slow: true },
  "rocket-self-destruct-ground": { run: checkRocketSelfDestructOnTheGround, about: "a rocket's self-destruct puts her together, arms and blows her up" },
  "rocket-self-destruct-flight": { run: checkRocketSelfDestructInFlight, about: "a rocket blows up in flight, her roar stops, and whoever was aboard floats down unhurt", slow: true },
  "rocket-switches-off": { run: checkRocketSwitchesOffWhereSheRests, about: "a rocket switches off where she comes to rest, with the ground lower under her middle or hidden by leaves", slow: true },
  "freighter-drag": { run: checkFreighterUnlearnsDrag, about: "a freighter that has learnt far too much drag is not pushed on past the speed set, and unlearns it", slow: true },
  "freighter-pad": { run: checkFreighterPad, about: "a freighter set to land at a pin with stone on it comes down beside it, level, and looks again next time", slow: true },
  "freighter-ramp": { run: checkFreighterRamp, slow: true, about: "a freighter's ramp comes down by itself, is walked up, is called from her leg, and holds her take-off while someone stands on it" },
  "freighter-turret": { run: checkFreighterTurret, slow: true, about: "a freighter's core ladder is climbed to her gunner's seat, whose cannon strikes a creeper and breaks nothing" },
  "laser-crossbow": { run: checkLaserCrossbow, about: "the laser crossbow strikes a creeper, never a cow, and vents after three quick shots" },
  "freighter-rooms": { run: checkFreighterRooms, about: "a freighter's crew quarters have their bunks, her lounge its booth with leg room, her hold its cargo and scanner" },
  "freighter-tardigrade": { run: checkFreighterTardigrade, about: "a freighter's small tardigrade lives in her lounge's tank and comes to its hatch to munch moss" },
  "fleet-on-freighter": { run: checkFleetOnTheFreighter, about: "the airliner, zeppelin and rocket paintings hang whole on the freighter" },
  "freighter-lights": { run: checkFreighterLights, about: "a freighter is built with wall washers round her rooms, her club and ring corridor lit" },
  "freighter-band": { run: checkFreighterBand, about: "a freighter's club has a bar and a band that plays her song to whoever comes near, silent as they go" },
  "freighter-spookie": { run: checkFreighterSpookie, about: "a freighter's Spookie greets, purrs, eats a cookie, is never hurt and walks its rounds" },
  "freighter-leds": { run: checkFreighterLeds, about: "a freighter's rooms each have LED strips and a panel, each room a colour and a floor of its own" },
  "freighter-jump-drive": { run: checkFreighterJumpDrive, about: "a freighter's jump drive spools up and fires, or sputters until thumped" },
  "freighter-breaker-board": { run: checkFreighterBreakerBoard, about: "a freighter's breaker board puts a room's lights out and back on" },
  "freighter-coolant-valve": { run: checkFreighterCoolantValve, about: "a freighter's coolant valve is cranked up to a blast of steam and lets it off" },
  "freighter-crossbow-rack": { run: checkFreighterCrossbowRack, about: "a freighter's crossbow rack gives the laser crossbow down and takes it back" },
  "freighter-furnishings": { run: checkFreighterFurnishings, about: "a freighter's rooms are filled out with furniture of their own" },
  "freighter-screens": { slow: true, run: checkFreighterScreens, about: "a freighter's passenger screens, pressed aboard her flying, show her outside cameras" },
  "freighter-lit": { run: checkFreighterLit, about: "a freighter has no spot inside her left unlit, where monsters could spawn" },
  "floor-window": { run: checkFloorWindow, about: "a round floor window laid into the ground blends into it, opens from its panel and comes out whole" },
  "freighter-beach": { run: checkFreighterBeach, about: "a freighter's beach: her round floor window in its sand, opened from its panel down to her belly's glass" },
  "freighter-exterior": { run: checkFreighterExterior, about: "a freighter is built with her outside, and each outside fitting goes up on a floor, a wall or a ceiling" },
  "freighter-exterior-lift-off": { run: checkFreighterExteriorLiftOff, slow: true, about: "a freighter lifting off leaves none of her outside behind" },
  "freighter-robot": { run: checkFreighterRobot, about: "a freighter's deck robot chirps hello as someone walks up to it, and trills when touched" },
  "freighter-lake": { run: checkFreighterLake, about: "a freighter set to land at a pin in a lake holds over it and says she finds no ground", slow: true },
  "freighter-flight": { run: checkFreighterFlight, about: "the freighter lifts off, hovers level and roars, not spooling up as she holds her spot, and lands by herself", slow: true },
  "rocket-middle-click": { run: checkRocketMiddleClick, about: "the mouse wheel gives something for every kind of block in the rocket", slow: true },
};

// Whether a child process is still running, or nothing here can tell. This script blocks Node's event loop, so a child
// that has exited is never reaped: it stays a zombie, which still answers a signal, and only ps tells the two apart.
function alive(pid) {
  const { stdout } = spawnSync("ps", ["-o", "stat=", "-p", String(pid)], { encoding: "utf8" });
  return stdout === null || (stdout.trim() !== "" && !stdout.trim().startsWith("Z"));
}

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
  const gradle = spawn("bash", ["./gradlew", "runClient", "--console=plain"], { cwd: ROOT, detached: true, stdio: "ignore" });
  gradle.unref();
  for (let waited = 0; waited < GAME_UP_SECONDS; waited += 5) {
    spawnSync("sleep", ["5"]);
    if (!alive(gradle.pid)) {
      throw new GameGone("the dev client closed as it started: see run/logs/latest.log and any run/hs_err_pid*.log");
    }
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
