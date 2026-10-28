package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * FACING is the way out of the shaft. While it slides open or shut (SLIDING), its block is drawn empty and its block
 * entity's renderer draws the pane on its way (see {@link LiftDoorBlockEntity}); standing still, it is drawn as the
 * block it is. Only the blueprints place it; broken, it gives back nothing.
 */
public class LiftDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<LiftDoorBlock> CODEC = simpleCodec(LiftDoorBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty SLIDING = BooleanProperty.create("sliding");
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
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(HALF, DoubleBlockHalf.LOWER)
            .setValue(SLIDING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, HALF, SLIDING);
    }

    /** A half of a shut door looking out one way. */
    public BlockState half(Direction facing, DoubleBlockHalf half) {
        return defaultBlockState().setValue(FACING, facing).setValue(HALF, half);
    }

    /** The way a door looking out one way slides as it opens, into the shaft's wall beside the doorway. */
    public static Direction slidesToward(Direction facing) {
        return facing.getClockWise();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LiftDoorBlockEntity(pos, state);
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
