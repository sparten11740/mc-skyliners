package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.OrreryBlockEntity;
import dev.aerolarge.block.RocketFinish;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

/**
 * Her dome: the inside of her nose over the ring of ceiling on the cockpit's glass, finished as a night sky full of
 * stars (see {@link RocketFinish#DOME}), and a brass orrery hanging from its tip (see
 * {@link dev.aerolarge.block.OrreryBlock}), its sun low enough over the ring to be seen from the cockpit through its
 * open middle, a soft light round it, and its planets circling as far out as the dome leaves room for at her scale.
 * The server config's {@code rocket.dome} leaves it out, as {@code rocket.rooms} leaves out every room: her nose is
 * then padded inside as her cockpit is, and bare.
 */
final class RocketDome implements RocketPiece {
    /** How many rows over the dome's floor the orrery's sun hangs: over the glass, in sight from the cockpit. */
    private static final int SUN_UP = 2;
    /** How far in from the dome's lining the outermost planet circles at least, in blocks. */
    private static final double CLEAR = 1.5;
    /** How far out the planets circle at most, and at least for an orrery to be worth hanging, in blocks. */
    private static final double REACH_MOST = 8;
    private static final double REACH_LEAST = 1.5;
    /** How bright the soft light round the sun is, in light levels: enough to see the planets by, not the stars out. */
    private static final int SUN_LIGHT = 8;

    private final RocketLayout layout;

    RocketDome(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return layout.rooms() && AerolargeConfig.ROCKET_DOME.get();
    }

    @Override
    public void build() {
        layout.finishNose(RocketFinish.DOME);
        int sun = layout.domeFloor() + SUN_UP;
        int tip = tip(sun);
        double reach = Math.min(REACH_MOST, room(sun) - CLEAR);
        if (tip <= sun + 1 || reach < REACH_LEAST || !layout.isOpen(0, sun, 0)) {
            return;
        }
        BlockPos mount = new BlockPos(0, tip, 0);
        layout.set(0, tip, 0, AerolargeRegistry.ORRERY.get().defaultBlockState());
        layout.set(0, sun, 0, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, SUN_LIGHT));
        double drop = tip - sun;
        BlockPos head = layout.autopilot();
        layout.fit(mount, (blockEntity, rotation) -> {
            if (blockEntity instanceof OrreryBlockEntity orrery) {
                orrery.configure(drop, reach, head == null ? null : head.subtract(mount).rotate(rotation));
            }
        });
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

    /** How far out from her axis the dome is open at a row, to the middle of its last open block, in blocks. */
    private double room(int y) {
        int x = 0;
        while (x < layout.radius() && layout.isOpen(x + 1, y, 0)) {
            x++;
        }
        return x;
    }
}
