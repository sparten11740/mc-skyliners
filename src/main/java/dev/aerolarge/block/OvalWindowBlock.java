package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A big oval window in the rocket's skin, a few blocks wide and tall, each block a piece of it (its column from the
 * left as seen from outside, its row from the top), its glass bulging a little out of the skin (see
 * {@code client.OvalWindowModel}). Its glass dims electrically: using any piece of it darkens the whole window's glass
 * a step, from clear through light, medium and dark and back to clear, with a chime that falls as it darkens. Only the
 * glass darkens: its frame and the skin round it, and the light it lets in, stay as they are. Each piece is the inner
 * half of its block, where the skin's cut runs through the block's middle out to the four ways round a round body, so
 * it sits flush with the skin beside it. It turns with the ship as she is built facing any way. Only the blueprints
 * place it; broken, it gives back glass.
 */
public class OvalWindowBlock extends TransparentBlock {
    private static final MapCodec<OvalWindowBlock> CODEC = simpleCodec(OvalWindowBlock::new);
    /** How many blocks wide and tall one window is. */
    public static final int COLUMNS = 3;
    public static final int ROWS = 4;
    /** The way the window looks out. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, COLUMNS - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, ROWS - 1);
    /** How dark its glass is: 0 clear, up to {@link #DARKEST}. */
    public static final int DARKEST = 3;
    public static final IntegerProperty DIM = IntegerProperty.create("dim", 0, DARKEST);
    /** The chime's pitch when clear, and how far it falls with each step darker. */
    private static final float CLEAR_PITCH = 1.5F;
    private static final float PITCH_STEP = 0.25F;
    /** The pieces' shapes, the inner half of the block, by the way they look out. */
    private static final VoxelShape LOOKING_NORTH = Block.box(0, 0, 8, 16, 16, 16);
    private static final VoxelShape LOOKING_SOUTH = Block.box(0, 0, 0, 16, 16, 8);
    private static final VoxelShape LOOKING_EAST = Block.box(0, 0, 0, 8, 16, 16);
    private static final VoxelShape LOOKING_WEST = Block.box(8, 0, 0, 16, 16, 16);

    public OvalWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 0).setValue(ROW, 0)
            .setValue(DIM, 0));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, ROW, DIM);
    }

    /** A piece of a window looking out one way, by its column from the left as seen from outside and its row. */
    public BlockState piece(Direction facing, int column, int row) {
        return defaultBlockState().setValue(FACING, facing).setValue(COLUMN, column).setValue(ROW, row);
    }

    /** A window turns with the ship it is set in, as she is built facing another way. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirrored, a window looks out the mirrored way, and its columns from the left run the other way round. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(COLUMN, COLUMNS - 1 - state.getValue(COLUMN));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> LOOKING_SOUTH;
            case EAST -> LOOKING_EAST;
            case WEST -> LOOKING_WEST;
            default -> LOOKING_NORTH;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            dim(level, pos, state);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Dims the whole window the piece is part of a step darker, or clears it from its darkest, with its chime. */
    private static void dim(Level level, BlockPos pos, BlockState state) {
        int dim = (state.getValue(DIM) + 1) % (DARKEST + 1);
        for (BlockPos at : JoinedBlocks.from(level, pos, COLUMNS * ROWS, OvalWindowBlock::sameWindow)) {
            level.setBlock(at, level.getBlockState(at).setValue(DIM, dim), Block.UPDATE_ALL);
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.5F, CLEAR_PITCH - PITCH_STEP * dim);
    }

    /** Whether a piece's neighbour is the next piece of its window: looking out the same way, a row or column on. */
    private static boolean sameWindow(BlockState piece, Direction way, BlockState neighbor) {
        if (!(neighbor.getBlock() instanceof OvalWindowBlock) || neighbor.getValue(FACING) != piece.getValue(FACING)) {
            return false;
        }
        int columns = Math.abs(neighbor.getValue(COLUMN) - piece.getValue(COLUMN));
        int rows = Math.abs(neighbor.getValue(ROW) - piece.getValue(ROW));
        return way.getAxis().isVertical() ? columns == 0 && rows == 1 : rows == 0 && columns == 1;
    }
}
