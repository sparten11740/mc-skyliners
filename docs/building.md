# Building Create: Skyliners

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
the pointer that follows the look, the key under it and the one picked lit through `TouchPanelArt` as on every touch
panel, as `AutopilotRenderer` draws the control head's display; `AutopilotBlockEntity` flies
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
`AirstairFlight` builds and moves them and `AirstairMachinery` the machinery working them, from their
`AirstairBlockEntity`, `AirstairSteps` laying the steps they are
walked on; `SlideRenderer` draws an evacuation slide
inflating and deflating, as `EvacuationSlide` lays it out for the ground and builds it, from its
`SlideControlBlockEntity`, and `SlideRide` carries whoever slides down it; `LaserTurretRenderer` draws the laser path
clearer's turret out of its hatch, turned toward the newest of its bolts, and `LaserBolts` those bolts, from the tip of
its barrel as it is turned; its `LaserTurretBlockEntity` fires them, from its muzzle under the nose, at what
`LaserAim`'s rays find in the way; `SelfDestructButtonBlockEntity` arms the self-destruct button, laid
through `SelfDestructFitting` on the airliner's wall and by `RocketSelfDestruct` on the rocket's pedestal, and `SelfDestruct`
sounds its alarm aboard and blows its ship up, taking it away through `SableFlight.remove` once the level's tick is
over, stopping those thrown clear where it blew up (`ThrownClear` on their clients), as `Blast` plays the explosion out in particles (`BlastPuffParticle`) and sounds, and `BlastFlash`, `CameraShake`
and `SelfDestructWarning` show it on screen; `GearBayRenderer` draws a landing gear leg swinging up or down, and its
doors, from the `GearBayBlockEntity` at its top, which takes the leg's blocks away going up and puts them back coming
down, worked by the `GearLeverBlockEntity`; `TailCameraFeed` draws the outside cameras' pictures, each a `CameraSpot`
of its airframe, for the flight deck's camera displays (`TailCameraDisplayBlock`, a touch panel whose buttons
`CameraButtons` lays out) and the larger one `ZeppelinCameraDisplay` hangs in a zeppelin's
control car, through Veil's perspective renderer (Veil comes inside Sable's jar), and `NightLights` the cabin's blue
light at night and `DiscoLights` the spots of a disco ball turning as `DiscoBallRenderer` draws it through Veil's light
renderer, by way of `VeilLights`, which also keeps Veil's light framebuffer's depth in the window's format once a mod
gives the window a stencil: `TailCameraFeed` and `VeilLights` are the only places Veil is touched, besides the mixin
`mixin/AdvancedFboImplMixin`, which lets a Veil framebuffer borrowing another's depth see its stencil; of the other
mixins, `mixin/GirderBlockMixin` turns Create's metal girders a quarter turn without losing their beams, which Create's
own turn does (the gym hall's trusses of an airliner built facing east or west), and `mixin/HotAirBurnerMixin` lets
`BurnerSmoke` say how much smoke Create Aeronautics' hot air burners puff; `CloudlessCabin` leaves the clouds out of
the player's view while the camera is inside a hull in the cloud layer; `ShipWeather`, through
`mixin/LevelRendererMixin` and `mixin/LevelMixin`, lets rain and snow fall only onto a ship's roof and fades the
weather over the clouds; `LandAhead` asks for the land ahead of a player
flying aboard, past what they see, a few chunks at a time. The zeppelin's geometry lives in `ZeppelinShape`
(the Hindenburg's measurements in metres) and is cut into blocks by `ZeppelinLayout` at the blocks a metre its
`ZeppelinSize` gives its hull, the car, decks and engine cars in blocks at every size, the layout keeping only its
blocks and working the air inside its hull out from the shape as it hands it over, its burners turned down to what a
small cell holds by `Burners`, and its boarding stairs folded away and brought down by the `BoardingStairsBlockEntity`
of the `BoardingStairsButtonBlock` at the head of each flight, as `BoardingAirstairs`, the airliner's airstairs hung in
her belly, which `AirstairRenderer` draws as it does the airliner's; both airframes go up through `AirframeBuild` as a
`Layout`, kept as packed positions, from an `AirframeBlueprintItem`, whose miniature `BlueprintPreview` draws from its
souvenir model (`SouvenirModelBlock`), as a rocket's launch pad does, a `LaunchPad` from a `LaunchPadBlueprintItem`,
whose hub's `LaunchPadBlockEntity` tells a `RocketBlueprintItem` where to set her down; and `PropellerRenderer` draws a
zeppelin's propellers behind their
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
show what the autopilot does; `ZeppelinPilot` flies it, from the hot air its mass needs in the air where it is, for
the autopilot's block entity, which knows each kind of craft only through its `CraftFlight`, made by its `Craft`
(`AirlinerFlight`, whose flying stays the block entity's own, `ZeppelinFlight`, and `RocketFlight` and `FreighterFlight`,
both `StandingFlight`s, craft that stand on feet of their own, come straight down onto them and can climb to space): what the craft is
wired to, how it is flown each tick, how it lands, switches off, saves and is shown to the clients, and what is said
aboard it for the flight deck's announcements (`says`: the rocket's arrival, the freighter's computer), so a new kind of
craft is a new class and a constant there, the block entity left as it is; `ZeppelinAssembly` gathers the blocks Sable assembles
it from through `SableFlight.assemble`, and `SableFlight.keepLoaded` keeps it loaded once it is assembled, until `ParkedShips` lets it go, parked with nobody near, noting where it stands to load the land under it when someone comes back, as Sable would not.
Whatever the craft, `Impacts` hears it run into something, ticked by the autopilot's block entity with the ship as a
whole (`SableFlight.body`: her centre of mass, how fast it moves and the box round her): a change in that velocity in
one tick, gravity taken out, of more than any craft's lift, engines or an airliner's brakes make is summed as one hit
and heard by how hard, a thud or a crunch and a groan after it, from the side of her box the push came from, and
`ImpactJolt` kicks the view of those aboard through `CameraShake`; slowed hard across the ground just after such a
push, she grinds, the scrape played again each second while she slides.
The rocket is drawn in `RocketShape`'s metres and cut into blocks by `RocketLayout`, its cockpit's flight deck planned
by `RocketFlightDeck` (where each seat, screen and console goes), its skin by the same `SurfaceCut`
as the zeppelin's cover, its features `RocketPiece`s on the layout's list (`RocketLadder` the crew's ladder, or
`RocketAirstairs` her airstairs in its place, kept by the `RocketStairsBlockEntity` of the `RocketStairsButtonBlock` by
her door as `BoardingAirstairs` out of a bay, holding her countdown through `RocketPilot.holdCountdownWhile` till away,
with the `AirlockDoors` across the doorway's `AirlockDoorBlock`s, which her `AirlockPanelBlock` works and
`RocketStairsRenderer` draws with the stairs,
`RocketCameraDisplay` the cockpit's camera display, which looks through the `RocketCamera`s, `RocketLiverySelector`
her livery selector, and a `RocketRoom` for each furnished story, from `RocketEntry` and `RocketCargoHold` to
`RocketObservationDeck`, a room's corners each a `RocketQuarter`, so one plan of a corner is laid mirrored in any of
them, `RocketPilotSeats` the cockpit's seats, `RocketCockpitLights` the lights over them, `RocketDomeLimit` the invisible `DomeLimitBlock`s that keep anyone from
getting behind the planetarium's sky, and `RocketDome` the dome
over it, its `OrreryBlock`'s `OrreryBlockEntity` drawn by `OrreryRenderer`, which finds the dome round it as
`OrreryDome` and lines it with the `PlanetariumShell`, rimming the hole under it with the `OculusRing`, whose lamps
follow her autopilot's phase and launch control's spoken count, and draws the orrery as it stands at the moment
(`OrreryNow`), its sun and planets as sun-lit `Globe`s (`OrreryPlanets`) and its brass `OrreryWorks`); its rooms' own
furniture is
`ShelfUnitBlock` and `PalletRackBlock`,
and their gadgets are blocks worked on the server in block states and scheduled ticks (`AirlockPanelBlock`,
`ParcelScannerBlock`, which reads the crate on its belt, `GalleyFridgeBlock`, its `GalleyFridgeBlockEntity` a container,
and `WaveMachineBlock` and `FogMachineBlock`, whose block entities draw their waves and fog on the client and keep them
to time on the server, `LostTicks` taking up where a rocket's assembly dropped their scheduled ticks), and its club's
bar has `DrinkTapBlock`, pouring Star Fizz (a `MocktailItem`), `NeonSignBlock` and `CocktailTableBlock`; its
control head is wired to its engine bells' and side jets' relays as it is built, it is assembled as a zeppelin is, and
`RocketPilot` flies it, its thrust from the mass Sable weighs and a trim it learns, its tilt held on its side jets; a
`Craft` of its own gives its screens its pictures (`PanelArt.Pictures`). `RocketLift` lays her lift: a
`LiftMachineBlockEntity` runs it, its `LiftCar` living among her blocks (in `#sable:retain_in_sub_level`) and placed
on its shaft by a clock both sides share, its shaft filled with unseen `LiftFloorBlock`s, solid wherever the car's
floor is now, along which `LiftRide` carries the player on the client, its
`LiftDoorBlock`s (each with a `LiftDoorBlockEntity`, whose
`LiftDoorRenderer` draws its pane as it slides), a `LiftCallButtonBlock` by each landing's doors and a `LiftBoardBlock`
inside on the shaft's back wall, each wired to it by its `LiftStopBlockEntity` (`LiftBoardRenderer` drawing the boards,
the floor looked at lit, and `HeightGauge` in flight; its board and the rover panel are touch panels, as are the
airliner's announcement panel and autopilot screen and her LED panels and console, `TouchPanelBlock`s laying out their
tiles with `TouchPanel` and drawing them with `TouchPanelArt`, which brightens the tile looked at and makes the one
picked glow alike on every panel (a button of two states, which shows only what pressing it does, is a
`TouchPanel.Toggle`, as the freighter's ramp panel's is), and over the paint of the zeppelin's `LiveryBoardBlock`, `SkywritingBoardBlock` and
`PictureBoardBlock`, whose buttons are painted on, as over the `LiverySelectorBlock`'s pictures); on the client
`LiftSounds` plays a riding car's hum, and its
music for whoever rides it or
stands right by it. `Livery` paints her as it does a zeppelin,
by a `Livery.RocketSpot` measured up her and round her, `ZeppelinPaint` running a repaint down her, and `LiveryPreview`
pictures her from `RocketSilhouette`; `OvalWindowModel` bakes her windows' domes, and `OvalWindowWall` dresses one set
by hand in the wall it is set into (`OvalWindowPieceBlock` holds what every round or oval window shares, measured by
its `OvalWindowPieceBlock.Size`, which the model reads from its JSON; `StandingWindowBlock` what one standing in a wall
shares, as `OvalWindowBlock` does, and `FlatWindowBlock` what one lying flat shares, as `FlatOvalWindowBlock` does).
`SpaceSky` draws space round any
camera far over the top of the world, over the game's own sky, the fog the land fades into taking the planet's colour,
and fades the clouds, which `CloudlessCabin` then leaves out; by night it has `Aurora` draw the northern lights along
the rim, and last of space `ShootingStars` the shooting star of the moment, both going by the game's time alone so every
client sees the same; `EdgeOfSpace` holds the height space begins at, and welcomes everyone aboard to space as she
climbs past it; `SpaceGravity` makes them light up there until it announces her artificial gravity; `RocketPlumes` sends
the clouds and the fire under her and the flames from her base; `RocketGroundFire` sets the ground
under her alight, `RocketScorch` burns it bare, and `RocketLaunchBlast` sends her lift-off's shockwave out over it,
telling each player it reaches
with a `LaunchShock`; each client draws her exhaust plume itself, from how hard her engines fire (`RocketExhaust`).
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
`LedConsoleBlock`, both touch panels, their tiles laid out by `LedControls`; on the client `LedLights` tints the strips
and times their
modes, and `LedPanelRenderer` (over the strips found by `LedRing`) and `LedConsoleRenderer` draw them through
`LedControlsArt`, which lights their tiles through `TouchPanelArt`.
`RocketWindowDimmers`, laid after them, puts a `WindowDimmerBlock` over each story's LED panel, another touch panel,
whose `WindowDimmerBlockEntity` keeps the box round its story's oval windows (round itself, put up by hand) and sets
them through `OvalWindowPieceBlock.dimWithin`, as the console does through each story's `LedPanelBlockEntity`; on the
client `WindowDimmerRenderer` draws its four buttons through `LedControlsArt`, the console's oval windows grown.
`RocketPosters`, a piece of its own, hangs her space posters on her lift shaft, a `SpacePosterBlock` for each block of
a picture, the pictures drawn by `tools/generate_textures.mjs`; `SpacePosterItem` puts one up by hand.
`RocketPaintings` hangs the fleet's paintings, more pictures on that block, on her entry story and by her pool;
`ZeppelinPaintings` at the head of the zeppelin's dining room, on a panelled wall of its own; and `CabinPaintings` high
on three of the airliner's bulkheads, last of its fit-out and only where the bulkhead carries on past every side of the
picture; `FreighterPaintings` on the freighter's two straight walls across her beam, each at the first place along its
wall where it hangs whole, a `FloorLightBlock` under it.
`airframe.fleetPaintings` switches all four. `FreighterGallery` hangs more space posters along the tunnel down the
freighter's port prong through `FreighterPaintings.hangWhole`, and stands the fleet's `SouvenirModelBlock`s and
`PlanterBlock`s along the ledges of the tube out to her cockpit; `freighter.gallery` switches both.
`FreighterPortholes` sets `PortholeBlock`s, as if set by hand, into flat stretches of her cover down the outer walls of
her prong tunnels and both sides of her tube, where open sky lies straight out past them; `freighter.portholes`
switches them.
`FreighterEngineBays`, laid straight after her engines and always with them, closes off the thrusters in her prongs'
tips: an `ENGINE_GRATING` bulkhead across each tip round her retro's relay, and round each nose jet's relay, out to the
wall the jet stands in, a housing of grating under `HAZARD_PLATE`, leaving the jet's exhaust open straight out from its
nozzle, the one line along which Create Propulsion counts a thruster obstructed, so it pushes as before.
`FreighterLookout`, after her portholes, whose places it looks for, and her LED strips, which it stands over, seats her
port tunnel by them and lays the nook at its tip, a `PassengerScreenBlock` on the bulkhead; `freighter.prongs` switches
it. `FreighterSmugglerDen`, before her portholes, which keep clear of it, walls her
starboard tunnel off short of the jet's housing, a `SecretPanelBlock` in the wall and a `LooseRivetBlock` on the plate
beside it, and furnishes the den behind, a chest and a barrel filled from `chests/smuggler_stash` and
`chests/smuggler_contraband`; nothing that a redstone pulse sends off stands beside a thrust relay, whose signal it
would take for one; `freighter.smugglerHold` switches it. The rivet is a `ButtonBlock`, its pulse reaching the panel
through the plate it is set in; the panel is a `DoorBlock` that swings at the start of each pulse and stays, shuts by
itself as `SecretBookshelfBlock` does (sharing its `inDoorway`), only sounds hollow knocked on from its front and is
pushed open from behind, and stands on her cut skin as well as on a full block.
`RocketShaftDecor`, laid after it, dresses the shaft's outside for each room, a `ShaftDecorBlock` for each block of a
curtain, a board, a string of lights or a tube, each piece told by its room (`Kind`), its place across the shaft's face
(`Along`) and its row (`Row`); put up on a floor or under a ceiling (`Mount`), it is its room's small one instead.
`RocketSounds` counts her down and puffs her side jets on the server, and shows the clients how hard her engines fire
(a `ShownShare`, a share sent in steps and not too often), from which `RocketRoar` plays their roar, ignition, cut-off
and her touchdown, her roar's thunder close by and the shockwave's boom, and shakes the view aboard as they light and
on the ground near her, its loops and shakes made of `EngineRoar`'s parts, as the freighter's are; `LaunchCallouts`
has launch control call her milestones on the same radio, waiting its turn on the flight deck's `Airtime`.
`RocketCockpitControls`, a piece laid after her seats, sets her centre console's controls round her control head,
each a `RocketControlBlock` (`RocketBoostButtonBlock`, `RocketStabilitySwitchBlock`, `RocketModeKnobBlock`,
`RocketAbortHandleBlock` and `RocketLaunchKeyBlock`) linked to the head through a `Fitting`, whose
`RocketControlBlockEntity` asks her autopilot to work it (`AutopilotBlockEntity.rocketControl`) and sets its lamps every
few ticks from what the autopilot shows (`RocketControls.Panel`); `RocketControls`, which `RocketPilot` owns, keeps her
flight mode (saved), boost and stability (neither saved), hands the pilot the limits it flies by each tick and snaps her
stability back on before she tips too far, and the autopilot's update carries them to the clients, where
`FlightDisplayRenderer`, `RocketRoar`, `RocketExhaust` and `HeightGauge` show them.
Her rover is a `Rover` entity and its bay a `RocketRoverBay` piece: the `RoverBayBlockEntity` on its door's sill
(`RoverHatchBlock`) holds it, works the door (laying the `RoverPlatformBlock`s it is walked on while down) and the winch
along `RoverRoute` and holds her countdown through
`RocketPilot.holdCountdownWhile`; the client's `RoverDriving` drives it from its `RoverRemoteItem` through the
`DriveRover` payload, and `RoverRenderer`, `RoverModel` and `RoverBayRenderer` draw it (`RoverClient` sets them up).
`RocketLab` furnishes her lab as `LabPlan` lays it out: the tank's `HabitatGlassBlock`s, its `HabitatFloorBlock`s and
`LabInstrumentBlock`s, and the `HabitatHatchBlock`, whose `HabitatHatchBlockEntity` makes her `Tardigrade` and keeps it
pacing the line the plan found for it (in `#sable:retain_in_sub_level`, so it lives among her blocks), curling it up in
space; on the client `TardigradeModel` and `TardigradeRenderer` draw it, `SleepParticle` its "z"s asleep and
`SporeParticle` the spores of its jumps. A tardigrade's size is the game's scale attribute (`Tardigrade.setSize`), which
the hatch sets as it makes one and which scales its reach, its clouds, its stride and its voice with it: the
freighter's `LoungeTank` lays a small tank of the same glass on a stand of galley cabinets in her lounge, under a
hood, a pad of moss beside its hatch, which keeps one a quarter the size. Her `Spookie` lives among her blocks as the
tardigrade does, kept by its `SpookieHammockBlockEntity`, which hands it the rounds `FreighterSpookie` lays out and
walks over her blocks as she is built; `WatchVisitors` is the goal both creatures watch whoever comes near by, and
`SpookieModel` and `SpookieRenderer` draw it, its eyes glowing. `LabBays` lays the food lab and the wet bench
either side of the shaft: the `BioreactorBlock`s, which grow their steaks on scheduled ticks of their own and hand them
out, and the `LabGrowerBlock`s, the hydroponic rack and the algae column, all `LabMachineBlock`s two blocks tall; the
`LabPartitionBlock`s and the `LabPipeBlock`s; and the galley's cupboards and the lavatory's basin. A
`SampleCentrifugeBlock` times its spin with a scheduled tick, and a microscope sends `LookIntoMicroscope` to the
player's client, where `MicroscopeScreen` shows the slide.
`RocketGreenhouse` furnishes her greenhouse as `GreenhousePlan` lays it out, its unevenness from the room's own random
sequence (`RocketRoom.scatter`): `GardenBedBlock`s, farmland in a frame that joins the beds beside it; `GrowLightBlock`s
hung on light cables; `SprinklerBlock`s, whose `SprinklerBlockEntity` (found by the valve among those loaded) sprays and
waters what grows round it; and the `IrrigationValveBlock` that sets them off.
The freighter is drawn in `FreighterShape`'s metres, her hull the union of her parts' solids, and cut into blocks by
`FreighterLayout`, always at scale 2.4, round one deck: her skin cut by `SurfaceCut` and glazed round her cockpit's cone
and her domes, her outer bulkhead, her core, ring corridor and room walls of `PADDED_BULKHEAD`, the passages out past
the bulkhead to her cockpit and along her prongs walled off from the void beside them, her landing legs, her thrusters
and her cockpit's control head, seats and autopilot screen, the head wired to her thrusters' relays and her legs' feet
as a `FreighterPilot.Wiring`, and to her leg bays. Each leg's strut meets her belly in its `LegBayBlock`, laid in place
of the skin over the strut, which her belly's curve cuts open underneath (`FreighterLayout.legTop`); its
`LegBayBlockEntity` takes the leg's blocks away as it draws up and puts them back as it locks down, the same
`RetractableLegBlockEntity` as the airliner's `GearBayBlockEntity`, with its own timings, sounds and reach, and
`LegBayRenderer` draws its strut telescoping and its foot tucked under her belly. `FreighterLegs`, her flight's own,
raises them once she climbs or cruises well clear of the ground and lowers them otherwise, and her pilot holds her
landing at its hover until they are down (`FreighterPilot.Orders.legsDown`). `FreighterFlight` is her `CraftFlight`, and `FreighterPilot` flies her: her lift from the
mass Sable weighs and a trim it learns, split over her four clusters by her tilt and by where Sable puts her centre of
mass (`SableFlight.centreOfMass`), her mains, retros and jets for the speed ahead and across and the heading she wants,
round a circle or onto a pin with the zeppelin's `Steering`, set to land near a pin coming down on the nearest clear,
level spot `PadSearch` finds round it over the airliner's `GroundSurvey`, from her feet and the columns under her hull
that `FreighterLayout` gives (`feet`, `underHull`), flying by how her middle moves rather than her control head out in
her cockpit's pod, and her height over the ground from the lowest of her legs' feet (`Legs`, which the rocket's fins
share). `FreighterFlight` shows the clients how hard her lift thrusters and her mains fire, two `ShownShare`s, and where
her disc's middle is from her control head, from which `FreighterRoar` plays her engines' roar at her stern, her lift
jets' blast under her belly, their thump and her mains' spooling up as they light, her hum aboard, the ground shaking
near her and her thunder going by; and `EngineGlow` lights her `EngineGrilleBlock`s across her stern from the same
shares, laid in place of her skin round her mains and wired to her head with her thrusters, changing them seldom, as
each change redraws her round them; `LiftPlumes` shows her lift thrusters' plumes only close over the ground
(`Thrusters.plume`), since every puff is sent to every player near. Her pilot smooths what it reads of how fast she
tilts, turns and moves, and holds each set of thrusters at a signal until it is asked a step and more away, so they are
not set back and forth with every jitter. Her jets fire inside her, so she never runs them at a third of full or more,
where Create Propulsion sets fire to whatever stands in a thruster's exhaust. Her canopy is `DimmingGlassBlock`, cut to
her curve as the windscreen is and drawn denser for each darkness, which the rocket's window dimmer sets, as it does her
oval windows, through the same `OvalWindowPieceBlock.DIM`. Seen from inside her canopy is smooth: its glass, lined
(`DimmingGlassBlock.LINED`), and the struts between its panes, made her `CANOPY_FRAME`, are drawn from outside only
(`ShellModel`'s `outside_only`, a whole block telling which way is out from the cut blocks round it), and the unseen
`CanopyLiningBlock` near the canopy's tip, whose `CanopyLiningBlockEntity` `FreighterLayout.canopyLining()` tells the
cone a skin's thickness in from her surface and a pane of her glass to follow, has `CanopyLiningRenderer` draw the
inside as one cone of tinted glass, darkening with that pane, framed by ribs. `FreighterConsole` lays her console round
her control head, a `ConsoleBlock` of each `ConsoleKind` (in `console/`) in the block each slice's middle falls in, its
`ConsoleBlockEntity` told its slice of the ring (`ConsoleShape.Facet`) and the way to her head through a `Fitting`;
`ConsoleShape` says where a module stands in its block, a slice, a desk of its own, a plate or a wedge, for the server to
read a touch on its toggles and the client's `ConsoleRenderer` to draw it, its casing by `ConsoleCasing`, the shading
rolled round the ring, and its screens and controls by `ConsoleArt`, from the autopilot and `SableClient.attitude`. Her
boost is `FreighterFlight.boost`, her pilot firing her mains flat out while it lasts (`FreighterPilot.Boost`), the
clients shown when it ends to draw it. Her hyperspace jump is `FreighterJump`, owned by `FreighterFlight` and asked for
by the jump console: her `JumpDriveBlock` spooled through `spoolForJump`, the land round the pin loaded by a region
ticket of its own (which lets go by itself), her lowest block kept clear over the highest of the land's
`MOTION_BLOCKING` heightmap under her, and `SableFlight.jump` moving her, Sable carrying whoever stands or sits on her
and the rest aboard moved with her; each step goes to everyone aboard as a `HyperspaceJump`, which the client's
`HyperspaceOverlay` shows and sounds, her computer's lines generated with the rest of hers (`COMPUTER_JUMP_LINES`).
After a jump, `SableFlight.letGoAboard` and `SableClient.keepAboard` let go of the jump's length Sable would otherwise
keep everyone carried across going at. Her climb boost is `FreighterClimbBoost`, also owned by `FreighterFlight` and
asked for by the climb console: it sets her autopilot's height to the top and tells her pilot it fires
(`FreighterPilot.Orders.climbBoost`), when she climbs at up to `CLIMB_BOOST_SPEED`, easing by `CLIMB_BOOST_EASE`,
holding the spot it began over. Her `DeckRobotBlock`s chirp, trill and beep from their block
entity on the server and turn their heads to whoever is near on the client (`DeckRobotRenderer`), as the rocket's
bartender does. Her boarding ramp is kept by its panel, `RampPanelBlockEntity`, as a `BoardingRamp`, the airstairs'
shape of thing: its hatch, every block of her well's columns from her deck down, taken away and put back, the swing
counted on the server and each client (`ToldTicks`), invisible `RampPlateBlock`s set under the plate each tick as it
swings, their slope in their state, and once down the invisible `RampDeckBlock`s laid, falling a block in two in four
little steps, both of which the ground passes through (`SableFlight.rampPlate`, `rampDeck`); the swing holds, chiming,
while anyone stands where it would move next (`BoardingRamp.anyoneInTheSwing`); `BoardingRampRenderer`
draws the panel's face and the plate, rams, lip and lamps; the `RampButtonBlock` on her leg finds the panel through
her frame; and her take-off waits for it through `FreighterFlight.holdTakeoffWhile`, which calls a take-off held too
long off. Her computer's line as it comes down holds her cockpit's floor (`AutopilotBlockEntity.holdFloor`), and after
a landing it comes down only once her cockpit is `quiet`, so it and her announcements never talk over each other.
`Hatch` is the hatch both the ramp and the zeppelin's and rocket's airstairs take away and put back. The
`LaserCrossbowItem` keeps its charge and heat on its stack as data components, worked out on its holder's client as on
the server, which `LaserCrossbowLooks` draws and `LaserCrossbowHands` poses in hand, as the game poses only its own
crossbow; it traces its bolt with `LaserFire`, as the laser pointer does, and sends it as a `LaserBolt`, whose glow
colour tells `LaserBolts` to draw it amber rather than the path clearer's red, whose holder has it drawn leaving the
crossbow as each client draws it in their hands (`LaserCrossbowHands.muzzle`), and whose end lies among a ship's blocks
where it strikes one, so it lands on her as she is drawn. Her top turret is in `turret/`: the
`LaserCannonBlockEntity` finds the player seated within reach under it, traces their look from its `sight` (over and
behind its swivel, square to their look, worked out the same on both sides from the seated player's turn, which is in
her frame) to what they look at and aims from its pivot at that, turned into her frame (`SableFlight.turnToPlot`), so
its bolts land under the sight's crosshair; it holds fire while its barrels point into her own hull
(`SableFlight.bodyAt`), keeps its heat, cooling after a rest, and tells clients its aim now and then and its heat with
every bolt in its update tag, which ease toward the aim (`HeadTurn`) and count the heat down themselves; it fires
while the gunner's `GunnerTrigger` says the attack key is held, which the client's `GunnerControls` sends and otherwise
swallows. The client's `GunnerView` moves the seated gunner's view to the sight, a `Lens` placed every frame through
`SableClient.view`, and looks along it each tick for a hostile mob to lock on to; `GunnerSight` draws the sight's
reticle, thermometer and bearing ring with `GuiShapes`, which the `PilotHud` draws with too; `LaserCannonRenderer`
draws its swivel and barrels and `CannonServo` whirrs as they swing. Its rockets are `CannonRocket` entities, never
saved: the cannon launches one from its next loaded tube, worked out in her frame by `tilted` (which
`LaserCannonRenderer` turns its pods by too) and taken into the world (`SableFlight.toWorld`), carried along at her
speed there (`SableFlight.velocity`) and homing on the mob its sight rested on; it flies on the server, through her own
blocks (`SableFlight.bodyAt`), and goes off through `TurretBlast.rocket`, sending a `RocketBlast` to the clients near,
where `CannonRockets` draws its fireball and soot and jolts the view (`CameraShake.jolt`). Each client carries it on
between its moves; `CannonRockets` trails its smoke and flame as `BlastPuffParticle`s made there, not sent, and plays
its roar, and `CannonRocketRenderer` draws it. `GunnerTrigger` carries the use key as the rockets' trigger, the cannon
counts its rockets and loads its pods, telling clients in its update tag, and `GunnerSight` draws them under the
reticle. Its bolts and the crossbow's strike through `LaserFire.strike`, the one rule for what a laser hurts. Her club's `BandstandBlock` keeps nothing in its
block entity but is tracked on clients, where `ClubBand` starts its song for a player who comes near, counting its
beat, and `BandstandRenderer` draws the three players swaying to it; `FreighterLayout.clubBar()` lays the rocket
club's bar, counter, stools, taps, bartender and back bar, along the club's straight wall, and `quarters()` and
`lounge()` her bunks and booth, its seats a block of leg room back from the table,
`galley()` the rocket mess's galley units along her lounge's wall, and `cargoHold()`
her racks, crates and the rocket's parcel scanner. Her deck is lit by the rocket's `WallLightBlock`s, which
`FreighterLayout.lights()` fixes to her padded walls round her corridor and rooms, stepping out from each wall to the
first block with room. `FreighterLedStrips` rings each of her rooms with the rocket's `LedStripBlock`s and sets an
`LedPanelBlock` by its doorway; as her rooms are wedges round her axis whose boxes overlap, its panel is told the
wedge too (`LedPanelBlockEntity.Wedge`), which the server's brightness, `LedLights`' tint and `LedRing` all keep to. `FreighterFloors`
lays each room's own floor over her deck, in the mod's own light blocks so her weight is unchanged. `FreighterEngineering`
sets her `JumpDriveBlock` in engineering, its steps its block state and its timing scheduled ticks, as the parcel
scanner's are; its block entity only keeps the client's ring speed for `JumpDriveRenderer`, which draws its core and
rings. Her `BreakerBoardBlock`, laid after her LED strips, which hand on where each
room's panel went, keeps those panels' offsets in its `BreakerBoardBlockEntity` and changes their brightness; a switch is on
while its room is lit, as `BreakerBoardRenderer` reads from the panel. Her `CoolantValveBlock` keeps its pressure in its block state,
letting it off on scheduled ticks; `CoolantValveRenderer` turns its wheel and swings its gauge's needle toward it. `FreighterEntryHall` hangs the
`CrossbowRackBlock` on her entry hall's wall, found as her LED panels' places are, by `FreighterWalls`. `FreighterFurnishings`
fills her rooms out with the fleet's furniture, each piece nudged to the nearest free spot near where it is meant or
hung on the nearest padded wall, never across a doorway. `FreighterChillout` lays her lounge's beach first: the round
floor window of `floorwindow/` (`FloorWindowBlock`, laid anywhere by `FloorWindowItem` and opened from
`FloorWindowPanelBlock`; each `FloorWindowBlockEntity` keeps the offset to the window's middle, the floor it blends into,
what it replaced and how deep a shaft runs under it), sand round it and her belly glazed under it. The client's
`FloorWindowRenderer` draws the window, as its blocks have no model: every block the floor but for the iris's hole, in
strips whose ends lie on the circle, and the middle block the round parts, as `OculusRing` hides a stepped edge. Her ramp keeps the row over its hatch clear with
`HullLayout.keepClear`, still air but no longer open, so nothing laid after it stands on the hatch.
`FreighterTractorBeam`, straight after her cargo hold, lays the `BeamPadBlock` of `beam/` into the hold's floor where
it has air under it down to her belly, keeping the two rows over it clear, and hangs the `TractorBeamBlock` under her
belly beneath it as `FreighterExterior` hangs her outside pieces, clear of her legs; `freighter.tractorBeam` switches
both, and keeps every beam dark. A lit beam's `TractorBeamBlockEntity` works in the world, where it finds its lens and
the ground under it through `SableFlight.toWorld`: each tick it carries what stands in its beam (players by levitation,
which their own game moves them by smoothly, anything else by its motion), brings what reaches its top onto its pad,
the nearest `BeamPadBlockEntity` in reach (`Loaded`, so none is searched for), and lets down what steps onto the pad,
only where it meets solid ground or water within its reach. What it does with each thing it holds is kept only while
it is lit, never saved; whatever it lets go of falls slowly a while. On each client `TractorBeams` draws every lit beam
after the particles, as `SearchlightBeams` draws searchlights, from where Sable holds its lens, and plays its hum.
`DarkCorners` is her
last pass: it spreads her lamps' light through her layout as the game does and lights every spot someone could stand
in that is left unlit, her passages to a dim but never black level (`FreighterLayout.leastLight`), with a wall washer
or a floor light, never on a window's glass; `skyliners:dark` measures the same in game. She and the rocket are both `HullLayout`s, which keeps their blocks, fittings and glazing and works out
the air inside their hulls, their features `ShipPiece`s.
`FreighterExterior` dresses her outside once her rooms stand with the fleet's outside fittings in `exterior/`, each a
`HullFittingBlock` that a player puts up on a floor, a wall or a ceiling (MOUNT, through `Mounting`), a model of its own
for each and an outline from `HullFittings`, in the open block just outside her solid skin (or in place of a sliver of
it, `FreighterExterior.against`), face to face with it so her assembly takes it along, the skin block behind made whole
by `FreighterExterior.pad` so no gap shows under it, as her lights' strips and lamps and her laser cannon's hub are too;
its `SatelliteDishBlock` keeps its dish's pivots and size, which `SatelliteDishRenderer` turns it about by
`DrawingClock`, and `ExhaustVentBlock`, the exhaust vent's and the vent grille's, lets its wisps off where
`SableFlight.toWorld` puts its mouth.
Her cover is `FREIGHTER_COVER`, a `ShellBlock` of her own that `ZeppelinPaint` tints as it does the zeppelin's cover,
as light as that by the `zeppelin_fabric` tag. Its blockstate is a weighted list of `ShellModel`s, one for each of her
skins (painted by `freighterSkinTextures` in `generate_textures.mjs`, weighted by `FREIGHTER_SKINS` in
`generate_resources.mjs`), so the game picks a skin for each block from its position, in her plot once she is
assembled, which shuffles the pattern as she lifts off; each variant bakes a shape the first time it draws it. Her
layout's passes look for her own cover; freighters built before it keep `ZEPPELIN_COVER`, which nothing of hers needs
once she is built.
`FreighterPlating` swaps her cover, where `FreighterPlating.unseen` says no room sees it, past her outer bulkhead and
clear of her passages, for the hull plates, `ShellBlock`s of their own textures cut to the same shapes.
`FreighterLights` lays her light band, a lit `ShellBlock` round her rim, her `HullFittingBlock` light strips along her
loading gap and prongs, and her `NavLightBlock`s.
On each client `FreighterEngineGlow` draws her engines' glow after the particles, from the shares her autopilot already
shows for her roar and the nozzles `FreighterLayout.mainNozzles` gives, each flare brought toward the camera so the hull
never cuts it, and passes over a freighter out of view.
What moves on a client is drawn by `DrawingClock`: its own count of ticks for what only moves for show, and the game's
time eased, never stepping back, for what keeps time with the server. A part a client counts itself, such as a door or a
flight of stairs, takes the server's count through `ToldTicks`.
`Nearby` finds the blocks of a kind round a spot among the chunks loaded, passing over every section of the level whose
palette holds none: a seat for a passenger (`SableFlight.nearest`), and the jump lights put up by hand round a jump
switch put up by hand, which `JumpSwitchBlock` works when no ship wired it, each light keeping what the switch turned it
to as `JumpLightBlock.SWITCHED`.
`MountedModule` puts a console module up against the block clicked, as a button goes, on a floor, a wall or a ceiling
(`MOUNT`), and tips its shape as its blockstate tips its model (the generator's `mountedVariants`); `TowerRadarBlock`
hangs the radar's and the weather screen's monitor on a wall without its stand, its renderers drawing on the screen
there (`TowerRadarRenderer.surface`).
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
announcements through a loudness normaliser. The settings for all of it are together in `VOICE`, to tune it. The
freighter's computer is Kokoro's deepest man's voice, Onyx, through her worn intercom (a narrow band, a boxy honk, a
little grit and crushed bits, her corridors' short echo), its settings in `COMPUTER`; `sayAsFreighterComputer` says any
line in it. Air traffic
control is Windows' own speech synthesis, its man's voice ("Microsoft David"), so that part runs on Windows only,
through a VHF radio: FFmpeg squeezes it into the radio's band (about 300 to 3000 Hz), compresses, clips and bit-crushes
it a little, and the script lays it over the carrier's hiss with the odd crackle, between the squelch's click as the
button is pressed and the burst of noise as it is let go; other aircraft are the same voice pitched up or down. Its
settings and every line said are in `RADIO` and `ATC`.

`tools/playtest.mjs` plays what has gone wrong before in a flat world of its own through Blockpilot, each check as a player
would find the fault: walking up each ship's stairs from the ground, the mouse wheel on every kind of block in the rocket
and so on (`--list`). A bug found in play gets a check there along with its fix.
