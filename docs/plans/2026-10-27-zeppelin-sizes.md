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

## Decision log

| # | Question | Decided | By |
|---|----------|---------|----|
| 1 | The sizes | A third, two thirds and full | Jan |
| 2 | What shrinks | The hull only; the car, rooms and fittings stay player-sized | Jan |
| 3 | Where the size lives | Each ship's own, from its blueprint, saved with its autopilot; none saved means full | Lead, from the planning agent's survey |
| 4 | The car under a smaller hull | Unchanged, its front 30 m along; the hull's axis raised to clear its roof | Lead, from the survey |

## Outcome

Planned (alpha.127).
