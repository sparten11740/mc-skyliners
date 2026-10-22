package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;

/**
 * A motion sensor, as over an automatic door: a white housing with a dark lens and a little lamp that lights while it
 * sees someone. It watches the ground a few blocks out on both sides of the wall it is set in, in front of it and
 * behind it, and while a player or a villager is there it gives off a full redstone signal all round, to open the
 * doors under it (Create's sliding doors open as a pair when either is powered), and for a moment after they have gone,
 * so the doors do not shut on their heels. FACING is the way its lens looks.
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

    public MotionSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(POWERED, false).setValue(HOLD, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, HOLD);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Set in place (by hand or by a blueprint), it starts looking. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this)) {
            level.scheduleTick(pos, this, LOOK_EVERY);
        }
    }

    /** Looks: on while it sees someone, and for a few looks after; then looks again in a moment. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int hold = seesSomeone(level, pos, state.getValue(FACING)) ? HOLD_LOOKS : Math.max(0, state.getValue(HOLD) - 1);
        BlockState now = state.setValue(HOLD, hold).setValue(POWERED, hold > 0);
        if (now != state) {
            level.setBlock(pos, now, Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, LOOK_EVERY);
    }

    /** Whether a player (not a spectator) or a villager stands on either side of its wall, within its reach. */
    private static boolean seesSomeone(ServerLevel level, BlockPos pos, Direction facing) {
        Direction along = facing.getClockWise();
        BlockPos front = pos.relative(facing, REACH).relative(along, SIDEWAYS);
        BlockPos back = pos.relative(facing, -REACH).relative(along, -SIDEWAYS).below(DOWN);
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
}
