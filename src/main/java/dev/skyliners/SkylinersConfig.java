package dev.skyliners;

import dev.skyliners.airframe.EngineFlames;
import dev.skyliners.airframe.ShellStyle;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings: how big the airliner is built and which other mods' blocks make it and the zeppelin fly. */
public final class SkylinersConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SCALE;
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ModConfigSpec.EnumValue<ShellStyle> SHELL_STYLE;
    public static final ModConfigSpec.BooleanValue FLEET_PAINTINGS;
    public static final ModConfigSpec.BooleanValue IMPACT_SOUNDS;
    public static final ModConfigSpec.BooleanValue CURTAINS_PART;
    public static final ModConfigSpec.ConfigValue<String> WING_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> ENGINE_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> ASSEMBLER_BLOCK;
    public static final ModConfigSpec.IntValue THRUST_SETTING;
    public static final ModConfigSpec.EnumValue<EngineFlames> ENGINE_FLAMES;
    public static final ModConfigSpec.ConfigValue<String> WHEEL_MOUNT_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> TIRE_ITEM;
    public static final ModConfigSpec.BooleanValue RETRACTABLE_GEAR;
    public static final ModConfigSpec.BooleanValue LAND_AHEAD;
    public static final ModConfigSpec.IntValue LAND_AHEAD_CHUNKS;
    public static final ModConfigSpec.BooleanValue LET_PARKED_SHIPS_GO;
    public static final ModConfigSpec.IntValue AUTOPILOT_CLIMB;
    public static final ModConfigSpec.DoubleValue AUTOPILOT_ROTATE_SPEED;
    public static final ModConfigSpec.DoubleValue ENGINE_SPOOL_SECONDS;
    public static final ModConfigSpec.DoubleValue FULL_LEVER_THRUST;
    public static final ModConfigSpec.BooleanValue LANDING_ASSIST;
    public static final ModConfigSpec.DoubleValue AUTOPILOT_CRUISE_SPEED;
    public static final ModConfigSpec.BooleanValue CONTROL_SURFACES;
    public static final ModConfigSpec.BooleanValue ATC_CHATTER;
    public static final ModConfigSpec.DoubleValue ATC_VOLUME;
    public static final ModConfigSpec.BooleanValue TURBULENCE;
    public static final ModConfigSpec.BooleanValue AIR_POCKETS;
    public static final ModConfigSpec.BooleanValue LIGHTNING_STRIKES;
    public static final ModConfigSpec.BooleanValue DECOMPRESSION;
    public static final ModConfigSpec.BooleanValue VILLAGER_PASSENGERS;
    public static final ModConfigSpec.BooleanValue LASER_PATH_CLEARER;
    public static final ModConfigSpec.IntValue LASER_RANGE;
    public static final ModConfigSpec.IntValue LASER_BOLTS_PER_SECOND;
    public static final ModConfigSpec.BooleanValue SELF_DESTRUCT;
    public static final ModConfigSpec.ConfigValue<String> BURNER_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> ENVELOPE_BLOCK;
    public static final ModConfigSpec.BooleanValue SAFETY_CANOPY;
    public static final ModConfigSpec.BooleanValue NAVIGATION_LIGHTS;
    public static final ModConfigSpec.BooleanValue SKY_DECK_LAUNCH;
    public static final ModConfigSpec.BooleanValue HORN;
    public static final ModConfigSpec.BooleanValue LIVERY_BOARD;
    public static final ModConfigSpec.BooleanValue CAMERA_DISPLAY;
    public static final ModConfigSpec.BooleanValue ROCKET_LIFT;
    public static final ModConfigSpec.BooleanValue ROCKET_ROOMS;
    public static final ModConfigSpec.BooleanValue ROCKET_FINISHES;
    public static final ModConfigSpec.IntValue ROCKET_CEILING;
    public static final ModConfigSpec.BooleanValue LED_STRIPS;
    public static final ModConfigSpec.BooleanValue ROCKET_POSTERS;
    public static final ModConfigSpec.BooleanValue ROCKET_WINDOW_DIMMERS;
    public static final ModConfigSpec.BooleanValue ROCKET_ROVER;
    public static final ModConfigSpec.BooleanValue ROCKET_AIRSTAIRS;
    public static final ModConfigSpec.BooleanValue ROCKET_LOW_GRAVITY;
    public static final ModConfigSpec.BooleanValue ROCKET_PLUMES;
    public static final ModConfigSpec.BooleanValue ROCKET_GROUND_FIRE;
    public static final ModConfigSpec.BooleanValue ROCKET_SCORCH;
    public static final ModConfigSpec.BooleanValue ROCKET_LAUNCH_BLAST;
    public static final ModConfigSpec.BooleanValue ROCKET_LAB;
    public static final ModConfigSpec.BooleanValue ROCKET_DOME;
    public static final ModConfigSpec.BooleanValue ROCKET_CALLOUTS;
    public static final ModConfigSpec.BooleanValue ROCKET_COCKPIT_CONTROLS;
    public static final ModConfigSpec.BooleanValue ROCKET_BOOST;
    public static final ModConfigSpec.BooleanValue ROCKET_STABILITY_SWITCH;
    public static final ModConfigSpec.BooleanValue ROCKET_ARC_ASCENT;
    public static final ModConfigSpec.BooleanValue FREIGHTER_ENGINE_GRILLE;
    public static final ModConfigSpec.BooleanValue FREIGHTER_EXTERIOR;
    public static final ModConfigSpec.BooleanValue FREIGHTER_PLATING;
    public static final ModConfigSpec.BooleanValue FREIGHTER_OUTSIDE_LIGHTS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_ENGINE_GLOW;
    public static final ModConfigSpec.BooleanValue FREIGHTER_EVERY_PLUME;
    public static final ModConfigSpec.BooleanValue FREIGHTER_NOZZLE_RIMS;
    public static final ModConfigSpec.IntValue FREIGHTER_PAD_REACH;
    public static final ModConfigSpec.BooleanValue FREIGHTER_GALLERY;
    public static final ModConfigSpec.BooleanValue FREIGHTER_TURRET_BLASTS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_TURRET_THROWS_PLAYERS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_ROCKETS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_SHIP_HITS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_PORTHOLES;
    public static final ModConfigSpec.BooleanValue FREIGHTER_PRONGS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_SMUGGLER_HOLD;
    public static final ModConfigSpec.BooleanValue FREIGHTER_CANOPY_DIMMER;
    public static final ModConfigSpec.BooleanValue FREIGHTER_SMOOTH_CANOPY;
    public static final ModConfigSpec.BooleanValue FREIGHTER_CONSOLE;
    public static final ModConfigSpec.BooleanValue FREIGHTER_HYPERSPACE_JUMP;
    public static final ModConfigSpec.BooleanValue FREIGHTER_HYPERSPACE_WAKE;
    public static final ModConfigSpec.BooleanValue FREIGHTER_CLIMB_BOOST;
    public static final ModConfigSpec.BooleanValue FREIGHTER_SLAM_LANDING;
    public static final ModConfigSpec.BooleanValue FREIGHTER_SPIN;
    public static final ModConfigSpec.BooleanValue FREIGHTER_ROBOTS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_RAMP;
    public static final ModConfigSpec.BooleanValue FREIGHTER_LASER_CROSSBOW;
    public static final ModConfigSpec.BooleanValue FREIGHTER_TURRETS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_BANDSTAND;
    public static final ModConfigSpec.BooleanValue FREIGHTER_LIGHTS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_CLUB_BAR;
    public static final ModConfigSpec.BooleanValue FREIGHTER_ROOMS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_TARDIGRADE;
    public static final ModConfigSpec.BooleanValue FREIGHTER_SPOOKIE;
    public static final ModConfigSpec.BooleanValue FREIGHTER_LED_STRIPS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_FINISHES;
    public static final ModConfigSpec.BooleanValue FREIGHTER_ENGINEERING;
    public static final ModConfigSpec.BooleanValue FREIGHTER_BEACH;
    public static final ModConfigSpec.BooleanValue FREIGHTER_RETRACTING_LEGS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_TRACTOR_BEAM;
    public static final ModConfigSpec.BooleanValue FREIGHTER_STICKER_MAP;
    public static final ModConfigSpec.BooleanValue FREIGHTER_ZERO_GRAVITY;
    public static final ModConfigSpec.BooleanValue FREIGHTER_REPAIRS;
    public static final ModConfigSpec.BooleanValue FREIGHTER_BOWLING;
    public static final ModConfigSpec.BooleanValue SHIPS_COLOURS;
    public static final ModConfigSpec.BooleanValue CHRISTENING;
    public static final ModConfigSpec.BooleanValue FLANK_SCREEN;
    public static final ModConfigSpec.BooleanValue ENGINE_REPEATERS;
    public static final ModConfigSpec.BooleanValue TRIM_CAR;
    public static final ModConfigSpec.BooleanValue TECHNO_ROOM;
    public static final ModConfigSpec.BooleanValue LOOKOUT;
    public static final ModConfigSpec.BooleanValue UP_SHIP;
    public static final ModConfigSpec.BooleanValue ZEPPELIN_SCREENS;
    public static final ModConfigSpec.BooleanValue SEARCHLIGHTS;
    public static final ModConfigSpec.BooleanValue FLOODLIGHTS;
    public static final ModConfigSpec.BooleanValue SKYWRITING;
    public static final ModConfigSpec.BooleanValue SPEED_BOOST;
    public static final ModConfigSpec.BooleanValue ZEPPELIN_POST;
    public static final ModConfigSpec.BooleanValue TOWED_BANNER;
    public static final ModConfigSpec.BooleanValue SKY_CRANE;
    public static final ModConfigSpec.BooleanValue FLYING_CARRIER;
    public static final ModConfigSpec.BooleanValue SPACE_WEATHER;
    public static final ModConfigSpec.IntValue SPACE_WEATHER_EVERY;
    public static final ModConfigSpec.IntValue SPACE_WEATHER_STRENGTH;
    public static final ModConfigSpec.IntValue SPACE_WEATHER_BLACKOUT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("airframe");
        SCALE = builder
            .comment(
                "Blocks per metre of the real A380 (72.7 m long, 79.8 m span, a 7.1 m wide and 8.4 m tall fuselage).",
                "At 2 the cabin is about 14 blocks wide and 11 tall above the floor; every step up roughly doubles the block count."
            )
            .defineInRange("scale", 2.0, 1.0, 4.0);
        BLOCKS_PER_TICK = builder
            .comment("How many blocks a blueprint places each tick while the airliner goes up.")
            .defineInRange("blocksPerTick", 6000, 500, 100000);
        SHELL_STYLE = builder
            .comment(
                "How the fuselage's skin is cut to its curve. FITTED cuts each block as close to the curve as one cut can, so neighbouring blocks",
                "meet with small steps; SNAPPED cuts through block corners and edge middles only, in 45 degree and one-in-two slopes that run",
                "into each other without a step, and also rounds off the inside of the nose's skin. Read when a blueprint is used."
            )
            .defineEnum("shellStyle", ShellStyle.SNAPPED);
        FLEET_PAINTINGS = builder
            .comment(
                "Whether each ship is built with paintings of the others: the rocket and the zeppelin high in a furnished airliner's",
                "forward door hall and pool hall, the airliner and the rocket in a furnished zeppelin's dining room, the airliner",
                "and the zeppelin on a rocket's entry story, each of them with the freighter too, and the airliner, the zeppelin",
                "and the rocket round the freighter's deck. Ships already built keep theirs."
            )
            .define("fleetPaintings", true);
        IMPACT_SOUNDS = builder
            .comment(
                "Whether a ship is heard running into something, the ground, a mountainside or another ship: a crunch and",
                "a boom for a hard hit, louder and lower the harder, with a short shake of the view aboard and the hull",
                "groaning after it, a thud for a bump, and a grinding scrape while she slides on along what she hit.",
                "Gentle landings make none of them."
            )
            .define("impactSounds", true);
        builder.pop();

        builder.push("flight");
        WING_BLOCK = builder
            .comment(
                "The block that makes up the lifting layer of the wings and tail, laid flat (vertical in the fin).",
                "Left out (or not installed) the wings are built from airframe panels and give no lift."
            )
            .define("wingBlock", "createpropulsion:wing");
        ENGINE_BLOCK = builder
            .comment(
                "The thruster at the back of each engine, facing the nose. The blueprint is creative only, so the default needs no fuel;",
                "createpropulsion:thruster runs on Turpentine instead. The thrust levers in the cockpit power them."
            )
            .define("engineBlock", "createpropulsion:creative_thruster");
        THRUST_SETTING = builder
            .comment(
                "How hard every engine thruster and control jet is set to push, on the creative thruster's own 1 to 99 scale (its value box).",
                "99 is its full rated thrust; a thruster placed by hand starts near 7."
            )
            .defineInRange("thrustSetting", 99, 1, 99);
        ENGINE_FLAMES = builder
            .comment(
                "What the engines show behind them while they run. LIGHTWEIGHT_FLAME: a flame each client draws itself at next to no cost,",
                "seen with Fancy or Fabulous graphics and not with Fast. PROPULSION_PLUME: Create Propulsion's own plasma plume, built into the",
                "engines' thrusters when a blueprint is used, its particles sent to every player near many times a second in flight and",
                "costing each client a good share of its frame. NONE: nothing, as on a real airliner (the engines still glow and leave",
                "contrails). Only the airliner's own thrusters are set, never Create Propulsion's settings or thrusters built elsewhere."
            )
            .defineEnum("engineFlames", EngineFlames.LIGHTWEIGHT_FLAME);
        ASSEMBLER_BLOCK = builder
            .comment("Left on the cockpit floor, ready to turn the finished airliner into a flying contraption. Empty for none.")
            .define("assemblerBlock", "simulated:physics_assembler");
        WHEEL_MOUNT_BLOCK = builder
            .comment(
                "The landing gear's wheel mounts, two to each leg facing outward, so the assembled airliner rolls on real wheels.",
                "Empty (or not installed) and the gear ends in fixed tire blocks it can only slide on."
            )
            .define("wheelMountBlock", "offroad:wheel_mount");
        TIRE_ITEM = builder
            .comment("The tire fitted to each wheel mount.")
            .define("tireItem", "offroad:large_tire");
        RETRACTABLE_GEAR = builder
            .comment(
                "Whether the landing gear folds up into the belly: a gear lever on the flight deck raises and lowers it, and the autopilot",
                "raises it after take-off and lowers it to land. Read when a blueprint is used (only gear on wheel mounts retracts), and",
                "switched off later the gear can still be lowered but no longer raised."
            )
            .define("retractableGear", true);
        LAND_AHEAD = builder
            .comment(
                "Whether the land ahead of a player flying aboard an airliner or a zeppelin (any of Sable's bodies) is loaded a little",
                "before they see it, a few chunks at a time while the server has time to spare, and generated where nobody has been.",
                "Without it, flying over new land the server generates a row of it all at once each time the player crosses into another",
                "chunk, and holds up its tick while it does."
            )
            .define("landAhead", true);
        LET_PARKED_SHIPS_GO = builder
            .comment(
                "Whether a zeppelin or rocket on the ground with its autopilot off is unloaded once nobody is within 600",
                "blocks of it, and brought back as someone comes within 400. A ship that never quite comes to rest on uneven",
                "ground otherwise costs the server its physics every tick, wherever it is. Off, a ship once assembled stays",
                "loaded for good. An unloaded ship does nothing until it is brought back."
            )
            .define("letParkedShipsGo", true);
        LAND_AHEAD_CHUNKS = builder
            .comment("How far past the edge of what each player sees the land ahead is loaded, in chunks.")
            .defineInRange("landAheadChunks", 8, 1, 32);
        builder.pop();

        builder.push("autopilot");
        AUTOPILOT_CLIMB = builder
            .comment("How many blocks above the runway the autopilot climbs to and then holds.")
            .defineInRange("climbHeight", 80, 10, 1000);
        AUTOPILOT_ROTATE_SPEED = builder
            .comment(
                "Ground speed, in blocks a second, at which the autopilot raises the nose to take off.",
                "It also rotates once the take-off roll stops gaining speed, whichever comes first."
            )
            .defineInRange("rotateSpeed", 30.0, 1.0, 200.0);
        AUTOPILOT_CRUISE_SPEED = builder
            .comment(
                "Speed, in blocks a second, the autopilot climbs and cruises at, setting the engines to hold it.",
                "Never less than a quarter above the speed it rotated at, so the wings keep lifting."
            )
            .defineInRange("cruiseSpeed", 50.0, 5.0, 300.0);
        ENGINE_SPOOL_SECONDS = builder
            .comment(
                "How long the airliner's engines take to wind up from idle to full thrust, in seconds: slowly at first and faster",
                "as they come up, as a jet's do, whether the thrust levers or the autopilot ask for it. Thrust drops at once.",
                "0 sets the thrust at once."
            )
            .defineInRange("engineSpoolSeconds", 8.0, 0.0, 30.0);
        FULL_LEVER_THRUST = builder
            .comment(
                "How much of the airliner's full engine thrust her thrust levers give at full, as a share of it, flying by hand:",
                "enough to outrun her cruising speed, little enough that she rolls a while down the runway before she lifts off.",
                "Every notch of the levers is cut alike. The autopilot sets the thrust it needs itself. 1 gives the engines' whole",
                "thrust at full lever, as before."
            )
            .defineInRange("fullLeverThrust", 0.33, 0.05, 1.0);
        LANDING_ASSIST = builder
            .comment(
                "Whether the airliner, flown by hand with her thrust levers at idle, is brought down to land once she is low over",
                "the ground: her nose set for a gentle sink, flaring over the ground, her wings level, her engines holding her",
                "rotation speed and her landing gear down. Pushing the levers off idle takes her back at once. Off, she slows",
                "and falls as she will."
            )
            .define("landingAssist", true);
        CONTROL_SURFACES = builder
            .comment(
                "Whether the autopilot rolls and pitches the airliner with control surfaces, turning the wing blocks of the",
                "outer wings' trailing edges (ailerons) and of the tailplane over, rather than with the control jets. The jets stay for",
                "safety: they do it all at take-off and landing speeds and whenever the airliner is past the attitude the autopilot keeps",
                "it within, and they add whatever more the surfaces cannot give. Only airliners built since the surfaces came in have them;",
                "older ones fly on their jets either way."
            )
            .define("controlSurfaces", true);
        builder.pop();

        builder.push("radio");
        ATC_CHATTER = builder
            .comment(
                "Whether air traffic control talks over the flight deck's radio: clearing the autopilot's take-offs (it holds the brakes",
                "until cleared) and landings, handing the airliner on as it climbs away, welcoming it in once landed, and chattering every",
                "few minutes in flight. Heard on the flight deck, and anywhere aboard in a pilot helmet."
            )
            .define("atcChatter", true);
        ATC_VOLUME = builder
            .comment("How loud air traffic control is, as a share of full volume; each player's Voice/Speech slider turns it down further.")
            .defineInRange("atcVolume", 0.8, 0.1, 1.0);
        builder.pop();

        builder.push("cabin");
        CURTAINS_PART = builder
            .comment(
                "Whether a shut curtain parts round whoever walks through it and falls shut behind them.",
                "Off, it stays as it hangs, and they pass through it just the same."
            )
            .define("curtainsPart", true);
        TURBULENCE = builder
            .comment(
                "Whether airliners in flight now and then run into rough air, shaking everyone aboard and throwing about whoever is not seated.",
                "It comes a little more often and harder in rain, and much more often, longer and harder in a thunderstorm."
            )
            .define("turbulence", true);
        AIR_POCKETS = builder
            .comment(
                "Whether airliners flying high up (60 blocks or more above the ground) now and then drop into an air pocket: a few blocks of",
                "sudden fall that lifts whoever is not seated off the floor. The higher, the more often; more often in rain, and more often",
                "and deeper in a thunderstorm."
            )
            .define("airPockets", true);
        LIGHTNING_STRIKES = builder
            .comment(
                "Whether lightning now and then strikes airliners flying through a thunderstorm, about once a minute (twice as often high up):",
                "a real bolt with its flash and thunder, the cabin flickering and the screens full of static, but nothing harmed or set alight."
            )
            .define("lightningStrikes", true);
        DECOMPRESSION = builder
            .comment(
                "Whether a door opened in flight lets the air rush out of the airliner: whoever stands near it, and anything loose, is dragged to the door and flung out.",
                "Anyone seated stays put; with a parachute on, being sucked out is just the start of a skydive."
            )
            .define("decompression", true);
        VILLAGER_PASSENGERS = builder
            .comment(
                "Whether villagers fly as passengers: handed a boarding pass from a check-in desk, a villager walks aboard and takes a seat,",
                "and pays its fare in emeralds when the airliner has flown it somewhere and stands still with the seatbelt signs off."
            )
            .define("villagerPassengers", true);
        builder.pop();

        builder.push("laser");
        LASER_PATH_CLEARER = builder
            .comment(
                "Whether the laser path clearer works: armed from the flight deck, a turret under the nose fires at hostile mobs and vaporises",
                "blocks in an assembled airliner's flight path. It never hits players or passive mobs, never vaporises blocks with block",
                "entities, unbreakable blocks, liquids or other airliners, leaves blocks alone where mobGriefing is off, and asks first as a",
                "player breaking them would, so claimed land keeps them. Only airliners built with it have the switch and the turret."
            )
            .define("laserPathClearer", true);
        LASER_RANGE = builder
            .comment("How far ahead along the flight path the laser reaches, in blocks.")
            .defineInRange("laserRange", 96, 16, 256);
        LASER_BOLTS_PER_SECOND = builder
            .comment("How many bolts the laser fires each second; each vaporises at most 27 blocks or hurts one mob.")
            .defineInRange("laserBoltsPerSecond", 5, 1, 20);
        builder.pop();

        builder.push("selfDestruct");
        SELF_DESTRUCT = builder
            .comment(
                "Whether the self-destruct button on the flight deck works: a big red button under a striped cover, opened, armed and",
                "pressed again within ten seconds, gives everyone aboard a parachute and blows the assembled ship up in a huge",
                "explosion of fire and smoke. The ship is gone, but nothing round it is harmed: no block is broken and nobody is",
                "hurt. Off, the button only thuds. Only airliners and rockets built while it is on have the button; one placed by",
                "hand works too."
            )
            .define("selfDestruct", true);
        builder.pop();

        builder.push("zeppelin");
        BURNER_BLOCK = builder
            .comment(
                "The hot air burner standing in each of the zeppelin's gas cells, lifting it with the hot air it fills the cell with.",
                "The relay beside each sets how hard it burns. Left out (or not installed) the cells hold no hot air and the zeppelin is a building."
            )
            .define("burnerBlock", "aeronautics:adjustable_burner");
        ENVELOPE_BLOCK = builder
            .comment(
                "The envelope the zeppelin's gas cells are made of, one cell in each colour: {colour} stands for the dye's name",
                "(red, light_blue, ...). Left out (or not installed) the cells are made of the zeppelin's own fabric and hold no gas."
            )
            .define("envelopeBlock", "aeronautics:{colour}_envelope");
        SAFETY_CANOPY = builder
            .comment(
                "Whether a canopy opens over anyone who falls from a zeppelin, a few blocks down, so they glide to the ground unhurt",
                "as under a parachute: off its boarding stairs in flight, or from its gangway inside the hull; and over anyone",
                "a laser cannon throws into the air (see freighter.turretThrowsPlayers)."
            )
            .define("safetyCanopy", true);
        NAVIGATION_LIGHTS = builder
            .comment(
                "Whether a zeppelin is built dressed for the night: red and green lights on its engine cars, a white one on its tail,",
                "strobes on its fins, a beacon under its belly and a line of warm bulbs along its crown. Zeppelins already built keep theirs."
            )
            .define("navigationLights", true);
        SKY_DECK_LAUNCH = builder
            .comment(
                "Whether a zeppelin is built with a parachute rack, a jump light and its switch, a box of paper planes and a locker",
                "holding a landing target and signal flares on its Sky Deck. Zeppelins already built keep theirs."
            )
            .define("skyDeckLaunch", true);
        HORN = builder
            .comment(
                "Whether a zeppelin is built with a horn cord in its control car: pulled, a deep, menacing horn heard far off, villagers and",
                "animals below looking up, and in flight its echo off the ground, back the later the higher it flies. Zeppelins already",
                "built keep theirs."
            )
            .define("horn", true);
        LIVERY_BOARD = builder
            .comment(
                "Whether a zeppelin is built with a livery selector under the camera display in its control car, its pictures of her repainting",
                "her in silver, cream, navy grey, the airliner's colours, polar red or midnight blue, and a rocket with one in her cockpit, in",
                "moon-rocket checks, roll stripes, red trim and the last three. Ships already built keep their selector and their paint."
            )
            .define("liveryBoard", true);
        CAMERA_DISPLAY = builder
            .comment(
                "Whether a zeppelin is built with a camera display over the livery selector in her control car, and a rocket with one",
                "in her cockpit, showing their outside cameras live as the airliner's flight deck shows its own, the camera circling",
                "them at first, so a new paint is seen going over them. Zeppelins and rockets already built keep theirs."
            )
            .define("cameraDisplay", true);
        SHIPS_COLOURS = builder
            .comment(
                "Whether a zeppelin is built with a flagstaff on its Sky Deck: a banner hoisted at its halyard flies from it as a great",
                "flag, and is painted on the zeppelin's top and bottom fins. Zeppelins already built keep theirs."
            )
            .define("shipsColours", true);
        CHRISTENING = builder
            .comment(
                "Whether a zeppelin is built with a ship's plaque in her control car, so a Christening Bottle broken on her nose names",
                "her and paints her crest and name on her flanks. Zeppelins already built keep theirs."
            )
            .define("christening", true);
        FLANK_SCREEN = builder
            .comment(
                "Whether a zeppelin is built with a glowing screen down each flank and a picture board on her upper deck choosing what",
                "they show: posters, her colours, a map, the faces aboard or her height and speed. Zeppelins already built keep theirs."
            )
            .define("flankScreen", true);
        ENGINE_REPEATERS = builder
            .comment(
                "Whether a zeppelin's engine cars are built fitted out for their engineers: a repeater in each, its engine waiting for each",
                "new order to be answered there before it follows (the engine crew answer it after a few seconds when no one does), a",
                "lamp, and a rope down from each to climb up by. Zeppelins already built keep theirs."
            )
            .define("engineRepeaters", true);
        TRIM_CAR = builder
            .comment(
                "Whether a zeppelin is built with a trim car: a heavy car hauled fore and aft on a rail under her gangway, pitching her as it",
                "moves, set from a trim wheel in her control car, where a bubble level shows her pitch; the autopilot moves it to hold her",
                "nose level by weight. Zeppelins already built keep theirs."
            )
            .define("trimCar", true);
        TECHNO_ROOM = builder
            .comment(
                "Whether a zeppelin is built with a secret techno room under her gangway, reached by a hidden hatch: its music starts as",
                "you walk in, builds and drops. Zeppelins already built keep theirs."
            )
            .define("technoRoom", true);
        LOOKOUT = builder
            .comment(
                "Whether a zeppelin is built with the Lookout's Glass in her control car, through which a spot on the land is marked",
                "with a flag carrying the lookout's face, to be picked on the autopilot's map as its pin. Off, the glasses already built",
                "mark nothing."
            )
            .define("lookout", true);
        UP_SHIP = builder
            .comment(
                "Whether a zeppelin taking off from the ground is held a moment on her ground handlers' lines while her bow's water",
                "ballast pours out, until her ground officer calls \"Up ship!\" and they let her go together. Off, she rises at once,",
                "and zeppelins built from then on have no ballast valve."
            )
            .define("upShip", true);
        ZEPPELIN_SCREENS = builder
            .comment(
                "Whether a zeppelin is built with passenger screens, before her promenades' armchairs and in her control car, looking",
                "through her outside cameras: at her bow, under her car, and the flyby camera circling her. Zeppelins already built",
                "keep theirs."
            )
            .define("zeppelinScreens", true);
        SEARCHLIGHTS = builder
            .comment(
                "Whether a zeppelin is built with two searchlights under her control car, swung from handles in its lounge to",
                "wherever whoever works them looks, a beam and a pool of light on the ground. Off, the handles already built do",
                "nothing."
            )
            .define("searchlights", true);
        FLOODLIGHTS = builder
            .comment(
                "Whether a zeppelin is built with floodlights under her control car and along her keel, always on, lighting the land",
                "under her and ahead, by day the shade of her hull. Zeppelins already built keep theirs."
            )
            .define("floodlights", true);
        SKYWRITING = builder
            .comment(
                "Whether a zeppelin is built with a skywriting board in her control car, whose picture buttons have her autopilot",
                "fly a circle, a heart, a figure of eight or a wave across the sky, smoke in her cells' colours streaming from under",
                "her belly. Off, the boards already built do nothing."
            )
            .define("skywriting", true);
        SPEED_BOOST = builder
            .comment(
                "Whether a zeppelin is built with a speed boost button in her control car and boosters round her tail, which the",
                "button fires for ten seconds while her autopilot holds the heading she had and her height. Off, the buttons",
                "already built do nothing."
            )
            .define("speedBoost", true);
        ZEPPELIN_POST = builder
            .comment(
                "Whether a zeppelin is built with a franking machine in her control car, which prints a postcard of the land under",
                "her from a sheet of paper, and over a village drops it a mail sack on a parachute. Off, the machines already built",
                "do nothing."
            )
            .define("zeppelinPost", true);
        TOWED_BANNER = builder
            .comment(
                "Whether a zeppelin is built with a banner cleat on her control car's back wall, which streams a banner with her",
                "name on it from her tail on a tow rope, hanging lower the slower she flies. Off, the cleats already built do",
                "nothing."
            )
            .define("towedBanner", true);
        SKY_CRANE = builder
            .comment(
                "Whether a zeppelin is built with a sky crane: a winch on her lounge's glass floor whose hook, lowered under",
                "her, catches and lifts an animal, a boat, a minecart, or a chest, barrel or cargo crate with all it holds. Off,",
                "the winches already built do nothing."
            )
            .define("skyCrane", true);
        FLYING_CARRIER = builder
            .comment(
                "Experimental: whether a zeppelin is built with a flight deck along her crown, whose docking clamps lock down a",
                "plane (another ship of Sable's) come to rest on it and keep its tanks full, until its release lever is pulled.",
                "Off, the decks already built hold nothing."
            )
            .define("flyingCarrier", false);
        builder.pop();

        builder.push("spaceWeather");
        SPACE_WEATHER = builder
            .comment(
                "Whether a rocket or a freighter flying above y 1,500, far past the black of space, now and then meets space",
                "weather: a meteor shower of glowing streaks, a few of them striking her with a sharp ping and sparks, an asteroid",
                "storm of big tumbling rocks, some crashing into her and throwing her about, or a solar flare that floods the view",
                "with glare, crackles in her speakers and makes her screens flicker. Her flight deck, or the freighter's computer,",
                "warns everyone aboard with an alarm and a picture first, and says when it is all clear. Nothing breaks and nobody",
                "aboard is hurt, and her autopilot brings her back on course. Without it, space stays calm."
            )
            .define("enabled", true);
        SPACE_WEATHER_EVERY = builder
            .comment(
                "How many minutes a ship flies above y 1,500 between bouts of space weather, on average: each comes at random,",
                "and never within a minute of the last."
            )
            .defineInRange("every", 4, 1, 60);
        SPACE_WEATHER_STRENGTH = builder
            .comment(
                "How hard space weather throws a ship about, as a share of how hard it does by default, in percent: 0 leaves her",
                "steady through it, every strike still seen, heard and felt, and 200 shoves her twice as hard."
            )
            .defineInRange("strength", 100, 0, 300);
        SPACE_WEATHER_BLACKOUT = builder
            .comment(
                "How many seconds a solar flare's pulse knocks out a ship's engines, lights and screens; her lift gone, she falls",
                "until they come back. Five seconds drops a freighter some 140 blocks. Fifteen drop her some 900 and she falls",
                "1,000 more before she has caught herself, so with less air than that under her she comes down hard on the",
                "ground, then lifts off again by herself. 0 leaves her systems running through a flare."
            )
            .defineInRange("flareBlackoutSeconds", 5, 0, 60);
        builder.pop();

        builder.push("rocket");
        ROCKET_LIFT = builder
            .comment(
                "Whether a rocket is built with a lift: a car riding her glass shaft from the entry story to the cockpit, a call button",
                "by each landing's sliding doors and a picture board inside to pick a floor by its icon. It stands open at a floor while",
                "she flies. Off, the shaft is open, and rockets already built keep theirs."
            )
            .define("lift", true);
        ROCKET_ROOMS = builder
            .comment(
                "Whether a rocket's stories are built furnished, each its room: the entry, the galley and mess, the bunk room, the",
                "greenhouse, the swimming pool, the techno club and the observation deck (the cargo bay and the lab are their own). Off,",
                "her stories are bare. Rockets already built keep theirs."
            )
            .define("rooms", true);
        ROCKET_FINISHES = builder
            .comment(
                "Whether a rocket's rooms are finished inside, each its own way: her cover lined where a room sees it (riveted steel",
                "in the entry, oak in the mess, quilting in the bunk room, mosaic at the pool, and so on), and each floor and ceiling",
                "in its room's materials. Off, her inside is her plain cover and grey floors. Rockets already built keep theirs."
            )
            .define("finishes", true);
        ROCKET_CEILING = builder
            .comment(
                "How high a rocket's or a freighter's autopilot can be set to climb, in world blocks: far over the top of the world,",
                "where there is only sky and it turns to space as she climbs (black by y 1,200), meeting space weather from y",
                "1,500 up. She flies as well at 9,000 as at 1,000; the higher, the longer the trip up and back down. The airliner",
                "and the zeppelin stay under the top of the world."
            )
            .defineInRange("highestAltitude", 2500, 100, 10000);
        LED_STRIPS = builder
            .comment(
                "Whether a rocket is built with LED strips round each of her stories, along the floor and under the ceiling, in ten",
                "colours, four brightnesses and four ways of lighting (steady, breathing, chasing round and pulsing to the club's beat),",
                "set from a panel by each story's lift door or from a console in her cockpit for any story or all of them. Rockets",
                "already built keep theirs."
            )
            .define("ledStrips", true);
        ROCKET_POSTERS = builder
            .comment(
                "Whether a rocket is built with space posters on her glass lift shaft: Earthrise on her entry story, the solar system in",
                "her bunk room, a black hole in her lab and a quasar on her observation deck. Rockets already built keep theirs."
            )
            .define("posters", true);
        ROCKET_WINDOW_DIMMERS = builder
            .comment(
                "Whether a rocket is built with a window dimmer by each story's lift door: four oval windows on it, clear, light,",
                "medium and dark, set the glass of every oval window of the story at once. Rockets already built keep theirs."
            )
            .define("windowDimmers", true);
        ROCKET_ROVER = builder
            .comment(
                "Whether a rocket is built with a rover bay on her cargo story: a door in her skin folding down into a platform,",
                "from which a winch lowers a six-wheeled rover to the ground, driven with its paired remote and hoisted back",
                "aboard. Rockets already built keep theirs."
            )
            .define("rover", true);
        ROCKET_AIRSTAIRS = builder
            .comment(
                "Whether a rocket is built with airstairs at her front door: the airliner's integral airstairs, three blocks wide, coming",
                "down from her door to the ground and folding away into a bay under her entry floor, by her stairs buttons (one by",
                "the door inside, one low on her front right fin) or of their own accord before she lifts off. Off, she is built",
                "with her crew ladder and a three-wide doorway instead. Rockets already built keep theirs."
            )
            .define("airstairs", true);
        ROCKET_PLUMES = builder
            .comment(
                "Whether a rocket's flight shows round her as well as in her engines' own plumes: smoke billowing out over the",
                "ground from under her as she lifts off and lands, fire bursting on the ground while she is close over it, and,",
                "falling back from space, her base glowing and a trail of flame streaming up behind it."
            )
            .define("plumes", true);
        ROCKET_GROUND_FIRE = builder
            .comment(
                "Whether a rocket's engines set the ground under her alight while they blaze close over it, as she lifts off and",
                "lands: fire catching on the ground round under her and burning on after her as any fire does, the grass and",
                "whatever burns near it with it. Only while the doFireTick game rule lets fire spread."
            )
            .define("groundFire", true);
        ROCKET_SCORCH = builder
            .comment(
                "Whether a rocket's engines scorch the grassy ground under her while they blaze close over it, as she lifts off",
                "and lands: grass, mycelium, podzol, moss and paths burnt to bare earth round under her, coarse close under her",
                "engines, and the plants and snow on them burnt away, so a pad of scorched earth is left where she stood."
            )
            .define("scorch", true);
        ROCKET_LAUNCH_BLAST = builder
            .comment(
                "Whether a rocket's engines lighting on the ground send a shockwave out over it: a ring of dust racing out from",
                "under her, throwing back off their feet whoever and whatever stands near, with a boom and a jolt as it",
                "passes each player, and its boom rolling in later and quieter farther off."
            )
            .define("launchBlast", true);
        ROCKET_LOW_GRAVITY = builder
            .comment(
                "Whether everyone aboard a rocket feels light for ten seconds as she reaches the edge of space (y 1,000), jumping",
                "three times as high and landing softly, with a rising glide and a puff of sparkles as it comes on, until her flight",
                "deck announces her artificial gravity and they are their own weight again as they next land. Once a climb; it",
                "lifts the moment they leave her."
            )
            .define("lowGravity", true);
        ROCKET_LAB = builder
            .comment(
                "Whether a rocket is built with her lab: lab counters with microscopes and screens, and her tardigrade in its glass tank",
                "behind the lift shaft, fed moss through a hatch, curling up in space. Off, as with `rooms` off, the lab's story is left",
                "empty. Rockets already built keep theirs."
            )
            .define("lab", true);
        ROCKET_DOME = builder
            .comment(
                "Whether a rocket is built with her dome: the inside of her nose over the cockpit a night sky full of stars, and",
                "a brass orrery hanging from its tip, a glowing sun with its six planets circling it on their arms. Off, as with",
                "`rooms` off, her nose is padded inside as her cockpit is and left bare. Rockets already built keep theirs."
            )
            .define("dome", true);
        ROCKET_CALLOUTS = builder
            .comment(
                "Whether launch control, which counts a rocket down, also calls out her flight's milestones on its radio as she passes",
                "them, each once a flight and never over another voice: \"Tower cleared.\" 40 blocks over the ground, \"Max Q.\" climbing",
                "through y 300, \"Main engine cut-off.\" as she reaches her height, and \"Landing burn.\" as it starts. Off, it only",
                "counts her down."
            )
            .define("callouts", true);
        ROCKET_COCKPIT_CONTROLS = builder
            .comment(
                "Whether a rocket is built with hands-on controls on a centre console round her control head: a boost button, a",
                "stability switch under a striped cover, a flight mode knob, an abort handle and a launch key. Off, they are left out",
                "of rockets built from then on; rockets already built keep theirs."
            )
            .define("cockpitControls", true);
        ROCKET_BOOST = builder
            .comment(
                "Whether a rocket's boost button kicks up her engines for eight seconds, climbing twice as fast or",
                "racing across faster and leaning further, then rests twenty seconds before it boosts again. Off, the button only",
                "sounds its low refusal note."
            )
            .define("boost", true);
        ROCKET_STABILITY_SWITCH = builder
            .comment(
                "Whether a rocket's stability switch, cruising well over the ground, turns off the side jets that",
                "hold her upright, so she starts to tip over and drift until it is switched back on, or snaps back on by itself",
                "before she tips too far. Off, the switch only sounds its low refusal note."
            )
            .define("stabilitySwitch", true);
        ROCKET_ARC_ASCENT = builder
            .comment(
                "Whether a rocket's flight mode knob offers the arc, a gravity turn leaning her toward her pin as",
                "she climbs and cruising across faster and leaning further. Off, the knob still turns to it, sounding its low",
                "refusal note, and she flies it straight up as ever."
            )
            .define("arcAscent", true);
        builder.pop();

        builder.push("freighter");
        FREIGHTER_ENGINE_GRILLE = builder
            .comment(
                "Whether the freighter is built with her engine grille across her stern round her main engines, glowing deep blue",
                "while her thrusters hold her up and bright cyan while her mains push her ahead, seen by day and by night.",
                "Without it, her stern is plain hull, her engines' plumes still showing."
            )
            .define("engineGrille", true);
        FREIGHTER_EXTERIOR = builder
            .comment(
                "Whether the freighter is built with her outside fitted out: satellite dishes turning on her top, her rim and her",
                "belly, antenna masts with blinking red lamps, sensor domes, heat-sink fins over her engines, exhaust vents letting",
                "off wisps, boxes and pipes of machinery on her prongs and floodlights under her. Without them, her hull is bare."
            )
            .define("exterior", true);
        FREIGHTER_PLATING = builder
            .comment(
                "Whether the freighter's outer ring and rim are plated: riveted seams running out from her middle, sectors of",
                "dark gunmetal plate, welded patches, and hazard stripes round her docking collars and under her engines.",
                "Without it, her hull is one pale grey skin."
            )
            .define("plating", true);
        FREIGHTER_OUTSIDE_LIGHTS = builder
            .comment(
                "Whether the freighter is built with her outside lights: a glowing band all round her rim, light strips along",
                "her loading gap and her prongs, red and green navigation lights on her beams and a white one at her stern,",
                "strobes on her prongs' tips and red beacons blinking on her top and under her belly."
            )
            .define("outsideLights", true);
        FREIGHTER_ENGINE_GLOW = builder
            .comment(
                "Whether the freighter's engines glow blue-white at her stern while she flies, soft as her lift holds her up,",
                "brighter and wider the harder her mains push her ahead, and out once she rests."
            )
            .define("engineGlow", true);
        FREIGHTER_EVERY_PLUME = builder
            .comment(
                "Whether every one of the freighter's thrusters trails Create Propulsion's plume: all six of her mains as she flies",
                "along and all twenty of her lift thrusters as she lifts off and lands. Off, four of her mains and the middle lift",
                "thruster of each cluster do, which looks much the same from outside. Every puff of a plume is sent to every player",
                "near and moved along by each of their clients: on, she sends half as many again as she flies along and five times",
                "as many as she lifts off and lands. A freighter already flying takes it up once she is next loaded."
            )
            .define("everyPlume", false);
        FREIGHTER_PAD_REACH = builder
            .comment(
                "How far from a pin the freighter looks for clear ground to come down on, in blocks. She can only look",
                "as far as the world round her is loaded, so past the server's view distance more does nothing."
            )
            .defineInRange("padReach", 200, 32, 256);
        FREIGHTER_NOZZLE_RIMS = builder
            .comment(
                "Whether the freighter's main engines show a nozzle standing out of her stern, a steel bell with a bright lip,",
                "drawn by each client and never built. Without it, each main is the bare thruster in its socket in her grille."
            )
            .define("nozzleRims", true);
        FREIGHTER_GALLERY = builder
            .comment(
                "Whether the freighter's long passages are galleries: space posters along the tunnel down her port prong, an",
                "earthrise, a black hole and the solar system, and the fleet's models with planters along the ledges of the tube",
                "out to her cockpit. Without it, both are bare."
            )
            .define("gallery", true);
        FREIGHTER_PORTHOLES = builder
            .comment(
                "Whether the freighter is built with portholes along her long passages, small round windows looking out to the",
                "sky down the outer wall of the tunnel in each prong and down both sides of the tube out to her cockpit.",
                "Without them, those walls are bare."
            )
            .define("portholes", true);
        FREIGHTER_PRONGS = builder
            .comment(
                "Whether the tunnel down the freighter's port prong is a lookout: a seat facing each porthole down its outer wall,",
                "and at its tip, past her nose jet's housing, a nook of seats facing a screen of her outside cameras.",
                "Without it, the tunnel has only its posters and portholes."
            )
            .define("prongs", true);
        FREIGHTER_SMUGGLER_HOLD = builder
            .comment(
                "Whether the freighter has her smugglers' hold: a secret panel in the wall across the tunnel down her starboard",
                "prong, opened by a loose brass rivet beside it, onto a cramped den of contraband crates, a cot, a lantern and a",
                "chest of loot. Without it, that tunnel runs on to her nose jet's housing."
            )
            .define("smugglerHold", true);
        FREIGHTER_CANOPY_DIMMER = builder
            .comment(
                "Whether the freighter's canopy is built of glass a window dimmer darkens, with a dimmer at her pilot's left",
                "hand, its four buttons from clear to all but black. Without it, her canopy is plain smoked glass."
            )
            .define("canopyDimmer", true);
        FREIGHTER_SMOOTH_CANOPY = builder
            .comment(
                "Whether the freighter's canopy is drawn smooth, inside and out: one round dome of tinted glass framed by slim",
                "gunmetal ribs, dimmed with the dimmer as smoothly as clear, in place of her glass and its struts stepping round",
                "her curve block by block. Without it, her canopy is built and drawn block by block, struts and all."
            )
            .define("smoothCanopy", true);
        FREIGHTER_CONSOLE = builder
            .comment(
                "Whether the freighter's cockpit has its console: a half-round desk curving round her pilots' seats, live",
                "readouts of her speed, height, climb and heading, her engines and a sweeping scanner, banks of toggles that",
                "click, and a big boost button that fires her mains flat out a while. Without it, her seats face her canopy bare."
            )
            .define("console", true);
        FREIGHTER_HYPERSPACE_JUMP = builder
            .comment(
                "Whether the freighter can jump to her pin through hyperspace from the jump button on her console: while she",
                "flies to a pin, her drive spools up as the land there loads, the stars stretch into streaks for everyone",
                "aboard, and in a flash she is at the pin, everyone aboard with her, clear over the highest ground there.",
                "Without it, the button only clicks, and her console has her flight readouts in its place."
            )
            .define("hyperspaceJump", true);
        FREIGHTER_HYPERSPACE_WAKE = builder
            .comment(
                "Whether those outside a freighter's hyperspace jump, on the ground or another ship, see it within 192 blocks",
                "and hear it within 640: light gathering on her as her charge whines up and she stretches out ahead into it,",
                "then a flash and streaks shooting off ahead where she was with a deep whump; and where she comes out, a rush",
                "and streaks rushing in, then a flash and a ring of light with a thunderclap as she snaps back out of a",
                "stretch, heard the later the further off. Without it, she only vanishes and reappears for them. Those aboard",
                "see and hear the jump from inside either way."
            )
            .define("hyperspaceWake", true);
        FREIGHTER_CLIMB_BOOST = builder
            .comment(
                "Whether the freighter can climb hard from the climb button on her console: while she flies along, her lift",
                "fires flat out and she climbs straight up far faster than she flies, holding her spot, until she is 300",
                "blocks higher or as high as rocket.highestAltitude lets her go, which her autopilot then holds. Climbing",
                "loads no new land, so the server keeps up. Without it, the button only says no, and her console has a bank",
                "of toggles in its place."
            )
            .define("climbBoost", true);
        FREIGHTER_SLAM_LANDING = builder
            .comment(
                "Whether the freighter can slam down from the slam button on her console: while she flies along with the",
                "ground within 160 blocks, she drives straight down where she is, legs out, far faster than she lands, brakes",
                "hard at the last and hits the ground with a crash, a ring of dust and everyone aboard shaken, then switches",
                "off on her legs. Her autopilot still sets her down gently enough that nothing breaks and nobody is hurt.",
                "Without it, the button only says no, and her console has her flight readouts in its place."
            )
            .define("slamLanding", true);
        FREIGHTER_SPIN = builder
            .comment(
                "Whether the freighter can spin from the spin button on her console: while she flies along high over the",
                "ground, she rolls right over about her length, a whole turn, and comes back level, her autopilot flying",
                "on. Too near the ground for the height a roll takes, the button only says no. Without it, the button only",
                "says no, and her console has her engine gauges in its place. She rights herself when knocked upside down",
                "either way."
            )
            .define("spin", true);
        FREIGHTER_ROBOTS = builder
            .comment(
                "Whether the freighter is built with her deck robots standing in two of her rooms by the doorways, chirping",
                "hello to whoever comes up to them, their heads turning to follow, and beeping to themselves now and then."
            )
            .define("robots", true);
        FREIGHTER_RAMP = builder
            .comment(
                "Whether the freighter is built with her boarding ramp out of her starboard side, which a panel at its head",
                "lets down to the ground on its rams and brings back up. Without it, her well's floor is plain deck."
            )
            .define("ramp", true);
        FREIGHTER_LASER_CROSSBOW = builder
            .comment(
                "Whether the laser crossbow fires: charged by holding use, its amber bolt strikes hostile mobs, never players,",
                "villagers, pets or animals, and never a block. Without it, it only clicks."
            )
            .define("laserCrossbow", true);
        FREIGHTER_TURRETS = builder
            .comment(
                "Whether the freighter is built with her laser cannon on her top dome, a gunner's seat under its glass reached",
                "by a ladder up her core, and whether laser cannons fire: aimed by the seated gunner's look, fired by holding",
                "attack, their bolts strike hostile mobs, never players, villagers, pets, animals or anyone aboard."
            )
            .define("turrets", true);
        FREIGHTER_TURRET_BLASTS = builder
            .comment(
                "Whether a laser cannon's bolt blasts out the blocks round where it strikes, as TNT would and by the world's",
                "rules for TNT, but never a block of any ship, and hurting only hostile mobs. Without it, bolts break nothing."
            )
            .define("turretBlasts", true);
        FREIGHTER_TURRET_THROWS_PLAYERS = builder
            .comment(
                "Whether a laser cannon's bolts and rockets throw players they strike, or go off near, far and high, in creative",
                "mode too, hurting them as they do hostile mobs unless they are in creative mode, never anyone aboard the cannon's",
                "own ship; whoever is thrown comes down unhurt by the fall, under their safety canopy (see safetyCanopy) or",
                "floating down slowly.",
                "Without it, they pass players by as before."
            )
            .define("turretThrowsPlayers", true);
        FREIGHTER_ROCKETS = builder
            .comment(
                "Whether a laser cannon carries rocket pods either side of its barrels, launched by holding use at its gunner's",
                "seat: four rockets, one after another, that home in on a hostile mob the sight is locked on to and go off with",
                "a bang, hurting hostile mobs only and breaking blocks as a creeper does only with turretBlasts on, never a",
                "block of any ship. The pods load again a rocket at a time. Without it, the cannon has no pods."
            )
            .define("rockets", true);
        FREIGHTER_SHIP_HITS = builder
            .comment(
                "Whether a ship struck by a laser cannon's bolt or rocket feels it, though it breaks none of her: rocked a little",
                "where it struck, a bolt lightly and a rocket harder, the hit ringing off her hull with sparks and smoke, and the",
                "view of everyone aboard jolted. Without it, shots strike ships unfelt and unheard."
            )
            .define("shipHits", true);
        FREIGHTER_BANDSTAND = builder
            .comment(
                "Whether the freighter is built with a bandstand in her club, the room through the doorway at her port bow, its",
                "robot jazz trio playing her song to anyone who comes near."
            )
            .define("bandstand", true);
        FREIGHTER_LIGHTS = builder
            .comment(
                "Whether the freighter is built with wall washers round her ring corridor and her rooms, over head height:",
                "warm white, pink in her club and a cool night blue in her crew quarters. Without them, her deck is dark."
            )
            .define("lights", true);
        FREIGHTER_CLUB_BAR = builder
            .comment(
                "Whether the freighter's club is built with a bar along its straight wall: a counter with stools and drink taps,",
                "the robot bartender behind it selling mocktails, and back-bar cabinets beside him."
            )
            .define("clubBar", true);
        FREIGHTER_ROOMS = builder
            .comment(
                "Whether the freighter's rooms are furnished: bunks with their reading lights in her crew quarters, a booth, a",
                "low table among four seats, in her lounge, and racks of cargo and crates in her cargo hold at her bow."
            )
            .define("rooms", true);
        FREIGHTER_TARDIGRADE = builder
            .comment(
                "Whether the freighter's lounge has a small tardigrade, a quarter the size of the rocket's, in a glass tank on a",
                "cabinet stand by its north wall, with a feeding hatch, a pad of moss beside it and more in the cabinet under it."
            )
            .define("tardigrade", true);
        FREIGHTER_SPOOKIE = builder
            .comment(
                "Whether the freighter has her Spookie, a shaggy giant of her crew, and its hammock in her crew quarters: it walks",
                "its rounds of her ring corridor and rooms, greets whoever comes near, purrs when touched or fed, and is never hurt."
            )
            .define("spookie", true);
        FREIGHTER_LED_STRIPS = builder
            .comment(
                "Whether the freighter is built with the rocket's LED strips round each of her rooms, along the foot of its walls,",
                "each room in a colour and a way of lighting of its own, and an LED panel by each room's doorway to set them."
            )
            .define("ledStrips", true);
        FREIGHTER_BEACH = builder
            .comment(
                "Whether the freighter's lounge has its beach: a huge round floor window in the middle of a patch of sand, opened",
                "from its panel to watch the ground pass far below her, sun loungers round it under a parasol, palms and tiki torches."
            )
            .define("beach", true);
        FREIGHTER_FINISHES = builder
            .comment(
                "Whether each of the freighter's rooms has a floor of its own: a steel deck with loading lines in her cargo hold,",
                "cream tiles in her entry hall, wooden boards in her lounge, glowing grating in engineering and a glossy dance floor in",
                "her club. Off, every room has the carpet of the rest of her deck."
            )
            .define("finishes", true);
        FREIGHTER_ENGINEERING = builder
            .comment(
                "Whether the freighter's engineering has its machines: her jump drive, spooled up by hand, which now and then",
                "sputters until it is thumped; her breaker board, a switch for each room's lights; and her coolant valve, cranked",
                "up to a blast of steam."
            )
            .define("engineering", true);
        FREIGHTER_RETRACTING_LEGS = builder
            .comment(
                "Whether the freighter's landing legs draw up into her belly once she is well clear of the ground, their struts",
                "telescoping up and their feet tucking under her, and come down again before she lands. Read when she is built: off,",
                "her legs are fixed. Switched off later, legs already built still come down but no longer go up."
            )
            .define("retractingLegs", true);
        FREIGHTER_TRACTOR_BEAM = builder
            .comment(
                "Whether the freighter is built with her tractor beam under her belly and its pad in her cargo hold, and whether",
                "tractor beams shine: a beam of green light that gently lifts players, animals, villagers and loose items up onto",
                "its pad, and lets down whoever steps onto the pad, never a hostile mob, and never hurting anyone. Without it,",
                "she has no beam and every tractor beam stays dark."
            )
            .define("tractorBeam", true);
        FREIGHTER_STICKER_MAP = builder
            .comment(
                "Whether the freighter is built with her sticker map on her entry hall's wall, and whether sticker maps fill: a",
                "picture sticker lights up with a fanfare for each kind of land a ship lands in for the first time, desert,",
                "jungle, snowy peaks and more, and space as she reaches it; on the ground a board fills from where whoever uses",
                "it has been. Without it, she has no board and every sticker map stays as it is."
            )
            .define("stickerMap", true);
        FREIGHTER_ZERO_GRAVITY = builder
            .comment(
                "Whether the freighter is built with a gravity switch by her club's doorway, and whether gravity switches work: pressed,",
                "everyone in the room floats, mobs and dropped items too, bumping gently off its walls, and pressed again everyone",
                "falls slowly back down, unhurt. Without it, she has no switch and every gravity switch only buzzes."
            )
            .define("zeroGravity", true);
        FREIGHTER_REPAIRS = builder
            .comment(
                "Whether the freighter is built with her running repairs, and whether they run: while she flies along, every few",
                "minutes a fault panel round her rooms sparks or a pipe steams, flashing and crackling until someone whacks it with",
                "a spanner (or a hand), which lights a star on her repair board in engineering. Nothing is ever damaged. Without it,",
                "she has no fault spots or board, and no fault spot ever goes off, not even on redstone."
            )
            .define("repairs", true);
        FREIGHTER_BOWLING = builder
            .comment(
                "Whether the tunnel down the freighter's starboard prong is a bowling lane: maple boards down it to a rack of ten",
                "pins at its end, and a ball return at its head that hands over a ball to bowl. A ball knocks the pins down with a",
                "clatter, all ten a strike with a fanfare, and the pinsetter stands them up again a few seconds later. Without",
                "it, that tunnel is bare. Freighters already built keep theirs."
            )
            .define("bowling", true);
        builder.pop();

        SPEC = builder.build();
    }

    private SkylinersConfig() {
    }
}
