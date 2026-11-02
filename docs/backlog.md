# Backlog

What is still open from the plans of the nights from 24 October to 1 November 2026. The plans themselves are done and
gone from `docs/plans`; `git log --diff-filter=D -- docs/plans` finds them, with every decision taken along the way.

## Known issues

- **Leaving a world can hang for good.** On 2026-11-01 (alpha.217) leaving a world in which an airliner flew on its
  autopilot with nobody aboard never finished saving: the integrated server spun in `stopServer`'s chunk unloading
  (`ChunkMap.processUnloads`), so the game had to be killed. Suspects: a keep-loaded ticket of the autopilot or of
  `ParkedShips`, or Sable. The stack dump is in `run/logs/stop-hang-2026-11-01.jstack` (not committed).
- **The airliner can't hold its height at its slowest speed.** Twice on 2026-11-01 (1.0.0's code), set to circle a pin
  at altitude 150 and 30 blocks a second (the lowest the autopilot's screen offers), a furnished airliner sank and
  flew into the ground, nose down at y 100 to 112: once into the rocket's launch pad, once into a hill. Unstuck fired
  the control jets but did not free it. At 50 blocks a second it held its height.
- **A removed ship comes back.** `/sable remove` only drops the loaded copy; Sable's stored copy stays in
  `sable storage find_all_sub_levels` and flies again once its area loads.

## The zeppelin

- A mooring mast and a Moor mode for the autopilot.
- Portholes in the gas cells, a drop room, bounce nets and crawl-ways out to the engine cars.
- A ballast board and a weigh-off balance, a drift telescope, a piano and an Owl's Nest.
- A band of stars on the screen under her natural ceiling.
- A duck hunt of her own (Duck Hunt counts the airliner's ducks only).
- Planting the flag: a banner dropped on a spike.
- The sky crane lifting glued builds, through a Sable rope.
- A plane docked on the flying carrier counted in her pilot's mass.

## The airliner

- A "gear down" voice callout. The voices are made with Windows' speech synthesis, so it waits for a Windows machine.

## The rocket

- A carpet ring round each lift doorway, and lamps over the lab's counters.

## Not yet checked

- By ear: the greenhouse's and the pool's sounds (measured and heard playing in game, but nobody has judged them by
  ear).
- The laser path clearer firing at real terrain while flying at speed.
- Climbing into an upper bunk in the rocket (the test player drops off the ladder).
- The rocket's cockpit controls flown at scales other than 2.
