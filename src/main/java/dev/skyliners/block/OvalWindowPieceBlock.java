package dev.skyliners.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of a round or oval window, its glass a dome in a steel collar, each block a piece of it by its column from
 * the left as seen from outside and its row from the top: standing in a wall ({@link StandingWindowBlock}), as the
 * rocket's big oval window, three blocks wide and four tall, stands in her skin ({@link OvalWindowBlock}), or lying
 * flat in a floor or a roof ({@link FlatWindowBlock}). How big a window is, and how its glass and steel are measured
 * round its oval, is its {@link Size}; a window a block across has no column or row to keep. On the rocket each piece
 * is the inner half of its block, its glass bulging out of the outer half; set by hand, it fills its whole block, flush
 * with the wall on both sides, its glass bulging out of the wall's face, and is solid over its glass as through its
 * block (see {@code client.OvalWindowModel}). Its glass dims electrically: using any piece of it darkens the whole
 * window's glass a step, from clear through light, medium and dark and back to clear, with a chime that falls as it
 * darkens; a window dimmer (see {@link WindowDimmerBlock}) or the rocket's cockpit console sets every window of a story
 * at once (see {@link #dimWithin}). Only the glass darkens: its frame and the wall round it, and the light it lets in,
 * stay as they are.
 *
 * <p>A window set by hand fills the opening that the spot is in, wherever in it the spot is, whichever way it fits the
 * opening best: standing in a wall, or lying in a floor or a roof (see {@link
 * StandingWindowBlock#getStateForPlacement}). Every piece goes in at once, or none where there is no room. Such a
 * window looks like the wall it is set into (see {@code client.OvalWindowWall}) and is only ever whole: taking any
 * piece away takes the whole window, which gives back the window itself.
 */
public abstract class OvalWindowPieceBlock extends TransparentBlock {
    /**
     * How dark its glass is: 0 clear, up to {@link #DARKEST}; any glass carrying it is set by a window dimmer, the
     * freighter's canopy too (see {@link DimmingGlassBlock}).
     */
    public static final int DARKEST = 3;
    public static final IntegerProperty DIM = IntegerProperty.create("dim", 0, DARKEST);
    /** The chime's pitch when clear, and how far it falls with each step darker; and how loud it is. */
    private static final float CLEAR_PITCH = 1.5F;
    private static final float PITCH_STEP = 0.25F;
    private static final float CHIME_VOLUME = 0.5F;
    /** How many pixels a block is across. */
    private static final int BLOCK = 16;
    /**
     * How the dome's height falls toward its foot, in every window: as this power of the share of the way still to go
     * to its edge, steep at the foot for below 1, as a bubble's.
     */
    public static final float PROFILE = 0.6F;
    /** How wide the squares are that the solid steps over a dome are laid out in, in pixels. */
    private static final int DOME_STEP = 2;
    /** The solid steps over each piece's share of its dome, by its block state. */
    private static final Map<BlockState, VoxelShape> DOMES = new ConcurrentHashMap<>();

    /**
     * How big a window is and how its glass and steel are measured round its oval, as its blocks, its model ({@code
     * client.OvalWindowModel}) and tools/generate_textures.mjs, which draws its glass, all measure them: how many
     * blocks wide and tall the whole window is; the oval's half width and half height; how far the collar's inner edge,
     * where the glass sits, stands out of the wall, and how far the dome bulges out of the collar at its middle; how
     * far out from the oval's edge the collar reaches; and how far out from it the recess's lining runs and how wide
     * the lip round the hole is; all in pixels. Its name is how a window's model names it.
     */
    public record Size(String name, int columns, int rows, float ovalWide, float ovalTall, float collar, float bulge, float rim,
                       float hole, float lip) {
        /** The rocket's big oval window, three blocks wide and four tall. */
        public static final Size ROCKET = new Size("rocket", 3, 4, 21, 29, 0.8F, 6, 2.6F, 1.2F, 1.7F);
        /** Every size a window's model can name. */
        private static final List<Size> ALL = List.of(ROCKET);

        /**
         * The size a window's model names, so a model naming one that does not exist fails to load rather than drawing
         * a window of the wrong size.
         */
        public static Size named(String name) {
            return ALL.stream().filter(size -> size.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No oval window is sized " + name));
        }

        /** How many blocks the whole window has. */
        public int pieces() {
            return columns * rows;
        }

        /** The piece that gives back a window set by hand when it is taken away: the middle of its bottom row. */
        int keeperColumn() {
            return columns / 2;
        }

        int keeperRow() {
            return rows - 1;
        }

        /**
         * How far the glass stands out of the wall at a point of the window, so far right of its middle and below it as
         * seen from outside, in pixels: none past the oval's edge.
         */
        double domeHeight(double right, double down) {
            double within = 1 - (right / ovalWide) * (right / ovalWide) - (down / ovalTall) * (down / ovalTall);
            return within <= 0 ? 0 : collar + bulge * Math.pow(within, PROFILE);
        }

        /**
         * How far the glass stands out of the wall at its highest over a square of the window DOME_STEP pixels across,
         * from its top left corner so far right of the window's left side and below its top as seen from outside, in
         * pixels: where the square comes nearest the oval's middle.
         */
        double highestOver(double fromLeft, double fromTop) {
            double right = nearestZero(fromLeft - columns * BLOCK / 2.0, fromLeft + DOME_STEP - columns * BLOCK / 2.0);
            double down = nearestZero(fromTop - rows * BLOCK / 2.0, fromTop + DOME_STEP - rows * BLOCK / 2.0);
            return domeHeight(right, down);
        }
    }

    private final Size size;

    protected OvalWindowPieceBlock(Properties properties, Size size) {
        super(properties);
        this.size = size;
    }

    /** How big a window of this kind is. */
    public Size size() {
        return size;
    }

    /**
     * The property a piece's column from the left is kept in, ranged by the window's size, static to its kind of
     * window as its block state is made before the block knows its size; null for a window a block wide.
     */
    @Nullable
    protected abstract IntegerProperty columnProperty();

    /** The property a piece's row from the top is kept in, as its column is; null for a window a block tall. */
    @Nullable
    protected abstract IntegerProperty rowProperty();

    /** A piece's column from the left as seen from outside. */
    public int column(BlockState piece) {
        IntegerProperty column = columnProperty();
        return column == null ? 0 : piece.getValue(column);
    }

    /** A piece's row from the top. */
    public int row(BlockState piece) {
        IntegerProperty row = rowProperty();
        return row == null ? 0 : piece.getValue(row);
    }

    /** The piece of the same window at a column and a row, looking out the same way. */
    public BlockState at(BlockState piece, int column, int row) {
        IntegerProperty columns = columnProperty();
        IntegerProperty rows = rowProperty();
        BlockState placed = columns == null ? piece : piece.setValue(columns, column);
        return rows == null ? placed : placed.setValue(rows, row);
    }

    /** A piece of a window mirrored: its columns from the left run the other way round. */
    protected BlockState mirrored(BlockState piece) {
        return at(piece, size.columns() - 1 - column(piece), row(piece));
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
     * A piece of a window set by hand, solid all through its block and over its share of the dome that stands out of
     * it, so nobody pokes their head into the glass: in steps DOME_STEP pixels square, each as high as the glass over
     * it at its highest, to the next whole pixel.
     */
    protected VoxelShape solidWithDome(BlockState piece) {
        return DOMES.computeIfAbsent(piece, state -> solidOver(out(state), up(state), column(state), row(state)));
    }

    /**
     * A piece's block and the steps over its share of the dome, by the way it looks out, the way toward its window's
     * top, and its column and row.
     */
    private VoxelShape solidOver(Direction out, Direction up, int column, int row) {
        Direction[] ways = {left(out, up).getOpposite(), up.getOpposite(), out};
        int squares = BLOCK / DOME_STEP;
        List<VoxelShape> steps = new ArrayList<>();
        for (int square = 0; square < squares * squares; square++) {
            int across = square % squares * DOME_STEP;
            int down = square / squares * DOME_STEP;
            double height = Math.ceil(size.highestOver(column * BLOCK + across, row * BLOCK + down));
            if (height > 0) {
                steps.add(box(ways, new double[]{across, down, BLOCK}, new double[]{across + DOME_STEP, down + DOME_STEP, BLOCK + height}));
            }
        }
        return Shapes.or(Shapes.block(), steps.toArray(VoxelShape[]::new));
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
        return pos.relative(left(piece), column(piece) - column).relative(up(piece), row(piece) - row);
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
        for (BlockState piece : ways.stream().flatMap(OvalWindowPieceBlock::spots).toList()) {
            int fit = ((OvalWindowPieceBlock) piece.getBlock()).fit(context, piece);
            if (fit > bestFit) {
                best = piece;
                bestFit = fit;
            }
        }
        return best;
    }

    /**
     * The pieces of a window lying one way that the spot clicked could be, in the order they are tried: the middle of
     * its bottom row first, then on along that row and up through the rows above it.
     */
    private static Stream<BlockState> spots(BlockState way) {
        OvalWindowPieceBlock window = (OvalWindowPieceBlock) way.getBlock();
        int columns = window.size.columns();
        return IntStream.range(0, window.size.pieces())
            .mapToObj(spot -> window.at(way, (columns / 2 + spot) % columns, window.size.rows() - 1 - spot / columns));
    }

    /**
     * How well a window would fit with the piece on the spot clicked: how many of the blocks round its edge in its wall
     * stand there already, or -1 where any of its pieces has no room, or has someone standing in its block. Whoever
     * stands where only its dome will reach is pushed aside by it, as it stands out of the wall toward them.
     */
    private int fit(BlockPlaceContext context, BlockState piece) {
        Level level = context.getLevel();
        int walled = 0;
        for (int i = 0; i < size.pieces(); i++) {
            BlockPos at = pieceAt(context.getClickedPos(), piece, i % size.columns(), i / size.columns());
            if (level.isOutsideBuildHeight(at) || !level.getWorldBorder().isWithinBounds(at) || !level.getBlockState(at).canBeReplaced(context)
                || !level.isUnobstructed(null, Shapes.block().move(at.getX(), at.getY(), at.getZ()))) {
                return -1;
            }
            walled += walled(level, at, at(piece, i % size.columns(), i / size.columns()));
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
        for (int i = 0; i < size.pieces(); i++) {
            BlockPos at = pieceAt(pos, state, i % size.columns(), i / size.columns());
            if (!at.equals(pos)) {
                level.setBlock(at, at(state, i % size.columns(), i / size.columns()), Block.UPDATE_ALL);
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
        int column = column(piece) + (way == left ? -1 : way == left.getOpposite() ? 1 : 0);
        int row = row(piece) + (way == up ? -1 : way == up.getOpposite() ? 1 : 0);
        boolean inside = column >= 0 && column < size.columns() && row >= 0 && row < size.rows();
        return inside ? at(piece, column, row) : null;
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
        BlockPos keeper = pieceAt(pos, state, size.keeperColumn(), size.keeperRow());
        if (!level.isClientSide && player.isCreative() && setByHand(state) && !keeper.equals(pos) && level.getBlockState(keeper).is(this)) {
            level.setBlock(keeper, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Using a piece dims its whole window. The game sends whoever used it that piece's new look at once, but the rest
     * of the window's only at the end of the server's tick, so under lag the piece pressed would darken alone for a
     * moment, its share of the dome a dark patch in the glass: they are sent the whole window at once with it.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        List<BlockPos> window = dim(level, pos, state);
        if (player instanceof ServerPlayer user) {
            window.forEach(at -> user.connection.send(new ClientboundBlockUpdatePacket(level, at)));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Dims the whole window the piece is part of a step darker, or clears it from its darkest, with its chime; gives
     * back where its pieces are.
     */
    private List<BlockPos> dim(Level level, BlockPos pos, BlockState state) {
        int dim = (state.getValue(DIM) + 1) % (DARKEST + 1);
        List<BlockPos> window = JoinedBlocks.from(level, pos, size.pieces(), this::sameWindow);
        for (BlockPos at : window) {
            level.setBlock(at, level.getBlockState(at).setValue(DIM, dim), Block.UPDATE_ALL);
        }
        chime(level, pos, dim);
        return window;
    }

    /**
     * Sets every piece of oval window in a box to a darkness, on the server, as a story's window dimmer and the
     * rocket's cockpit console set a story's windows: only where the box is loaded, and only the pieces not that dark
     * already.
     */
    public static void dimWithin(Level level, BoundingBox box, int dim) {
        for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            dimPiece(level, at.immutable(), dim);
        }
    }

    private static void dimPiece(Level level, BlockPos at, int dim) {
        if (!level.isLoaded(at)) {
            return;
        }
        BlockState state = level.getBlockState(at);
        if (state.hasProperty(DIM) && state.getValue(DIM) != dim) {
            level.setBlock(at, state.setValue(DIM, dim), Block.UPDATE_ALL);
        }
    }

    /**
     * The chime a window's glass makes as it dims to a darkness, heard by everyone near a place: wherever it is dimmed
     * from, using a window or touching a panel's oval window.
     */
    public static void chime(Level level, BlockPos pos, int dim) {
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, CHIME_VOLUME, chimePitch(dim));
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
        int columns = Math.abs(column(neighbor) - column(piece));
        int rows = Math.abs(row(neighbor) - row(piece));
        return way.getAxis() == up(piece).getAxis() ? columns == 0 && rows == 1 : rows == 0 && columns == 1;
    }
}
