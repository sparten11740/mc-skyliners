# The rocket: the night of 2026-10-28, for review in the morning

Jan asked at about 22:55 for the work to go on until 6:00. It should explore ideas (look, sound, gameplay), send
separable work to background agents, take decisions with a panel, and track each decision here for review. The plan is
`2026-10-28-rocket.md`; this log holds what was decided and done on the night, and why.

## In short

What the rocket has now that she did not have at 22:55, each with its server or client config switch:

- **A real lift** up her glass shaft: a car you ride, sliding doors, picture boards with each floor's icon, chimes,
  a motor hum and soft bossa nova while it rides; parked open in flight, its boards then showing how high she flies.
- **Furnished stories**: entry (parachute racks, lockers), cargo bay (baggage containers, crates, and the rover),
  galley and mess, bunk room, greenhouse (trees, beds, glow berries), a swimming pool with a glass bottom over the
  greenhouse, a techno club, the lab with its elephant-sized tardigrade, and an observation deck with a band of smoked
  glass all round and a starlight ceiling; two pilot seats in the cockpit.
- **LED strips** on every story, set from a panel by each lift door or for all stories from the cockpit's console.
- **Space posters** on the shaft (Earthrise, the solar system, a black hole, a quasar), also placeable by hand.
- **Space**: she climbs to y 1,400; the sky turns to space from y 400 (stars, a planet with its glowing rim, shooting
  stars, an aurora by night); "Welcome to space" at y 1,000 and an Edge of Space advancement; low gravity up there
  (off by default, `rocket.lowGravity`).
- **Sound and look of her flight**: launch control counting her down over the radio, her engines' roar, ignition,
  cut-off and touchdown; smoke billowing over the ground at lift-off and landing, a bed of fire under her close over
  it and the view shaking aboard as she lights, flames streaming up her sides as she falls back from space.
- **A mood for each room**: strips in the colour of its lift button, lanterns hung on chains, a planetarium of
  starlight over the observation deck, warm-coloured seats; the greenhouse and the pool heard as you stand in them.
- **The rover**: lowered from her cargo bay on a boom and winch, driven by remote from a chase camera, called home,
  and the countdown waits for it.
- **Fixes**: oval windows turning with her placement, her engines' signal chatter, her coming down after circling
  over the spot, the lift music restarting every tick aboard, a rocket's autopilot no longer speaking of circling the
  pin, and six code reviews' findings.

The last version tonight is alpha.181.

The versions handed out are under "Work done"; what wants your eye first is under "For Jan to look at".

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
| N30 | 00:35 | Next ideas (second panel) | Keep, in this order: low gravity aboard in space (off by default until proven in flight), a height gauge on the lift boards while she flies, shooting stars then an aurora, an "Edge of Space" advancement, lift muzak. Drop: LED strips switching themselves to a space scene (it would override what a child chose on a panel) | Panel (children's designer, tech lead, visual and audio designer) | Panel |
| N31 | 00:40 | Lift doors | Slide the pane aside over the doors' half second (a `sliding` state hides the block while a door block entity's renderer draws the pane), rather than snapping | Jan asked for a real lift; doors that snap read as a teleport's | Lead |
| N32 | 00:45 | Rover (agent's decisions) | Door and platform drawn only, a 1.5-high rail behind a red light curtain; the rover hooked on the platform, run out and lowered at 4 b/s on a telescoping boom; remotes from the panel's button and the creative tab, paired by using one on the rover or panel; the countdown waits (not refuses) and calls the rover home; range 96 blocks from the hinge, warnings from 84; no bay at scale 1 | Rover agent | Rover agent |
| N33 | 00:50 | Low gravity in space | Off by default (`rocket.lowGravity`, experimental): three tenths of gravity aboard from y 998 up, safe fall 7 blocks; lifted at once off her, and below the edge only once standing | Three tenths still pulls players down faster than she falls (6.6 b/s² at most), so no one floats off her floor; measured in game: a 3.15-block jump, about 1.6 s | Panel, lead |
| N34 | 01:00 | Lab's tank | Accepted: at scale 2 its back glass stands inside the back window's 4-block clearance (the window and the cells just inside it stay clear); the creature paces a straight line facing the hatch, turning round only from scale 3 | No 3-wide creature fits the free ring otherwise (lab agent) | Lab agent, lead |
| N35 | 01:00 | Shooting stars | 7-second slots, one streak each, 4 to 10 s apart, worked out from the game's time so all clients agree; drawn last so those under the rim show over the planet; as bright as the stars there | Sky agent | Sky agent |
| N36 | 01:00 | Lift music | Original bossa nova, 16 s loop, on the jukebox's volume (records), heard in the car and right by it | Lift agent's design, from the panel | Lift agent |
| N37 | 01:15 | Height gauge | While the lift is parked for her flight, its boards show a column of sky (sea at the foot, her ceiling at the top) with the edge of space dashed and starred and a little rocket at her height, in place of her floors; client config `heightGauge` | Panel's E | Panel, lead |
| N38 | 01:25 | Plumes | Cloud and smoke billowing over the ground within 40 blocks of it while her engines fire (lift-off and landing); flames streaming up her sides falling back from over y 500 faster than 20 b/s; `rocket.plumes`, on | Look of a launch; fun over realism (N7) | Lead |
| N39 | 01:30 | Circling over the spot | After 10 s within 12 blocks of the spot drifting under 3 b/s, she starts down anyway | Heavier now, she circled the spot 6–9 blocks off and never came down (over ten minutes seen); retuning the lean loop blind was riskier | Lead |
| N40 | 01:40 | Code review | Fixed: low gravity's lease lookup loading chunks; bamboo still growing; poster protection only at its middle; rover fall cover renewable while stowed; rover answers in words only. And at 02:15 the rover's range, capped by the server's view and simulation distances (seen at 6: about 56 blocks) | Independent review agent | Review, lead |
| N41 | 01:55 | Launch control callouts | "Tower cleared." at 40 blocks up, "Max Q." at y 300, "Main engine cut-off." into the cruise, "Landing burn." into the burn; each once a flight, waiting for quiet; `rocket.callouts`, on | Fun for adults who know real launches (N7); built by an agent on the countdown's voice | Lead, callouts agent |
| N42 | 02:00 | More furnishing | The cargo bay's baggage containers and crates; two pilot seats in the cockpit; a dimmed starlight ceiling over the observation deck | Seen bare at eye level | Lead |
| N43 | 02:10 | Black hole poster | A poster skips a shaft face with something standing within two blocks in front of it: the lab's tank put the black hole facing into it; it now hangs on the east face | Jan asked for it specially; it should be seen | Lead |
| N44 | 02:25 | Observation deck's lookout glasses | Replaced by armchairs facing out of the band | The zeppelin's glass marks land below; aboard her every steep look rests on her floors, the land through the band is beyond its 256 blocks, and a level look zooms into the band's glass; seen in game | Lead |
| N45 | 02:35 | Space helmet | A round white helmet with a big gold visor, worn as its own model like the pilot helmet, a lighter gold tint and no HUD from inside; a crew locker on the entry story and in the bunk room each holds one; breathing in it above the edge of space (client `helmetBreathing`); a recipe matching the pilot helmet's | Fun for children and adults; built by an agent | Lead, helmet agent |
| N46 | 02:35 | Lift sneak | No sneak is let off by the game; the car notes who sneaked and its machine sets them down outside an open door | The game set them down in the car, shut in on the ground and stuck in flight; seen in game | Lead |
| N47 | 02:55 | Observation deck at the bigger scales | A ceiling at most 16 rows over its floor, the starlight panels under it; nothing changes at scale 2 | Its story otherwise rose 68 or 110 blocks into her nose as bare skin; seen at scale 3 | Lead |
| N48 | 03:05 | Second code review | Fixed: the helmet's breathing reckoned by her middle (now her control head, as the welcome is); Max Q at y 300 of her head, lost at scale 3 and 4 (now 160 blocks over the ground); the plumes' packets (flames batched, smoke halved) and their size by the config's scale (now her own); the rover bay's constants; and, found play-testing the fix, a take-off announcement made after lift-off on a quick re-launch (now let go as the countdown begins) | Independent review agent, play-tests | Review, lead |
| N49 | 03:20 | Her rooms' mood (third panel, a critique of the night's screenshots) | Each story's LED strips start in its lift button's colour (a child pressing a colour steps out into it); lanterns on chains five rows up in the entry, mess and cargo bay, soul lanterns over the bunks; the observation deck's starlight up its whole wall (a planetarium) and its seats in warm colours; red armchairs in the entry, coloured blankets; the club's windows built at their darkest | Every room was the same white tile and grey floor, the furniture low and the height over it bare | Panel, lead |
| N50 | 03:25 | Backs of the boards and panels (same panel) | Brushed steel in a darker steel frame, the lift machine brushed steel with a hazard band | Black backs showed through the glass shaft as slabs on every story | Panel, lead |
| N51 | 03:40 | A lift-off worth the countdown (same panel) | Under 15 blocks over the ground, a bed of flames and embers bursts from the ground under her as wide as she is built, (a flash every 4 ticks, taken out by N53); aboard, the view shakes for 3 s at ignition (client `cameraShake`). Tried first at her fins' height and narrower: lost behind her plumes | Lift-off had looked like a gentle hover after a ten-second build-up | Panel, lead |
| N52 | 03:40 | The panel's other ideas | Not done tonight: a carpet ring round each shaft doorway, lamps over the lab's counters, the cockpit ceiling's livery tint, and the rover camera's item name overlapping its key help (a small client fix, left for a review with you) | Lower value for the time left; the play-test comes first | Lead |
| N53 | 04:00 | Third code review (of alpha.174) | Fixed: the lift-off fire's flash, five a second through her landing burn's last ten seconds, faster than the three a second the LED modes keep to (now no flash at all, rather than a slower strobe); the planetarium's notches at the ceiling's edge and slots at the four ways (it now fills from her skin in, but for the top row's skin blocks, where 36 of the deck's 48 ceiling strips had been lost); the shaft lanterns' copied loop and stale comments | Independent review agent; seen in fresh rockets at scales 2 and 3 | Review, lead |
| N54 | 03:55 | A rocket's autopilot naming its mode | "over to the pin and down on it" or "and back down where she rose" in chat, TO PIN or RETURN on her screens, in place of the airliner's "circling it, radius 225" and "heading 000" | Found on the final play-test: a rocket circles nothing | Lead |
| N55 | 04:08 | The deck's lower ceiling at scales 3 and 4 | It now reaches her skin all round (it stopped a block short at the four ways, holes up into the sealed space) and the deck's ceiling ring of strips hangs under it, not under the cockpit's floor over it | Found checking N53 at scale 3 | Lead |
| N56 | 04:25 | Rooms heard (fourth panel) | Yes, two rooms: the greenhouse (air through the leaves, a trickle, a drip, and now and then a bee buzzing past) and the pool (water lapping in a tiled hall, and now and then a slap), each swelling in as you step into its room, over 150 Hz to stay clear of the lift's hum, client `roomAmbience` on by default; the galley left for another night (a fridge hum would sit on the lift's), the bunks and the deck quiet on purpose, the club and the lab already heard | The sound tells a child which floor they stepped out on, matching the lift board's icons; panel (children's designer, tech lead, audio designer) | Panel, lead |
| N57 | 04:50 | Fourth code review (of the rooms' sounds) | Fixed: a loop the game let go of (a portal, a resource reload, rejoining) left its room silent for the session; swimmers in the pool heard the greenhouse; the next bee or slap was due by another world's time; around a rocket not yet flown, standing outside her skin heard the room; her rooms are now also heard before her first flight | Independent review agent; each fix heard in game | Review, lead |
| N58 | 05:15 | Fifth code review (of alpha.175 and alpha.176's fixes) | Fixed: her settings map's ring round the pin, her control head's circle radius and the helmet HUD's figure, none of which she flies; RETURN on her screens while told to land in the air (now her phase alone), and its chat line; Apply lit for a circle she has none of; the deck's lower ceiling told by her rooms being furnished, not a block of it | Independent review agent; the map and the header seen in game | Review, lead |
| N59 | 05:28 | Sixth code review (of alpha.179 and alpha.180) | Fixed: the four circle tiles taken off a rocket's screen (straight on and the pin alone), anything but the pin pictured as straight on on her control head and helmet HUD; the chat line "and down where she is" taken back out, as settings applied on her way down may steer her elsewhere; the map's readouts moved apart before their bands touch | Independent review agent; her screen seen in game | Review, lead |
| N60 | 06:10 (29th) | Her rooms lived in (**Jan**: same walls everywhere, sterile, orderly) | Split: the lead does inside paint and floors; agents do the greenhouse (raised beds, vegetables), the lab (food machines), the deck as a lounge with a cafeteria mezzanine, and sections and clutter in the six other rooms; plan in `2026-10-29-rocket-rooms.md` | Jan's words; separable by room | Lead |
| N61 | 06:15 | The mezzanine (**Jan**) | Over the observation deck, the chillout lounge, a cafeteria balcony over its back third | Jan chose it over a three-story atrium or the club | Jan |
| N62 | 06:40 | Wall paint (**Jan**: inside only, faces if possible) | Her cover's inner faces lined per room as the airliner's cabin is (`Lining`), its own finish each, untinted, her livery outside; each floor its room on top and the room under it underneath; `rocket.finishes` | Real face paint was possible, so no layer of blocks eats the walkway ring; mass unchanged | Lead |
| N63 | 06:45 | Custom interactive elements (**Jan**) | One or two per room, mod blocks, sent to the four agents | Jan's request | Jan, agents |
| N64 | 06:50 | Low gravity (**Jan**: on, ten seconds, then "artificial gravity enabled") | On by default; ten seconds from reaching space, then the cabin chime and "Artificial gravity enabled. Please mind your step."; once a climb | Jan's words; a second phrase brings the line level with the other announcements | Jan |
| N65 | 06:58 | "Aircraft" in the rocket's voice (**Jan**: spacecraft or rocket, the rocket only) | "spacecraft" in her own arrival announcement; the airliner's unchanged | Reads as the announcer's formal register | Lead |
| N66 | 07:00 | Windows' rims (**Jan**) | The wall round each oval inside is the room's own wall, its lowest row the dado, only the steel ring left | The livery-tinted weave rectangle round each oval was the misfit | Lead |
| N67 | 07:02 | Modern lights (**Jan**) | Halo lights, a ring on fine spokes from a slim cable, warm white or night blue, in place of lanterns on chains | Reads modern and spacecraft-like; reuses the airliner's light cable | Lead |
| N68 | 07:12 | Round halos (**Jan**) | The ring as sixteen segments turned along a circle in the 22.5-degree steps a block model allows | Eight bars read as an octagon | Lead |
| N69 | 07:25 | Wall washers (**Jan**: "Wandstrahler, die an der Wand hochstrahlen") | Eight per story but the deck, over head height between the windows, a drawn fan of light up the wall, warm, blue in the bunk room, pink in the club | The game's light cannot wash a wall, so the fan is drawn; each also lights the room | Lead |
| N70 | 07:30 | The agents' rooms merged | The lab's food lab (bioreactor, hydroponic rack, algae column, centrifuge, microscope view), the lounge's cafeteria mezzanine and snack machine, the greenhouse's kitchen garden (garden beds, sprinklers, grow lights, irrigation valve); alpha.183 | Seen in game: each room, the valve's spray, the snack machine's sale | Lead |
| N71 | 08:10 | The six other rooms merged | Sections and clutter in the entry (kit-up room, lounge), cargo bay (pallet racks, workbench), mess (pass, communal table), bunk room (bookshelf walls, reading nook), pool (loungers, towels) and club (booth, back bar, coat corner); gadgets: airlock panel, parcel scanner, galley fridge, wave machine, fog machine; their lanterns made halo lights in the merge; alpha.184 | Seen in game: every room both ways; each gadget used, its sounds heard (the fog itself not caught in a picture) | Lead |
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
  the lift's doors opening. From the ground 55 blocks off, the roar rises with her (logged 29, 110, 176 blocks up).
  Also seen on alpha.167: the cockpit's LED console (with all chosen, a swatch repaints every story, with a chime); a
  poster put up by hand on a wall and broken in survival (the whole poster comes down, one comes back); the rooms at
  scale 3 (all there, their middles emptier) and built facing east; a scale-1 rocket (four stories, the panels by the
  shaft, no room furniture, as it does not fit).
- alpha.168: the lift's doors slide (N31); the rover (N14, N15, N32; payload protocol 27). Play-tested: the doors
  sliding open and shut, frame by frame; the rover's bay and panel; a remote from the panel; deploy (the door folding
  into a platform, the boom out, the rover lowered on its cable to the ground); driving it from its chase camera
  (forward, turning, the horn when the key is held); calling it home from the remote (hoisted, stowed); a flight with
  it out (the countdown held until it was stowed, then lift-off with it in its cradle, carried in her plot, and still
  there after landing); deploying from her assembled. Not tried: quitting mid-lowering, scales 3 and 4, monsters by
  the driver, the range warning.
- alpha.169: the Edge of Space advancement; low gravity in space behind its switch (N33); shooting stars and the
  aurora (N35); lift music (N36), and a fix of mine for it restarting every tick aboard an assembled rocket (Sable plays
  a ship's sounds through instances of its own, so asking the sound engine about ours always said no); the lab and its
  tardigrade (N16, N34). Play-tested: low gravity (gravity 0.024 at y 1,400, a 3.15-block jump, weight back under the
  edge with its glide, landed unhurt); one music start for a 13 s ride; a shooting star caught at y 800 by night
  (subtle: short and small) and the aurora along the rim at y 800 and 1,200; the lab (counters, instruments, stools, the
  tank behind the shaft), its tardigrade fed moss at the hatch (the hatch clicks open, it purrs and munches), carried in
  her plot to y 420 and curling up there with its bloop. Not tried: the tardigrade's tun seen (it had uncurled by the
  time I looked), reloading the lab, the Edge of Space toast.
- alpha.170: the height gauge (N37); the plumes (N38); her coming down after circling (N39); the review's fixes (N40).
  Play-tested: the gauge at y 673 and 1,400 (the rocket kept inside the column); the lift-off billow by day from 40
  blocks; the fall-back flames, seen with the tick frozen at y 600; the circling rocket starting down and landing
  upright; bamboo planted done growing; the rover's answers' notes; the Edge of Space advancement earned; the lab
  reloaded with one tardigrade before and after; the rover reloaded mid-lowering (on the ground, one rover).
- alpha.171: launch control's callouts (N41); the cargo bay's load, the pilot seats and the starlight ceiling (N42);
  the black hole poster moved into view (N43). Play-tested: a flight to y 600 heard "Tower cleared." 2.5 s after
  lift-off, "Max Q.", "Main engine cut-off." and "Landing burn." in order, then the landing announcement, "Touchdown."
  and the arrival; the containers and five stacks of crates; both pilot seats, one sat in; the starlight ceiling at
  night; the poster on the shaft's east face at the lab. Also checked on alpha.170's code: quitting in a cruise at
  y 1,400 and reloading kept her cruising, with every tardigrade, rover and lift car back; a fresh rocket's assembly
  cost one 98 ms tick; the airliner's autopilot screen unchanged and the rocket's with its space band.
- alpha.172: the rover's range capped by the server's view distance (N40); armchairs in place of the observation deck's
  lookout glasses (N44); sneaking out of the lift (N46); the space helmet (N45); the README tidied. Play-tested: the rover
  stopped about 56 blocks out at view distance 6; sneaking out of the open car on the ground and in a climb, set down
  outside its door; the lockers' helmets; the helmet worn (front view, the tint from inside); its breathing at y 1,100,
  stopping as it came off; a scale-4 rocket (327,000 blocks, every room).
  Since, on alpha.172's code: the LED panel's modes and swatches (chase in cherry runs round the floor), the club's DJ
  deck playing aboard, the pool's water and ducks kept through flights, the Rocket Blueprint building one, the rover's
  monster warning (the red triangle blinking while a zombie stood by the driver's body); the cockpit's camera display at
  y 1,400 (the orbit camera showing her against space and the planet's rim); a rocket built with `rocket.rooms` off
  (bare stories, no tardigrade, the lift and 756 LED strips still there).
- alpha.173: the observation deck's ceiling at the bigger scales (N47); the second review's fixes (N48). Play-tested:
  the deck at scale 3 (starlit ceiling 15 rows up, the shaft open through it); the helmet's breath while holding at
  y 1,000; "Tower cleared.", "Max Q." 4 s later and "Main engine cut-off." on a flight flown straight after landing,
  with no stray announcement; the lift-off smoke rolling out over the ground; the black hole poster on the shaft's
  east face, beside the tank.
  Then a first-time player's walkthrough in a fresh world, in survival up to the door: the blueprint built her; the
  crew ladder climbed in ten seconds, a sideways step onto the step and in through the door (the ladder runs up the
  column beside it); the lift called by its board and ridden from the entry story to the cockpit (the doorway is one
  block wide between the LED panel and the board: line up on it); y 1,200 set on the autopilot's screen and Take Off
  pressed (it assembled her and counted down); a pilot seat sat in for the climb, space from it and the Edge of Space
  goal in chat; left unattended, she came down by herself with the landing burn, touchdown and arrival calls.
- alpha.174: her rooms' moods (N49), the steel backs (N50), the lift-off's fire and shake (N51). Play-tested: a freshly
  built rocket at dusk (the mess warm under its lanterns, the bunk room grape, the deck a starry dome); the boards'
  steel backs through the cargo bay's shaft glass; lift-off from the ground 40 blocks off (the bed of fire and embers
  under her as she rose, smoke rolling out round it) and from her cockpit (the view shaking against her own screen).
- alpha.175: the rover camera's held-item name kept off its key hint; a rocket's autopilot naming its mode (N54); the
  third review's fixes (N53). Play-tested: a full flight from a freshly built rocket's cockpit, up to y 1,200 (the Edge
  of Space goal), across 300 blocks leaning at most 9.6 degrees, down at 37 blocks a second and onto the pin 4 blocks
  from it, touchdown call, cut-off, bell and arrival announcement heard; the rover's remote taken from its panel, the
  rover lowered and driven, its camera clear of the item's name, and called home; the planetarium at scales 2 and 3.
- alpha.176: the deck's lower ceiling at scales 3 and 4 (N55); a rocket's pin without the airliner's circle and land
  switch. Play-tested: a fresh rocket at scale 3 (the ceiling meeting her skin, its strips under it); a rocket's
  screen set to a pin; a photo tour of every story of a fresh scale-2 rocket at noon, nothing floating or misplaced.
- alpha.177: her greenhouse and pool heard (N56). Play-tested aboard a landed rocket: the greenhouse's loop and two
  bees in half a minute, about six blocks off; the pool's loop and its slaps from the deck. Also, on alpha.176's code:
  her lift-off fire by night (a glowing bed of flames and embers, no strobe); the deck by night at y 1,200 (stars
  through the band, the planetarium over it); the world quit and reloaded while she climbed through y 350 with me
  aboard (she carried on climbing, held at her height and came back down beside where she rose).
- alpha.178: the fourth review's fixes to the rooms' sounds (N57). Play-tested in a fresh scale-3 rocket never flown:
  the greenhouse and the pool heard there, the pool's loop starting again after a resource reload (F3+T), the pool and
  not the greenhouse heard while swimming in it, nothing a few blocks outside her skin at greenhouse height. Then
  the lift ridden from her entry story to her cockpit (doors opened from the landing board, walked in, the cockpit
  picked): the greenhouse's loop beginning as the car rose through it, the pool's two seconds later, the club's track
  after it, and the up chime and the doors at the cockpit.
- alpha.179: the fifth review's fixes (N58). Play-tested on a scale-3 rocket: her map with a pin and no ring, and her
  screen reading DESCENT once Land was pressed in her climb; her control head after landing, its heading row drawn as
  before; not looked at: the helmet HUD, or the control head set to a circle.
- alpha.180: the autopilot map's pin coordinates kept a line over its own where the two would run into each other, as
  they did on the rocket's narrow map ("Z 92Pin X ..."). Seen on a rocket's screen with a pin set.
- alpha.181: the sixth review's fixes (N59). Seen in game: a rocket's screen without a pin, straight on and the pin
  tile alone under her heading dial.

## Background agents

Each in a git worktree of its own, briefed with the decisions above and the night's rules (no dev client, no version
bumps, commits cherry-picked onto main by the lead and play-tested there):

- Posters (N17), LED strips (N13), going to space (N12), the rover (N14, N15): from 23:15.
- The lab with its tardigrade (N16), the rocket's sound and voice (N18): from 23:35.
- Back and on main: going to space (3 commits), LED strips (1), posters (2), by 23:58; sound and voice (3), by 00:20;
  the rover (3), by 00:45.
- From 00:38: shooting stars and an aurora, and lift muzak (N30), each in a worktree of its own; both back, with the
  lab, by 01:00.

## For Jan to look at

- A slip of mine, found by the sound agent: resolving the posters' merge I dropped two closing braces, so from
  b82674e until be83c6a neither `generate_textures.mjs` nor `generate_resources.mjs` ran. My check had thrown their
  output away and read a clean `git status` as a pass. The files they write were right all along (they were committed
  from the agents' worktrees, and the fixed generators rewrite every one of them unchanged), so alpha.166's jar is not
  affected. I now run them showing their exit codes.

- N14: the rover lowered on a winch rope from a platform at the cargo bay door, not down a ramp.
- N23: the observation deck's band of smoked glass in place of its oval windows.
- N33: low gravity in space is off by default (`rocket.lowGravity`); worth a try with it on.
- N34: the lab's tank stands partly in front of the back window at scale 2.
- The shooting stars are a little bolder since alpha.172 (half as wide again, a bigger glow), but still meteor-sized;
  easy to make longer if you would like them showier.
- At scales 3 and 4 the nine stories keep their player-sized heights, so her observation deck rose as an atrium 68 or
  110 blocks tall up into her nose (seen at scale 4, which built 327,000 blocks in 3 s and every room, the pool a wide
  lagoon). Since 02:55 it has a ceiling 16 rows over its floor at those scales, the space over it up to the cockpit
  sealed off and the shaft running on through it (N47); more stories at the bigger scales would use that space.
- A slip of mine at 03:36: testing the lift-off fire I ran `./gradlew build` on uncommitted code, which rewrote
  alpha.173's jar in `build/libs` with it. Nothing was handed out (the desktop drive was not mounted); I rebuilt
  alpha.173 from its own commit in a scratch worktree and put that jar back, checked by its plume class.
- The autopilot's settings screen still shows a rocket without a pin the airliner's heading dial and box; she
  ignores the heading and comes back down where she rose (her screens say RETURN, N54). Her circle tiles, her circle
  and land switch at the pin and the ring round it are gone (N58, N59); hiding the dial too wants the screen laid out
  again, so I left it for you.
- Over her cockpit's glass, the inside of her nose shows as a stepped dome of bare cover in her livery's colour (red
  in the default), which the design panel called a red tint over the cockpit. A ceiling over the cockpit, as the deck
  has at the bigger scales, would hide it, but would also hide her livery from inside and needs care round what the
  nose holds; I left the choice to you (screenshots `run/blockpilot/screenshots/2026-10-29_04.11.32.*`).
- The greenhouse's and the pool's new sounds (N56) are synthesised and were measured (about 10 dB under the lift's
  hum, 30 dB down under 150 Hz) and heard playing in game, but nobody has listened to them by ear: worth a listen,
  and `node tools/generate_sounds.mjs greenhouse_air greenhouse_bee pool_water pool_slap` remakes just those.
- Testing the rooms' sounds with a resource reload (F3+T) at 04:48, the dev client crashed a minute later in the
  game's own font code drawing a recipe toast ("Image is not allocated", `crash-2026-10-29_04.48.55-client.txt`), no
  Aerolarge code in it: a bitmap font freed by the reload. The world had saved; I note it in case it ever shows
  without F3+T.
- Jan's desktop keeps `lowGravity = false` in `/Volumes/Desktop/config/aerolarge-server.toml` (a changed default never
  changes a saved value): set it to true there once the share is mounted and the game there is closed.
- Not verified tonight: anything with a second player (multiplayer).
- The side jets pulse at signal 2 for a few ticks every half second or so in a cruise (N29); a deadband did not stop
  it, so it is the tilt hold's own rhythm. Each pulse costs a few thruster updates; frame and tick times were fine.
