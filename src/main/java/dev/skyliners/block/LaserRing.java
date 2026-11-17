package dev.skyliners.block;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The laser path clearer looking all round its turret, as it does on the ground (see {@link LaserLook}): rays cast out
 * every way from under the turret, level with the airliner, in rows from her wheels' hubs to the top of her fin, a
 * sector of the circle each sweep, round and round. So whatever stands about her as she stands or rolls, a house under
 * a wing, a tree by her tail or a wall beside her, is found and cleared as far as the laser reaches, not only what is
 * ahead of her.
 */
final class LaserRing extends LaserLook {
    /**
     * The circle, cut into sectors, one swept at a time, with so many bearings each: a ray every 7.5 degrees, about 12
     * blocks apart at the laser's usual reach of 96.
     */
    private static final int SECTORS = 6;
    private static final int BEARINGS = 8;

    /** The sector it sweeps now. */
    private int sector;

    @Override
    protected int rays() {
        return BEARINGS * ROWS;
    }

    @Override
    protected Ray ray(int index, Lookout look) {
        double bearing = Mth.TWO_PI * (sector * BEARINGS + index % BEARINGS) / (SECTORS * BEARINGS);
        Vec3 nose = look.up().cross(look.right());
        Vec3 way = nose.scale(Math.cos(bearing)).add(look.right().scale(Math.sin(bearing)));
        return new Ray(look.muzzle().add(look.up().scale(rise(index / BEARINGS))), way);
    }

    @Override
    protected void swept() {
        sector = (sector + 1) % SECTORS;
    }

    /**
     * Whether a point lies in the airliner's way all round: within range of the turret, level with her, and from her
     * wheels' hubs to the top of her fin, so nothing under her wheels, the ground she stands on, is ever in the way.
     */
    @Override
    boolean inWay(Lookout look, Vec3 point) {
        Vec3 offset = point.subtract(look.muzzle());
        double rise = offset.dot(look.up());
        return offset.subtract(look.up().scale(rise)).length() < look.range() && rise >= low && rise <= high;
    }
}
