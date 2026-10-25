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
| 6 | Laser near the ground (it dug the runway it stood on and would dig the one it lands on) | Hold block fire while low (below 15 blocks over the ground, until 25 again), never cut below the wheels, mobs always; the switch glows amber while holding, with a beep | Panel: design A, engineering refined A, realism preferred a level aim |
| 7 | Engines' plasma plume (46% of the render thread in flight) | Server setting `enginePlume`, default none; control jets always none | Panel: unanimous |

## Outcome

Everything planned landed on `feat/overnight-gear-weather-laser` (never pushed), alpha.66 to alpha.71, one commit per
feature or fix, and every feature was play-tested: headless over RCON on a dev server, and in the dev client with
blockpilot (screenshots, frame bursts, camera placement, the sound log, per-tick samples, perf capture).

### 1. Retractable landing gear: done, verified in game
- Lever on the panel right of the autopilot screen, lamps green/red/dark; legs fold in 8 s, lower in 6; refused on
  the ground with a buzz; gear horn and flashing lamps coming down with the gear up; belly landings allowed and the
  legs lock once there is room again.
- Verified: blocks taken away and restored with their NBT (tires, suspension, relays, call button), no item drops;
  refusing on the ground; belly landing (3.2 blocks lower) and recovery; autopilot take-off raises the gear at 10
  blocks, Land lowers it and the airliner lands, brakes and parks on it; animation frames (legs swing in, belly clean);
  lever and lamps from the seat; layout at scales 1 to 4 (scale 1 has no flight deck, so no lever).
- Fixed after play-testing: the lamps were hidden behind the knob from the seats; the gear sounds never reached the
  cabin (the server sends a sound only 16 blocks per unit of volume; found with blockpilot's sound log); a belly-landed
  airliner counted as flying (nose up, radio altitude 3.26) and "landed" itself unattended, sliding a kilometre.
- Not done: a "gear down" voice callout (the voices need Windows' speech synthesis); the autopilot's lever movement
  and the hydraulics are the cue instead.

### 2. Gear sounds: done
- `gear_retract` (8.6 s) and `gear_extend` (6.8 s) synthesised: uplock clunk, air roar, hydraulic moan, wheels braked
  to a stop, thumps into the bays, doors shutting; `gear_horn`. Checked by their loudness over time and frequency
  bands, and that the client plays them from the legs at volume 3. Not listened to: Jan, please judge by ear.
- Tooling: Homebrew's FFmpeg 9 has no libvorbis, so the generator falls back to `oggenc` (installed vorbis-tools) and
  takes sound names to write only those.

### 3. Air pockets: done, verified in game
- Above 60 blocks: a sudden drop of a few blocks with the cabin's rattle and a thud, standing players float up, the
  view dips (10.6 degrees in a storm), the autopilot recovers. Verified with the airliner's vertical speed (-8 to -16
  blocks a second), the player's upward kick, the camera pitch and the sound in the client.

### 4. Lightning strikes: done, verified in game
- In a thunderstorm, about once a minute (twice above 150 blocks): a visual-only bolt at the hull, mostly at the
  extremities; white flash, two dark flickers, static on the flight displays, radio crackle. Verified with frame bursts
  (flash, static, flicker) and the sound log (thunder 5 blocks over the airliner).
- Fixed in testing: the screens' static drew into a finished buffer and crashed the client ("Not building!").

### 5. Laser path clearer: done, verified in game (standing and lifted high, physics paused)
- Switch on the console (red while clearing, amber while holding low), turret drops out under the nose, five bolts a
  second with beams, sparks and a hiss; hostile mobs first, then blocks, the clearing spreading across the obstacle.
- Verified: zombies killed, villager unharmed, a chest set in the wall left standing (alone, in mid-air), mobGriefing
  and claims honoured in code, nothing cut on the ground (holding, amber), a floating wall cleared from 40 blocks up
  (1,038 blocks in 15 s, red).
- Fixed in testing: the lowest rays started inside the ground and dug lines along the runway; rays alone only drilled
  tunnels (now the clearing spreads, with whole-cube bites); the spread reached below the wheels (now bounded by the
  airliner's real outline).
- Not tested: firing at real terrain while flying at speed.

### 6. Performance review: done, three fixes
Measured with blockpilot's perf capture and Java Flight Recorder: a furnished airliner parked, assembled, and cruising
at 50 blocks a second with the player in the cabin.

| Finding | Cost before | Fix | After |
|---------|-------------|-----|-------|
| Engines' plasma particles (Create Propulsion default) | 46% of the render thread; 1,806 particle packets/s (~300 KB/s per player aboard) | `enginePlume` setting, default none; control jets none | no particle packets; cruise 70 to 90 FPS |
| Control jets flicking a signal step every tick | 613 block entity packets/s | ignore one-step wobbles | about 250/s (engines' thrust) |
| Control surface panels drawn behind the camera | ~10% of the render thread; 0.25 ms/frame from the cabin | skip panels wholly behind the camera | 0 ms/frame from the cabin |

- Server side is cheap: parked, every Aerolarge block entity costs 0.02 ms a tick or less; cruising, the autopilot
  0.44 to 0.77 ms a tick, everything else 0.05 or less.
- What remains is mostly not Aerolarge's: streaming new terrain at 50 blocks a second (1,700 to 2,300 section rebuilds
  a second, server ticks of 14 to 17 ms with spikes, 1% lows near 12 FPS). Next candidates on the client: passenger
  screens (0.3 to 0.55 ms a frame), plug doors (0.2 to 0.3), Offroad's wheel mounts (0.3 to 0.5), engine intakes.

### Worth knowing
- New features only appear on airliners built tonight or later (gear, laser, plumes): build a new one to try them.
- The gear bay and the laser turret replace single skin blocks after the shell pass, as the gear legs always have,
  rather than going in before it as AGENTS.md asks of hull features.
- Seen while testing, not caused tonight: a heavy furnished airliner holds its cruise some 10 to 20 blocks below the
  height set (it did so before the jet change too); probably its climb limit in thin air.
- Dev setup left in place (all gitignored): `run/server.properties` with RCON on localhost, `run/eula.txt` accepted,
  the mods and blockpilot in `run/mods`. Installed with Homebrew: ffmpeg and vorbis-tools.
