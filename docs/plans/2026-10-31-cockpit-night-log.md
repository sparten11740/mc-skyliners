# The rocket's cockpit: the night of 2026-10-31, for review in the morning

Jan asked, before going out for the night, for work until 6:00:

- play-test and polish until no bugs are left;
- consistent in-game UX, no gaps in the hull, things accessible;
- a panel of agents to make the rocket's cockpit worth being in.

For the cockpit he named:

- seats reorganised, with the instruments within reach of them;
- a boost button that kicks up the thrusters;
- a dangerous button that switches off side stability;
- a flight-mode button for flying on an angle;
- a better dome orrery than the one before.

## How it was done

Three design agents read the code and each proposed one part: the layout and seats, the things to do, and the look of
the dome. Their plans were combined into one, then three implementer agents each built one part in its own worktree,
on files kept apart so their work merged without conflicts. A fourth agent reviewed the controls code and a fifth the
dome and the layout. The lead merged each part, tested it in game with Blockpilot, flew it, and fixed what testing and
the reviews found.

## What she has now (alpha.198)

- **A flight deck.** The control head faces forward with the commander's seat on its left and the pilot's on its right.
  - Past a row of legroom: a flight display in front of each seat, the autopilot screen between them, and a dark
    glareshield running on to the glass.
  - The LED console stands at the commander's left hand; the livery selector, under the camera display, at the
    pilot's right.
  - Jump seats stand behind the pilots, and a row of launch seats looks out of the back glass.
  - The layout is found by search, so every scale from 1 to 4 works. Scale 1 gets a capsule: the head in front of the
    lift with a seat either side. Before tonight, scale 1 put the head inside the lift shaft.
  - From the commander's seat, the far corners of the LED console and the far end of the autopilot screen are within
    reach (tested in game).
- **The autopilot screen** shows only a rocket's own buttons on a rocket: on and off, height and climb. Its map no
  longer draws a heading line.
- **A centre console** of five controls round the head, each with its own picture and lamp:
  - **Boost:** a mushroom button that kicks her up for eight seconds, then rests for twenty. Flown: in cruise her lean
    went from 10° to 13° and her speed across from 10 to 19 b/s. It ends early as she starts down.
  - **Stability switch:** under a striped cover; the cover and the switch each take a use.
    - Switched off, her jets first bring her upright with the alarm sounding, then let her go. She tips over on a
      schedule, swaying as she goes, from 2° to about 14° in about eleven seconds.
    - Switch it back in time and launch control says you saved her. Otherwise it snaps back on short of 15°, with a
      high note and a ten-second lockout.
    - It is refused while boosting or crossing faster than 35 b/s, as it would only snap back at once.
  - **Flight-mode knob:** straight up, the arc, or gentle.
    - The arc leans her toward her pin from about 100 blocks up as she climbs. Flown: 16° by the top of the climb, and
      she came down 9 blocks from her pin.
    - Gentle climbs at 11 b/s.
  - **Abort handle:** in the climb she holds where she is. In the descent the landing is called off and she climbs
    back to her height. Both flown.
  - **Launch key:** turned on the ground she flies, assembling first. Turned back during the countdown she scrubs.
- **The dome**, now a planetarium:
  - a smooth starry sky, the real northern sky, hides the stepped blocks;
  - a machined ring round the oculus has 30 lamps showing the flight phase; it was seen counting down from red to
    green;
  - a rebuilt orrery: round planets lit by the sun on one side, Saturn's ring, Earth's moon and air, a glowing sun,
    and brass arms above the planets on two gears.

  Rockets built before tonight get the sky and the orrery too, and find their autopilot.
- **Flush ceiling lights** replace the pendant lights hanging on cables across the cockpit's glass.
- **After the reviews (alpha.197):**
  - From the cockpit, the dome's orrery is drawn only while the hole in the ceiling is in front of the camera; inside
    the dome, the works and the sun's light only while they are in front of it. Globes no longer allocate each frame.
  - The cockpit ceiling's outer edge is lined as the cockpit, no longer in the dome's starry finish.
  - A control placed by hand links only to a rocket's autopilot, and looks again once that autopilot is gone.
  - A knob turned during the descent still waits for the next flight after a reload.
- **alpha.198:** the stairs' new hinge tube and cable drums sat across the second tread on all three crafts, found at
  scale 1; they now lie under that tread's plate.

## Decisions taken (for Jan to look at)

- **Default on.** The boost, the stability switch and the arc are on by default, after flight tests tonight. AGENTS.md
  asks for experimental behaviour to start off, so they first went in off, and were switched on in a separate commit
  once flown. Each can still be switched off in the server config.
- **The danger was tuned in flight.**
  - As first built, the stability switch let her drift by raw jet pushes and gusts. Flown, that either snapped back
    within two seconds (she was still leaning from her cruise) or tipped her too fast to save.
  - It now brings her upright first, then holds her to a lean that grows on a schedule. That gives about eleven
    seconds to save her, measured in flight.
- **Lights.** The dome agent reused the cabin light block for the flush lights rather than a new cool-blue block.
- **Sizes.** The orrery's orbits were pulled in from the design, so the Moon and Saturn's ring don't run into their
  neighbours.

## Also done tonight

- **Stairs for all three crafts:**
  - the rocket's top step meets her sill with a bridge plate;
  - the rocket's and the airliner's flights roll out on a carriage and rails, rams lift and tip them, and cables pay
    out the sections;
  - the zeppelin's belly stairs show their hinge and cables.

  One shared class draws all three.
- **Touch panels checked in game:** they all light the same way. Checked: the LED panel, LED console, livery selector,
  camera display, announcement panel, flight-deck keys, skywriting board and picture board.
- **The rocket that wouldn't lift off after a reload** did lift off when tried again, both before and after a quit and
  reload. It could not be made to happen again.
- **A rocket jammed against a zeppelin** built right beside her could not lift off. That is a test-setup problem, not
  a bug: two airframes built into each other.

## Not verified

- Climbing into an upper bunk: the test player drops off the ladder, so it could not be checked.
- The new controls flown at scales other than 2. They were seen in place at 1.5 and 4, and the dome was seen from the
  seats at 1.5, 2 and 4, and by night at 2.
- alpha.196 to alpha.198 are not on the desktop PC: it was offline all night.
