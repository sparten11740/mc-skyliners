package dev.aerolarge.airframe;

import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What a block of the layout needs set up once it stands: the stages an airstair controller works, the engines a
 * thrust lever drives, the tire on a wheel mount. The layout is drawn nose north, so anything a fitting holds
 * relative to its block (offsets, directions) is turned by the same rotation the airliner was placed with.
 */
@FunctionalInterface
public interface Fitting {
    void fit(BlockEntity blockEntity, Rotation rotation);
}
