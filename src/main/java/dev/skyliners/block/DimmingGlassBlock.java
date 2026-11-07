package dev.skyliners.block;

import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Glass cut to a hull's curve, as the windscreen's is (see {@link CockpitGlassBlock}), that a window dimmer darkens as
 * it does the rocket's oval windows, through the same {@link OvalWindowPieceBlock#DIM}: the freighter's canopy. Each
 * darkness is drawn with denser glass, from the smoked glass at clear to all but black; it darkens the view through
 * it, not the light inside.
 */
public class DimmingGlassBlock extends CockpitGlassBlock {
    public DimmingGlassBlock(Properties properties, Supplier<? extends ItemLike> part) {
        super(properties, part);
        registerDefaultState(defaultBlockState().setValue(OvalWindowPieceBlock.DIM, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OvalWindowPieceBlock.DIM);
    }
}
