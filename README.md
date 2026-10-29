# Aerolarge

A Minecraft mod for NeoForge 1.21.1: an A380-style double-deck airliner big enough to build in, that rolls on real
wheels and is flown from its own cockpit, and a gigantic Hindenburg-style zeppelin that floats on real hot air (see The
zeppelin). One blueprint lays the whole airframe down: a single long hollow hull with a main deck and an upper deck over
a cargo hold with a hatch down to it, six passenger doors and a cargo door, a flight deck behind a wrap-around
windscreen, swept wings with four engines, the tail, landing gear, folding airstairs and evacuation slides.

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
- **Zeppelin Blueprint**: a Hindenburg-sized zeppelin, 490 blocks long, with its control car, decks, gas cells, Sky
  Shaft and Sky Deck (see The zeppelin).
- **Furnished Zeppelin Blueprint**: the same zeppelin with its passenger decks furnished (see The zeppelin).
- **Half-Size Zeppelin Blueprints**, plain, furnished and festive: the zeppelin at half the size, 245 blocks long, its
  control car and decks as big as ever, a smaller ship drawn on the sheet (see The zeppelin).

Use a blueprint on the ground: the nose goes just past the block you click and the tail stretches away the way you face.
While you hold one, a see-through miniature of the airliner or the zeppelin floats over the block you aim at, turned the
way it would be built there, so you can see which way it will go before you click: nose toward you, tail away, along
whichever of north, east, south and west you face most. It refuses while anything is in the way and says where; sneak to
build through it. Operators can also run `/aerolarge build <pos> <north|east|south|west> [furnished]`.

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
it). Each engine is heard 160 blocks off, spooling up and down with its thrust, quieter aboard, and high up it leaves a
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
it. Sneak and use it from the flight deck (with an empty hand) to lock or unlock it, with the click of its bolt: a
small lamp over the handle on each face glows red while it is locked and green while it is not. Locked, it does not
open from the aisle, only rattling against its lock, though it still shuts from there; from the flight deck it opens
as ever. Behind the cockpit door the flight deck is a room of its own, raised so the pilots look straight out through
the windscreen: clear, lightly tinted glass cut to the nose's curve like the skin around it, one even tint wherever
you look through it, inside or out, and solid to walk against, one wide pane right across the front from the panel up
over the crown, running back into side windows past the pilots' shoulders, framed by posts round the sides only, so
from the seats you see the sky and the horizon ahead.

The two pilot seats are real airliner seats: a padded black leather back with a headrest and a harness, an armrest
either side, on a tall column between floor rails, raised so a pilot sits higher than on a Create seat and looks out
over the screens. On the flight deck each carries a sidestick at the front of its outboard armrest, the captain's at
the left hand and the first officer's at the right, as on an A380 (a seat put down by hand has none). Each is two
blocks tall, and using either half sits you down in it. It
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
built. While it is set on the ground and the autopilot is off, the engines stay at idle whatever the thrust levers
say, since the wheels' brakes cannot hold the airliner against any thrust: release it to taxi or take off by hand. The
dimmer steps every light in the cabin from bright
to dimmed (as for take-off and landing) to a blue night glow and back, the change running down the cabin front to
back; at night the lounge's starlit ceiling goes dark but for its stars, which glow whatever the light, and the WC,
the crew room and the lavatories keep their lights on as dimmed, as an airliner's lavatories do. Where Veil is
installed (it comes with Sable), the lights still on at night also cast their blue round the cabin near you, like an
airliner's mood lighting: the LED strips wash the walls and lockers, the cabin lights the aisle and the pendants the
floor under them, in flight as well as parked. That light is only drawn; what spawns where still goes by the dim night
glow. Turn it off with `nightLights` in the client config; with Iris installed it is never drawn, and the lights only
glow. The window shades' tile runs every cabin window's shade down, or up again, the same way, with the whirr of
their motors. A closed shade shuts out the daylight; anyone can open or close a window's own shade by using it.

In front of the console the screens curve round the seats like an airliner's glass cockpit, three quarters of a block
above the floor on a desk whose top slopes down toward the pilots, their top edges just under a seated pilot's eyes,
so the pilots look out over them through the windscreen, a dark glareshield hood running along their top edges. In the
middle, straight, is the systems screen, and either side of it two screens in the same slim dark casing, each turned
further toward the seats than the one inside it, so the row wraps round the pilots. Left to right, as on an A380: the
moving map, the captain's primary flight display, the systems screen, the first officer's primary flight display and
the outside camera, each pilot's own flight display straight ahead of them. Each is as wide as the nose leaves room
for, two blocks where it can (where the nose is too narrow for the outer pair, the inner pair shows the map and the
camera, and the tiller's keys go under the camera). Behind them the instrument panel carries on round to the hull as
plain casing, its own screens left off. The systems screen holds a tile for each of six switches, three across and two
down, each a picture of what its switch does, as it is now; while it is on its face takes on its colour, its rim
broadens in it and the light along its bottom glows in it, as on an airliner's push button: the cabin lights' dimmer
(a light bulb with long rays, lit pale yellow; with short ones in amber dimmed; dim blue under a crescent moon at
night), the seatbelt signs (someone in a seat with an amber belt buckled across their lap, lit amber), the jump lights
(a skydiver beside the jump light, its red and green halves both dark while it is off, red or green), the window
shades (a window on the sky with its striped shade a third of the way down, or pulled all the way down, lit amber),
the laser (its light metal turret firing a red beam, lit red) and the drop release (a crate on the bay's shut doors,
or the doors open and the crate floating down under a parachute, lit red). The everyday three, the dimmer, the
seatbelt signs and the shades, run along the top row, where a seated pilot's look rests, and the jump lights, the
laser and the drop along the bottom, out of the way of a stray touch. Touch a tile to work its switch. On the slope
under the screens are the keys: under each pilot's flight display the nose wheel tiller, drawn as the ground it steers
on, seen from above: dark tarmac between strips of grass, the airliner's white nose at the bottom with its nose
wheel's black tyre turned the way it is set, and five yellow taxi lines fanning out from the wheel, a hard curl and a
gentle bend either way and straight on between them. The line set is bright and solid, the others faint and dashed;
touch a line to steer that way at once (a ratchet's click, a deeper clunk back to straight ahead, a dull thud on the
line already set), and pointing at one brightens it first. While the airliner rolls, white dashes run along the line
it follows, and with the wheel turned the straight line pulses so it is not left turned by mistake; a green dot at its
foot shows the wheel is straight. Either pilot can taxi; under the map the parking brake, a wheel gripped red while it
is set, and the landing gear (see Landing gear); under the systems screen the autopilot's buttons (see Autopilot); and
under the camera's screen the cameras' buttons (see below). The desk runs round under the screens as one moulded
console in a blue-grey a step lighter than the screens, its sloped top brushed between a steel trim along its front
lip and its top edge, and past the outer screens it carries on without a screen out to the hull. Looking at the
screens or the slope from within reach, a little white dot follows your look, and the tile, key or camera button under
it is outlined in white, so you see what a touch will work before you touch it. The flight displays show attitude,
airspeed, altitude, vertical speed and heading once the airliner is assembled; until then STBY and ASSEMBLE TO FLY
under the green airliner of the autopilot's on/off key, the key that has the Physics Assembler assemble the airliner
and takes off. The moving map shows what the autopilot is doing over it and how the airliner flies under it, the way
it is heading always up, the track it has flown, and the circle it flies round or the heading it turns to; touch it to
zoom out a step, round to the closest again. The camera panel, like an A380's taxiing camera screen, shows the live
picture from one of the airliner's outside cameras, the ones the passenger screens look through (see below), the
camera on top of the fin at first: the whole airliner from above and behind, its wingtips at the edges, the ground or
the clouds beyond, with CAM TAIL and a blinking recording light in the corner. Where the nose leaves no room for the
camera panel, a tail camera display, a wide screen three blocks across, hangs from the crown just ahead of the seats
instead, tilted 45 degrees down toward them. Four buttons on the slope under the camera's screen pick the camera
(along the bottom left of its picture where the tiller takes that slope, and on the older tail camera display), each a
little airliner seen from the side with a big dot where its camera is, in a colour of its own, the one shown lit white
with a white rim: sky blue on the fin, looking ahead and down; green on the right wing, looking out along the wing
toward you; orange under the nose by the nose gear; and pink in front of the nose, level with the pilots' eyes and
looking fifteen degrees down at the runway coming up to meet the airliner. The camera on the picture's label takes the
same colour. Use one to switch; everyone sees the camera picked, and the screen keeps it. The picture is drawn small,
a few times a second, and only while a screen showing it is in sight within 32 blocks; every screen aboard the same
airliner set to the same camera shows the same picture. Without one it shows a test card: colour bars with the
airliner seen from behind, and under them NO SIGNAL, or ASSEMBLE TO FLY, then the same green airliner, while the
airliner stands as blocks. That is also what it shows with a shader pack (Iris, Oculus) or Fabulous graphics, or with
`tailCameraFeed` off in the client config, where `tailCameraFps`, `tailCameraResolution` and
`tailCameraRenderDistance` also set how often, how sharp and how far the picture is drawn. It is drawn at most ten
times a second by default (1 to 30), and reaches 12 chunks (2 to 32), never further than your own render distance,
since the world is only loaded that far round you, and the fog closes in at its edge as it does at the edge of your
own view. That is far enough for the whole airliner at the default scale to stand clear of the fog; at scale 3 it
takes 18 chunks, at scale 4 24. The further it reaches, the more each picture costs: what the camera sees is worked
out afresh every time it is drawn, while the game waits, about four times as much at twice the distance. A Tail Camera
Display can be crafted and put up in your own builds too: against a wall it stands upright, and put up against the
underside of a block it hangs from it tilted toward you. It reaches two blocks to the right of where it is put, as you
face it, and needs the room there; it comes down whole. On an assembled airliner with an autopilot it shows that
airliner's cameras. Against the wall behind the captain's seat is the Physics Assembler. Right behind each pilot's
seat stands that pilot's Flight Crew Locker, its door toward the back of the flight deck, with a Pilot Helmet and a
Pilot Cap in it when the airliner is built (see Souvenirs).

A Flight Crew Locker is a slim cabinet of brushed grey-blue panels for the crew's things, its pale door hinged on one
side with a small chrome handle on the other, and on the door a pilot's peaked cap, navy with its gold wings badge and
black peak, so even someone who cannot read yet knows what is inside. Use it and its door swings open with the clank of a light
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

The flight deck's voice, a calm, warm British woman's voice, unfailingly polite and not easily impressed, calls out
the take-off (V1, rotate, positive rate) and the landing (the height above the ground from a hundred down to ten, then
retard), and warns of too much bank ("Bank angle. Please do not do that.") or sinking too fast too low ("Pull up. This
is not a test."), whoever flies. The same voice makes the autopilot's cabin announcements (see below), as on an
airline's recorded announcements, over the cabin's loudspeakers.
Riding the airliner, the camera rumbles on the runway, thumps on touchdown and sways a little in flight; turn that
off with `cameraShake` in the client config. Whatever it is set to, the wheels are heard touching down, a thump of the
legs taking her weight and a chirp of the tyres spinning up, louder the harder she lands.

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
though, and the airliner slides to a stop on it. Lying on its belly, its wheels have no room to come down: the lever
bumps back up with a buzz and the lamps flash red, and the gear stays up until the airliner is in the air again, where
it comes down as ever (set to take off, the autopilot waits a few seconds for it, then slides off on its belly). The
autopilot works the gear by itself (see Autopilot). A Landing Gear Lever (a tire on a lever on an airframe panel) and
a Landing Gear Bay can be crafted, but only an airliner built with retractable gear has its legs wired to them. Switch
it off with `retractableGear` in the server config: an airliner built then keeps its gear down, and one built before
can still lower its gear but no longer raise it.

### Laser path clearer

On the systems screen is the laser's tile, a little turret firing a red beam (on older airliners, the laser switch on
the centre console: a big red button under a flip-up guard, pictured the same). Touch it and its rim glows red and,
with a rising whine, a turret drops out of a hatch in the underside of the nose. Once out, it fires rapid
short laser bolts, five a second, each a bright red beam with a sharp hiss and sparks where it strikes, at whatever lies in the assembled
airliner's flight path up to 96 blocks ahead (where it is going, or, standing still, where its nose points), as wide as
its wingspan and from under its wheels to the top of its fin: hostile mobs first, each bolt hurting one (two or three
bolts for a zombie), and otherwise the nearest block in the way, each bolt vaporising a bite of up to 27 blocks round
it, the clearing spreading from there across whatever stands in the way. As it fires, the turret swings its head
round and tilts its barrel to point where it is shooting, smoothly, as a motor would turn it, each bolt leaving the
barrel's glowing tip, and a moment after its last bolt it turns back to face ahead. Press the button again and the
turret folds back in. Low down (less than 15 blocks over the ground, until it is 25 blocks up again) with the engines
idle it holds its fire at blocks and shoots only hostile mobs, so a parked airliner never digs into the ground round it;
the button then glows amber instead of red, with a soft beep down as it starts holding and up as it clears the way
again. With the engines running it clears the way however low it is, taxiing, taking off, or pushing at something in its
way. It never vaporises anything standing no higher than the bottom of the airliner's wheels, so the runway under it is
safe. The laser fires only from an assembled airliner.

It never fires at players, villagers, pets or any mob that is not hostile, nor at anything aboard an airliner. It
leaves alone blocks with something in them (chests, signs and the like), blocks that cannot be broken, liquids and
other airliners, and blocks entirely wherever the `mobGriefing` game rule is off; it asks first, as a player breaking
the block would, so land a claim protects keeps its blocks. Turn it off with `laserPathClearer` in the server config
(the switch then only buzzes), where `laserRange` and `laserBoltsPerSecond` set how far it reaches and how fast it
fires. A Laser Switch and a Laser Turret can be crafted, but only an airliner built with the laser has them wired.

### Self-destruct

On the flight deck's side wall at a standing pilot's eyes, just left of the announcement panel as you face it from the
seats (on the other side wall where that has no room, and failing both on the back wall beside the door), is the
self-destruct button: a big red button on a plate in a yellow-and-black hazard border, under a clear lid in a striped
frame. It takes two presses on purpose to set off. Use it once and the lid flips up with a clack. Use it again and
it arms: the button lights up and flashes red, faster and faster, the alarm's siren wails for everyone aboard wherever
they are, a beep sounds every second, rising in pitch and twice a second over the last three, and on everyone's screen
the edges pulse red and a picture of the button stands over a row of ten red lamps, one going out each second, the
last one lit blinking. When the last lamp goes out, the airliner blows up; press it a third time before then and it
blows up at once. To stop it, sneak and use it (with an empty hand): the lid snaps shut and it disarms with a falling
hum. Holding the use key down, or pressing twice in quick succession, never gets it past a step: each press has to be
its own, and it waits a second after arming before a press sets it off.

As it blows up, everyone aboard puts on a parachute (whatever they wore on their chest goes into their inventory) and
is got out of their seat, and the airliner is gone, every block of it at once, with everything loose or stored aboard
(the chests' and lockers' contents, items on its floors, trolleys, balls, paper planes, frames and pictures): nothing
is dropped and none of it comes back. In its place it goes up in a huge explosion, Ultrasplosive's nuke scaled down to
the airliner (Ultrasplosive need not be installed): a blinding white flash and a shake of the view for everyone near,
fire bursting out all over where the airliner was, its wings and tail too, round a fireball in the middle, sparks and
burning embers flying out of it, a ring of dust racing out from it (along the ground if the ground is near), a column
of smoke rising into a small mushroom cloud that hangs on a while, and a huge bang, heard later the further off you
are, with a long rumble after it. Everyone who was aboard falls clear in the middle of it, stopped where she blew up
however fast she flew, so they see the fire burst all round them; their parachute opens by itself as soon as they fall
free, and they float down under it, steering it the way they walk; a fall too short for it to open does not hurt
either. A villager passenger or a pet aboard floats down slowly. Nothing round the airliner is
touched: the explosion breaks no blocks, sets nothing alight and hurts nobody.

The button only works on an airliner of this mod's, built from its blueprint, and only blows up the airliner it is
part of, never another mod's vehicle, even with an autopilot put aboard by hand; anywhere else, arming it only gives a
dull thud. Armed on an airliner still standing parked as blocks, it first has its Physics Assembler put it together,
as Take Off does, with a hum, and arms a moment later, once it has. Turn it off with `selfDestruct` in the server
config: the button then only thuds, and airliners built while it is off have none. A Self-Destruct Button can be
crafted and put up on a wall of an airliner in your own builds, facing you; it works the airliner of the nearest
autopilot within 24 blocks, once that airliner is assembled.

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

The autopilot makes announcements of its own, in the flight deck's voice after the same chime, and everyone aboard hears
them wherever they are in the cabin. Set to take off (Take Off, on the ground), it welcomes everyone aboard and asks the
cabin crew to be seated for take-off; set to land (Land), it asks everyone back to their seats and the crew to be seated
for landing; and once the airliner has landed and stopped, however it was flown (on the autopilot or on the thrust
levers), it welcomes everyone to their destination and lets them go. As crews do, it switches the seatbelt signs on with
the take-off and landing announcements (which opens the curtains), and off again once stopped after landing, so everyone
can get up. Announcements never talk over each other, nor over the callouts: one asked for while another plays, or just
before a callout is due, waits for it to end, and the same one is not made again within twenty seconds, however often
the buttons are pressed. Only a warning nobody could see coming ("bank angle", "pull up") cuts an announcement short, to
be heard.

On the flight deck's back wall, left of the door as you face it from the seats, is the announcement panel, a slim
quarter of a block deep: nine tiles, three by three, each making one announcement in the same voice when pressed (use
it), for young pilots too, since every tile is a big picture first and its name only small under it. Read row by row
from the top, in the order of a flight: Welcome (someone waving hello), Take-off (the airliner climbing off a runway;
the autopilot's own announcement), Seatbelts (a cloud over bumpy air and a buckled belt: bumpy air ahead, seatbelt
signs on), Snacks (a cup and a cookie: drinks and snacks are coming), Look out (a cabin window onto the sky and
mountains), Pool & gym (a basketball over the pool: seatbelt signs off, the pool and the gym open), Sleep (the moon and
stars: the cabin lights go down to their night glow), Landing (the airliner coming down to a runway) and Arrival (a
heart over the parked airliner). Where the wall leaves it no room, it hangs overhead instead, close under the ceiling
over the pilots' seats (ahead of them over the centre console where the ceiling leaves room, else reaching back behind
them, its rows then running the other way, so the first row, Welcome, Take-off and Seatbelts, hangs right over the
seats, within a seated pilot's reach). A tile glows in its colour while it can be pressed, lights up and pulses while
its announcement plays, blinks while it waits for the flight deck to fall quiet, and greys out while another is
playing and for twenty seconds after its own. An Announcement Panel can be crafted and put up in your own builds too,
on a wall or against the underside of a block, its middle tile where you put it (under a ceiling its top row nearest
you as you face); it works the nearest airliner's autopilot within 24 blocks.

Right of the door, as big as the announcement panel, is the flight map: the passenger screens' route map (see
Passenger screens) for good, the route flown since take-off and the airliner on it under a header saying whether it is
climbing, cruising or descending, and under the map its altitude, its speed over the ground and how far it has flown.
A Flight Map can be crafted and put up on a wall in your own builds, its middle where you put it; it shows the flight
of the airliner it is part of, and a welcome page while that stands as blocks.

On the side wall beside the announcement panel is the self-destruct button (see Self-destruct).

Air traffic control talks to the airliner over the flight deck's radio, calling it "Aerolarge three eight zero": a
man's voice, so it is never taken for the flight deck's own, crackling through a VHF radio's hiss and squelch. Set to
take off, the autopilot holds the brakes until the tower has cleared it for take-off, a few seconds, and the cabin
hears the take-off announcement as they come off. Climbing away past 20 blocks above the ground, on the autopilot or
by hand, the tower hands it to departure, which has it on radar and climbing. Set to land, the tower clears it to
land, with the wind and the runway, before the cabin hears the landing announcement; going around (not lined up with
the runway near the pin) has it climb straight ahead. Down and stopped, ground welcomes it home and sends it to the
gate. In flight, 30 blocks or more above the ground and not landing, it chatters every two to five minutes: first the
hand-over to the next controller, then traffic to look out for, the ride ahead, a flock of geese or a hot air balloon
to spot out of the window, and other aircraft on the frequency, one of them saying hello; now and then someone presses
the button and says nothing. Everyone on the flight deck hears it (within 24 blocks of the autopilot, as far as it
tells the flight deck what it does), and so does whoever wears a pilot helmet anywhere aboard: its ear cups are a
headset. It never talks over a callout or an announcement: it waits for them to end, and keeps clear of a callout
about to be made. What has waited too long, or no longer fits what the airliner is doing, is left unsaid. Each
transmission has its own subtitle ("ATC: Cleared for take-off"), and while it talks a little radio panel shows in the
top left corner of the screen of whoever hears it: a pilot's headset with the radio's green lamp lit, gently pulsing,
so that those who cannot read yet know the radio is talking too. Turn it off with `atcChatter` in the server config,
or make it quieter with `atcVolume`; each player's Voice/Speech slider turns it down too.

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
older airliner) to open the autopilot's settings. Under the systems screen, on the sloped panel, are its controls,
each a picture of what it sets, in the order the pilots' flight displays read. At the left is the on/off key, the
green airliner: lit while the autopilot flies, pulsing while it is off, the first to find. Then a speedometer, its
band green, amber and red from slow to fast, its white needle at the speed now and a magenta marker on its rim at the
speed set: touch the rim where you want the speed (30 to 120 blocks a second, in steps of 5). In the middle the
steering: a half dial seen from above, the airliner at its foot nose up as on the moving map, a magenta marker on its
rim where it is set to fly and a magenta path from the nose curving round to it, north a faint red mark; touch the
dial where you want to go and it flies that way, straight on (in steps of 5 degrees; touch it again further round to
turn more than a quarter). Under the dial, like road signs, the ways to fly: a tight circle left, a gentle circle
left, straight on, a gentle circle right and a tight circle right, each the path flown from a little airliner's nose,
the one flown lit (cyan straight on, magenta round a circle). Straight on flies on the way the airliner is going. A
tight circle is flown as steeply as the autopilot banks round a circle, 20 degrees; a gentle one at half that, about
twice as wide, but no wider than 2500 blocks where the tight one leaves room. How big that is follows from the speed
set: a radius of about 625 blocks at the cruising speed, more the faster (set faster or slower while circling,
it comes round a new circle beside it). At the right, the sky from the side, over the ground: the airliner in green at
the height set, faintly at the height it flies now, a dotted climb or descent between them, heights up its side; touch
the sky where you want the airliner to fly (from 40 blocks above the runway to 30 below the top of the world, in steps
of 10). Pointed at, the speedometer, the dial and the sky show a ghost of what a touch there would set, with its
number, before you touch; and small windows give the speed, heading and height set. A touch on the speedometer or the
sky sounds a note, higher the faster or higher it sets; a way to fly whooshes. A pin lamp in the dial's corner lights
while it flies to a pin (see below). While the autopilot is off the controls only thud, and the on/off key opens the
settings, where it is engaged; while it flies, the on/off key switches it off, and whenever it lets go, switched off or
landed, its disconnect warning sounds, three quick rising tones played twice. A touch it cannot take thuds too: while
it comes in to land, so nothing touched by accident calls the landing off, and while it rolls out after landing.

The control head's own small display shows what the autopilot is set to, in the same pictures and colours, turned to
face whoever looks at it: a lamp (green while it flies the airliner, amber while it is stuck, dark while it is off),
what it is doing (take-off, climb, cruise, descent, landing, rolling out or off) and the way it flies (the road sign,
or the pin), and the height, speed and heading (or the circle's radius) it is set to, each a picture beside its
number, greyed out while it is off. The settings have the same controls as the panel: at the top what the autopilot is
doing, with a lamp (green engaged, amber off), and on the glass how the airliner flies now (speed, height and heading)
or what engaging does; then the speedometer, the steering dial over the five ways to fly, and the sky, each clicked,
or dragged, where you want it, stepped with the arrow keys or the scroll wheel, or its number typed into the box under
it; and under the steering the pin (see below). Every setting and button has a picture and a colour of its own, so
that players who cannot read yet can fly too; point at one to see its name. The game goes on behind the settings, and
they stay open, to watch the autopilot fly, until you press the X in their title bar (neither the buttons nor Escape
close them).

Press the pin (a red map pin, under the steering) and the steering gives way to a map: the land and the villages as
the route map on the passenger screens has them (see Passenger screens), the airliner as a gold arrow, and the pin,
once dropped, as a red dot in a red ring, the circle it will be flown round. Drag the map to look about, scroll to
zoom out and in, and click to drop the pin: a click close to a village drops it right on the village. Every
transponder shows on the map too (see Control tower and transponders), by its picture in its colour on a dark disc, as
a beacon shows in Subnautica, other airliners with a few dots the way they fly; point at one to see what it is, where
and how far, and click on one to drop the pin right on it. Every other player in the dimension shows on it as well,
however far off, as their face in a white frame, where they were a second ago (those riding this airliner are left
out, its arrow standing for them); point at a face to see who it is, where and how far, and click on it to drop the
pin where they stand. The pin stays where it was dropped; it does not follow them. A small cross marks the middle of
the map, whose X and Z show in its bottom left corner (and the pin's in the bottom right once dropped), so you can
find your way about even where the land has not been seen yet. Engaged, the airliner turns for the circle round the
pin, heading for it along the line that just touches it, so it comes onto the circle already turning, and flies round
it clockwise, tightly or gently as set beside the pin (the tight or the gentle circle right: press it to swap them,
the ring on the map growing or shrinking), until set to fly something else or told to land. The height and speed fly
as set, and stay on the settings beside the map. Press the pin again to steer instead.

Beside them, while it flies to a pin, is Land at the Pin: an amber airliner settling onto a runway by a little red
pin, its tile lit green while it is on (point at it to see whether it is). Like every button, it has its picture and
its colour so that players who cannot read yet can use it: pick the pin on the map, light the button, engage. Then the
airliner does not circle the pin but lands near it, on its own, somewhere clear of buildings and trees. It flies
straight to the pin and on over it, back and forth, looking at the land below: the server only has the land near the
players aboard to look at, so it has to fly over it first. Meanwhile it measures itself, wingtip to wingtip and nose
to tail, and looks for a strip of land within 320 blocks of the pin, nearest first and every 15 degrees round, where
it fits: dry, level ground (no more than 2 blocks above or below where it touches down, and no step higher than 1) for
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
there starts it afresh: with Land at the Pin still lit it looks again and comes back round; switched off, it circles
the pin instead. Once it turns onto final it takes no new settings, as when it lands ahead (see below).

Where a runway laid down from its blueprint (see Runways) lies in the circle round the pin, it has nothing to look for:
as soon as it has measured itself it lands on that runway, from whichever end it comes to with less turning, touching
down just past the threshold and braking to a stop on the centre line. Everyone aboard as it stops there earns On the
Numbers.

Switched off, the settings start from what engaging would fly, and engaging (the wide glowing button, or Enter)
flies them. Engaged on an airliner not yet assembled, the autopilot first has its Physics Assembler assemble it, as a
Create deployer works the assembler, and engages with the settings once the airliner is assembled, telling everyone
aboard. The Take Off button greys out the moment it is pressed, with "Assembling..." on the screen, and
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
changed (it stays dark until something on the panel differs from what the autopilot flies, and while it comes in to
land), and Disengage hands the airliner back to the thrust levers, the tiller and the parking brake. How it is flying
shows on the pilots' head-up display (see below).

In the air, Land puts the airliner down ahead, engaging the autopilot if it is off. It rolls out of any circle onto
the heading it is on and slows until it flies with its nose level, banking less the lower it gets. High up it comes
down 4.5 blocks a second, easing to 3 blocks a second between 60 and 30 blocks above the ground, and it eases into the
descent, sinking faster by at most half a block a second every second, so the nose goes down gently; from 15 blocks
above the ground it flares, sinking ever more slowly, to touch down at half a block a second.
How fast it flies nose level depends on how heavily it is loaded and how thin the air is, so it finds that speed on
the way down, starting from a tenth above the rotation speed (`rotateSpeed`): furnishing it or loading it up needs
no new settings. Down, it idles the engines, lowers the nose and brakes to a stop, steering along the heading
with the nose wheel; then it pulls the thrust levers to idle, centres the tiller, sets the parking brake and
switches itself off. It lowers the gear as it starts down, and on a landing at the pin as it turns onto final. It lands
on whatever lies below, so line it up over open, level ground first. While it comes down it takes no new settings,
so nothing pressed by accident calls the landing off: Apply stays dark and the flight deck's controls thud. Disengage
still switches it off, handing the airliner back to the thrust levers; engaged again in the air, it flies on from
there.

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
either pilot's seat, or standing at the console), its visor shows a head-up display: thin, see-through green lines and
figures, as a real HUD's, edged with a thin dark line so they read against bright sky, with whatever the autopilot is
set to fly in magenta; without the helmet there is none. Putting the helmet on there switches it on with a beep, and
it fades in. It comes in parts, kept clear of the rest of the HUD and of the middle of the view. Close round the
crosshair are a short horizon line and a faint pitch ladder, about 15 degrees of it either way, tilting with the bank
and dropping as the nose comes up (the horizon amber past 35 degrees of bank), and the flight path marker, a small
ring with wings showing where the airliner is going. Left of the hotbar is the speed on a narrow moving tape, the
speed set as a magenta mark beside it and an arrow to where the speed will be in ten seconds; beside that a picture of
what the autopilot is doing over its name: taking off, climbing, cruising, descending, landing, braking to a stop
after landing, or a red power symbol while it is off. Right of the hotbar is the height on a moving tape (the
autopilot's own, as it holds it), the height set as a magenta mark and, once it is near, the ground rising up the tape
as an amber line; beyond it a bar that climbs green or sinks amber, and beside that the wheels' height above the
ground over the climb or sink with an arrow. At the top of the screen, under any boss bars, is a heading scale with
the heading in a box, the heading set as a magenta mark and the way it flies beside it (the road sign of the panel,
with the circle's radius while it circles). The speed and the height stand out against the screen's left and right
edges, 40 pixels in from them, level with the crosshair, or both higher to keep the speed above the chat and the
height above the subtitles. On a short screen the chat can reach up past the crosshair; it is drawn over the horizon
there. Each part curves with the visor where it sits: the visor bulges out in front of the eyes, so what is on it is
drawn in toward the middle of the view, the more the further out it is. The heading at the top arches, its ends
curving down and away; out at the sides the tapes bow outward; everything narrows a little toward the edges; and the
horizon in the middle stays straight. The head-up display is drawn about half the size of the rest of the HUD and
grows with the GUI scale; `pilotHudScale` in the client config makes it bigger or smaller (smaller again on a small
screen). Being on the visor, it shows only in first person. It hides with the rest of the HUD (F1), behind any screen,
under the debug screen and while you look through an outside camera; turn it off with `pilotHud` in the client config.
While you wear the helmet and look out through your own eyes, its visor also tints the view a touch toward its gold,
darkens it faintly toward the edges and catches a little light along its top.

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
strip of blue LED light glowing along its foot; side by side the pieces run on into one bar, and things stand on it. A
Bar Stool is a chrome stool with a round oxblood leather seat and a footrest ring, to sit on: it is one of Create's
seats underneath, as the cabin seats are. A Back Bar is a cabinet, walnut at its sides, its dark front of smoked glass
doors behind a chrome rail, under a stone top with a warm LED strip along its edge; put another on top of it and that
one is shelves of bottles in front of a smoked mirror, three rows of them in every colour and shape, tall and slim,
square, round and squat decanters, on the cabinet's top and on two glass shelves, a warm LED strip lighting each row
from below (shelves left with nothing under them come down). All three can be crafted. The furnished airliner's lounge
has its bar along the starboard wall: the counter with stools in front of every other piece, and behind it the back bar,
its shelves of bottles rising between the windows with planters between them.

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
plain cream bulkhead panel to build walls with, a Cabin Arch a white archway in one, a Bulkhead Sign a
backlit sign set into one, and an Exit Sign a green running man heading out through a doorway. All five can be
crafted. A Cabin Arch puts up a whole archway like the airliner's: a round white arch over an opening three blocks
wide, on white jambs three blocks tall either side of it, smooth white panelling round the opening with a moulding
along its edge, a bead and a groove behind it, the arch, its moulding and the panelling's edge all running smoothly
round the curve as the cockpit door's arch does, without a step, set into cream panels like the partition's and the
same from either side. It goes up across the way you face, the foot of its left-hand jamb where you put it and the
rest to your right and up, five blocks wide and five tall; sneak to put up a wide one, over an opening five blocks
wide, seven blocks wide and six tall with partition panels in its top corners. It only goes up where there is room
for all of it and nobody stands in its way. Breaking any piece of it takes the whole archway down, panels and all,
and gives back the one Cabin Arch, as picking it does. The airliner's own archways are built a piece at a time and
come down a piece at a time: taken down, or picked, such a piece keeps which piece it is, and goes down alone, the
right-hand side of an archway as you face it or the left-hand side sneaking, for building archways of other shapes,
with taller jambs say.
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
on the flight deck, perched on top of the captain's locker behind their seat (on the glareshield, where pilots keep
their mascots, where there is no locker), one in the WC behind the flight deck by the basin,
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
or a shelf, its nose toward you. Both can be crafted, or bought at a duty-free counter. A Model Zeppelin, a silver
cigar on its stand with its fins and its car, is in the creative tab.

A Pilot Helmet is a jet pilot's flight helmet: white with the livery's blue stripe over the crown, the airline's
roundel over each ear, a gold visor down over the eyes and a microphone on a boom in front of the mouth. It is worn
the same way as the cap, and protects as much as a leather cap. On the flight deck its visor shows the pilots'
head-up display (see Autopilot). Craft it from five iron ingots round a black stained glass pane, with two black wool
under them for the ear cups, or buy it at a duty-free counter for 10 emeralds. Every airliner with pilots' seats is
built with a helmet and a cap in each pilot's locker on its flight deck (see Flight deck).

A Space Helmet is an astronaut's, for the rocket's crew: a round white bubble of a helmet, well bigger than the head,
with a big gold visor over the face, mirrored, the sun's glint across it and the blue rim of the planet curving across
its foot, a dark ring at its neck and a mission patch on its left side, a little white rocket climbing out of its
flame on navy inside a gold ring. It is worn as the pilot helmet is, and protects as much. From inside, its visor tints
the view a touch of gold, lighter than the pilot helmet's, its rim only faint round the edges, and shows no head-up
display. Aboard a rocket at the edge of space or over it, its air is heard softly in the helmet, a calm, slow hiss in
and out through its valve as you breathe, until it comes off, you leave her or she comes back down (the client
config's `helmetBreathing` keeps it silent). Craft it from five white concrete round a gold ingot for the visor, with
three iron ingots under them for the neck ring. Every furnished rocket is built with one in each crew locker on her
entry story and in her bunk room (see The rocket).

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
everyone nearby with its distance from the middle, and using the target shows the best landing so far. Paper planes
that come down near it are called out the same way for whoever threw them, with a best of their own.

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
on it can. A zeppelin's are held the same way, its gas cells and burners with them, so no pickaxe lets a cell's hot
air out. Sneaking with a wrench (Create's will do), or sneaking in creative mode, still breaks them: to take an
airliner apart on purpose, or to take back a panel put in the wrong spot.

Only the blocks the blueprint laid are held so, and only while they are part of an airliner: inside the Super Glue the
blueprint glues it with, or among its blocks once it is assembled. A zeppelin, which is not glued, is held where it
stands only while its control car is loaded: a player far enough aft along its 490 blocks that the land under the car
unloads can take it apart there, until it has flown once. The same blocks anywhere else, as the runway's
lights and the control tower's glass, lights, panels and seats are, break as any other block does. So does one placed
by hand, a nav light or a panel added to a finished airliner say, glued in or flying; a Create deployer places by hand
too, a schematicannon does not. An airliner taken apart again forgets which of its blocks were placed by hand.

Put down by hand, the frame's blocks sit the way they do aboard. An Airframe Fairing, the skin with its outer edge cut
away at 45 degrees, goes down as stairs do: its cut faces you, along its top edge put on top of a block or against
the lower half of a block's side, and along its bottom edge put against the underside of a block or the upper half of
a block's side. A Nav Light shines out from the face it is put against. What a pilot sits behind (the thrust levers,
the autopilot's control head, the tiller and the parking brake) goes down with its nose the way you face, as a
Landing Gear Bay's leg swings up and a Laser Turret fires that way; what faces the pilots (the instrument panel, a
flight display, the gear lever and the laser, jump light and seatbelt switches) faces you, as a parachute rack does.

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
and a colour of its own, as a beacon does in Subnautica. Use it to pick them on its panel: eleven pictures (a control
tower, an airliner, home, a star, a flag, a heart, treasure, a forest, a harbour, a mine and an airship) and twelve
colours, each a button showing what it picks, with the marker as the maps will show it on the left; it shows its picture
in its colour on its own little screen, too. One standing on the ground shows on the maps from anywhere, however far
off; one aboard an assembled airliner flies with it, and shows where the airliner is while it is loaded, which it is
while anyone is near it. The control tower's transponder shows the tower as an orange tower, and every airliner built
from 0.1.0-alpha.48 on has one on the flight deck (on newer airliners on the centre console behind the seats), showing
it as a white airliner (an older airliner can be given one: a Transponder is crafted from iron, a glass pane, redstone
and a compass, and goes on a console or a desk anywhere). On its own autopilot's map an airliner's transponder is left
out, the airliner being its gold arrow there.

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

The First Flight tab of the advancements, there from the moment a world is joined without a toast or a word in chat,
marks the milestones: aboard as the airliner climbs away from the runway (Cleared for Takeoff), trying to get up with
the seatbelt signs on (Buckle Up), aboard for a touchdown sinking slower than a block a second (Butter Landing), a
basket scored in flight (Swish at 30,000 Feet), a parachute landing within a block of a target (Perfect Jump), a care
package down within 10 blocks of a burning flare (Special Delivery), a villager passenger's fare (Frequent Flyer),
emeralds spent at a duty-free counter (Duty Free), a paper plane thrown out of an airliner in flight coming down 100
blocks away (Air Mail), a dive into the moon pool off its springboard or its tower while the airliner flies, 15 metres
or more above the ground (Cannonball), the three golden suitcases hidden in the hold (Lost and Found), a vacuum toilet
flushed in flight, its blue ice let go 15 metres or more above the ground (Blue Ice), the five rubber ducks hidden
aboard (Duck Hunt), and an espresso, a cappuccino and a mocha taken from a coffee machine (Barista), and aboard as the
autopilot lands on a runway laid down from its blueprint and stops there (On the Numbers), and aboard a rocket as she
climbs to the edge of space at y 1,000 (Edge of Space).

### The zeppelin

A rigid airship in the Hindenburg's style, as big as the real one at the airliner's scale: about 490 blocks long and 82
across, its fins reaching some 100 blocks up from the ground, as long as three airliners nose to tail. It is built at
that size whatever `scale` the server config sets for the airliner. The Zeppelin Blueprint builds one as an airliner's
blueprint does, and operators with `/aerolarge build <pos> <north|east|south|west> zeppelin [furnished|festive]
[half|full]`, its nose tip at the position and its tail stretching away the way given, through whatever stands there; it
takes a few tens of seconds to go up. At half the size, some 245 blocks long, its hull is smaller and its control car
and decks a player's size as ever, the hull raised over the car and its gas cells fuller for its size, to float them;
its engine cars are a quarter slimmer and stand nearer the hull, with shorter propellers and a smaller room inside, and
push a third as hard, as she weighs a third as much; she climbs to some 175 blocks over the ground. The Half-Size
Zeppelin Blueprints build one, plain, furnished or festive, with everything below, each piece as big as the full ship's
wherever it fits: only the colours and the crest painted on her and the flank screens are made to fit her smaller fins
and flanks, the techno room is a little shorter, and she has no flight deck for the flying carrier.

Outside, a silver fabric cover is cut to the hull's curve block by block, as the airliner's skin is: a blunt rounded
nose, a long full middle and a long taper to a point at the stern, where four fins stand in a cross. A glazed control
car hangs under the bow, faired into the hull above it as the Hindenburg's was, lit inside by three rows of lamps along
its ceiling, and four streamlined engine cars stand out on girder struts along the flanks, two to each side, each with a
big four-bladed wooden propeller with brass tips behind it that winds up when its engine runs and spins down slowly when
it stops, the car's big diesel thrumming and its blades beating the air louder and higher as it turns faster, heard
from the ground under her as she passes over and muffled inside her hull. The zeppelin rests on a wheel under the control car and another under the lower fin.

At night it is dressed in lights, as an airship's are and as a ship's are when she is dressed overall: a red light on
each engine car to port and a green one on each to starboard, shining out to the side, a white light on the tip of the
tail, white strobes double-flashing on the tips of the fins, a red beacon blinking under the belly as the mast's does
on the Sky Deck, and a line of warm bulbs along the crown from the nose up the leading edge of the upper fin. Their glow
lights nothing up round them but reads from the ground the whole length of the ship, a bright point however far off; an
airliner's lights glow the same way. The server config's `navigationLights` leaves them off zeppelins built from then
on.

Floodlights hang under the control car and along the keel, flat black lamps whose lenses glow warm white. They come on
of their own accord as soon as she is built, with the clunk of their contactors and the crackle of their lamps striking
up, and stay on by day and night, throwing their light down and a little ahead onto the land under her: at night so
those aboard see where she is going and those below see her coming, and by day into the shade of her hull, where her
great shadow lies over the land. It is real light, through Veil (which comes with Sable), as the cabin's night lights
are; the client config's `floodlightLight` turns it off and draws a pool of light on the ground under each lamp
instead, as there always is with Iris installed. The server config's `floodlights` leaves them off zeppelins built
from then on.

By day, flying, she casts her great shadow on the land under her, hull and fins, from where the sun is: right under her
and sharp-edged at noon, thrown off and drawn out long, its edges soft, as the sun sinks, fading away near sunrise and
sunset, in rain and at night. It darkens the tops of the trees and the water as well as the ground, and is only drawn:
the light under her is as it ever was. Where her floodlights shine into it, it is lighter, the more so the lower she
flies: flying low, landing or parked, the ground they light under her is as bright as in the sun, and from a cruise
high up her shadow there is still lifted by half or less. The client config's `greatShadow` turns it off.

The Festive Zeppelin Blueprint, in the creative tab, builds the furnished zeppelin dressed for Christmas: frosted
silver with snow on its crown and a red band edged white round it at every ring, its fins and engine cars green, its
gas cells red and green within; fairy lights round every ring, red, gold, green and blue, twinkling one colour after
another so they seem to chase round; a wreath of evergreen garland dotted with berries round the tip of its nose, a red
bow under it; a gold star in place of the red beacon on its mast; and sleigh bells ringing with its engines as they
turn, heard all over the ship aboard and from its propellers below. Operators build one with `/aerolarge build <pos>
<north|east|south|west> zeppelin festive`. Its livery selector can repaint it in any of its liveries; its cells,
lights, wreath and star stay. The Festive Garland and Festive Bow its wreath is made of are in the creative tab too.

Inside, the cover is held up by seventeen rings of blue lattice girders, and in the sixteen bays between them hang its
sixteen gas cells, huge balloons of Create Aeronautics' envelope, every one a colour of its own from red at the bow
through orange, yellow, green and blue to black at the stern, so a cell is told by its colour alone. Under the cells an
axial gangway, an open aluminium grating with glass rails and lamps along its edges, runs from the second ring to the
second last, nearly the whole length of the hull. In the belly below lie the passenger decks: a lower deck, and over it
an upper deck with long promenade windows down both sides, slanting with the hull's curve. Stairs climb from the lower
deck to the upper and on up to the gangway; boarding stairs come up through the belly from the ground, and a ladder
climbs from the control car up its neck to a corridor running aft to the decks. Her boarding stairs are the airliner's
airstairs, two blocks wide: two flights, one either side of the middle, coming down forward from the lower deck
through stairwells in its floor, open treads with yellow nosings between aluminium stringers and a handrail along each
side, walked like any stairs. They fold away as soon as she moves, with the airliner's airstairs' motor and latch: the
handrails go down, the lower sections run in, the flight swings up flat under the deck's floor, and her cover and the
floor close over it. Once she is down on the ground again after flying, or as soon as she is built, they come down of
their own accord, as far as the ground lets them; going away, they wait with a chime until nobody is on them. A lit
amber button with a staircase on it, the airstairs' own, stands at the head of each flight on the lower deck, and
another low on the outside of the control car's port side, to call them down from the ground: pressing any of them
folds all her boarding stairs away or brings them down, while she stands still; moving, it only sounds a low note. A
zeppelin built before keeps her stairs of blocks, a block wide, which fold away into her belly a row at a time from
their foot.

Aboard, you hear her hull: the hollow boom of the wind over her cover ringing through the great space inside, louder the
faster she flies, and now and then a girder groaning somewhere round you, echoing away through the hull. The client
config's `hullAmbience` turns it off.

Flying through cloud or rain, her promenade windows mist over on the inside, a pale film creeping over the glass in a
few seconds and clearing again once she is out of it. Hold use with an empty hand on a misted pane and your finger draws
on it, squeaking on the wet glass, wiping lines of clear glass through the mist, which mist over again within half a
minute. Your breath and your drawing are your own: nobody else aboard sees them. The client config's `mistyWindows`
turns it off.

The Furnished Zeppelin Blueprint fits the decks out from the airliner's furniture, as the Hindenburg's were laid out. On
the lower deck the boarding hall at the front has bamboo in planters along its walls; the galley beside the stairs has
stainless worktops and stowage, a coffee machine on its middle worktop; and aft of the stairs lie the sleeping cabins,
bunks along both walls, each blanket a different soft colour and every reading light on. On the upper deck, blue
armchairs stand along both promenades facing out of the slanting windows; the dining room at the front has tables set
for four, two red armchairs down either side; and aft on the port side the lounge has a bar with stools before its back
bar, the coffee machine on the bar, and bean bags beyond it. Everything works as it does aboard the airliner: sit in the
chairs, stools and bean bags, brew a coffee, switch a bunk's reading light on and off.

The Sky Shaft climbs to the top. Just aft of the middle, where a ring stands between two cells' rounded ends, a gap in
the gangway's rail leads to a climbing rope inside a cage of girders. It rises past the glowing cells, some 40 blocks,
out through a hatch in the crown onto the Sky Deck. That is a railed grating on top of the zeppelin, with a lamp at each
corner, a gantry over the hatch that the rope hangs from, and a mast whose red beacon blinks over it. Climb the rope as
any ladder is climbed, and step off fore or aft at the top. Like a ladder it slows a fall, so nobody is hurt slipping
down it.

The Sky Deck is a place to jump from. Along its port side stand a rack of parachutes, the jump switch and the jump
light it works, red to stand by and green to go with a bell, by a gap in the rail: the way out onto the crown, down its
curve and off the flank, under a parachute (or the safety canopy, see below). Along its starboard side stand a Paper
Plane Box, never empty, whose planes glide hundreds of blocks from up here, and a locker holding a landing target and
four signal flares to lay out on the ground below as a drop zone: a Landing Target scores paper planes as well as
parachute landings, calling out how far from its middle each came down, with a record of its own for planes, and rings
its bell in the ears of a thrower too far off to hear it. A plane gliding far from anyone keeps the land round it going
until it is down, so it never hangs in the sky. The server config's `skyDeckLaunch` leaves it all off zeppelins built
from then on; a Paper Plane Box is crafted from eight paper planes round a chest.

A white flagstaff stands at the aft end of the Sky Deck, a gilded truck on its top and its halyard made fast to a brass
cleat at its foot: the ship's colours are hoisted there. Use the Flag Halyard with any banner and the banner is bent on
and run up the staff, the pulley squeaking hand over hand; at the top it breaks out with a flap of cloth as a great
flag, twenty blocks long and ten deep, rippling as it streams out aft over the crown, and the zeppelin's top and bottom
fins are painted with it, one block to each of the banner's pixels (to every second one on a half-size zeppelin's
smaller fins), as the Hindenburg wore its colours on its fins. As a banner flown as a flag is, it flies with its top at
the hoist and its left edge along the flag's top, and the fins carry it the same way, flying aft. Use the halyard with
an empty hand to strike the colours: the banner comes back, and the fins go back to the livery's colour. The server
config's `shipsColours` leaves the staff off zeppelins built from then on; a Flagstaff is crafted from white dye over
two sticks, and a Flag Halyard from string over a flagstaff over a copper ingot, to fly colours from anywhere.

A zeppelin is christened as a ship is launched, with a bottle broken on her nose. Name a Christening Bottle at an anvil
and swing it at her nose, close by or from the ground under it, for it reaches some 60 blocks: the glass smashes with a
bang, wine sprays, and a flock of 48 white doves flies off, as the airship Akron's pigeons did, while "I name this
ship..." is said. Then her crest is painted on both flanks, sweeping back from the bow: the colours flying from her
flagstaff, upright in a gold frame, one block to each of the banner's pixels (to every second one on a half-size
zeppelin), or a gold globe on blue when none fly; and beside it her name, in bold serif capitals some 11 blocks high
standing on the equator, as the Graf Zeppelin's ran along her bow (a long name is lettered smaller to fit), dark on a
light livery and white on a dark one, reading the right way from either side. A Ship's Plaque on the back wall of the
observation lounge keeps them, engraved with her crest and her name, or with who christened her when the bottle had no
name. Swung anywhere else on her, the bottle only clinks. A new bottle christens her afresh. The server config's
`christening` leaves the plaque off zeppelins built from then on; a Christening Bottle is crafted from a gold nugget
over sweet berries over a glass bottle, and a Ship's Plaque from a gold nugget between two copper ingots.

Down each flank, under the equator between the promenade windows and the engine cars, glows a screen some 64 blocks long
and 20 high, curving with the hull in a dark bezel and reading the right way round from either side, bright enough to be
seen at night. On a half-size zeppelin, whose promenade windows run as far aft as her engine cars, it is as long but
lower and narrower, some 11 high under the windows along her decks, ahead of her forward engine cars. A Picture Board at
the aft end of the upper deck chooses what both show, a little copy of the screens over its six painted buttons, since
they cannot be seen from inside: Posters, one at each press of four travel posters with the zeppelin over the Nether,
the End, the jungle and the frozen peaks, and four adverts (Creeper Insurance, the Villager Bank, Golem Guards and Cake
by Air), each under its headline; Colours, the banner flying from her flagstaff, as a flag; Map, a filled map handed to
the board, so a player's own map art is shown on her side, handed back at a second press; Faces, the faces of everyone
aboard, a few big or more smaller, a page at a time; Flight, her height and speed in huge figures, an arrow green as she
climbs and amber as she sinks; and Off. A new picture flickers on with a click. The server config's `flankScreen` leaves
the screens and the board off zeppelins built from then on; a Picture Board is crafted from three glass panes over
redstone, glowstone dust and a stone button, over three dark oak planks.

Each engine car is fitted out for its engineer: a grating along its floor, a lamp in its roof, and at its forward end an
Engine Repeater, the control car's telegraph's twin, a climbing rope hanging from a hatch in its floor to a little over
the ground to climb up into it, and to ride in it in flight. Each new order for its engine swings the repeater's handle
to it with the double ring of its bell, its lamp red, and the engine runs on as it was until the order is answered: use
the repeater and its lamp turns green with the clunk of its handle, and the engine does as it is told. Flown by hand
from the control car's telegraphs, an order waits a few seconds for an engineer, ringing now and then, before the engine
crew answer it themselves; under the autopilot the crew answer at once, so its flying never waits. The server config's
`engineRepeaters` leaves the cars bare on zeppelins built from then on; an Engine Repeater is crafted from a bell over a
gold ingot over a copper ingot.

Under the gangway hangs a trim car: a heavy iron ballast car on a 120-block rail of girders, a winch at its forward end
hauling it fore and aft a few blocks a second, its wheels rumbling over the rail's joints with a clink of chain, heard
softly at the trim wheel too. Moving it moves the zeppelin's weight, and the
whole ship pitches: wound right forward she dips her nose some five degrees before her burners take it up. A Trim Wheel
by the control car's starboard windows says where the car goes, all the way one way the forward end of the rail and all
the way the other its aft end, and a Bubble Level beside it shows her pitch, its bubble sliding toward whichever end is
higher. Under the autopilot the car is rolled a little at a time toward the end that eases the split of hot air between
her forward and aft cells, the wheel turning to show it; at speed it mostly rests at the forward end, her burners
holding the rest. The server config's `trimCar` leaves it off zeppelins built from then on.

In the observation lounge at the back of the control car, by its port windows over the glass floor, stands the Lookout's
Glass: a long brass glass on a teak post, at your chest and pointing out of the windows. Use it and you look through it,
out the way it points and down onto the land, through the windows or the glass floor, wherever you stood, the land round
and under the ship brought ten times nearer inside a spyglass's frame. Roll the mouse wheel to zoom further in, to
twenty and forty times nearer, or back out to four, a click at each step and a row of gold pips under the view lit as
far as it is zoomed; the nearer, the slower the view turns. A green pennant at the middle of the view stands on the land
you could mark, a red cross where there is nothing to mark: sky, the ship's own hull in the way, or land too far off
(256 blocks at most) or not loaded yet. Use it again to mark the spot: a column of white smoke rises from it, the ship's
bell strikes twice, and a pennant with your face on it is dropped on the autopilot's map there, saying who spotted it
when pointed at, to be clicked as the pin like a player's face. Each lookout has one flag, moved by their next mark,
four at most stand at once and each comes down after ten minutes. Alone aboard while the autopilot flies, your flag is
made its pin straight away, as nobody else is there to pick it. Step, jump or sneak to step back from the glass. The
server config's `lookout` leaves it out of zeppelins built from then on, and off, the glasses already built mark
nothing.

Taking off from the ground, she is held a moment on her lines, as the Hindenburg was at Lakehurst. Once her take-off
announcement is over, so that nobody talks over it, the ship's bell rings, a short blast of the ground officer's whistle
bids her handlers hold fast, her bow's water ballast pours out of her belly in a stream with a drumming roar, and on his
call "Up ship!" and a long blast of the whistle, the lines are let go together and she rises. The handlers and their
officer are heard but not seen. A zeppelin already floating rises at once. The server config's `upShip` switches it off,
and leaves the Water Ballast Valve under her gangway out of zeppelins built from then on.

Passenger screens look through her outside cameras, as an airliner's do: one before each armchair on her promenades,
standing on a teak post at the eyes of whoever sits in it and turned to them, and one on the control car's back wall
beside the Ship's Plaque. Use one and you look out of her bow, ahead and a little down; use it again for the camera under the control car,
looking straight down at the land she flies over; and again for the flyby camera, circling the whole ship from 240
blocks out and 60 above her, once in two minutes, so she slides past broadside, her poster screens, engine cars and fins
and all. Use it once more, or sneak, to come back. The client config's `flybyCamera` leaves the flyby camera out, and
the server config's `zeppelinScreens` leaves the screens out of zeppelins built from then on.

Two searchlights hang under the forward corners of the control car, black drums with big glass lenses, and their two
handles stand in the car's lounge over the glass floor, one for each side. Use a handle to take it and its light
switches on with a click and swings to wherever you look: a cone of warm light runs down from the lens to a glowing pool
on the land, plain to see at night from the car or the ground, and the lit lamp lights up the car's underside. Use the
handle again to let go, the light left shining where it points, or use it sneaking to switch the light off. Walking away
from a handle lets it go too. The server config's `searchlights` leaves them out of zeppelins built from then on, and
off, the handles already built do nothing.

On the control car's front wall, under the windows beside the rudder wheel, glows the red Speed Boost Button, a
fast-forward arrow on its cap. Press it while her autopilot flies her along and the boosters round her tail fire for ten
seconds with a roar, streaming blue plasma behind her: she gathers speed, to some three times as fast, on the heading
she had and at her height, her engines as they were; then she slows back to her set speed, and the button rests ten
seconds before it boosts again. At any other time it only sounds a low note. The boosters push along her length a little
over her axis, where the hull's drag centres, so her nose stays level rather than rising and lifting her, as it would
with her engine cars, which hang below it, run full ahead. The server config's `speedBoost` leaves it out of zeppelins
built from then on, and off, the buttons already built do nothing.

Zeppelin Post: a red Franking Machine stands on the control car's floor to starboard, a green lamp on it. Use it with a
sheet of paper and it prints a postcard: a map of the land she is flying over, named for her ("Postcard from" her
christened name, or the Zeppelin), franked with where she was, which fills in as you hold it. Use it with an empty hand
while she flies over a village and a mail sack drops from under the car on a parachute, landing among the houses with a
thump and a flutter of letters while the village bell rings for the post, loud enough to be heard aboard, and the
villagers sparkle; its lamp goes dark for half a minute before the next. Away from a village, or while it rests, it only
sounds a low note. The server config's `zeppelinPost` leaves it out of zeppelins built from then on, and off, the
machines already built do nothing.

Low on the control car's back wall, to starboard of the ladder, a brass Banner Cleat holds a coil of tow rope. Use it
and the rope runs out with a squeak and a flap of cloth, and a long banner streams from the tip of her tail on the rope:
her christened name in big black capitals on white sail cloth between red edges, or "AEROLARGE" before she is
christened, reading the right way round from either side. Still, it hangs straight down; the faster she flies the
further back it streams and the harder it flutters, nearly level behind her at her cruising speeds, so its angle shows
how fast she goes. It is only out while her tail is well clear of the ground under it, so it never drags on the land as
she comes in. Use the cleat again to haul it in, the rope coiled back round it. The server config's `towedBanner` leaves
it out of zeppelins built from then on, and off, the cleats already built do nothing.

The sky crane's Crane Winch stands at the aft end of the observation lounge's glass floor, to starboard: an iron frame
round a drum of rope, a lever with a red knob at its side and a load dial over the drum. Use it and the lever goes up a
notch, and sneaking, down one: down lowers the hook on its rope through the glass floor, level holds it, up hoists it,
the drum turning and ratcheting as it does, and the lever comes back level by itself when it is done. Looking down
through the glass you see the hook, a black weighted ball over an iron hook, swinging under her, trailing back on its
rope the faster she flies. Lower it onto a horse, a donkey, a llama, a cow, a pig, a sheep, a goat, a boat or a
minecart, and it catches it with the clunk of its latch and a jingle of chain, heard at the winch however far down it
is, in rope slings; onto a chest, a barrel or a care
package, and it takes it up in a net with everything inside. The hook catches anything within a block of where it
comes down, as she drifts a little while hovering. While it holds a load the dial's lamp glows green, and its needle
shows the strain: in the green for a pig or a boat, the amber for a horse, swinging into the red if the load swings
hard. A load too heavy for it, a camel, it catches but will not hoist: the lever drops back with a low note. Lower a
load until it rests on the ground while she hardly moves, and the hook lets go with a clank, standing a chest down
where it rests with all it holds; it then catches nothing until it has been hoisted a few blocks clear. It never
catches a player, a villager or a monster, nor a tamed animal unless its owner is aboard, nor anything a player rides,
and a load a player climbs onto is only ever lowered. A chest is taken only where whoever works the winch could break
it. The hook weighs nothing on her flight. Should the winch be taken away, the hook lets its load down gently, an
animal floating down slowly and a chest coming down on a parachute. The server config's `skyCrane` leaves it out of
zeppelins built from then on, and off, the winches already built do nothing.

The flying carrier, experimental and off until switched on with `flyingCarrier` in the server config: a full-size
zeppelin built then has a flight deck along her crown (a half-size one has none), forward of the Sky Deck where her
crown runs level, a 15 by 60 strip of dark non-slip deck with white lines along its edges and a dashed line down its
middle, bulbs along its edges to find it by at night, four docking clamps set into it, yellow jaws lying flat with a red
lamp between, and a yellow Clamp Release Lever at its forward port corner. Land a plane of your own on it (one built
with Create Aeronautics, or anything else Sable flies), coming in over her bow while she hovers or creeps along, and
once it has rested on the deck a second the clamps lock it down with a hiss of their rams and a thunk, their jaws raised
and their lamps green: it rides with her however she flies, turns or climbs, and every two seconds its tanks are topped
up with the fuel they hold (whatever the modpack counts as fuel, and water for steam engines) and its batteries filled,
with a gurgle. A plane heavier than a tenth of her is refused with a low note. Pull the release lever down, with a hiss,
and every clamp opens and stays open so the plane can take off; push it back up and they lock the next plane that rests
there (before her first flight it only gives a low note). Locked planes stay locked through a save. Off, the decks
already built hold nothing.

On the starboard wall of the control car's lounge hangs the Skywriting Board, four picture buttons drawn in white on
blue: a circle, a heart, a figure of eight and a wave. Press one while her autopilot flies her along (not while she
takes off or lands) and a chime sounds, its button lights up gold, she slows to 4 blocks a second and her autopilot
flies that shape across the sky, a few hundred blocks across, while coloured smoke streams from under her belly, puff
after puff in her gas cells' colours, drawing the shape behind her. A circle takes about four minutes, the wave five,
and the heart or the figure of eight about eight. Done, a bell rings and she flies on along the heading she ends
on, at her own speed again. Press the lit button again, or set her autopilot afresh or switch it off, to give the shape
up, with a low note; the same low note answers a button pressed while she is not flying along. The smoke glows at night
too and fades away a quarter of an hour later, in the order it was drawn. The server config's `skywriting` leaves the
board out of zeppelins built from then on, and off, the boards already built do nothing.

And somewhere aboard is a secret. One length of the gangway's grating towards the stern is a Secret Hatch, just like the
rest of it but for a musical note drifting up out of it now and then: use it and it swings open, a rope leading down
through it into a black room hung under the gangway, with no other way in. It is a techno club: a dance floor of black
glass tiles, big speakers along both walls, a disco ball and a DJ Deck at the forward end. The moment you step in, a
minute-long track starts, 128 beats a minute, played from the room as the zeppelin flies, and the floor lights up two
tiles at a time, pink and blue changing on the beat; the build quickens, the bass drops out, half a beat of silence, and
then the drop: a huge sub-bass boom, the camera shaking with it, the ceiling flashing white on the beat, beams of
coloured light sweeping the room and every tile flashing its own colour each half beat. It loops for as long as you stay
and fades away as you climb out. On a half-size zeppelin the room is a little shorter, 11 blocks long inside rather than
15, to fit between the rings there. The server config's `technoRoom` leaves it out of zeppelins built from then on.

Another rope climbs into a balloon. At a cell just forward of the middle, one of the biggest, a gap in the gangway's
rail leads to a caged rope up through an open mouth in the cell's floor into its hot air, to a little railed gallery
among its glowing burners, lit at its corners. The hot air stays put over the open mouth, as it does over a hot air
balloon's open throat, so the cell lifts as before.

How it flies: the zeppelin is lifted by real hot air. Each cell holds hot air burners, and the hot air they fill it with
lifts it, as Create Aeronautics' balloons do, the more the harder they burn and the less the thinner the air, so the
zeppelin rises until the thinning air balances it and floats there, at a height the burners set. Sable flies only what
touches the rest of a body, and a cell standing free in its bay would float off on its own, so the cells hang from the
crown on girders, the burners and their relays stand on girder posts down to the cells' floors, the gangway stands on
posts down to the rings, and the decks' floors run out to the cover. The fabric weighs a tenth of a block (its own
`physics_block_properties`, over the airframe's `#sable:super_light`), so a ship of this size, some 32,000 of Sable's
units, is carried by its burners, over a hundred of them, a dozen in each of the biggest cells: burning flat out they
would lift it half again as much as floats it at sea level, so it flies high over an ordinary world's hills with plenty
in hand. The burners are built with their pilot flames lit, and the relay beside each sets how hard it burns. The
engines' creative thrusters (Create Propulsion's, as the airliner's) sit in the engine cars round the propellers' hubs,
eight pushing ahead and two astern in each, without flames: an airship's engines turn propellers. Without Create
Aeronautics the cells are made of the zeppelin's own fabric and it is a building; `burnerBlock` and `envelopeBlock` in
the server config's `zeppelin` section name the blocks it is built with. Create Aeronautics' burners puff smoke as they
burn, which the game would draw whatever its Particles setting says, a good share of each frame with so many burners
near: any hot air burner's smoke, hers among them, here follows the Particles setting as other particles do, every puff
at All, two in three at Decreased and none at Minimal. The client config's `burnerSmoke` sets it otherwise: `ALL` for
every puff whatever the setting, as Aeronautics puffs them, or `NONE` for none. Their flames are drawn either way.

Its control car is its flight deck, laid out as the Hindenburg's was: the rudder wheel at the front windows, the
autopilot's control head behind it between the two engine telegraphs, a flight display and the transponder on the low
wall under the windows, the elevator wheel and the burner lever along the port wall, looking out of its windows, and the
flight map on the back wall by the ladder. The transponder shows the zeppelin on the maps and the tower's radar as a
white airship. Use the control head to open the autopilot's settings, the airliner's own screen, its speed dial running
from stopped to 25 blocks a second. Its pictures show the zeppelin wherever the airliner's show the airliner: in the title
bar, at the foot of the compass, in the turns and the sky, and on Take Off, which lifts it off the grass with an arrow
up, and on Land, which settles it with an arrow down, with no runway in either. The control head's own screen, the pilot
helmet's HUD and the flight display's standby picture show it too. The zeppelin is too big for the Physics Assembler, so it is not glued: engaging the
autopilot assembles it through Sable, every block of its own and everything built inside its hull, but not the ground
under it or the trees beside it.

The hand controls are ship's controls, read without words. The **Rudder Wheel** and the **Elevator Wheel** are big
wooden wheels on brass pedestals, the top spoke red so their turn is seen at a glance: use one to turn it a notch
clockwise, its spokes knocking and its steering chain rattling, sneak and use it to turn it back, four notches either
way; past the last a dull thud says it will go no further, as it does for a telegraph, the burner lever or the crane's
lever at their ends. Clockwise, the rudder wheel turns the zeppelin to
starboard and the elevator wheel tips its nose down. Each **Engine Telegraph**, the port engines' on a red plate and the
starboard engines' on a green one, has a dial of five orders in colours and pictures: astern (a blue arrow pointing
back), stop (a red bar), slow, half and full ahead (one yellow, two lime and three green dots); use it to ring it a step
ahead, sneak and use it a step back, its brass gong struck twice, ding-ding, a little higher for a faster order; an
engine car's repeater rings the order the same way, and rings back once, lower, when it is answered. The **Burner Lever** stands beside a flame painted from a
small blue one at the bottom to a big red one at the top: pushed up the burners burn harder and the zeppelin climbs,
pulled down it sinks, and in the middle it holds its height. While the autopilot flies, it moves the controls itself to
show what it does, ringing the telegraphs as it changes them: how hard it climbs or sinks on the lever, how it trims the
nose on the elevator wheel, its engines on both telegraphs and its turns on the rudder wheel. Switched off in the air,
it hands the zeppelin over to them, the lever in the middle and the engines stopped, so it floats where it is; parked,
it flies by them as soon as someone moves one, once it has flown under the autopilot. Two telegraphs rung differently
turn it, as the rudder does; with both at stop and the rudder hard over, it turns on the spot. Up high, where the air is
too thin for the burners to lift it further, the lever and the elevator have nothing more to give. With nobody aboard
for a minute, the autopilot comes down where it is, flown by hand or not.

A Horn Cord hangs from the control car's roof to starboard, just aft of the control head. Pull it and the zeppelin's
horn sounds, a deep, snarling blast like a warship's, heard some 300 blocks off, from the ship as it flies; the
villagers and animals on the ground under it turn and look up at it for a few seconds. In flight its echo comes back up
off the ground, the later the higher it flies, a second for every hundred blocks or so, so the height is heard, as an
airship's echo altimeter told it by the echo of a bang. The server config's `horn` leaves the cord off zeppelins built
from then on; one can be crafted from two string over dark oak planks and hung anywhere.

Under the camera display, just over the floor on the control car's starboard wall, stands the Livery Selector, a wooden
board four blocks wide with six pictures of the zeppelin on it in two rows of three, each painted in one of her liveries
just as she would be: Hindenburg silver, Graf Zeppelin cream and navy grey; the airliner's colours (white, with a blue
cheatline down each flank and its fins and engine cars blue), polar red with white fins, and midnight blue scattered
with gold stars. Use a picture and the zeppelin is repainted in it, on the ground or in flight: with the scrape of a
brush the new paint sweeps from the nose to the tail over a minute, a painters' cradle on each flank riding its edge, a
plank platform with a rail and a pot of paint slung on ropes over the hull from the crown, and the camera display over
the selector shows it happening; the selector frames the livery the zeppelin wears in gold. The paint is only drawn, so
no block changes and the zeppelin flies as before. The server config's `liveryBoard` leaves the selector off zeppelins
built from then on, which keep their paint. Zeppelins built before it keep their Livery Board, six painted swatches on
one block, which repaints her the same way. A Livery Board can be crafted from a red, a yellow and a blue dye over three
dark oak planks, and a Livery Selector from a Livery Board between two dark oak planks.

Over the Livery Selector, at eye height on the same wall, hangs a camera display like the airliner's flight deck
one, but four blocks wide and two tall, showing her outside cameras live: the flyby camera at first, circling the whole
ship, so whoever repaints her watches the new paint sweep over her, painters' cradles and all. Three buttons along its
bottom left pick the camera, each a little zeppelin seen from the side with a big dot where its camera is: sky blue
over her for the flyby camera, orange at the bow and green under the control car. The one shown is lit white with a
white rim, its name is on the picture's label beside a camera in its colour, and everyone sees the camera picked. The
flyby camera's picture reaches past the far end of the ship, so none of her is lost in the fog, but beyond your own
render distance the land is not drawn. Until she first flies the display shows its test card, a zeppelin seen from
behind, and ASSEMBLE TO FLY, then the green zeppelin. The client's `tailCameraFeed`, `tailCameraFps` and
`tailCameraResolution` settings apply to it as to the airliner's, and the server config's `cameraDisplay` leaves it
out of zeppelins built from then on.

Engaged on the ground, it weighs off and rises straight up, a couple of blocks a second at most, its burners roaring up
until it floats, then a little more; its engines start once it is 15 blocks clear of the ground, and it climbs on to the
height set, 60 blocks above the ground to start with, where it levels off. It goes no higher than its ceiling, where its
burners near full can lift it no further in the thinning air: worked out from its mass once it is assembled, the
settings screen's height stops there, so a heavily built zeppelin flies lower. It holds the height by its burners alone,
working out from its mass and the thinning air how much hot air floats it, and learns what more its trim needs; it holds
its nose level by burning the cells forward of its middle lower and those aft higher, or the other way round, and learns
the split that holds it. Its propellers drive it at the speed set, ahead or astern, and it turns by driving the port
engines harder than the starboard ones or the other way round, astern on one side and ahead on the other to turn on the
spot, a quarter turn in about half a minute. Set to fly to a pin it steers its middle for it, slowing as it comes near,
and hovers with its middle over it; with the pin off its nose it slows down more, and with the pin abeam or behind it,
overshot say, it stops and turns on the spot to face it first. Set to land there, it comes down along its approach,
never higher than a gentle glide down to 20 blocks over the pin, and the rest of the way once it hovers, holding itself
over the pin all the way. Told to Land, it stops, holding the way it points, and sinks straight down, briskly from high
up (a block and a half a second) and gently near the ground (a block a second). On the ground it rests on its wheels,
its burners holding most of its weight, its engines stopped, and switches itself off with a bell. With nobody aboard for
a minute it comes down where it is. Still on the ground a minute after it was engaged, too heavy for its burners (built
over with too much, say), it rests again and switches itself off with a thud, saying so; built without Create
Aeronautics, it has no burners at all, and its autopilot says so instead of engaging.

Nobody is hurt falling from a zeppelin: a striped safety canopy, the parachute's, opens over anyone who was aboard a
moment ago and has fallen a few blocks, off the boarding stairs as they fold away under her, say, or from the gangway
inside the hull, with the snap of a parachute opening. They glide down as under a parachute, steering the way they walk,
and it packs itself away as they land. It does not open in creative mode, while flying or under a parachute of one's
own; `safetyCanopy` in the server config's `zeppelin` section switches it off.

A zeppelin is far longer than the land simulated round a player reaches (12 chunks by default): someone riding in its
bow leaves its middle in land unloaded behind them, and Sable loads a ship back only once the land under all of it is
simulated, which for a zeppelin it never is. So once it has been assembled it keeps itself loaded for good (a loading
ticket of Sable's), flying or parked, with anyone aboard or not, wherever the players are; it lets go only when its
control head is taken away. That costs a parked zeppelin's ticking, a little, wherever it is.

The zeppelin's own blocks are in the Aerolarge creative tab under Zeppelin: its fabric, its girders, its catwalk
grating, the propeller (placed by hand, it pulls the way you look, its blades turning toward you, and spins with the
strongest redstone signal beside it), and the hand controls (placed by hand, each faces whoever places it, and a
telegraph rings for the port engines, on its red plate; they fly a zeppelin only where its blueprint laid them).

### The rocket

A rocket standing on four fins, as tall as the airliner is long at the scale the server config sets (145 blocks at the
default 2) and 20 blocks across, a pointed nose on top with a band of smoked, nearly black glass round the cockpit under
it, cut to the nose's curve. The Rocket Blueprint builds one just past where it is used, its front, with its door,
facing you, and operators with `/aerolarge build <pos> <north|east|south|west> rocket`. It is driven by Create
Propulsion's creative thrusters, which need no fuel: five bells of them in its flat bottom, their plumes trailing as
they fire, and two rings of four side jets in its skin, near the nose and near the bottom, that hold it upright. Its
fins are its legs; between them a crew ladder, the pool's chrome ladder up a column beside the door, climbs to a step in
front of the doorway. Inside, over an engine room, stories stand one above another round a lift shaft of dark, smoky
glass up the middle, a doorway into the shaft on each and a big oval window out to each side and the back (and the
front, but on the entry story, where the door is, and on the observation deck, which has a band of glass all round
instead), and the cockpit sits in the glass of the nose, the autopilot's control head facing the front between two
flight displays, two pilot seats behind it (the server config's `rocket.rooms` leaves them out with her rooms). The
windows' glass bulges out of the skin in a dome, the sun glinting off it, and sits inside in a recess lined with steel;
it dims electrically: use one to darken its glass a step, from clear through light and medium to dark, and once more to
clear it again, a chime falling as it darkens.

A lift rides the glass shaft from the entry story to the cockpit, a steel girder up each of its corners (Steel Girder
and Lift Glass are in the creative tab after the Halo Light, and can be crafted), where the shaft runs on up into a
glass booth with the lift's machine on its roof, a brushed-steel casing banded with hazard stripes (the server config's
`rocket.lift` leaves it out). By each landing's sliding doors, and on the shaft's back wall inside the car, a picture
board, its back brushed steel where it shows through the shaft's glass, lists her floors as she stands, the lowest at
the bottom: each an icon on its story's colour (a doorway for the entry, then a crate, a fork and knife, a bed, a
sprout, waves, a note, a flask, a telescope, and her nose for the cockpit), the board's own floor framed in white and
the car's lit. Touch a floor and the car comes, its doors slide open with a soft rush, you step in, and after a moment
it closes and glides there, easing off and in, at up to six blocks a second, while its motor hums; one chime as it comes
in going up, two going down, and the doors open to let you out. As it rides, lift music plays softly in the car, a bossa
nova on an electric piano over a plucked bass and a shaker, heard in the car and right by it and fading away as it comes
in; the Jukebox/Note Blocks volume sets how loud, and the client config's `liftMusic` turns it off. It never closes on
anyone in the doorway or moves with a door open, and no one gets off between floors; while its doors stand open at a
floor, sneak to step out of it. While she counts down, flies or lands, it stands at a floor with its doors open and
takes no rides, and an amber bar lies across its boards; in place of her floors they show how high she flies, a little
rocket climbing a column of sky in the colours of each height, from the sea at its foot to her ceiling, past a dashed
line with a star where space begins, stars twinkling over it once she is up there (the client config's `heightGauge`
keeps her floors on them).

Each room is finished inside its own way, on the walls her cover shows inside, along their foot, on its floor and on its
ceiling, the floor of the room over it (the server config's `rocket.finishes` leaves her plain cover and grey floors):
the entry riveted steel over a hazard-striped dado on studded rubber under a perforated ceiling; the cargo bay plywood
over a steel kick plate on diamond-plate tread under corrugated sheet; the mess oak boards over a walnut dado on
herringbone parquet under cream plaster; the bunk room quilted blue on teal carpet under a night ceiling of faint stars;
the greenhouse whitewashed brick over terracotta; the pool blue mosaic; the club black acoustic foam under a black
ceiling pricked with pink; the lab sage-green tiles over white on flecked vinyl; the observation deck walnut panelling,
a brass line along its top, on dark parquet; and the cockpit, up into her nose, dark grey padding. Her livery paints her
only outside. Round every room but the observation deck, eight wall washers over head height throw fans of light up the
wall, warm white, a cool blue in the bunk room and pink in the club. They and her halo lights are one light, the Halo
Light in the creative tab, which takes its shape from where it is put: under a ceiling or a light cable it hangs as a
halo ring, against a wall it is a wall washer, and on the ground a floor light, a low dark drum with a ring of light
round its top shining up. Use one to switch it from warm white to night blue to pink, with a soft click; broken, any of
them gives back the Halo Light. The pendant light and the gym rings go up only under a ceiling too, so nothing stands on
the floor with its cable up in the air.

Each story is furnished as a room of its own (the server config's `rocket.rooms` leaves them bare), its furniture round
the shaft, clear of a walkway round it, of the way out to the front, of the windows' fronts and of a ring along the
skin. Under every room's ceiling but the observation deck's, steel girders run out from the shaft along the four ways to
her wall, and under them, over everyone's heads and in nobody's way, hangs something of the room's own: mission-patch
banners in the entry, each a round embroidered patch on navy cloth, a rocket climbing past the moon or a ringed planet;
chain hoists on trolleys riding the girders in the cargo bay, a yellow hook on each; racks of copper pots and pans in
the mess; warm fairy lights looping along them in the bunk room; hanging baskets trailing green in the greenhouse;
bunting in the pool's colours over the pool; stage spotlights in the club, angled down, throwing cones of pink and cyan
light; and in the lab a ladder of cable tray, bundled cables of every colour along it (Girder Decoration, in the
creative tab after the Shaft Decoration, hangs a chain hoist; broken, each keeps what it is). The entry story is in two
halves. At its front, either side of the way in from the door, is a kit-up room: a
parachute rack by the door, for anyone who would rather leave her in flight; crew lockers round the corner, a Space
Helmet waiting in each (see Souvenirs) and one of them left ajar; a mudroom bench facing a low wall of boot shelves,
boots on the lower shelf and helmets on the upper, which parts the kit-up room from the back of the story (with a gap to
walk through in a big rocket), a model rocket left on top; and somebody's suitcase dropped by the bench or the lockers.
By the door on her left stands the Airlock Panel, a screen on a post showing a shut door under a red bar: use it and the
latches clunk, air hisses out of its vents in a cloud and amber chevrons sweep along the bar, then a chime rings and the
door on the screen slides open under a green bar, clear to go, until a while later it seals itself again with a clunk
(it is only to play with: the door itself is always open). At its back, either side, is a waiting lounge for the lift:
red or orange armchairs round a low table on a rug, one of them turned away as if someone just got up, and bamboo in a
planter. Halo lights hang on slim cables at the four diagonals round the shaft. The cargo bay over it is a storeroom. At
its front stand a pair of baggage containers by the way in from the lift and care-package crates stacked unevenly beside
them, ready to be packed and dropped; out to either side a wall of pallet racks three bays high, loaded with taped
boxes, drums and crates and now and then a bay left empty, parts the front from the back (with a gap in it in a big
rocket); at the back right is a workbench corner, a crafting table, a stool and a grindstone under shelves of tool
cases, and at the back left crates are piled where they were set down. By the way in on its left stands the Parcel
Scanner, a conveyor under a striped gantry with an empty crate waiting on its belt: set a crate on it, or use it, and a
red curtain of laser light fills the gantry with a rising whirr while a line sweeps down the crate on its screen, then a
bright double beep and a green tick if something is packed in the crate, ready to drop, or a low buzz and a red cross if
not. Halo lights hang round the shaft as in the entry, and the middle of its back is left to the rover (below). The
galley and mess has its kitchen at the back and its dining room at the front. Galley units curve round the back either
side of its window, an oven in each run and a coffee machine; out to either side a pass of worktops parts the kitchen
from the dining room, bar stools before it, a shelf of jars on it and a cake or a pot of tulips beside them; and in a
big rocket an island stands in the kitchen. Toward the window's end of the galley's right run stands the Galley Fridge,
a blue light by its handle: use it and its door swings open with the pop of its seal and the hum of its cooling, its
inside lit up, shelves of milk, cheese, greens, jars and bottles; it holds three rows of things, and comes stocked with
milk, apples, carrots, melon, chicken, sweet berries, pumpkin pie and cookies. At the front right a long communal table
runs out from the way in, its chairs every kind and colour, armchairs, stools and bean bags, a few places left empty; at
the front left is a smaller table with its own odd set. A halo light hangs over each table. The bunk room has bunk beds
two high at each of the four diagonals, their reading lights on, a blue halo light hanging over each pair; along each
bunk a rug of its sleeper's own colour, by each pair the sleepers' things, a suitcase and now and then a bean bag, and
crew lockers by the way out and by the back window, a Space Helmet in each. Out to either side a wall of bookshelves,
one high here and two there, parts the front pairs from the back ones, and behind the shaft a reading nook looks at the
solar system poster (below): an armchair between two low bookshelves on a rug, a blue halo light over it. The greenhouse
is a kitchen garden someone tends: paths out from the walkway to its three windows cut it into four quarters, and in
each, raised beds in spruce frames stand in rows a path apart, turned a quarter from the quarter before, no two rows
ending alike. They grow carrots, potatoes, beetroots and wheat at every stage, sometimes two in one bed, berry bushes,
pumpkins and melons on their vines, or flowers, with a gap here and there where something was picked; a magenta grow
light hangs over each on its cable, and sprinklers stand in water among them, keeping their soil dark and moist.
Beside the way out from the lift stands the irrigation valve: turn its red wheel and it squeaks open, its sight glass
glowing blue, and every sprinkler pops up and sweeps a hissing, ticking jet over the beds for eight seconds, green
sparkles showing where a plant comes on a stage. A cherry and an azalea grow among the beds (two cherries in a bigger
rocket), a composter, a water butt and a barrel or a potting bench with pots on them stand in a corner, herb pots and a
green bean bag on the spots left over, moss grows over the paths in patches, and glow berry vines hang from the ceiling;
the pool's channel hangs into its ceiling, its bottom glass, so swimmers are seen overhead from among the beds. Garden
beds, sprinklers, irrigation valves and grow lights are in the creative tab and can be crafted, for a kitchen garden
anywhere: a bed's frame runs round however many beds stand together, it never dries back to dirt or gets trampled, and a
valve sets off every sprinkler within 24 blocks, two rows up or down at most. The swimming pool is a channel two deep
curving round the shaft by way of her back, lined with glass mosaic lit from under the water line, coped round its edge,
with rubber ducks afloat on it and deck tiles all round. The deck at the front, between the channel's ends, is the
sunbathers': bean bag loungers on her right looking out of the front window, a side table between the first two and one
turned toward the water, a shelf of rolled towels on her left by the way in from the lift, and bamboo in planters, the
pool's palms, by the channel's ends, and in a big rocket an orange lounger out at the deck's corner. Beside the towels
stands the Wave Machine, a white post with a big blue button, a wave drawn on it: press it and the pumps start with a
rising whine, the button sinks and glows, its wave rolling, and for ten seconds the whole channel churns with splashes
and bubbles to the sound of waves washing. Both are heard as you stand in them: the air through the greenhouse's leaves
over a trickle of irrigation, a bee now and then buzzing past, and the pool's water lapping in its tiled hall, now and
then slapping against its side (the client setting `roomAmbience` keeps them quiet). The techno club has the dance
floor's black glass tiles round the shaft, lit two by two in pink and blue to the beat while its music plays, and past
them out to her skin a plum club carpet dotted with pink; speaker stacks at the diagonals, four disco balls and the DJ
deck behind the shaft playing the whole room. Beside the deck sits the Fog Machine, a black flight case, its little
light green: press it and it hisses and pumps a stream of thick fog out of its glowing nozzle for five seconds, the fog
rolling out over the floor behind the shaft and hanging in the disco balls' light; then its light turns amber while it
heats up again, and a beep says when it is ready. Along her left side a straight bar stands across the window there: its
counter, stools before it and two Drink Taps on it; behind its middle, his back to the window and the stars, the Robot
Bartender; and either side of the window the back bar, three tiers of shelves of bottles of every shape and colour lit
from below (the airliner lounge's back bar has them too). At the front right is a booth, purple and magenta armchairs
round a low table; at the back right two Cocktail Tables with stools; at the front left, by the bar's end, a coat corner
of lockers, a Space Helmet in each; and round the wall over head height hang neon signs: a martini glass over the back
bar, music notes over the window behind the deck, a lightning bolt by the bar's end, lips over the booth, a planet over
the tables and a rocket over the coats. The Robot Bartender is a chrome and white android two blocks tall on a pedestal
glowing cyan round its foot, polishing a glass, its head turning with a servo's whirr toward whoever comes near and its
cyan visor blinking now and then (craftable, and put down it faces you, so it can stand behind any bar). Use it and it
greets you in its robot voice and opens its shop of mocktails, none with any alcohol in it, each shown in its own glass
with its price in emeralds: an emerald buys two Nebula Fizzes, violet-blue and sparkling, for a minute of Night Vision,
two Zero-G Floats, lime under a scoop of ice cream, for 30 seconds of Slow Falling, or two Moon Milks, silvery white
with stars, which clear away effects as milk does; or one Rocket Fuel, fiery orange, for 30 seconds of Speed and Jump
Boost, or one Supernova Sunrise, red, orange and yellow in layers, for five seconds of Regeneration II. Each is a sip of
food and can be drunk whenever. Each sale it answers, shakes a cocktail shaker over its shoulder to the rattle of ice,
pours it into the glass and sets the drink down on the bar. The bar's furniture is in the creative tab by the DJ Deck
and all of it craftable. A Drink Tap stands on a bar counter, three tall neon handles, pink, cyan and lime, on a chrome
tower over a drip tray: use it and the handle you click tips down, and a stream of glowing Star Fizz runs fizzing into
the tray for a second, filling a glass bottle held under it with a bottle of Star Fizz, a sparkling soda to drink (the
empty bottle comes back). A Neon Sign hangs on a wall, no words on it, only a picture bent in glowing tubes in colours
of its own: a martini glass with an olive, a pair of music notes, a ringed planet, a rocket, a pair of lips or a
lightning bolt. Click it to switch it off and on, its transformer clinking and its tubes buzzing as they strike or die
away, and sneak and click it with an empty hand for the next picture. A Cocktail Table is a high round table to stand
at, as high as the counter, its dark glass top ringed with blue LED light under its rim; it is fenced round as high as a
fence, so nobody jumps up on to it from the floor. Its windows are built at their darkest, so daylight does not wash out
the floor's colours. The observation deck, her highest story and her chillout lounge, sees all round
through its band of smoked glass, under the airliner's starlight panels, dimmed so the stars outside outshine them, all
over the ceiling and up the wall from the band to it, so the room over the band is a starry dome, a planetarium. Its
lounge is set out unevenly in groups to talk in, armchairs and bean bags facing each other round low tables on round
rugs, with plants here and there, pairs of bean bags by the glass to watch the land fall away or the stars come out, and
a bookcase tucked under the top of the stairs. Over its back third, from behind the shaft out to her skin, a mezzanine
holds her cafeteria, a glass rail along its edge and carpeted stairs climbing her right-hand wall up to it from near the
lift's door: round the back wall a counter of galley units with the coffee machine, a cake and a pot of flowers on it, a
lit drinks cabinet, tables with bar stools or armchairs, plants, and the Snack Machine, a tall midnight-blue machine
with a lit showcase of bright packets. Use it and its spiral turns with a whirr, its lamp blinking amber; with a clunk a
snack drops into its flap, which lights up green with the packet in it, and using it again takes the snack out into your
hand: a cookie, an apple, a slice of melon, a pumpkin pie, glow berries, a bottle of honey or dried kelp, which one a
surprise. Cabin lights set into the mezzanine's floor glow both ways, over the groups under it and along its edge.

Round the outside of the lift shaft hang four space posters, bright, flat pictures in thin steel frames, with no words
on them: on the entry story, Earthrise, the Earth half-lit over the Moon's grey horizon and the rocket standing there
in front of it; in the bunk room, the fourth story, the solar system, half the sun and its planets in a row, Saturn in
its rings; in the lab, the eighth, a black hole, its burning disk bent over it and under it; and on the observation
deck at the top, a quasar, its jets running up and down the poster into violet clouds. Each hangs on the back of the
shaft, or on its side where something stands in the way there, and flies with her as a part of her; a smaller rocket,
with fewer stories, has only the posters for the stories it has. Nothing bumps into one; break any part of a poster
and the whole of it comes down, and is given back rolled up. Rolled up, the four are in the creative tab, each showing
its picture, and go up on any wall: used on the side of a block, a poster goes up against it, its middle in front of
the block used, if every block of it has open air to go in and a solid face behind it. The server config's
`rocket.posters` leaves them out of the rocket.

Each room dresses the outside of the lift shaft's glass as well, with no words anywhere (`rocket.rooms` leaves it bare
with the rooms). On the observation deck, her chillout lounge, deep blue velvet curtains hang from the ceiling to the
floor before each of the shaft's corner girders, drawn back with a gold rope and tassel a third of the way up, under a
box-pleated pelmet fringed in gold, and the valance's swags dip across the glass between them, so the cold glass feels
like a window in a cosy room; on the front, a curtain either side frames the lift's door. The entry story has a lit
departures board: under a rocket lifting off and a clock, a row for each destination, a clock face for when it goes,
the Moon, Mars, a ringed planet or a space station, split flaps in its colour, its gate's coloured door and a lamp,
green for boarding, amber for soon and red for held. The cargo bay has yellow-and-black rubber guards round the shaft's
corners and a load chart of pictograms, each with a green tick or a red cross: one crate on a pallet and two, but not
three; a crate strapped down, not one sliding loose; a forklift. The mess has a chalkboard menu, a burger, a bowl of
noodles, a slice of pizza, a carrot, a cupcake and a mug of cocoa in coloured chalk, each over the coins it costs. In
the bunk room warm fairy lights are strung across the glass over head height, strands of them hanging down; in the
greenhouse ivy climbs the corners and trails across the top; at the pool a red and white life ring hangs on a chrome
hook; in the club pink and cyan neon tubes glow from the floor to the ceiling; and in the lab a whiteboard shows a
molecule of balls and sticks, an arrow and a graph climbing past a dashed line, a sticky note with a water bear on it in
its corner. They go only where nothing else stands, a poster, the lift's board or its LED panel, and never in front of
the lift's doorway; nothing bumps into them, so the walkway round the shaft stays clear; and a story she is too small
to have has none. Shaft Decoration is in the creative tab after the Lift Glass: used on the side of a block, it puts up
a velvet curtain tied back against it. Any piece picked with the pick-block key puts up that piece, and a piece broken
gives itself back as it was.

The lab, her eighth story (the server config's `rocket.lab` leaves it bare, as `rocket.rooms` leaves every room), is
where her tardigrade lives: a water bear the size of an elephant, about six blocks long and three and a half tall, soft
peach with darker folds between the four plump segments of its body, eight stubby legs with pale claws, two dark eye
spots and a small round mouth at the tip of its snout. Its glass tank stands behind the lift shaft, past the walkway
round it, lit softly along its top, moss on its floor with a shallow pool and beds of pebbles. It lumbers to and fro,
rests, turns round where its tank is deep enough to (at the default scale it is not, and it always faces its hatch),
and turns its head to look at whoever is near. In the tank's glass on her right is a feeding hatch at chest height,
and over it a screen of its vitals, its picture and a green pulse tracing across. Use the hatch with moss in hand and
the hatch slides open, the creature comes and munches the moss, bits of it falling, with a crunch at each bite; use it
with an empty hand to reach in and touch it, and it wriggles and gives a soft, low purr, as it does now and then on its
own; while the hatch stands open, moss can go to it straight from your hand. Nothing hurts it, pushes it or leads it
away, and it flies with her as a part of her. Over the top of the world, in space, it curls up into a tun, the dry,
wrinkled barrel real tardigrades live through space in, with a bloop, and its screen goes slow and blue under a
barrel's picture; back under y 300 it uncurls with a purr. Round the front of the room lab counters curve either side
of the way out, microscopes on them, a centrifuge, sample racks with their vials glowing, trays of specimen jars (a
pickled tentacle, an eye and a sprouting potato, each in its own glowing liquid) and scan screens sweeping a line down a
picture of it, a unit here and there left bare to work at, a stool before each microscope, tucked in or pushed back or
aside as someone left it, and two by the tank's far end to sit and watch it from. Look into a microscope and you see
through its eyepiece, a slide magnified in the round field, coming into focus and drifting a little under a fine
crosshair: a water bear paddling, moss green in its gut; algae cells jostling; the bioreactors' muscle fibres twitching;
or diatoms' glass shells shimmering on a dark field. Any click looks up again. Use a centrifuge and it spins up with a
rising whir, its tubes blurring into a disc behind the window in its lid and its lamp turning amber, and a few seconds
later winds down and stops with a ding, its lamp green again.

On her left, round the window there, is the food lab, screened off from the counters and the tank by glazed partitions
in steel frames, frosted at eye height with a green sprout on them: two tall bioreactors glowing pink either side of a
gap left to look out of the window, bubbles rising through their broth round a steak growing on a white rod, and at the
ends an algae column glowing green and a hydroponic rack of lettuces, basil and strawberries under violet grow lights,
the bioreactors and the algae column piped up into the ceiling and their pipes joined along it. Use a bioreactor while
its steak is whole and its cap hisses open in a puff of steam and the steak comes out to your hand, a Lab-Grown Steak,
as filling as a cooked one and in the creative tab too; then the bars on its panel light green one by one as a new
steak grows up the rod, a stage a minute, with a pop of bubbles at each stage and a chime once it is whole again. Used
before that, it only gurgles. On her right is the wet bench: a sink to wash at, cabinets with jars on some, and in the
one nearest the tank sixteen moss carpets to feed the tardigrade with. A bigger rocket's bays are wider, with rows back
to back further in: in the food lab racks, now and then an algae column, another bioreactor, a cabinet or a gap; on the
wet bench lab benches, jars, sample racks, scan screens, a microscope or a centrifuge on them, and stools pushed out
before some. A rocket too small for a tank has the counters and the bays alone.

Against the glass on the cockpit's right stands the large camera display, as in the zeppelin's control car (the server
config's `cameraDisplay` leaves it out), showing her three outside cameras live: the orbit camera at first, circling
the whole of her from well out, sky blue; then up her side, from low by the door to her nose against the sky, orange;
and the landing leg, out on her front right fin's tip, looking in under her at the ground she lands on, green. Its
buttons show where each camera is on an outline of her, and its test card carries her badge.

Under it stands her livery selector (the server config's `liveryBoard` leaves it out), as the zeppelin's does, with
pictures of her laid on her side in her six liveries: the comic-book moon rocket's red and white checks, white with
black roll stripes, silver with a red nose and fins (as she is built), the airliner's white with a blue cheatline up
each side and blue fins, polar red with white fins, and midnight blue with gold stars all over her. The one she wears
is framed in gold. Use a picture and the new paint runs down her from her nose to her foot over a minute, with a
brush's sound; it is a tint, so no block of her changes, and her windows' frames take it too.

Round every story run two rings of LED strip along the inside of her skin, one along the floor and one under the
ceiling, stopping short of her windows (the server config's `rocket.ledStrips` leaves them out, and their panels and
console with them). The slim panel on the other side of each story's lift doors from its picture board sets its strips,
and every control on it is a picture: a swatch for each of ten colours (cherry, tangerine, sunshine, lime, mint, sky,
ocean, grape, pink and snow); a sun for each brightness, a dark ring struck through for off, then bigger and with more
rays for a third, two thirds and full; and a little strip for each way of lighting, doing what it does: steady;
breathing, brightening and dimming in steps over four seconds; chasing, bands of colour running round the floor one way
and round the ceiling the other; and club, pulsing on the techno club's 128 beats a minute, its colour and a partner
colour taking turns. What is set is framed in white, and each touch sounds: a swatch chimes a note of a scale rising
along the colours, a sun clicks higher the brighter it is, a mode blips. Nothing flashes faster than three times a
second. The strips give more light the brighter they are, and cast a soft glow up the wall from the floor and down it
from the ceiling. Each story starts in its own mood, most in the colour of its button on the lift's board, so a child
who presses a colour steps out into it: the entry snow white at two thirds, the cargo bay sunshine yellow, the galley
and mess a warm full tangerine, the bunk room a dim grape breathing like a night light, the greenhouse a full pink grow
light, the pool sky blue breathing like water, the techno club pulsing pink and ocean blue, the lab mint and the
observation deck a dim ocean blue for the stars. Against the glass on the cockpit's left stands the LED console, her
silhouette along its top laid on her side, each story a band glowing as its strips do: touch a band to pick that story,
or the little rocket beside it to pick them all, then use the same swatches, suns and strips under them. Set for all of
her, a change climbs her a story at a time from the entry story up.

To fly it, set a pin on the autopilot's map (or just a height) and fly: its screen has no circle tiles, and beside the
map no circle or landing switch and on it no circle round the pin, as she circles nothing and always comes down on the
pin. It counts down ten seconds, a note each second rising toward lift-off and a bell as the engines light, then lifts
off straight up and climbs to the height set, white cloud and smoke billowing out over the ground from under her as she
rises and again as she comes in to land, and fire and embers bursting from the ground under her while she is close over
it (the server config's `rocket.plumes` leaves them out). Aboard, her engines lighting shakes the view for three seconds
(the client setting `cameraShake` leaves it out). There it holds its height and leans toward the pin, never more than 10
degrees, as fast across as it can still slow down by it; once over the pin and no longer drifting it falls back (or,
circling it slowly for ten seconds, as a heavily loaded rocket can, all the same), never faster than six tenths of
gravity, so whoever stands aboard keeps their feet, and a landing burn catches it, leaning only a little to stop
drifting for the last 20 blocks and not at all for the last 3, touching down at a block and a half a second. Falling
back from space, her base glows and flames stream up behind it. Down on its fins it lets its engines go at once and
switches itself off with a bell. Without a pin it comes back down where it rose. Told to land, or switched off in the
air, it falls back where it is and lands there. Like the zeppelin, a rocket stays loaded wherever it is once its
autopilot has flown it. A Model Rocket, standing on its fins, is in the creative tab.

Her flight is heard as well as seen. Once the cabin has heard its take-off announcement, launch control comes on the
radio with a beep and counts her down in a voice of its own, "ten" to "one" over the cockpit's rising notes, then
"lift-off!" as her engines light with a crack and a deep whoomp and their roar swells in. It stays on the radio
through her flight, calling each milestone once, as a real launch is called, each call a transmission of its own
between two beeps: "Tower cleared." 40 blocks up, "Max Q." 160 blocks up on her way higher, "Main engine
cut-off." as she reaches her height, and "Landing burn." as her landing burn begins, each waiting for whatever else is
being said to end first (the server config's `rocket.callouts` leaves them out). The roar follows her thrust,
louder and higher the harder they push, and carries far across the ground; aboard, it comes through her hull as a deep
rumble wherever you stand. Her side jets puff as they hold her upright. Landing, her fins take her weight with a heavy
thunk and a hiss of their dampers, the flight deck's voice says "Touchdown.", and her engines cut off, falling away
with a pop, her hot bells ticking as they cool.

She can go to space. Her autopilot climbs her far past the top of the world, where the airliner and the zeppelin may not
go and there is only sky, up to y 1,400 (the server config's `rocket.ceiling`); the climb, the hold and the fall back
are as they are lower down, only longer. Her settings screen's sky reaches as high, numbered more sparsely so the
numbers keep apart, and from y 1,000, the edge of space, black space stands over it with its stars, along a thin blue
glow of air, so you can see where space begins.

As she climbs, the sky turns to space round her. From y 400 it deepens through darker and darker blues to all but black
by y 1,200; the stars come out from y 500, by day too, and by the edge of space they are all out, more of them than a
night shows from the ground; the sun burns white, with no sunrise in it; the clouds far below fade away between y 500
and 800, and the rain and snow with them. The land she rose from fades into a hazy blue planet as it falls behind, its
rim curving away under the horizon under a thin glow of air, dipping further the higher she goes. From y 500, where the
stars are out, a shooting star streaks across the sky every four to ten seconds, a white head with a tail fading to pale
blue, gone within a second, and one in ten is a bigger green fireball; from the edge of space they burn up below her
instead, streaking over the planet just under its rim. Space is silent: they make no sound. By night, from y 600 up, the
northern lights ripple along the planet's rim to the north, a ribbon of green shading to violet at its top, swelling and
sinking as bright folds drift along it. Everyone aboard sees the same shooting stars and the same lights. Her camera
display shows it all as its cameras see it. As she reaches the edge of space on the way up, the cabin chime sounds and
her flight deck's voice says "Welcome to space. Enjoy the view.", once a climb. The client config's `spaceSky` leaves
the sky as the game draws it, `shootingStars` keeps its stars still, and `aurora` leaves its nights dark. For ten
seconds as she reaches the edge of space everyone aboard feels light, with a soft rising glide and a puff of sparkles at
their feet: a jump rises three blocks and hangs nearly two seconds, and a fall of seven blocks does not hurt. Then the
chime sounds and her flight deck says "Artificial gravity enabled. Please mind your step.", and with the glide falling
each of them has their weight back as they next land; once a climb, and at once for anyone who leaves her (the server
config's `rocket.lowGravity` leaves it out).

Over her entry story is her cargo bay, and in it, on its cradle facing a door in her back, stands her rover: six wheels
on rocker-bogie legs, a white body in a band of gold foil under a dark blue solar deck, a twin-lensed camera on a mast
and an amber beacon (the server config's `rocket.rover` leaves it out of a rocket built from then on; a rocket too small
for it, as at scale 1, has none and keeps her back window). The panel on the back of the lift shaft's glass, facing the
door, has three picture buttons: the rover over an arrow pointing down sends it to the ground, an arrow pointing up over
the rover calls it home, and a remote hands you a Rover Remote paired with it. Its lamp is green with the rover in its
cradle and the door shut, amber while the door or the winch works, blue with the rover out, and red for a moment, with a
low note, when the door will not open: it only opens while she stands still on her fins with her autopilot off. Then it
folds down outward into a platform, a light curtain glowing red across the doorway so nobody walks out, and a yellow
boom slides out over it; the rover rolls out onto the platform, is hooked to the boom's trolley, lifted, run out past
the platform's end and lowered on the winch's cable to the ground, the winch ratcheting. Use the remote to drive it
(with the rover in its cradle, it sends it down first and takes you to it as it sets off): your view goes to a camera
behind and over it, your movement keys drive and steer it, jump sounds its horn, the mouse swings the camera round it,
and sneaking gives you your own view back, as does being hurt. Meanwhile your own body stays where you stood and takes
no fall damage, and a red warning triangle shows while a monster is near it. The rover whirs over the gravel as it
drives, its beacon blinking, climbs a block as a horse does and goes no further than 96 blocks from her (less on a
server whose view or simulation distance is short, so it never drives out of sight): as it nears the edge the signal
bars in the corner drop, an amber arrow points back to her and a soft tone sounds. Sneak and use the remote, or press
the panel's arrow up, and it drives back under the boom's tip by itself, reversing up the platform once hoisted, and the
door shuts behind it; stuck on the way, it is hoisted from where it stands. It comes home by itself, too, once nobody is
near enough to drive it, and when her autopilot is switched on, her countdown waiting for it to be back in its bay with
the door shut, its beep heard in her cockpit while it waits. A remote works its rover from up to 160 blocks off (less
where the server shows less), and is paired with a rover by using it on the rover or on its bay's panel, its antenna's
tip turning from red to green. The panel and the remote answer every touch in words over the hotbar and with a note of
their own: a bright chime as the rover sets off down or a remote is handed over, a lower one as it comes home, a soft
pling when there is nothing to do and a deep note for wait.

### Settings

The settings can be changed in game under Mods → Aerolarge → Config. The client settings (`cameraShake`, `pilotHud`,
`pilotHudScale`, the tail camera display's `tailCameraFeed`, `tailCameraFps`, `tailCameraRenderDistance` and
`tailCameraResolution`, the cabin's blue `nightLights`, the disco ball's coloured `discoLights`, `cloudlessCabin`, the
rocket's `spaceSky`, `shootingStars` and `aurora`, her lift's `liftMusic` and `heightGauge`, the space helmet's
`helmetBreathing`, her rooms' `roomAmbience`, and the hot air burners' `burnerSmoke`) are there from the title screen.
The server settings open only while a world is loaded, from the pause menu's Mods button while playing in single player;
they are kept in `config/aerolarge-server.toml`, on a dedicated server in its own.

Flying over land nobody has been near, the server makes it as it comes into view, a row of chunks each time you cross
into another one, and while it does, everything aboard can stop for a moment: at a zeppelin's speed, about once a
second. So the server loads the land ahead of anyone flying aboard an airliner or a zeppelin a little before they see
it, `landAheadChunks` chunks past the edge of their view (8 by default), a few at a time while it has time to spare;
`landAhead` in the server config turns that off. Measured aboard the zeppelin over new land, it took a third to two
fifths off each of those moments and most of the longest. Generating the world beforehand, with a mod such as Chunky,
does about as much on its own, and with both the moments came to about two fifths of what they were.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew runClient -PquickPlayWorld=<save>` loads straight into
`run/saves/<save>`; drop Create, Sable, Create Aeronautics and Create Propulsion into `run/mods` to run with them.

The geometry lives in `AirframeShape` (real A380 measurements in metres), is cut into blocks by `AirframeLayout`, with
the upper deck laid round the rooms under it by `UpperDeck` and its staircases, step by step and landing by landing, by
`DeckStairs`, and fitted out by `CabinFitOut`, `TailDen` and `HoldFitOut`, the WC signs put up by `WcSigns`, the
overhead ones once every room stands (each WC sign's block entity looks into its WC, in the airliner's frame through
`SableFlight` once it is assembled); the cabin archways' arches are drawn round by `CabinArchModel` from the faces
`ArchwayFaces` works out, baked once as the models load; the skin follows the curve in `ShellGeometry`'s shapes, which
`ShellModel` bakes (the windscreen's as a single pane, only its outer surface drawn), and is lined inside by
`HullLining`, face by face, as `Lining` says which faces and how each looks, and `Windscreen` says where the glass and
its posts go; `ScreenArc` lays out the flight deck's curved row of screens (`CockpitDisplayBlock`, whose block entity
keeps where each panel stands and what it is linked to, and `CockpitKeys` where its touch keys are, and the shape of the
autopilot's speedometer, steering dial and sky and of the tiller's taxi lines that a touch is read by), whose keys use
the switches standing out of sight in the avionics bay as a player would, and `CockpitDisplayRenderer`,
`CockpitKeysArt`, `AutopilotDeskArt` (the autopilot's controls) and `TillerArt` (the tiller's taxi lines) draw it, with
the pointer that follows the look, as `AutopilotRenderer` draws the control head's display; `AutopilotBlockEntity` flies
(not sending the clients the control jets' relays, which nothing there reads, nor the wing blocks `ControlSurfaces`
turns over), assembling its airliner when engaged on one not yet assembled through `PhysicsAssembler`, which works the
assembler as a Create deployer does, reading the airliner from Sable through `SableFlight`, which also keeps the cabin's
trolleys, basketballs and paper planes among an assembled airliner's blocks while they are inside its hull
(`#sable:retain_in_sub_level`) and puts them out into the world at the airliner's speed once they leave it; the
controls, slides, airstairs and other blocks that work blocks round them keep where those are as offsets from
themselves, which `Turning` turns with them when the Physics Assembler sets the airliner down on another heading, and
mends those an older version left unturned; Sable draws an assembled airliner's block entities every frame wherever the
camera looks, so the busiest renderers leave out what the camera cannot see themselves, as `Sight` tells them where it
stands: `PassengerScreenRenderer` draws a screen only from in front of it, its text laid out once a tick for all of an
airliner's screens and faded out as it gets too small to read, as `FlightDisplayRenderer` and `AutopilotScreenRenderer`
draw theirs only from in front too; `ControlSurfaceRenderer` leaves out the panels wholly behind the camera, as
`Sight.ahead` tells it; `EngineRenderer` draws the intakes in flat sides over the nacelles' faces, from where each
engine's core was told they are, what lies inside the duct only from in front of them, and `ExhaustFlame` the flame
behind each running engine, a picture on strips turned to the camera; `PlugDoorRenderer` draws the hull's doors shut,
swinging and open, from their `PlugDoorBlockEntity`, as the relief `PlugDoorMesh` cuts the panel and its lining into,
only the quads of it that face the camera; `CockpitDoorRenderer` draws the cockpit door's panel the same way, swinging
about its hinge, from its `CockpitDoorBlockEntity`; `AirstairRenderer` draws the airstairs coming out and going away, as
`AirstairFlight` builds and moves them, from their `AirstairBlockEntity`, `AirstairSteps` laying the steps they are
walked on; `SlideRenderer` draws an evacuation slide
inflating and deflating, as `EvacuationSlide` lays it out for the ground and builds it, from its
`SlideControlBlockEntity`, and `SlideRide` carries whoever slides down it; `LaserTurretRenderer` draws the laser path
clearer's turret out of its hatch, turned toward the newest of its bolts, and `LaserBolts` those bolts, from the tip of
its barrel as it is turned; its `LaserTurretBlockEntity` fires them, from its muzzle under the nose, at what
`LaserAim`'s rays find in the way; `SelfDestructButtonBlockEntity` arms the self-destruct button, and `SelfDestruct`
sounds its alarm aboard and blows its airliner up, taking it away through `SableFlight.remove` once the level's tick is
over, stopping those thrown clear where it blew up (`ThrownClear` on their clients), as `Blast` plays the explosion out in particles (`BlastPuffParticle`) and sounds, and `BlastFlash`, `CameraShake`
and `SelfDestructWarning` show it on screen; `GearBayRenderer` draws a landing gear leg swinging up or down, and its
doors, from the `GearBayBlockEntity` at its top, which takes the leg's blocks away going up and puts them back coming
down, worked by the `GearLeverBlockEntity`; `TailCameraFeed` draws the outside cameras' pictures, each a `CameraSpot`
of its airframe, for the flight deck's camera displays and the larger one `ZeppelinCameraDisplay` hangs in a zeppelin's
control car, through Veil's perspective renderer (Veil comes inside Sable's jar), and `NightLights` the cabin's blue
light at night and `DiscoLights` the spots of a disco ball turning as `DiscoBallRenderer` draws it through Veil's light
renderer, by way of `VeilLights`, which also keeps Veil's light framebuffer's depth in the window's format once a mod
gives the window a stencil: `TailCameraFeed` and `VeilLights` are the only places Veil is touched, besides the mixin
`mixin/AdvancedFboImplMixin`, which lets a Veil framebuffer borrowing another's depth see its stencil; of the other
mixins, `mixin/GirderBlockMixin` turns Create's metal girders a quarter turn without losing their beams, which Create's
own turn does (the gym hall's trusses of an airliner built facing east or west), and `mixin/HotAirBurnerMixin` lets
`BurnerSmoke` say how much smoke Create Aeronautics' hot air burners puff; `CloudlessCabin` leaves the clouds out of
the player's view while the camera is inside a hull in the cloud layer; `LandAhead` asks for the land ahead of a player
flying aboard, past what they see, a few chunks at a time. The zeppelin's geometry lives in `ZeppelinShape`
(the Hindenburg's measurements in metres) and is cut into blocks by `ZeppelinLayout` at the blocks a metre its
`ZeppelinSize` gives its hull, the car, decks and engine cars in blocks at every size, the layout keeping only its
blocks and working the air inside its hull out from the shape as it hands it over, its burners turned down to what a
small cell holds by `Burners`, and its boarding stairs folded away and brought down by the `BoardingStairsBlockEntity`
of the `BoardingStairsButtonBlock` at the head of each flight, as `BellyAirstairs`, the airliner's airstairs hung in
her belly, which `AirstairRenderer` draws as it does the airliner's; both airframes go up through `AirframeBuild` as a
`Layout`, kept as packed positions, from an `AirframeBlueprintItem`, whose miniature `BlueprintPreview` draws from its
souvenir model (`SouvenirModelBlock`), and `PropellerRenderer` draws a zeppelin's propellers behind their
`PropellerBlock` hubs, which spin them with their engine's signal. Once the hull stands, `ZeppelinLayout` lays its
features, each a `ZeppelinPiece` of its own on one list, as the blueprint's `ZeppelinOptions` ask: `ZeppelinSkyDeck` the
Sky Shaft and the Sky Deck, `ZeppelinCellGallery` the gallery inside the middle cell, `ZeppelinDecks` the furnished
zeppelin's furniture, `ZeppelinLights` its lights (`NavLightBlock`s, their glow drawn by `NavLightRenderer`),
`ZeppelinSkyDeckLaunch` the Sky Deck's rack, jump light and `PaperPlaneBoxBlock`, `ZeppelinHornCord` the `HornCordBlock`
that `ShipHorn` sounds the horn from, its echo and the mobs looking up, and `ZeppelinLiverySelector` the
`LiverySelectorBlock`, whose `LiverySelectorRenderer` draws a `LiveryPreview` of her in each livery and, as the
`LiveryBoardRenderer` of the older `LiveryBoardBlock` does, her `PaintersCradles`; a zeppelin's `Livery` is kept by its
autopilot, and `ZeppelinPaint` tints its cover (the shell model's `tint_index`) and fabric by it on the client, and
`ZeppelinFestive` dresses a festive zeppelin (`SleighBells` rings with its engines on the client), and `ZeppelinColours`
stands the flagstaff on the Sky Deck, its colours hoisted at a `FlagHalyardBlock`, flown by `ShipsColoursRenderer` and
painted on the fins by `ShipsColours` where `FinArt` says, and `ZeppelinChristening` hangs the `ShipsPlaqueBlock` that a
`ChristeningBottleItem` broken on her nose engraves through `Christening`, `ChristeningPaint` painting her crest and
name where `FlankArt` says (each a `PaintOverlay` on `ZeppelinPaint`'s list) and `ShipsPlaqueRenderer` engraving the
plaque, and `ZeppelinFlankScreen` stands the `PictureBoardBlock` whose `FlankScreenRenderer` draws the screens where
`FlankScreen` says, its headlines and figures set in a typeface by `ScreenType` and its maps painted by `ScreenMaps`,
and `ZeppelinEngineRooms` fits each engine car out with an `EngineRepeaterBlock`, whose `EngineRepeaterBlockEntity`
holds its engine in `ZeppelinPilot` until its order is answered, and `ZeppelinTrimCar` hangs the rail whose
`TrimWinchBlock` hauls the trim car as its trim wheel or the autopilot says, its `BubbleLevelBlock` drawn by
`BubbleLevelRenderer`, and `ZeppelinTechnoRoom` hides the room under its `SecretHatchBlock`, whose `TechnoDeckBlock`
plays it through `TechnoRoom` on the client, its lights drawn by `TechnoRoomRenderer`, and `ZeppelinLookout` stands the
`LookoutGlassBlock` in the control car, looked through by `LookoutView` and drawn by `LookoutGlassRenderer`, whose spots
`LookoutSpot` checks and drops as flags on the `LookoutGlassBlockEntity`, `LookoutFlags` putting them on the autopilot's
map (each a `MapMarker` on `DestinationMap`'s list), and `ZeppelinUpShip` hangs the `BallastValveBlock` whose
`BallastValveBlockEntity` strikes the take-off's beats, `UpShip` holding her on the lines (a `TakeoffHold` on
`ZeppelinPilot`'s list) and `UpShipScene` pouring the water, and `ZeppelinScreens` places the `PassengerScreenBlock`s,
on the promenades each on a `ScreenStandBlock`, that look through her `ZeppelinCamera`s (each a `CameraSpot`, as the
airliner's `OutsideCamera`s are, the flyby circling her in `CameraMount`), and `ZeppelinSearchlights` hangs the
`SearchlightBlock`s whose `SearchlightHandleBlock`s swing them, `SearchlightBeams` drawing their beams and pools, and
`ZeppelinFloodlights` hangs the `FloodlightBlock`s whose `FloodlightBlockEntity` lights them as the sky darkens,
`Floodlights` drawing their light, and `HullAmbience` plays her hull's boom and groans to those aboard, `GreatShadow`
draws her shadow from her `ZeppelinSilhouette`, `MistyWindows` mists her promenade windows, and `ZeppelinPost` stands
the `FrankingMachineBlock`, whose mail sacks (`MailSackItem`) land their own way from a `FallingCrate`,
`ZeppelinTowedBanner` fits the `BannerCleatBlock` whose banner `TowedBanner` draws from her tail, `ZeppelinSkyCrane`
stands the `CraneWinchBlock` whose `CraneWinchBlockEntity` hangs and holds the `CraneHook` (drawn by `CraneHookRenderer`,
its drum and dial by `CraneWinchRenderer`), `ZeppelinFlightDeck` lays the flight deck whose `DeckClampBlockEntity`s lock a
plane down through `SableFlight.lock` and refuel it, opened by the `CarrierLeverBlock`, `ZeppelinBoost`
fits the `BoostButtonBlock` and the boosters round her tail, its `BoostButtonBlockEntity` firing them and `SpeedBoost`
steering her as they do (a `Course` on `ZeppelinPilot`'s list, keeping her engines as they were), and
`ZeppelinSkywriting` hangs the `SkywritingBoardBlock` whose `SkywritingBoardBlockEntity` lays a `SkyShape` out ahead of
her and steers her along it, `Skywriting` handing her pilot the course (a `Course` on `ZeppelinPilot`'s list),
`SkyTrails` drawing the smoke and `SkywritingBoardRenderer` lighting the button; a feature is taken out of the zeppelin
with its line on the list. `ZeppelinControlCar` fits out its control room and wires the control head to every burner,
engine and hand control; `ZeppelinControls` reads the hand controls (`HelmBlock`, whose wheel `HelmRenderer` draws
turning as its `HelmBlockEntity` swings it, `TelegraphBlock` and `BurnerLeverBlock`) to fly by hand, and moves them to
show what the autopilot does; `ZeppelinPilot` flies it, from the hot air its mass needs in the air where it is, through
the autopilot's block entity, which `Craft` tells what it flies; `ZeppelinAssembly` gathers the blocks Sable assembles
it from through `SableFlight.assemble`, and `SableFlight.keepLoaded` keeps it loaded once it is assembled.
The rocket is drawn in `RocketShape`'s metres and cut into blocks by `RocketLayout`, its skin by the same `SurfaceCut`
as the zeppelin's cover, its features `RocketPiece`s on the layout's list (`RocketLadder` the crew's ladder,
`RocketCameraDisplay` the cockpit's camera display, which looks through the `RocketCamera`s, `RocketLiverySelector`
her livery selector, and a `RocketRoom` for each furnished story, from `RocketEntry` and `RocketCargoHold` to
`RocketObservationDeck`, a room's corners each a `RocketQuarter`, so one plan of a corner is laid mirrored in any of
them, and `RocketPilotSeats` the cockpit's seats); its rooms' own furniture is `ShelfUnitBlock` and `PalletRackBlock`,
and their gadgets are blocks worked on the server in block states and scheduled ticks (`AirlockPanelBlock`,
`ParcelScannerBlock`, which reads the crate on its belt, `GalleyFridgeBlock`, its `GalleyFridgeBlockEntity` a container,
and `WaveMachineBlock` and `FogMachineBlock`, whose block entities draw their waves and fog on the client and keep them
to time on the server, `LostTicks` taking up where a rocket's assembly dropped their scheduled ticks), and its club's
bar has `DrinkTapBlock`, pouring Star Fizz (a `MocktailItem`), `NeonSignBlock` and `CocktailTableBlock`; its
control head is wired to its engine bells' and side jets' relays as it is built, it is assembled as a zeppelin is, and
`RocketPilot` flies it, its thrust from the mass Sable weighs and a trim it learns, its tilt held on its side jets; a
`Craft` of its own gives its screens its pictures (`PanelArt.Pictures`). `RocketLift` lays her lift: a
`LiftMachineBlockEntity` runs it, its `LiftCar` living among her blocks (in `#sable:retain_in_sub_level`) and placed
on its shaft by a clock both sides share, its `LiftDoorBlock`s (each with a `LiftDoorBlockEntity`, whose
`LiftDoorRenderer` draws its pane as it slides) and `LiftBoardBlock`s by each landing (`LiftBoardRenderer` drawing them,
and `HeightGauge` in flight); on the client `LiftSounds` plays a riding car's hum, and its music for whoever rides it or
stands right by it. `Livery` paints her as it does a zeppelin,
by a `Livery.RocketSpot` measured up her and round her, `ZeppelinPaint` running a repaint down her, and `LiveryPreview`
pictures her from `RocketSilhouette`; `OvalWindowModel` bakes her windows' domes. `SpaceSky` draws space round any
camera far over the top of the world, over the game's own sky, the fog the land fades into taking the planet's colour,
and fades the clouds, which `CloudlessCabin` then leaves out; by night it has `Aurora` draw the northern lights along
the rim, and last of space `ShootingStars` the shooting star of the moment, both going by the game's time alone so every
client sees the same; `EdgeOfSpace` holds the height space begins at, and welcomes everyone aboard to space as she
climbs past it; `SpaceGravity` makes them light up there until it announces her artificial gravity; `RocketPlumes` sends
the smoke and the fire under her and the flames from her base.
Her crew lockers are stocked by `RocketRoom.crewLocker`, through a `Fitting`, with a `SpaceHelmetItem` each, drawn on
the head by its own model as the `PilotHelmetItem` is; on the client `HelmetVisor` lays either helmet's visor over the
view, the pilot helmet's under the `PilotHud`, and `HelmetBreathing` loops the space helmet's air while her control
head is at the edge of space or over it (`EdgeOfSpace.reached`). `RoomAmbience` plays her greenhouse and her pool to
whoever stands in them, telling their story from her control head by the floors and furnishing her autopilot keeps
(`rocketStories`, `rocketRooms`). `RocketObservationDeck` sets her lounge and her cafeteria about with the room's own
random sequence (`RocketRoom.scatter`), so each build comes out the same; its `SnackMachineBlock` keeps the step of a
sale in its block state, its spiral's turn a scheduled tick, so it needs no block entity. Her club's
`RobotBartenderBlock` opens a `Shop`, as the airliner's `DutyFreeCounterBlock` does, each with a `Shop.Shopfront` of its
own (its stock, its name and its sounds); its `RobotBartenderBlockEntity` hears of each sale by a block event, and on
the client `RobotBartenderRenderer` turns its head toward the nearest player and draws it polishing and serving, each
part a state of the block turned about its joint.
Last of all `RocketLayout.finishInside` finishes her rooms (`RocketFinish`): her cover a room sees becomes a
`RocketLinedCoverBlock` (cut to her curve) or a `RocketLiningPanelBlock` (whole), drawn by `ShellModel` with her livery
outside and the room's lining, untinted, on the faces it sees, as the airliner's cabin is lined (`Lining`), and each
story's floor a `RocketFloorBlock`, its room on top and the room under it underneath. `RocketWallLights` fixes the
wall washers (`WallLightBlock`) to her skin round each story, once the rooms have their furniture, where `SkinMount`
finds room on her wall, as it does for the club's neon signs; before them `RocketGirders` lays the steel girders under
her rooms' ceilings and `RocketGirderDecor` hangs each room's own under them (`GirderDecorBlock`, its `Part` drawing a
loop of string or a pot rack across two blocks as one), and her shaft's lift glass and corner girders are laid with the
shaft (`RocketLayout.shaftWall`).
`RocketLedStrips` lays her LED strips (`LedStripBlock`) round each story, the `LedPanelBlock` by each lift door, whose
`LedPanelBlockEntity` keeps its story's `LedSetting` and sets its strips' brightness, and the cockpit's
`LedConsoleBlock`, their controls laid out by `LedControls`; on the client `LedLights` tints the strips and times their
modes, and `LedPanelRenderer` (over the strips found by `LedRing`) and `LedConsoleRenderer` draw them through
`LedControlsArt`.
`RocketPosters`, a piece of its own, hangs her space posters on her lift shaft, a `SpacePosterBlock` for each block of
a picture, the pictures drawn by `tools/generate_textures.mjs`; `SpacePosterItem` puts one up by hand.
`RocketShaftDecor`, laid after it, dresses the shaft's outside for each room, a `ShaftDecorBlock` for each block of a
curtain, a board, a string of lights or a tube, each piece told by its room (`Kind`), its place across the shaft's face
(`Along`) and its row (`Row`).
`RocketSounds` counts her down and puffs her side jets on the server, and shows the clients how hard her engines fire,
from which `RocketRoar` plays their roar, ignition, cut-off and her touchdown and shakes the view aboard as they
light; `LaunchCallouts` has launch control call her milestones on the same radio, waiting its turn on the flight deck's
`Airtime`.
Her rover is a `Rover` entity and its bay a `RocketRoverBay` piece: the `RoverBayBlockEntity` on its door's sill
(`RoverHatchBlock`) holds it, works the door and the winch along `RoverRoute` and holds her countdown through
`RocketPilot.holdCountdownWhile`; the client's `RoverDriving` drives it from its `RoverRemoteItem` through the
`DriveRover` payload, and `RoverRenderer`, `RoverModel` and `RoverBayRenderer` draw it (`RoverClient` sets them up).
`RocketLab` furnishes her lab as `LabPlan` lays it out: the tank's `HabitatGlassBlock`s, its `HabitatFloorBlock`s and
`LabInstrumentBlock`s, and the `HabitatHatchBlock`, whose `HabitatHatchBlockEntity` makes her `Tardigrade` and keeps it
pacing the line the plan found for it (in `#sable:retain_in_sub_level`, so it lives among her blocks), curling it up in
space; on the client `TardigradeModel` and `TardigradeRenderer` draw it. `LabBays` lays the food lab and the wet bench
either side of the shaft: the `BioreactorBlock`s, which grow their steaks on scheduled ticks of their own and hand them
out, and the `LabGrowerBlock`s, the hydroponic rack and the algae column, all `LabMachineBlock`s two blocks tall; the
`LabPartitionBlock`s and the `LabPipeBlock`s; and the galley's cupboards and the lavatory's basin. A
`SampleCentrifugeBlock` times its spin with a scheduled tick, and a microscope sends `LookIntoMicroscope` to the
player's client, where `MicroscopeScreen` shows the slide.
`RocketGreenhouse` furnishes her greenhouse as `GreenhousePlan` lays it out, its unevenness from the room's own random
sequence (`RocketRoom.scatter`): `GardenBedBlock`s, farmland in a frame that joins the beds beside it; `GrowLightBlock`s
hung on light cables; `SprinklerBlock`s, whose `SprinklerBlockEntity` (found by the valve among those loaded) sprays and
waters what grows round it; and the `IrrigationValveBlock` that sets them off.
`SafetyCanopy` opens a canopy over anyone falling from a zeppelin, gliding as `Parachute` does. `Earshot` leaves out a
sound that starts too far off to be heard, so a zeppelin's burners crackling at the far end of it do not take every
sound channel there is. Assets are generated: `node tools/generate_textures.mjs` writes the textures (ordinary PNGs that
can also be painted by hand), and `node tools/generate_resources.mjs <vanilla-assets-dir>` the blockstates, models, loot
tables, tags, recipes and lang file. The door and the call button are vanilla's iron door and stone button retextured,
so their files come from vanilla's: pass the folder the client resources jar
(`build/moddev/artifacts/*-client-extra-*.jar`) is extracted to, or leave it out to keep them as they are. `node
tools/generate_sounds.mjs [<name> ...]` writes the sounds (only those named, if any) with FFmpeg, and with `oggenc` from
vorbis-tools where FFmpeg has no libvorbis (Homebrew's FFmpeg 9 has none): the engine loop is synthesised, the
self-destruct's siren and bang are made as Ultrasplosive makes its nuke's, from a public-domain siren and a CC0
explosion recorded on tape, kept in `tools/audio` (where each came from is in the script), and the flight deck's voice
(the callouts and the cabin announcements) is spoken by [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M), an open
speech synthesis model under the Apache License 2.0, in its British woman's voice Emma, on any system: install it once
with `npm install --prefix tools` (the model, some 300 MB, is fetched into `tools/node_modules` on the first run), or
the voice is left as it is. FFmpeg plays it through the cabin's loudspeakers, their band, a bright middle and the
speakers further along the cabin, and evens its level: the callouts compressed hard, crisp as warnings, the
announcements through a loudness normaliser. The settings for all of it are together in `VOICE`, to tune it. Air traffic
control is Windows' own speech synthesis, its man's voice ("Microsoft David"), so that part runs on Windows only,
through a VHF radio: FFmpeg squeezes it into the radio's band (about 300 to 3000 Hz), compresses, clips and bit-crushes
it a little, and the script lays it over the carrier's hiss with the odd crackle, between the squelch's click as the
button is pressed and the burst of noise as it is let go; other aircraft are the same voice pitched up or down. Its
settings and every line said are in `RADIO` and `ATC`.

## License

All Rights Reserved.
