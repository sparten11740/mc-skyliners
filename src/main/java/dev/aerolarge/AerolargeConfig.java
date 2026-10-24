package dev.aerolarge;

import dev.aerolarge.airframe.ShellStyle;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings: how big the airliner is built and which other mods' blocks make it fly. */
public final class AerolargeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SCALE;
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ModConfigSpec.EnumValue<ShellStyle> SHELL_STYLE;
    public static final ModConfigSpec.ConfigValue<String> WING_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> ENGINE_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> ASSEMBLER_BLOCK;
    public static final ModConfigSpec.IntValue THRUST_SETTING;
    public static final ModConfigSpec.ConfigValue<String> WHEEL_MOUNT_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> TIRE_ITEM;
    public static final ModConfigSpec.BooleanValue RETRACTABLE_GEAR;
    public static final ModConfigSpec.IntValue AUTOPILOT_CLIMB;
    public static final ModConfigSpec.DoubleValue AUTOPILOT_ROTATE_SPEED;
    public static final ModConfigSpec.DoubleValue AUTOPILOT_CRUISE_SPEED;
    public static final ModConfigSpec.BooleanValue CONTROL_SURFACES;
    public static final ModConfigSpec.BooleanValue ATC_CHATTER;
    public static final ModConfigSpec.DoubleValue ATC_VOLUME;
    public static final ModConfigSpec.BooleanValue TURBULENCE;
    public static final ModConfigSpec.BooleanValue AIR_POCKETS;
    public static final ModConfigSpec.BooleanValue DECOMPRESSION;
    public static final ModConfigSpec.BooleanValue VILLAGER_PASSENGERS;

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
        CONTROL_SURFACES = builder
            .comment(
                "Experimental: whether the autopilot rolls and pitches the airliner with control surfaces, turning the wing blocks of the",
                "outer wings' trailing edges (ailerons) and of the tailplane over, rather than with the control jets. The jets stay for",
                "safety: they do it all at take-off and landing speeds and whenever the airliner is past the attitude the autopilot keeps",
                "it within, and they add whatever more the surfaces cannot give. Only airliners built since the surfaces came in have them;",
                "older ones fly on their jets either way."
            )
            .define("controlSurfaces", false);
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

        SPEC = builder.build();
    }

    private AerolargeConfig() {
    }
}
