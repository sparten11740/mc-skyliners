package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A block of flight deck with a white line painted along its top: the flying carrier's edge and middle lines, and the
 * freighter's cargo hold's loading lanes. AXIS is the way its line runs, along her length as she is laid out, and turns
 * with her when she is built turned, so her lines still run along her; one laid before it had a way runs north and
 * south, as all did then. Put down by hand, its line runs the way its putter faces.
 */
public class DeckLineBlock extends Block {
    private static final MapCodec<DeckLineBlock> CODEC = simpleCodec(DeckLineBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public DeckLineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    /** Turned a quarter, its line runs the other way. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        boolean quarter = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        Direction.Axis axis = state.getValue(AXIS);
        return quarter ? state.setValue(AXIS, axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : state;
    }
}
