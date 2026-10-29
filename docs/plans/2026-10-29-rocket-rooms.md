# The rocket's rooms, lived in: 2026-10-29

Jan's verdict on alpha.181's rooms: every room has the same walls (her white cover from inside) and the same grey
floor, nothing divides a room into parts, and the furniture stands in tidy rings along the wall. It looks sterile,
fabricated and orderly. He asked for:

- wall paint on the inside only (her cover's inner faces, or a layer of blocks if a face cannot be painted);
- more custom furniture, and sections within the rooms;
- in the greenhouse, more raised beds (Hochbeete) and vegetable patches;
- in the lab, machines growing food;
- mezzanines in some rooms: a cafeteria on a mezzanine over a third of the chillout lounge, which is the observation
  deck (his choice, over a three-story atrium or the club).

## How it is split

| Part | Who | Files mostly |
|---|---|---|
| Inside paint and floors: her cover's inner faces lined per room (the airliner's `Lining` way), each room's floor its own material | Lead | `block/` lining, `client/ShellModel`, `client/ZeppelinPaint`, `RocketLayout`, `RocketLedStrips`, generators |
| Greenhouse: raised beds, vegetable patches, paths, potting bench, compost, set out unevenly | Agent | `RocketGreenhouse` |
| Lab: food-growing machines, partitions, clutter | Agent | `RocketLab`, `LabPlan`, new blocks |
| Observation deck as the chillout lounge, a cafeteria mezzanine over its back third | Agent | `RocketObservationDeck` |
| Entry, cargo bay, mess, bunk room, pool, club: sections, partitions, clutter | Agent | `RocketEntry`, `RocketCargoHold`, `RocketMess`, `RocketBunks`, `RocketPool`, `RocketClub` |

## Rules every part keeps

- The rooms keep clear what they keep clear now: the shaft and its walkway, the way out from the lift's door, the
  front of each window, the door and the lift's doorways (see `RocketRoom.free`, `offTheWays`).
- Unevenness comes from `RocketRoom.scatter()`, a random sequence seeded by the story, so every build of a rocket is
  the same; never `Math.random` or an unseeded random.
- Mass: Sable weighs every block. Mod blocks tagged `#sable:super_light` weigh almost nothing; vanilla blocks weigh
  their own. Prefer mod blocks for anything big, keep vanilla blocks to the detail, and say in the commit how many
  blocks a room gained.
- Every scale from 1 to 4 builds: a room that does not fit leaves things out, never misplaces them. A room at scale 3
  and 4 is wider, not taller (the stories keep their heights; the deck has its own ceiling 16 rows up).
- No text-only cues: anything a player needs has a picture, colour or sound.
- Fun for adults too: playful, never babyish.
- Generators: a new model, texture, loot table, tag or lang key goes into `tools/generate_resources.mjs` or
  `tools/generate_textures.mjs`, never into the files they write. New blocks are tagged `#sable:super_light`.
- AGENTS.md's code style: javadoc in its voice, named constants with their units, two levels of nesting at most.

## Decisions

Kept in the night log's style in `2026-10-28-rocket-night-log.md`, from N60.
