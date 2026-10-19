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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The tail camera display on the flight deck, like an A380's taxiing camera screen: a wide screen, {@link #COLUMNS}
 * blocks wide and {@link #ROWS} tall, for the picture from the camera on top of the fin, looking forward over the
 * whole airliner; its renderer draws it all. Every block of it is this block; PART says which, counting along the
 * bottom row from the viewer's left and then up, and part 0 holds the block entity the whole screen is drawn from.
 * FACING is the way the screen faces. Upright, it is a slim screen with its back to whatever it is fixed to. TILTED, it
 * is set into a ceiling like an overhead monitor: the screen runs across each block's diagonal, from its bottom edge
 * at the back to its top edge at the front, so it looks down toward FACING at 45 degrees, and behind it the block is
 * filled out to its top and back, plated like the hull, closed to the light, so set into the airliner's skin it keeps
 * the skin closed. Placed by hand against a wall it stands upright, put up against the underside of a block it hangs
 * tilted, and anywhere else it stands upright facing whoever puts it there; it needs the room for all of it and puts
 * every part up at once. Taking any part away takes the whole screen, which drops one display from part 0.
 */
public class TailCameraDisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<TailCameraDisplayBlock> CODEC = simpleCodec(TailCameraDisplayBlock::new);
    public static final int COLUMNS = 3;
    public static final int ROWS = 1;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, COLUMNS * ROWS - 1);
    public static final BooleanProperty TILTED = BooleanProperty.create("tilted");
    /** How thick the upright screen is, in pixels of its block, standing against the back of it. */
    public static final int DEPTH = 3;
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

    public TailCameraDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(PART, 0).setValue(TILTED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, TILTED);
    }

    /**
     * A box against the back of the block, the side away from the way the screen faces: from y0 to y1 up, and reaching
     * this deep from the back, in pixels.
     */
    private static VoxelShape againstBack(Direction facing, double y0, double y1, double depth) {
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

    /** Where a part of the screen is, from the part holding it. */
    private static BlockPos part(BlockPos origin, Direction facing, int part) {
        return origin.relative(right(facing), part % COLUMNS).above(part / COLUMNS);
    }

    /** Where the part holding the screen is, from any part. */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        int part = state.getValue(PART);
        return pos.relative(right(state.getValue(FACING)), -(part % COLUMNS)).below(part / COLUMNS);
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
        for (int part = 1; part < COLUMNS * ROWS; part++) {
            BlockPos at = part(context.getClickedPos(), facing, part);
            if (level.isOutsideBuildHeight(at) || !level.getWorldBorder().isWithinBounds(at) || !level.getBlockState(at).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(TILTED, tilted);
    }

    /** The rest of the screen goes up beside the part placed. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int part = 1; part < COLUMNS * ROWS; part++) {
            level.setBlock(part(pos, facing, part), state.setValue(PART, part), Block.UPDATE_ALL);
        }
    }

    /** A part whose neighbour in the screen is gone goes too, so the screen is only ever whole. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        Direction facing = state.getValue(FACING);
        BlockPos origin = origin(state, pos);
        for (int part = 0; part < COLUMNS * ROWS; part++) {
            if (part(origin, facing, part).equals(neighbourPos) && !(neighbour.is(this) && neighbour.getValue(FACING) == facing
                && neighbour.getValue(PART) == part && neighbour.getValue(TILTED) == state.getValue(TILTED))) {
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
            for (int part = 0; part < COLUMNS * ROWS; part++) {
                BlockPos at = part(origin, facing, part);
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

    /** Tilted, it fills the skin it is set into: no daylight comes through it. */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(TILTED) && super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(TILTED) ? level.getMaxLightLevel() : super.getLightBlock(state, level, pos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new TailCameraDisplayBlockEntity(pos, state) : null;
    }
}
