package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block of a rocket's front doorway, where her sliding airlock doors close (see {@link AirlockDoors}): shut, a thick
 * plate across the middle of the block, so nobody walks through the shut doors and anything aimed at them is aimed at
 * the leaves; open (OPEN), nothing at all, the doorway clear. FACING is the way out. Its leaves are drawn by her stairs
 * button's renderer, which keeps them, so the block itself is drawn by nothing. Only her blueprint places it, and only
 * the doors open and shut it; it is her hull, and breaks only as her hull does.
 */
public class AirlockDoorBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<AirlockDoorBlock> CODEC = simpleCodec(AirlockDoorBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** How thick the shut leaves are, in sixteenths, across the middle of the block. */
    public static final int THICKNESS = 4;
    /** Facing north: the plate across the block's middle. */
    private static final Map<Direction, VoxelShape> SHUT = TurnedShapes.of(box(0, 0, 8 - THICKNESS / 2.0, 16, 16, 8 + THICKNESS / 2.0));

    public AirlockDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    /** A block of the shut doorway, looking out the way given. */
    public BlockState shut(Direction out) {
        return defaultBlockState().setValue(FACING, out);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : SHUT.get(state.getValue(FACING));
    }

    /** Open, the doorway is open floor to anything finding its way; shut, a wall. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return state.getValue(OPEN) && type == PathComputationType.LAND;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
