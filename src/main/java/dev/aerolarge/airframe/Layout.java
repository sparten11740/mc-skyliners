package dev.aerolarge.airframe;

import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An airframe cut into blocks before it is put anywhere, drawn nose north, as {@link AirframeBuild} lays it down.
 * Positions are relative to the nose's tip, x to starboard (east), y up from the ground and z back toward the tail
 * (south).
 */
public interface Layout {
    /**
     * Hands over every block, air included, in the order they go in: the air first, so whatever stands in the way is
     * cleared before anything goes in, then the rest from the bottom up.
     */
    void inOrder(BiConsumer<BlockPos, BlockState> cell);

    /** How many blocks {@link #inOrder} hands over. */
    int size();

    /** The block entities to set up once they stand, by layout position (see {@link Fitting}). */
    Map<BlockPos, Fitting> fittings();

    /**
     * Whether it is glued together with Create's Super Glue once it stands, which is what a Physics Assembler takes as
     * the structure to assemble; one assembled some other way needs none.
     */
    default boolean glued() {
        return true;
    }

    /**
     * The key of a message about building it ({@code building}, {@code built} or {@code too_high}), saying it as
     * this airframe: the airliner's, unless it says otherwise.
     */
    default String message(String what) {
        return "message.aerolarge." + what;
    }
}
