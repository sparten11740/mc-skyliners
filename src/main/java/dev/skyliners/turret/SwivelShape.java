package dev.skyliners.turret;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What of a laser cannon's swivel stands out past its block, to collide with (see {@link LaserCannonBlock}): its twin
 * barrels and its rocket pods, wherever they point. It is a row of boxes along them, from its block's corner, each the
 * bounds of a short length of them turned to the aim: across both barrels out past the pods, then across both barrels
 * alone. A player walks into its barrels as they are drawn rather than through them, however they are turned.
 */
final class SwivelShape {
    /**
     * Where along its barrels, from their pivot, they first stand out of its housing, and how far either way of each
     * barrel's middle they reach, about its cooling jacket's round, in blocks.
     */
    private static final double BARRELS_FROM = 0.35;
    private static final double BARREL_ROUND = 0.1;
    /**
     * How far back of the pivot its rocket pods reach, and how far either way of each pod's middle they reach, across
     * and up, in blocks, as its renderer draws them.
     */
    private static final double POD_BACK = 0.35;
    private static final double POD_HALF_WIDTH = 0.12;
    private static final double POD_HALF_HEIGHT = 0.2;
    /**
     * How long a length of the barrels or pods each box takes in at most, in blocks: a few long boxes, rather than many
     * short ones, are quick to work out again as the barrels swing, which they are each tick they turn a degree, at the
     * cost of boxes a little larger than the barrels while they point askew of the blocks.
     */
    private static final double STEP = 0.5;

    private SwivelShape() {
    }

    /** Its barrels, and its pods if it has them, turned to an aim in degrees, round as Minecraft turns and up. */
    static VoxelShape of(float yaw, float pitch, boolean pods) {
        double barrelsOut = LaserCannonBlockEntity.BARRELS_APART + BARREL_ROUND;
        double barrelsTo = LaserCannonBlockEntity.BARREL + BARREL_ROUND;
        if (!pods) {
            return row(Shapes.empty(), yaw, pitch, BARRELS_FROM, barrelsTo, barrelsOut, BARREL_ROUND).optimize();
        }
        double podsTo = LaserCannonBlockEntity.POD_MOUTH;
        VoxelShape shape = row(Shapes.empty(), yaw, pitch, -POD_BACK, podsTo, LaserCannonBlockEntity.POD_OUT + POD_HALF_WIDTH,
            POD_HALF_HEIGHT);
        return row(shape, yaw, pitch, podsTo, barrelsTo, barrelsOut, BARREL_ROUND).optimize();
    }

    /**
     * Adds a row of boxes along its middle line from so far along it to so far, each the bounds of a length of it so
     * far either way across and up, turned to the aim.
     */
    private static VoxelShape row(VoxelShape shape, float yaw, float pitch, double from, double to, double across, double up) {
        int pieces = Math.max(1, (int) Math.ceil((to - from) / STEP));
        VoxelShape row = shape;
        for (int piece = 0; piece < pieces; piece++) {
            double near = from + (to - from) * piece / pieces;
            double far = from + (to - from) * (piece + 1) / pieces;
            row = Shapes.joinUnoptimized(row, length(yaw, pitch, near, far, across, up), BooleanOp.OR);
        }
        return row;
    }

    /** The box round a length of the swivel, from so far along to so far, so far either way across and up. */
    private static VoxelShape length(float yaw, float pitch, double near, double far, double across, double up) {
        Vec3 low = new Vec3(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
        Vec3 high = low.scale(-1);
        for (int corner = 0; corner < 8; corner++) {
            Vec3 at = LaserCannonBlockEntity.tilted(yaw, pitch, (corner & 1) == 0 ? -across : across,
                (corner & 2) == 0 ? -up : up, (corner & 4) == 0 ? near : far);
            low = new Vec3(Math.min(low.x, at.x), Math.min(low.y, at.y), Math.min(low.z, at.z));
            high = new Vec3(Math.max(high.x, at.x), Math.max(high.y, at.y), Math.max(high.z, at.z));
        }
        double pivotUp = LaserCannonBlockEntity.PIVOT_UP;
        return Shapes.box(0.5 + low.x, pivotUp + low.y, 0.5 + low.z, 0.5 + high.x, pivotUp + high.y, 0.5 + high.z);
    }
}
