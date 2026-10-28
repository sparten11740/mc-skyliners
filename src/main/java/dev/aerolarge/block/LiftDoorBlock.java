package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lift's landing door, a pane of smoked glass in a steel frame standing in the shaft's doorway, a block wide and two
 * tall (each block its lower or upper half). Shut, it closes the doorway; open, it has slid aside into the shaft's
 * wall and the doorway is clear. Its lift opens and shuts it (see {@link LiftMachineBlockEntity}); no one else can.
 * FACING is the way out of the shaft. Only the blueprints place it; broken, it gives back nothing.
 */
public class LiftDoorBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<LiftDoorBlock> CODEC = simpleCodec(LiftDoorBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** How thick the shut door is, in pixels, standing across the middle of its block. */
    private static final double THICKNESS = 2;
    private static final Map<Direction, VoxelShape> SHUT = new EnumMap<>(Direction.class);

    static {
        double from = 8 - THICKNESS / 2;
        double to = 8 + THICKNESS / 2;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHUT.put(facing, facing.getAxis() == Direction.Axis.Z ? Block.box(0, 0, from, 16, 16, to) : Block.box(from, 0, 0, to, 16, 16));
        }
    }

    public LiftDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, HALF);
    }

    /** A half of a shut door looking out one way. */
    public BlockState half(Direction facing, DoubleBlockHalf half) {
        return defaultBlockState().setValue(FACING, facing).setValue(HALF, half);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : SHUT.get(state.getValue(FACING));
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
