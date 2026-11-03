package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The steel grate over a launch pad's flame pit, a plate of square holes to look down through, and solid underfoot and
 * to what looks down for the ground (see {@code airport.LaunchPad}). Its blocks hide the faces they share, as glass
 * does, so a floor of them reads as one sheet and not as a heap of wire cages. Only the blueprint places it, so it has
 * no item of its own, and it gives nothing back.
 */
public class LaunchPadGrateBlock extends Block {
    public LaunchPadGrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState beside, Direction side) {
        return beside.is(this) || super.skipRendering(state, beside, side);
    }

    /** Picked, the zeppelin's catwalk grating, which it is drawn as and which can be put up by hand. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(SkylinersRegistry.ZEPPELIN_CATWALK_ITEM.get());
    }
}
