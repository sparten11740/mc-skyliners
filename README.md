# Aerolarge

A Minecraft mod for NeoForge 1.21.1: an A380-style double-deck airliner big enough to build in, that rolls on real
wheels and is flown from its own cockpit. One blueprint lays the whole airframe down: a single long hollow hull with
a cabin floor over a cargo hold with a hatch down to it, six passenger doors and a cargo door, a flight deck behind
a wrap-around windscreen, swept wings with four engines, the tail, landing gear and folding airstairs.

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
  the belly under it glass, so swimmers look straight down through the airliner at the ground or the clouds below,
  with a diving tower on its front rim (a ladder up to a small platform three metres up, glass rails round it, and
  a springboard sticking out from it over the water), a
  full-height gym hall (Turnhalle) with a court, hoops, wall bars, climbing ropes, a row of seats, roof trusses
  with gymnastic rings and the teams' banners, and a lounge
  with a bar and seats in the tail and a duty-free kiosk at its front (see below), under a starlit ceiling (navy
  Starlight Panels whose stars gently twinkle, LED strips along its edges), all lit by pendant lights, and behind
  the bar a secret door into a den in the tail; the hold under the cabin is stowed with luggage (both below). Plants
  line the walls: potted flowers on the bar's back shelf, spore blossoms drifting their spores down from the starlit
  ceiling over the bar and from under the diving tower's platform over the pool's rim, potted azaleas in moss along
  the pool's sides and potted bamboo by the doors. The seats are Create's, so they can be sat in.
  Passenger screens (see below) hang on the cabin walls between the windows, one in every fourth gap, and wherever
  seats stand in rows across the cabin, they hang on the back of each row, facing the one behind. Cream bulkheads wall
  the rooms off from the doors' aisles, each with a curtained white archway and a picture over it of what lies
  through it, with lavatories by the gym hall's entrance and an exit sign over every door (see Curtains, bulkheads and
  signs).

Use a blueprint on the ground: the nose goes just past the block you click and the tail stretches away the way you
face. It refuses while anything is in the way and says where; sneak to build through it. Operators can also run
`/aerolarge build <pos> <north|east|south|west> [furnished]`.

At the default scale (2 blocks per metre, set by `scale` in the server config) the airliner is about 145 blocks long
and 160 across the wings, and its cabin is about 14 blocks wide and 11 tall above the floor. Its hull is skinned in
blocks cut to the fuselage's curve (`shellStyle` in the server config: by default `SNAPPED`, which cuts every block
through its corners and edge middles only, in slopes that run into each other without a step, and rounds off the inside
of the nose too; `FITTED` cuts each block as close to the curve as one cut can), and two rows of tall cabin windows run
its length down each side, cut to the curve with it; cabin lights in the crown, the walls and the hold and LED strips along the tops of the
cabin walls keep it bright, and nothing spawns inside it on its own. Overhead lockers line both cabin walls
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
by posts round the sides only.

The two pilot seats are real airliner seats: a padded black leather back with a headrest and a harness, an armrest
either side, on a column between floor rails. Each is two blocks tall, and using either half sits you down in it. It
is one of Create's seats underneath, so the seatbelts and everything else that knows about seats treat you as
seated, but villager passengers leave it to the pilots. A Pilot Seat can be crafted and put down in your own builds
too.

Between and in front of the seats runs the centre console the pilots share, low enough to see the screens over, each
control on it a slim module of it, and everything on it within reach from either seat but the far outer thrust lever
(any lever moves all four) and the two controls right beside the other seat. Across its front, under the screens, is
the autopilot's control head (use it to open the autopilot's settings, see Autopilot) between the seatbelt switch and
the jump light switch (see Skydiving). In front of the seats is the throttle quadrant: four slim thrust levers,
engines 1 to 4 from left to right, with the parking brake in the middle. The levers move together: use any of them to
push all four a notch forward, sneak and use it to pull them back. Each drives its engine's thrusters through relays
hidden in the nacelle. The parking brake is set when the airliner is built. Between the seats is the nose wheel
tiller, a small handwheel (use it to steer right a notch, sneak and use to steer left), with the cabin lights' dimmer
on the captain's side of it and the captain's handset on the first officer's; right beside the captain is the window
shades' control, and beside the first officer the drop release. The dimmer steps every light in the cabin from bright
to dimmed (as for take-off and landing) to a blue night glow and back, the change running down the cabin front to
back; at night the lounge's starlit ceiling goes dark but for its stars, which glow whatever the light, and the WC,
the crew room and the lavatories keep their lights on as dimmed, as an airliner's lavatories do. The window
shades' control runs every cabin window's shade down, or up again, the same way, with the whirr of their motors. A
closed shade shuts out the daylight; anyone can open or close a window's own shade by using it.

In front of each seat a primary flight display in the panel shows attitude, airspeed, altitude, vertical speed and
heading once the airliner is assembled. In the middle, standing just in front of the panel, is one glass unit of two
screens in the same slim dark casing: the autopilot screen below, and on top of it the tail camera display, a wide
screen three blocks across like an A380's taxiing camera screen. Where the nose leaves it no room there, the camera
display hangs from the crown just ahead of the seats instead, tilted 45 degrees down toward them. Once the airliner is
assembled it shows the live picture from the camera on top of the fin, the one the passenger screens look through
(see below): the whole airliner from above and behind, its wingtips at the edges, the ground or the clouds beyond,
with CAM TAIL and a blinking recording light in the corner. The picture is drawn small, a few times a second, and only
while a display is in sight within 32 blocks; every display aboard the same airliner shows the same picture. Without
one it shows a test card: colour bars with the airliner seen from behind, and under them NO SIGNAL, or ASSEMBLE TO FLY
while the airliner stands as blocks. That is also what it shows with a shader pack (Iris, Oculus) or Fabulous
graphics, or with `tailCameraFeed` off in the client config, where `tailCameraFps` and `tailCameraResolution` also set
how often and how sharp the picture is drawn. A Tail Camera Display can be crafted and put up in your own builds too:
against a wall it stands upright, and put up against the underside of a block it hangs from it tilted toward you. It
reaches two blocks to the right of where it is put, as you face it, and needs the room there; it comes down whole. On
an assembled airliner with an autopilot it shows that airliner's fin camera. Against the wall behind the captain's
seat is the Physics Assembler.

In a nose too narrow for the centre console (at the smallest scales) the thrust levers stand between the seats with the
autopilot's control head among them, the tiller and the parking brake outboard of the seats where there is room, and
the switches, the dimmer and the handset on the panel either side of the screens.

The flight deck's voice, a calm, very even, faintly synthetic woman's voice, unfailingly polite and not easily
impressed, calls out the take-off (V1, rotate, positive rate) and the landing (the height above the ground from a
hundred down to ten, then retard), and warns of too much bank ("Bank angle. Please do not do that.") or sinking too
fast too low ("Pull up. This is not a test."), whoever flies. The same voice makes the autopilot's cabin
announcements (see below).
Riding the airliner, the camera rumbles on the runway, thumps on touchdown and sways a little in flight; turn that
off with `cameraShake` in the client config.

### Announcements and the radio

Use the captain's handset and your next chat message is a cabin announcement: everyone aboard hears the two-tone
chime and sees it on screen under "Captain" and your name, and it is kept in chat. Sneak and use it, and the message
is a radio call instead. A Tower Radio (craftable) is for the airfield: whatever is said into it goes out over the
radio. A radio call crackles in for everyone near any radio within 4096 blocks, the handsets of airliners in flight
included. Either way the message is left out of ordinary chat; use the handset or radio again to put it down.

The autopilot makes announcements of its own, in the flight deck's voice after the same chime, and everyone aboard
hears them wherever they are in the cabin. Set to take off (Take Off, on the ground), it welcomes everyone aboard and
asks the cabin crew to be seated for take-off; set to land (Land), it asks everyone back to their seats and the crew to
be seated for landing; and once it has landed and stopped, it welcomes everyone to their destination and lets them
go. As crews do, it switches the seatbelt signs on with the take-off and landing announcements (which opens the
curtains), and off again once stopped, so everyone can get up. Announcements never talk over each other: one asked
for while another plays waits for it to end, and the same one is not made again within a minute, however often the
buttons are pressed.

### Seatbelts and turbulence

The seatbelt switch lights the "fasten seatbelts" signs along both cabin walls, with a ding, and opens every curtain
aboard, front to back, as cabin crew do for take-off and landing. While the signs are on, anyone seated aboard stays
in their seat when they sneak to get up. The autopilot works the switch too, with its announcements (see above).
In flight the airliner runs into rough air now
and then, a few seconds of it about once a minute: everyone aboard feels the shake, and whoever is not sitting down
is thrown about the cabin. The weather makes it worse. In rain (or snow) it comes a little more often and is a little
rougher; a thunderstorm brings heavy turbulence, about every 15 seconds, twice as long and up to nearly twice as
hard: a stronger shake, and bumps that knock anyone standing off their feet. Only where something falls, though: a
storm passing over a desert is as calm as clear skies. Turn it off with `turbulence` in the server config.

### Autopilot

Use the autopilot's control head on the centre console, or the big screen in the middle of the instrument panel, to
open the autopilot's settings. At the top they show what the autopilot is doing, with a lamp (green engaged, amber
off), how the airliner flies now (speed, height and heading) and, once engaged, what it is set to fly. Below are the
settings: hold a heading (a straight arrow), or circle left or right round a point beside the airliner (arrows round
to the left or right); the heading (a compass, a compass bearing); the altitude to hold (a hill, from 40 blocks above
the runway to 30 below the top of the world); the speed (a speedometer, 30 to 120 blocks a second); and the circle's
radius (a circle, 150 to 1000 blocks; a circle too tight to fly at the speed set is flown wider round the same
middle, so it never banks more than 20 degrees going round). Slide each one, step it with the arrows either side of
its slider (15 degrees, 20 blocks, 5 blocks a second, 50 blocks), or type it into the box after it. Every setting
and button has a picture and a colour of its own, so that players who cannot read yet can fly too; point at one to
see its name. The game goes on behind the settings, and they close once you step away from the flight deck.

Switched off, the settings start from what engaging would fly, and engaging (the wide glowing button, or Enter)
flies them.
Engaged on an assembled airliner standing on the ground (the button says Take Off), it takes off: brakes off,
speeding up down the runway no faster than an airliner does (3 blocks a second every second, so nobody standing is
thrown down the cabin; it speeds up and slows down that gently in the air too), holding the heading it started on
with the nose wheel, nose up at rotation speed, then a climb straight ahead, wings level, until it is 30 blocks
above the ground (or at the altitude set, where the ground below rises that high). Only then does it turn onto the
heading or circle set, banking no steeper than its height allows (it never banks hard near the ground, and never
more than 25 degrees), and it climbs on to the altitude set and holds height and speed there. The altitude starts at
the cruising height (`climbHeight`, 80 blocks above the runway by default) and the speed at `cruiseSpeed`. Engaged
in the air, they start from the height, speed and heading it finds. While it is engaged, Apply flies the settings as
changed, and Disengage hands the airliner back to the thrust levers, the tiller and the parking brake. How it is
flying shows on the pilots' head-up display (see below).

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

Wearing a Pilot Helmet (see Souvenirs) on the flight deck of an assembled airliner, within reach of the autopilot (in
either pilot's seat, or standing at the console), its visor shows a head-up display round the middle of the view:
thin, see-through green lines and figures, as a real HUD's, edged with a thin dark line so they read against bright
sky, with whatever the autopilot is set to fly in magenta, curving with the visor (its rows lift a little toward
their ends); without the helmet there is none. Putting the helmet on there switches it on with a beep, and it fades
in. Round the crosshair are a slim horizon line and the pitch ladder, tilting with the bank and dropping as the nose
comes up (the horizon amber past 35 degrees of bank), and the flight path marker, a small ring with wings showing
where the airliner is going. Left of them is the speed on a narrow moving tape, the speed set as a magenta mark
beside it and an arrow to where the speed will be in ten seconds; under it a picture of what the autopilot is doing,
and its name: taking off, climbing, cruising, descending, landing, braking to a stop after landing, or a red power
symbol while it is off. Right of them is the height on a moving tape (the autopilot's own, as it holds it), the
height set as a magenta mark and, once it is near, the ground rising up the tape as an amber line; beyond it a bar
that climbs green or sinks amber, and under it the wheels' height above the ground and the climb or sink with an
arrow. Above them is a heading scale with the heading in a box, the heading set as a magenta mark and the mode beside
it (straight on, or circling left or right with the circle's radius). It is drawn about half the size of the rest of
the HUD and grows with the GUI scale; `pilotHudScale` in the client config makes it bigger or smaller. Being on the
visor, it shows only in first person. It hides with the rest of the HUD (F1), behind any screen, under the debug
screen and while you look through an outside camera; turn it off with `pilotHud` in the client config. While you wear
the helmet and look out through your own eyes, its visor also tints the view a touch toward its gold, darkens it
faintly toward the edges and catches a little light along its top.

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

A Coffee Machine is a bean-to-cup machine, as in an airliner's galley: a brushed steel and black body, a bean hopper on
top, a drip tray under its spout and a small lit display on its front. Use it and its panel opens, three big buttons
each showing its drink, so it needs no reading: an espresso in a small cup, a cappuccino with its foam, or a mocha in a
tall glass under its cream. It grinds the beans, then pours the coffee into a cup that stands under the spout and, for
a cappuccino or a mocha, froths the milk with a hiss of steam from its wand; its display shows each step in a picture
(a green cup when ready, a bean, a pour, steam, a green tick when done), and it chimes when the drink is ready. Use it
again to take the drink out; while it brews it only beeps. An espresso gives 45 seconds of Haste, a cappuccino 45
seconds of Speed, and a mocha, sweet with chocolate, half a minute of Speed and a few seconds of Regeneration, each a
little more filling than the one before; the cup goes with the drink. The machine can be crafted, the drinks only
brewed. Both blueprints have one on the cupboard in the crew room behind the flight deck, and the furnished airliner
another on the lounge's bar, just behind the galley.

### Curtains, bulkheads and signs

A Cabin Curtain hangs in pleats across the way you face; curtains put up beside, under or over one another join
into one curtain on a steel rail. Use any piece to draw the whole curtain open or shut, a column at a time away from
the piece used, with a swish; use a dye on it to dye all of it, for one dye, and taken down it keeps its colour. It
is only cloth: players, villagers, trolleys and basketballs pass straight through it, open or shut, and it keeps no
light out. A Cabin Partition is a plain cream bulkhead panel to build walls with, a Cabin Arch a piece of a white
archway in one, and an Exit Sign a green running man heading out through a doorway. All four can be crafted. A Cabin
Arch piece is a block of a round white arch over an opening three or five blocks wide, or of the jambs either side of
it: smooth white panelling round the opening with a moulding along its edge, a bead and a groove behind it, set into
cream panels like the partition's and the same from either side. Taken down, or picked from an archway, a piece keeps
which piece it is; put down, it is the right-hand side of an archway as you face it, or the left-hand side sneaking.

In the furnished airliner a partition wall stands between each room and the doors' aisle beside it, and between
business class and the pool, with an archway over the aisle hung with a curtain in the room's colour (navy for
business class, cyan for the pool, lime for the gym hall, red for the lounge), drawn open. Each archway is built of
Cabin Arch pieces, a round white arch over the curtain's rail on white jambs, and stays a head's height clear right
across; at the largest scales it is wider. Over each archway, on the side facing the doors, a picture in coloured
wool shows what lies through it: a seat, a drop of water, a basketball, a cup. Business class has such a wall at its
front as well, across the forward doors' aisle (where it has no room for one, a curtain on its own crosses the aisle
there instead). The gym hall's front wall has a lavatory either side of its archway, each with a toilet, a basin and
its tap, and a wooden door under a picture of a person.
The hoops are mounted on the gym hall's end walls, out of the way of anyone coming through the archways, and a lime
dividing curtain (Trennvorhang) hangs right across the middle of the court, to split the hall into two courts. Over
every passenger door glows an exit sign, lit a little so the doors can be found with the cabin lights set for the
night.

### The den

Behind the lounge's bar a wall of bookshelves closes off the tail. One of its books glows: that bookshelf is a secret
door, and while someone is near, sparks drift into the book with a faint shimmer. Use it and it swings open with a
deep creak; it swings shut by itself after ten seconds, once nobody stands in the doorway, or when used again. Behind
it the tail is a den for children: a landing with two cushions, a ball pit, then the tail cone's steps climbing to
the top of the tail, each carpeted in the next colour of the rainbow, with bunks (wool mattresses, each with a blanket
and a pillow) along the walls where a step is wide enough and cushions where it is not, fairy lights along both walls
that dim with the cabin lights, and at the very top a treasure chest with cookies, a golden apple, a signal flare, a
basketball and star rockets. The ball pit swallows whatever walks or falls in with a plop and a spray of balls: you
wade through it a little slower, a fall into it never hurts, nobody under the balls is smothered, and a jump always
gets you out. The secret door, the ball pit and the fairy lights can be crafted.

### The hold

In the furnished airliner the hold between the moon pool and the drop bay is stowed with luggage, piled up both sides
behind cargo nets of chain, with yellow and black stripes along the walkway down the middle, which stays clear from
the pool to the drop bay and on to the hatch and the cargo door. Along its forward half the belly under the walkway is
a sky window of clear glass, like the pool's: lie on it and look straight down at the ground, or the clouds, below.
The suitcases, in four colours, each hold a few things a traveller packs; among them are care packages packed with
supplies, ready to load into the drop bay. Three golden suitcases are hidden about the hold: they glint, glow faintly
and chime softly when someone comes near. Opening one counts toward Lost and Found in the First Flight advancements,
with a fanfare the first time; find all three to earn it. Suitcases can be crafted; golden ones can only be found.

### Vacuum toilets

A Vacuum Toilet (craftable from iron and a bucket) is an airliner's toilet: a stainless steel bowl in a white shroud,
with a seat and a lid, against a wall panel with a square blue flush button on it. Put down, it faces you. Use it to
flush: the vacuum snatches the bowl empty with a sharp, loud hiss, swirling it, and the valve shuts with a thunk; the
button stays lit for three seconds while the vacuum builds up again, and it flushes again only then. Sneak with an
empty hand to lift or lower the lid.

Each flush lets go of what a leaking airliner is famous for: a chunk of blue ice, the blue toilet water frozen, out of
the belly right under the toilet. It leaves at the airliner's own speed, so in flight it tumbles away behind and far
below, glinting as it falls; parked, or standing as blocks, the airliner just drops it on the ground underneath. It
lands with a crack, and where it comes down on top of solid ground with nothing there it lies as a lump of
see-through Blue Lav Ice that melts away after three or four minutes; anywhere else it shatters. It never breaks or
replaces a block and never hurts anyone: the worst it does is bonk whoever it lands on. A toilet with no airframe
under it, away from an airliner, only flushes. Both blueprints have one in the WC behind the flight deck, and the
furnished airliner one in each lavatory by the gym hall (at the smallest scale there is room for neither).

### Rubber ducks

A Rubber Duck is a little yellow duck with an orange beak, to put down anywhere, its beak toward you. Put into water
it floats, bobbing and rocking gently. Use it, or hit it, and it squeaks, with a wiggle and a few hearts. Yellow wool
under an orange dye makes two.

The furnished airliner hides five of them, a secret best left for children to find, so it works without reading: one
on the flight deck's glareshield, where pilots keep their mascots, one in the WC behind the flight deck by the basin,
one sitting on the balls of the den's ball pit, one on the luggage in the hold, and one bobbing on the moon pool's
water by the steps out, in plain sight, to show what to look for. (At the smallest scale the airliner has none of
these rooms, and no ducks.) Within a few blocks of one you have not found yet, it squeaks softly every few seconds
and wiggles, with a note over it. Use it, or hit it, to find it: it squeaks loudly, a row of five dots on screen fills
in yellow for the ducks found so far, and the duck counts them out in squeaks, each a little higher, with a heart
each. Every player finds the ducks for themselves, and a duck falls quiet only for whoever found it. Each counts
toward Duck Hunt in the First Flight advancements; find all five to earn it. A hidden duck stays put when hit, even in
creative mode; sneak to take one away, and it is gone until the airliner is built again.

Whoever finds the fifth sets off the surprise: with a great splash a giant rubber duck, three blocks wide and tall,
rises out of the airliner's moon pool, and once it is up it lets out a huge squeak, with a fanfare and a burst of
confetti and fireworks; the finder hears the squeak from anywhere aboard. It floats there, bobbing gently, and it is
solid: jump onto its back or its head and it bounces you straight back up with a deep squeak, like the springboard
(sneak to stand on it); use it, or hit it, and it squeaks. It is part of the airliner, flies with it and stays until
the airliner is built again: like a hidden duck it stays put when hit, and only comes apart for a player who sneaks,
breaking any part of it taking the whole duck away. A second player finding all five makes it squeak again.

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
the nearest free Create seat inside an airliner within about 24 blocks (never a pilot's seat), up the airstairs or
through the jet bridge and the doors (which open for it, see Doors), and sits down; unseated aboard, as it is when the
airliner is assembled around it, it sits straight back down. Fly it at least 200 blocks: once the airliner stands
still with the seatbelt signs off it gets up and pays its fare, an emerald for every 200 blocks flown, up to 32, to
whoever handed it the pass (or, with them gone from that world, dropped by its seat). A villager that finds no seat
within three minutes gives the pass back. The fares are spent at a duty-free counter.

### Duty free

A Duty-Free Counter (craftable) is a shop counter with a glass showcase on top. Use it and its shop opens with the
ding of a door bell: the trading screen, each of its goods shown by its picture and its price in emeralds, so it needs
no reading. An emerald buys four paper planes, two airline coffees, four bags of pretzels, a chicken or a pasta meal,
or two signal flares; a basketball costs 2, a parachute 4, a pilot cap 5, an empty care package 6, a model airliner 8
and a pilot helmet 10. Each sale rings the till. It never runs out and its prices never change, and nobody works
behind it, so it stands anywhere: at the airport, or aboard, where there is nobody to lose in flight. The furnished
airliner has a duty-free kiosk at the front of the lounge, to port across from the bar: a row of counters facing the
aisle under a lime and white striped awning, a shelf of flowers and candles behind them, and far enough back from the
aft doors that a shopper is out of reach of the air rushing out of one opened in flight. (At the smallest scales the
lounge is too narrow for it.)

### Basketball

The gym hall's hoops are real: take a ball from the rack in the corner by either hoop and use it to throw it the way you
look, sneaking for a softer throw. It bounces off the floor and walls and rolls to a stop; walk over it or use it to
pick it up, hit it to knock it on. A ball that drops through a hoop scores two points, or three thrown from beyond
the three-point line, for the side the hoop counts for: HOME at the front hoop, GUEST at the back (sneak and use a
hoop to swap it). The scoreboard over each backboard keeps the score; use it to sound the buzzer and start again.
Hoops, balls, scoreboards and racks can all be crafted to build a court anywhere.

Over the court the gym hall's pendant lights hang from metal roof trusses (Create's girders). From the trusses either
side of the middle hang gymnastic rings on chains, over the side of the court by the wall bars: use them to swing up
off the floor. The teams' banners hang between the upper windows, orange at the HOME end, blue at the GUEST end.
Gymnastic Rings can be crafted too.

For hide and seek, press the big yellow button with the eye on it beside either hoop (or sneak and use a
scoreboard): every scoreboard in the hall counts down from ten in huge numbers, amber, then red for the last three,
with a beep each second, and at nought the buzzer goes and the nought turns green before the score comes back. The
button stays down, its eye shut and glowing, until then; press it again to start over. Hide and Seek Buttons can be
crafted.

### The high dive

The springboard sticking out from the moon pool's diving tower, a slim blue-gripped board on a coiled spring, throws
whoever lands or stands on it straight back up, with a boing and a puff of cloud, about two and a half blocks every
time, never high enough to hurt coming down, and landing on it never hurts; sneak to stand on it without bouncing.
The tower's platform is railed along its sides and back; off the springboard or the front of the platform there is
only water below, and the pool's water takes the fall. A dive (off the springboard, or any fall of three blocks or
more into water) goes in with a big splash. A Springboard can be crafted; placed, it sticks out the way you look, and
it is a little trampoline wherever it is put.

### Paper planes

A sheet of paper folds into a Paper Plane, and a duty-free counter sells four for an emerald. Use it to throw it the
way you look, sneaking for a gentler throw, and it glides with a whoosh: a hard throw lifts it a little before it
settles into a long gentle glide, nose down as it sinks, wavering from side to side and drifting a little one way or
the other, as no two fold alike. From head height it comes down twenty blocks or so away and lies there to be picked
up; fly it into a friend and they catch it, use it in flight to catch it yourself, hit it to knock it down. Aboard an
airliner in flight it glides down the cabin as it would on the ground. Out through an open door (the rushing air of
decompression takes one near it out too) it meets the still air outside at the airliner's speed, slows and glides down
from the height it left at, hundreds of blocks.

### Souvenirs

A Pilot Cap, navy with a black peak and a gold badge, is worn on the head: use it to put it on, or put it in the
helmet slot. It is no armour and protects nothing; it just looks the part. A Model Airliner on its stand goes on a desk
or a shelf, its nose toward you. Both can be crafted, or bought at a duty-free counter.

A Pilot Helmet is a jet pilot's flight helmet: white with the livery's blue stripe over the crown, the airline's
roundel over each ear, a gold visor down over the eyes and a microphone on a boom in front of the mouth. It is worn
the same way as the cap, and protects as much as a leather cap. On the flight deck its visor shows the pilots'
head-up display (see Autopilot). Craft it from five iron ingots round a black stained glass pane, with two black wool
under them for the ear cups, or buy it at a duty-free counter for 10 emeralds.

### Skydiving

By the aft passenger doors and the cargo door are a red and green jump light in the wall and a rack of parachutes.
Use a rack to put a parachute on (or take one into your inventory if your chest slot is taken), then jump while
falling to open it; it opens by itself if you are still falling free close to the ground. Under the canopy you sink
gently, drift the way you walk, take no fall damage, and it packs itself away when you land. The jump light switch on
the centre console cycles every jump light in the airliner through off, red (stand by) and green (go, with a bell).

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

Or drop it through the drop bay: hatch doors in the belly, behind the main gear, loaded from the hold. Besides the
cargo door from outside, the hold is reached through the hatch in the cabin floor a few steps aft of the bay, to port
just behind the gym hall: a yellow and black border marks it and its lid shows a ladder. Open it by hand and climb
down the ladder under it, so the bay can be reloaded in flight. From the hold, use a care package on the bay to load
it (six at most; use it empty-handed to count them, sneaking to take the last one back out). The drop release on the
centre console, beside the first officer's seat, opens the bay doors on the first use and drops the next load
on each use after that; sneak and use it to close them. A load leaves at the airliner's speed.
With Ultrasplosive installed the bay also takes its nuke (the regular Fat
Man, not Sundial), which leaves the bay as Ultrasplosive's flying nuke and goes off where it hits. Its blast reaches
160 blocks, so the release only drops one from 200 blocks or more above the ground.

### A sturdy frame

The airliner's frame and controls (hull, wings, engines, gear, windscreen, flight deck and the Create and Propulsion
blocks it is built with) cannot be mined, broken in creative mode or blown up; the cabin floor and everything built
on it can. Sneaking with a wrench (Create's will do), or sneaking in creative mode, still breaks them: to take an
airliner apart on purpose, or to take back a panel put in the wrong spot.

### Doors

The six passenger doors and the cargo door are plug doors, one panel over each doorway as on the real airliner: white
skin flush with the hull, its seam a stepped gap right round it, with one window in the middle of its top and a flush
handle by the edge away from its hinge, two blocks wide and three tall (two at scale 1; the cargo door is two tall).
Inside, a padded lining in the cabin's off-white stands a few pixels proud of the panel, a rounded oval with the window
set deep into it and the handle on it. Use any part of a door, from inside or out, or give any part a redstone signal,
and it opens with a hiss: the panel lifts a little and moves out of its frame, then swings forward on its arm and
parks flat against the hull ahead of the doorway, standing just clear of it, its outside still facing out. The cargo
door swings up and out on its top edge instead. Open, the doorway is clear, and the panel outside is in nobody's way;
use the empty doorway to shut it again. The panel comes back the same way and latches home with a clunk, but not on
anyone: while someone stands where it goes back in, it waits, with a chime. The airstairs and a docked jet bridge open
the door they serve, and shut it again as they fold. Villagers find their way through the doors as through wooden
ones: while the airliner stands still, a door opens for a villager walking up to it and shuts again behind it; in
flight no villager opens one. The cockpit door and the doors of the WC and the crew room are ordinary doors.

### Airstairs

The forward port door has folding airstairs. The Airstair Controller set into the hull just aft of the door folds
them away, step by step from the bottom up once nobody is standing on them, and unfolds them again from the door
down until they meet the ground; the door opens as they unfold and shuts as they fold. Work it with the button beside
it inside or outside by the landing, by using the panel itself, with any redstone signal, or from the ground with the
yellow call button on the front of the nose gear.

### Jet bridge

For an airfield with a terminal, set a Jet Bridge into the terminal floor at the mouth of the bridge, its arrow
pointing out toward where the airliner parks, level with the cabin floor and in line with a door. Using it (or a
redstone signal) sends out a glazed tunnel, three blocks wide and high inside, slice by slice to the airliner, its
black canopy closing against the curve of the hull around the door; it docks at a door in the world or aboard an
assembled airliner standing still, up to 32 blocks out, and opens the door once it is there. If no door lines up it
says which way to move the bridge. Use it again to fold it back once everyone is off it; it also pulls back on its own
when the airliner starts to move, and shuts the door as it goes. Breaking it takes the whole tunnel with it.

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
a block of a target (Perfect Jump), a care package down within 10 blocks of a burning flare (Special Delivery), a
villager passenger's fare (Frequent Flyer), emeralds spent at a duty-free counter (Duty Free), a paper plane thrown
out of an airliner in flight coming down 100 blocks away (Air Mail), a dive into the moon pool off its springboard
or its tower while the airliner flies, 15 metres or more above the ground (Cannonball), the three golden suitcases
hidden in the hold (Lost and Found), a vacuum toilet flushed in flight, its blue ice let go 15 metres or more above
the ground (Blue Ice), the five rubber ducks hidden aboard (Duck Hunt), and an espresso, a cappuccino and a mocha
taken from a coffee machine (Barista).

### Settings

The settings can be changed in game under Mods → Aerolarge → Config. The client settings (`cameraShake`, `pilotHud`,
`pilotHudScale` and the tail camera display's `tailCameraFeed`, `tailCameraFps` and `tailCameraResolution`) are there
from the title screen. The server settings open only while a world is loaded, from the pause menu's Mods button while
playing in single player; they are kept in `config/aerolarge-server.toml`, on a dedicated server in its own.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew runClient -PquickPlayWorld=<save>` loads straight into
`run/saves/<save>`; drop Create, Sable, Create Aeronautics and Create Propulsion into `run/mods` to run with them.

The geometry lives in `AirframeShape` (real A380 measurements in metres), is cut into blocks by `AirframeLayout`
and fitted out by `CabinFitOut`, `TailDen` and `HoldFitOut`; the skin follows the curve in `ShellGeometry`'s
shapes, which `ShellModel` bakes, and `Windscreen` says where the glass and its posts go; `AutopilotBlockEntity`
flies, reading the airliner from Sable through `SableFlight`; `EngineIntakeRenderer` draws the intakes round over
the nacelles' faces, from where each engine's core was told they are; `PlugDoorRenderer` draws the hull's doors
shut, swinging and open, from their `PlugDoorBlockEntity`, in the layers `PlugDoorMesh` cuts the panel and its lining
into; `TailCameraFeed` draws the fin camera's
picture through Veil's perspective renderer (Veil comes inside Sable's jar), the one place Veil is touched. Assets
are generated: `node tools/generate_textures.mjs` writes the textures
(ordinary PNGs that can also be painted by hand), and `node tools/generate_resources.mjs <vanilla-assets-dir>` the
blockstates, models, loot tables, tags, recipes and lang file. The door and the call button are vanilla's iron door
and stone button retextured, so their files come from vanilla's: pass the folder the client resources jar
(`build/moddev/artifacts/*-client-extra-*.jar`) is extracted to, or leave it out to keep them as they are.
`node tools/generate_sounds.mjs` writes the sounds with FFmpeg: the engine loop is synthesised, and the flight deck's
voice (the callouts and the cabin announcements) is spoken by Windows' own speech synthesis ("Microsoft Zira"), so
that part runs on Windows only. The script then flattens the voice's intonation and tunes it to the semitones around
one note, and FFmpeg adds a faint vocoder buzz on that note, a metallic sweep and the echo of a small hard room; the
settings for all of it are together in `VOICE`, to tune it.

## License

All Rights Reserved.
