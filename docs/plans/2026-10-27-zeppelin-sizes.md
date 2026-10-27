# Zeppelin sizes plan, from 27 October 2026

Jan found the zeppelin too big at its one size, 490 blocks long, and asked for three: a third, two thirds and the full
size as it is. Then: "Don't scale the control car etc the same way as the hull." So only the hull shrinks, its cover,
rings, gas cells and fins; the control car, the decks, the engine cars and everything a player stands in or uses stay
the size of a player. The ground rules of the [extras plan](2026-10-27-zeppelin-extras.md) hold.

## The sizes

s is blocks a metre: the hull's 245 m at 2, 4/3 and 2/3 blocks a metre.

| | full | two thirds | a third |
|---|---|---|---|
| length, in blocks | 490 | 327 | 164 |
| hull radius, in blocks | 41.2 | 27.5 | 13.7 |
| axis over the ground, in blocks | 50 | 37 | 23 |
| all the gas cells, in m³ | 148,545 | 44,013 | 5,502 |

- **The control car stays as it is,** 11 blocks wide and 29 rows long, its floor a row over its wheel. Scaled with
  the hull, it would be 3 blocks wide inside at a third. Its front stays 30 m along the hull.
- **The hull rises over it:** its axis stands at 25 m, or higher where the full-size car needs it, so the belly behind
  the car never sinks below the car's roof. At full size that is the same 50 rows; nothing moves.
- **Each ship keeps its size.** A size of its own (`ZeppelinSize`), chosen by its blueprint, saved with its autopilot
  and sent to the clients with it, rather than a server setting as the airliner's scale is, so ships of every size
  fly side by side. A ship saved before has none, and is full size.

## What each size has

- **Full:** everything, as now.
- **Two thirds:** everything. Both decks fit inside the hull's full-radius middle, narrower; the techno room moves into
  metres along the hull, 165 to 172 m, as it would otherwise hang past the stern; the painted art is drawn at fewer
  blocks a picture's pixel.
- **A third:** one deck (the promenade, the dining tables and the bar) where there is room for one; no cell gallery,
  whose gantry would break its cell's ceiling; no techno room, whose box the rings cross; no lower deck (the boarding
  hall, the galley, the bunks); no banner on the fins or crest on the flanks, which have no room for them.
- **The lower fin** stops short of the ground under a raised hull: a girder tail skid carries the stern wheel down.

## Steps

Each builds, is play-tested and committed on its own. A piece says which sizes it fits (`ZeppelinPiece.fits`), at
first full size only, each step widening it for the pieces it finishes.

1. **Groundwork:** the size in `ZeppelinOptions`; the shape's metres times the ship's blocks a metre instead of the
   fixed `SCALE`; heights from the axis, not the ground; the car, the decks' heights and the engine pods in blocks.
   Gate: `aerolarge:layout` gives the same block count and fingerprint for every full-size zeppelin.
2. **The ship remembers its size:** saved and sent with its autopilot; the livery's spots, the fin and flank art, the
   flank screen, its cameras and the renderers take it. Gate: a world from alpha.127 loads, paints and shows its
   cameras as before.
3. **The hull at each size,** for testing: `aerolarge:build` and `/aerolarge build` take a size; the raised axis, the
   full-size car, the decks, the pods and the tail skid. Gate: `aerolarge:assembly` leaves nothing behind, every cell
   forms its balloon; the mass is noted; a picture of each for Jan before going on.
4. **Lift at each size:** each cell's burners and their output from its volume, its full burn saved with the pilot.
   Gate: takes off, climbs and cruises level.
5. **Handling at each size:** the thrusters' output and the turning and speed gains for its mass. Gate: turns without
   hunting, lands within 10 blocks of a pin, circles.
6. **The pieces, size by size:** the car's own (all sizes), the engine rooms, the Sky Deck and what stands on it, the
   lights and the festive dress, Up Ship and the trim car in metres, the deck furniture and screens, the techno room
   and the cell gallery at two thirds, the painted art.
7. **The blueprints:** each zeppelin blueprint at three sizes in the creative tab, its size shown by its picture as
   well as its name; the README.

## Risks

- **Lift at a third.** Its cells hold 27 times less gas than the full ship's, while the car and the deck keep their
  weight: if it cannot float, its cells fill more of its hull or more rooms are left out. Its mass is measured first.
- **Handling.** The same engines push a much lighter ship: their thrust and the pilot's gains go with its mass.
- **Looks.** At a third the car is two fifths of the hull's width. Jan sees it before the rest is built.

## Half size, from the same day

Jan, seeing the first smaller ships: "I'm also fine with just supporting two sizes, like half the size of what the
biggest one is right now, and the current size", and "don't scale everything proportionally ... some things could
just be like 25% smaller, some things 10% smaller, so that it still looks good, but you can still do everything". A
planning agent laid half out against the layout's own rules: at 1 block a metre (245 blocks) every feature fits where a
player can use it, the decks their full 80 rows with 11 to spare and two decks under the gangway; 0.92 blocks a metre
is the least that does. Each part shrinks by its own share:

| Part | Shrinks by | Why |
|---|---|---|
| The hull: cover, rings' spacing, nose, taper, fins, gangway's length | half | the size asked for |
| Gas cells | about 40% across | at half they barely float her (8,260 m³ of gas for some 11,000 of mass) |
| Rings' depth, the cover and envelope's thickness | nothing | they are a block thick |
| The control car, decks, stairs, furniture, gangway's width, Sky Deck, mast, cell gallery | nothing | a player uses them; the decks' width follows the hull |
| Engine cars | a quarter across and from the hull, a fifth in length, their propellers a quarter | at full size they looked oversized on her |
| The engine room in the car | headroom 4 to 2 rows, grating 13 to 9 | still a room to stand at the repeater |
| Techno room | a quarter in length (15 to 11 rows inside) | to fit one bay between the gangway's posts |
| The trim rail, Up Ship's lines, the flank screen, the flyby camera | half | they are laid along the hull |
| The painted art | half (two picture pixels a block) | the full ship's proportions |
| The festive wreath | a quarter | at full width it would look twice as big on the nose |
| Lights' spacing, the skywriting shapes | nothing | a smaller ship only turns tighter |

Steps, each built, played and committed alone: half in place of the third and two thirds; the engine cars at each
size; the car's pieces; the Sky Deck, the colours, the lights and the festive dress; the decks, screens and flank
board; the cell gallery; the trim car and Up Ship; the techno room; the painted art and the flank screen; and half
offered by the blueprints and commands.

## Decision log

| # | Question | Decided | By |
|---|----------|---------|----|
| 1 | The sizes | A third, two thirds and full | Jan |
| 2 | What shrinks | The hull only; the car, rooms and fittings stay player-sized | Jan |
| 3 | Where the size lives | Each ship's own, from its blueprint, saved with its autopilot; none saved means full | Lead, from the planning agent's survey |
| 4 | The car under a smaller hull | Unchanged, its front 30 m along; the hull's axis raised to clear its roof | Lead, from the survey |
| 5 | A third's lift | Found in play-testing: with the full ship's cells scaled down, a third's held only some 1,500 m³ of gas inside their block-thick envelopes, too little for her 4,400 of mass, and four of her cells' burners fitted at all. Her cells fill the room over her gangway (half 16 m wide and 9 m tall at their fullest, set a little lower), the gangway laid over her single deck's roof rather than on the axis, holding some 5,000 m³; mass 5,502 with them, she floats at about half burn. At two thirds the cells are as at full size (26,000 m³ for 15,900) | Lead, found in play-testing |
| 6 | Burners at every size | As many as the floor's breadth takes, no more than fill what the cell holds, at least one wherever there is room; each turned down by its value box where together they would give more than the cell holds, the pilot working each cell by its own most. At full size only the last cell changes: one burner where two gave twice what it holds | Lead |
| 7 | Two sizes | Half (1 block a metre) and full, in place of the third and two thirds; a ship saved at a third or two thirds loads as half, the nearest left | Jan |
| 8 | What shrinks at half | Each part by its own share (the table above), from the planning agent's layout survey | Lead, from the survey |
| 9 | Half's gas cells | Gap 1 m, half 13.5 m wide and 7.5 m tall at their fullest, their middle 0.55 of the radius over the axis: no nearer than 1.5 blocks to the cover, two rows of headroom over the gangway. Found in play-testing: mass 10,753 bare, she climbs at 2 blocks a second to her ceiling of some 117 (175 over the ground) and holds level there, her cells at signals 10 to 14 | Lead, found in play-testing |
| 10 | Half's engine cars | Pod radius 2.4, 15 rows long, its nose rounding over 3, 6 blocks off the hull, the propeller's blades 4.5 (its reach now saved with each propeller, 6 for one saved without); inside, the grating a row under the axis with two rows of headroom, 9 rows long. Found in play-testing: with the full ship's engines she hunted, full ahead and stopped by turns, her speed swinging from -3 to 16 b/s about the 8 asked; her engines pushing a third as hard, as she weighs a third as much, she settles on 8 within a minute | Lead, found in play-testing |
| 11 | The car's pieces at half | The horn cord, the livery board, the ship's plaque, the Lookout's Glass, the searchlights, the skywriting board, the floodlights and the speed boost are laid from the car's own corners (the floodlights' keel ones at shares of her length), the same at every size: all found aboard a half-size ship built bare, nothing left behind by her assembly | Lead, found in play-testing |
| 12 | The Sky Deck, colours, lights and festive dress at half | Laid round the ring the shaft climbs at and the hull's own tips, wherever they stand at the size; the wreath round the nose 4 to 5.5 blocks out instead of 5.5 to 7.5. A festive half-size ship built and photographed: the deck, its gantry, rack and starred mast, the flagstaff, the crown bulbs and fairy lights, nothing left behind | Lead, found in play-testing |

## Outcome

Steps 1 to 4 (alpha.128): the size in each layout and each ship, the hull at each size, and lift at each; flown at all
three by command.
