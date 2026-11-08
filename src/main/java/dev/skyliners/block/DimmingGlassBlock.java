package dev.skyliners.block;

import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Glass cut to a hull's curve, as the windscreen's is (see {@link CockpitGlassBlock}), that a window dimmer darkens as
 * it does the rocket's oval windows, through the same {@link OvalWindowPieceBlock#DIM}: the freighter's canopy. Each
 * darkness is drawn with denser glass, from the smoked glass at clear to all but black; it darkens the view through
 * it, not the light inside. LINED is whether her canopy's lining draws her canopy (see {@link CanopyLiningBlock}): then
 * the block draws nothing, and the lining's smooth dome shows inside and out instead of her blocks stepping round her
 * curve; a canopy built before there was a lining, or without one, is drawn whole as any glass.
 */
public class DimmingGlassBlock extends CockpitGlassBlock {
    public static final BooleanProperty LINED = BooleanProperty.create("lined");

    public DimmingGlassBlock(Properties properties, Supplier<? extends ItemLike> part) {
        super(properties, part);
        registerDefaultState(defaultBlockState().setValue(OvalWindowPieceBlock.DIM, 0).setValue(LINED, false));
    }

    /** Whether a block is canopy glass that draws nothing, its lining drawing the canopy at her true surface. */
    public static boolean lined(BlockState state) {
        return state.getBlock() instanceof DimmingGlassBlock && state.getValue(LINED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OvalWindowPieceBlock.DIM, LINED);
    }
}
