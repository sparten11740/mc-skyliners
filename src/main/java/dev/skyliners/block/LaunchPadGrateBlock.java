package dev.skyliners.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The steel grate over a launch pad's flame pit, a plate of square holes to look down through, and solid underfoot and
 * to what looks down for the ground (see {@code airport.LaunchPad}). Its blocks hide the faces they share, as glass
 * does, so a floor of them reads as one sheet and not as a heap of wire cages. Only the blueprint places it, so it has
 * no item, and it gives nothing back.
 */
public class LaunchPadGrateBlock extends Block {
    public LaunchPadGrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState beside, Direction side) {
        return beside.is(this) || super.skipRendering(state, beside, side);
    }
}
