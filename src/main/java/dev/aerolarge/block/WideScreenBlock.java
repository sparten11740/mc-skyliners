package dev.aerolarge.block;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wide screen, {@link #COLUMNS} blocks wide and {@link #ROWS} tall unless its block makes it bigger (see
 * {@link #columnsOf}), its renderer drawing all of it: the tail camera display on the flight deck, the larger one in a
 * zeppelin's control car, and the control tower's flight strip board. Every block of it is this block; PART says which,
 * counting along the bottom row from the viewer's left and then up, and part 0 holds the block entity the whole screen
 * is drawn from. FACING is the way the screen faces. Upright, it is a slim screen with its back to whatever it is fixed
 * to. TILTED, it hangs from a ceiling like an overhead monitor: the screen runs across each block's diagonal, from the
 * bottom edge of its back up to the top edge of the side it faces, along the ceiling, so it looks down toward FACING at
 * 45 degrees and whoever is out that way and below looks straight up at it; behind it the casing fills the block out to
 * its top and back; a screen more than a row tall only ever stands upright. Placed by hand, a screen is its block's own
 * size: against a wall it stands upright, put up against the underside of a block it hangs tilted, and anywhere else it
 * stands upright facing whoever puts it there; it needs the room for all of it and puts every part up at once. Taking
 * any part away takes the whole screen, which drops one screen from part 0.
 */
public abstract class WideScreenBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final int COLUMNS = 3;
    public static final int ROWS = 1;
    /**
     * The most parts any wide screen has: the large camera display's, four by two (see {@link TailCameraDisplayBlock}).
     */
    private static final int MOST_PARTS = 8;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, MOST_PARTS - 1);
    public static final BooleanProperty TILTED = BooleanProperty.create("tilted");
    /** How thick the upright screen is, in pixels of its block, standing against the back of it. */
    public static final int DEPTH = 3;
    /** How tall the face its renderer draws on is for each row, in blocks: the row upright, or tilted, its diagonal. */
    public static final double UPRIGHT_HEIGHT = 1;
    public static final double TILTED_HEIGHT = Math.sqrt(2);
    /** How many steps the tilted screen's filled half is made of for collision and selection. */
    private static final int TILTED_STEPS = 4;
    private static final Map<Direction, VoxelShape> UPRIGHT_SHAPES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> TILTED_SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            UPRIGHT_SHAPES.put(facing, againstBack(facing, 0, 16, DEPTH));
            VoxelShape tilted = Shapes.empty();
            int step = 16 / TILTED_STEPS;
            for (int k = 0; k < TILTED_STEPS; k++) {
                // Each step reaches out from the back as far as the diagonal does at its top.
                tilted = Shapes.or(tilted, againstBack(facing, k * step, (k + 1) * step, (k + 1) * step));
            }
            TILTED_SHAPES.put(facing, tilted);
        }
    }

    protected WideScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(PART, 0).setValue(TILTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, TILTED);
    }

    /**
     * A box against the back of the block, the side away from the way the screen faces: from y0 to y1 up, and reaching
     * this deep from the back, in pixels.
     */
    static VoxelShape againstBack(Direction facing, double y0, double y1, double depth) {
        return switch (facing) {
            case NORTH -> Block.box(0, y0, 16 - depth, 16, y1, 16);
            case SOUTH -> Block.box(0, y0, 0, 16, y1, depth);
            case WEST -> Block.box(16 - depth, y0, 0, 16, y1, 16);
            default -> Block.box(0, y0, 0, depth, y1, 16);
        };
    }

    /** The viewer's right, looking at the screen. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** How many blocks wide a screen in this state is, of this block: {@link #COLUMNS}, unless the block says more. */
    protected int columnsOf(BlockState state) {
        return COLUMNS;
    }

    /** How many blocks tall a screen in this state is, of this block: {@link #ROWS}, unless the block says more. */
    protected int rowsOf(BlockState state) {
        return ROWS;
    }

    /** How many blocks wide a wide screen in this state is. */
    public static int columns(BlockState state) {
        return ((WideScreenBlock) state.getBlock()).columnsOf(state);
    }

    /** How many blocks tall a wide screen in this state is. */
    public static int rows(BlockState state) {
        return ((WideScreenBlock) state.getBlock()).rowsOf(state);
    }

    /** How tall a row of the face of a screen in this state is, in blocks: the row upright, or tilted, its diagonal. */
    public static double rowHeight(BlockState state) {
        return state.getValue(TILTED) ? TILTED_HEIGHT : UPRIGHT_HEIGHT;
    }

    private static int parts(BlockState state) {
        return columns(state) * rows(state);
    }

    /** Where a part of a screen so many blocks wide is, from the part holding it. */
    private static BlockPos part(BlockPos origin, Direction facing, int part, int columns) {
        return origin.relative(right(facing), part % columns).above(part / columns);
    }

    /** Where the part holding the screen is, from any part. */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        int part = state.getValue(PART);
        int columns = columns(state);
        return pos.relative(right(state.getValue(FACING)), -(part % columns)).below(part / columns);
    }

    /** Where on a screen's face it was touched, in blocks from the face's bottom left corner as the viewer sees it. */
    public record Touch(double across, double up) {
    }

    /**
     * Whether a hit on this side of a part of the screen is on its face: upright, the front of the slim screen; tilted,
     * the diagonal, met from in front on its steps' fronts and from below on their undersides.
     */
    public static boolean onFace(BlockState state, Direction side) {
        return side == state.getValue(FACING) || state.getValue(TILTED) && side == Direction.DOWN;
    }

    /**
     * Where a hit on a part of the screen touched its face, the player looking along a direction turned into the
     * screen's frame; null for a hit on its back or ends. Upright, the hit is on the face. Tilted, the face runs across
     * the diagonal, behind the steps its blocks are shaped of, which stand out in front of it: the touch is where the
     * look, carried on past the hit, meets the diagonal.
     */
    @Nullable
    public static Touch touched(BlockState state, BlockPos pos, BlockHitResult hit, Vec3 look) {
        if (!onFace(state, hit.getDirection())) {
            return null;
        }
        Direction facing = state.getValue(FACING);
        boolean tilted = state.getValue(TILTED);
        Vec3 at = hit.getLocation().subtract(Vec3.atCenterOf(origin(state, pos)));
        if (tilted) {
            at = ontoDiagonal(at, Vec3.atLowerCornerOf(facing.getNormal()), look);
        }
        double up = (at.y + 0.5) * (tilted ? TILTED_HEIGHT : UPRIGHT_HEIGHT);
        return new Touch(at.dot(Vec3.atLowerCornerOf(right(facing).getNormal())) + 0.5, up);
    }

    /**
     * A point by the tilted face, from the middle of the block holding the screen, carried along a look onto the
     * diagonal, where it stands as far out from the block's back as up from its bottom; the point itself for a look
     * along the diagonal.
     */
    private static Vec3 ontoDiagonal(Vec3 at, Vec3 out, Vec3 look) {
        double closing = look.dot(out) - look.y;
        if (Math.abs(closing) < 1e-6) {
            return at;
        }
        return at.add(look.scale(-(at.dot(out) - at.y) / closing));
    }

    /**
     * Against the side of a block it stands upright facing out from it; against the underside of one it hangs tilted,
     * facing whoever puts it there; put anywhere else it stands upright facing them. It reaches to the viewer's right
     * and up from where it is put, and is not put up where anything stands in the way of that.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        boolean tilted = clicked == Direction.DOWN;
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockState placed = defaultBlockState();
        for (int part = 1; part < parts(placed); part++) {
            BlockPos at = part(context.getClickedPos(), facing, part, columns(placed));
            if (level.isOutsideBuildHeight(at) || !level.getWorldBorder().isWithinBounds(at) || !level.getBlockState(at).canBeReplaced(context)) {
                return null;
            }
        }
        return placed.setValue(FACING, facing).setValue(TILTED, tilted);
    }

    /** The rest of the screen goes up beside the part placed. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int part = 1; part < parts(state); part++) {
            level.setBlock(part(pos, facing, part, columns(state)), state.setValue(PART, part), Block.UPDATE_ALL);
        }
    }

    /** A part whose neighbour in the screen is gone goes too, so the screen is only ever whole. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        Direction facing = state.getValue(FACING);
        BlockPos origin = origin(state, pos);
        for (int part = 0; part < parts(state); part++) {
            // The part there must be this screen's: the same block in the same state but for its part.
            if (part(origin, facing, part, columns(state)).equals(neighbourPos) && neighbour != state.setValue(PART, part)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Broken in creative mode, the parts it takes with it drop nothing either. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos origin = origin(state, pos);
            Direction facing = state.getValue(FACING);
            for (int part = 0; part < parts(state); part++) {
                BlockPos at = part(origin, facing, part, columns(state));
                if (!at.equals(pos) && level.getBlockState(at).is(this)) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(TILTED) ? TILTED_SHAPES : UPRIGHT_SHAPES).get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? newScreen(pos, state) : null;
    }

    /** The block entity of the part holding the screen, which its renderer draws it all from. */
    protected abstract BlockEntity newScreen(BlockPos pos, BlockState state);
}
