# Aerolarge

A Minecraft mod for NeoForge 1.21.1: an A380-style double-deck airliner big enough to build in, that rolls on real
wheels and is flown from its own cockpit. One blueprint lays the whole airframe down: a single long hollow hull with
a main deck and an upper deck over a cargo hold with a hatch down to it, six passenger doors and a cargo door, a
flight deck behind a wrap-around windscreen, swept wings with four engines, the tail, landing gear, folding airstairs
and evacuation slides.

## Requirements

Minecraft 1.21.1, NeoForge 21.1.251+, [Create](https://modrinth.com/mod/create) 6.0.10+ and
[Create Propulsion](https://modrinth.com/mod/create-propulsion). To assemble and fly the airliner, also
[Create Aeronautics](https://modrinth.com/mod/create-aeronautics) (Sable, Simulated and Offroad); without it the
airliner is a building.

## In game

The blueprints are in the Aerolarge creative tab; there is no recipe for them. The tab sorts everything under
banners: blueprints, airframe, flight deck, cabin, gym hall, skydiving and drops, and the airport.

- **Airliner Blueprint**: the airframe with an empty cabin on both decks.
- **Furnished Airliner Blueprint**: the same airframe fitted out, front to back between the doors, with a business
  class just behind the forward doors (rows of business seats, two side by side left and right of the aisle, with
  seat consoles beside them, on a blue carpet), an indoor moon pool sunk into the hold, tiled in a blue mosaic
  inside a white coping (see The moon pool), its bottom and
  the belly under it glass, so swimmers look straight down through the airliner at the ground or the clouds below,
  with a diving tower on its front rim (a chrome pool ladder up to a small platform three metres up, the cabin's
  glass rails round it, and a springboard sticking out from it over the water), a
  full-height gym hall (Turnhalle) with a court, hoops on glass backboards, wall bars with gym mats in front of them,
  climbing ropes, seats between the windows and gym benches under them, roof trusses with gymnastic rings and the
  teams' banners, and a lounge
  with a bar and seats in the tail and a duty-free kiosk at its front (see below), under a starlit ceiling (navy
  Starlight Panels whose stars gently twinkle, LED strips along its edges), all lit by pendant lights, and behind
  the bar a secret door into a den in the tail; the hold under the cabin is stowed with luggage (both below). Plants
  line the walls: planters (see Planters) on the bar's back shelf, spore blossoms drifting their spores down from the
  starlit ceiling over the bar and from under the diving tower's platform over the pool's rim, planters of flowers
  between greenery along the pool's sides and in its corners, and bamboo by the doors. The seats are cabin seats (see
  Cabin seats), to sit in.
  Passenger screens (see below) hang on the cabin walls between the windows, one in every fourth gap, and wherever
  seats stand in rows across the cabin, they hang on the back of each row, facing the one behind. Cream bulkheads wall
  the rooms off from the doors' aisles, each with a curtained white archway and a backlit sign over it of what lies
  through it, with lavatories by the gym hall's entrance, lit WC signs pointing the way to the WCs and showing whether
  they are free, and an exit sign over every door (see Curtains, bulkheads and signs). Over all of it runs the upper
  deck, galleries round the tall rooms (see The upper deck).

Use a blueprint on the ground: the nose goes just past the block you click and the tail stretches away the way you
face. While you hold one, a see-through miniature of the airliner floats over the block you aim at, turned the way it
would be built there, so you can see which way it will go before you click: nose toward you, tail away, along
whichever of north, east, south and west you face most. It refuses while anything is in the way and says where;
sneak to build through it. Operators can also run
`/aerolarge build <pos> <north|east|south|west> [furnished]`.

At the default scale (2 blocks per metre, set by `scale` in the server config) the airliner is about 145 blocks long
and 160 across the wings, and its cabin is about 14 blocks wide and 11 tall above the main deck's floor, 5 of them
under the upper deck. Its hull is skinned in
blocks cut to the fuselage's curve (`shellStyle` in the server config: by default `SNAPPED`, which cuts every block
through its corners and edge middles only, in slopes that run into each other without a step, and rounds off the inside
of the nose too; `FITTED` cuts each block as close to the curve as one cut can; an airliner built before
0.1.0-alpha.30 loads with its curved skin as whole blocks and has to be built again), and two rows of tall cabin windows run
its length down each side, one for each deck, cut to the curve with it; cabin lights in the crown, the walls and the hold and LED strips along the tops of the
cabin walls keep it bright, and nothing spawns inside it on its own. Outside, the skin is white riveted plating; inside,
wherever it is seen from the cabin, the flight deck, the crew rooms, the den or the hold, it is lined like an
airliner's cabin, in the overhead lockers' off-white: smooth sidewall panels, a darker dado with an air grille along the
foot of every wall where a floor meets it, a deep rounded reveal round every cabin window (down its sides, over it and
under it), light ceiling panels over the crown, and grey floor panels where the skin is underfoot, as along the hold's
floor. An airliner built before the lining keeps its plating inside until it is built again. Overhead lockers line both cabin walls
above head height (not in the gym hall), each holding a cabin bag's worth like a small chest. Outside it carries red and green navigation lights on the wingtips with white strobes,
a white tail light and red beacons above and below the fuselage. The engines' nacelles are skinned round like the hull,
and each intake is faceted to match: a polished lip bevelled into the duct, the fan and its spinner at the bottom of it.
While an engine is at rest an iris shuts its intake; it opens as soon as the engine is asked for thrust, and behind the
turning fan the engine glows a dull red, brighter the harder it runs, until it has spun down and the iris shuts again.
Behind a running engine burns a blue flame, white-hot at its core and flickering, longer and brighter the harder the
engine runs; you see it with Fancy or Fabulous graphics, not with Fast (`engineFlames` in the server config, see Flying
it). Each engine is heard spooling up and down with its thrust, quieter from inside the cabin, and high up it leaves a
contrail. Flying through the cloud layer, the clouds stay outside: while you are inside the hull of an airliner that
reaches into the clouds, they are not drawn for you, so no slab of cloud cuts through the floor, the walls or the
seats; out on the wing or the airstairs, or looking through an outside camera, they are back, and from outside the
airliner still flies in and out of them. With Fast or Fancy graphics the windows never show the clouds behind them
anyway; with Fabulous graphics the clouds outside the windows go too while the airliner is in the cloud layer. Turn it
off with `cloudlessCabin` in the client config.

### Flight deck

Steps at the front of the main deck lead up to a raised floor in the nose. From the top of the steps a short aisle
runs forward to the cockpit door, between the WC to port (a toilet and a basin under its mirror, and a WC sign over
its door, see Curtains, bulkheads and signs) and the crew room to starboard (a row of crew seats and a galley
counter: a galley oven, and a galley worktop with a coffee machine on it); every door opens by hand from either side.
The cockpit door is a big one, two blocks wide and four tall (three in an airliner too small for that, and an ordinary
door in one too small for either): a pale grey panel with rounded top corners, a round porthole high in its middle and
a round chrome handle on each face, which swings out into the aisle about its port side and stands back against the
WC's wall, leaving the way past it clear. It waits to shut while anyone stands in the doorway; villagers do not open
it. Behind the cockpit door the flight deck is a room of its own, raised so the pilots look straight out through the
windscreen: tinted glass cut to the nose's curve like the skin around it, one wide pane right across the front from
the panel up over the crown, running back into side windows past the pilots' shoulders, framed by posts round the
sides only.

The two pilot seats are real airliner seats: a padded black leather back with a headrest and a harness, an armrest
either side, on a column between floor rails. Each is two blocks tall, and using either half sits you down in it. It
is one of Create's seats underneath, so the seatbelts and everything else that knows about seats treat you as
seated, but villager passengers leave it to the pilots. A Pilot Seat can be crafted and put down in your own builds
too.

The seats stand right beside the centre console the pilots share, a single block wide, each control on it a slim
module of it. At its front, just behind the screens, is the throttle: four slim thrust levers side by side, engines 1
to 4 from left to right, moving together; use it to push them a notch forward, sneak and use it to pull them back.
They drive the engines' thrusters through relays hidden in the nacelles. Between the seats is the autopilot's control
head (use it to open the autopilot's settings, see Autopilot), and behind it the captain's handset and the
transponder. Every other switch is a touch control on the screens in front of the seats (see below): the cabin
lights' dimmer, the seatbelt signs, the jump lights (see Skydiving), the window shades, the laser (see Laser path
clearer) and the drop release (see The hold) as tiles on the middle screen, and the nose wheel tiller, the parking
brake and the landing gear as keys on the sloped panel under the screens. Each shows its switch's state in a picture
and a colour. The switches themselves stand out of sight in the avionics bay under the flight deck's floor, as on the
A380, and a touch works them just as using them would, sneaking or not. The parking brake is set when the airliner is
built. The dimmer steps every light in the cabin from bright
to dimmed (as for take-off and landing) to a blue night glow and back, the change running down the cabin front to
back; at night the lounge's starlit ceiling goes dark but for its stars, which glow whatever the light, and the WC,
the crew room and the lavatories keep their lights on as dimmed, as an airliner's lavatories do. Where Veil is
installed (it comes with Sable), the lights still on at night also cast their blue round the cabin near you, like an
airliner's mood lighting: the LED strips wash the walls and lockers, the cabin lights the aisle and the pendants the
floor under them, in flight as well as parked. That light is only drawn; what spawns where still goes by the dim night
glow. Turn it off with `nightLights` in the client config; with Iris installed it is never drawn, and the lights only
glow. The window shades' tile runs every cabin window's shade down, or up again, the same way, with the whirr of
their motors. A closed shade shuts out the daylight; anyone can open or close a window's own shade by using it.

In front of the console the screens curve round the seats like an airliner's glass cockpit, a block above the floor on
a desk whose top slopes down toward the pilots. In the middle, straight, is the systems screen, and either side of it
two screens in the same slim dark casing, each turned further toward the seats than the one inside it, so the row
wraps round the pilots. Left to right: the captain's primary flight display, the moving map, the systems screen, the
outside camera and the first officer's primary flight display. Each is as wide as the nose leaves room for, two blocks
where it can (where the nose is too narrow for the outer pair, the tiller's keys go under the camera). Behind them the
instrument panel carries on round to the hull. The systems screen holds a tile for each of six switches, three across
and two down, each a picture of what its switch does, as it is now, its rim lit while it is on: the cabin lights' dimmer
(a light bulb with long rays, lit pale yellow; with short ones in amber dimmed; a blue moon and stars at night), the
seatbelt signs (someone in a seat with an amber belt buckled across their lap, lit amber), the jump lights (a skydiver
beside the jump light, dark, red or green), the window shades (a window on the sky with its shade up, or pulled down,
lit amber), the laser (its turret firing a red beam, lit red) and the drop release (a crate on the bay's shut doors,
or the doors open and the crate floating down under a parachute, lit red). Touch a tile to work its switch. On the
slope under the screens are the keys: under the captain's flight display the tiller, an arrow either side of a little
wheel turned as the nose wheel is (touch an arrow to steer a notch that way); under the map the parking brake, a wheel
gripped red while it is set, and the landing gear (see Landing gear); under the systems screen the autopilot's buttons
(see Autopilot). Looking at the screens or the slope from within reach, a little white
dot follows your look, and the tile, key or camera button under it is outlined in white, so you see what a touch will
work before you touch it. The flight displays show attitude, airspeed, altitude, vertical speed
and heading once the airliner is assembled. The moving map shows what the autopilot is doing over it and how the
airliner flies under it, the way it is heading always up, the track it has flown, and the circle it flies round or
the heading it turns to; touch it to zoom out a step, round to the closest again. The camera panel, like an A380's taxiing camera screen, shows the live picture from one of the airliner's
outside cameras, the ones the passenger screens look through (see below), the camera on top of the fin at first: the
whole airliner from above and behind, its wingtips at the edges, the ground or the clouds beyond, with CAM TAIL and a
blinking recording light in the corner. Where the nose leaves no room for the camera panel, a tail camera display, a
wide screen three blocks across, hangs from the crown just ahead of the seats instead, tilted 45 degrees down toward
them. Four buttons along the bottom left of the camera's picture pick the camera, each a little airliner seen from the
side with a dot where its camera sits, the one shown lit with a red dot: the fin, the right wing, under the nose by the
nose gear, and in front of the nose, level with the pilots' eyes and looking fifteen degrees down at the runway coming
up to meet the airliner. Use one to switch; everyone sees the camera picked, and the screen keeps it. The picture is
drawn small, a few times a second, and only while a screen showing it is in sight within 32 blocks; every screen
aboard the same airliner set to the same camera shows the same picture. Without
one it shows a test card: colour bars with the airliner seen from behind, and under them NO SIGNAL, or ASSEMBLE TO FLY
while the airliner stands as blocks. That is also what it shows with a shader pack (Iris, Oculus) or Fabulous
graphics, or with `tailCameraFeed` off in the client config, where `tailCameraFps`, `tailCameraResolution` and
`tailCameraRenderDistance` also set how often, how sharp and how far the picture is drawn. It is drawn at most ten
times a second by default (1 to 30), and reaches 12 chunks (2 to 32), never further than your own render distance,
since the world is only loaded that far round you, and the fog closes in at its edge as it does at the edge of your own
view. That is far enough for the whole airliner at the default scale to stand clear of the fog; at scale 3 it takes 18
chunks, at scale 4 24. The further it reaches, the more each picture costs: what the camera sees is worked out afresh
every time it is drawn, while the game waits, about four times as much at twice the distance. A Tail Camera Display
can be crafted and put up in your own builds too: against a wall it stands upright, and put up against the underside
of a block it hangs from it tilted toward you. It reaches two blocks to the right of where it is put, as you face it,
and needs the room there; it comes down whole. On
an assembled airliner with an autopilot it shows that airliner's cameras. Against the wall behind the captain's
seat is the Physics Assembler, and across from it, against the wall behind the first officer's, the flight crew's
locker, with a Pilot Helmet and a Pilot Cap in it for each pilot when the airliner is built (see Souvenirs): within
reach of the first officer's seat at the default scale, a few steps from either seat on the larger flight decks.

A Flight Crew Locker is a slim cabinet of brushed grey panels for the crew's things, its white door hinged on one side
with a small chrome handle on the other, and on the door the pilot's helmet, white with its blue stripe and gold visor,
so even someone who cannot read yet knows what is inside. Use it and its door swings open with the clank of a light
metal cabinet onto a row of nine slots; it swings shut again once nobody is looking in. It can be crafted (a chest and
an iron trapdoor in iron) and put down anywhere, its door toward you; broken, it gives itself back and spills what is
in it.

In a nose too narrow for the centre console and its screens the seats stand further apart with the four thrust
levers between them and the autopilot's control head among them, the tiller and the parking brake outboard of the
seats where there is room, and the switches, the dimmer, the handset and the gear lever on the panel either side of
the autopilot screen, a moving map with the autopilot's buttons lit beside it (touch the map to zoom, anywhere else
to open the settings). There a flight display in the panel stands in front of each seat, and the tail camera display
on top of the autopilot screen, as one glass unit with it. Airliners built before keep the flight deck they were
built with.

The flight deck's voice, a calm, very even, faintly synthetic woman's voice, unfailingly polite and not easily
impressed, calls out the take-off (V1, rotate, positive rate) and the landing (the height above the ground from a
hundred down to ten, then retard), and warns of too much bank ("Bank angle. Please do not do that.") or sinking too
fast too low ("Pull up. This is not a test."), whoever flies. The same voice makes the autopilot's cabin
announcements (see below).
Riding the airliner, the camera rumbles on the runway, thumps on touchdown and sways a little in flight; turn that
off with `cameraShake` in the client config.

### Landing gear

The landing gear folds up into the belly in flight. Its key is on the sloped panel under the moving map: a wheel, an
arrow the way the gear lever is thrown (down for the gear down, up for it up), and three lamps, one for each group of
legs: green with the gear down and locked, red while it moves, dark with it up. Touch it to throw the lever the other
way. (On older airliners the gear lever itself stands on the flight deck, a lever with a little black wheel for a
knob and the three lamps beyond it; use it to throw it.) Going up takes
eight seconds: the doors under each leg swing open, the leg swings up into the belly (the nose gear forward, the main
gear in toward the middle) and the doors close behind it, with the sounds of a real airliner's gear from the cabin:
the clunk of the uplocks, the roar of the air past the open doors, the hydraulics moaning, the wheels braked to a stop
in a rumble, the thump of the legs into their bays and the doors shutting. Coming down takes six: the air roars in,
the legs drop and lock down with a heavy thunk, the doors close, and the rumble of the gear out in the airstream
follows. Folded up, a leg's wheels and everything on them are gone from under the airliner, and they come back as they
were, tires and all.

The gear never goes up while the airliner stands on its wheels: the lever bumps back down with a buzz and the lamps
flash red. Coming down near the ground with the gear not down (sinking, its wheels less than 30 blocks above the
ground), the gear horn beeps on the flight deck and the lamps flash red; nothing stops a pilot landing on the belly,
though, and the airliner slides to a stop on it. Thrown down then, the legs swing down and wait until there is room
under them: lift the airliner off the ground (Unstuck does, see Autopilot) and they lock down. The autopilot works the
gear by itself (see Autopilot). A Landing Gear Lever (a tire on a lever on an airframe panel) and a Landing Gear Bay
can be crafted, but only an airliner built with retractable gear has its legs wired to them. Switch it off with
`retractableGear` in the server config: an airliner built then keeps its gear down, and one built before can still
lower its gear but no longer raise it.

### Laser path clearer

On the systems screen is the laser's tile, a little turret firing a red beam (on older airliners, the laser switch on
the centre console: a big red button under a flip-up guard, pictured the same). Touch it and its rim glows red and,
with a rising whine, a turret drops out of a hatch in the underside of the nose. Once out, it fires rapid
short laser bolts, five a second, each a bright red beam with a sharp hiss and sparks where it strikes, at whatever lies in the assembled
airliner's flight path up to 96 blocks ahead (where it is going, or, standing still, where its nose points), as wide as
its wingspan and from under its wheels to the top of its fin: hostile mobs first, each bolt hurting one (two or three
bolts for a zombie), and otherwise the nearest block in the way, each bolt vaporising a bite of up to 27 blocks round
it, the clearing spreading from there across whatever stands in the way. Press the button again and the turret folds
back in. Low down (less than 15 blocks over the ground, until it is 25 blocks up again) with the engines idle it holds
its fire at blocks and shoots only hostile mobs, so a parked airliner never digs into the ground round it; the button
then glows amber instead of red, with a soft beep down as it starts holding and up as it clears the way again. With
the engines running it clears the way however low it is, taxiing, taking off, or pushing at something in its way. It
never vaporises anything standing no higher than the bottom of the airliner's wheels, so the runway under it is safe.
The laser fires only from an assembled airliner.

It never fires at players, villagers, pets or any mob that is not hostile, nor at anything aboard an airliner. It
leaves alone blocks with something in them (chests, signs and the like), blocks that cannot be broken, liquids and
other airliners, and blocks entirely wherever the `mobGriefing` game rule is off; it asks first, as a player breaking
the block would, so land a claim protects keeps its blocks. Turn it off with `laserPathClearer` in the server config
(the switch then only buzzes), where `laserRange` and `laserBoltsPerSecond` set how far it reaches and how fast it
fires. A Laser Switch and a Laser Turret can be crafted, but only an airliner built with the laser has them wired.

### The upper deck

Like an A380, the airliner has two decks. The upper deck's floor runs three metres over the main deck's, under the
upper row of windows, from just behind the flight deck back to the lounge. A carpeted staircase winds up to it at the
front, to port beside the steps up to the flight deck: in at its corner by the forward doors, it climbs forward beside
the steps, turns outboard, comes back aft along the hull's side and turns inboard again, round a white newel, one and
a half times round at the default scale (wider and less often round at the larger ones). It only ever turns on a
landing, level with the step before it, and keeps two blocks of headroom all the way, so it is walked up and down
without a jump. At the back a straight flight climbs to the deck up the starboard wall, a metre short of the gym
hall's end. Glass rails, frameless glass on slim brushed steel posts under a steel handrail, run along the stairs' open
sides, sloping with the flights, and round the floor's open edges and the stairwells, and cabin lights set into the
floor down its middle light both decks at once; the dimmer and the seatbelt signs work the upper deck's lights and
signs with the rest. Like every Cabin Floor block it is carpeted on top and finished elsewhere: seen from below it is a
ceiling of white panels (as the main deck's floor is from the hold), and round its open edges the carpet's edge runs
over a steel trim and a white fascia. In the Airliner Blueprint the upper deck is one floor the
whole way, to build on like the main deck. In the furnished airliner the rooms keep their height: over the moon pool
and its diving tower, the gym hall and the doors' aisles, whose bulkheads carry the archways' signs, the upper deck
is a gallery along each wall, railed round an open well to look down into the rooms through, with doorways through
the bulkheads; the gym hall's rings and climbing ropes and the pendant lights by the walls hang from the galleries
where they run over them. Over business class it is a whole floor between business class's bulkheads, on a grey
carpet: rows of economy seats facing forward (three side by side either side of the aisle at the default scale, more
at the larger ones), each with a passenger screen on its back for the seat behind, a cross aisle at either end
onto the galleries and a walkway along the windows; business class's pendant lights hang from its underside. Where
the floor behind the flight deck is deep enough for the forward staircase (from scale 3), it is whole too, with the
upper deck's own galley (its trolley bay, galley stowage and a galley oven) against the starboard wall across from the
stairs' top. An exit sign glows over the top of each staircase, the way down to the doors, and beside it an overhead
WC sign points down the stairs, as the upper deck has no WC of its own. At scale 1 the airliner is too small for an
upper deck.

The staircases are built of blocks that can be crafted for building stairs anywhere. Cabin Stairs climb in two half
steps to a block, as any stairs do, the way you face as you put them down (against the underside of a block they go
in upside down, all white, for a flight's underside): each tread carpeted in the cabin's blue between white stringers,
with a slim brushed steel nosing along its edge, over white risers. A Cabin Landing is the flat carpeted block between
flights. A Cabin Rail is the glass rail: it stands along an edge of its block, the one you face as you put it down, so
the block itself can still be walked through; put another into it to add an edge, round a corner. Over a stair it
slopes with the flight, its glass down to the treads.

### Announcements and the radio

Use the captain's handset and your next chat message is a cabin announcement: everyone aboard hears the two-tone
chime and sees it on screen under "Captain" and your name, and it is kept in chat. Sneak and use it, and the message
is a radio call instead. A Tower Radio (craftable) is for the airfield: whatever is said into it goes out over the
radio. A radio call crackles in for everyone near any radio within 4096 blocks, the handsets of airliners in flight
included. Either way the message is left out of ordinary chat; use the handset or radio again to put it down.

The autopilot makes announcements of its own, in the flight deck's voice after the same chime, and everyone aboard
hears them wherever they are in the cabin. Set to take off (Take Off, on the ground), it welcomes everyone aboard and
asks the cabin crew to be seated for take-off; set to land (Land), it asks everyone back to their seats and the crew to
be seated for landing; and once the airliner has landed and stopped, however it was flown (on the autopilot or on the
thrust levers), it welcomes everyone to their destination and lets them go. As crews do, it switches the seatbelt signs
on with the take-off and landing announcements (which opens the curtains), and off again once stopped after landing,
so everyone can get up. Announcements never talk over each other: one asked for while another plays waits for it to
end, and the same one is not made again within twenty seconds, however often the buttons are pressed.

Overhead, hung close under the ceiling over the centre console between the seats, is the announcement panel, a slim
quarter of a block deep: nine tiles, three by three, each making one announcement in the same voice when pressed (use it), for young pilots too, since every
tile is a big picture first and its name only small under it. Read from the seats, row by row in the order of a flight:
Welcome (someone waving hello), Take-off (the airliner climbing off a runway; the autopilot's own announcement),
Seatbelts (a cloud over bumpy air and a buckled belt: bumpy air ahead, seatbelt signs on), Snacks (a cup and a
cookie: drinks and snacks are coming), Look out (a cabin window onto the sky and mountains), Pool & gym (a basketball
over the pool: seatbelt signs off, the pool and the gym open), Sleep (the moon and stars: the cabin lights go down to
their night glow), Landing (the airliner coming down to a runway) and Arrival (a heart over the parked airliner). A
tile glows in its colour while it can be pressed, lights up and pulses while its announcement plays, blinks while it
waits for the flight deck's voice to be free, and greys out while another is playing and for twenty seconds after its
own. An Announcement Panel can be crafted and put up in your own builds too, against the underside of a block, its
middle tile where you put it and its top row nearest you as you face; it works the nearest airliner's autopilot within
24 blocks.

Air traffic control talks to the airliner over the flight deck's radio, calling it "Aerolarge three eight zero": a
man's voice, so it is never taken for the flight deck's own, crackling through a VHF radio's hiss and squelch. Set to
take off, the autopilot holds the brakes until the tower has cleared it for take-off, a few seconds, and the cabin
hears the take-off announcement as they come off. Climbing away past 20 blocks above the ground, on the autopilot or
by hand, the tower hands it to departure, which has it on radar and climbing. Set to land, the tower clears it to
land, with the wind and the runway, before the cabin hears the landing announcement; going around (Apply while
landing) has it climb straight ahead. Down and stopped, ground welcomes it home and sends it to the gate. In flight,
30 blocks or more above the ground and not landing, it chatters every two to five minutes: first the hand-over to the
next controller, then traffic to look out for, the ride ahead, a flock of geese or a hot air balloon to spot out of the
window, and other aircraft on the frequency, one of them saying hello; now and then someone presses the button and
says nothing. Everyone on the flight deck hears it (within 24 blocks of the autopilot, as far as it tells the flight
deck what it does), and so does whoever wears a pilot helmet anywhere aboard: its ear cups are a headset. It never
talks over a callout or an announcement: it waits for them to end, and keeps clear of a callout about to be made.
What has waited too long, or no longer fits what the airliner is doing, is left unsaid. Each transmission has its own
subtitle ("ATC: Cleared for take-off"), and while it talks a little radio panel shows in the top left corner of the
screen of whoever hears it: a pilot's headset with the radio's green lamp lit, gently pulsing, so that those who
cannot read yet know the radio is talking too. Turn it off with `atcChatter` in the server config, or make it quieter
with `atcVolume`; each player's Voice/Speech slider turns it down too.

### Seatbelts and turbulence

The seatbelt signs' tile (the seatbelt switch, on an older airliner) lights the "fasten seatbelts" signs along both cabin walls on both decks, with a ding, and opens every curtain
aboard, front to back, as cabin crew do for take-off and landing. While the signs are on, anyone seated aboard stays
in their seat when they sneak to get up. The autopilot works the switch too, with its announcements (see above).
In flight the airliner runs into rough air now
and then, a few seconds of it about once a minute: everyone aboard feels the shake, and whoever is not sitting down
is thrown about the cabin. The weather makes it worse. In rain (or snow) it comes a little more often and is a little
rougher; a thunderstorm brings heavy turbulence, about every 15 seconds, twice as long and up to nearly twice as
hard: a stronger shake, and bumps that knock anyone standing off their feet. Only where something falls, though: a
storm passing over a desert is as calm as clear skies. Turn it off with `turbulence` in the server config.

High up (60 blocks or more above the ground) the airliner now and then drops into an air pocket: it falls a few
blocks in a moment, with the rush of air and everything loose in the cabin rattling, until the air catches it again
with a thud through the airframe. Everyone aboard sees their view drop away and swing back, and whoever is standing
floats up off the floor for a moment, as do balls and trolleys; anyone seated stays put. The higher it flies, the more
often (about every four minutes at 60 blocks up, every minute from 200), half again as often in rain, and three times
as often and half again as deep in a thunderstorm. The autopilot climbs back to the height it holds. Turn it off with
`airPockets` in the server config.

Flying through a thunderstorm (15 blocks or more above the ground, where it rains), the airliner is struck by
lightning about once a minute, twice as often from 150 blocks up: a real bolt cracks into it, mostly at a wingtip, the
nose or the tail, with the sky's flash and a crack of thunder right there. Everyone aboard sees a blinding white
flash, the cabin flickers dark twice, the flight displays fill with static for a moment and the radio crackles. Like a
real airliner it comes through unharmed: nothing aboard is hurt and nothing is set alight. Turn it off with
`lightningStrikes` in the server config.

### Autopilot

Use the autopilot's control head on the centre console (or the big screen in the middle of the instrument panel of an
older airliner) to open the autopilot's settings. Under the systems screen, on the sloped panel, are its buttons: on
and off, and a pair for each of the heading, circling, the altitude, the speed and the circle's size, each pair in
the setting's picture with an arrow the way it steps. While it flies they step what it flies (the heading 15 degrees
either way, circling left or right, the altitude 20 blocks, the speed 5 blocks a second, the circle 50 blocks), and
the on/off button switches it off; switched off, any of them opens the settings. The control head's own small display shows what the autopilot is set to, in the same
pictures and colours as the settings, turned to face whoever looks at it: a lamp (green while it flies the airliner,
amber while it is stuck, dark while it is off), what it is doing (take-off, climb, cruise, descent, landing, rolling
out or off) and how it steers, and the height, speed and heading (or circle) it is set to, each a picture beside its
number, greyed out while it is off. At the top the settings show what the autopilot is doing, with a lamp (green
engaged, amber off), how the airliner flies now (speed, height and heading) and, once engaged, what it is set to fly. Below are the
settings: hold a heading (a straight arrow), circle left or right round a point beside the airliner (arrows round
to the left or right), or fly to a pin (a red map pin); the heading (a compass, a compass bearing); the altitude to hold (a hill, from 40 blocks above
the runway to 30 below the top of the world); the speed (a speedometer, 30 to 120 blocks a second); and the circle's
radius (a circle, 150 to 1000 blocks; a circle too tight to fly at the speed set is flown wider round the same
middle, so it never banks more than 20 degrees going round). Slide each one, step it with the arrows either side of
its slider (15 degrees, 20 blocks, 5 blocks a second, 50 blocks), or type it into the box after it. Every setting
and button has a picture and a colour of its own, so that players who cannot read yet can fly too; point at one to
see its name. The game goes on behind the settings, and they stay open, to watch the autopilot fly, until you press
the X in their title bar (neither the buttons nor Escape close them).

Set to fly to a pin, the rows of settings give way to a map: the land and the villages as the route map on the
passenger screens has them (see Passenger screens), the airliner as a gold arrow, and the pin, once dropped, as a red
dot in a red ring, the circle it will be flown round. Drag the map to look about, scroll to zoom out and in, and click
to drop the pin: a click close to a village drops it right on the village. Every transponder shows on the map too
(see Control tower and transponders), by its picture in its colour on a dark disc, as a beacon shows in Subnautica,
other airliners with a few dots the way they fly; point at one to see what it is, where and how far, and click on one
to drop the pin right on it. Every other player in the dimension shows on it as well, however far off, as their face
in a white frame, where they were a second ago (those riding this airliner are left out, its arrow standing for them);
point at a face to see who it is, where and how far, and click on it to drop the pin where they stand. The pin stays
where it was dropped; it does not follow them. A small cross marks the middle of the map,
whose X and Z show in its bottom left corner (and the pin's in the bottom right once dropped), so you can find your
way about even where the land has not been seen yet. Engaged, the airliner turns for the circle
round the pin, heading for it along the line that just touches it, so it comes onto the circle already turning, and
flies round it clockwise at the radius set (150 blocks at least, banking no more than 20 degrees), until set to fly
something else or told to land. The height and speed fly as set.

Beside the mode, while it flies to a pin, is Land at the Pin: an amber airliner settling onto a runway by a little red
pin, its tile lit green while it is on (point at it to see whether it is). Like every button, it has its picture and
its colour so that players who cannot read yet can use it: pick the pin on the map, light the button, engage. Then the
airliner does not circle the pin but lands near it, on its own, somewhere clear of buildings and trees. It flies
straight to the pin and on over it, back and forth, looking at the land below: the server only has the land near the
players aboard to look at, so it has to fly over it first. Meanwhile it measures itself, wingtip to wingtip and nose to
tail, and looks for a strip of land within 320 blocks of the pin, nearest first and every 15 degrees round, where it
fits: dry, level ground (no more than 2 blocks above or below where it touches down, and no step higher than 1) for
its wheels all the way from touchdown until it has braked to a stop, a good 400 blocks with room either end; nothing
taller under its wings and engines than they stand above the ground; and nothing under the end of its glide down that
it would not pass over. Once it finds one it says so on the flight deck, and the strip shows on the map as a white
line. It slows down, flies out to a point well back along the strip's line (coming round from the side if it is on the
wrong side of it), comes down to 60 blocks above the strip, turns onto the line, then comes down a gentle glide path
toward it, cleared to land by the tower, the cabin hearing the landing announcement. It flares as Land does, steering
along the strip's middle all the way, touches down, brakes to a stop steering along the line, and parks. If nowhere
near the pin is quite clear, it picks the strip with the fewest things under its wings (never under its body); if
there is nowhere at all, it says so once and keeps flying over the pin, looking again as more land comes into view.
Not lined up with the strip as it comes to flare, it goes around and tries again. Applying the settings on the way
in goes around too: with Land at the Pin still lit it looks again and comes back round; switched off, it circles the
pin instead, though below the flare's 15 blocks switching it off is too late: it lands.

Where a runway laid down from its blueprint (see Runways) lies in the circle round the pin, it has nothing to look for:
as soon as it has measured itself it lands on that runway, from whichever end it comes to with less turning, touching
down just past the threshold and braking to a stop on the centre line. Everyone aboard as it stops there earns On the
Numbers.

Switched off, the settings start from what engaging would fly, and engaging (the wide glowing button, or Enter)
flies them. Engaged on an airliner not yet assembled, the autopilot first has its Physics Assembler assemble it, as a
Create deployer works the assembler, and engages with the settings once the airliner is assembled, telling everyone
aboard. The Take Off button greys out the moment it is pressed, with "Assembling the airliner..." on the screen, and
comes back if the airliner cannot be assembled; asked again meanwhile, the autopilot only says it is still assembling.
Whoever engaged it from a seat stays in it: assembling moves the seat, and the autopilot sits them back down.
With no assembler aboard, or one that cannot assemble it, it says so and why.
Engaged on an assembled airliner standing on the ground (the button says Take Off), it takes off: it has the
airstairs folded away, any evacuation slide packed and the gear down first (down on the ground they would hold the airliner where it
stands), and once they are and the tower has cleared it (a few seconds on the brakes, see Announcements and the radio;
it waits for the stairs and slides 20 seconds at most, as someone standing on the stairs keeps them out), brakes off, speeding up down the runway no faster than an airliner does (3 blocks a second every second, so nobody standing is
thrown down the cabin; it speeds up and slows down that gently in the air too), holding the heading it started on
with the nose wheel, nose up at rotation speed, then a climb straight ahead, wings level, until it is 30 blocks
above the ground (or at the altitude set, where the ground below rises that high), raising the gear once it is 10
blocks up and climbing (see Landing gear). Only then does it turn onto the
heading or circle set, banking no steeper than its height allows (it never banks hard near the ground, and never
more than 25 degrees), and it climbs on to the altitude set and holds height and speed there. The altitude starts at
the cruising height (`climbHeight`, 80 blocks above the runway by default) and the speed at `cruiseSpeed`. Engaged
in the air, they start from the height, speed and heading it finds. While it is engaged, Apply flies the settings as
changed (it stays dark until something on the panel differs from what the autopilot flies, but while it lands,
when it goes around), and Disengage hands the airliner back to the thrust levers, the tiller and the parking brake. How it is
flying shows on the pilots' head-up display (see below).

In the air, Land puts the airliner down ahead, engaging the autopilot if it is off. It rolls out of any circle onto
the heading it is on, slows until it flies with its nose level and sinks 3 blocks a second, banking less the lower it
gets; from 15 blocks above the ground it flares, sinking ever more slowly, to touch down at half a block a second.
How fast it flies nose level depends on how heavily it is loaded and how thin the air is, so it finds that speed on
the way down, starting from a tenth above the rotation speed (`rotateSpeed`): furnishing it or loading it up needs
no new settings. Down, it idles the engines, lowers the nose and brakes to a stop, steering along the heading
with the nose wheel; then it pulls the thrust levers to idle, centres the tiller, sets the parking brake and
switches itself off. It lowers the gear as it starts down, and on a landing at the pin as it turns onto final. It lands
on whatever lies below, so line it up over open, level ground first. Applying the
settings while it comes down goes around, climbing away to the altitude set.

An airliner left flying with nobody aboard (everyone jumped out with a parachute, say) lands by itself after a minute,
as if Land had been pressed, so it does not fly on for ever. The minute stops and starts over as soon as anyone is
aboard again, and is not counted on the ground or while it lands already.

An airliner that will not move, held by whatever it has run into or sunk into, can be hopped free, wherever it rests:
on the ground, on trees, on a roof. Once it has stood still (slower than a block a second) for 2 seconds with its engines running (on the thrust levers,
or taking off on the autopilot) and its brakes off, the settings show Unstuck (a cyan airliner lifted off the grass on
two white arrows over puffs of exhaust) in place of Land. It fires the control jets under the wings at full,
with the roar of jets on the flight deck, taking the weight off the wheels so the engines can move the airliner again.
The jets under the tailplane fire too, but all of them push from behind the centre of mass, and together they would
tip the airliner onto its nose, so the tailplane's hold the nose where it stood: under it at full while the nose stays
up, easing off as it dips and pushing the tail down past that. A click fires them for two and a half seconds; held
down, they keep firing until it is let go, so the airliner can be lifted over a house it has run into, while the
engines carry it forward. The burst never lasts longer than 10 seconds, and ends as soon as the airliner would coast
up 12 blocks; then the jets are the autopilot's again, and they rest 5 seconds before they hop it again. It is not
offered while the brakes hold the airliner: the parking brake set is what keeps it from moving, so
release it first (Unstuck leaves the brake as it is).

The big screen shows the autopilot at work. Its right side is the autopilot's buttons, lit for what it is doing;
touching them opens the settings. Its left side is a moving map, heading up: the track flown, the circle being
flown or the heading being turned to, and north; touch the map to zoom it out, and again past the widest to zoom
back in.

Wearing a Pilot Helmet (see Souvenirs) on the flight deck of an assembled airliner, within reach of the autopilot (in
either pilot's seat, or standing at the console), its visor shows a head-up display: thin, see-through green lines
and figures, as a real HUD's, edged with a thin dark line so they read against bright sky, with whatever the
autopilot is set to fly in magenta; without the helmet there is none. Putting the helmet on there switches it on with
a beep, and it fades in. It comes in parts, kept clear of the rest of the HUD and of the middle of the view. Close
round the crosshair are a short horizon line and a faint pitch ladder, about 15 degrees of it either way, tilting
with the bank and dropping as the nose comes up (the horizon amber past 35 degrees of bank), and the flight path
marker, a small ring with wings showing where the airliner is going. Left of the hotbar is the speed on a narrow
moving tape, the speed set as a magenta mark beside it and an arrow to where the speed will be in ten seconds; beside
that a picture of what the autopilot is doing over its name: taking off, climbing, cruising, descending, landing,
braking to a stop after landing, or a red power symbol while it is off. Right of the hotbar is the height on a moving
tape (the autopilot's own, as it holds it), the height set as a magenta mark and, once it is near, the ground rising
up the tape as an amber line; beyond it a bar that climbs green or sinks amber, and beside that the wheels' height
above the ground over the climb or sink with an arrow. At the top of the screen, under any boss bars, is a heading
scale with the heading in a box, the heading set as a magenta mark and the mode beside it (straight on, or circling
left or right with the circle's radius). The speed and the height stand out against the screen's left and right
edges, 40 pixels in from them, level with the crosshair, or both higher to keep the speed above the chat and the
height above the subtitles. On a short screen the chat can reach up past the crosshair; it is drawn over the horizon
there. Each part curves with the visor where it sits: the visor bulges out in front of the eyes, so what is on it is
drawn in toward the middle of the view, the more the further out it is. The heading at the top arches, its ends
curving down and away; out at the sides the tapes bow outward; everything narrows a little toward the edges; and the
horizon in the middle stays straight. The head-up display is drawn about half the size of the rest of the HUD and grows with the
GUI scale; `pilotHudScale` in the client config makes it bigger or smaller (smaller again on a small screen). Being on
the visor, it shows only in first person. It hides with the rest of the HUD (F1), behind any screen, under the debug
screen and while you look through an outside camera; turn it off with `pilotHud` in the client config. While you wear
the helmet and look out through your own eyes, its visor also tints the view a touch toward its gold, darkens it
faintly toward the edges and catches a little light along its top.

The airliner has no control surfaces that move, so the autopilot flies with thrusters: besides the engines there are
control jets on top of and under the outer tailplane (to pitch it) and the outer wings (to roll it). It turns by
banking, so the wings' lift turns it. Sable's wings lift with the airspeed even flying level, and the engines push
hard along the nose, so it learns the pitch that holds the height at its speed as it flies, and the push the nose
needs to stay at that pitch, so it settles on the height set rather than a few blocks off it. It eases the engines'
split as it swings toward a heading, so it rolls out onto it without rocking to and fro about it. Part of the
tailplane pushes down and the fin's lift cancels out, so the airliner keeps its nose up and runs straight at speed. It
needs Sable (Create Aeronautics) to read where the airliner is.

As an experiment, `controlSurfaces` in the server config (off by default) has the autopilot pitch and roll the
airliner with control surfaces instead, the jets kept for safety. Sable pushes each wing block along the way it faces,
as hard as it goes through the air (and as thick as the air is), whatever angle the air meets it at; turned upside
down, a block pushes the other way, and drags just as before. So the surfaces are the wings' own lifting blocks,
turned over in flight: ailerons over the whole outer half of each wing, front to back (turned over on the right wing,
they push it down and roll the airliner right; on the left, left), and the whole tailplane as the elevator, the way an
airliner's trimmable stabiliser moves (its blocks that face up, turned down, raise the nose; those its trim turned
down, turned up, lower it). One block turned over pushes very little against the airliner's weight, so it takes a lot
of them: a strip along the trailing edge, as on a real airliner, rolled it no faster than a single step of the jets.
The more blocks turned, the harder they push, the furthest out along the wing and the furthest aft along the tailplane
first; and the faster the airliner flies and the thicker the air, the fewer it takes, worked out from what each block
was measured to give in flight. The surfaces follow what the autopilot asks for, smoothed over a few ticks, as far as
they reach, sweeping at most 40 blocks a tick and standing still within a few blocks of where they should be; and the
jets give exactly what the surfaces do not give as they stand, so between them the autopilot always gets what it
asked, and the surfaces can only ever take work off the jets. Below 1.3 times the rotation speed (until it slows below
1.2 times), so on the take-off run and once a landing has slowed the airliner to its approach speed, and whenever it
is past 25 degrees nose up, 20 down or 35 of bank, the surfaces sweep back as built and the jets fly it all, as they
always have. In the test flights, the surfaces carried nearly all of the cruise, the climbs and descents and the
turns, the jets coming in for the take-off, the landing and the sharpest part of a turn. A wing block looks the same
either way up, so the surfaces are shown as hinged panels along the trailing edges instead: an aileron on the outer
part of each wing and an elevator along each side of the tailplane, made of the wing's own blocks and split where the
trailing edge steps in, each hung on a dark hinge rod with brackets. They swing up to 25 degrees with how hard the
surfaces steer, over and above the slow, steady part of it that keeps the airliner trimmed, the way a real airliner's
ailerons and elevators move about its trimmed stabiliser: the ailerons kick one up and one down as it rolls into and
out of a turn, and the elevators sit near the middle in level flight and tilt on climbs and descents. The panels are
drawn, not built, so they never touch the airliner's flight; where each lies at rest an invisible plate, as thin as
the wing's blocks and level with them, lets the wing be walked out to its very edge, and stays flat as the panel
swings. They are drawn wherever they are in view, even with the cockpit, whose autopilot draws them, out of sight.
Only airliners built since the surfaces came in have them, and only those built since the panels came in show them
(and since the plates came in, can be walked on); older ones fly on their jets whatever the setting. Switched off, or
once the airliner is disassembled, its surfaces turn back as they were built, and the panels swing back level.

Switched off, the autopilot still keeps the airliner safe while it is flown on the thrust levers. In the air the
control jets damp quick pitching and rolling and push it back once the nose goes more than 25 degrees up or 20 down,
or the wings more than 35 either way, righting it even from upside down. Past 90 blocks a second, or climbing fast
enough to coast past the top of the world, it cuts the engines itself, and hands them back to the levers once the
airliner has slowed to 75 and is well below the top again. Without that, full thrust would loop the airliner or
fling it off faster than the world can be generated around it.

### Cabin service

At the head of the lounge's bar is the galley, a stainless trolley bay with a trolley parked in it under its control
panel. Use it to roll out a full trolley, or to restock the trolleys standing by it. Push a trolley down the aisle by
walking into it (it squeaks); use it to be served the next of its meals: a chicken meal, airline coffee (a minute of
Speed), a pasta meal or pretzels, two dozen to a trolley. Sneak and hit it to fold it up and carry it. Galley, trolley
and food can all be crafted. Aboard an assembled airliner a trolley rolls with the cabin, in flight as on the ground,
the way Create's seats ride along: inside the hull it stands among the airliner's own blocks, and the galley rolls out
and restocks trolleys there. Rolled out of an open door it drops out of the airliner, and in flight it leaves at the
airliner's speed.

The galley's other units are as stainless, each put down facing you: a Galley Oven, a dark glass door under a strip of
controls and status lights, which cooks food as a smoker does, twice as fast as a furnace, its window glowing orange
while it cooks; Galley Stowage, two by two standard containers each held shut by a red latch; and a Galley Worktop,
drawers under a brushed steel top for a coffee machine to stand on. The stowage and the worktop are cupboards, a
chest's worth each, their latches clacking as they open. All three can be crafted.

A Bar Counter is a piece of a lounge bar: a white front curving out from a dark plinth up under a dark stone top, a
strip of blue LED light glowing along its foot; side by side the pieces run on into one bar, and things stand on it.
A Bar Stool is a chrome stool with a round oxblood leather seat and a footrest ring, to sit on: it is one of Create's
seats underneath, as the cabin seats are. A Back Bar is a walnut cabinet with smoked glass doors under a stone top;
put another on top of it and that one is lit shelves of bottles in front of a smoked mirror (shelves left with nothing
under them come down). All three can be crafted. The furnished airliner's lounge has its bar along the starboard wall:
the counter with stools in front of every other piece, and behind it the back bar, its shelves of bottles rising
between the windows with planters between them.

A Coffee Machine is a bean-to-cup machine, as in an airliner's galley: a brushed steel and black body, a bean hopper on
top, a drip tray under its spout and a small lit display on its front. Use it and its panel opens, four big buttons
each showing its drink, so it needs no reading: an espresso in a small cup, a cappuccino with its foam, a mocha in a
tall glass under its cream, or for children a babycino, a little glass of frothed milk with no coffee in it, dusted
with chocolate and topped with marshmallows. It grinds the beans, then pours the coffee into a cup that stands under
the spout and, for a cappuccino or a mocha, froths the milk with a hiss of steam from its wand (a babycino it only
froths); its display shows each step in a picture
(a green cup when ready, a bean, a pour, steam, a green tick when done), and it chimes when the drink is ready. Use it
again to take the drink out; while it brews it only beeps. An espresso gives 45 seconds of Haste, a cappuccino 45
seconds of Speed, and a mocha, sweet with chocolate, half a minute of Speed and a few seconds of Regeneration, each a
little more filling than the one before, and a babycino ten seconds of Regeneration; the cup goes with the drink.
Barista asks only for the three coffees. The machine can be crafted, the drinks only
brewed. Both blueprints have one on the galley worktop in the crew room behind the flight deck, and the furnished
airliner another on the lounge's bar, just behind the galley.

### Curtains, bulkheads and signs

A Cabin Curtain hangs in pleats across the way you face; curtains put up beside, under or over one another join
into one curtain on a steel rail. Use any piece to draw the whole curtain open or shut, a column at a time away from
the piece used, with a swish; drawn open, only its gathered ends and its rail show, and those are what to use. Use a
dye on it to dye all of it, for one dye, and taken down it keeps its colour. It is only cloth: players, villagers,
trolleys and basketballs pass straight through it, open or shut, and it keeps no light out. A Cabin Partition is a
plain cream bulkhead panel to build walls with, a Cabin Arch a piece of a white archway in one, a Bulkhead Sign a
backlit sign set into one, and an Exit Sign a green running man heading out through a doorway. All five can be
crafted. A Cabin
Arch piece is a block of a round white arch over an opening three or five blocks wide, or of the jambs either side of
it: smooth white panelling round the opening with a moulding along its edge, a bead and a groove behind it, set into
cream panels like the partition's and the same from either side. Taken down, or picked from an archway, a piece keeps
which piece it is; put down, it is the right-hand side of an archway as you face it, or the left-hand side sneaking.
A Bulkhead Sign is a block of the partition's cream panelling with a lightbox in its face: a picture printed on white
behind a slim brushed steel bezel, glowing whatever the light around (though it lights nothing), so it shows with the
cabin lights set for the night. Each picture says what lies beyond without a word, in its room's colour: a seat, a
drop of water over the waves, a basketball, a steaming cup, or a man and a woman for the lavatories. Put down, it
faces you; use it with an empty hand for the next picture, sneaking for the one before. Taken down, or picked, it
keeps its picture.

The WC signs say whether a WC is free, in pictures. A WC Sign is the one over a WC's door: a block of the partition's
cream panelling with the bulkhead sign's lightbox in its face, showing a toilet on a dark face, lit green while
nobody is in the WC and red while anyone is, a player or a villager, with someone sitting on the toilet then too, so
it says so without the colour. It looks into the WC a couple of times a second, aboard an assembled airliner in flight
as well; put down by hand, it faces you and watches the room behind the wall it is set into (three blocks deep, three
across, from two rows under it up to its own, as behind a door under it). An Overhead WC Sign is the lit sign high up
in an airliner's cabin that shows the way to the WCs: a slim off-white lightbox with a dark face, a white arrow on it
(straight on, left, right, down the stairs, or none) beside the same toilet, lit green while one of the WCs it shows
is free and red, someone sitting on it, while all of them are taken. Against the side of a block it stands out from
it; against the underside of one it hangs from it on two short rods. Put up by hand, it shows every WC whose sign is
within 16 blocks of it (8 up or down); use it with an empty hand for the next arrow, sneaking for the one before (one
put up before any WC sign round it looks for them again then). Taken down, or picked, it keeps its arrow. Both can be
crafted.

In the furnished airliner a partition wall stands between each room and the doors' aisle beside it, and between
business class and the pool, with an archway over the aisle hung with a curtain in the room's colour (navy for
business class, cyan for the pool, lime for the gym hall, red for the lounge), drawn open. Each archway is built of
Cabin Arch pieces, a round white arch over the curtain's rail on white jambs, and stays a head's height clear right
across; at the largest scales it is wider. Set into the wall right over each archway's crown, on the side facing the
doors (the pool's side of the wall between it and business class), a Bulkhead Sign shows what lies through it: a seat,
a drop of water, a basketball, a cup. Business class has such a wall at its front as well, across the forward doors'
aisle (where it has no room for one, a curtain on its own crosses the aisle there instead). The gym hall's front wall
has a lavatory either side of its archway, each with a toilet, a basin under its mirror, and a wooden door under a
WC sign.
The hoops are mounted on the gym hall's end walls, out of the way of anyone coming through the archways, and a lime
dividing curtain (Trennvorhang) hangs right across the middle of the court, to split the hall into two courts. Over
every passenger door glows an exit sign, lit a little so the doors can be found with the cabin lights set for the
night.

Every WC aboard has a WC sign over its door, the WC behind the flight deck in both blueprints too, and in the
furnished airliner overhead WC signs point the way to the nearest, high up where an airliner has them, each green
while one of the WCs it points to is free and red while all of them are taken: on a bulkhead facing into its room
wherever the nearest WC lies through the archway (business class's front wall, straight on to the WC behind the
flight deck; the pool's back wall and the lounge's front wall, straight on to the lavatories), over the archway or
beside it; on the back of each lavatory, facing into the gym hall; beside the top of the steps up to the flight deck,
facing down them into the cabin; and on the upper deck over its rows and beside the top of each staircase (see The
upper deck). Each is out of the doors' aisles and above everyone's head.

### The den

Behind the lounge's bar a wall of bookshelves closes off the tail. One of its books glows: that bookshelf is a secret
door, and while someone is near, sparks drift into the book with a faint shimmer. Use it and it swings open with a
deep creak; it swings shut by itself after ten seconds, once nobody stands in the doorway, or when used again. Behind
it the tail is a den for children: a landing with two bean bags, a ball pit with a disco ball (see below) turning over
it, hung from the crown on a cable and throwing its coloured spots round the den, then the tail cone's steps climbing
to the top of the tail, each carpeted in the next colour of the rainbow, with bunks (see below, their reading lights on)
along the walls where a step is wide enough and bean bags where it is not, all in soft colours, fairy lights along
both walls that dim with the cabin lights, and at the very top a treasure chest with cookies, a golden apple, a signal
flare, a basketball and star rockets. The ball pit swallows whatever walks or falls in with a plop and a spray of
balls: you wade through it a little slower, a fall into it never hurts, nobody under the balls is smothered, and a
jump always gets you out. The secret door, the ball pit and the fairy lights can be crafted.

A Bunk is a padded bed for a den, two blocks long: a quilted mattress on a white frame under a blanket, a pillow at
its head under a little reading light on the headboard, and a padded rail along its open side at the head end, the
foot end left open to climb in. Put it down against a wall: it faces you, its head where you put it and its foot to
your right. Use it with an empty hand to switch the reading light on or off; use a dye on it to dye its blanket and
the rail's padding, and taken down it keeps its colour. It is for lying about on, not for sleeping in. It can be
crafted.

A Bean Bag is a big squashy bag of fabric panels to flop into, slumped into a seat with its sides bunched up round it
and its back heaped up behind. Use it to sit down in it: like the cabin seats it is one of Create's seats underneath,
so villager passengers take it for one too. A fall into it never hurts, only flumps. Put down, it faces you, its back
away from you; use a dye on it to dye it, orange until dyed, and taken down it keeps its colour. It can be crafted.

A Disco Ball is a ball faceted all over with little mirror tiles, hanging on a short rod: put it up under a ceiling or
at the end of a light cable. It is on when put up: it turns slowly, its tiles glinting as they catch the light, and
it casts twelve spots of coloured light, red, green, blue and every colour between, each changing colour as it goes,
that sweep round the room's walls, floor and ceiling as the ball turns; it lights the room round it too. Use it to
switch it off: it slows to a stop with a falling chime and goes dark; use it again and it spins up with a rising one.
The spots are drawn through Veil, which comes with Sable (`discoLights` in the client settings); without it, or with
Iris installed, the ball still turns, glints and lights the room. Nothing bumps into it. It can be crafted.

### The hold

In the furnished airliner the hold between the moon pool and the drop bay is stowed with luggage, piled up both sides
in bays, each behind a cargo net from the floor to the ceiling (see below) with a gap to step into between them. Cargo
floor runs along both edges of the walkway down the middle, its yellow tie-down rails along the nets, and ball mats lie
round the drop bay; the walkway stays clear from the pool to the drop bay and on to the hatch and the cargo door. Aft of
the bay, ULD containers (see below) wait along both sides of the walkway to be loaded through the cargo door, short of
the hatch's ladder and of the door. Along its forward half the belly under the walkway is a sky window of clear glass,
like the pool's: lie on it and look straight down at the ground, or the clouds, below. The suitcases, in four colours,
each hold a few things a traveller packs; among them are care packages packed with supplies, ready to load into the
drop bay. Three golden suitcases are hidden about the hold: they glint, glow faintly and chime softly when someone comes
near. Opening one counts toward Lost and Found in the First Flight advancements, with a fanfare the first time; find all
three to earn it. Suitcases can be crafted; golden ones can only be found.

A Cargo Net is the hold's webbing: charcoal straps in a diamond lattice, sewn together where they cross, hanging from a
steel track along its top and locked into the floor by steel fittings along its foot. Nets put up beside one another
join into one net, as glass panes do, and a net's end against a wall hooks onto it; stacked, they join into one tall
net with the track only along its top and the fittings only along its foot, and wherever a net ends, an edge strap
runs down it. Nothing gets through a net, but it is a mesh to reach through: pointing at one, you point at whatever is
behind it, unless at its steel track, its fittings or an edge strap; sneak, or hold a net, to take hold of the webbing
itself. Cargo nets can be crafted.

Cargo Floor is a hold's floor panel, laid on a floor like a carpet: dark anti-slip plating with a steel roller track
down its middle, and along one edge, the one you face as you put it down, a yellow tie-down rail, the slotted seat
track nets and straps lock into; the rollers run along the rail, and panels laid in a row run on into each other. A
Ball Mat is a panel of polished steel ball-transfer units, each in its socket, on which a load turns any way. Both can
be crafted.

A ULD Container is the aluminium container an airliner's baggage is loaded in, an LD3, two blocks wide, deep and tall:
ribbed sheet in an extruded frame, the lower half of one side slanting in to its base (the contour that fits it to a
hold's curved floor), a net door over its front with the bags packed behind it, and its id stencilled on the other
side, AKE and the airline's code over one of a few serial numbers. Put down, its door faces you and it reaches up, away
from you and to your right, the contour on your right; it needs the room for all of it, and comes down whole. It can
be crafted from iron round a cargo net.

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

Beside each toilet is a Lavatory Basin (craftable from a cauldron): a vanity in the toilet's laminate with a white
counter, the basin set into it and a chrome tap arching over it from the back, a soap dispenser beside it, and over it
a mirror on the wall with a strip of warm light along its top. It is two blocks tall, put down facing you with its
back to the wall. Use it and the tap runs into the basin with a splash; hold a glass bottle under it and it comes away
full of water.

### Rubber ducks

A Rubber Duck is a little yellow duck with an orange beak, to put down anywhere, its beak toward you. Put into water
it floats, bobbing and rocking gently. Use it, or hit it, and it squeaks, with a wiggle and a few hearts. Yellow wool
under an orange dye makes two.

The furnished airliner hides five of them, a secret best left for children to find, so it works without reading: one
on the flight deck's glareshield, where pilots keep their mascots, one in the WC behind the flight deck by the basin,
one sitting on the balls of the den's ball pit, one on the luggage in the hold, and one bobbing on the moon pool's
water by the steps out, in plain sight, to show what to look for. (At the smallest scale the airliner has none of
these rooms, and no ducks.) Within a few blocks of one you have not found yet, it squeaks softly every few seconds
and wiggles, with a note over it. Use it, or hit it, to find it: it squeaks loudly, a row of dots on screen, one for
each duck the airliner hides, fills in yellow for those found so far, and the duck counts them out in squeaks, each a
little higher, with a heart each. Every player finds the ducks for themselves, and a duck falls quiet only for whoever
found it. Every airliner has a hunt of its own: the ducks found aboard one count toward that airliner only, so each
new airliner is hunted afresh. Each find also counts toward Duck Hunt in the First Flight advancements, which keeps
every duck found aboard any airliner; find all five to earn it. (An airliner built before each had a hunt of its own
counts its ducks by that advancement alone, so a duck found aboard any of those is found aboard them all.) A hidden
duck stays put when hit, even in creative mode; sneak to take one away, and it is gone until the airliner is built
again.

Whoever finds the last of an airliner's ducks sets off the surprise: with a great splash a giant rubber duck, three
blocks wide and tall, rises out of the airliner's moon pool, and once it is up it lets out a huge squeak, with a
fanfare and a burst of confetti and fireworks; the finder hears the squeak from anywhere aboard. It floats there,
bobbing gently, and it is solid: jump onto its back or its head and it bounces you straight back up with a deep
squeak, like the springboard (sneak to stand on it); use it, or hit it, and it squeaks. It is part of the airliner,
flies with it and stays until the airliner is built again: like a hidden duck it stays put when hit, and only comes
apart for a player who sneaks, breaking any part of it taking the whole duck away. A second player finding all of the
airliner's ducks makes it squeak again.

### Cabin seats

The cabin is seated in airliner seats, in two cuts. An Economy Seat is slim: a cushion on a pair of aluminium legs on
floor rails, the life vest's pouch under it, a slim armrest along either side (a row of them shares its armrests) and
a high back with a headrest under a white cover, its graphite shell carrying a tray table folded up over a seat pocket
for whoever sits behind. A Business Seat is roomier: a leather cushion on a pedestal with its leg rest folded down at
the front, in a white shell that wraps its sides up to wide walnut armrest consoles and rises behind the back over a
winged headrest. Each is two blocks tall, put down facing the way you face, and using either half sits you down in it:
underneath it is one of Create's seats, so the seatbelts, the outside cameras and villager passengers treat it as one.
Its upholstery takes any dye's colour: use a dye on it to recolour it, and taken up it keeps its colour. A passenger
screen put against the back of a seat's upper half sits on the seat's back. Both can be crafted. In the furnished
airliner business class and the lounge's armchairs are business seats (grey and red), and the upper deck's rows, the
gym hall's seats between its windows and the crew room's seats economy seats (light blue, blue and light grey).

A Seat Console is a business seat's side console, as high as its armrests: a moulded white unit with a stowage cubby
in its front, a walnut cocktail tray on top and, at its back by the sitter's shoulder, a reading lamp on a slim chrome
stalk, its shade glowing warm whatever the light around. Put down, it faces the way you face, as the seats beside it
do. A Lounge Table is a low round walnut table on a chrome pedestal. Both can be crafted. In the furnished airliner
business class's seats have a console either side, and the lounge's armchairs a round table in front of them.

### Cabin carpet

Cabin Carpet is an airliner's woven carpet, a pixel thick like vanilla's: small diamonds on a fine twill, in the
cabin's blue until dyed (use a dye on it; taken up, it keeps its colour). An Aisle Carpet is the aisle's runner, woven
in ribs along the aisle, with a strip of floor path lights along one edge (the edge you face as you put it down): small
warm white lights in an aluminium channel that glow whatever the light around, but light nothing. Laid either side of
an aisle with the lights toward the seats, the two strips lead the way out, as an airliner's emergency lighting does.
Both can be crafted. In the furnished airliner business class is carpeted blue and the upper deck grey, each with the
runner along its aisle, the lounge and the duty-free kiosk red, and the den's steps in the colours of the rainbow.

### Planters

A Planter is a slim white planter box for the cabin, put down with its back to the wall you face. It comes planted
with low clipped greenery; use a small flower on it to plant it with greenery in flower, bamboo for a stand of bamboo
canes up to the top of the block, or a fern or leaves for greenery again, the plant used up (unless in creative mode).
Taken up, it keeps what grows in it. It can be crafted.

### Passenger screens

A Passenger Screen (craftable) is a slim seat-back screen for the cabin. Put against the back of a seat or a wall it
faces out from it; put anywhere else it faces you. Once the airliner is assembled it shows the flight: whether it is
on the ground, climbing, cruising or descending; its altitude above sea level and its height above the ground; its
speed over the ground and how fast it climbs or sinks; the way it is heading; and how far it has flown and for how
long since it took off, kept up after landing until the next take-off. A block counts as a metre. While the airliner
stands as blocks the screen shows a welcome page.

Every ten seconds the page gives way to the route map for as long: the land below as seen from the airliner, in its
own colours as on a vanilla map, slopes shaded so hills and valleys stand out and water darker the deeper it is; the
villages on it as little houses with red roofs; the route flown since take-off as a cyan line from a green dot; and
the airliner as a gold arrow pointing the way it goes. North is up. The map zooms out as the route grows, so the
whole flight fits, and the land not seen yet is left dark under a faint grid. The land is taken in as the airliner
flies over it, as far as the game has it loaded round the player, and every player keeps their own map of each world
and dimension in the game's folder (`aerolarge/route_maps`), so it fills in a little more with every flight. The
villages come from the server, which knows where it generated each one, so a village shows whether or not its bell
still stands.

Use an assembled airliner's screen to look through its outside cameras, the way a security camera is watched: on
top of the fin looking forward over the whole airliner, over the right wing out at its engines, under the nose past
the nose gear down the runway, and in front of the nose, level with the pilots' eyes, looking ahead and down at
what the airliner flies toward. Use again for the next camera, and after the last you are back in the cabin;
sneak to go straight back. The view follows the airliner as it flies, while you stay where you are (seated, you
stay in your seat) and nothing you click reaches the world. The cameras are placed from the airliner's autopilot, so
an airliner without one has none.

### Villager passengers

On by default (turn off Villager Passengers, `villagerPassengers`, in the server settings, see Settings). A Check-in
Desk at the terminal then prints a Boarding Pass for whoever uses it. Hand the pass to a villager and it makes for
the nearest free seat inside an airliner (a cabin seat, or one of Create's) within about 24 blocks (never a pilot's
seat), up the airstairs or through the jet bridge and the doors (which open for it, see Doors), and sits down;
unseated aboard, as it is when the airliner is assembled around it, it sits straight back down. Fly it at least 200
blocks: once the airliner stands still with the seatbelt signs off it gets up and pays its fare, an emerald for every
200 blocks flown, up to 32, to whoever handed it the pass (or, with them gone from that world, dropped by its seat).
A villager that finds no seat within three minutes gives the pass back. The fares are spent at a duty-free counter.

### Duty free

A Duty-Free Counter (craftable) is a shop counter with a glass showcase on top. Use it and its shop opens with the
ding of a door bell: the trading screen, each of its goods shown by its picture and its price in emeralds, so it needs
no reading. An emerald buys four paper planes, two airline coffees, four bags of pretzels, two ice creams (a chocolate and a
strawberry scoop and a cherry on a waffle cone, quick to eat and cooling: 20 seconds of Fire Resistance), a chicken or
a pasta meal, or two signal flares; a basketball costs 2, a parachute 4, a pilot cap 5, an empty care package 6, a model airliner 8
and a pilot helmet 10. Each sale rings the till. It never runs out and its prices never change, and nobody works
behind it, so it stands anywhere: at the airport, or aboard, where there is nobody to lose in flight. The furnished
airliner has a duty-free kiosk at the front of the lounge, to port across from the bar: a row of counters facing the
aisle under a lime and white striped awning hung from the ceiling on steel rods, behind them a duty-free display with
its lit showcases between the windows, and far enough back from the aft doors that a shopper is out of reach of the air
rushing out of one opened in flight. (At the smallest scales the lounge is too narrow for it.)

A Duty-Free Display is a cabinet in the counters' blue with gold-handled drawers; put another on top of it and that
one is a lit glass showcase of perfumes under gold caps and boxes of chocolates tied with gold ribbon (a showcase left
with nothing under it comes down). An Awning is a lime and white striped canopy on a slim steel frame, put up facing
you: canopies side by side and one behind the other make one awning, with a scalloped valance along its front edge.
Put up against the underside of a block, or under another rod, it is a steel rod to hang the awning by. Both can be
crafted.

### Basketball

The gym hall's hoops are real: take a ball from the rack in the corner by either hoop and use it to throw it the way you
look, sneaking for a softer throw. It bounces off the floor and walls and rolls to a stop; walk over it or use it to
pick it up, hit it to knock it on. A ball that drops through a hoop scores two points, or three thrown from beyond
the three-point line, for the side the hoop counts for: HOME at the front hoop, GUEST at the back (sneak and use a
hoop to swap it). The scoreboard over each backboard keeps the score; use it to sound the buzzer and start again.
Aboard an assembled airliner the ball flies, bounces and rolls with the cabin, in flight as on the ground, the way
Create's seats ride along: inside the hull it moves among the airliner's own blocks. Out through an open door (or
pulled out as the cabin decompresses) it leaves the airliner, in flight at the airliner's speed, and falls away.
Hoops, balls, scoreboards and racks can all be crafted to build a court anywhere, and so can its floor: Gym Floor is
a sprung sports floor of narrow lacquered maple strips, laid the way you face, over white panelling underneath (the
hold under the gym hall sees a ceiling, not floorboards). Use white dye on a board to paint a court line on it, as
the gym hall's court is lined.

Over the court the gym hall's pendant lights hang from metal roof trusses (Create's girders). From the trusses either
side of the middle hang gymnastic rings on chains, over the side of the court by the wall bars: use them to swing up
off the floor. The teams' banners hang between the upper windows, orange at the HOME end, blue at the GUEST end.
Gymnastic Rings can be crafted too.

Wall Bars (Sprossenwand) can be crafted for a gym of your own: pale ash uprights against the wall with four round
rungs between them to every block, climbed as a ladder is. Bars side by side make one wall of them, and stacked up,
the top rung of the topmost stands out from the wall on brackets, to hang from. So can the rest of a gym's fittings.
A Gym Mat is a thick blue padded mat a quarter of a block high, piped in grey round its edges; mats side by side make
one big mat, piped only round its outside, and a fall onto one hurts a fifth as much. A Climbing Rope is a braided
hemp rope hung from a ceiling on a steel eye, ending in a knot: jump up into it and keep climbing, as up a ladder;
nothing bumps into it, and it comes down if what it hangs from is taken away. A Glass Backboard is a pane of
toughened glass in an aluminium frame, put up facing you; pieces side by side and one over another make one board,
framed round its outside, and a board three wide and two high shows the red shooter's square over its middle, where
a hoop put up against it goes. A Gym Bench (Turnbank) is a low bench of lacquered ash, the balance beam along its
underside, on a steel frame of legs, one block tall so it stands under a window without covering it: use it to sit
down on it facing the way it was put down. Like the cabin seats it is one of Create's seats underneath, so the
seatbelts and villager passengers treat it as a seat. Benches side by side make one long bench, with legs only at its
ends.

For hide and seek, press the big yellow button with the eye on it beside either hoop (or sneak and use a
scoreboard): every scoreboard in the hall counts down from ten in huge numbers, amber, then red for the last three,
with a beep each second, and at nought the buzzer goes and the nought turns green before the score comes back. The
button stays down, its eye shut and glowing, until then; press it again to start over. Hide and Seek Buttons can be
crafted.

### The moon pool

The furnished airliner's moon pool is tiled as a real pool is: its basin lined in a blue glass mosaic with a lane line
down its end walls at the middle of each lane, a white coping round its edge, non-slip tiles round that on the floor
and on the diving tower, and tiled steps out at its back, their lower tread under the water, with a chrome grab rail
at either end. A pool of your own can be tiled the same way, with blocks that can all be crafted. A Pool Deck Tile is a
sand-coloured non-slip tile with a grid of raised dots, for the floor round a pool, its underside a white ceiling.
Pool Mosaic lines the basin in small glass tiles in the pool's blues; use blue dye on one to lay a lane line into it,
a dark blue stripe down its middle. Pool Coping is the white stone kerb along the pool's edge, its front edge rounded
over the water above a band of dark blue tiles at the waterline: put down, its round faces the water you are looking
at; sneaking, it is a corner piece, plain all over. Pool Steps are stairs tiled like the deck, a dark blue edge along
the front of each tread, that turn corners and fill with water as any stairs do; steps side by side make one flight,
with a chrome grab rail curving up over each end of it. A Pool Ladder is climbed as any ladder is: two chrome rails
standing off the wall with a ribbed steel tread between them every half block, the rails of its top piece curling
over into grab handles down onto the floor beyond its top, as a pool ladder's do over the coping.

### The high dive

The springboard sticking out from the moon pool's diving tower, a slim blue-gripped board on a coiled spring, throws
whoever lands or stands on it straight back up, with a boing and a puff of cloud, about two and a half blocks every
time, never high enough to hurt coming down, and landing on it never hurts; sneak to stand on it without bouncing.
The tower is climbed by a chrome pool ladder, and its platform is railed with the cabin's glass rails along its sides
and back; off the springboard or the front of the platform there is only water below, and the pool's water takes the
fall. A dive (off the springboard, or any fall of three blocks or
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
under them for the ear cups, or buy it at a duty-free counter for 10 emeralds. Every airliner with pilots' seats is
built with two helmets and two caps in the flight crew's locker on its flight deck (see Flight deck).

### Skydiving

By the aft passenger doors and the cargo door are a red and green jump light in the wall and a rack of parachutes,
packed parachutes behind its front under a red and white parachute sign, the sign big on its sides too, so it is
found without reading. Use a rack to put a parachute on (or take one into your inventory if your chest slot is taken), then jump while
falling to open it; it opens by itself if you are still falling free close to the ground. Under the canopy you sink
gently, drift the way you walk, take no fall damage, and it packs itself away when you land. The jump lights' tile on
the systems screen cycles every jump light in the airliner through off, red (stand by) and green (go, with a bell).

Open one of the hull's doors in flight (20 blocks or more above the ground, at 20 blocks a second or more; the
cockpit door, the WC's and the crew room's are not among them) and the cabin decompresses with a bang: the air roars
out through the doorway in a stream of fog, everyone aboard is warned in chat, the door alarm's siren wails through the
whole cabin and every cabin light, LED strip and pendant light turns red, a wave of red running down the cabin from
the flight deck, until the door is shut again (then the lights go back to how the dimmer had them; with Veil's lights
drawn, the red lights cast red light round the cabin too), and whoever stands within a few blocks of the door (3 per unit of `scale`), and anything loose (mobs, villagers out of their seats, dropped items, a
basketball, a trolley), is dragged along the floor to it, the harder the closer, and flung out through it into the
sky, wherever the air has a clear way to the door (not up through the floor from the hold, or round a wall). Anyone
seated is belted in and stays put. With a parachute on, being sucked out is just the start of a skydive; close the
door from a few steps back to stop it, most easily with the Door Button on the wall beside it (see Doors), or wait:
ten seconds after it opened, the door shuts by itself (waiting, as ever, for anyone in its way).
Turn it off with `decompression` in the server config.

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
down the ladder under it, so the bay can be reloaded in flight. From the hold, use the bay, holding anything or
nothing, to open it: nine slots, like a dispenser's, between two pictures of what the bay does (its doors open under
the belly with a care package dropping out, and the package floating down under its parachute). Put anything in
them, a stack to a slot; shift-click moves a stack in or out. Broken, the bay spills what it holds. The drop release's
tile on the systems screen opens the bay doors on the first touch and on each touch after that drops one item from
the first slot that holds any; sneak and touch it to close them. A load leaves at the
airliner's speed, from under its wheels once it is assembled so it falls clear of the whole airliner: a care package
falls and floats down as above, TNT leaves lit, and anything else falls as itself,
to be picked up where it lands. With Ultrasplosive installed its nuke (the regular Fat Man, not Sundial) leaves the
bay as Ultrasplosive's flying nuke and goes off where it hits. Its blast reaches 160 blocks, so the release only drops
one from 200 blocks or more above the ground.

### A sturdy frame

The airliner's frame and controls (hull, wings, engines, gear, windscreen, flight deck and the Create and Propulsion
blocks it is built with) cannot be mined, broken in creative mode or blown up; the cabin floor and everything built
on it can. Sneaking with a wrench (Create's will do), or sneaking in creative mode, still breaks them: to take an
airliner apart on purpose, or to take back a panel put in the wrong spot.

Only the blocks the blueprint laid are held so, and only while they are part of an airliner: inside the Super Glue the
blueprint glues it with, or among its blocks once it is assembled. The same blocks anywhere else, as the runway's
lights and the control tower's glass, lights, panels and seats are, break as any other block does. So does one placed
by hand, a nav light or a panel added to a finished airliner say, glued in or flying; a Create deployer places by hand
too, a schematicannon does not. An airliner taken apart again forgets which of its blocks were placed by hand.

### Doors

The six passenger doors and the cargo door are plug doors, one panel over each doorway as on the real airliner: white
skin flush with the hull, its seam a stepped gap right round it, with one window in the middle of its top and a flush
handle by the edge away from its hinge, two blocks wide and three tall (two at scale 1; the cargo door is two tall).
Inside, a bulky padded lining in the cabin's off-white bulges out of the panel almost half a block, a big rounded
cushion that curves up from its edge to its crown, with the window set deep into it through a rounded reveal and the
handle on it. The window is glazed right through, faintly tinted: look out of it from the cabin, or in from outside;
it lets no daylight in. Use any part of a door, from inside or out, or give any part a redstone signal, and it opens
with a hiss: the panel lifts a little and moves out of its frame, then swings forward on its arm and parks flat
against the hull ahead of the doorway, standing just clear of it (further out where the hull there bulges out past
the doorway), its outside still facing out. The cargo door swings up and out on its top edge instead, first moving
out of its doorway where the hull above it stands proud of it. Open, the doorway is clear, and the panel outside is
in nobody's way; use the empty doorway to shut it again. The panel comes back the same way and latches home with a
clunk, but not on anyone: while someone stands where it goes back in, it waits, with a chime. The airstairs and a
docked jet bridge open the door they serve, and shut it again as they fold, and so does an evacuation slide (see
Evacuation slides). Villagers find their way through the doors as through wooden ones: while the airliner stands
still, a door opens for a villager walking up to it and shuts again behind it; in flight no villager opens one. The
doors of the WC and the crew room are ordinary doors; the cockpit door is its own (see Flight deck).

Inside, beside every one of these doors, a Door Button on the cabin wall works it too: an illuminated push-button in
a dark steel bezel like the Airstair Button (see Airstairs), its square cap glowing sky blue whatever the light, with a
door on it, so it needs no reading. Press it and the door opens if it is shut and shuts if it is open, just as using
the door does, in flight as on the ground: far easier to hit than the doorway while the cabin decompresses and drags
everyone near toward it (see Skydiving). It is in the jamb just ahead of the door, a block above the sill; by the aft
doors, where the jump light and the slide control take the jambs at that height, it is level with the sill, and by
the cargo door in the hold it is just aft of the door, a block above its sill. It gives off no redstone signal: it works
the door itself. An airliner built before the buttons has none; a Door Button can be crafted from an Airstair Button
and light blue dye, and put up within three blocks of a plug door it works that door.

### Airstairs

The forward port door has integral airstairs, like the ones built into regional and business jets: a flight of open
treads running straight out from the door down to the ground, each a dark non-slip plate with a yellow nosing, between
slim aluminium stringers, with a handrail along either side on thin posts, the top tread level with the sill. Stowed,
they lie folded under the cabin floor in a bay behind a hatch in the hull below the door, and nothing of them shows.
The Airstair Controller set into the hull just aft of the door brings them out: the door opens, the hatch drops, the
folded flight slides out of its bay, rises to the sill and tips down to its slope, its lower sections run out down to
the ground and the handrails rise, to the hum of their hydraulics and the clunk of their latches, in under five
seconds. They reach as far down as the ground lets them, so on uneven ground, or over something in the way, they end
where it meets them, and they are walked up and down like any stairs; the airliner never comes to rest on them, so
let down onto the ground they do not tilt it. An assembled airliner rides higher on its
suspension than the stairs' steps reach, so there they also lay an unseen plate at the foot of the flight, half way
between the ground and the bottom tread, and you walk up from the ground without a jump. Worked again, they go back the same way once
nobody is on them (a chime says they are waiting), and the door shuts behind them. They only come out while the
airliner stands still, and fold away by themselves as it starts to move. Work them with the lit Airstair Button (see
below) on the controller inside or outside by the top of the stairs, by using the panel itself, with any redstone
signal, or from the ground with the yellow call button on the front of the nose gear. An airliner is built with them
down. In one built before them, the old stairs of blocks fold away the old way the first time they are worked, and
from then on the new ones come out, folding into the hold behind the hull below the door.

An Airstair Button is an illuminated push-button in a dark steel bezel, its square cap glowing amber whatever the
light, with the controller's staircase on it; pressed, it sinks in and glows brighter. It is a stone button in all it
does: it stays down a second and powers what it is on, so put on an airstair controller it works the stairs. It can be
crafted from a call button and glowstone dust.

### Evacuation slides

Every passenger door but the forward port door, which has the airstairs, has an evacuation slide packed in it, as an
airliner's does; the cargo door in the hold has none. Its control is set into the cabin wall beside the door, a block
above the sill, in the jamb just aft of it (just ahead of it where that has no room): a red panel in a yellow frame
showing someone sliding down a slide, so it needs no reading, with a red handle in a slot down its side. It only works
from inside the cabin; outside it is the hull's skin. Pull the handle and the door opens; once it is open the pack drops
out onto the sill, and the slide unrolls down to the ground and inflates with the roar of its gas, in two seconds: a big
yellow slide with a raised rail along each side over a grey beam, grey underneath, a silver reflective stripe along its
rails, held to the sill by its girt bar and as wide as the doorway at its head, widening out past the parked door,
rounding out onto the ground and running a little way along it to its curled toe. Step out of the doorway onto it and it
carries you down, seated, quickly and smoothly, and sets you down on the ground just past its toe with no fall to hurt
you; sneaking does not get you off halfway, and nobody walks up it: try, and down you go. Villagers go down it the same
way, and find their way out of the airliner down it.

It comes down at 35 degrees, or steeper (up to 45) or shallower (down to 25) where something is in its way, as far down
as the ground lies below the door, measured from the world as it comes out, aboard an assembled airliner too. It only
comes out while the airliner stands still, with the ground within its reach (eight blocks down for each unit of
`scale`) and nothing in its way: in flight, over a drop, or onto the airliner itself, it refuses with a bass note and
says why. Pull the handle again and it deflates and rolls itself back up into the doorway once nobody is on it (a chime
says it is waiting), and the door shuts behind it, as it does behind the airstairs, rather than leave an open door over
a long drop. It goes the same way by itself as soon as the airliner starts to move, and when the door is shut on it. An
airliner is built with its slides packed; one built before them has none. An Evacuation Slide Control can be crafted and
put up beside a plug door of your own, which it then serves.

### Jet bridge

For an airfield with a terminal, set a Jet Bridge into the terminal floor at the mouth of the bridge, its arrow
pointing out toward where the airliner parks, level with the cabin floor and in line with a door. Using it (or a
redstone signal) sends out a glazed tunnel, three blocks wide and high inside, slice by slice to the airliner, its
black canopy closing against the curve of the hull around the door; it docks at a door in the world or aboard an
assembled airliner standing still, up to 32 blocks out, and opens the door once it is there. If no door lines up it
says which way to move the bridge. Use it again to fold it back once everyone is off it; it also pulls back on its own
when the airliner starts to move, and shuts the door as it goes. Breaking it takes the whole tunnel with it.

### Control tower and transponders

The **Control Tower Blueprint** (in the creative tab with the airliners') puts up an airfield's control tower, after the
towers at real airports: used on the ground, the tower goes up ahead of the block you click, the canopy over its door
two blocks past it and the door toward you, and it refuses while anything is in the way unless you sneak. A slender
octagonal shaft of white concrete, light grey pilasters at its corners and a glazed strip up two of its faces, rises
forty blocks to a wider equipment floor with a ribbon of windows round it, and on that sits the octagonal glass cab:
clear windows over a grey knee wall, the upper band leaning a block further out than the lower one, as real cabs lean
theirs out against reflections, dark mullions at the corners, a walkway with a railing round its foot and an
overhanging roof. On the roof stand a white radar dome, antennas and a mast with a flashing red beacon.

Its entrance is a glazed front four blocks wide set back under a canopy: a pair of Create's framed glass sliding doors
between fixed glass, a glass transom over them, and over the left door a motion sensor that slides them open for anyone
coming or going. Inside, switchback stairs climb round the shaft's glass lift
core, lit by lamps in the walls, to the equipment floor and on into the cab. The lift is Create's elevator, assembled
and powered when the tower is built (a creative motor under the cab's floor turns it): touch a floor on the lift panel
by its door, at the lobby (G) or the equipment floor (1), to call the cabin, whose arrival opens the iron door; aboard,
touch a floor on the cabin's own lift panel, or scroll on its controls under it and click. In the cab a ring of dark consoles runs round under the windows, with the tower's radio (see
Announcements and the radio), its radar screen and its transponder at the front, two pilot seats at them looking out,
and a second radar screen at the side. The ground under the shaft is filled in, so it stands on uneven ground too.

A **Transponder** shows wherever it is on every autopilot's map (see Autopilot) and on the tower's radar, by a picture
and a colour of its own, as a beacon does in Subnautica. Use it to pick them on its panel: ten pictures (a control
tower, an airliner, home, a star, a flag, a heart, treasure, a forest, a harbour and a mine) and twelve colours, each a
button showing what it picks, with the marker as the maps will show it on the left; it shows its picture in its colour
on its own little screen, too. One standing on the ground shows on the maps from anywhere, however far off; one aboard
an assembled airliner flies with it, and shows where the airliner is while it is loaded, which it is while anyone is
near it. The control tower's transponder shows the tower as an orange tower, and every airliner built from 0.1.0-alpha.48
on has one on the flight deck (on newer airliners on the centre console behind the seats), showing it as a white airliner (an
older airliner can be given one: a Transponder is crafted from iron, a glass pane, redstone and a compass, and goes on
a console or a desk anywhere). On its own autopilot's map an airliner's transponder is left out, the airliner being its gold arrow there.

A **Motion Sensor** (craftable from quartz, an observer and redstone) is the sensor over an automatic door: a white
housing with a dark lens and a little lamp that lights red while it sees someone. Put it in the wall over a door, its lens
facing out: while a player or a villager is within four blocks of the wall, in front of it or behind it, it gives off a
full redstone signal all round, and for a second after they have gone, so a door under it opens for them and shuts
behind them. Create's sliding doors open as a pair when either is powered, so one sensor works a double door.

A **Lift Panel** (craftable from iron, a black glass pane and redstone) is the digital panel by a lift's door, for one of
Create's elevators: amber and green light on black, readable in the dark. Across its top it shows the floor the cabin
is at in big amber figures, with its name under them and an arrow while the cabin goes up or down; under that a row for
each floor, the top one first, by the names given to its elevator contact (in Create's contact screen), the floor the
cabin was last sent to lit up in amber. Touch a floor's row to send the cabin there. Put one on the wall within three
blocks of the lift's column of contacts, at any landing; one glued into the cabin rides with it and works from inside.

The **Tower Radar** is a monitor on a desk stand whose screen sweeps round like a radar's: north up, range rings, and
every transponder within 2000 blocks a blip in its colour, brightest as the sweep passes it and fading until it comes
round again, an airliner's with a short line the way it flies. Use it to open the radar's map: the land round the tower,
rings every 250 blocks round it, every transponder's marker and every other player's face. Drag it, scroll to zoom,
point at a marker to see what it is, how far from the tower, and for an airliner how high and how fast it flies, or at
a face to see who it is and how far; click on either to centre the map on it.

While a radar screen is loaded, the server surveys the land round it, nobody needing to go there: it loads the chunks
round the tower one by one, nearest first, out to some thousand blocks, notes each one's land as the maps draw it and
lets it go again. Land nobody has been near is generated as it is loaded, so the survey goes slowly, about five chunks
a second, a few hundred blocks round the tower in the first minutes and all of it in under an hour, and pauses while
the server falls behind. Every tower in a dimension shares one survey: land any of them has surveyed is never surveyed
again, so towers near each other split the work, and a new tower near an old one knows its land at once. The survey is
kept with the world, in the dimension's `data` folder (`aerolarge_radar.dat`). Players within 96 blocks of a radar
screen are sent the land surveyed round it, a few chunks a tick, and it goes into their own maps: the radar's, the
autopilot's (see Autopilot) and the passenger screens' route map.

The wind over an airfield drifts slowly round the compass and rises and falls through the day, drawn afresh every
quarter of a day and eased into from the one before, the same on the server and every client; rain freshens it and a
thunderstorm makes a gale of it. A **Wind Sock** (craftable from iron bars and orange and white wool) flies with it: an
orange-and-white striped sock on a short steel pole that points the way the wind blows, hangs limp in a calm, stands
half out in rain and straight out in a storm, fluttering as it goes. The control tower flies one from a short mast at the
middle of its roof's west rim.

A **Weather Screen** (craftable from blue glass panes, a clock, iron and redstone) is the airfield's ATIS, on the radar
screen's monitor: "INFORMATION" and the report's letter, which steps on from A to Z and round again each time the sky
changes, the time of day as a 24-hour clock, a picture of the sky (the sun, or the moon at night; rain, snow where it is
cold, or a storm) with its word, the wind on a compass dial with its bearing and knots, or calm, and how far one sees:
ten kilometres when it is clear, less in rain, snow and storms. The control tower has one on its east console, facing
the second radar screen across the cab.

A **Flight Strip Board** (craftable from black glass panes, paper and a compass) is a wide screen three blocks across,
put up like the Tail Camera Display (against a wall it stands upright, against a ceiling it hangs tilted), with a flight
strip for every transponder within the radar's 2000 blocks: a bar and its picture in its colour, a callsign that is the
same for it every time (AER for an airliner, TWR for a tower, HOME, MINE and so on, and a number), its height, how far
it is from the board, its heading, and what it is doing: parked, taxiing, climbing, cruising, descending, or inbound
when it heads for the tower close by or comes down toward it; a transponder on the ground is a fix. The airliners come
first, then the nearest; when there are more than it holds, it pages through them every five seconds. The control
tower's hangs tilted from the cab's ceiling over the front consoles, looking down at the seats; its own transponder is
left off it. Round the rest of the cab, instrument panels stand on the corners of the consoles' ring, their screens
glowing at night, each in front of a mullion so it hides none of the view.

### Runways

The **Runway Blueprint** (in the creative tab with the airliners') lays down a runway for the airliner: used on the
ground, the runway starts two blocks past the block you click, centred on it, and runs away the way you face. It is
sized for the airliner at the scale it is built at: as wide as its wingspan with a shoulder either side, and twice as
long as it needs to take off on the autopilot (its roll up to `rotateSpeed`, the moment from there to lifting off and
its own length): at the default scale 780 blocks long and 179 wide. It takes the place of whatever is in the layer it is
laid in, clears everything above it as high as the airliner's fin and a few blocks more, and fills in holes under it.
It is painted as an airport's runway is: grey asphalt between lighter shoulders, white edge lines and a dashed centre
line with lights set into it, and at either end the piano keys, the runway's number (its heading in tens of degrees,
09, 18, 27 or 36, the far end the reciprocal) and the aiming point's bars, all sized to the runway. White edge lights run
down both sides, green lights across its start and red across its far end. It goes down a few chunks a tick, so even
the land it crosses that nobody has seen yet is generated without the server stalling, and it says when it is done.
To line an airliner up on it, use the airliner's blueprint at the runway's far end, looking back down it. A runway laid
down is remembered with the world, for the autopilot to land on (see Autopilot).

### Flying it

The finished airliner is covered in Create's Super Glue, in overlapping stretches the Physics Assembler can find from
anywhere in it; that is what it takes as the structure, so everything you build inside it flies along. Fold the
airstairs away and pull the thrust levers to idle, then assemble it. It rolls on Offroad wheel mounts fitted with
large tires, their suspension at its stiffest (each a Sable actor that finds the ground from wherever its block stands,
which is what lets the retractable gear take them away and put them back in flight); the main gear stands just behind its centre of mass, worked out from
Sable's block masses, so it rests on its nose gear. The wings and tail are built around a layer of Create Propulsion
wing blocks, and every airframe block is in `#sable:super_light`. Which blocks are used is set in the server config
(`wingBlock`, `engineBlock`, `assemblerBlock`, `wheelMountBlock`, `tireItem`), and so is how hard the creative
thrusters push (`thrustSetting`, on their own 1 to 99 scale: 99, their full rating, by default) and what the engines
show behind them (`engineFlames`): by default `LIGHTWEIGHT_FLAME`, the flame each player's game draws itself at next to
no cost, seen with Fancy or Fabulous graphics; `PROPULSION_PLUME`, Create Propulsion's own plasma plume, built into the
engines' thrusters when the blueprint is used, which looks grand but costs every client a good share of each frame in
flight and every player near a stream of particles from the server; or `NONE`, as a real airliner shows nothing but its
engines' glow and contrails. Only the airliner's own thrusters are set: Create Propulsion's settings and thrusters
built anywhere else are left alone. The control jets never leave a plume. An airliner built before this setting keeps
the plumes it was built with, so it may show both until it is built again.

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
taken from a coffee machine (Barista), and aboard as the autopilot lands on a runway laid down from its blueprint and
stops there (On the Numbers).

### Settings

The settings can be changed in game under Mods → Aerolarge → Config. The client settings (`cameraShake`, `pilotHud`,
`pilotHudScale`, the tail camera display's `tailCameraFeed`, `tailCameraFps`, `tailCameraRenderDistance` and
`tailCameraResolution`, the cabin's blue `nightLights`, the disco ball's coloured `discoLights`, and `cloudlessCabin`)
are there from the title screen. The server settings open only while a world is loaded, from the pause menu's Mods
button while playing in single player; they are kept in `config/aerolarge-server.toml`, on a dedicated server in its
own.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew runClient -PquickPlayWorld=<save>` loads straight into
`run/saves/<save>`; drop Create, Sable, Create Aeronautics and Create Propulsion into `run/mods` to run with them.

The geometry lives in `AirframeShape` (real A380 measurements in metres), is cut into blocks by `AirframeLayout`,
with the upper deck laid round the rooms under it by `UpperDeck` and its staircases, step by step and landing by
landing, by `DeckStairs`, and fitted out by `CabinFitOut`, `TailDen` and
`HoldFitOut`, the WC signs put up by `WcSigns`, the overhead ones once every room stands (each WC sign's block entity
looks into its WC, in the airliner's frame through `SableFlight` once it is assembled); the skin follows the curve in
`ShellGeometry`'s shapes, which `ShellModel` bakes, and is lined inside by `HullLining`, face by face, as `Lining` says which faces and how
each looks, and `Windscreen` says where the glass and its posts go; `ScreenArc` lays out the flight deck's curved row
of screens (`CockpitDisplayBlock`, whose block entity keeps where each panel stands and what it is linked to, and
`CockpitKeys` where its touch keys are), whose keys use the switches standing out of sight in the avionics bay as a
player would, and `CockpitDisplayRenderer` and `CockpitKeysArt` draw it, with the pointer that follows the look, as
`AutopilotRenderer` draws the control head's display; `AutopilotBlockEntity`
flies (not sending the clients the control jets' relays, which nothing there reads, nor the wing blocks
`ControlSurfaces` turns over), assembling its airliner when engaged on one not yet assembled through
`PhysicsAssembler`, which works the assembler as a Create deployer does, reading the airliner from Sable
through `SableFlight`, which also keeps the cabin's trolleys, basketballs and
paper planes among an assembled airliner's blocks while they are inside its hull (`#sable:retain_in_sub_level`) and
puts them out into the world at the airliner's speed once they leave it; the controls, slides, airstairs and other
blocks that work blocks round them keep where those are as offsets from themselves, which `Turning` turns with them
when the Physics Assembler sets the airliner down on another heading, and mends those an older version left unturned;
Sable draws an assembled airliner's block entities every frame wherever the camera looks, so the busiest renderers
leave out what the camera cannot see themselves, as `Sight` tells them where it stands: `PassengerScreenRenderer` draws
a screen only from in front of it, its text laid out once a tick for all of an airliner's screens and faded out as it
gets too small to read, as `FlightDisplayRenderer` and `AutopilotScreenRenderer` draw theirs only from in front too;
`ControlSurfaceRenderer` leaves out the panels wholly behind the camera, as `Sight.ahead` tells it;
`EngineRenderer` draws the intakes in flat sides over the nacelles' faces, from where each
engine's core was told they are, what lies inside the duct only from in front of them, and `ExhaustFlame` the flame
behind each running engine, a picture on strips turned to the camera; `PlugDoorRenderer` draws the
hull's doors shut, swinging and open, from their `PlugDoorBlockEntity`, as the relief `PlugDoorMesh` cuts the panel
and its lining into, only the quads of it that face the camera; `CockpitDoorRenderer` draws the cockpit door's panel
the same way, swinging about its hinge, from its `CockpitDoorBlockEntity`; `AirstairRenderer` draws the airstairs coming out and
going away, as `AirstairFlight` builds and moves them, from their `AirstairBlockEntity`; `SlideRenderer` draws an
evacuation slide inflating and deflating, as `EvacuationSlide` lays it out for the ground and builds it, from its
`SlideControlBlockEntity`, and `SlideRide` carries whoever slides down it; `LaserTurretRenderer` draws the laser path
clearer's turret out of its hatch and `LaserBolts` its bolts, which its `LaserTurretBlockEntity` fires at what
`LaserAim`'s rays find in the way; `GearBayRenderer` draws a landing gear leg
swinging up or down, and its doors, from the `GearBayBlockEntity` at its top, which takes the leg's blocks away going
up and puts them back coming down, worked by the `GearLeverBlockEntity`; `TailCameraFeed` draws the outside cameras'
pictures for the flight deck's camera displays through Veil's perspective renderer (Veil comes inside Sable's jar), and `NightLights` the cabin's blue light
at night and `DiscoLights` the spots of a disco ball turning as `DiscoBallRenderer` draws it through Veil's light
renderer, by way of `VeilLights`, which also keeps Veil's light framebuffer's depth in the window's format once a mod
gives the window a stencil: `TailCameraFeed` and `VeilLights` are the only places Veil is touched, besides the
mixin `mixin/AdvancedFboImplMixin`, which lets a Veil framebuffer borrowing another's depth see its stencil; the other
mixin, `mixin/GirderBlockMixin`, turns Create's metal girders a quarter turn without losing their beams, which
Create's own turn does (the gym hall's trusses of an airliner built facing east or west);
`CloudlessCabin` leaves the clouds out of the player's view while the camera is inside a hull in the cloud layer. Assets
are generated: `node tools/generate_textures.mjs` writes the textures
(ordinary PNGs that can also be painted by hand), and `node tools/generate_resources.mjs <vanilla-assets-dir>` the
blockstates, models, loot tables, tags, recipes and lang file. The door and the call button are vanilla's iron door
and stone button retextured, so their files come from vanilla's: pass the folder the client resources jar
(`build/moddev/artifacts/*-client-extra-*.jar`) is extracted to, or leave it out to keep them as they are.
`node tools/generate_sounds.mjs [<name> ...]` writes the sounds (only those named, if any) with FFmpeg, and with
`oggenc` from vorbis-tools where FFmpeg has no libvorbis (Homebrew's FFmpeg 9 has none): the engine loop is synthesised, and the flight deck's
voice (the callouts and the cabin announcements) is spoken by Windows' own speech synthesis ("Microsoft Zira"), so
that part runs on Windows only. The script then flattens the voice's intonation and tunes it to the semitones around
one note, and FFmpeg adds a faint vocoder buzz on that note, a metallic sweep and the echo of a small hard room; the
settings for all of it are together in `VOICE`, to tune it. Air traffic control is Windows' man's voice ("Microsoft
David"), through a VHF radio: FFmpeg squeezes it into the radio's band (about 300 to 3000 Hz), compresses, clips and
bit-crushes it a little, and the script lays it over the carrier's hiss with the odd crackle, between the squelch's
click as the button is pressed and the burst of noise as it is let go; other aircraft are the same voice pitched up or
down. Its settings and every line said are in `RADIO` and `ATC`.

## License

All Rights Reserved.
