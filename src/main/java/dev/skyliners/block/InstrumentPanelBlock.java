package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The flight deck's instrument panel below the windscreen, its screens facing the way FACING points; BLANK, the plain
 * casing alone, as it stands behind the curved row of screens, which shows what its screens would.
 */
public class InstrumentPanelBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<InstrumentPanelBlock> CODEC = simpleCodec(InstrumentPanelBlock::new);
    public static final BooleanProperty BLANK = BooleanProperty.create("blank");

    public InstrumentPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BLANK, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BLANK);
    }

    /** Put down by hand, its screens face whoever puts it down, as they face the pilots. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
