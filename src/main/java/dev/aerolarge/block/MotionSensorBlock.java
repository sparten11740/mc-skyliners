package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A motion sensor, as over an automatic door: a slim white housing screwed to the face of a wall, low in its block so
 * that over a door it sits on the door's head, with a band of dark lens across it and a little lamp that lights while
 * it sees someone. It watches the ground a few blocks out on both sides of the wall it is fixed to, and while a player
 * or a villager is there it gives off a full redstone signal, as a button does: all round, and through the block it is
 * fixed to, so the doors under that block open (Create's sliding doors open as a pair when either is powered); and for
 * a moment after they have gone, so the doors do not shut on their heels. FACING is the way its lens looks, out from
 * its wall; with the wall gone it drops off.
 */
public class MotionSensorBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<MotionSensorBlock> CODEC = simpleCodec(MotionSensorBlock::new);
    /** How often it looks, in ticks, and how long it stays on after it last saw someone, in looks. */
    private static final int LOOK_EVERY = 4;
    private static final int HOLD_LOOKS = 5;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** How many more looks it stays on for, having seen no one. */
    public static final IntegerProperty HOLD = IntegerProperty.create("hold", 0, HOLD_LOOKS);
    /** How far it sees in front of and behind its wall, either side along it, and down to the floor, in blocks. */
    private static final int REACH = 4;
    private static final int SIDEWAYS = 2;
    private static final int DOWN = 3;
    /**
     * Whether the housing sits half a block to the right of its block, as seen facing it, over the middle of a pair of
     * doors; set by a blueprint that lays out such a pair.
     */
    public static final BooleanProperty SHIFTED = BooleanProperty.create("shifted");
    /** The housing, by the way its lens looks: ten pixels wide, four tall and three deep, against its wall. */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(3, 0, 13, 13, 4, 16),
        Direction.SOUTH, Block.box(3, 0, 0, 13, 4, 3),
        Direction.WEST, Block.box(13, 0, 3, 16, 4, 13),
        Direction.EAST, Block.box(0, 0, 3, 3, 4, 13)
    );
    private static final Map<Direction, VoxelShape> SHIFTED_SHAPES = Map.of(
        Direction.NORTH, Block.box(-5, 0, 13, 5, 4, 16),
        Direction.SOUTH, Block.box(11, 0, 0, 21, 4, 3),
        Direction.WEST, Block.box(13, 0, 11, 16, 4, 21),
        Direction.EAST, Block.box(0, 0, -5, 3, 4, 5)
    );

    public MotionSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(POWERED, false).setValue(HOLD, 0).setValue(SHIFTED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, HOLD, SHIFTED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(SHIFTED) ? SHIFTED_SHAPES : SHAPES).get(state.getValue(FACING));
    }

    /** Fixed to the side of the block clicked, looking out from it; clicked on a top or a bottom, to the wall ahead. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        boolean wall = side == state.getValue(FACING).getOpposite();
        return wall && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** Set in place (by hand or by a blueprint), it starts looking. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this)) {
            level.scheduleTick(pos, this, LOOK_EVERY);
        }
    }

    /** Taken down while on, it lets go of the doors through its wall. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (!moving && !state.is(now.getBlock()) && state.getValue(POWERED)) {
            updateNeighbours(state, level, pos);
        }
        super.onRemove(state, level, pos, now, moving);
    }

    /** Looks: on while it sees someone, and for a few looks after; then looks again in a moment. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int hold = seesSomeone(level, pos, state.getValue(FACING)) ? HOLD_LOOKS : Math.max(0, state.getValue(HOLD) - 1);
        BlockState now = state.setValue(HOLD, hold).setValue(POWERED, hold > 0);
        if (now != state) {
            level.setBlock(pos, now, Block.UPDATE_ALL);
        }
        if (now.getValue(POWERED) != state.getValue(POWERED)) {
            updateNeighbours(now, level, pos);
        }
        level.scheduleTick(pos, this, LOOK_EVERY);
    }

    /** Tells what is round it, and round the wall it is fixed to, that its signal has come or gone, as a button does. */
    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), this);
    }

    /** Whether a player (not a spectator) or a villager stands on either side of the wall it is fixed to, within reach. */
    private static boolean seesSomeone(ServerLevel level, BlockPos pos, Direction facing) {
        Direction along = facing.getClockWise();
        BlockPos wall = pos.relative(facing.getOpposite());
        BlockPos front = wall.relative(facing, REACH).relative(along, SIDEWAYS);
        BlockPos back = wall.relative(facing, -REACH).relative(along, -SIDEWAYS).below(DOWN);
        List<LivingEntity> seen = level.getEntitiesOfClass(LivingEntity.class, AABB.encapsulatingFullBlocks(front, back),
            entity -> entity instanceof Player player ? !player.isSpectator() : entity instanceof Npc);
        return !seen.isEmpty();
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    /** Through the block it is fixed to, as a button's signal goes: so the doors under that block open. */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && direction == state.getValue(FACING) ? 15 : 0;
    }
}
