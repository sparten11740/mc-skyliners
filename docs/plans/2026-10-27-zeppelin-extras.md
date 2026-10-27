# Zeppelin extras plan, from 27 October 2026

Five panellists (wonder and discovery, an airship historian, a physics and Create engineer, multiplayer and the shared
world, a scope skeptic) proposed about 35 ideas to make the zeppelin special, inside and out; merged, they made 26,
and Jan picked 21 of them, one at a time. This plan builds all 21. It follows on from
[last night's plan](2026-10-26-zeppelin.md), whose ground rules still hold.

## Ground rules

- **For every age.** Nothing childish: the Hindenburg's grandeur and Create's craftsmanship, things a teenager or a
  parent enjoys as much as a child. Every cue still has a picture, a colour or a sound, so that someone who cannot
  read can use everything.
- **Modular.** Each feature is its own piece, with its own classes, and a config switch where a player or a server
  might want it off; nothing reaches into another feature's internals, so taking one out never breaks another (see
  Seams). Where a great feature cannot be made modular, it is built anyway, and the plan says so.
- **Not blocked on feasibility.** Jan judged the ideas. Where one turns out hard, it is built the best way that works,
  and a smaller first version comes before a perfect one.
- **Decisions** that would otherwise wait for Jan are taken by a panel of agents and logged below, with who decided.
- **Workflow** as ever: on `main`, never pushed; one atomic commit per feature, reviewed (`review-changes`) and
  diff-checked before it; a `chore(release)` version bump straight after each; the README describing what a player
  meets; every feature play-tested in the dev client with Blockpilot, and what was not verified said plainly.

## The 21, in the order they are built

The order follows what each needs from the ones before, not a ranking.

| # | Feature | What a player meets | Switch |
|---|---------|---------------------|--------|
| 0 | Groundwork | Nothing: the zeppelin's pieces become a list, its cover gets a livery, its build a set of options | |
| 1 | Dressed for night | Red and green navigation lights on the engine cars, a white tail light, double-flash strobes on the fin tips, a belly beacon, and a line of lamps from the nose over the mast to the top fin | `navigationLights` |
| 2 | Sky Deck launch | A parachute rack, a jump light and a paper-plane dispenser on the crown; landings scored at a target by a flare below | `skyDeckLaunch` |
| 3 | Horn and echo | A cord in the control car for a deep two-tone horn heard far off, villagers and animals below looking up; the echo back later the higher it flies | `horn` |
| 4 | Livery board | Paint schemes picked in the control car (Hindenburg silver, Graf Zeppelin cream, navy grey, the airliner's blue stripe, polar red, midnight blue with constellations at night), the new paint sweeping bow to stern behind a painters' cradle | `liveryBoard` |
| 5 | Festive blueprint | A Festive Zeppelin Blueprint: snow on the crown, red and white bands at the rings, green fins, fairy lights along the rings, a wreath on the nose, a gold star for the mast beacon, red and green gas cells, sleigh bells with the engines | (its own blueprint) |
| 6 | Ship's colours | A banner put in a slot at the mast flies as a giant flag from the Sky Deck, and its pattern is painted on the fins | `shipsColours` |
| 7 | Christening | A bottle broken on the nose: doves, and the banner becomes a crest on both flanks, the ship's name beside it | `christening` |
| 8 | Flank screen | A glowing screen down each side, picked from a picture board inside: travel posters and mock adverts, a banner or map art, the faces aboard, or height and speed shown huge | `flankScreen` |
| 9 | Answer the Telegraph | A repeater dial in each engine car clangs to the control car's order; an engineer turns its handle to answer before the engine responds | `engineRepeaters` |
| 10 | Trim car | A weighted car rolling on a rail under the gangway moves the centre of mass, the whole ship pitching; a bubble level shows it, and the autopilot uses it to trim | `trimCar` |
| 11 | Lookout's flag | The Sky Deck telescope drops a flag with the lookout's face onto the autopilot's map, to fly to | `lookout` |
| 12 | Up Ship! | Handlers in white caps hold lines at take-off, water ballast pours from the bow, "Up ship!", and they let go together | `upShip` |
| 13 | Flyby camera | Passenger screens on the promenade chairs and a camera display in the control car, with nose and down cameras and a slow orbiting flyby camera | `flybyCamera` (client) |
| 14 | Searchlights | Two searchlights under the control car, swung from handles in the lounge, a beam and a pool of light on the ground | `searchlights` |
| 15 | Skywriting | A shape picked on a board, and the autopilot flies it across the sky trailing smoke in the cells' colours | `skywriting` |
| 16 | The Great Shadow | The ship's 490-block shadow sliding over the ground, sharp at noon, long at sunset | `greatShadow` (client) |
| 17 | Zeppelin Post | A franking machine prints a postcard that is a map of the land below; mail sacks parachute down to villages | `zeppelinPost` |
| 18 | Breath on the glass | The promenade windows mist in cloud and rain, and can be drawn on | `mistyWindows` |
| 19 | Towed banner | A banner of Create sails trailing from the tail on a rope, its angle showing the speed | `towedBanner` |
| 20 | Sky crane | A hook on a winch from the belly lifts a horse, a boat, a chest or a glued build as a swinging load, a load dial showing the strain | `skyCrane` |
| 21 | Flying carrier | An Aeronautics plane landed on the Sky Deck is locked down by docking clamps and refilled | `flyingCarrier` |

Later: planting the flag (a banner dropped on a spike). Not picked: the ground crew, engine failure, the skyhook,
the water scoop, Topside, the present run, the Hull Hunt.

## Seams

From the scope skeptic, read against the code (`ZeppelinLayout.create` hard-codes its build order, two pieces are
classes already, `coverAt` names its block directly):

- **`ZeppelinPiece`:** a feature's part of the layout, `wanted(ZeppelinOptions)` and `build(ZeppelinLayout)`. The
  layout builds the hull itself (cover, rings, cells, car, decks, gangway, engine cars, fins), then every piece on one
  ordered list that is wanted. Unplugging a feature: delete its class and its line on the list.
- **`ZeppelinOptions`:** what a blueprint asks for (furnished, livery), read once at build time, replacing
  `create(boolean)`; the blueprints, `/aerolarge build` and the Blockpilot command pass it.
- **`Livery`:** how the cover is painted. A built zeppelin's livery is kept by its autopilot and sent to the clients
  with it; the cover's colour is a client-side tint by position, so a repaint changes no block, mass or balloon.
- **Config switches** in the `zeppelin` section, on by default unless a feature is experimental: off, a piece is not
  built, and what already stands goes dark or inert.
- **Rules every piece keeps:** a leaf, never a carrier (nothing else touches the ship only through its blocks, since
  Sable flies off whatever loses touch); never airtight near the cells; its state in its block entity's update tag
  rather than a new payload where it can; its blocks face to face with the ship, checked with `aerolarge:assembly`.

## Decision log

| # | Question | Decided | By |
|---|----------|---------|----|
| 1 | Which ideas | The 21 above, the flag drop later | Jan, idea by idea |
| 2 | The festive look | Its own blueprint, not a choice on the livery board | Jan |
| 3 | Modularity | Each feature pluggable, with its own switch; but a great idea is built even where it cannot be | Jan |
| 4 | Proving the groundwork moves nothing | `aerolarge:layout`, a fingerprint of a layout's blocks: the plain and furnished zeppelins' stayed `c16548f83e2fafef` and `ec5084dc1cbcd0c5` through the refactor | Lead |
| 5 | Lights seen from afar | Their glow drawn out to 512 blocks and never smaller to the eye than half a degree, the airliner's too: at 1.6 blocks across they vanished 220 blocks off | Lead, found in play-testing |
| 6 | Jumping from the Sky Deck | A gap in the deck's rail by the jump light: the rails stop a jump as a fence does, so there was no way off. Out through it a player walks down the crown's curve, over the forward engine car (a perch 40 blocks up) and off the flank | Lead, found in play-testing |
| 7 | Paper planes from the crown | They froze in mid-air 176 blocks out, past the land kept going round any player; now a gliding plane keeps a ticket on the chunks round it, as ender pearls later do, and comes down some 650 blocks off. Planes are scored at landing targets with a best of their own, and a far-off thrower hears the bell | Lead, found in play-testing |
| 8 | The horn's echo | Back a second for each 100 blocks up (10 blocks a tick there and back), slower than sound so it is heard apart from the blast; only in flight, 12 blocks or more up | Lead |
| 9 | How a livery is painted | A client-side tint by where a block sits on the hull, kept on the autopilot and sent with its frame, the slices the paint reaches meshed again: never a block or state swap (100,000 updates, a property on every cover piece), which would also churn the balloons and the assembly | Panel (the engineer and the skeptic in round 1) |
| 10 | The liveries | Silver, Graf Zeppelin cream, navy grey, the airliner's white with a blue cheatline and blue fins, polar red with white fins, midnight blue with gold stars (the stars do not glow: a tint cannot) | Jan's pick, lead |
| 11 | The festive wreath | Two blocks of Aerolarge's own, a garland and a bow: a zeppelin's assembly takes only Aerolarge's blocks (and the configured other mods') outside the hull, so a wreath of vanilla leaves and concrete stayed in the air and held the nose down at take-off. `aerolarge:assembly` now names such blocks | Lead, found in play-testing |
| 12 | The festive switch | The blueprint itself: a festive zeppelin is built only from its own blueprint, so it needs no config switch | Lead |
| 13 | Hearing the sleigh bells | Aboard, round the listener, since the engine cars are far aft of most of the ship; on the ground, from the fastest propeller, loud enough to carry 96 blocks | Lead, found in play-testing |
| 14 | The control car's gap | Faired into the hull: the cover carried down solid from the hull to the car's roof all round, as the Hindenburg's was. The car had hung on a narrow ladder neck with sky round it, looking like a floating box; solid, so no dark hollow spawns mobs, under 1% more mass | Jan spotted it, lead |
| 15 | Ship's colours | A 15-block flagstaff at the aft end of the Sky Deck's centre line, the halyard at its foot; the flag 20 by 10 blocks (two blocks a pixel), the banner's top at the hoist and its left edge along the flag's top, as flag protocol has a banner flown as a flag; it rises over two seconds with a pulley squeak and breaks out with a flap. Painted on the top and bottom fins only, at one block a pixel (40 by 20) in the middle of their outer part with a margin of the livery's colour, the flag's top skyward on both, mirrored seen from starboard as on aircraft tails; the side fins keep the livery, as the Hindenburg's did. The fins take the paint once the flag is closed up at the top, and lose it as soon as it is struck | Panel (historian, designer, skeptic) |

## Outcome

So far (alpha.114): the groundwork (pieces, options, `aerolarge:layout`), 1 Dressed for night, 2 Sky Deck launch
(with paper planes that no longer freeze and are scored at landing targets), 3 Horn and echo, 4 Livery board, 5 Festive
blueprint, 6 Ship's colours; and the hole at the nose's tip mended, and the control car faired into the hull.
