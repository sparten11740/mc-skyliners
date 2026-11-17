package dev.skyliners.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The laser path clearer looking along the airliner's flight path (see {@link LaserLook}): rays cast along it from a
 * grid of points across her front, as wide as her wingspan and from her wheels' hubs to the top of her fin, so it finds
 * what she would run into. A turret under a wing looks only along its own side of her, from her middle out to that
 * wingtip, its rays twice as close; one under her middle looks across her whole span.
 */
final class LaserAim extends LaserLook {
    /** The columns of rays across the airliner's front: a ray about every 13 blocks of wingspan at scale 2. */
    private static final int COLUMNS = 13;
    /**
     * How far from the middle of the airliner a turret has to be, in blocks across, to look along its own side of her
     * only: past the fuselage, out under a wing.
     */
    private static final double SIDE_TURRET = 10;

    /** How far the airliner reaches from the turret to its left (negative) and right, in blocks. */
    private double left;
    private double right;

    /** Measures her as every look does, and how far she reaches to either side; under a wing, its own side only. */
    @Override
    void outline(BoundingBox blocks, BlockPos turret, Direction nose, double muzzleY) {
        super.outline(blocks, turret, nose, muzzleY);
        Direction across = nose.getClockWise();
        double first = (blocks.minX() - turret.getX()) * across.getStepX() + (blocks.minZ() - turret.getZ()) * across.getStepZ();
        double second = (blocks.maxX() - turret.getX()) * across.getStepX() + (blocks.maxZ() - turret.getZ()) * across.getStepZ();
        left = Math.min(first, second);
        right = Math.max(first, second);
        double middle = (left + right) / 2;
        if (middle < -SIDE_TURRET) {
            left = middle;
        } else if (middle > SIDE_TURRET) {
            right = middle;
        }
    }

    /** How far out from the path the airliner reaches any way, in blocks: the most of its outline. */
    double reach() {
        return Math.max(Math.max(-left, right), Math.max(-low, high));
    }

    @Override
    protected int rays() {
        return COLUMNS * ROWS;
    }

    @Override
    protected Ray ray(int index, Lookout look) {
        double out = left + (right - left) * (index % COLUMNS) / (COLUMNS - 1);
        return new Ray(look.muzzle().add(look.right().scale(out)).add(look.up().scale(rise(index / COLUMNS))), look.path());
    }

    /**
     * Whether a point lies in the airliner's way: ahead of the muzzle along the path, within range, and within its
     * outline round the path: out to its wingtips, and from its wheels' hubs to the top of its fin, so nothing under
     * its wheels, the ground it rolls on, is ever in the way.
     */
    @Override
    boolean inWay(Lookout look, Vec3 point) {
        Vec3 offset = point.subtract(look.muzzle());
        double ahead = offset.dot(look.path());
        double side = offset.dot(look.right());
        double rise = offset.dot(look.up());
        return ahead > 0 && ahead < look.range() && side >= left && side <= right && rise >= low && rise <= high;
    }
}
