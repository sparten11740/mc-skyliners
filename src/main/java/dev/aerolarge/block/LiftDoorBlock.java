package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * block it is. Broken, its lower half gives it back. One put up by hand belongs to no lift, so it stays shut.
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

    /** Put down by hand, it looks out toward whoever puts it there, and needs the room above for its upper half. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return half(context.getHorizontalDirection().getOpposite(), DoubleBlockHalf.LOWER);
    }

    /** The upper half goes up over the lower half put down. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** A half whose other half is gone goes too, so a door is only ever whole. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardOtherHalf = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardOtherHalf && !(neighbour.is(this) && neighbour.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Broken by its upper half in creative mode, the lower half it takes with it drops nothing either. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER && level.getBlockState(pos.below()).is(this)) {
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
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
