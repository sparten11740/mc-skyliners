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

Both blueprints are in the Aerolarge creative tab; there is no recipe for them. The tab sorts everything under
banners: blueprints, airframe, flight deck, cabin, gym hall, skydiving and drops, and the airport.

- **Airliner Blueprint**: the airframe with an empty cabin.
- **Furnished Airliner Blueprint**: the same airframe fitted out, front to back between the doors, with a business
  class just behind the forward doors (rows of big seats, two side by side left and right of the aisle, each with a
  high back and wooden consoles beside it, on a blue carpet), an indoor moon pool sunk into the hold, its bottom and
  the belly under it glass, so swimmers look straight down through the airliner at the ground or the clouds below, a
  full-height gym hall (Turnhalle) with a court, hoops, wall bars, climbing ropes and a row of seats, and a lounge
  with a bar and seats in the tail, all lit by pendant lights. The seats are Create's, so they can be sat in.
  Passenger screens (see below) line the cabin walls between the windows, and wherever seats stand in rows across
  the cabin, they hang on the back of each row, facing the one behind.

Use a blueprint on the ground: the nose goes just past the block you click and the tail stretches away the way you
face. It refuses while anything is in the way and says where; sneak to build through it. Operators can also run
`/aerolarge build <pos> <north|east|south|west> [furnished]`.

At the default scale (2 blocks per metre, set by `scale` in the server config) the airliner is about 145 blocks long
and 160 across the wings, and its cabin is about 14 blocks wide and 11 tall above the floor. Its hull is skinned in
blocks cut to the fuselage's curve, and two rows of tall cabin windows run its length down each side, cut to the curve
with it; cabin lights in the crown, the walls and the hold and LED strips along the tops of the cabin walls keep it
bright, and nothing spawns inside it on its own. Overhead lockers line both cabin walls
above head height (not in the gym hall), each holding a cabin bag's worth like a small chest. Outside it carries red and green navigation lights on the wingtips with white strobes,
a white tail light and red beacons above and below the fuselage. The engines' nacelles are skinned round like the hull,
and each intake is round too: a polished lip curling into the duct, the fan and its spinner at the bottom of it. While
an engine is at rest an iris shuts its intake; it opens as soon as the engine is asked for thrust, and behind the
turning fan the engine glows a dull red, brighter the harder it runs, until it has spun down and the iris shuts again.
Each engine is heard spooling up and down with its thrust, quieter from inside the cabin, and high up it leaves a
contrail.

### Flight deck

Steps at the front of the main deck lead up to a raised floor in the nose. From the top of the steps a short aisle
runs forward to the cockpit door, between the WC to port (a toilet and a basin with its tap) and the crew room to
starboard (a couch of seats and a galley counter with an oven, a cupboard and a coffee machine); every door opens by
hand from either side. Behind the cockpit door the flight deck is a room of its own, raised so the pilots look
straight out through the windscreen: tinted glass cut to the nose's curve like the skin around it, one wide pane right
across the front from the panel up over the crown, running back into side windows past the pilots' shoulders, framed
by posts round the sides only. The pilots' two big seats, each a Create seat with a high back and an armrest, stand
back where the nose leaves room for side consoles beside them, with room to move about behind them.
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
is thrown about the cabin. The weather makes it worse. In rain (or snow) it comes a little more often and is a little
rougher; a thunderstorm brings heavy turbulence, about every 15 seconds, twice as long and up to nearly twice as
hard: a stronger shake, and bumps that knock anyone standing off their feet. Only where something falls, though: a
storm passing over a desert is as calm as clear skies. Turn it off with `turbulence` in the server config.

### Autopilot

Use the autopilot panel on the pedestal, or the big screen in the middle of the instrument panel, to open the
autopilot's settings. At the top they show what the autopilot is doing, with a lamp (green engaged, amber off), how
the airliner flies now (speed, height and heading) and, once engaged, what it is set to fly. Below are the settings:
hold a heading (a straight arrow), or circle left or right round a point beside the airliner (arrows round to the
left or right); the heading (a compass, a compass bearing); the altitude to hold (a hill, from 40 blocks above the
runway to 30 below the top of the world); the speed (a speedometer, 30 to 120 blocks a second); and the circle's
radius (a circle, 150 to 1000 blocks). Slide each one, step it with the arrows either side of its slider (15
degrees, 20 blocks, 5 blocks a second, 50 blocks), or type it into the box after it. Every setting and button has a
picture and a colour of its own, so that players who cannot read yet can fly too; point at one to see its name. The
game goes on behind the settings, and they close once you step away from the flight deck.

Switched off, the settings start from what engaging would fly, and engaging (the wide glowing button, or Enter)
flies them.
Engaged on an assembled airliner standing on the ground (the button says Take Off), it takes off: brakes off, full
thrust down the runway, holding the heading it started on with the nose wheel, nose up at rotation speed, then a
climb straight ahead, wings level, until it is 30 blocks above the ground. Only then does it turn onto the heading or
circle set, banking no steeper than its height allows (it never banks hard near the ground), and it climbs on to the
altitude set and holds height and speed there. The altitude starts at the cruising height (`climbHeight`, 80 blocks above the runway by default) and the
speed at `cruiseSpeed`. Engaged in the air, they start from the height, speed and heading it finds. While it is
engaged, Apply flies the settings as changed, and Disengage hands the airliner back to the thrust levers, the
tiller and the parking brake. While it flies it shows its phase, speed, height, pitch, bank and heading to
everyone on the flight deck.

In the air, Land puts the airliner down ahead, engaging the autopilot if it is off. It rolls out of any circle onto
the heading it is on, slows until it flies with its nose level and sinks 3 blocks a second, banking less the lower it
gets; from 15 blocks above the ground it flares, sinking ever more slowly, to touch down at half a block a second.
How fast it flies nose level depends on how heavily it is loaded and how thin the air is, so it finds that speed on
the way down, starting from a tenth above the rotation speed (`rotateSpeed`): furnishing it or loading it up needs
no new settings. Down, it idles the engines, lowers the nose and brakes to a stop, steering along the heading
with the nose wheel; then it pulls the thrust levers to idle, centres the tiller, sets the parking brake and
switches itself off. It lands on whatever lies below, so line it up over open, level ground first. Applying the
settings while it comes down goes around, climbing away to the altitude set.

The big screen shows the autopilot at work. Its right side is the autopilot's buttons, lit for what it is doing;
touching them opens the settings. Its left side is a moving map, heading up: the track flown, the circle being
flown or the heading being turned to, and north; touch the map to zoom it out, and again past the widest to zoom
back in.

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

### Cabin service

At the head of the lounge's bar is the galley. Use it to roll out a full trolley, or to restock the trolleys standing
by it. Push a trolley down the aisle by walking into it (it squeaks); use it to be served the next of its meals: a
chicken meal, airline coffee (a minute of Speed), a pasta meal or pretzels, two dozen to a trolley. Sneak and hit it
to fold it up and carry it. Galley, trolley and food can all be crafted.

### Passenger screens

A Passenger Screen (craftable) is a slim seat-back screen for the cabin. Put against the back of a seat or a wall it
faces out from it; put anywhere else it faces you. Once the airliner is assembled it shows the flight: whether it is
on the ground, climbing, cruising or descending; its altitude above sea level and its height above the ground; its
speed over the ground and how fast it climbs or sinks; the way it is heading; and how far it has flown and for how
long since it took off, kept up after landing until the next take-off. A block counts as a metre. While the airliner
stands as blocks the screen shows a welcome page.

Use an assembled airliner's screen to look through its outside cameras, the way a security camera is watched: on
top of the fin looking forward over the whole airliner, over the right wing out at its engines, and under the nose
past the nose gear down the runway. Use again for the next camera, and after the last you are back in the cabin;
sneak to go straight back. The view follows the airliner as it flies, while you stay where you are (seated, you
stay in your seat) and nothing you click reaches the world. The cameras are placed from the airliner's autopilot, so
an airliner without one has none.

### Villager passengers

Off by default: turn on Villager Passengers (`villagerPassengers`) in the server settings (see Settings). A Check-in
Desk at the terminal then prints a Boarding Pass for whoever uses it. Hand the pass to a villager and it makes for
the nearest free Create seat inside an airliner within about 24 blocks, up the airstairs or through the jet bridge
and the doors, and sits down; unseated aboard, as it is when the airliner is assembled around it, it sits straight
back down. Fly it at least 200 blocks: once the airliner stands still with the seatbelt signs off it gets up and pays
its fare, an emerald for every 200 blocks flown, up to 32, to whoever handed it the pass (or, with them gone from
that world, dropped by its seat). A villager that finds no seat within three minutes gives the pass back.

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

Open one of the hull's doors in flight (20 blocks or more above the ground, at 20 blocks a second or more; the
cockpit door, the WC's and the crew room's are not among them) and the cabin decompresses with a bang: the air roars
out through the doorway in a stream of fog, everyone aboard is warned in chat, and whoever stands within a few blocks
of the door (3 per unit of `scale`), and anything loose (mobs, villagers out of their seats, dropped items, a
basketball, a trolley), is dragged to it, the harder the closer, and flung out into the sky, wherever the air has a
clear way to the door (not up through the floor from the hold, or round a wall). Anyone seated is belted in and stays
put. With a parachute on, being sucked out is just the start of a skydive; close the door from a few steps back to
stop it. Turn it off with `decompression` in the server config.

Put a Landing Target on the ground at the drop zone: every parachute landing within 64 blocks of it is called out to
everyone nearby with its distance from the middle, and using the target shows the best landing so far.

A Signal Flare stuck in the ground marks a drop zone from the air: for three minutes it sends up a column of smoke a
hundred blocks tall, red, or the colour of any dye used on it, then burns out.

Pack a Care Package (a crate of two rows) with supplies and drop it: sneak and use it with an empty hand to shove it
the way you face, out of the cargo door, or give it a redstone pulse to send it off the way it faces. It falls free a
moment, then floats down under a parachute and lands as the same crate, contents and all, and everyone nearby (and
whoever dropped it) hears how far from the nearest burning flare it came down. A crate keeps its contents when broken,
like a shulker box.

Or drop it through the drop bay: hatch doors in the belly, behind the main gear. From the hold, use a care package on
the bay to load it (six at most; use it empty-handed to count them, sneaking to take the last one back out). The drop
release on the first officer's side console, behind the parking brake, opens the bay doors on the first use and
drops the next load on each use after that; sneak and use it to close them. A load leaves at the airliner's speed.
With Ultrasplosive installed the bay also takes its nuke (the regular Fat
Man, not Sundial), which leaves the bay as Ultrasplosive's flying nuke and goes off where it hits. Its blast reaches
160 blocks, so the release only drops one from 200 blocks or more above the ground.

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

### Jet bridge

For an airfield with a terminal, set a Jet Bridge into the terminal floor at the mouth of the bridge, its arrow
pointing out toward where the airliner parks, level with the cabin floor and in line with a door. Using it (or a
redstone signal) sends out a glazed tunnel, three blocks wide and high inside, slice by slice to the airliner, its
black canopy closing against the curve of the hull around the door; it docks at a door in the world or aboard an
assembled airliner standing still, up to 32 blocks out. If no door lines up it says which way to move the bridge.
Use it again to fold it back once everyone is off it; it also pulls back on its own when the airliner starts to
move, and breaking it takes the whole tunnel with it.

### Flying it

The finished airliner is covered in Create's Super Glue, in overlapping stretches the Physics Assembler can find from
anywhere in it; that is what it takes as the structure, so everything you build inside it flies along. Fold the
airstairs away and pull the thrust levers to idle, then assemble it. It rolls on Offroad wheel mounts fitted with
large tires, their suspension at its stiffest; the main gear stands just behind its centre of mass, worked out from
Sable's block masses, so it rests on its nose gear. The wings and tail are built around a layer of Create Propulsion
wing blocks, and every airframe block is in `#sable:super_light`. Which blocks are used is set in the server config
(`wingBlock`, `engineBlock`, `assemblerBlock`, `wheelMountBlock`, `tireItem`), and so is how hard the creative
thrusters push (`thrustSetting`, on their own 1 to 99 scale: 99, their full rating, by default).

### First Flight advancements

The First Flight tab of the advancements marks the milestones: aboard as the airliner climbs away from the runway
(Cleared for Takeoff), trying to get up with the seatbelt signs on (Buckle Up), aboard for a touchdown sinking slower
than a block a second (Butter Landing), a basket scored in flight (Swish at 30,000 Feet), a parachute landing within
a block of a target (Perfect Jump), a care package down within 10 blocks of a burning flare (Special Delivery) and a
villager passenger's fare (Frequent Flyer).

### Settings

The settings can be changed in game under Mods → Aerolarge → Config. The client settings (`cameraShake`) are there
from the title screen. The server settings open only while a world is loaded, from the pause menu's Mods button
while playing in single player; they are kept in `config/aerolarge-server.toml`, on a dedicated server in its own.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew runClient -PquickPlayWorld=<save>` loads straight into
`run/saves/<save>`; drop Create, Sable, Create Aeronautics and Create Propulsion into `run/mods` to run with them.

The geometry lives in `AirframeShape` (real A380 measurements in metres), is cut into blocks by `AirframeLayout`
and fitted out by `CabinFitOut`; the skin follows the curve in `ShellGeometry`'s shapes, which `ShellModel`
bakes, and `Windscreen` says where the glass and its posts go; `AutopilotBlockEntity` flies, reading the airliner
from Sable through `SableFlight`; `EngineIntakeRenderer` draws the intakes round over the nacelles' faces, from where
each engine's core was told they are. Assets are generated: `node tools/generate_textures.mjs` writes the textures
(ordinary PNGs that can also be painted by hand), and `node tools/generate_resources.mjs <vanilla-assets-dir>` the
blockstates, models, loot tables, tags, recipes and lang file. The door and the call button are vanilla's iron door
and stone button retextured, so their files come from vanilla's: pass the folder the client resources jar
(`build/moddev/artifacts/*-client-extra-*.jar`) is extracted to, or leave it out to keep them as they are.
`node tools/generate_sounds.mjs` writes the sounds with FFmpeg: the engine loop is synthesised, and the callouts are
spoken by Windows' own speech synthesis ("Microsoft David"), so that part runs on Windows only.

## License

All Rights Reserved.
