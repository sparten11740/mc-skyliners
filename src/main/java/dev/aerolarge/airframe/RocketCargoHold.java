package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CargoCrateBlock;
import dev.aerolarge.block.UldContainerBlock;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Her cargo bay's load, round its front and sides, well clear of her rover's cradle and its way out at her back (see
 * {@link RocketRoverBay}): a pair of the airliner's baggage containers either side of the way in from the lift, their
 * doors facing in, care-package crates stacked two high either side of the side windows, ready to be packed and
 * dropped, and halo lights hanging round the shaft. Laid after the rover's bay, so nothing of it stands where the bay has
 * put anything.
 */
final class RocketCargoHold extends RocketRoom {
    /** Her cargo bay's story, from the entry story up. */
    static final int STORY = 1;
    /** How far in from the skin's inside the containers and the crates stand, in blocks. */
    private static final double CONTAINERS_IN = 3.5;
    private static final double CRATES_IN = 2.5;
    /** How far round from her front the containers and the crates' stacks stand, in degrees: none toward her back. */
    private static final double[] CONTAINERS = {42, 318};
    private static final double[] CRATES = {56, 64, 124, 236, 296, 304};
    /** How many crates a stack is. */
    private static final int STACKED = 2;

    RocketCargoHold(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        int y = floor() + 1;
        double out = wall() - CONTAINERS_IN;
        for (int k = 0; k < CONTAINERS.length; k++) {
            double round = CONTAINERS[k];
            container(new BlockPos(across(out, round), y, along(out, round)), inward(round), k);
        }
        lightsRoundShaft();
        double crates = wall() - CRATES_IN;
        for (double round : CRATES) {
            BlockState crate = AerolargeRegistry.CARGO_CRATE.get().defaultBlockState().setValue(CargoCrateBlock.FACING, inward(round));
            for (int up = 0; up < STACKED; up++) {
                put(across(crates, round), y + up, along(crates, round), crate);
            }
        }
    }

    /** A baggage container with its door facing the way given, from a corner, where every block of it is free. */
    private void container(BlockPos origin, Direction facing, int number) {
        BlockState state = AerolargeRegistry.ULD_CONTAINER.get().defaultBlockState().setValue(UldContainerBlock.FACING, facing)
            .setValue(UldContainerBlock.NUMBER, number % (UldContainerBlock.NUMBER.getPossibleValues().size()));
        Map<BlockPos, BlockState> corners = UldContainerBlock.corners(origin, state);
        if (corners.keySet().stream().allMatch(at -> free(at.getX(), at.getY(), at.getZ()))) {
            corners.forEach((at, corner) -> layout.set(at.getX(), at.getY(), at.getZ(), corner));
        }
    }
}
