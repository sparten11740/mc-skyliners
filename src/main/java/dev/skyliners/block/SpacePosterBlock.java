package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A space poster, a few blocks wide and tall, each block a piece of it: its column from the left and its row from the
 * top as seen from in front, and which picture it is (see {@link SpacePoster}). It is a sheet of board a pixel thick
 * against the back of its block, the side away from the way it faces, so it hangs flat on whatever stands behind it,
 * and nothing collides with it, so it is walked past as a picture is. The rocket hangs them on her glass lift shaft
 * (see {@code RocketPosters}): blocks, not paintings, as a painting is an entity, which would be left behind when she
 * is assembled; and a poster rolled up is put up by hand (see {@code SpacePosterItem}). Taking any piece away takes
 * the whole poster down, with a painting's tearing sound, and it is given back rolled up from its top left piece. The
 * fleet's paintings are more pictures on this same block (see {@link SpacePoster}).
 */
public class SpacePosterBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<SpacePosterBlock> CODEC = simpleCodec(SpacePosterBlock::new);
    public static final EnumProperty<SpacePoster> POSTER = EnumProperty.create("poster", SpacePoster.class);
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, SpacePoster.mostColumns() - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, SpacePoster.mostRows() - 1);
    /**
     * How far the poster's front stands out from the back of its block, in pixels: its board a pixel thick and a
     * quarter pixel clear of what it hangs on, so the two are not drawn in one plane.
     */
    private static final double DEPTH = 1.25;
    /** Its shapes, by the way it faces. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    /** A poster's sounds: a painting's as it is put up and torn down, and soft underfoot. */
    public static final SoundType PAPER = new SoundType(1.0F, 1.0F, SoundEvents.PAINTING_BREAK, SoundEvents.WOOL_STEP,
        SoundEvents.PAINTING_PLACE, SoundEvents.WOOL_HIT, SoundEvents.WOOL_FALL);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES.put(facing, WideScreenBlock.againstBack(facing, 0, 16, DEPTH));
        }
    }

    public SpacePosterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POSTER, SpacePoster.BLACK_HOLE)
            .setValue(COLUMN, 0).setValue(ROW, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POSTER, COLUMN, ROW);
    }

    /** A piece of a poster facing one way, by its column from the left seen from in front and its row from the top. */
    public BlockState piece(SpacePoster poster, Direction facing, int column, int row) {
        return defaultBlockState().setValue(FACING, facing).setValue(POSTER, poster).setValue(COLUMN, column).setValue(ROW, row);
    }

    /**
     * Where each block of a poster goes, put up facing one way round its middle block, and the piece of it each is,
     * from its top left: on an even width the middle block is the right one of the two middle columns, and on an even
     * height the lower one of the two middle rows.
     */
    public Map<BlockPos, BlockState> pieces(SpacePoster poster, BlockPos middle, Direction facing) {
        Direction right = right(facing);
        Map<BlockPos, BlockState> pieces = new LinkedHashMap<>();
        for (int piece = 0; piece < poster.columns() * poster.rows(); piece++) {
            int column = piece % poster.columns();
            int row = piece / poster.columns();
            BlockPos at = middle.relative(right, column - poster.columns() / 2).above(poster.rows() / 2 - row);
            pieces.put(at, piece(poster, facing, column, row));
        }
        return pieces;
    }

    /** The viewer's right, looking at a poster facing this way: the way its columns run. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Picked, the poster rolled up, as one taken down gives back. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(switch (state.getValue(POSTER)) {
            case BLACK_HOLE -> SkylinersRegistry.BLACK_HOLE_POSTER.get();
            case SOLAR_SYSTEM -> SkylinersRegistry.SOLAR_SYSTEM_POSTER.get();
            case QUASAR -> SkylinersRegistry.QUASAR_POSTER.get();
            case EARTHRISE -> SkylinersRegistry.EARTHRISE_POSTER.get();
            case AIRLINER -> SkylinersRegistry.AIRLINER_PAINTING.get();
            case ZEPPELIN -> SkylinersRegistry.ZEPPELIN_PAINTING.get();
            case ROCKET -> SkylinersRegistry.ROCKET_PAINTING.get();
            case FREIGHTER -> SkylinersRegistry.FREIGHTER_PAINTING.get();
        });
    }

    /** A piece whose neighbour in the poster is gone goes too, so the poster is only ever whole. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (carriesOn(state, direction) && !joins(state, direction, neighbour)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /**
     * Broken in creative mode, it takes its top left piece first, the one that gives the poster back, so that nothing
     * drops as the rest goes with it.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos topLeft = topLeft(state, pos);
        if (!level.isClientSide && player.isCreative() && !topLeft.equals(pos) && isTopLeft(state, level.getBlockState(topLeft))) {
            level.setBlock(topLeft, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Where a poster's top left piece is, from any piece of it. */
    private static BlockPos topLeft(BlockState piece, BlockPos pos) {
        return pos.relative(right(piece.getValue(FACING)), -piece.getValue(COLUMN)).above(piece.getValue(ROW));
    }

    /** Whether a block is the top left piece of a poster like a piece's: the same picture, facing the same way. */
    private static boolean isTopLeft(BlockState piece, BlockState other) {
        return other.getBlock() instanceof SpacePosterBlock && other.getValue(FACING) == piece.getValue(FACING)
            && other.getValue(POSTER) == piece.getValue(POSTER) && other.getValue(COLUMN) == 0 && other.getValue(ROW) == 0;
    }

    /** Whether a piece's poster carries on past it the given way: a column or a row more of it there. */
    private static boolean carriesOn(BlockState piece, Direction way) {
        int[] next = next(piece, way);
        SpacePoster poster = piece.getValue(POSTER);
        return next != null && next[0] >= 0 && next[0] < poster.columns() && next[1] >= 0 && next[1] < poster.rows();
    }

    /** Whether a piece's neighbour the given way is the next piece of its poster. */
    private static boolean joins(BlockState piece, Direction way, BlockState neighbour) {
        if (!(neighbour.getBlock() instanceof SpacePosterBlock) || neighbour.getValue(FACING) != piece.getValue(FACING)
            || neighbour.getValue(POSTER) != piece.getValue(POSTER)) {
            return false;
        }
        int[] next = next(piece, way);
        return next != null && neighbour.getValue(COLUMN) == next[0] && neighbour.getValue(ROW) == next[1];
    }

    /**
     * The column and row of the piece next to a piece the given way, whether or not its poster reaches that far; null
     * for a way out of its face or into it.
     */
    @Nullable
    private static int[] next(BlockState piece, Direction way) {
        int column = piece.getValue(COLUMN);
        int row = piece.getValue(ROW);
        Direction right = right(piece.getValue(FACING));
        if (way == Direction.UP || way == Direction.DOWN) {
            return new int[]{column, way == Direction.UP ? row - 1 : row + 1};
        }
        if (way == right || way == right.getOpposite()) {
            return new int[]{way == right ? column + 1 : column - 1, row};
        }
        return null;
    }
}
