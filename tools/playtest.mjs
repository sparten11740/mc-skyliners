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
const FREIGHTER_UP_TICKS = 900;
const FREIGHTER_DOWN_TICKS = 2400;
const FREIGHTER_HOLD_WITHIN = 1.5;
const FREIGHTER_LEVEL_WITHIN = 2;
const FREIGHTER_WATCH_TICKS = 200;
const FREIGHTER_AWAY = 400;
const FREIGHTER_AWAY_TICKS = 60;
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
// Walking at a freighter's scanner module from her control head: how far out from its middle to start, in blocks,
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
// each block the player must come to stand, in blocks, with what margin: on the cannon's housing over its pedestal,
// and on the middle of the dish's bowl over its mount.
const DROP_FROM = 3;
const CANNON_HOUSING_TOP = 1.375;
const DISH_BOWL_TOP = 1.75;
const STANDS_WITHIN = 0.05;
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
// How long after her ramp folds away its call button is pressed again, and how long her take-off may be held before
// she calls it off, with a margin, in ticks.
const CALL_AGAIN_TICKS = 20;
const TAKEOFF_CALLED_OFF_TICKS = 700;
// A freighter's top turret: how long the walk up her core's ladder takes, in ticks; how far up and ahead of her cannon
// a creeper is hung to shoot at, in blocks; and how long attack is held, in ticks, several bolts' worth.
const LADDER_CLIMB_TICKS = 100;
const TURRET_TARGET_UP = 10;
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
// The laser crossbow: how long use is held to charge it, in ticks, longer than it takes, and how long to see that held
// past its click it does not fire by itself; how far in front of the
// player its targets stand, in blocks, and how far down the player looks at them, in degrees; a creeper's and a cow's
// full health, in half hearts; how long it is left to cool after venting, in ticks; and how many shots in a row vent
// it. Its bolt: how soon after a shot it is looked for and how much later again, in ticks; how long it must have shown
// by then at least, in ticks, more than the blink of four it once was; and how near the eye it must leave from, in
// blocks, as the crossbow's muzzle is.
const CROSSBOW_CHARGE_TICKS = 30;
const CROSSBOW_HELD_LONG_TICKS = 60;
const CROSSBOW_TARGET_AHEAD = 6;
const CROSSBOW_AIM_DOWN = 8;
const CREEPER_HEALTH = 20;
const COW_HEALTH = 10;
const CROSSBOW_COOLED_TICKS = 100;
const CROSSBOW_HEAT = 3;
const BOLT_LOOKED_FOR_TICKS = 3;
const BOLT_LATER_TICKS = 15;
const BOLT_SHOWN_TICKS = 8;
const BOLT_NEAR_EYE = 1.5;
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
// Her canopy's dimmer: how far its face stands out from her wall, in blocks, and where its darkest and its clear button
// are on it, so far along it toward its side a quarter turn clockwise from the way it faces and up from its foot, in
// blocks.
const DIMMER_FACE = 0.12;
const DIMMER_DARKEST = [0.8, 0.15];
const DIMMER_CLEAR = [0.2, 0.75];
// Her console: its modules round her seats, each a slice of its ring, by kind; and the kinds a player can set up by hand.
// Her slam button takes the toggles' place, as the server config's freighter.slamLanding has her built by default.
const CONSOLE_SLICES = { console_readouts: 1, console_boost: 1, console_climb: 1, console_scanner: 1, console_jump: 1, console_engines: 1,
  console_slam: 1 };
const CONSOLE_KINDS = ["console_readouts", "console_scanner", "console_engines", "console_switches", "console_boost", "console_jump", "console_climb",
  "console_slam"];
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
const TIP_STRIPS_NEAR = 4;
const COCKPIT_FLOOR_NEAR = 5;
// How far behind her control head her pilots' seats stand, in blocks, where her cockpit's floor is looked over from.
const SEATS_BEHIND_HEAD = 2;
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
  expect(off && lampAboard() === "down", "freighter: held a long while, she calls her take-off off, her ramp left down",
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
  return reply.on ? { at: String(reply.at).split(",").map(Number), locked: reply.locked, rockets: reply.rockets, loading: reply.loading } : undefined;
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

// The laser crossbow: charged by holding use, it fires an amber bolt that the player sees leave the crossbow's muzzle
// in front of them and that shows a while, not for a blink; it strikes a creeper hard and leaves a cow unharmed; three
// quick shots and it vents, and will not fire again until it has cooled. The creeper is shot with the world on easy, as
// a peaceful world takes hostile mobs away at once. The quick shots go in one script: sent one by one, the time between
// them let it cool before the third.
function checkLaserCrossbow() {
  const x = nextSite++ * SITE_APART;
  creative();
  must(`server tp @p ${x} ${ground} 0; turn 0 0; wait 40t; server item replace entity @p weapon.mainhand with skyliners:laser_crossbow`);
  const held = now();
  must(`turn 0 0; use hold=${CROSSBOW_HELD_LONG_TICKS}t; wait 5t`);
  const charged = /laser_crossbow_charged\W*1b/.test(JSON.stringify(send("server data get entity @p SelectedItem")[0]));
  expect(charged && !heard("skyliners:laser_crossbow_shot", held, 0), "laser crossbow: held long past its click, it charges and does not fire by itself",
    charged ? "charged" : "not charged");
  seesItsBolt("laser crossbow: fired", "use");
  must("server difficulty easy");
  const struck = shootAt("creeper", x);
  must("server difficulty peaceful");
  expect(struck < CREEPER_HEALTH, "laser crossbow: charged and fired, its bolt strikes a creeper", `health ${struck} of ${CREEPER_HEALTH}`);
  const spared = shootAt("cow", x);
  expect(spared === COW_HEALTH, "laser crossbow: its bolt never hurts a cow", `health ${spared} of ${COW_HEALTH}`);
  must(`wait ${CROSSBOW_COOLED_TICKS}t`);
  const quick = now();
  const shot = `use hold=${CROSSBOW_CHARGE_TICKS}t; wait 5t; use; wait 5t`;
  must(`${Array(CROSSBOW_HEAT + 1).fill(shot).join("; ")}; wait 10t`);
  const shots = played("skyliners:laser_crossbow_shot", quick).length;
  expect(shots === CROSSBOW_HEAT && heard("minecraft:block.fire.extinguish", quick, 0),
    "laser crossbow: three quick shots and it vents, hissing, and does not fire a fourth", `${shots} shots`);
  must(`wait ${CROSSBOW_COOLED_TICKS}t`);
  const cooled = now();
  must(`use hold=${CROSSBOW_CHARGE_TICKS}t; wait 5t; use; wait 10t`);
  expect(heard("skyliners:laser_crossbow_shot", cooled, 0), "laser crossbow: once cooled, it charges and fires again");
}

// Fires the laser crossbow in hand, charged, by a script ending in its use, and looks at its bolt as the player sees
// it: drawn leaving the crossbow's muzzle in front of them, not where the server had them, and still shown a moment
// later, not gone in a blink.
function seesItsBolt(what, fire, aimed = "") {
  const before = must("skyliners:bolts").received ?? 0;
  const bolt = must(`${fire}; wait ${BOLT_LOOKED_FOR_TICKS}t; skyliners:bolts`);
  const [right, up, ahead] = (bolt.from_camera ?? "0,0,-99").split(",").map(Number);
  const fromEye = Math.hypot(right, up, ahead);
  expect(bolt.received > before && bolt.from_muzzle === true && ahead > 0 && fromEye <= BOLT_NEAR_EYE,
    `${what}, its bolt is drawn leaving the crossbow's muzzle in front of the player`,
    `${bolt.received - before} sent, from the muzzle ${bolt.from_muzzle}, from the eye ${right}, ${up}, ${ahead}${aimed ? `, ${aimed}` : ""}`);
  const later = must(`wait ${BOLT_LATER_TICKS}t; skyliners:bolts`);
  expect(later.shown >= BOLT_SHOWN_TICKS, `${what}, its bolt shows a while, not for a blink`, `shown ${later.shown} ticks`);
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
  must(`use hold=${CROSSBOW_CHARGE_TICKS}t; wait 5t`);
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

// A freighter's canopy seen from inside: one smooth cone drawn by its lining, not her glass and its struts stepping
// round her curve, which are drawn from outside only; and dimmed by her dimmer, the view through it darkens with her
// glass, as smooth.
function checkFreighterCanopy() {
  ship("freighter");
  const linings = must("find skyliners:canopy_lining 128 2").rows;
  expect(linings.length === 1, "freighter: her canopy has its lining", `${linings.length} linings`);
  const unlined = must("find skyliners:dimming_glass[lined=false] 128 1").count ?? 0;
  const lined = must("find skyliners:dimming_glass[lined=true] 128 1").count ?? 0;
  expect(unlined === 0 && lined > 0, "freighter: her canopy's glass is drawn from outside only, its lining drawing its inside",
    `${lined} lined, ${unlined} not`);
  expect((must("find skyliners:canopy_frame 128 1").count ?? 0) > 0, "freighter: the struts between her canopy's panes are its frame");
  const [dimmer] = must("find skyliners:window_dimmer 128 1").rows;
  const [head] = must("find skyliners:autopilot 128 1").rows;
  if (!dimmer || !head) {
    expect(false, "freighter: her cockpit has its dimmer and control head");
    return;
  }
  const nose = must(`block ${head.at_abs.join(",")}`).props.facing ?? "north";
  const facing = must(`block ${dimmer.at_abs.join(",")}`).props.facing ?? "east";
  const seat = pilotSeat();
  const clear = skyThroughCanopy(seat, nose);
  pressDimmer(dimmer.at_abs, facing, ...DIMMER_DARKEST);
  const dimmed = skyThroughCanopy(seat, nose);
  expect(dimmed < clear * CANOPY_DIMMED_SHARE, "freighter: dimmed, the sky ahead through her canopy darkens from her seat",
    `luma ${round(clear)} clear, ${round(dimmed)} dimmed`);
  pressDimmer(dimmer.at_abs, facing, ...DIMMER_CLEAR);
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
  for (const kind of ["climb", "boost", "jump"]) {
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

// A freighter's console is as solid as it is drawn: walked at from her control head, her scanner module's desk stops
// the player at its near edge, just short of its block's middle. With the fault its whole block was solid, and its
// desk's empty room in front of it stopped them half a block short, and took clicks meant for what is beyond.
function checkFreighterConsoleSolid() {
  ship("freighter");
  const [scanner] = must("find skyliners:console_scanner 128 1").rows;
  const towardHead = scanner && STEPS[must(`block ${scanner.at_abs.join(",")}`).props.facing ?? "north"];
  if (!scanner || !clearWay(scanner.at_abs, towardHead, 1)) {
    expect(false, "freighter: her console has its scanner module, with open floor between it and her control head");
    return;
  }
  const { along } = bumpInto(scanner.at_abs, towardHead, DESK_FROM);
  expect(along < DESK_STOPS_WITHIN && along < DESK_FROM - BUMP_MOVED, "freighter: walked at from her control head, her scanner module stops the player at its desk",
    `${along} blocks short of its block's middle`);
}

// A freighter's outside is as solid as it is drawn: dropped onto her laser cannon, the player stands on its housing
// over its pedestal; onto her big satellite dish, on the middle of its bowl over its mount; and walked at from beside
// her parcel scanner in her hold, its gantry's post stops them. With the fault they fell through the housing and the
// bowl onto what is under them, and the post stood out past the scanner's block with nothing there.
function checkFreighterOutsideSolid() {
  ship("freighter");
  for (const [block, top, what] of [["laser_cannon", CANNON_HOUSING_TOP, "her laser cannon's housing"], ["big_satellite_dish", DISH_BOWL_TOP, "her big dish's bowl"]]) {
    const [found] = must(`find skyliners:${block} 128 1`).rows;
    const stood = found && landsOn(found.at_abs);
    expect(stood !== undefined && Math.abs(stood - top) < STANDS_WITHIN, `freighter: dropped onto ${what}, the player stands on it`,
      found ? `${stood} blocks over its block, ${top} wanted` : "not built");
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
  const ships = must("sable:list 2048 side=server").rows.length;
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
  const shipsAfter = must("sable:list 2048 side=server").rows.length;
  expect(shipsAfter === ships, "freighter: no piece of her is shed as a ship of its own", `${shipsAfter} ships, ${ships} before`);
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
// still aboard, her lowest block kept well clear over the pillar's top rather than in it.
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
  const longest = must("perf stop").summary?.server?.mspt?.max ?? 0;
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
  must("skyliners:autopilot land");
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
  must(`server tp @p ${px + 0.5 + dx * 1.5} ${py} ${pz + 0.5 + dz * 1.5}; wait 10t; use ${panel.at_abs.join(",")}; wait ${IRIS_TICKS}t`);
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

// The beach's tiki torch and the floor window's panel, put up by hand on the ground and on a wall, each take a shape of
// their own there: a torch standing tall or held out on a bracket, a panel on its post or flat in its rim.
function checkBeachOnWalls() {
  const [x0, z0] = [nextSite++ * SITE_APART, STAND_AWAY];
  creative();
  must(`server tp @p ${x0} ${ground} ${z0 - 3}; wait ${CHUNKS_TICKS}t`);
  must(`server fill ${x0 - 1} ${ground} ${z0 + 3} ${x0 + BEACH_ON_WALLS.length * STAND_APART} ${ground + STAND_HIGH} ${z0 + 3} stone`);
  BEACH_ON_WALLS.forEach((id, i) => putUpEveryWay(id, x0 + i * STAND_APART, z0, ["floor", "wall"]));
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
  must(`skyliners:autopilot fly altitude=${head[1] + FREIGHTER_CLIMB} speed=0`, BUILD_SECONDS);
  findShipFlying("skyliners:engine_grille");
  const up = waitForReport(freighterReport, (report) => report.phase === "cruise", FREIGHTER_UP_TICKS);
  expect(Boolean(up), "freighter: she lifts off and hovers for her repairs, the player aboard by her repair board");
  if (!up) {
    repairsReport("every=0");
    return;
  }
  const since = now();
  const broken = waitForReport(repairsReport, (report) => report.waiting > 0, REPAIR_FAULT_WITHIN);
  const noise = heard("skyliners:fault_*", since, LOOK_EVERY);
  expect(Boolean(broken) && Boolean(noise), "freighter: hovering, a fault goes off within the time set, crackling or hissing",
    JSON.stringify(broken ?? repairsReport()));
  repairsReport("every=0");
  const [fault] = faultyOf(broken);
  if (!fault) {
    return;
  }
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
  "freighter-gunner-takeoff": { run: checkFreighterGunnerTakeoff, slow: true, about: "a freighter set to fly from her gunner's seat lifts off" },
  "freighter-turret": { run: checkFreighterTurret, slow: true, about: "a freighter's core ladder is climbed to her gunner's seat, whose cannon strikes a creeper and blasts stone, breaking none of her" },
  "freighter-rockets": { run: checkFreighterRockets, slow: true, about: "a freighter's cannon launches its rockets at a creeper and blasts stone, breaking none of her, the sight showing its pods load again" },
  "laser-crossbow": { run: checkLaserCrossbow, about: "the laser crossbow's bolt is seen leaving it, strikes a creeper, never a cow, and it vents after three quick shots" },
  "laser-crossbow-flight": { run: checkLaserCrossbowInFlight, slow: true, about: "the laser crossbow's bolt, fired aboard a flying freighter, is seen leaving it" },
  "freighter-rooms": { run: checkFreighterRooms, about: "a freighter's crew quarters have their bunks, her lounge its booth with leg room, her hold its cargo and scanner" },
  "freighter-tardigrade": { run: checkFreighterTardigrade, about: "a freighter's small tardigrade lives in her lounge's tank and comes to its hatch to munch moss" },
  "fleet-on-freighter": { run: checkFleetOnTheFreighter, about: "the airliner, zeppelin and rocket paintings hang whole on the freighter" },
  "birthday-greeting": { run: checkBirthdayGreeting, slow: true, about: "a freighter greets whoever the client config names as they come aboard on their birthday, once, and nobody the day before" },
  "freighter-lights": { run: checkFreighterLights, about: "a freighter is built with wall washers round her rooms, her club and ring corridor lit" },
  "freighter-band": { run: checkFreighterBand, about: "a freighter's club has a bar and a band that plays her song to whoever comes near, silent as they go" },
  "freighter-spookie": { run: checkFreighterSpookie, about: "a freighter's Spookie greets, purrs, eats a cookie, is never hurt and walks its rounds" },
  "freighter-leds": { run: checkFreighterLeds, about: "a freighter's rooms each have LED strips and a panel, each room a colour and a floor of its own, out to her prongs' tips and her cockpit" },
  "freighter-console": { run: checkFreighterConsole, about: "a freighter's console curves round her seats, its boost says no on the ground and its toggles click" },
  "freighter-solid": { run: checkFreighterSolid, about: "a freighter's jump drive rings, band and sun loungers stop whoever walks into them" },
  "freighter-console-solid": { run: checkFreighterConsoleSolid, about: "a freighter's console desk stops whoever walks at it where it is drawn, not its whole block" },
  "freighter-outside-solid": { run: checkFreighterOutsideSolid, about: "a freighter's cannon housing and big dish are stood on, and her parcel scanner's gantry stops whoever walks into it" },
  "souvenir-shapes": { run: checkSouvenirShapes, about: "the rocket and freighter souvenir models are clicked only where they are drawn" },
  "freighter-cockpit-reach": { run: checkFreighterCockpitReach, about: "a freighter's climb, boost and jump buttons are all in reach from her pilot's seat" },
  "freighter-boost": { run: checkFreighterBoost, slow: true, about: "a freighter's boost button fires her mains flat out a while, then rests, and she slows back" },
  "freighter-climb-boost": { run: checkFreighterClimbBoost, slow: true, about: "a freighter's climb button fires her lift flat out, and she climbs straight up hard and holds the height" },
  "freighter-slam": { run: checkFreighterSlam, slow: true, about: "a freighter's slam button drives her straight down hard, strikes the ground with a crash, and sets her down unharmed, nobody aboard hurt" },
  "freighter-jump": { run: checkFreighterJump, slow: true, about: "a freighter jumps through hyperspace to her pin, clear over a pillar standing on it, everyone aboard with her" },
  "console-mounts": { run: checkConsoleMounts, about: "each console module set up by hand stands on a floor, a wall or a ceiling, facing whoever put it up" },
  "freighter-canopy": { run: checkFreighterCanopy, about: "a freighter's canopy is smooth from inside, drawn by its lining, and dims from her seat" },
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
  "freighter-gallery": { run: checkFreighterGallery, about: "a freighter's cockpit tube has the fleet's models on its ledges, her port prong's tunnel space posters, and no engine stands out in her prongs" },
  "freighter-engine-bays": { run: checkFreighterEngineBays, about: "a freighter's thrusters in her prongs' tips are boxed in, out of reach from the tunnels, their bays unbroken by a pickaxe" },
  "freighter-smuggler-hold": { run: checkFreighterSmugglerHold, about: "a freighter's loose rivet chimes, its panel knocks hollow, opens onto a den of loot, shuts by itself and opens from inside" },
  "secret-panel": { run: checkSecretPanelByHand, about: "a secret panel set into a wall by hand opens and shuts at its loose rivet, and a rivet goes on a wall, a floor and a ceiling" },
  "freighter-lookout": { run: checkFreighterLookout, about: "a freighter's port prong has seats by its portholes and a nook at its tip facing a screen" },
  "beach-on-walls": { run: checkBeachOnWalls, about: "a tiki torch and a floor window panel go up on a floor and on a wall, each in a shape of its own" },
  "freighter-beach": { run: checkFreighterBeach, about: "a freighter's beach: her round floor window in its sand, opened from its panel down to her belly's glass, her palms in the open" },
  "freighter-exterior": { run: checkFreighterExterior, about: "a freighter is built in her own hull with her outside, and each outside fitting goes up on a floor, a wall or a ceiling" },
  "freighter-exterior-flush": { run: checkFreighterExteriorFlush, about: "every piece of a freighter's outside meets her hull face to face, no gap under it" },
  "freighter-exterior-lift-off": { run: checkFreighterExteriorLiftOff, slow: true, about: "a freighter lifting off leaves none of her outside behind" },
  "freighter-legs": { run: checkFreighterLegs, slow: true, about: "a freighter's legs draw up into her belly once she is clear, and are down again before she lands on them" },
  "freighter-fixed-legs": { run: checkFreighterFixedLegs, slow: true, about: "a freighter built with fixed legs, as before they drew up, flies and lands on them as ever" },
  "freighter-robot": { run: checkFreighterRobot, about: "a freighter's deck robot chirps hello as someone walks up to it, and trills when touched" },
  "freighter-lake": { run: checkFreighterLake, about: "a freighter set to land at a pin in a lake holds over it and says she finds no ground", slow: true },
  "impact-sounds": { run: checkImpactSounds, about: "a freighter dropped onto the ground crunches and groans, and shoved along it grinds", slow: true },
  "freighter-flight": { run: checkFreighterFlight, about: "the freighter lifts off, hovers level and roars, not spooling up as she holds her spot, and lands by herself", slow: true },
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
