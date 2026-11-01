package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.OrreryBlockEntity;
import dev.skyliners.block.RocketFinish;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

/**
 * Her dome: the inside of her nose over the ring of ceiling on the cockpit's glass, finished as a night sky full of
 * stars (see {@link RocketFinish#DOME}), over which the clients draw a planetarium, and a brass orrery hanging from its
 * tip (see {@link dev.skyliners.block.OrreryBlock}), its sun a few rows over the ring, a light round it, and its
 * planets circling inside the hole in the ring, so all of it is seen from the cockpit, as far out as the dome leaves
 * room for at her scale. Where the hole is too narrow for an orrery worth hanging, only its mount is put up, for the
 * planetarium; where there is no hole, as in the smallest rocket, nothing. The server config's {@code rocket.dome}
 * leaves it out, as {@code rocket.rooms} leaves out every room: her nose is then padded inside as her cockpit is, and
 * bare.
 */
final class RocketDome implements RocketPiece {
    /** How many rows over the dome's floor the orrery's sun hangs at most, and at least. */
    private static final int SUN_UP = 4;
    private static final int SUN_LEAST = 2;
    /**
     * How near the widest orrery the dome leaves room for one with its sun higher must come to be hung there, a share:
     * higher, it is looked up at more nearly from under it, and reads better.
     */
    private static final double HIGHER_FOR = 0.97;
    /**
     * The narrowest hole in the ring of ceiling worth putting anything up over, from her axis to its edge, in blocks.
     */
    private static final double OCULUS_LEAST = 1;
    /** How bright the light round the sun is, in light levels: enough to see the orrery by, not the stars out. */
    private static final int SUN_LIGHT = 10;

    private final RocketLayout layout;

    RocketDome(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return layout.rooms() && SkylinersConfig.ROCKET_DOME.get();
    }

    @Override
    public void build() {
        layout.finishNose(RocketFinish.DOME);
        double oculus = opening(layout.domeFloor());
        if (oculus < OCULUS_LEAST) {
            return;
        }
        int sun = sunRow(oculus);
        int tip = tip(sun);
        if (tip <= sun + 1 || !layout.isOpen(0, sun, 0)) {
            return;
        }
        double widest = OrreryBlockEntity.reachIn(oculus, opening(sun + 1));
        double reach = widest >= OrreryBlockEntity.REACH_LEAST ? widest : 0;
        BlockPos mount = new BlockPos(0, tip, 0);
        layout.set(0, tip, 0, SkylinersRegistry.ORRERY.get().defaultBlockState());
        if (reach > 0) {
            layout.set(0, sun, 0, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, SUN_LIGHT));
        }
        double drop = tip - sun;
        BlockPos head = layout.autopilot();
        layout.fit(mount, (blockEntity, rotation) -> {
            if (blockEntity instanceof OrreryBlockEntity orrery) {
                orrery.configure(drop, reach, head == null ? null : head.subtract(mount).rotate(rotation));
            }
        });
    }

    /**
     * The row the sun hangs in: as high over the dome's floor as it may, unless lower leaves the orrery clearly more
     * room, the dome narrowing over it.
     */
    private int sunRow(double oculus) {
        int floor = layout.domeFloor();
        double widest = 0;
        for (int up = SUN_LEAST; up <= SUN_UP; up++) {
            widest = Math.max(widest, OrreryBlockEntity.reachIn(oculus, opening(floor + up + 1)));
        }
        for (int up = SUN_UP; up > SUN_LEAST; up--) {
            if (OrreryBlockEntity.reachIn(oculus, opening(floor + up + 1)) >= widest * HIGHER_FOR) {
                return floor + up;
            }
        }
        return floor + SUN_LEAST;
    }

    /** The highest open block inside her nose on her axis, the orrery's mount under her tip; the sun's row for none. */
    private int tip(int sun) {
        for (int y = layout.height(); y > sun; y--) {
            if (layout.isOpen(0, y, 0) && layout.inFrom(0, y, 0, 0)) {
                return y;
            }
        }
        return sun;
    }

    /**
     * How far out from her axis a row is open, to the nearest of her blocks on it, in blocks: her axis runs up the
     * middle of the blocks over x 0, z 0.
     */
    private double opening(int y) {
        int out = (int) Math.ceil(layout.radius()) + 2;
        int side = 2 * out + 1;
        double nearest = out;
        for (int i = 0; i < side * side; i++) {
            int x = i % side - out;
            int z = i / side - out;
            if (!layout.isOpen(x, y, z)) {
                nearest = Math.min(nearest, OrreryBlockEntity.nearAxis(x, z));
            }
        }
        return nearest;
    }
}
