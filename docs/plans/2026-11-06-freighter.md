# Freighter plan, from 6 November 2026

The fourth blueprint: a battered saucer freighter, one deck round a central core, flown on thrusters that hold her up
and push her along, mostly by her autopilot. Jan asked for her on 2026-11-06, after the Freighter Painting teased her
aboard every ship; he asked for a thorough plan first and for every decision to be taken by a panel of agents, not
him. Five panellists read the code for their own area (flight; airframe and architecture; rooms and play; rights and
sound; the ramp, turrets and creatures) and reported; their reports are summed up here so the plan stands without
them, and where they disagreed the lead decided, saying so in the table.

## Jan's requirements

1. Real flight physics: thrusters make her hover and move forward.
2. An autopilot like the others'.
3. Separate, distinct rooms comparable to the rocket's, each with a touch of its own.
4. Reusable logic and common components extracted wherever possible: touch displays are the existing touch panel (the
   lift's and the rover's).
5. A boarding ramp of her own with realistic animation and a visible mechanism, worked by a button.
6. A cockpit close to the original's, and a laser turret a player aims and fires by hand.
7. A smaller version of the rocket's tardigrade.
8. A furry giant co-pilot, as long as it does not infringe; otherwise called a Spookie, with a few parts changed.
9. A laser crossbow.
10. A cantina-band-like song in the club, as long as it does not infringe.
11. Her space: about half the rocket's, on one deck.
12. A thorough plan before implementation; ideas of our own, from the original and from this mod's style.

Added by Jan on 2026-11-06, once work had begun:

13. Dimmable windows, as the rocket has, though not necessarily her big oval ones.
14. Cockpit glass with smooth joins: no edgy bits or gaps standing out.
15. Thrusters that are very impressive, and loud from outside.
16. Droids aboard, beeping.

## Ground rules

- **Main.** Everything lands on `main`, atomic commits with Conventional Commits subjects, each reviewed with
  `review-changes` and `diff-check` first. A `chore(release): bump version to 1.2.0-alpha.N` follows every build handed
  out. Nothing is pushed without Jan.
- **The other ships must not change.** Every extraction is behaviour-free for the airliner, the zeppelin and the rocket,
  committed on its own before the freighter uses it, and checked: the build, `skyliners:layout` fingerprints of every
  layout unchanged, and the play-test script's checks for what it touches.
- **Reuse before writing anew.** The freighter takes the autopilot, its screen and map, the build pipeline, the
  thrusters and relays, the cockpit's screens and console modules, cameras, the livery selector, the touch panels,
  seats, bunks, galley, the club's bar and bartender, the tardigrade, the airstairs' machinery and hatches, the gear
  bays, the laser bolts, and generalises them where they assume a rocket, never copying.
- **Modular.** Every room and feature is a piece of its own with its own config switch, removable with its line.
  Anything experimental defaults to off (AGENTS.md).
- **Players.** Every cue has a picture, a colour or a sound, never text alone; fun for adults too, never babyish.
- **Measure, never claim.** Every number marked *to measure* is measured in game before it is relied on, and what was
  not verified is said plainly.
- **Rights.** See "Rights" below; it binds names, files, docs and commit subjects alike.
- **AGENTS.md's rules, named here so no milestone forgets them:** a payload's wire format changed bumps the protocol
  version (`event.registrar`, "31" today), at least for `FlyFreighter`, `GunnerTrigger` and `LaserBolt`'s style byte;
  every new block goes through `tools/generate_resources.mjs` (blockstates, models, loot, lang, recipes, tags), is
  light as the airframe is (`#sable:super_light`), and her lined cover and lining panels weigh what the rocket's do;
  every block entity that renderers draw or ticks look for is tracked with `Loaded` and its renderer culled with
  `Sight` (the turrets, holo table, bandstand, jump drive's rings, dice, dish, scoreboard, the Spookie's keeper and the
  drones' launcher, not only the ramp); `skyliners:assembly` is run over her before she first flies; saves stay
  readable, a missing key defaulted; client classes stay in `client/`.

## Rights

Not legal advice: a practical judgement for a free mod on CurseForge. Lucasfilm leaves most fan art alone but has shut
down fan games and enforced names from the films (the 2018 Sabacc case); "Wookiee" and "Droid" are registered
trademarks. The worst case is a takedown of the whole CurseForge project, so the freighter stays one self-contained
module that a single release can take out.

- **Never used, anywhere** (ids, class names, lang, docs, the CurseForge page, commit subjects, which become release
  notes): Star Wars, Millennium Falcon, Falcon as her name, YT-1300, Corellian, Kessel, Wookiee, Chewbacca, Chewie, Han,
  Lando, droid, Jedi, Sith, the Force, lightsaber, bowcaster, blaster, dejarik, holochess, sabacc, quad laser; Aurebesh
  lettering and every logo; film quotes ("never tell me the odds", "punch it", "bad feeling about this").
- **Music:** nothing of the Cantina Band, neither melody, chords, arrangement nor motif, and no close copy of its feel.
  Our song is a minor swing in another key, form and colour (see "Sound"). Any change to it is checked against the
  famous themes; the first draft's opening matched another film theme and was rewritten.
- **Sounds:** nothing recorded or resampled from the films, games or toys, and nothing imitating Ben Burtt's signature
  sounds (the falling "pew", the fighter's scream, the hyperdrive winding down, the droid's whistles, the famous
  growl). Everything is synthesised in `tools/generate_sounds.mjs` or comes from the CC0 and public-domain sources
  already used.
- **Fine to use:** freighter, saucer, laser, laser crossbow, turret, light speed, jump drive, hologram, smuggler's
  hold, star streaks, furry giant, co-pilot.
- **Her likeness:** her own name, her own measurements (never traced from studio plans or fan deck plans), her own
  livery and panel pattern, our own greebles from Create's pipes, cogs and vents, twin-barrelled guns rather than quad,
  and a cockpit built from this mod's own family of controls.
- **What is left:** *The freighter's outline, a round saucer with two forward prongs and a cockpit tube on her side, is
  recognisably modelled on a famous film ship whose design Lucasfilm owns; our own name, livery, interior and sounds
  lower the risk but do not remove it, and if Lucasfilm or CurseForge objects we take the freighter out in the next
  release, which her self-contained module allows.*

## The freighter as a player meets her

- **Her name:** the Freighter, built by the Freighter Blueprint; "a battered saucer freighter". Her call sign on the
  radio is Magpie, a bird that collects shiny things.
- **Her size:** a disc 25 m across (50 blocks at the default scale 2) with two prongs reaching forward, 34.75 m long in
  all, 7.8 m tall to the top of her radar dish; her cockpit pod out on a tube at her starboard front edge; a ball turret
  on top and another under her belly; her engines' blue glow in a strip round her stern; docking collars either side;
  standing on five landing legs; her boarding ramp coming down from her belly on the starboard side.
- **Inside:** one deck round a core with the turrets' ladder in it, a ring corridor round the core, and six rooms out to
  an outer bulkhead, about 1,190 blocks of floor, half the rocket's 2,360 (see below). Battered and lived-in where
  the rocket is showroom-clean: quilted off-white padding over grey ribs, floor grates with amber-lit pipes under them,
  scuffed and patched panels. Her running joke is that things work again once they are thumped.
- **Flying:** her autopilot lifts her straight up off her legs, flies to a pin on its map or on a heading, slides onto
  the pin, finds level ground and settles straight down on it. She can climb to space, as the rocket does.

### Her outline, in metres (`FreighterShape`)

The rocket's frame: y up from the legs' feet, the disc's axis through x 0, z 0, her prongs to the north, starboard to
+x. Measured from the published figures, never traced from studio or fan plans.

| Part | Numbers |
|---|---|
| All of her | 34.75 long (the prongs' tips 22.25 ahead of the axis), 25.6 wide with the docking collars, 7.8 tall to the dish's top |
| Disc | 25.0 across (R 12.5); its middle plane 3.4 up; a rim band 1.8 tall; a lens over and under it, f = 1 − (r/R)²: top 4.3 + 1.8f (6.1 at the axis), belly 2.5 − 1.0f (1.5 at the axis); steepest 16° |
| Prongs | the gap between them 3.6 wide (\|x\| ≤ 1.8), its back wall 8.5 ahead of the axis; each from x 1.8 out to 5.0 at the tip, widening to 6.6 where it meets the disc; flat top and bottom, 2.6 thick at the root, 2.0 at the tip |
| Cockpit | a tube of radius 1.5, its axis 4.0 up, along z at x +10 from inside the disc (z −3, where it crosses her outer bulkhead, standing a little proud of her top as it runs out over her rim, so the way in has 4 rows of headroom) to −12; a pod of radius 2.4, its axis 4.4 up, round from −12 to −14, then a cone to its tip at −16.5 |
| Radar dish | at (+5.5, −5.5) on a 0.8 mast, 2.8 across, tilted 30° forward |
| Turrets | both on the axis: the top dome 2.6 across reaching 6.8 up; the belly dome 2.0 across and 0.7 deep, 0.8 over the ground |
| Engines | a strip 1.0 tall in the rim band, from 135° to 225° round from the bow |
| Docking collars | at about 100° and 260°, radius 1.3, standing 0.3 proud of the rim |
| Legs | five: one forward under the gap's back wall, a pair either side aft at about (±7, +7) |

`SurfaceCut` describes all of it: her parts are small solids, the union taking the least depth and that part's
outward, the gap cut out by taking the greater of the hull's depth and the gap's negated. Curved parts tilt their depth
to the surface's normal as the rocket's nose does. Her skin is the zeppelin's cover, snapped as the rocket's is, so
liveries tint it unchanged.

### Her deck at scale 2

- **Floor and headroom:** the deck's floor is row 5 (standing at 3.0 m): 5 rows of headroom at the core, 4 at 8.8 m
  out, 3 at the outer bulkhead, 9.5 m (19 blocks) out. Past the bulkhead, the wedge out to the rim is a closed void:
  the engines sit in it aft, the ramp and the docking passages cross it. Under the floor, row 4 stays open out to 7.6
  m, a crawlspace for the hidden compartments. The prongs are crawlways 2 rows tall (3 at their roots), the cockpit
  tube has 4 rows and the pod 7.
- **The rocket's floor, counted:** a story's open footprint at radius 10 blocks is 261 blocks (`SurfaceCut`'s open
  cells, more than a quarter of a block inside), 236 without the shaft; nine stories and the cockpit come to about
  2,360. **Her target, half:** about 1,180. **Her floor:** a core of 5 by 5 (the turrets' ladder, not counted), a ring
  corridor 3 wide (72), six sectors of 60° from 6.5 to 19 blocks out (152, 158, 171, 167, 158 and 171: 977), the
  cockpit's tube and pod (about 70), the prongs' crawlways (2 × 44) and the passages out past the bulkhead (about 50):
  about 1,260, or 1,190 less some 70 blocks of walls.
- **Her rooms,** clockwise from the bow (0°, starboard 90°):

| Sector | Room | What makes it hers |
|---|---|---|
| Bow, 330°–30° | Cargo hold, with the prongs' crawlways | a loading hatch onto the gap between the prongs; crates, nets, pallet racks; hidden compartments under the floor grates; the training drones' launcher in the port prong, tool lockers and a sensor rack in the starboard one |
| Starboard fore, 30°–90° | Entry hall and the cockpit's passage | the ramp's head (its spur at about 75°), the tube to the cockpit leaving at about 53°, the laser crossbows' rack, a ship's map board |
| Starboard aft, 90°–150° | Lounge and galley | the horseshoe couch round the Holo Game Table, the small tardigrade's porthole tank, the turrets' scoreboard, a wall of blinking screens; the galley a tight U off it; the starboard docking airlock at about 100° |
| Aft, 150°–210° | Engineering | the jump drive's core, a cage of stacked glowing rings through floor and ceiling; the dented thump plate; coolant pipes, shelves, a half-dismantled robot throwing sparks; a window aft onto the engines' glow; an escape pod past the bulkhead either side |
| Port aft, 210°–270° | Crew quarters and med bay | bunk alcoves in the curving hull behind curtains, lockers, the Spookie's oversized hammock; the med bay's bunk under a scanner arm; the port docking airlock at about 260° |
| Port fore, 270°–330° | The Spacers' Club | a scruffy amber, smoky bar: counter, stools, taps, the robot bartender, tables, neon; a bandstand of a robot jazz trio of the deck robots' family on upright bass, keyboard and drums playing "Long Haul Swing" |
| Core | The turrets' well | a rung ladder up to the top gun and down to the belly gun |

- **Finding the way without reading:** every room opens off the ring through a hatchway with a picture plate, the
  room's icon on its colour, in the lift board's icon language.
- **Walls** inside are one padded bulkhead block (`PaddedBulkheadBlock`), the original's grey quilting; each room's
  own finish goes on its floor, the lined underside of her hull and the outer bulkhead (`FreighterFinish`,
  `FreighterLinedCoverBlock`, `FreighterLiningPanelBlock`, `FreighterFloorBlock`), her own blocks rather than more
  finishes on the rocket's.
- **Scale:** she is always built at scale 2, whatever the server's, as the zeppelin ignores it: at 1 her hull leaves 2
  rows of headroom and at 1.5 only 3, and at 3 or 4 her weight grows with the scale squared or faster while twenty
  thrusters do not, so she would not lift. One scale also means every room, route and thruster is tuned and tested once.
- **Liveries:** six on her selector, three of her own over the rocket's silver, polar and midnight: **Tramp** (built
  in), warm grey 0xB8B4AA in plates on a polar grid (rings 1.5 m apart, 12° each), about one plate in seven red-brown
  0x8A4B38 and one in nine a darker grey, a few rust streaks 0x9C5A2E running aft, gunmetal 0x5E6066 trim on the rim
  band, collars and legs; **Racer**, white with thin red stripes in pairs fanning out from the top turret, red prong
  tips and rim; **Navy**; midnight's stars go on her top. Painted through a `Livery.FreighterSpot`, her repaint
  sweeping bow to stern.
- **Config:** a `freighter` section with `rooms`, `finishes`, `ramp`, `turrets`, `trainingDrones`, `spookie`,
  `tardigrade`, `laserCrossbow`, `dish`, `corridorLights`, `jumpDrive` (the drive, its lever, its streaks and its
  failing and thumping: on), `jumpDriveTeleport` (the jump itself: off) and one switch per room; the existing
  `liveryBoard`, `cameraDisplay`, `selfDestruct` and `fleetPaintings` cover her too.
- **Build integration:** the blueprint in the creative tab's blueprints after the launch pad's, her own "freighter" and
  "freighter cockpit" sections after the rocket's (still one tab), the souvenir model after the rocket's;
  `/skyliners build <pos> <dir> freighter`, Blockpilot's `skyliners:build freighter`, `skyliners:layout freighter` and
  a `skyliners:freighter` query to sample her flight.

### The cockpit

Jan's ask 14: the canopy's glass must join her hull and its own struts smoothly, with no edgy bits or gaps. So the pod's
cone and front are drawn as smooth surfaces (a cone over a cylinder, its front a flat disc), cut in the snapped style so
glass and cover sit flush; the struts are cover cut to the same surface, never whole blocks standing proud; no pane is
left out behind another where that opens a gap; and the canopy is photographed close up from outside and from each seat
until nothing stands out, before the milestone is called done.

Close to the original's in arrangement, built from this mod's own family of controls (never the film's panel art):
the curved, padded tube with a rib every two blocks, an LED strip chasing toward the cockpit as the ramp opens; in the
pod, a glazed cone with spoked struts and two rings (her snapped skin's glass, struts left as cover), the pilot's and
co-pilot's seats forward and two jump seats behind; the autopilot's control head between the front seats, two flight
displays, the autopilot screen, the rocket's centre-console modules on their picture plates, an overhead arch of
toggle rows that click and light up, the Jump Lever plate (three handles in the thrust levers' shape under a
star-streak picture), the ramp switch, the camera display and livery selector at the co-pilot's side, the self-destruct
on the back wall, and a pair of gold dice hanging from the canopy that swing as she moves.

## Flight

Facts first, from Create Propulsion 1.1.5 (read with `javap`) and Sable 2.0.5's sources:

- A creative thruster pushes 10,000 at setting 99 and full signal, linear in signal/15, and ramps up over 10 ticks
  from 0 (`AbstractThrusterBlockEntity.STARTUP_DURATION_TICKS`). Creative thrusters ignore what stands in their exhaust
  (`calculateObstruction` always reports it clear), so short landing legs are fine. Thin air adds up to 15% thrust; the
  learnt trim takes it up.
- Sable has an angular velocity (`RigidBodyHandle.getAngularVelocity()`), a centre of mass and an inertia tensor
  (`MassTracker`) not yet in `SableFlight`; `/sable teleport` is `resetVelocity` plus `teleport`, and moves
  no riders.
- The furnished rocket weighs about 3,200 (S0, below); the freighter, half her floor, perhaps 1,500 to 2,500
  furnished (*to measure* once she stands, before her thrusters are sized).

How she flies (`FreighterPilot`, beside `RocketPilot` and `ZeppelinPilot`):

- **Lift:** four clusters of five creative thrusters in a plus, as the rocket's bells, on the diagonals at about 0.55 of
  the disc's radius: two flanking the prongs' roots, two flanking the engine block aft. Their value boxes are turned
  down (`Thrusters.thrust`) from her measured mass so she hovers at about 0.4 of full, signal about 6 of 15, a
  thrust-to-weight of about 2.5, a step of signal a few per cent of her weight; the setting is saved with her wiring
  as a zeppelin's cells' full gas is.
- **Main engines:** six thrusters facing aft in the engine strip, at her centre of mass's height. **Retros:** two facing
  forward in the prongs' tips, to brake and back up. **Lateral jets:** a pair at the prongs' roots and a pair at the
  tail, fired opposite ways to yaw and the same way to sidestep, so she hovers precisely over a pin without banking.
- **Height** as the rocket holds hers: thrust from the mass Sable weighs, gravity plus a gain on the climb's error,
  over the cosine of her tilt, times a trim learnt while calm; never under 0.4 g. Climb wanted is half the height
  error, between −3 and +5 blocks a second, eased at 1 block a second².
- **Level:** her tilt across her front and her right, its rate by differencing each tick (as `RocketPilot.attitude`),
  or Sable's angular velocity if the spike shows it cleaner; a turning moment of a gain on the tilt less a gain on its
  rate plus a learnt trim per axis (`ZeppelinPilot.pitchTrim`'s way), fed forward with the split Sable's centre of mass
  asks for; the four clusters mixed as a quadcopter's rotors, each held between signal 1 and 15, and on saturation she
  keeps level and gives up height. Lift never drops under signal 1 in flight, so no thruster pays the 10-tick restart.
- **Yaw** on the lateral jets toward a heading, at most 12° a second; **speed** on the main engines and retros, a
  gain plus a learnt drag trim, at most 1.5 blocks a second²; braking to arrive at v = min(set, √(2·a·d)). Her deck
  stays level: she never pitches to speed up, and over 10 blocks a second banks at most 5° into a turn, the lateral
  jets taking up the skid. Targets are eased and the zeppelin's signal hold keeps relays from flickering.
- **Phases:** her take-off waits for the ramp (`TakeoffHolds`), spools up 3 s at a tenth under hover, rises straight up
  with no translation for 12 blocks, then climbs away; she cruises on a heading, round a circle or to a pin with the
  zeppelin's steering; arriving she stops turning within 30 blocks and slides onto the pin, hovers, and a `PadSearch`
  over `GroundSurvey` picks the nearest level, dry spot in 64 blocks big enough for her; she descends to 10 blocks over
  it, then lands at a block a second, the lowest leg her radio height. Within half a block of touching, the level mix
  freezes and lift runs down over 20 ticks, so she never skates; 20 ticks still and her autopilot switches off.
  Switched off in the air she comes down under herself, as the rocket does.
- **Space:** she can climb to it, as the rocket does: `EdgeOfSpace`, `SpaceGravity` and `SpaceSky` follow.
- **`Craft.FREIGHTER(0, 40, 30, 36)`**, cruising at 20 blocks a second, with craft properties (`fallsWithoutPower`,
  `reachesSpace`, `assembledThroughSable`) in place of the `== ROCKET` checks. Her autopilot screen is the zeppelin's
  (a heading fan, circles, a pin with the land toggle), with her own pictures; its sky shows space by itself.
- **Hand flying** (should): from the pilot's seat, forward, sideways, turn and lift through a `FlyFreighter` payload
  sent on change and five times a second, as `DriveRover` is, and checked again on the server (alive, no spectator,
  seated in her pilot's seat, autopilot off, inputs clamped to ±1; ten ticks of silence is no input, so she hovers).
  Inputs are targets for the same control laws, never raw signals. Fly on the autopilot's screen hands her back.
- **The jump drive** (must: the Jump Lever, its spool-up, star streaks and 1.5 times her speed in space, and its
  failing and thumping, behind `freighter.jumpDrive`, on). **The jump** (should, behind `freighter.jumpDriveTeleport`,
  off until spike S7 holds): only in space, a 15 s spool-up
  streaking the stars while a ticket loads the 3×3 chunks where she will arrive; she jumps only once they are there,
  otherwise she gives a low note. `SableFlight.jump` moves her and every rider in the same tick, at most so many
  kilometres, at least y 1,050, arriving 300 blocks short of the pin, and she comes down as usual while the land loads
  under the fog. If spike S7 fails: the streaks alone, at 1.5 times her speed in space.

Shared out of the rocket's and the zeppelin's pilots, changing nothing in how they fly: `SignalHold` (the signal hold
both copy), `Steering` (bearing, rounding and the approach out of `ZeppelinPilot`), `Legs` (the lowest foot over the
ground, `RocketPilot.feetOverGround`, taking the feet's offsets, since her frame's middle is not her axis), `Tilt` (the
attitude record and its differencing), the existing `TakeoffHold` (the zeppelin's `HOLDS`) generalised so the rocket's
`holdCountdownWhile` and her ramp use it too, rather than a third way, and the craft properties.

**A fourth craft must not fall into the airliner's code.** `AutopilotBlockEntity` branches on the zeppelin and the
rocket and otherwise runs the airliner's ways (its approach and landing announcement, its save and load), and some 90
places ask which craft they serve. Before `Craft.FREIGHTER` exists, each such place becomes an exhaustive `switch` over
`Craft` (or asks a craft property), a behaviour-free commit checked place by place, so the compiler names every place
the freighter has to answer. The rocket's thrust maths and the phase
machines stay where they are; `FreighterPilot` has its own. Each is checked by flights sampled with `skyliners:rocket`
and `skyliners:zeppelin`, a sample a second for a minute, before and after.

## The boarding ramp

- **Geometry at scale 2:** a plate three blocks wide at a fixed slope of 1 in 2 (26.6°), dropping from her sill, 6
  blocks over level ground with her legs down, over a run of 12: 13.4 blocks long. It is hinged along its top edge,
  inboard, level with the sill, where its spur leaves the entry hall by the ring corridor. Stowed, the plate lies flat
  as the floor of that spur (3 wide, running out through the bulkhead toward her rim), so the ring is never cut; that
  floor, the belly skin under it and what lies between are its hatch, taken away and put back as the zeppelin's belly
  hatch is (`AirstairControllerBlockEntity.Step`, `BoardingAirstairs`). One slope means the walking surface needs only
  two shapes. A foot lip takes ground up to a block higher or lower; past that, or with anything in the way, the ramp
  refuses with a low note and a red lamp. At larger scales the run grows with her sill.
- **Motion,** counted on the server and on each client by `ToldTicks`: at 0 the latches clunk, steam hisses, the hatch
  blocks go and the amber edge lights blink; to 10 the rams pressurise; 10 to 80 the plate swings down, eased, the
  rams extending to the hydraulics' whine; 80 to 90 the foot lip unfolds with a thud and a second burst of steam, the
  walking surface is laid and the lights turn steady white. Going up runs backwards, after a waiting chime until
  nobody stands on it; going down waits while anyone stands on the stowed plate.
- **Drawn** by `BoardingRampRenderer` from `RampBuild` (shaped as `AirstairFlight`: a motion record and its parts):
  the plate, deck plating with yellow strips on top and hull panel under, two two-stage rams from the spur's side walls
  to lugs 40% along the plate's edges, lengthening from 3.5 to 4.9 blocks, the hinge tube, the belly hatch frame with
  hazard stripes and two red beacons, and the vents; steam is client particles timed by the keeper's client tick, never
  packets. Culled with `Sight`; stowed, the rams in the spur are drawn within 32 blocks only.
- **Walked** on `RampDeckBlock`, invisible as `RoverPlatformBlock` and laid and taken up as it is, its collision from
  its block state alone (facing; the upper or lower half of a one-in-two drop in four sub-steps; a guard wall on either
  edge), never from the level or the context, because Sable caches each state's shape for every ship. It passes
  through the ground as the airstairs' steps do (`SableFlight`), so a ramp on the ground never props her up.
- **Worked** by the `RampHingeBlockEntity` at the hinge's middle (its keeper, tracked in `Loaded`), through
  `BoardingRamp` (the state machine, saved as `BoardingAirstairs` saves, turned with `Turning`); inside, a ramp panel
  at the spur's head, a touch panel with a ramp-down and a ramp-up picture and a lamp (green stowed, amber moving, blue
  down, red refused); outside, a lit button with a ramp on its cap low on a landing leg, finding the keeper in her frame
  through `Loaded`. It comes down once she is built and after a landing once she has stood still a while, folds when
  her autopilot leaves off or she moves, and holds her take-off while it is not stowed (a `TakeoffHold`), everyone
  aboard told once with a chime (`StandingWatch`, extracted from `RocketStairsBlockEntity`).

## Turrets

- **Two ball turrets,** one on her top and one under her belly, reached by a rung ladder up and down the core. Each is
  a `LaserCannonBlock` in her hull with twin barrels, and a gunner's seat inside, sat in as the pilot seats are.
- **Aim:** nothing aimed is sent. Each tick the server turns the gunner's look to her frame (`SableFlight.turnToPlot`):
  a full turn of swing, and from level to 85° up for the top gun, down for the bottom one. The gunner's client draws
  its own turret from the live look; others get the aim in the block entity's update, at most five times a second and
  only past a degree, eased by the spring taken out of `LaserTurretRenderer` (`TurretSwing`).
- **Fire:** attack, through a `GunnerTrigger(held)` payload sent on change and every five ticks, checked on the server
  (alive, no spectator, in a wired gun's seat, guns switched on; a held trigger lapses after ten ticks of silence). Four
  bolts a second from alternate barrels; twenty in a row overheat the gun for two seconds.
- **What a bolt hits:** a hitscan from the muzzle along the barrel, entities found in 16-block steps. Hostile mobs,
  phantoms and the training drones take damage (`LASER_DAMAGE`, the gunner as attacker); players, villagers, pets,
  passive animals, the Spookie and anything aboard her never do. Bolts never break blocks: only sparks, and a bolt
  that meets her own hull, pod or dish stops there in sparks.
- **Arcs:** the top gun from level to 85° up; the belly gun from 10° to 85° down, since a seated gunner's eye in a dome
  0.7 m deep cannot see level.
- **The attack key** is swallowed while in a gunner's seat, as `RoverDriving` does, so a gunner never breaks the dome's
  glass or hits a crewmate in reach.
- **Bolts** are `LaserBolt`s with a style byte (the path clearer's beam, the guns' streak, a hit flag), drawn as a
  three-block streak at 150 blocks a second from the barrel tips (`LaserBolts`).
- **The gunner's sight** (`GunnerSight`, pictures only): a reticle ring with four ticks and a dot, heat as an arc round
  it from green to red, striped while overheated, the ring amber past the gun's arc, a red cross flashing on a hit, a
  small top or bottom gun picture.
- **Training drones:** an orange mushroom button by each seat, a drone on its cap, launches ten from the port prong;
  they loop past in arcs, each faster than the last, harmless, only in open sky within 48 blocks, gone when the round
  ends. A hit sparks with a chime rising with each hit in a row; a miss fades with a taunting bleep; at the round's end
  ten lamps count up with chimes, and a new ship's record gets a fanfare (and, once the Spookie is aboard, its roar).
  A gunnery board of her own in the lounge shows the top gun in orange and the bottom in blue, as numbers and as lamps
  (the basketball court's `ScoreboardBlock` counts hoops and starts hide and seek, so it is not reused as it is).

## The Spookie

Jan's own fallback name, kept (decision 12). A shaggy giant, 2.6 blocks tall, broad, arms to its knees, a slight
stoop; its own creature, not the famous one:

- **Fur:** short and dense, slate blue-grey with silver tips and a moss-green cast down its back; a pale cream mane over
  its shoulders and chest; lichen-pale dapples on its forearms.
- **Face:** bare charcoal skin, a heavy brow, a wide flat nose, two small lower tusks; big amber eyes that glow faintly
  in the dark.
- **Ears:** long, pointed and tufted like a lynx's, and moving: up and forward when happy, flat back when alarmed, its
  mood shown to players who cannot read.
- **Dress:** a hi-vis orange tool harness with reflective stripes, a big wrench and a satchel, brass goggles pushed up
  on its brow, leather bracers. No bandolier, and it carries no crossbow.
- **Voice:** hoots, chuffs, a rolling purr and a bellow like a didgeridoo, synthesised; never a growling moan.
- **Its life aboard:** `Spookie`, a mob living among her blocks as the tardigrade does (`#sable:retain_in_sub_level`),
  never hurt, pushed or despawned, walking routes its keeper (`SpookieHammockBlockEntity`, its hammock in the bunk room)
  hands it each tick rather than pathfinding: the ring with rests, the co-pilot's seat whenever her autopilot is on (a
  player taking the seat turns it out; it stands beside and grumbles), the top of the ramp, waving, while she is parked
  with the ramp down, engineering, tinkering in sparks, the holo table, the galley, and its hammock at night, snoring.
  Missing a hundred ticks, its keeper makes it again; more than four blocks off its route, it is brought home.
- **What it does:** greets whoever comes up the ramp with arms thrown up and a big roar, then chuffs and follows them
  with its eyes; roars at take-off; whoops at a good jump; howls and stomps to engineering when the jump drive fails,
  thumps it with a clang and roars in triumph; howls with its paws over its head when the self-destruct is armed;
  cheers a turret hit; warbles when clicked; gulps and chuffs when fed a cookie, sweet berries or honey; turns away when
  hit. At the holo table it slams the table when it loses and throws its arms up when it wins.
- **Walking:** its keeper hands it routes through `ShipWalker`, a small shared helper (a route of plot points, its
  rests, turning toward a player, put back on its route when lost) that the robots use too.
- **Drawn** by `SpookieModel` (a hierarchical code model, as `TardigradeModel`) and `SpookieRenderer`, a fur layer an
  inflated shell with ragged edges. About 0.8 by 2.6 blocks, its eyes at 2.2: her corridors are at least 3 blocks tall.
- It never follows players ashore in this plan (a could, behind `freighter.spookieAshore`).

## Her windows, dimmable

Jan's ask 13. The original has few windows, so hers are where a ship would have them, all one dimmable glass: her
cockpit's canopy, her turrets' domes, a ring of round viewports in the lens over the lounge and over the club (looking
up at the sky, or at the stars in space), and a viewport aft in engineering onto her engines' glow.

- **The glass:** a dimmable skin glass of her own, cut to her surface in the snapped style as her cover is (a
  `ShellBlock` with the shape property), with a `DIM` property in four steps from clear through light and medium to
  dark, its tint by step, as the rocket's oval windows' glass darkens; it needs no block entity.
- **Dimming:** the rocket's `WindowDimmerBlock` (a touch panel of four buttons, clear to dark, the sky and the sun
  through each) works on any glass that can be dimmed: `OvalWindowPieceBlock.dimWithin` is generalised into a shared
  `Dimming.within(level, box, step)` that dims every dimmable block in the box, her glass and the oval windows alike,
  so the rocket's dimmers behave as before. One by her cockpit's door dims the canopy, one in each room with viewports
  dims its own; using a pane of glass by hand dims it a step, with the falling chime, as an oval window does.
- **Switched off** with `freighter.windowDimmers`; her glass stays clear then.

## Her engines, impressive and loud

Jan's ask 15. Her thrusters are the show piece outside:

- **Her main engines:** a strip of blue glow along her stern's rim band (a light strip of her own, brighter with thrust)
  and Create Propulsion's plasma plumes from her six mains, long at full thrust; her lift thrusters' plumes show as she
  lifts off and lands and hide in cruise (their plume type set as they fire, sparingly, since a plume's every puff is
  sent to every player near).
- **Lifting off and landing:** a ring of dust and leaves blown out over the ground under her (`RocketDustRing`'s way,
  scaled to her), grass flattened in a shimmer of heat, and the view shaking near her and aboard as her thrusters light
  (`CameraShake`), as the rocket's does.
- **Heard from outside:** her engines' roar (`freighter_engine_roar`) heard up to 160 blocks off, rising with her
  thrust, a deep thump as her lift thrusters light, a rising whine as her mains spool up and a long rolling thunder as
  she goes by at speed; inside the hull it is muffled to her idle hum (`freighter_engine_idle`), as the rocket's roar is
  inside her. Played on the client from the thrust her autopilot shows (`FreighterRoar`, as `RocketRoar`).

## Robots aboard

Jan's ask 16. "Droid" is a registered trademark, so in game they are robots, and their looks and voices are their own
(no domed barrel on wheels, no gold man). They live among her blocks as the Spookie does, walking routes their docks
hand them, and beep in chirps and trills of their own synthesis (never the famous warbles):

- **The deck bot:** a knee-high boxy robot on two wheels with a single round lens and a tool arm, trundling round the
  ring corridor, stopping to scan a wall panel, beeping a little two-note greeting at whoever it passes.
- **The repair drone:** a hovering ball with a ring of thrusters and a pair of folding arms, tending the jump drive in
  engineering, welding in a shower of sparks, and darting to the thump plate when the drive fails.
- **The cargo bot:** a low flat-bed robot in the hold that carries a crate between the racks.
- Each has its dock (a charging cradle in the wall, its keeper), a config switch (`freighter.robots`), its renderer
  culled with `Sight`, and its beeps (`robot_chirp`, `robot_trill`, `robot_alarm`, `robot_happy`) heard within 16
  blocks. Clicking one makes it answer with a trill and a little spin. They share the Spookie's route-walking
  (`ShipWalker`, see The Spookie), so neither is written twice.

## The small tardigrade

A cousin of the rocket's, cat-sized (about 1.2 blocks long, 0.8 tall), pale mint with teal folds, in a porthole tank at
a child's eye height in the lounge. The `Tardigrade` gets a size, synched and saved ("Size", 1 when missing, so the
rocket's is unchanged), its dimensions, mouth, reach and sleep offsets scaled by it and its voice pitched up, the
habitat hatch saving the size it makes. It feeds on moss, purrs and sleeps as the big one does; its jumps are short
blinks across its tank in a pop of spores; it curls up at every jump the freighter makes; a babycino gives it three
hiccup-blinks, ice cream a tiny burp in a puff.

## The laser crossbow

`LaserCrossbowItem`, a gunmetal and dark-wood stock with two swept arms and a glowing amber coil for its string, a scope
and a three-lamp heat gauge. Hold use to charge it for a second, the coil brightening with a rising whine; use again to
fire an amber streak, a hitscan 48 blocks long (`LaserFire.trace`, out of `LaserPointerItem.trace`), 7 damage and a
big throw-back to hostile mobs and drones, sparks off blocks and target blocks lit. It never breaks, burns or moves a
block, and passes harmlessly through players, villagers, pets, passive animals and the Spookie. Three quick shots and it
vents steam for three seconds, its lamps showing the heat. It hangs on a rack in the lounge and can be crafted from a
crossbow, an amethyst shard, redstone and copper; `freighter.laserCrossbow` switches it off.

## Sound

All synthesised in `tools/generate_sounds.mjs`, each with its own `ownRandom` seed so no existing sound shifts.

- **The club's song, "Long Haul Swing" (`club_band`).** Up-tempo minor swing at 160 beats a minute in 4/4, eighths
  swung to 0.64, in D minor with an F major bridge, a 32-bar AABA played twice: 64 bars, 96 s, 1,920 ticks. A reedy
  horn leads (a 30% pulse through a low-pass opening on each attack, scooped up 40 cents, late vibrato, breath noise)
  doubled a fourth below, over kalimba comping, a wah bass walking, a glass FM bell on each downbeat, a ride cymbal,
  brushes and a feathered kick. Its chords, voicings, bass, bell and melody are written out in full in the appendix.
  It plays as the techno room's track does: a looping stream at the club's middle while anyone is in it, fading out as
  they leave; the client config's `clubMusic` turns it off.
- **Her other sounds:** the ramp (`ramp_steam`, `ramp_hydraulics`, `ramp_thud`; the airstairs' latch reused), her
  engines' idle and roar (`freighter_engine_idle`, `freighter_engine_roar`, crossfaded by thrust, steady with no
  pitch-dive), her lift jets, the ball turret's shot (two cracks 60 ms apart, rising, never a "pew") and its servo, the
  crossbow's shot and reload, the jump drive's spool-up, sputter, thump and jump, the Spookie's greet, happy, alarm and
  roar (formant synthesis, two pitches each), the small tardigrade's (the big one's at pitch 1.5, no new files), the
  radar dish's whirr, and the hidden compartments' latch, hiss and slide. The recipes are in the appendix.
- **Her computer's voice:** Kokoro's `af_bella`, used by no other voice, through a cockpit speaker with a two-tone
  falling blip before each line: "Jump drive charged.", "Jump drive fault.", "Ramp lowering.", "Turret ready.", "Jump in
  three, two, one." Her name, **Magpie**, is spoken by the computer ("Magpie ready.") and painted on her; air traffic
  control's lines are made with Windows' speech synthesis, so none of hers are in this plan.

## Ideas of our own

From the original, and from this mod's style; each marked must (in this plan's scope), should (built once the musts
stand) or could (left for later, listed in the backlog if not built).

| Idea | Mark |
|---|---|
| Hidden compartments under the hold's and the corridor's grates, a crawl-deep pit with storage under each, the only hint a scuff and a faint rattle; a stash hunt (the duck hunt's pattern): five finds open a stash of emeralds and a souvenir | must |
| The jump drive that sometimes fails: it winds down with a falling whine and a fist lights on the cockpit's plate; thump the plate (or the drive's in engineering) and it catches, the Spookie stomping off to do it if nobody does | must |
| The Holo Game Table: Minecraft mobs as holograms on a board of rings, a watch mode first, then a two-player game with rules shown in pips (bigger wins, a creeper takes both out) and the Spookie as the one-player opponent | must (watch mode), should (the game) |
| The self-destruct, the fleet's paintings, the robot bartender, the console modules, the camera display, the livery selector, LED strips round the ring | must (reuse) |
| A rotating radar dish, faster while her autopilot flies | should |
| Training drones for the turrets, the scoreboard, a ship's record with a fanfare | should |
| Hand flying from the pilot's seat | should |
| The jump drive's jump in space (teleport), behind its switch | should |
| Docking airlocks with round hatches, cycled with a hiss; in flight, jump doors with a jump light and a parachute rack | should |
| Escape pods: a round hatch, a seat and a red T-handle; in flight, a bang and the rider thrown clear under a canopy | should |
| A ship's map at the top of the ramp: touch a room's icon and floor lights chase round the ring to it | should |
| Landing legs that fold into her belly in flight (the gear bays' swing) | should |
| Lights that flicker until thumped; overhead toggles that play a power-up chord once all are flipped green | could |
| A regen tube in the med bay (ten seconds of regeneration, then two minutes' rest) | could |
| Hide and seek with the parcel scanner's red sweep (anyone not hidden under the floor is found) | could |
| A top hatch to stand on her hull; the radar dish snapping off in a hard landing; creepers keeping away from the Spookie as from cats; wanted posters (pictures only); a ration press in the galley; the Spookie walking ashore | could |
| Advancements: Thumped It, Let the Spookie Win, Ace Gunner (ten of ten) | should |

## Shared code

Extracted just before the freighter first needs each, each its own behaviour-free commit, checked by the build, every
layout's fingerprint (`skyliners:layout` for the airliner, furnished, every zeppelin at both sizes, the rocket and the
launch pad, recorded at a9781162 before the first) and the play-test checks that touch it. The fingerprint hashes
`state.toString()`, so renaming Java classes is safe; registry ids and property names never change.

- **Airframe:** `ShipPiece` (`wanted`, `build`), extended by `RocketPiece`; `HullLayout`, the cells, fittings, glazing,
  sky and air of `RocketLayout` lifted into an abstract `Layout` (the rocket's air box exactly as it is); `RoundPlan`
  and `ShipRoom` out of `RocketRoom` (the scatter's seed unchanged, the ceiling read per column); `SkinMount` over a
  `HullLayout`; `CorePictures` out of `RocketPosters`; `InsideFinish`, the lining loop with each ship's policy plugged
  in; `LinedCoverBlock` and `LiningPanelBlock` bases; `ShipPaint` in the client's `ZeppelinPaint`. Not worth
  extracting yet: `RocketQuarter`, `RocketFlightDeck`, the LED strips and their panels (keyed by story), the girders,
  the lift, `ZeppelinPiece`.
- **Flight:** the exhaustive craft switches, `SignalHold`, `Steering`, `Legs`, `Tilt`, the generalised `TakeoffHold`
  and the craft properties (see Flight).
- **Mechanisms:** `StandingWatch` (out of `RocketStairsBlockEntity`), the rams and box sink out of `AirstairMachinery`
  and a part painter out of `AirstairRenderer`, `LaserFire` (the pointer's trace only: the path clearer's vaporising
  stays where it is, since nothing of hers breaks blocks), `TurretSwing` (out of `LaserTurretRenderer`),
  `PictureButtonPanel` and its renderer (out of the rover's panel, its property names kept), `Spinner` (out of
  `PropellerBlockEntity`), `Strides` (the tardigrade's walking).
- **Packages:** her airframe classes stay in `airframe` (the pieces lean on its package-private access); her pilot in
  `autopilot`; her blocks in `block`; her entities in `entity`; her client classes in `client`, set up by a
  `FreighterClient` as `RoverClient` is.

## Milestones

Each ends in commits, a build and a play-test, and is said done only when seen working. Sounds come with the feature
they belong to, not at the end.

0. **Baseline and spikes.** Every layout's fingerprint at a9781162. Spike S0: hover the rocket and read her mass and
   thrust (the hover share against her mass confirms 10,000 a thruster).
1. **The bare hull.** `ShipPiece`, `HullLayout`; `FreighterShape`, `FreighterLayout` (skin, deck, bulkhead, passages,
   core, corridor, room walls, nothing furnished) and the padded bulkhead; the blueprint and its souvenir model, the
   build command, Blockpilot's build and layout, the creative tab, the lang keys. Spikes: screenshots of her rim, the
   prongs' tips, the gap's corners and the tube's crease for gaps in her skin, and her terraces judged; headroom of at
   least 3 rows on every route the Spookie will walk (ring, rooms, the way to the cockpit) and 2 everywhere a player
   goes; her mass read once she is assembled, before her thrusters are sized.
2. **The cockpit's bones and hovering.** The exhaustive craft switches (behaviour-free), `Craft.FREIGHTER` and its
   properties; the cockpit's seats, control head, flight displays and autopilot screen at their final places; her
   thrusters and relays wired to the head, sized from her measured mass so she hovers near 0.4 of full;
   `FreighterPilot`'s height and level hold, her assembly (as the zeppelin's), keep-loaded, `skyliners:freighter`.
   Spikes S1 (60 s hover: height within ±0.5, tilt under 1°, a knock settled in 3 s, at most two relay changes a second
   a cluster), S2 (a weight on one quadrant, level within 1° in 10 s), S8 (Sable's angular velocity against
   differencing), each thruster's NBT read to see it follows its own relay.
3. **Flying.** Her engines' show (the glow strip, plumes, dust ring, shaking and roar: Jan's ask 15), speed, yaw, bank
   and steering (S3: 20 b/s and a 180° turn, speed ±2, heading ±2°, height ±2; a coast-down for drag; 2 km at 40 b/s, on
   a dedicated server where one can be run); take-off, `PadSearch` (a ticket on the 64-block circle round the pin before
   it looks), descent and touchdown (S4: flat and on a 2-block slope with a player standing aboard, and once with nobody
   aboard; touchdown at most 1.5 b/s, bounce under 0.3, slide under 0.5, off within 3 s); switched off in the air; space
   (S6: holding y 1,050, the welcome played); her autopilot screen's pictures; S9: the rocket's tardigrade hatch set
   aboard by hand, its tardigrade living among her blocks through a yawing flight at 20 b/s and a `ParkedShips` unload
   and reload; a play-test check that she takes off and lands.
4. **The cockpit's dress and the jump drive:** the canopy made smooth (Jan's ask 14), its dimmable glass and dimmer, the
   tube's ribs, the overhead toggles, the console modules, the Jump Lever and the drive (spool, streaks, speed in space,
   failing and thumping), her cameras and camera display, the livery selector and her liveries, her silhouette and
   repaint, the self-destruct, the dice.
5. **The ramp,** a hand-laid deck of `RampDeckBlock`s walked first (cold start, `load server 200`, aboard once
   assembled), then the ramp, its panel, the call button, the take-off hold; play-test checks that her ramp is walked
   up from the ground and holds her take-off. Her legs stay fixed.
6. **Her rooms,** finishes first, the dimmable viewports and their dimmers (Jan's ask 13), then the ring corridor and
   entry hall, lounge (with the Holo Game Table's watch mode), engineering (the jump drive's core and its thump plate),
   cargo hold (hidden compartments), crew quarters and med bay, the Spacers' Club (bar, bandstand and song), galley;
   paintings, wall lights, LED strips, picture plates.
7. **Turrets,** first a spike: a seated player through a 12°/s yaw and a 5° bank, the server's reading of the look
   logged against the client's; then the ladder and domes, the guns, `GunnerTrigger`, the sight, the bolts' style; then
   the training drones and the gunnery board.
8. **Creatures:** the tardigrade's size and the small one's tank; `ShipWalker`; the Spookie (model, keeper, routes,
   the co-pilot's seat, its greeting) and every reaction the earlier milestones left for it (the thump, the record's
   roar, the self-destruct's howl, the holo table); the robots and their beeps.
9. **The laser crossbow** and its rack.
10. **Shoulds,** in this order: the radar dish, hand flying, the jump (S7: 3 km at y 1,100 with a player and a villager
    aboard, both still aboard, no "moved too quickly", no tick over 200 ms, kept loaded, a clean save and reload, again
    on a dedicated server), folding legs (their bays planned against the rooms over them: the belly is only 4 blocks
    under her deck there), the docking airlocks, the escape pods, the ship's map, the Holo Game Table's game,
    advancements.
11. **Docs and release:** `docs/guide.md` grows with each milestone; the README's tour, `docs/building.md`, pictures
    and a video at the end; `node tools/playtest.mjs --all`; the coulds left go to the backlog.

## Decisions

| # | Decision | Choice | By |
|---|---|---|---|
| 1 | Her name | The Freighter, Freighter Blueprint, as the Freighter Painting already is; Magpie on the radio | rights panel |
| 2 | How far her likeness goes | Her outline stays (Jan asked for it); her name, measurements, livery, greebles, cockpit panels, interior, music and sounds are our own; twin guns, not quad; the remaining risk stated in Rights | rights panel |
| 3 | Her size | A 25 m disc, 1,190 blocks of floor at scale 2 against the rocket's 2,360 | airframe panel |
| 4 | Her deck | One deck, a ring corridor round the turrets' core, six sector rooms out to a bulkhead 19 blocks out; the void past it holds engines and passages | airframe panel |
| 5 | Scale | Always scale 2, whatever the server's (the airframe panel proposed the greater of the server's and 2; the review showed twenty thrusters cannot lift her at 3 or 4) | lead, from the plan's review |
| 6 | Her hull's profile | A parabolic lens over a vertical rim band, her parts unioned as solids in `SurfaceCut` | airframe panel |
| 7 | Inner walls | One padded bulkhead block; finishes on floors, hull and outer bulkhead, in her own blocks | airframe panel |
| 8 | Which room where | Cargo hold at the bow, entry hall starboard fore, lounge and galley starboard aft, engineering aft, crew quarters and med bay port aft, the club port fore | lead, from the airframe and rooms panels |
| 9 | Lift | Four clusters of five creative thrusters on the diagonals; six mains, two retros, four lateral jets | flight panel |
| 10 | How she moves | Level deck: never pitching to speed up, at most 5° of bank over 10 b/s; sideways on her lateral jets to settle on a pin | flight panel |
| 11 | Her landing | Vertical, on level, dry ground found within 64 blocks of the pin (`PadSearch` over `GroundSurvey`) | flight panel |
| 12 | The furry giant's name | **Spookie**, as Jan asked if a Wookiee could not be used: the word is a trademark, so we are in his fallback. The rights panel argued for "Woodwose" (a sound-alike name looks deliberate in a takedown, and children hear "spooky"); kept as the alternative, a lang-only change if Jan wants it | lead (Jan's instruction), the rights panel dissenting |
| 13 | The Spookie's look | Slate blue-grey fur, cream mane, charcoal face with tusks, amber eyes, moving lynx ears, hi-vis tool harness, goggles; no bandolier and no crossbow | rights and rooms panels |
| 14 | The Spookie's life | Route-based, handed its routes by its hammock's keeper, never pathfinding: a path off her deck would drop it out of her blocks | mechanisms panel |
| 15 | The Spookie ashore | Not in this plan (could, behind a switch) | mechanisms and rooms panels |
| 16 | The small tardigrade | The `Tardigrade` with a saved size, 1 when missing; cat-sized, in a porthole tank in the lounge | mechanisms and rooms panels |
| 17 | The ramp | A plate 3 wide at a fixed 1 in 2, hinged at its top, stowed flat as a spur's floor, rams and steam drawn, walked on invisible state-shaped deck blocks that pass through the ground | lead, from the mechanisms panel (its slope) and the airframe panel (its place) |
| 18 | The ramp's controls | A touch panel inside (Jan's "button" as the house's touch panel, the lift's and rover's), a lit call button outside on a leg | mechanisms panel |
| 19 | Turret aim | The server reads the seated gunner's look in her frame; nothing aimed is sent | mechanisms panel |
| 20 | What bolts break | Nothing, ever: no switch to let them (the path clearer already clears terrain) | lead, siding with the rooms panel over a default-off switch |
| 21 | What bolts and crossbow hurt | Hostile mobs, phantoms and drones; never players, villagers, pets, passive animals, the Spookie or anything aboard | rooms and mechanisms panels |
| 22 | The crossbow | Charged by holding use, fired on use, hitscan 48 blocks, three quick shots then a vent; on a rack in the lounge and craftable; the Spookie does not carry it | rooms, mechanisms and rights panels |
| 23 | The club's song | "Long Haul Swing", our own minor swing at 160 bpm, 96 s, looped in the club | rights panel |
| 24 | Her computer's voice | Kokoro's `af_bella` through a cockpit speaker, a two-tone blip before each line | rights panel |
| 25 | The jump drive | The drive (lever, spool, streaks, 1.5 times her speed in space, failing and thumping) on, behind `freighter.jumpDrive`; the jump, a teleport in space to a destination loaded first, behind `freighter.jumpDriveTeleport`, off until spike S7 holds on a dedicated server | flight panel, split by the plan's review |
| 26 | Hand flying | A should, through a checked `FlyFreighter` payload, inputs as targets for the same control laws | flight panel |
| 27 | Extracting shared code | Just in time, each extraction its own commit right before her first use, rather than all up front | lead |
| 28 | Her liveries | Tramp (built in), Racer and Navy of her own, with the rocket's silver, polar and midnight | airframe panel |
| 29 | Touch panels | The ramp, the hold's compartments, the galley, the club's music, the corridor's lights; the rover's panel extracted into `PictureButtonPanel` | mechanisms panel |
| 30 | Experimental behaviour | Only the jump (teleport) is experimental; it defaults to off. The freighter herself is a ship as the others are, with no switch of her own, as the rocket has none; each of her pieces (rooms, ramp, turrets, robots, the Spookie) gets its own switch, on by default, as the rocket's do | lead (AGENTS.md), restated for the pair programmer |
| 31 | Craft checks | Every place that asks which craft it serves becomes an exhaustive switch before the freighter is added | plan's review |
| 32 | Take-off holds | The zeppelin's `TakeoffHold` generalised for the rocket's stairs and her ramp, not a third mechanism | plan's review |
| 33 | The belly gun's arc | 10° to 85° down; her own blocks stop bolts in sparks | plan's review |
| 34 | Her legs | Fixed until the shoulds; folding legs need bays planned against the rooms over them | plan's review |
| 35 | Her name on the radio | Magpie, spoken by her computer (Kokoro) and painted on her; no air traffic control lines (Windows-only) | plan's review |
| 36 | Her windows (ask 13) | One dimmable skin glass of hers in the canopy, the domes and round viewports over the lounge, the club and aft in engineering; the rocket's window dimmer works it through a shared `Dimming` | lead |
| 37 | Her canopy (ask 14) | Glass and struts both cut to one smooth cone and front, flush; photographed close up from outside and every seat before done | lead |
| 38 | Her engines' show (ask 15) | A blue glow strip, plasma plumes on the mains, lift plumes only lifting off and landing, a dust ring, shaking, and a roar heard 160 blocks off | lead |
| 39 | Droids (ask 16) | Robots, since "droid" is a trademark: a deck bot, a repair drone and a cargo bot of our own design, beeping in our own chirps, walking routes as the Spookie does | lead, by the rights panel's list |

## Appendix: the club's song

Written from scratch by the panel's sound designer. Notes are `name@beat/length` in beats from the bar's start, C4 =
MIDI 60.

**Chords.** A sections (bars 1–8, 9–16, 25–32): Dm6 | Bb7 | Gm6 | A7 | Dm6 | Bb7 | Em7b5 A7b9 (from beat 3) | Dm6.
Bridge (17–24): F6 | D7 | Gm7 | C7 | F6 | Bbmaj7 | Em7b5 | A7b9.

**Comping voicings (MIDI).** Dm6 62 65 69 71; Bb7 62 65 68 70; Gm6 and Em7b5 62 64 67 70; A7 61 64 67 69 (70 for b9);
F6 60 62 65 69; D7 60 62 66 69; Gm7 62 65 67 70; C7 60 64 67 70; Bbmaj7 58 62 65 69. The kalimba strikes on 2 and 4, and
in even bars on the swung "and" of 4 too, pushing into the next chord.

**Bass.** Walking quarters from the roots D2, Bb1, G2, A1, E2, F2, D2, G2, C2, Bb1: the root on 1, chord tones on 2 and
3, a semitone from the next root on 4.

**Bell.** One note on each downbeat. A sections: A5 Ab5 G5 G5 F5 D5 D5 (C#5 on 3) D5. Bridge: A5 C6 Bb5 Bb5 A5 A5 G5 G5.

**Melody.**

| Bars | Notes |
|---|---|
| 1, 5, 9, 13, 25, 29 | A4@0.5/0.5 B4@1/0.5 D5@1.5/1 F5@2.5/0.5 E5@3/1 |
| 2, 10, 26 | D5@0/0.5 C5@0.5/0.5 Ab4@1/1 F4@2/1 |
| 3, 11, 27 | G4@0.5/0.5 Bb4@1/0.5 D5@1.5/1 E5@2.5/0.5 D5@3/1 |
| 4, 12, 28 | C#5@0/0.5 E5@0.5/0.5 G5@1/0.5 Bb5@1.5/1 A5@2.5/0.5 G5@3/0.5 E5@3.5/0.5 |
| 6, 14, 30 | D5@0/0.5 F5@0.5/0.5 Ab5@1/1 G5@2/0.5 F5@2.5/0.5 D5@3/1 |
| 7, 31 | Bb4@0/0.5 D5@0.5/0.5 G5@1/1 E5@2/0.5 C#5@2.5/0.5 Bb4@3/0.5 G4@3.5/0.5 |
| 8, 32 | A4@0/0.5 D5@0.5/2 |
| 15 | G5@0/0.5 F5@0.5/0.5 E5@1/0.5 D5@1.5/0.5 C#5@2/0.5 E5@2.5/0.5 A5@3/1 |
| 16 | F5@0/0.5 D5@0.5/1.5 C5@3/0.5 E5@3.5/0.5 |
| 17 | F5@0/2 D5@2/1 C5@3/1 |
| 18 | F#5@0/1.5 A5@1.5/0.5 F#5@2/0.5 D5@2.5/1.5 |
| 19 | G5@0/2 F5@2/1 D5@3/1 |
| 20 | E5@0/1.5 G5@1.5/0.5 Bb5@2/1 A5@3/0.5 G5@3.5/0.5 |
| 21 | A5@0/2 F5@2/1 C5@3/1 |
| 22 | D5@0/1.5 F5@1.5/0.5 A5@2/2 |
| 23 | G5@0/1 Bb5@1/0.5 G5@1.5/0.5 E5@2/1 D5@3/1 |
| 24 | C#5@0/1 E5@1/0.5 G5@1.5/0.5 Bb5@2/1 |

**Second chorus (33–64).** The kalimba takes the A sections' tune an octave up while the horn holds the bell's notes;
the horn comes back in the bridge in fourths; the last A has both in octaves; bar 64 ends with a triplet brush fill on
3 and 4, back into the top.

**Instruments.** Lead: a 30% pulse through a one-pole low-pass opening on each attack, each note scooped up from 40
cents flat over 30 ms, vibrato of 5.5 Hz and ±12 cents coming in late, a little breath noise, doubled a fourth below at
0.3. Kalimba: a sine with an inharmonic partial at 5.4 times dying in 40 ms, a 3 ms click and a 20-cent drop on each
attack. Wah bass: a saw through `resonance()` swept 300 → 900 → 400 Hz over each note, a sine an octave down. Bell: FM,
modulator at 3.5 times, index 2 → 0 over 0.6 s, decay 1.2 s. Drums: a ride ("ding, ding-a ding, ding-a": noise through
resonances at 5.2 and 7.9 kHz with sines at 3.1 and 4.7 kHz), brushes (a 2–7 kHz swish, slapped on 2 and 4 with 0.12 s
of noise on a 190 Hz body), a feathered kick on every beat (`thunk` at 50 Hz, 0.15). Mix: tanh drive 0.9, written at
0.8, every note wrapped round the loop (`% length`, as `lift_music`'s `add`) so nothing is cut at the seam.

## Appendix: the other sounds

- `ramp_steam`, `ramp_hydraulics`, `ramp_thud` (about 4 s together): steam first, noise high-passed at 2 kHz dying
  over 0.6 s; a pump whine rising 140 → 210 Hz with harmonics and oil hiss (`gearHydraulics`' recipe on noise of its
  own); a `thunk` at 45 Hz and a 900 Hz latch click through `hullEcho` at 0.15. Going up the other way round, the hiss
  last as it seals.
- `freighter_engine_idle` (a seamless 4 s loop): a 41 Hz drone with its second and third harmonics throbbing at 3 Hz,
  a noise band round 400 Hz and a 1.1 kHz turbine whine, every partial turning a whole number of times in the loop.
  `freighter_engine_roar`: those plus a 1.2–4 kHz exhaust band and two whines 3 Hz apart, beating.
- `freighter_lift_jets` (a loop): `rocketRoar` cut off at 900 Hz, about ten crackles a second, fluttering nine times a
  second.
- `ball_turret_shot`: two shots 60 ms apart, each a 60 Hz `thunk`, an electric crack (noise through a 3.2 kHz
  resonance, bit-crushed) and 0.15 s of capacitor buzz at 110 Hz, its pitch rising. `ball_turret_servo` (a loop): the
  bartender's servo an octave and a half down over the turret ring's rumble, a click as it stops.
- `laser_crossbow_shot`: a Karplus-Strong pluck at 98 Hz, a crackling bolt (noise round 2.5 kHz gated by a 220 Hz
  square) swelling for 80 ms, a soft thump. `laser_crossbow_reload`: twelve quickening ratchet clicks, a charge whine
  300 → 2,400 Hz over 0.6 s, a latch clack.
- `jump_drive_spool` (3 s): three detuned saws sweeping 80 Hz → 2.4 kHz, beating faster as they rise, over a swelling
  sub-bass. `jump_drive_sputter` (2 s): the whine cut in and out six to twelve times a second and dropping in jerks,
  three backfire pops, sparks of 4–6 kHz clicks, a falling glide 400 → 120 Hz. `jump_drive_thump`: a fist on the panel
  (`thunk` at 70 Hz and `gong(330 Hz, 0.25)`), a rattle of four clicks, a clunk and the whine catching 200 → 900 Hz.
  `jump_drive_jump` (2.5 s): a noise swell, the whine shooting 300 Hz → 6 kHz in 0.4 s, 50 ms of silence, the techno
  track's falling boom (90 → 30 Hz).
- The Spookie: a pulse through three `resonance()` formants gliding between "oo" (300/870/2,240 Hz) and "ah"
  (730/1,090/2,440 Hz) with breath noise. `spookie_greet` a "hoo-HOO" rising 140 → 190 then 190 → 240 Hz and a chuff of
  two noise puffs through 900 Hz; `spookie_happy` a purr at 120 Hz rolled 24 times a second rising into a hoot;
  `spookie_alarm` three pairs of nasal honks a minor third apart (a formant at 1.1 kHz); `spookie_roar` a 65 Hz bellow
  with a subharmonic, a didgeridoo wah sweeping 400 → 1,100 → 600 Hz, tanh-driven, 2 s. Two of each in `sounds.json`,
  at pitch 0.95 and 1.05.
- `radar_dish_whirr`: a loop one turn of the dish long, a 60 Hz motor with a whine at its ninth harmonic and a bearing's
  squeak and creak once a turn.
- `hidden_hatch_open` and `_close`: two latch clicks at 2.2 kHz, 0.3 s of pneumatic hiss, the plate sliding (noise
  band-passed round 600 Hz, its loudness jittered for grit) and a soft thud as it seats; closing the other way round.

## Outcome

- **Plan review (2026-11-06):** an independent agent read the plan against the code; its eleven findings are folded
  in (decisions 5, 25 and 31 to 35, the ground rules, the milestones' order and spikes).
- **Baseline (a9781162):** airliner 2a9ad51d1a56d044 (34,993 blocks), furnished 855715f88bf654d6 (34,994), zeppelin
  3eb3f8ba01c78ecd, furnished zeppelin ca5f5b1efa753b2c, festive zeppelin e50807790d814432, rocket 6c740690d3cd4893
  (44,038), launch pad 882a45f2a4354176; at half size zeppelin 25cdb673d59ae7a2, furnished d132652f2944a4f3, festive
  6eea04847bbd260b.
- **S0 (2026-11-06):** the furnished rocket weighs 3,190 to 3,214 in Sable's units at scale 2, far under the panel's
  guess, and settles near hover at 0.14 to 0.16 of full thrust: 25 thrusters × 10,000 × 0.14 ≈ 3,200 × 11, so a
  creative thruster does push 10,000 and gravity is 11. Her thrusters will be sized from her own measured mass.
- **M1:** `HullLayout` and `ShipPiece` lifted out of the rocket's layout (8dead2d9); her fingerprint unchanged. The
  bare hull (024b583a): 1,382 blocks of floor a player reaches, prongs, tube and pod among them, 1,084 with the
  Spookie's three rows; nothing reaches the void past her bulkhead but her passages.
- **Jan's asks that evening:** dimmable windows, a smooth canopy, loud and impressive engines, beeping robots (6eaeebe6);
  the autopilots made open/closed (each craft a `CraftFlight` made by its `Craft`, so a new craft never edits the
  autopilot); a second model as standing pair programmer reviewing every change set; "make her 20% bigger" (scale 2.4);
  and "do not finish until I return in the morning; play test, polish, add features".
- **First hovers (M2, before the refactor):** she lifts off her legs, climbs and holds her height. Weighed bare at 1,393.
  With her centre of mass fed forward from Sable (it sits about three blocks ahead of her lift clusters' middle and a
  block to starboard, her pod and prongs) she holds level within ±0.8° and drifts under 0.2 b/s; without it she leant
  10° nose down and drifted 4.5 b/s. Only 12 of her 20 lift thrusters were laid at first (where her belly's skin is two
  blocks thick); mended.

- **She flies (M2):** 20% bigger, as Jan asked (scale 2.4: 60 blocks across, 84 long, weighing 2,067 bare). She lifts
  off her legs after three seconds of spooling up, rises clear, climbs and hovers within half a block of her height
  and ±0.6° of level; given a pin 230 blocks off she flew there, braked, came straight down and switched herself off
  on her legs about 6 blocks from it. A 180° turn takes 22 s. She reached only 12 of the 20 b/s set over that short a
  hop: her mains are tuned for 1.5 b/s² and air drag halves it (M3).
- **Two faults found flying her:** Sable reads a ship's velocity at its control head, which on her sits some thirty
  blocks from her middle, out in the cockpit's pod; turning, it swings sideways at 4 b/s, and she fought her own turns
  with her jets. She now flies by how her middle moves. And Create Propulsion sets fire to whatever stands in a
  thruster's exhaust once it runs at a third of full or more: her hidden jets fire inside her, so they are never given
  more than 4 of 15, turned up so they push as hard as before; she holds her heading to about 3° instead of 1°.
- **Pair review of M2:** the rocket's and the freighter's shared landing, switching off, space and ceiling moved into
  `StandingFlight`; the pilot's hot path no longer allocates per thruster; her top-down icon redrawn by hand so it
  reads at 16 pixels, her side icons' domes shaded.

## Night log, 6 to 7 November 2026

What was done while Jan slept, in order, and what is left for him to look at. Versions handed out are named here.

- M2 committed after the pair programmer's second review: d06edf73 (the shared autopilot pieces, `StandingFlight`
  among them, the rocket's play-test checks all holding) and cd630624 (she flies, built a fifth bigger).
- 7879ec03: the jump drive's and the Spookie's words made our own, as the pair's sound review asked.
- M3a, her engines heard: `ShownShare` and `EngineRoar` lifted out of the rocket's sounds (her roar checked unchanged
  in game and by the self-destruct check), then `FreighterRoar`. Heard in game: aboard, her thump at lift-off and her
  hum; from the ground, her roar from her stern and her lift jets under her belly. Left alone hovering, she came down
  by herself and was parked. The pair caught a whine that would have played on first sight of a freighter already
  flying; writing the play-test check for it showed her mains flickering on and off as she hovers, which would have
  replayed it every few seconds: mains that only trim her hover are now shown as nothing.
- Her engine grille: 99 blocks across her stern's rim band round her mains, deep blue while her thrusters hold her up,
  bright cyan while her mains push, dark once she is down, full-bright by night. Profiled over 90 s of lift-off, a push
  and a landing: five changes of 99 blocks, no server tick over 50 ms. The same profile shows her thrusters' own block
  updates at 58 a second (one main 8.75 a second) and some 860 particle packets a second from Create Propulsion's
  plumes reaching the client: next to cut.
- Cutting it, measured over the same 90-second flight each time (lift-off and hover, a push at 20 b/s, a landing):
  her jets held as her other thrusters are, the rates her pilot reads smoothed, and her lift plumes shown only within
  16 blocks of the ground. Thruster updates 5,601 down to about 4,100, particle packets 866 a second down to about 350,
  the plume's particle tick 2.6 down to 1.1 ms; hover height within 0.9 blocks RMS over two runs (0.56 before), level
  and heading as good or better; she reaches 18.2 of 20 b/s in the push's 20 seconds against 19.8 before. Holding
  her thrusters' signals wider, or pacing their changes, made her hunt and change them more: reverted.
- A runaway found doing it: a freighter that had learnt too much drag raced past the speed set (58, then 81 b/s on a
  20 set) and could never unlearn it. Fixed (9d3d0aa7) with a play-test check, freighter-drag, seen to fail without
  the fix and to hold with it.
- Her pad search (decision 11): set to land at a pin she now comes down on the nearest clear, level spot within 64
  blocks, her feet on dry, bare, level ground and nothing under her hull reaching it, looked at again pointing as she
  comes down; with none, she holds over the pin and says so. Play-test checks freighter-pad (a block of stone on the
  pin: she lands level beside it, and beside a second block built where she stood) and freighter-lake (a pin in a
  lake: she holds and says so). Before the search she landed on the stone and never switched off. Seen failing on
  the way, each now guarded by those checks: her rim a block over the stone (looked at every block now, a block wide
  of her hull), her prongs swinging over it as her heading wanders a few degrees (looked at 5 degrees either side),
  the ground read on a first flight trusted on the next, and her circling a spot beside her for ever (she now turns
  toward a pin before speeding to it). The search costs her autopilot 0.20 ms a tick on average over a landing, 0.16
  without it.
- Her canopy (ask 14): four struts square to the blocks and a ring, in place of eight that stepped across them (1888bb88).
- Her canopy dims (ask 13, 1db68720): the rocket's window dimmer at the pilot's left hand, reached from the seat, its four
  buttons from clear to all but black; the rocket's oval windows dim as before, checked on the final build.
- Before handing out 1.2.0-alpha.7: the quick play-test suite (58 checks) and the freighter's slow checks
  (freighter-flight, freighter-drag, freighter-pad, freighter-lake) all hold.
- Her deck robots (ask 16, 08bffd0d): two little white and teal robots, in the cargo hold and in engineering, chirping
  hello as someone comes up, their heads turning to follow, trilling when touched and beeping to themselves now and
  then; heard aboard her flying too. Their head turning and the bartender's now share `HeadTurn` and `Onlookers`
  (e5fbe1e5), the bartender's checks holding.
- Her autopilot screen moved from in front of the pilots to the ledge at the co-pilot's right (27894f9f): the view
  ahead through her canopy is clear from both front seats.
- M5, her boarding ramp (5c32ad8f, with the shared `Hatch` lifted out of the airstairs in 7af6f3f5): out of her
  starboard side along the wall of her front right room, its panel at its head and an amber call button on her
  starboard leg. It comes down by itself once she is built and after she lands, folds as she is set to fly, and her
  take-off waits for it; someone standing on it holds it, and after half a minute she calls her take-off off. Walked up
  from the ground into her ring corridor, on the ground and aboard her assembled. The pair caught a deck block's
  random tick that could have deleted it aboard while its panel's chunk was not loaded, and a take-off a stranger on
  the ramp could hold for ever; both mended and checked by freighter-ramp (11 checks).
- The laser crossbow: charged by holding use until it clicks, fired with the next use as an amber bolt that strikes
  hostile mobs only, venting after three quick shots. Found in play: charging when the hold finished let the held use
  key fire it at once; it now charges once let go, as the game's crossbow does, guarded by laser-crossbow. The bolt
  payload carries its glow's colour now, so the protocol went to 32.
- M7's first cut, her top turret: a ladder up her core to the gunner's seat under her top dome, "Turret ready" as a
  gunner sits, and a laser cannon on the dome whose twin barrels follow where the gunner looks and fire red bolts from
  alternate barrels while attack is held, hurting hostile mobs only and breaking nothing, overheating after twenty in a
  row, its servos whirring as it swings. The pair caught that bolts flew parallel to the gunner's look from a muzzle
  above their eyes, missing the crosshair at range: it now aims at what the look reaches. The crossbow and the cannon
  share one rule for what a laser hurts (`LaserFire.strike`). Checked by freighter-turret; one early run of it missed
  and could not be made to miss again in three runs.

- Her club's band: a robot jazz trio of the deck robots' family on a lit stage, playing "Long Haul Swing" to whoever
  comes near. First drawn as three dome-headed automatons on horn, squeezebox and drum, as the plan had it; the pair
  found that line-up in a spaceport bar too close to the film's cantina band, against the Rights section's "any close
  copy of its feel", so they became boxy robots of our own on bass, keyboard and drums.

### For Jan to look at

- Nobody has listened to the freighter's 35 new sounds (synthesised; levels, spectra and loop seams checked only).
- The Spookie keeps Jan's name; the rights panel's alternative, "Woodwose", is a lang-only change if wanted.
- The club's song, "Long Haul Swing" (`club_band`), wants a check by ear against the film's cantina tune: its notes
  and rhythm are our own (D minor, an F major bridge, 32 bars at 160 swung), but no machine can judge how close its
  feel is. The robots' trill wants a listen too, to be sure it is no sliding whistle like the film's robot's.
- Her canopy (ask 14) has four struts square to the blocks and a ring, clean from outside
  (run/blockpilot/screenshots/2026-11-07_02.35.36.788.png); from her seats the struts are still a frame of whole
  blocks, and from the pilot's seat the autopilot screen fills half the forward view
  (run/blockpilot/screenshots/2026-11-07_02.37.19.689.png): both are M4's, the cockpit's dress.
- Hovering high, her lift thrusters' plumes are now left out (the plan's decision 38, and half the particles anyone
  near her is sent): from the side nothing then shows she is held up but her hum and roar, her grille glowing astern
  (run/blockpilot/screenshots/2026-11-07_01.16.10.853.png). Showing them higher is one number, `LiftPlumes`'
  `SHOWN_UNDER`.
- Her ramp refuses where the ground under its foot rises (no adapting foot lip yet), and two freighters with ramps
  in one world were not tried.
- The laser crossbow is a flat picture held as the game's crossbow is; it has no rack yet, as her lounge is not built.
- Her top turret has no gunner's sight on screen yet (the plain crosshair is honest, the cannon aiming at what it
  covers), no bottom turret and no training drones; seen sideways from the seat, the dome's lower ring hides low
  targets, which its arc (level to 85° up) cannot reach anyway.
- Two leftovers of mine to clear, refused to me as irreversible: `git stash drop` of "freighter-m2-flight (before the
  pilot abstraction)", superseded by cd630624, and `git worktree remove --force` of the scratch worktree `wt-a`.
