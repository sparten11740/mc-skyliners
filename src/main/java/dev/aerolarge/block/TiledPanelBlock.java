package dev.aerolarge.block;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A slim panel {@link #COLUMNS} by {@link #ROWS} blocks, a tile of it in each block: hung from a ceiling, its face
 * looking down, or put up on a wall (WALL), its face looking out into the room. Every block of it is the same block;
 * PART says which, counting along the top row from the viewer's left and then on, row by row: under a ceiling the top
 * row is the one nearest the viewer and the rows run away from them, on a wall the rows run down. Part 0 holds the
 * block entity and draws the whole of it. FACING is the way whoever looks at it faces: toward the wall, or under a
 * ceiling, the way they look up along it. Put up by hand against the underside or the side of a block, it goes up with
 * the middle tile where it was put, facing the way whoever puts it there faces, if there is room for all of it; every
 * part goes up at once. Taking any part away takes the whole panel, which drops one panel from part 0.
 */
public abstract class TiledPanelBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final int COLUMNS = 3;
    public static final int ROWS = 3;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, COLUMNS * ROWS - 1);
    public static final BooleanProperty WALL = BooleanProperty.create("wall");
    /**
     * How deep the panel stands out from what it is put up against, in pixels: a quarter block, a slim panel close to
     * the ceiling or the wall.
     */
    public static final int DEPTH = 4;
    private static final VoxelShape UNDER_CEILING = Block.box(0, 16 - DEPTH, 0, 16, 16, 16);
    /** On a wall, against the side of the block the viewer faces. */
    private static final Map<Direction, VoxelShape> ON_WALL = Map.of(
        Direction.NORTH, Block.box(0, 0, 0, 16, 16, DEPTH),
        Direction.SOUTH, Block.box(0, 0, 16 - DEPTH, 16, 16, 16),
        Direction.WEST, Block.box(0, 0, 0, DEPTH, 16, 16),
        Direction.EAST, Block.box(16 - DEPTH, 0, 0, 16, 16, 16)
    );
    /** The middle part, which goes where the panel is put up. */
    private static final int MIDDLE = COLUMNS * ROWS / 2;

    /** Whether it can be hung from a ceiling by hand, as well as put up on a wall. */
    private final boolean ceilings;

    protected TiledPanelBlock(Properties properties, boolean ceilings) {
        super(properties);
        this.ceilings = ceilings;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0).setValue(WALL, !ceilings));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, WALL);
    }

    /** The viewer's right, looking at the panel facing this way. */
    public static Direction right(Direction facing) {
        return facing.getClockWise();
    }

    /** The way the panel's face looks: down from a ceiling, or out of a wall at whoever faces it. */
    public static Direction face(BlockState state) {
        return state.getValue(WALL) ? state.getValue(FACING).getOpposite() : Direction.DOWN;
    }

    /** The way from one row to the next: away from the viewer under a ceiling, down a wall. */
    private static Direction onward(BlockState state) {
        return state.getValue(WALL) ? Direction.DOWN : state.getValue(FACING);
    }

    /** Where a part of the panel is, from the part holding it. */
    private static BlockPos part(BlockPos origin, BlockState state, int part) {
        return origin.relative(right(state.getValue(FACING)), part % COLUMNS).relative(onward(state), part / COLUMNS);
    }

    /** Where the part holding the panel is, from any part. */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        int part = state.getValue(PART);
        return pos.relative(right(state.getValue(FACING)), -(part % COLUMNS)).relative(onward(state), -(part / COLUMNS));
    }

    /**
     * Against the underside of a block, where it hangs from ceilings, or the side of one, with room for the whole
     * panel round the spot.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        boolean wall = clicked.getAxis().isHorizontal();
        if (!wall && (clicked != Direction.DOWN || !ceilings)) {
            return null;
        }
        Direction facing = wall ? clicked.getOpposite() : context.getHorizontalDirection();
        BlockState middle = defaultBlockState().setValue(FACING, facing).setValue(PART, MIDDLE).setValue(WALL, wall);
        BlockPos origin = origin(middle, context.getClickedPos());
        Level level = context.getLevel();
        for (int part = 0; part < COLUMNS * ROWS; part++) {
            BlockPos at = part(origin, middle, part);
            if (level.isOutsideBuildHeight(at) || !level.getWorldBorder().isWithinBounds(at) || !level.getBlockState(at).canBeReplaced(context)) {
                return null;
            }
        }
        return middle;
    }

    /** The rest of the panel goes up round the middle part. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        BlockPos origin = origin(state, pos);
        for (int part = 0; part < COLUMNS * ROWS; part++) {
            if (part != MIDDLE) {
                level.setBlock(part(origin, state, part), state.setValue(PART, part), Block.UPDATE_ALL);
            }
        }
    }

    /** A part whose neighbour in the panel is gone goes too, so the panel is only ever whole. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        BlockPos origin = origin(state, pos);
        for (int part = 0; part < COLUMNS * ROWS; part++) {
            if (part(origin, state, part).equals(neighbourPos) && !sameAs(neighbour, state, part)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Whether a block is the given part of the same panel as a part of it. */
    private boolean sameAs(BlockState other, BlockState state, int part) {
        return other.is(this) && other.getValue(FACING) == state.getValue(FACING) && other.getValue(WALL) == state.getValue(WALL)
            && other.getValue(PART) == part;
    }

    /** Broken in creative mode, the parts it takes with it drop nothing either. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            takeDownRest(level, pos, state);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Takes down every part but the one at pos, dropping nothing. */
    private void takeDownRest(Level level, BlockPos pos, BlockState state) {
        BlockPos origin = origin(state, pos);
        for (int part = 0; part < COLUMNS * ROWS; part++) {
            BlockPos at = part(origin, state, part);
            if (!at.equals(pos) && level.getBlockState(at).is(this)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(WALL) ? ON_WALL.get(state.getValue(FACING)) : UNDER_CEILING;
    }
}
