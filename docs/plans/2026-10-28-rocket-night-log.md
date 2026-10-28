# The rocket: the night of 2026-10-28, for review in the morning

Jan asked at about 22:55 for the work to go on until 6:00. It should explore ideas (look, sound, gameplay), send
separable work to background agents, take decisions with a panel, and track each decision here for review. The plan is
`2026-10-28-rocket.md`; this log holds what was decided and done on the night, and why.

## How decisions are taken

A panel of three agents gives its view on the open design questions: a game designer for young children, a
Minecraft technical lead and a visual and audio designer. The lead weighs their views against the code and the
project's rules (AGENTS.md) and decides, writing down here the options, the choice and the reasons. Jan's own requests
are marked **Jan**. Anything Jan should look at first is under "For Jan to look at" at the end.

## Decisions

| # | Time | Question | Choice | Why | By |
|---|---|---|---|---|---|
| N1 | 21:50 | Window look | Big oval windows (3×4 blocks), their glass a dome bulging out of the skin, in a steel-lined recess inside | Jan asked for bigger oval windows, curved outward | Jan |
| N2 | 21:55 | Window shading | Electric dimming in three steps (clear, light, medium, dark), the glass only, no shades | Jan: "no shades in that case, just electrically dimmable windows, 3 levels"; "only the glass needs to dim" | Jan |
| N3 | 22:10 | Window colours on a livery | A window's frame takes one colour, the cover's at its middle; patterns run by story, their edges on the diagonals | Jan saw frames split across a pattern's edge | Jan, lead |
| N4 | 22:45 | Cockpit glass | The airliner's windscreen glass, then smoked near-black (N6) | Jan asked for the airliner's cockpit glass | Jan |
| N5 | 22:52 | Lift | Not Create's elevator: a Create contraption aboard becomes its own Sable body (spike). Our own lift car riding up and down the glass shaft, riders inside, not a teleport | Spike result; Jan: "not just a teleport, right?" | Spike, Jan |
| N6 | 22:55 | Rocket glass tint | Darker, near-black smoked glass for her cockpit and windows, not blue; the airliner's windscreen keeps its own | Jan | Jan |
| N7 | 23:00 | Tone | Fun for adults too: cool sounds, striking visuals, playful but never babyish; icons on buttons stay for non-readers | Jan: "don't make things too childish"; "fun for adults as well" | Jan |
| N8 | 23:05 | Smoked glass drawing | Every face drawn (not one pane a block), each at 0x70, two together about the windscreen's 0xA8 | One pane a block left gaps and overlaps round her stepped curve (Jan: "weird at the edges") | Jan, lead |
| N9 | 23:10 | Lift car architecture | An entity that lives in her plot (Sable's `retain_in_sub_level` and `destroy_with_sub_level` tags, as Create's seat), moving only up the shaft on a clock both sides share; owned by a lift machine block entity; riders standing passengers (up to four), let off only at an open door; set down on logout | Sable then carries it and its riders through her tilt and motion; a car chasing her from outside would lag (tech lead). Riskiest piece: spike first | Panel (tech) |
| N10 | 23:10 | Lift ride | A picture board by each landing's door, her rooms stacked as she is, each an icon on its story's colour; tap one and the car comes, doors slide open (0.5 s), you step in, it goes at up to 6 blocks a second easing in and out, a chime on arrival (one rising tone going up, two falling going down), doors open 4 s. It never moves with a door open or anyone in the doorway, and parks at a floor with its doors open through the countdown, lift-off and landing burn | Panel (children's designer, visual designer), toned down per N7 | Panel |
| N11 | 23:10 | Lift sound | A low motor hum (50/100/150 Hz) with a whine rising as it speeds up; a 0.9 s door slide ending in a soft thunk | Visual and audio designer | Panel |
| N12 | 23:10 | Space | The sky darkens from y 400 to black at y 1,200, stars fade in from y 500, the clouds fade 500–800, the sun goes white with no sunrise tint, a horizon glow and the planet's curve below. Drawn client-side at `RenderLevelStageEvent` AFTER_SKY plus fog colour, keyed on the camera (so camera feeds match), behind a client switch `spaceSky` (on). The rocket gets her own ceiling (server config, about y 1,400), and her altitude dial a space band at its top | Panel: heights from the visual designer, rendering from the tech lead. No sky-colour event in NeoForge 1.21.1; replacing the dimension's effects would fight other mods | Panel |
| N13 | 23:10 | LED strips | Round each story's floor and ceiling edges. Colour as a tint (as the livery is), brightness off/35/65/100% as a block state with block light, animated modes (breathe, chase, club beat) drawn by a renderer as overlays, not relit. Ten colours. A panel by each story's shaft door (left of it), and a cockpit console with her silhouette, each story's band tappable, or all. Never flashing faster than 3 times a second | Tech lead's hybrid keeps it cheap; flash limit for photosensitive players | Panel |
| N14 | 23:10 | Rover deployment | **Jan to review:** not a ramp to the ground but a fold-out door platform from the cargo bay, the rover lowered from it on a winch rope, sky-crane style, and hoisted back the same way | The cargo bay floor stands about 26 rows up at scale 2, so a ramp at a drivable slope would run about 50 blocks (tech lead) | Lead |
| N15 | 23:10 | Rover | An entity (not a Sable body), stowed aboard in the bay, driven with a paired remote: a chase-camera driving mode, the client's movement keys sent to the server in a payload (protocol bump), checked there (remote in hand, within 96 blocks, inputs clamped, stops without word for half a second); it comes home by itself on the countdown | Tech lead; the children's designer's chase camera and home button | Panel |
| N16 | 23:10 | Tardigrade | A creature retained in her plot (as Sable retains snow golems), restricted to a glass tank in the lab, invulnerable and persistent; a plump eight-legged water bear (about 6×3.5×3 blocks), restrained sounds; it can be fed moss, and curls into a tun in space | Panel, toned down per N7 | Panel |
| N17 | 23:10 | Posters | A poster block (FACING × COLUMN × ROW × poster), thin, no collision, assembled with her, broken as one. Four: solar system 6×2, black hole 4×3 (Jan's Kurzgesagt-style request, original artwork), quasar 3×4 upright, Earthrise 4×3 with the rocket | Paintings are entities and would be left behind at assembly; blocks are deterministic | Panel |
| N18 | 23:10 | Rocket sound and voice | Ignition, an engine loop following thrust, cut-off, side-jet puffs, a countdown voice over the radio, touchdown; "Welcome to space" | Visual and audio designer | Panel |
| N19 | 23:12 | Ninth story | Nine stories at scale 2, one for each room; the tall top story split | Each planned room needs a floor | Lead |
| N20 | 23:40 | Room layout rule | Furniture placed by distance out and angle round, so rooms come out round at every scale; every room keeps clear the shaft and a walkway round it, the way out to the front, the windows' fronts and a ring along the skin (for walking round and for the LED strips); what does not fit is left out | One rule for seven rooms, and room for the LED agent's strips without coordinating cell by cell | Lead |
| N21 | 23:48 | Greenhouse | Moss beds, ferns, flowers, bamboo, two cherry and two azalea trees, glow berry vines, rather than the airliner's planters | Seen in game: little planters in a 12-row room looked bare from above and at eye level | Lead |
| N22 | 23:48 | Swimming pool | A C-shaped channel two deep round her back, its bottom glass hanging into the greenhouse's ceiling; deck with bean bags at the front | A ring round the shaft leaves no deck at scale 2; the glass bottom gives the greenhouse a look up at the swimmers | Lead |
| N23 | 23:49 | Observation deck windows | A band of smoked glass all round (rows 1–5) in place of its oval windows | An observation deck should see the whole horizon; seen in game from y 1,400 it shows the planet's rim all round | Lead |
| N24 | 23:58 | Ceiling setting's name | `rocket.ceiling` (the space agent's `rocketCeiling`), beside `rocket.lift`, `rocket.rooms`, `rocket.posters`, `rocket.ledStrips` | One section, short names | Lead |
| N25 | 00:00 | Order of pieces | Rooms, then posters, then LED strips last | The strips fill in round whatever else stands; the posters need the shaft's back clear | Lead |
| N26 | 00:20 | Countdown and announcement | The countdown waits for the take-off announcement to end (at most 9 s), as the zeppelin's officer waits before "Up ship!"; lift-off comes about 6.5 s later than before | Otherwise the two voices overlapped for six seconds (sound agent) | Sound agent |
| N27 | 00:20 | Launch control's voice | One radio transmission for the whole count (a Quindar tone before "ten" and after "lift-off!"), Kokoro's `am_michael`; heard within 128 blocks of her, aboard or not; "Touchdown." aboard only | Sound agent | Sound agent |
| N28 | 00:20 | Engine sound | A roar following her thrust, heard 160 blocks off from the foot of her axis; aboard, a muffled rumble (a real 220 Hz low-pass) round the listener; ignition, cut-off and touchdown one-shots; side-jet puffs only from rest at signal 3 or more, at most every 2 s a jet | Sound agent, debounced after the lead's note on jet chatter | Sound agent |
| N29 | 00:25 | Engine and jet chatter | Hold her engines' total signal until the thrust wanted moves 0.75 of a step (as the zeppelin's); leave the jets as they were | Measured in a cruise at y 1,400: thruster changes sent 208/s before (one run), 145–165/s after (three runs); holding the jets too did not change them less often | Lead |

## Work done

Commits on main, newest last, with the versions handed out.

- alpha.160: the rocket (airframe, pilot, pin flights).
- alpha.161–162: the oval windows, then their domes and recess.
- alpha.163: her outside cameras and the cockpit's camera display.
- alpha.164: her six liveries and their selector; the cockpit in windscreen glass.
- On main since: smoked glass (N6, N8), her own "off" message, the ninth story (N19).
- alpha.165: her lift (N9–N11): the car riding the glass shaft, sliding doors, picture boards by every landing and in
  the car, its hum and chimes; parked open in flight. Play-tested: up and down the whole shaft (entry to cockpit), the
  doorway boarding (no drop), riders' feet on the floor, set down at each landing's exit, getting off between floors
  refused, the chime and door sounds, parked open at the cockpit through a hop with the car carried in her plot.
- alpha.166: her oval windows turn with her when she is built facing another way (the posters agent spotted it); her
  stories furnished (N20–N23); space (N12: her ceiling at y 1,400, the space sky, the welcome); the LED strips (N13);
  the space posters (N17), also placeable by hand. Play-tested at scale 2 in a fresh rocket: every room from above and
  at eye level; the four posters; the LED strips on every story, a panel's swatch turning the observation deck's strips
  pink with a chime; a flight to y 1,400 and back from the observation deck (clouds at 450, the planet's rim and stars
  at 800, near-black at 1,050, space at 1,400; the welcome heard once on the way up); assembly leaves no block
  behind. Not tried yet: the cockpit's LED console, the other modes, posters placed and broken by hand, rooms at
  scales other than 2, the rotated rocket's rooms.
- alpha.167: the generators mended; her engines' signal held (N29); her roar, ignition, cut-off and touchdown, and
  launch control counting her down over the radio (N26–N28). Play-tested: the take-off announcement, then "ten" to
  "one" a second apart and "lift-off!", the ignition and the aboard rumble; a cruise at y 1,400 (no jet puffs, the jets
  staying under signal 3); the landing announcement, "Touchdown.", the thunk and cut-off, the arrival announcement and
  the lift's doors opening. Not heard from the ground outside her yet (the roar's place as she flies).

## Background agents

Each in a git worktree of its own, briefed with the decisions above and the night's rules (no dev client, no version
bumps, commits cherry-picked onto main by the lead and play-tested there):

- Posters (N17), LED strips (N13), going to space (N12), the rover (N14, N15): from 23:15.
- The lab with its tardigrade (N16), the rocket's sound and voice (N18): from 23:35.
- Back and on main: going to space (3 commits), LED strips (1), posters (2), by 23:58; sound and voice (3), by 00:20.

## For Jan to look at

- A slip of mine, found by the sound agent: resolving the posters' merge I dropped two closing braces, so from
  b82674e until be83c6a neither `generate_textures.mjs` nor `generate_resources.mjs` ran. My check had thrown their
  output away and read a clean `git status` as a pass. The files they write were right all along (they were committed
  from the agents' worktrees, and the fixed generators rewrite every one of them unchanged), so alpha.166's jar is not
  affected. I now run them showing their exit codes.

- N14: the rover lowered on a winch rope from a platform at the cargo bay door, not down a ramp.
- N23: the observation deck's band of smoked glass in place of its oval windows.
- The side jets pulse at signal 2 for a few ticks every half second or so in a cruise (N29); a deadband did not stop
  it, so it is the tilt hold's own rhythm. Each pulse costs a few thruster updates; frame and tick times were fine.
