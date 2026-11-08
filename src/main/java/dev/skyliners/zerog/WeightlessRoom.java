package dev.skyliners.zerog;

import dev.skyliners.block.LedPanelBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The room a gravity switch makes weightless (see {@link GravitySwitchBlockEntity}), in the level as the switch stands:
 * aboard a ship, among her blocks, in her plot. A box, and for a room shaped as a wedge round a ship's axis, as the
 * freighter's are, whose box reaches into her ring corridor and her other rooms, the wedge it lies in and how far out
 * from the axis it starts, past the corridor.
 */
public record WeightlessRoom(BoundingBox box, @Nullable LedPanelBlockEntity.Wedge wedge, double inner) {
    /** Whether a block lies in the room. */
    public boolean contains(BlockPos pos) {
        if (!box.isInside(pos)) {
            return false;
        }
        if (wedge == null) {
            return true;
        }
        double out = Math.hypot(pos.getX() - wedge.axis().getX(), pos.getZ() - wedge.axis().getZ());
        return out >= inner && wedge.contains(pos);
    }

    /** Whether a point lies in the room. */
    public boolean contains(Vec3 point) {
        return contains(BlockPos.containing(point));
    }

    /** The box round the room, as a space to look for things in. */
    public AABB bounds() {
        return AABB.of(box);
    }

    /** The middle of the box round the room. */
    public Vec3 middle() {
        return bounds().getCenter();
    }
}
