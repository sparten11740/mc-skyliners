package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The blocks an airframe is built from. The flight hardware (wings that lift, thrusters, the Physics Assembler) comes
 * from Create Propulsion and Create Aeronautics when they are installed, looked up by the ids in the config, so this
 * mod never links against them; without them the same places get airframe panels, or nothing.
 */
record AirframePalette(
    BlockState hull,
    BlockState livery,
    BlockState floor,
    BlockState cabinWindow,
    BlockState light,
    BlockState intake,
    BlockState gear,
    BlockState tire,
    Optional<BlockState> lift,
    Optional<BlockState> engine,
    Optional<BlockState> assembler,
    Optional<BlockState> wheelMount,
    Optional<BlockState> seat,
    Optional<Item> tireItem
) {
    static AirframePalette fromConfig() {
        return new AirframePalette(
            AerolargeRegistry.AIRFRAME_PANEL.get().defaultBlockState(),
            AerolargeRegistry.LIVERY_PANEL.get().defaultBlockState(),
            AerolargeRegistry.CABIN_FLOOR.get().defaultBlockState(),
            AerolargeRegistry.CABIN_WINDOW.get().defaultBlockState(),
            AerolargeRegistry.CABIN_LIGHT.get().defaultBlockState(),
            AerolargeRegistry.ENGINE_INTAKE.get().defaultBlockState(),
            AerolargeRegistry.LANDING_GEAR.get().defaultBlockState(),
            AerolargeRegistry.LANDING_GEAR_TIRE.get().defaultBlockState(),
            block(AerolargeConfig.WING_BLOCK.get()),
            // Thrust points the way the thruster faces; the exhaust leaves the opposite side, out the back.
            block(AerolargeConfig.ENGINE_BLOCK.get()).map(state -> with(state, "facing", "north")),
            block(AerolargeConfig.ASSEMBLER_BLOCK.get()).map(state -> with(with(state, "face", "floor"), "facing", "north")),
            block(AerolargeConfig.WHEEL_MOUNT_BLOCK.get()),
            // Create is required, so its seats are always there for the pilots.
            seat(DyeColor.BLACK),
            id(AerolargeConfig.TIRE_ITEM.get()).flatMap(BuiltInRegistries.ITEM::getOptional)
        );
    }

    /** The lifting surface turned so its face is square to the given direction, or the fallback without one. */
    BlockState lift(Direction normal, BlockState fallback) {
        return lift.map(state -> with(state, "facing", normal.getSerializedName())).orElse(fallback);
    }

    /** One of Create's seats, in the colour. */
    static Optional<BlockState> seat(DyeColor colour) {
        return block("create:" + colour.getSerializedName() + "_seat");
    }

    /** Create's metal girder, for the gym hall's roof trusses. */
    static Optional<BlockState> girder() {
        return block("create:metal_girder");
    }

    /** Whether the state is the lifting surface, turned any way. */
    boolean isLift(BlockState state) {
        return lift.isPresent() && state.is(lift.get().getBlock());
    }

    private static Optional<BlockState> block(String id) {
        return id(id).flatMap(BuiltInRegistries.BLOCK::getOptional).map(Block::defaultBlockState);
    }

    private static Optional<ResourceLocation> id(String id) {
        return Optional.ofNullable(id.isBlank() ? null : ResourceLocation.tryParse(id));
    }

    /** Sets a property by name if the block has it and the value is valid, otherwise leaves the state alone. */
    static BlockState with(BlockState state, String name, String value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        return property == null ? state : with(state, property, value);
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }
}
