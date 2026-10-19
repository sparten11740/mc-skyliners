package dev.aerolarge;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings: how big the airliner is built and which other mods' blocks make it fly. */
public final class AerolargeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SCALE;
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ModConfigSpec.ConfigValue<String> WING_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> ENGINE_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> ASSEMBLER_BLOCK;
    public static final ModConfigSpec.IntValue THRUST_SETTING;
    public static final ModConfigSpec.ConfigValue<String> WHEEL_MOUNT_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> TIRE_ITEM;
    public static final ModConfigSpec.IntValue AUTOPILOT_CLIMB;
    public static final ModConfigSpec.DoubleValue AUTOPILOT_ROTATE_SPEED;
    public static final ModConfigSpec.DoubleValue AUTOPILOT_CRUISE_SPEED;

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
        builder.pop();

        SPEC = builder.build();
    }

    private AerolargeConfig() {
    }
}
