package dev.skyliners.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of a big oval window, three blocks wide and four tall, each block a piece of it by its column from the left
 * as seen from outside and its row from the top: in a wall or the rocket's skin ({@link OvalWindowBlock}), or lying
 * flat in a floor or a roof ({@link FlatOvalWindowBlock}). On the rocket each piece is the inner half of its block, its
 * glass bulging out of the outer half; set by hand, it fills its whole block, flush with the wall on both sides, its
 * glass bulging out of the wall's face, and is solid over its glass as through its block (see
 * {@code client.OvalWindowModel}). Its glass dims electrically: using any piece of it
 * darkens the whole window's glass a step, from clear through light, medium and dark and back to clear, with a chime
 * that falls as it darkens. Only the glass darkens: its frame and the wall round it, and the light it lets in, stay as
 * they are.
 *
 * <p>A window set by hand fills the opening that the spot is in, wherever in it the spot is, whichever way it fits the
 * opening best: standing in a wall, or lying in a floor or a roof (see {@link OvalWindowBlock#getStateForPlacement}).
 * Every piece goes in at once, or none where there is no room. Such a window looks like the wall it is set into (see
 * {@code client.OvalWindowWall}) and is only ever whole: taking any piece away takes the whole window, which gives back
 * the window itself.
 */
public abstract class OvalWindowPieceBlock extends TransparentBlock {
    /** How many blocks wide and tall one window is. */
    public static final int COLUMNS = 3;
    public static final int ROWS = 4;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, COLUMNS - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, ROWS - 1);
    /** How dark its glass is: 0 clear, up to {@link #DARKEST}. */
    public static final int DARKEST = 3;
    public static final IntegerProperty DIM = IntegerProperty.create("dim", 0, DARKEST);
    /** The chime's pitch when clear, and how far it falls with each step darker. */
    private static final float CLEAR_PITCH = 1.5F;
    private static final float PITCH_STEP = 0.25F;
    /** The piece that gives back the window when one set by hand is taken away: the middle of its bottom row. */
    private static final int KEEPER_COLUMN = COLUMNS / 2;
    private static final int KEEPER_ROW = ROWS - 1;
    /** How many pixels a block is across. */
    private static final int BLOCK = 16;
    /**
     * The glass's dome, as {@code client.OvalWindowModel} bakes it and tools/generate_textures.mjs draws it: the oval's
     * half width and half height, how far the collar's inner edge, where the glass sits, stands out of the wall, and how
     * far the dome bulges out of the collar at its middle, in pixels.
     */
    public static final float OVAL_WIDE = 21;
    public static final float OVAL_TALL = 29;
    public static final float COLLAR = 0.8F;
    public static final float BULGE = 6;
    /** How the dome's height falls toward its foot: as this power of the share of the way still to go to its edge. */
    public static final float PROFILE = 0.6F;
    /** How wide the squares are that the solid steps over a dome are laid out in, in pixels. */
    private static final int DOME_STEP = 2;
    /** The solid steps over each piece's share of its dome, by the way it looks out, its top and its place. */
    private static final Map<Dome, VoxelShape> DOMES = new ConcurrentHashMap<>();

    /** A piece's share of its window's dome: the way it looks out, its top, its column and its row. */
    private record Dome(Direction out, Direction up, int column, int row) {
    }

    protected OvalWindowPieceBlock(Properties properties) {
        super(properties);
    }

    /** The way a piece's window looks out: out of its wall, or up or down out of its floor or roof. */
    public abstract Direction out(BlockState piece);

    /** The way from a row of a piece's window toward its top row, across the way it looks out. */
    public abstract Direction up(BlockState piece);

    /** Whether a piece's window was set by hand, so it is only ever whole and looks like the wall it is set into. */
    public abstract boolean setByHand(BlockState piece);

    /** The way toward a piece's window's first column, its left as seen from outside. */
    public Direction left(BlockState piece) {
        return left(out(piece), up(piece));
    }

    /** The way toward a window's left as seen from outside, from the way it looks out and the way toward its top. */
    private static Direction left(Direction out, Direction up) {
        return Direction.getNearest(out.getStepY() * up.getStepZ() - out.getStepZ() * up.getStepY(),
            out.getStepZ() * up.getStepX() - out.getStepX() * up.getStepZ(), out.getStepX() * up.getStepY() - out.getStepY() * up.getStepX());
    }

    /**
     * How far the glass stands out of the wall at a point of the window, so far right of its middle and below it as seen
     * from outside, in pixels: none past the oval's edge.
     */
    private static double domeHeight(double right, double down) {
        double within = 1 - (right / OVAL_WIDE) * (right / OVAL_WIDE) - (down / OVAL_TALL) * (down / OVAL_TALL);
        return within <= 0 ? 0 : COLLAR + BULGE * Math.pow(within, PROFILE);
    }

    /**
     * A piece of a window set by hand, solid all through its block and over its share of the dome that stands out of
     * it, so nobody pokes their head into the glass: in steps DOME_STEP pixels square, each as high as the glass over
     * it at its highest, to the next whole pixel.
     */
    protected static VoxelShape solidWithDome(OvalWindowPieceBlock window, BlockState piece) {
        return DOMES.computeIfAbsent(new Dome(window.out(piece), window.up(piece), piece.getValue(COLUMN), piece.getValue(ROW)),
            OvalWindowPieceBlock::solidOver);
    }

    /** A piece's block and the steps over its share of the dome. */
    private static VoxelShape solidOver(Dome dome) {
        Direction[] ways = {left(dome.out(), dome.up()).getOpposite(), dome.up().getOpposite(), dome.out()};
        int squares = BLOCK / DOME_STEP;
        List<VoxelShape> steps = new ArrayList<>();
        for (int square = 0; square < squares * squares; square++) {
            int across = square % squares * DOME_STEP;
            int down = square / squares * DOME_STEP;
            double height = Math.ceil(highestOver(dome.column() * BLOCK + across, dome.row() * BLOCK + down));
            if (height > 0) {
                steps.add(box(ways, new double[]{across, down, BLOCK}, new double[]{across + DOME_STEP, down + DOME_STEP, BLOCK + height}));
            }
        }
        return Shapes.or(Shapes.block(), steps.toArray(VoxelShape[]::new));
    }

    /**
     * How far the glass stands out of the wall at its highest over a square of the window DOME_STEP pixels across, from
     * its top left corner so far right of the window's left side and below its top as seen from outside, in pixels: where
     * the square comes nearest the oval's middle.
     */
    private static double highestOver(double fromLeft, double fromTop) {
        double right = nearestZero(fromLeft - COLUMNS * BLOCK / 2.0, fromLeft + DOME_STEP - COLUMNS * BLOCK / 2.0);
        double down = nearestZero(fromTop - ROWS * BLOCK / 2.0, fromTop + DOME_STEP - ROWS * BLOCK / 2.0);
        return domeHeight(right, down);
    }

    /** Of the numbers from one to another, the one nearest nought. */
    private static double nearestZero(double from, double to) {
        return Math.max(from, Math.min(to, 0));
    }

    /**
     * A box in the block from its stretches along three ways square to one another, each in pixels from the side of the
     * block that way faces away from.
     */
    private static VoxelShape box(Direction[] ways, double[] from, double[] to) {
        double[] min = new double[Direction.Axis.values().length];
        double[] max = new double[Direction.Axis.values().length];
        for (int i = 0; i < ways.length; i++) {
            int axis = ways[i].getAxis().ordinal();
            boolean positive = ways[i].getAxisDirection() == Direction.AxisDirection.POSITIVE;
            min[axis] = positive ? from[i] : BLOCK - to[i];
            max[axis] = positive ? to[i] : BLOCK - from[i];
        }
        return Block.box(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    /** Where a piece of the window a piece of this kind is part of is, by its column and row. */
    public BlockPos pieceAt(BlockPos pos, BlockState piece, int column, int row) {
        return pos.relative(left(piece), piece.getValue(COLUMN) - column).relative(up(piece), piece.getValue(ROW) - row);
    }

    /**
     * The piece for the spot clicked that fits a window best into an opening round it, of the ways it can lie, the
     * first given winning where two fit as well; for each, the middle of its bottom row on the spot when none fits
     * better; null where none has room.
     */
    @Nullable
    protected static BlockState bestFit(BlockPlaceContext context, List<BlockState> ways) {
        BlockState best = null;
        int bestFit = -1;
        for (int i = 0; i < ways.size() * COLUMNS * ROWS; i++) {
            int spot = i % (COLUMNS * ROWS);
            BlockState piece = ways.get(i / (COLUMNS * ROWS)).setValue(COLUMN, (COLUMNS / 2 + spot) % COLUMNS).setValue(ROW, ROWS - 1 - spot / COLUMNS);
            int fit = piece.getBlock() instanceof OvalWindowPieceBlock window ? window.fit(context, piece) : -1;
            if (fit > bestFit) {
                best = piece;
                bestFit = fit;
            }
        }
        return best;
    }

    /**
     * How well a window would fit with the piece on the spot clicked: how many of the blocks round its edge in its wall
     * stand there already, or -1 where any of its pieces has no room.
     */
    private int fit(BlockPlaceContext context, BlockState piece) {
        Level level = context.getLevel();
        int walled = 0;
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            BlockPos at = pieceAt(context.getClickedPos(), piece, i % COLUMNS, i / COLUMNS);
            if (level.isOutsideBuildHeight(at) || !level.getWorldBorder().isWithinBounds(at) || !level.getBlockState(at).canBeReplaced(context)
                || !level.isUnobstructed(piece, at, CollisionContext.empty())) {
                return -1;
            }
            walled += walled(level, at, piece.setValue(COLUMN, i % COLUMNS).setValue(ROW, i / COLUMNS));
        }
        return walled;
    }

    /** How many of the blocks beside a piece across its window's edge stand there, as a wall round an opening would. */
    private int walled(Level level, BlockPos pos, BlockState piece) {
        Direction.Axis through = out(piece).getAxis();
        int walled = 0;
        for (Direction way : Direction.values()) {
            if (way.getAxis() != through && beside(piece, way) == null && !level.getBlockState(pos.relative(way)).canBeReplaced()) {
                walled++;
            }
        }
        return walled;
    }

    /** The rest of a window set by hand goes in round the piece set on the spot. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !setByHand(state)) {
            return;
        }
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            BlockPos at = pieceAt(pos, state, i % COLUMNS, i / COLUMNS);
            if (!at.equals(pos)) {
                level.setBlock(at, state.setValue(COLUMN, i % COLUMNS).setValue(ROW, i / COLUMNS), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * The piece a piece's window has beside it one way, as it should be there; null where the window ends that way, or
     * the way runs through it.
     */
    @Nullable
    private BlockState beside(BlockState piece, Direction way) {
        if (way.getAxis() == out(piece).getAxis()) {
            return null;
        }
        Direction left = left(piece);
        Direction up = up(piece);
        int column = piece.getValue(COLUMN) + (way == left ? -1 : way == left.getOpposite() ? 1 : 0);
        int row = piece.getValue(ROW) + (way == up ? -1 : way == up.getOpposite() ? 1 : 0);
        boolean inside = column >= 0 && column < COLUMNS && row >= 0 && row < ROWS;
        return inside ? piece.setValue(COLUMN, column).setValue(ROW, row) : null;
    }

    /** A piece of a window set by hand whose neighbour in it is gone goes too, so such a window is only ever whole. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        BlockState expected = setByHand(state) ? beside(state, direction) : null;
        if (expected != null && (!neighbour.is(this) || neighbour.setValue(DIM, 0) != expected.setValue(DIM, 0))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /**
     * Broken in creative mode, a window set by hand takes the piece that gives the window back with it first, dropping
     * nothing, and the rest follow it.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos keeper = pieceAt(pos, state, KEEPER_COLUMN, KEEPER_ROW);
        if (!level.isClientSide && player.isCreative() && setByHand(state) && !keeper.equals(pos) && level.getBlockState(keeper).is(this)) {
            level.setBlock(keeper, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            dim(level, pos, state);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Dims the whole window the piece is part of a step darker, or clears it from its darkest, with its chime. */
    private void dim(Level level, BlockPos pos, BlockState state) {
        int dim = (state.getValue(DIM) + 1) % (DARKEST + 1);
        for (BlockPos at : JoinedBlocks.from(level, pos, COLUMNS * ROWS, this::sameWindow)) {
            level.setBlock(at, level.getBlockState(at).setValue(DIM, dim), Block.UPDATE_ALL);
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.5F, chimePitch(dim));
    }

    /** The pitch of the chime a window's glass makes as it dims to a darkness, falling as it darkens. */
    public static float chimePitch(int dim) {
        return CLEAR_PITCH - PITCH_STEP * dim;
    }

    /**
     * Whether a piece's neighbour is the next piece of its window: the same kind of window, looking out the same way
     * with its top the same way, a row or a column on.
     */
    private boolean sameWindow(BlockState piece, Direction way, BlockState neighbor) {
        if (!neighbor.is(this) || out(neighbor) != out(piece) || up(neighbor) != up(piece)) {
            return false;
        }
        int columns = Math.abs(neighbor.getValue(COLUMN) - piece.getValue(COLUMN));
        int rows = Math.abs(neighbor.getValue(ROW) - piece.getValue(ROW));
        return way.getAxis() == up(piece).getAxis() ? columns == 0 && rows == 1 : rows == 0 && columns == 1;
    }
}
