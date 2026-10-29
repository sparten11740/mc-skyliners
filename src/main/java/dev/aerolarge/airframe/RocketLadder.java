package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.RungLadderBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The crew's ladder up to the rocket's door: a column of fabric from the ground beside the doorway, the pool's chrome
 * ladder up its front (see {@link dev.aerolarge.block.RungLadderBlock}), its rails curling over at the top, and there a
 * step out in front of the doorway to stand on and walk in from. It stands beside the side jet under the door, never
 * in its exhaust, and touches the skin face to face, so it is assembled with the rocket and rides along (a vanilla
 * ladder outside the skin would be left behind holding it); its foot stands on the ground with the fins'. She is built
 * with it only where the server config's {@code rocket.airstairs} is off, and with her airstairs instead where it is on
 * (see {@link RocketAirstairs}).
 */
final class RocketLadder implements RocketPiece {
    /** How far to the side of the door's middle the column stands, in blocks: just past the doorway's edge. */
    private static final int BESIDE = 2;

    private final RocketLayout layout;

    RocketLadder(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return !AerolargeConfig.ROCKET_AIRSTAIRS.get();
    }

    @Override
    public void build() {
        if (layout.floors().isEmpty()) {
            return;
        }
        int floor = layout.floors().getFirst();
        int skin = layout.frontSkin(BESIDE, floor);
        int column = skin - 1;
        BlockState fabric = AerolargeRegistry.ZEPPELIN_FABRIC.get().defaultBlockState();
        BlockState ladder = AerolargeRegistry.POOL_LADDER.get().defaultBlockState().setValue(RungLadderBlock.FACING, Direction.NORTH);
        for (int y = 0; y <= floor; y++) {
            layout.set(BESIDE, y, column, fabric);
            layout.set(BESIDE, y, column - 1, ladder.setValue(RungLadderBlock.TOP, y == floor));
        }
        for (int x = -BESIDE + 1; x < BESIDE; x++) {
            step(x, floor, column);
        }
    }

    /** The step in front of the doorway at a block, from the skin out to the column's front. */
    private void step(int x, int floor, int column) {
        for (int z = column; z < layout.frontSkin(x, floor); z++) {
            layout.set(x, floor, z, AerolargeRegistry.CABIN_FLOOR.get().defaultBlockState());
        }
    }
}
