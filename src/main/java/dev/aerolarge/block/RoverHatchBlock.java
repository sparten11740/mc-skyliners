package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.rover.RoverRoute;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The door of the rocket's rover bay, in her skin where her cargo story's back window would be: one panel over a
 * doorway three blocks wide and four tall, hinged along its sill, which folds down outward into a platform level with
 * the bay's floor. Each block of the doorway is a part of it, by its COLUMN (left to right as seen from outside, so it
 * is the same way round however she is turned) and ROW (from the sill up); the part in the middle of the sill keeps the
 * bay's block entity (see {@link RoverBayBlockEntity}), which works the door, the boom over it and the winch, and is
 * drawn by the client with all of them. FACING is the way out of her.
 *
 * <p>OPEN is whether the doorway is clear, from the moment the door starts to open until it is shut again: nothing then
 * stands in it but its sill, the outer half of each of its lowest row's blocks, out from the hinge, as thick as the
 * door and level with the deck of the platform it folds down into, the rover rolling out through it carried along by
 * its bay; once the door lies flat, the bay lays the rest of the platform out of her for anyone who walks out after it
 * (see {@link RoverPlatformBlock}). Shut, each part is a quarter of its block just inside the skin's cut through the
 * block's middle, so the door sits flush with the skin beside it. Only the blueprint places it, so it has no item and
 * drops nothing.
 */
public class RoverHatchBlock extends BaseEntityBlock {
    private static final MapCodec<RoverHatchBlock> CODEC = simpleCodec(RoverHatchBlock::new);
    /** How many blocks wide and tall the doorway is: an oval window's size, whose place it takes. */
    public static final int COLUMNS = 3;
    public static final int ROWS = 4;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, COLUMNS - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, ROWS - 1);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /**
     * The shut door's parts, by the way out: a quarter of a block thick, inside the skin's cut through the block's
     * middle, as thick as the door is drawn (see the client's {@code RoverBayRenderer}).
     */
    private static final Map<Direction, VoxelShape> SHUT = Map.of(
        Direction.NORTH, Block.box(0, 0, 8, 16, 16, 12),
        Direction.SOUTH, Block.box(0, 0, 4, 16, 16, 8),
        Direction.EAST, Block.box(4, 0, 0, 8, 16, 16),
        Direction.WEST, Block.box(8, 0, 0, 12, 16, 16)
    );
    /** How thick the door is, in sixteenths: how high its sill and its platform's deck stand over the floor, open. */
    static final double DECK = RoverRoute.DECK * 16;
    /** The open doorway's sill, the outer half of a block of its lowest row, by the way out. */
    private static final Map<Direction, VoxelShape> SILL = Map.of(
        Direction.NORTH, Block.box(0, 0, 0, 16, DECK, 8),
        Direction.SOUTH, Block.box(0, 0, 8, 16, DECK, 16),
        Direction.EAST, Block.box(8, 0, 0, 16, DECK, 16),
        Direction.WEST, Block.box(0, 0, 0, 8, DECK, 16)
    );

    public RoverHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 0).setValue(ROW, 0)
            .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, ROW, OPEN);
    }

    /** A part of a door looking out one way, by its column from the left as seen from outside and its row up. */
    public BlockState part(Direction facing, int column, int row) {
        return defaultBlockState().setValue(FACING, facing).setValue(COLUMN, column).setValue(ROW, row);
    }

    /** Whether a part is the one that keeps the bay's block entity: the middle of the sill. */
    public static boolean isController(BlockState state) {
        return state.getValue(COLUMN) == COLUMNS / 2 && state.getValue(ROW) == 0;
    }

    /** Where a part of the door is, by its column and row, from any part of it. */
    public static BlockPos part(BlockState state, BlockPos at, int column, int row) {
        Direction left = state.getValue(FACING).getClockWise();
        return at.relative(left, state.getValue(COLUMN) - column).above(row - state.getValue(ROW));
    }

    /** Opened or shut, the doorway clear from the moment it starts to open until it is shut again. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : SHUT.get(state.getValue(FACING));
    }

    /** Shut, the door; open, only the sill in its lowest row, which whoever walks out steps up onto. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(OPEN)) {
            return SHUT.get(state.getValue(FACING));
        }
        return state.getValue(ROW) == 0 ? SILL.get(state.getValue(FACING)) : Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirrored, the door faces the mirrored way and its left and right change places. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(COLUMN, COLUMNS - 1 - state.getValue(COLUMN));
    }

    /** Only the middle of the sill keeps the bay's block entity. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new RoverBayBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AerolargeRegistry.ROVER_BAY_ENTITY.get()) {
            return null;
        }
        return level.isClientSide
            ? (tickLevel, pos, tickState, bay) -> ((RoverBayBlockEntity) bay).clientTick()
            : (tickLevel, pos, tickState, bay) -> ((RoverBayBlockEntity) bay).serverTick((ServerLevel) tickLevel);
    }
}
