package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.LinkedHashMap;
import java.util.List;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ULD (unit load device), the aluminium container an airliner's baggage is loaded in: an LD3, two blocks wide, deep
 * and tall, one block of it for each corner. Ribbed aluminium sides in an extruded frame, the contour along one side
 * that fits it to the curve of a hold's floor (the lower half of that side slanting in to the base), a net door over
 * its front with the bags packed behind it, and its id stencilled on the other side: its type, AKE, its serial number,
 * one of a few (NUMBER), and the airline's code.
 * <p>
 * FACING is the way its door faces; HALF, FRONT and CONTOUR say which corner a block is: the lower or upper, at the
 * door or the back, on the contour side or the flat side. Put down, its door faces you and it reaches up, away from you
 * and to your right, the contour on your right; it needs the room for all of it, and comes down whole.
 */
public class UldContainerBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<UldContainerBlock> CODEC = simpleCodec(UldContainerBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty FRONT = BooleanProperty.create("front");
    public static final BooleanProperty CONTOUR = BooleanProperty.create("contour");
    /** Which of the serial numbers its id is stencilled with. */
    public static final IntegerProperty NUMBER = IntegerProperty.create("number", 0, 3);
    /**
     * The lower corner on the contour side, drawn facing north (the contour west): its side slants in from the
     * container's full width to its base, in steps here; the model draws the slant smooth.
     */
    private static final Map<Direction, VoxelShape> CONTOUR_BASE = TurnedShapes.of(Shapes.or(
        Block.box(6, 0, 0, 16, 4, 16),
        Block.box(4, 4, 0, 16, 9, 16),
        Block.box(2, 9, 0, 16, 14, 16),
        Block.box(0, 14, 0, 16, 16, 16)
    ));

    public UldContainerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
            .setValue(FRONT, true).setValue(CONTOUR, false).setValue(NUMBER, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, FRONT, CONTOUR, NUMBER);
    }

    /** The container's corner at the door's foot on the flat side: the one put down, and the one that drops it. */
    public static boolean isOrigin(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(FRONT) && !state.getValue(CONTOUR);
    }

    /** Every corner of a container whose origin corner is {@code origin}, each with where it is. */
    public static Map<BlockPos, BlockState> corners(BlockPos origin, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockState base = state.setValue(HALF, DoubleBlockHalf.LOWER).setValue(FRONT, true).setValue(CONTOUR, false);
        Map<BlockPos, BlockState> corners = new LinkedHashMap<>();
        for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
            for (boolean front : List.of(true, false)) {
                for (boolean contour : List.of(false, true)) {
                    BlockState corner = base.setValue(HALF, half).setValue(FRONT, front).setValue(CONTOUR, contour);
                    corners.put(origin.offset(offset(corner, facing)), corner);
                }
            }
        }
        return corners;
    }

    /** Where a corner is from the origin corner, for a container facing that way. */
    private static BlockPos offset(BlockState corner, Direction facing) {
        BlockPos at = BlockPos.ZERO;
        if (corner.getValue(HALF) == DoubleBlockHalf.UPPER) {
            at = at.above();
        }
        if (!corner.getValue(FRONT)) {
            at = at.relative(facing.getOpposite());
        }
        if (corner.getValue(CONTOUR)) {
            at = at.relative(facing.getCounterClockWise());
        }
        return at;
    }

    /** It faces whoever puts it down, from the corner clicked; it needs all eight blocks free. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(NUMBER, context.getLevel().getRandom().nextInt(NUMBER.getPossibleValues().size()));
        Level level = context.getLevel();
        for (BlockPos pos : corners(context.getClickedPos(), state).keySet()) {
            if (pos.getY() >= level.getMaxBuildHeight() || !level.getBlockState(pos).canBeReplaced(context)) {
                return null;
            }
        }
        return state;
    }

    /** The other seven corners go in round the one put down. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        corners(pos, state).forEach((at, corner) -> {
            if (!at.equals(pos)) {
                level.setBlock(at, corner, Block.UPDATE_ALL);
            }
        });
    }

    /**
     * A corner whose neighbour in the container is gone goes too, so the container is only ever whole: each corner
     * checks the three it touches, above or below, ahead or behind, and across.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        Direction facing = state.getValue(FACING);
        BlockState expected = null;
        if (direction == (state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)) {
            expected = state.setValue(HALF, state.getValue(HALF) == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
        } else if (direction == (state.getValue(FRONT) ? facing.getOpposite() : facing)) {
            expected = state.setValue(FRONT, !state.getValue(FRONT));
        } else if (direction == (state.getValue(CONTOUR) ? facing.getClockWise() : facing.getCounterClockWise())) {
            expected = state.setValue(CONTOUR, !state.getValue(CONTOUR));
        }
        if (expected != null && !neighbour.equals(expected)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Broken in creative mode, the container comes down without dropping itself from its origin corner. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && !isOrigin(state)) {
            BlockPos origin = pos.subtract(offset(state, state.getValue(FACING)));
            if (level.getBlockState(origin).is(this)) {
                level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean slanted = state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(CONTOUR);
        return slanted ? CONTOUR_BASE.get(state.getValue(FACING)) : Shapes.block();
    }

    /** Mobs go round it, slanted corners and all. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
