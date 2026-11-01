package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.OrreryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The limit of her planetarium (see {@link dev.aerolarge.block.DomeLimitBlock}): the clients draw its sky inside the
 * nearest her dome's lining comes to her axis in each row (see the client's {@code OrreryDome}), which leaves gaps
 * behind the sky round the rest of the row, out to the lining's steps. Laid in those gaps, and a little in from them, so
 * whoever comes up through the hole in the ring of ceiling stops with their eyes still in front of the sky. Laid last,
 * so it fills in round all else in her dome. Only where an orrery's mount says there is a sky.
 */
final class RocketDomeLimit implements RocketPiece {
    /** How far in from a row's lining a block's middle may lie and still be filled, in blocks: a player's eyes stay in. */
    private static final double MARGIN = 0.5;
    /** How much further out than her radius a row is looked over, in blocks, her skin included. */
    private static final int LOOK_PAST = 2;
    private static final BlockState LIMIT = AerolargeRegistry.DOME_LIMIT.get().defaultBlockState();

    private final RocketLayout layout;

    RocketDomeLimit(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return layout.rooms() && AerolargeConfig.ROCKET_DOME.get();
    }

    @Override
    public void build() {
        int floor = layout.domeFloor();
        int mount = mountRow(floor);
        for (int y = floor + 1; y <= mount; y++) {
            row(y);
        }
    }

    /** The row of the orrery's mount on her axis, the last with any sky in it; under the dome's floor for none. */
    private int mountRow(int floor) {
        for (int y = layout.height(); y > floor; y--) {
            if (layout.stateAt(0, y, 0).is(AerolargeRegistry.ORRERY.get())) {
                return y;
            }
        }
        return floor;
    }

    /** Fills the open blocks of a row inside her that lie further out than the sky comes, less the margin. */
    private void row(int y) {
        int out = (int) Math.ceil(layout.radius()) + LOOK_PAST;
        double sky = opening(y, out) - MARGIN;
        for (BlockPos at : BlockPos.betweenClosed(-out, y, -out, out, y, out)) {
            if (Math.hypot(at.getX(), at.getZ()) > sky && layout.isOpen(at.getX(), y, at.getZ()) && layout.inFrom(at.getX(), y, at.getZ(), 0)) {
                layout.set(at.getX(), y, at.getZ(), LIMIT);
            }
        }
    }

    /** How far out from her axis a row is open, the nearest her lining comes round it, as the sky is drawn by. */
    private double opening(int y, int out) {
        double nearest = out;
        for (BlockPos at : BlockPos.betweenClosed(-out, y, -out, out, y, out)) {
            if (!layout.isOpen(at.getX(), y, at.getZ()) && !inTheOpen(layout.stateAt(at.getX(), y, at.getZ()))) {
                nearest = Math.min(nearest, OrreryBlockEntity.nearAxis(at.getX(), at.getZ()));
            }
        }
        return nearest;
    }

    /** Whether something the sky is drawn round rather than in front of hangs here: the mount and the sun's light. */
    private static boolean inTheOpen(BlockState state) {
        return state.is(AerolargeRegistry.ORRERY.get()) || state.is(Blocks.LIGHT);
    }
}
