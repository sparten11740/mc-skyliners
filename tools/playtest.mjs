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
import { existsSync, readFileSync, rmSync } from "node:fs";
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
// How long an airliner's airstairs are given to fold or come down, in ticks.
const STAIRS_SWING_TICKS = 200;
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
// Cruising, how long its engines are watched, in seconds, and how many times a second its thrusters may be sent to every
// client near at most: holding its speed by flipping every engine between two signals, they were sent about 290 times.
const CRUISE_WATCH_SECONDS = 20;
const CRUISE_THRUSTER_SENDS = 40;
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
// A freighter's main engine seen from behind her, parked: how far astern of its thruster the camera stands, in blocks;
// where its nozzle's lip is, in blocks from its axis and astern of the thruster's face, and how far round that point
// the frame is read; how bright the brightest tenth of that must be, a luma from 0 to 255: a bare main shows only her
// dark grille there, about 55.
const NOZZLE_CAMERA_BACK = 6;
const NOZZLE_LIP_OUT = 0.88;
const NOZZLE_LIP_ASTERN = 1.22;
const NOZZLE_LIP_ACROSS = 0.08;
const NOZZLE_LIP_LUMA = 110;
// Her main engines: how many she has; how far forward of the sternmost of their relays the rest stand at most, in
// blocks, as her rim curves; and, as each client draws a nozzle, at most how many blocks it stands out past its
// thruster's face, and the blocks round its mouth, so many across and up from its axis, that make it stand out.
const NOZZLE_MAINS = 6;
const NOZZLE_MAINS_DEEP = 1;
const NOZZLE_STANDS_OFF_MOST = 2;
const NOZZLE_ROUND_MOUTH = [[-1, -1], [-1, 0], [-1, 1], [0, -1], [0, 1], [1, -1], [1, 0], [1, 1]];
// A freighter's underside camera: the height she is sent to hover at, in world blocks, low over the flat world's grass;
// how long a passenger screen is given to show it, in ticks; the halves of its picture read, as frame fractions from
// the top left, clear of its label; and how much greener than red and blue each must be to be the grass, the inside of
// her hull plate being browner (red 55, green 52, blue 43).
const UNDERSIDE_HOVER = -30;
const UNDERSIDE_SETTLE = 60;
const UNDERSIDE_LEFT = "0.05,0.15,0.45,0.85";
const UNDERSIDE_RIGHT = "0.55,0.15,0.95,0.85";
const UNDERSIDE_GREEN = 1.15;
// An engine grille set by hand: how far ahead of the player it is set, in blocks; how far its lever stands along a run
// of wire to it, in blocks, far enough that the lever's signal arrives weak; how long a change of signal is given to
// reach it, in ticks; and how long a flying freighter is given to have lit her own grille, in ticks.
const GRILLE_AHEAD = 4;
const GRILLE_WIRE = 12;
const GRILLE_SETTLE_TICKS = 10;
const GRILLE_LIGHT_TICKS = 60;
// A freighter knocked upside down: the height she hovers at as she is turned over, y 300, some 360 blocks over the
// flat world's ground, well over the 270 her fall upside down and then caught was seen to take; how far over she is
// turned, in degrees, a degree short of straight over so the way back is the one this check expects; how long she is
// watched, in ticks, and how often read; how near upright counts as upright again, in degrees; and how near the
// ground her lowest block may come, in blocks.
const FLIP_HEIGHT = 300;
const FLIP_OVER = 179;
const FLIP_WATCH_TICKS = 400;
const FLIP_EVERY = 5;
const FLIP_UPRIGHT = 10;
const FLIP_CLEAR = 20;
// A freighter's spin button: how high over the ground she first hovers, where it says no, in blocks; the height she is
// then lifted to, well over the height a roll takes, in blocks; how long she is watched rolling, in ticks; and how far
// from upright she must come to have rolled right over, in degrees.
const SPIN_LOW = 40;
const SPIN_HEIGHT = 600;
const SPIN_WATCH_TICKS = 400;
const SPIN_OVER = 150;
const FREIGHTER_UP_TICKS = 900;
const FREIGHTER_DOWN_TICKS = 2400;
const FREIGHTER_HOLD_WITHIN = 1.5;
const FREIGHTER_LEVEL_WITHIN = 2;
const FREIGHTER_WATCH_TICKS = 200;
const FREIGHTER_AWAY = 400;
const FREIGHTER_AWAY_TICKS = 60;
// A freighter left flying with nobody near: how far off everyone goes, in blocks, past the 600 within which a ship is
// kept once parked, and how soon she must be down and let go after, in ticks: she is gone after some 900, but used to
// fly on a minute (1,200 ticks) first and come down after it, staying loaded for some 1,950 in all.
const UNATTENDED_AWAY = 1500;
const UNATTENDED_GONE_TICKS = 1400;
// How long each of her computer's lines for her flight deck is given to be heard, in ticks, the take-off one waiting
// for the player to board her and hear it.
const FREIGHTER_VOICE_TICKS = 300;
// A freighter dropped onto the ground: how long her control head takes to be found aboard her once she is assembled,
// far too short a time for her to lift off, and how long she is given to settle, and to groan after landing, in ticks;
// how high she is lifted to fall, in blocks, some 23 blocks a second as she lands; how long she is given to be heard
// striking it, in ticks; and how fast she is then shoved along it, in blocks a second, to be heard grinding.
const IMPACT_ABOARD_TICKS = 2;
const IMPACT_SETTLE_TICKS = 60;
const IMPACT_DROP = 25;
const IMPACT_HEARD_TICKS = 100;
const IMPACT_SHOVE = 15;
// Any sound of a ship striking something: a crunch, the groan after it, a thud or a scrape.
const IMPACT_SOUND = "skyliners:impact_*";
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
const PAD_REACH = 200;
// A freighter landing on a slope: how far ahead of her it is, how far it reaches round the pin, how wide each step
// of the slope is and how many steps there are, in blocks, how far she may lean on it, in degrees, how high to look
// from, how many blocks a fill command may set, as it needs for a layer of the slope, then back to its usual limit, and
// how far behind her a flat pin is for her to land at after it.
const UNEVEN_PIN_AHEAD = 240;
const UNEVEN_REACH = 110;
const UNEVEN_STEP_WIDE = 16;
const UNEVEN_STEPS = 13;
const UNEVEN_LEVEL_WITHIN = 2;
const UNEVEN_LOOK_UP = 30;
const UNEVEN_FLAT_BEHIND = 200;
// A freighter sent to land in the middle of a wide lake, clear ground only well away from the pin: how far ahead of her,
// and how far it reaches round the pin, in blocks. How far she searches in the checks whose lake is smaller than her
// search would otherwise get past, in blocks.
const FAR_PAD_AHEAD = 320;
const FAR_PAD_LAKE = 130;
const SMALL_LAKE_SEARCH = 64;
const UNEVEN_FILL_MOST = 60000;
const FILL_LIMIT = 32768;
// A freighter landing far from the world's middle: the number of the site she is laid down on, 180,000 blocks out, far
// past the sites the other checks reach, where Sable's physics rounds her position so that she cannot creep.
const FAR_SITE = 150;
// A freighter set to land at a pin in a lake: how far round the pin the water reaches, past where she may come down,
// in blocks; how long she is given to say she finds no ground, in ticks; and how near the pin she must hold, in blocks.
const LAKE_REACH = 70;
const LAKE_TICKS = 1600;
const LAKE_HOLD_WITHIN = 8;
// Her autopilot's landing picture, drawn in the middle of its tile on the screen: how big it is in the screen's own
// pixels, and what share of its brightness it keeps dimmed, at most, finding no ground.
const LAKE_PICTURE_SIZE = 16;
const LAKE_PICTURE_DIMMED = 0.85;
// A freighter that cannot finish landing: how long she is given to come down on the stone and give it up, in ticks; how
// long she is watched holding over it, in ticks; how far from the lake's edge the player stands, in blocks, left behind
// on the ground; how long she is given with nobody aboard to come down twice and let go, past her minute of waiting
// each time, in ticks; and how long after that she is watched not to take off again, past another of those minutes, in
// ticks, and how long the launcher is told to wait for that, in seconds.
const BALKED_WITHIN_TICKS = 1200;
const BALKED_HOLD_WATCHED_TICKS = 300;
const BALKED_STAND_OFF = 6;
const BALKED_UNATTENDED_TICKS = 5400;
const BALKED_UNATTENDED_WATCHED_TICKS = 1500;
const BALKED_UNATTENDED_WATCHED_SECONDS = 120;
// Ground higher under a freighter's nose: how far ahead of her middle her nose foot stands, in blocks, how far the
// higher ground reaches round it and how high it stands, in blocks, lower than her belly's clearance.
const BALKED_NOSE_FOOT_AHEAD = 19;
const BALKED_STEP_REACH = 3;
const BALKED_STEP_HIGH = 2;
// A freighter landing under a lagging server: how many milliseconds each server tick is made to take, and how many
// client ticks she is given to come down at that rate.
const BALKED_LAG_MILLIS = 150;
const BALKED_LAG_WITHIN_TICKS = 2400;
// A freighter's boarding ramp: how long it is given to come down by itself once she stands, in ticks; how far it
// falls, in blocks, which walking up it climbs, and how long the walk up from just past its foot takes, in ticks; how
// many blocks up from its foot someone stands on it to hold her take-off; and how long she is watched held, in ticks.
const RAMP_DOWN_TICKS = 300;
// A freighter's ramp with the server running behind: how far behind, in milliseconds a tick; how long a whole swing is
// given, in ticks, read only after it so that nothing stalls the client meanwhile; and the most its drawn count may go
// back or leap on from one frame to the next, in ticks: a frame never spans much more than a tick.
const RAMP_LAG_MILLIS = 70;
const RAMP_SWING_TICKS = 180;
const RAMP_MOST_BACK = 0.01;
const RAMP_MOST_LEAP = 2;
// What stands under a freighter's ramp: how far in under it from its foot, in blocks, and how long an animal is watched
// holding it, in ticks.
const RAMP_MOB_UNDER = -5;
const RAMP_ANIMAL_HOLDS_TICKS = 200;
// How far into its way down her ramp is pressed again to turn it round, in ticks: halfway through its swing.
const RAMP_HALFWAY_TICKS = 40;
// A freighter saved and joined again with her ramp down, then set to fly: how long her heat map is given to be built
// again before she sets off, in ticks; how long her ramp is given to fold and Sable to finish working out that she is
// whole, in ticks; how far round her a piece shed as a ship of its own is looked for, in blocks; and how tall the post
// of stone is that is stood on her crown and cut at its foot to see a real break still comes away, in blocks.
const RELOADED_SETTLE_TICKS = 200;
const RAMP_FOLD_FLOOD_TICKS = 260;
const SHED_LOOK_BLOCKS = 200;
const SHED_POST_BLOCKS = 8;
const RAMP_RISE = 7;
const RAMP_WALK_TICKS = 110;
const RAMP_STAND_UP = 3;
const RAMP_HELD_TICKS = 200;
// Walking onto a freighter's ramp on its way: how far in from the middle of its head's block to stand in her corridor,
// in blocks, a block and a half past its hinge; how long after its panel is pressed to walk out, in ticks, well into its
// swing; how long the walk out and the look after it are, and the walk back, in ticks; and how far over the ground
// whoever walked onto it must still be, in blocks, to be standing on it rather than fallen through to the ground.
const RAMP_HEAD_IN = 2;
const RAMP_INTO_SWING = 35;
const RAMP_ONTO_TICKS = 25;
const RAMP_HELD_LOOK = 20;
const RAMP_BACK_TICKS = 40;
const RAMP_ABOVE_GROUND = 2;
// Standing under a freighter's ramp as it comes down: how far out from its head, in blocks, where it would come down
// lower than a head; how long they stand there, in ticks, longer than it takes to come down; and how far to step
// aside from under it, in blocks, clear of its width.
const RAMP_UNDER_OUT = 9;
const RAMP_UNDER_TICKS = 150;
const RAMP_STEP_ASIDE = 4;
// A freighter's ramp meeting ground a block higher or lower at its foot: how many rows of deck its foot gains or loses
// for a block, which fall a block in two; how far out past its foot the changed ground reaches, in blocks, and how far
// past its foot walking up starts, on that ground; how far to either side of it the changed ground reaches, in blocks;
// how many rows in from its foot a block is put in the room over its deck, to stand in its way; and how long the lamp
// is given to turn red after it is pressed, in ticks.
const RAMP_FOOT_ROWS = 2;
const RAMP_FOOT_OUT = 10;
const RAMP_WALK_FROM = 4;
const RAMP_FOOT_ACROSS = 3;
const RAMP_BLOCKED_ROW = 4;
const RAMP_REFUSED_TICKS = 10;
// Standing where a freighter's ramp's foot runs out to lower ground: how far out past its whole run to stand, in rows.
const RAMP_FOOT_STAND = 2;
// Walking at a freighter's furniture: how far out to start, in blocks, and how long to walk, in ticks, enough to reach
// it; how far from its middle along the way walked the player must have been stopped, in blocks: her jump drive's
// rings, round its core, and not its column; the body of a player of her band, in front of the side of its stage, and
// not past its middle; and a sun lounger's raised back, walked at from closer in, behind its middle.
const BUMP_FROM = 3;
const BUMP_TICKS = 30;
const DRIVE_RINGS_OUT = 1;
const BAND_PLAYER_OUT = 0.4;
const LOUNGER_FROM = 2;
const LOUNGER_BACK_OUT = 0.6;
// How far the player must have walked at all, in blocks, for a stop to be the lounger's and not where they started.
const BUMP_MOVED = 0.5;
// Where on a console module of a freighter its desk is aimed at, as a point of its block from 0 to 1 across, up and
// along: the middle of its desk's top, which is drawn a little over four fifths of a block up at its back.
const DESK_TOP_AT = "0.5,0.8,0.5";
// Walking at a freighter's console module from her control head: how far out from its middle to start, in blocks,
// clear of the head; and how far past its middle, toward the head, the player may be stopped at most, in blocks: its
// desk begins just short of its middle, where it was its block's face, half a block out, that stopped them.
const DESK_FROM = 1.15;
const DESK_STOPS_WITHIN = 0.5;
// Walking at a freighter's parcel scanner from beside it, along its gantry's bar: how far out to start, in blocks; and
// how far from its middle the player must have been stopped at least, in blocks: by its gantry's post, standing a
// pixel and a half out past its block's face, half a block and a player's half width out.
const GANTRY_FROM = 2;
const GANTRY_POST_OUT = 0.85;
// Where a parcel scanner is used, as a point of its block from 0 to 1 across, up and along, drawn facing north: its
// screen, on its front low on its cabinet, under the crate on its belt.
const SCANNER_SCREEN_AT = "0.5,0.4,0";
// Dropped onto her laser cannon and her big satellite dish: how high over each to start, in blocks; and how high over
// each block the player must come to stand, lowest and highest, in blocks, with what margin: on the cannon's housing
// over its pedestal, and on the dish's bowl over its mount, from its hub's top to the quarter-block cells of the tipped
// bowl over it, which stand a little higher or lower under the player as it turns.
const DROP_FROM = 3;
const CANNON_HOUSING_TOP = [1.375, 1.375];
const DISH_BOWL_TOP = [1.75, 2.25];
const STANDS_WITHIN = 0.05;
// The mod's blocks with a state to aim at that is walked through, each for what it does: a curtain parts round whoever
// walks through it, a ball pit is waded into, a climbing rope is climbed from inside its block, up the zeppelin's
// shafts with no room beside it, and an airliner's open plug door and cockpit door leave their doorway clear, a sliver
// across it only to aim at and shut them. The flight deck's screens are still walked through where they
// only graze a block, an open fault (see docs/backlog.md), not a choice. Every other block is solid as drawn.
const WALK_THROUGH = ["cabin_curtain", "ball_pit", "climbing_rope", "plug_door", "cockpit_door", "cockpit_display"];
// Walking at a Spookie's hammock set down by hand from beyond either end of it: how far out from the middle of the
// post's block to start, in blocks, and how far out from it the post stops the player at least, in blocks: its post
// stands up the far side of its block, and with nothing there the player walked on to the sling, short of its middle.
const HAMMOCK_WALK_FROM = 2.5;
const HAMMOCK_POST_OUT = 0.6;
// Walking at a big satellite dish set down by hand, the game's time stood still, from each side at its foot: how far
// out from its middle to start, in blocks, and how far out from it the bowl stops the player at least on the side it
// leans out over furthest, in blocks; its mount alone stops them 0.74 out.
const DISH_WALK_FROM = 4;
const DISH_BOWL_OUT = 1.1;
// The slim souvenir models, the rocket's and the freighter's, each set down on the ground facing north with a point of
// its block, from 0 to 1 across, up and along, where the box all four once shared took a click though nothing of it is
// drawn there, and one where it is drawn; and how far off to click them from, in blocks, south of it.
const SOUVENIRS = {
  model_rocket: { empty: "0.2,0.6,0.5", solid: "0.5,0.5,0.5" },
  model_freighter: { empty: "0.5,0.72,0.5", solid: "0.5,0.45,0.5" },
};
const SOUVENIR_FROM = 2.5;
// How fast blocks are ticked at random while a freighter's ramp's deck is watched, as the game rule has it, and for
// how long, in ticks: fast enough that every block of it looks whether it is a leftover several times.
const FAST_RANDOM_TICKS = 4096;
const RAMP_TICKED = 60;
// How long after her ramp folds away its call button is pressed again, how long her take-off may be held before she
// calls it off, with a margin, and how long her ramp's lamp may then lag behind it, as it is only looked at now and
// then, in ticks.
const CALL_AGAIN_TICKS = 20;
const TAKEOFF_CALLED_OFF_TICKS = 700;
const RAMP_LAMP_LAGS_TICKS = 40;
// A freighter's top turret: how long the walk up her core's ladder takes, in ticks; how far up and ahead of her cannon
// a creeper is hung to shoot at, in blocks; and how long attack is held, in ticks, several bolts' worth.
const LADDER_CLIMB_TICKS = 100;
const TURRET_TARGET_UP = 10;
// Walking across a freighter's cannon barrels, resting toward her bow: where the walk starts from the middle of the
// cannon's block, out to its starboard side, up and toward her bow, in blocks, on her dome's crown beside the barrels;
// how long the walk lasts, in ticks, ample to cross the crown; and how far out to starboard of the cannon's middle the
// player is to stop, in blocks: stopped against the near barrel, a little more than 0.38 out, they stand some 0.68 out.
const BARREL_WALK_FROM = [1.7, 0.3, 1.2];
const BARREL_WALK_TICKS = 30;
const BARREL_STOPPED_OUT = 0.5;
// How many of the nine blocks of stone shot at may still stand once her cannon's bolts have blasted it.
const TURRET_BLASTED_LEFT = 5;
const TURRET_TARGET_AHEAD = 10;
const TURRET_FIRE_TICKS = 30;
// How far down the gunner looks at her hull, in degrees, past the fifteen her cannon's barrels go, and how long the
// trigger is pulled there, in ticks; how long it is held to overheat the cannon, past twenty bolts at four a second,
// how long after that it is watched holding its fire, short of its cooling down, and how long it is let cool, in ticks.
const TURRET_LOOK_DOWN = 40;
const TURRET_HELD_TICKS = 20;
const TURRET_OVERHEAT_TICKS = 110;
const TURRET_HOT_WATCHED_TICKS = 20;
const TURRET_COOLED_TICKS = 60;
// How far a creeper's middle stands over its feet, in blocks, for the gunner to look at it from the sight: the cannon
// aims at what the sight looks at; and how soon after sneaking the gunner's own view must be back, in ticks.
const CREEPER_MIDDLE = 0.85;
const VIEW_BACK_TICKS = 2;
// A freighter's rocket pods: how many rockets they hold; how long use is held, past her four launches six ticks apart;
// how long the rockets are given to fly out and go off; how long after the last launch the sight must show the next
// rocket filling, and how long her pods may take to load all four again, with a margin, in ticks.
const ROCKETS = 4;
const ROCKET_SALVO_TICKS = 30;
const ROCKET_FLIGHT_TICKS = 40;
const ROCKET_LOAD_STARTS_TICKS = 40;
const ROCKET_RELOAD_TICKS = 220;
// How far ahead of her cannon, toward her bow, and how far under it her deck's cover is where stone is set on it to be
// shot at, in blocks: as low as her cannon's barrels reach; how many blocks of stone are set there, a row of three two
// high.
const ROCKET_DECK_AHEAD = 13;
const ROCKET_DECK_UNDER = 3;
const ROCKET_DECK_STONE = 6;
// A freighter's cannon fired at another ship: how far east of her the other freighter is laid down, in blocks, clear of
// her rim and in her cannon's reach, and how high over her control head she is flown to hover; how far in from her
// near side and how far up her hull the cannon is aimed, in blocks; how long each hit is watched, in ticks; how much a
// bolt must change her speed at least, in blocks a second, and how many times more a rocket must tilt her, four times
// as hard a push as a bolt's but both blurred by her hovering; how long a burst of bolts is held, in ticks,
// and how fast and how far it may shove her at most, in blocks a second and blocks: a rocket's worth and what she lets
// up of it in those seconds, with the push of the bolts' own blasts, where with a cap of nearly twice that nine bolts
// shoved her at 2.4 blocks a second and eight blocks off; and how long she is then given to settle, in ticks, how
// slowly she must then drift, in blocks a second, and how near the height she hovered at she must be, in blocks.
const HIT_TARGET_ABEAM = 75;
const HIT_TARGET_UP = 40;
const HIT_AIM_IN = 3;
const HIT_AIM_UP = 0.4;
const HIT_WATCH_TICKS = 40;
const BOLT_SHOVE_LEAST = 0.15;
const ROCKET_ROCKS_MORE = 1.5;
const HIT_BURST_TICKS = 60;
const HIT_BURST_FASTEST = 2.5;
const HIT_BURST_FARTHEST = 6;
const HIT_SETTLE_TICKS = 200;
const HIT_SETTLED_SPEED = 0.8;
const HIT_SETTLED_WITHIN = 3;
// The laser crossbow: how far in front of the player its targets stand, in blocks, and how far down the player looks at
// them, in degrees; how long a shot at one is given to fly there and strike, in ticks; a creeper's and a cow's full
// health, in half hearts; how long it is left to cool after venting, in ticks; how long use is held down for a burst,
// in ticks, how often the game repeats a held use, in ticks, and how many shots a burst fires at least before it vents.
// Its bolt: how soon after a shot it is looked for and how much later again, in ticks; how long it must have shown by
// then at least, in ticks, more than the blink of four it once was; how near the eye it must leave from, in blocks, as
// the crossbow's muzzle is; and, by the time it is first looked for, how far its tail must have flown from there at
// least and how long it may be drawn at most, in blocks, a short bolt and not a ray from the muzzle.
const CROSSBOW_TARGET_AHEAD = 6;
const CROSSBOW_AIM_DOWN = 8;
const CROSSBOW_STRIKE_TICKS = 20;
const CREEPER_HEALTH = 20;
const COW_HEALTH = 10;
const CROSSBOW_COOLED_TICKS = 80;
const CROSSBOW_BURST_TICKS = 120;
const CROSSBOW_REPEAT_TICKS = 4;
const CROSSBOW_LEAST_BURST = 10;
const BOLT_LOOKED_FOR_TICKS = 3;
const BOLT_LATER_TICKS = 15;
const BOLT_SHOWN_TICKS = 8;
const BOLT_NEAR_EYE = 1.5;
const BOLT_FLOWN_LEAST = 3;
const BOLT_LONGEST = 2.5;
// The laser crossbow aboard a flying freighter: how fast she flies along, in blocks a second, what share of that she
// must have come up to, and how far up the player looks to fire, in degrees, clear of her controls, which a click
// would work instead of firing.
const CROSSBOW_FLIGHT_SPEED = 20;
const CROSSBOW_FLIGHT_UP_TO = 0.8;
const CROSSBOW_FLIGHT_AIM_UP = 80;
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
// Her canopy seen from inside: where on the screen the sky ahead is looked at through it from the pilot's seat, as
// frame fractions from the top left, between her canopy's ribs; how much darker it must look once her dimmer darkens
// the canopy, as a share of how bright it was clear; and how long the dimmer and a drawn frame are given, in ticks.
const CANOPY_SKY = "sky=0.62,0.05,0.72,0.18";
const CANOPY_DIMMED_SHARE = 0.6;
const CANOPY_SETTLE = 20;
// How far out ahead of her canopy's lining her cockpit is looked at through her front window from outside, in blocks.
const CANOPY_OUTSIDE = 8;
// Her canopy's dimmer: how far its face stands out from her wall, in blocks, and where its darkest and its clear button
// are on it, so far along it toward its side a quarter turn clockwise from the way it faces and up from its foot, in
// blocks.
const DIMMER_FACE = 0.12;
const DIMMER_DARKEST = [0.8, 0.15];
const DIMMER_CLEAR = [0.2, 0.75];
// Rain over her canopy, seen from her pilot's seat: how long the rain is given to set in, in ticks; how much bluer than
// red or green a pixel must be, 0 to 255, to be taken for rain; and how many such pixels across the whole frame are left
// to her hull's own splashes, seen past her glass. With rain falling in her cockpit and splashing inside her glass there
// were two thousand and more; with neither, about a hundred.
const RAIN_SETS_IN = 120;
const RAIN_BLUE = 50;
const RAIN_PIXELS_MOST = 600;
// Her console: its modules round her seats, each a slice of its ring, by kind; and the kinds a player can set up by hand.
// Her slam button takes the toggles' place, and her spin button her engine gauges', as the server config's
// freighter.slamLanding and freighter.spin have her built by default.
const CONSOLE_SLICES = { console_readouts: 1, console_boost: 1, console_climb: 1, console_scanner: 1, console_jump: 1, console_slam: 1,
  console_spin: 1 };
const CONSOLE_KINDS = ["console_readouts", "console_scanner", "console_engines", "console_switches", "console_boost", "console_jump", "console_climb",
  "console_slam", "console_spin"];
// Airframe fairings put down by hand: how far off the cell the player stands to put one down, in blocks, and how far
// apart the corners are laid along the site, in blocks.
const FAIRING_STAND_OFF = 2.5;
const FAIRING_APART = 6;
// A transponder's pictures for space, as it saves them, and how long a pick is given to reach the server, in ticks.
const SPACE_PICTURES = ["ROCKET", "FREIGHTER", "FIGHTER", "STATION", "SATELLITE", "PLANET", "UFO", "ASTEROID"];
const PICK_TICKS = 5;
// Her boost: the speed she cruises at before it, in blocks a second; how long she is watched boosting, in ticks; how
// much faster she must go meanwhile at least, in blocks a second; how long she is given after it to slow back near her
// speed, in ticks, and how near, in blocks a second.
const BOOST_CRUISE = 10;
const BOOST_WATCH_TICKS = 160;
const BOOST_GAIN = 10;
const BOOST_SETTLE_TICKS = 600;
const BOOST_SETTLED_WITHIN = 3;
// Her climb boost: the height it gains, in blocks; how long she is watched climbing, in ticks, and the share of that
// height she must gain meanwhile at least; how far she may drift off her spot as she climbs, in blocks; and how long
// after she is watched holding it, in ticks, and how near it she must stay, in blocks.
const CLIMB_BOOST_GAIN = 300;
const CLIMB_BOOST_WATCH_TICKS = 600;
const CLIMB_BOOST_SHARE = 0.8;
const CLIMB_BOOST_DRIFT = 10;
const CLIMB_BOOST_HOLD_TICKS = 200;
const CLIMB_BOOST_HELD_WITHIN = 15;
// Her slam landing: how high over where she was built she hovers before it, in blocks, high enough that she reaches her
// fastest; how long she is watched coming down from the button, in ticks; how fast she must sink at the
// fastest, in blocks a second, against the three she sinks at most landing; how fast at most she may touch down, in
// blocks a second, under the four at which Sable's ship breaks the fragile blocks it strikes; how slowly she may sink
// at the least once at her fastest, in blocks a second, below which she stopped to wait for her legs, until her feet
// are so near the ground, in blocks, where she slows to touch down; how near the ground her feet must be to have touched
// it, in blocks; how long after she is off her ramp is given to come down, in ticks; and
// how far the player may end up from where they stood at her console, along the ground, or from her deck's height, in
// blocks, and still be aboard there.
// A freighter far past the edge of space: the highest her autopilot takes her, in blocks; where her climb button is
// pressed from, over the edge of space, how long she is watched climbing, in ticks, and how high she must have climbed
// by then, in blocks; how long she is given to settle after being moved, in ticks; how far under the top she is set
// going there, in blocks, how long she is given, in ticks, and how near it she must hold, in blocks.
const HIGHEST_ALTITUDE = 2500;
const SPACE_CLIMB_FROM = 1100;
const SPACE_CLIMB_WATCH_TICKS = 400;
const SPACE_CLIMB_GAINED = 250;
const SPACE_SETTLE_TICKS = 100;
// Her slam from far out in space: how many seconds she is watched coming down, a second at a time, and how fast she must
// drive down at the fastest, in blocks a second, far past the sixteen she drove at before from any height.
const SPACE_SLAM_WATCH_SECONDS = 150;
const SPACE_SLAM_FASTEST = 40;
const SPACE_TOP_UNDER = 40;
const SPACE_TOP_TICKS = 600;
const SPACE_HELD_WITHIN = 5;
// Space weather: how high a freighter hovers for it, in blocks, over the y 1,500 it blows from; for each kind, what her
// computer says of it, how a strike sounds, how long it lasts from the alarm to the all-clear, in ticks (its warning,
// its time on her and its lingering in SpaceWeather), and how much a strike must change her speed at least, in blocks a
// second; how long she is given to settle after, in ticks, and then how slowly she may drift, in blocks a second, how
// far she may lean, in degrees, and how near the height her autopilot holds she must be, in blocks; and how many times
// harder a rock must shove her than a meteor.
const WEATHER_HEIGHT = 1600;
const WEATHER = {
  meteor_shower: { voice: "skyliners:freighter_computer_meteors", strike: "skyliners:space_meteor_strike", ticks: 760, shoveLeast: 1.5 },
  asteroid_storm: { voice: "skyliners:freighter_computer_asteroids", strike: "skyliners:space_asteroid_strike", ticks: 960, shoveLeast: 5 },
};
const WEATHER_SETTLE_TICKS = 600;
// The longest any space weather lasts, in ticks: a giant asteroid, alarm to all-clear, its last rocks flown past.
const WEATHER_TICKS_MOST = 1100;
const WEATHER_SETTLED_SPEED = 1;
const WEATHER_SETTLED_TILT = 3;
const WEATHER_SETTLED_WITHIN = 5;
const ROCK_HARDER = 1.5;
// Shooting at space rocks from a freighter's laser cannon: how near the cannon's sight a rock flying in must have come
// before it is aimed at, in blocks, inside where rocks become real bodies; what a block of rock weighs, in Sable's
// units; how far ahead of a rock the sight is aimed for one bolt, in ticks of its flight, how long after aiming that
// bolt is fired, how heavy a rock must be to be aimed at, how many rocks are tried and how long is waited between them,
// in ticks; how long the sweep is given to run, in ticks; what a body must weigh less than to be a rock; how far under
// level a rock may lie for the cannon to look at it, in degrees; how near her middle a rock that strikes her may get
// before it has passed her hull, in blocks; how far ahead of a rock the sight is aimed for rockets, how many tries it
// gets at shooting one down, and how long its pods are given to load again between tries, in ticks; how long after
// aiming the rocket is launched, inside the 30 ticks the sight's lock on a rock holds, and how long it is given to
// strike, in ticks; and how long a bout of space weather is waited for to blow over at most, in ticks, with its last
// rocks flying in.
const ROCK_AIM_WITHIN = 130;
const ROCK_BLOCK_WEIGHT = 0.25;
const ROCK_BOLT_AHEAD = 10;
const ROCK_BOLT_AFTER = 8;
const ROCK_BOLT_LEAST = 2;
const ROCK_BOLT_TRIES = 8;
const ROCK_BOLT_BETWEEN = 20;
const ROCK_SWEEP_TICKS = 260;
const ROCK_LIGHTER_THAN = 1000;
const ROCK_LOWEST = 10;
const ROCK_NEAREST_MIDDLE = 5;
const ROCK_AIM_AHEAD = 14;
const ROCK_AIM_TRIES = 3;
const ROCK_RELOAD_TICKS = 180;
const ROCK_LAUNCH_AFTER = 18;
const ROCK_ROCKET_TICKS = 80;
// How far from her middle at least a rock of an asteroid storm becomes a real body, in blocks, rocks once becoming real
// only 150 out; and how far from the player at her cannon her client holds one at least.
const FAR_ROCK_MADE = 200;
const FAR_ROCK_SEEN = 180;
// A storm's rocks flying on after it: how long after its all-clear they are looked for, in ticks, past the three
// seconds its rocks once lasted; and how long they are given to fly out of sight at most, in ticks: the server lets the
// last go two minutes on, and one that drifted off her is drawn on until it is out of sight.
const FLY_ON_LOOK_AFTER = 100;
const FLY_ON_TICKS_MOST = 4200;
// How long a freighter is watched for a solar flare's blackout to begin and then to end, in ticks.
const BLACKOUT_WITHIN = 500;
// How long a solar flare's blackout lasts by default and as the check sets it, longer, in seconds; how far either may
// be seen to be off, in ticks, looking once a second; how long into the longer one to look at her screens, in ticks, by
// when she has fallen out of the weather; and how long after it she is to be climbing again, in ticks.
const TICKS_A_SECOND = 20;
const BLACKOUT_SECONDS = 5;
const BLACKOUT_SET_SECONDS = 12;
const BLACKOUT_OFF_BY = 50;
const BLACKOUT_SCREENS_LOOK = 200;
const BLACKOUT_CLIMBS_WITHIN = 1200;
const ROCK_WEATHER_TICKS = 1300;
// A giant asteroid: how long it is given to land, from the alarm, in ticks; how much its strike must change her speed
// at least, in blocks a second, and how many times an ordinary rock's at least; how fast it must set her turning at
// least, in degrees a second; how long a server tick may take at most through it, on average and at the worst, in
// milliseconds, so a tumbling ship never stalls the game.
// How far a giant asteroid's middle may be from half its size off where it lands, in blocks: a tick or so of its flight.
const GIANT_FRONT_WITHIN = 4;
// How long before a giant should land its strike is watched from, in ticks, as its body's bulges can reach her first,
// and how many samples are taken of her then, two ticks apart.
const GIANT_STRIKE_LEAD_TICKS = 30;
const GIANT_STRIKE_SAMPLES = 40;
const GIANT_TICKS = 1100;
const GIANT_SHOVE_LEAST = 15;
const GIANT_HARDER = 2;
const GIANT_SPIN_LEAST = 20;
const GIANT_TICK_MS = 50;
const GIANT_WORST_TICK_MS = 250;
// A strike's shove and spin: how many ticks before it lands its ship is first sampled, and how many samples are taken,
// two ticks apart; and how long a giant's tumble is watched for the server stalling, in ticks.
const STRIKE_LEAD_TICKS = 4;
const STRIKE_SAMPLES = 15;
const GIANT_TUMBLE_TICKS = 200;
// A giant cracked by rockets: how heavy a rock coming at her must be to be taken for the giant, and a piece for a piece
// of it rather than an ordinary rock, in Sable's units; how many times rockets are launched at it at most; how many
// times it is looked at after launching them, every four ticks, its pieces as last seen being judged; how fast its two
// heaviest pieces must fly apart at least, in blocks a second, and how far off the course it held the heavier must go
// at least, in degrees.
const GIANT_MASS_LEAST = 1000;
const GIANT_PIECE_LEAST = 100;
const GIANT_CRACK_TRIES = 2;
const GIANT_CRACK_SAMPLES = 20;
const GIANT_APART_LEAST = 10;
const GIANT_TURNED_LEAST = 5;
// The most of a giant a piece cracked off it can be, as a share: a crack never runs far enough off its middle to leave
// more than three quarters on one side.
const GIANT_PIECE_SHARE_MOST = 0.8;
// Space weather on the rocket: how long she is given to climb to it, in ticks; how much a rock must change her speed
// and tip her at least, in blocks a second and degrees; how far from the height she waits it out at she may be thrown
// meanwhile, in blocks; and, a while after, how upright she must stand again, in degrees.
const ROCKET_WEATHER_UP_TICKS = 2400;
const ROCKET_SHOVE_LEAST = 3;
const ROCKET_TIPPED_LEAST = 4;
const ROCKET_HELD_WITHIN = 10;
const ROCKET_UPRIGHT_WITHIN = 5;
const SLAM_HEIGHT = 90;
const SLAM_WATCH_TICKS = 300;
const SLAM_FASTEST_LEAST = 10;
const SLAM_TOUCHDOWN_MOST = 3;
const SLAM_PAUSED = 1;
const SLAM_CUSHIONED = 3;
const SLAM_TOUCHED = 0.6;
const SLAM_RAMP_TICKS = 200;
const SLAM_ABOARD_WITHIN = 3;
// Her hyperspace jump: how far off along the ground her pin is, in blocks, and how tall the pillar standing on it, in
// blocks over the ground; how long she is given from the button to come out there, in ticks; how near the pin her
// middle must come out, and how far her lowest block must keep over the pillar's top, in blocks.
const JUMP_PIN = 2000;
const JUMP_PILLAR = 70;
const JUMP_WITHIN = 600;
const JUMP_NEAR = 8;
const JUMP_CLEAR = 10;
// The longest a server tick may take while she jumps, in milliseconds: loading the land at her pin all at once held one
// for nearly four seconds.
const JUMP_LONGEST_TICK = 1500;
// The longest a frame of the game may take while she jumps, in milliseconds: Sable carrying the player aboard her the
// whole jump in a tick, searching every block along it, held the picture for seconds and once for over half a minute,
// the flash as she came out frozen on the screen; a jump's busiest frame took 20 to 90 ms on the Mac without that.
const JUMP_LONGEST_FRAME = 1000;
// Her jump watched from the ground: how far ahead of her the pin is set, in blocks along the ground, far enough for her
// to jump and near enough for one watcher to see both ends; how far aside from her course the watcher stands, halfway
// along it; and the least they must see of it: a flash at her middle and six round it at each end, a hundred specks of
// glitter at each end, and her streaks, rushing in to her and shooting off ahead as she goes and rushing in to where
// she comes out and rings of them closing in on where she was and bursting out round her where she is, some five
// hundred in all. How near the pin her coming out is heard, and how far from it her going is, in blocks along the
// ground: she was at least a hundred and twenty-eight off it as the button was pressed and flew some fifty on toward it
// before she went. And how many times her own length she must be drawn at most as she goes and as she comes out, out of
// the six she stretches to, and in how many frames at least, out of the eight or more her snapping back out lasts even
// at twenty frames a second.
const SEEN_PIN = 200;
const SEEN_ASIDE = 50;
const SEEN_FLASHES = 14;
const SEEN_GLITTER = 180;
const SEEN_STREAKS = 400;
const SEEN_IN_NEAR = 16;
const SEEN_OUT_OFF = 64;
const SEEN_STRETCH = 3;
const SEEN_STRETCH_FRAMES = 4;
// Her jump heard from far off: the height the listener stands at, on a block put down at the top of the world straight
// up from halfway along her course, so the land round them, and she with it, stays loaded; how far off both ends they
// must be, in blocks; how fast sound travels, in blocks a tick, and how much later than that a client hears the bangs,
// as it shows her later than the server has her (HyperspaceWakes.SHOWN_LATER), in ticks; and how many ticks either way
// of that each bang may be heard, as the client's ticks and the server's do not keep step.
const HEARD_PERCH = 320;
const HEARD_FAR_LEAST = 300;
const HEARD_SOUND_SPEED = 17.15;
const HEARD_SHOWN_LATER = 4;
const HEARD_SLACK = 3;
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
// Her sticker map: how many blocks it has; how far round the player the land under a hovering freighter is turned into
// another biome, and from how far under the ground to how far over it, in blocks, and how many blocks a command may
// change at once for that; how long after she is told to land her fanfare may be heard, and her computer's line, which
// waits for her cockpit to fall quiet, in ticks; and how long stickers put up together take to pop up, in ticks.
const STICKER_PARTS = 8;
const STICKER_BIOME_REACH = 48;
const STICKER_BIOME_UNDER = 4;
const STICKER_BIOME_OVER = 24;
const STICKER_FILL_MOST = 2000000;
const STICKER_HEARD_TICKS = 100;
const STICKER_VOICE_TICKS = 600;
const STICKER_POPS_TICKS = 40;
// Her tractor beam: how long something takes at most to ride it up onto its pad or down to the ground, in ticks; how
// near the pad's top a thing's feet must be to stand on it, in blocks; how far aside the player steps to leave the
// beam, in blocks; how long a zombie under it is watched, in ticks; and a player's full health, in half hearts.
const BEAM_RIDE_TICKS = 100;
const BEAM_ON_PAD = 0.8;
const BEAM_STEP_ASIDE = 4;
const BEAM_WATCH_TICKS = 60;
const FULL_HEALTH = 20;
// Her tractor beam as she hovers: how high she is set to hover over where she stood, in blocks (she hovers at least so
// high, and higher where she keeps clear of the ground round her); how long a ride down to the ground or up onto her
// pad takes at most, in ticks; and how near the ground the player's feet must be to be down.
const BEAM_HOVER = 24;
const BEAM_FLIGHT_RIDE_TICKS = 300;
const BEAM_DOWN_WITHIN = 0.5;
// Tractor beams set up by hand: how high the stone they hang under is over the ground, in blocks; how far the pad is
// from the hung one and the standing one from both, in blocks, past a pad's reach; how high a player standing on the
// standing one floats at least, in blocks, and how long they are given to, in ticks.
const BEAM_HAND_HIGH = 5;
const BEAM_HAND_PAD_OUT = 3;
const BEAM_HAND_LIFT_OUT = 24;
const BEAM_HAND_FLOATS = 8;
const BEAM_HAND_FLOAT_TICKS = 100;
// How long a lit beam takes at most to see what lies under it again, in ticks: TractorBeamBlockEntity.LOOK_EVERY, and
// a little more.
const BEAM_LOOKS_AGAIN = 15;
// Her running repairs: how many fault spots she is built with at least; how long a ship flies between faults for the
// play-test, in ticks, and how long a fault is then given to go off, how long she is watched on the ground for one
// that should not, and how long a fixed fault's star, chime and computer's line are given, in ticks.
const REPAIR_SPOTS_LEAST = 3;
const REPAIR_TEST_EVERY = 100;
const REPAIR_FAULT_WITHIN = 400;
const REPAIR_GROUND_WATCH = 300;
// How long a fault is listened to in flight, and how many noises it may make meanwhile, in ticks; and how long parked.
const REPAIR_NOISE_WATCH = 240;
const REPAIR_NOISES_MOST = 1;
const REPAIR_PARKED_WATCH = 500;
const REPAIR_FIXED_WITHIN = 40;
const REPAIR_VOICE_WITHIN = 300;
// A gravity switch: how far before it the player and a dropped cookie stand, in blocks; how long they are given to
// rise off the floor and how high over it they must then float, in ticks and blocks; how long jump is held and how much
// higher the player must have floated for it, in ticks and blocks; how long they are watched afterwards, still off the
// floor, in ticks; and how long they are given to come back down once gravity is back on, in ticks, and how near the
// floor they must then stand, in blocks.
const ZERO_G_STAND = 3;
const ZERO_G_COOKIE = 5;
const ZERO_G_RISE_TICKS = 60;
const ZERO_G_RISEN = 0.4;
const ZERO_G_JUMP_TICKS = 10;
const ZERO_G_JUMPED = 0.3;
const ZERO_G_WATCH_TICKS = 60;
// How many more times a weightless room's dropped cookie is looked at for having floated up, before it counts as not.
const ZERO_G_COOKIE_LOOKS = 3;
const ZERO_G_DOWN_TICKS = 200;
const ZERO_G_DOWN_WITHIN = 0.3;
// A gravity switch set by hand: the room built round it, how far out from its middle its walls stand and how many rows
// tall it is inside, in blocks.
const ZERO_G_ROOM_OUT = 4;
const ZERO_G_ROOM_ROWS = 6;
// A gravity switch aboard a hovering freighter: how high she is set to hover over where she stood, in blocks.
const ZERO_G_HOVER = 20;
// A round floor window: how many blocks it has at least; how long its iris is given to open or shut, in ticks; how
// many loungers and palms her beach has at least; and how far past the foot of its shaft her belly's glass is looked
// for, in blocks.
const FLOOR_WINDOW_BLOCKS = 60;
// Round floor windows set by hand into faces of each breadth, a floor's or a wall's: the largest a window comes, in
// blocks across; how wide each face is, in blocks, the size the window should take there, in blocks across, and how
// many blocks that is; how far apart the faces stand
// and how far from each the player stands, in blocks; how tall a wall is, in blocks; and how many blocks round the
// player a window's blocks are counted in.
const FULL_WINDOW_ACROSS = 9;
const SIZED_WINDOWS = [[13, FULL_WINDOW_ACROSS, 69], [3, 3, 9], [2, 2, 4]];
const SIZED_FACES_APART = 20;
const SIZED_STAND = 3;
const SIZED_WALL_ROWS = 13;
const SIZED_COUNT_REACH = 16;
// A round floor window laid to a floor's very edge, seen from beside it: how far out the camera stands, in blocks, how
// far round the middle of the edge block's side the picture is measured, in blocks, and how much redder than blue the
// floor's gold side is at least, where the window's grey-blue well or the sky would show through a side left out.
const EDGE_CAMERA_OUT = 3.5;
const EDGE_MEASURE = 0.3;
const EDGE_GOLD_RED = 1.5;
// Windows set by hand: each kind, with how many blocks wide and tall its opening is and how many pieces it has; how far
// apart their walls stand, how far in front of a wall the player stands and how far round a window its pieces are
// looked for, in blocks.
const HAND_SET_WINDOWS = [["oval_window", 3, 4, 12], ["porthole", 1, 1, 1]];
const WINDOW_WALLS_APART = 8;
const WINDOW_STAND_OFF = 2.5;
const WINDOW_REACH = 8;
// A freighter's portholes: how many she has at least, along both prongs and both sides of her cockpit's tube, and how
// much of the sky's light reaches the block inside each at least, as a block's light.
const FREIGHTER_PORTHOLES = 8;
const PORTHOLE_DAYLIGHT = 10;
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
// A laser cannon's rocket thrown at a player: how far behind them it is summoned, in blocks; how many samples of where
// they are are taken, two ticks apart; how high or far it must throw them at least, in blocks; and how long they are
// given to come down, in ticks.
const THROW_ROCKET_OFF = 6;
const THROW_SAMPLES = 40;
const THROWN_HIGH = 6;
const THROWN_FAR = 8;
const THROWN_LANDS_TICKS = 300;
// How high off the ground a player flying in creative mode hangs for a rocket flown at them, in blocks.
const THROWN_FLYING_UP = 8;
// How far her gunner's seat may stand from straight under her laser cannon, across or along, in blocks.
const GUNNER_SEAT_OFF = 1;
// Stepping back off the top of her core's ladder: how long the back key is held, in ticks, and how far under her gunner's
// platform's top they may stand and still be on it, in blocks.
const STEP_OFF_TICKS = 20;
const PLATFORM_SLACK = 0.05;
// Coming aboard a freighter on someone's birthday: how far from her bandstand to stand to be off her, in blocks; and how
// long the greeting may take to come once aboard, in ticks.
const BIRTHDAY_AWAY = 100;
const BIRTHDAY_WITHIN = 40;
// How long to stay off her before coming aboard again, in ticks: past the time off that counts as coming aboard anew.
const BIRTHDAY_OFF_TICKS = 120;
// Lifting off with them aboard: how long to give her to be assembled and climb, in ticks, and how high she must be by
// then to have taken off, in blocks.
const BIRTHDAY_LIFT_TICKS = 300;
const BIRTHDAY_LIFTED = 5;
// How far back from a prong's tip, in blocks, an LED strip is looked for, and within how far of there; and how far
// round her control head her cockpit's floor is looked over for carpet, in blocks.
const TIP_STRIPS_BACK = 3;
// A freighter's console modules, any of which a player walks at from her control head to find its desk solid.
const CONSOLE_MODULES = ["console_scanner", "console_boost", "console_spin", "console_jump", "console_slam", "console_climb", "console_readouts"];
// Walking off a zeppelin's upper deck at its front corner: how far out past the end of the wall with the paintings,
// and how far back from the wall's plane, to the middle of the deck's second row, the walk starts, in blocks; how long
// it lasts, in ticks, ample to reach the edge; and how far under the deck the player may be seen, in blocks, short of a
// fall.
const DECK_CORNER_OUT = 4;
const DECK_EDGE_BACK = 2;
const DECK_WALK_TICKS = 20;
const DECK_DROP_SLACK = 0.5;
// How far round the middle of a zeppelin's techno room its dark spots are looked over, in blocks: the whole room; and
// how far along her, north of where she was laid, it is looked for from, and how far up, in blocks: laid down she
// stretches away north, and it hangs under her gangway towards her stern, further than a look reaches from her nose.
const CLUB_LOOK_ALONG = 200;
const CLUB_LOOK_UP = 30;
const CLUB_LOOK_RADIUS = 8;
// Fittings that only hang from a ceiling or are fixed to a wall, and the mount each goes up on; the awning's canopy
// goes up against a wall, never on top of a block.
const MOUNTED_ONLY = [["light_strip", "wall"], ["fairy_lights", "wall"], ["grow_light", "ceiling"], ["horn_cord", "ceiling"], ["ships_plaque", "wall"],
  ["searchlight", "ceiling"], ["floodlight", "ceiling"], ["banner_cleat", "wall"], ["awning", "wall"]];
// An orrery's planetarium: how high over the ground a ceiling is put up to hang one under by hand, how far under the
// orrery the glass floor of the room is, and how far off it the orrery is looked at from, in blocks; how far up and
// back from a rocket's site her orrery is looked for from, as she stands some 150 blocks tall, and how far under it her
// cockpit is looked up at it from, in blocks; and how long it is looked at before its sky is read, in ticks, past when
// its dome is first found.
const ORRERY_CEILING_UP = 12;
const ORRERY_FLOOR_UNDER = 2;
const ORRERY_LOOK_FROM = 4;
const ORRERY_ROCKET_LOOK_UP = 100;
const ORRERY_ROCKET_LOOK_BACK = 26;
const ORRERY_COCKPIT_DOWN = 9;
const ORRERY_LOOK_TICKS = 20;
// Walking into a hoop on its stand: how far from it the walk starts, in blocks, on the ring's side; how long it lasts,
// in ticks, ample to cross it; and how high the player may rise on its base, in blocks, short of a step.
const HOOP_RUN_UP = 3;
const HOOP_WALK_TICKS = 30;
const HOOP_STEP_MOST = 0.3;
const TIP_STRIPS_NEAR = 4;
const COCKPIT_FLOOR_NEAR = 5;
// How far behind her control head her pilots' seats stand, in blocks, where her cockpit's floor is looked over from.
const SEATS_BEHIND_HEAD = 2;
// How many blocks of counter her club's bar has at least.
const CLUB_COUNTERS = 5;
const BAND_AWAY = 40;
const BAND_TICKS = 60;
// A freighter's deck robots: how many she is built with, how far in front of one the player walks up to, and how far
// off they start, in blocks; and how long its hello and its trill are given to be heard, in ticks. A block in front of
// one is its room's floor; two in front was the wall it faces, and set down half in it the player was pushed out only
// while the LED strip along its foot could be walked through.
// How far in front of her floor window's panel the player stands to press it, in blocks: on the floor before it, short
// of the wall behind, which half a block further set them down half in it.
const PANEL_FROM = 1;
const FREIGHTER_ROBOTS = 2;
const ROBOT_FRONT = 1;
const ROBOT_AWAY = 30;
const ROBOT_HEARD_TICKS = 80;
// A freighter's outside: how many of each piece of it she is built with at least, by block id, her fittings, her
// plates and her lights; the outside fittings put up by hand on a floor, a wall and under a ceiling; how far south of where she was laid down the stand they are
// put up on is, how far apart along it they go, and how high it is, in blocks; and how far above her control head she
// is sent to look for any of her outside left on the ground, in blocks.
const EXTERIOR_PIECES = [
  ["big_satellite_dish", 1], ["satellite_dish", 2], ["antenna_mast", 6], ["sensor_dome", 3], ["heat_sink", 12], ["exhaust_vent", 7],
  ["vent_grille", 30], ["hull_machinery", 20], ["hull_floodlight", 6],
  ["hull_plate_dark", 40], ["hull_plate_riveted", 20], ["hull_plate_patched", 10], ["hazard_plate", 20],
  ["hull_light_strip", 60], ["hull_light_band", 60], ["nav_light", 7],
];
// How many blocks of her own Freighter Hull a freighter's skin has at least, none of it the zeppelin's cover.
const FREIGHTER_HULL_LEAST = 500;
const FITTINGS_PUT_UP = [
  "satellite_dish", "big_satellite_dish", "antenna_mast", "sensor_dome", "heat_sink", "exhaust_vent", "vent_grille", "hull_machinery",
  "hull_floodlight", "hull_light_strip",
];
const STAND_AWAY = 24;
// How many of a freighter's passenger screens the screens check tries pressing at most.
const SCREENS_TRIED = 6;
const STAND_APART = 3;
const STAND_HIGH = 5;
// The pieces of a freighter's outside that stand against her hull rather than in it, the most of each looked for, and
// how many blocks are read in one script; and the way each direction points, as a block's facing names it.
const RESTING_PIECES = [...FITTINGS_PUT_UP, "nav_light", "laser_cannon"];
const RESTING_MOST = 400;
const READS_AT_ONCE = 100;
const POINTING = { up: [0, 1, 0], down: [0, -1, 0], north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0] };
// How far out from a freighter's axis her rooms' walls and what hangs on them reach at most, in blocks, past which her
// cockpit's tube and her prongs' tunnels lie; and how many blocks of posters her gallery has at least: an earthrise and
// a black hole, four by three, and the solar system, five by two.
const ROOMS_REACH = 22.5;
const GALLERY_POSTER_BLOCKS = 34;
// A freighter's prongs: how many thrusters stand in their tips, her two retros and her two nose jets, the furthest of
// her thrusters from her middle; how far back toward her middle from one, down the tunnel, the player stands to aim at
// it, in blocks; how far round a point down each tunnel it is looked over for unlit spots, and how far back from her
// retro that point is, in blocks.
const PRONG_THRUSTERS = 4;
const AIM_BACK = 3.5;
const PRONG_LIT_REACH = 18;
const PRONG_LIT_BACK = 12;
// Her smugglers' hold: how far before the secret panel the player stands to knock and to press the rivet, and how far
// behind it in the den, in blocks; how long the rivet is given to chime once the player is near it, and how long the
// panel takes to shut by itself once nobody is in its doorway, in ticks.
const PANEL_STAND = 3;
const DEN_STAND = 1.5;
const CHIME_WITHIN = 200;
const PANEL_SHUTS = 230;
// Her port prong's lookout: how many blocks of seats it has at least, one seat by a porthole and three in the nook at
// its tip, two blocks each.
const LOOKOUT_SEAT_BLOCKS = 8;
// The beach's pieces that go up on a wall as well as on a floor.
const BEACH_ON_WALLS = ["tiki_torch", "floor_window_panel"];
const EXTERIOR_CLIMB = 30;
// A freighter's landing legs: how many she stands on; how long they are given to draw up once she is cruising, in
// ticks; how often her landing is looked at, in ticks; and how near the ground her feet count as touching it, in blocks
// over where her control head stood on it.
const FREIGHTER_LEGS = 5;
const LEGS_UP_TICKS = 300;
const LEGS_LOOK_EVERY = 5;
const LEGS_TOUCHING = 2.5;
const STEPS = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] };
// Each way's next a quarter turn anticlockwise, seen from above: a pin's right as its bowler sees it.
const COUNTER_CLOCKWISE = { north: "west", west: "south", south: "east", east: "north" };
// A freighter's bowling lane: how many pins its rack has; how far back from the head pin the ball is bowled, in
// blocks; how long the ball is given to roll into the rack and scatter it, well short of the pinsetter's first pin
// standing up again, and how many pins it must have knocked down by then at least, the head pin and the one its fall
// most likely topples; and how long the pinsetter is given to stand them all up again and the ball to go back down the
// return, in ticks.
const BOWLING_PINS = 10;
const BOWL_FROM = 14;
const BOWL_REACHES_TICKS = 50;
const BOWLING_LEAST_DOWN = 2;
const PINSETTER_TICKS = 160;
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

// Stands the player on the ground at a far spot to build there, facing a way, as Minecraft yaw, north unless told. A
// client not yet sent its chunks there tells the server nothing, so the server still has the way they faced before, and
// someone just off a tumbling ship drifts on: ships were built facing south and six blocks up. So once the chunks have
// come they are put there again, by the server, which is what a build goes by.
function standToBuild(x, z, yaw = 180) {
  must(`server tp @p ${x} ${ground} ${z}; wait ${CHUNKS_TICKS}t; settle`, BUILD_SECONDS);
  must(`server tp @p ${x} ${ground} ${z} ${yaw} 0; wait 5t`);
}

// Lays a ship down on a site of its own, the player facing north, and gives its stairs time to come down.
function layDown(what, number = nextSite++) {
  const site = { x: number * SITE_APART, z: 0, what };
  creative();
  standToBuild(site.x, site.z);
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
  must(`server tp @p ${site.x} ${ground + 5} ${site.z}; wait ${CHUNKS_TICKS}t; settle`, BUILD_SECONDS);
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
// A sound played, as said in a check's detail: which, at what tick, how loud and how high.
const soundOf = (row) => `${row.id} at tick ${row.tick}, volume ${row.volume}, pitch ${row.pitch}`;

function checkAirstairsReachTheGround() {
  walksUp("furnished");
}

function checkZeppelinStairs() {
  walksUp("furnished_zeppelin");
}

function checkRocketStairs() {
  walksUp("rocket");
}

// Presses an airliner's stairs' button, a block in front of it, and waits for them to fold or come down.
function pressStairsButton(button) {
  const [dx, dz] = STEPS[must(`block ${button.join(",")}`).props.facing];
  must(`server tp @p ${button[0] + 0.5 + dx * 1.5} ${button[1] - 1} ${button[2] + 0.5 + dz * 1.5}; wait 20t; use ${button.join(",")}; wait ${STAIRS_SWING_TICKS}t`);
}

// What holds an airliner's airstairs from folding: a hostile mob standing on them does not, and they fold all the same;
// an animal there does, until it is gone. Before, a zombie that wandered onto them held them for as long as it stood.
function checkAirstairsMobs() {
  const site = layDown("airliner");
  const bottom = bottomOfStairs(site);
  expect(bottom.length > 0, "airliner: its stairs have come down");
  const [button] = must("find skyliners:airstair_button 128 1").rows;
  expect(button, "airliner: its stairs have a button");
  if (bottom.length === 0 || !button) {
    return;
  }
  const [x, y, z] = bottom[0].at;
  const on = `${x + 0.5} ${y + 1} ${z + 0.5}`;
  const at = button.at_abs;
  must("server difficulty easy");
  must(`server summon creeper ${on} {NoAI:1b,PersistenceRequired:1b}`);
  pressStairsButton(at);
  expect(bottomOfStairs(site).length === 0, "airliner: a creeper on its stairs does not stop them folding");
  must("server kill @e[type=creeper]");
  pressStairsButton(at);
  expect(bottomOfStairs(site).length > 0, "airliner: its stairs come down again");
  must(`server summon sheep ${on} {NoAI:1b,PersistenceRequired:1b}`);
  pressStairsButton(at);
  expect(bottomOfStairs(site).length > 0, "airliner: a sheep on its stairs holds them");
  must("server kill @e[type=sheep]");
  must(`wait ${STAIRS_SWING_TICKS}t`);
  expect(bottomOfStairs(site).length === 0, "airliner: with the sheep gone, its stairs fold");
  must("server difficulty peaceful");
}

function checkNoStepsLeftBehind() {
  const site = layDown("airliner");
  const bottom = bottomOfStairs(site);
  expect(bottom.length > 0, "airliner: its stairs are down before it flies");
  const xs = bottom.map((step) => step.at[0]);
  const zs = bottom.map((step) => step.at[2]);
  const [fromX, toX, fromZ, toZ] = [Math.min(...xs) - STAIRS_MARGIN, Math.max(...xs) + STAIRS_MARGIN, Math.min(...zs) - STAIRS_MARGIN, Math.max(...zs) + STAIRS_MARGIN];
  board();
  // She is soon past where the player aboard keeps the ground loaded: held loaded, it is still there to be read.
  must(`server forceload add ${fromX} ${fromZ} ${toX} ${toZ}`);
  must(`skyliners:autopilot fly altitude=${ground + FLIGHT_HEIGHT} speed=${FLIGHT_SPEED}`, BUILD_SECONDS);
  must(`wait ${FOLDED_TICKS}t`);
  const area = [...read([fromX, ground - 1, fromZ], [toX, ground + 1, toZ])];
  must(`server forceload remove ${fromX} ${fromZ} ${toX} ${toZ}`);
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

// Drops an ice cream into its tank over it. Its hatch stays open once used, and it waits at it, in the way of a click.
function throwIceCream() {
  const [creature] = must("find skyliners:tardigrade 128 1").rows;
  must(`server summon item ${creature.at_abs[0]} ${creature.at_abs[1] + 2} ${creature.at_abs[2]} {Item:{id:"skyliners:ice_cream",count:1}}`);
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
function checkTheShipShakes(site) {
  if (spawnSync("ffmpeg", ["-version"]).error) {
    console.log("  skip the view shaking: it takes FFmpeg to measure");
    return;
  }
  throwIceCream();
  must(`option renderClouds off; server tp @p ${site.x} ${ground} ${site.z + SHAKE_STAND_OFF}; turn 0 -10; wait 10t`);
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
  checkTheShipShakes(site);

  must(`server tp @p ${hx + 0.5 + dx * 2.5} ${hy - 1} ${hz + 0.5 + dz * 2.5}; wait ${BACK_AGAIN_TICKS}t`);
  const thrown = now();
  throwIceCream();
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

// A freighter standing on the ground: her self-destruct button is on her starboard wall by the co-pilot's jump seat as
// she is built, arming it puts her together and sounds the alarm, and as its countdown runs out she blows up, whoever
// stood aboard given a parachute, as the rocket's does. Putting a ship her size together takes longer than the rocket,
// so her countdown, not a third press, is what is waited for.
function checkFreighterSelfDestruct() {
  layDown("freighter");
  const [found] = must("find skyliners:self_destruct_button 128 1").rows;
  expect(found, "freighter: she is built with a self-destruct button");
  if (!found) {
    return;
  }
  const [x, y, z] = found.at_abs;
  const { props } = must(`block ${x},${y},${z}`);
  expect(props?.mount === "wall", "freighter: her self-destruct button is fixed to her wall", `${props?.mount}`);
  const [dx, dz] = STEPS[props?.facing ?? "north"];
  must(`fly off; server tp @p ${x + 0.5 - dx * 1.5} ${y - 1} ${z + 0.5 - dz * 1.5}; wait 20t`);
  const since = armButton(`${x},${y},${z}`);
  expect(heard("*self_destruct_siren*", since, DESTRUCT_ARMED_TICKS), "freighter: standing as blocks, arming the button puts her together and sounds the alarm");
  const freighter = findShipFlying("skyliners:engine_grille");
  expect(heard("*self_destruct_blast*", since, DESTRUCT_ARMED_TICKS), "freighter: as its countdown runs out, she blows up",
    played("*self_destruct*", since).map(soundOf).join("; ") || "no self-destruct sound since arming");
  expect(!stillThere(freighter), "freighter: blown up on the ground, she is gone");
  expect(wearsParachute(), "freighter: whoever stood aboard is given a parachute");
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
  const start = now();
  must(`skyliners:autopilot fly altitude=${height} speed=0`, BUILD_SECONDS);
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise" && Math.abs(report.altitude - height) < FREIGHTER_HOLD_WITHIN,
    FREIGHTER_UP_TICKS);
  // Not up, what she is doing says why: still held on her legs by what her console shows, or flying but short of it.
  const last = up ?? freighterReport();
  expect(up, "freighter: she lifts off her legs and climbs to the height set",
    up ? `at ${up.altitude}` : `${last?.phase} at ${last?.altitude}, lift ${last?.lift}, legs ${last?.legs}, held by ${last?.held}`);
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
    expect(!heard(IMPACT_SOUND, start, 0), "freighter: lifting off her legs and settling back on them, she is never heard striking the ground");
  }
}

// A ship left flying with nobody near: everyone aboard a cruising freighter goes far off, and she comes down at once
// and is let go once down, rather than flying on a minute first and costing the server her physics all that while.
function checkUnattendedFarOff() {
  const site = layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=${BOOST_CRUISE}`, BUILD_SECONDS);
  const cruising = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(cruising, "unattended: the freighter lifts off and cruises");
  if (!cruising) {
    return;
  }
  const freighter = findShipFlying("skyliners:engine_grille");
  // Aboard a moment first, so whatever minute she counted with nobody aboard starts over.
  must(`sable:tp ${freighter} skyliners:autopilot; wait ${LOOK_EVERY * 2}t`);
  must(`server tp @p ${site.x + UNATTENDED_AWAY} ${ground} ${site.z}`);
  const left = now();
  let loaded = true;
  for (let waited = 0; waited <= UNATTENDED_GONE_TICKS && loaded; waited += LOOK_EVERY) {
    loaded = JSON.stringify(send(`wait ${LOOK_EVERY}t; ?server sable info @e`)).includes(freighter);
  }
  expect(!loaded, "unattended: left far off with nobody aboard, she comes down at once and is let go",
    loaded ? `still loaded ${UNATTENDED_GONE_TICKS} ticks after everyone went` : `gone ${now() - left} ticks after everyone went`);
  must(`server tp @p 0 ${ground} 0`);
}

// A ship set down hard is heard striking the ground, and grinding along it: a freighter, assembled and switched off
// where she stands, lifted and let fall onto it, crunches, heard from the ground beside her; shoved along it, as a
// collision would, she grinds, heard aboard her. Her own landings, gentle, make no such sound (see
// checkFreighterFlight, landFreighterAt and checkFlightAndLanding).
function checkImpactSounds() {
  layDown("freighter");
  // Switched off in the same script as she is assembled, before she can lift off, once her control head has taken
  // its first tick aboard: off in the air, she would come down on her thrusters and stay engaged until down, held up
  // through the drop and the shove, as she was on a busy machine. Waited for all the same.
  must(`skyliners:autopilot fly altitude=${ground + FREIGHTER_CLIMB} speed=0; wait ${IMPACT_ABOARD_TICKS}t; skyliners:autopilot off`, BUILD_SECONDS);
  const off = waitForReport(freighterReport, (report) => report.phase === "off", FREIGHTER_DOWN_TICKS);
  expect(off, "impact: a freighter assembled and switched off stands on her legs", off ? "" : `still ${freighterReport()?.phase}`);
  if (!off) {
    return;
  }
  must(`wait ${IMPACT_SETTLE_TICKS}t`);
  const freighter = findShipFlying("skyliners:engine_grille");
  const since = now();
  must(`server sable physics translation ${freighter} add 0 ${IMPACT_DROP} 0 global`);
  expect(heard("skyliners:impact_crunch", since, IMPACT_HEARD_TICKS), "impact: dropped onto the ground, she crunches into it",
    played(IMPACT_SOUND, since).map(soundOf).join(", "));
  const groans = played("skyliners:impact_groan", since, `wait ${IMPACT_SETTLE_TICKS}t; `);
  expect(groans.length === 1, "impact: her hull groans once after it", groans.map(soundOf).slice(0, 3).join(", "));
  const { at } = must(`sable:find ${freighter} skyliners:autopilot 1 side=server`).rows[0];
  must(`sable:tp ${at.join(",")}; fly off; wait ${LOOK_EVERY}t`);
  const shoved = now();
  must(`server sable physics impulse ${freighter} linear ${freighterReport().mass * IMPACT_SHOVE} 0 0 global`);
  expect(heard("skyliners:impact_scrape", shoved, IMPACT_HEARD_TICKS), "impact: shoved along the ground, she grinds along it",
    played(IMPACT_SOUND, shoved).map(soundOf).join(", "));
}

// The freighter's computer speaks for her flight deck, in its own voice: set to fly with the player aboard in her
// entry hall, by her ramp, it, not the airliner's flight deck, says she is lifting off; told to land, that she is
// coming down; and on her legs again, that she is down, her ramp coming down saying so too without either line talking
// over the other, as they once did.
function checkFreighterVoice() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const since = now();
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  const { at } = must(`sable:find ${freighter} skyliners:crossbow_rack 1 side=server`).rows[0];
  must(`sable:tp ${at.join(",")}; wait 10t`);
  expect(heard("skyliners:freighter_computer_takeoff", since, FREIGHTER_VOICE_TICKS), "freighter: set to fly, her computer says she lifts off");
  expect(!heard("skyliners:announcement_takeoff", since, 0), "freighter: and the airliner's take-off announcement is not made aboard her");
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(up, "freighter: she lifts off with the player aboard");
  if (!up) {
    return;
  }
  const landing = now();
  must("skyliners:autopilot land");
  expect(heard("skyliners:freighter_computer_landing", landing, FREIGHTER_VOICE_TICKS), "freighter: told to land, her computer says she comes down");
  const down = waitForReport(freighterReport, (report) => report.phase === "off", FREIGHTER_DOWN_TICKS);
  expect(down, "freighter: she comes down and switches off");
  const arrival = heard("skyliners:freighter_computer_arrival", landing, FREIGHTER_VOICE_TICKS);
  expect(arrival, "freighter: on her legs again, her computer says she is down");
  expect(!heard("skyliners:announcement_*", since, 0), "freighter: none of the airliner's announcements is made aboard her");
  const ramp = heard("skyliners:freighter_computer_ramp_lowering", landing, FREIGHTER_VOICE_TICKS);
  const [first, second] = [ramp, arrival].sort((a, b) => a?.tick - b?.tick);
  const apart = ramp && arrival ? second.tick - first.tick : 0;
  expect(ramp && arrival && apart >= soundTicks(first.id), "freighter: her ramp comes down saying so, and neither line talks over the other",
    ramp && arrival ? `${first.id} for ${soundTicks(first.id)} ticks, ${second.id} ${apart} ticks after it began` : `ramp heard: ${Boolean(ramp)}`);
}

// How long one of the mod's sounds runs, in ticks, rounded down, from its Ogg Vorbis file (see VoiceLength): the sample
// its last page ends on, over the sample rate its identification header gives.
function soundTicks(sound) {
  const [namespace, name] = sound.split(":");
  const ogg = readFileSync(path.join(ROOT, "src/main/resources/assets", namespace, "sounds", `${name}.ogg`));
  const rate = ogg.readUInt32LE(ogg.indexOf("\u0001vorbis", 0, "latin1") + 12);
  const samples = Number(ogg.readBigUInt64LE(ogg.lastIndexOf("OggS", ogg.length, "latin1") + 6));
  return Math.floor((samples * 20) / rate);
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
  walkOntoRampOnItsWay(deck, [dx, dz, yaw], panel.at_abs, lamp, lampTurns);
  standUnderRamp(deck, [dx, dz], panel.at_abs, lamp, lampTurns);
  callRampFromTheGround(lamp, lampTurns);
  holdFreighterOnHerRamp(deck, footX + dx * 2 * RAMP_STAND_UP, lowest + RAMP_STAND_UP, footZ + dz * 2 * RAMP_STAND_UP, lamp);
}

// A freighter's boarding ramp on ground that does not lie where she stands: a block higher or lower at its foot, it
// still comes down, its deck ending where the ground meets it, and is walked up and down with the walk key alone, its
// plate holding up whoever walks on it while it swings; two blocks higher or lower, or with a block in its way, it
// refuses with a low note and a red lamp. It used to refuse the block higher, and left a block's step at the block lower.
// A freighter's ramp well walled in under her deck: down, her ramp's well opens through her deck and her belly, and
// looking aside from the ramp the player sees wall right under her deck on either side. The low space under her deck
// lay open beside the well, its thrusters and the underside of her floors in view from the ramp.
function checkFreighterRampWell() {
  layDown("freighter");
  const [panel] = must("find skyliners:ramp_panel 128 1").rows;
  const lamp = () => (panel ? must(`block ${panel.at_abs.join(",")} side=server`).props?.status : undefined);
  const down = panel && waitForReport(() => ({ status: lamp() }), (read) => read.status === "down", RAMP_DOWN_TICKS);
  expect(down, "freighter: laid down, her ramp comes down by itself", `lamp ${lamp()}`);
  const deck = must("find skyliners:ramp_deck 128 200 side=server").rows.map((row) => row.at_abs);
  if (!down || deck.length === 0) {
    return;
  }
  const top = Math.max(...deck.map(([, y]) => y));
  const facing = must(`block ${deck.find(([, y]) => y === top).join(",")} side=server`).props?.facing ?? "north";
  const [dx, dz] = CLIMBS[facing];
  const across = ([x, , z]) => x * -dz + z * dx;
  const edges = [Math.min(...deck.map(across)), Math.max(...deck.map(across))];
  const open = [];
  for (const [x, , z] of deck.filter((at) => edges.includes(across(at)))) {
    const out = across([x, 0, z]) === edges[0] ? -1 : 1;
    const side = [x - dz * out, top - 1, z + dx * out];
    if (must(`block ${side.join(",")} side=server`).id === "air") {
      open.push(side.join(","));
    }
  }
  expect(open.length === 0, "freighter: beside her ramp's well, right under her deck, stands wall", open.length ? `open at ${open.join(" ")}` : "");
}

function checkFreighterRampFoot() {
  layDown("freighter");
  const [panel] = must("find skyliners:ramp_panel 128 1").rows;
  expect(panel, "freighter: she is built with her ramp's panel");
  if (!panel) {
    return;
  }
  const lamp = () => must(`block ${panel.at_abs.join(",")} side=server`).props?.status;
  const lampTurns = (status, within) => waitForReport(() => ({ status: lamp() }), (read) => read.status === status, within);
  expect(lampTurns("down", RAMP_DOWN_TICKS), "freighter: on flat ground her ramp comes down by itself", `lamp ${lamp()}`);
  const foot = rampFootOf(panel.at_abs);
  if (!foot) {
    return;
  }
  const lowest = Math.min(...foot.deck.map(([, y]) => y));
  expect(lowest === ground, "freighter: on flat ground her ramp's lowest row lies on the ground", `lowest at y ${lowest}, the ground at y ${ground}`);
  if (lowest !== ground) {
    return;
  }
  pressRampPanel(foot);
  expect(lampTurns("ready", RAMP_DOWN_TICKS), "freighter: on flat ground her ramp folds away from its panel", `lamp ${lamp()}`);
  rampMeetsGround(foot, lamp, lampTurns, { name: "a block higher", from: ground, to: ground, fill: "stone replace air", level: ground + 1, undo: "air replace stone" });
  rampMeetsGround(foot, lamp, lampTurns, { name: "a block lower", from: ground - 1, to: ground - 1, fill: "air", level: ground - 1, undo: "grass_block replace air" });
  rampHeldWhereItsFootRunsOut(foot, lamp, lampTurns);
  rampMeetsRaggedGround(foot, lamp, lampTurns);
  rampRefuses(foot, lamp, { name: "two blocks higher", from: ground, to: ground + 1, fill: "stone replace air", undo: "air replace stone" });
  rampRefuses(foot, lamp, { name: "two blocks lower", from: ground - 2, to: ground - 1, fill: "air", undo: "dirt replace air" });
  rampRefusedByABlock(foot, lamp);
}

// A freighter saved and joined again with her ramp down, set to fly: her ramp folds and the blocks of its hatch, put
// back, stay hers. Sable's heat map used to shed them as ships of their own at a fold of a ship loaded from a save,
// pieces of her belly left lying under her and in the way of her ramp. Nothing lies round her but herself; and back on
// her legs her ramp comes down again.
function checkFreighterKeepsHerBelly() {
  layDown("freighter");
  must(`skyliners:autopilot fly altitude=${ground + FREIGHTER_CLIMB} speed=0; wait ${IMPACT_ABOARD_TICKS}t; skyliners:autopilot off`, BUILD_SECONDS);
  const parked = waitForReport(freighterReport, (report) => report.phase === "off", FREIGHTER_DOWN_TICKS);
  expect(parked, "freighter: assembled and switched off, she stands on her legs", parked ? "" : `still ${freighterReport()?.phase}`);
  if (!parked) {
    return;
  }
  const freighter = findShipFlying("skyliners:engine_grille");
  const [{ at: panelAt }] = must(`sable:find ${freighter} skyliners:ramp_panel 1 side=server`).rows;
  const lamp = () => must(`block ${panelAt.join(",")} side=server`).props?.status;
  const lampTurns = (status, within) => waitForReport(() => ({ status: lamp() }), (read) => read.status === status, within);
  expect(lampTurns("down", FREIGHTER_DOWN_TICKS), "freighter: stopped, her ramp comes down by itself", `lamp ${lamp()}`);
  must(`sable:tp ${freighter} skyliners:autopilot; wait ${LOOK_EVERY}t`);
  must(`leave; join world ${WORLD}; wait ${RELOADED_SETTLE_TICKS}t`, BUILD_SECONDS);
  must(`skyliners:autopilot fly altitude=${ground + FREIGHTER_CLIMB} speed=0; wait ${RAMP_FOLD_FLOOD_TICKS}t`, BUILD_SECONDS);
  const shed = () => must(`sable:list ${SHED_LOOK_BLOCKS} side=server`).rows.filter((row) => row.id !== freighter);
  const pieces = shed();
  expect(pieces.length === 0, "freighter: loaded from a save, her ramp folds and leaves no piece of her belly behind",
    pieces.map((piece) => `${piece.id} size ${piece.size}`).join(", "));
  must("skyliners:autopilot off", BUILD_SECONDS);
  const down = waitForReport(freighterReport, (report) => report.phase === "off", FREIGHTER_DOWN_TICKS);
  expect(down, "freighter: she comes back to her legs", down ? "" : `still ${freighterReport()?.phase}`);
  expect(lampTurns("down", FREIGHTER_DOWN_TICKS), "freighter: and her ramp comes down again with nothing lying in its way", `lamp ${lamp()}`);
  const after = shed();
  expect(after.length === 0, "freighter: and still nothing of hers lies apart from her", after.map((piece) => `${piece.id} size ${piece.size}`).join(", "));
  const crown = must(`sable:find ${freighter} skyliners:freighter_cover 1000 side=server`).rows.map((row) => row.at)
    .reduce((top, at) => (at[1] > top[1] ? at : top));
  const onCrown = (rise) => `${crown[0]} ${crown[1] + rise} ${crown[2]}`;
  must(`server fill ${onCrown(1)} ${onCrown(SHED_POST_BLOCKS)} stone; wait ${RAMP_FOLD_FLOOD_TICKS}t`, BUILD_SECONDS);
  must(`server setblock ${onCrown(1)} air; wait ${RAMP_FOLD_FLOOD_TICKS}t`, BUILD_SECONDS);
  const cutOff = shed();
  expect(cutOff.length === 1, "freighter: and a post of stone cut at its foot really does come away as a ship of its own",
    `${cutOff.length} apart from her`);
  cutOff.forEach((piece) => send(`?server sable remove ${piece.id}`));
}

// A freighter's ramp down on flat ground, as its laid deck shows it: the middle of its foot, the way it climbs, how many
// blocks of deck it has and how wide it is, the room to stand in at its head and where its panel is.
function rampFootOf(panelAt) {
  const deck = must("find skyliners:ramp_deck 128 200 side=server").rows.map((row) => row.at_abs);
  if (deck.length === 0) {
    expect(false, "freighter: her ramp's deck is laid");
    return undefined;
  }
  const lowest = Math.min(...deck.map(([, y]) => y));
  const facing = must(`block ${deck.find(([, y]) => y === lowest).join(",")} side=server`).props?.facing ?? "north";
  const [dx, dz, yaw] = CLIMBS[facing];
  const outermost = Math.min(...deck.map(([x, , z]) => x * dx + z * dz));
  const across = deck.filter(([x, , z]) => x * dx + z * dz === outermost).sort((a, b) => a[0] - b[0] || a[2] - b[2]);
  const [x, , z] = across[Math.floor(across.length / 2)];
  return { deck, x, z, dx, dz, yaw, outermost, width: across.length, head: rampHeadStand(deck, [dx, dz]), panelAt };
}

// A freighter's ramp with the server running behind: each client counts the swing itself, so it is drawn folding away
// and coming down again evenly, never back from where it had got to and never leaping on. Nothing is read from the
// server while it swings, as each such read stalls the client's own frames.
function checkFreighterRampLag() {
  layDown("freighter");
  const [panel] = must("find skyliners:ramp_panel 128 1").rows;
  expect(panel, "freighter: she is built with her ramp's panel");
  if (!panel) {
    return;
  }
  const lamp = () => must(`block ${panel.at_abs.join(",")} side=server`).props?.status;
  const lampTurns = (status, within) => waitForReport(() => ({ status: lamp() }), (read) => read.status === status, within);
  expect(lampTurns("down", RAMP_DOWN_TICKS), "freighter: laid down, her ramp comes down by itself", `lamp ${lamp()}`);
  const foot = rampFootOf(panel.at_abs);
  if (!foot) {
    return;
  }
  const swing = (name, status) => {
    const drawn = must(`server tp @p ${foot.head.join(" ")}; wait 20t; use ${foot.panelAt.join(",")}; wait ${RAMP_SWING_TICKS}t; skyliners:ramp`, BUILD_SECONDS);
    expect(drawn.frames > 0 && drawn.back <= RAMP_MOST_BACK && drawn.jump <= RAMP_MOST_LEAP,
      `freighter: with the server behind, her ramp is drawn ${name} evenly`, `${drawn.frames} frames, back ${drawn.back}, on ${drawn.jump} ticks at the worst`);
    expect(lampTurns(status, RAMP_DOWN_TICKS), `freighter: with the server behind, her ramp finishes ${name}`, `lamp ${lamp()}`);
  };
  must(`load server ${RAMP_LAG_MILLIS}`);
  swing("folding away", "ready");
  swing("coming down", "down");
  must("load off");
}

// What holds a freighter's ramp as it comes down: a hostile mob standing in its swing does not, and it comes down all
// the same; an animal there does, until it is gone. Before, a zombie that wandered under it held it for as long as it
// stood.
function checkFreighterRampMobs() {
  layDown("freighter");
  const [panel] = must("find skyliners:ramp_panel 128 1").rows;
  expect(panel, "freighter: she is built with her ramp's panel");
  if (!panel) {
    return;
  }
  const lamp = () => must(`block ${panel.at_abs.join(",")} side=server`).props?.status;
  const lampTurns = (status, within) => waitForReport(() => ({ status: lamp() }), (read) => read.status === status, within);
  expect(lampTurns("down", RAMP_DOWN_TICKS), "freighter: laid down, her ramp comes down by itself", `lamp ${lamp()}`);
  const foot = rampFootOf(panel.at_abs);
  if (!foot) {
    return;
  }
  const under = nearRampFoot(foot, RAMP_MOB_UNDER, 0, ground);
  must("server difficulty easy");
  pressRampPanel(foot);
  expect(lampTurns("ready", RAMP_DOWN_TICKS), "freighter: her ramp folds away", `lamp ${lamp()}`);
  must(`server summon creeper ${under} {NoAI:1b,PersistenceRequired:1b}`);
  pressRampPanel(foot);
  expect(lampTurns("down", RAMP_DOWN_TICKS), "freighter: a creeper under her ramp does not hold it", `lamp ${lamp()}`);
  must("server kill @e[type=creeper]");
  pressRampPanel(foot);
  expect(lampTurns("ready", RAMP_DOWN_TICKS), "freighter: her ramp folds away again", `lamp ${lamp()}`);
  must(`server summon sheep ${under} {NoAI:1b,PersistenceRequired:1b}`);
  pressRampPanel(foot);
  must(`wait ${RAMP_ANIMAL_HOLDS_TICKS}t`);
  expect(lamp() === "busy", "freighter: a sheep under her ramp holds it", `lamp ${lamp()}`);
  must("server kill @e[type=sheep]");
  expect(lampTurns("down", RAMP_DOWN_TICKS), "freighter: with the sheep gone, her ramp comes down", `lamp ${lamp()}`);
  must("server difficulty peaceful");
}

// Presses a freighter's ramp's panel from just in from its head, on her deck.
function pressRampPanel(foot) {
  must(`server tp @p ${foot.head.join(" ")}; wait 20t; use ${foot.panelAt.join(",")}`);
}

// Where a place on the ground round a freighter's ramp's foot is, as x, y and z: so far out past its foot, negative in
// under the ramp, so far across it, and at a height.
function nearRampFoot(foot, out, across, y) {
  return `${foot.x - foot.dx * out + foot.dz * across} ${y} ${foot.z - foot.dz * out + foot.dx * across}`;
}

// Changes the ground under and past a freighter's ramp's foot, whole rows from the last row of its deck on out, replacing
// only what is asked, so that nothing of hers beside it goes.
function reshapeRampFoot(foot, shape, fill) {
  const nearest = nearRampFoot(foot, 1 - RAMP_FOOT_ROWS, -RAMP_FOOT_ACROSS, shape.from);
  const furthest = nearRampFoot(foot, RAMP_FOOT_OUT, RAMP_FOOT_ACROSS, shape.to);
  must(`server fill ${nearest} ${furthest} ${fill}`);
}

// A freighter's ramp pressed down onto ground changed so, a block higher or lower: it comes down and its deck ends where
// the ground meets it, its lowest row lying on that ground; walked up and down with the walk key alone it has no step
// too high and no gap; it holds up whoever walks onto it as it swings; and folded away it leaves none of itself behind.
function rampMeetsGround(foot, lamp, lampTurns, shape) {
  reshapeRampFoot(foot, shape, shape.fill);
  pressRampPanel(foot);
  expect(lampTurns("down", RAMP_DOWN_TICKS), `freighter: on ground ${shape.name} at its foot, her ramp comes down`, `lamp ${lamp()}`);
  const deck = must("find skyliners:ramp_deck 128 200 side=server").rows.map((row) => row.at_abs);
  const whole = foot.deck.length + (shape.level > ground ? -1 : 1) * RAMP_FOOT_ROWS * foot.width;
  expect(deck.length === whole, `freighter: on ground ${shape.name}, its deck ends where the ground meets it`, `${deck.length} blocks, not ${whole}`);
  const lowest = deck.length ? Math.min(...deck.map(([, y]) => y)) : NaN;
  expect(lowest === shape.level, `freighter: on ground ${shape.name}, its lowest row lies on that ground`, `lowest at y ${lowest}, the ground at y ${shape.level}`);
  survival();
  const [fromX, , fromZ] = nearRampFoot(foot, RAMP_WALK_FROM, 0, shape.level).split(" ").map(Number);
  must(`server tp @p ${fromX + 0.5} ${shape.level} ${fromZ + 0.5}; turn ${foot.yaw} 0; wait 25t`);
  const upThere = must(`move forward ${RAMP_WALK_TICKS}t; status`);
  const climbed = round(upThere.pos[1] - shape.level);
  expect(climbed >= RAMP_RISE + ground - shape.level, `freighter: on ground ${shape.name}, its ramp is walked up with the walk key alone`,
    `climbed ${climbed}, to ${upThere.pos.map(round).join(",")}`);
  const downThere = must(`turn ${foot.yaw + 180} 0; move forward ${RAMP_WALK_TICKS}t; status`);
  const along = Math.floor(downThere.pos[0]) * foot.dx + Math.floor(downThere.pos[2]) * foot.dz;
  expect(round(downThere.pos[1]) === shape.level && along < foot.outermost, `freighter: on ground ${shape.name}, its ramp is walked back down to that ground`,
    `at ${downThere.pos.map(round).join(",")}, the ground at y ${shape.level}`);
  creative();
  walkOntoRampOnItsWay(foot.deck, [foot.dx, foot.dz, foot.yaw], foot.panelAt, lamp, lampTurns);
  pressRampPanel(foot);
  expect(lampTurns("ready", RAMP_DOWN_TICKS), `freighter: on ground ${shape.name}, her ramp folds away`, `lamp ${lamp()}`);
  const left = must("find skyliners:ramp_deck 128 200 side=server").rows.length;
  expect(left === 0, `freighter: on ground ${shape.name}, folding it away leaves none of its deck behind`, `${left} blocks`);
  reshapeRampFoot(foot, shape, shape.undo);
}

// Stands where a freighter's ramp's foot runs out to on ground a block lower, as it comes down: it holds, amber, with
// its chime heard there, a child on the ground at its foot, and comes down once they step off.
function rampHeldWhereItsFootRunsOut(foot, lamp, lampTurns) {
  const shape = { from: ground - 1, to: ground - 1 };
  reshapeRampFoot(foot, shape, "air");
  const [x, , z] = nearRampFoot(foot, RAMP_FOOT_STAND, 0, ground - 1).split(" ").map(Number);
  const since = now();
  must(`server tp @p ${foot.head.join(" ")}; wait 20t; use ${foot.panelAt.join(",")}; server tp @p ${x + 0.5} ${ground - 1} ${z + 0.5}; wait ${RAMP_UNDER_TICKS}t`);
  const held = lamp();
  expect(held === "busy", "freighter: standing where her ramp's foot runs out, it waits for them", `lamp ${held}`);
  expect(heard("block.note_block.bell", since, 0), "freighter: and they hear its chime at its foot");
  must(`server tp @p ${x + 0.5 + foot.dz * RAMP_STEP_ASIDE} ${ground - 1} ${z + 0.5 + foot.dx * RAMP_STEP_ASIDE}`);
  expect(lampTurns("down", RAMP_DOWN_TICKS), "freighter: stepped off, its foot runs out and it comes down", `lamp ${lamp()}`);
  pressRampPanel(foot);
  lampTurns("ready", RAMP_DOWN_TICKS);
  reshapeRampFoot(foot, shape, "grass_block replace air");
}

// A freighter's ramp pressed down with one column's ground a block higher than the rest: that column's deck ends where
// the ground meets it, two rows short of the others', and folded it leaves none behind.
function rampMeetsRaggedGround(foot, lamp, lampTurns) {
  const across = (foot.width - 1) / 2;
  must(`server fill ${nearRampFoot(foot, 1 - RAMP_FOOT_ROWS, across, ground)} ${nearRampFoot(foot, RAMP_FOOT_OUT, across, ground)} stone replace air`);
  pressRampPanel(foot);
  expect(lampTurns("down", RAMP_DOWN_TICKS), "freighter: with one column's ground a block higher, her ramp comes down", `lamp ${lamp()}`);
  const laid = must("find skyliners:ramp_deck 128 200 side=server").rows.length;
  expect(laid === foot.deck.length - RAMP_FOOT_ROWS, "freighter: with one column's ground a block higher, that column's deck meets it", `${laid} blocks, not ${foot.deck.length - RAMP_FOOT_ROWS}`);
  pressRampPanel(foot);
  expect(lampTurns("ready", RAMP_DOWN_TICKS), "freighter: with one column's ground a block higher, her ramp folds away", `lamp ${lamp()}`);
  must(`server fill ${nearRampFoot(foot, 1 - RAMP_FOOT_ROWS, across, ground)} ${nearRampFoot(foot, RAMP_FOOT_OUT, across, ground)} air replace stone`);
}

// A freighter's ramp pressed down where the ground lies further off than it takes: a low note and a red lamp, none of its
// deck laid.
function rampRefuses(foot, lamp, shape) {
  reshapeRampFoot(foot, shape, shape.fill);
  const since = now();
  pressRampPanel(foot);
  must(`wait ${RAMP_REFUSED_TICKS}t`);
  expect(lamp() === "refused", `freighter: on ground ${shape.name} at its foot, her ramp's lamp turns red`, `lamp ${lamp()}`);
  expect(heard("block.note_block.bass", since, 0), `freighter: on ground ${shape.name}, her ramp sounds its low note`);
  const laid = must("find skyliners:ramp_deck 128 200 side=server").rows.length;
  expect(laid === 0, `freighter: on ground ${shape.name}, its deck is not laid`, `${laid} blocks`);
  reshapeRampFoot(foot, shape, shape.undo);
}

// A freighter's ramp pressed down with a block standing in the room over its deck, on ground otherwise just as it was:
// a low note and a red lamp, as ever.
function rampRefusedByABlock(foot, lamp) {
  const [cellX, cellY, cellZ] = foot.deck.find(([x, , z]) => x === foot.x + foot.dx * RAMP_BLOCKED_ROW && z === foot.z + foot.dz * RAMP_BLOCKED_ROW);
  must(`server setblock ${cellX} ${cellY + 1} ${cellZ} stone`);
  const since = now();
  pressRampPanel(foot);
  must(`wait ${RAMP_REFUSED_TICKS}t`);
  const lit = lamp();
  expect(lit === "refused" && heard("block.note_block.bass", since, 0), "freighter: with a block standing in the room over its deck, her ramp refuses", `lamp ${lit}`);
  must(`server setblock ${cellX} ${cellY + 1} ${cellZ} air`);
}

// Where to stand in a freighter's ring corridor just in from the head of her ramp, as [x, y, z], from its deck laid and
// the way it climbs into her: the middle of its highest row, a little way in past its hinge, on her deck.
function rampHeadStand(deck, [dx, dz]) {
  const highest = Math.max(...deck.map(([, y]) => y));
  const innermost = Math.max(...deck.filter(([, y]) => y === highest).map(([x, , z]) => x * dx + z * dz));
  const head = deck.filter(([x, y, z]) => y === highest && x * dx + z * dz === innermost).sort((a, b) => a[0] - b[0] || a[2] - b[2]);
  const [x, y, z] = head[Math.floor(head.length / 2)];
  return [x + 0.5 + dx * RAMP_HEAD_IN, y + 1, z + 0.5 + dz * RAMP_HEAD_IN];
}

// Walks onto a freighter's ramp from her corridor, in survival, while it swings: going up, it holds where it is with the
// player standing on it rather than dropping them through the floor, and folds away once they step back off; coming
// down, it carries them down with it, never dropping them to the ground under her. With the fault there was nothing
// to stand on while it swung, and they fell through her floor to the ground.
function walkOntoRampOnItsWay(deck, [dx, dz, yaw], panelAt, lamp, lampTurns) {
  const [x, y, z] = rampHeadStand(deck, [dx, dz]);
  const out = yaw + 180;
  // Pressed and walked onto in one script, against the ramp's clock: a pause between the two let it all but fold first.
  const walkOnto = `server tp @p ${x} ${y} ${z}; turn ${out} 20; wait 20t; use ${panelAt.join(",")}; wait ${RAMP_INTO_SWING}t; turn ${out} 20; `
    + `move forward ${RAMP_ONTO_TICKS}t`;
  survival();
  const onIt = must(`${walkOnto}; wait ${RAMP_HELD_LOOK}t; status`);
  const held = lamp();
  expect(onIt.pos[1] > ground + RAMP_ABOVE_GROUND && held === "busy", "freighter: walked onto her ramp as it goes up, it holds them up, stopped",
    `at ${onIt.pos.map(round).join(",")}, the ground at ${ground}, lamp ${held}`);
  must(`turn ${yaw} 0; move forward ${RAMP_BACK_TICKS}t`);
  expect(lampTurns("ready", RAMP_DOWN_TICKS), "freighter: stepped back off it, her ramp folds away", `lamp ${lamp()}`);
  const riding = must(`${walkOnto}; status`);
  creative();
  expect(riding.pos[1] > ground + RAMP_ABOVE_GROUND, "freighter: walked onto her ramp as it comes down, it carries them down on it",
    `at ${riding.pos.map(round).join(",")}, the ground at ${ground}`);
  expect(lampTurns("down", RAMP_DOWN_TICKS), "freighter: and comes down all the way with them on it", `lamp ${lamp()}`);
}

// Stands on the ground under where a freighter's ramp comes down, as it comes down: it holds over them rather than
// coming down on them, and comes down once they step out from under it.
function standUnderRamp(deck, [dx, dz], panelAt, lamp, lampTurns) {
  const [x, y, z] = rampHeadStand(deck, [dx, dz]);
  must(`server tp @p ${x} ${y} ${z}; wait 20t; use ${panelAt.join(",")}`);
  expect(lampTurns("ready", RAMP_DOWN_TICKS), "freighter: her ramp folds away from its panel", `lamp ${lamp()}`);
  const [ux, uz] = [x - dx * (RAMP_HEAD_IN + RAMP_UNDER_OUT), z - dz * (RAMP_HEAD_IN + RAMP_UNDER_OUT)];
  must(`use ${panelAt.join(",")}; server tp @p ${ux} ${ground} ${uz}; wait ${RAMP_UNDER_TICKS}t`);
  const under = must("status");
  expect(lamp() === "busy" && under.pos[1] < ground + 1, "freighter: standing under her ramp as it comes down, it holds over them",
    `lamp ${lamp()}, at ${under.pos.map(round).join(",")}`);
  must(`server tp @p ${ux + dz * RAMP_STEP_ASIDE} ${ground} ${uz + dx * RAMP_STEP_ASIDE}`);
  expect(lampTurns("down", RAMP_DOWN_TICKS), "freighter: stepped out from under it, her ramp comes down", `lamp ${lamp()}`);
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
  expect(held?.held === "ramp", "freighter: held so, her console's scanner shows her ramp holding her", `${held?.held}`);
  expect(heard("block.note_block.bell", since, 0), "freighter: her ramp chimes for them to step off");
  const freighter = findShipFlying("skyliners:engine_grille");
  const [{ at: panelAboard }] = must(`sable:find ${freighter} skyliners:ramp_panel 1 side=server`).rows;
  const lampAboard = () => must(`block ${panelAboard.join(",")} side=server`).props?.status;
  const off = waitForReport(freighterReport, (report) => report.phase === "off", TAKEOFF_CALLED_OFF_TICKS);
  const lampDown = waitForReport(() => ({ status: lampAboard() }), (read) => read.status === "down", RAMP_LAMP_LAGS_TICKS);
  expect(off && lampDown, "freighter: held a long while, she calls her take-off off, her ramp left down",
    `${freighterReport()?.phase}, lamp ${lampAboard()}`);
  must(`sable:tp ${freighter} skyliners:autopilot; wait 20t; skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`);
  const up = waitForReport(freighterReport, (report) => report.phase === "climb" || report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(up, "freighter: set to fly again with nobody on it, her ramp folds away and she lifts off", up ? "" : `still ${freighterReport()?.phase}`);
  expect(up?.held === "none", "freighter: lifting off, her console no longer shows anything holding her", `${up?.held}`);
  must("skyliners:autopilot off");
}

// A freighter set to fly with the player sat at her gunner's seat, under her top dome, lifts off as she does with them
// on the ground beside her, nothing holding her: one was seen waiting on her legs so.
function checkFreighterGunnerTakeoff() {
  layDown("freighter");
  const [cannon] = must("find skyliners:laser_cannon 128 1").rows;
  const [x, , z] = cannon?.at_abs ?? [];
  const seat = gunnerSeat(x, z);
  if (!seat) {
    expect(false, "freighter: she has a gunner's seat under her dome");
    return;
  }
  must(`server tp @p ${seat[0] + 0.5} ${seat[1]} ${seat[2] - 0.5}; wait 20t`);
  sitAt(seat);
  expect(gunnerSight() !== undefined, "freighter: the player sits at her gunner's seat");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  const up = waitForReport(freighterReport, (report) => report.phase === "climb" || report.phase === "cruise", FREIGHTER_UP_TICKS);
  const last = up ?? freighterReport();
  expect(up, "freighter: set to fly from her gunner's seat, she lifts off", up ? "" : `still ${last?.phase}, lift ${last?.lift}, held by ${last?.held}`);
  must("skyliners:autopilot land; key press key.sneak 10t; wait 10t");
}

// A freighter's gunner's seat, the lowest seat at most GUNNER_SEAT_OFF across or along from her laser cannon above.
function gunnerSeat(x, z) {
  return must("find skyliners:pilot_seat 128 20").rows.map((row) => row.at_abs)
    .filter(([seatX, , seatZ]) => Math.abs(seatX - x) <= GUNNER_SEAT_OFF && Math.abs(seatZ - z) <= GUNNER_SEAT_OFF).sort((a, b) => a[1] - b[1])[0];
}

// A freighter's top turret: the ladder up her core is climbed from her deck with the walk key alone to the gunner's
// seat under her dome, where her computer says the turret is ready and the view goes up to her laser cannon's sight,
// above its swivel; aimed from there at a creeper in the sky, the sight locks on to it and holding attack fires her
// cannon where the sight looks, striking it, and breaks nothing, the dome's glass over the gunner whole after; looking
// lower than its barrels go, the trigger only clicks; held down, it overheats, holds its fire and fires again once
// cooled; sneaking gets the gunner up and gives them their own view back at once.
function checkFreighterTurret() {
  layDown("freighter");
  const [cannon] = must("find skyliners:laser_cannon 128 1").rows;
  expect(cannon, "freighter: she is built with a laser cannon on her top dome");
  if (!cannon) {
    return;
  }
  const [x, y, z] = cannon.at_abs;
  const seat = gunnerSeat(x, z);
  const ladder = must("find skyliners:crew_ladder 128 20").rows.map((row) => row.at_abs).sort((a, b) => a[1] - b[1])[0];
  if (!seat || !ladder) {
    expect(false, "freighter: she has a gunner's seat under her dome and a ladder up to it");
    return;
  }
  const [climbX, climbZ, yaw] = CLIMBS[must(`block ${ladder.join(",")}`).props?.facing === "north" ? "south" : "north"];
  survival();
  must(`server tp @p ${ladder[0] + 0.5} ${ladder[1]} ${ladder[2] + 0.5 - climbZ * 0.2}; turn ${yaw} 0; wait 20t`);
  // Climbed facing the ladder, its platform is behind them: they step straight back off its top, where the seat stood.
  const [, there, , , off] = send(`move forward ${LADDER_CLIMB_TICKS}t; status; move back ${STEP_OFF_TICKS}t; wait 10t; status`);
  creative();
  expect(there.pos[1] >= seat[1], "freighter: her core's ladder is climbed from her deck to her gunner's seat", `reached ${round(there.pos[1])}, seat at ${seat[1]}`);
  const offLadder = Math.floor(off.pos[0]) !== ladder[0] || Math.floor(off.pos[2]) !== ladder[2];
  expect(offLadder && off.pos[1] >= seat[1] - PLATFORM_SLACK, "freighter: stepping back off the top of her core's ladder, they stand on her gunner's platform",
    `at ${off.pos.map(round).join(",")}, the ladder's column ${ladder[0]},${ladder[2]}, the platform's top at ${seat[1]}`);
  expect(gunnerSight() === undefined, "freighter: standing by her gunner's seat, the player sees from their own eyes");
  const sat = now();
  sitAt(seat);
  expect(heard("skyliners:freighter_computer_turret_ready", sat, 0), "freighter: sat at her cannon, her computer says the turret is ready");
  const sight = gunnerSight();
  expect(sight && sight.at[1] > y + 1, "freighter: sat at her cannon, the view goes up to its sight, above its swivel",
    sight ? `sight at ${sight.at.join(",")}, cannon at ${y}` : "the view stayed with the player");
  const glass = must(`block ${x},${y - 1},${z}`).id;
  const creeper = [x + 0.5, y + TURRET_TARGET_UP, z + 0.5 - TURRET_TARGET_AHEAD];
  must(`server difficulty easy; server summon creeper ${creeper.join(" ")} {NoAI:1b,PersistenceRequired:1b}`);
  // The sight moves a little as the look turns, so the aim is taken from it twice.
  aimFromSight([creeper[0], creeper[1] + CREEPER_MIDDLE, creeper[2]]);
  const aimed = aimFromSight([creeper[0], creeper[1] + CREEPER_MIDDLE, creeper[2]]);
  expect(aimed?.locked === true, "freighter: aimed from her cannon's sight at a creeper, the sight locks on to it");
  expect(heard("skyliners:ball_turret_hiss", sat, 0), "freighter: as her cannon swings round to aim, its hydraulics hiss");
  must(`key down key.attack; wait ${TURRET_FIRE_TICKS}t; key up key.attack; wait 5t`);
  const [reply] = send(`server data get entity @e[type=creeper,limit=1,sort=nearest] Health`);
  const health = reply.ok === false ? 0 : Number(/(-?[0-9.]+)f/.exec(JSON.stringify(reply))?.[1] ?? CREEPER_HEALTH);
  must("server kill @e[type=creeper]; server difficulty peaceful");
  expect(health < CREEPER_HEALTH, "freighter: holding attack, her cannon strikes a creeper in the sky where the sight looks", `health ${health} of ${CREEPER_HEALTH}`);
  turretBlastsBlocks(creeper);
  expect(must(`block ${x},${y - 1},${z}`).id === glass, "freighter: firing, the gunner breaks none of her, the dome's glass whole");
  turretHoldsFireLow();
  turretOverheats();
  const gotUp = send(`key down left_shift; wait ${VIEW_BACK_TICKS}t; skyliners:gunner; key up left_shift; wait 5t`).find((reply) => "on" in reply);
  expect(gotUp?.on === false, "freighter: sneaking to get up from her gunner's seat gives the player their own view back at once");
}

// A block of stone hung in the sky where the creeper was, three by three, aimed at from her cannon's sight and shot:
// her bolts blast it away, or most of it.
function turretBlastsBlocks([cx, cy, cz]) {
  const [x, y, z] = [Math.floor(cx), Math.floor(cy) + 1, Math.floor(cz)];
  must(`server fill ${x - 1} ${y - 1} ${z} ${x + 1} ${y + 1} ${z} stone; wait ${TURRET_COOLED_TICKS}t`);
  aimFromSight([x + 0.5, y + 0.5, z + 1]);
  aimFromSight([x + 0.5, y + 0.5, z + 1]);
  must(`key down key.attack; wait ${TURRET_FIRE_TICKS}t; key up key.attack; wait 10t`);
  const left = [-1, 0, 1].flatMap((dx) => [-1, 0, 1].map((dy) => must(`block ${x + dx},${y + dy},${z}`).id)).filter((id) => id === "minecraft:stone").length;
  must(`server fill ${x - 1} ${y - 1} ${z} ${x + 1} ${y + 1} ${z} air`);
  expect(left < TURRET_BLASTED_LEFT, "freighter: her cannon's bolts blast away a block of stone they strike", `${left} of 9 stone left`);
}

// Looked down at her hull, lower than her cannon's barrels go, it holds its fire: the trigger only clicks.
function turretHoldsFireLow() {
  must(`turn 0 ${TURRET_LOOK_DOWN}; wait 10t`);
  const pulled = now();
  must(`key down key.attack; wait ${TURRET_HELD_TICKS}t; key up key.attack; wait 5t`);
  expect(played("skyliners:ball_turret_shot", pulled).length === 0 && played("block.dispenser.fail", pulled).length > 0,
    "freighter: looking down at her hull, lower than her cannon's barrels go, the trigger only clicks");
}

// Held down well past twenty bolts, her cannon overheats with a hiss and holds its fire a while, its sight's
// thermometer flashing; let go and given a moment to cool, it fires again.
function turretOverheats() {
  must(`turn 0 ${-TURRET_TARGET_UP}; wait ${TURRET_COOLED_TICKS}t`);
  const firing = now();
  must(`key down key.attack; wait ${TURRET_OVERHEAT_TICKS}t`);
  const hot = now();
  must(`wait ${TURRET_HOT_WATCHED_TICKS}t; key up key.attack`);
  const hissed = played("block.fire.extinguish", firing).length > 0;
  const shotsWhileHot = played("skyliners:ball_turret_shot", hot).length;
  expect(hissed && shotsWhileHot === 0, "freighter: fired twenty bolts in a row, her cannon overheats with a hiss and holds its fire",
    `${hissed ? "hissed" : "no hiss"}, ${shotsWhileHot} shots while hot`);
  must(`wait ${TURRET_COOLED_TICKS}t`);
  const cooled = now();
  must(`key down key.attack; wait ${TURRET_FIRE_TICKS}t; key up key.attack; wait 5t`);
  expect(played("skyliners:ball_turret_shot", cooled).length > 0, "freighter: once her cannon has cooled, it fires again");
}

// Where the player sees from a laser cannon's sight, whether it is locked on, and its rockets as it shows them: how
// many, and how far the next is loaded; undefined while they see from their own eyes.
function gunnerSight() {
  const reply = must("skyliners:gunner");
  return reply.on ? { at: String(reply.at).split(",").map(Number), look: String(reply.look).split(",").map(Number), locked: reply.locked,
    rockets: reply.rockets, loading: reply.loading } : undefined;
}

// A freighter's laser cannon barrels are solid: walking across them on her dome's crown, in survival, the player stops
// against them, on a freighter laid down and aboard one assembled, where Sable collides the player with her blocks.
// Both walked straight through them, as if they were not there.
function checkFreighterBarrels() {
  layDown("freighter");
  const [cannon] = must("find skyliners:laser_cannon 128 1").rows;
  expect(cannon, "freighter: she is built with a laser cannon on her top dome");
  if (!cannon) {
    return;
  }
  const laid = acrossBarrels(cannon.at_abs, must(`block ${cannon.at_abs.join(",")}`).props?.facing);
  survival();
  must(`server tp @p ${laid.from.join(" ")} ${laid.yaw} 0; wait 20t; move forward ${BARREL_WALK_TICKS}t; wait 10t`);
  const stopped = laid.out(must("status").pos);
  creative();
  expect(stopped >= BARREL_STOPPED_OUT, "freighter: walking across her cannon's barrels, they stop the player", `${round(stopped)} blocks out from the cannon`);
  must(`skyliners:autopilot fly altitude=${ground + FREIGHTER_CLIMB} speed=0; wait ${IMPACT_ABOARD_TICKS}t; skyliners:autopilot off`, BUILD_SECONDS);
  const parked = waitForReport(freighterReport, (report) => report.phase === "off", FREIGHTER_DOWN_TICKS);
  expect(parked, "freighter: assembled and switched off, she stands on her legs", parked ? "" : `still ${freighterReport()?.phase}`);
  if (!parked) {
    return;
  }
  const freighter = findShipFlying("skyliners:engine_grille");
  const [{ at: plot }] = must(`sable:find ${freighter} skyliners:laser_cannon 1 side=server`).rows;
  const aboard = acrossBarrels(plot, must(`block ${plot.join(",")} side=server`).props?.facing);
  survival();
  must(`server execute positioned ${aboard.from.join(" ")} out_sub_level ${freighter} run tp @p ~ ~ ~ ${aboard.yaw} 0`);
  must(`wait 20t; move forward ${BARREL_WALK_TICKS}t; wait 10t`);
  const stoppedAboard = aboard.out(aboardAt(freighter));
  creative();
  expect(stoppedAboard >= BARREL_STOPPED_OUT, "freighter: aboard her assembled, walking across her cannon's barrels, they stop the player too",
    `${round(stoppedAboard)} blocks out from the cannon`);
}

// A walk across a cannon's barrels from its starboard side, its block and the way it faces given, her bow: where it
// starts, the way it goes as a yaw, and how far out to starboard of the cannon's middle a position is.
function acrossBarrels([x, y, z], facing = "north") {
  const [aheadX, aheadZ] = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] }[facing];
  const [outX, outZ] = [-aheadZ, aheadX];
  const [out, up, ahead] = BARREL_WALK_FROM;
  return {
    from: [x + 0.5 + outX * out + aheadX * ahead, y + up, z + 0.5 + outZ * out + aheadZ * ahead],
    yaw: Math.round(Math.atan2(outX, -outZ) * 180 / Math.PI),
    out: (at) => (at[0] - x - 0.5) * outX + (at[2] - z - 0.5) * outZ,
  };
}

// Where the player stands aboard a ship, in her plot's blocks.
function aboardAt(ship) {
  must(`server execute as @p at @s in_sub_level ${ship} run summon marker ~ ~ ~ {Tags:["aboard_at"]}`);
  const at = entityAt("@e[tag=aboard_at,limit=1]");
  must("server kill @e[tag=aboard_at]");
  return at;
}

// A freighter's rocket pods: sat at her laser cannon, the sight shows its four rockets; aimed at a creeper in the sky
// with use held, her cannon launches all four with a whoosh each, and they go off with a bang, striking it, the
// sight's rockets emptied; a moment later the next fills up on the sight, and her pods load again with a clack each
// until it shows all four; a quick tap of use at a block of stone hung in the sky launches just one, whose blast knocks
// out most of the stone; and none of it breaks any of her, the dome's glass over the gunner whole after.
function checkFreighterRockets() {
  layDown("freighter");
  const [cannon] = must("find skyliners:laser_cannon 128 1").rows;
  expect(cannon, "freighter: she is built with a laser cannon on her top dome");
  if (!cannon) {
    return;
  }
  const [x, y, z] = cannon.at_abs;
  const seat = gunnerSeat(x, z);
  if (!seat) {
    expect(false, "freighter: she has a gunner's seat under her dome");
    return;
  }
  must(`server tp @p ${seat[0] + 0.5} ${seat[1]} ${seat[2] - 0.5}; wait 20t`);
  sitAt(seat);
  expect(gunnerSight()?.rockets === ROCKETS, "freighter: sat at her cannon, its sight shows four rockets in its pods", `${gunnerSight()?.rockets} shown`);
  const glass = must(`block ${x},${y - 1},${z}`).id;
  const creeper = [x + 0.5, y + TURRET_TARGET_UP, z + 0.5 - TURRET_TARGET_AHEAD];
  must(`server difficulty easy; server summon creeper ${creeper.join(" ")} {NoAI:1b,PersistenceRequired:1b}`);
  aimFromSight([creeper[0], creeper[1] + CREEPER_MIDDLE, creeper[2]]);
  const aimed = aimFromSight([creeper[0], creeper[1] + CREEPER_MIDDLE, creeper[2]]);
  expect(aimed?.locked === true, "freighter: aimed from her cannon's sight at a creeper, the sight locks on to it");
  const fired = now();
  must(`key down key.use; wait ${ROCKET_SALVO_TICKS}t; key up key.use`);
  const emptied = gunnerSight();
  must(`wait ${ROCKET_FLIGHT_TICKS}t`);
  const [reply] = send(`server data get entity @e[type=creeper,limit=1,sort=nearest] Health`);
  const health = reply.ok === false ? 0 : Number(/(-?[0-9.]+)f/.exec(JSON.stringify(reply))?.[1] ?? CREEPER_HEALTH);
  send("?server kill @e[type=creeper]; server difficulty peaceful");
  const whooshes = played("skyliners:cannon_rocket_launch", fired).length;
  const bangs = played("skyliners:cannon_rocket_blast", fired).length;
  expect(emptied?.rockets === 0 && whooshes > 0 && bangs > 0,
    "freighter: holding use, her cannon launches all four rockets with a whoosh, the sight's rockets emptying, and they go off with a bang",
    `${emptied?.rockets} shown after the salvo, ${whooshes} whooshes, ${bangs} bangs heard`);
  expect(health < CREEPER_HEALTH, "freighter: her rockets strike the creeper the sight is locked on to", `health ${health} of ${CREEPER_HEALTH}`);
  rocketsLoadAgain(fired);
  rocketBlastsBlocks(creeper);
  rocketSparesHer(cannon.at_abs);
  expect(must(`block ${x},${y - 1},${z}`).id === glass, "freighter: firing rockets, the gunner breaks none of her, the dome's glass whole");
}

// A row of stone set on her deck ahead of her cannon, shot with a rocket from its sight: its blast knocks stone out,
// but leaves her deck's cover under it whole, though she stands on the ground as blocks of the world, as she does
// until she first flies.
function rocketSparesHer([x, y, z]) {
  const ahead = z + ROCKET_DECK_AHEAD;
  const deck = y - ROCKET_DECK_UNDER;
  const under = [-1, 0, 1].flatMap((dx) => [-1, 0, 1].map((dz) => `${x + dx},${deck},${ahead + dz}`));
  const cover = () => under.map((at) => must(`block ${at} side=server`).id);
  const before = cover();
  if (before.includes("air")) {
    expect(false, "freighter: her deck is where stone is set on it to be shot at", before.join(" "));
    return;
  }
  const row = `${x - 1} ${deck + 1} ${ahead} ${x + 1} ${deck + 2} ${ahead}`;
  must(`server fill ${row} stone; wait 10t`);
  aimFromSight([x + 0.5, deck + 2.4, ahead]);
  aimFromSight([x + 0.5, deck + 2.4, ahead]);
  must(`key press key.use; wait ${ROCKET_FLIGHT_TICKS}t`);
  const stone = [-1, 0, 1].flatMap((dx) => [1, 2].map((dy) => must(`block ${x + dx},${deck + dy},${ahead} side=server`).id))
    .filter((id) => id === "minecraft:stone").length;
  const changed = cover().filter((id, i) => id !== before[i]).length;
  send(`?server fill ${row} air`);
  expect(stone < ROCKET_DECK_STONE && changed === 0, "freighter: a rocket at stone on her deck blasts the stone, but none of her deck's cover under it",
    `${stone} of ${ROCKET_DECK_STONE} stone left, ${changed} of 9 blocks of her cover under it changed`);
}

// A moment after her last rocket left, the sight shows the next filling up; her pods then load again, a clack each,
// until the sight shows all four.
function rocketsLoadAgain(fired) {
  const filling = waitForReport(gunnerSight, (sight) => sight.rockets < ROCKETS && sight.loading > 0, ROCKET_LOAD_STARTS_TICKS);
  expect(filling, "freighter: a moment after her last rocket, the sight shows the next filling up", `${JSON.stringify(gunnerSight())}`);
  const full = waitForReport(gunnerSight, (sight) => sight.rockets === ROCKETS, ROCKET_RELOAD_TICKS);
  const clacks = played("skyliners:cannon_rocket_load", fired).length;
  expect(full && clacks >= ROCKETS, "freighter: her pods load again with a clack each, until the sight shows all four", `${gunnerSight()?.rockets} shown, ${clacks} clacks`);
}

// A block of stone hung in the sky where the creeper was, three by three, aimed at from her cannon's sight: a quick
// tap of use launches one rocket, whose blast knocks it away, or most of it.
function rocketBlastsBlocks([cx, cy, cz]) {
  const [x, y, z] = [Math.floor(cx), Math.floor(cy) + 1, Math.floor(cz)];
  must(`server fill ${x - 1} ${y - 1} ${z} ${x + 1} ${y + 1} ${z} stone; wait 10t`);
  aimFromSight([x + 0.5, y + 0.5, z + 1]);
  aimFromSight([x + 0.5, y + 0.5, z + 1]);
  must(`key press key.use; wait ${ROCKET_FLIGHT_TICKS}t`);
  const shown = gunnerSight()?.rockets;
  const left = [-1, 0, 1].flatMap((dx) => [-1, 0, 1].map((dy) => must(`block ${x + dx},${y + dy},${z}`).id)).filter((id) => id === "minecraft:stone").length;
  send(`?server fill ${x - 1} ${y - 1} ${z} ${x + 1} ${y + 1} ${z} air`);
  expect(shown === ROCKETS - 1, "freighter: a quick tap of use launches just one rocket, the sight showing three left", `${shown} shown`);
  expect(left < TURRET_BLASTED_LEFT, "freighter: her rocket's blast knocks out a block of stone it strikes", `${left} of 9 stone left`);
}

// Another ship shot at: from a freighter's gunner's seat, her cannon fired at a second freighter hovering abeam of her,
// a bolt pings off her hull, heard and seen where it struck with sparks and a puff of smoke, and shoves her a little
// along its way; a rocket bursts on her with a crunch and shoves her harder; a long burst of bolts never shoves her
// fast or far; and she settles back to her hover after it. Shot at before, she felt nothing and nothing was heard.
function checkShipHits() {
  const site = layDown("freighter");
  const [cannon] = must("find skyliners:laser_cannon 128 1").rows;
  const seat = cannon && gunnerSeat(cannon.at_abs[0], cannon.at_abs[2]);
  if (!seat) {
    expect(false, "ship hits: the freighter has a laser cannon and a gunner's seat under it");
    return;
  }
  standToBuild(site.x + HIT_TARGET_ABEAM, site.z);
  must("skyliners:build freighter", BUILD_SECONDS);
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + HIT_TARGET_UP} speed=0`, BUILD_SECONDS);
  const target = findShipFlying("skyliners:engine_grille");
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(up, "ship hits: the other freighter lifts off and hovers abeam of her");
  if (!up) {
    return;
  }
  // Aboard her a moment, so the minute she flies on with nobody aboard starts over, and then at the gun.
  must(`sable:tp ${target} skyliners:autopilot; wait ${LOOK_EVERY}t`);
  must(`server tp @p ${seat[0] + 0.5} ${seat[1]} ${seat[2] - 0.5}; wait 20t`);
  sitAt(seat);
  const { box, at_abs: hovered } = must(`sable:info ${target} side=server`);
  const aim = [box.min[0] + HIT_AIM_IN, box.min[1] + (box.max[1] - box.min[1]) * HIT_AIM_UP, cannon.at_abs[2] + 0.5];
  aimFromSight(aim);
  aimFromSight(aim);
  const bolt = hitAndWatch(target, "key down key.attack; wait 3t; key up key.attack", HIT_WATCH_TICKS);
  expect(heard("skyliners:impact_bolt", bolt.since, 0), "ship hits: a bolt striking the other freighter pings off her hull");
  const seen = Object.fromEntries(must(`particles since=${bolt.since}`).rows.map((row) => [row.type, row.count]));
  expect((seen.electric_spark ?? 0) > 0 && (seen.smoke ?? 0) > 0, "ship hits: sparks and a puff of smoke burst where it strikes, seen from the gun",
    `${seen.electric_spark ?? 0} sparks, ${seen.smoke ?? 0} puffs`);
  expect(bolt.shove >= BOLT_SHOVE_LEAST && bolt.along > 0, "ship hits: the bolt shoves her a little along its way",
    `speed changed by ${round(bolt.shove)} blocks a second, ${round(bolt.along)} of it along the shot, tilted ${round(bolt.tilt)} degrees`);
  const rocket = hitAndWatch(target, "key press key.use", HIT_WATCH_TICKS);
  expect(heard("skyliners:impact_rocket", rocket.since, 0), "ship hits: a rocket bursts on her hull with a crunch");
  expect(rocket.along > bolt.along && rocket.tilt > bolt.tilt * ROCKET_ROCKS_MORE, "ship hits: a rocket shoves and rocks her harder than a bolt",
    `rocket ${round(rocket.along)} blocks a second away from the gun and ${round(rocket.tilt)} degrees, bolt ${round(bolt.along)} and ${round(bolt.tilt)}`);
  must(`wait ${HIT_SETTLE_TICKS}t`);
  const burst = hitAndWatch(target, `key down key.attack; sample sable:info ${target} side=server every 2t while wait ${HIT_BURST_TICKS}t; key up key.attack`,
    HIT_WATCH_TICKS);
  const pings = played("skyliners:impact_bolt", burst.since).length;
  expect(pings > 1 && burst.shove <= HIT_BURST_FASTEST && burst.moved <= HIT_BURST_FARTHEST,
    "ship hits: a long burst of bolts shoves her no faster or farther than a rocket might",
    `${pings} bolts struck her, speed changed by ${round(burst.shove)} blocks a second, moved ${round(burst.moved)} blocks`);
  const { vel, at_abs: settled } = must(`wait ${HIT_SETTLE_TICKS}t; sable:info ${target} side=server`);
  expect(Math.hypot(...vel) <= HIT_SETTLED_SPEED && Math.abs(settled[1] - hovered[1]) <= HIT_SETTLED_WITHIN, "ship hits: she settles back to her hover after it",
    `drifting at ${round(Math.hypot(...vel))} blocks a second, at y ${round(settled[1])}, hovering at ${round(hovered[1])} before`);
}

// A ship's motion through a hit, the action that fires it run first, watching her too if it samples her, and then her
// watched every two ticks for so long: how much her speed changed at most from what it was, in blocks a second, and how
// much of that away from the gun; how far her pitch or roll changed at most, in degrees; how far she moved, in blocks;
// and the tick it began.
function hitAndWatch(target, action, ticks) {
  const before = must(`sable:info ${target} side=server`);
  const since = now();
  const replies = send(`${action}; sample sable:info ${target} side=server every 2t x${ticks / 2}`, BUILD_SECONDS);
  const failed = replies.find((reply) => reply.ok === false);
  if (failed) {
    throw new Error(`${action}: ${failed.err} ${failed.arg ?? ""}`);
  }
  const rows = replies.flatMap((reply) => reply.rows ?? []).filter((row) => row.vel);
  const change = (row) => row.vel.map((v, i) => v - before.vel[i]);
  const shove = Math.max(...rows.map((row) => Math.hypot(...change(row))));
  const away = [Math.sign(before.at_abs[0] - must("status").pos[0]), 0, 0];
  const along = Math.max(...rows.map((row) => change(row).reduce((sum, v, i) => sum + v * away[i], 0)));
  const tilt = Math.max(...rows.map((row) => Math.max(Math.abs(row.pitch - before.pitch), Math.abs(row.roll - before.roll))));
  const moved = Math.max(...rows.map((row) => Math.hypot(...row.at_abs.map((v, i) => v - before.at_abs[i]))));
  return { shove, along, tilt, moved, since };
}

// Sits in a seat from where the player stands; if something stands in the way, from the floor beside it, where the
// seat is in reach and in plain sight.
function sitAt(seat) {
  const [{ ok }] = send(`use ${seat.join(",")}; wait 20t`);
  if (ok !== false) {
    return;
  }
  // A seat's back stands a block over it, in the way from behind or the side: it seats them as well.
  const back = [seat[0], seat[1] + 1, seat[2]];
  if (must(`block ${back.join(",")}`).id === "skyliners:pilot_seat" && send(`use ${back.join(",")}; wait 20t`)[0].ok !== false) {
    return;
  }
  const beside = [[1, 0], [-1, 0], [0, 1], [0, -1]].map(([dx, dz]) => [seat[0] + dx, seat[1], seat[2] + dz])
    .find((spot) => must(`block ${spot.join(",")}`).id === "air" && must(`block ${spot[0]},${spot[1] - 1},${spot[2]}`).id !== "air");
  if (beside) {
    must(`server tp @p ${beside[0] + 0.5} ${beside[1]} ${beside[2] + 0.5}; wait 10t; use ${seat.join(",")}; wait 20t`);
  }
}

// Turns the seated player to look at a point from the cannon's sight they see from; what the sight then says.
function aimFromSight(point) {
  const sight = gunnerSight();
  if (!sight) {
    return undefined;
  }
  const [toX, toY, toZ] = [point[0] - sight.at[0], point[1] - sight.at[1], point[2] - sight.at[2]];
  const toward = Math.atan2(-toX, toZ) * 180 / Math.PI;
  const up = Math.atan2(toY, Math.hypot(toX, toZ)) * 180 / Math.PI;
  must(`turn ${round(toward)} ${round(-up)}; wait 10t`);
  return gunnerSight();
}

// The laser crossbow: one click fires it at once, with no holding, an amber bolt that the player sees leave the
// crossbow's muzzle in front of them and fly out as a short bolt, not a ray from the muzzle, showing a while, not for a
// blink; it strikes a creeper and leaves a cow unharmed; held down it keeps firing until it overheats and vents,
// hissing, holding its fire, and once cooled it fires again. The creeper is shot with the world on easy, as a peaceful
// world takes hostile mobs away at once. The burst is held in one step: sent click by click, the time between them
// would let it cool.
function checkLaserCrossbow() {
  const x = nextSite++ * SITE_APART;
  creative();
  must(`server tp @p ${x} ${ground} 0; turn 0 0; wait 40t; server item replace entity @p weapon.mainhand with skyliners:laser_crossbow`);
  const clicked = now();
  must("turn 0 0; wait 10t; use; wait 5t");
  expect(heard("skyliners:laser_crossbow_shot", clicked, 0), "laser crossbow: one click fires it at once, without holding use");
  seesItsBolt("laser crossbow: fired", "wait 10t; use", "", true);
  must("server difficulty easy");
  const struck = shootAt("creeper", x);
  must("server difficulty peaceful");
  expect(struck < CREEPER_HEALTH, "laser crossbow: its bolt strikes a creeper", `health ${struck} of ${CREEPER_HEALTH}`);
  const spared = shootAt("cow", x);
  expect(spared === COW_HEALTH, "laser crossbow: its bolt never hurts a cow", `health ${spared} of ${COW_HEALTH}`);
  must(`turn 0 0; wait ${CROSSBOW_COOLED_TICKS}t`);
  const burst = now();
  must(`use hold=${CROSSBOW_BURST_TICKS}t; wait 5t`);
  const shots = played("skyliners:laser_crossbow_shot", burst).length;
  const vented = heard("skyliners:ramp_steam", burst, 0);
  expect(vented && shots >= CROSSBOW_LEAST_BURST && shots < CROSSBOW_BURST_TICKS / CROSSBOW_REPEAT_TICKS,
    "laser crossbow: held down it keeps firing, then overheats and vents, hissing, and holds its fire", `${shots} shots, ${vented ? "vented" : "never vented"}`);
  must(`wait ${CROSSBOW_COOLED_TICKS}t`);
  const cooled = now();
  must("use; wait 10t");
  expect(heard("skyliners:laser_crossbow_shot", cooled, 0), "laser crossbow: once cooled, it fires again");
}

// Fires the laser crossbow in hand by a script ending in its use, and looks at its bolt as the player sees it: drawn
// leaving the crossbow's muzzle in front of them, not where the server had them, and flying out as a short bolt rather
// than a ray from the muzzle; fired where it flies its whole range, it is still shown a moment later, not gone in a
// blink. Fired where it soon strikes something, as aboard a ship, it rightly vanishes as it gets there, having no
// length left by the time it is looked at; a ray from the muzzle would still be far too long.
function seesItsBolt(what, fire, aimed = "", fliesItsRange = false) {
  const before = must("skyliners:bolts").received ?? 0;
  const bolt = must(`${fire}; wait ${BOLT_LOOKED_FOR_TICKS}t; skyliners:bolts`);
  const [right, up, ahead] = (bolt.from_camera ?? "0,0,-99").split(",").map(Number);
  const fromEye = Math.hypot(right, up, ahead);
  expect(bolt.received > before && bolt.from_muzzle === true && ahead > 0 && fromEye <= BOLT_NEAR_EYE,
    `${what}, its bolt is drawn leaving the crossbow's muzzle in front of the player`,
    `${bolt.received - before} sent, from the muzzle ${bolt.from_muzzle}, from the eye ${right}, ${up}, ${ahead}${aimed ? `, ${aimed}` : ""}`);
  expect(bolt.flown >= BOLT_FLOWN_LEAST && (bolt.streak > 0 || !fliesItsRange) && bolt.streak <= BOLT_LONGEST,
    `${what}, its bolt flies out as a short bolt, not a ray from the muzzle`, `its tail ${bolt.flown} blocks out, ${bolt.streak} blocks long`);
  if (fliesItsRange) {
    const later = must(`wait ${BOLT_LATER_TICKS}t; skyliners:bolts`);
    expect(later.shown >= BOLT_SHOWN_TICKS, `${what}, its bolt shows a while, not for a blink`, `shown ${later.shown} ticks`);
  }
}

// Summons a mob that stands still in front of the player and fires the laser crossbow at it once: its health after the
// bolt has had time to fly there, the mob then taken away.
function shootAt(mob, x) {
  must(`server summon ${mob} ${x + 0.5} ${ground} ${CROSSBOW_TARGET_AHEAD + 0.5} {NoAI:1b,PersistenceRequired:1b}; wait 10t`);
  must(`turn 0 ${CROSSBOW_AIM_DOWN}; wait 10t; use; wait ${CROSSBOW_STRIKE_TICKS}t`);
  const [reply] = send(`server data get entity @e[type=${mob},limit=1,sort=nearest] Health`);
  must(`server kill @e[type=${mob}]; wait 5t`);
  const health = Number(/(-?[0-9.]+)f/.exec(JSON.stringify(reply))?.[1]);
  return Number.isNaN(health) ? 0 : health;
}

// The laser crossbow fired aboard a freighter flying along: its bolt is drawn leaving the crossbow in the player's
// hands as they ride along with her, not blocks behind, where the server had them as it fired, by the time it is drawn.
function checkLaserCrossbowInFlight() {
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
  must(`skyliners:autopilot fly altitude=${height} speed=${CROSSBOW_FLIGHT_SPEED} heading=${up.heading}`);
  const going = waitForReport(freighterReport, (report) => report.ahead >= CROSSBOW_FLIGHT_SPEED * CROSSBOW_FLIGHT_UP_TO, FREIGHTER_UP_TICKS);
  expect(going, "freighter: she flies along", `at ${freighterReport()?.ahead} blocks a second`);
  must(`sable:tp ${at.join(",")}; wait 20t; server item replace entity @p weapon.mainhand with skyliners:laser_crossbow`);
  const { aim } = must(`turn 0 ${-CROSSBOW_FLIGHT_AIM_UP}; wait 5t; status`);
  seesItsBolt("laser crossbow: fired aboard a freighter flying along", "use", `aimed at ${aim ?? "nothing in reach"}`);
  must("skyliners:autopilot land");
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

// Opens the autopilot's screen of the freighter the player is beside, set to fly to a pin; a function reading how
// bright its landing picture shows now, its luma from 0 to 255.
function openLandingPicture() {
  const freighter = findShipFlying("skyliners:engine_grille");
  const { at } = must(`sable:find ${freighter} skyliners:autopilot 1 side=server`).rows[0];
  const screen = must(`use ${at.join(",")}; wait 20t; screen`);
  const [tileX, tileY, tileW, tileH] = screen.rows.find((row) => row.kind === "LandToggle").bounds;
  const [screenW, screenH] = screen.size;
  const [middleX, middleY] = [tileX + tileW / 2, tileY + tileH / 2];
  const half = LAKE_PICTURE_SIZE / 2;
  const region = `${(middleX - half) / screenW},${(middleY - half) / screenH},${(middleX + half) / screenW},${(middleY + half) / screenH}`;
  return () => must(`wait 5t; pixels landing=${region}`).landing.luma;
}

// A freighter set to land at a pin in a lake, with no dry ground within reach of it: she holds over the pin and says
// so, with the low note, rather than come down in the water, and her autopilot's screen shows its landing picture
// dimmed for those who cannot read the message.
function checkFreighterLake() {
  const site = layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const [x, z] = [site.x, site.z - PAD_PIN_AHEAD];
  must(`server fill ${x - LAKE_REACH} ${ground - 1} ${z - LAKE_REACH} ${x + LAKE_REACH} ${ground - 1} ${z + LAKE_REACH} water`);
  if (!rideFreighterUp(head)) {
    return;
  }
  const since = now();
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=${DRAG_CRUISE} pin=${x},${z} land=true; wait 10t`);
  const landingPicture = openLandingPicture();
  const lit = landingPicture();
  expect(waitForMessage("*holding*", since, LAKE_TICKS), "freighter: set to land in a lake, she says she finds no ground");
  expect(heard("minecraft:block.note_block.pling", since, 0), "freighter: and the low note says it to those who cannot read");
  const dimmed = landingPicture();
  expect(dimmed < lit * LAKE_PICTURE_DIMMED, "freighter: her autopilot's screen shows its landing picture dimmed as she holds, finding no ground", `luma ${dimmed}, ${lit} before`);
  must("close");
  const near = (report) => Math.hypot(...String(report.middle).split(",").map(Number).map((at, i) => at - [x, z][i]));
  const holding = waitForReport(freighterReport, (report) => near(report) <= LAKE_HOLD_WITHIN, LAKE_TICKS) ?? freighterReport();
  must("wait 200t");
  const still = freighterReport();
  expect(still.phase === "cruise" && near(still) <= LAKE_HOLD_WITHIN, "freighter: she holds over the pin, not coming down",
    `${still.phase}, ${near(still).toFixed(1)} blocks from it, earlier ${near(holding).toFixed(1)}`);
}

// Stone standing under the middle of a freighter as she hovers, taller than her belly's clearance and clear of her
// feet, and, with a lake, water round it past where she may come down, so she finds nowhere else to try. Where it
// stands, as x and z.
function stoneUnderHer(lake) {
  const [x, z] = String(freighterReport().middle).split(",").map(Number);
  if (lake) {
    must(`server fill ${x - LAKE_REACH} ${ground - 1} ${z - LAKE_REACH} ${x + LAKE_REACH} ${ground - 1} ${z + LAKE_REACH} water`);
  }
  must(`server fill ${x - PAD_STONE_REACH} ${ground - 1} ${z - PAD_STONE_REACH} ${x + PAD_STONE_REACH} ${ground + PAD_STONE_HIGH - 1} ${z + PAD_STONE_REACH} stone`);
  return [x, z];
}

// Waits for a freighter set down on what her feet do not reach to give the landing up: her report once she climbs back.
function waitForBalk() {
  const back = waitForReport(freighterReport, (report) => report.phase === "climb" || report.phase === "cruise", BALKED_WITHIN_TICKS);
  const last = back ?? freighterReport();
  expect(back, "freighter: resting on stone with her legs in the air, she gives the landing up rather than sit there for ever",
    `${last?.phase}, at height ${last?.altitude}, climbing at ${last?.climb}, legs ${last?.legs}`);
  return back;
}

// Told to land where she is over stone taller than her belly's clearance, where she does not look for ground first, a
// freighter climbs back off it, says so with her computer's voice and a rising note as well as in words, and comes down
// on clear ground beside it, level on her legs. With the fault she sat on the stone with her legs in the air, for ever.
function checkFreighterBalked() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  if (!rideFreighterUp(head)) {
    return;
  }
  const [x, z] = stoneUnderHer(false);
  const since = now();
  must("skyliners:autopilot land");
  if (!waitForBalk()) {
    return;
  }
  expect(heard("skyliners:freighter_computer_balked", since, 0), "freighter: her computer says she cannot set down there");
  expect(heard("minecraft:block.note_block.pling", since, 0), "freighter: and a note says it to those who cannot read");
  expect(waitForMessage("*clear*ground*", since, 0), "freighter: and she says in words that she is looking for clear ground");
  const down = waitForReport(freighterReport, (report) => report.phase === "off", PAD_DOWN_TICKS);
  const last = down ?? freighterReport();
  expect(down, "freighter: she finds clear ground near the stone and comes down on it", `${last?.phase}, pad ${last?.pad}, middle ${last?.middle}`);
  if (!down) {
    return;
  }
  const lean = Math.max(Math.abs(down.tilt_front), Math.abs(down.tilt_right));
  expect(lean < PAD_LEVEL_WITHIN, "freighter: she stands level on her legs beside the stone", `leaning ${lean} degrees, middle ${down.middle}`);
  const [mx, mz] = String(down.middle).split(",").map(Number);
  const away = Math.hypot(mx - x, mz - z);
  expect(away > PAD_STONE_REACH && away <= PAD_REACH + 2, "freighter: she comes down off the stone, near it", `${away.toFixed(1)} blocks from it`);
}

// A freighter far from the world's middle, where Sable's physics rounds her position so coarsely that she cannot creep:
// set to land at a pin she slows onto the spot she found and stops some blocks short of it, her speed still reading as
// moving, and comes down where she has stopped. Before, she held there for ever.
function checkFreighterFarPad() {
  const site = layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const [x, z] = [site.x, site.z - FAR_PAD_AHEAD];
  must(`server gamerule commandModificationBlockLimit ${2 * UNEVEN_FILL_MOST}; server tp @p ${x} ${ground + UNEVEN_LOOK_UP} ${z}; wait ${CHUNKS_TICKS}t; settle`, BUILD_SECONDS);
  must(`server fill ${x - FAR_PAD_LAKE} ${ground - 1} ${z - FAR_PAD_LAKE} ${x + FAR_PAD_LAKE} ${ground - 1} ${z + FAR_PAD_LAKE} water`);
  must(`server gamerule commandModificationBlockLimit ${FILL_LIMIT}; server tp @p ${site.x} ${ground} ${site.z}; wait ${CHUNKS_TICKS}t; settle`, BUILD_SECONDS);
  if (!rideFreighterUp(head)) {
    return;
  }
  const since = now();
  const down = landFreighterAt(head, x, z, "on clear ground well away from the pin");
  if (down) {
    expect(waitForMessage("*blocks*from*pin*", since, 0), "freighter: she says how far from the pin she is going down");
    expect(heard("minecraft:block.note_block.pling", since, 0), "freighter: and a note says it to those who cannot read");
  }
}

// Runs a check whose lake is smaller than her search would otherwise get past, her search as small as it was built for.
function withSmallSearch(check) {
  must(`config skyliners server freighter.padReach ${SMALL_LAKE_SEARCH}`);
  try {
    check();
  } finally {
    send("?config reset");
  }
}

function checkFreighterFarLanding() {
  const site = layDown("freighter", FAR_SITE);
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  if (rideFreighterUp(head)) {
    landFreighterAt(head, site.x, site.z - PAD_PIN_AHEAD, "far from the world's middle");
  }
}

// A freighter set to land on a long, gentle slope, too steep for all her feet to stand within a block of each other:
// her legs telescope, each to its own ground, and she stands with her hull level, her ramp neither stuck moving nor
// left waiting. Before, she held over the pin for ever, finding no ground.
function checkFreighterUnevenLanding() {
  const site = layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const [x, z] = [site.x, site.z - UNEVEN_PIN_AHEAD];
  must(`server gamerule commandModificationBlockLimit ${UNEVEN_FILL_MOST}; server tp @p ${x} ${ground + UNEVEN_LOOK_UP} ${z}; wait ${CHUNKS_TICKS}t; settle`, BUILD_SECONDS);
  for (let step = 1; step <= UNEVEN_STEPS; step++) {
    must(`server fill ${x - UNEVEN_REACH + step * UNEVEN_STEP_WIDE} ${ground + step - 1} ${z - UNEVEN_REACH} ${x + UNEVEN_REACH} ${ground + step - 1} ${z + UNEVEN_REACH} stone`);
  }
  must(`server gamerule commandModificationBlockLimit ${FILL_LIMIT}; server tp @p ${site.x} ${ground} ${site.z}; wait ${CHUNKS_TICKS}t; settle`, BUILD_SECONDS);
  if (!rideFreighterUp(head)) {
    return;
  }
  const down = landFreighterAt(head, x, z, "on sloping ground");
  if (!down) {
    return;
  }
  const lean = Math.max(Math.abs(down.tilt_front), Math.abs(down.tilt_right));
  expect(lean < UNEVEN_LEVEL_WITHIN, "freighter: her legs telescope, each to its own ground, and she stands level", `leaning ${lean} degrees`);
  const freighter = findShipFlying("skyliners:engine_grille");
  const { at: panelAt } = must(`sable:find ${freighter} skyliners:ramp_panel 1 side=server`).rows[0];
  must(`wait ${RAMP_DOWN_TICKS}t`);
  const lamp = must(`block ${panelAt.join(",")} side=server`).props?.status;
  expect(lamp !== "busy", "freighter: on the slope, her ramp is not left moving", `lamp ${lamp}`);
  must(`sable:tp ${freighter} skyliners:autopilot; wait ${LOOK_EVERY}t; leave; join world ${WORLD}; wait ${RELOADED_SETTLE_TICKS}t`, BUILD_SECONDS);
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise" && report.legs === "up", FREIGHTER_UP_TICKS);
  expect(up, "freighter: saved and joined again on the slope, she lifts off her telescoped legs and draws them up", up ? "" : `still ${freighterReport()?.phase}, legs ${freighterReport()?.legs}`);
  if (up) {
    landFreighterAt(head, site.x, site.z + UNEVEN_FLAT_BEHIND, "on flat ground after the slope");
  }
}

// Ground higher under a freighter's nose than under the rest of her: one foot touches first and she settles from
// there as she comes down, which is not a landing she cannot finish. Set going on stopping for some seconds without
// settling, she would climb back off it and never land.
function checkFreighterBalkedSlope() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  if (!rideFreighterUp(head)) {
    return;
  }
  const hovering = freighterReport();
  const [x, z] = String(hovering.middle).split(",").map(Number);
  const nose = hovering.heading * Math.PI / 180;
  const [footX, footZ] = [Math.round(x + Math.sin(nose) * BALKED_NOSE_FOOT_AHEAD), Math.round(z - Math.cos(nose) * BALKED_NOSE_FOOT_AHEAD)];
  must(`server fill ${footX - BALKED_STEP_REACH} ${ground} ${footZ - BALKED_STEP_REACH} ${footX + BALKED_STEP_REACH} ${ground + BALKED_STEP_HIGH - 1} ${footZ + BALKED_STEP_REACH} stone`);
  const since = now();
  must("skyliners:autopilot land");
  const down = waitForReport(freighterReport, (report) => report.phase === "off" || report.phase === "climb" || report.phase === "cruise", BALKED_WITHIN_TICKS);
  const last = down ?? freighterReport();
  expect(down?.phase === "off", "freighter: coming down with one foot on higher ground, she settles and switches off", `${last?.phase}, at height ${last?.altitude}, climbing at ${last?.climb}`);
  expect(!heard("skyliners:freighter_computer_balked", since, 0), "freighter: and she does not give the landing up");
}

// A freighter coming down with the server far behind: how slowly the server is made to run decides how long her
// landing takes, but it is not a landing she cannot finish, however long it takes.
function checkFreighterBalkedLag() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  if (!rideFreighterUp(head)) {
    return;
  }
  const since = now();
  must(`load server ${BALKED_LAG_MILLIS}; skyliners:autopilot land`);
  const down = waitForReport(freighterReport, (report) => report.phase === "off" || report.phase === "climb" || report.phase === "cruise", BALKED_LAG_WITHIN_TICKS);
  const last = down ?? freighterReport();
  must("load off");
  expect(down?.phase === "off", "freighter: with the server running behind, she still comes down and switches off", `${last?.phase}, at height ${last?.altitude}, climbing at ${last?.climb}`);
  expect(!heard("skyliners:freighter_computer_balked", since, 0), "freighter: and she does not give the landing up");
}

// Stone in the middle of a lake, under a freighter with someone aboard: she climbs back off it, finds nowhere else to
// come down, holds and says so, and comes down no more by herself; told to switch off, she comes down on it again and
// lets go there, her thrusters quiet, rather than climbing back or staying on for ever.
function checkFreighterBalkedNowhere() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  if (!rideFreighterUp(head)) {
    return;
  }
  stoneUnderHer(true);
  const since = now();
  must("skyliners:autopilot land");
  if (!waitForBalk()) {
    return;
  }
  expect(waitForMessage("*holding*", since, LAKE_TICKS), "freighter: with no ground to try near the stone, she says she holds");
  expect(heard("skyliners:freighter_computer_holding", since, 0), "freighter: her computer says so too");
  expect(heard("minecraft:block.note_block.pling", since, 0), "freighter: and the low note says it to those who cannot read");
  must(`wait ${BALKED_HOLD_WATCHED_TICKS}t`);
  const still = freighterReport();
  expect(still.phase === "cruise", "freighter: she holds, not coming back down to try again by herself", `${still.phase}, at height ${still.altitude}`);
  const again = now();
  must("skyliners:autopilot land");
  if (!waitForBalk()) {
    return;
  }
  expect(waitForMessage("*either*", again, LAKE_TICKS), "freighter: told to land on it once more, she gives it up and says she holds");
  expect(heard("skyliners:freighter_computer_holding", again, 0), "freighter: her computer says so too");
  must(`wait ${BALKED_HOLD_WATCHED_TICKS}t`);
  const holding = freighterReport();
  expect(holding.phase === "cruise", "freighter: and she tries no third landing by herself", `${holding.phase}, at height ${holding.altitude}`);
  const told = now();
  must("skyliners:autopilot off");
  const off = waitForReport(freighterReport, (report) => report.phase === "off", BALKED_WITHIN_TICKS);
  const last = off ?? freighterReport();
  expect(off, "freighter: told to switch off over the stone, she comes down on it and lets go", `${last?.phase}, at height ${last?.altitude}`);
  if (!off) {
    return;
  }
  expect(off.lift === 0, "freighter: her thrusters are let go", `lift ${off.lift}`);
  expect(heard("skyliners:lookout_bell", told, 0), "freighter: and she switches off with her bell");
}

// Stone in a lake under a freighter with nobody aboard: she comes down by herself, climbs back off it, finds nowhere to
// try and holds, then comes down by herself once more and lets go on the stone, and stays switched off: she does not
// go up and down over it for as long as she is loaded.
function checkFreighterBalkedUnattended() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(up, "freighter: she lifts off with nobody aboard");
  if (!up) {
    return;
  }
  const [x, z] = stoneUnderHer(true);
  must(`server tp @p ${x + LAKE_REACH + BALKED_STAND_OFF} ${ground} ${z}; wait 20t`);
  const off = waitForReport(freighterReport, (report) => report.phase === "off", BALKED_UNATTENDED_TICKS);
  const last = off ?? freighterReport();
  expect(off, "freighter: with nobody aboard and nowhere to come down, she lets go on the stone in the end", `${last?.phase}, at height ${last?.altitude}`);
  if (!off) {
    return;
  }
  expect(off.lift === 0, "freighter: her thrusters are let go", `lift ${off.lift}`);
  must(`wait ${BALKED_UNATTENDED_WATCHED_TICKS}t`, BALKED_UNATTENDED_WATCHED_SECONDS);
  const still = freighterReport();
  expect(still.phase === "off", "freighter: switched off on the stone, she stays so, and does not go up and down over it", `${still.phase}, at height ${still.altitude}`);
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

// A freighter's cargo hold's parcel scanner, a crate waiting on its belt: used at its screen, from in front of it, it
// sweeps the crate with its lasers.
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
  must(`use ${at} at ${SCANNER_SCREEN_AT}`);
  expect(Boolean(heard("*scanner_sweep*", used, LOOK_EVERY)), "freighter: used, the parcel scanner sweeps its crate");
}

// A freighter's main engines read as engines from behind her, parked with nothing firing: straight behind each, a
// bright lip stands out round its nozzle, over it and on its side toward her middle, where it used to be only a black
// socket in her grille, and where her rim, stepping out a block beside the two second from her sides, cut into theirs.
function checkFreighterNozzles() {
  layDown("freighter");
  const relays = must("find skyliners:thrust_relay 128 64").rows.map((row) => row.at_abs);
  const sternmost = Math.min(...relays.map(([, , z]) => z));
  const sternY = relays.find((relay) => relay[2] === sternmost)[1];
  const mains = relays.filter(([, y, z]) => y === sternY && z - sternmost <= NOZZLE_MAINS_DEEP);
  const middle = mains.reduce((sum, [x]) => sum + x + 0.5, 0) / mains.length;
  const dark = [];
  for (const [x, y, z] of mains) {
    const thruster = z - 1;
    const face = thruster - standingOff(x, y, thruster);
    const axis = `${x + 0.5},${y + 0.5}`;
    const inward = Math.sign(middle - x - 0.5) || 1;
    must(`option hud off; camera at ${axis},${face - NOZZLE_CAMERA_BACK} look ${axis},${face}; wait 20t; settle`);
    const { over, inner } = must(`pixels over=@${x + 0.5},${y + 0.5 + NOZZLE_LIP_OUT},${face - NOZZLE_LIP_ASTERN} `
      + `inner=@${x + 0.5 + inward * NOZZLE_LIP_OUT},${y + 0.5},${face - NOZZLE_LIP_ASTERN} r=${NOZZLE_LIP_ACROSS}`);
    const least = Math.min(over.p90, inner.p90);
    if (least < NOZZLE_LIP_LUMA) {
      dark.push(`x ${x}: luma ${round(least)}`);
    }
  }
  must("camera reset; option hud on");
  expect(mains.length === NOZZLE_MAINS && dark.length === 0, "nozzles: behind her, each main has a bright nozzle lip all round, not a black socket or her grille",
    `${mains.length} mains${dark.length ? `, dark: ${dark.join(", ")}` : ""}`);
}

// A freighter's underside camera, as her passengers see it: with her flying, her legs drawn up, a passenger screen
// aboard shows the ground under her through it, green across the picture, where the camera used to rise into her belly
// as her legs went up and show the inside of her hull plate over half the picture.
function checkFreighterUndersideCamera() {
  layDown("freighter");
  must(`skyliners:autopilot fly altitude=${UNDERSIDE_HOVER} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise" && report.legs === "up", FREIGHTER_UP_TICKS);
  expect(up, "underside camera: she lifts off and draws her legs up", `legs ${freighterReport()?.legs}`);
  const [screen] = must(`sable:find ${freighter} skyliners:passenger_screen 1 side=server`).rows;
  if (!up || !screen) {
    return;
  }
  const at = screen.at.join(",");
  const { left, right } = must(`sable:tp ${at}; wait 10t; use ${at}; wait ${UNDERSIDE_SETTLE}t; pixels left=${UNDERSIDE_LEFT} right=${UNDERSIDE_RIGHT}`, BUILD_SECONDS);
  must("key press key.sneak 10t; wait 10t");
  const green = (half) => half.g > half.r * UNDERSIDE_GREEN && half.g > half.b * UNDERSIDE_GREEN;
  expect(green(left) && green(right), "underside camera: through it a passenger sees the ground under her, not the inside of her belly",
    `left rgb ${round(left.r)},${round(left.g)},${round(left.b)}, right ${round(right.r)},${round(right.g)},${round(right.b)}`);
}

// How many blocks a freighter's nozzle stands out past the face of her main thruster at a block, her stern to the
// north, as each client draws it: past each layer astern of it with a block of hers round its mouth.
function standingOff(x, y, thruster) {
  let standing = 0;
  while (standing < NOZZLE_STANDS_OFF_MOST) {
    const layer = thruster - standing - 1;
    const mouth = NOZZLE_ROUND_MOUTH.map(([across, up]) => `?block ${x + across},${y + up},${layer}`).join("; ");
    if (send(mouth, BUILD_SECONDS).filter((reply) => reply.id).every((reply) => reply.id === "air")) {
      break;
    }
    standing++;
  }
  return standing;
}

// An engine grille set by hand goes by the redstone it is given, dark with none, idling on a weak signal and at full on
// a strong one; a grille of a freighter's own keeps to her autopilot, however much redstone stands against it.
function checkEngineGrilleRedstone() {
  const x = nextSite++ * SITE_APART;
  creative();
  standToBuild(x, 0, 0);
  must("server give @p skyliners:engine_grille; server give @p minecraft:redstone_block; select 0");
  const grille = `${x},${ground},${GRILLE_AHEAD}`;
  const glow = (at) => Number(must(`block ${at} side=server`).props?.glow ?? 0);
  must(`place skyliners:engine_grille ${grille}; wait 5t`);
  expect(glow(grille) === 0, "grille: set by hand with no signal it stays dark");
  const block = `${x + 1},${ground},${GRILLE_AHEAD}`;
  must(`place minecraft:redstone_block ${block}; wait ${GRILLE_SETTLE_TICKS}t`);
  expect(glow(grille) === 2, "grille: a strong signal has it glowing at full");
  must(`mine ${block} timeout=5s; wait ${GRILLE_SETTLE_TICKS}t`);
  expect(glow(grille) === 0, "grille: with the signal gone it goes dark again");
  const lever = `${x + GRILLE_WIRE},${ground},${GRILLE_AHEAD}`;
  must(`server setblock ${x + GRILLE_WIRE} ${ground} ${GRILLE_AHEAD} lever[face=floor,facing=north,powered=true]; server fill ${x + 1} ${ground} ${GRILLE_AHEAD} ${x + GRILLE_WIRE - 1} ${ground} ${GRILLE_AHEAD} redstone_wire; wait ${GRILLE_SETTLE_TICKS}t`);
  expect(glow(grille) === 1, `grille: down ${GRILLE_WIRE - 1} blocks of wire, a weak signal has it idling`, `glow ${glow(grille)}`);
  must(`server tp @p ${x + GRILLE_WIRE} ${ground} ${GRILLE_AHEAD - 3}; wait 20t; use ${lever}; wait ${GRILLE_SETTLE_TICKS}t`);
  expect(glow(grille) === 0, "grille: the lever thrown off, it goes dark");

  layDown("freighter");
  const grilles = must("find skyliners:engine_grille 128 400").rows.map((row) => row.at_abs);
  const sternmost = Math.min(...grilles.map(([, , z]) => z));
  const [gx, gy, gz] = grilles.find((at) => at[2] === sternmost);
  const hers = `${gx},${gy},${gz}`;
  must(`server setblock ${gx} ${gy} ${gz - 1} minecraft:redstone_block; wait ${GRILLE_SETTLE_TICKS}t`);
  expect(glow(hers) === 0, "grille: one of a freighter's own takes no signal from redstone against it");
  must(`server setblock ${gx} ${gy} ${gz - 1} air`);
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  expect(waitForReport(freighterReport, (report) => report.lift > 0, FREIGHTER_UP_TICKS), "grille: she lifts off");
  const [{ at }] = must(`sable:find ${freighter} skyliners:engine_grille 1 side=server`).rows;
  must(`wait ${GRILLE_LIGHT_TICKS}t`);
  expect(glow(at.join(",")) === 1, "grille: her autopilot still lights her own grille as her engines idle", `glow ${glow(at.join(","))}`);
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

// Comes aboard a freighter with the client config naming someone whose birthday is tomorrow, then today as another
// account, then today as them, then again today, then lifts off with them aboard: her computer's birthday greeting, and its banner, come only on the day, and
// once from each freighter, not again as her taking off moves her autopilot into Sable's plot.
function checkBirthdayGreeting() {
  layDown("freighter");
  const [stand] = must("find skyliners:bandstand 128 1").rows;
  const [x, y, z] = stand.at_abs;
  const [dx, dz] = STEPS[must(`block ${x},${y},${z}`).props?.facing ?? "north"];
  const aboard = `server tp @p ${x + dx * BAND_NEAR + 0.5} ${y} ${z + dz * BAND_NEAR + 0.5}`;
  const away = `server tp @p ${x} ${ground} ${z + BIRTHDAY_AWAY}; wait ${BIRTHDAY_OFF_TICKS}t`;
  const day = (date) => `${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  const today = new Date();
  const tomorrow = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1);
  // Set off her, as a birthday that comes while they stand aboard greets them there and then.
  const greetedComingAboard = (date, account) => {
    must(`${away}; config skyliners client birthdayDate ${day(date)}; config skyliners client birthdayPlayer ${account}`);
    const since = now();
    must(aboard);
    return Boolean(heard("*freighter_computer_birthday*", since, BIRTHDAY_WITHIN));
  };
  const player = must("server list").rows.at(-1).line.split(": ").pop().split(", ")[0];
  try {
    must("config skyliners client birthdayName Sam");
    expect(!greetedComingAboard(tomorrow, player), "birthday: the day before it, coming aboard a freighter greets nobody");
    expect(!greetedComingAboard(today, "Somebody_Else"), "birthday: on the day, playing as another account, coming aboard greets nobody");
    expect(greetedComingAboard(today, player), "birthday: on their birthday, coming aboard a freighter, her computer wishes them a happy birthday",
      `playing as ${player}`);
    expect(!greetedComingAboard(today, player), "birthday: gone and come aboard her again that day, she does not greet them twice");
    const since = now();
    must(`skyliners:autopilot fly altitude=${ground + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
    const flying = must(`wait ${BIRTHDAY_LIFT_TICKS}t; sable:info here brief`);
    expect(flying.agl > BIRTHDAY_LIFTED && !heard("*freighter_computer_birthday*", since, 0), "birthday: lifting off with them aboard, she does not greet them again",
      `${flying.agl} blocks up`);
  } finally {
    send("?config reset");
  }
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
  const scale = must("server execute as @e[type=skyliners:tardigrade,sort=nearest,limit=1] run attribute @s minecraft:generic.scale get").rows[0]?.line ?? "";
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
  const [{ at_abs: middle }] = must("find skyliners:laser_cannon 128 1").rows;
  const { along, bow } = bowOf(middle);
  const across = 2 - along;
  const engines = prongEngines(middle);
  const prongs = [-1, 1].map((side) => engines.filter((at) => Math.sign(at[across] - middle[across]) === side));
  const tips = prongs.filter((prong) => prong.length > 0).map((prong) => prong.reduce((a, b) => ((b[along] - a[along]) * bow > 0 ? b : a)));
  const unlit = tips.filter((at) => !stripsNear(backFrom(at, along, bow, TIP_STRIPS_BACK)));
  expect(tips.length === 2 && unlit.length === 0, "freighter: her bow room's strips run down both prongs' tunnels to their tips, not stopping half way",
    `${tips.length} prong tips${unlit.map((at) => `, none near the tip at ${at.join(",")}`).join("")}`);
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const seat = backFrom(head, along, bow, SEATS_BEHIND_HEAD);
  must(`server tp @p ${seat[0]} ${seat[1]} ${seat[2]}; wait 10t`);
  const carpet = must(`find skyliners:cabin_floor ${COCKPIT_FLOOR_NEAR} 1`).rows;
  expect(carpet.length === 0, "freighter: her cockpit has one floor, no patch of carpet left before her console",
    carpet.map((row) => row.at_abs.join(",")).join("; "));
}

// The yaw and pitch that look from an eye at a point, as the turn command takes them, in degrees.
function aimFrom(eye, point) {
  const [dx, dy, dz] = [0, 1, 2].map((axis) => point[axis] - eye[axis]);
  const yaw = (Math.atan2(-dx, dz) * 180) / Math.PI;
  const pitch = (-Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI;
  return `${round(yaw)} ${round(pitch)}`;
}

// The ways round, as a block's facing, a unit step along the ground and the yaw that looks that way; and the way a
// quarter turn clockwise from each, seen from above.
const WAYS = { north: [0, -1, 180], east: [1, 0, -90], south: [0, 1, 0], west: [-1, 0, 90] };
const CLOCKWISE = { north: "east", east: "south", south: "west", west: "north" };

// Presses a freighter's canopy dimmer at a point of its face, so far along it toward its side a quarter turn clockwise
// from the way it faces, seen from above, and up from its foot, standing two blocks out in front of it; its face is
// against her wall.
function pressDimmer(dimmer, facing, along, up) {
  const [x, y, z] = dimmer;
  const [fx, fz] = WAYS[facing];
  const [rx, rz] = WAYS[CLOCKWISE[facing]];
  const eye = [x + 0.5 + fx * 2, y - 1 + EYE_HEIGHT, z + 0.5 + fz * 2];
  const back = 0.5 - DIMMER_FACE;
  const point = [x + 0.5 - fx * back + rx * (along - 0.5), y + up, z + 0.5 - fz * back + rz * (along - 0.5)];
  must(`server tp @p ${eye[0]} ${y - 1} ${eye[2]}; turn ${aimFrom(eye, point)}; wait 5t; use any; wait ${CANOPY_SETTLE}t`);
}

// How bright the sky ahead looks through a freighter's canopy from her pilot's seat, its luma from 0 to 255: seated,
// looking the way her nose points and up a little.
function skyThroughCanopy(seat, nose) {
  const [, , yaw] = WAYS[nose];
  must(`server tp @p ${seat[0] + 0.5} ${seat[1]} ${seat[2] + 0.5}; wait 10t; use ${seat.join(",")}; wait 10t; turn ${yaw} -20; wait ${CANOPY_SETTLE}t`);
  const luma = must(`pixels ${CANOPY_SKY}`).sky.luma;
  must("key press key.sneak 10t; wait 10t");
  return luma;
}

// How bright her cockpit looks through a freighter's canopy from outside, its luma from 0 to 255: from out ahead of her
// nose at the height of her lining, looking back at it through her front window.
function cockpitThroughCanopy(lining, nose) {
  const [fx, fz] = WAYS[nose];
  const middle = lining.map((at) => at + 0.5);
  const eye = [middle[0] + fx * CANOPY_OUTSIDE, middle[1], middle[2] + fz * CANOPY_OUTSIDE];
  must(`camera at ${eye.join(",")} look ${middle.join(",")}; wait ${CANOPY_SETTLE}t`);
  const luma = must(`pixels cockpit=@${middle.join(",")} r=1`).cockpit.luma;
  must("camera reset");
  return luma;
}

// A freighter's canopy: one smooth round dome drawn by its lining, inside and out, not her glass and struts stepping
// round her curve, all of which draw nothing; and dimmed by her dimmer, the view through it darkens with her glass, as
// smooth, from her seat and from outside alike. With the fault her struts were whole slabs of her cover drawn from
// outside, stepping and crossing each other with hard edges all over her canopy.
function checkFreighterCanopy() {
  ship("freighter");
  const linings = must("find skyliners:canopy_lining 128 2").rows;
  expect(linings.length === 1, "freighter: her canopy has its lining", `${linings.length} linings`);
  const unlined = must("find skyliners:dimming_glass[lined=false] 128 1").count ?? 0;
  const lined = must("find skyliners:dimming_glass[lined=true] 128 1").count ?? 0;
  expect(unlined === 0 && lined > 0, "freighter: her canopy's glass draws nothing, its lining drawing it inside and out",
    `${lined} lined, ${unlined} not`);
  const struts = must("find skyliners:canopy_frame 128 1").count ?? 0;
  expect(struts === 0, "freighter: her canopy is glass all over, its ribs drawn by its lining, with no struts of her cover", `${struts} struts`);
  const [dimmer] = must("find skyliners:window_dimmer 128 1").rows;
  const [head] = must("find skyliners:autopilot 128 1").rows;
  if (!dimmer || !head || !linings[0]) {
    expect(false, "freighter: her cockpit has its dimmer, control head and canopy lining");
    return;
  }
  const rounded = must(`server data get block ${linings[0].at_abs.join(" ")} Rounded`).result ?? 0;
  expect(rounded === 1, "freighter: her canopy's lining draws a round dome, not a cone");
  const nose = must(`block ${head.at_abs.join(",")}`).props.facing ?? "north";
  const facing = must(`block ${dimmer.at_abs.join(",")}`).props.facing ?? "east";
  const seat = pilotSeat();
  const clear = skyThroughCanopy(seat, nose);
  const clearOutside = cockpitThroughCanopy(linings[0].at_abs, nose);
  pressDimmer(dimmer.at_abs, facing, ...DIMMER_DARKEST);
  const dimmed = skyThroughCanopy(seat, nose);
  const dimmedOutside = cockpitThroughCanopy(linings[0].at_abs, nose);
  expect(dimmed < clear * CANOPY_DIMMED_SHARE, "freighter: dimmed, the sky ahead through her canopy darkens from her seat",
    `luma ${round(clear)} clear, ${round(dimmed)} dimmed`);
  expect(dimmedOutside < clearOutside * CANOPY_DIMMED_SHARE, "freighter: dimmed, her cockpit through her canopy darkens from outside",
    `luma ${round(clearOutside)} clear, ${round(dimmedOutside)} dimmed`);
  pressDimmer(dimmer.at_abs, facing, ...DIMMER_CLEAR);
}

// Rain over a freighter on the ground, seen from her pilot's seat with the clouds off: none falls in her cockpit and no
// drop splashes inside her canopy's glass. With the fault, slivers of glass at the crown of her canopy let the rain fall
// straight through onto her floor, and drops splashed on her canopy's blocks just inside the smooth glass her lining
// draws, speckling it all over.
function checkFreighterRain() {
  if (spawnSync("ffmpeg", ["-version"]).error) {
    console.log("  skip the freighter's rain: it takes FFmpeg to measure");
    return;
  }
  ship("freighter");
  const [head] = must("find skyliners:autopilot 128 1").rows;
  const nose = must(`block ${head.at_abs.join(",")}`).props.facing ?? "north";
  const seat = pilotSeat();
  const [, , yaw] = WAYS[nose];
  must(`option renderClouds off; server weather rain; server tp @p ${seat[0] + 0.5} ${seat[1]} ${seat[2] + 0.5}; wait 10t; use ${seat.join(",")}; ` +
    `wait 10t; turn ${yaw} -20; wait ${RAIN_SETS_IN}t`);
  const { file } = must("screenshot 1280 hud=off");
  const rain = rainPixels(file);
  rmSync(file, { force: true });
  must("key press key.sneak 10t; server weather clear; wait 10t");
  send("?option reset");
  expect(rain <= RAIN_PIXELS_MOST, "freighter: in rain, none falls in her cockpit or splashes inside her canopy, seen from her seat",
    `${rain} rain-blue pixels`);
}

// How many pixels of a screenshot are rain's blue, read through FFmpeg.
function rainPixels(file) {
  const decode = ["-hide_banner", "-loglevel", "error", "-i", file, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"];
  const rgb = spawnSync("ffmpeg", decode, { maxBuffer: 1 << 26 }).stdout;
  let rain = 0;
  for (let at = 0; at + 2 < rgb.length; at += 3) {
    rain += rgb[at + 2] - Math.max(rgb[at], rgb[at + 1]) > RAIN_BLUE ? 1 : 0;
  }
  return rain;
}

// A laser cannon's rocket flown at a player throws them far and high with its blast: one flying in creative mode, whom
// the game's own blasts leave standing, unhurt; one in survival hurt too, coming down under their safety canopy. A
// rocket with no cannon behind it, summoned here, counts everyone as not aboard her. Before, rockets flew straight past
// players and blasts left them standing.
function checkTurretThrowsPlayers() {
  creative();
  emptyHands();
  const flying = rocketAtPlayer(true);
  expect(flying.highest >= THROWN_HIGH || flying.farthest >= THROWN_FAR, "turret: a rocket flown at a player flying in creative mode throws them far",
    `${round(flying.highest)} blocks up, ${round(flying.farthest)} blocks off`);
  expect(flying.landed.hp === flying.landed.max_hp, "turret: in creative mode, they are not hurt", `health ${flying.landed.health}`);
  send("server difficulty easy");
  survival();
  const since = now();
  const walking = rocketAtPlayer(false);
  expect(walking.highest >= THROWN_HIGH || walking.farthest >= THROWN_FAR, "turret: a rocket flown at a player in survival throws them far and high",
    `${round(walking.highest)} blocks up, ${round(walking.farthest)} blocks off`);
  expect(walking.landed.hp < walking.landed.max_hp && walking.landed.hp > 0, "turret: in survival, its blast hurts them", `health ${walking.landed.health}`);
  expect(walking.highest < THROWN_HIGH || played("skyliners:canopy_open", since).length > 0, "turret: thrown high, their safety canopy opens over them");
  send("server difficulty peaceful");
  creative();
}

// Flies a summoned rocket at the player on a site of its own, flying or standing on the ground: how high and how far it
// threw them, in blocks, and how they were once down.
function rocketAtPlayer(flies) {
  const x = nextSite++ * SITE_APART + 0.5;
  const y = flies ? ground + THROWN_FLYING_UP : ground;
  must(`server tp @p ${x} ${y} 0.5 0 0; wait ${CHUNKS_TICKS}t${flies ? "; fly on" : ""}`);
  send("?server effect clear @p");
  send(`server effect give @p minecraft:instant_health 1 10 true`);
  must(`wait 5t; server summon skyliners:cannon_rocket ${x} ${y + 1} ${0.5 - THROW_ROCKET_OFF}`);
  const path = must(`sample status every 2t x${THROW_SAMPLES}`).rows.filter((row) => row.pos);
  const highest = Math.max(...path.map((row) => row.pos[1])) - y;
  const farthest = Math.max(...path.map((row) => Math.hypot(row.pos[0] - x, row.pos[2] - 0.5)));
  const landed = must(`wait ${THROWN_LANDS_TICKS}t${flies ? "; fly off" : ""}; status`);
  return { highest, farthest, landed };
}

// A freighter's console: her modules curve round her seats, each a slice of one ring; on the ground her boost button
// will not boost, saying so with a low note; and a bank of toggles, where she is built with one in place of her slam
// button, clicks a toggle up under the hand.
function checkFreighterConsole() {
  ship("freighter");
  const slices = Object.entries(CONSOLE_SLICES).map(([kind, count]) => [kind, count, must(`find skyliners:${kind} 128 ${count + 1}`).rows]);
  for (const [kind, count, rows] of slices) {
    expect(rows.length === count, `freighter: her console has its ${kind.replace("console_", "")}`, `${rows.length} of ${count}`);
  }
  const all = slices.flatMap(([kind, , rows]) => rows.map((row) => ({ kind, at: row.at_abs })));
  const flat = all.filter(({ at }) => !sliceOfRing(at));
  const named = flat.map(({ kind, at }) => `${kind} at ${at.join(",")}`).join("; ");
  expect(flat.length === 0, "freighter: each module is a slice of her half-round console",
    `${all.length - flat.length} of ${all.length}${named ? `, not: ${named}` : ""}`);
  const [boost] = must("find skyliners:console_boost 128 1").rows;
  if (boost) {
    const pressed = now();
    useModule(boost.at_abs);
    const refused = must(`messages since=${pressed} kind=actionbar match=*boosts*only* limit=1`).rows.length > 0;
    expect(refused && played("*note_block.bass*", pressed).length > 0, "freighter: on the ground her boost button says no, with a low note");
  }
  const [jumpButton] = must("find skyliners:console_jump 128 1").rows;
  if (jumpButton) {
    const pressed = now();
    useModule(jumpButton.at_abs, coPilotSeat());
    expect(Boolean(heard("*freighter_computer_jump_*", pressed, LOOK_EVERY)), "freighter: standing as built, her jump button says why not in her computer's voice",
      must(`messages since=${pressed} kind=actionbar limit=1`).rows[0]?.text ?? "");
  }
  const [toggles] = must("find skyliners:console_switches 128 1").rows;
  if (toggles) {
    const before = must(`server data get block ${toggles.at_abs.join(" ")} Toggles`).result ?? 0;
    const flipped = now();
    useModule(toggles.at_abs, coPilotSeat());
    const after = must(`server data get block ${toggles.at_abs.join(" ")} Toggles`).result ?? 0;
    expect(after !== before && played("*lever.click*", flipped).length > 0, "freighter: a toggle on her console clicks over", `${before} to ${after}`);
  }
}

// From a freighter's pilot's seat, each of her three big buttons on her console, climb, boost and jump, is pressed
// where its desk is seen, over the desks between, so whoever flies her alone presses them all from where they sit.
// With the fault each desk took clicks in the empty room over it up to the top of its block, so the jump button, round
// on the co-pilot's side, could not be pressed from the pilot's seat past the scanner's.
function checkFreighterCockpitReach() {
  ship("freighter");
  const seat = pilotSeat();
  must(`server tp @p ${seat[0] + 0.5} ${seat[1]} ${seat[2] + 0.5}; wait 10t; use ${seat.join(",")}; wait 10t`);
  for (const kind of ["climb", "spin", "boost", "jump"]) {
    const [module] = must(`find skyliners:console_${kind} 128 1`).rows;
    const reply = module ? send(`use ${module.at_abs.join(",")} at ${DESK_TOP_AT}; wait 10t`)[0] : undefined;
    expect(module && reply.ok !== false, `freighter: from her pilot's seat, her ${kind} button is pressed where it is seen`,
      module ? (reply.err ? `${reply.err} ${reply.arg ?? ""}` : "") : "no such button");
  }
  must("key press key.sneak 10t; wait 10t");
}

// Walks at a block of a freighter from so far out along a way, at the height of its foot, with the walk key alone;
// how far from the block's middle along that way the player then stands, in blocks. In survival, so nothing is flown.
function bumpInto(at, [wx, wz], out = BUMP_FROM) {
  const [x, y, z] = at;
  const [cx, cz] = [x + 0.5, z + 0.5];
  const yaw = Math.atan2(wx, -wz) * 180 / Math.PI;
  survival();
  const there = must(`server tp @p ${cx + wx * out} ${y} ${cz + wz * out}; turn ${yaw} 0; wait 10t; move forward ${BUMP_TICKS}t; status`);
  creative();
  return { along: round((there.pos[0] - cx) * wx + (there.pos[2] - cz) * wz), up: round(there.pos[1] - y) };
}

// Whether a column of blocks out from a block along a way, at its height and the two over it, is open floor to walk.
function clearWay([x, y, z], [wx, wz], out) {
  const cells = [];
  for (let step = 1; step <= out; step++) {
    cells.push([x + wx * step, y, z + wz * step], [x + wx * step, y + 1, z + wz * step]);
  }
  return cells.every((cell) => must(`block ${cell.join(",")} side=server`).id === "air");
}

// A freighter's furniture is as solid as it is drawn: walked at, her jump drive's rings stop the player short of its
// column, the band's players on the side thirds of the stage stop them short of the stage's middle, and a sun lounger's
// raised back stops them from behind rather than being walked over. With the fault all three were walked through.
function checkFreighterSolid() {
  ship("freighter");
  const [drive] = must("find skyliners:jump_drive 128 1").rows;
  const way = drive && Object.values(STEPS).find((step) => clearWay(drive.at_abs, step, BUMP_FROM + 1));
  if (way) {
    const { along } = bumpInto(drive.at_abs, way);
    expect(along >= DRIVE_RINGS_OUT && along < BUMP_FROM - BUMP_MOVED, "freighter: walked at, her jump drive's rings stop the player",
      `${along} blocks from its middle`);
  } else {
    expect(false, "freighter: she has a jump drive with open floor round it to walk at it from");
  }
  const [stage] = must("find skyliners:bandstand 128 1").rows;
  if (stage) {
    const facing = must(`block ${stage.at_abs.join(",")}`).props.facing ?? "north";
    const [ax, az] = STEPS[CLOCKWISE[facing]];
    const side = [stage.at_abs[0] + ax, stage.at_abs[1], stage.at_abs[2] + az];
    const open = clearWay(side, STEPS[facing], BUMP_FROM);
    const { along } = open ? bumpInto(side, STEPS[facing]) : { along: NaN };
    expect(open && along >= BAND_PLAYER_OUT && along < BUMP_FROM - BUMP_MOVED,
      "freighter: walked at the band from the club, the player on the side of the stage stops them",
      open ? `${along} blocks in front of the side's middle` : "no open floor in front of it to walk from");
  }
  const behindOf = (at) => STEPS[CLOCKWISE[CLOCKWISE[must(`block ${at.join(",")}`).props.facing ?? "north"]]];
  const lounger = must("find skyliners:sun_lounger 128 8").rows.map((row) => row.at_abs)
    .find((at) => clearWay(at, behindOf(at), LOUNGER_FROM));
  if (lounger) {
    const { along, up } = bumpInto(lounger, behindOf(lounger), LOUNGER_FROM);
    expect(along >= LOUNGER_BACK_OUT && along < LOUNGER_FROM - BUMP_MOVED && up < 1,
      "freighter: walked at a sun lounger from behind, its raised back stops the player", `${along} blocks behind its middle, ${up} up`);
  } else {
    expect(false, "freighter: she has a sun lounger with open floor behind it to walk at it from");
  }
}

// A freighter's console is as solid as it is drawn: walked at from her control head, a module's desk stops the player
// at its near edge, just short of its block's middle. With the fault its whole block was solid, and its desk's empty
// room in front of it stopped them half a block short, and took clicks meant for what is beyond. Any module with open
// floor between it and her control head will do: each has the same desk, and the modules move round as more are added.
function checkFreighterConsoleSolid() {
  ship("freighter");
  const module = CONSOLE_MODULES.map((id) => must(`find skyliners:${id} 128 1`).rows[0]).filter(Boolean)
    .map((row) => ({ at: row.at_abs, toward: STEPS[must(`block ${row.at_abs.join(",")}`).props.facing ?? "north"] }))
    .find(({ at, toward }) => clearWay(at, toward, 1));
  if (!module) {
    expect(false, "freighter: her console has a module with open floor between it and her control head");
    return;
  }
  const { along } = bumpInto(module.at, module.toward, DESK_FROM);
  expect(along < DESK_STOPS_WITHIN && along < DESK_FROM - BUMP_MOVED, "freighter: walked at from her control head, her console module stops the player at its desk",
    `${along} blocks short of its block's middle`);
}

// A freighter's outside is as solid as it is drawn: dropped onto her laser cannon, the player stands on its housing
// over its pedestal; onto her big satellite dish, on the middle of its bowl over its mount; and walked at from beside
// her parcel scanner in her hold, its gantry's post stops them. With the fault they fell through the housing and the
// bowl onto what is under them, and the post stood out past the scanner's block with nothing there.
function checkFreighterOutsideSolid() {
  ship("freighter");
  for (const [block, [low, high], what] of [["laser_cannon", CANNON_HOUSING_TOP, "her laser cannon's housing"], ["big_satellite_dish", DISH_BOWL_TOP, "her big dish's bowl"]]) {
    const [found] = must(`find skyliners:${block} 128 1`).rows;
    const stood = found && landsOn(found.at_abs);
    expect(stood !== undefined && stood > low - STANDS_WITHIN && stood < high + STANDS_WITHIN, `freighter: dropped onto ${what}, the player stands on it`,
      found ? `${stood} blocks over its block, ${low === high ? low : `${low} to ${high}`} wanted` : "not built");
  }
  const [scanner] = must("find skyliners:parcel_scanner 128 1").rows;
  const facing = scanner && (must(`block ${scanner.at_abs.join(",")}`).props.facing ?? "north");
  const side = scanner && [STEPS[CLOCKWISE[facing]], STEPS[CLOCKWISE[CLOCKWISE[CLOCKWISE[facing]]]]].find((way) => clearWay(scanner.at_abs, way, GANTRY_FROM));
  if (!side) {
    expect(false, "freighter: her hold has its parcel scanner, with open floor beside it to walk at it from");
    return;
  }
  const { along } = bumpInto(scanner.at_abs, side, GANTRY_FROM);
  expect(along > GANTRY_POST_OUT && along < GANTRY_FROM - BUMP_MOVED, "freighter: walked at her parcel scanner from beside it, its gantry's post stops the player",
    `${along} blocks from its middle`);
}

// LED strips laid by hand join up as train track does (see LedStripLineBlock): on the floor a run turns its corner when
// a strip is laid beside its end; round a block hung in the air, strips on each of its four sides go on round its
// corners into a ring; on the floor a run goes up a wall and over its top; under a ceiling a run lies straight; in a
// room's corner a run along one wall turns onto the other; laid alone looking steeply up, a strip runs up its wall;
// a run goes up a wall and on along the ceiling; and a strip laid beside the middle of a run leaves the run as it
// was. Each strip's way through its block is read back,
// end by end, each the face it lies on and the edge it leaves by. With the fault each strip lay alone along the far
// side of its block from whoever laid it, joining nothing.
function checkStripJoins() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  const [x, g, z] = [site.x, ground, site.z];
  creative();
  emptyHands();
  must(`server tp @p ${x + 0.5} ${g} ${z + 3.5} 180 30; wait ${CHUNKS_TICKS}t; server give @p skyliners:led_strip 64; wait 5t; select 0`);
  const lay = (at, from, yaw, pitch) => must(`server tp @p ${from.join(" ")} ${yaw} ${pitch}; wait 5t; place skyliners:led_strip ${at.join(",")}; wait 5t`);
  const layOn = (block, point, from, yaw, pitch) => must(`server tp @p ${from.join(" ")} ${yaw} ${pitch}; wait 5t; use ${block.join(",")} at ${point}; wait 5t`);
  const checks = [];
  // On the floor: a run north, turning east at its end.
  const run = [[x, g, z], [x, g, z - 1], [x, g, z - 2]];
  run.forEach((at) => lay(at, [x + 0.5, g, z + 2.5], 180, 40));
  lay([x + 1, g, z - 2], [x + 3.5, g, z - 1.5], 90, 40);
  checks.push(["on the floor, a run goes straight on", [x, g, z - 1], "down:north down:south"],
    ["on the floor, the run's end turns round to a strip laid beside it", [x, g, z - 2], "down:south down:east"],
    ["on the floor, the strip laid beside the run's end runs on from it", [x + 1, g, z - 2], "down:west down:east"]);
  // Beside the middle of the run, a strip leaves the run straight.
  lay([x - 1, g, z - 1], [x - 3.5, g, z - 0.5], -90, 40);
  checks.push(["on the floor, a strip laid beside the middle of a run leaves the run straight", [x, g, z - 1], "down:north down:south"]);
  // Round a block hung in the air, a strip on each of its sides, the ring closing round its corners.
  const k = [x + 7, g + 3, z];
  must(`server setblock ${k.join(" ")} stone`);
  lay([k[0] - 1, k[1], k[2]], [k[0] - 3.5, k[1], k[2] + 0.5], -90, 0);
  lay([k[0], k[1], k[2] - 1], [k[0] + 0.5, k[1], k[2] - 3.5], 0, 0);
  lay([k[0] + 1, k[1], k[2]], [k[0] + 3.5, k[1], k[2] + 0.5], 90, 0);
  lay([k[0], k[1], k[2] + 1], [k[0] + 0.5, k[1], k[2] + 3.5], 180, 0);
  checks.push(["round a block, its west side's strip runs along it", [k[0] - 1, k[1], k[2]], "east:north east:south", "both"],
    ["round a block, its north side's strip goes on round the corner from the west's", [k[0], k[1], k[2] - 1], "south:west south:east", "both"],
    ["round a block, its east side's strip goes on round the corner from the north's", [k[0] + 1, k[1], k[2]], "west:north west:south", "both"],
    ["round a block, its south side's strip closes the ring round both corners", [k[0], k[1], k[2] + 1], "north:west north:east", "both"]);
  // On the floor up to a wall, up it and over its top.
  const w = x + 14;
  must(`server fill ${w} ${g} ${z - 1} ${w} ${g + 2} ${z - 1} stone`);
  lay([w, g, z + 1], [w + 0.5, g, z + 3.5], 180, 40);
  lay([w, g, z], [w + 0.5, g, z + 3.5], 180, 40);
  layOn([w, g + 1, z - 1], "0.5,0.5,1", [w + 0.5, g + 1, z + 2.5], 180, 0);
  layOn([w, g + 2, z - 1], "0.5,0.5,1", [w + 0.5, g + 1, z + 2.5], 180, -20);
  lay([w, g + 3, z - 1], [w + 0.5, g + 4, z + 1.5], 180, 50);
  checks.push(["up to a wall, the floor's run turns up it", [w, g, z], "down:south north:up"],
    ["up a wall, the strip runs straight up it", [w, g + 1, z], "north:down north:up"],
    ["at a wall's top, the strip goes on over its edge", [w, g + 2, z], "north:down north:up", "second"],
    ["on a wall's top, the strip comes over its edge and runs on along it", [w, g + 3, z - 1], "down:north down:south", "second"]);
  // Under a ceiling, a run straight along it.
  const c = x + 20;
  must(`server fill ${c} ${g + 3} ${z - 1} ${c} ${g + 3} ${z} stone`);
  lay([c, g + 2, z], [c + 0.5, g, z + 2.5], 180, -40);
  lay([c, g + 2, z - 1], [c + 0.5, g, z + 2.5], 180, -40);
  checks.push(["under a ceiling, a run lies straight along it", [c, g + 2, z], "up:north up:south"]);
  // In a room's corner, a run along one wall turns onto the other; and laid alone looking steeply up, a strip runs up
  // its wall.
  const r = x + 26;
  must(`server fill ${r} ${g} ${z - 1} ${r + 3} ${g + 2} ${z - 1} stone; server fill ${r} ${g} ${z} ${r} ${g + 2} ${z + 3} stone`);
  layOn([r + 2, g + 1, z - 1], "0.5,0.5,1", [r + 2.5, g + 1, z + 2.5], 180, 0);
  layOn([r + 1, g + 1, z - 1], "0.5,0.5,1", [r + 2.5, g + 1, z + 2.5], 180, 0);
  layOn([r, g + 1, z + 1], "1,0.5,0.5", [r + 2.5, g + 1, z + 2.5], 90, 0);
  layOn([r + 3, g + 2, z - 1], "0.5,0.5,1", [r + 3.5, g, z + 0.6], 180, -60);
  checks.push(["in a room's corner, a run along one wall turns onto the other", [r + 1, g + 1, z], "north:east west:south"],
    ["laid alone looking steeply up, a strip runs up its wall", [r + 3, g + 2, z], "north:down north:up"]);
  // From the floor up a wall and on along the ceiling.
  const q = x + 34;
  must(`server fill ${q} ${g} ${z - 1} ${q} ${g + 2} ${z - 1} stone; server fill ${q} ${g + 3} ${z - 1} ${q} ${g + 3} ${z + 3} stone`);
  lay([q, g, z + 1], [q + 0.5, g, z + 3.5], 180, 40);
  lay([q, g, z], [q + 0.5, g, z + 3.5], 180, 40);
  layOn([q, g + 1, z - 1], "0.5,0.5,1", [q + 0.5, g, z + 2.5], 180, 0);
  layOn([q, g + 2, z - 1], "0.5,0.5,1", [q + 0.5, g, z + 2.5], 180, 0);
  layOn([q, g + 3, z + 1], "0.5,0,0.5", [q + 0.5, g, z + 2.5], 180, -40);
  layOn([q, g + 3, z + 2], "0.5,0,0.5", [q + 0.5, g, z + 3.5], 180, -40);
  checks.push(["up a wall to the ceiling, its top strip turns along the ceiling", [q, g + 2, z], "north:down up:south"],
    ["along the ceiling from a wall, the run goes on straight", [q, g + 2, z + 1], "up:north up:south"]);
  for (const [what, at, path, wrap] of checks) {
    const strip = must(`skyliners:strip x=${at[0]} y=${at[1]} z=${at[2]}`);
    const same = (strip.path ?? "").split(" ").sort().join(" ") === path.split(" ").sort().join(" ");
    expect(same && (!wrap || strip.wrap === wrap), `led strip: ${what}`, `${strip.path}${wrap ? `, round a corner at ${strip.wrap}` : ""}`);
  }
  // Round the block, its south side's strip broken: the west side's stops filling that corner; the block gone, its
  // strips go with it.
  must(`server tp @p ${k[0] + 0.5} ${k[1]} ${k[2] + 3.5} 180 0; wait 5t; mine ${k[0]},${k[1]},${k[2] + 1} timeout=5s; wait 5t`);
  const west = must(`skyliners:strip x=${k[0] - 1} y=${k[1]} z=${k[2]}`);
  expect(west.wrap === "first", "led strip: round a block, its south side's strip broken, the west side's leaves that corner", west.wrap);
  must(`server setblock ${k.join(" ")} air; wait 5t`);
  const left = must(`skyliners:strip x=${k[0] - 1} y=${k[1]} z=${k[2]}`).path;
  expect(left === "none", "led strip: the block it lies on gone, a strip goes with it", left);
}

// Nothing of the mod's is walked through but what must be for what it does (see WALK_THROUGH): every state of every
// block with a shape to aim at has one to bump into. With the fault some thirty blocks drawn with a body, from buttons
// and lamps to the disco ball and the bowling pins, were walked through.
function checkNothingWalkThrough() {
  const { blocks } = must("skyliners:walkthrough");
  const through = blocks ? blocks.split(",") : [];
  const unexpected = through.filter((id) => !WALK_THROUGH.includes(id));
  expect(unexpected.length === 0, "every block of the mod is solid as drawn, but those walked through for what they do",
    unexpected.length ? `walked through: ${unexpected.join(", ")}` : `only ${through.join(", ") || "none"}`);
}

// A Spookie's hammock is as solid as it is drawn: its posts are blocks of their own, standing either side of its sling
// as the freighter builds it and as it is set down by hand, and walked at from beyond either end, each post stops the
// player; broken by a post in survival, it all comes down and gives back one hammock. With the fault its posts were
// drawn from the sling's block, a block out either side of it with nothing there, and were walked straight through.
function checkHammockPosts() {
  ship("freighter");
  const built = must("find skyliners:spookie_hammock 128 3").rows.map((row) => must(`block ${row.at_abs.join(",")}`).props?.part);
  expect(built.filter((part) => part === "post").length === 2 && built.includes("sling"), "freighter: her Spookie's hammock stands on its two posts",
    built.join(", ") || "not built");
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  const at = [site.x, ground, site.z];
  creative();
  emptyHands();
  must(`server tp @p ${at[0] + 0.5} ${ground} ${at[2] - 2.5} 0 20; wait ${CHUNKS_TICKS}t; server give @p skyliners:spookie_hammock 1; wait 5t; select 0`);
  send(`place skyliners:spookie_hammock ${at.join(",")}`);
  for (const way of [[1, 0], [-1, 0]]) {
    const post = [at[0] + way[0], at[1], at[2]];
    const part = must(`block ${post.join(",")} side=server`).props?.part;
    const { along } = bumpInto(post, way, HAMMOCK_WALK_FROM);
    expect(part === "post" && along > HAMMOCK_POST_OUT && along < HAMMOCK_WALK_FROM - BUMP_MOVED,
      `spookie hammock: set down by hand, its post ${way[0] > 0 ? "east" : "west"} of it stops the player walking at it`, `${part ?? "nothing"} there, ${along} blocks out from its middle`);
  }
  emptyHands();
  survival();
  must(`server tp @p ${at[0] + 1 + HAMMOCK_WALK_FROM} ${ground} ${at[2] + 0.5} 90 20; wait 10t; mine ${at[0] + 1},${at[1]},${at[2]} timeout=10s; wait 20t`);
  const left = must(`find skyliners:spookie_hammock ${WINDOW_REACH} 3`).rows.length;
  const given = windowsGivenBack("spookie_hammock");
  creative();
  expect(left === 0 && given === 1, "spookie hammock: broken by a post, it all comes down and gives back one hammock", `${left} blocks left, ${given} given back`);
}

// A big satellite dish's bowl is as solid as it is drawn, out past its block, however it is turned: set down by hand on
// the ground, the game's time stood still so it holds still, and walked at from each side at its foot, its bowl stops the
// player further out than its mount on the side it leans out over. With the fault only its mount and the middle of its
// bowl over it were solid, and the player walked under the rest of the bowl right up to its mount from every side.
function checkDishBowl() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  const at = [site.x, ground, site.z];
  creative();
  must(`server tp @p ${at[0] + 0.5} ${ground} ${at[2] - DISH_WALK_FROM} 0 20; wait ${CHUNKS_TICKS}t`);
  must(`server setblock ${at.join(" ")} skyliners:big_satellite_dish[mount=floor,facing=north]; server tick freeze`);
  const stops = Object.values(STEPS).map((way) => bumpInto(at, way, DISH_WALK_FROM).along);
  must("server tick unfreeze");
  expect(Math.max(...stops) > DISH_BOWL_OUT, "big satellite dish: walked at from each side, its bowl stops the player out past its mount on the side it leans over",
    `stopped ${stops.join(", ")} blocks out from its middle`);
  must(`server setblock ${at.join(" ")} air`);
}

// Drops the player, in survival, from over the middle of a block onto it; how high over the block they come to stand.
function landsOn([x, y, z]) {
  survival();
  const there = must(`server tp @p ${x + 0.5} ${y + DROP_FROM} ${z + 0.5}; wait 40t; status`);
  creative();
  return round(there.pos[1] - y);
}

// The slim souvenir models set down on the ground are clicked only where they are drawn: aimed at from beside one where
// nothing of it is, the click passes it by to the ground beyond; aimed at its model, it takes it. With the fault all
// four shared one box, as big as the airliner's, and the rocket and the freighter took clicks well clear of them.
function checkSouvenirShapes() {
  // Out of every ship's way, short of the first site.
  const [x, z] = [-SITE_APART / 2, 0];
  creative();
  must(`server tp @p ${x} ${ground} ${z}; wait ${CHUNKS_TICKS}t`);
  for (const [model, { empty, solid }] of Object.entries(SOUVENIRS)) {
    must(`server setblock ${x} ${ground} ${z} skyliners:${model}[facing=north]; server tp @p ${x + 0.5} ${ground} ${z + 0.5 + SOUVENIR_FROM}; turn 180 0; wait 10t`);
    const missed = send(`use ${x},${ground},${z} at ${empty}`)[0];
    const taken = send(`use ${x},${ground},${z} at ${solid}`)[0];
    expect(missed.ok === false && taken.ok !== false, `${model}: clicked only where its model is drawn`,
      `beside it: ${missed.ok === false ? missed.err : "taken"}; on it: ${taken.ok === false ? taken.err : "taken"}`);
  }
  must(`server setblock ${x} ${ground} ${z} air`);
}

// Whether the console module at a block has its slice of the ring, asked twice a second apart: a server still busy
// with a ship just built may let a question go unanswered.
function sliceOfRing(at) {
  const ask = `server data get block ${at.join(" ")} Facet.Inner`;
  return send(ask)[0].ok !== false || send(`wait 20t; ${ask}`).at(-1).ok !== false;
}

// Uses a console module on the ground as her pilot would, from her seat, or from the seat given.
function useModule(at, seat = pilotSeat()) {
  must(`server tp @p ${seat[0] + 0.5} ${seat[1]} ${seat[2] + 0.5}; wait 10t; use ${seat.join(",")}; wait 10t; use ${at.join(",")}; wait 10t`);
  must("key press key.sneak 10t; wait 10t");
}

// Where a freighter's pilot sits, standing on the ground near the player: on her left, two blocks from her control head.
function pilotSeat() {
  const [head] = must("find skyliners:autopilot 128 1").rows;
  const nose = must(`block ${head.at_abs.join(",")}`).props.facing ?? "north";
  const [lx, lz] = WAYS[CLOCKWISE[CLOCKWISE[CLOCKWISE[nose]]]];
  return [head.at_abs[0] + 2 * lx, head.at_abs[1], head.at_abs[2] + 2 * lz];
}

// Where a freighter's co-pilot sits, standing on the ground near the player: on her right, two blocks from her head.
function coPilotSeat() {
  const [head] = must("find skyliners:autopilot 128 1").rows;
  const nose = must(`block ${head.at_abs.join(",")}`).props.facing ?? "north";
  const [rx, rz] = WAYS[CLOCKWISE[nose]];
  return [head.at_abs[0] + 2 * rx, head.at_abs[1], head.at_abs[2] + 2 * rz];
}

// A freighter's climb boost: hovering, its button fires her lift flat out, her roar going up, and she climbs straight
// up hard, gaining most of the height it takes her within the time it should and hardly drifting off her spot; then
// her autopilot holds that height rather than pulling her back down.
function checkFreighterClimbBoost() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  const hovering = waitForReport(freighterReport, (report) => report.phase === "cruise" && Math.abs(report.climb) < 1, FREIGHTER_UP_TICKS);
  expect(hovering, "freighter: she lifts off and hovers");
  if (!hovering) {
    return;
  }
  const freighter = findShipFlying("skyliners:engine_grille");
  const { at } = must(`sable:find ${freighter} skyliners:console_climb 1 side=server`).rows[0];
  const pressed = must(`sable:tp ${at.join(",")}; wait 20t; messages limit=1`).tick;
  const before = freighterReport();
  must(`use ${at.join(",")}`);
  expect(Boolean(heard("*speed_boost*", pressed, LOOK_EVERY)), "freighter: her climb button roars into it");
  const rows = must(`sample skyliners:freighter every 20t x${CLIMB_BOOST_WATCH_TICKS / 20}`, BUILD_SECONDS).rows;
  const fastest = Math.max(...rows.map((row) => row.climb));
  const top = rows.at(-1);
  const gained = top.altitude - before.altitude;
  expect(gained >= CLIMB_BOOST_GAIN * CLIMB_BOOST_SHARE, "freighter: climbing hard, she gains most of its height in time",
    `${round(gained)} blocks in ${CLIMB_BOOST_WATCH_TICKS / 20} s, climbing ${round(fastest)} a second at the fastest`);
  const [fromX, fromZ] = before.middle.split(",").map(Number);
  const [toX, toZ] = top.middle.split(",").map(Number);
  const drift = Math.hypot(toX - fromX, toZ - fromZ);
  expect(drift <= CLIMB_BOOST_DRIFT, "freighter: she climbs straight up, holding her spot", `${round(drift)} blocks off it`);
  must(`wait ${CLIMB_BOOST_HOLD_TICKS}t`, BUILD_SECONDS);
  const after = freighterReport();
  const off = Math.abs(after.altitude - (before.altitude + CLIMB_BOOST_GAIN));
  expect(off <= CLIMB_BOOST_HELD_WITHIN && after.target_altitude >= before.altitude + CLIMB_BOOST_GAIN - CLIMB_BOOST_HELD_WITHIN,
    "freighter: afterwards her autopilot holds the height she climbed to", `at ${after.altitude}, set to ${after.target_altitude}`);
  must("skyliners:autopilot land");
}

// A freighter sent far past the edge of space: her autopilot takes y 2,500, past the 1,400 it stopped at before; her
// climb button still climbs hard over the edge of space, where it used to say she was at the top; and set to the new
// height she gets there and holds it.
function checkFreighterSpaceCeiling() {
  layDown("freighter");
  must(`skyliners:autopilot fly altitude=${HIGHEST_ALTITUDE} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  must(`sable:tp ${freighter} skyliners:console_climb; wait ${LOOK_EVERY}t`);
  const set = freighterReport();
  expect(set?.target_altitude >= HIGHEST_ALTITUDE, "freighter: her autopilot can be set to y 2,500, far past the edge of space",
    `set to ${set?.target_altitude}`);
  lift(freighter, SPACE_CLIMB_FROM);
  must(`skyliners:autopilot fly altitude=${SPACE_CLIMB_FROM} speed=0; wait ${SPACE_SETTLE_TICKS}t`);
  const before = freighterReport();
  const { at } = must(`sable:find ${freighter} skyliners:console_climb 1 side=server`).rows[0];
  const pressed = must(`sable:tp ${at.join(",")}; wait 20t; messages limit=1`).tick;
  must(`use ${at.join(",")}`);
  expect(Boolean(heard("*speed_boost*", pressed, LOOK_EVERY)), "freighter: past the edge of space, her climb button still roars into it");
  const climbed = must(`wait ${SPACE_CLIMB_WATCH_TICKS}t; skyliners:freighter`);
  expect(climbed.altitude - before.altitude >= SPACE_CLIMB_GAINED, "freighter: and she climbs on hard, past the edge of space",
    `from y ${before.altitude} to ${climbed.altitude}`);
  lift(freighter, HIGHEST_ALTITUDE - SPACE_TOP_UNDER);
  must(`skyliners:autopilot fly altitude=${HIGHEST_ALTITUDE} speed=0; wait ${SPACE_TOP_TICKS}t`, BUILD_SECONDS);
  const top = freighterReport();
  expect(Math.abs(top.altitude - HIGHEST_ALTITUDE) <= SPACE_HELD_WITHIN && Math.abs(top.climb) < 1, "freighter: set to y 2,500, she gets there and holds it",
    `at y ${top.altitude}, climbing ${top.climb}`);
}

// Her slam button from far out in space: hovering at y 2,500 it is lit, where past 160 blocks over the ground it used
// to say no; pressed, she drives down far faster than she does from near the ground, all the way down, strikes it with
// her crash and settles on her legs, the player aboard her unhurt.
function checkFreighterSlamFromSpace() {
  layDown("freighter");
  must(`skyliners:autopilot fly altitude=${HIGHEST_ALTITUDE} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  lift(freighter, HIGHEST_ALTITUDE);
  must(`skyliners:autopilot fly altitude=${HIGHEST_ALTITUDE} speed=0; wait ${SPACE_SETTLE_TICKS}t`);
  const up = freighterReport();
  expect(up?.slam === "ready", "freighter: hovering far out in space, her slam button is lit", `slam ${up?.slam} at y ${up?.altitude}`);
  const { at } = must(`sable:find ${freighter} skyliners:console_slam 1 side=server`).rows[0];
  must(`sable:tp ${at.join(",")}; wait 20t`);
  survival();
  const health = healthOf();
  const pressed = now();
  must(`use ${at.join(",")}`);
  const rows = must(`sample skyliners:freighter every 20t x${SPACE_SLAM_WATCH_SECONDS}`, BUILD_SECONDS).rows;
  const fastest = Math.min(...rows.map((row) => row.climb ?? 0));
  expect(-fastest >= SPACE_SLAM_FASTEST, "freighter: slammed down from space, she drives down far faster than from near the ground",
    `${round(-fastest)} blocks a second at the fastest`);
  const down = rows.findIndex((row) => row.phase === "off");
  expect(down >= 0, "freighter: she comes all the way down and switches off on the ground", down >= 0 ? `${down} s from the button` : `last at y ${rows.at(-1)?.altitude}`);
  expect(played("skyliners:freighter_slam", pressed).length > 0, "freighter: she strikes the ground with her crash");
  expect(healthOf() === health, "freighter: nobody aboard is hurt", `health ${healthOf()}, ${health} before`);
  creative();
}

// A freighter knocked upside down rights herself: hovering high over the ground, she is turned over onto her back, and
// her autopilot turns her back upright the shortest way and catches her fall well clear of the ground, then hovers
// level again and climbs back to her height. Upside down she used to read herself as level and drive herself into the
// ground on her own lift, lying there on her back.
function checkFreighterRightsHerself() {
  layDown("freighter");
  must(`skyliners:autopilot fly altitude=${FLIP_HEIGHT} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  must(`sable:tp ${freighter} skyliners:console_climb; wait ${LOOK_EVERY}t`);
  lift(freighter, FLIP_HEIGHT);
  const steady = (report) => report.phase === "cruise" && Math.abs(report.altitude - FLIP_HEIGHT) < SPACE_HELD_WITHIN && Math.abs(report.climb) < 1;
  expect(Boolean(waitForReport(freighterReport, steady, FREIGHTER_UP_TICKS)), "flipped: she hovers high over the ground");
  const { at_abs: at } = must(`sable:info ${freighter} side=server`);
  must("perf start flip");
  const rows = must(`server sable teleport ${freighter} ${at.join(" ")} 0 ${FLIP_OVER}; sample sable:info ${freighter} side=server every ${FLIP_EVERY}t x${FLIP_WATCH_TICKS / FLIP_EVERY}`,
    BUILD_SECONDS).rows.filter((row) => row.vel);
  const { summary } = must("perf stop");
  const mspt = summary?.server?.mspt ?? summary?.mspt ?? {};
  expect((mspt.avg ?? 0) <= GIANT_TICK_MS && (mspt.max ?? 0) <= GIANT_WORST_TICK_MS, "flipped: the server keeps up as she rights herself",
    `server ticks ${round(mspt.avg ?? NaN)} ms on average, ${round(mspt.max ?? NaN)} ms at the worst`);
  const turnedOver = rows.length > 0 && upsetOf(rows[0]) > 90;
  expect(turnedOver, "flipped: she is turned over onto her back", rows.length > 0 ? `${round(upsetOf(rows[0]))} degrees from upright` : "never read");
  const upright = rows.findIndex((row) => upsetOf(row) < FLIP_UPRIGHT);
  expect(upright >= 0, "flipped: her autopilot turns her back upright", upright >= 0 ? `in ${round(upright * FLIP_EVERY / 20)} s`
    : `still ${round(upsetOf(rows.at(-1) ?? { pitch: 180, roll: 0 }))} degrees over`);
  const lowest = Math.min(...rows.map((row) => row.agl ?? 0));
  const lost = FLIP_HEIGHT - Math.min(...rows.map((row) => row.at_abs[1]));
  expect(lowest >= FLIP_CLEAR, "flipped: she catches her fall well clear of the ground", `${round(lost)} blocks lost, ${round(lowest)} over the ground at the lowest`);
  must(`sable:tp ${freighter} skyliners:console_climb; wait ${LOOK_EVERY}t`);
  const level = waitForReport(freighterReport, (report) => !report.righting && report.upset < FREIGHTER_LEVEL_WITHIN && report.climb > 1, FREIGHTER_UP_TICKS);
  expect(Boolean(level), "flipped: she hovers level again and climbs back to her height", level ? `climbing ${level.climb} from y ${level.altitude}` : "");
  must("skyliners:autopilot land");
}

// A freighter's spin button: hovering near the ground it only says no, with a low note, and she stays level; hovering
// high, pressed, her lift roars and she rolls right over about her length, onto her back and on round, comes back
// level well clear of the ground and hovers on.
function checkFreighterSpin() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + SPIN_LOW} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  const low = waitForReport(freighterReport, (report) => report.phase === "cruise" && Math.abs(report.climb) < 1, FREIGHTER_UP_TICKS);
  expect(Boolean(low), "spin: she lifts off and hovers near the ground");
  const { at } = must(`sable:find ${freighter} skyliners:console_spin 1 side=server`).rows[0];
  const refusedAt = must(`sable:tp ${at.join(",")}; wait 20t; messages limit=1`).tick;
  must(`use ${at.join(",")}`);
  const refused = must(`wait ${LOOK_EVERY}t; skyliners:freighter`);
  expect(played("*note_block.bass*", refusedAt).length > 0 && refused.spin !== "rolling" && refused.upset < FREIGHTER_LEVEL_WITHIN,
    "spin: near the ground her spin button says no, with a low note, and she stays level", `spin ${refused.spin}, ${refused.upset} degrees over`);
  lift(freighter, SPIN_HEIGHT);
  must(`skyliners:autopilot fly altitude=${SPIN_HEIGHT} speed=0`);
  const high = waitForReport(freighterReport, (report) => report.phase === "cruise" && report.spin === "ready" && Math.abs(report.climb) < 1,
    FREIGHTER_UP_TICKS);
  expect(Boolean(high), "spin: hovering high over the ground, her spin button is lit", high ? "" : `spin ${freighterReport()?.spin}`);
  if (!high) {
    return;
  }
  const pressed = must(`sable:tp ${at.join(",")}; wait 20t; messages limit=1`).tick;
  const rows = must(`use ${at.join(",")}; sample sable:info ${freighter} side=server every ${FLIP_EVERY}t x${SPIN_WATCH_TICKS / FLIP_EVERY}`,
    BUILD_SECONDS).rows.filter((row) => row.vel);
  expect(Boolean(heard("*speed_boost*", pressed, 0)), "spin: pressed, her lift roars into it");
  const over = Math.max(0, ...rows.map(upsetOf));
  expect(over >= SPIN_OVER, "spin: she rolls right over onto her back", `${round(over)} degrees from upright at the most`);
  const back = rows.findIndex((row, i) => i > 0 && upsetOf(row) < FLIP_UPRIGHT && rows.slice(0, i).some((before) => upsetOf(before) >= SPIN_OVER));
  expect(back >= 0, "spin: and on round, back level", back >= 0 ? `in ${round(back * FLIP_EVERY / 20)} s` : `${round(upsetOf(rows.at(-1) ?? { pitch: 180, roll: 0 }))} degrees over at the last`);
  const lowest = Math.min(...rows.map((row) => row.agl ?? 0));
  const lost = SPIN_HEIGHT - Math.min(...rows.map((row) => row.at_abs[1]));
  expect(lowest >= FLIP_CLEAR, "spin: well clear of the ground all the while", `${round(lost)} blocks lost, ${round(lowest)} over the ground at the lowest`);
  must(`sable:tp ${freighter} skyliners:console_climb; wait ${LOOK_EVERY}t`);
  const level = waitForReport(freighterReport, (report) => report.spin !== "rolling" && report.upset < FREIGHTER_LEVEL_WITHIN, FREIGHTER_UP_TICKS);
  expect(Boolean(level), "spin: her autopilot flies on, level", level ? `spin ${level.spin}` : "");
  must("skyliners:autopilot land");
}

// How far a ship's deck leans from upright, in degrees from 0 to 180, from a sampled reading of her pitch and roll.
function upsetOf(row) {
  return Math.acos(Math.cos(row.pitch * Math.PI / 180) * Math.cos(row.roll * Math.PI / 180)) * 180 / Math.PI;
}

// Moves a flying ship straight up or down to a height at once, as a long climb would take her, and puts the player back
// aboard at her climb button.
function lift(ship, height) {
  const { at_abs: at } = must(`sable:info ${ship} side=server`);
  must(`server sable teleport ${ship} ${at[0]} ${height} ${at[2]}; wait 40t; sable:tp ${ship} skyliners:console_climb; wait ${LOOK_EVERY}t`);
}

// Space weather far past the edge of space: a freighter hovering at y 1,600 with the player aboard her in survival, a
// meteor shower and then an asteroid storm brought over her: the alarm whoops and her computer warns of each; meteors
// and rocks strike her, each strike heard, shoving her, a rock far harder than a meteor; her computer says it is all
// clear; and afterwards she settles back level at the height she held, the player still aboard her and unhurt.
function checkSpaceWeather() {
  const freighter = freighterInSpace("space weather");
  if (!freighter) {
    return;
  }
  survival();
  const health = healthOf();
  const shoves = {};
  for (const [kind, { voice, strike, ticks, shoveLeast }] of Object.entries(WEATHER)) {
    // Weather may blow by itself up there; once it has passed, none comes for a minute.
    quietSpace();
    const before = must(`sable:info ${freighter} side=server`);
    const since = now();
    const asked = must(`skyliners:spaceweather start=${kind}`);
    expect(asked.started === "coming", `${kind}: brought over her`, `${asked.started}`);
    const rows = must(`sample sable:info ${freighter} side=server every 5t x${ticks / 5}`, BUILD_SECONDS).rows.filter((row) => row.vel);
    expect(played("skyliners:space_weather_alarm", since).length > 0 && played(voice, since).length > 0,
      `${kind}: the alarm whoops and her computer warns of it`);
    const { landed } = must("skyliners:spaceweather");
    const heardStrikes = played(strike, since).length;
    expect(landed > asked.landed && heardStrikes > 0, `${kind}: it strikes her, each strike heard`, `${landed - asked.landed} landed, ${heardStrikes} heard`);
    shoves[kind] = Math.max(...rows.map((row) => Math.hypot(...row.vel.map((v, i) => v - before.vel[i]))));
    const tilt = Math.max(...rows.map((row) => Math.max(Math.abs(row.pitch), Math.abs(row.roll))));
    expect(shoves[kind] >= shoveLeast, `${kind}: a strike shoves her hard`, `speed changed by ${round(shoves[kind])} blocks a second, tipped ${round(tilt)} degrees`);
    expect(played("skyliners:freighter_computer_all_clear", since).length > 0, `${kind}: her computer says it is all clear`);
    const settled = must(`wait ${WEATHER_SETTLE_TICKS}t; sable:info ${freighter} side=server`);
    const lean = Math.max(Math.abs(settled.pitch), Math.abs(settled.roll));
    const drift = Math.hypot(...settled.vel);
    const { altitude, target_altitude: held } = freighterReport();
    expect(drift <= WEATHER_SETTLED_SPEED && lean <= WEATHER_SETTLED_TILT && Math.abs(altitude - held) <= WEATHER_SETTLED_WITHIN,
      `${kind}: she settles back level at her height`, `drifting at ${round(drift)} blocks a second, leaning ${round(lean)}, at y ${altitude}, set to ${held}`);
  }
  expect(shoves.asteroid_storm > shoves.meteor_shower * ROCK_HARDER, "space weather: a rock shoves her far harder than a meteor",
    `${round(shoves.asteroid_storm)} against ${round(shoves.meteor_shower)} blocks a second`);
  const after = healthOf();
  expect(after >= health, "space weather: nobody aboard is hurt", `health ${after}, ${health} before`);
  const { aboard } = must("status");
  expect(Boolean(aboard) && freighter.startsWith(aboard), "space weather: the player is still aboard her", `aboard ${aboard ?? "nothing"}`);
  creative();
}

// Space weather strikes the rocket too: flown up to y 1,600 with the player aboard her in survival, an asteroid storm
// brought over her: the alarm whoops and her flight deck announces it after the chime, rocks strike her, each heard,
// shoving her and tipping her; she waits it out up there, holding her height, says it is all clear, and a while after
// stands upright again, the player still aboard and unhurt.
function checkRocketSpaceWeather() {
  layDown("rocket");
  must(`skyliners:autopilot fly altitude=${WEATHER_HEIGHT}`, BUILD_SECONDS);
  const rocket = findRocketFlying();
  must(`sable:tp ${rocket} skyliners:robot_bartender; wait ${LOOK_EVERY}t`);
  const up = waitForRocket((report) => report.phase === "cruise" && Math.abs(report.altitude - WEATHER_HEIGHT) < ROCKET_HELD_WITHIN,
    ROCKET_WEATHER_UP_TICKS);
  expect(up, "rocket: she climbs to y 1,600, past the black of space");
  if (!up) {
    return;
  }
  waitForReport(() => must("skyliners:spaceweather"), (weather) => weather.blowing === "none", WEATHER_TICKS_MOST);
  survival();
  const health = healthOf();
  const before = must(`sable:info ${rocket} side=server`);
  const since = now();
  const asked = must("skyliners:spaceweather start=asteroid_storm");
  expect(asked.started === "coming", "rocket: an asteroid storm is brought over her", `${asked.started}`);
  const rows = must(`sample sable:info ${rocket} side=server every 5t x${WEATHER.asteroid_storm.ticks / 5}`, BUILD_SECONDS).rows.filter((row) => row.vel);
  expect(played("skyliners:space_weather_alarm", since).length > 0 && played("skyliners:announcement_asteroids", since).length > 0,
    "rocket: the alarm whoops and her flight deck announces the storm");
  const { landed } = must("skyliners:spaceweather");
  const heardStrikes = played(WEATHER.asteroid_storm.strike, since).length;
  expect(landed > asked.landed && heardStrikes > 0, "rocket: rocks strike her, each strike heard", `${landed - asked.landed} landed, ${heardStrikes} heard`);
  const shove = Math.max(...rows.map((row) => Math.hypot(...row.vel.map((v, i) => v - before.vel[i]))));
  const tilt = Math.max(...rows.map((row) => Math.max(Math.abs(row.pitch - before.pitch), Math.abs(row.roll - before.roll))));
  expect(shove >= ROCKET_SHOVE_LEAST && tilt >= ROCKET_TIPPED_LEAST, "rocket: a rock shoves her and tips her nose",
    `speed changed by ${round(shove)} blocks a second, tipped ${round(tilt)} degrees`);
  const thrown = Math.max(...rows.map((row) => Math.abs(row.at_abs[1] - before.at_abs[1])));
  expect(thrown <= ROCKET_HELD_WITHIN, "rocket: she waits it out up there, holding her height", `${round(thrown)} blocks off it at most`);
  expect(played("skyliners:announcement_all_clear", since).length > 0, "rocket: her flight deck says it is all clear");
  const settled = must(`wait ${WEATHER_SETTLE_TICKS}t; skyliners:rocket`);
  const lean = Math.max(Math.abs(settled.tilt_front), Math.abs(settled.tilt_right));
  expect(lean <= ROCKET_UPRIGHT_WITHIN, "rocket: a while after, she stands upright again", `leaning ${lean}, ${settled.phase} at y ${settled.altitude}`);
  const after = healthOf();
  expect(after >= health, "rocket: nobody aboard is hurt", `health ${after}, ${health} before`);
  const { aboard } = must("status");
  expect(Boolean(aboard) && rocket.startsWith(aboard), "rocket: the player is still aboard her", `aboard ${aboard ?? "nothing"}`);
  creative();
}

// A freighter hovering at y 1,600, past the black of space, the player aboard at her climb button and no weather
// blowing; her id, or undefined if she never got there. Weather that blew by itself meanwhile may have left her low, a
// solar flare's blackout dropping her, so she is waited for to be back up there.
function freighterInSpace(what) {
  layDown("freighter");
  must(`skyliners:autopilot fly altitude=${WEATHER_HEIGHT} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  must(`sable:tp ${freighter} skyliners:console_climb; wait ${LOOK_EVERY}t`);
  lift(freighter, WEATHER_HEIGHT);
  const hovering = waitForReport(freighterReport, hoveringInSpace, FREIGHTER_UP_TICKS);
  expect(hovering, `${what}: she hovers at y 1,600, past the black of space`);
  if (!hovering) {
    return undefined;
  }
  must(`wait ${SPACE_SETTLE_TICKS}t`);
  quietSpace();
  waitForReport(freighterReport, hoveringInSpace, FREIGHTER_UP_TICKS);
  return freighter;
}

// Whether a freighter's report has her hovering at y 1,600, past the black of space.
function hoveringInSpace(report) {
  return report.phase === "cruise" && Math.abs(report.altitude - WEATHER_HEIGHT) < SPACE_HELD_WITHIN && Math.abs(report.climb) < 1;
}

// Waits for any space weather blowing over the nearest ship to pass: it may blow by itself up there.
function quietSpace() {
  waitForReport(() => must("skyliners:spaceweather"), (weather) => weather.blowing === "none", WEATHER_TICKS_MOST);
}

// Seats the player at a flying freighter's laser cannon, from beside its seat among her blocks; whether the view went
// up to its sight.
function atTheGun(freighter) {
  const [cannon] = must(`sable:find ${freighter} skyliners:laser_cannon 1 side=server`).rows;
  const seats = must(`sable:find ${freighter} skyliners:pilot_seat 20 side=server`).rows.map((row) => row.at);
  const seat = cannon && seats.filter(([x, , z]) => Math.abs(x - cannon.at[0]) <= GUNNER_SEAT_OFF && Math.abs(z - cannon.at[2]) <= GUNNER_SEAT_OFF)
    .sort((a, b) => a[1] - b[1])[0];
  if (!seat) {
    expect(false, "space rocks: she has a laser cannon and a gunner's seat under it");
    return false;
  }
  must(`sable:tp ${seat.join(",")}; wait 20t`);
  sitAt(seat);
  const seated = gunnerSight() !== undefined;
  expect(seated, "space rocks: the player sits at her laser cannon, seeing from its sight");
  return seated;
}

// How far the first rock flying in to strike her, in a report of the space weather, is from the cannon's sight, in
// blocks; far off for none.
function rockFromSight(weather) {
  const sight = gunnerSight();
  if (!weather.rock_at || !sight) {
    return Infinity;
  }
  return Math.hypot(...weather.rock_at.split(",").map((v, i) => Number(v) - sight.at[i]));
}

// Waits for a rock to be flying in to strike her within so many blocks of the cannon's sight; the weather's report
// then, or undefined if none came.
function rockWithin(within) {
  return waitForReport(() => must("skyliners:spaceweather"), (weather) => weather.coming > 0 && rockFromSight(weather) <= within
    && withinTheGun(weather.rock_at), ROCK_WEATHER_TICKS);
}

// Whether a spot in the world, as a report gives it, lies where the cannon can look: not far under level, past her
// hull.
function withinTheGun(at) {
  const sight = gunnerSight();
  if (!sight) {
    return false;
  }
  const [x, y, z] = at.split(",").map(Number);
  return Math.atan2(y - sight.at[1], Math.hypot(x - sight.at[0], z - sight.at[2])) * 180 / Math.PI > -ROCK_LOWEST;
}

// The turn that aims the cannon's sight where the first rock flying in, in a report of the space weather, will be so
// many ticks on, for it to fly into the sight. The seated gunner turns in her frame, which her flight turns in the
// world, so the turn is the player's own corrected by how far the sight's look in the world is off the rock's way.
function aimAhead(weather, ahead) {
  const at = weather.rock_at.split(",").map(Number);
  const vel = weather.rock_vel.split(",").map(Number);
  const sight = gunnerSight();
  const { yaw, pitch } = must("status");
  const wanted = angles(at.map((v, i) => v + vel[i] * ahead / 20 - sight.at[i]));
  const looking = angles(sight.look);
  const round180 = (degrees) => ((degrees % 360) + 540) % 360 - 180;
  return `turn ${round(yaw + round180(wanted.toward - looking.toward))} ${round(pitch - (wanted.up - looking.up))}`;
}

// A way in the world as Minecraft turns a look: round from south, toward west, and up from level, in degrees.
function angles([x, y, z]) {
  return { toward: Math.atan2(-x, z) * 180 / Math.PI, up: Math.atan2(y, Math.hypot(x, z)) * 180 / Math.PI };
}

// How much a strike landing at a tick changes a ship's speed at most, in blocks a second, from just before it to a
// second after; and how fast it sets her turning at most, in degrees a second, from how far she turns between samples.
function strikeAt(ship, lands, lead = STRIKE_LEAD_TICKS, samples = STRIKE_SAMPLES) {
  const ahead = Math.max(0, lands - now() - lead);
  const rows = must(`wait ${ahead}t; sample sable:info ${ship} side=server every 2t x${samples}`, BUILD_SECONDS).rows.filter((row) => row.vel);
  return { shove: shoveIn(rows), spin: spinIn(rows) };
}

// The most a ship's speed changed from the first of some samples of her, in blocks a second.
function shoveIn(rows) {
  const first = rows[0].vel;
  return Math.max(...rows.map((row) => Math.hypot(...row.vel.map((v, i) => v - first[i]))));
}

// How fast a ship turned at most between some samples of her taken every two ticks, in degrees a second, about any of
// her axes.
function spinIn(rows) {
  const turned = (a, b) => Math.abs(((a - b + 540) % 360) - 180);
  const rates = rows.slice(1).map((row, i) => Math.max(turned(row.yaw, rows[i].yaw), turned(row.pitch, rows[i].pitch), turned(row.roll, rows[i].roll)) * 10);
  return Math.max(0, ...rates);
}

// Aims the cannon's sight ahead of a rock flying in to strike her as it comes into its reach and launches two rockets
// at it, a rock at a time, until one wears through or it has tried ROCK_AIM_TRIES times; what the sight said as each
// pair was launched, the last first, and what it says after. She may still be turning from a strike, so the look in
// the world drifts under a sight held still in her frame, and a try can miss.
function shootRocks(before) {
  const tries = [];
  for (let tried = 0; tried < ROCK_AIM_TRIES; tried++) {
    const coming = rockWithin(ROCK_AIM_WITHIN);
    if (!coming) {
      break;
    }
    const sights = send(`${aimAhead(coming, ROCK_AIM_AHEAD)}; wait ${ROCK_LAUNCH_AFTER}t; skyliners:gunner; key press key.use; wait 6t; key press key.use; `
      + `wait ${ROCK_ROCKET_TICKS}t; skyliners:gunner`, BUILD_SECONDS).filter((reply) => "on" in reply);
    tries.unshift(sights);
    if (must("skyliners:spaceweather").shot_down > before.shot_down) {
      break;
    }
    must(`wait ${ROCK_RELOAD_TICKS}t`);
  }
  return tries;
}

// Space rocks shot down: a freighter hovering at y 1,600, the player at her laser cannon and an asteroid storm brought
// over her, the server's config having strikes shove her none, so her cannon's aim holds; aimed at a rock flying in to
// strike her, the cannon's sight locks on to it, and rockets launched at it home in and burst it, at the third try at
// the latest: the server counts it shot down, and the player's client is told so, drawing it no more.
function checkSpaceRocksShot() {
  const freighter = freighterInSpace("space rocks");
  if (!freighter || !atTheGun(freighter)) {
    return;
  }
  must("config skyliners server spaceWeather.strength 0");
  try {
    shootSpaceRocks();
  } finally {
    send("?config reset");
  }
  must(`key press key.sneak 10t; wait 10t`);
  quietSpace();
}

// Bolts and rockets fired at the rocks of asteroid storms from the cannon the player sits at, for checkSpaceRocksShot.
function shootSpaceRocks() {
  const before = must("skyliners:spaceweather");
  const asked = must("skyliners:spaceweather start=asteroid_storm");
  expect(asked.started === "coming", "space rocks: an asteroid storm is brought over her", `${asked.started}`);
  let lost = boltAtARealRock();
  if (lost === undefined) {
    quietSpace();
    must("skyliners:spaceweather start=asteroid_storm");
    lost = boltAtARealRock();
  }
  expect(lost === ROCK_BLOCK_WEIGHT, "space rocks: one bolt breaks exactly one block of the rock it strikes", `its weight fell by ${lost}`);
  quietSpace();
  waitForReport(freighterReport, hoveringInSpace, FREIGHTER_UP_TICKS);
  const storm = must("skyliners:spaceweather start=asteroid_storm");
  expect(storm.started === "coming", "space rocks: another asteroid storm is brought over her", `${storm.started}`);
  const tries = shootRocks(before);
  const [[aimed, after] = []] = tries;
  expect(aimed?.locked === true, "space rocks: aimed at a rock flying in to strike her, her cannon's sight locks on to it", `${tries.length} tries`);
  const weather = must("skyliners:spaceweather");
  expect(weather.shot_down > before.shot_down, "space rocks: rockets launched at it home in and wear it through", `${weather.shot_down - before.shot_down} burst`);
  expect(after?.bursts_seen > 0, "space rocks: the player's client is told it burst, and draws it no more", `${after?.bursts_seen} seen burst`);
}

// What the cannon is aimed by, in a report of the space weather, for the heaviest rock that is a real body.
function realRockIn(weather) {
  return { rock_at: weather.real_at, rock_vel: weather.real_vel };
}

// Fires one bolt, at most, at a rock that is a real body, a rock at a time until one hits or it has tried
// ROCK_BOLT_TRIES times; how much weight that rock lost to the bolt that hit it, undefined if none did. A rock that
// landed meanwhile, breaking up on her, is no measure of the bolt.
function boltAtARealRock() {
  for (let tried = 0; tried < ROCK_BOLT_TRIES; tried++) {
    const rock = waitForReport(() => must("skyliners:spaceweather"), (weather) => weather.real_coming === true && weather.real_mass >= ROCK_BOLT_LEAST
      && withinTheGun(weather.real_at), ROCK_WEATHER_TICKS);
    if (!rock) {
      return undefined;
    }
    const replies = send(`${aimAhead(realRockIn(rock), ROCK_BOLT_AHEAD)}; wait ${ROCK_BOLT_AFTER}t; skyliners:spaceweather; key down key.attack; wait 1t; key up key.attack; `
      + "wait 6t; skyliners:spaceweather", BUILD_SECONDS).filter((reply) => "real" in reply);
    const [aimed, after] = replies;
    if (aimed?.real_id === after?.real_id && after.real_mass < aimed.real_mass && after.landed === aimed.landed) {
      return aimed.real_mass - after.real_mass;
    }
    must(`wait ${ROCK_BOLT_BETWEEN}t`);
  }
  return undefined;
}

// Space rocks to shoot at from afar: a freighter hovering at y 1,600, the player at her laser cannon and an asteroid
// storm brought over her, strikes shoving her none; rocks become real bodies, which the cannon's sight locks on to and
// its bolts and rockets break, well over 150 blocks from her middle, where they used to only then, too late for
// rockets to crack a giant clear of her; and the player's client holds one that far off.
function checkSpaceRocksFar() {
  const freighter = freighterInSpace("far rocks");
  if (!freighter || !atTheGun(freighter)) {
    return;
  }
  must("config skyliners server spaceWeather.strength 0");
  try {
    farRocks();
  } finally {
    send("?config reset");
  }
  must(`key press key.sneak 10t; wait 10t`);
  quietSpace();
}

// How far out an asteroid storm's rocks become real, on the server and on the player's client, for checkSpaceRocksFar.
function farRocks() {
  const asked = must("skyliners:spaceweather start=asteroid_storm");
  expect(asked.started === "coming", "far rocks: an asteroid storm is brought over her", `${asked.started}`);
  let seen = 0;
  const far = waitForReport(() => {
    seen = Math.max(seen, must("skyliners:gunner").real_farthest ?? 0);
    return must("skyliners:spaceweather");
  }, (weather) => weather.real_farthest >= FAR_ROCK_MADE && seen >= FAR_ROCK_SEEN, ROCK_WEATHER_TICKS);
  const made = far?.real_farthest ?? must("skyliners:spaceweather").real_farthest;
  expect(made >= FAR_ROCK_MADE, "far rocks: rocks become real bodies, for her cannon to shoot at, well over 150 blocks out", `${made} blocks out at the farthest`);
  expect(seen >= FAR_ROCK_SEEN, "far rocks: the player's client holds a rock's body that far off", `${round(seen)} blocks off at the farthest`);
}

// A storm's rocks fly on once it is over: an asteroid storm brought over a freighter hovering in space, strikes shoving
// her none. Some seconds after her computer says it is all clear, rocks are still on their way past her, drawn by the
// player's client, and the server has them there to be shot at; only once each has flown out of sight, a minute or so
// on, are they all gone. They used to vanish where they were the moment it was over.
function checkSpaceRocksFlyOn() {
  const freighter = freighterInSpace("rocks fly on");
  if (!freighter) {
    return;
  }
  must("config skyliners server spaceWeather.strength 0");
  try {
    rocksFlyOn();
  } finally {
    send("?config reset");
  }
}

// An asteroid storm's rocks watched as it passes and after, for checkSpaceRocksFlyOn.
function rocksFlyOn() {
  const since = now();
  const asked = must("skyliners:spaceweather start=asteroid_storm");
  expect(asked.started === "coming", "rocks fly on: an asteroid storm is brought over her", `${asked.started}`);
  const over = waitForReport(() => must("skyliners:spaceweather"), (weather) => weather.blowing === "none", WEATHER_TICKS_MOST);
  expect(Boolean(over) && played("skyliners:freighter_computer_all_clear", since).length > 0, "rocks fly on: the storm passes, her computer saying it is all clear");
  const drawn = must(`wait ${FLY_ON_LOOK_AFTER}t; skyliners:gunner`).rocks_flying;
  const weather = must("skyliners:spaceweather");
  expect(drawn > 0 && weather.flying_on === true, "rocks fly on: seconds after, its rocks are still on their way past her, there to be shot at",
    `${drawn} on their way for the player, flying on ${weather.flying_on}`);
  const gone = waitForReport(() => ({ ...must("skyliners:spaceweather"), drawn: must("skyliners:gunner").rocks_flying }),
    (report) => report.flying_on === false && report.drawn === 0, FLY_ON_TICKS_MOST);
  expect(Boolean(gone), "rocks fly on: once each has flown out of sight, the last of them is gone", gone ? "" : `${must("skyliners:gunner").rocks_flying} still on their way`);
}

// A rock that strikes her stops at her hull: an asteroid storm brought over a freighter hovering in space, and the
// rock that is a real body and coming at her followed until it has landed. It never gets anywhere near her middle, and
// it has landed having met her.
function checkSpaceRockSolid() {
  const freighter = freighterInSpace("solid rock");
  if (!freighter) {
    return;
  }
  const [mx, mz] = String(freighterReport().middle).split(",").map(Number);
  const my = freighterReport().altitude;
  const asked = must("skyliners:spaceweather start=asteroid_storm");
  const real = waitForReport(() => must("skyliners:spaceweather"), (weather) => weather.real_coming === true, ROCK_WEATHER_TICKS);
  expect(real, "solid rock: a rock coming at her becomes a real body of blocks", real ? `${real.real_size} blocks across` : "");
  if (!real) {
    return;
  }
  let nearest = Infinity;
  const landed = waitForReport(() => {
    const weather = must("skyliners:spaceweather");
    if (weather.real_coming && weather.real_at) {
      const [x, y, z] = weather.real_at.split(",").map(Number);
      nearest = Math.min(nearest, Math.hypot(x - mx, y - my, z - mz));
    }
    return weather;
  }, (weather) => weather.landed > asked.landed, ROCK_WEATHER_TICKS);
  expect(landed, "solid rock: it lands on her by meeting her", `${landed?.landed ?? 0} landed`);
  expect(Number.isFinite(nearest) && nearest > ROCK_NEAREST_MIDDLE, "solid rock: it stops at her hull, nowhere near her middle",
    `at the nearest ${round(nearest)} blocks from her middle`);
  quietSpace();
}

// A storm's rocks leave nothing behind: an asteroid storm brought over a freighter in space, and the world left and
// joined again with rocks flying as real bodies. No body lighter than a ship comes back, ships from earlier checks
// being there too, even once the sweep that takes up what no weather flies has had time to run.
function checkSpaceRocksNotSaved() {
  const freighter = freighterInSpace("rocks not saved");
  if (!freighter) {
    return;
  }
  must("skyliners:spaceweather start=asteroid_storm");
  const flying = waitForReport(() => must("skyliners:spaceweather"), (weather) => weather.real > 0, ROCK_WEATHER_TICKS);
  expect(flying, "rocks not saved: rocks fly as real bodies", flying ? `${flying.real} flying` : "");
  if (!flying) {
    return;
  }
  must(`sable:tp ${freighter} skyliners:console_climb; wait ${LOOK_EVERY}t; leave; join world ${WORLD}; wait ${RELOADED_SETTLE_TICKS}t`, BUILD_SECONDS);
  must(`wait ${ROCK_SWEEP_TICKS}t`);
  const masses = must("server sable info @e").rows.map((row) => /Mass: ([\d.]+)/.exec(row.line)?.[1]).filter(Boolean).map(Number);
  const rocks = masses.filter((mass) => mass < ROCK_LIGHTER_THAN);
  expect(masses.length > 0 && rocks.length === 0, "rocks not saved: no rock comes back with the world, only ships", `${masses.length} bodies: ${masses.map(round).join(", ")}`);
}

// A solar flare's pulse: hovering in space, as it arrives her systems go out for five seconds, as a real one's could:
// every thruster let go, the lights out and her screens dark, her computer saying so, then all of it coming back by
// itself, and she climbs back from her fall, nobody aboard hurt. The server's config sets how long it lasts, her
// screens staying dark all the while, even once she has fallen out of the weather.
function checkFreighterBlackout() {
  const freighter = freighterInSpace("blackout");
  if (!freighter) {
    return;
  }
  const [{ at: strip }] = must(`sable:find ${freighter} skyliners:led_strip 1 side=server`).rows;
  const bright = () => Number(must(`block ${strip.join(",")} side=server`).props?.brightness ?? 0);
  expect(bright() > 0, "blackout: her lights are on to start with", `brightness ${bright()}`);
  survival();
  const health = healthOf();
  const since = now();
  const asked = must("skyliners:spaceweather start=solar_flare");
  expect(asked.started === "coming", "blackout: a solar flare is brought over her", `${asked.started}`);
  const out = waitForReport(freighterReport, (report) => report.lift === 0, BLACKOUT_WITHIN);
  expect(out, "blackout: as the flare reaches her, every thruster is let go", out ? "" : `lift ${freighterReport()?.lift}`);
  if (!out) {
    quietSpace();
    return;
  }
  const wentOut = now();
  must(`wait ${LOOK_EVERY}t`);
  expect(bright() === 0, "blackout: every light aboard goes out", `brightness ${bright()}`);
  expect(must("skyliners:screens").dark, "blackout: her screens go dark");
  expect(heard("skyliners:freighter_computer_blackout", since, 0), "blackout: her computer says everything's gone dark");
  const back = waitForReport(freighterReport, (report) => report.lift > 0, BLACKOUT_WITHIN);
  const lasted = now() - wentOut;
  expect(back && Math.abs(lasted - BLACKOUT_SECONDS * TICKS_A_SECOND) <= BLACKOUT_OFF_BY, "blackout: it lasts five seconds", `${lasted} ticks`);
  expect(back, "blackout: her thrusters come back by themselves", back ? `having fallen ${round(out.altitude - back.altitude)} blocks` : `lift ${freighterReport()?.lift}`);
  must(`wait ${2 * LOOK_EVERY}t`);
  expect(bright() > 0, "blackout: her lights come back as they were", `brightness ${bright()}`);
  expect(heard("skyliners:freighter_computer_systems_back", since, 0), "blackout: and her computer says her systems are back");
  const climbing = waitForReport(freighterReport, (report) => report.climb > 1, BLACKOUT_CLIMBS_WITHIN);
  expect(climbing, "blackout: having fallen, she climbs back by herself",
    climbing ? `at y ${climbing.altitude}` : `climb ${freighterReport()?.climb}`);
  expect(healthOf() >= health, "blackout: nobody aboard is hurt", `health ${healthOf()} of ${health}`);
  creative();
  quietSpace();
  longerBlackout(freighter);
}

// A flare over a freighter with the server's config asking for a longer blackout: it lasts as long as that says, and
// her screens stay dark all the while, even once she has fallen out of the weather.
function longerBlackout(freighter) {
  must(`config skyliners server spaceWeather.flareBlackoutSeconds ${BLACKOUT_SET_SECONDS}`);
  try {
    lift(freighter, WEATHER_HEIGHT);
    must(`wait ${SPACE_SETTLE_TICKS}t; skyliners:spaceweather start=solar_flare`);
    const out = waitForReport(freighterReport, (report) => report.lift === 0, BLACKOUT_WITHIN);
    const wentOut = now();
    if (out) {
      must(`wait ${BLACKOUT_SCREENS_LOOK}t`);
      expect(must("skyliners:screens").dark, "blackout: her screens stay dark, even once she has fallen out of the weather",
        `at y ${freighterReport()?.altitude}`);
    }
    const back = out && waitForReport(freighterReport, (report) => report.lift > 0, BLACKOUT_WITHIN);
    const lasted = now() - wentOut;
    expect(back && Math.abs(lasted - BLACKOUT_SET_SECONDS * TICKS_A_SECOND) <= BLACKOUT_OFF_BY, "blackout: the server's config sets how long it lasts", `${lasted} ticks`);
    quietSpace();
  } finally {
    send("?config reset");
  }
}

function checkGiantAsteroid() {
  const freighter = freighterInSpace("giant asteroid");
  if (!freighter) {
    return;
  }
  survival();
  const health = healthOf();
  const asked = must("skyliners:spaceweather start=giant_asteroid");
  expect(asked.started === "coming", "giant asteroid: brought over her", `${asked.started}`);
  const ordinary = waitForReport(() => must("skyliners:spaceweather"), (weather) => weather.coming > 0 && !weather.rock_giant, GIANT_TICKS);
  const rock = ordinary ? strikeAt(freighter, ordinary.rock_lands) : { shove: 0, spin: 0 };
  const coming = waitForReport(() => must("skyliners:spaceweather"), (weather) => weather.giant_lands > 0, GIANT_TICKS);
  expect(coming, "giant asteroid: a giant flies in to strike her", coming ? `${coming.giant_size} blocks across` : "");
  if (!coming) {
    creative();
    return;
  }
  const first = waitForReport(() => must("skyliners:spaceweather"), (weather) => weather.rock_giant === true, GIANT_TICKS);
  if (first) {
    const [at, vel, target] = [first.rock_at, first.rock_vel, first.rock_target].map((value) => value.split(",").map(Number));
    const left = (first.rock_lands - first.tick) / 20;
    const off = Math.hypot(...at.map((value, i) => value + vel[i] * left - target[i]));
    expect(Math.abs(off - first.rock_size / 2) <= GIANT_FRONT_WITHIN, "giant asteroid: it reaches her hull with its front, not its middle", `its middle ${round(off)} blocks from where it lands, ${round(first.rock_size)} across`);
  }
  must("perf start giant");
  const giant = strikeAt(freighter, coming.giant_lands, GIANT_STRIKE_LEAD_TICKS, GIANT_STRIKE_SAMPLES);
  must(`wait ${GIANT_TUMBLE_TICKS}t`);
  const { summary } = must("perf stop");
  const mspt = summary?.server?.mspt ?? summary?.mspt ?? {};
  expect(giant.shove >= GIANT_SHOVE_LEAST && giant.shove >= rock.shove * GIANT_HARDER && giant.spin >= GIANT_SPIN_LEAST,
    "giant asteroid: its strike shoves her far harder than an ordinary rock's and sets her spinning",
    `giant: ${round(giant.shove)} blocks a second, turning ${round(giant.spin)} degrees a second; a rock: ${round(rock.shove)} and ${round(rock.spin)}`);
  expect((mspt.avg ?? 0) <= GIANT_TICK_MS && (mspt.max ?? 0) <= GIANT_WORST_TICK_MS, "giant asteroid: the server never stalls as she tumbles",
    `server ticks ${round(mspt.avg ?? NaN)} ms on average, ${round(mspt.max ?? NaN)} ms at the worst`);
  quietSpace();
  const after = healthOf();
  expect(after >= health, "giant asteroid: nobody aboard is hurt", `health ${after}, ${health} before`);
  creative();
}

// A giant asteroid cracked by rockets: a freighter hovering at y 1,600, the player at her laser cannon and a giant
// asteroid brought over her, the server's config having strikes shove her none, so her cannon's aim holds; aimed at the
// giant as it comes into reach as a real body, the rockets launched at it crack it right through, the server never
// stalling. It flies on in pieces, each lighter than the giant, flying apart and off the course it held, and whatever
// of it still meets her lands softer than the whole would have.
function checkGiantAsteroidCracked() {
  const freighter = freighterInSpace("giant cracked");
  if (!freighter || !atTheGun(freighter)) {
    return;
  }
  must("config skyliners server spaceWeather.strength 0");
  try {
    crackGiant();
  } finally {
    send("?config reset");
  }
  must(`key press key.sneak 10t; wait 10t`);
  quietSpace();
}

// Brings a giant asteroid over the freighter the player sits at the cannon of and launches rockets at it as it comes
// into reach, for checkGiantAsteroidCracked.
function crackGiant() {
  const asked = must("skyliners:spaceweather start=giant_asteroid");
  expect(asked.started === "coming", "giant cracked: a giant asteroid is brought over her", `${asked.started}`);
  must("perf start crack");
  let whole;
  let cracked = [];
  for (let tried = 0; tried < GIANT_CRACK_TRIES && cracked.length === 0; tried++) {
    const coming = waitForReport(() => must("skyliners:spaceweather"), (weather) => weather.real_coming === true && weather.real_mass >= GIANT_MASS_LEAST
      && withinTheGun(weather.real_at), GIANT_TICKS);
    if (!coming || (whole && giantLanded(coming, whole))) {
      break;
    }
    whole ??= coming;
    cracked = send(`${aimAhead(realRockIn(coming), ROCK_AIM_AHEAD)}; wait ${ROCK_LAUNCH_AFTER}t; key press key.use; wait 6t; key press key.use; `
      + `sample skyliners:spaceweather every 4t x${GIANT_CRACK_SAMPLES}`, BUILD_SECONDS).flatMap((reply) => reply.rows ?? [])
      .filter((weather) => giantPieces(weather).length >= 2);
  }
  const [first] = cracked;
  const after = cracked.at(-1);
  const { summary } = must("perf stop");
  const mspt = summary?.server?.mspt ?? summary?.mspt ?? {};
  expect(whole, "giant cracked: the giant comes into her cannon's reach as a real body");
  if (!whole) {
    return;
  }
  const pieces = giantPieces(after);
  expect(pieces.length >= 2, "giant cracked: rockets launched at it crack it right through",
    `${pieces.length} pieces of it coming at her, ${after?.pieces_mass} of ${round(whole.real_mass)}, ${after?.shot_down} shot down, ${after?.real} real`);
  expect((mspt.max ?? Infinity) <= GIANT_WORST_TICK_MS, "giant cracked: the server never stalls as it cracks", `${round(mspt.max ?? NaN)} ms at the worst`);
  if (pieces.length >= 2) {
    const [heaviest, next] = pieces;
    expect(heaviest.mass < whole.real_mass, "giant cracked: each piece is lighter than the giant was",
      `${pieces.map((piece) => round(piece.mass)).join(", ")} of ${round(whole.real_mass)}`);
    const apart = Math.hypot(...heaviest.vel.map((v, i) => v - next.vel[i]));
    expect(apart >= GIANT_APART_LEAST, "giant cracked: its pieces fly apart", `${round(apart)} blocks a second apart`);
    const turned = degreesBetween(whole.real_vel.split(",").map(Number), heaviest.vel);
    expect(turned >= GIANT_TURNED_LEAST, "giant cracked: it goes off the course it held", `${round(turned)} degrees off it`);
    const struck = waitForReport(() => must("skyliners:spaceweather"), (weather) => giantLanded(weather, first) || weather.blowing === "none", GIANT_TICKS);
    const landed = giantLanded(struck, first);
    expect(!landed || struck.last_share <= GIANT_PIECE_SHARE_MOST, "giant cracked: what still meets her lands softer than the whole giant would have",
      landed ? `a piece of ${round(struck.last_share * 100)}% of it landed` : "none of it met her");
  }
}

// Whether the giant, or a piece of it, has landed on her by a report of the space weather, since an earlier report.
function giantLanded(weather, since) {
  return weather?.landed > since.landed && weather.last_giant === true;
}

// The pieces of a giant coming at her in a report of the space weather, the heaviest first: how heavy each is, in
// Sable's units, and how fast it moves, in blocks a second; none for no report.
function giantPieces(weather) {
  if (!weather?.pieces_coming) {
    return [];
  }
  const vels = weather.pieces_vel.split(";").map((vel) => vel.split(",").map(Number));
  return weather.pieces_mass.split(";").map((mass, i) => ({ mass: Number(mass), vel: vels[i] })).filter((piece) => piece.mass >= GIANT_PIECE_LEAST)
    .sort((a, b) => b.mass - a.mass);
}

// The angle between two ways, in degrees.
function degreesBetween(a, b) {
  const dot = a.reduce((sum, v, i) => sum + v * b[i], 0);
  return Math.acos(Math.max(-1, Math.min(1, dot / (Math.hypot(...a) * Math.hypot(...b))))) * 180 / Math.PI;
}

// A freighter's slam landing: hovering, its button lit, her thrusters roar and her computer braces everyone, and she
// drives straight down where she is far faster than she ever lands, strikes the ground with her crash heard, yet
// touches it slowly enough that nothing breaks, then stands level on her legs and switches off, its button dark. The
// player, standing aboard at it in survival, is neither hurt nor left behind, and no piece of her is shed as a ship of
// its own, even once her ramp has come down after.
function checkFreighterSlam() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + SLAM_HEIGHT} speed=0`, BUILD_SECONDS);
  const hovering = waitForReport(freighterReport, (report) => report.phase === "cruise" && Math.abs(report.climb) < 1, FREIGHTER_UP_TICKS);
  expect(hovering, "freighter: she lifts off and hovers");
  if (!hovering) {
    return;
  }
  expect(hovering.slam === "ready", "freighter: flying along over the ground, her slam button is lit", `slam ${hovering.slam}`);
  const freighter = findShipFlying("skyliners:engine_grille");
  const { at } = must(`sable:find ${freighter} skyliners:console_slam 1 side=server`).rows[0];
  const stood = must(`sable:tp ${at.join(",")}; wait 20t; status`).pos;
  survival();
  const health = healthOf();
  const pressed = now();
  must(`use ${at.join(",")}`);
  const rows = must(`sample sable:info ${freighter} brief side=server every 2t x${SLAM_WATCH_TICKS / 2}`, BUILD_SECONDS).rows;
  expect(played("*speed_boost*", pressed).length > 0, "freighter: her slam button roars into it");
  expect(played("skyliners:freighter_computer_slam", pressed).length > 0, "freighter: her computer braces everyone");
  const fastest = Math.min(...rows.map((row) => row.climb));
  expect(-fastest >= SLAM_FASTEST_LEAST, "freighter: slamming down, she drives down far faster than she lands", `${round(-fastest)} blocks a second at the fastest`);
  const touched = rows.findIndex((row) => row.agl !== "none" && row.agl < SLAM_TOUCHED);
  const touchdown = touched > 0 ? Math.min(rows[touched - 1].climb, rows[touched].climb) : NaN;
  expect(-touchdown <= SLAM_TOUCHDOWN_MOST, "freighter: yet she touches down slowly enough that nothing breaks",
    touched > 0 ? `${round(-touchdown)} blocks a second at ${rows[touched].agl} over the ground` : "she never touched");
  const fastestAt = rows.findIndex((row) => row.climb === fastest);
  const slowest = Math.max(...rows.slice(fastestAt, touched).filter((row) => row.agl === "none" || row.agl > SLAM_CUSHIONED).map((row) => row.climb));
  expect(fastestAt < touched && -slowest >= SLAM_PAUSED, "freighter: she comes down in one go, never stopping to wait for her legs",
    `${round(-slowest)} blocks a second at the slowest, ${(touched * 2) / 20} s from the button to the ground`);
  const crash = played("skyliners:freighter_slam", pressed);
  expect(crash.length > 0, "freighter: she strikes the ground with a crash", crash.map(soundOf).join(", "));
  const hits = played(IMPACT_SOUND, pressed);
  expect(hits.length === 0, "freighter: Sable feels no hit as she touches down, her crash all show", hits.map(soundOf).join(", "));
  const down = waitForReport(freighterReport, (report) => report.phase === "off", FREIGHTER_DOWN_TICKS);
  expect(down, "freighter: she settles and switches off", down ? "" : `still ${freighterReport()?.phase}`);
  if (down) {
    const lean = Math.max(Math.abs(down.tilt_front), Math.abs(down.tilt_right));
    expect(Math.abs(down.altitude - head[1]) < FREIGHTER_HOLD_WITHIN && lean < FREIGHTER_LEVEL_WITHIN && down.legs === "down",
      "freighter: she stands level on her legs", `control head at ${down.altitude}, built at ${head[1]}, leaning ${lean}, legs ${down.legs}`);
    expect(down.slam === "unavailable", "freighter: on the ground, her slam button is dark", `slam ${down.slam}`);
  }
  const after = healthOf();
  expect(after >= health, "freighter: nobody aboard is hurt", `health ${after}, ${health} before`);
  const { pos } = must("status");
  const away = Math.hypot(pos[0] - stood[0], pos[2] - stood[2]);
  expect(away < SLAM_ABOARD_WITHIN && Math.abs(pos[1] - head[1]) < SLAM_ABOARD_WITHIN, "freighter: the player is still aboard at her console",
    `${round(away)} blocks from where they stood, at y ${round(pos[1])}, her control head at y ${head[1]}`);
  must(`wait ${SLAM_RAMP_TICKS}t`);
  const pieces = must(`sable:list ${SHED_LOOK_BLOCKS} side=server`).rows.filter((row) => row.id !== freighter);
  expect(pieces.length === 0, "freighter: no piece of her is shed as a ship of its own", pieces.map((piece) => `${piece.id} size ${piece.size}`).join(", "));
  creative();
}

// A freighter's boost: cruising, its button fires her mains flat out a while, her roar going up and her speed with
// it, and afterwards she slows back to her speed; pressed again straight after, it rests, saying so.
function checkFreighterBoost() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=${BOOST_CRUISE}`, BUILD_SECONDS);
  const cruising = waitForReport(freighterReport, (report) => report.phase === "cruise" && Math.abs(report.ahead - BOOST_CRUISE) < 1,
    FREIGHTER_UP_TICKS);
  expect(cruising, "freighter: she lifts off and cruises at the speed set");
  if (!cruising) {
    return;
  }
  const freighter = findShipFlying("skyliners:engine_grille");
  const { at } = must(`sable:find ${freighter} skyliners:console_boost 1 side=server`).rows[0];
  const pressed = must(`sable:tp ${at.join(",")}; wait 20t; messages limit=1`).tick;
  must(`use ${at.join(",")}`);
  expect(Boolean(heard("*speed_boost*", pressed, LOOK_EVERY)), "freighter: her boost button roars into it");
  const rows = must(`sample skyliners:freighter every 20t x${BOOST_WATCH_TICKS / 20}`, BUILD_SECONDS).rows;
  const fastest = Math.max(...rows.map((row) => row.ahead));
  expect(fastest >= BOOST_CRUISE + BOOST_GAIN, "freighter: boosting, she surges ahead", `fastest ${round(fastest)}, set ${BOOST_CRUISE}`);
  const again = now();
  must(`use ${at.join(",")}`);
  expect(must(`messages since=${again} kind=actionbar match=*resting* limit=1`).rows.length > 0, "freighter: pressed again at once, the boost rests");
  const settled = waitForReport(freighterReport, (report) => Math.abs(report.ahead - BOOST_CRUISE) < BOOST_SETTLED_WITHIN, BOOST_SETTLE_TICKS);
  expect(settled, "freighter: afterwards she slows back to her speed", settled ? `at ${settled.ahead}` : "");
  must("skyliners:autopilot land");
}

// A freighter's hyperspace jump: flying along with no pin, her jump button says there is nowhere to jump to; flying to a
// pin far off, a pillar of stone standing on it taller than she flies, the button spools her drive up, the land round
// the pin loads as she spools without ever holding the server up, and she comes out over the pin, whoever pressed it
// still aboard, her lowest block kept well clear over the pillar's top rather than in it, and no frame of the game
// held up.
function checkFreighterJump() {
  const site = layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const below = head[1] - ground;
  const height = head[1] + FREIGHTER_CLIMB;
  const pin = [site.x + JUMP_PIN, site.z];
  must(`server forceload add ${pin[0]} ${pin[1]}; server fill ${pin[0]} ${ground} ${pin[1]} ${pin[0]} ${ground + JUMP_PILLAR - 1} ${pin[1]} stone`);
  must(`server forceload remove ${pin[0]} ${pin[1]}`);
  must(`skyliners:autopilot fly altitude=${height} speed=${BOOST_CRUISE}`, BUILD_SECONDS);
  const cruising = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(cruising, "freighter: she lifts off and cruises");
  if (!cruising) {
    return;
  }
  const freighter = findShipFlying("skyliners:engine_grille");
  const { at } = must(`sable:find ${freighter} skyliners:console_jump 1 side=server`).rows[0];
  const noPin = must(`sable:tp ${at.join(",")}; wait 20t; messages limit=1`).tick;
  must(`use ${at.join(",")}`);
  expect(Boolean(heard("*jump_no_pin*", noPin, LOOK_EVERY)), "freighter: with no pin set, her jump button says there is nowhere to jump to");
  must(`skyliners:autopilot fly altitude=${height} speed=${BOOST_CRUISE} pin=${pin[0]},${pin[1]}; wait 20t`);
  const pressed = must(`sable:tp ${at.join(",")}; wait 20t; messages limit=1`).tick;
  send("?perf stop");
  must(`perf start jump; use ${at.join(",")}`);
  expect(Boolean(heard("*jump_drive_spool*", pressed, LOOK_EVERY)), "freighter: her jump button spools her drive up");
  // Waited out on the client's clock: a question for the server would wait behind the land loading at her pin.
  must(`wait ${JUMP_WITHIN}t`, BUILD_SECONDS);
  const { summary } = must("perf stop");
  const longest = summary?.server?.mspt?.max ?? 0;
  const longestFrame = summary?.client?.frame_ms?.max ?? 0;
  expect(longestFrame < JUMP_LONGEST_FRAME, "freighter: her jump never holds the game up for whoever is aboard, the flash as she comes out clearing",
    `longest frame ${round(longestFrame)} ms`);
  const out = freighterReport();
  const off = out ? Math.hypot(...out.middle.split(",").map(Number).map((v, i) => v - pin[i])) : Infinity;
  expect(off < JUMP_NEAR, "freighter: she comes out of the jump over her pin", out ? `middle at ${out.middle}` : "no report");
  if (off >= JUMP_NEAR) {
    return;
  }
  const loaded = out.jump_loaded_after < 0 ? "not in yet as she went, at the cap" : `in after ${out.jump_loaded_after} ticks`;
  expect(longest < JUMP_LONGEST_TICK, "freighter: her jump never holds the server up, the land at her pin loading as she spools",
    `longest tick ${round(longest)} ms; the land under her ${loaded}`);
  const clear = out.altitude - below - (ground + JUMP_PILLAR - 1);
  expect(clear >= JUMP_CLEAR, "freighter: she comes out with her lowest block well clear over the pillar on her pin", `${round(clear)} blocks over it`);
  const aboard = must("wait 40t; status");
  expect(aboard.aboard !== undefined || JSON.stringify(aboard).includes("aboard"), "freighter: whoever pressed the button is still aboard her",
    aboard.pos?.map(round).join(","));
  const flashes = must(`particles since=${pressed}`).rows.find((row) => row.type === "flash")?.count ?? 0;
  expect(flashes === 0, "freighter: whoever is aboard sees the jump from inside only, not the flashes those outside her see", `${flashes} flashes`);
  const outside = played("skyliners:freighter_hyperspace_*", pressed);
  expect(outside.length === 0, "freighter: and hears it from inside only, not the charge, whump, rush or thunderclap those outside her hear",
    outside.map(soundOf).join(", "));
  must("skyliners:autopilot land");
}

// A freighter's hyperspace jump watched from the ground, where once she only vanished and reappeared: someone standing
// off her course, between where she goes from and where she comes out, hears her charge whine up and go with a whump
// where she was, and a rush and a thunderclap over her pin, each as heard near by, and sees a flash at each end,
// glitter hanging in the air and her streaks of light, and her stretching out ahead into the jump and snapping back out
// of a stretch over her pin.
function checkFreighterJumpSeen() {
  const site = layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const height = head[1] + FREIGHTER_CLIMB;
  must(`skyliners:autopilot fly altitude=${height} speed=${BOOST_CRUISE}`, BUILD_SECONDS);
  const cruising = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(cruising, "freighter: she lifts off and cruises");
  if (!cruising) {
    return;
  }
  const freighter = findShipFlying("skyliners:engine_grille");
  const [x, z] = cruising.middle.split(",").map(Number);
  const pin = [x + SEEN_PIN, z];
  must(`skyliners:autopilot fly altitude=${height} speed=${BOOST_CRUISE} pin=${pin[0]},${pin[1]}; wait 20t`);
  const { at } = must(`sable:find ${freighter} skyliners:console_jump 1 side=server`).rows[0];
  must(`sable:tp ${at.join(",")}; wait 20t`);
  const jumpsSeen = must("skyliners:stretch").jumps;
  // Off her once the button has spooled her drive up, onto the ground: off her at once, the server would have her out
  // of reach of the button before the click reached it.
  const pressed = now();
  must(`use ${at.join(",")}`);
  expect(Boolean(heard("*jump_drive_spool*", pressed, LOOK_EVERY)), "freighter: her jump button spools her drive up", site.what);
  must(`server tp @p ${x + SEEN_PIN / 2} ${ground} ${z + SEEN_ASIDE}`);
  const comingOut = heard("skyliners:freighter_hyperspace_in", pressed, JUMP_WITHIN);
  const going = played("skyliners:freighter_hyperspace_out", pressed, "wait 40t; ")[0];
  const off = (sound) => (sound ? Math.hypot(sound.at_abs[0] - pin[0], sound.at_abs[2] - pin[1]) : NaN);
  expect(off(going) >= SEEN_OUT_OFF, "freighter: watched from the ground, she is heard going with a whump, where she was", going ? `${round(off(going))} blocks off her pin` : "not heard");
  expect(off(comingOut) <= SEEN_IN_NEAR, "freighter: and heard coming out with a thunderclap, over her pin", comingOut ? `${round(off(comingOut))} blocks off it` : "not heard");
  const leadUp = ["charge", "rush"].map((part) => played(`skyliners:freighter_hyperspace_${part}`, pressed)[0]);
  expect(leadUp.every(Boolean) && leadUp[0].tick < going?.tick && leadUp[1].tick < comingOut?.tick,
    "freighter: her charge whines up before she goes, and a rush swells before she comes out", leadUp.map((sound) => (sound ? soundOf(sound) : "not heard")).join(", "));
  const seen = Object.fromEntries(must(`particles since=${pressed}`).rows.map((row) => [row.type, row.count]));
  expect((seen.flash ?? 0) >= SEEN_FLASHES, "freighter: a flash where she goes and another where she comes out", `${seen.flash ?? 0} flashes`);
  expect((seen.end_rod ?? 0) >= SEEN_GLITTER, "freighter: glitter in the air at both ends", `${seen.end_rod ?? 0} specks`);
  const streaks = seen["skyliners:hyperspace_streak"] ?? seen.hyperspace_streak ?? 0;
  expect(streaks >= SEEN_STREAKS, "freighter: streaks of light as she goes and as she comes out", `${streaks} streaks`);
  const shown = must("skyliners:stretch");
  const stretched = shown.jumps > jumpsSeen ? shown : {};
  expect(stretched.going >= SEEN_STRETCH && stretched.going_frames >= SEEN_STRETCH_FRAMES, "freighter: she stretches out ahead into the jump as she goes",
    `${stretched.going ?? 1} times her length at most, in ${stretched.going_frames ?? 0} frames`);
  expect(stretched.coming >= SEEN_STRETCH && stretched.coming_frames >= SEEN_STRETCH_FRAMES, "freighter: and snaps back out of a stretch over her pin",
    `${stretched.coming ?? 1} times her length at most, in ${stretched.coming_frames ?? 0} frames`);
  const out = freighterReport();
  const there = out ? Math.hypot(...out.middle.split(",").map(Number).map((v, i) => v - pin[i])) : Infinity;
  expect(there < JUMP_NEAR, "freighter: she came out over her pin", out ? `middle at ${out.middle}` : "no report");
  must(`sable:tp ${at.join(",")}; wait 20t; skyliners:autopilot land`);
}

// A freighter's hyperspace jump heard from far off, where once it was heard no further than it was seen: someone over
// three hundred blocks from both ends, high over the ground between them, hears her go with a whump and come out with a
// thunderclap, each the duller one heard far off, and each as much later than she went as sound takes to come that far,
// as thunder comes a second after the lightning.
function checkFreighterJumpHeardFar() {
  const site = layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const height = head[1] + FREIGHTER_CLIMB;
  must(`skyliners:autopilot fly altitude=${height} speed=${BOOST_CRUISE}`, BUILD_SECONDS);
  const cruising = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(cruising, "freighter: she lifts off and cruises");
  if (!cruising) {
    return;
  }
  const freighter = findShipFlying("skyliners:engine_grille");
  const [x, z] = cruising.middle.split(",").map(Number);
  const pin = [x + SEEN_PIN, z];
  must(`skyliners:autopilot fly altitude=${height} speed=${BOOST_CRUISE} pin=${pin[0]},${pin[1]}; wait 20t`);
  const { at } = must(`sable:find ${freighter} skyliners:console_jump 1 side=server`).rows[0];
  must(`sable:tp ${at.join(",")}; wait 20t`);
  // Off her once the button has spooled her drive up, as freighter-jump-seen does, and up to the top of the world.
  const pressed = now();
  must(`use ${at.join(",")}`);
  expect(Boolean(heard("*jump_drive_spool*", pressed, LOOK_EVERY)), "freighter: her jump button spools her drive up", site.what);
  const perch = [Math.floor(x + SEEN_PIN / 2), HEARD_PERCH, Math.floor(z)];
  must(`server setblock ${perch[0]} ${perch[1] - 1} ${perch[2]} glass; server tp @p ${perch[0] + 0.5} ${perch[1]} ${perch[2] + 0.5}`);
  const thunder = heard("skyliners:freighter_hyperspace_in*", pressed, JUMP_WITHIN);
  const whump = played("skyliners:freighter_hyperspace_out*", pressed, "wait 40t; ")[0];
  const { pos } = must("status");
  const ear = [pos[0], pos[1] + EYE_HEIGHT, pos[2]];
  const offEar = (sound) => (sound ? Math.hypot(...sound.at_abs.map((v, i) => v - ear[i])) : NaN);
  expect(offEar(whump) >= HEARD_FAR_LEAST && offEar(thunder) >= HEARD_FAR_LEAST, "freighter: the listener stands over three hundred blocks from where she goes and where she comes out",
    `${round(offEar(whump))} and ${round(offEar(thunder))} blocks off`);
  expect(whump?.id === "skyliners:freighter_hyperspace_out_far", "freighter: heard from far off, she goes with a whump, the duller one", whump ? soundOf(whump) : "not heard");
  expect(thunder?.id === "skyliners:freighter_hyperspace_in_far", "freighter: and comes out with a thunderclap, the duller one", thunder ? soundOf(thunder) : "not heard");
  must(`sable:tp ${at.join(",")}; wait 20t`);
  const jumped = freighterReport()?.jumped_at ?? -1;
  for (const [what, sound] of [["whump", whump], ["thunderclap", thunder]]) {
    const travelled = offEar(sound) / HEARD_SOUND_SPEED;
    const later = sound && jumped >= 0 ? sound.tick - jumped - HEARD_SHOWN_LATER : NaN;
    expect(Math.abs(later - travelled) <= HEARD_SLACK, `freighter: her ${what} is heard as much later than a near one as sound takes to come that far`,
      `${later} ticks later, sound taking ${round(travelled)}; she jumped at ${jumped}`);
  }
  must("skyliners:autopilot land");
}

// Airframe fairings put down by hand turn corners as stairs do. Round an inside corner the middle one fills it in,
// round an outside corner it rounds it off, and a run hung under a ceiling turns its corner too, as in a room's cove;
// the fairings either side of a corner, a lone one and one in a straight run stay straight. Broken again, a corner's
// neighbour straightens out.
function checkFairingCorners() {
  creative();
  emptyHands();
  const x = nextSite++ * SITE_APART;
  const [rests, hangs] = [ground, ground + 1];
  must(`server tp @p ${x} ${ground} 0; wait ${CHUNKS_TICKS}t; server fill ${x + 2 * FAIRING_APART - 2} ${hangs + 1} -3 ${x + 2 * FAIRING_APART + 2} ${hangs + 1} 3 stone`);
  must("server give @p skyliners:airframe_fairing 64; wait 5t; select 0");
  const shapeAt = ([px, py, pz]) => must(`block ${px},${py},${pz} side=server`).props?.shape ?? "straight";
  // An inside corner, put down corner first, so it turns as its neighbours come.
  const inside = [x, rests, 0];
  putFairing(inside, "south");
  putFairing([x - 1, rests, 0], "south");
  putFairing([x, rests, 1], "west");
  expect(shapeAt(inside).startsWith("inner_"), "fairing: three put down round an inside corner, the middle one fills the corner in", shapeAt(inside));
  expect(shapeAt([x - 1, rests, 0]) === "straight" && shapeAt([x, rests, 1]) === "straight", "fairing: the two either side of an inside corner stay straight");
  // An outside corner, put down corner last, so it turns as it goes down.
  const outside = [x + FAIRING_APART, rests, 0];
  putFairing([outside[0] - 1, rests, 0], "north");
  putFairing([outside[0], rests, 1], "east");
  putFairing(outside, "north");
  expect(shapeAt(outside).startsWith("outer_"), "fairing: three put down round an outside corner, the middle one rounds it off", shapeAt(outside));
  // A cove: an inside corner hung under a stone ceiling.
  const cove = [x + 2 * FAIRING_APART, hangs, 0];
  putFairing(cove, "south", true);
  putFairing([cove[0] - 1, hangs, 0], "south", true);
  putFairing([cove[0], hangs, 1], "west", true);
  const hung = must(`block ${cove.join(",")} side=server`).props ?? {};
  expect(hung.half === "bottom" && (hung.shape ?? "straight").startsWith("inner_"), "fairing: hung under a ceiling round an inside corner, the middle one fills the corner in",
    `${hung.half ?? "top"} ${hung.shape ?? "straight"}`);
  // A lone one and a straight run.
  const lone = [x + 3 * FAIRING_APART, rests, 0];
  putFairing(lone, "south");
  [0, 1, 2].forEach((i) => putFairing([lone[0] + 2 + i, rests, 0], "south"));
  expect(shapeAt(lone) === "straight" && shapeAt([lone[0] + 3, rests, 0]) === "straight", "fairing: a lone one and the middle of a straight run stay straight",
    `${shapeAt(lone)}, ${shapeAt([lone[0] + 3, rests, 0])}`);
  must(`server tp @p ${x - 2.5} ${ground} 1.5 -90 40; wait 5t; mine ${x},${rests},1 timeout=5s; wait 5t`);
  expect(shapeAt(inside) === "straight", "fairing: one of an inside corner's neighbours broken, the corner straightens out", shapeAt(inside));
}

// Puts a fairing down by hand at a cell with its cut facing the given way: the player stands out from the cell that
// way, looking back at the ground under it, or at the ceiling over it for one that hangs.
function putFairing([px, py, pz], cut, hangs = false) {
  const [dx, dz] = STEPS[cut];
  const [sx, sz] = [px + 0.5 + dx * FAIRING_STAND_OFF, pz + 0.5 + dz * FAIRING_STAND_OFF];
  const yaw = { north: 0, east: 90, south: 180, west: -90 }[cut];
  const lookedAt = hangs ? py + 1 : py;
  const pitch = round((Math.atan2(ground + EYE_HEIGHT - lookedAt, FAIRING_STAND_OFF) * 180) / Math.PI);
  must(`server tp @p ${sx} ${ground} ${sz} ${yaw} ${pitch}; wait 5t`);
  send(`place skyliners:airframe_fairing ${px},${py},${pz}`);
}

// The console's modules set up by hand: each kind stands on a floor as a desk, fixed to a wall as a plate and hung
// under a ceiling as a wedge, each facing whoever put it up.
function checkConsoleMounts() {
  creative();
  const x = nextSite++ * SITE_APART;
  const z = 0;
  // A wall and a ceiling of stone running past the last kind's spot, two blocks apart from the first's.
  const far = x - 2 + 2 * CONSOLE_KINDS.length;
  must(`server tp @p ${x} ${ground} ${z}; wait ${CHUNKS_TICKS}t; server fill ${x - 6} ${ground} ${z - 4} ${far} ${ground + 3} ${z - 4} stone`);
  must(`server fill ${x - 6} ${ground + 3} ${z - 3} ${far} ${ground + 3} ${z - 1} stone`);
  CONSOLE_KINDS.forEach((kind) => must(`server give @p skyliners:${kind}`));
  const tries = [["floor", ground, z, 30], ["wall", ground + 1, z - 3, 0], ["ceiling", ground + 2, z - 2, -50]];
  for (const [mount, y, at, pitch] of tries) {
    for (const [i, kind] of CONSOLE_KINDS.entries()) {
      const spot = x - 4 + 2 * i;
      must(`server tp @p ${spot + 0.5} ${ground} ${z + 1.5} 180 ${pitch}; wait 5t; place skyliners:${kind} ${spot},${y},${at}`);
      const state = must(`block ${spot},${y},${at}`);
      expect(state.props?.mount === mount && state.props?.facing === "south", `${kind}: put up on a ${mount}, it is put up so, facing whoever put it up`,
        `${state.props?.mount} facing ${state.props?.facing}`);
    }
  }
}

// A transponder's panel: every picture's button is on the screen, the panel not running off it, and each picks a
// picture of its own, which the transponder keeps, the pictures for space among them.
function checkTransponderPictures() {
  creative();
  const x = nextSite++ * SITE_APART;
  must(`server tp @p ${x + 0.5} ${ground} 0.5 0 0; wait ${CHUNKS_TICKS}t; server setblock ${x} ${ground} 2 skyliners:transponder[facing=north]`);
  const panel = must(`wait 5t; use ${x},${ground},2; wait 10t; screen`);
  const [width, height] = panel.size ?? [0, 0];
  const buttons = (panel.rows ?? []).filter((row) => row.kind === "IconChoice");
  const offScreen = buttons.filter(({ bounds: [left, top, w, h] }) => left < 0 || top < 0 || left + w > width || top + h > height);
  expect(panel.screen === "transponder" && offScreen.length === 0, "transponder: every picture's button is on the screen",
    `${buttons.length} buttons, off it: ${offScreen.map((row) => row.label).join(", ") || "none"}`);
  const picked = buttons.map((row) => must(`click ${row.key}; wait ${PICK_TICKS}t; nbt ${x},${ground},2`).value?.Icon);
  expect(new Set(picked).size === buttons.length, "transponder: each button picks a picture of its own, and it keeps it", picked.join(" "));
  const missing = SPACE_PICTURES.filter((name) => !picked.includes(name));
  expect(missing.length === 0, "transponder: the pictures for space are among them", missing.length ? `missing ${missing.join(", ")}` : "");
  send("?close");
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
// Where an entity is, by a selector from the player: its position, or undefined for none.
function entityAt(selector) {
  const text = JSON.stringify(send(`server data get entity ${selector} Pos`)[0]);
  const found = [...text.matchAll(/(-?[0-9.]+(?:E-?[0-9]+)?)d/g)].map((match) => Number(match[1]));
  return found.length >= 3 ? found.slice(-3) : undefined;
}

// The player's health, in half hearts.
function healthOf() {
  const text = JSON.stringify(send("server data get entity @p Health")[0]);
  return Number(/(-?[0-9.]+)f/.exec(text)?.[1]);
}

// Waits for a test of the player's position to hold, looking every few ticks; their position then, or undefined.
function waitForPlayer(test, within) {
  for (let waited = 0; waited <= within; waited += 5) {
    const { pos } = must("wait 5t; status");
    if (test(pos)) {
      return pos;
    }
  }
  return undefined;
}

// Waits for an entity to stand on a pad's top, looking every few ticks; whether it did.
function waitOnPad(selector, [px, py, pz], within) {
  for (let waited = 0; waited <= within; waited += 5) {
    must("wait 5t");
    const at = entityAt(selector);
    if (at && Math.hypot(at[0] - px - 0.5, at[2] - pz - 0.5) <= BEAM_ON_PAD && Math.abs(at[1] - py - 1) <= BEAM_ON_PAD) {
      return true;
    }
  }
  return false;
}

// A freighter's tractor beam as she stands on her legs: pressed with the player on it, its pad in her cargo hold lights
// the beam under her belly and lets the player down it, unhurt; stepping out of the beam and back in, they are beamed
// up onto the pad again, and so is a cow, but never a zombie; and the beam itself, pressed from the ground, goes dark.
function checkFreighterTractorBeam() {
  ship("freighter");
  const [beamRow] = must("find skyliners:tractor_beam 128 1").rows;
  const [padRow] = must("find skyliners:beam_pad 128 1").rows;
  expect(Boolean(beamRow && padRow), "freighter: she is built with a tractor beam under her belly and its pad in her cargo hold");
  if (!beamRow || !padRow) {
    return;
  }
  const [bx, by, bz] = beamRow.at_abs;
  const pad = padRow.at_abs;
  const [px, py, pz] = pad;
  expect(by < py && Math.hypot(bx - px, bz - pz) <= 2, "freighter: the beam hangs under her belly beneath its pad", `beam ${bx},${by},${bz}, pad ${pad}`);
  survival();
  const pressed = now();
  must(`server tp @p ${px + 0.5} ${py + 1} ${pz + 0.5}; wait 10t; use ${pad.join(",")}; wait 5t`);
  const lit = must(`block ${bx},${by},${bz} side=server`).props?.lit === "true" && must(`block ${pad.join(",")} side=server`).props?.lit === "true";
  expect(lit && Boolean(heard("skyliners:tractor_beam_on", pressed, LOOK_EVERY)), "freighter: pressed, her pad lights, and her tractor beam with it");
  const down = waitForPlayer((pos) => pos[1] < by && pos[1] - ground < BEAM_DOWN_WITHIN, BEAM_RIDE_TICKS);
  expect(Boolean(down && heard("skyliners:tractor_beam_drop", pressed, 0)), "freighter: standing on the pad as it lights, the player is let down her beam to the ground",
    down ? down.join(",") : must("status").pos.join(","));
  expect(healthOf() === FULL_HEALTH, "freighter: let down her beam, the player is unhurt", `health ${healthOf()}`);
  const stepped = now();
  must(`server tp @p ${bx + 0.5 + BEAM_STEP_ASIDE} ${ground} ${bz + 0.5}; wait 10t; server tp @p ${bx + 0.5} ${ground} ${bz + 0.5}`);
  const up = waitOnPad("@p", pad, BEAM_RIDE_TICKS);
  expect(up && Boolean(heard("skyliners:tractor_beam_lift", stepped, 0)) && Boolean(heard("skyliners:tractor_beam_arrive", stepped, 0)),
    "freighter: stepping out of her beam and back in, the player is beamed up onto the pad", must("status").pos.join(","));
  must(`server tp @p ${bx + 0.5 + BEAM_STEP_ASIDE} ${ground} ${bz + 0.5}; wait 10t; server summon cow ${bx + 0.5} ${ground} ${bz + 0.5} {PersistenceRequired:1b}`);
  expect(waitOnPad("@e[type=cow,limit=1,sort=nearest]", pad, BEAM_RIDE_TICKS), "freighter: a cow under her beam is beamed up onto the pad");
  must(`server kill @e[type=cow]; server difficulty easy; server summon zombie ${bx + 0.5} ${ground} ${bz + 0.5} {NoAI:1b,PersistenceRequired:1b}; wait ${BEAM_WATCH_TICKS}t`);
  const zombie = entityAt("@e[type=zombie,limit=1,sort=nearest]");
  send("?server kill @e[type=zombie]; server difficulty peaceful");
  expect(Boolean(zombie) && zombie[1] - ground < BEAM_DOWN_WITHIN, "freighter: a zombie under her beam is never lifted", zombie ? zombie.join(",") : "gone");
  const off = now();
  must(`server tp @p ${bx + 0.5} ${ground} ${bz + 2.5} 180 -60; wait 10t; use ${bx},${by},${bz}; wait 5t`);
  const dark = must(`block ${bx},${by},${bz} side=server`).props?.lit !== "true" && must(`block ${pad.join(",")} side=server`).props?.lit !== "true";
  expect(dark && Boolean(heard("skyliners:tractor_beam_off", off, LOOK_EVERY)), "freighter: pressed from the ground, her beam goes dark, and her pad with it");
  creative();
}

// A freighter's tractor beam as she hovers high over the ground: pressed with the player on it, her pad lights her beam
// and the player sinks down it all the way to the ground, unhurt; and stepping out of the beam and back in, they are
// beamed all the way up onto the pad again.
function checkFreighterTractorBeamInFlight() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const height = head[1] + BEAM_HOVER;
  must(`skyliners:autopilot fly altitude=${height} speed=0`, BUILD_SECONDS);
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise" && report.altitude > height - FREIGHTER_HOLD_WITHIN, FREIGHTER_UP_TICKS);
  const last = up ?? freighterReport();
  expect(up, "freighter: she lifts off and hovers high over the ground", `${last?.phase} at ${last?.altitude}, set ${height}`);
  if (!up) {
    return;
  }
  must(`wait ${FREIGHTER_WATCH_TICKS}t`);
  const freighter = findShipFlying("skyliners:engine_grille");
  const [padRow] = must(`sable:find ${freighter} skyliners:beam_pad 1 side=server`).rows;
  const [beamRow] = must(`sable:find ${freighter} skyliners:tractor_beam 1 side=server`).rows;
  const pad = padRow.at;
  must(`sable:tp ${pad.join(",")}; fly off; wait 20t`);
  must(`server execute positioned ${pad[0] + 0.5} ${pad[1] + 1} ${pad[2] + 0.5} out_sub_level ${freighter} run tp @p ~ ~ ~; wait 10t`);
  survival();
  const pressed = now();
  must(`use ${pad.join(",")}; wait 5t`);
  expect(Boolean(heard("skyliners:tractor_beam_on", pressed, LOOK_EVERY)), "freighter: hovering, her pad pressed lights her tractor beam");
  const down = waitForPlayer((pos) => pos[1] - ground < BEAM_DOWN_WITHIN, BEAM_FLIGHT_RIDE_TICKS);
  expect(Boolean(down), "freighter: standing on her pad as it lights, the player sinks down her beam to the ground", must("status").pos.join(","));
  expect(healthOf() === FULL_HEALTH, "freighter: let down from high over the ground, the player is unhurt", `health ${healthOf()}`);
  const beamAt = beamRow.at;
  must(`server execute positioned ${beamAt[0] + 0.5} ${beamAt[1]} ${beamAt[2] + 0.5} out_sub_level ${freighter} run summon marker ~ ~ ~ {Tags:["beam"]}`);
  const [wx, , wz] = entityAt("@e[type=marker,tag=beam,limit=1]") ?? [0, 0, 0];
  must(`server kill @e[type=marker,tag=beam]; server tp @p ${wx + BEAM_STEP_ASIDE} ${ground} ${wz}; wait 10t; server tp @p ${wx} ${ground} ${wz}`);
  const stepped = now();
  const aboard = waitForPlayer((pos) => pos[1] > height - BEAM_HOVER / 2, BEAM_FLIGHT_RIDE_TICKS) && heard("skyliners:tractor_beam_arrive", stepped, BEAM_RIDE_TICKS);
  expect(Boolean(aboard), "freighter: stepping out of her beam and back in, the player is beamed all the way up onto her pad",
    `${must("status").pos.join(",")}, beam at ${wx},${wz}, lifted ${Boolean(heard("skyliners:tractor_beam_lift", stepped, 0))}`);
  creative();
  send("?skyliners:autopilot land");
}

// Tractor beams set up by hand: one hung under a ceiling, which a sheep under it rides up onto a beam pad set down by
// it, and which over lava lets nobody down from the pad; and one standing on a floor, which a player standing on it
// floats up, and which, switched off by a redstone pulse, lets them float down unhurt.
function checkTractorBeamByHand() {
  const [x, z] = [nextSite++ * SITE_APART, STAND_AWAY];
  creative();
  emptyHands();
  must(`server tp @p ${x + 0.5} ${ground} ${z - 4.5} 0 0; wait 40t`);
  must(`server fill ${x - 1} ${ground + BEAM_HAND_HIGH} ${z - 1} ${x + 1} ${ground + BEAM_HAND_HIGH} ${z + 1} stone`);
  must("server give @p skyliners:tractor_beam 2; server give @p skyliners:beam_pad 1; wait 5t; select 0");
  const hung = [x, ground + BEAM_HAND_HIGH - 1, z];
  must(`server tp @p ${x + 0.5} ${ground} ${z - 2.5} 0 -60; wait 10t`);
  send(`place skyliners:tractor_beam ${hung.join(",")}`);
  const hungState = must(`block ${hung.join(",")} side=server`);
  expect((hungState.id ?? "").includes("tractor_beam") && hungState.props?.mount === "ceiling", "tractor beam: put up under a ceiling, it hangs there", `${hungState.id} ${hungState.props?.mount}`);
  const pad = [x + BEAM_HAND_PAD_OUT, ground, z];
  must(`select 1; server tp @p ${pad[0] + 0.5} ${ground} ${z - 2.5} 0 40; wait 10t`);
  send(`place skyliners:beam_pad ${pad.join(",")}`);
  const lit = now();
  must(`server tp @p ${x + 0.5} ${ground} ${z - 2.5} 0 -60; wait 10t; use ${hung.join(",")}; wait 5t`);
  expect(must(`block ${hung.join(",")} side=server`).props?.lit === "true" && Boolean(heard("skyliners:tractor_beam_on", lit, LOOK_EVERY)),
    "tractor beam: used, it lights up, humming");
  must(`server summon sheep ${x + 0.5} ${ground} ${z + 0.5} {PersistenceRequired:1b}`);
  expect(waitOnPad("@e[type=sheep,limit=1,sort=nearest]", pad, BEAM_RIDE_TICKS), "tractor beam: a sheep under it is beamed up onto the pad set down by it");
  send("?server kill @e[type=sheep]");
  must(`server setblock ${x} ${ground - 1} ${z} lava; wait ${BEAM_LOOKS_AGAIN}t`);
  survival();
  const refused = now();
  must(`server tp @p ${pad[0] + 0.5} ${ground + 1} ${pad[2] + 0.5}; wait ${BEAM_WATCH_TICKS}t`);
  const kept = must("status").pos;
  expect(kept[1] > ground + 0.5 && Boolean(heard("skyliners:tractor_beam_refused", refused, 0)),
    "tractor beam: over lava, stepping onto its pad, the player is not let down, and the pad buzzes", kept.join(","));
  creative();
  must(`server setblock ${x} ${ground - 1} ${z} grass_block`);
  const lift = [x + BEAM_HAND_LIFT_OUT, ground, z];
  must(`select 0; server tp @p ${lift[0] + 0.5} ${ground} ${z - 2.5} 0 40; wait 10t`);
  send(`place skyliners:tractor_beam ${lift.join(",")}`);
  const standing = must(`block ${lift.join(",")} side=server`);
  expect(standing.props?.mount === "floor", "tractor beam: put up on a floor, it stands there, looking up", `${standing.id} ${standing.props?.mount}`);
  must(`use ${lift.join(",")}; wait 5t`);
  survival();
  must(`server tp @p ${lift[0] + 0.5} ${ground + 0.6} ${z + 0.5}`);
  const floated = waitForPlayer((pos) => pos[1] - ground >= BEAM_HAND_FLOATS, BEAM_HAND_FLOAT_TICKS);
  expect(Boolean(floated), "tractor beam: standing on one set on a floor, the player floats up its beam", must("status").pos.join(","));
  must(`server setblock ${lift[0] + 1} ${ground} ${z} redstone_block`);
  const landed = waitForPlayer((pos) => pos[1] - ground < BEAM_DOWN_WITHIN + 1, BEAM_HAND_FLOAT_TICKS * 2);
  expect(Boolean(landed) && healthOf() === FULL_HEALTH, "tractor beam: switched off by a redstone pulse, it lets the player float down unhurt",
    `${must("status").pos.join(",")}, health ${healthOf()}`);
  creative();
}

// A gravity switch's room weightless, a player standing in it a few blocks before the switch on its floor (at floor
// height, in the world) and a cookie dropped further in: pressed, the switch says so by its sound and its state, and
// her computer speaks; the player rises off the floor and floats, higher still while they hold jump and not falling
// after; the cookie floats too; pressed again, everyone comes back down gently, unhurt, falling slowly. Every position
// given is the world's, the switch's included, or, aboard her flying, her plot's, with where the player stands then
// found by a marker. Gives whether the player floated.
function checkWeightlessRoom(who, switchAt, facing, toWorld) {
  const [dx, dz] = STEPS[facing];
  const [sx, sy, sz] = switchAt;
  const floor = sy - 1;
  const stand = toWorld([sx + 0.5 + dx * ZERO_G_STAND, floor, sz + 0.5 + dz * ZERO_G_STAND]);
  const cookie = toWorld([sx + 0.5 + dx * ZERO_G_COOKIE, floor + 0.1, sz + 0.5 + dz * ZERO_G_COOKIE]);
  survival();
  emptyHands();
  send("?server kill @e[type=item,tag=zero_g]");
  must(`server tp @p ${stand.join(" ")}; wait 20t`);
  must(`server summon item ${cookie.join(" ")} {Tags:["zero_g"],PickupDelay:32767,Item:{id:"minecraft:cookie",count:1}}; wait 10t`);
  const standing = must("status").pos;
  const cookieFrom = entityAt("@e[type=item,tag=zero_g,limit=1]") ?? cookie;
  const off = now();
  must(`turn ${WAYS[facing][2] + 180} 20; wait 5t; use ${switchAt.join(",")}; wait 5t`);
  const weightless = must(`block ${switchAt.join(",")} side=server`).props?.weightless === "true";
  expect(weightless && Boolean(heard("skyliners:gravity_switch_off", off, LOOK_EVERY)), `${who}: pressed, the gravity switch turns gravity off, whirring down`);
  expect(Boolean(heard("skyliners:freighter_computer_zero_gravity", off, 3 * LOOK_EVERY)), `${who}: her computer says gravity is off`);
  const risen = waitForPlayer((pos) => pos[1] - standing[1] >= ZERO_G_RISEN, ZERO_G_RISE_TICKS);
  expect(Boolean(risen), `${who}: with gravity off, the player rises off the floor`, `${(must("status").pos[1] - standing[1]).toFixed(2)} up`);
  const hums = must("sounds playing match=skyliners:zero_gravity_hum").rows.length;
  expect(hums === 1, `${who}: a soft hum fills the weightless room, one hum and not one on another`, `${hums} playing`);
  const before = must("status").pos[1];
  must(`key press key.jump ${ZERO_G_JUMP_TICKS}t; wait ${ZERO_G_JUMP_TICKS + 5}t`);
  const jumped = must("status").pos[1];
  expect(jumped - before >= ZERO_G_JUMPED, `${who}: holding jump, the player floats higher`, `${(jumped - before).toFixed(2)} higher`);
  must(`wait ${ZERO_G_WATCH_TICKS}t`);
  const after = must("status").pos[1];
  expect(after - standing[1] >= ZERO_G_RISEN, `${who}: let go of jump, the player floats on, not falling back to the floor`,
    `${(after - standing[1]).toFixed(2)} up`);
  // The cookie drifts up slowly, slower still on a busy server: it is looked at again, a little later, a few times.
  let cookieNow = entityAt("@e[type=item,tag=zero_g,limit=1]");
  for (let look = 0; look < ZERO_G_COOKIE_LOOKS && (!cookieNow || cookieNow[1] - cookieFrom[1] < ZERO_G_RISEN); look++) {
    must(`wait ${ZERO_G_WATCH_TICKS}t`);
    cookieNow = entityAt("@e[type=item,tag=zero_g,limit=1]");
  }
  expect(Boolean(cookieNow) && cookieNow[1] - cookieFrom[1] >= ZERO_G_RISEN, `${who}: a dropped cookie floats up off the floor`,
    cookieNow ? `${(cookieNow[1] - cookieFrom[1]).toFixed(2)} up` : "gone");
  must(`server tp @p ${stand[0]} ${stand[1] + 1} ${stand[2]}; wait 5t`);
  const on = now();
  must(`turn ${WAYS[facing][2] + 180} 0; wait 5t; use ${switchAt.join(",")}; wait 5t`);
  const back = must(`block ${switchAt.join(",")} side=server`).props?.weightless === "false";
  expect(back && Boolean(heard("skyliners:gravity_switch_on", on, LOOK_EVERY)), `${who}: pressed again, gravity comes back on with a thunk`);
  const falling = JSON.stringify(send("server data get entity @p active_effects")[0]).includes("slow_falling");
  const down = waitForPlayer((pos) => pos[1] - standing[1] <= ZERO_G_DOWN_WITHIN, ZERO_G_DOWN_TICKS);
  expect(falling && Boolean(down) && healthOf() === FULL_HEALTH, `${who}: gravity back on, the player falls slowly back to the floor, unhurt`,
    `${falling ? "falling slowly" : "not falling slowly"}, ${(must("status").pos[1] - standing[1]).toFixed(2)} up, health ${healthOf()}`);
  send("?server kill @e[type=item,tag=zero_g]");
  creative();
  return Boolean(risen);
}

// A freighter's club has a gravity switch by its doorway and rubber ducks on its tables; the room goes weightless and
// back (see checkWeightlessRoom).
function checkFreighterZeroGravity() {
  ship("freighter");
  const [row] = must("find skyliners:gravity_switch 128 1").rows;
  const ducks = must("find skyliners:rubber_duck 128 8").rows.length;
  expect(Boolean(row) && ducks > 0, "freighter: she is built with a gravity switch in her club, rubber ducks on its tables", `${ducks} ducks`);
  if (row) {
    checkWeightlessRoom("freighter", row.at_abs, must(`block ${row.at_abs.join(",")}`).props?.facing ?? "north", (at) => at);
  }
}

// A gravity switch set by hand on the wall of a little stone room makes that room weightless and back, and leaves
// someone standing outside it as heavy as ever.
function checkGravitySwitchByHand() {
  const [x, z] = [nextSite++ * SITE_APART, STAND_AWAY];
  const out = ZERO_G_ROOM_OUT;
  creative();
  emptyHands();
  must(`server tp @p ${x + 0.5} ${ground + 1} ${z + 0.5}; wait 40t`);
  must(`server fill ${x - out - 1} ${ground} ${z - out - 1} ${x + out + 1} ${ground + ZERO_G_ROOM_ROWS + 1} ${z + out + 1} stone hollow`);
  must(`server tp @p ${x + 0.5} ${ground + 1} ${z + 0.5} 180 0; wait 10t; server give @p skyliners:gravity_switch 1; wait 5t; select 0`);
  const at = [x, ground + 2, z - out];
  send(`place skyliners:gravity_switch ${at.join(",")}`);
  const placed = must(`block ${at.join(",")} side=server`);
  expect((placed.id ?? "").includes("gravity_switch") && placed.props?.mount === "wall", "gravity switch: put up on a wall, it hangs there",
    `${placed.id} ${placed.props?.mount}`);
  if (!(placed.id ?? "").includes("gravity_switch")) {
    return;
  }
  const facing = placed.props?.facing ?? "south";
  const [dx, dz] = STEPS[facing];
  const stand = [at[0] + 0.5 + dx * ZERO_G_STAND, ground + 1, at[2] + 0.5 + dz * ZERO_G_STAND];
  checkWeightlessRoom("gravity switch by hand", at, facing, ([px, py, pz]) => [px, py, pz]);
  must(`server tp @p ${stand.join(" ")}; wait 5t; turn ${WAYS[facing][2] + 180} 0; use ${at.join(",")}; wait 5t`);
  survival();
  must(`server tp @p ${x + out + 3.5} ${ground} ${z + 0.5}; wait 40t`);
  const outside = must("status").pos[1];
  expect(outside - ground < ZERO_G_DOWN_WITHIN, "gravity switch: outside its room, the player keeps their weight", `${outside - ground} up`);
  creative();
  must(`server tp @p ${stand.join(" ")}; wait 5t; turn ${WAYS[facing][2] + 180} 0; use ${at.join(",")}; wait 5t`);
}

// A freighter hovering high over the ground: her club's gravity switch makes it weightless and back as it does on the
// ground, everyone in it floating with her (see checkWeightlessRoom), its positions among her blocks.
function checkFreighterZeroGravityInFlight() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const height = head[1] + ZERO_G_HOVER;
  must(`skyliners:autopilot fly altitude=${height} speed=0`, BUILD_SECONDS);
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise" && report.altitude > height - FREIGHTER_HOLD_WITHIN, FREIGHTER_UP_TICKS);
  expect(Boolean(up), "freighter: she lifts off and hovers", `${(up ?? freighterReport())?.phase}`);
  if (!up) {
    return;
  }
  must(`wait ${FREIGHTER_WATCH_TICKS}t`);
  const freighter = findShipFlying("skyliners:engine_grille");
  const [row] = must(`sable:find ${freighter} skyliners:gravity_switch 1 side=server`).rows;
  expect(Boolean(row), "freighter: her gravity switch is aboard her in flight");
  if (!row) {
    return;
  }
  const facing = must(`block ${row.at.join(",")} side=server`).props?.facing ?? "north";
  const [sx, sy, sz] = row.at;
  const [dx, dz] = STEPS[facing];
  must(`sable:tp ${sx + dx * ZERO_G_STAND},${sy - 1},${sz + dz * ZERO_G_STAND}; fly off; wait 20t`);
  const toWorld = ([px, py, pz]) => {
    must(`server execute positioned ${px} ${py} ${pz} out_sub_level ${freighter} run summon marker ~ ~ ~ {Tags:["zero_g_spot"]}`);
    const spot = entityAt("@e[type=marker,tag=zero_g_spot,limit=1]") ?? [px, py, pz];
    send("?server kill @e[type=marker,tag=zero_g_spot]");
    return spot;
  };
  checkWeightlessRoom("freighter in flight", row.at, facing, toWorld);
  send("?skyliners:autopilot land");
}

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
  const { middle, tips } = prongThrusters();
  for (const retro of tips.slice(0, 2)) {
    must(`server tp @p ${backToward(middle, retro, PRONG_LIT_BACK).join(" ")}; wait 20t`);
    const prong = must(`skyliners:dark radius=${PRONG_LIT_REACH} least=1`);
    expect(Number(prong.dark) === 0, "freighter: no spot down either of her prongs' tunnels is left unlit, out to their tips",
      `${prong.dark} unlit, first ${prong.first || "none"}`);
  }
}

// A freighter's passenger screens, used aboard her once she flies: one shows the view from her outside cameras, where
// pressing one once crashed the game reaching for a camera she did not have.
function checkFreighterScreens() {
  layDown("freighter");
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  const screens = must(`sable:find ${freighter} skyliners:passenger_screen ${SCREENS_TRIED} side=server`).rows;
  expect(screens.length > 0, "freighter: aboard her flying, she has passenger screens");
  emptyHands();
  // Each screen in turn until one is reached and pressed: one before a row of seats, as her lookout's nook has, is
  // stood in front of among the seats and may not be reached from there.
  let pressed;
  for (const screen of screens) {
    pressed = pressScreen(screen.at);
    if (pressed.click) {
      break;
    }
  }
  expect(Boolean(pressed?.click) && pressed.thud === 0 && Boolean(must("wait 40t; status").pos),
    "freighter: pressed, a screen shows her outside camera and the game runs on", `${pressed?.click ? "a click" : "no click"}, ${pressed?.thud ?? 0} thuds`);
  must("key left_shift 5t; wait 10t");
}

// Stands before a passenger screen aboard a flying ship, at a plot position, and presses it: whether it clicked, and how
// many thuds were heard.
function pressScreen([sx, sy, sz]) {
  const at = [sx, sy, sz].join(",");
  const [dx, dz] = STEPS[must(`block ${at}`).props?.facing ?? "north"];
  must(`sable:tp ${sx + dx * SCREEN_STAND},${sy - 1},${sz + dz * SCREEN_STAND}; wait 20t; face ${at}`);
  const since = now();
  send(`use ${at}`);
  return { click: heard("*ui.button.click*", since, LOOK_EVERY), thud: played("*note_block.bass*", since).length };
}

// The Oval Window and the Porthole, each set by hand into an opening in a stone wall as a player would: each goes in
// whole, its dome toward whoever set it, dims at a touch with its chime, and broken in survival comes out whole, giving
// back one of itself. A porthole set into a hole in a floor lies flat in it, and comes out as a porthole too.
function checkHandSetWindows() {
  const x0 = nextSite++ * SITE_APART;
  creative();
  must(`server tp @p ${x0} ${ground} ${-WINDOW_STAND_OFF}; wait ${CHUNKS_TICKS}t`);
  HAND_SET_WINDOWS.forEach(([id, wide, tall, pieces], i) => setIntoWall(id, x0 + i * WINDOW_WALLS_APART, wide, tall, pieces));
  layIntoFloor("porthole", x0 + HAND_SET_WINDOWS.length * WINDOW_WALLS_APART);
}

// How many blocks of a window, in a state if given, stand round the player.
function windowPieces(id, state = "") {
  return must(`find skyliners:${id}${state} ${WINDOW_REACH} 50`).rows.length;
}

// How many of a window's items lie on the ground round the player or are in their inventory.
function windowsGivenBack(id) {
  const lying = send(`?server execute if entity @e[type=item,distance=..${WINDOW_REACH},nbt={Item:{id:"skyliners:${id}"}}]`).at(-1).result ?? 0;
  const held = send(`?server clear @p skyliners:${id} 0`).at(-1).result ?? 0;
  return Number(lying) + Number(held);
}

// One window set by hand into an opening in a stone wall, a row of stone under it, the player in front of it to the
// north; whether it goes in whole, dims, and comes out whole.
function setIntoWall(id, x, wide, tall, pieces) {
  const sill = ground + 1;
  must(`server fill ${x - 2} ${ground} 0 ${x + wide + 1} ${sill + tall + 1} 0 stone; server fill ${x} ${sill} 0 ${x + wide - 1} ${sill + tall - 1} 0 air`);
  emptyHands();
  must(`server give @p skyliners:${id} 1; wait 5t; select 0; server tp @p ${x + wide / 2} ${sill + 1} ${-WINDOW_STAND_OFF} 0 35; wait 10t`);
  send(`place skyliners:${id} ${x},${sill},0`);
  must("wait 5t");
  const facing = must(`block ${x},${sill},0`).props?.facing;
  const name = id.replaceAll("_", " ");
  expect(windowPieces(id) === pieces && facing === "north", `${name}: set by hand into a wall, it goes in whole, its dome toward whoever set it`,
    `${windowPieces(id)} of ${pieces} pieces, facing ${facing}`);
  const used = now();
  must(`use ${x},${sill},0; wait 5t`);
  expect(windowPieces(id, "[dim=1]") === pieces && Boolean(heard("*note_block.chime*", used, LOOK_EVERY)), `${name}: touched, all of its glass dims a step with a chime`,
    `${windowPieces(id, "[dim=1]")} of ${pieces} pieces dimmed`);
  survival();
  emptyHands();
  must(`server tp @p ${x + 0.5} ${ground} ${-WINDOW_STAND_OFF} 0 20; wait 10t; mine ${x},${sill},0 timeout=10s; wait 20t`);
  const given = windowsGivenBack(id);
  expect(windowPieces(id) === 0 && given === 1, `${name}: broken in survival, it all comes out and gives back one ${name}`,
    `${windowPieces(id)} pieces left, ${given} given back`);
  creative();
}

// A porthole set by hand into a hole in a stone floor, the player over it; whether it lies flat in it, its dome up
// toward them, and comes out as a porthole.
function layIntoFloor(id, x) {
  const floor = ground + 3;
  must(`server fill ${x - 2} ${floor} -2 ${x + 2} ${floor} 2 stone; server fill ${x} ${floor} 0 ${x} ${floor} 0 air`);
  emptyHands();
  must(`server give @p skyliners:${id} 1; wait 5t; select 0; server tp @p ${x + 0.5} ${floor + 2} 1.5 180 70; wait 10t`);
  send(`place skyliners:${id} ${x},${floor},0`);
  must("wait 5t");
  const laid = must(`block ${x},${floor},0`);
  expect(laid.id === `skyliners:flat_${id}` && laid.props?.facing !== "down", "porthole: set by hand into a hole in a floor, it lies flat, its dome up",
    `${laid.id} facing ${laid.props?.facing ?? "up"}`);
  survival();
  emptyHands();
  must(`server tp @p ${x + 0.5} ${floor + 1} -1.5 0 50; wait 10t; mine ${x},${floor},0 timeout=10s; wait 20t`);
  const given = windowsGivenBack(id);
  expect(windowPieces(`flat_${id}`) === 0 && given === 1, "porthole: broken out of a floor, it gives back one porthole", `${given} given back`);
  creative();
}

// A freighter's portholes along her long passages: she is built with them down both sides of her, looking out each
// way, and each lets the sky's light into the passage inside it, which her skin kept dark there before.
function checkFreighterPortholes() {
  ship("freighter");
  const portholes = must(`find skyliners:porthole 128 ${FREIGHTER_PORTHOLES * 4}`).rows.map((row) => row.at_abs);
  const facings = portholes.map((at) => must(`block ${at.join(",")}`).props?.facing ?? "north");
  const ways = new Set(facings);
  expect(portholes.length >= FREIGHTER_PORTHOLES && ways.size >= 2, "freighter: she is built with portholes down both sides of her",
    `${portholes.length} portholes, looking ${[...ways].join(" and ")}`);
  const dark = portholes.filter((at, i) => skyLightInside(at, facings[i]) < PORTHOLE_DAYLIGHT);
  expect(portholes.length > 0 && dark.length === 0, "freighter: each porthole lets daylight into the passage inside it",
    dark.length ? `dark inside ${dark.map((at) => at.join(",")).join(" ")}` : "");
}

// How much of the sky's light reaches the block inside a window looking out the way given, as a block's light.
function skyLightInside(at, facing) {
  const [dx, dz] = STEPS[facing];
  return must(`block ${at[0] - dx},${at[1]},${at[2] - dz} light`).sky ?? 0;
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

// A round floor window set by hand takes the largest size that fits the face it goes into, in a floor and in a wall
// alike: the full size on a broad one, three across on a ledge three blocks wide and two on one two wide. Each opens and
// shuts at a touch of its glass and, broken, comes out whole, the stone put back and the window given back.
function checkFloorWindowSizes() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  for (let i = 0; i < SIZED_WINDOWS.length; i++) {
    const [wide, size, blocks] = SIZED_WINDOWS[i];
    const x = site.x + i * SIZED_FACES_APART;
    sizedWindow({ face: "floor", wide, size, blocks, at: [x, ground + 6, site.z], feet: [x, ground + 7, site.z] });
    const wall = [x, ground + 2 + (SIZED_WALL_ROWS >> 1), site.z + SIZED_FACES_APART];
    sizedWindow({ face: "wall", wide, size, blocks, at: wall, feet: [x, wall[1] - 1, wall[2] - SIZED_STAND] });
  }
}

// Sets a round floor window by hand into a face of stone so many blocks wide, a floor's or a wall's, at the block
// given, the player standing with their feet in the block given, on the floor or on a step before the wall, and checks
// the size it takes there, that it opens and shuts at a touch of its glass, and that, broken, it comes out whole.
function sizedWindow({ face, wide, size, blocks, at, feet }) {
  const [x, y, z] = at;
  const [low, high] = [Math.floor((wide - 1) / 2), Math.ceil((wide - 1) / 2)];
  const reach = SIZED_WALL_ROWS >> 1;
  const fill = face === "floor" ? `${x - low} ${y} ${z - reach} ${x + high} ${y} ${z + reach}` : `${x - low} ${y - reach} ${z} ${x + high} ${y + reach} ${z}`;
  const what = `floor window in a ${face} ${wide} wide`;
  const stand = `${feet[0] + 0.5} ${feet[1]} ${feet[2] + 0.5}`;
  creative();
  emptyHands();
  must(`server tp @p ${stand}; wait ${CHUNKS_TICKS}t; server fill ${fill} stone`);
  send(`?server setblock ${feet[0]} ${feet[1] - 1} ${feet[2]} stone`);
  must(`server give @p skyliners:floor_window 1; wait 5t; select 0; server tp @p ${stand}; wait 20t; use ${x},${y},${z}; wait 10t`);
  const laid = must(`find skyliners:floor_window ${SIZED_COUNT_REACH} 100`).rows.length;
  const taken = Number((must(`server data get block ${x} ${y} ${z} Size`).rows[0]?.line ?? "").split(" ").at(-1));
  const mount = must(`block ${x},${y},${z}`).props?.mount ?? "floor";
  expect(laid === blocks && taken === size && mount === face, `${what}: set by hand, it takes the size that fits, ${size} across`,
    `${laid} blocks, size ${taken}, in a ${mount}`);
  must(`select 8; use ${x},${y},${z}; wait ${IRIS_TICKS}t`);
  const opened = must(`block ${x},${y},${z}`).props?.open === "true";
  must(`use ${x},${y},${z}; wait ${IRIS_TICKS}t`);
  const shut = must(`block ${x},${y},${z}`).props?.open !== "true";
  expect(opened && shut, `${what}: touched, its glass opens, and touched again it shuts`);
  emptyHands();
  survival();
  must(`select 8; mine ${x},${y},${z} timeout=10s; wait 20t`);
  const left = must(`find skyliners:floor_window ${SIZED_COUNT_REACH} 100`).rows.length;
  const back = must(`block ${x},${y},${z}`).id;
  const given = windowsGivenBack("floor_window");
  expect(left === 0 && back.endsWith("stone") && given === 1, `${what}: broken, it all comes out, the stone put back and the window given back`,
    `${left} blocks left, ${back} where it was, ${given} given back`);
  creative();
  send(`?server kill @e[type=item,distance=..${SIZED_COUNT_REACH}]`);
}

// A round floor window laid into a floor of gold reaching the floor's very edge, and opened: seen from beside the edge,
// it shows the floor's own gold side there, as solid as the floor round it, not its grey well or the sky through a
// side left out.
function checkFloorWindowEdge() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  const [x, y, z] = [site.x, ground + 6, site.z];
  const edge = Math.floor(FULL_WINDOW_ACROSS / 2);
  creative();
  emptyHands();
  const stand = `${x + 0.5} ${y + SIZED_STAND} ${z + 0.5} 0 90`;
  must(`server tp @p ${stand}; wait ${CHUNKS_TICKS}t; server fill ${x - edge} ${y} ${z - edge - 2} ${x + edge} ${y} ${z + edge + 2} gold_block`);
  must(`server give @p skyliners:floor_window 1; wait 5t; select 0; server tp @p ${stand}; wait 20t; use ${x},${y},${z}; wait 10t; select 8; use ${x},${y},${z}; wait ${IRIS_TICKS}t`);
  const side = [x + edge + 1, y + 0.5, z + 0.5];
  must(`camera at ${side[0] + EDGE_CAMERA_OUT},${side[1]},${side[2]} look ${side.join(",")}; wait 20t`);
  const seen = must(`pixels edge=@${side.join(",")} r=${EDGE_MEASURE}`).edge ?? {};
  must("camera reset");
  expect(seen.r > seen.b * EDGE_GOLD_RED, "floor window: laid to a floor's edge and open, its edge shows the floor's own side, not its well",
    `red ${round(seen.r)}, green ${round(seen.g)}, blue ${round(seen.b)}`);
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
  const hidden = must("find skyliners:potted_palm 128 10").rows.map((row) => row.at_abs).filter((at) => !crownInTheOpen(at));
  expect(hidden.length === 0, "freighter: each of her beach's palms spreads its fronds in the open, through no wall and under no parasol",
    hidden.map((at) => at.join(",")).join("; "));
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
  must(`server tp @p ${px + 0.5 + dx * PANEL_FROM} ${py} ${pz + 0.5 + dz * PANEL_FROM}; wait 10t; use ${panel.at_abs.join(",")}; wait ${IRIS_TICKS}t`);
  expect(must(`block ${middle.join(",")}`).props?.open === "true", "freighter: pressed, her window's panel opens it");
  const below = [...Array(shaft + BELLY_BELOW).keys()].map((down) => must(`block ${middle[0]},${middle[1] - 1 - down},${middle[2]}`).id);
  expect(below.includes("skyliners:smoked_glass"), "freighter: down its shaft, her belly is glass", below.join(", "));
  must(`use ${panel.at_abs.join(",")}; wait ${IRIS_TICKS}t`);
  expect(must(`block ${middle.join(",")}`).props?.open === "false", "freighter: pressed again, it shuts");
}

// The middle of the block so many blocks back toward her stern from a block of a freighter, her bow lying along the axis
// and the way given, as bowOf finds them; on the same row, where someone standing there has their feet.
function backFrom(at, along, bow, back) {
  return at.map((value, axis) => (axis === 1 ? value : value + 0.5 - (axis === along ? bow * back : 0)));
}

// Whether an LED strip lies near a point, standing there.
function stripsNear(stand) {
  must(`server tp @p ${stand.join(" ")}; wait 10t`);
  return must(`find skyliners:led_strip ${TIP_STRIPS_NEAR} 1`).rows.length > 0;
}

// Which way a freighter's bow lies from her middle, whichever way she was laid down: the axis, 0 for x or 2 for z, and
// the sign along it, toward her cockpit, which stands out ahead of her more than to her side.
function bowOf(middle) {
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const along = Math.abs(head[0] - middle[0]) > Math.abs(head[2] - middle[2]) ? 0 : 2;
  return { along, bow: Math.sign(head[along] - middle[along]) };
}

// The engines in a freighter's prongs: those standing further ahead of her middle than her rooms reach.
function prongEngines(middle) {
  const { along, bow } = bowOf(middle);
  return must("find createpropulsion:creative_thruster 128 100").rows.map((row) => row.at_abs)
    .filter((at) => (at[along] - middle[along]) * bow > ROOMS_REACH);
}

// Whether a potted palm's fronds have room: air beside its crown, a row up, and no parasol beside it, whose canopy
// would hide its crown.
function crownInTheOpen([x, y, z]) {
  const beside = Object.values(STEPS).map(([dx, dz]) => [x + dx, y + 1, z + dz]);
  const round = [-1, 0, 1].flatMap((dx) => [-1, 0, 1].map((dz) => [x + dx, y, z + dz]));
  const [crown, ground] = [readBlocks(beside), readBlocks(round)];
  return crown.every((block) => block?.id?.endsWith("air")) && !ground.some((block) => block?.id === "skyliners:parasol");
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
  const [own, zeppelin] = [found("freighter_cover"), found("zeppelin_cover")];
  expect(own >= FREIGHTER_HULL_LEAST && zeppelin === 0, "freighter: her skin is her own Freighter Hull, none of it the zeppelin's cover",
    `${own} of hers, ${zeppelin} of the zeppelin's`);
  const [x0, z0] = [site.x - Math.floor((FITTINGS_PUT_UP.length * STAND_APART) / 2), site.z + STAND_AWAY];
  const x1 = x0 + FITTINGS_PUT_UP.length * STAND_APART;
  must(`server fill ${x0 - 1} ${ground} ${z0 + 3} ${x1} ${ground + STAND_HIGH + 1} ${z0 + 3} stone`);
  must(`server fill ${x0 - 1} ${ground + STAND_HIGH} ${z0} ${x1} ${ground + STAND_HIGH} ${z0 + 2} stone`);
  FITTINGS_PUT_UP.forEach((id, i) => putUpEveryWay(id, x0 + i * STAND_APART, z0));
}

// A freighter's outside meets her hull: every fitting, light strip and navigation light, and her laser cannon, stands
// against a whole block of her, face to face. Her skin is cut to her curve, and each piece used to stand on the edge of
// its block over that cut, with a gap all round it under it.
function checkFreighterExteriorFlush() {
  ship("freighter");
  const pieces = RESTING_PIECES.flatMap((id) => must(`find skyliners:${id} 128 ${RESTING_MOST}`).rows.map((row) => row.at_abs));
  const states = readBlocks(pieces);
  const held = readBlocks(pieces.map((at, i) => restsAgainst(at, states[i])));
  const off = pieces.flatMap((at, i) => (wholeBlock(held[i]) ? [] : [`${states[i].id} at ${at.join(",")}`]));
  expect(pieces.length > 0 && off.length === 0, "freighter: every piece of her outside meets her hull face to face, no gap under it",
    `${off.length} of ${pieces.length} stand off it${off.length ? `, as ${off.slice(0, 4).join("; ")}` : ""}`);
}

// The server's blocks at the positions given, read a script at a time.
function readBlocks(positions) {
  const replies = [];
  for (let from = 0; from < positions.length; from += READS_AT_ONCE) {
    const reads = positions.slice(from, from + READS_AT_ONCE).map((at) => `block ${at.join(",")} side=server`);
    replies.push(...send(reads.join("; ")));
  }
  return replies;
}

// The block a piece of a ship's outside stands against: a light the way away from where it shines, a fitting under it
// on a floor, over it under a ceiling, and behind it on a wall.
function restsAgainst(at, piece) {
  const props = piece.props ?? {};
  const mount = piece.id?.endsWith("nav_light") ? "light" : props.mount ?? "floor";
  const away = { floor: [0, -1, 0], ceiling: [0, 1, 0], light: POINTING[props.facing ?? "up"].map((d) => -d) }[mount]
    ?? POINTING[props.facing ?? "north"].map((d) => -d);
  return at.map((value, axis) => value + away[axis]);
}

// Whether a block is solid and whole, neither air nor skin cut to a curve: skin's shape 0, in its two parts, is whole.
function wholeBlock(reply) {
  const props = reply?.props ?? {};
  return Boolean(reply?.id) && !reply.id.endsWith("air") && Number(props.shape_high ?? 0) === 0 && Number(props.shape_low ?? 0) === 0;
}

// How many blocks of the mod's of an id stand in the world round the player: Blockpilot gives the total only when there
// are more than it lists.
function found(id) {
  const reply = must(`find skyliners:${id} 128 1`);
  return reply.count ?? reply.rows.length;
}

// One fitting put up by hand as a player would, each way it goes up: standing on the ground, on the stand's wall and
// under its overhang; whether each took the shape for where it went.
function putUpEveryWay(id, x, z, mounts = ["floor", "wall", "ceiling"]) {
  emptyHands();
  must(`server give @p skyliners:${id} 3; wait 5t; select 0`);
  const spots = [["floor", x, ground, z - 3], ["wall", x, ground + 2, z + 2], ["ceiling", x, ground + STAND_HIGH - 1, z]]
    .filter(([mount]) => mounts.includes(mount));
  for (const [mount, sx, sy, sz] of spots) {
    must(`server tp @p ${sx + 0.5} ${ground} ${sz - 2.5} 0 20; wait 10t`);
    send(`place skyliners:${id} ${sx},${sy},${sz}`);
    const reply = must(`block ${sx},${sy},${sz} side=server`);
    const put = reply.id ?? "";
    const as = reply.props?.mount ?? "floor";
    expect(put.includes(id) && as === mount, `${id.replaceAll("_", " ")}: put up on a ${mount}, it takes its ${mount} shape`, `${put} mount=${as}`);
  }
}

// A furnished zeppelin's upper deck has a rail along its front edge out to her hull: walking forward off the deck past
// the end of the wall with the fleet's paintings, the player is stopped at the edge. They stepped off its front corners
// and fell four blocks into the dark among her girders.
function checkZeppelinDeckFront() {
  ship("furnished_zeppelin");
  const wall = must("find skyliners:cabin_partition 128 400").rows.map((row) => row.at_abs);
  const [poster] = must("find skyliners:space_poster 128 1").rows;
  expect(wall.length > 0 && poster, "zeppelin: her upper deck's front has its wall with the fleet's paintings");
  if (wall.length === 0 || !poster) {
    return;
  }
  const across = wall.every(([, , z]) => z === wall[0][2]) ? 0 : 2;
  const along = 2 - across;
  const plane = wall[0][along];
  const aft = Math.sign(poster.at_abs[along] - plane);
  const floor = Math.min(...wall.map(([, y]) => y));
  const end = Math.max(...wall.map((at) => at[across]));
  const stand = [0, floor + 1, 0];
  stand[across] = end + DECK_CORNER_OUT + 0.5;
  stand[along] = plane + 0.5 + aft * DECK_EDGE_BACK;
  const yaw = along === 2 ? (aft > 0 ? 180 : 0) : aft > 0 ? 90 : -90;
  const room = must(`block ${stand.map(Math.floor).join(",")}`).id;
  expect(room === "air", "zeppelin: past the end of the wall with the paintings her upper deck goes on", `${room} stands there`);
  survival();
  must(`server tp @p ${stand.join(" ")} ${yaw} 0; wait 20t; move forward ${DECK_WALK_TICKS}t; wait 10t`);
  const there = must("status").pos;
  creative();
  expect(there[1] >= floor + 1 - DECK_DROP_SLACK, "zeppelin: walking off her upper deck's front corner, a rail stops the player at the edge",
    `at y ${round(there[1])}, the deck's floor under y ${floor + 1}`);
}

// A furnished zeppelin's secret techno room, kept dark for its lights: nothing spawns in it, so no monster waits there
// for whoever opens the hatch. Its dance floor and walls let monsters spawn on them, as no other block of hers does.
function checkZeppelinClubSpawns() {
  const site = ship("furnished_zeppelin");
  const floor = [CLUB_LOOK_ALONG / 2, CLUB_LOOK_ALONG, 3 * CLUB_LOOK_ALONG / 2].map((along) => {
    must(`server tp @p ${site.x} ${ground + CLUB_LOOK_UP} ${site.z - along}; wait ${CHUNKS_TICKS}t`);
    return must("find skyliners:dance_floor 128 1").rows[0];
  }).find(Boolean);
  expect(floor, "zeppelin: she is built with her secret techno room");
  if (!floor) {
    return;
  }
  const [x, y, z] = floor.at_abs;
  must(`server tp @p ${x + 0.5} ${y + 1} ${z + 0.5}; wait 10t`);
  const room = must(`skyliners:dark radius=${CLUB_LOOK_RADIUS}`);
  expect(Number(room.spawnable) === 0, "zeppelin: no monster can spawn in her secret techno room, dark as it is",
    `${room.spawnable} spots, ${room.unlit} unlit, first ${room.first || "none"}`);
}

// Fittings hung from a ceiling or fixed to a wall, put up by hand: set down on a bare floor each is refused, where it
// stood in the air, a cord hanging from nothing or a plaque floating; on its own mount it goes up.
function checkMountedOnly() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  creative();
  must(`server tp @p ${site.x} ${ground} ${site.z}; wait ${CHUNKS_TICKS}t`);
  const [x0, z0] = [site.x, site.z + STAND_AWAY];
  const x1 = x0 + MOUNTED_ONLY.length * STAND_APART;
  must(`server fill ${x0 - 1} ${ground} ${z0 + 3} ${x1} ${ground + STAND_HIGH + 1} ${z0 + 3} stone`);
  must(`server fill ${x0 - 1} ${ground + STAND_HIGH} ${z0} ${x1} ${ground + STAND_HIGH} ${z0 + 2} stone`);
  MOUNTED_ONLY.forEach(([id, mount], i) => putUpOnly(id, x0 + i * STAND_APART, z0, mount));
}

// Puts a fitting up by hand on a bare floor, where it is to be refused, and on its own mount, where it is to go up, at
// the spots before a stand as putUpEveryWay uses them.
function putUpOnly(id, x, z, mount) {
  emptyHands();
  must(`server give @p skyliners:${id} 3; wait 5t; select 0`);
  const spots = { floor: [x, ground, z - 3], wall: [x, ground + 2, z + 2], ceiling: [x, ground + STAND_HIGH - 1, z] };
  const putAt = (at) => {
    must(`server tp @p ${at[0] + 0.5} ${ground} ${at[2] - 2.5} 0 20; wait 10t`);
    send(`place skyliners:${id} ${at.join(",")}`);
    return must(`block ${at.join(",")} side=server`).id ?? "";
  };
  const name = id.replaceAll("_", " ");
  const floor = putAt(spots.floor);
  expect(!floor.includes(id), `${name}: set down on a bare floor, it is refused`, floor);
  const mounted = putAt(spots[mount]);
  expect(mounted.includes(id), `${name}: put up on a ${mount}, it goes up`, mounted);
}

// An orrery hung up by hand in a room built up in the open, a ceiling over it and a glass floor under it, draws no
// planetarium's sky round it: the ground far under the floor passed for her dome's ring of ceiling, and a cone of night
// sky filled the room. Hung in a rocket's nose, it draws her planetarium's sky over her cockpit still.
function checkOrrerySky() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  creative();
  must(`server tp @p ${site.x} ${ground} ${site.z + ORRERY_LOOK_FROM}; wait ${CHUNKS_TICKS}t`);
  const ceiling = ground + ORRERY_CEILING_UP;
  const floor = ceiling - 1 - ORRERY_FLOOR_UNDER;
  must(`server fill ${site.x - 1} ${ceiling} ${site.z - 1} ${site.x + 1} ${ceiling} ${site.z + 1} stone`);
  must(`server fill ${site.x - 1} ${floor} ${site.z - 1} ${site.x + 1} ${floor} ${site.z + ORRERY_LOOK_FROM} glass`);
  must(`server setblock ${site.x} ${ceiling - 1} ${site.z} skyliners:orrery`);
  const byHand = skyOf([site.x, ceiling - 1, site.z], [0, -1, ORRERY_LOOK_FROM]);
  expect(byHand?.seen && !byHand.sky, "orrery: hung up by hand in a room built up in the open, it draws no planetarium's sky round it",
    `seen ${byHand?.seen}, sky ${byHand?.sky}`);
  const rocket = ship("rocket");
  must(`server tp @p ${rocket.x} ${ground + ORRERY_ROCKET_LOOK_UP} ${rocket.z - ORRERY_ROCKET_LOOK_BACK}; wait ${CHUNKS_TICKS}t`);
  const [orrery] = must("find skyliners:orrery 64 1").rows;
  const inNose = orrery && skyOf(orrery.at_abs, [0, -ORRERY_COCKPIT_DOWN, 0]);
  expect(inNose?.sky, "orrery: in a rocket's nose, it draws her planetarium's sky", `seen ${inNose?.seen}, sky ${inNose?.sky}`);
}

// Whether the orrery nearest the player draws its planetarium's sky, looked at from so far off its block: the player
// stood there and the camera there, looking at it a while, as only an orrery drawn has its dome found.
function skyOf([x, y, z], [dx, dy, dz]) {
  const eye = [x + 0.5 + dx, y + dy, z + 0.5 + dz];
  must(`server tp @p ${eye.join(" ")}; camera at ${eye.join(",")} look ${x + 0.5},${y + 0.5},${z + 0.5}; wait ${ORRERY_LOOK_TICKS}t`);
  const read = must("skyliners:planetarium");
  must("camera reset");
  return read;
}

// A basketball hoop on its stand, set down on a floor: walking into it from the ring's side in survival, the player is
// stopped by its stand. Its base, pole and backboard each rose less than a step, so the player climbed them like
// stairs, over its top and down its far side.
function checkHoopStand() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  creative();
  must(`server tp @p ${site.x} ${ground} ${site.z}; wait ${CHUNKS_TICKS}t`);
  must(`server setblock ${site.x} ${ground} ${site.z} skyliners:hoop[mount=floor,facing=north]`);
  survival();
  must(`server tp @p ${site.x + 0.5} ${ground} ${site.z + HOOP_RUN_UP + 0.5} 180 0; wait 20t`);
  const path = must(`sample status every 1t while move forward ${HOOP_WALK_TICKS}t`).rows.filter((row) => row.pos);
  creative();
  const rose = Math.max(...path.map((row) => row.pos[1])) - ground;
  expect(rose <= HOOP_STEP_MOST, "hoop: walking into its stand on a floor, the player is stopped, not stepped up it", `rose ${round(rose)} blocks`);
}

// A freighter's galleries: the fleet's models, the freighter's own among them, along the tube out to her cockpit, and
// space posters on the wall of the tunnel down her port prong, both farther out from her middle than any room's wall
// (her lounge's shelves have models too, and the walls across her beam the fleet's paintings).
function checkFreighterGallery() {
  ship("freighter");
  const [{ at_abs: middle }] = must("find skyliners:laser_cannon 128 1").rows;
  const outside = (id) => must(`find skyliners:${id} 128 200`).rows.filter(({ at_abs: at }) => Math.hypot(at[0] - middle[0], at[2] - middle[2]) > ROOMS_REACH);
  const models = ["model_airliner", "model_zeppelin", "model_rocket", "model_freighter"].filter((id) => outside(id).length > 0);
  expect(models.length === 4, "freighter: the fleet's four models stand along the tube out to her cockpit", models.join(", "));
  const posters = outside("space_poster").length;
  expect(posters >= GALLERY_POSTER_BLOCKS, "freighter: space posters hang along the tunnel down her port prong", `${posters} blocks of posters`);
  const ahead = prongEngines(middle);
  const over = readBlocks(ahead.map(([x, y, z]) => [x, y + 1, z]));
  const standing = ahead.filter((at, i) => over[i]?.id?.endsWith("air"));
  expect(ahead.length > 0 && standing.length === 0, "freighter: walking down her prongs' tunnels, no engine stands out in either, each set into her skin",
    `${standing.length} of ${ahead.length} stand in the open${standing.length ? `, as at ${standing[0].join(",")}` : ""}`);
}

// A freighter's middle and her prongs' tips: the laser cannon on her axis, and the thrusters in the tips, her retros and
// nose jets, the furthest of all her thrusters from her middle, the furthest first.
function prongThrusters() {
  const [{ at_abs: middle }] = must("find skyliners:laser_cannon 128 1").rows;
  const reach = (at) => Math.hypot(at[0] - middle[0], at[2] - middle[2]);
  const thrusters = must("find createpropulsion:creative_thruster 128 100").rows.map((row) => row.at_abs).sort((a, b) => reach(b) - reach(a));
  return { middle, tips: thrusters.slice(0, PRONG_THRUSTERS) };
}

// A point so many blocks back from a block toward a freighter's middle, down the length of her prong, at the block's
// floor: where someone stands in the tunnel looking out along it to the block.
function backToward(middle, at, blocks) {
  const [dx, dz] = [middle[0] - at[0], middle[2] - at[2]];
  const along = Math.abs(dz) >= Math.abs(dx) ? [0, Math.sign(dz)] : [Math.sign(dx), 0];
  return [at[0] + 0.5 + along[0] * blocks, at[1], at[2] + 0.5 + along[1] * blocks];
}

// A freighter's thrusters in her prongs' tips, her retros and her nose jets, boxed in behind her engine bays: aimed at
// from down the tunnel, the crosshair rests on the bay, never on a thruster, and a survival player's pickaxe at what it
// rests on breaks nothing. Her nose jets stood bare on the tunnels' floors before, creative blocks there for the taking.
function checkFreighterEngineBays() {
  ship("freighter");
  const { middle, tips } = prongThrusters();
  expect(tips.length === PRONG_THRUSTERS, "freighter: she has her retros and nose jets in her prongs' tips", `${tips.length} found`);
  for (const at of tips) {
    must(`server tp @p ${backToward(middle, at, AIM_BACK).join(" ")}; wait 10t; face ${at.join(",")}; wait 5t`);
    const look = must("status");
    expect(Boolean(look.aim) && !look.aim.includes("thruster"), `freighter: from down her prong's tunnel, the thruster at ${at.join(",")} is out of reach`,
      `the crosshair rests on ${look.aim ?? "nothing"}`);
    if (!look.aim_at) {
      continue;
    }
    const hit = look.aim_at.map(Math.floor).join(",");
    survival();
    send(`?mine ${hit} timeout=3s`);
    creative();
    expect(must(`block ${hit} side=server`).id === look.aim, "freighter: a pickaxe at her engine bay before it breaks nothing", `${look.aim} at ${hit}`);
  }
}

// A freighter's smugglers' hold, as a child would find it: down her starboard prong the tunnel ends at a wall whose one
// brass rivet chimes now and then as you stand near it; knocked on, the panel beside it sounds hollow but stays shut;
// the rivet pressed, the panel swings open with a hiss onto a den with a chest of loot; it shuts again by itself once
// nobody stands in its doorway; and from inside it is pushed open by hand, so nobody is ever shut in.
function checkFreighterSmugglerHold() {
  ship("freighter");
  const [rivet] = must("find skyliners:loose_rivet 128 1").rows;
  const panels = must("find skyliners:secret_panel 128 2").rows.map((row) => row.at_abs);
  expect(Boolean(rivet) && panels.length === 2, "freighter: a wall across her starboard prong has a secret panel and a loose rivet",
    `${rivet ? "a rivet" : "no rivet"}, ${panels.length} blocks of panel`);
  if (!rivet || panels.length < 2) {
    return;
  }
  const lower = panels.reduce((low, at) => (at[1] < low[1] ? at : low));
  const at = lower.join(",");
  const open = () => must(`block ${at} side=server`).props?.open === "true";
  const [dx, dz] = STEPS[must(`block ${at}`).props?.facing ?? "north"];
  emptyHands();
  const near = now();
  must(`server tp @p ${lower[0] + 0.5 - dx * PANEL_STAND} ${lower[1]} ${lower[2] + 0.5 - dz * PANEL_STAND}; wait 10t; face ${rivet.at_abs.join(",")}`);
  expect(Boolean(heard("*amethyst_block.chime*", near, CHIME_WITHIN)), "freighter: standing near, her loose rivet chimes, a hint a child can hear");
  const knocked = now();
  must(`use ${at}; wait 10t`);
  expect(Boolean(heard("*note_block.basedrum*", knocked, LOOK_EVERY)) && !open(), "freighter: knocked on, the panel sounds hollow and stays shut");
  const pressed = now();
  must(`use ${rivet.at_abs.join(",")}; wait 10t`);
  expect(open() && Boolean(heard("*fire.extinguish*", pressed, LOOK_EVERY)), "freighter: her loose rivet pressed, the secret panel swings open with a hiss");
  const [chest] = must("find minecraft:chest 16 1 side=server").rows;
  const loot = chest ? must(`server data get block ${chest.at_abs.join(" ")} LootTable`).rows[0]?.line ?? "" : "";
  expect(loot.includes("smuggler_stash"), "freighter: behind it, her smugglers' den has a chest of loot", loot || "no chest");
  must(`wait ${PANEL_SHUTS}t`);
  expect(!open(), "freighter: with nobody in its doorway, the panel shuts again by itself");
  must(`server tp @p ${lower[0] + 0.5 + dx * DEN_STAND} ${lower[1]} ${lower[2] + 0.5 + dz * DEN_STAND}; wait 10t; use ${at}; wait 10t`);
  expect(open(), "freighter: from inside her den, the panel is pushed open by hand");
}

// The secret panel and the loose rivet set up by hand away from any ship, as a player makes a secret door: the panel
// stands in a gap in a stone wall and the rivet goes on the wall beside it; pressed, the rivet swings the panel open,
// and pressed again shuts it. The rivet goes on a floor and under a ceiling too, a rivet's head on each.
function checkSecretPanelByHand() {
  const x = nextSite++ * SITE_APART;
  creative();
  must(`server tp @p ${x + 0.5} ${ground} -3.5 0 10; wait ${CHUNKS_TICKS}t`);
  must(`server fill ${x - 2} ${ground} 0 ${x + 2} ${ground + 2} 0 stone; server fill ${x} ${ground} 0 ${x} ${ground + 1} 0 air; server setblock ${x - 3} ${ground + 2} -2 stone`);
  emptyHands();
  must("server give @p skyliners:secret_panel 1; server give @p skyliners:loose_rivet 3; wait 5t; select 0");
  send(`place skyliners:secret_panel ${x},${ground},0`);
  must("wait 5t; select 1");
  send(`place skyliners:loose_rivet ${x + 1},${ground + 1},-1`);
  send(`place skyliners:loose_rivet ${x + 3},${ground},-2`);
  send(`place skyliners:loose_rivet ${x - 3},${ground + 1},-2`);
  const faces = [[x + 1, ground + 1, -1], [x + 3, ground, -2], [x - 3, ground + 1, -2]].map((spot) => must(`block ${spot.join(",")} side=server`).props?.face);
  expect(faces.join(" ") === "wall floor ceiling", "loose rivet: put up by hand, it goes on a wall, a floor and a ceiling", faces.join(", "));
  const panel = () => must(`block ${x},${ground},0 side=server`);
  expect(panel().id === "skyliners:secret_panel", "secret panel: set by hand into a gap in a wall, it stands there", panel().id);
  must(`use ${x + 1},${ground + 1},-1; wait 10t`);
  expect(panel().props?.open === "true", "secret panel: the rivet beside it pressed, it swings open");
  must(`wait 30t; use ${x + 1},${ground + 1},-1; wait 10t`);
  expect(panel().props?.open === "false", "secret panel: the rivet pressed again, it shuts");
  // Opened once more, and pushed shut by hand by whoever stands in its doorway: it would shut them inside its solid
  // blocks, hurting them, so it waits for them with a chime.
  must(`wait 30t; use ${x + 1},${ground + 1},-1; wait 10t`);
  survival();
  must(`server tp @p ${x + 0.5} ${ground} 0.5 180 0; wait 5t`);
  const pushed = now();
  must(`use ${x},${ground + 1},0; wait 10t`);
  const chimed = heard("minecraft:block.note_block.bell", pushed, LOOK_EVERY);
  expect(panel().props?.open === "true" && healthOf() === FULL_HEALTH && Boolean(chimed),
    "secret panel: pushed shut from inside its doorway, it waits with a chime and shuts nobody in",
    `${panel().props?.open === "true" ? "open" : "shut"}, health ${healthOf()}, ${chimed ? "chimed" : "no chime"}`);
  creative();
}

// A freighter's port prong a lookout: a seat faces a porthole down its tunnel, and at its tip, past her nose jet's
// housing, a row of seats faces a screen of her outside cameras. Her port retro is the one further from her cockpit's
// control head, out on her starboard side, and what is nearer her starboard retro, her cockpit among it, is left out.
function checkFreighterLookout() {
  ship("freighter");
  const { middle, tips } = prongThrusters();
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const away = (from, at) => Math.hypot(at[0] - from[0], at[2] - from[2]);
  const [starboard, port] = tips.slice(0, 2).sort((a, b) => away(head, a) - away(head, b));
  const inPort = (at) => away(middle, at) > ROOMS_REACH && away(port, at) < away(starboard, at);
  const seats = must("find skyliners:business_seat 128 300").rows.map((row) => row.at_abs).filter(inPort);
  const screens = must("find skyliners:passenger_screen 128 50").rows.map((row) => row.at_abs).filter(inPort);
  expect(seats.length >= LOOKOUT_SEAT_BLOCKS && screens.length >= 1, "freighter: her port prong is a lookout, seats by its portholes and a nook at its tip facing a screen",
    `${seats.length} blocks of seats, ${screens.length} screens`);
}

// Steel girders put down by hand next to each other, as a player starts a frame: the first, put down on the ground,
// stands up on its footing as a pillar; one put on top runs on up as one column with it; one put against the column's
// side lies out from it, the two bolted together there; and the one on top broken again, the others let go of it.
// Girders used to overlap and clash where they met, and one on the ground read as a beam lying on its side.
function checkGirdersJoin() {
  const [x, z] = [nextSite++ * SITE_APART, STAND_AWAY];
  creative();
  emptyHands();
  must("server give @p skyliners:steel_girder 3; wait 5t; select 0");
  must(`server tp @p ${x + 0.5} ${ground} ${z - 2.5} 0 20; wait ${CHUNKS_TICKS}t`);
  send(`place skyliners:steel_girder ${x},${ground},${z}`);
  send(`place skyliners:steel_girder ${x},${ground + 1},${z}`);
  must(`server tp @p ${x + 2.5} ${ground} ${z - 2.5} 30 20; wait 10t`);
  send(`place skyliners:steel_girder ${x + 1},${ground + 1},${z}`);
  const [foot, top, beam] = [[x, ground, z], [x, ground + 1, z], [x + 1, ground + 1, z]].map((at) => must(`block ${at.join(",")} side=server`));
  // Blockpilot leaves a property at its default off: a girder stands up and joins nothing unless it says.
  const joint = (girder, way) => girder.props?.[way] ?? "none";
  const axis = (girder) => girder.props?.axis ?? "y";
  const girders = [foot, top, beam].filter((girder) => girder.id === "skyliners:steel_girder").length;
  expect(girders === 3 && axis(foot) === "y" && joint(foot, "down") === "fixed", "steel girder: put down on the ground, it stands up on its footing",
    `${girders} of 3 put down, the first along ${axis(foot)}, its foot ${joint(foot, "down")}`);
  expect(axis(top) === "y" && joint(foot, "up") === "girder" && joint(top, "down") === "girder", "steel girder: one put on top of it runs on up as one column",
    `along ${axis(top)}, joined ${joint(foot, "up")} and ${joint(top, "down")}`);
  expect(axis(beam) === "x" && joint(beam, "west") === "girder" && joint(top, "east") === "girder", "steel girder: one put against the column's side lies out from it, bolted to it",
    `along ${axis(beam)}, its end ${joint(beam, "west")}, the column's side ${joint(top, "east")}`);
  // The top one broken again, nothing beside it is left joined to the air.
  must(`server tp @p ${x - 1.5} ${ground} ${z - 2.5} -30 20; wait 10t`);
  send(`mine ${x},${ground + 1},${z}`);
  const [bare, loose] = [[x, ground, z], [x + 1, ground + 1, z]].map((at) => must(`block ${at.join(",")} side=server`));
  expect(joint(bare, "up") === "none" && joint(loose, "west") === "none", "steel girder: the one on top broken, neither the column under it nor the beam beside it stays joined to it",
    `the column's top ${joint(bare, "up")}, the beam's end ${joint(loose, "west")}`);
}

// The beach's tiki torch and the floor window's panel, put up by hand on the ground and on a wall, each take a shape of
// their own there: a torch standing tall or held out on a bracket, a panel on its post or flat in its rim.
function checkBeachOnWalls() {
  const [x0, z0] = [nextSite++ * SITE_APART, STAND_AWAY];
  creative();
  must(`server tp @p ${x0} ${ground} ${z0 - 3}; wait ${CHUNKS_TICKS}t`);
  must(`server fill ${x0 - 1} ${ground} ${z0 + 3} ${x0 + BEACH_ON_WALLS.length * STAND_APART} ${ground + STAND_HIGH} ${z0 + 3} stone`);
  BEACH_ON_WALLS.forEach((id, i) => putUpEveryWay(id, x0 + i * STAND_APART, z0, ["floor", "wall"]));
}

// A freighter's bowling lane down her starboard prong, played as a player would: a ball taken from the ball return at
// its head, bowled from the middle of the lane straight at the rack of ten, knocks pins down with a clatter, the head
// pin among them; the ball goes back down the return by itself; and a few seconds later the pinsetter has stood every
// pin up again.
function checkFreighterBowling() {
  ship("freighter");
  const [ballReturn] = must("find skyliners:ball_return 128 1").rows;
  const pinsAt = () => must(`find skyliners:bowling_pin 128 ${BOWLING_PINS + 5} side=server`).rows;
  const pins = pinsAt();
  expect(Boolean(ballReturn) && pins.length === BOWLING_PINS, "freighter: her starboard prong has a bowling lane, a rack of ten pins and a ball return",
    `${pins.length} pins, ${ballReturn ? "a" : "no"} ball return`);
  if (!ballReturn || pins.length === 0) {
    return;
  }
  const [rx, ry, rz] = ballReturn.at_abs;
  const near = (row) => Math.hypot(row.at_abs[0] - rx, row.at_abs[2] - rz);
  const head = pins.reduce((nearest, row) => (near(row) < near(nearest) ? row : nearest));
  const { facing, shifted } = must(`block ${head.at_abs.join(",")} side=server`).props;
  const [tx, tz] = STEPS[facing];
  const [sx, sz] = STEPS[COUNTER_CLOCKWISE[facing]];
  const shift = shifted === "true" ? 0.5 : 0;
  const [hx, hz] = [head.at_abs[0] + 0.5 + sx * shift, head.at_abs[2] + 0.5 + sz * shift];
  const [fx, fz] = STEPS[must(`block ${rx},${ry},${rz}`).props.facing];
  emptyHands();
  must(`server tp @p ${rx + 0.5 + fx * 2} ${ry + 0.1} ${rz + 0.5 + fz * 2}; wait 10t`);
  const took = now();
  must(`use ${rx},${ry},${rz}; wait 5t`);
  const held = must("inv").rows.some((row) => row.item === "skyliners:bowling_ball");
  expect(held && Boolean(heard("skyliners:bowling_roll", took, LOOK_EVERY)), "freighter: used, her ball return hands over a bowling ball with a rumble");
  const yaw = Math.round((Math.atan2(tx, -tz) * 180) / Math.PI);
  // Written with decimals: a whole number in a command is taken as the middle of its block, half a block off the line.
  must(`server tp @p ${(hx + tx * BOWL_FROM).toFixed(2)} ${ry + 0.1} ${(hz + tz * BOWL_FROM).toFixed(2)} ${yaw} 0; wait 10t; select 0`);
  const corners = [0, 2].map((axis) => pins.map((row) => row.at_abs[axis]));
  const rack = () => read([Math.min(...corners[0]), ry, Math.min(...corners[1])], [Math.max(...corners[0]), ry, Math.max(...corners[1])]);
  const fallen = (states) => pins.filter((row) => (propsOf(states.get(row.at_abs.join(",")) ?? "").pose ?? "standing") !== "standing");
  const bowled = now();
  must(`use; wait ${BOWL_REACHES_TICKS}t`);
  const down = fallen(rack());
  const clatter = played("skyliners:bowling_pin_clonk", bowled).length > 0;
  const headDown = down.some((row) => row.at_abs.join(",") === head.at_abs.join(","));
  expect(Boolean(clatter) && headDown && down.length >= BOWLING_LEAST_DOWN, "freighter: a ball bowled down the middle of her lane knocks pins down with a clatter, the head pin among them",
    `${down.length} down, head pin ${headDown ? "down" : "standing"}, ${clatter ? "clattered" : "silent"}`);
  must(`wait ${PINSETTER_TICKS}t`);
  const standing = BOWLING_PINS - fallen(rack()).length;
  const gone = must("server execute unless entity @e[type=skyliners:bowling_ball]").result === 1;
  expect(standing === BOWLING_PINS && gone, "freighter: the ball goes back down the return, and the pinsetter stands every pin up again",
    `${standing} of ${BOWLING_PINS} standing, the ball ${gone ? "gone back" : "still out"}`);
}

// A bowling pin, a lane's boards and a ball return put up by hand away from any ship: the pin stands on a floor and is
// refused on a wall, where a pin makes no sense; set down on the boards it stands on a square of them; and the ball
// return takes a shape of its own on a floor and on a wall.
function checkBowlingByHand() {
  const [x0, z0] = [nextSite++ * SITE_APART, STAND_AWAY];
  creative();
  must(`server tp @p ${x0} ${ground} ${z0 - 3}; wait ${CHUNKS_TICKS}t`);
  must(`server fill ${x0 - 1} ${ground} ${z0 + 3} ${x0 + 2 * STAND_APART} ${ground + STAND_HIGH} ${z0 + 3} stone`);
  putUpEveryWay("ball_return", x0, z0, ["floor", "wall"]);
  const x = x0 + STAND_APART;
  emptyHands();
  must(`server give @p skyliners:bowling_pin 3; server give @p skyliners:bowling_lane 1; wait 5t; select 0`);
  must(`server tp @p ${x + 0.5} ${ground} ${z0 - 5.5} 0 20; wait 10t`);
  send(`place skyliners:bowling_pin ${x},${ground},${z0 - 3}`);
  send(`place skyliners:bowling_pin ${x},${ground + 2},${z0 + 2}`);
  const floor = must(`block ${x},${ground},${z0 - 3} side=server`);
  const wall = must(`block ${x},${ground + 2},${z0 + 2} side=server`).id ?? "";
  expect(floor.id === "skyliners:bowling_pin" && floor.props?.pose === "standing" && !wall.includes("bowling_pin"),
    "bowling pin: put up by hand, it stands on a floor and is refused on a wall", `floor ${floor.id}, wall ${wall}`);
  survival();
  must(`select 8; face ${x},${ground},${z0 - 3}; key press key.attack 1t; wait 5t`);
  creative();
  const struck = must(`block ${x},${ground},${z0 - 3} side=server`).props?.pose;
  must(`use ${x},${ground},${z0 - 3}; wait 5t`);
  const used = must(`block ${x},${ground},${z0 - 3} side=server`).props?.pose;
  expect(struck === "fallen" && used === "standing", "bowling pin: struck, it goes over, and used fallen, it stands up again", `struck ${struck}, used ${used}`);
  must("select 1");
  send(`place skyliners:bowling_lane ${x + 1},${ground},${z0 - 3}`);
  must(`select 0; use ${x + 1},${ground},${z0 - 3}; wait 5t`);
  const onLane = must(`block ${x + 1},${ground},${z0 - 3} side=server`);
  expect(onLane.id === "skyliners:bowling_pin" && onLane.props?.on_lane === "true", "bowling pin: set down on a lane's boards, it stands on a square of them",
    `${onLane.id} on_lane=${onLane.props?.on_lane}`);
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

// How many blocks of her legs a freighter flying carries, by id: their struts, their foot plates and her ramp's call
// button on one of them.
function legBlocks(freighter) {
  const count = (id, most) => must(`sable:find ${freighter} skyliners:${id} ${most} side=server`).rows.length;
  return { struts: count("landing_gear", 100), feet: count("zeppelin_fabric", 100), button: count("ramp_button", 1) };
}

// A freighter's landing legs, watched from the ground: she is built with a bay where each leg meets her belly; once
// she is well clear of the ground her legs draw up into it, no strut, foot plate or call button left hanging under
// her; set to land, they are down again before her feet touch the ground, and she stands level on them, every block of
// them back. Her legs used to hang under her all through every flight.
function checkFreighterLegs() {
  layDown("freighter");
  const bays = found("leg_bay");
  expect(bays === FREIGHTER_LEGS, "freighter: she is built with a bay where each of her legs meets her belly", `${bays} bays`);
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  const built = legBlocks(freighter);
  const raised = waitForReport(freighterReport, (report) => report.legs === "up", FREIGHTER_UP_TICKS + LEGS_UP_TICKS);
  const flying = freighterReport();
  expect(raised, "freighter: well clear of the ground, her legs draw up into her belly", `legs ${flying?.legs}, ${flying?.phase} at ${flying?.altitude}`);
  const hanging = legBlocks(freighter);
  expect(hanging.struts + hanging.feet + hanging.button === 0, "freighter: her legs up, no strut, foot plate or call button hangs under her",
    `${hanging.struts} struts, ${hanging.feet} foot plates, ${hanging.button} button`);
  waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  must("skyliners:autopilot land");
  const touching = waitForReport(freighterReport, (report) => report.altitude - head[1] < LEGS_TOUCHING, FREIGHTER_DOWN_TICKS);
  expect(touching?.legs === "down", "freighter: set to land, her legs are down before her feet touch the ground",
    `legs ${touching?.legs} at ${touching?.altitude}, her control head stood at ${head[1]}`);
  const down = waitForReport(freighterReport, (report) => report.phase === "off", FREIGHTER_DOWN_TICKS);
  expect(down, "freighter: she lands and switches herself off", down ? "" : `still ${freighterReport()?.phase}`);
  if (!down) {
    return;
  }
  const lean = Math.max(Math.abs(down.tilt_front), Math.abs(down.tilt_right));
  expect(lean < FREIGHTER_LEVEL_WITHIN && Math.abs(down.altitude - head[1]) < FREIGHTER_HOLD_WITHIN, "freighter: she stands level on her legs",
    `leaning ${lean} degrees, control head at ${down.altitude}, built at ${head[1]}`);
  const back = legBlocks(freighter);
  expect(back.struts === built.struts && back.feet === built.feet && back.button === built.button,
    "freighter: every block of her legs is back, her ramp's call button among them",
    `${back.struts} of ${built.struts} struts, ${back.feet} of ${built.feet} foot plates, ${back.button} of ${built.button} button`);
}

// A freighter built with her legs fixed, the server config's freighter.retractingLegs off, as one built before her legs
// drew up has them: she has no bays, flies with her legs down all the while and comes down on them as ever.
function checkFreighterFixedLegs() {
  must("config skyliners server freighter.retractingLegs false");
  try {
    layDown("freighter");
    expect(found("leg_bay") === 0, "freighter: her legs fixed, she is built with no leg bays");
    const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
    must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
    const freighter = findShipFlying("skyliners:engine_grille");
    const built = legBlocks(freighter);
    const up = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
    const flying = legBlocks(freighter);
    expect(up && up.legs === "fixed" && flying.struts === built.struts && flying.feet === built.feet,
      "freighter: her legs fixed, she climbs to the height set with them down", `${up?.phase}, legs ${up?.legs}, ${flying.struts} of ${built.struts} struts`);
    must("skyliners:autopilot land");
    const down = waitForReport(freighterReport, (report) => report.phase === "off", FREIGHTER_DOWN_TICKS);
    expect(down && Math.abs(down.altitude - head[1]) < FREIGHTER_HOLD_WITHIN, "freighter: her legs fixed, she lands on them and switches herself off",
      `${down?.phase ?? freighterReport()?.phase}, control head at ${down?.altitude}, built at ${head[1]}`);
  } finally {
    must("config reset");
  }
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
  const since = now();
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
  expect(!heard(IMPACT_SOUND, since, 0), `freighter: she settles ${where} without a bump or a crunch`);
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

// How far past where an airliner built facing east was laid down the player stands to look over her fin, in blocks: her
// tail ends about 145 blocks on, beyond the 128 a find reaches.
const FIN_LOOK_FROM = 100;

// An airliner built facing east has the lifting layer down the middle of her fin facing out of her sides, as one built
// facing north does. Create Propulsion's wing never turned with her, so on her it faced along her and stood edge-on in
// rows: a fin of grey slats with the sky between them.
function checkFinTurnsWithTheAirliner() {
  const site = { x: nextSite++ * SITE_APART, z: 0 };
  creative();
  standToBuild(site.x, site.z, -90);
  must("skyliners:build airliner", BUILD_SECONDS);
  must(`server tp @p ${site.x + FIN_LOOK_FROM} ${ground + 5} ${site.z}; wait ${CHUNKS_TICKS}t; settle`, BUILD_SECONDS);
  const facing = (way) => must(`find createpropulsion:wing[facing=${way}] 128 1`).count ?? 0;
  const outOfHerSides = facing("north") + facing("south");
  const alongHer = facing("east") + facing("west");
  expect(outOfHerSides > 0 && alongHer === 0, "an airliner built facing east: her fin's lifting layer faces out of her sides",
    `${outOfHerSides} face out of her sides, ${alongHer} along her`);
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
  const start = now();
  must(`skyliners:autopilot fly altitude=${ground + FLIGHT_HEIGHT} speed=${FLIGHT_SPEED}`, BUILD_SECONDS);
  must(`wait ${CLIMB_OUT_TICKS}t`, BUILD_SECONDS);
  const cruising = must("sable:info here brief");
  expect(cruising.agl >= FLIGHT_HEIGHT_WANTED, "flight: it takes off and climbs", `${cruising.agl} blocks over the ground at ${cruising.speed} blocks a second`);
  const watched = must(`perf ${CRUISE_WATCH_SECONDS}s cruise`, BUILD_SECONDS).summary;
  const sends = (watched?.server?.block_changes ?? []).filter((row) => row.block === "createpropulsion:creative_thruster")
    .reduce((sum, row) => sum + row.per_s, 0);
  expect(sends < CRUISE_THRUSTER_SENDS, "flight: cruising, its engines hold their thrust, not flickering and sending every client near afresh",
    `thrusters sent ${round(sends)} times a second`);
  const since = now();
  must("skyliners:autopilot land");
  const landed = waitForMessage("*Landed*", since, LANDING_TICKS);
  expect(landed, "flight: it lands and stops");
  expect(!heard(IMPACT_SOUND, start, 0), "flight: taking off, landing and rolling out on its wheels, it is never heard striking anything",
    played(IMPACT_SOUND, start).map(soundOf).join(", "));
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

// The stickers up on a sticker map, by the position of its part holding them (a plot position aboard), sorted.
function stickersOn(at) {
  const [reply] = send(`?nbt ${at.join(",")} path=Stickers`);
  return reply.ok === false || typeof reply.value !== "object" ? [] : Object.keys(reply.value).sort();
}

// Flies the freighter up from where she stands, so high over her control head as it stood when she was built, with the
// player aboard by her sticker map, turns the land round her into a biome and lands her there. Her board's part holding
// its stickers, as a plot position, and the tick she was told to land; undefined if she did not get up or down.
function stickerLanding(head, biome) {
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  const freighter = findShipFlying("skyliners:engine_grille");
  const parts = must(`sable:find ${freighter} skyliners:sticker_map ${STICKER_PARTS} side=server`).rows;
  const holding = parts.find((row) => must(`block ${row.at.join(",")} side=server`).props?.part === "0");
  if (!holding) {
    expect(false, "freighter: her sticker map goes up with her whole");
    return undefined;
  }
  // The client is sent her as a ship a moment after the server has her, and the player cannot board her before.
  must(`wait ${CHUNKS_TICKS}t; sable:tp ${freighter} skyliners:sticker_map; wait 10t`);
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(Boolean(up), `freighter: she lifts off to be set down in the ${biome}`);
  if (!up) {
    return undefined;
  }
  const reach = STICKER_BIOME_REACH;
  must(`server fillbiome ~-${reach} ${ground - STICKER_BIOME_UNDER} ~-${reach} ~${reach} ${ground + STICKER_BIOME_OVER} ~${reach} minecraft:${biome}`);
  const landed = now();
  must("skyliners:autopilot land");
  const down = waitForReport(freighterReport, (report) => report.phase === "off", FREIGHTER_DOWN_TICKS);
  expect(Boolean(down), `freighter: she comes down in the ${biome}`);
  return down ? { board: holding.at, landed } : undefined;
}

// A freighter's sticker map: she is built with it on her wall; set down in the desert, it puts up the desert's sticker
// with a fanfare, and her computer says so once her cockpit is quiet; set down there again, it puts up nothing more;
// and the sticker is still up once the world has been left and joined again.
function checkFreighterStickerMap() {
  layDown("freighter");
  const parts = must(`find skyliners:sticker_map 128 ${STICKER_PARTS} side=server`).rows;
  expect(parts.length === STICKER_PARTS, "freighter: she is built with her sticker map, four blocks wide and two tall", `${parts.length} parts`);
  if (parts.length !== STICKER_PARTS) {
    return;
  }
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  must(`server gamerule commandModificationBlockLimit ${STICKER_FILL_MOST}`);
  const [holding] = parts.filter((row) => must(`block ${row.at_abs.join(",")} side=server`).props?.part === "0");
  expect(Boolean(holding) && stickersOn(holding.at_abs).length === 0, "freighter: her sticker map is built with no sticker up",
    holding ? stickersOn(holding.at_abs).join(",") : "no part holding its stickers");
  const first = stickerLanding(head, "desert");
  if (!first) {
    return;
  }
  const up = stickersOn(first.board);
  expect(up.includes("desert") && Boolean(heard("skyliners:sticker_map_new", first.landed, STICKER_HEARD_TICKS)),
    "freighter: set down in the desert, her map puts up its sticker with a fanfare", up.join(","));
  expect(Boolean(heard("skyliners:freighter_computer_sticker", first.landed, STICKER_VOICE_TICKS)), "freighter: and her computer says so once her cockpit is quiet");
  const again = stickerLanding(head, "desert");
  if (!again) {
    return;
  }
  expect(!heard("skyliners:sticker_map_*", again.landed, STICKER_HEARD_TICKS) && stickersOn(first.board).join(",") === up.join(","),
    "freighter: set down in the desert again, her map puts up nothing more", stickersOn(first.board).join(","));
  must(`leave; join world ${WORLD}; wait ${CHUNKS_TICKS}t`, BUILD_SECONDS);
  expect(stickersOn(first.board).join(",") === up.join(","), "freighter: her sticker is still up once the world has been left and joined again",
    stickersOn(first.board).join(","));
}

// A sticker map set up by hand on the ground: it hangs whole on a wall; used, it puts up a sticker for each kind of land
// the player has been to, with a fanfare; used again with nothing new, it puts up nothing and sounds a low note; and its
// stickers are still up once the world has been left and joined again.
function checkStickerMapByHand() {
  const [x, z] = [nextSite++ * SITE_APART, STAND_AWAY];
  creative();
  emptyHands();
  must(`server tp @p ${x + 0.5} ${ground} ${z + 0.5} 180 0; wait 40t`);
  must(`server fill ${x - 2} ${ground} ${z - 4} ${x + 5} ${ground + 3} ${z - 4} stone`);
  must("server give @p skyliners:sticker_map 1; wait 5t; select 0");
  const at = [x, ground + 1, z - 3];
  send(`place skyliners:sticker_map ${at.join(",")}`);
  const parts = must(`find skyliners:sticker_map 16 ${STICKER_PARTS} side=server`).rows;
  expect(parts.length === STICKER_PARTS && must(`block ${at.join(",")} side=server`).props?.part === "0",
    "sticker map: put up by hand on a wall, it hangs there whole, four blocks wide and two tall", `${parts.length} parts`);
  if (parts.length !== STICKER_PARTS) {
    return;
  }
  // The plains the player stands on are granted along with the rest: revoked, the game gives them back by itself within
  // a second or so, and coming back between the two uses they would be put up as new.
  send("?server advancement revoke @p only minecraft:adventure/adventuring_time");
  must("server advancement grant @p only minecraft:adventure/adventuring_time minecraft:desert; "
    + "server advancement grant @p only minecraft:adventure/adventuring_time minecraft:cherry_grove");
  send("?server advancement grant @p only minecraft:adventure/adventuring_time minecraft:plains");
  const used = now();
  must(`select 1; use ${at.join(",")}; wait ${STICKER_POPS_TICKS}t`);
  const up = stickersOn(at);
  expect(up.includes("desert") && up.includes("cherry_grove") && !up.includes("jungle") && Boolean(heard("skyliners:sticker_map_new", used, LOOK_EVERY)),
    "sticker map: used on the ground, it puts up a sticker for each kind of land the player has been to, with a fanfare", up.join(","));
  const again = now();
  must(`use ${at.join(",")}; wait 10t`);
  expect(stickersOn(at).join(",") === up.join(",") && Boolean(heard("block.note_block.bass", again, LOOK_EVERY)) && !heard("skyliners:sticker_map_*", again, 0),
    "sticker map: used again with nothing new, it puts up nothing and sounds a low note", stickersOn(at).join(","));
  must(`leave; join world ${WORLD}; wait ${CHUNKS_TICKS}t`, BUILD_SECONDS);
  expect(stickersOn(at).join(",") === up.join(","), "sticker map: its stickers are still up once the world has been left and joined again",
    stickersOn(at).join(","));
}

// How the nearest repair board stands, as Blockpilot's skyliners:repairs reads it; undefined with none near.
function repairsReport(options = "") {
  const [reply] = send(`skyliners:repairs ${options}`.trim());
  return reply.ok === false ? undefined : reply;
}

// The plot positions of the fault spots a repair board's report says are at fault.
const faultyOf = (report) => (report?.faulty ?? "").split(" ").filter(Boolean);

// A freighter's running repairs: she is built with her repair board and fault spots; standing on the ground, none goes
// off however short the time between faults is set; hovering, one goes off within that time, crackling or hissing; a
// player who goes to it and whacks it with a spanner fixes it, a chime sounds, a star lights on the board and her
// computer has its say; and the star is still lit once the world has been left and joined again.
function checkFreighterRepairs() {
  layDown("freighter");
  const board = must("find skyliners:repair_board 128 2 side=server").rows;
  const spots = must("find skyliners:fault_panel 128 4 side=server").rows.length + must("find skyliners:leaky_pipe 128 4 side=server").rows.length;
  expect(board.length === 2 && spots >= REPAIR_SPOTS_LEAST, "freighter: she is built with her repair board and fault spots round her rooms",
    `${board.length} board parts, ${spots} fault spots`);
  if (board.length !== 2) {
    return;
  }
  const [bx, by, bz] = board[0].at_abs;
  must(`server tp @p ${bx + 0.5} ${by - 1} ${bz + 0.5}; fly off; wait ${CHUNKS_TICKS}t`);
  repairsReport(`every=${REPAIR_TEST_EVERY}`);
  must(`wait ${REPAIR_GROUND_WATCH}t`);
  const standing = repairsReport();
  expect(standing && standing.waiting === 0 && !standing.flying, "freighter: standing on the ground, no fault goes off", JSON.stringify(standing));
  const [{ at_abs: head }] = must("find skyliners:autopilot 128 1").rows;
  const since = now();
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  findShipFlying("skyliners:engine_grille");
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(Boolean(up), "freighter: she lifts off and hovers for her repairs, the player aboard by her repair board");
  if (!up) {
    repairsReport("every=0");
    return;
  }
  const broken = waitForReport(repairsReport, (report) => report.waiting > 0, REPAIR_FAULT_WITHIN);
  const noise = heard("skyliners:fault_*", since, LOOK_EVERY);
  expect(Boolean(broken) && Boolean(noise), "freighter: hovering, a fault goes off within the time set, crackling or hissing",
    JSON.stringify(broken ?? repairsReport()));
  repairsReport("every=0");
  const [fault] = faultyOf(broken);
  if (!fault) {
    return;
  }
  const flying = now();
  must(`wait ${REPAIR_NOISE_WATCH}t`);
  const noises = played("skyliners:fault_*", flying).length;
  expect(noises <= REPAIR_NOISES_MOST, "freighter: flying with a fault, it crackles or hisses only now and then", `${noises} in ${REPAIR_NOISE_WATCH} ticks`);
  must("skyliners:autopilot off", BUILD_SECONDS);
  const parked = waitForReport(freighterReport, (report) => report.phase === "off", FREIGHTER_DOWN_TICKS);
  const quiet = now();
  must(`wait ${REPAIR_PARKED_WATCH}t`);
  expect(Boolean(parked) && played("skyliners:fault_*", quiet).length === 0, "freighter: parked and switched off, a fault makes no more noise", parked ? "" : `still ${freighterReport()?.phase}`);
  emptyHands();
  must(`server give @p skyliners:spanner 1; wait 5t; select 0; sable:tp ${fault}; wait 20t`);
  const fixedAt = now();
  must(`use ${fault}; wait ${REPAIR_FIXED_WITHIN}t`);
  const fixed = repairsReport();
  expect(fixed && fixed.repairs === 1 && !faultyOf(fixed).includes(fault) && Boolean(heard("skyliners:centrifuge_ding", fixedAt, LOOK_EVERY)),
    "freighter: whacked with the spanner, the fault is fixed with a chime and a star lights on her board", JSON.stringify(fixed));
  expect(Boolean(heard("skyliners:freighter_computer_repair_*", fixedAt, REPAIR_VOICE_WITHIN)), "freighter: and her computer has its say");
  must(`leave; join world ${WORLD}; wait ${CHUNKS_TICKS}t`, BUILD_SECONDS);
  const rejoined = repairsReport();
  expect(rejoined?.repairs === 1, "freighter: her star is still lit once the world has been left and joined again", JSON.stringify(rejoined));
  send("?skyliners:autopilot land");
}

// A fault panel set up by hand on a wall beside a repair board: a redstone signal coming on sets it sparking and
// crackling; whacked with an empty hand it is fixed with a thump and a chime, lighting a star on the board; the board,
// used by someone with no spanner, hands them one; and on the ground nothing sets it off by itself.
function checkRepairsByHand() {
  const [x, z] = [nextSite++ * SITE_APART, STAND_AWAY];
  creative();
  emptyHands();
  must(`server tp @p ${x + 0.5} ${ground} ${z + 0.5} 180 0; wait 40t`);
  must(`server fill ${x - 3} ${ground} ${z - 4} ${x + 3} ${ground + 3} ${z - 4} stone`);
  must("server give @p skyliners:fault_panel 1; server give @p skyliners:repair_board 1; wait 5t; select 0");
  const panel = [x, ground + 1, z - 3];
  const boardAt = [x - 2, ground + 1, z - 3];
  send(`place skyliners:fault_panel ${panel.join(",")}`);
  must("select 1");
  send(`place skyliners:repair_board ${boardAt.join(",")}`);
  const placed = must(`block ${panel.join(",")} side=server`);
  expect((placed.id ?? "").includes("fault_panel") && placed.props?.faulty === "false", "fault panel: put up by hand on a wall, it hangs there, all well",
    JSON.stringify(placed));
  const powered = now();
  must(`server setblock ${x} ${ground + 2} ${z - 3} minecraft:redstone_block; wait 10t`);
  const sparking = must(`block ${panel.join(",")} side=server`).props?.faulty === "true";
  expect(sparking && Boolean(heard("skyliners:fault_crackle", powered, LOOK_EVERY)), "fault panel: a redstone signal sets it sparking and crackling");
  must(`server setblock ${x} ${ground + 2} ${z - 3} minecraft:air; wait 5t`);
  emptyHands();
  const whacked = now();
  must(`use ${panel.join(",")}; wait 10t`);
  const board = repairsReport();
  expect(must(`block ${panel.join(",")} side=server`).props?.faulty === "false" && Boolean(heard("skyliners:centrifuge_ding", whacked, LOOK_EVERY))
    && board?.repairs === 1, "fault panel: whacked with an empty hand, it is fixed with a chime and the board beside it lights a star", JSON.stringify(board));
  must(`use ${boardAt.join(",")}; wait 5t`);
  const held = must("inv").rows.some((row) => row.item === "skyliners:spanner");
  expect(held, "repair board: used with no spanner about you, it hands you one");
}

const CHECKS = {
  "curtain-parts": { run: checkCurtainParts, about: "a curtain parts as someone walks through it and shuts behind them" },
  "bartender-leaning-rocket": { run: checkBartenderOnALeaningRocket, about: "a bartender on a leaning rocket does not keep whirring at someone standing still" },
  "bartender-racket": { run: checkBartendersAreNoRacket, about: "a crowd of robot bartenders is not a constant racket of servos" },
  "airstairs-reach-ground": { run: checkAirstairsReachTheGround, about: "a newly built airliner's stairs can be walked up from the ground" },
  "airstairs-mobs": { run: checkAirstairsMobs, about: "a hostile mob on an airliner's stairs does not hold them, an animal does" },
  "airstairs-leave-no-steps": { run: checkNoStepsLeftBehind, about: "taking off from a new build leaves no steps behind and takes no ground along" },
  "zeppelin-stairs": { run: checkZeppelinStairs, about: "a zeppelin's stairs can be walked up from the ground" },
  "rocket-stairs": { run: checkRocketStairs, about: "a rocket's stairs can be walked up from the ground" },
  "freighter-painting": { run: checkFreighterOnTheShips, about: "the freighter hangs whole and unhidden on each ship, and the wheel and breaking it work" },
  "fog-hiss": { run: checkFogHissStops, about: "a fog machine's hiss plays out, and stops when the machine goes however it goes" },
  "tardigrade-ice-cream": { run: checkTardigradeIceCream, about: "an ice cream: gulp, massive burp, the ship shakes, hyperspace, back" },
  "control-surfaces": { run: checkControlSurfacesDefault, about: "control surfaces are on by default" },
  "fin-turns": { run: checkFinTurnsWithTheAirliner, about: "an airliner built facing east has her fin's lifting layer facing out of her sides, not edge-on in rows" },
  "flight": { run: checkFlightAndLanding, about: "take-off, cruise, landing, and walking up the stairs after it", slow: true },
  "freighter-self-destruct": { run: checkFreighterSelfDestruct, about: "a freighter's self-destruct button by her jump seats arms her, counts down and blows her up, whoever stood aboard given a parachute" },
  "rocket-self-destruct-ground": { run: checkRocketSelfDestructOnTheGround, about: "a rocket's self-destruct puts her together, arms and blows her up" },
  "rocket-self-destruct-flight": { run: checkRocketSelfDestructInFlight, about: "a rocket blows up in flight, her roar stops, and whoever was aboard floats down unhurt", slow: true },
  "rocket-switches-off": { run: checkRocketSwitchesOffWhereSheRests, about: "a rocket switches off where she comes to rest, with the ground lower under her middle or hidden by leaves", slow: true },
  "freighter-drag": { run: checkFreighterUnlearnsDrag, about: "a freighter that has learnt far too much drag is not pushed on past the speed set, and unlearns it", slow: true },
  "freighter-pad": { run: checkFreighterPad, about: "a freighter set to land at a pin with stone on it comes down beside it, level, and looks again next time", slow: true },
  "freighter-ramp": { run: checkFreighterRamp, slow: true, about: "a freighter's ramp comes down by itself, is walked up, is called from her leg, and holds her take-off while someone stands on it" },
  "freighter-ramp-lag": { run: checkFreighterRampLag, slow: true, about: "a freighter's ramp is drawn folding and coming down evenly with the server running behind" },
  "freighter-ramp-mobs": { run: checkFreighterRampMobs, slow: true, about: "a hostile mob under a freighter's ramp does not hold it, an animal does" },
  "zeppelin-deck-front": { run: checkZeppelinDeckFront, slow: true, about: "a furnished zeppelin's upper deck has a rail along its front edge, so nobody walks off its corners into the hull" },
  "zeppelin-club-spawns": { run: checkZeppelinClubSpawns, slow: true, about: "a furnished zeppelin's dark secret techno room lets no monster spawn in it" },
  "mounted-only": { run: checkMountedOnly, about: "fittings that hang from a ceiling or fix to a wall are refused on a bare floor, where they stood in the air, and go up on their mount" },
  "orrery-sky": { run: checkOrrerySky, slow: true, about: "an orrery hung up by hand in the open draws no planetarium's sky, and a rocket's still does" },
  "hoop-stand": { run: checkHoopStand, about: "a basketball hoop set down on a floor stops a player walking into it, rather than being climbed like stairs" },
  "freighter-ramp-well": { run: checkFreighterRampWell, slow: true, about: "a freighter's ramp well is walled in under her deck, so the space under it does not show from the ramp" },
  "freighter-ramp-foot": { run: checkFreighterRampFoot, slow: true, about: "a freighter's ramp meets ground a block higher or lower at its foot and is walked both ways, and refuses past that" },
  "freighter-keeps-her-belly": { run: checkFreighterKeepsHerBelly, slow: true, about: "a freighter loaded from a save folds her ramp and sheds no piece of her belly, her ramp comes down again, and a real break still comes away" },
  "freighter-gunner-takeoff": { run: checkFreighterGunnerTakeoff, slow: true, about: "a freighter set to fly from her gunner's seat lifts off" },
  "freighter-turret": { run: checkFreighterTurret, slow: true, about: "a freighter's core ladder is climbed to her gunner's seat, whose cannon strikes a creeper and blasts stone, breaking none of her" },
  "freighter-barrels": { run: checkFreighterBarrels, slow: true, about: "walking across a freighter's laser cannon barrels on her dome, laid down and assembled, they stop the player" },
  "freighter-rockets": { run: checkFreighterRockets, slow: true, about: "a freighter's cannon launches its rockets at a creeper and blasts stone, breaking none of her, the sight showing its pods load again" },
  "ship-hits": { run: checkShipHits, slow: true, about: "a freighter's cannon fired at another freighter hovering abeam: bolts ping off her and rock her a little, a rocket harder, a burst never far" },
  "laser-crossbow": { run: checkLaserCrossbow, about: "the laser crossbow fires at a click, its short bolt seen flying out of it, strikes a creeper, never a cow, and held down it vents and cools" },
  "laser-crossbow-flight": { run: checkLaserCrossbowInFlight, slow: true, about: "the laser crossbow's bolt, fired aboard a flying freighter, is seen leaving it" },
  "freighter-rooms": { run: checkFreighterRooms, about: "a freighter's crew quarters have their bunks, her lounge its booth with leg room, her hold its cargo and scanner" },
  "freighter-tardigrade": { run: checkFreighterTardigrade, about: "a freighter's small tardigrade lives in her lounge's tank and comes to its hatch to munch moss" },
  "fleet-on-freighter": { run: checkFleetOnTheFreighter, about: "the airliner, zeppelin and rocket paintings hang whole on the freighter" },
  "birthday-greeting": { run: checkBirthdayGreeting, slow: true, about: "a freighter greets whoever the client config names as they come aboard on their birthday, once, and nobody the day before" },
  "grille-redstone": { run: checkEngineGrilleRedstone, slow: true, about: "an engine grille set by hand goes by redstone, and a freighter's own keeps to her autopilot" },
  "freighter-nozzles": { run: checkFreighterNozzles, about: "a freighter's main engines show a nozzle with a bright lip all round from behind her, not a black socket or her grille cutting in" },
  "freighter-underside-camera": { run: checkFreighterUndersideCamera, about: "a freighter's underside camera shows the ground under her with her legs drawn up in flight, not the inside of her belly" },
  "freighter-lights": { run: checkFreighterLights, about: "a freighter is built with wall washers round her rooms, her club and ring corridor lit" },
  "freighter-band": { run: checkFreighterBand, about: "a freighter's club has a bar and a band that plays her song to whoever comes near, silent as they go" },
  "freighter-spookie": { run: checkFreighterSpookie, about: "a freighter's Spookie greets, purrs, eats a cookie, is never hurt and walks its rounds" },
  "freighter-leds": { run: checkFreighterLeds, about: "a freighter's rooms each have LED strips and a panel, each room a colour and a floor of its own, out to her prongs' tips and her cockpit" },
  "freighter-console": { run: checkFreighterConsole, about: "a freighter's console curves round her seats, its boost says no on the ground and its toggles click" },
  "freighter-solid": { run: checkFreighterSolid, about: "a freighter's jump drive rings, band and sun loungers stop whoever walks into them" },
  "freighter-console-solid": { run: checkFreighterConsoleSolid, about: "a freighter's console desk stops whoever walks at it where it is drawn, not its whole block" },
  "strip-joins": { run: checkStripJoins, about: "LED strips laid by hand join up as train track does: round corners on the floor, round a block, up a wall and over it, under a ceiling" },
  "nothing-walk-through": { run: checkNothingWalkThrough, about: "every block of the mod is solid as drawn, but a curtain, a ball pit, a climbing rope and an open door's doorway, walked through for what they do" },
  "hammock-posts": { run: checkHammockPosts, about: "a Spookie's hammock stands on two solid posts, built and set down by hand, and broken comes down whole as one hammock" },
  "dish-bowl": { run: checkDishBowl, about: "a big satellite dish's bowl stops whoever walks under it, out past its block, however it is turned" },
  "freighter-outside-solid": { run: checkFreighterOutsideSolid, about: "a freighter's cannon housing and big dish are stood on, and her parcel scanner's gantry stops whoever walks into it" },
  "souvenir-shapes": { run: checkSouvenirShapes, about: "the rocket and freighter souvenir models are clicked only where they are drawn" },
  "freighter-cockpit-reach": { run: checkFreighterCockpitReach, about: "a freighter's climb, spin, boost and jump buttons are all in reach from her pilot's seat" },
  "freighter-boost": { run: checkFreighterBoost, slow: true, about: "a freighter's boost button fires her mains flat out a while, then rests, and she slows back" },
  "freighter-climb-boost": { run: checkFreighterClimbBoost, slow: true, about: "a freighter's climb button fires her lift flat out, and she climbs straight up hard and holds the height" },
  "freighter-spin": { run: checkFreighterSpin, slow: true, about: "a freighter's spin button says no near the ground, and high up rolls her right over and back level, well clear of it" },
  "freighter-rights-herself": { run: checkFreighterRightsHerself, slow: true, about: "a freighter turned upside down in flight rights herself well clear of the ground and hovers level again" },
  "freighter-slam-from-space": { run: checkFreighterSlamFromSpace, slow: true, about: "a freighter's slam button works far out in space and brings her down hard all the way, nobody hurt" },
  "freighter-space-ceiling": { run: checkFreighterSpaceCeiling, slow: true, about: "a freighter's autopilot takes her to y 2,500 and holds her there, her climb button climbing on past the edge of space" },
  "space-weather": { run: checkSpaceWeather, slow: true, about: "a meteor shower and an asteroid storm, each warned of, strike a freighter in space and shove her hard, a rock harder; she settles back, nobody hurt" },
  "freighter-blackout": { run: checkFreighterBlackout, slow: true, about: "a solar flare's pulse knocks out a freighter's thrusters, lights and screens for five seconds, or as the server's config says, then they come back by themselves and she climbs back" },
  "space-rocks-shot": { run: checkSpaceRocksShot, slow: true, about: "from a freighter's laser cannon in space, one bolt breaks exactly one block of a rock, the sight locks on to a rock flying in and rockets wear it through, her client told so" },
  "space-rocks-not-saved": { run: checkSpaceRocksNotSaved, slow: true, about: "left mid-storm and joined again, a world holds none of the rocks that were flying as real bodies" },
  "space-rock-solid": { run: checkSpaceRockSolid, slow: true, about: "a rock coming at a freighter in space is a real body of blocks that stops at her hull" },
  "space-rocks-fly-on": { run: checkSpaceRocksFlyOn, slow: true, about: "a storm's rocks fly on out of sight once it is over, still there to be shot at, rather than vanishing where they are" },
  "space-rocks-far": { run: checkSpaceRocksFar, slow: true, about: "a freighter's laser cannon can shoot at space rocks well over 150 blocks off: they are real bodies there, on the server and her gunner's client" },
  "giant-asteroid": { run: checkGiantAsteroid, slow: true, about: "a giant asteroid shoves a freighter far harder than a rock and spins her, the server never stalling, nobody hurt" },
  "giant-asteroid-cracked": { run: checkGiantAsteroidCracked, slow: true, about: "from a freighter's laser cannon, rockets crack a giant asteroid into pieces that fly apart off its course and land softer, the server never stalling" },
  "rocket-space-weather": { run: checkRocketSpaceWeather, slow: true, about: "an asteroid storm, announced, strikes a rocket in space, shoving and tipping her; she waits it out, stands upright again, nobody hurt" },
  "freighter-slam": { run: checkFreighterSlam, slow: true, about: "a freighter's slam button drives her straight down hard, strikes the ground with a crash, and sets her down unharmed, nobody aboard hurt" },
  "freighter-jump": { run: checkFreighterJump, slow: true, about: "a freighter jumps through hyperspace to her pin, clear over a pillar standing on it, everyone aboard with her" },
  "turret-throws-players": { run: checkTurretThrowsPlayers, about: "a laser cannon's rocket throws a player far, flying in creative mode unhurt, in survival hurt and landing under their canopy" },
  "fairing-corners": { run: checkFairingCorners, about: "airframe fairings put down by hand round a corner, resting or hung, turn it as stairs do; lone ones and straight runs stay straight" },
  "transponder-pictures": { run: checkTransponderPictures, about: "a transponder's panel shows every picture on the screen, the space ones among them, and each picks and keeps its own" },
  "console-mounts": { run: checkConsoleMounts, about: "each console module set up by hand stands on a floor, a wall or a ceiling, facing whoever put it up" },
  "freighter-canopy": { run: checkFreighterCanopy, about: "a freighter's canopy is one smooth dome its lining draws inside and out, and dims, seen from her seat and from outside" },
  "freighter-rain": { run: checkFreighterRain, about: "rain over a freighter neither falls in her cockpit nor splashes inside her canopy, seen from her seat" },
  "freighter-jump-seen": { run: checkFreighterJumpSeen, slow: true, about: "a freighter's hyperspace jump is seen and heard from the ground at both ends, her stretching into it and out of it" },
  "freighter-jump-heard-far": { run: checkFreighterJumpHeardFar, slow: true, about: "a freighter's hyperspace jump is heard from over three hundred blocks off, dull and late as sound takes to come that far" },
  "freighter-jump-drive": { run: checkFreighterJumpDrive, about: "a freighter's jump drive spools up and fires, or sputters until thumped" },
  "freighter-breaker-board": { run: checkFreighterBreakerBoard, about: "a freighter's breaker board puts a room's lights out and back on" },
  "freighter-coolant-valve": { run: checkFreighterCoolantValve, about: "a freighter's coolant valve is cranked up to a blast of steam and lets it off" },
  "freighter-tractor-beam": { run: checkFreighterTractorBeam, about: "a freighter's tractor beam lets the player down from its pad and beams them and a cow up, never a zombie" },
  "freighter-beam-flight": { run: checkFreighterTractorBeamInFlight, about: "a hovering freighter's tractor beam lets the player down to the ground unhurt and beams them back up", slow: true },
  "freighter-sticker-map": { run: checkFreighterStickerMap, slow: true, about: "a freighter's sticker map puts up the desert's sticker with a fanfare as she lands there, not again next time, and keeps it through a rejoin" },
  "sticker-map-by-hand": { run: checkStickerMapByHand, about: "a sticker map set up by hand puts up the lands the player has been to, nothing twice, and keeps them through a rejoin" },
  "tractor-beam-by-hand": { run: checkTractorBeamByHand, about: "tractor beams set up by hand hang or stand, beam a sheep onto a pad and float a player up and down" },
  "freighter-zero-gravity": { run: checkFreighterZeroGravity, about: "a freighter's club gravity switch floats the player and a cookie, a jump lifts them higher, and pressed again they float down unhurt" },
  "gravity-switch-by-hand": { run: checkGravitySwitchByHand, about: "a gravity switch set by hand on a room's wall floats that room, not outside it" },
  "freighter-zero-gravity-flight": { run: checkFreighterZeroGravityInFlight, slow: true, about: "a hovering freighter's club gravity switch floats the player aboard her and lets them down unhurt" },
  "freighter-crossbow-rack": { run: checkFreighterCrossbowRack, about: "a freighter's crossbow rack gives the laser crossbow down and takes it back" },
  "freighter-furnishings": { run: checkFreighterFurnishings, about: "a freighter's rooms are filled out with furniture of their own" },
  "freighter-screens": { slow: true, run: checkFreighterScreens, about: "a freighter's passenger screens, pressed aboard her flying, show her outside cameras" },
  "freighter-portholes": { run: checkFreighterPortholes, about: "a freighter has portholes down her prongs and her cockpit's tube, each letting daylight in" },
  "freighter-lit": { run: checkFreighterLit, about: "a freighter has no spot inside her left unlit, where monsters could spawn" },
  "hand-set-windows": { run: checkHandSetWindows, about: "an oval window and a porthole set by hand into a wall go in whole, dim at a touch and come out whole; a porthole lies flat in a floor" },
  "floor-window": { run: checkFloorWindow, about: "a round floor window laid into the ground blends into it, opens from its panel and comes out whole" },
  "floor-window-sizes": { run: checkFloorWindowSizes, about: "a round floor window set by hand into a floor or a wall takes the largest size that fits, from full to three and two across, and each opens, shuts and comes out whole" },
  "floor-window-edge": { run: checkFloorWindowEdge, about: "a round floor window laid to a floor's very edge shows the floor's own side there, not its well" },
  "freighter-gallery": { run: checkFreighterGallery, about: "a freighter's cockpit tube has the fleet's models on its ledges, her port prong's tunnel space posters, and no engine stands out in her prongs" },
  "freighter-engine-bays": { run: checkFreighterEngineBays, about: "a freighter's thrusters in her prongs' tips are boxed in, out of reach from the tunnels, their bays unbroken by a pickaxe" },
  "freighter-smuggler-hold": { run: checkFreighterSmugglerHold, about: "a freighter's loose rivet chimes, its panel knocks hollow, opens onto a den of loot, shuts by itself and opens from inside" },
  "secret-panel": { run: checkSecretPanelByHand, about: "a secret panel set into a wall by hand opens and shuts at its loose rivet, and a rivet goes on a wall, a floor and a ceiling" },
  "girders-join": { run: checkGirdersJoin, about: "steel girders put down by hand join up, a column on its footing, one on top, a beam bolted to its side, and let go when one is broken" },
  "freighter-lookout": { run: checkFreighterLookout, about: "a freighter's port prong has seats by its portholes and a nook at its tip facing a screen" },
  "beach-on-walls": { run: checkBeachOnWalls, about: "a tiki torch and a floor window panel go up on a floor and on a wall, each in a shape of its own" },
  "freighter-bowling": { run: checkFreighterBowling, about: "a freighter's bowling lane hands over a ball that knocks pins down with a clatter, goes back down the return, and the pins stand up again" },
  "bowling-by-hand": { run: checkBowlingByHand, about: "a bowling pin stands on a floor and on a lane's boards but not on a wall, and a ball return goes up on a floor and a wall" },
  "freighter-beach": { run: checkFreighterBeach, about: "a freighter's beach: her round floor window in its sand, opened from its panel down to her belly's glass, her palms in the open" },
  "freighter-exterior": { run: checkFreighterExterior, about: "a freighter is built in her own hull with her outside, and each outside fitting goes up on a floor, a wall or a ceiling" },
  "freighter-exterior-flush": { run: checkFreighterExteriorFlush, about: "every piece of a freighter's outside meets her hull face to face, no gap under it" },
  "freighter-exterior-lift-off": { run: checkFreighterExteriorLiftOff, slow: true, about: "a freighter lifting off leaves none of her outside behind" },
  "freighter-legs": { run: checkFreighterLegs, slow: true, about: "a freighter's legs draw up into her belly once she is clear, and are down again before she lands on them" },
  "freighter-fixed-legs": { run: checkFreighterFixedLegs, slow: true, about: "a freighter built with fixed legs, as before they drew up, flies and lands on them as ever" },
  "freighter-robot": { run: checkFreighterRobot, about: "a freighter's deck robot chirps hello as someone walks up to it, and trills when touched" },
  "freighter-lake": { run: () => withSmallSearch(checkFreighterLake), about: "a freighter set to land at a pin in a lake holds over it and says she finds no ground", slow: true },
  "freighter-balked": { run: checkFreighterBalked, about: "a freighter told to land where she is over stone taller than her belly's clearance climbs back, says so and comes down on clear ground beside it", slow: true },
  "freighter-far-landing": { run: checkFreighterFarLanding, about: "a freighter set to land far from the world's middle, where she cannot creep, comes down where she stops short of her spot", slow: true },
  "freighter-far-pad": { run: checkFreighterFarPad, slow: true, about: "a freighter set to land in a wide lake goes on to clear ground well away from the pin and says how far" },
  "freighter-uneven-landing": { run: checkFreighterUnevenLanding, slow: true, about: "a freighter set to land on a gentle slope telescopes her legs and stands level" },
  "freighter-balked-slope": { run: checkFreighterBalkedSlope, about: "a freighter with one foot on higher ground settles and switches off, not taken for a landing she cannot finish", slow: true },
  "freighter-balked-lag": { run: checkFreighterBalkedLag, about: "a freighter landing with the server far behind still comes down and switches off, not taken for a landing she cannot finish", slow: true },
  "freighter-balked-nowhere": { run: () => withSmallSearch(checkFreighterBalkedNowhere), about: "a freighter that cannot finish landing and has nowhere else to try holds and says so, and told to switch off lets go on what stopped her", slow: true },
  "freighter-balked-unattended": { run: checkFreighterBalkedUnattended, about: "a freighter with nobody aboard that cannot finish landing lets go in the end and stays switched off, never going up and down for ever", slow: true },
  "impact-sounds": { run: checkImpactSounds, about: "a freighter dropped onto the ground crunches and groans, and shoved along it grinds", slow: true },
  "freighter-flight": { run: checkFreighterFlight, about: "the freighter lifts off, hovers level and roars, not spooling up as she holds her spot, and lands by herself", slow: true },
  "unattended-far-off": { run: checkUnattendedFarOff, about: "a freighter left flying with nobody near comes down at once and is let go", slow: true },
  "freighter-repairs": { run: checkFreighterRepairs, slow: true, about: "a freighter's faults go off only in flight, crackling; whacked with a spanner, one is fixed and lights a star that stays lit" },
  "repairs-by-hand": { run: checkRepairsByHand, about: "a fault panel set up by hand sparks on redstone and, whacked, lights a star on a repair board beside it, which hands out spanners" },
  "freighter-voice": { run: checkFreighterVoice, about: "a freighter's computer, not the airliner's flight deck, says she lifts off, comes down and is down, her ramp never talking over it", slow: true },
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

// A check that throws has failed, but the others still run; only losing the game itself ends it all. A player left dead
// would fail every check after it for nothing, with nothing to say why: that is said once, here, and they are brought
// back. A check that threw may have left the server slowed down on purpose, so that is ended here too.
function runCheck(name) {
  try {
    CHECKS[name].run();
  } catch (error) {
    if (error instanceof GameGone) {
      throw error;
    }
    expect(false, `${name} could not finish`, error.message);
    send("?server gamemode creative @p");
    send("?load off");
  }
  const [status] = send("?status");
  if (status?.hp === 0) {
    expect(false, `${name}: the player is left dead`, `at ${status.pos?.map(round).join(",")}`);
    send("?respawn; wait 40t");
    send("?server gamemode creative @p; fly on");
  }
}

process.exit(main());
