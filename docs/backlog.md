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
- **Ships far from the world's middle move in steps.** Sable's physics rounds a ship's position so coarsely out there
  that slow speeds vanish while its speed still reads as moving: at x 111,600, told two blocks a second, the freighter
  went 0.3 blocks in ten seconds, and told three it went at nearly six. The freighter's landing copes by nudging herself
  closer. The airliner, the zeppelin and the rocket have not been tried out there.

## The freighter

- Create Propulsion's plasma plumes on her mains, big pale balls, hide much of her engine grille at full thrust.
- On natural ground she still needs a clearing: at four pins in a default world (seed 12345) she came down at one, on
  plains, and held over three, in snowy forest and rivers. Her legs telescope for slopes, but trees and water under a
  disc 60 blocks across still turn her away.

## Not yet checked

- By ear: the greenhouse's and the pool's sounds (measured and heard playing in game, but nobody has judged them by
  ear).
- The laser path clearer firing at real terrain while flying at speed.
- Climbing into an upper bunk in the rocket (the test player drops off the ladder).
