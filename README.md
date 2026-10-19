# Aerolarge

A Minecraft mod for NeoForge 1.21.1: an A380-style double-deck airliner big enough to build in, that rolls on real
wheels and is flown from its own cockpit. One blueprint lays the whole airframe down: a single long hollow hull with
a cabin floor over a cargo hold, six passenger doors and a cargo door, a flight deck behind a wrap-around windscreen,
swept wings with four engines, the tail, landing gear and folding airstairs.

## Requirements

Minecraft 1.21.1, NeoForge 21.1.251+, [Create](https://modrinth.com/mod/create) 6.0.10+ and
[Create Propulsion](https://modrinth.com/mod/create-propulsion). To assemble and fly the airliner, also
[Create Aeronautics](https://modrinth.com/mod/create-aeronautics) (Sable, Simulated and Offroad); without it the
airliner is a building.

## In game

Both blueprints are in the Aerolarge creative tab; there is no recipe for them.

- **Airliner Blueprint**: the airframe with an empty cabin.
- **Furnished Airliner Blueprint**: the same airframe fitted out, front to back between the doors, with an indoor
  pool sunk into the hold, a full-height gym hall (Turnhalle) with a court, hoops, wall bars, climbing ropes and a
  row of seats, and a lounge with a bar and seats in the tail, all lit by pendant lights. The seats are Create's, so
  they can be sat in.

Use a blueprint on the ground: the nose goes just past the block you click and the tail stretches away the way you
face. It refuses while anything is in the way and says where; sneak to build through it. Operators can also run
`/aerolarge build <pos> <north|east|south|west> [furnished]`.

At the default scale (2 blocks per metre, set by `scale` in the server config) the airliner is about 145 blocks long
and 160 across the wings, and its cabin is about 14 blocks wide and 11 tall above the floor. Its hull is skinned in
blocks cut to the fuselage's curve; cabin lights in the crown, the walls and the hold and LED strips along the tops
of the cabin walls keep it bright, and nothing spawns inside it on its own. Overhead lockers line both cabin walls
above head height (not in the gym hall), each holding a cabin bag's worth like a small chest. Outside it carries red and green navigation lights on the wingtips with white strobes,
a white tail light and red beacons above and below the fuselage. Each engine is heard spooling up and down with its
thrust, quieter from inside the cabin, and high up it leaves a contrail.

### Flight deck

Steps at the front of the main deck lead up to the flight deck, raised so the pilots look straight out through the
windscreen: tinted glass cut to the nose's curve like the skin around it, framed into panes by posts.
On the pedestal between the two seats are four thrust levers, engines 1 to 4 from left to right. They move together:
use any of them to push all four a notch forward, sneak and use it to pull them back. Each drives its engine's
thrusters through relays hidden in the nacelle. Beside the captain's seat is the nose wheel tiller (use it to steer
right a notch, sneak and use to steer left) and beside the first officer's the parking brake, set when the airliner
is built. A primary flight display in front of each seat shows attitude, airspeed,
altitude, vertical speed and heading once the airliner is assembled. Against the wall behind the captain's seat is the
Physics Assembler.
On the instrument panel beside the autopilot screen are the jump light switch (see Skydiving), the seatbelt switch,
the captain's handset and the cabin lights' dimmer. The dimmer steps every light in the cabin from bright to dimmed
(as for take-off and landing) to a blue night glow and back, the change running down the cabin front to back. On
the captain's side console, just behind the tiller, is the window shades' control: it runs every cabin window's
shade down, or up again, the same way, with the whirr of their motors. A closed shade shuts out the daylight; anyone
can open or close a window's own shade by using it.

A synthetic voice calls out the take-off (V1, rotate, positive rate) and the landing (the height above the ground
from a hundred down to ten, then retard), and warns of too much bank or sinking too fast too low, whoever flies.
Riding the airliner, the camera rumbles on the runway, thumps on touchdown and sways a little in flight; turn that
off with `cameraShake` in the client config.

### Announcements and the radio

Use the captain's handset and your next chat message is a cabin announcement: everyone aboard hears the two-tone
chime and sees it on screen under "Captain" and your name, and it is kept in chat. Sneak and use it, and the message
is a radio call instead. A Tower Radio (craftable) is for the airfield: whatever is said into it goes out over the
radio. A radio call crackles in for everyone near any radio within 4096 blocks, the handsets of airliners in flight
included. Either way the message is left out of ordinary chat; use the handset or radio again to put it down.

### Seatbelts and turbulence

The seatbelt switch lights the "fasten seatbelts" signs along both cabin walls, with a ding. While they are on,
anyone seated aboard stays in their seat when they sneak to get up. In flight the airliner runs into rough air now
and then, a few seconds of it about once a minute: everyone aboard feels the shake, and whoever is not sitting down
is thrown about the cabin. Turn it off with `turbulence` in the server config.

### Autopilot

The autopilot is worked from the big screen in the middle of the instrument panel, or switched on and off with the
autopilot panel on the pedestal. Engaged on an assembled airliner standing on the ground, it takes off: brakes off,
full thrust down the runway, holding the heading it started on with the nose wheel, nose up at rotation speed, then
a climb to the cruising height (`climbHeight`, 80 blocks above the runway by default), where it holds height and
speed (`cruiseSpeed`). Engaged in the air, it holds the height, speed and heading it finds. Switching it off hands
the airliner back to the thrust levers, the tiller and the parking brake. While it flies it shows its phase, speed,
height, pitch, bank and heading to everyone on the flight deck.

The screen's right side is its buttons: AP on and off; turn left or right 15 degrees; circle left or right, round a
point beside the airliner (press the same one again to fly straight on); height up or down 20 blocks; speed up or
down 5 blocks a second; and the circle's radius, 150 to 1000 blocks. Its left side is a moving map,
heading up: the track flown, the circle being flown or the heading being turned to, and north; touch the map to
zoom it out, and again past the widest to zoom back in.

The airliner has no control surfaces, so the autopilot flies with thrusters only: besides the engines there are
control jets on top of and under the outer tailplane (to pitch it) and the outer wings (to roll it). It turns by
banking, so the wings' lift turns it. Sable's wings lift with the airspeed even flying level, and the engines push
hard along the nose, so it learns the pitch that holds the height at its speed as it flies. Part of the tailplane
pushes down and the fin's lift cancels out, so the airliner keeps its nose up and runs straight at speed. It needs
Sable (Create Aeronautics) to read where the airliner is.

Switched off, the autopilot still keeps the airliner safe while it is flown on the thrust levers. In the air the
control jets damp quick pitching and rolling and push it back once the nose goes more than 25 degrees up or 20 down,
or the wings more than 35 either way, righting it even from upside down. Past 90 blocks a second, or climbing fast
enough to coast past the top of the world, it cuts the engines itself, and hands them back to the levers once the
airliner has slowed to 75 and is well below the top again. Without that, full thrust would loop the airliner or
fling it off faster than the world can be generated around it.

### Basketball

The gym hall's hoops are real: take a ball from the rack in the corner by either hoop and use it to throw it the way you
look, sneaking for a softer throw. It bounces off the floor and walls and rolls to a stop; walk over it or use it to
pick it up, hit it to knock it on. A ball that drops through a hoop scores two points, or three thrown from beyond
the three-point line, for the side the hoop counts for: HOME at the front hoop, GUEST at the back (sneak and use a
hoop to swap it). The scoreboard over each backboard keeps the score; use it to sound the buzzer and start again.
Hoops, balls, scoreboards and racks can all be crafted to build a court anywhere.

### Skydiving

By the aft passenger doors and the cargo door are a red and green jump light in the wall and a rack of parachutes.
Use a rack to put a parachute on (or take one into your inventory if your chest slot is taken), then jump while
falling to open it; it opens by itself if you are still falling free close to the ground. Under the canopy you sink
gently, drift the way you walk, take no fall damage, and it packs itself away when you land. The jump light switch on
the instrument panel cycles every jump light in the airliner through off, red (stand by) and green (go, with a bell).

Put a Landing Target on the ground at the drop zone: every parachute landing within 64 blocks of it is called out to
everyone nearby with its distance from the middle, and using the target shows the best landing so far.

### A sturdy frame

The airliner's frame and controls (hull, wings, engines, gear, windscreen, flight deck and the Create and Propulsion
blocks it is built with) cannot be mined, broken in creative mode or blown up; the cabin floor and everything built
on it can. Sneaking with a wrench (Create's will do), or sneaking in creative mode, still breaks them: to take an
airliner apart on purpose, or to take back a panel put in the wrong spot.

### Airstairs

The forward port door has folding airstairs. The Airstair Controller set into the hull beside the door folds them
away, step by step from the bottom up once nobody is standing on them, and unfolds them again from the door down
until they meet the ground. Work it with the button beside it inside or outside, by using the panel itself, with any
redstone signal, or from the ground with the yellow call button on the front of the nose gear.

### Flying it

The finished airliner is covered in Create's Super Glue, in overlapping stretches the Physics Assembler can find from
anywhere in it; that is what it takes as the structure, so everything you build inside it flies along. Fold the
airstairs away and pull the thrust levers to idle, then assemble it. It rolls on Offroad wheel mounts fitted with
large tires, their suspension at its stiffest; the main gear stands just behind its centre of mass, worked out from
Sable's block masses, so it rests on its nose gear. The wings and tail are built around a layer of Create Propulsion
wing blocks, and every airframe block is in `#sable:super_light`. Which blocks are used is set in the server config
(`wingBlock`, `engineBlock`, `assemblerBlock`, `wheelMountBlock`, `tireItem`), and so is how hard the creative
thrusters push (`thrustSetting`, on their own 1 to 99 scale: 99, their full rating, by default).

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew runClient -PquickPlayWorld=<save>` loads straight into
`run/saves/<save>`; drop Create, Sable, Create Aeronautics and Create Propulsion into `run/mods` to run with them.

The geometry lives in `AirframeShape` (real A380 measurements in metres), is cut into blocks by `AirframeLayout`
and fitted out by `CabinFitOut`; the skin follows the curve in `ShellGeometry`'s shapes, which `ShellModel`
bakes, and `Windscreen` says where the glass and its posts go; `AutopilotBlockEntity` flies, reading the airliner
from Sable through `SableFlight`. Assets are generated: `node tools/generate_textures.mjs` writes the textures
(ordinary PNGs that can also be painted by hand), and `node tools/generate_resources.mjs <vanilla-assets-dir>` the
blockstates, models, loot tables, tags, recipes and lang file. The door and the call button are vanilla's iron door
and stone button retextured, so their files come from vanilla's: pass the folder the client resources jar
(`build/moddev/artifacts/*-client-extra-*.jar`) is extracted to, or leave it out to keep them as they are.
`node tools/generate_sounds.mjs` writes the sounds with FFmpeg: the engine loop is synthesised, and the callouts are
spoken by Windows' own speech synthesis ("Microsoft David"), so that part runs on Windows only.

## License

All Rights Reserved.
