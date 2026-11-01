package dev.skyliners.block;

import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * How a fitting a player puts up is fixed, and so the shape it takes: hung under a ceiling, fixed to a wall or
 * standing on the floor, each shape drawing in what holds it there (a lamp's stand on the floor, a sconce's arm on a
 * wall, a hoop's pole). A fitting keeps it in its state as MOUNT, by name, among the mounts that make sense for it
 * (see {@link #property}); one saved before it had a mount loads as its block's default, the shape it had then. Where
 * a player puts one is chosen by {@link Mounting#support}.
 */
public enum Mount implements StringRepresentable {
    CEILING,
    WALL,
    FLOOR;

    /** A fitting's mount, among those given, the ways it can be put up. */
    public static EnumProperty<Mount> property(Mount... mounts) {
        return EnumProperty.create("mount", Mount.class, mounts);
    }

    /** How a fitting is fixed to a block the way given from it: under it, beside it or on it. */
    public static Mount against(Direction toward) {
        return switch (toward) {
            case UP -> CEILING;
            case DOWN -> FLOOR;
            default -> WALL;
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
