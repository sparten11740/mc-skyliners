# Cockpit redesign

Jan's request (2026-10-25):
- The pilots' seats closer together.
- Four or five separate screens curved toward the seats: the middle one straight, the next ones to either side slightly
  angled, the outer ones more angled.
- A middle console between the seats that opens the autopilot (the same settings window as now).
- The autopilot unit on that console shows the current settings.
- Engaging the autopilot assembles the airliner if it can.
- A thinner announcement panel on the roof.

Decided with Jan:

| # | Question | Decided |
|---|----------|---------|
| 1 | What the five screens show, left to right | Captain's flight display (PFD), moving map, autopilot screen (straight, in the middle), tail camera, first officer's flight display |
| 2 | Which "autopilot panel indicator" shows the settings | The autopilot unit on the console between the seats gets its own small display |
| 3 | "Thinner" announcement panel | It hangs less deep from the ceiling (12 px today); confirmed by Jan |

Everything below applies to newly built airliners. Airliners built before keep their flight deck; only the thinner
announcement panel, being a block shape, changes on them too. Scales 2 to 4 get the new deck; scale 1 has no flight
deck, and noses too narrow for the centre console keep today's narrow layout (`narrowControls`).

## 1. Seats closer, console rearranged

- `SEAT_X` goes from 3 to 2: the seats stand two blocks either side of the middle, with a three-block console between
  them (x -1 to 1).
- Rows, counted back from the autopilot screen (`screenZ`):
  - Row +1, ahead of the pilots' knees: the throttle quadrant. Thrust levers at x -2, -1, 1, 2 (engines 1 to 4, as now),
    the parking brake in the middle. Wider than the console behind it; the pilots' legs stay clear in row +2.
  - Row +2, beside the pilots' legs (x -1 to 1): seatbelt switch, nose wheel tiller, jump light switch.
  - Row +3, between the seats (x -1 to 1): cabin lights dimmer, **the autopilot unit** (section 3), captain's handset.
  - Outboard of the seats (x -3 and 3), rows +2 and +3, where the nose leaves room: laser switch and window shades on
    the captain's side; gear lever and drop release on the first officer's side. Each faces the seat beside it.
  - The transponder goes on the instrument panel past the left end of the screen arc, where it fits; otherwise it is
    left out, as today when there is no room.
- `consoleStation` checks the new footprint: seats, console, side consoles and the screen arc (section 2). Without room
  for all of it, the deck falls back to the narrow layout as before.
- Unchanged: the Physics Assembler's spot behind the captain's seat, the cockpit door, the crew rooms.

## 2. Five curved screens

Layout, seen from the seats, in the layout's nose-north frame:

```
         [ map ]  [ AUTOPILOT ]  [ camera ]
 [ PFD ]  22.5°    straight 0°     22.5°    [ PFD ]
  45°                                         45°
     (captain)        console       (first officer)
```

- **Middle: the existing autopilot screen** (3 x 2 blocks, touch screen with map and buttons), straight, where it stands
  now. The tail camera display no longer stands on top of it; it gets its own panel.
- **Four angled panels**, each about 2 blocks wide and 2 tall, standing on the deck like the autopilot screen. Each
  hinges on the outer edge of the one inside it and turns a further 22.5 degrees toward the seats: the inner pair
  (moving map on the left, tail camera on the right) at 22.5 degrees, the outer pair (the PFDs) at 45. At scale 2 the
  arc spans about 9.5 blocks and curves back about 2 blocks toward the seats, following the nose's taper.
  - The widths shrink, down to 1.25 blocks, where the nose is narrower.
  - A pair that still does not fit is left out, outer pair first.
- **New block `CockpitDisplayBlock`**, a multi-block panel:
  - The panel's cells are listed by the layout. Part 0 holds the block entity, and the other parts pass a click on to
    it.
  - Block state: FACING (the way the straight panel would face, toward the seats), TURN (22.5 or 45 degrees, left or
    right) and PART.
  - The block entity: what it shows (PFD, MAP or CAMERA), its size, and a link to the autopilot (for the map's track
    and targets), set through a `Fitting` and turned with `Turning` like other links. The camera panel also keeps
    which camera it shows.
  - Each cell's collision is a thin box along the panel's line through it. The block outline is hidden
    (`RenderHighlightEvent`), so aiming at an angled panel draws no boxy outline.
  - Clicks: the server casts the player's look ray onto the panel's plane, giving a point on the screen. The camera
    panel uses it for its camera buttons and the map panel for zooming, as the autopilot screen does.
- **New `CockpitDisplayRenderer`**:
  - Draws the bezel and the screen as one turned quad from part 0, and only from in front, as `Sight` tells it.
  - What is on the screen comes from the existing renderers, split into helpers that draw into any rectangle:
    `FlightDisplayRenderer`'s attitude/speed/altitude/heading display, the moving map from `AutopilotScreenRenderer`,
    and the feed, test card and camera buttons from `TailCameraDisplayRenderer`.
  - The old blocks call the same helpers, so airliners built before look as they do now.
  - `TailCameraFeed.show` takes a small `CameraScreen` interface (level, position, camera) instead of the tail camera
    display's block entity, so both kinds of camera screen share one feed.
- The two small flight displays set into the instrument panel at x ±3 go: the PFD panels replace them. The instrument
  panel row and glareshield stay as the desk the screens stand in front of.
- Where the screens leave no room for the camera panel, the tail camera display hangs from the crown as it does today
  (`tailCameraDisplay`).

## 3. Autopilot unit between the seats, showing its settings

- The autopilot's control head (`AutopilotBlock`) moves from the console's front row to row +3, between the seats.
  Using it opens the same settings window as now (`OpenAutopilotScreen`).
- Its face gets a small display, drawn on the client from what the autopilot already sends (`getUpdateTag`):
  - a lamp, green while it flies the airliner and dark when off;
  - the mode as a picture (take-off, climb, cruise, circle, landing), in the art the autopilot screen already uses;
  - target height with an up arrow, target speed with a speedometer, heading with a compass needle (or the
    destination pin's direction);
  - numbers beside the pictures, so it reads for children who cannot read yet and for those who can.
- `AUTOPILOT_ENTITY` already has a renderer (`ControlSurfaceRenderer`, for the control surface panels). A small
  `AutopilotRenderer` draws the head's display and hands the rest to the control surface drawing, which stays as it is.
- The head's shape stays a console module; the display sits on its sloped face toward the seats.

## 4. Engaging the autopilot assembles the airliner

- Today, Fly on an airliner that is not assembled answers "not assembled".
- New: if the airliner has its Physics Assembler, the autopilot uses it the way a Create deployer would. That is a
  `DeployerFakePlayer` using the block: the assembler assembles at once for a deployer, while a real player gets its
  hold-the-lever screen. Create is compiled against already, and Simulated stays unnamed (only `assemblerBlock` from
  the config), as `AGENTS.md` asks.
- The autopilot's block entity moves into the new airliner's plot as it assembles. So it first keeps a "fly these
  settings once aboard" note, saved with it, and the moved autopilot engages with those settings on its first tick
  aboard. The player is told the airliner assembles, then the usual "autopilot engaged".
- Where the assembler is: an offset from the autopilot, wired at build time through a `Fitting` (from `assemblerSpot`)
  and turned with `Turning`.
  - An airliner built before has no such link: the autopilot looks for an assembler once, in a bounded box round the
    flight deck, and remembers it.
- Without an assembler, or if assembly fails (Simulated's own reason, e.g. too many blocks), the player gets the old
  message, plus the reason.
- Taking off from there is unchanged: stowing the airstairs and slides, the gear check, clearance from the tower.

## 5. Thinner announcement panel

- `AnnouncementPanelBlock.DEPTH` goes from 12 px to 4: it hangs a quarter block below the ceiling instead of three
  quarters. Its renderer draws the tiles on the underside at the new depth.
- Headroom under it grows by half a block; `ANNOUNCEMENT_PANEL_HEADROOM` and the boxed-in mount above it stay as they
  are.

## Testing (blockpilot)

- Build airliners at scales 2, 3 and 4 (and check scale 1 still has no flight deck):
  - screenshots from both seats, from behind the console and from the door;
  - walk the deck for floating or misplaced blocks;
  - confirm every control works: levers, brake, tiller, switches, gear lever with its lamps, window shades, drop
    release, handset.
- Screens:
  - each panel's content (PFD moving with the airliner in flight, the map's track, the camera feed and its buttons);
  - clicks on the angled camera panel picking cameras;
  - no outline drawn on the angled panels;
  - `assert pixels` on the screens' regions once blockpilot's pixel probe lands.
- Autopilot unit:
  - the display changes when settings change, and the lamp follows engaging and switching off;
  - the window opens from the unit.
- Auto-assemble:
  - on a freshly built airliner: Fly from the unit, and it assembles, engages and takes off;
  - without the assembler: the old message;
  - on an airliner built before alpha.74: the bounded search finds its assembler.
- Announcement panel: thinner from the seats; tiles still pressable.
- Narrow layout: a nose too narrow for the console builds as before.
- Performance: block entity renderers' time per frame on the flight deck, before and after (blockpilot `perf`).

## Commits

One per feature, each followed by its own `chore(release)` version bump, `README.md` updated with each:

1. `refactor(client)`: split the flight display, moving map and camera feed drawing into shared helpers (no visible
   change).
2. `feat(airframe)`: seats closer, console rearranged, the curved screens (`CockpitDisplayBlock`).
3. `feat(autopilot)`: the settings display on the autopilot unit between the seats.
4. `feat(autopilot)`: assemble the airliner when the autopilot is engaged on the ground.
5. `feat(block)`: thinner announcement panel.

## Open questions

- The side consoles take the laser switch, window shades, gear lever and drop release. If the nose is too narrow there
  at scale 2, the gear lever moves to the console's legs row in place of the tiller, which then goes beside the
  throttle quadrant.

## Changed while play-testing

- The angled panels are one block tall, level with the instrument panel, not two. Seated, a pilot's eyes are about
  1.4 blocks over the floor, so two-block panels hid the windscreen; and the nose at scale 2 is two blocks narrower a
  row up, where the outer pair would not fit at all. The panels stand in a single row of blocks, and their "cells" are
  only those the face runs through (the slab's back only needs to be open).
- The panel block is one block type with FACING, HOLDER and SHOWS (FLIGHT, MAP, CAMERA); the block holding a panel
  keeps its corners in the facing's frame, so it needs no `Turning`, and finds the autopilot screen beside it by a
  bounded search instead of a link.
- The station is the one that leaves the arc the most width (the first of equals), not the first that fits: at scale 2
  that is three rows further back than before, where the whole arc fits at full width. The door stays where it was.
- The instrument panel carries on round behind the arc to the hull (facing the nose, so its screens stay hidden).
- The console's first two rows swapped: the thrust levers right in front of the seats (the pilots look over them at
  the screens; two rows ahead they hid the inner panels), the tiller and switches just behind the screens.
- The side consoles: laser switch and window shades by the captain, the transponder behind them; gear lever and drop
  release by the first officer.
- The autopilot unit's display uses the settings window's own pictures (`PanelArt`'s icons) and turns a quarter at a
  time to face whoever looks at it, since the pilots sit either side of it.
- Engaging from a seat: assembling takes the seat block out from under the pilot (Create's seat entity removes itself
  without its block), so the autopilot sits the pilot who asked back down in the same seat once aboard.

## Second round (2026-10-25): raised screens over a sloped touch panel

Jan's feedback on alpha.79 to 81: a single-block centre console, the screens higher, and the console's switches as
touch controls on a sloped panel under the screens; the middle screen used for controls rather than a second map.
Decided, each control in exactly one place:

| Where | What |
|-------|------|
| Middle console, one block wide, the seats right beside it | One throttle block showing all four levers (one lever wired to all four engines, as they moved together anyway), the autopilot unit with its display between the seats, the handset behind it, the transponder behind that |
| Screens, 1 to 2 blocks over the floor | Captain's flight display, moving map, systems screen (middle, straight), outside camera, first officer's flight display |
| Middle screen: systems page | Touch tiles, each showing its control's state in that control's own picture: cabin lights, seatbelt signs, jump lights, window shades, laser, drop release |
| Sloped touch panel (45 degrees) under the screens | Under the captain's flight display the tiller (left, right); under the map the parking brake and the landing gear with its lamps; under the systems screen the autopilot's buttons (on/off, heading, circle, altitude, speed, circle size) |

- The switches' blocks stay as they are, with all their wiring, but out of sight: in an avionics bay under the raised
  flight deck floor (two rows high at scale 2), as on the A380. A touch on a tile or a key uses the block there for
  whoever touched it, sneaking passed on (the drop release closes the bay on a sneaking touch, as before); the tiles
  and keys show the blocks' states, which the client reads from the blocks.
- The autopilot buttons step a setting while it flies (heading 15 degrees, altitude 20, speed 5, circle 50, circling
  either way); switched off, any of them opens the settings window, as the old screen did.
- The autopilot screen block is no longer built into new airliners; the map panel keeps its own track and zoom.
- The arc: the systems screen 3 or 2 blocks wide, the pairs turning 22.5, 30 or 37.5 degrees a step (the outer pair
  twice that) and 2 to 1.25 blocks wide, whatever fits widest where the nose is narrowest a row up.

Found while play-testing the second round:

- At scale 2 the outer pair only fits with the inner pair narrowed (1.25 blocks at 22.5 degrees, then 1.25 at 45):
  the row tries every inner width rather than the widest first.
- A pointer: looking at a screen or a slope from within reach, a white dot follows the look and the key under it is
  outlined, so whoever is about to touch sees what the touch will work (Jan's request).
- The Physics Assembler's spot is measured before the console, whose transponder now stands in that row's middle.
