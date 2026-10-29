package dev.aerolarge.block;

import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The rocket's cover cut to her curve where a room sees it from inside: painted by her livery outside, as her cover
 * is, and lined behind its cut, the faces a room sees, in that room's {@link RocketFinish}. Only her blueprint places
 * it; broken, it gives back the fabric her cover is made from.
 */
public class RocketLinedCoverBlock extends LinedShellBlock {
    public RocketLinedCoverBlock(Properties properties, Supplier<? extends ItemLike> part) {
        super(properties, part);
        registerDefaultState(defaultBlockState().setValue(RocketFinish.PROPERTY, RocketFinish.ENTRY));
    }

    /** The cover cut to a shape, lined behind its cut in a room's finish. */
    public BlockState lined(int shape, RocketFinish finish) {
        return lined(shape, Side.BACK).setValue(RocketFinish.PROPERTY, finish);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RocketFinish.PROPERTY);
    }
}
