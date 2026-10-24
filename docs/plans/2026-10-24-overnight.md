# Overnight plan, 24–25 October 2026

Work Jan asked for while away: finish the retractable landing gear, play-test with the blockpilot session's MCP mod,
and add air pockets, lightning strikes, a laser path clearer and a performance review. Each feature has its own
section below: what the player gets, how it is built, how it is tested, and its status. Decisions taken along the way
(by a panel of agents where a question came up) are in the decision log at the end, and the outcome of the night is
in the last section.

## Ground rules for the night

- **Branch:** everything lands on `feat/overnight-gear-weather-laser`, branched from `main`, never pushed. One atomic
  commit per feature or fix, Conventional Commits subjects (`feat(airframe): …`, `fix(autopilot): …`), each reviewed
  with the `review-changes` skill first, and a `chore(release): bump version to 0.1.0-alpha.N` after each feature that
  gets play-tested.
- **Config:** every new feature has a server (or client) config switch, and **all default to on** (Jan's instruction,
  overriding the AGENTS.md default of off for experimental behaviour).
- **Children:** every cue also gets a picture, a colour or a sound, never text alone.
- **Testing:** headless over RCON against a dev server (`sh ./gradlew runServer`, see Testing below) for server logic,
  and in the dev client with blockpilot (MCP, screenshots, player actions, performance capture) for what needs eyes.
  Whatever could not be verified in game is said plainly in the outcome.
- **Questions:** decided by a panel of three agents (game design for young players, engineering and performance, and
  realism), majority vote, logged below.

## 1. Retractable landing gear

**Player:** a gear lever on the flight deck (a lever with a small wheel-shaped knob): use it to raise the gear, sneak
and use it to lower it. Beside it three lights, one per gear group: green when down and locked, red while moving, dark
when up. Raising takes about 8 seconds: doors open under the belly, each leg folds up into it, the doors close flush;
lowering is the same backwards in about 6. It will not raise on the ground (it bumps back with a buzz). The autopilot
raises the gear once climbing away after take-off and lowers it as it starts down to land, with a "gear down" callout
where a voice exists (otherwise a tone); Take Off lowers it first if it is up. Below about 30 blocks and sinking with
the gear up, a warning horn sounds and the lights flash red, but a pilot can still land on the belly and slide; the
gear then comes down as soon as there is room under it (an Unstuck hop, say).

**Build:**
- Step 0 (done, 24 Oct): Offroad's wheel mount is a Sable `BlockEntitySubLevelActor` that casts to the terrain each
  physics tick, and Sable's `LevelPlot.onBlockChange` adds or drops the actor as its block changes. Live test over
  RCON: mounts taken away drop the airliner 1.16 blocks onto its legs, and mounts put back (on the ground, and while
  falling from 25 blocks up) carry it on its wheels again. So retracting is taking real blocks away and putting them back.
- `GearBayBlockEntity`: one hidden block at the top of each leg, inside the hull, set up through a `Fitting` so it
  turns with the placement. Retracting stores every block of its leg below the hull (offset, state, block entity NBT:
  the mounts with their tire and suspension, the relays, the leg, the call button) and takes them away; extending
  puts them back where nothing else has taken their place. State saved by name (`DOWN`, `RETRACTING`, `UP`,
  `EXTENDING`) with a fallback, and synced to clients through block entity data (no new payload).
- `GearLeverBlock` with its block entity: offsets to every bay (turned and mended like the other controls), a
  weight-on-wheels lock read through `SableFlight`, and the indicator lights from the bays' states.
- `GearBayRenderer` (client): draws the doors and the folding leg with its tires while the gear moves; nothing when
  it is down or up.
- Fixes the gear needs: `AirlinerShape` measures the gear from the bays' stored blocks when it is up, and the controls'
  link mending (`Turning.mend`) leaves links into a stowed leg alone.
- Autopilot: gear up in the take-off climb, gear down at the start of Land and at final for Land at the Pin, Take Off
  waits for the gear like it waits for the airstairs (`Stowing`), and `Callouts` gains the gear horn.
- Config: `retractableGear` (server, default on).

**Test:** RCON: build, assemble, retract and extend on the ground (refused) and in flight (lifted with
`sable teleport`), check blocks and the height it rests at, autopilot take-off and landing with gear. Client with
blockpilot: the lever, lights and animation, screenshots.

## 2. Gear sounds like a real airliner's

**Player:** raising the gear sounds like it does from a real cabin: the hydraulic pump whining up, the rumble of the
wheels braked to a stop, the heavy thump of the gear coming into its wells and the doors closing with a clunk, the
airflow noise dropping away. Lowering: the uplocks letting go with a clunk, the roar of the air rushing in as the
doors open, the gear dropping and locking down with a heavy thud, then the rumble of the gear in the airstream.

**Build:** new synthesised sounds in `tools/generate_sounds.mjs` (`gear_retract`, `gear_extend`, `gear_horn`), played
at the bays so they come from under the floor, with subtitles. The generator gains an `oggenc` fallback for FFmpeg
builds without libvorbis (Homebrew's FFmpeg 9 has none); `oggenc` is the same reference libvorbis encoder.

**Test:** listen-through is not possible headless; check the files' lengths and levels and that the game plays them
(server log and blockpilot). Jan to listen in the morning.

## 3. Air pockets (Luftlöcher) at height

**Player:** high up, the airliner now and then drops into an air pocket: a sudden sink of a few blocks in a second or
two, the stomach-lifting moment of near weightlessness for anyone standing (they float up off the floor), loose balls
and trolleys lifting, cameras dipping, a thud as the air catches it again. The higher it flies, the more often; in a
storm, more often and deeper. Seated and belted passengers stay put, so the seatbelt signs matter even more. The
autopilot recovers the height.

**Build:** an `AirPocket` step in the existing `Turbulence` (one per airliner, worked by its autopilot), above a
height threshold; a downward impulse on the airliner through a new `SableFlight.push` (Sable's `RigidBodyHandle`), a
short lift for unseated entities aboard, and a client payload for the camera's drop (protocol version bump). Config:
`airPockets` (server, default on).

**Test:** RCON: lift a furnished airliner high, force pockets (dev-only trigger kept out of commits), watch its height
over ticks with `sable info`; client: feel and screenshots.

## 4. Lightning strikes

**Player:** flying through a thunderstorm, the airliner is now and then struck: a real lightning bolt cracks into a
wingtip, the nose or the fin, with a blinding flash, a deafening crack of thunder right there, the cabin lights and
the screens flickering for a moment and static on the radio. Like a real airliner it comes through unharmed: no fire,
no damage, nobody hurt.

**Build:** `LightningStrike` in `cabin/` (or `autopilot/` beside `Turbulence`), ticked by the autopilot like
turbulence: only in a thunderstorm where it rains, flying, a chance each tick that grows with height. A visual-only
`LightningBolt` at a hull point turned into the world, thunder for everyone aboard, a payload for the client's flash
and the screens' flicker. Config: `lightningStrikes` (server, default on).

**Test:** RCON `weather thunder`, a lifted airliner, a dev-only trigger; client screenshots of the flash.

## 5. Laser path clearer

**Player:** a laser switch on the flight deck (a red button under a flip-up guard, with a laser picture). Armed, a
turret drops out of a hatch under the nose and fires rapid short laser bolts at whatever lies in the airliner's flight
path ahead: blocks are vaporised and hostile mobs zapped, each bolt a bright beam with a sharp hiss. Disarmed, the
turret folds back into the nose.

**Build:**
- Turret block entity under the nose, placed by the layout (hull feature, before the shell pass), with its renderer
  (deploy animation) and the switch on the flight deck linked to it like the other controls.
- Server: while armed and the airliner moves, look ahead along its velocity (rays across its frontal outline, a slice
  of them a tick, so the cost is bounded), pick the nearest obstacle in the corridor, and fire a few bolts a second:
  each removes a small cluster of blocks or hurts one hostile mob (a `laser` damage type). Never players, villagers,
  pets or other passive mobs; never block entities, unbreakable blocks or another airliner's blocks; only where
  `mobGriefing` allows, and a `BreakEvent` from a fake player first so claim mods can say no.
- Client: a payload per bolt (world from and to), drawn as a short-lived beam with a glow and impact particles, and a
  synthesised `laser_hiss`. Protocol version bump.
- Config: `laserPathClearer` (server, default on), plus its range and bolts a second.

**Test:** RCON: armed airliner moving toward a wall of blocks and a zombie, count blocks gone and the mob's health;
check the bounds (blocks a second), refusals with `mobGriefing false`. Client: screenshots of beams.

## 6. Performance review

**Method:** profile a realistic session before and after: a furnished airliner parked with players aboard, in cruise,
landing at a pin, and three airliners at once. Server: Java Flight Recorder on the game's JVM (`jcmd <pid> JFR.start`,
`jfr print`/`jfr summary`), vanilla `/debug` reports, and blockpilot's capture (MSPT, per-type block entity and entity
timings). Client: blockpilot's capture (FPS, 1% lows, block entity renderer time by type, memory). Read the hottest
Aerolarge frames, check each against the Cost rule in AGENTS.md (no unbounded scans, `Loaded<T>`, work spread over
ticks, renderers culling what the camera cannot see), and fix what is clearly wasteful in separate `perf(...)` commits,
each measured before and after.

**Output:** a findings table (what, where, cost, fix, measured gain) in the outcome below.

## Testing setup

- Dev server: `run/server.properties` binds the game port and RCON to 127.0.0.1, offline mode, flat peaceful world;
  mods copied from the CurseForge instance into `run/mods`; a small Node RCON client in the session scratchpad.
- Headless recipe: `/aerolarge build`, `/sable assemble area`, `/sable forceload add <uuid>`, `/sable info <uuid>`;
  world-to-plot offsets via a marker summoned `in_sub_level`; `sable teleport <uuid>` to lift (never `@e`).
- blockpilot (another Claude session) is building an MCP client mod with player actions, screenshots, JSON queries
  and performance capture; it runs in `runClient` single-player.

## Decision log

| # | Question | Decided | By |
|---|----------|---------|----|
| 1 | Gear control | A gear lever with indicator lights | Jan |
| 2 | Autopilot and the gear | Raises it after take-off, lowers it to land | Jan |
| 3 | Landing with the gear up | Warning horn only; belly landings allowed | Jan |
| 4 | How retracting looks | Animated legs, doors flush with the hull, no wells cut into the hold | Jan |
| 5 | New features' config default | On | Jan |

## Outcome

To be filled in as the work lands.
